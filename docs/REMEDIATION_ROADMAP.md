# Remediation Roadmap

## Internet Banking Microservices - Java 21 / Spring Boot 3.2.4

---

## Phasing Strategy

- **Phase 1 — Quick Wins:** Low-effort, high-impact fixes that improve security, correctness, and developer experience immediately. Target: 1-2 weeks.
- **Phase 2 — Important:** Medium-effort improvements that address structural issues, testing gaps, and resilience. Target: 3-6 weeks.
- **Phase 3 — Polish:** Larger refactors and enhancements that bring the codebase to production-grade standards. Target: 6-12 weeks.

---

## Phase 1: Quick Wins

### 1.1 Fix HTTP Status Codes in Error Handlers

**Gap:** All errors return HTTP 400 Bad Request (Critical)
**Effort:** Small

Map exceptions to appropriate HTTP status codes across all services.

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, update the `GlobalExceptionHandler` in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return proper HTTP status codes: `EntityNotFoundException` -> 404, `InsufficientFundsException` -> 422 Unprocessable Entity, `UserAlreadyRegisteredException` -> 409 Conflict, `InvalidEmailException` / `InvalidBankingUserException` -> 400, and the generic `Exception` catch-all -> 500 Internal Server Error. Also change the generic handler to return a structured `ErrorResponse` object instead of a plain string. Do not expose stack traces or exception class names in responses. Open a PR with the changes.

---

### 1.2 Stop Leaking Exception Details in API Responses

**Gap:** Exception leaks internal details (High)
**Effort:** Small

**Devin Prompt:**
> In all 4 business services of ts-java-spring-boot-internet-banking-microservices, update the generic `Exception` handler in each `GlobalExceptionHandler` to return a structured `ErrorResponse` with code `"INTERNAL_ERROR"` and message `"An unexpected error occurred. Please try again later."` instead of the current `"Exception occur inside API " + e` which leaks stack traces. Log the full exception at ERROR level server-side for debugging. Open a PR.

---

### 1.3 Add Input Validation to All Request DTOs

**Gap:** No input validation (Critical)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Bean Validation (jakarta.validation) to all services. First, add `spring-boot-starter-validation` to the `build.gradle` of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Then add validation annotations to all request DTOs: `FundTransferRequest` (fromAccount @NotBlank, toAccount @NotBlank, amount @NotNull @Positive), `UtilityPaymentRequest` (providerId @NotNull, amount @NotNull @Positive, referenceNumber @NotBlank, account @NotBlank), `User` registration (email @NotBlank @Email, password @NotBlank @Size(min=8), identification @NotBlank), and `UserUpdateRequest` (status @NotNull). Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Open a PR.

---

### 1.4 Remove Sensitive Data from Logs

