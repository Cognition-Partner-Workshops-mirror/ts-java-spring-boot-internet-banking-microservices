# Remediation Roadmap

## Overview

This roadmap organizes the 37 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins**: Critical/High severity + Small effort. Immediate fixes that reduce risk with minimal investment.
- **Phase 2 — Important**: Critical/High severity + Medium effort, or Medium severity items that unlock further improvements.
- **Phase 3 — Polish**: Lower severity, larger effort, or nice-to-have improvements.

Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1 — Quick Wins

> **Goal:** Fix critical bugs, plug security holes, and correct error handling with small, targeted changes.

### 1.1 Fix Double-Deduction Bug in Balance Calculations (GAP-36)

**Severity:** Critical | **Effort:** Small

Fix the `availableBalance` calculation in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()` in core-banking-service. After debiting `actualBalance`, the code incorrectly subtracts the amount again when setting `availableBalance`.

**Devin Prompt:**
```
In core-banking-service, fix the double-deduction bug in TransactionService.java.

In internalFundTransfer(): after setting actualBalance = actualBalance - amount, set availableBalance = actualBalance (not actualBalance - amount again). Apply the same fix in utilPayment(). The corrected pattern should be:

BigDecimal newBalance = entity.getActualBalance().subtract(amount);
entity.setActualBalance(newBalance);
entity.setAvailableBalance(newBalance);

Apply this fix for both the fromBankAccountEntity in internalFundTransfer() and the fromAccount in utilPayment(). Also fix the credit side in internalFundTransfer() for toBankAccountEntity. Add unit tests that verify availableBalance equals actualBalance after each operation.
```

### 1.2 Fix Error Handling — Proper HTTP Status Codes (GAP-05)

**Severity:** Critical | **Effort:** Small

Update `GlobalExceptionHandler` in all 4 services to return appropriate HTTP status codes instead of always returning 400.

**Devin Prompt:**
```
Update the GlobalExceptionHandler in all services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service) to use proper HTTP status codes:

1. EntityNotFoundException -> 404 Not Found
2. InsufficientFundsException -> 422 Unprocessable Entity
3. UserAlreadyRegisteredException -> 409 Conflict
4. InvalidEmailException -> 400 Bad Request
5. InvalidBankingUserException -> 400 Bad Request
6. Generic Exception -> 500 Internal Server Error (log the full exception server-side, return only a generic message to the client)

Make sure the catch-all Exception handler never leaks raw exception details to clients. Return a structured ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred".
```

### 1.3 Stop Leaking Exception Details to Clients (GAP-06)

**Severity:** High | **Effort:** Small

Addressed as part of 1.2 above — the catch-all handler should log the exception and return a sanitized response.

### 1.4 Add Input Validation to All Request DTOs (GAP-13)

**Severity:** Critical | **Effort:** Small

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to all controller `@RequestBody` parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation (jakarta.validation) to all request DTOs across all services:

1. core-banking-service FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
2. core-banking-service UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
3. internet-banking-fund-transfer-service FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
4. internet-banking-fund-transfer-service UtilityPaymentRequest: same as above
5. internet-banking-user-service User (for registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
6. internet-banking-user-service UserUpdateRequest: @NotNull status
7. internet-banking-utility-payment-service UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

Add @Valid annotation to every @RequestBody parameter in all controllers. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details. Add spring-boot-starter-validation dependency to each build.gradle if not already present.
```

### 1.5 Remove Hardcoded Credentials from Source (GAP-15)

**Severity:** Critical | **Effort:** Small

Replace hardcoded passwords with environment variable references in Docker Compose and SQL files.

**Devin Prompt:**
```
Remove all hardcoded credentials from the codebase:

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD}
   - Replace POSTGRES_PASSWORD value with ${POSTGRES_PASSWORD}

2. In docker-compose/mysql/Dockerfile:
   - Remove the ENV MYSQL_ROOT_PASSWORD line (will be set via docker-compose env)

3. In docker-compose/mysql/privileges.sql:
   - This file runs at container init; document that the password should be changed post-deployment

4. Create a docker-compose/.env.example file with all required environment variables and placeholder values

5. Add docker-compose/.env to .gitignore

6. Remove the Keycloak client-secret from internet-banking-user-service/src/test/resources/application.yml and replace with a test-specific value
```

