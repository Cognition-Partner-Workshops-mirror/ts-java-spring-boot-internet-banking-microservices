# Remediation Roadmap

## Overview

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins:** Critical fixes and low-effort improvements that provide immediate risk reduction. Target: 1-2 sprints.
- **Phase 2 — Important:** High-impact changes that require moderate effort and planning. Target: 3-6 sprints.
- **Phase 3 — Polish:** Improvements that enhance developer experience and long-term maintainability. Target: ongoing.

---

## Phase 1: Quick Wins (Critical fixes, Small effort)

These items address critical security vulnerabilities, data corruption bugs, and high-severity issues that can be fixed with small, focused changes.

---

### 1.1 Fix Balance Calculation Bug (GAP-RES-06)

**Severity:** Critical | **Effort:** Small | **Risk:** Data corruption on every transaction

The `availableBalance` is double-subtracted/double-added in `TransactionService.internalFundTransfer()` and `utilPayment()`. This is a live data corruption bug.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java.

In the `internalFundTransfer()` method:
- Line setting fromBankAccountEntity.setAvailableBalance should use the ORIGINAL actualBalance before subtraction, or simply set availableBalance = actualBalance after the subtraction.
- Same fix for toBankAccountEntity credit side.

In the `utilPayment()` method:
- Same pattern: availableBalance is double-subtracted. Fix to match actualBalance after debit.

Update the existing unit tests in TransactionServiceTest to verify that availableBalance equals actualBalance after each transfer and payment. Run the tests to confirm they pass.
```

---

### 1.2 Stop Leaking Stack Traces in Error Responses (GAP-ERR-02)

**Severity:** Critical | **Effort:** Small | **Risk:** Information disclosure vulnerability

**Devin Prompt:**
```
In all four GlobalExceptionHandler classes (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), fix the catch-all exception handler.

Replace:
  return ResponseEntity.badRequest().body("Exception occur inside API " + e);

With:
  log.error("Unhandled exception", e);
  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred").build());

Add the @Slf4j annotation and HttpStatus import to each GlobalExceptionHandler if not present. Run the existing tests to confirm nothing breaks.
```

---

### 1.3 Fix HTTP Status Codes in Error Responses (GAP-ERR-03)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Update the GlobalExceptionHandler in core-banking-service and internet-banking-user-service to return appropriate HTTP status codes:

In core-banking-service:
- Add handler for EntityNotFoundException → return 404 Not Found
- Add handler for InsufficientFundsException → return 422 Unprocessable Entity
- Keep SimpleBankingGlobalException → 400 Bad Request

In internet-banking-user-service:
- Add handler for EntityNotFoundException → return 404 Not Found
- Add handler for UserAlreadyRegisteredException → return 409 Conflict
- Add handler for InvalidEmailException → return 400 Bad Request
- Add handler for InvalidBankingUserException → return 404 Not Found

For fund-transfer-service and utility-payment-service, keep the existing SimpleBankingGlobalException → 400 mapping for now, but update the catch-all to return 500.
```

---

### 1.4 Add Input Validation to Request DTOs (GAP-SEC-02)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs and @Valid to controller parameters across all services.

1. core-banking-service FundTransferRequest:
   - fromAccount: @NotBlank
   - toAccount: @NotBlank
   - amount: @NotNull @Positive

2. core-banking-service UtilityPaymentRequest:
   - providerId: @NotNull
   - amount: @NotNull @Positive
   - referenceNumber: @NotBlank
   - account: @NotBlank

3. internet-banking-fund-transfer-service FundTransferRequest:
   - fromAccount: @NotBlank
   - toAccount: @NotBlank
   - amount: @NotNull @Positive

4. internet-banking-utility-payment-service UtilityPaymentRequest:
   - providerId: @NotNull
   - amount: @NotNull @Positive
   - referenceNumber: @NotBlank
   - account: @NotBlank

5. internet-banking-user-service User DTO (for registration):
   - email: @NotBlank @Email
   - identification: @NotBlank
   - password: @NotBlank @Size(min=8)

Add @Valid before @RequestBody in all controllers that accept these DTOs. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details.

