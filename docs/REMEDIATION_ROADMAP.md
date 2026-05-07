# Remediation Roadmap

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Created:** 2026-05-07
> **Based on:** [Gap Analysis](./GAP_ANALYSIS.md)

---

## Phasing Strategy

| Phase | Focus | Timeline | Selection Criteria |
|-------|-------|----------|-------------------|
| **Phase 1** | Quick Wins | 1-2 weeks | Critical bugs, security fixes, and small-effort items that deliver immediate value |
| **Phase 2** | Important | 3-6 weeks | High-impact architectural improvements that require moderate effort |
| **Phase 3** | Polish | 6-12 weeks | Nice-to-have improvements for long-term maintainability and developer experience |

---

## Phase 1: Quick Wins

### 1.1 Fix Balance Calculation Bug (GAP-RES-06)
- **Severity:** Critical | **Effort:** Small
- **Description:** Fix the double-subtraction bug in `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()` where `availableBalance` is incorrectly computed.
- **Files:** `core-banking-service/.../service/TransactionService.java`

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.
In internalFundTransfer() lines 90-91 and utilPayment() lines 63-64,
availableBalance is set to actualBalance.subtract(amount) AFTER actualBalance
was already reduced, causing a double subtraction.

The fix: after setting actualBalance, set availableBalance to the same value
as the new actualBalance (not subtract amount again). Apply the same fix to
both the debit and credit sides in internalFundTransfer() and the debit
in utilPayment(). Update the existing unit tests in TransactionServiceTest
to verify correct balance calculations.
```

---

### 1.2 Fix HTTP Status Codes in Error Handlers (GAP-ERR-01)
- **Severity:** High | **Effort:** Small
- **Description:** Map exceptions to correct HTTP status codes instead of returning 400 for everything.
- **Files:** `*/exception/GlobalExceptionHandler.java` (4 files)

**Devin Prompt:**
```
Update GlobalExceptionHandler in all 4 business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to return proper HTTP status codes:

- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException -> 400 Bad Request
- InvalidBankingUserException -> 400 Bad Request
- SimpleBankingGlobalException (generic) -> 400 Bad Request
- Exception (catch-all) -> 500 Internal Server Error

Also fix the catch-all handler to return a structured ErrorResponse instead
of a raw string, and log the exception server-side without exposing internals
to the client (GAP-ERR-02, GAP-ERR-03).
```

---

### 1.3 Remove Sensitive Data Exposure (GAP-SEC-06, GAP-OBS-04)
- **Severity:** High | **Effort:** Small
- **Description:** Prevent password from appearing in API responses and stop logging sensitive request data.
- **Files:** `internet-banking-user-service/.../model/dto/User.java`, all `*Controller.java` files

**Devin Prompt:**
```
In internet-banking-user-service, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
to the password field in model/dto/User.java so the password is never
serialized in responses.

Also update all controller classes across all services to stop logging
full request toString() output. Replace with sanitized log messages that
only include non-sensitive identifiers (e.g., account number last 4 digits,
user ID, but never passwords or full account numbers).

Reduce Feign logging from Level.FULL to Level.BASIC in
CustomFeignClientConfiguration in fund-transfer-service and
utility-payment-service.
```

---

### 1.4 Add Input Validation (GAP-SEC-03)
- **Severity:** Critical | **Effort:** Medium
- **Description:** Add Jakarta Bean Validation annotations to all DTOs and `@Valid` on controller parameters.
- **Files:** All DTO classes and controller classes across all services

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across all services:

core-banking-service FundTransferRequest:
- fromAccount: @NotBlank
- toAccount: @NotBlank
- amount: @NotNull @Positive

core-banking-service UtilityPaymentRequest:
- providerId: @NotNull
- amount: @NotNull @Positive
- referenceNumber: @NotBlank
- account: @NotBlank

internet-banking-user-service User (registration):
- email: @NotBlank @Email
- identification: @NotBlank
- password: @NotBlank @Size(min=8)

internet-banking-fund-transfer-service FundTransferRequest:
- fromAccount: @NotBlank
- toAccount: @NotBlank
- amount: @NotNull @Positive

internet-banking-utility-payment-service UtilityPaymentRequest:
- providerId: @NotNull
- amount: @NotNull @Positive
- referenceNumber: @NotBlank
- account: @NotBlank

Add @Valid annotation to all @RequestBody parameters in controllers.
Add spring-boot-starter-validation dependency to each service's build.gradle
if not already present. Update GlobalExceptionHandler to handle
MethodArgumentNotValidException and return 400 with field-level error details.
```

