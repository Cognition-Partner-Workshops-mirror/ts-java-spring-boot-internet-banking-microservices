# Remediation Roadmap

This roadmap organizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort changes that immediately improve security, correctness, and developer experience.

### 1.1 Fix HTTP Status Codes in Exception Handlers
**Gap:** GAP-ERR-01 (Critical) | **Effort:** Small

All `GlobalExceptionHandler` classes return HTTP 400 for every error. Map exceptions to correct HTTP statuses.

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Map `EntityNotFoundException` to HTTP 404, `InsufficientFundsException` to HTTP 409, `SimpleBankingGlobalException` to HTTP 400, and the catch-all `Exception` handler to HTTP 500. Ensure all responses use the `ErrorResponse` DTO (never return raw strings). Add a handler for `MethodArgumentNotValidException` that returns HTTP 422 with field-level error details. Open a PR with the changes.

---

### 1.2 Remove Leaked Exception Details from Error Responses
**Gap:** GAP-ERR-02 (High) | **Effort:** Small

The catch-all exception handler returns `"Exception occur inside API " + e`, exposing internals.

**Devin Prompt:**
> In all `GlobalExceptionHandler` classes across the four business services, replace the catch-all `Exception` handler so it returns a generic `ErrorResponse` with code `"INTERNAL_ERROR"` and message `"An unexpected error occurred. Please try again later."` instead of leaking the raw exception. Log the full exception at ERROR level for debugging. Open a PR.

---

### 1.3 Add Input Validation to All Request DTOs
**Gap:** GAP-SEC-02 (Critical) | **Effort:** Medium

No controller validates request bodies. Add Jakarta Bean Validation.

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services:
> - `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`.
> - `UtilityPaymentRequest`: `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`.
> - `User` (user-service): `@NotBlank @Email` on `email`, `@NotBlank` on `identification`, `@NotBlank @Size(min=8)` on `password`.
> - `UserUpdateRequest`: `@NotNull` on `status`.
>
> Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` that returns HTTP 422 with field-level errors. Open a PR.

---

### 1.4 Externalize Hardcoded Credentials
**Gap:** GAP-SEC-01 (Critical) | **Effort:** Small

Passwords are hardcoded in Docker Compose files and source code.

**Devin Prompt:**
> Replace all hardcoded credentials in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `docker-compose/.env.example` file documenting all required variables with placeholder values. Update the `README.md` to instruct users to copy `.env.example` to `.env` and fill in their values. Add `.env` to `.gitignore`. Remove the Keycloak client-secret from `internet-banking-user-service/src/test/resources/application.yml` and use a placeholder. Open a PR.

---

### 1.5 Fix OpenAPI Dependency (WebFlux → WebMVC)
**Gap:** GAP-API-05 (Low) | **Effort:** Small

Four MVC services use the WebFlux Springdoc starter, which may not scan MVC controllers properly.

**Devin Prompt:**
> In the `build.gradle` of core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`. Verify the Swagger UI loads correctly for each service. Open a PR.

---

### 1.6 Add Typed ResponseEntity to All Controllers
**Gap:** GAP-API-01 (High) | **Effort:** Small

Controllers return raw `ResponseEntity` without generic types, breaking Swagger docs.

**Devin Prompt:**
> Update all controller methods across all services to use typed `ResponseEntity<T>` instead of raw `ResponseEntity`. For example, `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`, `ResponseEntity<FundTransferResponse>`, etc. This will improve OpenAPI documentation and compile-time safety. Open a PR.

---

### 1.7 Set Feign Client Timeouts
**Gap:** GAP-RES-02 (High) | **Effort:** Small

Feign clients have no timeout configuration.

**Devin Prompt:**
> Add Feign timeout configuration to all three Feign-client-using services (user-service, fund-transfer-service, utility-payment-service). In each service's `application.yml`, add:
> ```yaml
> spring:
>   cloud:
>     openfeign:
>       client:
>         config:
>           default:
>             connect-timeout: 5000
>             read-timeout: 10000
> ```
> Open a PR.

---

### 1.8 Fix Keycloak Singleton Thread-Safety
**Gap:** GAP-SEC-04 (High) | **Effort:** Small

