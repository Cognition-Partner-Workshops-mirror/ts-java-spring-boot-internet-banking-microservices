# Internet Banking Microservices — Remediation Roadmap

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins:** High-severity gaps with small effort. Immediate risk reduction.
- **Phase 2 — Important:** High-severity gaps with medium effort, plus medium-severity structural improvements.
- **Phase 3 — Polish:** Lower-severity improvements for long-term maintainability.

Each item includes a **sample Devin prompt** that can be used to execute the remediation directly.

---

## Phase 1 — Quick Wins (1–2 Sprints)

> High-severity/critical gaps that can be fixed with small effort. These address the most dangerous issues first.

### 1.1 Add Input Validation to All Request DTOs (GAP-14)

**Priority:** P0 — Financial data corruption risk

Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Min`) to all request DTOs and add `@Valid` to all `@RequestBody` parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation (jakarta.validation) to all request DTOs and controllers across
all services in the internet banking microservices project:

1. In core-banking-service:
   - FundTransferRequest: @NotBlank on fromAccount and toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
     @NotBlank on referenceNumber and account

2. In internet-banking-fund-transfer-service:
   - FundTransferRequest: @NotBlank on fromAccount and toAccount, @NotNull @Positive on amount

3. In internet-banking-utility-payment-service:
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
     @NotBlank on referenceNumber and account

4. In internet-banking-user-service:
   - User (registration): @NotBlank @Email on email, @NotBlank on identification and password

5. Add @Valid annotation to all @RequestBody parameters in all controllers.

6. Add spring-boot-starter-validation dependency to each service's build.gradle if not
   already present.

7. Update GlobalExceptionHandler in each service to handle MethodArgumentNotValidException
   and return HTTP 422 with structured field-level error messages.

8. Add unit tests to verify validation rejects invalid inputs.
```

### 1.2 Fix Error HTTP Status Codes (GAP-05)

**Priority:** P0 — Clients cannot distinguish error types

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all 4 application services (core-banking-service,
internet-banking-fund-transfer-service, internet-banking-user-service,
internet-banking-utility-payment-service) to return correct HTTP status codes:

1. EntityNotFoundException -> HTTP 404 Not Found
2. InsufficientFundsException -> HTTP 422 Unprocessable Entity
3. InvalidBankingUserException -> HTTP 404 Not Found
4. InvalidEmailException -> HTTP 400 Bad Request
5. UserAlreadyRegisteredException -> HTTP 409 Conflict
6. SimpleBankingGlobalException (generic) -> HTTP 400 Bad Request
7. Generic Exception catch-all -> HTTP 500 Internal Server Error

All error responses must use the structured ErrorResponse { code, message } format.
Remove the string concatenation of exception details in the generic handler.
Add unit tests for the exception handler.
```

### 1.3 Remove Internal Details from Error Responses (GAP-06)

**Priority:** P0 — Information disclosure vulnerability

**Devin Prompt:**
```
In all GlobalExceptionHandler classes across the internet banking microservices project,
update the generic Exception handler to:

1. Stop leaking exception details: replace the response body
   "Exception occur inside API " + e with a generic ErrorResponse containing
   code="INTERNAL_ERROR" and message="An unexpected error occurred. Please try again later."
2. Log the full exception details at ERROR level for debugging.
3. Return HTTP 500 instead of HTTP 400.
4. Ensure the ErrorResponse { code, message } format is used consistently.
```

### 1.4 Fix Inconsistent Error Response Format (GAP-07)

**Priority:** P1 — API consistency

This is addressed as part of items 1.2 and 1.3 above. Ensure all error responses use the `ErrorResponse { code, message }` format.

### 1.5 Add Feign Error Decoder to Fund Transfer and Utility Payment Services (GAP-08)

**Priority:** P1 — Proper Feign error propagation

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, modeled after the existing implementation
in internet-banking-user-service:

1. Copy the CustomFeignErrorDecoder and CustomFeignClientConfiguration pattern from
   internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/feign/
2. Adapt it for each service's package structure.
3. Register the error decoder in the Feign client configuration.
4. Map error status codes to appropriate exceptions:
   - 400 -> extract SimpleBankingGlobalException from response body
   - 404 -> EntityNotFoundException
   - 401/403 -> appropriate auth exception
   - 5xx -> service unavailable exception
5. Add unit tests for the error decoder.
```

