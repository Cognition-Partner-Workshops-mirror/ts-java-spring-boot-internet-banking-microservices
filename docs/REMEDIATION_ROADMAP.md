# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, impact, and effort.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact, low-effort fixes that address critical security and stability risks.

### 1.1 Fix Stack Trace Leakage in Exception Handlers

**Gap Reference**: 2.1 (Critical, Small)

Replace the catch-all exception handler that exposes internal details to clients.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in all four services (`core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`) to replace the generic `@ExceptionHandler({Exception.class})` method. Instead of returning the raw exception string, return a structured `ErrorResponse` with a generic message like "An internal error occurred" and log the full exception server-side at ERROR level. Never expose stack traces or internal exception details to the client.

---

### 1.2 Add Input Validation to Request DTOs

**Gap Reference**: 4.2 (Critical, Small-Medium)

Add Jakarta Bean Validation annotations to prevent malicious or malformed input.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Jakarta Bean Validation annotations to all request DTOs:
> - `FundTransferRequest` (both core-banking and fund-transfer versions): `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`
> - `UtilityPaymentRequest` (both core-banking and utility-payment versions): `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`
> - `User` DTO in user-service: `@NotBlank @Email` on `email`, `@NotBlank` on `password` and `identification`
>
> Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`.

---

### 1.3 Remove Hardcoded Credentials from Source Code

**Gap Reference**: 4.1 (Critical, Small)

Move all secrets to environment variables or Docker secrets.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, refactor `docker-compose/docker-compose.yml` and `docker-compose/mysql/Dockerfile` to use environment variables for all credentials instead of hardcoded values. Replace hardcoded MySQL password, Keycloak admin password, and DB user password with `${VARIABLE:-default}` syntax referencing a `.env` file. Create a `.env.example` file documenting required variables. Add `.env` to `.gitignore`. Update `docker-compose/mysql/privileges.sql` to use an environment variable for the DB user password.

---

### 1.4 Fix HTTP Status Code Mapping

**Gap Reference**: 2.2 (High, Small)

Return appropriate HTTP status codes for different error types.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update `GlobalExceptionHandler` in all services to return proper HTTP status codes:
> - `EntityNotFoundException` → HTTP 404
> - `InsufficientFundsException` → HTTP 422
> - `UserAlreadyRegisteredException` → HTTP 409
> - `InvalidEmailException` → HTTP 400
> - `InvalidBankingUserException` → HTTP 404
> - Generic `SimpleBankingGlobalException` → HTTP 400
> - Unhandled `Exception` → HTTP 500
>
> Update error responses to include the HTTP status code in the response body.

---

### 1.5 Add Feign Client Timeout Configuration

**Gap Reference**: 7.3 (High, Small)

Prevent thread starvation from hung downstream services.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Feign client timeout configuration to the `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Configure via application properties: `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`. Also configure specific timeouts for `core-banking-service` Feign clients with `connect-timeout=3000` and `read-timeout=8000`.

---

### 1.6 Add Feign Retry Configuration

**Gap Reference**: 7.2 (High, Small)

