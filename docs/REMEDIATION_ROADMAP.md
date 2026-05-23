# Internet Banking Microservices — Remediation Roadmap

This roadmap prioritizes the 32 gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on severity and effort. Each item includes an actionable **Devin prompt** that can be executed directly.

---

## Phase 1: Quick Wins (Critical/High Severity + Small/Medium Effort)

These items address the most impactful issues with the least effort. They should be tackled first.

---

### 1.1 Fix Balance Calculation Bug (GAP-7.7)

**Severity: Critical | Effort: Small**

The `availableBalance` is double-subtracted in `TransactionService.utilPayment()` and `internalFundTransfer()`. The `setAvailableBalance` call subtracts from the already-reduced `actualBalance`.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java. In both the utilPayment() and internalFundTransfer() methods, the availableBalance is being set to actualBalance.subtract(amount) AFTER actualBalance has already been reduced by the same amount. This causes a double-subtraction. The fix is to set availableBalance equal to the new actualBalance (not subtract amount again). Update the existing unit tests in TransactionServiceTest.java to verify the correct balance calculation. Run tests to confirm.
```

---

### 1.2 Fix Error Handling — Proper HTTP Status Codes (GAP-2.1, GAP-2.2, GAP-2.3)

**Severity: Critical/High | Effort: Small**

All errors currently return HTTP 400. The generic exception handler also leaks stack traces.

**Devin Prompt:**
```
Refactor the GlobalExceptionHandler in all 4 business services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service) to:
1. Return HTTP 404 for EntityNotFoundException
2. Return HTTP 422 for InsufficientFundsException, InvalidEmailException, InvalidBankingUserException, UserAlreadyRegisteredException
3. Return HTTP 500 for the catch-all Exception handler with a generic error message (do NOT include the exception details or stack trace in the response)
4. Always return the structured ErrorResponse {code, message} format, never a plain string
5. Add appropriate error codes for each exception type
Run existing tests to verify nothing breaks.
```

---

### 1.3 Externalize Hardcoded Credentials (GAP-4.2)

**Severity: Critical | Effort: Small**

Database passwords, Keycloak admin credentials, and user passwords are committed in plaintext.

**Devin Prompt:**
```
Externalize all hardcoded credentials in docker-compose/docker-compose.yml, docker-compose/docker-compose-support-apps.yml, and docker-compose/mysql/Dockerfile to use environment variables with .env file:
1. Create a docker-compose/.env.example file with placeholder values for: MYSQL_ROOT_PASSWORD, MYSQL_APP_USER, MYSQL_APP_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD
2. Update docker-compose.yml and docker-compose-support-apps.yml to reference these env vars (e.g., ${MYSQL_ROOT_PASSWORD})
3. Update the MySQL Dockerfile to use ARG/ENV pattern instead of hardcoded password
4. Add .env to .gitignore
5. Update the README.md with instructions to copy .env.example to .env and set values
Do not commit any actual secret values.
```

---

### 1.4 Add Request Validation (GAP-2.4, GAP-4.3)

**Severity: High | Effort: Small**

No `@Valid` or Bean Validation annotations exist on any request DTOs.

**Devin Prompt:**
```
Add Bean Validation (Jakarta Validation) to all request DTOs and controllers across all services:
1. Add spring-boot-starter-validation dependency to build.gradle for core-banking-service, fund-transfer-service, utility-payment-service, and user-service
2. Add @NotNull, @NotBlank, @Positive, @Size annotations to:
   - FundTransferRequest: fromAccount (not blank), toAccount (not blank), amount (positive, not null)
   - UtilityPaymentRequest: providerId (not null), amount (positive, not null), referenceNumber (not blank), account (not blank)
   - User (user-service): email (valid email, not blank), identification (not blank), password (not blank, min 6 chars)
