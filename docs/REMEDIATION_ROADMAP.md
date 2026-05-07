# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (Critical & High severity, Small effort)

These items address the most dangerous issues with the least effort. They should be completed first.

### 1.1 Fix Balance Calculation Bug (Double Subtraction/Addition)

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Small

The `TransactionService.internalFundTransfer()` and `utilPayment()` methods double-subtract from `availableBalance` on debits and double-add on credits. This causes incorrect account balances after every transaction.

**Fix:** Set `availableBalance` equal to the already-updated `actualBalance` (remove the second subtraction/addition).

> **Devin Prompt:**
> *"In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the balance calculation bug. In `internalFundTransfer()`, lines 90-91 should be: `setActualBalance(subtract(amount))` then `setAvailableBalance(getActualBalance())` — not subtracting amount a second time. Apply the same fix for the credit side (lines 99-100) and for `utilPayment()` (lines 63-64). Update the existing unit tests in `TransactionServiceTest.java` to assert that `actualBalance` and `availableBalance` are equal after each operation."*

---

### 1.2 Add Request Body Validation

**Gap:** 2.4 / 4.3 | **Severity:** Critical | **Effort:** Small

No `@Valid` or Bean Validation annotations exist on any request DTO. Null or empty values cause NPEs deep in the service layer.

**Fix:** Add `spring-boot-starter-validation` dependency and `@NotNull`, `@NotBlank`, `@Min`, `@Size` annotations to all request DTOs. Add `@Valid` to all `@RequestBody` parameters.

> **Devin Prompt:**
> *"Add Bean Validation to all request DTOs across all services. (1) Add `implementation 'org.springframework.boot:spring-boot-starter-validation'` to each service's `build.gradle`. (2) Add validation annotations to: `FundTransferRequest` (fromAccount @NotBlank, toAccount @NotBlank, amount @NotNull @DecimalMin('0.01')), `UtilityPaymentRequest` (providerId @NotNull, amount @NotNull @DecimalMin('0.01'), referenceNumber @NotBlank, account @NotBlank), and the `User` registration DTO (email @NotBlank @Email, password @NotBlank @Size(min=8), identification @NotBlank). (3) Add `@Valid` before every `@RequestBody` parameter in all controllers. (4) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns a structured `ErrorResponse` with HTTP 422."*

---

### 1.3 Externalize Docker Compose Credentials

**Gap:** 4.1 | **Severity:** Critical | **Effort:** Small

Plain-text passwords are hardcoded in `docker-compose.yml`.

**Fix:** Replace hardcoded values with environment variable references and add a `.env.example` file.

> **Devin Prompt:**
> *"In `docker-compose/docker-compose.yml` and `docker-compose-support-apps.yml`, replace all hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}`, `${KC_DB_PASSWORD}`). Create a `docker-compose/.env.example` file with placeholder values and add `docker-compose/.env` to `.gitignore`. Update the README to document the required environment variables."*

---

### 1.4 Fix Error Response Consistency

**Gap:** 2.1, 2.2 | **Severity:** High | **Effort:** Small

All exceptions return HTTP 400 with inconsistent response bodies. The generic handler leaks stack traces.

**Fix:** Return proper HTTP status codes and always use the structured `ErrorResponse` format.

> **Devin Prompt:**
> *"Refactor the `GlobalExceptionHandler` in all four business services to: (1) Return HTTP 404 for `EntityNotFoundException`. (2) Return HTTP 422 for `InsufficientFundsException`, `InvalidEmailException`, `UserAlreadyRegisteredException`, `InvalidBankingUserException`. (3) Return HTTP 500 for the generic `Exception` catch-all, using a structured `ErrorResponse` body with a generic message (never expose the exception details or stack trace to the client). (4) Log the full exception at ERROR level in the generic handler. Make sure all services use the same `ErrorResponse` structure with `code` and `message` fields."*

---

### 1.5 Fix Keycloak Singleton Thread Safety

**Gap:** 4.4 | **Severity:** High | **Effort:** Small

The `KeycloakProperties.getInstance()` method is not thread-safe.

**Fix:** Use `synchronized` or convert to a Spring `@Bean`.

> **Devin Prompt:**
> *"In `internet-banking-user-service`, refactor `KeycloakProperties` to expose the `Keycloak` client as a Spring `@Bean` instead of using a manual lazy singleton. Create a `@Configuration` class `KeycloakConfig` that produces a `@Bean Keycloak` using `KeycloakBuilder`. Remove the static `keycloakInstance` field and `getInstance()` method. Update `KeycloakManager` to inject the `Keycloak` bean directly."*

