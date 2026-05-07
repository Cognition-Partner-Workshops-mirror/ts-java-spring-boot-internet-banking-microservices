# Engineering Standards Gap Analysis

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Assessed:** 2026-05-07
> **Methodology:** Manual code review against industry engineering best practices

---

## Severity & Effort Definitions

| Severity | Description |
|----------|-------------|
| **Critical** | Bugs or security issues that could cause data loss, financial errors, or breaches in production |
| **High** | Significant architectural or quality gaps that hinder reliability, maintainability, or security |
| **Medium** | Deviations from best practices that increase technical debt and reduce developer productivity |
| **Low** | Minor improvements for polish, consistency, or developer experience |

| Effort | Description |
|--------|-------------|
| **Small** | < 1 day, isolated changes |
| **Medium** | 1-3 days, cross-cutting but bounded |
| **Large** | 3+ days, significant refactoring or new infrastructure |

---

## 1. Code Organization

### GAP-ORG-01: Duplicated Code Across Services
- **Severity:** Medium | **Effort:** Medium
- **Finding:** Multiple classes are copy-pasted across services with minor variations:
  - `BaseMapper` interface exists in 4 services with identical code.
  - `AuditAware` mapped superclass is duplicated in user-service, fund-transfer-service, and utility-payment-service.
  - `ApiRequestContext`, `ApiRequestContextHolder`, and `AppAuthUserFilter` are duplicated across fund-transfer and utility-payment services.
  - `ErrorResponse`, `SimpleBankingGlobalException`, and `GlobalExceptionHandler` are duplicated across all 4 business services.
  - `CustomFeignClientConfiguration` is duplicated in fund-transfer and utility-payment services.
- **Recommendation:** Extract shared code into a `common` or `shared-library` Gradle module that all services depend on.

### GAP-ORG-02: Inconsistent Package Structure
- **Severity:** Low | **Effort:** Small
- **Finding:** Package naming is inconsistent across services:
  - Fund transfer: `model.repository.FundTransferRepository` vs. Core banking: `repository.BankAccountRepository` (no `model` prefix).
  - User service: `configuration.feign.CustomFeignClientConfiguration` vs. Fund transfer: `configuration.CustomFeignClientConfiguration`.
  - User service: `service.rest.BankingCoreRestClient` vs. Fund transfer: `service.rest.client.BankingCoreFeignClient`.
- **Recommendation:** Standardize on a single package convention across all services.

### GAP-ORG-03: No Multi-Module Gradle Build
- **Severity:** Low | **Effort:** Medium
- **Finding:** Each service is a standalone Gradle project with its own `gradlew` wrapper, `settings.gradle`, and independent `build.gradle`. There is no root `settings.gradle` or shared dependency version catalog. This leads to version drift risk and makes bulk upgrades difficult.
- **Recommendation:** Create a root `settings.gradle` including all services and a shared version catalog or platform BOM for dependency alignment.

### GAP-ORG-04: Mapper Instantiation Not Spring-Managed
- **Severity:** Low | **Effort:** Small
- **Finding:** Mappers are instantiated inline (`new BankAccountMapper()`) rather than injected via Spring. This bypasses Spring's lifecycle and makes unit testing harder.
- **Recommendation:** Annotate mappers with `@Component` and inject them via constructor injection.

---

## 2. Error Handling

### GAP-ERR-01: All Errors Return HTTP 400
- **Severity:** High | **Effort:** Small
- **Finding:** Every `GlobalExceptionHandler` across all services returns `ResponseEntity.badRequest()` (HTTP 400) for ALL exceptions, including:
  - `EntityNotFoundException` (should be 404)
  - `InsufficientFundsException` (should be 422 Unprocessable Entity)
  - Generic `Exception` (should be 500 Internal Server Error)
- **Recommendation:** Map exceptions to appropriate HTTP status codes (404, 409, 422, 500).

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details
- **Severity:** High | **Effort:** Small
- **Finding:** The catch-all `handleException(Exception e)` returns the raw exception string: `"Exception occur inside API " + e`. This can leak stack traces, class names, and internal state to API consumers.
- **Recommendation:** Return a generic error message to the client and log the full exception server-side.

### GAP-ERR-03: Inconsistent Error Response Format
- **Severity:** Medium | **Effort:** Small
- **Finding:** Custom exceptions return a structured `ErrorResponse` (with `code` and `message`), but the generic exception handler returns a plain string. Consumers cannot reliably parse error responses.
- **Recommendation:** Always return the `ErrorResponse` structure, including for unexpected errors.

### GAP-ERR-04: No Feign Error Decoder in Most Services
- **Severity:** Medium | **Effort:** Small
- **Finding:** Only the user service has a `CustomFeignErrorDecoder`. Fund transfer and utility payment services have no error decoder, meaning Feign errors are wrapped as generic `FeignException` and not translated to domain exceptions. The user receives raw Feign error payloads.
- **Recommendation:** Add a `CustomFeignErrorDecoder` to all services that use Feign clients.

