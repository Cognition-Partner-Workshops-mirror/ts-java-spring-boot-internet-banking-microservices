# Remediation Roadmap

## Overview

This roadmap prioritizes the 40 identified gaps into three phases based on risk, impact, and effort. Each item includes a sample Devin prompt that can be used to implement the remediation.

---

## Phase 1: Quick Wins (1-2 Weeks)

Critical and high-severity items that require small effort. These address security vulnerabilities, data leakage, and immediate reliability risks.

### 1.1 Fix Exception Leakage in Error Handlers

**Gap Reference:** 2.1  
**Severity:** Critical | **Effort:** Small  
**Description:** All services return raw exception messages (including stack traces) to API callers via the generic `handleException()` catch-all.

**Remediation:** Replace the generic handler body with a safe error response that logs the full exception server-side but returns only a generic message to the client.

**Sample Devin Prompt:**
> In all `GlobalExceptionHandler` classes across all services, update the `handleException(Exception e, Locale locale)` method to: (1) log the full exception at ERROR level, (2) return HTTP 500 with a generic `ErrorResponse` containing code `INTERNAL_ERROR` and message `"An unexpected error occurred. Please try again later."`. Do not expose `e.toString()` or any stack trace in the response body.

---

### 1.2 Add Input Validation to All Request DTOs

**Gap Reference:** 4.1, 2.5  
**Severity:** Critical | **Effort:** Small  
**Description:** No Jakarta Bean Validation annotations on request objects. Negative transfer amounts, null accounts, and invalid emails can reach business logic.

**Remediation:** Add `spring-boot-starter-validation` dependency and annotate all request DTOs with appropriate constraints.

**Sample Devin Prompt:**
> Add `implementation 'org.springframework.boot:spring-boot-starter-validation'` to all service `build.gradle` files. Then add Jakarta validation annotations to all request DTOs: `FundTransferRequest` (both in core-banking and fund-transfer services) should have `@NotBlank` on fromAccount/toAccount and `@NotNull @Positive` on amount. `UtilityPaymentRequest` should have `@NotNull` on providerId, `@NotNull @Positive` on amount, `@NotBlank` on referenceNumber and account. `User` DTO in user-service should have `@NotBlank @Email` on email, `@NotBlank` on identification, `@NotBlank @Size(min=8)` on password. Add `@Valid` to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details.

---

### 1.3 Remove Sensitive Data from Logs

**Gap Reference:** 6.2, 4.7  
**Severity:** Critical | **Effort:** Small  
**Description:** `User.password`, financial request data, and full DTOs are logged via `.toString()` at INFO level.

**Remediation:** Remove or redact sensitive fields from log statements. Exclude `password` from `User.toString()`.

**Sample Devin Prompt:**
> Audit all `log.info()` calls across all services. In `internet-banking-user-service/UserController.java`, change `log.info("Creating user with {}", request.toString())` to `log.info("Creating user with email {}", request.getEmail())`. In `core-banking-service/TransactionController.java`, redact account details: log only transaction type and a masked account (last 4 digits). In the `User` DTO in user-service, add `@ToString.Exclude` on the `password` field. Ensure no financial amounts or full account numbers are logged at INFO level — move detailed logging to DEBUG.

---

### 1.4 Externalize Secrets from Docker Compose

**Gap Reference:** 4.2  
**Severity:** Critical | **Effort:** Small  
**Description:** Database passwords and Keycloak admin credentials are hardcoded in `docker-compose.yml`.

**Remediation:** Use a `.env` file (gitignored) for secrets and reference them via `${VARIABLE}` syntax in Docker Compose.

**Sample Devin Prompt:**
> Create a `docker-compose/.env.example` file with placeholder values for: `MYSQL_ROOT_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `KC_DB_PASSWORD`, `POSTGRES_PASSWORD`, and `MYSQL_APP_USER_PASSWORD`. Update `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to reference these variables using `${VAR}` syntax. Add `docker-compose/.env` to `.gitignore`. Update `docker-compose/mysql/privileges.sql` to use an environment variable for the password (or document that it must be changed). Add a note to the README explaining the `.env` setup step.

