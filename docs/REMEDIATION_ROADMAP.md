# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (Critical fixes, Small/Medium effort)

These items address the most severe risks with relatively low effort. Target: **1-2 weeks**.

### 1.1 Fix Balance Calculation Bug (Gap 7.6)

**Priority:** P0 - Data corruption risk
**Effort:** Small
**Impact:** Prevents double-subtraction bug that causes incorrect `availableBalance` on every fund transfer and utility payment.

The `availableBalance` is being set to `actualBalance - amount` *after* `actualBalance` was already reduced, resulting in a double subtraction. The same bug exists in both `internalFundTransfer()` and `utilPayment()` in `TransactionService`.

> **Devin Prompt:**
> ```
> Fix the balance calculation bug in core-banking-service TransactionService.
>
> In internalFundTransfer(), after subtracting amount from actualBalance,
> availableBalance is set to actualBalance.subtract(amount) AGAIN (double subtraction).
> The correct behavior is: availableBalance = actualBalance (they should match after the debit).
>
> The same bug exists in utilPayment(). Fix both methods.
> Also add optimistic locking (@Version) to BankAccountEntity to prevent
> concurrent balance update race conditions.
>
> Add unit tests for both fixes.
> ```

---

### 1.2 Add Input Validation to All DTOs (Gap 4.1)

**Priority:** P0 - Security vulnerability
**Effort:** Medium
**Impact:** Prevents null pointer exceptions, negative transfer amounts, and malformed requests from reaching business logic.

> **Devin Prompt:**
> ```
> Add Bean Validation (jakarta.validation) to all request DTOs across all services:
>
> 1. Add spring-boot-starter-validation dependency to all service build.gradle files
> 2. Add validation annotations to these DTOs:
>    - core-banking FundTransferRequest: @NotBlank fromAccount/toAccount, @NotNull @Positive amount
>    - core-banking UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber/account
>    - user-service User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
>    - user-service UserUpdateRequest: @NotNull status
>    - fund-transfer FundTransferRequest: @NotBlank fromAccount/toAccount, @NotNull @Positive amount
>    - utility-payment UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber/account
> 3. Add @Valid to all @RequestBody parameters in controllers
> 4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler
>    that returns HTTP 422 with field-level error details
> 5. Add unit tests for validation failures
> ```

---

### 1.3 Fix HTTP Status Codes in Error Handlers (Gap 2.2, 2.4)

**Priority:** P1 - API correctness
**Effort:** Small
**Impact:** Clients get correct HTTP semantics; eliminates stack trace leakage.

> **Devin Prompt:**
> ```
> Fix the GlobalExceptionHandler in all 4 business services
> (core-banking, user-service, fund-transfer, utility-payment):
>
> 1. Map EntityNotFoundException -> HTTP 404
> 2. Map InsufficientFundsException -> HTTP 422
> 3. Map UserAlreadyRegisteredException -> HTTP 409
> 4. Map InvalidBankingUserException -> HTTP 404
> 5. Map InvalidEmailException -> HTTP 400
> 6. Change the generic Exception handler to return HTTP 500 with a safe
>    generic message (do NOT include exception details in the response body)
> 7. All error responses must use the structured ErrorResponse format
>    (never raw strings)
> 8. Add type parameters to all ResponseEntity return types in controllers
>    (e.g., ResponseEntity<BankAccount> instead of raw ResponseEntity)
> ```

---

### 1.4 Unify GlobalExceptionHandler Across Services (Gap 2.1)

**Priority:** P1 - Consistency
**Effort:** Small
**Impact:** Single error response format for all API consumers.

> **Devin Prompt:**
> ```
> Unify the GlobalExceptionHandler across all 4 business services.
> Pick the core-banking-service implementation as the baseline and ensure all
> services have identical exception handling behavior:
>
> 1. Use ErrorResponse.builder() pattern consistently
> 2. Include the same exception-to-HTTP-status mappings in all handlers
> 3. Ensure the catch-all Exception handler returns HTTP 500 with a safe message
> 4. Add a handler for MethodArgumentNotValidException (for Bean Validation errors)
> ```

---

### 1.5 Add Feign Timeouts and Error Decoders (Gap 2.5, 7.3)

