# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### Current State

- Six independent Gradle projects with **no shared root build file** (no multi-module Gradle setup).
- All services use the same base package `com.javatodev.finance` with a consistent internal structure: `controller`, `service`, `model` (dto, entity, mapper), `repository`, `exception`, `configuration`.
- Significant **code duplication** across services:
  - `AuditAware` class is copy-pasted in 3 services (user, fund-transfer, utility-payment).
  - `BaseMapper` interface is duplicated in 4 services.
  - `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` are duplicated in 4 services.
  - `TransactionStatus` enum is duplicated in 2 services.
  - `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are duplicated in 3 services.
  - `CustomFeignClientConfiguration` is duplicated in 2 services.
  - `AuditConfig`, `AuditorAwareConfig` are duplicated in 3 services.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| CO-1 | **No shared library / multi-module build.** Duplicated code (exception classes, audit, mappers, filters) across services creates maintenance risk. Any bug fix must be applied in 3-4 places. | High | Large |
| CO-2 | **No root Gradle build file.** Each service must be built independently. No unified `./gradlew build` from the project root. | Medium | Medium |
| CO-3 | **Inconsistent package naming for Feign configuration.** User service uses `configuration.feign.CustomFeignClientConfiguration`, while fund-transfer and utility-payment use `configuration.CustomFeignClientConfiguration`. | Low | Small |
| CO-4 | **Inconsistent package naming for Feign client.** Fund-transfer service places Feign client under `service.rest.client`, while user and utility-payment services use `service.rest`. | Low | Small |
| CO-5 | **Repository package inconsistency.** Core-banking and user services use `model.repository`, while utility-payment uses `repository` (top-level). Fund-transfer uses `model.repository`. | Low | Small |

---

## 2. Error Handling

### Current State

- Each service has a `GlobalExceptionHandler` (`@ControllerAdvice`) extending `ResponseEntityExceptionHandler`.
- Custom exception hierarchy: `SimpleBankingGlobalException` → `EntityNotFoundException`, `InsufficientFundsException`, etc.
- Error response format: `{ code, message }` via `ErrorResponse` class.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| EH-1 | **All errors return HTTP 400 Bad Request.** The `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` for ALL exceptions, including `EntityNotFoundException` (should be 404), `InsufficientFundsException` (should be 422), and generic `Exception` (should be 500). | Critical | Small |
| EH-2 | **Generic exception handler leaks internal details.** The catch-all handler returns `"Exception occur inside API " + e`, which exposes stack traces and internal implementation details to API consumers. | Critical | Small |
| EH-3 | **Inconsistent error response format.** Core-banking `GlobalExceptionHandler` constructs `ErrorResponse` via builder, fund-transfer uses constructor `new ErrorResponse(code, message)`, and the catch-all handler returns a plain string. The `ErrorResponse` class uses `@Builder` in core-banking/user/utility-payment but is constructed differently in fund-transfer. | Medium | Small |
| EH-4 | **No error handling for Feign client failures.** Only the user service has a `CustomFeignErrorDecoder`. Fund-transfer and utility-payment services have no Feign error decoder, meaning Feign exceptions will bubble up as unhandled errors. | High | Medium |
| EH-5 | **Missing exception types.** Fund-transfer and utility-payment services only define `SimpleBankingGlobalException`. They lack specific exceptions for their domain (e.g., `TransferNotFoundException`, `PaymentFailedException`). | Medium | Small |
| EH-6 | **No error response timestamp or trace ID.** Error responses lack timestamps, request IDs, or trace IDs, making debugging in production difficult. | Medium | Small |

---

## 3. Testing

### Current State

- Core Banking Service has 3 unit test classes: `AccountServiceTest` (6 tests), `TransactionServiceTest` (10 tests), `UserServiceTest` (unknown).
- All other services have only a single context-load test (`*ApplicationTests.java`).
- Tests use JUnit 5 and Mockito with manual mock setup (no `@MockBean` or Spring test context).
- H2 in-memory database available as test dependency in core-banking, user, and fund-transfer services.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| TE-1 | **Near-zero test coverage for 3 of 4 business services.** User service, fund-transfer service, and utility-payment service have NO unit tests beyond the auto-generated context-load test. | Critical | Large |
| TE-2 | **No integration tests.** No tests verify actual database operations, Spring context wiring, or API endpoint behavior. | High | Large |
| TE-3 | **No contract tests between services.** Services communicate via Feign clients, but there are no Spring Cloud Contract or Pact tests to verify API compatibility. | High | Large |
| TE-4 | **No test coverage reporting.** No JaCoCo or equivalent coverage tool configured. | Medium | Small |
| TE-5 | **Context-load tests may fail without infrastructure.** The `*ApplicationTests.java` files use `@SpringBootTest` but require Config Server, Eureka, and MySQL. They likely fail in CI without Docker infrastructure. | Medium | Medium |
| TE-6 | **No end-to-end tests.** No automated tests exercise the full flow through the API Gateway. | Medium | Large |

---

## 4. Security

### Current State

- API Gateway enforces OAuth2/JWT via Keycloak at the edge (`SecurityConfiguration`).
- User registration endpoint is explicitly permitted without authentication.
- Actuator endpoints are permitted without authentication.
- Gateway injects `X-Auth-Id` header into proxied requests.
- Downstream services extract `X-Auth-Id` via `AppAuthUserFilter`.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| SE-1 | **Downstream services have no authentication.** Core-banking, user, fund-transfer, and utility-payment services accept all HTTP requests without verifying JWT tokens. If accessed directly (bypassing the gateway), they are fully open. | Critical | Medium |
| SE-2 | **Hardcoded credentials in source code.** MySQL root password, DB user password, Keycloak admin password, and test credentials are hardcoded in `docker-compose.yml`, `Dockerfile`, and `privileges.sql` rather than using environment variables or a secrets manager. | Critical | Medium |
| SE-3 | **No input validation.** None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.). Malformed or incomplete requests are accepted. | High | Small |
| SE-4 | **CSRF disabled without justification.** The API Gateway disables CSRF protection (`csrf().disable()`). While acceptable for stateless JWT APIs, this should be documented. | Low | Small |
| SE-5 | **No role-based access control (RBAC).** All authenticated users can access all endpoints. There is no distinction between admin and regular user roles. | High | Medium |
| SE-6 | **Keycloak singleton is not thread-safe.** `KeycloakProperties.getInstance()` uses a non-synchronized static singleton pattern, which is a classic double-checked locking issue. | Medium | Small |
| SE-7 | **No dependency vulnerability scanning.** No OWASP Dependency Check, Snyk, or equivalent tool is configured. | Medium | Small |
| SE-8 | **Password logged in plaintext.** User registration logs `request.toString()` which includes the password field. | High | Small |
| SE-9 | **`X-Auth-Id` header can be spoofed.** Downstream services trust the `X-Auth-Id` header without verifying it came from the gateway. Any direct caller can set this header. | High | Medium |

---

## 5. API Design

### Current State

- RESTful URL structure with `/api/v1/` prefix.
- JSON request/response format.
- OpenAPI/Swagger dependency included (`springdoc-openapi-starter-webflux-ui`) in 4 services.
- Pageable support on list endpoints via Spring Data `Pageable`.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| AD-1 | **Raw `ResponseEntity` without generic type.** Most controller methods return `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This breaks OpenAPI schema generation. | High | Small |
| AD-2 | **No API versioning strategy.** URL-based versioning (`/api/v1/`) is used but there is no mechanism or convention for introducing `v2`. | Low | Small |
| AD-3 | **Inconsistent endpoint naming.** User service uses `/bank-users`, core service uses `/user`. The Postman collection references `/bank-user` (singular) while the actual endpoint is `/bank-users` (plural). | Medium | Small |
| AD-4 | **No standardized pagination response wrapper.** List endpoints return raw `List<T>` instead of a paginated wrapper with `totalElements`, `totalPages`, `currentPage`, etc. Page metadata is lost. | High | Medium |
| AD-5 | **No filtering or sorting parameters.** List endpoints accept `Pageable` for paging but expose no documented filtering capabilities. | Low | Medium |
| AD-6 | **Wrong Swagger dependency.** Services use `springdoc-openapi-starter-webflux-ui` (WebFlux variant) but run on Spring MVC (WebMVC). This may cause runtime issues or incomplete documentation. | Medium | Small |
| AD-7 | **No consistent response envelope.** Success responses vary: some return entities directly, others return `{ message, transactionId }`. There is no unified response wrapper. | Medium | Medium |
| AD-8 | **Missing DELETE endpoints.** No endpoints for deleting or deactivating users, accounts, or transfers. | Low | Medium |

