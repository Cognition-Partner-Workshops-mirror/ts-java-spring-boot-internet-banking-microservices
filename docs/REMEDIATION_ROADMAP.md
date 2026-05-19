# Remediation Roadmap

> Prioritised plan for closing the engineering gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md).

---

## Phase 1 — Quick Wins (High Severity / Small Effort)

Items that can be completed in 1–2 sessions each and address critical or high-severity gaps.

---

### 1.1 Add Input Validation to All Request DTOs

**Gap ref:** 4.1 (Critical / Small)

Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`) to all request DTOs and add `@Valid` to controller method parameters. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler`.

**Devin prompt:**
```
Add Jakarta Bean Validation to all request DTOs and controllers across all services in ts-java-spring-boot-internet-banking-microservices. Specifically:

1. In core-banking-service FundTransferRequest: @NotBlank on fromAccount and toAccount, @NotNull @Min(1) on amount.
2. In core-banking-service UtilityPaymentRequest: @NotNull on providerId, @NotNull @Min(1) on amount, @NotBlank on referenceNumber and account.
3. In internet-banking-fund-transfer-service FundTransferRequest: same as above.
4. In internet-banking-utility-payment-service UtilityPaymentRequest: same as above.
5. In internet-banking-user-service User DTO: @NotBlank on email and identification, @NotBlank @Size(min=8) on password.
6. Add @Valid to all @RequestBody parameters in all controllers.
7. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns 400 with structured ErrorResponse listing field errors.

Run all tests after changes.
```

---

### 1.2 Fix HTTP Status Codes in Exception Handlers

**Gap ref:** 2.1 (High / Small), 2.2 (Medium / Small)

Update all `GlobalExceptionHandler` classes to return proper HTTP status codes and consistent error format.

**Devin prompt:**
```
Fix HTTP status codes in all GlobalExceptionHandler classes across ts-java-spring-boot-internet-banking-microservices:

1. EntityNotFoundException → return 404 Not Found (not 400).
2. InsufficientFundsException → return 422 Unprocessable Entity (not 400).
3. Generic Exception handler → return 500 Internal Server Error with a structured ErrorResponse (code: "INTERNAL_ERROR", message: "An unexpected error occurred"). Do NOT leak the exception message or stack trace to the client. Log the full exception server-side.
4. Ensure ErrorResponse class is consistent across all services: use @Builder pattern everywhere.
5. Fund-transfer-service ErrorResponse uses constructor — update it to use @Builder for consistency.

Run all tests after changes.
```

---

### 1.3 Remove Hardcoded Credentials

**Gap ref:** 4.2 (Critical / Small)

Replace hardcoded passwords in Docker Compose and SQL files with environment variable references.

**Devin prompt:**
```
Remove hardcoded credentials from ts-java-spring-boot-internet-banking-microservices:

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD:-changeme}
   - Replace KC_DB_PASSWORD with ${KC_DB_PASSWORD:-changeme}
   - Replace KEYCLOAK_ADMIN_PASSWORD with ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
   - Replace POSTGRES_PASSWORD with ${POSTGRES_PASSWORD:-changeme}
2. In docker-compose/mysql/privileges.sql: replace the hardcoded password with a placeholder and add a comment instructing users to set it via environment variable.
3. Create a docker-compose/.env.example file listing all required environment variables with safe defaults.
4. Update README.md to reference the .env.example file and remove the plaintext test credentials section (or move it to a separate TESTING.md).
```

---

### 1.4 Fix OpenAPI Dependency (webflux → webmvc)

**Gap ref:** 5.4 (Medium / Small)

Replace the incorrect `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` for servlet-based services.

**Devin prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, fix the OpenAPI dependency for servlet-based services:

1. In core-banking-service/build.gradle, internet-banking-fund-transfer-service/build.gradle, internet-banking-user-service/build.gradle, and internet-banking-utility-payment-service/build.gradle:
   - Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
2. The API Gateway is WebFlux-based so it should keep the webflux dependency if present (it currently does not have one — leave as-is).
3. Build all services to verify no compilation errors.
```

---

### 1.5 Add Type Parameters to ResponseEntity

**Gap ref:** 5.1 (Medium / Small)

Add generic type parameters to all `ResponseEntity` return types in controllers.

**Devin prompt:**
```
Add generic type parameters to all ResponseEntity return types in ts-java-spring-boot-internet-banking-microservices controllers:

1. core-banking-service AccountController: ResponseEntity<BankAccount> for both methods.
2. core-banking-service TransactionController: ResponseEntity<FundTransferResponse> and ResponseEntity<UtilityPaymentResponse>.
3. core-banking-service UserController: ResponseEntity<User> and ResponseEntity<List<User>>.
4. internet-banking-fund-transfer-service FundTransferController: ResponseEntity<FundTransferResponse> and ResponseEntity<List<FundTransfer>>.
5. internet-banking-utility-payment-service UtilityPaymentController: ResponseEntity<List<UtilityPayment>> and ResponseEntity<UtilityPaymentResponse>.

Build all services to verify.
```

---

### 1.6 Add Pagination Metadata to List Endpoints

**Gap ref:** 5.3 (Medium / Small)

Return Spring Data's `Page<T>` wrapper instead of raw `List<T>` from paginated endpoints.

**Devin prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update all paginated GET endpoints to return page metadata:

1. In core-banking-service UserController.readUsers(): return ResponseEntity<Page<User>> and update UserService to return Page<User>.
2. In internet-banking-fund-transfer-service FundTransferController.readFundTransfers(): return Page<FundTransfer> and update FundTransferService.readAllTransfers().
3. In internet-banking-user-service UserController.readUsers(): return Page<User> and update UserService.readUsers().
4. In internet-banking-utility-payment-service UtilityPaymentController.readPayments(): return Page<UtilityPayment> and update UtilityPaymentService.readPayments().

The response should include totalElements, totalPages, number, size alongside the content list.
```

---

### 1.7 Fix Sensitive Data Logging

**Gap ref:** 6.1 (Medium / Small)

Remove `toString()` calls on request objects that may contain sensitive data (passwords, account numbers).

**Devin prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, fix sensitive data in log statements:

1. In internet-banking-user-service UserController.createUser(): do NOT log the full User object (contains password). Log only the email.
2. In internet-banking-user-service UserController.updateUser(): do NOT log the full UserUpdateRequest. Log only the user ID.
3. In internet-banking-fund-transfer-service FundTransferService: replace request.toString() with a safe summary (fromAccount last 4 digits, amount).
4. In core-banking-service TransactionController: replace toString() with safe summaries.
5. Review all log.info() calls in controllers/services and ensure no passwords, full account numbers, or tokens are logged.
```

---

### 1.8 Add Feign Timeout Configuration

**Gap ref:** 7.3 (High / Small)

Configure explicit timeouts for all Feign clients.

**Devin prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign timeout configuration:

1. In each service that uses Feign (fund-transfer-service, utility-payment-service, user-service), add timeout configuration. This can be done via the Spring Cloud Config repo or via each service's application.yml.
2. Set connect-timeout to 5000ms and read-timeout to 10000ms:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000

3. Build all services to verify.
```

---

### 1.9 Fix Keycloak Singleton and Exception Handling

**Gap ref:** 4.5 (Medium / Small), 2.4 (Medium / Small)

Fix the Keycloak client anti-patterns.

**Devin prompt:**
```
In ts-java-spring-boot-internet-banking-microservices internet-banking-user-service:

1. KeycloakProperties: replace the static singleton with a Spring @Bean so Spring manages the lifecycle. Create a @Configuration class with a @Bean method that returns the Keycloak instance.
2. KeycloakUserService.readUser(): replace the catch-all Exception handler. Catch specific Keycloak exceptions (NotFoundException for missing users, ProcessingException for connectivity). Log and rethrow with appropriate custom exceptions.

Build and run tests.
```

---

## Phase 2 — Important (High Severity / Medium Effort)

Items that require more implementation work but significantly improve system reliability and maintainability.

---

### 2.1 Add Circuit Breakers with Resilience4j

**Gap ref:** 7.1 (Critical / Medium), 7.4 (Medium / Medium)

Add Resilience4j circuit breakers to all Feign clients with fallback behavior.

