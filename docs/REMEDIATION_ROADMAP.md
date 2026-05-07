# Remediation Roadmap

This document prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased remediation plan. Each item includes a sample Devin prompt to execute the fix.

---

## Phase 1: Quick Wins (1-2 days each)

High-impact, low-effort fixes that address critical security and stability concerns.

### 1.1 Fix Stack Trace Leakage in Error Handlers (EH-1)

**Severity:** Critical | **Effort:** Small

Replace the generic `Exception` handler in all `GlobalExceptionHandler` classes to return a safe, structured error response instead of raw exception details.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update all GlobalExceptionHandler classes across all services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to replace the generic Exception handler. Instead of returning "Exception occur inside API " + e, return a structured ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred. Please try again later." with HTTP 500 status. Log the full exception at ERROR level for debugging.
```

---

### 1.2 Add Input Validation to All Request Bodies (S-1)

**Severity:** Critical | **Effort:** Small-Medium

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller method parameters.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta Bean Validation (jakarta.validation) annotations to all request DTOs:
- FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber and account
- User (register): @NotBlank @Email on email, @NotBlank on identification, @NotBlank @Size(min=8) on password

Add @Valid annotation on all controller @RequestBody parameters. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns HTTP 422 with field-level error details. Include spring-boot-starter-validation dependency in each build.gradle.
```

---

### 1.3 Fix Feign Client Timeouts (R-2)

**Severity:** Critical | **Effort:** Small

Configure explicit connect and read timeouts for all Feign clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Feign timeout configuration to each service that uses OpenFeign (internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service). In each application.yml, add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

This ensures Feign calls fail fast (5s connect, 10s read) instead of the default 60s.
```

---

### 1.4 Fix HTTP Status Codes in Exception Handlers (EH-2)

**Severity:** High | **Effort:** Small

Map exceptions to appropriate HTTP status codes instead of blanket 400.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update all GlobalExceptionHandler classes to use correct HTTP status codes:
- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException, InvalidBankingUserException -> 400 Bad Request
Keep SimpleBankingGlobalException as 400 by default. Use @ResponseStatus or ResponseEntity.status(HttpStatus.XXX) as appropriate.
```

---

### 1.5 Fix Available Balance Double-Subtraction Bug (R-8)

**Severity:** High | **Effort:** Small

Fix the arithmetic bug in `TransactionService.utilPayment()` where available balance is reduced twice.

**Devin Prompt:**
```
In core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java, fix the utilPayment() method. On line 64, the code does:
  fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
But actualBalance was ALREADY reduced on line 63, so this double-subtracts. Fix it to:
  fromAccount.setAvailableBalance(fromAccount.getActualBalance());
This ensures availableBalance matches the already-updated actualBalance. Apply the same fix pattern in internalFundTransfer() where availableBalance is set after actualBalance is modified.
```

---

### 1.6 Restrict Actuator Endpoints (S-2)

**Severity:** High | **Effort:** Small