### 1.6 Fix Hardcoded Credentials (GAP-16)

**Priority:** P0 — Secrets in version control

**Devin Prompt:**
```
Remove all hardcoded credentials from the internet banking microservices codebase and
replace them with environment variables:

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD}
   - Replace POSTGRES_PASSWORD value with ${POSTGRES_PASSWORD}
2. In docker-compose/mysql/privileges.sql:
   - Replace the hardcoded password with a variable or
     document that this file must be regenerated for production
3. Create a .env.example file in docker-compose/ with placeholder values
4. Add .env to .gitignore
5. Update README.md with instructions to create .env file from .env.example
6. Remove test credentials from README.md and reference the .env.example instead
```

### 1.7 Fix Wrong OpenAPI Dependency (GAP-21)

**Priority:** P1 — Swagger UI broken for MVC services

**Devin Prompt:**
```
In the internet banking microservices project, fix the OpenAPI/Swagger dependency for
all Spring MVC services. Replace the WebFlux dependency with the WebMVC dependency:

In build.gradle for core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, and internet-banking-utility-payment-service:

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

Verify the Swagger UI loads correctly at /swagger-ui.html for each service.
```

### 1.8 Fix Sensitive Data in Log Statements (GAP-27)

**Priority:** P1 — Data leakage in logs

**Devin Prompt:**
```
Audit all log statements in the internet banking microservices project and remove or
mask sensitive data:

1. In internet-banking-user-service UserController: "Creating user with {}" logs the
   full User DTO including password. Log only user email or identification.
2. In internet-banking-fund-transfer-service FundTransferService: "Sending fund transfer
   request {}" logs full request. Log only fromAccount last 4 digits and amount.
3. In internet-banking-utility-payment-service UtilityPaymentService: "Utility payment
   processing {}" logs full request. Log only provider ID and amount.
4. Override toString() in sensitive DTOs to mask sensitive fields, or create dedicated
   log-safe representations.
5. Ensure no account numbers, passwords, or personal identifiers appear in log output.
```

### 1.9 Add @Transactional to Orchestration Services (GAP-36)

**Priority:** P1 — Data consistency

**Devin Prompt:**
```
Add @Transactional annotations to the service methods in internet-banking-fund-transfer-service
and internet-banking-utility-payment-service:

1. In FundTransferService.fundTransfer(): Add @Transactional so the entity save and
   status update happen in a single database transaction.
2. In UtilityPaymentService.utilPayment(): Add @Transactional so the entity save and
   status update happen in a single database transaction.
3. Ensure the @EnableTransactionManagement is present on the application or config class.
4. Add the spring-boot-starter-data-jpa dependency if not already present (it includes
   transaction management).
5. Verify with a test that the entity status is rolled back if an exception occurs
   after the initial save.
```

### 1.10 Fix Keycloak Singleton Thread Safety (GAP-17)

**Priority:** P2 — Race condition under load

**Devin Prompt:**
```
Fix the thread-safety issue in KeycloakProperties.getInstance() in
internet-banking-user-service:

Option A (recommended): Use Spring @Bean to create the Keycloak instance as a
singleton bean managed by Spring's IoC container.

Option B: Add double-checked locking with volatile keyword to the static instance field.

Refactor KeycloakProperties to register a Keycloak @Bean in a @Configuration class.
Remove the static field pattern entirely. Inject the Keycloak bean into KeycloakManager.
```

### 1.11 Add Raw ResponseEntity Type Parameters (GAP-20)

