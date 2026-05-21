# Internet Banking Microservices — Remediation Roadmap

Gaps from the [Gap Analysis](GAP_ANALYSIS.md) are organized into three phases based on a severity-vs-effort prioritization matrix:

- **Phase 1 (Quick Wins):** Critical/High severity + Small/Medium effort — fix first
- **Phase 2 (Important):** Critical/High severity + Large effort, or Medium severity + Small effort — tackle next
- **Phase 3 (Polish):** Medium/Low severity + Medium/Large effort — address as capacity allows

---

## Phase 1 — Quick Wins (Weeks 1–2)

These items address the most severe gaps with the least effort. Each can be completed in a single Devin session.

### 1.1 Fix All Errors Returning HTTP 400 (GAP-EH-01, GAP-EH-02, GAP-EH-04)

**Impact:** Critical — clients can't distinguish error types; internal details leak to consumers.

Update `GlobalExceptionHandler` in all four services to return correct HTTP status codes and a consistent error response shape.

**Devin Prompt:**
```
In the internet-banking-microservices repo, update the GlobalExceptionHandler in all four services
(core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service,
internet-banking-utility-payment-service) to:
1. Return HTTP 404 for EntityNotFoundException
2. Return HTTP 422 for InsufficientFundsException
3. Return HTTP 409 for UserAlreadyRegisteredException
4. Return HTTP 500 for generic Exception (with a sanitized message, never expose stack traces)
5. Always return an ErrorResponse { code, message, timestamp } JSON body — never a plain string
6. Add a timestamp field to the ErrorResponse class
Update or add unit tests for each handler. Run all tests and ensure they pass.
```

### 1.2 Add Input Validation to All Request DTOs (GAP-SE-02)

**Impact:** Critical — null/negative amounts can corrupt financial data.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add Jakarta Bean Validation annotations to all request DTOs:
1. core-banking-service: FundTransferRequest (fromAccount @NotBlank, toAccount @NotBlank, amount @NotNull @Positive),
   UtilityPaymentRequest (providerId @NotNull, amount @NotNull @Positive, referenceNumber @NotBlank, account @NotBlank)
2. internet-banking-fund-transfer-service: FundTransferRequest (same as above)
3. internet-banking-user-service: User DTO for registration (email @NotBlank @Email, identification @NotBlank, password @NotBlank @Size(min=8))
4. internet-banking-utility-payment-service: UtilityPaymentRequest (same as core-banking)
Add @Valid annotation to all @RequestBody parameters in controllers.
Add spring-boot-starter-validation dependency to each service's build.gradle if not present.
Add unit tests for validation. Run all tests.
```

### 1.3 Fix Double-Deduction Balance Bug (GAP — Business Logic)

**Impact:** Critical — financial transactions deduct/credit 2× the intended amount.

**Devin Prompt:**
```
In core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java, fix three bugs:
1. internalFundTransfer() lines 90-91: availableBalance is set to (actualBalance - amount) but actualBalance
   was ALREADY decremented. Fix: set availableBalance = actualBalance (they should be equal after debit).
2. internalFundTransfer() lines 99-100: Same issue on credit side. Fix: set availableBalance = actualBalance.
3. utilPayment() lines 63-64: Same double-deduction pattern. Fix: set availableBalance = actualBalance after debit.
Update the existing TransactionServiceTest to add assertions that verify correct balance values after
fund transfer and utility payment. Run all core-banking-service tests.
```

### 1.4 Add Feign Error Decoders to Fund Transfer and Utility Payment (GAP-EH-03)

**Impact:** High — raw Feign exceptions leak to clients.

**Devin Prompt:**
```
In the internet-banking-microservices repo:
1. Copy the CustomFeignErrorDecoder pattern from internet-banking-user-service to:
   - internet-banking-fund-transfer-service (create configuration/feign/ package)
   - internet-banking-utility-payment-service (create configuration/feign/ package)
