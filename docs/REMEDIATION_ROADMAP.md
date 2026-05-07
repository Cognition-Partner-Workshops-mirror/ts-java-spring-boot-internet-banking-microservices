# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to kick off the remediation work.

---

## Phase 1: Quick Wins (Critical fixes & small-effort items)

> **Goal:** Eliminate security vulnerabilities, data-integrity bugs, and information-disclosure risks. All items are small effort (< 1 day each).

### 1.1 Fix Available Balance Double-Decrement Bug
**Gap:** 7.7 · **Severity:** Critical · **Effort:** Small

The `TransactionService.internalFundTransfer()` and `utilPayment()` methods subtract `amount` from `actualBalance` first, then set `availableBalance = actualBalance - amount` — resulting in a double deduction.

**Devin Prompt:**
> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the available balance calculation in both `internalFundTransfer()` and `utilPayment()`. After updating `actualBalance`, set `availableBalance` equal to the new `actualBalance` (not `actualBalance - amount` again). Add unit tests that verify correct balances after a fund transfer and utility payment.

---

### 1.2 Fix Transaction-Account `@OneToOne` / Cascade Bug
**Gap:** 7.6 · **Severity:** Critical · **Effort:** Small

`TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)` but the relationship is actually Many-to-One.

**Devin Prompt:**
> In `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`, change the `@OneToOne(cascade = CascadeType.ALL)` annotation on the `account` field to `@ManyToOne` with no cascade. Update the `@JoinColumn` accordingly. Verify existing tests still pass.

---

### 1.3 Stop Leaking Stack Traces in Error Responses
**Gap:** 2.1 · **Severity:** Critical · **Effort:** Small

The generic `handleException(Exception e)` handler in all `GlobalExceptionHandler` classes returns the raw exception string to clients.

**Devin Prompt:**
> In all `GlobalExceptionHandler` classes across the codebase (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update the catch-all `handleException` method to: (1) log the full exception at ERROR level, (2) return a generic `ErrorResponse` with code `"INTERNAL_ERROR"` and message `"An unexpected error occurred"`, and (3) return HTTP 500 instead of 400.

---

### 1.4 Remove Hardcoded Credentials from Source
**Gap:** 4.1 · **Severity:** Critical · **Effort:** Small

MySQL password, Keycloak admin password, and test credentials are hardcoded in docker-compose files and README.

**Devin Prompt:**
> Replace all hardcoded passwords in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD:-changeme}`). Create a `docker-compose/.env.example` file documenting the required variables with placeholder values. Remove the plaintext test credentials from `README.md` and reference the `.env.example` instead.

---

### 1.5 Stop Logging Sensitive Data
**Gap:** 4.7 · **Severity:** Critical · **Effort:** Small

Controllers log full request objects including passwords and financial data.

**Devin Prompt:**
> Audit all `log.info()` calls in controller and service classes across all services. Remove or redact any logging of: (1) password fields, (2) full request objects that contain sensitive data. For `UserController.createUser`, log only the email (not the full User object). For fund transfer and payment controllers, log only a transaction summary (account numbers partially masked, amount).

---

### 1.6 Add Input Validation on All Request DTOs
**Gap:** 4.3 / 2.3 · **Severity:** Critical · **Effort:** Small

No Bean Validation annotations on any request body.

**Devin Prompt:**
> Add `spring-boot-starter-validation` dependency to all business service `build.gradle` files. Add `@Valid` to all `@RequestBody` parameters in controllers. Add validation annotations to all request DTOs: `@NotNull`, `@NotBlank` for strings, `@Positive` for amounts (BigDecimal), `@Email` for email fields. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns a 422 response with field-level error details.

---

### 1.7 Use Proper HTTP Status Codes in Error Handlers
**Gap:** 2.2 · **Severity:** High · **Effort:** Small

All errors return 400 Bad Request regardless of the actual error type.

**Devin Prompt:**
> Update all `GlobalExceptionHandler` classes to return appropriate HTTP status codes: `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, `UserAlreadyRegisteredException` → 409, `InvalidEmailException` → 422, `InvalidBankingUserException` → 404, validation errors → 422, unknown exceptions → 500. Ensure all responses use the `ErrorResponse { code, message }` format consistently.

