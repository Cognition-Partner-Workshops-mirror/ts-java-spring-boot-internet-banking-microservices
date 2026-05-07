# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, impact, and effort.

- **Phase 1 — Quick Wins** (1-2 weeks): Low-effort, high-impact fixes that immediately improve security, stability, and developer experience.
- **Phase 2 — Important** (3-6 weeks): Medium-effort improvements that close major architectural gaps.
- **Phase 3 — Polish** (6-12 weeks): Larger structural investments that bring the system to production-grade maturity.

Each item includes a sample Devin prompt you can use to execute the remediation.

---

## Phase 1: Quick Wins

### 1.1 Fix Exception Information Leakage (Gap 2.3)

**Priority**: Immediate — security risk
**Effort**: Small

Replace the catch-all `Exception` handler in `GlobalExceptionHandler` across all services to return a generic `500` error without exposing internal details.

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler class in all four services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Change the catch-all Exception handler to return HTTP 500 with a generic ErrorResponse body: { "code": "INTERNAL_ERROR", "message": "An unexpected error occurred. Please try again later." }. Do not expose the exception message or stack trace. Also update EntityNotFoundException handlers to return 404 and InsufficientFundsException to return 422. Open a PR with the changes.
> ```

### 1.2 Remove Sensitive Data from Logs (Gap 6.2)

**Priority**: Immediate — security risk
**Effort**: Small

Remove `toString()` logging of request objects that may contain passwords or financial data.

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, audit all log statements across all services. Remove or redact any logging of request bodies that may contain sensitive data (passwords, account numbers, transfer amounts). Replace with safe identifiers only (e.g., log the email but not the password for user registration, log the transaction ID but not the full request for fund transfers). Open a PR.
> ```

### 1.3 Add Input Validation (Gap 4.2)

**Priority**: Immediate — security risk
**Effort**: Medium

Add Jakarta Bean Validation annotations to all request DTOs and enable validation in controllers.

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta Bean Validation to all request DTOs:
> - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
> - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
> - User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
> - UserUpdateRequest: @NotNull status
> Add @Valid annotation to all controller method parameters that accept request bodies. Add a MethodArgumentNotValidException handler to GlobalExceptionHandler that returns 400 with field-level error details. Add spring-boot-starter-validation dependency to each service's build.gradle. Open a PR.
> ```

### 1.4 Fix HTTP Status Codes (Gap 2.2)

**Priority**: High
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler in all services to return appropriate HTTP status codes: EntityNotFoundException → 404 Not Found, InsufficientFundsException → 422 Unprocessable Entity, SimpleBankingGlobalException → 400 Bad Request, generic Exception → 500 Internal Server Error. Ensure all handlers return a consistent ErrorResponse JSON body with "code" and "message" fields. Open a PR.
> ```

### 1.5 Configure Feign Timeouts (Gap 7.3)

**Priority**: High
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add explicit timeout configuration for all Feign clients. In each service that has a Feign client (user-service, fund-transfer-service, utility-payment-service), add the following to application.yml:
> spring.cloud.openfeign.client.config.default.connectTimeout: 5000
> spring.cloud.openfeign.client.config.default.readTimeout: 10000
> Also add spring.cloud.openfeign.client.config.core-banking-service with the same values as a named override. Open a PR.
> ```

### 1.6 Configure Feign Retry (Gap 7.2)

**Priority**: High
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add a Spring Retry-based Retryer configuration for Feign clients. Create a shared FeignRetryConfig class that provides a Retryer.Default bean with period=1000ms, maxPeriod=5000ms, maxAttempts=3. Apply this configuration to all Feign clients. Add the spring-retry dependency to each service's build.gradle. Ensure retries only apply to GET requests (idempotent) — POST requests should NOT be retried. Open a PR.
> ```

### 1.7 Fix Wrong OpenAPI Starter (Gap 5.5)

**Priority**: Medium
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, replace the incorrect springdoc dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Change 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' to 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0' since these services use Spring MVC (not WebFlux). Leave the API Gateway unchanged as it uses WebFlux. Open a PR.
> ```

### 1.8 Add Prometheus Metrics (Gap 6.4)

**Priority**: Medium
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics support. Add 'io.micrometer:micrometer-registry-prometheus' to the dependencies of all six services. Configure actuator to expose the prometheus endpoint by adding management.endpoints.web.exposure.include=health,info,prometheus,metrics to each service's application.yml. Open a PR.
> ```

