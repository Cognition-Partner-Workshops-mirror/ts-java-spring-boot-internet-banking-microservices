# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): Low-effort, high-impact fixes that immediately improve security, correctness, and developer experience.
- **Phase 2 — Important** (3-6 weeks): Medium-effort improvements that strengthen resilience, testing, and code quality.
- **Phase 3 — Polish** (6-12 weeks): Larger structural improvements for long-term maintainability and operational excellence.

---

## Phase 1: Quick Wins

These items are small effort and address critical or high-severity gaps.

### 1.1 Add Request Body Validation (Gap 2.5 / 4.2)

**Severity: Critical | Effort: Medium**

Add `spring-boot-starter-validation` to all business services and annotate DTOs with Jakarta Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.). Add `@Valid` to all `@RequestBody` parameters.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Jakarta Bean Validation to all microservices in the internet-banking project:

1. Add `spring-boot-starter-validation` dependency to the build.gradle of: core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service.

2. Add validation annotations to all request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount and toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber and account
   - User (user-service): @NotBlank @Email on email, @NotBlank on identification, @NotBlank on password

3. Add @Valid annotation to all @RequestBody parameters in all controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns a 400 response with field-level error details using the ErrorResponse format.

5. Run the existing tests to make sure nothing breaks.
```
</details>

### 1.2 Fix HTTP Status Codes in Error Handlers (Gap 2.1 / 2.2)

**Severity: High | Effort: Small**

Update all `GlobalExceptionHandler` classes to return appropriate HTTP status codes and never leak internal exception details.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix error handling in all GlobalExceptionHandler classes across all services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service):

1. Map exceptions to correct HTTP status codes:
   - EntityNotFoundException → 404 Not Found
   - InsufficientFundsException → 422 Unprocessable Entity
   - UserAlreadyRegisteredException → 409 Conflict
   - InvalidEmailException → 400 Bad Request
   - InvalidBankingUserException → 400 Bad Request
   - Generic Exception → 500 Internal Server Error

2. Replace the generic Exception handler's plain string response with a structured ErrorResponse that does NOT include the exception message or stack trace. Use a generic message like "An unexpected error occurred. Please try again later." and log the actual exception server-side.

3. Make sure all handlers return ResponseEntity<ErrorResponse> consistently.

4. Run the existing tests to verify nothing breaks.
```
</details>

### 1.3 Fix springdoc Dependency (Gap 5.6)

**Severity: Medium | Effort: Small**

Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in core-banking-service, user-service, fund-transfer-service, and utility-payment-service.

<details>
<summary>Sample Devin Prompt</summary>

```
In the internet-banking project, replace the incorrect springdoc dependency in all four business service build.gradle files.

Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

The services affected are: core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service.

The API Gateway should keep the webflux variant since it uses Spring WebFlux, but it doesn't currently have springdoc, so no changes needed there.
```
</details>

### 1.4 Add Typed ResponseEntity Generics (Gap 5.1)

**Severity: Medium | Effort: Small**

Add generic type parameters to all `ResponseEntity` return types in controllers.

<details>
<summary>Sample Devin Prompt</summary>

```
Add generic type parameters to all ResponseEntity return types in all controllers across the internet-banking project. For example:

- AccountController.getBankAccount → ResponseEntity<BankAccount>
- AccountController.getUtilityAccount → ResponseEntity<UtilityAccount>
- TransactionController.fundTransfer → ResponseEntity<FundTransferResponse>
- TransactionController.utilPayment → ResponseEntity<UtilityPaymentResponse>
- UserController (core-banking) readUser → ResponseEntity<User>, readUsers → ResponseEntity<List<User>>
- FundTransferController.sendFundTransfer → ResponseEntity<FundTransferResponse>, readFundTransfers → ResponseEntity<List<FundTransfer>>
- UtilityPaymentController.processPayment → ResponseEntity<UtilityPaymentResponse>, readPayments → ResponseEntity<List<UtilityPayment>>

The user-service UserController already has typed ResponseEntity — leave it as is.
```
</details>

### 1.5 Fix Logging String Concatenation Bug (Gap 6.1)

**Severity: Medium | Effort: Small**