---

### 1.8 Add Feign Timeout Configuration
**Gap:** 7.3 · **Severity:** High · **Effort:** Small

No explicit timeouts configured for Feign clients.

**Devin Prompt:**
> Add Feign timeout configuration to the Spring Cloud Config for all services that use Feign clients (user-service, fund-transfer-service, utility-payment-service). Set `connectTimeout: 5000` and `readTimeout: 10000` (milliseconds). Also add these as defaults in each service's local `application.yml` as fallback.

---

### 1.9 Add Feign Retry Configuration
**Gap:** 7.2 · **Severity:** High · **Effort:** Small

No retry policies for transient failures on inter-service calls.

**Devin Prompt:**
> Add Spring Retry dependency to fund-transfer-service and utility-payment-service. Configure Feign clients with a retry policy: max 3 attempts, 1-second initial backoff, exponential multiplier of 2, retry only on 5xx responses and connection exceptions. Ensure retries are safe by adding idempotency support (see Phase 2 item).

---

### 1.10 Fix OpenAPI Dependency (WebFlux → WebMVC)
**Gap:** 5.7 · **Severity:** Medium · **Effort:** Small

MVC-based services use the WebFlux OpenAPI starter.

**Devin Prompt:**
> In `core-banking-service/build.gradle`, `internet-banking-user-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`, replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0`. Keep the WebFlux version only in `internet-banking-api-gateway/build.gradle`. Verify Swagger UI loads correctly on each service.

---

### 1.11 Add Typed ResponseEntity to All Controllers
**Gap:** 5.1 · **Severity:** Medium · **Effort:** Small

Controllers return raw `ResponseEntity` without type parameters.

**Devin Prompt:**
> Update all controller methods across all services to use typed `ResponseEntity<T>` (e.g., `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`). This ensures OpenAPI documentation correctly reflects response schemas.

---

### 1.12 Fix Keycloak Singleton Thread Safety
**Gap:** 4.5 · **Severity:** Medium · **Effort:** Small

`KeycloakProperties.getInstance()` is not thread-safe.

**Devin Prompt:**
> In `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`, replace the lazy-initialized static singleton with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once during Spring context initialization. Remove the static field and `getInstance()` method. Inject the `Keycloak` bean into `KeycloakManager` via constructor injection.

---

### 1.13 Add Dependency Vulnerability Scanning
**Gap:** 4.6 · **Severity:** High · **Effort:** Small

No automated vulnerability scanning.

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to each service's `build.gradle`. Configure it to fail the build on CVSS score >= 7. Also create a GitHub Actions workflow (`.github/workflows/dependency-check.yml`) that runs the check on every PR and weekly on the main branch.

---

### 1.14 Add Structured Logging
**Gap:** 6.1 · **Severity:** Medium · **Effort:** Small

No JSON logging or MDC enrichment.

**Devin Prompt:**
> Add `logstash-logback-encoder` dependency to all business services. Create a shared `logback-spring.xml` configuration that outputs JSON in non-local profiles and plain text in local/dev. Include MDC fields for traceId, spanId, and service name. Verify trace IDs from Micrometer Brave appear in log output.

---

### 1.15 Configure Actuator Health Checks and Prometheus
**Gap:** 6.2 / 6.3 · **Severity:** Medium · **Effort:** Small

Health endpoints are not customized; Prometheus metrics are missing.

**Devin Prompt:**
> Add `micrometer-registry-prometheus` dependency to all business services. Configure actuator in each service's `application.yml` to expose `health`, `info`, `prometheus`, and `metrics` endpoints. Enable health details for database, Eureka, and disk space. For the user-service, add a custom health indicator for Keycloak connectivity.

---

## Phase 2: Important (High-impact structural improvements)

> **Goal:** Build the foundation for reliable, maintainable, and testable microservices. Items are medium effort (1–3 days each).

### 2.1 Add Idempotency to Financial Operations
**Gap:** 7.5 · **Severity:** Critical · **Effort:** Medium

No idempotency keys on fund transfer or payment endpoints.

**Devin Prompt:**
> Implement idempotency for the fund transfer and utility payment flows. Add an `Idempotency-Key` header to `POST /api/v1/transfer` and `POST /api/v1/utility-payment`. Store the key in the database alongside the transaction record. Before processing, check if a transaction with the same idempotency key already exists — if so, return the previous result. Add appropriate tests and document the header in the OpenAPI annotations.

---

### 2.2 Add Circuit Breakers with Resilience4j
**Gap:** 7.1 · **Severity:** Critical · **Effort:** Medium

No circuit breakers on any inter-service call.

**Devin Prompt:**
> Add `resilience4j-spring-boot3` and `resilience4j-feign` dependencies to fund-transfer-service, utility-payment-service, and user-service. Configure circuit breakers for all Feign clients with: sliding window size of 10, failure rate threshold of 50%, wait duration in open state of 30 seconds. Add fallback methods that return appropriate error responses. Add a `/actuator/circuitbreakers` endpoint for monitoring.

---

### 2.3 Create a Shared Common Module
**Gap:** 1.2 / 1.3 · **Severity:** High · **Effort:** Medium

Massive code duplication with no shared library.

**Devin Prompt:**
> Create a new Gradle module called `internet-banking-common` at the project root. Move the following shared classes into it: `AuditAware`, `BaseMapper`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `GlobalErrorCode`, `CustomFeignClientConfiguration`. Update all services to depend on this common module. Set up a root `settings.gradle` that includes all subprojects.

---

### 2.4 Set Up Unified Gradle Multi-Project Build
**Gap:** 1.1 · **Severity:** Medium · **Effort:** Medium

No root build file to unify all services.

**Devin Prompt:**
> Create a root `settings.gradle` and `build.gradle` for the entire project. Include all microservice modules as subprojects. Extract shared dependency versions into a Gradle version catalog (`gradle/libs.versions.toml`). Add common plugin configuration in the root `build.gradle` using `subprojects {}` block. Verify that `./gradlew build` from the root builds all services.

---

### 2.5 Add Unit Tests for All Business Services
**Gap:** 3.1 · **Severity:** High · **Effort:** Medium

Tests exist only in core-banking-service.

**Devin Prompt:**
> Add comprehensive unit tests for: (1) `internet-banking-user-service` — test `UserService.createUser()`, `updateUser()`, `readUsers()`, mock Keycloak and Feign dependencies; (2) `internet-banking-fund-transfer-service` — test `FundTransferService.fundTransfer()` and `readAllTransfers()`, mock Feign client; (3) `internet-banking-utility-payment-service` — test `UtilityPaymentService.utilPayment()` and `readPayments()`, mock Feign client. Use JUnit 5 + Mockito. Target 80% line coverage on service classes.

---

### 2.6 Set Up CI Pipeline with GitHub Actions
**Gap:** 3.5 · **Severity:** High · **Effort:** Medium

No CI pipeline exists.

**Devin Prompt:**
> Create a GitHub Actions CI pipeline (`.github/workflows/ci.yml`) that: (1) runs on every PR and push to main, (2) sets up Java 21, (3) builds all services with Gradle, (4) runs all unit tests, (5) uploads test reports as artifacts, (6) runs OWASP dependency check. Add branch protection rules requiring CI to pass before merge.

---

### 2.7 Add Authentication to Downstream Services
**Gap:** 4.4 · **Severity:** High · **Effort:** Medium

Individual services do not validate JWT tokens.

**Devin Prompt:**
> Add `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security` to core-banking-service, user-service, fund-transfer-service, and utility-payment-service. Configure each service as an OAuth2 Resource Server that validates JWTs from Keycloak. Exempt actuator endpoints from authentication. Ensure the API Gateway forwards the `Authorization` header to downstream services. Add integration tests to verify that unauthenticated requests to downstream services are rejected with 401.

---

### 2.8 Add Fallback Behavior for Feign Clients
**Gap:** 7.4 · **Severity:** Medium · **Effort:** Medium

No graceful degradation when dependencies are unavailable.

**Devin Prompt:**
> Create Feign fallback classes for all Feign clients: `BankingCoreFeignClientFallback` in fund-transfer-service and utility-payment-service, `BankingCoreRestClientFallback` in user-service. Each fallback should log the error and throw a meaningful exception (e.g., `ServiceUnavailableException`) that the `GlobalExceptionHandler` maps to HTTP 503. Register fallbacks via `@FeignClient(fallback = ...)`.

---

### 2.9 Add Pagination Metadata to List Endpoints
**Gap:** 5.4 · **Severity:** Medium · **Effort:** Small

Paginated endpoints return raw lists without page info.

**Devin Prompt:**
> Create a generic `PageResponse<T>` wrapper class in the common module with fields: `content` (List<T>), `page` (int), `size` (int), `totalElements` (long), `totalPages` (int). Update all paginated controller endpoints across all services to return `ResponseEntity<PageResponse<T>>` instead of `ResponseEntity<List<T>>`. Update the service layer to return the Spring Data `Page` object and map it to `PageResponse` in the controller.

---

## Phase 3: Polish (Best-practice refinements)

> **Goal:** Elevate code quality, documentation, and developer experience. Items are low severity or large effort.

### 3.1 Add Integration Tests with Testcontainers
**Gap:** 3.2 · **Severity:** High · **Effort:** Large

No integration tests for database operations or service startup.

**Devin Prompt:**
> Add Testcontainers dependency to all business services. Create `@SpringBootTest` integration tests that: (1) start a MySQL Testcontainer, (2) verify the application context loads, (3) test repository operations against a real database, (4) for core-banking-service, verify Flyway migrations run successfully. Use WireMock to stub Feign client calls in integration tests for user-service, fund-transfer-service, and utility-payment-service.

---

### 3.2 Add Contract Tests Between Services
**Gap:** 3.3 · **Severity:** Medium · **Effort:** Large

No consumer-driven contract testing.

**Devin Prompt:**
> Add Spring Cloud Contract to core-banking-service as the provider. Define contracts for all endpoints consumed by other services: `GET /api/v1/user/{identification}`, `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`. Generate WireMock stubs from the contracts. Configure consumer services (user-service, fund-transfer-service, utility-payment-service) to use the generated stubs in their tests.

---

### 3.3 Add Test Coverage Reporting with JaCoCo
**Gap:** 3.4 · **Severity:** Low · **Effort:** Small

No coverage metrics or enforcement.

**Devin Prompt:**
> Add the JaCoCo Gradle plugin to all services via the root `build.gradle`. Configure a minimum coverage threshold of 70% on service and controller packages. Generate HTML and XML reports. Update the GitHub Actions CI pipeline to upload coverage reports and add a coverage badge to the README.

---

### 3.4 Standardize Package Structure Across Services
**Gap:** 1.4 · **Severity:** Low · **Effort:** Small

Inconsistent package naming between services.

**Devin Prompt:**
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.exception`, `com.javatodev.finance.configuration`. Refactor user-service's `model.repository` to `repository`, and fund-transfer's `service.rest.client` to `service.rest`.

