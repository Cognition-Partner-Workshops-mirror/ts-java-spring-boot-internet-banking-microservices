# Engineering Standards Gap Analysis

## Methodology

This analysis compares the current codebase against industry-standard engineering best practices for Java/Spring Boot microservices. Each gap is rated by:

- **Severity:** Critical / High / Medium / Low
- **Effort:** Small (< 1 day) / Medium (1–3 days) / Large (> 3 days)

---

## 1. Code Organization

### GAP-ORG-001: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Medium

Each service has its own independent `build.gradle` with duplicated dependency declarations, plugin versions, and repository configurations. There is no parent `settings.gradle` or shared conventions plugin.

**Evidence:** All 6 `build.gradle` files repeat `version '3.2.4'`, `springCloudVersion "2023.0.0"`, and identical dependency blocks.

**Impact:** Version drift risk, tedious upgrades, inconsistent dependency versions across services.

---

### GAP-ORG-002: Duplicated Code Across Services
**Severity:** High | **Effort:** Medium

The following classes are copy-pasted across 3+ services with minor variations:
- `AuditAware` (base entity with audit timestamps)
- `BaseMapper` interface
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder`
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`
- `CustomFeignClientConfiguration`

**Evidence:** Identical `AppAuthUserFilter.java` in fund-transfer, user, and utility-payment services. Identical `BaseMapper<E, D>` interface in all business services.

**Impact:** Bug fixes must be applied in multiple places; inconsistencies creep in over time.

---

### GAP-ORG-003: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

- Core banking uses `repository/` at package root; other services use `model/repository/`
- Fund transfer uses `service/rest/client/`; user service uses `service/rest/`; utility payment uses `service/rest/`
- User service has `configuration/feign/` and `configuration/keycloak/`; others put Feign config at `configuration/` root

**Impact:** Developer confusion when navigating unfamiliar services.

---

### GAP-ORG-004: Missing Shared Library / BOM
**Severity:** Medium | **Effort:** Large

No shared library exists for common DTOs, exception classes, or utility code. Each service re-defines its own version of `FundTransferRequest`, `AccountResponse`, etc.

**Impact:** Contract drift between services; changes to shared contracts require coordinated updates.

---

## 2. Error Handling

### GAP-ERR-001: Generic Exception Handler Returns 400 for All Errors
**Severity:** Critical | **Effort:** Small

All services catch `Exception.class` and return HTTP 400 with a raw string body (`"Exception occur inside API " + e`). This:
- Leaks stack traces and internal details to clients
- Returns incorrect HTTP status codes (a 500-class error returns 400)
- Produces inconsistent response format (string vs. structured `ErrorResponse`)

**Evidence:** All 4 `GlobalExceptionHandler.java` files have identical pattern.

---

### GAP-ERR-002: No HTTP Status Code Differentiation
**Severity:** High | **Effort:** Small

All custom exceptions map to `400 Bad Request` regardless of the actual error:
- `EntityNotFoundException` → should be 404
- `InsufficientFundsException` → 422 is more appropriate
- `UserAlreadyRegisteredException` → 409 Conflict

**Evidence:** `GlobalExceptionHandler.handleGlobalException()` always calls `ResponseEntity.badRequest()`.

---

### GAP-ERR-003: No Feign Error Propagation Strategy
**Severity:** High | **Effort:** Medium

Only the user service has a `CustomFeignErrorDecoder`. Fund-transfer and utility-payment services use `CustomFeignClientConfiguration` but it only sets an `OkHttpClient` — there is no error decoder, so Feign errors surface as opaque `FeignException` with raw HTTP bodies.

**Impact:** Downstream errors cannot be meaningfully communicated to callers.

---

### GAP-ERR-004: No Validation on Request Bodies
**Severity:** Critical | **Effort:** Small

No `@Valid` / `@NotNull` / `@Min` annotations on any controller method parameters. A null `amount` or empty `fromAccount` will produce a NullPointerException deep in service logic rather than a clean 400 response.

**Evidence:** All `@RequestBody` parameters lack `@Valid`; no `jakarta.validation` constraints on any DTO.

---

## 3. Testing

### GAP-TEST-001: Minimal Test Coverage
**Severity:** Critical | **Effort:** Large

