# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

Items that are high-impact with small effort, or critical security fixes that must be addressed immediately.

### 1.1 Add Input Validation to All Request DTOs

**Gap:** 4.2 - No input validation | **Severity:** Critical | **Effort:** Medium

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across all services in
ts-java-spring-boot-internet-banking-microservices. Specifically:

1. Add spring-boot-starter-validation to each service's build.gradle
2. Add @NotNull, @NotBlank, @Min, @Positive, @Size annotations to all request DTO fields:
   - FundTransferRequest: fromAccount (@NotBlank), toAccount (@NotBlank), amount (@NotNull @Positive)
   - UtilityPaymentRequest: providerId (@NotNull), amount (@NotNull @Positive), referenceNumber (@NotBlank), account (@NotBlank)
   - User (registration): email (@NotBlank @Email), identification (@NotBlank), password (@NotBlank @Size(min=8))
   - UserUpdateRequest: status (@NotNull)
3. Add @Valid annotation to all @RequestBody parameters in controllers
4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns
   a structured ErrorResponse with field-level validation messages and HTTP 422
5. Run existing tests to ensure nothing breaks
```

### 1.2 Fix HTTP Status Codes in Error Handlers

**Gap:** 2.2 - All errors return HTTP 400 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Fix HTTP status codes in GlobalExceptionHandler across all 4 business services in
ts-java-spring-boot-internet-banking-microservices. Currently everything returns 400.

1. EntityNotFoundException -> return 404 Not Found
2. InsufficientFundsException -> return 422 Unprocessable Entity
3. UserAlreadyRegisteredException -> return 409 Conflict
4. InvalidEmailException -> return 422 Unprocessable Entity
5. InvalidBankingUserException -> return 404 Not Found
6. Generic Exception.class -> return 500 Internal Server Error (and do NOT include the
   exception message or stack trace in the response body - log it instead)
7. Fix the catch-all handler to return a structured ErrorResponse instead of a raw string
8. Update or add unit tests for the exception handlers
```

### 1.3 Remove Password from User DTO Response

**Gap:** 4.6 - Password in User DTO response | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service, the User DTO class includes a password field that
could be returned in API responses. Fix this by:

1. Add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field
   in com.javatodev.finance.model.dto.User
2. Alternatively, consider creating separate UserRegistrationRequest and UserResponse DTOs
   to fully separate request and response shapes
3. Ensure existing tests still pass
```

### 1.4 Fix OpenAPI Starter Dependency

**Gap:** 5.5 - Wrong OpenAPI starter | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, the business services
(core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) use springdoc-openapi-starter-webflux-ui but they
are Spring MVC applications, not WebFlux.

1. Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with
   'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' in all 4 business service
   build.gradle files
2. Update the version to the latest 2.x release
3. Verify Swagger UI loads correctly at /swagger-ui.html for each service
```

### 1.5 Add Type Parameters to ResponseEntity

**Gap:** 5.1 - Raw ResponseEntity | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, most controller methods return
untyped ResponseEntity instead of ResponseEntity<T>. Fix all controllers:

1. core-banking-service AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
2. core-banking-service TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
3. core-banking-service UserController: ResponseEntity<User>, ResponseEntity<List<User>>
4. fund-transfer FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
5. utility-payment UtilityPaymentController: ResponseEntity<UtilityPaymentResponse>, ResponseEntity<List<UtilityPayment>>

This enables proper OpenAPI schema generation and compile-time type safety.
```

### 1.6 Return Pagination Metadata

**Gap:** 5.4 - No pagination metadata | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, all paginated GET endpoints
return List<T> instead of Page<T>. Fix these endpoints to return the full Spring
Page response which includes totalElements, totalPages, number, size:

1. core-banking-service UserController.readUsers() - return ResponseEntity<Page<User>>
2. internet-banking-user-service UserController.readUsers() - return ResponseEntity<Page<User>>
3. internet-banking-fund-transfer-service FundTransferController.readFundTransfers() - return ResponseEntity<Page<FundTransfer>>
4. internet-banking-utility-payment-service UtilityPaymentController.readPayments() - return ResponseEntity<Page<UtilityPayment>>

Update the corresponding service methods to return Page<T> instead of List<T>.
```

### 1.7 Fix Logging Inconsistencies

**Gap:** 6.1 - Logging inconsistency | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix logging inconsistencies across all services in
ts-java-spring-boot-internet-banking-microservices:

1. Ensure all controllers and services use @Slf4j consistently (add where missing,
   remove manual LoggerFactory imports in KeycloakUserService)
