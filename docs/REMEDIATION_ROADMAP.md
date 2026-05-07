# Remediation Roadmap

This roadmap prioritizes the gaps identified in `GAP_ANALYSIS.md` into three phases. Each item includes a reference to the gap ID, a description, and a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort items that immediately improve security, reliability, and code quality.

### 1.1 Fix HTTP Status Codes in Error Handlers (EH-1)

**Gap:** All errors return HTTP 400 regardless of type.
**Effort:** Small

> **Devin Prompt:**
> "In each service's `GlobalExceptionHandler`, update the exception handlers to return appropriate HTTP status codes: `EntityNotFoundException` should return `404 Not Found`, `InsufficientFundsException` should return `400 Bad Request`, the generic `Exception` handler should return `500 Internal Server Error`. Apply this consistently across core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service."

### 1.2 Remove Stack Trace Leakage (EH-2)

**Gap:** Generic exception handler returns the full exception object to the client.
**Effort:** Small

> **Devin Prompt:**
> "In each service's `GlobalExceptionHandler`, replace the generic `Exception` handler response body from `'Exception occur inside API ' + e` with a safe `ErrorResponse` containing a generic message like 'An unexpected error occurred' and an internal error code. Log the full exception at ERROR level server-side instead."

### 1.3 Add Bean Validation to Request DTOs (SE-2, EH-6)

**Gap:** No input validation on any request body.
**Effort:** Small

> **Devin Prompt:**
> "Add Jakarta Bean Validation annotations to all request DTOs across the project:
> - `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`
> - `UtilityPaymentRequest`: `@NotBlank` on `account` and `referenceNumber`, `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`
> - `User` (user-service): `@NotBlank @Email` on `email`, `@NotBlank` on `identification` and `password`
>
> Add `@Valid` on all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns `422 Unprocessable Entity` with field-level error details."

### 1.4 Fix `availableBalance` Double-Subtraction Bug (Business Logic)

**Gap:** `TransactionService.internalFundTransfer()` and `utilPayment()` double-subtract the amount from `availableBalance`.
**Effort:** Small

> **Devin Prompt:**
> "In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the balance calculation bug:
> - In `internalFundTransfer()`: change `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())` (actual balance was already subtracted on the previous line). Same fix for `toBankAccountEntity`.
> - In `utilPayment()`: apply the same fix for `fromAccount.setAvailableBalance(...)`.
> Add unit tests to verify the correct balance after a transfer."

### 1.5 Remove Sensitive Data from Logs (OB-3)

**Gap:** User registration logs the full User object including password. Fund transfers log account numbers.
**Effort:** Small

> **Devin Prompt:**
> "Audit all `log.info()` calls across all services that log request objects via `.toString()`. In `internet-banking-user-service/UserController.java`, remove the password from the log output by either excluding the `password` field from `toString()` (add `@ToString.Exclude` on the password field in `User.java`) or replacing the log message with a safe summary. In `internet-banking-fund-transfer-service`, redact full account numbers in logs (show only last 4 digits). Fix the string concatenation bug in `FundTransferService.java` line 32: change `'Sending fund transfer request {}' + request.toString()` to `'Sending fund transfer request {}'` with `request` as the second argument."

### 1.6 Add `.gitignore` Rules and Clean Up (CO-5, CO-6)

**Gap:** Build output directories and `.DS_Store` committed to git.
**Effort:** Small

> **Devin Prompt:**
> "Update the `.gitignore` file in the repository root to include: `build/`, `bin/`, `.DS_Store`, `*.class`, `.gradle/`, `out/`. Then remove the currently tracked files: `git rm -r --cached '**/build' '**/bin' .DS_Store`."

### 1.7 Secure Actuator Endpoints (SE-8)

**Gap:** All actuator endpoints are publicly accessible.
**Effort:** Small

> **Devin Prompt:**
> "In the API Gateway's `SecurityConfiguration.java`, restrict actuator endpoint access: only permit `/actuator/health` and `/actuator/info` without authentication. Require authentication for all other actuator paths (`/actuator/env`, `/actuator/beans`, `/actuator/configprops`, etc.). Update the path matchers from `.pathMatchers('/actuator/**').permitAll()` to `.pathMatchers('/actuator/health', '/actuator/info').permitAll()` and add `.pathMatchers('/actuator/**').authenticated()` for the rest."