**Priority:** P2 — Type safety and OpenAPI accuracy

**Devin Prompt:**
```
Add generic type parameters to all ResponseEntity return types in controllers across
the internet banking microservices:

1. core-banking-service:
   - AccountController.getBankAccount() -> ResponseEntity<BankAccount>
   - AccountController.getUtilityAccount() -> ResponseEntity<UtilityAccount>
   - TransactionController.fundTransfer() -> ResponseEntity<FundTransferResponse>
   - TransactionController.utilPayment() -> ResponseEntity<UtilityPaymentResponse>
   - UserController.readUser() -> ResponseEntity<User>
   - UserController.readUsers() -> ResponseEntity<List<User>>

2. internet-banking-fund-transfer-service:
   - FundTransferController.sendFundTransfer() -> ResponseEntity<FundTransferResponse>
   - FundTransferController.readFundTransfers() -> ResponseEntity<List<FundTransfer>>

3. internet-banking-utility-payment-service:
   - UtilityPaymentController.readPayments() -> ResponseEntity<List<UtilityPayment>>
   - UtilityPaymentController.processPayment() -> ResponseEntity<UtilityPaymentResponse>
```

### 1.12 Add Dependency Vulnerability Scanning (GAP-19)

**Priority:** P2 — Proactive security

**Devin Prompt:**
```
Add the OWASP Dependency-Check Gradle plugin to all services in the internet banking
microservices project:

1. Add to each build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '10.0.3'
   }
2. Configure the plugin to fail the build on CVSS score >= 7 (High).
3. Add a dependencyCheckAnalyze task to the build lifecycle.
4. Generate an HTML report in each service's build/reports directory.
5. Run the check and document any existing vulnerabilities found.
```

---

## Phase 2 — Important (2–4 Sprints)

> High/medium-severity gaps that require more implementation effort, plus structural improvements.

### 2.1 Add Error Handling and Compensation for Feign Failures (GAP-09)

**Priority:** P0 — Stuck transactions

**Devin Prompt:**
```
Add error handling and compensation logic to FundTransferService and
UtilityPaymentService in the internet banking microservices project:

1. Wrap Feign client calls in try-catch blocks.
2. On Feign failure:
   - Update entity status to FAILED (add FAILED to TransactionStatus enum)
   - Log the error with full context (transaction ID, accounts, amount)
   - Store the error message in a new 'failureReason' column
3. Add a scheduled job (@Scheduled) that retries FAILED transactions up to 3 times
   with exponential backoff.
4. After 3 retries, mark as PERMANENTLY_FAILED and alert (log at ERROR level).
5. Add a GET endpoint to query failed transactions for manual review.
6. Add unit and integration tests for the failure scenarios.
```

### 2.2 Add Circuit Breakers with Resilience4j (GAP-31)

**Priority:** P0 — Cascading failure prevention

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls in the internet banking
microservices project:

1. Add dependencies to build.gradle for fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breaker for each Feign client:
   - Failure rate threshold: 50%
   - Slow call rate threshold: 80%
   - Slow call duration: 3 seconds
   - Wait duration in open state: 30 seconds
   - Minimum number of calls: 5
   - Sliding window size: 10

3. Add fallback methods that return meaningful error responses when the circuit is open.

4. Add circuit breaker health indicator to actuator.

5. Add application.yml configuration for each circuit breaker instance.

6. Add tests to verify circuit breaker opens after threshold failures.
```

### 2.3 Add Authentication to Downstream Services (GAP-15)

**Priority:** P0 — Network-level access control bypass

**Devin Prompt:**
```
Add authentication to downstream microservices in the internet banking project so they
don't rely solely on the spoofable X-Auth-Id header:

Option A (recommended for internal services): Mutual TLS or shared secret token
1. Generate a shared internal API key for service-to-service communication.
2. Add a filter to each downstream service that validates the X-Internal-Auth header.
3. Configure Feign clients to include this header in all requests.
4. The gateway injects this header when proxying requests.

