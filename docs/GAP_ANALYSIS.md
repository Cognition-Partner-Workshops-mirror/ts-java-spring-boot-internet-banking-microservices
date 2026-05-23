# Internet Banking Microservices — Gap Analysis

This document compares the current codebase against engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### GAP-1.1: No Shared Library — Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3–4 services with only trivial differences:

- `BaseMapper` (core-banking, fund-transfer, utility-payment, user-service)
- `AuditAware` (fund-transfer, utility-payment, user-service)
- `GlobalExceptionHandler` (core-banking, fund-transfer, utility-payment, user-service)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (fund-transfer, utility-payment, user-service)
- `ErrorResponse` / `SimpleBankingGlobalException` (all 4 business services)
- `TransactionStatus` enum (fund-transfer, utility-payment)
- `CustomFeignClientConfiguration` (fund-transfer, utility-payment)
- `AuditConfig` / `AuditorAwareConfig` (fund-transfer, utility-payment, user-service)

There is no shared library or module to house common code. Any bug fix or enhancement must be replicated across all services manually.

### GAP-1.2: No Multi-Module Root Build

**Severity: Medium | Effort: Small**

Each service has its own independent Gradle build. There is no root-level `settings.gradle` or `build.gradle` for unified builds, dependency version management, or consistent plugin configuration.

### GAP-1.3: Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package naming is inconsistent across services:
- Fund Transfer: `model.repository.FundTransferRepository`
- Utility Payment: `repository.UtilityPaymentRepository` (different package path)
- User Service: `model.repository.UserRepository`
- Core Banking: `repository.*Repository`

DTO packages also vary: `model.dto.request` vs `model.rest.request`.

---

## 2. Error Handling

### GAP-2.1: All Errors Return HTTP 400 Bad Request

**Severity: Critical | Effort: Small**

The `GlobalExceptionHandler` in all services maps every exception to `ResponseEntity.badRequest()`. This means:
- Entity not found → 400 (should be 404)
- Insufficient funds → 400 (acceptable, but no differentiation)
- Unexpected server errors → 400 (should be 500)
- All business errors use the same HTTP status code

This violates REST conventions and makes it impossible for clients to distinguish between error types.

### GAP-2.2: Generic Exception Handler Leaks Stack Traces

**Severity: High | Effort: Small**

The catch-all `@ExceptionHandler({Exception.class})` handler returns `"Exception occur inside API " + e`, which concatenates the full exception (including stack trace) into the response body. This is a security risk and provides poor developer experience.

### GAP-2.3: Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- Business exceptions return `ErrorResponse {code, message}` — structured
- Generic exceptions return a plain string — unstructured
- Clients cannot reliably parse error responses

### GAP-2.4: No Request Validation

**Severity: High | Effort: Small**

No `@Valid` / `@NotNull` / `@Size` annotations on any `@RequestBody` parameters. Invalid requests (null amounts, empty account numbers) are passed directly to service logic, potentially causing `NullPointerException` at runtime.

---

## 3. Testing

### GAP-3.1: Minimal Unit Test Coverage

**Severity: High | Effort: Large**

Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have only empty `ApplicationTests` classes that verify the Spring context loads (and most likely fail without database connectivity).

| Service | Test Classes | Meaningful Tests |
|---------|-------------|-----------------|
| core-banking-service | 4 | 3 (AccountServiceTest, TransactionServiceTest, UserServiceTest) |
| internet-banking-user-service | 1 | 0 (empty context test) |
| internet-banking-fund-transfer-service | 1 | 0 (empty context test) |
| internet-banking-utility-payment-service | 1 | 0 (empty context test) |
| internet-banking-api-gateway | 1 | 0 (empty context test) |
| internet-banking-config-server | 1 | 0 (empty context test) |

### GAP-3.2: No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests using `@SpringBootTest` with a test container or in-memory database that validate actual HTTP endpoints, Feign client calls, or JPA repository queries.

### GAP-3.3: No Contract Tests

**Severity: Medium | Effort: Large**

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. A breaking change in core-banking-service could silently break fund-transfer and utility-payment services.

---

## 4. Security

### GAP-4.1: No Auth on Downstream Services

