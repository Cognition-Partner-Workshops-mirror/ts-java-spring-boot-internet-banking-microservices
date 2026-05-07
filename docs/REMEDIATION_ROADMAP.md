# Remediation Roadmap

## Overview

This roadmap prioritizes the 62 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): High-impact, low-effort items that immediately improve security, reliability, and code quality.
- **Phase 2 — Important** (3-6 weeks): Medium-to-large effort items that address structural deficiencies and significantly improve maintainability.
- **Phase 3 — Polish** (6-12 weeks): Longer-term improvements that bring the codebase to production-grade standards.

Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins

*Focus: Immediate security fixes, error handling, and basic resilience. These are high-impact changes with small effort.*

### 1.1 Add Input Validation to All Request DTOs (S-1, S-2)

**Severity:** Critical | **Effort:** Small-Medium | **Gaps:** S-1, S-2

Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Email`, `@Size`) to all request DTOs and `@Valid` to controller method parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across the internet-banking microservices project.
Specifically:
1. Add @NotBlank, @NotNull, @Email, @Min, @Size annotations to FundTransferRequest (fromAccount, toAccount required; amount > 0), UtilityPaymentRequest (account, providerId required; amount > 0; referenceNumber required), User registration DTO (identification, email required; email must be valid), and UserUpdateRequest (status required).
2. Add @Valid annotation to all @RequestBody parameters in controllers.
3. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns a structured ErrorResponse with field-level validation errors and HTTP 422.
4. Add corresponding unit tests for validation.
```

### 1.2 Fix Error Handler to Use Correct HTTP Status Codes (EH-1, EH-2)

**Severity:** High | **Effort:** Small | **Gaps:** EH-1, EH-2

Replace the generic catch-all handler that returns a raw string with a structured response, and map exceptions to appropriate HTTP status codes.

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service):
1. Change EntityNotFoundException handler to return HTTP 404 with a structured ErrorResponse.
2. Change InsufficientFundsException handler to return HTTP 422 with a structured ErrorResponse.
3. Change the generic Exception catch-all to return HTTP 500 with a structured ErrorResponse that does NOT include the exception message or stack trace (log it at ERROR level instead).
4. Add a timestamp and request path to the ErrorResponse class.
5. Add unit tests verifying correct HTTP status codes for each exception type.
```

### 1.3 Add Feign Timeout Configuration (R-4)

**Severity:** High | **Effort:** Small | **Gap:** R-4

Configure explicit connect and read timeouts for all Feign clients to prevent thread exhaustion.

**Devin Prompt:**
```
Add Feign timeout configuration to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service.
Set connectTimeout to 5000ms and readTimeout to 10000ms for the core-banking-service Feign clients.
Add this via application.yml configuration under spring.cloud.openfeign.client.config.core-banking-service.
Also add gateway route timeout configuration in the API gateway application.yml.
```

### 1.4 Add Feign Error Decoder to All Services (EH-4)

**Severity:** High | **Effort:** Small | **Gap:** EH-4

Add a `FeignErrorDecoder` to Fund Transfer and Utility Payment services (User Service already has one) so Feign errors are translated into domain exceptions.

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service, modeled after the one in internet-banking-user-service.
The decoder should:
1. Map 404 responses to EntityNotFoundException
2. Map 422 responses to SimpleBankingGlobalException with the original error code/message
3. Map other 4xx/5xx to SimpleBankingGlobalException with appropriate messages
4. Register it in the CustomFeignClientConfiguration class
5. Add unit tests for the error decoder
```

### 1.5 Fix Hardcoded Credentials in Docker Compose (S-8)

**Severity:** Critical | **Effort:** Small | **Gap:** S-8

Replace hardcoded passwords with environment variable references and add a `.env.example` file.

**Devin Prompt:**
```
Replace all hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml with environment variable references.
1. Replace MYSQL_ROOT_PASSWORD, Keycloak admin password, KC_DB_PASSWORD, and POSTGRES_PASSWORD with ${VARIABLE_NAME} syntax.
2. Create a .env.example file with placeholder values and comments.
3. Add .env to .gitignore.
4. Update README.md with instructions to copy .env.example to .env and fill in values.
```

### 1.6 Remove Sensitive Data from Logs (O-2)

**Severity:** High | **Effort:** Small | **Gap:** O-2

Remove or mask sensitive data (passwords, full account numbers) from log statements.

