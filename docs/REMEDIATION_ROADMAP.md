# Remediation Roadmap

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Created:** 2026-05-07
> **Based on:** [Gap Analysis](./GAP_ANALYSIS.md)

---

## Phasing Strategy

| Phase | Focus | Timeline | Criteria |
|-------|-------|----------|----------|
| **Phase 1 — Quick Wins** | Critical bugs, security holes, small-effort items | 1-2 weeks | Severity Critical/High + Effort Small |
| **Phase 2 — Important** | Structural improvements, testing, resilience | 3-6 weeks | High/Medium severity + Medium effort |
| **Phase 3 — Polish** | Best-practice alignment, long-term quality | 6-12 weeks | Lower severity or Large effort items |

---

## Phase 1: Quick Wins (1-2 weeks)

### 1.1 Fix Double-Subtraction Balance Bug
**Gap:** GAP-RES-06 | **Severity:** Critical | **Effort:** Small

Fix the `availableBalance` calculation in `TransactionService.utilPayment()` and `internalFundTransfer()` where the amount is subtracted twice from the available balance.

**Files:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`

<details>
<summary>Devin Prompt</summary>

```
Fix the double-subtraction bug in TransactionService in core-banking-service.

In utilPayment(), line 64 sets availableBalance to actualBalance minus amount, but
actualBalance was already reduced on line 63. The availableBalance ends up double-debited.
The same pattern exists in internalFundTransfer() at lines 91 and 100.

Fix: after subtracting from actualBalance, set availableBalance equal to the new
actualBalance (not actualBalance minus amount again). Update the existing unit tests
in TransactionServiceTest to assert correct balance values after transfers and payments.
```

</details>

---

### 1.2 Fix Transaction-Account Relationship
**Gap:** GAP-RES-07 | **Severity:** Medium | **Effort:** Small

Change `@OneToOne(cascade = CascadeType.ALL)` to `@ManyToOne` on `TransactionEntity.account` and remove the dangerous cascade.

**Files:** `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`

<details>
<summary>Devin Prompt</summary>

```
In core-banking-service, fix the TransactionEntity JPA mapping. The `account` field
uses @OneToOne(cascade = CascadeType.ALL) but should be @ManyToOne (an account has
many transactions). Remove CascadeType.ALL to prevent accidental account deletion.
Change to @ManyToOne with no cascade. Verify the existing tests still pass.
```

</details>

---

### 1.3 Stop Leaking Exception Details to Clients
**Gap:** GAP-ERR-02 | **Severity:** Critical | **Effort:** Small

Replace the generic `Exception` handler in all four `GlobalExceptionHandler` classes to return a safe error response instead of `"Exception occur inside API " + e`.

**Files:**
- `core-banking-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-user-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-fund-transfer-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-utility-payment-service/.../exception/GlobalExceptionHandler.java`

<details>
<summary>Devin Prompt</summary>

```
In all four business services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service),
update the GlobalExceptionHandler's generic Exception handler.

Currently it returns: "Exception occur inside API " + e
This leaks internal details.

Change it to return an ErrorResponse with:
- code: "INTERNAL_ERROR"
- message: "An internal error occurred. Please try again later."
- HTTP status: 500 Internal Server Error (not 400)

Also log the full exception at ERROR level with log.error("Unhandled exception", e)
so it's available for debugging.
```

</details>

---

### 1.4 Map Correct HTTP Status Codes to Exceptions
**Gap:** GAP-ERR-01 | **Severity:** High | **Effort:** Small

Add dedicated exception handlers for `EntityNotFoundException` (404), `InsufficientFundsException` (422), `UserAlreadyRegisteredException` (409), and `InvalidEmailException` (400).

**Files:** All four `GlobalExceptionHandler.java` files

<details>
<summary>Devin Prompt</summary>

```
In all four GlobalExceptionHandler classes across the business services, add specific
@ExceptionHandler methods for each custom exception type with correct HTTP status codes:

- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidBankingUserException -> 404 Not Found
- InvalidEmailException -> 400 Bad Request

Each handler should return the standard ErrorResponse { code, message } format.
Keep the SimpleBankingGlobalException handler as a fallback for 400.
Make sure to import all needed exception classes in each handler.
```

</details>

---

### 1.5 Fix Keycloak Singleton Thread Safety
**Gap:** GAP-SEC-05 | **Severity:** High | **Effort:** Small

Replace the lazy singleton in `KeycloakProperties` with a proper Spring `@Bean` definition.

**Files:** `internet-banking-user-service/.../configuration/keycloak/KeycloakProperties.java`

<details>
<summary>Devin Prompt</summary>

```
In internet-banking-user-service, refactor the Keycloak client initialization in
KeycloakProperties.java.