2. Update the CustomFeignClientConfiguration in both services to register the error decoder.
3. Update the @FeignClient annotations to reference the new configuration.
4. Ensure the error decoder maps core-banking error codes to appropriate local exceptions.
Run the build for all services.
```

### 1.5 Add Feign Timeout Configuration (GAP-RE-03)

**Impact:** High — infinite timeouts cause thread exhaustion.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add explicit Feign timeout configuration for all three
Feign-using services (fund-transfer, user-service, utility-payment):
1. Add to each service's application.yml (or externalized config):
   spring.cloud.openfeign.client.config.default.connect-timeout: 5000
   spring.cloud.openfeign.client.config.default.read-timeout: 10000
2. Add to each CustomFeignClientConfiguration a Request.Options bean with connect=5s, read=10s.
Run the build for all affected services.
```

### 1.6 Add Retry Policies for Feign Clients (GAP-RE-02)

**Impact:** High — single transient failure kills the operation.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add Spring Retry support for Feign clients in fund-transfer,
user-service, and utility-payment services:
1. Add spring-retry and spring-boot-starter-aop dependencies to each build.gradle.
2. Add @EnableRetry to each main application class.
3. Configure Feign retry via application.yml: max-attempts=3, backoff=1000ms, multiplier=2.
4. Ensure retries only happen on GET requests and idempotent operations (NOT on POST for fund transfers
   or payments until idempotency keys are implemented).
Run the build for all affected services.
```

### 1.7 Fix Wrong OpenAPI Dependency (GAP-AD-02)

**Impact:** Medium — Swagger UI likely broken on MVC services.

**Devin Prompt:**
```
In the internet-banking-microservices repo, in the build.gradle of core-banking-service,
internet-banking-fund-transfer-service, internet-banking-user-service, and
internet-banking-utility-payment-service:
Replace: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
Run the build for all four services.
```

### 1.8 Remove Hardcoded Credentials (GAP-SE-03)

**Impact:** High — secrets visible in version control.

**Devin Prompt:**
```
In the internet-banking-microservices repo:
1. Replace all hardcoded passwords in docker-compose/docker-compose.yml with environment variable
   references (e.g., ${MYSQL_ROOT_PASSWORD:-changeme}, ${KEYCLOAK_ADMIN_PASSWORD:-changeme}).
2. Create a docker-compose/.env.example file listing all required env vars with placeholder values.
3. Add docker-compose/.env to .gitignore.
4. Replace the hardcoded Keycloak client secret in internet-banking-user-service/src/test/resources/application.yml
   with a placeholder that is set via environment variable.
5. Remove the plaintext test credentials from README.md and reference the .env.example instead.
Ensure docker-compose still works with the .env.example renamed to .env.
```

### 1.9 Add Structured Logging (GAP-OB-01, GAP-OB-04)

**Impact:** Medium — essential for production log aggregation; also fixes sensitive data leakage.

**Devin Prompt:**
```
In the internet-banking-microservices repo:
1. Add Logback JSON encoder dependency (net.logstash.logback:logstash-logback-encoder:7.4) to all
   services that have spring-boot-starter-web.
2. Add a logback-spring.xml configuration to each service's src/main/resources/ that outputs JSON
   in production profile and plain text in dev profile.
3. Replace all log.info() calls that log .toString() of request DTOs with structured log fields
   (using MDC or key-value pairs) that exclude sensitive data (account numbers, amounts).
4. Add traceId and spanId to the log pattern for correlation with Zipkin.
Run the build for all services.
```

### 1.10 Add ResponseEntity Type Parameters (GAP-AD-01)

**Impact:** Medium — improves type safety and OpenAPI docs.

**Devin Prompt:**
```
In the internet-banking-microservices repo, update all controller methods to use typed ResponseEntity:
1. core-banking-service: AccountController, TransactionController, UserController
2. internet-banking-fund-transfer-service: FundTransferController
3. internet-banking-user-service: UserController (already partially typed)
4. internet-banking-utility-payment-service: UtilityPaymentController
For example: ResponseEntity<BankAccount> instead of ResponseEntity.
Run the build for all services to verify compilation.
```

---

## Phase 2 — Important (Weeks 3–6)

These items require more effort but are critical for production readiness.

### 2.1 Add Circuit Breakers (GAP-RE-01, GAP-RE-05)

**Impact:** Critical — prevents cascade failures across the entire system.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add Resilience4j circuit breakers for all Feign clients:
1. Add spring-cloud-starter-circuitbreaker-resilience4j to build.gradle of fund-transfer, user-service,
   and utility-payment services.
2. Configure circuit breakers in application.yml for each Feign client:
   - sliding-window-size: 10
   - failure-rate-threshold: 50
   - wait-duration-in-open-state: 30s
   - permitted-number-of-calls-in-half-open-state: 3
3. Add fallback implementations for each Feign client that return meaningful error responses.
4. Add fallbackFactory to each @FeignClient annotation.
5. Add unit tests that verify fallback behavior when core-banking is unavailable.
Run all tests.
```

