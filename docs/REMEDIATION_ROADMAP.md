# Internet Banking Microservices — Remediation Roadmap

## Table of Contents

- [Overview](#overview)
- [Phase 1: Quick Wins (Critical/High Severity, Small Effort)](#phase-1-quick-wins)
- [Phase 2: Important (Critical/High Severity, Medium Effort)](#phase-2-important)
- [Phase 3: Polish (Medium/Low Severity, Various Effort)](#phase-3-polish)
- [Phase Summary](#phase-summary)

---

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on a severity-vs-effort matrix:

| Phase | Criteria | Expected Duration | Gaps |
|---|---|---|---|
| **Phase 1** | Critical/High severity + Small effort | 1–2 weeks | 14 items |
| **Phase 2** | Critical/High severity + Medium/Large effort | 3–6 weeks | 10 items |
| **Phase 3** | Medium/Low severity + Various effort | Ongoing | 13 items |

---

## Phase 1: Quick Wins

> Critical and High severity gaps that can be fixed with Small effort. These represent the highest ROI remediations — maximum risk reduction for minimum investment.

---

### 1.1 Fix Double-Deduction Bug in Balance Calculations (GAP-36)

**Priority:** P0 — Active data corruption bug  
**Services affected:** `core-banking-service`  
**Files:** `TransactionService.java`

**Devin Prompt:**
```
Fix the double-deduction bug in core-banking-service TransactionService. In the
internalFundTransfer() method (lines 90-91), availableBalance is computed from the
already-debited actualBalance, causing double subtraction. The same bug exists on
the credit side (lines 99-100) and in the utilPayment() method (lines 63-64).

Fix: change availableBalance updates to use the original availableBalance field
instead of re-reading from the already-modified actualBalance. For debit:
  entity.setAvailableBalance(entity.getAvailableBalance().subtract(amount));
For credit:
  entity.setAvailableBalance(entity.getAvailableBalance().add(amount));

Apply the same fix to utilPayment(). Update existing unit tests in
TransactionServiceTest to verify that actualBalance and availableBalance are
updated correctly and independently.
```

---

### 1.2 Fix Error Handling — Proper HTTP Status Codes (GAP-05, GAP-06, GAP-08)

**Priority:** P0 — All errors currently return 400  
**Services affected:** All 4 business services  
**Files:** `GlobalExceptionHandler.java` in each service

**Devin Prompt:**
```
Refactor GlobalExceptionHandler in all 4 business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to:

1. Map EntityNotFoundException → HTTP 404 Not Found
2. Map InsufficientFundsException → HTTP 422 Unprocessable Entity
3. Map InvalidEmailException, UserAlreadyRegisteredException → HTTP 409 Conflict
4. Map SimpleBankingGlobalException (generic) → HTTP 400 Bad Request
5. Map Exception (catch-all) → HTTP 500 Internal Server Error with a generic
   sanitized message (do NOT include exception details in response body)
6. Log the full exception stack trace at ERROR level for the 500 handler

Standardize all error responses to use the ErrorResponse format:
  { "code": "...", "message": "..." }
Remove the plain-text "Exception occur inside API" responses.

Add a comment in each handler method explaining the HTTP status code mapping.
```

---

### 1.3 Add Feign Error Decoder to Fund-Transfer and Utility-Payment (GAP-07)

**Priority:** P1  
**Services affected:** `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, modeled after the existing implementation
in internet-banking-user-service (CustomFeignErrorDecoder.java).

The decoder should:
1. Parse the ErrorResponse JSON from core-banking-service error responses
2. Map HTTP 400 → SimpleBankingGlobalException with code and message from response
3. Map HTTP 401 → a descriptive exception
4. Map HTTP 404 → EntityNotFoundException
5. Default → generic exception with status code

Register the decoder in CustomFeignClientConfiguration as a @Bean.
Add a comment explaining the purpose of the error decoder.
```

---

### 1.4 Add Input Validation to All Request DTOs (GAP-13)

**Priority:** P0 — Null values cause NPEs in financial operations  
**Services affected:** All 4 business services

**Devin Prompt:**
```
Add Bean Validation (jakarta.validation) to all request DTOs across all services.
Add spring-boot-starter-validation dependency to each build.gradle that doesn't
already have it.

Specific validations:
- FundTransferRequest (core-banking + fund-transfer): @NotBlank fromAccount,
  @NotBlank toAccount, @NotNull @Positive amount
- UtilityPaymentRequest (core-banking + utility-payment): @NotNull providerId,
  @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
- User DTO (user-service): @NotBlank @Email email, @NotBlank identification,
  @NotBlank password (on create)
- UserUpdateRequest (user-service): @NotNull status

Add @Valid annotation to all @RequestBody parameters in all controllers.

Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that
returns HTTP 400 with field-level error details. Add comments on each validation
annotation explaining the constraint.
```

---

### 1.5 Remove Hardcoded Credentials from Source Code (GAP-15)

**Priority:** P0 — Credentials committed to version control  
**Files:** `docker-compose/*.yml`, `docker-compose/mysql/Dockerfile`, `docker-compose/mysql/privileges.sql`

**Devin Prompt:**
```
Remove all hardcoded credentials from Docker Compose files and MySQL init scripts:

1. Create a docker-compose/.env.example file with placeholder values for:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_USER_PASSWORD, KEYCLOAK_ADMIN_PASSWORD,
   KC_DB_PASSWORD, POSTGRES_PASSWORD

2. Update docker-compose.yml and docker-compose-support-apps.yml to use
   ${VARIABLE} references instead of hardcoded values

3. Update docker-compose/mysql/Dockerfile to use ARG/ENV from build args

4. Update docker-compose/mysql/privileges.sql to use a variable or document
   that the password must be changed

5. Add docker-compose/.env to .gitignore

6. Add a comment in each file referencing the .env.example for configuration.
```

---

### 1.6 Restrict Database Privileges (GAP-16)

**Priority:** P1  
**Files:** `docker-compose/mysql/privileges.sql`

**Devin Prompt:**
```
Refactor docker-compose/mysql/privileges.sql to use least-privilege grants:

1. Remove the wildcard GRANT on *.*
2. For each of the 4 schemas, grant only the needed privileges:
   - banking_core_service: SELECT, INSERT, UPDATE, DELETE
   - banking_core_fund_transfer_service: SELECT, INSERT, UPDATE, DELETE
   - banking_core_user_service: SELECT, INSERT, UPDATE, DELETE
   - banking_core_utility_payment_service: SELECT, INSERT, UPDATE, DELETE
3. Keep CREATE, ALTER for the core_banking_service schema only (for Flyway migrations)
4. Remove DROP privilege entirely

Add a comment at the top of the file documenting the privilege strategy.
```

---

### 1.7 Fix OpenAPI Dependency — WebFlux to WebMVC (GAP-22)

**Priority:** P1  
**Services affected:** All 4 business services  
**Files:** `build.gradle` in each service

**Devin Prompt:**
```
In all 4 business services (core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, internet-banking-utility-payment-service), replace the
incorrect OpenAPI dependency:

Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (spring-boot-starter-web), not WebFlux. The webflux-ui
dependency causes incorrect auto-configuration. Add a comment next to the dependency
explaining why webmvc-ui is used.
```

---

### 1.8 Add Pagination Response Envelope (GAP-20)

**Priority:** P1  
**Services affected:** All services with list endpoints

**Devin Prompt:**
```
Create a generic PagedResponse<T> DTO in each service (or in a shared module) with
fields: content (List<T>), totalElements, totalPages, page, size.

Refactor all paginated endpoints to return PagedResponse instead of raw List:
- core-banking UserController.readUsers()
- user-service UserController.readUsers()
- fund-transfer FundTransferController.readFundTransfers()
- utility-payment UtilityPaymentController.readPayments()

Map Spring Data Page metadata to the PagedResponse fields. Add comments explaining
each field in the PagedResponse class.
```

---

### 1.9 Stop Logging Sensitive Data (GAP-27)

**Priority:** P1  
**Services affected:** All business services

**Devin Prompt:**
```
Audit and fix all log statements across all services that output sensitive data:

1. In user-service User DTO: Add @ToString.Exclude on the password field
2. In user-service UserController: Remove request.toString() from log, replace with
   log.info("Creating user with identification {}", request.getIdentification())
3. In fund-transfer FundTransferController: Mask account numbers in logs
4. In core-banking TransactionController: Mask account numbers in logs
5. In utility-payment: Mask account numbers in logs

General rule: never log passwords, full account numbers, or transaction amounts at
INFO level. Add a comment at each changed log statement explaining why the data is
masked.
```

---

### 1.10 Add Feign Client Timeout Configuration (GAP-33)

**Priority:** P1  
**Services affected:** `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`

**Devin Prompt:**
```
Add explicit Feign client timeout configuration to the three services that use
OpenFeign (fund-transfer, utility-payment, user-service).

In each service's application.yml (or bootstrap.yml), add:

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

Add a comment in the configuration explaining the timeout values and their purpose.
This prevents threads from blocking indefinitely when core-banking is slow or
unavailable.
```

---

### 1.11 Add Feign Retry Policy for GET Operations (GAP-32)

**Priority:** P1  
**Services affected:** All services with Feign clients

**Devin Prompt:**
```
Add a Feign Retryer bean to CustomFeignClientConfiguration in fund-transfer,
utility-payment, and user-service:

@Bean
public Retryer retryer() {
    // Retry up to 3 times with 100ms initial interval and 1s max interval
    return new Retryer.Default(100, 1000, 3);
}

Important: This will retry ALL requests including POSTs. Since POST endpoints are
not idempotent yet (see GAP-34), add a note/comment that retry for POST operations
should be disabled once idempotency keys are implemented. For now, the retry provides
resilience against transient network issues. Add comments explaining the retry
configuration.
```

---

### 1.12 Fix Test Configuration for All Services (GAP-12)

**Priority:** P1  
**Services affected:** All services except core-banking (which already has test config)

**Devin Prompt:**
```
Add src/test/resources/application.yml to all services that lack one:
- internet-banking-user-service
- internet-banking-fund-transfer-service
- internet-banking-utility-payment-service
- internet-banking-api-gateway
- internet-banking-config-server
- internet-banking-service-registry

Each test application.yml should:
1. Set spring.cloud.config.enabled=false (bypass Config Server)
2. Set eureka.client.enabled=false (bypass Eureka)
3. For data services: configure H2 in-memory datasource
4. Disable Flyway if applicable

Add a comment at the top of each file explaining that this configuration is used
for testing without external dependencies.

This ensures @SpringBootTest context loading works without external infrastructure.
```

---

### 1.13 Fix Keycloak Singleton Thread Safety (GAP-17)

**Priority:** P2  
**Services affected:** `internet-banking-user-service`

**Devin Prompt:**
```
Refactor KeycloakProperties in internet-banking-user-service to use Spring's bean
lifecycle instead of a manual singleton pattern.

1. Create a new @Configuration class KeycloakConfig
2. Move the Keycloak instance creation to a @Bean method:
   @Bean
   public Keycloak keycloakInstance(KeycloakProperties properties) {
       return KeycloakBuilder.builder()
           .serverUrl(properties.getServerUrl())
           .realm(properties.getRealm())
           .grantType("client_credentials")
           .clientId(properties.getClientId())
           .clientSecret(properties.getClientSecret())
           .build();
   }
3. Remove the static field and getInstance() method from KeycloakProperties
4. Update KeycloakManager to inject the Keycloak bean directly

Add a comment on the @Bean method explaining that Spring manages the singleton
lifecycle, eliminating the thread-safety issue.
```

---

### 1.14 Add Dependency Vulnerability Scanning (GAP-19)

**Priority:** P2  
**Services affected:** All services

**Devin Prompt:**
```
Add the OWASP Dependency-Check Gradle plugin to all services.

In each build.gradle, add:
  plugins {
      id 'org.owasp.dependencycheck' version '9.0.10'
  }

Configure in each build.gradle:
  dependencyCheck {
      failBuildOnCVSS = 7
      formats = ['HTML', 'JSON']
  }

Add a comment explaining the CVSS threshold and how to run the check:
  ./gradlew dependencyCheckAnalyze

Also create a .github/dependabot.yml file to enable GitHub Dependabot alerts for
Gradle dependencies. Add a comment in the file explaining its purpose.
```

---

## Phase 2: Important

> Critical and High severity gaps requiring Medium or Large effort. These are essential for production readiness but require more design and implementation work.

---

### 2.1 Add Circuit Breakers with Resilience4j (GAP-31)

**Priority:** P0 — Cascading failure risk  
**Services affected:** `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all services that call core-banking-service
via Feign.

1. Add dependency to each service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breakers in each service's application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           sliding-window-size: 10
           failure-rate-threshold: 50
           wait-duration-in-open-state: 30s
           permitted-number-of-calls-in-half-open-state: 3

3. Add @CircuitBreaker annotations on the service methods that call Feign clients,
   with fallback methods that:
   - For fund-transfer: return a response indicating service temporarily unavailable,
     keep entity in PENDING status
   - For utility-payment: similar fallback, keep entity in PROCESSING status
   - For user-service: return appropriate error for registration failure

4. Add custom health indicator for circuit breaker state

Add comments on each circuit breaker annotation explaining the fallback behavior.
```

---

### 2.2 Add Authentication to Downstream Services (GAP-14)

**Priority:** P0 — Unauthenticated access to all business APIs  
**Services affected:** All 4 business services

**Devin Prompt:**
```
Add JWT validation to all downstream services (core-banking, user-service,
fund-transfer, utility-payment) so they don't rely solely on the Gateway for
authentication.

Option A (recommended for simplicity): Internal service token validation
1. Add spring-boot-starter-security and spring-boot-starter-oauth2-resource-server
   to each business service's build.gradle
2. Create a SecurityConfig class in each service that:
   - Validates JWT tokens using the same Keycloak JWK URI as the Gateway
   - Permits actuator endpoints without auth
   - Permits specific internal endpoints if needed for service-to-service calls
3. Configure Feign clients to forward the JWT token from incoming requests using
   a RequestInterceptor that reads the Authorization header

Add comments in each SecurityConfig explaining the authentication strategy and
which endpoints are public vs protected.
```

---

### 2.3 Implement Idempotency Keys for Transaction Endpoints (GAP-34)

**Priority:** P0 — Duplicate transactions risk  
**Services affected:** `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

**Devin Prompt:**
```
Implement idempotency key support for fund-transfer and utility-payment POST
endpoints to prevent duplicate transactions.

1. Create an IdempotencyKey entity and repository in each service:
   - Fields: id, key (unique), requestHash, responseBody, httpStatus, createdAt, expiresAt
   - Index on (key) with unique constraint

2. Create an IdempotencyFilter or interceptor that:
   - Reads X-Idempotency-Key header from POST requests
   - If key exists in DB and not expired: return stored response (skip processing)
   - If key is new: proceed with request, store response on completion
   - If no header provided: reject with HTTP 400 and message requiring the header

3. Add X-Idempotency-Key to the API documentation

4. Set key expiration to 24 hours, with a scheduled cleanup job

Add comments explaining the idempotency flow in the filter and entity classes.
```

---

### 2.4 Add Fallback and Compensation for Failed Transactions (GAP-35)

**Priority:** P1  
**Services affected:** `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

**Devin Prompt:**
```
Implement proper failure handling for fund-transfer and utility-payment services
when Feign calls to core-banking fail.

1. Add a FAILED status to the TransactionStatus enum in both services

2. Wrap Feign calls in try-catch in FundTransferService.fundTransfer() and
   UtilityPaymentService.utilPayment():
   - On FeignException: update entity status to FAILED, store error message
   - On timeout: update entity status to FAILED with timeout reason
   - Re-throw an appropriate exception for the client

3. Create a @Scheduled retry job (RetryFailedTransactionsJob) in each service:
   - Run every 5 minutes
   - Find all PENDING/PROCESSING records older than 2 minutes
   - Retry up to 3 times (add retryCount field to entities)
   - After 3 retries: mark as FAILED permanently, log alert

4. Add a GET endpoint to query failed transactions for admin monitoring

Add comments explaining the retry strategy and failure handling flow.
```

---

### 2.5 Add Database Transaction Isolation for Financial Operations (GAP-37)

**Priority:** P1  
**Services affected:** `core-banking-service`

**Devin Prompt:**
```
Add proper concurrency control for financial operations in core-banking-service
TransactionService.

1. Add pessimistic write locking to BankAccountRepository:
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT a FROM BankAccountEntity a WHERE a.number = :number")
   Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String number);

2. Update TransactionService.internalFundTransfer() to use findByNumberForUpdate()
   instead of findByNumber() for both from and to accounts

3. Update TransactionService.utilPayment() similarly

4. Set explicit transaction isolation on the @Transactional annotation:
   @Transactional(isolation = Isolation.READ_COMMITTED)
   (pessimistic lock provides the needed serialization)

5. Add unit tests verifying that the locked query methods are called

Add comments explaining why pessimistic locking is used for financial operations
and the implications for concurrent access.
```

---

### 2.6 Extract Shared Library Module (GAP-01)

**Priority:** P1  
**Services affected:** All services

**Devin Prompt:**
```
Create a banking-common shared Gradle module to eliminate code duplication across
services.

1. Create a new directory banking-common/ with its own build.gradle:
   - Apply java-library plugin
   - Include common dependencies: lombok, jakarta.persistence, spring-data-jpa,
     jackson, spring-web

2. Move these classes to banking-common:
   - BaseMapper<E, D>
   - AuditAware
   - SimpleBankingGlobalException
   - ErrorResponse
   - GlobalExceptionHandler (as a base class)
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - CustomFeignClientConfiguration

3. Create a root settings.gradle that includes all services and banking-common

4. Update each service's build.gradle to depend on banking-common:
   implementation project(':banking-common')

5. Remove duplicated classes from each service

6. Verify all services compile and tests pass

Add comments in banking-common's build.gradle explaining its purpose as a shared
library.
```

---

### 2.7 Add Unit Tests for User, Fund-Transfer, and Utility-Payment Services (GAP-09)

**Priority:** P1  
**Services affected:** `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`

**Devin Prompt:**
```
Add comprehensive unit tests (Mockito-based, no Spring context) for the three
business services that currently have no tests.

For internet-banking-user-service (UserService):
- createUser: success, email already registered, user not found in core-banking,
  email mismatch, Keycloak creation failure
- readUsers: success with pagination
- readUser: found, not found
- updateUser: approve (verify Keycloak update), invalid ID

For internet-banking-fund-transfer-service (FundTransferService):
- fundTransfer: success flow, Feign exception handling
- readAllTransfers: paginated results

For internet-banking-utility-payment-service (UtilityPaymentService):
- utilPayment: success flow, Feign exception handling
- readPayments: paginated results

Use Mockito to mock repositories, Feign clients, and KeycloakUserService. Follow
the existing test patterns in core-banking-service. Add comments explaining what
each test verifies.
```

---

### 2.8 Add Integration Tests with Testcontainers (GAP-10)

**Priority:** P2  
**Services affected:** `core-banking-service` (start here, extend to others)

**Devin Prompt:**
```
Add integration tests to core-banking-service using Testcontainers for MySQL.

1. Add test dependencies to build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create an AbstractIntegrationTest base class with:
   @Testcontainers and @Container MySQL container
   @DynamicPropertySource to inject container URL

3. Create integration tests:
   - AccountControllerIT: @SpringBootTest @AutoConfigureMockMvc
     Test GET bank-account and GET util-account endpoints
   - TransactionControllerIT: Test fund-transfer and util-payment with real DB
   - UserControllerIT: Test user lookup and listing

4. Verify Flyway migrations run against the test container

Add comments explaining the Testcontainers setup and how to run integration tests
locally.
```

---

### 2.9 Add Custom Business Metrics (GAP-29)

**Priority:** P2  
**Services affected:** All business services

**Devin Prompt:**
```
Add Micrometer custom business metrics to all business services.

1. In core-banking-service TransactionService:
   - Counter: banking.fund.transfers.total (tags: status=success|failed)
   - Counter: banking.utility.payments.total (tags: status=success|failed)
   - Timer: banking.fund.transfers.duration
   - DistributionSummary: banking.fund.transfers.amount

2. In fund-transfer-service:
   - Counter: fund.transfer.requests.total (tags: status=pending|success|failed)
   - Timer: fund.transfer.feign.duration

3. In utility-payment-service:
   - Counter: utility.payment.requests.total
   - Timer: utility.payment.feign.duration

4. In user-service:
   - Counter: user.registrations.total (tags: status=success|failed)

5. Enable Prometheus endpoint in all services:
   management.endpoints.web.exposure.include=health,info,metrics,prometheus

Add comments on each metric bean explaining what business event it tracks.
```

---

### 2.10 Add API Versioning Strategy Documentation (GAP-21)

**Priority:** P2  
**Services affected:** All services

**Devin Prompt:**
```
Document and formalize the API versioning strategy for the banking microservices.

1. Create docs/API_VERSIONING.md documenting:
   - Current strategy: URL path versioning (/api/v1/)
   - Rules for when to create v2: breaking changes to request/response format,
     removed fields, changed semantics
   - Deprecation policy: v(N-1) supported for 6 months after v(N) release
   - Sunset header: Add Sunset HTTP header to deprecated endpoints

2. Add a VersionInterceptor or filter in the Gateway that adds response headers:
   - API-Version: v1
   - Deprecation: (date, when applicable)
   - Sunset: (date, when applicable)

Add comments explaining the versioning strategy in the documentation and code.
```

---

## Phase 3: Polish

> Medium and Low severity gaps. These improve code quality, developer experience, and operational maturity but are not blocking production readiness.

---

### 3.1 Create Multi-Module Gradle Root Project (GAP-02)

**Devin Prompt:**
```
Create a root-level settings.gradle.kts and build.gradle.kts for the entire project.

1. settings.gradle.kts: include all 6 services and banking-common as subprojects
2. build.gradle.kts: define shared plugin versions, dependency versions via a
   version catalog (gradle/libs.versions.toml)
3. Move Spring Boot version (3.2.4), Spring Cloud version (2023.0.0), and common
   dependency versions to the catalog
4. Update each service's build.gradle to reference catalog versions
5. Verify: ./gradlew build from root builds all services

Add comments in the root build file explaining the multi-module structure.
```

---

### 3.2 Standardize Package Structure (GAP-03)

**Devin Prompt:**
```
Standardize package naming across all services to follow a consistent convention:
- com.javatodev.finance.controller (REST controllers)
- com.javatodev.finance.service (business logic)
- com.javatodev.finance.client (Feign clients — rename from service.rest/service.rest.client)
- com.javatodev.finance.model.dto (request/response DTOs — rename from model.rest.*)
- com.javatodev.finance.model.entity (JPA entities)
- com.javatodev.finance.repository (Spring Data repositories — rename from model.repository)
- com.javatodev.finance.config (configuration — rename from configuration)
- com.javatodev.finance.exception (exception classes)

Add a comment in each service's main application class documenting the package
structure convention.
```

---

### 3.3 Convert Mappers to Spring Beans or MapStruct (GAP-04)

**Devin Prompt:**
```
Refactor all mapper classes to be Spring-managed beans:

Option A (minimal change): Add @Component to each mapper class and @Autowired
injection in services instead of 'new Mapper()'.

Option B (recommended): Adopt MapStruct:
1. Add MapStruct dependencies to build.gradle:
   implementation 'org.mapstruct:mapstruct:1.5.5.Final'
   annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'
2. Convert BaseMapper subclasses to MapStruct @Mapper interfaces
3. Remove manual mapping code

Add comments explaining the chosen mapping approach.
```

---

### 3.4 Add Structured JSON Logging (GAP-26)

**Devin Prompt:**
```
Configure structured JSON logging for all services using logstash-logback-encoder.

1. Add dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - JSON console appender for production (activated by 'docker' profile)
   - Standard pattern appender for development (default)
   - Include traceId and spanId from Micrometer in log context

3. Verify logs include: timestamp, level, service name, traceId, spanId, message

Add comments in the logback config explaining the profile-based appender selection.
```

---

### 3.5 Add Custom Health Checks (GAP-28)

**Devin Prompt:**
```
Add custom HealthIndicator implementations to services with external dependencies:

1. user-service: KeycloakHealthIndicator — verify Keycloak realm is accessible
2. fund-transfer-service: CoreBankingHealthIndicator — verify core-banking actuator
   is reachable
3. utility-payment-service: CoreBankingHealthIndicator — same as above
4. core-banking-service: DatabaseHealthIndicator — verify MySQL connection beyond
   default DataSource check

Register each as a @Component. The health endpoint will automatically include them.
Add comments explaining what each health indicator checks.
```

---

### 3.6 Document CSRF Decision (GAP-18)

**Devin Prompt:**
```
Add documentation for the CSRF security decision in the API Gateway.

1. Add a comment block in SecurityConfiguration.java explaining:
   - CSRF is disabled because all API consumers use stateless JWT authentication
   - No cookie-based sessions are used
   - If browser-based SPAs are added with cookie auth, re-evaluate CSRF

2. Add a Security section to docs/KNOWLEDGE_BASE.md documenting:
   - Authentication flow (Keycloak → JWT → Gateway → downstream)
   - CSRF rationale
   - Endpoint authorization matrix
```

---

### 3.7 Add Distributed Tracing Fallback Configuration (GAP-30)

**Devin Prompt:**
```
Add fallback tracing configuration to each service's application.yml so distributed
tracing works in development mode without the Config Server.

Add to each service's application.yml:
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

These values will be overridden by Config Server in production. Add a comment
explaining the fallback configuration purpose.
```

---

### 3.8 Fix Non-RESTful URL Patterns (GAP-24)

**Devin Prompt:**
```
Refactor non-RESTful URL patterns in user-service:

1. Change POST /api/v1/bank-users/register → POST /api/v1/bank-users
   (POST on collection = create resource)
2. Change PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
   (PATCH on resource = update resource)
3. Update Gateway security config to permit the new registration path:
   /user/api/v1/bank-users (POST only, not GET)
4. Update any Postman collections or documentation

Add comments on each controller method explaining the REST convention being followed.
```

---

### 3.9 Add Type Parameters to ResponseEntity Returns (GAP-25)

**Devin Prompt:**
```
Add generic type parameters to all ResponseEntity return types across all controllers:

Examples:
- ResponseEntity → ResponseEntity<BankAccount>
- ResponseEntity → ResponseEntity<List<User>>
- ResponseEntity → ResponseEntity<FundTransferResponse>

This improves OpenAPI documentation generation and compile-time type safety. Add a
comment at the class level of each controller noting that typed ResponseEntity is
used for OpenAPI accuracy.
```

---

### 3.10 Add Filtering and Search to List Endpoints (GAP-23)

**Devin Prompt:**
```
Add query parameter filtering to paginated list endpoints:

1. Fund transfer list: filter by status, fromAccount, toAccount, date range
2. Utility payment list: filter by status, providerId, account, date range
3. User list (user-service): filter by status
4. User list (core-banking): filter by email (partial match)

Use Spring Data JPA Specifications or @Query with optional parameters. Add comments
on each query method explaining the available filter criteria.
```

---

### 3.11 Add Contract Tests Between Services (GAP-11)

**Devin Prompt:**
```
Implement Spring Cloud Contract tests between orchestrating services and
core-banking-service.

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'

2. Create contract definitions in core-banking-service/src/test/resources/contracts/
   for each endpoint consumed by Feign clients

3. Add Spring Cloud Contract stub runner to consumer services:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

4. Write consumer-side tests that verify Feign clients work against the contracts

Add comments explaining the contract testing approach and how to update contracts
when APIs change.
```

---

### 3.12 Add Unit Tests for Gateway Security (GAP-09 continued)

**Devin Prompt:**
```
Add unit tests for the API Gateway's security configuration and routing.

1. Add @WebFluxTest tests for SecurityConfiguration:
   - Verify /user/api/v1/bank-users/register is accessible without JWT
   - Verify /actuator/** endpoints are accessible without JWT
   - Verify other paths require valid JWT
   - Verify invalid JWT returns 401

2. Add tests for GatewayConfiguration:
   - Verify X-Auth-Id header is added to proxied requests
   - Verify unauthenticated requests get "SYSTEM USER" as X-Auth-Id

Add comments explaining each security test scenario.
```

---

### 3.13 Set Up CI/CD Pipeline

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline for the project.

1. Create .github/workflows/ci.yml with:
   - Trigger on: push to main, pull requests
   - Matrix build: all 6 services
   - Steps: checkout, setup JDK 21, gradle build, run tests
   - Cache Gradle dependencies
   - Upload test reports as artifacts

2. Create .github/workflows/docker.yml for Docker image builds:
   - Trigger on: push to main (after CI passes)
   - Build and tag Docker images for each service
   - Push to container registry (configurable)

Add comments in each workflow file explaining the pipeline stages and triggers.
```

---

## Phase Summary

| Phase | Gaps Addressed | Key Outcomes |
|---|---|---|
| **Phase 1** | GAP-05, GAP-06, GAP-07, GAP-08, GAP-12, GAP-13, GAP-15, GAP-16, GAP-17, GAP-19, GAP-20, GAP-22, GAP-27, GAP-32, GAP-33, GAP-36 | Fix active bugs, add input validation, fix error handling, secure credentials, add timeouts/retries |
| **Phase 2** | GAP-01, GAP-09, GAP-10, GAP-14, GAP-21, GAP-29, GAP-31, GAP-34, GAP-35, GAP-37 | Add circuit breakers, authentication, idempotency, shared library, tests, metrics |
| **Phase 3** | GAP-02, GAP-03, GAP-04, GAP-11, GAP-18, GAP-23, GAP-24, GAP-25, GAP-26, GAP-28, GAP-30, remaining GAP-09 | Standardize code structure, add structured logging, health checks, contract tests, CI/CD |

### Critical Path

The following items should be addressed **before any production deployment**:

1. **GAP-36** — Double-deduction bug (active data corruption)
2. **GAP-13** — Input validation (NPE risk on every endpoint)
3. **GAP-05** — Error handling (all errors return 400)
4. **GAP-14** — Downstream service authentication
5. **GAP-15** — Hardcoded credentials
6. **GAP-31** — Circuit breakers (cascading failure risk)
7. **GAP-34** — Idempotency keys (duplicate transaction risk)