Option B: Propagate JWT to downstream services
1. Configure Feign clients to forward the Authorization header from the original request.
2. Add Spring Security OAuth2 Resource Server to each downstream service.
3. Configure JWT validation with the Keycloak JWK Set URI.

Implement Option A first as it's simpler for internal services. Document the auth
architecture in README.
```

### 2.4 Add Retry and Timeout Configuration for Feign Clients (GAP-32, GAP-33)

**Priority:** P1 — Resilience

**Devin Prompt:**
```
Configure Feign client retry policies and timeouts for all services in the internet
banking microservices project:

1. Add timeout configuration in application.yml for each service:
   spring:
     cloud:
       openfeign:
         client:
           config:
             core-banking-service:
               connect-timeout: 3000
               read-timeout: 5000

2. Configure retry policy with a custom Retryer bean:
   - Max attempts: 3
   - Initial interval: 200ms
   - Max interval: 1000ms
   - Only retry on connection exceptions and 5xx errors, NOT on 4xx

3. Ensure retries are safe (only for idempotent operations or with idempotency keys).

4. Add tests to verify timeout and retry behavior.
```

### 2.5 Add Idempotency Keys for Financial Transactions (GAP-35)

**Priority:** P0 — Duplicate transaction prevention

**Devin Prompt:**
```
Implement idempotency key support for financial transaction endpoints in the internet
banking microservices project:

1. Add an 'Idempotency-Key' header requirement to:
   - POST /api/v1/transfer (fund-transfer-service)
   - POST /api/v1/utility-payment (utility-payment-service)
   - POST /api/v1/transaction/fund-transfer (core-banking-service)
   - POST /api/v1/transaction/util-payment (core-banking-service)

2. Create an idempotency_key table in each service's schema:
   CREATE TABLE idempotency_key (
       key_value VARCHAR(36) PRIMARY KEY,
       response_body TEXT,
       response_status INT,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
       expires_at TIMESTAMP
   );

3. Add a filter/interceptor that:
   - Checks if the idempotency key exists in the table
   - If yes: return the cached response without processing
   - If no: process the request and store the response
   - If key is missing: return HTTP 400 with error message

4. Add a scheduled job to clean up expired idempotency keys (>24 hours old).

5. Add unit and integration tests.
```

### 2.6 Create Shared Library Module (GAP-01)

**Priority:** P1 — Code duplication elimination

**Devin Prompt:**
```
Create a shared library module (banking-common) for the internet banking microservices
project to eliminate code duplication:

1. Create a new Gradle module 'banking-common' with build.gradle containing shared
   dependencies (Spring Boot starters, Lombok, Jakarta Validation).

2. Move the following classes into banking-common:
   - BaseMapper (generic mapper)
   - AuditAware (JPA audit superclass)
   - AuditConfig + AuditorAwareConfig (JPA auditing config)
   - AppAuthUserFilter + ApiRequestContext + ApiRequestContextHolder (auth filter)
   - GlobalExceptionHandler (controller advice)
   - ErrorResponse (error DTO)
   - SimpleBankingGlobalException + EntityNotFoundException (exceptions)
   - GlobalErrorCode (error constants — merge codes from all services)

3. Publish banking-common to local Maven repository or use Gradle composite builds.

4. Update each service's build.gradle to depend on banking-common.

5. Remove duplicated classes from individual services.

6. Ensure all services still compile and pass tests.
```

### 2.7 Set Up CI Pipeline with GitHub Actions (GAP-13)

**Priority:** P1 — Automated quality gate

**Devin Prompt:**
```
Create a GitHub Actions CI pipeline for the internet banking microservices project:

1. Create .github/workflows/ci.yml with the following jobs:

   build-and-test:
     - Checkout code
     - Set up JDK 21
     - Build and test each service in parallel using a matrix strategy:
       services: [core-banking-service, internet-banking-fund-transfer-service,
                  internet-banking-user-service, internet-banking-utility-payment-service,
                  internet-banking-api-gateway, internet-banking-config-server,
                  internet-banking-service-registry]
     - Cache Gradle dependencies
     - Run: cd $service && ./gradlew build
     - Upload test reports as artifacts

