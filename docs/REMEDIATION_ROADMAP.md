# Remediation Roadmap

## Overview

This roadmap organizes the 37 gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins:** High-impact fixes that are small effort and can be shipped independently. Focus on critical bugs, security holes, and foundational improvements.
- **Phase 2 — Important:** Structural improvements that require moderate effort. Focus on resilience, testing, and code quality.
- **Phase 3 — Polish:** Lower-priority enhancements that round out engineering maturity. Focus on observability, documentation, and advanced patterns.

Each item includes a sample Devin prompt that can be used to implement the fix.

---

## Phase 1 — Quick Wins

*Estimated total: 2–3 sprints. All items are Small effort unless noted.*

### 1.1 Fix Balance Calculation Bug (Critical)

**Gap:** 7.7 — `availableBalance` is double-subtracted on debits and double-added on credits in `TransactionService`.

**What to do:** Fix the `setAvailableBalance()` calls in `internalFundTransfer()` and `utilPayment()` to use the original balance minus amount (not the already-updated `actualBalance` minus amount).

<details>
<summary>Devin Prompt</summary>

```
In core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java, 
there is a balance calculation bug. In both internalFundTransfer() and utilPayment(), the 
availableBalance is set using the already-updated actualBalance, causing a double subtraction 
(or double addition). Fix it so availableBalance = actualBalance after the update (i.e., 
setAvailableBalance should use the same value that was just set on actualBalance, not subtract 
again). Also update the existing unit tests in TransactionServiceTest.java to assert correct 
balance values after transfers. Open a PR with the fix.
```
</details>

---

### 1.2 Stop Leaking Exception Details (Critical)

**Gap:** 2.2 — The catch-all `Exception` handler returns the full exception toString to clients.

**What to do:** Replace the generic exception handler in all 4 services to return a sanitized error message with HTTP 500.

<details>
<summary>Devin Prompt</summary>

```
In all four business services (core-banking-service, internet-banking-user-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service), the 
GlobalExceptionHandler has a catch-all @ExceptionHandler({Exception.class}) that returns 
"Exception occur inside API " + e, leaking internal details. Fix all four to return a 
generic ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred. 
Please try again later." with HTTP 500 status. Log the actual exception at ERROR level for 
debugging. Open a PR with the fix.
```
</details>

---

### 1.3 Use Correct HTTP Status Codes (High)

**Gap:** 2.1 — All exceptions return HTTP 400 regardless of type.

**What to do:** Map exception types to appropriate HTTP status codes in all `GlobalExceptionHandler` classes.

<details>
<summary>Devin Prompt</summary>

```
In all four business services, update GlobalExceptionHandler to use appropriate HTTP status 
codes: EntityNotFoundException should return 404, InsufficientFundsException should return 
422, UserAlreadyRegisteredException should return 409, InvalidEmailException and 
InvalidBankingUserException should stay 400. The catch-all Exception handler should return 
500 (from the previous fix). Open a PR with the changes.
```
</details>

---

### 1.4 Add Input Validation (Critical)

**Gap:** 4.1 — No Bean Validation on any request body or path variable.

**What to do:** Add `spring-boot-starter-validation` to all services and annotate request DTOs.

<details>
<summary>Devin Prompt</summary>

```
Add input validation across all services:
1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle for 
   core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, 
   and internet-banking-utility-payment-service.
2. Annotate request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
   - User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank password
   - UserUpdateRequest: @NotNull status
3. Add @Valid annotation to all @RequestBody parameters in controllers.
4. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler that returns 
   422 with field-level error details.
Open a PR with all changes.
```
</details>

---

### 1.5 Fix OpenAPI Dependency (Medium)

**Gap:** 5.6 — Servlet-based services incorrectly use the WebFlux OpenAPI starter.

**What to do:** Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in all servlet-based services.

<details>
<summary>Devin Prompt</summary>

```
In core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, 
and internet-banking-utility-payment-service, replace the Swagger dependency 
'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 
'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' since these are Spring MVC 
(servlet) services, not WebFlux. Verify each service still compiles. Open a PR.
```
</details>

---