### 2.2 Add Idempotency Keys (GAP-RE-04)

**Impact:** Critical — prevents duplicate financial transactions.

**Devin Prompt:**
```
In the internet-banking-microservices repo, implement idempotency for financial endpoints:
1. Add an idempotencyKey field (UUID, required) to FundTransferRequest and UtilityPaymentRequest DTOs
   in both the orchestrator services and core-banking-service.
2. Add a unique constraint on idempotencyKey in fund_transfer and utility_payment tables.
3. In FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(), check for existing
   record with the same idempotencyKey before processing. If found, return the existing result.
4. Document the idempotency key in the OpenAPI annotations.
5. Add unit tests for duplicate-request handling.
Run all tests.
```

### 2.3 Implement Compensation/Saga for Failed Transactions (GAP-EH-05)

**Impact:** Critical — stale PENDING/PROCESSING records are a financial risk.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add compensation logic for failed Feign calls:
1. In FundTransferService.fundTransfer(), wrap the bankingCoreFeignClient.fundTransfer() call in a
   try-catch. On failure, update the FundTransferEntity status to FAILED and log the error.
2. In UtilityPaymentService.utilPayment(), same pattern — catch Feign exceptions, update status to FAILED.
3. Add a FAILED value to TransactionStatus enum in both services.
4. Create a scheduled task (@Scheduled) in each service that periodically scans for PENDING/PROCESSING
   records older than 5 minutes and either retries or marks them FAILED.
5. Add unit tests for failure and retry scenarios.
Run all tests.
```

### 2.4 Secure Downstream Services (GAP-SE-01)

**Impact:** Critical — any network-adjacent service can call financial APIs without auth.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add authentication to all downstream services:
1. Add spring-boot-starter-security to core-banking, fund-transfer, user-service, and utility-payment.
2. Implement a ServiceAuthFilter that validates a shared internal API key or propagated JWT on
   inter-service calls. Use the existing X-Auth-Id header plus a new X-Internal-Auth-Token header.
3. Configure the API Gateway to forward the JWT token (or a derived internal token) to downstream services.
4. Add security configuration to each service that permits actuator endpoints but requires auth for
   all /api/** endpoints.
5. Update Feign client configurations to include the auth header in outgoing requests.
Run the full build and test suite.
```

### 2.5 Create Shared Library (GAP-CO-01, GAP-CO-02)

**Impact:** High — eliminates code duplication across 4 services.

**Devin Prompt:**
```
In the internet-banking-microservices repo, create a shared library module:
1. Create a new directory banking-common/ with its own build.gradle (as a Java library, not a Spring Boot app).
2. Move these duplicated classes into banking-common:
   - BaseMapper, AuditAware, GlobalExceptionHandler, AppAuthUserFilter, ApiRequestContext,
     ApiRequestContextHolder, ErrorResponse, SimpleBankingGlobalException, GlobalErrorCode
3. Add a root settings.gradle that includes banking-common as a composite build.
4. Update each service's build.gradle to depend on banking-common.
5. Remove the duplicated classes from each service.
6. Ensure all services build successfully.
Run the full build for all services.
```

### 2.6 Add Unit Tests for All Services (GAP-TE-01)

**Impact:** High — essential for safe refactoring and regression prevention.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add comprehensive unit tests:
1. internet-banking-fund-transfer-service: Test FundTransferService (success, Feign failure, validation).
2. internet-banking-user-service: Test UserService (create, update, read, Keycloak failure, duplicate email).
3. internet-banking-utility-payment-service: Test UtilityPaymentService (success, Feign failure, validation).
4. Use Mockito to mock Feign clients, repositories, and Keycloak service.
5. Each service should have at least 80% line coverage on service layer classes.
6. Update test application.yml configs as needed (H2 in-memory DB, Eureka disabled).
Run all tests across all services and verify they pass.
```

### 2.7 Add Pagination Metadata to List Responses (GAP-AD-03)

**Impact:** Medium — required for any UI consuming these APIs.

**Devin Prompt:**
```
In the internet-banking-microservices repo, update all list endpoints to return pagination metadata:
1. Create a generic PageResponse<T> class in the shared library with fields:
   content (List<T>), pageNumber, pageSize, totalElements, totalPages, last.