3. Add @Valid annotation to all @RequestBody parameters in controllers
4. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler that returns HTTP 400 with field-level error details
5. Run existing tests to confirm nothing breaks.
```

---

### 1.5 Fix OpenAPI Dependency (GAP-5.3)

**Severity: Medium | Effort: Small**

MVC services incorrectly use the WebFlux OpenAPI dependency.

**Devin Prompt:**
```
In core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service, change the OpenAPI dependency in build.gradle from:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
These are Spring Web MVC services, not WebFlux. Build all 4 services to confirm compilation succeeds.
```

---

### 1.6 Stop Leaking Sensitive Data in Logs (GAP-6.5)

**Severity: High | Effort: Small**

Controllers log full request objects including PII and financial data.

**Devin Prompt:**
```
Audit all log statements across all services and remove or redact sensitive information:
1. In FundTransferController: log only "Fund transfer initiated" without the full request object
2. In UtilityPaymentService: log only "Utility payment processing" without request details
3. In UserController (user-service): log only "Creating user" without the full request (which includes password)
4. In TransactionController: log "Fund transfer initiated" and "Utility payment initiated" without full request objects
5. Ensure no account numbers, amounts, passwords, or NIC identifiers appear in log output
6. Run existing tests to confirm nothing breaks.
```

---

### 1.7 Add Feign Timeout Configuration (GAP-7.3)

**Severity: High | Effort: Small**

No connection or read timeouts configured for Feign clients.

**Devin Prompt:**
```
Add Feign timeout configuration to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service:
1. Add timeout configuration in each service's application.yml:
   spring.cloud.openfeign.client.config.default.connect-timeout: 5000
   spring.cloud.openfeign.client.config.default.read-timeout: 10000
2. These values ensure connections time out after 5 seconds and reads after 10 seconds
3. Build all services to confirm no errors.
```

---

### 1.8 Add Feign Retry Policy (GAP-7.2)

**Severity: Medium | Effort: Small**

No retry configuration for transient failures.

**Devin Prompt:**
```
Add Feign retry configuration to the 3 services that use Feign clients (fund-transfer, utility-payment, user-service):
1. Add a Retryer bean to each service's configuration that retries up to 3 times with 1-second initial backoff and 3-second max backoff
2. Only retry on connection-level exceptions (not on 4xx/5xx responses, as financial operations should not be blindly retried)
3. Add appropriate comments explaining the retry behavior.
```

---

### 1.9 Add Typed ResponseEntity to Controllers (GAP-5.1)

**Severity: Medium | Effort: Small**

Controllers return raw `ResponseEntity` without type parameters.

**Devin Prompt:**
```
Update all controller methods in core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service to use properly typed ResponseEntity generics. For example:
- ResponseEntity readUser() → ResponseEntity<User> readUser()
- ResponseEntity readBankAccount() → ResponseEntity<BankAccount> readBankAccount()
Update all return types in AccountController, UserController, TransactionController (core), FundTransferController, UtilityPaymentController. Build all services to confirm compilation.
```

---

### 1.10 Fix Keycloak Client Thread Safety (GAP-4.5)

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses an unsynchronized lazy singleton.

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.java. The static Keycloak instance uses lazy initialization without synchronization. Convert it to a Spring @Bean approach:
1. Make KeycloakProperties a @ConfigurationProperties class
2. Create a @Configuration class that provides a singleton Keycloak @Bean using KeycloakBuilder
3. Remove the static field and getInstance() method
4. Inject the Keycloak bean directly where needed
5. Run tests to ensure nothing breaks.
```

---

### 1.11 Add Pagination Metadata (GAP-5.2)

**Severity: Medium | Effort: Small**

List endpoints return `List<T>` instead of paginated responses.

**Devin Prompt:**
```
Update all paginated endpoints to return proper pagination metadata:
1. Change return types from List<T> to Page<T> (or create a PageResponse wrapper DTO with content, totalElements, totalPages, currentPage, size)
2. Update these endpoints:
   - GET /api/v1/user (core-banking)
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/transfer (fund-transfer)
   - GET /api/v1/utility-payment (utility-payment)
3. Update the service layer methods to return Page<T> instead of List<T>
4. Update existing tests if needed. Build all services.
```