### 1.6 Add ResponseEntity Type Parameters (Medium)

**Gap:** 5.1 — Raw `ResponseEntity` without generics.

**What to do:** Add type parameters to all controller method return types.

<details>
<summary>Devin Prompt</summary>

```
In all controllers across all services, add proper generic type parameters to ResponseEntity 
return types. For example, change 'public ResponseEntity getBankAccount(...)' to 
'public ResponseEntity<BankAccount> getBankAccount(...)'. This applies to:
- AccountController (core-banking): BankAccount, UtilityAccount
- TransactionController (core-banking): FundTransferResponse, UtilityPaymentResponse
- UserController (core-banking): User, List<User>
- FundTransferController: FundTransferResponse, List<FundTransfer>
- UtilityPaymentController: List<UtilityPayment>, UtilityPaymentResponse
Ensure all services still compile. Open a PR.
```
</details>

---

### 1.7 Externalize Secrets from Source Control (High)

**Gap:** 4.2 — Hardcoded credentials in docker-compose.yml and SQL files.

**What to do:** Replace hardcoded values with environment variable references and add a `.env.example`.

<details>
<summary>Devin Prompt</summary>

```
In docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml:
1. Replace all hardcoded passwords with environment variable references (e.g., 
   ${MYSQL_ROOT_PASSWORD:-defaultpassword}).
2. Create a docker-compose/.env.example file listing all required variables with placeholder 
   values.
3. Add docker-compose/.env to .gitignore.
4. Update docker-compose/mysql/privileges.sql to use a placeholder or document that the 
   password should be changed.
5. Remove the Keycloak client secret from internet-banking-user-service/src/test/resources/application.yml
   and replace with a test-only value or externalize it.
Open a PR.
```
</details>

---

### 1.8 Scope Database Privileges (Medium)

**Gap:** 4.3 — MySQL user has `*.*` privileges.

<details>
<summary>Devin Prompt</summary>

```
In docker-compose/mysql/privileges.sql, change the GRANT statement to scope privileges 
per-database instead of *.* :
  GRANT ALL PRIVILEGES ON banking_core_service.* TO 'javatodev_development'@'%';
  GRANT ALL PRIVILEGES ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
  GRANT ALL PRIVILEGES ON banking_core_user_service.* TO 'javatodev_development'@'%';
  GRANT ALL PRIVILEGES ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';
Open a PR.
```
</details>

---

### 1.9 Fix Keycloak Singleton Thread Safety (Medium)

**Gap:** 4.4 — Race condition in `KeycloakProperties.getInstance()`.

<details>
<summary>Devin Prompt</summary>

```
In internet-banking-user-service KeycloakProperties.java, the static Keycloak singleton has 
a race condition. Fix it by either:
(a) Using a synchronized block or double-checked locking, or
(b) Better: remove the static field entirely and make the Keycloak instance a @Bean in a 
    @Configuration class, letting Spring manage the singleton lifecycle.
Option (b) is preferred. Open a PR.
```
</details>

---

### 1.10 Fix Inconsistent Error Response Format (Medium)

**Gap:** 2.3 — Business exceptions use `ErrorResponse` but generic exceptions return plain strings.

<details>
<summary>Devin Prompt</summary>

```
Ensure all GlobalExceptionHandler classes in all four services return ErrorResponse objects 
consistently for every exception type. The catch-all Exception handler should return 
ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred").build() 
wrapped in ResponseEntity.status(500). Remove any plain string responses. Open a PR.
```
</details>

---

### 1.11 Fix Logging of Sensitive Data (Medium)

**Gap:** 6.1 — User registration logs passwords; log messages expose request details.

<details>
<summary>Devin Prompt</summary>

```
In internet-banking-user-service UserController.java, the createUser method logs the full 
User request object which includes the password field: 
log.info("Creating user with {}", request.toString()). Fix this by either:
1. Overriding toString() on the User DTO to exclude the password field, or
2. Logging only non-sensitive fields: log.info("Creating user with email={}", request.getEmail())
Apply the same review to all other controllers - ensure no sensitive data (passwords, account 
balances, full account numbers) is logged at INFO level. Open a PR.
```
</details>