---

### 1.5 Fix HTTP Status Codes in Error Responses

**Gap Reference:** 2.2  
**Severity:** High | **Effort:** Small  
**Description:** All exceptions currently return `400 BAD_REQUEST` regardless of type.

**Remediation:** Map each exception type to its correct HTTP status.

**Sample Devin Prompt:**
> Update all `GlobalExceptionHandler` classes to return proper HTTP status codes: `EntityNotFoundException` should return 404 NOT_FOUND. `InsufficientFundsException` should return 422 UNPROCESSABLE_ENTITY. `UserAlreadyRegisteredException` should return 409 CONFLICT. `InvalidEmailException` and `InvalidBankingUserException` should return 400 BAD_REQUEST. Generic `Exception` should return 500 INTERNAL_SERVER_ERROR. Update the `ErrorResponse` class to include an `httpStatus` field.

---

### 1.6 Add Feign Timeout Configuration

**Gap Reference:** 7.3  
**Severity:** High | **Effort:** Small  
**Description:** No explicit connect/read timeouts on Feign clients. A slow downstream can block threads indefinitely.

**Remediation:** Configure Feign client timeouts in application properties.

**Sample Devin Prompt:**
> Add Feign timeout configuration to the Spring Cloud Config repository (or local application.yml if config server is not available) for all services that use Feign clients (fund-transfer-service, utility-payment-service, user-service). Set `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`. Also add `spring.cloud.openfeign.client.config.core-banking-service.read-timeout=15000` for the core banking calls specifically.

---

### 1.7 Add Retry Configuration for Feign Clients

**Gap Reference:** 7.2  
**Severity:** High | **Effort:** Small  
**Description:** No retry mechanism for transient network failures on inter-service calls.

**Remediation:** Add Spring Retry with appropriate configuration.

**Sample Devin Prompt:**
> Add `implementation 'org.springframework.retry:spring-retry'` to the `build.gradle` of fund-transfer-service, utility-payment-service, and user-service. Enable retry via `@EnableRetry` on each application class. Configure retry in application.yml: `spring.cloud.openfeign.client.config.default.retryer=feign.Retryer.Default` with max 3 attempts and 1-second backoff. Ensure retries only happen on `5xx` errors and connection timeouts, NOT on `4xx` client errors.

---

### 1.8 Mark Transactions as FAILED on Error

**Gap Reference:** 7.6  
**Severity:** High | **Effort:** Small  
**Description:** If the Feign call to core-banking fails, fund transfer and utility payment records remain in `PENDING`/`PROCESSING` status forever.

**Remediation:** Add try-catch around Feign calls to update status to `FAILED` on exception.

**Sample Devin Prompt:**
> In `FundTransferService.fundTransfer()`, wrap the `bankingCoreFeignClient.fundTransfer(request)` call in a try-catch block. On exception, update `optFundTransfer.setStatus(TransactionStatus.FAILED)`, save, and re-throw the exception. Apply the same pattern in `UtilityPaymentService.utilPayment()` — on exception from `bankingCoreRestClient.utilityPayment()`, set `optUtilPayment.setStatus(TransactionStatus.FAILED)`, save, and re-throw.

---

### 1.9 Fix Raw ResponseEntity Types

**Gap Reference:** 5.1  
**Severity:** Medium | **Effort:** Small  
**Description:** Controllers use raw `ResponseEntity` without type parameters, losing type safety and OpenAPI accuracy.

**Remediation:** Add type parameters to all ResponseEntity return types.

**Sample Devin Prompt:**
> Update all controller methods across all services to use typed `ResponseEntity<T>` instead of raw `ResponseEntity`. For example: `AccountController.getBankAccount()` should return `ResponseEntity<BankAccount>`; `TransactionController.fundTransfer()` should return `ResponseEntity<FundTransferResponse>`; `FundTransferController.readFundTransfers()` should return `ResponseEntity<List<FundTransfer>>`. Ensure all return types are properly specified.

---

### 1.10 Fix Swagger Dependency (WebFlux → WebMVC)

