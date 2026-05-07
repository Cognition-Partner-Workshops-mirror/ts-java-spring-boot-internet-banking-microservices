# Remediation Roadmap

A phased plan to address the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md). Each phase is ordered by impact and dependency — quick wins first to reduce immediate risk, followed by structural improvements, then polish.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort fixes that reduce production risk immediately.

### 1.1 Fix Error Response Leakage (Gap 2.1)

**Priority**: Critical | **Effort**: Small

Replace the generic exception handler that leaks stack traces with a structured error response.

**Devin Prompt:**
> In all services that have a `GlobalExceptionHandler`, update the `handleException(Exception e)` method to return a structured `ErrorResponse` with a generic message like "An unexpected error occurred" and log the actual exception at ERROR level. Never include `e.toString()` or stack trace in the HTTP response body. Apply this change to: `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`.

---

### 1.2 Add Input Validation (Gap 4.2, 2.5)

**Priority**: Critical | **Effort**: Small

Add Bean Validation annotations to all request DTOs and handle validation errors.

**Devin Prompt:**
> Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`) to all request DTO classes: `FundTransferRequest` (in core-banking-service and fund-transfer-service), `UtilityPaymentRequest` (in core-banking-service and utility-payment-service), and `User`/`UserUpdateRequest` (in user-service). Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details.

---

### 1.3 Use Proper HTTP Status Codes (Gap 2.2)

**Priority**: High | **Effort**: Small

Map exceptions to appropriate HTTP status codes instead of always returning 400.

**Devin Prompt:**
> Update `GlobalExceptionHandler` in all services to return proper HTTP status codes: `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, `UserAlreadyRegisteredException` → 409, `InvalidEmailException` → 422, `InvalidBankingUserException` → 404. Keep `SimpleBankingGlobalException` as 400. Add a catch-all for unexpected exceptions returning 500.

---

### 1.4 Externalize Credentials (Gap 4.1)

**Priority**: Critical | **Effort**: Small

Remove hardcoded passwords from Docker Compose files.

**Devin Prompt:**
> Refactor `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to read all passwords and credentials from environment variables with sensible defaults for local development. Create a `.env.example` file documenting all required environment variables: `MYSQL_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KC_DB_PASSWORD`, `POSTGRES_PASSWORD`. Add `.env` to `.gitignore`. Update the README with instructions on setting up the `.env` file.

---

### 1.5 Add Feign Client Timeouts (Gap 7.3)

**Priority**: High | **Effort**: Small

Configure explicit timeouts to prevent indefinite hanging on downstream failures.

**Devin Prompt:**
> Add Feign client timeout configuration to `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Set connect timeout to 5 seconds and read timeout to 10 seconds. This can be done via `application.yml` properties: `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`.

---

### 1.6 Fix Sensitive Data in Logs (Gap 6.2)

**Priority**: High | **Effort**: Small

Prevent account numbers, emails, and passwords from appearing in log output.

**Devin Prompt:**
> Review all `log.info()` calls in controllers and services across the project. Replace direct `toString()` logging of request objects with selective field logging that excludes sensitive data. For `FundTransferRequest`, log only the transfer amount. For `User` objects, log only the user ID. For `UtilityPaymentRequest`, log only the provider ID. Never log passwords, full account numbers, or email addresses at INFO level.

---

### 1.7 Restrict Database User Permissions (Gap 4.6)

**Priority**: High | **Effort**: Small

Create per-service database users with least-privilege access.

**Devin Prompt:**
> Update `docker-compose/mysql/privileges.sql` to create separate database users for each service with minimal permissions. `core_banking_user` should have SELECT, INSERT, UPDATE on `banking_core_service`. `fund_transfer_user` should have SELECT, INSERT, UPDATE on `banking_core_fund_transfer_service`. `user_service_user` should have SELECT, INSERT, UPDATE on `banking_core_user_service`. `utility_payment_user` should have SELECT, INSERT, UPDATE on `banking_core_utility_payment_service`. Remove the overly-permissive `javatodev_development` user or restrict it to local development only.

---

### 1.8 Add Graceful Shutdown (Gap 7.8)

**Priority**: Medium | **Effort**: Small

Prevent request failures during deployments.

