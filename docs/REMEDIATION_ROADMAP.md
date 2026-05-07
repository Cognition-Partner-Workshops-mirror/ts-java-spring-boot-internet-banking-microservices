# Remediation Roadmap

This roadmap prioritizes the gaps identified in [`GAP_ANALYSIS.md`](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to kick off the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact fixes that are small in effort. These address critical security, data integrity, and correctness issues.

### 1.1 Fix Balance Update Race Condition (Resilience 7.8)

**Severity**: Critical | **Effort**: Small

Add pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to `BankAccountRepository.findByNumber()` to prevent concurrent balance corruption during fund transfers.

> **Devin Prompt**: *"In core-banking-service, add pessimistic write locking to BankAccountRepository.findByNumber() and verify that TransactionService.internalFundTransfer() and utilPayment() use the locked query. Add a concurrent transfer test using CountDownLatch to prove the race condition is fixed. Open a PR."*

### 1.2 Fix Double-Deduction Bug (Business Logic)

**Severity**: Critical | **Effort**: Small

Fix `TransactionService.internalFundTransfer()` and `utilPayment()` where `availableBalance` is incorrectly calculated by subtracting `amount` from the already-reduced `actualBalance`.

> **Devin Prompt**: *"In core-banking-service TransactionService, fix the availableBalance calculation in both internalFundTransfer() and utilPayment(). Currently availableBalance is set to actualBalance.subtract(amount) AFTER actualBalance has already been reduced, causing a double deduction. The correct behavior is availableBalance = actualBalance (they should be equal after the transaction). Update existing tests to verify the correct balances. Open a PR."*

### 1.3 Add Input Validation to All Request DTOs (Security 4.1)

**Severity**: Critical | **Effort**: Small-Medium

Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`) to all request DTOs and `@Valid` to controller method parameters.

> **Devin Prompt**: *"Add Jakarta Bean Validation annotations to all request DTOs across all services: FundTransferRequest (both core and fund-transfer-service), UtilityPaymentRequest (both core and utility-payment-service), User DTO in user-service, and UserUpdateRequest. Add @Valid to all @RequestBody parameters in controllers. Amounts must be @Positive, account numbers must be @NotBlank, and required fields must be @NotNull. Add the spring-boot-starter-validation dependency where missing. Write unit tests for validation. Open a PR."*

### 1.4 Fix Error Response Consistency (Error Handling 2.1, 2.2)

**Severity**: Critical/High | **Effort**: Small

Standardize all `GlobalExceptionHandler` implementations to return proper HTTP status codes and consistent JSON error responses.

> **Devin Prompt**: *"Refactor GlobalExceptionHandler in all four business services (core-banking, user, fund-transfer, utility-payment) to: (1) Return 404 for EntityNotFoundException, 400 for validation errors, 409 for UserAlreadyRegisteredException, and 500 for unexpected exceptions. (2) Always return a structured ErrorResponse JSON body with code and message fields - never return a raw string. (3) Add @ResponseStatus annotations where appropriate. (4) Log the full exception at ERROR level for 500s. Write tests verifying each handler. Open a PR."*

### 1.5 Add Typed ResponseEntity to Controllers (Error Handling 2.3)

**Severity**: Medium | **Effort**: Small

Add generic type parameters to all `ResponseEntity` return types in controllers.

> **Devin Prompt**: *"Update all controller methods across core-banking-service, fund-transfer-service, and utility-payment-service to use typed ResponseEntity<T> instead of raw ResponseEntity. For example, ResponseEntity<BankAccount> instead of ResponseEntity. This improves compile-time safety and OpenAPI documentation. Open a PR."*

### 1.6 Prevent Password Leakage in User Response (Security 4.6)

**Severity**: High | **Effort**: Small

Add `@JsonIgnore` to the `password` field in the User DTO, or split the DTO into separate request/response models.

> **Devin Prompt**: *"In internet-banking-user-service, the User DTO contains a password field that could be serialized back to clients. Either add @JsonProperty(access = Access.WRITE_ONLY) to the password field, or split the User class into a UserRequest (with password) and UserResponse (without password). Ensure the register endpoint accepts the password but never returns it. Open a PR."*

### 1.7 Redact Sensitive Data from Logs (Observability 6.5)

**Severity**: High | **Effort**: Small

Remove or mask sensitive data (account numbers, passwords, amounts) from log statements.

> **Devin Prompt**: *"Audit all log.info() and log.error() statements across all services. Remove toString() calls on request objects that contain sensitive data (account numbers, passwords, amounts). Replace with targeted logging of non-sensitive fields like request type and transaction status. Specifically fix: FundTransferController, UtilityPaymentController, UserController (user-service), and FundTransferService. Open a PR."*

### 1.8 Add Prometheus Metrics Registry (Observability 6.3)

**Severity**: High | **Effort**: Small

Add `micrometer-registry-prometheus` dependency and configure the Prometheus actuator endpoint.

> **Devin Prompt**: *"Add the micrometer-registry-prometheus dependency to all services' build.gradle files. Ensure the /actuator/prometheus endpoint is exposed in the actuator configuration. Verify the endpoint returns Prometheus-format metrics. Open a PR."*

### 1.9 Add Feign Timeout Configuration (Resilience 7.3)

**Severity**: High | **Effort**: Small

Configure explicit connect and read timeouts for all Feign clients.

> **Devin Prompt**: *"Add explicit Feign client timeout configuration to fund-transfer-service, utility-payment-service, and user-service. Set connectTimeout to 5000ms and readTimeout to 10000ms. Configure via application.yml properties (spring.cloud.openfeign.client.config.default.connectTimeout and readTimeout). Open a PR."*

### 1.10 Add Feign Retry Policy (Resilience 7.2)

**Severity**: High | **Effort**: Small

Add a Feign Retryer bean for transient failure recovery.

> **Devin Prompt**: *"Add a Feign Retryer configuration to fund-transfer-service, utility-payment-service, and user-service. Configure it to retry up to 3 times with 100ms initial backoff and 1s max backoff. Add this as a @Bean in each service's Feign configuration class. Only retry on 5xx and connection errors, not on 4xx. Open a PR."*

### 1.11 Move Secrets to Environment Variables (Security 4.2)

**Severity**: High | **Effort**: Small

Replace hardcoded passwords in docker-compose with environment variable references.

> **Devin Prompt**: *"In docker-compose/docker-compose.yml and docker-compose-support-apps.yml, replace all hardcoded passwords (MYSQL_ROOT_PASSWORD, KC_DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD) with environment variable references using ${VAR_NAME} syntax. Create a .env.example file documenting the required variables. Add .env to .gitignore. Open a PR."*

---

## Phase 2: Important Improvements (3-6 weeks)

Structural improvements that require more effort but significantly raise engineering quality.

### 2.1 Extract Shared Library Module (Code Organization 1.1)

**Severity**: High | **Effort**: Medium

Create a `common-library` Gradle module containing shared code and convert the project to a multi-module Gradle build.

> **Devin Prompt**: *"Convert this project from independent Gradle projects to a Gradle multi-module build. Create a settings.gradle at the root that includes all services. Create a new module called 'common-library' and move the following duplicated classes into it: AuditAware, BaseMapper, ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter, TransactionStatus. Update all services to depend on common-library. Ensure all services still compile and tests pass. Open a PR."*

### 2.2 Add Circuit Breakers (Resilience 7.1)

**Severity**: Critical | **Effort**: Medium

Add Resilience4j circuit breakers to all Feign client calls.

> **Devin Prompt**: *"Add Resilience4j circuit breaker support to fund-transfer-service, utility-payment-service, and user-service. Add the spring-cloud-starter-circuitbreaker-resilience4j dependency. Configure circuit breakers for each Feign client with: failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10. Add fallback methods that return meaningful error responses. Write tests that verify circuit breaker behavior. Open a PR."*

### 2.3 Add Idempotency for Financial Operations (Resilience 7.6)

**Severity**: Critical | **Effort**: Medium

Implement idempotency keys for fund transfers and utility payments.

> **Devin Prompt**: *"Implement idempotency for fund transfers and utility payments. Add an 'idempotencyKey' field to FundTransferRequest and UtilityPaymentRequest. Before processing, check if a transaction with the same idempotency key already exists - if so, return the existing result. Add a unique constraint on the idempotency key column. The idempotency key should be a required UUID provided by the client. Write tests for duplicate request handling. Open a PR."*

### 2.4 Implement Role-Based Authorization (Security 4.5)

**Severity**: Critical | **Effort**: Large

Add role-based access control so users can only access their own resources.

> **Devin Prompt**: *"Implement role-based authorization across the banking microservices. (1) At the API gateway, extract JWT roles/scopes and forward them as headers. (2) In user-service, restrict the PATCH update endpoint to ADMIN role only. (3) In fund-transfer-service and utility-payment-service, verify that the authenticated user owns the source account before allowing transfers/payments. (4) In user-service, restrict GET all users to ADMIN role, and GET single user to either ADMIN or the user themselves. Configure Keycloak realm roles (ADMIN, USER) in the realm export. Open a PR."*

### 2.5 Add Feign Error Decoder to All Services (Error Handling 2.4)

**Severity**: High | **Effort**: Medium

Port the `CustomFeignErrorDecoder` from user-service to fund-transfer-service and utility-payment-service.

> **Devin Prompt**: *"Add a CustomFeignErrorDecoder (similar to the one in user-service) to both fund-transfer-service and utility-payment-service. The decoder should: (1) Parse the ErrorResponse JSON from core-banking-service error responses. (2) Map 400 errors to SimpleBankingGlobalException. (3) Map 404 to EntityNotFoundException. (4) Map other errors to appropriate exception types. Register it in each service's CustomFeignClientConfiguration. Write tests using WireMock to verify error decoding. Open a PR."*

### 2.6 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (Testing 3.1)

**Severity**: Critical | **Effort**: Large

Write comprehensive unit tests for all service and controller classes.

> **Devin Prompt**: *"Write comprehensive unit tests for internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For each service, create: (1) Service layer tests with mocked repositories and Feign clients. (2) Controller layer tests using MockMvc with @WebMvcTest. Target at least 80% line coverage for service and controller classes. Use Mockito for mocking. Mock the Keycloak admin client in user-service tests. Open a PR."*

### 2.7 Add Pagination Metadata to List Endpoints (API Design 5.3)

**Severity**: High | **Effort**: Small

Return pagination metadata (total elements, total pages, current page) in list responses.

> **Devin Prompt**: *"Create a generic PaginatedResponse<T> wrapper class (in the shared library if it exists, otherwise in each service) with fields: content (List<T>), totalElements, totalPages, pageNumber, pageSize. Update all paginated endpoints (GET /api/v1/user in core-banking, GET /api/v1/bank-users in user-service, GET /api/v1/transfer, GET /api/v1/utility-payment) to return PaginatedResponse instead of raw List. Preserve backward compatibility by keeping the 'content' field name. Update existing tests. Open a PR."*

### 2.8 Add Custom Health Indicators (Observability 6.2)

**Severity**: Medium | **Effort**: Small

Add health indicators for critical dependencies.

> **Devin Prompt**: *"Add custom Spring Boot health indicators to the services: (1) In user-service, add a KeycloakHealthIndicator that checks Keycloak connectivity. (2) In fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator that pings core-banking-service's actuator/health endpoint. (3) Ensure all health indicators are registered and appear in /actuator/health. Write tests for each health indicator. Open a PR."*

### 2.9 Add Account Status Validation (Business Logic)

**Severity**: Medium | **Effort**: Small

Reject transactions from/to accounts that are not in ACTIVE status.

> **Devin Prompt**: *"In core-banking-service TransactionService, add account status validation in fundTransfer() and utilPayment(). Before processing any transaction, verify that all involved bank accounts have status ACTIVE. If an account is DORMANT, BLOCKED, or PENDING, throw a new AccountNotActiveException with an appropriate error code. Add the new exception class and update GlobalExceptionHandler to return 403 Forbidden. Write tests for each status scenario. Open a PR."*

### 2.10 Add Rate Limiting at API Gateway (Resilience 7.5)

**Severity**: Medium | **Effort**: Medium

Implement request rate limiting at the gateway level.

> **Devin Prompt**: *"Add rate limiting to the API gateway using Spring Cloud Gateway's built-in RequestRateLimiter filter with Redis. Configure rate limits per authenticated user: 100 requests/second for read endpoints, 10 requests/second for write endpoints (POST/PUT/PATCH). Add Redis to docker-compose. Configure fallback to return 429 Too Many Requests with a Retry-After header. Write integration tests. Open a PR."*

---

## Phase 3: Polish (6-12 weeks)

Improvements that complete the engineering maturity of the platform.

### 3.1 Add Integration Tests with Testcontainers (Testing 3.2)

**Severity**: High | **Effort**: Large

Write integration tests that verify full request flows using Testcontainers for MySQL and WireMock for service mocking.

> **Devin Prompt**: *"Add integration tests to core-banking-service using Testcontainers for MySQL. Create an integration test profile that uses a Testcontainers MySQL instance with Flyway migrations. Write integration tests for: (1) Full fund transfer flow - POST /api/v1/transaction/fund-transfer with real database verification. (2) Full utility payment flow. (3) Account balance verification after transactions. (4) Insufficient funds rejection. Add Testcontainers dependencies to build.gradle. Open a PR."*

### 3.2 Add Contract Tests Between Services (Testing 3.3)

**Severity**: High | **Effort**: Large

Implement Spring Cloud Contract tests for all Feign client interfaces.

> **Devin Prompt**: *"Add Spring Cloud Contract tests for inter-service communication. In core-banking-service (the producer), define contracts for: (1) GET /api/v1/account/bank-account/{account_number} (2) POST /api/v1/transaction/fund-transfer (3) POST /api/v1/transaction/util-payment (4) GET /api/v1/user/{identification}. Generate contract stubs. In the consumer services (fund-transfer, utility-payment, user), write contract verification tests using the generated stubs. Add spring-cloud-starter-contract-verifier and spring-cloud-starter-contract-stub-runner dependencies. Open a PR."*

### 3.3 Standardize URL Naming Conventions (API Design 5.1)

**Severity**: Medium | **Effort**: Medium

Align all endpoint paths to consistent plural, non-abbreviated naming.

> **Devin Prompt**: *"Standardize all API endpoint paths across services to use consistent plural nouns without abbreviations: (1) core-banking: rename /api/v1/account/util-account to /api/v1/accounts/utility-accounts, rename /api/v1/account/bank-account to /api/v1/accounts/bank-accounts. (2) Rename /api/v1/transaction/util-payment to /api/v1/transactions/utility-payments. (3) user-service: rename /api/v1/bank-users/update/{id} to just PATCH /api/v1/bank-users/{id}. Update all Feign clients in consuming services to match. Update gateway route configurations. Ensure Postman collection is also updated. Open a PR."*

### 3.4 Add Structured JSON Logging (Observability 6.1)

**Severity**: Medium | **Effort**: Medium

Switch to structured JSON logging with correlation IDs.

> **Devin Prompt**: *"Add structured JSON logging to all services. (1) Add logstash-logback-encoder dependency. (2) Create a logback-spring.xml in each service that outputs JSON format in non-dev profiles and plain text in dev profile. (3) Include traceId, spanId, service name, and a custom correlationId in every log line. (4) Propagate the correlationId (X-Correlation-Id header) from the API gateway through all downstream services. Open a PR."*

### 3.5 Add Filtering and Search to List Endpoints (API Design 5.4)

**Severity**: Medium | **Effort**: Medium

Add query parameter filtering to paginated endpoints.

> **Devin Prompt**: *"Add filtering capabilities to list endpoints: (1) GET /api/v1/transfer - filter by status, fromAccount, toAccount, date range (fromDate, toDate). (2) GET /api/v1/utility-payment - filter by status, providerId, account, date range. (3) GET /api/v1/bank-users - filter by status. (4) GET /api/v1/user (core) - filter by email. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering. Write tests for each filter combination. Open a PR."*

### 3.6 Implement Notification Service with RabbitMQ (Architecture)

**Severity**: Medium | **Effort**: Large

Build the planned but unimplemented notification service.

> **Devin Prompt**: *"Implement the notification service that was planned but never built. (1) Add spring-boot-starter-amqp to fund-transfer-service and utility-payment-service. (2) Create a new internet-banking-notification-service module. (3) Define a NotificationMessage DTO with fields: type, recipient, subject, body, metadata. (4) In fund-transfer-service and utility-payment-service, publish NotificationMessage to a RabbitMQ exchange after successful transactions. (5) In notification-service, consume messages and log them (email sending can be mocked). (6) Add RabbitMQ to docker-compose. (7) Register notification-service with Eureka. Open a PR."*

### 3.7 Add CI/CD Pipeline (Build/Deploy)

**Severity**: Medium | **Effort**: Medium

Create a GitHub Actions workflow for automated build, test, and Docker image publishing.

> **Devin Prompt**: *"Create a GitHub Actions CI/CD pipeline in .github/workflows/ci.yml that: (1) Triggers on push to main and on pull requests. (2) Runs ./gradlew build test for each service in parallel using a matrix strategy. (3) Uses a MySQL service container for core-banking-service integration tests. (4) Generates JaCoCo code coverage reports. (5) Builds Docker images for each service on main branch pushes. (6) Caches Gradle dependencies between runs. Open a PR."*

### 3.8 Add OpenAPI Response Documentation (API Design 5.5)

**Severity**: Medium | **Effort**: Small

Enhance Swagger/OpenAPI annotations with response examples and error documentation.

> **Devin Prompt**: *"Enhance OpenAPI documentation across all services. (1) Add @ApiResponse annotations to all controller methods documenting 200, 400, 404, and 500 responses with example payloads. (2) Add @Schema annotations to all DTO fields with descriptions and examples. (3) Add @Parameter annotations to path variables and query parameters. (4) Configure springdoc to include the API gateway as an aggregation point. Verify the generated docs at /swagger-ui.html. Open a PR."*

### 3.9 Add Bulkhead Pattern (Resilience 7.7)

**Severity**: Medium | **Effort**: Medium

Implement thread pool isolation for downstream service calls.

> **Devin Prompt**: *"Add Resilience4j bulkhead configuration to fund-transfer-service, utility-payment-service, and user-service. Configure thread pool bulkheads for Feign client calls with: maxConcurrentCalls=25, maxWaitDuration=500ms. This prevents a slow downstream service from consuming all threads. Add metrics for bulkhead state (full, available). Write tests that verify rejection when the bulkhead is full. Open a PR."*

### 3.10 Add Dependency Vulnerability Scanning (Security 4.8)

**Severity**: Medium | **Effort**: Small

Add automated dependency scanning to the build pipeline.

> **Devin Prompt**: *"Add the OWASP dependency-check Gradle plugin to all services. Configure it to fail the build on CVSS score >= 7.0. Add a GitHub Actions step that runs the dependency check and uploads the report as an artifact. Also add a Dependabot configuration file (.github/dependabot.yml) to automatically create PRs for dependency updates. Open a PR."*

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| Phase 1 - Quick Wins | 11 | 4 | 1-2 weeks |
| Phase 2 - Important | 10 | 3 | 3-6 weeks |
| Phase 3 - Polish | 10 | 0 | 6-12 weeks |
| **Total** | **31** | **7** | **10-20 weeks** |

### Recommended Execution Order Within Phases

**Phase 1 priority order**: 1.1 (race condition) -> 1.2 (double deduction) -> 1.3 (input validation) -> 1.4 (error responses) -> 1.6 (password leak) -> 1.7 (log redaction) -> 1.11 (docker secrets) -> 1.9 (timeouts) -> 1.10 (retry) -> 1.8 (metrics) -> 1.5 (typed ResponseEntity)

**Phase 2 priority order**: 2.1 (shared library) -> 2.2 (circuit breakers) -> 2.3 (idempotency) -> 2.4 (authorization) -> 2.5 (error decoder) -> 2.6 (unit tests) -> 2.7 (pagination) -> 2.9 (account status) -> 2.8 (health) -> 2.10 (rate limiting)

**Phase 3 priority order**: 3.1 (integration tests) -> 3.2 (contract tests) -> 3.7 (CI/CD) -> 3.4 (JSON logging) -> 3.3 (URL naming) -> 3.5 (filtering) -> 3.8 (OpenAPI docs) -> 3.10 (dependency scanning) -> 3.9 (bulkhead) -> 3.6 (notification service)
