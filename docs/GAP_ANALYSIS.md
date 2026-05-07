# Engineering Standards Gap Analysis

This document evaluates the codebase against engineering best practices across seven categories. Each gap is rated by **severity** (Critical / High / Medium / Low) and **remediation effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Shared Library — Duplicated Code Across Services

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding:** Core abstractions are copy-pasted verbatim across the fund-transfer, utility-payment, and user services:

- `BaseMapper<E, D>` — identical in all three services
- `AuditAware` base entity — identical in all three services
- `AuditConfig` + `AuditorAwareConfig` — identical in all three services
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — identical in all three services
- `ErrorResponse` / `GlobalExceptionHandler` / `SimpleBankingGlobalException` — identical in all three services
- `GlobalErrorCode` constants — identical across services
- `CustomFeignClientConfiguration` — identical across services

**Impact:** Bug fixes must be applied N times. Behavioral drift between services is inevitable.

### 1.2 No Multi-Module Gradle Build

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Each service is a standalone Gradle project with its own `build.gradle`, `settings.gradle`, and Gradle wrapper. There is no root-level build that ties them together or enforces consistent dependency versions.

**Impact:** Dependency version drift is possible. No single command to build/test all services. Difficult to enforce shared build conventions (formatting, static analysis, etc.).

### 1.3 Mapper Instantiation via `new` Instead of Spring Beans

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Mappers are instantiated directly (`private UserMapper userMapper = new UserMapper()`) rather than being managed as Spring beans. This prevents dependency injection into mappers and is inconsistent with the rest of the codebase's Spring-managed approach.

### 1.4 Inconsistent Package Naming

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** The Core Banking Service uses `model.entity` for JPA entities and `model` for DTOs. Other services place entities under `model.entity` and DTOs under `model.dto`. The Core Banking Service also names its DTO classes the same as entity classes (`User`, `BankAccount`) but in different packages, creating potential import confusion.

---

## 2. Error Handling

### 2.1 Catch-All Returns HTTP 400 for All Unhandled Exceptions

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** The `GlobalExceptionHandler` in every service handles `Exception.class` by returning HTTP 400 (Bad Request) with a raw string body:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

**Problems:**
1. Server errors (NPE, DB failures, etc.) return 400 instead of 500
2. Exception `toString()` is exposed to the client, leaking implementation details (stack traces, class names, SQL errors)
3. The response format (`String`) differs from the structured `ErrorResponse` format used for known exceptions

### 2.2 Missing Validation Error Handling

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No `@Valid` annotations are used on any `@RequestBody` parameter. No Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Min`, etc.) are defined on any request DTO. The `MethodArgumentNotValidException` handler is not implemented in any `GlobalExceptionHandler`.

**Impact:** Invalid inputs (null amounts, empty account numbers, negative values) propagate to the service layer and cause opaque failures.

### 2.3 No Feign Error Decoder

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** None of the Feign clients define a custom `ErrorDecoder`. When the Core Banking Service returns a 4xx/5xx error, the calling service receives a generic `FeignException` with no structured error propagation. The orchestrating services (fund-transfer, utility-payment) do not catch Feign errors, meaning failures bubble up as unhandled exceptions and hit the catch-all 400 handler.

### 2.4 Raw `ResponseEntity` Without Type Parameters

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** All controllers use raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This suppresses compile-time type safety and results in less informative OpenAPI documentation.

---

## 3. Testing

### 3.1 Minimal Unit Test Coverage

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |

**Finding:** Only the Core Banking Service has meaningful unit tests (3 test classes covering `AccountService`, `TransactionService`, and `UserService`). The remaining 5 services have only the auto-generated `contextLoads()` test. Fund Transfer Service, Utility Payment Service, and User Service — which contain significant business logic and orchestration — have zero business logic tests.

**Estimated coverage:** < 10% overall.

### 3.2 No Integration Tests

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding:** There are no integration tests that verify:
- Database operations (repository tests with `@DataJpaTest` or `@SpringBootTest` + Testcontainers)
- Controller endpoint behavior (`@WebMvcTest` or `MockMvc` tests)
- Service-to-database interaction with real queries

All existing tests mock every dependency, including repositories.

### 3.3 No Contract Tests Between Services

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between services. When the Core Banking Service changes an API contract (endpoint path, request/response shape), there is no automated mechanism to detect breaking changes in the three consumer services (fund-transfer, utility-payment, user).

### 3.4 No Test for Negative / Edge Cases

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Existing tests only cover the happy path. No tests for:
- Insufficient funds scenarios
- Account not found
- Duplicate user registration
- Invalid email mismatch
- Concurrent transfers on the same account

---

## 4. Security

### 4.1 No Input Validation on Any Request DTO

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** No request DTO uses Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Email`, etc.). Key examples:

- `FundTransferRequest`: `fromAccount`, `toAccount`, and `amount` are all unvalidated
- `UtilityPaymentRequest`: `providerId`, `amount`, `referenceNumber`, `account` are unvalidated
- `User` (registration): `email`, `identification`, `password` are unvalidated

A null `amount` will cause a `NullPointerException` at `BigDecimal.compareTo()` in `validateBalance()`.

### 4.2 Exception Details Leaked to Clients

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** The catch-all exception handler returns `"Exception occur inside API " + e`, which includes the full `toString()` of the exception. This can expose:
- Database table/column names
- SQL query fragments
- Internal class names and stack traces
- Sensitive configuration details

### 4.3 Hardcoded Credentials in Docker Compose and SQL

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** Production-style credentials are hardcoded in:
- `docker-compose/mysql/privileges.sql`: MySQL user password `oPItyPticIAt`
- `docker-compose/docker-compose.yml`: Keycloak admin password `Pa55w0rd`, PostgreSQL password
- Test data in Flyway migrations: `V1.0.20210427174721__temp_data.sql`

While acceptable for local development, no mechanism exists to inject different credentials in staging/production environments.

### 4.4 No CSRF Protection Configuration

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** The API Gateway security configuration disables CSRF (`csrf(ServerHttpSecurity.CsrfSpec::disable)`) with no compensating controls. While CSRF is typically unnecessary for stateless JWT-based APIs, this should be explicitly documented and intentional rather than a blanket disable.

### 4.5 No Rate Limiting

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** The API Gateway has no rate limiting configured. Financial endpoints (fund transfer, payment) are vulnerable to abuse. Spring Cloud Gateway has built-in `RequestRateLimiter` filter support that is unused.

### 4.6 Sensitive Data in Logs

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Request objects are logged using `toString()`:
```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```
This logs financial details (account numbers, amounts) at INFO level. In production, this creates compliance concerns (PCI-DSS, GDPR).

---

## 5. API Design

### 5.1 No Pagination Metadata in Responses

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, etc.) accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with total count, page number, and page size. Clients cannot determine if there are more pages.

### 5.2 Non-Standard Endpoint Naming

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Several inconsistencies in REST naming:
- `POST /api/v1/bank-users/register` — verb in URL (should be `POST /api/v1/bank-users`)
- `PATCH /api/v1/bank-users/update/{id}` — verb in URL (should be `PATCH /api/v1/bank-users/{id}`)
- `POST /api/v1/transfer` (fund transfer) vs `POST /api/v1/utility-payment` — inconsistent noun patterns

### 5.3 Missing HATEOAS / Hypermedia Links

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Finding:** Responses do not include hypermedia links. For a banking application, this would improve discoverability (e.g., linking from a fund transfer to the account detail).

### 5.4 No API Versioning Strategy

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** While endpoints include `/v1/` in the path, there is no versioning strategy, no documentation on when to bump the version, and no mechanism to support multiple versions simultaneously. Gateway routing does not account for API versions.

### 5.5 OpenAPI Documentation Partially Implemented

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** `@Tag` and `@Operation` annotations were recently added to controllers (Swagger changes in commit `2f08427`), but request/response schemas lack `@Schema` annotations with descriptions, examples, and constraints. The `springdoc-openapi-starter-webmvc-ui` dependency is included but minimally configured.

