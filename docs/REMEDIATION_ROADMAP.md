# Remediation Roadmap

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Created:** 2026-05-07
> **References:** [Gap Analysis](./GAP_ANALYSIS.md) | [Knowledge Base](./KNOWLEDGE_BASE.md)

---

## Phasing Strategy

| Phase | Focus | Timeline | Selection Criteria |
|-------|-------|----------|-------------------|
| **Phase 1 — Quick Wins** | Critical fixes, small effort items | 1-2 weeks | Critical/High severity + Small effort, or security fixes |
| **Phase 2 — Important** | Structural improvements, resilience | 3-6 weeks | High/Medium severity + Medium effort |
| **Phase 3 — Polish** | Best-practice alignment, DX improvements | 6-10 weeks | Medium/Low severity, large refactors |

---

## Phase 1: Quick Wins (1-2 Weeks)

These items address the most dangerous issues with minimal effort. Each can be completed independently.

---

### 1.1 Fix Double-Subtraction Balance Bug
**Gaps:** GAP-RES-05 | **Severity:** Critical | **Effort:** Small

The `availableBalance` calculation subtracts the amount twice — once from `actualBalance`, then again from the already-reduced value. This silently corrupts account balances.

**Files to change:**
- `core-banking-service/.../service/TransactionService.java` (lines 90-91 and 63-64)

**Devin Prompt:**
```
In the file core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java,
fix the double-subtraction bug in both internalFundTransfer() and utilPayment() methods.

In internalFundTransfer():
- Line 91: Change fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))
  to fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())
- Line 100: Change toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))
  to toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance())

In utilPayment():
- Line 64: Change fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()))
  to fromAccount.setAvailableBalance(fromAccount.getActualBalance())

After fixing, actualBalance and availableBalance should always be equal after each operation.
Update the existing unit tests in TransactionServiceTest.java to assert that availableBalance == actualBalance after transfers.
```

---

### 1.2 Stop Leaking Stack Traces in Error Responses
**Gaps:** GAP-ERR-02 | **Severity:** Critical | **Effort:** Small

**Files to change:**
- `core-banking-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-user-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-fund-transfer-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-utility-payment-service/.../exception/GlobalExceptionHandler.java`

**Devin Prompt:**
```
In all four GlobalExceptionHandler.java files across the project, update the generic
Exception handler to stop leaking stack traces. Replace:

  .body("Exception occur inside API " + e);

with:

  .body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred").build());

Also change the HTTP status from 400 (badRequest) to 500 (internalServerError) for the
generic Exception handler. Log the full exception at ERROR level instead:

  log.error("Unhandled exception", e);

Add @Slf4j annotation to each GlobalExceptionHandler class.
```

---

### 1.3 Fix HTTP Status Codes in Exception Handlers
**Gaps:** GAP-ERR-01 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
In all GlobalExceptionHandler.java files, add specific exception handlers with correct HTTP status codes:

1. Add handler for EntityNotFoundException -> return 404 (NOT_FOUND)
2. Add handler for InsufficientFundsException (core-banking) -> return 422 (UNPROCESSABLE_ENTITY)
3. Add handler for UserAlreadyRegisteredException (user-service) -> return 409 (CONFLICT)
4. Add handler for InvalidEmailException (user-service) -> return 400 (BAD_REQUEST)
5. Add handler for InvalidBankingUserException (user-service) -> return 404 (NOT_FOUND)
6. Keep SimpleBankingGlobalException handler at 400 as a fallback for business errors.
7. Change the generic Exception handler to return 500 (INTERNAL_SERVER_ERROR).

Each handler should return an ErrorResponse with an appropriate code and message.
Do not leak exception details in the response body.
```

---

### 1.4 Add Input Validation to All Request DTOs
**Gaps:** GAP-SEC-02 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Bean Validation to all microservices:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to the dependencies
   in build.gradle for: core-banking-service, internet-banking-user-service,
   internet-banking-fund-transfer-service, internet-banking-utility-payment-service.

2. Add validation annotations to all request DTOs:

   core-banking-service FundTransferRequest:
     - @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

   core-banking-service UtilityPaymentRequest:
     - @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

   internet-banking-fund-transfer-service FundTransferRequest:
     - @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount

   internet-banking-utility-payment-service UtilityPaymentRequest:
     - @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

   internet-banking-user-service User (for registration):
     - @NotBlank email with @Email, @NotBlank password with @Size(min=8),
       @NotBlank identification, @NotBlank firstName, @NotBlank lastName

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that
   returns 400 with field-level validation error details.
```