---

### 1.12 Add Structured Logging (GAP-6.1)

**Severity: Medium | Effort: Small**

No JSON logging configured for production environments.

**Devin Prompt:**
```
Add structured JSON logging for all services:
1. Add logback-spring.xml to src/main/resources of all 6 services
2. Configure two profiles: "default" (console, human-readable) and "docker" (JSON format using logstash-logback-encoder)
3. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency to all services
4. Include traceId and spanId in the JSON log output (MDC context from Micrometer Tracing)
5. Build all services to confirm.
```

---

### 1.13 Configure Actuator Health Checks (GAP-6.2)

**Severity: Medium | Effort: Small**

Actuator is included but not configured.

**Devin Prompt:**
```
Configure Spring Boot Actuator health checks for all services:
1. In application.yml for each service, add:
   management.endpoints.web.exposure.include: health,info,metrics,prometheus
   management.endpoint.health.show-details: always
2. For services with database connections (core-banking, fund-transfer, utility-payment, user-service), the auto-configured DataSourceHealthIndicator will report database health
3. Add healthcheck configuration to docker-compose.yml for each service container using the /actuator/health endpoint
4. Build all services.
```

---

## Phase 2: Important (High Severity + Medium/Large Effort)

These items require more work but are important for production readiness.

---

### 2.1 Create Shared Library Module (GAP-1.1)

**Severity: High | Effort: Medium**

Extract duplicated code into a shared library.

**Devin Prompt:**
```
Create a shared library module called banking-common:
1. Create a new Gradle module at the root: banking-common/ with its own build.gradle
2. Move these common classes into banking-common:
   - BaseMapper, AuditAware, ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - TransactionStatus enum, CustomFeignClientConfiguration
   - AuditConfig, AuditorAwareConfig
3. Update all 4 business services to depend on banking-common
4. Remove the duplicated classes from each service
5. Build all services to confirm compilation
6. Run all existing tests.
```

---

### 2.2 Add Circuit Breakers (GAP-7.1, GAP-7.4)

**Severity: High | Effort: Medium**

No circuit breakers or fallback behavior for Feign clients.

**Devin Prompt:**
```
Add Resilience4j circuit breakers to the 3 Feign client services:
1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to fund-transfer, utility-payment, and user-service build.gradle
2. Enable Feign circuit breaker support: spring.cloud.openfeign.circuitbreaker.enabled=true in application.yml
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return error response for fundTransfer and readAccount
   - BankingCoreRestClientFallback (utility-payment): return error response for utilityPayment and readAccount
   - BankingCoreRestClientFallback (user-service): return error response for readUser
4. Configure circuit breaker parameters: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s
5. Build and run tests.
```

---

### 2.3 Add Idempotency Protection (GAP-7.5)

**Severity: High | Effort: Medium**

No duplicate transaction protection.

**Devin Prompt:**
```
Add idempotency key support to fund-transfer and utility-payment services:
1. Add an X-Idempotency-Key header requirement to POST endpoints in FundTransferController and UtilityPaymentController
2. Create an idempotency_key table in each service's database with columns: key (VARCHAR, UNIQUE), response (TEXT), created_at (TIMESTAMP)
3. Before processing a request, check if the idempotency key exists. If yes, return the stored response. If no, process and store the response with the key
4. Add TTL-based cleanup (e.g., delete keys older than 24 hours)
5. Add unit tests for idempotency behavior.
```

---

### 2.4 Secure Downstream Services (GAP-4.1)

**Severity: Critical | Effort: Medium**

Downstream services have no authentication.

