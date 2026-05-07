# Remediation Roadmap

This document organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased remediation plan. Each phase is ordered by impact and risk, with estimated effort and sample Devin prompts for execution.

---

## Phase 1: Quick Wins (Critical Fixes & Low-Effort Improvements)

> **Goal:** Eliminate security vulnerabilities, data-integrity bugs, and information leaks. These items carry the highest risk and can be resolved quickly.

### 1.1 Fix Available Balance Calculation Bug

**Gap:** GAP-RES-007 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `utilPayment()`. After updating `actualBalance`, the code incorrectly subtracts `amount` again when setting `availableBalance`.

**Devin Prompt:**
```
In the core-banking-service, fix the available balance calculation bug in
TransactionService.java. In both internalFundTransfer() and utilPayment() methods,
after updating actualBalance, the availableBalance should be set equal to the new
actualBalance (not actualBalance minus amount again). The same fix is needed for
both the debit and credit sides of internalFundTransfer(). Update the existing
unit tests in TransactionServiceTest.java to assert correct balance values after
transfers. Open a PR with the fix.
```

### 1.2 Stop Leaking Exception Details to Clients

**Gap:** GAP-ERR-002 | **Severity:** Critical | **Effort:** Small

The generic `Exception` handler in all `GlobalExceptionHandler` classes returns raw exception details including stack traces.

**Devin Prompt:**
```
In all four services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service),
update the GlobalExceptionHandler's catch-all @ExceptionHandler(Exception.class)
method to return HTTP 500 with a generic ErrorResponse body containing
code="INTERNAL_ERROR" and message="An unexpected error occurred. Please try again
later." Do not include the exception message or stack trace in the response. Log the
full exception at ERROR level instead. Open a PR with the changes.
```

### 1.3 Add Input Validation to All Request DTOs

**Gap:** GAP-SEC-002 | **Severity:** Critical | **Effort:** Medium

No Jakarta Bean Validation annotations exist on any request DTO.

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. core-banking-service FundTransferRequest: @NotBlank fromAccount, @NotBlank
   toAccount, @NotNull @Positive amount
2. core-banking-service UtilityPaymentRequest: @NotNull providerId, @NotNull
   @Positive amount, @NotBlank referenceNumber, @NotBlank account
3. internet-banking-fund-transfer-service FundTransferRequest: same as above plus
   @NotBlank authID
4. internet-banking-utility-payment-service UtilityPaymentRequest: @NotNull
   providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank
   account
5. internet-banking-user-service User (for registration): @Email @NotBlank email,
   @NotBlank identification, @NotBlank @Size(min=8) password
6. internet-banking-user-service UserUpdateRequest: @NotNull status

Add @Valid to all @RequestBody parameters in controllers. Add the
spring-boot-starter-validation dependency to each service's build.gradle. Add a
MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns
HTTP 422 with field-level error details. Open a PR.
```

### 1.4 Fix Race Condition in Balance Updates

**Gap:** GAP-RES-006 | **Severity:** Critical | **Effort:** Medium

Concurrent fund transfers from the same account can cause lost updates and negative balances.

**Devin Prompt:**
```
In the core-banking-service, fix the race condition in TransactionService balance
updates. Add a @Version field (Long version) to BankAccountEntity for optimistic
locking. Also add @Transactional with isolation level SERIALIZABLE to the
fundTransfer and utilPayment methods, or alternatively use a pessimistic lock with
@Lock(LockModeType.PESSIMISTIC_WRITE) on the BankAccountRepository.findByNumber()
query. Add retry logic (Spring @Retryable or manual retry) to handle
OptimisticLockException. Write unit tests to verify that concurrent transfers
do not produce negative balances. Open a PR.
```

### 1.5 Externalize Hardcoded Credentials

**Gap:** GAP-SEC-001 | **Severity:** Critical | **Effort:** Small

Database passwords, Keycloak credentials, and other secrets are hardcoded in Docker Compose files.

**Devin Prompt:**
```
Replace all hardcoded credentials in docker-compose/docker-compose.yml and
docker-compose/docker-compose-support-apps.yml with environment variable references.
Create a docker-compose/.env.example file with placeholder values. Update the
README.md installation instructions to mention copying .env.example to .env and
setting real values. The following credentials need to be externalized:
MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD, POSTGRES_PASSWORD.
Also remove the test credentials from README.md and move them to the .env.example.
Open a PR.
```

### 1.6 Remove Sensitive Data from Log Statements

**Gap:** GAP-OBS-005 | **Severity:** High | **Effort:** Small

Controllers log full request objects that may include passwords and account numbers.

**Devin Prompt:**
```
In all services, audit every log.info/log.debug statement that logs request objects
via toString(). Replace them with safe logging that only includes non-sensitive
identifiers. Specifically:
- UserController (user-service): Do not log the full User object (contains password).
  Log only the email.
