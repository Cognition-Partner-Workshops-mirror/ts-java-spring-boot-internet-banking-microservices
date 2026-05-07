# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases based on risk, impact, and effort.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be resolved with small effort. Focus on critical security and correctness issues.

### 1.1 Fix Double-Deduction Balance Bug

**Gap Ref:** Resilience 7.5 | **Severity:** Critical | **Effort:** Small

The `availableBalance` calculation in `TransactionService` subtracts the amount from the already-debited `actualBalance`, causing a double deduction.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In the `internalFundTransfer()` method, `setAvailableBalance` is called with `getActualBalance().subtract(amount)` after `actualBalance` has already been reduced. The available balance should equal the new actual balance (or use a separate calculation). The same bug exists in `utilPayment()`. Fix both methods and update the existing unit tests in `TransactionServiceTest.java` to verify correct balance calculations after transfers and payments.

---

### 1.2 Add Input Validation to All Request DTOs

**Gap Ref:** Security 4.1 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services. Specifically:
> - `core-banking-service` `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`
> - `core-banking-service` `UtilityPaymentRequest`: `@NotBlank` on `account` and `referenceNumber`, `@NotNull @Positive` on `amount` and `providerId`
> - `internet-banking-fund-transfer-service` `FundTransferRequest`: same as above plus `@NotBlank` on `authID`
> - `internet-banking-utility-payment-service` `UtilityPaymentRequest`: `@NotBlank` on `account` and `referenceNumber`, `@NotNull @Positive` on `amount` and `providerId`
> - `internet-banking-user-service` `User` DTO: `@NotBlank` on `email`, `identification`, `password`; `UserUpdateRequest`: `@NotNull` on `status`
>
> Add `@Valid` to all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with structured error details.

---

### 1.3 Fix HTTP Status Codes in Error Handlers

**Gap Ref:** Error Handling 2.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Update the `GlobalExceptionHandler` in all three services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return appropriate HTTP status codes:
> - `EntityNotFoundException` → HTTP 404 Not Found
> - `InsufficientFundsException` → HTTP 422 Unprocessable Entity
> - `SimpleBankingGlobalException` → HTTP 400 Bad Request
> - Generic `Exception` → HTTP 500 Internal Server Error (remove the raw exception message from the response body — return a generic "Internal server error" message instead)
>
> Ensure all error responses use the structured `ErrorResponse` format with `code` and `message` fields. Also add generic type parameters to all `ResponseEntity` return types in controllers (e.g., `ResponseEntity<BankAccount>` instead of raw `ResponseEntity`).

---

### 1.4 Remove Sensitive Data from Logs

**Gap Ref:** Security 4.6, Observability 6.1 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Audit all `log.info()` calls across all services and remove logging of sensitive data. Specifically:
> - In `internet-banking-user-service` `UserController.createUser()`, do not log the full request (which includes password). Log only the email.
> - In `User.java` DTO in user-service, add `@ToString.Exclude` on the `password` field to prevent it from appearing in any `toString()` output.
> - In all controllers, avoid logging full request bodies. Log only identifiers (account numbers, user IDs, transaction IDs).
> - In `GlobalExceptionHandler.handleException()` across all services, do not include the raw exception in the HTTP response body. Log it server-side at ERROR level instead.

---

### 1.5 Fix Keycloak Singleton Thread Safety

**Gap Ref:** Security 4.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Refactor `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java` to use a thread-safe initialization pattern. Replace the manual singleton with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once during Spring context startup. Remove the static field and the `getInstance()` method. Update `KeycloakManager` to inject the `Keycloak` bean directly.

---

### 1.6 Fix OpenAPI Dependency for MVC Services

