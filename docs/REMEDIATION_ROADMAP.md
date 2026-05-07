# Remediation Roadmap

> **Repository:** ts-java-spring-boot-internet-banking-microservices
> **Created:** 2026-05-07
> **Reference:** [Gap Analysis](./GAP_ANALYSIS.md)

---

## Phase 1 — Quick Wins (Critical/High severity, Small effort)

These items address the most dangerous issues with minimal code changes. Target completion: **1-2 weeks**.

---

### 1.1 Fix Balance Calculation Bug (GAP-RES-07)

**Priority:** P0 — Data corruption in production
**Effort:** Small

The `availableBalance` is double-deducted because it subtracts from the already-reduced `actualBalance`.

**Devin Prompt:**
```
In core-banking-service TransactionService.java, fix the balance calculation bug in
internalFundTransfer() and utilPayment(). The availableBalance is being set to
actualBalance AFTER actualBalance has already been reduced, causing a double deduction.

For the debit leg, the correct logic is:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance());

For the credit leg:
  entity.setActualBalance(entity.getActualBalance().add(amount));
  entity.setAvailableBalance(entity.getActualBalance());

Apply the same fix in utilPayment(). Add unit tests in TransactionServiceTest to verify
correct balance after transfer and after utility payment. Open a PR.
```

---

### 1.2 Stop Leaking Exception Details (GAP-ERR-02)

**Priority:** P0 — Information disclosure vulnerability
**Effort:** Small

**Devin Prompt:**
```
In all four services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service),
update the GlobalExceptionHandler catch-all handler for Exception.class.

Replace the current implementation that returns "Exception occur inside API " + e with:
1. Log the full exception at ERROR level with stack trace.
2. Return a generic ErrorResponse with code "INTERNAL_ERROR" and message
   "An unexpected error occurred. Please try again later."
3. Return HTTP 500 instead of HTTP 400.

Do not change the SimpleBankingGlobalException handler. Open a PR.
```

---

### 1.3 Fix HTTP Status Codes (GAP-ERR-01)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
In all four services' GlobalExceptionHandler classes, update the exception-to-status mappings:

1. For EntityNotFoundException: return 404 Not Found (not 400).
2. For InsufficientFundsException: return 422 Unprocessable Entity.
3. For UserAlreadyRegisteredException: return 409 Conflict.
4. For InvalidEmailException / InvalidBankingUserException: keep 400 Bad Request.
5. For the generic Exception catch-all: return 500 Internal Server Error.

Keep the ErrorResponse body format unchanged. Open a PR.
```

---

### 1.4 Add Input Validation (GAP-SEC-02)

**Priority:** P1
**Effort:** Medium

**Devin Prompt:**
```
Add Jakarta Bean Validation to all four business services:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to each build.gradle.

2. Add validation annotations to request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account
   - User (register): @NotBlank @Email email, @NotBlank identification,
     @NotBlank @Size(min=8) password
   - UserUpdateRequest: @NotNull status

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that
   returns 400 with field-level error details in ErrorResponse format.

5. Add unit tests for validation. Open a PR.
```

---

### 1.5 Secure Actuator Endpoints (GAP-OBS-06)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
In internet-banking-api-gateway SecurityConfiguration.java, restrict actuator access:

1. Change the permitAll() rules for actuator endpoints to only allow /actuator/health
   and /actuator/info without authentication.
2. Require authentication for all other actuator paths (env, configprops, beans, etc.).

Update the pathMatchers like this:
  exchanges.pathMatchers("/actuator/health", "/actuator/info").permitAll()
  .pathMatchers("/user/actuator/health", "/user/actuator/info").permitAll()
  ... (same for all service prefixes)

Also add management.endpoints.web.exposure.include=health,info,metrics to each
service's application.yml to limit what actuator exposes. Open a PR.
```

---

### 1.6 Remove Sensitive Data from Logs (GAP-OBS-03, GAP-SEC-07)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
Fix sensitive data logging across all services:

1. In all controller classes, replace log statements that call toString() on request
   objects. Instead log only non-sensitive identifiers. For example:
   - FundTransferController: log "Fund transfer from {} to {}" with account numbers only
   - UserController (user-service): log "Creating user with email {}" but NOT the password
   - UtilityPaymentController: log "Utility payment for provider {}" with providerId only

