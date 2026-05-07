# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are organized into three phases based on risk, effort, and dependency ordering. Each item includes a Devin prompt you can use to implement the fix.

---

## Phase 1: Quick Wins (Critical bugs & low-effort high-impact fixes)

These items address data-integrity bugs, security leaks, and foundational issues that should be fixed immediately. Most are small effort.

### 1.1 Fix Double-Deduction Bug in Balance Calculations

**Gap Ref**: Resilience 7.7 | **Severity**: Critical | **Effort**: Small

The `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` has already been debited, causing a double deduction. This exists in both `internalFundTransfer()` and `utilPayment()` in `TransactionService`.

**Devin Prompt**:
> Fix the double-deduction bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is incorrectly set by subtracting `amount` from the already-debited `actualBalance`. After debiting `actualBalance`, `availableBalance` should be set equal to `actualBalance` (not `actualBalance - amount`). Add unit tests to verify correct balance calculations for both fund transfers and utility payments.

---

### 1.2 Fix Error Handling — Proper HTTP Status Codes

**Gap Ref**: Error Handling 2.1, 2.2, 2.3 | **Severity**: Critical/High | **Effort**: Small

**Devin Prompt**:
> Refactor the `GlobalExceptionHandler` in all four business services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service) to: (1) Return 404 for `EntityNotFoundException`, (2) Return 409 for `UserAlreadyRegisteredException`, (3) Return 422 for `InsufficientFundsException` and `InvalidBankingUserException`, (4) Return 500 for unhandled `Exception` with a generic message (do NOT expose stack traces or exception details), (5) Always return structured `ErrorResponse` JSON (`{code, message, timestamp}`) — never raw strings. Use `@ResponseStatus` or explicit `ResponseEntity.status()` calls. Make the error response format consistent across all services.

---

### 1.3 Add Bean Validation to All Request DTOs

**Gap Ref**: Security 4.2 | **Severity**: Critical | **Effort**: Small

**Devin Prompt**:
> Add Jakarta Bean Validation annotations to all request DTOs across the project: (1) `FundTransferRequest` in both fund-transfer-service and core-banking-service: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`. (2) `UtilityPaymentRequest` in both utility-payment-service and core-banking-service: `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`. (3) `User` registration DTO in user-service: `@Email @NotBlank` on `email`, `@NotBlank @Size(min=6)` on `password`, `@NotBlank` on `identification`. Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns 400 with field-level error details.

---

### 1.4 Add Type Parameters to All ResponseEntity Returns

**Gap Ref**: Error Handling 2.4 | **Severity**: Low | **Effort**: Small

**Devin Prompt**:
> Add explicit generic type parameters to all `ResponseEntity` return types in every controller across all services. For example, change `public ResponseEntity readUsers(Pageable pageable)` to `public ResponseEntity<List<User>> readUsers(Pageable pageable)`. This improves compile-time safety and OpenAPI documentation generation. Fix all controllers in core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service.

---

### 1.5 Add Feign Timeout Configuration

**Gap Ref**: Resilience 7.2 | **Severity**: Critical | **Effort**: Small

**Devin Prompt**:
> Add explicit timeout configuration for all Feign clients in the project. In each service that uses Feign (internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service), add the following to their `application.yml`: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also configure the core-banking-service-specific client with `spring.cloud.openfeign.client.config.core-banking-service.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 15000`.

---

### 1.6 Fix Keycloak Singleton Thread Safety

**Gap Ref**: Security 4.5 | **Severity**: High | **Effort**: Small

**Devin Prompt**:
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. The `getInstance()` method uses an unsynchronized lazy singleton pattern. Replace it with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once as a Spring-managed singleton. Remove the static field and manual null-check pattern. The `Keycloak` bean should be created using `KeycloakBuilder` with the existing `@Value` properties.

---

### 1.7 Fix Logging Bug in FundTransferService

**Gap Ref**: Observability 6.1 | **Severity**: Low | **Effort**: Small

**Devin Prompt**:
> Fix the logging bug in `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java` line 32. The log statement `log.info("Sending fund transfer request {}" + request.toString())` concatenates instead of using the SLF4J placeholder. Change it to `log.info("Sending fund transfer request {}", request)`. Also audit all log statements across all services to ensure SLF4J placeholders are used correctly and that no PII (emails, full account numbers) is logged at INFO level — redact or mask sensitive fields.

---

### 1.8 Fix OpenAPI Dependency (webflux-ui on Servlet Apps)

**Gap Ref**: API Design 5.3 | **Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Fix the incorrect OpenAPI dependency in core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. These are servlet-based (Spring MVC) applications but they use `springdoc-openapi-starter-webflux-ui`. Replace with `springdoc-openapi-starter-webmvc-ui:2.1.0` in their `build.gradle` files. Also update to the latest springdoc version if compatible with Spring Boot 3.2.4.

---

## Phase 2: Important (Structural improvements & reliability)

These items address resilience, testing gaps, and architectural concerns that significantly impact production readiness.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap Ref**: Resilience 7.1, 7.3, 7.5 | **Severity**: Critical/High | **Effort**: Medium

**Devin Prompt**:
> Add Resilience4j circuit breaker, retry, and fallback support to all Feign clients in the project. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. (2) Enable Feign circuit breaker integration with `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's `application.yml`. (3) Create fallback classes for each Feign client that return appropriate error responses (e.g., `BankingCoreFeignClientFallback`). (4) Configure circuit breaker parameters: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`. (5) Add retry configuration: `maxAttempts=3`, `waitDuration=1s` for transient failures. (6) Add unit tests for the fallback behavior.

