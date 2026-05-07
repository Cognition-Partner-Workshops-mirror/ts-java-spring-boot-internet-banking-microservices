# Remediation Roadmap

## Overview

This roadmap organizes the 32 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): Critical fixes with small effort. Stop the bleeding.
- **Phase 2 — Important** (3-6 weeks): High-impact improvements requiring moderate effort.
- **Phase 3 — Polish** (6-12 weeks): Strategic improvements for long-term maintainability.

Each item includes a ready-to-use Devin prompt.

---

## Phase 1: Quick Wins

*Focus: Fix critical bugs, plug security holes, and stop data corruption. All items are Small effort.*

### 1.1 Fix Balance Calculation Bug (Double Subtraction)

**Gap**: 7.6 | **Severity**: Critical | **Effort**: Small

The `availableBalance` is being double-subtracted in both `internalFundTransfer()` and `utilPayment()` in `TransactionService.java`. This corrupts account balances on every transaction.

**Devin Prompt**:
> In `core-banking-service`, fix the balance calculation bug in `TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is set by subtracting `amount` from the already-updated `actualBalance`, causing a double subtraction. Fix it so that `availableBalance` is set equal to the new `actualBalance` after each balance update. Update the existing unit tests in `TransactionServiceTest.java` to assert that both `actualBalance` and `availableBalance` are correct after fund transfers and utility payments. Open a PR with the fix.

---

### 1.2 Remove Hardcoded Credentials from Source Code

**Gap**: 4.1 | **Severity**: Critical | **Effort**: Small

MySQL passwords, Keycloak admin passwords, and test credentials are hardcoded in `docker-compose.yml`, `privileges.sql`, and `README.md`.

**Devin Prompt**:
> In the `docker-compose/` directory, replace all hardcoded passwords in `docker-compose.yml`, `docker-compose-support-apps.yml`, and `mysql/privileges.sql` with `${VARIABLE}` references (e.g., `${MYSQL_ROOT_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}`, `${MYSQL_APP_PASSWORD}`). Create a `.env.example` file with placeholder values documenting each required variable. Add `.env` to `.gitignore`. Update `README.md` to reference the `.env.example` file instead of hardcoding credentials. Open a PR.

---

### 1.3 Fix Password Logging and Response Exposure

**Gap**: 4.5 | **Severity**: High | **Effort**: Small

The `User` DTO includes a `password` field that gets logged and returned in API responses.

**Devin Prompt**:
> In `internet-banking-user-service`, fix the `User` DTO in `model/dto/User.java` to prevent password exposure. Add `@ToString.Exclude` on the `password` field to prevent it from appearing in logs. Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` so the password is never serialized in API responses. Verify the `UserController.createUser()` log statement does not log the full request object. Open a PR.

---

### 1.4 Fix Keycloak Singleton Thread Safety

**Gap**: 4.4 | **Severity**: High | **Effort**: Small

The `KeycloakProperties.getInstance()` method has a race condition with its static singleton pattern.

**Devin Prompt**:
> In `internet-banking-user-service`, refactor the Keycloak client initialization. Replace the manual static singleton in `KeycloakProperties.java` with a proper Spring `@Bean` definition in a new `@Configuration` class (e.g., `KeycloakConfig.java`). The `Keycloak` instance should be created as a Spring-managed singleton bean using `KeycloakBuilder`. Inject it into `KeycloakManager` via constructor injection instead of calling `keycloakProperties.getInstance()`. Remove the static `keycloakInstance` field. Open a PR.

---

### 1.5 Add Proper HTTP Status Codes to Exception Handlers

**Gap**: 2.2 | **Severity**: High | **Effort**: Small

All exceptions return HTTP 400 regardless of the actual error type.

