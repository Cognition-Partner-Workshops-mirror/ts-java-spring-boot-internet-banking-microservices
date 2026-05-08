# Remediation Roadmap

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins**: Critical/High severity + Small effort. Immediate fixes that reduce risk with minimal investment.
- **Phase 2 — Important**: Critical/High severity + Medium effort, or Medium severity items that unlock further improvements.
- **Phase 3 — Polish**: Lower severity items, large-effort improvements, and long-term architectural enhancements.

Each item includes a **Devin prompt** that can be used to execute the remediation directly.

---

## Phase 1 — Quick Wins (Critical/High + Small Effort)

Target: 1–2 sprints. These items address the most dangerous issues with the least effort.

---

### 1.1 Fix Double-Deduction Bug in Balance Calculations (GAP-36)

**Severity: Critical** | **Effort: Small** | **Risk: Financial Loss**

Fix the `availableBalance` calculation in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()` in core-banking-service. The `availableBalance` is currently computed from the already-modified `actualBalance`, causing double deduction/addition.

<details>
<summary>Devin Prompt</summary>

```
Fix the double-deduction balance bug in core-banking-service TransactionService.

In `internalFundTransfer()` (lines 90-91 and 99-100), `availableBalance` is set by subtracting/adding `amount` from the already-modified `actualBalance`. This causes a double deduction for the sender and double credit for the receiver.

Fix:
- Line 91: Change `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())`
- Line 100: Change `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))` to `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance())`

Apply the same fix in `utilPayment()` (line 64): Change `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()))` to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())`

Update the existing unit tests in TransactionServiceTest to verify the correct balances after fund transfer and utility payment. Add assertions that check actualBalance equals availableBalance after each operation.

Open a PR with the fix and updated tests.
```

</details>

---

### 1.2 Add Input Validation to All Request DTOs (GAP-14)

**Severity: Critical** | **Effort: Small** | **Risk: Data Integrity / Financial Loss**

Add Jakarta Bean Validation to all request DTOs and `@Valid` to controller parameters.

<details>
<summary>Devin Prompt</summary>

```
Add input validation to all request DTOs across the internet banking microservices.

1. Add `spring-boot-starter-validation` dependency to build.gradle of: core-banking-service, fund-transfer-service, user-service, utility-payment-service.

2. Add validation annotations to these DTOs:

   core-banking-service FundTransferRequest:
   - @NotBlank fromAccount
   - @NotBlank toAccount
   - @NotNull @Positive amount

   core-banking-service UtilityPaymentRequest:
   - @NotNull providerId
   - @NotNull @Positive amount
   - @NotBlank referenceNumber
   - @NotBlank account

   fund-transfer-service FundTransferRequest:
   - @NotBlank fromAccount
   - @NotBlank toAccount
   - @NotNull @Positive amount

   utility-payment-service UtilityPaymentRequest:
   - @NotNull providerId
   - @NotNull @Positive amount
   - @NotBlank referenceNumber
   - @NotBlank account

   user-service User (for registration):
   - @NotBlank @Email email
   - @NotBlank identification
   - @NotBlank @Size(min=8) password

   user-service UserUpdateRequest:
   - @NotNull status

3. Add @Valid annotation to all @RequestBody parameters in all controllers.

4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns HTTP 422 with field-level error details.

5. Add unit tests validating that invalid requests return 422.

Open a PR with all changes.
```

</details>

---

### 1.3 Fix Error Response HTTP Status Codes (GAP-05)

**Severity: Critical** | **Effort: Small** | **Risk: Broken Monitoring / Poor UX**

Map exceptions to proper HTTP status codes instead of returning 400 for everything.

<details>
<summary>Devin Prompt</summary>

```
Fix the GlobalExceptionHandler in all 4 services (core-banking-service, fund-transfer-service, user-service, utility-payment-service) to return proper HTTP status codes.

Update each GlobalExceptionHandler to map exceptions as follows:
- EntityNotFoundException → HTTP 404 Not Found
- InsufficientFundsException → HTTP 422 Unprocessable Entity
- UserAlreadyRegisteredException → HTTP 409 Conflict
- InvalidEmailException → HTTP 422 Unprocessable Entity
- InvalidBankingUserException → HTTP 404 Not Found
- SimpleBankingGlobalException (catch-all for custom) → HTTP 400 Bad Request
- Generic Exception → HTTP 500 Internal Server Error (with generic message, no stack trace)

Ensure all handlers return a consistent ErrorResponse JSON body: { "code": "...", "message": "..." }.

Remove the string concatenation that leaks exception details in the generic Exception handler: replace `"Exception occur inside API " + e` with a generic `ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")` and log the full exception at ERROR level.

