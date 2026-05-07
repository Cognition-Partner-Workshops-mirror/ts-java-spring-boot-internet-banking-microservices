# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are organized into three phases based on risk reduction, dependency order, and effort. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort items that immediately reduce risk and improve code quality.

### 1.1 Fix Error Handling — Proper HTTP Status Codes

**Gap Refs:** 2.1, 2.2, 2.3
**Severity:** Critical | **Effort:** Small

Standardize `GlobalExceptionHandler` across all four services to return appropriate HTTP status codes and a consistent `ErrorResponse` structure. Stop leaking stack traces.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler in all four
services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to:

1. Return 404 for EntityNotFoundException
2. Return 422 for InsufficientFundsException
3. Return 409 for UserAlreadyRegisteredException
4. Return 400 for InvalidEmailException and InvalidBankingUserException
5. Return 500 for the catch-all Exception handler
6. Always return the structured ErrorResponse { code, message } — never a raw string
7. Never include the exception's toString() or stack trace in the response body
8. Add type parameters to all ResponseEntity return types (e.g., ResponseEntity<ErrorResponse>)

Make sure all four services have identical exception handling behavior. Run tests after changes.
```

---

### 1.2 Add Input Validation to All DTOs

**Gap Ref:** 4.2
**Severity:** Critical | **Effort:** Medium

Add Jakarta Bean Validation annotations to all request DTOs and enable `@Valid` on controller methods.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation to all request DTOs:

1. Add spring-boot-starter-validation to each service's build.gradle
2. Core Banking FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
3. Core Banking UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
4. User Service User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
5. Fund Transfer FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
6. Utility Payment UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
7. Add @Valid to all @RequestBody parameters in controllers
8. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details
9. Run all tests to verify nothing breaks
```

---

### 1.3 Secure the User DTO — Exclude Password from Responses

**Gap Ref:** 4.6
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, add
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field in
com.javatodev.finance.model.dto.User so that passwords are never serialized in API responses.
Add the necessary Jackson import. Run tests.
```

---

### 1.4 Fix Sensitive Data Logging

**Gap Ref:** 6.5
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, audit all log statements across all services
and fix sensitive data exposure:

1. In UserController (user-service): do not log the full User object (contains password). Log only email.
2. In FundTransferController and FundTransferService: do not log full request objects. Log only fromAccount and toAccount.
3. In UtilityPaymentService: do not log full request. Log only account and providerId.
4. Fix the string concatenation in FundTransferService: change log.info("Sending fund transfer request {}" + request.toString()) to use SLF4J placeholder.
5. Fix typo "utitlity" → "utility" in AccountController.
```

---

### 1.5 Fix Feign Timeout and Retry Configuration

**Gap Refs:** 7.2, 7.3
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign timeout configuration to all three
Feign-client services (user-service, fund-transfer-service, utility-payment-service):

1. In each service's application.yml, add:
   spring.cloud.openfeign.client.config.default.connect-timeout: 5000
   spring.cloud.openfeign.client.config.default.read-timeout: 10000
2. Add spring-retry dependency to each build.gradle
3. Configure Feign retry: max 3 attempts with 1-second initial backoff
4. Ensure @EnableRetry is present on application classes if using Spring Retry
```

---

### 1.6 Scope Database Permissions

**Gap Ref:** 4.4
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/docker-compose/mysql/privileges.sql, replace the
overly broad GRANT statement with per-database grants:

1. Grant only INSERT, UPDATE, DELETE, SELECT on banking_core_service.* to javatodev_development
2. Grant only INSERT, UPDATE, DELETE, SELECT on banking_core_fund_transfer_service.* to javatodev_development
3. Grant only INSERT, UPDATE, DELETE, SELECT on banking_core_user_service.* to javatodev_development
4. Grant only INSERT, UPDATE, DELETE, SELECT on banking_core_utility_payment_service.* to javatodev_development
5. Keep CREATE, ALTER for the core-banking-service database only (Flyway needs DDL)
6. Remove DROP and REFERENCES from all grants
```

---

### 1.7 Fix OpenAPI Dependency Mismatch

**Gap Ref:** 5.5
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, change the springdoc dependency in
core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and
internet-banking-utility-payment-service from:
  springdoc-openapi-starter-webflux-ui:2.1.0
to:
  springdoc-openapi-starter-webmvc-ui:2.1.0

These services use Spring MVC (servlet), not WebFlux. Only the API Gateway uses WebFlux.
Run gradle build in each service to verify.
```

---

### 1.8 Add Pagination Metadata to List Responses

**Gap Ref:** 5.3
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update all list endpoints to return pagination
metadata instead of raw lists:

1. Create a generic PageResponse<T> class in each service (or a shared module) with fields:
   content: List<T>, totalElements: long, totalPages: int, currentPage: int, pageSize: int
2. Update UserController.readUsers (both core and user-service), FundTransferController.readFundTransfers,
   and UtilityPaymentController.readPayments to return PageResponse instead of List
3. Map from Spring's Page<T> to PageResponse<T> in each service method
```

