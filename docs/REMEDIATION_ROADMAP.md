# Remediation Roadmap

## Overview

This roadmap prioritizes the 39 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins**: Critical/High severity + Small effort. Immediate safety and correctness fixes.
- **Phase 2 — Important**: Critical/High severity + Medium effort. Structural improvements for production readiness.
- **Phase 3 — Polish**: Medium/Low severity improvements for maintainability and developer experience.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort fixes that address critical bugs and security issues.

### 1.1 Fix Balance Calculation Bug (Critical / Small)

**Gap #39** — `TransactionService.internalFundTransfer()` and `utilPayment()` double-subtract from `availableBalance`.

```
Devin Prompt:
Fix the balance calculation bug in core-banking-service TransactionService.
In internalFundTransfer(), the availableBalance is set to actualBalance.subtract(amount)
AFTER actualBalance has already been decremented, causing double subtraction.
The correct logic should set availableBalance = actualBalance (after the debit/credit),
not actualBalance - amount. Same bug exists in utilPayment().
Fix both methods and update the existing TransactionServiceTest to verify
the corrected balance values after a fund transfer and utility payment.
```

### 1.2 Stop Logging Sensitive Data (Critical / Small)

**Gap #21** — Passwords, account numbers, and amounts logged in plain text via `toString()`.

```
Devin Prompt:
In internet-banking-user-service UserController.createUser(), the User object (which
contains a password field) is logged via toString(). Remove the password field from
logging. In the User DTO class, add @ToString.Exclude on the password field using Lombok.
Also review all controllers in fund-transfer-service and utility-payment-service — replace
request.toString() in log statements with only the non-sensitive identifiers (e.g.,
log the fromAccount and toAccount but not the full request). Add @JsonProperty(access =
WRITE_ONLY) on User.password so it is never serialized in responses.
```

### 1.3 Fix HTTP Status Codes in Exception Handlers (High / Small)

**Gap #9** — All exceptions return HTTP 400 regardless of error type.

```
Devin Prompt:
Update GlobalExceptionHandler in all three services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service) to return
appropriate HTTP status codes:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity
- SimpleBankingGlobalException → 400 Bad Request (keep as-is)
- Generic Exception → 500 Internal Server Error (not 400)
Also change the generic exception handler to return a structured ErrorResponse
object instead of a plain string, so stack traces are never leaked to clients.
```

### 1.4 Fix Generic Exception Handler Leaking Stack Traces (High / Small)

**Gap #8** — The catch-all handler returns `"Exception occur inside API " + e` which includes the full exception and potentially stack trace.

```
Devin Prompt:
In the GlobalExceptionHandler of core-banking-service, internet-banking-fund-transfer-service,
and internet-banking-user-service, change the generic Exception handler to:
1. Log the full exception at ERROR level (for debugging)
2. Return a structured ErrorResponse with code "INTERNAL_ERROR" and a generic message
   "An unexpected error occurred" — never include the exception details in the response
3. Return HTTP 500 instead of 400
```

### 1.5 Add Feign Error Decoder to Fund Transfer and Utility Payment Services (High / Small)

**Gap #10** — Only user-service has a proper Feign error decoder.

```
Devin Prompt:
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, following the same pattern as the existing
one in internet-banking-user-service (CustomFeignErrorDecoder.java). Register it
in the CustomFeignClientConfiguration class of each service. The decoder should:
1. Parse the ErrorResponse JSON from core-banking-service error responses
2. Re-throw as SimpleBankingGlobalException for 400 errors
3. Throw appropriate exceptions for 401 and 404
```

### 1.6 Add Feign Client Timeouts (High / Small)

**Gap #36** — No explicit timeouts on Feign clients.

```
Devin Prompt:
Add explicit connection and read timeouts to all Feign clients in this project.
In each service's application.yml (via the centralized config server), add:
  spring.cloud.openfeign.client.config.default.connect-timeout: 5000
  spring.cloud.openfeign.client.config.default.read-timeout: 10000
Also add these to each service's test application.yml. Since config is managed
externally, add a comment in each service's local application.yml noting the
expected Feign timeout configuration.
```

### 1.7 Add Retry Policies for Feign Clients (High / Small)

**Gap #35** — No retry on transient failures.

