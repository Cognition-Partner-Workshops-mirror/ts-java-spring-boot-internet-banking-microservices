# Remediation Roadmap

> Prioritized plan to address gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md).
> Organized into three phases: Quick Wins, Important Improvements, and Polish.

---

## Phase 1: Quick Wins (1-2 Weeks)

> High-impact, low-effort items that immediately improve security, correctness, and developer experience.

### 1.1 Fix Generic Exception Handler (GAP-ERR-01)
**Severity: Critical | Effort: Small**

Replace the catch-all `Exception.class` handler in all four `GlobalExceptionHandler` classes to return `500 Internal Server Error` with a structured `ErrorResponse` instead of leaking stack traces via `400 Bad Request`.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler 
in all four services (core-banking-service, internet-banking-user-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service). 
Change the catch-all @ExceptionHandler({Exception.class}) method to:
1. Return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) instead of badRequest()
2. Return a structured ErrorResponse with code="INTERNAL_ERROR" and a generic message 
   "An unexpected error occurred" — do NOT include the exception details in the response
3. Log the full exception at ERROR level with log.error("Unexpected error", e)
```

---

### 1.2 Add Proper HTTP Status Codes (GAP-ERR-02)
**Severity: High | Effort: Small**

Add specific `@ExceptionHandler` methods for each exception type with appropriate HTTP status codes.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler 
in all four services to add specific exception handlers:
1. EntityNotFoundException -> return 404 Not Found
2. InsufficientFundsException -> return 422 Unprocessable Entity
3. UserAlreadyRegisteredException -> return 409 Conflict
4. InvalidEmailException -> return 400 Bad Request
5. InvalidBankingUserException -> return 404 Not Found
Keep the existing SimpleBankingGlobalException handler as a fallback for any other custom exceptions.
All responses should use the ErrorResponse structure with code and message fields.
```

---

### 1.3 Add Input Validation (GAP-SEC-02)
**Severity: Critical | Effort: Small**

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add input validation:

1. Add spring-boot-starter-validation dependency to build.gradle in all 4 business services.

2. Add validation annotations to these DTOs:
   - core-banking-service FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, 
     @NotNull @Positive amount
   - core-banking-service UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, 
     @NotBlank referenceNumber, @NotBlank account
   - internet-banking-user-service User: @NotBlank @Email email, @NotBlank identification, 
     @NotBlank @Size(min=8) password
   - internet-banking-fund-transfer-service FundTransferRequest: @NotBlank fromAccount, 
     @NotBlank toAccount, @NotNull @Positive amount
   - internet-banking-utility-payment-service UtilityPaymentRequest: @NotNull providerId, 
     @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

3. Add @Valid annotation before @RequestBody in all controller POST/PATCH methods.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns 
   400 Bad Request with field-level error messages.
```

---

### 1.4 Fix Password Exposure in User DTO (GAP-SEC-04)
**Severity: High | Effort: Small**

Prevent the password field from being serialized in API responses.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, in the internet-banking-user-service,
add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field in 
src/main/java/com/javatodev/finance/model/dto/User.java. This ensures the password is accepted 
on input (registration) but never included in JSON responses.
```

---

### 1.5 Fix ResponseEntity Type Parameters (GAP-API-01)
**Severity: Medium | Effort: Small**

Add generic type parameters to all `ResponseEntity` return types.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update all controller methods 
across all services to use typed ResponseEntity<T> instead of raw ResponseEntity. For example:
- AccountController.getBankAccount -> ResponseEntity<BankAccount>
- TransactionController.fundTransfer -> ResponseEntity<FundTransferResponse>
- FundTransferController.sendFundTransfer -> ResponseEntity<FundTransferResponse>
- FundTransferController.readFundTransfers -> ResponseEntity<List<FundTransfer>>
etc. Check every controller in every service.
```

---

### 1.6 Fix OpenAPI Dependency (GAP-API-05)
**Severity: Medium | Effort: Small**

Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in all non-gateway services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, in the build.gradle files for 
core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, 
and internet-banking-user-service, change the dependency:
  'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
  'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
Only the internet-banking-api-gateway should keep the webflux variant.
```

---

### 1.7 Fix TransactionEntity Mapping (GAP-RES-07)
**Severity: High | Effort: Small**

