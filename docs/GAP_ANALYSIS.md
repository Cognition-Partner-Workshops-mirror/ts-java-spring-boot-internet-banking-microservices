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

### GAP-01: No Shared Library — Duplicated Code Across Services

**Severity: High** | **Effort: Medium**

The following classes are copy-pasted across 3–4 services with near-identical implementations:

| Duplicated Class | Services |
|---|---|
| `BaseMapper<E, D>` | core-banking, fund-transfer, user, utility-payment |
| `AuditAware` (MappedSuperclass) | fund-transfer, user, utility-payment |
| `GlobalExceptionHandler` | core-banking, fund-transfer, user, utility-payment |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, user, utility-payment |
| `ErrorResponse` | core-banking, fund-transfer, user, utility-payment |
| `AppAuthUserFilter` | fund-transfer, user, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | fund-transfer, user, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact**: Bug fixes must be applied in 3–4 places. Divergence is already occurring (e.g., `ErrorResponse` uses `@Builder` in some services and a constructor in others).

**Recommendation**: Extract a `banking-common` shared Gradle module and publish it as an internal dependency.

---

### GAP-02: No Multi-Module Gradle Root Project

**Severity: Medium** | **Effort: Small**

Each service has a completely independent Gradle build. There is no root `settings.gradle` or `build.gradle` to coordinate builds, enforce consistent dependency versions, or run all tests from a single command.

**Impact**: Developers must `cd` into each service directory individually. No single command to build or test the entire system.

**Recommendation**: Create a root Gradle multi-module project with a `settings.gradle` that includes all 6 services and the shared library.

---

### GAP-03: Inconsistent Package Structure Across Services

**Severity: Low** | **Effort: Small**

Package layouts differ between services:

| Pattern | Services |
|---|---|
| `model.repository` | fund-transfer, user |
| `repository` (top-level) | core-banking, utility-payment |
| `service.rest.client` | fund-transfer |
| `service.rest` | user, utility-payment |
| `configuration.feign` | user |
| `configuration` (flat) | fund-transfer, utility-payment |

**Impact**: Developers must relearn package conventions when switching between services.

**Recommendation**: Standardize on a single package structure (e.g., `controller`, `service`, `repository`, `model.entity`, `model.dto`, `configuration`, `exception`).

---

### GAP-04: Mapper Instantiation via `new` Instead of Dependency Injection

**Severity: Low** | **Effort: Small**

All mapper classes are instantiated manually with `new` in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans.

**Impact**: Cannot use dependency injection in mappers; harder to mock in tests; bypasses Spring lifecycle.

**Recommendation**: Annotate mappers with `@Component` and inject via constructor injection, or adopt MapStruct for compile-time mapping.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity: Critical** | **Effort: Small**