### GAP-ERR-05: No Compensation/Rollback on Feign Failure
- **Severity:** Critical | **Effort:** Medium
- **Finding:** In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, a local entity is saved with status `PENDING`/`PROCESSING`, then a Feign call is made. If the Feign call fails, the local entity remains in `PENDING`/`PROCESSING` forever -- there is no catch block, no status update to `FAILED`, and no retry mechanism.
- **Recommendation:** Wrap the Feign call in a try-catch, update the entity status to `FAILED` on error, and consider implementing a saga pattern or outbox pattern for distributed transactions.

---

## 3. Testing

### GAP-TST-01: Tests Only Exist for Core Banking Service
- **Severity:** High | **Effort:** Large
- **Finding:** Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services only have the default Spring Boot context-load test (`*ApplicationTests.java`), which require database and config server connections and will fail in isolation.
- **Recommendation:** Add unit tests for service classes in all services, targeting at minimum the service layer.

### GAP-TST-02: No Integration Tests
- **Severity:** High | **Effort:** Large
- **Finding:** There are no integration tests that verify REST endpoints, database interactions with actual queries, or Spring context behavior. The existing tests are pure unit tests with Mockito mocks.
- **Recommendation:** Add `@WebMvcTest` controller tests and `@DataJpaTest` repository tests. Consider using Testcontainers for MySQL integration tests.

### GAP-TST-03: No Contract Tests Between Services
- **Severity:** Medium | **Effort:** Large
- **Finding:** Services communicate via OpenFeign with no contract testing (e.g., Spring Cloud Contract, Pact). If core-banking-service changes an API, downstream services will break silently at runtime.
- **Recommendation:** Implement consumer-driven contract tests using Spring Cloud Contract or Pact.

### GAP-TST-04: Application Context Tests Will Fail in CI
- **Severity:** Medium | **Effort:** Small
- **Finding:** `*ApplicationTests.java` classes attempt to load the full Spring context, which requires a running Config Server and Eureka. These tests will fail in any CI environment without those services.
- **Recommendation:** Either disable these tests or configure them with test profiles that disable cloud config and Eureka.

---

## 4. Security

### GAP-SEC-01: Hardcoded Database Credentials in Docker Compose
- **Severity:** Critical | **Effort:** Small
- **Finding:** `docker-compose.yml` and `mysql/Dockerfile` contain plaintext credentials:
  - MySQL root: `woVERANKliGharym`
  - MySQL app user: `oPItyPticIAt`
  - Keycloak admin: `admin` / `password`
  - Keycloak DB: `keycloak` / `password`
- **Recommendation:** Use Docker secrets or environment variable files (`.env`) excluded from version control. At minimum, add a `.env.example` with placeholder values.

