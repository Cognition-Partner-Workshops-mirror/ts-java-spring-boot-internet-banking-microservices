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

### 1.1 GAP: No Shared Library / Multi-Module Build

**Severity:** Medium | **Effort:** Medium

**Observation:** Each service is an independent Gradle project with duplicated code. The following classes are copy-pasted across 3+ services with minor variations:

- `AuditAware` (user-service, fund-transfer-service, utility-payment-service)
- `BaseMapper<E, D>` (core-banking, user-service, fund-transfer-service, utility-payment-service)
- `ErrorResponse` (all 4 business services)
- `SimpleBankingGlobalException` (all 4 business services)
- `GlobalExceptionHandler` (all 4 business services - with inconsistent implementations)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (user-service, fund-transfer-service, utility-payment-service)
- `AccountResponse` (fund-transfer-service, utility-payment-service)
- `CustomFeignClientConfiguration` (user-service, fund-transfer-service, utility-payment-service)

**Best practice:** Extract shared code into a `common` or `shared-lib` Gradle module and use a Gradle multi-module build (root `settings.gradle` with `include`).

### 1.2 GAP: Inconsistent Package Structure Across Services

**Severity:** Low | **Effort:** Small

**Observation:** Services use slightly different package layouts:

| Concern | core-banking | user-service | fund-transfer | utility-payment |
|---------|-------------|-------------|--------------|----------------|
| Repository package | `repository` | `model.repository` | `model.repository` | `repository` |
| Feign client package | N/A | `service.rest` | `service.rest.client` | `service.rest` |
| Request DTO package | `model.dto.request` | `model.dto` | `model.dto.request` | `model.rest.request` |
| Response DTO package | `model.dto.response` | `model.rest.response` | `model.dto.response` | `model.rest.response` |

**Best practice:** Standardize on a single package structure convention and document it.

### 1.3 GAP: Mapper Instantiation Pattern

**Severity:** Low | **Effort:** Small

**Observation:** Mappers are instantiated with `new` inside `@Service` classes rather than being Spring beans:

```java
private UserMapper userMapper = new UserMapper();  // AccountService, UserService, etc.
```

This bypasses Spring's dependency injection and makes testing harder. In services using `@RequiredArgsConstructor`, these fields are not injected by the constructor.

**Best practice:** Register mappers as `@Component` beans or use MapStruct with Spring integration.

---

## 2. Error Handling

### 2.1 GAP: All Errors Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

**Observation:** Every `GlobalExceptionHandler` across all services returns `ResponseEntity.badRequest()` (HTTP 400) for all exceptions, including:

- Entity not found (should be **404**)
- Insufficient funds (could be **422** Unprocessable Entity)
- Generic exceptions (should be **500**)
- User already registered (could be **409** Conflict)