Add spring-boot-starter-validation dependency to each service's build.gradle if not already present.
```

---

### 1.5 Remove Sensitive Data from Logs (GAP-OBS-05)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix sensitive data logging across all services:

1. In FundTransferController.java, change:
   log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
   To:
   log.info("Fund transfer request received: fromAccount={}, toAccount={}", fundTransferRequest.getFromAccount(), fundTransferRequest.getToAccount());

2. In FundTransferService.java, change:
   log.info("Sending fund transfer request {}" + request.toString());
   To:
   log.info("Processing fund transfer: fromAccount={}, toAccount={}, amount={}", request.getFromAccount(), request.getToAccount(), request.getAmount());
   Also fix the string concatenation to use SLF4J placeholder.

3. In UtilityPaymentService.java, change:
   log.info("Utility payment processing {}", paymentRequest.toString());
   To:
   log.info("Processing utility payment: providerId={}, account={}", paymentRequest.getProviderId(), paymentRequest.getAccount());

4. Ensure the User DTO in internet-banking-user-service has a custom toString() that excludes the password field. Add @ToString(exclude = "password") from Lombok.
```

---

### 1.6 Fix Wrong OpenAPI Starter Dependency (GAP-API-05)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the following build.gradle files, replace the incorrect OpenAPI starter:

Files:
- core-banking-service/build.gradle
- internet-banking-user-service/build.gradle
- internet-banking-fund-transfer-service/build.gradle
- internet-banking-utility-payment-service/build.gradle

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC, not WebFlux. Only the API gateway should use the webflux starter. Build all four services to verify compilation succeeds.
```

---

### 1.7 Add Dependency Vulnerability Scanning (GAP-SEC-05)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add the OWASP Dependency Check Gradle plugin to each service. Since there is no multi-module build yet, add it to each service's build.gradle:

Add to plugins block:
  id 'org.owasp.dependencycheck' version '9.0.9'

Add configuration:
  dependencyCheck {
      failBuildOnCVSS = 7
      formats = ['HTML', 'JSON']
  }

Run ./gradlew dependencyCheckAnalyze in each service directory and report any findings. Do not fail the build initially — set failBuildOnCVSS to 11 (effectively disabled) and add a TODO to lower it once existing vulnerabilities are triaged.
```

---

### 1.8 Add Timeout Configuration for Feign Clients (GAP-RES-03)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add explicit timeout configuration for all Feign clients. In each service that uses Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), add the following to src/main/resources/application.yml:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

This sets a 5-second connect timeout and 10-second read timeout for all Feign clients. These are reasonable defaults for a banking application.
```

---

### 1.9 Add Retry Policies for Feign Clients (GAP-RES-02)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add Resilience4j retry configuration for Feign clients in fund-transfer-service and utility-payment-service.

1. Add dependency to both services' build.gradle:
   implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
   implementation 'io.github.resilience4j:resilience4j-feign:2.2.0'

2. Add retry configuration to application.yml for each service:
   resilience4j:
     retry:
       instances:
         coreBankingService:
           maxAttempts: 3
           waitDuration: 1s
           enableExponentialBackoff: true
           exponentialBackoffMultiplier: 2
           retryExceptions:
             - java.io.IOException
             - feign.RetryableException

Note: Do NOT retry fund transfer POST requests automatically — these are not idempotent. Only retry GET/read operations. Add a comment explaining this decision.
```

---

## Phase 2: Important (High-impact, Moderate effort)

These items address structural issues that improve reliability, testability, and maintainability.

---

### 2.1 Add Error Handling for Failed Feign Calls (GAP-ERR-04)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add proper error handling for Feign call failures in the orchestrator services.

In FundTransferService.fundTransfer():
- Wrap the bankingCoreFeignClient.fundTransfer(request) call in try/catch.
- On failure: update the entity status to FAILED, save it, and rethrow a meaningful exception.
- Add a FundTransferFailedException custom exception class.