---

## Phase 2 — Important

*Estimated total: 3–5 sprints. Mix of Small and Medium effort items.*

### 2.1 Add Circuit Breakers (Critical → Medium Effort)

**Gap:** 7.1 — No circuit breakers on Feign calls.

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j circuit breakers to all Feign client calls:
1. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j' to 
   build.gradle for internet-banking-user-service, internet-banking-fund-transfer-service, 
   and internet-banking-utility-payment-service.
2. Configure circuit breaker defaults in application.yml for each service:
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - slidingWindowSize: 10
3. Add @CircuitBreaker annotations on the Feign client methods or configure via 
   Resilience4j Feign integration.
4. Add fallback methods that return meaningful error responses when the circuit is open.
Open a PR.
```
</details>

---

### 2.2 Add Timeout and Retry Configuration (High → Small Effort)

**Gap:** 7.2, 7.3 — No timeouts or retries configured.

<details>
<summary>Devin Prompt</summary>

```
Add timeout and retry configuration for all Feign clients:
1. In each service's application.yml (or via Spring Cloud Config), add:
   spring.cloud.openfeign.client.config.default.connectTimeout: 5000
   spring.cloud.openfeign.client.config.default.readTimeout: 10000
2. Add Resilience4j retry configuration:
   - maxAttempts: 3
   - waitDuration: 1s
   - retryExceptions: [java.net.ConnectException, java.net.SocketTimeoutException]
   - ignoreExceptions: [com.javatodev.finance.exception.SimpleBankingGlobalException]
3. Ensure retries are only applied to idempotent operations (GET requests), not to 
   fund transfers or payments.
Open a PR.
```
</details>

---

### 2.3 Extract Shared Library (High → Medium Effort)

**Gap:** 1.2 — Duplicated code across services.

<details>
<summary>Devin Prompt</summary>

```
Create a shared library module called 'internet-banking-common' containing the duplicated 
classes:
1. Create a new Gradle module 'internet-banking-common' with its own build.gradle.
2. Move these classes into the common module under package com.javatodev.finance.common:
   - BaseMapper
   - AuditAware
   - AuditConfig + AuditorAwareConfig
   - ApiRequestContext + ApiRequestContextHolder + AppAuthUserFilter
   - SimpleBankingGlobalException
   - ErrorResponse
   - GlobalExceptionHandler
   - TransactionStatus enum
3. Add 'internet-banking-common' as a dependency in each service's build.gradle.
4. Remove the duplicated classes from each service and update imports.
5. Create a root settings.gradle that includes all 7 services + the common module.
6. Verify all services compile and tests pass.
Open a PR.
```
</details>

---

### 2.4 Add Feign Error Decoder to All Services (Medium → Medium Effort)

**Gap:** 2.5 — Only User Service has proper Feign error handling.

<details>
<summary>Devin Prompt</summary>

```
Port the CustomFeignErrorDecoder from internet-banking-user-service to 
internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
1. Copy CustomFeignErrorDecoder.java to both services (or put it in the shared library 
   if that exists already).
2. Update the CustomFeignClientConfiguration in each service to register the error decoder.
3. Verify that business exceptions from core-banking-service are properly propagated 
   through Feign to the calling services.
Open a PR.
```
</details>

---

### 2.5 Address Transaction Integrity (Critical → Medium Effort)

**Gap:** 7.6 — No saga/compensation pattern for distributed transactions.

<details>
<summary>Devin Prompt</summary>

```
Improve transaction integrity in internet-banking-fund-transfer-service and 
internet-banking-utility-payment-service:
1. Wrap the Feign call + local DB update in a try-catch. If the local update fails after 
   a successful remote call, log a CRITICAL error with the transaction reference so it 
   can be reconciled.
2. If the Feign call fails, update the local entity status to FAILED (currently it stays 
   PENDING forever on failure).
3. Add a scheduled job or endpoint to list PENDING/FAILED transactions for manual 
   reconciliation.
