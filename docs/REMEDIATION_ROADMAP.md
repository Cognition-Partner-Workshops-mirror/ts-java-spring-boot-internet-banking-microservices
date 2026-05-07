# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to kick off the remediation.

---

## Phase 1: Quick Wins (Critical/High severity, Small effort)

These items address the most impactful issues with minimal code changes. Target: **1–2 weeks.**

### 1.1 Fix Exception Handler Leaking Internals (GAP-ERR-02)

**What:** The catch-all `@ExceptionHandler(Exception.class)` in all 4 services returns `"Exception occur inside API " + e`, exposing stack traces and SQL errors to clients.

**Fix:** Replace the catch-all handler with a generic error response that logs the full exception server-side but returns only a safe message to the client.

**Devin prompt:**
> In each of the 4 business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update the `GlobalExceptionHandler` class. Change the catch-all `@ExceptionHandler({Exception.class})` method to: (1) log the full exception at ERROR level with `log.error("Unhandled exception", e)`, and (2) return `ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred. Please try again later.").build())`. Add `@Slf4j` to the class if not already present. Do not change the `SimpleBankingGlobalException` handler.

---

### 1.2 Use Correct HTTP Status Codes (GAP-ERR-01)

**What:** All business exceptions return HTTP 400. `EntityNotFoundException` should return 404; `InsufficientFundsException` should return 422.

**Fix:** Add specific `@ExceptionHandler` methods for each exception type with appropriate HTTP status codes.

**Devin prompt:**
> In the `GlobalExceptionHandler` of each business service, add separate exception handler methods: (1) `@ExceptionHandler(EntityNotFoundException.class)` returning `ResponseEntity.status(HttpStatus.NOT_FOUND)` with an `ErrorResponse` body, and (2) `@ExceptionHandler(InsufficientFundsException.class)` returning `ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)` with an `ErrorResponse` body. In services that don't have these exception classes (fund-transfer, utility-payment), create them mirroring the core-banking-service pattern with appropriate error codes.

---

### 1.3 Secure Actuator Endpoints (GAP-SEC-04)

**What:** All actuator endpoints are publicly accessible through the API Gateway, potentially exposing sensitive configuration and heap dumps.

**Fix:** Restrict actuator access to only the `/health` endpoint publicly; require authentication for all others.

**Devin prompt:**
> In `internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/security/SecurityConfiguration.java`, update the security configuration to only allow unauthenticated access to actuator health endpoints. Change the actuator `pathMatchers` from `"/actuator/**"` to `"/actuator/health"`, and similarly for each service prefix (e.g., `"/user/actuator/health"` instead of `"/user/actuator/**"`). All other actuator endpoints should require authentication via the existing `.anyExchange().authenticated()` rule.

---

### 1.4 Add Input Validation to Request DTOs (GAP-SEC-01)

**What:** No request DTO has any validation annotations. Null accounts, negative amounts, and empty strings are accepted.

**Fix:** Add Jakarta Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Devin prompt:**
> Add Jakarta Bean Validation to all request DTOs across all services. For each service: (1) Add `spring-boot-starter-validation` to `build.gradle` if not already present. (2) In `FundTransferRequest`: add `@NotBlank` to `fromAccount` and `toAccount`, `@NotNull @Positive` to `amount`. (3) In `UtilityPaymentRequest`: add `@NotNull` to `providerId`, `@NotNull @Positive` to `amount`, `@NotBlank` to `referenceNumber` and `account`. (4) In the User service `User` DTO used for registration: add `@NotBlank @Email` to `email`, `@NotBlank` to `identification`, `@NotBlank @Size(min=6)` to `password`. (5) In `UserUpdateRequest`: add `@NotNull` to `status`. (6) Add `@Valid` annotation to `@RequestBody` parameters in all controllers. (7) Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns 400 with field-level error details.

---

### 1.5 Configure Feign Timeouts (GAP-RES-03)