**Devin Prompt:**
```
Add JWT validation to all downstream services so they don't solely rely on the X-Auth-Id header:
1. Add spring-boot-starter-oauth2-resource-server dependency to core-banking-service, fund-transfer-service, utility-payment-service, and user-service
2. Configure each service as an OAuth2 resource server validating JWTs from Keycloak
3. Add SecurityFilterChain that:
   - Permits /actuator/** endpoints
   - Requires authentication for all other endpoints
4. Update Feign client configurations to propagate the JWT Bearer token from incoming requests to outgoing Feign calls (via a RequestInterceptor)
5. Build and test all services.
```

---

### 2.5 Add Unit Tests for All Services (GAP-3.1)

**Severity: High | Effort: Large**

5 of 6 services have no meaningful tests.

**Devin Prompt:**
```
Add comprehensive unit tests for all services that currently lack them:
1. internet-banking-fund-transfer-service: Test FundTransferService (fundTransfer success, failure, readAllTransfers)
2. internet-banking-utility-payment-service: Test UtilityPaymentService (utilPayment success, failure, readPayments)
3. internet-banking-user-service: Test UserService (createUser with various scenarios, readUsers, readUser, updateUser)
4. Use Mockito to mock Feign clients, repositories, and Keycloak service
5. Test both happy paths and error cases (entity not found, insufficient funds, duplicate email, etc.)
6. Target at least 80% line coverage for service classes
7. Run all tests and confirm they pass.
```

---

### 2.6 Add Integration Tests (GAP-3.2)

**Severity: High | Effort: Large**

No integration tests exist.

**Devin Prompt:**
```
Add integration tests for core-banking-service using Spring Boot Test and H2:
1. Add @SpringBootTest integration tests that test actual HTTP endpoints using TestRestTemplate or MockMvc
2. Use the existing H2 test dependency for in-memory database
3. Test the full request-response cycle for:
   - GET /api/v1/account/bank-account/{account_number}
   - GET /api/v1/user/{identification}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
4. Create test data fixtures using Flyway test migrations or @Sql annotations
5. Verify response status codes, response body structure, and database state changes
6. Run tests and confirm they pass.
```

---

## Phase 3: Polish (Lower Severity or High Effort Items)

These items improve overall code quality and maintainability but are lower priority.

---

### 3.1 Implement Saga Pattern for Distributed Transactions (GAP-7.6)

**Severity: Critical | Effort: Large**

Financial operations span multiple services without consistency guarantees.

**Devin Prompt:**
```
Implement a choreography-based saga pattern for fund transfers:
1. Add a status field to track saga state: INITIATED → CORE_PROCESSING → COMPLETED / COMPENSATION_NEEDED → COMPENSATED
2. In FundTransferService, wrap the core-banking call in try-catch:
   - On success: update to COMPLETED
   - On failure: update to COMPENSATION_NEEDED, trigger compensation (revert local record)
3. Add a scheduled job that checks for COMPENSATION_NEEDED records and retries compensation
4. Add a transaction_events audit table to log each state transition
5. Add unit tests for both success and compensation flows.
```

---

### 3.2 Add Multi-Module Root Build (GAP-1.2)

**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
Create a root settings.gradle that includes all service modules for unified builds:
1. Create /settings.gradle that includes all 6 service directories and banking-common
2. Create a root build.gradle with common configuration (Java 21, Spring Boot version, common dependencies)
3. Ensure ./gradlew build at the root builds all modules
4. Keep individual service builds working independently as well.
```

---

### 3.3 Standardize Package Structure (GAP-1.3)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Standardize the package structure across all services to follow a consistent pattern:
- com.javatodev.finance.controller
- com.javatodev.finance.service
- com.javatodev.finance.repository
- com.javatodev.finance.model.entity
- com.javatodev.finance.model.dto
- com.javatodev.finance.model.dto.request
- com.javatodev.finance.model.dto.response
- com.javatodev.finance.model.mapper
- com.javatodev.finance.exception
- com.javatodev.finance.configuration
Specifically, move utility-payment's repository package from com.javatodev.finance.repository to com.javatodev.finance.model.repository (or vice versa — pick one convention and apply everywhere). Same for rest client packages.
```

---

### 3.4 Add Contract Tests (GAP-3.3)