### 1.8 Add Typed `ResponseEntity` Return Types (EH-7, AD-7)

**Gap:** Raw `ResponseEntity` without type parameters.
**Effort:** Small

> **Devin Prompt:**
> "Across all controllers in all services, add type parameters to `ResponseEntity` return types. For example, in `AccountController.getBankAccount()`, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do this for every controller method in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service."

---

## Phase 2: Important Improvements (2-6 weeks)

Structural improvements that require more effort but significantly improve maintainability, resilience, and testability.

### 2.1 Extract Shared Library Module (CO-1, CO-2)

**Gap:** Exception classes, DTOs, mappers, and filters are duplicated across services.
**Effort:** Medium

> **Devin Prompt:**
> "Create a new Gradle module called `banking-common` at the repository root. Set up a root `settings.gradle` that includes all 7 modules (6 services + banking-common). Move the following shared code into `banking-common`:
> - `exception/` package: `SimpleBankingGlobalException`, `EntityNotFoundException`, `ErrorResponse`, `GlobalErrorCode`, `GlobalExceptionHandler`
> - `model/mapper/BaseMapper`
> - `model/dto/AuditAware`
> - `configuration/filter/AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
>
> Update each service's `build.gradle` to depend on `implementation project(':banking-common')` and remove the duplicated classes."

### 2.2 Add Circuit Breakers and Timeouts (RE-1, RE-2, RE-3, RE-4)

**Gap:** No resilience patterns on Feign calls.
**Effort:** Medium

> **Devin Prompt:**
> "Add Resilience4j circuit breaker and retry support to all Feign clients:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle` in fund-transfer-service, utility-payment-service, and user-service.
> 2. Configure default Feign timeouts in each service's application config: `connectTimeout: 5000`, `readTimeout: 10000`.
> 3. Configure circuit breaker defaults: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 30s`.
> 4. Configure retry: `maxAttempts: 3`, `waitDuration: 1s`, `retryExceptions: [java.io.IOException, feign.RetryableException]`.
> 5. Add fallback methods for each Feign client that return appropriate error responses.
> 6. Verify the configuration by adding integration tests."

### 2.3 Secure Downstream Services (SE-1)

**Gap:** Services behind the gateway have no authentication.
**Effort:** Medium

> **Devin Prompt:**
> "Add Spring Security to the downstream business services (core-banking-service, user-service, fund-transfer-service, utility-payment-service) so they validate the JWT token from the gateway:
> 1. Add `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security` dependencies.
> 2. Add a `SecurityConfiguration` class in each service that validates JWTs using the same Keycloak JWK Set URI.
> 3. Alternatively, implement service-to-service authentication using a shared secret or mTLS, and configure the API Gateway to forward the JWT to downstream services.
> 4. Ensure actuator health endpoints remain accessible without auth for Docker health checks."

### 2.4 Add Role-Based Access Control (SE-5)

**Gap:** No authorization beyond authentication.
**Effort:** Medium

> **Devin Prompt:**
> "Implement role-based access control (RBAC) across the application:
> 1. Define roles in Keycloak: `ROLE_ADMIN`, `ROLE_USER`.
> 2. In the API Gateway's `SecurityConfiguration`, map Keycloak realm roles to Spring Security authorities using a custom `ReactiveJwtAuthenticationConverter`.
> 3. In `internet-banking-user-service`, restrict `PATCH /api/v1/bank-users/update/{id}` to `ROLE_ADMIN` only.
> 4. In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, validate that the authenticated user owns the source account before allowing a transfer or payment.
> 5. Add `@PreAuthorize` annotations and enable method security with `@EnableMethodSecurity`."

### 2.5 Add Comprehensive Unit Tests (TE-1)

**Gap:** 5 of 6 services have no functional tests.
**Effort:** Large

> **Devin Prompt:**
> "Add unit tests for all service classes in the project:
>
> **internet-banking-user-service:**
> - `UserServiceTest`: test createUser (happy path, duplicate email, user not found in core, email mismatch, Keycloak creation failure), readUsers, readUser, updateUser (approval flow).
> - `KeycloakUserServiceTest`: test createUser, readUser, readUserByEmail, updateUser with mocked KeycloakManager.
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferServiceTest`: test fundTransfer (happy path, core-banking failure, save failure), readAllTransfers.
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentServiceTest`: test utilPayment (happy path, core-banking failure), readPayments.
>
> For each service, create a `src/test/resources/application.yml` that configures H2 in-memory database, disables Eureka client, and disables Spring Cloud Config. Use Mockito for external dependencies (Feign clients, Keycloak)."

### 2.6 Add Integration Tests (TE-2)

**Gap:** No endpoint-level tests.
**Effort:** Large

> **Devin Prompt:**
> "Add MockMvc-based integration tests for each service's REST endpoints:
>
> **core-banking-service:**
> - `AccountControllerIT`: test GET bank-account (found, not found), GET utility-account (found, not found).
> - `TransactionControllerIT`: test POST fund-transfer (success, insufficient funds, invalid account), POST util-payment.
> - `UserControllerIT`: test GET user by identification, GET users paginated.
>
> Use `@SpringBootTest`, `@AutoConfigureMockMvc`, and H2 test database. Use `@Sql` to load test data before each test. Disable Eureka and Config Server in test profile."

### 2.7 Add Feign Error Handling (EH-4)

**Gap:** No structured error decoding for Feign failures.
**Effort:** Medium

> **Devin Prompt:**
> "Create a shared `FeignErrorDecoder` (in the banking-common module or in each service) that implements `feign.codec.ErrorDecoder`:
> - For HTTP 404 responses, throw `EntityNotFoundException`.
> - For HTTP 400 responses, parse the `ErrorResponse` body and throw a corresponding `SimpleBankingGlobalException`.
> - For HTTP 5xx responses, throw a custom `ServiceUnavailableException` (new exception class).
> - For unknown errors, throw a generic `SimpleBankingGlobalException` with the status code.
>
> Register this decoder in the `CustomFeignClientConfiguration` class and apply it to all Feign clients."

### 2.8 Add Structured Logging (OB-2)

**Gap:** Plain text logging not suitable for log aggregation.
**Effort:** Medium

> **Devin Prompt:**
> "Add structured JSON logging to all services for production use:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service.
> 2. Create a `logback-spring.xml` in each service's `src/main/resources/` that uses the Logstash JSON encoder for the `docker` profile and plain text for local development.
> 3. Include trace ID and span ID in the structured log output (these are auto-populated by Micrometer).
> 4. Add MDC fields for `userId` (from `X-Auth-Id` header) in the `AppAuthUserFilter`.
> 5. Ensure consistency by putting the logback configuration in the shared banking-common module."

### 2.9 Add Idempotency to Write Operations (RE-7)

**Gap:** Duplicate transactions possible on retry.
**Effort:** Medium

> **Devin Prompt:**
> "Add idempotency support to the fund transfer and utility payment endpoints:
> 1. Add an `Idempotency-Key` header requirement for all POST endpoints in fund-transfer-service and utility-payment-service.
> 2. Create an `idempotency_keys` table in each service's database with columns: `key` (unique), `response_body`, `status_code`, `created_at`.
> 3. Create an `IdempotencyFilter` that checks for existing keys before processing. If found, return the cached response. If not, process the request and store the result.
> 4. Add a TTL cleanup job (e.g., 24 hours) to remove old idempotency keys."

### 2.10 Add Prometheus Metrics (OB-5)

**Gap:** No Prometheus metrics endpoint despite Prometheus being in the tech stack.
**Effort:** Small

> **Devin Prompt:**
> "Add Prometheus metrics support to all services:
> 1. Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`.
> 2. Ensure `management.endpoints.web.exposure.include=health,info,prometheus` is set in each service's configuration.
> 3. Verify the `/actuator/prometheus` endpoint returns metrics in Prometheus format.
> 4. Add a Prometheus scrape config example to the docker-compose documentation."

