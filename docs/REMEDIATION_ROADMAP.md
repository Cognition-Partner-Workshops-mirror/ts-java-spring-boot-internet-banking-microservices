# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins**: High-severity items with small effort. Immediate security and correctness improvements.
- **Phase 2 — Important**: High-severity items with medium effort. Structural improvements for reliability and maintainability.
- **Phase 3 — Polish**: Lower-severity items and large-effort improvements. Long-term quality and operational maturity.

Each item includes a **sample Devin prompt** that can be used to execute the remediation.

---

## Phase 1 — Quick Wins

*High impact, small effort. Target: 1–2 weeks.*

### 1.1 Fix HTTP Status Codes in GlobalExceptionHandler (Gap 2.1, 2.2)

**Severity: High | Effort: Small**

Map exception types to appropriate HTTP status codes and stop leaking exception details to clients.

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all 4 business services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). Map `EntityNotFoundException` to HTTP 404, `InsufficientFundsException` to HTTP 422, `InvalidEmailException` / `UserAlreadyRegisteredException` / `InvalidBankingUserException` to HTTP 400, and the generic `Exception` catch-all to HTTP 500 with a sanitized error message (do not expose the raw exception). Ensure all handlers return a typed `ResponseEntity<ErrorResponse>`. Add the same `ErrorResponse` format for all error types. Run the existing tests to verify nothing breaks.

### 1.2 Add Bean Validation to Request DTOs (Gap 4.2)

**Severity: High | Effort: Small**

Add `jakarta.validation` annotations to all request DTOs and `@Valid` to controller methods.

**Devin Prompt:**
> Add `spring-boot-starter-validation` dependency to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service build.gradle files. Add `@NotNull`, `@NotBlank`, `@Positive`, and `@Size` annotations to all request DTO fields as appropriate: `FundTransferRequest` (fromAccount, toAccount not blank; amount positive), `UtilityPaymentRequest` (providerId not null; amount positive; account not blank), `User` registration (email not blank, identification not blank, password not blank). Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Run tests.

### 1.3 Stop Logging Passwords (Gap 4.5)

**Severity: High | Effort: Small**

Exclude the password field from the `User` DTO's `toString()` and prevent it from being logged.

**Devin Prompt:**
> In internet-banking-user-service, modify the `User` DTO class to exclude the `password` field from `toString()` by adding `@ToString.Exclude` on the `password` field (Lombok). Also add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to ensure the password is never included in JSON responses. Verify the UserController's log statement no longer includes the password. Run tests.

### 1.4 Externalize Hardcoded Credentials (Gap 4.1)

**Severity: Critical | Effort: Small**

Move all hardcoded credentials to environment variables.

