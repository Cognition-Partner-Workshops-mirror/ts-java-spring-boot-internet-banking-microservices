# Internet Banking Microservices — Remediation Roadmap

## Table of Contents
- [Phase 1: Quick Wins (Critical/High Severity, Small Effort)](#phase-1-quick-wins)
- [Phase 2: Important (Critical/High Severity, Medium Effort)](#phase-2-important)
- [Phase 3: Polish (Medium/Low Severity, Various Effort)](#phase-3-polish)
- [Roadmap Summary](#roadmap-summary)

---

## Phase 1: Quick Wins

*High-impact items that can be resolved quickly — target completion: 1–2 weeks.*

### 1.1 Fix Generic Exception Handler to Stop Leaking Internal Details

**Gap Reference:** 2.2 (Critical, Small)

The catch-all `Exception` handler in every service exposes `"Exception occur inside API " + e`, which leaks stack traces and class names to clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler in all services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). Change the generic Exception handler to return a 500 Internal Server Error with a structured ErrorResponse body containing code="INTERNAL_ERROR" and message="An unexpected error occurred. Please try again later." Log the actual exception at ERROR level. Do not expose exception details to the client.
```

### 1.2 Fix HTTP Status Codes in Exception Handlers

**Gap Reference:** 2.1 (High, Small)

All exceptions currently return 400 Bad Request. Map them to correct HTTP semantics.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler in all services to return correct HTTP status codes: EntityNotFoundException should return 404 Not Found, InsufficientFundsException should return 422 Unprocessable Entity, UserAlreadyRegisteredException should return 409 Conflict, InvalidEmailException and InvalidBankingUserException should return 400 Bad Request, and the generic Exception handler should return 500 Internal Server Error. Use the structured ErrorResponse format for all error responses.
```

### 1.3 Add Bean Validation to Request DTOs

**Gap Reference:** 2.4 (High, Small) and 4.2 (Critical, Medium — partial)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta Bean Validation annotations to all request DTOs:
- FundTransferRequest (both core-banking and fund-transfer): @NotBlank on fromAccount, toAccount; @NotNull @Positive on amount.
- UtilityPaymentRequest (both core-banking and utility-payment): @NotNull on providerId; @NotNull @Positive on amount; @NotBlank on referenceNumber, account.
- User (user-service): @NotBlank @Email on email; @NotBlank on identification, password.
- UserUpdateRequest: @NotNull on status.
Add @Valid on all @RequestBody parameters in controllers. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details. Add the spring-boot-starter-validation dependency to each service's build.gradle if not present.
```

### 1.4 Stop Logging Sensitive Data

**Gap Reference:** 6.5 (High, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, audit all log statements across all services and redact sensitive data. Specifically:
- In FundTransferController and FundTransferService: do not log full request objects; instead log only the transfer ID and a non-sensitive summary (e.g., "Fund transfer initiated, amount: {amount}").
- In UserController (user-service): do not log user creation requests that may contain passwords; only log the email.
- In UtilityPaymentService: do not log full payment requests; log only the provider ID and amount.
- In core-banking TransactionController: do not log full request toString(); log only non-sensitive identifiers.
Ensure no account numbers, passwords, or PII are logged.
```

### 1.5 Fix Inconsistent Error Response Format

**Gap Reference:** 2.3 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, ensure all exception handlers in all services return the same ErrorResponse shape: { "code": "string", "message": "string" }. Currently the generic Exception handler returns a raw string. Update it to return an ErrorResponse with code "INTERNAL_ERROR". Also standardize the ErrorResponse class across all services to use the same structure (with @Builder and @Data).
```

### 1.6 Add Feign Client Timeout Configuration

**Gap Reference:** 7.3 (High, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure Feign client timeouts for the fund-transfer-service, user-service, and utility-payment-service. In each service's application.yml (or via the Spring Cloud Config repo), add:
spring.cloud.openfeign.client.config.default.connect-timeout=5000
spring.cloud.openfeign.client.config.default.read-timeout=10000
This ensures Feign calls fail fast rather than using the default 60-second timeout.
```

### 1.7 Add Feign Retry Configuration

**Gap Reference:** 7.2 (High, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add a Spring Retry configuration for Feign clients in fund-transfer-service, user-service, and utility-payment-service. Add the spring-retry dependency. Configure a Retryer bean with maxAttempts=3, initial interval=100ms, max interval=1000ms. Only retry on connection exceptions and 503 responses, NOT on 4xx errors. Apply to all Feign client configurations.
```

### 1.8 Remove Hardcoded Credentials from Source

**Gap Reference:** 4.1 (Critical, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, replace all hardcoded passwords in docker-compose files with environment variable references:
- docker-compose.yml and docker-compose-support-apps.yml: replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}, KC_DB_PASSWORD with ${KC_DB_PASSWORD}, KEYCLOAK_ADMIN_PASSWORD with ${KEYCLOAK_ADMIN_PASSWORD}.
- Create a docker-compose/.env.example file documenting required environment variables.
- In privileges.sql, add a comment noting the password should be changed for non-development environments.
- Update the README to reference the .env.example file.
```

---

## Phase 2: Important

*High-severity items requiring moderate implementation effort — target completion: 3–6 weeks.*

### 2.1 Add Circuit Breakers to Feign Clients

**Gap Reference:** 7.1 (Critical, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breakers to all Feign clients:
1. Add resilience4j-spring-boot3 and resilience4j-feign dependencies to fund-transfer-service, user-service, and utility-payment-service build.gradle files.
2. Enable Spring Cloud CircuitBreaker: spring.cloud.openfeign.circuitbreaker.enabled=true.
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return an error FundTransferResponse with message "Core banking service is currently unavailable."
   - BankingCoreRestClientFallback (user-service): throw a meaningful exception.
   - BankingCoreRestClientFallback (utility-payment): return an error UtilityPaymentResponse.
4. Configure circuit breaker thresholds in application.yml: failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10.
```

### 2.2 Fix Transaction Safety Issues

**Gap Reference:** 7.6 (Critical, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the transaction safety issues in core-banking-service TransactionService:
1. Fix the available balance calculation bug in internalFundTransfer(): after setting actualBalance, availableBalance should be set to the same new value (not actualBalance.subtract(amount) again, which causes double subtraction).
2. Same fix needed in utilPayment() method.
3. Add @Lock(LockModeType.PESSIMISTIC_WRITE) to BankAccountRepository.findByNumber() to prevent concurrent modification.
4. Add integration tests that verify correct balance after a transfer and after a utility payment.
```

### 2.3 Add Idempotency to Financial Operations

**Gap Reference:** 7.5 (Critical, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement idempotency for fund transfers and utility payments:
1. Add an optional X-Idempotency-Key header to FundTransferRequest and UtilityPaymentRequest processing.
2. Create an idempotency_key table in each service's database with columns: key (unique), response (JSON), created_at.
3. Before processing a fund transfer or utility payment, check if the idempotency key already exists — if so, return the stored response.
4. After successful processing, store the response against the key.
5. Add the X-Idempotency-Key header to the Feign client requests forwarded to core-banking-service.
```

### 2.4 Add Role-Based Authorization

**Gap Reference:** 4.3 (High, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add role-based authorization:
1. In the Keycloak realm export, define roles: ROLE_ADMIN, ROLE_USER.
2. Update SecurityConfiguration in the API gateway to enforce roles:
   - PATCH /user/api/v1/bank-users/update/** → requires ROLE_ADMIN.
   - POST /fund-transfer/api/v1/transfer → requires ROLE_USER.
   - POST /utility-payment/api/v1/utility-payment → requires ROLE_USER.
   - GET endpoints → requires ROLE_USER or ROLE_ADMIN.
3. In downstream services, add @PreAuthorize annotations for defense-in-depth where the X-Auth-Id header is used.
4. Update Keycloak realm configuration to include role claims in JWT tokens.
```

### 2.5 Consolidate Duplicated Code into banking-common

**Gap Reference:** 1.1 (High, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, extract duplicated code into the banking-common shared library:
1. Move these classes into banking-common: SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, AuditAware, BaseMapper, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, AuditConfig, AuditorAwareConfig.
2. Update each service's build.gradle to depend on banking-common (composite build or published artifact).
3. Remove the duplicated classes from each service.
4. Ensure all services compile and tests pass after the refactoring.
```

### 2.6 Add Fallback Behavior for Feign Clients

**Gap Reference:** 7.4 (Medium, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add FallbackFactory implementations for all Feign clients:
1. For BankingCoreFeignClient in fund-transfer-service: log the cause, set fund transfer status to FAILED, return an error response.
2. For BankingCoreRestClient in user-service: log the cause, throw a service unavailable exception.
3. For BankingCoreRestClient in utility-payment-service: log the cause, set payment status to FAILED, return an error response.
Register the fallback factories using @FeignClient(fallbackFactory = ...).
```

### 2.7 Add Unit Tests for All Services

**Gap Reference:** 3.1 (Critical, Large — first increment)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit tests:
1. For internet-banking-fund-transfer-service: test FundTransferService (success, feign failure, validation), test FundTransferController with MockMvc.
2. For internet-banking-user-service: test UserService (create, update, read, keycloak failures), test KeycloakUserService with mocked KeycloakManager, test UserController with MockMvc.
3. For internet-banking-utility-payment-service: test UtilityPaymentService (success, feign failure), test UtilityPaymentController with MockMvc.
Target at least 80% line coverage on service and controller layers. Use Mockito for mocking dependencies.
```

### 2.8 Add Input Validation Beyond DTOs

**Gap Reference:** 4.2 (Critical, Medium — remainder)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add business-level input validation:
1. In core-banking-service TransactionService.fundTransfer(): validate that fromAccount != toAccount.
2. In core-banking-service: validate account status is ACTIVE before allowing transactions.
3. In user-service UserService.createUser(): validate password strength (min length, complexity).
4. In fund-transfer-service: validate that the authenticated user (from X-Auth-Id) owns the fromAccount before processing.
5. Add corresponding test cases for each validation rule.
```

---

## Phase 3: Polish

*Medium and low severity items for production-readiness — target completion: 6–12 weeks.*

### 3.1 Add Integration Tests with Testcontainers

**Gap Reference:** 3.2 (High, Large)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests using Testcontainers:
1. Add the testcontainers and testcontainers-mysql dependencies to core-banking-service, fund-transfer-service, user-service, and utility-payment-service.
2. For core-banking-service: create integration tests that verify fund transfer and utility payment flows against a real MySQL database, verifying balance updates and transaction records.
3. For fund-transfer-service and utility-payment-service: use WireMock to simulate core-banking-service responses and verify local database state.
4. For user-service: mock Keycloak responses and verify user creation flow against a real MySQL database.
```

### 3.2 Fix OpenAPI/Swagger Dependency

**Gap Reference:** 5.1 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the OpenAPI documentation:
1. Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui in all 4 business services' build.gradle (they use Spring MVC, not WebFlux).
2. Add OpenAPI configuration class to each service with @OpenAPIDefinition including title, description, version.
3. Verify Swagger UI is accessible at /swagger-ui.html for each service.
4. Optionally configure the API gateway to aggregate all downstream OpenAPI specs.
```

### 3.3 Add Pagination Metadata to Responses

**Gap Reference:** 5.3 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update paginated endpoints to return proper pagination metadata:
1. Create a PageResponse<T> wrapper in banking-common with fields: content (List<T>), pageNumber, pageSize, totalElements, totalPages.
2. Update these endpoints to return PageResponse instead of List:
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/transfer (fund-transfer-service)
   - GET /api/v1/utility-payment (utility-payment-service)
   - GET /api/v1/user (core-banking-service)
3. Update existing tests to verify pagination metadata.
```

### 3.4 Configure Structured JSON Logging

**Gap Reference:** 6.1 (Medium, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure structured JSON logging for all services:
1. Add logstash-logback-encoder dependency to all services.
2. Create a logback-spring.xml in each service's resources with a JSON layout for the "docker" and "prod" profiles, and a console pattern layout for the "default" profile.
3. Include traceId, spanId, service name, and timestamp in every log line.
4. Add MDC enrichment for userId (from X-Auth-Id header) in the AppAuthUserFilter.
```

### 3.5 Add Custom Health Checks

**Gap Reference:** 6.2 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators:
1. In user-service: add a KeycloakHealthIndicator that checks if the Keycloak server is reachable.
2. In fund-transfer-service and utility-payment-service: add a CoreBankingHealthIndicator that calls the core-banking-service actuator/health endpoint.
3. In all services: ensure management.endpoint.health.show-details=always is set for detailed health output.
4. In the API gateway: add a CompositeHealthIndicator that checks all downstream services.
```

### 3.6 Add Metrics Export and Business Metrics

**Gap Reference:** 6.3 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure Prometheus metrics:
1. Add micrometer-registry-prometheus to all services.
2. Expose /actuator/prometheus endpoint in application.yml.
3. Add custom business metrics using MeterRegistry:
   - core-banking-service: counter for fund_transfers_total, utility_payments_total with status tag; gauge for active_accounts.
   - fund-transfer-service: timer for fund_transfer_duration; counter for fund_transfer_failures.
   - utility-payment-service: timer for utility_payment_duration; counter for utility_payment_failures.
4. Add a Prometheus + Grafana service to docker-compose.yml with pre-configured dashboards.
```

### 3.7 Add Contract Tests Between Services

**Gap Reference:** 3.3 (Medium, Medium)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests:
1. Add spring-cloud-starter-contract-verifier to core-banking-service as a test dependency.
2. Write contract definitions for:
   - GET /api/v1/account/bank-account/{number} (success and not-found)
   - POST /api/v1/transaction/fund-transfer (success and insufficient funds)
   - POST /api/v1/transaction/util-payment (success)
   - GET /api/v1/user/{identification} (success and not-found)
3. Add spring-cloud-starter-contract-stub-runner to fund-transfer-service, user-service, and utility-payment-service.
4. Write consumer-side tests that verify Feign clients match the contracts.
```

### 3.8 Add Dependency Vulnerability Scanning

**Gap Reference:** 4.6 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add OWASP Dependency-Check to the build:
1. Add the org.owasp.dependencycheck Gradle plugin to all services.
2. Configure it with failBuildOnCVSS=7 to fail the build on high-severity CVEs.
3. Create a suppressions.xml for any known false positives.
4. Add a dependencyCheckAnalyze task to the CI pipeline.
```

### 3.9 Encrypt Config Server Secrets

**Gap Reference:** 4.5 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, enable encryption for sensitive properties in Spring Cloud Config Server:
1. Generate an encryption key and configure it in config-server's application.yml using encrypt.key (or a keystore).
2. In the external Git configuration repository, encrypt sensitive properties (database passwords, Keycloak client secret) using the {cipher} prefix.
3. Document the encryption setup in the README.
4. Ensure services can decrypt properties at startup.
```

### 3.10 Fix ResponseEntity Type Parameters

**Gap Reference:** 5.5 (Low, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add generic type parameters to all ResponseEntity return types across all controllers:
- AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- UserController (core-banking): ResponseEntity<User>, ResponseEntity<List<User>>
- FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- UtilityPaymentController: ResponseEntity<UtilityPaymentResponse>, ResponseEntity<List<UtilityPayment>>
This improves compile-time safety and OpenAPI spec generation.
```

### 3.11 Standardize URL Patterns

**Gap Reference:** 5.4 (Low, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, standardize URL path variable naming to use kebab-case and consistent patterns:
- core-banking-service AccountController: rename {account_number} to {accountNumber} and {account_name} to {providerName}.
- Ensure all path variables use camelCase consistently across all services.
- Update any Feign client @RequestMapping paths that reference these endpoints.
- Verify all Postman collection references are updated.
```

### 3.12 Inject Mappers as Spring Beans

**Gap Reference:** 1.3 (Medium, Small)

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, refactor all Mapper classes to be Spring-managed beans:
1. Add @Component to UserMapper, BankAccountMapper, UtilityAccountMapper (core-banking), FundTransferMapper (fund-transfer), UserMapper (user-service), UtilityPaymentMapper (utility-payment).
2. Replace inline instantiation (e.g., "private UserMapper userMapper = new UserMapper()") with constructor injection via @RequiredArgsConstructor.
3. Verify all existing tests still pass (update mocks where needed).
```

---

## Roadmap Summary

| Phase | Items | Estimated Timeline | Key Outcomes |
|---|---|---|---|
| **Phase 1: Quick Wins** | 8 items | 1–2 weeks | Secure error handling, input validation, timeout/retry for Feign, credential hygiene, no PII in logs |
| **Phase 2: Important** | 8 items | 3–6 weeks | Circuit breakers, transaction safety, idempotency, RBAC, code deduplication, unit test coverage |
| **Phase 3: Polish** | 12 items | 6–12 weeks | Integration/contract tests, structured logging, metrics, health checks, OpenAPI fix, production readiness |

### Priority Order Within Phases

**Phase 1 (execute in this order):**
1. Fix generic exception handler (security — stops information leakage)
2. Remove hardcoded credentials (security — immediate risk)
3. Stop logging sensitive data (compliance)
4. Fix HTTP status codes (correctness)
5. Add Bean Validation (security + correctness)
6. Fix error response format consistency
7. Add Feign timeouts (resilience)
8. Add Feign retry (resilience)

**Phase 2 (execute in this order):**
1. Fix transaction safety issues (data integrity — prevents balance corruption)
2. Add idempotency (data integrity — prevents double charges)
3. Add circuit breakers (availability)
4. Add fallback behavior (availability)
5. Add role-based authorization (security)
6. Add input validation beyond DTOs (security)
7. Consolidate duplicated code (maintainability)
8. Add unit tests for all services (quality assurance)

**Phase 3 (flexible ordering — execute based on team capacity):**
- Start with OpenAPI fix and pagination metadata (quick developer experience wins)
- Then structured logging and metrics (observability foundation)
- Then integration and contract tests (long-term quality)
- Finally dependency scanning and config encryption (security hardening)
