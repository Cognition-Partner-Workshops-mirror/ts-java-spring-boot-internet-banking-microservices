# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are organized into three phases based on risk, impact, and effort. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical fixes, Small/Medium effort)

These items address security vulnerabilities, data-integrity bugs, and information-disclosure risks that should be fixed immediately.

### 1.1 Fix Incorrect Available Balance Calculation (GAP-RES-07)

**Why now:** Every transaction produces wrong `availableBalance` — a correctness bug in the financial core.

**Devin prompt:**
> In `core-banking-service`, fix the double-deduction bug in `TransactionService.internalFundTransfer()` and `utilPayment()`. After debiting `actualBalance`, set `availableBalance = actualBalance` (not `actualBalance - amount` again). Add unit tests to verify balance correctness after fund transfer and utility payment. Open a PR.

---

### 1.2 Stop Leaking Exception Details to Clients (GAP-ERR-02)

**Why now:** Stack traces and internal class names are returned in HTTP responses — a critical information disclosure.

**Devin prompt:**
> In every service's `GlobalExceptionHandler`, replace the catch-all `@ExceptionHandler({Exception.class})` handler. Instead of returning `"Exception occur inside API " + e`, return a generic `ErrorResponse` with code `"INTERNAL_ERROR"` and message `"An unexpected error occurred"`. Log the full exception server-side at ERROR level. Open a PR.

---

### 1.3 Add Input Validation to All Request DTOs (GAP-SEC-01)

**Why now:** No validation on financial inputs — amounts can be null/negative, emails unvalidated.

**Devin prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services:
> - `FundTransferRequest`: `@NotBlank fromAccount, toAccount`; `@NotNull @Positive amount`
> - `UtilityPaymentRequest`: `@NotNull providerId`; `@NotNull @Positive amount`; `@NotBlank account`
> - User `User` DTO: `@NotBlank @Email email`; `@NotBlank identification`; `@NotBlank @Size(min=8) password`
> - `UserUpdateRequest`: `@NotNull status`
>
> Add `@Valid` to all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency if not already present. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 422 with field-level error details. Open a PR with tests.

---

### 1.4 Return Correct HTTP Status Codes (GAP-ERR-01)

**Why now:** All errors return 400, making the API unusable for proper client error handling.

**Devin prompt:**
> Refactor `GlobalExceptionHandler` in all services to use proper HTTP status codes:
> - `EntityNotFoundException` → 404 Not Found
> - `InsufficientFundsException` → 422 Unprocessable Entity
> - `UserAlreadyRegisteredException` → 409 Conflict
> - `InvalidEmailException`, `InvalidBankingUserException` → 400 Bad Request
> - `SimpleBankingGlobalException` (generic) → 400 Bad Request
> - `Exception` (catch-all) → 500 Internal Server Error
>
> Ensure all handlers return a consistent `ErrorResponse {code, message}` JSON shape. Open a PR.

---

### 1.5 Secure Actuator Endpoints (GAP-SEC-05)

**Why now:** Sensitive runtime info is publicly accessible.

**Devin prompt:**
> In `internet-banking-api-gateway`'s `SecurityConfiguration`, change actuator endpoint permissions:
> - Allow `/actuator/health` and `/actuator/info` without authentication
> - Require authentication for all other actuator paths (`/actuator/**`)
> - Apply the same restriction for proxied service actuator paths (`/user/actuator/**`, etc.)
>
> Open a PR.

---

### 1.6 Add Feign Timeout Configuration (GAP-RES-03)

**Why now:** Small config change that prevents thread exhaustion from slow downstream calls.

**Devin prompt:**
> Add Feign client timeout configuration for all services that use OpenFeign (fund-transfer, user-service, utility-payment). Set `connectTimeout: 5000` and `readTimeout: 10000` milliseconds via Spring Cloud OpenFeign configuration properties in each service's externalized config or `application.yml`. Open a PR.

---

