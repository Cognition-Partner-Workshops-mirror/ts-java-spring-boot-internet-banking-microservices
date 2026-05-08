# Internet Banking Microservices — Remediation Roadmap

> **Generated:** 2026-05-08 | **Based on:** [Gap Analysis](./GAP_ANALYSIS.md) (37 gaps identified)

---

## Phasing Strategy

| Phase | Focus | Selection Criteria | Timeline |
|---|---|---|---|
| **Phase 1 — Quick Wins** | Fix critical bugs and security holes | Critical/High severity + Small effort | 1–2 weeks |
| **Phase 2 — Important** | Structural improvements and resilience | Critical/High severity + Medium effort | 3–6 weeks |
| **Phase 3 — Polish** | Quality-of-life improvements and best practices | Medium/Low severity, larger efforts | 6–12 weeks |

---

## Phase 1 — Quick Wins

> High-impact fixes that can be completed in less than a day each.

### 1.1 Fix double-deduction balance bug (GAP-36)
**Priority: P0 | Severity: Critical | Effort: Small**

The `availableBalance` is computed from already-debited `actualBalance`, causing a double subtraction in both `internalFundTransfer()` and `utilPayment()` in `TransactionService.java`.

**Devin Prompt:**
```
Fix the double-deduction bug in core-banking-service TransactionService.java.

In `internalFundTransfer()` (lines 90-91), `availableBalance` is set from the already-debited
`actualBalance`, causing double subtraction. The fix: set availableBalance BEFORE modifying
actualBalance, or compute both from the original balance. Same bug in `utilPayment()` (lines 63-64).

For both methods, change the pattern from:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance().subtract(amount));
To:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getAvailableBalance().subtract(amount));

Update the existing unit tests in TransactionServiceTest to verify correct balance calculations.
Open a PR with the fix.
```

### 1.2 Fix all errors returning HTTP 400 (GAP-05)
**Priority: P0 | Severity: Critical | Effort: Small**

**Devin Prompt:**
```
Update GlobalExceptionHandler in all 4 MVC services (core-banking-service,
internet-banking-fund-transfer-service, internet-banking-user-service,
internet-banking-utility-payment-service) to return correct HTTP status codes:

- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException -> 400 Bad Request
- InvalidBankingUserException -> 404 Not Found
- SimpleBankingGlobalException (generic) -> 400 Bad Request
- Exception (catch-all) -> 500 Internal Server Error

Also fix the catch-all handler to return a structured ErrorResponse instead of a raw string
with internal exception details. Use ErrorResponse { code: "INTERNAL_ERROR", message: "An
unexpected error occurred" } and log the full exception server-side.

Open a PR with the changes.
```

### 1.3 Add input validation to all request DTOs (GAP-14)
**Priority: P0 | Severity: Critical | Effort: Small**

**Devin Prompt:**
```
Add Bean Validation (jakarta.validation) to all request DTOs across all services.
Add spring-boot-starter-validation to each service's build.gradle dependencies.

Core Banking Service:
- FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

Fund Transfer Service:
- FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

User Service:
- User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
- UserUpdateRequest: @NotNull status

Utility Payment Service:
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

Add @Valid annotation to all @RequestBody parameters in controllers. Add a
MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns HTTP 422
with field-level error details. Open a PR.
```

### 1.4 Remove hardcoded credentials from source control (GAP-16)
**Priority: P0 | Severity: Critical | Effort: Small**

**Devin Prompt:**
```
Remove all hardcoded passwords from docker-compose/docker-compose.yml,
docker-compose/docker-compose-support-apps.yml, and docker-compose/mysql/privileges.sql.

Replace them with environment variable references:
- MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
- MySQL app user password in privileges.sql: use an entrypoint script that reads from env var
- KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
- KC_DB_PASSWORD / POSTGRES_PASSWORD: ${KC_DB_PASSWORD}

Create a docker-compose/.env.example file with placeholder values and documentation.
Add docker-compose/.env to .gitignore. Remove test credentials from README.md.
Open a PR.
```

