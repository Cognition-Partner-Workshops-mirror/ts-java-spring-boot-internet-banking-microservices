# Remediation Roadmap

This roadmap prioritizes the gaps identified in [`GAP_ANALYSIS.md`](./GAP_ANALYSIS.md) into three phases based on risk, effort, and dependency ordering.

---

## Phase 1: Quick Wins (1–2 Weeks)

High-impact, low-effort items that address critical risks and establish foundational quality.

### 1.1 Add Input Validation to All Request DTOs

**Gap:** 4.1 — No input validation | **Severity:** Critical | **Effort:** Small

Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size`) to all request DTOs and `@Valid` to controller parameters. Add `spring-boot-starter-validation` to each service's `build.gradle`.

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs in all services (FundTransferRequest, UtilityPaymentRequest, User DTO in user-service). Add @NotNull, @NotBlank, @Positive, @Email, @Size where appropriate. Add @Valid to all controller method parameters that accept request bodies. Add spring-boot-starter-validation to each service's build.gradle. Also add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns a structured ErrorResponse with field-level validation errors and HTTP 422 status.

---

### 1.2 Fix HTTP Status Codes in Error Handlers

**Gap:** 2.2 — All errors return 400 | **Severity:** High | **Effort:** Small

Map exceptions to proper HTTP status codes across all services.

**Devin Prompt:**
> Refactor the GlobalExceptionHandler in all services (core-banking, user, fund-transfer, utility-payment) to return correct HTTP status codes: EntityNotFoundException → 404, InsufficientFundsException → 422, UserAlreadyRegisteredException → 409, InvalidEmailException → 422, InvalidBankingUserException → 404, generic Exception → 500. Also fix the generic Exception handler to return a structured ErrorResponse JSON object (with code and message fields) instead of a plain string. Never expose raw exception details or stack traces.

---

### 1.3 Standardize Error Response Format

**Gap:** 2.1 — Inconsistent error response format | **Severity:** High | **Effort:** Small

Ensure all error handlers return the same JSON structure.

**Devin Prompt:**
> Standardize the ErrorResponse class across all services to include fields: code (String), message (String), timestamp (Instant), and path (String). Update all GlobalExceptionHandler methods to populate these fields consistently. The generic Exception handler in fund-transfer and core-banking services currently returns a raw string — fix it to return the same ErrorResponse structure. Extract the request path from HttpServletRequest where available.

---

### 1.4 Configure Feign Client Timeouts

**Gap:** 7.3 — No timeout configuration | **Severity:** High | **Effort:** Small

Prevent thread exhaustion from slow downstream services.

**Devin Prompt:**
> Add Feign client timeout configuration to the Spring Cloud Config repository (or application.yml if config server is not available) for all services that use Feign clients (user-service, fund-transfer-service, utility-payment-service). Set connectTimeout to 5000ms and readTimeout to 10000ms. Use the config path feign.client.config.default.connectTimeout and feign.client.config.default.readTimeout. Also add connection pool configuration for the database (spring.datasource.hikari.connectionTimeout=30000, maximumPoolSize=10).

---

### 1.5 Add Feign Error Handling in Fund Transfer and Utility Payment Services

**Gap:** 2.3 — No Feign failure handling | **Severity:** Critical | **Effort:** Small–Medium

Prevent silent failures and stuck PENDING/PROCESSING records.

**Devin Prompt:**
> In FundTransferService.fundTransfer(), wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch block. If a FeignException or any exception occurs, update the FundTransferEntity status to FAILED and re-throw a meaningful SimpleBankingGlobalException. Do the same in UtilityPaymentService.utilPayment() — wrap the bankingCoreRestClient.utilityPayment() call and set the entity status to FAILED on any exception. Also add a CustomFeignErrorDecoder to both fund-transfer-service and utility-payment-service (similar to the one in user-service) that translates upstream HTTP error responses into typed domain exceptions.

---

### 1.6 Fix Raw ResponseEntity Types

**Gap:** 5.1 — Raw ResponseEntity without type parameters | **Severity:** Medium | **Effort:** Small

Add generic type parameters for OpenAPI compatibility and type safety.

**Devin Prompt:**
> Update all controller methods across all services to use parameterized ResponseEntity<T> instead of raw ResponseEntity. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. For paginated endpoints, change the return type to ResponseEntity<List<T>> or ResponseEntity<Page<T>> as appropriate. This includes controllers in core-banking-service (AccountController, TransactionController, UserController), user-service (UserController), fund-transfer-service (FundTransferController), and utility-payment-service (UtilityPaymentController).

---

### 1.7 Fix Logging Bug and Standardize Logging

**Gap:** 6.1 — Inconsistent logging | **Severity:** Medium | **Effort:** Small

Fix the string concatenation bug and standardize.

**Devin Prompt:**
> Fix the logging bug in FundTransferService.fundTransfer() where string concatenation is used instead of SLF4J placeholders: change `log.info("Sending fund transfer request {}" + request.toString())` to `log.info("Sending fund transfer request {}", request)`. Review all log statements across all services and fix any similar concatenation issues. Ensure all service methods log at entry with request details and at exit with response/result details using SLF4J {} placeholders.

---

### 1.8 Fix OpenAPI Starter Dependency

**Gap:** 5.3 — Incomplete OpenAPI docs | **Severity:** Medium | **Effort:** Small

The wrong springdoc starter is used in servlet-based services.

**Devin Prompt:**
> In core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service build.gradle files, replace the dependency `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` since these are Spring MVC (servlet) based services, not WebFlux. Only the api-gateway should potentially use the webflux variant. Also add @Schema annotations with descriptions to all DTO fields in request/response classes.

---

### 1.9 Externalize Docker Compose Credentials

**Gap:** 4.2 — Hardcoded credentials | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Replace all hardcoded passwords in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml with environment variable references. Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD:-changeme}, the Keycloak admin password with ${KEYCLOAK_ADMIN_PASSWORD:-changeme}, the Keycloak DB password with ${KC_DB_PASSWORD:-changeme}, and the PostgreSQL password with ${POSTGRES_PASSWORD:-changeme}. Create a docker-compose/.env.example file listing all required environment variables with placeholder values. Add .env to .gitignore. Also update docker-compose/mysql/Dockerfile to use ARG/ENV instead of hardcoding the root password.

---

### 1.10 Add Dependency Vulnerability Scanning

**Gap:** 4.5 — No vulnerability scanning | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin to each service's build.gradle. Add `id 'org.owasp.dependencycheck' version '9.0.10'` to the plugins block. Configure it with `dependencyCheck { failBuildOnCVSS = 7; suppressionFile = "${rootDir}/owasp-suppressions.xml" }`. Create a shared owasp-suppressions.xml at the repo root for any known false positives. Also create a .github/dependabot.yml file configured to check Gradle dependencies weekly.

---

## Phase 2: Important Improvements (3–6 Weeks)

Structural improvements that require more design work and cross-service coordination.

### 2.1 Create Shared Common Library

**Gaps:** 1.1, 1.2 — No multi-project build, massive code duplication | **Severity:** High | **Effort:** Medium

Extract shared code into a common module.

**Devin Prompt:**
> Convert the repository to a Gradle multi-project build. Create a root settings.gradle that includes all 7 services plus a new internet-banking-common module. Create the common module with the following shared classes extracted from the existing services: BaseMapper, AuditAware, AuditConfig, AuditorAwareConfig, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, GlobalErrorCode, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter, and CustomFeignClientConfiguration. Update all services to depend on the common module via `implementation project(':internet-banking-common')`. Remove the duplicated classes from each service. Ensure all services still compile and their existing tests pass.

---

### 2.2 Add Circuit Breakers with Resilience4j

**Gap:** 7.1 — No circuit breakers | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Add spring-cloud-starter-circuitbreaker-resilience4j dependency to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Configure circuit breakers for all Feign clients using Resilience4j with the following settings: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3. Add @CircuitBreaker annotations to Feign client interfaces or configure via application.yml under resilience4j.circuitbreaker.instances. Implement fallback methods that return meaningful error responses (e.g., "Core Banking Service is temporarily unavailable, please try again later") and update the local entity status to FAILED.

---

### 2.3 Add Retry Policies

**Gap:** 7.2 — No retry policies | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add spring-retry dependency to fund-transfer-service, utility-payment-service, and user-service. Configure Resilience4j retry for Feign clients with maxAttempts=3, waitDuration=1s, exponentialBackoffMultiplier=2. Only retry on 5xx responses and connection exceptions, NOT on 4xx errors. Ensure fund transfer retries are safe by checking for existing transaction references before re-processing. Configure retry via application.yml under resilience4j.retry.instances.

---

### 2.4 Add Unit Tests for All Service Classes

**Gap:** 3.1 — Minimal test coverage | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> Add comprehensive unit tests for the following service classes using JUnit 5 and Mockito:
> 1. internet-banking-user-service: UserService (test createUser with all branches — success, email already registered, email mismatch, user not found; test readUsers, readUser, updateUser with APPROVED and other statuses), KeycloakUserService (mock KeycloakManager)
> 2. internet-banking-fund-transfer-service: FundTransferService (test fundTransfer success, Feign failure with FAILED status update, readAllTransfers)
> 3. internet-banking-utility-payment-service: UtilityPaymentService (test utilPayment success, Feign failure, readPayments)
>
> Also add src/test/resources/application.yml to each service that disables Eureka client (eureka.client.enabled=false), disables Config Server (spring.cloud.config.enabled=false), and uses H2 in-memory database. Ensure all context-load tests can run without external dependencies.

---

### 2.5 Add Pagination Metadata to List Endpoints

**Gap:** 5.4 — No pagination metadata | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Update all paginated GET endpoints across all services to return Page<T> instead of List<T>. In FundTransferService.readAllTransfers(), change the return type to Page<FundTransfer> and map the Page object preserving metadata. Do the same for UtilityPaymentService.readPayments(), UserService.readUsers() in both user-service and core-banking UserService.readUsers(). Update the corresponding controller return types to ResponseEntity<Page<T>>. The Page object from Spring Data already includes totalElements, totalPages, number, and size fields.

---

### 2.6 Configure Actuator and Health Checks

**Gaps:** 6.2, 6.3 — Actuator not configured, no custom health checks | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Configure Spring Boot Actuator for all services. In each service's application.yml (or config server configuration), add: management.endpoints.web.exposure.include=health,info,metrics,prometheus and management.endpoint.health.show-details=when-authorized. Add custom HealthIndicator beans: in user-service add a KeycloakHealthIndicator that pings the Keycloak server, in fund-transfer-service and utility-payment-service add a CoreBankingHealthIndicator that calls the core-banking actuator health endpoint via Feign. Add spring-boot-starter-actuator if not already present.

---

### 2.7 Add Role-Based Access Control

**Gap:** 4.3 — No downstream RBAC | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add method-level authorization to the internet-banking-user-service. The PATCH /api/v1/bank-users/{id} endpoint (user approval/status change) should require an ADMIN role. Add Spring Security as a dependency, configure it to extract roles from the X-Auth-Id header or from a JWT token passed through the gateway. Add @PreAuthorize("hasRole('ADMIN')") to the updateUser controller method. The POST /register endpoint should remain open. GET endpoints should require authentication. Document the role requirements in the OpenAPI annotations using @SecurityRequirement.

---

### 2.8 Fix Transaction Management in Core Banking

**Gap:** 7.6 — Transaction management gaps | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Review and fix the transaction management in core-banking-service TransactionService. The internalFundTransfer method should be atomic — ensure the debit and credit operations are within a single @Transactional method (it already is, but verify the propagation). In the FundTransferService (fund-transfer microservice), implement compensating logic: if the Feign call succeeds but the local entity update fails, log the inconsistency and trigger an alert. Add a scheduled job that scans for FundTransferEntity records stuck in PENDING status for more than 5 minutes and reconciles them against core banking. Do the same for UtilityPaymentEntity records stuck in PROCESSING.

---

### 2.9 Add Idempotency Keys for Financial Operations

**Gap:** 7.5 — No idempotency keys | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add idempotency key support to the fund transfer and utility payment flows. Add a new field `idempotencyKey` (String, unique) to FundTransferEntity and UtilityPaymentEntity. Update FundTransferRequest and UtilityPaymentRequest to include an optional `idempotencyKey` field. In FundTransferService.fundTransfer(), before processing, check if a record with the same idempotencyKey already exists — if so, return the existing result instead of processing again. Do the same in UtilityPaymentService.utilPayment(). Add a unique constraint on the idempotencyKey column. If no key is provided, generate a UUID as default.

---

## Phase 3: Polish and Production Readiness (6–10 Weeks)

Enhancements for long-term maintainability, developer experience, and production operations.

### 3.1 Add Integration Tests with Testcontainers

**Gap:** 3.3 — No integration/contract tests | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add integration tests using Testcontainers for the core-banking-service. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies. Create an integration test class CoreBankingIntegrationTest that starts a MySQL container, runs Flyway migrations, and tests the full flow: create a user via UserController, look up accounts via AccountController, perform a fund transfer via TransactionController, and verify balances are updated. Use @SpringBootTest with webEnvironment=RANDOM_PORT and TestRestTemplate. Also add Testcontainers-based tests for user-service, fund-transfer-service, and utility-payment-service.

---

### 3.2 Add Consumer-Driven Contract Tests

**Gap:** 3.3 — No contract tests | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add Spring Cloud Contract tests to verify the Feign client / provider compatibility between services. In core-banking-service (the provider), add spring-cloud-contract-verifier dependency and create contract DSL files for each endpoint consumed by other services: GET /api/v1/user/{identification}, GET /api/v1/account/bank-account/{account_number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment. Generate provider-side tests and stubs. In the consumer services (user-service, fund-transfer-service, utility-payment-service), add spring-cloud-contract-stub-runner and write tests that verify the Feign clients work against the generated stubs.

---

### 3.3 Implement Structured JSON Logging

**Gap:** 6.1 — Inconsistent logging | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add structured JSON logging to all services using logstash-logback-encoder. Add dependency `net.logstash.logback:logstash-logback-encoder:7.4` to each service's build.gradle. Create a logback-spring.xml in each service's src/main/resources that outputs JSON format in production profile and human-readable format in dev profile. Include fields: timestamp, level, logger, message, traceId, spanId, service name. Configure the Brave/Micrometer tracing to propagate trace IDs into MDC so they appear in log output.

---

### 3.4 Add Business Metrics with Micrometer

**Gap:** 6.4 — No business metrics | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add custom Micrometer metrics to track business operations. In fund-transfer-service, add a Counter for successful transfers (fund.transfer.success), failed transfers (fund.transfer.failed), and a Timer for transfer processing duration (fund.transfer.duration). In utility-payment-service, add similar counters and timers. In core-banking-service, add a Gauge for pending transactions count. Use @Timed annotation on service methods or inject MeterRegistry and record manually. Configure Prometheus endpoint exposure in actuator for scraping.

---

### 3.5 Standardize Package Structure Across Services

**Gap:** 1.3 — Inconsistent package structure | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the Java package structure across all services to follow this convention: com.javatodev.finance.controller/, com.javatodev.finance.service/, com.javatodev.finance.service.rest/ (for Feign clients), com.javatodev.finance.model.entity/, com.javatodev.finance.model.dto/, com.javatodev.finance.model.dto.request/, com.javatodev.finance.model.dto.response/, com.javatodev.finance.model.mapper/, com.javatodev.finance.repository/, com.javatodev.finance.configuration/, com.javatodev.finance.exception/. Move classes that are in non-standard locations (e.g., utility-payment's repository/ at top level instead of under model/). Ensure all imports are updated.

---

### 3.6 Make Mappers Spring-Managed or Use MapStruct

**Gap:** 1.4 — Mappers not Spring-managed | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Replace the manual BaseMapper implementations with MapStruct across all services. Add MapStruct dependency (org.mapstruct:mapstruct:1.5.5.Final) and annotation processor (org.mapstruct:mapstruct-processor:1.5.5.Final) to each service's build.gradle. Convert UserMapper, FundTransferMapper, UtilityPaymentMapper, BankAccountMapper, and UtilityAccountMapper to MapStruct interfaces with @Mapper(componentModel = "spring"). Inject them via @Autowired in service classes instead of instantiating with new. Remove the BaseMapper abstract class and all manual BeanUtils.copyProperties calls.

---

### 3.7 Implement RabbitMQ Notification Service

**Gap:** Architecture gap — Notification service referenced but not implemented

**Devin Prompt:**
> Create a new internet-banking-notification-service microservice. Add spring-boot-starter-amqp dependency. Configure RabbitMQ connection. Create a NotificationConsumer that listens to a fund-transfer-notifications queue and a utility-payment-notifications queue. In fund-transfer-service, after a successful transfer, publish a FundTransferNotification message to RabbitMQ with transfer details. Do the same in utility-payment-service. The notification service should log the notification for now (email/SMS integration can be added later). Add RabbitMQ to docker-compose.yml. Register the new service with Eureka.

---

### 3.8 Add Fallback Behavior and Caching

**Gap:** 7.4 — No fallback behavior | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add caching for frequently-read data in user-service and utility-payment-service. Add spring-boot-starter-cache dependency. Cache the core banking user lookup in UserService (used during registration) with a TTL of 5 minutes. Cache utility account lookups in UtilityPaymentService. Use @Cacheable annotations. Add circuit breaker fallback methods that return cached data when the core-banking-service is unavailable. For fund transfers, the fallback should queue the request for later processing rather than failing immediately.

---

### 3.9 Document API Versioning Strategy

**Gap:** 5.2 — No API versioning strategy | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Create a docs/API_VERSIONING.md document that describes the API versioning strategy: URL-based versioning with /api/v1/ prefix. Document the rules for when to create v2 (breaking changes to request/response schemas, removed fields, changed semantics). Document the deprecation policy (v1 supported for 6 months after v2 release). Add a note in each service's OpenAPI configuration that sets the API version info.

---

### 3.10 Add Filtering and Sorting to List Endpoints

**Gap:** 5.5 — No filtering/sorting documentation | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Add query parameter filtering to the list endpoints. In FundTransferController.readFundTransfers(), add optional @RequestParam filters for status (TransactionStatus), fromAccount, and toAccount. Implement Spring Data JPA Specification or QueryDSL predicates in FundTransferRepository. Do the same for UtilityPaymentController (filter by status, account, providerId) and UserController in core-banking (filter by email, identificationNumber). Document the supported filter parameters in OpenAPI annotations.

---

## Summary Timeline

```
Week 1-2: Phase 1 (Quick Wins)
├── 1.1  Input validation
├── 1.2  Fix HTTP status codes
├── 1.3  Standardize error responses
├── 1.4  Configure Feign timeouts
├── 1.5  Feign error handling
├── 1.6  Fix ResponseEntity types
├── 1.7  Fix logging
├── 1.8  Fix OpenAPI starter
├── 1.9  Externalize credentials
└── 1.10 Add vulnerability scanning

