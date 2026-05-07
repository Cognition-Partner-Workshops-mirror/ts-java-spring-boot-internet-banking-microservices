# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to implement the fix.

---

## Phase 1: Quick Wins (Critical + High Severity, Small Effort)

**Timeline:** 1-2 weeks | **Goal:** Eliminate data-loss bugs, security holes, and basic reliability gaps

### 1.1 Fix Balance Calculation Double-Deduction Bug
**Gap:** GAP-RES-06 (Critical) | **Effort:** Small

The `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` was already reduced, causing a double deduction.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.
In both internalFundTransfer() and utilPayment(), the availableBalance is being
double-deducted. After subtracting from actualBalance, the availableBalance should
be set equal to the new actualBalance (not actualBalance minus amount again).
Fix both the fromAccount deduction in internalFundTransfer() and the fromAccount
deduction in utilPayment(). Update the existing unit tests to verify correct
balance values after transfers.
```

### 1.2 Add Input Validation to All Request DTOs
**Gap:** GAP-SEC-02 (Critical) | **Effort:** Medium

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. core-banking-service FundTransferRequest: @NotBlank on fromAccount/toAccount,
   @NotNull @Positive on amount
2. core-banking-service UtilityPaymentRequest: @NotNull on providerId,
   @NotNull @Positive on amount, @NotBlank on referenceNumber and account
3. internet-banking-fund-transfer-service FundTransferRequest: same as above
   plus @NotBlank on authID
4. internet-banking-utility-payment-service UtilityPaymentRequest: same as core
5. internet-banking-user-service User (registration): @NotBlank @Email on email,
   @NotBlank on identification, @NotBlank @Size(min=8) on password

Add @Valid annotation to all @RequestBody parameters in controllers.
Add spring-boot-starter-validation dependency to each service's build.gradle.
Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler
that returns 422 with field-level error details.
```

### 1.3 Externalize Hardcoded Credentials
**Gap:** GAP-SEC-01 (Critical) | **Effort:** Small

**Devin Prompt:**
```
Replace all hardcoded credentials in docker-compose files and Dockerfiles with
environment variables:

1. docker-compose.yml and docker-compose-support-apps.yml: Replace hardcoded
   MYSQL_ROOT_PASSWORD, KC_DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD with ${} env
   var references. Create a .env.example file with placeholder values.
2. docker-compose/mysql/Dockerfile: Remove the ENV MYSQL_ROOT_PASSWORD line
   (it's already set in docker-compose.yml).
3. docker-compose/mysql/privileges.sql: Replace the hardcoded password with a
   note that it should be set via environment variable, or use a Docker
   entrypoint script.
4. Add .env to .gitignore.
5. Remove test credentials from README.md and move them to a
   docs/TEST_CREDENTIALS.md that is gitignored, or reference the .env file.
```

### 1.4 Fix HTTP Status Codes in Error Handlers
**Gap:** GAP-ERR-01 (High) | **Effort:** Small

**Devin Prompt:**
```
Update GlobalExceptionHandler in all 4 business services to return correct
HTTP status codes:

1. EntityNotFoundException -> 404 Not Found
2. InsufficientFundsException -> 422 Unprocessable Entity
3. UserAlreadyRegisteredException -> 409 Conflict
4. InvalidEmailException -> 400 Bad Request
5. InvalidBankingUserException -> 404 Not Found
6. SimpleBankingGlobalException (generic) -> 400 Bad Request
7. Exception (catch-all) -> 500 Internal Server Error with structured
   ErrorResponse (not raw string)

Ensure all responses use the ErrorResponse DTO with code and message fields.
Add a timestamp and traceId field to ErrorResponse for debugging.
```

### 1.5 Add Feign Error Decoder to Fund Transfer and Utility Payment Services
**Gap:** GAP-ERR-04 (High) | **Effort:** Small