```java
// Every service does this:
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Best practice:** Map exceptions to semantically correct HTTP status codes (404, 409, 422, 500, etc.).

### 2.2 GAP: Exception Details Leaked to Clients

**Severity:** Critical | **Effort:** Small

**Observation:** The generic exception handler concatenates the full exception object into the response body:

```java
.body("Exception occur inside API " + e);
```

This exposes internal stack traces, class names, database details, and potentially sensitive information to API consumers.

**Best practice:** Return a generic error message for unhandled exceptions. Log the full stack trace server-side. Never expose internal exception details.

### 2.3 GAP: Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

**Observation:** 
- Business exceptions (`SimpleBankingGlobalException`) return structured `ErrorResponse { code, message }`.
- Generic exceptions return a plain string: `"Exception occur inside API ..."`.
- Fund-transfer-service uses `new ErrorResponse(code, message)` while others use `ErrorResponse.builder().code(...).message(...).build()`.

**Best practice:** All error responses should use the same structured format with consistent fields (`code`, `message`, `timestamp`, `path`).

### 2.4 GAP: Missing Raw Type Parameterization in ResponseEntity

**Severity:** Low | **Effort:** Small

**Observation:** Most controller methods and exception handlers return raw `ResponseEntity` instead of parameterized `ResponseEntity<?>` or `ResponseEntity<ErrorResponse>`. This suppresses compile-time type checking.

**Best practice:** Always parameterize `ResponseEntity<T>` with the concrete response type.

---

## 3. Testing

### 3.1 GAP: Minimal Test Coverage - Only One Service Has Unit Tests

**Severity:** Critical | **Effort:** Large

**Observation:**
- **core-banking-service:** Has 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with ~20 test methods covering service-layer logic. Uses Mockito for mocking.
- **internet-banking-user-service:** Only has an empty `ApplicationTests` class (context load test, which will fail without infrastructure).
- **internet-banking-fund-transfer-service:** Only has an empty `ApplicationTests` class.
- **internet-banking-utility-payment-service:** Only has an empty `ApplicationTests` class.
- **internet-banking-api-gateway:** Only has an empty `ApplicationTests` class.
- **internet-banking-config-server:** Only has an empty `ApplicationTests` class.
- **internet-banking-service-registry:** Only has an empty `ApplicationTests` class.

No controller-layer tests, no integration tests, and no contract tests exist.

**Best practice:** Each service should have unit tests for services and controllers, integration tests for repository and API layers, and contract tests (e.g., Spring Cloud Contract or Pact) for inter-service communication.

### 3.2 GAP: No Integration Tests

**Severity:** High | **Effort:** Large

**Observation:** There are no `@SpringBootTest` integration tests, no Testcontainers setup, and no `@WebMvcTest` controller tests. The existing empty `ApplicationTests` classes will fail on load because they require external infrastructure (Eureka, Config Server, MySQL).

**Best practice:** Use `@SpringBootTest` with H2 or Testcontainers for integration tests. Use `@WebMvcTest` for controller slice tests. Configure test profiles to disable Eureka and Config Server dependencies.

### 3.3 GAP: No Contract Tests Between Services

**Severity:** High | **Effort:** Medium

**Observation:** Services communicate via Feign clients but there are no consumer-driven contract tests to verify that API changes in core-banking-service don't break downstream consumers.

**Best practice:** Implement Spring Cloud Contract or Pact tests for all Feign client interfaces.

### 3.4 GAP: Test Configuration Incomplete

**Severity:** Medium | **Effort:** Small

**Observation:** Only core-banking-service has a `test/resources/application.yml` with H2 config. The other 3 services with databases (user, fund-transfer, utility-payment) have either no test config or incomplete configs that still reference infrastructure services.

**Best practice:** Each service should have a self-contained test configuration with embedded database and disabled external dependencies.

---

## 4. Security

### 4.1 GAP: Hard-Coded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

**Observation:** Multiple credentials are committed to the repository:

| Location | Credential |
|----------|-----------|
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Best practice:** Use Docker secrets, environment variable files (`.env` in `.gitignore`), or a secrets manager. Never commit real passwords to version control.

### 4.2 GAP: No Input Validation on Request Bodies

**Severity:** Critical | **Effort:** Small

**Observation:** No controller uses `@Valid` or Jakarta Bean Validation annotations. Request DTOs have no validation constraints:

```java
// FundTransferRequest - no validation
@Data
public class FundTransferRequest {
    private String fromAccount;   // Could be null
    private String toAccount;     // Could be null
    private BigDecimal amount;    // Could be null, zero, or negative
}
```

This applies to all request DTOs across all services: `FundTransferRequest`, `UtilityPaymentRequest`, `User` (registration), `UserUpdateRequest`.

**Best practice:** Add `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc. to all request fields. Add `@Valid` to controller parameters. Add `spring-boot-starter-validation` dependency.

### 4.3 GAP: Keycloak Singleton is Not Thread-Safe

**Severity:** High | **Effort:** Small

**Observation:** `KeycloakProperties.getInstance()` uses a non-synchronized singleton pattern:

```java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {   // Race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

In a multi-threaded web application, this can lead to multiple Keycloak instances being created or partially-initialized instances being used.

**Best practice:** Use `@Bean` in a `@Configuration` class, or use `synchronized` / `volatile` / double-checked locking / `AtomicReference`.

### 4.4 GAP: CSRF Disabled Without Documentation

**Severity:** Medium | **Effort:** Small

**Observation:** CSRF protection is explicitly disabled in the API Gateway:

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While this is common for stateless REST APIs using JWT, there is no documentation explaining this decision.

**Best practice:** Document security decisions. Consider CSRF protection if the API is accessed by browser-based clients with session cookies.

### 4.5 GAP: No Authorization Beyond Authentication

**Severity:** High | **Effort:** Medium

**Observation:** The API Gateway authenticates all requests (except registration and actuator), but there is no role-based access control (RBAC). Any authenticated user can:
- Approve other users (`PATCH /update/{id}`)
- Transfer funds from any account
- View all users and transactions

The `X-Auth-Id` header is propagated but never used to authorize operations.

**Best practice:** Implement role-based access control. Verify that the authenticated user owns the accounts they are operating on. Restrict admin operations (user approval) to admin roles.

### 4.6 GAP: Dependency Vulnerabilities Not Monitored

**Severity:** Medium | **Effort:** Small

**Observation:** No dependency scanning tools are configured (OWASP Dependency Check, Snyk, Dependabot). The project uses specific versions of MySQL connector (`8.4.0`), Keycloak admin client (`24.0.4`), and other libraries without automated vulnerability monitoring.

**Best practice:** Add OWASP Dependency Check Gradle plugin or enable GitHub Dependabot/Snyk for automated vulnerability scanning.

---

## 5. API Design

### 5.1 GAP: Inconsistent REST Conventions

**Severity:** Medium | **Effort:** Small

**Observation:**
- Mixed path naming: snake_case (`/bank-account/{account_number}`, `/util-account/{account_name}`) vs. kebab-case paths.
- `POST /register` under `/bank-users` instead of just `POST /bank-users`.
- `PATCH /update/{id}` is redundant (`PATCH /bank-users/{id}` would suffice).
- Fund transfer uses `POST /api/v1/transfer` (no sub-resource) but lists use `GET /api/v1/transfer`.
- Utility payment uses `POST /api/v1/utility-payment` and `GET /api/v1/utility-payment`.

**Best practice:** Adopt consistent RESTful resource naming. Use nouns for resources, HTTP methods for actions. Standardize on kebab-case for path segments.

### 5.2 GAP: No Pagination Metadata in Responses

**Severity:** Medium | **Effort:** Small

**Observation:** List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. The response contains no pagination metadata (total elements, total pages, current page, page size).

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

**Best practice:** Return `Page<T>` or a wrapper containing `{ content, totalElements, totalPages, number, size }`.

### 5.3 GAP: No API Versioning Strategy

**Severity:** Low | **Effort:** Small

**Observation:** All endpoints use `/api/v1/` but there is no documented versioning strategy, no mechanism for deprecation, and no headers for version negotiation.

**Best practice:** Document the API versioning strategy and establish a deprecation policy.

### 5.4 GAP: No Filtering or Sorting on List Endpoints

**Severity:** Low | **Effort:** Medium

**Observation:** List endpoints only support pagination via Spring's `Pageable`. There is no support for filtering (e.g., transfers by account, payments by status) or explicit sort parameters.

**Best practice:** Add query parameters for common filters and document them in OpenAPI specs.

### 5.5 GAP: OpenAPI Documentation Incomplete

**Severity:** Medium | **Effort:** Small

**Observation:** The `springdoc-openapi-starter-webflux-ui` dependency is included in core-banking, user-service, fund-transfer, and utility-payment services. Basic `@Tag` and `@Operation` annotations exist. However:
- Controller return types are raw `ResponseEntity` (not parameterized), so Swagger cannot infer response schemas.
- No `@ApiResponse` annotations documenting error responses.
- No `@Schema` annotations on DTOs.
- The webflux UI dependency is used in non-reactive (servlet) services, which is incorrect (should be `springdoc-openapi-starter-webmvc-ui`).

**Best practice:** Use the correct springdoc artifact. Parameterize `ResponseEntity<T>`. Add `@ApiResponse` and `@Schema` annotations.

---

## 6. Observability

### 6.1 GAP: No Structured Logging

**Severity:** Medium | **Effort:** Medium

**Observation:** All logging uses unstructured string interpolation:

```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Reading account by ID {}", accountNumber);
log.info("Incoming Request From {}", userAuthId);
```

There is no consistent log format, no correlation IDs in log messages (despite Micrometer Tracing being configured), and no JSON log output for production.

**Best practice:** Configure structured JSON logging (e.g., Logstash Logback Encoder). Include traceId/spanId in log output. Standardize log levels and message formats.

### 6.2 GAP: Health Checks Are Default Only

**Severity:** Low | **Effort:** Small

**Observation:** Actuator is included in all services, providing `/actuator/health`. However, no custom health indicators exist for:
- Database connectivity
- Keycloak connectivity (user-service)
- Feign client targets (downstream service availability)
- Config Server connectivity

**Best practice:** Add custom health indicators for critical dependencies. Configure `management.endpoint.health.show-details=always` for detailed health info.

### 6.3 GAP: No Metrics Endpoints Configured

**Severity:** Medium | **Effort:** Small

**Observation:** Micrometer is included via `spring-boot-starter-actuator` but no Prometheus endpoint is exposed (despite Prometheus being listed in the tech stack in README). There are no custom business metrics (e.g., transfer count, payment amounts, error rates).

**Best practice:** Add `micrometer-registry-prometheus` and expose `/actuator/prometheus`. Define custom metrics for key business operations.

### 6.4 GAP: Distributed Tracing Configuration Incomplete

**Severity:** Medium | **Effort:** Small

**Observation:** Tracing dependencies (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) are present in all services, but:
- No tracing sampling rate is configured (defaults to 10%).
- No span customization or additional instrumentation.
- Trace IDs are not included in HTTP response headers for debugging.
- No tracing configuration in `application.yml` (relies entirely on Config Server).

**Best practice:** Configure 100% sampling for non-production environments. Include trace IDs in response headers. Add custom spans for business operations.

### 6.5 GAP: Sensitive Data in Log Output

**Severity:** High | **Effort:** Small

**Observation:** Request objects are logged with `toString()`, which may include sensitive data:

```java
log.info("Creating user with {}", request.toString());  // May log passwords
log.info("Utility payment processing {}", paymentRequest.toString());  // Logs account numbers
```

The `User` DTO includes a `password` field that could be logged.

**Best practice:** Implement `@ToString.Exclude` on sensitive fields. Use dedicated log DTOs that strip sensitive data. Never log passwords or full account numbers.

---

## 7. Resilience

### 7.1 GAP: No Circuit Breakers

**Severity:** High | **Effort:** Medium

**Observation:** All inter-service communication via OpenFeign has no circuit breaker configuration. If core-banking-service goes down, all dependent services will block on HTTP connections until timeout, potentially cascading the failure.

**Best practice:** Add Spring Cloud Circuit Breaker (Resilience4j) with Feign integration. Configure circuit breaker thresholds, fallback methods, and half-open states.

### 7.2 GAP: No Retry Policies

**Severity:** Medium | **Effort:** Small

**Observation:** Feign clients have no retry configuration. Transient network failures (connection resets, DNS blips) immediately result in errors. There is no Spring Retry or Feign Retryer configuration.

**Best practice:** Configure Feign retry with exponential backoff for idempotent GET requests. Avoid retry on non-idempotent POST requests (fund transfers) without idempotency keys.

### 7.3 GAP: No Timeout Configuration

**Severity:** High | **Effort:** Small

**Observation:** No explicit timeouts are configured for:
- Feign client connections and reads (uses defaults, which may be very long or infinite)
- Database connection pool (no HikariCP tuning)
- Keycloak admin client connections

**Best practice:** Set explicit `connectTimeout` and `readTimeout` on all Feign clients. Configure HikariCP connection pool timeouts. Set Keycloak client timeouts.

### 7.4 GAP: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

**Observation:** When any downstream service call fails, the exception propagates directly to the client. There are no fallback strategies:
- If Keycloak is down, user registration fails entirely.
- If core-banking-service is down, fund transfers and payments fail with raw exceptions.
- There is no graceful degradation or cached responses.

**Best practice:** Implement Feign fallback factories. Consider cached account data for read operations. Implement async retry for failed operations.

### 7.5 GAP: No Idempotency Protection on Financial Operations

**Severity:** Critical | **Effort:** Medium

**Observation:** The fund transfer and utility payment endpoints have no idempotency protection. If a client retries a `POST` due to a timeout (where the server actually processed the request), the operation will be executed twice, resulting in double charges.

The fund-transfer-service saves the entity before calling core banking:
```java
FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
```
If the Feign call times out but succeeds on the server side, retrying will create a duplicate transfer.

**Best practice:** Implement idempotency keys (e.g., client-generated UUID in headers). Check for duplicate transaction references before processing. Use optimistic locking or unique constraints.

### 7.6 GAP: Balance Calculation Bug (Double Subtraction/Addition)

**Severity:** Critical | **Effort:** Small

**Observation:** In `TransactionService.internalFundTransfer()`:

```java
// Debit: subtracts amount from actualBalance, then sets availableBalance = actualBalance - amount (double subtract)
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));

