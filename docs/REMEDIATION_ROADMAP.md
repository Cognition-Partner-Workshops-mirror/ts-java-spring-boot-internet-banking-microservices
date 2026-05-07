# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on risk, effort, and business impact.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact, low-effort items that immediately improve security and reliability.

### 1.1 Fix Error Handling — Proper HTTP Status Codes

**Gap**: All errors return HTTP 400; generic handler exposes stack traces  
**Severity**: High | **Effort**: Small

**What to do**:
- Map `EntityNotFoundException` → 404
- Map `InsufficientFundsException` → 422
- Map `UserAlreadyRegisteredException` → 409
- Map generic `Exception` → 500 with structured `ErrorResponse` (never raw string)
- Apply consistently across all 3 `GlobalExceptionHandler` classes

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, refactor all GlobalExceptionHandler classes 
(core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, 
internet-banking-utility-payment-service) to:
1. Return proper HTTP status codes: EntityNotFoundException → 404, InsufficientFundsException → 422, 
   UserAlreadyRegisteredException → 409, generic Exception → 500
2. Always return a structured ErrorResponse object (never a raw string)
3. Never expose internal exception details to clients
4. Make all GlobalExceptionHandler implementations consistent
Open a PR with these changes.
```

---

### 1.2 Remove Hardcoded Credentials

**Gap**: Passwords committed to source control  
**Severity**: Critical | **Effort**: Small

**What to do**:
- Replace hardcoded passwords in `docker-compose.yml` and `docker-compose-support-apps.yml` with environment variable references (`${MYSQL_ROOT_PASSWORD}`)
- Create a `.env.example` file with placeholder values
- Add `.env` to `.gitignore`
- Remove test credentials from `README.md`
- Update `docker-compose/mysql/Dockerfile` to use build args or env vars

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, remove all hardcoded credentials:
1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml, replace hardcoded passwords 
   with ${ENV_VAR} references (MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD, POSTGRES_PASSWORD)
2. Create a .env.example file with placeholder values for all required env vars
3. Add .env to .gitignore
4. In docker-compose/mysql/Dockerfile, use ARG/ENV pattern instead of hardcoded password
5. Remove the test credentials line from README.md
Open a PR with these changes.
```

---

### 1.3 Add Input Validation

**Gap**: No Bean Validation on request DTOs  
**Severity**: High | **Effort**: Small

**What to do**:
- Add `spring-boot-starter-validation` dependency to services that accept input
- Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size` to request DTOs
- Add `@Valid` to all `@RequestBody` parameters
- Add `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` returning 422

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Bean Validation:
1. Add spring-boot-starter-validation to build.gradle for core-banking-service, 
   internet-banking-fund-transfer-service, internet-banking-user-service, 
   internet-banking-utility-payment-service
2. Add validation annotations to all request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on account/referenceNumber
   - User (registration): @NotBlank @Email on email, @NotBlank on password/identification
3. Add @Valid annotation to all @RequestBody parameters in controllers
4. Add MethodArgumentNotValidException handler in all GlobalExceptionHandlers returning 422 with field-level errors
Open a PR with these changes.
```

---

### 1.4 Configure Timeouts on Feign Clients

**Gap**: No explicit timeout configuration  
**Severity**: High | **Effort**: Small

**What to do**:
- Add Feign timeout configuration to each service's `application.yml` (or config server):
  - `connectTimeout: 2000` (2 seconds)
  - `readTimeout: 5000` (5 seconds)
- Add connection pool configuration for OkHttp/Apache HTTP client

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure Feign client timeouts:
1. In internet-banking-fund-transfer-service/src/main/resources/application.yml, add:
   spring.cloud.openfeign.client.config.default.connectTimeout: 2000
   spring.cloud.openfeign.client.config.default.readTimeout: 5000
2. Apply the same configuration to internet-banking-user-service and 
   internet-banking-utility-payment-service
