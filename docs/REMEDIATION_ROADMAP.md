# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be fixed with small, focused changes. No architectural redesign required.

### 1.1 Fix Balance Calculation Bug (Critical | Small)

**Gap Reference**: 7.7

The `TransactionService.internalFundTransfer()` and `utilPayment()` methods double-subtract/add amounts when updating `availableBalance`. This is a data-corrupting production bug.

**Devin Prompt**:
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `setAvailableBalance()` calls subtract the amount from `actualBalance` after `actualBalance` was already reduced, resulting in double-deduction. Change these lines so that `availableBalance` is set to the same value as the updated `actualBalance` (or to `actualBalance` directly). Add unit tests to verify correct balance calculations after transfers and payments. Open a PR with the fix.

---

### 1.2 Stop Leaking Internal Details in Error Responses (Critical | Small)

**Gap Reference**: 2.2

The catch-all `@ExceptionHandler(Exception.class)` in all `GlobalExceptionHandler` classes concatenates the full exception into the response body.

**Devin Prompt**:
> In all `GlobalExceptionHandler` classes across core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service: change the catch-all `@ExceptionHandler({Exception.class})` to return a generic error message like `"An unexpected error occurred. Please try again later."` with HTTP 500 status. Log the full exception at ERROR level instead. Do not expose exception details in the response body. Open a PR with the changes.

---

### 1.3 Use Correct HTTP Status Codes (High | Small)

**Gap Reference**: 2.3

All exceptions currently return HTTP 400. Different exception types should map to appropriate status codes.

