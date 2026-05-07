# Remediation Roadmap

This roadmap prioritizes the 32 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

## Table of Contents

- [Phase 1: Quick Wins (Critical Fixes & Small Effort)](#phase-1-quick-wins)
- [Phase 2: Important (High-Value Structural Improvements)](#phase-2-important)
- [Phase 3: Polish (Quality & Completeness)](#phase-3-polish)
- [Effort Summary](#effort-summary)

---

## Phase 1: Quick Wins

> **Goal:** Fix critical bugs and security issues that have small effort. These should be addressed immediately.

### 1.1 Fix Balance Calculation Bug (Gap 7.6)

**Severity:** Critical | **Effort:** Small

The `internalFundTransfer()` and `utilPayment()` methods double-subtract/add when updating `availableBalance`, causing incorrect balances after every transaction.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java. In both 
internalFundTransfer() and utilPayment(), the availableBalance is being double-subtracted 
(or double-added). After updating actualBalance, set availableBalance = actualBalance 
(not actualBalance minus amount again). Fix both the debit and credit sides. Update the 
existing unit tests in TransactionServiceTest.java to assert correct availableBalance 
values after transfers and payments.
```

---

### 1.2 Stop Leaking Exception Details to Clients (Gap 2.2)

**Severity:** Critical | **Effort:** Small

The generic `Exception` handler in all four services returns internal exception details (stack traces, class names) in the response body.

**Devin Prompt:**
```
In all four business services (core-banking-service, internet-banking-user-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update 
the GlobalExceptionHandler's generic Exception handler to return a structured ErrorResponse 
with code "INTERNAL_ERROR" and message "An unexpected error occurred. Please try again 
later." instead of concatenating the exception. Log the full exception at ERROR level 
server-side. Make the ErrorResponse format consistent across all services (use the builder 
pattern everywhere).
```

---

### 1.3 Add Input Validation to All Request DTOs (Gap 4.2)

**Severity:** Critical | **Effort:** Small

No request DTOs have Jakarta Bean Validation annotations. Null, negative, or empty values are accepted.

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across the project:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle for 
   core-banking-service, internet-banking-fund-transfer-service, 
   internet-banking-utility-payment-service, and internet-banking-user-service.

2. Add validation annotations to these DTOs:
   - core-banking FundTransferRequest: @NotBlank fromAccount/toAccount, @NotNull @Positive amount
   - core-banking UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, 
     @NotBlank referenceNumber, @NotBlank account
   - fund-transfer FundTransferRequest: same as above plus @NotBlank authID
   - utility-payment UtilityPaymentRequest: same as core-banking version
   - user-service User (registration): @NotBlank email, @Email email, @NotBlank identification, 
     @NotBlank password

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns 
   HTTP 400 with field-level error details in a structured ErrorResponse.
```

---

### 1.4 Externalize Hard-Coded Credentials (Gap 4.1)

**Severity:** Critical | **Effort:** Small

Database passwords, Keycloak admin credentials, and MySQL user passwords are committed to source code.

**Devin Prompt:**
```
Externalize all hard-coded credentials in docker-compose files:

1. Create a docker-compose/.env.example file documenting all required environment variables:
   MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD, POSTGRES_PASSWORD, 
   MYSQL_APP_USER_PASSWORD

2. Update docker-compose.yml and docker-compose-support-apps.yml to reference these 
   variables using ${VARIABLE_NAME} syntax.

3. Update docker-compose/mysql/privileges.sql to use an environment variable for the 
   application user password.

4. Add .env to .gitignore.

5. Remove the test credentials from README.md and reference the .env.example file instead.
```

---

### 1.5 Add Idempotency Protection for Financial Operations (Gap 7.5)

**Severity:** Critical | **Effort:** Medium

Fund transfers and utility payments can be duplicated if a client retries after a timeout.

**Devin Prompt:**
```
Add idempotency protection to the fund transfer and utility payment flows:

1. In internet-banking-fund-transfer-service:
   - Add a transactionReference field (UUID) that the client must provide in the request
   - Add a unique constraint on transactionReference in FundTransferEntity
   - Before processing, check if a transfer with this reference already exists; if so, 
     return the existing result
   - Update the FundTransferRequest DTO to include @NotBlank transactionReference

2. In internet-banking-utility-payment-service:
   - Apply the same pattern with a client-provided idempotency key
   - Add unique constraint on the idempotency key in UtilityPaymentEntity

3. Add appropriate error handling for duplicate key violations (return 409 Conflict).
```

---

### 1.6 Use Correct HTTP Status Codes (Gap 2.1)

**Severity:** High | **Effort:** Small

All exceptions return HTTP 400 regardless of the actual error type.

**Devin Prompt:**
```
Update GlobalExceptionHandler in all four business services to return correct HTTP status codes:

- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity  
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException -> 400 Bad Request
- InvalidBankingUserException -> 404 Not Found
- SimpleBankingGlobalException (generic) -> 400 Bad Request
- Exception (unhandled) -> 500 Internal Server Error

Use ResponseEntity.status(HttpStatus.XXX) instead of ResponseEntity.badRequest() everywhere.
Ensure all responses use the structured ErrorResponse format.
```

---

### 1.7 Fix Keycloak Singleton Thread Safety (Gap 4.3)

**Severity:** High | **Effort:** Small

The lazy-initialized Keycloak instance in `KeycloakProperties` has a race condition.

**Devin Prompt:**
```
Refactor KeycloakProperties in internet-banking-user-service to be thread-safe:

1. Remove the static Keycloak singleton field and getInstance() method from 
   KeycloakProperties.

2. Create a new KeycloakConfig @Configuration class that defines a @Bean method returning 
   a Keycloak instance built from the @Value-injected properties. Spring will manage the 
   singleton lifecycle.

3. Update KeycloakManager to inject the Keycloak bean directly instead of calling 
   keycloakProperties.getInstance().
```

---

### 1.8 Configure Timeouts for All Feign Clients (Gap 7.3)

**Severity:** High | **Effort:** Small

No explicit timeouts are set on Feign clients, risking indefinite blocking.

**Devin Prompt:**
```
Add timeout configuration for all Feign clients in the project:

1. In each service that uses Feign (user-service, fund-transfer-service, 
   utility-payment-service), add the following to application.yml:

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
               readTimeout: 15000

2. Also add HikariCP connection pool timeouts in the datasource configuration:
   spring.datasource.hikari.connection-timeout: 10000
   spring.datasource.hikari.maximum-pool-size: 10
```

---

### 1.9 Remove Sensitive Data from Log Output (Gap 6.5)

**Severity:** High | **Effort:** Small

User passwords and account numbers are logged via `toString()` on request DTOs.

**Devin Prompt:**
```
Fix sensitive data logging across all services:

1. In internet-banking-user-service User DTO: add @ToString.Exclude on the password field.

2. In all controllers that log request objects, either:
   a. Remove the toString() logging of full request objects, OR
   b. Create a sanitized log representation that masks sensitive fields

3. In AppAuthUserFilter, change the log level from INFO to DEBUG for the auth ID logging.

4. Ensure no controller logs full account numbers - log only the last 4 digits.
```

---

### 1.10 Add Authorization / Ownership Checks (Gap 4.5)

**Severity:** High | **Effort:** Medium

Any authenticated user can perform any operation, including admin actions and transfers from other users' accounts.

**Devin Prompt:**
```
Add role-based access control and ownership verification:

1. In the API Gateway SecurityConfiguration, add role-based matchers:
   - /user/api/v1/bank-users/update/** -> require ROLE_ADMIN
   - /user/api/v1/bank-users (GET list) -> require ROLE_ADMIN
   - All other authenticated endpoints -> require ROLE_USER

2. In internet-banking-fund-transfer-service FundTransferService:
   - Extract the authenticated user's ID from ApiRequestContextHolder
   - Verify the fromAccount belongs to the authenticated user by calling 
     core-banking-service to check account ownership
   - Reject transfers from accounts the user doesn't own (403 Forbidden)

3. Apply the same ownership check in utility-payment-service.

4. Document the required Keycloak roles in the README.
```

---

## Phase 2: Important

> **Goal:** Structural improvements that significantly improve maintainability, reliability, and developer experience.

### 2.1 Add Unit Tests for All Services (Gap 3.1)

**Severity:** Critical | **Effort:** Large

Only core-banking-service has unit tests. The other 6 services have no meaningful tests.

**Devin Prompt:**
```
Add comprehensive unit tests for internet-banking-user-service:

1. Create UserServiceTest with tests for:
   - createUser success flow (mock Keycloak and BankingCoreRestClient)
   - createUser when email already registered (expect UserAlreadyRegisteredException)
   - createUser when email doesn't match core banking (expect InvalidEmailException)
   - createUser when user not found in core banking (expect InvalidBankingUserException)
   - readUsers (verify Keycloak enrichment)
   - readUser by ID (found and not found cases)
   - updateUser with APPROVED status (verify Keycloak enable)

2. Create UserControllerTest using @WebMvcTest with MockMvc for all 4 endpoints.

3. Create KeycloakUserServiceTest with mocked KeycloakManager.

4. Add test/resources/application.yml with H2 config and disabled eureka/config.

Use JUnit 5, Mockito, and existing test patterns from core-banking-service.
```

**Additional prompts for other services:**
```
Add unit tests for internet-banking-fund-transfer-service: Create FundTransferServiceTest 
with tests for successful transfer, Feign client failure handling, and readAllTransfers. 
Create FundTransferControllerTest using @WebMvcTest. Add test application.yml with H2 
and disabled eureka/config.
```

```
Add unit tests for internet-banking-utility-payment-service: Create 
UtilityPaymentServiceTest with tests for successful payment, Feign client failure, and 
readPayments. Create UtilityPaymentControllerTest using @WebMvcTest. Add test 
application.yml with H2 and disabled eureka/config.
```

---

### 2.2 Add Circuit Breakers (Gap 7.1)

**Severity:** High | **Effort:** Medium

No circuit breaker protection exists for inter-service calls.

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls:

1. Add these dependencies to fund-transfer, utility-payment, and user services:
   - org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j

2. Enable circuit breaker for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true

3. Create fallback factories for each Feign client:
   - BankingCoreFeignClientFallbackFactory (fund-transfer and utility-payment)
   - BankingCoreRestClientFallbackFactory (user-service)
   
   Fallbacks should log the error and throw a meaningful exception with HTTP 503.

4. Configure circuit breaker properties:
   resilience4j.circuitbreaker.instances.core-banking-service:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     permittedNumberOfCallsInHalfOpenState: 3
```

---

### 2.3 Add Integration Tests (Gap 3.2)

**Severity:** High | **Effort:** Large

No integration tests exist. Application context tests fail without infrastructure.

**Devin Prompt:**
```
Add integration tests for core-banking-service:

1. Add Testcontainers dependencies:
   - org.testcontainers:testcontainers
   - org.testcontainers:mysql
   - org.testcontainers:junit-jupiter

2. Create an AbstractIntegrationTest base class with @Testcontainers that starts MySQL 
   and configures Spring datasource dynamically.

3. Create AccountControllerIntegrationTest (@SpringBootTest + TestRestTemplate):
   - Test GET /api/v1/account/bank-account/{number} returns correct account
   - Test GET with invalid number returns 404

4. Create TransactionControllerIntegrationTest:
   - Test POST /api/v1/transaction/fund-transfer with valid data
   - Test fund transfer with insufficient funds returns 422

5. Disable Eureka client in test profile.
6. Configure Flyway to run migrations against Testcontainers MySQL.
```

---

### 2.4 Add Contract Tests (Gap 3.3)

**Severity:** High | **Effort:** Medium

No contract tests verify Feign client compatibility with core-banking-service APIs.

**Devin Prompt:**
```
Add Spring Cloud Contract tests for core-banking-service APIs:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   - spring-cloud-starter-contract-verifier (test)
   - spring-cloud-contract-gradle-plugin

2. Write contracts in test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number} (success + not found)
   - GET /api/v1/user/{identification} (success + not found)
   - POST /api/v1/transaction/fund-transfer (success + insufficient funds)
   - POST /api/v1/transaction/util-payment (success)

3. Generate contract stubs JAR.

4. In fund-transfer-service and utility-payment-service, add:
   - spring-cloud-starter-contract-stub-runner (test)
   - Tests that use @AutoConfigureStubRunner to verify Feign clients against stubs.
```

---

### 2.5 Extract Shared Library (Gap 1.1)

**Severity:** Medium | **Effort:** Medium

Duplicated code across services creates maintenance burden and inconsistency.

**Devin Prompt:**
```
Create a shared library module and convert to a Gradle multi-module build:

1. Create a root settings.gradle that includes all services:
   include 'shared-lib', 'core-banking-service', 'internet-banking-user-service', etc.

2. Create shared-lib/build.gradle as a plain Java library (no Spring Boot plugin, 
   just dependency-management).

3. Move these shared classes into shared-lib:
   - AuditAware (from model.dto package)
   - BaseMapper<E, D> interface
   - ErrorResponse
   - SimpleBankingGlobalException and subclasses
   - GlobalExceptionHandler (create a base class)
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - AccountResponse DTO

4. Update each service's build.gradle to depend on shared-lib:
   implementation project(':shared-lib')

5. Remove the duplicated classes from each service.
6. Verify all services compile and tests pass.
```

---

### 2.6 Add Structured Logging (Gap 6.1)

**Severity:** Medium | **Effort:** Medium

Logs are unstructured plain text with no correlation IDs.

**Devin Prompt:**
```
Add structured JSON logging across all services:

1. Add logstash-logback-encoder dependency to all services:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a shared logback-spring.xml configuration:
   - Console appender with LogstashEncoder for production (JSON format)
   - Pattern appender for local development
   - Include traceId and spanId from MDC in all log entries
   - Activate JSON format when profile is 'docker' or 'prod'

3. Place the logback-spring.xml in each service's src/main/resources/.

4. Update log statements to use structured key-value pairs where appropriate:
   log.info("Fund transfer initiated", kv("fromAccount", masked), kv("amount", amount));
```

---

### 2.7 Add Retry Policies and Fallback Behavior (Gaps 7.2, 7.4)

**Severity:** Medium | **Effort:** Medium

No retry or fallback strategies for transient failures.

**Devin Prompt:**
```
Add retry policies for Feign clients:

1. Add spring-retry dependency to all Feign-using services.

2. Configure Feign retry in application.yml for GET requests only:
   spring.cloud.openfeign.client.config.default:
     retryer: feign.Retryer.Default
   
3. Create a custom Retryer that retries up to 3 times with 1s initial interval 
   and exponential backoff, but ONLY for GET methods.

4. For fund-transfer and utility-payment POST operations, do NOT retry automatically.
   Instead, implement a fallback that:
   - Sets the local entity status to FAILED
   - Returns a response indicating the transfer/payment needs manual review
   - Logs the failure for operations team
```

---

### 2.8 Add Prometheus Metrics Endpoint (Gap 6.3)

**Severity:** Medium | **Effort:** Small

Prometheus is listed in the tech stack but no metrics endpoint is exposed.

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add micrometer-registry-prometheus to all service build.gradle files:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the prometheus actuator endpoint in application.yml:
   management.endpoints.web.exposure.include: health,info,prometheus,metrics

3. Add custom business metrics in key services:
   - core-banking: Counter for fund transfers (success/failure), timer for transaction 
     processing
   - fund-transfer: Counter for transfers by status
   - utility-payment: Counter for payments by status
   - user-service: Counter for registrations

4. Add a sample Prometheus scrape configuration in docker-compose/ for reference.
```

---

## Phase 3: Polish

> **Goal:** Improve developer experience, documentation, and overall code quality.

### 3.1 Fix OpenAPI Documentation (Gap 5.5)

**Severity:** Medium | **Effort:** Small

OpenAPI docs use wrong artifact and lack response schema annotations.

**Devin Prompt:**
```
Fix and improve OpenAPI documentation across all services:

1. Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui 
   in core-banking-service, user-service, fund-transfer-service, and utility-payment-service 
   (these are servlet-based, not reactive).

2. Parameterize all ResponseEntity return types in controllers 
   (e.g., ResponseEntity<BankAccount> instead of raw ResponseEntity).

3. Add @ApiResponse annotations for error cases on all endpoints.

4. Add @Schema annotations with descriptions to all DTO fields.

5. Add an OpenAPI info configuration bean in each service with title, description, 
   and version.
```

---

### 3.2 Add Pagination Metadata to List Responses (Gap 5.2)

**Severity:** Medium | **Effort:** Small

List endpoints return raw lists without pagination metadata.

**Devin Prompt:**
```
Update all list endpoints to return pagination metadata:

1. Create a shared PageResponse<T> wrapper class:
   { content: List<T>, totalElements: long, totalPages: int, number: int, size: int }

2. Update these endpoints to return PageResponse instead of List:
   - CoreBanking UserController.readUsers()
   - UserService UserController.readUsers()
   - FundTransferController.readFundTransfers()
   - UtilityPaymentController.readPayments()

3. Update the service layer to pass through Spring's Page metadata instead of calling 
   .getContent() and discarding pagination info.
```

---

### 3.3 Standardize REST Conventions (Gap 5.1)

**Severity:** Medium | **Effort:** Small

Mixed naming patterns and non-RESTful paths.

**Devin Prompt:**
```
Standardize REST API paths across all services for consistency:

1. Core Banking Service - no changes needed (paths are reasonable).

2. User Service:
   - POST /api/v1/bank-users/register -> POST /api/v1/bank-users (creation is implied 
     by POST on the resource)
   - PATCH /api/v1/bank-users/update/{id} -> PATCH /api/v1/bank-users/{id}

3. Update API Gateway route configuration if paths change.

4. Update all Feign client paths to match.

5. Update Postman collection references in README.

Note: This is a breaking change. Document the migration in the PR description.
```

---

### 3.4 Complete Distributed Tracing Configuration (Gap 6.4)

**Severity:** Medium | **Effort:** Small

Tracing is included but not optimally configured.

**Devin Prompt:**
```
Improve distributed tracing configuration:

1. Add tracing configuration to each service's application.yml:
   management:
     tracing:
       sampling:
         probability: 1.0  # 100% for non-production

2. Add a filter that includes the trace ID in response headers:
   X-Trace-Id: {traceId}

3. Ensure Feign client calls propagate trace context (verify feign-micrometer is working).

4. Add custom spans for business operations:
   - @NewSpan("fundTransfer") on TransactionService.fundTransfer()
   - @NewSpan("utilPayment") on TransactionService.utilPayment()
   - @NewSpan("createUser") on UserService.createUser()
```

---

### 3.5 Add Dependency Vulnerability Scanning (Gap 4.6)

**Severity:** Medium | **Effort:** Small

No automated dependency vulnerability monitoring.

**Devin Prompt:**
```
Add OWASP Dependency Check to the Gradle build:

1. Add the OWASP dependency-check plugin to each service's build.gradle:
   plugins {
     id 'org.owasp.dependencycheck' version '9.0.10'
   }

2. Configure the plugin:
   dependencyCheck {
     failBuildOnCVSS = 7
     suppressionFile = "${rootProject.projectDir}/owasp-suppressions.xml"
   }

3. Create a GitHub Actions workflow (.github/workflows/dependency-check.yml) that runs 
   the dependency check on PRs and weekly.

4. Create an initial owasp-suppressions.xml for any known false positives.
```

---

### 3.6 Standardize Package Structure (Gap 1.2)

**Severity:** Low | **Effort:** Small

Inconsistent package naming across services.

**Devin Prompt:**
```
Standardize package structure across all services to follow this convention:

com.javatodev.finance/
  controller/       - REST controllers
  service/           - Business logic
  service.rest/      - Feign clients for external services
  model.entity/      - JPA entities
  model.dto/         - Data transfer objects
  model.dto.request/ - Request DTOs
  model.dto.response/- Response DTOs
  model.mapper/      - Entity-DTO mappers
  repository/        - Spring Data repositories
  configuration/     - Spring configuration classes
  exception/         - Exception classes

Move classes to match this structure in:
- utility-payment-service: move repository to model.repository (or vice versa)
- fund-transfer-service: move service.rest.client to service.rest
- user-service: move model.rest.response to model.dto.response
```

---

### 3.7 Register Mappers as Spring Beans (Gap 1.3)

**Severity:** Low | **Effort:** Small

Mappers are instantiated via `new` instead of Spring DI.

**Devin Prompt:**
```
Convert all mapper classes to Spring-managed beans:

1. Add @Component to all mapper classes:
   - BankAccountMapper, UserMapper, UtilityAccountMapper (core-banking)
   - UserMapper (user-service)
   - FundTransferMapper (fund-transfer-service)
   - UtilityPaymentMapper (utility-payment-service)

2. In service classes, replace field initialization:
   - BEFORE: private UserMapper userMapper = new UserMapper();
   - AFTER:  private final UserMapper userMapper; (injected via @RequiredArgsConstructor)

3. Update tests to mock or instantiate mappers as needed.
```

---

### 3.8 Add Custom Health Indicators (Gap 6.2)

**Severity:** Low | **Effort:** Small

Only default Spring Boot health checks exist.

**Devin Prompt:**
```
Add custom health indicators to key services:

1. In internet-banking-user-service, add a KeycloakHealthIndicator that checks 
   connectivity to the Keycloak server.

2. In all Feign-using services, add a CoreBankingHealthIndicator that pings 
   core-banking-service's /actuator/health endpoint.

3. Configure actuator to show health details:
   management.endpoint.health.show-details: always
   management.endpoint.health.show-components: always

4. Add health check configuration to docker-compose.yml for each service:
   healthcheck:
     test: ["CMD", "curl", "-f", "http://localhost:{port}/actuator/health"]
     interval: 30s
     timeout: 10s
     retries: 3
```

---

### 3.9 Add Filtering and Sorting to List Endpoints (Gap 5.4)

**Severity:** Low | **Effort:** Medium

List endpoints only support basic pagination.

**Devin Prompt:**
```
Add filtering support to list endpoints:

1. Fund Transfer list (GET /api/v1/transfer):
   - Add optional query params: fromAccount, toAccount, status, dateFrom, dateTo
   - Implement using Spring Data JPA Specifications or @Query

2. Utility Payment list (GET /api/v1/utility-payment):
   - Add optional query params: account, providerId, status, dateFrom, dateTo

3. User list (GET /api/v1/bank-users):
   - Add optional query params: status, identification

4. Ensure all list endpoints support sort parameter via Pageable.

5. Document the new parameters in OpenAPI annotations.
```

---

### 3.10 Document CSRF and Security Decisions (Gap 4.4)

**Severity:** Medium | **Effort:** Small

Security decisions like CSRF disabling are undocumented.

**Devin Prompt:**
```
Add security documentation:

1. Add inline comments in SecurityConfiguration.java explaining why CSRF is disabled 
   (stateless JWT-based API, no browser cookie sessions).

2. Create a docs/SECURITY.md file documenting:
   - Authentication flow (Keycloak -> JWT -> API Gateway -> X-Auth-Id)
   - Authorization model (current state and planned RBAC)
   - CSRF decision rationale
   - Secrets management approach
   - Network security (Docker network isolation)
```

---

### 3.11 Add API Versioning Strategy Documentation (Gap 5.3)

**Severity:** Low | **Effort:** Small

No documented API versioning strategy.

**Devin Prompt:**
```
Document the API versioning strategy:

1. Add a section to docs/KNOWLEDGE_BASE.md or create docs/API_VERSIONING.md covering:
   - Current approach: URL path versioning (/api/v1/)
   - When to increment the version (breaking changes)
   - Deprecation policy (how long old versions are supported)
   - How to run multiple versions simultaneously

2. Add Deprecated annotation support: document that deprecated endpoints should use 
   @Deprecated and @Operation(deprecated = true) in OpenAPI.
```

---

### 3.12 Parameterize Raw ResponseEntity Types (Gap 2.4)

**Severity:** Low | **Effort:** Small

Raw `ResponseEntity` types suppress compile-time type checking.

**Devin Prompt:**
```
Add type parameters to all ResponseEntity return types across all controllers:

- AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- TransactionController: ResponseEntity<FundTransferResponse>, 
  ResponseEntity<UtilityPaymentResponse>
- CoreBanking UserController: ResponseEntity<User>, ResponseEntity<List<User>>
- FundTransferController: ResponseEntity<FundTransferResponse>, 
  ResponseEntity<List<FundTransfer>>
- UtilityPaymentController: ResponseEntity<UtilityPaymentResponse>, 
  ResponseEntity<List<UtilityPayment>>

Also parameterize the GlobalExceptionHandler return types: ResponseEntity<ErrorResponse>.
```

---

### 3.13 Add Test Configuration for All Services (Gap 3.4)

**Severity:** Medium | **Effort:** Small

Most services lack proper test configuration.

**Devin Prompt:**
```
Add self-contained test configurations for all services that need databases:

1. For internet-banking-user-service/src/test/resources/application.yml:
   - H2 in-memory datasource
   - Eureka client disabled: eureka.client.enabled=false
   - Config import disabled
   - Keycloak properties with test values

2. For internet-banking-fund-transfer-service/src/test/resources/application.yml:
   - H2 in-memory datasource  
   - Eureka client disabled
   - Config import disabled

3. For internet-banking-utility-payment-service/src/test/resources/application.yml:
   - Same pattern as above

4. Ensure all ApplicationTests context load tests pass with these configurations.
```

---

## Effort Summary

| Phase | Items | Critical | High | Medium | Low | Est. Total Effort |
|-------|-------|----------|------|--------|-----|-------------------|
| Phase 1 (Quick Wins) | 10 | 5 | 5 | 0 | 0 | ~3-5 days |
| Phase 2 (Important) | 8 | 1 | 3 | 4 | 0 | ~2-3 weeks |
| Phase 3 (Polish) | 13 | 0 | 0 | 5 | 8 | ~1-2 weeks |
| **Total** | **31** | **6** | **8** | **9** | **8** | **~4-6 weeks** |

### Recommended Execution Order

1. **Start with Phase 1.1** (balance bug) - this is a live data corruption issue.
2. **Then 1.2 + 1.3 + 1.6** (error handling cluster) - fixes the entire error response surface.
3. **Then 1.4 + 1.9** (credentials and logging) - security hygiene.
4. **Then 1.5 + 1.7 + 1.8 + 1.10** (remaining Phase 1) - resilience and authorization.
5. **Phase 2** in order listed - tests, circuit breakers, shared lib.
6. **Phase 3** can be parallelized across developers.

Each Devin prompt above is designed to be self-contained and can be executed independently, though some have logical dependencies (e.g., shared library extraction should happen before standardizing package structure).