Add @ResponseStatus annotations or explicit status codes to each handler.

Open a PR with all changes.
```

</details>

---

### 1.4 Stop Logging Sensitive Data (GAP-27)

**Severity: High** | **Effort: Small** | **Risk: Data Breach / Compliance**

Remove password and financial data from log statements.

<details>
<summary>Devin Prompt</summary>

```
Fix sensitive data logging across all services in the internet banking microservices.

1. In user-service UserController.createUser(): Change `log.info("Creating user with {}", request.toString())` to `log.info("Creating user with email {}", request.getEmail())` to avoid logging the password.

2. In core-banking-service TransactionController: Change fund transfer and utility payment log statements to only log account identifiers, not full request objects with amounts.

3. In fund-transfer-service FundTransferController and FundTransferService: Log only fromAccount and toAccount, not the full request toString().

4. In utility-payment-service: Log only the provider ID and account, not the full request.

5. Override toString() on the user-service User DTO to exclude the password field, or add @ToString.Exclude on the password field.

Open a PR with all changes.
```

</details>

---

### 1.5 Fix Generic Exception Handler Information Leak (GAP-06)

**Severity: High** | **Effort: Small** | **Risk: Security**

This is addressed as part of item 1.3 above (returning generic error message for unhandled exceptions).

---

### 1.6 Add Feign Error Decoder to Fund-Transfer and Utility-Payment Services (GAP-07)

**Severity: High** | **Effort: Small** | **Risk: Poor Error Propagation**

<details>
<summary>Devin Prompt</summary>

```
Add a CustomFeignErrorDecoder to the fund-transfer-service and utility-payment-service.

The user-service already has a working CustomFeignErrorDecoder at:
internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/feign/CustomFeignErrorDecoder.java

1. Copy the CustomFeignErrorDecoder class to both fund-transfer-service and utility-payment-service, placing it in a `configuration.feign` package in each service.

2. Update the CustomFeignClientConfiguration in both services to register the error decoder as a bean:
   @Bean
   public ErrorDecoder errorDecoder() {
       return new CustomFeignErrorDecoder();
   }

3. Ensure the FeignClient annotations in both services reference the updated configuration.

4. Add unit tests that verify Feign errors are properly decoded and re-thrown as SimpleBankingGlobalException.

Open a PR with all changes.
```

</details>

---

### 1.7 Fix OpenAPI Dependency — WebFlux to WebMVC (GAP-21)

**Severity: High** | **Effort: Small** | **Risk: Broken API Documentation**

<details>
<summary>Devin Prompt</summary>

```
Fix the incorrect OpenAPI/Swagger dependency in 4 services.

In the build.gradle of core-banking-service, fund-transfer-service, user-service, and utility-payment-service, replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These 4 services use Spring MVC (servlet stack), not WebFlux. The WebFlux dependency is incorrect.

The api-gateway does NOT need this change because it uses WebFlux (Spring Cloud Gateway).

Open a PR with the dependency fix.
```

</details>

---

### 1.8 Remove Hardcoded Credentials from Docker Compose (GAP-16)

**Severity: High** | **Effort: Small** | **Risk: Secret Exposure**

<details>
<summary>Devin Prompt</summary>

```
Remove hardcoded credentials from Docker Compose files in the internet banking microservices project.

1. Create a `.env.example` file in the docker-compose/ directory with all required environment variables:
   MYSQL_ROOT_PASSWORD=changeme
   MYSQL_APP_PASSWORD=changeme
   KC_DB_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   POSTGRES_PASSWORD=changeme

2. Update docker-compose.yml and docker-compose-support-apps.yml to use variable substitution:
   - MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
   - KC_DB_PASSWORD: ${KC_DB_PASSWORD}
   - etc.

3. Add `.env` to .gitignore (the actual file with real values should not be committed).

4. Update docker-compose/mysql/privileges.sql to use an environment variable for the password, or document that the password must be changed before deployment.

5. Update README.md to document the .env setup step.

