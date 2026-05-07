# Engineering Standards Gap Analysis

This document compares the current codebase against industry engineering best practices and catalogs gaps with severity ratings and remediation effort estimates.

**Severity Scale:**
- **Critical** — Security vulnerability, data loss risk, or production-breaking issue
- **High** — Significant quality/reliability concern that will cause problems at scale
- **Medium** — Deviation from best practices that increases maintenance burden
- **Low** — Minor improvement opportunity or polish item

**Effort Scale:**
- **Small** — < 1 day per service (config changes, simple additions)
- **Medium** — 1–3 days per service (moderate refactoring, new code)
- **Large** — 3+ days per service (significant architecture or design changes)

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with its own `build.gradle` and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to unify them. This prevents:
- Shared dependency version management
- Single-command builds across all services
- Consistent plugin versions enforced centrally

**Current state:** 6 independent `build.gradle` files with duplicated Spring Boot (`3.2.4`), Spring Cloud (`2023.0.0`), and plugin version declarations.

### GAP-ORG-02: Duplicated Code Across Services

**Severity: Medium | Effort: Medium**

The following classes are duplicated (copy-pasted) across multiple services with minor variations:

| Class | Services |
|---|---|
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` / `AuditConfig` / `AuditorAwareConfig` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact:** Bug fixes or behavior changes must be applied identically across all copies. Divergence is already visible (e.g., `ErrorResponse` uses `@Builder` in some services but a constructor in others).

### GAP-ORG-03: Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package organization varies across services:
- Core Banking: `model.dto`, `model.entity`, `model.mapper`, `repository`
- User Service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`
- Fund Transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.dto.request`, `model.dto.response`
- Utility Payment: `model.rest.request`, `model.rest.response`, `repository` (top-level)

Request/response DTOs are placed in different sub-packages per service (`model.dto.request` vs `model.rest.request`).

### GAP-ORG-04: No Shared Library / Common Module

**Severity: Medium | Effort: Large**

There is no shared library (e.g., `banking-common`) to house cross-cutting concerns: exception classes, audit configuration, Feign configuration, auth filters, mapper base classes, and DTOs shared across services.

---

## 2. Error Handling

### GAP-ERR-01: Generic Exception Catch-All Returns 400 for All Errors

**Severity: High | Effort: Small**

Every service's `GlobalExceptionHandler` catches `Exception.class` and returns **HTTP 400 Bad Request** with a raw string body:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Problems:**
- Server errors (NPE, database failures, timeouts) are reported as 400 instead of 500
- Exception `toString()` is leaked to clients, potentially exposing stack traces and internal details
- No structured error response format for the catch-all handler

### GAP-ERR-02: Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` errors return `ErrorResponse { code, message }`
- Generic exceptions return a raw `String` body
- Fund Transfer Service uses `new ErrorResponse(code, message)` constructor while others use `ErrorResponse.builder()`
- HTTP status codes are not differentiated (everything is 400)

### GAP-ERR-03: Missing HTTP Status Code Differentiation

**Severity: High | Effort: Small**

All custom exceptions return HTTP 400 regardless of the actual error type:
- `EntityNotFoundException` should return **404**
- `InsufficientFundsException` should return **422** (Unprocessable Entity) or **400**
- `UserAlreadyRegisteredException` should return **409** (Conflict)
- Internal errors should return **500**

### GAP-ERR-04: No Feign Error Handling for Core Banking Failures

**Severity: High | Effort: Medium**

Fund Transfer and Utility Payment services call Core Banking via Feign but have no resilient error handling for downstream failures:
- If Core Banking returns an error after the local entity is saved as `PENDING`, the entity remains in `PENDING` state permanently
- No retry logic, circuit breaker, or compensation/rollback mechanism
- `CustomFeignErrorDecoder` exists only in User Service; Fund Transfer and Utility Payment services rely on default Feign error decoding

---

## 3. Testing

### GAP-TEST-01: Minimal Test Coverage

**Severity: High | Effort: Large**

| Service | Unit Tests | Integration Tests | Contract Tests |
|---|---|---|---|
| Core Banking | 3 test classes (AccountService, TransactionService, UserService) | None | None |
| User Service | Context load test only | None | None |
| Fund Transfer | Context load test only | None | None |
| Utility Payment | Context load test only | None | None |
| API Gateway | Context load test only | None | None |
| Config Server | Context load test only | None | None |
| Service Registry | Context load test only | None | None |

Only Core Banking Service has meaningful unit tests. No service has:
- Controller/API-layer tests (MockMvc / WebTestClient)
- Integration tests with real database
- Feign client contract tests (e.g., Spring Cloud Contract or Pact)
- End-to-end tests

### GAP-TEST-02: No Test Coverage Tooling

**Severity: Medium | Effort: Small**

No code coverage tool (JaCoCo) is configured. There is no way to measure or enforce minimum test coverage thresholds.