In UtilityPaymentService.utilPayment():
- Wrap the bankingCoreRestClient.utilityPayment(paymentRequest) call in try/catch.
- On failure: update the entity status to FAILED, save it, and rethrow a meaningful exception.
- Add a UtilityPaymentFailedException custom exception class.

Add corresponding handlers in each service's GlobalExceptionHandler.
Write unit tests for both success and failure scenarios.
```

---

### 2.2 Add Circuit Breakers (GAP-RES-01)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients in the three consumer services (user-service, fund-transfer-service, utility-payment-service).

1. Add dependencies to each service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Add circuit breaker configuration to each service's application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           registerHealthIndicator: true
           slidingWindowSize: 10
           minimumNumberOfCalls: 5
           failureRateThreshold: 50
           waitDurationInOpenState: 30s
           permittedNumberOfCallsInHalfOpenState: 3

3. Add @CircuitBreaker annotations to the Feign client interfaces or wrap calls in the service layer using CircuitBreakerFactory.

4. Add fallback methods that return meaningful error responses when the circuit is open.

5. Write tests that verify circuit breaker behavior.
```

---

### 2.3 Add Idempotency Protection (GAP-RES-05)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to the fund transfer and utility payment endpoints.

1. Add an "Idempotency-Key" header parameter to FundTransferController.sendFundTransfer() and UtilityPaymentController.processPayment().

2. In each service:
   - Before processing, check if a record with the same idempotency key already exists.
   - If it does, return the original response without processing again.
   - If it doesn't, proceed with processing and store the idempotency key with the entity.

3. Add an "idempotencyKey" column to both fund_transfer and utility_payment tables.
   - Add a unique index on idempotencyKey.

4. Add unit tests verifying:
   - First request processes normally.
   - Duplicate request with same key returns original response without reprocessing.
   - Different key processes as a new request.
```

---

### 2.4 Add Authorization Checks (GAP-SEC-03)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add authorization checks to ensure users can only operate on their own accounts.

1. In internet-banking-fund-transfer-service:
   - In FundTransferService.fundTransfer(), verify that the authenticated user (from ApiRequestContextHolder) owns the fromAccount by calling core-banking-service to read the account and checking the user ID.
   - Throw an UnauthorizedAccessException if the user doesn't own the account.

2. In internet-banking-utility-payment-service:
   - Same pattern: verify the authenticated user owns the source account.

3. In internet-banking-user-service:
   - For GET /api/v1/bank-users/{id}: verify the authenticated user is requesting their own profile, or is an admin.
   - For PATCH /api/v1/bank-users/{id}: restrict to admin users only.

4. Add role-based access control by reading roles from the JWT token claims.
```

---

### 2.5 Extract Shared Library (GAP-ORG-02)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create a shared library module that contains duplicated code.

1. Create a new directory: shared-library/ with its own build.gradle.
2. Move the following classes into the shared library under package com.javatodev.finance.common:
   - BaseMapper
   - AuditAware, AuditConfig, AuditorAwareConfig
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler (base class)
   - GlobalErrorCode

3. Publish the shared library as a local Maven artifact or use a composite Gradle build.
4. Update all services to depend on the shared library and remove the duplicated classes.
5. Verify all services still compile and tests pass.
```

---

### 2.6 Create Multi-Module Gradle Build (GAP-ORG-01)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Create a root-level Gradle multi-module build.

1. Create a root settings.gradle that includes all services:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'

2. Create a root build.gradle with shared configuration:
   subprojects {
       apply plugin: 'java'
       java { sourceCompatibility = '21' }
       repositories { mavenCentral() }
   }

3. Remove redundant settings from individual service build.gradle files.
4. Verify that `./gradlew build` from the root builds all services.
5. Add a root .editorconfig file for consistent formatting.
```

---

### 2.7 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (GAP-TEST-01)

**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests for the three business services that currently have no tests.

For internet-banking-user-service UserService:
- Test createUser() happy path
- Test createUser() with already registered email
- Test createUser() with mismatched email
- Test createUser() with user not found in core banking
- Test readUsers() with pagination
- Test readUser() by ID - found and not found
- Test updateUser() to APPROVED status (verify Keycloak update)
- Test updateUser() to DISABLED status
Mock BankingCoreRestClient, KeycloakUserService, and UserRepository.

