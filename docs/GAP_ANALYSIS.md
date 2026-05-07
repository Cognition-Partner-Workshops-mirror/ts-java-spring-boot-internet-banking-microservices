# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### 1.1 No Shared Library / Common Module

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Location** | All services |

**Finding**: Each service independently defines identical classes that could be shared:
- `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` (duplicated in 4 services)
- `BaseMapper` interface (duplicated in 4 services)
- `AuditAware` base entity (duplicated in 3 services)
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` (duplicated in 3 services)
- `TransactionStatus` enum (duplicated in 2 services)

**Impact**: Changes to shared patterns (e.g., error response format) require updating every service independently, increasing risk of drift.

**Recommendation**: Create a `banking-common` shared library module published as a local Gradle dependency containing exception classes, base entities, DTOs, mappers, and filter infrastructure.

### 1.2 No Root-Level Multi-Project Gradle Build

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | Project root |

**Finding**: Each service has its own standalone `build.gradle`. There is no root `settings.gradle` or `build.gradle` that aggregates them as a multi-module project.

**Impact**: Cannot run a single `./gradlew build` from the root. Each service must be built individually. Dependency versions are repeated across all `build.gradle` files.

**Recommendation**: Introduce a root `settings.gradle` with `include` for all service subprojects and a root `build.gradle` with shared dependency versions (via `subprojects {}` or a version catalog).

### 1.3 Inconsistent Package Structure

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | Multiple services |

**Finding**: Package naming varies across services:
- User service: `model.repository.UserRepository` vs. core-banking: `repository.BankAccountRepository` (no `model.` prefix)
- User service Feign client: `service.rest.BankingCoreRestClient` vs. fund-transfer: `service.rest.client.BankingCoreFeignClient` (different subpackage and naming)
- Utility payment: `model.rest.request/response` vs. fund-transfer: `model.dto.request/response`
- User service Feign config: `configuration.feign.CustomFeignClientConfiguration` vs. fund-transfer: `configuration.CustomFeignClientConfiguration`

**Impact**: Developers cannot predict where classes are located across services, increasing cognitive load.

**Recommendation**: Standardize on a canonical package layout (e.g., `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.mapper`, `exception`, `configuration`, `client`).

### 1.4 Mapper Instantiation via `new` Instead of DI

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | All services with mappers |

**Finding**: Mappers are instantiated directly in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans.

**Impact**: Cannot be mocked in tests, cannot leverage DI for dependencies, inconsistent with the rest of the codebase which uses `@RequiredArgsConstructor` injection.

**Recommendation**: Annotate mappers with `@Component` and inject them via constructor injection. Alternatively, adopt MapStruct for compile-time type-safe mapping.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns 400 for All Exceptions

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Location** | `GlobalExceptionHandler` in all 4 business services |

**Finding**: Every `GlobalExceptionHandler` has:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Issues**:
1. **All exceptions return HTTP 400**: Server errors (NPE, DB failures, timeout) incorrectly return 400 instead of 500.
2. **Stack trace leakage**: `e.toString()` is sent directly to the client, exposing internal class names and potentially sensitive information.
3. **Inconsistent response format**: The catch-all returns a plain string, while `SimpleBankingGlobalException` returns a structured `ErrorResponse`. Clients cannot rely on a consistent error schema.

**Recommendation**: Return 500 for unhandled exceptions with a generic message (no stack trace). Log the full exception server-side. Always return the structured `ErrorResponse` format.

### 2.2 Missing HTTP Status Code Differentiation

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Location** | `GlobalExceptionHandler` in all services |

**Finding**: `SimpleBankingGlobalException` always returns HTTP 400. There is no differentiation between:
- 404 Not Found (`EntityNotFoundException`)
- 409 Conflict (`UserAlreadyRegisteredException`)
- 422 Unprocessable Entity (`InsufficientFundsException`, `InvalidEmailException`)
- 400 Bad Request (malformed input)

**Recommendation**: Add specific `@ExceptionHandler` methods for each exception type with appropriate HTTP status codes. Consider using `@ResponseStatus` annotations or a status code field in the exception hierarchy.

### 2.3 No Input Validation

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Location** | All controllers |

**Finding**: No `@Valid` / `@Validated` annotations on any `@RequestBody` parameter. No Bean Validation (`jakarta.validation`) constraints on any DTO. For example:
- `FundTransferRequest.amount` accepts null or negative values
- `User.email` has no format validation
- `UtilityPaymentRequest.account` has no null check

**Impact**: Invalid data reaches the service and database layers, causing cryptic NullPointerExceptions or database constraint violations instead of clear 400 responses.

**Recommendation**: Add `spring-boot-starter-validation` dependency. Annotate DTOs with `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc. Add `@Valid` to controller method parameters.

