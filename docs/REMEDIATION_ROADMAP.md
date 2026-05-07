# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are prioritized into three phases:

- **Phase 1 — Quick Wins:** Critical/High severity items that are Small effort. Immediate safety and correctness fixes.
- **Phase 2 — Important:** Critical/High severity items that are Medium/Large effort, plus Medium severity items. Structural improvements.
- **Phase 3 — Polish:** Low severity items and nice-to-haves. Long-term quality improvements.

---

## Phase 1: Quick Wins (1-2 Weeks)

These items fix critical bugs, security holes, and correctness issues with minimal code changes.

### 1.1 Fix Double-Deduction Bug in Balance Calculation

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is being set to `actualBalance - amount` after `actualBalance` has already been reduced, causing a double deduction.

**Devin Prompt:**
> Fix the double-deduction bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In `internalFundTransfer()`, lines 90-91 and 99-100, and in `utilPayment()`, lines 63-64, `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been reduced. The `availableBalance` should be set to the same value as `actualBalance` after the deduction, not subtracted again. Fix all three occurrences and update the existing unit tests in `TransactionServiceTest.java` to verify the correct balance after each operation.

---

### 1.2 Fix Transaction Status Not Updated on Failure

**Gap:** 7.5 | **Severity:** Critical | **Effort:** Small

When Feign calls to Core Banking fail, fund transfer and utility payment entities remain stuck in `PENDING`/`PROCESSING` forever.

**Devin Prompt:**
> In `internet-banking-fund-transfer-service`, wrap the Feign call in `FundTransferService.fundTransfer()` with a try-catch. On exception, set `optFundTransfer.setStatus(TransactionStatus.FAILED)`, save the entity, and rethrow the exception. Apply the same pattern in `internet-banking-utility-payment-service/UtilityPaymentService.utilPayment()`. Add unit tests that mock a Feign failure and verify the entity is saved with `FAILED` status.

---

### 1.3 Fix Exception Handlers — Proper HTTP Status Codes

**Gap:** 2.1, 2.2 | **Severity:** Critical + High | **Effort:** Small

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in ALL four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to:
> 1. Return `404 Not Found` for `EntityNotFoundException` instead of `400`.
> 2. Return `400 Bad Request` for `SimpleBankingGlobalException` (keep current behavior).
> 3. Return `500 Internal Server Error` for the generic `Exception` catch-all, with a safe error message (do NOT expose `e.toString()` to clients — log it server-side only).
> 4. Use a consistent `ErrorResponse` body (`{code, message, timestamp}`) for all error responses, including the generic handler.
> Make sure each `GlobalExceptionHandler` has the same structure across all services.

---

### 1.4 Add Input Validation to All Request DTOs

**Gap:** 4.2 | **Severity:** Critical | **Effort:** Small-Medium

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across the project:
>
> **core-banking-service:**
> - `FundTransferRequest`: `@NotBlank fromAccount`, `@NotBlank toAccount`, `@NotNull @Positive amount`
> - `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank referenceNumber`, `@NotBlank account`
>
> **internet-banking-user-service:**
> - `User` (registration): `@NotBlank @Email email`, `@NotBlank password` (min length 8), `@NotBlank identification`
> - `UserUpdateRequest`: `@NotNull status`
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferRequest`: `@NotBlank fromAccount`, `@NotBlank toAccount`, `@NotNull @Positive amount`
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank referenceNumber`, `@NotBlank account`
>
> Add `@Valid` annotation on all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` to each service's `build.gradle` if not already present. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns `400` with field-level error details.

---

### 1.5 Configure Feign Timeouts

**Gap:** 7.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add Feign client timeout configuration to the `application.yml` (or config server properties) for internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Set connect timeout to 5 seconds and read timeout to 10 seconds. Use Spring Cloud OpenFeign's `spring.cloud.openfeign.client.config.default.connect-timeout` and `read-timeout` properties. Also add a connection pool configuration for the OkHttp Feign client in user-service.

---

### 1.6 Configure Feign Retry Policies

**Gap:** 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add a Spring Retry configuration for Feign clients in internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Add `spring-retry` dependency to each service's `build.gradle`. Configure a `Retryer` bean that retries up to 3 times with 1-second initial backoff and 2x multiplier, only for GET requests (do NOT retry POST/fund-transfer/payment requests to avoid duplicate transactions). Document the retry behavior in a code comment.

