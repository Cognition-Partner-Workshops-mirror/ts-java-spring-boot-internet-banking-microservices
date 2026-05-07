# Remediation Roadmap

## Overview

This roadmap organizes the 34 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 days each): Critical and high-severity items with small effort. These reduce risk immediately with minimal code changes.
- **Phase 2 — Important** (3-5 days each): Medium-to-high severity items requiring moderate effort. These improve reliability and developer experience.
- **Phase 3 — Polish** (varies): Lower-severity items that improve consistency, documentation, and long-term maintainability.

Each item includes a sample Devin prompt you can use to automate the remediation.

---

## Phase 1: Quick Wins

### 1.1 Fix Balance Calculation Bug (GAP-RES-07)

**Severity:** Critical | **Effort:** Small | **Impact:** Prevents financial data corruption

The `availableBalance` is double-subtracted on debits and double-added on credits in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`.

**Remediation:** After updating `actualBalance`, set `availableBalance = actualBalance`.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the balance calculation bug in core-banking-service TransactionService.
In internalFundTransfer() and utilPayment(), after updating actualBalance,
set availableBalance equal to actualBalance instead of subtracting/adding
the amount again. Update the existing unit tests in TransactionServiceTest
to assert that availableBalance equals actualBalance after each operation.
Open a PR with the fix.
```
</details>

---

### 1.2 Fix Generic Exception Handler — Stop Leaking Stack Traces (GAP-ERR-02)

**Severity:** Critical | **Effort:** Small | **Impact:** Prevents information disclosure

**Remediation:** In all four `GlobalExceptionHandler` classes, change the generic `Exception` handler to:
1. Log the full exception at `ERROR` level.
2. Return a structured `ErrorResponse` with a generic message and HTTP 500.

<details>
<summary>Sample Devin Prompt</summary>

```
In all GlobalExceptionHandler classes across core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
and internet-banking-utility-payment-service: update the catch-all
@ExceptionHandler({Exception.class}) to log the full exception at ERROR
level and return a structured ErrorResponse with code="INTERNAL_ERROR"
and message="An unexpected error occurred. Please try again later." with
HTTP 500 status. Do not expose the exception message or stack trace in
the response body. Open a PR with these changes.
```
</details>

---

### 1.3 Add Input Validation to Request DTOs (GAP-SEC-02)

**Severity:** Critical | **Effort:** Small | **Impact:** Prevents invalid data from reaching business logic

**Remediation:** Add Jakarta Validation annotations to all request DTOs and `@Valid` to controller parameters.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Jakarta Bean Validation to the project:
1. Add spring-boot-starter-validation dependency to core-banking-service,
   internet-banking-fund-transfer-service, internet-banking-utility-payment-service,
   and internet-banking-user-service build.gradle files.
2. Add validation annotations to these DTOs:
   - core-banking FundTransferRequest: @NotBlank on fromAccount and toAccount,
     @NotNull @Positive on amount
   - core-banking UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive
     on amount, @NotBlank on referenceNumber and account
   - user-service User: @Email @NotBlank on email, @NotBlank on identification,
     @NotBlank @Size(min=8) on password
   - fund-transfer FundTransferRequest: same as core-banking version
   - utility-payment UtilityPaymentRequest: @NotNull on providerId,
     @NotNull @Positive on amount, @NotBlank on referenceNumber and account
3. Add @Valid annotation to all @RequestBody parameters in controllers.
4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler
   that returns HTTP 400 with field-level error details.
5. Add unit tests for validation.
Open a PR with all changes.
```
</details>

---

### 1.4 Fix Transaction Entity Relationship (GAP-RES-06)

**Severity:** High | **Effort:** Small | **Impact:** Prevents data integrity issues

**Remediation:** Change `TransactionEntity.account` from `@OneToOne(cascade=ALL)` to `@ManyToOne` with no cascade.

<details>
<summary>Sample Devin Prompt</summary>

```
In core-banking-service, fix the TransactionEntity JPA mapping:
1. Change @OneToOne(cascade = CascadeType.ALL) to @ManyToOne on the
   account field.
