# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on risk, impact, and effort.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact, low-effort items that address critical security and reliability risks.

### 1.1 Fix Transaction Balance Bug (GAP-RES-06)
**Severity:** Critical | **Effort:** Small

Fix the double-subtraction bug in `TransactionService.internalFundTransfer()` and `utilPayment()` where `availableBalance` is incorrectly calculated.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In `internalFundTransfer()`, after setting `actualBalance = actualBalance - amount`, `availableBalance` is set to `actualBalance - amount` again (double subtraction). The correct logic should set `availableBalance = actualBalance` (they should be equal after the transfer). Apply the same fix to `utilPayment()`. Write unit tests to verify correct balance updates for both methods.

---

### 1.2 Add Input Validation (GAP-SEC-02)
**Severity:** Critical | **Effort:** Small

Add Bean Validation annotations to all request DTOs across services.

**Devin Prompt:**
> Add Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size`) to all request DTOs in the project: `FundTransferRequest` (fund-transfer-service), `UtilityPaymentRequest` (utility-payment-service), and `User` (user-service registration). Add `@Valid` annotations to controller method parameters. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 400 with field-level error details.

---

### 1.3 Fix Password Exposure in API Response (GAP-SEC-03)
**Severity:** Critical | **Effort:** Small

Prevent password from being serialized in User Service responses.

**Devin Prompt:**
> In `internet-banking-user-service`, fix the password exposure issue. Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field in `com.javatodev.finance.model.dto.User`. Also exclude `password` from Lombok's `@ToString` by adding `@ToString(exclude = "password")` to prevent logging passwords. Verify no other sensitive fields are exposed in API responses.

---

### 1.4 Fix Error Handler HTTP Status Codes (GAP-ERR-01, GAP-ERR-02)
**Severity:** High | **Effort:** Small

Return appropriate HTTP status codes and hide internal error details.

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all services (core-banking, user-service, fund-transfer, utility-payment). Changes: (1) `EntityNotFoundException` -> return 404 Not Found. (2) `InsufficientFundsException` -> return 422 Unprocessable Entity. (3) `InvalidEmailException`, `InvalidBankingUserException`, `UserAlreadyRegisteredException` -> return 409 Conflict. (4) Generic `Exception.class` handler -> return 500 Internal Server Error with a generic message "An unexpected error occurred" (do NOT expose exception details). (5) Ensure all error responses use the consistent `ErrorResponse{code, message}` format.

---

### 1.5 Remove Sensitive Data from Logs (GAP-OBS-02)
**Severity:** High | **Effort:** Small

Prevent passwords and sensitive financial data from being logged.

**Devin Prompt:**
> Audit all `log.info()` and `log.debug()` calls across the codebase that log request objects via `.toString()`. In `internet-banking-user-service/UserController.java`, remove the user request logging or log only non-sensitive fields (email, identification). In fund-transfer and utility-payment controllers, log only account numbers without full request bodies. Add `@ToString(exclude = "password")` to any DTO that contains a password field.

---

### 1.6 Add Timeout Configuration (GAP-RES-03)
**Severity:** High | **Effort:** Small

Configure explicit timeouts for all Feign clients.

**Devin Prompt:**
> Add Feign client timeout configuration to the `application.yml` (or add a `FeignClientConfiguration` bean) in `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Set connection timeout to 5 seconds and read timeout to 10 seconds. Configuration should be: `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`.

---

### 1.7 Add Retry Policies (GAP-RES-02)
**Severity:** High | **Effort:** Small

Add retry configuration for transient Feign client failures.

**Devin Prompt:**
> Add Spring Retry support to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add `spring-retry` and `spring-boot-starter-aop` dependencies. Configure Feign retry with max 3 attempts, 1-second initial backoff, exponential multiplier of 2, for connection exceptions and 5xx responses only (not 4xx). Ensure retries are only applied to idempotent operations or add idempotency checks.

---

### 1.8 Externalize Credentials from Docker Compose (GAP-SEC-01)
**Severity:** Critical | **Effort:** Small

Move hardcoded credentials to environment variables.