2. In the User DTO (user-service), add @ToString.Exclude on the password field,
   or create a custom toString() that excludes it.

3. In FundTransferService, fix the string concatenation log bug:
   log.info("Sending fund transfer request {}" + request.toString())
   should be log.info("Sending fund transfer request {}", request.getFromAccount())

Open a PR.
```

---

### 1.7 Add Tracing to Core Banking Service (GAP-OBS-01)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
Add distributed tracing dependencies to core-banking-service/build.gradle:

  implementation 'io.micrometer:micrometer-tracing-bridge-brave'
  implementation 'io.zipkin.reporter2:zipkin-reporter-brave'
  implementation 'io.github.openfeign:feign-micrometer'

Also add the management.tracing and management.zipkin configuration to the service's
bootstrap.yml (or the config server's core-banking-service.yml):

  management:
    tracing:
      sampling:
        probability: 1.0
    zipkin:
      tracing:
        endpoint: http://localhost:9411/api/v2/spans

Open a PR.
```

---

### 1.8 Add Feign Timeout Configuration (GAP-RES-03)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
Add Feign client timeout configuration to all three services that use Feign
(user-service, fund-transfer-service, utility-payment-service).

In each service's application.yml (or the config server config), add:

  spring:
    cloud:
      openfeign:
        client:
          config:
            default:
              connectTimeout: 5000
              readTimeout: 10000

This ensures Feign calls fail after 10 seconds instead of hanging indefinitely.
Open a PR.
```

---

### 1.9 Fix Keycloak Singleton Thread Safety (GAP-SEC-04)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service KeycloakProperties.java, fix the thread-unsafe
singleton pattern. Replace the manual null-check with a proper Spring @Bean approach:

1. Remove the static keycloakInstance field and getInstance() method.
2. Create a new @Configuration class KeycloakConfig that declares a @Bean Keycloak
   built from KeycloakBuilder with the injected properties.
3. Update KeycloakManager to inject the Keycloak bean directly.

This ensures thread safety and lets Spring manage the lifecycle. Open a PR.
```

---

### 1.10 Externalize Docker Compose Credentials (GAP-SEC-01)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
Externalize all hardcoded credentials in Docker Compose files:

1. Create a docker-compose/.env.example file with placeholder values for:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KC_DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD

2. Update docker-compose.yml and docker-compose-support-apps.yml to use
   ${VARIABLE} syntax instead of hardcoded values.

3. Update docker-compose/mysql/privileges.sql to use a variable or document
   that the password must be changed.

4. Add docker-compose/.env to .gitignore.

5. Update README.md to document the .env setup. Open a PR.
```

---

### 1.11 Restrict MySQL User Privileges (GAP-SEC-06)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
In docker-compose/mysql/privileges.sql, restrict the application database user:

1. Replace the GRANT ALL on *.* with specific grants per database:
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_service.* TO 'javatodev_development'@'%';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* TO 'javatodev_development'@'%';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
   GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';

2. Keep CREATE, ALTER for the Flyway migrations user, or create a separate migrations user.

Open a PR.
```

---

### 1.12 Add Feign Retry Configuration (GAP-RES-02)

**Priority:** P1
**Effort:** Small

**Devin Prompt:**
```
Add a Feign Retryer bean to the CustomFeignClientConfiguration in all three Feign-using
services (user-service, fund-transfer-service, utility-payment-service):

  @Bean
  public Retryer retryer() {
      return new Retryer.Default(100, 1000, 3);
  }

This retries failed requests up to 3 times with exponential backoff (100ms initial,
1s max). Only retries on IOException, not on HTTP errors. Open a PR.
```

---

## Phase 2 — Important (High/Medium severity, Medium effort)

These items significantly improve reliability and maintainability. Target: **3-6 weeks**.

---

### 2.1 Handle Failed Downstream Calls (GAP-ERR-04)

**Priority:** P2
**Effort:** Medium

**Devin Prompt:**
```
In internet-banking-fund-transfer-service FundTransferService.fundTransfer():

1. Wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch.
2. On any exception, update the FundTransferEntity status to FAILED and save it.
3. Log the error with the entity ID for troubleshooting.
4. Re-throw the exception so the controller can return an appropriate error.

Apply the same pattern in internet-banking-utility-payment-service
UtilityPaymentService.utilPayment().

Add unit tests that verify the entity status is set to FAILED when the Feign call
throws an exception. Open a PR.
```