### 1.9 Add Type Parameters to ResponseEntity (Gap 2.4)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add proper generic type parameters to all ResponseEntity return types in every controller. For example, change `ResponseEntity` to `ResponseEntity<BankAccount>` in AccountController.getBankAccount(). Do this for all controller methods across all services. This improves type safety and OpenAPI documentation accuracy. Open a PR.
> ```

### 1.10 Add JaCoCo Coverage Reporting (Gap 3.4)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add the JaCoCo Gradle plugin to all services. Configure it to generate XML and HTML reports on `./gradlew test`. Set a minimum coverage threshold of 0% initially (so the build doesn't break) with a TODO comment to increase it as tests are added. Open a PR.
> ```

---

## Phase 2: Important

### 2.1 Add Circuit Breakers (Gap 7.1)

**Priority**: Critical
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breaker support to all Feign clients. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j' to fund-transfer-service, utility-payment-service, and user-service. Enable circuit breakers for Feign with spring.cloud.openfeign.circuitbreaker.enabled=true. Configure default circuit breaker settings: failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10. Add fallback classes for each Feign client that return meaningful error responses (e.g., "Core Banking Service is currently unavailable"). Open a PR.
> ```

### 2.2 Fix Distributed Transaction Consistency (Gap 7.5)

**Priority**: Critical
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, fix the distributed transaction consistency issue in FundTransferService and UtilityPaymentService. Currently, if the local database update fails after a successful core-banking call, data is inconsistent. Implement a transactional outbox pattern: (1) wrap the local entity creation and the status update in a single local transaction, (2) if the Feign call to core-banking fails, catch the exception, set the local entity status to FAILED, save it, and return an error response instead of letting the exception propagate. Also fix the availableBalance double-deduction bug in core-banking-service TransactionService: change `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount))` to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())` since actualBalance was already subtracted. Apply the same fix to the credit side and utilPayment method. Open a PR.
> ```

### 2.3 Extract Shared Library (Gap 1.2)

**Priority**: High
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, create a new Gradle module called 'internet-banking-common' that contains all code currently duplicated across services: BaseMapper, AuditAware, AuditorAwareConfig, AuditConfig, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, and GlobalErrorCode. Set up a root settings.gradle and build.gradle that includes all service modules plus the common module. Update each service's build.gradle to depend on the common module. Remove the duplicated classes from each service. Open a PR.
> ```

### 2.4 Unify Gradle Build (Gap 1.1)

**Priority**: Medium (can be combined with 2.3)
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, convert the project to a Gradle multi-project build. Create a root build.gradle with shared configuration (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0 BOM, common dependencies like Lombok and spring-boot-starter-test). Create a root settings.gradle that includes all seven service modules plus the common module. Move shared plugin and dependency declarations to the root build.gradle using subprojects {} block. Each service's build.gradle should only declare service-specific dependencies. Open a PR.
> ```

### 2.5 Add Unit Tests for Untested Services (Gap 3.1)

**Priority**: Critical
**Effort**: Large

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit tests for the three untested business services:
>
> 1. internet-banking-user-service: Test UserService (createUser success/failure paths, readUsers, readUser, updateUser with APPROVED status enabling Keycloak user), test KeycloakUserService with mocked KeycloakManager.
> 2. internet-banking-fund-transfer-service: Test FundTransferService (fundTransfer success path, Feign client failure, readAllTransfers).
> 3. internet-banking-utility-payment-service: Test UtilityPaymentService (utilPayment success path, Feign client failure, readPayments).
>
> Use Mockito to mock repositories, Feign clients, and Keycloak dependencies. Each service should have at least 5-8 test methods covering happy path, error cases, and edge cases. Configure test application.yml profiles with H2, disabled Eureka, and disabled Flyway. Open a PR.
> ```

### 2.6 Add Integration Tests (Gap 3.2)