**Severity: Critical | Effort: Medium**

JWT validation only occurs at the API Gateway. Downstream services (core-banking, fund-transfer, utility-payment, user-service) have no authentication or authorization. They trust the `X-Auth-Id` header, which is a plain HTTP header that can be spoofed if services are accessed directly (bypassing the gateway).

### GAP-4.2: Hardcoded Credentials in Source

**Severity: Critical | Effort: Small**

Multiple plaintext credentials are committed to the repository:
- MySQL root password: `woVERANKliGharym` (docker-compose.yml, Dockerfile)
- MySQL app user: `javatodev_development` / `oPItyPticIAt` (privileges.sql)
- Keycloak admin: `admin` / `password` (docker-compose.yml)
- Keycloak DB: `keycloak` / `password` (docker-compose.yml)
- Test user credentials in README: `ib_admin@javatodev.com` / `5V7huE3G86uB`

These should be externalized to environment variables or a secrets manager.

### GAP-4.3: No Input Validation or Sanitization

**Severity: High | Effort: Small**

As noted in GAP-2.4, there is zero request body validation. This extends to:
- No check for negative transfer amounts
- No account number format validation
- No email format validation on user registration
- No SQL injection protection beyond JPA parameterization

### GAP-4.4: CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the gateway security configuration (`csrf.disable()`). For a REST API using JWT Bearer tokens this is acceptable, but it is not documented or justified.

### GAP-4.5: Keycloak Client Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern with a static field. This is not thread-safe and could lead to multiple Keycloak client instances being created.

---

## 5. API Design

### GAP-5.1: Raw ResponseEntity Without Generic Types

**Severity: Medium | Effort: Small**

Most controllers return raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This produces incomplete OpenAPI documentation and loses compile-time type safety.

### GAP-5.2: No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (total elements, total pages, current page). Clients cannot implement pagination UIs correctly.

### GAP-5.3: Wrong OpenAPI Dependency

**Severity: Medium | Effort: Small**

All MVC-based services (core-banking, fund-transfer, utility-payment, user-service) use `springdoc-openapi-starter-webflux-ui:2.1.0`. This is the WebFlux (reactive) dependency, but these services use Spring Web MVC (servlet). The correct dependency should be `springdoc-openapi-starter-webmvc-ui`.

### GAP-5.4: No API Versioning Strategy

**Severity: Low | Effort: Small**

While URLs include `/api/v1/`, there is no versioning strategy, documentation, or mechanism for supporting multiple API versions.

### GAP-5.5: Inconsistent Endpoint Naming

**Severity: Low | Effort: Small**

- Fund transfer: `/api/v1/transfer` (service) vs `/api/v1/transaction/fund-transfer` (core)
- Utility payment: `/api/v1/utility-payment` (service) vs `/api/v1/transaction/util-payment` (core)
- Account: `/api/v1/account/util-account` (abbreviation) vs full names elsewhere

### GAP-5.6: No HATEOAS or Hypermedia Links

**Severity: Low | Effort: Medium**

API responses are plain DTOs with no hypermedia links for discoverability.

---

## 6. Observability

### GAP-6.1: No Structured Logging

**Severity: Medium | Effort: Small**

Logging uses plain text format via SLF4J/Logback defaults. There is no structured (JSON) logging configuration for production use, making log aggregation and querying in tools like ELK or Loki difficult.

### GAP-6.2: No Health Check Configuration

