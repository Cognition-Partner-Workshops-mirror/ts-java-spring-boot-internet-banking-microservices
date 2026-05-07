# Internet Banking Microservices - Remediation Roadmap

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 - Quick Wins**: High severity + low effort. Immediate risk reduction with minimal investment.
- **Phase 2 - Important**: High severity + medium effort, or medium severity items that compound.
- **Phase 3 - Polish**: Lower severity items that improve maintainability and developer experience.

Each item includes a sample **Devin prompt** that can be used to execute the remediation.

---

## Phase 1: Quick Wins (1-2 Weeks)

> High-severity gaps that can be fixed with small, focused changes.

### 1.1 Fix Double-Deduction Balance Bug (GAP-36)

**Priority**: P0 - Data Corruption  
**Severity**: Critical | **Effort**: Small

The `availableBalance` calculation in `TransactionService` uses the already-debited `actualBalance`, causing double subtraction on debits and double addition on credits.

**Files to change**: `core-banking-service/.../service/TransactionService.java`

**Devin Prompt**:
```
Fix the double-deduction bug in core-banking-service TransactionService.

In internalFundTransfer() (lines 90-91), availableBalance is computed from 
the already-debited actualBalance. Change line 91 to:
  fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance());
And line 100 to:
  toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance());

Apply the same fix in utilPayment() (lines 63-64):
  fromAccount.setAvailableBalance(fromAccount.getActualBalance());

Update the existing unit tests in TransactionServiceTest to assert correct 
availableBalance values after transfers and payments.
```

---

### 1.2 Fix Error Handling - Proper HTTP Status Codes (GAP-05, GAP-06)

**Priority**: P0 - Security + Correctness  
**Severity**: Critical | **Effort**: Small

Replace the catch-all `400 Bad Request` handler with specific status codes and remove stack trace leakage.

**Files to change**: `GlobalExceptionHandler.java` in all 4 business services.

**Devin Prompt**:
```
Refactor GlobalExceptionHandler in all 4 business services (core-banking-service, 
internet-banking-fund-transfer-service, internet-banking-user-service, 
internet-banking-utility-payment-service).

For each GlobalExceptionHandler:
1. Map EntityNotFoundException to 404 Not Found
2. Map InsufficientFundsException to 422 Unprocessable Entity  
3. Map SimpleBankingGlobalException to 400 Bad Request (keep existing behavior)
4. Map all other Exception to 500 Internal Server Error
5. Replace the catch-all handler body from:
     .body("Exception occur inside API " + e)
   to a structured ErrorResponse with a generic message (no stack trace):
     .body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred").build())
6. Add @Slf4j logging of the full exception at ERROR level in the catch-all handler

Ensure consistent ErrorResponse format across all services.
```

---

### 1.3 Add Input Validation to All Request DTOs (GAP-13)

**Priority**: P0 - Security  
**Severity**: Critical | **Effort**: Small

**Devin Prompt**:
```
Add Jakarta Bean Validation annotations to all request DTOs across the codebase 
and enable validation on controller endpoints.

1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle 
   for core-banking-service, fund-transfer-service, user-service, and 
   utility-payment-service.

2. Add validation annotations to these DTOs:
   - FundTransferRequest: @NotBlank on fromAccount and toAccount, 
     @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, 
     @NotBlank on referenceNumber and account
   - User (user-service): @NotBlank @Email on email, @NotBlank on identification, 
     @NotBlank @Size(min=8) on password
   - UserUpdateRequest: @NotNull on status

3. Add @Valid annotation before @RequestBody in all controller methods that 
   accept request bodies.

4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler 
   that returns 400 with field-level error details.
```

---

### 1.4 Move Hardcoded Credentials to Environment Variables (GAP-15)

**Priority**: P0 - Security  
**Severity**: Critical | **Effort**: Small

