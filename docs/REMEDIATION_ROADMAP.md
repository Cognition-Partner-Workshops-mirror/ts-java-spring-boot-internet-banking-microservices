# Remediation Roadmap

## Overview

This roadmap organizes the 33 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): High-impact, low-effort fixes that address critical bugs and security issues.
- **Phase 2 — Important** (3-6 weeks): Structural improvements to error handling, resilience, testing, and API design.
- **Phase 3 — Polish** (6-12 weeks): Long-term quality improvements including comprehensive test coverage, contract testing, and advanced observability.

Each item includes a sample Devin prompt that can be used to implement the fix.

---

## Phase 1: Quick Wins

> High-impact fixes that can be completed in small, focused PRs. Prioritized by risk to production correctness and security.

### 1.1 Fix Balance Calculation Bug (GAP-RES-06)

**Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`. This causes available balance to drift after every transaction.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been reduced. The `availableBalance` should be set equal to the new `actualBalance` (not subtracted again). Apply the same fix for both debit operations in both methods. Add unit tests to verify the correct balance after a transfer.

---

### 1.2 Stop Leaking Exception Details (GAP-ERR-02)

**Severity:** High | **Effort:** Small

The generic exception handler returns `"Exception occur inside API " + e`, exposing stack traces to API consumers.

**Devin Prompt:**
> In all four services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update the `GlobalExceptionHandler.handleException()` method to return a generic error message like `"An unexpected error occurred. Please contact support."` with a unique error code. Log the full exception server-side at ERROR level but do not include it in the response body. Return HTTP 500 instead of 400 for unhandled exceptions.

---

### 1.3 Use Correct HTTP Status Codes (GAP-ERR-01)

**Severity:** High | **Effort:** Small

All exceptions currently return HTTP 400. Entity-not-found should be 404, insufficient funds should be 422, etc.

**Devin Prompt:**
> Update the `GlobalExceptionHandler` in all four services to return appropriate HTTP status codes: `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `UserAlreadyRegisteredException` should return 409, `InvalidEmailException` and `InvalidBankingUserException` should return 400, and the generic `Exception` catch-all should return 500. Keep the `ErrorResponse` body structure consistent.

---

### 1.4 Add Request Body Validation (GAP-ERR-04)

**Severity:** High | **Effort:** Small

No validation annotations exist on request DTOs.

**Devin Prompt:**
> Add Jakarta Validation to all request DTOs across the project. Add `spring-boot-starter-validation` to each service's `build.gradle`. Annotate `FundTransferRequest` fields: `@NotBlank fromAccount`, `@NotBlank toAccount`, `@NotNull @Positive amount`. Annotate `UtilityPaymentRequest` fields: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank referenceNumber`, `@NotBlank account`. Annotate the User registration DTO: `@NotBlank @Email email`, `@NotBlank identification`, `@NotBlank @Size(min=8) password`. Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details.

---

### 1.5 Externalize Docker Compose Secrets (GAP-SEC-01)

**Severity:** Critical | **Effort:** Small

Credentials are hardcoded in `docker-compose.yml` and `privileges.sql`.