**Devin prompt:**
```
Add Resilience4j circuit breakers to ts-java-spring-boot-internet-banking-microservices:

1. Add dependencies to fund-transfer-service, utility-payment-service, and user-service build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. For each @FeignClient, create a fallback factory class:
   - BankingCoreFeignClientFallbackFactory in fund-transfer-service: return a meaningful error response on circuit open.
   - BankingCoreRestClientFallbackFactory in utility-payment-service: same pattern.
   - BankingCoreRestClientFallbackFactory in user-service: same pattern.

3. Add circuit breaker configuration in each service's application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         core-banking-service:
           sliding-window-size: 10
           failure-rate-threshold: 50
           wait-duration-in-open-state: 30s
           permitted-number-of-calls-in-half-open-state: 5

4. Update @FeignClient annotations with fallbackFactory parameter.

Build and test all services.
```

---

### 2.2 Add Retry Policies

**Gap ref:** 7.2 (High / Medium)

Configure retry for transient failures in Feign calls.

**Devin prompt:**
```
Add retry policies to Feign clients in ts-java-spring-boot-internet-banking-microservices:

1. Add Spring Retry dependency to fund-transfer-service, utility-payment-service, and user-service:
   implementation 'org.springframework.retry:spring-retry'

2. Configure retry in application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               retryer: feign.Retryer.Default

3. IMPORTANT: Only retry on safe/idempotent operations (GET requests). Do NOT retry POST (fund-transfer, payment) without idempotency keys — this must be addressed together with gap 7.5.

Build and test.
```

---

### 2.3 Extract Shared Library (banking-common)

**Gap ref:** 1.2 (High / Medium), 1.1 (Medium / Medium)

Create a shared library module for duplicated code.

**Devin prompt:**
```
Create a banking-common shared library in ts-java-spring-boot-internet-banking-microservices:

1. Create a new module banking-common/ with its own build.gradle (Java library, no Spring Boot plugin — use spring-boot-dependencies BOM for version management).
2. Move the following shared classes into banking-common:
   - exception/SimpleBankingGlobalException.java
   - exception/ErrorResponse.java
   - exception/GlobalExceptionHandler.java
   - exception/EntityNotFoundException.java
   - model/dto/AuditAware.java
   - model/mapper/BaseMapper.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/audit/AuditConfig.java
   - configuration/audit/AuditorAwareConfig.java
3. Update each service's build.gradle to depend on banking-common (use Gradle composite builds or publish to mavenLocal).
4. Delete the duplicated classes from each service.
5. Build and test all services.
```

---

### 2.4 Add Unit Tests to All Services

**Gap ref:** 3.1 (Critical / Large)

Write unit tests for fund-transfer, user, and utility-payment services.

**Devin prompt:**
```
Add comprehensive unit tests to ts-java-spring-boot-internet-banking-microservices:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer() success, test fundTransfer() when core-banking returns error, test readAllTransfers().
   - FundTransferControllerTest: test POST and GET endpoints with MockMvc.

2. internet-banking-user-service:
   - UserServiceTest: test createUser() success, test createUser() with duplicate email, test createUser() with invalid identification, test updateUser() approval flow, test readUsers().
   - UserControllerTest: test all 4 endpoints with MockMvc.
   - Note: Mock KeycloakUserService and BankingCoreRestClient.

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment() success, test utilPayment() with error, test readPayments().
   - UtilityPaymentControllerTest: test POST and GET endpoints with MockMvc.

Use Mockito for all service mocks. Each test class should have at least 3-5 test methods covering happy path, error cases, and edge cases.
```

---

### 2.5 Add Idempotency Protection

**Gap ref:** 7.5 (High / Medium)

Add idempotency keys to fund transfer and payment operations.

**Devin prompt:**
```
Add idempotency protection to fund transfer and utility payment operations in ts-java-spring-boot-internet-banking-microservices:

1. Add an X-Idempotency-Key header parameter to POST endpoints in fund-transfer-service and utility-payment-service controllers.
2. Before processing, check if a record with that idempotency key already exists:
   - If found with SUCCESS status, return the existing response (do not reprocess).
   - If found with PENDING/PROCESSING status, return 409 Conflict.
3. Add an idempotency_key column to fund_transfer and utility_payment tables.
4. Add a unique constraint on the idempotency_key column.
5. Update the Feign call to core-banking to forward the idempotency key.
6. Write unit tests for duplicate request handling.
```