**Devin Prompt**:
```
Replace all hardcoded credentials in docker-compose files with environment 
variable references.

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD}
   - Replace POSTGRES_PASSWORD value with ${POSTGRES_PASSWORD}

2. In docker-compose/mysql/Dockerfile:
   - Remove the ENV MYSQL_ROOT_PASSWORD line (it's passed via docker-compose)

3. In docker-compose/mysql/privileges.sql:
   - Add a comment noting the password should match the MYSQL_APP_PASSWORD env var
   - Note: This file runs at init time and can't use env vars directly; 
     document the manual step required

4. Create a docker-compose/.env.example file with all required variables 
   and placeholder values.

5. Add .env to .gitignore if not already present.
```

---

### 1.5 Fix OpenAPI Dependency (GAP-23)

**Priority**: P1  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
In core-banking-service, internet-banking-fund-transfer-service, 
internet-banking-user-service, and internet-banking-utility-payment-service, 
replace the incorrect OpenAPI dependency in each build.gradle:

Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (spring-boot-starter-web), not WebFlux.
Verify the Swagger UI loads at /swagger-ui.html after the change.
```

---

### 1.6 Fix Transaction Entity Mapping (GAP-37)

**Priority**: P1  
**Severity**: High | **Effort**: Small

**Devin Prompt**:
```
In core-banking-service TransactionEntity.java, fix the JPA mapping for the 
account relationship:

1. Change @OneToOne(cascade = CascadeType.ALL) to @ManyToOne(fetch = FetchType.LAZY)
2. Remove CascadeType.ALL to prevent accidental account deletion
3. Keep the @JoinColumn(name = "account_id", referencedColumnName = "id")
4. Verify the Flyway migration already has the correct FK constraint 
   (it does - the DB schema is fine, only the JPA mapping is wrong)
```

---

### 1.7 Add Feign Error Decoders to Remaining Services (GAP-07)

**Priority**: P1  
**Severity**: High | **Effort**: Small

**Devin Prompt**:
```
Add CustomFeignErrorDecoder to internet-banking-fund-transfer-service and 
internet-banking-utility-payment-service, mirroring the implementation in 
internet-banking-user-service.

1. Copy CustomFeignErrorDecoder.java from user-service to both services, 
   adjusting the package name.
2. Update each service's CustomFeignClientConfiguration to register the 
   error decoder bean (like user-service does).
3. Ensure SimpleBankingGlobalException and ErrorResponse classes exist in 
   both services (they already do as duplicated code).
```

---

### 1.8 Add Feign Timeout Configuration (GAP-32)

**Priority**: P1  
**Severity**: High | **Effort**: Small

**Devin Prompt**:
```
Add explicit timeout configuration for Feign clients in 
internet-banking-fund-transfer-service, internet-banking-user-service, and 
internet-banking-utility-payment-service.

In each service's application.yml (or bootstrap.yml), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

This sets a 5-second connection timeout and 10-second read timeout for all 
Feign clients. Adjust values based on expected core-banking response times.
```

---

### 1.9 Add Feign Retry Policy (GAP-31)

**Priority**: P1  
**Severity**: High | **Effort**: Small

**Devin Prompt**:
```
Add a Feign Retryer bean to the CustomFeignClientConfiguration in 
fund-transfer-service, user-service, and utility-payment-service.

Add to each CustomFeignClientConfiguration:

@Bean
public Retryer retryer() {
    return new Retryer.Default(100, 1000, 3);
    // 100ms initial interval, 1s max interval, 3 max attempts
}

Import feign.Retryer. Note: Only GET requests should be retried automatically. 
For POST (financial transactions), retries should only happen with idempotency 
keys (see Phase 2).
```

---

### 1.10 Fix Inconsistent Logging (GAP-26)

**Priority**: P2  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
Fix logging issues across all services:

1. In FundTransferService.java line 32, fix string concatenation in log:
   Change: log.info("Sending fund transfer request {}" + request.toString());
   To: log.info("Sending fund transfer request {}", request);

2. Remove all .toString() calls in log statements across all controllers 
   and services - SLF4J calls toString() automatically.

3. Ensure no request objects containing passwords are logged. In 
   UserController.createUser(), the User object contains a password field. 
   Either exclude password from logging or log only non-sensitive fields.
```

---

### 1.11 Fix Keycloak Singleton Thread Safety (GAP-17)

