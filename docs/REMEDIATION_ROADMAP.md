# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases: quick wins, important structural improvements, and polish items.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort fixes that immediately improve security, correctness, and developer experience.

### 1.1 Fix the Double-Deduction Balance Bug

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Medium

The `availableBalance` calculation in `TransactionService.internalFundTransfer()` and `utilPayment()` subtracts the amount twice. This is a data-corrupting production bug.

**Devin Prompt:**
```
Fix the double-deduction bug in core-banking-service TransactionService.
In internalFundTransfer(), line 91 sets availableBalance = actualBalance - amount,
but actualBalance was already decremented on line 90, causing a double deduction.
The correct line should be: fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance()).
Apply the same fix to the toAccount credit (line 100) and to the utilPayment() method (line 64).
Add unit tests that assert availableBalance equals actualBalance after each transfer.
```

### 1.2 Fix Error Handling: Stop Returning 400 for Everything

**Gap:** 2.1, 2.2, 2.3 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
Refactor GlobalExceptionHandler in all 4 business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service):

1. Change the catch-all Exception handler to return 500 Internal Server Error
   with a structured ErrorResponse (never leak exception details to clients).
2. Add specific handlers:
   - EntityNotFoundException -> 404 Not Found
   - InsufficientFundsException -> 422 Unprocessable Entity
   - UserAlreadyRegisteredException -> 409 Conflict
   - InvalidEmailException / InvalidBankingUserException -> 400 Bad Request
3. Ensure all handlers return ErrorResponse { code, message } consistently.
4. Add unit tests for each exception handler mapping.
```

### 1.3 Add Input Validation to All Request DTOs

**Gap:** 4.2 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Jakarta Validation annotations to all request DTOs across all services:

1. Core Banking FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
   @NotNull @Positive amount.
2. Core Banking UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
   @NotBlank referenceNumber, @NotBlank account.
3. User Service User (registration): @NotBlank email, @Email email,
   @NotBlank identification, @NotBlank password, @Size(min=8) password.
4. Fund Transfer FundTransferRequest: same as Core Banking.
5. Utility Payment UtilityPaymentRequest: same as Core Banking.
6. Add @Valid on all @RequestBody parameters in controllers.
7. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler
   that returns 400 with field-level error details in ErrorResponse format.
8. Add unit tests for validation.
```

### 1.4 Stop Logging Passwords

**Gap:** 4.5 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service UserController.createUser(), the log statement
logs the entire User DTO including the password field via Lombok's @Data toString().

Fix this by:
1. Excluding the password field from toString() using @ToString.Exclude on
   the password field in the User DTO.
2. Alternatively, override toString() to exclude sensitive fields.
3. Audit all other log statements across all services for potential PII/credential
   leakage and fix any found.
```

### 1.5 Fix Keycloak Singleton Thread Safety

**Gap:** 4.4 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix the thread-unsafe lazy singleton in internet-banking-user-service
KeycloakProperties.getInstance(). The current implementation has a race condition.

Replace the manual singleton with a @Bean method in a @Configuration class,
or make the field volatile and use double-checked locking, or simplest:
initialize the Keycloak instance eagerly in a @PostConstruct method.
Add a unit test that verifies the same instance is returned across calls.
```

### 1.6 Add Type Parameters to ResponseEntity

**Gap:** 5.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add generic type parameters to all ResponseEntity return types across all controllers
in all services. For example, change:
  public ResponseEntity getBankAccount(...)
to:
  public ResponseEntity<BankAccount> getBankAccount(...)

This will fix Swagger/OpenAPI documentation to show proper response schemas.
Apply to all controller methods in core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
```

### 1.7 Fix Swagger Dependency for Servlet Services

**Gap:** 5.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the build.gradle of core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service,
replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (servlet), not WebFlux. Only the API Gateway should
use the webflux variant (and it doesn't currently include springdoc at all).
Verify Swagger UI loads at /swagger-ui.html after the change.
```

### 1.8 Add Feign Timeout Configuration

**Gap:** 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add explicit Feign timeout configuration to internet-banking-fund-transfer-service
and internet-banking-utility-payment-service.

In each service's application.yml (or via Spring Cloud Config), add:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Also add these to internet-banking-user-service for its BankingCoreRestClient.
This prevents indefinite hangs when core-banking-service is slow or down.
```

### 1.9 Add Retry Policies for Feign Clients

**Gap:** 7.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add spring-retry and Resilience4j retry to the Feign clients in
internet-banking-fund-transfer-service, internet-banking-utility-payment-service,
and internet-banking-user-service.

1. Add dependency: implementation 'org.springframework.retry:spring-retry'
2. Add @EnableRetry to each application class.
3. Configure retry for Feign:
   spring.cloud.openfeign.client.config.default.retryer with max 3 attempts
   and 1-second backoff.
4. Only retry on connection errors and 5xx responses, NOT on 4xx.
```

