# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

**Severity: Medium** | **Effort: Small**

Each service is an independent Gradle project with its own `gradlew`, `gradle/wrapper`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to manage them as a unified multi-module build.

**Impact**: Cannot run `./gradlew build` from the project root. Each service must be built independently. Shared dependency versions must be synchronized manually across 6 `build.gradle` files.

**Current State**: 6 independent `build.gradle` files, each repeating the same Spring Boot/Cloud versions.

### 1.2 Duplicated Code Across Services

**Severity: High** | **Effort: Medium**

Significant code is copy-pasted across multiple services with no shared library:

| Duplicated Code | Services |
|---|---|
| `BaseMapper<E, D>` abstract class | core-banking, user, fund-transfer, utility-payment |
| `AuditAware` mapped superclass | user, fund-transfer, utility-payment |
| `AuditConfig` + `AuditorAwareConfig` | user, fund-transfer, utility-payment |
| `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` | user, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | all 4 business services |
| `ErrorResponse` | all 4 business services |
| `GlobalExceptionHandler` | all 4 business services |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |
| `TransactionStatus` enum | fund-transfer, utility-payment |

**Impact**: Bug fixes or improvements must be applied to 3-4 copies. Divergence between copies is already observable (e.g., `ErrorResponse` uses `@Builder` in some services but a constructor in others).

### 1.3 Inconsistent Package Structure

**Severity: Low** | **Effort: Small**

Minor inconsistencies in package naming:
- `model.repository` (user-service) vs. `repository` (core-banking, utility-payment) vs. `model.repository` (fund-transfer)
- `model.rest.request` / `model.rest.response` (utility-payment) vs. `model.dto.request` / `model.dto.response` (fund-transfer, core-banking)
- `service.rest.client` (fund-transfer) vs. `service.rest` (user, utility-payment)

**Impact**: Developers must learn different conventions per service. Reduces navigability.

### 1.4 Mappers Instantiated Manually Instead of Injected

**Severity: Low** | **Effort: Small**

Mappers are created via `new` in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans or using libraries like MapStruct.

**Impact**: Cannot be mocked in tests. Violates dependency injection principles. Mapping logic cannot leverage Spring context.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400 Bad Request

**Severity: High** | **Effort: Small**

Every `GlobalExceptionHandler` across all services returns `ResponseEntity.badRequest()` (HTTP 400) for all exceptions, including:
- Entity not found (should be **404**)
- Insufficient funds (could be **422** Unprocessable Entity)
- Unhandled exceptions (should be **500**)
- User already registered (could be **409** Conflict)

**Current Code** (identical in all services):
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact**: Clients cannot distinguish between error types by HTTP status code. Violates REST semantics.

### 2.2 Raw Exception Details Leaked to Clients

**Severity: Critical** | **Effort: Small**

The generic exception handler concatenates the raw Java exception into the response body:
```java
.body("Exception occur inside API " + e);
```

This exposes:
- Java class names and package paths
- Stack trace fragments
- Database error messages (SQL syntax, table names, column names)
- Internal service implementation details

**Impact**: Information disclosure vulnerability. Aids attackers in understanding system internals.

### 2.3 Inconsistent Error Response Format

**Severity: Medium** | **Effort: Small**

Two different error response formats exist:
1. **Structured**: `ErrorResponse { code, message }` — for `SimpleBankingGlobalException` subclasses
2. **Raw String**: `"Exception occur inside API <exception>"` — for all other exceptions

Additionally, `ErrorResponse` is constructed differently across services:
- Core-banking and utility-payment use `ErrorResponse.builder().code(...).message(...).build()`
- Fund-transfer uses `new ErrorResponse(e.getCode(), e.getMessage())`

**Impact**: Clients must handle two different response shapes. Inconsistent developer experience.

### 2.4 Missing Raw Type Parameterization on ResponseEntity

**Severity: Low** | **Effort: Small**

Most controller methods return raw `ResponseEntity` instead of parameterized types like `ResponseEntity<BankAccount>`. This applies to all controllers in core-banking, fund-transfer, and utility-payment services.

