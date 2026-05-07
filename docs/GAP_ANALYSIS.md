# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven dimensions. Each gap is rated by **severity** (Critical / High / Medium / Low) and **remediation effort** (Small / Medium / Large).

---

## Summary Dashboard

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

### GAP-ORG-01: No Multi-Module Gradle Build
**Severity: High | Effort: Medium**

Each of the 6 services is an independent Gradle project with its own `gradlew`, `settings.gradle`, and `gradle/wrapper/`. There is no root-level `settings.gradle` or `build.gradle` to unify the build. This means:
- Dependency versions must be kept in sync manually across 6 `build.gradle` files.
- There is no single command to build or test all services.
- Plugin and Spring Boot version upgrades require editing 6 files.

**Current state:** 6 independent Gradle wrapper installations, duplicated version strings.
**Expected state:** A root multi-module Gradle build with shared dependency version catalogs and a single `./gradlew build` command.

---

### GAP-ORG-02: Massive Code Duplication Across Services
**Severity: High | Effort: Large**

The following classes are copy-pasted identically (or near-identically) across 3–4 services:
- `BaseMapper<E, D>` — identical in core-banking, user, fund-transfer, utility-payment (4 copies)
- `AuditAware` — identical in user, fund-transfer, utility-payment (3 copies)
- `AuditorAwareConfig` — identical in user, fund-transfer, utility-payment (3 copies)
- `AuditConfig` — identical in user, fund-transfer, utility-payment (3 copies)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — identical in user, fund-transfer, utility-payment (3 copies)
- `ErrorResponse` — identical in all 4 business services (4 copies)
- `SimpleBankingGlobalException` — identical in all 4 business services (4 copies)
- `GlobalExceptionHandler` — near-identical in all 4 business services (4 copies)
- `TransactionStatus` enum — identical in fund-transfer and utility-payment (2 copies)
- `CustomFeignClientConfiguration` — exists in fund-transfer and utility-payment with different implementations

**Current state:** ~30+ duplicated files across services.
**Expected state:** A shared library module (e.g., `banking-common`) containing common models, exception handling, auditing, and filter infrastructure.

---

### GAP-ORG-03: Inconsistent Package Structure
**Severity: Medium | Effort: Small**

- Core-banking uses `com.javatodev.finance.repository` at the top level.
- User service uses `com.javatodev.finance.model.repository`.
- Fund-transfer uses `com.javatodev.finance.model.repository`.
- Utility-payment uses `com.javatodev.finance.repository` (top level).
- Feign client config location varies: `configuration/` (fund-transfer), `configuration/feign/` (user), `configuration/` (utility-payment).

**Current state:** Inconsistent package naming across services.
**Expected state:** Standardized package structure: `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.mapper`, `config`, `exception`.

---

### GAP-ORG-04: DTOs Used as JPA Entities
**Severity: Medium | Effort: Medium**

`AuditAware` is annotated with both `@MappedSuperclass` and `@EntityListeners` — it is a JPA entity base class. However, it lives in the `model.dto` package and is also extended by DTO classes like `FundTransfer` and `UtilityPayment`. This blurs the boundary between persistence and API layers.

**Current state:** Entity base class in `model.dto` package; DTOs extend JPA-annotated superclass.
**Expected state:** Clear separation — entity base classes in `model.entity`, DTOs in `model.dto` with no JPA annotations.

---

### GAP-ORG-05: No .editorconfig or Code Formatting Standard
**Severity: Low | Effort: Small**

Indentation is inconsistent across services — some files use tabs, others use spaces. There is no `.editorconfig`, Checkstyle configuration, or Spotless plugin configured.

