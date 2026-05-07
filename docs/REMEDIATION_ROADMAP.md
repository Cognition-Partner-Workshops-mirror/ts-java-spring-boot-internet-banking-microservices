# Remediation Roadmap

Prioritized phased plan derived from the [Gap Analysis](./GAP_ANALYSIS.md). Items are grouped by phase based on severity, risk-to-business, and effort.

---

## Phase 1 — Quick Wins (Critical + High severity, Small effort)

These items fix the most dangerous gaps with the least work. Estimated total: **1–2 weeks**.

### 1.1 Fix Catch-All Exception Handler (All Services)

**Gap:** §2.1 — Catch-all returns HTTP 400 for all exceptions; leaks stack traces to clients.

**What to do:**
- Return HTTP 500 for unhandled exceptions
- Use structured `ErrorResponse` body (not raw strings)
- Log the full exception server-side; return only a generic message to clients

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler in every service (core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service). Change the catch-all @ExceptionHandler({Exception.class}) method to: (1) return HTTP 500 instead of 400, (2) return a structured ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred. Please try again later.", (3) log the full exception at ERROR level. Do not expose the exception message or stack trace in the response body. Run existing tests to make sure nothing breaks.
```
</details>

### 1.2 Add Bean Validation to All Request DTOs

**Gap:** §4.1 — No input validation on any request DTO.

**What to do:**
- Add `spring-boot-starter-validation` dependency (already transitively available)
- Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size` constraints to all request DTOs
- Add `@Valid` on all `@RequestBody` parameters
- Add `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler`

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add Bean Validation to all request DTOs:

1. FundTransferRequest (core-banking-service): @NotBlank on fromAccount and toAccount, @NotNull @Positive on amount
2. UtilityPaymentRequest (core-banking-service): @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber and account
3. FundTransferRequest (fund-transfer-service): same as #1
4. UtilityPaymentRequest (utility-payment-service): same as #2
5. User DTO (user-service): @NotBlank @Email on email, @NotBlank on identification, @NotBlank @Size(min=8) on password

Add @Valid annotation to every @RequestBody parameter in all controllers. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns HTTP 400 with a structured ErrorResponse containing the field validation errors. Run all existing tests.
```
</details>

### 1.3 Add Feign Error Decoder

**Gap:** §2.3 — No Feign error decoder; errors from downstream services are opaque.

**What to do:**
- Create a custom `FeignErrorDecoder` implementing `feign.codec.ErrorDecoder`
- Parse the error response body and throw domain-specific exceptions
- Register the decoder in each service's Feign configuration

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, create a reusable FeignErrorDecoder for the Feign clients in internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. The decoder should: (1) read the response body and attempt to deserialize it as an ErrorResponse (code, message), (2) if the upstream returns 4xx, throw a SimpleBankingGlobalException with the upstream error code and message, (3) if the upstream returns 5xx, throw a new ServiceUnavailableException (create this class extending SimpleBankingGlobalException) with an appropriate message. Register the decoder as a @Bean in each service's Feign configuration class. Add the ServiceUnavailableException handler to GlobalExceptionHandler returning HTTP 503.
```
</details>

### 1.4 Add Explicit Feign Timeouts

**Gap:** §7.3 — No explicit timeout configuration on Feign clients.

**What to do:**
- Configure `connectTimeout` and `readTimeout` in each service's `application.yml` (or config server)
- Recommended: 5s connect, 10s read for banking operations

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add explicit Feign client timeout configuration to the three services that use Feign (fund-transfer, utility-payment, user). In each service's application.yml or bootstrap.yml, add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Also add a more aggressive timeout for the user-service's readUser call (3s read timeout) since it's on the registration path. Verify that each service starts correctly with the new config.
```
</details>

### 1.5 Add Retry Policies

**Gap:** §7.2 — No retry configuration; transient failures cause immediate request failure.

**What to do:**
- Add `spring-retry` dependency
- Configure Feign retry with exponential backoff (max 3 retries, only on 5xx or connection errors)
- Ensure retries are NOT applied to non-idempotent POST endpoints (fund-transfer, util-payment)

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add retry support to Feign clients in the fund-transfer, utility-payment, and user services. Add the spring-retry dependency to each build.gradle. Configure Feign retry with: maxAttempts=3, backoff period=1000ms, multiplier=2.0. IMPORTANT: Only enable retry on GET requests (read operations). Do NOT retry POST requests for fund-transfer or util-payment since they are not idempotent. Create a custom Retryer bean that checks the HTTP method. Run tests to verify.
```
</details>