**Devin Prompt**:
> Update all `GlobalExceptionHandler` classes across all services to return appropriate HTTP status codes: `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `SimpleBankingGlobalException` should return 400, and the catch-all `Exception` handler should return 500. Make sure to update both the core-banking-service (which has typed exceptions) and the other services. Open a PR with the changes.

---

### 1.4 Add Bean Validation to Request DTOs (Critical | Medium)

**Gap Reference**: 4.2

No request DTO has any validation annotations. Invalid input can cause NullPointerExceptions or corrupt data.

**Devin Prompt**:
> Add Jakarta Bean Validation annotations to all request DTOs across the banking microservices. For `FundTransferRequest`: add `@NotBlank` on fromAccount and toAccount, `@NotNull @DecimalMin("0.01")` on amount. For `UtilityPaymentRequest`: add `@NotNull` on providerId, `@NotNull @DecimalMin("0.01")` on amount, `@NotBlank` on referenceNumber and account. For User registration: add `@Email @NotBlank` on email, `@NotBlank` on password and identification. Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add the `spring-boot-starter-validation` dependency to each service's build.gradle if not already present. Open a PR with the changes.

---

### 1.5 Add Feign Timeout Configuration (High | Small)

**Gap Reference**: 7.3

No Feign timeouts are configured, meaning calls can hang indefinitely.

**Devin Prompt**:
> Add Feign client timeout configuration to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Set connect timeout to 5 seconds and read timeout to 10 seconds. This can be done via application.yml properties: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Since configs are managed by Spring Cloud Config Server, add these to the appropriate service configuration files or document where they should be added. Open a PR.

---

### 1.6 Add Feign Retry Configuration (High | Small)

**Gap Reference**: 7.2

No retry policy exists for transient failures on inter-service calls.

**Devin Prompt**:
> Add a Feign Retryer bean to the fund-transfer-service and utility-payment-service CustomFeignClientConfiguration classes. Configure it with a period of 1 second, max period of 3 seconds, and max attempts of 3. Only retries should happen on 5xx errors or connection failures, not on 4xx client errors. Add the retry configuration as a Spring bean. Open a PR.

---

### 1.7 Fix Feign Error Decoders in Fund Transfer and Utility Payment Services (High | Medium)

**Gap Reference**: 2.5

These services lack error decoders, so core-banking-service domain errors are lost.

**Devin Prompt**:
> The internet-banking-user-service has a `CustomFeignErrorDecoder` that properly extracts domain exceptions from core-banking-service error responses. Port this same error decoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Each service should have a `CustomFeignErrorDecoder` that reads the error response body, deserializes it into `SimpleBankingGlobalException`, and rethrows it. Register it as a bean in each service's `CustomFeignClientConfiguration`. Open a PR.

---

### 1.8 Fix Logging Bug and Standardize Log Messages (Medium | Small)

**Gap Reference**: 6.1

`FundTransferService` has a string concatenation bug in its log statement, and logging is inconsistent across services.

**Devin Prompt**:
> Fix the logging bug in `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java` where `log.info("Sending fund transfer request {}" + request.toString())` uses string concatenation instead of SLF4J placeholder. Change it to `log.info("Sending fund transfer request {}", request)`. Also add `@Slf4j` to `UtilityPaymentController` which is missing it. Ensure all controllers log incoming requests consistently. Open a PR.

---

### 1.9 Add Type Parameters to ResponseEntity (Medium | Small)

**Gap Reference**: 5.1

Raw `ResponseEntity` without generics breaks compile-time safety and Swagger documentation.

**Devin Prompt**:
> Add proper generic type parameters to all `ResponseEntity` return types across all controllers in all services. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Update each controller method in core-banking-service (AccountController, TransactionController, UserController), internet-banking-fund-transfer-service (FundTransferController), internet-banking-user-service (UserController), and internet-banking-utility-payment-service (UtilityPaymentController). Open a PR.

---

### 1.10 Return Page Metadata from Paginated Endpoints (Medium | Small)

**Gap Reference**: 5.3

List endpoints return `List<T>` instead of `Page<T>`, losing pagination metadata.

**Devin Prompt**:
> Update all paginated list endpoints to return `Page<T>` instead of `List<T>`. Modify the service methods in FundTransferService.readAllTransfers(), UtilityPaymentService.readPayments(), UserService.readUsers() (in both core-banking and user-service) to return `Page<T>` objects that include totalElements, totalPages, and pageSize. Update the corresponding controller methods to wrap the response properly. Open a PR.

---

### 1.11 Fix Keycloak Singleton Thread-Safety (Medium | Small)

**Gap Reference**: 4.4

The static `Keycloak` instance in `KeycloakProperties` is not thread-safe.

**Devin Prompt**:
> Refactor `KeycloakProperties` in internet-banking-user-service to use a proper Spring `@Bean` for the `Keycloak` instance instead of a static mutable singleton. Create the Keycloak client bean in a `@Configuration` class and inject it into `KeycloakManager`. Remove the static field and `getInstance()` method. Open a PR.

---

### 1.12 Fix Context Load Tests (Medium | Small)

**Gap Reference**: 3.4

Several `@SpringBootTest` contextLoads tests fail in isolation because they require external infrastructure.

**Devin Prompt**:
> Fix the contextLoads tests in internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-api-gateway so they can run without external dependencies. For services that require config server, add `spring.cloud.config.enabled=false` to test application.yml. For services requiring Eureka, ensure `eureka.client.enabled=false` is set. For the api-gateway, mock the OAuth2 resource server configuration in test config. Ensure `./gradlew test` passes for each service in isolation. Open a PR.

---

## Phase 2: Important Improvements (3-6 weeks)

Structural improvements that require more design work but significantly improve reliability and maintainability.

### 2.1 Introduce a Shared Common Library (High | Medium)

**Gap Reference**: 1.2

Eliminate duplicated code by creating a shared module.

**Devin Prompt**:
> Create a shared Gradle module called `internet-banking-common` at the project root. Move the following shared classes into it: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `SimpleBankingGlobalException`, `ErrorResponse`, and `GlobalExceptionHandler`. Update all services to depend on `internet-banking-common`. Set up a root `settings.gradle` that includes all service modules and the common module as a multi-project Gradle build. Ensure all services compile and their tests pass. Open a PR.

---

### 2.2 Add Circuit Breakers with Resilience4j (Critical | Medium)

**Gap Reference**: 7.1

Protect services from cascading failures when core-banking-service is unavailable.

**Devin Prompt**:
> Add Resilience4j circuit breaker support to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's build.gradle. Configure circuit breakers for each Feign client with: failure rate threshold of 50%, wait duration in open state of 30 seconds, sliding window size of 10. Add fallback methods that return meaningful error responses. Configure the circuit breaker state to be exposed via the actuator health endpoint. Open a PR.

---

### 2.3 Add Idempotency Keys for Financial Transactions (High | Medium)

**Gap Reference**: 7.5

Prevent duplicate transactions from client retries.

**Devin Prompt**:
> Add idempotency key support to the fund transfer and utility payment flows. Add an optional `idempotencyKey` field (UUID) to `FundTransferRequest` and `UtilityPaymentRequest`. In each orchestrating service, before processing: check if a record with the same idempotency key already exists in the database and return the existing result if found. Add a unique index on idempotency_key in both `fund_transfer` and `utility_payment` tables. If no idempotency key is provided, generate one server-side. Open a PR.

---

### 2.4 Add Unit Tests for All Services (Critical | Large)

**Gap Reference**: 3.1

Only core-banking-service has meaningful tests.

**Devin Prompt**:
> Add comprehensive unit tests for all service classes across the banking microservices: (1) internet-banking-user-service: test UserService (createUser success/failures, readUsers, readUser, updateUser), test KeycloakUserService with mocked Keycloak admin client; (2) internet-banking-fund-transfer-service: test FundTransferService (fundTransfer success, fundTransfer when core-banking fails, readAllTransfers); (3) internet-banking-utility-payment-service: test UtilityPaymentService (utilPayment success, utilPayment when core-banking fails, readPayments). Use Mockito to mock repositories and Feign clients. Aim for >80% line coverage on service classes. Open a PR.

---

### 2.5 Add Controller Tests with MockMvc (High | Large)

**Gap Reference**: 3.2

No controller-level tests exist.

**Devin Prompt**:
> Add `@WebMvcTest` controller tests for all controllers across the banking microservices. For each controller, use `MockMvc` to test: (1) successful requests return correct status codes and response shapes, (2) validation errors return 400 with error details, (3) not-found scenarios return 404. Mock the service layer using `@MockBean`. Cover: core-banking-service (AccountController, TransactionController, UserController), fund-transfer-service (FundTransferController), user-service (UserController), utility-payment-service (UtilityPaymentController). Open a PR.

---

### 2.6 Secure Downstream Services (High | Medium)

**Gap Reference**: 4.3

Downstream services have no authentication/authorization.

**Devin Prompt**:
> Add security configuration to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Each service should validate the `X-Auth-Id` header for all endpoints. Create a security filter that rejects requests without a valid `X-Auth-Id` header (return 401). Optionally, add service-to-service authentication using a shared secret or JWT propagation through Feign interceptors. Ensure actuator health endpoints remain accessible without authentication. Open a PR.

---

### 2.7 Standardize Error Response Format (RFC 7807) (Medium | Small)

**Gap Reference**: 2.4

Error responses are inconsistent across services.

**Devin Prompt**:
> Adopt RFC 7807 Problem Details for HTTP APIs as the standard error response format across all services. Use Spring Boot 3's built-in `ProblemDetail` support. Update all `GlobalExceptionHandler` classes to return `ProblemDetail` objects with consistent fields: type (error URI), title, status, detail, and instance (request path). Replace the custom `ErrorResponse` class with `ProblemDetail`. Ensure Feign error decoders can parse this format. Open a PR.

---

### 2.8 Add Prometheus Metrics Endpoint (Medium | Small)

**Gap Reference**: 6.3

Prometheus is mentioned in the README but not configured.

**Devin Prompt**:
> Enable Prometheus metrics in all services by adding `io.micrometer:micrometer-registry-prometheus` to each service's build.gradle. Expose the `/actuator/prometheus` endpoint by adding `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to application configuration. Add custom business metrics: a counter for fund transfers processed, a counter for utility payments processed, a gauge for active Feign connections, and a timer for transaction processing duration. Open a PR.

