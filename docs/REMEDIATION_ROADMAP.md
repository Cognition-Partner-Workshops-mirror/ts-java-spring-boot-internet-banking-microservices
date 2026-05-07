# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort fixes that address critical bugs, security risks, and foundational issues.

### 1.1 Fix Available Balance Calculation Bug (RE-9)

**Severity:** Critical | **Effort:** Small

The `internalFundTransfer()` and `utilPayment()` methods in `TransactionService` double-subtract from `availableBalance` because they set `availableBalance = actualBalance - amount` after `actualBalance` was already reduced by `amount`.

**Sample Devin Prompt:**
> Fix the available balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, after updating `actualBalance`, the `availableBalance` should be set equal to the new `actualBalance` (not `actualBalance - amount` again). Update the existing unit tests in `TransactionServiceTest.java` to assert correct balance values after transfers and payments.

---

### 1.2 Fix HTTP Status Codes in Exception Handlers (EH-1)

**Severity:** Critical | **Effort:** Small

All exception handlers return HTTP 400 for every error type. `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, and unhandled exceptions should return 500.

**Sample Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all four services (`core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`) to return appropriate HTTP status codes: 404 for `EntityNotFoundException`, 422 for `InsufficientFundsException`, 400 for other `SimpleBankingGlobalException` subtypes, and 500 for unhandled `Exception`. Ensure the error response format is consistent across all services using `ErrorResponse { code, message }`.

---

### 1.3 Remove Internal Details from Error Responses (EH-2)

**Severity:** Critical | **Effort:** Small

The generic `Exception` handler returns `"Exception occur inside API " + e`, exposing stack traces to clients.

**Sample Devin Prompt:**
> In all four `GlobalExceptionHandler` classes, replace the catch-all `Exception` handler's response body from `"Exception occur inside API " + e` with a safe generic `ErrorResponse` object: `{ code: "INTERNAL_ERROR", message: "An unexpected error occurred. Please try again later." }`. Log the full exception at ERROR level for debugging purposes.

---

### 1.4 Add Input Validation to Request DTOs (SE-3)

**Severity:** High | **Effort:** Small

No request objects use Bean Validation. Null amounts, empty account numbers, and negative values are accepted.

**Sample Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across services. For `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`. For `UtilityPaymentRequest`: `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`. For User registration: `@NotBlank @Email` on `email`, `@NotBlank` on `identification` and `password`. Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details.

---

### 1.5 Stop Logging Sensitive Data (SE-8, OB-7)

**Severity:** High | **Effort:** Small

User registration logs `request.toString()` which includes plaintext passwords. Fund transfer logs expose full financial request details.

**Sample Devin Prompt:**
> Audit all `log.info()` calls across all services for sensitive data. In `UserController.createUser()`, change the log to only include the email address, never the password. Exclude the `password` field from `User.toString()` by adding `@ToString.Exclude` on the password field. In `FundTransferController` and `TransactionController`, log only account number suffixes (last 4 digits) and transaction IDs, not full account numbers or amounts. Review all other log statements and redact any sensitive financial or personal data.

---

### 1.6 Add Feign Timeout Configuration (RE-3)

**Severity:** Critical | **Effort:** Small

Feign clients have no timeout settings, risking thread pool exhaustion if a downstream service hangs.

**Sample Devin Prompt:**
> Add Feign client timeout configuration to all services that use Feign (`internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`). Set connect timeout to 5 seconds and read timeout to 10 seconds. This can be done via `application.yml` properties: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Alternatively, add this to the centralized config server's configuration files.

---

### 1.7 Add Prometheus Metrics Endpoint (OB-4)

**Severity:** High | **Effort:** Small

Prometheus is listed as a technology but the `micrometer-registry-prometheus` dependency is missing.

**Sample Devin Prompt:**
> Add the `micrometer-registry-prometheus` dependency to all six service `build.gradle` files: `implementation 'io.micrometer:micrometer-registry-prometheus'`. Ensure the `/actuator/prometheus` endpoint is exposed by adding `management.endpoints.web.exposure.include: health,info,prometheus,metrics` to each service's `application.yml` or to the centralized config server.

---

### 1.8 Fix Raw ResponseEntity Types (AD-1)

**Severity:** High | **Effort:** Small

Controllers return `ResponseEntity` without generic type parameters, breaking OpenAPI documentation.

**Sample Devin Prompt:**
> Add proper generic type parameters to all `ResponseEntity` return types in all controller classes. For example: `ResponseEntity<BankAccount>` instead of `ResponseEntity` in `AccountController.getBankAccount()`. Update all controllers in `core-banking-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. Ensure the Swagger dependency is corrected from `springdoc-openapi-starter-webflux-ui` to `springdoc-openapi-starter-webmvc-ui` in all services that use Spring MVC (all except the API Gateway).