// Credit: adds amount to actualBalance, then sets availableBalance = actualBalance + amount (double add)
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
```

After a $100 transfer from an account with $200:
- `actualBalance` = $200 - $100 = **$100** (correct)
- `availableBalance` = $100 - $100 = **$0** (incorrect, should be $100)

The same bug exists in `utilPayment()`.

**Best practice:** Set `availableBalance = actualBalance` after balance update, or track them independently with correct logic.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No shared library / multi-module build | Code Organization | Medium | Medium |
| 1.2 | Inconsistent package structure | Code Organization | Low | Small |
| 1.3 | Mapper instantiation bypasses DI | Code Organization | Low | Small |
| 2.1 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.2 | Exception details leaked to clients | Error Handling | Critical | Small |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | Raw ResponseEntity types | Error Handling | Low | Small |
| 3.1 | Only 1 of 7 services has unit tests | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | High | Medium |
| 3.4 | Test configuration incomplete | Testing | Medium | Small |
| 4.1 | Hard-coded credentials in source | Security | Critical | Small |
| 4.2 | No input validation on requests | Security | Critical | Small |
| 4.3 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.4 | CSRF disabled without documentation | Security | Medium | Small |
| 4.5 | No authorization beyond authentication | Security | High | Medium |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Inconsistent REST conventions | API Design | Medium | Small |
| 5.2 | No pagination metadata in responses | API Design | Medium | Small |
| 5.3 | No API versioning strategy | API Design | Low | Small |
| 5.4 | No filtering or sorting on lists | API Design | Low | Medium |
| 5.5 | OpenAPI documentation incomplete | API Design | Medium | Small |
| 6.1 | No structured logging | Observability | Medium | Medium |
| 6.2 | Health checks are default only | Observability | Low | Small |
| 6.3 | No Prometheus metrics endpoint | Observability | Medium | Small |
| 6.4 | Distributed tracing config incomplete | Observability | Medium | Small |
| 6.5 | Sensitive data in log output | Observability | High | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.2 | No retry policies | Resilience | Medium | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency on financial operations | Resilience | Critical | Medium |
| 7.6 | Balance calculation bug (double subtract) | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 8 |
| Medium | 13 |
| Low | 5 |
| **Total** | **32** |