### GAP-TEST-03: ApplicationTests May Fail Without Infrastructure

**Severity: Low | Effort: Small**

The `*ApplicationTests` classes (annotated `@SpringBootTest`) in services that depend on external infrastructure (Keycloak, Config Server, Eureka) will fail unless those dependencies are available. Some test `application.yml` files exist (core-banking, fund-transfer, user-service, utility-payment) but not all configurations disable external dependencies.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Plaintext credentials are committed to the repository:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`
- `privileges.sql`: `IDENTIFIED BY 'oPItyPticIAt'`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-SEC-02: CSRF Disabled on API Gateway

**Severity: Medium | Effort: Small**

CSRF protection is explicitly disabled in `SecurityConfiguration`:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While acceptable for pure API-only backends using bearer tokens, this should be documented as a deliberate decision. If the gateway ever serves browser-based sessions, this becomes a vulnerability.

### GAP-SEC-03: No Input Validation

**Severity: Critical | Effort: Medium**

None of the request DTOs have any validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Valid`, etc.):
- `FundTransferRequest`: No validation that `fromAccount`, `toAccount` are non-null or that `amount` is positive
- `UtilityPaymentRequest`: No validation on any fields
- `User` registration: No email format validation at the DTO level
- Controller methods don't use `@Valid` annotation

Malformed or missing fields will cause `NullPointerException` or database constraint violations rather than meaningful validation errors.

### GAP-SEC-04: No Rate Limiting

**Severity: High | Effort: Medium**

No rate limiting is configured at the API Gateway or any service level. Financial operations (fund transfers, payments) are fully unthrottled, making the system vulnerable to:
- Brute force attacks on authentication
- Denial of service via rapid fund transfer submissions
- Account balance manipulation through rapid concurrent requests

### GAP-SEC-05: Keycloak Client as Singleton with Static Reference

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static singleton pattern that is not thread-safe:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { keycloakInstance = ... }
}
```
This is a classic double-checked locking bug without synchronization. It also prevents credential rotation without restart.

### GAP-SEC-06: No Dependency Vulnerability Scanning

**Severity: High | Effort: Small**

No dependency vulnerability scanning tool is configured (e.g., OWASP Dependency-Check, Snyk, or Dependabot). There is no automated way to detect known CVEs in the dependency tree.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...) // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)   // should be ResponseEntity<FundTransferResponse>
```

Only User Service's `UserController` uses typed responses (`ResponseEntity<User>`). This reduces API documentation quality and type safety.

### GAP-API-02: No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While endpoints use `/api/v1/` in the path, there is no documented versioning strategy or mechanism to support multiple API versions simultaneously. No content negotiation or header-based versioning is implemented.

### GAP-API-03: Incomplete Pagination Support

**Severity: Medium | Effort: Small**

Paginated endpoints accept Spring's `Pageable` but:
- Return `List<T>` instead of `Page<T>`, discarding pagination metadata (total elements, total pages, page number)
- No standardized pagination response envelope
- Core Banking `UserController.readUsers()` returns `List<User>` despite accepting `Pageable`

### GAP-API-04: No Filtering or Sorting Parameters

**Severity: Low | Effort: Medium**

List endpoints provide no filtering capabilities:
- Cannot filter fund transfers by account, status, or date range
- Cannot filter utility payments by provider or status
- Cannot search users by name or email

### GAP-API-05: OpenAPI/Swagger Dependency Mismatch

**Severity: Low | Effort: Small**

Services include `springdoc-openapi-starter-webflux-ui:2.1.0` but the User Service, Fund Transfer Service, Utility Payment Service, and Core Banking Service use Spring MVC (not WebFlux). The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. Only the API Gateway (which uses WebFlux) should use the webflux variant.

### GAP-API-06: Non-RESTful Endpoint Naming

**Severity: Low | Effort: Small**

Some endpoints deviate from REST conventions:
- `POST /api/v1/bank-users/register` — should be `POST /api/v1/bank-users` (POST to collection implies creation)
- `PATCH /api/v1/bank-users/update/{id}` — should be `PATCH /api/v1/bank-users/{id}` (PATCH method already implies update)

---

## 6. Observability

### GAP-OBS-01: No Structured Logging

**Severity: Medium | Effort: Small**

All services use default Spring Boot logging (plain text format). For production observability:
- No JSON log format configured (needed for log aggregation tools like ELK, Datadog, etc.)
- No consistent log correlation IDs beyond what Micrometer tracing provides
- Log levels are not externalized or configurable at runtime

### GAP-OBS-02: Sensitive Data in Logs

**Severity: High | Effort: Small**

Controllers log full request objects via `toString()`:
```java
log.info("Creating user with {}", request.toString()); // may include password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```
The `User` DTO contains a `password` field that could be logged in plaintext.

### GAP-OBS-03: No Custom Health Checks