---

### 1.9 Fix Keycloak Singleton Thread Safety (SE-6)

**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses an unsynchronized static singleton.

**Sample Devin Prompt:**
> Refactor `KeycloakProperties` in `internet-banking-user-service` to be thread-safe. Replace the manual singleton pattern with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance. Use `@PostConstruct` or `@Bean` lifecycle to ensure the Keycloak client is initialized once by Spring's container. Remove the static `keycloakInstance` field.

---

## Phase 2: Important Improvements (3-6 weeks)

Structural improvements that require more effort but significantly improve reliability, maintainability, and security.

### 2.1 Extract Shared Library (CO-1)

**Severity:** High | **Effort:** Large

Duplicated code across 4 services creates maintenance risk.

**Sample Devin Prompt:**
> Create a shared library module `internet-banking-common` as a Gradle subproject. Extract the following duplicated classes into it: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `TransactionStatus`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `CustomFeignClientConfiguration`, `AuditConfig`, `AuditorAwareConfig`. Set up a root `settings.gradle` that includes all service modules and the common module. Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. Remove the duplicated classes from each service. Verify all services still compile and tests pass.

---

### 2.2 Add Circuit Breakers (RE-1)

**Severity:** Critical | **Effort:** Medium

No circuit breakers on Feign calls. A core-banking outage cascades to all services.

**Sample Devin Prompt:**
> Add Resilience4j circuit breaker support to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add `spring-cloud-starter-circuitbreaker-resilience4j` and `resilience4j-spring-boot3` dependencies. Configure circuit breakers for each Feign client with: failure rate threshold 50%, slow call threshold 80%, wait duration in open state 30s, sliding window size 10. Add fallback methods that return appropriate error responses (e.g., "Core banking service is currently unavailable, please try again later") with HTTP 503 status. Implement Feign fallback factories for `BankingCoreFeignClient` in both services.

---

### 2.3 Add Unit Tests for All Services (TE-1)

**Severity:** Critical | **Effort:** Large

Only core-banking-service has meaningful unit tests. Three other business services have zero.

**Sample Devin Prompt:**
> Write comprehensive unit tests for `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. For each service, test: (1) the service layer with mocked repositories and Feign clients, covering happy path, validation errors, and downstream failures; (2) the controller layer using `@WebMvcTest` with mocked services. Target at least 80% line coverage for service and controller packages. Add JaCoCo plugin to all `build.gradle` files with a minimum coverage threshold of 70%. The user service should test: user creation (success, duplicate email, invalid email, user not found in core), user update (approve flow), read user. The fund-transfer service should test: transfer (success, core failure), read transfers. The utility-payment service should test: payment (success, core failure), read payments.

---

### 2.4 Secure Downstream Services (SE-1)

**Severity:** Critical | **Effort:** Medium

Business services accept all requests without authentication when accessed directly.

**Sample Devin Prompt:**
> Add OAuth2 resource server configuration to `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. Add `spring-boot-starter-oauth2-resource-server` dependency to each service. Create a `SecurityConfig` class that validates JWT tokens on all endpoints except actuator endpoints. Ensure the Feign clients propagate the JWT token from incoming requests to outgoing Feign calls using a `RequestInterceptor` that reads the `Authorization` header from the current request context. Update Docker compose environment to pass Keycloak JWT issuer URI to all services.