Limit publicly accessible actuator endpoints to only health and info.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the SecurityConfiguration in internet-banking-api-gateway to restrict actuator access. Change the permitAll on /actuator/** to only allow /actuator/health and /actuator/info. All other actuator endpoints should require authentication. Update the application config to only expose health and info endpoints:

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

---

### 1.7 Add Structured Logging (O-1)

**Severity:** High | **Effort:** Small

Switch to JSON-formatted log output for all services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add structured JSON logging to all services. Add the logstash-logback-encoder dependency to each build.gradle:
  implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

Create a src/main/resources/logback-spring.xml in each service that outputs JSON format in non-local profiles and plain text in local/dev. Include traceId and spanId from MDC in the JSON output.
```

---

### 1.8 Remove Sensitive Data from Logs (O-2)

**Severity:** High | **Effort:** Small

Stop logging full request objects that may contain passwords and account numbers.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, audit all log.info() statements that log request.toString(). In internet-banking-user-service UserController.createUser(), the User object contains a password field - log only the email. In fund-transfer and utility-payment controllers, log only a transaction identifier, not full account numbers and amounts. Also exclude the password field from User.toString() by adding @ToString.Exclude on the password field.
```

---

## Phase 2: Important (3-5 days each)

Structural improvements that significantly enhance reliability and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j (R-1)

**Severity:** Critical | **Effort:** Medium

Add circuit breaker pattern to all Feign client calls to prevent cascading failures.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breaker support to internet-banking-fund-transfer-service and internet-banking-utility-payment-service:

1. Add dependencies: spring-cloud-starter-circuitbreaker-resilience4j
2. Enable circuit breaker on Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for BankingCoreFeignClient in each service that return appropriate error responses when the circuit is open
4. Configure circuit breaker in application.yml:
   - failure-rate-threshold: 50
   - wait-duration-in-open-state: 30s
   - sliding-window-size: 10
5. Add retry configuration: max-attempts=3, wait-duration=1s
```

---

### 2.2 Add Pessimistic Locking for Balance Updates (R-6)

**Severity:** Critical | **Effort:** Medium

Fix the race condition in fund transfer and payment processing.

**Devin Prompt:**
```
In core-banking-service, fix the race condition in TransactionService.internalFundTransfer() and utilPayment(). Add pessimistic locking when reading accounts for balance modification:

1. In BankAccountRepository, add a method:
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT a FROM BankAccountEntity a WHERE a.number = :number")
   Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String number);

2. Replace bankAccountRepository.findByNumber() calls in TransactionService with findByNumberForUpdate() for all write operations.

3. Ensure @Transactional isolation level is READ_COMMITTED or higher.
```

---

### 2.3 Add Idempotency Keys to Write Operations (R-5)

**Severity:** High | **Effort:** Medium

Prevent duplicate transactions from network retries.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement idempotency for fund transfer and utility payment:

1. Add a transactionReference/idempotencyKey field to FundTransferRequest and UtilityPaymentRequest (client-generated UUID).
2. Before processing, check if a transaction with the same idempotency key already exists in the local database.
3. If it exists and is SUCCESS, return the cached response.
4. If it exists and is PENDING/PROCESSING, return a 409 Conflict.
5. If it doesn't exist, proceed with normal processing.
6. Add a unique constraint on the idempotency key column in both fund_transfer and utility_payment tables.
```

---

### 2.4 Add Unit Tests for User, Fund Transfer, and Utility Payment Services (T-1)

**Severity:** Critical | **Effort:** Large

Write comprehensive unit tests for all untested services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, write unit tests for:

1. internet-banking-user-service: Test UserService (createUser happy path, duplicate email rejection, user not found in core banking, email mismatch, status update to APPROVED enables Keycloak user). Mock KeycloakUserService, BankingCoreRestClient, UserRepository.

2. internet-banking-fund-transfer-service: Test FundTransferService (successful transfer saves PENDING then SUCCESS, Feign error handling, read all transfers). Mock BankingCoreFeignClient, FundTransferRepository.

3. internet-banking-utility-payment-service: Test UtilityPaymentService (successful payment, Feign error handling, read payments). Mock BankingCoreRestClient, UtilityPaymentRepository.

Use JUnit 5 + Mockito, following the same pattern as the existing core-banking-service tests.
```

---

### 2.5 Add Downstream Service Authentication (S-3)

**Severity:** High | **Effort:** Medium

Ensure downstream services verify the X-Auth-Id header and reject direct access.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add security to downstream services so they cannot be called directly without the gateway:

1. Add a shared secret/API key that the gateway includes in requests (e.g., X-Internal-Api-Key header).
2. Add a servlet filter to each downstream service (user, fund-transfer, utility-payment, core-banking) that validates this header.
3. If the header is missing or invalid, return 403 Forbidden.
4. Configure the API key via Spring Cloud Config (externalized, not hardcoded).
5. Update the gateway's GlobalFilter to include this header in all proxied requests.
```

---

### 2.6 Extract Shared Library (CO-1)

**Severity:** High | **Effort:** Medium

Create a shared common module to eliminate code duplication.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a shared library module called 'internet-banking-common':

1. Create a new Gradle subproject with shared code:
   - AuditAware base class
   - BaseMapper interface
   - ErrorResponse DTO
   - GlobalExceptionHandler base class
   - SimpleBankingGlobalException and common exceptions
   - AppAuthUserFilter and ApiRequestContext classes
   - CustomFeignClientConfiguration

2. Convert the project to a multi-module Gradle build with a root settings.gradle.
3. Update each service's build.gradle to depend on the common module.
4. Remove the duplicated classes from each service.
```

---

### 2.7 Add Integration Tests with Testcontainers (T-2)

**Severity:** High | **Effort:** Large

Add integration tests that verify HTTP endpoints with real MySQL.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests using Testcontainers for the core-banking-service:

1. Add dependencies: testcontainers, testcontainers-mysql, testcontainers-junit-jupiter
2. Create a base test class that starts a MySQL Testcontainer and configures Spring datasource.
3. Write integration tests for:
   - AccountController: GET bank account, GET utility account (including not found)
   - TransactionController: POST fund transfer, POST utility payment
   - UserController: GET user by identification, GET paginated users
4. Use @SpringBootTest with WebEnvironment.RANDOM_PORT and TestRestTemplate.
5. Flyway migrations should run automatically against the Testcontainer.
```

---

### 2.8 Add Retry Policies (R-3)

**Severity:** High | **Effort:** Small

Configure automatic retries for transient failures on Feign calls.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Retry support to Feign clients in internet-banking-fund-transfer-service and internet-banking-utility-payment-service:

1. Add dependency: spring-retry
2. Configure in application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               retryer: feign.Retryer.Default
   
3. Or implement a custom Retryer bean that retries 3 times with 1s initial backoff and 1.5x multiplier.
4. Ensure retries only happen on 5xx errors and connection timeouts, NOT on 4xx client errors.
```

---

## Phase 3: Polish (1-2 weeks each)

Improvements that elevate the system to production-grade quality.

### 3.1 Add Contract Tests Between Services (T-3)

**Severity:** High | **Effort:** Large

Implement consumer-driven contract testing to catch breaking API changes.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract testing:

1. In core-banking-service (provider), add spring-cloud-starter-contract-verifier. Define contracts in src/test/resources/contracts/ for each endpoint (fund-transfer, util-payment, bank-account lookup, user lookup).

2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service (consumers), add spring-cloud-contract-stub-runner. Write consumer contract tests that verify the Feign client expectations match the provider's contract.

3. Configure the contracts to generate WireMock stubs that consumers can use for offline testing.
```

---

### 3.2 Add Prometheus Metrics (O-4)

**Severity:** Medium | **Effort:** Small

Enable Prometheus metrics scraping endpoint.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics to all services:

1. Add dependency to each build.gradle: implementation 'io.micrometer:micrometer-registry-prometheus'
2. Expose the prometheus endpoint in actuator config:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
3. Add custom metrics for business operations:
   - Counter: fund_transfers_total (tagged by status: success/failed)
   - Counter: utility_payments_total (tagged by status: success/failed)
   - Timer: fund_transfer_duration_seconds
   - Gauge: active_fund_transfers (currently processing)
```

---

### 3.3 Add OpenAPI Specification Generation (AD-6)

**Severity:** Medium | **Effort:** Small

Fix Swagger/OpenAPI integration and auto-generate API docs.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the OpenAPI/Swagger setup:

1. Replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0' in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service (they use Spring MVC, not WebFlux).

2. Add generic type parameters to all ResponseEntity returns in controllers (e.g., ResponseEntity<BankAccount> instead of raw ResponseEntity).

3. Verify Swagger UI is accessible at /swagger-ui.html for each service.

4. Add @Schema annotations to DTOs for better documentation.
```

---

### 3.4 Add Custom Health Indicators (O-3)

**Severity:** Medium | **Effort:** Small

Implement health checks that verify downstream dependencies.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators:

1. In internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak server and reports UP/DOWN.

2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service: Add a CoreBankingHealthIndicator that calls the core banking actuator/health endpoint.

3. In core-banking-service: The existing DataSource health indicator is sufficient, but add a custom indicator that checks Zipkin connectivity.

4. Configure health details to show in the response:
   management:
     endpoint:
       health:
         show-details: when_authorized
```

---

### 3.5 Add Response Envelope and Pagination Metadata (AD-3)

**Severity:** Medium | **Effort:** Medium

Wrap all API responses in a standard envelope.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a standard API response wrapper:

1. Create an ApiResponse<T> class in the common module:
   {
     "success": true,
     "data": T,
     "error": null,
     "meta": { "timestamp": "...", "traceId": "..." }
   }

2. Create a PagedResponse<T> that extends the envelope with pagination:
   {
     "success": true,
     "data": [...],
     "pagination": { "page": 0, "size": 20, "totalElements": 100, "totalPages": 5 }
   }

3. Update all controllers to return wrapped responses.
4. Update error handlers to use the same envelope format with success=false.
```

---

### 3.6 Implement RabbitMQ Notification Service (Planned Feature)

**Severity:** Medium | **Effort:** Large

Complete the notification service mentioned in the README.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement the notification service:

1. Add spring-boot-starter-amqp to internet-banking-fund-transfer-service and internet-banking-utility-payment-service.
2. After successful fund transfer/utility payment, publish a message to RabbitMQ with transaction details.
3. Create a new microservice 'internet-banking-notification-service' that:
   - Consumes messages from the RabbitMQ queue
   - Logs notification details (placeholder for email/SMS integration)
   - Handles dead-letter queue for failed messages
4. Add RabbitMQ container to docker-compose.yml.
5. Register the notification service with Eureka.
```

---

### 3.7 Add Dependency Vulnerability Scanning (S-9)

**Severity:** Medium | **Effort:** Small

Integrate automated dependency vulnerability checking.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add OWASP dependency-check to the Gradle build:

1. Add the OWASP dependency-check plugin to each service's build.gradle:
   plugins { id 'org.owasp.dependencycheck' version '9.0.9' }

2. Configure it to fail the build on CVSS score >= 7:
   dependencyCheck { failBuildOnCVSS = 7 }

3. Add a GitHub Actions workflow that runs dependency-check on PRs and weekly on the main branch.

4. Alternatively, if a single root build.gradle is created (per CO-1), apply the plugin once at the root level.
```

---

### 3.8 Add Keycloak Singleton Thread Safety Fix (S-5)

**Severity:** Medium | **Effort:** Small

Fix the non-thread-safe singleton pattern in KeycloakProperties.

**Devin Prompt:**
```
In internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java, replace the manual singleton pattern with a Spring-managed @Bean:

1. Remove the static keycloakInstance field and getInstance() method.
2. Create a @Configuration class (e.g., KeycloakConfig) with a @Bean method that builds the Keycloak instance.
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance().
4. This ensures thread safety via Spring's singleton scope and proper lifecycle management.
```

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        |
     Phase 1            |           Phase 2
  (Quick Wins)          |        (Important)
                        |
  R-8, EH-1, S-1       |    R-6, R-1, R-5, T-1
  R-2, EH-2, S-2       |    S-3, CO-1, T-2
  O-1, O-2             |    R-3
                        |
LOW EFFORT ------------|------------- HIGH EFFORT
                        |
  AD-6, S-5, O-4       |    T-3, AD-3, 3.6
  O-3, S-9             |    AD-7
                        |
     Phase 1/3          |          Phase 3
   (Low-hanging)        |         (Polish)
                        |
                   LOW IMPACT
```

---

## Execution Order Recommendation

1. **Week 1:** Items 1.1, 1.2, 1.3, 1.4, 1.5 (critical fixes)
2. **Week 2:** Items 1.6, 1.7, 1.8, 2.8 (quick security + observability)
3. **Week 3-4:** Items 2.1, 2.2, 2.3 (resilience overhaul)
4. **Week 5-6:** Items 2.4, 2.5, 2.6 (testing + security)
5. **Week 7-8:** Items 2.7, 3.1, 3.2, 3.3 (integration tests + monitoring)
6. **Week 9+:** Items 3.4, 3.5, 3.6, 3.7, 3.8 (polish)
