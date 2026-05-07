# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are organized into three phases. Each item includes a severity tag, effort estimate, and a sample Devin prompt you can use to automate the remediation.

---

## Phase 1: Quick Wins (Critical fixes, Small/Medium effort)

These items address security vulnerabilities, data integrity bugs, and foundational issues that carry the highest risk with the least effort.

### 1.1 Fix Balance Calculation Bug
**Gap Ref:** 7.7 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted during fund transfers and utility payments in `TransactionService`.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is being set to `actualBalance - amount` after `actualBalance` has already been decremented, causing a double subtraction. The correct logic should set `availableBalance` equal to the new `actualBalance` (not subtract amount again). Apply the same fix for credit operations on the destination account. Add unit tests to verify the correct balance after transfers.

---

### 1.2 Fix Generic Exception Handler (Information Leakage + Wrong Status Codes)
**Gap Ref:** 2.1 | **Severity:** Critical | **Effort:** Small

All four `GlobalExceptionHandler` classes leak exception details and always return 400.

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all four services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). The generic `Exception` handler currently returns `400 Bad Request` with the full exception toString(). Change it to: (1) return `500 Internal Server Error`, (2) return a structured `ErrorResponse` JSON object with a generic message like "An internal error occurred" (no exception details), (3) log the full exception at ERROR level. Keep the `SimpleBankingGlobalException` handler unchanged. Also add a `404 Not Found` handler for `EntityNotFoundException` (currently falls through to the generic handler as a 400).

---

### 1.3 Add Input Validation to All Request DTOs
**Gap Ref:** 2.2, 4.2 | **Severity:** Critical | **Effort:** Small

No Bean Validation exists on any endpoint.

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across the codebase. Specifically: (1) Add `spring-boot-starter-validation` dependency to core-banking-service, fund-transfer-service, user-service, and utility-payment-service build.gradle files. (2) Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, and `@Size` annotations to: `FundTransferRequest` (fromAccount, toAccount not blank; amount positive), `UtilityPaymentRequest` (providerId not null; amount positive; account not blank), `User` (email valid email; identification not blank; password not blank with min length). (3) Add `@Valid` to all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns 400 with field-level error details.

---

### 1.4 Externalize Hardcoded Credentials
**Gap Ref:** 4.1 | **Severity:** Critical | **Effort:** Small

Database and Keycloak passwords are committed to the repository.