**Gap:** Sensitive data in logs (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, audit all `log.info()` calls in controllers and services that log request objects via `toString()`. In the internet-banking-user-service `UserController.createUser()`, the `User` object containing a password is logged. Replace `request.toString()` with a safe representation that excludes the password field (e.g., log only the email). Also review all other controllers for similar issues. Add `@ToString.Exclude` on the password field of the User DTO if Lombok is used. Open a PR.

---

### 1.5 Fix Balance Calculation Bugs

**Gap:** Transaction integrity issues — double-deduction bug (Critical)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices core-banking-service, fix the balance calculation bugs in `TransactionService`. In `internalFundTransfer()` at line 91, `availableBalance` is set to `actualBalance.subtract(amount)` which double-deducts — it should be `fromBankAccountEntity.getAvailableBalance().subtract(amount)`. The same bug exists at line 100 for the destination account (should use `getAvailableBalance().add(amount)`). In `utilPayment()` at lines 63-64, the same double-deduction bug exists — line 64 should use `fromAccount.getAvailableBalance().subtract(...)` not `fromAccount.getActualBalance().subtract(...)`. Add or update unit tests to verify correct balance calculations after transfers and payments. Open a PR.

---

### 1.6 Add Timeout Configuration for Feign Clients

**Gap:** No timeout configuration (High)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Feign client timeout configuration. In each service that uses Feign (internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service), add the following to the Spring Cloud Config or application.yml: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also set `spring.cloud.gateway.httpclient.connect-timeout: 5000` and `spring.cloud.gateway.httpclient.response-timeout: 10s` for the API Gateway. Open a PR.

---

### 1.7 Fix Swagger/OpenAPI Starter

**Gap:** Swagger/OpenAPI partially configured (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, the business services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service) incorrectly use `springdoc-openapi-starter-webflux-ui` even though they are Spring MVC (not WebFlux) applications. Replace this with `springdoc-openapi-starter-webmvc-ui:2.1.0` in each service's `build.gradle`. Also add typed `ResponseEntity<T>` generics to all controller methods so OpenAPI can generate accurate response schemas. Add a global OpenAPI configuration bean with API title, version, and description. Open a PR.

---

### 1.8 Add Pagination Metadata to List Responses

**Gap:** No pagination metadata (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, update all list endpoints to return pagination metadata. Change controller methods that accept `Pageable` to return `Page<T>` or a custom `PageResponse<T>` wrapper (with fields: content, totalElements, totalPages, pageNumber, pageSize) instead of raw `List<T>`. Update: core-banking-service `UserController.readUsers()`, internet-banking-user-service `UserController.readUsers()`, internet-banking-fund-transfer-service `FundTransferController.readFundTransfers()`, and internet-banking-utility-payment-service `UtilityPaymentController.readPayments()`. Open a PR.

---

### 1.9 Fix Keycloak Singleton Thread Safety

**Gap:** Keycloak singleton not thread-safe (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices internet-banking-user-service, the `KeycloakProperties.getInstance()` method has a race condition in its lazy singleton pattern. Refactor it to use a Spring `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once, or use the `synchronized` keyword / double-checked locking. The simplest fix: remove the static singleton pattern and make `getInstance()` return a `@Bean`-managed instance. Open a PR.

---

## Phase 2: Important

### 2.1 Add Unit Tests to All Services

**Gap:** Tests only in core-banking-service (Critical)
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests to the 3 business services that currently lack them. For each service, create test configuration files (`src/test/resources/application.yml`) with H2 in-memory database and disabled Eureka/Config Server. Then write unit tests: (1) internet-banking-user-service: test `UserService.createUser()` (happy path, duplicate email, invalid email, user not found in core), `readUsers()`, `readUser()`, `updateUser()` — mock `KeycloakUserService` and `BankingCoreRestClient`. (2) internet-banking-fund-transfer-service: test `FundTransferService.fundTransfer()` (happy path, Feign failure handling), `readAllTransfers()` — mock `BankingCoreFeignClient`. (3) internet-banking-utility-payment-service: test `UtilityPaymentService.utilPayment()` (happy path, Feign failure), `readPayments()` — mock `BankingCoreRestClient`. Aim for >80% line coverage on service classes. Open a PR.

---

### 2.2 Add Circuit Breakers with Resilience4j

**Gap:** No circuit breakers (Critical)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers to all Feign client calls. Add `spring-cloud-starter-circuitbreaker-resilience4j` to the `build.gradle` of internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Configure circuit breakers in each service's application config with: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=5. Add `@CircuitBreaker` annotations to the Feign client interfaces or wrap calls in service methods with fallback methods that return meaningful error responses. For fund transfers: fallback should set local status to FAILED. For utility payments: similar FAILED status. For user registration: return a clear error message. Open a PR.

---

### 2.3 Add Retry Policies

**Gap:** No retry policies (High)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Spring Retry support to Feign clients. Add `spring-retry` dependency to the `build.gradle` of internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Enable retry via `spring.cloud.openfeign.client.config.default.retryer` or configure Resilience4j retry alongside the circuit breakers from the previous item. Configure: maxAttempts=3, waitDuration=1s, retryExceptions=[IOException, FeignException.ServiceUnavailable]. Ensure non-idempotent operations (POST fund-transfer) are NOT retried to prevent duplicate transactions. Open a PR.

---

### 2.4 Add Feign Error Handling to All Services

**Gap:** No Feign error handling for Fund Transfer / Utility Payment (High)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add proper Feign error decoders to internet-banking-fund-transfer-service and internet-banking-utility-payment-service (the user-service already has `CustomFeignErrorDecoder`). Create a `CustomFeignErrorDecoder implements ErrorDecoder` in each service that: (1) maps 404 responses to `EntityNotFoundException`, (2) maps 400 responses to `SimpleBankingGlobalException` with the error details, (3) maps 5xx responses to a retriable exception. Register it in the `CustomFeignClientConfiguration`. Also update `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` to catch Feign failures and update the local record status to `FAILED` instead of leaving it as `PENDING`/`PROCESSING`. Open a PR.

---

### 2.5 Implement Role-Based Authorization

**Gap:** No authorization beyond authentication (High)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, implement role-based authorization. In Keycloak, ensure the realm has roles: `ROLE_USER` and `ROLE_ADMIN`. In the API Gateway `SecurityConfiguration`, add role-based path matchers: admin-only endpoints (user approval `PATCH /user/api/v1/bank-users/update/**`) require `ROLE_ADMIN`; user endpoints (fund transfer, utility payment, user profile) require `ROLE_USER`. Extract roles from the JWT token's realm_access.roles claim. Add a custom `ReactiveJwtAuthenticationConverter` to map Keycloak roles to Spring Security authorities. Also ensure fund transfer and utility payment services validate that the authenticated user owns the source account (pass the `X-Auth-Id` header to Core Banking for ownership verification). Open a PR.

---

### 2.6 Externalize Secrets from Docker Compose

**Gap:** Hardcoded credentials (Critical)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, externalize all hardcoded credentials from Docker Compose files and SQL scripts. Replace hardcoded passwords with environment variable references: `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`, `KC_DB_PASSWORD: ${KC_DB_PASSWORD}`, `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}`. Create a `.env.example` file documenting all required environment variables with placeholder values. Update `docker-compose/mysql/Dockerfile` to use `ARG`/`ENV` for the root password. Update `privileges.sql` to use a variable or move user creation to an entrypoint script. Add `.env` to `.gitignore`. Open a PR.

---

### 2.7 Add Structured Logging

**Gap:** Inconsistent logging (Medium)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add structured JSON logging to all services. Add `logback-spring.xml` to each service's `src/main/resources/` that outputs JSON-formatted logs in non-local profiles (using `net.logstash.logback:logstash-logback-encoder`). Include traceId and spanId from Micrometer in the MDC. Add the `logstash-logback-encoder` dependency to each service's `build.gradle`. Configure console output for local development and JSON output for docker/production profiles. Also reduce the Feign logging level from `FULL` to `BASIC` in the `CustomFeignClientConfiguration` classes. Open a PR.

---

### 2.8 Add Custom Health Indicators

**Gap:** No health check customization (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add custom health indicators. In each business service that calls Core Banking via Feign, add a custom `HealthIndicator` implementation that pings the Core Banking actuator health endpoint. In the user service, add a health indicator that checks Keycloak availability. Configure actuator endpoints in each service: expose `health`, `info`, `metrics`, and `prometheus` endpoints. Set `management.endpoint.health.show-details=when_authorized` and `management.health.defaults.enabled=true`. Ensure the API Gateway's security config allows unauthenticated access to `/actuator/health` paths. Open a PR.

---

### 2.9 Configure Prometheus Metrics

**Gap:** No metrics endpoints (Medium)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics support. Add `io.micrometer:micrometer-registry-prometheus` to the `build.gradle` of all services. Expose the `/actuator/prometheus` endpoint by adding `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to each service's configuration. Add custom business metrics using Micrometer: a Counter for fund transfers (tagged by status), a Counter for utility payments (tagged by status), a Timer for Core Banking API call durations, and a Gauge for active Keycloak sessions. Open a PR.

---

## Phase 3: Polish

### 3.1 Create Shared Library for Common Code

**Gap:** Duplicated code across services (High)
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, create a shared library module to eliminate code duplication. Create a new `internet-banking-common` module with its own `build.gradle` (plain Java library, no Spring Boot plugin). Move these shared classes into it: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalErrorCode`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditConfig`, `AuditorAwareConfig`. Create a root `settings.gradle` that includes all 7 modules. Update each service's `build.gradle` to add `implementation project(':internet-banking-common')` and remove the local copies of these classes. Ensure all services still compile and tests pass. Open a PR.

---

### 3.2 Add Integration Tests with Testcontainers

**Gap:** No integration tests (High)
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers for MySQL. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies to each business service. Create `@SpringBootTest` integration tests that: (1) core-banking-service: test the full REST API via MockMvc against a real MySQL container, verify fund transfer and utility payment flows end-to-end within the service. (2) internet-banking-user-service: test user CRUD endpoints with a MySQL container (mock Keycloak and Core Banking Feign client). (3) internet-banking-fund-transfer-service: test transfer endpoints with MySQL (mock Core Banking Feign). (4) internet-banking-utility-payment-service: test payment endpoints with MySQL (mock Core Banking Feign). Use `@DynamicPropertySource` to inject Testcontainers datasource properties. Open a PR.

---

### 3.3 Add Contract Tests Between Services

**Gap:** No contract tests (High)
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests to verify Feign client contracts. In core-banking-service (the producer), add `spring-cloud-starter-contract-verifier` and define contracts in `src/test/resources/contracts/` for all endpoints consumed by other services: `GET /api/v1/user/{identification}`, `GET /api/v1/account/bank-account/{account_number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`. Generate contract test stubs. In the consumer services (user, fund-transfer, utility-payment), add `spring-cloud-starter-contract-stub-runner` and write tests that verify each Feign client works against the generated stubs. This ensures API changes in core-banking-service don't silently break consumers. Open a PR.

---

### 3.4 Implement Saga Pattern for Distributed Transactions

**Gap:** Transaction integrity issues — no compensating transactions (Critical)
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, implement a choreography-based saga pattern for fund transfers and utility payments. When the Fund Transfer Service initiates a transfer: (1) save local record as PENDING, (2) call Core Banking, (3) if Core Banking succeeds, update to SUCCESS, (4) if Core Banking fails or times out, update to FAILED and publish a compensation event. Add a scheduled job (`@Scheduled`) that checks for records stuck in PENDING/PROCESSING status for more than 5 minutes and either retries or marks them as FAILED. For now, implement this with a simple status-checking mechanism; in the future this can be evolved to use RabbitMQ events. Add the same pattern to the Utility Payment Service. Open a PR.

---

### 3.5 Standardize Project Structure Across Services

**Gap:** Inconsistent package structure (Low)
**Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, standardize the package structure across all business services to use a consistent layout: `controller/`, `service/`, `service/rest/` (for Feign clients), `model/entity/`, `model/dto/`, `model/dto/request/`, `model/dto/response/`, `model/mapper/`, `repository/`, `configuration/`, `exception/`. Rename packages in internet-banking-user-service (move `model.rest.response` -> `model.dto.response`, `model.repository` -> `repository`), internet-banking-utility-payment-service (move `model.rest.request` -> `model.dto.request`, `model.rest.response` -> `model.dto.response`). Ensure no functionality changes, just package reorganization. Update all imports accordingly. Open a PR.

---

### 3.6 Add Multi-Project Gradle Build

**Gap:** No multi-project Gradle build (Medium)
**Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, create a multi-project Gradle build. Add a root `settings.gradle` that includes all service modules. Add a root `build.gradle` with a `subprojects` block that centralizes: Java 21 source compatibility, common repositories (mavenCentral), Spring Boot and Spring Cloud dependency management, common test configuration (JUnit Platform). Create a `gradle/libs.versions.toml` version catalog to centralize all dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector, Lombok, etc.). Update each service's `build.gradle` to use the version catalog references and remove duplicated version declarations. Ensure all services still build independently and the full project builds with `./gradlew build` from the root. Open a PR.

---

### 3.7 Implement Notification Service

**Gap:** RabbitMQ integration planned but not implemented
**Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, implement the notification service that is mentioned in the README but not yet built. Create a new `internet-banking-notification-service` module with Spring Boot, Spring AMQP (RabbitMQ), and Spring Cloud dependencies. Add RabbitMQ to the Docker Compose configuration. In the fund-transfer-service and utility-payment-service, add `spring-boot-starter-amqp` and publish messages to a `banking.notifications` exchange after successful transactions. The notification service should consume these messages and log them (as a placeholder for email/SMS sending). Include: a `NotificationEntity` to store notification history, a `NotificationService`, and a REST endpoint to query notification history. Register the service with Eureka. Open a PR.

---

## Roadmap Summary

| Phase | Item | Gap Severity | Effort | Description |
|-------|------|-------------|--------|-------------|
| **1** | 1.1 | Critical | Small | Fix HTTP status codes |
| **1** | 1.2 | High | Small | Stop leaking exception details |
| **1** | 1.3 | Critical | Medium | Add input validation |
| **1** | 1.4 | Medium | Small | Remove sensitive data from logs |
| **1** | 1.5 | Critical | Small | Fix balance calculation bugs |
| **1** | 1.6 | High | Small | Add Feign timeout configuration |
| **1** | 1.7 | Medium | Small | Fix Swagger/OpenAPI starter |
| **1** | 1.8 | Medium | Small | Add pagination metadata |
| **1** | 1.9 | Medium | Small | Fix Keycloak singleton thread safety |
| **2** | 2.1 | Critical | Large | Add unit tests to all services |
| **2** | 2.2 | Critical | Medium | Add circuit breakers |
| **2** | 2.3 | High | Small | Add retry policies |
| **2** | 2.4 | High | Medium | Add Feign error handling |
| **2** | 2.5 | High | Medium | Implement role-based authorization |
| **2** | 2.6 | Critical | Small | Externalize secrets |
| **2** | 2.7 | Medium | Medium | Add structured logging |
| **2** | 2.8 | Medium | Small | Add custom health indicators |
| **2** | 2.9 | Medium | Small | Configure Prometheus metrics |
| **3** | 3.1 | High | Large | Create shared library |
| **3** | 3.2 | High | Large | Add integration tests |
| **3** | 3.3 | High | Large | Add contract tests |
| **3** | 3.4 | Critical | Large | Implement saga pattern |
| **3** | 3.5 | Low | Small | Standardize project structure |
| **3** | 3.6 | Medium | Medium | Multi-project Gradle build |
| **3** | 3.7 | N/A | Large | Implement notification service |
