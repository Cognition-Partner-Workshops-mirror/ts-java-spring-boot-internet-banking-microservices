# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to execute the remediation.

---

## Phase 1: Quick Wins (Critical & High severity, Small effort)

These items address the most dangerous issues with minimal code changes. Target: **1-2 weeks**.

### 1.1 Fix Generic Exception Handler -- Stop Stack Trace Exposure

**Gaps addressed:** 2.1, 4.4, 2.5

All four `GlobalExceptionHandler` classes return raw exception strings to clients. Fix the catch-all handler to return a safe, structured `ErrorResponse` with HTTP 500 and log the actual exception server-side.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, update all `GlobalExceptionHandler` classes across all services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). Change the generic `@ExceptionHandler({Exception.class})` method to: (1) log the full exception at ERROR level, (2) return HTTP 500 with a structured `ErrorResponse` containing code `"INTERNAL_ERROR"` and message `"An unexpected error occurred. Please try again later."`. Do NOT expose the exception details in the response body. Open a PR with the changes.

---

### 1.2 Add Bean Validation to All Request DTOs

**Gaps addressed:** 2.4, 4.3

No request bodies are validated. Add `@Valid` on controller parameters and Jakarta Bean Validation annotations on DTO fields.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add input validation to all REST API request DTOs: (1) Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. (2) Add `@Valid` to all `@RequestBody` parameters in controllers. (3) Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc. annotations to fields in `FundTransferRequest` (fromAccount, toAccount, amount), `UtilityPaymentRequest` (account, providerId, amount, referenceNumber), `User` (email, password, identification), and `UserUpdateRequest` (status). (4) Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Open a PR.

---

### 1.3 Fix HTTP Status Codes in Exception Handlers

**Gap addressed:** 2.2

`EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, etc.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, update all `GlobalExceptionHandler` classes to return appropriate HTTP status codes: `EntityNotFoundException` -> 404 Not Found, `InsufficientFundsException` -> 422 Unprocessable Entity, `UserAlreadyRegisteredException` -> 409 Conflict, `InvalidEmailException` -> 400 Bad Request, `InvalidBankingUserException` -> 404 Not Found. Keep the existing `ErrorResponse` body format. Open a PR.

---

### 1.4 Fix TransactionEntity @OneToOne Bug

**Gap addressed:** 7.6

`TransactionEntity.account` is `@OneToOne(cascade = CascadeType.ALL)` but should be `@ManyToOne`.

**Sample Devin Prompt:**
> In `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`, change the `account` field mapping from `@OneToOne(cascade = CascadeType.ALL)` to `@ManyToOne(fetch = FetchType.LAZY)` and remove `cascade = CascadeType.ALL`. Update the `@JoinColumn` to keep `name = "account_id"` and `referencedColumnName = "id"`. Verify existing tests still pass. Open a PR.

---

### 1.5 Externalize Docker Compose Credentials

**Gap addressed:** 4.1

Hardcoded passwords in `docker-compose.yml`.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, refactor `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to use environment variable substitution for all passwords and credentials. Replace hardcoded values with `${MYSQL_ROOT_PASSWORD:-changeme}`, `${KEYCLOAK_ADMIN_PASSWORD:-changeme}`, `${KC_DB_PASSWORD:-changeme}`, `${POSTGRES_PASSWORD:-changeme}`. Create a `.env.example` file in the `docker-compose/` directory documenting all required variables with placeholder values. Add `docker-compose/.env` to `.gitignore`. Open a PR.

---

### 1.6 Add Feign Client Timeout Configuration

**Gap addressed:** 7.3