`KeycloakProperties.getInstance()` is not thread-safe.

**Devin Prompt:**
> Refactor `KeycloakProperties` in internet-banking-user-service to use a Spring `@Bean` method for the `Keycloak` instance instead of the manual static singleton. Create a `KeycloakConfig` class annotated with `@Configuration` that exposes a `@Bean Keycloak keycloak(KeycloakProperties props)`. Remove the static `keycloakInstance` field and `getInstance()` method. Update `KeycloakManager` to inject the `Keycloak` bean directly. Open a PR.

---

### 1.9 Add Feign Retry for Idempotent Operations
**Gap:** GAP-RES-03 (High) | **Effort:** Small

No retry logic for transient Feign errors.

**Devin Prompt:**
> Add a Feign `Retryer` bean to `CustomFeignClientConfiguration` in all three Feign-client-using services. Configure it to retry up to 3 times with 1-second initial interval and 5-second max interval for GET requests only. For POST requests, use `Retryer.NEVER_RETRY`. Open a PR.

---

### 1.10 Add Consistent Error Code Catalog
**Gap:** GAP-ERR-03 (High) | **Effort:** Small

Only 2 of 4 services define error codes.

**Devin Prompt:**
> Create a `GlobalErrorCode` class in the fund-transfer-service and utility-payment-service with service-specific error codes following the pattern `{SERVICE_PREFIX}-{NUMBER}` (e.g., `FUND-TRANSFER-1000`, `UTILITY-PAYMENT-1000`). Update all `SimpleBankingGlobalException` usages in these services to include error codes. Document the full error code catalog as a table in a new `docs/ERROR_CODES.md` file. Open a PR.

---

## Phase 2: Important (3–6 weeks)

Structural improvements that require moderate effort but significantly improve maintainability, testability, and resilience.

### 2.1 Create Multi-Module Gradle Build
**Gap:** GAP-ORG-01 (High) | **Effort:** Medium

**Devin Prompt:**
> Convert this project into a Gradle multi-module build. Create a root `settings.gradle` that includes all 6 services as subprojects. Create a root `build.gradle` that defines shared dependency versions using a Gradle version catalog (`libs.versions.toml`). Move common Spring Boot, Spring Cloud, and plugin versions to the root. Each service should inherit from the root and only declare service-specific dependencies. Remove the individual Gradle wrapper files from each service (keep only the root wrapper). Verify that `./gradlew build` from the root builds all services. Open a PR.

---

### 2.2 Extract Shared Library Module
**Gap:** GAP-ORG-02 (High) | **Effort:** Large

**Devin Prompt:**
> Create a new `banking-common` module in the multi-module Gradle project. Move the following duplicated classes into it:
> - `BaseMapper<E, D>`
> - `AuditAware` (rename to `AuditableEntity`, move to `model.entity` package)
> - `AuditorAwareConfig`, `AuditConfig`
> - `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`
> - `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`
> - `TransactionStatus` enum
> - `CustomFeignClientConfiguration`
>
> Update all services to depend on `banking-common` and remove their local copies. Verify all services compile and tests pass. Open a PR.

---