### GAP-SEC-02: Test Credentials Committed in README
- **Severity:** High | **Effort:** Small
- **Finding:** The README contains production-style test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`. These could be mistakenly used in deployed environments.
- **Recommendation:** Remove credentials from README. Provide them via secure channels or auto-generate them during setup.

### GAP-SEC-03: No Input Validation on Request Bodies
- **Severity:** Critical | **Effort:** Medium
- **Finding:** No `@Valid` annotations, no Bean Validation constraints (`@NotNull`, `@Min`, `@Size`, etc.) on any DTO. Examples:
  - `FundTransferRequest.amount` accepts null or negative values.
  - `User.email` has no format validation.
  - `UtilityPaymentRequest.providerId` can be null.
- **Recommendation:** Add Jakarta Bean Validation annotations to all DTOs and `@Valid` on controller method parameters.

### GAP-SEC-04: CSRF Disabled on API Gateway
- **Severity:** Low | **Effort:** Small
- **Finding:** `SecurityConfiguration` disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. For a pure REST API with JWT authentication, this is acceptable but should be explicitly documented.
- **Recommendation:** Add a code comment documenting the rationale.

### GAP-SEC-05: Keycloak Singleton Not Thread-Safe
- **Severity:** Medium | **Effort:** Small
- **Finding:** `KeycloakProperties.getInstance()` uses a non-synchronized static singleton. Under concurrent requests, multiple Keycloak instances could be created (classic double-checked locking issue).
- **Recommendation:** Use `@Bean` with Spring's singleton scope, or add proper synchronization.

### GAP-SEC-06: Password Transmitted in Plain User DTO
- **Severity:** High | **Effort:** Small
- **Finding:** The `User` DTO in user-service includes a `password` field. This field is deserialized from the registration request AND serialized in the response. The password is returned to the client in the registration response.
- **Recommendation:** Use `@JsonProperty(access = WRITE_ONLY)` on the password field, or use separate request/response DTOs.

### GAP-SEC-07: No Rate Limiting
- **Severity:** Medium | **Effort:** Medium
- **Finding:** The API Gateway has no rate limiting configuration. Financial APIs are prime targets for abuse.
- **Recommendation:** Add Spring Cloud Gateway rate limiting filters (e.g., `RequestRateLimiter` with Redis).

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters
- **Severity:** Medium | **Effort:** Small
- **Finding:** Most controller methods return raw `ResponseEntity` instead of `ResponseEntity<SpecificType>`. This prevents compile-time type checking and degrades OpenAPI spec generation. Only the user-service controllers use typed responses.
- **Recommendation:** Add explicit generic types to all `ResponseEntity` returns.

### GAP-API-02: No API Versioning Strategy
- **Severity:** Low | **Effort:** Small
- **Finding:** All endpoints use `/api/v1/` but there is no documented versioning strategy, no version negotiation, and no plan for v2.
- **Recommendation:** Document the versioning strategy (URL-based is fine) and establish guidelines for introducing breaking changes.

### GAP-API-03: Inconsistent Pagination Response
- **Severity:** Medium | **Effort:** Small
- **Finding:** Paginated endpoints accept `Pageable` parameters but return `List<T>` instead of a `Page<T>` wrapper. Clients have no access to total count, total pages, or current page metadata.
- **Recommendation:** Return Spring's `Page<T>` or a custom pagination envelope with metadata.

### GAP-API-04: No Filtering or Sorting Parameters
- **Severity:** Low | **Effort:** Medium
- **Finding:** List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, etc.) accept `Pageable` but provide no explicit filtering (e.g., by date range, status, account).
- **Recommendation:** Add query parameters for common filters (date range, status, account number).

### GAP-API-05: PATCH Endpoint Uses Wrong Semantics
- **Severity:** Low | **Effort:** Small
- **Finding:** `PATCH /api/v1/bank-users/update/{id}` includes "update" in the path, which is redundant with the PATCH method. RESTful convention would be `PATCH /api/v1/bank-users/{id}`.
- **Recommendation:** Remove "update" from the path.

### GAP-API-06: OpenAPI Spec Uses WebFlux Starter on Non-Reactive Services
- **Severity:** Low | **Effort:** Small
- **Finding:** All non-gateway services use `springdoc-openapi-starter-webflux-ui` despite being Spring MVC (servlet-based) applications. The correct dependency is `springdoc-openapi-starter-webmvc-ui`.
- **Recommendation:** Switch to `springdoc-openapi-starter-webmvc-ui` for MVC services.

---

## 6. Observability

### GAP-OBS-01: No Structured Logging
- **Severity:** Medium | **Effort:** Medium
- **Finding:** All services use default Logback text format. There is no JSON structured logging, which makes log aggregation and parsing difficult in production.
- **Recommendation:** Configure Logback to output JSON (e.g., `logstash-logback-encoder`) and include trace IDs.

### GAP-OBS-02: Health Check Endpoints Not Customized
- **Severity:** Low | **Effort:** Small
- **Finding:** All services include `spring-boot-starter-actuator`, which provides `/actuator/health`. However, no custom health indicators are configured (e.g., database connectivity, Keycloak availability, Feign circuit state).
- **Recommendation:** Add custom health indicators for critical dependencies.

### GAP-OBS-03: No Metrics Endpoints Beyond Defaults
- **Severity:** Medium | **Effort:** Medium
- **Finding:** While Micrometer is included (via actuator + tracing), there are no custom business metrics (e.g., fund transfer count, payment processing time, error rates by type).
- **Recommendation:** Add Micrometer counters/timers for key business operations. Consider exposing a Prometheus endpoint.

### GAP-OBS-04: Sensitive Data in Logs
- **Severity:** High | **Effort:** Small
- **Finding:** Controllers log full request `toString()` output, which may include sensitive data:
  - `UserController`: `log.info("Creating user with {}", request.toString())` -- could log passwords.
  - `FundTransferController`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` -- logs account numbers and amounts.
  - Feign client logging is set to `Level.FULL`, which logs request/response headers and bodies including auth tokens.
- **Recommendation:** Mask sensitive fields in log output. Reduce Feign logging to `BASIC` or `HEADERS` in production.

### GAP-OBS-05: No Distributed Tracing Sampling Configuration
- **Severity:** Low | **Effort:** Small
- **Finding:** Tracing is configured but no sampling rate is set. The default may be 10% or 100% depending on the version, which could cause performance issues or missed traces.
- **Recommendation:** Explicitly configure `management.tracing.sampling.probability` (e.g., `1.0` for dev, `0.1` for production).

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
- **Severity:** High | **Effort:** Medium
- **Finding:** All inter-service communication is via OpenFeign with no circuit breaker configuration. If `core-banking-service` goes down, all upstream services will hang on Feign calls and eventually exhaust thread pools.
- **Recommendation:** Add Resilience4j circuit breakers to Feign clients (Spring Cloud CircuitBreaker + Feign integration).