2. Trigger on: push to main, pull_request to main

3. Add build status badge to README.md

4. Add branch protection rule requiring CI to pass before merge
```

### 2.8 Add Pagination Response Wrapper (GAP-23)

**Priority:** P2 — API usability

**Devin Prompt:**
```
Add a pagination response wrapper to all paginated endpoints in the internet banking
microservices project:

1. Create a generic PageResponse<T> class in banking-common (or each service if no
   shared library yet):
   public class PageResponse<T> {
       private List<T> content;
       private int page;
       private int size;
       private long totalElements;
       private int totalPages;
       private boolean first;
       private boolean last;
   }

2. Update all paginated GET endpoints to return PageResponse<T> instead of List<T>:
   - GET /api/v1/transfer -> PageResponse<FundTransfer>
   - GET /api/v1/utility-payment -> PageResponse<UtilityPayment>
   - GET /api/v1/bank-users -> PageResponse<User>
   - GET /api/v1/user -> PageResponse<User> (core-banking)

3. Map Spring's Page<Entity> to PageResponse<DTO> in service methods.

4. Update existing tests and add new ones for pagination metadata.
```

### 2.9 Add Structured JSON Logging (GAP-26)

**Priority:** P2 — Production readiness

**Devin Prompt:**
```
Add structured JSON logging to all services in the internet banking microservices project:

1. Add logstash-logback-encoder dependency to each build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a logback-spring.xml in each service's src/main/resources with:
   - Console appender with pattern layout for local development (dev profile)
   - JSON appender using LogstashEncoder for Docker/production (docker profile)
   - Include MDC fields: traceId, spanId, serviceName

3. Add MDC fields for business context:
   - Transaction ID for fund transfers and payments
   - User identification for user operations
   - Account number (masked) for account operations

4. Test that JSON logs are parseable and contain expected fields.
```

### 2.10 Add Unit Tests for All Services (GAP-10)

**Priority:** P1 — Code quality

**Devin Prompt:**
```
Add comprehensive unit tests for all application services in the internet banking
microservices project. Target 80% line coverage for service and controller layers.

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, Feign failure, validation
   - FundTransferControllerTest: test POST and GET endpoints with MockMvc

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success, Feign failure, validation
   - UtilityPaymentControllerTest: test POST and GET endpoints with MockMvc

3. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid NIC,
     Keycloak failure), readUsers, updateUser
   - KeycloakUserServiceTest: test createUser, readUser, updateUser
   - UserControllerTest: test all endpoints with MockMvc

4. Use Mockito for mocking dependencies.
5. Use @WebMvcTest for controller tests.
6. Add test coverage reporting with JaCoCo Gradle plugin.
```

---

## Phase 3 — Polish (2–3 Sprints)

> Lower-severity improvements for long-term maintainability, developer experience, and operational excellence.

### 3.1 Create Multi-Module Gradle Build (GAP-02)

**Priority:** P2 — Build management

**Devin Prompt:**
```
Convert the internet banking microservices project to a Gradle multi-module build:

1. Create a root settings.gradle that includes all services:
   rootProject.name = 'internet-banking-microservices'
   include 'banking-common',
           'core-banking-service',
           'internet-banking-api-gateway',
           'internet-banking-config-server',
           'internet-banking-fund-transfer-service',
           'internet-banking-service-registry',
           'internet-banking-user-service',
           'internet-banking-utility-payment-service'

2. Create a root build.gradle with:
   - Shared Java version (21)
   - Shared Spring Boot and Spring Cloud versions
   - Shared test configuration
   - Shared repository definitions

3. Simplify each service's build.gradle to inherit from root.

4. Remove individual gradlew from each service (use root gradlew).