2. Remove logging of sensitive data: do not log full request bodies that may contain
   passwords or account numbers. Log only non-sensitive identifiers
3. Add WARN-level logging for business rule violations (insufficient funds, duplicate email)
4. Add ERROR-level logging in catch blocks that handle unexpected exceptions
5. Ensure the catch-all GlobalExceptionHandler logs the full exception at ERROR level
   (but does NOT return it to the client)
```

### 1.8 Fix Keycloak Singleton Thread Safety

**Gap:** 4.5 - Keycloak singleton anti-pattern | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service, KeycloakProperties uses a non-thread-safe lazy
singleton for the Keycloak client. Refactor this:

1. Remove the static Keycloak field from KeycloakProperties
2. Create a @Bean method in a @Configuration class that produces a singleton Keycloak instance
   using KeycloakBuilder, injecting properties via @Value
3. Inject the Keycloak bean into KeycloakManager instead of calling keycloakProperties.getInstance()
4. This ensures thread-safe initialization via Spring's singleton scope and proper testability
```

### 1.9 Add Missing Error Codes

**Gap:** 2.4 - Missing error codes | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, only core-banking-service and
internet-banking-user-service define GlobalErrorCode constants. Add GlobalErrorCode
classes to:

1. internet-banking-fund-transfer-service with codes like:
   - FUND_TRANSFER_SERVICE_1000 (entity not found)
   - FUND_TRANSFER_SERVICE_1001 (transfer failed)
2. internet-banking-utility-payment-service with codes like:
   - UTILITY_PAYMENT_SERVICE_1000 (entity not found)
   - UTILITY_PAYMENT_SERVICE_1001 (payment failed)

Use these codes in the respective SimpleBankingGlobalException instances.
```

### 1.10 Add Custom Health Indicators

**Gap:** 6.2 - No custom health checks | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom Spring Boot health indicators to services in
ts-java-spring-boot-internet-banking-microservices:

1. core-banking-service: Add a DatabaseHealthIndicator that verifies MySQL connectivity
2. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak
   server URL
3. internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
   Add a CoreBankingHealthIndicator that calls the core-banking-service actuator health endpoint
4. Ensure actuator health endpoint is enabled in all services
   (management.endpoints.web.exposure.include=health,info,metrics)
```

---

## Phase 2: Important Improvements (3-6 weeks)

Items that are important for production readiness but require more significant engineering effort.

### 2.1 Implement Circuit Breakers

**Gap:** 7.1 - No circuit breakers | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls in
ts-java-spring-boot-internet-banking-microservices:

1. Add spring-cloud-starter-circuitbreaker-resilience4j to build.gradle for:
   - internet-banking-user-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service
2. Configure circuit breaker defaults in each service's configuration:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 5
3. Add @CircuitBreaker annotations to Feign client methods with fallback methods
4. Implement fallback methods that return appropriate error responses
5. Add circuit breaker actuator endpoints for monitoring
```

### 2.2 Add Retry and Timeout Configuration

**Gap:** 7.2, 7.3 - No retry/timeout | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Configure retry policies and timeouts for all inter-service communication in
ts-java-spring-boot-internet-banking-microservices:

1. Add Resilience4j retry configuration to all Feign clients:
   - maxAttempts: 3
   - waitDuration: 500ms
   - retryOnExceptions: IOException, FeignException.ServiceUnavailable
   - Do NOT retry on 4xx client errors
2. Configure Feign client timeouts in each service:
   - connectTimeout: 5000ms
   - readTimeout: 10000ms
3. Configure Spring Cloud Gateway timeouts:
   - connect-timeout: 5000ms
   - response-timeout: 30s
4. Add connection pool configuration for MySQL datasources
5. Add Keycloak client timeout configuration in User Service
```

### 2.3 Add Feign Error Decoder to Fund Transfer Service

**Gap:** 2.3 - No Feign error handling | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
The internet-banking-fund-transfer-service has a CustomFeignClientConfiguration but no
CustomFeignErrorDecoder like the user-service has. Add one:

1. Create CustomFeignErrorDecoder in internet-banking-fund-transfer-service that:
   - Decodes the ErrorResponse body from Core Banking error responses
   - Maps 404 -> EntityNotFoundException
   - Maps 422 -> SimpleBankingGlobalException with the error code from the response
   - Maps 5xx -> a retriable exception