**Devin Prompt:**
```
Copy the CustomFeignErrorDecoder and CustomFeignClientConfiguration from
internet-banking-user-service to both internet-banking-fund-transfer-service
and internet-banking-utility-payment-service. Ensure the Feign clients in those
services reference the configuration class. This ensures that Core Banking error
responses (e.g., insufficient funds) are properly decoded and re-thrown as
structured exceptions instead of raw FeignExceptions.
```

### 1.6 Prevent Password Leakage in Logs and API Responses
**Gap:** GAP-SEC-03 (High) | **Effort:** Small

**Devin Prompt:**
```
Fix password exposure in internet-banking-user-service:

1. In User DTO, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
   to the password field so it is never serialized in responses.
2. In UserController.createUser(), change the log statement to not log the
   full request object. Log only the email: log.info("Creating user with
   email {}", request.getEmail())
3. Review all other controllers for similar toString() logging of request
   bodies that might contain sensitive data. Replace with specific field logging.
```

### 1.7 Add Feign Timeout Configuration
**Gap:** GAP-RES-03 (High) | **Effort:** Small

**Devin Prompt:**
```
Add Feign client timeout configuration to all services that use OpenFeign
(user-service, fund-transfer-service, utility-payment-service).

In each service's application.yml (or the centralized config repo), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000

This prevents Feign calls from blocking indefinitely when Core Banking is slow.
```

### 1.8 Add Retry Policies for Feign Clients
**Gap:** GAP-RES-02 (High) | **Effort:** Small

**Devin Prompt:**
```
Add Spring Retry support to all Feign client services:

1. Add 'org.springframework.retry:spring-retry' and
   'org.springframework:spring-aop' dependencies to build.gradle for
   user-service, fund-transfer-service, and utility-payment-service.
2. Add @EnableRetry to each application's main class.
3. Configure retry for GET requests only (not POST to avoid duplicate
   transfers). Add a Retryer bean in the Feign configuration:

   @Bean
   public Retryer retryer() {
       return new Retryer.Default(100, 1000, 3);
   }

Do NOT retry POST requests for fund-transfer or util-payment to avoid
duplicate transactions.
```

### 1.9 Fix Transaction Entity Relationship
**Gap:** GAP-RES-07 (Medium) | **Effort:** Small

**Devin Prompt:**
```
In core-banking-service TransactionEntity.java, change the account relationship
from @OneToOne(cascade = CascadeType.ALL) to @ManyToOne (no cascade). One
account can have many transactions, and we should never cascade deletes from
transactions to accounts. Update:

@ManyToOne
@JoinColumn(name = "account_id", referencedColumnName = "id")
private BankAccountEntity account;
```

---

## Phase 2: Important Improvements (High/Medium Severity, Medium Effort)

**Timeline:** 3-6 weeks | **Goal:** Improve reliability, testing, and operational maturity

### 2.1 Add Circuit Breakers with Resilience4j
**Gap:** GAP-RES-01 (High) | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breaker support to all Feign client services:

1. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
   to build.gradle for user-service, fund-transfer-service, and
   utility-payment-service.
2. Enable circuit breaker for Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return error response
     with "Core Banking Service unavailable" message
   - BankingCoreRestClientFallback (utility-payment): same pattern
   - BankingCoreRestClientFallback (user-service): same pattern
4. Configure circuit breaker thresholds:
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - slidingWindowSize: 10
```

### 2.2 Secure Internal Services
**Gap:** GAP-SEC-06 (High) | **Effort:** Medium

**Devin Prompt:**
```
Add service-to-service authentication for internal microservices. Since
Keycloak is already in place, propagate the JWT token through Feign calls:

1. Create a FeignRequestInterceptor in each calling service that extracts
   the current request's Authorization header and forwards it in Feign calls.
2. Add spring-boot-starter-oauth2-resource-server to core-banking-service,
   user-service, fund-transfer-service, and utility-payment-service.
