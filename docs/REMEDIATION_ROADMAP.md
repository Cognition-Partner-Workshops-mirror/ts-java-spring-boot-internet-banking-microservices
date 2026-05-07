# Remediation Roadmap

## Overview

This roadmap prioritizes the gaps identified in [`GAP_ANALYSIS.md`](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): Critical fixes with small effort that immediately improve security, correctness, and reliability.
- **Phase 2 — Important** (3-6 weeks): High-impact items that require moderate effort and significantly improve production-readiness.
- **Phase 3 — Polish** (6-12 weeks): Architectural improvements, long-term quality, and operational excellence.

Each item includes a sample **Devin prompt** that can be used to automate the remediation.

---

## Phase 1 — Quick Wins

### 1.1 Fix Balance Calculation Double-Deduction Bug

**Gaps Addressed:** GAP-RES-06
**Severity:** Critical | **Effort:** Small
**Priority Justification:** Data integrity bug that causes incorrect account balances on every transaction.

**Description:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, after updating `actualBalance`, the code sets `availableBalance = actualBalance - amount` again, effectively double-deducting. The fix: set `availableBalance = actualBalance` after the deduction.

<details>
<summary>Devin Prompt</summary>

```
Fix the balance calculation bug in core-banking-service TransactionService.

In `internalFundTransfer()`, after subtracting the amount from `actualBalance`, the
`availableBalance` is set by subtracting the amount again from the already-reduced
`actualBalance`. This double-deducts from `availableBalance`.

Fix both the debit and credit sides in `internalFundTransfer()` and the same pattern in
`utilPayment()`. After updating `actualBalance`, set `availableBalance = actualBalance`
(i.e., they should be equal after each operation).

Update the existing unit tests in `TransactionServiceTest` to assert correct balance
values after fund transfers and utility payments. Run the core-banking-service tests
to verify.
```
</details>

---

### 1.2 Stop Leaking Exception Details to Clients

**Gaps Addressed:** GAP-ERR-02
**Severity:** Critical | **Effort:** Small

**Description:** The generic exception handler in all 4 business services concatenates the full exception object into the response body. Replace with a safe generic message.

<details>
<summary>Devin Prompt</summary>

```
Fix the generic exception handler in all 4 business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service).

In each service's `GlobalExceptionHandler`, change the generic `Exception` handler to:
1. Log the full exception with stack trace at ERROR level using parameterized logging.
2. Return a generic ErrorResponse with code "INTERNAL_ERROR" and message
   "An unexpected error occurred. Please try again later."
3. Return HTTP 500 instead of 400.

Do NOT include the exception message or stack trace in the response body.
```
</details>

---

### 1.3 Map Exceptions to Correct HTTP Status Codes

**Gaps Addressed:** GAP-ERR-01
**Severity:** Critical | **Effort:** Small

**Description:** Add separate `@ExceptionHandler` methods for `EntityNotFoundException` (404) and `InsufficientFundsException` (422).

<details>
<summary>Devin Prompt</summary>

```
Update the GlobalExceptionHandler in all business services to return correct HTTP status codes:

1. In core-banking-service: Add handlers for EntityNotFoundException (return 404) and
   InsufficientFundsException (return 422).
2. In internet-banking-user-service: Add handlers for EntityNotFoundException (404),
   InvalidEmailException (400), InvalidBankingUserException (404),
   UserAlreadyRegisteredException (409 Conflict).
3. In fund-transfer-service and utility-payment-service: Keep SimpleBankingGlobalException
   as 400, but add a generic Exception handler returning 500.

Ensure all error responses use the ErrorResponse DTO consistently (never return raw strings).
```
</details>

---

### 1.4 Add Input Validation to All Request DTOs

**Gaps Addressed:** GAP-SEC-02
**Severity:** Critical | **Effort:** Small

**Description:** Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller parameters.

<details>
<summary>Devin Prompt</summary>