**Devin Prompt:**
> Refactor `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to use environment variable references instead of hardcoded credentials. Replace: `MYSQL_ROOT_PASSWORD: woVERANKliGharym` with `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`, `KEYCLOAK_ADMIN_PASSWORD: password` with `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}`, and similar for all passwords. Create a `.env.example` file with placeholder values and add `.env` to `.gitignore`. Also update `docker-compose/mysql/privileges.sql` to use an environment variable for the user password.

---

## Phase 2: Important Improvements (3-6 Weeks)

Structural improvements that significantly improve reliability, maintainability, and developer experience.

### 2.1 Add Circuit Breakers (GAP-RES-01)
**Severity:** Critical | **Effort:** Medium

Implement circuit breaker pattern for all inter-service calls.

**Devin Prompt:**
> Add Resilience4j circuit breaker to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency. Wrap the Feign client calls in `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` with circuit breakers. Configure: failure rate threshold 50%, wait duration in open state 30s, sliding window size 10. Add fallback methods that update the entity status to `FAILED` and return an appropriate error response. Add circuit breaker health indicator to actuator.

---

### 2.2 Add Feign Error Handling (GAP-ERR-03)
**Severity:** High | **Effort:** Medium

Implement proper error handling for Feign client failures.

**Devin Prompt:**
> Add a `CustomFeignErrorDecoder` (similar to the one in user-service) to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. The decoder should: (1) Parse the error response body into `ErrorResponse`. (2) Throw appropriate exceptions based on HTTP status (404 -> EntityNotFoundException, 422 -> InsufficientFundsException, 5xx -> ServiceUnavailableException). (3) Wrap the Feign calls in try-catch in `FundTransferService` and `UtilityPaymentService` to update entity status to `FAILED` on any exception. Register the error decoder in `CustomFeignClientConfiguration`.

---

### 2.3 Add Idempotency Controls (GAP-RES-05)
**Severity:** High | **Effort:** Medium

Prevent duplicate transaction processing.

**Devin Prompt:**
> Implement idempotency for fund transfer and utility payment endpoints. (1) Add an optional `idempotencyKey` field to `FundTransferRequest` and `UtilityPaymentRequest`. (2) Before processing, check if a record with the same idempotency key already exists in the database. (3) If found, return the existing response instead of processing again. (4) Add a unique constraint on the idempotency key column. (5) If no key is provided, generate one server-side (UUID) to prevent duplicate submissions from retries.

---

### 2.4 Add Unit Test Coverage (GAP-TEST-01)
**Severity:** Critical | **Effort:** Large

Add comprehensive unit tests for all service layers.

**Devin Prompt:**
> Add unit tests for `core-banking-service`. Create test classes: (1) `TransactionServiceTest` - test `fundTransfer()` with valid accounts, insufficient funds, non-existent accounts. (2) `AccountServiceTest` - test `readBankAccount()` with valid/invalid account numbers. (3) `UserServiceTest` - test `readUser()` with valid/invalid identification. (4) `TransactionControllerTest` using MockMvc - test fund transfer and utility payment endpoints with valid/invalid payloads. Use Mockito for mocking repositories. Achieve at minimum 80% line coverage on service classes.

**Additional Devin Prompts for other services:**
> Add unit tests for `internet-banking-fund-transfer-service`. Test `FundTransferService.fundTransfer()`: mock `BankingCoreFeignClient`, verify entity is saved with PENDING then updated to SUCCESS, test failure scenario where Feign throws exception. Test `FundTransferController` with MockMvc for valid/invalid requests.

> Add unit tests for `internet-banking-user-service`. Test `UserService.createUser()`: mock KeycloakUserService and BankingCoreRestClient, test happy path, duplicate email, invalid identification, email mismatch. Test `updateUser()` with APPROVED status enabling Keycloak account.

---

### 2.5 Create Shared Library Module (GAP-ORG-02)
**Severity:** Medium | **Effort:** Medium

Extract common code into a shared module.

**Devin Prompt:**
> Create a Gradle multi-project build for this repository. (1) Create a root `settings.gradle` that includes all service subprojects. (2) Create a root `build.gradle` with shared plugin versions and dependency management. (3) Create a `shared-library` module containing: `AuditAware`, `BaseMapper`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`. (4) Update each service's `build.gradle` to depend on the shared library. (5) Remove duplicated classes from each service.

---

### 2.6 Add Structured Logging (GAP-OBS-01)
**Severity:** Medium | **Effort:** Small

Configure JSON-formatted structured logging with correlation IDs.

**Devin Prompt:**
> Add structured JSON logging to all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency. (2) Create a shared `logback-spring.xml` configuration that outputs JSON in production profile and plain text in dev profile. (3) Include trace ID, span ID, service name, and request ID in every log line via MDC. (4) Configure log levels: INFO for application code, WARN for framework code, ERROR for exceptions.

---

### 2.7 Fix Pagination Responses (GAP-API-03)
**Severity:** Medium | **Effort:** Small

Return proper pagination metadata in list endpoints.