- FundTransferController: Log only fromAccount and toAccount, not the full request.
- UtilityPaymentController: Log only the providerId and account.
- TransactionController (core-banking): Log only fromAccount/toAccount for transfers,
  account for utility payments.
Open a PR.
```

### 1.7 Fix HTTP Status Codes in Error Handlers

**Gap:** GAP-ERR-001 | **Severity:** High | **Effort:** Medium

All exceptions return HTTP 400 regardless of the actual error type.

**Devin Prompt:**
```
In all four services with GlobalExceptionHandler, update the exception-to-HTTP-status
mapping:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity
- UserAlreadyRegisteredException → 409 Conflict
- InvalidEmailException → 422 Unprocessable Entity
- InvalidBankingUserException → 404 Not Found
- SimpleBankingGlobalException (generic) → 400 Bad Request (keep as-is)
- Exception (catch-all) → 500 Internal Server Error
Open a PR.
```

### 1.8 Fix Keycloak Singleton Thread Safety

**Gap:** GAP-SEC-005 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service, fix the thread-safety issue in
KeycloakProperties.getInstance(). Replace the non-synchronized lazy initialization
with either: (a) eager initialization in the field declaration, (b) a @Bean method
in a @Configuration class that returns a singleton Keycloak instance, or (c) use
the double-checked locking pattern with a volatile field. Option (b) is preferred
as it leverages Spring's singleton scope. Open a PR.
```

### 1.9 Add Feign Timeout Configuration

**Gap:** GAP-RES-003 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add explicit Feign client timeout configuration for all services that use OpenFeign
(user-service, fund-transfer-service, utility-payment-service). In each service's
application.yml (or via the Spring Cloud Config repository), add:
spring.cloud.openfeign.client.config.default.connect-timeout=5000
spring.cloud.openfeign.client.config.default.read-timeout=10000
This ensures Feign calls fail fast instead of waiting indefinitely. Open a PR.
```

### 1.10 Restrict Database User Privileges

**Gap:** GAP-SEC-003 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In docker-compose/mysql/privileges.sql, replace the single overly-permissive
database user with per-service users that have minimal privileges:
- banking_core_user: SELECT, INSERT, UPDATE, DELETE on banking_core_service.*
- banking_user_svc_user: SELECT, INSERT, UPDATE, DELETE on
  banking_core_user_service.*
- banking_ft_svc_user: SELECT, INSERT, UPDATE, DELETE on
  banking_core_fund_transfer_service.*
- banking_up_svc_user: SELECT, INSERT, UPDATE, DELETE on
  banking_core_utility_payment_service.*
Remove the global GRANT. Update each service's database connection configuration
to use its dedicated user. Open a PR.
```

### 1.11 Add .gitignore for Build Artifacts

**Gap:** GAP-ORG-004 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update the root .gitignore to exclude build/ and .gradle/ directories for all
services. Then remove all committed build/ and .gradle/ directories from Git tracking
using git rm -r --cached. Open a PR.
```

### 1.12 Fix Raw ResponseEntity Types

**Gap:** GAP-ERR-005 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In all controllers across all services, replace raw ResponseEntity with properly
parameterized types (e.g., ResponseEntity<BankAccount>, ResponseEntity<List<User>>,
ResponseEntity<FundTransferResponse>). The internet-banking-user-service
UserController is already correct and can serve as a reference. Open a PR.
```

---