**Priority**: P2  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
Fix the thread-safety issue in internet-banking-user-service 
KeycloakProperties.getInstance().

Replace the manual lazy singleton with a synchronized block or, better, 
use a @PostConstruct method:

@PostConstruct
public void init() {
    keycloakInstance = KeycloakBuilder.builder()
        .serverUrl(serverUrl)
        .realm(realm)
        .grantType("client_credentials")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .build();
}

public Keycloak getInstance() {
    return keycloakInstance;
}

Remove the static modifier from keycloakInstance and make it a regular 
instance field.
```

---

### 1.12 Add ResponseEntity Generic Types (GAP-20)

**Priority**: P2  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
Add proper generic type parameters to all ResponseEntity return types across 
all controllers in the codebase.

For example, change:
  public ResponseEntity readFundTransfers(Pageable pageable)
To:
  public ResponseEntity<List<FundTransfer>> readFundTransfers(Pageable pageable)

Do this for every controller method in:
- core-banking-service: AccountController, TransactionController, UserController
- fund-transfer-service: FundTransferController
- user-service: UserController  
- utility-payment-service: UtilityPaymentController

This enables accurate OpenAPI schema generation.
```

---

## Phase 2: Important (2-4 Weeks)

> High-severity gaps requiring medium effort, plus compounding medium-severity items.

### 2.1 Add Circuit Breakers with Resilience4j (GAP-30, GAP-33)

**Priority**: P0  
**Severity**: Critical | **Effort**: Medium

**Devin Prompt**:
```
Add Resilience4j circuit breakers to all Feign clients in fund-transfer-service, 
user-service, and utility-payment-service.

1. Add dependencies to each service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign in application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return error response 
     with "Core banking service temporarily unavailable"
   - BankingCoreRestClientFallback (utility-payment): same pattern
   - BankingCoreRestClientFallback (user-service): same pattern

4. Register fallbacks in @FeignClient annotations:
   @FeignClient(name = "core-banking-service", fallback = BankingCoreFeignClientFallback.class)

5. Configure circuit breaker thresholds in application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         core-banking-service:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 30s
           permittedNumberOfCallsInHalfOpenState: 3
```

---

### 2.2 Add Idempotency Keys for Financial Operations (GAP-34)

**Priority**: P0  
**Severity**: Critical | **Effort**: Medium

**Devin Prompt**:
```
Add idempotency key support for fund transfer and utility payment POST endpoints.

1. Add an idempotencyKey field to FundTransferRequest and UtilityPaymentRequest.

2. In FundTransferService.fundTransfer():
   - Before creating a new entity, check if a fund_transfer with the same 
     idempotencyKey already exists
   - If found, return the existing response (don't process again)
   - Add a unique constraint on idempotencyKey in FundTransferEntity

3. Apply the same pattern in UtilityPaymentService.utilPayment().

4. Add a Flyway migration (or JPA schema update) to add the idempotency_key 
   column with a unique index.

5. Document that clients should generate a UUID and pass it as idempotencyKey 
   to prevent duplicate transactions.
```

---

### 2.3 Add Compensation / Saga for Failed Transactions (GAP-35)

**Priority**: P0  
**Severity**: Critical | **Effort**: Medium

**Devin Prompt**:
```
Add compensation logic for failed Feign calls in fund-transfer-service and 
utility-payment-service.

1. In FundTransferService.fundTransfer(), wrap the Feign call in try-catch:
   - On success: update status to SUCCESS (existing behavior)
   - On failure: update status to FAILED, log the error
   - Add a new TransactionStatus.FAILED enum value

2. Apply the same pattern in UtilityPaymentService.utilPayment().

3. Create a scheduled job (@Scheduled) in each service that:
   - Queries for PENDING/PROCESSING records older than 5 minutes
   - Attempts to retry the Feign call
   - After 3 retry attempts, marks as FAILED and logs for manual review

4. Add a retryCount field to FundTransferEntity and UtilityPaymentEntity.

5. Add FAILED status to TransactionStatus enum in both services.
```

---

### 2.4 Secure Downstream Services (GAP-14)

**Priority**: P0  
**Severity**: Critical | **Effort**: Medium

