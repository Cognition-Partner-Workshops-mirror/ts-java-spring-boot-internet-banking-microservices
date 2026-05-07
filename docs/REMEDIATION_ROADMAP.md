# Remediation Roadmap

This document organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a prioritized, phased remediation plan. Each item includes a sample Devin prompt to execute the work.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact improvements that require small effort. Focus on security, correctness, and preventing data leaks.

### 1.1 Fix Exception Information Leak (EH-2)

**Gap:** Generic exception handler exposes stack traces to API consumers.
**Severity:** Critical | **Effort:** Small

Replace the generic exception handler in all services to return a safe error message.

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, update the `GlobalExceptionHandler` class in ALL services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service) so that the `handleException(Exception e)` method returns a generic error response like `ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred. Please try again later.")` with HTTP 500 status instead of exposing the exception details. Keep logging the full exception at ERROR level internally.

---

### 1.2 Fix HTTP Status Codes (EH-1)

**Gap:** All errors return HTTP 400 regardless of the actual error type.
**Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, update the `GlobalExceptionHandler` in ALL services to return appropriate HTTP status codes: `EntityNotFoundException` should return 404 Not Found, `InsufficientFundsException` should return 422 Unprocessable Entity, `UserAlreadyRegisteredException` and other validation errors should return 409 Conflict or 400 Bad Request as appropriate, and the generic `Exception` handler should return 500 Internal Server Error. Use the existing `ErrorResponse` structure for all responses.

---

### 1.3 Add Input Validation (SE-1)

**Gap:** No Bean Validation on any request DTO.
**Severity:** Critical | **Effort:** Small-Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta Bean Validation annotations to all request DTOs: `FundTransferRequest` (fromAccount @NotBlank, toAccount @NotBlank, amount @NotNull @Positive), `UtilityPaymentRequest` (providerId @NotNull, amount @NotNull @Positive, referenceNumber @NotBlank, account @NotBlank), and `User` registration DTO (email @NotBlank @Email, password @NotBlank @Size(min=8), identification @NotBlank). Add `@Valid` to all controller method parameters that accept request bodies. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Add `spring-boot-starter-validation` dependency to each service's build.gradle.

---

### 1.4 Remove Hardcoded Credentials from Source (SE-2)

**Gap:** Database passwords and Keycloak credentials committed to Git.
**Severity:** Critical | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, replace all hardcoded credentials in docker-compose files and SQL scripts with environment variable references. In `docker-compose.yml`, use `${MYSQL_ROOT_PASSWORD}`, `${KC_DB_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}` with a `.env.example` file documenting required variables. In `mysql/privileges.sql`, parameterize the DB user password. Add a `.env.example` file listing all required environment variables with placeholder values. Add `.env` to `.gitignore`. Update the README with instructions to copy `.env.example` to `.env` and fill in values.

---

### 1.5 Stop Logging Sensitive Data (OB-2)

**Gap:** Controllers log full request objects including passwords.
**Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, audit all `log.info` statements in controllers and services. Remove or redact any logging of full request objects that may contain sensitive data. Specifically: in `internet-banking-user-service` UserController, do NOT log the full User object (contains password). In all controllers, log only non-sensitive identifiers (e.g., account numbers, user IDs) rather than full `.toString()`. Add `@ToString.Exclude` on the `password` field in the User DTO.

---

### 1.6 Add Feign Timeout Configuration (RE-3)

**Gap:** No timeouts on inter-service HTTP calls.
**Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add Feign client timeout configuration to `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. In each service's `application.yml`, add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. This sets a 5-second connection timeout and 10-second read timeout for all Feign clients.

---

### 1.7 Fix SpringDoc Dependency (AD-4)

**Gap:** Business services use webflux-ui dependency but are servlet-based.
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, in the build.gradle of `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. The API Gateway should keep the webflux version as it uses Spring WebFlux. Verify the Swagger UI is accessible at `/swagger-ui.html` for each service.

---

### 1.8 Expose Actuator Endpoints Selectively (SE-5)

**Gap:** All actuator endpoints are publicly accessible.
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, configure actuator endpoint exposure for all services. In the externalized configuration (or application.yml), add `management.endpoints.web.exposure.include: health,info,prometheus` to only expose safe endpoints. In the API Gateway's `SecurityConfiguration`, restrict actuator access to only `/actuator/health` being fully public; other actuator endpoints should require authentication.

