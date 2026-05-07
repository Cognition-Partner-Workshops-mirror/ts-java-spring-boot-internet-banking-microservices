# Remediation Roadmap

This roadmap organizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on risk reduction, effort, and dependency ordering. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort items that reduce critical risk immediately.

### 1.1 Fix Exception Stack Trace Leak & Catch-All HTTP Status

**Gaps:** 2.1, 2.2 | **Severity:** Critical | **Effort:** Small

Update every `GlobalExceptionHandler` to return proper HTTP status codes and never expose internal exception details.

**Devin Prompt:**
> In all microservices in this repo, update every `GlobalExceptionHandler` class:
> 1. Change the catch-all `@ExceptionHandler({Exception.class})` to return HTTP 500 with a generic `ErrorResponse` body (`code: "INTERNAL_ERROR"`, `message: "An unexpected error occurred"`). Never include `e.toString()` or stack traces in the response.
> 2. Add a dedicated `@ExceptionHandler(EntityNotFoundException.class)` that returns HTTP 404 with `ErrorResponse`.
> 3. Keep the existing `SimpleBankingGlobalException` handler but change it to return the appropriate HTTP status (400 for validation errors, 404 for not-found, etc.).
> 4. Ensure all handlers return `ResponseEntity<ErrorResponse>` (typed).
> Make sure existing tests still pass.

---

### 1.2 Add Bean Validation to All Request DTOs

**Gaps:** 4.1 | **Severity:** Critical | **Effort:** Medium

Add `spring-boot-starter-validation` and annotate all `@RequestBody` DTOs.