### GAP-RES-02: No Retry Policies
- **Severity:** Medium | **Effort:** Small
- **Finding:** No retry configuration exists for Feign clients. Transient network errors will immediately fail the request.
- **Recommendation:** Configure Feign retry with exponential backoff. Ensure retries are only applied to idempotent operations (GET requests) to avoid double-processing financial transactions.

### GAP-RES-03: No Timeout Configuration
- **Severity:** High | **Effort:** Small
- **Finding:** No explicit connection or read timeouts are configured for Feign clients, database connections, or the API Gateway. Default timeouts (often infinite or very long) can cause cascading failures.
- **Recommendation:** Set explicit timeouts:
  - Feign: `connectTimeout` and `readTimeout` in application config.
  - Database: `spring.datasource.hikari.connectionTimeout` and `maxLifetime`.
  - Gateway: per-route timeouts.

### GAP-RES-04: No Fallback Behavior
- **Severity:** Medium | **Effort:** Medium
- **Finding:** No fallback methods or default responses are configured for any Feign client. When a downstream service is unavailable, the error propagates raw to the client.
- **Recommendation:** Add `@FeignClient(fallback = ...)` or `fallbackFactory` classes to provide degraded responses.

### GAP-RES-05: No Idempotency Keys for Financial Operations
- **Severity:** Critical | **Effort:** Medium
- **Finding:** Fund transfer and utility payment endpoints have no idempotency mechanism. If a client retries a request (e.g., due to network timeout), the same transfer/payment will be processed multiple times, resulting in **double-charging**.
- **Recommendation:** Accept an idempotency key header (e.g., `Idempotency-Key`) and check for duplicate requests before processing.

### GAP-RES-06: Balance Calculation Bug (Double Subtraction)
- **Severity:** Critical | **Effort:** Small
- **Finding:** In `TransactionService.internalFundTransfer()` (lines 90-91) and `TransactionService.utilPayment()` (lines 63-64):
  ```java
  fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
  fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
  ```
  After the first line, `actualBalance` is already reduced. The second line subtracts `amount` again from the already-reduced `actualBalance`, making `availableBalance = originalBalance - 2*amount`. The same pattern exists for the credit side.
- **Recommendation:** Fix to: `fromAccount.setAvailableBalance(fromAccount.getActualBalance())` or compute both from the original balance.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-02 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-03 | Code Organization | No multi-module Gradle build | Low | Medium |
| GAP-ORG-04 | Code Organization | Mappers not Spring-managed | Low | Small |
| GAP-ERR-01 | Error Handling | All errors return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic handler leaks internal details | High | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-04 | Error Handling | No Feign error decoder in most services | Medium | Small |
| GAP-ERR-05 | Error Handling | No compensation on Feign failure | Critical | Medium |
| GAP-TST-01 | Testing | Tests only in core-banking-service | High | Large |
| GAP-TST-02 | Testing | No integration tests | High | Large |
| GAP-TST-03 | Testing | No contract tests | Medium | Large |
| GAP-TST-04 | Testing | Context tests fail in CI | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded DB credentials in Docker Compose | Critical | Small |
| GAP-SEC-02 | Security | Test credentials in README | High | Small |
| GAP-SEC-03 | Security | No input validation | Critical | Medium |
| GAP-SEC-04 | Security | CSRF disabled (acceptable, needs docs) | Low | Small |
| GAP-SEC-05 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-06 | Security | Password in User response | High | Small |
| GAP-SEC-07 | Security | No rate limiting | Medium | Medium |
| GAP-API-01 | API Design | Raw ResponseEntity types | Medium | Small |
| GAP-API-02 | API Design | No versioning strategy documented | Low | Small |
| GAP-API-03 | API Design | Pagination returns List instead of Page | Medium | Small |
| GAP-API-04 | API Design | No filtering/sorting parameters | Low | Medium |
| GAP-API-05 | API Design | Redundant "update" in PATCH path | Low | Small |
| GAP-API-06 | API Design | Wrong OpenAPI starter dependency | Low | Small |
| GAP-OBS-01 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-02 | Observability | Health checks not customized | Low | Small |
| GAP-OBS-03 | Observability | No custom business metrics | Medium | Medium |
| GAP-OBS-04 | Observability | Sensitive data in logs | High | Small |
| GAP-OBS-05 | Observability | No tracing sampling config | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | Medium | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency keys | Critical | Medium |
| GAP-RES-06 | Resilience | Balance calculation bug (double subtract) | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 10 |
| Medium | 14 |
| Low | 7 |
| **Total** | **36** |