For internet-banking-fund-transfer-service FundTransferService:
- Test fundTransfer() happy path
- Test fundTransfer() when core banking call fails
- Test readAllTransfers() with pagination
Mock BankingCoreFeignClient and FundTransferRepository.

For internet-banking-utility-payment-service UtilityPaymentService:
- Test utilPayment() happy path
- Test utilPayment() when core banking call fails
- Test readPayments() with pagination
Mock BankingCoreRestClient and UtilityPaymentRepository.
```

---

### 2.8 Add Pagination Metadata to Responses (GAP-API-02)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update paginated endpoints to return Page<T> instead of List<T> to include pagination metadata.

1. In core-banking-service UserController.readUsers():
   - Change return type to ResponseEntity<Page<User>>
   - Update UserService.readUsers() to return Page<User> instead of List<User>

2. In internet-banking-user-service UserController.readUsers():
   - Same pattern: return Page<User>

3. In internet-banking-fund-transfer-service FundTransferController.readFundTransfers():
   - Return Page<FundTransfer> instead of List<FundTransfer>
   - Update FundTransferService.readAllTransfers()

4. In internet-banking-utility-payment-service UtilityPaymentController.readPayments():
   - Return Page<UtilityPayment> instead of List<UtilityPayment>
   - Update UtilityPaymentService.readPayments()

This gives clients: { content: [], totalElements, totalPages, number, size, ... }
```

---

### 2.9 Add Structured Logging (GAP-OBS-01)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add structured JSON logging to all services.

1. Add dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with LogstashEncoder for JSON output
   - Include MDC fields for traceId and spanId (already populated by Micrometer)
   - Profile-specific: use JSON in docker/production, use pattern in local dev

3. Fix all string concatenation in log statements to use SLF4J placeholders.
```

---

### 2.10 Add Prometheus Metrics (GAP-OBS-03)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add Prometheus metrics export to all services.

1. Add dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Add to each service's application.yml (or externalized config):
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom business metrics in key services:
   - core-banking-service: Counter for fund transfers, counter for utility payments, gauge for active accounts
   - fund-transfer-service: Counter for transfers by status (SUCCESS, FAILED, PENDING)

4. Verify /actuator/prometheus returns metrics in Prometheus format.
```

---

## Phase 3: Polish (Long-term improvements)

These items improve developer experience, API consistency, and operational maturity.

---

### 3.1 Add Integration Tests with Testcontainers (GAP-TEST-02)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for the core-banking-service.

1. Add test dependencies:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create a base test class that starts a MySQL Testcontainer and configures Spring to use it.

3. Write integration tests for:
   - AccountController: GET bank account, GET utility account (404 cases too)
   - TransactionController: POST fund transfer, POST utility payment
   - UserController: GET user by identification, GET users with pagination
   
4. Verify Flyway migrations run correctly against the real MySQL container.

5. Add WireMock-based integration tests for Feign clients in fund-transfer-service and utility-payment-service.
```

---

### 3.2 Add Contract Tests (GAP-TEST-03)

**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services.

1. In core-banking-service (provider side):
   - Add Spring Cloud Contract Verifier dependency
   - Write contract DSL files for each API endpoint
   - Generate provider-side tests from contracts
   
2. In consumer services (fund-transfer, utility-payment, user-service):
   - Add Spring Cloud Contract Stub Runner dependency
   - Write consumer-side tests that use generated stubs
   
3. Set up contract publishing to a local Maven repository or artifact store.
```

---

### 3.3 Add Custom Health Indicators (GAP-OBS-02)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom health indicators to downstream services.

1. In internet-banking-user-service:
   - Add a KeycloakHealthIndicator that checks Keycloak server reachability
   - Add a CoreBankingHealthIndicator that checks core-banking-service via the Feign client or actuator

2. In internet-banking-fund-transfer-service and utility-payment-service:
   - Add a CoreBankingHealthIndicator

