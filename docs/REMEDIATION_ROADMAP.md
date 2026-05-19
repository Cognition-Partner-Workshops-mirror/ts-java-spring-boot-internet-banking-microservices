# Internet Banking Microservices — Remediation Roadmap

## Phasing Strategy

| Phase | Focus | Selection Criteria |
|---|---|---|
| **Phase 1 — Quick Wins** | Critical/High severity + Small effort | Immediate risk reduction with minimal investment |
| **Phase 2 — Important** | High/Medium severity + Medium effort | Structural improvements that build on Phase 1 |
| **Phase 3 — Polish** | Lower severity or Large effort | Long-term quality and architecture improvements |

---

## Phase 1: Quick Wins (1–2 Sprints)

### 1.1 Fix Double-Subtraction Balance Bug (GAP-RES-06)

**Priority: P0 — Data corruption bug**

The `availableBalance` is subtracted twice in `TransactionService.utilPayment()` and `TransactionService.internalFundTransfer()`. This is an active correctness bug.

**Devin Prompt:**
```
Fix the double-subtraction bug in core-banking-service TransactionService.

In `utilPayment()`, line setting `availableBalance` subtracts from the already-subtracted
`actualBalance`. The `availableBalance` should be set to the same value as `actualBalance`
after subtraction (or calculated independently from the original balance).

The same pattern exists in `internalFundTransfer()` for both the source (debit) and
destination (credit) accounts.

Fix all occurrences, update the existing unit tests in TransactionServiceTest to verify
correct balance calculations, and ensure all tests pass.
```

---

### 1.2 Add Input Validation to All Request DTOs (GAP-SEC-02)

**Priority: P0 — Security gap**

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. core-banking-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

2. internet-banking-fund-transfer-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

3. internet-banking-utility-payment-service:
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

4. internet-banking-user-service:
   - User (registration): @NotBlank email, @Email email, @NotBlank identification, @NotBlank password

Add @Valid to all @RequestBody parameters in controllers.
Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns
HTTP 422 with a structured error response listing field-level errors.

Run all existing tests to ensure nothing breaks.
```

---

### 1.3 Fix HTTP Status Code Mapping (GAP-ERR-01)

**Priority: P1 — API correctness**

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all four business services to return proper HTTP status codes:

1. EntityNotFoundException → HTTP 404 (Not Found)
2. InsufficientFundsException → HTTP 422 (Unprocessable Entity)
3. SimpleBankingGlobalException → HTTP 400 (Bad Request)
4. General Exception → HTTP 500 (Internal Server Error) — do NOT expose exception details in the response body

Also ensure the catch-all Exception handler returns a structured ErrorResponse { code, message }
instead of a plain string. Use a generic error code like "INTERNAL_SERVER_ERROR" and message
"An unexpected error occurred".

Update the error response to always use the same ErrorResponse structure across all services.
Run all existing tests.
```

---

### 1.4 Externalize Hardcoded Credentials (GAP-SEC-01)

**Priority: P1 — Security**

**Devin Prompt:**
```
Replace all hardcoded credentials in docker-compose files and SQL scripts with environment
variables:

1. docker-compose/docker-compose.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD:-password}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD:-password}

2. docker-compose/docker-compose-support-apps.yml: Apply the same changes.

3. docker-compose/mysql/privileges.sql:
   - This file runs at container init. Document that the password should be changed
     for non-local environments. Add a comment at the top of the file.

4. Create a docker-compose/.env.example file with placeholder values and instructions.

5. Add docker-compose/.env to .gitignore.
```

---

### 1.5 Remove Sensitive Data from Logs (GAP-OBS-02)

**Priority: P1 — Security**

**Devin Prompt:**
```
Audit and fix all log statements across all services that log potentially sensitive data:

1. FundTransferController.java: Replace `request.toString()` with just the fromAccount
   and toAccount (no amount).
2. FundTransferService.java: Fix the string concatenation bug (uses + instead of {})
   and remove sensitive fields from the log.
3. UserController.java (user-service): Remove password from log output. Log only the email.
4. UtilityPaymentService.java: Remove full request object from log. Log only the account
   and providerId.
5. TransactionController.java (core-banking): Remove full request toString() from logs.

General rule: Never log amounts, passwords, or full request objects. Log only identifiers
(account numbers, user IDs, transaction IDs).
```

---

### 1.6 Fix Feign Timeout Configuration (GAP-RES-03)

**Priority: P1 — Reliability**