3. Configure each service as an OAuth2 resource server validating Keycloak JWTs.
4. This ensures internal APIs can only be called with valid tokens.
```

### 2.3 Add Unit Tests for All Services
**Gap:** GAP-TEST-01 (High) | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests for all services that currently lack them:

1. internet-banking-user-service: Test UserService.createUser() (happy path,
   duplicate email, invalid email, user not found in core banking),
   readUsers(), readUser(), updateUser() with status transitions. Mock
   KeycloakUserService, BankingCoreRestClient, and UserRepository.

2. internet-banking-fund-transfer-service: Test FundTransferService.fundTransfer()
   (happy path, core banking error), readAllTransfers(). Mock
   BankingCoreFeignClient and FundTransferRepository.

3. internet-banking-utility-payment-service: Test
   UtilityPaymentService.utilPayment() (happy path, core banking error),
   readPayments(). Mock BankingCoreRestClient and UtilityPaymentRepository.

Use JUnit 5 and Mockito. Follow the same patterns used in core-banking-service
tests.
```

### 2.4 Add Idempotency Protection for Financial Operations
**Gap:** GAP-RES-05 (High) | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment operations:

1. Add an optional 'idempotencyKey' field (UUID) to FundTransferRequest and
   UtilityPaymentRequest in both the internet-banking services and core-banking.
2. In FundTransferService and UtilityPaymentService, before processing:
   - Check if a record with the same idempotencyKey already exists
   - If it does, return the existing result instead of processing again
3. Add a unique index on idempotency_key column in fund_transfer and
   utility_payment tables.
4. Document in API docs that clients should generate and send an idempotency
   key for every mutation request.
```

### 2.5 Migrate to Flyway for All Services
**Gap:** GAP-ORG-03 (Medium) | **Effort:** Medium

**Devin Prompt:**
```
Migrate internet-banking-user-service, internet-banking-fund-transfer-service,
and internet-banking-utility-payment-service from JPA auto-DDL to Flyway:

1. Add 'org.flywaydb:flyway-core' and 'org.flywaydb:flyway-mysql' dependencies
   to each service's build.gradle.
2. Generate initial migration scripts by examining the current JPA entities:
   - user-service: V1__create_user_table.sql
   - fund-transfer: V1__create_fund_transfer_table.sql
   - utility-payment: V1__create_utility_payment_table.sql
3. Include audit columns (created_date, created_by, modified_date, modified_by,
   version) from the AuditAware base class.
4. Set spring.jpa.hibernate.ddl-auto=validate in application.yml to ensure
   entities match migrations.
```

### 2.6 Create Shared Library Module
**Gap:** GAP-ORG-01 (Medium) | **Effort:** Medium

**Devin Prompt:**
```
Create a Gradle multi-module build with a shared library:

1. Create a root settings.gradle that includes all service modules.
2. Create a 'shared-lib' module containing:
   - Common exception classes (SimpleBankingGlobalException, ErrorResponse,
     GlobalExceptionHandler base, EntityNotFoundException)
   - AuditAware base class
   - BaseMapper interface
   - CustomFeignClientConfiguration and CustomFeignErrorDecoder
3. Update each service's build.gradle to depend on shared-lib:
   implementation project(':shared-lib')
4. Remove the duplicated classes from each service.
5. Ensure all services still compile and tests pass.
```

### 2.7 Add Prometheus Metrics
**Gap:** GAP-OBS-03 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Add Prometheus metrics endpoints to all services:

1. Add 'io.micrometer:micrometer-registry-prometheus' to each service's
   build.gradle.
2. In application.yml (or centralized config), expose the prometheus endpoint:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
3. Add custom business metrics using Micrometer's MeterRegistry:
   - core-banking: Counter for fund_transfers_total, utility_payments_total
   - Timer for transaction processing duration
4. Add a sample Grafana dashboard JSON in docs/grafana/ directory.
```