---

### 2.9 Add Dependency Vulnerability Scanning (Medium | Small)

**Gap Reference**: 4.5

No automated scanning for known CVEs in dependencies.

**Devin Prompt**:
> Add the OWASP Dependency Check Gradle plugin to all services. Configure it in each service's build.gradle: `id 'org.owasp.dependencycheck' version '9.0.9'`. Add a `dependencyCheckAnalyze` task configuration that fails the build on CVSS score >= 7. Run the scan and document any existing vulnerabilities found. If using a multi-project build, add it to the root build.gradle to scan all services at once. Open a PR.

---

### 2.10 Add Custom Health Indicators (Low | Small)

**Gap Reference**: 6.2

No custom health checks for critical dependencies.

**Devin Prompt**:
> Add custom Spring Boot health indicators to the services: (1) core-banking-service: add a MySQL connectivity health indicator; (2) internet-banking-user-service: add a Keycloak connectivity health indicator that checks the Keycloak server is reachable; (3) internet-banking-api-gateway: add health indicators for Eureka registration and downstream service availability. Configure readiness and liveness probes separately. Open a PR.

---

## Phase 3: Polish & Advanced (6-12 weeks)

Strategic improvements for long-term maintainability and production readiness.

### 3.1 Implement Saga Pattern for Distributed Transactions (Critical | Large)