**Devin Prompt:**
> Refactor all paginated endpoints to return pagination metadata. Create a generic `PageResponse<T>` wrapper class in the shared library with fields: `content` (List<T>), `totalElements` (long), `totalPages` (int), `currentPage` (int), `pageSize` (int). Update all controller GET (list) endpoints in fund-transfer, utility-payment, user-service, and core-banking to return `ResponseEntity<PageResponse<T>>` instead of `ResponseEntity<List<T>>`.

---

### 2.8 Add Type Parameters to ResponseEntity (GAP-API-01)
**Severity:** Medium | **Effort:** Small

Add proper generic type parameters to all controller return types.

**Devin Prompt:**
> Add explicit type parameters to all `ResponseEntity` return types in controllers across all services. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. This enables proper OpenAPI schema generation. Fix all raw-type warnings in: `AccountController`, `TransactionController`, `UserController` (core-banking), `FundTransferController`, `UtilityPaymentController`.

---

### 2.9 Add Rate Limiting (GAP-SEC-06)
**Severity:** Medium | **Effort:** Medium

Implement rate limiting at the API Gateway.

**Devin Prompt:**
> Add rate limiting to `internet-banking-api-gateway` using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency. (2) Add Redis to docker-compose. (3) Configure rate limiting on the user registration route: 10 requests per minute per IP. (4) Configure general rate limiting: 100 requests per minute per authenticated user. (5) Return HTTP 429 with a `Retry-After` header when rate limit is exceeded.

---

### 2.10 Add Custom Health Indicators (GAP-OBS-03, GAP-OBS-04)
**Severity:** Medium | **Effort:** Small

Add business-specific health checks and metrics.

**Devin Prompt:**
> Add custom health indicators and metrics to the services. (1) In core-banking-service, add a health indicator that checks database connectivity. (2) In user-service, add a health indicator that checks Keycloak connectivity. (3) In fund-transfer and utility-payment services, add health indicators that check core-banking-service availability via a lightweight ping endpoint. (4) Add Prometheus metrics endpoint by adding `micrometer-registry-prometheus` dependency. (5) Add custom counters for: `fund_transfers_total`, `utility_payments_total`, `fund_transfers_failed_total`.

---

## Phase 3: Polish & Excellence (6-12 Weeks)

Items that improve developer experience, long-term maintainability, and operational excellence.

### 3.1 Add Integration Tests with Testcontainers (GAP-TEST-02)
**Severity:** High | **Effort:** Large

Create integration tests using Testcontainers for realistic testing.

**Devin Prompt:**
> Add integration tests using Testcontainers to `core-banking-service`. (1) Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies. (2) Create an `@IntegrationTest` base class that starts a MySQL container and configures Spring datasource. (3) Write integration tests for the full fund transfer flow: create users, create accounts, perform transfer, verify balances. (4) Write integration tests for utility payment flow. (5) Verify Flyway migrations run correctly against real MySQL.

---

### 3.2 Add Contract Tests (GAP-TEST-03)
**Severity:** Medium | **Effort:** Large

Implement consumer-driven contract tests between services.

**Devin Prompt:**
> Add Spring Cloud Contract tests between services. (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service (provider). (2) Define contracts for: GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}. (3) Add `spring-cloud-starter-contract-stub-runner` to consumer services (fund-transfer, utility-payment, user-service). (4) Generate stubs from core-banking contracts and use them in consumer integration tests.

---

### 3.3 Add CI/CD Pipeline (No existing gap - net new)
**Severity:** High | **Effort:** Medium

Create automated build and test pipeline.

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline for this repository. Create `.github/workflows/ci.yml` with: (1) Trigger on push to main and pull requests. (2) Set up Java 21 with Gradle caching. (3) Run `./gradlew build` for all services. (4) Run unit tests with coverage report. (5) Run integration tests (with Testcontainers). (6) Upload test results and coverage reports as artifacts. (7) Build Docker images for each service. (8) Push images to GitHub Container Registry on main branch merges.

---

### 3.4 Standardize Package Structure (GAP-ORG-03)
**Severity:** Low | **Effort:** Medium

Align all services to a consistent package layout.

**Devin Prompt:**
> Standardize the package structure across all business services (fund-transfer, utility-payment, user-service) to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.dto.request`, `com.javatodev.finance.model.dto.response`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.configuration`, `com.javatodev.finance.exception`, `com.javatodev.finance.client` (for Feign clients). Move classes to match this structure.

---

### 3.5 Fix OpenAPI Configuration (GAP-API-04)
**Severity:** Low | **Effort:** Small

Correct Swagger/OpenAPI dependencies and add proper metadata.