**Priority:** P1 - Resilience
**Effort:** Small
**Impact:** Prevents thread pool exhaustion from slow/unavailable downstream services.

> **Devin Prompt:**
> ```
> Configure Feign client timeouts and error handling for fund-transfer-service
> and utility-payment-service:
>
> 1. Copy the CustomFeignErrorDecoder from user-service to both services
>    (adapt package names as needed)
> 2. Update both services' CustomFeignClientConfiguration to register the error decoder
> 3. Add Feign timeout configuration to application.yml (via config server or bootstrap):
>    - connectTimeout: 5000ms
>    - readTimeout: 10000ms
> 4. Test that a 400 error from core-banking-service is properly decoded into
>    a SimpleBankingGlobalException
> ```

---

### 1.6 Externalize Hardcoded Credentials (Gap 4.2)

**Priority:** P1 - Security
**Effort:** Small
**Impact:** Prevents credential leakage; prepares for production deployment.

> **Devin Prompt:**
> ```
> Replace all hardcoded credentials in docker-compose files and SQL scripts
> with environment variables:
>
> 1. In docker-compose.yml and docker-compose-support-apps.yml:
>    - Replace MySQL root password with ${MYSQL_ROOT_PASSWORD}
>    - Replace Keycloak admin password with ${KEYCLOAK_ADMIN_PASSWORD}
>    - Replace Keycloak DB password with ${KC_DB_PASSWORD}
> 2. In docker-compose/mysql/Dockerfile: use ARG/ENV for the root password
> 3. Create a .env.example file with placeholder values
> 4. Add .env to .gitignore
> 5. Remove the Keycloak client-secret from the test application.yml in
>    user-service (use a test-specific value or mock)
> 6. Update README.md with instructions to copy .env.example to .env
> ```

---

### 1.7 Fix Wrong OpenAPI Starter Dependency (Gap 5.6)

**Priority:** P2 - Correctness
**Effort:** Small
**Impact:** Swagger UI will actually work correctly on servlet-based services.

> **Devin Prompt:**
> ```
> In the build.gradle of these 4 services: core-banking-service,
> internet-banking-user-service, internet-banking-fund-transfer-service,
> internet-banking-utility-payment-service:
>
> Replace:
>   implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
> With:
>   implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
>
> These services use Spring MVC (Web), not WebFlux, so they need the webmvc starter.
> Update the version to 2.5.0 (latest stable).
>
> Verify that /swagger-ui.html loads correctly for each service.
> ```

---

### 1.8 Secure Actuator Endpoints (Gap 6.2)

**Priority:** P2 - Security
**Effort:** Small
**Impact:** Prevents information disclosure of environment variables, config, beans.

> **Devin Prompt:**
> ```
> Restrict actuator endpoint exposure in the API gateway security config:
>
> 1. In SecurityConfiguration.java, change the actuator permit rules to only
>    allow /actuator/health and /actuator/info without authentication
> 2. All other actuator endpoints should require authentication
> 3. In each service's application.yml (via config server), configure:
>    management.endpoints.web.exposure.include=health,info,prometheus
>    management.endpoint.health.show-details=when-authorized
> ```

---

### 1.9 Prevent Sensitive Data Logging (Gap 6.1)

**Priority:** P2 - Security
**Effort:** Small
**Impact:** Stops passwords and account numbers from appearing in log files.

> **Devin Prompt:**
> ```
> Fix logging across all services to prevent sensitive data exposure:
>
> 1. In user-service UserController.createUser(): Do NOT log the full request
>    (it contains the password). Log only the email.
> 2. In all controllers: replace request.toString() logging with specific
>    non-sensitive fields only
> 3. Add @ToString.Exclude on the password field in user-service User DTO
> 4. Consider adding a log sanitization pattern or using Lombok's
>    @ToString(exclude = {"password", "amount"}) where appropriate
> ```

---

## Phase 2: Important Improvements (High severity, Medium/Large effort)

These items significantly improve reliability and maintainability. Target: **3-6 weeks**.

### 2.1 Add Circuit Breakers with Resilience4j (Gap 7.1, 7.2, 7.4)

**Priority:** P1 - Resilience
**Effort:** Medium
**Impact:** Prevents cascade failures when core-banking-service is unavailable.