**Gap Reference**: 7.6

Fund transfers and payments span multiple databases without consistency guarantees.

**Devin Prompt**:
> Refactor the fund transfer and utility payment flows to use the Saga pattern with compensating transactions. For fund transfers: (1) Fund Transfer Service creates a local record with status PENDING, (2) calls Core Banking Service to execute the transfer, (3) on success: updates local status to SUCCESS, (4) on failure: updates local status to FAILED and logs the failure for investigation. Add a compensation endpoint in core-banking-service that can reverse a transfer by transactionId. Consider using the Outbox pattern with a database polling mechanism to ensure the core-banking call eventually completes. Open a PR.

---

### 3.2 Add Contract Tests Between Services (Medium | Large)

**Gap Reference**: 3.3

No contract verification between Feign clients and their target services.

**Devin Prompt**:
> Add Spring Cloud Contract tests between the banking microservices. On the provider side (core-banking-service), define contracts for all endpoints consumed by other services: GET /api/v1/account/bank-account/{number}, GET /api/v1/user/{identification}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment. Generate stubs from these contracts. On the consumer side (fund-transfer, utility-payment, user services), write contract verification tests that use the generated stubs to validate Feign clients work correctly. Open a PR.

---

### 3.3 Create Multi-Project Gradle Build (Medium | Medium)

**Gap Reference**: 1.1

Unify all services under a single Gradle build.

**Devin Prompt**:
> Convert the project to a Gradle multi-project build. Create a root `settings.gradle` that includes all 7 service modules (core-banking-service, internet-banking-api-gateway, internet-banking-config-server, internet-banking-fund-transfer-service, internet-banking-service-registry, internet-banking-user-service, internet-banking-utility-payment-service) and the shared common module. Create a root `build.gradle` that defines common configuration (Java version, Spring Boot version, Spring Cloud version, common dependencies, test configuration) using `subprojects {}`. Remove duplicated configuration from individual service build files. Ensure `./gradlew build` from the root builds all services. Open a PR.

---

### 3.4 Add Integration Tests with Testcontainers (High | Large)

**Gap Reference**: 3.2

No integration tests that exercise actual database and infrastructure interactions.