### 2.3 Add Circuit Breakers to Feign Clients
**Gap:** GAP-RES-01 (Critical) | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breakers to all Feign clients in the three business services (user-service, fund-transfer-service, utility-payment-service). Steps:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency.
> 2. Enable circuit breakers for Feign: `spring.cloud.openfeign.circuitbreaker.enabled=true`.
> 3. Create fallback classes for each Feign client that return meaningful error responses (e.g., throw a `SimpleBankingGlobalException` with message "Core banking service is temporarily unavailable").
> 4. Configure Resilience4j with: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`.
> 5. Add unit tests for the fallback behavior.
> Open a PR.

---

### 2.4 Add Custom Feign Error Decoder to All Services
**Gap:** GAP-ERR-04 (Medium) | **Effort:** Medium

**Devin Prompt:**
> The user-service has a `CustomFeignErrorDecoder` but the fund-transfer-service and utility-payment-service do not. After extracting the shared library (Phase 2.2), move `CustomFeignErrorDecoder` to the `banking-common` module. Register it in the shared `CustomFeignClientConfiguration`. Ensure all Feign clients across all services use this error decoder to properly propagate structured error responses from downstream services. Add unit tests for the error decoder. Open a PR.

---

### 2.5 Add Unit Tests for User, Fund-Transfer, and Utility-Payment Services
**Gap:** GAP-TEST-01 (Critical) | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for the three internet banking services:
>
> **internet-banking-user-service:**
> - `UserServiceTest`: Test `createUser` (happy path, duplicate email, invalid email, user not found in core), `readUsers`, `readUser`, `updateUser` (approve, disable). Mock `KeycloakUserService`, `UserRepository`, `BankingCoreRestClient`.
> - `KeycloakUserServiceTest`: Test `createUser`, `updateUser`, `readUserByEmail`, `readUser`. Mock `KeycloakManager`.
> - `UserControllerTest`: Use `@WebMvcTest` + `MockMvc` to test all endpoints with mocked service layer.
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferServiceTest`: Test `fundTransfer` (happy path, Feign error), `readAllTransfers`. Mock `FundTransferRepository`, `BankingCoreFeignClient`.
> - `FundTransferControllerTest`: Use `@WebMvcTest` + `MockMvc`.
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentServiceTest`: Test `utilPayment` (happy path, Feign error), `readPayments`. Mock `UtilityPaymentRepository`, `BankingCoreRestClient`.
> - `UtilityPaymentControllerTest`: Use `@WebMvcTest` + `MockMvc`.
>
> Fix or remove the broken `contextLoads()` tests. Open a PR.

---

### 2.6 Add Structured JSON Logging
**Gap:** GAP-OBS-01 (High) | **Effort:** Medium

**Devin Prompt:**
> Configure structured JSON logging for all services:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to all services (or the shared `banking-common` module).
> 2. Create a `logback-spring.xml` in each service's `src/main/resources/` that outputs JSON in production profile and plain text in dev profile.
> 3. Include trace ID, span ID, service name, and log level in the JSON output.
> 4. Audit all `log.info()` statements to ensure no passwords, tokens, or PII are logged. Redact `User.password` and any `toString()` calls that may expose sensitive fields.
> Open a PR.

---

### 2.7 Return Pagination Metadata from List Endpoints
**Gap:** GAP-API-03 (Medium) | **Effort:** Small

**Devin Prompt:**
> Update all paginated list endpoints to return full pagination metadata instead of raw lists. Create a generic `PageResponse<T>` wrapper DTO in the shared library with fields: `content`, `totalElements`, `totalPages`, `pageNumber`, `pageSize`. Update the following endpoints:
> - `GET /api/v1/user` (core-banking-service)
> - `GET /api/v1/bank-users` (user-service)
> - `GET /api/v1/transfer` (fund-transfer-service)
> - `GET /api/v1/utility-payment` (utility-payment-service)
> Open a PR.

---

### 2.8 Secure Downstream Services
**Gap:** GAP-SEC-03 (High) | **Effort:** Medium

**Devin Prompt:**
> Add basic security to the four downstream services (core-banking, user, fund-transfer, utility-payment) so they cannot be accessed directly without going through the API Gateway. Options:
> 1. **Preferred:** Add `spring-boot-starter-security` to each service. Configure Spring Security to require the `X-Auth-Id` header on all `/api/**` endpoints (reject with 401 if missing). Allow `/actuator/**` without authentication.
> 2. **Alternative:** Validate the JWT token in each service by adding `spring-boot-starter-oauth2-resource-server` and configuring the JWK Set URI.
> Choose option 1 for simplicity. Open a PR.

---

### 2.9 Add Dependency Vulnerability Scanning
**Gap:** GAP-SEC-05 (Medium) | **Effort:** Small

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to the root `build.gradle` (or each service if not yet multi-module). Configure it with `failBuildOnCVSS=7` to fail the build on high-severity vulnerabilities. Add a GitHub Actions workflow that runs `./gradlew dependencyCheckAnalyze` on every PR. Open a PR.

---

## Phase 3: Polish (6–12 weeks)

Advanced improvements for production readiness, operational excellence, and long-term maintainability.

### 3.1 Add Contract Tests Between Services
**Gap:** GAP-TEST-03 (High) | **Effort:** Medium

**Devin Prompt:**
> Add Spring Cloud Contract tests to verify Feign client compatibility between services:
> 1. Add `spring-cloud-starter-contract-verifier` to `core-banking-service` (the provider).
> 2. Write contract DSL files for each endpoint consumed by other services: `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`, `GET /api/v1/user/{identification}`.
> 3. Add `spring-cloud-starter-contract-stub-runner` to the consumer services (user, fund-transfer, utility-payment).
> 4. Write consumer-side tests that verify the Feign clients work against the contract stubs.
> Open a PR.

---

### 3.2 Add Integration Tests with Testcontainers
**Gap:** GAP-TEST-02 (High) | **Effort:** Large

**Devin Prompt:**
> Add integration tests using Testcontainers for all database-backed services:
> 1. Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to the test dependencies.
> 2. Create a base test class `AbstractIntegrationTest` that starts a MySQL container and configures the datasource.
> 3. Write integration tests for `core-banking-service` that verify Flyway migrations run successfully and repository methods work with real MySQL.
> 4. Write integration tests for the other three services that verify JPA entity persistence and query methods.
> Open a PR.

---

### 3.3 Implement Saga / Outbox Pattern for Fund Transfers
**Gap:** GAP-RES-04 (Medium) | **Effort:** Large

**Devin Prompt:**
> Implement the transactional outbox pattern for the fund-transfer-service to handle distributed transaction consistency:
> 1. Create an `outbox_event` table with columns: `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload` (JSON), `status` (PENDING/PUBLISHED), `created_at`.
> 2. When processing a fund transfer, save the `FundTransferEntity` and an `OutboxEvent` in the same local transaction.
> 3. Create a scheduled job (`@Scheduled`) that polls for PENDING outbox events and calls core-banking-service. On success, mark the event as PUBLISHED and update the transfer status.
> 4. On failure, increment a retry counter; after max retries, mark the transfer as FAILED.
> 5. Add a reconciliation endpoint `GET /api/v1/transfer/reconcile` that checks for stuck PENDING transfers.
> Open a PR.

---

### 3.4 Add Prometheus Metrics and Grafana Dashboards
**Gap:** GAP-OBS-03 (Medium) | **Effort:** Medium

**Devin Prompt:**
> Add Prometheus metrics support to all services:
> 1. Add `io.micrometer:micrometer-registry-prometheus` dependency to all services.
> 2. Expose `/actuator/prometheus` endpoint (add to actuator includes).
> 3. Add custom business metrics using Micrometer `Counter` and `Timer`:
>    - `banking.fund_transfer.total` (counter, tags: status)
>    - `banking.utility_payment.total` (counter, tags: status)
>    - `banking.user.registration.total` (counter, tags: status)
>    - `banking.core.transaction.duration` (timer)
> 4. Add a `docker-compose/prometheus.yml` configuration that scrapes all services.
> 5. Add a Prometheus and Grafana service to `docker-compose.yml`.
> 6. Create a basic Grafana dashboard JSON with panels for request rate, error rate, latency percentiles, and JVM metrics.
> Open a PR.

---

### 3.5 Add Custom Health Indicators and Docker Health Checks
**Gap:** GAP-OBS-02 (Medium) | **Effort:** Small

**Devin Prompt:**
> Improve health check coverage:
> 1. Add custom `HealthIndicator` beans in each service:
>    - Database connectivity (already provided by Spring Boot auto-config, just ensure it's enabled).
>    - Eureka registration status.
>    - For user-service: Keycloak connectivity check.
>    - For fund-transfer/utility-payment: core-banking-service Feign client health.
> 2. Add `healthcheck` directives to all Docker Compose services using `curl` to `/actuator/health`.
> 3. Configure liveness and readiness probes in a Kubernetes deployment manifest (`k8s/` directory) if one exists, or create a basic one.
> Open a PR.

---

### 3.6 Standardize Package Structure Across Services
**Gap:** GAP-ORG-03 (Medium) | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all services to follow this convention:
> ```
> com.javatodev.finance
> ├── config/          (Spring @Configuration classes)
> ├── controller/      (REST controllers)
> ├── service/         (Business logic)
> ├── repository/      (Spring Data repositories)
> ├── model/
> │   ├── entity/      (JPA entities)
> │   ├── dto/         (Data transfer objects)
> │   └── mapper/      (Entity ↔ DTO mappers)
> ├── exception/       (Exception classes and handlers)
> └── client/          (Feign clients, if applicable)
> ```
> Move classes to match this structure. Update all imports. Verify all services compile and tests pass. Open a PR.

---

### 3.7 Add Filtering and Sorting to List Endpoints
**Gap:** GAP-API-04 (Medium) | **Effort:** Medium

**Devin Prompt:**
> Add query parameter support for filtering and sorting on list endpoints:
> - `GET /api/v1/transfer?status=SUCCESS&fromAccount=100015003000&sort=createdDate,desc`
> - `GET /api/v1/utility-payment?status=PROCESSING&providerId=1&sort=amount,asc`
> - `GET /api/v1/bank-users?status=PENDING&sort=id,desc`
> - `GET /api/v1/user?email=sam@gmail.com&sort=firstName,asc`
>
> Use Spring Data JPA `Specification` pattern or `@Query` with optional parameters. Document the query parameters in OpenAPI annotations. Open a PR.

---

### 3.8 Add CI/CD Pipeline
**Gap:** GAP-ORG (implicit) | **Effort:** Medium

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline in `.github/workflows/ci.yml`:
> 1. **On PR**: Run `./gradlew build` (compiles + tests), `./gradlew dependencyCheckAnalyze` (vulnerability scan), and report test results.
> 2. **On merge to main**: Build Docker images for all 6 services, tag with the Git SHA and `latest`, and push to GitHub Container Registry (ghcr.io).
> 3. Add build status badges to `README.md`.
> 4. Cache Gradle dependencies between runs.
> Open a PR.

---

### 3.9 Add .editorconfig and Code Formatting
**Gap:** GAP-ORG-05 (Low) | **Effort:** Small

**Devin Prompt:**
> Add an `.editorconfig` file to the repository root with settings for Java (4-space indent, UTF-8, LF line endings, trim trailing whitespace). Add the Spotless Gradle plugin (`com.diffplug.spotless`) to the root build with Google Java Format. Run `./gradlew spotlessApply` to format all Java files. Open a PR.

---

### 3.10 Add Zipkin Tracing Defaults
**Gap:** GAP-OBS-04 (Low) | **Effort:** Small

**Devin Prompt:**
> Add sensible default tracing configuration to each service's `application.yml` so tracing works even without the Config Server:
> ```yaml
> management:
>   zipkin:
>     tracing:
>       endpoint: http://localhost:9411/api/v2/spans
>   tracing:
>     sampling:
>       probability: 0.1
> ```
> These defaults will be overridden by Config Server values when available. Open a PR.

---

## Phase Summary

| Phase | Items | Critical Gaps Addressed | Estimated Duration |
|---|---|---|---|
| **Phase 1: Quick Wins** | 10 items | GAP-ERR-01, GAP-SEC-01, GAP-SEC-02 | 1–2 weeks |
| **Phase 2: Important** | 9 items | GAP-RES-01, GAP-TEST-01 | 3–6 weeks |
| **Phase 3: Polish** | 10 items | — | 6–12 weeks |
| **Total** | **29 items** | **5 Critical gaps** | **10–20 weeks** |

---

## Dependency Graph

Some items have dependencies on others:

```
Phase 2.1 (Multi-Module Gradle) ──▶ Phase 2.2 (Shared Library) ──▶ Phase 2.4 (Shared Feign Error Decoder)
                                                                  ──▶ Phase 2.6 (Shared Logging Config)
                                                                  ──▶ Phase 2.7 (Shared PageResponse DTO)
Phase 1.3 (Input Validation) ──▶ Phase 1.2 (Error Response Fix) [MethodArgumentNotValidException handler]
Phase 2.9 (Dep Scanning) ──▶ Phase 3.8 (CI/CD Pipeline) [integrate into workflow]
```

All Phase 1 items are independent and can be executed in parallel.