**Devin Prompt:**
> Replace all hardcoded credentials in `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `docker-compose/.env.example` file documenting all required variables with placeholder values. Update `docker-compose/mysql/privileges.sql` to use environment variable substitution or document that the password must be changed. Add `.env` to `.gitignore`. Update the README with instructions to copy `.env.example` to `.env` and fill in values.

---

### 1.6 Fix Keycloak Singleton Thread Safety (GAP-SEC-04)

**Severity:** Medium | **Effort:** Small

The Keycloak client singleton in `KeycloakProperties` has a race condition.

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the lazy singleton with a `@Bean` method in a `@Configuration` class that creates the `Keycloak` instance once during Spring context initialization. Remove the static field and the manual null-check pattern.

---

### 1.7 Fix OpenAPI Dependency (GAP-API-03)

**Severity:** Medium | **Effort:** Small

Services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC applications.

**Devin Prompt:**
> In core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace the `springdoc-openapi-starter-webflux-ui:2.1.0` dependency with `springdoc-openapi-starter-webmvc-ui:2.1.0` since these services use Spring MVC (not WebFlux). Verify that `/swagger-ui.html` loads correctly for each service.

---

### 1.8 Add Typed ResponseEntity (GAP-API-01)

**Severity:** Medium | **Effort:** Small

Controllers use raw `ResponseEntity` without type parameters.

**Devin Prompt:**
> Update all controller methods across core-banking-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service to use typed `ResponseEntity<T>` (e.g., `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`, `ResponseEntity<FundTransferResponse>`). The internet-banking-user-service already uses typed ResponseEntity in some methods — make it consistent there too.

---

### 1.9 Add Pagination Metadata (GAP-API-05)

**Severity:** Medium | **Effort:** Small

List endpoints discard Spring Data's `Page` metadata.

**Devin Prompt:**
> Update all paginated list endpoints to return Spring Data's `Page<T>` (or a custom `PageResponse<T>` wrapper with `content`, `totalElements`, `totalPages`, `pageNumber`, `pageSize`) instead of raw `List<T>`. In `UserService.readUsers()`, `FundTransferService.readAllTransfers()`, and `UtilityPaymentService.readPayments()`, return the full `Page` object instead of calling `.getContent()`.

---

### 1.10 Configure Feign Timeouts (GAP-RES-03)

**Severity:** High | **Effort:** Small

No timeout configuration exists for inter-service Feign calls.

**Devin Prompt:**
> Add default Feign client timeout configuration to all services that use OpenFeign (user-service, fund-transfer-service, utility-payment-service). Add the following to each service's application configuration (via Config Server or local `application.yml`): `feign.client.config.default.connectTimeout: 5000` and `feign.client.config.default.readTimeout: 10000`. Also configure the connection pool: `feign.okhttp.enabled: true` (for user-service which already has OkHttp dependency).

---

### 1.11 Add Dependency Vulnerability Scanning (GAP-SEC-05)

**Severity:** Medium | **Effort:** Small

No vulnerability scanning is configured.

**Devin Prompt:**
> Add the OWASP Dependency Check Gradle plugin to each service's `build.gradle`. Add `id 'org.owasp.dependencycheck' version '9.0.9'` to the plugins block. Configure it to fail the build on CVSS score >= 7. Add a `dependencyCheckAnalyze` task. Also create a `.github/dependabot.yml` configuration file to enable automated dependency updates for Gradle.

---

## Phase 2: Important

> Structural improvements requiring more design thought and cross-service coordination.

### 2.1 Add Feign Error Handling (GAP-ERR-03)

**Severity:** High | **Effort:** Medium

Failed Core Banking calls leave fund transfers in `PENDING` status forever.

**Devin Prompt:**
> Implement proper Feign error handling in internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Create a `CustomFeignErrorDecoder` (similar to the one in user-service) that decodes Core Banking error responses into appropriate exceptions. In `FundTransferService.fundTransfer()`, wrap the `bankingCoreFeignClient.fundTransfer()` call in a try-catch that updates the entity status to `FAILED` on any exception. Do the same for `UtilityPaymentService.utilPayment()`. Ensure the error message from Core Banking is preserved and returned to the caller.

---

### 2.2 Add Circuit Breakers (GAP-RES-01)

**Severity:** High | **Effort:** Medium

No circuit breaker protection for inter-service calls.

**Devin Prompt:**
> Add Resilience4j circuit breakers to the fund-transfer-service and utility-payment-service. Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's `build.gradle`. Configure a circuit breaker for the Core Banking Feign client with: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 30s`, `permittedNumberOfCallsInHalfOpenState: 3`. Add a fallback method that returns a response indicating the service is temporarily unavailable and sets the transaction status to `FAILED`. Add `@CircuitBreaker` annotations to the Feign client methods.

---

### 2.3 Add Retry Policies (GAP-RES-02)

**Severity:** High | **Effort:** Small

No retry logic for transient failures.

**Devin Prompt:**
> Add Spring Retry support to fund-transfer-service and utility-payment-service. Add `spring-retry` and `spring-boot-starter-aop` dependencies. Configure Feign retry for the Core Banking client: max 3 attempts with exponential backoff (initial interval 1s, multiplier 2, max interval 5s). Only retry on `5xx` responses and connection timeouts, NOT on `4xx` client errors. Ensure retries are safe given the idempotency characteristics of the endpoints.

---

### 2.4 Add Idempotency Protection (GAP-RES-05)

**Severity:** High | **Effort:** Medium

Duplicate fund transfers can be processed.

**Devin Prompt:**
> Add idempotency protection to the fund transfer and utility payment endpoints. Add an `idempotencyKey` field to `FundTransferRequest` and `UtilityPaymentRequest`. Add a unique constraint on `idempotency_key` in both the `fund_transfer` and `utility_payment` tables. Before processing, check if a record with the same idempotency key exists — if so, return the existing result. If no idempotency key is provided, generate one from a hash of `(fromAccount, toAccount, amount, timestamp-window)`. Add a Flyway migration for the schema change.

---