---

### 1.5 Externalize Docker Compose Secrets (GAP-SEC-01)
- **Severity:** Critical | **Effort:** Small
- **Description:** Move hardcoded credentials out of Docker Compose and Dockerfile into a `.env` file.
- **Files:** `docker-compose/*.yml`, `docker-compose/mysql/Dockerfile`

**Devin Prompt:**
```
Externalize all hardcoded credentials in the docker-compose directory:

1. Create docker-compose/.env.example with placeholder values:
   MYSQL_ROOT_PASSWORD=changeme
   MYSQL_APP_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   KC_DB_PASSWORD=changeme

2. Create docker-compose/.env with the actual development values
   (add .env to .gitignore).

3. Update docker-compose.yml and docker-compose-support-apps.yml to
   reference ${VARIABLE} syntax instead of hardcoded values.

4. Update docker-compose/mysql/Dockerfile to use ARG/ENV from build args
   instead of hardcoded MYSQL_ROOT_PASSWORD.

5. Update docker-compose/mysql/privileges.sql to use a parameterized
   password or document that it needs manual updating.

6. Remove the test credentials line from README.md (GAP-SEC-02).
```

---

### 1.6 Add Feign Timeout Configuration (GAP-RES-03)
- **Severity:** High | **Effort:** Small
- **Description:** Set explicit connection and read timeouts for all Feign clients and database connections.
- **Files:** Application config files (remote config repo or bootstrap overrides)

**Devin Prompt:**
```
Add timeout configuration for all Feign clients and database connections.

For each service that uses Feign (user-service, fund-transfer-service,
utility-payment-service), add to application.yml or the remote config:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Also add HikariCP timeout configuration for all services with database access:

spring:
  datasource:
    hikari:
      connectionTimeout: 5000
      maxLifetime: 1800000
      maximumPoolSize: 10

Since configs are managed via the remote config repo, create the config
changes as a separate commit or document exactly what properties need to
be added to each service's config file in the remote repository.
```

---

### 1.7 Fix Application Context Tests (GAP-TST-04)
- **Severity:** Medium | **Effort:** Small
- **Description:** Make default `*ApplicationTests` classes work in CI without running infrastructure.
- **Files:** `*/src/test/java/**/*ApplicationTests.java`, `*/src/test/resources/application.yml`

**Devin Prompt:**
```
Fix the default *ApplicationTests.java classes in all services so they
can run without a Config Server or Eureka:

1. For each service (user-service, fund-transfer-service,
   utility-payment-service, api-gateway), create or update
   src/test/resources/application.yml with:
   - spring.cloud.config.enabled: false
   - eureka.client.enabled: false
   - Spring datasource pointing to H2 in-memory (for services with JPA)

2. For user-service, mock or disable the Keycloak dependency in tests.

3. Ensure all *ApplicationTests load the Spring context successfully
   when run via ./gradlew test.
```

---

### 1.8 Fix OpenAPI Starter Dependency (GAP-API-06)
- **Severity:** Low | **Effort:** Small
- **Description:** Switch from WebFlux OpenAPI starter to WebMVC starter for servlet-based services.
- **Files:** `build.gradle` in core-banking-service, user-service, fund-transfer-service, utility-payment-service

**Devin Prompt:**
```
In the build.gradle of core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and
internet-banking-utility-payment-service, replace:

  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'

with:

  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (servlet stack), not WebFlux. Only the
api-gateway uses WebFlux. Verify that Swagger UI still works after
the change.
```

---

## Phase 2: Important

### 2.1 Add Compensation Logic for Distributed Transactions (GAP-ERR-05)
- **Severity:** Critical | **Effort:** Medium
- **Description:** Implement proper error handling when Feign calls fail during fund transfers and utility payments.
- **Files:** `internet-banking-fund-transfer-service/.../service/FundTransferService.java`, `internet-banking-utility-payment-service/.../service/UtilityPaymentService.java`

**Devin Prompt:**
```
Add proper error handling and compensation logic for distributed transactions
in FundTransferService and UtilityPaymentService:

1. Wrap the bankingCoreFeignClient call in a try-catch block.
2. On FeignException or any exception:
   - Update the local entity status to FAILED.
   - Log the error with the transaction reference.
   - Return an error response to the client (don't let raw Feign
     exceptions propagate).
3. Consider adding a TransactionStatus.FAILED enum value if not present.
4. Add unit tests for the failure scenarios.

Do NOT implement a full saga pattern yet -- just ensure failures are
properly recorded and communicated to clients.
```

