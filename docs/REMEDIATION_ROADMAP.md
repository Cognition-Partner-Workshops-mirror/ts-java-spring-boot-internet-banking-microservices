# Remediation Roadmap

This roadmap prioritizes the gaps identified in [`GAP_ANALYSIS.md`](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical Fixes & Small Effort)

These items address critical security and correctness issues that require minimal effort. They should be completed before any production deployment.

### 1.1 Fix Balance Calculation Bug (GAP-RE-06)

**Severity:** Critical | **Effort:** Small

**Problem:** `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` has already been reduced, resulting in double-deduction of the available balance.

**Remediation:** Fix the two methods in `TransactionService` so `availableBalance` is set to the new `actualBalance` (not `actualBalance - amount` again).

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is being double-deducted. After updating `actualBalance`, set `availableBalance` equal to the new `actualBalance` instead of subtracting the amount again. Update the existing unit tests if needed and ensure all tests pass.

---

### 1.2 Stop Leaking Exception Details (GAP-EH-02)

**Severity:** Critical | **Effort:** Small

**Problem:** The generic `Exception` catch-all handler in all four `GlobalExceptionHandler` classes returns `"Exception occur inside API " + e`, exposing stack traces and internal class names to clients.

**Remediation:** Return a generic error response with a safe message and log the full exception server-side.

**Devin Prompt:**
> In all four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update the `GlobalExceptionHandler.handleException()` method to: (1) log the full exception at ERROR level, (2) return HTTP 500 with a generic `ErrorResponse { code: "INTERNAL_ERROR", message: "An unexpected error occurred" }` instead of exposing the exception details. Do not change the `handleGlobalException` method.

---

### 1.3 Externalize Hardcoded Credentials (GAP-SE-01)

**Severity:** Critical | **Effort:** Small

**Problem:** Database passwords, Keycloak admin credentials, and test credentials are hardcoded in `docker-compose.yml`, `Dockerfile`, and `privileges.sql`.

**Remediation:** Replace hardcoded values with environment variable references and provide a `.env.example` template.

**Devin Prompt:**
> Externalize all hardcoded credentials in the docker-compose directory. Replace hardcoded passwords in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `docker-compose/.env.example` file with placeholder values and add `docker-compose/.env` to `.gitignore`. Remove the test credentials from `README.md` and reference the `.env.example` file instead.

---

### 1.4 Add Input Validation (GAP-SE-02)

**Severity:** Critical | **Effort:** Medium

**Problem:** No Bean Validation annotations on any request DTOs. Null, negative, or empty values are accepted without checks.

**Remediation:** Add `spring-boot-starter-validation` dependency and annotate all request DTOs. Add `@Valid` to controller method parameters.

**Devin Prompt:**
> Add input validation across all business services. (1) Add `org.springframework.boot:spring-boot-starter-validation` to each service's `build.gradle`. (2) Add Bean Validation annotations to all request DTOs: `FundTransferRequest` (fromAccount @NotBlank, toAccount @NotBlank, amount @NotNull @Positive), `UtilityPaymentRequest` (providerId @NotNull, amount @NotNull @Positive, referenceNumber @NotBlank, account @NotBlank), User service `User` DTO (email @NotBlank @Email, identification @NotBlank, password @NotBlank), `UserUpdateRequest` (status @NotNull). (3) Add `@Valid` annotation to all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details.

---

### 1.5 Fix HTTP Status Codes (GAP-EH-01)

**Severity:** High | **Effort:** Small

**Problem:** All exceptions return HTTP 400 regardless of the actual error type.

**Remediation:** Map exception types to appropriate HTTP status codes.