5. Verify all services build from root: ./gradlew build
```

### 3.2 Standardize Package Structure (GAP-03, GAP-04)

**Priority:** P3 — Consistency

**Devin Prompt:**
```
Standardize the package structure across all services in the internet banking
microservices project. Use this consistent layout:

com.javatodev.finance.
├── configuration/
│   ├── audit/
│   ├── feign/
│   ├── filter/
│   └── security/ (where applicable)
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
    └── rest/ (for Feign clients)

Specifically:
1. Move utility-payment-service's repository package to model.repository
2. Move utility-payment-service's model.rest.* to model.dto.*
3. Ensure consistent Feign client location (service.rest.client)
```

### 3.3 Standardize Resource Naming (GAP-25)

**Priority:** P3 — API consistency

**Devin Prompt:**
```
Standardize REST resource naming across the internet banking microservices to use
plural nouns consistently:

1. Fund transfer: /api/v1/transfer -> /api/v1/transfers
2. Utility payment: /api/v1/utility-payment -> /api/v1/utility-payments
3. Core accounts: /api/v1/account -> /api/v1/accounts
4. Core transactions: /api/v1/transaction -> /api/v1/transactions
5. Keep /api/v1/bank-users as-is (already plural)

Update:
- All @RequestMapping paths in controllers
- Gateway route configuration
- Feign client @RequestMapping paths
- Postman collection
- Documentation

Maintain backward compatibility by keeping old paths active with @Deprecated
annotation for one release cycle.
```

### 3.4 Add Custom Health Indicators (GAP-28)

**Priority:** P3 — Operational readiness

**Devin Prompt:**
```
Add custom health indicators to all application services in the internet banking
microservices project:

1. core-banking-service:
   - DatabaseHealthIndicator: verify MySQL connectivity
   - Add readiness/liveness probe configuration

2. internet-banking-user-service:
   - KeycloakHealthIndicator: verify Keycloak server is reachable
   - DatabaseHealthIndicator: verify MySQL connectivity

3. internet-banking-fund-transfer-service:
   - CoreBankingHealthIndicator: verify core-banking-service is reachable via Feign
   - DatabaseHealthIndicator: verify MySQL connectivity

4. internet-banking-utility-payment-service:
   - CoreBankingHealthIndicator: verify core-banking-service is reachable via Feign
   - DatabaseHealthIndicator: verify MySQL connectivity

5. Configure actuator endpoints:
   management:
     endpoint:
       health:
         show-details: always
     health:
       readinessstate:
         enabled: true
       livenessstate:
         enabled: true

6. Add Kubernetes-style probe paths: /actuator/health/liveness, /actuator/health/readiness
```

### 3.5 Add Integration Tests (GAP-11)

**Priority:** P2 — Test coverage

**Devin Prompt:**
```
Add integration tests for all application services in the internet banking microservices
project using Spring Boot Test and H2 in-memory database:

1. core-banking-service:
   - AccountControllerIntegrationTest: test GET endpoints with seeded data
   - TransactionControllerIntegrationTest: test fund transfer and utility payment flows
   - UserControllerIntegrationTest: test user lookup

2. internet-banking-fund-transfer-service:
   - FundTransferIntegrationTest: test POST /api/v1/transfer with mocked Feign client
     (use @MockBean for BankingCoreFeignClient)

3. internet-banking-utility-payment-service:
   - UtilityPaymentIntegrationTest: test POST /api/v1/utility-payment with mocked
     Feign client

4. internet-banking-user-service:
   - UserIntegrationTest: test registration flow with mocked Keycloak and core-banking
     Feign clients

