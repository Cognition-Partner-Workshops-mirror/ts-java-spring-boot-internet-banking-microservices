# Remediation Roadmap

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## Phasing Strategy

| Phase | Focus | Timeline | Criteria |
|-------|-------|----------|----------|
| **Phase 1 — Quick Wins** | Security fixes, correctness bugs, small effort / high impact | 1-2 weeks | Critical + High severity, Small effort |
| **Phase 2 — Important** | Structural improvements, testing, resilience | 3-6 weeks | Critical/High severity with Medium/Large effort, plus Medium severity items |
| **Phase 3 — Polish** | Developer experience, advanced observability, API maturity | Ongoing | Low/Medium severity, nice-to-haves |

---

## Phase 1: Quick Wins (1-2 weeks)

### 1.1 Fix Exception Handler — Stop Leaking Internal Details

**Gap Ref:** 2.1 (Critical, Small) + 2.2 (High, Small)

Replace the generic catch-all exception handler in all 4 services to return a safe error response with proper HTTP status codes.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in all four services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). Replace the generic `Exception` handler to return HTTP 500 with a generic message like "An internal error occurred" — never expose the exception details to the client. Also update the `SimpleBankingGlobalException` handler to use proper HTTP status codes: `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `UserAlreadyRegisteredException` should return 409, `InvalidEmailException` should return 400, and `InvalidBankingUserException` should return 404. Add a test for each handler.

---

### 1.2 Fix the Double-Debit Bug in Balance Calculation

**Gap Ref:** 7.7 (Critical, Medium — but the specific bug fix is Small)

Fix the `availableBalance` calculation in `TransactionService.internalFundTransfer()` and `utilPayment()`.

**Devin Prompt:**
> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the `availableBalance` calculation bug. Currently in `internalFundTransfer()`, the code does `setAvailableBalance(getActualBalance().subtract(amount))` AFTER already subtracting from `actualBalance`, causing a double-subtraction. The same pattern exists in `utilPayment()`. Change these to `setAvailableBalance(getActualBalance())` since `actualBalance` has already been updated. Verify the fix passes the existing `TransactionServiceTest` tests and add a test that asserts the correct balances after a transfer.

---

### 1.3 Add Input Validation to All Request DTOs

**Gap Ref:** 4.2 (Critical, Medium)

Add Bean Validation annotations to all request DTOs and `@Valid` on controller parameters.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Jakarta Bean Validation to all request DTOs across all services. Specifically: (1) Add `spring-boot-starter-validation` to the `build.gradle` of core-banking-service, fund-transfer-service, user-service, and utility-payment-service. (2) Add `@NotBlank`, `@NotNull`, `@Positive`, `@Email`, and `@Size` annotations as appropriate to: `FundTransferRequest` (both versions), `UtilityPaymentRequest` (both versions), `User` (user-service DTO), and `UserUpdateRequest`. (3) Add `@Valid` before all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details.

---

### 1.4 Remove Hardcoded Credentials from Source

**Gap Ref:** 4.1 (Critical, Small)

Extract all credentials into environment variables.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, replace all hardcoded credentials in `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD:-changeme}`). Create a `docker-compose/.env.example` file listing all required environment variables with placeholder values. Update `docker-compose/mysql/privileges.sql` to use a placeholder password. Add a note to the README about copying `.env.example` to `.env` and setting real values. Remove the test credentials from `README.md` and the Keycloak client secret from `internet-banking-user-service/src/test/resources/application.yml`.

---

### 1.5 Fix OpenAPI Dependency (WebFlux → WebMVC)

**Gap Ref:** 5.5 (Medium, Small)

Replace the incorrect `springdoc-openapi-starter-webflux-ui` with the correct WebMVC variant.

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0` in the `build.gradle` files of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. These services use Spring WebMVC (not WebFlux). Verify each service builds successfully after the change.

---

### 1.6 Add ResponseEntity Type Parameters

**Gap Ref:** 5.1 (Medium, Small)

Add proper generics to all controller return types.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add proper type parameters to all `ResponseEntity` return types in all controller classes. For example, `ResponseEntity` should become `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`, `ResponseEntity<FundTransferResponse>`, etc. Update all controllers in core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service.

---

### 1.7 Add Feign Client Timeout Configuration

**Gap Ref:** 7.3 (High, Small)

Add sensible timeouts to prevent thread exhaustion.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Feign client timeout configuration. In the Spring Cloud Config repository (or in each service's `application.yml` if config server isn't available), add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add `spring.cloud.gateway.httpclient.connect-timeout: 5000` and `spring.cloud.gateway.httpclient.response-timeout: 15s` for the API Gateway.

---

### 1.8 Add Missing Error Codes to Fund Transfer and Utility Payment