Open a PR with all changes.
```

</details>

---

### 1.9 Add Timeout Configuration for Feign Clients (GAP-33)

**Severity: High** | **Effort: Small** | **Risk: Thread Starvation**

<details>
<summary>Devin Prompt</summary>

```
Add explicit timeout configuration for all Feign clients in the internet banking microservices.

Since configuration is externalized via Spring Cloud Config Server, add the following timeout settings to each service's application.yml (for local/test use) and document what should be added to the Config Server repo:

For fund-transfer-service, utility-payment-service, and user-service, add to application.yml:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
          core-banking-service:
            connect-timeout: 5000
            read-timeout: 10000

Also add logging level for Feign debugging:
logging:
  level:
    com.javatodev.finance.service.rest: DEBUG

Open a PR with all changes.
```

</details>

---

### 1.10 Add Retry Policies for Feign Clients (GAP-32)

**Severity: High** | **Effort: Small** | **Risk: Transient Failure Sensitivity**

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j retry configuration for Feign clients in fund-transfer-service, utility-payment-service, and user-service.

1. Add dependency to each service's build.gradle:
   implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
   implementation 'io.github.resilience4j:resilience4j-feign:2.2.0'

2. Add retry configuration to each service's application.yml:
   resilience4j:
     retry:
       instances:
         coreBankingService:
           max-attempts: 3
           wait-duration: 1s
           exponential-backoff-multiplier: 2
           retry-exceptions:
             - java.io.IOException
             - feign.RetryableException
           ignore-exceptions:
             - com.javatodev.finance.exception.SimpleBankingGlobalException

3. Annotate the Feign call methods in FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment() with @Retry(name = "coreBankingService").

Note: Only add retries for read operations or idempotent writes. Fund transfers that modify state should NOT be retried without idempotency keys (see Phase 2 item).

Open a PR with all changes.
```

</details>

---

### 1.11 Fix Empty ApplicationTests to Work Without Infrastructure (GAP-13)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Fix the empty ApplicationTests in fund-transfer-service, user-service, utility-payment-service, and api-gateway so they can run without Config Server and Eureka.

1. For fund-transfer-service, user-service, and utility-payment-service:
   - Create src/test/resources/application.yml (use core-banking-service's test config as template):
     spring:
       application:
         name: <service-name>
       datasource:
         url: jdbc:h2:mem:<schema-name>
         username: root
         password: password
         driver-class-name: org.h2.Driver
       jpa:
         hibernate:
           ddl-auto: create-drop
         database-platform: org.hibernate.dialect.H2Dialect
       cloud:
         config:
           enabled: false
         discovery:
           enabled: false
     eureka:
       client:
         enabled: false

   - Update the ApplicationTests class to either:
     a. Add @SpringBootTest with specific configuration to disable external dependencies, or
     b. Convert to a simple non-contextual test that verifies the application class exists.

2. For api-gateway: Add a test profile that disables Eureka and Config Server bootstrap.

3. Verify all tests pass with `./gradlew test` in each service directory.

Open a PR with all changes.
```

</details>

---

### 1.12 Fix Keycloak Client Thread Safety (GAP-19)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Fix the thread-unsafe Keycloak client singleton in user-service KeycloakProperties.

Replace the manual singleton pattern with a Spring-managed @Bean. In KeycloakProperties:

1. Remove the static `keycloakInstance` field and the `getInstance()` method.
2. Create a new @Configuration class (e.g., KeycloakConfig) that exposes a @Bean Keycloak:

   @Configuration
   public class KeycloakConfig {
       @Bean
       public Keycloak keycloak(KeycloakProperties properties) {
           return KeycloakBuilder.builder()
               .serverUrl(properties.getServerUrl())
               .realm(properties.getRealm())
               .grantType("client_credentials")
               .clientId(properties.getClientId())
               .clientSecret(properties.getClientSecret())
               .build();
       }
   }

3. Update KeycloakProperties to expose getters for serverUrl, clientId, clientSecret (add @Getter to all fields).
4. Update KeycloakManager to inject the Keycloak bean instead of calling KeycloakProperties.getInstance().

Open a PR with the changes.
```

</details>

---

### 1.13 Add Dependency Vulnerability Scanning (GAP-20)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Add OWASP Dependency-Check to the Gradle build for all services in the internet banking microservices project.

1. Since there is no root build.gradle, add the OWASP plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.1.0'
   }

   dependencyCheck {
       failBuildOnCVSS = 7.0
       suppressionFile = "${rootDir}/owasp-suppressions.xml"
   }