**What:** No explicit timeouts on Feign clients, database connections, or Keycloak calls.

**Fix:** Add Feign timeout configuration in `application.yml` for each service.

**Devin prompt:**
> For the three services that use Feign clients (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), add Feign timeout configuration to their `application.yml` files. Add the following properties: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add `spring.cloud.openfeign.client.config.core-banking-service.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 15000` for the specific core-banking-service client.

---

### 1.6 Add Feign Retry Policies (GAP-RES-02)

**What:** No retry configuration for transient failures on inter-service calls.

**Fix:** Add a Spring Retry-based Feign retryer bean.

**Devin prompt:**
> For all three Feign-using services (user-service, fund-transfer-service, utility-payment-service): (1) Add `spring-retry` dependency to `build.gradle`. (2) Create a `FeignRetryConfiguration` class annotated with `@Configuration` that defines a `@Bean Retryer` returning `new Retryer.Default(100, 1000, 3)` (100ms initial interval, 1s max interval, 3 max attempts). (3) Register this configuration in each Feign client's `configuration` attribute. Make sure POST requests for fund transfers and payments are NOT retried (only GET requests should be retried) to avoid double-processing.

---

### 1.7 Fix Raw ResponseEntity Types (GAP-ERR-05)

**What:** Controllers return `ResponseEntity` without type parameters, suppressing compile-time checks.

**Fix:** Parameterize all `ResponseEntity` return types.

**Devin prompt:**
> Across all 4 business services, update every controller method to use parameterized `ResponseEntity<T>` instead of raw `ResponseEntity`. For example, `public ResponseEntity getBankAccount(...)` should become `public ResponseEntity<BankAccount> getBankAccount(...)`. For list endpoints, use `ResponseEntity<List<T>>`. For POST endpoints returning response DTOs, use the appropriate response type (e.g., `ResponseEntity<FundTransferResponse>`). Ensure all necessary imports are added.

---

### 1.8 Fix Logging Concatenation Anti-Pattern (GAP-OBS-01 — partial)

**What:** Some log statements use string concatenation instead of SLF4J placeholders, and sensitive data is logged.

**Fix:** Replace concatenation with placeholders; avoid logging full request bodies.

**Devin prompt:**
> In `FundTransferService.java`, change `log.info("Sending fund transfer request {}" + request.toString())` to `log.info("Sending fund transfer request from {} to {}", request.getFromAccount(), request.getToAccount())`. Review all other `log.info` and `log.error` calls across all services. Replace any `+ request.toString()` or `+ e` patterns with SLF4J `{}` placeholders. Remove logging of sensitive fields (full amounts, account numbers) at INFO level — move these to DEBUG level instead. Ensure all exception logging uses `log.error("message", exception)` format to preserve stack traces.

---

## Phase 2: Important (High/Medium severity, Medium effort)

These items require more substantial changes but are critical for production readiness. Target: **3–6 weeks.**

### 2.1 Add Circuit Breakers (GAP-RES-01)

**What:** No circuit breakers on inter-service Feign calls. A Core Banking failure cascades to all services.