**Priority**: High
**Effort**: Large

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests for core-banking-service using @SpringBootTest with an H2 database. Test the full HTTP request/response cycle using MockMvc:
> - AccountController: GET bank account (found/not found), GET utility account (found/not found)
> - TransactionController: POST fund-transfer (success, insufficient funds, account not found), POST util-payment
> - UserController: GET user by identification, GET users with pagination
> Use Flyway with H2-compatible SQL scripts or disable Flyway and use schema.sql/data.sql for test data setup. Open a PR.
> ```

### 2.7 Externalize Secrets (Gap 4.1)

**Priority**: Critical
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, externalize all hard-coded credentials. In docker-compose.yml and docker-compose-support-apps.yml, replace all hard-coded passwords with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD:-defaultDevPassword}). Create a .env.example file documenting all required environment variables with placeholder values. Add .env to .gitignore. Update the mysql/privileges.sql to use environment variables or document that the password should be changed. Add a Security section to the README explaining how to configure credentials for different environments. Open a PR.
> ```

### 2.8 Add Rate Limiting (Gap 4.3)

**Priority**: Medium
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting to the API Gateway. Use Spring Cloud Gateway's built-in RequestRateLimiter filter with an in-memory rate limiter (or Redis-based if Redis is added). Configure rate limits: 10 requests/second for the registration endpoint (/user/api/v1/bank-users/register), 50 requests/second for authenticated endpoints. Add the necessary dependencies and configuration to the api-gateway's build.gradle and application.yml. Open a PR.
> ```

### 2.9 Add Structured Logging (Gap 6.1)

**Priority**: Medium
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, configure structured JSON logging for all services. Add 'net.logstash.logback:logstash-logback-encoder:7.4' to all services. Create a shared logback-spring.xml configuration that outputs JSON in production profile and plain text in development. Include trace ID and span ID fields in the JSON output for correlation with Zipkin. Fix all log statements that use string concatenation (e.g., "message {}" + variable) to use proper SLF4J parameterized logging. Open a PR.
> ```

### 2.10 Fix Keycloak Singleton Thread Safety (Gap 4.5)

**Priority**: Medium
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, fix the thread-safety issue in KeycloakProperties.getInstance(). Replace the static singleton pattern with a proper Spring @Bean configuration. Create a KeycloakConfig @Configuration class that provides a Keycloak @Bean using KeycloakBuilder. Remove the static field and getInstance() method from KeycloakProperties. Update KeycloakManager to inject the Keycloak bean directly. Open a PR.
> ```

---

## Phase 3: Polish

### 3.1 Add Consumer-Driven Contract Tests (Gap 3.3)

**Priority**: Medium
**Effort**: Large

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests between services. On the producer side (core-banking-service), define contracts for all Feign client endpoints: GET /api/v1/user/{identification}, GET /api/v1/account/bank-account/{account_number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment. On the consumer side (user-service, fund-transfer-service, utility-payment-service), add stub-based integration tests that verify each Feign client works correctly against the contract stubs. Add spring-cloud-starter-contract-verifier and spring-cloud-starter-contract-stub-runner dependencies. Open a PR.
> ```

### 3.2 Add Fallback Behavior for Feign Clients (Gap 7.4)

**Priority**: Medium
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add fallback implementations for all Feign clients. Create BankingCoreFeignClientFallback, BankingCoreRestClientFallback classes that implement the respective Feign interfaces. Each fallback method should return a meaningful error response or throw a custom ServiceUnavailableException. Register the fallbacks using @FeignClient(fallback = ...) or @FeignClient(fallbackFactory = ...) for access to the exception cause. Open a PR.
> ```

### 3.3 Add Bulkhead Isolation (Gap 7.6)

**Priority**: Medium
**Effort**: Medium

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j bulkhead isolation for Feign clients. Configure separate thread pool bulkheads for each Feign client so that a slow core-banking endpoint doesn't block other requests. Add resilience4j.bulkhead configuration in each service's application.yml with maxConcurrentCalls=10 and maxWaitDuration=500ms. Open a PR.
> ```

### 3.4 Add Pagination Metadata (Gap 5.3)