**Severity: Medium | Effort: Large**

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:
1. Add spring-cloud-contract-verifier and spring-cloud-contract-stub-runner dependencies
2. In core-banking-service (the provider), define contract DSLs for:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}
3. In consumer services (fund-transfer, utility-payment, user-service), add stub runner tests that validate against the contracts
4. Run both provider and consumer tests.
```

---

### 3.5 Add Custom Business Metrics (GAP-6.3)

**Severity: Low | Effort: Medium**

**Devin Prompt:**
```
Add custom Micrometer metrics to business services:
1. In core-banking-service TransactionService:
   - Counter: banking.fund_transfer.total (tags: status=success|failure)
   - Counter: banking.utility_payment.total (tags: status=success|failure)
   - Gauge: banking.transaction.amount.total
2. In fund-transfer-service: Counter for fund_transfer.initiated, fund_transfer.completed
3. In utility-payment-service: Counter for utility_payment.initiated, utility_payment.completed
4. Expose via /actuator/prometheus endpoint
5. Add Prometheus service to docker-compose.yml to scrape metrics.
```

---

### 3.6 Add Centralized Log Aggregation (GAP-6.4)

**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
Add ELK stack (Elasticsearch, Logstash, Kibana) or Loki+Grafana to docker-compose for centralized log aggregation:
1. Add Loki and Grafana services to docker-compose-support-apps.yml
2. Configure the Loki Docker logging driver for all service containers
3. Add a Grafana dashboard for log exploration
4. Document the Grafana URL and default credentials in README.md.
```

---

### 3.7 Standardize Endpoint Naming (GAP-5.5)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Standardize API endpoint naming across services:
1. Rename /api/v1/account/util-account to /api/v1/account/utility-account (no abbreviations)
2. Rename /api/v1/transaction/util-payment to /api/v1/transaction/utility-payment
3. Update corresponding Feign client mappings in utility-payment-service and fund-transfer-service
4. Update Postman collection if present
5. Build and test all services.
```

---

### 3.8 Document CSRF Decision (GAP-4.4)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Add a comment in SecurityConfiguration.java explaining why CSRF is disabled:
"CSRF protection is disabled because this API uses stateless JWT Bearer token authentication. CSRF attacks rely on browser-based cookie sessions, which are not used here."
```

---

### 3.9 Add API Versioning Strategy Documentation (GAP-5.4)

**Severity: Low | Effort: Small**

**Devin Prompt:**
```
Add an API_VERSIONING.md document to the docs/ folder that describes:
1. Current versioning: URL path prefix /api/v1/
2. Strategy for introducing v2: new URL prefix /api/v2/ with backward compatibility period
3. Deprecation policy: v1 supported for 6 months after v2 release
4. Header-based versioning as a future alternative.
```

---

## Phase Summary

| Phase | Items | Focus |
|-------|-------|-------|
| Phase 1 | 13 items | Quick wins — critical bugs, security, error handling, basic resilience |
| Phase 2 | 6 items | Important — shared library, circuit breakers, testing, downstream auth |
| Phase 3 | 9 items | Polish — saga pattern, contract tests, metrics, log aggregation |

### Recommended Execution Order (Phase 1)

1. **GAP-7.7** — Fix balance calculation bug (immediate data corruption risk)
2. **GAP-4.2** — Externalize hardcoded credentials
3. **GAP-2.1/2.2/2.3** — Fix error handling
4. **GAP-2.4/4.3** — Add request validation
5. **GAP-6.5** — Stop leaking sensitive data in logs
6. **GAP-5.3** — Fix OpenAPI dependency
7. **GAP-7.3** — Add Feign timeouts
8. **GAP-4.5** — Fix Keycloak thread safety
9. **GAP-5.1** — Add typed ResponseEntity
10. **GAP-5.2** — Add pagination metadata
11. **GAP-6.1** — Structured logging
12. **GAP-6.2** — Actuator health checks
13. **GAP-7.2** — Feign retry policy
