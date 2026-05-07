# Internet Banking Microservices - Remediation Roadmap

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases:

- **Phase 1 - Quick Wins**: High severity + low effort items that reduce the most risk fastest
- **Phase 2 - Important**: High-impact items requiring moderate effort
- **Phase 3 - Polish**: Lower severity improvements and larger structural changes

Each item includes a **sample Devin prompt** that can be used to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High severity, small effort. These should be addressed immediately.

### 1.1 Fix Double-Deduction Balance Bug (GAP-37)

**Priority**: P0 - Data corruption in production
**Effort**: Small

The `availableBalance` calculation in `TransactionService` subtracts the amount twice because it reads from the already-debited `actualBalance`.

**Devin Prompt**:
> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the double-deduction bug. In `internalFundTransfer()`, line 91 should set `availableBalance` to the same value as `actualBalance` (not subtract again). Same fix needed for `toBankAccountEntity` on line 100 (availableBalance should equal actualBalance after credit). Apply the identical fix in `utilPayment()` on line 64. Add unit tests that assert correct balance values after each operation. Open a PR with the fix.

---

### 1.2 Fix Error Handling - Proper HTTP Status Codes (GAP-05, GAP-06, GAP-07)

**Priority**: P0 - All errors returning 400 masks real issues
**Effort**: Small

**Devin Prompt**:
> Refactor the `GlobalExceptionHandler` in all four services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service). Add specific exception handlers that return proper HTTP status codes: `EntityNotFoundException` -> 404, `InsufficientFundsException` -> 422, `InvalidEmailException` / `InvalidBankingUserException` -> 400, `UserAlreadyRegisteredException` -> 409. The catch-all handler should return 500 with a generic message (do NOT expose the exception object or stack trace). Standardize the error response format across all services to: `{"timestamp": "...", "status": 400, "code": "ERR_CODE", "message": "Human-readable message"}`. Open a PR with these changes.

---

### 1.3 Add Input Validation to All Request DTOs (GAP-14)

**Priority**: P0 - Null/negative values cause NPEs and incorrect transactions
**Effort**: Small

**Devin Prompt**:
> Add Bean Validation (jakarta.validation) annotations to all request DTOs across the codebase. Add `spring-boot-starter-validation` to the build.gradle of core-banking-service, fund-transfer-service, utility-payment-service, and user-service. Annotate: `FundTransferRequest.fromAccount` and `toAccount` with `@NotBlank`, `amount` with `@NotNull @Positive`. `UtilityPaymentRequest.providerId` with `@NotNull`, `amount` with `@NotNull @Positive`, `referenceNumber` with `@NotBlank`, `account` with `@NotBlank`. `User` registration: `email` with `@NotBlank @Email`, `identification` with `@NotBlank`, `password` with `@NotBlank @Size(min=8)`. Add `@Valid` to all `@RequestBody` parameters in all controllers. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns 400 with field-level error details. Open a PR.

---

### 1.4 Remove Hardcoded Credentials from Source (GAP-16)

**Priority**: P0 - Credentials committed to Git
**Effort**: Small

**Devin Prompt**:
> Replace all hardcoded credentials in the Docker Compose files and MySQL setup with environment variables. In `docker-compose/docker-compose.yml` and `docker-compose-support-apps.yml`: replace `MYSQL_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KC_DB_PASSWORD`, `POSTGRES_PASSWORD` values with `${VARIABLE_NAME}` references. Create a `.env.example` file documenting all required environment variables with placeholder values. Update `docker-compose/mysql/Dockerfile` to not hardcode `MYSQL_ROOT_PASSWORD`. Update `docker-compose/mysql/privileges.sql` to use a parameterized password or document that it should be customized. Add `.env` to `.gitignore`. Remove the test credentials from `README.md` and reference the `.env.example` file instead. Open a PR.

---

### 1.5 Fix Wrong OpenAPI Dependency (GAP-23)

**Priority**: P1 - Classpath conflict
**Effort**: Small