---

## Phase 3: Polish (6-12 weeks)

Longer-term improvements for production readiness, maintainability, and developer experience.

### 3.1 Implement Saga Pattern for Distributed Transactions (RE-8)

**Gap:** No consistency guarantee across service boundaries.
**Effort:** Large

> **Devin Prompt:**
> "Implement the Saga pattern for the fund transfer flow to handle distributed transaction failures:
> 1. In `internet-banking-fund-transfer-service`, add a state machine for fund transfers: `INITIATED` -> `CORE_PROCESSING` -> `SUCCESS` / `FAILED` / `COMPENSATING`.
> 2. If the Feign call to core-banking-service succeeds but the local status update fails, implement a compensating transaction that reverses the core banking operation.
> 3. Add a `POST /api/v1/transaction/reverse/{transactionId}` endpoint to core-banking-service.
> 4. Add a scheduled job that checks for `CORE_PROCESSING` status records older than 5 minutes and triggers compensation or retry.
> 5. Apply the same pattern to utility-payment-service."

### 3.2 Add Contract Tests Between Services (TE-3)

**Gap:** No consumer-driven contract tests.
**Effort:** Large

> **Devin Prompt:**
> "Add Spring Cloud Contract tests between services:
> 1. Add `spring-cloud-starter-contract-verifier` to core-banking-service (the provider).
> 2. Create contract DSL files (Groovy or YAML) in `core-banking-service/src/test/resources/contracts/` defining the API contracts for:
>    - `GET /api/v1/account/bank-account/{account_number}` (found, not found)
>    - `POST /api/v1/transaction/fund-transfer` (success, insufficient funds)
>    - `POST /api/v1/transaction/util-payment` (success)
>    - `GET /api/v1/user/{identification}` (found, not found)
> 3. Generate stubs from core-banking-service and publish them.
> 4. Add `spring-cloud-starter-contract-stub-runner` to consumer services (fund-transfer, utility-payment, user) and write consumer-side tests that verify compatibility with the stubs."