**Devin Prompt:**
> Add input validation across all microservices:
> 1. Add `spring-boot-starter-validation` dependency to `build.gradle` for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
> 2. Annotate all `@RequestBody` parameters in controllers with `@Valid`.
> 3. Add Bean Validation annotations to all request DTOs:
>    - `FundTransferRequest`: `@NotBlank` on fromAccount/toAccount, `@NotNull @Positive` on amount
>    - `UtilityPaymentRequest`: `@NotBlank` on account/referenceNumber, `@NotNull @Positive` on amount, `@NotNull` on providerId
>    - `User` (registration): `@NotBlank @Email` on email, `@NotBlank` on identification/password
>    - `UserUpdateRequest`: `@NotNull` on status
> 4. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` returning 400 with field-level error details.
> Make sure existing tests still pass.

---

### 1.3 Mask Password in User DTO Response

**Gaps:** 4.5 | **Severity:** High | **Effort:** Small

Prevent the password from being serialized in API responses.

**Devin Prompt:**
> In `internet-banking-user-service`, add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field in `com.javatodev.finance.model.dto.User`. This ensures the password is accepted on registration requests but never included in responses.

---

### 1.4 Remove Sensitive Data from Log Statements

**Gaps:** 6.2 | **Severity:** High | **Effort:** Small

Replace `.toString()` logging of request objects with safe field logging.

**Devin Prompt:**
> Across all microservices, find every `log.info` or `log.error` call that logs a full request object using `.toString()` and replace it with logging only non-sensitive fields. For example:
> - `FundTransferController`: Log only `fromAccount` and `toAccount` (not amount)
> - `UserController` (user-service): Log only email (never password)
> - `UtilityPaymentService`: Log only `providerId` and `referenceNumber`
> Do not remove the log statements — just make them safe.

---

### 1.5 Configure Feign Timeouts

**Gaps:** 7.3 | **Severity:** High | **Effort:** Small

Set explicit connection and read timeouts for all Feign clients.

**Devin Prompt:**
> Add Feign timeout configuration to the `application.yml` (or equivalent Spring Cloud Config properties) for internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service:
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
> Since these services get their main config from Spring Cloud Config Server, add this to the local `application.yml` as a baseline that can be overridden by the config server.

---

### 1.6 Add Feign Retry Configuration

**Gaps:** 7.2 | **Severity:** High | **Effort:** Small

Configure basic retry for transient failures.

**Devin Prompt:**
> For each Feign-using service (fund-transfer, utility-payment, user-service), add a Feign `Retryer` bean in the configuration:
> ```java
> @Bean
> public Retryer retryer() {
>     return new Retryer.Default(100, 1000, 3); // 100ms initial, 1s max, 3 attempts
> }
> ```
> Only retry on connection-level errors and 503s, not on 4xx client errors. Add a `FeignErrorDecoder` to each service (fund-transfer and utility-payment already have `CustomFeignClientConfiguration` where this can go).

---

### 1.7 Fix Raw ResponseEntity Types

**Gaps:** 5.1 | **Severity:** Medium | **Effort:** Small

Add type parameters to all `ResponseEntity` return types.

**Devin Prompt:**
> Across all controllers in all microservices, change raw `ResponseEntity` return types to their proper generic types. For example:
> - `AccountController.getBankAccount` → `ResponseEntity<BankAccount>`
> - `TransactionController.fundTransfer` → `ResponseEntity<FundTransferResponse>`
> - `FundTransferController.readFundTransfers` → `ResponseEntity<List<FundTransfer>>`
> This improves OpenAPI generation and type safety. Make sure existing tests still compile and pass.

---

### 1.8 Fix OpenAPI Dependency (WebFlux → WebMVC)

**Gaps:** 5.5 | **Severity:** Medium | **Effort:** Small

Replace the incorrect Swagger dependency.

**Devin Prompt:**
> In `build.gradle` for core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service, replace:
> ```
> implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
> ```
> with:
> ```
> implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
> ```
> These services use Spring MVC, not WebFlux. The API Gateway (which IS WebFlux) does not need Swagger.

---

### 1.9 Fix Keycloak Singleton Thread Safety

**Gaps:** 4.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In `internet-banking-user-service`, refactor `KeycloakProperties.getInstance()` to use a thread-safe initialization pattern. The simplest fix: remove the static `keycloakInstance` field and instead declare a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once as a Spring singleton bean. Inject it into `KeycloakManager` instead of calling `keycloakProperties.getInstance()`.

---

### 1.10 Fix SimpleBankingGlobalException Message Shadow

**Gaps:** 2.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In all microservices, update `SimpleBankingGlobalException` to not shadow the parent `RuntimeException.message` field. Remove the local `private String message` field and use `super(message)` in the constructors. Keep the `code` field. Update the `@AllArgsConstructor` to a custom constructor that calls `super(message)` and sets `this.code = code`. Ensure `getMessage()` returns the correct value. Run all existing tests to verify.

---

## Phase 2: Important (3–6 weeks)

Structural improvements that require more effort but significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers

**Gaps:** 7.1 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breakers to all inter-service Feign calls:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle` for fund-transfer-service, utility-payment-service, and user-service.
> 2. Enable Feign circuit breaker integration: `spring.cloud.openfeign.circuitbreaker.enabled=true`
> 3. Create fallback classes for each Feign client:
>    - `BankingCoreFeignClientFallback` for fund-transfer: return error response with `FAILED` status
>    - `BankingCoreRestClientFallback` for utility-payment: return error response
>    - `BankingCoreRestClientFallback` for user-service: throw a descriptive exception
> 4. Register fallbacks via `@FeignClient(fallback = ...)`.
> 5. Configure circuit breaker parameters in `application.yml`:
>    ```yaml
>    resilience4j.circuitbreaker:
>      instances:
>        core-banking-service:
>          sliding-window-size: 10
>          failure-rate-threshold: 50
>          wait-duration-in-open-state: 30s
>    ```
> 6. Update the fund transfer and utility payment services to set the transaction entity status to `FAILED` when the circuit breaker triggers a fallback.

---

### 2.2 Extract Shared Library Module