3. Add a comment explaining the timeout values chosen
Open a PR with these changes.
```

---

### 1.5 Add Retry Policies for Feign Clients

**Gap**: No retry configuration  
**Severity**: High | **Effort**: Small

**What to do**:
- Add `spring-retry` and `resilience4j-spring-boot3` dependencies
- Configure retry for GET operations only (safe to retry)
- Exclude POST operations from retry (fund transfers must not be retried without idempotency)

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add retry policies:
1. Add spring-retry dependency to internet-banking-fund-transfer-service, 
   internet-banking-user-service, internet-banking-utility-payment-service
2. Configure Feign retry for GET requests only (max 3 attempts, 1s backoff):
   spring.cloud.openfeign.client.config.default.retryer with exponential backoff
3. Ensure POST endpoints (fund transfers, utility payments) are NOT retried 
   (since they are not idempotent yet)
Open a PR with these changes.
```

---

### 1.6 Fix Keycloak Singleton Thread Safety

**Gap**: Race condition in `KeycloakProperties.getInstance()`  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Convert `KeycloakProperties.getInstance()` to a `@Bean` method in a `@Configuration` class
- Or use `synchronized` keyword on the method

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the Keycloak singleton thread safety issue:
1. In internet-banking-user-service KeycloakProperties.java, convert the Keycloak instance 
   creation to a Spring @Bean so that Spring manages the singleton lifecycle
2. Create a KeycloakConfig @Configuration class that produces a Keycloak @Bean
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance()
4. Remove the static keycloakInstance field
Open a PR with these changes.
```

---

### 1.7 Fix Pagination Responses

**Gap**: Paginated endpoints return raw lists without metadata  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Return `Page<T>` or a custom `PageResponse<T>` wrapper from paginated endpoints
- Include `totalElements`, `totalPages`, `currentPage`, `size` in the response

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix pagination responses:
1. Create a shared PageResponse<T> DTO with fields: content, totalElements, totalPages, 
   currentPage, size
2. Update all paginated endpoints in all services to return PageResponse<T> instead of List<T>
3. In the service layer, use Page.map() to convert entities and wrap in PageResponse
Open a PR with these changes.
```

---

## Phase 2: Important (3-6 Weeks)

Structural improvements that significantly increase reliability and maintainability.

### 2.1 Add Circuit Breakers

**Gap**: No circuit breaker pattern; cascading failures possible  
**Severity**: Critical | **Effort**: Medium

**What to do**:
- Add `resilience4j-spring-boot3` and `resilience4j-circuitbreaker` dependencies
- Configure circuit breakers on all Feign clients
- Define fallback methods that return meaningful error responses
- Configure thresholds: failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breakers:
1. Add resilience4j-spring-boot3 and resilience4j-circuitbreaker dependencies to 
   internet-banking-fund-transfer-service, internet-banking-user-service, 
   internet-banking-utility-payment-service
2. Configure circuit breakers on all Feign client methods with:
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - slidingWindowSize: 10
3. Add @CircuitBreaker annotations with fallback methods that return appropriate error responses
4. Add circuit breaker health indicator to actuator
Open a PR with these changes.
```

---

### 2.2 Add Idempotency for Financial Operations

**Gap**: Duplicate transfers possible on retry  
**Severity**: High | **Effort**: Medium

**What to do**:
- Add `idempotencyKey` field to `FundTransferRequest` and `UtilityPaymentRequest`
- Store idempotency keys in a dedicated table with TTL
- Before processing, check if key exists; if so, return cached response
- Require `X-Idempotency-Key` header on all POST endpoints

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement idempotency:
1. Add an IdempotencyKey entity/table in fund-transfer-service and utility-payment-service 
   with columns: key (unique), response (JSON), created_at
2. Add X-Idempotency-Key header requirement to POST endpoints
3. Before processing a transfer/payment, check if the key exists:
   - If exists: return the cached response
   - If not: process normally and store the key+response
4. Add a filter/interceptor that enforces the idempotency key presence on mutations
Open a PR with these changes.
```

---

### 2.3 Extract Shared Library

**Gap**: Duplicated code across services  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Create `banking-common` module
- Move shared classes: `BaseMapper`, `AuditAware`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`
- Set up multi-project Gradle build
- Update all services to depend on `banking-common`

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, extract shared code:
1. Create a settings.gradle at the root to define a multi-project build including all services 
   and a new banking-common module
2. Create banking-common/build.gradle as a plain Java library
3. Move these shared classes into banking-common:
   - BaseMapper, AuditAware, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler (base class)
4. Update all service build.gradle files to add: implementation project(':banking-common')
5. Remove duplicated classes from each service and import from banking-common
6. Verify all services still compile
Open a PR with these changes.
```