---

### 1.5 Externalize Docker Compose Secrets
**Gaps:** GAP-SEC-01 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Refactor docker-compose/docker-compose.yml and docker-compose-support-apps.yml to use
environment variables instead of hardcoded passwords:

1. Replace all hardcoded credentials with ${VAR:-default} syntax:
   - MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
   - KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
   - KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
   - POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}

2. Create a docker-compose/.env.example file showing all required variables
   with placeholder values.

3. Add docker-compose/.env to .gitignore.

4. Remove the hardcoded test credentials from README.md and reference the
   .env.example file instead.
```

---

### 1.6 Fix Keycloak Singleton Thread Safety
**Gaps:** GAP-SEC-04 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service/.../configuration/keycloak/KeycloakProperties.java,
replace the manual singleton pattern with a Spring @Bean:

1. Remove the static keycloakInstance field and getInstance() method.
2. Create a new @Configuration class KeycloakConfig that declares a @Bean Keycloak method
   using KeycloakBuilder with the injected properties.
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance().

This eliminates the thread-safety issue and follows Spring's singleton lifecycle management.
```

---

### 1.7 Remove Sensitive Data from Logs
**Gaps:** GAP-OBS-03 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Audit all log statements across the codebase that call .toString() on request objects.
Replace them with safe alternatives that don't log sensitive fields:

1. In internet-banking-user-service UserController.java:
   - Change log.info("Creating user with {}", request.toString())
     to log.info("Creating user with email={}", request.getEmail())

2. In core-banking-service TransactionController.java:
   - Change log.info("Fund transfer initiated...{}", fundTransferRequest.toString())
     to log.info("Fund transfer from={} to={} amount={}", request.getFromAccount(), request.getToAccount(), request.getAmount())

3. Apply similar changes to all controllers and services that log request DTOs.
   Never log passwords, full account numbers, or PII.
```

---

### 1.8 Add Feign Timeout Configuration
**Gaps:** GAP-RES-03 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add default Feign client timeout configuration to the following services:
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service.

In each service's application.yml, add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000

This ensures no Feign call blocks indefinitely.
```

---

### 1.9 Add Feign Retry Policy
**Gaps:** GAP-RES-02 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add a Spring Retry-based Feign retryer to the three services that use OpenFeign:

1. Add 'org.springframework.retry:spring-retry' dependency to build.gradle for:
   internet-banking-user-service, internet-banking-fund-transfer-service,
   internet-banking-utility-payment-service.

2. In each service's application.yml, add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            retryer: feign.Retryer.Default

This provides 5 retry attempts with 100ms initial backoff and 1s max backoff for
connection-level failures (not HTTP errors).
```

---

### 1.10 Fix OpenAPI Dependency (webflux -> webmvc)
**Gaps:** GAP-API-05 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the build.gradle of these servlet-based services, replace the wrong OpenAPI dependency:
- core-banking-service
- internet-banking-user-service
- internet-banking-fund-transfer-service
- internet-banking-utility-payment-service

Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
To:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

The webflux variant is for reactive stacks; these services use Spring MVC (servlet).
Also bump to 2.5.0 for security fixes.
```

---

### 1.11 Configure Actuator Endpoints
**Gaps:** GAP-OBS-01 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In each service's application.yml (or via Config Server), add actuator endpoint
configuration:

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized

This exposes health, info, and metrics endpoints for monitoring.
```

---

### 1.12 Add Dependency Vulnerability Scanning
**Gaps:** GAP-SEC-06 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add the OWASP Dependency Check Gradle plugin to all services:

1. In each service's build.gradle, add to the plugins block:
   id 'org.owasp.dependencycheck' version '9.0.9'

2. Configure it:
   dependencyCheck {
       failBuildOnCVSS = 7
       formats = ['HTML', 'JSON']
   }

3. Add a GitHub Actions workflow (.github/workflows/dependency-check.yml) that
   runs './gradlew dependencyCheckAnalyze' on push to main and on PRs.
```

