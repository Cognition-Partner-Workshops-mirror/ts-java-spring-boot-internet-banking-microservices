# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are prioritized into three phases based on severity and effort. Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (High Severity / Low Effort)

These items address critical bugs and security issues that can be fixed with small, focused changes.

### 1.1 Fix Balance Calculation Bug (Double Deduction)

**Gap Ref:** 7.6 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is calculated incorrectly in `TransactionService.internalFundTransfer()` and `utilPayment()` -- it subtracts the amount twice from `actualBalance`.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.

In internalFundTransfer(), lines 90-91:
- fromBankAccountEntity.setAvailableBalance() should equal the NEW actualBalance (after subtraction), not actualBalance minus amount again.
- Same fix needed for toBankAccountEntity (lines 99-100).

In utilPayment(), lines 63-64:
- Same double-deduction bug. availableBalance should just equal the new actualBalance.

The correct pattern is:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance()); // already subtracted

Update the existing unit tests in TransactionServiceTest.java to assert correct balance values after transfers. Open a PR.
```

---

### 1.2 Fix Error Handling: Stop Leaking Exceptions

**Gap Ref:** 2.1, 2.2, 2.4 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all services that have one (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service).

Changes needed in each GlobalExceptionHandler.java:

1. The catch-all Exception handler should:
   - Return HTTP 500 (Internal Server Error) instead of 400
   - Return a structured ErrorResponse JSON body (not a raw string with exception details)
   - Log the full exception server-side at ERROR level
   - Never expose exception class names or stack traces to the client

2. Add a dedicated handler for EntityNotFoundException that returns HTTP 404 (Not Found) with a structured ErrorResponse body.

3. Ensure all error responses use the same ErrorResponse { code, message } JSON structure.

Do NOT modify existing tests. Open a PR.
```

---

### 1.3 Add Input Validation to All Request DTOs

**Gap Ref:** 4.2 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Add Bean Validation (jakarta.validation) annotations to all request DTOs across all services.

1. Add spring-boot-starter-validation dependency to build.gradle for: core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service.

2. Add validation annotations to these DTOs:
   - core-banking-service FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - core-banking-service UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
   - internet-banking-fund-transfer-service FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - internet-banking-utility-payment-service UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
   - internet-banking-user-service User (for registration): @NotBlank email, @Email email, @NotBlank identification, @NotBlank password

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns HTTP 422 with field-level error details.

Open a PR.
```

---

### 1.4 Stop Logging Passwords and Sensitive Data

**Gap Ref:** 4.3, 6.1 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
Fix sensitive data exposure in logging and API responses across all services.

1. In internet-banking-user-service User.java DTO:
   - Add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field so it is accepted in requests but never serialized in responses.
   - Add @ToString.Exclude (Lombok) to the password field to prevent it from appearing in toString() output and logs.

2. In all controllers that log request.toString(), replace with specific field logging that excludes sensitive data. For example:
   - UserController: log email and identification only, never password
   - FundTransferController: log fromAccount, toAccount, amount only
   - UtilityPaymentController: log providerId, amount only

3. Review all log statements across all services for account numbers being logged at INFO level. Mask account numbers in logs (show only last 4 digits).

Open a PR.
```

---

### 1.5 Add Feign Error Decoder to All Services

**Gap Ref:** 2.3, 2.5 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add proper Feign error handling to internet-banking-user-service and ensure consistent error decoding across all Feign clients.

1. The internet-banking-fund-transfer-service already has a CustomFeignClientConfiguration with an error decoder. Copy this pattern to:
   - internet-banking-user-service (BankingCoreRestClient currently has no error decoder configured)
   - internet-banking-utility-payment-service (verify it has one; if not, add it)

2. In FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(), wrap the Feign call in a try-catch:
   - On failure, update the local entity status to FAILED
   - Log the error at ERROR level
   - Re-throw a meaningful exception that the GlobalExceptionHandler can handle

3. Ensure the Feign error decoder translates core-banking ErrorResponse into appropriate local exceptions.

Open a PR.
```

---

### 1.6 Add Retry and Timeout Configuration to Feign Clients

**Gap Ref:** 7.2, 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add timeout and retry configuration to all Feign clients in the project.

1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to build.gradle for: internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service.

2. Configure Feign timeouts in each service's application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connectTimeout: 5000
               readTimeout: 10000

3. Configure Resilience4j retry for Feign calls:
   resilience4j:
     retry:
       instances:
         coreBankingService:
           maxAttempts: 3
           waitDuration: 1s
           retryExceptions:
             - java.io.IOException
             - feign.RetryableException

4. Do NOT retry on 4xx client errors (only on 5xx and connection failures).

Open a PR.
```

