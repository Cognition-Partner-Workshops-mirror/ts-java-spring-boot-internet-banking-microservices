# Remediation Roadmap

This document prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (Small effort, high impact)

These items can be completed individually in under a day and address Critical/High severity gaps.

### 1.1 Fix Hardcoded Credentials in Docker Compose (Gap 4.1)
**Severity: Critical | Effort: Small**

Replace hardcoded passwords in `docker-compose.yml`, `docker-compose-support-apps.yml`, and `privileges.sql` with environment variable references. Create a `.env.example` file documenting required variables.

> **Devin Prompt:**
> *"In the `docker-compose/` directory, replace all hardcoded passwords and credentials in `docker-compose.yml`, `docker-compose-support-apps.yml`, and `mysql/privileges.sql` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `.env.example` file listing all required variables with placeholder values. Add `.env` to `.gitignore`. Do NOT commit any real credentials."*

---

### 1.2 Fix Error Response Consistency (Gaps 2.1, 2.2)
**Severity: High | Effort: Small**

Standardize all `GlobalExceptionHandler` implementations to use proper HTTP status codes and structured `ErrorResponse` for all exceptions.

> **Devin Prompt:**
> *"Refactor the `GlobalExceptionHandler` in all four services (`core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`). Make these changes: (1) Return `404 Not Found` for `EntityNotFoundException`. (2) Return `500 Internal Server Error` with a structured `ErrorResponse` (not a raw string) for generic `Exception`. (3) Ensure the `ErrorResponse` body always has `code` and `message` fields. (4) Make all handlers consistent across services."*

---

### 1.3 Fix Password Leakage in User DTO (Gap 4.5)
**Severity: High | Effort: Small**

Separate the User registration request from the response DTO to prevent password fields from appearing in API responses.

> **Devin Prompt:**
> *"In `internet-banking-user-service`, split the `User` DTO into `UserRegistrationRequest` (with password field) and `UserResponse` (without password). Update `UserController.createUser` to accept `UserRegistrationRequest` and return `UserResponse`. Add `@JsonIgnore` on the password field of the original `User` class as a safety net. Ensure the existing tests still compile and pass."*

---

### 1.4 Add Feign Timeout Configuration (Gap 7.3)
**Severity: High | Effort: Small**

Add explicit connection and read timeouts to prevent thread exhaustion.

> **Devin Prompt:**
> *"Add Feign client timeout configuration to `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. In each service's `application.yml`, add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add `spring.cloud.openfeign.client.config.core-banking-service.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 10000` for the specific named client."*

---

### 1.5 Add Feign Retry Policies (Gap 7.2)
**Severity: High | Effort: Small**

Configure Spring Retry for transient Feign failures.

> **Devin Prompt:**
> *"Add retry configuration for Feign clients in `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. Add `spring-retry` dependency to each service's `build.gradle`. Create a `RetryConfig` class that configures a `Retryer` bean with maxAttempts=3 and a 1-second backoff. Make sure retries only happen for GET requests (not POST) to avoid double-processing of fund transfers."*

---

### 1.6 Fix Raw ResponseEntity Types (Gap 5.1)
**Severity: Medium | Effort: Small**

Add generic type parameters to all `ResponseEntity` return types.

> **Devin Prompt:**
> *"In all controller classes across all services, add generic type parameters to `ResponseEntity` return types. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Check the actual return type from the service layer to determine the correct generic parameter. The User Service controllers already have this — use them as a reference."*

---

### 1.7 Fix OpenAPI Starter Dependency (Gap 5.5)
**Severity: Medium | Effort: Small**

Replace the WebFlux OpenAPI starter with the WebMVC one for business services.

> **Devin Prompt:**
> *"In the `build.gradle` files of `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. These services use Spring MVC, not WebFlux. The API Gateway already correctly uses WebFlux. Verify each service still compiles after the change."*

---

### 1.8 Fix Keycloak Singleton Thread-Safety (Gap 4.4)
**Severity: Medium | Effort: Small**

Make the Keycloak instance initialization thread-safe.