**Impact**: Compiler warnings. Loss of type safety. OpenAPI documentation cannot infer response types.

---

## 3. Testing

### 3.1 No Tests in 3 of 4 Business Services

**Severity: Critical** | **Effort: Large**

| Service | Test Files | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 files | 19 unit tests (AccountService, TransactionService, UserService) |
| internet-banking-user-service | 1 file | 0 (empty context load test only) |
| internet-banking-fund-transfer-service | 1 file | 0 (empty context load test only) |
| internet-banking-utility-payment-service | 1 file | 0 (empty context load test only) |
| internet-banking-api-gateway | 1 file | 0 (empty context load test only) |
| internet-banking-config-server | 1 file | 0 (empty context load test only) |
| internet-banking-service-registry | 1 file | 0 (empty context load test only) |

**Impact**: No validation of business logic in user registration, fund transfer orchestration, or utility payment orchestration. Regressions cannot be caught automatically.

### 3.2 No Integration Tests

**Severity: High** | **Effort: Large**

No `@SpringBootTest` integration tests exist that verify:
- Database interactions with real schemas
- Feign client contract compliance
- API Gateway routing and security filtering
- End-to-end request flows

**Impact**: Cannot verify that services work together. Schema mismatches, serialization issues, and routing errors are only discovered at runtime.

### 3.3 No Contract Tests Between Services

**Severity: High** | **Effort: Medium**

Services communicate via Feign clients, but there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that:
- The fund-transfer Feign client's expected request/response matches core-banking's actual API
- The utility-payment Feign client matches core-banking's API
- The user-service Feign client matches core-banking's API

**Impact**: Breaking API changes in core-banking will silently break downstream services with no automated detection.

### 3.4 Context Load Tests Will Fail Without Infrastructure

**Severity: Low** | **Effort: Small**

The empty `@SpringBootTest` tests in user-service, fund-transfer, and utility-payment will fail because they attempt to connect to MySQL, Eureka, and the Config Server. The test `application.yml` files configure H2 and disable Eureka/Config, but some services may still fail due to Keycloak or Feign client initialization.