---

### 3.5 Convert Mappers to Spring Beans
**Gap:** 1.5 · **Severity:** Low · **Effort:** Small

Mappers are `new`-ed inline instead of injected.

**Devin Prompt:**
> Add `@Component` to all mapper classes across all services. Remove the `new XyzMapper()` inline instantiation from service classes and replace with constructor injection. This enables mockability in tests and consistency with the Spring DI pattern.

---

### 3.6 Standardize URL Naming Conventions
**Gap:** 5.3 / 5.6 · **Severity:** Low · **Effort:** Small

Inconsistent and non-RESTful URL patterns.

**Devin Prompt:**
> Standardize all API URLs to use plural nouns, no verbs: rename `/api/v1/bank-users/register` to `POST /api/v1/users`, rename `/api/v1/bank-users/update/{id}` to `PATCH /api/v1/users/{id}`, rename `/api/v1/transfer` to `/api/v1/fund-transfers`, rename `/api/v1/utility-payment` to `/api/v1/utility-payments`. Update API Gateway routes accordingly. Document the changes in CHANGELOG.md.

---

### 3.7 Document API Versioning Strategy
**Gap:** 5.2 · **Severity:** Low · **Effort:** Small

No documented approach for API evolution.

**Devin Prompt:**
> Create an `API_VERSIONING.md` document in the `docs/` folder that outlines the API versioning strategy: URL-path-based versioning (`/api/v1/`, `/api/v2/`), rules for when to increment versions (breaking changes only), backward compatibility requirements, and deprecation timeline. Add a link to this document from the main README.