> **Devin Prompt:**
> *"In `internet-banking-user-service`, refactor `KeycloakProperties.getInstance()` to be thread-safe. Convert the `Keycloak` instance to a Spring `@Bean` method in a `@Configuration` class instead of using a manually-managed static singleton. Create a new `KeycloakConfig` class annotated with `@Configuration` that provides a `@Bean Keycloak keycloak(KeycloakProperties props)`. Remove the static field and `getInstance()` method from `KeycloakProperties`. Update `KeycloakManager` to inject the `Keycloak` bean directly."*

---

### 1.9 Add Security Headers to API Gateway (Gap 4.6)
**Severity: Medium | Effort: Small**

Configure standard security response headers.

> **Devin Prompt:**
> *"In `internet-banking-api-gateway`, add a `SecurityHeadersFilter` as a `GlobalFilter` bean that adds these response headers to every request: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=31536000; includeSubDomains`, `X-XSS-Protection: 0`, and `Cache-Control: no-store`. Register it in `GatewayConfiguration` or a new configuration class."*

---

### 1.10 Fix Pagination Responses (Gap 5.3)
**Severity: Medium | Effort: Small**

Return pagination metadata in list endpoints.

> **Devin Prompt:**
> *"Create a generic `PageResponse<T>` wrapper class in each service (or ideally a shared library) with fields: `content: List<T>`, `pageNumber: int`, `pageSize: int`, `totalElements: long`, `totalPages: int`. Update all controller methods that accept `Pageable` to return `ResponseEntity<PageResponse<...>>` instead of `ResponseEntity<List<...>>`. In the service layer, map `Page<Entity>` to `PageResponse<DTO>` instead of extracting just `.getContent()`."*

---

## Phase 2: Important (Medium effort, structural improvements)

These items require more coordination and may touch multiple services, but are essential for production readiness.

### 2.1 Add Input Validation (Gap 2.3)
**Severity: Critical | Effort: Medium**

Add Jakarta Bean Validation to all request DTOs and controllers.

> **Devin Prompt:**
> *"Add Jakarta Bean Validation across all services. (1) Add `spring-boot-starter-validation` dependency to each service's `build.gradle`. (2) Annotate request DTO fields: `FundTransferRequest.fromAccount` and `.toAccount` with `@NotBlank`, `.amount` with `@NotNull @DecimalMin("0.01")`; `UtilityPaymentRequest.providerId` with `@NotNull`, `.amount` with `@NotNull @DecimalMin("0.01")`, `.account` with `@NotBlank`; `User.email` with `@Email @NotBlank`, `.identification` with `@NotBlank`, `.password` with `@NotBlank @Size(min=8)`; `UserUpdateRequest.status` with `@NotNull`. (3) Add `@Valid` to all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns `400` with field-level error details in `ErrorResponse` format."*

---

### 2.2 Add Circuit Breakers (Gap 7.1)
**Severity: Critical | Effort: Medium**

Implement Resilience4j circuit breakers on all Feign clients.

> **Devin Prompt:**
> *"Add Resilience4j circuit breaker support to `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's `build.gradle`. (2) Enable circuit breaker for Feign: set `spring.cloud.openfeign.circuitbreaker.enabled=true` in each `application.yml`. (3) Create fallback classes for each Feign client that return meaningful error responses (e.g., `BankingCoreFeignClientFallback` returning an error `FundTransferResponse`). (4) Configure circuit breaker thresholds in `application.yml`: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`."*

---

### 2.3 Extract Shared Library (Gap 1.2)
**Severity: High | Effort: Medium**

Create a shared library module for duplicated code.

> **Devin Prompt:**
> *"Create a shared Gradle module called `banking-common` at the project root. (1) Create a root `settings.gradle` that includes all 7 modules. (2) Move these common classes into `banking-common/src/main/java/com/javatodev/finance/common/`: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`. (3) Add `banking-common` as a dependency in each service's `build.gradle`: `implementation project(':banking-common')`. (4) Remove the duplicated classes from each service and update imports. (5) Ensure all services still compile."*

---

### 2.4 Add Rate Limiting to API Gateway (Gap 4.3)
**Severity: High | Effort: Medium**

Configure Spring Cloud Gateway rate limiting for financial endpoints.