## Phase 2: Important Improvements (Reliability, Testing & Observability)

> **Goal:** Improve system reliability with circuit breakers and retries, establish test coverage, and add production-grade observability.

### 2.1 Add Circuit Breakers to Feign Clients

**Gap:** GAP-RES-001 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breaker support to all three services that call Core Banking
via Feign (user-service, fund-transfer-service, utility-payment-service):

1. Add spring-cloud-starter-circuitbreaker-resilience4j to each service's
   build.gradle
2. Enable Feign circuit breaker: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client that return meaningful error
   responses (e.g., FundTransferResponse with message="Core banking service is
   temporarily unavailable")
4. Configure circuit breaker parameters: failure-rate-threshold=50,
   wait-duration-in-open-state=30s, sliding-window-size=10
5. Add @FeignClient(fallback = ...) to each Feign client interface
Open a PR.
```

### 2.2 Add @Transactional to Orchestration Services

**Gap:** GAP-RES-008 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In internet-banking-fund-transfer-service and internet-banking-utility-payment-service,
add proper transaction management:

1. Add @Transactional to FundTransferService.fundTransfer() method
2. Add @Transactional to UtilityPaymentService.utilPayment() method
3. Ensure that if the Feign call succeeds but the local DB update fails, the local
   record is rolled back (the core banking side cannot be rolled back automatically,
   so add a compensating transaction mechanism or at minimum mark the local record
   as FAILED for manual reconciliation)
4. Add error handling that catches FeignException and marks the local record as FAILED
   instead of letting the exception propagate
Open a PR.
```

### 2.3 Add Idempotency Protection

**Gap:** GAP-RES-005 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to the fund transfer and utility payment endpoints:

1. Add an optional X-Idempotency-Key header to POST /api/v1/transfer and
   POST /api/v1/utility-payment
2. Before processing, check if a record with that idempotency key already exists
   in the database. If it does, return the existing result
3. Add a unique constraint on the idempotency key column in fund_transfer and
   utility_payment tables
4. If no idempotency key is provided, generate a UUID (backward compatible)
5. Write unit tests for duplicate request handling
Open a PR.
```

### 2.4 Add Feign Error Handling and Retry Policies

**Gaps:** GAP-ERR-004, GAP-RES-002 | **Severity:** High + Medium | **Effort:** Medium

**Devin Prompt:**
```
Improve Feign client error handling across all services:

1. Add a CustomFeignErrorDecoder to internet-banking-user-service's
   BankingCoreRestClient (it currently has none)
2. Standardize the error decoders across all services to translate core banking
   error responses into domain-specific exceptions
3. Add Resilience4j @Retry with max-attempts=3 and wait-duration=1s for transient
   failures (HTTP 500, 502, 503, 504)
4. Do NOT retry 4xx errors (client errors)
Open a PR.
```

### 2.5 Write Unit Tests for Business Services

**Gap:** GAP-TEST-001 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Write comprehensive unit tests for internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service:

For internet-banking-user-service UserService:
- Test createUser() happy path (user created in Keycloak and DB)
- Test createUser() with already registered email
- Test createUser() with mismatched email
- Test createUser() with non-existent identification
- Test updateUser() with APPROVED status (enables Keycloak user)
- Test readUsers() pagination
- Test readUser() by ID, both found and not found

For internet-banking-fund-transfer-service FundTransferService:
- Test fundTransfer() happy path
- Test fundTransfer() when core banking returns error
- Test readAllTransfers() pagination

For internet-banking-utility-payment-service UtilityPaymentService:
- Test utilPayment() happy path
- Test utilPayment() when core banking returns error
- Test readPayments() pagination

Use Mockito to mock Feign clients and repositories. Add test application.yml
with H2 configuration for fund-transfer-service and utility-payment-service.
Open a PR.
```

### 2.6 Add Integration Tests

**Gap:** GAP-TEST-002 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests for the core-banking-service using @SpringBootTest with H2:

1. Create AccountControllerIntegrationTest using @WebMvcTest or @SpringBootTest
   with MockMvc
2. Test GET /api/v1/account/bank-account/{number} returns 200 for existing account
3. Test GET /api/v1/account/bank-account/{number} returns 404 for non-existent
4. Test POST /api/v1/transaction/fund-transfer with valid data returns 200
5. Test POST /api/v1/transaction/fund-transfer with insufficient funds returns 422
6. Verify database state after successful transfer

Add src/test/resources/application.yml with H2 config and Flyway test migrations.
Ensure tests are self-contained and don't require external services.
Open a PR.
```

### 2.7 Add Custom Health Indicators

**Gap:** GAP-OBS-001 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom Spring Boot Actuator health indicators to the services:

1. In core-banking-service: Add a DatabaseHealthIndicator that queries
   SELECT 1 to verify MySQL connectivity
2. In internet-banking-user-service: Add a KeycloakHealthIndicator that checks
   Keycloak server availability
3. In fund-transfer-service and utility-payment-service: Add a
   CoreBankingHealthIndicator that calls the core banking actuator health endpoint

Implement each as a @Component extending AbstractHealthIndicator.
Configure management.endpoint.health.show-details=always for all services.
Open a PR.
```

### 2.8 Add Structured JSON Logging

**Gap:** GAP-OBS-002 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Configure structured JSON logging for all services:

1. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency to all services
2. Create a logback-spring.xml in each service's src/main/resources with:
   - Console appender with JSON encoder for the docker profile
   - Pattern appender (existing default) for the default profile
3. Include MDC fields: traceId, spanId, serviceName
4. Ensure Micrometer Brave trace context is propagated to MDC
Open a PR.
```

### 2.9 Add Prometheus Metrics

**Gap:** GAP-OBS-003 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add io.micrometer:micrometer-registry-prometheus dependency to all services
2. Expose /actuator/prometheus endpoint in application config
3. Add @Timed annotations to key service methods:
   - TransactionService.fundTransfer() and utilPayment()
   - FundTransferService.fundTransfer()
   - UtilityPaymentService.utilPayment()
   - UserService.createUser()
4. Add custom Counter metrics for business events:
   - fund_transfer_total (success/failure)
   - utility_payment_total (success/failure)
   - user_registration_total (success/failure)
Open a PR.
```

### 2.10 Add Pagination Metadata to List Responses

**Gap:** GAP-API-003 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Update all paginated endpoints to return pagination metadata instead of raw lists:

1. Create a shared PagedResponse<T> wrapper DTO with fields:
   content (List<T>), pageNumber, pageSize, totalElements, totalPages, last
2. Update these endpoints to return PagedResponse:
   - Core Banking GET /api/v1/user
   - User Service GET /api/v1/bank-users
   - Fund Transfer GET /api/v1/transfer
   - Utility Payment GET /api/v1/utility-payment
3. Use Spring's Page object to populate the metadata
Open a PR.
```

### 2.11 Add Retry Policies

**Gap:** GAP-RES-002 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add Resilience4j retry configuration for Feign clients in fund-transfer-service,
utility-payment-service, and user-service:

1. Add io.github.resilience4j:resilience4j-spring-boot3 dependency
2. Configure retry for each Feign client with: max-attempts=3,
   wait-duration=1s, exponential-backoff-multiplier=2,
   retry-exceptions=[FeignException.ServiceUnavailable, FeignException.GatewayTimeout]
3. Do not retry on 4xx responses
Open a PR.
```

### 2.12 Configure Code Formatter

**Gap:** GAP-ORG-003 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add Spotless Gradle plugin to all services for consistent code formatting:

1. Add id 'com.diffplug.spotless' version '6.25.0' to each service's build.gradle
2. Configure it with google-java-format for Java files
3. Add a spotlessCheck task to the build pipeline
4. Run spotlessApply to format all existing code
Open a PR.
```

---

## Phase 3: Polish (Architecture & API Maturity)

> **Goal:** Improve developer experience, API maturity, and long-term maintainability.

### 3.1 Extract Shared Library as Multi-Module Build

**Gap:** GAP-ORG-001 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Restructure the project as a Gradle multi-module build:

1. Create a root settings.gradle that includes all 7 services as subprojects
2. Create a root build.gradle with shared plugin and dependency management
3. Extract a new shared-library module containing:
   - BaseMapper
   - AuditAware
   - SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - CustomFeignClientConfiguration, CustomFeignErrorDecoder
4. Update each service's build.gradle to depend on the shared library
5. Remove the duplicated classes from each service
6. Verify all services still compile and tests pass
Open a PR.
```

### 3.2 Add Consumer-Driven Contract Tests

**Gap:** GAP-TEST-003 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between the Core Banking Service (provider) and its
consumers (User Service, Fund Transfer Service, Utility Payment Service):

1. Add spring-cloud-contract-verifier to core-banking-service
2. Define contracts in src/test/resources/contracts/ for each endpoint:
   - GET /api/v1/user/{identification} → user response shape
   - GET /api/v1/account/bank-account/{number} → account response shape
   - POST /api/v1/transaction/fund-transfer → transfer response shape
   - POST /api/v1/transaction/util-payment → payment response shape
3. Generate and publish stubs
4. Add spring-cloud-contract-stub-runner to consumer services' tests
5. Write consumer-side contract verification tests using WireMock stubs
Open a PR.
```

### 3.3 Standardize API Error Format (RFC 7807)

**Gap:** GAP-API-005, GAP-ERR-003 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Adopt RFC 7807 Problem Details for HTTP APIs as the standard error response format
across all services:

1. Replace ErrorResponse with a ProblemDetail class (or use Spring 6's built-in
   org.springframework.http.ProblemDetail)
2. Update all GlobalExceptionHandler methods to return ProblemDetail with fields:
   type (URI), title, status, detail, instance
3. Add a custom extension field "errorCode" for the existing business error codes
4. Ensure Content-Type: application/problem+json is set on error responses
Open a PR.
```

### 3.4 Add Rate Limiting to API Gateway

**Gap:** GAP-SEC-006 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in
RequestRateLimiter filter:

1. Add spring-boot-starter-data-redis-reactive dependency
2. Add Redis to docker-compose.yml
3. Configure rate limiting per route:
   - /user/api/v1/bank-users/register: 5 requests/minute per IP
   - All authenticated routes: 100 requests/minute per user
4. Use RedisRateLimiter with the Token Bucket algorithm
5. Return HTTP 429 Too Many Requests when limit is exceeded
Open a PR.
```

### 3.5 Add Dependency Vulnerability Scanning

**Gap:** GAP-SEC-007 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency-Check to all services:

1. Add the org.owasp.dependencycheck Gradle plugin (version 9.0.9) to each
   service's build.gradle
2. Configure it to fail the build on CVSS score >= 7
3. Run the check once and document any existing vulnerabilities
4. Add a suppressions.xml for any false positives
Open a PR.
```

### 3.6 Standardize Package Structure

**Gap:** GAP-ORG-002 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:
- com.javatodev.finance.controller (REST controllers)
- com.javatodev.finance.service (business logic)
- com.javatodev.finance.model.entity (JPA entities)
- com.javatodev.finance.model.dto (DTOs and request/response objects)
- com.javatodev.finance.model.mapper (entity-DTO mappers)
- com.javatodev.finance.repository (Spring Data repositories)
- com.javatodev.finance.client (Feign clients)
- com.javatodev.finance.configuration (Spring config classes)
- com.javatodev.finance.exception (exception classes)

Move classes that are in non-standard packages. Update all imports.
Verify compilation and tests pass.
Open a PR.
```

### 3.7 Fix OpenAPI/Swagger Configuration

**Gap:** GAP-API-004 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Fix OpenAPI/Swagger configuration in core-banking-service, user-service,
fund-transfer-service, and utility-payment-service:

1. Replace springdoc-openapi-starter-webflux-ui with
   springdoc-openapi-starter-webmvc-ui (these are servlet-based, not reactive)
2. Add an @OpenAPIDefinition annotation to each service's main class with:
   title, version, description, contact info
3. Add @ApiResponse annotations to all controller methods documenting success
   and error response codes
4. Add @Schema annotations to DTOs with descriptions and examples
Open a PR.
```

### 3.8 Standardize Endpoint Naming

**Gap:** GAP-API-002 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize REST endpoint naming across all services to follow RESTful conventions:

1. Core Banking: Rename /api/v1/account/bank-account/{account_number} to
   /api/v1/accounts/{accountNumber} (kebab-case path, camelCase variable)
2. Core Banking: Rename /api/v1/account/util-account/{account_name} to
   /api/v1/utility-accounts/{providerName}
3. User Service: Rename /api/v1/bank-users/register to POST /api/v1/users
   (resource creation via POST, no verb)
4. User Service: Rename /api/v1/bank-users/update/{id} to
   PATCH /api/v1/users/{id} (no verb in URL)
5. Update API Gateway route configuration to match new paths
6. Update all Feign client URL mappings
Open a PR.
```

### 3.9 Implement Flyway for All Services

**Gap:** Related to data model consistency

**Devin Prompt:**
```
Add Flyway database migration support to user-service, fund-transfer-service, and
utility-payment-service (currently only core-banking-service uses Flyway):

1. Add org.flywaydb:flyway-core and org.flywaydb:flyway-mysql to each service's
   build.gradle
2. Create initial migration scripts (V1.0__create_schema.sql) for each service
   based on the current JPA entity definitions
3. Remove reliance on JPA ddl-auto for schema creation
4. Create corresponding H2-compatible test migrations
Open a PR.
```

### 3.10 Document API Versioning Strategy

**Gap:** GAP-API-001 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create an API versioning strategy document at docs/API_VERSIONING.md that covers:

1. URL-based versioning scheme (/api/v1, /api/v2)
2. Rules for when a new version is required (breaking changes only)
3. Deprecation policy (minimum 6 months notice before removing old versions)
4. Header-based content negotiation as an alternative for minor variations
5. Update the API Gateway configuration to support routing to multiple API versions
Open a PR.
```

---

## Summary

| Phase | Items | Focus | Total Effort |
|-------|-------|-------|-------------|
| **Phase 1** | 12 items | Security, data integrity, information leaks | ~2-3 weeks |
| **Phase 2** | 12 items | Reliability, testing, observability | ~4-6 weeks |
| **Phase 3** | 10 items | Architecture, API maturity, polish | ~3-4 weeks |

### Recommended Execution Order Within Each Phase

**Phase 1 (Critical Path):**
1. GAP-RES-007 — Balance bug fix (immediate data integrity risk)
2. GAP-RES-006 — Race condition fix (concurrent data corruption risk)
3. GAP-ERR-002 — Stop leaking exception details
4. GAP-SEC-002 — Input validation
5. GAP-SEC-001 — Externalize credentials
6. GAP-OBS-005 — Remove sensitive data from logs
7. GAP-ERR-001 — HTTP status codes
8. GAP-SEC-005 — Keycloak singleton
9. GAP-RES-003 — Timeouts
10. GAP-SEC-003 — DB privileges
11. GAP-ORG-004 — Build artifacts in git
12. GAP-ERR-005 — ResponseEntity types

**Phase 2 (Reliability):**
1. GAP-RES-001 — Circuit breakers (prevents cascading failures)
2. GAP-RES-008 — @Transactional on orchestrators
3. GAP-RES-005 — Idempotency (prevents duplicate transactions)
4. GAP-ERR-004 + GAP-RES-002 — Feign error handling + retries
5. GAP-TEST-001 — Unit tests
6. GAP-TEST-002 — Integration tests
7. GAP-OBS-001 — Health indicators
8. GAP-OBS-002 — Structured logging
9. GAP-OBS-003 — Prometheus metrics
10. GAP-API-003 — Pagination metadata
11. GAP-RES-002 — Retry policies
12. GAP-ORG-003 — Code formatter

**Phase 3 (Polish):**
1. GAP-ORG-001 — Multi-module build
2. GAP-TEST-003 — Contract tests
3. GAP-API-005 — RFC 7807 errors
4. GAP-SEC-006 — Rate limiting
5. GAP-SEC-007 — Dependency scanning
6. GAP-ORG-002 — Package structure
7. GAP-API-004 — OpenAPI config
8. GAP-API-002 — Endpoint naming
9. Flyway for all services
10. GAP-API-001 — Versioning strategy