### 2.4 No Error Handling for Feign Client Failures

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Location** | `FundTransferService`, `UtilityPaymentService` |

**Finding**: Feign calls to core-banking-service have no error handling. If the Feign call fails (network error, 4xx, 5xx), the fund-transfer or utility-payment entity remains in `PENDING`/`PROCESSING` status forever. Only the user-service has a `CustomFeignErrorDecoder`.

**Impact**: Silent failures with inconsistent state. No retry, no compensation, no status update to `FAILED`.

**Recommendation**: Add try-catch around Feign calls, update entity status to `FAILED` on exception, and implement `ErrorDecoder` consistently across all Feign clients.

---

## 3. Testing

### 3.1 Near-Zero Test Coverage Outside Core Banking

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |
| **Location** | All services except core-banking |

**Finding**: 
- **core-banking-service**: Has unit tests for `AccountService` (6 tests), `TransactionService` (10 tests), `UserService` (assumed, file exists).
- **All other services**: Only have empty Spring Boot context-load tests (`contextLoads()`) which would fail without a running Config Server / Eureka / MySQL.
- **No integration tests** exist anywhere.
- **No controller tests** (MockMvc / WebTestClient) exist.

**Impact**: No automated verification of business logic in 4 out of 6 services. Regressions go undetected.

**Recommendation**: 
1. Add unit tests for `FundTransferService`, `UtilityPaymentService`, `UserService` (user-service), `KeycloakUserService`.
2. Add controller-layer tests using `@WebMvcTest`.
3. Add integration tests using `@SpringBootTest` with Testcontainers for MySQL and WireMock for Feign dependencies.

### 3.2 No Contract Tests Between Services

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |
| **Location** | All inter-service communication |

**Finding**: No Spring Cloud Contract or Pact tests verify that the Feign client interfaces match the actual controller endpoints. Three services depend on core-banking-service's API shape:
- `BankingCoreFeignClient` (fund-transfer) -> `AccountController`, `TransactionController`
- `BankingCoreRestClient` (utility-payment) -> `AccountController`, `TransactionController`
- `BankingCoreRestClient` (user-service) -> `UserController`

**Impact**: A change to core-banking-service's API could silently break all downstream services.

**Recommendation**: Adopt Spring Cloud Contract (provider-side) or Pact (consumer-side) to verify API contracts.

### 3.3 Context-Load Tests Require External Dependencies

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Location** | All service test classes |

**Finding**: The default `contextLoads()` tests in non-core services will fail because they try to connect to Config Server, Eureka, and MySQL. No test profiles disable these.

**Impact**: Cannot run `./gradlew test` for individual services without the full infrastructure running.

**Recommendation**: Add `src/test/resources/application.yml` with embedded datasource (H2), disabled Eureka/Config, and mock Feign clients. Only `core-banking-service` currently has a proper test config.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Location** | `docker-compose.yml`, `privileges.sql`, `README.md` |

**Finding**:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml`)
- MySQL app user password: `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin password: `password` (in `docker-compose.yml`)
- Keycloak DB password: `password` (in `docker-compose.yml`)
- Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` (in `README.md`)

**Impact**: Secrets in version control. Anyone with repo access has database and admin credentials.

**Recommendation**: Use Docker Compose secrets, `.env` files (gitignored), or a secrets manager. Replace hardcoded values with `${VARIABLE}` references.

### 4.2 CSRF Disabled Without Explanation

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Location** | `SecurityConfiguration.java` (API Gateway) |

**Finding**: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)` is set without documentation explaining why.

**Impact**: For a pure REST API with JWT Bearer tokens (no cookies), disabling CSRF is acceptable. However, if cookies are ever used (e.g., Keycloak session), this becomes a vulnerability.

**Recommendation**: Add a code comment explaining the rationale. If browser-based clients with cookies are planned, re-enable CSRF with proper token handling.

### 4.3 No Rate Limiting or Request Size Limits

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Location** | API Gateway |

**Finding**: No rate limiting is configured at the gateway level. No maximum request body size is enforced. A banking API without rate limiting is vulnerable to brute-force attacks and denial-of-service.