**Devin Prompt:**
> Externalize all hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml. Create a `.env.example` file with placeholder values and a `.env` file (added to .gitignore) with the actual development values. Replace hardcoded values with `${VARIABLE}` references: `MYSQL_ROOT_PASSWORD`, `KC_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `MYSQL_APP_PASSWORD`. Update docker-compose/mysql/privileges.sql to use a variable or document that the password must be changed. Add `.env` to .gitignore.

---

### 1.5 Prevent Password Logging
**Gap Ref:** 4.7 | **Severity:** High | **Effort:** Small

Passwords appear in logs via Lombok's `@Data` toString().

**Devin Prompt:**
> Fix password logging exposure in internet-banking-user-service. The `User` DTO uses Lombok `@Data` which generates a `toString()` that includes the `password` field. In `UserController.createUser()`, this is logged via `log.info("Creating user with {}", request.toString())`. Fix by: (1) Adding `@ToString.Exclude` to the `password` field in the User DTO, or (2) replacing `@Data` with `@Getter @Setter` and writing a custom `toString()` that excludes password. Also review all other log statements across services to ensure no sensitive data is logged.

---

### 1.6 Fix Keycloak Singleton Thread Safety
**Gap Ref:** 4.4 | **Severity:** Medium | **Effort:** Small

The Keycloak client singleton in `KeycloakProperties` is not thread-safe.

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. The `getInstance()` method uses an unsynchronized check-then-act pattern on a static field. Replace this with either: (1) a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance as a Spring-managed singleton, or (2) use `synchronized` or `volatile` + double-checked locking. Option 1 is preferred as it follows Spring conventions.

---

### 1.7 Add Feign Client Timeouts
**Gap Ref:** 7.3 | **Severity:** High | **Effort:** Small

No timeouts configured on Feign clients — threads can block indefinitely.

**Devin Prompt:**
> Add Feign client timeout configuration to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. In each service's `application.yml` (or via Spring Cloud Config), add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add service-specific overrides if needed: `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 15000` for fund-transfer and utility-payment services since those involve financial transactions.

---

### 1.8 Add Retry Policies for Feign Clients
**Gap Ref:** 7.2 | **Severity:** High | **Effort:** Small

No retry configuration for transient failures.

**Devin Prompt:**
> Add Spring Retry support for Feign clients in fund-transfer-service, utility-payment-service, and user-service. Add `spring-retry` and `spring-boot-starter-aop` dependencies to each service's build.gradle. Configure retries for GET operations only (to avoid duplicating POST side effects): add `@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))` to the Feign client methods that do reads (e.g., `readAccount`, `readUser`). Do NOT add retry to fund transfer or payment POST calls since those are not idempotent.

---

## Phase 2: Important Improvements (High/Medium severity, Medium effort)

These items address architectural gaps, test coverage, and resilience patterns that are important for production readiness.

### 2.1 Add Circuit Breakers with Resilience4j
**Gap Ref:** 7.1, 7.4 | **Severity:** Critical | **Effort:** Medium

No circuit breakers or fallback behavior for inter-service calls.

**Devin Prompt:**
> Add Resilience4j circuit breaker support to all Feign client calls. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to fund-transfer-service, utility-payment-service, and user-service. (2) Enable circuit breaker for Feign: set `spring.cloud.openfeign.circuitbreaker.enabled: true` in each service's config. (3) Create fallback factory classes for each Feign client (e.g., `BankingCoreFeignClientFallbackFactory`) that return appropriate error responses when the circuit is open. (4) Configure circuit breaker parameters: `failureRateThreshold: 50`, `waitDurationInOpenState: 30s`, `slidingWindowSize: 10`. (5) Add the fallback factory to each `@FeignClient` annotation.

---

### 2.2 Create Shared Common Library
**Gap Ref:** 1.1, 1.2 | **Severity:** High | **Effort:** Medium

Exception classes, mappers, audit base classes, and auth filters are duplicated.

**Devin Prompt:**
> Create a shared `internet-banking-common` Gradle module. (1) Create a new directory `internet-banking-common/` with its own `build.gradle` and `settings.gradle`. (2) Move these duplicated classes into the common module under package `com.javatodev.finance.common`: `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ErrorResponse`, `EntityNotFoundException`, `GlobalErrorCode`, `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`. (3) Create a root `settings.gradle` that includes all 7 modules. (4) Add `implementation project(':internet-banking-common')` to each service's `build.gradle`. (5) Remove the duplicated classes from each service and update imports.

---

### 2.3 Add Unit Tests for User Service, Fund Transfer Service, and Utility Payment Service
**Gap Ref:** 3.1 | **Severity:** Critical | **Effort:** Large

Only core-banking-service has meaningful tests.

**Devin Prompt:**
> Add comprehensive unit tests for the three business services that currently have none. For each service, create service-layer tests using Mockito: (1) `internet-banking-user-service`: Test `UserService.createUser()` (happy path, duplicate email, user not found in core banking, email mismatch), `updateUser()` (approve flow, entity not found), `readUser()`, `readUsers()`. Mock `KeycloakUserService`, `UserRepository`, and `BankingCoreRestClient`. (2) `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()` (happy path, core banking failure), `readAllTransfers()`. Mock `FundTransferRepository` and `BankingCoreFeignClient`. (3) `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()` (happy path, core banking failure), `readPayments()`. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`. Use H2 test configs already present.

---

### 2.4 Add Controller Layer Tests
**Gap Ref:** 3.4 | **Severity:** Medium | **Effort:** Medium

No `@WebMvcTest` tests for any controller.