> **Devin Prompt:**
> ```
> Add Resilience4j circuit breaker, retry, and fallback to all Feign clients:
>
> 1. Add these dependencies to fund-transfer-service, utility-payment-service,
>    and user-service build.gradle:
>    - implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
> 2. Configure Resilience4j in application.yml:
>    - Circuit breaker: slidingWindowSize=10, failureRateThreshold=50,
>      waitDurationInOpenState=30s
>    - Retry: maxAttempts=3, waitDuration=1s, retryExceptions=IOException,TimeoutException
>    - TimeLimiter: timeoutDuration=10s
> 3. Add @CircuitBreaker annotations to Feign client interfaces with fallback factories
> 4. Create fallback factory classes that return meaningful error responses
>    (e.g., "Core banking service is temporarily unavailable")
> 5. Add unit tests for circuit breaker behavior
> ```

---

### 2.2 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (Gap 3.1)

**Priority:** P1 - Quality
**Effort:** Large
**Impact:** Covers the 3 business services that currently have 0% test coverage.

> **Devin Prompt:**
> ```
> Add comprehensive unit tests for these services (use Mockito, target >80% coverage):
>
> 1. internet-banking-user-service:
>    - UserService: test createUser (success, duplicate email, user not found in core,
>      email mismatch, Keycloak failure), readUsers, readUser, updateUser (approve, disable)
>    - KeycloakUserService: test createUser, readUser, readUserByEmail, updateUser
>    - UserController: test all endpoints with MockMvc
>
> 2. internet-banking-fund-transfer-service:
>    - FundTransferService: test fundTransfer (success, core-banking error, insufficient
>      funds passthrough), readAllTransfers
>    - FundTransferController: test all endpoints with MockMvc
>
> 3. internet-banking-utility-payment-service:
>    - UtilityPaymentService: test utilPayment (success, core-banking error),
>      readPayments
>    - UtilityPaymentController: test all endpoints with MockMvc
>
> Mock all Feign clients and Keycloak dependencies. Use H2 in-memory database
> for repository tests.
> ```

---

### 2.3 Extract Shared Library (Gap 1.2)

**Priority:** P2 - Maintainability
**Effort:** Medium
**Impact:** Eliminates code duplication across 4 services.

> **Devin Prompt:**
> ```
> Create a shared library module for common code used across all services:
>
> 1. Create a new module: internet-banking-common
> 2. Move these classes into it:
>    - BaseMapper<E, D>
>    - AuditAware (MappedSuperclass)
>    - SimpleBankingGlobalException
>    - ErrorResponse
>    - GlobalExceptionHandler (base implementation)
>    - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
> 3. Publish as a local Maven artifact or use Gradle composite builds
> 4. Update all 4 business services to depend on internet-banking-common
> 5. Remove the duplicated classes from each service
> 6. Verify all services still compile and tests pass
> ```

---

### 2.4 Add Role-Based Access Control (Gap 4.4)

**Priority:** P2 - Security
**Effort:** Medium
**Impact:** Prevents regular users from accessing admin-only operations.

> **Devin Prompt:**
> ```
> Implement role-based access control using Keycloak roles:
>
> 1. Define roles in Keycloak realm: ADMIN, USER
> 2. Update the API Gateway SecurityConfiguration to enforce role-based rules:
>    - /user/api/v1/bank-users/update/** -> requires ADMIN role
>    - /user/api/v1/bank-users (GET, list all) -> requires ADMIN role
>    - /fund-transfer/api/v1/transfer (GET, list all) -> requires ADMIN role
>    - /utility-payment/api/v1/utility-payment (GET, list all) -> requires ADMIN role
>    - All other authenticated endpoints -> require USER role
> 3. Extract roles from the JWT token claims (Keycloak realm_access.roles)
> 4. Update the Keycloak realm-export.json with the new roles and assign
>    ADMIN role to the test user
> 5. Add integration tests for role-based access
> ```

---

### 2.5 Set Up Multi-Project Gradle Build (Gap 1.1)

**Priority:** P2 - Developer experience
**Effort:** Medium
**Impact:** Single command to build all services; consistent dependency versions.