3. Implement each as a @Component implementing HealthIndicator interface.
4. Return Health.up() or Health.down() with details about what failed.
```

---

### 3.4 Standardize Endpoint Naming and Response Envelope (GAP-API-04, GAP-API-06)

**Severity:** Low-Medium | **Effort:** Medium

**Devin Prompt:**
```
Standardize API endpoints and add a response envelope.

1. Create a generic ApiResponse<T> class in the shared library:
   { success: boolean, data: T, error: { code, message }, timestamp, path }

2. Rename endpoints for consistency:
   - /api/v1/bank-users → /api/v1/users
   - /api/v1/transfer → /api/v1/transfers
   - /api/v1/utility-payment → /api/v1/utility-payments
   - /api/v1/account/util-account → /api/v1/accounts/utility-accounts

3. Update all controllers to return ApiResponse<T>.
4. Update the GlobalExceptionHandler to wrap errors in ApiResponse.
5. Update the Feign clients and their response DTOs.
6. Update all existing tests.

Note: This is a breaking API change. Coordinate with frontend clients and update Postman collection.
```

---

### 3.5 Add Fallback Behavior for Feign Clients (GAP-RES-04)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add fallback implementations for Feign clients.

1. For BankingCoreRestClient in user-service:
   - Create BankingCoreRestClientFallback implementing the interface
   - Return a cached user response or a meaningful "service unavailable" response
   
2. For BankingCoreFeignClient in fund-transfer-service:
   - Return a FundTransferResponse with status FAILED and message "Core banking service unavailable"
   
3. For BankingCoreRestClient in utility-payment-service:
   - Return a UtilityPaymentResponse with status FAILED and message "Core banking service unavailable"

4. Register fallbacks via @FeignClient(fallback = ...) attribute.
5. Write tests that verify fallback behavior when the circuit is open.
```

---

### 3.6 Externalize Secrets from Version Control (GAP-SEC-01)

**Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Remove hardcoded credentials from Docker Compose and source files.

1. Create a docker-compose/.env.example file with placeholder values:
   MYSQL_ROOT_PASSWORD=change_me
   KEYCLOAK_ADMIN_PASSWORD=change_me
   KEYCLOAK_DB_PASSWORD=change_me
   MYSQL_APP_USER_PASSWORD=change_me

2. Update docker-compose.yml to use environment variable substitution:
   environment:
     MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}

3. Update docker-compose/mysql/privileges.sql to use environment variables or a template.

4. Add .env to .gitignore.

5. Remove test credentials from README.md or move to a separate non-committed file.

6. Remove the Keycloak client-secret from test application.yml and use a test-specific value.
```

---

### 3.7 Standardize Package Structure (GAP-ORG-03)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance/
├── controller/        (REST controllers)
├── service/           (business logic)
│   └── rest/          (Feign clients and REST integrations)
├── model/
│   ├── entity/        (JPA entities)
│   ├── dto/           (Data Transfer Objects)
│   │   ├── request/   (Request DTOs)
│   │   └── response/  (Response DTOs)
│   └── mapper/        (Entity-DTO mappers)
├── repository/        (Spring Data repositories)
├── configuration/     (Spring configuration)
│   ├── audit/
│   ├── feign/
│   ├── filter/
│   └── security/
└── exception/         (Exception classes and handlers)

Move classes in utility-payment-service and core-banking-service to match this structure. Update imports accordingly.
```

---

### 3.8 Set Up CI/CD Pipeline

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline for the project.

1. Create .github/workflows/ci.yml with:
   - Trigger on push to main and pull requests
   - Set up Java 21 with Gradle
   - Build all services
   - Run all unit tests
   - Run OWASP dependency check
   - Build Docker images (without pushing)

2. Create .github/workflows/docker-publish.yml with:
   - Trigger on tag push (v*)
   - Build and push Docker images to a container registry
   - Tag images with git tag version

3. Add build status badges to README.md.
```

---

### 3.9 Document API Versioning Strategy (GAP-API-01)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Document the API versioning strategy for the project.

1. Create docs/API_VERSIONING.md that describes:
   - URL path versioning is used (/api/v1/, /api/v2/)
   - When to create a new version (breaking changes to request/response shapes)
   - How to maintain backward compatibility during migration
   - Deprecation policy (how long v1 remains available after v2 is released)

2. Add @Deprecated annotations and sunset headers pattern for when v2 is eventually needed.
```