**Devin Prompt**:
> In the `build.gradle` files of core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service, replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0`. These are Spring MVC applications and should use the MVC variant. Verify each service still compiles with `./gradlew clean build`. Open a PR.

---

### 1.6 Add Feign Error Decoders to Fund Transfer and Utility Payment (GAP-08)

**Priority**: P1 - Raw Feign exceptions propagating
**Effort**: Small

**Devin Prompt**:
> The internet-banking-user-service already has a `CustomFeignErrorDecoder` in `configuration/feign/`. Copy this pattern to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Create `CustomFeignErrorDecoder` classes in both services that handle 400, 401, 404, and default cases. Register the error decoder in each service's `CustomFeignClientConfiguration` as a `@Bean`. Ensure the Feign client configurations reference the new decoder. Open a PR.

---

### 1.7 Fix Keycloak Client Thread Safety (GAP-18)

**Priority**: P1 - Race condition under concurrent requests
**Effort**: Small

**Devin Prompt**:
> In `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`, fix the non-thread-safe singleton pattern for the Keycloak instance. Replace the manual null-check singleton with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once at startup using `KeycloakBuilder`. Remove the static `keycloakInstance` field. Inject the `Keycloak` bean into `KeycloakManager` instead of calling `keycloakProperties.getInstance()`. Open a PR.

---

### 1.8 Stop Logging Sensitive Data (GAP-30)

**Priority**: P1 - Passwords logged in plain text
**Effort**: Small

**Devin Prompt**:
> Audit all `log.info()` calls across all services that log request DTOs. In `internet-banking-user-service/UserController.java`, the `createUser` log statement logs the full `User` object which contains the password field. Remove or mask the password before logging. In all controllers, replace `request.toString()` logging with specific non-sensitive fields only (e.g., log account numbers but not full request bodies). Alternatively, add `@ToString.Exclude` (Lombok) on sensitive fields like `password`. Also fix the string concatenation in `FundTransferService` line 32: `log.info("Sending fund transfer request {}" + request.toString())` should use `{}` placeholder syntax. Open a PR.

---

### 1.9 Add Feign Timeout Configuration (GAP-33)

**Priority**: P1 - Requests can hang indefinitely
**Effort**: Small

**Devin Prompt**:
> Add Feign timeout configuration to the fund-transfer-service, utility-payment-service, and user-service. In each service's `application.yml` (or the centralized config repo), add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. If you use the centralized config, update the config files in the config server GitHub repo. For the local approach, add the configuration to each service's `application.yml`. Open a PR.

---

### 1.10 Add Retry Policies to Feign Clients (GAP-32)

**Priority**: P1 - No recovery from transient failures
**Effort**: Small

**Devin Prompt**:
> Add Spring Retry support to the fund-transfer-service, utility-payment-service, and user-service. Add `spring-retry` and `spring-boot-starter-aop` dependencies to each service's `build.gradle`. Add `@EnableRetry` to each application's main class. Configure Feign retry via `spring.cloud.openfeign.client.config.default.retryer` properties with max 3 attempts and 1-second backoff. Add `Retryer` bean in each `CustomFeignClientConfiguration`. Open a PR.

---

### 1.11 Add Pagination Metadata to Responses (GAP-21)

**Priority**: P2 - Clients cannot navigate pages
**Effort**: Small

**Devin Prompt**:
> Create a generic `PageResponse<T>` wrapper class in each service (or in a shared location) with fields: `content` (List<T>), `pageNumber` (int), `pageSize` (int), `totalElements` (long), `totalPages` (int), `last` (boolean). Update all paginated service methods to return `PageResponse<T>` instead of `List<T>`. In the service layer, use `Page<Entity>` metadata from Spring Data to populate the wrapper. Update controllers to return `ResponseEntity<PageResponse<T>>`. Affected endpoints: `GET /api/v1/user` (core), `GET /api/v1/bank-users` (user-service), `GET /api/v1/transfer` (fund-transfer), `GET /api/v1/utility-payment` (utility-payment). Open a PR.

---

### 1.12 Configure Actuator Health Checks (GAP-27)

**Priority**: P2 - No visibility into service health
**Effort**: Small

**Devin Prompt**:
> Configure Spring Boot Actuator health checks for all services. In each service's `application.yml` (or centralized config), add: `management.endpoint.health.show-details: always`, `management.endpoints.web.exposure.include: health,info,metrics,prometheus`. For database-backed services, the DataSource health indicator is auto-configured. For the user-service, add a custom `KeycloakHealthIndicator` that checks Keycloak connectivity. For services using Feign, consider adding health indicators for downstream service availability. Open a PR.

---

### 1.13 Add Prometheus Metrics Export (GAP-28)

**Priority**: P2 - Mentioned in README but not implemented
**Effort**: Small

**Devin Prompt**:
> Add `io.micrometer:micrometer-registry-prometheus` dependency to the `build.gradle` of all 6 services (or at least the 4 application services). Configure `management.endpoints.web.exposure.include` to include `prometheus`. Verify that `GET /actuator/prometheus` returns Prometheus-format metrics for each service. Open a PR.

---

## Phase 2: Important (3-6 weeks)

High-impact items requiring moderate planning and effort.

### 2.1 Add Circuit Breakers to All Feign Clients (GAP-31)

**Priority**: P0 - Cascading failure risk
**Effort**: Medium

**Devin Prompt**:
> Add Resilience4j circuit breaker support to the fund-transfer-service, utility-payment-service, and user-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to each service's `build.gradle`. Enable circuit breakers for Feign: set `spring.cloud.openfeign.circuitbreaker.enabled: true` in configuration. Create fallback classes for each Feign client (e.g., `BankingCoreFeignClientFallback`) that return appropriate error responses. Configure circuit breaker parameters: `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`, `slidingWindowSize: 10`. Register fallback classes in the `@FeignClient` annotations using `fallback` attribute. Add unit tests for fallback behavior. Open a PR.

---

### 2.2 Implement Compensation for Failed Feign Calls (GAP-34)

**Priority**: P0 - Orphaned transactions
**Effort**: Medium

**Devin Prompt**:
> Implement compensation logic for failed Feign calls in fund-transfer-service and utility-payment-service. When the Feign call to core-banking-service fails: (1) Catch the exception in `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`. (2) Update the local entity status to `FAILED` instead of leaving it as `PENDING`/`PROCESSING`. (3) Add a `failureReason` field to `FundTransferEntity` and `UtilityPaymentEntity` to record why it failed. (4) Create a `@Scheduled` job in each service that retries `FAILED` transactions up to 3 times with exponential backoff. (5) After max retries, set status to `PERMANENTLY_FAILED`. Add the new status values to `TransactionStatus` enum. Add database migration for the new column. Open a PR.

---

### 2.3 Add Authentication to Downstream Services (GAP-15)

**Priority**: P0 - Services accessible without auth
**Effort**: Medium

**Devin Prompt**:
> Add JWT validation to all downstream services (core-banking-service, fund-transfer-service, utility-payment-service, user-service) so they don't rely solely on the API Gateway for authentication. Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` dependencies to each service's `build.gradle`. Create a `SecurityConfiguration` class in each service that validates JWT tokens using the Keycloak JWK Set URI. Configure the security filter chain to require authentication on all endpoints except `/actuator/**`. The `X-Auth-Id` header extraction should be a secondary mechanism (for audit logging) after JWT validation. Update test configurations to disable security or provide mock tokens. Open a PR.