**Gap Reference:** 5.6  
**Severity:** Medium | **Effort:** Small  
**Description:** Services use `springdoc-openapi-starter-webflux-ui` but are WebMVC applications.

**Remediation:** Replace with the correct WebMVC starter.

**Sample Devin Prompt:**
> In `build.gradle` of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service, replace `implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'` with `implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'`. Note: the API gateway (which IS WebFlux-based) should keep the webflux variant. Verify each service compiles and the Swagger UI is accessible at `/swagger-ui.html`.

---

## Phase 2: Important (3-6 Weeks)

High and medium-severity items requiring moderate effort. These establish proper resilience, testing, and observability.

### 2.1 Add Circuit Breaker Pattern

**Gap Reference:** 7.1  
**Severity:** High | **Effort:** Medium  
**Description:** No circuit breaker on Feign calls. Core banking outage cascades to all services.

**Sample Devin Prompt:**
> Add Resilience4j circuit breaker to the fund-transfer-service, utility-payment-service, and user-service. Add dependencies: `implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'` and `implementation 'io.github.resilience4j:resilience4j-spring-boot3'`. Configure a circuit breaker instance named `coreBankingService` with: `failureRateThreshold=50`, `waitDurationInOpenState=30s`, `slidingWindowSize=10`. Apply `@CircuitBreaker(name = "coreBankingService")` to service methods that call core-banking. Add a fallback method that returns a meaningful error response.

---

### 2.2 Add Unit Tests for All Services

**Gap Reference:** 3.1, 3.4  
**Severity:** High | **Effort:** Large  
**Description:** Only core-banking has unit tests. Other services have zero business logic tests.

**Sample Devin Prompt:**
> Write comprehensive unit tests for: (1) `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()` success, Feign failure, and `readAllTransfers()`. Test `FundTransferController` with MockMvc. (2) `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()` success/failure and `readPayments()`. Test controller layer. (3) `internet-banking-user-service`: Test `UserService.createUser()` all paths (duplicate email, invalid email, user not found, success), `readUsers()`, `readUser()`, `updateUser()`. Mock Keycloak and Feign dependencies. Target 80%+ line coverage for service classes.

---

### 2.3 Add Integration Tests with Testcontainers

**Gap Reference:** 3.2  
**Severity:** High | **Effort:** Large  
**Description:** No integration tests verify that services work with real databases and dependencies.

**Sample Devin Prompt:**
> Add integration tests using Testcontainers for: (1) `core-banking-service`: Add `testImplementation 'org.testcontainers:mysql'` and `testImplementation 'org.testcontainers:junit-jupiter'`. Create an integration test that starts a MySQL container, runs Flyway migrations, and tests the full flow: create account → fund transfer → verify balances. (2) `internet-banking-fund-transfer-service`: Test with MySQL + WireMock for core-banking-service mock. Verify the full transfer lifecycle (PENDING → SUCCESS/FAILED). Use `@SpringBootTest` with `@Testcontainers` annotation.

---

### 2.4 Add Service-Level Security

**Gap Reference:** 4.3  
**Severity:** High | **Effort:** Medium  
**Description:** Downstream services have no authentication. Direct access bypasses the gateway.

**Sample Devin Prompt:**
> Add security to downstream services so they cannot be accessed without a valid internal token. Option A (recommended): Add `spring-boot-starter-security` to fund-transfer, utility-payment, and core-banking services. Configure them as OAuth2 resource servers that validate the JWT token passed through from the gateway. The gateway should forward the `Authorization` header to downstream services. Option B: Add a shared API key filter that validates an internal `X-Internal-Api-Key` header set by the gateway. Implement Option A: add the security starter, configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointing to Keycloak, and update Feign clients to forward the Authorization header.

---

### 2.5 Implement Structured JSON Logging

**Gap Reference:** 6.1  
**Severity:** High | **Effort:** Medium  
**Description:** Unstructured text logs make aggregation and search in production difficult.

