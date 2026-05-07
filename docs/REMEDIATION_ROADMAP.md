# Internet Banking Microservices - Remediation Roadmap

## Overview

This roadmap prioritizes the 37 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 (Quick Wins):** High-impact, low-effort items that improve security, correctness, and stability immediately.
- **Phase 2 (Important):** Medium-effort improvements that bring the codebase to production-grade quality.
- **Phase 3 (Polish):** Larger structural improvements and nice-to-haves for long-term maintainability.

---

## Phase 1: Quick Wins (1-2 Weeks)

Items that can be completed individually in hours, with immediate impact on correctness, security, or reliability.

### 1.1 Fix Balance Calculation Bug (Critical)

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `utilPayment()`. This is a data-corrupting production bug.

**Devin Prompt:**
```
In core-banking-service, fix the balance calculation bug in TransactionService.java.
In both internalFundTransfer() and utilPayment(), the availableBalance is set using
actualBalance AFTER it has already been subtracted, causing a double subtraction.
The fix: set availableBalance by subtracting amount from the ORIGINAL balance, or
set availableBalance equal to actualBalance after the subtraction (they should be
the same for these operations). Apply the same fix to both the debit and credit
sides. Update the existing unit tests in TransactionServiceTest.java to assert
correct balance values after transfers.
```

### 1.2 Fix Error HTTP Status Codes (High)

**Gap:** 2.1, 2.2, 2.3 | **Severity:** High | **Effort:** Small

All exceptions currently return 400. Fix the `GlobalExceptionHandler` in all four business services.

**Devin Prompt:**
```
Update the GlobalExceptionHandler in all four services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to return proper HTTP status codes:
- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException, InvalidBankingUserException -> 400 Bad Request
- Generic Exception -> 500 Internal Server Error

Replace the catch-all handler that leaks exception details with a safe generic
error response using the same ErrorResponse format. Never expose stack traces
or internal class names in error responses.
```

### 1.3 Add Input Validation (Critical)

**Gap:** 4.1 | **Severity:** Critical | **Effort:** Medium

No request body validation exists anywhere.

**Devin Prompt:**
```
Add Jakarta Bean Validation to all request DTOs across all services:

1. Add spring-boot-starter-validation dependency to each service's build.gradle.

2. In core-banking-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - UtilityPaymentRequest: @NotBlank account, @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber

3. In internet-banking-user-service:
   - User (registration): @NotBlank identification, @Email @NotBlank email, @NotBlank password
   - UserUpdateRequest: @NotNull status

4. In internet-banking-fund-transfer-service:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

5. In internet-banking-utility-payment-service:
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

6. Add @Valid annotation to all @RequestBody parameters in controllers.

7. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler
   that returns 400 with field-level error details.
```

### 1.4 Secure Actuator Endpoints (High)

**Gap:** 4.4 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-api-gateway SecurityConfiguration.java, restrict actuator
endpoint access. Change the permitAll() rules for actuator endpoints to only
allow /actuator/health and /actuator/info publicly. All other actuator endpoints
(env, beans, metrics, heapdump, etc.) should require authentication. Update:

exchanges.pathMatchers("/actuator/health", "/actuator/info").permitAll()
    .pathMatchers("/user/actuator/health", "/user/actuator/info").permitAll()
    .pathMatchers("/fund-transfer/actuator/health", "/fund-transfer/actuator/info").permitAll()
    .pathMatchers("/banking-core/actuator/health", "/banking-core/actuator/info").permitAll()
    .pathMatchers("/utility-payment/actuator/health", "/utility-payment/actuator/info").permitAll()

Also add management.endpoints.web.exposure.include=health,info,metrics,prometheus
to each service's config to limit which actuator endpoints are exposed at all.
```

### 1.5 Externalize Docker Compose Credentials (High)

**Gap:** 4.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In docker-compose/docker-compose.yml and docker-compose-support-apps.yml, replace
all hardcoded passwords with environment variable references using ${VAR:-default}
syntax. Create a .env.example file in the docker-compose directory with all
required variables documented. Add docker-compose/.env to .gitignore.

Variables to externalize:
- MYSQL_ROOT_PASSWORD
- KEYCLOAK_ADMIN_PASSWORD
- KC_DB_PASSWORD
- POSTGRES_PASSWORD
- MYSQL_APP_USER_PASSWORD (for privileges.sql - use an entrypoint script)
```

