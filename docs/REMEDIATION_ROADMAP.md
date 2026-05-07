# Remediation Roadmap

This roadmap prioritizes the gaps identified in `GAP_ANALYSIS.md` into three phases. Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort items that improve security, correctness, and developer experience immediately.

### 1.1 Fix HTTP Status Codes in Exception Handlers

**Gap:** 2.2 — All errors return HTTP 400
**Severity:** High | **Effort:** Small

Update `GlobalExceptionHandler` in all 4 services to return appropriate HTTP status codes: 404 for `EntityNotFoundException`, 409 for `UserAlreadyRegisteredException`, 422 for `InsufficientFundsException`, and 500 for unhandled exceptions.

**Devin Prompt:**
> Update the `GlobalExceptionHandler` class in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return proper HTTP status codes. `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `UserAlreadyRegisteredException` should return 409, `InvalidEmailException` should return 400, `InvalidBankingUserException` should return 400, and the generic `Exception` catch-all should return 500 with a safe error message (no stack trace leakage). Also make the `ErrorResponse` construction consistent across all services using the builder pattern. Run the existing tests to verify nothing breaks.

---

### 1.2 Add Input Validation to All Request DTOs

**Gap:** 4.2 — No input validation
**Severity:** Critical | **Effort:** Medium

Add Bean Validation annotations to all request DTOs and `@Valid` on controller parameters.

**Devin Prompt:**
> Add Jakarta Bean Validation to all request DTOs and controllers across all services. Specifically: (1) In core-banking-service, add `@NotBlank` to `fromAccount`, `toAccount` in `FundTransferRequest`, `@NotNull @Positive` to `amount`, `@NotNull` to `providerId`, `@NotBlank` to `account` and `referenceNumber` in `UtilityPaymentRequest`. (2) In internet-banking-user-service, add `@NotBlank @Email` to `email`, `@NotBlank` to `identification` and `password` in the `User` DTO. (3) In internet-banking-fund-transfer-service, add the same constraints to its `FundTransferRequest`. (4) In internet-banking-utility-payment-service, add constraints to its `UtilityPaymentRequest`. Add `@Valid` annotations to all `@RequestBody` parameters in controllers. Add `spring-boot-starter-validation` dependency to each service's `build.gradle` if not already present. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Run all tests afterward.

---

### 1.3 Fix Password Exposure in User Registration

**Gap:** 4.3 — Password handling issues
**Severity:** High | **Effort:** Small

Prevent the password field from being logged or serialized in responses.

**Devin Prompt:**
> In internet-banking-user-service, fix password handling: (1) Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field in `com.javatodev.finance.model.dto.User`. (2) In `UserController.createUser()`, change the log statement to not log the full request object — log only the email instead. (3) Add a `@ToString.Exclude` Lombok annotation to the `password` field to prevent it appearing in `toString()` output.

---

### 1.4 Externalize Hardcoded Credentials

**Gap:** 4.1 — Hardcoded credentials in source code
**Severity:** Critical | **Effort:** Small

Replace hardcoded passwords in docker-compose and Dockerfiles with environment variables.

**Devin Prompt:**
> Externalize all hardcoded credentials in the docker-compose setup. (1) In `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml`, replace all hardcoded passwords with `${VARIABLE:-default}` syntax using a `.env` file. Create a `docker-compose/.env.example` file documenting all required variables: `MYSQL_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KC_DB_PASSWORD`, `POSTGRES_PASSWORD`. (2) In `docker-compose/mysql/Dockerfile`, remove the hardcoded `ENV MYSQL_ROOT_PASSWORD` line and pass it via docker-compose instead. (3) Add `.env` to `.gitignore`. (4) Update `README.md` to document the new `.env` setup requirement.

---

### 1.5 Add Feign Client Timeouts

**Gap:** 7.3 — No timeout configuration
**Severity:** High | **Effort:** Small

Configure connection and read timeouts for all Feign clients.

**Devin Prompt:**
> Add timeout configuration for all Feign clients. Since the configuration is managed via Spring Cloud Config Server (external Git repo), add the timeout config to each service's local `application.yml` as a fallback. For `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`, add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add `spring.cloud.openfeign.client.config.core-banking-service.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 15000` for the specific core-banking client. Run all tests afterward.

---

### 1.6 Add Retry Policies for Feign Clients

**Gap:** 7.2 — No retry policies
**Severity:** High | **Effort:** Small

Add Spring Retry with basic retry configuration for transient failures.

**Devin Prompt:**
> Add retry support for Feign clients in internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. (1) Add `spring-retry` and `spring-boot-starter-aop` dependencies to each service's `build.gradle`. (2) Add `@EnableRetry` to each service's main application class. (3) Configure Feign retry in each `application.yml`: `spring.cloud.openfeign.client.config.default.retryer: feign.Retryer.Default` with max 3 attempts and 1-second initial backoff. Note: do NOT add retry to POST endpoints that perform fund transfers or payments — only to GET (read) operations. Create a custom `Retryer` bean that only retries on `IOException` and `RetryableException`, not on business exceptions.

---

### 1.7 Secure Actuator Endpoints

**Gap:** 6.2 — Actuator endpoints fully exposed
**Severity:** Medium | **Effort:** Small

Restrict which actuator endpoints are publicly accessible.

**Devin Prompt:**
> Secure the actuator endpoints. In the API gateway's `SecurityConfiguration`, change the actuator path matchers to only allow `/actuator/health` and `/actuator/info` without authentication. Block all other actuator endpoints (like `/actuator/env`, `/actuator/configprops`, `/actuator/beans`) behind authentication. Add the following to each service's `application.yml`: `management.endpoints.web.exposure.include: health,info,prometheus` and `management.endpoint.health.show-details: when-authorized`.

---

### 1.8 Fix Logging Issues

**Gap:** 6.1 — Inconsistent logging
**Severity:** Medium | **Effort:** Small

Fix string concatenation in log statements and prevent sensitive data logging.

**Devin Prompt:**
> Fix logging issues across all services: (1) In `FundTransferService.fundTransfer()`, change `log.info("Sending fund transfer request {}" + request.toString())` to `log.info("Sending fund transfer request {}", request)` (use parameterized logging). (2) In `UserController.createUser()`, change the log to only log non-sensitive fields: `log.info("Creating user with email={}", request.getEmail())`. (3) In `UtilityPaymentService.utilPayment()`, ensure the log doesn't expose full account numbers — mask them. (4) Add `@ToString.Exclude` to any sensitive fields in request/response DTOs that might be logged.

---

### 1.9 Fix SimpleBankingGlobalException Message Shadowing

**Gap:** 2.4 — Exception message field shadowing
**Severity:** Medium | **Effort:** Small

Fix the `SimpleBankingGlobalException` class so `getMessage()` works correctly.

**Devin Prompt:**
> Fix `SimpleBankingGlobalException` in all 4 services. The class declares its own `message` field that shadows `Throwable.message`. Update the two-arg constructor `SimpleBankingGlobalException(String code, String message)` to call `super(message)` and remove the local `message` field — use `getMessage()` from `Throwable` instead. Keep the `code` field. Update the `@Getter`/`@Setter` annotations accordingly. Verify the `GlobalExceptionHandler` still works correctly by running existing tests.

---

### 1.10 Fix Raw Type ResponseEntity Warnings

**Gap:** 2.3 — Raw type ResponseEntity
**Severity:** Medium | **Effort:** Small

Add type parameters to all `ResponseEntity` return types.

**Devin Prompt:**
> Add proper generic type parameters to all `ResponseEntity` return types in all controllers and exception handlers across all services. For example, change `public ResponseEntity readUser(...)` to `public ResponseEntity<User> readUser(...)`, and in `GlobalExceptionHandler`, change `protected ResponseEntity handleGlobalException(...)` to `protected ResponseEntity<ErrorResponse> handleGlobalException(...)`. For list endpoints, use `ResponseEntity<List<T>>`. For the catch-all Exception handler, use `ResponseEntity<ErrorResponse>` and return an `ErrorResponse` object instead of a raw string.

---

## Phase 2: Important (2-4 weeks)

Structural improvements that address resilience, testing, and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap:** 7.1 — No circuit breakers
**Severity:** Critical | **Effort:** Medium

Add circuit breaker patterns to prevent cascading failures.

**Devin Prompt:**
> Add Resilience4j circuit breakers to all Feign clients. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to the `build.gradle` of internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. (2) Enable circuit breakers for Feign: set `spring.cloud.openfeign.circuitbreaker.enabled: true` in each service's `application.yml`. (3) Create fallback classes for each Feign client: `BankingCoreFeignClientFallback` that returns appropriate error responses or throws a custom `ServiceUnavailableException`. (4) Configure circuit breaker parameters in `application.yml`: sliding window size of 10, failure rate threshold of 50%, wait duration in open state of 30 seconds, permitted calls in half-open state of 3. (5) Add a `@ExceptionHandler` for `ServiceUnavailableException` returning HTTP 503. Run all tests afterward.

---

### 2.2 Add Unit Tests for All Services

**Gap:** 3.1 — Minimal test coverage
**Severity:** Critical | **Effort:** Large

Write comprehensive unit tests for all service classes.

**Devin Prompt:**
> Write comprehensive unit tests for internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For each service: (1) Create service-layer tests using Mockito to mock repositories and Feign clients. Test both happy paths and error cases (entity not found, validation failures, downstream service errors). (2) Create controller-layer tests using `@WebMvcTest` and `MockMvc` to test request/response serialization, validation, and error handling. (3) For internet-banking-user-service, mock the `KeycloakUserService` and `BankingCoreRestClient`. Test `createUser` (success, duplicate email, invalid identification, email mismatch), `readUsers`, `readUser`, and `updateUser`. (4) For internet-banking-fund-transfer-service, mock `BankingCoreFeignClient`. Test `fundTransfer` (success, downstream error) and `readAllTransfers`. (5) For internet-banking-utility-payment-service, mock `BankingCoreRestClient`. Test `utilPayment` (success, downstream error) and `readPayments`. Use H2 for repository tests. Target >80% line coverage.

---

### 2.3 Extract Shared Library

**Gap:** 1.2 — Duplicated code across services
**Severity:** High | **Effort:** Medium

Create a shared library module for common code.

**Devin Prompt:**
> Create a shared library module called `internet-banking-common` that consolidates duplicated code. (1) Create a new Gradle module at the root with `build.gradle` and `src/main/java/com/javatodev/finance/common/`. (2) Move the following classes into the shared module: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`. (3) Create a root `settings.gradle` that includes all modules. (4) Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. (5) Remove the duplicate classes from each service. (6) Run all existing tests to verify nothing is broken.