**Devin Prompt**:
```
Add JWT validation to downstream services so they don't rely solely on 
the X-Auth-Id header.

Option A (recommended - simpler): Add Spring Security OAuth2 Resource Server 
to each downstream service:

1. Add to build.gradle of core-banking, fund-transfer, user-service, 
   utility-payment:
   implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
   implementation 'org.springframework.boot:spring-boot-starter-security'

2. Configure JWT validation in application.yml:
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             jwk-set-uri: ${KEYCLOAK_JWK_URI}

3. Create a SecurityConfiguration class in each service that:
   - Permits actuator endpoints
   - Requires authentication for all other endpoints

4. Configure Feign clients to forward the Authorization header from the 
   incoming request to downstream calls.

5. Remove reliance on X-Auth-Id header for authentication (keep it for 
   logging/audit only).
```

---

### 2.5 Add Pagination Metadata to Responses (GAP-21)

**Priority**: P2  
**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
```
Add pagination metadata to all paginated GET endpoints.

1. Create a shared PageResponse<T> wrapper class:
   public class PageResponse<T> {
       private List<T> content;
       private int page;
       private int size;
       private long totalElements;
       private int totalPages;
       private boolean last;
   }

2. Update all services that return paginated data to use PageResponse:
   - core-banking-service UserController.readUsers()
   - fund-transfer-service FundTransferController.readFundTransfers()
   - user-service UserController.readUsers()
   - utility-payment-service UtilityPaymentController.readPayments()

3. Change the service layer to return Page<T> instead of List<T>, and let 
   the controller wrap it in PageResponse.
```

---

### 2.6 Add Unit Tests for Remaining Services (GAP-09)

**Priority**: P1  
**Severity**: High | **Effort**: Large

**Devin Prompt**:
```
Add unit tests for internet-banking-fund-transfer-service.

Create FundTransferServiceTest with these test cases:
1. fundTransfer_success: Mock Feign client, verify entity saved with 
   PENDING then SUCCESS, verify response
2. fundTransfer_feignFailure: Mock Feign client throwing exception, 
   verify entity stays PENDING
3. readAllTransfers_success: Mock repository, verify pagination works

Create FundTransferControllerTest using @WebMvcTest:
1. POST /api/v1/transfer with valid request returns 200
2. POST /api/v1/transfer with invalid request returns 400
3. GET /api/v1/transfer returns paginated list

Repeat the same pattern for internet-banking-utility-payment-service 
and internet-banking-user-service.
```

---

### 2.7 Add Dependency Vulnerability Scanning (GAP-18)

**Priority**: P2  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
Add OWASP Dependency Check to all service build.gradle files.

1. Add the plugin to each build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.10'
   }

2. Configure the task:
   dependencyCheck {
       failBuildOnCVSS = 7.0
       formats = ['HTML', 'JSON']
   }

3. Run ./gradlew dependencyCheckAnalyze in each service and document 
   any findings.

Alternatively, add this as a GitHub Actions workflow that runs weekly.
```

---

### 2.8 Scope Database Permissions (GAP-19)

**Priority**: P2  
**Severity**: Medium | **Effort**: Small

**Devin Prompt**:
```
Update docker-compose/mysql/privileges.sql to use least-privilege database 
permissions.

Replace the single broad grant with schema-specific grants:

CREATE USER 'javatodev_development'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';

GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_service.* 
  TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* 
  TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* 
  TO 'javatodev_development'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* 
  TO 'javatodev_development'@'%';

FLUSH PRIVILEGES;

Note: CREATE, ALTER, DROP are needed for JPA auto-DDL services. Consider 
using separate users for Flyway migrations vs application runtime.
```

---

## Phase 3: Polish (4-8 Weeks)

> Lower-severity items that improve developer experience and long-term maintainability.

### 3.1 Create Shared Library Module (GAP-01, GAP-02)

**Priority**: P3  
**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
```
Create a shared library module and convert the project to a Gradle 
multi-module build.

1. Create a root settings.gradle that includes all 7 services plus a new 
   banking-common module.