2. Update FundTransferService.readAllTransfers(), UtilityPaymentService.readPayments(),
   UserService.readUsers() (both core-banking and user-service) to return PageResponse.
3. Update the corresponding controller methods to return ResponseEntity<PageResponse<T>>.
4. Add unit tests for pagination response structure.
Run all tests.
```

### 2.8 Add Custom Health Checks (GAP-OB-02)

**Impact:** Medium — needed for production monitoring and load balancer integration.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add custom health indicators:
1. core-banking-service: Add a DatabaseHealthIndicator that checks MySQL connectivity.
2. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak server.
3. fund-transfer and utility-payment services: Add a CoreBankingHealthIndicator that calls
   the core-banking actuator/health endpoint via Feign.
4. Configure actuator to expose health details: management.endpoint.health.show-details=always.
5. Add the health check endpoints to the API Gateway's permit-all list.
Run all builds.
```

### 2.9 Add Rate Limiting at the Gateway (GAP-SE-07)

**Impact:** Medium — protects financial endpoints from abuse.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add rate limiting to the API Gateway:
1. Add spring-boot-starter-data-redis-reactive dependency to the gateway's build.gradle.
2. Configure Spring Cloud Gateway's RequestRateLimiter filter with Redis backend:
   - Default: 10 requests/second per user
   - Fund transfer and payment endpoints: 3 requests/second per user
3. Add a Redis service to docker-compose.yml.
4. Configure a KeyResolver that extracts the user ID from the JWT token.
If Redis is not desired, alternatively use an in-memory rate limiter with Bucket4j.
Run the gateway build.
```

---

## Phase 3 — Polish (Weeks 7+)

These items improve developer experience and operational maturity.

### 3.1 Add Integration Tests (GAP-TE-02)

**Impact:** High (but large effort) — validates real inter-service behavior.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add integration tests using Testcontainers:
1. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter to core-banking-service test deps.
2. Create integration tests that run against a real MySQL container (Flyway enabled).
3. For fund-transfer and utility-payment services, add WireMock to simulate core-banking responses.
4. For user-service, add Testcontainers with Keycloak container.
5. Add a Gradle task 'integrationTest' separate from 'test'.
Run both unit and integration tests.
```

### 3.2 Add Contract Tests (GAP-TE-03)

**Impact:** Medium (large effort) — prevents breaking changes between services.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add Spring Cloud Contract tests:
1. In core-banking-service (provider), define contracts for all Feign-consumed endpoints:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}
2. Generate contract stubs from the provider.
3. In fund-transfer, user-service, and utility-payment (consumers), add stub-based tests that verify
   Feign clients work against the published stubs.
4. Add spring-cloud-starter-contract-verifier and spring-cloud-starter-contract-stub-runner.
Run all contract tests.
```

### 3.3 Add CI/CD Pipeline (No existing CI)

**Impact:** High — automates build, test, and deployment.

**Devin Prompt:**
```
In the internet-banking-microservices repo, create a GitHub Actions CI pipeline:
1. Create .github/workflows/ci.yml that:
   - Triggers on push to main and pull requests
   - Sets up JDK 21
   - Builds banking-common first
   - Builds all 6 services in parallel (./gradlew build)
   - Runs all unit tests
   - Reports test results as GitHub check annotations
   - Builds Docker images (but does not push)
2. Add a build status badge to README.md.
Use only open-source tools and GitHub-hosted runners.
```

### 3.4 Add Custom Business Metrics (GAP-OB-03)

**Impact:** Low-Medium — valuable for business observability.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add Micrometer custom metrics:
1. In core-banking-service: Add counters for fund_transfer_total, utility_payment_total,
   and gauges for active_accounts, total_balance.
2. In fund-transfer-service: Add counter for transfers_initiated, timer for transfer_duration,
   counter for transfers_failed.
3. In utility-payment-service: Similar counters and timers.
4. In user-service: Add counters for user_registrations, user_approvals.
5. Expose metrics via /actuator/prometheus endpoint.
Run all builds.
```