**Current state:** Mixed indentation; no formatting enforcement.
**Expected state:** `.editorconfig` + Spotless or Checkstyle plugin in the Gradle build.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400
**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` across all four services maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). This violates HTTP semantics:
- Not-found errors should return **404**.
- Validation errors should return **422** or **400**.
- Unexpected server errors should return **500**.
- Insufficient funds could return **409** (Conflict) or a domain-specific 4xx.

The catch-all `Exception` handler also returns 400 with the raw exception string, which leaks internal details.

**Current state:** All errors → 400 Bad Request.
**Expected state:** Proper HTTP status mapping (400, 404, 409, 422, 500) with structured error responses.

---

### GAP-ERR-02: Error Response Leaks Internal Exception Details
**Severity: High | Effort: Small**

The catch-all exception handler in all services returns:
```java
"Exception occur inside API " + e
```
This exposes stack traces, class names, and internal details to the client — a security and usability problem.

**Current state:** Raw exception `.toString()` returned to clients.
**Expected state:** Generic error message for unexpected errors; structured `ErrorResponse` for all cases.

---

### GAP-ERR-03: Inconsistent Error Code Scheme
**Severity: High | Effort: Small**

- Core-banking uses `BANKING-CORE-SERVICE-1000`, `BANKING-CORE-SERVICE-1001`.
- User service uses `USER-SERVICE-1000` through `USER-SERVICE-1003`.
- Fund-transfer and utility-payment have no `GlobalErrorCode` class at all — they use raw `SimpleBankingGlobalException` with no codes.

**Current state:** Only 2 of 4 services define error codes; no shared catalog.
**Expected state:** Unified error code catalog across all services (e.g., `ERR-{SERVICE}-{CATEGORY}-{NUMBER}`).

---

### GAP-ERR-04: No Feign Error Propagation in Fund-Transfer and Utility-Payment
**Severity: Medium | Effort: Medium**

Only the user service has a `CustomFeignErrorDecoder` that extracts structured errors from downstream Feign responses. The fund-transfer and utility-payment services use the default Feign error decoder, meaning errors from core-banking-service are wrapped in generic `FeignException` and the structured error code/message is lost.

**Current state:** 1 of 3 Feign-using services has custom error decoding.
**Expected state:** All Feign clients have a shared custom error decoder that preserves upstream error context.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage on 5 of 6 Services
**Severity: Critical | Effort: Large**

| Service | Meaningful Tests | Test Files |
|---|---|---|
| core-banking-service | 3 test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) with ~15 test cases | ✅ |
| internet-banking-user-service | Only `contextLoads()` (would fail without Keycloak) | ❌ |
| internet-banking-fund-transfer-service | Only `contextLoads()` (would fail without MySQL + Eureka) | ❌ |
| internet-banking-utility-payment-service | Only `contextLoads()` (would fail without MySQL + Eureka) | ❌ |
| internet-banking-api-gateway | Only `contextLoads()` (would fail without Config Server) | ❌ |
| internet-banking-config-server | Only `contextLoads()` (would fail without Git repo access) | ❌ |

The `contextLoads()` tests in most services will fail because they use `@SpringBootTest` which attempts to bootstrap the full application context, requiring live infrastructure (Eureka, Config Server, MySQL, Keycloak).

**Current state:** Only core-banking-service has unit tests. Most `contextLoads()` tests are broken.
**Expected state:** Each service should have unit tests for service layer and controller layer. Context load tests should use test profiles with mocked dependencies.

---

### GAP-TEST-02: No Integration Tests
**Severity: High | Effort: Large**

There are no integration tests that verify:
- Feign client contracts between services.
- Database operations with actual SQL (Flyway migrations + H2 or Testcontainers).
- End-to-end request flow through the API Gateway.

**Current state:** Zero integration tests.
**Expected state:** Spring Boot integration tests with `@SpringBootTest` and `WebTestClient`/`MockMvc`; Testcontainers for database tests; contract tests for Feign clients.

---

### GAP-TEST-03: No Contract Tests Between Services
**Severity: High | Effort: Medium**

Services communicate via Feign clients with hand-written DTOs. There are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that the provider's API shape matches the consumer's expectations. If a DTO field is renamed in core-banking-service, the fund-transfer and utility-payment services would break at runtime with no build-time warning.

**Current state:** No contract or consumer-driven tests.
**Expected state:** Spring Cloud Contract or Pact tests verifying Feign client compatibility.

---

### GAP-TEST-04: Test Configuration Is Incomplete
**Severity: Medium | Effort: Small**

Test `application.yml` files exist for fund-transfer, utility-payment, and user services with H2 + Flyway disabled, but they are not used by any real tests. The core-banking-service tests do not use `@SpringBootTest` and thus don't need the test config — they use plain Mockito. No test configuration exists for the API gateway or config server.

**Current state:** Test configs exist but aren't actively used; gateway/config-server lack test configs.
**Expected state:** All services have proper test configuration; context load tests are either fixed or removed.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code and Docker Compose
**Severity: Critical | Effort: Small**

The following credentials are committed to the repository in plain text:

| Location | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin` / `password` |
| `docker-compose.yml` | Keycloak DB: `keycloak` / `password` |
| `docker-compose/mysql/privileges.sql` | App user: `javatodev_development` / `oPItyPticIAt` |
| `docker-compose/mysql/Dockerfile` | ENV `MYSQL_ROOT_PASSWORD` |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |
| `src/test/resources/application.yml` (user-service) | Keycloak client-secret: `e8548d56-d743-45ef-8655-063c9cd96759` |

**Current state:** Secrets hardcoded across multiple files.
**Expected state:** Use environment variables or Docker secrets; reference `.env` files (gitignored) for local development.

---

### GAP-SEC-02: No Input Validation on Any Endpoint
**Severity: Critical | Effort: Medium**