2. Create an empty owasp-suppressions.xml file at the repo root for future suppressions.

3. Add a GitHub Actions workflow (.github/workflows/dependency-check.yml) that runs the dependency check on each push/PR:
   - Checkout code
   - Set up Java 21
   - Run ./gradlew dependencyCheckAnalyze in each service directory
   - Upload reports as artifacts

Open a PR with all changes.
```

</details>

---

## Phase 2 — Important (Critical/High + Medium Effort)

Target: 2–4 sprints. These items require more design work but address significant risks.

---

### 2.1 Add Circuit Breakers to All Feign Clients (GAP-31)

**Severity: Critical** | **Effort: Medium** | **Risk: Cascading Failure**

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j circuit breakers to all Feign clients in the internet banking microservices.

1. Add dependencies to fund-transfer-service, utility-payment-service, and user-service build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign in each service's application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Configure circuit breaker parameters in application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           sliding-window-size: 10
           failure-rate-threshold: 50
           wait-duration-in-open-state: 30s
           permitted-number-of-calls-in-half-open-state: 3
           slow-call-duration-threshold: 5s
           slow-call-rate-threshold: 80

4. Create fallback factories for each Feign client:
   - BankingCoreFeignClientFallbackFactory for fund-transfer-service
   - BankingCoreRestClientFallbackFactory for utility-payment-service and user-service
   
   Each fallback should return a meaningful error response (e.g., throw a custom ServiceUnavailableException that the GlobalExceptionHandler maps to HTTP 503).

5. Register the fallback factories in the @FeignClient annotations.

6. Add tests that verify circuit breaker behavior using WireMock to simulate core-banking failures.

Open a PR with all changes.
```

</details>

---

### 2.2 Add Idempotency Keys for Financial Transactions (GAP-35)

**Severity: Critical** | **Effort: Medium** | **Risk: Duplicate Transactions**

<details>
<summary>Devin Prompt</summary>

```
Implement idempotency key support for the fund transfer and utility payment endpoints.

1. In fund-transfer-service:
   - Add a unique constraint on a new `idempotencyKey` column in the `fund_transfer` table.
   - Accept an `Idempotency-Key` header in FundTransferController.sendFundTransfer().
   - Before processing, check if a fund_transfer with that key already exists:
     - If found with status SUCCESS, return the cached response.
     - If found with status PENDING, return HTTP 409 Conflict (processing in progress).
     - If not found, proceed with the transfer using the provided key.
   - If no Idempotency-Key header is provided, generate a UUID (backward compatible).

2. Apply the same pattern to utility-payment-service.

3. Add tests:
   - First request with key → processes normally.
   - Second request with same key → returns cached response without reprocessing.
   - Request without key → generates a new UUID and processes normally.

Open a PR with all changes.
```

</details>

---

### 2.3 Implement Compensation/Rollback on Feign Failure (GAP-08)

**Severity: Critical** | **Effort: Large** → Medium (with simplified approach)

<details>
<summary>Devin Prompt</summary>

```
Add failure handling and compensation logic to fund-transfer-service and utility-payment-service.

1. In FundTransferService.fundTransfer():
   - Wrap the Feign call in a try-catch.
   - On FeignException or any exception:
     a. Update the FundTransferEntity status to FAILED (add FAILED to TransactionStatus enum).
     b. Log the error with full context (fromAccount, toAccount, amount, exception).
     c. Throw a custom TransferFailedException that the GlobalExceptionHandler maps to HTTP 502 Bad Gateway.

2. Apply the same pattern to UtilityPaymentService.utilPayment().

3. Add a @Scheduled job (e.g., every 5 minutes) in each service that:
   - Queries for records in PENDING/PROCESSING status older than 10 minutes.
   - Logs a warning for each stuck record.
   - Optionally attempts retry (if idempotency keys are implemented).

4. Add the FAILED status to the TransactionStatus enum in both services.

5. Add unit tests:
   - Feign call fails → entity status is FAILED.
   - Feign call succeeds → entity status is SUCCESS (existing behavior).

Open a PR with all changes.
```

</details>

---

### 2.4 Add Authentication to Downstream Services (GAP-15)