### 2.8 Fix OpenAPI Dependency and Configuration
**Gap:** GAP-API-04 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Fix the OpenAPI/Swagger configuration across all services:

1. Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with
   'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0' in build.gradle
   for core-banking-service, user-service, fund-transfer-service, and
   utility-payment-service (these are servlet-based, not WebFlux).
2. Create an OpenApiConfig class in each service with @OpenAPIDefinition
   providing title, version, description, and contact info.
3. Ensure /swagger-ui.html and /v3/api-docs endpoints are accessible.
```

### 2.9 Add CORS Configuration
**Gap:** GAP-SEC-05 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Add CORS configuration to the API Gateway since it is the single entry point
for browser clients:

1. In internet-banking-api-gateway, add a CorsWebFilter bean or configure
   CORS in the application config:
   spring:
     cloud:
       gateway:
         globalcors:
           cors-configurations:
             '[/**]':
               allowed-origins: "${CORS_ALLOWED_ORIGINS:http://localhost:3000}"
               allowed-methods: GET,POST,PATCH,PUT,DELETE,OPTIONS
               allowed-headers: "*"
               allow-credentials: true
2. Make allowed-origins configurable via environment variable.
```

### 2.10 Fix Keycloak Singleton Thread Safety
**Gap:** GAP-SEC-04 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Fix the thread-unsafe Keycloak singleton in internet-banking-user-service
KeycloakProperties.java. Replace the manual singleton pattern with a Spring
@Bean:

1. Remove the static keycloakInstance field and getInstance() method from
   KeycloakProperties.
2. Create a @Configuration class (KeycloakConfig) with a @Bean method that
   builds and returns the Keycloak instance.
3. Inject Keycloak directly into KeycloakManager instead of calling
   getInstance().
```

---

## Phase 3: Polish (Low Severity + Large Effort Items)

**Timeline:** 6-12 weeks | **Goal:** Achieve production-grade maturity

### 3.1 Add Integration Tests
**Gap:** GAP-TEST-02 (High) | **Effort:** Large

**Devin Prompt:**
```
Add integration tests for all business services using Testcontainers:

1. Add Testcontainers MySQL dependency to each service's build.gradle:
   testImplementation 'org.testcontainers:mysql'
   testImplementation 'org.testcontainers:junit-jupiter'
2. Create a base test class that starts a MySQL container and configures
   Spring datasource properties.
3. Write integration tests:
   - core-banking-service: Test full fund transfer flow through controller
     -> service -> repository -> database
   - user-service: Test user CRUD (mock Keycloak with WireMock)
   - fund-transfer: Test with WireMock for Core Banking responses
   - utility-payment: Test with WireMock for Core Banking responses
4. Verify Flyway migrations run correctly against real MySQL.
```

### 3.2 Add Contract Tests
**Gap:** GAP-TEST-03 (Medium) | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests to verify API compatibility between services:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   - spring-cloud-starter-contract-verifier (test dependency)
   - spring-cloud-contract-gradle-plugin
2. Write contract stubs for Core Banking endpoints that Fund Transfer, User,
   and Utility Payment services depend on:
   - GET /api/v1/user/{identification}
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Add contract stub runner tests in consumer services to verify they can
   parse Core Banking responses correctly.
4. Generate and publish contract stubs as a Maven artifact.
```

