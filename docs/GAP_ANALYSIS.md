# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Small**

Each microservice is a standalone Gradle project with its own `build.gradle`, `settings.gradle`, and Gradle wrapper. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds across all services.

**Impact:** Developers must `cd` into each service directory to build, test, or run. There is no single command to build or test the entire system. This complicates CI/CD setup and makes dependency version drift across services more likely.

**Evidence:**
- 7 separate `build.gradle` files with independently declared Spring Boot (`3.2.4`) and Spring Cloud (`2023.0.0`) versions
- No root `settings.gradle` or `build.gradle`

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

Identical classes are copy-pasted across multiple services with no shared library:

| Duplicated Class | Services |
|-----------------|----------|
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment (4 copies) |
| `AuditAware` | user-service, fund-transfer, utility-payment (3 copies) |
| `AuditConfig` + `AuditorAwareConfig` | user-service, fund-transfer, utility-payment (3 copies) |
| `ApiRequestContext` + `ApiRequestContextHolder` + `AppAuthUserFilter` | user-service, fund-transfer, utility-payment (3 copies) |
| `SimpleBankingGlobalException` | all 4 business services (4 copies) |
| `ErrorResponse` | all 4 business services (4 copies) |
| `GlobalExceptionHandler` | all 4 business services (4 copies, with slight variations) |
| `TransactionStatus` enum | fund-transfer, utility-payment (2 copies) |

**Impact:** Bug fixes or improvements must be manually replicated across all copies. Divergence between copies introduces subtle inconsistencies (e.g., `GlobalExceptionHandler` in fund-transfer constructs `ErrorResponse` differently than in core-banking).

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

While services generally follow a `controller/service/model/exception` structure, there are inconsistencies:

- User Service puts `UserRepository` under `model.repository`; Core Banking puts repositories under `repository`
- Fund Transfer Service puts its Feign client under `service.rest.client`; User Service uses `service.rest`; Utility Payment uses `service.rest`
- User Service has a `configuration.feign` sub-package with custom error decoder; other services do not

### 1.4 Mappers Instantiated Inline Instead of as Beans

**Severity: Low | Effort: Small**

Mapper classes (`BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` inside service classes rather than being Spring-managed beans.

```java
private UserMapper userMapper = new UserMapper(); // in AccountService, UserService, etc.
```

**Impact:** Prevents injection of dependencies into mappers and makes unit testing harder (can't mock mappers). Inconsistent with the DI pattern used everywhere else.

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` in every service returns `400 Bad Request` for all exceptions, including:
- `EntityNotFoundException` → should be `404 Not Found`
- `InsufficientFundsException` → could be `422 Unprocessable Entity`
- Generic `Exception` → should be `500 Internal Server Error`

**Evidence:**
```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);  // Always 400
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);  // Always 400
}
```

### 2.2 Generic Exception Handler Leaks Internal Details

**Severity: Critical | Effort: Small**

The catch-all `Exception` handler in all services returns the full exception toString, which can include stack traces, class names, and internal system details:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Impact:** Exposes internal implementation details to clients. In a banking application, this is a significant security risk.

### 2.3 Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- Business exceptions return structured `ErrorResponse` objects (`{ code, message }`).
- Generic exceptions return plain strings (`"Exception occur inside API " + e`).
- The fund-transfer service constructs `ErrorResponse` via constructor (`new ErrorResponse(...)`) while others use the builder pattern (`ErrorResponse.builder()...`).

**Impact:** API consumers cannot reliably parse error responses since the format varies by exception type.

### 2.4 Missing Error Codes in Some Services

**Severity: Low | Effort: Small**

- Core Banking has `GlobalErrorCode` with 2 codes (`BANKING-CORE-SERVICE-1000`, `1001`).
- User Service has `GlobalErrorCode` with 4 codes (`USER-SERVICE-1000`–`1003`).
- Fund Transfer Service and Utility Payment Service have **no** `GlobalErrorCode` class and no service-specific error codes.

### 2.5 Feign Error Handling Only in User Service

**Severity: Medium | Effort: Medium**

Only the User Service has a custom `CustomFeignErrorDecoder` that deserializes error responses from core banking. The Fund Transfer Service has a `CustomFeignClientConfiguration` that only sets Feign log level. The Utility Payment Service's Feign configuration extends `FeignClientConfiguration` but adds nothing.

**Impact:** Feign errors from core banking in Fund Transfer and Utility Payment services will result in generic Feign exceptions rather than properly propagated business errors.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Files | Tests | Coverage |
|---------|-----------|-------|----------|
| Core Banking | 4 files (1 context test, 3 service unit tests) | ~15 tests | Service layer only |
| User Service | 1 file (context test only) | 1 test | **None** (context load) |
| Fund Transfer | 1 file (context test only) | 1 test | **None** (context load) |
| Utility Payment | 1 file (context test only) | 1 test | **None** (context load) |
| API Gateway | 1 file (context test only) | 1 test | **None** (context load) |
| Config Server | 1 file (context test only) | 1 test | **None** (context load) |
| Service Registry | 1 file (context test only) | 1 test | **None** (context load) |

Only the Core Banking Service has meaningful unit tests (for `AccountService`, `TransactionService`, `UserService`). All other services have only the auto-generated `contextLoads()` test.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller layer behavior (MockMvc or WebTestClient tests)
- Database operations with a real or embedded database
- Feign client interactions (WireMock or Testcontainers)
- End-to-end flows through the API Gateway

### 3.3 No Contract Tests

**Severity: High | Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) between services. Given the tight Feign coupling between services, breaking changes in core banking's API would silently break fund transfer and utility payment services.

### 3.4 Test Configuration Issues

**Severity: Medium | Effort: Small**

- Test `application.yml` files disable Flyway (`flyway.enabled: false`) and use H2, but set `ddl-auto: none`, meaning tables aren't created in test databases. Tests that touch the database would fail.
- The context load test for Core Banking may fail because the H2 database has no schema and Flyway is disabled.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No controller method uses Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on request bodies or path variables. Examples:

- `FundTransferRequest` accepts any amount including null, negative, or zero
- `User` registration accepts null email, null identification, null password
- `UtilityPaymentRequest` accepts null providerId, null amount

**Evidence:** No `jakarta.validation` or `spring-boot-starter-validation` dependency in any `build.gradle`.

### 4.2 Hardcoded Credentials in Docker Compose

**Severity: High | Effort: Small**

Multiple credentials are hardcoded in `docker-compose.yml` and `privileges.sql`:

| Secret | Location | Value |
|--------|----------|-------|
| MySQL root password | `docker-compose.yml` | `woVERANKliGharym` |
| MySQL app user password | `privileges.sql` | `oPItyPticIAt` |
| Keycloak admin password | `docker-compose.yml` | `password` |
| Keycloak DB password | `docker-compose.yml` | `password` |
| Keycloak client secret | `test application.yml` | `e8548d56-d743-45ef-8655-063c9cd96759` |

**Impact:** Credentials committed to source control. While this is a development/demo setup, it sets a bad precedent and may be deployed as-is.

### 4.3 Overly Broad Database Privileges

**Severity: Medium | Effort: Small**

The MySQL init script grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` to the application user:

```sql
GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO 'javatodev_development'@'%';
```

**Impact:** Application user can modify any database including the Keycloak PostgreSQL or system tables. Should be scoped per-database.

### 4.4 Keycloak Singleton Pattern is Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static field with no synchronization:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Race condition on first access in a multi-threaded environment could create multiple Keycloak client instances.

### 4.5 No CORS Configuration

**Severity: Medium | Effort: Small**

No CORS configuration exists in the API Gateway or any service. If a frontend application is consuming these APIs, cross-origin requests would be blocked by browsers.

### 4.6 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the API Gateway's `SecurityConfiguration`:

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While this is appropriate for a stateless JWT-based API, it should be documented and the security rationale should be explicit.

### 4.7 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured at the API Gateway or service level. Financial APIs without rate limiting are vulnerable to abuse.

---

## 5. API Design

### 5.1 Missing Response Type Generics

**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without type parameters:

```java
public ResponseEntity getBankAccount(...) { ... }  // Should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...) { ... }     // Should be ResponseEntity<FundTransferResponse>
```

**Impact:** OpenAPI/Swagger documentation cannot infer response types, and clients get no compile-time type safety.

### 5.2 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

All endpoints use `/api/v1/` in their path, but there is no mechanism to support multiple API versions simultaneously. No versioning headers, content negotiation, or routing strategy exists.

### 5.3 Inconsistent Pagination

**Severity: Low | Effort: Small**

Pagination is handled via Spring's `Pageable` parameter injection in GET endpoints, but:
- No default page size is configured
- No maximum page size is enforced
- Response doesn't include pagination metadata (total count, total pages, current page)

### 5.4 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

While Spring Data's `Pageable` supports sorting and paging query params, this is not documented in the API or OpenAPI annotations.

### 5.5 Inconsistent Endpoint Naming

**Severity: Low | Effort: Small**

- Core Banking uses `snake_case` path variables: `/bank-account/{account_number}`, `/util-account/{account_name}`
- User Service uses `camelCase` or simple path: `/update/{id}`, `/{id}`
- `PATCH /update/{id}` is non-RESTful — should be `PATCH /{id}`

### 5.6 OpenAPI/Swagger Dependency Mismatch

**Severity: Medium | Effort: Small**

Services that are servlet-based (Spring MVC) include `springdoc-openapi-starter-webflux-ui` which is intended for WebFlux/reactive applications. This may cause classpath conflicts or the Swagger UI may not work correctly.