```
Add input validation to all REST API request DTOs across all services.

1. Add spring-boot-starter-validation dependency to each service's build.gradle.

2. Add validation annotations to these DTOs:
   - FundTransferRequest (core-banking & fund-transfer): @NotBlank on fromAccount and
     toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest (core-banking & utility-payment): @NotNull on providerId,
     @NotNull @Positive on amount, @NotBlank on referenceNumber and account
   - User (user-service registration): @NotBlank @Email on email,
     @NotBlank on identification, @NotBlank @Size(min=8) on password

3. Add @Valid annotation on all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that
   returns HTTP 400 with field-level error details.
```
</details>

---

### 1.5 Fix Keycloak Singleton Thread Safety

**Gaps Addressed:** GAP-SEC-04
**Severity:** High | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.

The `getInstance()` method uses lazy initialization without synchronization. Replace
the manual singleton pattern with a Spring @Bean approach:

1. Remove the static `keycloakInstance` field and `getInstance()` method from
   KeycloakProperties.
2. Add a @Bean method in a @Configuration class that creates the Keycloak instance
   using KeycloakBuilder with the injected properties.
3. Update KeycloakManager to inject the Keycloak bean directly instead of calling
   keycloakProperties.getInstance().
```
</details>

---

### 1.6 Protect Password Field from API Responses

**Gaps Addressed:** GAP-SEC-06
**Severity:** High | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
In internet-banking-user-service, protect the password field in the User DTO.

Add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the `password` field in
the User class (model/dto/User.java). This ensures the password is accepted in request
bodies but never serialized in responses.

Alternatively, create separate UserRegistrationRequest and UserResponse DTOs to properly
separate input from output concerns.
```
</details>

---

### 1.7 Secure Actuator Endpoints

**Gaps Addressed:** GAP-OBS-05
**Severity:** High | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Restrict actuator endpoint access in the API gateway SecurityConfiguration.

1. Change the actuator permitAll() rules to only allow /actuator/health and
   /actuator/info without authentication.
2. All other actuator endpoints should require authentication.

Update the pathMatchers in SecurityConfiguration.java:
   .pathMatchers("/actuator/health", "/actuator/info").permitAll()
   .pathMatchers("/user/actuator/health", "/user/actuator/info").permitAll()
   (repeat for fund-transfer, banking-core, utility-payment prefixes)
```
</details>

---

### 1.8 Fix OpenAPI Dependency (WebFlux → WebMVC)

**Gaps Addressed:** GAP-API-04
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Fix the OpenAPI/Swagger dependency in all servlet-based services.

In build.gradle for core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service:

Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

The webflux variant is only correct for the API gateway (which uses Spring WebFlux).
The other 4 services are Spring MVC servlet-based and need the webmvc starter.

Build all services to verify no compilation errors.
```
</details>

---

### 1.9 Fix Logging Anti-Patterns

**Gaps Addressed:** GAP-OBS-02
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Fix all logging anti-patterns across the codebase.

1. Find and fix string concatenation in log statements. Replace:
   - log.error("..." + e) → log.error("...", e)
   - log.info("..." + request.toString()) → log.info("...", request)

2. Fix the mixed placeholder/concatenation in FundTransferService:
   log.info("Sending fund transfer request {}" + request.toString())
   → log.info("Sending fund transfer request {}", request)

3. Remove .toString() calls in log statements — SLF4J calls toString() automatically.

Search all .java files for patterns like `log.*(.*" +` and fix them.
```
</details>

---

### 1.10 Add .editorconfig for Consistent Formatting

**Gaps Addressed:** GAP-ORG-04
**Severity:** Low | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Create an .editorconfig file at the repository root with these settings:

- root = true
- Default: indent_style = space, indent_size = 4, charset = utf-8, end_of_line = lf,
  trim_trailing_whitespace = true, insert_final_newline = true
- For *.gradle: indent_size = 4
- For *.yml/*.yaml: indent_size = 2
- For *.md: trim_trailing_whitespace = false
```
</details>