---

### 1.7 Fix Pagination to Include Metadata

**Gap Ref:** 5.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix all paginated endpoints to return pagination metadata instead of raw lists.

In all services with paginated GET endpoints (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, core-banking-service):

1. Change service methods to return Page<T> or a custom PageResponse<T> wrapper instead of List<T>.

2. The response should include: content (list of items), totalElements, totalPages, pageNumber, pageSize.

3. Option A (simpler): Return Spring's Page<T> directly -- Jackson will serialize it with metadata.
   Option B (cleaner): Create a shared PageResponse<T> record with the fields above.

4. Update controllers to return ResponseEntity<Page<T>> or ResponseEntity<PageResponse<T>>.

Prefer Option A for simplicity. Open a PR.
```

---

### 1.8 Fix Hardcoded Credentials in Docker Compose

**Gap Ref:** 4.1 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Replace hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml with environment variable references.

1. Replace hardcoded values with ${VARIABLE:-default} syntax:
   - MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
   - KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
   - KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
   - KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
   - POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}

2. Create a docker-compose/.env.example file documenting all required environment variables.

3. Add docker-compose/.env to .gitignore.

4. Update README.md with instructions to copy .env.example to .env and set values before running docker-compose.

Open a PR.
```

---

## Phase 2: Important (High Severity / Medium Effort)

These items improve reliability and maintainability and require moderate implementation effort.

### 2.1 Add Circuit Breakers

**Gap Ref:** 7.1 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls across internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

1. Add spring-cloud-starter-circuitbreaker-resilience4j to each service's build.gradle (if not already added from the retry/timeout task).

2. Enable Feign circuit breaker integration in application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Configure circuit breaker instances in application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           registerHealthIndicator: true
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 30s
           permittedNumberOfCallsInHalfOpenState: 3

4. Add fallback classes for each Feign client that return meaningful error responses when the circuit is open. For example, FundTransferService should return a response indicating the service is temporarily unavailable.

5. Log circuit breaker state transitions at WARN level.

Open a PR.
```

---

### 2.2 Add Unit Tests for All Services

**Gap Ref:** 3.1 | **Severity:** Critical | **Effort:** Large (broken into parts)

**Devin Prompt (Part 1 -- User Service):**
```
Add unit tests for internet-banking-user-service.

Create test classes:
1. UserServiceTest -- test createUser (happy path, duplicate email, invalid email, user not found in core banking), readUsers, readUser, updateUser (approval enables Keycloak user).
2. KeycloakUserServiceTest -- test createUser, updateUser, readUserByEmail, readUser (not found case).

Use Mockito to mock KeycloakManager, UserRepository, BankingCoreRestClient, and KeycloakUserService.

Place tests in src/test/java/com/javatodev/finance/service/.
Ensure tests pass with ./gradlew :internet-banking-user-service:test.
Open a PR.
```

**Devin Prompt (Part 2 -- Fund Transfer & Utility Payment Services):**
```
Add unit tests for internet-banking-fund-transfer-service and internet-banking-utility-payment-service.

For fund-transfer-service, create FundTransferServiceTest:
- Test fundTransfer happy path (Feign call succeeds, entity updated to SUCCESS)
- Test fundTransfer when Feign call fails (entity should be FAILED)
- Test readAllTransfers pagination

For utility-payment-service, create UtilityPaymentServiceTest:
- Test utilPayment happy path
- Test utilPayment when Feign call fails
- Test readPayments pagination

Use Mockito to mock repositories and Feign clients.
Ensure tests pass with ./gradlew test for each service.
Open a PR.
```

---

### 2.3 Add Idempotency Protection for Financial Operations

**Gap Ref:** 7.5 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment endpoints.

1. Add an idempotencyKey field (String, UUID) to FundTransferRequest and UtilityPaymentRequest in both the internet-banking services and core-banking-service.

2. In FundTransferService and UtilityPaymentService:
   - Before processing, check if a record with the same idempotencyKey already exists in the local database
   - If it exists and is SUCCESS, return the existing response (do not re-process)
   - If it exists and is FAILED, allow retry
   - Add a unique constraint on idempotencyKey in the entity

3. Add @NotBlank validation on the idempotencyKey field.

4. Add unit tests for the idempotency logic (duplicate key returns existing result, new key processes normally).

Open a PR.
```