---

## 6. Observability

### Current State

- Spring Boot Actuator enabled on all services.
- Micrometer Tracing with Brave bridge for distributed tracing.
- Zipkin reporter configured for trace export.
- `git.properties` generated by Gradle plugin for `/actuator/info` endpoint.
- `@Slf4j` logging annotations present on controllers and services.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| OB-1 | **No structured logging.** Log output uses default Spring Boot format (plaintext). No JSON logging configuration for log aggregation tools (ELK, Datadog, CloudWatch). | Medium | Small |
| OB-2 | **Inconsistent log levels and messages.** Some controllers log at `info` with request details, others don't log at all. Service layer logging is minimal. | Low | Small |
| OB-3 | **No custom health indicators.** Health checks only use default Spring Boot health (`/actuator/health`). No custom checks for database connectivity, Keycloak availability, or downstream service reachability. | Medium | Small |
| OB-4 | **No Prometheus metrics endpoint.** Despite Prometheus being listed in the README tech stack, there is no `micrometer-registry-prometheus` dependency in any service. `/actuator/prometheus` is not available. | High | Small |
| OB-5 | **No custom business metrics.** No counters, gauges, or timers for business operations (e.g., fund transfer count, payment success rate, average transfer amount). | Medium | Medium |
| OB-6 | **Tracing coverage unclear.** While tracing dependencies are included, there is no verification that trace context propagates correctly through Feign calls, and no sampling rate configuration is visible. | Medium | Small |
| OB-7 | **Sensitive data logged.** User registration logs the full request object including password. Fund transfer logs include full request details. | High | Small |