**Severity: Critical** | **Effort: Medium** | **Risk: Unauthorized Access**

<details>
<summary>Devin Prompt</summary>

```
Secure downstream services so they cannot be accessed directly without proper authentication.

Option A (Recommended — Network Isolation):
1. In docker-compose.yml, remove the `ports` mapping for internal services (core-banking, fund-transfer, user, utility-payment). Only the API Gateway (8082), Service Registry (8081), and Config Server (8090) need external ports.

2. Remove the user-service and utility-payment-service port mappings:
   - Remove `ports: - 8083:8083` from user-service
   - Remove `ports: - 8084:8084` from fund-transfer-service
   - Remove `ports: - 8085:8085` from utility-payment-service
   - Remove `ports: - 8092:8092` from core-banking-service

3. Services can still communicate via the Docker network using container names.

Option B (Defense in Depth — add JWT validation):
1. Add spring-boot-starter-security and spring-boot-starter-oauth2-resource-server to core-banking, fund-transfer, user, and utility-payment services.
2. Configure JWT validation in each service pointing to the Keycloak JWK Set URI.
3. Propagate the JWT token from Gateway to downstream services via Feign interceptors.

Implement Option A first (simpler, immediate), then Option B for defense in depth.

Open a PR with the changes.
```

</details>

---

### 2.5 Extract Shared Library (GAP-01)

**Severity: High** | **Effort: Medium**

<details>
<summary>Devin Prompt</summary>

```
Create a shared banking-common library and extract duplicated code from the internet banking microservices.

1. Create a new module `banking-common/` at the repo root with its own build.gradle:
   - Group: com.javatodev.finance
   - No Spring Boot plugin (use dependency-management only)
   - Dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, lombok, jackson

2. Move these classes into banking-common:
   - model/mapper/BaseMapper.java
   - model/dto/AuditAware.java
   - exception/GlobalExceptionHandler.java
   - exception/SimpleBankingGlobalException.java
   - exception/ErrorResponse.java
   - exception/EntityNotFoundException.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java

3. Create a root settings.gradle that includes all 7 modules (banking-common + 6 services).

4. Add `implementation project(':banking-common')` to each service's build.gradle.

5. Remove the duplicated classes from each service and update imports.

6. Verify all services build and tests pass.

Open a PR with all changes.
```

</details>

---

### 2.6 Add Database Transaction Isolation for Financial Operations (GAP-37)

**Severity: High** | **Effort: Medium**

<details>
<summary>Devin Prompt</summary>

```
Add proper transaction isolation and locking for financial operations in core-banking-service.

1. Add @Version annotation to BankAccountEntity for optimistic locking:
   @Version
   private Long version;

2. Update TransactionService.internalFundTransfer():
   - Use @Transactional(isolation = Isolation.REPEATABLE_READ) on the method.
   - Use bankAccountRepository.findByNumberForUpdate() with a custom query using pessimistic locking:
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT b FROM BankAccountEntity b WHERE b.number = :number")
     Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String number);

3. Apply the same locking to TransactionService.utilPayment().

4. Add integration tests that simulate concurrent fund transfers to the same account and verify no lost updates.

Open a PR with all changes.
```

</details>

---

### 2.7 Add Pagination Metadata to Responses (GAP-22)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Add pagination metadata to all paginated endpoints in the internet banking microservices.

1. Create a generic PageResponse<T> DTO in the banking-common module (or in each service if the shared library is not yet extracted):
   @Data
   public class PageResponse<T> {
       private List<T> content;
       private int page;
       private int size;
       private long totalElements;
       private int totalPages;
       private boolean last;
   }

2. Update all services that return paginated data to return PageResponse instead of raw List:
   - core-banking-service UserController.readUsers()
   - fund-transfer-service FundTransferController.readFundTransfers()
   - user-service UserController.readUsers()
   - utility-payment-service UtilityPaymentController.readPayments()

3. In each service layer, keep the Page<Entity> object and map it to PageResponse<DTO>.

4. Update existing tests to account for the new response shape.

Open a PR with all changes.
```

</details>

---

### 2.8 Add Structured Logging (GAP-26)

**Severity: Medium** | **Effort: Medium**

<details>
<summary>Devin Prompt</summary>

```
Add structured JSON logging to all services in the internet banking microservices.

