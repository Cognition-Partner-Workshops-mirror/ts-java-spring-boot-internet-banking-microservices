# Remediation Roadmap

## Overview

This roadmap prioritizes the 39 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

| Phase | Focus | Timeline Estimate | Items |
|-------|-------|-------------------|-------|
| **Phase 1** | Quick Wins — Critical bugs and security issues that are small effort | 1–2 sprints | 13 items |
| **Phase 2** | Important — Structural improvements with medium effort | 3–5 sprints | 15 items |
| **Phase 3** | Polish — Nice-to-haves and long-term quality investments | Ongoing | 11 items |

---

## Phase 1: Quick Wins

High-impact fixes that are small effort — address critical bugs, security holes, and foundational error handling.

---

### 1.1 Fix Available Balance Double-Subtraction Bug (GAP-RE-08)

**Severity:** High | **Effort:** Small | **Services:** `core-banking-service`

The `availableBalance` is subtracted twice due to a code bug in `TransactionService`.

**Devin Prompt:**
> In the `core-banking-service`, fix the double-subtraction bug in `TransactionService.java`. In both the `internalFundTransfer()` and `utilPayment()` methods, the line `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount))` should be changed to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())` since `actualBalance` has already been reduced. Similarly fix the credit side in `internalFundTransfer()` for `toBankAccountEntity`. Add unit tests to verify correct balance calculations. Open a PR with the fix.

---

### 1.2 Add Input Validation to All Request DTOs (GAP-SE-02)

**Severity:** Critical | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across the codebase. Specifically:
> - `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`
> - `UtilityPaymentRequest`: `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`
> - `User` (registration): `@NotBlank @Email` on `email`, `@NotBlank` on `identification` and `password`
> - `UserUpdateRequest`: `@NotNull` on `status`
>
> Add `@Valid` annotations on all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency to each service's `build.gradle` if not already present. Add unit tests for validation. Open a PR.

---

### 1.3 Add Global Exception Handler to Core Banking Service (GAP-EH-03)

**Severity:** High | **Effort:** Small | **Services:** `core-banking-service`

**Devin Prompt:**
> Add a `@ControllerAdvice` class `GlobalExceptionHandler` to `core-banking-service` in the `exception` package. Handle:
> - `EntityNotFoundException` → 404 Not Found with `ErrorResponse{code, message}`
> - `InsufficientFundsException` → 422 Unprocessable Entity with `ErrorResponse`
> - `MethodArgumentNotValidException` → 400 Bad Request with field error details
> - `Exception` (fallback) → 500 Internal Server Error with generic message (never expose stack trace)
>
> Create an `ErrorResponse` DTO with `code`, `message`, and optional `fieldErrors` list. Add unit tests. Open a PR.

---

### 1.4 Add Global Exception Handler to Utility Payment Service (GAP-EH-04)

**Severity:** Medium | **Effort:** Small | **Services:** `internet-banking-utility-payment-service`

**Devin Prompt:**
> Add exception handling to `internet-banking-utility-payment-service`:
> 1. Create `SimpleBankingGlobalException`, `ErrorResponse`, and `GlobalExceptionHandler` classes following the same pattern used in `internet-banking-fund-transfer-service`
> 2. Handle `SimpleBankingGlobalException` → 400, `Exception` → 500 (generic message, no stack trace)
> 3. Add unit tests for the exception handler
> 4. Open a PR

---

### 1.5 Fix HTTP Status Codes in Exception Handlers (GAP-EH-02)

**Severity:** High | **Effort:** Small | **Services:** `user-service`, `fund-transfer-service`

**Devin Prompt:**
> Update the `GlobalExceptionHandler` in both `internet-banking-user-service` and `internet-banking-fund-transfer-service`:
> - `EntityNotFoundException` → return `404 Not Found` instead of `400 Bad Request`
> - Generic `Exception` handler → return `500 Internal Server Error` with a generic message like `{"code": "INTERNAL_ERROR", "message": "An unexpected error occurred"}` — never include `e.toString()` or stack trace
> - Add handler for `MethodArgumentNotValidException` → return `400 Bad Request` with field-level error details
> - Add unit tests verifying correct HTTP status codes for each exception type
> Open a PR.

---

### 1.6 Remove Hardcoded Credentials from Repository (GAP-SE-01)

**Severity:** Critical | **Effort:** Small | **Services:** Infrastructure

**Devin Prompt:**
> Remove all hardcoded credentials from the repository:
> 1. Create a `.env.example` file in `docker-compose/` with placeholder values for `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_PASSWORD`, `KC_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`
> 2. Update `docker-compose/docker-compose.yml` and `docker-compose-support-apps.yml` to use `${VARIABLE}` references from `.env`
> 3. Update `docker-compose/mysql/Dockerfile` to use `ARG`/`ENV` for root password
> 4. Update `docker-compose/mysql/privileges.sql` to use a variable or documented placeholder
> 5. Add `.env` to `.gitignore`
> 6. Remove the hardcoded bearer token from `postman_collection/JAVA_TO_DEV_MICROSERVICES.postman_collection.json` (replace with placeholder)
> 7. Add a note in README about creating `.env` from `.env.example`
> Open a PR.

---

### 1.7 Add Feign Error Decoders to Fund Transfer and Utility Payment Services (GAP-EH-05)

**Severity:** Medium | **Effort:** Small | **Services:** `fund-transfer-service`, `utility-payment-service`

**Devin Prompt:**
> Add custom Feign `ErrorDecoder` implementations to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, following the pattern in `internet-banking-user-service`'s `CustomFeignErrorDecoder`:
> 1. Create `CustomFeignErrorDecoder` in each service's configuration package
> 2. Map HTTP 400 → extract error body into `SimpleBankingGlobalException`
> 3. Map HTTP 404 → `EntityNotFoundException`
> 4. Map other statuses → generic exception with context
> 5. Register the decoder in each service's `CustomFeignClientConfiguration`
> 6. Add unit tests for the error decoders
> Open a PR.

---

### 1.8 Configure Feign Client Timeouts (GAP-RE-03)

**Severity:** High | **Effort:** Small | **Services:** `user-service`, `fund-transfer-service`, `utility-payment-service`

**Devin Prompt:**
> Add explicit timeout configuration for all Feign clients across the three services that use them (`internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`). In each service's config (via Config Server or local `application.yml`):
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
> Also add Gateway route-level timeouts in the API Gateway configuration. Open a PR.

---

### 1.9 Fix Logging Anti-Patterns (GAP-OB-01)

**Severity:** Medium | **Effort:** Small | **Services:** All

**Devin Prompt:**
> Fix logging issues across all services:
> 1. Replace string concatenation with parameterized logging: change `log.info("message {}" + var)` to `log.info("message {}", var)`
> 2. Fix exception logging to include stack traces: change `log.error("message" + e)` to `log.error("message", e)`
> 3. Remove logging of sensitive data: in `UserController.createUser()`, replace `request.toString()` with `request.getEmail()` (do not log passwords)
> 4. In `FundTransferService.fundTransfer()`, fix the logging pattern: `log.info("Sending fund transfer request {}" + request.toString())` should be `log.info("Sending fund transfer request {}", request)`
> Open a PR.

---

### 1.10 Fix Keycloak Singleton Thread Safety (GAP-SE-06)

**Severity:** Medium | **Effort:** Small | **Services:** `user-service`

**Devin Prompt:**
> In `internet-banking-user-service`, refactor `KeycloakProperties.getInstance()` to be thread-safe. Replace the manual lazy singleton pattern with a Spring `@Bean` method:
> 1. Move the Keycloak instance creation into a `@Configuration` class as a `@Bean` method
> 2. Remove the `static` field and `getInstance()` method from `KeycloakProperties`
> 3. Inject the `Keycloak` bean into `KeycloakManager` via constructor injection
> 4. Add a unit test verifying the bean is created correctly
> Open a PR.

---

### 1.11 Add JaCoCo Test Coverage Reporting (GAP-TE-05)

**Severity:** Medium | **Effort:** Small | **Services:** All

**Devin Prompt:**
> Add JaCoCo test coverage reporting to all 7 services:
> 1. Add the `jacoco` plugin to each service's `build.gradle`
> 2. Configure `jacocoTestReport` to generate HTML and XML reports
> 3. Configure `jacocoTestCoverageVerification` with a minimum line coverage of 50% (to be increased later)
> 4. Add `check.dependsOn jacocoTestCoverageVerification` to enforce at build time
> 5. Add `build/reports/` to `.gitignore`
> Open a PR.

---

### 1.12 Restrict Actuator Endpoints (GAP-OB-05)

**Severity:** Medium | **Effort:** Small | **Services:** `api-gateway`

**Devin Prompt:**
> Update the API Gateway's `SecurityConfiguration` to restrict actuator endpoint access:
> 1. Only permit `/actuator/health` and `/actuator/info` without authentication
> 2. Require authentication for all other actuator endpoints (`/actuator/env`, `/actuator/configprops`, `/actuator/beans`, etc.)
> 3. Update the gateway configuration to only expose `health` and `info` endpoints by default: `management.endpoints.web.exposure.include=health,info`
> 4. Add the same exposure restriction to each downstream service's configuration
> Open a PR.

---

### 1.13 Add Typed ResponseEntity to Controllers (GAP-EH-06)

**Severity:** Low | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Update all controller methods across `core-banking-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service` to use typed `ResponseEntity<T>` instead of raw `ResponseEntity`. For example:
> - `public ResponseEntity sendFundTransfer(...)` → `public ResponseEntity<FundTransferResponse> sendFundTransfer(...)`
> - `public ResponseEntity readFundTransfers(...)` → `public ResponseEntity<List<FundTransfer>> readFundTransfers(...)`
> Ensure all controller methods specify the concrete return type. Open a PR.

---

## Phase 2: Important

Structural improvements requiring more effort — resilience patterns, testing foundation, security hardening, and code organization.

---

### 2.1 Add Circuit Breakers to All Feign Clients (GAP-RE-01)

**Severity:** High | **Effort:** Medium | **Services:** `user-service`, `fund-transfer-service`, `utility-payment-service`

**Devin Prompt:**
> Add Resilience4j circuit breakers to all Feign clients:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to `user-service`, `fund-transfer-service`, and `utility-payment-service`
> 2. Enable circuit breaker integration with Feign: `spring.cloud.openfeign.circuitbreaker.enabled=true`
> 3. Create fallback classes for each Feign client that return appropriate error responses
> 4. Configure circuit breaker parameters:
>    ```yaml
>    resilience4j:
>      circuitbreaker:
>        instances:
>          core-banking-service:
>            sliding-window-size: 10
>            failure-rate-threshold: 50
>            wait-duration-in-open-state: 30s
>            permitted-number-of-calls-in-half-open-state: 5
>    ```
> 5. Add unit tests for fallback behavior
> Open a PR.

---

### 2.2 Add Retry Policies for Idempotent Operations (GAP-RE-02)

**Severity:** High | **Effort:** Small | **Services:** `user-service`, `fund-transfer-service`, `utility-payment-service`

**Devin Prompt:**
> Add Resilience4j retry policies for GET (idempotent) Feign operations:
> 1. Add `resilience4j-spring-boot3` and `resilience4j-feign` dependencies
> 2. Configure retry for GET operations only:
>    ```yaml
>    resilience4j:
>      retry:
>        instances:
>          core-banking-service:
>            max-attempts: 3
>            wait-duration: 500ms
>            exponential-backoff-multiplier: 2
>            retry-exceptions:
>              - java.io.IOException
>              - feign.RetryableException
>    ```
> 3. Ensure POST operations (fund transfer, utility payment) are NOT retried (to prevent double-execution)
> 4. Add integration tests verifying retry behavior
> Open a PR.

---

### 2.3 Implement Idempotent Fund Transfers (GAP-RE-05)

**Severity:** High | **Effort:** Medium | **Services:** `fund-transfer-service`, `core-banking-service`

**Devin Prompt:**
> Make fund transfer operations idempotent:
> 1. Add an `idempotencyKey` field to `FundTransferRequest` (UUID, required, validated with `@NotBlank`)
> 2. In `FundTransferService.fundTransfer()`, check if a `FundTransferEntity` with the same `idempotencyKey` already exists
>    - If found and `status=SUCCESS`, return the existing response
>    - If found and `status=PENDING/PROCESSING`, return 409 Conflict
>    - If not found, proceed with transfer
> 3. Add a unique constraint on `idempotency_key` column in the `fund_transfer` table
> 4. Apply the same pattern to `UtilityPaymentService`
> 5. Add unit and integration tests for idempotency
> Open a PR.

---

### 2.4 Fix Balance Update Race Condition (GAP-RE-07)

**Severity:** High | **Effort:** Medium | **Services:** `core-banking-service`

**Devin Prompt:**
> Fix the concurrent balance update race condition in `core-banking-service`:
> 1. Add `@Version` annotation to `BankAccountEntity` (for optimistic locking)
> 2. Alternatively (preferred for financial transactions): add `@Lock(LockModeType.PESSIMISTIC_WRITE)` to the `findByNumber` method in `BankAccountRepository`
> 3. Add a custom repository method that does atomic balance updates: `@Modifying @Query("UPDATE BankAccountEntity b SET b.actualBalance = b.actualBalance - :amount, b.availableBalance = b.availableBalance - :amount WHERE b.number = :number AND b.actualBalance >= :amount")`
> 4. Handle `OptimisticLockException` / `PessimisticLockException` in the service layer with appropriate error responses
> 5. Add concurrent test cases to verify that simultaneous transfers from the same account are handled correctly
> Open a PR.

---

### 2.5 Add Role-Based Authorization (GAP-SE-03)

**Severity:** High | **Effort:** Medium | **Services:** `api-gateway`, `user-service`

**Devin Prompt:**
> Implement role-based access control:
> 1. Define roles in Keycloak: `ROLE_USER`, `ROLE_ADMIN` (update `realm-export.json`)
> 2. Update the API Gateway's `SecurityConfiguration` to enforce roles:
>    - `PATCH /user/api/v1/bank-users/update/**` → require `ROLE_ADMIN`
>    - `GET /user/api/v1/bank-users` (list all) → require `ROLE_ADMIN`
>    - `POST /fund-transfer/api/v1/transfer` → require `ROLE_USER`
>    - `POST /utility-payment/api/v1/utility-payment` → require `ROLE_USER`
> 3. Add ownership validation in `fund-transfer-service`: verify the `X-Auth-Id` corresponds to the account owner before allowing a transfer
> 4. Extract roles from the JWT claims in the gateway filter
> 5. Add integration tests with mock JWT tokens for each role
> Open a PR.

---

### 2.6 Secure Internal Services (GAP-SE-04)

**Severity:** High | **Effort:** Medium | **Services:** `core-banking-service`, Docker Compose

**Devin Prompt:**
> Secure internal service communication:
> 1. Remove published ports for `core-banking-service` from `docker-compose.yml` (only expose via internal Docker network)
> 2. Add `spring-boot-starter-security` to `core-banking-service`
> 3. Configure it to accept requests only from the internal network or with a shared API key:
>    - Option A: Add a simple API key filter that checks for `X-Internal-Api-Key` header
>    - Option B: Configure IP-based allowlisting for the Docker subnet
> 4. Propagate the API key from upstream services via Feign request interceptors
> 5. Add tests verifying unauthorized direct access is rejected
> Open a PR.

---

### 2.7 Create Shared Commons Library (GAP-CO-02)

**Severity:** Medium | **Effort:** Medium | **Services:** New `banking-commons` module

**Devin Prompt:**
> Create a shared `banking-commons` library to eliminate code duplication:
> 1. Create a new Gradle module `banking-commons` with the shared code
> 2. Move into it:
>    - `BaseMapper<E, D>` abstract class
>    - `AuditAware` MappedSuperclass
>    - `AuditConfig` + `AuditorAwareConfig`
>    - `ApiRequestContext` + `ApiRequestContextHolder` + `AppAuthUserFilter`
>    - `SimpleBankingGlobalException` + `ErrorResponse` + `GlobalExceptionHandler`
> 3. Publish as a local dependency and add it to each service's `build.gradle`
> 4. Remove the duplicated classes from each service
> 5. Verify all services build and tests pass
> Open a PR.

---

### 2.8 Set Up Multi-Module Gradle Build (GAP-CO-01)

**Severity:** Medium | **Effort:** Medium | **Services:** Root project

**Devin Prompt:**
> Convert the project to a multi-module Gradle build:
> 1. Create a root `settings.gradle.kts` that includes all 7 service modules plus `banking-commons`
> 2. Create a root `build.gradle.kts` with shared configuration:
>    - Shared Java 21 toolchain configuration
>    - Shared Spring Boot and Spring Cloud BOMs
>    - Common dependency versions via a Gradle version catalog (`gradle/libs.versions.toml`)
> 3. Simplify each service's `build.gradle` to only declare service-specific dependencies
> 4. Remove individual `gradlew` wrappers — use only the root wrapper
> 5. Verify `./gradlew build` from root builds all modules
> Open a PR.

---

### 2.9 Add Unit Tests for User Service (GAP-TE-01 partial)

**Severity:** Critical | **Effort:** Medium | **Services:** `internet-banking-user-service`

**Devin Prompt:**
> Add comprehensive unit tests for `internet-banking-user-service`:
> 1. `UserServiceTest`: Test `createUser()` — happy path, duplicate email, email mismatch, core banking user not found, Keycloak 409
> 2. `UserServiceTest`: Test `updateUser()` — approve flow (Keycloak enable), status transitions
> 3. `UserServiceTest`: Test `readUsers()` — pagination, Keycloak enrichment
> 4. `KeycloakUserServiceTest`: Mock `KeycloakManager` and test create/update/read operations
> 5. `UserControllerTest`: Use `@WebMvcTest` with MockMvc to test each endpoint
> 6. Target: >80% line coverage on service and controller layers
> Use Mockito for mocking; add test dependencies if needed. Open a PR.

---

### 2.10 Add Unit Tests for Fund Transfer Service (GAP-TE-01 partial)

**Severity:** Critical | **Effort:** Medium | **Services:** `internet-banking-fund-transfer-service`

**Devin Prompt:**
> Add comprehensive unit tests for `internet-banking-fund-transfer-service`:
> 1. `FundTransferServiceTest`: Test `fundTransfer()` — happy path, Feign failure (core banking down), save PENDING/SUCCESS transitions
> 2. `FundTransferServiceTest`: Test `readAllTransfers()` — pagination, empty results
> 3. `FundTransferControllerTest`: Use `@WebMvcTest` with MockMvc to test POST and GET endpoints, validation errors
> 4. Mock `BankingCoreFeignClient` in all tests
> 5. Target: >80% line coverage
> Open a PR.

---

### 2.11 Add Unit Tests for Utility Payment Service (GAP-TE-01 partial)

**Severity:** Critical | **Effort:** Medium | **Services:** `internet-banking-utility-payment-service`

**Devin Prompt:**
> Add comprehensive unit tests for `internet-banking-utility-payment-service`:
> 1. `UtilityPaymentServiceTest`: Test `utilPayment()` — happy path, Feign failure, insufficient funds via error decoder
> 2. `UtilityPaymentServiceTest`: Test `readPayments()` — pagination, empty results
> 3. `UtilityPaymentControllerTest`: Use `@WebMvcTest` with MockMvc to test POST and GET endpoints
> 4. Mock `BankingCoreRestClient` in all tests
> 5. Target: >80% line coverage
> Open a PR.

---

### 2.12 Add Pagination Metadata to List Responses (GAP-AD-02)

**Severity:** Medium | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Update all list endpoints to return pagination metadata instead of plain lists:
> 1. Create a generic `PagedResponse<T>` wrapper in `banking-commons`:
>    ```java
>    public class PagedResponse<T> {
>        private List<T> content;
>        private int page;
>        private int size;
>        private long totalElements;
>        private int totalPages;
>        private boolean last;
>    }
>    ```
> 2. Update service methods to return `Page<T>` from Spring Data
> 3. Update controllers to map `Page<T>` → `PagedResponse<T>`
> 4. Update existing tests to verify pagination metadata
> Open a PR.

---

### 2.13 Add Custom Health Checks (GAP-OB-02)

**Severity:** Medium | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Add custom Spring Boot health indicators to each service:
> 1. `core-banking-service`: Add `DatabaseHealthIndicator` checking MySQL connectivity
> 2. `user-service`: Add `KeycloakHealthIndicator` pinging Keycloak's health endpoint
> 3. `fund-transfer-service`: Add `CoreBankingHealthIndicator` checking if core-banking-service is reachable via Eureka
> 4. `utility-payment-service`: Same as fund-transfer-service
> 5. Configure `management.endpoint.health.show-details=when-authorized` in each service
> 6. Add tests for each health indicator
> Open a PR.

---

### 2.14 Add Correlation ID to Responses (GAP-OB-04)

**Severity:** Medium | **Effort:** Small | **Services:** `api-gateway`

**Devin Prompt:**
> Add correlation ID propagation via the API Gateway:
> 1. In `GatewayConfiguration`, update the `GlobalFilter` to:
>    - Check for incoming `X-Request-Id` header; if absent, generate a UUID
>    - Add the `X-Request-Id` to the downstream request headers
>    - Add the `X-Request-Id` to the response headers
> 2. Configure Sleuth/Micrometer to use the same correlation ID as the trace ID
> 3. Update downstream services to include the correlation ID in log patterns (MDC)
> 4. Add integration tests verifying the header is present in responses
> Open a PR.

---

### 2.15 Add Dependency Vulnerability Scanning (GAP-SE-07)

**Severity:** Medium | **Effort:** Small | **Services:** Root build

**Devin Prompt:**
> Add OWASP Dependency Check to the Gradle build:
> 1. Add the `org.owasp.dependencycheck` plugin to the root `build.gradle` (or each service if multi-module isn't set up yet)
> 2. Configure it to fail the build on CVSS score >= 7 (High/Critical)
> 3. Add a suppression file for known false positives
> 4. Run `./gradlew dependencyCheckAnalyze` and document any findings
> 5. Add to CI pipeline if one exists
> Open a PR.

---

## Phase 3: Polish

Long-term quality improvements, advanced patterns, and developer experience enhancements.

---

### 3.1 Add Integration Tests with Test Containers (GAP-TE-02)

**Severity:** High | **Effort:** Large | **Services:** All business services

**Devin Prompt:**
> Add Testcontainers-based integration tests:
> 1. Add `testcontainers` and `testcontainers-mysql` dependencies to services that use MySQL
> 2. Create a base test class `AbstractIntegrationTest` with `@Testcontainers` and MySQL container setup
> 3. Write integration tests for:
>    - `core-banking-service`: Full flow — create user, create account, fund transfer, verify balances
>    - `fund-transfer-service`: POST transfer with WireMock standing in for core-banking-service
>    - `user-service`: POST register with WireMock for core-banking and mock Keycloak
> 4. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`
> 5. Ensure Flyway migrations run against the test MySQL container
> Open a PR.

---

### 3.2 Add Contract Tests for Feign Clients (GAP-TE-03)

**Severity:** High | **Effort:** Large | **Services:** All Feign consumer services

**Devin Prompt:**
> Add Spring Cloud Contract or Pact consumer-driven contract tests:
> 1. Add `spring-cloud-contract-wiremock` and `spring-cloud-contract-stub-runner` dependencies
> 2. Create contract stubs for `core-banking-service`'s API
> 3. Write consumer tests in `fund-transfer-service`, `user-service`, and `utility-payment-service` that:
>    - Start a WireMock server with the contract stubs
>    - Execute Feign client calls and verify responses match expectations
> 4. Create provider verification tests in `core-banking-service` that validate the same contracts
> 5. Document the contract testing workflow in CONTRIBUTING.md
> Open a PR.

---

### 3.3 Add Prometheus Metrics and Business KPIs (GAP-OB-03)

**Severity:** Medium | **Effort:** Medium | **Services:** All

**Devin Prompt:**
> Add Prometheus metrics collection:
> 1. Add `micrometer-registry-prometheus` dependency to all services
> 2. Expose `/actuator/prometheus` endpoint (restricted to authorized access)
> 3. Add custom business metrics in each service:
>    - `fund_transfers_total` (counter, tags: status=SUCCESS/FAILED)
>    - `fund_transfer_amount_total` (counter of monetary amounts)
>    - `utility_payments_total` (counter, tags: status, provider)
>    - `user_registrations_total` (counter, tags: status)
> 4. Add a `docker-compose` service for Prometheus with a basic scrape config
> 5. Optionally add a Grafana service with a pre-built dashboard JSON
> Open a PR.

---

### 3.4 Standardize Package Structure Across Services (GAP-CO-03)

**Severity:** Low | **Effort:** Small | **Services:** All

**Devin Prompt:**
> Standardize the package structure across all services to follow this convention:
> ```
> com.javatodev.finance
> ├── config/          (Spring @Configuration classes)
> │   ├── audit/
> │   ├── feign/
> │   ├── filter/
> │   └── security/
> ├── controller/      (REST controllers)
> ├── exception/       (Exception classes + handler)
> ├── model/
> │   ├── dto/         (Request/Response DTOs)
> │   ├── entity/      (JPA entities)
> │   └── mapper/      (Entity-DTO mappers)
> ├── repository/      (Spring Data repositories)
> ├── service/         (Business logic)
> └── client/          (Feign clients)
> ```
> Refactor each service to match. Update imports throughout. Verify all tests pass. Open a PR.

---

### 3.5 Replace Manual Mappers with MapStruct (GAP-CO-04)

**Severity:** Low | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Replace the manual `BaseMapper` implementations with MapStruct:
> 1. Add `mapstruct` and `mapstruct-processor` dependencies (with annotation processor config) to each service's `build.gradle`
> 2. Create MapStruct `@Mapper` interfaces for each entity-DTO pair:
>    - `UserMapper`, `BankAccountMapper`, `TransactionMapper`, `UtilityAccountMapper`
>    - `FundTransferMapper`, `UtilityPaymentMapper`
> 3. Configure `componentModel = "spring"` so mappers are injectable beans
> 4. Remove the old `BaseMapper` abstract class and all manual mapper implementations
> 5. Verify all existing tests still pass
> Open a PR.

---

### 3.6 Add Rate Limiting at the API Gateway (GAP-RE-06)

**Severity:** Medium | **Effort:** Small | **Services:** `api-gateway`

**Devin Prompt:**
> Add rate limiting to the API Gateway:
> 1. Add `spring-boot-starter-data-redis-reactive` dependency to the gateway
> 2. Add a Redis container to `docker-compose.yml`
> 3. Configure `RequestRateLimiter` filter on sensitive routes:
>    ```yaml
>    spring.cloud.gateway.routes:
>      - id: fund-transfer
>        uri: lb://internet-banking-fund-transfer-service
>        predicates:
>          - Path=/fund-transfer/**
>        filters:
>          - name: RequestRateLimiter
>            args:
>              redis-rate-limiter.replenishRate: 10
>              redis-rate-limiter.burstCapacity: 20
>    ```
> 4. Implement a `KeyResolver` that extracts the user ID from the JWT
> 5. Add tests verifying rate limiting works
> Open a PR.

---

### 3.7 Add Fallback Methods for Feign Clients (GAP-RE-04)

**Severity:** Medium | **Effort:** Medium | **Services:** All Feign consumer services

**Devin Prompt:**
> Add fallback implementations for all Feign clients:
> 1. Create `BankingCoreFeignClientFallback` in `fund-transfer-service` implementing `BankingCoreFeignClient`:
>    - `readAccount()` → return cached account if available, or throw `ServiceUnavailableException`
>    - `fundTransfer()` → throw `ServiceUnavailableException` with message "Core banking service is currently unavailable"
> 2. Create similar fallbacks in `user-service` and `utility-payment-service`
> 3. Register fallbacks via `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)`
> 4. Add unit tests for each fallback
> Open a PR.

---

### 3.8 Complete OpenAPI Documentation (GAP-AD-06)

**Severity:** Low | **Effort:** Small | **Services:** All business services

**Devin Prompt:**
> Enhance OpenAPI/Swagger annotations across all services:
> 1. Add `@ApiResponse` annotations for all HTTP status codes each endpoint can return (200, 400, 404, 422, 500)
> 2. Add `@Schema` annotations to all DTOs with descriptions and examples
> 3. Add `@Parameter` annotations for path variables and query parameters
> 4. Add `@SecurityRequirement` annotations to indicate OAuth2 is required
> 5. Configure a global OpenAPI info bean with title, version, description, and contact info
> 6. Verify Swagger UI is accessible and documentation is correct
> Open a PR.

---

### 3.9 Add Filtering and Sorting to List Endpoints (GAP-AD-04)

**Severity:** Low | **Effort:** Medium | **Services:** All business services

**Devin Prompt:**
> Add filtering and sorting to list endpoints:
> 1. `GET /api/v1/transfer` — add filters: `?status=SUCCESS&fromDate=2024-01-01&toDate=2024-12-31&fromAccount=123`
> 2. `GET /api/v1/utility-payment` — add filters: `?status=SUCCESS&providerId=1`
> 3. `GET /api/v1/bank-users` — add filters: `?status=APPROVED&email=...`
> 4. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering
> 5. Support sort parameter: `?sort=createdDate,desc`
> 6. Add tests for each filter combination
> Open a PR.

---

### 3.10 Document API Versioning Strategy (GAP-AD-03)

**Severity:** Medium | **Effort:** Medium | **Services:** Documentation + API Gateway

**Devin Prompt:**
> Document and implement a formal API versioning strategy:
> 1. Create `docs/API_VERSIONING.md` documenting:
>    - URL-based versioning (current: `/api/v1/`) is the chosen strategy
>    - What constitutes a breaking change vs non-breaking
>    - Deprecation policy (support N-1 for 6 months)
>    - How to introduce v2 endpoints
> 2. Add gateway route configuration that can handle multiple versions
> 3. Add version information to OpenAPI configuration
> Open a PR.

---

### 3.11 Fix Context Load Tests (GAP-TE-04)

**Severity:** Medium | **Effort:** Small | **Services:** All services with failing context tests

**Devin Prompt:**
> Fix the `@SpringBootTest` context load tests that fail without infrastructure:
> 1. Create `application-test.yml` in each service's `src/test/resources/` with:
>    - H2 in-memory database configuration
>    - Eureka client disabled: `eureka.client.enabled=false`
>    - Config server disabled: `spring.cloud.config.enabled=false`
>    - Keycloak mocked or disabled
> 2. Annotate context load tests with `@ActiveProfiles("test")`
> 3. For `user-service`: Mock `KeycloakManager` and `BankingCoreRestClient` beans with `@MockBean`
> 4. For `fund-transfer-service` and `utility-payment-service`: Mock Feign client beans
> 5. Verify all context load tests pass without any external services running
> Open a PR.

---

## Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| Phase 1 | 13 | 2 | 5 | 5 | 1 |
| Phase 2 | 15 | 3 | 5 | 6 | 1 |
| Phase 3 | 11 | 1 | 1 | 5 | 4 |
| **Total** | **39** | **6** | **11** | **16** | **6** |

### Recommended Execution Order Within Phase 1

Start with these first (blocking bugs and security):
1. **1.1** Fix balance double-subtraction bug (functional correctness)
2. **1.2** Add input validation (security)
3. **1.6** Remove hardcoded credentials (security)
4. **1.3** + **1.4** + **1.5** Error handling improvements (stability)
5. **1.8** Configure timeouts (resilience)
6. Remaining Phase 1 items in any order
