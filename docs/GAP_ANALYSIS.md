# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Shared Library / Multi-Module Build

**Gap:** Each microservice duplicates common code (exception classes, `AuditAware`, `BaseMapper`, `ApiRequestContext`, `AppAuthUserFilter`, `CustomFeignClientConfiguration`). There is no shared library or Gradle multi-project build.

- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` are copy-pasted across 3+ services
- `AuditAware` base class is duplicated in user, fund-transfer, and utility-payment services
- `BaseMapper<E, D>` is duplicated in every service
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are duplicated across user, fund-transfer, and utility-payment services

| Severity | Effort |
|---|---|
| **High** | **Large** |

### 1.2 Inconsistent Package Structure

**Gap:** Package naming diverges across services:
- Core banking: `repository` package at `com.javatodev.finance.repository`
- User service: `model.repository` at `com.javatodev.finance.model.repository`
- Fund transfer: `model.repository` at `com.javatodev.finance.model.repository`
- Utility payment: `repository` at `com.javatodev.finance.repository`
- DTOs/request/response naming differs: `model.dto.request` vs `model.rest.request` vs `model.dto.response` vs `model.rest.response`

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 1.3 Mapper Instantiation Pattern

**Gap:** Mappers are manually instantiated with `new` in service classes rather than being Spring-managed beans or using a mapping framework (e.g., MapStruct). This prevents dependency injection and makes testing harder.

```java
// Found in every service class
private UserMapper userMapper = new UserMapper();
private FundTransferMapper mapper = new FundTransferMapper();
```

| Severity | Effort |
|---|---|
| **Low** | **Small** |

### 1.4 Keycloak Singleton Anti-Pattern

**Gap:** `KeycloakProperties.getInstance()` uses a manual static singleton pattern instead of Spring bean lifecycle management. This is not thread-safe and bypasses Spring's connection management.

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // not thread-safe
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

| Severity | Effort |
|---|---|
| **Medium** | **Small** |

---

## 2. Error Handling

### 2.1 Catch-All Returns HTTP 400 for Everything

**Gap:** `GlobalExceptionHandler.handleException(Exception e)` catches all unhandled exceptions and returns `400 Bad Request` with a raw exception string. This is incorrect -- server errors should return 5xx, and the response should never leak exception details.

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

Issues:
- Server errors (NPE, DB failures) return HTTP 400 instead of 500
- Exception stack trace leaks to clients (security risk)
- Raw string response instead of structured `ErrorResponse` JSON

| Severity | Effort |
|---|---|
| **Critical** | **Small** |

### 2.2 EntityNotFoundException Returns 400 Instead of 404

**Gap:** `EntityNotFoundException` is handled by the generic `SimpleBankingGlobalException` handler which always returns `400 Bad Request`. A not-found entity should return `404 Not Found`.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 2.3 No Error Handling in Feign Clients (Partial)

**Gap:** Only the fund-transfer service has a `CustomFeignErrorDecoder`. The user service's `BankingCoreRestClient` does not configure a custom error decoder. Feign errors from core-banking will propagate as raw `FeignException` to the client.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 2.4 Inconsistent Error Response Format

**Gap:** The global exception handler has two paths:
- `SimpleBankingGlobalException` -> `ErrorResponse { code, message }` (structured)
- `Exception` -> plain string `"Exception occur inside API " + e` (unstructured)

All error responses should use a consistent JSON structure.

| Severity | Effort |
|---|---|
| **Medium** | **Small** |

### 2.5 No Feign Call Error Handling in Fund Transfer / Utility Payment Services

**Gap:** `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` call core-banking via Feign but have no try-catch or error handling. If the Feign call fails, the local entity remains in `PENDING`/`PROCESSING` status permanently with no retry or cleanup.

| Severity | Effort |
|---|---|
| **High** | **Medium** |

---

## 3. Testing

### 3.1 Tests Only Exist for Core Banking Service

**Gap:** Unit tests exist only in `core-banking-service` (3 test files: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other four business services have only empty Spring Boot context-loading tests (`*ApplicationTests.java`).

| Service | Real Tests | Context Test Only |
|---|---|---|
| core-banking-service | 3 test classes, ~16 tests | Yes |
| internet-banking-user-service | None | Yes |
| internet-banking-fund-transfer-service | None | Yes |
| internet-banking-utility-payment-service | None | Yes |
| internet-banking-api-gateway | None | Yes |
| internet-banking-service-registry | None | Yes |
| internet-banking-config-server | None | Yes (implied) |

| Severity | Effort |
|---|---|
| **Critical** | **Large** |

### 3.2 No Integration Tests

**Gap:** There are no integration tests that verify actual HTTP endpoints, database interactions, or Feign client behavior. All existing tests are pure unit tests with Mockito mocks.

| Severity | Effort |
|---|---|
| **High** | **Large** |

### 3.3 No Contract Tests Between Services

**Gap:** No contract tests (e.g., Spring Cloud Contract, Pact) exist to verify that Feign client expectations match the provider's actual API. Breaking changes in core-banking-service could silently break downstream consumers.

| Severity | Effort |
|---|---|
| **Medium** | **Large** |

### 3.4 No Test Coverage Reporting

**Gap:** No JaCoCo or other coverage reporting is configured in any `build.gradle`.

| Severity | Effort |
|---|---|
| **Low** | **Small** |

---

## 4. Security

### 4.1 Hardcoded Database Credentials in Docker Compose

**Gap:** MySQL root password (`woVERANKliGharym`) and Keycloak admin credentials (`admin`/`password`) are hardcoded in `docker-compose.yml`. These should use environment variable substitution or Docker secrets.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 4.2 No Input Validation on Request Bodies

**Gap:** No `@Valid` annotations, no `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Bean Validation constraints on any request DTO across all services. Clients can submit empty or malformed requests that will cause uncontrolled `NullPointerException` or `NumberFormatException`.