1. Add the Logstash Logback encoder dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a logback-spring.xml in each service's src/main/resources/:
   - Console appender with LogstashEncoder for JSON output.
   - Include service name, trace ID, span ID in each log line.
   - Use a plain text encoder for the 'dev' profile for local readability.

3. Add MDC context (user auth ID, request ID) via the AppAuthUserFilter.

4. Ensure all existing log.info/error/warn statements still work correctly.

Open a PR with all changes.
```

</details>

---

### 2.9 Add Custom Health Indicators (GAP-28)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Add custom health indicators to the internet banking microservices.

1. In fund-transfer-service and utility-payment-service, create a CoreBankingHealthIndicator that pings the core-banking-service health endpoint via Feign:
   @Component
   public class CoreBankingHealthIndicator implements HealthIndicator {
       @Override
       public Health health() {
           // Try to call core-banking actuator/health
           // Return Health.up() or Health.down() with details
       }
   }

2. In user-service, add:
   - CoreBankingHealthIndicator (same as above)
   - KeycloakHealthIndicator that checks Keycloak connectivity

3. Ensure health endpoints are exposed in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
     endpoint:
       health:
         show-details: always

Open a PR with all changes.
```

</details>

---

### 2.10 Add Fallback Behavior for Feign Clients (GAP-34)

**Severity: Medium** | **Effort: Medium**

This is addressed as part of item 2.1 (Circuit Breakers) — fallback factories are included in that implementation.

---

### 2.11 Reduce Database User Privileges (GAP-17)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Create per-service database users with minimal privileges in the internet banking microservices.

1. Update docker-compose/mysql/privileges.sql to create 4 separate users:

   -- Core banking service (needs DDL for Flyway)
   CREATE USER 'core_banking_svc'@'%' IDENTIFIED BY '${CORE_BANKING_DB_PASSWORD}';
   GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, REFERENCES ON banking_core_service.* TO 'core_banking_svc'@'%';

   -- Fund transfer service
   CREATE USER 'fund_transfer_svc'@'%' IDENTIFIED BY '${FUND_TRANSFER_DB_PASSWORD}';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* TO 'fund_transfer_svc'@'%';

   -- User service
   CREATE USER 'user_svc'@'%' IDENTIFIED BY '${USER_SVC_DB_PASSWORD}';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* TO 'user_svc'@'%';

   -- Utility payment service
   CREATE USER 'util_payment_svc'@'%' IDENTIFIED BY '${UTIL_PAYMENT_DB_PASSWORD}';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* TO 'util_payment_svc'@'%';

2. Update each service's database connection configuration to use its own credentials.

3. Remove the overly-privileged javatodev_development user.

Open a PR with all changes.
```

</details>

---

## Phase 3 — Polish (Lower Severity / Large Effort)

Target: Ongoing. These items improve developer experience and long-term maintainability.

---

### 3.1 Add Comprehensive Unit Tests to All Services (GAP-10)

**Severity: Critical** | **Effort: Large**

<details>
<summary>Devin Prompt</summary>

```
Add comprehensive unit tests to all services in the internet banking microservices that currently lack tests.

For each service (fund-transfer-service, user-service, utility-payment-service), create:

1. Service layer tests (with mocked repositories and Feign clients):
   - Happy path for each public method.
   - Error cases (entity not found, Feign failure, validation errors).
   
2. Controller layer tests using MockMvc:
   - Verify HTTP status codes for success and error cases.
   - Verify request validation (when @Valid is added).
   - Verify response body structure.

3. Mapper tests:
   - Verify entity-to-DTO and DTO-to-entity conversions.

Specific test classes needed:

fund-transfer-service:
- FundTransferServiceTest (mock FundTransferRepository, BankingCoreFeignClient)
- FundTransferControllerTest (MockMvc)
- FundTransferMapperTest

user-service:
- UserServiceTest (mock UserRepository, BankingCoreRestClient, KeycloakUserService)
- KeycloakUserServiceTest (mock KeycloakManager)
- UserControllerTest (MockMvc)

utility-payment-service:
- UtilityPaymentServiceTest (mock UtilityPaymentRepository, BankingCoreRestClient)
- UtilityPaymentControllerTest (MockMvc)
- UtilityPaymentMapperTest

Use the existing core-banking-service tests as a reference for style and patterns.