4. Document the consistency model and reconciliation process.
Open a PR.
```
</details>

---

### 2.6 Add Unit Tests for All Services (Critical → Large Effort)

**Gap:** 3.1 — Only core-banking has meaningful tests.

<details>
<summary>Devin Prompt</summary>

```
Add comprehensive unit tests for all services that currently only have contextLoads() tests:

For internet-banking-user-service:
- Test UserService: createUser (success, duplicate email, email mismatch, user not found), 
  readUsers, readUser, updateUser (approve flow, other status changes)
- Test KeycloakUserService: createUser, updateUser, readUserByEmail, readUser

For internet-banking-fund-transfer-service:
- Test FundTransferService: fundTransfer (success, core banking failure), readAllTransfers

For internet-banking-utility-payment-service:
- Test UtilityPaymentService: utilPayment (success, core banking failure), readPayments

Use Mockito to mock repositories and Feign clients. Target >80% line coverage on service 
classes. Open a PR.
```
</details>

---

### 2.7 Fix Test Configuration (Medium → Small Effort)

**Gap:** 3.4 — Test application.yml sets `ddl-auto: none` with Flyway disabled and H2.

<details>
<summary>Devin Prompt</summary>

```
Fix test configuration in all services' src/test/resources/application.yml:
1. Change spring.jpa.hibernate.ddl-auto from 'none' to 'create-drop' so H2 creates the 
   schema automatically for tests.
2. Ensure the contextLoads() tests pass for all services by verifying all required config 
   properties are set in test application.yml (especially for user-service which needs 
   Keycloak properties).
3. For user-service, either mock the Keycloak dependency in tests or add a test profile 
   that disables Keycloak integration.
Open a PR.
```
</details>

---

### 2.8 Add CORS Configuration (Medium → Small Effort)

**Gap:** 4.5 — No CORS policy configured.

<details>
<summary>Devin Prompt</summary>

```
Add CORS configuration to the API Gateway (internet-banking-api-gateway):
1. In SecurityConfiguration.java, add a CorsConfigurationSource bean that:
   - Allows configurable origins (via application property, default to localhost:3000)
   - Allows methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
   - Allows headers: Authorization, Content-Type, X-Auth-Id
   - Allows credentials
2. Apply the CORS configuration in the security filter chain.
3. Add the CORS origin as a configurable property in application.yml.
Open a PR.
```
</details>

---

### 2.9 Add Rate Limiting (Medium → Medium Effort)

**Gap:** 4.7 — No rate limiting on financial APIs.

<details>
<summary>Devin Prompt</summary>

```
Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in RequestRateLimiter:
1. Add 'org.springframework.boot:spring-boot-starter-data-redis-reactive' to the gateway's 
   build.gradle.
2. Add Redis to docker-compose.yml.
3. Configure the RequestRateLimiter filter in the gateway routes:
   - Default: 100 requests/second per IP
   - Fund transfer: 10 requests/second per authenticated user
   - Registration: 5 requests/minute per IP
4. Add a 429 Too Many Requests response with Retry-After header.
Open a PR.
```
</details>

---

### 2.10 Set Up Root Multi-Project Gradle Build (Medium → Small Effort)

**Gap:** 1.1 — No root build file.

<details>
<summary>Devin Prompt</summary>

```
Create a root-level Gradle multi-project build:
1. Create a root settings.gradle that includes all 7 service subprojects.
2. Create a root build.gradle with shared configuration:
   - Common repositories (mavenCentral)
   - Common Java 21 source compatibility
   - Common Spring Boot and Spring Cloud versions
   - Common test configuration (useJUnitPlatform)
3. Simplify each service's build.gradle to inherit from the root where possible.
4. Verify that 'gradle build' from the root directory builds all services.
5. Verify that 'gradle test' from the root runs all tests.
Open a PR.
```
</details>

---

## Phase 3 — Polish

*Estimated total: 3–4 sprints. Mix of Small, Medium, and Large effort items.*

### 3.1 Add Integration Tests (High → Large Effort)

**Gap:** 3.2 — No integration tests.

<details>
<summary>Devin Prompt</summary>

```
Add integration tests for each service:
1. For core-banking-service: Add @SpringBootTest + MockMvc tests for all controller 
   endpoints. Use H2 with Flyway migrations enabled (set spring.flyway.enabled=true 
   and configure H2 in MySQL compatibility mode).