2. Create banking-common/ with:
   - AuditAware base class
   - BaseMapper interface
   - GlobalExceptionHandler
   - ErrorResponse / SimpleBankingGlobalException
   - AppAuthUserFilter / ApiRequestContext / ApiRequestContextHolder

3. Add banking-common as a dependency in each service's build.gradle:
   implementation project(':banking-common')

4. Remove the duplicated classes from each service.

5. Create a root build.gradle with shared configuration (Java version, 
   Spring Boot version, common dependencies).
```

---

### 3.2 Add Integration Tests (GAP-10)

**Priority**: P2  
**Severity**: High | **Effort**: Large

**Devin Prompt**:
```
Add integration tests using Testcontainers for database-backed services.

1. Add Testcontainers dependencies to build.gradle for core-banking, 
   fund-transfer, user-service, and utility-payment:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create base test configuration class with @Testcontainers that starts 
   a MySQL container.

3. For core-banking-service, create:
   - AccountRepositoryIntegrationTest: verify findByNumber works
   - TransactionServiceIntegrationTest: verify fund transfer updates 
     both accounts in a single transaction

4. For fund-transfer-service, create:
   - FundTransferRepositoryIntegrationTest: verify entity persistence

5. Use WireMock to mock Feign client responses for service-level 
   integration tests.
```

---

### 3.3 Add Contract Tests (GAP-11)

**Priority**: P3  
**Severity**: Medium | **Effort**: Large

**Devin Prompt**:
```
Add Spring Cloud Contract tests between services.

1. Add Spring Cloud Contract to core-banking-service (producer side):
   - Add spring-cloud-starter-contract-verifier plugin
   - Create contract DSL files in src/test/resources/contracts/ for each 
     endpoint that Feign clients call

2. The contract verifier will generate:
   - Server-side tests for core-banking-service
   - Client stubs (WireMock) published to local Maven

3. In consumer services (fund-transfer, utility-payment, user-service):
   - Add spring-cloud-starter-contract-stub-runner
   - Write tests that use the generated stubs to verify Feign client 
     compatibility
```

---

### 3.4 Add Controller Tests (GAP-12)

**Priority**: P3  
**Severity**: Medium | **Effort**: Medium

**Devin Prompt**:
```
Add @WebMvcTest controller tests for all services.

For each controller in each service:
1. Create a test class annotated with @WebMvcTest(XController.class)
2. Mock the service layer with @MockBean
3. Test each endpoint for:
   - Successful response (200)
   - Validation errors (400) - after GAP-13 fix is in place
   - Not found errors (404) - after GAP-05 fix is in place
   - Correct content type (application/json)
   - Correct response structure

Start with core-banking-service controllers since they have the most 
complex logic, then expand to other services.
```

---

### 3.5 Standardize REST URL Patterns (GAP-22)

**Priority**: P3  
**Severity**: Low | **Effort**: Small

**Devin Prompt**:
```
Standardize REST URL patterns in internet-banking-user-service:

1. Change POST /api/v1/bank-users/register to POST /api/v1/bank-users
   (the HTTP method already implies creation)

2. Change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
   (the HTTP method already implies update)

3. Update the API Gateway security configuration to match the new paths:
   - Change "/user/api/v1/bank-users/register" to "/user/api/v1/bank-users" 
     with POST method matcher

4. Update any Postman collections in postman_collection/ to reflect 
   the new URLs.

Note: This is a breaking API change - coordinate with any existing clients.
```

---

### 3.6 Add Custom Health Indicators (GAP-27)

**Priority**: P3  
**Severity**: Low | **Effort**: Small

**Devin Prompt**:
```
Add custom health check indicators to services with external dependencies.

1. In core-banking-service, add a DatabaseHealthIndicator that runs a 
   simple query to verify MySQL connectivity.

2. In user-service, add a KeycloakHealthIndicator that checks Keycloak 
   server info endpoint.

3. In fund-transfer-service and utility-payment-service, add a 
   CoreBankingHealthIndicator that calls the core-banking actuator 
   health endpoint.

4. Configure actuator exposure in application.yml:
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

### 3.7 Add Custom Business Metrics (GAP-28)

**Priority**: P3  
**Severity**: Low | **Effort**: Medium