**Devin prompt:**
> Add Resilience4j circuit breakers to all Feign clients. For each of the three Feign-using services: (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle`. (2) Enable Feign circuit breaker integration with `spring.cloud.openfeign.circuitbreaker.enabled: true` in `application.yml`. (3) Create a fallback class for each Feign client that implements the client interface and returns appropriate error responses (e.g., `FundTransferResponse` with a "Service temporarily unavailable" message). (4) Register the fallback in the `@FeignClient` annotation using the `fallback` attribute. (5) Configure circuit breaker properties: `resilience4j.circuitbreaker.instances.core-banking-service.slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 30s`.

---

### 2.2 Add Feign Error Decoder to Fund Transfer and Utility Payment (GAP-ERR-04)

**What:** Only User Service has a `CustomFeignErrorDecoder`. Fund Transfer and Utility Payment services lack Feign error handling.

**Devin prompt:**
> Copy the `CustomFeignErrorDecoder` pattern from `internet-banking-user-service` to both `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. For each service: (1) Create `configuration/feign/CustomFeignErrorDecoder.java` matching the user-service implementation. (2) Update the existing `CustomFeignClientConfiguration` class to extend `FeignClientProperties.FeignClientConfiguration` and register the error decoder as a `@Bean`. (3) Remove the existing `Logger.Level` bean from fund-transfer's `CustomFeignClientConfiguration` (or keep it alongside the error decoder). (4) Update the `@FeignClient` annotation's `configuration` to reference the updated configuration class.

---

### 2.3 Extract Shared Library (GAP-ORG-02)

**What:** `BaseMapper`, `AuditAware`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `AppAuthUserFilter`, and related classes are duplicated across services.

**Devin prompt:**
> Create a new Gradle subproject called `banking-common` at the repository root. (1) Create `banking-common/build.gradle` as a plain Java library (no Spring Boot plugin, just `java-library` plugin) with dependencies on `spring-boot-starter-data-jpa`, `spring-boot-starter-web`, `lombok`, and `jakarta.validation-api`. (2) Move the following classes into `banking-common/src/main/java/com/javatodev/finance/common/`: `BaseMapper`, `AuditAware`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`. (3) Create a root `settings.gradle` that includes `banking-common` and all 6 service projects. (4) Add `implementation project(':banking-common')` to each service's `build.gradle`. (5) Update imports in all services to reference the common package. (6) Delete the duplicated files from each service.

---

### 2.4 Add Role-Based Access Control (GAP-SEC-03)

**What:** Any authenticated user can perform any operation, including admin actions like approving users.

**Devin prompt:**
> Implement role-based access control: (1) In Keycloak, define two realm roles: `ROLE_USER` and `ROLE_ADMIN` in the realm-export.json. (2) In the API Gateway's `SecurityConfiguration`, add role-based rules: `PATCH /user/api/v1/bank-users/update/**` requires `ROLE_ADMIN`, `GET /user/api/v1/bank-users` (list all users) requires `ROLE_ADMIN`, all fund transfer and utility payment endpoints require `ROLE_USER`. (3) Alternatively, push authorization to individual services by adding `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to each business service's `build.gradle`, then add `@PreAuthorize("hasRole('ADMIN')")` to admin-only controller methods. Document the chosen approach in the README.

---

### 2.5 Add Prometheus Metrics (GAP-OBS-03)

**What:** No Prometheus registry despite Prometheus being listed in the tech stack.

**Devin prompt:**
> For all 6 services: (1) Add `io.micrometer:micrometer-registry-prometheus` to each `build.gradle`. (2) In each service's `application.yml`, ensure the Prometheus actuator endpoint is exposed: `management.endpoints.web.exposure.include: health,info,prometheus,metrics`. (3) Create a `docker-compose/prometheus.yml` configuration file with scrape targets for all services. (4) Add a Prometheus container to `docker-compose.yml` mounting this config file. (5) Optionally add a Grafana container with a pre-configured Spring Boot dashboard.

---

### 2.6 Add Pagination Metadata to List Endpoints (GAP-API-02)

**What:** List endpoints return `List<T>` without total count, page number, or page size.

**Devin prompt:**
> Create a generic `PageResponse<T>` wrapper class in the shared library (or in each service if no shared library exists yet) with fields: `List<T> content`, `int pageNumber`, `int pageSize`, `long totalElements`, `int totalPages`, `boolean last`. Update all list endpoints across all services to return `PageResponse<T>` instead of `List<T>`. In each service method, use the Spring `Page<T>` returned by the repository's `findAll(pageable)` to populate the `PageResponse` fields. Update controller return types accordingly.

---

### 2.7 Fix the Balance Double-Subtraction Bug

**What:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` has already been reduced, causing double deduction.

**Devin prompt:**
> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the balance calculation bug. In the `internalFundTransfer` method: (1) For the from-account, change `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())` (since actualBalance was already reduced). (2) Apply the same fix for the to-account's availableBalance. (3) In the `utilPayment` method, apply the same fix: after setting `actualBalance`, set `availableBalance` to the new `actualBalance` value, not `actualBalance.subtract(amount)` again. (4) Add unit tests covering balance calculations before and after transfers to verify the fix.

---

### 2.8 Add a Standard Response Envelope (GAP-API-06)

**What:** Successful responses return raw DTOs while errors return `ErrorResponse`. No consistent wrapper.

**Devin prompt:**
> Create an `ApiResponse<T>` wrapper class with fields: `boolean success`, `T data`, `ErrorResponse error`, `String timestamp`, `String path`. For successful responses, `success=true`, `data` contains the DTO, `error=null`. For error responses, `success=false`, `data=null`, `error` contains the error details. Update all controller methods to wrap their return values in `ApiResponse.success(data)`. Update all `GlobalExceptionHandler` methods to return `ApiResponse.error(errorResponse)`. Add a utility method `ApiResponse.success(T data)` and `ApiResponse.error(String code, String message)` for convenience.

---

### 2.9 Fix OpenAPI Dependency (GAP-API-05)

**What:** Business services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC (servlet) applications.

**Devin prompt:**
> In the `build.gradle` of `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`, replace the dependency `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`. Verify that the Swagger UI is accessible at `/swagger-ui.html` for each service. Add `springdoc.api-docs.path=/v3/api-docs` and `springdoc.swagger-ui.path=/swagger-ui.html` to each service's `application.yml` if not already configured.

---

## Phase 3: Polish (Medium/Low severity, or Large effort)

These items improve long-term maintainability and operational maturity. Target: **6–12 weeks.**

### 3.1 Add Unit Tests for All Services (GAP-TEST-01)

**What:** Fund Transfer, Utility Payment, and User services have no meaningful tests.

**Devin prompt:**
> For each of the three under-tested services (internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service): (1) Add Mockito dependencies if not present. (2) Create unit tests for each service class mirroring the pattern in core-banking-service tests. For `FundTransferService`: test successful transfer, test Feign client failure handling, test mapper conversions. For `UtilityPaymentService`: test successful payment, test error scenarios. For `UserService`: test user creation (mock KeycloakUserService and BankingCoreRestClient), test user update with status APPROVED (verify Keycloak enable), test duplicate email rejection, test invalid identification. Target at least 80% line coverage for service classes.

---

### 3.2 Add Integration Tests (GAP-TEST-02)

**What:** No integration tests exist. API endpoints are untested end-to-end.

**Devin prompt:**
> For each business service, create integration tests using Spring Boot Test with Testcontainers: (1) Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to each service's `build.gradle` `testImplementation`. (2) Create a base test class that starts a MySQL Testcontainer and configures `spring.datasource` properties. (3) For `core-banking-service`: create `AccountControllerIntegrationTest` using `@SpringBootTest(webEnvironment = RANDOM_PORT)` and `TestRestTemplate` — test GET account, POST fund transfer with real database. (4) For `internet-banking-fund-transfer-service`: use WireMock to mock the core-banking-service Feign calls, then test POST transfer and GET transfers endpoints. (5) For user-service: mock Keycloak and core-banking-service, test registration and update flows.

---

### 3.3 Add Contract Tests (GAP-TEST-03)

**What:** No consumer-driven contract tests between services.

**Devin prompt:**
> Add Spring Cloud Contract tests between services. (1) In `core-banking-service` (the provider), add `spring-cloud-starter-contract-verifier` to `build.gradle`. Create contract stubs in `src/test/resources/contracts/` for: GET `/api/v1/account/bank-account/{number}`, POST `/api/v1/transaction/fund-transfer`, POST `/api/v1/transaction/util-payment`, GET `/api/v1/user/{identification}`. (2) Generate the contract test base class and verify contracts pass. (3) In `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` (consumers), add `spring-cloud-contract-stub-runner` and write consumer-side tests that validate their Feign clients work with the provider's stubs. This ensures API changes in core-banking-service are caught before deployment.

---

### 3.4 Set Up CI/CD Pipeline

**What:** No CI/CD pipeline exists. Builds and deployments are fully manual.

**Devin prompt:**
> Create a GitHub Actions CI/CD pipeline. Create `.github/workflows/ci.yml` with: (1) A `build` job that runs on `ubuntu-latest` with Java 21, iterates over each service directory, runs `./gradlew build` in each. (2) A `test` job that runs `./gradlew test` in each service directory and uploads test reports as artifacts. (3) A `docker-build` job (triggered on main branch only) that builds Docker images for each service using their Dockerfiles and pushes to GitHub Container Registry. (4) Add a `lint` job that runs Checkstyle or SpotBugs (choose one) across all services. Ensure the workflow runs on pull requests and pushes to main.

---

### 3.5 Implement Saga Pattern for Distributed Transactions (GAP-RES-05)

**What:** Fund transfer and utility payment flows span two services with no distributed consistency guarantees.

**Devin prompt:**
> Implement a choreography-based saga pattern for fund transfers: (1) Add `spring-boot-starter-amqp` (RabbitMQ) to fund-transfer-service and core-banking-service `build.gradle`. (2) In fund-transfer-service: after saving the `PENDING` entity, publish a `FundTransferRequested` event to a RabbitMQ exchange. (3) In core-banking-service: consume the event, process the transfer, and publish either `FundTransferCompleted` or `FundTransferFailed`. (4) In fund-transfer-service: consume the result event and update the entity status accordingly. (5) Add an idempotency key (UUID) to `FundTransferRequest` to prevent duplicate processing. (6) Add a compensation handler: if `FundTransferFailed` is received, update the entity to `FAILED` status. (7) Add RabbitMQ to `docker-compose.yml`. (8) Apply the same pattern to utility-payment-service.

---

### 3.6 Externalize Secrets (GAP-SEC-02)

**What:** Database passwords, Keycloak credentials, and client secrets are hardcoded in Docker Compose and source files.

**Devin prompt:**
> Externalize all secrets from the codebase: (1) In `docker-compose.yml`, replace all hardcoded passwords with environment variable references: `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`, `KC_DB_PASSWORD: ${KC_DB_PASSWORD}`, etc. (2) Create a `docker-compose/.env.example` file listing all required variables with placeholder values. (3) Add `docker-compose/.env` to `.gitignore`. (4) In `privileges.sql`, use an init script that reads passwords from environment variables. (5) Move the Keycloak client secret from test `application.yml` to the Config Server's externalized configuration. (6) Add a note to the README about setting up the `.env` file before running Docker Compose.

---

### 3.7 Add Rate Limiting to API Gateway (GAP-RES-06)

**What:** No rate limiting on the API Gateway.

**Devin prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` to the API Gateway's `build.gradle`. (2) Add a Redis container to `docker-compose.yml`. (3) Configure a `RedisRateLimiter` bean with `replenishRate: 10` and `burstCapacity: 20` (10 requests/second sustained, 20 burst). (4) Apply the rate limiter filter to all routes in the gateway configuration (via Config Server or local application.yml). (5) Use the JWT subject claim as the key resolver so rate limits are per-user. (6) Return HTTP 429 (Too Many Requests) when the limit is exceeded.

---

### 3.8 Unify Gradle Build (GAP-ORG-01)

**What:** No multi-project build. Each service is fully independent.

**Devin prompt:**
> Convert the project to a Gradle multi-project build: (1) Create a root `settings.gradle` that includes all 6 service projects and the shared library. (2) Create a root `build.gradle` with shared configuration: Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common dependencies, common test configuration. (3) Simplify each service's `build.gradle` to only declare service-specific dependencies (remove duplicated plugin and dependency management blocks). (4) Remove individual `gradlew` wrappers from each service; keep only the root wrapper. (5) Verify `./gradlew build` from the root builds all services. (6) Update Dockerfiles to reference JARs from the new build output paths.

---

### 3.9 Add Structured JSON Logging (GAP-OBS-01 — complete)

**What:** No structured logging format configured.

**Devin prompt:**
> Add structured JSON logging to all services: (1) Add `ch.qos.logback.contrib:logback-json-classic:0.1.5` and `ch.qos.logback.contrib:logback-jackson:0.1.5` to each service's `build.gradle`. (2) Create a shared `logback-spring.xml` that uses `JsonLayout` for production profile and standard pattern layout for local development. (3) Include trace ID and span ID in the JSON output using Micrometer's MDC integration. (4) Place the `logback-spring.xml` in each service's `src/main/resources/`. (5) Verify JSON output appears when running with the `docker` profile.

---

## Priority Matrix

| Phase | ID | Gap | Severity | Effort | Description |
|---|---|---|---|---|---|
| **1** | GAP-ERR-02 | Exception handler leaks internals | Critical | Small | Stop exposing stack traces |
| **1** | GAP-ERR-01 | All errors return HTTP 400 | High | Small | Correct HTTP status codes |
| **1** | GAP-SEC-04 | Actuator endpoints public | High | Small | Restrict actuator access |
| **1** | GAP-SEC-01 | No input validation | Critical | Medium | Add Jakarta Validation |
| **1** | GAP-RES-03 | No timeout configuration | High | Small | Configure Feign timeouts |
| **1** | GAP-RES-02 | No retry policies | High | Small | Add Feign retries |
| **1** | GAP-ERR-05 | Raw ResponseEntity types | Medium | Small | Parameterize return types |
| **1** | GAP-OBS-01 | Logging issues (partial) | Medium | Small | Fix log concatenation |
| **2** | GAP-RES-01 | No circuit breakers | Critical | Medium | Add Resilience4j |
| **2** | GAP-ERR-04 | Missing Feign error decoders | High | Medium | Add error decoders |
| **2** | GAP-ORG-02 | Duplicated code | High | Medium | Extract shared library |
| **2** | GAP-SEC-03 | No role-based access | High | Medium | Add RBAC |
| **2** | GAP-OBS-03 | No Prometheus metrics | Medium | Medium | Add metrics registry |
| **2** | GAP-API-02 | No pagination metadata | Medium | Small | Add PageResponse wrapper |
| **2** | — | Balance double-subtraction bug | Critical | Small | Fix math bug |
| **2** | GAP-API-06 | No response envelope | Medium | Medium | Add ApiResponse wrapper |
| **2** | GAP-API-05 | Wrong OpenAPI dependency | Medium | Small | Fix springdoc artifact |
| **3** | GAP-TEST-01 | No test coverage (3 services) | Critical | Large | Write unit tests |
| **3** | GAP-TEST-02 | No integration tests | High | Large | Add Testcontainers tests |
| **3** | GAP-TEST-03 | No contract tests | Medium | Large | Add Spring Cloud Contract |
| **3** | — | No CI/CD pipeline | High | Medium | Create GitHub Actions |
| **3** | GAP-RES-05 | No distributed tx safety | Critical | Large | Implement saga pattern |
| **3** | GAP-SEC-02 | Hardcoded credentials | Critical | Small | Externalize secrets |
| **3** | GAP-RES-06 | No rate limiting | Medium | Small | Add Redis rate limiter |
| **3** | GAP-ORG-01 | No multi-project build | Medium | Medium | Unify Gradle |
| **3** | GAP-OBS-01 | Logging (complete) | Medium | Small | Add JSON logging |