---

## Phase 2: Important (3-6 Weeks)

These items require more effort but significantly improve reliability and maintainability.

---

### 2.1 Add Circuit Breakers with Resilience4j
**Gaps:** GAP-RES-01 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls:

1. Add dependencies to build.gradle for user-service, fund-transfer-service,
   and utility-payment-service:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker support for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true

3. Create fallback classes for each Feign client:
   - BankingCoreRestClientFallback (user-service): return error response for readUser
   - BankingCoreFeignClientFallback (fund-transfer): return error for fundTransfer, readAccount
   - BankingCoreRestClientFallback (utility-payment): return error for utilityPayment, readAccount

4. Configure circuit breaker properties:
   resilience4j.circuitbreaker.configs.default:
     sliding-window-size: 10
     failure-rate-threshold: 50
     wait-duration-in-open-state: 30s
     permitted-number-of-calls-in-half-open-state: 5

5. Wire fallback classes into @FeignClient annotations.
```

---

### 2.2 Add Custom Feign Error Decoder
**Gaps:** GAP-ERR-04 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a custom Feign ErrorDecoder for all three services that consume Core Banking:

1. Create a class CustomFeignErrorDecoder implements feign.codec.ErrorDecoder in each service.

2. Map Core Banking error responses back to appropriate exceptions:
   - 404 from core -> throw EntityNotFoundException locally
   - 400 with error code INSUFFICIENT_FUNDS -> throw InsufficientFundsException
   - 400 with other codes -> throw SimpleBankingGlobalException with the code/message
   - 5xx -> throw a new ServiceUnavailableException (create this class)

3. Parse the ErrorResponse JSON body from the Feign response.

4. Register the decoder in CustomFeignClientConfiguration as a @Bean.

5. Add unit tests for the error decoder with various HTTP status scenarios.
```

---

### 2.3 Handle Failed Downstream Calls (Status Cleanup)
**Gaps:** GAP-ERR-05 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(),
wrap the Feign client call in a try-catch:

1. If the Feign call throws an exception:
   - Update the local entity status to FAILED
   - Save the entity
   - Re-throw the exception (or wrap it in a service-specific exception)

2. Add a FAILED value to both TransactionStatus enums.

3. Add a GET endpoint to retrieve transfers/payments by status so admins
   can query failed transactions.

Example pattern:
  try {
      FundTransferResponse response = bankingCoreFeignClient.fundTransfer(request);
      entity.setStatus(TransactionStatus.SUCCESS);
  } catch (Exception e) {
      entity.setStatus(TransactionStatus.FAILED);
      fundTransferRepository.save(entity);
      throw e;
  }
```

---

### 2.4 Secure Internal Service-to-Service Communication
**Gaps:** GAP-SEC-07 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add authentication to Core Banking Service endpoints and configure Feign clients
to propagate JWT tokens:

1. Add spring-boot-starter-security and spring-boot-starter-oauth2-resource-server
   to core-banking-service build.gradle.

2. Create a SecurityConfig in core-banking-service that:
   - Validates JWT tokens from Keycloak
   - Permits /actuator/** endpoints
   - Requires authentication for all /api/** endpoints

3. In the API Gateway's GatewayConfiguration, ensure the JWT token is forwarded
   to downstream services (add Authorization header relay).

4. In each Feign client configuration (fund-transfer, utility-payment, user-service),
   add a RequestInterceptor that copies the Authorization header from the incoming
   request to outbound Feign calls.
```

---

### 2.5 Add Idempotency Keys for Write Operations
**Gaps:** GAP-RES-06 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment endpoints:

1. Add an optional X-Idempotency-Key header to POST endpoints.

2. In FundTransferService:
   - Before creating a new entity, check if an entity with the given idempotency key exists.
   - If it does, return the existing result instead of processing again.
   - Store the idempotency key in a new column on FundTransferEntity.

3. Apply the same pattern to UtilityPaymentService.