**Devin Prompt:**
> Add `@WebMvcTest` controller tests for all business services. For each service, create controller tests that verify: (1) Correct HTTP status codes for success and error cases. (2) Request/response JSON serialization. (3) Path mappings and parameter binding. (4) Pagination parameter handling. Create tests for: `AccountController`, `TransactionController`, `UserController` (core-banking), `UserController` (user-service), `FundTransferController`, `UtilityPaymentController`. Use `@MockBean` to mock service dependencies and `MockMvc` for HTTP assertions.

---

### 2.5 Fix Pagination to Return Page Metadata
**Gap Ref:** 5.3 | **Severity:** Medium | **Effort:** Small

Paginated endpoints discard page metadata.

**Devin Prompt:**
> Fix all paginated endpoints to return `Page<T>` instead of `List<T>`. In each controller and service method that accepts a `Pageable` parameter, change the return type from `List<T>` to `Page<T>` and return the full `Page` object from the repository. This provides clients with `totalElements`, `totalPages`, `number`, `size`, and `last` metadata. Update the following: `UserController.readUsers()` and `UserService.readUsers()` in both core-banking and user-service; `FundTransferController.readFundTransfers()` and `FundTransferService.readAllTransfers()`; `UtilityPaymentController.readPayments()` and `UtilityPaymentService.readPayments()`.

---

### 2.6 Add Fund Transfer Idempotency
**Gap Ref:** 7.6 | **Severity:** High | **Effort:** Medium

Fund transfer endpoint is not idempotent — retries can cause duplicate transfers.

**Devin Prompt:**
> Add idempotency support to the fund transfer endpoint. (1) Add an `idempotencyKey` field (String, UUID) to `FundTransferRequest`. (2) Add a unique index on `idempotency_key` column in the `fund_transfer` table. (3) In `FundTransferService.fundTransfer()`, before processing, check if a transfer with the same idempotency key already exists. If found with status SUCCESS, return the existing response. If found with status PENDING, either wait or return a conflict. (4) Apply the same pattern to utility payments. This prevents duplicate financial transactions from client retries.

---

### 2.7 Clean Up REST API Conventions
**Gap Ref:** 5.2 | **Severity:** Medium | **Effort:** Small

Redundant path segments and inconsistent naming.

**Devin Prompt:**
> Clean up REST API endpoint naming for consistency across all services. Make the following changes: (1) User Service: Change `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users` (POST to collection implies creation). (2) User Service: Change `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}` (PATCH already implies update). (3) Core Banking: Rename `/api/v1/account/util-account/` to `/api/v1/account/utility-account/` for consistency with `utility-payment`. (4) Update all Feign client mappings that reference these endpoints. (5) Update the API Gateway routing rules if needed. Note: This is a breaking API change — document the migration in a CHANGELOG.

---

### 2.8 Add Structured Logging
**Gap Ref:** 6.1 | **Severity:** Medium | **Effort:** Small

Text-format logs make aggregation difficult.

**Devin Prompt:**
> Add structured JSON logging to all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service. (2) Create a shared `logback-spring.xml` configuration that outputs JSON format in non-local profiles and human-readable format in local/dev profiles. (3) Include traceId and spanId from Micrometer in log output. (4) Review and sanitize all existing log statements to remove sensitive data (passwords, tokens). Replace `request.toString()` calls with explicit field logging.

---

### 2.9 Add Rate Limiting to API Gateway
**Gap Ref:** 4.6 | **Severity:** Medium | **Effort:** Medium

No rate limiting on financial endpoints.

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency to the API Gateway. (2) Add a Redis container to docker-compose.yml. (3) Configure rate limiting in the gateway routes: apply a `RequestRateLimiter` filter with `redis-rate-limiter.replenishRate: 10` and `redis-rate-limiter.burstCapacity: 20` for general endpoints, and stricter limits (`replenishRate: 2`, `burstCapacity: 5`) for fund transfer and utility payment routes. (4) Configure the key resolver to rate-limit by authenticated user principal.

---

### 2.10 Restrict Database Permissions
**Gap Ref:** 4.5 | **Severity:** Medium | **Effort:** Small

Application database user has overly broad privileges.