---

### 2.4 Extract Shared Library

**Gap Ref:** 1.1 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Create a shared library module and convert the project to a Gradle multi-project build.

1. Create a new module: internet-banking-common/
   - Move these shared classes into it:
     - exception/SimpleBankingGlobalException.java
     - exception/ErrorResponse.java
     - exception/GlobalExceptionHandler.java
     - exception/EntityNotFoundException.java
     - model/dto/AuditAware.java
     - model/mapper/BaseMapper.java
     - configuration/filter/ApiRequestContext.java
     - configuration/filter/ApiRequestContextHolder.java
     - configuration/filter/AppAuthUserFilter.java

2. Create a root settings.gradle that includes all service modules.

3. In each service's build.gradle, add: implementation project(':internet-banking-common')

4. Remove the duplicated classes from each service.

5. Ensure all services compile and tests pass.

Open a PR.
```

---

### 2.5 Add Custom Health Indicators and Metrics

**Gap Ref:** 6.2, 6.3 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add custom health indicators and Prometheus metrics to all business services.

1. Add micrometer-registry-prometheus dependency to build.gradle for all business services.

2. Configure actuator endpoints in each service's application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
     endpoint:
       health:
         show-details: always
     health:
       circuitbreakers:
         enabled: true

3. Add custom health indicators:
   - core-banking-service: DatabaseHealthIndicator (verify MySQL connectivity)
   - internet-banking-user-service: KeycloakHealthIndicator (verify Keycloak is reachable)
   - All Feign-consuming services: CoreBankingHealthIndicator (verify core-banking-service is registered in Eureka)

4. Add custom metrics counters:
   - fund_transfers_total (tagged by status: success/failed)
   - utility_payments_total (tagged by status: success/failed)
   - user_registrations_total

Open a PR.
```

---

### 2.6 Add Rate Limiting to API Gateway

**Gap Ref:** 4.4 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's built-in RequestRateLimiter filter.

1. Add spring-boot-starter-data-redis-reactive dependency to the API gateway's build.gradle.

2. Add a Redis service to docker-compose.yml.

3. Configure rate limiting in the gateway routes (via Spring Cloud Config or local application.yml):
   - Default: 10 requests/second per user
   - Fund transfer endpoint: 5 requests/second per user
   - Utility payment endpoint: 5 requests/second per user

4. Use the principal name (from OAuth2 token) as the rate limit key.

5. Return HTTP 429 Too Many Requests with a Retry-After header when the limit is exceeded.

Open a PR.
```

---

## Phase 3: Polish (Medium-Low Severity / Various Effort)

These items improve code quality, developer experience, and long-term maintainability.

### 3.1 Standardize Package Structure

**Gap Ref:** 1.2 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Standardize the package structure across all microservices to follow this convention:

com.javatodev.finance/
  controller/
  service/
  service/rest/       (Feign clients)
  repository/
  model/
    entity/
    dto/
    dto/request/
    dto/response/
    mapper/
  configuration/
  exception/

Move classes that are in non-standard locations:
- internet-banking-user-service: move model.repository -> repository, model.rest.response -> model.dto.response
- internet-banking-utility-payment-service: move model.rest.request -> model.dto.request, model.rest.response -> model.dto.response

Update all imports. Ensure all services compile and tests pass.
Open a PR.
```

---

### 3.2 Fix OpenAPI Documentation

**Gap Ref:** 5.4 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Fix and complete OpenAPI documentation across all business services.

1. Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui in build.gradle for: core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service. (These are WebMVC services, not WebFlux.)

2. Add @Schema annotations to all DTOs with descriptions for each field.

3. Add @ApiResponse annotations to all controller methods for success and error responses.

4. Add an OpenApiConfig class in each service with @OpenAPIDefinition providing title, description, version, and security scheme (Bearer token).

5. Verify Swagger UI is accessible at /swagger-ui.html for each service.

Open a PR.
```

---

### 3.3 Add Integration Tests

**Gap Ref:** 3.2 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add Spring Boot integration tests for core-banking-service using @SpringBootTest with H2 in-memory database (already in test dependencies).

Create:
1. AccountControllerIntegrationTest -- use @SpringBootTest(webEnvironment = RANDOM_PORT) with TestRestTemplate to test:
   - GET /api/v1/account/bank-account/{number} returns 200 with valid data
   - GET /api/v1/account/bank-account/{number} returns 404 for non-existent account

2. TransactionControllerIntegrationTest -- test:
   - POST /api/v1/transaction/fund-transfer with valid data returns 200
   - POST /api/v1/transaction/fund-transfer with insufficient balance returns 400

3. Use Flyway to set up test data or create a data.sql / @Sql annotation for test data.

4. Add test configuration in src/test/resources/application.yml with H2 datasource.

Open a PR.
```