Fix the logging bug in `FundTransferService.fundTransfer()` and ensure consistent logging across all services.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix logging issues across the internet-banking project:

1. In internet-banking-fund-transfer-service FundTransferService.java line 32, fix:
   log.info("Sending fund transfer request {}" + request.toString())
   Change to:
   log.info("Sending fund transfer request {}", request)

2. Add @Slf4j annotation to UtilityPaymentController if missing.

3. Review all log statements across all services and ensure they use parameterized logging (SLF4J `{}` placeholders) instead of string concatenation.
```
</details>

### 1.6 Fix Keycloak Singleton Thread Safety (Gap 4.6)

**Severity: Medium | Effort: Small**

Make the Keycloak instance creation thread-safe.

<details>
<summary>Sample Devin Prompt</summary>

```
Fix the thread-unsafe Keycloak singleton in internet-banking-user-service KeycloakProperties.java.

Replace the current lazy initialization with a thread-safe approach. Either:
- Use synchronized keyword on the getInstance() method, or
- Use a @Bean method in a @Configuration class to create the Keycloak instance as a proper Spring singleton bean (preferred approach).

The preferred solution: Create a KeycloakConfig @Configuration class that provides a @Bean Keycloak instance built from the @Value properties. Remove the static keycloakInstance field. Inject the Keycloak bean into KeycloakManager instead of calling KeycloakProperties.getInstance().
```
</details>

### 1.7 Add Dependency Vulnerability Scanning (Gap 4.7)

**Severity: Medium | Effort: Small**

Add the OWASP Dependency-Check Gradle plugin to all services.

<details>
<summary>Sample Devin Prompt</summary>

```
Add the OWASP dependency-check Gradle plugin to all services in the internet-banking project:

1. Add the plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.10'
   }

2. Configure it to fail the build on CVSS score >= 7:
   dependencyCheck {
       failBuildOnCVSS = 7.0f
   }

3. Run `./gradlew dependencyCheckAnalyze` in core-banking-service to verify it works.
```
</details>

### 1.8 Externalize Hardcoded Credentials (Gap 4.1)

**Severity: Critical | Effort: Small**

Replace hardcoded passwords in Docker Compose with environment variable references.

<details>
<summary>Sample Devin Prompt</summary>

```
Externalize all hardcoded credentials in the internet-banking project:

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace all hardcoded passwords with ${VARIABLE:-default} syntax
   - MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
   - KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
   - KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
   - POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}

2. Create a docker-compose/.env.example file with all required variables and placeholder values.

3. Add docker-compose/.env to .gitignore.

4. In docker-compose/mysql/Dockerfile, replace the hardcoded ENV with an ARG:
   ARG MYSQL_ROOT_PASSWORD
   ENV MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}

5. Update privileges.sql to use a variable or document that the password should be changed.

6. Remove test credentials from README.md or move them to a separate, clearly-marked test data section with a warning.
```
</details>

---

## Phase 2: Important

These items require more effort and address resilience, testing, and architectural gaps.

### 2.1 Add Circuit Breakers and Retry Policies (Gap 7.1 / 7.2 / 7.3)

**Severity: Critical–High | Effort: Medium**

Add Resilience4j circuit breakers, retry policies, and timeout configuration to all Feign clients.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Resilience4j circuit breakers, retries, and timeouts to all Feign clients in the internet-banking project:

1. Add dependencies to fund-transfer-service, utility-payment-service, and user-service build.gradle files:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure Resilience4j in each service's application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 30s
           permittedNumberOfCallsInHalfOpenState: 3
     retry:
       instances:
         coreBankingService:
           maxAttempts: 3
           waitDuration: 1s
           retryExceptions:
             - java.io.IOException
             - feign.RetryableException
     timelimiter:
       instances:
         coreBankingService:
           timeoutDuration: 5s

3. Configure Feign timeouts in each service:
   feign:
     client:
       config:
         core-banking-service:
           connectTimeout: 3000
           readTimeout: 5000

4. Add @CircuitBreaker annotations to Feign client methods with fallback methods that handle graceful degradation.

5. Run existing tests to verify compatibility.
```
</details>

### 2.2 Add Fallback Behavior and Status Correction (Gap 7.4)

**Severity: High | Effort: Medium**