Change `@OneToOne` to `@ManyToOne` and remove `CascadeType.ALL`.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, in core-banking-service, 
edit TransactionEntity.java:
1. Change @OneToOne(cascade = CascadeType.ALL) to @ManyToOne(fetch = FetchType.LAZY)
2. Keep the @JoinColumn annotation as-is
This correctly models that one account has many transactions, and prevents 
cascade-deleting an account when a transaction is removed.
```

---

### 1.8 Fix Logging Issues (GAP-OBS-01)
**Severity: Medium | Effort: Small**

Fix string concatenation in logging statements and incorrect placeholder usage.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix these logging issues:

1. internet-banking-fund-transfer-service FundTransferService.java line 32:
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To: log.info("Sending fund transfer request {}", request)

2. internet-banking-user-service CustomFeignErrorDecoder.java lines 55, 62:
   Change: log.error("IO Exception on reading exception message feign client" + e)
   To: log.error("IO Exception on reading exception message feign client", e)

3. internet-banking-user-service KeycloakUserService.java line 45:
   Change: log.error("User not found under given ID {}", e.toString())
   To: log.error("User not found under given ID", e)
   Also remove the unused imports for org.slf4j.Logger and org.slf4j.LoggerFactory.
```

---

### 1.9 Fix Keycloak Singleton Thread Safety (GAP-SEC-05)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, in internet-banking-user-service, 
refactor KeycloakProperties.java to make the Keycloak singleton thread-safe. Replace the static 
field + null-check pattern with a synchronized block or, better yet, use a @Bean method in a 
@Configuration class to let Spring manage the Keycloak instance lifecycle as a singleton bean.
```

---

### 1.10 Add Feign Timeout Configuration (GAP-RES-03)
**Severity: High | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Feign timeout configuration.
Since configuration is externalized via Spring Cloud Config, the ideal place is the external 
config repo. However, you can also add defaults in each service's application.yml:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Add this to the application.yml of internet-banking-fund-transfer-service, 
internet-banking-utility-payment-service, and internet-banking-user-service.
```

---

## Phase 2: Important Improvements (3-6 Weeks)

> Structural improvements that significantly improve reliability, maintainability, and testability.

### 2.1 Add Circuit Breakers with Resilience4j (GAP-RES-01, GAP-RES-04)
**Severity: High | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add circuit breaker support:

1. Add these dependencies to build.gradle for fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breakers for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback in fund-transfer service
   - BankingCoreRestClientFallback in utility-payment and user services
   
4. Configure circuit breaker defaults in application.yml:
   resilience4j.circuitbreaker.instances.default:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     
5. Add @FeignClient(..., fallback = XxxFallback.class) to each Feign client interface.
```

---

### 2.2 Add Retry Policies (GAP-RES-02)
**Severity: High | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add retry configuration 
for Feign clients:

1. Add to build.gradle of fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.retry:spring-retry'

2. Add retry configuration to application.yml:
   spring.cloud.openfeign.client.config.default:
     retryer: feign.Retryer.Default
   
   Or configure Resilience4j retry:
   resilience4j.retry.instances.default:
     maxAttempts: 3
     waitDuration: 1s
     retryExceptions:
       - java.net.ConnectException
       - feign.RetryableException

Note: Do NOT retry POST requests to fund-transfer or util-payment endpoints 
(non-idempotent). Only retry GET requests.
```

---

### 2.3 Extract Shared Library (GAP-ORG-02)
**Severity: High | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a shared library module:

1. Create a new directory: internet-banking-common/
2. Set up build.gradle as a plain Java library (no Spring Boot plugin, just java-library plugin)
3. Move these shared classes into it:
   - com.javatodev.finance.exception.SimpleBankingGlobalException
   - com.javatodev.finance.exception.ErrorResponse
   - com.javatodev.finance.exception.GlobalExceptionHandler (as a base class)
   - com.javatodev.finance.model.dto.AuditAware
   - com.javatodev.finance.model.TransactionStatus
   - com.javatodev.finance.configuration.filter.ApiRequestContext
   - com.javatodev.finance.configuration.filter.ApiRequestContextHolder
   - com.javatodev.finance.configuration.filter.AppAuthUserFilter
   - com.javatodev.finance.model.mapper.BaseMapper

4. Create a root settings.gradle that includes all service modules.
5. Update each service's build.gradle to depend on the common module:
   implementation project(':internet-banking-common')
