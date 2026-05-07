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

### GAP-ORG-01: No Multi-Module Gradle Build

**Severity:** Medium | **Effort:** Small

**Current State:** Each of the 6 services is a standalone Gradle project with its own `build.gradle`, `settings.gradle`, and Gradle wrapper. There is no root-level `build.gradle` or `settings.gradle` to unify them.

**Impact:** Developers must `cd` into each service directory to build, test, or update dependencies. Dependency versions drift between services. No single command to build or test the entire system.

**Best Practice:** Use a Gradle multi-module build with a root `settings.gradle` that includes all services as subprojects, and a root `build.gradle` with shared dependency management (e.g., a `subprojects {}` block or a version catalog).

---

### GAP-ORG-02: Duplicated Code Across Services

**Severity:** Medium | **Effort:** Medium

**Current State:** The following classes are duplicated verbatim (or near-verbatim) across 3-4 services:
- `BaseMapper` (4 copies: core-banking, user-service, fund-transfer, utility-payment)
- `AuditAware` / `AuditConfig` / `AuditorAwareConfig` (3 copies: user-service, fund-transfer, utility-payment)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (3 copies: user-service, fund-transfer, utility-payment)
- `SimpleBankingGlobalException` / `ErrorResponse` / `GlobalExceptionHandler` (4 copies with minor variations)
- `CustomFeignClientConfiguration` (2 copies: fund-transfer, utility-payment)

**Impact:** Bug fixes and changes must be applied in multiple places. Inconsistencies creep in (e.g., `GlobalExceptionHandler` in fund-transfer-service has a different constructor pattern for `ErrorResponse` than utility-payment-service).

**Best Practice:** Extract shared code into a `common` or `shared-library` module that all services depend on.

---

### GAP-ORG-03: Inconsistent Package Structure Across Services

**Severity:** Low | **Effort:** Small

**Current State:**
- `core-banking-service`: `repository/` at top level, no `configuration/` package
- `user-service`: `model/repository/`, `configuration/feign/`, `configuration/keycloak/`, `configuration/filter/`, `configuration/audit/`
- `fund-transfer-service`: `model/repository/`, `configuration/filter/`, `configuration/audit/`, `service/rest/client/`
- `utility-payment-service`: `repository/` at top level (not under `model/`), `configuration/` (flat), `service/rest/`

**Impact:** Navigating across services is confusing. New developers have to learn different conventions per service.

**Best Practice:** Standardize on a single package layout convention (e.g., `controller/`, `service/`, `model/entity/`, `model/dto/`, `model/mapper/`, `repository/`, `configuration/`, `exception/`).

---

### GAP-ORG-04: Inconsistent Indentation in Build Files

**Severity:** Low | **Effort:** Small

**Current State:** Some `build.gradle` files use tabs, others use spaces. Mix of tab-indented and space-indented blocks within the same file (e.g., `utility-payment-service/build.gradle`).

**Impact:** Minor readability issue; can cause noisy diffs.

**Best Practice:** Standardize on a single indentation style. Consider adding an `.editorconfig` or Spotless Gradle plugin.

---

## 2. Error Handling

### GAP-ERR-01: Inconsistent Exception Hierarchy Across Services

**Severity:** High | **Effort:** Medium

**Current State:**
- `core-banking-service` has: `EntityNotFoundException`, `InsufficientFundsException`, `GlobalErrorCode` constants
- `user-service` has: `EntityNotFoundException`, `InvalidEmailException`, `InvalidBankingUserException`, `UserAlreadyRegisteredException`, `GlobalErrorCode` constants
- `fund-transfer-service` has: only `SimpleBankingGlobalException` (no specific exceptions)
- `utility-payment-service` has: only `SimpleBankingGlobalException` (no specific exceptions)

The base `SimpleBankingGlobalException` is duplicated in each service with the same structure but different package paths.

**Impact:** No consistent error taxonomy across the system. Services cannot reliably interpret error responses from other services.

---

### GAP-ERR-02: Catch-All Exception Handler Returns Sensitive Information

**Severity:** Critical | **Effort:** Small