---

### 1.7 Externalize Hardcoded Credentials

**Gap:** 4.1 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
> Replace hardcoded passwords in `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` with environment variable references (`${MYSQL_ROOT_PASSWORD:-defaultPassword}`, `${KEYCLOAK_ADMIN_PASSWORD:-password}`, etc.). Create a `.env.example` file in the `docker-compose/` directory documenting all required environment variables with placeholder values. Remove the Keycloak client secret from `internet-banking-user-service/src/test/resources/application.yml` and replace it with a placeholder. Add `.env` to `.gitignore`.

---

### 1.8 Add Feign Error Decoder to All Services

**Gap:** 2.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> The User Service already has a `CustomFeignErrorDecoder` that properly extracts error responses from Feign failures. Port this same error decoder to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. In each service: (1) create a `CustomFeignErrorDecoder` class in the `configuration` package (copy from user-service, adapting package names), (2) register it as a bean in the existing `CustomFeignClientConfiguration` class. This ensures downstream Core Banking errors are properly propagated with their error codes and messages.

---

### 1.9 Fix OpenAPI Starter Dependency

**Gap:** 5.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In `build.gradle` for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. These services use Spring MVC (not WebFlux), so the WebFlux starter is incorrect. Also update all controller methods to use typed `ResponseEntity<T>` instead of raw `ResponseEntity` so Springdoc can generate accurate response schemas.

---

## Phase 2: Important (3-6 Weeks)

Structural improvements that require more significant effort.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap:** 7.1, 7.4 | **Severity:** Critical + High | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breaker support to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's `build.gradle`.
> 2. Enable Feign circuit breaker integration with `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's config.
> 3. Create fallback classes for each Feign client (e.g., `BankingCoreFeignClientFallback`) that return appropriate error responses or throw meaningful exceptions.
> 4. Configure circuit breaker defaults: failure rate threshold 50%, wait duration in open state 30s, sliding window size 10.
> 5. Add Resilience4j actuator endpoints for monitoring circuit breaker state.

---

### 2.2 Add Idempotency Protection for Financial Operations

**Gap:** 7.7 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Implement idempotency for fund transfer and utility payment endpoints:
> 1. Add an `X-Idempotency-Key` header requirement to `POST /api/v1/transfer` and `POST /api/v1/utility-payment`.
> 2. In each service, create an `idempotency_key` table (or column on the existing entity) with a unique constraint.
> 3. Before processing a request, check if the idempotency key already exists. If so, return the previous response.
> 4. Add a servlet filter or `HandlerInterceptor` that validates the idempotency key header is present on POST requests.
> 5. Add unit and integration tests for duplicate request handling.

---

### 2.3 Extract Shared Library Module

**Gap:** 1.2 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a shared library module (`banking-common`) that contains code duplicated across services:
> 1. Create a new directory `banking-common/` with its own `build.gradle` (as a plain Java library, not a Spring Boot application).
> 2. Move these classes into it: `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditConfig`, `AuditorAwareConfig`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `TransactionStatus`.
> 3. Create a root `settings.gradle` that includes `banking-common` and all service projects.
> 4. Update each service's `build.gradle` to depend on `banking-common` via `implementation project(':banking-common')`.
> 5. Remove the duplicated classes from each service.
> 6. Ensure all existing tests still pass.

---

### 2.4 Add Unit Tests for User, Fund Transfer, and Utility Payment Services

**Gap:** 3.1 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for the three services that currently have no meaningful test coverage:
>
> **internet-banking-user-service:**
> - `UserServiceTest`: Test `createUser()` (happy path, email already registered, user not found in core, email mismatch), `readUsers()`, `readUser()`, `updateUser()` (approve flow, entity not found). Mock `KeycloakUserService`, `BankingCoreRestClient`, and `UserRepository`.
> - `KeycloakUserServiceTest`: Test `createUser()`, `readUserByEmail()`, `readUser()` (found, not found). Mock `KeycloakManager`.
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferServiceTest`: Test `fundTransfer()` (happy path, core banking failure, repository save), `readAllTransfers()`. Mock `FundTransferRepository` and `BankingCoreFeignClient`.
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentServiceTest`: Test `utilPayment()` (happy path, core banking failure), `readPayments()`. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`.
>
> Target at minimum 80% line coverage on all service classes.