> **Devin Prompt:**
> ```
> Convert the project to a Gradle multi-project build:
>
> 1. Create a root settings.gradle that includes all 7 service modules
>    plus the new internet-banking-common module
> 2. Create a root build.gradle with:
>    - Shared repositories (mavenCentral)
>    - Shared Java 21 source compatibility
>    - Common Spring Boot 3.2.4 and Spring Cloud 2023.0.0 version management
>    - Common test configuration (useJUnitPlatform)
> 3. Simplify each service's build.gradle to only declare service-specific dependencies
> 4. Remove individual settings.gradle from each service
> 5. Remove individual Gradle wrappers (keep only root)
> 6. Verify: ./gradlew build from root compiles all services
> 7. Verify: ./gradlew test from root runs all tests
> ```

---

### 2.6 Add Distributed Transaction Safety (Gap 7.7)

**Priority:** P2 - Reliability
**Effort:** Large
**Impact:** Prevents stuck transactions when services crash mid-flow.

> **Devin Prompt:**
> ```
> Implement a simple Saga pattern for fund transfers and utility payments
> to handle partial failures:
>
> 1. In fund-transfer-service FundTransferService.fundTransfer():
>    - Wrap the core-banking Feign call in a try-catch
>    - On FeignException or timeout: update local entity status to FAILED
>    - On success: update to SUCCESS (already done)
>    - Add a scheduled job to retry FAILED transfers (max 3 retries)
>
> 2. In utility-payment-service UtilityPaymentService.utilPayment():
>    - Same pattern: catch Feign failures and mark as FAILED
>    - Add retry scheduler
>
> 3. Add a status field 'retryCount' to both FundTransferEntity and
>    UtilityPaymentEntity
>
> 4. Add a GET endpoint to check transaction status by reference ID
>
> 5. Add unit tests for failure scenarios
> ```

---

### 2.7 Add Prometheus Metrics (Gap 6.4)

**Priority:** P2 - Observability
**Effort:** Small
**Impact:** Enables production monitoring dashboards.

> **Devin Prompt:**
> ```
> Add Prometheus metrics to all services:
>
> 1. Add dependency to all service build.gradle files:
>    implementation 'io.micrometer:micrometer-registry-prometheus'
>
> 2. Configure in application.yml (via config server):
>    management.endpoints.web.exposure.include=health,info,prometheus
>    management.prometheus.metrics.export.enabled=true
>
> 3. Add custom metrics for key business operations:
>    - counter: fund_transfers_total (tags: status=success|failed)
>    - counter: utility_payments_total (tags: status=success|failed)
>    - counter: user_registrations_total (tags: status=success|failed)
>    - timer: fund_transfer_duration_seconds
>    - timer: utility_payment_duration_seconds
>
> 4. Add a docker-compose-monitoring.yml with Prometheus + Grafana containers
>    that scrape all service /actuator/prometheus endpoints
> ```

---

## Phase 3: Polish (Medium/Low severity, refinements)

These items improve developer experience, API polish, and long-term maintainability. Target: **ongoing**.

### 3.1 Add Integration Tests with Testcontainers (Gap 3.2)

**Priority:** P3 - Quality
**Effort:** Large
**Impact:** Validates database interactions and service integration with real infrastructure.

> **Devin Prompt:**
> ```
> Add integration tests using Testcontainers for all 4 business services:
>
> 1. Add Testcontainers dependencies:
>    testImplementation 'org.testcontainers:mysql:1.19.7'
>    testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
>
> 2. For core-banking-service:
>    - Test Flyway migrations run successfully against real MySQL
>    - Test AccountService and TransactionService with real database
>    - Test fund transfer end-to-end (debit + credit)
>
> 3. For user-service, fund-transfer, utility-payment:
>    - Test repository operations with real MySQL
>    - Test service layer with mocked Feign clients but real database
>
> 4. Create a base test class with Testcontainers MySQL configuration
>    to share across all integration tests
> ```

---

### 3.2 Add Contract Tests Between Services (Gap 3.3)

**Priority:** P3 - Quality
**Effort:** Large
**Impact:** Detects breaking API changes between services before deployment.