---

### 2.4 Add Idempotency Protection for Financial Transactions

**Gap:** 7.5 — No idempotency protection
**Severity:** High | **Effort:** Medium

Add idempotency key support for fund transfer and payment endpoints.

**Devin Prompt:**
> Add idempotency protection for financial transaction endpoints. (1) Create an `IdempotencyKey` entity and repository in both internet-banking-fund-transfer-service and internet-banking-utility-payment-service, storing the key, response, and expiry timestamp. (2) Add a servlet filter that checks for an `Idempotency-Key` HTTP header on POST requests. If the key exists in the database, return the cached response. If not, proceed with the request and store the result. (3) Add the `Idempotency-Key` header to the Swagger documentation. (4) Set a 24-hour TTL for idempotency records. (5) Add a scheduled task to clean up expired records. (6) Write unit tests for the idempotency filter covering: first request, duplicate request, expired key, missing key scenarios.

---

### 2.5 Add Pagination Response Wrapper

**Gap:** 5.2 — Pagination lacks metadata
**Severity:** Medium | **Effort:** Small

Return proper pagination metadata in list endpoints.

**Devin Prompt:**
> Create a generic `PageResponse<T>` wrapper class in the shared library (or in each service if the shared library doesn't exist yet) with fields: `content` (List<T>), `totalElements` (long), `totalPages` (int), `currentPage` (int), `pageSize` (int), `hasNext` (boolean), `hasPrevious` (boolean). Update all GET list endpoints in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service to return `ResponseEntity<PageResponse<T>>` instead of `ResponseEntity<List<T>>`. Convert the Spring Data `Page` object to `PageResponse` in each service layer. Update existing tests.

---

### 2.6 Add Rate Limiting to API Gateway

**Gap:** 4.5 — No rate limiting
**Severity:** Medium | **Effort:** Medium

Add rate limiting at the gateway level to protect financial endpoints.

**Devin Prompt:**
> Add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency to the gateway's `build.gradle`. (2) Add a Redis service to `docker-compose.yml`. (3) Configure rate limiting in the gateway's route configuration with: replenishRate of 10 requests/second and burstCapacity of 20 for fund transfer and payment routes, and higher limits (50/100) for read-only routes. (4) Use the `PrincipalNameKeyResolver` to rate-limit per authenticated user. (5) Return HTTP 429 (Too Many Requests) when limits are exceeded. (6) Document the rate limits in the API documentation.

---

### 2.7 Add Dependency Vulnerability Scanning

**Gap:** 4.6 — No dependency vulnerability scanning
**Severity:** Medium | **Effort:** Small

Add OWASP dependency check to the build pipeline.

**Devin Prompt:**
> Add OWASP Dependency Check to all services. (1) Add the `org.owasp.dependencycheck` Gradle plugin (version 9.x) to each service's `build.gradle`. (2) Configure it to fail the build on CVSS score >= 7.0 (high severity). (3) Create a shared suppression file at `config/owasp-suppressions.xml` for any known false positives. (4) Add a `dependencyCheckAnalyze` task that can be run independently. (5) If a root `build.gradle` is created, configure the plugin there to avoid repetition.

---

### 2.8 Fix OpenAPI Documentation

**Gap:** 5.4 — Inconsistent OpenAPI documentation
**Severity:** Medium | **Effort:** Small

Fix the incorrect OpenAPI starter and enhance API documentation.

**Devin Prompt:**
> Fix the OpenAPI documentation across all services. (1) Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in the `build.gradle` of core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service (these are servlet-based, not reactive). (2) Add `@ApiResponse` annotations to all controller methods documenting success (200/201) and error (400/404/500) responses. (3) Add `@Schema` annotations to all DTO fields with descriptions and examples. (4) Configure OpenAPI info (title, version, description) in each service's `application.yml` under `springdoc.info.*`.

---

## Phase 3: Polish (4-8 weeks)

Strategic improvements for production readiness, maintainability, and operational excellence.

### 3.1 Implement Saga Pattern for Distributed Transactions

**Gap:** 7.6 — No saga / compensation pattern
**Severity:** High | **Effort:** Large

Implement a choreography-based saga for fund transfers and payments.

**Devin Prompt:**
> Implement a saga pattern for the fund transfer flow to handle distributed transaction failures. (1) Add a `TransactionStatus` state machine in both fund-transfer-service and core-banking-service: INITIATED → PENDING → PROCESSING → SUCCESS/FAILED/COMPENSATING/COMPENSATED. (2) Add a compensation endpoint in core-banking-service: `POST /api/v1/transaction/compensate/{transactionId}` that reverses a completed transfer. (3) In fund-transfer-service's `FundTransferService`, wrap the flow in try/catch: if the core banking call succeeds but the local status update fails, call the compensation endpoint. (4) Add a scheduled job that checks for PENDING transfers older than 5 minutes and either retries or compensates them. (5) Write comprehensive tests for all failure scenarios. Consider using Spring State Machine or a lightweight saga orchestrator library.

---

### 3.2 Add Integration Tests with Testcontainers

**Gap:** 3.2 — No integration tests
**Severity:** High | **Effort:** Large

Add end-to-end integration tests that verify real infrastructure.

**Devin Prompt:**
> Add integration tests using Testcontainers for core-banking-service and the internet banking services. (1) Add `org.testcontainers:testcontainers`, `org.testcontainers:mysql`, and `org.testcontainers:junit-jupiter` dependencies to each service's `build.gradle`. (2) For core-banking-service, create integration tests that: start a MySQL container, run Flyway migrations, and test the full fund-transfer and utility-payment flows against a real database. (3) For internet-banking-fund-transfer-service, create integration tests using WireMock to mock the core-banking-service Feign client and MySQL Testcontainer for the local database. Test the complete transfer lifecycle: PENDING → SUCCESS and PENDING → FAILED scenarios. (4) Repeat for internet-banking-utility-payment-service. (5) For internet-banking-user-service, add a Keycloak Testcontainer using `dasniko/testcontainers-keycloak` to test the full registration flow. (6) Configure a separate Gradle test task `integrationTest` so unit and integration tests can run independently.

---

### 3.3 Add Consumer-Driven Contract Tests

**Gap:** 3.3 — No contract tests
**Severity:** Medium | **Effort:** Large

Add Spring Cloud Contract tests between services.

**Devin Prompt:**
> Add Spring Cloud Contract tests to verify the API contract between consumers and the core-banking-service provider. (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service's `build.gradle`. (2) Write contract DSL files in `core-banking-service/src/test/resources/contracts/` for: fund-transfer request/response, utility-payment request/response, account lookup, and user lookup. (3) Generate a contract stub JAR for core-banking-service. (4) Add `spring-cloud-starter-contract-stub-runner` to internet-banking-fund-transfer-service and internet-banking-utility-payment-service's `build.gradle`. (5) Write consumer contract tests that run against the stub, verifying that Feign client DTOs are compatible with the provider's API.

---

### 3.4 Add Custom Metrics and Dashboards

**Gap:** 6.4 — No metrics/dashboards
**Severity:** Medium | **Effort:** Medium

Add Prometheus metrics and Grafana dashboards.

**Devin Prompt:**
> Add custom business metrics and a monitoring stack. (1) Add `micrometer-registry-prometheus` dependency to all services' `build.gradle`. (2) Expose `/actuator/prometheus` endpoint in each service. (3) Add custom metrics in key service methods: counter for `fund_transfers_total` (tagged by status), counter for `utility_payments_total` (tagged by status), timer for `fund_transfer_duration_seconds`, gauge for `active_users_total`. Use `MeterRegistry` injection. (4) Add Prometheus and Grafana services to `docker-compose.yml`. (5) Create a `docker-compose/prometheus/prometheus.yml` config that scrapes all service endpoints. (6) Create a Grafana dashboard JSON provisioning file with panels for: transaction throughput, error rates, response times (p50, p95, p99), and circuit breaker state.

---

### 3.5 Create Multi-Module Gradle Build

**Gap:** 1.1 — No multi-module Gradle build
**Severity:** Medium | **Effort:** Medium

Unify all services under a single Gradle build.

**Devin Prompt:**
> Convert the project to a multi-module Gradle build. (1) Create a root `settings.gradle` that includes all service modules: `core-banking-service`, `internet-banking-api-gateway`, `internet-banking-config-server`, `internet-banking-fund-transfer-service`, `internet-banking-service-registry`, `internet-banking-user-service`, `internet-banking-utility-payment-service`, and `internet-banking-common` (shared library). (2) Create a root `build.gradle` with shared configuration: Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common test dependencies, Lombok. Use `subprojects {}` block for shared config. (3) Simplify each service's `build.gradle` to only declare service-specific dependencies. (4) Add a root `gradlew` wrapper. (5) Verify all services build with `./gradlew build` from the root. (6) Update the `README.md` and Dockerfiles to reference the new build structure.

---

### 3.6 Add Custom Health Checks

**Gap:** 6.3 — No custom health checks
**Severity:** Low | **Effort:** Small

Add application-specific health indicators.

**Devin Prompt:**
> Add custom health check indicators to relevant services. (1) In core-banking-service, add a `DatabaseHealthIndicator` that verifies the MySQL connection and checks that critical tables exist. (2) In internet-banking-user-service, add a `KeycloakHealthIndicator` that pings the Keycloak server URL. (3) In internet-banking-fund-transfer-service and internet-banking-utility-payment-service, add a `CoreBankingHealthIndicator` that calls a lightweight endpoint on core-banking-service (e.g., `/actuator/health`). (4) Register all custom health indicators as Spring beans. (5) Configure `management.endpoint.health.show-details: when-authorized` in each service.

---

### 3.7 Standardize Resource Naming and POST Response Codes

**Gaps:** 5.5, 5.6 — Inconsistent naming and wrong status codes
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize REST API conventions across all services. (1) Update POST endpoints to return `ResponseEntity.status(HttpStatus.CREATED).body(...)` instead of `ResponseEntity.ok(...)` for resource creation endpoints (user registration, fund transfer initiation, utility payment processing). (2) Document the naming rationale in a `docs/API_CONVENTIONS.md` file: explain why `/api/v1/transfer` (noun) is used rather than `/api/v1/transfers` and whether the team prefers singular or plural. (3) Add `Location` headers to POST responses pointing to the created resource where applicable.

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| **Phase 1: Quick Wins** | 10 items | 2 (credentials, validation) | 1-2 weeks |
| **Phase 2: Important** | 8 items | 2 (circuit breakers, tests) | 2-4 weeks |
| **Phase 3: Polish** | 7 items | 0 | 4-8 weeks |

### Priority Order Within Each Phase

**Phase 1 (do in this order):**
1. Externalize hardcoded credentials (4.1) — security baseline
2. Add input validation (4.2) — prevent injection/abuse
3. Fix password exposure (4.3) — data protection
4. Fix HTTP status codes (2.2) — API correctness
5. Fix exception message shadowing (2.4) — bug fix
6. Fix raw type ResponseEntity (2.3) — code quality
7. Fix logging issues (6.1) — prevent data leaks
8. Add Feign timeouts (7.3) — resilience baseline
9. Add retry policies (7.2) — resilience
10. Secure actuator endpoints (6.2) — security hardening

**Phase 2 (do in this order):**
1. Add circuit breakers (7.1) — prevent cascading failures
2. Add unit tests (3.1) — safety net for all changes
3. Extract shared library (1.2) — reduce maintenance burden
4. Add idempotency protection (7.5) — financial correctness
5. Add pagination metadata (5.2) — API usability
6. Fix OpenAPI documentation (5.4) — developer experience
7. Add dependency vulnerability scanning (4.6) — security
8. Add rate limiting (4.5) — abuse prevention

**Phase 3 (do in this order):**
1. Implement saga pattern (7.6) — distributed transaction safety
2. Add integration tests (3.2) — confidence in system behavior
3. Add contract tests (3.3) — inter-service compatibility
4. Add metrics and dashboards (6.4) — operational visibility
5. Create multi-module build (1.1) — build efficiency
6. Add custom health checks (6.3) — operational readiness
7. Standardize naming and response codes (5.5, 5.6) — API polish