---

### 2.2 Add Idempotency Keys for Financial Operations (GAP-RES-05)
- **Severity:** Critical | **Effort:** Medium
- **Description:** Prevent double-processing of fund transfers and payments when clients retry.
- **Files:** Fund transfer and utility payment controllers and services

**Devin Prompt:**
```
Implement idempotency for financial operations in fund-transfer-service
and utility-payment-service:

1. Accept an "Idempotency-Key" HTTP header on POST endpoints.
2. Before processing, check if a transaction with that idempotency key
   already exists in the database.
3. If found, return the existing result without reprocessing.
4. If not found, proceed with normal processing and store the
   idempotency key with the transaction record.
5. Add an "idempotency_key" column to fund_transfer and utility_payment
   tables with a unique constraint.
6. Add appropriate error handling for missing idempotency keys
   (return 400 if header is absent on financial POST endpoints).
7. Add unit tests covering duplicate request scenarios.
```

---

### 2.3 Add Circuit Breakers (GAP-RES-01)
- **Severity:** High | **Effort:** Medium
- **Description:** Add Resilience4j circuit breakers to prevent cascading failures.
- **Files:** All services with Feign clients, `build.gradle` files

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies to build.gradle for each service that uses Feign:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled: true

3. Create fallback classes for each Feign client:
   - BankingCoreRestClientFallback (user-service)
   - BankingCoreFeignClientFallback (fund-transfer-service)
   - BankingCoreRestClientFallback (utility-payment-service)

4. Fallbacks should return meaningful error responses (e.g.,
   "Core banking service is currently unavailable").

5. Configure circuit breaker parameters:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 10s

6. Add unit tests for fallback behavior.
```

---

### 2.4 Extract Shared Library (GAP-ORG-01)
- **Severity:** Medium | **Effort:** Medium
- **Description:** Create a shared module to eliminate code duplication.
- **Files:** New `shared-library/` module, all services

**Devin Prompt:**
```
Create a shared Gradle module to eliminate duplicated code:

1. Create a new module: shared-library/ with build.gradle.
2. Move the following common classes into it:
   - BaseMapper interface
   - AuditAware mapped superclass
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - ErrorResponse, SimpleBankingGlobalException
   - GlobalExceptionHandler (base class)
   - CustomFeignClientConfiguration
3. Set up proper package: com.javatodev.finance.common.*
4. Add shared-library as a dependency in each service's build.gradle:
   implementation project(':shared-library')
5. Create a root settings.gradle that includes all services and the
   shared library.
6. Remove the duplicated classes from each service.
7. Verify all services compile and tests pass.
```

---

### 2.5 Add Unit Tests for All Services (GAP-TST-01)
- **Severity:** High | **Effort:** Large
- **Description:** Add service-layer unit tests for user-service, fund-transfer-service, and utility-payment-service.
- **Files:** `*/src/test/java/**/*Test.java`

**Devin Prompt:**
```
Add comprehensive unit tests for all services that currently lack them:

internet-banking-user-service:
- UserServiceTest: test createUser (success, duplicate email, invalid
  email, user not in core banking), readUsers, readUser, updateUser
  (approve flow, entity not found).
- KeycloakUserServiceTest: test createUser, readUser, readUserByEmail,
  updateUser with mocked KeycloakManager.

internet-banking-fund-transfer-service:
- FundTransferServiceTest: test fundTransfer (success, Feign failure),
  readAllTransfers.

internet-banking-utility-payment-service:
- UtilityPaymentServiceTest: test utilPayment (success, Feign failure),
  readPayments.

Use Mockito for mocking dependencies. Follow the same patterns used in
core-banking-service tests. Target at least 80% line coverage for
service classes.
```

---

### 2.6 Add Feign Error Decoders (GAP-ERR-04)
- **Severity:** Medium | **Effort:** Small
- **Description:** Add custom error decoders to translate Feign errors into domain exceptions.
- **Files:** Fund-transfer and utility-payment services

**Devin Prompt:**
```
Add CustomFeignErrorDecoder to internet-banking-fund-transfer-service
and internet-banking-utility-payment-service, similar to the one in
internet-banking-user-service:

1. Create a CustomFeignErrorDecoder class that implements feign.codec.ErrorDecoder.
2. Map HTTP status codes from core-banking-service to appropriate
   domain exceptions:
   - 404 -> EntityNotFoundException
   - 422 -> InsufficientFundsException (or a new domain exception)
   - 400 -> SimpleBankingGlobalException with the error message
   - Other -> SimpleBankingGlobalException with generic message
3. Register the error decoder in CustomFeignClientConfiguration.
4. Add unit tests for the error decoder.
```

---

### 2.7 Add Structured Logging (GAP-OBS-01)
- **Severity:** Medium | **Effort:** Medium
- **Description:** Configure JSON structured logging for all services.
- **Files:** `*/src/main/resources/logback-spring.xml` (new), `build.gradle` files

**Devin Prompt:**
```
Add JSON structured logging to all services:

1. Add logstash-logback-encoder dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with LogstashEncoder for all profiles
   - Include trace ID and span ID in log output
   - Set reasonable log levels (INFO for app, WARN for frameworks)

3. Configure per-profile behavior:
   - dev profile: human-readable text format
   - docker/prod profile: JSON format

4. Verify trace correlation IDs appear in log output.
```

---

### 2.8 Add Rate Limiting to API Gateway (GAP-SEC-07)
- **Severity:** Medium | **Effort:** Medium
- **Description:** Add request rate limiting at the gateway level to prevent abuse.
- **Files:** `internet-banking-api-gateway` config and dependencies

**Devin Prompt:**
```
Add rate limiting to the API Gateway:

1. Add Redis dependency for rate limiter state:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Configure RequestRateLimiter filter in the gateway routes:
   - Default: 10 requests/second per user (by JWT subject)
   - Registration endpoint: 2 requests/minute per IP
   - Fund transfer: 5 requests/second per user

3. Add a Redis service to docker-compose.yml.

4. Configure a fallback for when Redis is unavailable (allow requests
   through rather than blocking all traffic).

5. Return HTTP 429 Too Many Requests with a Retry-After header.
```

---

## Phase 3: Polish

### 3.1 Add Integration Tests with Testcontainers (GAP-TST-02)
- **Severity:** High | **Effort:** Large
- **Description:** Add integration tests that verify real database interactions and REST endpoints.
- **Files:** New test classes across all services

**Devin Prompt:**
```
Add integration tests using Testcontainers for MySQL:

1. Add Testcontainers dependencies to each service's build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service, create:
   - AccountControllerIntegrationTest (@WebMvcTest + @DataJpaTest)
   - TransactionControllerIntegrationTest
   - Verify Flyway migrations run correctly against real MySQL

3. For user-service, fund-transfer-service, utility-payment-service:
   - Repository integration tests with @DataJpaTest + Testcontainers
   - Controller integration tests with @WebMvcTest

4. Use @TestConfiguration to provide test-specific beans where needed
   (mock Feign clients, disable Eureka/Config).

5. Target: verify CRUD operations and error handling with real database.
```

---

### 3.2 Add Contract Tests (GAP-TST-03)
- **Severity:** Medium | **Effort:** Large
- **Description:** Implement consumer-driven contract tests between services.
- **Files:** New contract test classes and contract definitions

**Devin Prompt:**
```
Implement Spring Cloud Contract tests between services:

1. In core-banking-service (provider), add:
   - spring-cloud-starter-contract-verifier dependency
   - Contract DSL files in src/test/resources/contracts/ for each API:
     * GET /api/v1/account/bank-account/{account_number}
     * GET /api/v1/user/{identification}
     * POST /api/v1/transaction/fund-transfer
     * POST /api/v1/transaction/util-payment
   - Base test class for contract verification

2. In consumer services, add:
   - spring-cloud-starter-contract-stub-runner dependency
   - Tests that use @AutoConfigureStubRunner to verify Feign clients
     work correctly against the generated stubs

3. Configure the contract verification to run as part of the build.

4. Document the contract testing workflow in a TESTING.md file.
```

---

### 3.3 Return Pagination Metadata (GAP-API-03)
- **Severity:** Medium | **Effort:** Small
- **Description:** Return `Page<T>` wrapper instead of `List<T>` for paginated endpoints.
- **Files:** All controllers with paginated endpoints

**Devin Prompt:**
```
Update all paginated endpoints to return Page<T> instead of List<T>:

1. In core-banking-service UserController.readUsers():
   - Change return type to ResponseEntity<Page<User>>
   - Update UserService.readUsers() to return Page<User>