---

### 3.8 Add OpenAPI Filtering/Sorting Documentation
**Gap:** 5.5 · **Severity:** Low · **Effort:** Small

Pageable parameters are not documented in OpenAPI.

**Devin Prompt:**
> Add `@Parameter` annotations to all controller methods that accept `Pageable` to document the available query parameters: `page` (default 0), `size` (default 20), and `sort` (e.g., `sort=createdDate,desc`). List the sortable fields for each endpoint.

---

### 3.9 Add Logging Level Configuration
**Gap:** 6.5 · **Severity:** Low · **Effort:** Small

No per-package logging configuration.

**Devin Prompt:**
> Add logging level configuration to each service's Spring Cloud Config: set `com.javatodev.finance` to `INFO`, `org.springframework.web` to `WARN`, `org.hibernate.SQL` to `WARN` (with `DEBUG` in dev profile). Enable the `/actuator/loggers` endpoint so logging levels can be changed at runtime without restart.

---

### 3.10 Verify and Document Distributed Tracing
**Gap:** 6.4 · **Severity:** Low · **Effort:** Small

Tracing may not be fully functional end-to-end.

**Devin Prompt:**
> Verify that distributed tracing works end-to-end by: (1) starting all services with Docker Compose, (2) making a fund transfer request through the API Gateway, (3) checking Zipkin for a complete trace spanning API Gateway → fund-transfer-service → core-banking-service. Document the tracing setup and verification steps in `docs/OBSERVABILITY.md`. If trace propagation is broken, fix the Feign/Gateway configuration.

