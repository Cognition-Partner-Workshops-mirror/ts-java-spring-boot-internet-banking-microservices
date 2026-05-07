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

### GAP-ORG-01: No Multi-Project Gradle Build

**Severity:** Medium | **Effort:** Small

Each service has an independent `build.gradle` with duplicated Spring Boot/Cloud versions, plugin declarations, and dependency management blocks. There is no root `settings.gradle` or `build.gradle` to enforce consistent versions or share common configuration.

**Evidence:**
- Spring Boot `3.2.4`, Spring Cloud `2023.0.0`, and the git-properties plugin `2.4.2` are repeated in every `build.gradle`.
- Dependency versions (e.g., `mysql-connector-j:8.4.0`, `springdoc-openapi:2.1.0`) are hardcoded in each file.

**Risk:** Version drift between services; a developer upgrades one service but forgets another.

---

### GAP-ORG-02: Duplicated Code Across Services

**Severity:** Medium | **Effort:** Medium

The following classes are copy-pasted (with minor variations) across 3-4 services with no shared library:

| Class | Duplicated In |
|---|---|
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` (mapped superclass) | user-service, fund-transfer, utility-payment |
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | user-service, fund-transfer, utility-payment |

**Risk:** Bug fixes or improvements must be applied N times. Inconsistencies already exist (e.g., `ErrorResponse` uses `@Builder` in some services but a constructor in fund-transfer).

---

### GAP-ORG-03: Inconsistent Package Structure

**Severity:** Low | **Effort:** Small

- Core Banking uses `com.javatodev.finance.repository` for JPA repositories.
- User Service uses `com.javatodev.finance.model.repository`.
- Utility Payment Service uses `com.javatodev.finance.repository`.
- DTOs live under `model.dto` in some services but `model.rest.request` / `model.rest.response` in others.

**Risk:** Increases cognitive load for developers context-switching between services.

---

### GAP-ORG-04: Mapper Classes Instantiated Inline

**Severity:** Low | **Effort:** Small

Mapper objects (e.g., `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are created with `new` inside service classes rather than being Spring-managed beans or using a mapping framework like MapStruct.

**Evidence:** `private UserMapper userMapper = new UserMapper();` in multiple services.

**Risk:** Cannot leverage dependency injection, testing is harder, and there is no compile-time validation of mappings.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). Entity-not-found errors should return HTTP 404, server errors should return HTTP 500, etc.