---

### 2.4 Add Unit Tests for All Services

**Gap**: Only core-banking-service has tests  
**Severity**: Critical | **Effort**: Large

**What to do**:
- Add unit tests for `FundTransferService` (fund-transfer-service)
- Add unit tests for `UtilityPaymentService` (utility-payment-service)
- Add unit tests for `UserService` and `KeycloakUserService` (user-service)
- Add controller layer tests with `@WebMvcTest`
- Target 80% coverage on service and controller layers

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit tests:
1. For internet-banking-fund-transfer-service: add FundTransferServiceTest with tests for:
   - Successful fund transfer
   - Core banking service failure handling
   - Reading all transfers with pagination
2. For internet-banking-utility-payment-service: add UtilityPaymentServiceTest with tests for:
   - Successful utility payment
   - Core banking service failure handling
   - Reading all payments with pagination
3. For internet-banking-user-service: add UserServiceTest with tests for:
   - User registration success
   - Duplicate email rejection
   - User not found in core banking
   - Email mismatch
   - User approval flow
   - User listing
4. Use Mockito to mock Feign clients and repositories
5. Run all tests and ensure they pass
Open a PR with these changes.
```

---

### 2.5 Add Integration Tests with Testcontainers

**Gap**: No integration tests  
**Severity**: High | **Effort**: Large

**What to do**:
- Add Testcontainers dependency to all services
- Create integration tests for core-banking-service with real MySQL
- Create contract tests using WireMock for Feign clients
- Verify Flyway migrations run correctly

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests:
1. Add testcontainers-mysql dependency to core-banking-service
2. Create CoreBankingIntegrationTest using @SpringBootTest with a MySQL Testcontainer:
   - Test full fund transfer flow (create accounts → transfer → verify balances)
   - Test utility payment flow
   - Verify Flyway migrations apply correctly
3. Add WireMock dependency to fund-transfer-service and utility-payment-service
4. Create Feign client contract tests that verify requests are correctly formed
5. Ensure all tests pass with `./gradlew test`
Open a PR with these changes.
```

---

### 2.6 Add Custom Metrics and Health Checks

**Gap**: No business metrics or dependency health checks  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Add Prometheus metrics endpoint (`micrometer-registry-prometheus`)
- Add custom counters: `fund_transfer_total`, `utility_payment_total`, `fund_transfer_failed_total`
- Add custom timers: `fund_transfer_duration`, `utility_payment_duration`
- Add custom health indicators for MySQL, Keycloak, and downstream services

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add observability:
1. Add micrometer-registry-prometheus dependency to all services
2. Expose /actuator/prometheus endpoint in actuator configuration
3. In FundTransferService, add Micrometer Counter for successful/failed transfers 
   and Timer for transfer duration
4. In UtilityPaymentService, add Counter for successful/failed payments and Timer for duration
5. Add a custom HealthIndicator in user-service that checks Keycloak connectivity
6. Add a custom HealthIndicator in fund-transfer-service that checks core-banking-service 
   via a lightweight GET endpoint
7. Configure actuator to expose health details: management.endpoint.health.show-details=always
Open a PR with these changes.
```

---

### 2.7 Implement Structured Logging

**Gap**: Inconsistent, unstructured logging  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Add Logback JSON encoder (`logstash-logback-encoder`)
- Configure JSON logging format for all services
- Add MDC context for trace IDs (automatic with Micrometer)
- Remove `toString()` calls on request objects in log statements
- Add log sanitization for PII fields

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement structured logging:
1. Add logstash-logback-encoder dependency to all services
2. Create a shared logback-spring.xml that outputs JSON in non-local profiles 
   and human-readable format in local/dev profile
3. Remove all .toString() logging of request objects (potential PII leak)
4. Replace with specific field logging: log.info("Fund transfer from={} to={} amount={}", 
   request.getFromAccount(), request.getToAccount(), request.getAmount())
5. Ensure trace IDs from Micrometer/Brave appear automatically in log output
Open a PR with these changes.
```