**Recommendation**: Add Spring Cloud Gateway's `RequestRateLimiter` filter with Redis backend. Configure `spring.codec.max-in-memory-size` and request body size limits.

### 4.4 Keycloak Singleton Not Thread-Safe

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Location** | `KeycloakProperties.java` |

**Finding**: The Keycloak client uses a static singleton with a non-synchronized null check:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact**: Race condition during startup could create multiple instances or return a partially initialized client.

**Recommendation**: Use `@Bean` method in a `@Configuration` class to leverage Spring's singleton scope, or use `synchronized` / double-checked locking.

### 4.5 Password Stored in DTO Without Masking

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Location** | `User.java` (user-service DTO) |

**Finding**: The `User` DTO has a `password` field that is logged (`log.info("Creating user with {}", request.toString())`) via Lombok's `@Data` which includes all fields in `toString()`. The password is also included in API responses since the same DTO is used for request and response.

**Impact**: Passwords appear in log files and API responses.

**Recommendation**: 
1. Separate request/response DTOs (don't reuse `User` for both).
2. Exclude `password` from `toString()` using `@ToString.Exclude`.
3. Mark `password` as `@JsonProperty(access = WRITE_ONLY)` so it's never serialized in responses.

### 4.6 No Authorization Beyond Authentication

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Location** | All services |

**Finding**: The API Gateway authenticates users via JWT, but no role-based or resource-based authorization is implemented. Any authenticated user can:
- View/modify any other user's profile
- Initiate fund transfers from any account
- Approve/disable any user
- Access all admin endpoints

**Impact**: Horizontal privilege escalation. Users can access and modify resources belonging to other users.

**Recommendation**: Implement `@PreAuthorize` or custom authorization filters. Verify that the authenticated user (from `X-Auth-Id`) owns the resources they're operating on.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Location** | Most controllers in core-banking, fund-transfer, utility-payment |

**Finding**: Many controller methods use raw `ResponseEntity` instead of `ResponseEntity<SpecificType>`:
```java
public ResponseEntity getBankAccount(...) // missing type parameter
```

**Impact**: No compile-time type safety. OpenAPI/Swagger cannot infer response schema. Clients don't know the response structure from the API definition alone.

**Recommendation**: Parameterize all `ResponseEntity` returns (e.g., `ResponseEntity<BankAccount>`).

### 5.2 No API Versioning Strategy

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |
| **Location** | All controllers |

**Finding**: All endpoints use `/api/v1/` prefix, but there is no mechanism for running multiple versions simultaneously or deprecating endpoints. No versioning via headers or media types.

**Impact**: Low immediate risk since this is v1, but breaking changes would require a coordinated migration.

**Recommendation**: Document the versioning strategy. Consider header-based versioning (`Accept-Version`) for future flexibility.

### 5.3 No Pagination Metadata in Responses

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Location** | All paginated endpoints |

**Finding**: Paginated endpoints accept `Pageable` parameters but return raw `List<>` instead of `Page<>` with metadata:
```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable)); // returns List, not Page
}
```

**Impact**: Clients don't know total count, total pages, or whether more results exist.

**Recommendation**: Return `Page<T>` or a custom wrapper containing `{ content, totalElements, totalPages, page, size }`.

### 5.4 No OpenAPI/Swagger UI for All Services

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Location** | All services |

**Finding**: While `springdoc-openapi-starter-webflux-ui` is included as a dependency in business services, the dependency is for WebFlux but most services use WebMVC. Basic `@Operation` and `@Tag` annotations have been added but may not render correctly. No aggregated Swagger UI at the gateway level.

**Impact**: Developers cannot explore the full API surface from a single location. The wrong Springdoc starter could cause runtime issues.

**Recommendation**: Switch to `springdoc-openapi-starter-webmvc-ui` for MVC services. Configure the gateway to aggregate all downstream OpenAPI specs.

### 5.5 Inconsistent Endpoint Naming

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | Core banking controllers |

**Finding**: Mixed naming conventions:
- `/api/v1/account/bank-account/{account_number}` uses `snake_case` for path variable
- `/api/v1/account/util-account/{account_name}` uses abbreviated "util"
- `/api/v1/bank-users/register` vs. `/api/v1/user/{identification}` (different pluralization)

**Recommendation**: Standardize on `kebab-case` for paths, `camelCase` for path variables. Use consistent resource naming.

---

## 6. Observability

### 6.1 Logging Lacks Structure and Consistency

| Attribute | Detail |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Location** | All services |

**Finding**:
- Log messages are informal and inconsistent: `"Reading utitlity account by ID {}"` (typo), `"Got fund transfer request from API {}"`.
- No structured logging (JSON format) for log aggregation tools.
- Sensitive data logged: `request.toString()` logs full DTOs including potential PII.
- No correlation IDs in log messages (Micrometer tracing provides trace IDs but they're not manually included).
- Mixed log message concatenation: `log.info("Sending fund transfer request {}" + request.toString())` — uses string concatenation instead of SLF4J placeholders, defeating lazy evaluation.

**Recommendation**: Adopt structured JSON logging (e.g., Logback JSON encoder). Define logging standards (what to log at each level). Never log raw request objects. Use SLF4J placeholder syntax consistently.

### 6.2 No Health Check Customization

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | All services |

**Finding**: `spring-boot-starter-actuator` is included, which provides default `/actuator/health`. However:
- No custom health indicators for downstream dependencies (MySQL connectivity, Feign client availability, Keycloak reachability).
- Health endpoints are permitted through the gateway without configuration of what details to expose.
- No readiness/liveness probe differentiation for Kubernetes.

**Recommendation**: Add custom `HealthIndicator` beans for critical dependencies. Configure `management.endpoint.health.show-details` and group health checks into `liveness` and `readiness` groups.

### 6.3 No Metrics Endpoints Beyond Defaults

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | All services |

**Finding**: Actuator provides default JVM/HTTP metrics, but no custom business metrics are defined (e.g., fund transfers per minute, payment success rate, registration count).

**Recommendation**: Add Micrometer counters/timers for key business operations. Expose Prometheus-compatible metrics endpoint.

### 6.4 Distributed Tracing Not Verified End-to-End

| Attribute | Detail |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Location** | All services |

**Finding**: Zipkin dependencies are included and Zipkin runs in Docker. However, tracing configuration (sampling rate, propagation) comes from the remote Config Server and is not visible in the repo. No verification that traces propagate correctly through the gateway and Feign clients.

**Recommendation**: Add tracing configuration to local bootstrap/application YAMLs as defaults. Verify trace propagation in integration tests.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Location** | All Feign clients |

**Finding**: No circuit breaker library (Resilience4j, Hystrix) is included in any service. All Feign calls are fire-and-forget: if core-banking-service goes down, fund-transfer and utility-payment services will block on failing HTTP calls until they timeout (default: no timeout configured).

**Impact**: A single downstream service failure cascades to all upstream services, potentially causing full system outage.

**Recommendation**: Add `spring-cloud-starter-circuitbreaker-resilience4j`. Configure `@CircuitBreaker` on Feign clients with fallback methods that return meaningful error responses.

### 7.2 No Retry Policies

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Location** | All Feign clients |

**Finding**: No retry configuration for any Feign client. Transient failures (network blips, momentary unavailability) are not retried.

**Recommendation**: Configure `spring-cloud-starter-circuitbreaker-resilience4j` with `@Retry` annotations. Set `maxAttempts`, `waitDuration`, and `retryExceptions` for idempotent operations only.

### 7.3 No Timeout Configuration

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Location** | All Feign clients, all services |

**Finding**: No explicit connection or read timeouts are configured for:
- Feign clients (default: infinite/very long)
- Database connection pools
- Keycloak Admin Client

**Impact**: A slow downstream service can hold threads indefinitely, exhausting the thread pool.

**Recommendation**: Configure Feign timeouts (`feign.client.config.default.connectTimeout`, `readTimeout`). Configure datasource pool settings (`spring.datasource.hikari.connectionTimeout`).

### 7.4 No Fallback Behavior

| Attribute | Detail |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Location** | All Feign clients |

**Finding**: No fallback methods are defined for any Feign client. When core-banking-service is unavailable, the error propagates directly to the end user as a raw exception.

**Recommendation**: Implement Feign fallback factories that return graceful error responses (e.g., "Service temporarily unavailable, please retry").

### 7.5 Non-Atomic Fund Transfer (No Saga / Compensation)

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |
| **Location** | `FundTransferService`, `UtilityPaymentService` |

**Finding**: The fund transfer flow is:
1. Save entity with `PENDING` status (local DB)
2. Call core-banking-service to execute transfer (remote)
3. Update entity to `SUCCESS` (local DB)

If step 3 fails (e.g., app crashes after step 2), the transfer is executed in core-banking but the local record stays `PENDING` forever. There is no compensation logic, no saga orchestration, no idempotency key, and no reconciliation mechanism.

**Impact**: Money is moved but the record doesn't reflect it. Potential for duplicate transfers on retry since there's no idempotency check.

**Recommendation**: Implement the Saga pattern (choreography or orchestration). Add idempotency keys to prevent duplicate transfers. Add a scheduled reconciliation job to detect and fix inconsistent states.

### 7.6 Balance Calculation Bug

| Attribute | Detail |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Location** | `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()` |

**Finding**: Available balance is calculated incorrectly:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// Available = Actual - amount - amount (double subtraction!)
```
After the first line, `actualBalance` is already reduced. The second line subtracts again from the already-reduced actual balance. This means `availableBalance = originalActual - 2 * amount`.

Similarly for the credit side: `availableBalance = originalActual + 2 * amount`.

**Impact**: Account balances become progressively incorrect with every transaction. This is a data corruption bug in core financial logic.

**Recommendation**: Fix to: `setAvailableBalance(fromBankAccountEntity.getActualBalance())` (set available = actual after update), or calculate both from the original balance before mutation.

---

## Summary Matrix

| # | Gap | Category | Severity | Effort | Service(s) |
|---|---|---|---|---|---|
| 1.1 | No shared library / common module | Code Organization | Medium | Medium | All |
| 1.2 | No root-level multi-project Gradle build | Code Organization | Low | Small | Root |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small | Multiple |
| 1.4 | Mapper instantiation via `new` | Code Organization | Low | Small | All |
| 2.1 | Generic catch-all returns 400 for all exceptions | Error Handling | Critical | Medium | All business services |
| 2.2 | Missing HTTP status code differentiation | Error Handling | High | Small | All business services |
| 2.3 | No input validation | Error Handling | Critical | Medium | All controllers |
| 2.4 | No error handling for Feign client failures | Error Handling | High | Medium | Fund-transfer, Utility-payment |
| 3.1 | Near-zero test coverage outside core banking | Testing | Critical | Large | 5 of 6 services |
| 3.2 | No contract tests between services | Testing | Medium | Large | All inter-service |
| 3.3 | Context-load tests require external dependencies | Testing | Medium | Small | All non-core services |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small | Docker/SQL files |
| 4.2 | CSRF disabled without explanation | Security | Medium | Small | API Gateway |
| 4.3 | No rate limiting or request size limits | Security | High | Medium | API Gateway |
| 4.4 | Keycloak singleton not thread-safe | Security | High | Small | User Service |
| 4.5 | Password stored in DTO without masking | Security | High | Small | User Service |
| 4.6 | No authorization beyond authentication | Security | High | Medium | All services |
| 5.1 | Raw `ResponseEntity` without type parameters | API Design | Medium | Small | Multiple controllers |
| 5.2 | No API versioning strategy | API Design | Low | Medium | All |
| 5.3 | No pagination metadata in responses | API Design | Medium | Small | All paginated endpoints |
| 5.4 | Wrong OpenAPI starter / no aggregated Swagger | API Design | Medium | Small | All services |
| 5.5 | Inconsistent endpoint naming | API Design | Low | Small | Core Banking |
| 6.1 | Logging lacks structure and consistency | Observability | Medium | Medium | All |
| 6.2 | No health check customization | Observability | Low | Small | All |
| 6.3 | No custom business metrics | Observability | Low | Small | All |
| 6.4 | Distributed tracing not verified | Observability | Low | Small | All |
| 7.1 | No circuit breakers | Resilience | Critical | Medium | All Feign clients |
| 7.2 | No retry policies | Resilience | High | Small | All Feign clients |
| 7.3 | No timeout configuration | Resilience | High | Small | All |
| 7.4 | No fallback behavior | Resilience | High | Medium | All Feign clients |
| 7.5 | Non-atomic fund transfer (no saga) | Resilience | Critical | Large | Fund-transfer, Utility-payment |
| 7.6 | Balance calculation bug (double subtraction) | Resilience | Critical | Small | Core Banking |

### Severity Distribution

| Severity | Count |
|---|---|
| **Critical** | 7 |
| **High** | 9 |
| **Medium** | 10 |
| **Low** | 6 |
| **Total** | 32 |