---

### 3.11 Document CSRF Disable Decision
**Gap:** 4.2 · **Severity:** Medium · **Effort:** Small

CSRF disabled without justification.

**Devin Prompt:**
> Add a code comment in `SecurityConfiguration.java` in the API Gateway explaining why CSRF is disabled (stateless API using JWT Bearer tokens, no cookie-based sessions). Also add a `SECURITY.md` document in `docs/` that lists all security design decisions and their rationale.

---

## Roadmap Summary

| Phase | Items | Critical Fixes | Estimated Total Effort |
|---|---|---|---|
| **Phase 1: Quick Wins** | 15 items | 6 | ~10 days |
| **Phase 2: Important** | 9 items | 2 | ~15 days |
| **Phase 3: Polish** | 11 items | 0 | ~8 days |
| **Total** | **35 items** | **8** | **~33 days** |

### Recommended Execution Order

1. **Immediately** (Phase 1.1–1.6): Fix bugs and critical security issues first.
2. **Week 1–2** (Phase 1.7–1.15): Complete remaining quick wins.
3. **Week 3–4** (Phase 2.1–2.2): Add idempotency and circuit breakers.
4. **Week 4–5** (Phase 2.3–2.4): Create shared module and unified build.
5. **Week 5–6** (Phase 2.5–2.9): Add tests, CI, authentication, and fallbacks.
6. **Week 7–9** (Phase 3): Polish items as time permits, prioritizing 3.1 (integration tests) and 3.11 (security documentation).