Currently it uses a non-thread-safe lazy singleton pattern with a static field.
Replace it with a @Bean method that creates the Keycloak instance once as a
Spring-managed singleton. You can either:
1. Add a @Bean method in a @Configuration class that creates the Keycloak instance, OR
2. Use @PostConstruct to initialize the instance once

Remove the static mutable field and the null-check pattern.
```

</details>

---

### 1.6 Add Input Validation to All Request DTOs
**Gap:** GAP-SEC-02 | **Severity:** Critical | **Effort:** Medium

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller method parameters.

**Files:** All request DTO classes and controller classes across all services

<details>
<summary>Devin Prompt</summary>

```
Add Jakarta Bean Validation to all request DTOs across the microservices.

1. In core-banking-service FundTransferRequest:
   - @NotBlank on fromAccount, toAccount
   - @NotNull @Positive on amount

2. In core-banking-service UtilityPaymentRequest:
   - @NotNull on providerId
   - @NotNull @Positive on amount
   - @NotBlank on referenceNumber, account

3. In internet-banking-fund-transfer-service FundTransferRequest:
   - @NotBlank on fromAccount, toAccount
   - @NotNull @Positive on amount

4. In internet-banking-utility-payment-service UtilityPaymentRequest:
   - @NotNull on providerId
   - @NotNull @Positive on amount
   - @NotBlank on referenceNumber, account

5. In internet-banking-user-service User DTO:
   - @NotBlank @Email on email
   - @NotBlank on identification
   - @NotBlank @Size(min=8) on password

6. Add @Valid annotation to all @RequestBody parameters in all controllers.

7. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler
   that returns 400 with field-level error details.

Add spring-boot-starter-validation to each service's build.gradle if not already present.
```

</details>

---

### 1.7 Restrict Actuator Endpoint Exposure
**Gap:** GAP-SEC-06 | **Severity:** Medium | **Effort:** Small

Limit publicly accessible actuator endpoints to only `/health` and `/info` in the gateway security configuration.

**Files:** `internet-banking-api-gateway/.../configuration/security/SecurityConfiguration.java`

<details>
<summary>Devin Prompt</summary>

```
In the API Gateway's SecurityConfiguration.java, restrict actuator endpoint access.

Currently all actuator endpoints are publicly accessible via permitAll().
Change the gateway security to only permit:
- /actuator/health and /actuator/info (and their service-prefixed equivalents)

All other actuator endpoints should require authentication.

Update the pathMatchers to be specific:
  .pathMatchers("/actuator/health", "/actuator/info").permitAll()
  .pathMatchers("/user/actuator/health", "/user/actuator/info").permitAll()
  // ... same for other service prefixes
```

</details>

---

### 1.8 Add Feign Client Timeouts
**Gap:** GAP-RES-03 | **Severity:** High | **Effort:** Small

Configure explicit connection and read timeouts for all Feign clients.

**Files:** Application configuration (YAML or Feign configuration classes)

<details>
<summary>Devin Prompt</summary>

```
Add explicit timeout configuration for all Feign clients in the microservices.

In each service that uses Feign (user-service, fund-transfer-service,
utility-payment-service), add the following to application.yml:

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
            read-timeout: 15000

This ensures that if core-banking-service is slow or unresponsive, the calling
service will fail fast after 15 seconds instead of waiting indefinitely.
```

</details>

---

### 1.9 Add Raw Type Parameters to ResponseEntity
**Gap:** GAP-ERR-04 | **Severity:** Low | **Effort:** Small

Add type parameters to all `ResponseEntity` return types across controllers.

**Files:** All controller classes

<details>
<summary>Devin Prompt</summary>

```
Across all controller classes in all services, add proper generic type parameters
to ResponseEntity return types. For example:

- ResponseEntity -> ResponseEntity<BankAccount>
- ResponseEntity -> ResponseEntity<List<User>>
- ResponseEntity -> ResponseEntity<FundTransferResponse>