Handle transient network failures gracefully.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a Spring Retry configuration for Feign clients in `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Add `spring-retry` dependency. Configure a `Retryer` bean with max 3 attempts, 1-second initial interval, and 2-second max interval. Only retry on `IOException` and `FeignException.ServiceUnavailable`, never on POST methods that aren't idempotent (fund-transfer and utility-payment POST endpoints should NOT retry).

---

### 1.7 Stop Logging Sensitive Data

**Gap Reference**: 4.6 / 6.4 (Medium, Small)

Prevent PII and financial data from appearing in logs.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, audit all `log.info()` calls in controller classes across all services. Replace `request.toString()` logging with safe alternatives that only log non-sensitive fields (e.g., transaction type, account number last 4 digits). In the `User` DTO in user-service, override `toString()` to exclude the `password` field. Add a `@JsonIgnore` annotation on the password getter to prevent accidental serialization in responses.

---

### 1.8 Fix OpenAPI/Swagger Dependency

**Gap Reference**: 5.6 (Medium, Small)

Correct the Swagger UI dependency for web-mvc services.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` in the `build.gradle` of `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. The webflux variant is incorrect for Spring MVC services. Also add `ResponseEntity<SpecificType>` generic type parameters to all controller methods for accurate OpenAPI schema generation.

---

## Phase 2: Important Improvements (3-6 Weeks)

Structural improvements that address high-severity gaps requiring moderate effort.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap Reference**: 7.1 (Critical, Medium)

Prevent cascading failures across services.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Resilience4j circuit breaker support to all services that make Feign calls (`internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`):
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency
> 2. Configure circuit breaker instances for each Feign client with: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10, minimum-number-of-calls=5
> 3. Add `@CircuitBreaker` annotation to Feign client calls in service classes with fallback methods
> 4. Fallback for fund-transfer: return a response indicating the transfer is queued for retry
> 5. Fallback for utility-payment: return a response indicating payment processing is delayed
> 6. Fallback for user-service core-banking calls: throw a clear "Core banking unavailable" exception
> 7. Add Actuator endpoint to expose circuit breaker state

---

### 2.2 Implement Role-Based Authorization

**Gap Reference**: 4.5 (High, Medium)

Ensure users can only access their own resources.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement role-based access control:
> 1. Define roles in Keycloak realm: `ROLE_USER`, `ROLE_ADMIN`
> 2. Update API Gateway `SecurityConfiguration` to extract roles from JWT claims and map them to Spring Security authorities
> 3. In `internet-banking-user-service`: restrict `PATCH /update/{id}` to `ROLE_ADMIN` only; restrict `GET /` (list all users) to `ROLE_ADMIN`
> 4. In `internet-banking-fund-transfer-service`: validate that the `X-Auth-Id` matches the account owner before allowing transfers
> 5. In `internet-banking-utility-payment-service`: validate that the `X-Auth-Id` matches the account owner before allowing payments
> 6. Add ownership validation by calling core-banking to verify account belongs to authenticated user

---

### 2.3 Create Shared Library Module

**Gap Reference**: 1.2, 1.6 (High, Large)

Eliminate code duplication with a shared module.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a shared library module:
> 1. Create a new directory `internet-banking-common` with its own `build.gradle` (plain Java library, no Spring Boot plugin)
> 2. Move common classes into it: `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `EntityNotFoundException`
> 3. Create a root `settings.gradle` that includes all services as subprojects
> 4. Add `implementation project(':internet-banking-common')` to each service's `build.gradle`
> 5. Remove duplicated classes from individual services
> 6. Ensure all services still compile and tests pass

---

### 2.4 Add CI/CD Pipeline with GitHub Actions

**Gap Reference**: 3.6 (High, Medium)

Automate testing and build verification.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a GitHub Actions CI pipeline at `.github/workflows/ci.yml` that:
> 1. Triggers on push to `main` and on all pull requests
> 2. Uses Java 21 (Eclipse Temurin)
> 3. Caches Gradle dependencies
> 4. Runs `./gradlew build` for each service (in parallel using a matrix strategy)
> 5. Runs `./gradlew test` for each service and uploads test reports as artifacts
> 6. Fails the pipeline if any test fails
> 7. Adds a step to check for dependency vulnerabilities using the OWASP dependency-check Gradle plugin
> 8. Include a Docker build step that verifies all Dockerfiles build successfully (without pushing)

---

### 2.5 Expand Unit Test Coverage

**Gap Reference**: 3.1 (High, Large)

Ensure business logic is properly tested.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests to services that currently lack them:
>
> For `internet-banking-user-service`:
> - Test `UserService.createUser()`: happy path, duplicate email, user not found in core banking, email mismatch, Keycloak creation failure
> - Test `UserService.updateUser()`: approve flow (enables Keycloak user), user not found
> - Test `KeycloakUserService`: mock Keycloak admin client interactions
>
> For `internet-banking-fund-transfer-service`:
> - Test `FundTransferService.fundTransfer()`: happy path, Feign client failure, verify local entity status transitions
> - Test `FundTransferService.readAllTransfers()`: pagination behavior
>
> For `internet-banking-utility-payment-service`:
> - Test `UtilityPaymentService.utilPayment()`: happy path, Feign client failure, verify local entity status transitions
> - Test `UtilityPaymentService.readPayments()`: pagination behavior
>
> Use Mockito for mocking, JUnit 5 assertions. Target 80%+ line coverage for service classes.

---

### 2.6 Add Proper Feign Error Handling

**Gap Reference**: 2.4 (High, Medium)