> **Devin Prompt:**
> ```
> Add Spring Cloud Contract tests for inter-service communication:
>
> 1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
>    - testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
>
> 2. Write contract definitions (Groovy DSL) for core-banking-service endpoints:
>    - GET /api/v1/account/bank-account/{account_number}
>    - GET /api/v1/user/{identification}
>    - POST /api/v1/transaction/fund-transfer
>    - POST /api/v1/transaction/util-payment
>
> 3. Generate contract stubs from core-banking-service
>
> 4. Add contract stub dependencies to consumer services (fund-transfer,
>    utility-payment, user-service) and write consumer-side contract tests
>    using @AutoConfigureStubRunner
> ```

---

### 3.3 Standardize REST API Conventions (Gap 5.1, 5.2, 5.4)

**Priority:** P3 - API quality
**Effort:** Small
**Impact:** Cleaner, more predictable API for consumers.

> **Devin Prompt:**
> ```
> Standardize REST API conventions across all services:
>
> 1. URL naming:
>    - Use kebab-case consistently for path segments
>    - Use camelCase for path parameters (matching Java field names)
>    - Rename: /bank-account/{account_number} -> /bank-accounts/{accountNumber}
>    - Rename: /util-account/{account_name} -> /utility-accounts/{providerName}
>
> 2. RESTful resource naming:
>    - Rename: POST /bank-users/register -> POST /bank-users
>    - Rename: PATCH /bank-users/update/{id} -> PATCH /bank-users/{id}
>    - Rename: /api/v1/user -> /api/v1/users (plural)
>
> 3. Pagination response wrapper:
>    - Create a generic PageResponse<T> with: content, page, size, totalElements,
>      totalPages, hasNext
>    - Use it for all paginated GET endpoints
>    - Set default page size to 20, max to 100
>
> 4. Update Feign clients in consumer services to match renamed endpoints
> 5. Update Postman collection
> ```

---

### 3.4 Implement Rate Limiting at Gateway (Gap 7.5)

**Priority:** P3 - Resilience
**Effort:** Medium
**Impact:** Protects against traffic spikes and abuse.

> **Devin Prompt:**
> ```
> Add rate limiting to the API Gateway using Spring Cloud Gateway's
> built-in RequestRateLimiter filter:
>
> 1. Add Redis dependency to api-gateway:
>    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
>
> 2. Add Redis container to docker-compose.yml
>
> 3. Configure rate limiter in gateway application.yml:
>    - Default: 10 requests/second per user
>    - Fund transfer: 5 requests/second per user
>    - User registration: 3 requests/minute per IP
>
> 4. Use the authenticated principal as the key resolver for authenticated
>    endpoints, and client IP for public endpoints
>
> 5. Return HTTP 429 Too Many Requests with Retry-After header
> ```

---

### 3.5 Add CI/CD Pipeline (Gap 6 - general)

**Priority:** P3 - DevOps
**Effort:** Medium
**Impact:** Automated build, test, and deployment.

> **Devin Prompt:**
> ```
> Create a GitHub Actions CI pipeline for the project:
>
> 1. Create .github/workflows/ci.yml with these jobs:
>    - build: Run ./gradlew build for all services
>    - test: Run ./gradlew test for all services (with MySQL Testcontainers)
>    - lint: Run Checkstyle or SpotBugs
>    - security: Run OWASP Dependency-Check
>    - docker: Build Docker images for all services
>
> 2. Trigger on: push to main, pull requests to main
>
> 3. Use Java 21, cache Gradle dependencies
>
> 4. Create .github/workflows/release.yml:
>    - Triggered on version tags (v*)
>    - Build and push Docker images to GitHub Container Registry
>    - Create GitHub Release with changelog
> ```

---

### 3.6 Add Custom Health Checks (Gap 6.3)

**Priority:** P3 - Observability
**Effort:** Small
**Impact:** Better visibility into service health beyond just "running".

> **Devin Prompt:**
> ```
> Add custom health indicators to services:
>
> 1. core-banking-service: HealthIndicator that checks MySQL connection
> 2. user-service: HealthIndicator that checks Keycloak reachability
>    (call /realms/javatodev-internet-banking/.well-known/openid-configuration)
> 3. fund-transfer-service: HealthIndicator that checks core-banking-service
>    is registered in Eureka
> 4. utility-payment-service: Same as fund-transfer
> 5. api-gateway: HealthIndicator that checks all downstream services
>    are registered in Eureka
>
> Configure health groups:
>   management.endpoint.health.group.liveness.include=ping
>   management.endpoint.health.group.readiness.include=db,keycloak,corebanking
> ```