**Devin Prompt:**
```
Add explicit timeout configuration for all Feign clients across the three services that
use them (fund-transfer-service, utility-payment-service, user-service).

For each service, add to the application.yml (or the externalized config if you can
identify the config repo files):

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

This ensures requests fail fast rather than hanging for the default 60-second read timeout.
```

---

### 1.7 Fix OpenAPI Dependency (GAP-API-03)

**Priority: P2 — Developer experience**

**Devin Prompt:**
```
In the build.gradle files of core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, and internet-banking-utility-payment-service, replace:

    implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'

with:

    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (servlet), not WebFlux. The api-gateway should keep the
webflux dependency (but the gateway does not currently have this dependency, which is fine).

Build all services to verify no compilation errors.
```

---

### 1.8 Fix Raw ResponseEntity Types (GAP-SEC-03)

**Priority: P2 — Code quality**

**Devin Prompt:**
```
Add proper type parameters to all ResponseEntity return types across all controllers:

- AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- UserController (core-banking): ResponseEntity<User>, ResponseEntity<List<User>>
- UserController (user-service): already typed — verify
- FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- UtilityPaymentController: ResponseEntity<UtilityPaymentResponse>, ResponseEntity<List<UtilityPayment>>

Build all services to verify.
```

---

### 1.9 Fix Keycloak Singleton Thread Safety (GAP-SEC-04)

**Priority: P2 — Correctness**

**Devin Prompt:**
```
Fix the thread-safety issue in KeycloakProperties.getInstance() in
internet-banking-user-service.

Replace the manual singleton pattern with a Spring @Bean approach:

1. Remove the static `keycloakInstance` field and `getInstance()` method.
2. Add a @Bean method in KeycloakProperties (or a separate @Configuration class)
   that creates the Keycloak instance using KeycloakBuilder.
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance().

This leverages Spring's singleton scope for thread-safe initialization.
```

---

## Phase 2: Important (2–4 Sprints)

### 2.1 Add Circuit Breakers to Feign Clients (GAP-RES-01)

**Priority: P1 — Resilience**

**Devin Prompt:**
```
Add Resilience4j circuit breaker support to all Feign clients:

1. Add dependencies to fund-transfer-service, utility-payment-service, and user-service:
   - implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign in application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Create fallback factory classes for each Feign client that return meaningful
   error responses instead of propagating exceptions.

4. Configure circuit breaker defaults in application.yml:
   resilience4j:
     circuitbreaker:
       configs:
         default:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 10000
           permittedNumberOfCallsInHalfOpenState: 3

5. Add retry configuration:
   resilience4j:
     retry:
       configs:
         default:
           maxAttempts: 3
           waitDuration: 1000
```

---

### 2.2 Consolidate Common Code into banking-common (GAP-ORG-01)

**Priority: P1 — Maintainability**

**Devin Prompt:**
```
Move the following duplicated classes into the banking-common shared library module:

1. Exception classes: SimpleBankingGlobalException, ErrorResponse, GlobalErrorCode,
   EntityNotFoundException, GlobalExceptionHandler
2. Audit classes: AuditAware, AuditConfig, AuditorAwareConfig
3. Filter classes: ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
4. Mapper base: BaseMapper

Update each service's build.gradle to depend on banking-common. Remove the duplicated
classes from each service. Ensure all services build and tests pass.
```

---

### 2.3 Add Pagination Metadata to List Responses (GAP-API-01)

**Priority: P2 — API quality**

**Devin Prompt:**
```
Update all list endpoints to return pagination metadata instead of bare lists:

1. Create a generic PageResponse<T> class in banking-common:
   - content: List<T>
   - pageNumber: int
   - pageSize: int
   - totalElements: long
   - totalPages: int

2. Update these endpoints to return PageResponse<T>:
   - GET /api/v1/user (core-banking)
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/transfer (fund-transfer-service)
   - GET /api/v1/utility-payment (utility-payment-service)

3. Update service methods to return the Page object's metadata along with the content.
```

---

### 2.4 Add Unit Tests for All Services (GAP-TEST-01)

**Priority: P1 — Quality**

**Devin Prompt:**
```
Add comprehensive unit tests for the three business services that currently lack them:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, Feign client failure handling,
     readAllTransfers pagination
   - FundTransferControllerTest: @WebMvcTest with mocked service

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success, Feign client failure,
     readPayments pagination
   - UtilityPaymentControllerTest: @WebMvcTest with mocked service

3. internet-banking-user-service:
   - UserServiceTest: test createUser happy path, duplicate email, invalid email,
     user not found in core banking, updateUser approval flow, readUsers
   - UserControllerTest: @WebMvcTest with mocked service

Use JUnit 5 + Mockito. For controller tests, use MockMvc. Add H2 test configuration
where needed.
```