---

### 1.6 Remove Sensitive Data from Logs

**Gap:** 4.6 | **Severity:** High | **Effort:** Small

Controllers log full request objects including passwords and account numbers.

**Fix:** Remove or mask sensitive fields from log statements.

> **Devin Prompt:**
> *"Audit all `log.info` and `log.debug` statements across all controllers and services. Remove logging of full request objects (`request.toString()`, `fundTransferRequest.toString()`). Replace with minimal logging that does not include passwords, account numbers, or financial amounts. For example, `log.info('Fund transfer initiated, reference={}', transactionId)` instead of logging the full request body. Specifically fix: `UserController.java` in user-service (line 31), `FundTransferController.java` (line 31), `TransactionController.java` (lines 31, 40)."*

---

### 1.7 Add Feign Client Timeout Configuration

**Gap:** 7.3 | **Severity:** High | **Effort:** Small

No timeouts are configured on Feign clients, risking thread pool exhaustion.

**Fix:** Add global Feign timeout configuration.

> **Devin Prompt:**
> *"Add Feign client timeout configuration to each service that uses OpenFeign (user-service, fund-transfer-service, utility-payment-service). Add the following to each service's externalized config (or `application.yml` for local dev): `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 15000` for the core banking calls which may take longer."*

---

### 1.8 Add Gradle Wrapper

**Gap:** 1.4 | **Severity:** Medium | **Effort:** Small

No `gradlew` in the repository.

> **Devin Prompt:**
> *"Generate the Gradle wrapper for this project. Run `gradle wrapper --gradle-version 8.7` in the repository root. Commit the generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/` directory. Update the README build instructions to use `./gradlew` instead of `gradle`."*

---

### 1.9 Fix Swagger Dependency (WebFlux → WebMVC)

**Gap:** 5.4 | **Severity:** Medium | **Effort:** Small

Business services use the WebFlux Swagger starter but are actually WebMVC apps.

> **Devin Prompt:**
> *"In `build.gradle` for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace `implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'` with `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'`. These services use Spring WebMVC, not WebFlux. Verify that the Swagger UI loads correctly at `/swagger-ui.html` for each service."*

---

### 1.10 Add Typed ResponseEntity and Pagination Metadata

**Gap:** 5.1, 5.5 | **Severity:** Medium | **Effort:** Small

Controllers return raw `ResponseEntity` and list endpoints lose pagination metadata.

> **Devin Prompt:**
> *"Across all controllers: (1) Replace raw `ResponseEntity` return types with typed versions (e.g., `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`). (2) For paginated list endpoints (`readUsers`, `readFundTransfers`, `readPayments`), return `ResponseEntity<Page<T>>` instead of `ResponseEntity<List<T>>` to preserve pagination metadata (totalElements, totalPages, number, size). Update the corresponding service methods to return `Page<T>` instead of `List<T>`."*

---

## Phase 2: Important (High severity + Medium effort, or structural improvements)

These items require more effort but are important for production readiness.

### 2.1 Extract Shared Library

**Gap:** 1.2 | **Severity:** High | **Effort:** Medium

Identical code (BaseMapper, AuditAware, filters, exception classes) is duplicated across services.

> **Devin Prompt:**
> *"Create a new Gradle subproject `internet-banking-common` that contains the shared code duplicated across services: (1) `BaseMapper` interface, (2) `AuditAware` base entity class, (3) `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, (4) `ErrorResponse`, `GlobalErrorCode`, `SimpleBankingGlobalException`, `EntityNotFoundException`, `GlobalExceptionHandler`. Set up a root `settings.gradle` that includes all services as subprojects. Update each service's `build.gradle` to depend on `internet-banking-common` and delete the duplicated classes. Make sure all services still compile and tests pass."*

---

### 2.2 Secure Downstream Services

**Gap:** 4.7 | **Severity:** High | **Effort:** Medium

Individual services are accessible without authentication when accessed directly (bypassing the gateway).

> **Devin Prompt:**
> *"Add Spring Security to core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Each service should: (1) Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to `build.gradle`. (2) Create a `SecurityConfiguration` class that validates JWT tokens from Keycloak (using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`). (3) Permit actuator endpoints without auth. (4) Require authentication for all other endpoints. (5) Propagate the JWT token in Feign client calls by adding a `RequestInterceptor` that forwards the Authorization header. Update test configurations to disable security for unit tests."*