> **Devin Prompt:**
> *"Add rate limiting to `internet-banking-api-gateway`. (1) Add `spring-boot-starter-data-redis-reactive` dependency. (2) Configure `RequestRateLimiter` filter on routes for `/fund-transfer/**` and `/utility-payment/**` with `replenishRate=10` and `burstCapacity=20` per user. (3) Implement a `KeyResolver` bean that extracts the user ID from the JWT `sub` claim. (4) Add a Redis service to `docker-compose.yml` on the `javatodev_ib_network`. (5) Configure a fallback rate limiter using in-memory if Redis is unavailable."*

---

### 2.5 Secure Downstream Services (Gap 4.7)
**Severity: High | Effort: Medium**

Add authentication verification to downstream services.

> **Devin Prompt:**
> *"Add security to downstream services to prevent direct unauthenticated access. For `core-banking-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`: (1) Add `spring-boot-starter-security` dependency. (2) Create a `SecurityConfig` that requires a valid internal API key header (`X-Internal-Api-Key`) for all requests. The gateway should inject this header, and each service should validate it. (3) Alternatively, configure each service as an OAuth2 resource server (reusing the same Keycloak JWK Set URI). (4) Ensure inter-service Feign calls propagate the JWT token using a `RequestInterceptor`."*

---

### 2.6 Convert to Multi-Project Gradle Build (Gap 1.1)
**Severity: Medium | Effort: Medium**

Unify all services under a single Gradle build.

> **Devin Prompt:**
> *"Convert the project to a Gradle multi-project build. (1) Create a root `settings.gradle` that includes all service directories. (2) Create a root `build.gradle` with a `subprojects` block that configures shared settings: Java 21 source compatibility, Spring Boot 3.2.4 plugin, Spring Cloud 2023.0.0 BOM, common test dependencies, and the JUnit Platform. (3) Simplify each service's `build.gradle` to only declare service-specific dependencies. (4) Remove individual `gradlew`/`gradlew.bat`/`gradle/` directories from each service — keep only the root-level wrapper. (5) Verify `./gradlew build` from the root compiles all services."*

---

### 2.7 Fix Fund Transfer Atomicity and Balance Bug (Gap 7.5)
**Severity: Critical | Effort: Medium**

Fix the double-subtraction bug and add idempotency.

> **Devin Prompt:**
> *"Fix two critical bugs in `core-banking-service` `TransactionService`: (1) **Balance calculation bug**: In `internalFundTransfer`, line 91 sets `availableBalance = actualBalance - amount` AFTER `actualBalance` was already reduced by `amount` on line 90, causing a double subtraction. Fix: `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getAvailableBalance().subtract(amount))`. Apply the same fix to `toBankAccountEntity` (line 100) and in `utilPayment` (lines 63-64). (2) **Idempotency**: Add a `transactionReference` field to `FundTransferRequest` that callers must provide. Before processing, check if a transaction with that reference already exists — if so, return the existing result instead of processing again. Add a unique constraint on `transaction_reference` in `FundTransferEntity`. Add unit tests for both fixes."*

---

### 2.8 Add Structured Logging (Gap 6.1)
**Severity: Medium | Effort: Medium**

Configure JSON logging with correlation IDs.

> **Devin Prompt:**
> *"Configure structured JSON logging across all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service's `build.gradle`. (2) Create a shared `logback-spring.xml` in each service's `src/main/resources/` that outputs JSON format with fields: timestamp, level, logger, message, traceId, spanId, service name. (3) Remove all `toString()` calls on request objects in log statements to prevent logging sensitive data (passwords, account numbers). Replace with specific field logging. (4) Add WARN-level logging in `GlobalExceptionHandler` for all handled exceptions."*

---

### 2.9 Configure Health Checks and Metrics (Gaps 6.2, 6.3)
**Severity: Medium | Effort: Medium**

Set up proper health indicators and Prometheus metrics.

> **Devin Prompt:**
> *"Configure Actuator health checks and Prometheus metrics across all services. (1) In each service's `application.yml`, add: `management.endpoints.web.exposure.include=health,info,prometheus,metrics` and `management.endpoint.health.show-details=always`. (2) In `core-banking-service`, create a custom `HealthIndicator` that checks MySQL connectivity. (3) In `internet-banking-user-service`, create a `HealthIndicator` that checks Keycloak reachability. (4) Add `io.micrometer:micrometer-registry-prometheus` dependency to each service. (5) Add a Prometheus service to `docker-compose.yml` with a `prometheus.yml` that scrapes all service `/actuator/prometheus` endpoints."*