**Gap Ref:** API Design 5.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `build.gradle` of core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service, replace:
> ```
> implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
> ```
> with:
> ```
> implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
> ```
> These services use Spring MVC, not WebFlux. Only the API Gateway should use the webflux variant (and it doesn't currently include springdoc). Verify each service starts correctly after the change.

---

### 1.7 Return Pagination Metadata

**Gap Ref:** API Design 5.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Update all paginated GET endpoints across all services to return `Page<T>` (or a custom pagination wrapper) instead of `List<T>`. The response should include `content`, `totalElements`, `totalPages`, `number` (current page), and `size`. Affected endpoints:
> - `core-banking-service` `UserController.readUsers()` — return `Page<User>`
> - `internet-banking-fund-transfer-service` `FundTransferController.readFundTransfers()` — return `Page<FundTransfer>`
> - `internet-banking-user-service` `UserController.readUsers()` — return `Page<User>`
> - `internet-banking-utility-payment-service` `UtilityPaymentController.readPayments()` — return `Page<UtilityPayment>`

---

## Phase 2: Important Improvements (3-6 weeks)

Structural improvements that significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers and Resilience Patterns

**Gap Ref:** Resilience 7.1, 7.2, 7.3, 7.4 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breaker, retry, and timeout patterns to all Feign client calls across the three business services:
>
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle` of fund-transfer-service, utility-payment-service, and user-service.
> 2. Configure Resilience4j in each service's `application.yml`:
>    - Circuit breaker: sliding window size 10, failure rate threshold 50%, wait duration in open state 30s
>    - Retry: max attempts 3, wait duration 1s, exponential backoff
>    - Timeout: 5s for all Feign calls
> 3. Add `@CircuitBreaker` and `@Retry` annotations to service methods that call Feign clients.
> 4. Add fallback methods that return appropriate error responses when the circuit is open.
> 5. For `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, on Feign failure update the entity status to `FAILED` instead of leaving it in `PENDING`/`PROCESSING`.

---

### 2.2 Add Idempotency Keys to Financial Operations

**Gap Ref:** Resilience 7.6 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Implement idempotency for fund transfer and utility payment endpoints:
> 1. Add an `X-Idempotency-Key` header requirement to `POST /api/v1/transfer` and `POST /api/v1/utility-payment`.
> 2. Create an `idempotency_key` column in both `fund_transfer` and `utility_payment` tables with a unique constraint.
> 3. Before processing a request, check if a record with the same idempotency key exists. If so, return the existing result instead of processing again.
> 4. Add a filter or interceptor that validates the presence of the idempotency key header on POST requests.
> 5. Add tests to verify that duplicate requests with the same idempotency key return the same result without creating duplicate transactions.

---

### 2.3 Secure Downstream Services

**Gap Ref:** Security 4.8 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add authentication to all downstream services so they cannot be accessed directly bypassing the API Gateway:
> 1. Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to core-banking-service, fund-transfer-service, utility-payment-service, and user-service.
> 2. Create a `SecurityConfig` in each service that:
>    - Validates JWT tokens from Keycloak (same issuer as the gateway)
>    - Permits actuator endpoints without auth
>    - Requires authentication for all other endpoints
> 3. Configure the API Gateway to propagate the JWT token downstream (TokenRelay filter).
> 4. Update Feign client configurations to include the JWT token in outgoing requests (use a `RequestInterceptor`).
> 5. Test that direct access to downstream services without a valid JWT is rejected with HTTP 401.

---

### 2.4 Create Shared Library Module

**Gap Ref:** Code Organization 1.2 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a shared library module `internet-banking-common` and extract duplicated code:
> 1. Create a new Gradle module `internet-banking-common` with a `build.gradle` that publishes a JAR.
> 2. Move these classes into the shared module:
>    - `BaseMapper` (generic mapper interface)
>    - `AuditAware` (audit timestamp base class)
>    - `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`
>    - `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
>    - `CustomFeignClientConfiguration`
>    - `AuditConfig`, `AuditorAwareConfig`
> 3. Add `implementation project(':internet-banking-common')` to each service's `build.gradle`.
> 4. Create a root `settings.gradle` that includes all services and the common module.
> 5. Remove the duplicated classes from each service.
> 6. Verify all services compile and tests pass.

---

### 2.5 Add Unit Tests for All Services

**Gap Ref:** Testing 3.1 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for the three business services that currently have no tests:
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferServiceTest`: Test `fundTransfer()` success, Feign failure handling, and `readAllTransfers()` pagination. Mock `FundTransferRepository` and `BankingCoreFeignClient`.
> - `FundTransferControllerTest`: Test POST and GET endpoints using `@WebMvcTest` with mocked service.
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentServiceTest`: Test `utilPayment()` success, Feign failure handling, and `readPayments()` pagination. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`.
> - `UtilityPaymentControllerTest`: Test POST and GET endpoints using `@WebMvcTest` with mocked service.
>
> **internet-banking-user-service:**
> - `UserServiceTest`: Test `createUser()` with various scenarios (duplicate email, invalid email, user not found in core banking, successful creation), `readUsers()`, `readUser()`, `updateUser()` with approval flow. Mock `KeycloakUserService`, `UserRepository`, and `BankingCoreRestClient`.
> - `KeycloakUserServiceTest`: Test Keycloak interactions with mocked `KeycloakManager`.
>
> Target: minimum 80% line coverage per service.

---

### 2.6 Add Feign Client Timeout and Retry Configuration

**Gap Ref:** Resilience 7.2, 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add explicit timeout and retry configuration for all Feign clients:
> 1. In each service's `application.yml`, add:
>    ```yaml
>    spring:
>      cloud:
>        openfeign:
>          client:
>            config:
>              default:
>                connectTimeout: 5000
>                readTimeout: 10000
>                loggerLevel: basic
>    ```
> 2. Add Spring Retry dependency and enable `@Retryable` for transient failures:
>    ```
>    implementation 'org.springframework.retry:spring-retry'
>    ```
> 3. Configure retry for connection timeouts only (not for 4xx errors).

---

### 2.7 Add Health Checks and Prometheus Metrics

**Gap Ref:** Observability 6.2, 6.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Enhance observability across all services:
> 1. Add `micrometer-registry-prometheus` dependency to all services.
> 2. Expose Prometheus metrics endpoint by adding to each service's config:
>    ```yaml
>    management:
>      endpoints:
>        web:
>          exposure:
>            include: health,info,prometheus,metrics
>      endpoint:
>        health:
>          show-details: always
>    ```
> 3. Add custom health indicators in services that depend on external resources:
>    - `core-banking-service`: Database health (auto-configured by Spring), Eureka health
>    - `fund-transfer-service`: Add a health indicator that checks core-banking-service availability via Feign
>    - `user-service`: Add Keycloak connectivity health indicator
> 4. Add custom Micrometer metrics:
>    - Counter for fund transfers (success/failure)
>    - Counter for utility payments (success/failure)
>    - Timer for Feign client call duration

---

### 2.8 Move Secrets to Environment Variables

**Gap Ref:** Security 4.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Remove all hardcoded credentials from `docker-compose.yml`, `docker-compose-support-apps.yml`, `mysql/Dockerfile`, and `privileges.sql`:
> 1. Replace hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`).
> 2. Create a `.env.example` file with placeholder values.
> 3. Add `.env` to `.gitignore`.
> 4. Update `mysql/Dockerfile` to use `ARG`/`ENV` instead of hardcoded passwords.
> 5. Update `README.md` with instructions to create a `.env` file from the example.
> 6. Remove the test credentials from `README.md` and reference the `.env.example` file instead.

---

## Phase 3: Polish (6-12 weeks)

Improvements for long-term maintainability, developer experience, and operational excellence.

### 3.1 Add Contract Tests Between Services

**Gap Ref:** Testing 3.2 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Implement consumer-driven contract tests using Spring Cloud Contract:
> 1. Add `spring-cloud-starter-contract-verifier` to core-banking-service (provider).
> 2. Add `spring-cloud-starter-contract-stub-runner` to fund-transfer-service, utility-payment-service, and user-service (consumers).
> 3. Define contracts for each Feign client endpoint:
>    - `GET /api/v1/account/bank-account/{number}` — success and not-found cases
>    - `POST /api/v1/transaction/fund-transfer` — success and insufficient-funds cases
>    - `POST /api/v1/transaction/util-payment` — success case
>    - `GET /api/v1/user/{identification}` — success and not-found cases
> 4. Generate stubs from contracts in core-banking-service.
> 5. Use stubs in consumer service tests to verify Feign client compatibility.
> 6. Add contract test execution to the CI pipeline.

---

### 3.2 Set Up Multi-Module Gradle Build

**Gap Ref:** Code Organization 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Convert the project to a Gradle multi-module build:
> 1. Create a root `settings.gradle` that includes all 6 services and the common module.
> 2. Create a root `build.gradle` with shared configuration (Java version, Spring Boot version, Spring Cloud version, common repositories).
> 3. Simplify each service's `build.gradle` to only declare service-specific dependencies.
> 4. Remove individual Gradle wrappers from each service; use a single root wrapper.
> 5. Verify `./gradlew build` from the root builds all services.
> 6. Verify `./gradlew test` from the root runs all tests.

---

### 3.3 Add Rate Limiting at API Gateway

**Gap Ref:** Security 4.5 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter:
> 1. Add `spring-boot-starter-data-redis-reactive` to the API Gateway's `build.gradle`.
> 2. Add Redis to `docker-compose.yml`.
> 3. Configure rate limiting per route:
>    - Fund transfer: 10 requests/minute per user
>    - Utility payment: 10 requests/minute per user
>    - User registration: 5 requests/minute per IP
>    - Read endpoints: 100 requests/minute per user
> 4. Use the JWT subject claim as the key resolver for authenticated routes.
> 5. Return HTTP 429 Too Many Requests with a `Retry-After` header when limit is exceeded.
> 6. Add integration tests to verify rate limiting behavior.

---

### 3.4 Implement the Notification Service

**Gap Ref:** Documented as "PENDING Development" | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Implement the notification service that was documented but never built:
> 1. Create a new Spring Boot service `internet-banking-notification-service`.
> 2. Add RabbitMQ dependency (`spring-boot-starter-amqp`).
> 3. Add RabbitMQ to `docker-compose.yml`.
> 4. Define message queues for fund transfer notifications and utility payment notifications.
> 5. In fund-transfer-service and utility-payment-service, publish messages to RabbitMQ after successful transactions.
> 6. In notification-service, consume messages and log them (placeholder for email/SMS integration).
> 7. Register the service with Eureka and add tracing dependencies.
> 8. Add unit tests and Docker configuration.

---

### 3.5 Add Structured Logging and Log Aggregation

**Gap Ref:** Observability 6.1, 6.5 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Implement structured JSON logging and prepare for centralized log aggregation:
> 1. Add `logstash-logback-encoder` dependency to all services.
> 2. Create a shared `logback-spring.xml` that outputs JSON-formatted logs with fields: `timestamp`, `level`, `service`, `traceId`, `spanId`, `message`, `exception`.
> 3. Include the trace ID and span ID from Micrometer Tracing in all log entries.
> 4. Add a request correlation ID (from `X-Request-Id` header or auto-generated UUID) to the MDC context.
> 5. Configure console output for development and file/JSON output for Docker.
> 6. Optionally, add a Loki + Grafana setup to `docker-compose.yml` for local log aggregation.

---

### 3.6 Add Integration Tests with Testcontainers

**Gap Ref:** Testing 3.1, 3.4 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Add integration tests using Testcontainers for database and external service testing:
> 1. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` to each service's test dependencies.
> 2. Create integration test base classes that start MySQL containers.
> 3. For core-banking-service: test full CRUD flows against a real MySQL instance with Flyway migrations.
> 4. For fund-transfer-service and utility-payment-service: use WireMock to mock core-banking-service responses and test full flows.
> 5. For user-service: use Testcontainers to start a Keycloak instance and test the full registration/approval flow.
> 6. Configure a separate Gradle task `integrationTest` that runs these tests.

---

### 3.7 Add Dependency Vulnerability Scanning

**Gap Ref:** Security 4.7 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add automated dependency vulnerability scanning to the build:
> 1. Add the OWASP Dependency-Check Gradle plugin to the root `build.gradle`:
>    ```gradle
>    plugins {
>        id 'org.owasp.dependencycheck' version '9.0.9'
>    }
>    ```
> 2. Configure it to fail the build on CVSS score >= 7.
> 3. Generate HTML reports in `build/reports/dependency-check/`.
> 4. Add a GitHub Actions workflow that runs `./gradlew dependencyCheckAnalyze` on PRs.
> 5. Review and address any current vulnerabilities found in the initial scan.

---

### 3.8 Standardize Package Structure

**Gap Ref:** Code Organization 1.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all services to follow a consistent pattern:
> ```
> com.javatodev.finance
> ├── configuration/       # Spring @Configuration classes
> │   ├── security/        # Security configuration
> │   ├── feign/           # Feign client configuration
> │   └── audit/           # Audit configuration
> ├── controller/          # REST controllers
> ├── service/             # Business logic
> │   └── client/          # Feign client interfaces
> ├── repository/          # Spring Data repositories
> ├── model/
> │   ├── entity/          # JPA entities
> │   ├── dto/             # DTOs (request/response)
> │   │   ├── request/     # Request DTOs
> │   │   └── response/    # Response DTOs
> │   ├── mapper/          # Entity-DTO mappers
> │   └── enums/           # Enumerations
> └── exception/           # Exception classes
> ```
> Refactor the utility-payment-service (which uses `model.rest.request/response` instead of `model.dto.request/response`) and user-service to match this structure. Update all imports accordingly.

---

## Summary

| Phase | Items | Critical Fixes | Estimated Effort |
|---|---|---|---|
| **Phase 1: Quick Wins** | 7 items | Balance bug, input validation | 1-2 weeks |
| **Phase 2: Important** | 8 items | Circuit breakers, idempotency, tests, auth | 3-6 weeks |
| **Phase 3: Polish** | 8 items | Contracts, rate limiting, notifications | 6-12 weeks |

### Priority Order Within Each Phase

**Phase 1** (do in this order):
1. Fix double-deduction balance bug (data integrity)
2. Add input validation (security)
3. Fix HTTP status codes (API correctness)
4. Remove sensitive data from logs (security)
5. Fix Keycloak thread safety (correctness)
6. Fix OpenAPI dependency (developer experience)
7. Return pagination metadata (API quality)

**Phase 2** (do in this order):
1. Add circuit breakers (prevent cascading failures)
2. Add idempotency keys (prevent duplicate transactions)
3. Secure downstream services (close security gap)
4. Add unit tests (catch regressions)
5. Add Feign timeouts (prevent hangs)
6. Create shared library (reduce tech debt)
7. Add health checks and metrics (operational visibility)
8. Move secrets to env vars (security hygiene)

**Phase 3** (flexible order):
1. Contract tests
2. Multi-module Gradle build
3. Rate limiting
4. Notification service
5. Structured logging
6. Integration tests
7. Dependency scanning
8. Package structure standardization