---

### 3.7 Standardize Package Structure (Gap 1.3, 1.4)

**Priority:** P3 - Maintainability
**Effort:** Small
**Impact:** Consistent navigation across all services.

> **Devin Prompt:**
> ```
> Standardize the package structure across all business services to follow
> this convention:
>
>   com.javatodev.finance
>   ├── configuration/    (Spring @Configuration classes)
>   ├── controller/       (REST controllers)
>   ├── exception/        (exceptions and handlers)
>   ├── model/
>   │   ├── dto/          (request/response DTOs)
>   │   ├── entity/       (JPA entities)
>   │   └── mapper/       (entity-DTO mappers)
>   ├── repository/       (Spring Data repositories)
>   └── service/
>       └── client/       (Feign clients)
>
> Move classes as needed to match this structure. Also convert all mappers
> to Spring @Component beans and inject them via constructor injection
> instead of new().
> ```

---

### 3.8 Add Dependency Vulnerability Scanning (Gap 4.6)

**Priority:** P3 - Security
**Effort:** Small
**Impact:** Catches known CVEs in third-party dependencies.

> **Devin Prompt:**
> ```
> Add OWASP Dependency-Check to the Gradle build:
>
> 1. Add the plugin to the root build.gradle:
>    plugins {
>      id 'org.owasp.dependencycheck' version '9.0.10'
>    }
>
> 2. Configure it to fail on CVSS score >= 7:
>    dependencyCheck {
>      failBuildOnCVSS = 7.0f
>      formats = ['HTML', 'JSON']
>    }
>
> 3. Run ./gradlew dependencyCheckAnalyze and review the report
> 4. Triage any findings: update vulnerable dependencies or suppress
>    false positives with a suppression XML file
> 5. Add to CI pipeline
> ```

---

### 3.9 Fix Keycloak Singleton Thread Safety (Gap 4.5)

**Priority:** P3 - Correctness
**Effort:** Small
**Impact:** Eliminates potential race condition in Keycloak client initialization.

> **Devin Prompt:**
> ```
> Fix the thread-unsafe Keycloak singleton in user-service KeycloakProperties:
>
> Replace the static Keycloak field with a proper Spring @Bean definition:
>
> 1. Remove the static keycloakInstance field and getInstance() method
> 2. Create a @Configuration class (KeycloakConfig) that defines a @Bean
>    Keycloak keycloak() method
> 3. Inject the Keycloak bean into KeycloakManager via constructor injection
> 4. Update KeycloakManager to use the injected Keycloak instance
> 5. Verify user registration and approval flows still work
> ```

---

## Summary

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 9 items | 1-2 weeks | Critical bugs fixed, basic security, proper error handling |
| **Phase 2** | 7 items | 3-6 weeks | Resilience patterns, test coverage, shared library, RBAC |
| **Phase 3** | 9 items | Ongoing | API polish, CI/CD, monitoring, contract tests |

### Execution Order Within Each Phase

**Phase 1 (recommended order):**
1. Fix balance calculation bug (1.1) - data integrity
2. Add input validation (1.2) - security
3. Fix HTTP status codes (1.3) - API correctness
4. Unify exception handlers (1.4) - consistency
5. Add Feign timeouts (1.5) - resilience
6. Externalize credentials (1.6) - security
7. Fix OpenAPI dependency (1.7) - correctness
8. Secure actuator endpoints (1.8) - security
9. Fix sensitive logging (1.9) - security

**Phase 2 (recommended order):**
1. Circuit breakers (2.1) - resilience foundation
2. Unit tests (2.2) - quality safety net
3. Shared library (2.3) - maintainability
4. RBAC (2.4) - security
5. Multi-project build (2.5) - DX
6. Distributed transaction safety (2.6) - reliability
7. Prometheus metrics (2.7) - observability

**Phase 3:** Items can be executed in any order based on team priorities.