**Sample Devin Prompt:**
> Add structured JSON logging to all services. Add `implementation 'net.logstash.logback:logstash-logback-encoder:7.4'` to each service's `build.gradle`. Create a shared `logback-spring.xml` configuration that: (1) Uses `LogstashEncoder` for JSON output in `docker` profile, (2) Keeps human-readable console output for `default` profile, (3) Includes MDC fields: `traceId`, `spanId`, `service.name`, `X-Auth-Id`. Add a filter or interceptor that populates MDC with the auth user ID from the `X-Auth-Id` header.

---

### 2.6 Add Idempotency Keys for Financial Operations

**Gap Reference:** 7.7  
**Severity:** High | **Effort:** Medium  
**Description:** No idempotency mechanism exists. Client retries can cause duplicate transactions.

**Sample Devin Prompt:**
> Implement idempotency for fund transfer and utility payment endpoints. Add an `X-Idempotency-Key` header requirement to POST endpoints. In fund-transfer-service: (1) Add a `idempotency_key` column to the `fund_transfer` table with a unique constraint. (2) Before processing, check if a record with that key exists — if so, return the existing result. (3) Save the key with the initial PENDING record. Apply the same pattern to utility-payment-service. Return HTTP 409 if a duplicate key is detected with a different payload. Add the header to the Postman collection.

---

### 2.7 Add Rate Limiting at API Gateway

**Gap Reference:** 4.6  
**Severity:** Medium | **Effort:** Medium  
**Description:** No rate limiting protects financial endpoints from abuse.

**Sample Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. Add `implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'` to the gateway's build.gradle. Configure Redis-based rate limiting with: 10 requests/second for fund transfer endpoints, 10 requests/second for utility payment endpoints, 50 requests/second for read endpoints. Use the authenticated user's ID as the rate limit key. Add Redis to the docker-compose.yml. Include a `429 Too Many Requests` response with `Retry-After` header.

---

### 2.8 Add Health Check Customization

**Gap Reference:** 6.3, 6.4  
**Severity:** Medium | **Effort:** Small  
**Description:** Only default health checks exist. No custom indicators for critical dependencies.

**Sample Devin Prompt:**
> Add custom health indicators to each service: (1) In core-banking-service, add a `DatabaseHealthIndicator` that verifies the MySQL connection. (2) In user-service, add a `KeycloakHealthIndicator` that pings the Keycloak server. (3) In fund-transfer and utility-payment services, add health indicators that verify core-banking-service reachability via a lightweight GET. Enable Prometheus metrics endpoint in all services by adding `management.endpoints.web.exposure.include=health,info,prometheus` and `management.metrics.export.prometheus.enabled=true` to configuration.

---

### 2.9 Add Pagination Metadata to List Responses

**Gap Reference:** 5.4  
**Severity:** Medium | **Effort:** Small  
**Description:** List endpoints lose pagination metadata by returning raw lists.

**Sample Devin Prompt:**
> Create a generic `PageResponse<T>` wrapper class in each service (or a shared module) with fields: `content` (List<T>), `totalElements` (long), `totalPages` (int), `currentPage` (int), `pageSize` (int). Update `FundTransferService.readAllTransfers()`, `UtilityPaymentService.readPayments()`, and `UserService.readUsers()` to return `PageResponse<T>` instead of `List<T>`. Populate from Spring's `Page` object. Update corresponding controller return types.

---

### 2.10 Implement Graceful Shutdown

**Gap Reference:** 7.9  
**Severity:** Medium | **Effort:** Small  
**Description:** No graceful shutdown configured. In-flight requests may be terminated.

**Sample Devin Prompt:**
> Add graceful shutdown configuration to all services. In the Spring Cloud Config repository (or local application.yml), add: `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`. Update Docker Compose to use `stop_grace_period: 35s` for all service containers. This ensures in-flight requests complete before the container is killed.

---

## Phase 3: Polish (6-12 Weeks)

Medium and low-severity items that improve developer experience, code consistency, and operational maturity.

### 3.1 Extract Shared Library Module

**Gap Reference:** 1.1, 1.2  
**Severity:** Medium | **Effort:** Medium  
**Description:** Common code is duplicated across services.