**Devin Prompt:**
```
Audit all log statements in the internet-banking microservices and fix sensitive data exposure:
1. In internet-banking-user-service UserService.createUser(), do NOT log the full User object (it contains the password). Log only the email.
2. In internet-banking-fund-transfer-service FundTransferService, mask account numbers in logs (show only last 4 digits).
3. In internet-banking-utility-payment-service UtilityPaymentService, mask account numbers similarly.
4. Add a utility method for masking account numbers to keep it consistent.
```

### 1.7 Fix the Balance Calculation Bug (Knowledge Base 4.5)

**Severity:** Critical | **Effort:** Small | **Gap:** N/A (bug)

Fix the double-subtraction/addition bug in `TransactionService`.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService:
1. In internalFundTransfer(): After subtracting from actualBalance, set availableBalance = actualBalance (not actualBalance - amount again). Same for the credit side.
2. In utilPayment(): Same fix for the debit operation.
3. Verify the existing TransactionServiceTest tests still pass and add a test that asserts the correct final balances after a transfer.
```

### 1.8 Add Type Parameters to ResponseEntity (A-1)

**Severity:** Medium | **Effort:** Small | **Gap:** A-1

Add generic type parameters to all `ResponseEntity` return types in controllers.

**Devin Prompt:**
```
Add generic type parameters to all ResponseEntity return types across all controllers in the internet-banking microservices.
For example, change `ResponseEntity` to `ResponseEntity<FundTransferResponse>` in FundTransferController.sendFundTransfer().
Do this for all controller methods in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
```

---

## Phase 2: Important

*Focus: Structural improvements, testing, and resilience patterns. These require moderate effort but significantly improve reliability.*

### 2.1 Add Circuit Breakers with Resilience4j (R-1, R-2, R-3, R-5)

**Severity:** Critical-High | **Effort:** Medium | **Gaps:** R-1, R-2, R-3, R-5

Add Resilience4j circuit breakers, retry, and fallback to all inter-service communication.

**Devin Prompt:**
```
Add Resilience4j circuit breaker and retry to all Feign clients in the internet-banking microservices:
1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service build.gradle files.
2. Configure circuit breaker in application.yml with: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=5.
3. Configure retry with: maxAttempts=3, waitDuration=1s, retryExceptions=[FeignException.class].
4. Add @CircuitBreaker and @Retry annotations to Feign client usage in service classes.
5. Add fallback methods that return appropriate error responses.
6. Add unit tests for circuit breaker behavior.
```

### 2.2 Add Unit Tests for All Business Services (T-1, T-2, T-3)

**Severity:** Critical | **Effort:** Medium | **Gaps:** T-1, T-2, T-3

Write comprehensive unit tests for the three untested business services.

**Devin Prompt:**
```
Add comprehensive unit tests using JUnit 5 and Mockito for:
1. internet-banking-user-service UserService: Test createUser (happy path, duplicate email, invalid email, user not found in core banking, Keycloak failure), updateUser (approve flow, entity not found), readUser, readUsers.
2. internet-banking-fund-transfer-service FundTransferService: Test fundTransfer (happy path, Feign failure, status transitions), readAllTransfers.
3. internet-banking-utility-payment-service UtilityPaymentService: Test utilPayment (happy path, Feign failure, status transitions), readPayments.
Mock all external dependencies (Feign clients, repositories, KeycloakUserService).
Target 80%+ line coverage for each service class.
```

### 2.3 Extract Shared Library Module (CO-1, CO-2, EH-6)

**Severity:** Medium | **Effort:** Medium | **Gaps:** CO-1, CO-2, EH-6

Create a shared library to eliminate code duplication across services.

**Devin Prompt:**
```
Create a shared Gradle module called 'internet-banking-common' in the project:
1. Set up a Gradle multi-project build with a settings.gradle at the root that includes all existing services plus the new common module.
2. Move duplicated classes to the common module: ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler, AuditAware, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, BaseMapper, CustomFeignClientConfiguration, TransactionStatus enum.
3. Update all services to depend on the common module.
4. Remove the duplicated classes from each service.
5. Ensure all existing tests still pass.
```

### 2.4 Add Role-Based Access Control (S-4, S-5)

**Severity:** Critical-High | **Effort:** Medium | **Gaps:** S-4, S-5

Implement RBAC using Keycloak roles and Spring Security.

**Devin Prompt:**
```
Implement role-based access control (RBAC) in the internet-banking microservices:
1. Define roles in Keycloak: ROLE_USER (can view own accounts, make transfers from own accounts), ROLE_ADMIN (can approve users, view all accounts).
2. Update the API Gateway SecurityConfiguration to extract roles from JWT claims.
3. Propagate roles via headers or keep them in the JWT and validate at the service level.
4. Add ownership validation in internet-banking-fund-transfer-service: verify the authenticated user owns the fromAccount before processing a transfer.
5. Add ownership validation in internet-banking-utility-payment-service similarly.
6. Add @PreAuthorize annotations to user-service endpoints: ROLE_ADMIN for user approval, ROLE_USER for read operations.
7. Update the Keycloak realm export with the new roles.
```

### 2.5 Add Integration Tests with MockMvc (T-5, T-6)

**Severity:** High | **Effort:** Medium-Large | **Gaps:** T-5, T-6

Add controller-level integration tests for all services.

**Devin Prompt:**
```
Add @SpringBootTest integration tests with MockMvc for all services:
1. core-banking-service: Test AccountController, UserController, TransactionController endpoints with H2 database.
2. internet-banking-user-service: Test UserController with mocked Feign client and mocked KeycloakUserService.
3. internet-banking-fund-transfer-service: Test FundTransferController with mocked Feign client.
4. internet-banking-utility-payment-service: Test UtilityPaymentController with mocked Feign client.
Each test should verify:
- Correct HTTP status codes for success and error cases
- Response body structure matches expected format
- Proper error responses for invalid input
Use @WebMvcTest or @SpringBootTest with MockMvc and @MockBean for external dependencies.
```

### 2.6 Add Pagination Metadata to List Responses (A-5)

**Severity:** Medium | **Effort:** Small | **Gaps:** A-5

Wrap list responses in a paginated response object.

**Devin Prompt:**
```
Create a generic PaginatedResponse<T> class in the shared common module with fields: content (List<T>), page (int), size (int), totalElements (long), totalPages (int).
Update all list endpoints across all services to return PaginatedResponse<T> instead of List<T>.
Modify service methods to return the full Page<T> object so controllers can extract pagination metadata.
Update existing tests to verify the new response structure.
```

### 2.7 Add Structured JSON Logging (O-1, O-4)

**Severity:** Medium | **Effort:** Medium | **Gaps:** O-1, O-4

Configure JSON logging with trace ID correlation.

**Devin Prompt:**
```
Add structured JSON logging to all microservices:
1. Add logstash-logback-encoder dependency to each service's build.gradle.
2. Create a logback-spring.xml in each service's src/main/resources with a JSON console appender.
3. Include fields: timestamp, level, logger, message, traceId, spanId, service name.
4. Configure MDC propagation so Micrometer trace/span IDs appear in all log entries.
5. Keep a plain text format for the 'local' profile for developer readability.
6. Use the JSON format for 'docker' and 'production' profiles.
```

### 2.8 Fix Swagger Dependency for MVC Services (A-8)

**Severity:** Medium | **Effort:** Small | **Gap:** A-8

Replace the incorrect webflux OpenAPI dependency in Spring MVC services.

**Devin Prompt:**
```
Fix the OpenAPI/Swagger dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' since these are Spring MVC (not WebFlux) services.
Keep the webflux variant only in internet-banking-api-gateway which is a WebFlux application.
Verify Swagger UI loads correctly by building each service.
```

---

## Phase 3: Polish

*Focus: Production-grade hardening, contract testing, observability, and long-term architectural improvements.*

### 3.1 Add Contract Tests Between Services (T-8)

**Severity:** High | **Effort:** Large | **Gap:** T-8

Implement Spring Cloud Contract tests to guarantee API compatibility.

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:
1. Add spring-cloud-starter-contract-verifier to core-banking-service (as the provider).
2. Create contract DSL files for each endpoint consumed by other services:
   - GET /api/v1/user/{identification} (consumed by user-service)
   - GET /api/v1/account/bank-account/{account_number} (consumed by fund-transfer and utility-payment)
   - POST /api/v1/transaction/fund-transfer (consumed by fund-transfer)
   - POST /api/v1/transaction/util-payment (consumed by utility-payment)
3. Generate contract stubs from core-banking-service.
4. Add spring-cloud-starter-contract-stub-runner to consumer services and write tests that verify Feign clients against the stubs.
```

