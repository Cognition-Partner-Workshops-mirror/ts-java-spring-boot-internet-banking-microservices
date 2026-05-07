# Engineering Standards Gap Analysis

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Matrix](#8-summary-matrix)

---

## 1. Code Organization

### GAP-CO-01: No Multi-Module Gradle Build

**Severity:** Medium | **Effort:** Medium

Each microservice is an independent Gradle project with its own `gradlew` wrapper and `build.gradle`. There is no parent/root `settings.gradle` that aggregates all modules. This leads to:
- Duplicated dependency versions across 7 `build.gradle` files
- No central BOM or version catalog for dependency management
- Difficulty performing project-wide builds, checks, or upgrades

**Expected:** A root `settings.gradle.kts` including all services, with a shared `buildSrc` or Gradle version catalog for dependency management.

### GAP-CO-02: Duplicated Boilerplate Code Across Services

**Severity:** Medium | **Effort:** Medium

The following classes are copy-pasted (with minor variations) across `user-service`, `fund-transfer-service`, and `utility-payment-service`:

| Duplicated Class | Copies |
|---|---|
| `BaseMapper<E, D>` | 4 copies (core-banking, user, fund-transfer, utility-payment) |
| `AuditAware` (MappedSuperclass) | 3 copies (user, fund-transfer, utility-payment) |
| `AuditConfig` + `AuditorAwareConfig` | 3 copies |
| `ApiRequestContext` + `ApiRequestContextHolder` + `AppAuthUserFilter` | 3 copies |
| `SimpleBankingGlobalException` | 3 copies (user, fund-transfer, utility-payment — with different field names) |
| `ErrorResponse` | 2 copies |

**Expected:** Shared library module (e.g., `banking-commons`) published as a local dependency.

### GAP-CO-03: Inconsistent Package Structure

**Severity:** Low | **Effort:** Small

Services use slightly different package layouts:

| Service | Exception Package | Feign Package | Repository Package |
|---------|------------------|---------------|-------------------|
| core-banking | `exception` | N/A | `repository` |
| user-service | `exception` | `configuration.feign` | `model.repository` |
| fund-transfer | `exception` | `service.rest.client` | `model.repository` |
| utility-payment | No exception pkg | `service.rest` | `model.repository` |

**Expected:** Consistent package naming across all services (e.g., `controller`, `service`, `repository`, `model.entity`, `model.dto`, `exception`, `client`, `config`).

### GAP-CO-04: Mappers Instantiated Inline Instead of as Spring Beans

**Severity:** Low | **Effort:** Small

All mapper instances are created with `new` directly in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans or using a mapping framework.

**Expected:** Use MapStruct (compile-time mapping) or register mappers as `@Component` beans for testability and consistency.

---

## 2. Error Handling

### GAP-EH-01: Inconsistent Exception Hierarchies

**Severity:** High | **Effort:** Medium

Each service defines its own exception hierarchy with inconsistencies:

| Service | Base Exception | Error Codes | HTTP Status |
|---------|---------------|-------------|-------------|
| core-banking | `EntityNotFoundException`, `InsufficientFundsException` (both extend `RuntimeException`) | `GlobalErrorCode` class with static strings | Returns `ErrorResponse` (code + message) |
| user-service | `SimpleBankingGlobalException` → `EntityNotFoundException`, `UserAlreadyRegisteredException`, `InvalidEmailException`, `InvalidBankingUserException` | `GlobalErrorCode` class | `ErrorResponse` (code + message) |
| fund-transfer | `SimpleBankingGlobalException` (no subclasses) | Free-form string codes | `ErrorResponse` (code + message) |
| utility-payment | **No exception classes at all** | — | — |

**Expected:** Shared exception hierarchy in a common library with consistent error codes and response format.

### GAP-EH-02: All Exceptions Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

Both `GlobalExceptionHandler` implementations (user-service, fund-transfer-service) return `400 Bad Request` for every exception:

```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

Issues:
- `EntityNotFoundException` should return `404 Not Found`
- `InsufficientFundsException` should return `422 Unprocessable Entity`
- Generic `Exception` handler leaks stack traces to the client (`"Exception occur inside API " + e`)
- No handler for `MethodArgumentNotValidException`, `ConstraintViolationException`, etc.

**Expected:** Proper HTTP status codes per exception type; never expose internal exception details.

### GAP-EH-03: No Error Handling in Core Banking Service

**Severity:** High | **Effort:** Small

The `core-banking-service` has **no `@ControllerAdvice` or `GlobalExceptionHandler`**. Uncaught exceptions bubble up as Spring Boot's default `WhitelabelErrorPage` or a raw 500 with stack trace.

**Expected:** Add a `@ControllerAdvice` with handlers for `EntityNotFoundException`, `InsufficientFundsException`, and generic fallback.

### GAP-EH-04: No Exception Handler in Utility Payment Service

**Severity:** Medium | **Effort:** Small

The `utility-payment-service` has no exception classes and no `@ControllerAdvice`. All errors return Spring Boot defaults.

### GAP-EH-05: Feign Error Handling Only in User Service

**Severity:** Medium | **Effort:** Small

Only `user-service` implements a custom `ErrorDecoder` for Feign (`CustomFeignErrorDecoder`). The `fund-transfer-service` and `utility-payment-service` use the default Feign error decoder, which wraps all failures as `FeignException` — these are not caught by the `GlobalExceptionHandler` and result in 500 errors with Feign stack traces.

**Expected:** All services using Feign clients should implement custom error decoders that translate upstream errors into appropriate downstream responses.

### GAP-EH-06: Raw `ResponseEntity` Without Type Parameters

**Severity:** Low | **Effort:** Small

Many controller methods return `ResponseEntity` without generics (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This suppresses compile-time type checking and makes the API contract less clear.

---

## 3. Testing

### GAP-TE-01: Near-Zero Test Coverage Outside Core Banking

**Severity:** Critical | **Effort:** Large

| Service | Unit Tests | Integration Tests | Test Classes |
|---------|-----------|------------------|--------------|
| core-banking-service | 18 tests (AccountService, TransactionService, UserService) | 0 | 4 |
| user-service | 0 | 0 | 1 (contextLoads only) |
| fund-transfer-service | 0 | 0 | 1 (contextLoads only) |
| utility-payment-service | 0 | 0 | 1 (contextLoads only) |
| api-gateway | 0 | 0 | 1 (contextLoads only) |
| service-registry | 0 | 0 | 1 (contextLoads only) |
| config-server | 0 | 0 | 1 (contextLoads only) |

5 out of 7 services have **zero meaningful tests**.

**Expected:** Minimum 80% unit test coverage on business logic; integration tests for each controller; contract tests for Feign client interfaces.

### GAP-TE-02: No Integration Tests

**Severity:** High | **Effort:** Large

No `@SpringBootTest` with `@AutoConfigureMockMvc` or `WebTestClient` tests exist for any controller. The existing `@SpringBootTest` tests only verify context loading.

**Expected:** Controller-level integration tests using MockMvc or WebTestClient with test slices (`@WebMvcTest`, `@DataJpaTest`).

### GAP-TE-03: No Contract Tests for Inter-Service Communication

**Severity:** High | **Effort:** Large

Three services depend on `core-banking-service` via Feign clients. There are no Pact, Spring Cloud Contract, or WireMock-based contract tests to verify that the Feign client interfaces match the provider's actual API.

**Expected:** Consumer-driven contract tests or at minimum WireMock-based tests for all Feign clients.

### GAP-TE-04: Context Load Tests Likely Fail Without Infrastructure

**Severity:** Medium | **Effort:** Small

The `@SpringBootTest` context-load tests in user-service, fund-transfer-service, and utility-payment-service will fail without Keycloak, Config Server, and MySQL running because they don't mock external dependencies or use `@SpringBootTest(webEnvironment = NONE)` with test profiles.

**Expected:** Test profiles that replace external dependencies with mocks or test containers.

### GAP-TE-05: No Test Coverage Reporting

**Severity:** Medium | **Effort:** Small

No JaCoCo or similar coverage tool is configured in any `build.gradle` file.

**Expected:** JaCoCo plugin with minimum coverage thresholds enforced at build time.

---

## 4. Security

### GAP-SE-01: Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

Multiple credentials are committed to the repository:

| File | Credential |
|------|-----------|
| `docker-compose/mysql/Dockerfile` | `MYSQL_ROOT_PASSWORD=woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `docker-compose/docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password` |
| `postman_collection/*.json` | Bearer tokens, client secrets (`0efd3e37-258e-4488-96ae-1dfe34679c9d`) |
| `core-banking-service/src/test/resources/application.yml` | `password: password` (test DB — acceptable) |

**Expected:** Use Docker Compose secrets, `.env` files (gitignored), or a secrets manager. Rotate any exposed credentials immediately.

### GAP-SE-02: No Input Validation on Request DTOs

**Severity:** Critical | **Effort:** Small

**No request DTO uses Jakarta Bean Validation annotations** (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.):

```java
// FundTransferRequest — no validation at all
@Data
public class FundTransferRequest {
    private String fromAccount;    // Could be null
    private String toAccount;      // Could be null
    private BigDecimal amount;     // Could be negative, zero, or null
    private String authID;
}
```

This allows:
- Null amounts, negative transfers
- Missing account numbers (NullPointerException instead of 400)
- Transfer from account to itself

**Expected:** All request DTOs annotated with Bean Validation constraints; controllers annotated with `@Valid`/`@Validated`.

### GAP-SE-03: No Authorization Beyond Authentication

**Severity:** High | **Effort:** Medium

The API Gateway enforces that requests are authenticated (valid JWT), but there is **no role-based or resource-based authorization**:
- Any authenticated user can approve other users
- Any authenticated user can view all fund transfers
- Any authenticated user can initiate transfers from any account
- The `X-Auth-Id` header is not validated against the operation being performed

**Expected:** Role-based access control (e.g., admin-only for user approval), ownership checks (user can only access own accounts), method-level security (`@PreAuthorize`).

### GAP-SE-04: Internal Services Exposed Without Authentication

**Severity:** High | **Effort:** Medium

The `core-banking-service` has no Spring Security dependency — it trusts any request. In Docker Compose, it's on the same network as the gateway but has a published port (8092), meaning it can be accessed directly, bypassing the gateway's OAuth2 enforcement.

**Expected:** Either remove direct port exposure (internal-only networking) or add Spring Security to all downstream services.

### GAP-SE-05: CSRF Disabled Without Justification

**Severity:** Medium | **Effort:** Small

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF is disabled at the gateway. For a pure API (no browser sessions), this is acceptable if the application uses Bearer tokens exclusively. However, Keycloak session cookies could be in play.

**Expected:** Document the justification for CSRF being disabled; consider enabling CSRF for cookie-based authentication flows.

### GAP-SE-06: Keycloak Singleton Not Thread-Safe

**Severity:** Medium | **Effort:** Small

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

This lazy singleton is not thread-safe. Under concurrent requests, multiple Keycloak instances could be created.

**Expected:** Use `@Bean` with Spring's singleton scope, or use `synchronized`/double-checked locking/`AtomicReference`.

### GAP-SE-07: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar vulnerability scanner is configured in the build.

**Expected:** Add `dependency-check-gradle` plugin or equivalent.

---

## 5. API Design

### GAP-AD-01: Inconsistent Endpoint Naming Conventions

**Severity:** Medium | **Effort:** Small

| Issue | Example |
|-------|---------|
| Snake_case path variable | `/api/v1/account/bank-account/{account_number}` |
| Mixed kebab/camel naming | `/util-account/{account_name}` vs `/bank-account/{account_number}` |
| No consistent resource naming | `/api/v1/transfer` (noun) vs `/api/v1/transaction/fund-transfer` (verb-like) |

**Expected:** Consistent kebab-case or camelCase for path parameters; resource-oriented nouns for endpoints.

### GAP-AD-02: No Pagination Metadata in Responses

**Severity:** Medium | **Effort:** Small

List endpoints accept `Pageable` parameters but return plain `List<T>` instead of a `Page<T>` response:

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

Clients receive no information about total pages, total elements, or current page.

**Expected:** Return `Page<T>` or a custom wrapper with pagination metadata.

### GAP-AD-03: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

All endpoints use `/api/v1/` but there's no mechanism for version negotiation (header-based, content-type, etc.) and no documentation about what constitutes a breaking change.

**Expected:** Documented versioning strategy; consider URL-based (current) with clear deprecation policy.

### GAP-AD-04: No Filtering or Sorting on List Endpoints

**Severity:** Low | **Effort:** Medium

List endpoints only support pagination. There's no filtering (e.g., by status, date range, account number) or explicit sorting configuration.

**Expected:** Query parameter-based filtering for key fields; Spring Data's `Sort` parameter support.

### GAP-AD-05: Missing HTTP Methods for CRUD Operations

**Severity:** Low | **Effort:** Small

- No `DELETE` endpoint for any entity
- No `PUT` endpoint for full updates (only `PATCH` for user status)
- Core banking service has no create/update endpoints for accounts or users (data loaded via SQL migration only)

**Expected:** Full CRUD where business logic permits, or documentation of why operations are excluded.

### GAP-AD-06: OpenAPI Annotations Incomplete

**Severity:** Low | **Effort:** Small

While `springdoc-openapi-starter-webflux-ui` is included, the annotations are minimal:
- `@Tag` and `@Operation` present on some controllers
- No `@ApiResponse`, `@Schema`, or `@Parameter` annotations
- No documented error response schemas

**Expected:** Complete OpenAPI annotations including response schemas, error codes, and examples.

---

## 6. Observability

### GAP-OB-01: Inconsistent Logging Patterns

**Severity:** Medium | **Effort:** Small

Logging inconsistencies across services:

| Issue | Example |
|-------|---------|
| String concatenation instead of parameterized logging | `log.info("Sending fund transfer request {}" + request.toString())` — the `{}` is not used as a placeholder |
| Exception swallowed with `toString()` | `log.error("IO Exception on reading exception message feign client" + e)` — loses stack trace |
| Sensitive data logged | `log.info("Creating user with {}", request.toString())` — may log passwords |
| No structured logging format | All services use default Spring Boot logging (not JSON) |

**Expected:** Parameterized logging throughout; JSON structured log format for machine parsing; sensitive field masking.

### GAP-OB-02: No Custom Health Checks

**Severity:** Medium | **Effort:** Small

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Database connectivity readiness
- Keycloak reachability
- Feign client target availability
- Downstream service health

**Expected:** Custom `HealthIndicator` beans for critical dependencies.

### GAP-OB-03: No Metrics Endpoints or Dashboards

**Severity:** Medium | **Effort:** Medium

While Micrometer is present (via tracing), there are no:
- Custom business metrics (transfer count, payment amounts, failure rates)
- Prometheus endpoint export (`micrometer-registry-prometheus` not included)
- Grafana dashboards or alerting rules

**Expected:** Prometheus metrics endpoint; custom counters/gauges for business KPIs; pre-built dashboards.

### GAP-OB-04: No Correlation ID Propagation

**Severity:** Medium | **Effort:** Small

While Zipkin trace IDs propagate through Feign calls, there's no explicit correlation/request ID in HTTP responses. Clients cannot correlate their request to server-side traces.

**Expected:** Return `X-Request-Id` or `X-Trace-Id` response headers for all requests.

### GAP-OB-05: Actuator Endpoints Unprotected

**Severity:** Medium | **Effort:** Small

The gateway permits all `/actuator/**` endpoints without authentication. While health checks should be public, endpoints like `/actuator/env`, `/actuator/configprops`, `/actuator/beans` may expose sensitive configuration.

**Expected:** Only expose `/actuator/health` and `/actuator/info` publicly; restrict others to authorized access or disable them.

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

**Severity:** High | **Effort:** Medium

No circuit breaker is configured on any Feign client or HTTP call. If `core-banking-service` goes down, all dependent services will hang on synchronous HTTP calls until TCP timeout.

**Expected:** Resilience4j circuit breaker on all Feign clients with defined failure thresholds, open-state duration, and fallback methods.

### GAP-RE-02: No Retry Policies

**Severity:** High | **Effort:** Small

No retry configuration exists for Feign clients or any HTTP communication. Transient failures (network blips, 503s) are not retried.

**Expected:** Retry policies with exponential backoff for idempotent operations (`GET` requests); no retry for non-idempotent operations (`POST`) without idempotency keys.

### GAP-RE-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit timeouts are configured for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Spring Cloud Gateway route timeouts

Default timeouts (often infinite or very long) can cause thread pool exhaustion under failure conditions.

**Expected:** Explicit connection and read timeouts on all HTTP clients; database pool max-wait configuration; gateway route-level timeouts.

### GAP-RE-04: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

When downstream services fail, there are no graceful degradation patterns:
- No fallback methods on Feign clients
- No cached responses for read operations
- No queued retries for write operations

**Expected:** Fallback methods for non-critical read operations; error responses that indicate degraded functionality.

### GAP-RE-05: Fund Transfer Not Idempotent

**Severity:** High | **Effort:** Medium

The `POST /api/v1/transfer` endpoint has no idempotency key. If a client retries a timed-out request (or Feign retries internally), the transfer may be executed twice:

1. `fund-transfer-service` saves with `PENDING` → calls core-banking → times out
2. Core-banking actually succeeds and debits/credits
3. Client retries → second transfer created → core-banking debits/credits again

**Expected:** Idempotency key in the request; check for duplicate `transactionReference` before processing.

### GAP-RE-06: No Rate Limiting at the Gateway

**Severity:** Medium | **Effort:** Small

The API Gateway has no rate limiting configured. A misbehaving client or attack can overwhelm downstream services.

**Expected:** Spring Cloud Gateway `RequestRateLimiter` filter with Redis backing.

### GAP-RE-07: Balance Update Race Condition

**Severity:** High | **Effort:** Medium

The `TransactionService.internalFundTransfer()` method reads an account, modifies the balance in memory, and saves it back without pessimistic locking:

```java
BankAccountEntity fromBankAccountEntity = bankAccountRepository.findByNumber(...).get();
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
bankAccountRepository.save(fromBankAccountEntity);
```

Concurrent transfers from the same account can lead to lost updates (both reads $100, both subtract $50, both write $50 instead of final $0). The `@Transactional` annotation uses the default isolation level (typically `READ_COMMITTED`), which does not prevent this.

**Expected:** Pessimistic locking (`@Lock(PESSIMISTIC_WRITE)`) on account reads, or optimistic locking with `@Version` on `BankAccountEntity`, or database-level `UPDATE ... SET balance = balance - ?` queries.

### GAP-RE-08: Available Balance Double-Subtraction Bug

**Severity:** High | **Effort:** Small

In `TransactionService.internalFundTransfer()` and `utilPayment()`:

```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
```

The second line subtracts `amount` from the **already-reduced** `actualBalance`, resulting in `availableBalance = actualBalance - 2*amount`. This is a functional bug.

**Expected:** `setAvailableBalance(fromAccount.getActualBalance())` (or a separate business rule for holds).

---

## 8. Summary Matrix

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-CO-01 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-CO-02 | Code Organization | Duplicated boilerplate across services | Medium | Medium |
| GAP-CO-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-CO-04 | Code Organization | Mappers not Spring-managed | Low | Small |
| GAP-EH-01 | Error Handling | Inconsistent exception hierarchies | High | Medium |
| GAP-EH-02 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-EH-03 | Error Handling | No error handling in core-banking-service | High | Small |
| GAP-EH-04 | Error Handling | No error handling in utility-payment-service | Medium | Small |
| GAP-EH-05 | Error Handling | Feign error handling only in user-service | Medium | Small |
| GAP-EH-06 | Error Handling | Raw ResponseEntity without type parameters | Low | Small |
| GAP-TE-01 | Testing | Near-zero test coverage (5/7 services untested) | Critical | Large |
| GAP-TE-02 | Testing | No integration tests | High | Large |
| GAP-TE-03 | Testing | No contract tests for Feign clients | High | Large |
| GAP-TE-04 | Testing | Context load tests fail without infra | Medium | Small |
| GAP-TE-05 | Testing | No test coverage reporting | Medium | Small |
| GAP-SE-01 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-SE-02 | Security | No input validation on request DTOs | Critical | Small |
| GAP-SE-03 | Security | No authorization beyond authentication | High | Medium |
| GAP-SE-04 | Security | Internal services exposed without auth | High | Medium |
| GAP-SE-05 | Security | CSRF disabled without justification | Medium | Small |
| GAP-SE-06 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SE-07 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-AD-01 | API Design | Inconsistent endpoint naming | Medium | Small |
| GAP-AD-02 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-AD-03 | API Design | No API versioning strategy | Medium | Medium |
| GAP-AD-04 | API Design | No filtering or sorting on lists | Low | Medium |
| GAP-AD-05 | API Design | Missing HTTP methods for CRUD | Low | Small |
| GAP-AD-06 | API Design | OpenAPI annotations incomplete | Low | Small |
| GAP-OB-01 | Observability | Inconsistent logging patterns | Medium | Small |
| GAP-OB-02 | Observability | No custom health checks | Medium | Small |
| GAP-OB-03 | Observability | No metrics endpoints or dashboards | Medium | Medium |
| GAP-OB-04 | Observability | No correlation ID propagation | Medium | Small |
| GAP-OB-05 | Observability | Actuator endpoints unprotected | Medium | Small |
| GAP-RE-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RE-02 | Resilience | No retry policies | High | Small |
| GAP-RE-03 | Resilience | No timeout configuration | High | Small |
| GAP-RE-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RE-05 | Resilience | Fund transfer not idempotent | High | Medium |
| GAP-RE-06 | Resilience | No rate limiting at gateway | Medium | Small |
| GAP-RE-07 | Resilience | Balance update race condition | High | Medium |
| GAP-RE-08 | Resilience | Available balance double-subtraction bug | High | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 3 |
| High | 14 |
| Medium | 17 |
| Low | 5 |
| **Total** | **39** |
