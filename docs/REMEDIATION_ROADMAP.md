# Remediation Roadmap

## Overview

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): High-impact, low-effort fixes that immediately improve security, correctness, and reliability
- **Phase 2 — Important** (3-6 weeks): Structural improvements that require moderate effort but significantly raise engineering quality
- **Phase 3 — Polish** (6-12 weeks): Long-term investments in testing infrastructure, observability, and architectural patterns

Each item includes a sample Devin prompt that can be used to implement the fix.

---

## Phase 1: Quick Wins

*High-impact fixes that can be completed in small, focused PRs.*

### 1.1 Fix Balance Calculation Bug (Gap 7.6)

**Severity: Critical** | **Effort: Small** | **Priority: Immediate**

The `availableBalance` double-subtraction/addition bug in `TransactionService` causes incorrect account balances on every transaction.

**Files:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the balance calculation bug in core-banking-service TransactionService.

In internalFundTransfer():
- Line setting fromBankAccountEntity.setAvailableBalance() subtracts amount from the 
  already-reduced actualBalance (double subtraction). Change it to:
  fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())
- Same fix for toBankAccountEntity: availableBalance should equal the new actualBalance

In utilPayment():
- Same double-subtraction bug on fromAccount.setAvailableBalance(). Fix it to:
  fromAccount.setAvailableBalance(fromAccount.getActualBalance())

Update the existing unit tests in TransactionServiceTest to assert that actualBalance 
and availableBalance are equal after each operation. Add test assertions verifying 
the exact expected balances.
```

</details>

---

### 1.2 Fix HTTP Status Codes in Error Handlers (Gap 2.1)

**Severity: Critical** | **Effort: Small**

All exceptions incorrectly return HTTP 400. Fix across all 4 services.

**Files:** `*/exception/GlobalExceptionHandler.java` (4 files)

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the GlobalExceptionHandler in all 4 services (core-banking-service, 
internet-banking-user-service, internet-banking-fund-transfer-service, 
internet-banking-utility-payment-service) to return correct HTTP status codes:

1. EntityNotFoundException -> 404 Not Found
2. InsufficientFundsException -> 422 Unprocessable Entity
3. InvalidEmailException, InvalidBankingUserException, UserAlreadyRegisteredException -> 400 Bad Request
4. Generic Exception catch-all -> 500 Internal Server Error

Keep the structured ErrorResponse format for all responses. Do not change the 
ErrorResponse class structure.
```

</details>

---

### 1.3 Remove Exception Details from Client Responses (Gap 2.2)

**Severity: Critical** | **Effort: Small**

Stop leaking stack traces and internal details to API consumers.

**Files:** `*/exception/GlobalExceptionHandler.java` (4 files)

<details>
<summary>Sample Devin Prompt</summary>

```
In all 4 GlobalExceptionHandler classes across the microservices, update the generic 
Exception handler to:

1. Log the full exception at ERROR level: log.error("Unhandled exception", e)
2. Return a generic ErrorResponse with code "INTERNAL_ERROR" and message 
   "An unexpected error occurred. Please try again later."
3. Return HTTP 500 status code
4. Add @Slf4j annotation to the class if not present
5. Never include exception.toString() or stack trace in the response body
```

</details>

---

### 1.4 Add Bean Validation to Request DTOs (Gaps 2.3, 4.3)

**Severity: High** | **Effort: Small**

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Files:** All request DTO classes + all controller classes

<details>
<summary>Sample Devin Prompt</summary>

```
Add Jakarta Bean Validation to all request DTOs and controllers across the microservices:

1. Add spring-boot-starter-validation dependency to each service's build.gradle

2. core-banking-service FundTransferRequest:
   - @NotBlank on fromAccount, toAccount
   - @NotNull @DecimalMin("0.01") on amount

3. core-banking-service UtilityPaymentRequest:
   - @NotNull on providerId
   - @NotNull @DecimalMin("0.01") on amount
   - @NotBlank on referenceNumber, account

4. internet-banking-user-service User DTO (for registration):
   - @NotBlank @Email on email
   - @NotBlank on password, identification

5. Add @Valid annotation before every @RequestBody parameter in all controllers

6. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that 
   returns 400 with field-level error details in the ErrorResponse format

7. Do the same for the fund-transfer and utility-payment service request DTOs
```

</details>

---