### 3.3 Add Test Coverage Reporting (TE-4)

**Gap:** No coverage measurement or enforcement.
**Effort:** Small

> **Devin Prompt:**
> "Add JaCoCo test coverage reporting to all services:
> 1. Apply the `jacoco` plugin in each service's `build.gradle`.
> 2. Configure `jacocoTestReport` to generate HTML and XML reports.
> 3. Add a `jacocoTestCoverageVerification` task with minimum thresholds: 60% line coverage, 50% branch coverage.
> 4. Wire `check` task to depend on `jacocoTestCoverageVerification`.
> 5. If using a root build, add a report aggregation task that merges coverage across all services."

### 3.4 Externalize and Secure Credentials (SE-3)

**Gap:** Credentials hardcoded in Docker Compose and SQL files.
**Effort:** Medium

> **Devin Prompt:**
> "Externalize all hardcoded credentials in the project:
> 1. In `docker-compose.yml` and `docker-compose-support-apps.yml`, replace hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}`).
> 2. Create a `.env.example` file with placeholder values and add `.env` to `.gitignore`.
> 3. In `docker-compose/mysql/privileges.sql`, parameterize the database user password.
> 4. Add documentation in the README explaining how to configure secrets for local development."

### 3.5 Add Rate Limiting at API Gateway (RE-6)

**Gap:** No rate limiting.
**Effort:** Medium

> **Devin Prompt:**
> "Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter:
> 1. Add `spring-boot-starter-data-redis-reactive` dependency to the gateway.
> 2. Add a Redis service to docker-compose.
> 3. Configure `RedisRateLimiter` with default limits: `replenishRate: 10`, `burstCapacity: 20` requests per second per user.
> 4. Apply the filter globally or to specific routes (fund-transfer and utility-payment routes should have stricter limits).
> 5. Return HTTP `429 Too Many Requests` when limits are exceeded."

### 3.6 Add Graceful Shutdown (RE-10)

**Gap:** In-flight requests may be dropped during deployment.
**Effort:** Small

> **Devin Prompt:**
> "Configure graceful shutdown for all services:
> 1. Add `server.shutdown=graceful` to each service's application configuration.
> 2. Set `spring.lifecycle.timeout-per-shutdown-phase=30s`.
> 3. Update the Docker Compose `stop_grace_period` to 35 seconds for each service.
> 4. Verify by sending a request during shutdown and confirming it completes before the process exits."

### 3.7 Add Custom Health Checks (OB-4)

**Gap:** No health checks for downstream dependencies.
**Effort:** Small

> **Devin Prompt:**
> "Add custom health indicators to services that depend on external systems:
> 1. In `internet-banking-user-service`, add a `KeycloakHealthIndicator` that checks if Keycloak is reachable by calling the `.well-known/openid-configuration` endpoint.
> 2. In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, add a `CoreBankingHealthIndicator` that calls `GET /actuator/health` on core-banking-service.
> 3. Register these as Spring beans implementing `HealthIndicator`.
> 4. Verify they appear in the `/actuator/health` response."

### 3.8 Implement Consistent Response Envelope (AD-2)

**Gap:** No standard response format.
**Effort:** Medium

> **Devin Prompt:**
> "Create a standard API response envelope and apply it across all services:
> 1. In the shared banking-common module, create a generic `ApiResponse<T>` class with fields: `data` (T), `errors` (List<ErrorDetail>), `meta` (pagination info).
> 2. Create a `ResponseAdvice` class implementing `ResponseBodyAdvice<Object>` that wraps all controller responses in `ApiResponse`.
> 3. Update the `GlobalExceptionHandler` to return errors in the same `ApiResponse` format.
> 4. Update OpenAPI annotations to reflect the new response structure.
> 5. Update any Feign response types to unwrap from the envelope."

### 3.9 Add Dependency Vulnerability Scanning (SE-9)

**Gap:** No automated vulnerability detection.
**Effort:** Small

> **Devin Prompt:**
> "Add the OWASP Dependency Check Gradle plugin to the project:
> 1. Apply `org.owasp.dependencycheck` plugin version 9.x to each service's `build.gradle`.
> 2. Configure `dependencyCheck { failBuildOnCVSS = 7 }` to fail builds on high/critical CVEs.
> 3. Add a `dependencyCheckAnalyze` task that generates HTML reports.
> 4. Add a GitHub Actions workflow that runs dependency check on pull requests.
> 5. Document the process for reviewing and suppressing false positives in `docs/SECURITY.md`."

### 3.10 Fix Swagger Dependency for MVC Services (AD-6)

**Gap:** WebFlux Swagger dependency used in MVC services.
**Effort:** Small

> **Devin Prompt:**
> "In core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` in each `build.gradle`. The WebFlux variant should only remain in the API Gateway (which is a WebFlux app). Verify that Swagger UI loads correctly at `/swagger-ui.html` on each service after the change."