---

### 2.5 Externalize Secrets (SE-2)

**Severity:** Critical | **Effort:** Medium

Database passwords, Keycloak credentials, and test secrets are hardcoded in source-controlled files.

**Sample Devin Prompt:**
> Remove all hardcoded credentials from `docker-compose.yml`, `docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile`. Replace them with environment variables referenced via `${VARIABLE_NAME}` syntax. Create a `.env.example` file documenting all required environment variables with placeholder values. Add `.env` to `.gitignore`. Update the `privileges.sql` to use environment variable substitution or move credential setup to an entrypoint script. Document the required environment variables in the README.

---

### 2.6 Add Retry Policies (RE-2)

**Severity:** High | **Effort:** Small

Transient failures cause immediate request failure.

**Sample Devin Prompt:**
> Add Spring Retry support to Feign clients in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add `spring-retry` dependency. Configure retry for transient errors (HTTP 503, connection timeouts) with 3 max attempts, 1-second initial backoff, 2x multiplier, and 5-second max backoff. Ensure retries are NOT applied to POST endpoints that modify state unless idempotency keys are in place. For now, only add retry to GET operations (e.g., `readAccount`).

---

### 2.7 Add Feign Error Decoders (EH-4)

**Severity:** High | **Effort:** Medium

Fund-transfer and utility-payment services have no Feign error decoder.

**Sample Devin Prompt:**
> Create `CustomFeignErrorDecoder` classes for `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` (matching the pattern in user-service). The decoder should parse the `ErrorResponse` JSON from downstream errors and throw appropriate domain exceptions. Map HTTP 404 to `EntityNotFoundException`, HTTP 422 to a new `TransactionDeclinedException`, and other errors to `SimpleBankingGlobalException`. Register the decoder in the Feign client configuration.

---

### 2.8 Add Paginated Response Wrapper (AD-4)

**Severity:** High | **Effort:** Medium

List endpoints return raw lists, losing page metadata.

**Sample Devin Prompt:**
> Create a generic `PagedResponse<T>` class in the shared common library with fields: `content` (List<T>), `page` (int), `size` (int), `totalElements` (long), `totalPages` (int), `last` (boolean). Update all paginated endpoints across all services to return `ResponseEntity<PagedResponse<T>>` instead of `ResponseEntity<List<T>>`. Map from Spring Data `Page<Entity>` to `PagedResponse<DTO>` in each service method.

---

### 2.9 Add Idempotency Keys for Financial Operations (RE-6)

**Severity:** Critical | **Effort:** Medium

Duplicate requests can create duplicate financial transactions.

**Sample Devin Prompt:**
> Add idempotency key support to fund transfer and utility payment endpoints. Add an `idempotencyKey` field (UUID) to `FundTransferRequest` and `UtilityPaymentRequest`. Before processing, check if a transaction with the same idempotency key already exists in the database. If it does, return the existing result instead of processing again. Add a unique index on the idempotency key column. Add `@NotNull` validation on the idempotency key field. Document the idempotency key requirement in the API documentation.

---

### 2.10 Add Structured Logging (OB-1)

**Severity:** Medium | **Effort:** Small

Plaintext logs are difficult to aggregate and search.

**Sample Devin Prompt:**
> Configure structured JSON logging for all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to all services. Create a shared `logback-spring.xml` configuration that outputs JSON format in Docker/production profiles and human-readable format in dev profile. Include MDC fields for `traceId`, `spanId`, `userId`, and `serviceName` in every log line.

---

## Phase 3: Polish (6-12 weeks)

Improvements that enhance developer experience, operational maturity, and long-term maintainability.

### 3.1 Add Integration Tests (TE-2)

**Severity:** High | **Effort:** Large

No tests verify real database operations or Spring context wiring.