Open a PR with all tests.
```

</details>

---

### 3.2 Add Integration Tests (GAP-11)

**Severity: High** | **Effort: Large**

<details>
<summary>Devin Prompt</summary>

```
Add integration tests for all services in the internet banking microservices.

1. Add WireMock dependency to fund-transfer-service, utility-payment-service, and user-service:
   testImplementation 'org.springframework.cloud:spring-cloud-contract-wiremock'

2. For each service, create @SpringBootTest integration tests that:
   - Start the full Spring context with H2 database.
   - Use WireMock to stub Feign client calls to core-banking-service.
   - Test the full request lifecycle: controller → service → repository → Feign call.

3. For core-banking-service, create integration tests that:
   - Use H2 with Flyway migrations (enable Flyway in test profile).
   - Test account lookups, fund transfers, and utility payments with real database.
   - Verify transaction records are created correctly.

4. Create an integration test profile in each service's test resources.

Open a PR with all integration tests.
```

</details>

---

### 3.3 Add Contract Tests Between Services (GAP-12)

**Severity: Medium** | **Effort: Large**

<details>
<summary>Devin Prompt</summary>

```
Implement Spring Cloud Contract tests between the internet banking microservices.

1. Add Spring Cloud Contract dependencies to core-banking-service (the provider):
   - spring-cloud-contract-verifier (test dependency)
   - spring-cloud-starter-contract-verifier (Gradle plugin)

2. Define contracts in core-banking-service for each endpoint consumed by other services:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}

3. Generate and publish stubs from core-banking-service.

4. In fund-transfer-service, utility-payment-service, and user-service:
   - Add spring-cloud-contract-stub-runner dependency.
   - Write consumer-side tests that verify Feign clients work against the generated stubs.

Open a PR with all contract tests.
```

</details>

---

### 3.4 Create Multi-Module Gradle Root Project (GAP-02)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Create a Gradle multi-module root project for the internet banking microservices.

1. Create a root settings.gradle in the repo root:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'

2. Create a root build.gradle with shared configuration:
   - Common Java version (21)
   - Common Spring Boot version (3.2.4)
   - Common Spring Cloud version (2023.0.0)
   - Common test configuration

3. Simplify each service's build.gradle to inherit from the root.

4. Add a Gradle wrapper at the root level.

5. Verify `./gradlew build` from the root builds all services.
6. Verify `./gradlew test` from the root runs all tests.

Open a PR with the changes.
```

</details>

---

### 3.5 Standardize Package Structure (GAP-03)

**Severity: Low** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Standardize the package structure across all services in the internet banking microservices.

Adopt this consistent structure for all services:
  com.javatodev.finance/
  ├── controller/
  ├── service/
  │   └── rest/          (Feign clients)
  ├── repository/
  ├── model/
  │   ├── entity/
  │   ├── dto/
  │   │   ├── request/
  │   │   └── response/
  │   └── mapper/
  ├── configuration/
  │   ├── audit/
  │   ├── feign/
  │   ├── filter/
  │   └── security/      (if applicable)
  └── exception/

Move classes that are in non-standard packages:
- fund-transfer-service: model.repository → repository
- user-service: model.repository → repository
- user-service: model.rest.response → model.dto.response
- utility-payment-service: model.rest.request → model.dto.request
- utility-payment-service: model.rest.response → model.dto.response

Update all imports accordingly. Verify all services build and tests pass.

Open a PR with the refactoring.
```

</details>

---

### 3.6 Add Prometheus Metrics Endpoints (GAP-29)

**Severity: Low** | **Effort: Medium**

<details>
<summary>Devin Prompt</summary>

```
Add Prometheus metrics endpoints and custom business metrics to the internet banking microservices.

1. Add the Prometheus registry dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the Prometheus endpoint in each service's application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus

3. Add custom business metrics using Micrometer:

   In fund-transfer-service:
   - Counter: fund_transfers_total (tags: status=success|failed)
   - Timer: fund_transfer_duration_seconds
   - Gauge: fund_transfers_pending_count

   In utility-payment-service:
   - Counter: utility_payments_total (tags: status=success|failed)
   - Timer: utility_payment_duration_seconds

   In user-service:
   - Counter: user_registrations_total (tags: status=success|failed)

4. Inject MeterRegistry and record metrics in each service class.

