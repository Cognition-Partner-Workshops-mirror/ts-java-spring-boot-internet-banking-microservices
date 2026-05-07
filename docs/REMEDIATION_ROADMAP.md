# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be resolved quickly with minimal risk.

### 1.1 Fix Catch-All Exception Handler (GAP-ERR-01)
**Severity:** Critical | **Effort:** Small

Replace the exception-leaking catch-all handler in all four services with a safe, structured error response that returns HTTP 500 for unexpected errors.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in all four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Replace the catch-all `@ExceptionHandler({Exception.class})` method so it: (1) returns HTTP 500 instead of 400, (2) returns a structured `ErrorResponse` with code `INTERNAL_SERVER_ERROR` and a generic message like "An unexpected error occurred", (3) does NOT include the exception message or stack trace in the response body, and (4) logs the full exception at ERROR level. Open a PR with the changes.

---

### 1.2 Add Input Validation to All API Endpoints (GAP-SEC-02)
**Severity:** Critical | **Effort:** Medium

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller method parameters.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add input validation to all REST API request DTOs across all services. Specifically: (1) Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. (2) Add `@NotBlank`, `@NotNull`, `@Positive`, `@Email`, and `@Size` annotations as appropriate to: `FundTransferRequest` (fromAccount not blank, toAccount not blank, amount positive), `UtilityPaymentRequest` (providerId not null, amount positive, referenceNumber not blank, account not blank), and `User` DTO in user-service (email valid email, identification not blank, password not blank). (3) Add `@Valid` annotation to all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details. Open a PR.

---

### 1.3 Add Idempotency Keys to Financial Endpoints (GAP-RES-06)
**Severity:** Critical | **Effort:** Medium

Prevent duplicate financial transactions by requiring an `X-Idempotency-Key` header on fund transfer and utility payment endpoints.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement idempotency protection for the `POST /api/v1/transfer` endpoint in `internet-banking-fund-transfer-service` and the `POST /api/v1/utility-payment` endpoint in `internet-banking-utility-payment-service`. Add an `X-Idempotency-Key` request header (UUID). Store the idempotency key in a new DB column on `FundTransferEntity` and `UtilityPaymentEntity` with a unique constraint. Before processing, check if a record with the same key exists — if so, return the existing result. Add the header requirement to the controller methods. Open a PR.

---

### 1.4 Configure Feign Timeouts (GAP-RES-03)
**Severity:** High | **Effort:** Small

Add explicit connect and read timeouts to all Feign clients to prevent thread exhaustion.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure Feign client timeouts for all three services that use Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Set connect timeout to 5 seconds and read timeout to 10 seconds. Add the configuration in each service's `application.yml` using `spring.cloud.openfeign.client.config.default.connect-timeout` and `read-timeout` properties. Open a PR.

---

### 1.5 Redact Sensitive Data from Logs (GAP-OBS-03)
**Severity:** High | **Effort:** Small

Remove or mask PII and financial data from log statements across all services.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, audit all `log.info()`, `log.error()`, and `log.debug()` statements across all services. Replace any that log full request objects (`request.toString()`, `fundTransferRequest.toString()`, etc.) with masked versions that only log non-sensitive identifiers (e.g., transaction reference, status) and redact account numbers (show last 4 digits only), email addresses, and monetary amounts. Open a PR.

---

### 1.6 Fix Swagger Dependency (GAP-API-04)
**Severity:** Medium | **Effort:** Small

Replace the incorrect WebFlux Swagger dependency with the correct WebMVC one in MVC-based services.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, replace the `springdoc-openapi-starter-webflux-ui:2.1.0` dependency with `springdoc-openapi-starter-webmvc-ui:2.1.0` in the `build.gradle` of these three services: `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`. Keep the webflux variant only in `internet-banking-utility-payment-service` if it is reactive, otherwise change it too. Verify the Swagger UI loads correctly. Open a PR.

---

### 1.7 Fix ResponseEntity Generic Types (GAP-ERR-02)
**Severity:** Medium | **Effort:** Small