### 1.5 Fix catch-all exception handler information leakage (GAP-06)
**Priority: P1 | Severity: High | Effort: Small**

This is addressed as part of item 1.2 above.

### 1.6 Add Feign error decoder to fund-transfer and utility-payment services (GAP-07)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service. The user-service already has one — use it as a
reference (internet-banking-user-service/src/main/java/.../configuration/feign/CustomFeignErrorDecoder.java).

The decoder should:
1. Read the response body from the upstream error.
2. Parse the ErrorResponse JSON (code + message).
3. Map HTTP 404 to EntityNotFoundException.
4. Map HTTP 400/422 to SimpleBankingGlobalException with the upstream error code.
5. Map other status codes to a generic SimpleBankingGlobalException.

Register the decoder in CustomFeignClientConfiguration for each service. Open a PR.
```

### 1.7 Fix wrong OpenAPI dependency (GAP-24)
**Priority: P1 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In all 4 MVC services (core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, internet-banking-utility-payment-service), change the Swagger
dependency from:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

The webflux variant is only for reactive/WebFlux services. These are all Spring MVC services.
Verify that the Swagger UI loads correctly after the change. Open a PR.
```

### 1.8 Fix Transaction entity JPA mapping (GAP-37)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
In core-banking-service, fix TransactionEntity.java:

Change the `account` field mapping from:
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "account_id", referencedColumnName = "id")
  private BankAccountEntity account;
To:
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", referencedColumnName = "id")
  private BankAccountEntity account;

The current @OneToOne with CascadeType.ALL is incorrect because many transactions reference
the same account, and cascade-delete would destroy the account when a transaction is deleted.
Open a PR.
```

### 1.9 Restrict database privileges (GAP-17)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
Update docker-compose/mysql/privileges.sql to use least-privilege access.

Replace the current global grant:
  GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO ...
With per-schema grants:
  GRANT INSERT, UPDATE, DELETE, SELECT ON banking_core_service.* TO 'javatodev_development'@'%';
  GRANT INSERT, UPDATE, DELETE, SELECT ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
  GRANT INSERT, UPDATE, DELETE, SELECT ON banking_core_user_service.* TO 'javatodev_development'@'%';
  GRANT INSERT, UPDATE, DELETE, SELECT ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';

Add a separate migration user for Flyway with DDL permissions or have Flyway run as root.
Open a PR.
```

### 1.10 Add OWASP dependency vulnerability scanning (GAP-20)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
Add the OWASP Dependency Check Gradle plugin to all 6 services.

In each build.gradle, add:
  plugins {
    id 'org.owasp.dependencycheck' version '10.0.3'
  }

Configure the plugin to fail the build on CVSS score >= 7:
  dependencyCheck {
    failBuildOnCVSS = 7.0f
    formats = ['HTML', 'JSON']
  }

Run `./gradlew dependencyCheckAnalyze` in each service to verify it works.
Open a PR with the plugin configuration and document any existing vulnerabilities found.
```

### 1.11 Add Feign timeout configuration (GAP-33)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
Add explicit Feign client timeout configuration to internet-banking-fund-transfer-service,
internet-banking-user-service, and internet-banking-utility-payment-service.

In each service's application.yml (or via the config server), add:
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

This prevents thread pool exhaustion when core-banking-service is slow. Open a PR.
```

### 1.12 Add retry policies to Feign clients (GAP-32)
**Priority: P1 | Severity: High | Effort: Small**

**Devin Prompt:**
```
Add Spring Retry support to the 3 Feign-consuming services (fund-transfer, user,
utility-payment).

In each build.gradle add:
  implementation 'org.springframework.retry:spring-retry'

In each application.yml add:
  spring:
    cloud:
      openfeign:
        client:
          config:
            default:
              retryer: feign.Retryer.Default

Configure retries for GET requests only (reads are idempotent). POST requests should NOT be
retried to avoid duplicate transactions. Use a custom Retryer bean if needed. Open a PR.
```