**Current State:** In all 4 `GlobalExceptionHandler` implementations:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest()
        .body("Exception occur inside API " + e);
}
```

This concatenates the full exception (including stack trace via `toString()`) into the response body.

**Impact:** Stack traces, class names, SQL queries, and internal details are leaked to clients. This is a security vulnerability.

**Best Practice:** Return a generic error message with a correlation ID. Log the full exception server-side.

---

### GAP-ERR-03: All Errors Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

**Current State:** `GlobalExceptionHandler` maps `SimpleBankingGlobalException` to `400 Bad Request` and all other exceptions also to `400 Bad Request`.

**Impact:** Clients cannot distinguish between validation errors (400), not found (404), authorization failures (403), or server errors (500). This breaks REST semantics and makes client-side error handling unreliable.

**Best Practice:** Map exceptions to appropriate HTTP status codes:
- `EntityNotFoundException` → 404
- `InsufficientFundsException` → 422 (Unprocessable Entity) or 400
- `UserAlreadyRegisteredException` → 409 (Conflict)
- Unexpected exceptions → 500

---

### GAP-ERR-04: No Error Handling for Failed Feign Calls in Orchestrator Services

**Severity:** High | **Effort:** Medium

**Current State:** In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, the entity is saved with `PENDING`/`PROCESSING` status, then the Feign call is made. If the Feign call fails, the entity remains in `PENDING`/`PROCESSING` state forever — there is no `try/catch` to update the status to `FAILED`.

**Impact:** Failed transactions are stuck in limbo. No visibility into failures. No retry or compensation logic.

**Best Practice:** Wrap Feign calls in try/catch. On failure, update entity status to `FAILED` and rethrow or return an error response.

---

### GAP-ERR-05: Missing Raw Type Parameterization on ResponseEntity

**Severity:** Medium | **Effort:** Small

**Current State:** All controller methods return raw `ResponseEntity` instead of parameterized `ResponseEntity<?>` or `ResponseEntity<SpecificType>`.

**Impact:** No compile-time type safety. Swagger/OpenAPI cannot infer response types, leading to incomplete API documentation.

**Best Practice:** Use parameterized response types: `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`, etc.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage for Most Services

**Severity:** Critical | **Effort:** Large

**Current State:**
| Service | Tests |
|---|---|
| core-banking-service | `TransactionServiceTest` (8 tests), `UserServiceTest` (3 tests) — good unit test coverage |
| internet-banking-user-service | Only `contextLoads()` placeholder |
| internet-banking-fund-transfer-service | Only `contextLoads()` placeholder |
| internet-banking-utility-payment-service | Only `contextLoads()` placeholder |
| internet-banking-api-gateway | Only `contextLoads()` placeholder |
| internet-banking-config-server | Only `contextLoads()` placeholder |
| internet-banking-service-registry | Only `contextLoads()` placeholder |

**Impact:** No safety net for regressions. Critical business logic in user-service (registration, approval), fund-transfer-service (orchestration), and utility-payment-service (orchestration) has zero test coverage.

---

### GAP-TEST-02: No Integration Tests

**Severity:** High | **Effort:** Large

**Current State:** No `@SpringBootTest` tests that exercise the full Spring context with controllers, services, and repositories together. No tests using Testcontainers or WireMock.

**Impact:** Cannot verify that Spring wiring, JPA mappings, and Feign configurations work correctly. Issues only discovered at runtime.

**Best Practice:** Add Spring Boot integration tests with H2 for repositories, `@WebMvcTest` for controllers, and WireMock for Feign client testing.

---

### GAP-TEST-03: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

**Current State:** No Spring Cloud Contract, Pact, or any other contract testing framework. The Feign client interfaces and their corresponding controller endpoints have no automated verification of compatibility.

**Impact:** Breaking changes in core-banking-service APIs silently break fund-transfer-service and utility-payment-service. Changes to request/response DTOs are only caught at runtime.

---

### GAP-TEST-04: contextLoads() Tests Will Fail Without Infrastructure

**Severity:** Medium | **Effort:** Small

**Current State:** The `@SpringBootTest` `contextLoads()` tests in `api-gateway`, `config-server`, and `service-registry` attempt to load the full Spring context but require external dependencies (Keycloak, Config Server Git repo, Eureka ports).

**Impact:** These tests will fail in CI without proper test configuration, making them effectively dead code.

**Best Practice:** Either configure proper test profiles that mock external dependencies, or annotate these tests to skip in CI.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code and Docker Compose

**Severity:** Critical | **Effort:** Small

**Current State:**
- `docker-compose.yml`: MySQL root password `woVERANKliGharym` in plaintext
- `docker-compose.yml`: Keycloak admin password `password` in plaintext
- `docker-compose.yml`: PostgreSQL password `password` in plaintext
- `docker-compose/mysql/privileges.sql`: Database user password `oPItyPticIAt` in plaintext
- `docker-compose/mysql/Dockerfile`: MySQL root password in `ENV`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` in plaintext
- `internet-banking-user-service/src/test/resources/application.yml`: Keycloak client secret in plaintext

