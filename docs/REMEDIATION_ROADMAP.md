# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort.

---

## Phase 1: Quick Wins (1–2 Weeks)

High-impact items that can be resolved with small effort. Focus on security fixes, critical bugs, and foundational improvements.

### 1.1 Fix Balance Calculation Bug (GAP-RES-07)

**Priority:** P0 — Data corruption bug  
**Effort:** Small  
**Services:** core-banking-service

The `availableBalance` double-deduction bug in `TransactionService.utilPayment()` and `internalFundTransfer()` is actively causing incorrect balances.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `utilPayment()` and `internalFundTransfer()`, the `availableBalance` is set by subtracting the amount from `actualBalance` after `actualBalance` has already been decremented, causing a double deduction. The correct behavior is `setAvailableBalance(getActualBalance())` after the `actualBalance` has been updated (since available should equal actual for this simple model). Apply the fix, update existing unit tests to assert correct balance values, and ensure all tests pass.

---

### 1.2 Add Input Validation to All DTOs (GAP-SEC-03)

**Priority:** P0 — Security vulnerability  
**Effort:** Medium  
**Services:** All 4 business services

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services. Specifically: (1) `FundTransferRequest`: add `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`. (2) `UtilityPaymentRequest` in core-banking: add `@NotBlank` on `account` and `referenceNumber`, `@NotNull @Positive` on `amount` and `providerId`. (3) `User` registration DTO in user-service: add `@NotBlank` on `identification`, `@Email @NotBlank` on `email`, `@NotBlank` on `password`. (4) `UtilityPaymentRequest` in utility-payment-service: same as core-banking version. Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 400 with a structured error listing field-level errors. Write unit tests for validation.

---

### 1.3 Fix HTTP Status Codes in Exception Handlers (GAP-ERR-01, GAP-ERR-02, GAP-ERR-03)

**Priority:** P1  
**Effort:** Small  
**Services:** All 4 business services

**Devin Prompt:**
> Standardize error handling across all services. In every `GlobalExceptionHandler`: (1) Change `EntityNotFoundException` to return HTTP 404 instead of 400. (2) Add a handler for `InsufficientFundsException` returning HTTP 422. (3) Add a handler for `UserAlreadyRegisteredException` returning HTTP 409. (4) Change the generic `Exception.class` handler to return HTTP 500 with a structured `ErrorResponse` body (do not leak exception details — log the exception server-side and return a generic message). (5) Ensure all error responses use the `ErrorResponse { code, message }` format consistently. Make `ErrorResponse` identical across all services (use `@Builder` pattern everywhere). Write unit tests for each exception handler.

---

### 1.4 Remove Sensitive Data from Logs (GAP-OBS-02)

**Priority:** P1 — Security concern  
**Effort:** Small  
**Services:** All business services

**Devin Prompt:**
> Audit all `log.info()` and `log.debug()` calls across all services for sensitive data exposure. In `internet-banking-user-service/UserController.java`, the `log.info("Creating user with {}", request.toString())` could log passwords. Replace `request.toString()` with only safe fields (e.g., `request.getEmail()`). Add `@ToString.Exclude` (Lombok) annotation to the `password` field in the `User` DTO. Review all other `toString()` calls in controllers and remove or sanitize any that could leak sensitive information (account numbers should be masked to show only last 4 digits in logs).

---

### 1.5 Add Feign Timeout Configuration (GAP-RES-03)

**Priority:** P1  
**Effort:** Small  
**Services:** user-service, fund-transfer-service, utility-payment-service

**Devin Prompt:**
> Add Feign client timeout configuration to all services that use OpenFeign. In each service's `application.yml` (or via Spring Cloud Config), add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. For the core-banking-service Feign clients specifically, set read-timeout to 15000 since financial transactions may take longer. Also configure API Gateway route timeouts in the gateway's config.

---

### 1.6 Add Retry Policies (GAP-RES-02)

**Priority:** P1  
**Effort:** Small  
**Services:** fund-transfer-service, utility-payment-service, user-service