2. Register it in CustomFeignClientConfiguration
3. Do the same for internet-banking-utility-payment-service
4. Add unit tests for the error decoders
```

### 2.4 Write Unit Tests for All Services

**Gap:** 3.1 - Tests only in Core Banking | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests to all services in
ts-java-spring-boot-internet-banking-microservices that currently lack them:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid email, user not found),
     readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: test createUser, readUser, readUserByEmail, updateUser with
     mocked KeycloakManager
   - UserControllerTest: test all endpoints with MockMvc

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (success, core banking error), readAllTransfers
   - FundTransferControllerTest: test endpoints with MockMvc

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (success, core banking error), readPayments
   - UtilityPaymentControllerTest: test endpoints with MockMvc

Use Mockito for mocking, JUnit 5, and follow the patterns already established
in core-banking-service tests.
```

### 2.5 Externalize Secrets from Docker Compose

**Gap:** 4.1 - Hardcoded credentials | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Remove all hardcoded credentials from version-controlled files in
ts-java-spring-boot-internet-banking-microservices:

1. Create a docker-compose/.env.example file documenting all required environment variables
2. Update docker-compose.yml to use environment variable substitution:
   - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
   - KC_DB_PASSWORD=${KC_DB_PASSWORD}
   - KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
3. Update mysql/privileges.sql to use environment variables or move to an
   entrypoint script that reads from environment
4. Add docker-compose/.env to .gitignore
5. Remove the test credentials from README.md and reference the .env.example instead
6. Document the setup process for new developers
```

### 2.6 Implement Authorization in Downstream Services

**Gap:** 4.3 - No authorization beyond gateway | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add Spring Security to downstream business services in
ts-java-spring-boot-internet-banking-microservices so they are not unprotected
when accessed directly:

1. Add spring-boot-starter-security to each business service
2. Configure each service to validate the X-Auth-Id header and optionally
   validate JWT tokens directly (defense in depth)
3. Add role-based access control:
   - Admin endpoints (user approval, list all transfers): require ADMIN role
   - User endpoints (initiate transfer, make payment): require USER role
   - Read own data: validate that the authenticated user owns the resource
4. Configure the services to allow inter-service calls (service-to-service auth
   via a shared secret or JWT)
5. Update integration with the AppAuthUserFilter to work with Spring Security
6. Add tests for authorization rules
```

### 2.7 Create Root Gradle Build File

**Gap:** 1.4 - No root build file | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Create a multi-module Gradle build for ts-java-spring-boot-internet-banking-microservices:

1. Create a root settings.gradle that includes all 7 service modules
2. Create a root build.gradle that:
   - Defines common dependency versions in an ext block or version catalog
   - Applies common plugins (java, spring-boot, dependency-management) to subprojects
   - Sets common Java version (21) and group across all modules
3. Remove duplicated version declarations from individual build.gradle files
4. Verify that ./gradlew build from the root builds all services
5. Verify that ./gradlew test from the root runs all tests
```

### 2.8 Add Structured Logging

**Gap:** 6.5 - No structured logging | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add structured JSON logging to all services in
ts-java-spring-boot-internet-banking-microservices:

1. Add net.logstash.logback:logstash-logback-encoder to each service's build.gradle
2. Create a logback-spring.xml in each service's src/main/resources that:
   - Uses JsonEncoder for production profile
   - Uses standard console output for development profile
   - Includes trace ID and span ID in structured log output
3. Configure log levels appropriately:
   - com.javatodev.finance: DEBUG in dev, INFO in production
   - org.springframework: WARN
   - org.hibernate.SQL: DEBUG in dev only
```

### 2.9 Add Custom Metrics

**Gap:** 6.3 - No custom metrics | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add custom Micrometer metrics to business services in
ts-java-spring-boot-internet-banking-microservices:

1. core-banking-service:
   - Counter: banking.fund_transfers.total (tags: status=success|failed)
   - Counter: banking.utility_payments.total (tags: status=success|failed)
   - Gauge: banking.accounts.active.count
   - Timer: banking.transaction.duration

2. internet-banking-fund-transfer-service:
   - Counter: fund_transfer.requests.total (tags: status)
   - Timer: fund_transfer.core_banking.call.duration

3. internet-banking-utility-payment-service:
   - Counter: utility_payment.requests.total (tags: status)

4. Add Prometheus endpoint configuration to all services:
   management.endpoints.web.exposure.include=health,info,metrics,prometheus