---

## Phase 3: Polish (6-12 Weeks)

Improvements that bring the system to production-grade quality.

### 3.1 Implement Saga Pattern for Distributed Transactions

**Gap**: Non-atomic transaction processing across services  
**Severity**: High | **Effort**: Large

**What to do**:
- Implement the Transactional Outbox pattern
- Add a state machine for transfer lifecycle: `INITIATED` → `CORE_PROCESSING` → `COMPLETED` / `FAILED`
- Add compensation logic: if core-banking-service reports success but local save fails, trigger reversal
- Add a scheduled reconciliation job to detect stuck transactions

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement the Saga pattern:
1. Add a TransferState enum: INITIATED, CORE_PROCESSING, COMPLETED, FAILED, COMPENSATING
2. Refactor FundTransferService to:
   a. Save entity as INITIATED
   b. Update to CORE_PROCESSING before Feign call
   c. On success: update to COMPLETED
   d. On failure: update to FAILED
   e. On ambiguous failure (timeout): mark as CORE_PROCESSING for reconciliation
3. Create a FundTransferReconciliationJob (@Scheduled) that:
   - Finds transfers stuck in CORE_PROCESSING for > 5 minutes
   - Queries core-banking-service to verify actual status
   - Updates local record accordingly
4. Apply the same pattern to UtilityPaymentService
Open a PR with these changes.
```

---

### 3.2 Add Contract Tests (Spring Cloud Contract)

**Gap**: No consumer-driven contract tests  
**Severity**: High | **Effort**: Medium

**What to do**:
- Add Spring Cloud Contract to core-banking-service (producer)
- Define contracts for all endpoints consumed by other services
- Generate stubs for consumer services to test against
- Add contract verification to CI pipeline

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add contract tests:
1. Add spring-cloud-contract-verifier to core-banking-service
2. Create contract definitions in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number} (success and not-found cases)
   - POST /api/v1/transaction/fund-transfer (success and insufficient-funds cases)
   - POST /api/v1/transaction/util-payment (success case)
   - GET /api/v1/user/{identification} (success and not-found cases)
3. Create a base test class for contract verification
4. Generate and publish stubs
5. In consumer services, add spring-cloud-contract-stub-runner and write stub-based tests 
   for Feign clients
Open a PR with these changes.
```

---

### 3.3 Complete OpenAPI Documentation

**Gap**: Incomplete auto-generated API docs  
**Severity**: Low | **Effort**: Small

**What to do**:
- Add typed `ResponseEntity<T>` to all controller methods
- Add `@ApiResponse` annotations for success and error cases
- Add `@Schema` annotations to DTOs
- Configure global OpenAPI info (title, version, description, contact)
- Add API gateway aggregation of all service OpenAPI specs

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, complete OpenAPI documentation:
1. Add typed ResponseEntity<T> returns to all controller methods (replace raw ResponseEntity)
2. Add @ApiResponse annotations for 200, 400, 404, 422, 500 cases on each endpoint
3. Add @Schema annotations with descriptions to all DTO fields
4. Add a global OpenApiConfig @Configuration class in each service with:
   - Title, version, description, contact info
5. Verify Swagger UI loads correctly at /swagger-ui.html for each service
Open a PR with these changes.
```

---

### 3.4 Set Up CI/CD Pipeline

**Gap**: No automated build/test pipeline  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Create GitHub Actions workflow for PR checks (build + test)
- Create workflow for Docker image builds on merge to main
- Add Gradle build caching
- Add parallel service builds

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, set up CI/CD:
1. Create .github/workflows/pr-checks.yml that on pull_request:
   - Sets up Java 21
   - Runs ./gradlew build for each service in parallel
   - Runs ./gradlew test for each service
   - Uploads test reports as artifacts
2. Create .github/workflows/docker-build.yml that on push to main:
   - Builds Docker images for all services
   - Tags with commit SHA and 'latest'
   - Pushes to container registry (configurable)
3. Add Gradle build cache configuration
Open a PR with these changes.
```

---

### 3.5 Add Fallback Behavior for Partial Outages