Open a PR with all changes.
```

</details>

---

### 3.7 Convert Mappers to Spring Beans or MapStruct (GAP-04)

**Severity: Low** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Convert manual mapper classes to Spring-managed beans in the internet banking microservices.

For each mapper class (BankAccountMapper, UserMapper, UtilityAccountMapper, FundTransferMapper, UtilityPaymentMapper):

1. Add @Component annotation to the mapper class.
2. Change service classes to inject the mapper via constructor injection instead of `new`.
3. Remove the `private XMapper mapper = new XMapper()` field initializations.

For example, in FundTransferService:
- Before: `private FundTransferMapper mapper = new FundTransferMapper();`
- After: `private final FundTransferMapper mapper;` (injected via @RequiredArgsConstructor)

Update all affected service classes across all services. Verify tests pass.

Open a PR with all changes.
```

</details>

---

### 3.8 Improve RESTful URL Conventions (GAP-24)

**Severity: Low** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Improve RESTful URL conventions in the internet banking microservices.

Update the following endpoints to follow REST conventions:

1. user-service:
   - PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
   - POST /api/v1/bank-users/register → POST /api/v1/bank-users

2. Standardize path parameter naming:
   - Use consistent names: {id} for entity IDs, {accountNumber} for account numbers, {identification} for NIC

3. Update the Gateway route configuration to match any new paths.

4. Update Postman collection if accessible, or document the API changes.

5. Ensure backward compatibility by keeping the old endpoints active with @Deprecated annotation and a redirect, or document this as a breaking change.

Open a PR with all changes.
```

</details>

---

### 3.9 Add Separate Request/Response DTOs (GAP-25)

**Severity: Low** | **Effort: Small**

<details>
<summary>Devin Prompt</summary>

```
Create separate request and response DTOs for the user registration endpoint in user-service.

1. Create CreateUserRequest DTO:
   @Data
   public class CreateUserRequest {
       @NotBlank @Email
       private String email;
       @NotBlank
       private String identification;
       @NotBlank @Size(min = 8)
       private String password;
   }

2. Create UserResponse DTO (excludes sensitive fields):
   @Data
   public class UserResponse {
       private Long id;
       private String identification;
       private Status status;
       private Instant createdDate;
   }

3. Update UserController.createUser() to accept CreateUserRequest and return UserResponse.
4. Update UserService.createUser() to work with the new DTOs.
5. Add mapper methods for the new DTOs.

Open a PR with all changes.
```

</details>

---

### 3.10 Document API Versioning Strategy (GAP-23)

**Severity: Low** | **Effort: Medium**

<details>
<summary>Devin Prompt</summary>

```
Document and formalize the API versioning strategy for the internet banking microservices.

1. Create docs/API_VERSIONING.md documenting:
   - Current strategy: URL path prefix (/api/v1/)
   - Rules for when a new version is required (breaking changes)
   - Deprecation policy (how long old versions are maintained)
   - Migration guide template

2. Add @Tag annotations to all controllers with version information.

3. Configure springdoc-openapi to group endpoints by version.

Open a PR with the documentation and configuration.
```

</details>

---

## Priority Summary

| Phase | Items | Severity Coverage | Timeline |
|---|---|---|---|
| **Phase 1** | 13 items | 3 Critical, 7 High, 3 Medium | 1–2 sprints |
| **Phase 2** | 11 items | 4 Critical, 3 High, 4 Medium | 2–4 sprints |
| **Phase 3** | 10 items | 1 Critical, 1 High, 2 Medium, 6 Low | Ongoing |

### Recommended Execution Order Within Phase 1

1. **GAP-36** — Fix double-deduction bug (active financial loss)
2. **GAP-14** — Add input validation (prevents negative transfers, null crashes)
3. **GAP-05/06** — Fix error handling (enables proper monitoring)
4. **GAP-27** — Stop logging sensitive data (compliance risk)
5. **GAP-07** — Add Feign error decoders (error propagation)
6. **GAP-21** — Fix OpenAPI dependency (unblocks API documentation)
7. **GAP-16** — Remove hardcoded credentials (security hygiene)
8. **GAP-33** — Add timeouts (prevents thread starvation)
9. **GAP-32** — Add retry policies (transient failure resilience)
10. **GAP-13** — Fix ApplicationTests (enables CI testing)
11. **GAP-19** — Fix Keycloak thread safety (correctness)
12. **GAP-20** — Add dependency scanning (supply chain security)