Every `GlobalExceptionHandler` across all 4 services maps **all exceptions** (including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception`) to `ResponseEntity.badRequest()` (HTTP 400).

```java
// Every service does this:
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact**: Clients cannot distinguish between "not found" (404), "validation error" (422), "server error" (500), or "insufficient funds" (400). Monitoring tools cannot alert on 5xx errors because none are ever returned.

**Recommendation**: Map exceptions to appropriate HTTP status codes: `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, `InvalidEmailException` → 422, unexpected exceptions → 500.

---

### GAP-06: Generic Exception Handler Leaks Internal Details

**Severity: High** | **Effort: Small**

The catch-all `Exception` handler returns the full exception object as a string: `"Exception occur inside API " + e`. This exposes stack traces, class names, and internal details to API consumers.

**Impact**: Information disclosure vulnerability. Attackers can learn about internal architecture, library versions, and database structure.

**Recommendation**: Return a generic error message for unhandled exceptions. Log the full exception server-side at ERROR level.

---

### GAP-07: No Feign Error Decoder in Fund-Transfer and Utility-Payment Services

**Severity: High** | **Effort: Small**

The `fund-transfer-service` and `utility-payment-service` have **no custom Feign error decoder**. Only `user-service` has a `CustomFeignErrorDecoder`. When core-banking-service returns an error (e.g., insufficient funds), the raw Feign exception propagates up and gets caught by the generic `Exception` handler, losing the original error context.

**Impact**: Clients receive meaningless error messages like `"Exception occur inside API feign.FeignException$BadRequest: [400]..."` instead of the actual business error.

**Recommendation**: Add the `CustomFeignErrorDecoder` (already implemented in user-service) to both fund-transfer and utility-payment services.

---

### GAP-08: No Compensation/Rollback on Feign Failure

**Severity: Critical** | **Effort: Large**

In both `FundTransferService` and `UtilityPaymentService`, if the Feign call to core-banking-service fails **after** the local entity has been saved:
- `fund_transfer` record stays in `PENDING` status forever.
- `utility_payment` record stays in `PROCESSING` status forever.

There is no try-catch around the Feign call, no compensation logic, no retry mechanism, and no scheduled job to clean up stuck records.

**Impact**: Data inconsistency. Orphaned records accumulate. No way for the system to self-heal or for operators to identify and resolve failed transactions.

**Recommendation**: Wrap Feign calls in try-catch, implement compensation logic (mark as FAILED), and add a scheduled job to retry or alert on stuck transactions.

---

### GAP-09: Raw `ResponseEntity` Without Type Parameters

**Severity: Low** | **Effort: Small**

All controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). Only the user-service controller uses typed responses.

**Impact**: No compile-time type safety. OpenAPI/Swagger cannot infer response types, producing incomplete API documentation.

**Recommendation**: Add type parameters to all `ResponseEntity` return types.

---

## 3. Testing

### GAP-10: Minimal Test Coverage — Only 1 of 6 Services Has Tests

**Severity: Critical** | **Effort: Large**

| Service | Test Classes | Test Methods | Coverage |
|---|---|---|---|
| core-banking-service | 3 (service-layer) | 19 | Service layer only — no controller or integration tests |
| fund-transfer-service | 1 (empty `ApplicationTests`) | 0 | None |
| user-service | 1 (empty `ApplicationTests`) | 0 | None |
| utility-payment-service | 1 (empty `ApplicationTests`) | 0 | None |
| api-gateway | 1 (empty `ApplicationTests`) | 0 | None |
| config-server | 1 (empty `ApplicationTests`) | 0 | None |

**Impact**: No safety net for refactoring. Bugs in fund-transfer, user, and utility-payment services (which handle money and user data) are completely unguarded.

**Recommendation**: Add unit tests for all service classes, controller tests with MockMvc, and integration tests with H2 for each service.

---

### GAP-11: No Integration Tests

**Severity: High** | **Effort: Large**

There are no integration tests that exercise the full Spring context, database interactions, or Feign client behavior. The existing core-banking tests are pure unit tests with mocked repositories.

**Impact**: Database queries, JPA mappings, Flyway migrations, and Feign client configurations are never tested.

**Recommendation**: Add `@SpringBootTest` integration tests using H2 and WireMock for Feign client testing.

---

### GAP-12: No Contract Tests Between Services

**Severity: Medium** | **Effort: Large**

No consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) exist between the Feign clients and their target endpoints.

**Impact**: Breaking changes in core-banking-service API are not detected until runtime, risking production failures.

**Recommendation**: Implement Spring Cloud Contract tests between fund-transfer/utility-payment/user services and core-banking-service.

---

### GAP-13: Empty ApplicationTests Will Fail Without Config Server

**Severity: Medium** | **Effort: Small**

The empty `ApplicationTests` classes (annotated with `@SpringBootTest`) in fund-transfer, user, and utility-payment services will fail to load because they try to connect to the Config Server and Eureka during startup.

**Impact**: Cannot run even basic smoke tests without infrastructure running.

**Recommendation**: Add test profiles that disable Config Server and Eureka bootstrap, and configure H2 in-memory databases for testing (already done for core-banking-service — replicate the pattern).

---

## 4. Security

### GAP-14: No Input Validation on Any Request DTOs

**Severity: Critical** | **Effort: Small**

No request DTO uses Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Valid`, etc.). Controllers do not use `@Valid` on `@RequestBody` parameters.