4. Add a unique constraint on the idempotency_key column.

5. Document the header in OpenAPI annotations.
```

---

### 2.6 Standardize Pagination Responses
**Gaps:** GAP-API-03 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Create a generic PageResponse<T> wrapper class in each service (or in a shared module):

public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

Update all paginated endpoints (readUsers, readFundTransfers, readPayments) to
return PageResponse<T> instead of List<T>. Map from Spring's Page object.
```

---

### 2.7 Add Structured JSON Logging
**Gaps:** GAP-OBS-02 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Configure structured JSON logging for all services:

1. Add 'net.logstash.logback:logstash-logback-encoder:7.4' dependency to each
   service's build.gradle.

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with pattern layout for local development (profile: !docker)
   - Console appender with LogstashEncoder for Docker/production (profile: docker)
   - Include traceId/spanId from Micrometer in JSON output

3. Ensure log output includes: timestamp, level, service name, traceId, spanId,
   logger, message, and any MDC values.
```

---

### 2.8 Add Unit Tests for User, Fund Transfer, and Utility Payment Services
**Gaps:** GAP-TEST-01 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Write comprehensive unit tests for the three internet-banking services:

1. internet-banking-user-service UserServiceTest:
   - Test createUser success flow (mock Keycloak + CoreBanking Feign)
   - Test createUser with existing email (should throw UserAlreadyRegisteredException)
   - Test createUser with invalid email (should throw InvalidEmailException)
   - Test createUser with unknown NIC (should throw InvalidBankingUserException)
   - Test updateUser to APPROVED (verify Keycloak update called)
   - Test readUser found/not found
   - Test readUsers pagination

2. internet-banking-fund-transfer-service FundTransferServiceTest:
   - Test fundTransfer success (mock CoreBanking Feign)
   - Test fundTransfer when CoreBanking returns error
   - Test readAllTransfers

3. internet-banking-utility-payment-service UtilityPaymentServiceTest:
   - Test utilPayment success (mock CoreBanking Feign)
   - Test utilPayment when CoreBanking returns error
   - Test readPayments

Use Mockito for mocking. Each test class should set up mocks in @BeforeEach.
Target: at least 80% line coverage for service classes.
```

---

### 2.9 Add Prometheus Metrics Endpoint
**Gaps:** GAP-OBS-05 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add 'io.micrometer:micrometer-registry-prometheus' to each service's build.gradle.

2. Ensure actuator configuration exposes the prometheus endpoint:
   management.endpoints.web.exposure.include: health,info,metrics,prometheus

3. Verify /actuator/prometheus returns metrics in Prometheus format.

4. Optionally, add a prometheus.yml configuration to docker-compose/ that
   scrapes all service endpoints.
```

---

### 2.10 Standardize Error Response Envelope
**Gaps:** GAP-API-06 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Create a standardized error response format across all services:

public class ApiErrorResponse {
    private String timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private List<FieldError> fieldErrors; // for validation errors
}

Update all GlobalExceptionHandler classes to use this format. Include the request
path from HttpServletRequest. Use ISO 8601 timestamp format.
```

---

## Phase 3: Polish (6-10 Weeks)

These items bring the codebase to production-grade standards.

---

### 3.1 Create Multi-Project Gradle Build
**Gaps:** GAP-ORG-01 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Restructure the repository into a Gradle multi-project build:

1. Create a root settings.gradle that includes all 7 service subprojects.

2. Create a root build.gradle with shared configuration:
   - Common plugins (spring-boot, dependency-management)
   - Shared dependency versions via a version catalog (gradle/libs.versions.toml)
   - Common test configuration

3. Move each service's unique dependencies into their own build.gradle,
   inheriting common config from the root.

4. Remove duplicate gradle/ wrapper directories from subprojects.

5. Verify that './gradlew build' from the root builds all services.