Fix raw type usage in:
- core-banking-service: AccountController, UserController, TransactionController
- internet-banking-fund-transfer-service: FundTransferController
- internet-banking-utility-payment-service: UtilityPaymentController
```

</details>

---

### 1.10 Add Dependency Vulnerability Scanning
**Gap:** GAP-SEC-07 | **Severity:** Medium | **Effort:** Small

Add the OWASP Dependency Check Gradle plugin to detect known vulnerabilities.

<details>
<summary>Devin Prompt</summary>

```
Add the OWASP Dependency Check Gradle plugin to the project.

Since there's no root build.gradle, add the plugin to each service's build.gradle:

plugins {
    id 'org.owasp.dependencycheck' version '9.0.10'
}

dependencyCheck {
    failBuildOnCVSS = 7
    suppressionFile = "${rootDir}/owasp-suppressions.xml"
}

Create a shared owasp-suppressions.xml in the repo root (can be empty initially).
Verify the plugin works by running ./gradlew dependencyCheckAnalyze in one service.
```

</details>

---

## Phase 2: Important (3-6 weeks)

### 2.1 Externalize Credentials from Source Control
**Gap:** GAP-SEC-01 | **Severity:** Critical | **Effort:** Medium

Move all hardcoded credentials to `.env` files or Docker secrets.

<details>
<summary>Devin Prompt</summary>

```
Externalize all hardcoded credentials from the repository.

1. Create a docker-compose/.env.example file with placeholder values:
   MYSQL_ROOT_PASSWORD=changeme
   MYSQL_APP_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   KEYCLOAK_DB_PASSWORD=changeme

2. Update docker-compose.yml and docker-compose-support-apps.yml to use
   ${VARIABLE} references instead of hardcoded values.

3. Update docker-compose/mysql/Dockerfile to use ARG/ENV from compose.

4. Update docker-compose/mysql/privileges.sql to be a template or use
   environment variables.

5. Add docker-compose/.env to .gitignore.

6. Remove hardcoded credentials from README.md (reference .env.example instead).

7. Add documentation in README.md explaining how to set up the .env file.
```

</details>

---

### 2.2 Implement Role-Based Authorization
**Gap:** GAP-SEC-03 | **Severity:** High | **Effort:** Medium

Add role-based access control for admin operations and resource ownership verification.

<details>
<summary>Devin Prompt</summary>

```
Implement role-based access control across the banking microservices.

1. In the Keycloak realm configuration, define roles: ROLE_ADMIN, ROLE_USER.

2. In the API Gateway SecurityConfiguration:
   - Add role extraction from JWT claims
   - Restrict admin endpoints (e.g., user approval) to ROLE_ADMIN

3. In internet-banking-user-service:
   - PATCH /update/{id} should require ROLE_ADMIN
   - GET /bank-users (list all) should require ROLE_ADMIN

4. In fund-transfer and utility-payment services:
   - Verify the X-Auth-Id matches the account owner before processing transfers
   - Add an ownership check by calling core-banking-service to verify the account
     belongs to the authenticated user

5. Add @PreAuthorize or gateway-level route predicates for role enforcement.
```

</details>

---

### 2.3 Create Shared Library Module
**Gap:** GAP-ORG-02 | **Severity:** High | **Effort:** Medium

Extract duplicated code into a shared module.

<details>
<summary>Devin Prompt</summary>

```
Create a shared library module called banking-common for the microservices project.

1. Create a new Gradle subproject banking-common/ with:
   - BaseMapper interface
   - AuditAware base class
   - AuditConfig and AuditorAwareConfig
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler (base)
   - GlobalErrorCode enum

2. Create a root settings.gradle that includes all subprojects.

3. Create a root build.gradle with shared configuration in subprojects {}.

4. Update each service's build.gradle to depend on banking-common:
   implementation project(':banking-common')

5. Remove the duplicated classes from each service and import from banking-common.

6. Verify all services compile and tests pass.
```

</details>

---

### 2.4 Add Circuit Breakers to Feign Clients
**Gap:** GAP-RES-01 | **Severity:** High | **Effort:** Medium

Add Resilience4j circuit breakers to all Feign client calls.

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j circuit breakers to all Feign clients in the microservices.

1. Add dependencies to fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breakers in application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Configure circuit breaker parameters:
   resilience4j:
     circuitbreaker:
       instances:
         core-banking-service:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 10000
           permittedNumberOfCallsInHalfOpenState: 3

4. Create fallback classes for each Feign client that return appropriate error
   responses when the circuit is open.

5. Add the fallback to the @FeignClient annotation:
   @FeignClient(name = "core-banking-service", fallback = BankingCoreFeignClientFallback.class)
```