**Sample Devin Prompt:**
> Create integration tests for all four business services using `@SpringBootTest` with Testcontainers for MySQL. For each service: (1) Test repository layer with real database operations. (2) Test full request-response flow using `MockMvc` or `WebTestClient`. For services that call other services via Feign, use WireMock to stub downstream APIs. Add Testcontainers dependency: `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7`. Create test configurations that disable Eureka and Config Server registration. Ensure each test class creates its own database schema using Flyway or JPA auto-DDL.

---

### 3.2 Add Contract Tests (TE-3)

**Severity:** High | **Effort:** Large

No contract verification between services communicating via Feign.

**Sample Devin Prompt:**
> Set up Spring Cloud Contract tests between services. On the provider side (core-banking-service), create contract DSL files defining the API contracts for: (1) GET `/api/v1/account/bank-account/{number}`, (2) GET `/api/v1/user/{identification}`, (3) POST `/api/v1/transaction/fund-transfer`, (4) POST `/api/v1/transaction/util-payment`. Generate provider verification tests. On the consumer side (user-service, fund-transfer-service, utility-payment-service), use contract stubs for consumer-driven contract tests. Add `spring-cloud-starter-contract-verifier` and `spring-cloud-starter-contract-stub-runner` dependencies.

---

### 3.3 Add Role-Based Access Control (SE-5)

**Severity:** High | **Effort:** Medium

All authenticated users can access all endpoints.

**Sample Devin Prompt:**
> Implement RBAC using Keycloak realm roles. Define two roles in Keycloak: `ROLE_USER` and `ROLE_ADMIN`. In the API Gateway `SecurityConfiguration`, restrict: (1) User approval/update endpoints to `ROLE_ADMIN`. (2) Fund transfer and utility payment POST endpoints to `ROLE_USER`. (3) User list and read endpoints to `ROLE_ADMIN`. (4) Self-service endpoints (read own user, own transfers) to `ROLE_USER`. Configure the JWT to include realm roles and extract them as Spring Security authorities. Add `@PreAuthorize` annotations to downstream service controllers as a defense-in-depth measure.

---

### 3.4 Set Up CI/CD Pipeline

**Severity:** Medium | **Effort:** Medium

No automated build or deployment pipeline exists.

**Sample Devin Prompt:**
> Create a GitHub Actions CI pipeline with the following jobs: (1) **Build & Test**: Build all 6 services with Gradle, run unit and integration tests. (2) **Code Quality**: Run JaCoCo coverage check, Checkstyle or SpotBugs. (3) **Security Scan**: Run OWASP Dependency Check on all services. (4) **Docker Build**: Build Docker images for all services. Use a matrix strategy to build services in parallel. Cache Gradle dependencies between runs. Trigger on push to `main` and on pull requests. Add status badges to README.

---

### 3.5 Implement Multi-Module Gradle Build (CO-2)

**Severity:** Medium | **Effort:** Medium

Each service must be built independently.

**Sample Devin Prompt:**
> Create a root `build.gradle` and `settings.gradle` that defines a multi-module Gradle project including all 6 services and the shared common library. Configure common plugins, dependency versions, and Java toolchain in the root build file using `subprojects {}` block. Ensure `./gradlew build` from the project root builds all services. Add a `./gradlew bootJar` task that builds all bootable JARs. Maintain backward compatibility so individual services can still be built from their own directory.

---

### 3.6 Add Custom Health Indicators (OB-3)

**Severity:** Medium | **Effort:** Small

Only default health checks are available.

**Sample Devin Prompt:**
> Create custom Spring Boot health indicators for each business service. For services that depend on core-banking-service via Feign, add a health indicator that pings the core-banking-service's health endpoint. For user-service, add a health indicator that checks Keycloak connectivity. For core-banking-service, ensure the database health indicator is active. Register all health indicators and configure `/actuator/health` to show details: `management.endpoint.health.show-details: always`.

---

### 3.7 Add Bulkhead Pattern (RE-5)

**Severity:** Medium | **Effort:** Medium

All Feign calls share one thread pool.

