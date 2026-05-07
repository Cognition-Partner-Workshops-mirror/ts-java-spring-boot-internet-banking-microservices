# Remediation Roadmap

## Overview

This roadmap prioritizes the 36 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): Critical bugs and small-effort fixes that immediately improve safety and correctness
- **Phase 2 — Important** (3-6 weeks): High-impact structural improvements requiring moderate effort
- **Phase 3 — Polish** (6-12 weeks): Low-severity refinements and advanced capabilities

Each item includes a sample Devin prompt that can be used to implement the remediation.

---

## Phase 1: Quick Wins

*Focus: Fix critical bugs, plug security holes, and improve error handling. All items are Small effort.*

### 1.1 Fix Balance Calculation Bug (GAP-RES-07)

**Severity: Critical | Effort: Small**

The `availableBalance` is double-subtracted/added in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`. Every transaction corrupts account balances.

**Devin Prompt:**
```
In core-banking-service TransactionService.java, fix the balance calculation bug.
In internalFundTransfer(), lines 90-91: availableBalance should be set to
actualBalance (after subtraction) without subtracting amount again. Same fix
needed for the credit side (lines 99-100) and in utilPayment() (lines 63-64).
The correct pattern is:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance());
Add unit tests for TransactionService covering fund transfer and utility payment
balance calculations. Verify both actualBalance and availableBalance are correct
after each operation.
```

### 1.2 Add Input Validation to All Request DTOs (GAP-SEC-02)

**Severity: High | Effort: Small**

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:
- FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
  @NotBlank on referenceNumber/account
- User (registration): @Email @NotBlank on email, @NotBlank @Size(min=6) on password,
  @NotBlank on identification
- UserUpdateRequest: @NotNull on status
Add @Valid to all @RequestBody parameters in controllers. Add spring-boot-starter-validation
dependency to each service's build.gradle. Add a MethodArgumentNotValidException handler
to each GlobalExceptionHandler that returns 422 with field-level error details.
```

### 1.3 Fix HTTP Status Codes in Error Handlers (GAP-ERR-01, GAP-ERR-02)

**Severity: High | Effort: Small**

**Devin Prompt:**
```
Update GlobalExceptionHandler in all 4 business services:
1. EntityNotFoundException -> return HTTP 404 (not 400)
2. InsufficientFundsException -> return HTTP 422
3. UserAlreadyRegisteredException -> return HTTP 409
4. InvalidEmailException, InvalidBankingUserException -> return HTTP 400 (keep as-is)
5. Generic Exception handler -> return HTTP 500 with structured ErrorResponse
   (never expose raw exception toString to clients)
6. Add @ResponseStatus annotations to custom exceptions as appropriate
7. Ensure all handlers return ErrorResponse objects (not plain strings)
Make the error response consistent: { "code": "...", "message": "...", "timestamp": "..." }
```

### 1.4 Prevent Password Logging (GAP-SEC-07)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In internet-banking-user-service, the User DTO uses @Data which includes password
in toString(). The controller logs request.toString() at INFO level, which would
log passwords in cleartext.
Fix this by:
1. Add @ToString.Exclude on the password field in User.java
2. Alternatively, replace @Data with @Getter/@Setter and write a custom toString()
   that excludes password
3. Review all log.info() calls across all services to ensure no sensitive data
   (passwords, tokens, full request bodies) is logged
```

### 1.5 Fix Keycloak Singleton Thread Safety (GAP-SEC-04)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In internet-banking-user-service KeycloakProperties.java, the Keycloak singleton
is not thread-safe. Fix by converting it to a Spring @Bean:
1. Remove the static keycloakInstance field and getInstance() method
2. Create a @Configuration class that defines a @Bean Keycloak method
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance()
This ensures Spring manages the lifecycle and provides thread-safe singleton semantics.
```

### 1.6 Add Feign Client Timeout Configuration (GAP-RES-03)

**Severity: High | Effort: Small**