**Impact**: Default test suite may not be runnable in CI without additional test configuration.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical** | **Effort: Small**

Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|---|---|
| `docker-compose/docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose/mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact**: Credentials are exposed in the Git history. Attackers can access databases and admin panels if deployed with these defaults.

### 4.2 No Input Validation

**Severity: Critical** | **Effort: Medium**

No request body validation exists anywhere in the codebase:
- No `@Valid` / `@Validated` annotations on controller parameters
- No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on DTO fields
- No validation of account number format, email format, or amount constraints

**Examples of unvalidated inputs**:
- `FundTransferRequest.amount` — can be null, zero, or negative
- `User.email` — no format validation
- `User.password` — no strength requirements
- `UtilityPaymentRequest.providerId` — can be null

**Impact**: Malformed requests reach business logic and database layers. Could cause `NullPointerException`, negative balance transfers, or data corruption.

### 4.3 CSRF Disabled Without Documentation

**Severity: Medium** | **Effort: Small**

CSRF protection is disabled in the API Gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While acceptable for a stateless JWT-based API, this should be documented and the decision justified. If a web frontend with cookie-based sessions is ever added, this becomes a vulnerability.

### 4.4 Keycloak Singleton Not Thread-Safe

**Severity: Medium** | **Effort: Small**

`KeycloakProperties.getInstance()` uses a lazy-initialized static singleton without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact**: Race condition during startup. Multiple Keycloak instances could be created, potentially causing connection pool issues or token management problems.

### 4.5 No Rate Limiting

**Severity: Medium** | **Effort: Medium**

No rate limiting is configured at the API Gateway or any service level. Financial APIs (fund transfers, payments) are particularly sensitive to abuse.

**Impact**: Susceptible to brute-force attacks, denial of service, and automated fraud attempts.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No dependency vulnerability scanning tools are configured:
- No OWASP Dependency-Check Gradle plugin
- No Snyk, Dependabot, or Renovate integration
- No GitHub security advisories configured

**Impact**: Vulnerable transitive dependencies may go undetected.

---

## 5. API Design

### 5.1 No API Versioning Strategy

**Severity: Medium** | **Effort: Medium**

While endpoints use `/api/v1/` prefix, there is no documented versioning strategy. No mechanism exists for:
- Running multiple API versions simultaneously
- Deprecating old endpoints
- Version negotiation via headers

**Impact**: Any breaking API change will require coordinated deployment of all services and clients simultaneously.

### 5.2 Inconsistent Pagination

**Severity: Medium** | **Effort: Small**

Paginated endpoints accept Spring's `Pageable` parameter but:
- Return `List<T>` instead of `Page<T>`, losing pagination metadata (total count, total pages, current page)
- No documented query parameter format (`?page=0&size=20&sort=id,asc`)
- Core-banking user endpoint returns `List<User>`, fund-transfer returns `List<FundTransfer>`

**Impact**: Clients cannot implement proper pagination UI (no total count). Different services may handle pagination parameters differently.

### 5.3 No Filtering or Sorting API

**Severity: Low** | **Effort: Medium**

No endpoints support filtering (e.g., transfers by account, payments by date range, users by status). Only basic pagination is available.

**Impact**: Clients must fetch all records and filter client-side, which is inefficient for large datasets.

### 5.4 OpenAPI/Swagger Misconfigured

**Severity: Medium** | **Effort: Small**

All services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but:
- The non-gateway services use Spring MVC (not WebFlux), so the `webflux-ui` starter is incorrect — should be `springdoc-openapi-starter-webmvc-ui`
- Raw `ResponseEntity` return types prevent automatic response schema generation
- No centralized/aggregated Swagger UI across all services

**Impact**: Swagger UI may not work correctly. API documentation is incomplete or missing response schemas.

### 5.5 Missing HATEOAS / Resource Links

**Severity: Low** | **Effort: Medium**

Responses do not include hypermedia links to related resources. For example, a fund transfer response doesn't link to the transaction details or account balances.

**Impact**: Clients must hardcode URLs. API is less discoverable.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium** | **Effort: Small**

Logging inconsistencies across services:
- Some methods log entry (`log.info("Got fund transfer request from API {}")`) but no exit/result
- String concatenation in log statements (`"Sending fund transfer request {}" + request.toString()`) — should use parameterized logging
- No structured logging format (JSON) configured
- No correlation ID logging (though trace IDs are propagated via Micrometer)
- `KeycloakUserService.readUser()` imports `org.slf4j.LoggerFactory` manually while also using Lombok `@Slf4j`

**Impact**: Difficult to trace requests across services in log aggregation. Performance impact from string concatenation in hot paths.

### 6.2 No Health Check Customization

**Severity: Low** | **Effort: Small**

Services include `spring-boot-starter-actuator` but no custom health indicators are configured. Default health checks exist for database connectivity, but there are no checks for:
- Keycloak connectivity
- RabbitMQ availability (when implemented)
- Downstream service availability via Feign

**Impact**: Health endpoints report service as "UP" even when critical dependencies are unavailable.

### 6.3 No Metrics Endpoints Configured

**Severity: Medium** | **Effort: Small**

While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, there is no:
- Prometheus metrics endpoint exposed (`/actuator/prometheus`)
- Custom business metrics (transfer counts, payment volumes, error rates)
- Metrics dashboard or alerting configuration
- README mentions Prometheus but no actual integration exists

**Impact**: No visibility into application performance, throughput, or error rates.

### 6.4 Distributed Tracing Not Fully Verified

**Severity: Low** | **Effort: Small**

Zipkin dependencies are included in all services and a Zipkin container is defined in Docker Compose, but:
- No sampling rate configuration visible (defaults to 10%)
- No custom span annotations on business-critical operations
- Tracing configuration is in the external Config Server Git repo, not directly verifiable from this codebase

**Impact**: May miss important traces. Critical business operations may not have dedicated spans.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

No circuit breaker pattern is implemented on any Feign client or inter-service call:
- Fund-transfer → core-banking calls have no fallback
- Utility-payment → core-banking calls have no fallback
- User-service → core-banking and Keycloak calls have no fallback
- No `spring-cloud-starter-circuitbreaker-resilience4j` or Hystrix dependency

**Impact**: If core-banking-service goes down, all dependent services will fail with cascading timeouts. No graceful degradation.

### 7.2 No Retry Policies

**Severity: High** | **Effort: Small**

No retry configuration exists for:
- Feign client calls (transient network errors will fail immediately)
- Database connections
- Keycloak admin API calls

Spring Cloud OpenFeign supports declarative retry, but it is not configured.

**Impact**: Transient failures (network blips, brief service restarts) cause immediate request failures instead of transparent recovery.

### 7.3 No Timeout Configuration

**Severity: High** | **Effort: Small**

No explicit timeout configuration for:
- Feign client connection and read timeouts (defaults may be infinite or very long)
- Database connection pool timeouts
- Keycloak API call timeouts

**Impact**: A slow or unresponsive downstream service can exhaust thread pools in calling services, leading to cascading failures.

### 7.4 No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

No fallback responses are defined for any inter-service communication. When a downstream service is unavailable:
- No cached responses
- No default values
- No graceful error messages
- Raw Feign exceptions propagate to clients

**Impact**: Any single service failure results in user-facing errors with no graceful degradation.

### 7.5 No Bulkhead Pattern

**Severity: Medium** | **Effort: Medium**

No thread pool isolation or semaphore bulkheads exist. All Feign calls share the same thread pool.

**Impact**: A slow downstream service (e.g., Keycloak) can consume all threads, blocking unrelated operations (e.g., reading transfer history).

### 7.6 No Idempotency for Financial Operations

**Severity: High** | **Effort: Medium**

Fund transfer and utility payment endpoints are not idempotent:
- No idempotency key in request headers or body
- No duplicate detection mechanism
- If a client retries a timed-out transfer, the money could be debited twice

**Impact**: Network retries or client-side retries can cause duplicate financial transactions.

### 7.7 Balance Update Not Atomic (Race Condition)

**Severity: Critical** | **Effort: Medium**

In `TransactionService.internalFundTransfer()`, balance updates follow a read-modify-write pattern without pessimistic locking:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

Additionally, `availableBalance` is computed incorrectly — it subtracts `amount` from the already-debited `actualBalance`:
```java
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
This results in `availableBalance` being `actualBalance - 2 * amount` instead of `actualBalance - amount`.