2. For user-service, fund-transfer-service, utility-payment-service: Add @SpringBootTest 
   tests with WireMock to mock the core-banking-service Feign client responses.
3. For the API Gateway: Add WebTestClient tests to verify routing rules and security 
   (authenticated vs unauthenticated requests).
4. Add Testcontainers for MySQL-based integration tests that verify Flyway migrations 
   and actual database behavior.
Open a PR.
```
</details>

---

### 3.2 Add Contract Tests (High → Large Effort)

**Gap:** 3.3 — No consumer-driven contracts between services.

<details>
<summary>Devin Prompt</summary>

```
Add Spring Cloud Contract tests between services:
1. Add spring-cloud-contract-verifier to core-banking-service (the provider).
2. Define contracts in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/user/{identification}
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Generate contract stubs that consumer services can use in tests.
4. Add spring-cloud-contract-stub-runner to consumer services (user-service, 
   fund-transfer-service, utility-payment-service) and write tests against the stubs.
Open a PR.
```
</details>

---

### 3.3 Add Custom Metrics and Prometheus (Medium → Medium Effort)

**Gap:** 6.3 — No custom metrics or Prometheus registry.

<details>
<summary>Devin Prompt</summary>

```
Add Prometheus metrics to all services:
1. Add 'io.micrometer:micrometer-registry-prometheus' to all service build.gradle files.
2. Enable the Prometheus actuator endpoint in application.yml:
   management.endpoints.web.exposure.include: health,info,prometheus
3. Add custom metrics using Micrometer in key services:
   - core-banking: Counter for fund transfers (success/failure), timer for transaction processing
   - fund-transfer: Counter for transfers initiated, gauge for pending transfers
   - utility-payment: Counter for payments processed, gauge for pending payments
4. Add a Prometheus container to docker-compose.yml with scrape configs for all services.
5. Optionally add a Grafana container with a pre-built dashboard.
Open a PR.
```
</details>

---

### 3.4 Add Structured JSON Logging (Medium → Small Effort)

**Gap:** 6.1 (partial) — No structured logging.

<details>
<summary>Devin Prompt</summary>

```
Configure structured JSON logging across all services:
1. Add 'ch.qos.logback:logback-classic' JSON encoder or 
   'net.logstash.logback:logstash-logback-encoder:7.4' to all services.
2. Create a logback-spring.xml in each service's resources/ that outputs JSON in 
   production (docker profile) and plain text in development (default profile).
3. Include trace ID and span ID from Micrometer in log entries.
4. Include the service name and instance ID in each log line.
Open a PR.
```
</details>

---

### 3.5 Add Fallback Behavior (Medium → Medium Effort)

**Gap:** 7.4 — No fallback methods for Feign calls.

<details>
<summary>Devin Prompt</summary>

```
Add fallback behavior for Feign client failures:
1. For fund-transfer-service readAllTransfers: If core banking is down, still return 
   locally stored transfer records (they already exist in the local DB).
2. For utility-payment-service readPayments: Same approach - serve from local DB.
3. For write operations (fund transfer, utility payment): Return a clear error message 
   indicating the core banking service is temporarily unavailable, and mark the local 
   record as FAILED.
4. Implement Feign fallback factory classes for each Feign client.
Open a PR.
```
</details>

---

### 3.6 Add Bulkhead Pattern (Medium → Medium Effort)

**Gap:** 7.5 — No thread pool isolation.

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j bulkhead configuration to isolate thread pools:
1. Configure a ThreadPoolBulkhead for each Feign client:
   - maxThreadPoolSize: 10
   - coreThreadPoolSize: 5
   - queueCapacity: 20
2. Add @Bulkhead annotations on Feign client methods.
3. Add configuration in application.yml for each bulkhead instance.
4. Add metrics for bulkhead utilization (via Prometheus).
Open a PR.
```
</details>

---

### 3.7 Normalize Endpoint Naming (Low → Small Effort)

**Gap:** 5.5 — Inconsistent naming conventions.