2. Keep the @JoinColumn(name = "account_id", referencedColumnName = "id").
3. Remove CascadeType.ALL.
4. Verify the Flyway migration for banking_core_transaction already has
   a non-unique FK on account_id (it does).
5. Update any affected tests. Open a PR.
```
</details>

---

### 1.5 Map Exceptions to Correct HTTP Status Codes (GAP-ERR-01)

**Severity:** High | **Effort:** Small | **Impact:** Proper REST semantics for API consumers

**Remediation:** Update all `GlobalExceptionHandler` classes to use appropriate HTTP status codes.

<details>
<summary>Sample Devin Prompt</summary>

```
Update GlobalExceptionHandler in all four business services to return
correct HTTP status codes:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity
- UserAlreadyRegisteredException → 409 Conflict
- InvalidBankingUserException → 404 Not Found
- InvalidEmailException → 400 Bad Request (keep as-is)
- SimpleBankingGlobalException (generic) → 400 Bad Request (keep as-is)
- Exception (catch-all) → 500 Internal Server Error
Add specific @ExceptionHandler methods for each exception type.
Ensure all return structured ErrorResponse. Open a PR.
```
</details>

---

### 1.6 Add Timeout Configuration for Feign Clients (GAP-RES-03)

**Severity:** High | **Effort:** Small | **Impact:** Prevents thread pool exhaustion from slow downstream services

**Remediation:** Add Feign timeout configuration to application properties.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Feign client timeout configuration to internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
Add the following to each service's application.yml:
  spring.cloud.openfeign.client.config.default.connect-timeout: 5000
  spring.cloud.openfeign.client.config.default.read-timeout: 10000
Also add Keycloak admin client timeout configuration to the user service.
Open a PR.
```
</details>

---

### 1.7 Add Retry Policies for Feign Clients (GAP-RES-02)

**Severity:** High | **Effort:** Small | **Impact:** Handles transient network failures gracefully

**Remediation:** Configure Feign retry with backoff for transient failures.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Feign retry configuration to internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
Add a @Bean Retryer in each service's Feign configuration class that retries
up to 3 times with 100ms initial interval and 1s max interval.
Only retry on 5xx errors and connection exceptions, not on 4xx errors.
Open a PR.
```
</details>

---

### 1.8 Externalize Docker Compose Credentials (GAP-SEC-01)

**Severity:** Critical | **Effort:** Small | **Impact:** Removes hardcoded secrets from version control

**Remediation:** Move credentials to a `.env` file and reference via `${VARIABLE}` in docker-compose.yml.

<details>
<summary>Sample Devin Prompt</summary>

```
Externalize all hardcoded credentials in docker-compose/docker-compose.yml
and docker-compose/docker-compose-support-apps.yml:
1. Create a docker-compose/.env.example file with placeholder values for
   MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD,
   and POSTGRES_PASSWORD.
2. Update both docker-compose files to use ${VARIABLE} syntax.
3. Add docker-compose/.env to .gitignore.
4. Create docker-compose/.env with the current default values for local dev.
5. Update README.md with instructions to copy .env.example to .env.
Open a PR.
```
</details>

---

### 1.9 Add Feign Error Decoder to Fund Transfer and Utility Payment Services (GAP-ERR-05)

**Severity:** High | **Effort:** Medium | **Impact:** Translates downstream errors into meaningful exceptions

<details>
<summary>Sample Devin Prompt</summary>

```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service
and internet-banking-utility-payment-service, similar to the one in
internet-banking-user-service:
1. Create a CustomFeignErrorDecoder class in each service that reads the
   response body and maps HTTP status codes to domain exceptions:
   - 404 → EntityNotFoundException
   - 422 → SimpleBankingGlobalException with appropriate message
   - 5xx → SimpleBankingGlobalException with "Core banking service unavailable"
2. Register the decoder in each service's CustomFeignClientConfiguration.
3. Add unit tests for the error decoder.
Open a PR.
```
</details>

---

### 1.10 Fix Keycloak Singleton Thread Safety (GAP-SEC-04)

**Severity:** Medium | **Effort:** Small | **Impact:** Prevents race conditions in multi-threaded environment

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the thread-safety issue in internet-banking-user-service
KeycloakProperties.getInstance(). Replace the manual singleton pattern
with a Spring @Bean definition: create a @Configuration class that
provides a Keycloak @Bean using KeycloakBuilder. Inject the Keycloak
bean into KeycloakManager instead of calling getInstance().
Remove the static keycloakInstance field. Open a PR.
```
</details>