### 1.13 Add JaCoCo test coverage measurement (GAP-13)
**Priority: P2 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add JaCoCo code coverage plugin to all 6 services.

In each build.gradle, add:
  plugins {
    id 'jacoco'
  }

  jacocoTestReport {
    dependsOn test
    reports {
      xml.required = true
      html.required = true
    }
  }

  test {
    finalizedBy jacocoTestReport
  }

Run `./gradlew test jacocoTestReport` in core-banking-service (the only one with tests) and
verify the report generates. Open a PR.
```

### 1.14 Add typed ResponseEntity to all controllers (GAP-21)
**Priority: P2 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add proper generic type parameters to all ResponseEntity return types across all controllers
in all services. For example, change:
  public ResponseEntity getBankAccount(...)
to:
  public ResponseEntity<BankAccount> getBankAccount(...)

Update all controllers:
- core-banking-service: AccountController, TransactionController, UserController
- internet-banking-fund-transfer-service: FundTransferController
- internet-banking-utility-payment-service: UtilityPaymentController

(User service UserController already has proper types.) Open a PR.
```

### 1.15 Fix Keycloak singleton thread safety (GAP-19)
**Priority: P2 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Fix the thread-unsafe Keycloak singleton in internet-banking-user-service
KeycloakProperties.java.

Replace the manual lazy initialization with either:
Option A: `synchronized` keyword on getInstance()
Option B (preferred): Initialize in a @PostConstruct method or make it a Spring @Bean

Preferred approach — create a @Bean in a @Configuration class:
  @Bean
  public Keycloak keycloak(KeycloakProperties props) {
    return KeycloakBuilder.builder()
      .serverUrl(props.getServerUrl())
      .realm(props.getRealm())
      .grantType("client_credentials")
      .clientId(props.getClientId())
      .clientSecret(props.getClientSecret())
      .build();
  }

Open a PR.
```

---

## Phase 2 — Important

> Structural improvements that require touching multiple services or adding new capabilities.

### 2.1 Add compensation/rollback logic for Feign failures (GAP-08)
**Priority: P0 | Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Add error handling and compensation logic to FundTransferService.fundTransfer() and
UtilityPaymentService.utilPayment().

In each service:
1. Wrap the Feign call in a try-catch.
2. On FeignException, update the entity status to FAILED (add FAILED to TransactionStatus enum).
3. Log the error with full context (transaction ID, account numbers, amount).
4. Return an error response to the client.

Additionally, create a scheduled job (@Scheduled) that runs every 5 minutes to find entities
stuck in PENDING/PROCESSING status for more than 15 minutes and marks them as FAILED.

Open a PR.
```

### 2.2 Add circuit breakers to Feign clients (GAP-31)
**Priority: P0 | Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Add Resilience4j circuit breaker to all 3 Feign-consuming services.

In each service's build.gradle add:
  implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

In application.yml add circuit breaker configuration:
  resilience4j:
    circuitbreaker:
      instances:
        coreBankingService:
          registerHealthIndicator: true
          slidingWindowSize: 10
          failureRateThreshold: 50
          waitDurationInOpenState: 30s
          permittedNumberOfCallsInHalfOpenState: 3

Add @CircuitBreaker annotations to the Feign client methods or configure via
CircuitBreakerFactory. Add fallback methods that return meaningful error responses.
Open a PR.
```

### 2.3 Add idempotency keys to POST endpoints (GAP-26)
**Priority: P0 | Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Add idempotency key support to fund-transfer and utility-payment services.

Implementation:
1. Accept an optional `X-Idempotency-Key` header on POST endpoints.
2. Before processing, check if a transaction with that key already exists.
3. If it exists, return the cached response (200 OK with original result).
4. If not, process normally and store the idempotency key with the entity.

Add an `idempotencyKey` column to fund_transfer and utility_payment tables.
Add a unique index on the idempotency key column.
Add @RequestHeader("X-Idempotency-Key") Optional<String> parameter to POST controllers.

Open a PR.
```