</details>

---

### 2.5 Add Unit Tests for All Services
**Gap:** GAP-TEST-01 | **Severity:** High | **Effort:** Large

Write unit tests for user-service, fund-transfer-service, and utility-payment-service.

<details>
<summary>Devin Prompt</summary>

```
Add comprehensive unit tests for the three business services that currently lack them.

1. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid NIC,
     email mismatch), readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: test createUser, readUser, readUserByEmail, updateUser
   - UserControllerTest: MockMvc tests for all 4 endpoints

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (success, core service error),
     readAllTransfers
   - FundTransferControllerTest: MockMvc tests for POST and GET

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (success, core service error),
     readPayments
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET

For each service, add a test application.yml with H2, disabled Eureka client,
and disabled Config Server bootstrap. Use Mockito for mocking Feign clients
and Keycloak dependencies.

Target: at least 80% line coverage on service and controller layers.
```

</details>

---

### 2.6 Add Pagination Metadata to List Responses
**Gap:** GAP-API-02 | **Severity:** Medium | **Effort:** Small

Return `Page<T>` or a wrapper DTO with pagination metadata instead of raw `List<T>`.

<details>
<summary>Devin Prompt</summary>

```
Update all paginated list endpoints to return pagination metadata.

1. Create a generic PagedResponse<T> DTO in each service (or in banking-common):
   {
     content: List<T>,
     pageNumber: int,
     pageSize: int,
     totalElements: long,
     totalPages: int,
     last: boolean
   }

2. Update service methods to return Page<T> instead of List<T>.

3. Update controllers to map Page<T> to PagedResponse<T>.

4. Affected endpoints:
   - GET /api/v1/user (core-banking)
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/transfer (fund-transfer-service)
   - GET /api/v1/utility-payment (utility-payment-service)
```

</details>

---

### 2.7 Implement Structured JSON Logging
**Gap:** GAP-OBS-01 | **Severity:** Medium | **Effort:** Small

Configure Logback to output structured JSON logs with trace context.

<details>
<summary>Devin Prompt</summary>

```
Add structured JSON logging to all microservices.

1. Add logstash-logback-encoder dependency to each service:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a shared logback-spring.xml in each service's resources/:
   - Use LogstashEncoder for JSON output
   - Include traceId and spanId from MDC
   - Mask sensitive fields (account numbers, amounts) in log patterns

3. Remove toString() calls on request objects in log statements to avoid
   logging sensitive data. Use specific field logging instead:
   log.info("Fund transfer from={} to={}", request.getFromAccount(), request.getToAccount())

4. Add a log correlation filter that logs the request method, path, and
   response status for each API call.
```

</details>

---

### 2.8 Add Prometheus Metrics Endpoint
**Gap:** GAP-OBS-03 | **Severity:** Medium | **Effort:** Small

Add `micrometer-registry-prometheus` and enable the Prometheus actuator endpoint.

<details>
<summary>Devin Prompt</summary>

```
Add Prometheus metrics support to all microservices.

1. Add to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Ensure the prometheus actuator endpoint is exposed in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics

3. Add custom metrics for key business operations:
   - Counter for fund transfers (success/failure)
   - Counter for utility payments (success/failure)
   - Timer for core-banking-service response times
   - Gauge for active fund transfers in PENDING state

4. Verify /actuator/prometheus returns metrics in Prometheus exposition format.
```

</details>

---

### 2.9 Add Retry Policies for Feign Clients
**Gap:** GAP-RES-02 | **Severity:** Medium | **Effort:** Small

Configure retry policies with exponential backoff for transient failures.

<details>
<summary>Devin Prompt</summary>

```
Add retry policies for Feign clients in fund-transfer, utility-payment, and user services.

1. Add Spring Retry dependency:
   implementation 'org.springframework.retry:spring-retry'

2. Configure Resilience4j retry in application.yml:
   resilience4j:
     retry:
       instances:
         core-banking-service:
           maxAttempts: 3
           waitDuration: 500ms
           enableExponentialBackoff: true
           exponentialBackoffMultiplier: 2
           retryExceptions:
             - java.io.IOException
             - feign.RetryableException

3. Important: Do NOT retry fund transfer or payment POST requests (not idempotent).
   Only retry GET requests (account lookups, user lookups).

4. Add unit tests verifying retry behavior with mock failures.
```