---

### 1.11 Add JaCoCo Test Coverage Plugin (GAP-TST-04)

**Severity:** Medium | **Effort:** Small | **Impact:** Provides visibility into test coverage

<details>
<summary>Sample Devin Prompt</summary>

```
Add the JaCoCo Gradle plugin to all six microservices:
1. Add 'jacoco' plugin to each build.gradle.
2. Configure jacocoTestReport to generate HTML and XML reports.
3. Configure jacocoTestCoverageVerification with a minimum line coverage
   of 50% (as a starting point) for business services.
4. Make the 'check' task depend on jacocoTestCoverageVerification.
Open a PR.
```
</details>

---

## Phase 2: Important

### 2.1 Add Circuit Breakers with Resilience4j (GAP-RES-01)

**Severity:** Critical | **Effort:** Medium | **Impact:** Prevents cascading failures across services

<details>
<summary>Sample Devin Prompt</summary>

```
Add Resilience4j circuit breaker to all Feign clients:
1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to
   internet-banking-user-service, internet-banking-fund-transfer-service,
   and internet-banking-utility-payment-service.
2. Enable circuit breaker for Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Configure default circuit breaker in application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3
4. Add @CircuitBreaker fallback methods for each Feign client interface.
5. Add integration tests verifying circuit breaker behavior.
Open a PR.
```
</details>

---

### 2.2 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (GAP-TST-01)

**Severity:** High | **Effort:** Large | **Impact:** Catches regressions in orchestration logic

<details>
<summary>Sample Devin Prompt</summary>

```
Add comprehensive unit tests for the three services that currently have none:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid email,
     user not found in core banking, Keycloak failure), readUsers, readUser,
     updateUser (approve flow, reject flow)
   - KeycloakUserServiceTest: test createUser, readUser, readUserByEmail, updateUser
   - UserControllerTest: MockMvc tests for all endpoints

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (success, core banking failure,
     save failure), readAllTransfers
   - FundTransferControllerTest: MockMvc tests for POST and GET

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (success, core banking failure),
     readPayments
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET

Use Mockito for mocking Feign clients and repositories.
Use H2 test configuration already in test dependencies. Open a PR.
```
</details>

---

### 2.3 Add Fund Transfer Idempotency (GAP-RES-05)

**Severity:** High | **Effort:** Medium | **Impact:** Prevents duplicate financial transactions

<details>
<summary>Sample Devin Prompt</summary>

```
Add idempotency support to the fund transfer flow:
1. In internet-banking-fund-transfer-service:
   - Accept an optional X-Idempotency-Key header in FundTransferController.
   - Before processing, check if a FundTransferEntity with that idempotency
     key already exists. If so, return the existing response.
   - Add an idempotencyKey column to FundTransferEntity with a unique index.
   - Add a Flyway migration for the new column.
2. In internet-banking-utility-payment-service:
   - Apply the same pattern with X-Idempotency-Key header.
   - Add idempotencyKey column to UtilityPaymentEntity.
3. Add unit tests for duplicate detection.
Open a PR.
```
</details>

---

### 2.4 Implement Fallback Behavior for Service Failures (GAP-RES-04)

**Severity:** Medium | **Effort:** Medium | **Impact:** Graceful degradation instead of hard failures

<details>
<summary>Sample Devin Prompt</summary>

```
Add fallback behavior for Feign client failures:
1. In internet-banking-user-service:
   - When Keycloak is unavailable during readUsers(), return users from
     local database without Keycloak enrichment (email will be null).
   - Log a warning when fallback is triggered.
2. In internet-banking-fund-transfer-service:
   - When core banking fails after local entity is saved, update the
     local entity status to FAILED instead of leaving it as PENDING.
   - Add a scheduled job to retry FAILED transfers.
3. In internet-banking-utility-payment-service:
   - Same pattern: mark failed payments as FAILED, add retry mechanism.
4. Add unit tests for fallback scenarios.
Open a PR.
```
</details>