---

## 7. Resilience

### Current State

- `wait-for-it.sh` used in Docker for startup ordering.
- No resilience libraries (Resilience4j, Spring Retry, Hystrix) in any `build.gradle`.
- Feign clients make synchronous calls with no timeout, retry, or fallback configuration.

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| RE-1 | **No circuit breakers.** Feign calls to core-banking-service have no circuit breaker. If core-banking is down, all requests to fund-transfer and utility-payment services will hang or fail with unhandled exceptions. | Critical | Medium |
| RE-2 | **No retry policies.** Transient network failures between services will cause immediate request failure. No Spring Retry or Resilience4j retry configuration. | High | Small |
| RE-3 | **No timeout configuration.** Feign clients and RestTemplate calls have no explicit timeout settings. Default timeouts may be very long or infinite, causing thread pool exhaustion. | Critical | Small |
| RE-4 | **No fallback behavior.** No Feign fallback classes or factory methods defined. Service degradation is not graceful. | Medium | Medium |
| RE-5 | **No bulkhead pattern.** All Feign calls share the same thread pool. A slow core-banking-service could exhaust all threads, blocking unrelated requests. | Medium | Medium |
| RE-6 | **No idempotency protection.** Fund transfer and utility payment endpoints have no idempotency keys. Duplicate requests (due to retries or network issues) could result in duplicate transactions. | Critical | Medium |
| RE-7 | **No database connection pool configuration.** Default HikariCP settings are used without tuning for connection pool size, timeout, or leak detection. | Medium | Small |
| RE-8 | **Transaction integrity risk.** The `internalFundTransfer` method debits and credits accounts in sequence. If the process fails after debiting but before crediting, funds are lost. There is no compensation or saga pattern. | Critical | Large |
| RE-9 | **Available balance calculation bug.** In `TransactionService.internalFundTransfer()`, `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already reduced. This double-subtracts, causing incorrect available balances. Same bug exists in `utilPayment()`. | Critical | Small |

---

## Summary Matrix

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 1 | 3 | 5 |
| Error Handling | 2 | 1 | 2 | 0 | 5 (+1 EH-6 Medium) |
| Testing | 1 | 2 | 3 | 0 | 6 |
| Security | 2 | 3 | 2 | 2 | 9 |
| API Design | 0 | 2 | 3 | 3 | 8 |
| Observability | 0 | 2 | 4 | 1 | 7 |
| Resilience | 4 | 1 | 3 | 0 | 8 (+1 RE-9 Critical) |
| **Total** | **9** | **12** | **18** | **9** | **48** |