### 1.6 Fix Wrong OpenAPI Dependency (GAP-20)

**Severity:** Medium | **Effort:** Small

Replace the WebFlux OpenAPI dependency with WebMVC in all MVC services.

**Devin Prompt:**
```
In the build.gradle files for core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service:

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (spring-boot-starter-web), not WebFlux. The webflux-ui dependency is incorrect and may cause classpath conflicts. Verify that Swagger UI loads correctly at /swagger-ui.html after the change.
```

### 1.7 Add Feign Error Decoders to Fund Transfer and Utility Payment Services (GAP-08)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service, similar to the one in internet-banking-user-service.

The error decoder should:
1. Read the response body from the Feign response
2. Deserialize it as an ErrorResponse (code + message)
3. Map known error codes to appropriate domain exceptions (e.g., BANKING-CORE-SERVICE-1000 -> EntityNotFoundException, BANKING-CORE-SERVICE-1001 -> InsufficientFundsException)
4. For unknown errors, throw a SimpleBankingGlobalException with the error details
5. Register the decoder in CustomFeignClientConfiguration.java

Also ensure the fund-transfer-service has appropriate exception classes (EntityNotFoundException, InsufficientFundsException) similar to core-banking-service.
```

### 1.8 Add Typed ResponseEntity to Controllers (GAP-19)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update all controller methods across all services to use typed ResponseEntity instead of raw ResponseEntity:

For example, change:
  public ResponseEntity readFundTransfers(Pageable pageable)
To:
  public ResponseEntity<List<FundTransfer>> readFundTransfers(Pageable pageable)

Apply to all endpoints in:
- core-banking-service: AccountController, TransactionController, UserController
- internet-banking-fund-transfer-service: FundTransferController
- internet-banking-user-service: UserController
- internet-banking-utility-payment-service: UtilityPaymentController

This improves type safety and enables accurate OpenAPI schema generation.
```

### 1.9 Fix Sensitive Data Logging (GAP-29)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix sensitive data logging across all services:

1. In FundTransferController.java: Replace log.info("Got fund transfer request from API {}", fundTransferRequest.toString()) with a sanitized version that only logs fromAccount (last 4 digits) and amount
2. In TransactionController.java: Same sanitization for fund transfer and utility payment logging
3. In FundTransferService.java: Fix the string concatenation bug (replace "Sending fund transfer request {}" + request.toString() with proper SLF4J parameterized logging, and sanitize the output)
4. In UtilityPaymentService.java: Sanitize payment request logging
5. In UserService.java: Don't log the full user object (contains password)
6. In AppAuthUserFilter.java (all services): Log "Incoming request from user [REDACTED]" instead of the full auth ID

Create a LogSanitizer utility class in the shared codebase pattern that masks account numbers and PII.
```

### 1.10 Reduce Database Permissions (GAP-16)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Update docker-compose/mysql/privileges.sql to use least-privilege database permissions:

1. Create separate users for each service:
   - core_banking_user with INSERT, UPDATE, DELETE, SELECT on banking_core_service.*
   - fund_transfer_user with INSERT, UPDATE, DELETE, SELECT on banking_core_fund_transfer_service.*
   - user_service_user with INSERT, UPDATE, DELETE, SELECT on banking_core_user_service.*
   - utility_payment_user with INSERT, UPDATE, DELETE, SELECT on banking_core_utility_payment_service.*

2. Keep a migration_user with CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT on banking_core_service.* for Flyway (core-banking only)

3. Remove the overly broad GRANT on *.* for javatodev_development

4. Update the Spring Cloud Config properties (or document which config properties need to change) to use the new per-service database users
```

### 1.11 Add Timeout Configuration for Feign Clients (GAP-32)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add explicit timeout configuration for all Feign clients in internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service.

In each service's application.yml (or via Spring Cloud Config), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
          core-banking-service:
            connectTimeout: 5000
            readTimeout: 10000

Also configure HikariCP connection pool timeouts in the datasource config:
spring:
  datasource:
    hikari:
      connection-timeout: 5000
      maximum-pool-size: 10