---

### 2.5 Add Idempotency to Fund Transfers (GAP-RES-05)

**Priority: P1 — Data integrity**

**Devin Prompt:**
```
Add idempotency support to the fund transfer flow:

1. Add an optional `idempotencyKey` field to FundTransferRequest in both
   fund-transfer-service and core-banking-service.

2. In FundTransferService.fundTransfer():
   - Before creating a new entity, check if a FundTransferEntity with the same
     idempotencyKey already exists.
   - If it exists and status is SUCCESS, return the existing response.
   - If it exists and status is PENDING/PROCESSING, return a 409 Conflict.
   - Add a unique index on idempotencyKey in FundTransferEntity.

3. Apply the same pattern to utility-payment-service.
```

---

### 2.6 Add Structured Logging (GAP-OBS-01)

**Priority: P2 — Observability**

**Devin Prompt:**
```
Configure structured JSON logging for all services:

1. Add logstash-logback-encoder dependency to all business services:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a shared logback-spring.xml in each service's src/main/resources/ that:
   - Uses LogstashEncoder for JSON output in production profiles
   - Uses standard PatternLayout for local development
   - Includes MDC fields for traceId, spanId, serviceName

3. Ensure log output includes: timestamp, level, logger, message, traceId, spanId,
   service name, and any MDC context.
```

---

### 2.7 Implement Error Handling for Saga Failure (GAP-RES-07 — Partial)

**Priority: P1 — Reliability**

**Devin Prompt:**
```
Add basic failure handling to the fund-transfer and utility-payment orchestration flows:

1. In FundTransferService.fundTransfer():
   - Wrap the Feign call in a try-catch
   - On failure, update the FundTransferEntity status to FAILED
   - Log the failure with the transaction reference
   - Return a meaningful error response to the client

2. In UtilityPaymentService.utilPayment():
   - Same pattern: catch Feign exceptions, update status to FAILED, return error

This is a minimal improvement — a full saga/outbox pattern is Phase 3.
```

---

### 2.8 Add Custom Health Indicators (GAP-OBS-03)

**Priority: P3 — Observability**

**Devin Prompt:**
```
Add custom HealthIndicator beans to business services:

1. core-banking-service: DatabaseHealthIndicator (verify MySQL connection)
2. user-service: KeycloakHealthIndicator (verify Keycloak connectivity)
3. fund-transfer-service: CoreBankingHealthIndicator (verify core-banking-service
   is reachable via Feign)
4. utility-payment-service: CoreBankingHealthIndicator (same)

Each indicator should do a lightweight check (e.g., SELECT 1 for DB, /actuator/health
for services) and report UP/DOWN status.

Also expose health details in application config:
management.endpoint.health.show-details: always
```

---

## Phase 3: Polish (3–6 Sprints)

### 3.1 Add Integration Tests with Testcontainers (GAP-TEST-02)

**Priority: P2 — Quality**

**Devin Prompt:**
```
Add integration tests using Testcontainers for all business services:

1. Add Testcontainers dependencies:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service:
   - Create an integration test class that starts a MySQL container
   - Verify Flyway migrations run successfully
   - Test full API flow: create user → create account → fund transfer → verify balances

3. For user-service:
   - Mock Keycloak with WireMock or use a Keycloak Testcontainer
   - Test user registration and approval flow

4. For fund-transfer-service and utility-payment-service:
   - Use WireMock to mock core-banking-service responses
   - Test full orchestration flow including failure scenarios
```

---

### 3.2 Add Contract Tests (GAP-TEST-03)

**Priority: P2 — Quality**

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. Add Spring Cloud Contract dependencies:
   - Provider side (core-banking-service): spring-cloud-starter-contract-verifier
   - Consumer side (fund-transfer, utility-payment, user): spring-cloud-contract-stub-runner

2. Define contracts in core-banking-service for:
   - GET /api/v1/account/bank-account/{number} → account response
   - GET /api/v1/user/{identification} → user response
   - POST /api/v1/transaction/fund-transfer → success/failure responses
   - POST /api/v1/transaction/util-payment → success/failure responses

3. Generate stubs from core-banking contracts and use them in consumer service tests.
```

---

### 3.3 Create Root Multi-Module Gradle Build (GAP-ORG-03)

**Priority: P3 — Developer experience**

**Devin Prompt:**
```
Create a root settings.gradle and build.gradle that includes all services as subprojects:

1. Root settings.gradle:
   - Include all 8 service directories and banking-common as subprojects

2. Root build.gradle:
   - Define common configuration (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0)
     in a subprojects {} block
   - Centralize dependency version management using a version catalog (libs.versions.toml)
   - Apply common plugins (java, spring-boot, dependency-management) to all subprojects

3. Ensure `./gradlew build` from the root builds all services.
4. Keep individual gradlew wrappers working for per-service builds.
```

---

### 3.4 Add Dependency Vulnerability Scanning (GAP-SEC-06)

**Priority: P2 — Security**

**Devin Prompt:**
```
Add OWASP Dependency-Check to the build:

1. Add the OWASP Dependency-Check Gradle plugin to each service's build.gradle
   (or to the root build.gradle if it exists by this point):
   id 'org.owasp.dependencycheck' version '9.1.0'

2. Configure the plugin:
   dependencyCheck {
     failBuildOnCVSS = 7
     formats = ['HTML', 'JSON']
   }

3. Add a GitHub Actions workflow (.github/workflows/dependency-check.yml) that:
   - Runs on push to main and on PRs
   - Executes `./gradlew dependencyCheckAnalyze` for all services
   - Uploads the HTML report as an artifact
```

---

### 3.5 Implement Full Saga Pattern with Outbox (GAP-RES-07 — Full)

**Priority: P2 — Architecture**

**Devin Prompt:**
```
Implement the transactional outbox pattern for fund transfers and utility payments:

1. Create an `outbox_event` table in fund-transfer-service and utility-payment-service:
   - id, aggregate_type, aggregate_id, event_type, payload (JSON), status, created_at

2. When initiating a transfer/payment:
   - Save the FundTransferEntity and an OutboxEvent in the SAME transaction
   - A background scheduler picks up PENDING outbox events and sends them to
     core-banking-service
   - On success: mark event as PROCESSED, update entity status to SUCCESS
   - On failure: mark event as FAILED, update entity status to FAILED
   - On repeated failure: implement exponential backoff

3. This decouples the local transaction from the remote call, ensuring consistency.
```

---

### 3.6 Add Prometheus Metrics and Dashboards (GAP-OBS-04)

**Priority: P3 — Observability**

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add dependency: implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the Prometheus endpoint in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,prometheus,info
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom metrics:
   - Counter: fund_transfers_total (tagged by status: success/failed)
   - Counter: utility_payments_total (tagged by status)
   - Timer: feign_request_duration (for inter-service calls)
   - Gauge: active_pending_transfers

4. Add a Prometheus service to docker-compose.yml that scrapes all service endpoints.
5. Optionally add a Grafana service with a pre-configured dashboard.
```

---

### 3.7 Standardize Package Structure (GAP-ORG-02)

**Priority: P3 — Maintainability**

**Devin Prompt:**
```
Standardize the package structure across all business services to follow this convention:

com.javatodev.finance
├── configuration/      # Spring @Configuration classes, security, Feign config
├── controller/         # REST controllers
├── exception/          # Exception classes and handlers
├── model/
│   ├── dto/            # Data Transfer Objects (request/response)
│   ├── entity/         # JPA entities
│   ├── enums/          # Enumerations
│   └── mapper/         # Entity ↔ DTO mappers
├── repository/         # Spring Data JPA repositories
└── service/
    └── client/         # Feign clients and external service integrations

Move classes as needed. Update all imports. Build and test all services.
```

---

### 3.8 Add API Filtering Capabilities (GAP-API-04)

**Priority: P3 — Usability**

**Devin Prompt:**
```
Add filtering support to list endpoints:

1. GET /api/v1/transfer:
   - Filter by status (PENDING, SUCCESS, FAILED)
   - Filter by fromAccount or toAccount
   - Filter by date range (createdAfter, createdBefore)

2. GET /api/v1/utility-payment:
   - Filter by status
   - Filter by account
   - Filter by providerId

3. GET /api/v1/bank-users:
   - Filter by status (PENDING, APPROVED)

Use Spring Data JPA Specifications or QueryDSL for dynamic filtering.
Accept filter parameters as query parameters.
```

---

## Summary Timeline

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 9 items | 1–2 sprints | Critical bugs fixed, security gaps closed, basic error handling corrected |
| **Phase 2** | 8 items | 2–4 sprints | Resilience patterns in place, test coverage improved, observability enhanced |
| **Phase 3** | 8 items | 3–6 sprints | Architecture modernized, full test pyramid, production-grade observability |