```
Devin Prompt:
Add Spring Retry support to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service. Add spring-retry and spring-boot-starter-aop
dependencies to each build.gradle. Enable @EnableRetry on the main application class.
Configure Feign retry with a Retryer bean in CustomFeignClientConfiguration that retries
up to 3 times with 1-second intervals for connection failures only (not for HTTP errors,
which should not be retried).
```

### 1.8 Configure Prometheus Metrics Endpoint (Medium / Small)

**Gap #32** — Prometheus listed in tech stack but not configured.

```
Devin Prompt:
Add micrometer-registry-prometheus dependency to the build.gradle of all application
services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service)
and the API gateway. Add the following to each service's actuator configuration
(via config server or local application.yml):
  management.endpoints.web.exposure.include: health,info,prometheus,metrics
  management.endpoint.prometheus.enabled: true
Verify the /actuator/prometheus endpoint returns Prometheus-format metrics.
```

### 1.9 Enable Structured JSON Logging (Medium / Small)

**Gap #28** — No structured logging for aggregation.

```
Devin Prompt:
Add structured JSON logging to all services in this project. Add the
spring-boot-starter-logging dependency (if not already included via starter-web)
and configure Logback to use JSON format. Create a shared logback-spring.xml that
outputs JSON format in the 'docker' profile and plain text in the default profile.
Include fields: timestamp, level, logger, message, traceId, spanId, service.
Place this in each service's src/main/resources/ directory.
```

### 1.10 Fix Inconsistent Log Formatting (Low / Small)

**Gap #29** — Mix of `{}` placeholder and string concatenation.

```
Devin Prompt:
In internet-banking-fund-transfer-service FundTransferService.java, line 32 has:
log.info("Sending fund transfer request {}" + request.toString())
This should use SLF4J placeholder syntax. Fix it to:
log.info("Sending fund transfer request {}", request)
Search all Java files in the project for similar string concatenation in log
statements and fix them to use {} placeholders.
```

### 1.11 Add Dependency Vulnerability Scanning (High / Small)

**Gap #19** — No automated vulnerability checks.

```
Devin Prompt:
Add the OWASP Dependency Check Gradle plugin to all services in this project.
Add to each build.gradle:
  plugins { id 'org.owasp.dependencycheck' version '10.0.3' }
  dependencyCheck { failBuildOnCVSS = 7 }
Also update springdoc-openapi-starter-webflux-ui from 2.1.0 to 2.6.0 (latest stable)
across all build.gradle files that include it.
```

### 1.12 Add Custom Health Indicators (Medium / Small)

**Gap #30** — No health checks for external dependencies.

```
Devin Prompt:
Add custom health indicators to the relevant services:
1. core-banking-service: MySQL connectivity check (Spring Data auto-configures this,
   but verify it's exposed)
2. internet-banking-user-service: Keycloak reachability health indicator (check if
   the Keycloak server-url is reachable)
3. All services: Eureka registration status check
Configure management.endpoint.health.show-details=always for the docker profile.
```

---

## Phase 2: Important (2–4 weeks)

Structural improvements required for production readiness.

### 2.1 Add Input Validation to All API Endpoints (Critical / Medium)

**Gap #15** — No validation on any request body.

```
Devin Prompt:
Add Bean Validation (Jakarta Validation) to all API endpoints across all services.
1. Add spring-boot-starter-validation dependency to build.gradle of core-banking-service,
   internet-banking-user-service, internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service.
2. Add validation annotations to all request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
   - User: @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
   - UserUpdateRequest: @NotNull status
3. Add @Valid annotation to all @RequestBody parameters in controllers.
4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that
   returns 422 with field-level error details.
```

### 2.2 Add Circuit Breakers with Resilience4j (Critical / Medium)

**Gap #34** — No circuit breakers on any inter-service call.

```
Devin Prompt:
Add Resilience4j circuit breakers to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service for all Feign calls to core-banking-service.
1. Add spring-cloud-starter-circuitbreaker-resilience4j to both build.gradle files.
2. Enable Feign circuit breaker: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer) — return error response
   - BankingCoreRestClientFallback (utility-payment) — return error response
4. When the circuit opens, update the local entity status to FAILED.
5. Configure circuit breaker: slidingWindowSize=10, failureRateThreshold=50,
   waitDurationInOpenState=30s.
```