Add proper generic type parameters to all controller methods returning `ResponseEntity`.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add proper generic type parameters to all `ResponseEntity` return types in all controller classes across all services. For example, change `ResponseEntity` to `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc. This improves type safety and Swagger documentation. Open a PR.

---

### 1.8 Add Prometheus Metrics Dependency (GAP-OBS-04)
**Severity:** Medium | **Effort:** Small

Add the Micrometer Prometheus registry so the `/actuator/prometheus` endpoint works.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the `io.micrometer:micrometer-registry-prometheus` dependency to all services that already have `spring-boot-starter-actuator`. Also configure `management.endpoints.web.exposure.include` to include `health,info,prometheus,metrics` in each service's `application.yml` (or the centralized config repo). Open a PR.

---

### 1.9 Make Keycloak Singleton Thread-Safe (GAP-SEC-05)
**Severity:** Medium | **Effort:** Small

Fix the thread-unsafe lazy initialization in `KeycloakProperties`.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, in `internet-banking-user-service`, refactor `KeycloakProperties.getInstance()` to be thread-safe. Either use a `@Bean` method in a `@Configuration` class to create the `Keycloak` instance as a Spring-managed singleton, or use `synchronized` / double-checked locking. The `@Bean` approach is preferred. Open a PR.

---

### 1.10 Externalize Secrets from Docker Compose (GAP-SEC-01)
**Severity:** Critical | **Effort:** Medium

Move hardcoded passwords to environment variables or Docker secrets.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, refactor `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to remove all hardcoded passwords. Replace them with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`) and add a `.env.example` file with placeholder values. Also update `docker-compose/mysql/Dockerfile` to use `ARG` or `ENV` references instead of hardcoded passwords. Add `.env` to `.gitignore`. Do NOT include actual passwords in the committed files. Open a PR.

---

## Phase 2: Important Improvements (2-4 weeks)

Structural improvements that require more effort but significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j (GAP-RES-01)
**Severity:** High | **Effort:** Medium

Add circuit breaker patterns to all inter-service Feign calls to prevent cascading failures.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Resilience4j circuit breakers to all Feign client calls. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. (2) Enable Feign circuit breaker integration via `spring.cloud.openfeign.circuitbreaker.enabled=true`. (3) Create fallback classes for each Feign client that return meaningful error responses. (4) Configure circuit breaker parameters: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. Open a PR.

---

### 2.2 Add Feign Error Decoders to All Services (GAP-ERR-03)
**Severity:** High | **Effort:** Medium

Ensure error responses from downstream services are properly decoded and propagated.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a custom `FeignErrorDecoder` to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` (user-service already has one). The decoder should: (1) Parse the `ErrorResponse` JSON body from the downstream service. (2) Map HTTP 400 responses to appropriate business exceptions (e.g., `InsufficientFundsException`, `EntityNotFoundException`). (3) Map HTTP 500 responses to a generic `ServiceUnavailableException`. (4) Register the decoder in the `CustomFeignClientConfiguration` class. Open a PR.

---

### 2.3 Add Authentication to Downstream Services (GAP-SEC-04)
**Severity:** High | **Effort:** Medium

Add OAuth2 resource server configuration to each downstream service so they validate JWT tokens independently.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Spring Security OAuth2 Resource Server to each downstream business service (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). (1) Add `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security` dependencies. (2) Configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointing to Keycloak. (3) Create a `SecurityConfiguration` class that permits actuator endpoints and requires authentication for all other endpoints. (4) Ensure Feign clients propagate the JWT token via a request interceptor. Open a PR.

---

### 2.4 Extract Shared Library (GAP-ORG-02)
**Severity:** High | **Effort:** Large