### 1.7 Add Feign Retry Policy (GAP-RES-02)

**Why now:** Small effort, prevents failures from transient network issues.

**Devin prompt:**
> Add Spring Retry support to all Feign clients. Add `spring-retry` dependency and configure a `Retryer` bean with max 3 attempts, 1-second initial backoff, and 5-second max backoff. Only retry on connection exceptions, NOT on business errors (4xx). Open a PR.

---

### 1.8 Add `.gitignore` (GAP-ORG-04)

**Devin prompt:**
> Add a root `.gitignore` file appropriate for a Java/Gradle project. Include patterns for: `build/`, `.gradle/`, `*.class`, `.idea/`, `*.iml`, `.settings/`, `.project`, `.classpath`, `*.log`, `.env`, `*.jar` (except gradle-wrapper.jar). Open a PR.

---

### 1.9 Fix Keycloak Singleton Thread Safety (GAP-SEC-07)

**Devin prompt:**
> In `internet-banking-user-service`'s `KeycloakProperties`, make the `getInstance()` method thread-safe. Either use `synchronized`, `volatile` with double-checked locking, or refactor to inject `Keycloak` as a Spring `@Bean`. Open a PR.

---

## Phase 2: Important (High-impact structural improvements)

### 2.1 Add Circuit Breakers with Resilience4j (GAP-RES-01)