### 3.11 Add Bulkhead / Thread Pool Isolation (RE-5)

**Gap:** All Feign calls share the same thread pool.
**Effort:** Medium

> **Devin Prompt:**
> "Configure Resilience4j bulkhead for Feign clients to prevent thread pool exhaustion:
> 1. Add `resilience4j-bulkhead` dependency to fund-transfer-service and utility-payment-service.
> 2. Configure a thread pool bulkhead for core-banking-service Feign calls: `maxConcurrentCalls: 25`, `maxWaitDuration: 500ms`.
> 3. Configure a separate bulkhead for any other external calls.
> 4. Add metrics for bulkhead state (active calls, rejected calls) exposed via Prometheus."

---

## Prioritization Summary

| Phase | Items | Key Outcomes |
|---|---|---|
| **Phase 1** (Quick Wins) | 8 items | Fixes critical security holes, data leakage, business logic bugs. Improves error handling. Cleans up repo hygiene. |
| **Phase 2** (Important) | 10 items | Adds resilience patterns, authentication on downstream services, RBAC, comprehensive testing, structured logging, idempotency. |
| **Phase 3** (Polish) | 11 items | Production hardening: saga pattern, contract tests, coverage gates, rate limiting, vulnerability scanning, graceful shutdown. |

### Recommended Execution Order Within Each Phase

**Phase 1 (do in parallel where possible):**
1. SE-2 + EH-6: Input validation (blocks most injection attacks)
2. EH-1 + EH-2: Fix error responses (security + correctness)
3. OB-3: Remove sensitive data from logs
4. Business logic bug fix
5. SE-8: Secure actuator endpoints
6. CO-5/CO-6: Repo hygiene
7. EH-7/AD-7: Type safety improvements

**Phase 2 (sequential, build on each other):**
1. CO-1/CO-2: Shared library (unblocks consistent changes)
2. RE-1/RE-2/RE-3: Circuit breakers + timeouts (critical resilience)
3. SE-1: Downstream service security
4. SE-5: RBAC
5. TE-1: Unit tests
6. TE-2: Integration tests
7. Remaining items

**Phase 3 (independent, can be parallelized):**
- Items can largely be done in parallel by different developers/sessions.