**Devin Prompt:**
> Update the `GlobalExceptionHandler` in all four business services to return appropriate HTTP status codes. `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `UserAlreadyRegisteredException` should return 409, `InvalidEmailException` and `InvalidBankingUserException` should return 400, and the generic `Exception` handler should return 500. Add separate `@ExceptionHandler` methods for each exception type instead of relying on the `SimpleBankingGlobalException` catch-all.

---

### 1.6 Add Feign Timeout Configuration (GAP-RE-03)

**Severity:** High | **Effort:** Small

**Problem:** No timeouts configured for Feign clients, database connections, or Keycloak client.

**Remediation:** Add Feign timeout configuration to each service's `application.yml`.

**Devin Prompt:**
> Add timeout configuration for all Feign clients. In each business service that uses Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), add the following to `application.yml`: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add connection pool timeouts for the MySQL datasource using `spring.datasource.hikari.connection-timeout: 30000` and `spring.datasource.hikari.maximum-pool-size: 10`.

---

### 1.7 Add Feign Error Decoders (GAP-EH-04)

**Severity:** High | **Effort:** Medium

**Problem:** Fund-transfer and utility-payment services have no Feign error decoder; errors from core-banking propagate as raw exceptions.

**Remediation:** Port the user-service's `CustomFeignErrorDecoder` to the other two services.

**Devin Prompt:**
> Add proper Feign error handling to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Port the `CustomFeignErrorDecoder` from internet-banking-user-service to both services. Update their `CustomFeignClientConfiguration` classes to register the error decoder bean. Ensure the error decoder maps 400 responses to `SimpleBankingGlobalException`, 404 to `EntityNotFoundException`, and other errors to appropriate exceptions.

---

### 1.8 Fix OpenAPI Starter Dependency (GAP-AD-05)

**Severity:** Medium | **Effort:** Small

**Problem:** Business services use `springdoc-openapi-starter-webflux-ui` but are servlet-based (Spring MVC), not WebFlux.

**Devin Prompt:**
> In all four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` in their `build.gradle` files. Verify the services still compile successfully.

---

### 1.9 Fix Context-Load Tests (GAP-TE-04)

**Severity:** Medium | **Effort:** Small

**Problem:** `*ApplicationTests` classes try to load the full Spring context requiring infrastructure that isn't available in CI.

**Devin Prompt:**
> Fix the context-load tests across all services so they can run without external infrastructure. For each `*ApplicationTests.java` file, add `@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})` and appropriate test-profile configuration. For services that use a database, ensure the H2 in-memory database is used in tests by adding a `src/test/resources/application.yml` with `spring.datasource.url: jdbc:h2:mem:testdb` and `spring.jpa.hibernate.ddl-auto: create-drop`. For the user-service, mock the Keycloak dependency in tests.

---

### 1.10 Fix Keycloak Singleton Thread-Safety (GAP-SE-05)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/.../configuration/keycloak/KeycloakProperties.java`. Replace the static lazy singleton pattern with a proper Spring `@Bean` method that returns a `Keycloak` instance. Create the bean in a `@Configuration` class so Spring manages the lifecycle and thread-safety. Remove the static `keycloakInstance` field.

---

## Phase 2: Important Improvements (High-Impact, Medium Effort)

These items significantly improve reliability, maintainability, and security. They should be addressed before scaling or onboarding new developers.

### 2.1 Add Circuit Breakers (GAP-RE-01)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breakers to all inter-service Feign calls. (1) Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service `build.gradle` files. (2) Enable Feign circuit breaker support with `spring.cloud.openfeign.circuitbreaker.enabled: true` in each service's `application.yml`. (3) Configure circuit breaker defaults: `resilience4j.circuitbreaker.configs.default.sliding-window-size: 10`, `failure-rate-threshold: 50`, `wait-duration-in-open-state: 10s`. (4) Create fallback classes for each Feign client that return meaningful error responses instead of propagating failures.

---

### 2.2 Create Shared Library Module (GAP-CO-02)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Create a shared library module to eliminate code duplication. (1) Create a root `settings.gradle` that includes all service projects plus a new `banking-common` module. (2) Move the following shared classes into `banking-common`: `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`. (3) Add `banking-common` as a dependency to each business service. (4) Remove the duplicated classes from each service and update imports. (5) Ensure all services compile and tests pass.

---

### 2.3 Secure Downstream Services (GAP-SE-04)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add service-level authentication to all downstream business services. (1) Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. (2) Configure each service to validate JWT tokens from Keycloak using `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`. (3) Allow actuator endpoints without auth. (4) Configure Feign clients to propagate the Authorization header from incoming requests to outgoing Feign calls using a `RequestInterceptor`.

---

### 2.4 Add Unit Tests for All Services (GAP-TE-01)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For each service: (1) Create service-layer tests using Mockito to mock repositories and Feign clients. (2) Test all happy paths and error paths (entity not found, validation failures, Feign errors). (3) For user-service, mock `KeycloakUserService` and test `createUser`, `updateUser`, `readUser`, and `readUsers`. (4) For fund-transfer-service, mock `BankingCoreFeignClient` and test `fundTransfer` and `readAllTransfers`. (5) For utility-payment-service, mock `BankingCoreRestClient` and test `utilPayment` and `readPayments`. Target at least 80% line coverage for service classes.

---

### 2.5 Add Structured Logging (GAP-OB-01)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Implement structured JSON logging across all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` to each business service's `build.gradle`. (2) Create a shared `logback-spring.xml` in each service's `src/main/resources/` that outputs JSON in production profile and plain text in development profile. Include fields: timestamp, level, logger, message, traceId, spanId, service name. (3) Replace all string-concatenation logging (`log.error("message" + e)`) with parameterized logging (`log.error("message {}", e.getMessage(), e)`). (4) Add MDC context for `X-Auth-Id` in the `AppAuthUserFilter`.