**Devin Prompt:**
> Update `docker-compose/mysql/privileges.sql` to follow least-privilege principles. Create separate database users per service, each with access only to their own database: (1) `core_banking_user` with `SELECT, INSERT, UPDATE, DELETE` on `banking_core_service.*` (2) `user_service_user` with the same on `banking_core_user_service.*` (3) `fund_transfer_user` on `banking_core_fund_transfer_service.*` (4) `utility_payment_user` on `banking_core_utility_payment_service.*`. Remove the global `javatodev_development` user. Update Spring Cloud Config properties for each service's datasource credentials accordingly.

---

## Phase 3: Polish (Medium/Low severity, improves production readiness)

### 3.1 Add Integration Tests with Testcontainers
**Gap Ref:** 3.2 | **Severity:** High | **Effort:** Large

No integration tests exist.

**Devin Prompt:**
> Add Testcontainers-based integration tests for the core-banking-service and user-service. (1) Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies. (2) Create an `@SpringBootTest` test class for core-banking-service that starts a MySQL Testcontainer, applies Flyway migrations, and tests the full fund transfer flow (create accounts, transfer funds, verify balances). (3) For user-service, add `org.testcontainers:keycloak` and create an integration test that tests user registration end-to-end with a real Keycloak instance. (4) Verify that Feign calls between services work correctly using `@SpringBootTest(webEnvironment = RANDOM_PORT)`.

---

### 3.2 Add Consumer-Driven Contract Tests
**Gap Ref:** 3.3 | **Severity:** High | **Effort:** Large

No contract tests between services.