**Devin Prompt:**
> Replace all hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml with environment variable references using `${VAR_NAME:-default}` syntax. Create a `.env.example` file documenting all required environment variables: `MYSQL_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `APP_DB_PASSWORD`. Add `.env` to `.gitignore`. Remove the test credentials from README.md and replace with a note to check `.env.example`. Remove the hardcoded Keycloak client secret from internet-banking-user-service/src/test/resources/application.yml and replace with a placeholder.

### 1.5 Add ResponseEntity Type Parameters (Gap 2.3)

**Severity: Medium | Effort: Small**

Add generic type parameters to all controller return types for compile-time safety and better OpenAPI docs.

**Devin Prompt:**
> Add explicit generic type parameters to all `ResponseEntity` return types in all controllers across all services. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do this for AccountController, TransactionController, UserController (core-banking), FundTransferController, UserController (user-service), and UtilityPaymentController. Run the build to verify compilation.

### 1.6 Configure Feign Client Timeouts (Gap 7.3)

**Severity: High | Effort: Small**

Add explicit connection and read timeouts to all Feign clients.

**Devin Prompt:**
> Add Feign client timeout configuration to the `application.yml` (or bootstrap config) of internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Set `spring.cloud.openfeign.client.config.default.connect-timeout` to 5000ms and `spring.cloud.openfeign.client.config.default.read-timeout` to 10000ms. Also set `spring.cloud.openfeign.client.config.core-banking-service.connect-timeout` and `read-timeout` with the same values as named overrides. Verify the configuration loads correctly.

### 1.7 Add Retry Policies for Feign Clients (Gap 7.2)

**Severity: High | Effort: Small**

Configure automatic retries for transient failures on Feign calls.

**Devin Prompt:**
> Add `spring-retry` and `spring-cloud-starter-circuitbreaker-resilience4j` dependencies to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Configure a Spring Retry `Retryer` bean in each service's Feign configuration that retries up to 3 times with 1-second intervals for `IOException` and `RetryableException`. Ensure POST (non-idempotent) endpoints are excluded from automatic retries by using a custom retry policy. Run builds to verify.

### 1.8 Fix Inconsistent Logging (Gap 6.1)

**Severity: Medium | Effort: Small**

Standardize log messages and fix typos.

**Devin Prompt:**
> Audit all log statements across all services. Fix the typo "utitlity" → "utility" in core-banking-service AccountController. Ensure every controller method has a consistent entry log at INFO level with the format: `log.info("{} - request: {}", methodDescription, sanitizedRequest)`. Add `logging.pattern.level=%5p [${spring.application.name},%X{traceId},%X{spanId}]` to each service's application.yml to include trace context in log output. This enables log correlation with Zipkin traces.

---

## Phase 2 — Important

*High impact, medium effort. Target: 3–6 weeks.*

### 2.1 Add Circuit Breakers to All Feign Clients (Gap 7.1)

**Severity: Critical | Effort: Medium**

Prevent cascade failures when Core Banking Service is unavailable.

**Devin Prompt:**
> Add Resilience4j circuit breaker support to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency. Enable circuit breakers on Feign clients with `spring.cloud.openfeign.circuitbreaker.enabled=true`. Create fallback classes for each Feign client: `BankingCoreFeignClientFallback`, `BankingCoreRestClientFallback` that return meaningful error responses (e.g., "Core Banking Service is temporarily unavailable"). Configure circuit breaker parameters: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`, `permittedNumberOfCallsInHalfOpenState=5`. Add unit tests for fallback behavior.

### 2.2 Extract Shared Library Module (Gap 1.1, 1.2)

**Severity: High | Effort: Medium**

Create a shared library for duplicated code and convert to a Gradle multi-project build.

**Devin Prompt:**
> Create a new module `internet-banking-common` as a shared library. Move the following duplicated classes into it under package `com.javatodev.finance.common`: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditConfig`, `AuditorAwareConfig`. Create a root `settings.gradle` that includes all 7 service modules plus the common module. Create a root `build.gradle` with shared plugin and dependency version declarations using a version catalog or `ext` block. Update each service's `build.gradle` to depend on `internet-banking-common` and remove the duplicated classes. Run all tests across all services.

### 2.3 Add Feign Error Decoder to All Services (Gap 2.4)

**Severity: High | Effort: Medium**

Ensure consistent error handling for inter-service Feign calls.

**Devin Prompt:**
> Port the `CustomFeignErrorDecoder` from internet-banking-user-service to the shared library (or if the shared library doesn't exist yet, copy it to internet-banking-fund-transfer-service and internet-banking-utility-payment-service). Register it in each service's `CustomFeignClientConfiguration`. The decoder should map: 400 → propagate the `SimpleBankingGlobalException` from the response body, 404 → `EntityNotFoundException`, 401/403 → `SecurityException`, 500+ → generic service unavailable error. Add unit tests for the decoder.

### 2.4 Secure Downstream Services (Gap 4.4)

**Severity: High | Effort: Medium**

Add authentication to downstream services so they cannot be accessed directly without valid credentials.

**Devin Prompt:**
> Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` dependencies to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Create a `SecurityConfig` class in each service that validates the JWT token from the `Authorization` header using the same Keycloak JWK Set URI. Configure the API Gateway to propagate the `Authorization` header (in addition to `X-Auth-Id`) using a `TokenRelay` filter. Permit actuator endpoints without auth. Update the Feign client configurations to forward the Bearer token using a `RequestInterceptor`. Update tests to work with the new security configuration.