The same bug exists in `utilPayment()`.

**Impact**: Concurrent transfers from the same account can overdraw funds. The available balance calculation is mathematically incorrect, leading to data integrity issues.

---

## Summary Matrix

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-module Gradle build | Code Organization | Medium | Small |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers manually instantiated | Code Organization | Low | Small |
| 2.1 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.2 | Raw exception details leaked | Error Handling | Critical | Small |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | Raw ResponseEntity types | Error Handling | Low | Small |
| 3.1 | No tests in 3 of 4 business services | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | High | Medium |
| 3.4 | Context load tests misconfigured | Testing | Low | Small |
| 4.1 | Hardcoded credentials | Security | Critical | Small |
| 4.2 | No input validation | Security | Critical | Medium |
| 4.3 | CSRF disabled without docs | Security | Medium | Small |
| 4.4 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.5 | No rate limiting | Security | Medium | Medium |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | No API versioning strategy | API Design | Medium | Medium |
| 5.2 | Inconsistent pagination | API Design | Medium | Small |
| 5.3 | No filtering or sorting | API Design | Low | Medium |
| 5.4 | OpenAPI/Swagger misconfigured | API Design | Medium | Small |
| 5.5 | Missing HATEOAS | API Design | Low | Medium |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Low | Small |
| 6.3 | No metrics endpoints | Observability | Medium | Small |
| 6.4 | Tracing not fully verified | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No bulkhead pattern | Resilience | Medium | Medium |
| 7.6 | No idempotency for financial ops | Resilience | High | Medium |
| 7.7 | Balance update race condition / bug | Resilience | Critical | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 6 |
| High | 8 |
| Medium | 14 |
| Low | 7 |

### Effort Distribution

| Effort | Count |
|---|---|
| Small | 18 |
| Medium | 14 |
| Large | 3 |