Create a shared module to eliminate code duplication across services.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a new Gradle module called `internet-banking-common` that contains all the duplicated classes: `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `BaseMapper`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `CustomFeignClientConfiguration`, and `TransactionStatus`. (1) Create a root `settings.gradle` and `build.gradle` for a multi-project build. (2) Include the common module as a dependency in each service's `build.gradle`. (3) Remove the duplicated classes from each service and update imports. (4) Verify all services compile successfully. Open a PR.

---

### 2.5 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (GAP-TEST-01)
**Severity:** Critical | **Effort:** Large

Write unit tests for the three untested business services.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, write comprehensive unit tests for: (1) `internet-banking-user-service`: Test `UserService.createUser()` (happy path, duplicate email, email mismatch, NIC not found), `UserService.updateUser()` (approve flow, user not found), `UserService.readUsers()`, `KeycloakUserService` methods. (2) `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()` (happy path, Feign error handling), `FundTransferService.readAllTransfers()`. (3) `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()` (happy path, Feign error handling), `UtilityPaymentService.readPayments()`. Use Mockito for mocking dependencies. Target 80%+ line coverage. Open a PR.

---

### 2.6 Add Retry Policies (GAP-RES-02)
**Severity:** Medium | **Effort:** Small

Add retry configuration for transient failures on Feign calls.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Spring Retry support to all Feign client calls. (1) Add `spring-retry` dependency to all three Feign-using services. (2) Configure Feign retry: max-attempts=3, backoff with 1-second initial interval and 2x multiplier. (3) Only retry on `5xx` responses and connection exceptions — NOT on `4xx` responses. Open a PR.

---

### 2.7 Add Pagination Metadata to Responses (GAP-API-02)
**Severity:** Medium | **Effort:** Small

Return full `Page<T>` or a custom page wrapper from paginated endpoints.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all paginated GET endpoints to return pagination metadata. Create a generic `PageResponse<T>` wrapper with fields: `content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `last`. Update the service layer to return `Page` objects and controllers to wrap them in `PageResponse`. Affected endpoints: `GET /api/v1/user` (core-banking-service), `GET /api/v1/bank-users` (user-service), `GET /api/v1/transfer` (fund-transfer-service), `GET /api/v1/utility-payment` (utility-payment-service). Open a PR.

---

### 2.8 Standardize Error Response Schema (GAP-API-05)
**Severity:** Medium | **Effort:** Small

Ensure all error responses follow a single, consistent JSON schema.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, standardize the error response format across all services. Update the `ErrorResponse` class to include fields: `timestamp` (ISO-8601), `status` (HTTP status code), `code` (application error code), `message` (user-friendly message), and `path` (request URI). Update all `GlobalExceptionHandler` methods to populate these fields consistently. Ensure the catch-all handler uses this same schema instead of returning a plain string. Open a PR.

---

### 2.9 Add Structured JSON Logging (GAP-OBS-02)
**Severity:** Medium | **Effort:** Medium

Configure Logback to output JSON-formatted logs for log aggregation systems.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure structured JSON logging for all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service. (2) Create a `logback-spring.xml` in each service's `src/main/resources/` that uses `LogstashEncoder` for non-local profiles and the default pattern for local/dev. (3) Include MDC fields for `traceId`, `spanId`, `serviceName`, and `userId`. Open a PR.

---

### 2.10 Add Health Indicators for External Dependencies (GAP-OBS-05)
**Severity:** Medium | **Effort:** Medium

Create custom health indicators so `/actuator/health` reflects the true system state.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom Spring Boot health indicators: (1) In all MySQL-using services, the built-in DataSource health indicator should already work — verify it by setting `management.endpoint.health.show-details=always`. (2) In `internet-banking-user-service`, add a custom `KeycloakHealthIndicator` that pings the Keycloak server URL. (3) In all Eureka client services, ensure the Eureka health indicator is enabled. (4) Configure `management.endpoints.web.exposure.include` to expose health details. Open a PR.

---

### 2.11 Add Rate Limiting to API Gateway (GAP-RES-05)
**Severity:** Medium | **Effort:** Medium