### 1.10 Externalize Docker Compose Credentials

**Gap:** 4.1 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Move all hardcoded credentials in docker-compose/docker-compose.yml and
docker-compose/docker-compose-support-apps.yml to a .env file:

1. Create docker-compose/.env.example with placeholder values.
2. Update docker-compose.yml to use ${MYSQL_ROOT_PASSWORD}, ${KC_DB_PASSWORD},
   ${KEYCLOAK_ADMIN_PASSWORD} etc.
3. Add docker-compose/.env to .gitignore.
4. Update README.md with instructions to copy .env.example to .env.
```

---

## Phase 2: Important Structural Improvements (3-6 weeks)

These items require more effort but are necessary for production readiness and long-term maintainability.

### 2.1 Extract Shared Library

**Gap:** 1.2 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a shared library module (e.g., banking-common) that contains the duplicated
code across services:

1. Create a new Gradle module 'banking-common' with the following classes:
   - exception/ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - configuration/filter/AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - model/dto/AuditAware
   - configuration/audit/AuditConfig, AuditorAwareConfig
   - model/mapper/BaseMapper
2. Publish it as a local Maven artifact or use Gradle composite builds.
3. Update each service's build.gradle to depend on banking-common.
4. Remove the duplicated classes from each service.
5. Ensure all services compile and tests pass.
```

### 2.2 Set Up Multi-Project Gradle Build

**Gap:** 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Convert the project to a Gradle multi-project build:

1. Create a root settings.gradle that includes all 6 service subprojects
   plus the banking-common module.
2. Create a root build.gradle with shared configuration:
   - Java 21 toolchain
   - Spring Boot 3.2.4 and Spring Cloud 2023.0.0 version catalogs
   - Common repositories (mavenCentral)
   - Shared test configuration (useJUnitPlatform)
3. Simplify each service's build.gradle to only declare service-specific
   dependencies.
4. Add a Gradle version catalog (libs.versions.toml) for centralized
   dependency version management.
5. Verify all services build with './gradlew build' from root.
```

### 2.3 Add Circuit Breakers

**Gap:** 7.1, 7.4 | **Severity:** Critical/High | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies to fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
2. Enable Feign circuit breaker: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback in fund-transfer that returns a
     FundTransferResponse with status FAILED and an error message.
   - BankingCoreRestClientFallback in utility-payment with similar behavior.
   - BankingCoreRestClientFallback in user-service that throws a
     ServiceUnavailableException.
4. Configure circuit breaker parameters:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
5. Add integration tests that verify fallback behavior.
```

### 2.4 Add Comprehensive Unit Tests

**Gap:** 3.1, 3.2 | **Severity:** Critical/High | **Effort:** Large

**Devin Prompt:**
```
Add unit tests to achieve at least 80% code coverage for all business services:

For internet-banking-user-service:
1. UserServiceTest: test createUser (success, duplicate email, invalid email,
   user not found in core banking), readUsers, readUser, updateUser (approve flow).
2. KeycloakUserServiceTest: mock KeycloakManager and test all operations.
3. UserControllerTest: @WebMvcTest tests for all 4 endpoints with MockMvc.

For internet-banking-fund-transfer-service:
1. FundTransferServiceTest: test fundTransfer (success, core banking failure),
   readAllTransfers.
2. FundTransferControllerTest: @WebMvcTest for both endpoints.

For internet-banking-utility-payment-service:
1. UtilityPaymentServiceTest: test utilPayment (success, failure), readPayments.
2. UtilityPaymentControllerTest: @WebMvcTest for both endpoints.

Use Mockito for mocking, AssertJ for assertions. Configure test profiles
with H2 databases.
```

### 2.5 Add Feign Error Decoding

**Gap:** 2.4 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add proper Feign error decoding to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service:

1. Create a CustomFeignErrorDecoder (like user-service already has) that:
   - Parses the ErrorResponse JSON from core-banking-service responses.
   - Maps 404 to EntityNotFoundException.
   - Maps 422 to InsufficientFundsException.
   - Maps 5xx to ServiceUnavailableException.
   - Maps unknown errors to a generic service exception.