**Devin Prompt:**
> Add graceful shutdown configuration to all application services. In the Spring Cloud Config repository (or local `application.yml` if config server is not available), add `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`. Also configure Eureka deregistration delay to allow in-flight requests to complete before the instance is removed from the registry.

---

## Phase 2: Important (3–6 weeks)

Structural improvements that significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers (Gap 7.1)

**Priority**: Critical | **Effort**: Medium

Prevent cascading failures when Core Banking Service is unavailable.

**Devin Prompt:**
> Add Resilience4j circuit breaker to all Feign clients in `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to each service's `build.gradle`. Configure circuit breakers with: failure rate threshold of 50%, wait duration in open state of 30 seconds, sliding window size of 10. Add `@CircuitBreaker` annotation to Feign client methods with appropriate fallback methods that return meaningful error responses.

---

### 2.2 Add Retry Policies (Gap 7.2)

**Priority**: High | **Effort**: Small

Handle transient failures gracefully.

**Devin Prompt:**
> Configure Resilience4j retry for all Feign clients. Add retry configuration in `application.yml` for each service: max attempts = 3, wait duration = 1 second, exponential backoff multiplier = 2. Only retry on `ConnectException`, `SocketTimeoutException`, and HTTP 503 responses. Do NOT retry on 4xx client errors or POST requests that modify state (to avoid duplicate transactions).

---

### 2.3 Implement Idempotency Protection (Gap 7.6)

**Priority**: Critical | **Effort**: Medium

Prevent duplicate financial transactions from double-submissions.

**Devin Prompt:**
> Add idempotency key support to the fund transfer and utility payment endpoints. Accept an `X-Idempotency-Key` header on POST requests. Before processing, check if a transaction with that key already exists in the database — if so, return the cached response. Add an `idempotencyKey` column to `FundTransferEntity` and `UtilityPaymentEntity` tables with a unique constraint. Create a Flyway migration for the schema change. Return HTTP 409 if a duplicate key is detected with a different request payload.

---

### 2.4 Fix Non-Atomic Distributed Transactions (Gap 7.7)

**Priority**: Critical | **Effort**: Large

Prevent data inconsistency when Core Banking call fails after local save.

**Devin Prompt:**
> Implement the Saga pattern for fund transfer and utility payment flows. Replace the current synchronous approach with: (1) Save local entity with status PENDING, (2) Call Core Banking Service, (3) On success → update status to SUCCESS, (4) On failure → update status to FAILED and implement compensating transaction. Add a scheduled job that retries PENDING transactions older than 5 minutes. Consider implementing a transactional outbox pattern for reliable message delivery when RabbitMQ is added.

---

### 2.5 Add Unit Test Coverage (Gap 3.1)

**Priority**: High | **Effort**: Large

Achieve meaningful test coverage across all services.

**Devin Prompt:**
> Write unit tests for all service classes in the project. Target files: `FundTransferService` (internet-banking-fund-transfer-service), `UtilityPaymentService` (internet-banking-utility-payment-service), `UserService` and `KeycloakUserService` (internet-banking-user-service). Use Mockito for mocking dependencies. Test happy paths, error cases (entity not found, insufficient funds), and edge cases (zero amounts, null fields). Aim for 80%+ line coverage on service classes. Use JUnit 5 assertions and follow the existing test style in `TransactionServiceTest`.

---

### 2.6 Add Integration Tests (Gap 3.2)

**Priority**: High | **Effort**: Large

Verify end-to-end flows with real databases.

**Devin Prompt:**
> Add integration tests using Testcontainers for MySQL to `core-banking-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies to each service's `build.gradle`. Write `@SpringBootTest` tests that verify: (1) account creation and retrieval, (2) fund transfer end-to-end with balance verification, (3) utility payment with balance deduction. Use `@DynamicPropertySource` to configure the MySQL container URL.

---

### 2.7 Add Pagination Metadata (Gap 5.3)

**Priority**: High | **Effort**: Small

Return proper pagination information in list responses.

**Devin Prompt:**
> Update all GET list endpoints to return pagination metadata. Instead of returning `List<T>`, return a wrapper object containing `content` (the list), `totalElements`, `totalPages`, `pageNumber`, `pageSize`, and `hasNext`. Apply this to: `GET /api/v1/transfer` (fund-transfer-service), `GET /api/v1/utility-payment` (utility-payment-service), `GET /api/v1/bank-users` (user-service), and `GET /api/v1/user` (core-banking-service). Use Spring's `Page` object properties to populate the metadata.