---

### 1.9 Fix Keycloak Singleton Thread Safety

**Gap Ref:** 4.5
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, fix the
thread-safety issue in KeycloakProperties.getInstance():

1. Make the keycloakInstance field volatile
2. Use double-checked locking with synchronized block
3. Alternatively, refactor to use a @Bean method in a @Configuration class that returns a
   singleton Keycloak instance managed by Spring's container
```

---

### 1.10 Fix Raw ResponseEntity Types

**Gap Ref:** 2.5
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add generic type parameters to all controller
method return types. For example, change:
  public ResponseEntity getBankAccount(...)
to:
  public ResponseEntity<BankAccount> getBankAccount(...)

Do this for every controller in core-banking-service, internet-banking-fund-transfer-service,
and internet-banking-utility-payment-service. The user-service controllers already have type params.
```

---

## Phase 2: Important Improvements (3-6 weeks)

Structural changes that significantly improve reliability, maintainability, and security.

### 2.1 Add Circuit Breakers to All Feign Clients

**Gap Ref:** 7.1
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers:

1. Add spring-cloud-starter-circuitbreaker-resilience4j to build.gradle for:
   internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service
2. Enable circuit breaker with Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback in fund-transfer-service
   - BankingCoreRestClientFallback in user-service and utility-payment-service
4. In fallbacks: log the error, return a meaningful error response or throw a custom exception
5. Configure circuit breaker properties:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 5
6. For fund-transfer and utility-payment: when core banking is unavailable, mark the local
   record as FAILED instead of leaving it in PENDING/PROCESSING
```

---

### 2.2 Add Optimistic Locking to Account Balances

**Gap Ref:** 7.5
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/core-banking-service:

1. Add a @Version field (Long version) to BankAccountEntity
2. Add a Flyway migration to add a 'version' column (BIGINT DEFAULT 0) to banking_core_account
3. Fix the balance calculation bug in TransactionService:
   - internalFundTransfer: availableBalance should equal actualBalance after the transfer, not actualBalance minus amount again
   - utilPayment: same bug — availableBalance is double-subtracted
4. Add an OptimisticLockException handler to GlobalExceptionHandler that returns 409 Conflict
   with a message like "Concurrent modification detected, please retry"
5. Add unit tests for concurrent transfer scenarios
```

---

### 2.3 Create Shared Library Module

**Gap Ref:** 1.2
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a shared library to eliminate code
duplication:

1. Create a new Gradle subproject: banking-common-lib
2. Move these shared classes into it:
   - AuditAware, BaseMapper, ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler (base class)
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - CustomFeignClientConfiguration
   - TransactionStatus enum
3. Create a root settings.gradle that includes all services as subprojects
4. Create a root build.gradle with shared configuration (Java 21, Spring Boot 3.2.4, Spring Cloud version)
5. Update each service's build.gradle to depend on banking-common-lib
6. Remove the duplicated classes from each service
7. Run all tests across all services to verify
```

---

### 2.4 Add Unit Tests for User, Fund Transfer, and Utility Payment Services

**Gap Ref:** 3.1
**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests:

For internet-banking-user-service:
- Test UserService.createUser: happy path, duplicate email, user not in core, email mismatch, Keycloak failure
- Test UserService.updateUser: approve flow (Keycloak enabled), user not found
- Test UserService.readUsers: pagination, Keycloak enrichment
- Test KeycloakUserService: create, read, update, readByEmail

For internet-banking-fund-transfer-service:
- Test FundTransferService.fundTransfer: happy path, core banking failure, save + status update
- Test FundTransferService.readAllTransfers: pagination

For internet-banking-utility-payment-service:
- Test UtilityPaymentService.utilPayment: happy path, core banking failure, save + status update
- Test UtilityPaymentService.readPayments: pagination

Use Mockito to mock Feign clients and repositories. Each test class should have @BeforeEach setup
similar to the existing core-banking-service tests.
```

---

### 2.5 Add Feign Error Handling to Fund Transfer and Utility Payment

**Gap Ref:** 2.4
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
   internet-banking-utility-payment-service (user-service already has one)
2. The decoder should:
   - Parse the ErrorResponse JSON from the core-banking-service response body
   - Throw appropriate exceptions: EntityNotFoundException for 404, InsufficientFundsException for 422,
     SimpleBankingGlobalException for other 4xx, and a new CoreBankingUnavailableException for 5xx
3. Register the decoder in CustomFeignClientConfiguration
4. In FundTransferService.fundTransfer: catch exceptions from the Feign call and update the local
   entity status to FAILED before rethrowing