**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` is included in all services, no custom health indicators are defined for:
- Database connectivity verification
- Keycloak reachability (User Service)
- Core Banking Service availability (from dependent services)
- Downstream Feign client health

### GAP-OBS-04: No Metrics Endpoints / Prometheus Integration

**Severity: Medium | Effort: Small**

Despite Prometheus being listed in the README's technology stack, no `micrometer-registry-prometheus` dependency is included in any service's `build.gradle`. The `/actuator/prometheus` endpoint is not available.

### GAP-OBS-05: No Centralized Log Aggregation

**Severity: Medium | Effort: Large**

No log aggregation solution (ELK stack, Loki, etc.) is configured in the Docker Compose setup. Logs are only available per-container via `docker logs`.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker pattern is implemented for inter-service calls. If Core Banking Service becomes unresponsive:
- Fund Transfer Service will hang on Feign calls indefinitely (no timeout configured)
- Utility Payment Service will similarly hang
- Thread pools will exhaust, cascading the failure to the API Gateway

Spring Cloud Circuit Breaker (Resilience4j) is not in any service's dependencies.

### GAP-RES-02: No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for Feign clients or any inter-service calls. Transient network failures will immediately fail requests without retry attempts. Spring Retry is not included as a dependency.

### GAP-RES-03: No Timeout Configuration

**Severity: High | Effort: Small**

Feign clients have no explicit timeout configuration:
- No `connectTimeout` or `readTimeout` settings
- Default Feign timeouts may be too long for financial operations
- API Gateway has no route-level timeout configuration

### GAP-RES-04: No Fallback Behavior

**Severity: High | Effort: Medium**

No fallback mechanisms exist for any inter-service communication:
- Fund Transfer Service has no graceful degradation if Core Banking is down
- User Service has no fallback if Keycloak is unreachable
- No cached responses or default values for read operations

### GAP-RES-05: No Idempotency Protection

**Severity: Critical | Effort: Medium**

Financial operations (`fundTransfer`, `utilPayment`) have no idempotency keys:
- Duplicate POST requests (e.g., due to network retries or client double-clicks) will create duplicate transactions
- No deduplication based on request ID or content hash
- The `transactionId` is generated server-side (UUID) and cannot be used for client-side deduplication

### GAP-RES-06: Transaction Inconsistency Risk

**Severity: Critical | Effort: Large**

The fund transfer and utility payment flows span two services without distributed transaction support:
1. Fund Transfer Service saves `PENDING` entity (local DB)
2. Fund Transfer Service calls Core Banking Service (remote HTTP call)
3. If step 2 succeeds but the response is lost (network timeout), the local entity remains `PENDING` while money has already moved in core banking
4. If step 2 fails after Core Banking partially processes, there is no rollback mechanism

This is a classic distributed transaction problem (Saga pattern not implemented).

### GAP-RES-07: Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.utilPayment()` (core-banking-service), the available balance is calculated incorrectly:

```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```

After line 1, `actualBalance` is already reduced. Line 2 subtracts the amount again from the already-reduced `actualBalance`, causing a **double deduction** of available balance. The same bug exists in `internalFundTransfer()`.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | No shared library / common module | Medium | Large |
| GAP-ERR-01 | Error Handling | Generic exception returns 400 for all errors | High | Small |
| GAP-ERR-02 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-03 | Error Handling | Missing HTTP status code differentiation | High | Small |
| GAP-ERR-04 | Error Handling | No Feign error handling for downstream failures | High | Medium |
| GAP-TEST-01 | Testing | Minimal test coverage | High | Large |
| GAP-TEST-02 | Testing | No test coverage tooling | Medium | Small |
| GAP-TEST-03 | Testing | ApplicationTests may fail without infrastructure | Low | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-SEC-02 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-03 | Security | No input validation | Critical | Medium |
| GAP-SEC-04 | Security | No rate limiting | High | Medium |
| GAP-SEC-05 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | High | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-03 | API Design | Incomplete pagination support | Medium | Small |
| GAP-API-04 | API Design | No filtering or sorting parameters | Low | Medium |
| GAP-API-05 | API Design | OpenAPI dependency mismatch (webflux vs webmvc) | Low | Small |
| GAP-API-06 | API Design | Non-RESTful endpoint naming | Low | Small |
| GAP-OBS-01 | Observability | No structured logging | Medium | Small |
| GAP-OBS-02 | Observability | Sensitive data in logs | High | Small |
| GAP-OBS-03 | Observability | No custom health checks | Medium | Small |
| GAP-OBS-04 | Observability | No Prometheus metrics integration | Medium | Small |
| GAP-OBS-05 | Observability | No centralized log aggregation | Medium | Large |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | High | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | Critical | Medium |
| GAP-RES-06 | Resilience | Transaction inconsistency risk (no Saga) | Critical | Large |
| GAP-RES-07 | Resilience | Balance calculation bug (double deduction) | Critical | Small |