### 1.6 Fix Transaction Status Never Transitioning to FAILED

**Gap:** §7.6 — Orchestrating services never update status to FAILED on errors.

**What to do:**
- Wrap Feign calls in try-catch in `FundTransferService` and `UtilityPaymentService`
- On exception, update entity status to `FAILED` before re-throwing

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, fix the fund transfer and utility payment orchestration services to properly handle failures:

1. In FundTransferService.fundTransfer(): wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch. On any exception, update the FundTransferEntity status to FAILED, save it, then re-throw the exception.

2. In UtilityPaymentService.utilPayment(): wrap the bankingCoreRestClient.utilPayment() call in a try-catch. On any exception, update the UtilityPaymentEntity status to FAILED, save it, then re-throw the exception.

Write unit tests for both failure scenarios. Verify existing tests still pass.
```
</details>

---

## Phase 2 — Important (High severity or compounding risk, Medium effort)

These items address structural and architectural gaps. Estimated total: **3–5 weeks**.

### 2.1 Add Circuit Breakers

**Gap:** §7.1 — No circuit breakers on inter-service calls; cascading failures possible.

**What to do:**
- Add Resilience4j dependency (`spring-cloud-starter-circuitbreaker-resilience4j`)
- Configure circuit breakers on all Feign clients
- Add fallback methods that return meaningful error responses
- Configure thresholds (e.g., 50% failure rate → open, 10s wait → half-open)

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers to all Feign clients:

1. Add spring-cloud-starter-circuitbreaker-resilience4j to build.gradle in fund-transfer, utility-payment, and user services.

2. Create a Resilience4j configuration in each service's application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 10s
   - permittedNumberOfCallsInHalfOpenState: 3

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return FundTransferResponse with status FAILED and message "Core banking service unavailable"
   - BankingCoreRestClientFallback (utility-payment): similar pattern
   - BankingCoreRestClientFallback (user-service): throw a ServiceUnavailableException

4. Register the fallbacks in each @FeignClient annotation using fallback attribute.

Run all tests and verify services start correctly.
```
</details>

### 2.2 Add Idempotency to Write Operations

**Gap:** §7.5 — No idempotency keys; duplicate fund transfers possible.

**What to do:**
- Add `Idempotency-Key` header support to fund transfer and utility payment endpoints
- Store processed idempotency keys in the database with the result
- Return the cached result if the same key is seen again

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, implement idempotency for financial write operations:

1. Create an IdempotencyKey entity/table in both fund-transfer and utility-payment services with columns: id, idempotency_key (unique), response_body (TEXT), created_date.

2. Create an @Idempotent annotation and a Spring HandlerInterceptor that:
   - Reads the "Idempotency-Key" request header
   - If the key exists in the database, returns the cached response (HTTP 200)
   - If the key is new, proceeds with the request, then stores the response
   - If no Idempotency-Key header is provided, proceeds without caching

3. Apply the interceptor to POST /api/v1/transfer and POST /api/v1/utility-payment.

4. Add a Flyway migration for the new table.

Write tests for: duplicate key returns cached response, missing key proceeds normally, different keys produce separate results.
```
</details>

### 2.3 Fix Database Transaction Isolation for Financial Operations

**Gap:** §7.7 — Default isolation level allows race conditions on concurrent transfers.

**What to do:**
- Add `@Transactional(isolation = Isolation.SERIALIZABLE)` to `internalFundTransfer`
- Or use pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on account reads
- Fix the `availableBalance` double-subtraction bug

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices core-banking-service, fix the concurrency and balance calculation issues in TransactionService:

1. Fix the availableBalance calculation bug in internalFundTransfer(): after updating actualBalance, set availableBalance = actualBalance (not actualBalance.subtract(amount) again, which double-subtracts).

2. Add pessimistic locking to prevent concurrent transfer race conditions. In BankAccountRepository, add a method:
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT a FROM BankAccount a WHERE a.number = :number")
   Optional<BankAccount> findByNumberForUpdate(@Param("number") String number);

3. Update TransactionService.fundTransfer() and utilPayment() to use findByNumberForUpdate() instead of the regular find method.

4. Update existing unit tests and add a test that verifies the correct balance after a transfer (checking both actualBalance and availableBalance).
```
</details>

### 2.4 Extract Shared Library

**Gap:** §1.1 — Duplicated code across services.