</details>

---

### 2.10 Set Up Multi-Project Gradle Build
**Gap:** GAP-ORG-01 | **Severity:** Medium | **Effort:** Small

Create root `settings.gradle` and `build.gradle` for unified builds.

<details>
<summary>Devin Prompt</summary>

```
Create a multi-project Gradle build for the microservices repository.

1. Create a root settings.gradle:
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
       // shared dependency management
   }

3. Move common plugin versions and dependency declarations to the root build.

4. Verify `./gradlew build` from the root builds all services.

5. Update README with the new build command.
```

</details>

---

## Phase 3: Polish (6-12 weeks)

### 3.1 Implement Saga Pattern for Distributed Transactions
**Gap:** GAP-RES-05 | **Severity:** Critical | **Effort:** Large

Implement compensation logic for cross-service transaction failures.

<details>
<summary>Devin Prompt</summary>

```
Implement the Saga pattern for the fund transfer flow to handle distributed
transaction failures.

1. Add a transactional outbox table to fund-transfer-service:
   CREATE TABLE outbox_event (
     id BIGINT PRIMARY KEY AUTO_INCREMENT,
     aggregate_type VARCHAR(255),
     aggregate_id VARCHAR(255),
     event_type VARCHAR(255),
     payload TEXT,
     created_at TIMESTAMP,
     processed BOOLEAN DEFAULT FALSE
   );

2. When a fund transfer is initiated:
   a. Save FundTransferEntity with PENDING status
   b. Save an outbox event with the transfer details
   c. A scheduled job polls the outbox and calls core-banking-service

3. If core-banking-service succeeds:
   a. Update FundTransferEntity to SUCCESS
   b. Mark outbox event as processed

4. If core-banking-service fails:
   a. Update FundTransferEntity to FAILED
   b. No compensation needed (money was never moved)

5. If the service crashes after core-banking succeeds but before updating status:
   a. The outbox poller will retry and detect (via idempotency key) that the
      transfer was already completed
   b. Update status to SUCCESS

6. Add an idempotency key (UUID) to FundTransferRequest that core-banking-service
   checks to prevent duplicate processing.

7. Apply the same pattern to utility-payment-service.
```

</details>

---

### 3.2 Add Integration Tests with Testcontainers
**Gap:** GAP-TEST-02 | **Severity:** High | **Effort:** Large

Write integration tests that verify real database and Feign interactions.

<details>
<summary>Devin Prompt</summary>

```
Add integration tests using Testcontainers for each business service.

1. Add test dependencies to each service:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
   testImplementation 'org.springframework.cloud:spring-cloud-contract-wiremock'

2. For core-banking-service:
   - Test fund transfer end-to-end with real MySQL (Testcontainers)
   - Test utility payment end-to-end
   - Verify Flyway migrations run correctly

3. For fund-transfer-service:
   - Use WireMock to simulate core-banking-service responses
   - Test success and failure scenarios
   - Verify database state after each operation

4. For utility-payment-service:
   - Use WireMock for core-banking-service
   - Test payment processing with various scenarios

5. For user-service:
   - Use WireMock for core-banking-service
   - Mock Keycloak admin client
   - Test registration and approval flows

6. Create a test base class with shared Testcontainers setup.
```

</details>

---

### 3.3 Add Contract Tests
**Gap:** GAP-TEST-03 | **Severity:** Medium | **Effort:** Large

Implement consumer-driven contract tests between services.

<details>
<summary>Devin Prompt</summary>

```
Add Spring Cloud Contract tests for the core-banking-service API.

1. Add Spring Cloud Contract dependencies to core-banking-service:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'

2. Write contract definitions in core-banking-service/src/test/resources/contracts/:
   - fundTransfer.groovy: defines request/response for POST /api/v1/transaction/fund-transfer
   - utilPayment.groovy: defines request/response for POST /api/v1/transaction/util-payment
   - readAccount.groovy: defines request/response for GET /api/v1/account/bank-account/{number}
   - readUser.groovy: defines request/response for GET /api/v1/user/{identification}

3. Generate contract stubs JAR from core-banking-service.

4. In fund-transfer-service and utility-payment-service, add:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side tests that use the generated stubs to verify
   Feign client compatibility.

6. Add contract verification to the CI pipeline.
```

</details>

---