No controller in any service uses Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on request bodies or path variables. Examples:
- `FundTransferRequest.amount` accepts null or negative values.
- `User.email` is not validated as an email format.
- `User.password` has no length/complexity requirements.
- `UtilityPaymentRequest.providerId` could be null.

**Current state:** Zero input validation; any malformed request reaches the service layer.
**Expected state:** Jakarta Bean Validation annotations on all request DTOs; `@Valid` on controller method parameters; `MethodArgumentNotValidException` handler returning 422.

---

### GAP-SEC-03: Downstream Services Have No Authentication
**Severity: High | Effort: Medium**

Only the API Gateway enforces JWT authentication. The individual microservices (core-banking, user, fund-transfer, utility-payment) have no Spring Security configuration and accept unauthenticated requests on their direct ports. In a Docker network, any container can call any service directly, bypassing the gateway.

**Current state:** Security is perimeter-only at the gateway.
**Expected state:** Each service validates the JWT token or uses mutual TLS / service mesh. At minimum, services should reject requests that don't come through the gateway (e.g., verify `X-Auth-Id` header presence and/or validate JWT).

---

### GAP-SEC-04: Keycloak Client Uses Static Singleton (Thread-Safety Issue)
**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a naive static singleton without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This is a classic double-checked locking bug — under concurrent access, multiple Keycloak instances could be created, or a partially constructed instance could be returned.

**Current state:** Non-thread-safe lazy initialization.
**Expected state:** Use Spring-managed `@Bean` for the Keycloak instance, or use `volatile` + proper double-checked locking.

---

### GAP-SEC-05: No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

There is no OWASP Dependency-Check, Snyk, or Dependabot configuration. The project uses some third-party libraries (e.g., `commons-io` via Keycloak, `feign-okhttp`) that may have known CVEs.

**Current state:** No vulnerability scanning.
**Expected state:** OWASP Dependency-Check Gradle plugin or GitHub Dependabot alerts enabled.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Generic Type Parameters
**Severity: High | Effort: Small**

Almost all controller methods return raw `ResponseEntity` (without generic type parameter), e.g.:
```java
public ResponseEntity readPayments(Pageable pageable) { ... }
```

This means:
- OpenAPI/Swagger cannot infer response types — API documentation shows `object` instead of the actual DTO.
- Compile-time type safety is lost.

The user service `UserController` is the only controller that uses typed responses (`ResponseEntity<User>`).

**Current state:** 10 of 12 endpoints return untyped `ResponseEntity`.
**Expected state:** All controller methods use `ResponseEntity<SpecificType>`.

---

### GAP-API-02: No API Versioning Strategy
**Severity: Medium | Effort: Small**

While all endpoints use `/api/v1/`, there is no mechanism for versioning (e.g., content negotiation, header-based versioning, or path-based routing). More importantly, there is no documentation or convention for when to bump the version.

**Current state:** `/api/v1/` prefix used but no versioning strategy documented.
**Expected state:** Documented API versioning strategy; gateway routes support multiple versions.

---

### GAP-API-03: Pagination Returns Raw List Instead of Page Metadata
**Severity: Medium | Effort: Small**

All paginated endpoints (user list, fund transfers, utility payments) accept `Pageable` but return `List<T>` instead of `Page<T>`, discarding pagination metadata (total elements, total pages, current page, size):

```java
public List<FundTransfer> readAllTransfers(Pageable pageable) {
    return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
}
```

**Current state:** Pagination metadata is lost; clients cannot implement proper pagination UIs.
**Expected state:** Return `Page<T>` or a custom wrapper with `content`, `totalElements`, `totalPages`, `number`, `size`.

---

### GAP-API-04: No Filtering or Sorting Support
**Severity: Medium | Effort: Medium**

List endpoints accept `Pageable` (which supports `sort`) but have no query parameter documentation and no filtering capability (e.g., filter transfers by status, date range, or account).

**Current state:** Basic pagination only; no filtering or explicit sorting support.
**Expected state:** Query parameters for common filters; Spring Data Specifications or QueryDSL for dynamic filtering.

---

### GAP-API-05: OpenAPI/Swagger Dependency Mismatch
**Severity: Low | Effort: Small**

Services use `springdoc-openapi-starter-webflux-ui:2.1.0`, which is the WebFlux variant. However, core-banking, user, fund-transfer, and utility-payment services are all Spring MVC (servlet-based) applications — they should use `springdoc-openapi-starter-webmvc-ui` instead. The webflux variant may not correctly scan MVC controllers.

**Current state:** Wrong Springdoc OpenAPI starter used in 4 services.
**Expected state:** Use `springdoc-openapi-starter-webmvc-ui` for MVC services.

---

## 6. Observability