**Devin prompt:**
> Add Resilience4j circuit breaker support to fund-transfer-service, user-service, and utility-payment-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency. Configure circuit breakers on all Feign clients with: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`. Add fallback methods that return meaningful error responses and log the failure. Open a PR.

---

### 2.2 Add Idempotency Protection for Financial Operations (GAP-RES-05)

**Devin prompt:**
> Add idempotency key support to fund transfer and utility payment endpoints:
> 1. Accept an `X-Idempotency-Key` header on POST endpoints
> 2. Store the key with the transaction record
> 3. If a duplicate key is received, return the original response instead of re-processing
> 4. Add a unique constraint on the idempotency key column
>
> Open a PR with tests.

---

### 2.3 Fix Non-Atomic Balance Updates with Pessimistic Locking (GAP-RES-06)

**Devin prompt:**
> In `core-banking-service`, add pessimistic write locking to prevent lost updates on concurrent balance modifications:
> 1. Add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to `BankAccountRepository.findByNumber()`
> 2. Ensure `TransactionService.internalFundTransfer()` and `utilPayment()` read and write within the same transaction
> 3. Add a concurrent transfer integration test using multiple threads
>
> Open a PR.

---

### 2.4 Create Shared Common Library (GAP-ORG-02)

**Devin prompt:**
> Create a `common-library` module with a root `settings.gradle` that includes all services. Move shared code into the common library:
> - `BaseMapper`
> - `AuditAware`, `AuditConfig`, `AuditorAwareConfig`
> - `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`
> - `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`
>
> Update all services to depend on the common library. Open a PR.

---

### 2.5 Add Unit Tests for All Services (GAP-TEST-01)

**Devin prompt:**
> Add comprehensive unit tests for all services that currently lack them:
> - `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()`, `readAllTransfers()` with mocked Feign client and repository
> - `internet-banking-user-service`: Test `UserService.createUser()`, `updateUser()`, `readUsers()`, `readUser()` with mocked Keycloak and repository
> - `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()`, `readPayments()` with mocked Feign client and repository
>
> Target: at least 80% line coverage per service class. Open a PR.

---

### 2.6 Add Feign Error Decoder to All Feign Clients (GAP-ERR-04)

**Devin prompt:**
> Add a `CustomFeignErrorDecoder` (similar to the one in user-service) to fund-transfer-service and utility-payment-service. The decoder should:
> 1. Parse `ErrorResponse` from the response body
> 2. Re-throw appropriate exceptions based on HTTP status (400 → business exception, 404 → EntityNotFoundException, 5xx → ServiceUnavailableException)
> 3. Log the error details
>
> Open a PR with tests.

---

### 2.7 Return Typed ResponseEntity and Pagination Metadata (GAP-API-01, GAP-API-03)

**Devin prompt:**
> Across all services:
> 1. Add generic type parameters to all `ResponseEntity` return types (e.g., `ResponseEntity<BankAccount>`)
> 2. For list endpoints, return a `Page<T>` wrapper or a custom `PageResponse<T>` DTO that includes `content`, `totalElements`, `totalPages`, `currentPage`, and `pageSize`
> 3. Return HTTP 201 (Created) from POST endpoints that create resources
>
> Open a PR.

---

### 2.8 Add Rate Limiting to API Gateway (GAP-SEC-04)

**Devin prompt:**
> Add rate limiting to `internet-banking-api-gateway` using Spring Cloud Gateway's `RequestRateLimiter` filter with Redis or in-memory token bucket. Configure:
> - Default: 100 requests/minute per authenticated user
> - Registration endpoint: 10 requests/minute per IP
> - Fund transfer/payment: 30 requests/minute per user
>
> Add Redis to docker-compose if needed. Open a PR.

---

### 2.9 Add Dependency Vulnerability Scanning (GAP-SEC-06)

**Devin prompt:**
> Add the OWASP Dependency-Check Gradle plugin to all services. Configure it to fail the build on CVSS >= 7.0 vulnerabilities. Add a `dependencyCheckAnalyze` task. Run the scan and fix or suppress any critical/high findings. Open a PR.

---

### 2.10 Improve Logging Consistency (GAP-OBS-01)

**Devin prompt:**
> Standardize logging across all services:
> 1. Fix the string concatenation bug in `FundTransferService`: change `log.info("Sending fund transfer request {}" + request.toString())` to `log.info("Sending fund transfer request {}", request)`
> 2. Configure JSON-structured logging using Logback's `LogstashEncoder` (add `logstash-logback-encoder` dependency)
> 3. Ensure all services log: request method, path, response status, duration, and correlation/trace ID
>
> Open a PR.

---

## Phase 3: Polish (Low-risk improvements for long-term maintainability)

### 3.1 Add Integration Tests with Testcontainers (GAP-TEST-02)

**Devin prompt:**
> Add integration tests for core-banking-service using Testcontainers for MySQL:
> 1. Add `testcontainers` and `mysql` Testcontainers dependencies
> 2. Create `@SpringBootTest` tests that verify the full request lifecycle: create user → create account → fund transfer → verify balances
> 3. Use `@DynamicPropertySource` to inject the Testcontainers MySQL URL
>
> Open a PR.

---

### 3.2 Add Contract Tests Between Services (GAP-TEST-03)

**Devin prompt:**
> Add Spring Cloud Contract tests between fund-transfer-service (consumer) and core-banking-service (provider):
> 1. Add `spring-cloud-starter-contract-verifier` to core-banking-service
> 2. Define contracts for `GET /api/v1/account/bank-account/{number}` and `POST /api/v1/transaction/fund-transfer`
> 3. Add `spring-cloud-starter-contract-stub-runner` to fund-transfer-service for consumer-driven tests
>
> Open a PR.

---

### 3.3 Enhance OpenAPI Documentation (GAP-API-04)

**Devin prompt:**
> Improve OpenAPI documentation across all services:
> 1. Add `@ApiResponse` annotations for success (200/201) and error (400/404/422/500) cases on all controller methods
> 2. Add `@Schema` annotations with descriptions and examples to all DTOs
> 3. Configure `springdoc.api-docs.path` and `springdoc.swagger-ui.path` consistently
> 4. Consider adding a gateway-level Swagger aggregator
>
> Open a PR.

---

### 3.4 Unify Project Structure with Multi-Project Gradle Build (GAP-ORG-01)

**Devin prompt:**
> Create a root `settings.gradle` and `build.gradle` for the entire project:
> 1. `settings.gradle` should include all 7 sub-projects (6 services + common library)
> 2. Root `build.gradle` should define common `repositories`, `dependencyManagement`, Java version, and shared plugins
> 3. Each service `build.gradle` should only declare its specific dependencies
> 4. Verify `./gradlew build` from root builds all services
>
> Open a PR.

---

### 3.5 Fix REST Convention Issues (GAP-API-05)

**Devin prompt:**
> Refactor endpoint paths to follow REST conventions:
> - Rename `POST /api/v1/transfer` → `POST /api/v1/transfers`
> - Rename `PATCH /api/v1/bank-users/update/{id}` → `PATCH /api/v1/bank-users/{id}`
> - Return 201 Created (with `Location` header) from all POST endpoints that create resources
> - Update gateway routes and Feign client paths accordingly
>
> Open a PR.

---

### 3.6 Add Custom Health Checks and Metrics (GAP-OBS-02, GAP-OBS-03)

**Devin prompt:**
> Add custom health indicators and business metrics:
> 1. Core Banking: Health check for MySQL connectivity; counter metrics for `fund_transfer_total`, `utility_payment_total` (with success/failure tags)
> 2. User Service: Health check for Keycloak connectivity; counter for `user_registration_total`
> 3. All services: Add Prometheus metrics endpoint (`/actuator/prometheus`) by adding `micrometer-registry-prometheus` dependency
> 4. Add a Prometheus scrape config to docker-compose
>
> Open a PR.

---

### 3.7 Add Filtering and Sorting to List Endpoints (GAP-API-06)

**Devin prompt:**
> Add query parameter filtering to list endpoints:
> - `GET /api/v1/transfers?status=SUCCESS&fromAccount=100015003000&dateFrom=2024-01-01&dateTo=2024-12-31`
> - `GET /api/v1/utility-payment?status=SUCCESS&providerId=1`
> - `GET /api/v1/bank-users?status=APPROVED`
>
> Use Spring Data JPA Specifications or `@Query` annotations. Open a PR.

---

### 3.8 Externalize and Secure Credentials (GAP-SEC-02)

**Devin prompt:**
> Remove all hardcoded credentials from source code:
> 1. Replace hardcoded passwords in `docker-compose.yml` with environment variable references (`${MYSQL_ROOT_PASSWORD}`)
> 2. Create a `.env.example` file with placeholder values
> 3. Add `.env` to `.gitignore`
> 4. Move Keycloak client-secret to externalized config or environment variables
> 5. Document the required environment variables in the README
>
> Open a PR.

---

### 3.9 Add Fallback Behavior for Feign Clients (GAP-RES-04)

**Devin prompt:**
> Add Feign fallback implementations for all Feign clients:
> - `BankingCoreFeignClient` (fund-transfer): Return a fallback `FundTransferResponse` with status `FAILED` and a descriptive message
> - `BankingCoreRestClient` (utility-payment): Return a fallback `UtilityPaymentResponse` with status `FAILED`
> - `BankingCoreRestClient` (user-service): Throw a clear `ServiceUnavailableException`
>
> Log all fallback invocations at WARN level. Open a PR.

---

### 3.10 Fix Context-Load Tests (GAP-TEST-04)

**Devin prompt:**
> Fix the boilerplate `contextLoads()` tests in all services so they can run without external infrastructure:
> 1. Add test-profile `application.yml` files for API Gateway, Config Server, and Service Registry
> 2. Disable Eureka client, Config Server, and external dependencies in test profiles
> 3. Verify all `contextLoads()` tests pass with `./gradlew test`
>
> Open a PR.

---

## Summary Timeline

| Phase | Items | Estimated Total Effort | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 9 items | ~2 weeks | Security holes closed, data-integrity bugs fixed, basic resilience |
| **Phase 2** | 10 items | ~4 weeks | Structural improvements, test coverage, circuit breakers, shared library |
| **Phase 3** | 10 items | ~4 weeks | API polish, advanced observability, contract tests, full CI readiness |