### 1.6 Add Feign Timeout Configuration (High)

**Gap:** 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add default Feign client timeout configuration to each service that uses Feign
(internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service). Add the following to each service's
application.yml:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
            loggerLevel: BASIC

This ensures Feign calls fail fast (5s connect, 10s read) instead of hanging
indefinitely when core-banking-service is slow or unreachable.
```

### 1.7 Add Feign Retry Configuration (High)

**Gap:** 7.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add a Spring Retry or Feign Retryer bean to each service that uses Feign clients
(internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service).

Create a FeignRetryConfiguration class in each service's configuration package:

@Configuration
public class FeignRetryConfiguration {
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(1000, 3000, 3); // 1s initial, 3s max, 3 attempts
    }
}

Note: Only retry on GET requests and idempotent operations. POST fund-transfer
and utility-payment should NOT be retried (not idempotent). Configure the retry
to apply only to read operations.
```

### 1.8 Fix Logging Issues (Medium)

**Gap:** 6.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix logging issues across all services:

1. In FundTransferService.java line 32, fix the string concatenation bug:
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To:     log.info("Sending fund transfer request {}", request)

2. Remove all explicit .toString() calls in log statements (SLF4J handles this
   automatically and avoids NPEs).

3. Standardize log format across all controllers to use a consistent pattern:
   log.info("[ServiceName] Operation description - key={}", value)

4. Add logback-spring.xml to each service with JSON structured logging format
   for production profile.
```

---

## Phase 2: Important (3-6 Weeks)

Medium-effort improvements that bring the codebase to production readiness.

### 2.1 Add Circuit Breakers (Critical)

**Gap:** 7.1 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls:

1. Add these dependencies to build.gradle in user-service, fund-transfer-service,
   and utility-payment-service:
   - implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable Resilience4j for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true

3. Create fallback classes for each Feign client:
   - BankingCoreRestClientFallback (user-service)
   - BankingCoreFeignClientFallback (fund-transfer-service)
   - BankingCoreRestClientFallback (utility-payment-service)

4. Each fallback should return a meaningful error response or throw a service-
   specific exception (e.g., CoreBankingUnavailableException).

5. Configure circuit breaker parameters in application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 5
```

### 2.2 Add Unit Tests for All Services (Critical)

**Gap:** 3.1 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests for internet-banking-user-service:

1. Create UserServiceTest: test createUser (happy path, duplicate email, user not
   found in core, email mismatch, Keycloak failure), readUsers, readUser, updateUser
   (approve flow, status update). Mock KeycloakUserService, UserRepository, and
   BankingCoreRestClient.

2. Create KeycloakUserServiceTest: test createUser, updateUser, readUserByEmail,
   readUser (happy path and not found). Mock KeycloakManager.

3. Create UserControllerTest using @WebMvcTest: test all endpoints with MockMvc,
   verify request/response serialization and HTTP status codes.

Use Mockito for mocking. Follow the same patterns used in the existing
core-banking-service tests. Ensure all tests pass with ./gradlew test.
```

**Devin Prompt (fund-transfer-service):**
```
Add comprehensive unit tests for internet-banking-fund-transfer-service:

1. Create FundTransferServiceTest: test fundTransfer (happy path, core-banking
   failure, repository save failure), readAllTransfers. Mock
   FundTransferRepository and BankingCoreFeignClient.

2. Create FundTransferControllerTest using @WebMvcTest: test POST /api/v1/transfer
   and GET /api/v1/transfer with MockMvc.

Follow existing test patterns from core-banking-service. Use H2 test config.
```

**Devin Prompt (utility-payment-service):**
```
Add comprehensive unit tests for internet-banking-utility-payment-service:

1. Create UtilityPaymentServiceTest: test utilPayment (happy path, core-banking
   failure, repository save failure), readPayments. Mock UtilityPaymentRepository
   and BankingCoreRestClient.

2. Create UtilityPaymentControllerTest using @WebMvcTest: test POST and GET
   /api/v1/utility-payment with MockMvc.

