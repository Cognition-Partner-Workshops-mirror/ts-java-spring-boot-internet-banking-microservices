# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to initiate the remediation.

---

## Phase 1: Quick Wins (Critical bugs + Small/Medium effort fixes)

These items address production risks, data integrity issues, and security vulnerabilities that can be resolved with focused, isolated changes.

### 1.1 Fix Balance Calculation Double-Subtraction Bug

**Gap Ref:** 7.6 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already decremented, causing a double-subtraction on every transfer and payment.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.
In both internalFundTransfer() and utilPayment(), the availableBalance is being
double-subtracted. After subtracting from actualBalance, the code subtracts again
when setting availableBalance. Change the logic so that after updating actualBalance,
availableBalance is set to the same value as actualBalance (not actualBalance minus
amount again). Apply the fix to both the fromAccount debit in internalFundTransfer()
(lines 90-91) and the fromAccount debit in utilPayment() (lines 63-64). Update the
existing unit tests in TransactionServiceTest to verify the correct balance after
transfers.
```

---

### 1.2 Fix Generic Exception Handler — Proper HTTP Status Codes + No Stack Trace Leakage

**Gap Refs:** 2.1, 2.2, 4.7 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
Refactor the GlobalExceptionHandler in all four services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service). Make these changes:

1. The EntityNotFoundException handler should return HTTP 404 (not 400).
   Add a dedicated @ExceptionHandler(EntityNotFoundException.class) that returns
   ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(...)).

2. The generic Exception catch-all should return HTTP 500 (Internal Server Error)
   with a generic message like "An internal error occurred" — do NOT include the
   exception details in the response body.

3. Log the full exception at ERROR level in the catch-all handler so it is captured
   in logs but not exposed to clients.

4. Add type parameters to all ResponseEntity return types.

Apply these changes consistently across all four GlobalExceptionHandler classes.
```

---

### 1.3 Add Bean Validation to All Request DTOs

**Gap Ref:** 4.2 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across the project.
Add spring-boot-starter-validation to each service's build.gradle that doesn't
already have it.

Specific validations needed:

core-banking-service:
- FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

internet-banking-fund-transfer-service:
- FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

internet-banking-utility-payment-service:
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

internet-banking-user-service:
- User (registration): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password

Add @Valid annotation to all @RequestBody parameters in controllers. Add a
MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns
HTTP 400 with field-level error details.
```

---

### 1.4 Remove Sensitive Data from Logs

**Gap Ref:** 4.5 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Audit all log statements across every microservice and remove or mask sensitive data.
Specifically:

1. In internet-banking-user-service UserController.createUser(): Do not log the full
   request object (it contains the password). Log only the email.
2. In core-banking-service TransactionController: Do not log full request objects
   containing account numbers and amounts. Log only a transaction type indicator.
3. In internet-banking-fund-transfer-service FundTransferController: Same — avoid
   logging full request with account numbers.
4. Review all other log.info() calls for PII or financial data exposure.

Replace toString() calls with selective field logging (e.g., log only non-sensitive
identifiers).
```

---

### 1.5 Fix Keycloak Singleton Thread-Safety

**Gap Ref:** 4.4 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.java.
The static Keycloak instance is lazily initialized without synchronization. Replace the
manual singleton with a Spring @Bean definition. Create a @Configuration class called
KeycloakConfig that defines a @Bean Keycloak method using KeycloakBuilder. Remove the
static field and getInstance() method from KeycloakProperties. Update KeycloakManager
to inject the Keycloak bean directly instead of calling keycloakProperties.getInstance().
```

---

### 1.6 Add Pagination Metadata to List Responses

**Gap Ref:** 5.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Update all paginated list endpoints to return pagination metadata instead of raw lists.
Create a generic PageResponse<T> wrapper class with fields: content (List<T>),
pageNumber (int), pageSize (int), totalElements (long), totalPages (int), last (boolean).

Apply this to:
- core-banking-service UserController.readUsers()
- internet-banking-user-service UserController.readUsers()
- internet-banking-fund-transfer-service FundTransferController.readFundTransfers()
- internet-banking-utility-payment-service UtilityPaymentController.readPayments()

Update the service methods to return PageResponse instead of List. Put the PageResponse
class in a location consistent with each service's package structure.
```