Use @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) with TestRestTemplate.
Configure H2 test database with ddl-auto=create for schema generation.
```

### 3.6 Add Contract Tests (GAP-12)

**Priority:** P3 — Service contract stability

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services in the internet banking microservices
project:

1. Add Spring Cloud Contract dependencies:
   - Verifier to core-banking-service (provider)
   - Stub Runner to fund-transfer, utility-payment, and user services (consumers)

2. Define contracts in core-banking-service/src/test/resources/contracts/:
   - fund-transfer.groovy: POST /api/v1/transaction/fund-transfer contract
   - util-payment.groovy: POST /api/v1/transaction/util-payment contract
   - get-account.groovy: GET /api/v1/account/bank-account/{number} contract
   - get-user.groovy: GET /api/v1/user/{identification} contract

3. Generate stubs from core-banking-service contracts.

4. Write consumer-side tests in fund-transfer, utility-payment, and user services
   that run against the generated stubs.

5. Add contract verification to the CI pipeline.
```

### 3.7 Add Custom Metrics and Dashboards (GAP-29)

**Priority:** P3 — Operational visibility

**Devin Prompt:**
```
Add custom business metrics to the internet banking microservices project:

1. Add Micrometer metrics to service classes:
   - fund_transfer_total (counter, tags: status=[SUCCESS,FAILED])
   - fund_transfer_amount (distribution summary)
   - utility_payment_total (counter, tags: status=[SUCCESS,FAILED], provider)
   - user_registration_total (counter, tags: status=[SUCCESS,FAILED])
   - feign_call_duration (timer, tags: service, endpoint)

2. Configure Prometheus endpoint in each service:
   management:
     endpoints:
       web:
         exposure:
           include: prometheus,health,info,metrics

3. Create a docker-compose-monitoring.yml with:
   - Prometheus (with scrape config for all services)
   - Grafana (with pre-built dashboard JSON)

4. Create Grafana dashboard with panels for:
   - Transaction volume over time
   - Error rate by service
   - P95 response latency
   - Circuit breaker state
```

### 3.8 Improve Distributed Tracing (GAP-30)

**Priority:** P3 — Debugging

**Devin Prompt:**
```
Improve distributed tracing in the internet banking microservices project:

1. Set explicit trace sampling rate in each service's application.yml:
   management:
     tracing:
       sampling:
         probability: 1.0  # 100% for development, reduce for production

2. Add trace ID to all error responses so clients can reference it for support:
   Update GlobalExceptionHandler to include traceId from MDC in ErrorResponse.

3. Add @NewSpan annotations to business-critical methods:
   - TransactionService.fundTransfer()
   - TransactionService.utilPayment()
   - UserService.createUser()
   - FundTransferService.fundTransfer()
   - UtilityPaymentService.utilPayment()

4. Add custom span tags for business context (account numbers, transaction IDs).
```

### 3.9 Add Fallback Behavior (GAP-34)

**Priority:** P3 — Graceful degradation

**Devin Prompt:**
```
Add fallback behavior for downstream service failures in the internet banking
microservices project:

1. For read operations (GET endpoints):
   - Fund transfer list: return cached results or empty list with a warning header
   - User list: return local DB records without Keycloak enrichment

2. For write operations (POST endpoints):
   - Fund transfer: queue the request for later processing if core-banking is down
   - Utility payment: queue the request for later processing

3. Implement using Resilience4j @CircuitBreaker fallbackMethod:
   @CircuitBreaker(name = "coreBanking", fallbackMethod = "fundTransferFallback")
   public FundTransferResponse fundTransfer(FundTransferRequest request) { ... }

   private FundTransferResponse fundTransferFallback(FundTransferRequest request, Exception e) {
       // Save with QUEUED status, return response indicating delayed processing
   }

4. Add a message to API consumers when operating in degraded mode via response headers.
```

### 3.10 Document API Versioning Strategy (GAP-22)

**Priority:** P3 — Future-proofing

**Devin Prompt:**
```
Document and implement an API versioning strategy for the internet banking microservices:

1. Create docs/API_VERSIONING.md documenting:
   - Current version: v1 (URL path-based)
   - Versioning approach: URL path prefix (/api/v1/, /api/v2/)
   - Deprecation policy: support N-1 versions for 6 months
   - Breaking vs non-breaking change definitions

2. Add API version configuration to each service:
   - Create a VersionController that returns the current API version
   - Add version info to OpenAPI specification metadata

3. Add deprecation annotations and headers for any future endpoint changes.
```