Configure request rate limiting at the API Gateway to protect downstream services.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add rate limiting to `internet-banking-api-gateway` using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency. (2) Configure a `RedisRateLimiter` with replenish-rate=10 and burst-capacity=20 per user (resolved from JWT claim). (3) Add Redis to `docker-compose.yml`. (4) Apply the rate limiter as a default filter on all routes. Open a PR.

---

## Phase 3: Polish & Maturity (4-8 weeks)

Items that bring the project to production-grade maturity.

### 3.1 Add Integration Tests with Testcontainers (GAP-TEST-02)
**Severity:** High | **Effort:** Large

Write integration tests that verify database interactions, Feign contracts, and API behavior against real dependencies.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration tests using Testcontainers. (1) Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` to each service's test dependencies. (2) For `core-banking-service`: write integration tests that start a MySQL container, run Flyway migrations, and test the full request flow through controllers (using `@SpringBootTest` + `TestRestTemplate`). (3) For `internet-banking-fund-transfer-service`: write an integration test with WireMock standing in for `core-banking-service`. (4) Target at least 3 integration tests per service covering happy paths and error scenarios. Open a PR.

---

### 3.2 Add Contract Tests Between Services (GAP-TEST-03)
**Severity:** Medium | **Effort:** Large

Implement Spring Cloud Contract or Pact tests to verify inter-service API compatibility.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Spring Cloud Contract tests for all Feign client contracts. (1) In `core-banking-service` (the provider), add `spring-cloud-starter-contract-verifier` and write contract DSL files for each endpoint consumed by other services. (2) In consumer services, add `spring-cloud-starter-contract-stub-runner` and write contract tests that verify the Feign clients work correctly against the generated stubs. (3) Cover at minimum: `GET /api/v1/account/bank-account/{number}`, `POST /api/v1/transaction/fund-transfer`, `POST /api/v1/transaction/util-payment`, `GET /api/v1/user/{identification}`. Open a PR.

---

### 3.3 Create Multi-Project Gradle Build (GAP-ORG-01)
**Severity:** Medium | **Effort:** Medium

Unify all services under a single Gradle build for easier dependency management and CI.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a root-level multi-project Gradle build. (1) Create a root `settings.gradle` that includes all 6 service projects and the common library. (2) Create a root `build.gradle` that defines shared dependency versions (Spring Boot, Spring Cloud, etc.) using a `subprojects` block. (3) Remove version declarations from individual service `build.gradle` files. (4) Verify `./gradlew build` from the root builds all services. (5) Remove individual `gradlew` wrappers from each service. Open a PR.

---

### 3.4 Implement Saga / Compensation for Distributed Transactions (GAP-ERR-04)
**Severity:** Critical | **Effort:** Large

Add compensation logic for the fund transfer and utility payment orchestrations.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement a Saga pattern for the fund transfer flow in `internet-banking-fund-transfer-service`. (1) Wrap the Feign call to core-banking-service in a try-catch. (2) If the call fails after the local entity was saved as PENDING, update the entity status to `FAILED` and log the failure. (3) If the call succeeds but the local save fails, add a compensation endpoint on `core-banking-service` to reverse the transaction, and call it. (4) Apply the same pattern to `internet-banking-utility-payment-service`. (5) Add a `FAILED` value to the `TransactionStatus` enum. Open a PR.

---

### 3.5 Add Fallback Behavior for Feign Clients (GAP-RES-04)
**Severity:** Medium | **Effort:** Medium

Implement Feign fallback classes that return meaningful degraded responses.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create Feign fallback factory classes for all Feign clients. (1) In `internet-banking-fund-transfer-service`, create `BankingCoreFeignClientFallback` implementing `BankingCoreFeignClient` that returns error responses with a "Service temporarily unavailable" message. (2) Do the same for `internet-banking-utility-payment-service` and `internet-banking-user-service`. (3) Register the fallback factories using `@FeignClient(fallbackFactory = ...)`. (4) Log the cause of the fallback at WARN level. Open a PR.

---

### 3.6 Clean Up REST Resource Naming (GAP-API-01)
**Severity:** Medium | **Effort:** Medium

Refactor endpoints to follow RESTful conventions.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, refactor API endpoints to follow RESTful naming conventions. (1) In `internet-banking-user-service`, change `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users` and `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}`. (2) Update the API Gateway security configuration to match the new paths (especially the permitAll rule for registration). (3) Update the Postman collection to match. (4) Ensure backward compatibility by optionally keeping old routes with `@Deprecated` annotations and redirect mappings. Open a PR.

---

### 3.7 Add Dependency Vulnerability Scanning (GAP-SEC-06)
**Severity:** Medium | **Effort:** Small

Configure automated dependency vulnerability detection.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the OWASP Dependency-Check Gradle plugin. (1) Add `org.owasp.dependencycheck` plugin version 9.x to the root `build.gradle` (or to each service if no root build exists). (2) Configure it to fail the build on CVSS score >= 7. (3) Add a GitHub Actions workflow that runs `./gradlew dependencyCheckAnalyze` on every PR. (4) Run the check once and document any existing high-severity CVEs that need immediate attention. Open a PR.

---

### 3.8 Configure Code Formatting (GAP-ORG-04)
**Severity:** Low | **Effort:** Small

Add automated code formatting to enforce consistency.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the Spotless Gradle plugin for Java code formatting. (1) Add `com.diffplug.spotless` plugin to the root build (or each service). (2) Configure it to use Google Java Format. (3) Add a `spotlessCheck` step to CI. (4) Run `spotlessApply` once to format all existing code. Open a PR.

---

### 3.9 Add Actuator Endpoint Exposure Configuration (GAP-OBS-01)
**Severity:** Medium | **Effort:** Small

Configure which actuator endpoints are exposed and secured.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure Spring Boot Actuator for all services. Set `management.endpoints.web.exposure.include` to `health,info,prometheus,metrics,loggers,env` and `management.endpoint.health.show-details` to `when-authorized`. Ensure actuator endpoints are excluded from JWT authentication requirements in both the API Gateway security config and any downstream service security configs. Open a PR.

---

## Roadmap Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| Phase 1 — Quick Wins | 10 items | 4 Critical (ERR-01, SEC-01, SEC-02, RES-06) | 1-2 weeks |
| Phase 2 — Important | 11 items | 1 Critical (TEST-01) | 2-4 weeks |
| Phase 3 — Polish | 9 items | 1 Critical (ERR-04) | 4-8 weeks |

### Recommended Execution Order Within Phases

**Phase 1 (do in this order):**
1. Fix catch-all exception handler (immediate security fix)
2. Externalize secrets from Docker Compose (immediate security fix)
3. Add input validation (immediate security fix)
4. Add idempotency keys (prevents financial data corruption)
5. Configure Feign timeouts (prevents cascading failures)
6. Redact sensitive log data (compliance)
7. Fix Swagger dependency (developer experience)
8. Fix ResponseEntity types (developer experience)
9. Add Prometheus metrics (observability)
10. Fix Keycloak singleton (correctness)

**Phase 2 (do in this order):**
1. Add unit tests (safety net for subsequent changes)
2. Extract shared library (reduces maintenance burden for all future work)
3. Add circuit breakers (resilience)
4. Add Feign error decoders (error handling)
5. Add downstream service authentication (security)
6. Add retry policies (resilience)
7. Standardize error responses (API quality)
8. Add pagination metadata (API quality)
9. Add structured logging (observability)
10. Add health indicators (observability)
11. Add rate limiting (resilience)

**Phase 3 (do in this order):**
1. Multi-project Gradle build (enables CI improvements)
2. Integration tests (quality)
3. Contract tests (quality)
4. Saga / compensation pattern (data integrity)
5. Feign fallbacks (resilience)
6. REST naming cleanup (API quality)
7. Dependency vulnerability scanning (security)
8. Code formatting (consistency)
9. Actuator configuration (observability)