6. Remove the duplicated classes from each service.
```

---

### 2.4 Add Unit Tests for All Services (GAP-TEST-01)
**Severity: Critical | Effort: Large**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit tests:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid email, user not found), 
     readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: mock KeycloakManager, test createUser, readUser, readUserByEmail
   - UserControllerTest: use MockMvc to test all endpoints with mocked service

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (success, core banking failure), readAllTransfers
   - FundTransferControllerTest: MockMvc tests for POST and GET endpoints

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (success, failure), readPayments
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

Use Mockito for mocking dependencies. Use @WebMvcTest for controller tests.
Each test class should test happy path, error paths, and edge cases.
```

---

### 2.5 Add Feign Error Decoders (GAP-ERR-03)
**Severity: High | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add proper Feign error handling:

1. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
   - Create a CustomFeignErrorDecoder class (similar to the one in user-service but improved)
   - Use a shared ObjectMapper instance (injected, not created per call)
   - Map 400 -> SimpleBankingGlobalException, 404 -> EntityNotFoundException, 
     500 -> ServiceUnavailableException (new), others -> generic exception
   - Register it in CustomFeignClientConfiguration as a @Bean

2. In internet-banking-user-service:
   - Wire the existing CustomFeignErrorDecoder into the BankingCoreRestClient by adding 
     configuration = CustomFeignClientConfiguration.class to the @FeignClient annotation
   - Add CustomFeignErrorDecoder as a @Bean in CustomFeignClientConfiguration
   - Refactor to use an injected ObjectMapper instead of creating new instances
```

---

### 2.6 Externalize Secrets from Source Code (GAP-SEC-01)
**Severity: Critical | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, externalize hardcoded credentials:

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace hardcoded passwords with environment variable references:
     MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
     KC_DB_PASSWORD: ${KC_DB_PASSWORD:-password}
     KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-password}
   - Create a .env.example file with placeholder values
   - Add .env to .gitignore

2. In docker-compose/mysql/Dockerfile:
   - Remove the ENV MYSQL_ROOT_PASSWORD line (it's already set via docker-compose)

3. In docker-compose/mysql/privileges.sql:
   - Add a comment that the password should be changed for non-local deployments
   
4. Remove test credentials from README.md or move them to a separate 
   TESTING.md document that's clearly marked as local-only.
```

---

### 2.7 Add Structured Logging (GAP-OBS-02)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add structured JSON logging:

1. Add to build.gradle of all services:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with pattern layout for local development (spring profile: default, dev)
   - Console appender with LogstashEncoder for production (spring profile: docker, prod)
   - Include MDC fields: traceId, spanId, service name

3. This enables structured log aggregation with ELK/Splunk/CloudWatch.
```

---

### 2.8 Add Prometheus Metrics (GAP-OBS-04)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics:

1. Add to build.gradle of all services:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Enable the Prometheus endpoint in application.yml (or external config):
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom business metrics using Micrometer in the service classes:
   - core-banking-service: Counter for fund transfers, utility payments
   - fund-transfer-service: Timer for transfer processing, Counter for success/failure
   - utility-payment-service: Timer for payment processing, Counter for success/failure
   - user-service: Counter for registrations, approvals
```

---

### 2.9 Add Rate Limiting to API Gateway (GAP-RES-05)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting 
to the API Gateway:

1. Add Redis dependency to internet-banking-api-gateway/build.gradle:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Add RequestRateLimiter filter to gateway routes in the external config:
   spring.cloud.gateway.routes:
     - id: fund-transfer
       filters:
         - name: RequestRateLimiter
           args:
             redis-rate-limiter.replenishRate: 10
             redis-rate-limiter.burstCapacity: 20
             key-resolver: "#{@userKeyResolver}"

3. Create a UserKeyResolver @Bean that extracts the user principal from the JWT.