---

### 3.4 Add Test Coverage Reporting

**Gap Ref:** 3.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add JaCoCo test coverage reporting to all services.

1. Add the jacoco plugin to each service's build.gradle:
   plugins {
     id 'jacoco'
   }

2. Configure JaCoCo:
   jacocoTestReport {
     reports {
       xml.required = true
       html.required = true
     }
   }

   test {
     finalizedBy jacocoTestReport
   }

3. Optionally add coverage thresholds:
   jacocoTestCoverageVerification {
     violationRules {
       rule {
         limit {
           minimum = 0.5
         }
       }
     }
   }

Open a PR.
```

---

### 3.5 Fix Keycloak Singleton to Spring Bean

**Gap Ref:** 1.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Refactor KeycloakProperties in internet-banking-user-service to use a proper Spring @Bean instead of a manual static singleton.

1. Create a KeycloakConfig class with a @Bean method that returns a Keycloak instance built from the @Value-injected properties.

2. Remove the static keycloakInstance field and getInstance() method from KeycloakProperties.

3. Update KeycloakManager to inject the Keycloak bean directly instead of calling keycloakProperties.getInstance().

4. Ensure the bean is scoped as singleton (default Spring scope).

Open a PR.
```

---

### 3.6 Standardize POST Response Codes

**Gap Ref:** 5.6 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Update all POST endpoints that create resources to return HTTP 201 Created instead of 200 OK.

Affected endpoints:
- POST /api/v1/bank-users/register -> return ResponseEntity.status(HttpStatus.CREATED).body(...)
- POST /api/v1/transfer -> return 201
- POST /api/v1/utility-payment -> return 201

Do not change POST endpoints that are operations/actions (like fund-transfer in core-banking which processes a transaction).

Open a PR.
```

---

### 3.7 Add Filtering and Sorting to List Endpoints

**Gap Ref:** 5.3 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add filtering support to paginated list endpoints.

1. internet-banking-fund-transfer-service GET /api/v1/transfer:
   - Add optional query params: status, fromAccount, toAccount, dateFrom, dateTo
   - Use Spring Data JPA Specifications or @Query with optional params

2. internet-banking-utility-payment-service GET /api/v1/utility-payment:
   - Add optional query params: status, account, providerId, dateFrom, dateTo

3. core-banking-service GET /api/v1/user:
   - Add optional query params: email, firstName, lastName

4. Ensure Pageable already supports sort parameter (it does by default with Spring Data).

Open a PR.
```

---

### 3.8 Add Contract Tests Between Services

**Gap Ref:** 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between Feign consumers and the core-banking-service provider.

1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider side):
   - Define contracts in src/test/resources/contracts/ for:
     - GET /api/v1/account/bank-account/{number}
     - GET /api/v1/user/{identification}
     - POST /api/v1/transaction/fund-transfer
     - POST /api/v1/transaction/util-payment

2. Add spring-cloud-starter-contract-stub-runner to consumer services (fund-transfer, utility-payment, user):
   - Write consumer-side contract tests that verify Feign clients against the stubs

3. Configure the contract tests to run as part of the normal test suite.

Open a PR.
```

---

## Summary

| Phase | Items | Focus |
|---|---|---|
| **Phase 1** (Quick Wins) | 8 items | Fix critical bugs, security holes, and error handling |
| **Phase 2** (Important) | 6 items | Add resilience, testing, shared library, observability |
| **Phase 3** (Polish) | 8 items | Standardize structure, improve docs, add advanced testing |

### Recommended Execution Order

1. **7.6** Fix balance calculation bug (data integrity -- highest priority)
2. **4.3** Stop logging passwords
3. **2.1** Fix exception leaking
4. **4.2** Add input validation
5. **2.3** Add Feign error decoders
6. **7.2/7.3** Add retry + timeout config
7. **5.2** Fix pagination metadata
8. **4.1** Secure Docker Compose credentials
9. **7.1** Add circuit breakers
10. **3.1** Add unit tests to remaining services
11. **7.5** Add idempotency protection
12. **1.1** Extract shared library
13. Remaining Phase 2 and Phase 3 items in listed order