### GAP-OBS-01: No Structured Logging
**Severity: High | Effort: Medium**

All services use `Slf4j` with Logback defaults (plain text format). There is no structured (JSON) logging configured, making log aggregation and search difficult in production environments. Log levels are not configurable per service.

Additionally, some log statements include sensitive data or object `.toString()`:
```java
log.info("Creating user with {}", request.toString()); // may log passwords
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

**Current state:** Plain text logging; potential PII/credential exposure in logs.
**Expected state:** JSON-structured logging (Logback JSON encoder); no sensitive data in log statements; configurable log levels via Config Server.

---

### GAP-OBS-02: Health Checks Not Explicitly Configured
**Severity: Medium | Effort: Small**

All services include `spring-boot-starter-actuator`, which provides `/actuator/health` by default. However:
- Health check configuration is externalized to the Config Server (not visible in the repo).
- There is no custom health indicator for database connectivity, Keycloak availability, or Feign client health.
- Docker Compose services have no `healthcheck` directive — they rely on `wait-for-it.sh` TCP checks only.

**Current state:** Default actuator health endpoint; no custom health indicators; no Docker health checks.
**Expected state:** Custom health indicators for critical dependencies; Docker `healthcheck` directives; liveness/readiness probes for Kubernetes.

---

### GAP-OBS-03: No Metrics Endpoints or Dashboards
**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` provides Micrometer metrics, there is no Prometheus scrape configuration, no Grafana dashboards, and no custom business metrics (e.g., transfer count, payment amount, error rate).

The README mentions Prometheus in the technology stack, but no `micrometer-registry-prometheus` dependency exists in any service.

**Current state:** Built-in Micrometer metrics exist but are not exposed for Prometheus; no dashboards.
**Expected state:** Add `micrometer-registry-prometheus` dependency; configure `/actuator/prometheus` endpoint; provide Grafana dashboard templates.

---

### GAP-OBS-04: Zipkin Tracing Configuration Is External-Only
**Severity: Low | Effort: Small**

Tracing dependencies are present, but the Zipkin endpoint URL and sampling rate are configured externally via Config Server. If the Config Server is unreachable, tracing silently fails with no fallback. There is no local default configuration in any service's `application.yml`.

**Current state:** Tracing depends entirely on Config Server availability.
**Expected state:** Sensible defaults in local `application.yml` (e.g., `management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans`, `management.tracing.sampling.probability=0.1`).

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers on Feign Clients
**Severity: Critical | Effort: Medium**

Three services make synchronous Feign calls to `core-banking-service`:
- `internet-banking-user-service` → `core-banking-service`
- `internet-banking-fund-transfer-service` → `core-banking-service`
- `internet-banking-utility-payment-service` → `core-banking-service`

There are no circuit breakers configured (no Resilience4j or Spring Cloud Circuit Breaker dependency). If `core-banking-service` becomes slow or unresponsive, all upstream services will block indefinitely, leading to cascading failures and potential thread pool exhaustion.

**Current state:** No circuit breakers; no fallback behavior.
**Expected state:** Resilience4j circuit breakers on all Feign clients with defined fallback behavior and open/half-open/closed thresholds.

---

### GAP-RES-02: No Timeout Configuration on Feign Clients
**Severity: High | Effort: Small**

Feign clients use default timeouts (which are effectively infinite for connection and read). If core-banking-service hangs, calling services will wait indefinitely.

**Current state:** Default (no) timeout configuration.
**Expected state:** Explicit connection and read timeouts (e.g., 5s connect, 10s read) configured per Feign client.

---

### GAP-RES-03: No Retry Policies
**Severity: High | Effort: Small**

There is no retry configuration on Feign clients or any other HTTP calls. Transient network errors (e.g., DNS resolution blip, temporary connection reset) will immediately fail the request without retry.

**Current state:** No retry logic.
**Expected state:** Configurable retry policies with exponential backoff for idempotent operations (GET requests); no retry for non-idempotent operations (POST) unless the operation is idempotent by design.

---

### GAP-RES-04: No Transaction Compensation / Saga Pattern
**Severity: Medium | Effort: Large**

The fund transfer flow spans two services:
1. `fund-transfer-service` persists a local record with status `PENDING`.
2. Calls `core-banking-service` to execute the transfer.
3. Updates local record to `SUCCESS`.

If step 3 fails (e.g., network error after core-banking completes), the local record stays `PENDING` while the actual transfer completed — an inconsistent state. There is no compensation mechanism, saga orchestrator, or outbox pattern to handle this.

The same issue exists in the utility payment flow.

**Current state:** No distributed transaction handling; potential data inconsistency on partial failures.
**Expected state:** Implement an outbox pattern, saga pattern, or at minimum add a reconciliation mechanism to detect and resolve inconsistencies.