---

### 2.2 Add Circuit Breakers (GAP-RES-01)

**Priority:** P2
**Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies to each Feign-using service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign in application.yml:
   spring.cloud.openfeign.circuitbreaker.enabled: true

3. Create fallback factory classes for each Feign client that return meaningful
   error responses (e.g., "Core Banking Service is temporarily unavailable").

4. Configure circuit breaker properties:
   resilience4j.circuitbreaker.instances.core-banking-service:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     permittedNumberOfCallsInHalfOpenState: 3

Open a PR.
```

---

### 2.3 Add Idempotency for Financial Operations (GAP-RES-06)

**Priority:** P2
**Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment endpoints:

1. Add an optional X-Idempotency-Key header to FundTransferController.sendFundTransfer()
   and UtilityPaymentController.processPayment().

2. Before processing, check if a record with that idempotency key already exists
   in the database. If so, return the existing result.

3. Add an 'idempotencyKey' column to fund_transfer and utility_payment tables.
   Add a unique index on the column.

4. If no idempotency key is provided, generate one server-side (UUID).

5. Add unit tests verifying that duplicate requests with the same key return the
   same response without creating duplicate records. Open a PR.
```

---

### 2.4 Return Pagination Metadata (GAP-API-03)

**Priority:** P2
**Effort:** Small

**Devin Prompt:**
```
Update all paginated endpoints to return Page<T> instead of List<T>:

1. In core-banking-service UserService.readUsers(): return Page<User> wrapping the
   repository result instead of extracting getContent().

2. In fund-transfer-service FundTransferService.readAllTransfers(): return a Page
   with mapped DTOs.

3. In utility-payment-service UtilityPaymentService.readPayments(): same approach.

4. Update controllers to return ResponseEntity<Page<T>>.

This gives consumers totalElements, totalPages, number, size, etc. automatically
via Jackson serialization. Open a PR.
```

---

### 2.5 Add Unit Tests for Business Services (GAP-TEST-01, partial)

**Priority:** P2
**Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests for all business service classes:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer() success, Feign failure, mapper
   - FundTransferControllerTest: test with MockMvc

2. internet-banking-user-service:
   - UserServiceTest: test createUser() success, duplicate email, invalid NIC,
     readUsers(), readUser(), updateUser() with approval
   - KeycloakUserServiceTest: test CRUD operations with mocked KeycloakManager

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment() success, Feign failure
   - UtilityPaymentControllerTest: test with MockMvc

Use Mockito for mocking Feign clients and repositories. Target 80% line coverage
for service classes. Open a PR.
```

---

### 2.6 Add Structured JSON Logging (GAP-OBS-02)

**Priority:** P2
**Effort:** Medium

**Devin Prompt:**
```
Configure structured JSON logging for all services:

1. Add logstash-logback-encoder dependency to each build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with pattern layout for local development (profile: !docker)
   - Console appender with JSON layout for Docker/production (profile: docker)
   - Include traceId, spanId, service name in JSON output

3. Remove any System.out.println or e.printStackTrace() calls if present.

Open a PR.
```

---

### 2.7 Add Custom Health Indicators (GAP-OBS-04)

**Priority:** P2
**Effort:** Small

**Devin Prompt:**
```
Add custom health indicators to services with external dependencies:

1. In internet-banking-user-service, create a KeycloakHealthIndicator that calls
   keycloakManager.getKeyCloakInstanceWithRealm() and returns UP/DOWN.

2. In internet-banking-fund-transfer-service and utility-payment-service, create a
   CoreBankingHealthIndicator that calls the Feign client's readAccount() with a
   known test account and returns UP/DOWN.

3. In core-banking-service, add a DatabaseHealthIndicator that runs a simple
   SELECT 1 query (or rely on Spring's built-in DataSourceHealthIndicator by ensuring
   management.health.db.enabled=true).

Open a PR.
```

---

### 2.8 Add Rate Limiting to API Gateway (GAP-RES-05)

**Priority:** P2
**Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway:

1. Add Redis dependency:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Add a RequestRateLimiter filter to gateway routes in application.yml:
   spring.cloud.gateway.routes:
     - id: fund-transfer
       uri: lb://internet-banking-fund-transfer-service
       predicates:
         - Path=/fund-transfer/**
       filters:
         - name: RequestRateLimiter
           args:
             redis-rate-limiter.replenishRate: 10
             redis-rate-limiter.burstCapacity: 20

3. Add a Redis container to docker-compose.yml.

4. Create a KeyResolver bean that resolves rate limit keys from the JWT subject
   (authenticated user) or IP address (unauthenticated).

Open a PR.
```