No timeout configuration on Feign clients; threads can block indefinitely.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add Feign client timeout configuration to `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. In each service's `application.yml`, add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Open a PR.

---

### 1.7 Add Retry Policies on Feign Clients

**Gap addressed:** 7.2

No retry configuration for transient failures.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add Spring Retry support to `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`: (1) Add `spring-retry` and `spring-boot-starter-aop` dependencies to each `build.gradle`. (2) Add `@EnableRetry` to each main application class. (3) Configure Feign retry with `spring.cloud.openfeign.client.config.default.retryer` in `application.yml` -- max 3 attempts, 1 second initial backoff. Ensure retries only apply to GET requests (safe methods). Open a PR.

---

### 1.8 Add Dependency Vulnerability Scanning

**Gap addressed:** 4.7

No OWASP or Snyk scanning.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add the OWASP Dependency-Check Gradle plugin to all services. In each `build.gradle`, add `id 'org.owasp.dependencycheck' version '9.0.9'` to the plugins block. Add a task alias `dependencyCheckAnalyze` and configure `failBuildOnCVSS = 7`. Verify it runs successfully on at least one service. Open a PR.

---

### 1.9 Fix OpenAPI Dependency (WebFlux -> WebMVC)

**Gap addressed:** 5.5

MVC services incorrectly use the WebFlux OpenAPI dependency.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0` in the `build.gradle` of `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Keep the WebFlux version only in `internet-banking-api-gateway` (which actually uses WebFlux). Open a PR.

---

### 1.10 Add JaCoCo Test Coverage Reporting

**Gap addressed:** 3.4

No test coverage measurement.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add the JaCoCo Gradle plugin to all services. In each `build.gradle`, add `id 'jacoco'` to the plugins block. Configure the `jacocoTestReport` task to generate HTML and XML reports. Add a `jacocoTestCoverageVerification` task with a minimum line coverage of 40% (to start). Verify it runs on `core-banking-service` which has existing tests. Open a PR.

---

## Phase 2: Important (High & Medium severity, Medium effort)

These items significantly improve reliability and maintainability. Target: **3-6 weeks**.

### 2.1 Add Circuit Breakers with Resilience4j

**Gaps addressed:** 7.1, 7.4

No circuit breakers; cascading failures possible.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add Resilience4j circuit breakers to all Feign clients: (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service` `build.gradle`. (2) Enable Feign circuit breaker: `spring.cloud.openfeign.circuitbreaker.enabled: true`. (3) Create fallback classes for each Feign client that return appropriate error responses (e.g., `BankingCoreFeignClientFallback` returning a `FundTransferResponse` with a failure message). (4) Configure circuit breaker defaults: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 30s`. Open a PR.

---

### 2.2 Add Feign Error Decoders to All Services

**Gap addressed:** 2.3

Only user-service has a custom Feign error decoder.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add a `CustomFeignErrorDecoder` to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` (user-service already has one). The decoder should: (1) Parse the error response body as `ErrorResponse` (code + message). (2) Re-throw appropriate exceptions: 404 -> `EntityNotFoundException`, 422 -> `InsufficientFundsException`, other 4xx -> `SimpleBankingGlobalException`, 5xx -> `SimpleBankingGlobalException` with a "Service unavailable" message. (3) Register the decoder in each `CustomFeignClientConfiguration`. Open a PR.

---

### 2.3 Add Idempotency Keys to Write Operations

**Gap addressed:** 7.5

No idempotency protection; duplicate transactions possible.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, implement idempotency for fund transfer and utility payment operations: (1) Add an `idempotencyKey` (UUID) field to `FundTransferRequest` and `UtilityPaymentRequest`. (2) Add a unique constraint on `idempotencyKey` in `FundTransferEntity` and `UtilityPaymentEntity`. (3) In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, check if a record with the same `idempotencyKey` already exists -- if so, return the existing result instead of processing again. (4) Update the corresponding controller to require the `Idempotency-Key` HTTP header. Open a PR.

---

### 2.4 Extract Shared Library for Common Code

**Gap addressed:** 1.2

Exception classes, audit infrastructure, filters, and mappers are duplicated.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, create a new Gradle module `internet-banking-common` at the repo root. Move the following shared classes into it: `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` (a configurable base class), `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `BaseMapper`. Publish as a local Maven artifact. Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. Create a root `settings.gradle` that includes all subprojects. Remove the duplicated classes from each service. Ensure all services compile. Open a PR.

---

### 2.5 Write Unit Tests for Remaining Services

**Gap addressed:** 3.1

Only core-banking-service has tests.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, write unit tests for: (1) `internet-banking-fund-transfer-service`: `FundTransferServiceTest` covering `fundTransfer()` success, Feign failure, and `readAllTransfers()`. (2) `internet-banking-utility-payment-service`: `UtilityPaymentServiceTest` covering `utilPayment()` success, Feign failure, and `readPayments()`. (3) `internet-banking-user-service`: `UserServiceTest` covering `createUser()` (happy path, duplicate email, invalid NIC, email mismatch), `readUsers()`, `readUser()`, and `updateUser()`. Use Mockito to mock Feign clients, repositories, and KeycloakUserService. Each test class should have tests for both success and error paths. Ensure all tests pass. Open a PR.

---

### 2.6 Add Structured Logging

**Gap addressed:** 6.1

Inconsistent, unstructured logging.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, implement structured JSON logging across all services: (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to all service `build.gradle` files. (2) Create a shared `logback-spring.xml` in each service's `src/main/resources/` that uses `LogstashEncoder` for JSON output, including trace/span IDs from Micrometer. (3) Add MDC context for `userId` (from `X-Auth-Id` header) in the `AppAuthUserFilter`. (4) Ensure all controllers and services use `@Slf4j` consistently. Open a PR.

---

### 2.7 Add Custom Health Checks

**Gap addressed:** 6.2

No custom health indicators.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add custom `HealthIndicator` beans: (1) In `core-banking-service`: a `DatabaseHealthIndicator` that runs a simple query. (2) In `internet-banking-user-service`: a `KeycloakHealthIndicator` that pings the Keycloak server URL. (3) In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`: a `CoreBankingHealthIndicator` that calls the core-banking-service actuator health endpoint. (4) Enable detailed health info: `management.endpoint.health.show-details: always` in each `application.yml`. Open a PR.