### 3.2 Implement Saga Pattern for Distributed Transactions (R-7, R-8)

**Severity:** Critical | **Effort:** Large | **Gaps:** R-7, R-8

Implement a saga pattern with compensation for the fund transfer and utility payment flows.

**Devin Prompt:**
```
Implement a choreography-based saga pattern for the fund transfer flow:
1. Add a 'status' field tracking to fund transfer: PENDING -> CORE_BANKING_PROCESSING -> SUCCESS / FAILED / COMPENSATION_NEEDED.
2. Wrap the Feign call in a try-catch; on failure, update status to FAILED.
3. Add a compensation endpoint in core-banking-service to reverse a completed transfer by transactionId.
4. Add a scheduled job in fund-transfer-service that detects PENDING transfers older than N minutes and marks them for investigation.
5. Add @Transactional to the orchestrating service methods in fund-transfer-service and utility-payment-service.
6. Implement the same pattern for utility-payment-service.
7. Document the saga flow in the KNOWLEDGE_BASE.md.
```

### 3.3 Add Prometheus Metrics and Custom Business Metrics (O-7, O-8)

**Severity:** Medium | **Effort:** Medium | **Gaps:** O-7, O-8

Enable Prometheus metrics export and add custom business metrics.

**Devin Prompt:**
```
Add Prometheus metrics to all microservices:
1. Add micrometer-registry-prometheus dependency to each service's build.gradle.
2. Configure management.endpoints.web.exposure.include to expose prometheus, health, info, metrics endpoints.
3. Add custom metrics using Micrometer:
   - Counter: fund_transfers_total (tagged by status: success/failed)
   - Counter: utility_payments_total (tagged by status: success/failed)
   - Counter: user_registrations_total (tagged by status: success/failed)
   - Timer: fund_transfer_duration_seconds
   - Timer: utility_payment_duration_seconds
4. Add @Timed annotation to key service methods.
5. Add a docker-compose service for Prometheus with a prometheus.yml that scrapes all services.
```