**Devin Prompt:**
```
Add explicit timeout configuration for all Feign clients. In each service that uses
Feign (fund-transfer-service, utility-payment-service, user-service), add to
application.yml or the config server properties:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Also configure connection pool settings for the underlying HTTP client.
```

### 1.7 Add Retry Policies for Feign Clients (GAP-RES-02)

**Severity: High | Effort: Small**

**Devin Prompt:**
```
Add Spring Retry support for Feign clients in fund-transfer-service,
utility-payment-service, and user-service:
1. Add 'org.springframework.retry:spring-retry' and
   'org.springframework:spring-aop' dependencies to each build.gradle
2. Enable retry via application.yml:
   spring.cloud.openfeign.client.config.default.retryer: feign.Retryer.Default
3. Configure max retries (3), initial backoff (1s), and max backoff (5s)
4. Only retry on connection failures and 5xx responses, NOT on 4xx
```

### 1.8 Externalize Secrets from Source Code (GAP-SEC-01)

**Severity: Critical | Effort: Small**

**Devin Prompt:**
```
Remove hardcoded credentials from docker-compose files and SQL scripts:
1. In docker-compose.yml and docker-compose-support-apps.yml, replace all
   hardcoded passwords with environment variable references:
   MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
   KC_DB_PASSWORD: ${KC_DB_PASSWORD}
   KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
   POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
2. Create a docker-compose/.env.example file with placeholder values
3. Add docker-compose/.env to .gitignore
4. Update privileges.sql to use environment variable substitution or
   document the need to change the default password
5. Remove test credentials from README.md and reference the .env.example file
```

### 1.9 Fix Logging Issues (GAP-OBS-01)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Fix logging issues across all services:
1. core-banking-service AccountController: fix typo "utitlity" -> "utility"
2. fund-transfer-service FundTransferService: fix broken log statement
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To:     log.info("Sending fund transfer request {}", request)
3. Ensure all services consistently use @Slf4j and parameterized logging
4. Remove or redact logging of full request objects that may contain PII
```

### 1.10 Restrict Database Permissions (GAP-SEC-06)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Update docker-compose/mysql/privileges.sql to use least-privilege access:
Instead of granting all permissions on *.*, grant only the needed permissions
on specific databases:
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_service.* TO 'javatodev_development'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* TO ...;
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* TO ...;
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* TO ...;
Keep CREATE/ALTER only for migration user if needed, or handle schema via Flyway
with a separate privileged user.
```

---

## Phase 2: Important

*Focus: Structural improvements for resilience, testability, and maintainability. Medium effort items.*

### 2.1 Add Circuit Breakers (GAP-RES-01)

**Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls:
1. Add dependencies to fund-transfer-service, utility-payment-service, user-service:
   - 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
2. Configure circuit breaker defaults in application.yml:
   resilience4j.circuitbreaker.instances.coreBankingService:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     permittedNumberOfCallsInHalfOpenState: 3
3. Add @CircuitBreaker annotations to Feign client methods or create wrapper
   service classes with fallback methods
4. Implement fallback behavior:
   - fund-transfer: return error response with FAILED status
   - utility-payment: return error response with FAILED status
   - user-service: return cached user data or appropriate error
```

### 2.2 Fix Transaction Integrity (GAP-RES-06)

**Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Implement a saga pattern for the fund transfer and utility payment flows:
1. Add idempotency keys to FundTransferRequest and UtilityPaymentRequest
2. In fund-transfer-service:
   a. Generate a unique transaction ID before calling core-banking
   b. Pass the transaction ID to core-banking-service
   c. If the Feign call fails, update local entity to FAILED status
   d. Add a compensating endpoint in core-banking to reverse a transfer
   e. Add a scheduled job to reconcile PENDING transfers older than X minutes
3. In utility-payment-service: same pattern
4. In core-banking-service:
   a. Check for duplicate transaction IDs before processing
   b. Add a reversal/compensation endpoint
5. Add unit and integration tests for failure scenarios
```

