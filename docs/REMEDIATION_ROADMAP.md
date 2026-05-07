# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases based on impact, risk, and effort. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical bugs + Small/Medium effort items)

These items address critical bugs, security vulnerabilities, and high-impact issues that can be resolved quickly. Target: **1-2 weeks**.

### 1.1 Fix Balance Calculation Bug (Gap 7.7)
**Severity: Critical | Effort: Small**

The double-subtraction bug in `TransactionService.internalFundTransfer()` and `utilPayment()` causes incorrect balance calculations. `availableBalance` is set by subtracting from `actualBalance` after `actualBalance` was already modified, resulting in double the intended deduction.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is being double-subtracted because it reads from `actualBalance` after it was already modified. The fix should set `availableBalance` to the same value as the new `actualBalance` (i.e., `setAvailableBalance(entity.getActualBalance())` after setting `actualBalance`). Also update the existing unit tests to verify the correct balances after transfer. Open a PR with the fix.

---

### 1.2 Fix HTTP Status Codes in Error Handlers (Gap 2.1)
**Severity: Critical | Effort: Medium**

All exceptions return HTTP 400. `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, and unexpected exceptions should return 500.

**Devin Prompt:**
> Refactor the `GlobalExceptionHandler` in all 4 data-bearing services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return proper HTTP status codes. Map `EntityNotFoundException` to 404, `InsufficientFundsException` to 422, `UserAlreadyRegisteredException` to 409, and the generic `Exception` catch-all to 500. Also fix the generic handler to return a structured `ErrorResponse` JSON instead of a raw string. Ensure all services use the same error response structure. Open a PR.

---

### 1.3 Fix Inconsistent Error Response Format (Gap 2.2)
**Severity: High | Effort: Small**

The generic `Exception` handler returns a raw string that leaks internal details. All error responses should use the structured `ErrorResponse` format.

*Can be combined with 1.2 above.*

---

### 1.4 Secure Actuator Endpoints (Gap 6.4)
**Severity: High | Effort: Small**

Actuator endpoints are publicly accessible, potentially exposing environment variables, heap dumps, and configuration properties.

**Devin Prompt:**
> In `internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/security/SecurityConfiguration.java`, restrict actuator endpoint access. Only permit `/actuator/health` and `/actuator/info` without authentication. All other actuator endpoints (e.g., `/actuator/env`, `/actuator/heapdump`, `/actuator/configprops`) should require authentication. Apply the same pattern for all service-prefixed actuator paths (`/user/actuator/**`, `/fund-transfer/actuator/**`, etc.). Open a PR.

---

### 1.5 Fix Keycloak Singleton Thread Safety (Gap 4.5)
**Severity: High | Effort: Small**

The lazy singleton in `KeycloakProperties.getInstance()` is not thread-safe and could create duplicate Keycloak client instances.

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the non-synchronized lazy singleton pattern with either a `synchronized` block, double-checked locking with `volatile`, or ideally refactor to use a `@Bean`-annotated method in a `@Configuration` class so Spring manages the singleton lifecycle. Open a PR.

---

### 1.6 Add Feign Client Timeouts (Gap 7.3)
**Severity: High | Effort: Small**

No timeouts are configured for inter-service HTTP calls, risking thread starvation.

**Devin Prompt:**
> Add timeout configuration for all Feign clients across the three services that use them (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Configure connection timeout of 5 seconds and read timeout of 10 seconds. This can be done via `application.yml` properties under `spring.cloud.openfeign.client.config.default.connect-timeout` and `read-timeout`. Also add these defaults to the config server Git repository configuration if accessible, or document the expected config. Open a PR.

---

### 1.7 Add Feign Client Retry Policies (Gap 7.2)
**Severity: High | Effort: Small**

No retry configuration exists for transient network failures.

**Devin Prompt:**
> Add retry configuration for Feign clients across all three consuming services. Configure Spring Cloud OpenFeign's built-in `Retryer` with a maximum of 3 attempts, 100ms initial interval, and 1-second max interval. Add a `Retryer` bean in each service's Feign configuration class. Ensure retries only apply to GET requests (not POST, to avoid duplicate transactions). Open a PR.

---

### 1.8 Remove Sensitive Data from Logs (Gap 4.7)
**Severity: Medium | Effort: Small**

User passwords are logged in plaintext via `request.toString()` in controllers.

**Devin Prompt:**
> Audit all controller classes across all services and remove or sanitize any logging of request objects that may contain sensitive data. Specifically, in `internet-banking-user-service/UserController.java`, the `createUser` method logs the full `User` object which includes the password field. Either exclude the password from `toString()` (add `@ToString.Exclude` on the password field in the `User` DTO) or log only non-sensitive fields. Apply the same pattern to all controllers. Open a PR.

---

### 1.9 Add JaCoCo Test Coverage Reporting (Gap 3.4)
**Severity: Medium | Effort: Small**

No code coverage tools are configured.

**Devin Prompt:**
> Add the JaCoCo Gradle plugin to all service `build.gradle` files. Configure it to generate HTML and XML reports on `./gradlew test`. Add a minimum coverage threshold of 40% (to be raised incrementally) with the `jacocoTestCoverageVerification` task. This gives us a baseline to track coverage improvements. Open a PR.

---

### 1.10 Fix OpenAPI/Swagger Configuration (Gap 5.6)
**Severity: Medium | Effort: Small**

The wrong Springdoc artifact (`webflux-ui`) is used in Spring MVC services, and `ResponseEntity` lacks type parameters.

**Devin Prompt:**
> Fix the OpenAPI configuration in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Replace the `springdoc-openapi-starter-webflux-ui` dependency with `springdoc-openapi-starter-webmvc-ui` (same version 2.1.0) since these services use Spring MVC, not WebFlux. Also add generic type parameters to all `ResponseEntity` return types in controllers (e.g., `ResponseEntity<BankAccount>` instead of raw `ResponseEntity`). Open a PR.

---

### 1.11 Add Prometheus Metrics Endpoint (Gap 6.3)
**Severity: Medium | Effort: Small**

Prometheus is listed as part of the tech stack but the registry dependency is missing.

**Devin Prompt:**
> Add `implementation 'io.micrometer:micrometer-registry-prometheus'` to the `build.gradle` of all services that have the actuator dependency. Ensure the `/actuator/prometheus` endpoint is exposed by adding `management.endpoints.web.exposure.include=health,info,prometheus` to each service's configuration. Open a PR.

---

## Phase 2: Important (High-impact structural improvements)

These items address architectural gaps, security hardening, and testing foundations. Target: **3-6 weeks**.

### 2.1 Add Input Validation to All Request DTOs (Gaps 2.3, 4.2)
**Severity: Critical | Effort: Medium**

No Bean Validation exists on any request body.

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services. For `FundTransferRequest`: add `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`. For `UtilityPaymentRequest`: add `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`. For `User` (user-service): add `@NotBlank @Email` on `email`, `@NotBlank` on `identification` and `password`. Add `@Valid` annotations on all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 400 with field-level validation errors in the `ErrorResponse` format. Add `spring-boot-starter-validation` dependency if not already present. Open a PR.

---

### 2.2 Externalize Secrets from Source Code (Gap 4.1)
**Severity: Critical | Effort: Medium**

Database passwords, Keycloak credentials, and other secrets are hardcoded.

**Devin Prompt:**
> Remove all hardcoded credentials from the codebase and Docker Compose files. In `docker-compose.yml` and `docker-compose-support-apps.yml`, replace hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD:-changeme}`). Create a `.env.example` file documenting all required environment variables with placeholder values. Add `.env` to `.gitignore`. For `docker-compose/mysql/Dockerfile`, remove the hardcoded `ENV MYSQL_ROOT_PASSWORD` and use the compose environment variable instead. Update `docker-compose/mysql/privileges.sql` to use environment variable substitution or document that the password should be changed. Remove the test credentials from `README.md` or move them to a separate non-committed file. Open a PR.

---

### 2.3 Add Circuit Breakers (Gap 7.1)
**Severity: Critical | Effort: Medium**

No circuit breakers protect against cascading failures when core-banking-service is unavailable.

**Devin Prompt:**
> Add Resilience4j circuit breaker support to the three services that call core-banking-service via Feign (internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service). Add `spring-cloud-starter-circuitbreaker-resilience4j` and `resilience4j-spring-boot3` dependencies. Configure circuit breakers with: failure rate threshold 50%, slow call rate threshold 80%, slow call duration threshold 3s, sliding window size 10, wait duration in open state 30s. Add fallback methods that return meaningful error responses (e.g., "Core banking service is temporarily unavailable. Please try again later.") instead of raw Feign exceptions. Open a PR.

---

### 2.4 Add Role-Based Access Control (Gap 4.6)
**Severity: High | Effort: Medium**

All authenticated users can access all endpoints including admin operations.

**Devin Prompt:**
> Implement role-based access control (RBAC) for the banking application. In the API gateway's `SecurityConfiguration`, differentiate between admin and user roles based on JWT claims from Keycloak. The `PATCH /user/api/v1/bank-users/update/{id}` endpoint should require an `ADMIN` role. User listing endpoints should require `ADMIN` role. Transaction and payment endpoints should require `USER` role. User registration should remain public. Configure the JWT decoder to extract roles from the Keycloak token's `realm_access.roles` claim. Update the Keycloak realm export to include `ADMIN` and `USER` roles if not already present. Open a PR.

---

### 2.5 Add Rate Limiting (Gap 4.4)
**Severity: High | Effort: Medium**

No rate limiting exists on any endpoint.

**Devin Prompt:**
> Add rate limiting to the API gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter with Redis or in-memory token bucket. Configure a default rate of 100 requests per minute per authenticated user. Apply stricter limits to financial transaction endpoints: 10 fund transfers per minute and 20 utility payments per minute per user. Add Redis as a dependency in Docker Compose for the rate limiter's distributed token bucket. Document the rate limit headers (`X-RateLimit-Remaining`, `X-RateLimit-Limit`) in the API documentation. Open a PR.

---

### 2.6 Write Unit Tests for All Services (Gap 3.1)
**Severity: Critical | Effort: Large**

Only core-banking-service has meaningful tests.

**Devin Prompt:**
> Add comprehensive unit tests for internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For each service, create tests for the service layer with mocked dependencies. For user-service: test `createUser` (happy path, duplicate email, invalid email, user not found in core banking), `updateUser` (approve, reject), `readUsers`, `readUser`. For fund-transfer-service: test `fundTransfer` (happy path, core banking failure), `readAllTransfers`. For utility-payment-service: test `utilPayment` (happy path, core banking failure), `readPayments`. Each service should also have controller tests using `@WebMvcTest` with `MockMvc` to verify HTTP status codes, request validation, and response shapes. Use H2 test profiles. Open a PR.

---

### 2.7 Create Shared Library for Common Code (Gap 1.2)
**Severity: High | Effort: Large**

Significant code duplication across services.

**Devin Prompt:**
> Create a shared library module called `internet-banking-common` containing the duplicated code across services. Extract: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` (base version), and `GlobalErrorCode`. Set up a Gradle multi-project build with a root `settings.gradle` that includes all 7 services plus the common library. Each service's `build.gradle` should declare `implementation project(':internet-banking-common')`. Remove the duplicated classes from each service and import from the common library. Ensure all tests still pass. Open a PR.

---

### 2.8 Add Structured JSON Logging (Gap 6.1)
**Severity: Medium | Effort: Medium**

No structured logging or correlation IDs across services.

**Devin Prompt:**
> Add structured JSON logging to all services. Add the `logstash-logback-encoder` dependency and create a shared `logback-spring.xml` configuration that outputs JSON-formatted logs with fields: timestamp, level, service name, trace ID (from Micrometer), span ID, thread, logger, message. Configure console appender for local development (text format) and file/stdout appender for Docker profile (JSON format) using Spring profiles. Ensure trace IDs from Micrometer Tracing are automatically included in the MDC. Open a PR.

---

### 2.9 Add Pagination Metadata to List Responses (Gap 5.4)
**Severity: Medium | Effort: Small**

List endpoints return raw lists without pagination context.

**Devin Prompt:**
> Update all paginated list endpoints across all services to return a standard paginated response wrapper instead of raw `List<T>`. Create a generic `PageResponse<T>` class in the shared common module with fields: `content` (list of items), `page` (current page number), `size` (page size), `totalElements`, `totalPages`, `last` (boolean). Update controllers to return `ResponseEntity<PageResponse<T>>` and map from Spring's `Page<T>`. Update existing tests. Open a PR.

---

### 2.10 Implement Idempotency Keys for Transaction Endpoints (Gap 7.6)
**Severity: High | Effort: Medium**

Duplicate transaction submissions could cause double-debits.

**Devin Prompt:**
> Add idempotency key support to the fund transfer and utility payment endpoints. Accept an `X-Idempotency-Key` HTTP header (UUID) on POST endpoints. Before processing, check if a transaction with the given idempotency key already exists in the database. If it does, return the existing result without re-processing. Add an `idempotency_key` column (unique, indexed) to the `fund_transfer` and `utility_payment` tables. If the header is missing, generate one automatically but log a warning. Add tests for duplicate submission scenarios. Open a PR.

---

## Phase 3: Polish (Structural excellence + long-term maintainability)

These items improve developer experience, long-term maintainability, and operational maturity. Target: **6-12 weeks**.

### 3.1 Implement Saga Pattern for Cross-Service Transactions (Gap 7.5)
**Severity: Critical | Effort: Large**

Fund transfers and payments span two services without distributed transaction support.

**Devin Prompt:**
> Implement the Saga pattern for the fund transfer flow to handle partial failures across services. Add a state machine to the fund-transfer-service with states: INITIATED, CORE_BANKING_PROCESSING, COMPLETED, COMPENSATION_REQUIRED, COMPENSATED, FAILED. If the core-banking call succeeds but the local status update fails, a scheduled compensating job should detect stuck `CORE_BANKING_PROCESSING` records and either retry the status update or trigger a reversal via a new core-banking endpoint `POST /api/v1/transaction/fund-transfer/reverse`. Add a `@Scheduled` compensation task that runs every 60 seconds. Apply the same pattern to utility payments. Add comprehensive tests for each failure scenario. Open a PR.

---

### 3.2 Add Integration Tests with Testcontainers (Gap 3.2)
**Severity: High | Effort: Large**

No integration tests verify actual database operations or Spring wiring.

**Devin Prompt:**
> Add integration tests using Testcontainers for all data-bearing services. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies. Create an `@IntegrationTest` base class that starts a MySQL Testcontainer and configures Spring datasource properties. For core-banking-service: test the full Flyway migration + data seeding, verify fund transfer and utility payment end-to-end through the controller layer using `@SpringBootTest` with `TestRestTemplate`. For user-service, fund-transfer, and utility-payment services: test JPA repository operations against real MySQL. Use WireMock to stub Feign client calls to core-banking-service. Open a PR.

---

### 3.3 Add Consumer-Driven Contract Tests (Gap 3.3)
**Severity: High | Effort: Large**

No contract tests between services to catch API breaking changes.

**Devin Prompt:**
> Add Spring Cloud Contract tests between the Feign client consumers and the core-banking-service provider. On the provider side (core-banking-service): add `spring-cloud-starter-contract-verifier` and write contract DSL files for each endpoint (fund-transfer, util-payment, bank-account lookup, user lookup). On the consumer side (fund-transfer-service, utility-payment-service, user-service): add `spring-cloud-starter-contract-stub-runner` and write tests that run against the generated stubs. Configure the contract verification to run as part of the provider's build. Open a PR.

---

### 3.4 Set Up Gradle Multi-Project Build (Gap 1.1)
**Severity: Medium | Effort: Medium**

Independent build files lead to version drift.

**Devin Prompt:**
> Convert the project from independent Gradle builds to a multi-project build. Create a root `settings.gradle` that includes all 7 service modules plus the common library. Create a root `build.gradle` with a `subprojects` block that defines shared configuration: Java 21 source compatibility, Spring Boot 3.2.4 plugin (apply false at root), Spring Cloud BOM 2023.0.0, common repositories, and shared test configuration (JUnit Platform). Each service's `build.gradle` should only declare its unique dependencies. Ensure all services build correctly with `./gradlew build` from the root. Open a PR.

---

### 3.5 Add Dependency Vulnerability Scanning (Gap 4.8)
**Severity: Medium | Effort: Small**

No automated detection of vulnerable dependencies.

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to the root build configuration. Configure it to run on `./gradlew dependencyCheckAnalyze` and fail the build if any CVE with CVSS score >= 7.0 is found. Generate HTML and JSON reports in each service's `build/reports/dependency-check/` directory. Add a CI step (if GitHub Actions exists) to run the check on every PR. Suppress any known false positives with a shared `dependency-check-suppressions.xml` file. Open a PR.

---

### 3.6 Standardize Package Structure Across Services (Gap 1.3)
**Severity: Low | Effort: Small**

Package layouts are inconsistent across services.

**Devin Prompt:**
> Standardize the package structure across all services to follow a consistent convention. Each service should use: `controller/`, `service/`, `model/dto/`, `model/entity/`, `model/mapper/`, `repository/`, `configuration/`, `exception/`, and `service/rest/` (for Feign clients). Move classes that are in non-standard locations: in user-service, move `model/repository/` to `repository/`; in fund-transfer-service, move `service/rest/client/` to `service/rest/`. Ensure all imports are updated and tests pass. Open a PR.

---

### 3.7 Add Fallback Behavior for Feign Clients (Gap 7.4)
**Severity: Medium | Effort: Medium**

No graceful degradation when core-banking-service is unavailable.

**Devin Prompt:**
> Add fallback classes for all Feign clients. For each Feign client interface, create a fallback implementation class annotated with `@Component` that returns meaningful error responses instead of propagating raw exceptions. For `BankingCoreFeignClient` in fund-transfer-service: the fallback `fundTransfer()` should throw a `ServiceUnavailableException` (new custom exception, HTTP 503) with message "Core banking service is temporarily unavailable". Wire fallbacks using `@FeignClient(fallback = ...)` with Resilience4j. Add tests that verify fallback behavior. Open a PR.

---

### 3.8 Add Custom Health Indicators (Gap 6.2)
**Severity: Low | Effort: Small**

Default health checks don't cover critical dependencies.

**Devin Prompt:**
> Add custom Spring Boot Actuator health indicators for critical dependencies. In user-service: add a `KeycloakHealthIndicator` that checks Keycloak connectivity by calling the realm endpoint. In all database services: verify the default `DataSourceHealthIndicator` is active (it should be by default with actuator + JPA). In all Eureka client services: verify `EurekaHealthIndicator` is active. In the API gateway: add a composite health check that aggregates downstream service health. Configure `management.endpoint.health.show-details=when-authorized` so detailed health info is only visible to authenticated users. Open a PR.

---

### 3.9 Implement API Versioning Strategy (Gap 5.2)
**Severity: Medium | Effort: Medium**

No formal versioning or deprecation mechanism exists.

**Devin Prompt:**
> Document and formalize the API versioning strategy for the banking microservices. Create a `docs/API_VERSIONING.md` document that describes: URL-based versioning convention (`/api/v{N}/`), backward compatibility policy, deprecation process (minimum 2 release cycles), and header-based version negotiation as a future option. Add a `@Deprecated` annotation pattern for endpoints being phased out. Add API version response headers (`X-API-Version: v1`) via a Spring interceptor or gateway filter. Open a PR.

---

### 3.10 Implement Notification Service with RabbitMQ (Referenced in README)
**Severity: Medium | Effort: Large**

The notification service is listed in the README as planned but not yet implemented.

**Devin Prompt:**
> Implement the notification service referenced in the README. Create a new `internet-banking-notification-service` module. Add `spring-boot-starter-amqp` (RabbitMQ) dependency. Define message schemas for fund transfer and utility payment notifications. In fund-transfer-service and utility-payment-service, publish notification messages to RabbitMQ after successful transactions. The notification service should consume these messages and log them (email/SMS integration can be stubbed). Add RabbitMQ to the Docker Compose file. Register the service with Eureka. Add unit and integration tests. Open a PR.

---

## Summary Timeline

| Phase | Focus | Duration | Items |
|---|---|---|---|
| **Phase 1** | Quick wins — critical bugs, security, observability | 1-2 weeks | 11 items (1.1 - 1.11) |
| **Phase 2** | Important — validation, testing, resilience, RBAC | 3-6 weeks | 10 items (2.1 - 2.10) |
| **Phase 3** | Polish — sagas, contracts, structure, monitoring | 6-12 weeks | 10 items (3.1 - 3.10) |

## Priority Order Within Each Phase

### Phase 1 (do in this order):
1. Fix balance calculation bug (1.1) — **data corruption in production**
2. Fix HTTP status codes + error format (1.2, 1.3) — **client-facing**
3. Secure actuator endpoints (1.4) — **security**
4. Fix Keycloak singleton (1.5) — **concurrency bug**
5. Add timeouts + retries (1.6, 1.7) — **resilience basics**
6. Remove sensitive data from logs (1.8) — **compliance**
7. Fix OpenAPI config (1.10) — **developer experience**
8. Add Prometheus metrics (1.11) — **observability**
9. Add JaCoCo coverage (1.9) — **quality baseline**

### Phase 2 (do in this order):
1. Add input validation (2.1) — **security + data integrity**
2. Externalize secrets (2.2) — **security**
3. Add circuit breakers (2.3) — **resilience**
4. Add RBAC (2.4) — **security**
5. Write unit tests (2.6) — **quality foundation**
6. Add idempotency keys (2.10) — **data integrity**
7. Create shared library (2.7) — **maintainability**
8. Add rate limiting (2.5) — **security**
9. Add structured logging (2.8) — **observability**
10. Add pagination metadata (2.9) — **API quality**

### Phase 3 (do in this order):
1. Implement saga pattern (3.1) — **data consistency**
2. Add integration tests (3.2) — **quality**
3. Add contract tests (3.3) — **quality**
4. Multi-project Gradle build (3.4) — **maintainability**
5. Dependency vulnerability scanning (3.5) — **security**
6. Standardize packages (3.6) — **consistency**
7. Add Feign fallbacks (3.7) — **resilience**
8. Custom health indicators (3.8) — **observability**
9. API versioning strategy (3.9) — **API governance**
10. Notification service (3.10) — **feature completion**