**Sample Devin Prompt:**
> Configure Resilience4j bulkhead for Feign clients in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Use semaphore-based bulkheads with: max concurrent calls 25, max wait duration 500ms. Create separate bulkhead instances for each Feign operation (e.g., `readAccount`, `fundTransfer`, `utilityPayment`). This ensures a slow operation on one Feign method doesn't exhaust all available threads for other operations.

---

### 3.8 Add Business Metrics (OB-5)

**Severity:** Medium | **Effort:** Medium

No business-level metrics for monitoring.

**Sample Devin Prompt:**
> Add Micrometer custom metrics to all business services. In fund-transfer-service: counter for `fund.transfer.total` (tags: status=success/failure), timer for `fund.transfer.duration`, gauge for `fund.transfer.pending.count`. In utility-payment-service: counter for `utility.payment.total`, timer for `utility.payment.duration`. In user-service: counter for `user.registration.total` (tags: status=success/failure). In core-banking-service: counter for `transaction.total` (tags: type=FUND_TRANSFER/UTILITY_PAYMENT), histogram for `transaction.amount`. Use `MeterRegistry` injection and Micrometer's `@Timed` annotation where appropriate.

---

### 3.9 Implement Saga Pattern for Fund Transfers (RE-8)

**Severity:** Critical | **Effort:** Large

Fund transfers lack compensation logic. A failure mid-transfer can lose funds.

**Sample Devin Prompt:**
> Implement a choreography-based saga pattern for fund transfers in core-banking-service. Refactor `TransactionService.internalFundTransfer()` to: (1) Create a pending transaction record first. (2) Debit the source account in a separate @Transactional method. (3) Credit the destination account in a separate @Transactional method. (4) If credit fails, execute a compensation method that reverses the debit. (5) Update the transaction record status to SUCCESS or FAILED. Add a `TransactionStatus` field to `TransactionEntity`. Log all steps for audit trail. Consider adding a scheduled job to detect and resolve stuck transactions (status=PENDING older than 5 minutes).

---

### 3.10 Add Dependency Vulnerability Scanning (SE-7)

**Severity:** Medium | **Effort:** Small

No automated dependency vulnerability detection.

**Sample Devin Prompt:**
> Add OWASP Dependency Check to the Gradle build. Add the plugin `org.owasp.dependencycheck` version `9.0.9` to all service `build.gradle` files (or to the root build file if multi-module is set up). Configure it to fail the build on CVSS score >= 7. Add `./gradlew dependencyCheckAnalyze` to the CI pipeline. Suppress known false positives in a `dependency-check-suppression.xml` file. Generate HTML and JSON reports.

---

## Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| **Phase 1: Quick Wins** | 9 items | 4 (balance bug, HTTP codes, error leak, timeouts) | 1-2 weeks |
| **Phase 2: Important** | 10 items | 3 (circuit breakers, downstream auth, idempotency) | 3-6 weeks |
| **Phase 3: Polish** | 10 items | 1 (saga pattern) | 6-12 weeks |
| **Total** | **29 items** | **8 critical fixes** | **10-20 weeks** |

### Priority Order Within Phases

**Phase 1 (do first → last):**
1. Balance calculation bug fix (RE-9) — data corruption risk
2. HTTP status codes (EH-1) — client contract correctness
3. Remove error detail leakage (EH-2) — security
4. Feign timeout configuration (RE-3) — availability
5. Stop logging sensitive data (SE-8) — compliance
6. Input validation (SE-3) — security
7. Prometheus metrics (OB-4) — observability
8. Fix ResponseEntity types (AD-1) — API documentation
9. Keycloak thread safety (SE-6) — reliability

**Phase 2 (do first → last):**
1. Idempotency keys (RE-6) — financial integrity
2. Circuit breakers (RE-1) — availability
3. Secure downstream services (SE-1) — security
4. Externalize secrets (SE-2) — security
5. Unit tests (TE-1) — quality
6. Retry policies (RE-2) — resilience
7. Feign error decoders (EH-4) — error handling
8. Shared library extraction (CO-1) — maintainability
9. Paginated response wrapper (AD-4) — API quality
10. Structured logging (OB-1) — observability