### 2.5 Add Unit Tests for Fund Transfer and Utility Payment Services (Gap 3.1)

**Severity: Critical | Effort: Medium**

Write unit tests for the untested business services.

**Devin Prompt:**
> Write comprehensive unit tests for internet-banking-fund-transfer-service: create `FundTransferServiceTest` with tests for successful transfer, Feign client failure, and entity save failure. Create `FundTransferControllerTest` using `@WebMvcTest` with mocked service layer. Write comprehensive unit tests for internet-banking-utility-payment-service: create `UtilityPaymentServiceTest` with tests for successful payment, Feign failure, and validation scenarios. Create `UtilityPaymentControllerTest` using `@WebMvcTest`. Write unit tests for internet-banking-user-service: create `UserServiceTest` with tests for createUser (happy path, duplicate email, invalid email, core user not found), readUsers, readUser, and updateUser flows. Mock Keycloak and Feign clients. Target >80% line coverage for service classes.

### 2.6 Add Idempotency Keys to Write Endpoints (Gap 7.5)

**Severity: High | Effort: Medium**

Prevent duplicate transactions from client retries.

**Devin Prompt:**
> Add idempotency key support to fund transfer and utility payment endpoints. Accept an `X-Idempotency-Key` header on POST endpoints. Create an `idempotency_key` table in each service's database with columns: `key` (VARCHAR, UNIQUE), `response_body` (TEXT), `status_code` (INT), `created_at` (TIMESTAMP). Before processing a request, check if the idempotency key exists — if so, return the cached response. If not, process the request and store the response. Add a scheduled cleanup job to delete keys older than 24 hours. Add the idempotency key as a field on `FundTransferEntity` and `UtilityPaymentEntity` for traceability.

### 2.7 Handle Feign Failure Rollback in Orchestration Services (Gap 2.5)

**Severity: Critical | Effort: Medium**

Add compensation logic when Feign calls fail after local entity creation.

**Devin Prompt:**
> Refactor `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` to handle Feign call failures properly. Wrap the Feign call in a try-catch. On failure: update the local entity status to `FAILED`, log the error with the transaction reference, and throw a descriptive exception. Consider adding a `failureReason` column to `FundTransferEntity` and `UtilityPaymentEntity` to record why the transaction failed. Add a comment documenting that a full saga pattern should be implemented for production use. Add unit tests for the failure scenarios.

### 2.8 Add Pagination Metadata to List Responses (Gap 5.2)

**Severity: Medium | Effort: Small**

Return proper pagination information in list endpoints.

**Devin Prompt:**
> Create a generic `PagedResponse<T>` wrapper class in the shared library (or in each service) with fields: `content` (List<T>), `page` (int), `size` (int), `totalElements` (long), `totalPages` (int), `last` (boolean). Update all list endpoints in FundTransferController, UtilityPaymentController, UserController (user-service), and UserController (core-banking) to return `ResponseEntity<PagedResponse<T>>` instead of `ResponseEntity<List<T>>`. Update the service methods to return the `Page` object and construct the `PagedResponse` from it. Run tests.

---

## Phase 3 — Polish

*Lower severity or high effort. Target: 6–12 weeks.*

### 3.1 Add Integration Tests with Testcontainers (Gap 3.2)

**Severity: High | Effort: Large**

Create end-to-end integration tests for each service.

**Devin Prompt:**
> Add Testcontainers dependency to all business services. Create integration test classes using `@SpringBootTest` and `@Testcontainers` with MySQL and (for user-service) Keycloak containers. For core-banking-service: write integration tests that verify the full fund transfer flow through the REST API using `MockMvc`, including database state verification. For user-service: write integration tests that mock the Feign client but use a real Keycloak Testcontainer for user registration. For fund-transfer and utility-payment services: write integration tests that use WireMock to simulate the Core Banking Service responses. Create a shared `AbstractIntegrationTest` base class with common Testcontainers configuration.