### 2.4 Add authentication to downstream services (GAP-15)
**Priority: P0 | Severity: Critical | Effort: Medium**

**Devin Prompt:**
```
Add Spring Security with JWT validation to all downstream services (core-banking-service,
internet-banking-fund-transfer-service, internet-banking-user-service,
internet-banking-utility-payment-service).

In each service's build.gradle add:
  implementation 'org.springframework.boot:spring-boot-starter-security'
  implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

Add SecurityConfiguration class to each service that:
1. Requires JWT authentication on all endpoints except /actuator/health.
2. Validates the JWT using the Keycloak JWK Set URI.
3. Extracts the user principal from the JWT claims.
4. Removes reliance on the spoofable X-Auth-Id header.

Ensure Feign clients propagate the JWT token by adding a RequestInterceptor that forwards
the Authorization header. Open a PR.
```

### 2.5 Extract shared library module (GAP-01)
**Priority: P1 | Severity: High | Effort: Medium**

**Devin Prompt:**
```
Create a shared library module for the internet banking microservices.

1. Create a new directory: banking-common/
2. Add a build.gradle for a plain Java library (not a Spring Boot app).
3. Move these duplicated classes into the shared library:
   - BaseMapper<E, D>
   - AuditAware
   - GlobalExceptionHandler
   - ErrorResponse
   - SimpleBankingGlobalException
   - AppAuthUserFilter / ApiRequestContext / ApiRequestContextHolder
4. Create a root settings.gradle that includes all services and banking-common.
5. Update each service's build.gradle to depend on banking-common:
   implementation project(':banking-common')
6. Remove the duplicated classes from each service.
7. Verify each service builds successfully.

Open a PR.
```

### 2.6 Add pagination metadata to list endpoints (GAP-22)
**Priority: P2 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Update all paginated GET endpoints to return pagination metadata instead of raw lists.

Create a generic PageResponse<T> DTO in the shared library (or in each service):
  public class PageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
  }

Update these endpoints:
- core-banking-service: GET /api/v1/user
- fund-transfer-service: GET /api/v1/transfer
- user-service: GET /api/v1/bank-users
- utility-payment-service: GET /api/v1/utility-payment

Return PageResponse<T> instead of List<T>. Open a PR.
```

### 2.7 Add rate limiting to API Gateway (GAP-25)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in
RequestRateLimiter filter.

Option A (Redis-based — production):
  Add spring-boot-starter-data-redis-reactive dependency.
  Configure Redis-backed rate limiter in routes.

Option B (in-memory — simpler):
  Use Bucket4j or a custom GlobalFilter with a ConcurrentHashMap-based token bucket.

Configure limits:
- POST /fund-transfer/** : 10 requests/minute per user
- POST /payment/** : 10 requests/minute per user
- POST /user/**/register : 5 requests/minute per IP
- GET endpoints: 60 requests/minute per user

Open a PR.
```

### 2.8 Add structured logging (GAP-27)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add structured JSON logging to all 6 services.

1. Add logback-classic JSON encoder dependency or use logstash-logback-encoder:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with JSON output format.

3. Add MDC population for traceId, spanId, userId, and requestId in the AppAuthUserFilter.

4. Review all log.info() calls and remove any that log sensitive data (e.g., user passwords,
   full request objects in user-service).

5. Ensure the JSON log format includes: timestamp, level, logger, message, traceId, spanId,
   userId, service name.

Open a PR.
```

### 2.9 Add custom health checks (GAP-28)
**Priority: P2 | Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Add custom HealthIndicator beans to services that depend on external systems.

Core Banking Service:
- DatabaseHealthIndicator (verify MySQL connectivity)

User Service:
- KeycloakHealthIndicator (verify Keycloak server is reachable)
- CoreBankingHealthIndicator (verify core-banking-service is reachable via Eureka)

Fund Transfer Service:
- CoreBankingHealthIndicator (verify core-banking-service is reachable)

Utility Payment Service:
- CoreBankingHealthIndicator (verify core-banking-service is reachable)

Each indicator should return UP/DOWN with details (response time, error message).
Enable actuator health details: management.endpoint.health.show-details=always.
Open a PR.
```