---

### 2.6 Add Rate Limiting to API Gateway

**Gap ref:** 4.4 (Medium / Medium)

Configure request rate limiting at the gateway level.

**Devin prompt:**
```
Add rate limiting to the API Gateway in ts-java-spring-boot-internet-banking-microservices:

1. Add the Redis-based rate limiter dependency:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Configure route-level rate limiting in the gateway configuration:
   - /user/api/v1/bank-users/register: 5 requests/minute per IP (prevent registration abuse)
   - /fund-transfer/api/v1/transfer: 30 requests/minute per authenticated user
   - /utility-payment/api/v1/utility-payment: 30 requests/minute per authenticated user
   - Default: 100 requests/minute per authenticated user

3. Add Redis to docker-compose.yml.
4. Return 429 Too Many Requests with Retry-After header when rate limit is exceeded.
```

---

### 2.7 Add Custom Health Checks

**Gap ref:** 6.2 (Medium / Small)

Add health indicators for critical dependencies.

**Devin prompt:**
```
Add custom health indicators to ts-java-spring-boot-internet-banking-microservices:

1. core-banking-service: Add a health indicator that checks MySQL connectivity (DataSource health is auto-configured but verify it's exposed).
2. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak server URL.
3. internet-banking-fund-transfer-service and internet-banking-utility-payment-service: Add a health indicator that checks core-banking-service availability via a lightweight endpoint (e.g. actuator/health).
4. Ensure all services expose management.endpoints.web.exposure.include=health,info,metrics in their config.
5. Build all services.
```

---

### 2.8 Fix Non-Atomic Transaction Processing

**Gap ref:** 7.6 (High / Medium)

Add compensation/reconciliation for the two-phase commit pattern.

**Devin prompt:**
```
Fix non-atomic transaction processing in ts-java-spring-boot-internet-banking-microservices:

1. In FundTransferService.fundTransfer():
   - Wrap the Feign call in a try-catch.
   - On failure, update the local FundTransferEntity to FAILED status.
   - Log the failure with the transaction reference for reconciliation.

2. In UtilityPaymentService.utilPayment():
   - Same pattern: catch Feign exceptions, set status to FAILED.

3. Add a scheduled reconciliation task (Spring @Scheduled) that:
   - Finds records older than 5 minutes in PENDING/PROCESSING status.
   - Logs warnings for manual review.

4. Write unit tests covering the failure scenarios.
```

---

## Phase 3 — Polish (Lower Severity / Various Effort)

Items that improve overall quality but are not immediately critical.

---

### 3.1 Add Integration Tests with Testcontainers

**Gap ref:** 3.2 (High / Large)

Write integration tests using Testcontainers for MySQL.

**Devin prompt:**
```
Add integration tests with Testcontainers to ts-java-spring-boot-internet-banking-microservices:

1. Add Testcontainers dependencies to core-banking-service, fund-transfer-service, user-service, and utility-payment-service:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service:
   - Create AccountControllerIntegrationTest with @SpringBootTest and @Testcontainers.
   - Test GET /api/v1/account/bank-account/{number} returns 200 for existing and 404 for missing accounts.
   - Test POST /api/v1/transaction/fund-transfer with valid and insufficient funds scenarios.

3. For each downstream service:
   - Use WireMock to mock core-banking-service Feign calls.
   - Test the full request lifecycle from controller to database.

4. Update test application.yml to use Testcontainers MySQL URL.
```

---

### 3.2 Add Contract Tests

**Gap ref:** 3.3 (High / Large)

Add Spring Cloud Contract or Pact tests for inter-service APIs.

**Devin prompt:**
```
Add Spring Cloud Contract tests to ts-java-spring-boot-internet-banking-microservices:

1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider side).
2. Define contracts for:
   - GET /api/v1/account/bank-account/{number} — success and not-found cases
   - POST /api/v1/transaction/fund-transfer — success and insufficient funds cases
   - POST /api/v1/transaction/util-payment — success case
   - GET /api/v1/user/{identification} — success and not-found cases

3. Add spring-cloud-starter-contract-stub-runner to consumer services (fund-transfer, utility-payment, user).
4. Write consumer-side tests that verify Feign clients work against the generated stubs.
5. Run both provider and consumer tests.
```

