# Engineering Standards Gap Analysis

## Summary

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 2 | 2 | 1 | 5 |
| Error Handling | 1 | 2 | 1 | 0 | 4 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 2 | 2 | 1 | 0 | 5 |
| API Design | 0 | 1 | 3 | 1 | 5 |
| Observability | 0 | 1 | 2 | 1 | 4 |
| Resilience | 1 | 2 | 1 | 0 | 4 |
| **Total** | **5** | **12** | **11** | **3** | **31** |

---

## 1. Code Organization

### GAP-CO-1: No Shared Library Module
**Severity: High | Effort: Medium**

Exception classes (`SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ErrorResponse`, `GlobalErrorCode`, `EntityNotFoundException`), `AuditAware`, and DTO classes are copy-pasted across 4 services with minor inconsistencies. For example, `ErrorResponse` uses `@Builder` in some services but a constructor in others.

**Current**: Each service has its own copy of `com.javatodev.finance.exception.*` and `com.javatodev.finance.model.dto.AuditAware`.
**Expected**: A shared `banking-common` library published as a Gradle module.

### GAP-CO-2: No Multi-Project Gradle Build
**Severity: High | Effort: Medium**

Each service is a standalone Gradle project with duplicated build configuration (Spring Boot version, Spring Cloud version, common dependencies). Version drift is likely.

**Current**: 7 independent `build.gradle` files with duplicated `springCloudVersion`, plugin versions.
**Expected**: Root `settings.gradle` + `build.gradle` with shared dependency management via `subprojects {}` block.

### GAP-CO-3: Package Structure Inconsistency
**Severity: Medium | Effort: Small**

All services use `com.javatodev.finance` as base package regardless of service. This makes it unclear which service a class belongs to when viewing stack traces or logs.

**Current**: `com.javatodev.finance` for all services.
**Expected**: `com.javatodev.finance.core`, `com.javatodev.finance.user`, `com.javatodev.finance.transfer`, `com.javatodev.finance.payment`.

### GAP-CO-4: Missing Dependency Injection Best Practices
**Severity: Medium | Effort: Small**

Some services use `@Autowired` field injection instead of constructor injection. Constructor injection is preferred for testability and immutability.

**Current**: Mixed `@Autowired` field injection (e.g., `UserService`, `FundTransferService`).
**Expected**: Constructor injection with `@RequiredArgsConstructor` (Lombok) for all services.

### GAP-CO-5: Incorrect OpenAPI Dependency
**Severity: Low | Effort: Small**

All services include `springdoc-openapi-starter-webflux-ui:2.1.0` but are Spring MVC (not WebFlux) applications. The WebFlux variant may not render Swagger UI correctly.

