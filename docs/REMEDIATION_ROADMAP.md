# Internet Banking Microservices — Remediation Roadmap

<!-- Phased remediation plan prioritized by severity and effort. -->
<!-- Each item includes a sample Devin prompt that can be executed directly. -->

## Table of Contents

- [Phase 1: Quick Wins (High Severity, Low Effort)](#phase-1-quick-wins)
- [Phase 2: Important (High Severity, Medium Effort)](#phase-2-important)
- [Phase 3: Polish (Lower Severity, Various Effort)](#phase-3-polish)
- [Prioritization Matrix](#prioritization-matrix)

---

## Phase 1: Quick Wins

<!-- High-severity issues that can be fixed with small effort. Target: 1-2 weeks. -->
<!-- These items address critical bugs, security holes, and correctness issues. -->

### 1.1 Fix Double-Deduction Bug in Balance Calculations (GAP-37)

**Severity: Critical** | **Effort: Small** | **Estimated Time: 1-2 hours**

Fix the arithmetic bug in `TransactionService.internalFundTransfer()` and `utilPayment()` where `availableBalance` is double-deducted because it subtracts from the already-reduced `actualBalance`.

**Devin Prompt:**
```
Fix the double-deduction bug in core-banking-service TransactionService. In internalFundTransfer(), 
lines 90-91 set availableBalance by subtracting amount from the already-debited actualBalance, 
causing a double deduction. The same pattern on lines 99-100 causes double addition on the credit 
side. The same bug exists in utilPayment() at lines 63-64. Fix all three locations so that 
availableBalance is set equal to actualBalance after the balance adjustment (i.e., 
setAvailableBalance(getActualBalance()) with no additional subtraction). Update the existing unit 
tests in TransactionServiceTest to verify correct balance calculations. Open a PR with the fix.
```

### 1.2 Add Input Validation to All Request DTOs (GAP-13)

**Severity: Critical** | **Effort: Small** | **Estimated Time: 2-3 hours**

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to all controller method parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across all services:

1. core-banking-service: FundTransferRequest (fromAccount @NotBlank, toAccount @NotBlank, 
   amount @NotNull @Positive), UtilityPaymentRequest (providerId @NotBlank, amount @NotNull 
   @Positive, referenceNumber @NotBlank, account @NotBlank)
2. internet-banking-fund-transfer-service: FundTransferRequest (fromAccount @NotBlank, 
   toAccount @NotBlank, amount @NotNull @Positive)
3. internet-banking-utility-payment-service: UtilityPaymentRequest (providerId @NotNull, 
   amount @NotNull @Positive, referenceNumber @NotBlank, account @NotBlank)
4. internet-banking-user-service: User register request (email @NotBlank @Email, 
   identification @NotBlank, password @NotBlank @Size(min=8))

Add @Valid to all @RequestBody parameters in all controllers. Add a MethodArgumentNotValidException 
handler in each GlobalExceptionHandler that returns 422 with field-level error details. 
Add spring-boot-starter-validation dependency to each build.gradle if not already present. 
Open a PR with the changes.
```

### 1.3 Fix HTTP Status Codes in Error Handling (GAP-05)

**Severity: Critical** | **Effort: Small** | **Estimated Time: 2-3 hours**

Update all `GlobalExceptionHandler` classes to return appropriate HTTP status codes instead of always returning 400.

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all four services (core-banking-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service, 
internet-banking-user-service) to return correct HTTP status codes:

1. EntityNotFoundException -> 404 Not Found
2. InsufficientFundsException -> 422 Unprocessable Entity
3. UserAlreadyRegisteredException -> 409 Conflict
4. InvalidEmailException -> 422 Unprocessable Entity
5. InvalidBankingUserException -> 404 Not Found
6. MethodArgumentNotValidException -> 422 with field errors
7. FeignException -> 502 Bad Gateway
8. Generic Exception -> 500 Internal Server Error (do NOT expose exception details to client)

All error responses must use the structured ErrorResponse format with code and message fields. 
Remove the string concatenation of exception objects in the generic handler. Open a PR.
```

### 1.4 Move Hardcoded Credentials to Environment Variables (GAP-15)

**Severity: Critical** | **Effort: Small** | **Estimated Time: 1-2 hours**

Replace all hardcoded credentials in docker-compose files and SQL scripts with environment variable references.

**Devin Prompt:**
```
Remove all hardcoded credentials from docker-compose/docker-compose.yml, 
docker-compose/docker-compose-support-apps.yml, docker-compose/mysql/Dockerfile, 
and docker-compose/mysql/privileges.sql. Replace them with environment variable references 
(e.g., ${MYSQL_ROOT_PASSWORD}, ${MYSQL_APP_PASSWORD}, ${KC_DB_PASSWORD}, 
${KEYCLOAK_ADMIN_PASSWORD}). Create a docker-compose/.env.example file with placeholder 
values documenting all required variables. Update the MySQL Dockerfile to use ARG/ENV 
pattern. Update privileges.sql to use a placeholder that gets substituted at container 
startup. Open a PR.
```

### 1.5 Add Feign Error Decoder to Fund-Transfer and Utility-Payment (GAP-08)

**Severity: High** | **Effort: Small** | **Estimated Time: 1-2 hours**

Port the `CustomFeignErrorDecoder` from user-service to fund-transfer and utility-payment services.

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and 
internet-banking-utility-payment-service. Port the implementation from 
internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/feign/CustomFeignErrorDecoder.java. 
Register it in each service's CustomFeignClientConfiguration. Ensure the decoder maps:
- 400 -> SimpleBankingGlobalException (parsed from response body)
- 401 -> appropriate auth exception
- 404 -> EntityNotFoundException
- 5xx -> ServiceUnavailableException (new exception class to create)
Open a PR.
```

### 1.6 Fix Structured Error Response for Generic Exceptions (GAP-06)

**Severity: High** | **Effort: Small** | **Estimated Time: 1 hour**

Ensure all exception handlers return the structured `ErrorResponse` object and never leak internal exception details.

**Devin Prompt:**
```
Update the generic Exception handler in all four GlobalExceptionHandler classes to return 
a structured ErrorResponse instead of a plain string. Replace:
  .body("Exception occur inside API " + e)
with:
  .status(HttpStatus.INTERNAL_SERVER_ERROR)
  .body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred").build())

This prevents leaking internal exception details (class names, stack traces) to API clients. 
Open a PR.
```

### 1.7 Remove Sensitive Data from Logs (GAP-27)

**Severity: High** | **Effort: Small** | **Estimated Time: 1-2 hours**

Remove or mask sensitive data in log statements across all services.

**Devin Prompt:**
```
Audit all log statements across all services and remove or mask sensitive data:

1. internet-banking-user-service UserController: "Creating user with {}" logs the full User 
   object including password. Change to log only the email/identification.
2. internet-banking-user-service UserService: Remove password from any logged objects.
3. All services: Ensure X-Auth-Id header values are not logged at INFO level (move to DEBUG).
4. All services: Ensure request DTOs with financial data (account numbers, amounts) use 
   masked formats in logs (e.g., mask account numbers to show only last 4 digits).

Open a PR.
```

### 1.8 Fix Password Exposure in User Response DTO (GAP-19)

**Severity: High** | **Effort: Small** | **Estimated Time: 1 hour**

Prevent the password field from being included in API responses.

**Devin Prompt:**
```
In internet-banking-user-service, fix the User DTO to prevent password from appearing in 
API responses. Add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password 
field in com.javatodev.finance.model.dto.User. Alternatively, create separate 
UserRegistrationRequest and UserResponse DTOs to properly separate concerns. Open a PR.
```

### 1.9 Fix OpenAPI Dependency — WebFlux to WebMVC (GAP-21)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 30 minutes**

Replace the incorrect WebFlux OpenAPI dependency with the correct WebMVC one in all MVC services.

**Devin Prompt:**
```
In the build.gradle files of core-banking-service, internet-banking-fund-transfer-service, 
internet-banking-utility-payment-service, and internet-banking-user-service, replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

These are Spring MVC applications, not WebFlux, so the webmvc variant is correct. 
Update to version 2.5.0 (latest stable). Open a PR.
```

### 1.10 Add JaCoCo Test Coverage Reporting (GAP-12)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 1 hour**

Configure JaCoCo in all services to generate coverage reports.

**Devin Prompt:**
```
Add JaCoCo test coverage reporting to all 6 services. In each build.gradle:
1. Apply the jacoco plugin: id 'jacoco'
2. Configure jacocoTestReport to generate HTML and XML reports
3. Configure jacocoTestCoverageVerification with a minimum line coverage of 0% initially 
   (to be increased incrementally as tests are added)
4. Make the check task depend on jacocoTestCoverageVerification

Verify it works by running ./gradlew test jacocoTestReport in core-banking-service 
(which has existing tests). Open a PR.
```

### 1.11 Add Type Parameters to ResponseEntity (GAP-20)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 1 hour**

Add proper generic type parameters to all `ResponseEntity` return types.

**Devin Prompt:**
```
Add type parameters to all ResponseEntity return types in controllers across core-banking-service, 
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For example, 
change ResponseEntity to ResponseEntity<BankAccount> in AccountController.getBankAccount(). 
This improves compile-time type safety and OpenAPI documentation generation. 
The user-service already has correct types. Open a PR.
```

### 1.12 Fix Keycloak Singleton Thread Safety (GAP-17)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 30 minutes**

Make the Keycloak instance initialization thread-safe.

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.getInstance(). 
Replace the non-synchronized lazy singleton with a @Bean method in a @Configuration class 
that returns a singleton-scoped Keycloak instance, or use synchronized/volatile double-checked 
locking. The cleanest approach is to create a KeycloakConfiguration @Configuration class with 
a @Bean method that builds and returns the Keycloak instance. Remove the static field and 
getInstance() method from KeycloakProperties. Open a PR.
```

---

## Phase 2: Important

<!-- High-severity issues that require medium effort. Target: 2-4 weeks. -->
<!-- These items address architectural gaps and cross-cutting concerns. -->

### 2.1 Add Circuit Breakers with Resilience4j (GAP-31)

**Severity: Critical** | **Effort: Medium** | **Estimated Time: 3-4 days**

Add Resilience4j circuit breakers to all Feign clients to prevent cascading failures.

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all three services that call core-banking-service:

1. Add dependencies to build.gradle of fund-transfer, utility-payment, and user-service:
   - spring-cloud-starter-circuitbreaker-resilience4j
   - resilience4j-spring-boot3
   
2. Configure circuit breakers in application.yml for each service:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3
   
3. Add @CircuitBreaker annotations to Feign client calls in each service class 
   (FundTransferService, UtilityPaymentService, UserService) with fallback methods.
   
4. Fallback methods should:
   - For fund-transfer: Save entity with status=FAILED and return error response
   - For utility-payment: Save entity with status=FAILED and return error response
   - For user-service: Throw a ServiceUnavailableException with clear message

5. Add unit tests for circuit breaker behavior. Open a PR.
```

### 2.2 Add Idempotency Keys for Financial Operations (GAP-35)

**Severity: Critical** | **Effort: Medium** | **Estimated Time: 2-3 days**

Implement idempotency keys for fund transfer and utility payment POST endpoints.

**Devin Prompt:**
```
Implement idempotency keys for financial POST operations:

1. Add an "Idempotency-Key" request header to POST /api/v1/transfer and 
   POST /api/v1/utility-payment endpoints.
   
2. Create an idempotency_key table in each service's database schema:
   - key (VARCHAR, PRIMARY KEY)
   - response_body (TEXT)
   - status_code (INT)
   - created_at (TIMESTAMP)
   
3. Implement an IdempotencyFilter or interceptor that:
   a. Checks if the idempotency key exists in the database
   b. If yes, return the cached response
   c. If no, proceed with the request and cache the response
   d. If the key header is missing, reject with 400
   
4. Add Flyway migrations for the new tables.
5. Add unit tests verifying duplicate requests return the same response.
Open a PR.
```

### 2.3 Add Authentication to Downstream Services (GAP-14)

**Severity: Critical** | **Effort: Medium** | **Estimated Time: 3-4 days**

Add JWT validation to all downstream services instead of relying solely on the gateway.

**Devin Prompt:**
```
Add JWT-based authentication to all downstream services (core-banking-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service, 
internet-banking-user-service):

1. Add spring-boot-starter-oauth2-resource-server and spring-boot-starter-security 
   dependencies to each service's build.gradle.
   
2. Create a SecurityConfiguration class in each service that:
   - Configures JWT validation using the Keycloak JWK Set URI
   - Permits actuator endpoints without auth
   - Requires authentication for all other endpoints
   
3. Update Feign client configurations to propagate the JWT token from incoming requests 
   to outgoing Feign calls using a RequestInterceptor that reads the Authorization header.
   
4. Remove the X-Auth-Id header approach and instead extract the user identity from the 
   JWT token's claims in each service.
   
5. Update the gateway's GlobalFilter to still forward the JWT token (Authorization header).
6. Add integration tests verifying that unauthenticated requests are rejected with 401.
Open a PR.
```

### 2.4 Implement Transaction Compensation / Saga Pattern (GAP-36)

**Severity: Critical** | **Effort: Large** | **Estimated Time: 1-2 weeks**

Implement compensation logic for failed distributed transactions.

**Devin Prompt:**
```
Implement a saga-based compensation pattern for fund transfers and utility payments:

1. Add FAILED status to TransactionStatus enum in both fund-transfer and utility-payment services.

2. In FundTransferService.fundTransfer():
   - Wrap the Feign call in try-catch
   - On FeignException: update entity status to FAILED, save error details, return error response
   - On success: update to SUCCESS as before
   
3. In UtilityPaymentService.utilPayment():
   - Same try-catch pattern with FAILED status
   
4. Create a FailedTransactionRecoveryJob @Scheduled component in each service that:
   - Runs every 5 minutes
   - Finds entities in PENDING/PROCESSING status older than 10 minutes
   - Retries the Feign call up to 3 times
   - After 3 retries, marks as FAILED and logs an alert
   
5. Add a GET endpoint to query failed transactions for manual reconciliation.
6. Add unit tests for the compensation flow.
Open a PR.
```

### 2.5 Create Shared Common Library (GAP-01)

**Severity: High** | **Effort: Medium** | **Estimated Time: 3-4 days**

Extract duplicated code into a shared library module.

**Devin Prompt:**
```
Create a shared common library module called "internet-banking-common":

1. Create a new directory internet-banking-common/ with its own build.gradle that 
   produces a JAR (no Spring Boot plugin, just java-library plugin).
   
2. Move these duplicated classes into the common library:
   - BaseMapper<E, D>
   - AuditAware (MappedSuperclass)
   - AuditConfig / AuditorAwareConfig
   - GlobalExceptionHandler (base class)
   - ErrorResponse
   - SimpleBankingGlobalException
   - AppAuthUserFilter
   - ApiRequestContext / ApiRequestContextHolder
   
3. Create a root settings.gradle that includes all services and the common module.
   
4. Update each service's build.gradle to depend on the common library:
   implementation project(':internet-banking-common')
   
5. Remove the duplicated classes from each service.
6. Ensure all services still compile and tests pass.
Open a PR.
```

### 2.6 Add Feign Timeout and Retry Configuration (GAP-32, GAP-33)

**Severity: High** | **Effort: Small** | **Estimated Time: 1-2 hours**

Configure explicit timeouts and retry policies for all Feign clients.

**Devin Prompt:**
```
Add explicit timeout and retry configuration for all Feign clients:

1. In each service that uses Feign (fund-transfer, utility-payment, user-service), 
   add to application.yml:
   
   spring:
     cloud:
       openfeign:
         client:
           config:
             core-banking-service:
               connectTimeout: 3000
               readTimeout: 5000
               loggerLevel: BASIC
   
2. Add Retryer configuration in each CustomFeignClientConfiguration:
   - maxAttempts: 3
   - period: 1000ms (initial interval)
   - maxPeriod: 3000ms
   - Only retry on 5xx and IOException (not on 4xx)
   
3. Add unit tests verifying timeout behavior.
Open a PR.
```

### 2.7 Add Structured Logging with JSON Format (GAP-26)

**Severity: Medium** | **Effort: Medium** | **Estimated Time: 2-3 days**

Implement structured JSON logging across all services.

**Devin Prompt:**
```
Add structured JSON logging to all 6 services:

1. Add logstash-logback-encoder dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
   
2. Create a logback-spring.xml in each service's src/main/resources/ that:
   - Uses LogstashEncoder for JSON output in docker profile
   - Uses standard PatternLayout for dev profile (human-readable)
   - Includes trace_id and span_id from Micrometer in every log line
   - Includes service name in every log line
   
3. Add MDC context for request-scoped fields (userId, requestId) in the 
   AppAuthUserFilter.
   
4. Ensure sensitive data (passwords, full account numbers) is never logged.
Open a PR.
```

### 2.8 Add Pagination Metadata to List Responses (GAP-23)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 2-3 hours**

Return `Page<T>` or a wrapper with pagination metadata instead of `List<T>`.

**Devin Prompt:**
```
Fix all paginated endpoints to return pagination metadata:

1. Create a generic PagedResponse<T> wrapper in the common library (or each service) 
   containing: content (List<T>), pageNumber, pageSize, totalElements, totalPages, 
   isLast, isFirst.
   
2. Update these endpoints to return PagedResponse instead of List:
   - core-banking-service: GET /api/v1/user
   - internet-banking-user-service: GET /api/v1/bank-users
   - internet-banking-fund-transfer-service: GET /api/v1/transfer
   - internet-banking-utility-payment-service: GET /api/v1/utility-payment
   
3. Update service methods to pass through the Spring Data Page object's metadata.
4. Update any existing tests. Open a PR.
```

### 2.9 Add Dependency Vulnerability Scanning (GAP-18)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 1-2 hours**

Add OWASP Dependency Check to the Gradle build.

**Devin Prompt:**
```
Add OWASP Dependency Check plugin to all service build.gradle files:

1. Add the plugin: id 'org.owasp.dependencycheck' version '9.1.0'
2. Configure to fail the build on CVSS >= 7.0 (HIGH severity)
3. Configure to generate HTML and JSON reports
4. Add a root build.gradle or script that can run dependency check across all services
5. Run the check and document any findings that need immediate attention
Open a PR.
```

### 2.10 Restrict Database Privileges Per Service (GAP-16)

**Severity: Medium** | **Effort: Small** | **Estimated Time: 1 hour**

Create dedicated database users with least-privilege access for each service.

**Devin Prompt:**
```
Update docker-compose/mysql/privileges.sql to create separate database users for 
each service with minimal privileges:

1. core_banking_user: SELECT, INSERT, UPDATE, DELETE on banking_core_service only
2. fund_transfer_user: SELECT, INSERT, UPDATE, DELETE on banking_core_fund_transfer_service only
3. utility_payment_user: SELECT, INSERT, UPDATE, DELETE on banking_core_utility_payment_service only
4. user_service_user: SELECT, INSERT, UPDATE, DELETE on banking_core_user_service only
5. Keep the javatodev_development user for Flyway migrations only (with CREATE, ALTER, DROP)

Update bootstrap-docker.yml configurations for each service to use their dedicated user.
Open a PR.
```

---

## Phase 3: Polish

<!-- Lower-severity issues and improvements for long-term quality. Target: 4-8 weeks. -->

### 3.1 Add Comprehensive Unit Tests to All Services (GAP-09)

**Severity: Critical** | **Effort: Large** | **Estimated Time: 1-2 weeks**

Write unit tests for all service and controller classes across all 6 services.

**Devin Prompt:**
```
Add comprehensive unit tests to all services that currently have no tests:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, Feign failure handling, 
     readAllTransfers pagination
   - FundTransferControllerTest: test POST /api/v1/transfer, GET /api/v1/transfer 
     with MockMvc
   
2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success, Feign failure, readPayments
   - UtilityPaymentControllerTest: test POST/GET endpoints with MockMvc
   
3. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, NIC not found, 
     email mismatch), readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: mock Keycloak admin client
   - UserControllerTest: test all CRUD endpoints with MockMvc
   
4. internet-banking-api-gateway:
   - SecurityConfigurationTest: test public vs authenticated routes
   - GatewayConfigurationTest: test X-Auth-Id header propagation

Target minimum 80% line coverage on service and controller classes.
Open a PR.
```

### 3.2 Add Integration Tests with Testcontainers (GAP-10)

**Severity: High** | **Effort: Large** | **Estimated Time: 1-2 weeks**

Add integration tests using Testcontainers for MySQL.

**Devin Prompt:**
```
Add integration tests with Testcontainers to core-banking-service and 
internet-banking-fund-transfer-service:

1. Add Testcontainers dependencies to build.gradle:
   - testImplementation 'org.testcontainers:testcontainers'
   - testImplementation 'org.testcontainers:mysql'
   - testImplementation 'org.testcontainers:junit-jupiter'
   
2. core-banking-service integration tests:
   - Full fund transfer flow (create accounts, transfer, verify balances)
   - Full utility payment flow
   - Flyway migration verification
   - API-level tests with @SpringBootTest and TestRestTemplate
   
3. internet-banking-fund-transfer-service integration tests:
   - @SpringBootTest with WireMock for core-banking-service
   - Test full transfer flow with mocked core-banking responses
   - Test Feign error handling with WireMock error responses
   
4. Use @DynamicPropertySource to wire Testcontainers MySQL connection.
Open a PR.
```

### 3.3 Add Contract Tests Between Services (GAP-11)

**Severity: High** | **Effort: Large** | **Estimated Time: 1 week**

Implement consumer-driven contract tests using Spring Cloud Contract.

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. Add spring-cloud-starter-contract-verifier to core-banking-service (producer).
2. Write contract DSL files for all core-banking-service endpoints that are called 
   by other services:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}
   
3. Generate contract stubs JAR from core-banking-service.

4. Add spring-cloud-starter-contract-stub-runner to fund-transfer-service, 
   utility-payment-service, and user-service as test dependencies.
   
5. Write consumer-side contract tests that verify Feign clients work correctly 
   against the contract stubs.
   
Open a PR.
```

### 3.4 Add Fallback Behavior for Read Operations (GAP-34)

**Severity: Medium** | **Effort: Medium** | **Estimated Time: 2-3 days**

Implement graceful degradation for read operations when downstream services are unavailable.

**Devin Prompt:**
```
Add fallback behavior for read operations:

1. In internet-banking-user-service readUsers():
   - If Keycloak is unavailable, return user list from local DB without Keycloak 
     enrichment (email will be missing)
   - Log a warning when fallback is triggered
   
2. In internet-banking-fund-transfer-service readAllTransfers():
   - This operation is local-only (no Feign call), so no fallback needed
   
3. In internet-banking-utility-payment-service readPayments():
   - This operation is local-only, so no fallback needed
   
4. Add Spring Cache (@Cacheable) for frequently accessed, rarely changing data:
   - Utility account lookups in core-banking-service
   - User lookups by NIC in core-banking-service
   
5. Configure cache eviction policies (TTL: 5 minutes for accounts, 1 hour for users).
Open a PR.
```

### 3.5 Standardize Package Structure (GAP-03)

**Severity: Low** | **Effort: Small** | **Estimated Time: 1-2 hours**

Align package naming conventions across all services.

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:
  com.javatodev.finance
    ├── configuration/
    │   ├── audit/
    │   ├── feign/
    │   ├── filter/
    │   └── security/
    ├── controller/
    ├── exception/
    ├── model/
    │   ├── dto/
    │   │   ├── request/
    │   │   └── response/
    │   ├── entity/
    │   └── mapper/
    ├── repository/
    └── service/
        └── rest/

Move classes that don't follow this convention:
- utility-payment: repository/ -> model/repository/
- fund-transfer feign: service/rest/client/ -> service/rest/
- Rename BankingCoreFeignClient -> BankingCoreRestClient (or vice versa) for consistency
Open a PR.
```

### 3.6 Register Mappers as Spring Beans (GAP-04)

**Severity: Low** | **Effort: Small** | **Estimated Time: 1 hour**

Convert manual mapper instantiation to Spring-managed beans.

**Devin Prompt:**
```
Convert all mapper classes to Spring beans:

1. Add @Component annotation to all mapper implementations (BankAccountMapper, 
   UserMapper, FundTransferMapper, UtilityPaymentMapper, UtilityAccountMapper).
   
2. Replace "private XMapper mapper = new XMapper()" with constructor-injected beans 
   in all service classes.
   
3. Alternatively, consider replacing the custom BaseMapper pattern with MapStruct 
   for compile-time type-safe mapping. If using MapStruct, add the mapstruct and 
   mapstruct-processor dependencies and convert all mappers to MapStruct interfaces.
   
Open a PR.
```

### 3.7 Add Custom Health Indicators (GAP-28)

**Severity: Low** | **Effort: Small** | **Estimated Time: 2-3 hours**

Add custom health indicators for critical dependencies.

**Devin Prompt:**
```
Add custom health indicators to relevant services:

1. internet-banking-user-service:
   - KeycloakHealthIndicator: check Keycloak connectivity via admin client
   
2. internet-banking-fund-transfer-service and utility-payment-service:
   - CoreBankingHealthIndicator: check core-banking-service via Feign 
     (GET /actuator/health)
   
3. core-banking-service:
   - DatabaseHealthIndicator is auto-configured, verify it's enabled
   
4. All services:
   - Configure actuator to expose health details: 
     management.endpoint.health.show-details=when-authorized

Open a PR.
```

### 3.8 Add Business Metrics (GAP-29)

**Severity: Low** | **Effort: Medium** | **Estimated Time: 2-3 days**

Add custom Micrometer metrics for business operations.

**Devin Prompt:**
```
Add custom Micrometer metrics to track business operations:

1. core-banking-service:
   - Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT, 
     status=SUCCESS|FAILED)
   - Timer: banking.transactions.duration
   - Gauge: banking.accounts.active.count
   
2. internet-banking-fund-transfer-service:
   - Counter: fund_transfer.requests.total (tags: status=SUCCESS|PENDING|FAILED)
   - Timer: fund_transfer.processing.duration
   
3. internet-banking-utility-payment-service:
   - Counter: utility_payment.requests.total (tags: status=SUCCESS|PROCESSING|FAILED)
   - Timer: utility_payment.processing.duration
   
4. internet-banking-user-service:
   - Counter: user.registrations.total (tags: status=SUCCESS|FAILED)

Use MeterRegistry injection and @Timed annotations where appropriate.
Open a PR.
```

### 3.9 Implement API Versioning Strategy (GAP-22)

**Severity: Medium** | **Effort: Medium** | **Estimated Time: 2-3 days**

Document and implement a formal API versioning strategy.

**Devin Prompt:**
```
Implement a URL-based API versioning strategy:

1. Document the versioning policy in docs/API_VERSIONING.md:
   - URL path versioning: /api/v1/, /api/v2/, etc.
   - Deprecation policy: v(N-1) supported for 6 months after v(N) release
   - Breaking change definition and migration guide template
   
2. Add an ApiVersion annotation and interceptor that can extract the version 
   from the URL path.
   
3. Add response headers: X-API-Version, X-API-Deprecated (when applicable).

4. Ensure gateway routes support version-aware routing.
Open a PR.
```

### 3.10 Add Filtering Parameters to List Endpoints (GAP-24)

**Severity: Low** | **Effort: Medium** | **Estimated Time: 2-3 days**

Add query parameter filtering to list endpoints.

**Devin Prompt:**
```
Add filtering parameters to paginated list endpoints:

1. GET /api/v1/transfer:
   - ?status=PENDING|SUCCESS|FAILED
   - ?fromAccount=xxx
   - ?dateFrom=yyyy-MM-dd&dateTo=yyyy-MM-dd
   
2. GET /api/v1/utility-payment:
   - ?status=PROCESSING|SUCCESS|FAILED
   - ?providerId=xxx
   - ?dateFrom=yyyy-MM-dd&dateTo=yyyy-MM-dd
   
3. GET /api/v1/bank-users:
   - ?status=PENDING|APPROVED
   
4. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering.
5. Add OpenAPI documentation for all filter parameters.
Open a PR.
```

### 3.11 Add Zipkin Fallback Configuration (GAP-30)

**Severity: Low** | **Effort: Small** | **Estimated Time: 30 minutes**

Add tracing fallback configuration directly in each service's application.yml.

**Devin Prompt:**
```
Add Zipkin/tracing fallback configuration to each service's application.yml so 
tracing works even if the config server is temporarily unavailable:

management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

Also add a bootstrap-docker.yml override pointing to the Docker Zipkin IP (172.25.0.12).
This ensures tracing configuration is available locally as a fallback.
Open a PR.
```

---

## Prioritization Matrix

<!-- Visual matrix mapping severity vs effort for quick reference. -->

```
                      EFFORT
                Small        Medium         Large
           ┌────────────┬─────────────┬─────────────┐
 Critical  │ GAP-37 Bug │ GAP-31 CB   │ GAP-36 Saga │
           │ GAP-13 Val │ GAP-35 Idemp│ GAP-09 Tests│
           │ GAP-05 HTTP│ GAP-14 Auth │             │
           │ GAP-15 Cred│             │             │
 SEVERITY  ├────────────┼─────────────┼─────────────┤
   High    │ GAP-06 Err │ GAP-01 Lib  │ GAP-10 IntT │
           │ GAP-08 Fgn │ GAP-32+33 TO│ GAP-11 CnTr │
           │ GAP-27 Logs│             │             │
           │ GAP-19 Pwd │             │             │
           ├────────────┼─────────────┼─────────────┤
  Medium   │ GAP-12 Cov │ GAP-02 Root │ GAP-26 Log  │
           │ GAP-20 Type│ GAP-22 Ver  │             │
           │ GAP-21 API │ GAP-34 Fall │             │
           │ GAP-16 Priv│ GAP-23 Page │             │
           │ GAP-17 KC  │ GAP-18 Vuln │             │
           │ GAP-07 Exc │             │             │
           ├────────────┼─────────────┼─────────────┤
   Low     │ GAP-03 Pkg │ GAP-24 Filt │             │
           │ GAP-04 Map │ GAP-29 Met  │             │
           │ GAP-25 Name│             │             │
           │ GAP-28 Hlth│             │             │
           │ GAP-30 Zip │             │             │
           └────────────┴─────────────┴─────────────┘

Legend: CB=Circuit Breaker, Idemp=Idempotency, Val=Validation, 
Cred=Credentials, Err=Error Response, Fgn=Feign Decoder, 
TO=Timeouts, Lib=Shared Library, IntT=Integration Tests, 
CnTr=Contract Tests, Cov=Coverage, Ver=Versioning, 
Fall=Fallback, Page=Pagination, Vuln=Vulnerability Scan,
KC=Keycloak, Exc=Exceptions, Pkg=Package, Map=Mappers, 
Name=Naming, Hlth=Health, Zip=Zipkin, Filt=Filtering, 
Met=Metrics, Log=Structured Logging
```

### Execution Priority Order

| Priority | Phase | Items | Rationale |
|---|---|---|---|
| 1 | Phase 1 | GAP-37 (Bug fix) | Active data corruption in production |
| 2 | Phase 1 | GAP-13 (Validation) | Prevents NPEs and invalid financial operations |
| 3 | Phase 1 | GAP-05, GAP-06 (Error handling) | Correct HTTP semantics, prevent info leakage |
| 4 | Phase 1 | GAP-15 (Credentials) | Security compliance requirement |
| 5 | Phase 1 | GAP-08, GAP-19, GAP-27 (Error/Security) | Quick security hardening |
| 6 | Phase 2 | GAP-31 (Circuit breakers) | Prevent cascading failures |
| 7 | Phase 2 | GAP-35 (Idempotency) | Prevent duplicate financial transactions |
| 8 | Phase 2 | GAP-14 (Downstream auth) | Defense in depth |
| 9 | Phase 2 | GAP-36 (Saga/compensation) | Transaction reliability |
| 10 | Phase 2 | GAP-01, GAP-32, GAP-33 | Code quality and resilience |
| 11 | Phase 3 | GAP-09, GAP-10, GAP-11 | Test coverage foundation |
| 12 | Phase 3 | All remaining | Polish and completeness |