---

## Phase 2 — Important

### 2.1 Add Circuit Breakers to All Feign Clients

**Gaps Addressed:** GAP-RES-01, GAP-RES-02, GAP-RES-03, GAP-RES-04
**Severity:** Critical | **Effort:** Medium
**Priority Justification:** Without circuit breakers, a single service failure cascades to all upstream services.

<details>
<summary>Devin Prompt</summary>

```
Add Resilience4j circuit breakers, retry, and timeout to all OpenFeign clients.

1. Add these dependencies to fund-transfer-service, utility-payment-service, and
   user-service build.gradle files:
   - implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breakers for Feign:
   Add to each service's application.yml (or bootstrap.yml):
   spring.cloud.openfeign.circuitbreaker.enabled: true

3. Configure Resilience4j in each service's application.yml:
   - Circuit breaker: failureRateThreshold=50, waitDurationInOpenState=30s,
     slidingWindowSize=10
   - Retry: maxAttempts=3, waitDuration=1s, retryExceptions=java.io.IOException
   - Timeout: timeoutDuration=5s

4. Create fallback classes for each Feign client that return sensible error responses
   instead of propagating exceptions.

5. Add a FeignClient fallbackFactory for each client.
```
</details>

---

### 2.2 Add Transaction Failure Handling in Orchestration Services

**Gaps Addressed:** GAP-ERR-06
**Severity:** Critical | **Effort:** Medium

<details>
<summary>Devin Prompt</summary>

```
Add proper failure handling in the fund transfer and utility payment orchestration services.

In FundTransferService.fundTransfer():
1. Wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch.
2. On failure, update the FundTransferEntity status to FAILED and persist it.
3. Log the error with full context (from/to accounts, amount, exception).
4. Re-throw a domain-specific exception (e.g., FundTransferFailedException).

In UtilityPaymentService.utilPayment():
1. Wrap the bankingCoreRestClient.utilityPayment() call in a try-catch.
2. On failure, update the UtilityPaymentEntity status to FAILED and persist it.
3. Log the error and re-throw.

Add unit tests for both failure scenarios using Mockito to simulate Feign client failures.
```
</details>

---

### 2.3 Add Feign Error Decoders to Fund Transfer and Utility Payment Services

**Gaps Addressed:** GAP-ERR-05
**Severity:** High | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Add CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, following the pattern already implemented
in internet-banking-user-service.

1. Copy the CustomFeignErrorDecoder and CustomFeignClientConfiguration from
   user-service to both services.
2. Update the FeignClient annotations to reference the new configuration class.
3. Add unit tests for the error decoder.
```
</details>

---

### 2.4 Add Unit Tests for All Business Services

**Gaps Addressed:** GAP-TEST-01, GAP-TEST-04
**Severity:** High | **Effort:** Large

<details>
<summary>Devin Prompt</summary>

```
Add comprehensive unit tests for internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

For each service:
1. Add test-specific application.yml that disables Eureka, Config Server, and uses
   H2 in-memory database.
2. Write unit tests for:
   - Service layer (mock repositories and Feign clients with Mockito)
   - Controller layer (use @WebMvcTest with MockMvc)
   - Mapper classes

Target tests:
- UserService: createUser (success, duplicate email, invalid email, user not found in core),
  readUsers, readUser, updateUser (approve flow, disable flow)
- FundTransferService: fundTransfer (success, Feign failure), readAllTransfers
- UtilityPaymentService: utilPayment (success, Feign failure), readPayments

Each service should have at least 10 meaningful test cases. Use @MockBean for
external dependencies. Add H2 test dependency where not already present.
```
</details>

---

### 2.5 Move Credentials to Environment Variables

**Gaps Addressed:** GAP-SEC-01
**Severity:** Critical | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Remove hardcoded credentials from docker-compose files and source code.

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace all hardcoded passwords with ${VARIABLE:-default} syntax:
     MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
     KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
     KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
     POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}

2. Create a docker-compose/.env.example file documenting all required variables.

3. Add .env to .gitignore to prevent accidental commits.

4. In docker-compose/mysql/privileges.sql, replace the hardcoded password with
   a placeholder and add a note to update it.

5. In README.md, replace the inline test credentials with a reference to the
   .env.example file.
```
</details>