### 1.5 Externalize Hardcoded Credentials (Gap 4.1)

**Severity: Critical** | **Effort: Small**

Move all credentials from version-controlled files to environment variables.

**Files:** `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, `docker-compose/mysql/privileges.sql`

<details>
<summary>Sample Devin Prompt</summary>

```
Externalize all hardcoded credentials in the Docker Compose files:

1. Create a docker-compose/.env.example file with placeholder values for:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_USER, MYSQL_APP_PASSWORD, 
   KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD

2. Update docker-compose.yml and docker-compose-support-apps.yml to reference 
   environment variables: ${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD}, etc.

3. Update privileges.sql to use a variable or document that the password should be 
   changed post-deployment

4. Add docker-compose/.env to .gitignore

5. Create docker-compose/.env with the current values for local development 
   (but add it to .gitignore so it's not committed)

6. Update README.md to document the new environment variable setup
```

</details>

---

### 1.6 Add Feign Timeout Configuration (Gap 7.3)

**Severity: High** | **Effort: Small**

Configure sensible timeouts for all Feign clients.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Feign client timeout configuration to the three services that use Feign 
(user-service, fund-transfer-service, utility-payment-service):

1. Add to each service's application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connectTimeout: 5000
               readTimeout: 10000
               loggerLevel: basic

2. For the fund-transfer service, set a shorter readTimeout of 5000ms for the 
   core-banking-service client since financial transactions should fail fast.

3. Verify each service still compiles after the change.
```

</details>

---

### 1.7 Add Retry Policies (Gap 7.2)

**Severity: High** | **Effort: Small**

Add Spring Retry for transient failures on Feign calls.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Spring Retry to the fund-transfer and utility-payment services:

1. Add spring-retry and spring-boot-starter-aop dependencies to build.gradle

2. Add @EnableRetry to the main application class of each service

3. Add @Retryable annotation to the Feign client methods in BankingCoreFeignClient 
   with:
   - maxAttempts = 3
   - backoff = @Backoff(delay = 1000, multiplier = 2)
   - retryFor = {FeignException.class}
   - noRetryFor = {FeignException.BadRequest.class, FeignException.NotFound.class}

4. Do NOT add retry to fund transfer POST operations (not idempotent). Only add 
   retry to GET operations (account lookups).
```

</details>

---

### 1.8 Fix OpenAPI/Swagger Configuration (Gap 5.6)

**Severity: Medium** | **Effort: Small**

Fix the incorrect Swagger dependency and add type parameters to ResponseEntity.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix OpenAPI/Swagger configuration across the microservices:

1. In core-banking-service, internet-banking-user-service, 
   internet-banking-fund-transfer-service, and internet-banking-utility-payment-service 
   build.gradle files:
   - Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' 
     with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
   (These are Spring MVC services, not WebFlux)

2. Add generic type parameters to all controller ResponseEntity return types:
   - ResponseEntity -> ResponseEntity<BankAccount>
   - ResponseEntity -> ResponseEntity<FundTransferResponse>
   etc.

3. Leave the API Gateway's dependency as-is since it uses WebFlux.
```

</details>

---

### 1.9 Fix Pagination to Return Page Metadata (Gap 5.4)

**Severity: Medium** | **Effort: Small**

Return page metadata instead of raw lists.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix pagination across all services to return page metadata:

1. In core-banking-service UserService.readUsers():
   - Change return type from List<User> to Page<User>
   - Return the mapped page instead of just .getContent()

2. Same fix in internet-banking-user-service UserService.readUsers()

3. Same fix in internet-banking-fund-transfer-service 
   FundTransferService.readAllTransfers()

4. Same fix in internet-banking-utility-payment-service 
   UtilityPaymentService.readPayments()

5. Update corresponding controller return types to ResponseEntity<Page<T>>

6. This ensures clients receive totalElements, totalPages, number, size metadata.
```

</details>

---

### 1.10 Add JaCoCo Test Coverage Reporting (Gap 3.4)

**Severity: Low** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

```
Add JaCoCo test coverage reporting to all microservices:

1. Add the JaCoCo plugin to each service's build.gradle:
   plugins {
       id 'jacoco'
   }

2. Configure JaCoCo:
   jacocoTestReport {
       dependsOn test
       reports {
           xml.required = true
           html.required = true
       }
   }

3. Add a minimum coverage threshold (start at 30% to not break the build):
   jacocoTestCoverageVerification {
       violationRules {
           rule {
               limit {
                   minimum = 0.30
               }
           }
       }
   }

4. Wire it up: test.finalizedBy jacocoTestReport
```

</details>

---

### 1.11 Standardize Logging and Remove Sensitive Data (Gap 6.1)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

```
Standardize logging across all microservices:

1. Remove logging of sensitive data in controllers:
   - FundTransferController: Don't log the full fundTransferRequest (contains account numbers)
   - Replace with: log.info("Fund transfer request received")
   - Same for UtilityPaymentController, UserController (don't log user passwords)

2. Add @Slf4j to UtilityPaymentController (missing)

3. Add consistent request logging to all controllers:
   - Log method entry with non-sensitive identifiers only
   - Log at DEBUG level for detailed info, INFO for operations

4. Configure logback-spring.xml in each service with:
   - JSON format for production profile
   - Console format for dev profile
   - Include traceId and spanId in log pattern
```

</details>

---

## Phase 2: Important

*Structural improvements requiring moderate effort with significant quality impact.*

### 2.1 Add Circuit Breakers (Gap 7.1)

**Severity: Critical** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Add Resilience4j circuit breakers to all Feign clients:

1. Add these dependencies to fund-transfer-service, utility-payment-service, and 
   user-service build.gradle:
   - 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breakers in each service's application.yml:
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

3. Add @CircuitBreaker annotations to Feign client calls in each service class:
   @CircuitBreaker(name = "coreBankingService", fallbackMethod = "fundTransferFallback")

4. Implement fallback methods that:
   - Log the error
   - Update the transaction status to FAILED
   - Return a meaningful error response

5. Enable Feign circuit breaker integration:
   spring.cloud.openfeign.circuitbreaker.enabled=true
```

</details>

---

### 2.2 Add Feign Error Handling (Gap 2.4)

**Severity: High** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Add proper Feign error handling to fund-transfer-service and utility-payment-service:

1. Create a CustomFeignErrorDecoder class in each service (similar to user-service):
   - Decode Feign error responses into appropriate application exceptions
   - Map HTTP 404 -> EntityNotFoundException
   - Map HTTP 422 -> InsufficientFundsException
   - Map HTTP 400 -> SimpleBankingGlobalException with the upstream error message
   - Map HTTP 5xx -> a new ServiceUnavailableException

2. Register the error decoder in CustomFeignClientConfiguration

3. Add try-catch blocks in FundTransferService.fundTransfer():
   - Catch exceptions from the Feign call
   - Update the FundTransferEntity status to FAILED
   - Save the error message/code
   - Re-throw a meaningful exception

4. Same pattern for UtilityPaymentService.utilPayment()

5. Add a FAILED value to the TransactionStatus enum if not present
```

</details>

---

### 2.3 Add Role-Based Access Control (Gap 4.5)

**Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Add role-based access control to the API Gateway and downstream services:

1. In SecurityConfiguration (API Gateway):
   - Add role-based matchers:
     .pathMatchers("/user/api/v1/bank-users/update/**").hasRole("ADMIN")
     .pathMatchers("/user/api/v1/bank-users").hasAnyRole("ADMIN", "USER")
     .pathMatchers("/fund-transfer/**").hasAnyRole("ADMIN", "USER")
     .pathMatchers("/utility-payment/**").hasAnyRole("ADMIN", "USER")
     .pathMatchers("/banking-core/**").hasRole("ADMIN")

2. Configure Keycloak JWT role extraction:
   - Add a custom ReactiveJwtAuthenticationConverter that maps Keycloak 
     realm_access.roles to Spring Security GrantedAuthority

3. Update the Keycloak realm export to include ADMIN and USER roles

4. Verify the Gateway correctly forwards role information
```

</details>

---

### 2.4 Extract Shared Library Module (Gap 1.2)

**Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Create a shared library module to eliminate code duplication:

1. Create a new module: banking-common/

2. Set up a root settings.gradle that includes all service modules

3. Move these shared classes to banking-common:
   - exception/GlobalExceptionHandler.java
   - exception/ErrorResponse.java
   - exception/SimpleBankingGlobalException.java
   - exception/EntityNotFoundException.java
   - exception/GlobalErrorCode.java
   - model/dto/AuditAware.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/CustomFeignClientConfiguration.java

4. Add banking-common as a dependency in each service's build.gradle:
   implementation project(':banking-common')

5. Remove the duplicated classes from each service

6. Update imports in all affected files

7. Verify all services compile and tests pass
```

</details>

---

### 2.5 Convert to Gradle Multi-Project Build (Gap 1.1)

**Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Convert the project to a Gradle multi-project build:

1. Create a root settings.gradle that includes all service modules:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'

2. Create a root build.gradle with:
   - Shared plugin versions in plugins { } block
   - subprojects { } block with common:
     - Java 21 sourceCompatibility
     - Spring Cloud BOM import
     - Common test configuration
     - JaCoCo plugin

3. Simplify each service's build.gradle to only declare service-specific dependencies

4. Verify: ./gradlew build from root builds all services
```

</details>

---

### 2.6 Add Custom Health Checks (Gap 6.2)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

```
Add custom health indicators to each service:

1. core-banking-service: Add a DatabaseHealthIndicator that checks MySQL connectivity 
   via a simple query

2. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the 
   Keycloak server URL

3. fund-transfer-service and utility-payment-service: Add a 
   CoreBankingServiceHealthIndicator that calls the core banking actuator/health endpoint

4. Expose detailed health info in application.yml:
   management:
     endpoint:
       health:
         show-details: always
         show-components: always

5. Ensure /actuator/health returns component-level status for each dependency
```

</details>

---

### 2.7 Fix Keycloak Client Singleton (Gap 4.4)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the Keycloak client singleton pattern in internet-banking-user-service:

1. In KeycloakProperties.java:
   - Remove the static keycloakInstance field
   - Convert getInstance() to a @Bean factory method that returns a Keycloak instance
   - Use @Configuration instead of @Component
   - Create the Keycloak bean as a Spring-managed singleton (thread-safe by default)

2. Update KeycloakManager to inject Keycloak directly instead of KeycloakProperties:
   @RequiredArgsConstructor
   public class KeycloakManager {
       private final Keycloak keycloak;
       private final String realm;
       
       public RealmResource getKeyCloakInstanceWithRealm() {
           return keycloak.realm(realm);
       }
   }

3. Create a KeycloakConfig @Configuration class that defines both beans

4. Update tests if any reference the old pattern
```

</details>

---

### 2.8 Add Dependency Vulnerability Scanning (Gap 4.6)

**Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

```
Add OWASP Dependency-Check to the Gradle build:

1. Add to the root build.gradle (or each service's build.gradle if no multi-project):
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure:
   dependencyCheck {
       failBuildOnCVSS = 9  // Only fail on critical vulnerabilities initially
       formats = ['HTML', 'JSON']
       suppressionFile = 'dependency-check-suppressions.xml'
   }

3. Create an empty suppression file for known false positives

4. Run ./gradlew dependencyCheckAnalyze and report findings

5. Add the dependency-check-report.html to .gitignore
```

</details>

---

### 2.9 Add Prometheus Metrics Endpoint (Gap 6.3)

**Severity: Low** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Add Prometheus metrics to all microservices:

1. Add micrometer-registry-prometheus dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the Prometheus endpoint in each service's application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics

3. Add custom business metrics in key service classes:
   - TransactionService: Counter for fund_transfers_total (tagged by status)
   - TransactionService: Counter for utility_payments_total (tagged by status)
   - UserService: Counter for user_registrations_total

4. Add a Prometheus scrape config example in docker-compose/prometheus.yml

5. Optionally add a Prometheus container to docker-compose.yml
```

</details>

---

## Phase 3: Polish

*Long-term investments for production-grade quality.*

### 3.1 Add Unit Tests for All Services (Gap 3.1)

**Severity: High** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

```
Add comprehensive unit tests for internet-banking-user-service:

1. Create UserServiceTest:
   - Mock KeycloakUserService, UserRepository, BankingCoreRestClient
   - Test createUser() happy path: user not in Keycloak, found in core banking, 
     email matches, Keycloak returns 201
   - Test createUser() with already registered email
   - Test createUser() with email mismatch
   - Test createUser() with user not found in core banking
   - Test readUsers() with pagination
   - Test readUser() with found and not-found cases
   - Test updateUser() with APPROVED status (verifies Keycloak update)
   - Test updateUser() with other status (no Keycloak call)

2. Create KeycloakUserServiceTest:
   - Mock KeycloakManager
   - Test createUser(), updateUser(), readUserByEmail(), readUser()
   - Test readUser() throwing EntityNotFoundException

3. Create UserControllerTest using MockMvc:
   - Test all 4 endpoints with valid/invalid inputs
   - Verify correct HTTP status codes
   - Verify response body structure
```

</details>

<details>
<summary>Sample Devin Prompt (Fund Transfer Service)</summary>

```
Add comprehensive unit tests for internet-banking-fund-transfer-service:

1. Create FundTransferServiceTest:
   - Mock FundTransferRepository and BankingCoreFeignClient
   - Test fundTransfer() happy path: saves PENDING, calls core banking, updates SUCCESS
   - Test fundTransfer() when Feign call fails (after error handling is added)
   - Test readAllTransfers() with pagination

2. Create FundTransferControllerTest using MockMvc:
   - Test POST /api/v1/transfer with valid request
   - Test POST /api/v1/transfer with invalid request (after validation added)
   - Test GET /api/v1/transfer pagination
```

</details>

<details>
<summary>Sample Devin Prompt (Utility Payment Service)</summary>

```
Add comprehensive unit tests for internet-banking-utility-payment-service:

1. Create UtilityPaymentServiceTest:
   - Mock UtilityPaymentRepository and BankingCoreRestClient
   - Test utilPayment() happy path
   - Test utilPayment() with Feign failure
   - Test readPayments() pagination

2. Create UtilityPaymentControllerTest using MockMvc:
   - Test POST /api/v1/utility-payment with valid request
   - Test GET /api/v1/utility-payment pagination
```

</details>

---

### 3.2 Add Integration Tests with Testcontainers (Gap 3.2)

**Severity: High** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

```
Add integration tests using Testcontainers for core-banking-service:

1. Add Testcontainers dependencies to build.gradle:
   testImplementation 'org.testcontainers:testcontainers:1.19.7'
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create a base test class CoreBankingIntegrationTest with:
   - @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
   - @Testcontainers
   - MySQL container that runs Flyway migrations
   - TestRestTemplate for HTTP calls

3. Write integration tests:
   - AccountControllerIT: GET /api/v1/account/bank-account/{number} returns seeded data
   - TransactionControllerIT: POST /api/v1/transaction/fund-transfer with real DB
   - Verify balance changes persist correctly in MySQL
   - Verify transaction records are created

4. Disable Eureka client for integration tests:
   eureka.client.enabled=false

5. Configure a test-specific application-test.yml profile
```

</details>

---

### 3.3 Add Contract Tests (Gap 3.3)

**Severity: Medium** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

```
Add Spring Cloud Contract tests between services:

1. Add Spring Cloud Contract to core-banking-service (producer):
   - Add spring-cloud-starter-contract-verifier plugin and dependency
   - Create contract files in src/test/resources/contracts/ for:
     * GET /api/v1/account/bank-account/{number} -> BankAccount response
     * GET /api/v1/user/{identification} -> User response
     * POST /api/v1/transaction/fund-transfer -> FundTransferResponse
     * POST /api/v1/transaction/util-payment -> UtilityPaymentResponse
   - Generate and run contract verification tests

2. Add Spring Cloud Contract to consumers (fund-transfer, utility-payment, user-service):
   - Add spring-cloud-contract-stub-runner dependency
   - Write @AutoConfigureStubRunner tests that verify Feign clients work 
     against the generated stubs
   - Use stubs-per-consumer mode

3. This ensures API changes in core-banking-service don't silently break consumers
```

</details>

---

### 3.4 Implement Saga Pattern for Financial Transactions (Gap 7.5)

**Severity: Critical** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

```
Implement a choreography-based saga pattern for fund transfers:

1. Add RabbitMQ dependency to fund-transfer-service and core-banking-service:
   implementation 'org.springframework.boot:spring-boot-starter-amqp'

2. Add RabbitMQ to docker-compose.yml

3. Redesign the fund transfer flow:
   a. Fund-transfer-service saves PENDING record
   b. Fund-transfer-service publishes FundTransferRequestedEvent to RabbitMQ
   c. Core-banking-service consumes the event, validates and executes the transfer
   d. Core-banking-service publishes FundTransferCompletedEvent or FundTransferFailedEvent
   e. Fund-transfer-service consumes the result event and updates status to SUCCESS or FAILED

4. Add idempotency:
   - Generate a unique idempotency key per transfer request
   - Core-banking-service checks if the transaction ID already exists before processing
   - This prevents duplicate transfers on retry

5. Add compensation:
   - If credit fails after debit, publish a CompensationEvent
   - Core-banking-service reverses the debit on compensation

6. This replaces the current synchronous Feign call pattern for transfers
```

</details>

---

### 3.5 Add Centralized Logging with ELK Stack (Gap 6.5)

**Severity: Low** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

```
Add centralized logging infrastructure using the ELK stack:

1. Add to docker-compose.yml:
   - Elasticsearch container
   - Logstash container with pipeline config
   - Kibana container (port 5601)

2. Add logstash-logback-encoder to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

3. Create logback-spring.xml in each service's resources:
   - Console appender for dev profile
   - Logstash TCP appender for docker profile
   - Include MDC fields: traceId, spanId, serviceName

4. Configure Kibana index patterns and a basic dashboard with:
   - Log volume by service
   - Error rate timeline
   - Request latency (from access logs)

5. Add correlation ID propagation via MDC filters
```

</details>

---

### 3.6 Add API Filtering and Search (Gap 5.5)

**Severity: Low** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

```
Add filtering and search capabilities to list endpoints:

1. core-banking-service UserController:
   - Add query params: ?email=, ?firstName=, ?lastName=
   - Use Spring Data JPA Specifications for dynamic filtering

2. core-banking-service - Add new Transaction list endpoint:
   - GET /api/v1/transaction?accountNumber=&type=&from=&to=
   - Support date range filtering
   - Return paginated results

3. fund-transfer-service:
   - GET /api/v1/transfer?status=&fromAccount=&toAccount=&from=&to=
   
4. utility-payment-service:
   - GET /api/v1/utility-payment?status=&providerId=&from=&to=

5. Use Spring Data JPA Specification pattern consistently across all services
```

</details>

---

## Summary Timeline

```
Phase 1 (Weeks 1-2): Quick Wins
├── 1.1  Fix balance calculation bug          [Day 1]
├── 1.2  Fix HTTP status codes                [Day 1]
├── 1.3  Remove exception details leak        [Day 1]
├── 1.4  Add bean validation                  [Day 2]
├── 1.5  Externalize credentials              [Day 2]
├── 1.6  Add Feign timeouts                   [Day 3]
├── 1.7  Add retry policies                   [Day 3]
├── 1.8  Fix Swagger configuration            [Day 3]
├── 1.9  Fix pagination                       [Day 4]
├── 1.10 Add JaCoCo coverage                  [Day 4]
└── 1.11 Standardize logging                  [Day 5]

Phase 2 (Weeks 3-6): Important
├── 2.1  Add circuit breakers                 [Week 3]
├── 2.2  Add Feign error handling             [Week 3]
├── 2.3  Add role-based access control        [Week 3]
├── 2.4  Extract shared library               [Week 4]
├── 2.5  Gradle multi-project build           [Week 4]
├── 2.6  Add custom health checks             [Week 5]
├── 2.7  Fix Keycloak singleton               [Week 5]
├── 2.8  Add dependency scanning              [Week 5]
└── 2.9  Add Prometheus metrics               [Week 6]

Phase 3 (Weeks 7-12): Polish
├── 3.1  Unit tests for all services          [Weeks 7-8]
├── 3.2  Integration tests (Testcontainers)   [Weeks 8-9]
├── 3.3  Contract tests                       [Week 9]
├── 3.4  Saga pattern for transactions        [Weeks 10-11]
├── 3.5  Centralized logging (ELK)            [Week 11]
└── 3.6  API filtering and search             [Week 12]
```

---

## Risk Considerations

| Risk | Mitigation |
|------|-----------|
| Balance bug is silently corrupting data in any environment using this code | Fix 1.1 should be deployed immediately; run a data audit query to identify affected accounts |
| Exception details leaking could expose internal architecture to attackers | Fix 1.3 is a security patch and should be prioritized |
| No circuit breakers means a single service failure cascades to all services | Fix 2.1 should be prioritized if the system is deployed to any non-local environment |
| Saga pattern (3.4) is a significant architectural change | Consider implementing it after circuit breakers and error handling are in place to reduce risk |
| Multi-project build (2.5) may conflict with existing CI/CD if present | Coordinate with the team before implementing |