Ensure that when Feign calls fail, local records are updated to `FAILED` status instead of remaining stuck in `PENDING`/`PROCESSING`.

<details>
<summary>Sample Devin Prompt</summary>

```
Add fallback and error status handling to fund-transfer and utility-payment services:

1. In FundTransferService.fundTransfer():
   - Wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch
   - On exception: update the entity status to FAILED, save it, and re-throw or return an error response
   - Consider adding a FeignClient fallback factory that returns a response indicating the core service is unavailable

2. In UtilityPaymentService.utilPayment():
   - Same pattern: wrap bankingCoreRestClient.utilityPayment() in try-catch
   - On exception: update entity status to FAILED, save, and handle gracefully

3. Add a FAILED enum value to TransactionStatus if not already present.

4. Write unit tests for the failure scenarios.
```
</details>

### 2.3 Add Feign Error Decoders to All Services (Gap 2.4)

**Severity: High | Effort: Medium**

Add `CustomFeignErrorDecoder` to fund-transfer-service and utility-payment-service, matching the pattern in user-service.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Feign error decoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service:

1. Copy the CustomFeignErrorDecoder pattern from user-service's configuration/feign/CustomFeignErrorDecoder.java.

2. Create a CustomFeignErrorDecoder in each service that:
   - Parses the error response body from core-banking-service
   - Maps HTTP 400 → SimpleBankingGlobalException
   - Maps HTTP 404 → EntityNotFoundException
   - Maps HTTP 500 → a generic service unavailable exception
   - Throws appropriate exceptions instead of raw FeignException

3. Register the error decoder in each service's CustomFeignClientConfiguration.

4. Write unit tests for the error decoder.
```
</details>

### 2.4 Add Role-Based Authorization (Gap 4.5)

**Severity: High | Effort: Medium**

Configure Keycloak role mapping and enforce roles at the gateway and service level.

<details>
<summary>Sample Devin Prompt</summary>

```
Add role-based authorization to the internet-banking project:

1. In the API Gateway SecurityConfiguration:
   - Add role-based path matchers:
     exchanges.pathMatchers("/user/api/v1/bank-users/update/**").hasRole("ADMIN")
     exchanges.pathMatchers("/banking-core/**").hasRole("ADMIN")
   - Configure JWT role extraction from Keycloak's realm_access.roles claim

2. Add a JwtGrantedAuthoritiesConverter bean that maps Keycloak roles to Spring Security authorities:
   - Extract roles from the "realm_access.roles" claim in the JWT
   - Prefix them with "ROLE_"

3. Update the Keycloak realm-export.json to include at least two roles: ADMIN and USER.

4. Ensure the user registration endpoint remains public.

5. Document the role mapping in the README.
```
</details>

### 2.5 Add Unit Tests for All Business Services (Gap 3.1)

**Severity: Critical | Effort: Large**

Write comprehensive unit tests for user-service, fund-transfer-service, and utility-payment-service.

<details>
<summary>Sample Devin Prompt</summary>

```
Add comprehensive unit tests to the three internet-banking services that currently have no meaningful tests:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (happy path, email already registered, user not in core banking, email mismatch, Keycloak creation failure), readUsers, readUser, updateUser (approve flow, other status changes)
   - KeycloakUserServiceTest: test createUser, updateUser, readUserByEmail, readUser (happy path + not found)
   - UserControllerTest: use @WebMvcTest to test all endpoints

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (happy path, core banking failure, save failure), readAllTransfers
   - FundTransferControllerTest: use @WebMvcTest to test POST and GET endpoints

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (happy path, core banking failure), readPayments
   - UtilityPaymentControllerTest: use @WebMvcTest to test POST and GET endpoints

Use Mockito to mock dependencies (repositories, Feign clients, KeycloakUserService). Use @MockBean for controller tests. Target at least 80% line coverage for service classes.
```
</details>

### 2.6 Add Idempotency Protection (Gap 7.5)

**Severity: High | Effort: Medium**

Add idempotency key support to fund transfer and utility payment endpoints.

<details>
<summary>Sample Devin Prompt</summary>

```
Add idempotency key support to the fund transfer and utility payment services:

1. Add an optional "Idempotency-Key" request header to POST endpoints in FundTransferController and UtilityPaymentController.