2. In fund-transfer-service FundTransferController.readFundTransfers():
   - Change return type to ResponseEntity<Page<FundTransfer>>
   - Update FundTransferService.readAllTransfers()

3. In utility-payment-service UtilityPaymentController.readPayments():
   - Change return type to ResponseEntity<Page<UtilityPayment>>
   - Update UtilityPaymentService.readPayments()

4. In user-service UserController.readUsers():
   - Change return type to ResponseEntity<Page<User>>
   - Update UserService.readUsers()

This gives clients totalElements, totalPages, number, size, etc.
Update any existing tests to reflect the new return types.
```

---

### 3.4 Standardize Package Structure (GAP-ORG-02)
- **Severity:** Low | **Effort:** Small
- **Description:** Align package conventions across all services.
- **Files:** Various packages across all services

**Devin Prompt:**
```
Standardize the package structure across all services to follow this
convention:

com.javatodev.finance/
  configuration/       (Spring config, beans, security)
    filter/            (servlet filters)
    feign/             (Feign config, error decoders)
    keycloak/          (Keycloak-specific config)
    audit/             (JPA auditing config)
  controller/          (REST controllers)
  service/             (business logic)
    rest/              (Feign clients)
  model/
    entity/            (JPA entities)
    dto/               (request/response DTOs)
      request/
      response/
    mapper/            (entity-DTO mappers)
    enums/             (enumerations)
  repository/          (Spring Data repositories)
  exception/           (exceptions, handlers, error codes)

Move classes that don't follow this pattern. Update all import statements.
Verify compilation and tests pass after the refactoring.
```

---

### 3.5 Add Custom Health Indicators (GAP-OBS-02)
- **Severity:** Low | **Effort:** Small
- **Description:** Add health indicators for critical dependencies.
- **Files:** New health indicator classes in each service

**Devin Prompt:**
```
Add custom health indicators to each service:

1. core-banking-service:
   - DatabaseHealthIndicator (verify MySQL connectivity)

2. internet-banking-user-service:
   - KeycloakHealthIndicator (verify Keycloak connectivity via admin API)
   - CoreBankingHealthIndicator (verify core-banking-service reachability)

3. internet-banking-fund-transfer-service:
   - CoreBankingHealthIndicator

4. internet-banking-utility-payment-service:
   - CoreBankingHealthIndicator

5. internet-banking-api-gateway:
   - Aggregate health from downstream services via their /actuator/health

Implement each as a @Component extending AbstractHealthIndicator.
Configure actuator to expose health details:
  management.endpoint.health.show-details: always
```

---

### 3.6 Add Business Metrics (GAP-OBS-03)
- **Severity:** Medium | **Effort:** Medium
- **Description:** Add custom Micrometer metrics for key business operations.
- **Files:** Service classes across business services

**Devin Prompt:**
```
Add custom Micrometer metrics to track key business operations:

1. core-banking-service TransactionService:
   - Counter: banking.fund_transfer.total (tags: status=success|failed)
   - Counter: banking.utility_payment.total (tags: status=success|failed)
   - Timer: banking.fund_transfer.duration
   - Timer: banking.utility_payment.duration

2. internet-banking-user-service UserService:
   - Counter: banking.user.registration.total (tags: status=success|failed)
   - Counter: banking.user.approval.total

3. internet-banking-fund-transfer-service:
   - Counter: banking.transfer.requests.total (tags: status)

4. internet-banking-utility-payment-service:
   - Counter: banking.payment.requests.total (tags: status)

5. Expose Prometheus endpoint:
   management.endpoints.web.exposure.include: health,info,prometheus
   Add micrometer-registry-prometheus dependency to each service.

6. Inject MeterRegistry into services and use it to record metrics.
```

---

### 3.7 Add Retry Policies and Fallbacks (GAP-RES-02, GAP-RES-04)
- **Severity:** Medium | **Effort:** Medium
- **Description:** Configure retry policies for Feign clients with fallback behavior.
- **Files:** Application configs, Feign client configurations

**Devin Prompt:**
```
Add retry policies and fallback behavior for Feign clients:

1. Configure Feign retry in application.yml for each service:
   spring.cloud.openfeign.client.config.default:
     retryer: feign.Retryer.Default
     # Only retry on GET (safe/idempotent) operations
     # POST operations (fund transfer, payment) should NOT be retried

2. Create a custom Retryer that only retries on connection errors
   (not on HTTP 4xx/5xx responses).