**Gaps:** 1.2, 1.1 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a shared library module and convert the project to a Gradle multi-project build:
> 1. Create a root `settings.gradle` that includes all 7 subprojects (6 services + 1 shared lib).
> 2. Create a root `build.gradle` with common dependency versions and plugins.
> 3. Create `shared-banking-lib/` module containing:
>    - `AuditAware` base class
>    - `BaseMapper` interface
>    - `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` base
>    - `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
>    - Common DTOs like `AccountResponse`
> 4. Update each service's `build.gradle` to use `implementation project(':shared-banking-lib')`.
> 5. Remove duplicated classes from each service.
> 6. Ensure all existing tests still pass.

---

### 2.3 Add Feign Error Handling to Fund Transfer & Utility Payment

**Gaps:** 2.5 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
> 1. Wrap the Feign client calls in `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` with try-catch blocks.
> 2. On Feign exception, update the transaction entity status to `FAILED` before re-throwing.
> 3. Add a `CustomFeignErrorDecoder` (similar to what user-service has) that translates Feign error responses into appropriate service exceptions.
> 4. Add unit tests for the failure scenarios.

---

### 2.4 Implement Role-Based Access Control

**Gaps:** 4.4 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add role-based authorization to the API Gateway and downstream services:
> 1. In Keycloak realm configuration, define roles: `ADMIN`, `CUSTOMER`.
> 2. Update `SecurityConfiguration` in the API Gateway to enforce roles:
>    - `PATCH /user/api/v1/bank-users/update/**` → requires `ADMIN` role
>    - `GET /user/api/v1/bank-users` (list all) → requires `ADMIN` role
>    - All other authenticated endpoints → `CUSTOMER` or `ADMIN`
> 3. Extract roles from the JWT `realm_access.roles` claim using a custom `ReactiveJwtAuthenticationConverter`.
> 4. Add the authenticated user's ID to the `X-Auth-Id` header so downstream services can verify resource ownership (e.g., users can only transfer from their own accounts).

---

### 2.5 Add Structured JSON Logging

**Gaps:** 6.1, 6.6 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Configure structured JSON logging across all microservices:
> 1. Add `logstash-logback-encoder` dependency to each service's `build.gradle`.
> 2. Create a shared `logback-spring.xml` configuration that:
>    - Uses `LogstashEncoder` for JSON output
>    - Includes MDC fields: `traceId`, `spanId` (from Micrometer), `serviceName`, `userId`
>    - Has a console appender for local dev (plain text) and a JSON appender for Docker profile
> 3. Ensure the `X-Auth-Id` header value is added to MDC in `AppAuthUserFilter` so it appears in all log entries for that request.

---

### 2.6 Add Unit Tests for All Services

**Gaps:** 3.1 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for services that currently have zero or minimal test coverage:
> 1. **internet-banking-fund-transfer-service**: Test `FundTransferService` — success flow, Feign failure handling, mapper correctness.
> 2. **internet-banking-utility-payment-service**: Test `UtilityPaymentService` — success flow, Feign failure, mapper.
> 3. **internet-banking-user-service**: Test `UserService` — createUser (happy path, duplicate email, user not in core banking, email mismatch), readUsers, readUser, updateUser (approval flow, status change). Mock `KeycloakUserService` and `BankingCoreRestClient`.
> 4. **internet-banking-user-service**: Test `KeycloakUserService` — mock `KeycloakManager`.
> Use Mockito for mocking, JUnit 5 assertions. Target ≥80% line coverage for service classes.

---

### 2.7 Add Custom Health Checks and Metrics

**Gaps:** 6.3, 6.4 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add custom observability to all business services:
> 1. **Health Checks**: Create a custom `HealthIndicator` in each Feign-using service that pings the target service's `/actuator/health` endpoint. Register it as a Spring bean.
> 2. **Business Metrics**: Add Micrometer counters/timers in each service:
>    - `fund_transfer_total` (counter, tagged by status: SUCCESS/FAILED)
>    - `fund_transfer_duration` (timer)
>    - `utility_payment_total` (counter, tagged by status)
>    - `user_registration_total` (counter)
> 3. Ensure all metrics are exposed via `/actuator/prometheus` by adding `micrometer-registry-prometheus` dependency.

---

### 2.8 Add Rate Limiting to API Gateway

**Gaps:** 7.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add rate limiting to the Spring Cloud Gateway:
> 1. Add `spring-boot-starter-data-redis-reactive` dependency.
> 2. Configure `RequestRateLimiter` filter using Redis:
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
> 3. Add Redis to docker-compose.yml.
> 4. Configure key resolver to rate-limit by authenticated user principal.

---

## Phase 3: Polish (6–12 weeks)

Long-term improvements for production readiness and operational excellence.

### 3.1 Implement Saga Pattern for Fund Transfers

**Gaps:** 7.5 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> Implement a choreography-based Saga pattern for fund transfers:
> 1. Add RabbitMQ (or replace synchronous Feign call for the transaction portion with event-driven communication).
> 2. Define events: `FundTransferInitiated`, `FundTransferCompleted`, `FundTransferFailed`.
> 3. Fund Transfer Service publishes `FundTransferInitiated` → Core Banking consumes and processes → publishes `FundTransferCompleted` or `FundTransferFailed` → Fund Transfer Service updates local entity status.
> 4. Add a compensating transaction in Core Banking: `POST /api/v1/transaction/fund-transfer/reverse/{transactionId}`.
> 5. Implement a scheduled job to detect stuck `PENDING` transactions older than 5 minutes and trigger compensation.
> 6. Add the same pattern for utility payments.
> This is a significant architectural change — start with a design document before implementation.

---

### 3.2 Add Integration Tests with Testcontainers

**Gaps:** 3.2, 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Set up integration test infrastructure using Testcontainers:
> 1. Add `testcontainers` and `testcontainers-mysql` dependencies to each service's `build.gradle`.
> 2. Create a `BaseIntegrationTest` class that:
>    - Starts a MySQL Testcontainer
>    - Configures Spring datasource to use the container
>    - Uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`
> 3. Write integration tests for each service:
>    - Core Banking: Full REST endpoint tests (create user, lookup account, fund transfer, utility payment)
>    - User Service: Test with mocked Keycloak (WireMock) and real MySQL
>    - Fund Transfer: Test with mocked Core Banking (WireMock) and real MySQL
>    - Utility Payment: Test with mocked Core Banking (WireMock) and real MySQL
> 4. Add Testcontainers for Keycloak in user-service integration tests.

---

### 3.3 Add CI/CD Pipeline

**Gaps:** No existing CI/CD | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline:
> 1. Create `.github/workflows/ci.yml` that:
>    - Triggers on push to `main` and pull requests
>    - Sets up Java 21
>    - Runs `./gradlew build` for each service
>    - Runs tests with test results published
>    - Builds Docker images
>    - Runs OWASP dependency-check (gap 4.7)
> 2. Create `.github/workflows/deploy.yml` for deployment (optional, can be a placeholder).
> 3. Add a root-level `Makefile` or `build-all.sh` script to build all services sequentially.

---

### 3.4 Externalize Secrets from Source Control

**Gaps:** 4.2 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
> Remove hardcoded credentials from the repository:
> 1. Replace all hardcoded passwords in `docker-compose.yml` and `docker-compose-support-apps.yml` with environment variable references (`${MYSQL_ROOT_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}`, etc.).
> 2. Create a `.env.example` file documenting all required environment variables with placeholder values.
> 3. Add `.env` to `.gitignore`.
> 4. Update `docker-compose/mysql/privileges.sql` to use the MySQL `MYSQL_USER` and `MYSQL_PASSWORD` environment variables instead of hardcoded values.
> 5. Remove the test credentials from `README.md` and reference the `.env.example` file instead.

---

### 3.5 Add Bulkhead Pattern

**Gaps:** 7.7 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j bulkhead configuration to isolate Feign client thread pools:
> 1. In each Feign-using service, configure thread pool bulkheads:
>    ```yaml
>    resilience4j.thread-pool-bulkhead:
>      instances:
>        core-banking-service:
>          max-thread-pool-size: 10
>          core-thread-pool-size: 5
>          queue-capacity: 20
>    ```
> 2. Ensure each Feign client uses its own bulkhead instance so that a slow endpoint doesn't starve others.

---

### 3.6 Add Contract Tests Between Services

**Gaps:** 3.2 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Add Spring Cloud Contract tests between services:
> 1. Add `spring-cloud-starter-contract-verifier` to core-banking-service (the provider).
> 2. Define contracts in `src/test/resources/contracts/` for each endpoint that Feign clients call:
>    - `GET /api/v1/account/bank-account/{account_number}`
>    - `POST /api/v1/transaction/fund-transfer`
>    - `POST /api/v1/transaction/util-payment`
>    - `GET /api/v1/user/{identification}`
> 3. Generate and publish contract stubs.
> 4. Add `spring-cloud-starter-contract-stub-runner` to consumer services' test dependencies.
> 5. Write consumer-driven contract tests in fund-transfer-service, utility-payment-service, and user-service that verify the Feign clients against the stubs.

---

### 3.7 Standardize API Resource Naming

**Gaps:** 5.3, 5.4, 5.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Refactor API endpoints to follow RESTful naming conventions:
> 1. `POST /api/v1/bank-users/register` → `POST /api/v1/users` (use HTTP method to imply action)
> 2. `PATCH /api/v1/bank-users/update/{id}` → `PATCH /api/v1/users/{id}`
> 3. `/api/v1/bank-users` → `/api/v1/users`
> 4. `/api/v1/account/util-account/{account_name}` → `/api/v1/utility-accounts/{provider_name}`
> 5. Update all Feign clients and Gateway route configurations to match.
> 6. Update list endpoints to return `Page<T>` instead of `List<T>` so pagination metadata is included.
> 7. Update the Postman collection to match new URLs.
> **Note:** This is a breaking change — coordinate with API consumers.

---

### 3.8 Normalize Package Structure

**Gaps:** 1.3, 1.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all microservices to follow a consistent convention:
> ```
> com.javatodev.finance/
> ├── configuration/
> │   ├── audit/
> │   ├── feign/
> │   ├── filter/
> │   └── security/
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
> Move classes to match this structure. Convert inline mapper instantiation (`new FundTransferMapper()`) to Spring `@Component` beans with constructor injection.

---

## Summary Timeline

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 10 items | 1–2 weeks | Critical security/error handling fixes, basic resilience |
| **Phase 2** | 8 items | 3–6 weeks | Circuit breakers, shared library, RBAC, testing, observability |
| **Phase 3** | 8 items | 6–12 weeks | Saga pattern, CI/CD, contract tests, API standardization |

### Priority Order Within Phases

**Phase 1** (do in this order):
1. Fix exception leak & HTTP status codes (1.1)
2. Add input validation (1.2)
3. Mask password in response (1.3)
4. Remove sensitive data from logs (1.4)
5. Configure Feign timeouts (1.5)
6. Add Feign retry (1.6)
7. Fix ResponseEntity types (1.7)
8. Fix OpenAPI dependency (1.8)
9. Fix Keycloak thread safety (1.9)
10. Fix exception message shadow (1.10)

**Phase 2** (do in this order):
1. Circuit breakers (2.1)
2. Extract shared library (2.2)
3. Feign error handling (2.3)
4. Role-based access control (2.4)
5. Structured logging (2.5)
6. Unit tests (2.6)
7. Health checks & metrics (2.7)
8. Rate limiting (2.8)

**Phase 3** (do in this order):
1. Externalize secrets (3.4) — quick but deferred to avoid breaking local dev setup
2. CI/CD pipeline (3.3)
3. Saga pattern (3.1)
4. Integration tests (3.2)
5. Bulkhead pattern (3.5)
6. Contract tests (3.6)
7. API naming standardization (3.7)
8. Package structure normalization (3.8)