### 2.10 Add fallback behavior for Feign clients (GAP-34)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add Feign fallback classes for all FeignClient interfaces.

For each @FeignClient, create a fallback implementation:
- BankingCoreFeignClient (fund-transfer) -> BankingCoreFeignClientFallback
- BankingCoreRestClient (user-service) -> BankingCoreRestClientFallback
- BankingCoreRestClient (utility-payment) -> BankingCoreRestClientFallback

Each fallback should:
1. Log a warning with the circuit breaker state.
2. Throw a meaningful exception (e.g., ServiceUnavailableException) with a user-friendly message.

Register fallbacks using @FeignClient(fallback = XxxFallback.class). This requires
Resilience4j circuit breaker (from item 2.2). Open a PR.
```

---

## Phase 3 — Polish

> Quality-of-life improvements that increase long-term maintainability.

### 3.1 Add unit tests to all services (GAP-10)
**Priority: P1 | Severity: High | Effort: Large**

**Devin Prompt:**
```
Add comprehensive unit tests to the 5 services that currently have no tests:
internet-banking-fund-transfer-service, internet-banking-user-service,
internet-banking-utility-payment-service, internet-banking-api-gateway,
and internet-banking-config-server.

For each service, test:
1. Service layer methods (mock repository and Feign clients)
2. Mapper classes
3. Edge cases (null inputs, empty results, exception scenarios)

Target: At least 80% line coverage for the service layer.
Use Mockito for mocking, JUnit 5 for assertions.
Run tests with JaCoCo and verify coverage. Open a PR.
```

### 3.2 Add integration tests (GAP-11)
**Priority: P1 | Severity: High | Effort: Large**

**Devin Prompt:**
```
Add @SpringBootTest integration tests for all 4 MVC services.

For each service, create integration tests that:
1. Use @SpringBootTest with WebEnvironment.RANDOM_PORT or MockMvc.
2. Test controller endpoints with proper request/response serialization.
3. Use H2 in-memory database for repository tests.
4. Use WireMock (@WireMockTest) to mock Feign client calls.
5. Verify Spring Security filter chain (once auth is added).

Create at least one integration test per controller endpoint. Open a PR.
```

### 3.3 Add contract tests (GAP-12)
**Priority: P2 | Severity: Medium | Effort: Large**

**Devin Prompt:**
```
Add Spring Cloud Contract tests between core-banking-service (provider) and its 3 consumers
(fund-transfer, user, utility-payment services).

1. Add spring-cloud-contract-verifier to core-banking-service.
2. Write contract DSL files for each endpoint consumed by downstream services.
3. Generate WireMock stubs from contracts.
4. Add spring-cloud-contract-stub-runner to each consumer service's test configuration.
5. Write consumer-side tests that verify Feign client compatibility with the stubs.

This ensures API changes in core-banking-service are caught at build time. Open a PR.
```

### 3.4 Create multi-module Gradle root project (GAP-02)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Create a Gradle multi-module root project for all services.

1. Create a root build.gradle with shared configuration:
   - Common Java version, dependency management, Spring Cloud BOM
   - Common test configuration
   - Shared plugin versions

2. Create a root settings.gradle that includes all services:
   include 'banking-common', 'core-banking-service', 'internet-banking-api-gateway', etc.

3. Move common dependency versions to a root ext {} or version catalog (libs.versions.toml).

4. Update each service's build.gradle to inherit from the root project.

5. Verify `./gradlew build` from root builds all services.

Open a PR.
```

### 3.5 Add custom business metrics (GAP-29)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add custom Micrometer metrics to track business KPIs.

Core Banking Service:
- Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT, status=SUCCESS|FAILED)
- Timer: banking.transactions.duration
- Gauge: banking.accounts.active.count

Fund Transfer Service:
- Counter: banking.fund_transfer.total (tags: status=PENDING|SUCCESS|FAILED)
- Timer: banking.fund_transfer.duration