---

### 2.6 Add Pagination Metadata to List Endpoints

**Gaps Addressed:** GAP-API-01
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Add pagination metadata to all paginated list endpoints across all services.

Create a generic PagedResponse<T> wrapper class:
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}

Update these endpoints to return PagedResponse instead of List:
- UserController.readUsers() in both core-banking and user-service
- FundTransferController.readFundTransfers()
- UtilityPaymentController.readPayments()

In each service layer, return the Page object instead of extracting .getContent(),
and map it in the controller.
```
</details>

---

### 2.7 Add Prometheus Metrics Export

**Gaps Addressed:** GAP-OBS-03
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Enable Prometheus metrics export for all services that include spring-boot-starter-actuator.

1. Add to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Configure actuator endpoints in each service's application.yml (or via Config Server):
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom health indicators in services that use Feign clients:
   - A FeignHealthIndicator that pings core-banking-service /actuator/health

4. Optionally add a prometheus service to docker-compose.yml that scrapes all services.
```
</details>

---

### 2.8 Add Rate Limiting to Public Endpoints

**Gaps Addressed:** GAP-SEC-07
**Severity:** Medium | **Effort:** Medium

<details>
<summary>Devin Prompt</summary>

```
Add rate limiting to the API gateway for publicly accessible endpoints.

1. Add the Spring Cloud Gateway RequestRateLimiter filter dependency.
2. Configure a Redis-based or in-memory rate limiter for the
   /user/api/v1/bank-users/register endpoint.
3. Set limits: 10 requests per minute per IP address.
4. Return HTTP 429 Too Many Requests when the limit is exceeded.

If Redis is not desired, use Bucket4j or a simple in-memory rate limiter
with Spring Cloud Gateway's built-in filter.
```
</details>

---

### 2.9 Add Dependency Vulnerability Scanning

**Gaps Addressed:** GAP-SEC-05
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Add OWASP Dependency Check to the Gradle builds for all services.