---

### 3.10 Add .editorconfig and Code Formatting (GAP-ORG-04)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add consistent code formatting configuration.

1. Create a root .editorconfig file:
   [*]
   indent_style = space
   indent_size = 4
   end_of_line = lf
   charset = utf-8
   trim_trailing_whitespace = true
   insert_final_newline = true

   [*.gradle]
   indent_size = 4

   [*.yml]
   indent_size = 2

2. Add the Spotless Gradle plugin to the root build.gradle for automated formatting:
   - Java: Google Java Format or Palantir Java Format
   - Gradle: groovyGradle with indentation rules

3. Run spotlessApply to fix existing formatting issues.
```

---

## Priority Matrix

```
                    Small Effort          Medium Effort         Large Effort
                ┌─────────────────────┬─────────────────────┬─────────────────────┐
   Critical     │ 1.1 Balance bug     │ 1.4 Input valid.    │ 2.7 Unit tests      │
                │ 1.2 Stack traces    │ 2.2 Circuit breakers│                     │
                │ 3.6 Secrets         │ 2.3 Idempotency     │                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
   High         │ 1.3 Status codes    │ 2.1 Feign errors    │ 3.1 Integration     │
                │ 1.5 Log masking     │ 2.4 Authorization   │     tests           │
                │ 1.8 Timeouts        │                     │                     │
                │ 1.9 Retries         │                     │                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
   Medium       │ 1.6 OpenAPI fix     │ 2.5 Shared library  │ 3.2 Contract tests  │
                │ 1.7 Vuln scanning   │ 2.6 Multi-module    │                     │
                │ 2.8 Pagination      │ 3.4 API standards   │                     │
                │ 2.9 Structured logs │ 3.5 Fallbacks       │                     │
                │ 2.10 Metrics        │ 3.8 CI/CD           │                     │
                │ 3.3 Health checks   │                     │                     │
                │ 3.9 API versioning  │                     │                     │
                │ 3.10 Formatting     │                     │                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
   Low          │ 3.7 Package struct  │                     │                     │
                └─────────────────────┴─────────────────────┴─────────────────────┘
```

---

## Execution Order Summary

| Order | Item | ID(s) | Est. Effort |
|---|---|---|---|
| 1 | Fix balance calculation bug | GAP-RES-06 | 1 hour |
| 2 | Stop leaking stack traces | GAP-ERR-02 | 1 hour |
| 3 | Fix HTTP status codes | GAP-ERR-03 | 2 hours |
| 4 | Add input validation | GAP-SEC-02 | 4 hours |
| 5 | Remove sensitive data from logs | GAP-OBS-05 | 1 hour |
| 6 | Fix OpenAPI starter | GAP-API-05 | 30 min |
| 7 | Add timeouts | GAP-RES-03 | 1 hour |
| 8 | Add retries | GAP-RES-02 | 2 hours |
| 9 | Add Feign error handling | GAP-ERR-04 | 4 hours |
| 10 | Add circuit breakers | GAP-RES-01 | 1 day |
| 11 | Add idempotency protection | GAP-RES-05 | 1 day |
| 12 | Add authorization | GAP-SEC-03 | 1 day |
| 13 | Extract shared library | GAP-ORG-02 | 1 day |
| 14 | Create multi-module build | GAP-ORG-01 | 4 hours |
| 15 | Add unit tests | GAP-TEST-01 | 2-3 days |
| 16 | Add pagination metadata | GAP-API-02 | 2 hours |
| 17 | Add structured logging | GAP-OBS-01 | 4 hours |
| 18 | Add Prometheus metrics | GAP-OBS-03 | 4 hours |
| 19 | Externalize secrets | GAP-SEC-01 | 2 hours |
| 20 | Add integration tests | GAP-TEST-02 | 3-5 days |
| 21+ | Remaining Phase 3 items | Various | Ongoing |