| Service | Unit Tests | Integration Tests | Coverage |
|---------|-----------|-------------------|----------|
| core-banking-service | 3 test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) | 0 | ~30% of service layer |
| fund-transfer-service | 1 empty context-load test | 0 | ~0% |
| user-service | 1 empty context-load test | 0 | ~0% |
| utility-payment-service | 1 empty context-load test | 0 | ~0% |
| api-gateway | 1 empty context-load test | 0 | ~0% |
| config-server | 1 empty context-load test | 0 | N/A |

**Impact:** No regression safety net; bugs can ship without detection.

---

### GAP-TEST-002: No Integration Tests
**Severity:** High | **Effort:** Large

No `@SpringBootTest` with test containers, no Feign client contract tests, no API integration tests. The H2 test config exists but is only used for the empty context-load test.

---

### GAP-TEST-003: No Contract Tests Between Services
**Severity:** High | **Effort:** Large

Services communicate via Feign but there are no Pact or Spring Cloud Contract tests to verify API compatibility. Breaking changes in core-banking-service would not be detected until runtime.

---

### GAP-TEST-004: No Test Coverage Reporting
**Severity:** Medium | **Effort:** Small

No JaCoCo or similar coverage plugin configured. Cannot measure or enforce coverage thresholds.

---

## 4. Security

### GAP-SEC-001: Hardcoded Credentials in Source
**Severity:** Critical | **Effort:** Small

- MySQL root password in `docker-compose.yml`: `woVERANKliGharym`
- MySQL user password in `privileges.sql`: `oPItyPticIAt`
- Keycloak admin password: `password`
- Keycloak DB password: `password`
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

**Impact:** Secrets exposed in version control; any fork inherits production-like credentials.

---

### GAP-SEC-002: No Input Validation
**Severity:** Critical | **Effort:** Small

(Same as GAP-ERR-004) No Bean Validation annotations anywhere. SQL injection via JPA is mitigated by parameterized queries, but application-level validation is absent.

---

### GAP-SEC-003: CSRF Disabled Without Justification
**Severity:** Medium | **Effort:** Small

`ServerHttpSecurity.CsrfSpec::disable` in the gateway security config. For a REST API with JWT-only auth this is acceptable, but there's no documentation of this decision.

---

### GAP-SEC-004: Keycloak Client as Static Singleton
**Severity:** High | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-thread-safe lazy singleton (`if (keycloakInstance == null)`). Under concurrent requests, multiple Keycloak clients may be instantiated, and the cached instance never refreshes expired tokens.

**Evidence:** `KeycloakProperties.java:29` — classic double-check locking bug (no `synchronized`).

---

### GAP-SEC-005: No Dependency Vulnerability Scanning
**Severity:** Medium | **Effort:** Small

No OWASP Dependency-Check, Snyk, or Dependabot configuration. Dependencies are pinned to specific versions but never audited for CVEs.

---

### GAP-SEC-006: Internal Services Lack Authentication
**Severity:** High | **Effort:** Medium

Core-banking-service endpoints (`/api/v1/account/*`, `/api/v1/transaction/*`, `/api/v1/user/*`) have no security at all. Only the API Gateway enforces JWT auth. If any internal service port is exposed, all banking operations are unprotected.

**Impact:** Network-level access to port 8092 bypasses all security.

---

## 5. API Design

### GAP-API-001: Raw `ResponseEntity` Without Type Parameters
**Severity:** Medium | **Effort:** Small

Most controllers return `ResponseEntity` (raw type) instead of `ResponseEntity<FundTransferResponse>`. This:
- Breaks compile-time type safety
- Produces incomplete OpenAPI schemas
- Generates Swagger docs without response models

**Evidence:** All controllers in core-banking and fund-transfer services use unparameterized `ResponseEntity`.

---

### GAP-API-002: No API Versioning Strategy
**Severity:** Low | **Effort:** Medium

All endpoints use `/api/v1/` but there's no mechanism for version evolution (no content negotiation, no header-based versioning, no version-specific packages).

---

### GAP-API-003: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Small

Paginated endpoints return `List<T>` instead of a wrapper with `totalElements`, `totalPages`, `currentPage`. Clients cannot determine if more pages exist.

**Evidence:** `FundTransferService.readAllTransfers()` calls `.getContent()` and returns raw list.