**Devin Prompt**:
> Across all 4 business services (core-banking, user-service, fund-transfer, utility-payment), update each `GlobalExceptionHandler` to return appropriate HTTP status codes. Add specific `@ExceptionHandler` methods: `EntityNotFoundException` -> 404, `InsufficientFundsException` -> 422, `UserAlreadyRegisteredException` -> 409, `InvalidEmailException` -> 422, `InvalidBankingUserException` -> 404. Always return the structured `ErrorResponse` format (never a plain string). The generic `Exception` catch-all should return 500 with a generic message (do not expose stack traces). Open a PR.

---

### 1.6 Add Timeout Configuration for Feign Clients

**Gap**: 7.3 | **Severity**: High | **Effort**: Small

No connection or read timeouts are configured anywhere.

**Devin Prompt**:
> Add Feign client timeout configuration to all three services that use Feign clients (user-service, fund-transfer-service, utility-payment-service). In each service's `application.yml`, add `feign.client.config.default.connectTimeout: 5000` and `feign.client.config.default.readTimeout: 10000`. Also add `spring.datasource.hikari.connectionTimeout: 30000` and `spring.datasource.hikari.maximumPoolSize: 10` to each service that uses a database. Open a PR.

---

### 1.7 Add Retry Policies for Feign Clients

**Gap**: 7.2 | **Severity**: High | **Effort**: Small

No retries for transient failures on inter-service calls.

**Devin Prompt**:
> Add Resilience4j retry support to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add the `spring-cloud-starter-circuitbreaker-resilience4j` dependency to both `build.gradle` files. Configure retry in `application.yml` with `resilience4j.retry.instances.coreBankingService.maxAttempts: 3`, `waitDuration: 500ms`, and `retryExceptions: java.io.IOException, feign.RetryableException`. Apply `@Retry(name = "coreBankingService")` to the service methods that call the Feign clients. Open a PR.

---

### 1.8 Fix Raw ResponseEntity Type Parameters

**Gap**: 5.1 | **Severity**: Medium | **Effort**: Small

Controllers use raw `ResponseEntity` without generic type parameters.

**Devin Prompt**:
> Across all controllers in core-banking-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, add proper generic type parameters to all `ResponseEntity` return types. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Match the actual return type in each case. Open a PR.

---

### 1.9 Add Pagination Metadata to List Endpoints

**Gap**: 5.3 | **Severity**: Medium | **Effort**: Small

Paginated endpoints return raw `List<>` without page metadata.

**Devin Prompt**:
> Update all paginated endpoints across all services to return `Page<T>` instead of `List<T>`. In each service method that accepts `Pageable`, return the `Page` object from the repository directly (or map its content while preserving the page wrapper). Update controller return types to `ResponseEntity<Page<...>>`. This affects `UserController.readUsers()` in both user-service and core-banking, `FundTransferController.readFundTransfers()`, and `UtilityPaymentController.readPayments()`. Open a PR.

---

### 1.10 Fix Context-Load Tests

**Gap**: 3.3 | **Severity**: Medium | **Effort**: Small

Default test classes fail without full infrastructure running.

**Devin Prompt**:
> For each of the 5 non-core services (api-gateway, config-server, fund-transfer, user-service, utility-payment), add or update `src/test/resources/application.yml` to disable external dependencies: set `eureka.client.enabled: false`, `spring.cloud.config.enabled: false`, use H2 in-memory datasource where applicable, and disable Flyway. Verify each service's `contextLoads()` test passes independently by running `./gradlew test` in each service directory. Open a PR.

---

## Phase 2: Important

*Focus: Structural improvements, security hardening, and resilience patterns. Moderate effort items.*

### 2.1 Add Input Validation Across All Services

**Gap**: 2.3 | **Severity**: Critical | **Effort**: Medium

No request validation exists anywhere in the codebase.