---

## Phase 3: Polish (Larger effort, long-term quality improvements)

These items improve long-term maintainability, developer experience, and operational maturity.

### 3.1 Add Unit Tests to All Services (Gap 3.1)
**Severity: Critical | Effort: Large**

Bring test coverage to all services to match core-banking-service.

> **Devin Prompt:**
> *"Add comprehensive unit tests to `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. For each service: (1) Create test `application.yml` with H2 in-memory database and disabled Eureka/Config Server (like core-banking-service's test config). (2) Write service-layer unit tests with Mockito mocks for repositories and Feign clients. Cover: successful operations, error cases (entity not found, validation failures), and edge cases. (3) Target at least 80% line coverage for service classes. (4) For `internet-banking-user-service`, mock `KeycloakUserService` and `BankingCoreRestClient`. Test user creation flow: duplicate email, invalid email, successful creation, user update with status approval."*

---

### 3.2 Add Integration Tests (Gap 3.2)
**Severity: High | Effort: Large**

Add API-layer and repository-layer integration tests.

> **Devin Prompt:**
> *"Add integration tests to `core-banking-service` as a reference implementation. (1) Add Testcontainers dependency: `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7`. (2) Create `@DataJpaTest` tests for `BankAccountRepository` and `TransactionRepository` using a Testcontainers MySQL instance. (3) Create `@WebMvcTest` controller tests for `AccountController` and `TransactionController` using `MockMvc` with mocked services. (4) Create a full `@SpringBootTest` integration test that tests the fund transfer flow end-to-end against a real MySQL container. (5) Document the testing patterns in a `TESTING.md` so the same approach can be replicated in other services."*

---

### 3.3 Add Contract Tests (Gap 3.3)
**Severity: High | Effort: Large**

Implement consumer-driven contract tests between services.

> **Devin Prompt:**
> *"Add Spring Cloud Contract tests between `internet-banking-fund-transfer-service` (consumer) and `core-banking-service` (producer). (1) Add `spring-cloud-starter-contract-verifier` to `core-banking-service` and `spring-cloud-starter-contract-stub-runner` to `internet-banking-fund-transfer-service`. (2) In `core-banking-service`, create contract definitions in `src/test/resources/contracts/` for: `GET /api/v1/account/bank-account/{number}` (found and not-found) and `POST /api/v1/transaction/fund-transfer` (success and insufficient funds). (3) Generate and verify producer-side tests. (4) In `internet-banking-fund-transfer-service`, write consumer-side tests using `@AutoConfigureStubRunner` that verify the Feign client works against the contract stubs."*

---

### 3.4 Add Fallback Behavior (Gap 7.4)
**Severity: Medium | Effort: Medium**

Implement graceful degradation patterns.

> **Devin Prompt:**
> *"Add fallback behavior for Feign client failures in all three consuming services. (1) For `internet-banking-fund-transfer-service`: Create a `BankingCoreFeignClientFallback` that returns `FundTransferResponse` with status `FAILED` and a user-friendly message. (2) For `internet-banking-utility-payment-service`: Create a fallback that returns `UtilityPaymentResponse` with status `FAILED`. (3) For `internet-banking-user-service`: Create a fallback for `BankingCoreRestClient` that throws a service-specific exception with a clear message. (4) Register fallbacks via `@FeignClient(fallback = ...)`. (5) Add unit tests for each fallback class."*

---

### 3.5 Add Bulkhead Pattern (Gap 7.6)
**Severity: Medium | Effort: Medium**

Isolate thread pools for different Feign clients.

> **Devin Prompt:**
> *"Configure Resilience4j bulkhead isolation for Feign clients. (1) In each consuming service, add Resilience4j bulkhead configuration in `application.yml` with `maxConcurrentCalls=10` and `maxWaitDuration=500ms` for the `core-banking-service` Feign client. (2) This requires the Resilience4j circuit breaker from Phase 2.2 to already be in place. (3) Add a `TimeLimiter` configuration with `timeoutDuration=5s`. (4) Add metrics exposure for bulkhead state so it's visible in Prometheus."*