---

### 1.7 Configure Feign Client Timeouts

**Gap Refs:** 7.2, 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add explicit Feign client timeout and retry configuration to internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service.

In each service's application.yml (or bootstrap.yml), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
            loggerLevel: BASIC

Also add Spring Retry support:
1. Add 'org.springframework.retry:spring-retry' and
   'org.springframework.boot:spring-boot-starter-aop' to each service's build.gradle.
2. Add @EnableRetry to each application's main class.
3. Configure retry with max 3 attempts and 1-second backoff for Feign calls.
```

---

### 1.8 Add Feign Error Handling to Fund Transfer and Utility Payment

**Gap Ref:** 2.3 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add proper Feign error handling to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service.

1. Create a CustomFeignErrorDecoder class in each service (similar to the one in
   internet-banking-user-service) that implements feign.codec.ErrorDecoder.
2. Map 4xx responses to appropriate application exceptions and 5xx to a
   ServiceUnavailableException.
3. Register the error decoder in the CustomFeignClientConfiguration.
4. In FundTransferService.fundTransfer(), wrap the Feign call in a try-catch.
   On failure, update the FundTransferEntity status to FAILED and rethrow.
5. In UtilityPaymentService.utilPayment(), do the same — update status to FAILED
   on Feign errors.
```

---

### 1.9 Move Docker Compose Credentials to Environment Variables

**Gap Ref:** 4.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Refactor docker-compose/docker-compose.yml and docker-compose-support-apps.yml to use
environment variable substitution for all credentials:
- MYSQL_ROOT_PASSWORD → ${MYSQL_ROOT_PASSWORD:-changeme}
- KEYCLOAK_ADMIN_PASSWORD → ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
- KC_DB_PASSWORD → ${KC_DB_PASSWORD:-changeme}
- KC_DB_USERNAME → ${KC_DB_USERNAME:-keycloak}
- POSTGRES_PASSWORD → ${POSTGRES_PASSWORD:-changeme}

Create a docker-compose/.env.example file documenting all required environment variables
with placeholder values. Add docker-compose/.env to .gitignore.
```

---

## Phase 2: Important (Architectural improvements + Medium effort)

These items improve system reliability, maintainability, and developer experience but require more coordinated effort.

### 2.1 Add Circuit Breakers to Feign Clients

**Gap Ref:** 7.1 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls in internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service.

1. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
   to each service's build.gradle.
2. Enable circuit breaker support for Feign: set spring.cloud.openfeign.circuitbreaker.enabled=true
   in each service's configuration.
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return a FundTransferResponse
     with status FAILED.
   - BankingCoreRestClientFallback (utility-payment): return a UtilityPaymentResponse
     with status FAILED.
   - BankingCoreRestClientFallback (user-service): throw a ServiceUnavailableException
     with a clear message.
4. Configure circuit breaker thresholds in application.yml:
   slidingWindowSize: 10, failureRateThreshold: 50, waitDurationInOpenState: 30s.
```

---

### 2.2 Add Idempotency Keys for Financial Transactions

**Gap Ref:** 7.5 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to the fund transfer and utility payment flows.

1. Add an 'idempotencyKey' field (String, UUID) to FundTransferRequest and
   UtilityPaymentRequest in both the internet-banking services and core-banking-service.
2. Add a unique constraint on idempotencyKey in the fund_transfer and utility_payment tables.
3. In FundTransferService.fundTransfer(), before processing, check if a record with
   the same idempotencyKey already exists. If it does and status is SUCCESS, return the
   existing response. If PENDING/PROCESSING, return a 409 Conflict.
4. Apply the same pattern to UtilityPaymentService.utilPayment().
5. Add the idempotencyKey to the core-banking-service FundTransferRequest and
   UtilityPaymentRequest DTOs so it flows through to the transaction records.