### 2.5 Secure Internal Services (GAP-SEC-06)

**Severity:** High | **Effort:** Medium

Core Banking Service has no authentication.

**Devin Prompt:**
> Add service-to-service authentication for Core Banking Service. Add `spring-boot-starter-security` to core-banking-service's `build.gradle`. Configure it to require a shared API key via a custom `X-Service-Key` header for all `/api/v1/**` endpoints. Expose actuator endpoints without authentication. In the OpenFeign client configuration classes (`CustomFeignClientConfiguration`) in fund-transfer-service, utility-payment-service, and user-service, add a `RequestInterceptor` that sets the `X-Service-Key` header. Store the API key in the Config Server properties.

---

### 2.6 Add Input Sanitization and Log Safety (GAP-SEC-03)

**Severity:** High | **Effort:** Medium

User-controlled values are logged directly without sanitization.

**Devin Prompt:**
> Add input sanitization across all services. Create a shared utility method `sanitizeLogInput(String input)` that strips newlines, carriage returns, and tabs from strings before logging. Apply it to all `log.info()` calls that include user-controlled values (account numbers, user IDs, etc.). Also add `@Size` constraints to all String fields in request DTOs to prevent oversized payloads. Fix the typo "utitlity" in `AccountController.java`.

---

### 2.7 Extract Shared Library (GAP-ORG-02)

**Severity:** Medium | **Effort:** Medium

Common code is duplicated across services.

**Devin Prompt:**
> Create a shared library module called `internet-banking-common` as a new Gradle subproject. Move the following duplicated classes into it: `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ErrorResponse`, `GlobalErrorCode`, `AuditAware`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, and `CustomFeignClientConfiguration`. Update all services to depend on `internet-banking-common`. Create a root `settings.gradle` that includes all services as subprojects. Remove the duplicated classes from each service.

---

### 2.8 Create Multi-Project Gradle Build (GAP-ORG-01)

**Severity:** Medium | **Effort:** Small

Versions and plugins are duplicated across build files.

**Devin Prompt:**
> Create a root `build.gradle` and `settings.gradle` for the entire project. Define common plugin versions, Spring Boot version (3.2.4), Spring Cloud version (2023.0.0), and shared dependencies in a `subprojects {}` block. Include all seven service directories as Gradle subprojects. Each service's `build.gradle` should only declare its unique dependencies. Verify that `./gradlew build` from the root builds all services.

---

### 2.9 Add Fallback Behavior (GAP-RES-04)

**Severity:** Medium | **Effort:** Medium

No graceful degradation when Core Banking is unavailable.

**Devin Prompt:**
> Implement fallback behavior in the fund-transfer-service and utility-payment-service for when Core Banking is unavailable. When a circuit breaker triggers, the services should: (1) save the transaction with status `QUEUED`, (2) return a 202 Accepted response with a message indicating the transfer will be processed when the service recovers, (3) implement a `@Scheduled` background task that periodically retries `QUEUED` transactions. Add a configurable retry interval and max retry count.

---

### 2.10 Standardize Logging (GAP-OBS-01)

**Severity:** Medium | **Effort:** Small

Logging is inconsistent and may expose sensitive data.

**Devin Prompt:**
> Standardize logging across all services. Add `logback-spring.xml` to each service's `src/main/resources/` with: (1) JSON format for production profile using `logstash-logback-encoder`, (2) human-readable format for development. Mask sensitive fields (account numbers, email addresses) in log output. Remove all `toString()` calls on request objects in log statements — instead log only non-sensitive identifiers. Add the `logstash-logback-encoder` dependency to each service.

---

### 2.11 Configure Prometheus Metrics (GAP-OBS-03)

**Severity:** Medium | **Effort:** Small

No metrics exporter despite Prometheus being in the tech stack.