---

### GAP-API-004: Inconsistent Endpoint Naming
**Severity:** Low | **Effort:** Small

- Core banking: `/api/v1/account/bank-account/{account_number}` (snake_case path variable)
- User service: `/api/v1/bank-users/update/{id}` (verb in URL — not RESTful)
- Mixed use of path variable naming: `{account_number}` vs `{id}` vs `{identification}`

---

### GAP-API-005: OpenAPI Documentation Incomplete
**Severity:** Medium | **Effort:** Small

Swagger/springdoc-openapi is added as a dependency and `@Operation` + `@Tag` annotations exist, but:
- `springdoc-openapi-starter-webflux-ui` is wrong for non-reactive services (should be `webmvc-ui`)
- No response schema annotations (`@ApiResponse`)
- No request body schema documentation
- No global security scheme documented

---

## 6. Observability

### GAP-OBS-001: Logging Inconsistency
**Severity:** Medium | **Effort:** Small

- Sensitive data logged: `request.toString()` in controllers logs full request bodies including account numbers
- No structured logging (JSON format) configured
- No correlation ID logging (trace ID from Brave is available but not in log pattern)
- Inconsistent log levels: some services log at INFO for every request

---

### GAP-OBS-002: No Custom Health Indicators
**Severity:** Low | **Effort:** Small

Services rely on default Spring Boot health endpoint. No custom health checks for:
- Database connectivity with timeout
- Keycloak reachability
- Eureka registration status
- Downstream service availability

---

### GAP-OBS-003: No Metrics Endpoints
**Severity:** Medium | **Effort:** Small

`spring-boot-starter-actuator` is included but no Prometheus endpoint is configured (no `micrometer-registry-prometheus` dependency despite Prometheus being listed in the tech stack). Business metrics (transfer count, payment volume) are not tracked.

---

### GAP-OBS-004: Incomplete Distributed Tracing Coverage
**Severity:** Low | **Effort:** Small

Brave + Zipkin reporter configured across all services, but:
- No custom span annotations on business-critical operations
- No sampling rate configuration (defaults to 10%)
- Database queries not instrumented with span tags

---

## 7. Resilience

### GAP-RES-001: No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

Feign clients call core-banking-service synchronously with no circuit breaker (no Resilience4j, no Hystrix). If core-banking-service is down, all dependent services will hang indefinitely, exhausting thread pools.

**Impact:** Cascading failure across entire system.

---

### GAP-RES-002: No Timeout Configuration
**Severity:** High | **Effort:** Small

No Feign timeout configuration (connect/read timeouts). Default is infinite wait. No Spring Cloud Gateway timeout for downstream routes.

**Evidence:** No `feign.client.config` in any application.yml; no `CustomFeignClientConfiguration` timeout settings.

---

### GAP-RES-003: No Retry Policies
**Severity:** High | **Effort:** Small

No Spring Retry or Resilience4j retry configuration. Transient network errors between services result in immediate failure.

---

### GAP-RES-004: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

No `@FeignClient(fallback=...)` or `@CircuitBreaker(fallbackMethod=...)` defined. Failed calls always propagate as exceptions with no graceful degradation.

---

### GAP-RES-005: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

API Gateway has no rate limiting configuration. A single client can overwhelm the system with requests.

---

### GAP-RES-006: Non-Atomic Fund Transfer
**Severity:** Critical | **Effort:** Large

`TransactionService.internalFundTransfer()` performs two separate `bankAccountRepository.save()` calls. If the process crashes between the debit and credit, funds are lost. The `@Transactional` annotation uses `jakarta.transaction.Transactional` which should provide atomicity, but the orchestrating fund-transfer-service has no saga/compensation pattern — if core-banking succeeds but the local DB update fails, the transfer appears completed but is not recorded.

---

## Summary Table

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 1 | 2 | 1 |
| Error Handling | 2 | 2 | 0 | 0 |
| Testing | 1 | 2 | 1 | 0 |
| Security | 2 | 2 | 2 | 0 |
| API Design | 0 | 0 | 3 | 2 |
| Observability | 0 | 0 | 3 | 1 |
| Resilience | 2 | 2 | 2 | 0 |
| **Total** | **7** | **9** | **13** | **4** |