Examples of unvalidated inputs:
- `FundTransferRequest`: `fromAccount`, `toAccount`, `amount` can all be null or negative.
- `UtilityPaymentRequest`: `providerId`, `amount`, `referenceNumber`, `account` can all be null.
- `User` registration: `email`, `identification`, `password` are not validated.

**Impact**: Null pointer exceptions, negative transfers, empty account numbers. Potential for financial loss through negative-amount transfers.

**Recommendation**: Add Bean Validation annotations to all request DTOs and `@Valid` to all controller method parameters. Add `spring-boot-starter-validation` dependency.

---

### GAP-15: No Authentication on Downstream Services — Spoofable X-Auth-Id Header

**Severity: Critical** | **Effort: Medium**

JWT validation happens **only** at the API Gateway. Downstream services (core-banking, fund-transfer, user, utility-payment) have **no security configuration**. They trust the `X-Auth-Id` header injected by the Gateway, but this header can be trivially spoofed by any client that bypasses the Gateway (e.g., direct access to service ports).

The `AppAuthUserFilter` in downstream services reads `X-Auth-Id` and sets it in a thread-local `ApiRequestContextHolder`, but never validates it.

**Impact**: Any client with network access to the internal services can impersonate any user by setting the `X-Auth-Id` header.

**Recommendation**: Either (a) add JWT validation to each downstream service, or (b) enforce strict network isolation so only the Gateway can reach downstream services (currently, all ports are exposed in Docker Compose).

---

### GAP-16: Hardcoded Credentials in Docker Compose and SQL

**Severity: High** | **Effort: Small**

| Location | Credential |
|---|---|
| `docker-compose.yml` / `docker-compose-support-apps.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` / `docker-compose-support-apps.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password`, `POSTGRES_PASSWORD: password` |
| `privileges.sql` | `CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact**: Secrets are committed to source control. Anyone with repo access has full database and admin credentials.

**Recommendation**: Use Docker Compose environment variable substitution with `.env` files (gitignored), or Docker secrets.

---

### GAP-17: Overly Broad Database User Privileges

**Severity: Medium** | **Effort: Small**

The `javatodev_development` MySQL user has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` (all databases, all tables). `DROP` and `ALTER` on production data is dangerous.

**Impact**: A SQL injection in any service could drop tables across all schemas.

**Recommendation**: Create per-service database users with minimal privileges (typically `INSERT, UPDATE, DELETE, SELECT` on their own schema only).

---

### GAP-18: CSRF Disabled at Gateway

**Severity: Medium** | **Effort: Small**

The Gateway's `SecurityConfiguration` explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`.

**Impact**: If the Gateway ever serves browser-based clients, it is vulnerable to CSRF attacks.

**Recommendation**: If this is a pure API (no browser clients), document the decision. If browser clients are expected, enable CSRF protection with a token-based approach.

---

### GAP-19: Keycloak Client Singleton Not Thread-Safe

**Severity: Medium** | **Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized singleton pattern (classic double-check locking bug — no `volatile` or `synchronized`):

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact**: Race condition during startup could create multiple Keycloak client instances or return a partially constructed instance.

**Recommendation**: Use `@Bean` with Spring's singleton scope, or use `synchronized` / `volatile`.

---

### GAP-20: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No dependency scanning tools (OWASP Dependency-Check, Snyk, Dependabot, etc.) are configured.

**Impact**: Known CVEs in transitive dependencies go undetected.

**Recommendation**: Add OWASP Dependency-Check Gradle plugin or enable GitHub Dependabot alerts.

---

## 5. API Design

### GAP-21: Incorrect OpenAPI Dependency — WebFlux UI on MVC Services

**Severity: High** | **Effort: Small**