4. Add Redis to docker-compose.yml.
```

---

## Phase 3: Polish (6-12 Weeks)

> Architectural improvements and comprehensive quality measures for long-term maintainability.

### 3.1 Implement Saga Pattern for Transaction Consistency (GAP-RES-06)
**Severity: Critical | Effort: Large**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, implement a saga pattern 
for fund transfers and utility payments to prevent data inconsistency:

Option A - Choreography-based Saga (simpler):
1. Add RabbitMQ dependencies to fund-transfer, utility-payment, and core-banking services
2. Instead of synchronous Feign calls, publish TransferRequested/PaymentRequested events
3. Core Banking consumes events, processes them, publishes TransferCompleted/TransferFailed
4. Fund Transfer / Utility Payment services consume completion events and update local status
5. Add a scheduled job to detect and handle timed-out PENDING/PROCESSING records

Option B - Orchestration-based Saga (more control):
1. Keep the current synchronous flow but add compensation logic
2. If the local update fails after core banking succeeds, publish a compensation event
3. Add idempotency keys to prevent duplicate processing
4. Add a reconciliation scheduled job that checks PENDING records older than X minutes

Start with Option B as it requires less architectural change. Add idempotency keys first:
- Generate a UUID before calling core banking
- Include it in the request as an idempotency key
- Core banking checks for duplicate transaction IDs before processing
```

---

### 3.2 Add Integration Tests with Testcontainers (GAP-TEST-02)
**Severity: High | Effort: Large**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests:

1. Add to build.gradle of all business services:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create a base test class with @Testcontainers that starts MySQL.

3. For core-banking-service:
   - AccountControllerIntegrationTest: test GET endpoints with real DB
   - TransactionControllerIntegrationTest: test fund transfer and utility payment 
     with real DB, verify account balances change correctly

4. For internet-banking-fund-transfer-service:
   - FundTransferIntegrationTest: use WireMock to mock core banking, test full 
     request flow from controller to DB

5. For internet-banking-utility-payment-service:
   - UtilityPaymentIntegrationTest: similar to fund transfer

6. For internet-banking-user-service:
   - UserIntegrationTest: use WireMock for core banking, mock Keycloak admin client

7. Fix existing context load tests to use test profiles that don't require 
   external dependencies (disable Eureka, use H2, mock Keycloak).
```

---

### 3.3 Add Contract Tests (GAP-TEST-03)
**Severity: Medium | Effort: Large**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests:

1. Add to core-banking-service build.gradle:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   
2. Define contracts in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number} (success + not found)
   - POST /api/v1/transaction/fund-transfer (success + insufficient funds)
   - POST /api/v1/transaction/util-payment (success + insufficient funds)
   - GET /api/v1/user/{identification} (success + not found)

3. Generate contract stubs JAR from core-banking-service.

4. Add consumer-side contract tests in fund-transfer, utility-payment, and user services 
   using the stubs JAR with @AutoConfigureStubRunner.

This ensures API changes in core-banking-service are caught at build time.
```

---

### 3.4 Convert to Multi-Module Gradle Build (GAP-ORG-01)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, convert to a multi-module 
Gradle build:

1. Create a root build.gradle with:
   - Common plugin versions (Spring Boot, dependency management)
   - Shared dependency versions in ext block
   - subprojects block with common configurations (Java 21, test config, repositories)

2. Create a root settings.gradle including all modules:
   include 'core-banking-service', 'internet-banking-api-gateway', etc.

3. Simplify each service's build.gradle to only declare service-specific dependencies.

4. Remove individual Gradle wrapper directories from each service 
   (keep only the root wrapper).

5. Update docker-compose entrypoints if needed.
```

---

### 3.5 Add Dependency Vulnerability Scanning (GAP-SEC-06)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add OWASP Dependency-Check:

1. Add to the root build.gradle (or each service if not yet multi-module):
   plugins {
     id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure:
   dependencyCheck {
     failBuildOnCVSS = 7
     suppressionFile = 'config/owasp-suppressions.xml'
   }

3. Create config/owasp-suppressions.xml with an empty suppressions file.

4. Add a CI step (when CI is created) to run: ./gradlew dependencyCheckAnalyze
```

---

### 3.6 Add CI/CD Pipeline
**Severity: High | Effort: Medium**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a GitHub Actions CI pipeline:

1. Create .github/workflows/ci.yml with:
   - Trigger on push to main and pull requests
   - Java 21 setup with Gradle caching
   - Steps for each service:
     a. Build: ./gradlew :service-name:build
     b. Test: ./gradlew :service-name:test
     c. OWASP check: ./gradlew :service-name:dependencyCheckAnalyze
   - Upload test reports as artifacts
   - Use a matrix strategy to build all services in parallel