### 3.3 Add CI/CD Pipeline
**Gap:** No existing CI/CD | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml:
   - Trigger on push to main and pull requests
   - Matrix build for all 7 services
   - Steps: checkout, setup JDK 21, Gradle build, run tests
   - Cache Gradle dependencies
   - Build Docker images (but don't push on PRs)

2. Create .github/workflows/security.yml:
   - Run OWASP Dependency Check on all services
   - Run Trivy for Docker image vulnerability scanning
   - Fail on Critical/High vulnerabilities

3. Create .github/workflows/release.yml:
   - Trigger on tags (v*)
   - Build and push Docker images to GitHub Container Registry
```

### 3.4 Standardize Package Structure
**Gap:** GAP-ORG-02 (Low) | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance
  ├── config/           (Spring @Configuration classes)
  ├── controller/       (REST controllers)
  ├── dto/
  │   ├── request/     (request DTOs)
  │   └── response/    (response DTOs)
  ├── entity/          (JPA entities)
  ├── exception/       (exception classes)
  ├── mapper/          (entity <-> DTO mappers)
  ├── repository/      (Spring Data repositories)
  └── service/
      └── client/      (Feign clients)

Move classes to match this structure in all services while maintaining
backward compatibility. Update all import statements.
```

### 3.5 Improve Pagination Responses
**Gap:** GAP-API-03 (Low) | **Effort:** Small

**Devin Prompt:**
```
Update all list/GET endpoints to return proper pagination metadata:

1. Change return types from List<T> to Page<T> (or a custom PageResponse wrapper).
2. In controllers, return the Page object directly instead of calling
   getContent().
3. This will include totalElements, totalPages, size, number, and content
   in responses.
4. Add @RequestParam defaults for page=0, size=20 as documentation.
5. Add a maximum page size limit (e.g., 100) using a custom Pageable resolver.
```

### 3.6 Standardize URL Patterns
**Gap:** GAP-API-05 (Low) | **Effort:** Small

**Devin Prompt:**
```
Standardize REST URL patterns across all services to follow kebab-case
noun-based conventions:

1. Core Banking:
   - /api/v1/account/bank-account/{accountNumber} (rename path variable)
   - /api/v1/account/utility-account/{providerName} (rename from util-account)
   - /api/v1/transaction/fund-transfer (keep)
   - /api/v1/transaction/utility-payment (rename from util-payment)
2. User Service: /api/v1/users (rename from bank-users)
   - POST /api/v1/users (rename from /register)
   - PATCH /api/v1/users/{id} (rename from /update/{id})
3. Update all Feign clients to match the new URLs.
4. Add URL aliases (or redirects) for backward compatibility if needed.
```

### 3.7 Add Structured Logging
**Gap:** GAP-OBS-01 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Configure structured JSON logging across all services:

1. Add 'net.logstash.logback:logstash-logback-encoder:7.4' to each
   service's build.gradle.
2. Create a logback-spring.xml in each service's resources:
   - Console appender with pattern layout for local dev
   - JSON appender for Docker/production profile
3. Add MDC fields for requestId, userId, and traceId.
4. Review and sanitize all log statements to not log sensitive data
   (passwords, tokens, full request bodies).
```

### 3.8 Add Custom Health Indicators
**Gap:** GAP-OBS-02 (Low) | **Effort:** Small

**Devin Prompt:**
```
Add custom health indicators to services:

1. core-banking-service: Health indicator that checks MySQL connectivity.
2. user-service: Health indicators for MySQL and Keycloak reachability.
3. fund-transfer-service: Health indicator for Core Banking Service
   availability (via Eureka registration check).
4. utility-payment-service: Same as fund-transfer.
5. Configure actuator to expose health details:
   management:
     endpoint:
       health:
         show-details: when-authorized
     health:
       readinessstate:
         enabled: true
       livenessstate:
         enabled: true
```

### 3.9 Add Dependency Vulnerability Scanning
**Gap:** GAP-SEC-07 (Medium) | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency Check to the Gradle build:

1. Add the OWASP plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.1.0'
   }
2. Configure it to fail on CVSS score >= 7:
   dependencyCheck {
       failBuildOnCVSS = 7.0f
       formats = ['HTML', 'JSON']
   }
3. Add a Gradle task alias: ./gradlew dependencyCheckAnalyze
4. Document the process in README.md for running security scans locally.
```

### 3.10 Add Fallback Behavior for Degraded Operations
**Gap:** GAP-RES-04 (Medium) | **Effort:** Medium

**Devin Prompt:**
```
Add graceful degradation for when downstream services are unavailable:

1. In user-service readUsers(): If Keycloak is unavailable, return users
   from local DB without enriching with Keycloak data. Add a flag in the
   response indicating "partial data".
2. In fund-transfer-service: If Core Banking is unavailable, save the
   transfer with FAILED status and return an appropriate error message
   suggesting retry.
3. In utility-payment-service: Same pattern as fund-transfer.
4. Use Resilience4j @CircuitBreaker with fallbackMethod annotation.
```

---

## Summary Timeline

```
Week 1-2:   Phase 1 (Quick Wins)
            ├── Fix balance bug (GAP-RES-06)
            ├── Add input validation (GAP-SEC-02)
            ├── Externalize credentials (GAP-SEC-01)
            ├── Fix HTTP status codes (GAP-ERR-01)
            ├── Add Feign error decoders (GAP-ERR-04)
            ├── Fix password leakage (GAP-SEC-03)
            ├── Add timeouts (GAP-RES-03)
            ├── Add retry policies (GAP-RES-02)
            └── Fix entity relationship (GAP-RES-07)

Week 3-8:   Phase 2 (Important)
            ├── Circuit breakers (GAP-RES-01)
            ├── Internal service auth (GAP-SEC-06)
            ├── Unit tests for all services (GAP-TEST-01)
            ├── Idempotency protection (GAP-RES-05)
            ├── Flyway everywhere (GAP-ORG-03)
            ├── Shared library (GAP-ORG-01)
            ├── Prometheus metrics (GAP-OBS-03)
            ├── Fix OpenAPI config (GAP-API-04)
            ├── CORS config (GAP-SEC-05)
            └── Keycloak singleton fix (GAP-SEC-04)

Week 9-16:  Phase 3 (Polish)
            ├── Integration tests (GAP-TEST-02)
            ├── Contract tests (GAP-TEST-03)
            ├── CI/CD pipeline
            ├── Package structure cleanup (GAP-ORG-02)
            ├── Pagination improvements (GAP-API-03)
            ├── URL standardization (GAP-API-05)
            ├── Structured logging (GAP-OBS-01)
            ├── Custom health checks (GAP-OBS-02)
            ├── Dependency scanning (GAP-SEC-07)
            └── Fallback behavior (GAP-RES-04)
```

## Priority Matrix

| | Small Effort | Medium Effort | Large Effort |
|---|---|---|---|
| **Critical** | GAP-RES-06 (balance bug), GAP-SEC-01 (credentials) | GAP-SEC-02 (validation) | |
| **High** | GAP-ERR-01 (status codes), GAP-ERR-04 (Feign decoder), GAP-SEC-03 (password), GAP-RES-02 (retry), GAP-RES-03 (timeout) | GAP-RES-01 (circuit breaker), GAP-SEC-06 (internal auth), GAP-RES-05 (idempotency) | GAP-TEST-01 (unit tests), GAP-TEST-02 (integration tests) |
| **Medium** | GAP-OBS-01 (logging), GAP-OBS-03 (metrics), GAP-API-01 (typed ResponseEntity), GAP-API-04 (OpenAPI), GAP-SEC-05 (CORS), GAP-SEC-04 (singleton), GAP-RES-07 (entity rel), GAP-SEC-07 (dep scan) | GAP-ORG-01 (shared lib), GAP-ORG-03 (Flyway), GAP-RES-04 (fallback) | GAP-TEST-03 (contract tests) |
| **Low** | GAP-ORG-02 (packages), GAP-API-03 (pagination), GAP-API-05 (URLs), GAP-OBS-02 (health), GAP-OBS-04 (Zipkin), GAP-TEST-04 (H2 config), GAP-ERR-03 (exception hierarchy) | GAP-API-02 (versioning), GAP-API-06 (HATEOAS) | |