5. Add micrometer-registry-prometheus dependency to all services
```

### 2.10 Add Rate Limiting to API Gateway

**Gap:** 7.6 - No rate limiting | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway in ts-java-spring-boot-internet-banking-microservices:

1. Add Spring Cloud Gateway's built-in RequestRateLimiter filter using Redis or
   in-memory token bucket
2. Configure rate limits:
   - Default: 100 requests per second per client
   - Fund transfer: 10 requests per second per client
   - User registration: 5 requests per minute per IP
3. Return HTTP 429 Too Many Requests with a Retry-After header when limits are exceeded
4. Add Redis to docker-compose.yml if using Redis-backed rate limiter
5. Add tests to verify rate limiting behavior
```

---

## Phase 3: Polish and Production Hardening (6-12 weeks)

Items that improve long-term maintainability and production resilience.

### 3.1 Extract Shared Library

**Gap:** 1.2 - No shared library | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Extract duplicated code into a shared library module in
ts-java-spring-boot-internet-banking-microservices:

1. Create a new module: banking-common-lib
2. Move these duplicated classes into the shared library:
   - AuditAware base class
   - BaseMapper<E, D> interface
   - AppAuthUserFilter + ApiRequestContext + ApiRequestContextHolder
   - ErrorResponse
   - SimpleBankingGlobalException + GlobalExceptionHandler base
   - TransactionStatus enum
   - GlobalErrorCode base constants
3. Publish the library as a Gradle module dependency
4. Update all services to depend on banking-common-lib instead of their local copies
5. Remove the duplicated classes from each service
6. Run all tests to verify nothing breaks
```

### 3.2 Add Integration Tests with Testcontainers

**Gap:** 3.2 - No integration tests | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for all services in
ts-java-spring-boot-internet-banking-microservices:

1. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter to test dependencies
2. For core-banking-service:
   - Test full API endpoints with a real MySQL container
   - Verify Flyway migrations run correctly
   - Test fund transfer and utility payment end-to-end within the service
3. For internet-banking-user-service:
   - Add org.testcontainers:keycloak for Keycloak testing
   - Test user registration flow with real Keycloak and MySQL
4. For internet-banking-fund-transfer-service:
   - Use WireMock to simulate Core Banking responses
   - Test the full fund transfer flow including error scenarios
5. For internet-banking-utility-payment-service:
   - Use WireMock to simulate Core Banking responses
   - Test the full utility payment flow
6. Create a base test class with shared Testcontainers configuration
```

### 3.3 Add Contract Tests

**Gap:** 3.3 - No contract tests | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services in
ts-java-spring-boot-internet-banking-microservices:

1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider)
2. Define contracts for all endpoints consumed by other services:
   - GET /api/v1/account/bank-account/{account_number}
   - GET /api/v1/user/{identification}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Generate contract stubs from core-banking-service
4. Add spring-cloud-starter-contract-stub-runner to consumer services
5. Write consumer-side contract tests in:
   - internet-banking-user-service (verify readUser contract)
   - internet-banking-fund-transfer-service (verify readAccount and fundTransfer contracts)
   - internet-banking-utility-payment-service (verify readAccount and utilPayment contracts)
```

### 3.4 Implement Saga Pattern for Fund Transfers

**Gap:** 7.5 - Non-atomic fund transfers | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Implement a saga pattern for the fund transfer flow in
ts-java-spring-boot-internet-banking-microservices to handle distributed
transaction failures:

1. Add a status field to track saga state: INITIATED -> CORE_BANKING_PROCESSING ->
   COMPLETED / FAILED / ROLLBACK_REQUIRED
2. Add idempotency keys to fund transfer requests to prevent duplicate processing
3. Implement compensation logic: if the Fund Transfer Service fails after Core
   Banking succeeds, trigger a reversal transaction
4. Add a scheduled job to detect and handle stuck transactions (e.g., PENDING for
   more than 5 minutes)
5. Consider using the transactional outbox pattern with a polling publisher
6. Add comprehensive tests for failure scenarios:
   - Core Banking succeeds but Fund Transfer DB write fails
   - Network timeout during Core Banking call
   - Duplicate request with same idempotency key
7. Apply the same pattern to the Utility Payment Service
```

### 3.5 Standardize Package Structure

**Gap:** 1.1 - Inconsistent package structure | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Standardize the package structure across all business services in
ts-java-spring-boot-internet-banking-microservices. Adopt this consistent layout:

com.javatodev.finance/
  configuration/
    audit/
    feign/
    filter/
    security/        (for Keycloak config in user service)
  controller/
  exception/
  model/
    dto/
      request/
      response/
    entity/
    mapper/
  repository/
  service/
    rest/