**Devin Prompt:**
> Add Spring Cloud Contract tests between the three consumer services and core-banking-service. (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service (provider). (2) Write contract DSL files in `src/test/resources/contracts/` for: fund transfer endpoint, utility payment endpoint, account lookup endpoint, and user lookup endpoint. (3) Add `spring-cloud-starter-contract-stub-runner` to fund-transfer-service, utility-payment-service, and user-service (consumers). (4) Write consumer-side contract tests that verify Feign clients work correctly against the generated stubs. This ensures API changes in core-banking-service are caught before deployment.

---

### 3.3 Add Custom Health Indicators
**Gap Ref:** 6.2 | **Severity:** Low | **Effort:** Small

Only default health checks are configured.

**Devin Prompt:**
> Add custom Spring Boot health indicators to each business service. (1) In user-service: add a `KeycloakHealthIndicator` that pings the Keycloak server and reports UP/DOWN. (2) In fund-transfer-service and utility-payment-service: add a `CoreBankingHealthIndicator` that calls a lightweight endpoint on core-banking-service (e.g., actuator health). (3) In core-banking-service: add a `DatabaseHealthIndicator` that runs a simple query. (4) Ensure all health indicators are registered and visible at `/actuator/health`.

---

### 3.4 Add Custom Business Metrics
**Gap Ref:** 6.3 | **Severity:** Medium | **Effort:** Medium

No business metrics beyond defaults.

**Devin Prompt:**
> Add custom Micrometer metrics to track key business operations. (1) In core-banking-service `TransactionService`: add counters for `banking.fund_transfer.total` (tagged by status: success/failure) and `banking.utility_payment.total`. Add a timer for `banking.fund_transfer.duration`. (2) In fund-transfer-service: add a counter for `fund_transfer.requests` and a gauge for `fund_transfer.pending.count`. (3) In utility-payment-service: add similar counters. (4) Ensure all metrics are exposed via the `/actuator/prometheus` endpoint. (5) Create a sample Grafana dashboard JSON that visualizes these metrics.

---

### 3.5 Add Prometheus and Grafana to Docker Compose
**Gap Ref:** 6.5 | **Severity:** Medium | **Effort:** Medium

No monitoring stack configured despite Prometheus being in the tech stack.

**Devin Prompt:**
> Add Prometheus and Grafana containers to `docker-compose/docker-compose.yml`. (1) Add a Prometheus container with a `prometheus.yml` config that scrapes all service actuator endpoints at `/actuator/prometheus`. (2) Add a Grafana container with a pre-configured Prometheus data source. (3) Create a `docker-compose/grafana/` directory with provisioning files for the data source and a default dashboard. (4) Expose Prometheus on port 9090 and Grafana on port 3000. (5) Ensure all services expose the Prometheus endpoint by adding `management.endpoints.web.exposure.include: health,info,prometheus` to their configs.

---

### 3.6 Add OpenAPI Documentation Enhancements
**Gap Ref:** 5.5 | **Severity:** Low | **Effort:** Small

Generated API docs lack response schemas due to raw `ResponseEntity`.

**Devin Prompt:**
> Improve OpenAPI documentation across all services. (1) Fix the springdoc dependency: replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in core-banking-service, user-service, fund-transfer-service, and utility-payment-service (these are Spring MVC, not WebFlux). (2) Add generic type parameters to all `ResponseEntity` return types (e.g., `ResponseEntity<BankAccount>` instead of raw `ResponseEntity`). (3) Add `@ApiResponse` annotations for error cases (400, 404, 500) on each endpoint. (4) Add a global OpenAPI configuration class in each service with API title, description, and version.

---

### 3.7 Add Bulkhead Isolation
**Gap Ref:** 7.5 | **Severity:** Medium | **Effort:** Medium

All Feign calls share the same thread pool.

**Devin Prompt:**
> Add Resilience4j bulkhead isolation to Feign clients. (1) Configure thread-pool bulkheads in each consuming service's config: `resilience4j.thread-pool-bulkhead.instances.coreBankingService.maxThreadPoolSize: 10`, `coreQueueCapacity: 20`. (2) Assign each Feign client to its own bulkhead instance. This prevents a slow core-banking response from exhausting all threads and blocking other operations. (3) Add bulkhead metrics to the Prometheus endpoint.

---

### 3.8 Set Up CI/CD Pipeline
**Gap Ref:** 6.4 (Build/Deploy) | **Severity:** Medium | **Effort:** Medium

No CI/CD pipeline exists in the repository.

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline for this project. (1) Create `.github/workflows/ci.yml` that: builds all 6 services with Gradle, runs all unit tests, runs integration tests, and reports test results. (2) Use a matrix strategy to build services in parallel. (3) Add a MySQL service container for integration tests. (4) Add a Docker build step that builds and tags images for each service. (5) Add branch protection rules documentation recommending: require CI to pass before merge, require at least one review.

---

### 3.9 Add API Versioning Infrastructure
**Gap Ref:** 5.1 | **Severity:** Medium | **Effort:** Medium

No infrastructure for multi-version API support.

**Devin Prompt:**
> Add API versioning infrastructure to support future breaking changes. (1) Create a versioning strategy document in `docs/API_VERSIONING.md` that defines the URL-based approach (already using `/api/v1/`). (2) Add a `@ApiVersion` custom annotation and a `WebMvcConfigurer` that supports routing to versioned controllers. (3) Create example v2 controller stubs alongside existing v1 controllers. (4) Configure the API Gateway to route version-specific paths. (5) Add deprecation headers (`Sunset`, `Deprecation`) support for when v1 endpoints are eventually deprecated.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| Phase 1 (Quick Wins) | 8 | 4 | 3 | 1 | 0 |
| Phase 2 (Important) | 10 | 1 | 2 | 7 | 0 |
| Phase 3 (Polish) | 9 | 0 | 2 | 5 | 2 |
| **Total** | **27** | **5** | **7** | **13** | **2** |

### Recommended Execution Order within Phase 1
1. **1.1** Fix Balance Bug (data integrity, immediate risk)
2. **1.2** Fix Exception Handler (information leakage)
3. **1.3** Add Input Validation (injection defense)
4. **1.4** Externalize Credentials (security hygiene)
5. **1.5** Prevent Password Logging (compliance)
6. **1.6** Fix Keycloak Thread Safety (reliability)
7. **1.7** Add Feign Timeouts (resilience)
8. **1.8** Add Retry Policies (resilience)