**Impact:** Secrets are committed to version control and visible to anyone with repo access.

**Best Practice:** Use Docker secrets, environment variable files (`.env` excluded from VCS), or a secrets manager. Never commit real credentials.

---

### GAP-SEC-02: No Input Validation on API Request Bodies

**Severity:** Critical | **Effort:** Medium

**Current State:** None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) have Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, `@Email`, etc.). Controllers do not use `@Valid` or `@Validated`.

**Impact:** Null amounts, negative transfers, blank account numbers, and invalid emails are accepted and processed. This can lead to data corruption and financial errors.

**Best Practice:** Add Bean Validation annotations to all request DTOs and `@Valid` to all `@RequestBody` parameters.

---

### GAP-SEC-03: No Authorization Checks in Downstream Services

**Severity:** High | **Effort:** Medium

**Current State:** The API gateway handles JWT authentication and passes the user ID via `X-Auth-Id` header. However, downstream services (user-service, fund-transfer-service, utility-payment-service) do not verify:
1. That the `X-Auth-Id` header is actually from the gateway (no shared secret or token).
2. That the authenticated user is authorized to perform the requested operation (e.g., a user transferring from someone else's account).

The core-banking-service has no security at all — any service can call it without authentication.

**Impact:** If a service is accessed directly (bypassing the gateway), there is no authentication. Even through the gateway, there is no authorization (any authenticated user can transfer from any account).

---

### GAP-SEC-04: CSRF Disabled Without Explanation

**Severity:** Low | **Effort:** Small

**Current State:** `SecurityConfiguration` in the API gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`.

**Impact:** For a REST API that uses JWT bearer tokens (not cookies), disabling CSRF is acceptable. However, this should be documented.

---

### GAP-SEC-05: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

**Current State:** No OWASP Dependency Check, Snyk, or any other vulnerability scanning tool is configured in the build pipeline.

**Impact:** Known CVEs in transitive dependencies go undetected.

**Best Practice:** Add the OWASP Dependency Check Gradle plugin or integrate with a CI-based scanner.

---

## 5. API Design

### GAP-API-01: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

**Current State:** All endpoints use `/api/v1/` but there is no mechanism for introducing `v2` endpoints or managing backward compatibility. No URL versioning, header versioning, or media type versioning strategy is documented.

**Impact:** When breaking changes are needed, there is no established pattern for maintaining backward compatibility.

---

### GAP-API-02: No Pagination Metadata in Responses

**Severity:** Medium | **Effort:** Small

**Current State:** Paginated endpoints (e.g., `GET /api/v1/transfer`, `GET /api/v1/utility-payment`) accept Spring Data `Pageable` parameters but return a raw `List<>`. Page metadata (total elements, total pages, current page, page size) is discarded.

**Impact:** Clients cannot implement pagination UIs because they don't know the total number of pages/elements.

**Best Practice:** Return `Page<T>` directly (which serializes to `{ content: [], totalElements, totalPages, number, size }`) or wrap in a standard envelope.

---

### GAP-API-03: No Filtering or Sorting Documentation

**Severity:** Low | **Effort:** Small

**Current State:** Spring Data `Pageable` is used for pagination and sorting, but there is no documentation of supported sort fields or filter parameters.

**Impact:** API consumers must guess which fields can be sorted or filtered.

---

### GAP-API-04: Inconsistent Endpoint Naming

**Severity:** Low | **Effort:** Small

**Current State:**
- User service: `/api/v1/bank-users/register` (noun + verb)
- Fund transfer service: `/api/v1/transfer` (verb as noun)
- Utility payment service: `/api/v1/utility-payment` (noun)
- Core banking: `/api/v1/account/bank-account/{id}`, `/api/v1/account/util-account/{name}` (inconsistent abbreviation `util` vs `utility`)

**Impact:** Inconsistent API surface is harder to learn and document.

**Best Practice:** Follow consistent RESTful noun-based naming (e.g., `/api/v1/users`, `/api/v1/transfers`, `/api/v1/payments`).

---

### GAP-API-05: OpenAPI Spec Uses Wrong Starter

**Severity:** Medium | **Effort:** Small

**Current State:** Core-banking-service, user-service, fund-transfer-service, and utility-payment-service all declare:
```
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```
However, these services use Spring MVC (not WebFlux). Only the API gateway uses WebFlux.

**Impact:** The wrong starter may not correctly generate OpenAPI documentation for Spring MVC controllers. The correct dependency is `springdoc-openapi-starter-webmvc-ui`.

---

### GAP-API-06: No Standardized Response Envelope

**Severity:** Medium | **Effort:** Medium

**Current State:** Success responses vary by endpoint:
- Some return entities directly
- Some return `{ message, transactionId }`
- Error responses return `{ code, message }` or a raw string

**Impact:** Clients must handle different response shapes per endpoint. No consistent way to check for success/failure.

**Best Practice:** Adopt a standard response envelope, e.g., `{ status, data, error }` across all endpoints.

---

## 6. Observability

### GAP-OBS-01: No Structured Logging

**Severity:** Medium | **Effort:** Small

**Current State:** Services use `@Slf4j` with default Logback configuration. Log messages are unstructured text with string concatenation:
```java
log.info("Reading account by ID {}", accountNumber);
log.info("Sending fund transfer request {}" + request.toString());
```
Note: the second example uses string concatenation instead of SLF4J placeholders.

**Impact:** Logs are not machine-parseable. Log aggregation tools (ELK, Datadog) cannot efficiently index or search fields. The string concatenation wastes resources even when the log level is disabled.

**Best Practice:** Use structured logging (JSON format via Logback's `LogstashEncoder` or similar). Always use SLF4J `{}` placeholders.

---

### GAP-OBS-02: No Custom Health Checks

**Severity:** Medium | **Effort:** Small

**Current State:** Spring Boot Actuator is included in all services, providing `/actuator/health` by default. However, no custom health indicators are implemented for critical dependencies (database connectivity, Keycloak reachability, config server availability).

**Impact:** The health endpoint only reports basic UP/DOWN. It doesn't reveal whether critical integrations are healthy.

**Best Practice:** Implement `HealthIndicator` beans for Keycloak, RabbitMQ (when added), and external service dependencies.

---

### GAP-OBS-03: No Metrics Endpoints Configured

**Severity:** Medium | **Effort:** Small

**Current State:** `spring-boot-starter-actuator` is included but there is no configuration for Prometheus metrics export or custom business metrics. The README mentions Prometheus as a technology but no `micrometer-registry-prometheus` dependency exists.

**Impact:** No operational metrics for request rates, error rates, latencies, or business KPIs.

**Best Practice:** Add `micrometer-registry-prometheus` dependency and configure the `/actuator/prometheus` endpoint.

---

### GAP-OBS-04: Incomplete Distributed Tracing Coverage

**Severity:** Low | **Effort:** Small

**Current State:** Micrometer tracing libraries are included in all business services, which provides automatic trace propagation. However:
- No custom spans are added for important business operations.
- No span tags for business context (e.g., transaction ID, account number).
- Zipkin endpoint configuration depends on externalized config (not verifiable from source).

**Impact:** Traces show HTTP call chains but lack business-level context for debugging.

---

### GAP-OBS-05: Sensitive Data Logged in Request Objects

**Severity:** High | **Effort:** Small

**Current State:** Controllers and services log full request objects:
```java
log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
log.info("Utility payment processing {}", paymentRequest.toString());
```
These request objects contain account numbers and financial amounts. The `User` DTO contains `password` field.

**Impact:** Sensitive financial data and potentially passwords appear in log files.

**Best Practice:** Implement custom `toString()` methods that mask sensitive fields, or use dedicated log-safe representations.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

**Current State:** All inter-service communication uses OpenFeign without any circuit breaker (Resilience4j, Hystrix, or Spring Cloud Circuit Breaker). If core-banking-service goes down, fund-transfer-service and utility-payment-service will hang until the Feign client times out on every request.

**Impact:** A single service failure cascades to all dependent services. No graceful degradation.

**Best Practice:** Add Resilience4j circuit breakers to all Feign clients. Configure open/half-open/closed thresholds.

---

### GAP-RES-02: No Retry Policies

**Severity:** High | **Effort:** Small

**Current State:** No retry configuration on Feign clients. Transient network errors or brief service restarts cause immediate failures.

**Impact:** Temporary glitches that would self-heal in seconds cause user-visible errors.

**Best Practice:** Add Feign or Resilience4j retry configuration with exponential backoff. Be careful to only retry idempotent operations or add idempotency keys.

---

### GAP-RES-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

**Current State:** No explicit timeout configuration for Feign clients, database connections, or HTTP clients. Default timeouts (which may be infinite or very long) are used.

**Impact:** Slow or unresponsive dependencies can cause thread pool exhaustion and cascading failures.

**Best Practice:** Configure explicit connect and read timeouts for all Feign clients and connection pools.

---

### GAP-RES-04: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

**Current State:** No fallback methods are defined for any Feign client. When a downstream service is unavailable, the raw exception propagates to the client.

**Impact:** Clients receive cryptic error messages. No graceful degradation (e.g., returning cached data, queuing requests for later).

---

### GAP-RES-05: No Idempotency Protection on Financial Operations

**Severity:** Critical | **Effort:** Medium

**Current State:** The `POST /api/v1/transfer` and `POST /api/v1/utility-payment` endpoints have no idempotency key mechanism. If a client retries a request (e.g., due to a network timeout), the transfer or payment may be processed twice.

**Impact:** Duplicate financial transactions. Money can be transferred or debited multiple times.

**Best Practice:** Accept an idempotency key in the request header or body. Check for duplicates before processing. Return the original response for duplicate requests.

---

### GAP-RES-06: Balance Calculation Bug in Core Banking

**Severity:** Critical | **Effort:** Small

**Current State:** In `TransactionService.internalFundTransfer()` and `utilPayment()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` has already been reduced. This means `availableBalance = originalBalance - amount - amount` (double-subtracted).

Similarly for credit:
```java
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
```
The `availableBalance = originalBalance + amount + amount` (double-added).

**Impact:** Every fund transfer and utility payment corrupts the `availableBalance` field, progressively diverging from the correct value.

**Best Practice:** Either set both balances to the same value, or calculate them independently from the original values.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-module Gradle build | Medium | Small |
| GAP-ORG-02 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Inconsistent indentation in build files | Low | Small |
| GAP-ERR-01 | Error Handling | Inconsistent exception hierarchy | High | Medium |
| GAP-ERR-02 | Error Handling | Catch-all handler leaks stack traces | Critical | Small |
| GAP-ERR-03 | Error Handling | All errors return HTTP 400 | High | Small |
| GAP-ERR-04 | Error Handling | No error handling for failed Feign calls in orchestrators | High | Medium |
| GAP-ERR-05 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage for most services | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | contextLoads() tests fail without infrastructure | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | No authorization checks in downstream services | High | Medium |
| GAP-SEC-04 | Security | CSRF disabled without documentation | Low | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-02 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-API-03 | API Design | No filtering/sorting documentation | Low | Small |
| GAP-API-04 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-API-05 | API Design | Wrong OpenAPI starter (webflux vs webmvc) | Medium | Small |
| GAP-API-06 | API Design | No standardized response envelope | Medium | Medium |
| GAP-OBS-01 | Observability | No structured logging | Medium | Small |
| GAP-OBS-02 | Observability | No custom health checks | Medium | Small |
| GAP-OBS-03 | Observability | No metrics endpoints | Medium | Small |
| GAP-OBS-04 | Observability | Incomplete tracing coverage | Low | Small |
| GAP-OBS-05 | Observability | Sensitive data in logs | High | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | Critical | Medium |
| GAP-RES-06 | Resilience | Balance calculation bug (double-subtraction) | Critical | Small |