### 2.3 Handle Feign Client Failures Gracefully (GAP-ERR-04)

**Severity: High | Effort: Medium**

**Devin Prompt:**
```
Add proper error handling for Feign client failures in all consuming services:
1. Create a FeignErrorDecoder that:
   - Maps 404 responses to EntityNotFoundException
   - Maps 422 responses to business-specific exceptions
   - Maps 5xx responses to ServiceUnavailableException
   - Preserves the original error response body
2. Wrap Feign calls in try-catch blocks in service classes:
   - On failure, update local entity status to FAILED
   - Log the error with correlation/trace ID
   - Return meaningful error response to the client
3. Add the error decoder to each Feign client configuration
```

### 2.4 Create Shared Common Library (GAP-ORG-01)

**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Create a shared common library module for cross-cutting concerns:
1. Create a new Gradle module: internet-banking-common
2. Move shared classes into it:
   - exception/ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - model/dto/AuditAware
   - configuration/filter/AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - model/mapper/BaseMapper
3. Publish to local Maven repository or use Gradle composite builds
4. Update all service build.gradle files to depend on the common module
5. Remove duplicated classes from individual services
6. Consider creating a root settings.gradle for multi-project build
```

### 2.5 Add Unit Test Coverage (GAP-TEST-01)

**Severity: Critical | Effort: Large**

**Devin Prompt:**
```
Add comprehensive unit tests across all business services. Target 80%+ coverage
for service and controller layers. For each service:

core-banking-service:
- AccountServiceTest: test readBankAccount (found, not found), readUtilityAccount
- TransactionServiceTest: test fundTransfer (success, insufficient funds),
  utilPayment (success, insufficient funds), balance calculations
- UserServiceTest: test readUser, readUsers

internet-banking-user-service:
- UserServiceTest: test createUser (success, duplicate email, email mismatch,
  user not found in core), readUsers, readUser, updateUser (approve flow)
- Mock KeycloakUserService and BankingCoreRestClient

internet-banking-fund-transfer-service:
- FundTransferServiceTest: test fundTransfer (success, core banking failure),
  readAllTransfers
- Mock BankingCoreFeignClient

internet-banking-utility-payment-service:
- UtilityPaymentServiceTest: test utilPayment (success, failure), readPayments
- Mock BankingCoreRestClient

Use Mockito for mocking, AssertJ for assertions. Add test fixtures/factories
for creating test entities.
```

### 2.6 Add Test Configuration for Isolation (GAP-TEST-02, GAP-TEST-03)

**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Create proper test configuration so tests run in isolation:
1. For each service, create src/test/resources/application-test.yml:
   - Disable Eureka registration: eureka.client.enabled=false
   - Disable Spring Cloud Config: spring.cloud.config.enabled=false
   - Use H2 in-memory database
   - Disable Flyway for test profile (or point to test migrations)
2. Create test @Configuration classes that mock external dependencies:
   - Mock Feign clients with @MockBean
   - Mock KeycloakUserService for user-service tests
3. Use @ActiveProfiles("test") on all test classes
4. Add Testcontainers for MySQL integration tests (optional)
5. Verify all existing @SpringBootTest context load tests pass
```

### 2.7 Add Rate Limiting to API Gateway (GAP-RES-05)

**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's
RequestRateLimiter filter:
1. Add Redis dependency: 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
2. Configure rate limiter in gateway routes (via config server properties):
   spring.cloud.gateway.routes[*].filters:
     - name: RequestRateLimiter
       args:
         redis-rate-limiter.replenishRate: 10
         redis-rate-limiter.burstCapacity: 20
         key-resolver: "#{@userKeyResolver}"
3. Create a KeyResolver bean that resolves rate limit keys by:
   - Authenticated user ID (from JWT)
   - IP address as fallback for unauthenticated requests