### 2.3 Add Idempotency Keys for Financial Transactions (Critical / Medium)

**Gap #38** — No idempotency protection.

```
Devin Prompt:
Add idempotency support to fund transfer and utility payment endpoints.
1. Add an X-Idempotency-Key header to FundTransferController.sendFundTransfer()
   and UtilityPaymentController.processPayment().
2. Create an idempotency_key table in each service's database with columns:
   idempotency_key (PK, VARCHAR), response_body (TEXT), created_at (TIMESTAMP).
3. Before processing, check if the key exists. If yes, return the cached response.
4. After processing, store the response with the key.
5. Add a Flyway migration for the new table.
6. Add the X-Idempotency-Key requirement to the API documentation.
```

### 2.4 Secure Downstream Services (Critical / Medium)

**Gap #16** — Services are unprotected when accessed directly.

```
Devin Prompt:
Add security to downstream services so they cannot be accessed without proper auth.
Option 1 (recommended for this project): Add a shared secret header validation.
1. Define a shared internal API key in the config server for service-to-service calls.
2. Add a servlet filter to core-banking-service that validates an X-Internal-Api-Key header.
3. Configure the API Gateway's GlobalFilter to add this header when routing to services.
4. Configure each Feign client to propagate this header via a RequestInterceptor.
This prevents direct access to internal services while keeping the implementation simple.
```

### 2.5 Externalize Secrets from Source Code (Critical / Medium)

**Gap #18** — Hardcoded secrets in Docker Compose and SQL.

```
Devin Prompt:
Remove all hardcoded secrets from source-controlled files in this project:
1. Create a docker-compose/.env file (add to .gitignore) with variables:
   MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD, DB_USER_PASSWORD
2. Update docker-compose.yml to reference ${MYSQL_ROOT_PASSWORD}, etc.
3. Update docker-compose/mysql/Dockerfile to use ARG instead of hardcoded ENV.
4. Move Keycloak client-secret from test application.yml to environment variable.
5. Create a docker-compose/.env.example with placeholder values for documentation.
6. Add .env to .gitignore if not already present.
```

### 2.6 Add Role-Based Access Control (High / Medium)

**Gap #17** — Any authenticated user can perform admin operations.

```
Devin Prompt:
Add RBAC to the API Gateway and downstream services:
1. Define two Keycloak roles in the realm: ROLE_USER and ROLE_ADMIN.
2. Update SecurityConfiguration in the API Gateway to enforce roles:
   - /user/api/v1/bank-users/update/** → ROLE_ADMIN only
   - /user/api/v1/bank-users (GET, list) → ROLE_ADMIN only
   - /fund-transfer/** → ROLE_USER
   - /utility-payment/** → ROLE_USER
   - /banking-core/** → ROLE_ADMIN only (internal service)
3. Propagate the JWT roles downstream via the X-Auth-Id header or by forwarding
   the full JWT token.
```

### 2.7 Create Shared Library Module (High / Medium)

**Gap #2** — Significant code duplication across services.

```
Devin Prompt:
Create a banking-common shared library module for this project:
1. Create a new directory banking-common/ with its own build.gradle (java-library plugin,
   no Spring Boot plugin).
2. Move these shared classes into banking-common:
   - AuditAware (base entity with audit fields)
   - BaseMapper (abstract mapper class)
   - ErrorResponse and SimpleBankingGlobalException
   - GlobalExceptionHandler (parameterized for service-specific error codes)
   - TransactionStatus enum
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - CustomFeignClientConfiguration
3. Publish to local Maven repository or use Gradle composite builds.
4. Update each service's build.gradle to depend on banking-common.
5. Remove the duplicated classes from each service.
```

### 2.8 Add Fallback and Error Status Handling (High / Medium)

**Gap #37** — PENDING records never marked FAILED on errors.

```
Devin Prompt:
Fix the fund transfer and utility payment services to properly handle failure cases:
1. In FundTransferService.fundTransfer(), wrap the bankingCoreFeignClient.fundTransfer()
   call in a try-catch. On any exception, update the FundTransferEntity status to FAILED
   and re-throw the exception.
2. Same pattern in UtilityPaymentService.utilPayment() — catch exceptions from
   bankingCoreRestClient.utilityPayment(), mark entity as FAILED, then re-throw.
3. Add unit tests to verify that FAILED status is set when core banking returns an error.
```