1. Move repository/ to a consistent location across all services
2. Move Feign clients to service/rest/ consistently
3. Ensure all services follow the exact same directory structure
4. Update all import statements
5. Run all tests to verify
```

### 3.6 Add Bulkhead and Thread Pool Isolation

**Gap:** 7.7 - No bulkhead isolation | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j bulkhead patterns to prevent thread pool exhaustion in
ts-java-spring-boot-internet-banking-microservices:

1. Configure separate thread pools for different Feign client operations:
   - Fund Transfer -> Core Banking: maxConcurrentCalls=10
   - Utility Payment -> Core Banking: maxConcurrentCalls=10
   - User Service -> Core Banking: maxConcurrentCalls=5
   - User Service -> Keycloak: maxConcurrentCalls=5
2. Add Resilience4j bulkhead configuration to application configs
3. Add @Bulkhead annotations to service methods
4. Configure bulkhead actuator endpoints for monitoring
5. Add tests to verify bulkhead behavior under load
```

### 3.7 Add Filtering and Search to List Endpoints

**Gap:** 5.6 - No filtering/search | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Add filtering and search capabilities to list endpoints in
ts-java-spring-boot-internet-banking-microservices:

1. core-banking-service:
   - GET /api/v1/user: add optional filters for email, firstName, lastName
   - GET /api/v1/account: add new endpoint to list accounts with filters for
     status, type, userId
2. internet-banking-user-service:
   - GET /api/v1/bank-users: add optional filters for status, identification
3. internet-banking-fund-transfer-service:
   - GET /api/v1/transfer: add optional filters for status, fromAccount, toAccount,
     date range
4. internet-banking-utility-payment-service:
   - GET /api/v1/utility-payment: add optional filters for status, providerId,
     account, date range
5. Use Spring Data JPA Specifications for dynamic query building
6. Add sorting support via Pageable parameters
```

### 3.8 Implement Fallback Behavior

**Gap:** 7.4 - No fallback behavior | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Implement fallback behavior for graceful degradation in
ts-java-spring-boot-internet-banking-microservices:

1. For read operations (account lookup, user lookup):
   - Add Spring Cache (Redis-backed) for account and user data
   - When Core Banking is unavailable, serve cached responses with a
     "data may be stale" warning header
2. For write operations (fund transfer, utility payment):
   - When Core Banking is unavailable, queue the request in a local database
     with status QUEUED
   - Implement a scheduled retry processor that processes queued requests
     when Core Banking comes back online
3. Add RabbitMQ to docker-compose.yml for async processing
4. Implement the Notification Service skeleton that consumes from RabbitMQ
5. Add Redis to docker-compose.yml for caching
```

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|-------|-------|---------------|-------------------|
| **Phase 1: Quick Wins** | 10 items | Input validation, error codes | 1-2 weeks |
| **Phase 2: Important** | 10 items | Circuit breakers, tests, secrets, auth | 3-6 weeks |
| **Phase 3: Polish** | 8 items | Saga pattern, contract tests, shared lib | 6-12 weeks |

### Priority Order Within Each Phase

**Phase 1** (do first to last):
1. Input validation (4.2) - prevents invalid data from entering the system
2. Fix HTTP status codes (2.2) - clients need correct error semantics
3. Remove password from response (4.6) - immediate data leak risk
4. Fix OpenAPI starter (5.5) - enables correct API documentation
5. Add ResponseEntity types (5.1) - improves API documentation
6. Return pagination metadata (5.4) - clients need this for proper pagination
7. Fix logging (6.1) - stop logging sensitive data
8. Fix Keycloak singleton (4.5) - thread safety
9. Add error codes (2.4) - error categorization
10. Add health indicators (6.2) - operational visibility

**Phase 2** (do first to last):
1. Circuit breakers (7.1) - prevents cascade failures
2. Retry/timeout config (7.2, 7.3) - basic resilience
3. Feign error decoder (2.3) - proper error propagation
4. Unit tests (3.1) - catch regressions
5. Externalize secrets (4.1) - security compliance
6. Authorization (4.3) - defense in depth
7. Root Gradle build (1.4) - developer experience
8. Structured logging (6.5) - production observability
9. Custom metrics (6.3) - production monitoring
10. Rate limiting (7.6) - abuse prevention

**Phase 3** (do first to last):
1. Saga pattern (7.5) - data consistency
2. Integration tests (3.2) - confidence in system behavior
3. Contract tests (3.3) - cross-service compatibility
4. Shared library (1.2) - reduce duplication
5. Package structure (1.1) - consistency
6. Bulkhead isolation (7.7) - thread pool safety
7. Filtering/search (5.6) - API completeness
8. Fallback behavior (7.4) - graceful degradation