Four MVC-based services (`core-banking`, `fund-transfer`, `user`, `utility-payment`) include:
```
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

This is the **WebFlux** variant of springdoc. These services use Spring MVC (servlet stack), not WebFlux. The correct dependency is:
```
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
```

**Impact**: Swagger UI may not work correctly or at all on MVC services. API documentation is broken or incomplete.

**Recommendation**: Replace `webflux-ui` with `webmvc-ui` in all 4 MVC services.

---

### GAP-22: Pagination Response Lacks Metadata

**Severity: Medium** | **Effort: Small**

Paginated endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, `GET /api/v1/bank-users`, `GET /api/v1/user`) return a raw `List<T>` instead of a pagination wrapper with metadata.

Services convert `Page<T>.getContent()` to a list, discarding `totalElements`, `totalPages`, `pageNumber`, `pageSize`, and navigation links.

**Impact**: Clients cannot determine if there are more pages, how many total results exist, or navigate between pages.

**Recommendation**: Return the full `Page<T>` object or a custom pagination wrapper: `{ content: [...], totalElements, totalPages, page, size }`.

---

### GAP-23: No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

While endpoints use `/api/v1/` prefix, there is no documented versioning strategy, no infrastructure for running multiple versions simultaneously, and no deprecation mechanism.

**Impact**: Breaking changes require coordinated deployment of all consumers.

**Recommendation**: Document the versioning strategy. Consider header-based versioning for backward compatibility.

---

### GAP-24: Non-RESTful URL Patterns

**Severity: Low** | **Effort: Small**

Some endpoints deviate from REST conventions:
- `PATCH /api/v1/bank-users/update/{id}` — redundant "update" in URL (PATCH already implies update)
- `POST /api/v1/bank-users/register` — RPC-style "register" instead of `POST /api/v1/bank-users`
- `GET /api/v1/account/util-account/{account_name}` — uses `account_name` but actually queries by provider name
- Inconsistent path parameter naming: `{account_number}`, `{account_name}`, `{identification}`, `{id}`

**Impact**: API is harder to learn and use; inconsistent conventions confuse consumers.

**Recommendation**: Adopt consistent REST naming: `POST /api/v1/users`, `PATCH /api/v1/users/{id}`, etc.

---

### GAP-25: No Request/Response DTOs for Some Operations

**Severity: Low** | **Effort: Small**

The user registration endpoint reuses the `User` DTO (which includes `id`, `authId`, `status`, `version`) as both the request and response type. Clients can send fields they shouldn't (mass assignment risk).

**Impact**: Over-posting vulnerability. Clients could potentially set `authId` or `status` directly.

**Recommendation**: Create separate `CreateUserRequest` / `UserResponse` DTOs.

---

## 6. Observability

### GAP-26: No Structured Logging

**Severity: Medium** | **Effort: Medium**

All logging uses default Spring Boot format with `Slf4j` and `log.info()`. No structured logging (JSON format), no consistent log correlation IDs beyond Micrometer trace IDs, and no log aggregation configuration.

**Impact**: Logs are harder to parse, search, and correlate in production log management systems (ELK, Datadog, etc.).

**Recommendation**: Add a structured logging encoder (e.g., Logstash Logback encoder) for JSON output. Include trace IDs, service name, and request metadata in log context.

---

### GAP-27: Sensitive Data Logged in Request Bodies

**Severity: High** | **Effort: Small**

Controllers log full request objects using `toString()`:
```java
log.info("Creating user with {}", request.toString());      // Logs password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());  // Logs account numbers
```

The user registration request includes `password`, which will be logged in plaintext.

**Impact**: Passwords and financial data appear in log files, violating data protection regulations.

**Recommendation**: Override `toString()` to exclude sensitive fields, or use a sanitized logging approach.

---

### GAP-28: No Custom Health Check Endpoints

**Severity: Medium** | **Effort: Small**

While all services include `spring-boot-starter-actuator`, no custom health indicators are configured. The default `/actuator/health` does not check:
- Feign client connectivity to core-banking-service
- Keycloak connectivity (user-service)
- Config Server availability

**Impact**: Health endpoints report "UP" even when critical dependencies are unreachable.

**Recommendation**: Implement custom `HealthIndicator` beans for Feign clients, Keycloak, and database connections.

---

### GAP-29: No Metrics Endpoints Beyond Defaults

**Severity: Low** | **Effort: Medium**

No custom metrics are defined. No Prometheus endpoint is configured despite being listed in the README's technology stack.

**Impact**: No visibility into business metrics (transfer count, payment volume, error rates per endpoint).

**Recommendation**: Add Micrometer metrics for business operations. Expose `/actuator/prometheus` endpoint.

---

### GAP-30: Distributed Tracing Configuration Not Verified

**Severity: Low** | **Effort: Small**

While tracing dependencies are included in all services, the Zipkin URL and sampling rate are configured in the external Config Server repository, making it impossible to verify from this codebase alone. No integration tests validate that traces are properly propagated.

**Impact**: Tracing may be misconfigured or disabled without anyone noticing.

**Recommendation**: Add a test that validates trace context propagation across Feign calls.

---

## 7. Resilience

### GAP-31: No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

No circuit breaker library (Resilience4j, Hystrix) is configured. All Feign calls to core-banking-service are unprotected. If core-banking-service becomes slow or unresponsive:
- Fund-transfer-service threads block indefinitely.
- Utility-payment-service threads block indefinitely.
- User-service registration threads block indefinitely.
- Thread pool exhaustion cascades to the Gateway.

**Impact**: A single slow/failing service causes cascading failure across the entire platform.

**Recommendation**: Add Resilience4j circuit breakers to all Feign clients with appropriate thresholds, timeouts, and fallback methods.

---

### GAP-32: No Retry Policies

**Severity: High** | **Effort: Small**

No retry configuration exists for Feign clients or any service-to-service calls. Transient network errors (connection reset, timeout) cause immediate failure.

**Impact**: Temporary network glitches result in failed transactions that require manual intervention.

**Recommendation**: Add Resilience4j retry with exponential backoff for Feign calls. Ensure idempotency before adding retries.

---

### GAP-33: No Timeout Configuration

**Severity: High** | **Effort: Small**

No explicit timeouts are configured for:
- Feign client connections and reads
- Database connections (HikariCP pool)
- Keycloak Admin Client calls

Default Feign timeouts (10s connect, 60s read) may be too generous for a banking application.

**Impact**: Slow downstream services tie up threads for extended periods, reducing throughput and eventually causing thread starvation.

**Recommendation**: Configure explicit timeouts for all external calls (Feign: 5s connect / 10s read, Database: 5s connection timeout, Keycloak: 10s).

---

### GAP-34: No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

When any downstream call fails, the error simply propagates up as an HTTP 400 (see GAP-05). No graceful degradation, cached responses, or meaningful fallback messages are provided.

**Impact**: Users see cryptic error messages. No partial functionality when one service is down.

**Recommendation**: Implement Feign fallback factories that return meaningful error responses and potentially cached data for read operations.

---

### GAP-35: No Idempotency Keys for Financial Transactions

**Severity: Critical** | **Effort: Medium**

Neither `POST /api/v1/transfer` nor `POST /api/v1/utility-payment` accepts or generates idempotency keys. If a client retries a failed request (e.g., due to network timeout), the same transfer/payment will be processed twice.

**Impact**: Duplicate financial transactions. Real money loss for users.

**Recommendation**: Accept a client-provided `Idempotency-Key` header. Check for existing transactions with the same key before processing. Return the cached response for duplicate requests.

---

### GAP-36: Double-Deduction Bug in Balance Calculations

**Severity: Critical** | **Effort: Small**

In `TransactionService.internalFundTransfer()` (core-banking-service, line 90–91):

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

`availableBalance` is computed from the already-debited `actualBalance`, causing a double deduction. The same pattern exists for the credit side (line 99–100) and in `utilPayment()` (line 63–64).

**Impact**: Every fund transfer and utility payment causes incorrect balance calculations. Users lose/gain more money than intended.

**Recommendation**: Fix to: `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())` (or compute both from the original balance).

---

### GAP-37: No Database Transaction Isolation for Financial Operations

**Severity: High** | **Effort: Medium**

`TransactionService` uses `@Transactional` at the class level (JTA), but does not specify isolation level. The default `READ_COMMITTED` isolation may allow dirty reads between the debit and credit operations in `internalFundTransfer()`.

Additionally, there is no pessimistic locking on `BankAccountEntity` records during balance modifications, which could lead to lost updates under concurrent access.

**Impact**: Concurrent fund transfers from the same account could result in negative balances or lost updates.

**Recommendation**: Use `@Transactional(isolation = Isolation.SERIALIZABLE)` for financial operations, or implement optimistic locking with `@Version` on `BankAccountEntity`, or use `SELECT ... FOR UPDATE` (pessimistic locking).

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | Code Organization | No shared library — duplicated code across services | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle root project | Medium | Small |
| GAP-03 | Code Organization | Inconsistent package structure across services | Low | Small |
| GAP-04 | Code Organization | Mapper instantiation via `new` instead of DI | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 Bad Request | Critical | Small |
| GAP-06 | Error Handling | Generic exception handler leaks internal details | High | Small |
| GAP-07 | Error Handling | No Feign error decoder in fund-transfer and utility-payment | High | Small |
| GAP-08 | Error Handling | No compensation/rollback on Feign failure | Critical | Large |
| GAP-09 | Error Handling | Raw `ResponseEntity` without type parameters | Low | Small |
| GAP-10 | Testing | Minimal test coverage — only 1 of 6 services has tests | Critical | Large |
| GAP-11 | Testing | No integration tests | High | Large |
| GAP-12 | Testing | No contract tests between services | Medium | Large |
| GAP-13 | Testing | Empty ApplicationTests will fail without Config Server | Medium | Small |
| GAP-14 | Security | No input validation on any request DTOs | Critical | Small |
| GAP-15 | Security | No authentication on downstream services | Critical | Medium |
| GAP-16 | Security | Hardcoded credentials in Docker Compose and SQL | High | Small |
| GAP-17 | Security | Overly broad database user privileges | Medium | Small |
| GAP-18 | Security | CSRF disabled at Gateway | Medium | Small |
| GAP-19 | Security | Keycloak client singleton not thread-safe | Medium | Small |
| GAP-20 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-21 | API Design | Incorrect OpenAPI dependency (WebFlux UI on MVC services) | High | Small |
| GAP-22 | API Design | Pagination response lacks metadata | Medium | Small |
| GAP-23 | API Design | No API versioning strategy | Low | Medium |
| GAP-24 | API Design | Non-RESTful URL patterns | Low | Small |
| GAP-25 | API Design | No request/response DTOs for some operations | Low | Small |
| GAP-26 | Observability | No structured logging | Medium | Medium |
| GAP-27 | Observability | Sensitive data logged in request bodies | High | Small |
| GAP-28 | Observability | No custom health check endpoints | Medium | Small |
| GAP-29 | Observability | No metrics endpoints beyond defaults | Low | Medium |
| GAP-30 | Observability | Distributed tracing configuration not verified | Low | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies | High | Small |
| GAP-33 | Resilience | No timeout configuration | High | Small |
| GAP-34 | Resilience | No fallback behavior | Medium | Medium |
| GAP-35 | Resilience | No idempotency keys for financial transactions | Critical | Medium |
| GAP-36 | Resilience | Double-deduction bug in balance calculations | Critical | Small |
| GAP-37 | Resilience | No database transaction isolation for financial operations | High | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 8 |
| High | 11 |
| Medium | 12 |
| Low | 6 |
| **Total** | **37** |