**Devin Prompt:**
> Add Spring Retry support to all services that make inter-service calls. Add `spring-retry` and `spring-boot-starter-aop` dependencies to `build.gradle` for fund-transfer-service, utility-payment-service, and user-service. Configure Feign retry via `spring.cloud.openfeign.client.config.default.retryer` with max 3 attempts and 1-second backoff. Important: only retry on GET requests and connection failures — do NOT retry POST requests (fund transfers, payments) as they are not idempotent. Add `@Retryable` to read-only Feign methods where appropriate.

---

### 1.7 Fix OpenAPI Dependency (GAP-API-05)

**Priority:** P2  
**Effort:** Small  
**Services:** core-banking, user-service, fund-transfer-service, utility-payment-service

**Devin Prompt:**
> In `build.gradle` for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, change the OpenAPI dependency from `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` to `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. These services use Spring MVC, not WebFlux. Only the API Gateway should use the webflux variant. Verify each service starts correctly and the Swagger UI is accessible at `/swagger-ui.html`.

---

### 1.8 Add Typed ResponseEntity (GAP-API-01)

**Priority:** P2  
**Effort:** Small  
**Services:** core-banking, fund-transfer-service, utility-payment-service

**Devin Prompt:**
> Add generic type parameters to all `ResponseEntity` return types in controllers across core-banking-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Use the correct DTO type for each endpoint. The internet-banking-user-service already has typed responses and can be used as a reference.

---

### 1.9 Add JaCoCo Test Coverage (GAP-TEST-02)

**Priority:** P2  
**Effort:** Small  
**Services:** All services

**Devin Prompt:**
> Add the JaCoCo Gradle plugin to all services. In each `build.gradle`, add `id 'jacoco'` to the plugins block. Configure `jacocoTestReport` to generate HTML and XML reports. Add a `jacocoTestCoverageVerification` task with a minimum line coverage threshold of 40% (to start — this will be raised later). Wire it so `check` depends on `jacocoTestCoverageVerification`. Run `./gradlew test jacocoTestReport` in each service and verify reports are generated.

---

### 1.10 Add Dependency Vulnerability Scanning (GAP-SEC-06)

**Priority:** P1  
**Effort:** Small  
**Services:** All services

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to all services. In each `build.gradle`, add `id 'org.owasp.dependencycheck' version '9.0.10'` to the plugins block. Configure it to fail the build on CVSS score >= 7. Run `./gradlew dependencyCheckAnalyze` on each service and document any findings. If there are high-severity vulnerabilities, create a follow-up task to update the affected dependencies.

---

### 1.11 Add Prometheus Metrics (GAP-OBS-04)

**Priority:** P2  
**Effort:** Small  
**Services:** All services

**Devin Prompt:**
> Add Prometheus metrics support to all services. Add `io.micrometer:micrometer-registry-prometheus` to the `dependencies` block in each service's `build.gradle`. In each service's application config, ensure `management.endpoints.web.exposure.include` includes `health,info,prometheus,metrics`. Verify that `/actuator/prometheus` returns metrics in Prometheus format. Optionally add a Prometheus service to `docker-compose.yml` that scrapes all services.

---

### 1.12 Fix Keycloak Singleton Thread Safety (GAP-SEC-05)

**Priority:** P2  
**Effort:** Small  
**Services:** user-service

**Devin Prompt:**
> Fix the thread-unsafe Keycloak singleton in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the manual singleton pattern with a Spring `@Bean` method in a `@Configuration` class. Create a new `KeycloakConfig` class that defines a `@Bean Keycloak keycloak(KeycloakProperties props)` method. Remove the `getInstance()` method and static field from `KeycloakProperties`. Update `KeycloakManager` to inject the `Keycloak` bean directly instead of calling `keycloakProperties.getInstance()`.

---

## Phase 2: Important (3–6 Weeks)

Structural improvements that significantly improve reliability, maintainability, and operational readiness.

### 2.1 Add Circuit Breakers (GAP-RES-01)

**Priority:** P0  
**Effort:** Medium  
**Services:** fund-transfer-service, utility-payment-service, user-service

**Devin Prompt:**
> Add Resilience4j circuit breaker support to all services that call other services via Feign. Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle` for fund-transfer-service, utility-payment-service, and user-service. Configure circuit breakers in application.yml with: `failure-rate-threshold: 50`, `wait-duration-in-open-state: 30s`, `sliding-window-size: 10`. Apply `@CircuitBreaker` to Feign client calls in each service class (FundTransferService, UtilityPaymentService, UserService). Add fallback methods that return appropriate error responses when the circuit is open. Write unit tests that verify circuit breaker behavior using Resilience4j test utilities.