**Evidence:**
```java
// core-banking-service GlobalExceptionHandler
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Risk:** Clients cannot distinguish between client errors, not-found errors, and server errors. Violates HTTP semantics. Monitoring tools cannot differentiate error categories.

---

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details

**Severity:** High | **Effort:** Small

The catch-all `@ExceptionHandler({Exception.class})` returns the full exception object as a string: `"Exception occur inside API " + e`. This exposes stack traces, class names, and potentially sensitive information to API consumers.

**Evidence:** Present in all four services' `GlobalExceptionHandler`.

**Risk:** Information disclosure vulnerability. Attackers can learn about internal architecture, library versions, and database structure from error messages.

---

### GAP-ERR-03: No Feign Error Handling for Core Banking Calls

**Severity:** High | **Effort:** Medium

Fund Transfer and Utility Payment services call Core Banking via OpenFeign but have no robust error decoding. If Core Banking returns an error, the Feign client will throw a generic `FeignException` which is not caught or translated.

**Evidence:**
- `FundTransferService.fundTransfer()` calls `bankingCoreFeignClient.fundTransfer(request)` with no try-catch.
- A `CustomFeignErrorDecoder` exists only in the user service but does minimal handling.
- Fund transfer entity is saved as `PENDING` but never updated to `FAILED` if the Core Banking call fails.

**Risk:** Failed transactions remain in `PENDING` status forever. Users get raw Feign exceptions instead of meaningful error messages.

---

### GAP-ERR-04: Missing Validation on Request Bodies

**Severity:** High | **Effort:** Small

No controller uses `@Valid` or `@Validated`. No request DTO has Jakarta Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, etc.).

**Evidence:**
- `FundTransferRequest` has `fromAccount`, `toAccount`, `amount` — all nullable with no constraints.
- `UtilityPaymentRequest` has `providerId`, `amount`, `referenceNumber`, `account` — all nullable.
- A null `amount` would cause a `NullPointerException` during `BigDecimal.compareTo()` in balance validation.

**Risk:** Invalid data enters the system, causing cryptic NPEs instead of clear 422 validation errors.

---

## 3. Testing

### GAP-TEST-01: Tests Only Exist in Core Banking Service

**Severity:** Critical | **Effort:** Large

Only `core-banking-service` has meaningful unit tests (3 test classes with ~20 test methods). The other five services have only the default Spring Boot application context test (which would fail without a running database/config server).

| Service | Test Classes | Test Methods |
|---|---|---|
| core-banking-service | 4 (3 service tests + 1 context test) | ~20 |
| internet-banking-user-service | 1 (context test only) | 1 |
| internet-banking-fund-transfer-service | 1 (context test only) | 1 |
| internet-banking-utility-payment-service | 1 (context test only) | 1 |
| internet-banking-api-gateway | 1 (context test only) | 1 |
| internet-banking-service-registry | 1 (context test only) | 1 |

**Risk:** No automated verification of business logic in four of six services. Regressions in user registration, fund transfer orchestration, and payment processing go undetected.

---

### GAP-TEST-02: No Integration Tests

**Severity:** High | **Effort:** Large

There are no integration tests that verify:
- Database interactions (JPA repositories with a real or embedded database)
- Controller endpoints (MockMvc / WebTestClient tests)
- Feign client contracts between services
- End-to-end API gateway routing

**Risk:** Unit tests mock everything, so integration issues (serialization mismatches, query errors, routing problems) are only caught in production.

---

### GAP-TEST-03: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

Services communicate via OpenFeign with hardcoded endpoint paths. There are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that the provider's API matches the consumer's expectations.

**Evidence:** Fund Transfer Service's `BankingCoreFeignClient` calls `/api/v1/transaction/fund-transfer` — if Core Banking renames or changes this endpoint, there is no automated check.

**Risk:** Breaking changes in one service silently break dependent services.

---

### GAP-TEST-04: Application Context Tests Would Fail in Isolation

**Severity:** Medium | **Effort:** Small

The default `*ApplicationTests` classes (e.g., `InternetBankingUserServiceApplicationTests`) try to load the full Spring context, which requires the Config Server, MySQL, and Keycloak to be running. They would fail in CI without those services.

**Evidence:** No test profile with embedded alternatives (H2, mock Keycloak, etc.) for most services. Core Banking has an H2 test dependency and a test `application.yml`, but the other services do not.

**Risk:** Tests are effectively un-runnable in CI pipelines without external infrastructure.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Docker Compose and SQL

**Severity:** Critical | **Effort:** Small

Production-style credentials are committed to version control:

| Location | Credential |
|---|---|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| `privileges.sql` | `CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Risk:** Secrets in version control are a critical security violation. Even if these are "dev-only" values, they establish bad habits and may be reused in staging/production.

---

### GAP-SEC-02: CSRF Disabled on API Gateway

**Severity:** Medium | **Effort:** Small

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF protection is disabled globally. For a REST API consumed by non-browser clients with JWT authentication, this is acceptable. However, if any browser-based client uses cookie-based sessions, this is a vulnerability.

**Risk:** If the API is ever consumed from a browser with cookie-based auth, CSRF attacks become possible.

---

### GAP-SEC-03: No Input Sanitization

**Severity:** High | **Effort:** Medium

No input validation or sanitization is performed on any request body or path parameter. Combined with the lack of Jakarta Validation (GAP-ERR-04), the system is vulnerable to:
- SQL injection (mitigated by JPA parameterized queries, but not defense-in-depth)
- Log injection (user-controlled values like `accountNumber` are logged directly)
- Oversized payloads (no `@Size` constraints)

**Evidence:** `log.info("Reading account by ID {}", accountNumber);` — a crafted account number with newlines could forge log entries.

**Risk:** Log forging, potential for downstream injection if raw values are ever used in string concatenation.

---

### GAP-SEC-04: Keycloak Client Singleton Is Not Thread-Safe

**Severity:** Medium | **Effort:** Small