**Current**: `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0`.
**Expected**: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`.

---

## 2. Error Handling

### GAP-EH-1: HTTP 200 for All Error Responses
**Severity: Critical | Effort: Small**

`GlobalExceptionHandler` in all services returns `ResponseEntity.ok(ErrorResponse)` for every exception, including `EntityNotFoundException` and `InsufficientFundsException`. Clients cannot distinguish success from failure without parsing the body.

**Current**: All error responses return HTTP 200.
**Expected**: 404 for `EntityNotFoundException`, 422 for business validation errors, 500 for unexpected exceptions.

### GAP-EH-2: Exception Details Leaked in Responses
**Severity: High | Effort: Small**

The generic `Exception` handler returns the raw exception message to the client, potentially exposing internal details (stack traces, SQL errors, class names).

**Current**: `ErrorResponse(GlobalErrorCode.ERROR, ex.getMessage())`.
**Expected**: Generic message for 500 errors; detailed messages only for business exceptions.

### GAP-EH-3: No Feign Error Decoder
**Severity: High | Effort: Medium**

Feign clients have no custom `ErrorDecoder`. When core-banking-service returns an error, the calling service receives a raw `FeignException` instead of a meaningful business exception. This results in HTTP 500 with a Feign stack trace.

**Current**: Default `ErrorDecoder` (throws `FeignException`).
**Expected**: Custom `ErrorDecoder` that maps HTTP status codes to domain exceptions.

### GAP-EH-4: No Validation on Request Bodies
**Severity: Medium | Effort: Medium**

No `@Valid` annotations on `@RequestBody` parameters. Null or empty fields are passed through to service layer, causing `NullPointerException` or database constraint violations instead of meaningful validation errors.

**Current**: No Bean Validation annotations on DTOs; no `@Valid` on controller parameters.
**Expected**: `@NotNull`, `@NotBlank`, `@Positive` on DTO fields; `@Valid` on all `@RequestBody` parameters; `MethodArgumentNotValidException` handler returning HTTP 400.

---

## 3. Testing

### GAP-T-1: No Unit Tests for Business Services
**Severity: Critical | Effort: Large**

Three of four business services have zero test classes: `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`. Only `core-banking-service` has a single test class (`TransactionServiceTest`) with 2 test methods.

**Current**: 2 test methods total across entire codebase.
**Expected**: Minimum 80% line coverage for service classes.

### GAP-T-2: No Integration Tests
**Severity: High | Effort: Large**

No `@SpringBootTest` or `@WebMvcTest` tests exist. API contract compliance is untested.

**Current**: Zero integration tests.
**Expected**: MockMvc-based integration tests for each controller endpoint.

### GAP-T-3: No Contract Tests for Feign Clients
**Severity: High | Effort: Large**

Feign interfaces are not tested against actual or mock provider responses. Breaking changes in core-banking-service API would not be caught until runtime.

**Current**: No consumer-driven contract tests.
**Expected**: Spring Cloud Contract or Pact tests for all Feign client interfaces.

### GAP-T-4: No Test Coverage Reporting
**Severity: Medium | Effort: Small**

No JaCoCo or equivalent coverage tool configured. Coverage metrics are unknown.

**Current**: No coverage plugin in any `build.gradle`.
**Expected**: JaCoCo configured with minimum coverage thresholds enforced in CI.

---

## 4. Security

### GAP-S-1: No Input Validation
**Severity: Critical | Effort: Medium**

Request DTOs have no validation annotations. Malicious or malformed input (negative amounts, SQL injection in string fields, oversized payloads) is processed without checks.

**Current**: No `spring-boot-starter-validation` dependency; no `@Valid` usage.
**Expected**: Bean Validation on all DTOs; `@Valid` on controllers; validation error handler.

### GAP-S-2: Hardcoded Secrets in Docker Compose
**Severity: Critical | Effort: Small**

`docker-compose.yml` contains hardcoded passwords: MySQL root password, Keycloak admin password, Keycloak DB credentials. These are committed to version control.

**Current**: Plaintext passwords in `docker-compose.yml` and `README.md`.
**Expected**: Environment variable references with `.env` file (gitignored); `.env.example` with placeholders.

### GAP-S-3: Business Services Not Secured
**Severity: High | Effort: Medium**

Only the API Gateway has OAuth2 security configuration. Business services (ports 8083-8085, 8092) accept unauthenticated requests directly. In production, network segmentation might mitigate this, but defense-in-depth requires service-level security.

**Current**: No Spring Security dependency or configuration in business services.
**Expected**: OAuth2 resource server configuration in each business service; JWT validation against Keycloak.

### GAP-S-4: No CORS Configuration
**Severity: High | Effort: Small**

No CORS configuration in any service. Browser-based clients would be blocked by same-origin policy.

**Current**: No `WebMvcConfigurer` or `@CrossOrigin` annotations.
**Expected**: Configurable CORS policy per service via application properties.

### GAP-S-5: No Rate Limiting
**Severity: Medium | Effort: Medium**

No rate limiting on any endpoint. Financial transaction endpoints (fund transfer, utility payment) are vulnerable to abuse.

**Current**: No rate limiting configuration.
**Expected**: Spring Cloud Gateway rate limiter or per-service rate limiting with Bucket4j or similar.

---

## 5. API Design

### GAP-AD-1: Raw ResponseEntity Without Type Parameters
**Severity: High | Effort: Small**

Controllers return raw `ResponseEntity` without generic type parameters, losing compile-time type safety and making API documentation inaccurate.

**Current**: `public ResponseEntity readUser(...)`.
**Expected**: `public ResponseEntity<User> readUser(...)`.

### GAP-AD-2: Inconsistent Pagination
**Severity: Medium | Effort: Small**

Some endpoints accept `Pageable` but return `List<T>` instead of `Page<T>`, discarding pagination metadata (totalElements, totalPages).

**Current**: `return ResponseEntity.ok(userService.readUsers(pageable))` returns `List<User>`.
**Expected**: Return `Page<User>` with pagination metadata.

### GAP-AD-3: No API Versioning Strategy
**Severity: Medium | Effort: Medium**

URL path versioning (`/api/v1/`) is used but there's no documentation or mechanism for version migration.

**Current**: All endpoints are `/api/v1/` with no strategy for v2.
**Expected**: Documented versioning strategy; header-based or URL-based with deprecation policy.

### GAP-AD-4: Inconsistent Endpoint Naming
**Severity: Medium | Effort: Small**

Mixed naming conventions across services: `bank-users`, `transfer`, `utility-payment`, `bank-account`, `util-account`. Some use verbs in URLs (`/register`, `/update`).

**Current**: `/api/v1/bank-users/register`, `/api/v1/bank-users/update/{id}`.
**Expected**: RESTful noun-based URLs: `POST /api/v1/bank-users`, `PATCH /api/v1/bank-users/{id}`.

### GAP-AD-5: Missing HATEOAS
**Severity: Low | Effort: Large**

No hypermedia links in responses. Clients must hardcode URLs for related resources.

**Current**: Plain JSON responses without links.
**Expected**: Spring HATEOAS with resource links (optional — depends on project goals).

---

## 6. Observability

### GAP-O-1: No Structured Logging
**Severity: High | Effort: Medium**

Services use default Spring Boot logging (console, unstructured). No JSON log format, no correlation IDs in logs, no log aggregation configuration.

**Current**: Default `logback-spring.xml` (not present — using Spring Boot defaults).
**Expected**: JSON-formatted logs with trace ID, span ID, service name; centralized log aggregation.

### GAP-O-2: Minimal Actuator Configuration
**Severity: Medium | Effort: Small**

Actuator endpoints are included but only default endpoints are exposed. No custom health indicators, no metrics endpoints exposed, no Prometheus integration.

**Current**: Default actuator configuration.
**Expected**: Expose health, info, metrics, prometheus endpoints; custom health indicators for database and Feign client connectivity.

### GAP-O-3: No Custom Metrics
**Severity: Medium | Effort: Medium**

No application-level metrics (transaction counts, transfer amounts, error rates, latency histograms).

**Current**: Only default Spring Boot metrics.
**Expected**: Micrometer `@Timed` annotations on service methods; custom counters for business events.

### GAP-O-4: Zipkin Not Configured in Application Properties
**Severity: Low | Effort: Small**

Zipkin dependencies are included but configuration relies on centralized config server. If config server is unavailable, tracing silently fails with no fallback.

**Current**: Zipkin URL configured only in external config repo.
**Expected**: Fallback Zipkin configuration in local `application.yml` with sensible defaults.

---

## 7. Resilience

### GAP-R-1: No Circuit Breakers
**Severity: Critical | Effort: Medium**

Feign clients have no circuit breaker configuration. If core-banking-service goes down, all dependent services will hang on HTTP calls until socket timeout, causing cascading failures.

**Current**: No Resilience4j or Hystrix dependency; no fallback methods.
**Expected**: Resilience4j circuit breaker on all Feign clients with fallback responses.

### GAP-R-2: No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeout configuration for Feign clients. Default socket timeouts (potentially infinite) apply.

**Current**: No `feign.client.config.default.connectTimeout` or `readTimeout`.
**Expected**: Connect timeout: 5s, Read timeout: 10s, with circuit breaker integration.

### GAP-R-3: No Retry Mechanism
**Severity: High | Effort: Medium**

No retry configuration for transient failures (network glitches, temporary unavailability).

**Current**: No `Retryer` configuration in Feign or Spring Retry.
**Expected**: Retry with exponential backoff for idempotent operations (GET requests); no retry for non-idempotent operations (POST fund transfers).

### GAP-R-4: No Graceful Degradation
**Severity: Medium | Effort: Medium**

When a dependency is unavailable, services return raw error responses instead of degraded functionality.

**Current**: `FeignException` propagated to client.
**Expected**: Fallback responses with clear "service unavailable" messages; cached responses where appropriate.