---

### 3.3 Add Dependency Vulnerability Scanning

**Gap ref:** 4.6 (Medium / Small)

Add OWASP Dependency Check to the build.

**Devin prompt:**
```
Add OWASP Dependency Check to ts-java-spring-boot-internet-banking-microservices:

1. Add the plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.1.0'
   }

2. Configure the plugin:
   dependencyCheck {
       failBuildOnCVSS = 7
       suppressionFile = "${rootDir}/owasp-suppressions.xml"
   }

3. Create an owasp-suppressions.xml file for known false positives.
4. Run ./gradlew dependencyCheckAnalyze on each service and document any findings.
```

---

### 3.4 Add Structured JSON Logging

**Gap ref:** 6.1 (Medium / Small)

Configure JSON-formatted log output for production deployments.

**Devin prompt:**
```
Add structured JSON logging to ts-java-spring-boot-internet-banking-microservices:

1. Add logstash-logback-encoder dependency to all services:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender using LogstashEncoder for the docker/production profile.
   - Default pattern appender for local development.
   - Include traceId and spanId from MDC for correlation.

3. Build all services and verify log output format.
```

---

### 3.5 Add Prometheus Metrics

**Gap ref:** 6.3 (Low / Medium)

Configure Prometheus metrics endpoint and custom business metrics.

**Devin prompt:**
```
Add Prometheus metrics support to ts-java-spring-boot-internet-banking-microservices:

1. Add micrometer-registry-prometheus to all services:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the /actuator/prometheus endpoint:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics

3. Add custom business metrics in core-banking-service TransactionService:
   - Counter: banking.transactions.total (tagged by type: FUND_TRANSFER, UTILITY_PAYMENT)
   - Counter: banking.transactions.failed (tagged by reason)
   - Timer: banking.transactions.duration

4. Add a Prometheus service to docker-compose.yml with a scrape config targeting all services.
5. Build all services.
```

---

### 3.6 Standardise Package Structure

**Gap ref:** 1.3 (Low / Small), 5.5 (Low / Small)

Align package naming and path variable conventions.

**Devin prompt:**
```
Standardise package structure and naming across ts-java-spring-boot-internet-banking-microservices:

1. Move utility-payment-service repository from top-level 'repository' package to 'model.repository' for consistency with fund-transfer-service.
2. Standardise DTO package locations: use 'model.dto.request' and 'model.dto.response' for all services (move utility-payment-service from 'model.rest.request' and 'model.rest.response').
3. Standardise controller path variable naming: use camelCase consistently ({accountNumber} instead of {account_number}, {accountName} instead of {account_name}).
4. Build and test all services.
```

---

### 3.7 Add Distributed Tracing to Infrastructure Services

**Gap ref:** 6.4 (Low / Small)

Add tracing dependencies to config-server and service-registry.

**Devin prompt:**
```
Add distributed tracing to config-server and service-registry in ts-java-spring-boot-internet-banking-microservices:

1. Add to internet-banking-config-server/build.gradle:
   implementation 'io.micrometer:micrometer-tracing-bridge-brave'
   implementation 'io.zipkin.reporter2:zipkin-reporter-brave'

2. Add to internet-banking-service-registry/build.gradle:
   implementation 'io.micrometer:micrometer-tracing-bridge-brave'
   implementation 'io.zipkin.reporter2:zipkin-reporter-brave'

3. Build both services.
```

---

## Summary

| Phase | Items | Estimated Sessions | Focus |
|-------|-------|-------------------|-------|
| **Phase 1** | 9 items | 5–8 sessions | Input validation, error handling, credentials cleanup, OpenAPI fixes, timeouts, logging |
| **Phase 2** | 8 items | 10–15 sessions | Circuit breakers, retries, shared library, unit tests, idempotency, rate limiting, health checks, transaction atomicity |
| **Phase 3** | 7 items | 8–12 sessions | Integration tests, contract tests, vulnerability scanning, structured logging, metrics, package cleanup, tracing |