**Devin Prompt:**
> Add Prometheus metrics support to all services. Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`. Configure actuator to expose the Prometheus endpoint: `management.endpoints.web.exposure.include: health,info,prometheus,metrics`. Add custom business metrics using Micrometer: count of fund transfers (tagged by status), count of utility payments (tagged by status), and histogram of transaction processing time. Add a Prometheus scrape configuration to docker-compose.

---

## Phase 3: Polish

> Comprehensive quality improvements for long-term maintainability.

### 3.1 Add Unit Tests to All Services (GAP-TEST-01)

**Severity:** Critical | **Effort:** Large

Only core-banking-service has unit tests.

**Devin Prompt:**
> Add comprehensive unit tests for internet-banking-user-service. Create tests for `UserService` covering: (1) successful user creation with Keycloak and Core Banking mocks, (2) duplicate email rejection, (3) email mismatch rejection, (4) core banking user not found, (5) user approval flow enabling Keycloak user, (6) reading users with pagination. Create tests for `KeycloakUserService` with a mocked `KeycloakManager`. Target 80%+ line coverage for service classes. Use Mockito for mocking and JUnit 5 assertions.

**Additional Devin Prompts for other services:**

> Add unit tests for internet-banking-fund-transfer-service. Test `FundTransferService`: (1) successful transfer, (2) Core Banking error sets status to FAILED, (3) reading all transfers with pagination. Mock the `BankingCoreFeignClient` and `FundTransferRepository`.

> Add unit tests for internet-banking-utility-payment-service. Test `UtilityPaymentService`: (1) successful payment, (2) Core Banking error handling, (3) reading payments with pagination. Mock the `BankingCoreRestClient` and `UtilityPaymentRepository`.

---

### 3.2 Add Integration Tests (GAP-TEST-02)

**Severity:** High | **Effort:** Large

No integration tests exist.

**Devin Prompt:**
> Add integration tests for core-banking-service using `@SpringBootTest` with an H2 in-memory database (already in test dependencies). Test the full request lifecycle: (1) `AccountController` GET endpoints with pre-seeded data, (2) `TransactionController` fund transfer end-to-end including balance updates, (3) `UserController` pagination. Use `@AutoConfigureMockMvc` for controller tests. Create a `test` profile with H2 configuration and Flyway migrations. Verify that all Flyway migrations run successfully against H2.

---

### 3.3 Fix Application Context Tests (GAP-TEST-04)

**Severity:** Medium | **Effort:** Small

Default context tests require running infrastructure.

**Devin Prompt:**
> Fix the application context tests in all services so they can run without external dependencies. For each service, create a `src/test/resources/application.yml` (or `application-test.yml`) that: (1) disables Eureka client (`eureka.client.enabled: false`), (2) disables Config Server bootstrap (`spring.cloud.config.enabled: false`), (3) uses H2 in-memory database, (4) mocks or disables Keycloak (for user-service). Add H2 test dependency where missing. Verify all context tests pass with `./gradlew test`.

---

### 3.4 Add Contract Tests (GAP-TEST-03)

**Severity:** Medium | **Effort:** Large

No contract tests between services.

**Devin Prompt:**
> Add Spring Cloud Contract tests between the fund-transfer-service (consumer) and core-banking-service (provider). On the provider side (core-banking-service), add `spring-cloud-starter-contract-verifier` and define contracts for `/api/v1/transaction/fund-transfer` and `/api/v1/account/bank-account/{account_number}`. On the consumer side (fund-transfer-service), add `spring-cloud-starter-contract-stub-runner` and write tests that verify the Feign client works against the generated stubs. Repeat for the utility-payment-service consumer.

---

### 3.5 Standardize Package Structure (GAP-ORG-03)

**Severity:** Low | **Effort:** Small

Package naming is inconsistent across services.

**Devin Prompt:**
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.repository`, `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.exception`, `com.javatodev.finance.configuration`. Move `model.repository` (user-service) to `repository`. Move `model.rest.request` / `model.rest.response` (user-service, utility-payment) to `model.dto.request` / `model.dto.response`. Update all imports.

---

### 3.6 Replace Manual Mappers with MapStruct (GAP-ORG-04)

**Severity:** Low | **Effort:** Small

Mapper classes are manually written and instantiated with `new`.

**Devin Prompt:**
> Replace all manual mapper classes with MapStruct. Add the MapStruct dependency (`org.mapstruct:mapstruct:1.5.5.Final`) and annotation processor to each service's `build.gradle`. Convert `BankAccountMapper`, `UserMapper`, `UtilityAccountMapper`, `FundTransferMapper`, and `UtilityPaymentMapper` to MapStruct `@Mapper` interfaces. Remove the `BaseMapper` abstract class. Inject mappers via Spring (`componentModel = "spring"`) instead of using `new`.

---

### 3.7 Add API Versioning Strategy (GAP-API-02)

**Severity:** Medium | **Effort:** Medium

No mechanism for API versioning beyond the `/v1/` path prefix.

**Devin Prompt:**
> Document and implement an API versioning strategy. Add a `docs/API_VERSIONING.md` that describes the URL-based versioning approach (`/api/v1/`, `/api/v2/`). Add a `Sunset` response header to v1 endpoints when v2 is introduced. Configure the API Gateway to route both `/v1/` and `/v2/` paths. Add `@Deprecated` annotations and OpenAPI deprecation markers to any endpoints that will be superseded.