### 3.4 Add Fallback Behavior for Service Degradation
**Gap:** GAP-RES-04 | **Severity:** Medium | **Effort:** Medium

Implement graceful degradation when downstream services are unavailable.

<details>
<summary>Devin Prompt</summary>

```
Add fallback behavior for all Feign clients when core-banking-service is unavailable.

1. For fund-transfer-service:
   - When circuit is open, save transfer with QUEUED status
   - Return response indicating the transfer is queued for processing
   - Add a scheduled job that retries queued transfers when circuit closes

2. For utility-payment-service:
   - Similar queuing mechanism for payments
   - Return a "payment queued" response to the client

3. For user-service:
   - When core-banking-service is down during registration, return a clear
     error message: "Banking core service is temporarily unavailable"
   - Do not create partial records in Keycloak

4. Add health indicators that reflect the circuit breaker state.

5. Add tests for each fallback scenario.
```

</details>

---

### 3.5 Standardize REST API Naming Conventions
**Gap:** GAP-API-04 | **Severity:** Low | **Effort:** Small

Align endpoint paths with RESTful conventions.

<details>
<summary>Devin Prompt</summary>

```
Standardize API endpoint naming across all services to follow RESTful conventions.

Proposed changes (keep old endpoints as deprecated aliases for backward compatibility):

Core Banking Service:
  /api/v1/account/bank-account/{number} -> /api/v1/accounts/{number}
  /api/v1/account/util-account/{name} -> /api/v1/utility-accounts/{name}
  /api/v1/user/{identification} -> /api/v1/users/{identification}
  /api/v1/transaction/fund-transfer -> /api/v1/transactions/fund-transfers
  /api/v1/transaction/util-payment -> /api/v1/transactions/utility-payments

User Service:
  /api/v1/bank-users/register -> POST /api/v1/users
  /api/v1/bank-users/update/{id} -> PATCH /api/v1/users/{id}

Fund Transfer Service:
  /api/v1/transfer -> /api/v1/fund-transfers

Utility Payment Service:
  /api/v1/utility-payment -> /api/v1/utility-payments

Update the API Gateway routes and Feign clients accordingly.
Update the Postman collection.
```

</details>

---

### 3.6 Add Custom Health Indicators
**Gap:** GAP-OBS-02 | **Severity:** Low | **Effort:** Small

Add health checks for critical dependencies.

<details>
<summary>Devin Prompt</summary>

```
Add custom health indicators for critical service dependencies.

1. In core-banking-service:
   - DatabaseHealthIndicator (verify MySQL connectivity beyond default)

2. In user-service:
   - KeycloakHealthIndicator: check Keycloak server reachability
   - CoreBankingServiceHealthIndicator: check Feign client connectivity

3. In fund-transfer-service:
   - CoreBankingServiceHealthIndicator: verify core service is reachable

4. In utility-payment-service:
   - CoreBankingServiceHealthIndicator: same as above

5. Each health indicator should return UP/DOWN with details:
   @Component
   public class KeycloakHealthIndicator implements HealthIndicator {
       @Override
       public Health health() {
           // check Keycloak reachability
           return Health.up().withDetail("url", keycloakUrl).build();
       }
   }

6. Verify /actuator/health shows all custom indicators.
```

</details>

---

### 3.7 Add Filtering and Sorting to List Endpoints
**Gap:** GAP-API-03 | **Severity:** Low | **Effort:** Medium

Add query parameter-based filtering for business-relevant attributes.

<details>
<summary>Devin Prompt</summary>

```
Add filtering support to all list/search endpoints.

1. Fund Transfer Service GET /api/v1/transfer:
   - Filter by status (PENDING, SUCCESS, FAILED)
   - Filter by fromAccount or toAccount
   - Filter by date range (createdAfter, createdBefore)

2. Utility Payment Service GET /api/v1/utility-payment:
   - Filter by status
   - Filter by providerId
   - Filter by date range

3. User Service GET /api/v1/bank-users:
   - Filter by status (PENDING, APPROVED)

4. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering.

5. Add Swagger documentation for all filter parameters.

6. Add tests for each filter combination.
```

</details>

---

### 3.8 Configure Aggregated OpenAPI Documentation
**Gap:** GAP-API-05 | **Severity:** Low | **Effort:** Small

Set up gateway-level aggregated Swagger UI.

<details>
<summary>Devin Prompt</summary>