---

### 2.6 Add Prometheus Metrics (GAP-OB-03)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add Prometheus metrics support to all services. (1) Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`. (2) Configure actuator to expose the prometheus endpoint: `management.endpoints.web.exposure.include: health,info,prometheus,metrics` in each service's application config. (3) Add a `prometheus.yml` scrape configuration to the `docker-compose/` directory. (4) Optionally add a Prometheus container to `docker-compose-support-apps.yml`.

---

### 2.7 Return Pagination Metadata (GAP-AD-04)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Update all paginated endpoints to return pagination metadata. In every service that has paginated GET endpoints (core-banking UserController, user-service UserController, fund-transfer FundTransferController, utility-payment UtilityPaymentController), change the return type from `List<T>` to `Page<T>`. Update the service methods to return `Page<T>` instead of calling `.getContent()`. This gives clients access to `totalElements`, `totalPages`, `number`, `size`, and `numberOfElements`.

---

### 2.8 Add Retry Policies (GAP-RE-02)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add retry policies for Feign clients. In each service that uses Feign (user-service, fund-transfer-service, utility-payment-service), configure Spring Cloud OpenFeign retry: add `spring.cloud.openfeign.client.config.default.retryer: feign.Retryer.Default` with max 3 attempts and 1-second backoff. Only retry on connection errors and 503 responses — do not retry on 4xx errors to avoid duplicate fund transfers. Ensure idempotent operations (GET requests) are retried but non-idempotent operations (POST) use caution.

---

## Phase 3: Polish & Long-Term Improvements

These items improve developer experience, long-term maintainability, and operational maturity. They can be addressed incrementally.

### 3.1 Implement Saga Pattern for Fund Transfers (GAP-RE-05)

**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> Implement a saga pattern for the fund transfer flow to ensure data consistency across services. (1) Add a `TransactionStatus.FAILED` state to fund-transfer-service. (2) Wrap the Feign call in a try-catch; on failure, update the fund-transfer entity to `FAILED`. (3) Add an idempotency key (UUID) generated by the client and passed in the request to prevent duplicate transfers. (4) In core-banking-service, check for duplicate `transactionId` before processing. (5) Add a compensation endpoint in core-banking-service (`POST /api/v1/transaction/fund-transfer/{transactionId}/reverse`) that can reverse a completed transfer. (6) Document the failure modes and recovery procedures.

---

### 3.2 Add Integration Tests (GAP-TE-02)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add integration tests for all business services using Spring Boot Test and Testcontainers. (1) Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to each service's `build.gradle` test dependencies. (2) Create `@SpringBootTest` integration tests that start the full Spring context with a MySQL Testcontainer. (3) For services with Feign clients, use WireMock to stub the downstream service responses. (4) Test the full request lifecycle: HTTP request → controller → service → repository → response. (5) Include both happy-path and error scenarios.

---

### 3.3 Add Contract Tests (GAP-TE-03)