**Devin Prompt**:
> Add Bean Validation to all request DTOs across all services. Add the `spring-boot-starter-validation` dependency to each service's `build.gradle`. Annotate DTO fields: `FundTransferRequest` (fromAccount: `@NotBlank`, toAccount: `@NotBlank`, amount: `@NotNull @Positive`), `UtilityPaymentRequest` (providerId: `@NotNull`, amount: `@NotNull @Positive`, referenceNumber: `@NotBlank`, account: `@NotBlank`), `User` (email: `@NotBlank @Email`, identification: `@NotBlank`, password: `@NotBlank @Size(min=8)`), `UserUpdateRequest` (status: `@NotNull`). Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns 400 with field-level error details in the `ErrorResponse` format. Open a PR.

---

### 2.2 Add Circuit Breakers

**Gap**: 7.1 | **Severity**: Critical | **Effort**: Medium

No circuit breakers protect against cascading failures.

**Devin Prompt**:
> Add Resilience4j circuit breaker support to all three Feign-client services (user-service, fund-transfer-service, utility-payment-service). Add `spring-cloud-starter-circuitbreaker-resilience4j` to each `build.gradle`. Enable Feign circuit breaker integration with `spring.cloud.openfeign.circuitbreaker.enabled: true` in each `application.yml`. Create fallback factory classes for each Feign client that return appropriate error responses (e.g., `BankingCoreFeignClientFallbackFactory`). Configure circuit breaker settings: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`. Register fallback factories via the `fallbackFactory` attribute on each `@FeignClient` annotation. Open a PR.

---

### 2.3 Fix Global Exception Handler (Stop Stack Trace Leakage)

**Gap**: 2.1 | **Severity**: Critical | **Effort**: Medium

The catch-all exception handler exposes stack traces to clients and returns 400 for server errors.

**Devin Prompt**:
> Refactor the `GlobalExceptionHandler` across all 4 business services to be production-safe. The generic `Exception` handler must: (1) log the full stack trace at ERROR level, (2) return HTTP 500 with a structured `ErrorResponse` containing code `INTERNAL_SERVER_ERROR` and message `An unexpected error occurred. Please try again later.` (never expose exception details to the client). Add handlers for `MethodArgumentNotValidException` (400), `HttpMessageNotReadableException` (400), and `ConstraintViolationException` (400). Ensure all handlers return the consistent `ErrorResponse { code, message }` format. Open a PR.

---

### 2.4 Add Error Handling for Feign Client Failures

**Gap**: 2.4 | **Severity**: High | **Effort**: Medium

Feign failures leave entities in inconsistent states.

**Devin Prompt**:
> In `internet-banking-fund-transfer-service`, wrap the `bankingCoreFeignClient.fundTransfer()` call in `FundTransferService.fundTransfer()` with a try-catch. On any exception, update the `FundTransferEntity` status to `FAILED` and save it before re-throwing. Do the same in `internet-banking-utility-payment-service` for `bankingCoreRestClient.utilityPayment()` in `UtilityPaymentService.utilPayment()`. Also add a `CustomFeignErrorDecoder` to both services (similar to the one in user-service) that translates Feign error responses into appropriate exceptions. Open a PR.

---

### 2.5 Implement Role-Based Authorization

**Gap**: 4.6 | **Severity**: High | **Effort**: Medium

Any authenticated user can access any resource.

**Devin Prompt**:
> Add role-based and resource-based authorization to the application. In the API Gateway's `SecurityConfiguration`, extract roles from the Keycloak JWT and map them to Spring Security authorities. In `internet-banking-user-service`, add `@PreAuthorize("hasRole('ADMIN')")` to the `updateUser` endpoint (only admins can approve users). In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, add logic to verify that the authenticated user (from the `X-Auth-Id` header) owns the source account before allowing a fund transfer or utility payment. Add `spring-boot-starter-security` dependency to each business service and configure method-level security with `@EnableMethodSecurity`. Open a PR.

---

### 2.6 Add Rate Limiting at API Gateway

**Gap**: 4.3 | **Severity**: High | **Effort**: Medium

No rate limiting protects the banking API.

**Devin Prompt**:
> Add rate limiting to the API Gateway. Add `spring-boot-starter-data-redis-reactive` dependency to the gateway's `build.gradle`. Configure Spring Cloud Gateway's `RequestRateLimiter` filter with a Redis-backed rate limiter. Set default limits of 100 requests/second per user (keyed by JWT subject claim). Add a Redis service to `docker-compose.yml`. Configure the rate limiter in the gateway's route configuration. Also add `spring.codec.max-in-memory-size: 1MB` to limit request body size. Open a PR.

---

### 2.7 Create Shared Common Library Module

**Gap**: 1.1 | **Severity**: Medium | **Effort**: Medium

Exception classes, base entities, filters, and mappers are duplicated across services.

**Devin Prompt**:
> Create a new `banking-common` Gradle submodule at the project root. Move the following shared classes into it: `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalErrorCode`, `EntityNotFoundException`, `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, and `TransactionStatus`. Update the package to `com.javatodev.finance.common.*`. Add `banking-common` as a dependency in each service's `build.gradle` using `implementation project(':banking-common')`. Create a root `settings.gradle` that includes all service subprojects. Remove the duplicated classes from each service. Ensure all services compile and tests pass. Open a PR.