---

### 2.5 Add Rate Limiting to Public Endpoints (GAP-SEC-06)

**Severity:** Medium | **Effort:** Medium | **Impact:** Prevents abuse of registration endpoint

<details>
<summary>Sample Devin Prompt</summary>

```
Add rate limiting to the API Gateway for the public registration endpoint:
1. Add spring-boot-starter-data-redis-reactive dependency to
   internet-banking-api-gateway.
2. Add a Redis container to docker-compose.yml.
3. Configure Spring Cloud Gateway RequestRateLimiter filter for the
   /user/api/v1/bank-users/register route:
   - replenishRate: 5 requests per second
   - burstCapacity: 10
   - Use IP-based key resolver
4. Add rate limit headers (X-RateLimit-Remaining, X-RateLimit-Limit)
   to responses.
5. Update docker-compose to include Redis.
Open a PR.
```
</details>

---

### 2.6 Create Shared Library Module (GAP-ORG-01)

**Severity:** Medium | **Effort:** Medium | **Impact:** Eliminates code duplication, enables consistent behavior

<details>
<summary>Sample Devin Prompt</summary>

```
Refactor the project to use a Gradle multi-module build with a shared library:
1. Create a root settings.gradle that includes all service modules.
2. Create a shared-library module containing:
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - BaseMapper interface
   - AuditAware base class
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
3. Update each service's build.gradle to depend on shared-library.
4. Remove duplicated classes from each service.
5. Verify all services still compile and tests pass.
Open a PR.
```
</details>

---

### 2.7 Fix OpenAPI Documentation (GAP-API-04)

**Severity:** Medium | **Effort:** Small | **Impact:** Accurate API documentation for consumers

<details>
<summary>Sample Devin Prompt</summary>

```
Fix OpenAPI/Swagger documentation across all business services:
1. Replace springdoc-openapi-starter-webflux-ui with
   springdoc-openapi-starter-webmvc-ui in core-banking-service,
   internet-banking-user-service, internet-banking-fund-transfer-service,
   and internet-banking-utility-payment-service build.gradle files.
2. Add type parameters to all raw ResponseEntity return types in controllers.
3. Add @ApiResponse annotations for success and error responses on all
   controller methods.
4. Add @Schema annotations to request/response DTOs.
5. Verify Swagger UI loads correctly at /swagger-ui.html for each service.
Open a PR.
```
</details>

---

### 2.8 Add Pagination Metadata to List Responses (GAP-API-03)

**Severity:** Medium | **Effort:** Small | **Impact:** Clients can navigate paginated data properly

<details>
<summary>Sample Devin Prompt</summary>

```
Update all list/paginated endpoints to return pagination metadata:
1. Create a generic PaginatedResponse<T> class in the shared library with
   fields: content (List<T>), totalElements, totalPages, page, size, hasNext.
2. Update these endpoints to return PaginatedResponse instead of List:
   - core-banking UserController.readUsers()
   - user-service UserController.readUsers()
   - fund-transfer FundTransferController.readFundTransfers()
   - utility-payment UtilityPaymentController.readPayments()
3. Update the service methods to return Page<T> from the repository and
   map it to PaginatedResponse.
4. Update existing tests.
Open a PR.
```
</details>

---

### 2.9 Add Integration Tests (GAP-TST-02)

**Severity:** High | **Effort:** Large | **Impact:** Validates end-to-end behavior within each service

<details>
<summary>Sample Devin Prompt</summary>

```
Add integration tests for core-banking-service and user-service:
1. core-banking-service:
   - Create a test application.yml that uses H2 in-memory database
     with MySQL compatibility mode.
   - Write @SpringBootTest tests for AccountController, UserController,
     and TransactionController using MockMvc.
   - Verify Flyway migrations run against H2.
   - Test the full fund transfer flow end-to-end within the service.
2. internet-banking-user-service:
   - Create integration tests with @MockBean for BankingCoreRestClient
     and KeycloakUserService.
   - Test registration, approval, and listing flows.
3. Disable Eureka client and Config Server in test profiles to allow
   standalone testing.
Open a PR.
```
</details>

