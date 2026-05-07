# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical fixes, Small/Medium effort)

These items address critical bugs, security issues, and foundational problems that can be resolved relatively quickly.

### 1.1 Fix Available Balance Double-Deduction Bug

**Gap Reference:** Resilience 7.7 — Incorrect Available Balance Calculation
**Severity:** Critical | **Effort:** Small

The `availableBalance` is subtracted from the already-reduced `actualBalance`, causing a double deduction. This is an active financial bug.

**Devin Prompt:**
> Fix the available balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, `setAvailableBalance` incorrectly subtracts `amount` from the already-updated `actualBalance`. Change the `setAvailableBalance` calls to subtract `amount` from the original balance, or simply set `availableBalance = actualBalance` after the `actualBalance` update (since they should stay in sync for this application). Add unit tests for both methods that assert the final `actualBalance` and `availableBalance` are correct.

---

### 1.2 Add Bean Validation to All Request DTOs

**Gap Reference:** Error Handling 2.5, Security 4.3 — No Input Validation
**Severity:** Critical | **Effort:** Medium

No `@Valid` or constraint annotations exist. Financial amounts, account numbers, and user data are accepted without any server-side validation.

**Devin Prompt:**
> Add Jakarta Bean Validation (`spring-boot-starter-validation`) to all four business services. Annotate all request DTO fields with appropriate constraints: `@NotBlank` for account numbers, `@NotNull @Positive` for amounts, `@Email` for email fields, `@NotNull` for required IDs. Add `@Valid` to all `@RequestBody` parameters in controllers. Update `GlobalExceptionHandler` in each service to handle `MethodArgumentNotValidException` and return a structured `ErrorResponse` with field-level error details and HTTP 400. Add unit tests for validation in each service.

---

### 1.3 Fix Error Handling — Proper HTTP Status Codes and Structured Responses

**Gap Reference:** Error Handling 2.1, 2.2, 2.3 — Generic catch-all, inconsistent responses, wrong status codes
**Severity:** Critical/High | **Effort:** Small

All errors currently return 400 with raw exception strings, leaking internal details.

**Devin Prompt:**
> Refactor `GlobalExceptionHandler` in all four business services (core-banking, user-service, fund-transfer, utility-payment) to: (1) Return `404 Not Found` for `EntityNotFoundException`, (2) Return `422 Unprocessable Entity` for `InsufficientFundsException`, (3) Return `409 Conflict` for `UserAlreadyRegisteredException`, (4) Return `500 Internal Server Error` for unhandled exceptions with a generic message (do NOT expose exception details), (5) Always return `ErrorResponse{code, message}` as the response body. Extract the exception handler into a shared library module. Add unit tests for each error scenario.

---

### 1.4 Externalize Secrets from Source Code

**Gap Reference:** Security 4.1 — Hardcoded Credentials
**Severity:** Critical | **Effort:** Small

Database passwords, Keycloak admin credentials, and test user passwords are committed in plain text.

**Devin Prompt:**
> Replace all hardcoded credentials in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/privileges.sql` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `.env.example` file with placeholder values documenting all required variables. Add `.env` to `.gitignore`. Remove the test credentials from `README.md` and reference the `.env.example` file instead. Ensure Docker Compose still functions correctly with a populated `.env` file.

---

### 1.5 Add Circuit Breakers to Feign Clients

**Gap Reference:** Resilience 7.1 — No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

Core banking failures cascade to all upstream services with no protection.

**Devin Prompt:**
> Add Resilience4j circuit breaker support to the fund-transfer-service, utility-payment-service, and user-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's `build.gradle`. Enable Feign circuit breakers via `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's configuration. Create fallback classes for each Feign client that return meaningful error responses (e.g., "Core banking service is temporarily unavailable"). Configure circuit breaker thresholds: 50% failure rate, 10-second sliding window, 30-second wait in open state. Add integration tests demonstrating circuit breaker behavior.

---

