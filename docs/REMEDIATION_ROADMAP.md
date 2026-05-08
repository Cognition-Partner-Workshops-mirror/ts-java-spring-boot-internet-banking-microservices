# Remediation Roadmap — Internet Banking Microservices

## Phase 1: Quick Wins (Critical severity, Small effort)

These items fix correctness and security issues with minimal code changes.

### 1.1 Fix double-subtraction bug in fund transfer

**File:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java` lines 90-91 and 99-100

**Problem:** `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` was already subtracted, causing a 2× deduction. Same issue on the receiver side with addition.

**Fix:** Set `availableBalance` equal to the already-updated `actualBalance` (remove the extra `.subtract(amount)` / `.add(amount)`).

> **Devin prompt:** "Fix the double-subtraction bug in core-banking-service TransactionService.java lines 90-91 and 99-100. On line 91, change `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())`. On line 100, change `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))` to `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance())`. Add unit tests that assert actualBalance and availableBalance are each reduced/increased by exactly the transfer amount."

### 1.2 Fix double-subtraction bug in utility payment

**File:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java` lines 63-64

**Problem:** Same pattern — `availableBalance` double-subtracted.

> **Devin prompt:** "Fix the double-subtraction bug in core-banking-service TransactionService.java lines 63-64. Change line 64 from `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()))` to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())`. Add a unit test that asserts availableBalance equals actualBalance after a utility payment."

### 1.3 Fix cascade annotation on TransactionEntity

**File:** `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java` line 32

**Problem:** `@OneToOne(cascade = CascadeType.ALL)` should be `@ManyToOne` with no cascade.

> **Devin prompt:** "In core-banking-service TransactionEntity.java, change `@OneToOne(cascade = CascadeType.ALL)` on the `account` field to `@ManyToOne` with no cascade attribute. Verify the existing Flyway migration already has the correct FK constraint (it does — `banking_core_transaction.account_id` FK to `banking_core_account.id`). Run existing tests to confirm nothing breaks."

### 1.4 Fix exception handlers — proper status codes and no stack trace leaks

**Files:**
- `core-banking-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-user-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`

> **Devin prompt:** "In all four GlobalExceptionHandler classes across core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service: (1) Change the catch-all `handleException(Exception e)` to return HTTP 500 instead of 400, and replace the response body `'Exception occur inside API ' + e` with a generic JSON error `{code: 'INTERNAL_ERROR', message: 'An unexpected error occurred'}`. Log the full exception at ERROR level server-side. (2) Add specific `@ExceptionHandler` methods for `EntityNotFoundException` → 404, and keep `SimpleBankingGlobalException` → 400. Add tests for each handler."

### 1.5 Add `@Valid` and Bean Validation constraints

> **Devin prompt:** "Add `@Valid` annotation to every `@RequestBody` parameter across all controllers in all four business services. Add Jakarta Bean Validation annotations to all request DTOs: `@NotBlank` on string fields, `@NotNull` and `@Positive` on amount fields, `@NotNull` on required object fields. Add `jakarta.validation:jakarta.validation-api` dependency if not already present. Add unit tests that verify 400 responses for invalid input."

### 1.6 Protect password field in User DTO

**File:** `internet-banking-user-service/src/main/java/com/javatodev/finance/model/dto/User.java`

> **Devin prompt:** "In internet-banking-user-service User.java, add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field so it is accepted on POST but never serialized in GET responses. Add a test that verifies the password field is absent from the JSON response of GET /api/v1/bank-users/{id}."

### 1.7 Add Feign timeouts

> **Devin prompt:** "Add default Feign client timeout configuration to all services that use Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). In each service's `application.yml` (or bootstrap.yml), add `feign.client.config.default.connectTimeout: 5000` and `feign.client.config.default.readTimeout: 10000`. If config is managed by Spring Cloud Config, add to the config repo's application files."

### 1.8 Mask sensitive data in logs

> **Devin prompt:** "In internet-banking-user-service UserController.java, replace `log.info('Creating user with {}', request.toString())` with a log statement that does not include the password. Either override `toString()` in User.java to exclude password, or log only non-sensitive fields. Apply the same pattern to any other controller that logs request DTOs containing sensitive data."

---

## Phase 2: Important (High severity, Medium effort)

### 2.1 Add Resilience4j circuit breakers and retries

> **Devin prompt:** "Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` dependency to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Configure circuit breakers on all Feign clients with sensible defaults (failure rate threshold 50%, wait duration 30s, sliding window 10). Add retry configuration with max 3 attempts and exponential backoff. Add fallback methods that return meaningful error responses."

### 2.2 Add idempotency keys