2. Register it in CustomFeignClientConfiguration.
3. Update GlobalExceptionHandler to handle these mapped exceptions.
4. Add unit tests for the error decoder.
```

### 2.6 Add Idempotency Keys

**Gap:** 7.5 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment endpoints:

1. Add an optional X-Idempotency-Key header to POST endpoints.
2. Create an idempotency_key table in each service's database with columns:
   key (unique), response (JSON), created_at, expires_at.
3. Before processing, check if the key exists. If so, return the cached response.
4. After processing, store the response with the key.
5. Add a scheduled job to clean up expired keys (e.g., 24 hours).
6. Add tests for idempotent and non-idempotent requests.
```

### 2.7 Fix Pagination Responses

**Gap:** 5.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update all list/paginated endpoints to return pagination metadata:

1. Create a PageResponse<T> wrapper DTO in the shared library:
   { content: List<T>, page: int, size: int, totalElements: long,
     totalPages: int, last: boolean }
2. Update service methods to return Page<T> instead of List<T>.
3. Update controllers to wrap in PageResponse.
4. Apply to: core-banking UserController.readUsers,
   user-service UserController.readUsers,
   fund-transfer FundTransferController.readFundTransfers,
   utility-payment UtilityPaymentController.readPayments.
```

### 2.8 Add Optimistic Locking to Account Entities

**Gap:** 7.6 (partial) | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add optimistic locking to BankAccountEntity in core-banking-service to prevent
concurrent modification:

1. Add @Version private Long version; field to BankAccountEntity.
2. Add a Flyway migration to add the version column with default value 0.
3. Handle OptimisticLockException in TransactionService by retrying or
   returning an appropriate error.
4. Add a concurrent transfer test that verifies no money is lost.
```

### 2.9 Add Correlation ID to Logs and Responses

**Gap:** 6.6 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add trace/correlation ID propagation to log output and error responses:

1. Configure logback-spring.xml in each service to include traceId and spanId
   in the log pattern using Micrometer's MDC integration:
   %d{yyyy-MM-dd HH:mm:ss} [%X{traceId}/%X{spanId}] %-5level %logger - %msg%n
2. Include traceId in ErrorResponse: add a 'traceId' field populated from MDC.
3. This enables correlating user-reported errors to Zipkin traces.
```

### 2.10 Add Rate Limiting to API Gateway

**Gap:** 4.6 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's
RequestRateLimiter filter:

1. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
2. Add a Redis service to docker-compose.yml.
3. Configure rate limiter:
   - Global: 100 requests/second per IP.
   - Registration endpoint: 5 requests/minute per IP.
4. Configure the RedisRateLimiter bean with appropriate replenish rate and
   burst capacity.
5. Add integration tests.
```

---

## Phase 3: Polish (6-12 weeks)

Longer-term improvements for production-grade operations and developer experience.

### 3.1 Add Integration Tests with Testcontainers

**Gap:** 3.3 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for all services:

1. Add Testcontainers dependencies: testcontainers, mysql (or postgresql for keycloak).
2. For core-banking-service: @SpringBootTest with MySQL Testcontainer,
   test full CRUD operations on accounts, users, and transactions.
3. For user-service: @SpringBootTest with MySQL + Keycloak Testcontainers,
   test user registration end-to-end.
4. For fund-transfer and utility-payment: @SpringBootTest with MySQL +
   WireMock for core-banking-service responses.
5. Fix context load tests to use test profiles that don't require
   external Config Server or Eureka.
```

### 3.2 Add Consumer-Driven Contract Tests

**Gap:** 3.4 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. In core-banking-service (producer): add spring-cloud-contract-verifier
   and define contracts for all Feign client endpoints.
2. In fund-transfer and utility-payment (consumers): add
   spring-cloud-contract-stub-runner and write consumer tests that
   verify Feign clients work against the generated stubs.
3. Configure CI to fail if contracts are broken.
```

### 3.3 Add Structured JSON Logging

**Gap:** 6.2 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add structured JSON logging to all services:

1. Add logstash-logback-encoder dependency to each service.
2. Create logback-spring.xml with JSON encoder for non-local profiles
   and console encoder for local/dev profiles.
3. Include standard fields: timestamp, level, logger, message, traceId,
   spanId, service name.