---

### 2.4 Add Role-Based Access Control (GAP-19)

**Priority**: P1 - No authorization checks
**Effort**: Medium

**Devin Prompt**:
> Implement role-based access control using Keycloak roles. Define roles in the Keycloak realm: `ROLE_ADMIN` (can approve users, view all data) and `ROLE_USER` (can transfer funds, make payments, view own data). Update the `SecurityConfiguration` in each service to enforce roles: `PATCH /bank-users/update/{id}` requires `ROLE_ADMIN`, `GET /api/v1/user` (list all) requires `ROLE_ADMIN`, `POST /api/v1/transfer` requires `ROLE_USER`, `POST /api/v1/utility-payment` requires `ROLE_USER`. Add a Keycloak role mapper to extract roles from JWT claims. Update the Keycloak realm export to include the new roles. Open a PR.

---

### 2.5 Add Idempotency Keys to Financial Endpoints (GAP-35)

**Priority**: P1 - Duplicate transaction risk
**Effort**: Medium

**Devin Prompt**:
> Implement idempotency for fund transfer and utility payment POST endpoints. Create an `IdempotencyKey` entity and repository in each service with columns: `key` (unique), `response` (JSON), `created_at`, `expires_at`. Add an `Idempotency-Key` header requirement to POST endpoints. Create a servlet filter or AOP aspect that: (1) checks if the idempotency key exists and returns the cached response if so, (2) processes the request and stores the response with the key if not. Return HTTP 409 if a request with the same key is in-flight. Keys should expire after 24 hours. Add a scheduled cleanup job. Open a PR.

---

### 2.6 Extract Shared Library Module (GAP-01)

**Priority**: P1 - Reduce code duplication
**Effort**: Medium