### 1.6 Fix OpenAPI Dependency Mismatch

**Gap Reference:** API Design 5.6 — webflux-ui used on webmvc services
**Severity:** Medium | **Effort:** Small

Services run Spring MVC but declare `springdoc-openapi-starter-webflux-ui`. Swagger UI may not work correctly.

**Devin Prompt:**
> In the `build.gradle` of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. Verify each service starts correctly and that the Swagger UI is accessible at `/swagger-ui.html`. Leave the API Gateway's dependency as-is since it uses WebFlux.

---

### 1.7 Add Type Parameters to ResponseEntity Returns

**Gap Reference:** API Design 5.1 — Raw ResponseEntity without generics
**Severity:** Medium | **Effort:** Small

Controllers return untyped `ResponseEntity`, weakening compile-time safety and OpenAPI documentation.

**Devin Prompt:**
> Add type parameters to all `ResponseEntity` return types in every controller across all services. For example, change `ResponseEntity` to `ResponseEntity<BankAccount>` in `AccountController.getBankAccount()`. Update all controller methods to use explicit generic types that match the actual return values. This will improve OpenAPI spec generation and compile-time safety.

---

## Phase 2: Important (High-severity gaps and structural improvements)

### 2.1 Extract Shared Library Module

**Gap Reference:** Code Organization 1.1, 1.2 — No multi-project build, duplicated code
**Severity:** High/Medium | **Effort:** Medium

Eliminate the copy-pasted code across services by creating a shared module.

**Devin Prompt:**
> Create a new Gradle submodule `banking-common` at the repository root. Move the following duplicated classes into it: `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `BaseMapper`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`. Set up a root `settings.gradle` that includes all service projects and the common module. Update each service's `build.gradle` to depend on `banking-common`. Remove the duplicate source files from each service. Verify all services compile and tests pass.

---

### 2.2 Secure Core Banking Service

**Gap Reference:** Security 4.7 — Downstream services not authenticated
**Severity:** High | **Effort:** Medium

Core banking has no authentication — anyone on the network can call it directly.

**Devin Prompt:**
> Add Spring Security to core-banking-service. Add `spring-boot-starter-security` to its `build.gradle`. Configure it to validate the `X-Auth-Id` header as a trusted principal (since requests come from the API Gateway which has already validated the JWT). Alternatively, implement service-to-service authentication by: (1) adding `spring-boot-starter-oauth2-resource-server` and validating JWTs directly, OR (2) adding a shared secret / API key mechanism. Ensure actuator endpoints remain accessible without authentication. Update the Feign client configurations in upstream services to forward the JWT or API key. Add integration tests verifying that unauthenticated requests are rejected.

---

### 2.3 Add Retry and Timeout Configuration

**Gap Reference:** Resilience 7.2, 7.3 — No retries, no timeouts
**Severity:** High | **Effort:** Small

No timeout or retry policies exist for inter-service communication.

**Devin Prompt:**
> Configure Feign client timeouts and retry policies for the fund-transfer-service, utility-payment-service, and user-service. In each service's configuration, set: `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`. Add Resilience4j retry configuration with 3 max attempts, 1-second wait between retries, and retry only on `5xx` errors and `IOException`. Ensure retries do NOT apply to POST endpoints (fund transfers, payments) since they are not idempotent yet. Add tests verifying timeout behavior.

---

### 2.4 Add Idempotency to Financial Transactions

**Gap Reference:** Resilience 7.5 — No idempotency keys
**Severity:** Critical | **Effort:** Medium

Retried requests can cause duplicate fund transfers or payments.

**Devin Prompt:**
> Add idempotency key support to the fund transfer and utility payment flows. In `FundTransferRequest` and `UtilityPaymentRequest`, add a required `idempotencyKey` field (UUID). In both the fund-transfer-service and utility-payment-service, before processing a transaction: (1) check if a record with the same idempotency key already exists, (2) if it does, return the existing result, (3) if not, proceed with the transaction. Add a unique constraint on the idempotency key column in both `fund_transfer` and `utility_payment` tables. Add unit and integration tests for duplicate request handling.