```

### 1.12 Add Retry Policies for Read Operations (GAP-31)

**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add Resilience4j retry configuration for Feign read operations in internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service.

1. Add resilience4j-spring-boot3 dependency to each service's build.gradle
2. Configure retry for GET operations only (not POST/write operations to avoid double-processing):

resilience4j:
  retry:
    instances:
      coreBankingRead:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.ConnectException
          - feign.RetryableException

3. Apply @Retry(name = "coreBankingRead") to read methods in Feign clients
4. Do NOT add retry to fund transfer or payment write operations without idempotency keys
```

### 1.13 Fix Inconsistent Error Response Format (GAP-07)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Standardize the error response format across all services:

1. Update ErrorResponse in all services to include additional fields:
   @Getter @Setter @Builder
   public class ErrorResponse {
       private String code;
       private String message;
       private Instant timestamp;
       private String path;
   }

2. Update all GlobalExceptionHandler methods to populate timestamp and path from the request

3. Ensure the catch-all Exception handler also returns an ErrorResponse object (not a plain String)

4. Ensure the MethodArgumentNotValidException handler (from GAP-13 fix) returns the same format with validation details in the message field
```

### 1.14 Add Structured Logging (GAP-25)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add structured JSON logging to all services:

1. Add logstash-logback-encoder dependency to each build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a logback-spring.xml in each service's src/main/resources with:
   - JSON encoder for non-local profiles
   - Console encoder for local/dev profiles
   - Include traceId and spanId from Micrometer in log output

3. Fix the string concatenation logging bug in FundTransferService:
   Change: log.info("Sending fund transfer request {}" + request.toString());
   To: log.info("Sending fund transfer request {}", request.toString());
```

### 1.15 Add Dependency Vulnerability Scanning (GAP-17)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency Check to the Gradle build for all services:

1. Add the plugin to each build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.1.0'
   }

2. Configure the plugin:
   dependencyCheck {
       failBuildOnCVSS = 7
       formats = ['HTML', 'JSON']
   }

3. Create a GitHub Actions workflow (.github/workflows/dependency-check.yml) that runs dependency check weekly on all services

4. Alternatively, add a Dependabot configuration (.github/dependabot.yml) for Gradle dependencies with weekly update schedule
```

---

## Phase 2 — Important

> **Goal:** Add resilience patterns, authentication, testing foundation, and code organization improvements.

### 2.1 Add Circuit Breakers to Feign Clients (GAP-30)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies to fund-transfer, user-service, and utility-payment build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true

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

4. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): Return error response indicating core banking unavailable
   - BankingCoreRestClientFallback (user-service): Return error response
   - BankingCoreRestClientFallback (utility-payment): Return error response

5. Register fallbacks in @FeignClient annotations using fallback or fallbackFactory parameter
```

### 2.2 Add Idempotency Keys for Financial Transactions (GAP-34)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Implement idempotency for fund transfer and utility payment endpoints:

1. In internet-banking-fund-transfer-service:
   - Add an idempotencyKey field to FundTransferRequest
   - Add a unique index on idempotencyKey in FundTransferEntity
   - Before processing, check if a transfer with this key already exists
   - If it exists, return the existing result instead of processing again
   - Add Idempotency-Key header support in the controller

2. In internet-banking-utility-payment-service:
   - Same pattern: add idempotencyKey to UtilityPaymentRequest
   - Add unique index on idempotencyKey in UtilityPaymentEntity
   - Check-before-process logic

3. In core-banking-service:
   - Add a unique constraint on transactionId in banking_core_transaction
   - Accept and propagate idempotency keys from upstream services