---

### 3.8 Standardize Resource Naming (GAP-API-04)

**Severity:** Low | **Effort:** Small

API paths use inconsistent naming conventions.

**Devin Prompt:**
> Standardize REST resource naming across all services. Use plural nouns, kebab-case, and consistent patterns: `/api/v1/accounts/{accountNumber}` (not `bank-account`), `/api/v1/utility-accounts/{providerName}`, `/api/v1/users/{identification}`, `/api/v1/transactions/fund-transfers`, `/api/v1/transactions/utility-payments`. Update the API Gateway route configuration to match. Update all Feign client endpoint paths. Keep old endpoints as deprecated aliases for backward compatibility.

---

### 3.9 Add Custom Health Indicators (GAP-OBS-02)

**Severity:** Low | **Effort:** Small

Health endpoints only report basic UP/DOWN status.

**Devin Prompt:**
> Add custom health indicators to each service. In core-banking-service, add a `DatabaseHealthIndicator` that checks the MySQL connection. In user-service, add a `KeycloakHealthIndicator` that pings the Keycloak server. In fund-transfer-service and utility-payment-service, add a `CoreBankingHealthIndicator` that calls `/actuator/health` on core-banking-service via Feign. Configure `management.endpoint.health.show-details: always` for detailed health responses. Add liveness and readiness probe endpoints for Kubernetes: `/actuator/health/liveness` and `/actuator/health/readiness`.

---

### 3.10 Verify and Configure Distributed Tracing (GAP-OBS-04)

**Severity:** Low | **Effort:** Small

Tracing sampling rate and propagation are not explicitly configured.

**Devin Prompt:**
> Configure distributed tracing explicitly across all services. Set `management.tracing.sampling.probability: 1.0` for development/staging and `0.1` for production. Verify that trace IDs propagate correctly through the API Gateway -> User/Fund Transfer/Utility Payment Service -> Core Banking Service chain by making a test request and checking Zipkin UI. Add the trace ID to log output using the `%X{traceId}` MDC pattern in logback configuration.

---

### 3.11 Document Sorting and Filtering (GAP-API-06)

**Severity:** Low | **Effort:** Small

No documentation of supported query parameters.

**Devin Prompt:**
> Add OpenAPI documentation for all paginated endpoints. Use `@Parameter` annotations to document supported `page`, `size`, and `sort` parameters. Add `@Schema` annotations to all DTO fields. Create a global OpenAPI configuration class in each service with `@OpenAPIDefinition` providing API title, version, description, and contact information. Add examples to request/response schemas.

---

### 3.12 Add CSRF Documentation (GAP-SEC-02)

**Severity:** Medium | **Effort:** Small

CSRF is disabled without documented justification.

**Devin Prompt:**
> Add a code comment in `SecurityConfiguration.java` documenting why CSRF is disabled (stateless JWT authentication, no cookie-based sessions). Add an `ADR-001-csrf-disabled.md` Architecture Decision Record in the `docs/` directory explaining the decision, its context (REST API with JWT bearer tokens), and the conditions under which it should be revisited (e.g., if browser-based cookie authentication is ever added).

---

## Timeline Summary

| Phase | Duration | Items | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 1-2 weeks | 11 items | Critical bugs fixed, security basics in place, API consistency improved |
| **Phase 2** | 3-6 weeks | 11 items | Resilience patterns implemented, shared library extracted, proper error handling |
| **Phase 3** | 6-12 weeks | 11 items | Comprehensive test coverage, contract testing, full observability stack |

## Priority Matrix

```
                    HIGH IMPACT
                        │
   Phase 1              │              Phase 2
   (Quick Wins)         │              (Important)
   GAP-RES-06           │              GAP-ERR-03
   GAP-ERR-02           │              GAP-RES-01
   GAP-ERR-01           │              GAP-RES-05
   GAP-ERR-04           │              GAP-SEC-06
   GAP-SEC-01           │              GAP-ORG-02
   GAP-RES-03           │              GAP-RES-02
                        │              GAP-RES-04
 ───────────────────────┼──────────────────────────
                        │
   Phase 1              │              Phase 3
   (Easy Polish)        │              (Long-term)
   GAP-SEC-04           │              GAP-TEST-01
   GAP-API-03           │              GAP-TEST-02
   GAP-API-01           │              GAP-TEST-03
   GAP-API-05           │              GAP-ORG-03
   GAP-OBS-01           │              GAP-API-04
                        │              GAP-OBS-02
                        │
               LOW EFFORT ──────────── HIGH EFFORT
```