<details>
<summary>Devin Prompt</summary>

```
Normalize REST endpoint naming across all services to follow consistent conventions:
1. Use kebab-case for all path segments (already mostly done).
2. Remove redundant path segments: Change PATCH /update/{id} to PATCH /{id} in 
   user-service UserController.
3. Standardize path variable naming: Use {accountNumber} instead of {account_number}, 
   or pick one style and apply consistently.
4. Update Feign clients and Postman collection to match the new paths.
5. Document the naming convention in a CONTRIBUTING.md or API guide.
Open a PR.
```
</details>

---

### 3.8 Add Pagination Metadata to Responses (Low → Small Effort)

**Gap:** 5.3 — No pagination metadata in list responses.

<details>
<summary>Devin Prompt</summary>

```
Add pagination metadata to all paginated endpoints:
1. Create a generic PagedResponse<T> wrapper class (in the shared library) with fields:
   content, page, size, totalElements, totalPages, hasNext, hasPrevious.
2. Update all GET list endpoints to return PagedResponse instead of List:
   - GET /api/v1/user (core-banking)
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/transfer (fund-transfer)
   - GET /api/v1/utility-payment (utility-payment)
3. Configure default page size (20) and maximum page size (100) in each service.
Open a PR.
```
</details>

---

### 3.9 Add Custom Health Checks (Low → Small Effort)

**Gap:** 6.2 — No custom health indicators.

<details>
<summary>Devin Prompt</summary>

```
Add custom health indicators to services:
1. In user-service: Add a KeycloakHealthIndicator that checks connectivity to the 
   Keycloak server.
2. In fund-transfer-service and utility-payment-service: Add a CoreBankingHealthIndicator 
   that checks if core-banking-service is reachable (via Eureka or a simple HTTP ping 
   to its actuator/health endpoint).
3. Expose health details in application.yml:
   management.endpoint.health.show-details: always
Open a PR.
```
</details>

---

### 3.10 Enrich Distributed Tracing (Low → Small Effort)

**Gap:** 6.4 — No custom spans or business context in traces.

<details>
<summary>Devin Prompt</summary>

```
Enrich distributed tracing with business context:
1. Add @NewSpan or manual span creation in key business methods:
   - TransactionService.fundTransfer, TransactionService.utilPayment
   - UserService.createUser
   - FundTransferService.fundTransfer
   - UtilityPaymentService.utilPayment
2. Add span tags for business context: transaction.id, account.from, account.to, 
   transfer.amount, user.identification.
3. Ensure Feign client calls propagate trace context (already handled by 
   feign-micrometer, but verify).
Open a PR.
```
</details>

---

### 3.11 Add Centralized Log Aggregation (Medium → Medium Effort)

**Gap:** 6.5 — No log aggregation solution.

<details>
<summary>Devin Prompt</summary>

```
Add centralized log aggregation using Loki + Grafana:
1. Add Loki and Grafana containers to docker-compose.yml.
2. Configure the Loki Docker logging driver for all service containers, OR
   use Promtail as a sidecar to ship logs from containers.
3. Add a pre-built Grafana dashboard for log exploration filtered by service name, 
   trace ID, and log level.
4. Document how to access logs at http://localhost:3000 (Grafana).
Open a PR.
```
</details>

---

### 3.12 Add Error Codes to All Services (Low → Small Effort)

**Gap:** 2.4 — Fund Transfer and Utility Payment services have no `GlobalErrorCode`.

<details>
<summary>Devin Prompt</summary>

```
Add GlobalErrorCode classes to internet-banking-fund-transfer-service and 
internet-banking-utility-payment-service:
1. Create GlobalErrorCode in fund-transfer-service with codes:
   FUND_TRANSFER_SERVICE_1000 (entity not found), FUND_TRANSFER_SERVICE_1001 (transfer failed)
2. Create GlobalErrorCode in utility-payment-service with codes:
   UTILITY_PAYMENT_SERVICE_1000 (entity not found), UTILITY_PAYMENT_SERVICE_1001 (payment failed)
3. Use these codes when throwing SimpleBankingGlobalException in each service.
Open a PR.
```
</details>