Follow existing test patterns from core-banking-service. Use H2 test config.
```

### 2.3 Add Custom Health Checks (Medium)

**Gap:** 6.2 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom Spring Boot Actuator health indicators:

1. In internet-banking-user-service, create KeycloakHealthIndicator that checks
   Keycloak server availability by calling the realm endpoint.

2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-
   service, create CoreBankingHealthIndicator that checks core-banking-service
   availability via its actuator health endpoint.

3. Register each as a @Component implementing HealthIndicator.

4. Add management.endpoint.health.show-details=when-authorized to each service's
   config so details are visible to authenticated users only.
```

### 2.4 Add Pagination Response Wrapper (Medium)

**Gap:** 5.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Create a standardized pagination response wrapper and apply it to all paginated
endpoints across all services.

1. Create a generic PagedResponse<T> class with fields: content (List<T>),
   page (int), size (int), totalElements (long), totalPages (int).

2. Update all services that return paginated data to use PagedResponse<T>:
   - core-banking UserController.readUsers()
   - user-service UserController.readUsers()
   - fund-transfer FundTransferController.readFundTransfers()
   - utility-payment UtilityPaymentController.readPayments()

3. Map Spring's Page object to PagedResponse in each service method.
```

### 2.5 Type ResponseEntity Generic Parameters (Medium)

**Gap:** 5.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add proper generic type parameters to all ResponseEntity return types in
controllers across all services. For example:

- AccountController.getBankAccount() -> ResponseEntity<BankAccount>
- TransactionController.fundTransfer() -> ResponseEntity<FundTransferResponse>
- FundTransferController.sendFundTransfer() -> ResponseEntity<FundTransferResponse>
- UtilityPaymentController.processPayment() -> ResponseEntity<UtilityPaymentResponse>

This improves type safety and generates accurate OpenAPI documentation.
```

### 2.6 Add Feign Error Decoder (Medium)

**Gap:** 2.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add proper Feign error decoding to fund-transfer-service and utility-payment-service.

The CustomFeignClientConfiguration already exists but needs a proper ErrorDecoder.
The error decoder in CustomFeignErrorDecoder (user-service) can be used as reference.

1. Create/update FeignErrorDecoder in each service that translates downstream HTTP
   errors into meaningful domain exceptions:
   - 404 from core-banking -> EntityNotFoundException
   - 422 from core-banking -> InsufficientFundsException or similar
   - 5xx from core-banking -> CoreBankingServiceException (new)
   - Connection failures -> ServiceUnavailableException (new)

2. Register the error decoder in CustomFeignClientConfiguration.
```

### 2.7 Add Dependency Vulnerability Scanning (Medium)

**Gap:** 4.7 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency Check plugin to all service build.gradle files:

1. Add to each build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure the plugin:
   dependencyCheck {
       failBuildOnCVSS = 7
       suppressionFile = "${rootProject.projectDir}/owasp-suppressions.xml"
   }

3. Create a shared owasp-suppressions.xml for known false positives.
4. Add a Gradle task alias: ./gradlew dependencyCheckAnalyze
```

### 2.8 Fix Keycloak Thread Safety (Medium)

**Gap:** 4.5 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix the thread-safety issue in KeycloakProperties.java in internet-banking-user-
service. Replace the manual lazy singleton with a proper Spring-managed bean:

1. Remove the static keycloakInstance field and getInstance() method from
   KeycloakProperties.

2. Create a @Configuration class (KeycloakConfig) that defines a @Bean for
   Keycloak, injecting KeycloakProperties values.

3. Update KeycloakManager to inject the Keycloak bean directly instead of
   calling keycloakProperties.getInstance().

This leverages Spring's built-in singleton scope which is thread-safe.
```

### 2.9 Add Rate Limiting (Medium)

**Gap:** 4.6 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in
RequestRateLimiter filter with Redis (or in-memory for development).

1. Add spring-boot-starter-data-redis-reactive dependency to the API gateway.
2. Configure a RedisRateLimiter bean with:
   - replenishRate: 10 (requests per second)
   - burstCapacity: 20
3. Apply the rate limiter filter to sensitive routes:
   - /user/api/v1/bank-users/register (public, abuse-prone)
   - /fund-transfer/** (financial transactions)
   - /utility-payment/** (financial transactions)
4. Return HTTP 429 Too Many Requests when rate limit is exceeded.
5. For development without Redis, provide an in-memory fallback.
```