4. Add Redis to docker-compose.yml
5. Return HTTP 429 with Retry-After header when limit exceeded
```

### 2.8 Add Prometheus Metrics (GAP-OBS-04)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add Prometheus metrics export to all services:
1. Add 'io.micrometer:micrometer-registry-prometheus' to each build.gradle
2. Expose Prometheus endpoint in actuator:
   management.endpoints.web.exposure.include: health,info,prometheus,metrics
3. Add custom business metrics using Micrometer:
   - Counter: fund_transfers_total (tags: status=success|failed)
   - Counter: utility_payments_total (tags: status=success|failed)
   - Counter: user_registrations_total (tags: status=success|failed)
   - Timer: fund_transfer_duration_seconds
   - Gauge: active_accounts_count
4. Add Prometheus service to docker-compose.yml with scrape config
5. Optionally add a basic Grafana dashboard JSON
```

### 2.9 Add Structured Logging (GAP-OBS-02)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add structured JSON logging configuration to all services:
1. Add logback-spring.xml to each service's src/main/resources/:
   - Console appender with JSON format for docker profile
   - Pattern appender with readable format for local development
   - Include traceId and spanId in log output (from Micrometer)
2. Use net.logstash.logback:logstash-logback-encoder:7.4 for JSON encoding
3. Configure log levels per environment via Spring Cloud Config:
   - Development: DEBUG for com.javatodev, INFO for everything else
   - Production: INFO for com.javatodev, WARN for everything else
4. Add MDC context (userId, transactionId) to service layer methods
```

### 2.10 Add Dependency Vulnerability Scanning (GAP-SEC-05)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Set up dependency vulnerability scanning:
1. Add the OWASP Dependency Check Gradle plugin to each service:
   plugins { id 'org.owasp.dependencycheck' version '9.0.9' }
2. Configure it to fail on CVSS score >= 7:
   dependencyCheck { failBuildOnCVSS = 7.0 }
3. Add a GitHub Actions workflow that runs dependency-check on PRs
4. Alternatively, add a Dependabot configuration at .github/dependabot.yml
   to auto-create PRs for dependency updates
5. Review and update any dependencies with known vulnerabilities
```

---

## Phase 3: Polish

*Focus: API refinements, advanced observability, and developer experience. Low-severity items.*

### 3.1 Add Pagination Metadata to Responses (GAP-API-03)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Update all list endpoints to return pagination metadata:
1. Create a shared PageResponse<T> wrapper:
   { "content": [...], "page": 0, "size": 20, "totalElements": 100,
     "totalPages": 5, "last": false }
2. Update service methods to return Page<T> instead of List<T>
3. Update controllers to wrap the response
4. Apply to: UserController.readUsers(), FundTransferController.readFundTransfers(),
   UtilityPaymentController.readPayments(), core-banking UserController.readUsers()
```

### 3.2 Add Type Parameters to ResponseEntity (GAP-API-01)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add proper generic type parameters to all ResponseEntity return types in controllers:
- ResponseEntity -> ResponseEntity<BankAccount>
- ResponseEntity -> ResponseEntity<FundTransferResponse>
- ResponseEntity -> ResponseEntity<List<User>>
etc.
This improves compile-time type safety and generates better OpenAPI documentation.
Apply to all controller methods across all 4 business services.
```

### 3.3 Standardize Package Structure (GAP-ORG-03)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Standardize the package structure across all services to follow a consistent pattern:
  com.javatodev.finance/
    controller/
    service/
    repository/
    model/
      entity/
      dto/
        request/
        response/
      mapper/
    exception/
    configuration/
Move any classes that are in non-standard locations (e.g., repository inside model,
rest inside model) to the standard locations. Update all imports accordingly.
```

### 3.4 Convert Mappers to Spring Beans (GAP-ORG-04)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Convert all mapper classes from manual instantiation to Spring-managed beans:
1. Add @Component to each mapper class (UserMapper, BankAccountMapper,
   FundTransferMapper, UtilityPaymentMapper, UtilityAccountMapper)
2. Replace field instantiation in service classes:
   Before: private UserMapper userMapper = new UserMapper();
   After:  private final UserMapper userMapper; (injected via @RequiredArgsConstructor)
3. Alternatively, consider adopting MapStruct for compile-time mapper generation
```