---

### 2.2 Add Unit Tests for All Business Services

**Gap Ref**: Testing 3.1 | **Severity**: Critical | **Effort**: Large

**Devin Prompt**:
> Add comprehensive unit tests for the three business services that currently have no tests. For each service, create test classes using JUnit 5 and Mockito: (1) **internet-banking-fund-transfer-service**: Test `FundTransferService.fundTransfer()` (success, Feign failure, null fields) and `FundTransferService.readAllTransfers()`. (2) **internet-banking-user-service**: Test `UserService.createUser()` (success, duplicate email, NIC not found, email mismatch, Keycloak failure), `UserService.readUsers()`, `UserService.readUser()`, `UserService.updateUser()` (approval flow). Test `KeycloakUserService` methods with mocked `KeycloakManager`. (3) **internet-banking-utility-payment-service**: Test `UtilityPaymentService.utilPayment()` (success, Feign failure) and `UtilityPaymentService.readPayments()`. Add H2 test configuration for each service. Target at least 80% line coverage on service classes.

---

### 2.3 Add Integration Tests with Testcontainers

**Gap Ref**: Testing 3.2 | **Severity**: High | **Effort**: Large

**Devin Prompt**:
> Add integration tests using Testcontainers for MySQL to the core-banking-service. (1) Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies. (2) Create a base test class that starts a MySQL Testcontainer and configures Spring datasource properties. (3) Write integration tests for `AccountController`, `UserController`, and `TransactionController` using `@SpringBootTest` and `MockMvc`. (4) Test the full fund transfer flow end-to-end within the service (controller → service → repository → DB). (5) Verify Flyway migrations run correctly against real MySQL. (6) Add a test for the insufficient funds scenario at the API level.

---

### 2.4 Extract Shared Library