```java
// KeycloakProperties.java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

This is a non-thread-safe lazy singleton. Under concurrent requests, multiple Keycloak instances could be created (race condition).

**Risk:** Resource leaks, unexpected authentication failures under load.

---

### GAP-SEC-05: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No dependency vulnerability scanning tool is configured:
- No OWASP Dependency Check Gradle plugin
- No GitHub Dependabot configuration
- No Snyk or similar integration

**Risk:** Known vulnerabilities in transitive dependencies go undetected.

---

### GAP-SEC-06: Internal Services Are Not Authenticated

**Severity:** High | **Effort:** Medium

Core Banking Service endpoints are accessible without authentication. The API Gateway enforces OAuth2, but if Core Banking is accessed directly (bypassing the gateway), there is no authentication.

**Evidence:**
- Core Banking's `build.gradle` does not include `spring-boot-starter-security`.
- No security configuration exists in the Core Banking Service.
- OpenFeign calls from other services to Core Banking do not pass any auth token.

**Risk:** In a network breach or misconfiguration, Core Banking endpoints (which directly modify account balances) are completely unprotected.

---

## 5. API Design

### GAP-API-01: Raw Types in ResponseEntity

**Severity:** Medium | **Effort:** Small

Controllers return `ResponseEntity` without type parameters (raw types) instead of `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`, etc.

**Evidence:**
```java
// AccountController
public ResponseEntity getBankAccount(...) { ... }
// TransactionController
public ResponseEntity fundTransfer(...) { ... }
```

Only the User Service's `UserController` uses typed `ResponseEntity<User>`.

**Risk:** Clients and OpenAPI documentation cannot determine response types. Compile-time type safety is lost.

---

### GAP-API-02: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

All endpoints use `/api/v1/...` but there is no mechanism for introducing `/api/v2/` alongside v1, no deprecation headers, and no documentation of the versioning strategy.

**Risk:** Breaking changes require coordinated big-bang migrations across all consumers.

---

### GAP-API-03: Incomplete OpenAPI/Swagger Configuration

**Severity:** Medium | **Effort:** Small

- `springdoc-openapi-starter-webflux-ui:2.1.0` is used, but this is the WebFlux variant while all business services are Spring MVC (Web). This may produce incorrect or missing documentation.
- There is no `@OpenAPIDefinition` or global Swagger configuration for API metadata, contact info, or server URLs.
- Response DTOs lack `@Schema` annotations for field descriptions.

**Risk:** Auto-generated API documentation may be broken or incomplete. Developers and consumers cannot reliably use Swagger UI.

---

### GAP-API-04: Inconsistent Resource Naming

**Severity:** Low | **Effort:** Small

- Core Banking: `/api/v1/account/bank-account/{account_number}` (uses underscore in path variable)
- Core Banking: `/api/v1/account/util-account/{account_name}` (abbreviated "util")
- User Service: `/api/v1/bank-users/register` (hyphenated plural)
- Fund Transfer: `/api/v1/transfer` (singular noun)
- Utility Payment: `/api/v1/utility-payment` (singular noun, hyphenated)

**Risk:** Inconsistent API feels unprofessional and increases integration friction.

---

### GAP-API-05: No Pagination Metadata in List Responses

**Severity:** Medium | **Effort:** Small

List endpoints accept `Pageable` parameters but return raw `List<T>` instead of a page wrapper with total count, page number, and page size.

**Evidence:**
```java
// UserService (core banking)
public List<User> readUsers(Pageable pageable) {
    return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
}
```

The `Page` object is discarded; only `.getContent()` is returned.

**Risk:** Clients cannot implement pagination UIs (no total count, no "has next page" indicator).

---

### GAP-API-06: No Filtering or Sorting Documentation

**Severity:** Low | **Effort:** Small

While Spring Data's `Pageable` supports `sort` parameters, there is no documentation of which fields are sortable or filterable. No custom query endpoints exist for searching by date range, status, amount range, etc.

**Risk:** Clients must guess at supported query parameters.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging

**Severity:** Medium | **Effort:** Small

- Some controllers log request payloads with `toString()` (e.g., `FundTransferRequest.toString()`) which could log sensitive data.
- Log levels are not documented or configurable per-service.
- No structured logging (JSON format) is configured for production.
- A typo exists: `"Reading utitlity account"` in `AccountController`.

**Risk:** Sensitive data (account numbers, user IDs) in logs. Unstructured logs are hard to parse in centralized logging systems.

---

### GAP-OBS-02: No Health Check Customization

**Severity:** Low | **Effort:** Small

All services include `spring-boot-starter-actuator`, which provides a default `/actuator/health` endpoint. However:
- No custom health indicators for database connectivity, Keycloak availability, or Feign client reachability.
- No readiness/liveness probe differentiation for Kubernetes deployments.

**Risk:** Basic health endpoint only reports "UP" even if critical dependencies are down.

---

### GAP-OBS-03: No Metrics Endpoints Configured

**Severity:** Medium | **Effort:** Small

Actuator is included but:
- No Prometheus metrics exporter configured (`micrometer-registry-prometheus` is not in any `build.gradle`).
- README mentions Prometheus in the tech stack, but no actual integration exists.
- No custom business metrics (transfer count, payment processing time, error rates).

**Risk:** No quantitative observability for production operations, capacity planning, or alerting.

---

### GAP-OBS-04: Distributed Tracing Coverage Unverified

**Severity:** Low | **Effort:** Small

Tracing dependencies (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) are present in all services. However:
- No `management.tracing.sampling.probability` configuration visible (default is 0.1 = 10% sampling).
- Trace context propagation between services via OpenFeign is configured (`feign-micrometer`) but never tested.

**Risk:** Only 10% of traces may be captured by default, making debugging intermittent issues difficult.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity:** High | **Effort:** Medium

No circuit breaker library (Resilience4j, Hystrix) is configured anywhere. All OpenFeign calls to Core Banking will fail-fast with unhandled exceptions if Core Banking is down.

**Evidence:** No `resilience4j` or `spring-cloud-starter-circuitbreaker` dependency in any `build.gradle`.

**Risk:** A Core Banking outage cascades immediately to all dependent services. No graceful degradation.

---

### GAP-RES-02: No Retry Policies

**Severity:** High | **Effort:** Small

No retry configuration exists for OpenFeign clients or any other HTTP calls. Transient network errors or brief service restarts will cause immediate failures.

**Evidence:** No `spring-retry` dependency, no `@Retryable` annotations, no Feign retry configuration.

**Risk:** Transient failures cause user-visible errors that would self-resolve with a simple retry.

---

### GAP-RES-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit timeout configuration for:
- OpenFeign client calls (defaults to no timeout or very long timeout)
- Database connection pool
- Keycloak admin client calls

**Evidence:** No `feign.client.config.default.connectTimeout` or `readTimeout` in any configuration.

**Risk:** A slow or hung Core Banking Service will cause thread pool exhaustion in upstream services, leading to cascading failures.

---

### GAP-RES-04: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

When Core Banking is unavailable, fund transfer and utility payment services have no fallback logic. Options like queuing requests for later processing, returning a "pending" status, or serving cached data are not implemented.

**Risk:** Any downstream failure immediately becomes a complete service outage for the user-facing function.

---

### GAP-RES-05: No Idempotency Protection

**Severity:** High | **Effort:** Medium

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a request (e.g., due to network timeout), the same transfer could be processed twice.

**Evidence:**
- `FundTransferService.fundTransfer()` creates a new entity on every call with no duplicate check.
- No `Idempotency-Key` header handling.
- No unique constraint on `(fromAccount, toAccount, amount, timestamp)` or similar.

**Risk:** Double-charging customers. In a banking context, this is a critical financial risk.

---

### GAP-RES-06: Balance Calculation Bug

**Severity:** Critical | **Effort:** Small

In `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`, the `availableBalance` is incorrectly calculated:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^ This subtracts 'amount' from the ALREADY REDUCED actualBalance
```