6. Verify that './gradlew test' from the root runs all tests.
```

---

### 3.2 Extract Shared Common Library
**Gaps:** GAP-ORG-02, GAP-ORG-04 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Create a 'banking-common' module in the multi-project build:

1. Move shared classes into banking-common/src/main/java:
   - exception/: GlobalExceptionHandler, SimpleBankingGlobalException, ErrorResponse,
     EntityNotFoundException, ApiErrorResponse
   - audit/: AuditAware, AuditConfig, AuditorAwareConfig
   - filter/: AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - mapper/: BaseMapper interface
   - config/: CustomFeignClientConfiguration

2. Have all services depend on 'banking-common' as an implementation dependency.

3. Remove duplicated classes from each service.

4. Create shared request/response DTOs for the Core Banking API contract in
   a 'banking-core-api' module that both Core Banking and its consumers depend on.
```

---

### 3.3 Add Contract Tests
**Gaps:** GAP-TEST-02 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between Core Banking (provider) and its consumers:

1. Add 'org.springframework.cloud:spring-cloud-starter-contract-verifier' to
   core-banking-service as a testImplementation dependency.

2. Add 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner' to
   user-service, fund-transfer-service, and utility-payment-service as test deps.

3. In core-banking-service, create contract DSL files under src/test/resources/contracts/:
   - userApi/shouldReturnUserByIdentification.groovy
   - accountApi/shouldReturnBankAccountByNumber.groovy
   - transactionApi/shouldProcessFundTransfer.groovy
   - transactionApi/shouldProcessUtilityPayment.groovy

4. Create base test classes for contract verification in core-banking-service.

5. In consumer services, write stub-runner integration tests that verify
   Feign clients work correctly against the contract stubs.
```

---

### 3.4 Add Integration Tests with Testcontainers
**Gaps:** GAP-TEST-03, GAP-TEST-04 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for each service:

1. Add Testcontainers dependencies to each service:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create an abstract BaseIntegrationTest class with @Testcontainers and
   MySQLContainer setup.

3. For core-banking-service: write integration tests that:
   - Start MySQL testcontainer
   - Run Flyway migrations
   - Test full API request/response cycle via MockMvc
   - Test fund transfer end-to-end with real DB

4. For user-service: add Keycloak testcontainer (dasniko/testcontainers-keycloak)
   for integration testing user registration flow.

5. Create application-test.yml profiles for each service that configure
   Testcontainers-provided datasource URLs.
```

---

### 3.5 Add CI/CD Pipeline
**Gaps:** No specific gap (infrastructure) | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml:
   - Trigger on push to main and on pull requests
   - Matrix build: run './gradlew build test' for each service
   - Cache Gradle dependencies
   - Upload test reports as artifacts
   - Run OWASP dependency check

2. Create .github/workflows/docker-build.yml:
   - Build Docker images for all services on release tags
   - Push to a container registry (configurable)

3. Add build status badges to README.md.
```

---

### 3.6 Add Rate Limiting to Public Endpoints
**Gaps:** GAP-SEC-05 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway for public endpoints:

1. Add Spring Cloud Gateway's built-in rate limiter:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Configure a Redis-backed rate limiter for the registration endpoint:
   spring.cloud.gateway.routes:
     - id: user-service
       uri: lb://internet-banking-user-service
       predicates:
         - Path=/user/**
       filters:
         - name: RequestRateLimiter
           args:
             redis-rate-limiter.replenishRate: 10
             redis-rate-limiter.burstCapacity: 20

3. Add Redis to docker-compose.yml.

4. Alternatively, if Redis is too heavy, use Resilience4j rate limiter
   (no external dependency) with in-memory state.
```

---

### 3.7 Standardize REST URL Conventions
**Gaps:** GAP-API-01 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Refactor API endpoints to follow RESTful resource naming conventions:

1. User Service:
   - POST /api/v1/bank-users/register -> POST /api/v1/bank-users
   - PATCH /api/v1/bank-users/update/{id} -> PATCH /api/v1/bank-users/{id}

2. Core Banking:
   - POST /api/v1/transaction/fund-transfer -> POST /api/v1/transactions (with type in body)
   - POST /api/v1/transaction/util-payment -> POST /api/v1/transactions (with type in body)
   - GET /api/v1/account/bank-account/{number} -> GET /api/v1/bank-accounts/{number}
   - GET /api/v1/account/util-account/{name} -> GET /api/v1/utility-accounts/{name}