**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Add consumer-driven contract tests between services using Spring Cloud Contract. (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service (the provider). (2) Define contracts in `src/test/resources/contracts/` for each endpoint consumed by other services: fund-transfer read-account, fund-transfer fund-transfer, utility-payment read-account, utility-payment util-payment, user-service read-user. (3) Add `spring-cloud-starter-contract-stub-runner` to each consumer service's test dependencies. (4) Write consumer-side tests that verify the Feign client behavior matches the contract stubs.

---

### 3.4 Unify Gradle Build (GAP-CO-01)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Create a unified Gradle multi-project build. (1) Create a root `settings.gradle` that includes all 7 service projects and the shared library. (2) Create a root `build.gradle` with a `subprojects {}` block that configures common plugins, Java version, Spring Boot version, Spring Cloud version, and shared dependencies. (3) Simplify each service's `build.gradle` to only declare service-specific dependencies. (4) Verify `./gradlew build` from the root compiles all services and runs all tests.

---

### 3.5 Add Fallback Behavior (GAP-RE-04)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add fallback behavior for all Feign clients using Resilience4j. (1) Create a `@Component` fallback factory for each Feign client that returns a meaningful error response when the circuit is open or the call fails. For read operations (GET), return cached or default data where appropriate. For write operations (POST), return a response indicating the request has been queued for retry. (2) Register the fallback factories in the `@FeignClient` annotations. (3) Add tests for the fallback behavior.

---

### 3.6 Standardize Package Structure (GAP-CO-03)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all services. Adopt a consistent layout: `com.javatodev.finance.{controller, service, repository, model.entity, model.dto, model.mapper, configuration, exception}`. Move `model/repository/` to `repository/` in user-service and fund-transfer-service. Standardize Feign client packages to `service/rest/` across all services. Move Feign configuration to `configuration/feign/` in all services. Update all imports accordingly.

---

### 3.7 Use Spring-Managed Mappers (GAP-CO-04)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Refactor mappers to be Spring-managed beans. (1) Add `@Component` to all mapper classes (`UserMapper`, `BankAccountMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`). (2) Inject them via constructor injection in service classes instead of `new Mapper()`. (3) Alternatively, consider adopting MapStruct for compile-time type-safe mapping. Update all service classes and tests.

---

### 3.8 Add ResponseEntity Generics (GAP-AD-02)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Add generic type parameters to all `ResponseEntity` return types in controllers. For example, change `ResponseEntity getBankAccount(...)` to `ResponseEntity<BankAccount> getBankAccount(...)`. Do this for all controller methods across all services. This improves type safety and makes the auto-generated OpenAPI documentation more accurate.

---

### 3.9 Add Dependency Vulnerability Scanning (GAP-SE-06)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add OWASP Dependency Check to the Gradle build. (1) Add the `org.owasp.dependencycheck` Gradle plugin version 9.0.9 to each service's `build.gradle` (or to the root build if unified). (2) Configure it with `dependencyCheck { failBuildOnCVSS = 7.0 }` to fail the build on high-severity vulnerabilities. (3) Add a `./gradlew dependencyCheckAnalyze` step. (4) Document the process for reviewing and suppressing false positives.

---

### 3.10 Add Distributed Tracing to Logs (GAP-OB-04)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Ensure trace IDs appear in all log output. (1) Configure the logback pattern to include `%X{traceId}` and `%X{spanId}` in each service's `logback-spring.xml`. (2) Add tracing dependencies (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) to `internet-banking-service-registry` and `internet-banking-config-server` so they also participate in distributed tracing. (3) Verify end-to-end trace visibility in Zipkin for a fund-transfer request.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| Phase 1: Quick Wins | 10 | 4 | 3 | 3 | 0 |
| Phase 2: Important | 8 | 0 | 3 | 5 | 0 |
| Phase 3: Polish | 10 | 1 | 1 | 3 | 5 |
| **Total** | **28** | **5** | **7** | **11** | **5** |

> **Note:** Some gaps (e.g., GAP-SE-03 CSRF documentation, GAP-AD-01 URL consistency, GAP-AD-03 API versioning strategy) are documentation or design-decision items that do not require code changes and are omitted from this roadmap. They should be addressed as part of an ADR (Architecture Decision Record) process.

---

## Recommended Execution Order

```
Phase 1 (Week 1-2):
  1.1  Fix balance calculation bug          ← CRITICAL correctness fix
  1.2  Stop leaking exception details       ← CRITICAL security fix
  1.3  Externalize hardcoded credentials    ← CRITICAL security fix
  1.4  Add input validation                 ← CRITICAL security fix
  1.5  Fix HTTP status codes                ← improves API correctness
  1.6  Add Feign timeout configuration      ← prevents cascading hangs
  1.7  Add Feign error decoders             ← proper error propagation
  1.8  Fix OpenAPI starter dependency       ← corrects documentation
  1.9  Fix context-load tests               ← enables CI/CD
  1.10 Fix Keycloak singleton               ← prevents race condition

Phase 2 (Week 3-5):
  2.1  Add circuit breakers                 ← resilience
  2.2  Create shared library module         ← eliminates duplication
  2.3  Secure downstream services           ← defense in depth
  2.4  Add unit tests for all services      ← quality assurance
  2.5  Add structured logging               ← operational visibility
  2.6  Add Prometheus metrics               ← monitoring
  2.7  Return pagination metadata           ← API completeness
  2.8  Add retry policies                   ← resilience

Phase 3 (Week 6+):
  3.1  Implement saga pattern               ← data consistency
  3.2  Add integration tests                ← end-to-end confidence
  3.3  Add contract tests                   ← API compatibility
  3.4  Unify Gradle build                   ← developer experience
  3.5  Add fallback behavior                ← graceful degradation
  3.6  Standardize package structure        ← consistency
  3.7  Use Spring-managed mappers           ← clean architecture
  3.8  Add ResponseEntity generics          ← type safety
  3.9  Add dependency vulnerability scanning← supply chain security
  3.10 Add distributed tracing to logs      ← observability
```