After a $100 transfer from an account with $200 actual balance:
- `actualBalance` = $200 - $100 = $100 (correct)
- `availableBalance` = $100 - $100 = $0 (incorrect — should be $100)

**Risk:** Available balance drifts out of sync with actual balance after every transaction. Customers appear to have less available money than they actually do.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | Medium | Small |
| GAP-ORG-02 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Mapper classes instantiated inline | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic handler leaks internal details | High | Small |
| GAP-ERR-03 | Error Handling | No Feign error handling for core banking calls | High | Medium |
| GAP-ERR-04 | Error Handling | Missing validation on request bodies | High | Small |
| GAP-TEST-01 | Testing | Tests only exist in core banking service | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests between services | Medium | Large |
| GAP-TEST-04 | Testing | Application context tests fail in isolation | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in Docker Compose and SQL | Critical | Small |
| GAP-SEC-02 | Security | CSRF disabled on API gateway | Medium | Small |
| GAP-SEC-03 | Security | No input sanitization | High | Medium |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-06 | Security | Internal services not authenticated | High | Medium |
| GAP-API-01 | API Design | Raw types in ResponseEntity | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-03 | API Design | Incorrect OpenAPI dependency / incomplete config | Medium | Small |
| GAP-API-04 | API Design | Inconsistent resource naming | Low | Small |
| GAP-API-05 | API Design | No pagination metadata in list responses | Medium | Small |
| GAP-API-06 | API Design | No filtering or sorting documentation | Low | Small |
| GAP-OBS-01 | Observability | Inconsistent logging / sensitive data in logs | Medium | Small |
| GAP-OBS-02 | Observability | No health check customization | Low | Small |
| GAP-OBS-03 | Observability | No metrics endpoints configured | Medium | Small |
| GAP-OBS-04 | Observability | Distributed tracing coverage unverified | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | High | Medium |
| GAP-RES-06 | Resilience | Balance calculation bug | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 3 |
| High | 12 |
| Medium | 13 |
| Low | 5 |
| **Total** | **33** |
