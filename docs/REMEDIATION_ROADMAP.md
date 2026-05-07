# Remediation Roadmap

## Table of Contents

- [Overview](#overview)
- [Phase 1: Quick Wins (1-2 weeks)](#phase-1-quick-wins-1-2-weeks)
- [Phase 2: Important Improvements (3-6 weeks)](#phase-2-important-improvements-3-6-weeks)
- [Phase 3: Polish and Maturity (6-12 weeks)](#phase-3-polish-and-maturity-6-12-weeks)
- [Priority Matrix](#priority-matrix)

---

## Overview

This roadmap prioritizes the 40 gaps identified in the Gap Analysis into three phases:

- **Phase 1 (Quick Wins):** High-impact, low-effort fixes that reduce critical risk immediately. Mostly configuration changes, small code fixes, and adding validation.
- **Phase 2 (Important):** Structural improvements that require moderate refactoring. Testing infrastructure, resilience patterns, and code organization.
- **Phase 3 (Polish):** Long-term investments in code quality, developer experience, and operational maturity.

**Estimated Total Effort:** 12-16 weeks for a small team (2-3 developers)

---

## Phase 1: Quick Wins (1-2 weeks)

### 1.1 Fix Balance Calculation Bug
**Gap Ref:** Appendix (Balance Calculation Bug) | **Severity:** Critical | **Effort:** Small

Fix the double-subtraction/addition bug in `TransactionService` that corrupts `availableBalance` on every transaction.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.
In the fundTransfer, utilPayment, and internalFundTransfer methods, the availableBalance
is being set by subtracting from actualBalance AFTER actualBalance was already decremented,
causing a double-subtraction. The availableBalance should be set equal to the new actualBalance
after the debit/credit (or calculated independently). Fix all three occurrences (lines 63-64,
90-91, 99-100). Update the existing unit tests to verify the correct balance calculations
after transfers.
```

---

### 1.2 Fix Exception HTTP Status Codes
**Gap Ref:** GAP-ERR-001 | **Severity:** Critical | **Effort:** Medium

Map exceptions to proper HTTP status codes instead of returning 400 for everything.

**Devin Prompt:**
```
Refactor the GlobalExceptionHandler in all 4 business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to return correct HTTP status codes:
- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 409 Conflict
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException -> 422 Unprocessable Entity
- InvalidBankingUserException -> 404 Not Found
- Generic Exception -> 500 Internal Server Error with a sanitized message (do NOT leak
  the exception details in the response body)
Use @ResponseStatus or explicit ResponseEntity.status() calls. Ensure the ErrorResponse
format { "code": "...", "message": "..." } is used consistently for all error types.
Remove the raw string response from the catch-all handler.
```

---

### 1.3 Add Input Validation to All Endpoints
**Gap Ref:** GAP-SEC-002 | **Severity:** Critical | **Effort:** Medium

Add Jakarta Bean Validation to all request DTOs and controller parameters.

**Devin Prompt:**
```
Add input validation across all microservices:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle for
   core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service,
   and internet-banking-utility-payment-service.

2. Add validation annotations to all request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount,
     add a custom validation that fromAccount != toAccount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
     @NotBlank on referenceNumber and account
   - User (user-service): @NotBlank @Email on email, @NotBlank on identification,
     @NotBlank @Size(min=8) on password
   - UserUpdateRequest: @NotNull on status

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns
   HTTP 422 with field-level error details in the ErrorResponse format.
```

---

### 1.4 Remove Hardcoded Secrets from Source
**Gap Ref:** GAP-SEC-001 | **Severity:** Critical | **Effort:** Medium

Move credentials out of version-controlled files.

**Devin Prompt:**
```
Remove hardcoded credentials from docker-compose files and MySQL Dockerfile:

1. Create a docker-compose/.env.example file with placeholder values for:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_USER_PASSWORD, KEYCLOAK_ADMIN_PASSWORD,
   KEYCLOAK_DB_PASSWORD

2. Update docker-compose.yml and docker-compose-support-apps.yml to reference these
   environment variables using ${VARIABLE} syntax.

3. Update docker-compose/mysql/Dockerfile to use ARG/ENV from compose instead of hardcoded.

4. Update docker-compose/mysql/privileges.sql to use a placeholder and document that
   the password should be changed.

5. Add .env to .gitignore.

6. Update README.md to document the .env setup step.
```

---

### 1.5 Fix Keycloak Singleton Thread Safety
**Gap Ref:** GAP-SEC-005 | **Severity:** High | **Effort:** Small

Replace the manual singleton with a Spring-managed bean.

**Devin Prompt:**
```
Refactor KeycloakProperties in internet-banking-user-service to fix the thread-safety issue.
Remove the static keycloakInstance field and the getInstance() method. Instead, create a
@Configuration class (e.g., KeycloakConfig) with a @Bean method that returns a singleton
Keycloak instance built from the @Value properties. Inject the Keycloak bean directly
into KeycloakManager instead of calling getInstance().
```

---

### 1.6 Stop Logging Sensitive Data
**Gap Ref:** GAP-OBS-002 | **Severity:** Medium | **Effort:** Small

Prevent passwords and financial data from appearing in logs.

**Devin Prompt:**
```
Fix sensitive data logging across all services:

1. In internet-banking-user-service User DTO, add @ToString.Exclude on the password field.

2. In all controller classes that log request.toString(), replace with logging only
   non-sensitive identifiers. For example:
   - UserController: log email only (not password)
   - FundTransferController: log fromAccount and toAccount (not amount)
   - UtilityPaymentController: log account and providerId (not amount)
   - TransactionController: log account identifiers only

3. Review and ensure no other DTO toString() methods expose sensitive financial data
   in logs.
```

---

### 1.7 Fix OpenAPI Dependency Mismatch
**Gap Ref:** GAP-API-006 | **Severity:** Low | **Effort:** Small

Use the correct SpringDoc dependency for servlet-based services.

**Devin Prompt:**
```
In the build.gradle files for core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service,
change the springdoc dependency from:
  'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
  'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (servlet stack), not WebFlux. Only the API Gateway should
use the webflux variant.
```

---

### 1.8 Add Type Parameters to ResponseEntity
**Gap Ref:** GAP-API-001 | **Severity:** High | **Effort:** Medium

Add generic types to all controller return types.

**Devin Prompt:**
```
Add explicit generic type parameters to all ResponseEntity return types across all controllers:

- core-banking-service AccountController:
  getBankAccount -> ResponseEntity<BankAccount>
  getUtilityAccount -> ResponseEntity<UtilityAccount>

- core-banking-service UserController:
  readUser -> ResponseEntity<User>
  readUsers -> ResponseEntity<List<User>>

- core-banking-service TransactionController:
  fundTransfer -> ResponseEntity<FundTransferResponse>
  utilPayment -> ResponseEntity<UtilityPaymentResponse>

- internet-banking-fund-transfer-service FundTransferController:
  sendFundTransfer -> ResponseEntity<FundTransferResponse>
  readFundTransfers -> ResponseEntity<List<FundTransfer>>

- internet-banking-utility-payment-service UtilityPaymentController:
  readPayments -> ResponseEntity<List<UtilityPayment>>
  processPayment -> ResponseEntity<UtilityPaymentResponse>

This improves type safety and enables proper OpenAPI schema generation.
```

---

### 1.9 Add .editorconfig and Formatting
**Gap Ref:** GAP-ORG-007 | **Severity:** Medium | **Effort:** Small

Standardize code formatting across all services.

**Devin Prompt:**
```
Add an .editorconfig file at the repository root with these settings:
- charset = utf-8
- indent_style = space
- indent_size = 4
- end_of_line = lf
- trim_trailing_whitespace = true
- insert_final_newline = true

For *.gradle files, use indent_size = 4.
For *.yml files, use indent_size = 2.
For *.md files, set trim_trailing_whitespace = false.
```

---

## Phase 2: Important Improvements (3-6 weeks)

### 2.1 Add Circuit Breakers and Resilience
**Gap Ref:** GAP-RES-001, GAP-RES-002, GAP-RES-003, GAP-RES-004 | **Severity:** Critical/High | **Effort:** Medium

Add Resilience4j for circuit breakers, retries, timeouts, and fallbacks.

**Devin Prompt:**
```
Add Resilience4j circuit breakers, retry policies, and timeouts to all Feign clients:

1. Add these dependencies to build.gradle for internet-banking-user-service,
   internet-banking-fund-transfer-service, and internet-banking-utility-payment-service:
   - 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breakers in application.yml for each service:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3

3. Configure retry policies:
   - maxAttempts: 3
   - waitDuration: 1s
   - retryExceptions: [IOException, FeignException.class]
   - ignoreExceptions: [BusinessException subclasses]

4. Configure timeouts:
   - Feign connectTimeout: 5000ms
   - Feign readTimeout: 10000ms

5. Add @CircuitBreaker annotations with fallback methods on each Feign client interface
   or on the service methods that call Feign clients. Fallbacks should return meaningful
   error messages (e.g., "Core Banking Service is currently unavailable").

6. Add unit tests for the fallback behavior.
```

---

### 2.2 Add Unit Tests for All Services
**Gap Ref:** GAP-TEST-001 | **Severity:** Critical | **Effort:** Large

Write unit tests for user-service, fund-transfer-service, and utility-payment-service.

**Devin Prompt:**
```
Add unit tests for the service layer of all three internet banking services. Use Mockito
for mocking dependencies. Each test class should follow the pattern established in
core-banking-service tests.

For internet-banking-user-service UserService:
- Test createUser success flow
- Test createUser with already registered email
- Test createUser with invalid email (mismatch)
- Test createUser with NIC not found in core banking
- Test readUsers returns enriched user list
- Test readUser by ID (found and not found)
- Test updateUser with APPROVED status (verify Keycloak update)

For internet-banking-fund-transfer-service FundTransferService:
- Test fundTransfer success flow (verify entity saved with PENDING then SUCCESS)
- Test fundTransfer when core banking returns error
- Test readAllTransfers pagination

For internet-banking-utility-payment-service UtilityPaymentService:
- Test utilPayment success flow
- Test utilPayment when core banking returns error
- Test readPayments pagination

Also fix the *ApplicationTests.java files in all services to use a test profile
that disables Eureka and Config Server discovery (use bootstrap properties override
or @TestPropertySource).
```

---

### 2.3 Create Shared Common Library
**Gap Ref:** GAP-ORG-002 | **Severity:** High | **Effort:** Large

Extract duplicated code into a shared module.

**Devin Prompt:**
```
Create a shared library module called 'banking-common' at the repository root:

1. Set up a Gradle multi-project build:
   - Create root settings.gradle that includes all 7 projects (6 services + common)
   - Create root build.gradle with shared dependency versions using a version catalog
     or ext block

2. Move these classes into banking-common:
   - com.javatodev.finance.common.dto.AuditAware
   - com.javatodev.finance.common.mapper.BaseMapper
   - com.javatodev.finance.common.exception.SimpleBankingGlobalException
   - com.javatodev.finance.common.exception.ErrorResponse
   - com.javatodev.finance.common.exception.GlobalExceptionHandler
   - com.javatodev.finance.common.exception.EntityNotFoundException
   - com.javatodev.finance.common.filter.AppAuthUserFilter
   - com.javatodev.finance.common.filter.ApiRequestContext
   - com.javatodev.finance.common.filter.ApiRequestContextHolder

3. Add banking-common as a dependency in the services that need it.

4. Remove the duplicated classes from individual services.

5. Ensure all services still compile and tests pass.
```

---

### 2.4 Add Integration Tests with Testcontainers
**Gap Ref:** GAP-TEST-002 | **Severity:** High | **Effort:** Large

Add integration tests with real database using Testcontainers.

**Devin Prompt:**
```
Add integration tests using Testcontainers for MySQL to the core-banking-service:

1. Add testImplementation dependencies:
   - 'org.testcontainers:mysql:1.19.7'
   - 'org.testcontainers:junit-jupiter:1.19.7'

2. Create an integration test base class that:
   - Starts a MySQL Testcontainer
   - Configures Spring datasource to point to the container
   - Runs Flyway migrations

3. Write integration tests for:
   - AccountController: GET bank account, GET utility account (using MockMvc)
   - UserController: GET user by identification, GET users paginated
   - TransactionController: POST fund transfer, POST utility payment
   - Verify database state after transactions

4. Create a separate Gradle test source set (e.g., 'integrationTest') so integration
   tests can be run independently from unit tests.
```

---

### 2.5 Add Structured Logging
**Gap Ref:** GAP-OBS-001 | **Severity:** High | **Effort:** Medium

Configure JSON structured logging across all services.

**Devin Prompt:**
```
Add structured JSON logging to all services:

1. Add 'ch.qos.logback.contrib:logback-json-classic:0.1.5' and
   'ch.qos.logback.contrib:logback-jackson:0.1.5' to all service build.gradle files.

2. Create a logback-spring.xml in each service's src/main/resources/ with:
   - JSON format for production/docker profile
   - Console text format for local development
   - Include traceId and spanId from Micrometer in the MDC
   - Include service name in every log entry

3. Ensure the docker profile activates JSON logging automatically.
```

---

### 2.6 Add Rate Limiting to API Gateway
**Gap Ref:** GAP-SEC-004 | **Severity:** High | **Effort:** Medium

Configure request rate limiting at the gateway level.

**Devin Prompt:**
```
Add rate limiting to the internet-banking-api-gateway:

1. Add 'org.springframework.boot:spring-boot-starter-data-redis-reactive' to build.gradle.

2. Add a Redis service to docker-compose.yml.

3. Configure Spring Cloud Gateway RequestRateLimiter filter in the gateway's route
   configuration with:
   - replenishRate: 10 requests/second
   - burstCapacity: 20
   - Key resolver based on the authenticated principal (JWT subject) with fallback
     to IP address for unauthenticated endpoints

4. Add appropriate 429 Too Many Requests response handling.

5. Test with a simple script that sends rapid requests to verify rate limiting works.
```

---

### 2.7 Add Pagination Metadata to List Endpoints
**Gap Ref:** GAP-API-003 | **Severity:** Medium | **Effort:** Small

Return page metadata in list responses.

**Devin Prompt:**
```
Update all paginated list endpoints across all services to return page metadata:

1. Create a generic PageResponse<T> class in the common library (or in each service
   if common library isn't created yet) with fields:
   - content: List<T>
   - totalElements: long
   - totalPages: int
   - pageNumber: int
   - pageSize: int

2. Update service methods to return PageResponse instead of List:
   - core-banking-service UserService.readUsers()
   - internet-banking-user-service UserService.readUsers()
   - internet-banking-fund-transfer-service FundTransferService.readAllTransfers()
   - internet-banking-utility-payment-service UtilityPaymentService.readPayments()

3. Update controllers to return ResponseEntity<PageResponse<T>>.

4. Update existing tests to verify pagination metadata.
```

---

### 2.8 Add Idempotency to Fund Transfers
**Gap Ref:** GAP-RES-005 | **Severity:** Medium | **Effort:** Medium

Prevent duplicate fund transfers from retries.

**Devin Prompt:**
```
Add idempotency support to the fund transfer flow:

1. Add an 'idempotencyKey' field (String, UUID) to the FundTransferRequest DTO.

2. In FundTransferService.fundTransfer(), before processing:
   - Check if a FundTransferEntity already exists with the same idempotencyKey
   - If found and status is SUCCESS, return the existing response
   - If found and status is PENDING/PROCESSING, return a 409 Conflict

3. Add a unique constraint on idempotencyKey in FundTransferEntity.

4. Propagate the idempotencyKey to core-banking-service FundTransferRequest.

5. Add unit tests for:
   - Duplicate request with same key returns existing result
   - Different key processes normally
```

---

### 2.9 Add Dependency Vulnerability Scanning
**Gap Ref:** GAP-SEC-006 | **Severity:** High | **Effort:** Small

Add automated security scanning for dependencies.

**Devin Prompt:**
```
Add OWASP Dependency-Check to the Gradle build:

1. Add the OWASP dependency-check plugin to each service's build.gradle:
   id 'org.owasp.dependencycheck' version '9.0.10'

2. Configure the plugin to:
   - Fail the build on CVSS score >= 7 (HIGH)
   - Generate HTML and JSON reports
   - Suppress known false positives if any

3. Add a root-level Gradle task that runs dependency check across all services.

4. Document how to run: ./gradlew dependencyCheckAnalyze
```

---

## Phase 3: Polish and Maturity (6-12 weeks)

### 3.1 Add Contract Tests Between Services
**Gap Ref:** GAP-TEST-003 | **Severity:** High | **Effort:** Large

Implement consumer-driven contract testing.

**Devin Prompt:**
```
Add Spring Cloud Contract testing between services:

1. In core-banking-service (provider), add:
   - 'org.springframework.cloud:spring-cloud-starter-contract-verifier' as testImplementation
   - Contract DSL files in src/test/resources/contracts/ for each endpoint:
     * GET /api/v1/user/{identification}
     * GET /api/v1/account/bank-account/{account_number}
     * POST /api/v1/transaction/fund-transfer
     * POST /api/v1/transaction/util-payment

2. Generate and publish contract stubs as a test artifact.

3. In consumer services (user-service, fund-transfer-service, utility-payment-service), add:
   - 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner' as testImplementation
   - Stub-runner tests that verify Feign clients work correctly against the provider stubs

4. Add Gradle tasks to run contract verification as part of CI.
```

---

### 3.2 Add Custom Health Checks
**Gap Ref:** GAP-OBS-003 | **Severity:** Medium | **Effort:** Small

Add health indicators for critical dependencies.

**Devin Prompt:**
```
Add custom HealthIndicator beans to each service:

1. In internet-banking-user-service, add a KeycloakHealthIndicator that:
   - Calls Keycloak's well-known endpoint to verify connectivity
   - Returns Health.up() or Health.down() with error details

2. In all business services, add a ConfigServerHealthIndicator that:
   - Checks if the config server is reachable
   - Returns current configuration profile info

3. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service,
   add a CoreBankingHealthIndicator that:
   - Calls core-banking-service actuator health endpoint via Feign
   - Reports downstream service health

4. Configure actuator to expose health details:
   management.endpoint.health.show-details: when_authorized
```

---

### 3.3 Add Prometheus Metrics and Grafana Dashboards
**Gap Ref:** GAP-OBS-004 | **Severity:** Medium | **Effort:** Medium

Add metrics collection and visualization.

**Devin Prompt:**
```
Add Prometheus metrics to all services:

1. Add 'io.micrometer:micrometer-registry-prometheus' to all service build.gradle files.

2. Expose the Prometheus endpoint in application.yml:
   management.endpoints.web.exposure.include: health,prometheus,info

3. Add a Prometheus container to docker-compose.yml that scrapes all services.

4. Add a Grafana container to docker-compose.yml with:
   - Pre-configured Prometheus data source
   - Dashboard JSON files for:
     * JVM metrics (heap, threads, GC)
     * HTTP request rate, latency, error rate per service
     * Database connection pool metrics
     * Feign client call metrics

5. Document access URLs in README.md (Grafana at port 3000, Prometheus at 9090).
```

---

### 3.4 Standardize Package Structure
**Gap Ref:** GAP-ORG-003 | **Severity:** Medium | **Effort:** Small

Normalize package organization across all services.

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance/
  ├── controller/          (REST controllers)
  ├── service/             (business logic)
  ├── client/              (Feign clients, renamed from service.rest)
  ├── repository/          (JPA repositories, moved from model.repository)
  ├── model/
  │   ├── entity/          (JPA entities)
  │   ├── dto/             (request/response DTOs)
  │   └── mapper/          (entity-DTO mappers)
  ├── config/              (Spring configuration, renamed from configuration)
  └── exception/           (exception classes and handlers)

Rename packages and update all imports. Ensure all tests still pass.
```

---

### 3.5 Separate Request/Response DTOs
**Gap Ref:** GAP-ORG-004, GAP-SEC-007 | **Severity:** Medium | **Effort:** Medium

Decouple request and response models.

**Devin Prompt:**
```
Refactor DTOs across all services to separate request and response objects:

1. In internet-banking-user-service:
   - Create UserRegistrationRequest (email, identification, password)
   - Create UserResponse (id, email, identification, status) - NO password field
   - Update User DTO to not extend AuditAware

2. In internet-banking-fund-transfer-service:
   - FundTransferRequest already exists (keep as-is)
   - Create FundTransferListResponse (id, fromAccount, toAccount, amount, status, createdDate)

3. In internet-banking-utility-payment-service:
   - UtilityPaymentRequest already exists (keep as-is)
   - Create UtilityPaymentListResponse (id, providerId, amount, status, transactionId, createdDate)

4. Update controllers and services to use the new request/response types.
5. Update mappers to convert between entities and the new DTOs.
6. Ensure password is never included in any response payload.
```

---

### 3.6 Restrict Actuator Endpoints
**Gap Ref:** GAP-OBS-005 | **Severity:** Low | **Effort:** Small

Limit actuator exposure in production.

**Devin Prompt:**
```
Restrict actuator endpoint exposure:

1. In each service's application.yml (or config server properties), configure:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
     endpoint:
       health:
         show-details: when_authorized

2. In the API Gateway SecurityConfiguration, restrict actuator access:
   - Only allow /actuator/health and /actuator/info without authentication
   - Require authentication for all other actuator paths
   - Remove the blanket permitAll() for actuator endpoints

3. Ensure Prometheus can still scrape metrics (configure a service account or
   network-level access control).
```

---

### 3.7 Add API Versioning Infrastructure
**Gap Ref:** GAP-API-002 | **Severity:** High | **Effort:** Medium

Document and implement API version management.

**Devin Prompt:**
```
Document and implement an API versioning strategy:

1. Create docs/API_VERSIONING.md documenting:
   - Versioning approach: URI path-based (/api/v1/, /api/v2/)
   - Deprecation policy: v(N-1) supported for 6 months after v(N) release
   - How to add a new version: create new controller methods/classes

2. In each service, create a base controller class or annotation that standardizes
   the version prefix:
   @RequestMapping("/api/v1")
   public abstract class BaseV1Controller {}

3. Add the X-API-Version response header via a Spring interceptor or filter
   that includes the current API version in all responses.

4. Update OpenAPI annotations to include version information.
```

---

### 3.8 Implement RabbitMQ Notification Service
**Gap Ref:** Architecture documentation references RabbitMQ | **Severity:** Medium | **Effort:** Large

Build the planned notification service.

**Devin Prompt:**
```
Implement the notification service that was planned in the architecture:

1. Add RabbitMQ to docker-compose.yml (image: rabbitmq:3-management, ports 5672/15672).

2. Add spring-boot-starter-amqp to fund-transfer-service and utility-payment-service.

3. In fund-transfer-service, publish a FundTransferCompletedEvent to RabbitMQ after
   a successful transfer (include fromAccount, toAccount, amount, transactionId).

4. In utility-payment-service, publish a UtilityPaymentCompletedEvent after a
   successful payment.

5. Create a new internet-banking-notification-service that:
   - Consumes messages from the RabbitMQ queues
   - Logs the notification (placeholder for email/SMS integration)
   - Registers with Eureka
   - Includes actuator and tracing

6. Add the notification service to docker-compose.yml.
```

---

### 3.9 Add Test Coverage Measurement
**Gap Ref:** GAP-TEST-005 | **Severity:** Medium | **Effort:** Small

Configure JaCoCo for coverage tracking.

**Devin Prompt:**
```
Add JaCoCo code coverage to all services:

1. Add the JaCoCo plugin to each service's build.gradle:
   id 'jacoco'

2. Configure JaCoCo:
   jacocoTestReport {
     reports {
       xml.required = true
       html.required = true
     }
   }
   jacocoTestCoverageVerification {
     violationRules {
       rule {
         limit {
           minimum = 0.60  // Start at 60%, increase over time
         }
       }
     }
   }

3. Add 'check.dependsOn jacocoTestCoverageVerification' to enforce coverage on build.

4. Document coverage targets in this roadmap:
   - Phase 2 target: 60% line coverage on service layer
   - Phase 3 target: 70% line coverage overall
```

---

## Priority Matrix

| # | Item | Phase | Severity | Effort | Gap Refs |
|---|---|---|---|---|---|
| 1 | Fix balance calculation bug | 1 | Critical | Small | Appendix |
| 2 | Fix exception HTTP status codes | 1 | Critical | Medium | ERR-001 |
| 3 | Add input validation | 1 | Critical | Medium | SEC-002 |
| 4 | Remove hardcoded secrets | 1 | Critical | Medium | SEC-001 |
| 5 | Fix Keycloak singleton | 1 | High | Small | SEC-005 |
| 6 | Stop logging sensitive data | 1 | Medium | Small | OBS-002 |
| 7 | Fix OpenAPI dependency | 1 | Low | Small | API-006 |
| 8 | Add ResponseEntity type params | 1 | High | Medium | API-001 |
| 9 | Add .editorconfig | 1 | Medium | Small | ORG-007 |
| 10 | Add circuit breakers + resilience | 2 | Critical | Medium | RES-001-004 |
| 11 | Add unit tests for all services | 2 | Critical | Large | TEST-001 |
| 12 | Create shared common library | 2 | High | Large | ORG-002 |
| 13 | Add integration tests | 2 | High | Large | TEST-002 |
| 14 | Add structured logging | 2 | High | Medium | OBS-001 |
| 15 | Add rate limiting | 2 | High | Medium | SEC-004 |
| 16 | Add pagination metadata | 2 | Medium | Small | API-003 |
| 17 | Add idempotency to transfers | 2 | Medium | Medium | RES-005 |
| 18 | Add vulnerability scanning | 2 | High | Small | SEC-006 |
| 19 | Add contract tests | 3 | High | Large | TEST-003 |
| 20 | Add custom health checks | 3 | Medium | Small | OBS-003 |
| 21 | Add Prometheus + Grafana | 3 | Medium | Medium | OBS-004 |
| 22 | Standardize packages | 3 | Medium | Small | ORG-003 |
| 23 | Separate request/response DTOs | 3 | Medium | Medium | ORG-004, SEC-007 |
| 24 | Restrict actuator endpoints | 3 | Low | Small | OBS-005 |
| 25 | Add API versioning infra | 3 | High | Medium | API-002 |
| 26 | Implement notification service | 3 | Medium | Large | Architecture |
| 27 | Add test coverage measurement | 3 | Medium | Small | TEST-005 |

---

## Success Criteria

| Metric | Current | Phase 1 Target | Phase 2 Target | Phase 3 Target |
|---|---|---|---|---|
| Critical bugs | 1 (balance) | 0 | 0 | 0 |
| HTTP status correctness | ~0% | 100% | 100% | 100% |
| Input validation coverage | 0% | 100% | 100% | 100% |
| Unit test coverage | ~15% (1 of 4 services) | ~15% | ~60% | ~70% |
| Integration test coverage | 0% | 0% | Key flows | All flows |
| Contract test coverage | 0% | 0% | 0% | All Feign clients |
| Circuit breaker coverage | 0% | 0% | All Feign calls | All Feign calls |
| Hardcoded secrets | 5+ | 0 | 0 | 0 |
| Services with structured logging | 0 | 0 | All | All |
| Prometheus metrics | No | No | Yes | Yes + dashboards |