### 2.9 Add Unit Tests for 3 Untested Services (Critical / Large)

**Gap #11** — Only core-banking-service has meaningful tests.

```
Devin Prompt:
Add comprehensive unit tests for the three application services that currently lack them:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (happy path, email already registered, user not
     found in core banking, email mismatch), readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: test createUser, updateUser, readUser, readUserByEmail
   - Mock BankingCoreRestClient and KeycloakManager

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (happy path, core banking failure),
     readAllTransfers
   - Mock BankingCoreFeignClient and FundTransferRepository

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (happy path, core banking failure),
     readPayments
   - Mock BankingCoreRestClient and UtilityPaymentRepository

Use Mockito for all mocks. Follow the same patterns as existing core-banking-service tests.
```

### 2.10 Add Controller Tests (High / Medium)

**Gap #14** — No controller-layer tests.

```
Devin Prompt:
Add @WebMvcTest controller tests for all 4 application services:
1. core-banking-service: AccountControllerTest, TransactionControllerTest, UserControllerTest
2. internet-banking-user-service: UserControllerTest
3. internet-banking-fund-transfer-service: FundTransferControllerTest
4. internet-banking-utility-payment-service: UtilityPaymentControllerTest
Each test should use MockMvc and mock the service layer. Test:
- Happy path for each endpoint
- 404 responses for entity not found
- Request body validation (after validation is added)
- Correct HTTP status codes
```

---

## Phase 3: Polish (4–8 weeks)

Lower-severity improvements for maintainability, developer experience, and operational maturity.

### 3.1 Return Pagination Metadata in Responses (Medium / Small)

**Gap #24** — List endpoints strip pagination info.

```
Devin Prompt:
Update all paginated GET endpoints to return Spring's Page<T> wrapper instead of
List<T>. This includes:
- UserController.readUsers() in both user-service and core-banking-service
- FundTransferController.readFundTransfers()
- UtilityPaymentController.readPayments()
Change service methods to return Page<DTO> instead of List<DTO>. The Page wrapper
automatically includes totalElements, totalPages, number, size in the JSON response.
Update any existing tests that check return types.
```

### 3.2 Fix ResponseEntity Generic Types for OpenAPI (Medium / Small)

**Gap #26** — Raw types prevent schema generation.

```
Devin Prompt:
Add generic type parameters to all ResponseEntity return types across all controllers:
- AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- UserController (core): ResponseEntity<User>, ResponseEntity<List<User>>
- FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- UtilityPaymentController: ResponseEntity<UtilityPaymentResponse>, ResponseEntity<List<UtilityPayment>>
This allows springdoc-openapi to generate proper response schemas.
```

### 3.3 Standardize Package Structure Across Services (Medium / Medium)

**Gap #1** — Inconsistent package layouts.

```
Devin Prompt:
Standardize the package structure across all application services to follow this layout:
  com.javatodev.finance/
    controller/
    service/
    service/rest/ (Feign clients)
    model/
      dto/
        request/
        response/
      entity/
      mapper/
      enums/
    repository/
    exception/
    configuration/
      filter/
      audit/
      security/
Rename and move classes as needed. Update all imports. Ensure tests still pass.
```

### 3.4 Add Filtering to List Endpoints (Medium / Medium)

**Gap #25** — No filtering capabilities.

```
Devin Prompt:
Add filtering support to list endpoints using Spring Data JPA Specifications:
1. FundTransferController GET /api/v1/transfer: filter by status, fromAccount, toAccount, date range
2. UtilityPaymentController GET /api/v1/utility-payment: filter by status, providerId, account, date range
3. UserController GET /api/v1/bank-users: filter by status
4. Core UserController GET /api/v1/user: filter by email, firstName, lastName
Add @RequestParam query parameters for each filter field and build JPA Specifications dynamically.
```

### 3.5 Add Integration Tests with Testcontainers (High / Large)

**Gap #12** — No integration tests.