3. Update all Feign client paths accordingly.

4. Update API Gateway route mappings.

5. Update Postman collection.

Note: This is a breaking change. Consider supporting both old and new paths temporarily
with deprecation headers.
```

---

### 3.8 Add Health Checks for External Dependencies
**Gaps:** GAP-OBS-04 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom health indicators for external dependencies:

1. In internet-banking-user-service, create KeycloakHealthIndicator:
   - Implement org.springframework.boot.actuate.health.HealthIndicator
   - Check Keycloak connectivity by calling the realm endpoint
   - Return Health.up() or Health.down() with details

2. In services that use Feign, create CoreBankingHealthIndicator:
   - Call /actuator/health on core-banking-service
   - Return Health.up()/down() based on response

3. Register health indicators as @Component beans.
```

---

### 3.9 Add Database Connection Pool Configuration
**Gaps:** GAP-RES-07 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add explicit HikariCP connection pool configuration for all services that use JPA.
In each service's application.yml (or via Config Server), add:

spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: ${spring.application.name}-pool

These values are starting points; tune based on load testing.
```

---

### 3.10 Implement Notification Service (RabbitMQ)
**Gaps:** N/A (pending feature from README) | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
Implement the pending Notification Service:

1. Create a new internet-banking-notification-service module.

2. Add RabbitMQ dependencies to fund-transfer-service and utility-payment-service:
   implementation 'org.springframework.boot:spring-boot-starter-amqp'

3. In fund-transfer-service and utility-payment-service, publish messages to
   RabbitMQ after successful transactions:
   - Queue: banking.notifications
   - Message: { type, transactionId, userId, amount, timestamp }

4. In notification-service, consume messages from the queue and log them
   (as a starting point for email/SMS integration).

5. Add RabbitMQ to docker-compose.yml.

6. Add RabbitMQ health indicator to producing services.
```

---

## Progress Tracking

| # | Item | Phase | Status |
|---|------|-------|--------|
| 1.1 | Fix double-subtraction balance bug | 1 | Not Started |
| 1.2 | Stop leaking stack traces | 1 | Not Started |
| 1.3 | Fix HTTP status codes | 1 | Not Started |
| 1.4 | Add input validation | 1 | Not Started |
| 1.5 | Externalize Docker secrets | 1 | Not Started |
| 1.6 | Fix Keycloak thread safety | 1 | Not Started |
| 1.7 | Remove sensitive data from logs | 1 | Not Started |
| 1.8 | Add Feign timeouts | 1 | Not Started |
| 1.9 | Add Feign retry policy | 1 | Not Started |
| 1.10 | Fix OpenAPI dependency | 1 | Not Started |
| 1.11 | Configure actuator endpoints | 1 | Not Started |
| 1.12 | Add dependency vulnerability scanning | 1 | Not Started |
| 2.1 | Add circuit breakers | 2 | Not Started |
| 2.2 | Add Feign error decoder | 2 | Not Started |
| 2.3 | Handle failed downstream calls | 2 | Not Started |
| 2.4 | Secure service-to-service comms | 2 | Not Started |
| 2.5 | Add idempotency keys | 2 | Not Started |
| 2.6 | Standardize pagination responses | 2 | Not Started |
| 2.7 | Add structured JSON logging | 2 | Not Started |
| 2.8 | Add unit tests | 2 | Not Started |
| 2.9 | Add Prometheus metrics | 2 | Not Started |
| 2.10 | Standardize error response envelope | 2 | Not Started |
| 3.1 | Multi-project Gradle build | 3 | Not Started |
| 3.2 | Extract shared common library | 3 | Not Started |
| 3.3 | Add contract tests | 3 | Not Started |
| 3.4 | Add integration tests (Testcontainers) | 3 | Not Started |
| 3.5 | Add CI/CD pipeline | 3 | Not Started |
| 3.6 | Add rate limiting | 3 | Not Started |
| 3.7 | Standardize REST conventions | 3 | Not Started |
| 3.8 | Add health checks for dependencies | 3 | Not Started |
| 3.9 | Add DB connection pool config | 3 | Not Started |
| 3.10 | Implement notification service | 3 | Not Started |