---

### 2.10 Add Structured Logging (GAP-OBS-03)

**Severity:** Medium | **Effort:** Small | **Impact:** Enables log aggregation and analysis

<details>
<summary>Sample Devin Prompt</summary>

```
Add JSON structured logging to all microservices:
1. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency
   to all service build.gradle files.
2. Create a shared logback-spring.xml configuration that:
   - Uses JSON format in docker/production profiles.
   - Uses human-readable console format in default/dev profiles.
   - Includes traceId and spanId from Micrometer in JSON output.
3. Place the logback-spring.xml in each service's src/main/resources/.
4. Remove any toString() calls on request objects that might log
   sensitive data (especially User.password in user-service).
Open a PR.
```
</details>

---

### 2.11 Add Dependency Vulnerability Scanning (GAP-SEC-05)

**Severity:** Medium | **Effort:** Small | **Impact:** Automated detection of known CVEs

<details>
<summary>Sample Devin Prompt</summary>

```
Add OWASP Dependency-Check to the project:
1. Add the org.owasp.dependencycheck Gradle plugin to all service
   build.gradle files.
2. Configure the plugin to fail the build on CVSS score >= 7.
3. Add a CI step (GitHub Actions workflow) that runs dependency-check
   on every PR.
4. Generate an initial report and document any existing vulnerabilities
   that need attention.
Open a PR.
```
</details>

---

## Phase 3: Polish

### 3.1 Standardize Package Structure (GAP-ORG-02)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Standardize the package structure across all microservices to follow
this convention:
- com.javatodev.finance.controller
- com.javatodev.finance.service
- com.javatodev.finance.repository
- com.javatodev.finance.model.entity
- com.javatodev.finance.model.dto
- com.javatodev.finance.model.mapper
- com.javatodev.finance.exception
- com.javatodev.finance.configuration

Move classes as needed. The main changes are:
- utility-payment: move repository from top-level to model.repository
  (or standardize all to top-level repository)
- user-service: move model.rest.response to model.dto.response
Ensure all imports are updated and tests pass. Open a PR.
```
</details>

---

### 3.2 Convert Mappers to Spring Beans (GAP-ORG-03)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Convert all mapper classes to Spring-managed beans:
1. Add @Component to BaseMapper, UserMapper, BankAccountMapper,
   UtilityAccountMapper, FundTransferMapper, UtilityPaymentMapper.
2. Inject mappers via constructor injection in service classes instead
   of instantiating with new.
3. Update unit tests to mock or create mapper instances as needed.
Open a PR.
```
</details>

---

### 3.3 Standardize URL Naming Conventions (GAP-API-01)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Standardize URL naming across all services:
1. Replace abbreviated paths: /util-account → /utility-account,
   /util-payment → /utility-payment
2. Standardize path variable naming to use kebab-case:
   {account_number} → {accountNumber} (or pick one convention)
3. Update all Feign client mappings to match.
4. Update API Gateway route configurations.
5. Update Postman collection if present.
Open a PR.
```
</details>

---

### 3.4 Document API Versioning Strategy (GAP-API-02)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Create docs/API_VERSIONING.md documenting the API versioning strategy:
1. Document that the project uses URL path versioning (/api/v1/).
2. Define rules for when to increment the version.
3. Define the deprecation process (Sunset header, minimum support period).
4. Document backward compatibility requirements.
Open a PR.
```
</details>

---

### 3.5 Document Filtering and Sorting Parameters (GAP-API-05)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Add @Parameter annotations to all paginated endpoints documenting
the available query parameters (page, size, sort). Add examples in
the @Operation description. Update the README API documentation
section. Open a PR.
```
</details>

---

### 3.6 Add Custom Health Indicators (GAP-OBS-02)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Add custom health indicators to each service:
1. core-banking-service: database health (auto-configured, just verify).
2. internet-banking-user-service: add KeycloakHealthIndicator that
   pings the Keycloak server.
3. internet-banking-fund-transfer-service: add health indicator for
   core-banking-service availability via Eureka.
4. internet-banking-utility-payment-service: same as fund-transfer.
5. Configure actuator to expose health, info, and prometheus endpoints.
6. Configure liveness and readiness probes for Kubernetes readiness.
Open a PR.
```
</details>