```
Configure aggregated OpenAPI documentation accessible through the API Gateway.

1. Verify springdoc-openapi is working in each business service by accessing
   /v3/api-docs on each service.

2. In the API Gateway, add springdoc routing configuration:
   springdoc:
     swagger-ui:
       urls:
         - name: Core Banking Service
           url: /banking-core/v3/api-docs
         - name: User Service
           url: /user/v3/api-docs
         - name: Fund Transfer Service
           url: /fund-transfer/v3/api-docs
         - name: Utility Payment Service
           url: /utility-payment/v3/api-docs

3. Ensure the Swagger UI is accessible at the gateway: http://localhost:8082/swagger-ui.html

4. Add API descriptions, contact info, and license to each service's OpenAPI config.
```

</details>

---

### 3.9 Standardize Package Structure Across Services
**Gap:** GAP-ORG-03 | **Severity:** Low | **Effort:** Small

Align package naming conventions.

<details>
<summary>Devin Prompt</summary>

```
Standardize the Java package structure across all business services.

Target structure for each service:
  com.javatodev.finance/
    ├── configuration/        (Spring beans, filters, security)
    │   ├── audit/
    │   ├── feign/
    │   └── filter/
    ├── controller/           (REST controllers)
    ├── exception/            (exception classes, handler)
    ├── model/
    │   ├── dto/              (data transfer objects)
    │   │   ├── request/
    │   │   └── response/
    │   ├── entity/           (JPA entities)
    │   └── mapper/           (entity <-> DTO mappers)
    ├── repository/           (Spring Data repositories)
    └── service/              (business logic)
        └── rest/             (Feign clients)

Move classes to match this structure in:
- user-service: model.repository -> repository, model.rest.response -> model.dto.response
- utility-payment-service: model.rest.request -> model.dto.request, model.rest.response -> model.dto.response

Update all imports accordingly and verify compilation.
```

</details>

---

### 3.10 Use MapStruct for Type-Safe Mapping
**Gap:** GAP-ORG-04 | **Severity:** Low | **Effort:** Small

Replace manual mappers with compile-time MapStruct mappers.

<details>
<summary>Devin Prompt</summary>

```
Replace manual mapper classes with MapStruct across all services.

1. Add MapStruct dependencies to each service's build.gradle:
   implementation 'org.mapstruct:mapstruct:1.5.5.Final'
   annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'

2. For core-banking-service, convert:
   - BankAccountMapper -> @Mapper interface with methods
   - UtilityAccountMapper -> @Mapper interface
   - UserMapper -> @Mapper interface

3. For fund-transfer-service:
   - FundTransferMapper -> @Mapper interface

4. For utility-payment-service:
   - UtilityPaymentMapper -> @Mapper interface

5. For user-service:
   - UserMapper -> @Mapper interface

6. Remove BaseMapper abstract class.

7. Update service classes to inject the generated mapper implementations.

8. Verify all existing tests still pass.
```

</details>

---

## Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| Phase 1 (Quick Wins) | 10 | 3 | 3 | 3 | 1 |
| Phase 2 (Important) | 10 | 1 | 3 | 5 | 1 |
| Phase 3 (Polish) | 10 | 1 | 1 | 2 | 6 |
| **Total** | **30** | **5** | **7** | **10** | **8** |

### Recommended Execution Order Within Phases

**Phase 1 priority order:**
1. GAP-RES-06: Fix double-subtraction bug (data corruption)
2. GAP-RES-07: Fix @OneToOne relationship (data integrity)
3. GAP-ERR-02: Stop leaking exception details (security)
4. GAP-SEC-02: Add input validation (security)
5. GAP-ERR-01: Map correct HTTP status codes
6. GAP-SEC-05: Fix Keycloak singleton
7. GAP-RES-03: Add Feign timeouts
8. GAP-SEC-06: Restrict actuator endpoints
9. GAP-SEC-07: Add vulnerability scanning
10. GAP-ERR-04: Fix raw ResponseEntity types

**Phase 2 priority order:**
1. GAP-SEC-01: Externalize credentials
2. GAP-SEC-03: Implement authorization
3. GAP-RES-01: Add circuit breakers
4. GAP-ORG-02: Create shared library
5. GAP-TEST-01: Add unit tests
6. GAP-OBS-01: Structured logging
7. GAP-OBS-03: Prometheus metrics
8. GAP-API-02: Pagination metadata
9. GAP-RES-02: Retry policies
10. GAP-ORG-01: Multi-project build