6. Clients should generate a UUID v4 idempotency key and send it with each request.
```

---

### 2.3 Extract Shared Library

**Gap Ref:** 1.2 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a shared library module called 'banking-common' at the project root. This should
be a Gradle subproject. Move the following duplicated classes into it:

- SimpleBankingGlobalException
- ErrorResponse
- GlobalExceptionHandler (as an abstract base or auto-configuration)
- BaseMapper interface
- AuditAware, AuditConfig, AuditorAwareConfig
- AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder

Steps:
1. Create banking-common/ directory with its own build.gradle (java-library plugin,
   no Spring Boot plugin).
2. Move the shared classes into com.javatodev.finance.common package hierarchy.
3. Create a root settings.gradle that includes all service projects and banking-common.
4. Update each service's build.gradle to add: implementation project(':banking-common').
5. Remove the duplicated classes from each service.
6. Verify all services compile and tests pass.
```

---

### 2.4 Add Authorization Enforcement in Downstream Services

**Gap Ref:** 4.6 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add authorization enforcement to downstream services using the X-Auth-Id header
propagated by the API Gateway.

1. In internet-banking-fund-transfer-service:
   - Before processing a fund transfer, verify that the authenticated user (from
     X-Auth-Id / ApiRequestContextHolder) owns the source account. Call
     BankingCoreFeignClient.readAccount(fromAccount) and compare the account's
     user auth ID with the request context's auth ID.
   - Return HTTP 403 Forbidden if the user doesn't own the account.

2. In internet-banking-user-service:
   - For readUser() and updateUser(), verify the authenticated user is either
     accessing their own record or has an admin role.
   - Add role-based checks using Keycloak role claims.

3. In internet-banking-utility-payment-service:
   - Similar to fund-transfer: verify account ownership before processing payment.

4. Create an AuthorizationException class and add it to the GlobalExceptionHandler
   returning HTTP 403.
```

---

### 2.5 Add Unit Tests to All Services

**Gap Ref:** 3.1 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests to the four services that currently lack them.

internet-banking-user-service:
- UserServiceTest: Test createUser (happy path, email already exists, user not found
  in core banking, email mismatch), readUsers, readUser, updateUser (approve flow).
- KeycloakUserServiceTest: Test createUser, updateUser, readUser, readUserByEmail
  with mocked KeycloakManager.

internet-banking-fund-transfer-service:
- FundTransferServiceTest: Test fundTransfer (happy path, Feign error), readAllTransfers.

internet-banking-utility-payment-service:
- UtilityPaymentServiceTest: Test utilPayment (happy path, Feign error), readPayments.

For each test class:
- Use Mockito to mock repositories and Feign clients.
- Test both success and failure paths.
- Verify correct status transitions (PENDING → SUCCESS, PENDING → FAILED).
- Use @BeforeEach for setup, following the pattern in the existing core-banking tests.
```

---

### 2.6 Standardize REST URI Naming

**Gap Ref:** 5.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Standardize all REST endpoint URIs to follow RESTful naming conventions.

Changes needed:
1. internet-banking-user-service:
   - POST /api/v1/bank-users/register → POST /api/v1/bank-users
   - PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
   Update the API Gateway routing configuration to match.
   Update the SecurityConfiguration's permitAll path for registration.

2. core-banking-service:
   - /api/v1/account/bank-account/{account_number} → /api/v1/accounts/{account_number}
   - /api/v1/account/util-account/{account_name} → /api/v1/utility-accounts/{account_name}
   Update all Feign client interfaces that reference these paths.

3. Use consistent kebab-case for all path segments.

Make sure to update Feign client paths in all consuming services and the Postman collection.
```

---

### 2.7 Improve Logging Consistency and Structure

**Gap Refs:** 6.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Standardize logging across all microservices:

1. Ensure every controller class has @Slf4j annotation.
2. Add a logback-spring.xml to each service's src/main/resources/ with a structured
   JSON log format using Logstash encoder. Add 'net.logstash.logback:logstash-logback-encoder:7.4'
   to each service's build.gradle.
3. Include traceId and spanId in the log pattern so distributed trace correlation works
   with Zipkin.
4. Configure log levels in application.yml:
   - com.javatodev.finance: INFO
   - org.springframework.cloud: WARN
   - feign: DEBUG (for development profile only)
```