---

## Phase 2: Important (2-4 weeks)

Structural improvements that significantly improve reliability, maintainability, and developer experience.

### 2.1 Add Circuit Breakers (RE-1)

**Gap:** No circuit breaker pattern on inter-service calls.
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breaker support to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to both services' build.gradle. Configure a circuit breaker with: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. Add `@CircuitBreaker` annotations on the Feign client methods with a fallback that returns an appropriate error response indicating the core banking service is temporarily unavailable. Add retry configuration: max-attempts=3, wait-duration=1s for transient failures.

---

### 2.2 Add Feign Error Decoder to All Services (EH-4)

**Gap:** Feign client failures are unhandled in most services.
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, create a shared `CustomFeignErrorDecoder` implementation for `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` (similar to the one in user-service). The decoder should: parse the ErrorResponse body from the downstream service, map HTTP 404 to `EntityNotFoundException`, map HTTP 422 to a `BusinessException`, map HTTP 5xx to a `ServiceUnavailableException`, and include the original error message. Register the decoder in each service's `CustomFeignClientConfiguration`. Add appropriate exception handlers in each service's `GlobalExceptionHandler`.

---

### 2.3 Add Unit Tests for All Services (TE-1)

**Gap:** 4 of 6 services have zero meaningful tests.
**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit tests for: (1) `internet-banking-fund-transfer-service`: test `FundTransferService.fundTransfer()` success path, failure when core banking returns error, and `readAllTransfers()`. (2) `internet-banking-utility-payment-service`: test `UtilityPaymentService.utilPayment()` success and failure paths, and `readPayments()`. (3) `internet-banking-user-service`: test `UserService.createUser()` for all branches (user already exists, invalid email, invalid identification, success), `readUsers()`, `readUser()`, and `updateUser()`. Mock all Feign clients and Keycloak interactions. Use Mockito and JUnit 5. Aim for >80% line coverage on service classes.

---

### 2.4 Add CI Pipeline (TE-6)

**Gap:** No automated build/test pipeline.
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, create a GitHub Actions workflow at `.github/workflows/ci.yml` that: (1) triggers on push to main and pull requests, (2) uses Java 21, (3) runs `./gradlew build` for each service in parallel using a matrix strategy, (4) uploads test reports as artifacts, (5) fails the build if any tests fail. Use `actions/setup-java@v4` with Temurin distribution. Cache Gradle dependencies using `actions/cache`. Add a badge to the README showing build status.

---

### 2.5 Add Idempotency to Fund Transfers (RE-5)

**Gap:** Fund transfers are not idempotent; retries can cause double-debit.
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add idempotency key support to the fund transfer flow. (1) Add an optional `idempotencyKey` field to `FundTransferRequest`. (2) In `FundTransferService.fundTransfer()`, before creating a new entity, check if a fund transfer with the same idempotency key already exists; if so, return the existing response. (3) Add a unique constraint on `idempotency_key` column in the `fund_transfer` table. (4) If no key is provided by the client, generate one from a hash of (fromAccount + toAccount + amount + timestamp rounded to minute) to prevent exact duplicates within a time window. Add the same pattern to the utility payment service.

---

### 2.6 Fix Balance Race Condition (RE-8)

**Gap:** Concurrent fund transfers can overdraw accounts due to lack of pessimistic locking.
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, in `core-banking-service`, fix the race condition in `TransactionService.internalFundTransfer()`. Add a `@Lock(LockModeType.PESSIMISTIC_WRITE)` annotation to a new repository method `findByNumberForUpdate(String number)` in `BankAccountRepository`. Replace the `findByNumber()` calls in `internalFundTransfer()` and `utilPayment()` with this locking query. This ensures that concurrent transfers on the same account are serialized at the database level. Also add `@Transactional` to `utilPayment()` method if not already present (it is via class-level annotation). Add a test that verifies concurrent transfers don't overdraw.

---

### 2.7 Add Downstream Service Authentication (SE-3)

**Gap:** Business services accept requests without any auth validation.
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add security to downstream services so they cannot be accessed directly bypassing the API Gateway. For `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`: (1) Add `spring-boot-starter-security` dependency. (2) Configure a simple security filter that validates the presence of the `X-Auth-Id` header (set by the API Gateway) for all endpoints except actuator health. Requests without this header should receive HTTP 403. (3) Alternatively, implement a shared API key that the gateway includes and downstream services validate. This prevents direct access to internal services.