**Gap Ref**: Code Organization 1.2 | **Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Create a shared library module `internet-banking-common` at the project root. (1) Create a `build.gradle` for the shared module as a plain Java library (no Spring Boot plugin, just `java-library`). (2) Move the following duplicated classes into the shared module under appropriate packages: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`. (3) Create a root `settings.gradle` that includes all service modules and the common module. (4) Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. (5) Remove the duplicated classes from each service. (6) Ensure all services still compile and tests pass.

---

### 2.5 Add Feign Error Decoder to All Services

**Gap Ref**: Error Handling 2.5 | **Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Add a custom `FeignErrorDecoder` to the shared library (or to each service if no shared library exists yet) that decodes error responses from downstream services into appropriate exceptions. The decoder should: (1) Parse the `ErrorResponse` JSON from the response body. (2) Map HTTP 404 to `EntityNotFoundException`. (3) Map HTTP 400/422 to `SimpleBankingGlobalException` with the error code from the response. (4) Map HTTP 5xx to a new `ServiceUnavailableException`. (5) Register the decoder in `CustomFeignClientConfiguration` for all Feign clients. (6) Add unit tests for the decoder.

---

### 2.6 Secure Downstream Services

**Gap Ref**: Security 4.7 | **Severity**: High | **Effort**: Medium

**Devin Prompt**:
> Add security to the downstream services so they are not accessible without proper authentication when accessed directly (bypassing the API Gateway). For each business service (core-banking, fund-transfer, user, utility-payment): (1) Add `spring-boot-starter-security` dependency. (2) Create a `SecurityConfiguration` class that either validates the `X-Auth-Id` header (internal calls from the gateway) or validates a JWT token. (3) For inter-service communication, configure Feign to propagate the authentication context by adding a `RequestInterceptor` that forwards the `Authorization` header or `X-Auth-Id` header. (4) Ensure actuator health endpoints remain accessible without authentication.

---

### 2.7 Add Distributed Transaction Safety (Saga Pattern)

**Gap Ref**: Resilience 7.6 | **Severity**: Critical | **Effort**: Large

**Devin Prompt**:
> Implement a basic saga pattern for the fund transfer flow to handle partial failures. In `internet-banking-fund-transfer-service`: (1) After saving the `PENDING` entity, wrap the Feign call to core-banking in a try-catch. (2) If the Feign call fails, update the local entity status to `FAILED` and record the error message. (3) If the Feign call succeeds but the local DB update fails, implement a compensation call to core-banking to reverse the transfer (add a new `POST /api/v1/transaction/fund-transfer/reverse` endpoint in core-banking). (4) Add a `FAILED` value to `TransactionStatus` enum. (5) Add a scheduled job that retries `FAILED` transfers or alerts administrators. (6) Apply the same pattern to the utility payment flow. (7) Add comprehensive tests for each failure scenario.

---

### 2.8 Add Pagination Metadata to Responses

**Gap Ref**: API Design 5.4 | **Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Fix all paginated endpoints to return proper pagination metadata. In every controller method that accepts `Pageable` and returns a list, change the return type from `List<T>` to Spring Data's `Page<T>` (or a custom `PageResponse<T>` DTO with `content`, `totalElements`, `totalPages`, `pageNumber`, `pageSize`). Affected endpoints: `GET /api/v1/user` (core-banking), `GET /api/v1/bank-users` (user-service), `GET /api/v1/transfer` (fund-transfer), `GET /api/v1/utility-payment` (utility-payment). Update the corresponding service methods to return the full `Page` object instead of calling `.getContent()`.

---

## Phase 3: Polish (Production hardening & operational excellence)

These items improve operational visibility, developer experience, and long-term maintainability.

### 3.1 Add Structured JSON Logging

**Gap Ref**: Observability 6.1, 6.4 | **Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Configure structured JSON logging across all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service. (2) Create a shared `logback-spring.xml` that outputs JSON format in production profile and plain text in development. (3) Include trace ID, span ID, service name, and request ID in every log line via MDC. (4) Ensure sensitive fields (email, account numbers) are masked in log output using a custom `MaskingPatternLayout` or field masking in the JSON encoder. (5) Add the logback config to the shared library or each service's `src/main/resources/`.

---

### 3.2 Add Contract Tests Between Services

**Gap Ref**: Testing 3.3 | **Severity**: Medium | **Effort**: Large

**Devin Prompt**:
> Add Spring Cloud Contract tests to verify API compatibility between Feign clients and their provider services. (1) In core-banking-service (the provider), add `spring-cloud-starter-contract-verifier` dependency and create contract DSL files for each endpoint consumed by other services. (2) In fund-transfer-service, user-service, and utility-payment-service (consumers), add `spring-cloud-contract-stub-runner` and write stub-based integration tests that verify the Feign clients work correctly against the contract stubs. (3) Create contracts for: `GET /api/v1/account/bank-account/{number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`, `GET /api/v1/user/{identification}`.

---

### 3.3 Set Up CI/CD Pipeline

**Gap Ref**: Build/Deploy (no existing CI) | **Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Create a GitHub Actions CI pipeline for the project. Create `.github/workflows/ci.yml` that: (1) Triggers on push to `main` and on all PRs. (2) Uses Java 21 and Gradle. (3) Runs `./gradlew build` for each service (or uses a Gradle composite build if the shared library is in place). (4) Runs all unit and integration tests. (5) Uploads test reports as artifacts. (6) Includes a dependency vulnerability scan step using `./gradlew dependencyCheckAnalyze` (add OWASP Dependency Check Gradle plugin). (7) Builds Docker images for each service but does not push them (build verification only). (8) Caches Gradle dependencies for faster builds.

---

### 3.4 Add Rate Limiting at API Gateway

**Gap Ref**: Security 4.6 | **Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency to the API Gateway. (2) Add a Redis container to `docker-compose.yml`. (3) Configure rate limiting in the gateway routes: 10 requests/second for fund transfer and utility payment endpoints, 50 requests/second for read endpoints. (4) Use the JWT subject claim as the rate limit key (per-user rate limiting). (5) Return HTTP 429 with a `Retry-After` header when rate limit is exceeded. (6) Add rate limit configuration to the external config repository.

---

### 3.5 Add CORS Configuration

**Gap Ref**: Security 4.4 | **Severity**: Medium | **Effort**: Small

**Devin Prompt**:
> Add CORS configuration to the API Gateway. In the `SecurityConfiguration` class, add a `CorsConfigurationSource` bean that: (1) Allows origins from a configurable property (`app.cors.allowed-origins`, defaulting to `http://localhost:3000`). (2) Allows methods: GET, POST, PATCH, PUT, DELETE, OPTIONS. (3) Allows headers: Authorization, Content-Type, X-Requested-With. (4) Exposes headers: X-Total-Count (for pagination). (5) Sets `allowCredentials` to true. (6) Sets `maxAge` to 3600 seconds. Apply the CORS configuration to the `SecurityWebFilterChain`.

---

### 3.6 Add Custom Health Indicators

**Gap Ref**: Observability 6.2, 6.3 | **Severity**: Low | **Effort**: Small

**Devin Prompt**:
> Add custom Spring Boot Actuator health indicators and expose Prometheus metrics. (1) In each service, ensure `management.endpoints.web.exposure.include=health,info,prometheus,metrics` is set. (2) In the user-service, add a `KeycloakHealthIndicator` that checks Keycloak reachability. (3) In services with Feign clients, add a health indicator that pings the core-banking-service health endpoint. (4) Add `micrometer-registry-prometheus` dependency to each service for Prometheus metric export. (5) Add custom metrics for business operations: fund transfer count/latency, payment count/latency, user registration count.

---

### 3.7 Externalize Secrets from Source Code

**Gap Ref**: Security 4.1 | **Severity**: Critical | **Effort**: Small

**Devin Prompt**:
> Remove all hardcoded credentials from the repository and replace them with environment variables. (1) In `docker-compose.yml`, replace all inline passwords with `${VARIABLE}` references and create a `.env.example` file documenting required variables (without actual values). (2) Add `.env` to `.gitignore`. (3) In `privileges.sql`, use environment variable substitution or move to a Docker entrypoint script that reads env vars. (4) Remove the test credentials from `README.md` and the Postman collection. (5) Ensure the Keycloak client secret in the Postman environment file is replaced with a placeholder. (6) Add a `SECURITY.md` documenting how to configure secrets for local development.

---

### 3.8 Add OpenAPI Documentation

**Gap Ref**: API Design 5.3 | **Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Enhance OpenAPI documentation across all services. (1) Ensure the correct `springdoc-openapi-starter-webmvc-ui` dependency is used (not webflux). (2) Add `@Schema` annotations to all DTOs with field descriptions, examples, and constraints. (3) Add `@ApiResponse` annotations to all controller methods documenting success (200/201) and error (400/404/409/500) responses. (4) Configure SpringDoc in each service's `application.yml` to set API title, version, and description. (5) At the gateway level, add SpringDoc's gateway aggregation to provide a single unified Swagger UI that routes to each service's API docs.

---

### 3.9 Create Multi-Project Gradle Build

**Gap Ref**: Code Organization 1.1 | **Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
> Convert the project to a Gradle multi-project build. (1) Create a root `settings.gradle` that includes all service subprojects. (2) Create a root `build.gradle` with an `allprojects` block that sets the repository, group, and Java version, and a `subprojects` block that applies common plugins and configures shared dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0). (3) Move common dependencies (actuator, tracing, Lombok, test) to the `subprojects` block. (4) Simplify each service's `build.gradle` to only declare service-specific dependencies. (5) Ensure `./gradlew build` from the root builds all services. (6) Update Dockerfiles if JAR paths change.