---

### 2.8 Add Custom Health Indicators

**Gap Ref:** 6.2 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add custom health indicators to each microservice:

1. For services with database connections (core-banking, user-service, fund-transfer,
   utility-payment): Spring Boot auto-configures DataSourceHealthIndicator, but ensure
   management.endpoint.health.show-details=always is set in application.yml.

2. For internet-banking-user-service: Add a KeycloakHealthIndicator that pings the
   Keycloak server URL and returns UP/DOWN.

3. For internet-banking-api-gateway: Add health indicators for Eureka connectivity
   and Config Server connectivity.

4. Configure readiness and liveness probes in application.yml:
   management.endpoint.health.probes.enabled=true
   management.health.readinessstate.enabled=true
   management.health.livenessstate.enabled=true

5. Expose health endpoints in actuator:
   management.endpoints.web.exposure.include=health,info,prometheus
```

---

## Phase 3: Polish (Nice-to-haves + Large effort improvements)

These items improve developer experience, documentation, and operational maturity but are lower priority.

### 3.1 Add Integration Tests

**Gap Ref:** 3.2 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests for each business service using Spring Boot Test with
Testcontainers for MySQL.

1. Add Testcontainers dependencies to each service:
   - testImplementation 'org.testcontainers:mysql:1.19.7'
   - testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service:
   - AccountControllerIntegrationTest: Test GET endpoints with @SpringBootTest
     and TestRestTemplate against a real MySQL container.
   - TransactionControllerIntegrationTest: Test fund transfer and utility payment
     end-to-end against the database.

3. For other services, use WireMock to stub Feign client responses:
   - testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'
   - FundTransferControllerIntegrationTest: Stub core-banking-service, test full flow.

4. Create a test profile that disables Eureka registration and Config Server bootstrap.
5. Add @ActiveProfiles("test") to all integration tests.
```

---

### 3.2 Add Consumer-Driven Contract Tests

**Gap Ref:** 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Set up Spring Cloud Contract for consumer-driven contract testing between services.

1. In core-banking-service (provider):
   - Add spring-cloud-starter-contract-verifier plugin to build.gradle.
   - Create contract DSL files in src/test/resources/contracts/ for each endpoint:
     fundTransfer, utilPayment, readBankAccount, readUser.
   - Generate and run contract tests.

2. In internet-banking-fund-transfer-service (consumer):
   - Add spring-cloud-starter-contract-stub-runner to build.gradle.
   - Write consumer-side tests that verify the Feign client behavior against
     the generated stubs from core-banking-service.

3. Repeat for internet-banking-utility-payment-service and internet-banking-user-service
   as consumers of core-banking-service.

4. Set up a local Maven repository or Artifactory for sharing contract stubs.
```

---

### 3.3 Enrich OpenAPI Documentation

**Gap Ref:** 5.4 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Enhance OpenAPI/Swagger documentation across all services:

1. Add @Schema annotations to all DTO classes with descriptions for each field.
2. Add @ApiResponse annotations to all controller methods documenting:
   - 200: Success response shape
   - 400: Validation error response
   - 404: Entity not found
   - 500: Internal server error
3. Add @Parameter annotations to path variables and query parameters.
4. Configure springdoc properties in each service's application.yml:
   springdoc.api-docs.path=/api-docs
   springdoc.swagger-ui.path=/swagger-ui.html
5. Add an API overview description via @OpenAPIDefinition on each Application class.
6. Ensure the Swagger UI is accessible through the API Gateway.
```

---

### 3.4 Add Filtering and Search to List Endpoints

**Gap Ref:** 5.6 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add filtering capabilities to list endpoints:

1. core-banking-service UserController.readUsers():
   - Add optional query parameters: firstName, lastName, email.
   - Use Spring Data JPA Specifications or query-by-example.