---

### 2.8 Add Structured JSON Logging

**Gap**: 6.1 | **Severity**: Medium | **Effort**: Medium

Logging is unstructured and inconsistent.

**Devin Prompt**:
> Add structured JSON logging to all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. Create a shared `logback-spring.xml` in each service's `src/main/resources/` that outputs JSON-formatted logs with fields: `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, `service`. For the `dev` profile, keep console output human-readable; for `docker` profile, use JSON format. Remove all log statements that log raw request objects (replace with specific field logging). Fix the string concatenation in `FundTransferService` (`"Sending fund transfer request {}" + request.toString()`) to use SLF4J placeholder syntax. Open a PR.

---

### 2.9 Fix OpenAPI/Swagger Configuration

**Gap**: 5.4 | **Severity**: Medium | **Effort**: Small

Wrong Springdoc starter is used; no aggregated API docs.

**Devin Prompt**:
> Fix the OpenAPI/Swagger setup across all services. In `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`, replace the `springdoc-openapi-starter-webflux-ui` dependency with `springdoc-openapi-starter-webmvc-ui:2.1.0` (these are WebMVC services, not WebFlux). Verify Swagger UI is accessible at `/swagger-ui.html` for each service. Add an `OpenApiConfig` class to each service with `@OpenAPIDefinition` containing the service name, version, and description. Open a PR.

---

## Phase 3: Polish

*Focus: Long-term quality, testing infrastructure, and operational excellence.*

### 3.1 Add Comprehensive Unit Tests

**Gap**: 3.1 | **Severity**: Critical | **Effort**: Large

Only core-banking has unit tests; other services have none.

**Devin Prompt**:
> Add comprehensive unit tests for all business services. For `internet-banking-user-service`: test `UserService.createUser()` (happy path, duplicate email, invalid email, user not in core banking, Keycloak failure), `UserService.updateUser()` (approve, not found), `UserService.readUser()`, `KeycloakUserService` (all methods). For `internet-banking-fund-transfer-service`: test `FundTransferService.fundTransfer()` (happy path, Feign failure, state transitions) and `readAllTransfers()`. For `internet-banking-utility-payment-service`: test `UtilityPaymentService.utilPayment()` (happy path, Feign failure, state transitions) and `readPayments()`. Use Mockito to mock repositories and Feign clients. Each service should have at least 80% line coverage on service classes. Open a PR.

---

### 3.2 Add Integration Tests with Testcontainers

**Gap**: 3.1 (extended) | **Severity**: High | **Effort**: Large

No integration tests verify database and service interactions.