---

### 2.9 Fix OpenAPI Dependencies (GAP-API-07)

**Priority:** P2
**Effort:** Small

**Devin Prompt:**
```
Fix OpenAPI/Swagger dependencies in servlet-based services:

1. In internet-banking-user-service, internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service build.gradle files, replace:
     springdoc-openapi-starter-webflux-ui
   with:
     springdoc-openapi-starter-webmvc-ui

2. Keep springdoc-openapi-starter-webflux-ui only in internet-banking-api-gateway
   (which uses WebFlux).

3. Add springdoc-openapi-starter-webmvc-ui to core-banking-service as well.

4. Verify Swagger UI loads at /swagger-ui.html for each service. Open a PR.
```

---

## Phase 3 — Polish (Medium/Low severity, larger effort)

These items bring the codebase to production-grade quality. Target: **6-12 weeks**.

---

### 3.1 Create Shared Library Module (GAP-ORG-02)

**Priority:** P3
**Effort:** Large

**Devin Prompt:**
```
Create a shared library module to eliminate code duplication:

1. Create a new Gradle module: banking-common-lib/
2. Move these shared classes into it:
   - BaseMapper<E, D>
   - AuditAware (MappedSuperclass)
   - AuditConfig, AuditorAwareConfig
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler
   - GlobalErrorCode (make it extensible for service-specific codes)

3. Create a root settings.gradle that includes all services and the shared lib.

4. Update each service's build.gradle to depend on:
   implementation project(':banking-common-lib')

5. Remove the duplicated classes from each service.

6. Ensure all existing tests still pass. Open a PR.
```

---

### 3.2 Convert to Multi-Module Gradle Build (GAP-ORG-01)

**Priority:** P3
**Effort:** Medium

**Devin Prompt:**
```
Convert the project to a multi-module Gradle build:

1. Create a root build.gradle with:
   - Common plugins (java, spring-boot, dependency-management)
   - Shared dependency versions in ext block or a version catalog (gradle/libs.versions.toml)
   - subprojects block with common settings (Java 21, encoding, test config)

2. Create a root settings.gradle including all modules:
   include 'banking-common-lib',
           'core-banking-service',
           'internet-banking-api-gateway',
           ... (all services)

3. Remove duplicate Gradle wrapper files from each service (keep one root gradlew).

4. Update each service's build.gradle to only declare service-specific dependencies.

5. Verify './gradlew build' from root compiles all services. Open a PR.
```

---

### 3.3 Add Integration Tests with Testcontainers (GAP-TEST-02)

**Priority:** P3
**Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for each business service:

1. Add testcontainers dependencies to each service:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service, create integration tests that:
   - Start a MySQL container with Flyway migrations
   - Test the full fund transfer flow (create accounts, transfer, verify balances)
   - Test utility payment flow

3. For user-service, create tests with MySQL + Keycloak containers:
   - Test user registration end-to-end
   - Test user approval flow

4. For fund-transfer and utility-payment services:
   - Use WireMock to stub core-banking-service responses
   - Test the full request lifecycle

Open a PR.
```

---

### 3.4 Add Contract Tests (GAP-TEST-03)

**Priority:** P3
**Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. In core-banking-service (producer), add Spring Cloud Contract Verifier:
   - Add spring-cloud-contract-verifier plugin to build.gradle
   - Write contracts in src/test/resources/contracts/ for each endpoint:
     - GET /api/v1/account/bank-account/{number} → returns BankAccount
     - POST /api/v1/transaction/fund-transfer → returns FundTransferResponse
     - POST /api/v1/transaction/util-payment → returns UtilityPaymentResponse
     - GET /api/v1/user/{identification} → returns User

2. Generate and publish contract stubs as a Maven artifact.

3. In consumer services (user, fund-transfer, utility-payment):
   - Add spring-cloud-contract-stub-runner
   - Write @AutoConfigureStubRunner tests that verify Feign clients
     work correctly against the contract stubs

Open a PR.
```

---

### 3.5 Add Prometheus Metrics (GAP-OBS-05)

**Priority:** P3
**Effort:** Medium