---

### 2.5 Add Rate Limiting at API Gateway

**Gap Reference:** Security 4.4 — No rate limiting
**Severity:** High | **Effort:** Medium

Financial endpoints are unprotected against abuse.

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter with a Redis-backed `RedisRateLimiter`. Add `spring-boot-starter-data-redis-reactive` to the gateway's dependencies. Add a Redis container to the Docker Compose file. Configure rate limits per user (extracted from JWT subject): 10 requests/second for fund transfers, 10 requests/second for utility payments, and 50 requests/second for read operations. Return `429 Too Many Requests` when limits are exceeded. Add the rate limiter as a default filter on financial service routes.

---

### 2.6 Add Unit Tests for All Business Services

**Gap Reference:** Testing 3.1 — Minimal test coverage
**Severity:** Critical | **Effort:** Large

Only core-banking has unit tests. Fund-transfer, user-service, and utility-payment have none.

**Devin Prompt:**
> Add comprehensive unit tests for the three internet banking services. For `internet-banking-fund-transfer-service`: test `FundTransferService.fundTransfer()` (success, Feign error, core banking failure), `readAllTransfers()` (pagination). For `internet-banking-user-service`: test `UserService.createUser()` (success, duplicate email, invalid identification, Keycloak failure), `readUsers()`, `readUser()`, `updateUser()` (approve flow, entity not found). For `internet-banking-utility-payment-service`: test `UtilityPaymentService.utilPayment()` (success, core banking failure), `readPayments()`. Use Mockito to mock Feign clients and repositories. Target at least 80% line coverage on service classes.

---

### 2.7 Add Pessimistic Locking for Balance Updates

**Gap Reference:** Resilience 7.6 — Non-atomic balance updates / race conditions
**Severity:** Critical | **Effort:** Medium

Concurrent transfers from the same account can both pass validation due to lack of database-level locking.

**Devin Prompt:**
> Add pessimistic write locking to account balance operations in core-banking-service. In `BankAccountRepository`, add a method `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT b FROM BankAccountEntity b WHERE b.number = :number") Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String number)`. Update `TransactionService.internalFundTransfer()` and `utilPayment()` to use `findByNumberForUpdate()` instead of `findByNumber()` when reading accounts for modification. Add a concurrent transfer test using multiple threads to verify that race conditions are prevented.

---

### 2.8 Add Dependency Vulnerability Scanning

**Gap Reference:** Security 4.6 — No dependency vulnerability scanning
**Severity:** High | **Effort:** Small

No automated scanning for known CVEs in dependencies.

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to all service `build.gradle` files. Apply `id 'org.owasp.dependencycheck' version '9.0.9'` in each plugins block. Configure it to fail the build on CVSS score >= 7.0. Add a root `build.gradle` with the plugin applied to all subprojects (once the multi-project build is set up). Run `./gradlew dependencyCheckAnalyze` on each service and document any existing vulnerabilities that need attention. Add the OWASP reports directory to `.gitignore`.

---

## Phase 3: Polish (Medium/Low-severity improvements)

### 3.1 Add Pagination Metadata to List Endpoints

**Gap Reference:** API Design 5.4 — No pagination metadata
**Severity:** Medium | **Effort:** Small

List endpoints return raw `List<T>` losing pagination context.

**Devin Prompt:**
> Update all list/paginated endpoints across all services to return `Page<T>` (or a custom `PageResponse<T>` wrapper with `content`, `totalElements`, `totalPages`, `pageNumber`, `pageSize` fields) instead of `List<T>`. In the service layer, return the full `Page` object from JPA. In the controllers, wrap the response appropriately. Update any existing tests to verify pagination metadata is present in the response.

---

### 3.2 Add Structured JSON Logging