### 3.5 Scope Database Privileges (GAP-SE-06)

**Impact:** Medium — principle of least privilege.

**Devin Prompt:**
```
In the internet-banking-microservices repo, update docker-compose/mysql/privileges.sql to:
1. Create separate MySQL users for each service:
   - core_banking_user with SELECT, INSERT, UPDATE, DELETE on banking_core_service.*
   - fund_transfer_user with same on banking_core_fund_transfer_service.*
   - user_service_user with same on banking_core_user_service.*
   - utility_payment_user with same on banking_core_utility_payment_service.*
2. Remove the broad-privilege javatodev_development user.
3. Update each service's externalized configuration to use its dedicated user.
4. Test that the full Docker Compose stack still starts and works.
```

### 3.6 Fix Keycloak Singleton Thread Safety (GAP-SE-05)

**Impact:** Medium — race condition during startup.

**Devin Prompt:**
```
In internet-banking-user-service, refactor KeycloakProperties to be thread-safe:
1. Remove the static Keycloak singleton from KeycloakProperties.
2. Create the Keycloak instance as a @Bean in a @Configuration class, making it a Spring-managed singleton.
3. Inject it into KeycloakManager via constructor injection.
4. Add a unit test that verifies the Keycloak bean is created correctly.
Run user-service tests.
```

### 3.7 Fix TransactionEntity Cascade (GAP-RE-07)

**Impact:** Medium — prevents unexpected side effects.

**Devin Prompt:**
```
In core-banking-service, update TransactionEntity:
1. Change @OneToOne(cascade = CascadeType.ALL) to @ManyToOne (since multiple transactions can
   reference the same account) with no cascade.
2. Remove the bidirectional relationship if not needed.
3. Update the Flyway migration or add a new one if the column constraint changes.
4. Update TransactionServiceTest to verify the cascade behavior is correct.
Run core-banking-service tests.
```

### 3.8 Add Database Connection Pool Configuration (GAP-RE-06)

**Impact:** Medium — prevents connection exhaustion under load.

**Devin Prompt:**
```
In the internet-banking-microservices repo, add HikariCP configuration to all four database-using
services via their externalized configuration:
spring.datasource.hikari:
  maximum-pool-size: 20
  minimum-idle: 5
  connection-timeout: 30000
  idle-timeout: 600000
  max-lifetime: 1800000
  pool-name: <ServiceName>HikariPool
Add these to the externalized config repo or to each service's application.yml.
Run all builds.
```

---

## Priority Matrix

```
                    Small Effort          Medium Effort         Large Effort
                ┌─────────────────────┬─────────────────────┬─────────────────────┐
  Critical      │ 1.1 Error codes     │ 2.1 Circuit breakers│ 2.3 Compensation/   │
                │ 1.2 Input validation│ 2.2 Idempotency     │     Saga            │
                │ 1.3 Balance bug fix │ 2.4 Secure downstream                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
  High          │ 1.4 Feign decoders  │ 2.5 Shared library  │ 2.6 Unit tests      │
                │ 1.5 Timeouts        │                     │ 3.1 Integration     │
                │ 1.6 Retry policies  │                     │     tests           │
                │ 1.8 Credentials     │                     │                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
  Medium        │ 1.7 OpenAPI dep     │ 2.7 Pagination      │ 3.2 Contract tests  │
                │ 1.9 Structured logs │ 2.8 Health checks   │                     │
                │ 1.10 Typed Response │ 2.9 Rate limiting   │                     │
                │ 3.5 DB privileges   │ 3.4 Custom metrics  │                     │
                │ 3.6 Keycloak thread │ 3.8 Connection pool │                     │
                │ 3.7 Cascade fix     │                     │                     │
                ├─────────────────────┼─────────────────────┼─────────────────────┤
  Low           │ Package structure   │ API versioning      │                     │
                │ Mappers as beans    │ HATEOAS             │                     │
                │ Endpoint naming     │                     │                     │
                └─────────────────────┴─────────────────────┴─────────────────────┘
```