---

### 2.8 Fix Keycloak Singleton Thread Safety

**Gap addressed:** 4.6

Lazy singleton without synchronization.

**Sample Devin Prompt:**
> In `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`, fix the thread-unsafe lazy singleton. Replace the manual singleton pattern with a `@Bean` method in a `@Configuration` class that creates a single `Keycloak` instance. Remove the static `keycloakInstance` field. Ensure `KeycloakManager` receives the bean via constructor injection. Open a PR.

---

### 2.9 Add Pagination Metadata to List Endpoints

**Gap addressed:** 5.3

List endpoints discard pagination metadata.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, update all paginated GET endpoints to return `Page<T>` (or a custom `PageResponse<T>` wrapper) instead of `List<T>`: (1) In `core-banking-service` `UserController.readUsers()`, return `ResponseEntity<Page<User>>`. (2) In `internet-banking-fund-transfer-service` `FundTransferController.readFundTransfers()`, return `ResponseEntity<Page<FundTransfer>>`. (3) In `internet-banking-utility-payment-service` `UtilityPaymentController.readPayments()`, return `ResponseEntity<Page<UtilityPayment>>`. (4) In `internet-banking-user-service` `UserController.readUsers()`, return `ResponseEntity<Page<User>>`. Update the service layer to return `Page` objects. Open a PR.

---

### 2.10 Add CORS Configuration

**Gap addressed:** 4.2

No CORS policy defined.

**Sample Devin Prompt:**
> In `internet-banking-api-gateway`, add CORS configuration to `SecurityConfiguration.java`. Allow configurable origins (default to `http://localhost:3000` for development), allow methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, allow headers `Authorization, Content-Type, X-Requested-With, Idempotency-Key`, expose headers `X-Total-Count`, and allow credentials. Make the allowed origins configurable via `app.config.cors.allowed-origins` property. Open a PR.

---

### 2.11 Add Rate Limiting at API Gateway

**Gap addressed:** 4.5

No rate limiting.

**Sample Devin Prompt:**
> In `internet-banking-api-gateway`, add rate limiting using Spring Cloud Gateway's built-in `RequestRateLimiter` filter with Redis (or in-memory for development). Configure: (1) 100 requests per second for authenticated endpoints. (2) 10 requests per minute for the unauthenticated `/user/api/v1/bank-users/register` endpoint. (3) Return HTTP 429 with a structured error response when rate limit is exceeded. Add Redis dependency and configuration. Open a PR.

---

## Phase 3: Polish (Medium & Low severity, various effort)

These items improve developer experience and long-term maintainability. Target: **2-3 months**.

### 3.1 Create Multi-Project Gradle Build

**Gap addressed:** 1.1

No root build; each service is isolated.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, create a root `settings.gradle` that includes all 6 service subprojects and the new `internet-banking-common` module. Create a root `build.gradle` that defines common dependency versions, plugin versions, and shared configuration (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0) in a `subprojects {}` block. Remove duplicated version declarations from individual service `build.gradle` files. Ensure `./gradlew build` at the root compiles all services. Open a PR.

---

### 3.2 Standardize Package Structure

**Gap addressed:** 1.3

Inconsistent package layouts across services.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, standardize the package structure across all services to follow this convention: `com.javatodev.finance.{controller, service, service.rest, model.entity, model.dto, model.dto.request, model.dto.response, model.mapper, repository, configuration, configuration.filter, configuration.audit, exception}`. Refactor packages in `internet-banking-fund-transfer-service` (move `model.repository` -> `repository`, `service.rest.client` -> `service.rest`), `internet-banking-utility-payment-service` (move `model.rest.request` -> `model.dto.request`, `model.rest.response` -> `model.dto.response`), and `internet-banking-user-service` (move `model.rest.response` -> `model.dto.response`, `configuration.feign` -> `configuration`). Ensure all services compile. Open a PR.

---

### 3.3 Add Integration Tests with TestContainers

**Gap addressed:** 3.2