**Devin Prompt:**
```
Add Prometheus metrics export to all services:

1. Add to each build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Configure management endpoints in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus
     metrics:
       tags:
         application: ${spring.application.name}

3. Add a Prometheus container to docker-compose.yml with a prometheus.yml config
   that scrapes all service /actuator/prometheus endpoints.

4. Optionally add a Grafana container with a pre-configured dashboard JSON
   showing JVM metrics, HTTP request rates, and Feign client metrics.

Open a PR.
```

---

### 3.6 Enhance OpenAPI Documentation (GAP-API-05)

**Priority:** P3
**Effort:** Medium

**Devin Prompt:**
```
Enhance OpenAPI documentation across all services:

1. Add @ApiResponse annotations to all controller methods documenting:
   - 200 success response with schema
   - 400 validation error response
   - 404 not found (where applicable)
   - 500 internal error

2. Add @Schema annotations to all DTO fields with descriptions and examples:
   - BankAccount.number: @Schema(description = "Account number", example = "100015003000")
   - FundTransferRequest.amount: @Schema(description = "Transfer amount", example = "1000.00")

3. Add @Parameter annotations to path variables and query parameters.

4. Configure OpenAPI info bean in each service with title, description, version.

5. Add typed ResponseEntity<T> return types to all controllers. Open a PR.
```

---

### 3.7 Implement Notification Service (Not in GAP — Architecture completion)

**Priority:** P3
**Effort:** Large

**Devin Prompt:**
```
Implement the pending Notification Service:

1. Create a new Spring Boot module: internet-banking-notification-service
   - Port: 8086
   - Dependencies: spring-boot-starter-amqp, spring-boot-starter-mail

2. Add spring-boot-starter-amqp to fund-transfer and utility-payment services.

3. In fund-transfer-service, publish a message to RabbitMQ after successful transfer:
   - Queue: fund-transfer-notifications
   - Payload: { transactionId, fromAccount, toAccount, amount, status }

4. In utility-payment-service, publish after successful payment:
   - Queue: utility-payment-notifications
   - Payload: { transactionId, providerId, amount, status }

5. In notification-service, create consumers that:
   - Read from both queues
   - Send email notifications (or log for now)

6. Add RabbitMQ container to docker-compose.yml. Open a PR.
```

---

### 3.8 Update .gitignore (GAP-ORG-04)

**Priority:** P3
**Effort:** Small

**Devin Prompt:**
```
Update the root .gitignore to exclude build artifacts:

1. Add these patterns:
   build/
   .gradle/
   *.class
   *.jar
   *.log
   .idea/
   *.iml
   .DS_Store

2. Remove any already-tracked build artifacts:
   git rm -r --cached */build/ */.gradle/

Open a PR.
```

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Effort |
|-------|-------|---------------|-----------------|
| **Phase 1** | 12 items | Balance bug, exception leak, validation, credential hardcoding, idempotency | 2-3 weeks |
| **Phase 2** | 9 items | Circuit breakers, error handling, test coverage, structured logging | 4-6 weeks |
| **Phase 3** | 8 items | Shared library, integration/contract tests, metrics, notification service | 6-12 weeks |

### Dependency Graph

```
Phase 1 (parallel tracks):
  Track A: GAP-RES-07 (balance bug) → GAP-RES-06 (idempotency, Phase 2.3)
  Track B: GAP-ERR-02 (exception leak) → GAP-ERR-01 (status codes) → GAP-ERR-04 (Feign error handling, Phase 2.1)
  Track C: GAP-SEC-02 (validation) — standalone
  Track D: GAP-SEC-01 (credentials) → GAP-SEC-06 (MySQL privileges) — standalone
  Track E: GAP-OBS-01 (tracing) → GAP-OBS-02 (structured logging, Phase 2.6)
  Track F: GAP-RES-03 (timeouts) → GAP-RES-02 (retry) → GAP-RES-01 (circuit breakers, Phase 2.2)

Phase 2 (after Phase 1):
  GAP-ERR-04 depends on GAP-ERR-01
  GAP-RES-01 depends on GAP-RES-03
  GAP-TEST-01 can start in parallel

Phase 3 (after Phase 2):
  GAP-ORG-02 (shared lib) should precede GAP-ORG-01 (multi-module)
  GAP-TEST-02 and GAP-TEST-03 can start after GAP-TEST-01
```