2. internet-banking-fund-transfer-service FundTransferController.readFundTransfers():
   - Add optional query parameters: status, fromAccount, toAccount, dateFrom, dateTo.

3. internet-banking-utility-payment-service UtilityPaymentController.readPayments():
   - Add optional query parameters: status, providerId, account, dateFrom, dateTo.

4. internet-banking-user-service UserController.readUsers():
   - Add optional query parameters: status, identification.

Use Spring Data JPA Specifications pattern for dynamic query building.
```

---

### 3.5 Set Up Prometheus Metrics and Grafana Dashboards

**Gap Ref:** 6.3 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Set up Prometheus metrics collection and Grafana dashboards:

1. Add 'io.micrometer:micrometer-registry-prometheus' to each service's build.gradle.
2. Configure each service's application.yml:
   management.endpoints.web.exposure.include=health,info,prometheus,metrics
   management.metrics.tags.application=${spring.application.name}

3. Add a Prometheus container to docker-compose.yml with a prometheus.yml config
   that scrapes all service /actuator/prometheus endpoints.

4. Add a Grafana container to docker-compose.yml with:
   - Prometheus as a pre-configured data source.
   - A JVM dashboard (import Grafana dashboard ID 4701).
   - A Spring Boot dashboard (import Grafana dashboard ID 12900).

5. Add custom business metrics using Micrometer:
   - Counter: fund_transfers_total (tagged by status)
   - Counter: utility_payments_total (tagged by status)
   - Timer: fund_transfer_duration_seconds
```

---

### 3.6 Convert to Multi-Module Gradle Build

**Gap Ref:** 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Convert the project to a multi-module Gradle build:

1. Create a root build.gradle with:
   - Common plugin versions defined in plugins block with apply false.
   - A subprojects block that sets group, Java version, and common repositories.
   - Common dependency versions in ext block.

2. Create a root settings.gradle that includes all modules:
   include 'banking-common', 'core-banking-service', 'internet-banking-api-gateway',
   'internet-banking-config-server', 'internet-banking-service-registry',
   'internet-banking-fund-transfer-service', 'internet-banking-user-service',
   'internet-banking-utility-payment-service'

3. Simplify each service's build.gradle to inherit common settings from the root.
4. Move shared dependency versions to a gradle/libs.versions.toml version catalog.
5. Verify all services build from the root: ./gradlew clean build.
```

---

### 3.7 Add CI/CD Pipeline

**Gap (not in original analysis — new recommendation)** | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline in .github/workflows/ci.yml:

1. Trigger on: push to main, pull requests to main.
2. Jobs:
   a. build-and-test: Matrix strategy for all 7 services.
      - Set up Java 21 (Temurin).
      - Run ./gradlew build for each service.
      - Upload test reports as artifacts.
   b. docker-build: Build Docker images for each service (build only, no push).
      - Depends on build-and-test passing.
3. Cache Gradle dependencies between runs.
4. Add a badge to the README.
```

---

## Summary Timeline

| Phase | Items | Estimated Effort | Focus |
|---|---|---|---|
| **Phase 1** | 9 items | ~2 weeks | Fix critical bugs, security holes, basic resilience |
| **Phase 2** | 8 items | ~3–4 weeks | Architecture hardening, testing, authorization |
| **Phase 3** | 7 items | ~3–4 weeks | Polish, observability, CI/CD, documentation |

### Recommended Execution Order Within Phase 1

1. **1.1** Fix balance calculation bug (data integrity — immediate)
2. **1.2** Fix exception handlers (security + correctness)
3. **1.4** Remove sensitive data from logs (security)
4. **1.5** Fix Keycloak singleton thread-safety (reliability)
5. **1.3** Add input validation (security)
6. **1.8** Add Feign error handling (resilience)
7. **1.6** Add pagination metadata (API quality)
8. **1.7** Configure Feign timeouts + retries (resilience)
9. **1.9** Externalize Docker Compose credentials (security hygiene)