---

### 2.8 Add Structured Logging (Gap 6.1)

**Priority**: Medium | **Effort**: Small

Enable log aggregation and searchability.

**Devin Prompt:**
> Configure JSON-structured logging for all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service's `build.gradle`. Create a `logback-spring.xml` in each service's `src/main/resources/` that uses `LogstashEncoder` for the `docker` profile and standard pattern for local development. Include MDC fields for `traceId`, `spanId`, `userId`, and `serviceName` in all log entries.

---

### 2.9 Create Shared Library Module (Gap 1.1)

**Priority**: Medium | **Effort**: Medium

Eliminate code duplication across services.

**Devin Prompt:**
> Create a new Gradle module called `internet-banking-common` containing shared code: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` (base class), and common enums (`TransactionStatus`). Create a root `settings.gradle` that includes all service modules and the common module. Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. Remove the duplicated classes from individual services.

---

### 2.10 Add Custom Health Indicators (Gap 6.3)

**Priority**: Medium | **Effort**: Small

Expose meaningful health status for monitoring.

**Devin Prompt:**
> Add custom health indicators to each application service. For `core-banking-service`: check MySQL connectivity. For `internet-banking-user-service`: check Keycloak admin API reachability. For `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`: check that `core-banking-service` is reachable via Eureka. Implement `org.springframework.boot.actuate.health.HealthIndicator` for each and register as Spring beans.

---

## Phase 3: Polish (6+ weeks)

Improvements that enhance developer experience and operational maturity.

### 3.1 Add Contract Tests (Gap 3.3)

**Priority**: Medium | **Effort**: Large

Prevent breaking changes between services.

**Devin Prompt:**
> Implement Spring Cloud Contract tests between services. In `core-banking-service`, define contracts for: `GET /api/v1/account/bank-account/{number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`, `GET /api/v1/user/{identification}`. Generate stubs that consumer services (`fund-transfer-service`, `utility-payment-service`, `user-service`) can use in their integration tests. Add `spring-cloud-starter-contract-verifier` to the producer and `spring-cloud-starter-contract-stub-runner` to consumers.

---

### 3.2 Add Rate Limiting (Gap 4.4)

**Priority**: Medium | **Effort**: Medium

Protect against abuse and DoS.

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. Configure Redis-backed rate limiting with: 10 requests/second for authenticated users, 2 requests/second for unauthenticated endpoints (registration). Add `spring-boot-starter-data-redis-reactive` dependency to the gateway's `build.gradle`. Add a Redis container to `docker-compose.yml`. Configure rate limiter in the gateway routes using `spring.cloud.gateway.routes[].filters`.

---

### 3.3 Add Prometheus Metrics (Gap 6.4)

**Priority**: Medium | **Effort**: Small

Enable metrics-based monitoring and alerting.

**Devin Prompt:**
> Add `io.micrometer:micrometer-registry-prometheus` dependency to all services' `build.gradle`. Configure actuator to expose the Prometheus endpoint: `management.endpoints.web.exposure.include=health,info,prometheus,metrics`. Add custom metrics for business operations: counter for fund transfers (success/failure), histogram for transfer amounts, gauge for active transactions. Optionally add a Prometheus + Grafana stack to `docker-compose.yml` for local monitoring.

---

### 3.4 Add Bulkhead Isolation (Gap 7.5)

**Priority**: Medium | **Effort**: Medium

Prevent thread pool exhaustion from slow downstream services.

**Devin Prompt:**
> Configure Resilience4j bulkheads for Feign clients in `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Use semaphore-based bulkheads with: max concurrent calls = 25, max wait duration = 500ms. Configure separate bulkhead instances for each downstream service to isolate failures. Add bulkhead configuration in `application.yml` under `resilience4j.bulkhead.instances`.

---

### 3.5 Add Security Headers (Gap 4.7)

**Priority**: Medium | **Effort**: Small

Harden HTTP responses against common web attacks.

**Devin Prompt:**
> Add a global filter to the API Gateway that adds security headers to all responses: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `Cache-Control: no-store` for API responses. Implement as a `GlobalFilter` bean in `GatewayConfiguration.java`.

---

### 3.6 Add Dependency Vulnerability Scanning (Gap 4.5)

**Priority**: Medium | **Effort**: Small

Detect known vulnerabilities in third-party libraries.

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to the root build. Add `id 'org.owasp.dependencycheck' version '9.0.9'` to a root `build.gradle` that applies to all subprojects. Configure it to fail the build on CVSS score >= 7. Add a CI/CD step that runs `./gradlew dependencyCheckAnalyze` and publishes the HTML report as an artifact. Suppress any false positives with a `suppressions.xml` file.

---

### 3.7 Enhance OpenAPI Documentation (Gap 5.6)

**Priority**: Low | **Effort**: Small

Provide comprehensive API documentation.

**Devin Prompt:**
> Add `@OpenAPIDefinition` to each service's main application class with title, version, description, and contact information. Add `@SecurityScheme` annotation for Bearer JWT authentication. Add `@ApiResponse` annotations to all controller methods documenting success and error responses with example payloads. Configure springdoc to group APIs by tag and expose the Swagger UI at `/swagger-ui.html`. Ensure raw `ResponseEntity` returns are replaced with `ResponseEntity<SpecificType>` for accurate schema generation.

---

### 3.8 Add Controller Tests (Gap 3.4)

**Priority**: Medium | **Effort**: Medium

Verify REST layer behavior independently.

**Devin Prompt:**
> Add `@WebMvcTest` controller tests for all REST controllers. For each controller, test: (1) successful request/response mapping, (2) validation error responses (after Bean Validation is added), (3) proper HTTP status codes for different error scenarios, (4) pagination parameter handling. Use MockMvc with `@MockBean` for service dependencies. Add tests for: `FundTransferController`, `UtilityPaymentController`, `UserController` (user-service), `AccountController`, `TransactionController`, `UserController` (core-banking).

---

### 3.9 Implement Notification Service (Planned Feature)

**Priority**: Low | **Effort**: Large

Complete the originally planned notification architecture.

**Devin Prompt:**
> Create a new `internet-banking-notification-service` module. Add RabbitMQ (`spring-boot-starter-amqp`) to `fund-transfer-service` and `utility-payment-service` to publish notification events after successful transactions. The notification service should: consume messages from a `banking.notifications` queue, support email notifications (via Spring Mail), and log notifications. Add a RabbitMQ container to `docker-compose.yml`. Define message DTOs with fields: `userId`, `transactionId`, `type` (FUND_TRANSFER/UTILITY_PAYMENT), `amount`, `timestamp`.

---

### 3.10 Add Multi-Module Gradle Build (Gap 1.3)

**Priority**: Low | **Effort**: Medium

Unify the build system for easier dependency management.

**Devin Prompt:**
> Create a root `settings.gradle` and `build.gradle` for the project. Include all service modules: `core-banking-service`, `internet-banking-api-gateway`, `internet-banking-config-server`, `internet-banking-service-registry`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, `internet-banking-utility-payment-service`, and `internet-banking-common`. Move shared dependency versions (Spring Boot, Spring Cloud, Lombok, MySQL connector) to the root `build.gradle` using `subprojects { }` block. Ensure each module can still be built independently.

---

## Summary Timeline

| Phase | Duration | Items | Key Outcomes |
|-------|----------|-------|-------------|
| Phase 1 | 1–2 weeks | 8 items | Security hardened, error handling fixed, basic resilience |
| Phase 2 | 3–6 weeks | 10 items | Reliable transactions, test coverage, structured observability |
| Phase 3 | 6+ weeks | 10 items | Production-grade monitoring, contracts, full documentation |

## Dependency Graph

```
Phase 1.2 (Validation) ──→ Phase 2.5 (Unit Tests) ──→ Phase 3.8 (Controller Tests)
Phase 1.1 (Error Fix) ──→ Phase 1.3 (Status Codes) ──→ Phase 2.7 (Pagination)
Phase 2.1 (Circuit Breakers) ──→ Phase 2.2 (Retry) ──→ Phase 3.4 (Bulkhead)
Phase 2.9 (Shared Module) ──→ Phase 3.10 (Multi-Module Build)
Phase 2.3 (Idempotency) ──→ Phase 2.4 (Saga Pattern)
```