**Devin Prompt**:
```
Add Micrometer custom metrics for business-relevant measurements.

1. In fund-transfer-service FundTransferService:
   - Counter: fund.transfer.total (tags: status=SUCCESS|FAILED)
   - Timer: fund.transfer.duration
   - DistributionSummary: fund.transfer.amount

2. In utility-payment-service UtilityPaymentService:
   - Counter: utility.payment.total (tags: status=SUCCESS|FAILED)
   - Timer: utility.payment.duration

3. In user-service UserService:
   - Counter: user.registration.total (tags: status=SUCCESS|FAILED)
   - Counter: user.approval.total

4. Inject MeterRegistry into each service and record metrics at 
   appropriate points in the business logic.
```

---

### 3.8 Normalize Feign Client Naming (GAP-03)

**Priority**: P3  
**Severity**: Low | **Effort**: Small

**Devin Prompt**:
```
Standardize Feign client naming across all services.

1. Rename fund-transfer-service's BankingCoreFeignClient to 
   BankingCoreRestClient (matching user-service and utility-payment-service)

2. Move it from package 'service.rest.client' to 'service.rest' 
   (matching other services)

3. Update all references in FundTransferService and configuration classes.
```

---

### 3.9 Convert Mappers to Spring Beans (GAP-04)

**Priority**: P3  
**Severity**: Low | **Effort**: Small

**Devin Prompt**:
```
Convert all mapper classes to Spring-managed beans.

1. Add @Component to all mapper classes:
   - core-banking: BankAccountMapper, UtilityAccountMapper, UserMapper
   - fund-transfer: FundTransferMapper
   - user-service: UserMapper
   - utility-payment: UtilityPaymentMapper

2. In service classes, replace inline instantiation:
   Change: private XMapper mapper = new XMapper();
   To: private final XMapper mapper; (injected via constructor)

3. Make sure @RequiredArgsConstructor picks up the new final field.
```

---

### 3.10 Document API Versioning Strategy (GAP-24)

**Priority**: P3  
**Severity**: Low | **Effort**: Medium

**Devin Prompt**:
```
Create an API versioning strategy document at docs/API_VERSIONING.md.

Document:
1. Current versioning approach (URL path: /api/v1/)
2. Guidelines for when to increment the version
3. Deprecation policy (how long v1 will be supported when v2 ships)
4. Header-based version negotiation as future option
5. Backward compatibility requirements for minor changes
```

---

### 3.11 Add Missing HTTP Methods (GAP-25)

**Priority**: P3  
**Severity**: Low | **Effort**: Small

**Devin Prompt**:
```
Add missing CRUD endpoints to appropriate services:

1. In user-service UserController:
   - DELETE /api/v1/bank-users/{id}: Soft-delete by setting status to DEACTIVATED
   - Also disable the Keycloak user

2. In core-banking-service AccountController:
   - PATCH /api/v1/account/bank-account/{account_number}/status: 
     Update account status (ACTIVE/FROZEN/CLOSED)

Add appropriate status enums if they don't exist.
```

---

## Priority Summary

| Phase   | Items | Estimated Duration | Key Outcomes                                       |
|---------|-------|--------------------|----------------------------------------------------|
| Phase 1 | 12    | 1-2 weeks          | Fix data corruption bugs, secure APIs, proper error handling, input validation |
| Phase 2 | 8     | 2-4 weeks          | Circuit breakers, idempotency, saga/compensation, JWT on all services, test coverage |
| Phase 3 | 11    | 4-8 weeks          | Shared library, integration/contract tests, REST standards, metrics, health checks |

### Critical Path

```
Phase 1 (immediate):
  GAP-36 (balance bug) -> GAP-05/06 (error handling) -> GAP-13 (validation) -> GAP-15 (secrets)

Phase 2 (after Phase 1):
  GAP-30 (circuit breakers) -> GAP-34 (idempotency) -> GAP-35 (compensation) -> GAP-14 (downstream auth)

Phase 3 (after Phase 2):
  GAP-01/02 (shared library) -> GAP-10 (integration tests) -> GAP-11 (contract tests)
```