### 3.4 Add Custom Health Indicators (O-5)

**Severity:** Medium | **Effort:** Small | **Gap:** O-5

Add health indicators for critical dependencies.

**Devin Prompt:**
```
Add custom health indicators to the microservices:
1. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak server.
2. internet-banking-fund-transfer-service: Add a CoreBankingHealthIndicator that calls the core banking actuator health endpoint.
3. internet-banking-utility-payment-service: Same CoreBankingHealthIndicator.
4. internet-banking-api-gateway: Add health indicators for each downstream service.
5. Configure management.endpoint.health.show-details=when-authorized in all services.
```

### 3.5 Add Bulkhead Isolation (R-6)

**Severity:** Medium | **Effort:** Medium | **Gap:** R-6

Add bulkhead configuration to prevent cascading resource exhaustion.

**Devin Prompt:**
```
Add Resilience4j bulkhead configuration to all services that make Feign calls:
1. Add resilience4j-bulkhead dependency to fund-transfer, utility-payment, and user services.
2. Configure thread pool bulkhead for Feign calls: maxConcurrentCalls=10, maxWaitDuration=500ms.
3. Add @Bulkhead annotations to service methods that call Core Banking.
4. Configure separate bulkhead instances for different Feign client methods to prevent one slow endpoint from affecting others.
5. Add tests verifying bulkhead rejection when limit is exceeded.
```

### 3.6 Add Filtering and Search to List Endpoints (A-6)

**Severity:** Medium | **Effort:** Medium | **Gap:** A-6

Add query parameter-based filtering to all list endpoints.

**Devin Prompt:**
```
Add filtering capabilities to list endpoints:
1. Fund transfer list: Filter by status, fromAccount, toAccount, date range (createdDateFrom, createdDateTo).
2. Utility payment list: Filter by status, providerId, account, date range.
3. User list (user-service): Filter by status.
4. User list (core-banking): Filter by email, firstName, lastName.
5. Use Spring Data JPA Specifications or QueryDSL for dynamic query building.
6. Add @RequestParam annotations with required=false for all filter parameters.
7. Update OpenAPI annotations to document the new parameters.
8. Add tests for filtered queries.
```

### 3.7 Add Dependency Vulnerability Scanning (S-12)

**Severity:** High | **Effort:** Small | **Gap:** S-12

Set up automated dependency vulnerability scanning.