No integration tests.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add integration tests using TestContainers for `core-banking-service`: (1) Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to test dependencies. (2) Create a `@SpringBootTest` test that starts a MySQL container, runs Flyway migrations, and tests: account lookup, fund transfer (success + insufficient funds), utility payment, and user lookup. (3) Use `@DynamicPropertySource` to inject the MySQL URL. (4) Create a similar integration test for `internet-banking-fund-transfer-service` using WireMock for the core-banking-service Feign client. Open a PR.

---

### 3.4 Add Contract Tests (Spring Cloud Contract)

**Gap addressed:** 3.3

No consumer-driven contracts.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, set up Spring Cloud Contract between `core-banking-service` (producer) and `internet-banking-fund-transfer-service` / `internet-banking-utility-payment-service` (consumers): (1) Add `spring-cloud-starter-contract-verifier` to `core-banking-service`. (2) Write contract DSL files for the fund transfer and utility payment endpoints. (3) Generate stubs and verify them in consumer services using `spring-cloud-contract-stub-runner`. (4) Add `spring-cloud-starter-contract-stub-runner` to the consumer test dependencies. Open a PR.

---

### 3.5 Add Custom Business Metrics

**Gap addressed:** 6.3

No Micrometer counters or timers for business events.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add custom Micrometer metrics: (1) Add `micrometer-registry-prometheus` to all service `build.gradle` files. (2) In `core-banking-service`, add a counter `banking.transactions.total` with tags `type` (FUND_TRANSFER, UTILITY_PAYMENT) and `status` (SUCCESS, FAILED), and a timer `banking.transactions.duration`. (3) In `internet-banking-user-service`, add a counter `banking.users.registrations.total` with tag `status`. (4) Expose the Prometheus endpoint at `/actuator/prometheus`. Open a PR.

---

### 3.6 Complete OpenAPI Annotations

**Gap addressed:** 5.6

Incomplete Swagger documentation.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, complete OpenAPI annotations on all controllers and DTOs: (1) Add `@ApiResponse` annotations for all status codes (200, 400, 404, 422, 500) on every controller method. (2) Add `@Schema` annotations to all request/response DTO fields with descriptions and examples. (3) Add `@Parameter` annotations to path variables and query parameters. (4) Add a global `OpenApiConfig` class in each service with `@OpenAPIDefinition` specifying title, version, and description. Verify the Swagger UI renders correctly. Open a PR.

---

### 3.7 Add Filtering and Sorting to List Endpoints

**Gap addressed:** 5.4

No query parameters for filtering data.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, add filtering support to paginated endpoints: (1) `FundTransferController.readFundTransfers()`: add optional query parameters `status`, `fromDate`, `toDate`. (2) `UtilityPaymentController.readPayments()`: add optional query parameters `status`, `providerId`, `fromDate`, `toDate`. (3) `UserController.readUsers()` (both services): add optional query parameters `status`, `email`. Use Spring Data JPA Specifications or `@Query` with optional parameters. Open a PR.

---

### 3.8 Convert Mappers to Spring Beans

**Gap addressed:** 1.4

Mappers instantiated inline instead of injected.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, convert all mapper classes to Spring `@Component` beans: (1) Add `@Component` to `UserMapper`, `BankAccountMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`. (2) Replace `private XMapper mapper = new XMapper()` with `private final XMapper mapper` using constructor injection (already `@RequiredArgsConstructor`). (3) Update tests to inject or mock mappers. Open a PR.

---

### 3.9 Implement Notification Service

**Gap addressed:** Planned feature from README, not yet built.

**Sample Devin Prompt:**
> In the repo `ts-java-spring-boot-internet-banking-microservices`, create a new `internet-banking-notification-service` microservice: (1) Generate a Spring Boot 3.2.4 project with dependencies: `spring-boot-starter-amqp` (RabbitMQ), `spring-boot-starter-mail`, `spring-cloud-starter-netflix-eureka-client`, `spring-cloud-starter-config`. (2) Create a `NotificationConsumer` that listens to a `banking.notifications` RabbitMQ queue. (3) Add message publishing in `FundTransferService` and `UtilityPaymentService` after successful transactions. (4) Add RabbitMQ to `docker-compose.yml`. (5) Include Dockerfile, `wait-for-it.sh`, and configuration. Open a PR.

---

## Summary Timeline

| Phase | Items | Target Duration | Key Outcomes |
|-------|-------|----------------|--------------|
| **Phase 1** | 10 items | 1-2 weeks | Secure error handling, input validation, data integrity fix, credential hygiene, resilience basics, vulnerability scanning |
| **Phase 2** | 11 items | 3-6 weeks | Circuit breakers, idempotency, shared library, unit tests, structured logging, health checks, pagination, CORS, rate limiting |
| **Phase 3** | 9 items | 2-3 months | Multi-project build, integration tests, contract tests, metrics, OpenAPI docs, filtering, notification service |