3. For fallbacks, implement FallbackFactory classes that:
   - Log the cause of the failure
   - Return a meaningful error object
   - Do NOT silently swallow errors for financial operations

4. Configure Resilience4j retry (if circuit breakers from Phase 2
   are already in place):
   resilience4j.retry.instances.coreBanking:
     maxAttempts: 3
     waitDuration: 1s
     retryExceptions: java.io.IOException
```

---

### 3.8 Add Filtering and Sorting to List Endpoints (GAP-API-04)
- **Severity:** Low | **Effort:** Medium
- **Description:** Add query parameters for filtering list endpoints.
- **Files:** Controllers, services, and repositories across services

**Devin Prompt:**
```
Add filtering and sorting capabilities to list endpoints:

1. Fund Transfer list (GET /api/v1/transfer):
   - Filter by: fromAccount, toAccount, status, dateRange (fromDate, toDate)
   - Default sort: createdDate DESC

2. Utility Payment list (GET /api/v1/utility-payment):
   - Filter by: account, providerId, status, dateRange
   - Default sort: createdDate DESC

3. User list (GET /api/v1/bank-users):
   - Filter by: status, identification
   - Default sort: createdDate DESC

4. Implementation approach:
   - Use Spring Data JPA Specifications for dynamic queries
   - Accept filter params as @RequestParam in controllers
   - Create Specification builder classes for each entity
   - Ensure filters are properly validated and sanitized
```

---

### 3.9 Configure Tracing Sampling (GAP-OBS-05)
- **Severity:** Low | **Effort:** Small
- **Description:** Explicitly set tracing sampling rates.
- **Files:** Application config files

**Devin Prompt:**
```
Add explicit tracing sampling configuration to all services.

In the remote configuration repository (or local application.yml
for each service), add:

management:
  tracing:
    sampling:
      probability: 1.0  # 100% for development

Document that this should be reduced to 0.1 (10%) or lower for
production deployments. Add the configuration as a profile-specific
setting so production can override it.
```

---

### 3.10 Fix Keycloak Singleton Thread Safety (GAP-SEC-05)
- **Severity:** Medium | **Effort:** Small
- **Description:** Replace the manual singleton with a Spring-managed bean.
- **Files:** `internet-banking-user-service/.../configuration/keycloak/KeycloakProperties.java`

**Devin Prompt:**
```
Fix the thread-unsafe Keycloak singleton in KeycloakProperties:

1. Remove the static keycloakInstance field and getInstance() method.
2. Create a @Bean method in a @Configuration class that builds the
   Keycloak instance using KeycloakBuilder.
3. Inject the Keycloak bean into KeycloakManager instead of calling
   getInstance().
4. This ensures Spring manages the singleton lifecycle with proper
   thread safety.

Alternative (minimal change): add synchronized keyword to getInstance()
and use the double-checked locking pattern with volatile.
```

---

## Implementation Priority Matrix

```
                    High Impact
                        |
    GAP-RES-06 (bug)    |    GAP-ERR-05 (compensation)
    GAP-SEC-03 (valid)  |    GAP-RES-05 (idempotency)
    GAP-ERR-01 (status) |    GAP-RES-01 (circuit breakers)
    GAP-SEC-01 (creds)  |    GAP-TST-01 (unit tests)
    GAP-SEC-06 (passwd) |    GAP-ORG-01 (shared lib)
    GAP-RES-03 (timeout)|
  ──────────────────────+────────────────────────────
    GAP-API-06 (openapi)|    GAP-TST-02 (integration)
    GAP-OBS-05 (sample) |    GAP-TST-03 (contracts)
    GAP-API-05 (path)   |    GAP-OBS-01 (logging)
    GAP-SEC-04 (csrf)   |    GAP-OBS-03 (metrics)
                        |    GAP-API-04 (filters)
                    Low Impact
       Small Effort          Large Effort
```

---

## Success Criteria

| Phase | Metric | Target |
|-------|--------|--------|
| Phase 1 | Critical/High gaps resolved | All 5 Critical + top 5 High fixed |
| Phase 1 | CI pipeline | All services build and pass tests |
| Phase 2 | Service resilience | Circuit breakers active, timeouts configured |
| Phase 2 | Test coverage | > 60% line coverage on service layer |
| Phase 3 | Observability | JSON logs, custom metrics, health checks |
| Phase 3 | API quality | Pagination metadata, filtering, proper OpenAPI spec |