---

### 2.3 Add Circuit Breakers

**Gap:** 7.1 | **Severity:** High | **Effort:** Medium

No circuit breakers protect against cascading failures.

> **Devin Prompt:**
> *"Add Resilience4j circuit breakers to all Feign clients. (1) Add `implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'` to fund-transfer-service, utility-payment-service, and user-service. (2) Configure default circuit breaker settings: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`, `slowCallDurationThreshold=5s`. (3) Add `@CircuitBreaker` annotations to each Feign client method with appropriate fallback methods. (4) Fallbacks should return user-friendly error responses (e.g., 'Core banking service is temporarily unavailable, please try again later')."*

---

### 2.4 Add Idempotency to Financial Operations

**Gap:** 7.5 | **Severity:** Critical | **Effort:** Medium

Duplicate submissions can cause double fund transfers or payments.

> **Devin Prompt:**
> *"Implement idempotency for fund transfer and utility payment endpoints. (1) Add an `idempotencyKey` field (UUID) to `FundTransferRequest` and `UtilityPaymentRequest` (annotated with `@NotBlank`). (2) In `FundTransferService.fundTransfer()`, check if a `FundTransferEntity` with the same `idempotencyKey` already exists. If so, return the existing result instead of processing a new transfer. (3) Apply the same pattern to `UtilityPaymentService.utilPayment()`. (4) Add a unique constraint on the `idempotency_key` column in the `fund_transfer` and `utility_payment` tables. (5) Add database migration scripts for the new columns."*

---

### 2.5 Add Rate Limiting

**Gap:** 4.5 | **Severity:** Medium | **Effort:** Medium

No rate limiting on any endpoint.

> **Devin Prompt:**
> *"Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter with Redis. (1) Add `spring-boot-starter-data-redis-reactive` to the gateway's `build.gradle`. (2) Add a Redis service to `docker-compose.yml`. (3) Configure rate limiting on the gateway routes: 10 requests/second for authenticated users (keyed by JWT subject claim), 3 requests/second for the unauthenticated registration endpoint (keyed by IP). (4) Return HTTP 429 with a `Retry-After` header when rate limit is exceeded."*

---

### 2.6 Set Up Multi-Project Gradle Build

**Gap:** 1.1 | **Severity:** Medium | **Effort:** Medium

No root build file to orchestrate all services.

> **Devin Prompt:**
> *"Create a root-level `settings.gradle` and `build.gradle` for the multi-project Gradle build. The `settings.gradle` should include all six services as subprojects. The root `build.gradle` should: (1) Apply common plugins (Java, Spring Boot, dependency management) to all subprojects. (2) Define shared dependency versions in an `ext` block. (3) Configure common test settings (`useJUnitPlatform()`). Remove duplicated plugin and version declarations from individual service `build.gradle` files. Generate the Gradle wrapper from the root project."*

---

### 2.7 Add Structured Logging

**Gap:** 6.1 | **Severity:** Medium | **Effort:** Medium

No JSON logging for log aggregation.

> **Devin Prompt:**
> *"Configure structured JSON logging for all services. (1) Add `implementation 'net.logstash.logback:logstash-logback-encoder:7.4'` to each service's `build.gradle`. (2) Create a shared `logback-spring.xml` that outputs JSON format in `docker` and `production` profiles, and plain text in the `default` profile. (3) Include traceId, spanId, service name, and timestamp in every log line. (4) Ensure the MDC context from Micrometer Tracing is propagated into log fields."*

---

### 2.8 Add Retry Policies and Fallbacks

**Gap:** 7.2, 7.4 | **Severity:** Medium | **Effort:** Medium

No retry or fallback behavior on Feign clients.

> **Devin Prompt:**
> *"Configure Resilience4j retry policies on all Feign clients. (1) Set default retry config: `maxAttempts=3`, `waitDuration=500ms`, `retryExceptions=[IOException.class, TimeoutException.class]`. (2) Do NOT retry on `4xx` client errors (non-retryable). (3) Add fallback methods to each Feign client that return meaningful error responses when all retries are exhausted. (4) Log each retry attempt at WARN level."*

---

## Phase 3: Polish (Medium/Low severity, or Large effort)