User Service:
- Counter: banking.registrations.total (tags: status=SUCCESS|FAILED)
- Counter: banking.approvals.total

Utility Payment Service:
- Counter: banking.utility_payment.total (tags: status=PROCESSING|SUCCESS|FAILED)

Use @Counted and @Timed annotations or inject MeterRegistry directly. Open a PR.
```

### 3.6 Add bulkhead isolation (GAP-35)
**Priority: P2 | Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add Resilience4j bulkhead configuration to isolate Feign call thread pools.

In each Feign-consuming service, add bulkhead config in application.yml:
  resilience4j:
    bulkhead:
      instances:
        coreBankingService:
          maxConcurrentCalls: 25
          maxWaitDuration: 500ms
    thread-pool-bulkhead:
      instances:
        coreBankingService:
          maxThreadPoolSize: 10
          coreThreadPoolSize: 5
          queueCapacity: 20

This prevents a slow core-banking-service from consuming all threads in downstream services.
Open a PR.
```

### 3.7 Standardize package structure (GAP-03)
**Priority: P3 | Severity: Low | Effort: Small**

**Devin Prompt:**
```
Standardize the package structure across all services to follow a consistent pattern:

  com.javatodev.finance
  ├── configuration/
  │   ├── audit/
  │   ├── feign/
  │   ├── filter/
  │   └── security/
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
      └── rest/

Move classes that are in inconsistent locations. Ensure all services follow the same pattern.
Open a PR.
```

### 3.8 Convert mappers to Spring-managed components (GAP-04)
**Priority: P3 | Severity: Low | Effort: Small**

**Devin Prompt:**
```
Convert all mapper classes from manual instantiation to Spring-managed beans.

In each mapper class (BankAccountMapper, UserMapper, FundTransferMapper,
UtilityPaymentMapper, UtilityAccountMapper), add @Component annotation.

In each service class, replace:
  private XxxMapper mapper = new XxxMapper();
with:
  private final XxxMapper mapper;  // injected via @RequiredArgsConstructor

This enables proper mocking in unit tests and follows Spring DI conventions. Open a PR.
```

### 3.9 Document API versioning strategy (GAP-23)
**Priority: P3 | Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Create docs/API_VERSIONING.md documenting the API versioning strategy for the project.

Include:
1. Current approach: URI path versioning (/api/v1/)
2. When to create v2: breaking changes to request/response shapes
3. Deprecation policy: v1 remains available for 6 months after v2 release
4. Header-based versioning option for future consideration
5. OpenAPI spec generation per version

Open a PR.
```

### 3.10 Add CSRF disable justification comment (GAP-18)
**Priority: P3 | Severity: Low | Effort: Small**

**Devin Prompt:**
```
Add a comment to SecurityConfiguration.java in the API Gateway explaining why CSRF is
disabled:

  // CSRF protection is disabled because this is a stateless REST API that uses JWT bearer
  // tokens for authentication. CSRF attacks exploit session cookies, which are not used here.
  httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);

Open a PR.
```

---

## Summary

| Phase | Items | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| Phase 1 — Quick Wins | 15 | 4 | 8 | 3 | 0 |
| Phase 2 — Important | 10 | 4 | 1 | 5 | 0 |
| Phase 3 — Polish | 10 | 0 | 2 | 5 | 3 |
| **Total** | **35** | **8** | **11** | **13** | **3** |

> **Note:** GAP-05 and GAP-06 are addressed together in item 1.2. GAP-30 (Zipkin config) is deferred as it requires access to the external config repository.

### Recommended Execution Order

1. **Immediately:** Items 1.1 (balance bug), 1.2 (error codes), 1.3 (validation), 1.4 (credentials)
2. **This sprint:** Items 1.6–1.12 (remaining Phase 1)
3. **Next sprint:** Items 2.1–2.4 (critical Phase 2: compensation, circuit breakers, idempotency, auth)
4. **Following sprints:** Items 2.5–2.10, then Phase 3