4. Document the idempotency contract in OpenAPI annotations
```

### 2.3 Add Authentication to Downstream Services (GAP-14)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add JWT authentication to all downstream services so they don't rely solely on the API Gateway:

Option A (recommended — JWT propagation):
1. Add spring-boot-starter-oauth2-resource-server to core-banking-service, fund-transfer-service, user-service, and utility-payment-service build.gradle

2. Add SecurityConfiguration to each service that validates JWT tokens:
   @Configuration
   @EnableWebSecurity
   public class SecurityConfiguration {
       @Bean
       public SecurityFilterChain filterChain(HttpSecurity http) {
           http.csrf(csrf -> csrf.disable())
               .authorizeHttpRequests(auth -> auth
                   .requestMatchers("/actuator/**").permitAll()
                   .anyRequest().authenticated())
               .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
           return http.build();
       }
   }

3. Configure the JWT issuer-uri in each service's config to point to Keycloak

4. Add a Feign RequestInterceptor to propagate the Bearer token from incoming requests to outgoing Feign calls:
   @Component
   public class FeignAuthInterceptor implements RequestInterceptor {
       @Override
       public void apply(RequestTemplate template) {
           ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
           if (attrs != null) {
               String auth = attrs.getRequest().getHeader("Authorization");
               if (auth != null) template.header("Authorization", auth);
           }
       }
   }
```

### 2.4 Extract Shared Library (GAP-01)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a shared library module (banking-common) to eliminate code duplication:

1. Create a new directory banking-common/ with its own build.gradle:
   - Apply java-library plugin
   - Include shared dependencies: lombok, spring-boot-starter-web, spring-data-jpa

2. Move these duplicated classes into banking-common:
   - model/dto/AuditAware.java
   - model/mapper/BaseMapper.java
   - exception/SimpleBankingGlobalException.java
   - exception/ErrorResponse.java
   - exception/GlobalExceptionHandler.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java
   - configuration/audit/AuditConfig.java
   - configuration/audit/AuditorAwareConfig.java

3. Create a root settings.gradle that includes banking-common and all services

4. Update each service's build.gradle to depend on banking-common:
   implementation project(':banking-common')

5. Remove the duplicated classes from each service
6. Verify all services compile and tests pass
```

### 2.5 Add Pagination Metadata to List Endpoints (GAP-22)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update all paginated endpoints to return pagination metadata:

1. Create a generic PageResponse<T> class in the shared library (or each service):
   @Data
   public class PageResponse<T> {
       private List<T> content;
       private int currentPage;
       private int pageSize;
       private long totalElements;
       private int totalPages;
       private boolean last;
   }

2. Update service methods to return PageResponse instead of List:
   - FundTransferService.readAllTransfers() -> return PageResponse<FundTransfer>
   - UtilityPaymentService.readPayments() -> return PageResponse<UtilityPayment>
   - UserService.readUsers() -> return PageResponse<User>
   - core-banking UserService.readUsers() -> return PageResponse<User>

3. Map from Spring's Page<T> to PageResponse<T> in each service method

4. Update controller return types to match
```

### 2.6 Add Unit Tests for Fund Transfer, User, and Utility Payment Services (GAP-09)

**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests for the three business services that currently have zero tests:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, Feign failure handling, null request fields
   - FundTransferControllerTest: MockMvc tests for POST /api/v1/transfer and GET /api/v1/transfer

2. internet-banking-user-service:
   - UserServiceTest: test createUser success, duplicate email, invalid email, user not found in core banking, updateUser approval flow, readUsers pagination
   - KeycloakUserServiceTest: test createUser, readUser, updateUser with mocked KeycloakManager
   - UserControllerTest: MockMvc tests for all 4 endpoints

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success, Feign failure, null request fields
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

Use Mockito to mock Feign clients and repositories. Use @WebMvcTest for controller tests. Configure test application.yml to disable Eureka and Config Server.
```

### 2.7 Add Rate Limiting at API Gateway (GAP-37)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway:

1. Add Redis dependency:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Configure rate limiter in the gateway routes (via Spring Cloud Config or application.yml):
   spring:
     cloud:
       gateway:
         routes:
           - id: fund-transfer
             uri: lb://internet-banking-fund-transfer-service
             predicates:
               - Path=/fund-transfer/**
             filters:
               - name: RequestRateLimiter
                 args:
                   redis-rate-limiter.replenishRate: 10
                   redis-rate-limiter.burstCapacity: 20
                   redis-rate-limiter.requestedTokens: 1

3. Implement a KeyResolver bean that extracts the rate limit key from the JWT principal

4. Add Redis to docker-compose.yml

5. Configure lower limits for write endpoints (fund-transfer POST, utility-payment POST) and higher limits for read endpoints
```