---

## 6. Observability

### 6.1 Inconsistent Logging Patterns

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Logging varies significantly:
- Some methods log entry points, others don't
- Log levels are inconsistent (some errors logged at INFO, some at ERROR)
- No structured logging format (all services use default Spring Boot logback)
- No correlation ID / trace ID in log messages (Micrometer tracing is present but not explicitly wired to MDC for log output)

### 6.2 No Custom Health Indicators

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Services rely on default Spring Boot Actuator health checks. No custom health indicators for:
- Database connectivity verification
- Keycloak reachability (User Service)
- Core Banking Service availability (from fund-transfer, utility-payment, user services)

### 6.3 No Custom Metrics

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** No custom Micrometer metrics are defined. Key metrics missing:
- Transaction count/rate (by type: fund transfer, utility payment)
- Transaction amount totals
- Transaction failure rates
- Feign client latency histograms
- Active user sessions

Default Actuator metrics (JVM, HTTP) are available but not enhanced for business monitoring.

### 6.4 No Centralized Log Aggregation

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** No ELK/EFK stack, Loki, or other log aggregation setup in Docker Compose. Logs are only available via `docker logs`. For a 6-service architecture, this makes troubleshooting very difficult.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** No circuit breaker (Resilience4j, Hystrix) is configured on any Feign client. If the Core Banking Service goes down:
- Fund Transfer Service will fail on every request with a Feign connection timeout
- Utility Payment Service will fail on every request
- User Service registration will fail
- All failures will cascade through the API Gateway to clients

This is particularly dangerous for a financial application where partial failures should be handled gracefully.

### 7.2 No Retry Policies

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No retry configuration exists on any Feign client or service call. Transient network failures (momentary DNS issues, TCP resets) cause immediate request failure. Spring Retry or Resilience4j retry can be added declaratively.

### 7.3 No Explicit Timeout Configuration

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** Feign clients use default timeouts (which may be infinite depending on the underlying HTTP client). No explicit `connectTimeout` or `readTimeout` configured in any Feign client or `application.yml`. A slow Core Banking Service response can hold threads indefinitely in calling services.

### 7.4 No Fallback Behavior

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** No Feign fallback implementations exist. When inter-service calls fail:
- No graceful degradation
- No cached responses
- No default/partial responses
- Errors cascade directly to the end user

### 7.5 No Idempotency on Write Operations

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** Fund transfer and utility payment endpoints have no idempotency key mechanism. If a client retries a failed request (network timeout, but the request actually succeeded), the transaction may be processed twice. This is a critical concern for financial operations.

### 7.6 Transaction Status Never Transitions to FAILED

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** In both Fund Transfer and Utility Payment services, the orchestration flow is:
1. Save entity with status `PENDING` / `PROCESSING`
2. Call Core Banking Service
3. On success → update to `SUCCESS`

If step 2 throws an exception, the entity remains in `PENDING` / `PROCESSING` forever. No catch block updates the status to `FAILED`. No background job or reconciliation process exists to clean up stale records.

### 7.7 No Database Transaction Isolation for Financial Operations

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** The `internalFundTransfer` method in Core Banking Service uses the default transaction isolation level. Concurrent fund transfers from the same account could lead to race conditions where the balance check passes for both transactions before either debit is applied, resulting in overdrafts.

---

## Summary Table

| Category | Critical | High | Medium | Low |
|---|---|---|---|---|
| Code Organization | 0 | 1 | 1 | 2 |
| Error Handling | 1 | 2 | 0 | 1 |
| Testing | 1 | 2 | 1 | 0 |
| Security | 1 | 2 | 3 | 0 |
| API Design | 0 | 0 | 2 | 3 |
| Observability | 0 | 0 | 3 | 1 |
| Resilience | 1 | 5 | 0 | 0 |
| **Total** | **4** | **12** | **10** | **7** |