**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` is included in all services, health check endpoints are not explicitly configured (e.g., database health, Keycloak connectivity). Docker Compose also does not include `healthcheck` configurations.

### GAP-6.3: No Custom Metrics

**Severity: Low | Effort: Medium**

While Micrometer is on the classpath for tracing, no custom business metrics are defined (e.g., transfer count, transfer amount, payment volume, error rates).

### GAP-6.4: No Centralized Log Aggregation

**Severity: Medium | Effort: Medium**

There is no log aggregation infrastructure (ELK, Loki, etc.) configured. Logs are only available per-container.

### GAP-6.5: Sensitive Data in Logs

**Severity: High | Effort: Small**

Controllers log full request objects via `toString()` (e.g., `"Got fund transfer request from API {}"` with `fundTransferRequest.toString()`). This could expose PII, account numbers, and financial amounts in log files.

---

## 7. Resilience

### GAP-7.1: No Circuit Breakers

**Severity: High | Effort: Medium**

Feign clients to core-banking-service have no circuit breakers (Resilience4j, Hystrix). If core-banking-service goes down, all downstream calls will block and cascade failures.

### GAP-7.2: No Retry Policies

**Severity: Medium | Effort: Small**

No retry configuration for Feign clients. Transient network errors cause immediate failure.

### GAP-7.3: No Timeout Configuration

**Severity: High | Effort: Small**

Feign clients use default timeouts (which may be very long or infinite). No explicit connection or read timeout is configured, risking thread exhaustion under load.

### GAP-7.4: No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback methods are defined for Feign client failures. Errors propagate directly as unhandled exceptions.

### GAP-7.5: No Idempotency Protection

**Severity: High | Effort: Medium**

Fund transfer and utility payment endpoints can process duplicate transactions. There is no idempotency key, duplicate detection, or deduplication logic. A network retry could result in double-charging a customer.

### GAP-7.6: Non-Atomic Financial Operations

**Severity: Critical | Effort: Large**

The fund transfer flow spans two services (fund-transfer-service → core-banking-service). If the core-banking call succeeds but the fund-transfer-service fails to update its local record (e.g., database error), the system enters an inconsistent state. There is no saga pattern, compensation logic, or distributed transaction management.

### GAP-7.7: Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.utilPayment()` and `internalFundTransfer()`, the `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been reduced. This double-subtracts, causing `availableBalance` to be lower than intended.

```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount)); // BUG: actualBalance already reduced
```

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-1.1 | Code Organization | Duplicated code across services (no shared library) | High | Medium |
| GAP-1.2 | Code Organization | No multi-module root build | Medium | Small |
| GAP-1.3 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-2.1 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-2.2 | Error Handling | Generic exception handler leaks stack traces | High | Small |
| GAP-2.3 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-2.4 | Error Handling | No request validation | High | Small |
| GAP-3.1 | Testing | Minimal unit test coverage (only 1 of 6 services) | High | Large |
| GAP-3.2 | Testing | No integration tests | High | Large |
| GAP-3.3 | Testing | No contract tests | Medium | Large |
| GAP-4.1 | Security | No auth on downstream services | Critical | Medium |
| GAP-4.2 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-4.3 | Security | No input validation or sanitization | High | Small |
| GAP-4.4 | Security | CSRF disabled without documentation | Low | Small |
| GAP-4.5 | Security | Keycloak client singleton not thread-safe | Medium | Small |
| GAP-5.1 | API Design | Raw ResponseEntity without generic types | Medium | Small |
| GAP-5.2 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-5.3 | API Design | Wrong OpenAPI dependency (webflux-ui for MVC services) | Medium | Small |
| GAP-5.4 | API Design | No API versioning strategy | Low | Small |
| GAP-5.5 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-5.6 | API Design | No HATEOAS or hypermedia links | Low | Medium |
| GAP-6.1 | Observability | No structured logging | Medium | Small |
| GAP-6.2 | Observability | No health check configuration | Medium | Small |
| GAP-6.3 | Observability | No custom metrics | Low | Medium |
| GAP-6.4 | Observability | No centralized log aggregation | Medium | Medium |
| GAP-6.5 | Observability | Sensitive data in logs | High | Small |
| GAP-7.1 | Resilience | No circuit breakers | High | Medium |
| GAP-7.2 | Resilience | No retry policies | Medium | Small |
| GAP-7.3 | Resilience | No timeout configuration | High | Small |
| GAP-7.4 | Resilience | No fallback behavior | Medium | Medium |
| GAP-7.5 | Resilience | No idempotency protection | High | Medium |
| GAP-7.6 | Resilience | Non-atomic financial operations (no saga pattern) | Critical | Large |
| GAP-7.7 | Resilience | Balance calculation bug (double-subtraction) | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 11 |
| Medium | 11 |
| Low | 5 |
| **Total** | **32** |