5. Same for UtilityPaymentService.utilPayment
6. Add unit tests for the error decoder and the failure handling paths
```

---

### 2.6 Secure Downstream Services

**Gap Ref:** 4.7
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add authentication to downstream services:

Option A (simpler — shared secret):
1. Add a shared API key that the gateway injects and downstream services validate
2. Create a filter in each downstream service that checks for X-Internal-Api-Key header
3. Reject requests without the valid key with 401

Option B (recommended — JWT propagation):
1. Add spring-boot-starter-oauth2-resource-server to each downstream service's build.gradle
2. Configure each service as a JWT resource server validating against Keycloak's JWK set
3. The gateway already forwards the Authorization header; downstream services validate it independently
4. Remove the X-Auth-Id header approach and extract the principal from the JWT directly

Implement Option B. Update the AppAuthUserFilter to extract the user ID from the validated JWT
instead of trusting the X-Auth-Id header.
```

---

### 2.7 Add Integration Tests with Testcontainers

**Gap Ref:** 3.2
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers:

1. Add testcontainers and testcontainers-mysql dependencies to each service's build.gradle
2. For core-banking-service: create @SpringBootTest integration tests that:
   - Start a MySQL Testcontainer
   - Run Flyway migrations
   - Test the full REST API via MockMvc (create accounts, make transfers, verify balances)
3. For internet-banking-user-service: create tests with WireMock for core banking Feign client
   and Testcontainers for MySQL
4. For fund-transfer and utility-payment: same pattern with WireMock for core banking
5. Use @DynamicPropertySource to inject container URLs
6. Verify that H2 test configs are still available for fast unit test execution
```

---

### 2.8 Add Rate Limiting at the API Gateway

**Gap Ref:** 7.6
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-api-gateway:

1. Add spring-boot-starter-data-redis-reactive dependency
2. Add a Redis container to docker-compose.yml
3. Configure Spring Cloud Gateway's built-in RequestRateLimiter filter:
   - Fund transfer endpoints: 10 requests per second per user
   - Utility payment endpoints: 10 requests per second per user
   - User endpoints: 30 requests per second per user
4. Use the JWT subject claim as the rate limit key
5. Return 429 Too Many Requests when limit exceeded
```

---

### 2.9 Improve RESTful URL Design

**Gap Ref:** 5.1
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, refactor
endpoint URLs to follow RESTful conventions:

1. Change POST /api/v1/bank-users/register → POST /api/v1/bank-users
2. Change PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
3. Update the API Gateway security configuration to permit the new registration path
4. Update any Postman collection references if present
5. Add @Deprecated annotations to old endpoints temporarily for backward compatibility,
   mapping them to the new handlers
```

---

## Phase 3: Polish & Maturity (6-12 weeks)

Improvements that bring the system to production-grade maturity.

### 3.1 Set Up Multi-Project Gradle Build

**Gap Ref:** 1.1
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, convert to a Gradle multi-project build:

1. Create a root settings.gradle that includes all service subprojects
2. Create a root build.gradle with:
   - Common plugins (Java 21, Spring Boot 3.2.4, dependency management)
   - Shared dependency versions in ext block
   - Common test configuration
3. Simplify each service's build.gradle to only declare service-specific dependencies
4. Remove duplicate gradle wrapper directories from subprojects (keep only root)
5. Verify ./gradlew build from root compiles and tests all services
6. Update Dockerfiles if JAR paths change
```

---

### 3.2 Add Contract Tests Between Services

**Gap Ref:** 3.3
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests:

1. Add spring-cloud-starter-contract-verifier to core-banking-service
2. Define contracts for all core banking endpoints consumed by other services:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}
3. Generate stubs JAR from core-banking-service
4. Add spring-cloud-starter-contract-stub-runner to consumer services
5. Write consumer-side contract tests in fund-transfer and utility-payment services
   that verify their Feign clients work against the generated stubs
6. Add the contract verification to the CI pipeline
```

---

### 3.3 Configure Prometheus Metrics

**Gap Ref:** 6.3
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. Add micrometer-registry-prometheus dependency to all services' build.gradle
2. Add to each service's application.yml:
   management.endpoints.web.exposure.include: health,info,prometheus
   management.metrics.tags.application: ${spring.application.name}
3. Add a Prometheus container to docker-compose.yml with a prometheus.yml config
   that scrapes all service /actuator/prometheus endpoints
4. Optionally add a Grafana container with a pre-built dashboard for Spring Boot metrics
5. Update the gateway security config to permit /actuator/prometheus endpoints
```

---

### 3.4 Implement Structured JSON Logging

**Gap Ref:** 6.1
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, configure structured JSON logging:

1. Add logstash-logback-encoder dependency to all services
2. Create a logback-spring.xml in each service's resources folder that:
   - Uses LogstashEncoder for JSON output in the docker profile
   - Keeps console pattern output for the default/dev profile