### 3.2 Add Consumer-Driven Contract Tests (Gap 3.3)

**Severity: Medium | Effort: Large**

Add contract tests between services to catch breaking API changes.

**Devin Prompt:**
> Add Spring Cloud Contract to core-banking-service as the producer. Define contracts for all endpoints consumed by other services: `/api/v1/account/bank-account/{account_number}`, `/api/v1/transaction/fund-transfer`, `/api/v1/transaction/util-payment`, `/api/v1/user/{identification}`. Generate contract stubs. In internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service, add Spring Cloud Contract Stub Runner as a test dependency and write consumer-side tests that verify the Feign client interfaces match the contract stubs. Set up the contract verification to run as part of the CI pipeline.

### 3.3 Implement the Saga Pattern for Fund Transfers (Gap 7.6)

**Severity: Critical | Effort: Large**

Make cross-service transactions reliable with compensating transactions.

**Devin Prompt:**
> Implement a choreography-based saga pattern for the fund transfer flow. Add RabbitMQ dependency to fund-transfer-service and core-banking-service. Instead of a synchronous Feign call, fund-transfer-service publishes a `FundTransferRequested` event to a RabbitMQ exchange. Core-banking-service consumes this event, processes the transfer, and publishes either `FundTransferCompleted` or `FundTransferFailed`. Fund-transfer-service consumes the response events and updates the local entity status accordingly. Add a `saga_status` column to track the saga state. Implement a compensating transaction in core-banking-service that reverses the debit if the credit fails. Add comprehensive tests for all saga states including failure scenarios.

### 3.4 Add CI/CD Pipeline with GitHub Actions (Gap: no CI/CD)

**Severity: Medium | Effort: Medium**

Set up automated build, test, and quality checks.

**Devin Prompt:**
> Create a `.github/workflows/ci.yml` GitHub Actions workflow that: (1) triggers on push to main and all PRs, (2) sets up Java 21 with Temurin, (3) builds all 7 services using Gradle, (4) runs all unit tests, (5) runs integration tests (if Testcontainers tests exist), (6) uploads test reports as artifacts. Add a separate `.github/workflows/security.yml` that runs OWASP Dependency Check on all services weekly and on PRs. Add Gradle wrapper to the root of the repository. Add a build matrix to parallelize service builds.

### 3.5 Add Custom Health Indicators and Business Metrics (Gap 6.2, 6.3)

**Severity: Medium | Effort: Medium**

Improve operational visibility with custom health checks and Prometheus metrics.

**Devin Prompt:**
> Add custom health indicators to each business service: core-banking-service should check MySQL connectivity, user-service should check both MySQL and Keycloak reachability, fund-transfer and utility-payment services should check MySQL and Core Banking Service health (via a lightweight ping endpoint). Add `micrometer-registry-prometheus` dependency to all services. Create custom Micrometer metrics: `fund_transfer_total` (counter by status), `fund_transfer_amount_total` (counter), `utility_payment_total` (counter by status), `user_registration_total` (counter by status). Add a Prometheus scrape configuration to docker-compose.yml. Expose metrics at `/actuator/prometheus`.

### 3.6 Normalize REST URL Conventions (Gap 5.1)

**Severity: Medium | Effort: Small**

Standardize all API paths to use plural nouns.

**Devin Prompt:**
> Rename all API endpoint base paths to use consistent plural nouns: core-banking-service `/api/v1/accounts/`, `/api/v1/transactions/`, `/api/v1/users/`; user-service `/api/v1/users/`; fund-transfer-service `/api/v1/transfers/`; utility-payment-service `/api/v1/payments/`. Update the API Gateway route configuration to match the new paths. Update all Feign client interfaces to use the new paths. Update the Postman collection. Add redirect mappings from old paths to new paths for backward compatibility during migration. Run all tests.