These items improve maintainability and quality but are lower priority.

### 3.1 Add Unit Tests for All Services

**Gap:** 3.1 | **Severity:** High | **Effort:** Large

Only core-banking-service has meaningful tests.

> **Devin Prompt:**
> *"Add comprehensive unit tests for the three internet-banking services: (1) `internet-banking-user-service`: Test `UserService.createUser()` (happy path, duplicate email, invalid email, user not found in core), `UserService.updateUser()` (approve flow, reject flow), `UserService.readUsers()`. Mock `KeycloakUserService`, `BankingCoreRestClient`, and `UserRepository`. (2) `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()` (happy path, core banking error) and `readAllTransfers()`. Mock `BankingCoreFeignClient` and `FundTransferRepository`. (3) `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()` (happy path, core banking error) and `readPayments()`. Mock `BankingCoreRestClient` and `UtilityPaymentRepository`. Use JUnit 5 and Mockito. Aim for >80% line coverage on service classes."*

---

### 3.2 Add Integration Tests

**Gap:** 3.2 | **Severity:** High | **Effort:** Large

No integration tests exist.

> **Devin Prompt:**
> *"Add integration tests using Testcontainers for core-banking-service. (1) Add `testImplementation 'org.testcontainers:mysql:1.19.7'` and `testImplementation 'org.testcontainers:junit-jupiter:1.19.7'` to `build.gradle`. (2) Create a `@SpringBootTest` integration test class that starts a MySQL Testcontainer, runs Flyway migrations, and tests the full request flow: create a fund transfer via the REST API and verify the account balances are updated correctly in the database. (3) Add a similar integration test for the utility payment flow. (4) Configure a test profile that disables Eureka registration and Config Server."*

---

### 3.3 Add Contract Tests

**Gap:** 3.3 | **Severity:** Medium | **Effort:** Large

No contract tests between services.

> **Devin Prompt:**
> *"Add Spring Cloud Contract tests to verify the API contracts between services. (1) In core-banking-service (the provider), add the Spring Cloud Contract Verifier plugin and write contract DSLs for: GET `/api/v1/user/{identification}`, GET `/api/v1/account/bank-account/{account_number}`, POST `/api/v1/transaction/fund-transfer`, POST `/api/v1/transaction/util-payment`. (2) Generate contract stubs as a JAR. (3) In the consumer services (fund-transfer, utility-payment, user-service), add Spring Cloud Contract Stub Runner and write consumer-side tests that verify the Feign clients work against the generated stubs."*

---

### 3.4 Add Test Coverage Reporting

**Gap:** 3.4 | **Severity:** Low | **Effort:** Small

No coverage reporting configured.

> **Devin Prompt:**
> *"Add JaCoCo test coverage reporting to all services. (1) Apply the `jacoco` Gradle plugin in the root `build.gradle` (or in each service's `build.gradle`). (2) Configure `jacocoTestReport` to generate HTML and XML reports. (3) Add `jacocoTestCoverageVerification` with a minimum line coverage threshold of 60% (to start). (4) Make the `check` task depend on `jacocoTestCoverageVerification`."*

---

### 3.5 Add Custom Health Indicators

**Gap:** 6.2 | **Severity:** Low | **Effort:** Small

No custom health checks for downstream dependencies.

> **Devin Prompt:**
> *"Add custom Spring Boot health indicators: (1) In core-banking-service, add a health indicator that checks MySQL connectivity. (2) In user-service, add a health indicator that checks Keycloak reachability (ping the server URL). (3) In fund-transfer-service and utility-payment-service, add health indicators that check core-banking-service availability via the Eureka service list. Configure actuator to expose the `health` endpoint with details: `management.endpoint.health.show-details=always`."*

---

### 3.6 Add Custom Business Metrics

**Gap:** 6.3 | **Severity:** Low | **Effort:** Small

No business metrics instrumented.

> **Devin Prompt:**
> *"Add custom Micrometer metrics to the business services. (1) In core-banking-service, add a counter `banking.transactions.total` tagged by `type` (FUND_TRANSFER, UTILITY_PAYMENT) and `status` (SUCCESS, FAILED). (2) In fund-transfer-service, add a timer `banking.fund_transfer.duration` and a counter `banking.fund_transfer.total` by status. (3) In utility-payment-service, add equivalent metrics for utility payments. (4) Inject `MeterRegistry` and record metrics in the service layer methods."*

---