**Gap Reference:** Observability 6.1 — Inconsistent logging
**Severity:** Medium | **Effort:** Medium

Plain text logs are difficult to parse in production log aggregation systems.

**Devin Prompt:**
> Add structured JSON logging to all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. Create a shared `logback-spring.xml` configuration that outputs JSON format in the `docker` profile and plain text in the `default` profile. Include traceId, spanId, serviceName, and timestamp in every log line. Audit all `log.info()` calls to ensure no sensitive data (passwords, tokens) is logged — specifically fix `UserController.createUser()` in the user-service which logs the entire User DTO including the password field. Add a `@ToString.Exclude` on the `password` field in the User DTO.

---

### 3.3 Add Custom Health Indicators

**Gap Reference:** Observability 6.2 — No custom health checks
**Severity:** Medium | **Effort:** Small

No health checks for critical dependencies beyond the default Spring Boot Actuator.

**Devin Prompt:**
> Add custom health indicators to the business services. For user-service: add a `KeycloakHealthIndicator` that checks Keycloak's `/realms/master` endpoint. For fund-transfer and utility-payment services: add a health indicator that checks core-banking-service availability via the Feign client. For all services: ensure the actuator health endpoint includes database connectivity (this should be auto-configured with JPA). Configure `management.endpoint.health.show-details=when-authorized` and expose health and info endpoints.

---

### 3.4 Add Prometheus Metrics

**Gap Reference:** Observability 6.3 — No Prometheus metrics endpoint
**Severity:** Medium | **Effort:** Small

Prometheus is mentioned in the README but the required dependency is missing.

**Devin Prompt:**
> Add `io.micrometer:micrometer-registry-prometheus` to the `build.gradle` of all services. Configure actuator to expose the Prometheus endpoint: `management.endpoints.web.exposure.include=health,info,prometheus,metrics`. Optionally add a Prometheus container and a Grafana container to the Docker Compose file with a pre-configured datasource and basic dashboard for JVM metrics, HTTP request rates, and Feign client latencies.

---

### 3.5 Standardize URL Conventions

**Gap Reference:** API Design 5.3 — Inconsistent URL conventions
**Severity:** Low | **Effort:** Small

Mixed abbreviations and naming styles in API paths.

**Devin Prompt:**
> Standardize all API URL paths across services. Rename `util-account` to `utility-account` and `util-payment` to `utility-payment` in core-banking-service controllers and Feign client definitions. Ensure all path variables use consistent kebab-case naming: rename `{account_number}` to `{accountNumber}` and `{account_name}` to `{accountName}` (or use consistent snake_case — pick one and apply everywhere). Update Feign client paths in fund-transfer-service and utility-payment-service to match. Update any Postman collections accordingly.

---

### 3.6 Add Integration Tests with Testcontainers

**Gap Reference:** Testing 3.2 — No integration tests
**Severity:** High | **Effort:** Large

No real database or service integration testing exists.

**Devin Prompt:**
> Add integration tests to core-banking-service using Testcontainers. Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to the test dependencies. Create a base test class that starts a MySQL container and configures the Spring datasource. Write integration tests for: (1) Flyway migrations run successfully, (2) `AccountController` returns correct data via `@WebMvcTest`, (3) `TransactionService.fundTransfer()` persists correct data end-to-end. For the user-service, add a Keycloak testcontainer for testing the full registration flow. Target key happy paths and critical error scenarios.

---

### 3.7 Add Contract Tests Between Services

**Gap Reference:** Testing 3.3 — No contract tests
**Severity:** High | **Effort:** Large

Feign client contracts are only validated at runtime.

**Devin Prompt:**
> Add Spring Cloud Contract tests between the internet banking services and core-banking-service. In core-banking-service (the provider), add `spring-cloud-starter-contract-verifier` and define contracts for: `/api/v1/account/bank-account/{number}` (GET), `/api/v1/transaction/fund-transfer` (POST), `/api/v1/transaction/util-payment` (POST), `/api/v1/user/{identification}` (GET). Generate stubs. In fund-transfer-service and utility-payment-service (consumers), add `spring-cloud-starter-contract-stub-runner` and write tests that verify Feign clients work correctly against the generated stubs. This ensures API changes in core-banking are caught at build time.