**Evidence:** `build.gradle` in core-banking, user-service, fund-transfer, utility-payment all include:
```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

These services use `spring-boot-starter-web` (servlet), not `spring-boot-starter-webflux`.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request details; others don't (e.g., `UtilityPaymentController` has no `@Slf4j`)
- Log messages expose sensitive data: `log.info("Creating user with {}", request.toString())` logs user registration details including passwords
- No structured logging format (JSON) configured — defaults to plain text
- No correlation ID logging despite having distributed tracing

### 6.2 No Custom Health Checks

**Severity: Low | Effort: Small**

All services include `spring-boot-starter-actuator`, but no custom health indicators are defined for:
- Database connectivity (beyond the auto-configured JPA health)
- Keycloak connectivity
- Downstream service availability
- RabbitMQ (for future use)

### 6.3 No Metrics Endpoints Beyond Defaults

**Severity: Medium | Effort: Medium**

While Actuator is included, there are no custom metrics for:
- Fund transfer volume/count
- Payment success/failure rates
- Response time percentiles per endpoint
- Business KPIs (active users, transaction amounts)

The README mentions Prometheus but there is no `micrometer-registry-prometheus` dependency in any `build.gradle`.

### 6.4 Distributed Tracing Gaps

**Severity: Low | Effort: Small**

- Tracing dependencies are present and Zipkin is configured, which is good
- However, custom span annotations for key business operations (e.g., fund transfer, payment processing) are absent
- No span tags for business context (account numbers, transaction IDs, user IDs)

### 6.5 No Centralized Log Aggregation

**Severity: Medium | Effort: Medium**

No ELK stack, Loki, or other log aggregation solution is configured. In a microservices architecture with 7 services, correlating logs across services requires centralized logging.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker pattern is implemented on any Feign client call. If core banking becomes unavailable:

- Fund Transfer Service will block on every request until Feign timeout
- Utility Payment Service will block on every request until Feign timeout
- User Service registration will block on every request until Feign timeout

No `spring-cloud-starter-circuitbreaker-resilience4j` dependency exists in any service.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for Feign clients or any network calls. Transient failures (network blips, temporary unavailability) will immediately fail the request.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign client connection and read timeouts (defaults may be too long)
- Database connection pool timeouts
- Keycloak API call timeouts

**Impact:** A hung downstream service can cascade and exhaust thread pools in upstream services.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback methods or degraded service behavior is defined for any Feign call. When core banking is down:
- Users can't see their transfer history (could be served from local DB cache)
- New transfers fail immediately (could queue for later processing)

### 7.5 No Bulkhead Pattern

**Severity: Medium | Effort: Medium**

No thread pool isolation or bulkhead pattern is implemented. A slow dependency can consume all available threads and bring down the entire service.

### 7.6 Transaction Integrity Issues

**Severity: Critical | Effort: Medium**

The fund transfer orchestration in `FundTransferService` is not atomic across services:

```java
FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);  // Step 1: Local save
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);  // Step 2: Remote call
optFundTransfer.setStatus(TransactionStatus.SUCCESS);  // Step 3: Local update
fundTransferRepository.save(optFundTransfer);
```

If Step 2 succeeds but Step 3 fails (e.g., network issue after response received), the core banking records the transfer but the fund transfer service records it as PENDING. There is no compensation or saga pattern.

### 7.7 Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`, available balance is double-subtracted:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// After these lines: availableBalance = originalActual - amount - amount (DOUBLE SUBTRACTED)
```

Similarly, for credits:
```java
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
// After these lines: availableBalance = originalActual + amount + amount (DOUBLE ADDED)
```

**Impact:** Account balances become inconsistent after every transaction. This is a critical financial bug.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Small |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers not Spring beans | Code Organization | Low | Small |
| 2.1 | All exceptions return HTTP 400 | Error Handling | High | Small |
| 2.2 | Generic exception handler leaks internals | Error Handling | Critical | Small |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | Missing error codes in some services | Error Handling | Low | Small |
| 2.5 | Feign error handling only in User Service | Error Handling | Medium | Medium |
| 3.1 | Minimal test coverage | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | High | Large |
| 3.4 | Test configuration issues | Testing | Medium | Small |
| 4.1 | No input validation | Security | Critical | Medium |
| 4.2 | Hardcoded credentials | Security | High | Small |
| 4.3 | Overly broad DB privileges | Security | Medium | Small |
| 4.4 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.5 | No CORS configuration | Security | Medium | Small |
| 4.6 | CSRF disabled without documentation | Security | Low | Small |
| 4.7 | No rate limiting | Security | Medium | Medium |
| 5.1 | Missing ResponseEntity type generics | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Medium | Medium |
| 5.3 | Inconsistent pagination | API Design | Low | Small |
| 5.4 | No filtering/sorting documentation | API Design | Low | Small |
| 5.5 | Inconsistent endpoint naming | API Design | Low | Small |
| 5.6 | OpenAPI dependency mismatch | API Design | Medium | Small |
| 6.1 | Inconsistent/insecure logging | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Low | Small |
| 6.3 | No custom metrics / Prometheus | Observability | Medium | Medium |
| 6.4 | Distributed tracing gaps | Observability | Low | Small |
| 6.5 | No centralized log aggregation | Observability | Medium | Medium |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No bulkhead pattern | Resilience | Medium | Medium |
| 7.6 | Transaction integrity (no saga) | Resilience | Critical | Medium |
| 7.7 | Balance calculation bug (double subtraction) | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 7 |
| Medium | 16 |
| Low | 8 |
| **Total** | **37** |