> **Devin prompt:** "Add idempotency key support to the fund transfer and utility payment flows. Accept an `Idempotency-Key` HTTP header in FundTransferController and UtilityPaymentController. Store the key with the transaction record. Before processing, check if a transaction with the same idempotency key already exists and return the cached response if so. Add a unique constraint on the idempotency key column."

### 2.3 Add unit tests for all services

> **Devin prompt:** "Add comprehensive unit tests for internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-api-gateway, and internet-banking-service-registry. For each business service, test: (1) service layer methods with mocked dependencies, (2) controller layer with MockMvc, (3) mapper logic. Target at least 80% line coverage on service and controller classes."

### 2.4 Add Feign ErrorDecoder

> **Devin prompt:** "Create a custom Feign `ErrorDecoder` in each service that uses Feign (user-service, fund-transfer-service, utility-payment-service). Map HTTP 404 from core-banking-service to a local `EntityNotFoundException`, 400 to `BadRequestException`, and 5xx to `ServiceUnavailableException`. Register the decoder in the existing `CustomFeignClientConfiguration` class in each service."

### 2.5 Secure downstream services

> **Devin prompt:** "Add OAuth2 resource server configuration to core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service so they validate JWT tokens on incoming requests. Configure Feign clients to propagate the Authorization header using a `RequestInterceptor`. Alternatively, restrict Docker network access so only the API gateway can reach downstream service ports."

### 2.6 Externalize secrets

> **Devin prompt:** "In docker-compose/docker-compose.yml, replace all hardcoded passwords (MySQL root password, Keycloak admin password, PostgreSQL password) with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). Create a `.env.example` file with placeholder values. Add `.env` to `.gitignore`."

### 2.7 Fix Swagger dependency

> **Devin prompt:** "In the build.gradle of internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service, and core-banking-service, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` since all services are servlet-based (spring-boot-starter-web), not reactive."

---

## Phase 3: Polish (Medium severity, Medium/Large effort)

### 3.1 Extract shared library

> **Devin prompt:** "Create a new Gradle module `banking-common` containing the shared classes: `BaseMapper`, `AuditAware`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, and `GlobalErrorCode`. Publish it as a local dependency. Update all four business services to depend on `banking-common` and remove their local copies of these classes. Set up a Gradle multi-project build with a root `settings.gradle`."

### 3.2 Add integration tests

> **Devin prompt:** "Add integration tests using Testcontainers for MySQL and WireMock for Feign clients. For core-banking-service, test the full fund transfer and utility payment flows against a real MySQL database. For the orchestration services, test Feign client behavior with WireMock stubs. Add a Testcontainers-based test for Flyway migrations."

### 3.3 Add contract tests

> **Devin prompt:** "Add Spring Cloud Contract tests between the orchestration services (fund-transfer, utility-payment, user) and core-banking-service. Define contracts for each Feign client endpoint. Generate stubs from core-banking-service contracts and use them in consumer service tests."

### 3.4 Structured JSON logging

> **Devin prompt:** "Add `net.logstash.logback:logstash-logback-encoder` dependency to all services. Configure `logback-spring.xml` in each service to output JSON-formatted logs with fields: timestamp, level, logger, message, traceId, spanId, service name. Keep console text format for local development profile."

### 3.5 Custom health indicators

> **Devin prompt:** "Add custom `HealthIndicator` beans to each service: (1) core-banking-service: MySQL connectivity check, (2) user-service: Keycloak reachability check, (3) fund-transfer-service and utility-payment-service: core-banking-service Feign client health check. Register them with Spring Boot Actuator."

### 3.6 Custom Micrometer metrics

> **Devin prompt:** "Add custom Micrometer metrics to core-banking-service: counters for `banking.fund_transfer.count` and `banking.utility_payment.count`, timers for `banking.fund_transfer.duration` and `banking.utility_payment.duration`, and a gauge for active transaction count. Expose via `/actuator/prometheus` endpoint."

### 3.7 Standardize pagination with `Page<T>`

> **Devin prompt:** "In all controllers that accept `Pageable` parameters, change the return type from `ResponseEntity<List<T>>` to `ResponseEntity<Page<T>>` (or a custom `PageResponse<T>` wrapper). Remove the `.getContent()` calls in service methods that strip pagination metadata. This affects: UserController in user-service, FundTransferController, UtilityPaymentController, and UserController in core-banking-service."

### 3.8 Add rate limiting

> **Devin prompt:** "Add rate limiting to the API Gateway using Spring Cloud Gateway's `RequestRateLimiter` filter. Configure Redis-backed rate limiting with sensible defaults (e.g., 100 requests/second per client IP). Add Redis to docker-compose.yml. Add rate limit headers (X-RateLimit-Remaining, X-RateLimit-Limit) to responses."