2. Before processing a request:
   - Check if a record with the same idempotency key already exists in the database
   - If found with status SUCCESS, return the existing response (no re-processing)
   - If found with status PENDING/PROCESSING, return a 409 Conflict indicating the request is in progress
   - If not found, proceed normally

3. Add an "idempotencyKey" column to fund_transfer and utility_payment tables.

4. Add a unique constraint on the idempotencyKey column.

5. Write tests for duplicate request scenarios.
```
</details>

### 2.7 Add Rate Limiting to API Gateway (Gap 4.4)

**Severity: Medium | Effort: Medium**

Configure Spring Cloud Gateway's built-in rate limiting.

<details>
<summary>Sample Devin Prompt</summary>

```
Add rate limiting to the internet-banking-api-gateway:

1. Add Redis dependency for the rate limiter token bucket:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Configure a RequestRateLimiter filter in the gateway routes (in the Config Server's gateway configuration):
   - Default rate: 10 requests/second per user
   - Burst capacity: 20
   - Use the JWT subject claim as the key resolver

3. Add a KeyResolver bean that extracts the user identity from the JWT principal.

4. For the public registration endpoint, use IP-based rate limiting:
   - 5 requests/minute per IP

5. Add Redis to docker-compose.yml as a supporting service.

6. Document the rate limiting configuration in the README.
```
</details>

### 2.8 Return Pagination Metadata in List Endpoints (Gap 5.3)

**Severity: Medium | Effort: Small**

Wrap list responses in a pagination envelope.

<details>
<summary>Sample Devin Prompt</summary>

```
Add pagination response wrapper to all list endpoints in the internet-banking project:

1. Create a generic PageResponse<T> class (or put it in each service since there's no shared module):
   public class PageResponse<T> {
       private List<T> content;
       private int pageNumber;
       private int pageSize;
       private long totalElements;
       private int totalPages;
       private boolean last;
   }

2. Update all list endpoints to return PageResponse instead of List:
   - core-banking UserController.readUsers
   - fund-transfer FundTransferController.readFundTransfers
   - utility-payment UtilityPaymentController.readPayments
   - user-service UserController.readUsers

3. Use Spring Data's Page object to populate the metadata instead of calling .getContent() and discarding it.

4. Update any existing tests to expect the new response shape.
```
</details>

---

## Phase 3: Polish

These items are larger structural improvements for long-term maintainability.

### 3.1 Create Shared Library Module (Gap 1.2)

**Severity: High | Effort: Medium**

Extract duplicated code into a shared Gradle module.

<details>
<summary>Sample Devin Prompt</summary>

```
Create a shared library module for the internet-banking project to eliminate code duplication:

1. Create a new directory: internet-banking-common/
   - Add build.gradle with just the needed dependencies (Spring Boot starter, JPA, Lombok)
   - Add settings.gradle

2. Move these shared classes into the common module under package com.javatodev.finance.common:
   - exception/ErrorResponse
   - exception/SimpleBankingGlobalException
   - exception/GlobalExceptionHandler (as abstract base class)
   - exception/EntityNotFoundException
   - model/dto/AuditAware
   - model/mapper/BaseMapper
   - configuration/filter/ApiRequestContext
   - configuration/filter/ApiRequestContextHolder
   - configuration/filter/AppAuthUserFilter
   - configuration/audit/AuditConfig
   - configuration/audit/AuditorAwareConfig

3. Publish the common module as a local Gradle dependency.

4. Update all service build.gradle files to depend on the common module:
   implementation project(':internet-banking-common')

5. Remove the duplicated classes from each service.

6. Create a root settings.gradle that includes all projects.

7. Run all tests to verify nothing breaks.
```
</details>

### 3.2 Implement Multi-Project Gradle Build (Gap 1.1)

**Severity: Medium | Effort: Small**

Create a root Gradle build that ties all services together.

<details>
<summary>Sample Devin Prompt</summary>

```
Convert the internet-banking project to a Gradle multi-project build:

1. Create a root-level settings.gradle that includes all subprojects:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'

2. Create a root-level build.gradle with shared configuration:
   - Common repositories (mavenCentral)
   - Shared dependency versions via a version catalog or ext block
   - Common test configuration

3. Remove individual settings.gradle files from each subproject (they're no longer needed).

4. Verify that `./gradlew build` from the root builds all services.
5. Verify that `./gradlew test` from the root runs all tests.
```
</details>

### 3.3 Add Integration Tests (Gap 3.2)

**Severity: High | Effort: Large**

Add integration tests using Testcontainers for MySQL and WireMock for Feign client targets.

<details>
<summary>Sample Devin Prompt</summary>

```
Add integration tests to the internet-banking project using Testcontainers and WireMock:

1. Add test dependencies to all business service build.gradle files:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
   testImplementation 'org.wiremock:wiremock-standalone:3.5.4'

2. For core-banking-service:
   - Create AccountControllerIntegrationTest using @SpringBootTest + @AutoConfigureMockMvc + Testcontainers MySQL
   - Test: GET bank account (found + not found), GET utility account
   - Create TransactionControllerIntegrationTest
   - Test: POST fund-transfer (success, insufficient funds), POST util-payment

3. For fund-transfer-service:
   - Create FundTransferIntegrationTest using Testcontainers MySQL + WireMock for core-banking
   - Disable Eureka in test profile
   - Test: POST transfer (success, core-banking failure), GET transfers

4. For utility-payment-service:
   - Same pattern as fund-transfer

5. For user-service:
   - Use Testcontainers MySQL + WireMock for core-banking + mock Keycloak
   - Test: POST register (success, duplicate email, invalid identification), PATCH update, GET users

6. Configure a test Spring profile that disables Eureka and Config Server:
   spring.cloud.config.enabled: false
   eureka.client.enabled: false
```
</details>

### 3.4 Add Contract Tests Between Services (Gap 3.3)

**Severity: Medium | Effort: Large**

Add Spring Cloud Contract tests to verify API contracts between services.

<details>
<summary>Sample Devin Prompt</summary>

```
Add Spring Cloud Contract tests between the internet-banking services:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   Plugin: id 'org.springframework.cloud.contract' version '4.1.1'

2. Write contract definitions for core-banking-service endpoints in src/test/resources/contracts/:
   - GET /api/v1/account/bank-account/{number} → returns BankAccount
   - GET /api/v1/user/{identification} → returns User
   - POST /api/v1/transaction/fund-transfer → returns FundTransferResponse
   - POST /api/v1/transaction/util-payment → returns UtilityPaymentResponse

3. Generate and run contract tests (producer side).

4. Add contract stub dependencies to consumer services:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side tests in fund-transfer-service and utility-payment-service that use the generated stubs.
```
</details>

### 3.5 Implement Saga Pattern for Fund Transfers (Gap 7.6)

**Severity: Critical | Effort: Large**

Replace the current synchronous call chain with a saga pattern to handle partial failures.

<details>
<summary>Sample Devin Prompt</summary>

```
Implement a saga pattern for fund transfers in the internet-banking project to handle partial failures:

Option A (Orchestration-based saga):
1. In FundTransferService, implement a saga orchestrator:
   Step 1: Save local record (PENDING)
   Step 2: Call core-banking-service to debit source account
   Step 3: Call core-banking-service to credit destination account
   Step 4: Update local record (SUCCESS)
   
   Compensation:
   - If Step 3 fails: Call core-banking-service to reverse the debit (Step 2 compensation)
   - If Step 4 fails: The transaction is complete in core-banking; retry the local update

2. Add compensation endpoints to core-banking-service:
   POST /api/v1/transaction/reverse/{transactionId}

3. Add a scheduled job to detect stuck PENDING records and either retry or compensate.

Option B (Simpler alternative — Transactional Outbox):
1. Keep the current core-banking-service @Transactional fund transfer (atomic debit+credit)
2. Add proper error handling so fund-transfer-service correctly marks records as FAILED on exception
3. Add a retry mechanism for failed transfers

Choose Option B for a pragmatic improvement; document Option A as the ideal long-term solution.
```
</details>

### 3.6 Add Centralized Log Aggregation (Gap 6.4)

**Severity: Medium | Effort: Medium**

Add ELK stack or Loki to Docker Compose for centralized logging.

<details>
<summary>Sample Devin Prompt</summary>

```
Add centralized log aggregation to the internet-banking project using Loki + Grafana (lightweight alternative to ELK):

1. Add Loki and Grafana services to docker-compose.yml:
   loki:
     image: grafana/loki:2.9.0
     ports: [3100:3100]
   grafana:
     image: grafana/grafana:10.0.0
     ports: [3000:3000]
     depends_on: [loki]

2. Add Loki logback appender to all business services:
   - Add dependency: implementation 'com.github.loki4j:loki-logback-appender:1.5.1'
   - Add logback-spring.xml with Loki appender pointing to http://loki:3100/loki/api/v1/push
   - Include service name, traceId, and spanId as labels

3. Configure Grafana with Loki as a datasource (provision via grafana/provisioning/).

4. Add structured JSON logging format using logstash-logback-encoder.
```
</details>

### 3.7 Complete Prometheus Integration (Gap 6.3)

**Severity: Medium | Effort: Small**

Add Prometheus metrics registry and scrape configuration.

<details>
<summary>Sample Devin Prompt</summary>

```
Complete the Prometheus integration in the internet-banking project:

1. Add the Prometheus micrometer registry to all business service build.gradle files:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the Prometheus actuator endpoint in each service's configuration:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus

3. Add a Prometheus service to docker-compose.yml:
   prometheus:
     image: prom/prometheus:v2.51.0
     ports: [9090:9090]
     volumes:
       - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

4. Create docker-compose/prometheus/prometheus.yml with scrape configs for all services.

5. If Grafana was added in 3.6, add Prometheus as a datasource.
```
</details>

### 3.8 Add Custom Health Indicators (Gap 6.2)

**Severity: Low | Effort: Small**

Add health indicators for external dependencies.

<details>
<summary>Sample Devin Prompt</summary>

```
Add custom health indicators to internet-banking services:

1. In user-service, add a KeycloakHealthIndicator that checks Keycloak connectivity.

2. In all services using MySQL, verify the DataSource health indicator is active (it should be auto-configured by Spring Boot Actuator + JPA).

3. In fund-transfer-service and utility-payment-service, add a health indicator that verifies core-banking-service is reachable via the Eureka registry.

4. Configure health endpoint to show details:
   management:
     endpoint:
       health:
         show-details: always
```
</details>

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|---|---|---|---|
| **Phase 1: Quick Wins** | 8 items | Validation, credentials, error handling | 1-2 weeks |
| **Phase 2: Important** | 8 items | Circuit breakers, tests, authz, idempotency | 3-6 weeks |
| **Phase 3: Polish** | 8 items | Shared library, integration tests, sagas, observability | 6-12 weeks |

### Priority Order Within Each Phase

**Phase 1** (do in this order):
1. Request body validation (4.2/2.5) — prevents corrupt data entry
2. Externalize credentials (4.1) — security hygiene
3. Fix HTTP status codes (2.1/2.2) — correct API behavior
4. Fix springdoc dependency (5.6) — Swagger UI may be broken
5. Fix logging bug (6.1) — quick fix
6. Add ResponseEntity generics (5.1) — improves docs and type safety
7. Fix Keycloak thread safety (4.6) — prevents subtle race condition
8. Add dependency scanning (4.7) — baseline security

**Phase 2** (do in this order):
1. Circuit breakers + retries + timeouts (7.1/7.2/7.3) — prevents cascading failures
2. Fallback behavior (7.4) — prevents stuck records
3. Feign error decoders (2.4) — clean error propagation
4. Unit tests (3.1) — safety net for all changes
5. Role-based authorization (4.5) — security enforcement
6. Idempotency protection (7.5) — financial correctness
7. Rate limiting (4.4) — abuse prevention
8. Pagination metadata (5.3) — API completeness

**Phase 3** (do in this order):
1. Shared library module (1.2) — reduces maintenance burden
2. Multi-project Gradle build (1.1) — enables #1
3. Integration tests (3.2) — validates service interactions
4. Contract tests (3.3) — prevents API drift
5. Saga pattern (7.6) — handles distributed failures
6. Prometheus integration (6.3) — runtime visibility
7. Centralized logging (6.4) — debugging across services
8. Custom health indicators (6.2) — operational awareness