---

### 2.8 Extract Shared Library (CO-1)

**Gap:** Common code duplicated across all services.
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, create a new module `internet-banking-common` with a `build.gradle` that publishes a JAR (no Spring Boot plugin, just a plain library). Move the following duplicated classes into it: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` (as a base class), `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`. Update all services' `build.gradle` to depend on this common module via `implementation project(':internet-banking-common')`. Create a root `settings.gradle` that includes all modules. Verify all services still compile.

---

### 2.9 Add Saga/Compensation for Distributed Transactions (RE-6)

**Gap:** No compensation when Feign call fails after local record is created.
**Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, implement a simple saga pattern for the fund transfer and utility payment flows. In `FundTransferService.fundTransfer()`: wrap the Feign call in a try-catch. If the Feign call fails, update the local `FundTransferEntity` status to `FAILED` with an error message, and return a failure response to the client. Same for `UtilityPaymentService`. Add a scheduled job that periodically checks for records stuck in `PENDING`/`PROCESSING` status for more than 5 minutes and marks them as `FAILED` (or retries them). Log all failures at WARN level for operational visibility.

---

## Phase 3: Polish (4-8 weeks)

Improvements for production readiness, developer experience, and operational excellence.

### 3.1 Add Structured JSON Logging (OB-1)

**Gap:** Unstructured text logging not suitable for aggregation.
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add structured JSON logging to all services. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service. Create a shared `logback-spring.xml` configuration that outputs JSON in production profile and plain text in development. Include MDC fields for traceId, spanId, service name, and authenticated user ID. Configure the `AppAuthUserFilter` to add the auth ID to MDC for all log statements within a request.

---

### 3.2 Add Integration Tests with Testcontainers (TE-2)

**Gap:** No integration tests verifying real DB or service interactions.
**Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests using Testcontainers for `core-banking-service`. Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to test dependencies. Create integration tests that: (1) start a MySQL container, (2) run Flyway migrations, (3) test the full flow of creating an account, performing a fund transfer, and verifying balances. Add a similar integration test for the user registration flow in `internet-banking-user-service` using a Keycloak testcontainer.

---

### 3.3 Add Contract Tests (TE-3)

**Gap:** No API contract verification between services.
**Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests between `internet-banking-fund-transfer-service` (consumer) and `core-banking-service` (producer). In core-banking-service, add the Spring Cloud Contract Verifier plugin and create contract definitions for `/api/v1/transaction/fund-transfer` and `/api/v1/account/bank-account/{account_number}`. In fund-transfer-service, add the Stub Runner dependency and write consumer-side tests that validate the Feign client against the generated stubs. This ensures API changes in core-banking don't silently break consumers.

---

### 3.4 Add Role-Based Access Control (SE-4)

**Gap:** No differentiation between admin and regular user permissions.
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, implement role-based access control. (1) In the Keycloak realm configuration, define roles: `ROLE_ADMIN` and `ROLE_USER`. (2) In the API Gateway SecurityConfiguration, add role extraction from the JWT token. (3) Restrict `PATCH /user/api/v1/bank-users/update/{id}` to `ROLE_ADMIN` only. (4) Restrict `GET /user/api/v1/bank-users` (list all users) to `ROLE_ADMIN`. (5) Allow `ROLE_USER` access to fund transfers and utility payments. Update the Keycloak realm export to include these roles and assign them to test users.

---

### 3.5 Add Prometheus Metrics and Grafana Dashboards (OB-4, OB-6)

**Gap:** No metrics collection or monitoring dashboards.
**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics collection and Grafana dashboards. (1) Add `io.micrometer:micrometer-registry-prometheus` to all services. (2) Configure `management.endpoints.web.exposure.include: health,prometheus` and `management.metrics.tags.application: ${spring.application.name}`. (3) Add custom metrics: counter for fund transfers (success/failure), histogram for transfer amounts, gauge for active connections. (4) Add a `docker-compose-monitoring.yml` with Prometheus and Grafana containers. (5) Create a Grafana dashboard JSON showing request rate, error rate, latency percentiles, and JVM metrics per service.

---

### 3.6 Add Rate Limiting (SE-7)

**Gap:** No rate limiting on API endpoints.
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting to the API Gateway. Use Spring Cloud Gateway's built-in `RequestRateLimiter` filter with Redis (add Redis to docker-compose). Configure: (1) Global rate limit of 100 requests/second per IP. (2) Stricter limit of 10 requests/minute on `/user/api/v1/bank-users/register` to prevent registration abuse. (3) Limit of 30 requests/minute on fund transfer and utility payment POST endpoints. Return HTTP 429 Too Many Requests with a `Retry-After` header when limits are exceeded.

---

### 3.7 Add Database Connection Pool Tuning (RE-7)

**Gap:** No HikariCP configuration; using defaults.
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add HikariCP connection pool configuration to all database-connected services (core-banking-service, fund-transfer-service, user-service, utility-payment-service). In the externalized configuration, add: `spring.datasource.hikari.maximum-pool-size: 20`, `spring.datasource.hikari.minimum-idle: 5`, `spring.datasource.hikari.idle-timeout: 300000`, `spring.datasource.hikari.connection-timeout: 20000`, `spring.datasource.hikari.leak-detection-threshold: 60000`. These values are suitable for a moderate-load banking application.

---

### 3.8 Add Multi-Project Gradle Build (CO-2)

**Gap:** Each service has independent Gradle wrapper and build.
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, create a root-level `settings.gradle` that includes all service modules. Create a root `build.gradle` with shared configuration (Java version, repositories, common dependencies in a `subprojects` block). Remove individual `gradle/wrapper` directories from each service and keep only the root-level wrapper. Add a root-level `gradlew` that can build all services with `./gradlew build`. Verify each service still builds correctly as a subproject.

---

### 3.9 Standardize API Response Envelope (AD-2)

**Gap:** Inconsistent response wrapping; pagination without metadata.
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, create a standardized API response wrapper. (1) In the common module, create `ApiResponse<T>` with fields: `data` (T), `message` (String), `timestamp` (Instant). (2) Create `PagedResponse<T>` extending ApiResponse with: `page` (int), `size` (int), `totalElements` (long), `totalPages` (int). (3) Update all controllers to wrap responses in `ApiResponse` for single items and `PagedResponse` for paginated results. (4) Update error responses to use a consistent `ApiErrorResponse` with: `code`, `message`, `timestamp`, `path`, `errors[]` (for validation).

---

### 3.10 Add Dead Letter Queue and Transaction Recovery (RE-9)

**Gap:** Failed transactions are never retried or reconciled.
**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, implement a transaction recovery mechanism. (1) Add Spring Scheduling (`@EnableScheduling`) to fund-transfer-service and utility-payment-service. (2) Create a `TransactionRecoveryJob` that runs every 5 minutes, finds records with status `PENDING` or `PROCESSING` older than 10 minutes, and attempts to retry the Feign call to core-banking-service. (3) After 3 retry attempts, mark the record as `FAILED` and log at ERROR level. (4) Add a GET endpoint `/api/v1/transfer/failed` to list failed transfers for manual review. (5) Add RabbitMQ integration to publish failed transaction events for alerting (this implements the notification service mentioned in the architecture).

---

## Summary Timeline

| Phase | Duration | Items | Focus |
|-------|----------|-------|-------|
| **Phase 1** | 1-2 weeks | 8 items | Security fixes, error handling, basic resilience |
| **Phase 2** | 2-4 weeks | 9 items | Testing, circuit breakers, distributed transactions, shared code |
| **Phase 3** | 4-8 weeks | 10 items | Observability, monitoring, rate limiting, API standards |

## Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │   Phase 1         │   Phase 2         │
    │   (Do First)      │   (Do Next)       │
    │                   │                   │
    │ • Fix info leak   │ • Circuit breakers│
    │ • Input validation│ • Idempotency     │
    │ • Remove creds    │ • Race condition  │
    │ • Fix status codes│ • Unit tests      │
    │ • Add timeouts    │ • CI pipeline     │
    │                   │ • Saga pattern    │
LOW ├───────────────────┼───────────────────┤ HIGH
EFFORT                  │                   EFFORT
    │                   │                   │
    │   Quick Polish    │   Phase 3         │
    │                   │   (Do Later)      │
    │ • Fix SpringDoc   │ • Testcontainers  │
    │ • Actuator config │ • Contract tests  │
    │ • Gradle multi    │ • RBAC            │
    │ • Connection pool │ • Monitoring      │
    │                   │ • Rate limiting   │
    │                   │ • Response envelope│
    │                   │                   │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```