---

### 3.6 Standardize URL Patterns (Gap 5.6)
**Severity: Low | Effort: Small**

Align all endpoints with RESTful conventions.

> **Devin Prompt:**
> *"Refactor API endpoints across all services to follow consistent RESTful conventions: (1) In `internet-banking-user-service`, rename `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users` (POST on a collection implies creation). Rename `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}`. (2) In `internet-banking-fund-transfer-service`, rename `/api/v1/transfer` to `/api/v1/transfers` (plural). (3) In `internet-banking-utility-payment-service`, rename `/api/v1/utility-payment` to `/api/v1/utility-payments` (plural). (4) Update the API Gateway route configurations to match. (5) Update the Postman collection if present."*

---

### 3.7 Add Custom Business Metrics (Gap 6.3)
**Severity: Medium | Effort: Medium**

Track key business KPIs through Micrometer.

> **Devin Prompt:**
> *"Add custom Micrometer metrics to track business KPIs. (1) In `core-banking-service`: Add a Counter `banking.transactions.total` tagged by `type` (FUND_TRANSFER, UTILITY_PAYMENT) and `status` (SUCCESS, FAILED). Add a Timer `banking.transactions.duration` for transaction processing time. (2) In `internet-banking-fund-transfer-service`: Add a Counter `banking.fund_transfers.total` with tags for status. Add a Gauge for pending transfers count. (3) In `internet-banking-utility-payment-service`: Add a Counter `banking.utility_payments.total`. (4) Inject `MeterRegistry` into service classes and record metrics at the appropriate points. (5) Verify metrics appear at `/actuator/prometheus`."*

---

### 3.8 Add Filtering and Sorting to List Endpoints (Gap 5.4)
**Severity: Low | Effort: Medium**

Extend list APIs with query parameters.

> **Devin Prompt:**
> *"Add filtering and sorting support to list endpoints. (1) In `core-banking-service` `UserController.readUsers`: Add optional query parameters `@RequestParam(required=false) String email` and `@RequestParam(required=false) String name`. Use Spring Data JPA `Specification<UserEntity>` to build dynamic queries. (2) In `internet-banking-fund-transfer-service`: Add filtering by `status`, `fromAccount`, and date range (`fromDate`/`toDate`) to `GET /api/v1/transfer`. (3) In `internet-banking-utility-payment-service`: Add filtering by `status` and `providerId` to `GET /api/v1/utility-payment`. (4) Sorting is already supported via Spring's `Pageable` — document the `sort` query parameter in OpenAPI annotations."*

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Total Effort |
|---|---|---|---|
| **Phase 1: Quick Wins** | 10 items | 1 (credentials) | ~7-8 days |
| **Phase 2: Important** | 9 items | 3 (validation, circuit breakers, fund transfer bug) | ~15-20 days |
| **Phase 3: Polish** | 8 items | 1 (unit tests) | ~20-25 days |

### Recommended Execution Order

1. **Immediate** (Phase 1.1): Fix hardcoded credentials — security risk
2. **Immediate** (Phase 2.7): Fix fund transfer balance bug — data integrity risk
3. **Week 1** (Phase 1.2–1.5): Error handling, timeouts, retries — stability baseline
4. **Week 2** (Phase 2.1–2.2): Input validation and circuit breakers — production readiness
5. **Week 3** (Phase 2.3, 2.6): Shared library and multi-project build — developer productivity
6. **Weeks 4-5** (Phase 2.4–2.5, 2.8–2.9): Security hardening and observability
7. **Ongoing** (Phase 3): Tests, contracts, and polish — long-term quality

---

## Dependencies Between Items

```
Phase 2.3 (Shared Library) ──► Phase 2.6 (Multi-Project Build)
Phase 2.2 (Circuit Breakers) ──► Phase 3.4 (Fallbacks) ──► Phase 3.5 (Bulkhead)
Phase 1.7 (OpenAPI Fix) ──► Phase 3.6 (URL Standardization)
Phase 2.9 (Metrics Setup) ──► Phase 3.7 (Custom Metrics)
Phase 3.1 (Unit Tests) ──► Phase 3.2 (Integration Tests) ──► Phase 3.3 (Contract Tests)
```