### 2.8 Add Custom Health Indicators (GAP-26) and Metrics (GAP-27)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add custom health indicators and business metrics to all services:

1. Health indicators for each service:
   - DatabaseHealthIndicator: verify database connection and schema access
   - For user-service: KeycloakHealthIndicator checking Keycloak realm accessibility
   - For fund-transfer/utility-payment: CoreBankingHealthIndicator checking core-banking is reachable

2. Custom Micrometer metrics:
   - fund-transfer-service: counter for transfers (success/failure), timer for transfer processing, gauge for pending transfers
   - utility-payment-service: counter for payments (success/failure), timer for payment processing
   - user-service: counter for registrations, counter for approvals
   - core-banking-service: counter for transactions by type, histogram for transaction amounts

3. Expose Prometheus endpoint:
   management:
     endpoints:
       web:
         exposure:
           include: health, info, metrics, prometheus
     metrics:
       export:
         prometheus:
           enabled: true
```

### 2.9 Implement Compensation for Failed Feign Calls (GAP-35)

**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Implement a compensation mechanism for failed Feign calls in fund-transfer and utility-payment services:

1. Add a scheduled job in each service that scans for PENDING/PROCESSING entities older than 5 minutes:

   @Scheduled(fixedDelay = 300000)
   public void processStaleTransactions() {
       List<FundTransferEntity> staleTransfers = repository.findByStatusAndCreatedDateBefore(
           TransactionStatus.PENDING, Instant.now().minus(5, ChronoUnit.MINUTES));
       for (FundTransferEntity transfer : staleTransfers) {
           try {
               // Retry the Feign call
               // On success: update to SUCCESS
               // On failure after max retries: update to FAILED
           } catch (Exception e) {
               transfer.setRetryCount(transfer.getRetryCount() + 1);
               if (transfer.getRetryCount() >= MAX_RETRIES) {
                   transfer.setStatus(TransactionStatus.FAILED);
               }
               repository.save(transfer);
           }
       }
   }

2. Add retryCount and failureReason columns to fund_transfer and utility_payment tables

3. Add a REST endpoint to query failed transactions for manual review

4. Consider implementing the Transactional Outbox pattern with a message broker (RabbitMQ) for more robust eventual consistency — this would be a Phase 3 enhancement
```

---

## Phase 3 — Polish

> **Goal:** Improve developer experience, add advanced testing, and enhance API design.

### 3.1 Create Multi-Module Gradle Root Project (GAP-03)

**Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Create a root Gradle project that manages all services as subprojects:

1. Create a root settings.gradle:
   rootProject.name = 'internet-banking-microservices'
   include 'banking-common'
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
       // Shared dependency versions via platform/BOM
   }

3. Create a gradle/libs.versions.toml for centralized version management

4. Update each service's build.gradle to remove duplicated configuration

5. Verify ./gradlew build from root builds all services
```

### 3.2 Standardize Package Structure (GAP-02)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance/
├── configuration/       (Spring @Configuration classes)
│   ├── audit/
│   ├── feign/
│   ├── filter/
│   └── security/
├── controller/          (REST controllers)
├── exception/           (Exception classes and handlers)
├── model/
│   ├── dto/            (Data transfer objects)
│   │   ├── request/
│   │   └── response/
│   ├── entity/         (JPA entities)
│   └── mapper/         (Entity-DTO mappers)
├── repository/          (Spring Data repositories - top level, not under model)
└── service/
    └── client/          (Feign clients - consistent naming)

Refactor each service to match this structure. Ensure all imports are updated and tests still pass.
```

### 3.3 Register Mappers as Spring Beans (GAP-04)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Convert all Mapper classes to Spring-managed beans:

1. Add @Component annotation to all mapper classes:
   - BankAccountMapper, UserMapper, UtilityAccountMapper (core-banking)
   - FundTransferMapper (fund-transfer)
   - UserMapper (user-service)
   - UtilityPaymentMapper (utility-payment)

2. Update all service classes to inject mappers via constructor injection instead of 'new':
   Change: private FundTransferMapper mapper = new FundTransferMapper();
   To: private final FundTransferMapper mapper; (injected via @RequiredArgsConstructor)

3. Update tests to inject mock mappers where needed