### 3.5 Fix PATCH Endpoint Naming (GAP-API-06)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
In internet-banking-user-service UserController.java:
Rename PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
The "update" in the path is redundant since PATCH already implies update.
Update the gateway route configuration if applicable. Update any Postman
collection references.
```

### 3.6 Add Filtering and Sorting (GAP-API-04)

**Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Add filtering and sorting capabilities to list endpoints:
1. Fund transfers: filter by status, fromAccount, toAccount, date range
2. Utility payments: filter by providerId, status, date range
3. Users (core-banking): filter by email, name
4. Use Spring Data JPA Specifications or QueryDSL for dynamic queries
5. Accept filter parameters as query parameters:
   GET /api/v1/transfer?status=SUCCESS&fromAccount=100015003000&sort=createdDate,desc
6. Add corresponding repository methods and service layer logic
```

### 3.7 Add Custom Health Indicators (GAP-OBS-03)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Add custom health indicators to each service:
1. core-banking-service: database connectivity check
2. user-service: Keycloak connectivity check (ping admin API)
3. fund-transfer-service: core-banking-service availability check
4. utility-payment-service: core-banking-service availability check
5. All services: config server connectivity check

Implement as Spring Boot HealthIndicator beans. Configure actuator to show
details: management.endpoint.health.show-details=always (for internal use)
or when-authorized (for production).
```

### 3.8 Add Fallback Behavior (GAP-RES-04)

**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Implement fallback behavior for Feign client failures:
1. For readAccount in fund-transfer and utility-payment services:
   - Return a cached account response (implement simple Redis or in-memory cache)
   - Or return an error DTO indicating the core service is unavailable
2. For readUser in user-service:
   - Return locally stored user data (user-service has its own DB)
3. For transaction endpoints:
   - Queue the request for later processing (requires RabbitMQ integration)
   - Or return a PENDING status and implement async reconciliation
Use @FeignClient fallbackFactory for structured fallback with error context.
```

### 3.9 Document CSRF Decision (GAP-SEC-03)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add documentation explaining the CSRF configuration decision in the API Gateway:
1. Add a code comment in SecurityConfiguration.java explaining why CSRF is disabled
   (stateless JWT-based API, no browser session cookies)
2. Add a security section to the project README documenting:
   - Authentication flow (OAuth2 JWT via Keycloak)
   - CSRF protection rationale
   - CORS configuration (currently missing, should be added)
3. Add CORS configuration to the gateway if browser clients will call the API directly
```

### 3.10 Set Up Monitoring Dashboards (GAP-OBS-05)

**Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Create monitoring infrastructure:
1. Add Grafana and Prometheus to docker-compose.yml
2. Create Prometheus scrape config for all service actuator endpoints
3. Create Grafana dashboards:
   - Service health overview (up/down status, response times)
   - Business metrics (transfers/min, payments/min, registrations/min)
   - JVM metrics (heap usage, GC, thread count)
   - Database connection pool metrics
4. Add basic Prometheus alerting rules:
   - Service down for > 1 minute
   - Error rate > 5%
   - Response time p95 > 2 seconds
5. Export dashboard JSON files to the repository for version control
```

### 3.11 Set Up Multi-Project Gradle Build (GAP-ORG-02)

**Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Create a multi-project Gradle build for unified build management:
1. Create a root settings.gradle that includes all 7 service projects
2. Create a root build.gradle with:
   - Shared repository declarations
   - Common dependency versions via a version catalog (gradle/libs.versions.toml)
   - Shared plugin configuration
   - Subproject configuration for common dependencies