**Devin Prompt**:
> Create a shared library module `banking-common` as a new Gradle subproject. Move the following duplicated classes into it: `AuditAware`, `BaseMapper`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `CustomFeignClientConfiguration`. Publish the shared library to the local Maven repository. Update each service's `build.gradle` to depend on `banking-common` instead of maintaining local copies. Remove the duplicated classes from each service. Verify all services compile and tests pass. Open a PR.

---

### 2.7 Add Structured Logging (GAP-26)

**Priority**: P2 - Log aggregation difficult
**Effort**: Medium

**Devin Prompt**:
> Configure structured JSON logging across all services. Add `net.logstash.logback:logstash-logback-encoder` dependency to each service's `build.gradle`. Create a shared `logback-spring.xml` configuration in each service's `src/main/resources/` that outputs JSON format in Docker profile and human-readable format in dev profile. Include standard fields: `timestamp`, `level`, `service`, `traceId`, `spanId`, `logger`, `message`. Add MDC context for `userId` from the `X-Auth-Id` header in the `AppAuthUserFilter`. Open a PR.

---

### 2.8 Set Up Multi-Module Gradle Build (GAP-02)

**Priority**: P2 - No coordinated builds
**Effort**: Medium

**Devin Prompt**:
> Create a root `settings.gradle` and `build.gradle` at the repository root to establish a Gradle multi-module build. The `settings.gradle` should include all 7 modules (6 services + banking-common if it exists). The root `build.gradle` should define shared dependency versions using a `platform` or `dependencyManagement` block, shared plugin configurations, and common test settings. Each service retains its own `build.gradle` but inherits shared configuration. Add tasks: `./gradlew buildAll` to build all services, `./gradlew testAll` to run all tests. Ensure `./gradlew clean build` from root builds everything. Open a PR.

---

### 2.9 Add Rate Limiting at the API Gateway (GAP-36)

**Priority**: P2 - No abuse protection
**Effort**: Small

**Devin Prompt**:
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. Add `spring-boot-starter-data-redis-reactive` dependency. Configure rate limits per route: `/user/api/v1/bank-users/register` at 10 req/min (prevent registration abuse), `/fund-transfer/**` at 30 req/min per user, `/payment/**` at 30 req/min per user. Use the `X-Auth-Id` header or JWT subject as the rate limit key. Add Redis to the Docker Compose configuration. Include fallback for when Redis is unavailable. Open a PR.

---

## Phase 3: Polish (6-12 weeks)

Lower severity improvements and larger structural work.

### 3.1 Add Unit Tests to All Services (GAP-10)

**Priority**: P1 - Foundational quality gap
**Effort**: Large

**Devin Prompt**:
> Add comprehensive unit tests to the 5 services that currently have no tests. For each service, create: (1) Service layer tests with Mockito mocks for repositories and Feign clients. (2) Controller layer tests using `@WebMvcTest` with `MockMvc`. (3) Repository tests using `@DataJpaTest` with H2. Target minimum 80% line coverage. Services to test: internet-banking-user-service (UserService, KeycloakUserService, UserController), internet-banking-fund-transfer-service (FundTransferService, FundTransferController), internet-banking-utility-payment-service (UtilityPaymentService, UtilityPaymentController). Create proper test `application.yml` files for each service with H2 database config and disabled Config Server/Eureka. Open a PR.

---

### 3.2 Add Integration Tests (GAP-11)

**Priority**: P1 - No end-to-end verification
**Effort**: Large