**Gap**: No graceful degradation  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- User listing: if Keycloak is down, return local data without email enrichment
- Fund transfer read: always return local data regardless of core-banking availability
- Health endpoint: report degraded (not DOWN) when non-critical dependencies are unavailable

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, add fallback behavior:
1. In UserService.readUsers(): wrap Keycloak calls in try-catch; if Keycloak is unavailable, 
   return users with local data only (skip email enrichment)
2. In FundTransferService.readAllTransfers(): this already reads from local DB only, 
   so add a @CircuitBreaker fallback annotation as documentation
3. Add a degraded health status concept: when Keycloak is down, health should report 
   status=UP with details showing keycloak=DOWN rather than failing the entire health check
4. Document the degradation behavior in a new RESILIENCE.md file
Open a PR with these changes.
```

---

### 3.6 Implement Per-Service Database Users

**Gap**: Single overly-permissive database user  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Create separate MySQL users per service in `privileges.sql`
- Grant only necessary permissions to each service's specific database
- Update connection configuration per service

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, create per-service DB users:
1. Update docker-compose/mysql/privileges.sql to create 4 separate users:
   - core_banking_user: CRUD on banking_core_service only
   - fund_transfer_user: CRUD on banking_core_fund_transfer_service only
   - user_service_user: CRUD on banking_core_user_service only
   - utility_payment_user: CRUD on banking_core_utility_payment_service only
2. Remove the overly-permissive javatodev_development user
3. Document the credential management approach in the .env.example
Open a PR with these changes.
```

---

### 3.7 Add API Versioning Strategy Documentation

**Gap**: No documented versioning strategy  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Document the URL-based versioning convention (`/api/v1/`)
- Define rules for when to increment versions
- Add deprecation policy documentation
- Consider Accept header versioning for internal service APIs

**Devin Prompt**:
```
In the ts-java-spring-boot-internet-banking-microservices repo, document API versioning:
1. Create docs/API_VERSIONING.md with:
   - Current convention: URL-based /api/v{n}/
   - Rules for version bumps (breaking changes only)
   - Deprecation policy (support N-1 for 6 months)
   - Internal vs external API versioning strategy
2. Add API version info to OpenAPI configuration
Open a PR with these changes.
```

---

## Priority Matrix

```
                    LOW EFFORT                    HIGH EFFORT
           ┌──────────────────────────┬──────────────────────────┐
           │                          │                          │
 CRITICAL  │  1.2 Remove credentials  │  2.4 Add unit tests      │
           │  1.1 Fix error handling  │  2.1 Circuit breakers    │
           │                          │                          │
           ├──────────────────────────┼──────────────────────────┤
           │                          │                          │
   HIGH    │  1.3 Input validation    │  2.5 Integration tests   │
           │  1.4 Timeouts            │  2.2 Idempotency         │
           │  1.5 Retry policies      │  3.1 Saga pattern        │
           │                          │  3.2 Contract tests      │
           ├──────────────────────────┼──────────────────────────┤
           │                          │                          │
  MEDIUM   │  1.6 Keycloak fix        │  2.3 Shared library      │
           │  1.7 Pagination          │  2.6 Metrics/health      │
           │  2.7 Structured logging  │  3.4 CI/CD pipeline      │
           │  3.6 Per-service DB user │  3.5 Fallback behavior   │
           │                          │                          │
           ├──────────────────────────┼──────────────────────────┤
           │                          │                          │
   LOW     │  3.3 OpenAPI docs        │                          │
           │  3.7 API versioning docs │                          │
           │                          │                          │
           └──────────────────────────┴──────────────────────────┘
```

---

## Success Metrics

| Phase | Metric | Target |
|-------|--------|--------|
| Phase 1 | Zero hardcoded credentials in source | 0 |
| Phase 1 | Error responses use correct HTTP status codes | 100% |
| Phase 1 | All request DTOs validated | 100% |
| Phase 2 | Unit test coverage (service + controller layers) | > 80% |
| Phase 2 | Circuit breaker configured on all Feign clients | 100% |
| Phase 2 | Custom business metrics exposed | 5+ metrics |
| Phase 3 | Contract test coverage for inter-service APIs | 100% |
| Phase 3 | CI pipeline runs on every PR | Yes |
| Phase 3 | Mean time to detect stuck transactions | < 10 min |