### 3.7 Standardize Endpoint Naming

**Gap:** 5.2, 5.6 | **Severity:** Low | **Effort:** Small

Inconsistent path naming conventions and non-RESTful endpoints.

> **Devin Prompt:**
> *"Standardize the REST API endpoint naming across all services to use kebab-case, plural nouns, and no verbs in paths: (1) Rename `/api/v1/bank-users/register` to `POST /api/v1/bank-users` (POST to the collection is already 'create'). (2) Rename `/api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}`. (3) Rename `/api/v1/account/bank-account/{account_number}` to `/api/v1/accounts/{accountNumber}`. (4) Rename `/api/v1/account/util-account/{account_name}` to `/api/v1/utility-accounts/{providerName}`. (5) Update all Feign client interfaces and gateway routes to match. (6) Update the Postman collection if present."*

---

### 3.8 Document CSRF Decision and Security Architecture

**Gap:** 4.2 | **Severity:** Medium | **Effort:** Small

CSRF is disabled without explicit documentation.

> **Devin Prompt:**
> *"Add a `docs/SECURITY.md` document that covers: (1) Authentication flow (Keycloak → JWT → API Gateway validation → X-Auth-Id header propagation). (2) Authorization model (which endpoints are public vs authenticated). (3) Explicit note that CSRF is disabled because the API is stateless and uses Bearer token authentication. (4) List of security configurations per service. (5) Secrets management approach (what's externalized, what needs environment variables)."*

---

### 3.9 Fix Distributed Tracing Configuration

**Gap:** 6.4, 6.5 | **Severity:** Medium | **Effort:** Small

Tracing is partially configured and logging is inconsistent.

> **Devin Prompt:**
> *"Ensure distributed tracing is fully configured: (1) Verify that `management.tracing.sampling.probability=1.0` is set in the externalized config for all services (or add it to each service's `application.yml` for local dev). (2) Add `@Slf4j` to all controllers and ensure every endpoint logs at least the operation name and traceId. (3) Verify that Feign calls propagate trace headers (B3 or W3C) — this should work automatically with `feign-micrometer` but confirm. (4) Add the Zipkin URL to the README documentation."*

---

## Summary Timeline

| Phase | Items | Estimated Effort | Focus |
|---|---|---|---|
| **Phase 1** | 10 items | 1-2 weeks | Fix critical bugs, security holes, and configuration errors |
| **Phase 2** | 8 items | 3-4 weeks | Structural improvements for production readiness |
| **Phase 3** | 9 items | 4-6 weeks | Quality, testing coverage, and polish |

### Priority Matrix

```
                    Small Effort          Medium Effort         Large Effort
                ┌─────────────────┬─────────────────────┬─────────────────────┐
  Critical      │ 7.6 Balance bug │ 7.5 Idempotency     │                     │
                │ 2.4 Validation  │                     │                     │
                │ 4.1 Credentials │                     │                     │
                ├─────────────────┼─────────────────────┼─────────────────────┤
  High          │ 2.1 Error resp  │ 1.2 Shared library  │ 3.1 Unit tests      │
                │ 2.2 HTTP codes  │ 4.7 Service security│ 3.2 Integration     │
                │ 4.4 Keycloak    │ 7.1 Circuit breakers│   tests             │
                │ 4.6 Log secrets │                     │                     │
                │ 7.3 Timeouts    │                     │                     │
                ├─────────────────┼─────────────────────┼─────────────────────┤
  Medium        │ 1.4 Gradle wrap │ 1.1 Multi-project   │ 3.3 Contract tests  │
                │ 5.1 Typed resp  │ 4.5 Rate limiting   │                     │
                │ 5.4 Swagger dep │ 6.1 Structured logs │                     │
                │ 5.5 Pagination  │ 7.2 Retries         │                     │
                │ 6.4 Tracing     │ 7.4 Fallbacks       │                     │
                ├─────────────────┼─────────────────────┼─────────────────────┤
  Low           │ 1.3 Packages   │ 5.3 API versioning  │                     │
                │ 3.4 Coverage   │                     │                     │
                │ 5.2 Naming     │                     │                     │
                │ 5.6 PATCH verb │                     │                     │
                │ 6.2 Health     │                     │                     │
                │ 6.3 Metrics    │                     │                     │
                │ 6.5 Logging    │                     │                     │
                └─────────────────┴─────────────────────┴─────────────────────┘
```