---

### 2.5 Add Rate Limiting to API Gateway

**Gap:** 4.3 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter:
> 1. Add `spring-boot-starter-data-redis-reactive` dependency.
> 2. Add Redis to `docker-compose.yml`.
> 3. Configure a `RedisRateLimiter` with `replenish-rate: 10` and `burst-capacity: 20` for general endpoints.
> 4. Apply stricter limits (replenish-rate: 2, burst-capacity: 5) for financial transaction endpoints (`/fund-transfer/**`, `/utility-payment/**`).
> 5. Configure a `KeyResolver` bean that resolves rate limit keys by JWT subject (authenticated user).
> 6. Return `429 Too Many Requests` when limit is exceeded.

---

### 2.6 Add Pagination Metadata to API Responses

**Gap:** 5.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Update all paginated endpoints across all services to return `Page<T>` (or a custom `PagedResponse<T>` wrapper) instead of bare `List<T>`:
> 1. In `core-banking-service/UserController.readUsers()`, return `ResponseEntity<Page<User>>`.
> 2. In `internet-banking-user-service/UserController.readUsers()`, return the page object with total count.
> 3. In `internet-banking-fund-transfer-service/FundTransferController.readFundTransfers()`, return `Page<FundTransfer>`.
> 4. In `internet-banking-utility-payment-service/UtilityPaymentController.readPayments()`, return `Page<UtilityPayment>`.
> Update the corresponding service methods to return `Page<T>` instead of `List<T>`.

---

### 2.7 Add Dependency Vulnerability Scanning

**Gap:** 4.5 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to each service's `build.gradle`:
> 1. Add `id 'org.owasp.dependencycheck' version '9.0.9'` to the plugins block.
> 2. Configure `dependencyCheck { failBuildOnCVSS = 7.0 }` to fail builds on high-severity CVEs.
> 3. Run `./gradlew dependencyCheckAnalyze` in each service and document any findings.
> 4. Create a GitHub Actions workflow (`.github/workflows/dependency-check.yml`) that runs the check on PRs.

---

### 2.8 Add Structured Logging

**Gap:** 6.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Configure structured JSON logging across all services:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`.
> 2. Create a shared `logback-spring.xml` configuration that outputs JSON in production profile and plain text in dev profile.
> 3. Fix string concatenation in log statements (e.g., `"Sending fund transfer request {}" + request.toString()` should be `"Sending fund transfer request {}", request`).
> 4. Ensure trace IDs from Micrometer/Brave are included in every log line.

---

### 2.9 Add Prometheus Metrics Endpoint

**Gap:** 6.3 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Enable Prometheus metrics endpoints in all services:
> 1. Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`.
> 2. Configure `management.endpoints.web.exposure.include=health,info,prometheus` in each service's config.
> 3. Add custom business metrics using Micrometer's `Counter` and `Timer`:
>    - `banking.fund_transfer.total` (counter, tagged by status)
>    - `banking.utility_payment.total` (counter, tagged by status)
>    - `banking.user_registration.total` (counter, tagged by status)
> 4. Add a Prometheus scrape config and optionally a basic Grafana dashboard JSON to the `docker-compose/` directory.

---

## Phase 3: Polish (6-12 Weeks)

Long-term quality improvements.

### 3.1 Add Integration and Contract Tests