**What to do:**
- Create a `banking-common` module with shared classes
- Move `BaseMapper`, `AuditAware`, `AuditConfig`, `ApiRequestContext*`, `AppAuthUserFilter`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `GlobalErrorCode`, `CustomFeignClientConfiguration`
- Publish as a local Maven/Gradle dependency

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, extract duplicated code into a shared library:

1. Create a new module 'banking-common' at the root with its own build.gradle. Set it up as a plain Java library (no Spring Boot plugin, just java-library plugin) with dependencies on spring-boot-starter-web, spring-boot-starter-data-jpa, spring-cloud-starter-openfeign, and lombok.

2. Move these classes from all services into banking-common under package com.javatodev.finance.common:
   - BaseMapper, AuditAware, AuditConfig, AuditorAwareConfig
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - ErrorResponse, GlobalExceptionHandler, SimpleBankingGlobalException, GlobalErrorCode
   - CustomFeignClientConfiguration

3. Create a root settings.gradle that includes all services + banking-common.

4. Update each service's build.gradle to depend on banking-common (implementation project(':banking-common')).

5. Remove the duplicated classes from each service and update imports.

6. Verify all services compile and tests pass.
```
</details>

### 2.5 Expand Unit Test Coverage

**Gap:** §3.1 — Only Core Banking has meaningful tests (< 10% coverage).

**What to do:**
- Add service-layer unit tests for fund-transfer, utility-payment, and user services
- Cover happy paths and error paths
- Target 80%+ coverage on service classes

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests for the three services that currently have only contextLoads() tests:

1. internet-banking-fund-transfer-service - FundTransferServiceTest:
   - Test successful fund transfer (mock Feign client returns success)
   - Test fund transfer when Feign client throws exception (status should be FAILED)
   - Test readTransfers pagination
   - Verify entity status transitions: PENDING → SUCCESS, PENDING → FAILED

2. internet-banking-utility-payment-service - UtilityPaymentServiceTest:
   - Test successful utility payment (mock Feign client returns success)
   - Test utility payment when Feign client throws exception (status should be FAILED)
   - Test readPayments pagination

3. internet-banking-user-service - UserServiceTest:
   - Test successful user registration (mock Keycloak + Feign)
   - Test registration with already-registered email (should throw UserAlreadyRegisteredException)
   - Test registration with email mismatch (should throw InvalidEmailException)
   - Test readUsers pagination
   - Test updateUser with APPROVED status (should enable Keycloak user)

Use Mockito to mock all dependencies. Follow the same patterns as the existing core-banking-service tests. Run all tests and verify they pass.
```
</details>

### 2.6 Add Pagination Metadata to List Endpoints

**Gap:** §5.1 — List endpoints return raw lists without pagination info.

**What to do:**
- Return `Page<T>` or a wrapper DTO with `content`, `totalElements`, `totalPages`, `page`, `size`
- Update all `GET` list endpoints in fund-transfer, utility-payment, user, and core-banking services

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, update all paginated list endpoints to return pagination metadata:

1. Create a generic PaginatedResponse<T> DTO in each service (or in the shared library if it exists) with fields: List<T> content, long totalElements, int totalPages, int currentPage, int pageSize.

2. Update these endpoints to return PaginatedResponse instead of List:
   - Core Banking: GET /api/v1/user (UserController.readUsers)
   - Fund Transfer: GET /api/v1/transfer (FundTransferController.readTransfers)
   - Utility Payment: GET /api/v1/utility-payment (UtilityPaymentController.readPayments)
   - User Service: GET /api/v1/bank-users (UserController.readAllUsers)

3. In each service method, use the Page object's metadata (getTotalElements(), getTotalPages(), getNumber(), getSize()) to populate the wrapper.

4. Update controller return types to ResponseEntity<PaginatedResponse<...>> for proper OpenAPI docs.

Run all existing tests and fix any that break due to the response shape change.
```
</details>

### 2.7 Improve Observability — Structured Logging and Business Metrics

**Gap:** §6.1, §6.3 — Inconsistent logging; no custom business metrics.

**What to do:**
- Configure structured JSON logging (logback-spring.xml)
- Add trace ID / span ID to log patterns via Micrometer MDC
- Add custom Micrometer counters/timers for key business operations

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, improve observability across all services:

1. Create a shared logback-spring.xml in each service's src/main/resources/ that:
   - Uses JSON format in 'docker' profile (net.logstash.logback:logstash-logback-encoder)
   - Includes traceId and spanId from MDC in log output
   - Uses standard text format in 'default' profile
   - Sets root log level to INFO, com.javatodev to DEBUG

2. Add the logstash-logback-encoder dependency to each service's build.gradle.

3. Add custom Micrometer metrics to core-banking-service TransactionService:
   - Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT, status=SUCCESS|FAILED)
   - Timer: banking.transactions.duration (tags: type=FUND_TRANSFER|UTILITY_PAYMENT)
   - Gauge: (optional) banking.accounts.active.count

4. Remove the toString() logging of request objects that expose sensitive data (account numbers, amounts). Replace with sanitized log messages that log only non-sensitive identifiers.

Run all tests and verify services start correctly.
```
</details>