Alternatively, consider migrating to MapStruct for compile-time type-safe mapping.
```

### 3.4 Add Integration Tests with Testcontainers (GAP-10)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for core-banking-service:

1. Add Testcontainers dependencies:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create an integration test base class:
   @SpringBootTest
   @Testcontainers
   abstract class IntegrationTestBase {
       @Container
       static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
           .withDatabaseName("banking_core_service");
       @DynamicPropertySource
       static void configureProperties(DynamicPropertyRegistry registry) {
           registry.add("spring.datasource.url", mysql::getJdbcUrl);
       }
   }

3. Write integration tests for:
   - AccountController: GET bank-account, GET util-account
   - TransactionController: POST fund-transfer, POST util-payment
   - UserController: GET user by identification, GET users paginated

4. Add WireMock for testing Feign clients in fund-transfer and utility-payment services

5. Verify Flyway migrations run successfully against the test MySQL container
```

### 3.5 Add Contract Tests Between Services (GAP-11)

**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between consumer and producer services:

1. In core-banking-service (producer):
   - Add spring-cloud-contract-verifier dependency
   - Create contract files in src/test/resources/contracts/ for:
     * GET /api/v1/account/bank-account/{account_number}
     * POST /api/v1/transaction/fund-transfer
     * POST /api/v1/transaction/util-payment
     * GET /api/v1/user/{identification}
   - Generate contract verification tests

2. In consumer services (fund-transfer, utility-payment, user-service):
   - Add spring-cloud-contract-stub-runner dependency
   - Write consumer-side contract tests that verify Feign clients match the contract stubs

3. Publish contract stubs to a local Maven repository for cross-service verification
```

### 3.6 Add API Versioning Strategy Documentation (GAP-21)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Document and formalize the API versioning strategy:

1. Create docs/API_VERSIONING.md documenting:
   - Current strategy: URL path versioning (/api/v1/)
   - When to create v2: breaking changes to request/response schemas
   - Deprecation policy: v(N-1) supported for 6 months after v(N) release
   - How to run multiple versions: separate controller classes per version

2. Add @Deprecated annotation support and OpenAPI deprecation metadata for future endpoint transitions

3. Add API version to all response headers:
   X-API-Version: 1.0
```

### 3.7 Add Filtering and Search to List Endpoints (GAP-23)

**Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Add filtering capabilities to all list endpoints:

1. Fund Transfer GET /api/v1/transfer:
   - Filter by: status, fromAccount, toAccount, dateRange (createdAfter, createdBefore), amountMin, amountMax
   - Use Spring Data Specifications (JpaSpecificationExecutor)

2. Utility Payment GET /api/v1/utility-payment:
   - Filter by: status, account, providerId, dateRange, amountMin, amountMax

3. User Service GET /api/v1/bank-users:
   - Filter by: status, identification

4. Core Banking GET /api/v1/user:
   - Filter by: email, identificationNumber

Implement using JPA Specifications pattern with a generic SpecificationBuilder.
```

### 3.8 Standardize Endpoint Naming (GAP-24)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize all REST endpoint paths to use plural nouns consistently:

Current -> New:
- /api/v1/bank-users -> /api/v1/users (user-service)
- /api/v1/transfer -> /api/v1/transfers (fund-transfer-service)
- /api/v1/utility-payment -> /api/v1/utility-payments (utility-payment-service)
- /api/v1/user -> /api/v1/users (core-banking — note: different service, same path pattern)

Update the API Gateway route configuration to match the new paths. Maintain backward compatibility by keeping old paths as aliases for one release cycle if needed. Update Postman collection.

Note: This is a breaking change for API consumers. Coordinate with any frontend teams before applying.
```

### 3.9 Verify and Configure Distributed Tracing (GAP-28)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Verify and properly configure distributed tracing:

1. Ensure all services have the correct tracing configuration:
   management:
     tracing:
       sampling:
         probability: 1.0  # 100% for dev, lower for prod
     zipkin:
       tracing:
         endpoint: http://zipkin:9411/api/v2/spans

2. Add trace ID to all log output by adding to logback-spring.xml:
   [%X{traceId}/%X{spanId}]

3. Verify traces flow end-to-end: Gateway -> downstream service -> core-banking