```
Devin Prompt:
Add integration tests using Testcontainers for the core-banking-service:
1. Add testcontainers and testcontainers-mysql dependencies to build.gradle.
2. Create a base test class that starts MySQL via Testcontainers and configures
   Spring's datasource to use it.
3. Write integration tests for AccountController, TransactionController, and
   UserController that make actual HTTP calls and verify database state.
4. Run Flyway migrations against the test container.
This serves as a template for adding integration tests to other services later.
```

### 3.6 Add Contract Tests Between Services (High / Large)

**Gap #13** — No contract testing.

```
Devin Prompt:
Add Spring Cloud Contract tests between fund-transfer-service and core-banking-service:
1. Add spring-cloud-starter-contract-verifier to core-banking-service (producer).
2. Define contracts in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number} (found and not found)
   - POST /api/v1/transaction/fund-transfer (success and insufficient funds)
3. Generate stubs JAR from core-banking-service.
4. Add spring-cloud-starter-contract-stub-runner to fund-transfer-service (consumer).
5. Write consumer-side tests that use the generated stubs.
```

### 3.7 Use POST 201 Created for Resource Creation (Low / Small)

**Gap #22** — POST endpoints return 200.

```
Devin Prompt:
Update all POST endpoints that create resources to return HTTP 201 Created:
- UserController.createUser() → ResponseEntity.created(uri).body(user)
- FundTransferController.sendFundTransfer() → ResponseEntity.status(HttpStatus.CREATED).body(response)
- UtilityPaymentController.processPayment() → ResponseEntity.status(HttpStatus.CREATED).body(response)
For createUser, also include a Location header pointing to the new user's URI.
```

### 3.8 Fix Keycloak Singleton Thread Safety (Medium / Small)

**Gap #4** — Static singleton is not thread-safe.

```
Devin Prompt:
Fix the KeycloakProperties class in internet-banking-user-service. The static
keycloakInstance field uses a lazy initialization pattern that is not thread-safe.
Replace it with a Spring @Bean method in a @Configuration class that creates the
Keycloak instance once during application startup. Use @Bean with default singleton
scope instead of a manual static field. Remove the static field and getInstance()
method, replacing it with proper Spring bean injection.
```

### 3.9 Add Kubernetes Readiness/Liveness Probes (Low / Small)

**Gap #31** — No probe configuration.

```
Devin Prompt:
Add Kubernetes readiness and liveness probe configuration to all services. In each
service's application.yml (or via config server), add:
  management.endpoint.health.probes.enabled: true
  management.health.livenessState.enabled: true
  management.health.readinessState.enabled: true
This exposes /actuator/health/liveness and /actuator/health/readiness endpoints.
Also create a Kubernetes deployment manifest template in a new k8s/ directory
showing the probe configuration for one service as an example.
```

### 3.10 Add Aggregated API Documentation (Low / Medium)

**Gap #27** — No centralized API docs.

```
Devin Prompt:
Add aggregated OpenAPI documentation through the API Gateway. Configure the API
Gateway to aggregate swagger docs from all downstream services:
1. Add springdoc-openapi-starter-webflux-ui to the API Gateway's build.gradle.
2. Configure springdoc to discover services via Eureka and aggregate their
   OpenAPI specs at a single /swagger-ui.html endpoint on the gateway.
3. Add springdoc group configuration for each downstream service.
```

### 3.11 Add Custom Business Metrics (Low / Medium)

**Gap #33** — No business-level metrics.

```
Devin Prompt:
Add custom Micrometer metrics to track key business events:
1. core-banking-service: Counter for fund transfers (tagged by status), counter for
   utility payments, gauge for total transaction volume.
2. fund-transfer-service: Timer for end-to-end transfer processing time,
   counter for failed transfers.
3. utility-payment-service: Timer for payment processing time,
   counter for payments by provider.
Use MeterRegistry to register counters and timers in each service class.
```

### 3.12 Clean Up URL Design (Low / Small)

**Gap #23** — Redundant path segments.

```
Devin Prompt:
Clean up the API URL design:
1. Rename PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
   (remove redundant 'update' segment).
2. Update the API Gateway route configuration if it references the old path.
3. Update any Postman collections or API documentation.
```

---

## Priority Matrix