---

### 3.7 Add Business Metrics (GAP-OBS-04)

**Severity:** Low | **Effort:** Medium

<details>
<summary>Sample Devin Prompt</summary>

```
Add custom Micrometer metrics for key business operations:
1. Add micrometer-registry-prometheus dependency to all services.
2. core-banking-service: add counters for fund_transfer_total,
   utility_payment_total, and a timer for transaction_duration.
3. internet-banking-user-service: add counter for user_registration_total
   with tags for status (success, failure, duplicate).
4. Configure /actuator/prometheus endpoint in all services.
5. Add a Prometheus scrape configuration example to docker-compose.
Open a PR.
```
</details>

---

### 3.8 Configure Tracing Sampling (GAP-OBS-05)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Add explicit tracing sampling configuration to all services:
1. Set management.tracing.sampling.probability=1.0 in default profile.
2. Set management.tracing.sampling.probability=0.1 in a production
   profile.
3. Document the sampling configuration in the README.
Open a PR.
```
</details>

---

### 3.9 Document CSRF Disable Decision (GAP-SEC-03)

**Severity:** Low | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Add a code comment in internet-banking-api-gateway SecurityConfiguration
explaining why CSRF is disabled: the API is stateless with JWT-based
authentication, so CSRF protection is not needed and would break
non-browser API clients. Open a PR.
```
</details>

---

### 3.10 Add Contract Tests (GAP-TST-03)

**Severity:** Medium | **Effort:** Large

<details>
<summary>Sample Devin Prompt</summary>

```
Add Spring Cloud Contract tests between services:
1. Add spring-cloud-starter-contract-verifier to core-banking-service
   (producer side).
2. Define contracts for the Feign client interfaces:
   - GET /api/v1/account/bank-account/{account_number}
   - GET /api/v1/user/{identification}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Add spring-cloud-starter-contract-stub-runner to consumer services
   (user-service, fund-transfer-service, utility-payment-service).
4. Write consumer-side tests that verify Feign clients against the
   generated stubs.
Open a PR.
```
</details>

---

### 3.11 Add Consistent Logging Standards (GAP-OBS-01)

**Severity:** Medium | **Effort:** Small

<details>
<summary>Sample Devin Prompt</summary>

```
Standardize logging across all services:
1. Fix the typo "utitlity" in AccountController log message.
2. Ensure all controller methods log at DEBUG level for request entry.
3. Log business events (transfer completed, payment processed, user
   registered) at INFO level.
4. Log all exceptions in GlobalExceptionHandler at ERROR level with
   full stack trace.
5. Remove toString() calls on User objects that would log passwords.
6. Use MDC to include requestId in all log messages.
Open a PR.
```
</details>

---

## Priority Summary

| Phase | Items | Key Outcomes |
|-------|-------|-------------|
| **Phase 1** | 11 items | Fix critical bugs (balance calc, stack trace leak), add input validation, proper HTTP codes, timeouts, retries, externalize secrets |
| **Phase 2** | 11 items | Circuit breakers, comprehensive tests, idempotency, shared library, structured logging, OpenAPI fixes |
| **Phase 3** | 11 items | Code consistency, documentation, health checks, metrics, contract tests, tracing config |

### Recommended Execution Order Within Phase 1

1. **GAP-RES-07** — Balance bug (financial data integrity)
2. **GAP-ERR-02** — Stack trace leak (security)
3. **GAP-SEC-02** — Input validation (security)
4. **GAP-SEC-01** — Externalize credentials (security)
5. **GAP-RES-06** — Fix @OneToOne mapping (data integrity)
6. **GAP-ERR-01** — HTTP status codes (API correctness)
7. **GAP-RES-03** — Timeouts (resilience)
8. **GAP-RES-02** — Retries (resilience)
9. **GAP-ERR-05** — Feign error decoders (error handling)
10. **GAP-SEC-04** — Keycloak thread safety (reliability)
11. **GAP-TST-04** — JaCoCo coverage (visibility)