---

### 2.2 Implement Feign Error Decoding (GAP-ERR-04)

**Priority:** P1  
**Effort:** Medium  
**Services:** fund-transfer-service, utility-payment-service

**Devin Prompt:**
> Add custom Feign error decoders to fund-transfer-service and utility-payment-service (user-service already has `CustomFeignErrorDecoder`). Create a `CustomFeignErrorDecoder` class in each service that implements `feign.codec.ErrorDecoder`. Map Core Banking Service error responses: 404 -> `EntityNotFoundException`, 422 -> `InsufficientFundsException`, 400 -> `SimpleBankingGlobalException`, 5xx -> `ServiceUnavailableException` (new exception). Register the error decoder in `CustomFeignClientConfiguration`. Handle the case where a fund transfer or payment entity is saved as PENDING but the downstream call fails — update the entity status to `FAILED` in a catch block. Write integration tests using WireMock to test error scenarios.

---

### 2.3 Add Idempotency Protection (GAP-RES-05)

**Priority:** P0  
**Effort:** Medium  
**Services:** fund-transfer-service, utility-payment-service, core-banking-service

**Devin Prompt:**
> Implement idempotency for financial operations. (1) Add an `Idempotency-Key` HTTP header support: create a servlet filter that reads the `Idempotency-Key` header. (2) In fund-transfer-service and utility-payment-service, before processing a transfer/payment, check if a record with the same idempotency key already exists. If it does, return the cached response. (3) Add an `idempotency_key` column to `fund_transfer` and `utility_payment` tables with a UNIQUE constraint. (4) Store the idempotency key when creating the entity. (5) In the controllers, require the `Idempotency-Key` header for POST requests (return 400 if missing). Write tests that verify duplicate requests with the same idempotency key return the same response without creating duplicate records.

---

### 2.4 Create Shared Common Library (GAP-ORG-02, GAP-ORG-04)

**Priority:** P1  
**Effort:** Large  
**Services:** All services

**Devin Prompt:**
> Create a shared `banking-common` Gradle module that extracts duplicated code. (1) Create a new top-level directory `banking-common/` with its own `build.gradle` (as a library, not a Spring Boot app). (2) Move the following shared classes into it: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `EntityNotFoundException`, `CustomFeignClientConfiguration`. (3) Create a root `settings.gradle` that includes all service projects and `banking-common`. (4) Update each service's `build.gradle` to depend on `banking-common` via `implementation project(':banking-common')`. (5) Remove the duplicated classes from each service. (6) Verify all services compile and tests pass.

---

### 2.5 Add Unit Tests for All Services (GAP-TEST-01)

**Priority:** P1  
**Effort:** Large  
**Services:** user-service, fund-transfer-service, utility-payment-service

**Devin Prompt:**
> Add comprehensive unit tests for the three business services that currently lack them. For each service: (1) Add service-layer unit tests using Mockito for all methods in the main service class. (2) Add controller-layer tests using MockMvc for all endpoints, testing happy paths and error cases. (3) Add mapper tests to verify entity-to-DTO conversion. Target at least 70% line coverage. For internet-banking-user-service, mock the KeycloakUserService and BankingCoreRestClient. For internet-banking-fund-transfer-service, mock the BankingCoreFeignClient. For internet-banking-utility-payment-service, mock the BankingCoreRestClient. Use H2 in-memory database for repository tests. Ensure test application.yml files disable Eureka, Config Server, and Flyway.