**Sample Devin Prompt:**
> Create a shared Gradle module called `banking-common` at the repository root. Move the following shared code into it: (1) Exception classes: `SimpleBankingGlobalException`, `EntityNotFoundException`, `ErrorResponse`, `GlobalErrorCode`. (2) Audit base class: `AuditAware` with JPA auditing config. (3) Filter classes: `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`. (4) `BaseMapper` interface. Create a root `settings.gradle` that includes all services and the shared module. Update each service's `build.gradle` to depend on `project(':banking-common')`. Remove duplicated classes from individual services.

---

### 3.2 Standardize Package Structure

**Gap Reference:** 1.3, 1.5  
**Severity:** Medium | **Effort:** Medium  
**Description:** Package naming varies across services making navigation inconsistent.

**Sample Devin Prompt:**
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.{service-name}.controller`, `com.javatodev.finance.{service-name}.service`, `com.javatodev.finance.{service-name}.model.entity`, `com.javatodev.finance.{service-name}.model.dto`, `com.javatodev.finance.{service-name}.model.mapper`, `com.javatodev.finance.{service-name}.repository`, `com.javatodev.finance.{service-name}.client` (for Feign clients), `com.javatodev.finance.{service-name}.config`. Rename packages and update imports accordingly. Ensure all Feign client interfaces follow the naming pattern `{ServiceName}Client` (e.g., `CoreBankingClient`).

---

### 3.3 Add Contract Tests Between Services

**Gap Reference:** 3.3  
**Severity:** High | **Effort:** Large  
**Description:** No contract verification between service APIs.

**Sample Devin Prompt:**
> Implement Spring Cloud Contract tests for the core-banking-service as the producer. Add `testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'` to core-banking-service. Write contract DSL files (Groovy) for: (1) GET `/api/v1/account/bank-account/{number}` — success and not-found cases, (2) POST `/api/v1/transaction/fund-transfer` — success and insufficient-funds cases, (3) GET `/api/v1/user/{identification}` — success case. Generate stubs JAR. In consumer services (fund-transfer, utility-payment, user), add `testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'` and write tests that verify Feign clients against the generated stubs.

---

### 3.4 Add CI/CD Pipeline

**Gap Reference:** No active CI  
**Severity:** Medium | **Effort:** Medium  
**Description:** No automated build, test, or deployment pipeline exists.

**Sample Devin Prompt:**
> Create a GitHub Actions CI pipeline in `.github/workflows/ci.yml` that: (1) Triggers on push to `main` and on pull requests. (2) Uses Java 21 with Gradle. (3) Runs `./gradlew build` for each service in parallel using a matrix strategy. (4) Runs unit tests and reports test results. (5) Builds Docker images for each service. (6) Runs OWASP Dependency Check (`org.owasp:dependency-check-gradle` plugin). (7) Caches Gradle dependencies between runs. Add the Gradle wrapper to each service if missing.

---

### 3.5 Add Bulkhead / Thread Pool Isolation

**Gap Reference:** 7.5  
**Severity:** Medium | **Effort:** Medium  
**Description:** All Feign calls share one thread pool, risking cascade failures.

**Sample Devin Prompt:**
> Configure Resilience4j bulkhead for Feign clients in fund-transfer-service and utility-payment-service. Add bulkhead configuration: `resilience4j.bulkhead.instances.coreBankingService.maxConcurrentCalls=10` and `resilience4j.bulkhead.instances.coreBankingService.maxWaitDuration=500ms`. Apply `@Bulkhead(name = "coreBankingService")` alongside the circuit breaker annotations on service methods that call core-banking. This ensures a slow core-banking service doesn't exhaust all threads.

---

### 3.6 Add Filtering and Search to List Endpoints

**Gap Reference:** 5.5  
**Severity:** Low | **Effort:** Medium  
**Description:** List endpoints only support basic pagination, no filtering.

**Sample Devin Prompt:**
> Add filtering capabilities to list endpoints: (1) Fund transfer list: Add query params `status` (enum filter), `fromDate`/`toDate` (date range), `fromAccount`/`toAccount`. Use Spring Data JPA Specifications or QueryDSL. (2) Utility payment list: Add query params `status`, `providerId`, `fromDate`/`toDate`. (3) User list (user-service): Add query params `status`, `email` (partial match). Update repository interfaces with custom query methods. Update controllers to accept filter parameters.

---

### 3.7 Add API Gateway Access Logging

**Gap Reference:** 6.7  
**Severity:** Medium | **Effort:** Small  
**Description:** No request/response logging at the gateway level.

**Sample Devin Prompt:**
> Add an access logging filter to the API Gateway. Create a `GlobalFilter` that logs: HTTP method, request path, response status code, latency in milliseconds, and the authenticated user ID. Use structured logging format. Configure it to log at INFO level for all requests. Exclude health check endpoints (`/actuator/**`) from access logs to reduce noise. Also add `spring.cloud.gateway.httpserver.wiretap=true` for DEBUG-level detailed logging.

---

### 3.8 Fix Keycloak Singleton Thread Safety

**Gap Reference:** 4.4  
**Severity:** Medium | **Effort:** Small  
**Description:** `KeycloakProperties.getInstance()` has a race condition.

**Sample Devin Prompt:**
> Fix the thread-safety issue in `KeycloakProperties.getInstance()` in the user-service. Replace the manual singleton pattern with either: (A) Declare the field as `volatile` and use double-checked locking, or (B, preferred) Remove the static singleton entirely and make the `Keycloak` instance a Spring-managed `@Bean` in a `@Configuration` class. Create a `KeycloakConfig` class with a `@Bean` method that builds and returns the Keycloak instance. Inject it into `KeycloakManager` instead of calling `keycloakProperties.getInstance()`.

---

### 3.9 Add Standardized Response Wrapper

**Gap Reference:** 5.7  
**Severity:** Medium | **Effort:** Small  
**Description:** No consistent API response envelope.

**Sample Devin Prompt:**
> Create a standard API response wrapper. Define `ApiResponse<T>` with fields: `boolean success`, `T data`, `ErrorDetail error` (nullable), and `Map<String, Object> meta` (for pagination, timestamps). Create `ErrorDetail` with `String code`, `String message`, `List<FieldError> fieldErrors`. Apply this wrapper to all controller responses using a `ResponseBodyAdvice` that automatically wraps successful responses. Error responses should already use this format via the `GlobalExceptionHandler`.

---

### 3.10 Add Dependency Vulnerability Scanning

**Gap Reference:** 4.8  
**Severity:** Medium | **Effort:** Small  
**Description:** No automated vulnerability scanning of dependencies.

**Sample Devin Prompt:**
> Add OWASP Dependency Check to the Gradle build. Add the plugin `id 'org.owasp.dependencycheck' version '9.0.10'` to each service's `build.gradle`. Configure it with: `dependencyCheck { failBuildOnCVSS = 7; suppressionFile = "${rootProject.projectDir}/owasp-suppressions.xml" }`. Create the suppressions file for any known false positives. Add a Gradle task alias so `./gradlew dependencyCheckAnalyze` can be run easily. Document the process in the README.

---

## Summary Timeline

| Phase | Duration | Items | Key Outcomes |
|-------|----------|-------|--------------|
| **Phase 1** | Weeks 1-2 | 10 items | Security holes closed, error handling fixed, basic resilience |
| **Phase 2** | Weeks 3-8 | 10 items | Circuit breakers, test coverage, observability, idempotency |
| **Phase 3** | Weeks 9-14 | 10 items | Code organization, CI/CD, contract tests, operational polish |

## Priority Matrix

```
                    HIGH EFFORT          LOW EFFORT
              ┌─────────────────────┬─────────────────────┐
   CRITICAL   │ (none)              │ 1.1, 1.2, 1.3, 1.4 │
              ├─────────────────────┼─────────────────────┤
   HIGH       │ 2.2, 2.3, 3.3      │ 1.5-1.8, 2.1        │
              ├─────────────────────┼─────────────────────┤
   MEDIUM     │ 3.1, 3.2, 3.5, 3.6 │ 1.9, 1.10, 2.7-2.10│
              ├─────────────────────┼─────────────────────┤
   LOW        │ (none)              │ 3.7, 3.8            │
              └─────────────────────┴─────────────────────┘
```