**Devin Prompt**:
> Add integration tests using Testcontainers for `core-banking-service`. Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to `build.gradle` testImplementation. Create integration tests that spin up a real MySQL container, run Flyway migrations, and verify: (1) account lookup by number, (2) fund transfer with balance updates, (3) utility payment with balance deduction, (4) insufficient funds rejection. Use `@SpringBootTest` with `@Testcontainers`. Create a `TestcontainersConfig` class with `@DynamicPropertySource` to configure the datasource. Open a PR.

---

### 3.3 Add Contract Tests Between Services

**Gap**: 3.2 | **Severity**: Medium | **Effort**: Large

No contract tests verify Feign client compatibility.

**Devin Prompt**:
> Add Spring Cloud Contract tests to verify the API contracts between services. On the provider side (core-banking-service), add `spring-cloud-starter-contract-verifier` to `build.gradle`. Create contract definitions in `src/test/resources/contracts/` for each endpoint consumed by downstream Feign clients: `GET /api/v1/account/bank-account/{number}`, `GET /api/v1/user/{identification}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`. On the consumer side (fund-transfer, utility-payment, user-service), add `spring-cloud-starter-contract-stub-runner` and write `@AutoConfigureStubRunner` tests that verify each Feign client works against the generated stubs. Open a PR.

---

### 3.4 Implement Saga Pattern for Fund Transfers

**Gap**: 7.5 | **Severity**: Critical | **Effort**: Large

Fund transfers are non-atomic across service boundaries.

**Devin Prompt**:
> Implement a basic saga pattern for fund transfers in `internet-banking-fund-transfer-service`. Add an idempotency key field to `FundTransferRequest` and `FundTransferEntity`. Before processing, check if a transfer with the same idempotency key already exists and return the existing result. Wrap the Feign call in a try-catch: on success, update to `SUCCESS`; on failure, update to `FAILED`. Add a compensation endpoint to `core-banking-service` (`POST /api/v1/transaction/fund-transfer/reverse`) that reverses a transfer by transactionId. Add a scheduled job (`@Scheduled`) that runs every 5 minutes to find transfers stuck in `PENDING` for more than 10 minutes and either retries or marks them as `FAILED`. Apply the same pattern to `internet-banking-utility-payment-service`. Open a PR.

---

### 3.5 Add Root-Level Multi-Project Gradle Build

**Gap**: 1.2 | **Severity**: Low | **Effort**: Small

No unified build system.

**Devin Prompt**:
> Create a root-level `settings.gradle` that includes all 7 subprojects (6 services + banking-common if it exists). Create a root `build.gradle` that uses `subprojects {}` to define shared configurations: Java 21 source compatibility, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common test dependencies, and shared repository declarations. Move duplicated version numbers from individual `build.gradle` files into the root config or a Gradle version catalog (`gradle/libs.versions.toml`). Verify `./gradlew build` from the root directory builds all services. Open a PR.

---

### 3.6 Standardize Package Structure

**Gap**: 1.3 | **Severity**: Low | **Effort**: Small

Package naming is inconsistent across services.