**Gap Ref:** 2.4 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, create a `GlobalErrorCode` class in both `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` under the `exception` package, following the same pattern as `core-banking-service`. Define error codes like `FUND-TRANSFER-SERVICE-1000` for entity not found and `UTILITY-PAYMENT-SERVICE-1000` similarly. Update any exception throws in these services to include the error code.

---

### 1.9 Fix Logging Bug in Fund Transfer Service

**Gap Ref:** 6.1 (Medium, Small)

**Devin Prompt:**
> In `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java`, fix the logging statement `log.info("Sending fund transfer request {}" + request.toString())` — change it to `log.info("Sending fund transfer request {}", request)` to use SLF4J parameterized logging properly.

---

### 1.10 Add Prometheus Metrics Registry

**Gap Ref:** 6.3 (Medium, Small)

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add `implementation 'io.micrometer:micrometer-registry-prometheus'` to the `build.gradle` of all 6 services (or at least the 4 business services). Then ensure `management.endpoints.web.exposure.include=health,info,prometheus,metrics` is set in each service's application configuration. Verify the `/actuator/prometheus` endpoint returns metrics.

---

## Phase 2: Important (3-6 weeks)

### 2.1 Add Circuit Breakers with Resilience4j

**Gap Ref:** 7.1 (Critical, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add Resilience4j circuit breakers to the Feign clients in internet-banking-fund-transfer-service and internet-banking-utility-payment-service. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to their `build.gradle` files. (2) Enable circuit breaker integration with Feign: `spring.cloud.openfeign.circuitbreaker.enabled=true`. (3) Create fallback classes for `BankingCoreFeignClient` in both services that return a meaningful error response and update the local entity to `FAILED` status. (4) Configure circuit breaker parameters: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`. (5) Add unit tests for the fallback behavior.

---

### 2.2 Add Retry Policies to Feign Clients

**Gap Ref:** 7.2 (High, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add Spring Retry support to the Feign clients in fund-transfer-service and utility-payment-service. Add `spring-retry` dependency. Configure retry for transient errors only (5xx, connection timeouts) with max 3 attempts and exponential backoff (initial=1s, multiplier=2, max=5s). Make sure retries do NOT apply to POST endpoints unless idempotency is guaranteed (see Phase 2.3).

---

### 2.3 Implement Idempotency for Financial Transactions

**Gap Ref:** 7.6 (High, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, implement idempotency for fund transfers and utility payments. (1) Add an `idempotencyKey` field (String, unique) to `FundTransferEntity` and `UtilityPaymentEntity`. (2) Require clients to send an `X-Idempotency-Key` header. (3) In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, check if a record with the given idempotency key already exists — if so, return the existing response. (4) Add a unique constraint on the `idempotency_key` column. (5) Add tests to verify duplicate requests return the same response without re-processing.

---

### 2.4 Add Feign Error Decoder to Fund Transfer and Utility Payment

**Gap Ref:** 2.3 (High, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add a `CustomFeignErrorDecoder` (similar to the one in user-service) to both internet-banking-fund-transfer-service and internet-banking-utility-payment-service. The decoder should parse error responses from core-banking-service and throw appropriate exceptions (e.g., `EntityNotFoundException` for 404, `InsufficientFundsException` for 422). Also ensure that when a Feign call fails, the local entity status is updated to `FAILED`. Add unit tests for the error decoder.

---

### 2.5 Extract Shared Library

**Gap Ref:** 1.2 (High, Medium) + 1.1 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, create a shared library module called `internet-banking-common`. (1) Create a root `settings.gradle` that includes all 7 projects (common + 6 services). (2) Move duplicated code into the common module: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditConfig`, `AuditorAwareConfig`, and `TransactionStatus`. (3) Update each service's `build.gradle` to depend on `implementation project(':internet-banking-common')`. (4) Remove the duplicated classes from each service. (5) Ensure all services build and tests pass.

---

### 2.6 Add Unit Tests for All Services

**Gap Ref:** 3.1 (Critical, Large)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add comprehensive unit tests for the service and controller layers of internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. For each service: (1) Write Mockito-based unit tests for the service classes (e.g., `FundTransferServiceTest`, `UserServiceTest`, `UtilityPaymentServiceTest`). (2) Write `@WebMvcTest` controller tests using MockMvc for each controller. (3) Mock Feign clients and repositories. (4) Test happy path, validation errors, and error scenarios. (5) Fix the existing `contextLoads()` tests so they work without external dependencies by properly mocking or using test profiles. Target at least 80% line coverage per service.

---

### 2.7 Add Integration Tests with Testcontainers

**Gap Ref:** 3.2 (High, Large)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add integration tests using Testcontainers for core-banking-service. (1) Add `spring-boot-testcontainers` and `testcontainers-mysql` dependencies. (2) Create a `@TestConfiguration` that starts a MySQL container and configures the datasource. (3) Write integration tests for the full request lifecycle: create user → create account → fund transfer → verify balances. (4) Ensure Flyway migrations run against the test container. (5) Add a WireMock-based contract test for the Fund Transfer Service's Feign client that verifies it correctly calls Core Banking's API.

---

### 2.8 Secure Downstream Services

**Gap Ref:** 4.3 (Medium, Small) + 4.6 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add security to the downstream services (fund-transfer, user, utility-payment, core-banking) so they don't rely solely on the API gateway. (1) Add `spring-boot-starter-security` to each service's `build.gradle`. (2) Configure each service to validate the `X-Auth-Id` header by checking it was set by a trusted source (e.g., validate a shared internal secret header or add JWT validation). (3) Alternatively, restrict network access so downstream services only accept connections from the API gateway's IP (configure in Docker Compose). (4) Add tests for the security configuration.

---

### 2.9 Scope Database Privileges Per Service

**Gap Ref:** 4.4 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, update `docker-compose/mysql/privileges.sql` to create separate MySQL users for each service database. Create users `core_banking_user`, `fund_transfer_user`, `user_service_user`, and `utility_payment_user`, each with privileges limited to their own database only. Update each service's datasource configuration to use its dedicated user.

---

### 2.10 Add Dependency Vulnerability Scanning

**Gap Ref:** 4.5 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add the OWASP Dependency-Check Gradle plugin to all services. (1) Add `id 'org.owasp.dependencycheck' version '9.0.9'` to the plugins block in each `build.gradle`. (2) Configure it to fail the build on CVSS score >= 7. (3) Create a GitHub Actions workflow `.github/workflows/dependency-check.yml` that runs `./gradlew dependencyCheckAnalyze` on push and PR. (4) Run the check once and document any existing vulnerabilities that need immediate attention.

---

### 2.11 Add Pagination Metadata to List Endpoints

**Gap Ref:** 5.3 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, update all paginated list endpoints to return pagination metadata. (1) Create a generic `PageResponse<T>` wrapper class in the shared library with fields: `content`, `page`, `size`, `totalElements`, `totalPages`. (2) Update all list endpoints in core-banking-service, fund-transfer-service, user-service, and utility-payment-service to return `ResponseEntity<PageResponse<T>>` instead of `ResponseEntity<List<T>>`. (3) Update the corresponding service methods to return the full `Page` object and map it to `PageResponse`.

---

## Phase 3: Polish (Ongoing)

### 3.1 Add Rate Limiting at API Gateway

**Gap Ref:** 7.5 (Medium, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency. (2) Add a Redis container to docker-compose. (3) Configure rate limiting per route: 10 requests/second for fund-transfer and utility-payment, 50 requests/second for read endpoints. (4) Return HTTP 429 with a `Retry-After` header when rate limited.

---

### 3.2 Add Fallback Behavior for Degraded Services

**Gap Ref:** 7.4 (Medium, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, implement graceful degradation in the API Gateway. (1) Add circuit breaker filters to the gateway routes. (2) Configure fallback URIs that return cached or default responses when downstream services are unavailable. (3) For read endpoints, return a "Service temporarily unavailable" response with HTTP 503. (4) For write endpoints (fund transfer, payment), return HTTP 503 with a message asking the client to retry later.

---

### 3.3 Add Structured JSON Logging

**Gap Ref:** 6.1 (Medium, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, configure structured JSON logging for all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. (2) Create a `logback-spring.xml` configuration in each service's `src/main/resources/` that outputs JSON format with fields: timestamp, level, logger, message, traceId, spanId, service. (3) Add MDC enrichment for `userId` (from `ApiRequestContextHolder`) in the `AppAuthUserFilter`. (4) Ensure console output remains human-readable in the `dev` profile.

---

### 3.4 Add Custom Health Indicators

**Gap Ref:** 6.2 (Low, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add custom health indicators. (1) In user-service, add a `KeycloakHealthIndicator` that checks Keycloak connectivity. (2) In fund-transfer-service and utility-payment-service, add a `CoreBankingHealthIndicator` that checks Core Banking Service availability via the Feign client. (3) Expose health details: `management.endpoint.health.show-details=when-authorized`. (4) Add tests for each health indicator.

---

### 3.5 Complete Distributed Tracing Configuration

**Gap Ref:** 6.4 (Low, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, complete the tracing configuration. (1) Add tracing dependencies to internet-banking-config-server. (2) Set sampling rate to 100% for non-production: `management.tracing.sampling.probability=1.0`. (3) Configure a tracing export fallback so the application doesn't fail if Zipkin is unavailable: add `management.zipkin.tracing.connect-timeout=1s` and ensure exceptions on export are logged but not propagated.

---

### 3.6 Add Filtering and Search to List Endpoints

**Gap Ref:** 5.4 (Low, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, add filtering support to list endpoints. (1) In fund-transfer-service, add query parameters to `GET /api/v1/transfer`: `status`, `fromAccount`, `toAccount`, `dateFrom`, `dateTo`. (2) In utility-payment-service, add query parameters to `GET /api/v1/utility-payment`: `status`, `providerId`, `dateFrom`, `dateTo`. (3) In core-banking-service, add query parameters to `GET /api/v1/user`: `email`, `firstName`. (4) Use Spring Data JPA Specifications or QueryDSL for dynamic filtering.

---

### 3.7 Standardize Package Structure

**Gap Ref:** 1.3 (Low, Small)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, standardize the package structure across all services to follow a consistent convention: `com.javatodev.finance.{controller, service, service.rest, repository, model.entity, model.dto, model.mapper, model.enums, exception, configuration, configuration.security, configuration.audit, configuration.filter}`. Move user-service's `model.repository` to `repository` and align Feign client package paths.

---

### 3.8 Add CI/CD Pipeline with GitHub Actions

**Gap Ref:** 6.5 / general (Medium, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, create a GitHub Actions CI/CD pipeline. (1) Create `.github/workflows/ci.yml` that on push/PR: checks out code, sets up Java 21, runs `./gradlew build test` for each service in parallel. (2) Create `.github/workflows/docker-build.yml` that builds Docker images for all services on main branch pushes. (3) Add a Gradle wrapper validation step. (4) Add caching for Gradle dependencies. (5) Add a step that runs the OWASP dependency check.

---

### 3.9 Document API Versioning Strategy

**Gap Ref:** 5.2 (Low, Medium)

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, create an `API_VERSIONING.md` document in the `docs/` folder. Document the current URI-based versioning approach (`/api/v1/`), define rules for when to increment versions (breaking changes only), describe how to maintain backward compatibility, and outline the deprecation process. Add a note about this in the README.

---

### 3.10 Implement Notification Service (RabbitMQ)

**Gap Ref:** Not in gap analysis — referenced as PENDING in README

**Devin Prompt:**
> In `ts-java-spring-boot-internet-banking-microservices`, implement the pending Notification Service. (1) Create a new `internet-banking-notification-service` module. (2) Add RabbitMQ dependencies (`spring-boot-starter-amqp`). (3) Add RabbitMQ to docker-compose. (4) In fund-transfer-service and utility-payment-service, publish events to RabbitMQ after successful transactions. (5) In notification-service, consume these events and log them (email sending can be stubbed). (6) Register the service with Eureka and add tracing.

---

## Prioritized Checklist

```
Phase 1 (Quick Wins — 1-2 weeks)
  [  ] 1.1  Fix exception handler (stop leaking internals, proper HTTP codes)
  [  ] 1.2  Fix double-debit bug in TransactionService
  [  ] 1.3  Add input validation (Bean Validation)
  [  ] 1.4  Remove hardcoded credentials
  [  ] 1.5  Fix OpenAPI dependency (webflux → webmvc)
  [  ] 1.6  Add ResponseEntity type parameters
  [  ] 1.7  Add Feign client timeout configuration
  [  ] 1.8  Add error codes to fund-transfer and utility-payment
  [  ] 1.9  Fix logging bug in FundTransferService
  [  ] 1.10 Add Prometheus metrics registry

Phase 2 (Important — 3-6 weeks)
  [  ] 2.1  Add circuit breakers (Resilience4j)
  [  ] 2.2  Add retry policies
  [  ] 2.3  Implement idempotency for financial transactions
  [  ] 2.4  Add Feign error decoder to fund-transfer and utility-payment
  [  ] 2.5  Extract shared library module
  [  ] 2.6  Add unit tests for all services (target 80% coverage)
  [  ] 2.7  Add integration tests with Testcontainers
  [  ] 2.8  Secure downstream services
  [  ] 2.9  Scope database privileges per service
  [  ] 2.10 Add dependency vulnerability scanning
  [  ] 2.11 Add pagination metadata to list endpoints

Phase 3 (Polish — Ongoing)
  [  ] 3.1  Add rate limiting at API Gateway
  [  ] 3.2  Add fallback behavior for degraded services
  [  ] 3.3  Add structured JSON logging
  [  ] 3.4  Add custom health indicators
  [  ] 3.5  Complete distributed tracing configuration
  [  ] 3.6  Add filtering and search to list endpoints
  [  ] 3.7  Standardize package structure
  [  ] 3.8  Add CI/CD pipeline with GitHub Actions
  [  ] 3.9  Document API versioning strategy
  [  ] 3.10 Implement Notification Service (RabbitMQ)
```