**Priority**: Medium
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, update all paginated endpoints to return Spring's Page<T> instead of List<T>. This includes:
> - core-banking-service UserController.readUsers()
> - internet-banking-user-service UserController.readUsers()
> - internet-banking-fund-transfer-service FundTransferController.readFundTransfers()
> - internet-banking-utility-payment-service UtilityPaymentController.readPayments()
> Update the service layer to return Page<T> as well (currently they call .getContent() which loses pagination metadata). Open a PR.
> ```

### 3.5 Standardize Package Structure (Gap 1.3)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, standardize the package structure across all services to follow a consistent convention:
> - com.javatodev.finance.controller
> - com.javatodev.finance.service
> - com.javatodev.finance.repository (not model.repository)
> - com.javatodev.finance.model.entity
> - com.javatodev.finance.model.dto
> - com.javatodev.finance.model.dto.request
> - com.javatodev.finance.model.dto.response
> - com.javatodev.finance.model.mapper
> - com.javatodev.finance.configuration
> - com.javatodev.finance.exception
> Move classes as needed in user-service, fund-transfer-service, and utility-payment-service to match this structure. Open a PR.
> ```

### 3.6 Add OWASP Dependency Scanning (Gap 4.6)

**Priority**: Medium
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add the OWASP Dependency-Check Gradle plugin to scan for known vulnerabilities. Add 'org.owasp:dependency-check-gradle:9.0.9' plugin to the root build.gradle (or each service's build.gradle if no root exists yet). Configure it to fail the build on CVSS score >= 7. Add a Gradle task alias `./gradlew dependencyCheckAnalyze` and document it in the README. Open a PR.
> ```

### 3.7 Add Custom Health Checks (Gap 6.3)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators for each service. For services with database access, Spring Boot auto-configures DB health checks — verify this works. For user-service, add a KeycloakHealthIndicator that pings the Keycloak server URL. For fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator that calls the core-banking actuator health endpoint via Feign. Configure management.endpoint.health.show-details=always for detailed health info. Open a PR.
> ```

### 3.8 Standardize RESTful URL Naming (Gap 5.1)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, standardize URL naming to follow RESTful conventions:
> - Change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
> - Change /api/v1/account/util-account/{account_name} to /api/v1/account/utility-account/{providerName}
> - Use consistent kebab-case path variable naming (account-number instead of account_number)
> Update the corresponding Feign client mappings in dependent services. Open a PR.
> ```

### 3.9 Make Mappers Spring Beans (Gap 1.4)

**Priority**: Low
**Effort**: Small

> **Devin Prompt:**
> ```
> In the ts-java-spring-boot-internet-banking-microservices repo, convert all Mapper classes to Spring-managed beans. Add @Component annotation to BankAccountMapper, UserMapper, UtilityAccountMapper, FundTransferMapper, and UtilityPaymentMapper. In service classes, change the mapper fields from `private XMapper mapper = new XMapper()` to `private final XMapper mapper` so they are injected via @RequiredArgsConstructor. Open a PR.
> ```

---

## Phase Summary

| Phase | Items | Critical Gaps Addressed | Estimated Duration |
|---|---|---|---|
| **Phase 1: Quick Wins** | 10 items | Security (credentials, validation, leakage), Resilience (timeouts, retry) | 1-2 weeks |
| **Phase 2: Important** | 10 items | Resilience (circuit breakers, transactions), Testing (unit + integration), Code Organization (shared library) | 3-6 weeks |
| **Phase 3: Polish** | 9 items | Testing (contracts), Resilience (fallbacks, bulkheads), API Design, Observability | 6-12 weeks |

### Recommended Execution Order Within Each Phase

**Phase 1** (in order of priority):
1. Fix exception leakage (1.1) + Fix HTTP status codes (1.4) — can be combined into one PR
2. Remove sensitive data from logs (1.2)
3. Add input validation (1.3)
4. Configure Feign timeouts (1.5) + retry (1.6) — can be combined
5. Fix OpenAPI starter (1.7)
6. Add Prometheus metrics (1.8)
7. Add ResponseEntity generics (1.9) + JaCoCo (1.10) — low priority, can be batched

**Phase 2** (in order of priority):
1. Fix available balance bug + transaction consistency (2.2) — correctness issue
2. Externalize secrets (2.7)
3. Add circuit breakers (2.1)
4. Add unit tests (2.5)
5. Extract shared library (2.3) + unify Gradle build (2.4)
6. Add integration tests (2.6)
7. Fix Keycloak singleton (2.10)
8. Add rate limiting (2.8) + structured logging (2.9)

**Phase 3** — order is flexible; prioritize based on team preference.