Week 3-8: Phase 2 (Important)
├── 2.1  Shared common library
├── 2.2  Circuit breakers
├── 2.3  Retry policies
├── 2.4  Unit tests
├── 2.5  Pagination metadata
├── 2.6  Actuator & health checks
├── 2.7  Role-based access control
├── 2.8  Fix transaction management
└── 2.9  Idempotency keys

Week 9-14: Phase 3 (Polish)
├── 3.1  Integration tests (Testcontainers)
├── 3.2  Contract tests
├── 3.3  Structured JSON logging
├── 3.4  Business metrics
├── 3.5  Standardize packages
├── 3.6  MapStruct migration
├── 3.7  Notification service (RabbitMQ)
├── 3.8  Fallback behavior & caching
├── 3.9  API versioning docs
└── 3.10 Filtering & sorting
```

## Critical Path

The following items form the critical dependency chain and should be prioritized within their phases:

1. **Phase 1:** Input validation (1.1) and Feign error handling (1.5) are the highest-risk items
2. **Phase 2:** Common library (2.1) should be done first as other Phase 2 items benefit from it. Circuit breakers (2.2) and transaction management (2.8) are the next highest priority
3. **Phase 3:** Unit tests from Phase 2 (2.4) should be complete before starting integration tests (3.1) and contract tests (3.2)