---

## Phase 3 — Polish (Medium/Low severity or Large effort)

These items improve long-term maintainability. Estimated total: **4–8 weeks**.

### 3.1 Add Integration Tests with Testcontainers

**Gap:** §3.2 — No integration tests exist.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers for the core-banking-service:

1. Add Testcontainers dependencies to core-banking-service build.gradle: testcontainers-bom, testcontainers-mysql, testcontainers-junit-jupiter.

2. Create a base test class AbstractIntegrationTest with @SpringBootTest and @Testcontainers that starts a MySQL container and overrides spring.datasource properties.

3. Create AccountRepositoryIntegrationTest:
   - Test save and find bank account
   - Test find by account number

4. Create TransactionServiceIntegrationTest:
   - Test fund transfer end-to-end against real DB
   - Test insufficient funds rejection
   - Test concurrent transfers on the same account (verify no overdraft)

5. Create TransactionControllerIntegrationTest using MockMvc:
   - Test POST /api/v1/transaction/fund-transfer with valid request
   - Test POST /api/v1/transaction/fund-transfer with invalid request (after validation is added)
   - Test POST /api/v1/transaction/util-payment

Run all tests. Ensure existing unit tests still pass.
```
</details>

### 3.2 Add Consumer-Driven Contract Tests

**Gap:** §3.3 — No contract tests between services.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests between the core-banking-service (producer) and its consumers:

1. Add spring-cloud-starter-contract-verifier to core-banking-service build.gradle.

2. Create contracts in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number} → returns BankAccount
   - POST /api/v1/transaction/fund-transfer → returns FundTransferResponse
   - POST /api/v1/transaction/util-payment → returns UtilityPaymentResponse
   - GET /api/v1/user/{identification} → returns User

3. Create a ContractVerifierBaseTest that sets up MockMvc with mocked services.

4. Generate and run the contract tests.

5. Add spring-cloud-starter-contract-stub-runner to the consumer services' test dependencies and create stub-based integration tests that verify the consumers work with the contract stubs.

Run all tests in all services.
```
</details>

### 3.3 Create Multi-Module Gradle Build

**Gap:** §1.2 — No multi-module build; independent projects.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, convert to a multi-module Gradle build:

1. Create a root build.gradle.kts with:
   - Shared plugin declarations (spring-boot, dependency-management)
   - Shared repository configuration (mavenCentral)
   - Shared Java toolchain (Java 21)
   - Shared dependency versions in a version catalog (libs.versions.toml)

2. Create a root settings.gradle.kts that includes all 7 services + banking-common.

3. Move common dependencies and configurations from individual build.gradle files to the root subprojects {} block.

4. Keep service-specific dependencies in each service's build.gradle.

5. Remove individual Gradle wrappers from each service (keep only the root wrapper).

6. Verify: ./gradlew build from root compiles all services and runs all tests.
```
</details>

### 3.4 Add Rate Limiting to API Gateway

**Gap:** §4.5 — No rate limiting; financial endpoints vulnerable to abuse.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add rate limiting to the API Gateway:

1. Add spring-boot-starter-data-redis-reactive dependency to the API gateway's build.gradle.

2. Add a Redis service to docker-compose.yml.

3. Configure RequestRateLimiter filter in the gateway routes:
   - /fund-transfer/**: 10 requests/second per user
   - /payment/**: 10 requests/second per user
   - /user/**: 20 requests/second per user
   - Default: 50 requests/second per IP

4. Create a custom KeyResolver that extracts the user ID from the JWT token for authenticated routes, and falls back to client IP for unauthenticated routes.

5. Configure rate limit response to return HTTP 429 with a structured error body.

Test by sending rapid requests to the fund transfer endpoint and verifying 429 responses.
```
</details>

### 3.5 Enhance OpenAPI Documentation

**Gap:** §5.5 — OpenAPI annotations are minimal.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, enhance OpenAPI/Swagger documentation across all services:

1. Add @Schema annotations with descriptions and examples to all DTOs:
   - FundTransferRequest: describe each field, add example values
   - UtilityPaymentRequest: describe each field, add example values
   - User: describe each field
   - All response DTOs

2. Add @ApiResponse annotations to all controller methods documenting:
   - 200/201 success responses with schema
   - 400 validation error responses
   - 404 not found responses
   - 500 server error responses

3. Add a global OpenAPI configuration class to each service with:
   - API title, description, version
   - Contact info
   - Security scheme (Bearer JWT)

4. Type all ResponseEntity return types (e.g., ResponseEntity<FundTransferResponse>).

Verify Swagger UI loads correctly at /swagger-ui.html for each service.
```
</details>

### 3.6 Add Centralized Log Aggregation

**Gap:** §6.4 — No log aggregation in Docker Compose.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add a centralized log aggregation stack to docker-compose.yml:

1. Add Loki + Grafana containers to docker-compose.yml:
   - Loki for log storage (port 3100)
   - Grafana for visualization (port 3000)

2. Configure all service containers to use the Loki Docker logging driver, or alternatively add a Promtail sidecar that tails log files.

3. Add a Grafana provisioning config that:
   - Auto-configures Loki as a data source
   - Includes a pre-built dashboard for viewing logs by service name
   - Includes a dashboard for Zipkin trace correlation

4. Update the project README with instructions for accessing Grafana.

Verify: start docker-compose, make a few API calls, and confirm logs appear in Grafana.
```
</details>

### 3.7 Add Custom Health Indicators

**Gap:** §6.2 — No custom health indicators for dependencies.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, add custom Spring Boot Actuator health indicators:

1. In internet-banking-user-service: add a KeycloakHealthIndicator that checks Keycloak reachability by calling the realm endpoint.

2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service: add a CoreBankingHealthIndicator that checks Core Banking Service health via its /actuator/health endpoint.

3. In core-banking-service: add a DatabaseHealthIndicator that runs a simple query (SELECT 1) to verify MySQL connectivity (beyond the default DataSource health check).

4. Configure actuator to expose health details: management.endpoint.health.show-details=when-authorized.

5. Write unit tests for each health indicator (mock the external calls).

Verify: start each service and check /actuator/health returns the custom indicators.
```
</details>

### 3.8 Externalize Secrets Management

**Gap:** §4.3 — Hardcoded credentials in Docker Compose and SQL.

<details>
<summary>Sample Devin Prompt</summary>

```
In ts-java-spring-boot-internet-banking-microservices, externalize hardcoded credentials:

1. Update docker-compose.yml to read all passwords from environment variables with .env file defaults:
   - MYSQL_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, POSTGRES_PASSWORD
   - Create a .env.example file with placeholder values
   - Add .env to .gitignore

2. Update mysql/privileges.sql to use an environment variable for the password (use a Docker entrypoint script that performs envsubst).

3. For Spring Cloud Config Server, document how to encrypt sensitive values using Spring Cloud Config's /encrypt endpoint.

4. Update README.md with instructions for configuring secrets in different environments.
```
</details>

---

## Phase Summary

| Phase | Items | Severity Focus | Estimated Effort |
|---|---|---|---|
| Phase 1 | 6 items | Critical + High | 1–2 weeks |
| Phase 2 | 7 items | High + Medium (structural) | 3–5 weeks |
| Phase 3 | 8 items | Medium + Low (polish) | 4–8 weeks |
| **Total** | **21 items** | | **8–15 weeks** |

### Recommended Execution Order Within Each Phase

**Phase 1** (do in this order to maximize stability):
1. §1.1 Fix catch-all exception handler
2. §1.2 Add Bean Validation
3. §1.3 Add Feign error decoder
4. §1.4 Add Feign timeouts
5. §1.5 Add retry policies (GET-only)
6. §1.6 Fix FAILED status transitions

**Phase 2** (do in this order):
1. §2.3 Fix transaction isolation (before adding more tests)
2. §2.5 Expand unit test coverage (validates all Phase 1 + 2.3 fixes)
3. §2.1 Add circuit breakers
4. §2.4 Extract shared library
5. §2.2 Add idempotency keys
6. §2.6 Add pagination metadata
7. §2.7 Improve observability

**Phase 3** (flexible order):
- §3.1 and §3.2 (testing) can run in parallel with §3.3 (multi-module build)
- §3.4 (rate limiting) and §3.6 (log aggregation) require Docker Compose changes
- §3.5 (OpenAPI) and §3.7 (health indicators) are independent