2. Create .github/workflows/docker.yml for building and pushing Docker images 
   on tag/release.
```

---

### 3.7 Complete OpenAPI Documentation (GAP-API-06)
**Severity: Low | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, enhance OpenAPI documentation:

1. Add @Schema annotations to all DTOs (description, example values):
   - FundTransferRequest, UtilityPaymentRequest, User, etc.
   
2. Add @ApiResponse annotations to controller methods:
   - @ApiResponse(responseCode="200", description="Success")
   - @ApiResponse(responseCode="400", description="Validation error")
   - @ApiResponse(responseCode="404", description="Entity not found")
   - @ApiResponse(responseCode="500", description="Internal server error")

3. Add @Parameter annotations for path and query parameters.

4. Configure OpenAPI info in each service's application.yml:
   springdoc:
     info:
       title: Core Banking Service API
       version: 1.0.0
       description: ...
```

---

### 3.8 Standardize Pagination Response (GAP-API-03)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, standardize pagination:

1. Create a generic PageResponse<T> class in the shared library with fields:
   content, page, size, totalElements, totalPages

2. Update all list endpoints to return PageResponse<T> instead of List<T>.

3. Update service methods to return Page<T> from repositories and map to PageResponse.

This provides consistent pagination metadata across all services.
```

---

### 3.9 Add Custom Health Indicators (GAP-OBS-03)
**Severity: Low | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators:

1. core-banking-service: Add a DatabaseHealthIndicator that checks MySQL connectivity.
2. internet-banking-user-service: Add a KeycloakHealthIndicator that pings the Keycloak 
   server URL.
3. fund-transfer and utility-payment services: Add a CoreBankingHealthIndicator that 
   calls the core banking actuator health endpoint via Feign.

Implement by creating classes that implement HealthIndicator and are annotated with @Component.
```

---

### 3.10 Configure Distributed Tracing Sampling (GAP-OBS-05)
**Severity: Low | Effort: Small**

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure tracing:

Add to each service's application.yml (or external config):
management:
  tracing:
    sampling:
      probability: 1.0  # 100% for dev/staging, reduce to 0.1 for production

This ensures all traces are captured during development and testing.
```

---

## Summary

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 10 items | 1-2 weeks | Security hardening, correct HTTP semantics, input validation, fix data model bug, proper logging |
| **Phase 2** | 9 items | 3-6 weeks | Resilience patterns, shared library, unit tests, structured logging, metrics, secrets management |
| **Phase 3** | 10 items | 6-12 weeks | Saga pattern, integration/contract tests, CI/CD, multi-module build, complete API docs |

### Priority Order Within Each Phase

**Phase 1 (do in this order):**
1. GAP-SEC-02 — Input validation (prevents invalid data from entering system)
2. GAP-ERR-01 — Fix exception handler (stops leaking internals)
3. GAP-SEC-04 — Fix password exposure
4. GAP-RES-07 — Fix @OneToOne mapping
5. GAP-ERR-02 — Proper HTTP status codes
6. GAP-API-05 — Fix OpenAPI dependency
7. GAP-API-01 — Type ResponseEntity
8. GAP-OBS-01 — Fix logging
9. GAP-SEC-05 — Fix Keycloak singleton
10. GAP-RES-03 — Feign timeouts

**Phase 2 (do in this order):**
1. GAP-SEC-01 — Externalize secrets
2. GAP-RES-01 + GAP-RES-04 — Circuit breakers + fallbacks
3. GAP-RES-02 — Retry policies
4. GAP-ERR-03 — Feign error decoders
5. GAP-TEST-01 — Unit tests
6. GAP-ORG-02 — Shared library
7. GAP-OBS-02 — Structured logging
8. GAP-OBS-04 — Prometheus metrics
9. GAP-RES-05 — Rate limiting

**Phase 3 (do in this order):**
1. GAP-RES-06 — Saga pattern (highest severity)
2. GAP-TEST-02 — Integration tests
3. CI/CD pipeline (enables everything else)
4. GAP-ORG-01 — Multi-module build
5. GAP-SEC-06 — Dependency scanning
6. GAP-TEST-03 — Contract tests
7. GAP-API-03 — Pagination standardization
8. GAP-API-06 — Complete OpenAPI docs
9. GAP-OBS-03 — Custom health indicators
10. GAP-OBS-05 — Tracing configuration