1. Add the OWASP dependency-check Gradle plugin to each build.gradle:
   plugins {
     id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure it to fail the build on CVSS score >= 7:
   dependencyCheck {
     failBuildOnCVSS = 7
     formats = ['HTML', 'JSON']
   }

3. Add a GitHub Actions workflow (.github/workflows/dependency-check.yml) that
   runs the check on pull requests.

4. Create a .github/dependabot.yml configuration for automated dependency updates.
```
</details>

---

### 2.10 Configure Database Connection Pooling

**Gaps Addressed:** GAP-RES-05
**Severity:** Medium | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Add explicit HikariCP connection pool configuration for all database-connected services.

Add to each service's application.yml (or via Config Server):

spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: ${spring.application.name}-pool

Apply to: core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service.
```
</details>

---

## Phase 3 — Polish

### 3.1 Extract Shared Library

**Gaps Addressed:** GAP-ORG-02
**Severity:** High | **Effort:** Large
**Priority Justification:** Reduces maintenance burden but requires significant refactoring.

<details>
<summary>Devin Prompt</summary>

```
Create a shared library module (internet-banking-common) to eliminate code duplication.

1. Create a new Gradle module: internet-banking-common/
2. Move these shared classes into the common module:
   - BaseMapper<E, D>
   - AuditAware (MappedSuperclass)
   - AppAuthUserFilter
   - ApiRequestContext / ApiRequestContextHolder
   - AuditConfig / AuditorAwareConfig
   - ErrorResponse
   - SimpleBankingGlobalException
   - GlobalExceptionHandler (as a base class)
   - TransactionStatus enum

3. Publish as a local Maven artifact or use Gradle composite builds.
4. Update all services to depend on the common module.
5. Remove the duplicated classes from each service.
6. Run all tests to verify nothing breaks.
```
</details>

---

### 3.2 Convert to Multi-Module Gradle Build

**Gaps Addressed:** GAP-ORG-01
**Severity:** Medium | **Effort:** Medium

<details>
<summary>Devin Prompt</summary>

```
Convert the project to a Gradle multi-module build.

1. Create a root settings.gradle that includes all service modules:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'
   include 'internet-banking-common'

2. Create a root build.gradle with shared configuration:
   - Common plugins
   - Shared dependency versions
   - Spring Cloud BOM import
   - Common test configuration

3. Simplify each service's build.gradle to only contain service-specific dependencies.
4. Verify all services build with: ./gradlew build
```
</details>

---

### 3.3 Add Integration Tests with Testcontainers

**Gaps Addressed:** GAP-TEST-02
**Severity:** High | **Effort:** Large

<details>
<summary>Devin Prompt</summary>

```
Add integration tests using Testcontainers for all database-connected services.

1. Add Testcontainers dependencies to each service's build.gradle:
   testImplementation 'org.testcontainers:junit-jupiter'
   testImplementation 'org.testcontainers:mysql'

2. For each service, create integration test classes:
   - Use @SpringBootTest with a real MySQL container
   - Disable Eureka and Config Server for isolated testing
   - Test the full request lifecycle from controller to database

3. For core-banking-service, verify Flyway migrations run correctly against
   a real MySQL instance.

4. For services using OpenFeign, use WireMock to mock downstream services.

Target: At least 5 integration tests per service covering happy path and error scenarios.
```
</details>

---

### 3.4 Add Contract Tests Between Services

**Gaps Addressed:** GAP-TEST-03
**Severity:** Medium | **Effort:** Large

<details>
<summary>Devin Prompt</summary>

```
Add Spring Cloud Contract tests between services.

1. Add Spring Cloud Contract dependencies to core-banking-service (provider):
   - spring-cloud-starter-contract-verifier

2. Define contracts for each API endpoint in core-banking-service:
   - GET /api/v1/account/bank-account/{account_number}
   - GET /api/v1/user/{identification}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment

3. Generate contract stubs and publish to local Maven repository.

4. Add Spring Cloud Contract Stub Runner to consumer services:
   - internet-banking-user-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service

5. Write consumer-driven contract tests that verify Feign clients
   work correctly against the generated stubs.
```
</details>

---

### 3.5 Standardize Package Structure

**Gaps Addressed:** GAP-ORG-03
**Severity:** Low | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Standardize the package structure across all services to follow a consistent pattern.

Target structure for each service:
  com.javatodev.finance
  ├── configuration/       # Spring config, beans, filters
  │   ├── audit/
  │   ├── feign/
  │   ├── filter/
  │   └── security/
  ├── controller/          # REST controllers
  ├── exception/           # Exception classes and handlers
  ├── model/
  │   ├── dto/             # Data transfer objects
  │   │   ├── request/     # Request DTOs
  │   │   └── response/    # Response DTOs
  │   ├── entity/          # JPA entities
  │   └── mapper/          # Entity-DTO mappers
  ├── repository/          # Spring Data repositories (at root, not under model)
  └── service/             # Business logic
      └── rest/            # Feign clients

Move classes to match this structure in all services. Update all imports accordingly.
```
</details>

---

### 3.6 Add Response Envelope and Correct HTTP Status Codes for POST

**Gaps Addressed:** GAP-API-05, GAP-API-06, GAP-ERR-03
**Severity:** Low | **Effort:** Medium

<details>
<summary>Devin Prompt</summary>

```
Standardize API responses across all services.

1. Create a generic ApiResponse<T> wrapper:
   {
     "success": true,
     "data": { ... },
     "error": null,
     "timestamp": "2024-01-01T00:00:00Z",
     "path": "/api/v1/transfer"
   }

2. Update all controller methods to wrap responses in ApiResponse.

3. Change POST endpoints to return HTTP 201 Created:
   - UserController.createUser() → ResponseEntity.status(HttpStatus.CREATED)
   - FundTransferController.sendFundTransfer() → ResponseEntity.status(HttpStatus.CREATED)
   - UtilityPaymentController.processPayment() → ResponseEntity.status(HttpStatus.CREATED)

4. Update GlobalExceptionHandler to use the same ApiResponse envelope for errors:
   {
     "success": false,
     "data": null,
     "error": { "code": "...", "message": "..." },
     "timestamp": "...",
     "path": "..."
   }
```
</details>

---

### 3.7 Implement RabbitMQ Notification Service

**Gaps Addressed:** Planned but unimplemented feature from README
**Severity:** Low | **Effort:** Large

<details>
<summary>Devin Prompt</summary>

```
Implement the notification service mentioned in the README that consumes messages
from RabbitMQ.

1. Add RabbitMQ to docker-compose.yml.

2. Add spring-boot-starter-amqp dependency to fund-transfer-service and
   utility-payment-service.

3. In both services, publish a notification event to RabbitMQ after successful
   transactions (fund transfer completion, utility payment completion).

4. Create a new internet-banking-notification-service module:
   - Consumes messages from RabbitMQ
   - Logs notification details (email sending can be stubbed)
   - Registers with Eureka

5. Add the notification service to docker-compose.yml.
```
</details>

---

### 3.8 Add Custom Health Indicators and Structured Logging

**Gaps Addressed:** GAP-OBS-01, GAP-OBS-04
**Severity:** Low | **Effort:** Small

<details>
<summary>Devin Prompt</summary>

```
Improve observability across all services.

1. Add custom HealthIndicators:
   - In user-service: KeycloakHealthIndicator that checks Keycloak /health endpoint
   - In services with Feign clients: CoreBankingHealthIndicator that pings
     core-banking /actuator/health

2. Configure structured JSON logging:
   - Add logback-spring.xml to each service with JSON encoder for production profile
   - Include trace/span IDs in log output using Micrometer context propagation

3. Add custom Micrometer metrics:
   - fund_transfer_total (counter) with tags: status=success|failed
   - utility_payment_total (counter) with tags: status=success|failed
   - user_registration_total (counter)
```
</details>

---

## Roadmap Summary

| Phase | Items | Critical Fixes | Estimated Duration |
|---|---|---|---|
| **Phase 1** | 10 items | 5 (balance bug, error leaks, HTTP codes, validation, credentials) | 1-2 weeks |
| **Phase 2** | 10 items | 2 (circuit breakers, failure handling) | 3-6 weeks |
| **Phase 3** | 8 items | 0 | 6-12 weeks |

### Recommended Execution Order Within Phases

**Phase 1 (sequential priority):**
1. Balance bug fix (1.1) — immediate data integrity
2. Exception leak fix (1.2) — immediate security
3. HTTP status codes (1.3) — correctness
4. Input validation (1.4) — security hardening
5. Keycloak thread safety (1.5) — race condition
6. Password field protection (1.6) — data exposure
7. Actuator security (1.7) — endpoint exposure
8. OpenAPI fix (1.8) — developer experience
9. Logging fixes (1.9) — observability
10. EditorConfig (1.10) — consistency

**Phase 2 (can be parallelized):**
- Stream A (resilience): 2.1 → 2.2 → 2.3
- Stream B (testing): 2.4
- Stream C (security): 2.5 → 2.8 → 2.9
- Stream D (operations): 2.6 → 2.7 → 2.10

**Phase 3 (sequential):**
- 3.1 → 3.2 (shared library before multi-module)
- 3.3 → 3.4 (integration tests before contract tests)
- 3.5, 3.6, 3.7, 3.8 (independent)