### 2.10 Fix OpenAPI Dependency (Low)

**Gap:** 5.5 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Fix the OpenAPI dependency in core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

These are servlet-based (Spring MVC) applications but include the WebFlux OpenAPI
dependency. Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

Verify the Swagger UI is accessible at /swagger-ui.html after the change.
```

---

## Phase 3: Polish (6-12 Weeks)

Larger structural improvements for long-term maintainability and operational excellence.

### 3.1 Create Shared Library Module (Medium)

**Gap:** 1.1, 1.2 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Restructure the project into a Gradle multi-project build with a shared library:

1. Create a root settings.gradle that includes all services:
   include 'core-banking-service', 'internet-banking-api-gateway', etc.

2. Create a root build.gradle with shared configurations (Java 21, Spring Boot
   3.2.4, Spring Cloud 2023.0.0, common dependencies).

3. Create a new module 'banking-common' containing shared classes:
   - BaseMapper
   - AuditAware, AuditConfig, AuditorAwareConfig
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - GlobalErrorCode
   - CustomFeignClientConfiguration
   - PagedResponse

4. Update each service's build.gradle to depend on banking-common:
   implementation project(':banking-common')

5. Remove duplicated classes from each service.
6. Verify all services build and tests pass.
```

### 3.2 Add Integration Tests (High)

**Gap:** 3.2 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests to core-banking-service using @SpringBootTest with H2:

1. Create AccountControllerIntegrationTest with @SpringBootTest and MockMvc:
   - Test GET /api/v1/account/bank-account/{number} returns correct account
   - Test GET /api/v1/account/bank-account/{number} returns 404 for missing
   - Test GET /api/v1/account/util-account/{name} works correctly

2. Create TransactionControllerIntegrationTest:
   - Test full fund transfer flow end-to-end with real DB
   - Verify balance changes in both accounts
   - Test insufficient funds scenario
   - Test utility payment flow

3. Create test application.yml with H2 config and test data SQL.
4. Use @Transactional on tests for automatic rollback.
5. All tests should pass with ./gradlew test.
```

### 3.3 Add Contract Tests (Medium)

**Gap:** 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between consumer services and core-banking-service:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   - spring-cloud-starter-contract-verifier
   - spring-cloud-contract-spec-kotlin (or Groovy DSL)

2. Define contracts for each API used by consumers:
   - GET /api/v1/user/{identification} (used by user-service)
   - GET /api/v1/account/bank-account/{account_number} (used by fund-transfer, utility-payment)
   - POST /api/v1/transaction/fund-transfer (used by fund-transfer)
   - POST /api/v1/transaction/util-payment (used by utility-payment)

3. Generate and verify producer stubs.

4. Add stub dependencies to consumer services for Feign client testing.
```

### 3.4 Implement Saga / Outbox Pattern (High)

**Gap:** 7.5 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Implement a transactional outbox pattern for fund transfers to ensure data
consistency between fund-transfer-service and core-banking-service:

1. Add an outbox table to the fund-transfer-service database:
   CREATE TABLE outbox_event (
       id BIGINT AUTO_INCREMENT PRIMARY KEY,
       aggregate_type VARCHAR(255),
       aggregate_id VARCHAR(255),
       event_type VARCHAR(255),
       payload JSON,
       status VARCHAR(50) DEFAULT 'PENDING',
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

2. Instead of calling core-banking-service synchronously in the same request,
   save the fund transfer and an outbox event in the same local transaction.

3. Create a scheduled job (@Scheduled) that polls the outbox table, sends events
   to core-banking-service, and marks them as PROCESSED or FAILED.

4. Add idempotency keys to fund transfer requests so retries are safe.

5. Implement the same pattern for utility-payment-service.

This ensures that if the local save succeeds, the event will eventually be
processed, and if the local save fails, no event is created.
```

### 3.5 Add Prometheus Metrics (Low)

**Gap:** 6.3 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add micrometer-registry-prometheus dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose Prometheus endpoint in application.yml:
   management.endpoints.web.exposure.include: health,info,metrics,prometheus

3. Add custom metrics for business operations:
   - Counter: fund_transfers_total (tags: status=success|failure)
   - Counter: utility_payments_total (tags: status=success|failure)
   - Counter: user_registrations_total (tags: status=success|failure)
   - Timer: fund_transfer_duration_seconds
   - Timer: utility_payment_duration_seconds
   - Gauge: active_users_count

4. Instrument service methods using Micrometer's MeterRegistry.

5. Add a docker-compose service for Prometheus with a preconfigured
   prometheus.yml that scrapes all service /actuator/prometheus endpoints.
```

### 3.6 Standardize Package Structure (Low)

**Gap:** 1.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance.{service-name}
  ├── config/          (all configuration: audit, feign, filter, security, keycloak)
  ├── controller/      (REST controllers)
  ├── dto/             (request, response, and model DTOs)
  │   ├── request/
  │   └── response/
  ├── entity/          (JPA entities)
  ├── exception/       (exception classes and handlers)
  ├── mapper/          (DTO-entity mappers)
  ├── repository/      (Spring Data repositories)
  └── service/         (business logic)
      └── client/      (Feign clients)

Refactor each service to match this structure. Update all imports.
Verify compilation and tests pass after refactoring.
```

### 3.7 Add Missing CRUD Operations (Low)

**Gap:** 5.6 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
Add missing CRUD endpoints to core-banking-service:

1. AccountController:
   - POST /api/v1/account/bank-account (create bank account)
   - PUT /api/v1/account/bank-account/{number} (update account status)
   - GET /api/v1/account/bank-account (list all accounts, paginated)

2. UserController:
   - POST /api/v1/user (create user)
   - PUT /api/v1/user/{identification} (update user)
   - DELETE /api/v1/user/{identification} (soft delete user)

3. Add corresponding service methods, DTOs, and validation.
4. Add unit tests for all new endpoints.
5. Update OpenAPI annotations.
```

### 3.8 Convert Mappers to Spring Beans (Low)

**Gap:** 1.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Convert all mapper classes from direct instantiation to Spring-managed beans:

1. Add @Component annotation to all mapper classes:
   - BankAccountMapper, UserMapper, UtilityAccountMapper (core-banking)
   - UserMapper (user-service)
   - FundTransferMapper (fund-transfer-service)
   - UtilityPaymentMapper (utility-payment-service)

2. Replace direct instantiation in service classes:
   Change: private UserMapper userMapper = new UserMapper();
   To:     private final UserMapper userMapper; (injected via constructor)

3. Update existing unit tests to mock mappers where needed.
4. Verify all tests pass.
```

---

## Summary Timeline

| Phase | Duration | Items | Critical Fixes | Key Outcomes |
|---|---|---|---|---|
| **Phase 1** | 1-2 weeks | 8 items | Balance bug, input validation, error codes | Correct data, secure endpoints, reliable timeouts |
| **Phase 2** | 3-6 weeks | 10 items | Circuit breakers, unit tests | Production-grade resilience and test coverage |
| **Phase 3** | 6-12 weeks | 8 items | Saga pattern, integration tests | Long-term maintainability and operational excellence |

### Priority Order Within Each Phase

**Phase 1 (do in this order):**
1. Fix balance calculation bug (data corruption)
2. Add input validation (security)
3. Fix error HTTP status codes (correctness)
4. Secure actuator endpoints (security)
5. Externalize credentials (security)
6. Add Feign timeouts (reliability)
7. Add Feign retries (reliability)
8. Fix logging issues (observability)

**Phase 2 (do in this order):**
1. Add circuit breakers (resilience)
2. Add unit tests for all services (quality)
3. Fix Keycloak thread safety (correctness)
4. Add Feign error decoders (error handling)
5. Add custom health checks (observability)
6. Add pagination response wrapper (API quality)
7. Type ResponseEntity generics (API quality)
8. Add rate limiting (security)
9. Add dependency vulnerability scanning (security)
10. Fix OpenAPI dependency (API docs)

**Phase 3 (do in this order):**
1. Add integration tests (quality)
2. Implement saga/outbox pattern (data consistency)
3. Create shared library module (maintainability)
4. Add contract tests (inter-service quality)
5. Add Prometheus metrics (observability)
6. Standardize package structure (maintainability)
7. Add missing CRUD operations (completeness)
8. Convert mappers to Spring beans (code quality)