Map downstream errors to meaningful client responses.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement proper Feign error decoding in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`:
> 1. Create a `CustomFeignErrorDecoder` class implementing `feign.codec.ErrorDecoder`
> 2. Map HTTP 404 responses from core-banking to `EntityNotFoundException`
> 3. Map HTTP 422 responses (insufficient funds) to a local `InsufficientFundsException`
> 4. Map HTTP 5xx responses to a `ServiceUnavailableException`
> 5. Preserve the original error message from the downstream response body
> 6. Register the decoder in `CustomFeignClientConfiguration`
> 7. Add corresponding handlers in `GlobalExceptionHandler` for these mapped exceptions

---

### 2.7 Add Structured JSON Logging

**Gap Reference**: 6.1 (Medium, Medium)

Enable log aggregation and analysis.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure structured JSON logging for all services:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service
> 2. Create a `logback-spring.xml` in each service's `src/main/resources/` that:
>    - Uses `LogstashEncoder` for the `docker` and `prod` profiles (JSON output)
>    - Uses default pattern encoder for `dev` profile (human-readable)
>    - Includes MDC fields: `traceId`, `spanId`, `service.name`
> 3. Add MDC context to the `AppAuthUserFilter` to include `userId` in all log lines
> 4. Ensure log output includes timestamp, level, logger, message, and trace context

---

### 2.8 Add Pagination Metadata to List Endpoints

**Gap Reference**: 5.4 (Medium, Small)

Provide clients with pagination context.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all list/paginated endpoints to return pagination metadata:
> 1. Create a generic `PageResponse<T>` class in the common module with fields: `content`, `totalElements`, `totalPages`, `currentPage`, `pageSize`
> 2. Update `FundTransferController.readFundTransfers()` to return `PageResponse<FundTransfer>`
> 3. Update `UtilityPaymentController.readPayments()` to return `PageResponse<UtilityPayment>`
> 4. Update `UserController.readUsers()` (user-service) to return `PageResponse<User>`
> 5. Update `UserController.readUsers()` (core-banking) to return `PageResponse<User>`
> 6. Include `Link` headers for HATEOAS-style navigation (optional)

---

## Phase 3: Polish & Hardening (6-12 Weeks)

Longer-term improvements for production-readiness and operational excellence.

### 3.1 Add Contract Tests Between Services

**Gap Reference**: 3.3 (Medium, Large)

Prevent breaking changes between service APIs.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement Spring Cloud Contract tests:
> 1. Add `spring-cloud-starter-contract-verifier` to `core-banking-service` (producer)
> 2. Define contracts in `src/test/resources/contracts/` for:
>    - `GET /api/v1/account/bank-account/{number}` → returns account with balance fields
>    - `POST /api/v1/transaction/fund-transfer` → returns transactionId on success, 422 on insufficient funds
>    - `POST /api/v1/transaction/util-payment` → returns transactionId on success
>    - `GET /api/v1/user/{identification}` → returns user with accounts
> 3. Generate stubs JAR from core-banking-service contracts
> 4. Add `spring-cloud-starter-contract-stub-runner` to consumer services (fund-transfer, utility-payment, user-service)
> 5. Write consumer-side tests that verify Feign clients work correctly against the generated stubs
> 6. Integrate contract verification into the CI pipeline

---

### 3.2 Add Integration Tests with Testcontainers

**Gap Reference**: 3.2 (High, Large)

Verify end-to-end behavior with real infrastructure.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration tests using Testcontainers:
> 1. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` dependencies
> 2. For `core-banking-service`: write an integration test that starts a MySQL container, runs Flyway migrations, and tests the full fund-transfer flow through the controller layer
> 3. For `internet-banking-user-service`: write an integration test with MySQL + Keycloak testcontainers that tests user registration end-to-end
> 4. Add a `@TestConfiguration` class that provides Testcontainers-based datasource
> 5. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate`
> 6. Ensure tests run in CI and add MySQL Testcontainer to the GitHub Actions workflow

---

### 3.3 Implement Bulkhead and Rate Limiting

**Gap Reference**: 7.5, 4.4 (Medium, Medium)

Protect against resource exhaustion and abuse.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add bulkhead isolation and rate limiting:
> 1. Add Resilience4j bulkhead configuration to all Feign-calling services: `max-concurrent-calls=25`, `max-wait-duration=0` (fail fast if full)
> 2. Add rate limiting at the API Gateway using Spring Cloud Gateway's `RequestRateLimiter` filter:
>    - Global: 100 requests/second per IP
>    - Fund transfer endpoint: 10 requests/second per user
>    - User registration: 5 requests/minute per IP
> 3. Add Redis dependency to API Gateway for the rate limiter token bucket store
> 4. Add Redis container to `docker-compose.yml`
> 5. Return HTTP 429 with `Retry-After` header when rate limited

---

### 3.4 Configure Database Connection Pooling

**Gap Reference**: 7.6 (High, Small)

Optimize database resource usage.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure HikariCP connection pool settings for all database-connected services. Add to each service's externalized configuration:
> ```yaml
> spring.datasource.hikari:
>   maximum-pool-size: 20
>   minimum-idle: 5
>   idle-timeout: 300000
>   connection-timeout: 20000
>   max-lifetime: 1200000
>   leak-detection-threshold: 60000
> ```
> Also add a custom health indicator that reports pool utilization metrics.

---

### 3.5 Add Custom Metrics and Dashboards

**Gap Reference**: 6.3, 6.5 (Medium, Medium)

Enable production monitoring and alerting.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom Micrometer metrics:
> 1. In `core-banking-service`: add counters for `fund.transfer.success`, `fund.transfer.failed`, `utility.payment.success`, `utility.payment.failed`; add timer for `fund.transfer.duration`
> 2. In all services: add a gauge for active Feign client requests
> 3. Add Prometheus configuration to `docker-compose.yml` with a `prometheus.yml` that scrapes all service `/actuator/prometheus` endpoints
> 4. Create a basic Grafana dashboard JSON that visualizes: request rate, error rate, p95 latency, circuit breaker state, connection pool utilization
> 5. Add Grafana container to docker-compose with the dashboard auto-provisioned
> 6. Document alerting thresholds: error rate > 5%, p95 > 2s, circuit breaker open

---

### 3.6 Implement Graceful Degradation for Keycloak

**Gap Reference**: 7.7 (Medium, Medium)

Allow partial system operation during Keycloak outages.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add graceful degradation for Keycloak dependency in `internet-banking-user-service`:
> 1. Add a circuit breaker around all Keycloak admin client calls in `KeycloakUserService`
> 2. When Keycloak is unavailable during user registration: save the user record with `status=PENDING_KEYCLOAK` and return a response indicating the account will be activated once Keycloak recovers
> 3. Add a scheduled task (`@Scheduled`) that periodically retries creating Keycloak users for records in `PENDING_KEYCLOAK` status
> 4. Fix the thread-safety issue in `KeycloakProperties.getInstance()` by using `synchronized` or `@Bean` scope
> 5. Add a health indicator that reports Keycloak connectivity status

---

### 3.7 Add API Versioning Strategy

**Gap Reference**: 5.2 (Low, Small)

Document and enforce API versioning.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, document and formalize the API versioning strategy:
> 1. Create `docs/API_VERSIONING.md` documenting that URI path versioning (`/api/v1/`) is the chosen approach
> 2. Add an `Accept-Version` header reader to the API Gateway that can route to different service versions in the future
> 3. Add deprecation headers middleware: when a `v1` endpoint has a `v2` replacement, automatically add `Sunset` and `Deprecation` headers
> 4. Ensure all OpenAPI specs include version metadata

---

### 3.8 Migrate All Services to Flyway

**Gap Reference**: Consistency improvement

Ensure all schema changes are version-controlled.

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Flyway migration support to services currently using Hibernate auto-DDL:
> 1. Add `org.flywaydb:flyway-core` and `org.flywaydb:flyway-mysql` dependencies to `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`
> 2. Generate initial migration scripts from current Hibernate-generated schemas (use `schema-generation` or capture from running DB)
> 3. Create `V1.0__initial_schema.sql` for each service based on their entity definitions
> 4. Set `spring.jpa.hibernate.ddl-auto=validate` (instead of auto-create) in all profiles except test
> 5. Ensure Flyway runs on startup and validates schema matches JPA entities
> 6. Update test profiles to use Flyway with H2-compatible SQL or keep `ddl-auto=create` for test speed

---

## Phase Summary

| Phase | Items | Timeline | Key Outcomes |
|-------|-------|----------|-------------|
| **Phase 1** | 8 items | 1-2 weeks | Critical security fixes, basic resilience, proper error handling |
| **Phase 2** | 8 items | 3-6 weeks | Circuit breakers, authorization, shared library, CI/CD, test coverage |
| **Phase 3** | 8 items | 6-12 weeks | Contract tests, integration tests, monitoring, production hardening |

## Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │  Phase 1          │  Phase 2          │
    │  (Do First)       │  (Do Next)        │
    │                   │                   │
    │  • Fix stack trace│  • Circuit breakers│
    │  • Input valid.   │  • Authorization  │
    │  • Remove creds   │  • Shared library │
    │  • HTTP status    │  • CI/CD          │
    │  • Timeouts       │  • Test coverage  │
LOW ├───────────────────┼───────────────────┤ HIGH
EFFORT│                 │                   │ EFFORT
    │                   │                   │
    │  Phase 1b         │  Phase 3          │
    │  (Quick Polish)   │  (Longer Term)    │
    │                   │                   │
    │  • Fix Swagger    │  • Contract tests │
    │  • Log sanitize   │  • Integration    │
    │  • Pagination     │  • Rate limiting  │
    │                   │  • Monitoring      │
    │                   │                   │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```