3. Keep individual build.gradle files for service-specific dependencies
4. Add Gradle tasks for building all services, running all tests, etc.
5. Ensure each service can still be built independently
```

### 3.12 Establish API Versioning Strategy (GAP-API-02)

**Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Document and implement an API versioning strategy:
1. Document the current v1 API contract in an OpenAPI specification file per service
2. Add API versioning support via:
   - URL path versioning (current: /api/v1/) - document as the chosen strategy
   - Add a gateway route configuration pattern for version routing
3. Create a deprecation policy document:
   - How long old versions are supported
   - How clients are notified of deprecation
   - Migration guide template
4. Add a Sunset header to deprecated endpoints
```

### 3.13 Standardize Endpoint Naming (GAP-API-05)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Standardize REST endpoint naming conventions:
1. Use consistent plural nouns for all resources:
   /api/v1/accounts (not /account)
   /api/v1/users (not /user)
   /api/v1/transfers (not /transfer)
   /api/v1/utility-payments (not /utility-payment)
2. Use full words (not abbreviations):
   /api/v1/accounts/utility/{name} (not /util-account)
3. Update gateway route mappings accordingly
4. Document the naming conventions in a style guide
Note: This is a breaking change; consider versioning (v2) or keeping v1 aliases.
```

---

## Summary Timeline

```
Phase 1 (Weeks 1-2): Quick Wins
├── 1.1  Fix balance calculation bug          [Critical, Day 1]
├── 1.2  Add input validation                 [High, Day 1-2]
├── 1.3  Fix HTTP status codes                [High, Day 2]
├── 1.4  Prevent password logging             [Medium, Day 2]
├── 1.5  Fix Keycloak thread safety           [Medium, Day 3]
├── 1.6  Add Feign timeouts                   [High, Day 3]
├── 1.7  Add retry policies                   [High, Day 3]
├── 1.8  Externalize secrets                  [Critical, Day 4]
├── 1.9  Fix logging issues                   [Medium, Day 4]
└── 1.10 Restrict DB permissions              [Medium, Day 5]

Phase 2 (Weeks 3-6): Important
├── 2.1  Add circuit breakers                 [Critical]
├── 2.2  Fix transaction integrity            [Critical]
├── 2.3  Handle Feign failures gracefully     [High]
├── 2.4  Create shared common library         [Medium]
├── 2.5  Add unit test coverage               [Critical]
├── 2.6  Add test configuration               [Medium]
├── 2.7  Add rate limiting                    [Medium]
├── 2.8  Add Prometheus metrics               [Medium]
├── 2.9  Add structured logging               [Medium]
└── 2.10 Add vulnerability scanning           [Medium]

Phase 3 (Weeks 7-12): Polish
├── 3.1  Pagination metadata                  [Medium]
├── 3.2  ResponseEntity type parameters       [Medium]
├── 3.3  Standardize package structure        [Low]
├── 3.4  Convert mappers to Spring beans      [Low]
├── 3.5  Fix PATCH naming                     [Low]
├── 3.6  Add filtering/sorting                [Low]
├── 3.7  Custom health indicators             [Low]
├── 3.8  Fallback behavior                    [Medium]
├── 3.9  Document CSRF decision               [Medium]
├── 3.10 Monitoring dashboards                [Low]
├── 3.11 Multi-project Gradle build           [Low]
├── 3.12 API versioning strategy              [Low]
└── 3.13 Standardize endpoint naming          [Low]
```

## Key Metrics to Track

| Metric | Current | Phase 1 Target | Phase 2 Target | Phase 3 Target |
|---|---|---|---|---|
| Critical gaps | 5 | 2 | 0 | 0 |
| Unit test coverage | ~0% | ~20% | ~80% | ~85% |
| Known CVEs | Unknown | Scanned | 0 high/critical | 0 medium+ |
| Error response consistency | ~30% | ~90% | 100% | 100% |
| Hardcoded secrets | 5+ | 0 | 0 | 0 |
| Services with circuit breakers | 0/3 | 0/3 | 3/3 | 3/3 |