**Devin Prompt**:
> Add integration tests using Testcontainers for database-backed services. Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` to test dependencies. Create integration test classes annotated with `@SpringBootTest` and `@Testcontainers` for: (1) core-banking-service: full transaction flow with real MySQL. (2) fund-transfer-service: mock the Feign client, test DB persistence. (3) user-service: mock Feign and Keycloak, test DB persistence. (4) utility-payment-service: mock Feign, test DB persistence. Each integration test should use `@DynamicPropertySource` to configure the Testcontainers MySQL URL. Open a PR.

---

### 3.3 Add Contract Tests Between Services (GAP-12)

**Priority**: P2 - API compatibility not verified
**Effort**: Large

**Devin Prompt**:
> Add Spring Cloud Contract tests to verify API compatibility between services. In core-banking-service (the producer), define contracts for each endpoint in `src/test/resources/contracts/`. Generate contract stubs using `spring-cloud-contract-maven-plugin` (or Gradle equivalent). In consumer services (fund-transfer, utility-payment, user-service), write `@AutoConfigureStubRunner` tests that verify Feign clients work against the generated stubs. This ensures changes to core-banking-service APIs are caught before deployment. Open a PR.

---

### 3.4 Fix Empty contextLoads Tests (GAP-13)

**Priority**: P2 - Tests fail in CI
**Effort**: Small

**Devin Prompt**:
> Fix the `@SpringBootTest` contextLoads tests in all services so they can run without external dependencies. For each service (fund-transfer, utility-payment, user-service, api-gateway, config-server, service-registry): create or update `src/test/resources/application.yml` with H2 database config, disabled Eureka client (`eureka.client.enabled: false`), disabled Config Server import (`spring.cloud.config.enabled: false`), and mock Keycloak properties where needed. Alternatively, replace `@SpringBootTest` with a simpler test that doesn't load the full context if the service has complex dependencies. Open a PR.

---

### 3.5 Add Dependency Vulnerability Scanning (GAP-20)

**Priority**: P2 - No audit of known CVEs
**Effort**: Small

**Devin Prompt**:
> Add OWASP Dependency-Check to the Gradle build. Add the `org.owasp.dependencycheck` Gradle plugin to the root build (or each service's `build.gradle`). Configure it with: `failBuildOnCVSS: 7` (fail on High+ vulnerabilities), HTML and JSON report output, NVD API key if available. Add a `dependencyCheckAnalyze` task. Create a GitHub Actions workflow that runs dependency check on PRs and weekly on the main branch. Open a PR.

---

### 3.6 Standardize REST Conventions (GAP-24)

**Priority**: P3 - Inconsistent URL patterns
**Effort**: Small

**Devin Prompt**:
> Standardize REST URL conventions across all services. Rename: `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users` (resource creation, no verb). `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}` (no verb in path). Update the API Gateway route configuration to match the new paths. Update the Postman collection if it exists in the repo. Ensure backward compatibility by adding temporary redirect mappings if needed. Open a PR.

---

### 3.7 Add Centralized Request/Response Logging (GAP-25)

**Priority**: P3 - Ad-hoc logging in controllers
**Effort**: Small

**Devin Prompt**:
> Create a centralized request/response logging filter for all MVC services. Implement a `OncePerRequestFilter` that logs: HTTP method, URI, response status, duration, and request ID. Do NOT log request/response bodies by default (security risk). Add a configuration property to enable body logging for debugging. Register the filter as a `@Bean` in a shared configuration class. Remove ad-hoc `log.info()` calls from individual controller methods. Ensure sensitive headers (Authorization, Cookie) are never logged. Open a PR.

---

### 3.8 Make Mapper Classes Spring-Managed Beans (GAP-04)

**Priority**: P3 - Minor Spring convention issue
**Effort**: Small

**Devin Prompt**:
> Refactor all mapper classes to be Spring-managed components. Add `@Component` annotation to `BankAccountMapper`, `UtilityAccountMapper`, `UserMapper` (in core-banking and user-service), `FundTransferMapper`, and `UtilityPaymentMapper`. In each service class, replace `private XMapper mapper = new XMapper()` with `private final XMapper mapper` injected via constructor (using `@RequiredArgsConstructor`). Verify all tests still pass. Open a PR.

---

### 3.9 Standardize Package Structure (GAP-03)

**Priority**: P3 - Minor inconsistency
**Effort**: Small

**Devin Prompt**:
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.exception`, `com.javatodev.finance.configuration`. Move files that don't follow this pattern. In core-banking-service, move `repository/` to be at the same level as other services. Ensure all imports are updated and services compile. Open a PR.

---

## Summary

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 13 items | 1-2 weeks | Fix critical bugs, add validation, secure credentials, proper error handling, basic resilience |
| **Phase 2** | 9 items | 3-6 weeks | Circuit breakers, compensation logic, auth on all services, shared library, structured logging |
| **Phase 3** | 9 items | 6-12 weeks | Comprehensive tests, contract tests, REST conventions, vulnerability scanning |

### Critical Path

The most impactful sequence to execute:

1. **GAP-37** (balance bug) - Immediate data integrity fix
2. **GAP-14** (input validation) - Prevent garbage data
3. **GAP-05/06/07** (error handling) - Proper status codes and no leaks
4. **GAP-31** (circuit breakers) - Prevent cascading failures
5. **GAP-34** (compensation) - Handle orphaned transactions
6. **GAP-15** (downstream auth) - Close security hole
7. **GAP-10** (unit tests) - Foundation for safe future changes