4. Add trace ID to error responses so clients can reference it for support
```

### 3.10 Add Fallback Behavior for Read Operations (GAP-33)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add graceful fallback behavior for Feign client failures:

1. For fund-transfer-service read operations:
   - When core-banking is unavailable, return cached account data or a clear "service temporarily unavailable" message
   - GET /api/v1/transfer should still work (reads from local DB only)

2. For user-service:
   - GET /api/v1/bank-users should return local data even if Keycloak is down (skip email enrichment)
   - Registration (POST) should return 503 if Keycloak is unavailable

3. For utility-payment-service:
   - GET /api/v1/utility-payment should still work (reads from local DB only)
   - POST should return 503 if core-banking is unavailable

4. Implement using @CircuitBreaker fallbackMethod or FeignClient fallbackFactory
```

### 3.11 Fix ApplicationTests to Work Standalone (GAP-12)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix all @SpringBootTest contextLoads tests to work without external dependencies:

1. For each service's test/resources/application.yml, ensure:
   - Eureka client is disabled: eureka.client.enabled=false
   - Config server bootstrap is disabled: spring.cloud.config.enabled=false
   - spring.cloud.bootstrap.enabled=false
   - Database uses H2: spring.datasource.url=jdbc:h2:mem:testdb
   - Flyway is disabled for services that don't use it
   - Keycloak properties have test defaults (user-service)
   - Feign clients are properly mocked or pointed to localhost

2. For internet-banking-api-gateway:
   - Mock the OAuth2 resource server JWT configuration
   - Disable route forwarding in tests

3. Verify all contextLoads() tests pass with: ./gradlew test --tests '*ApplicationTests'
```

### 3.12 Add CI/CD Pipeline (No GAP — New Capability)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml:
   - Trigger on push to main and pull requests
   - Java 21 setup
   - Build all services: run ./gradlew build for each service (or root if multi-module)
   - Run all tests
   - Run OWASP dependency check
   - Build Docker images
   - Push to container registry (on main branch merge only)

2. Create .github/workflows/dependency-check.yml:
   - Weekly schedule
   - Run OWASP dependency check across all services
   - Create issue on new high-severity CVEs

3. Add build status badges to README.md
```

### 3.13 Document CSRF Rationale (GAP-18)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add documentation comments to SecurityConfiguration in the API Gateway explaining why CSRF is disabled:

Add a comment above the csrf disable line:
// CSRF protection is disabled because this is a stateless REST API using JWT Bearer tokens.
// CSRF attacks target cookie-based authentication; since this API uses Authorization headers,
// CSRF protection is not applicable. See OWASP CSRF Prevention Cheat Sheet.

Also add this rationale to the KNOWLEDGE_BASE.md security section.
```

---

## Prioritization Summary

| Phase | Items | Critical Fixes | Estimated Effort |
|-------|-------|---------------|-----------------|
| Phase 1 | 15 items | 5 Critical, 5 High, 5 Medium | ~2-3 sprints |
| Phase 2 | 9 items | 4 Critical, 1 High, 3 Medium | ~3-4 sprints |
| Phase 3 | 13 items | 0 Critical, 1 High, 5 Medium, 4 Low | ~4-5 sprints |

### Recommended Execution Order Within Phase 1

1. **GAP-36** — Fix double-deduction bug (data integrity, highest impact)
2. **GAP-13** — Add input validation (prevents NPEs and invalid data)
3. **GAP-05/06/07** — Fix error handling (correct status codes, stop leaking details)
4. **GAP-15** — Remove hardcoded credentials (security hygiene)
5. **GAP-08** — Add Feign error decoders (proper error propagation)
6. **GAP-29** — Fix sensitive data logging (compliance)
7. **GAP-32** — Add timeouts (prevent thread exhaustion)
8. **GAP-31** — Add retry policies (reliability)
9. **GAP-20** — Fix OpenAPI dependency (developer experience)
10. **GAP-19** — Type ResponseEntity (code quality)
11. **GAP-16** — Reduce database permissions (security hardening)
12. **GAP-25** — Structured logging (observability foundation)
13. **GAP-17** — Dependency scanning (ongoing security)
14. **GAP-22** — Pagination metadata (API quality)