4. Remove any System.out.println or raw print statements if found.
```

### 3.4 Add Custom Business Metrics

**Gap:** 6.4 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Add custom Micrometer metrics to business services:

1. In core-banking-service TransactionService:
   - Counter: banking.fund_transfer.total (tags: status=success|failed)
   - Counter: banking.utility_payment.total (tags: status=success|failed)
   - Timer: banking.fund_transfer.duration
   - Gauge: banking.account.balance (per account type)
2. In user-service:
   - Counter: banking.user.registration.total (tags: status=success|failed)
3. In fund-transfer and utility-payment:
   - Counter: banking.transfer.requests (tags: status)
   - Timer: banking.transfer.processing_time
4. Verify metrics appear at /actuator/metrics and /actuator/prometheus.
```

### 3.5 Add Dependency Vulnerability Scanning

**Gap:** 4.7 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency-Check to the Gradle build:

1. Add plugin: id 'org.owasp.dependencycheck' version '9.0.9' to root build.gradle.
2. Configure: dependencyCheck { failBuildOnCVSS = 7 }
3. Add a CI step that runs './gradlew dependencyCheckAnalyze'.
4. Review and address any critical/high vulnerabilities found.
```

### 3.6 Implement Saga Pattern for Distributed Transactions

**Gap:** 7.7 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Implement a choreography-based Saga pattern for fund transfers:

1. Add RabbitMQ dependency and Spring AMQP to fund-transfer and core-banking services.
2. Fund Transfer Service publishes a FundTransferRequested event.
3. Core Banking Service consumes it, processes the transfer, and publishes
   FundTransferCompleted or FundTransferFailed.
4. Fund Transfer Service updates its local record based on the response event.
5. Add compensation logic: if the local record update fails after receiving
   FundTransferCompleted, publish a CompensateFundTransfer event.
6. Add dead letter queues for failed message processing.
7. Add integration tests with embedded RabbitMQ.
```

### 3.7 Document and Standardize API Naming Conventions

**Gap:** 5.2, 5.3, 5.5 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize API naming across all services:

1. Remove verbs from URLs:
   - /api/v1/bank-users/register -> POST /api/v1/bank-users
   - /api/v1/bank-users/update/{id} -> PATCH /api/v1/bank-users/{id}
2. Use consistent naming:
   - /api/v1/account/util-account -> /api/v1/account/utility-account
3. Use hyphens consistently in path variables:
   - {account_number} -> {accountNumber} (or keep underscores but be consistent)
4. Document the versioning strategy in a new docs/API_CONVENTIONS.md.
5. Add @Parameter annotations to document Pageable query params.
6. Update Feign clients to match the new paths.
```

### 3.8 Add Health Check Details

**Gap:** 6.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Configure detailed health checks for all services:

1. In each service's application.yml, expose health details:
   management.endpoint.health.show-details: when-authorized
   management.health.db.enabled: true
   management.health.diskSpace.enabled: true
2. For user-service, add a custom Keycloak health indicator.
3. For services with Feign clients, add health indicators that ping
   core-banking-service.
4. Expose the health endpoint through the API Gateway for monitoring.
```

### 3.9 Fix Context Load Tests

**Gap:** 3.5 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix the default context load tests in all services so they pass without
external dependencies:

1. Add src/test/resources/application.yml to each service with:
   - H2 in-memory database config
   - Eureka client disabled (eureka.client.enabled=false)
   - Config server disabled (spring.cloud.config.enabled=false)
   - Keycloak properties mocked (for user-service)
2. For user-service, mock the KeycloakManager bean in test config.
3. Verify all context load tests pass with: ./gradlew test
```

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| **Phase 1: Quick Wins** | 10 items | 4 Critical fixes (balance bug, error handling, validation, credentials) | 1-2 weeks |
| **Phase 2: Important** | 10 items | 2 Critical fixes (circuit breakers, locking) + structural improvements | 3-6 weeks |
| **Phase 3: Polish** | 9 items | Production readiness and developer experience | 6-12 weeks |

### Recommended Execution Order Within Each Phase

**Phase 1 priority order:**
1. Fix double-deduction balance bug (1.1) - data corruption risk
2. Externalize credentials (1.10) - security risk
3. Stop logging passwords (1.4) - compliance risk
4. Fix error handling (1.2) - affects all API consumers
5. Add input validation (1.3) - prevents invalid data
6. Fix Keycloak thread safety (1.5)
7. Add Feign timeouts (1.8) and retries (1.9)
8. Fix ResponseEntity types (1.6) and Swagger dep (1.7)

**Phase 2 priority order:**
1. Add circuit breakers (2.3) - prevents cascading failures
2. Add optimistic locking (2.8) - prevents concurrent data corruption
3. Add Feign error decoding (2.5)
4. Add comprehensive unit tests (2.4)
5. Extract shared library (2.1) and multi-project build (2.2)
6. Remaining items in any order