### 3.11 Add Filtering and Sorting Documentation (GAP-24)

**Priority:** P3 — API documentation

**Devin Prompt:**
```
Add filtering, sorting, and search capabilities to paginated endpoints in the internet
banking microservices project:

1. Fund transfers: add filters for status, fromAccount, toAccount, date range
2. Utility payments: add filters for status, providerId, date range
3. Users: add filters for status, identification
4. Core banking users: add search by email, name

For each filterable endpoint:
- Add @RequestParam query parameters with @Schema annotations
- Implement Spring Data JPA Specifications or query methods
- Document sortable fields in OpenAPI descriptions
- Add examples to Postman collection
```

### 3.12 Increase wait-for-it.sh Timeout (GAP-37)

**Priority:** P3 — Deployment reliability

**Devin Prompt:**
```
Increase the wait-for-it.sh timeout in all docker-compose entrypoints from 50 seconds
to 120 seconds to handle cold-start scenarios where MySQL and Keycloak need time for
initial setup:

1. Update docker-compose.yml entrypoints for all services:
   - Change --timeout=50 to --timeout=120

2. Consider adding health check support in docker-compose:
   healthcheck:
     test: ["CMD", "curl", "-f", "http://localhost:PORT/actuator/health"]
     interval: 10s
     timeout: 5s
     retries: 10

3. Replace wait-for-it.sh with Docker Compose depends_on with condition: service_healthy
   for a more robust startup ordering.
```

---

## Phase Summary

| Phase | Items | Focus | Estimated Effort |
|-------|-------|-------|-----------------|
| Phase 1 | 12 items (GAP-05, 06, 07, 08, 14, 16, 17, 19, 20, 21, 27, 36) | Critical security, error handling, and data integrity fixes | 1–2 sprints |
| Phase 2 | 10 items (GAP-01, 09, 10, 13, 15, 23, 26, 31, 32/33, 35) | Resilience, testing, shared library, and CI/CD | 2–4 sprints |
| Phase 3 | 12 items (GAP-02, 03, 04, 11, 12, 22, 24, 25, 28, 29, 30, 34, 37) | Polish, documentation, monitoring, and long-term quality | 2–3 sprints |

### Recommended Execution Order Within Each Phase

**Phase 1 (start immediately):**
1. Input validation (GAP-14) — prevents data corruption
2. Error HTTP status codes + internal detail removal (GAP-05, 06, 07) — fixes client error handling
3. Hardcoded credentials (GAP-16) — security hygiene
4. Sensitive data in logs (GAP-27) — data leakage prevention
5. Feign error decoders (GAP-08) — error propagation
6. @Transactional (GAP-36) — data consistency
7. OpenAPI dependency (GAP-21) — Swagger fix
8. ResponseEntity types (GAP-20) — type safety
9. Keycloak thread safety (GAP-17) — race condition fix
10. Dependency scanning (GAP-19) — proactive security

**Phase 2 (after Phase 1 is complete):**
1. Feign failure handling + compensation (GAP-09) — stuck transaction recovery
2. Circuit breakers (GAP-31) — cascading failure prevention
3. Idempotency keys (GAP-35) — duplicate transaction prevention
4. Downstream auth (GAP-15) — internal API security
5. Retry + timeout config (GAP-32, 33) — resilience
6. CI pipeline (GAP-13) — automated quality gate
7. Shared library (GAP-01) — code deduplication
8. Unit tests (GAP-10) — code quality
9. Pagination wrapper (GAP-23) — API usability
10. Structured logging (GAP-26) — production readiness

**Phase 3 (ongoing):**
- Can be executed in any order based on team priorities
- Contract tests and integration tests can be started in parallel with Phase 2