---

### 3.8 Fix Keycloak Singleton Thread Safety

**Gap Reference:** Security 4.5 — Keycloak singleton anti-pattern
**Severity:** Medium | **Effort:** Small

The static Keycloak singleton is not thread-safe and bypasses Spring lifecycle management.

**Devin Prompt:**
> Refactor `KeycloakProperties` in internet-banking-user-service to use a proper Spring `@Bean` instead of a manually managed static singleton. Create a `KeycloakConfig` configuration class with a `@Bean Keycloak keycloak()` method that builds the instance using `KeycloakBuilder`. Remove the static field and `getInstance()` method. Inject `Keycloak` directly into `KeycloakManager`. This ensures thread-safe initialization, proper lifecycle management, and testability.

---

### 3.9 Set Up CI/CD Pipeline

**Gap Reference:** Build 6.4 — No CI/CD pipeline
**Severity:** Medium | **Effort:** Medium

No automated build, test, or deployment pipeline exists.

**Devin Prompt:**
> Create a GitHub Actions CI workflow at `.github/workflows/ci.yml` that: (1) runs on push to `main` and on pull requests, (2) sets up Java 21, (3) runs `./gradlew build` for each service in parallel using a matrix strategy, (4) runs tests with `./gradlew test`, (5) uploads test reports as artifacts, (6) optionally runs OWASP dependency check. Add a separate `docker-build.yml` workflow that builds Docker images for all services on tagged releases.

---

### 3.10 Add Test Coverage Reporting

**Gap Reference:** Testing 3.4 — No test coverage tooling
**Severity:** Medium | **Effort:** Small

No coverage measurement or enforcement.

**Devin Prompt:**
> Add JaCoCo to all service `build.gradle` files. Apply the `jacoco` plugin and configure `jacocoTestReport` to generate HTML and XML reports. Add `jacocoTestCoverageVerification` with a minimum line coverage threshold of 60% (increase to 80% after Phase 2 testing work). Configure the `test` task to generate coverage data. Add the JaCoCo reports directory to `.gitignore`.

---

## Summary Timeline

| Phase | Items | Estimated Scope |
|---|---|---|
| **Phase 1** — Quick Wins | 7 items | 1-2 weeks |
| **Phase 2** — Important | 8 items | 3-4 weeks |
| **Phase 3** — Polish | 10 items | 4-6 weeks |

### Recommended Execution Order Within Phases

**Phase 1 (priority order):**
1. Fix balance bug (1.1) — active financial defect
2. Fix error handling (1.3) — stops information leakage
3. Add input validation (1.2) — closes injection/abuse vectors
4. Externalize secrets (1.4) — security hygiene
5. Fix OpenAPI dependency (1.6) — unblocks Swagger UI
6. Add circuit breakers (1.5) — prevents cascade failures
7. Add ResponseEntity types (1.7) — improves API documentation

**Phase 2 (priority order):**
1. Fix balance race condition with locking (2.7)
2. Add idempotency keys (2.4)
3. Secure core banking service (2.2)
4. Add timeouts and retries (2.3)
5. Add unit tests (2.6)
6. Extract shared library (2.1)
7. Add rate limiting (2.5)
8. Add vulnerability scanning (2.8)

**Phase 3 (priority order):**
1. Structured logging (3.2) — fix password logging first
2. Health indicators (3.3)
3. Prometheus metrics (3.4)
4. Pagination metadata (3.1)
5. Integration tests (3.6)
6. Contract tests (3.7)
7. Keycloak singleton fix (3.8)
8. URL standardization (3.5)
9. CI/CD pipeline (3.9)
10. Coverage reporting (3.10)