3. Include trace ID and span ID in log output using MDC
4. Add custom fields: service name, environment
```

---

### 3.5 Add Custom Health Checks

**Gap Ref:** 6.2
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. In internet-banking-user-service: add a custom HealthIndicator that checks Keycloak reachability
   by calling keycloakManager.getKeyCloakInstanceWithRealm().toRepresentation()
2. In fund-transfer and utility-payment services: add a custom HealthIndicator that checks
   core-banking-service availability by calling the Feign client's readAccount with a known test account
3. Ensure all health details are exposed: management.endpoint.health.show-details=always
4. Add the database health indicator (auto-configured by Spring Boot Actuator + JPA)
```

---

### 3.6 Add a Standard Response Envelope

**Gap Ref:** 5.6
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, implement a consistent response envelope:

1. Create an ApiResponse<T> class in the shared library:
   { success: boolean, data: T, error: ErrorResponse, meta: Map<String, Object> }
2. Create a ResponseEntityAdvice (@ControllerAdvice implementing ResponseBodyAdvice)
   that automatically wraps all responses in ApiResponse
3. Update GlobalExceptionHandler to return ApiResponse with success=false
4. Ensure Swagger documentation reflects the envelope structure
5. Update all existing tests to account for the new response format
```

---

### 3.7 Implement Fallback and Reconciliation for Orphaned Records

**Gap Ref:** 7.4
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. In fund-transfer-service: add a @Scheduled reconciliation job that:
   - Queries for FundTransferEntity records with status=PENDING older than 5 minutes
   - Attempts to check the transaction status with core-banking-service
   - Updates records to SUCCESS or FAILED based on the response
   - If core banking is unavailable, logs and retries on next schedule
2. Same for utility-payment-service with status=PROCESSING records
3. Configure schedule: run every 2 minutes
4. Add a management endpoint to trigger reconciliation manually
5. Add unit tests for the reconciliation logic
```

---

### 3.8 Externalize Secrets with Docker Secrets or Vault

**Gap Ref:** 4.1
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, externalize hardcoded credentials:

1. Replace hardcoded passwords in docker-compose.yml with environment variable references:
   MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
   KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
   KC_DB_PASSWORD: ${KC_DB_PASSWORD}
2. Create a .env.example file with placeholder values
3. Add .env to .gitignore
4. Update mysql/privileges.sql to use an environment variable for the DB user password
5. Remove test credentials from README.md (move to .env.example)
6. Add documentation for setting up secrets for development
```

---

### 3.9 Complete Distributed Tracing Configuration

**Gap Ref:** 6.4
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. Set management.tracing.sampling.probability=1.0 in all services' application.yml
   (100% sampling for development; production should use 0.1 or adaptive sampling)
2. Add @Observed annotations to key business methods:
   - TransactionService.fundTransfer, utilPayment
   - UserService.createUser, updateUser
   - FundTransferService.fundTransfer
   - UtilityPaymentService.utilPayment
3. Configure custom span names for Feign client calls
4. Verify trace propagation end-to-end by reviewing Zipkin UI
```

---

## Phase Summary

| Phase | Items | Critical Gaps Addressed | Estimated Effort |
|---|---|---|---|
| **Phase 1: Quick Wins** | 10 items | 2 Critical, 6 High, 2 Medium | 1-2 weeks |
| **Phase 2: Important** | 9 items | 3 Critical, 4 High, 2 Medium | 3-6 weeks |
| **Phase 3: Polish** | 9 items | 1 Critical, 2 High, 6 Medium/Low | 6-12 weeks |

### Recommended Execution Order Within Each Phase

**Phase 1 priority order:**
1. Error handling fixes (1.1) — immediate safety improvement
2. Input validation (1.2) — prevents invalid data entering the system
3. Password field security (1.3) — prevents credential leakage
4. Sensitive data logging (1.4) — stops PII exposure in logs
5. Feign timeouts (1.5) — prevents thread exhaustion
6. Database permissions (1.6) — reduces blast radius
7. Remaining items (1.7-1.10) — quality improvements

**Phase 2 priority order:**
1. Circuit breakers (2.1) — prevents cascade failures
2. Optimistic locking (2.2) — prevents data corruption
3. Feign error handling (2.5) — proper failure management
4. Downstream auth (2.6) — closes security hole
5. Unit tests (2.4) — safety net for all changes
6. Shared library (2.3) — reduces maintenance burden
7. Remaining items (2.7-2.9) — maturity improvements

**Phase 3 priority order:**
1. Externalize secrets (3.8) — last Critical gap
2. Reconciliation jobs (3.7) — data consistency
3. Contract tests (3.2) — cross-service safety
4. Remaining items — operational excellence