---

## Summary Timeline

| Phase | Items | Estimated Effort | Focus |
|---|---|---|---|
| **Phase 1** | 8 items | 1-2 weeks | Bug fixes, validation, security basics, timeouts |
| **Phase 2** | 8 items | 3-5 weeks | Resilience, testing, architecture, security hardening |
| **Phase 3** | 9 items | 3-5 weeks | Observability, CI/CD, documentation, developer experience |

### Recommended Execution Order Within Phases

**Phase 1** (prioritized):
1. Fix double-deduction bug (1.1) — data integrity
2. Fix error handling (1.2) — security + correctness
3. Add input validation (1.3) — security
4. Add Feign timeouts (1.5) — resilience
5. Fix Keycloak thread safety (1.6) — reliability
6. Fix logging bug (1.7) — operational
7. Fix OpenAPI dependency (1.8) — developer experience
8. Add ResponseEntity generics (1.4) — code quality

**Phase 2** (prioritized):
1. Add circuit breakers (2.1) — resilience
2. Add unit tests (2.2) — quality gate
3. Add Feign error decoder (2.5) — error handling
4. Secure downstream services (2.6) — security
5. Add distributed transaction safety (2.7) — data integrity
6. Add pagination metadata (2.8) — API quality
7. Extract shared library (2.4) — maintainability
8. Add integration tests (2.3) — quality gate

**Phase 3** (prioritized):
1. Externalize secrets (3.7) — security (do this sooner if deploying to production)
2. Set up CI/CD (3.3) — automation
3. Add structured logging (3.1) — observability
4. Add health indicators & metrics (3.6) — observability
5. Add CORS config (3.5) — frontend readiness
6. Add rate limiting (3.4) — security
7. Add contract tests (3.2) — quality
8. Add OpenAPI docs (3.8) — developer experience
9. Create multi-project build (3.9) — maintainability