**Devin Prompt:**
> Fix the OpenAPI/Swagger configuration in all non-gateway services. (1) Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` (these services use Spring MVC, not WebFlux). (2) Add an `OpenApiConfig` class with `@OpenAPIDefinition` including title, version, description, and contact info. (3) Add `@SecurityScheme` annotation for Bearer JWT authentication. (4) Add response schema annotations (`@ApiResponse`) to all controller methods. (5) Verify Swagger UI is accessible at `/swagger-ui.html` for each service.

---

### 3.6 Add Database Connection Pool Tuning (GAP-RES-07)
**Severity:** Medium | **Effort:** Small

Configure HikariCP connection pooling for production readiness.

**Devin Prompt:**
> Add explicit HikariCP connection pool configuration to all database-backed services (core-banking, fund-transfer, utility-payment, user-service). Add to each service's application configuration: `spring.datasource.hikari.maximum-pool-size=10`, `spring.datasource.hikari.minimum-idle=5`, `spring.datasource.hikari.connection-timeout=30000`, `spring.datasource.hikari.idle-timeout=600000`, `spring.datasource.hikari.max-lifetime=1800000`. Add different values for the docker profile (larger pool sizes for production).

---

### 3.7 Add Fallback Behavior (GAP-RES-04)
**Severity:** Medium | **Effort:** Medium

Implement graceful degradation for downstream failures.

**Devin Prompt:**
> Add fallback behavior when core-banking-service is unavailable. (1) In fund-transfer-service, implement a Resilience4j fallback that saves the transfer with `QUEUED` status and returns a response indicating the transfer is queued for processing. (2) Add a scheduled job that retries `QUEUED` transfers periodically. (3) In utility-payment-service, implement similar queuing behavior. (4) In user-service, if core-banking is down during user lookup, return a clear error message without crashing.

---

### 3.8 Add API Filtering Capabilities (GAP-API-05)
**Severity:** Low | **Effort:** Medium

Add query filtering to list endpoints.

**Devin Prompt:**
> Add filtering support to list endpoints. (1) In fund-transfer-service GET `/api/v1/transfer`, add optional query params: `status`, `fromAccount`, `toAccount`, `dateFrom`, `dateTo`. (2) In utility-payment-service GET `/api/v1/utility-payment`, add: `status`, `providerId`, `account`, `dateFrom`, `dateTo`. (3) In user-service GET `/api/v1/bank-users`, add: `status`, `email`. (4) Use Spring Data JPA Specifications or QueryDSL for dynamic query construction.

---

### 3.9 Separate DTO and Entity Layers (GAP-ORG-04)
**Severity:** Low | **Effort:** Medium

Properly separate persistence entities from API DTOs.

**Devin Prompt:**
> Refactor the user-service to properly separate DTOs from entities. (1) Create a new `UserRegistrationRequest` DTO (with email, identification, password) separate from the response DTO. (2) Create a `UserResponse` DTO without password or JPA annotations. (3) Remove the JPA `@MappedSuperclass` inheritance from DTOs - only entities should extend `AuditAware`. (4) Create separate request/response DTOs for each endpoint. (5) Update mapper classes to handle the conversions. Apply similar separation to fund-transfer and utility-payment services.

---

### 3.10 Fix Keycloak Thread Safety (GAP-SEC-05)
**Severity:** Medium | **Effort:** Small

Make Keycloak initialization thread-safe.

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the manual lazy-initialization pattern with a Spring `@Bean` in a `@Configuration` class. Create a `KeycloakConfig` class that defines a `@Bean Keycloak keycloak(KeycloakProperties props)` method. Remove the static `keycloakInstance` field and the `getInstance()` method. Inject the `Keycloak` bean directly into `KeycloakManager`.

---

## Priority Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| Phase 1 (Quick Wins) | 8 | 4 | 4 | 0 | 0 |
| Phase 2 (Important) | 10 | 1 | 3 | 6 | 0 |
| Phase 3 (Polish) | 10 | 0 | 2 | 4 | 4 |
| **Total** | **28** | **5** | **9** | **10** | **4** |

## Recommended Execution Order

1. **Week 1:** Items 1.1 (balance bug), 1.2 (validation), 1.3 (password exposure), 1.5 (log sanitization)
2. **Week 2:** Items 1.4 (error codes), 1.6 (timeouts), 1.7 (retries), 1.8 (credentials)
3. **Weeks 3-4:** Items 2.1 (circuit breakers), 2.2 (Feign errors), 2.3 (idempotency)
4. **Weeks 5-6:** Items 2.4 (unit tests), 2.5 (shared library)
5. **Weeks 7-8:** Items 2.6-2.10 (logging, pagination, types, rate limiting, health)
6. **Weeks 9-12:** Phase 3 items based on team capacity