---

### 2.6 Implement Pagination Response Envelopes (GAP-API-03)

**Priority:** P2  
**Effort:** Small  
**Services:** All business services

**Devin Prompt:**
> Fix pagination responses across all services. (1) Create a generic `PagedResponse<T>` DTO in the shared common library with fields: `content: List<T>`, `pageNumber: int`, `pageSize: int`, `totalElements: long`, `totalPages: int`, `last: boolean`. (2) Update all paginated endpoints to return `ResponseEntity<PagedResponse<T>>` instead of `ResponseEntity<List<T>>`. (3) Map Spring's `Page<T>` to `PagedResponse<T>` in each service method. (4) Update existing tests to verify pagination metadata is returned. This affects: `UserController.readUsers()` in core-banking and user-service, `FundTransferController.readFundTransfers()`, and `UtilityPaymentController.readPayments()`.

---

### 2.7 Add Structured JSON Logging (GAP-OBS-01)

**Priority:** P2  
**Effort:** Small  
**Services:** All services

**Devin Prompt:**
> Configure structured JSON logging for all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. Create a `logback-spring.xml` in each service's `src/main/resources/` that uses `LogstashEncoder` for the console appender in the `docker` profile and keeps the standard pattern encoder for local development. Include trace ID and span ID in the JSON output. Ensure the logging config is consistent across all services.

---

### 2.8 Add Custom Health Checks (GAP-OBS-03)

**Priority:** P2  
**Effort:** Small  
**Services:** user-service, fund-transfer-service, utility-payment-service

**Devin Prompt:**
> Add custom Spring Boot health indicators to each business service. (1) In user-service, create a `KeycloakHealthIndicator` that checks Keycloak reachability. (2) In fund-transfer-service and utility-payment-service, create a `CoreBankingHealthIndicator` that calls a lightweight Core Banking endpoint (e.g., actuator health). (3) Ensure `management.endpoint.health.show-details=always` is set in application config. (4) Write unit tests for each health indicator. Register the health indicators as Spring beans via `@Component`.

---

### 2.9 Set Up Multi-Project Gradle Build (GAP-ORG-01)

**Priority:** P2  
**Effort:** Medium  
**Services:** All services

**Devin Prompt:**
> Create a root-level Gradle multi-project build. (1) Create a root `settings.gradle` that includes all 6 service projects and the `banking-common` module. (2) Create a root `build.gradle` that defines shared configuration: Java 21 sourceCompatibility, Spring Boot 3.2.4 plugin version, Spring Cloud 2023.0.0 BOM, common test configuration, and shared repositories. (3) Use `subprojects {}` block to apply common configuration. (4) Move version declarations to the root build file and remove duplicates from each service's `build.gradle`. (5) Add a Gradle wrapper at the root level (`gradle wrapper`). (6) Verify `./gradlew build` from the root builds all services successfully.

---

### 2.10 Add Rate Limiting at API Gateway (GAP-SEC-04)

**Priority:** P1  
**Effort:** Medium  
**Services:** api-gateway

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency to the API Gateway's `build.gradle`. (2) Add a Redis container to `docker-compose.yml`. (3) Configure rate limiting in the gateway's route configuration with: replenish rate of 10 requests/second and burst capacity of 20 for general endpoints, and 2 requests/second with burst of 5 for financial operations (fund-transfer, utility-payment POST endpoints). (4) Use the `PrincipalNameKeyResolver` to rate limit per authenticated user. (5) Test that rate-limited requests receive HTTP 429 Too Many Requests.

---

## Phase 3: Polish (6–12 Weeks)

Architectural improvements and operational excellence items for production readiness.

### 3.1 Implement Saga Pattern for Distributed Transactions (GAP-RES-06)

**Priority:** P0  
**Effort:** Large  
**Services:** fund-transfer-service, utility-payment-service, core-banking-service

