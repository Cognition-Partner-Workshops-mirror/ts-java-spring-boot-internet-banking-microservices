# Remediation Roadmap

## Table of Contents

- [Phase 1: Quick Wins (Critical fixes, Small effort)](#phase-1-quick-wins)
- [Phase 2: Important (High-value improvements, Medium effort)](#phase-2-important)
- [Phase 3: Polish (Long-term quality, Larger effort)](#phase-3-polish)
- [Execution Summary](#execution-summary)

---

## Phase 1: Quick Wins

**Goal**: Fix critical security and correctness issues that can be addressed with small, focused changes. Estimated timeline: 1-2 weeks.

---

### 1.1 Fix Balance Update Bug (Gap 7.7)

**Severity: Critical** | **Effort: Small** | **Priority: Immediate**

The `availableBalance` calculation in `TransactionService` double-subtracts the amount. This is a data integrity bug in production-critical financial logic.

**What to fix**:
- `internalFundTransfer()`: Change `setAvailableBalance(getActualBalance().subtract(amount))` to `setAvailableBalance(getActualBalance())` (after actualBalance is already updated)
- `utilPayment()`: Same fix for the debit calculation
- Add pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to `BankAccountRepository.findByNumber()`

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix the balance calculation bug in core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java.

In both internalFundTransfer() and utilPayment(), the availableBalance is incorrectly set by subtracting amount from the already-debited actualBalance. After setting actualBalance, set availableBalance equal to actualBalance (not actualBalance minus amount again).

Also add @Lock(LockModeType.PESSIMISTIC_WRITE) to the findByNumber method in BankAccountRepository to prevent concurrent balance update race conditions. Update the existing unit tests to verify the correct balance values after transfers.
```

---

### 1.2 Stop Leaking Exception Details (Gap 2.2)

**Severity: Critical** | **Effort: Small** | **Priority: Immediate**

Raw Java exception details are returned to clients in all 4 services' `GlobalExceptionHandler`.

**What to fix**:
- Replace `"Exception occur inside API " + e` with a generic message and log the actual exception server-side
- Return HTTP 500 instead of 400 for unhandled exceptions

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix the GlobalExceptionHandler in all 4 business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service).

In each GlobalExceptionHandler, change the catch-all Exception handler to:
1. Log the full exception with log.error("Unhandled exception", e)
2. Return HTTP 500 (ResponseEntity.internalServerError()) instead of 400
3. Return a generic ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred. Please try again later." instead of exposing raw exception details
4. Add @Slf4j annotation to the class

Do NOT change the SimpleBankingGlobalException handler.
```

---

### 1.3 Use Correct HTTP Status Codes (Gap 2.1)

**Severity: High** | **Effort: Small** | **Priority: Week 1**

All custom exceptions return HTTP 400. Map them to proper status codes.

**What to fix**:
- `EntityNotFoundException` → HTTP 404
- `InsufficientFundsException` → HTTP 422
- `UserAlreadyRegisteredException` → HTTP 409
- `InvalidEmailException` / `InvalidBankingUserException` → HTTP 400 (keep as-is)

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler in all 4 business services to return proper HTTP status codes.

Add separate @ExceptionHandler methods for:
- EntityNotFoundException → return ResponseEntity.status(HttpStatus.NOT_FOUND) with ErrorResponse
- InsufficientFundsException → return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY) with ErrorResponse

In the user-service GlobalExceptionHandler, also add:
- UserAlreadyRegisteredException → return ResponseEntity.status(HttpStatus.CONFLICT) with ErrorResponse

Keep InvalidEmailException and InvalidBankingUserException as HTTP 400.
```

---

### 1.4 Externalize Hardcoded Credentials (Gap 4.1)

**Severity: Critical** | **Effort: Small** | **Priority: Week 1**

Move all hardcoded passwords to environment variables in Docker Compose.

**What to fix**:
- Replace hardcoded passwords in `docker-compose.yml` and `docker-compose-support-apps.yml` with `${VAR:-default}` syntax
- Update `mysql/Dockerfile` to use `ARG` instead of hardcoded `ENV`
- Add a `.env.example` file documenting required variables
- Add `*.env` to `.gitignore`

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, externalize all hardcoded credentials from Docker Compose files and MySQL Dockerfile.

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD:-changeme}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD:-changeme}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD:-changeme}

2. In docker-compose/mysql/Dockerfile:
   - Remove the hardcoded ENV MYSQL_ROOT_PASSWORD line (it will come from docker-compose)

3. In docker-compose/mysql/privileges.sql:
   - Document that the password should be changed for production

4. Create docker-compose/.env.example with all required environment variables and safe defaults for local development

5. Add .env to the root .gitignore
```

---

### 1.5 Add Input Validation (Gap 4.2)

**Severity: Critical** | **Effort: Small-Medium** | **Priority: Week 1-2**

Add Bean Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Bean Validation to all request DTOs and controllers across all services.

1. Add spring-boot-starter-validation dependency to build.gradle for core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service.

2. Add validation annotations to request DTOs:
   - FundTransferRequest (core-banking & fund-transfer): @NotBlank on fromAccount and toAccount, @NotNull @DecimalMin("0.01") on amount
   - UtilityPaymentRequest (core-banking & utility-payment): @NotNull on providerId, @NotNull @DecimalMin("0.01") on amount, @NotBlank on referenceNumber and account
   - User (user-service): @NotBlank @Email on email, @NotBlank on identification, @NotBlank @Size(min=8) on password

3. Add @Valid annotation before @RequestBody in all controller methods that accept request bodies.

4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns HTTP 400 with field-level error details.
```

---

### 1.6 Fix OpenAPI Dependency (Gap 5.4)

**Severity: Medium** | **Effort: Small** | **Priority: Week 2**

Non-gateway services use Spring MVC but have the WebFlux OpenAPI starter.

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix the OpenAPI/Swagger dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

In each service's build.gradle, replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

These services use Spring MVC (spring-boot-starter-web), not WebFlux, so the webflux starter is incorrect. Also update the version to 2.5.0 (latest stable).

Additionally, add typed ResponseEntity<T> return types to all controller methods that currently return raw ResponseEntity, so OpenAPI can generate accurate response schemas.
```

---

### 1.7 Fix Keycloak Singleton Thread Safety (Gap 4.4)

**Severity: Medium** | **Effort: Small** | **Priority: Week 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix the thread-safety issue in internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java.

Replace the lazy singleton pattern with a @Bean method in a @Configuration class. Create a new KeycloakConfig.java file that provides the Keycloak instance as a Spring-managed singleton bean. Remove the static field and getInstance() method from KeycloakProperties. Update KeycloakManager to inject the Keycloak bean directly.
```

---

### 1.8 Fix Logging Issues (Gap 6.1)

**Severity: Medium** | **Effort: Small** | **Priority: Week 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix logging issues across all services:

1. In internet-banking-fund-transfer-service FundTransferService.java, change the string concatenation log:
   log.info("Sending fund transfer request {}" + request.toString())
   to parameterized logging:
   log.info("Sending fund transfer request {}", request)

2. In internet-banking-user-service KeycloakUserService.java, remove the manual LoggerFactory import (line 8-9) since @Slf4j is already used.

3. Add logback-spring.xml to each service's src/main/resources/ with JSON-formatted logging for the docker profile and human-readable logging for the default profile.
```

---

### 1.9 Expose Prometheus Metrics (Gap 6.3)

**Severity: Medium** | **Effort: Small** | **Priority: Week 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics support to all services.

1. Add micrometer-registry-prometheus dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. The actuator endpoints should be configured via Spring Cloud Config, but verify each service's application.yml or test application.yml includes:
   management.endpoints.web.exposure.include: health,info,prometheus,metrics
   management.endpoint.health.show-details: always

3. The API Gateway's SecurityConfiguration already permits /actuator/** paths, so no security changes needed.
```

---

## Phase 2: Important

**Goal**: Improve resilience, testability, and code quality. Estimated timeline: 3-6 weeks.

---

### 2.1 Add Circuit Breakers with Resilience4j (Gap 7.1)

**Severity: Critical** | **Effort: Medium** | **Priority: Week 3-4**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers to all Feign client calls.

1. Add these dependencies to build.gradle for internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breakers for Feign by adding to each service's application.yml:
   spring.cloud.openfeign.circuitbreaker.enabled: true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClient in fund-transfer: return a FundTransferResponse with error message and FAILED status
   - BankingCoreRestClient in utility-payment: return a UtilityPaymentResponse with error message and FAILED status
   - BankingCoreRestClient in user-service: throw a descriptive exception that the core banking service is unavailable

4. Register fallback factories with @FeignClient(fallbackFactory = ...) on each client interface.

5. Configure circuit breaker thresholds in application.yml:
   - failure-rate-threshold: 50%
   - wait-duration-in-open-state: 30s
   - sliding-window-size: 10
```

---

### 2.2 Add Feign Timeout and Retry Configuration (Gaps 7.2, 7.3)

**Severity: High** | **Effort: Small** | **Priority: Week 3**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add timeout and retry configuration for all Feign clients.

Add the following to each service's application.yml (or via Spring Cloud Config):

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
            retryer: feign.Retryer.Default
          core-banking-service:
            connectTimeout: 5000
            readTimeout: 15000

Configure a Spring Retry bean with max 3 attempts and 1-second backoff for transient failures (5xx responses, connection errors). Do NOT retry POST requests to financial endpoints to avoid duplicate transactions — only retry GET requests.
```

---

### 2.3 Add Idempotency for Financial Operations (Gap 7.6)

**Severity: High** | **Effort: Medium** | **Priority: Week 3-4**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add idempotency support for fund transfer and utility payment operations.

1. Add an X-Idempotency-Key header requirement to POST endpoints in fund-transfer and utility-payment controllers.

2. Create an idempotency_key table in each service's database with columns: id, idempotency_key (unique), response_body, created_at, expires_at.

3. Before processing a fund transfer or utility payment:
   - Check if the idempotency key already exists in the database
   - If it does, return the stored response (HTTP 200)
   - If not, process the request, store the response with the key, and return it

4. Add a @UniqueConstraint on the idempotency_key column and handle duplicate key exceptions gracefully.

5. Add a scheduled job to clean up expired idempotency records (older than 24 hours).
```

---

### 2.4 Create Shared Library Module (Gap 1.2)

**Severity: High** | **Effort: Medium** | **Priority: Week 4-5**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, create a shared library module to eliminate code duplication.

1. Create a root-level settings.gradle that includes all service modules and a new internet-banking-common module.

2. Create internet-banking-common/build.gradle as a plain Java library (no Spring Boot plugin, just spring-boot-dependencies BOM for dependency management).

3. Move these classes into internet-banking-common with package com.javatodev.finance.common:
   - exception/SimpleBankingGlobalException
   - exception/ErrorResponse
   - exception/GlobalExceptionHandler (as a base class)
   - exception/EntityNotFoundException
   - model/mapper/BaseMapper
   - model/dto/AuditAware
   - configuration/audit/AuditConfig + AuditorAwareConfig
   - configuration/filter/AppAuthUserFilter + ApiRequestContext + ApiRequestContextHolder
   - model/TransactionStatus

4. Add implementation project(':internet-banking-common') to each service's build.gradle.

5. Update imports in all services to use the common package.

6. Delete the duplicated classes from each service.
```

---

### 2.5 Add Unit Tests for All Business Services (Gap 3.1)

**Severity: Critical** | **Effort: Large** | **Priority: Week 4-6**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests for the 3 business services that currently have no tests.

For internet-banking-user-service, create test classes:
- UserServiceTest: test createUser (success, duplicate email, email mismatch, user not found in core banking), readUser, readUsers, updateUser (approve flow)
- KeycloakUserServiceTest: test createUser, readUser, readUserByEmail, updateUser with mocked KeycloakManager

For internet-banking-fund-transfer-service, create test classes:
- FundTransferServiceTest: test fundTransfer (success flow, core banking error propagation), readAllTransfers with mocked BankingCoreFeignClient and FundTransferRepository

For internet-banking-utility-payment-service, create test classes:
- UtilityPaymentServiceTest: test utilPayment (success flow, core banking error propagation), readPayments with mocked BankingCoreRestClient and UtilityPaymentRepository

Use Mockito for all external dependencies (Feign clients, Keycloak, repositories). Each test class should have @BeforeEach setup with mocks. Aim for >80% line coverage on service classes.
```

---

### 2.6 Add Pagination Metadata to Responses (Gap 5.2)

**Severity: Medium** | **Effort: Small** | **Priority: Week 4**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix paginated endpoints to return proper pagination metadata.

1. Create a generic PageResponse<T> class in the common library (or each service) with fields:
   - content: List<T>
   - page: int
   - size: int
   - totalElements: long
   - totalPages: int

2. Update these controller methods to return ResponseEntity<PageResponse<T>> instead of ResponseEntity<List<T>>:
   - core-banking UserController.readUsers()
   - user-service UserController.readUsers()
   - fund-transfer FundTransferController.readFundTransfers()
   - utility-payment UtilityPaymentController.readPayments()

3. Update the corresponding service methods to return PageResponse with the Page metadata from Spring Data.
```

---

### 2.7 Add Rate Limiting at API Gateway (Gap 4.5)

**Severity: Medium** | **Effort: Medium** | **Priority: Week 5**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add rate limiting to the API Gateway using Spring Cloud Gateway's built-in RequestRateLimiter filter.

1. Add Redis dependency to internet-banking-api-gateway build.gradle:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Add a Redis container to docker-compose.yml.

3. Configure rate limiting in the gateway's application.yml (via Spring Cloud Config):
   - Default rate: 100 requests/second per user
   - Fund transfer endpoints: 10 requests/second per user
   - Utility payment endpoints: 10 requests/second per user
   - Use the JWT subject claim as the key resolver

4. Create a custom KeyResolver bean that extracts the user identity from the JWT token.
```

---

### 2.8 Add Dependency Vulnerability Scanning (Gap 4.6)

**Severity: Medium** | **Effort: Small** | **Priority: Week 3**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add OWASP Dependency-Check to the Gradle build.

1. Add the OWASP dependency-check plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.1.0'
   }

2. Configure the plugin to fail the build on CVSS score >= 7:
   dependencyCheck {
       failBuildOnCVSS = 7.0f
       formats = ['HTML', 'JSON']
   }

3. Add a GitHub Actions workflow (.github/workflows/security-scan.yml) that runs ./gradlew dependencyCheckAnalyze on push to main and on PRs. Upload the HTML report as an artifact.
```

---

## Phase 3: Polish

**Goal**: Achieve production-grade quality with comprehensive testing, advanced resilience patterns, and operational excellence. Estimated timeline: 2-3 months.

---

### 3.1 Add Integration Tests (Gap 3.2)

**Severity: High** | **Effort: Large** | **Priority: Month 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers for all business services.

1. Add Testcontainers dependencies to each service's build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service, create integration tests:
   - AccountControllerIntegrationTest: @SpringBootTest with @Testcontainers, use MySQLContainer, test GET endpoints with real database
   - TransactionControllerIntegrationTest: test fund transfer and utility payment with real database, verify balance changes and transaction records
   - Verify Flyway migrations run successfully against MySQL

3. For fund-transfer and utility-payment services:
   - Use WireMock to stub the core-banking-service Feign client
   - Test the full request flow from controller to database

4. For user-service:
   - Use WireMock for core-banking-service stub
   - Use Testcontainers Keycloak container for Keycloak integration
   - Test user registration and approval flow end-to-end

5. Create a test profile (application-integration-test.yml) for each service with Testcontainers configuration.
```

---

### 3.2 Add Contract Tests (Gap 3.3)

**Severity: High** | **Effort: Medium** | **Priority: Month 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests between services.

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   Add the spring-cloud-contract-gradle-plugin.

2. Create contract definitions in core-banking-service/src/test/resources/contracts/:
   - fundTransfer.groovy: defines the POST /api/v1/transaction/fund-transfer contract
   - utilPayment.groovy: defines the POST /api/v1/transaction/util-payment contract
   - readAccount.groovy: defines the GET /api/v1/account/bank-account/{number} contract
   - readUser.groovy: defines the GET /api/v1/user/{identification} contract

3. Create a base test class for contract verification that sets up mock services.

4. Add contract stub dependencies to consumer services (fund-transfer, utility-payment, user-service):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side contract tests that verify Feign clients work against the generated stubs.
```

---

### 3.3 Add Bulkhead Pattern (Gap 7.5)

**Severity: Medium** | **Effort: Medium** | **Priority: Month 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add thread pool isolation (bulkhead pattern) using Resilience4j.

1. Add Resilience4j bulkhead configuration to each service that makes Feign calls (fund-transfer, utility-payment, user-service).

2. Configure separate thread pools for:
   - Core banking service calls (max 10 concurrent)
   - Keycloak calls in user-service (max 5 concurrent)

3. Add bulkhead configuration in application.yml:
   resilience4j:
     bulkhead:
       instances:
         coreBankingBulkhead:
           maxConcurrentCalls: 10
           maxWaitDuration: 2s
         keycloakBulkhead:
           maxConcurrentCalls: 5
           maxWaitDuration: 3s

4. Apply @Bulkhead annotations to service methods or configure via Feign integration.
```

---

### 3.4 Add Custom Health Indicators (Gap 6.2)

**Severity: Low** | **Effort: Small** | **Priority: Month 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add custom health indicators to services.

1. In internet-banking-user-service, create a KeycloakHealthIndicator that checks Keycloak connectivity by calling the realm endpoint.

2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service, create a CoreBankingHealthIndicator that calls the core-banking-service actuator health endpoint via Feign.

3. Register these as @Component classes implementing HealthIndicator.

4. Configure health endpoint to show details: management.endpoint.health.show-details=always
```

---

### 3.5 Add CI/CD Pipeline (Related to Gap 3.1)

**Severity: High** | **Effort: Medium** | **Priority: Month 2**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, create a GitHub Actions CI/CD pipeline.

Create .github/workflows/ci.yml with:

1. Build job (runs on every push and PR):
   - Set up Java 21 (Temurin)
   - Run ./gradlew build for each service
   - Run ./gradlew test for each service
   - Upload test reports as artifacts

2. Security scan job (runs on main and PRs):
   - Run OWASP dependency check
   - Upload vulnerability report

3. Docker build job (runs on main only):
   - Build Docker images for each service
   - Tag with commit SHA and 'latest'
   - Push to GitHub Container Registry (ghcr.io)

4. Use matrix strategy to build all services in parallel.

5. Cache Gradle dependencies between runs.
```

---

### 3.6 Add API Filtering and Sorting (Gap 5.3)

**Severity: Low** | **Effort: Medium** | **Priority: Month 3**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add filtering capabilities to list endpoints.

1. In internet-banking-fund-transfer-service, add query parameters to GET /api/v1/transfer:
   - fromAccount (optional filter)
   - toAccount (optional filter)
   - status (optional filter)
   - dateFrom / dateTo (optional date range filter on createdDate)

2. In internet-banking-utility-payment-service, add query parameters to GET /api/v1/utility-payment:
   - account (optional filter)
   - providerId (optional filter)
   - status (optional filter)
   - dateFrom / dateTo (optional date range)

3. Use Spring Data JPA Specifications or QueryDSL for dynamic query building.

4. Add sorting support via Spring's Sort parameter (already supported by Pageable but not documented).
```

---

### 3.7 Implement Notification Service (Gap from README)

**Severity: Medium** | **Effort: Large** | **Priority: Month 3**

**Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, implement the notification service that is referenced in the README as "PENDING Development".

1. Create a new internet-banking-notification-service module with Spring Boot 3.2.4.

2. Add RabbitMQ dependencies:
   - spring-boot-starter-amqp to the notification service (consumer)
   - spring-boot-starter-amqp to fund-transfer and utility-payment services (producers)

3. Add a RabbitMQ container to docker-compose.yml.

4. Define message types: FundTransferNotification, UtilityPaymentNotification.

5. In fund-transfer and utility-payment services, publish a message to RabbitMQ after successful transactions.

6. In the notification service, consume messages and log them (email/SMS integration can be added later).

7. Register the notification service with Eureka and Spring Cloud Config.
```

---

## Execution Summary

### Phase Overview

| Phase | Items | Critical Fixes | Estimated Duration |
|---|---|---|---|
| Phase 1: Quick Wins | 9 items | 4 (balance bug, exception leak, credentials, validation) | 1-2 weeks |
| Phase 2: Important | 8 items | 1 (unit tests) + resilience | 3-6 weeks |
| Phase 3: Polish | 7 items | 0 (quality improvements) | 2-3 months |

### Priority Order (Top 10)

| Rank | Item | Gap # | Rationale |
|---|---|---|---|
| 1 | Fix balance update bug | 7.7 | Active data corruption in financial calculations |
| 2 | Stop leaking exception details | 2.2 | Security vulnerability exposing internals |
| 3 | Externalize hardcoded credentials | 4.1 | Security baseline for any deployment |
| 4 | Add input validation | 4.2 | Prevents malformed data from reaching business logic |
| 5 | Use correct HTTP status codes | 2.1 | REST API correctness |
| 6 | Add circuit breakers | 7.1 | Prevents cascading failures across services |
| 7 | Add Feign timeouts and retries | 7.2/7.3 | Prevents thread pool exhaustion |
| 8 | Add idempotency for financial ops | 7.6 | Prevents duplicate transactions |
| 9 | Add unit tests | 3.1 | Enables safe refactoring for all other items |
| 10 | Create shared library | 1.2 | Reduces maintenance burden for ongoing fixes |

### Dependencies Between Items

```
1.1 Balance Bug Fix ──────────────────────── (independent, do first)
1.2 Exception Leak Fix ──────────────────── (independent)
1.3 HTTP Status Codes ───────────────────── (depends on 1.2)
1.4 Externalize Credentials ─────────────── (independent)
1.5 Input Validation ────────────────────── (independent)
2.4 Shared Library ──┬── 2.5 Unit Tests ──── 3.1 Integration Tests ── 3.2 Contract Tests
                     │
2.1 Circuit Breakers ┘── 2.2 Timeouts ───── 3.3 Bulkheads
                         2.3 Idempotency ── (independent)
```

### Risk Notes

1. **Balance Bug (1.1)** should be fixed and deployed immediately — it is actively corrupting data.
2. **Shared Library (2.4)** is a large refactor that will conflict with concurrent development. Coordinate timing carefully.
3. **Circuit Breakers (2.1)** require testing with actual failure scenarios (chaos engineering). Plan for manual testing time.
4. **Contract Tests (3.2)** depend on the core-banking API being stable. Do not start until Phase 2 items stabilize the API.