---

### 3.13 Add API Versioning Strategy (Medium → Medium Effort)

**Gap:** 5.2 — No mechanism to support multiple API versions.

<details>
<summary>Devin Prompt</summary>

```
Document and implement an API versioning strategy:
1. Document in a new docs/API_VERSIONING.md the chosen strategy: URI path versioning 
   (already using /api/v1/) with support for running v1 and v2 simultaneously.
2. Add an example v2 controller in core-banking-service that demonstrates how a new 
   version would be added alongside v1.
3. Configure the API Gateway to route /v1/** and /v2/** to the appropriate service 
   versions.
Open a PR.
```
</details>

---

## Summary Matrix

| Phase | Item | Gap # | Severity | Effort | Category |
|-------|------|-------|----------|--------|----------|
| **1** | Fix balance calculation bug | 7.7 | Critical | Small | Resilience |
| **1** | Stop leaking exception details | 2.2 | Critical | Small | Error Handling |
| **1** | Use correct HTTP status codes | 2.1 | High | Small | Error Handling |
| **1** | Add input validation | 4.1 | Critical | Medium | Security |
| **1** | Fix OpenAPI dependency | 5.6 | Medium | Small | API Design |
| **1** | Add ResponseEntity generics | 5.1 | Medium | Small | API Design |
| **1** | Externalize secrets | 4.2 | High | Small | Security |
| **1** | Scope database privileges | 4.3 | Medium | Small | Security |
| **1** | Fix Keycloak thread safety | 4.4 | Medium | Small | Security |
| **1** | Fix error response format | 2.3 | Medium | Small | Error Handling |
| **1** | Fix sensitive data logging | 6.1 | Medium | Small | Observability |
| **2** | Add circuit breakers | 7.1 | Critical | Medium | Resilience |
| **2** | Add timeouts and retries | 7.2/7.3 | High | Small | Resilience |
| **2** | Extract shared library | 1.2 | High | Medium | Code Organization |
| **2** | Add Feign error decoder everywhere | 2.5 | Medium | Medium | Error Handling |
| **2** | Address transaction integrity | 7.6 | Critical | Medium | Resilience |
| **2** | Add unit tests | 3.1 | Critical | Large | Testing |
| **2** | Fix test configuration | 3.4 | Medium | Small | Testing |
| **2** | Add CORS configuration | 4.5 | Medium | Small | Security |
| **2** | Add rate limiting | 4.7 | Medium | Medium | Security |
| **2** | Set up root Gradle build | 1.1 | Medium | Small | Code Organization |
| **3** | Add integration tests | 3.2 | High | Large | Testing |
| **3** | Add contract tests | 3.3 | High | Large | Testing |
| **3** | Add Prometheus metrics | 6.3 | Medium | Medium | Observability |
| **3** | Add structured JSON logging | 6.1 | Medium | Small | Observability |
| **3** | Add fallback behavior | 7.4 | Medium | Medium | Resilience |
| **3** | Add bulkhead pattern | 7.5 | Medium | Medium | Resilience |
| **3** | Normalize endpoint naming | 5.5 | Low | Small | API Design |
| **3** | Add pagination metadata | 5.3 | Low | Small | API Design |
| **3** | Add custom health checks | 6.2 | Low | Small | Observability |
| **3** | Enrich distributed tracing | 6.4 | Low | Small | Observability |
| **3** | Add centralized logging | 6.5 | Medium | Medium | Observability |
| **3** | Add error codes everywhere | 2.4 | Low | Small | Error Handling |
| **3** | Add API versioning strategy | 5.2 | Medium | Medium | API Design |

### Remaining Low-Priority Gaps (Not Scheduled)

These items are Low severity and can be addressed opportunistically:

| Gap # | Description |
|-------|-------------|
| 1.3 | Inconsistent package structure — address during shared library extraction |
| 1.4 | Mappers not Spring beans — address during shared library extraction |
| 4.6 | CSRF disabled without documentation — add comment in SecurityConfiguration |
| 5.4 | No filtering/sorting documentation — address with OpenAPI annotations |