**Devin Prompt:**
> Implement a choreography-based Saga pattern for fund transfer and utility payment flows to handle distributed transaction failures. (1) Add RabbitMQ to `docker-compose.yml` and add `spring-boot-starter-amqp` to fund-transfer-service, utility-payment-service, and core-banking-service. (2) Define events: `FundTransferInitiated`, `FundTransferCompleted`, `FundTransferFailed`, `FundTransferCompensated` (and equivalent for utility payments). (3) Modify the fund transfer flow: Fund Transfer Service publishes `FundTransferInitiated` event, Core Banking Service consumes it and processes the transfer, publishes `FundTransferCompleted` or `FundTransferFailed`, Fund Transfer Service updates its local entity status accordingly. (4) Add a compensation endpoint in Core Banking Service to reverse a failed transfer. (5) Add a scheduled job that checks for `PENDING` entities older than 5 minutes and triggers compensation. (6) Write integration tests using Testcontainers with RabbitMQ.

---

### 3.2 Add Contract Tests Between Services (GAP-TEST-01)

**Priority:** P1  
**Effort:** Large  
**Services:** All services with inter-service communication

**Devin Prompt:**
> Add Spring Cloud Contract tests between services. (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service (the provider). (2) Define contracts in Groovy DSL for all Core Banking endpoints consumed by other services: fund-transfer Feign client endpoints, utility-payment Feign client endpoints, and user-service Feign client endpoints. (3) Add `spring-cloud-starter-contract-stub-runner` to fund-transfer-service, utility-payment-service, and user-service (the consumers). (4) Write consumer-side tests that use stub runners to verify Feign clients work correctly against the contract stubs. (5) Configure the contract tests to run as part of the standard `./gradlew test` task.

---

### 3.3 Externalize Secrets Management (GAP-SEC-01)

**Priority:** P0  
**Effort:** Medium  
**Services:** docker-compose, all services

**Devin Prompt:**
> Remove all hardcoded credentials from the repository and implement proper secrets management. (1) Create a `.env.example` file in the `docker-compose/` directory documenting all required environment variables (without actual values). (2) Update `docker-compose.yml` to use environment variable references (`${MYSQL_ROOT_PASSWORD}`) instead of hardcoded values. (3) Add `.env` to `.gitignore`. (4) For Spring Cloud Config, evaluate using encrypted properties or Vault integration. (5) Remove the test credentials from `README.md` and replace with instructions on how to set up credentials. (6) Run `git filter-branch` or `BFG Repo Cleaner` to remove historical commits containing credentials (coordinate with team as this rewrites history).

---

### 3.4 Add Filtering and Sorting to List Endpoints (GAP-API-04)

**Priority:** P2  
**Effort:** Medium  
**Services:** All business services

**Devin Prompt:**
> Add filtering and sorting support to all list/paginated endpoints. (1) In fund-transfer-service, add query parameters: `status`, `fromAccount`, `toAccount`, `fromDate`, `toDate`. Create a JPA Specification or query derivation for dynamic filtering. (2) In utility-payment-service, add query parameters: `status`, `providerId`, `account`, `fromDate`, `toDate`. (3) In user-service and core-banking user endpoints, add query parameters: `status`, `email` (partial match), `name` (partial match). (4) Support Spring Data's sort parameters (`sort=amount,desc`). (5) Write tests for each filter combination.

---

### 3.5 Implement Notification Service (Referenced but Unbuilt)

**Priority:** P2  
**Effort:** Large  
**Services:** New service + fund-transfer, utility-payment

**Devin Prompt:**
> Implement the Notification Service that is referenced in the README but not yet built. (1) Create a new `internet-banking-notification-service` Gradle project following the same structure as other services. (2) Add RabbitMQ dependency (`spring-boot-starter-amqp`). (3) Define message queues: `fund-transfer-notifications`, `utility-payment-notifications`. (4) Create message consumers that listen to these queues. (5) Implement a simple email notification sender (use Spring Mail with a configurable SMTP server). (6) In fund-transfer-service and utility-payment-service, publish messages to RabbitMQ after successful operations. (7) Add the notification service to `docker-compose.yml`. (8) Write unit and integration tests.

---

### 3.6 Add Centralized Log Aggregation (GAP-OBS-05)

**Priority:** P2  
**Effort:** Large  
**Services:** Infrastructure (docker-compose)

**Devin Prompt:**
> Add an ELK (Elasticsearch, Logstash, Kibana) or Loki/Grafana stack to the docker-compose setup for centralized log aggregation. (1) Add Elasticsearch, Logstash, and Kibana containers to `docker-compose.yml`. (2) Configure Logstash to receive logs from all services (via the JSON logging already configured in Phase 2). (3) Create a Kibana index pattern for the application logs. (4) Add a Grafana container with pre-configured dashboards showing request rates, error rates, and latency percentiles per service. (5) Document how to access the logging/monitoring dashboards in the README.

---

### 3.7 RESTful Endpoint Naming Cleanup (GAP-API-06)

**Priority:** P3  
**Effort:** Small  
**Services:** user-service

**Devin Prompt:**
> Clean up non-RESTful endpoint naming in user-service. Change `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users` (POST to collection already implies creation). Change `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}` (PATCH method already implies update). Keep the old endpoints working as deprecated aliases (using an additional `@RequestMapping`) for backward compatibility. Add `@Deprecated` annotation to the old endpoint methods. Update the Postman collection if applicable. Update API Gateway route configuration if needed.

---

### 3.8 Standardize Package Structure (GAP-ORG-03)

**Priority:** P3  
**Effort:** Small  
**Services:** All services

**Devin Prompt:**
> Standardize package structure across all services to follow a consistent convention. Adopt the core-banking-service's structure as the standard: `controller/`, `service/`, `model/dto/`, `model/dto/request/`, `model/dto/response/`, `model/entity/`, `model/mapper/`, `repository/`, `exception/`, `configuration/`. Refactor user-service to move `model.repository` to `repository` and `model.rest.response` to `model.dto.response`. Refactor utility-payment-service to move `model.rest.request` to `model.dto.request` and `model.rest.response` to `model.dto.response` and `repository` to the standard location. Ensure all imports are updated and tests pass.

---

## Summary Timeline

| Phase | Duration | Key Outcomes |
|---|---|---|
| **Phase 1: Quick Wins** | Weeks 1–2 | Critical bugs fixed, input validation added, error handling standardized, timeouts configured, metrics enabled |
| **Phase 2: Important** | Weeks 3–8 | Circuit breakers operational, idempotency protection live, shared library created, test coverage > 70%, rate limiting active |
| **Phase 3: Polish** | Weeks 9–16+ | Saga pattern for distributed transactions, contract tests, secrets externalized, notification service built, full observability stack |

## Priority Matrix

```
                    Low Effort ──────────────────── High Effort
                    │                                        │
  Critical/High ────┤  1.1 Balance Bug Fix                   │  2.1 Circuit Breakers
  Impact            │  1.3 HTTP Status Codes                 │  2.3 Idempotency
                    │  1.4 Sensitive Data in Logs             │  2.5 Unit Tests
                    │  1.5 Feign Timeouts                    │  3.1 Saga Pattern
                    │  1.6 Retry Policies                    │  2.10 Rate Limiting
                    │  1.2 Input Validation                  │
                    │  1.10 Dep Vulnerability Scan            │
                    │                                        │
  Medium/Low ───────┤  1.7 OpenAPI Fix                       │  2.4 Shared Library
  Impact            │  1.8 Typed ResponseEntity              │  2.9 Multi-Project Build
                    │  1.9 JaCoCo Coverage                   │  3.4 Filtering/Sorting
                    │  1.11 Prometheus Metrics                │  3.5 Notification Service
                    │  1.12 Keycloak Singleton               │  3.6 Log Aggregation
                    │  2.7 JSON Logging                      │
                    │  3.7 RESTful Naming                    │
                    │  3.8 Package Structure                 │
                    │                                        │
                    └────────────────────────────────────────┘
```
