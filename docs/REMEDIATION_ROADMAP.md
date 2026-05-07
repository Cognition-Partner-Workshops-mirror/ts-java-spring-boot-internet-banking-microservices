# Remediation Roadmap

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Input:** [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) — 37 gaps across 7 categories

---

## Table of Contents

1. [Phase 1 — Quick Wins (High Severity / Low Effort)](#phase-1--quick-wins)
2. [Phase 2 — Important (High Severity / Medium Effort)](#phase-2--important)
3. [Phase 3 — Polish (Lower Severity or High Effort)](#phase-3--polish)
4. [Phase Summary](#phase-summary)

---

## Phase 1 — Quick Wins

> **Goal:** Fix critical bugs and security holes that can be addressed with small, focused changes.  
> **Timeline:** 1-2 sprints  
> **Gaps covered:** GAP-34, GAP-12, GAP-14, GAP-05, GAP-06, GAP-08, GAP-16, GAP-24, GAP-17, GAP-29, GAP-30, GAP-35, GAP-37, GAP-18, GAP-04

---

### 1.1 Fix Available Balance Double-Deduction Bug (GAP-34)

**Priority:** P0 — Active data-corruption bug  
**Severity:** Critical | **Effort:** Small

The `TransactionService` in core-banking-service subtracts the amount from `actualBalance`, then subtracts it again from the (already-reduced) `actualBalance` to set `availableBalance`. This affects both `internalFundTransfer()` and `utilPayment()` methods, and both the debit and credit sides.

**Devin prompt:**
```
In core-banking-service, fix the available balance double-deduction bug in
TransactionService.java.

In internalFundTransfer():
- Line ~90-91: After setting actualBalance = actualBalance - amount, the code
  sets availableBalance = actualBalance - amount (subtracting twice). Fix to:
  availableBalance = actualBalance (both should equal the same post-debit value).
- Line ~99-100: Same bug on the credit side for the toAccount.

In utilPayment():
- Line ~63-64: Same pattern — fix availableBalance to equal actualBalance after
  the single subtraction.

Update the existing unit tests in TransactionServiceTest to verify that after
a fund transfer of 100 from an account with balance 1000:
- actualBalance = 900
- availableBalance = 900
```

---

### 1.2 Add Input Validation to All Request DTOs (GAP-12)

**Priority:** P0 — Any input currently accepted without checks  
**Severity:** Critical | **Effort:** Small

**Devin prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services
and enable validation on controllers.

1. In core-banking-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account

2. In internet-banking-fund-transfer-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount

3. In internet-banking-utility-payment-service:
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account

4. In internet-banking-user-service:
   - User (registration): @NotBlank @Email email, @NotBlank identification,
     @NotBlank @Size(min=8) password

5. Add @Valid annotation to all @RequestBody parameters in controllers.

6. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler
   that returns 422 with field-level error details:
   { "code": "VALIDATION_ERROR", "errors": [{"field": "amount", "message": "must be positive"}] }

Do NOT modify any existing test files. Add new test classes if needed.
```

---

### 1.3 Externalize Hardcoded Credentials (GAP-14)

**Priority:** P0 — Credentials in version control  
**Severity:** Critical | **Effort:** Small

**Devin prompt:**
```
Externalize all hardcoded credentials from Docker Compose and MySQL config files.

1. Create docker-compose/.env with variables:
   MYSQL_ROOT_PASSWORD=woVERANKliGharym
   MYSQL_APP_USER=javatodev_development
   MYSQL_APP_PASSWORD=oPItyPticIAt
   KC_DB_USERNAME=keycloak
   KC_DB_PASSWORD=password
   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=password

2. Update docker-compose/docker-compose.yml to use ${VARIABLE} references.

3. Update docker-compose/mysql/Dockerfile to use ARG + ENV pattern:
   ARG MYSQL_ROOT_PASSWORD
   ENV MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}

4. Update docker-compose/mysql/privileges.sql to be a template or use
   environment variable substitution via entrypoint script.

5. Add docker-compose/.env to .gitignore.

6. Create docker-compose/.env.example with placeholder values.
```

---

### 1.4 Fix Error Handling — Proper HTTP Status Codes (GAP-05, GAP-06)

**Priority:** P1 — Misleading error responses  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
Fix GlobalExceptionHandler in all 4 services (core-banking, fund-transfer,
utility-payment, user-service) to return correct HTTP status codes.

1. Map exceptions to proper status codes:
   - EntityNotFoundException → 404 Not Found
   - InsufficientFundsException → 422 Unprocessable Entity
   - SimpleBankingGlobalException → 400 Bad Request
   - MethodArgumentNotValidException → 422 (if GAP-12 is done)
   - FeignException → propagate downstream status code (or 502 Bad Gateway)
   - All other Exception → 500 Internal Server Error

2. Replace the generic catch-all handler:
   BEFORE: return ResponseEntity.badRequest().body("Exception occur inside API " + e);
   AFTER:  return ResponseEntity.status(500).body(new ErrorResponse("INTERNAL_ERROR",
           "An unexpected error occurred. Please try again later."));

3. Never expose exception class names, stack traces, or internal messages
   in error responses. Log the full exception at ERROR level instead.

Do NOT modify any existing test files.
```

---

### 1.5 Add Feign Error Decoder to Payment Services (GAP-08)

**Priority:** P1 — Error context lost between services  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
Add a FeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service (user-service already has one).

1. Create a CustomFeignErrorDecoder class in each service that:
   - Reads the response body from the Feign Response
   - Parses the ErrorResponse JSON (code + message)
   - Maps HTTP 404 → EntityNotFoundException
   - Maps HTTP 422 → InsufficientFundsException (or a new PaymentRejectedException)
   - Maps HTTP 4xx → SimpleBankingGlobalException with the downstream message
   - Maps HTTP 5xx → SimpleBankingGlobalException("Downstream service unavailable")

2. Register it via a @Bean in FeignClientConfiguration.

You can reference internet-banking-user-service's existing
CustomFeignErrorDecoder as a model.
```

---

### 1.6 Add Account Status Check Before Transactions (GAP-16)

**Priority:** P1 — BLOCKED/DORMANT accounts can transact  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
In core-banking-service TransactionService, add account status validation
before processing any transaction.

1. In both internalFundTransfer() and utilPayment(), before the balance check,
   add:
   if (fromBankAccountEntity.getStatus() != AccountStatus.ACTIVE) {
       throw new SimpleBankingGlobalException("Account " +
           fromBankAccountEntity.getNumber() + " is not active (status: " +
           fromBankAccountEntity.getStatus() + ")");
   }

2. In internalFundTransfer(), also check the toAccount:
   if (toBankAccountEntity.getStatus() != AccountStatus.ACTIVE) {
       throw new SimpleBankingGlobalException("Destination account " +
           toBankAccountEntity.getNumber() + " is not active");
   }

3. Add unit tests verifying that PENDING, DORMANT, and BLOCKED accounts
   cannot initiate or receive transfers.
```

---

### 1.7 Stop Logging Sensitive Data (GAP-24, GAP-17)

**Priority:** P1 — PII and passwords in logs  
**Severity:** High / Medium | **Effort:** Small

**Devin prompt:**
```
Remove or redact sensitive data from all log statements across all services.

1. In FundTransferController.fundTransfer():
   BEFORE: log.info("Got fund transfer request from API {}", fundTransferRequest.toString())
   AFTER:  log.info("Fund transfer request received: {} -> {}", request.getFromAccount(), request.getToAccount())

2. In UtilityPaymentService:
   BEFORE: log.info("Utility payment processing {}", paymentRequest.toString())
   AFTER:  log.info("Utility payment processing for provider {}", paymentRequest.getProviderId())

3. In TransactionController:
   Redact full request objects — log only transaction type and non-sensitive identifiers.

4. In UserController:
   BEFORE: log.info("Creating user with {}", request.toString())
   AFTER:  log.info("User registration request for identification {}", request.getIdentification())

5. In the User DTO (user-service), exclude password from toString:
   Add @ToString.Exclude on the password field, or replace @Data with
   @Getter @Setter and write a custom toString.
```

---

### 1.8 Add Feign Retry Policy and Timeouts (GAP-29, GAP-30)

**Priority:** P1 — Transient failures immediately fatal  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
Configure Feign client timeouts and retry policies in fund-transfer-service,
utility-payment-service, and user-service.

1. In each service's application.yml (or via Config Server), add:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connectTimeout: 3000
               readTimeout: 5000

2. Create a RetryConfig @Configuration class with:
   @Bean
   public Retryer feignRetryer() {
       return new Retryer.Default(200, 1000, 3);
       // 200ms initial interval, 1s max interval, 3 max attempts
   }

3. Ensure retries only happen on connection failures and 503s,
   NOT on 4xx errors (the error decoder from GAP-08 should throw
   non-retryable exceptions for 4xx).
```

---

### 1.9 Add Transaction Amount Limits (GAP-35)

**Priority:** P1 — Unlimited transaction amounts  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
Add configurable transaction amount limits to core-banking-service.

1. Add configuration properties:
   banking:
     limits:
       min-transfer-amount: 0.01
       max-transfer-amount: 1000000.00
       min-payment-amount: 0.01
       max-payment-amount: 100000.00

2. Create a @ConfigurationProperties class TransactionLimitsConfig.

3. In TransactionService, before balance check, validate:
   if (amount.compareTo(limits.getMinTransferAmount()) < 0 ||
       amount.compareTo(limits.getMaxTransferAmount()) > 0) {
       throw new SimpleBankingGlobalException("Transfer amount must be between " +
           limits.getMinTransferAmount() + " and " + limits.getMaxTransferAmount());
   }

4. Add similar validation in utilPayment() with payment-specific limits.

5. Add unit tests for boundary values.
```

---

### 1.10 Add @Transactional to Orchestration Services (GAP-37)

**Priority:** P1 — Non-atomic multi-step operations  
**Severity:** High | **Effort:** Small

**Devin prompt:**
```
Add @Transactional annotations to the service methods in
internet-banking-fund-transfer-service and
internet-banking-utility-payment-service.

1. FundTransferService.fundTransfer(): Add @Transactional so the
   save(PENDING) and save(SUCCESS/FAILED) happen in the same transaction.
   If the Feign call succeeds but the status update fails, the transaction
   rolls back to PENDING.

2. UtilityPaymentService.utilPayment(): Same pattern.

Note: This only provides local transaction atomicity within each orchestration
service's own database. It does NOT provide distributed transaction guarantees
across core-banking — that requires the saga pattern (Phase 3).
```

---

### 1.11 Fix OpenAPI Dependency (GAP-18)

**Priority:** P2  
**Severity:** Medium | **Effort:** Small

**Devin prompt:**
```
In core-banking-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service,
replace the incorrect OpenAPI dependency in build.gradle:

BEFORE: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
AFTER:  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0'

The API Gateway (which uses WebFlux) should keep webflux-ui if it uses
springdoc, but currently it does not include springdoc at all.

Verify each service builds successfully after the change.
```

---

### 1.12 Remove Dead Code (GAP-04)

**Priority:** P2  
**Severity:** Low | **Effort:** Small

**Devin prompt:**
```
Clean up dead/unused code:

1. Delete UtilityPaymentResponse.java from internet-banking-fund-transfer-service
   (empty class with no fields, never used).

2. Delete UtilityPaymentRequest.java from internet-banking-fund-transfer-service
   (defined but never referenced — fund-transfer only handles fund transfers).

3. Verify the build succeeds after deletion in all services.
```

---

## Phase 2 — Important

> **Goal:** Introduce structural improvements for maintainability, security, and resilience.  
> **Timeline:** 2-4 sprints  
> **Gaps covered:** GAP-01, GAP-03, GAP-02, GAP-13, GAP-28, GAP-31, GAP-32, GAP-36, GAP-07, GAP-19, GAP-15, GAP-25

---

### 2.1 Extract Shared Library Module (GAP-01, GAP-03)

**Priority:** P1 — Foundation for reducing duplication  
**Severity:** High | **Effort:** Medium

**Devin prompt:**
```
Create a shared library module (banking-common) and migrate duplicated code.

1. Create a new directory: banking-common/ with build.gradle configured as
   a plain Java library (no Spring Boot plugin, just spring-boot-dependencies
   BOM for version management).

2. Move these classes into banking-common under package
   com.javatodev.finance.common:
   - exception/GlobalExceptionHandler.java
   - exception/SimpleBankingGlobalException.java
   - exception/EntityNotFoundException.java
   - exception/InsufficientFundsException.java
   - exception/ErrorResponse.java
   - audit/AuditAware.java
   - audit/AuditConfig.java
   - audit/AuditorAwareConfig.java
   - filter/AppAuthUserFilter.java
   - filter/ApiRequestContext.java
   - filter/ApiRequestContextHolder.java
   - mapper/BaseMapper.java

3. Create a root settings.gradle that includes all 7 modules:
   include 'banking-common', 'core-banking-service',
           'internet-banking-fund-transfer-service', etc.

4. In each service's build.gradle, add:
   implementation project(':banking-common')

5. Delete the local copies from each service and update imports.

6. Verify all services build and existing tests pass.
```

---

### 2.2 Create Root Multi-Module Gradle Project (GAP-02)

**Priority:** P2 — Part of shared library extraction  
**Severity:** Medium | **Effort:** Small

**Devin prompt:**
```
Create a root-level Gradle multi-module project setup.

1. Create settings.gradle in the repo root:
   rootProject.name = 'internet-banking-microservices'
   include 'banking-common'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'

2. Create a root build.gradle with:
   - Common plugin versions in plugins { } block with apply false
   - Shared dependency versions via ext { } or version catalog
   - Common test configuration for all subprojects

3. Move Spring Boot version (3.2.4), Spring Cloud BOM (2023.0.0),
   and common dependency versions to the root so they are declared once.

This task can be combined with the shared library extraction (2.1).
```

---

### 2.3 Add Authentication to Downstream Services (GAP-13)

**Priority:** P1 — Services unprotected when accessed directly  
**Severity:** High | **Effort:** Medium

**Devin prompt:**
```
Add JWT validation to all downstream services so they are protected
even when accessed outside the API Gateway.

1. Add spring-boot-starter-oauth2-resource-server to build.gradle of:
   - core-banking-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service
   - internet-banking-user-service

2. Configure each service's application.yml with:
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             jwk-set-uri: http://keycloak_web:8080/realms/{realm}/protocol/openid-connect/certs

3. Create a SecurityConfig class in each service that:
   - Permits /actuator/** without auth
   - Requires authentication for all other endpoints
   - Extracts the principal from the JWT (replacing X-Auth-Id header reliance)

4. Configure Feign clients to propagate the Bearer token from incoming
   requests using a RequestInterceptor that reads the Authorization header
   from the current request context.

5. Keep the POST /user/api/v1/bank-users/register endpoint public
   (permitAll).
```

---

### 2.4 Add Circuit Breakers (GAP-28, GAP-31)

**Priority:** P1 — No resilience against downstream failures  
**Severity:** Critical | **Effort:** Medium

**Devin prompt:**
```
Add Resilience4j circuit breakers to fund-transfer-service,
utility-payment-service, and user-service for all Feign calls to
core-banking-service.

1. Add to each service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breaker defaults in application.yml:
   resilience4j:
     circuitbreaker:
       configs:
         default:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 30000
           permittedNumberOfCallsInHalfOpenState: 3

3. Add @CircuitBreaker annotations on Feign client interfaces or
   create FallbackFactory implementations that:
   - Log the failure
   - Return a meaningful error response (e.g., "Core banking service
     temporarily unavailable, please try again later")
   - Set the local transaction status to FAILED

4. Add Actuator endpoints for circuit breaker monitoring:
   management.endpoints.web.exposure.include: health,circuitbreakers,circuitbreakerevents
```

---

### 2.5 Add Idempotency Keys to Payment Endpoints (GAP-32)

**Priority:** P1 — Double-charge risk on retries  
**Severity:** Critical | **Effort:** Medium

**Devin prompt:**
```
Add idempotency key support to fund-transfer and utility-payment endpoints.

1. Require an X-Idempotency-Key header on POST /transfer and
   POST /utility-payment.

2. In each service, create an IdempotencyFilter or interceptor that:
   - Extracts the idempotency key from the request header
   - Checks a database table (idempotency_keys) for an existing entry
     with the same key
   - If found: return the stored response (HTTP 200 with original result)
   - If not found: proceed with the request, then store the key + response

3. Create the idempotency_keys table via Flyway migration:
   CREATE TABLE idempotency_keys (
       idempotency_key VARCHAR(64) PRIMARY KEY,
       response_body TEXT,
       response_status INT,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       expires_at TIMESTAMP
   );

4. Add a scheduled task to purge expired idempotency keys (e.g., after 24h).

5. Return 409 Conflict if the same idempotency key is used with
   different request parameters.
```

---

### 2.6 Add Duplicate Detection (GAP-36)

**Priority:** P1  
**Severity:** High | **Effort:** Medium

**Devin prompt:**
```
Add server-side duplicate transaction detection to core-banking-service.

1. Add a unique constraint on banking_core_transaction:
   ALTER TABLE banking_core_transaction
   ADD CONSTRAINT uk_transaction_id UNIQUE (transaction_id);

2. In TransactionService, before creating a transaction, check if a
   transaction with the same fromAccount + toAccount + amount exists
   within the last 5 minutes. If so, reject with a clear error message
   suggesting the client use a different idempotency key if intentional.

3. Add a Flyway migration for the unique constraint.
```

---

### 2.7 Standardize Error Response Format (GAP-07)

**Priority:** P2  
**Severity:** Medium | **Effort:** Small

**Devin prompt:**
```
Standardize the error response format across all services.

1. Define a canonical ErrorResponse in the shared library (banking-common):
   {
     "error": {
       "code": "INSUFFICIENT_FUNDS",
       "message": "Account 100015003000 has insufficient funds for this transfer",
       "timestamp": "2026-05-07T13:00:00Z",
       "traceId": "abc123"
     }
   }

2. Update all GlobalExceptionHandler methods to return this format.

3. Include the Micrometer trace ID in every error response for
   correlation with Zipkin traces.

4. Update the Feign error decoders to parse this canonical format.
```

---

### 2.8 Return Pagination Metadata (GAP-19)

**Priority:** P2  
**Severity:** Medium | **Effort:** Small

**Devin prompt:**
```
Update all list endpoints to return pagination metadata.

1. Change return types from List<T> to Page<T> (or a custom PageResponse<T>):
   - FundTransferService.readFundTransfers()
   - UtilityPaymentService.readUtilityPayment()
   - UserService.readUsers()
   - UserService (core-banking) getUsers()

2. Instead of calling .getContent() on the Page, return the full Page object
   which serializes to:
   {
     "content": [...],
     "totalElements": 100,
     "totalPages": 10,
     "number": 0,
     "size": 10,
     "first": true,
     "last": false
   }
```

---

### 2.9 Restrict Database Privileges (GAP-15)

**Priority:** P2  
**Severity:** Medium | **Effort:** Small

**Devin prompt:**
```
Restrict database privileges in docker-compose/mysql/privileges.sql.

Replace the current broad grant with schema-specific grants:

GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_service.* TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';

Keep CREATE, ALTER for a separate migration user or use Flyway's own
connection with elevated privileges.
```

---

### 2.10 Add Structured Logging (GAP-25)

**Priority:** P2  
**Severity:** Medium | **Effort:** Medium

**Devin prompt:**
```
Add structured JSON logging to all services.

1. Add logstash-logback-encoder to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender using LogstashEncoder for JSON output
   - Include MDC fields: traceId, spanId, userId, serviceName

3. Add MDC enrichment in AppAuthUserFilter:
   MDC.put("userId", apiRequestContext.getAuthId());

4. Configure profile-specific logging:
   - dev profile: plain text console output
   - docker profile: JSON structured output
```

---

## Phase 3 — Polish

> **Goal:** Comprehensive testing, advanced resilience, and operational maturity.  
> **Timeline:** 3-6 sprints  
> **Gaps covered:** GAP-09, GAP-10, GAP-11, GAP-33, GAP-20, GAP-21, GAP-22, GAP-23, GAP-26, GAP-27

---

### 3.1 Add Unit Tests to All Services (GAP-09)

**Priority:** P1 — 5 of 6 services have zero tests  
**Severity:** Critical | **Effort:** Large

**Devin prompt:**
```
Add comprehensive unit tests for all services. Target: 80%+ line coverage
on service and controller layers.

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer() success, Feign failure
     handling, insufficient funds propagation, validation errors
   - FundTransferControllerTest: MockMvc tests for POST /transfer (valid,
     invalid, missing fields) and GET /transfer (pagination)

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment() success, Feign failure,
     invalid provider
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

3. internet-banking-user-service:
   - UserServiceTest: test registerUser() success, duplicate email,
     NIC not found, email mismatch
   - UserControllerTest: MockMvc tests for all 4 endpoints

4. internet-banking-api-gateway:
   - Route configuration tests verifying path predicates and filters

Use Mockito for mocking Feign clients and repositories. Use H2 for
any tests that need a database. Follow existing test patterns in
core-banking-service as reference.
```

---

### 3.2 Add Integration Tests (GAP-10)

**Priority:** P1  
**Severity:** High | **Effort:** Large

**Devin prompt:**
```
Add integration tests using Testcontainers for MySQL and a realistic
end-to-end fund transfer flow.

1. Add Testcontainers dependencies to core-banking-service and
   fund-transfer-service build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create CoreBankingIntegrationTest:
   - Start MySQL Testcontainer
   - Run Flyway migrations
   - Test full fund transfer: create accounts → POST /fund-transfer →
     verify balances → verify transaction records

3. Create FundTransferIntegrationTest:
   - Use WireMock to stub core-banking-service responses
   - Test fund transfer success → verify local entity status = SUCCESS
   - Test core-banking failure → verify local entity status = FAILED
   - Test core-banking timeout → verify behavior with circuit breaker

4. Create UtilityPaymentIntegrationTest with similar pattern.
```

---

### 3.3 Add Contract Tests (GAP-11)

**Priority:** P2  
**Severity:** Medium | **Effort:** Large

**Devin prompt:**
```
Add Spring Cloud Contract tests for the core-banking-service API
consumed by fund-transfer and utility-payment services.

1. Add Spring Cloud Contract dependencies to core-banking-service:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   Plugin: id 'org.springframework.cloud.contract' version '4.1.1'

2. Write contracts in core-banking-service/src/test/resources/contracts/:
   - fundTransfer_success.groovy: POST /api/v1/transaction/fund-transfer
     with valid body → 200 + {message, transactionId}
   - fundTransfer_insufficientFunds.groovy: → 422 + error response
   - utilPayment_success.groovy: POST /api/v1/transaction/util-payment → 200
   - accountLookup.groovy: GET /api/v1/account/bank-account/{num} → 200

3. Generate contract stubs JAR from core-banking-service.

4. In fund-transfer-service and utility-payment-service, write
   stub-based tests using @AutoConfigureStubRunner that verify
   Feign clients parse responses correctly.
```

---

### 3.4 Implement Saga Pattern for Distributed Transactions (GAP-33)

**Priority:** P1  
**Severity:** High | **Effort:** Large

**Devin prompt:**
```
Implement a choreography-based saga for the fund transfer flow to handle
partial failures between fund-transfer-service and core-banking-service.

1. Add RabbitMQ dependencies (already in Docker stack but unused):
   implementation 'org.springframework.boot:spring-boot-starter-amqp'

2. Define events:
   - FundTransferInitiated (published by fund-transfer-service)
   - FundTransferCompleted (published by core-banking-service on success)
   - FundTransferFailed (published by core-banking-service on failure)
   - FundTransferCompensation (published to reverse a completed transfer)

3. In core-banking-service, add a compensation endpoint:
   POST /api/v1/transaction/compensate/{transactionId}
   that reverses the debit/credit and creates compensating transaction entries.

4. In fund-transfer-service, add a scheduled reconciliation job that:
   - Finds PROCESSING records older than 5 minutes
   - Queries core-banking for the transaction status
   - Updates or compensates accordingly

5. Add dead-letter queue handling for failed event processing.
```

---

### 3.5 Standardize API Path Naming (GAP-20)

**Priority:** P3  
**Severity:** Low | **Effort:** Small

**Devin prompt:**
```
Standardize all API paths to use plural nouns consistently.

Current → Proposed:
- /api/v1/transfer → /api/v1/transfers
- /api/v1/utility-payment → /api/v1/utility-payments
- /api/v1/bank-users → /api/v1/users (already plural, rename for consistency)
- /api/v1/account/bank-account/{n} → /api/v1/accounts/{accountNumber}
- /api/v1/account/util-account/{n} → /api/v1/utility-accounts/{providerName}
- /api/v1/transaction/fund-transfer → /api/v1/transactions/fund-transfer
- /api/v1/user/{id} → /api/v1/users/{identification}

Update API Gateway route predicates to match new paths.
Keep old paths as deprecated aliases (forward to new paths) for
backward compatibility during transition.
```

---

### 3.6 Add API Version Negotiation (GAP-21)

**Priority:** P3  
**Severity:** Low | **Effort:** Small

**Devin prompt:**
```
Document and implement an API versioning strategy.

1. Keep URL-based versioning (/api/v1/, /api/v2/) as the primary strategy.

2. Create a VERSIONING.md document describing:
   - When to create v2 (breaking changes only)
   - Deprecation policy (v(N-1) supported for 6 months after v(N) release)
   - How to add a v2 endpoint alongside v1

3. Add an API-Version response header to all responses via a Spring
   WebFilter/HandlerInterceptor showing the current version.

4. Add a Sunset header on deprecated endpoints per RFC 8594.
```

---

### 3.7 Add Filtering and Sorting to List Endpoints (GAP-22)

**Priority:** P3  
**Severity:** Low | **Effort:** Medium

**Devin prompt:**
```
Add domain-specific filtering to list endpoints.

1. Fund transfers — GET /api/v1/transfer?status=SUCCESS&fromAccount=X&dateFrom=&dateTo=
   Add a FundTransferSpecification using Spring Data JPA Specifications.

2. Utility payments — GET /api/v1/utility-payment?providerId=X&status=SUCCESS&dateFrom=&dateTo=
   Add UtilityPaymentSpecification.

3. Users — GET /api/v1/bank-users?status=APPROVED&search=sam
   Add UserSpecification.

4. Use Pageable for pagination + sorting: ?sort=createdDate,desc
```

---

### 3.8 Add Generic Type Parameters to ResponseEntity (GAP-23)

**Priority:** P3  
**Severity:** Low | **Effort:** Small

**Devin prompt:**
```
Add generic type parameters to all ResponseEntity return types in controllers.

Examples:
- ResponseEntity → ResponseEntity<FundTransferResponse>
- ResponseEntity → ResponseEntity<List<FundTransferDto>>
- ResponseEntity → ResponseEntity<ErrorResponse>

This improves OpenAPI documentation accuracy (Swagger will show exact
response schemas) and IDE type checking.

Update all controller methods in:
- core-banking-service: AccountController, TransactionController, UserController
- fund-transfer-service: FundTransferController
- utility-payment-service: UtilityPaymentController
- user-service: UserController
```

---

### 3.9 Add Custom Health Indicators (GAP-26)

**Priority:** P3  
**Severity:** Low | **Effort:** Small

**Devin prompt:**
```
Add custom HealthIndicator beans to verify critical dependencies.

1. In fund-transfer-service and utility-payment-service:
   @Component
   public class CoreBankingHealthIndicator implements HealthIndicator {
       // Call core-banking actuator/health via Feign
       // Return Health.up() or Health.down() with details
   }

2. In user-service:
   - CoreBankingHealthIndicator (same as above)
   - KeycloakHealthIndicator (check Keycloak /realms/{realm} endpoint)

3. In core-banking-service:
   - MySQL connectivity is auto-detected by Spring Boot's DataSourceHealthIndicator

4. Configure actuator exposure:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
     endpoint:
       health:
         show-details: when_authorized
```

---

### 3.10 Add Custom Business Metrics (GAP-27)

**Priority:** P3  
**Severity:** Low | **Effort:** Medium

**Devin prompt:**
```
Add custom Micrometer metrics for key business operations.

1. In core-banking-service TransactionService:
   - Counter: banking.transactions.total (tags: type=[FUND_TRANSFER|UTILITY_PAYMENT], status=[SUCCESS|FAILED])
   - Timer: banking.transactions.duration (tags: type)
   - Gauge: banking.accounts.total (by status)

2. In fund-transfer-service:
   - Counter: banking.fund_transfers.initiated
   - Counter: banking.fund_transfers.completed (tags: status=[SUCCESS|FAILED])
   - Timer: banking.fund_transfers.core_banking_call.duration

3. In utility-payment-service:
   - Counter: banking.utility_payments.initiated
   - Counter: banking.utility_payments.completed
   - Timer: banking.utility_payments.core_banking_call.duration

4. In user-service:
   - Counter: banking.user_registrations.total (tags: status=[SUCCESS|FAILED])

5. Expose metrics via Actuator for Prometheus scraping:
   management.endpoints.web.exposure.include: health,metrics,prometheus
```

---

## Phase Summary

| Phase | Items | Gaps Covered | Estimated Sprints |
|---|---|---|---|
| **Phase 1 — Quick Wins** | 12 items | GAP-34, GAP-12, GAP-14, GAP-05, GAP-06, GAP-08, GAP-16, GAP-24, GAP-17, GAP-29, GAP-30, GAP-35, GAP-37, GAP-18, GAP-04 | 1-2 sprints |
| **Phase 2 — Important** | 10 items | GAP-01, GAP-02, GAP-03, GAP-07, GAP-13, GAP-15, GAP-19, GAP-25, GAP-28, GAP-31, GAP-32, GAP-36 | 2-4 sprints |
| **Phase 3 — Polish** | 10 items | GAP-09, GAP-10, GAP-11, GAP-20, GAP-21, GAP-22, GAP-23, GAP-26, GAP-27, GAP-33 | 3-6 sprints |

### Critical Path

```
Phase 1.1  Fix double-deduction bug          ─┐
Phase 1.2  Add input validation              ─┤
Phase 1.3  Externalize credentials           ─┤── Can be done in parallel
Phase 1.4  Fix HTTP status codes             ─┤
Phase 1.5  Add Feign error decoder           ─┘
                                               │
Phase 2.1  Extract shared library            ──┤── Depends on Phase 1.4, 1.5
Phase 2.3  Add downstream auth               ──┤   (error handling consolidated first)
Phase 2.4  Add circuit breakers              ──┘
                                               │
Phase 2.5  Add idempotency keys             ───┤── Depends on Phase 2.1
Phase 2.7  Standardize error format          ──┘   (shared library available)
                                               │
Phase 3.1  Add unit tests                   ───┤── Depends on Phase 2.1
Phase 3.2  Add integration tests             ──┤   (shared library + error handling stable)
Phase 3.4  Implement saga pattern            ──┘
```