Examples of missing validation:
- `FundTransferRequest`: `fromAccount`, `toAccount`, `amount` -- all nullable
- `UtilityPaymentRequest`: `providerId`, `amount`, `account` -- all nullable
- `User` registration: `email`, `identification`, `password` -- all nullable

| Severity | Effort |
|---|---|
| **Critical** | **Small** |

### 4.3 Password Handling in User Registration

**Gap:** The `User` DTO carries a `password` field in plaintext from the API through to Keycloak. While Keycloak handles password hashing, the password is:
- Logged via `log.info("Creating user with {}", request.toString())` (Lombok `@Data` includes all fields in `toString()`)
- Not excluded from API response serialization (returned in the response body)

| Severity | Effort |
|---|---|
| **Critical** | **Small** |

### 4.4 No Rate Limiting

**Gap:** No rate limiting is configured on the API gateway or any service. Financial endpoints (fund transfer, payment) are vulnerable to abuse.

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 4.5 Test Credentials in README

**Gap:** Production-resembling test credentials are documented in the README: `ib_admin@javatodev.com / 5V7huE3G86uB`. While these are for local testing, this practice normalizes credential exposure.

| Severity | Effort |
|---|---|
| **Low** | **Small** |

### 4.6 Raw `ResponseEntity` Without Type Parameters

**Gap:** Most controller methods return raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This prevents compile-time type safety and can leak unexpected objects in responses.

| Severity | Effort |
|---|---|
| **Medium** | **Small** |

---

## 5. API Design

### 5.1 No API Versioning Strategy

**Gap:** While endpoints use `/api/v1/` prefix, there is no documented versioning strategy, no content negotiation, and no mechanism to support multiple API versions simultaneously.

| Severity | Effort |
|---|---|
| **Low** | **Small** |

### 5.2 Pagination Response Missing Metadata

**Gap:** Paginated endpoints return `List<T>` instead of a proper page wrapper with metadata (total elements, page number, page size, total pages). Example:

```java
// UserController.java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

The `Page<T>` from Spring Data is unwrapped to a raw list, discarding pagination metadata.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 5.3 No Filtering or Sorting Parameters

**Gap:** List endpoints accept `Pageable` but no filtering criteria. There is no way to filter transactions by date range, status, account number, etc.

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 5.4 OpenAPI Documentation Incomplete

**Gap:** `springdoc-openapi-starter-webflux-ui` is included in business services, but:
- Uses the WebFlux variant in WebMVC services (incorrect dependency)
- `@Tag` and `@Operation` annotations are present, but no `@Schema` annotations on DTOs
- No `@ApiResponse` annotations for error responses
- No global OpenAPI configuration (title, description, security scheme)

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 5.5 Inconsistent Resource Naming

**Gap:** Endpoint naming conventions vary:
- `/api/v1/bank-users` (hyphenated, plural) -- user service
- `/api/v1/transfer` (singular) -- fund transfer service
- `/api/v1/utility-payment` (singular) -- utility payment service
- `/api/v1/account/bank-account/{account_number}` (underscore in path param) -- core banking
- `/api/v1/user/{identification}` (singular) -- core banking

REST convention is to use plural nouns consistently.

| Severity | Effort |
|---|---|
| **Low** | **Small** |

### 5.6 POST Endpoints Return 200 Instead of 201

**Gap:** Create/transfer endpoints (`POST /register`, `POST /transfer`, `POST /utility-payment`) return `200 OK` instead of `201 Created`.

| Severity | Effort |
|---|---|
| **Low** | **Small** |

---

## 6. Observability

### 6.1 Logging Is Inconsistent and Exposes Sensitive Data

**Gap:**
- `@Slf4j` is present on most controllers/services but logging levels and patterns are inconsistent
- `request.toString()` logs entire request objects including sensitive fields (passwords, account numbers)
- No structured logging (JSON format) -- all logs are unstructured text
- No correlation ID propagation beyond what Micrometer tracing provides

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 6.2 No Custom Health Check Endpoints

**Gap:** `spring-boot-starter-actuator` is included in all services, which provides `/actuator/health` by default. However:
- No custom health indicators for downstream dependencies (MySQL connectivity, Keycloak availability, Eureka registration)
- Health endpoint exposure configuration is managed in external config (not visible in repo)
- No readiness/liveness probe configuration for container orchestration

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 6.3 No Metrics Beyond Defaults

**Gap:** Actuator provides default metrics, but:
- No custom business metrics (transfer counts, payment amounts, error rates)
- Prometheus integration is mentioned in README but no `micrometer-registry-prometheus` dependency in any `build.gradle`
- No dashboards or alerting configuration

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 6.4 Distributed Tracing Coverage Unclear

**Gap:** Tracing dependencies are present in all services, but:
- No custom spans for business-critical operations
- Zipkin URL configuration is in external config (not verifiable in repo)
- No sampling rate configuration visible
- `feign-micrometer` is included but Feign span propagation is not explicitly verified

| Severity | Effort |
|---|---|
| **Low** | **Small** |

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap:** No circuit breaker library (Resilience4j, Hystrix) is included in any service. All Feign calls are fire-and-forget -- if core-banking-service is down, all dependent services will fail with unhandled exceptions and cascading timeouts.

| Severity | Effort |
|---|---|
| **Critical** | **Medium** |

### 7.2 No Retry Policies

**Gap:** No retry configuration on Feign clients or any other outbound call. Transient network failures immediately propagate as errors.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 7.3 No Timeout Configuration

**Gap:** No explicit timeout configuration on Feign clients, database connections, or HTTP clients. Default timeouts (often infinite or very long) will cause thread pool exhaustion under load.

| Severity | Effort |
|---|---|
| **High** | **Small** |

### 7.4 No Fallback Behavior

**Gap:** No fallback methods or degraded responses when downstream services are unavailable. A fund transfer or payment that fails partway through has no compensation logic.

| Severity | Effort |
|---|---|
| **Medium** | **Medium** |

### 7.5 No Idempotency Protection

**Gap:** `POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency keys. Retrying a failed request could result in duplicate transfers or payments.

| Severity | Effort |
|---|---|
| **High** | **Medium** |

### 7.6 Non-Atomic Balance Updates

**Gap:** In `TransactionService.internalFundTransfer()`, the source account debit and destination account credit are separate `save()` calls. While `@Transactional` is present, the `availableBalance` calculation has a bug:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^ availableBalance = actualBalance - amount - amount (double deduction)
```

The same pattern exists in `utilPayment()`. This is a **data integrity bug**.

| Severity | Effort |
|---|---|
| **Critical** | **Small** |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No shared library / duplicated code | Code Organization | High | Large |
| 1.2 | Inconsistent package structure | Code Organization | Medium | Medium |
| 1.3 | Manual mapper instantiation | Code Organization | Low | Small |
| 1.4 | Keycloak singleton anti-pattern | Code Organization | Medium | Small |
| 2.1 | Catch-all returns 400 + leaks exceptions | Error Handling | Critical | Small |
| 2.2 | EntityNotFoundException returns 400 not 404 | Error Handling | High | Small |
| 2.3 | Missing Feign error decoder on some services | Error Handling | High | Small |
| 2.4 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.5 | No error handling around Feign calls | Error Handling | High | Medium |
| 3.1 | Tests only in core-banking-service | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | No test coverage reporting | Testing | Low | Small |
| 4.1 | Hardcoded credentials in Docker Compose | Security | High | Small |
| 4.2 | No input validation | Security | Critical | Small |
| 4.3 | Password logged and returned in response | Security | Critical | Small |
| 4.4 | No rate limiting | Security | Medium | Medium |
| 4.5 | Test credentials in README | Security | Low | Small |
| 4.6 | Raw ResponseEntity without type params | Security | Medium | Small |
| 5.1 | No API versioning strategy | API Design | Low | Small |
| 5.2 | Pagination missing metadata | API Design | High | Small |
| 5.3 | No filtering/sorting support | API Design | Medium | Medium |
| 5.4 | OpenAPI docs incomplete (wrong dependency) | API Design | Medium | Medium |
| 5.5 | Inconsistent resource naming | API Design | Low | Small |
| 5.6 | POST returns 200 instead of 201 | API Design | Low | Small |
| 6.1 | Sensitive data in logs | Observability | High | Small |
| 6.2 | No custom health indicators | Observability | Medium | Medium |
| 6.3 | No custom metrics / Prometheus not wired | Observability | Medium | Medium |
| 6.4 | Tracing coverage unclear | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency protection | Resilience | High | Medium |
| 7.6 | Balance calculation bug (double deduction) | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 6 |
| High | 11 |
| Medium | 11 |
| Low | 7 |