**Devin Prompt:**
```
Add dependency vulnerability scanning to the project:
1. Add the OWASP dependency-check Gradle plugin to all service build.gradle files.
2. Configure it to fail the build on CVSS score >= 7 (HIGH severity).
3. Add a GitHub Actions workflow that runs dependency-check on every PR.
4. Also add a Dependabot configuration (.github/dependabot.yml) to automatically create PRs for dependency updates for all Gradle services.
5. Run the initial scan and document any existing vulnerabilities.
```

### 3.8 Implement Rate Limiting on Registration Endpoint (S-7)

**Severity:** Medium | **Effort:** Small | **Gap:** S-7

Add rate limiting to the public user registration endpoint to prevent abuse.

**Devin Prompt:**
```
Add rate limiting to the user registration endpoint in the API Gateway:
1. Add spring-cloud-starter-gateway rate limiting configuration.
2. Configure a RequestRateLimiter filter on the /user/api/v1/bank-users/register route.
3. Use Redis-based or in-memory rate limiting with: replenishRate=5, burstCapacity=10 (5 requests per second per IP).
4. Add a Redis service to docker-compose.yml for rate limit state storage.
5. Return HTTP 429 Too Many Requests when the limit is exceeded.
6. Add integration tests for rate limiting behavior.
```

### 3.9 Fix Thread-Safety Issue in KeycloakProperties (S-11)

**Severity:** Medium | **Effort:** Small | **Gap:** S-11

Fix the non-thread-safe singleton pattern in `KeycloakProperties`.

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.getInstance():
1. Either use double-checked locking with volatile, or better yet, initialize the Keycloak instance in a @PostConstruct method.
2. Remove the static field and make the Keycloak instance a regular Spring-managed bean via a @Bean method in a @Configuration class.
3. Inject the Keycloak bean directly into KeycloakManager instead of calling getInstance().
4. Update tests accordingly.
```

### 3.10 Set Up Testcontainers for MySQL (T-7)

**Severity:** Medium | **Effort:** Medium | **Gap:** T-7

Replace H2 test databases with Testcontainers MySQL for higher-fidelity testing.

**Devin Prompt:**
```
Add Testcontainers with MySQL to the core-banking-service for integration tests:
1. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies to the test scope.
2. Create a base test class that starts a MySQL Testcontainer and configures Spring datasource properties.
3. Enable Flyway in the Testcontainer-based test profile so migrations run against real MySQL.
4. Convert existing integration tests to extend the base class.
5. Add a test that verifies the full Flyway migration + seed data runs successfully.
6. Document how to run Testcontainer-based tests (requires Docker).
```

---

## Execution Summary

| Phase | Items | Estimated Effort | Key Outcomes |
|-------|-------|-----------------|--------------|
| **Phase 1** | 8 items | 1-2 weeks | Input validation, proper error codes, timeout/circuit-breaker basics, credential hygiene, bug fix |
| **Phase 2** | 8 items | 3-6 weeks | RBAC, comprehensive tests, shared library, structured logging, resilience patterns |
| **Phase 3** | 10 items | 6-12 weeks | Contract tests, saga pattern, Prometheus metrics, vulnerability scanning, rate limiting |

### Recommended Execution Order Within Each Phase

**Phase 1 (do in order):**
1. Fix balance calculation bug (1.7) — correctness first
2. Input validation (1.1) — immediate security fix
3. Error handling (1.2) — proper error responses
4. Feign error decoder (1.4) — clean error propagation
5. Feign timeouts (1.3) — basic resilience
6. Remove sensitive logs (1.6) — compliance
7. Fix credentials (1.5) — security hygiene
8. ResponseEntity types (1.8) — code quality

**Phase 2 (do in order):**
1. Swagger dependency fix (2.8) — quick fix to unblock docs
2. Unit tests (2.2) — safety net before refactoring
3. Shared library (2.3) — enables consistent changes
4. Circuit breakers (2.1) — resilience
5. RBAC (2.4) — security
6. Integration tests (2.5) — validation
7. Pagination (2.6) — API quality
8. JSON logging (2.7) — observability

**Phase 3 (do in order):**
1. Dependency scanning (3.7) — security baseline
2. KeycloakProperties fix (3.9) — quick thread-safety fix
3. Rate limiting (3.8) — abuse prevention
4. Health indicators (3.4) — observability
5. Prometheus metrics (3.3) — monitoring
6. Testcontainers (3.10) — test fidelity
7. Filtering (3.6) — API completeness
8. Bulkhead (3.5) — advanced resilience
9. Contract tests (3.1) — cross-service safety
10. Saga pattern (3.2) — data consistency (largest effort, do last)