**Gap:** 3.2, 3.3 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add integration tests using Testcontainers and Spring Cloud Contract:
>
> **Integration Tests (per service):**
> 1. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` to each service's test dependencies.
> 2. Write `@SpringBootTest` integration tests that spin up a MySQL container and test the full request-response cycle using `MockMvc` or `WebTestClient`.
> 3. For `internet-banking-user-service`, add a Keycloak testcontainer (`dasniko/testcontainers-keycloak`).
>
> **Contract Tests:**
> 1. Add Spring Cloud Contract Verifier to `core-banking-service` as the producer.
> 2. Define contracts for all endpoints consumed by downstream services.
> 3. Add Spring Cloud Contract Stub Runner to fund-transfer, utility-payment, and user services as consumers.
> 4. Verify contracts in CI.

---

### 3.2 Create Multi-Project Gradle Build

**Gap:** 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Convert the repository to a Gradle multi-project build:
> 1. Create a root `settings.gradle` that includes all 6 service projects plus the `banking-common` module.
> 2. Create a root `build.gradle` with shared configuration (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common test dependencies, common plugins).
> 3. Simplify each service's `build.gradle` to only declare service-specific dependencies.
> 4. Remove individual `gradle/wrapper` directories from each service (use root wrapper only).
> 5. Update Dockerfiles to reference the new build output paths.
> 6. Verify all services build and tests pass from the root: `./gradlew build`.

---

### 3.3 Standardize Package Structure

**Gap:** 1.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all business services to follow this convention:
> ```
> com.javatodev.finance
> ├── configuration/
> ├── controller/
> ├── exception/
> ├── model/
> │   ├── dto/
> │   │   ├── request/
> │   │   └── response/
> │   ├── entity/
> │   └── mapper/
> ├── repository/
> └── service/
>     └── rest/
> ```
> Move `internet-banking-user-service`'s `model.repository` to `repository`. Move `internet-banking-user-service` and `internet-banking-utility-payment-service`'s `model.rest.request/response` to `model.dto.request/response`. Update all imports. Verify all tests pass.

---

### 3.4 Standardize URL Naming Conventions

**Gap:** 5.2 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Standardize REST API URLs across all services to follow consistent conventions:
> 1. Use plural nouns for collection resources.
> 2. Remove verbs from URLs: change `/bank-users/update/{id}` to `PATCH /bank-users/{id}`.
> 3. Use consistent path variable naming: replace `{account_number}` with `{accountNumber}`, `{account_name}` with `{providerName}`.
> 4. Update the Postman collection in `postman_collection/` to match.
> 5. Update the API Gateway route configuration if needed.
> 6. Document the URL changes in a migration note.

---

### 3.5 Make Mappers Spring-Managed Beans

**Gap:** 1.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Convert all Mapper classes to Spring `@Component` beans instead of manual `new` instantiation:
> 1. Add `@Component` annotation to `BankAccountMapper`, `UserMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`.
> 2. In service classes, replace `private XMapper mapper = new XMapper()` with `private final XMapper mapper` injected via constructor (already `@RequiredArgsConstructor`).
> 3. Update unit tests to inject mock or real mapper instances.
> 4. Verify all tests pass.

---

### 3.6 Add Custom Health Indicators

**Gap:** 6.2 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Add custom health indicators to services that depend on external systems:
> 1. In `internet-banking-user-service`, create a `KeycloakHealthIndicator` that checks Keycloak connectivity via a lightweight API call.
> 2. In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, create a `CoreBankingHealthIndicator` that calls the core banking actuator health endpoint via Feign.
> 3. Register each as a Spring `@Component` implementing `HealthIndicator`.
> 4. Verify that `/actuator/health` includes the new indicators.

---

### 3.7 Verify and Configure Distributed Tracing

**Gap:** 6.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Verify and improve distributed tracing configuration:
> 1. Set `management.tracing.sampling.probability=1.0` in all services for development (override to 0.1 in production).
> 2. Add `@Observed` or `@NewSpan` annotations on key service methods (`fundTransfer`, `utilPayment`, `createUser`).
> 3. Verify that the trace ID propagates through Feign calls by writing an integration test that checks the Zipkin API for connected spans.
> 4. Add trace ID to the error response format so clients can reference it for support.

---

### 3.8 Add CI/CD Pipeline

**Gap:** Not in original gap list, but critical for sustainability | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline at `.github/workflows/ci.yml`:
> 1. Trigger on push to `main` and all PRs.
> 2. Steps: checkout, setup Java 21, run `./gradlew build` for each service (or root build if multi-project is done).
> 3. Run unit tests with coverage reporting (JaCoCo).
> 4. Run OWASP dependency check.
> 5. Build Docker images for each service.
> 6. Add a badge to `README.md`.
> Optionally add a separate deploy workflow for tagged releases.

---

## Summary Timeline

| Phase | Duration | Key Deliverables |
|---|---|---|
| **Phase 1** | Weeks 1-2 | Bug fixes (double-deduction, error handling), input validation, Feign timeouts/retries, credential externalization |
| **Phase 2** | Weeks 3-8 | Circuit breakers, idempotency, shared library, unit tests, rate limiting, structured logging, Prometheus |
| **Phase 3** | Weeks 9-16 | Integration/contract tests, multi-project build, URL standardization, health indicators, CI/CD pipeline |