**Devin Prompt**:
> Add Testcontainers-based integration tests to core-banking-service and internet-banking-user-service. For core-banking-service: add `org.testcontainers:mysql` dependency, create an integration test that starts a MySQL container, runs Flyway migrations, and exercises the full fund-transfer flow through the controller -> service -> repository stack. For internet-banking-user-service: add a test with a Keycloak testcontainer that verifies user registration and approval flows. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`. Open a PR.

---

### 3.5 Implement Event-Driven Architecture with RabbitMQ (Medium | Large)

**Gap Reference**: README mentions RabbitMQ for notifications but it's not implemented.

**Devin Prompt**:
> Implement the notification service and event-driven communication with RabbitMQ. (1) Add RabbitMQ to docker-compose.yml. (2) In fund-transfer-service and utility-payment-service, add `spring-boot-starter-amqp` and publish events (TRANSFER_COMPLETED, PAYMENT_COMPLETED) to a RabbitMQ exchange after successful transactions. (3) Create a new internet-banking-notification-service that consumes these events and logs them (placeholder for email/SMS). (4) This decouples the notification concern from transaction processing. Open a PR.

---

### 3.6 Standardize Package Structure Across Services (Low | Small)

**Gap Reference**: 1.3

Package naming is inconsistent between services.

**Devin Prompt**:
> Standardize the package structure across all banking microservices to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.dto.request`, `com.javatodev.finance.model.dto.response`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.configuration`, `com.javatodev.finance.exception`. Move classes as needed to match this structure. Update all imports. Verify all services compile and tests pass. Open a PR.

---

### 3.7 Add API Filtering and Sorting (Low | Medium)

**Gap Reference**: 5.4

List endpoints only support basic pagination.

**Devin Prompt**:
> Add filtering and sorting support to list endpoints. For GET /api/v1/transfer: add query parameters for status, fromAccount, toAccount, dateFrom, dateTo. For GET /api/v1/utility-payment: add query parameters for status, account, providerId, dateFrom, dateTo. For GET /api/v1/user and GET /api/v1/bank-user: add query parameters for status, identification. Use Spring Data JPA Specifications or QueryDSL for dynamic query building. Ensure Swagger documents the query parameters. Open a PR.

---

### 3.8 Aggregate Swagger Documentation at Gateway (Medium | Small)

**Gap Reference**: 5.6

No centralized API documentation across all services.

**Devin Prompt**:
> Set up centralized OpenAPI documentation at the API Gateway level. Fix the incorrect `springdoc-openapi-starter-webflux-ui` dependency in non-reactive services (core-banking, fund-transfer, user, utility-payment) — replace with `springdoc-openapi-starter-webmvc-ui`. At the gateway, configure SpringDoc to aggregate OpenAPI specs from all downstream services using the `springdoc.swagger-ui.urls` configuration to list each service's `/v3/api-docs` endpoint. Open a PR.

---

## Summary Timeline

| Phase | Duration | Items | Key Outcomes |
|---|---|---|---|
| **Phase 1: Quick Wins** | 1-2 weeks | 12 items | Fix critical bugs (balance calc, info leak), add validation, timeouts, retries, improve error handling |
| **Phase 2: Important** | 3-6 weeks | 10 items | Shared library, circuit breakers, idempotency, comprehensive tests, downstream security, metrics |
| **Phase 3: Polish** | 6-12 weeks | 8 items | Saga pattern, contract tests, multi-project build, Testcontainers, RabbitMQ, Swagger aggregation |

### Priority Order Within Phases

**Phase 1 (do first to last)**:
1. Fix balance calculation bug (1.1) — data corruption in production
2. Stop leaking internal details (1.2) — security exposure
3. Add input validation (1.4) — security exposure
4. Correct HTTP status codes (1.3)
5. Add Feign timeouts (1.5) and retries (1.6)
6. Fix Feign error decoders (1.7)
7. Fix logging (1.8), ResponseEntity types (1.9), pagination (1.10)
8. Fix Keycloak singleton (1.11) and context load tests (1.12)

**Phase 2 (do first to last)**:
1. Add circuit breakers (2.2) — prevent cascading failures
2. Add unit tests (2.4) and controller tests (2.5) — catch regressions
3. Add idempotency keys (2.3) — prevent duplicate transactions
4. Secure downstream services (2.6)
5. Shared common library (2.1) — reduce maintenance burden
6. Standardize errors (2.7), metrics (2.8), vuln scanning (2.9), health checks (2.10)

**Phase 3 (do first to last)**:
1. Saga pattern (3.1) — data consistency
2. Integration tests (3.4) — confidence in infrastructure
3. Multi-project build (3.3) — developer experience
4. Contract tests (3.2), RabbitMQ (3.5), package structure (3.6), filtering (3.7), Swagger (3.8)