### 3.7 Enhance OpenAPI Documentation (Gap 5.4)

**Severity: Low | Effort: Small**

Add comprehensive API documentation annotations.

**Devin Prompt:**
> Fix the OpenAPI dependency in all services: replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` (these are servlet-based services, not WebFlux). Add `@ApiResponse` annotations to all controller methods documenting success (200/201) and error (400/404/422/500) response shapes. Add `@Schema` annotations to all DTO fields with descriptions and examples. Add `@Parameter` annotations to path variables and query parameters. Configure OpenAPI info (title, version, description) in each service's application.yml using `springdoc.api-docs` properties.

### 3.8 Add Dependency Vulnerability Scanning (Gap 4.6)

**Severity: Medium | Effort: Small**

Automate detection of known vulnerabilities in dependencies.

**Devin Prompt:**
> Add the OWASP Dependency Check Gradle plugin (`org.owasp.dependencycheck`) to all services' build.gradle files. Configure it with `failBuildOnCVSS = 7` to fail builds on high-severity vulnerabilities. Also update the legacy `commons-lang` dependency (used by `StringUtils` in the filter classes) to `commons-lang3` (`org.apache.commons:commons-lang3`). Update all `StringUtils` imports from `org.apache.commons.lang.StringUtils` to `org.apache.commons.lang3.StringUtils`. Run the dependency check and fix any critical vulnerabilities found.

### 3.9 Add Domain-Specific Filtering to List Endpoints (Gap 5.5)

**Severity: Low | Effort: Medium**

Allow clients to filter and search list results.

**Devin Prompt:**
> Add query parameter filtering to list endpoints. For fund transfers (`GET /api/v1/transfers`): add filters for `status`, `fromAccount`, `toAccount`, `dateFrom`, `dateTo`. For utility payments (`GET /api/v1/payments`): add filters for `status`, `providerId`, `account`, `dateFrom`, `dateTo`. For users (`GET /api/v1/users` in both services): add filters for `status`, `email` (partial match). Use Spring Data JPA Specifications or Querydsl to implement dynamic filtering. Add the filter parameters to the OpenAPI documentation.

### 3.10 Fix Context Load Tests (Gap 3.4)

**Severity: Medium | Effort: Small**

Make context load tests pass without external infrastructure.

**Devin Prompt:**
> Fix the `*ApplicationTests.java` context load tests in all services. For fund-transfer, user, and utility-payment services: add `@MockBean` annotations for Feign client interfaces and any external dependencies. For user-service: also add `@MockBean` for `KeycloakManager` and `KeycloakProperties`. Ensure the test application.yml files have all required properties set (including placeholders for Keycloak config). Verify all context load tests pass with `./gradlew test` in each service directory.

---

## Priority Matrix

```
                    ┌─────────────────────────────────────┐
                    │           EFFORT                     │
                    │   Small      Medium       Large      │
         ┌──────────┼─────────────────────────────────────┤
         │ Critical │ 1.4          2.1, 2.7     3.3       │
Severity │ High     │ 1.1-1.3,    2.2-2.6      3.1, 3.2  │
         │          │ 1.5-1.8                              │
         │ Medium   │ 1.5, 2.8,   3.4, 3.5     3.9       │
         │          │ 3.6-3.8,                             │
         │          │ 3.10                                 │
         │ Low      │ 3.7         —             —         │
         └──────────┴─────────────────────────────────────┘
```

## Execution Summary

| Phase | Items | Estimated Effort | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 8 items | 1–2 weeks | Proper HTTP status codes, input validation, no leaked credentials/passwords, Feign timeouts/retries, consistent logging |
| **Phase 2** | 8 items | 3–6 weeks | Circuit breakers, shared library, comprehensive unit tests, secured services, idempotency, pagination |
| **Phase 3** | 10 items | 6–12 weeks | Integration/contract tests, saga pattern, CI/CD pipeline, metrics, OpenAPI docs, vulnerability scanning |