```
                     Small Effort          Medium Effort         Large Effort
                ┌──────────────────┬──────────────────────┬──────────────────┐
   Critical     │ 1.1 Balance bug  │ 2.1 Input validation │ 2.9 Unit tests   │
                │ 1.2 Log secrets  │ 2.2 Circuit breakers │                  │
                │                  │ 2.3 Idempotency      │                  │
                │                  │ 2.4 Service auth     │                  │
                │                  │ 2.5 Secret mgmt      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   High         │ 1.3 HTTP codes   │ 2.6 RBAC             │ 3.5 Integration  │
                │ 1.4 Stack traces │ 2.7 Shared library   │      tests       │
                │ 1.5 Feign decoder│ 2.8 Fallback/FAILED  │ 3.6 Contract     │
                │ 1.6 Timeouts     │ 2.10 Controller tests│      tests       │
                │ 1.7 Retry        │                      │                  │
                │ 1.11 Vuln scan   │                      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   Medium       │ 1.8 Prometheus   │ 3.3 Package structure│                  │
                │ 1.9 JSON logging │ 3.4 Filtering        │                  │
                │ 1.12 Health      │                      │                  │
                │ 3.1 Pagination   │                      │                  │
                │ 3.2 ResponseEntity│                     │                  │
                │ 3.8 Keycloak fix │                      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   Low          │ 1.10 Log format  │ 3.10 Aggregated docs │                  │
                │ 3.7 POST 201     │ 3.11 Business metrics│                  │
                │ 3.9 K8s probes   │                      │                  │
                │ 3.12 URL cleanup │                      │                  │
                └──────────────────┴──────────────────────┴──────────────────┘
```

---

## Execution Order Summary

| Order | Item | Gap # | Severity | Effort | Phase |
|-------|------|-------|----------|--------|-------|
| 1 | Fix balance calculation bug | 39 | Critical | Small | 1 |
| 2 | Stop logging sensitive data | 21 | Critical | Small | 1 |
| 3 | Fix HTTP status codes | 9 | High | Small | 1 |
| 4 | Fix generic exception handler | 8 | High | Small | 1 |
| 5 | Add Feign error decoders | 10 | High | Small | 1 |
| 6 | Add Feign timeouts | 36 | High | Small | 1 |
| 7 | Add retry policies | 35 | High | Small | 1 |
| 8 | Configure Prometheus metrics | 32 | Medium | Small | 1 |
| 9 | Enable JSON logging | 28 | Medium | Small | 1 |
| 10 | Fix log formatting | 29 | Low | Small | 1 |
| 11 | Add vulnerability scanning | 19 | High | Small | 1 |
| 12 | Add custom health indicators | 30 | Medium | Small | 1 |
| 13 | Add input validation | 15 | Critical | Medium | 2 |
| 14 | Add circuit breakers | 34 | Critical | Medium | 2 |
| 15 | Add idempotency keys | 38 | Critical | Medium | 2 |
| 16 | Secure downstream services | 16 | Critical | Medium | 2 |
| 17 | Externalize secrets | 18 | Critical | Medium | 2 |
| 18 | Add RBAC | 17 | High | Medium | 2 |
| 19 | Create shared library | 2 | High | Medium | 2 |
| 20 | Add fallback/error status handling | 37 | High | Medium | 2 |
| 21 | Add unit tests for 3 services | 11 | Critical | Large | 2 |
| 22 | Add controller tests | 14 | High | Medium | 2 |
| 23 | Return pagination metadata | 24 | Medium | Small | 3 |
| 24 | Fix ResponseEntity generics | 26 | Medium | Small | 3 |
| 25 | Standardize package structure | 1 | Medium | Medium | 3 |
| 26 | Add filtering to list endpoints | 25 | Medium | Medium | 3 |
| 27 | Add integration tests | 12 | High | Large | 3 |
| 28 | Add contract tests | 13 | High | Large | 3 |
| 29 | POST 201 Created | 22 | Low | Small | 3 |
| 30 | Fix Keycloak singleton | 4 | Medium | Small | 3 |
| 31 | K8s readiness/liveness probes | 31 | Low | Small | 3 |
| 32 | Aggregated API docs | 27 | Low | Medium | 3 |
| 33 | Custom business metrics | 33 | Low | Medium | 3 |
| 34 | Clean up URL design | 23 | Low | Small | 3 |