**Devin Prompt**:
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.exception`, `com.javatodev.finance.configuration`, `com.javatodev.finance.client` (for Feign clients). Move classes to their canonical locations. Update all imports. Ensure all services compile and tests pass. Open a PR.

---

### 3.7 Add Custom Health Indicators

**Gap**: 6.2 | **Severity**: Low | **Effort**: Small

No custom health checks for downstream dependencies.

**Devin Prompt**:
> Add custom health indicators to all business services. In `core-banking-service`, add a `DatabaseHealthIndicator` that checks MySQL connectivity. In `internet-banking-user-service`, add a `KeycloakHealthIndicator` that pings the Keycloak server URL. In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, add a `CoreBankingHealthIndicator` that checks if core-banking-service is reachable via Eureka. Configure `management.endpoint.health.show-details: when-authorized` and set up liveness/readiness health groups in each service's `application.yml`. Open a PR.

---

### 3.8 Add Custom Business Metrics

**Gap**: 6.3 | **Severity**: Low | **Effort**: Small

No business-level metrics are tracked.

**Devin Prompt**:
> Add Micrometer business metrics to the key services. In `core-banking-service`, add counters for `banking.fund_transfer.total` (tagged by status: success/failure) and `banking.utility_payment.total`. Add a timer for `banking.fund_transfer.duration`. In `internet-banking-user-service`, add counters for `banking.user_registration.total` and `banking.user_approval.total`. Inject `MeterRegistry` into each service class and record metrics at the appropriate points. Verify metrics appear at `/actuator/metrics`. Open a PR.

---

### 3.9 Convert Mappers to Spring Beans

**Gap**: 1.4 | **Severity**: Low | **Effort**: Small

Mappers are manually instantiated.

**Devin Prompt**:
> Refactor all mapper classes across all services to be Spring-managed beans. Add `@Component` to `BankAccountMapper`, `UserMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`. Remove the `new XxxMapper()` instantiation in service classes and use constructor injection instead. Verify all services compile and tests pass (update tests to inject or mock mappers). Open a PR.

---

### 3.10 Document API Versioning Strategy and CSRF Rationale

**Gaps**: 5.2, 4.2 | **Severity**: Low | **Effort**: Small

No versioning strategy documented; CSRF disabled without explanation.

**Devin Prompt**:
> Add an `API_CONVENTIONS.md` document to the `docs/` folder covering: (1) The API versioning strategy — currently using URL path versioning (`/api/v1/`), with guidelines for introducing v2 endpoints alongside v1 using Spring MVC path matching. (2) The rationale for CSRF being disabled — this is a stateless REST API using JWT Bearer tokens (no cookie-based sessions), so CSRF protection is not needed. Also add a brief code comment in `SecurityConfiguration.java` at the `csrf.disable()` line explaining this rationale. Open a PR.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| **Phase 1: Quick Wins** | 10 | 2 | 5 | 3 | 0 |
| **Phase 2: Important** | 9 | 3 | 3 | 3 | 0 |
| **Phase 3: Polish** | 10 | 2 | 1 | 1 | 6 |
| **Total** | 29 | 7 | 9 | 7 | 6 |

> **Note**: Some related gaps have been combined into single remediation items (e.g., 7.2 + 7.3 are addressed together; 5.2 + 4.2 are addressed together). The 3 remaining low-priority items (5.5 inconsistent naming, 5.2 versioning strategy, 6.4 tracing verification) are folded into the items above.

---

## Execution Order Recommendation

```
Phase 1 (Week 1-2)
├── 1.1 Fix balance calculation bug          ← DO FIRST (data corruption)
├── 1.2 Remove hardcoded credentials
├── 1.3 Fix password logging exposure
├── 1.4 Fix Keycloak singleton thread safety
├── 1.5 Add proper HTTP status codes
├── 1.6 Add timeout configuration
├── 1.7 Add retry policies
├── 1.8 Fix raw ResponseEntity types
├── 1.9 Add pagination metadata
└── 1.10 Fix context-load tests

Phase 2 (Week 3-6)
├── 2.1 Add input validation               ← DO FIRST (security)
├── 2.2 Add circuit breakers
├── 2.3 Fix exception handler (stop leaks)
├── 2.4 Add Feign error handling
├── 2.5 Implement authorization
├── 2.6 Add rate limiting
├── 2.7 Create shared common library
├── 2.8 Add structured JSON logging
└── 2.9 Fix OpenAPI/Swagger config

Phase 3 (Week 6-12)
├── 3.1 Add comprehensive unit tests        ← DO FIRST (test safety net)
├── 3.2 Add integration tests
├── 3.3 Add contract tests
├── 3.4 Implement saga pattern
├── 3.5 Add root-level Gradle build
├── 3.6 Standardize package structure
├── 3.7 Add custom health indicators
├── 3.8 Add custom business metrics
├── 3.9 Convert mappers to Spring beans
└── 3.10 Document conventions
```
