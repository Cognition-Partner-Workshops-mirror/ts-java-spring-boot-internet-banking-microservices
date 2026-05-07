# Remediation Roadmap

This document prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased plan with actionable Devin prompts for each item.

---

## Phase 0: Fix Now (Day 1) — Critical Correctness Bug

### 0.1 Fix `availableBalance` Double-Mutation Bug (CX-1 / RE-9)

**Gap:** Every transaction silently corrupts account balances. `TransactionService` updates `actualBalance`, then reads the *already-mutated* value to compute `availableBalance`, effectively applying the amount twice.

**Impact:** After a $100 transfer from a $1000 account: `actualBalance` = $900 (correct), `availableBalance` = $800 (WRONG — should be $900). The error compounds with every transaction.

**Affected locations:**
- `TransactionService.internalFundTransfer()` lines 90-91 (debit side)
- `TransactionService.internalFundTransfer()` lines 99-100 (credit side)
- `TransactionService.utilPayment()` lines 63-64

**Fix:** Compute the new balance once, then assign it to both fields.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, fix a critical balance calculation bug in core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java:

BUG: The code does:
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance().subtract(amount));

The second line reads the ALREADY UPDATED actualBalance, so availableBalance gets amount subtracted TWICE.

FIX all three locations:

1. internalFundTransfer() debit (lines 90-91):
   BigDecimal newFromBalance = fromBankAccountEntity.getActualBalance().subtract(amount);
   fromBankAccountEntity.setActualBalance(newFromBalance);
   fromBankAccountEntity.setAvailableBalance(newFromBalance);

2. internalFundTransfer() credit (lines 99-100):
   BigDecimal newToBalance = toBankAccountEntity.getActualBalance().add(amount);
   toBankAccountEntity.setActualBalance(newToBalance);
   toBankAccountEntity.setAvailableBalance(newToBalance);

3. utilPayment() debit (lines 63-64):
   BigDecimal newBalance = fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount());
   fromAccount.setActualBalance(newBalance);
   fromAccount.setAvailableBalance(newBalance);

Then add a unit test in TransactionServiceTest that verifies:
- After a $100 transfer from a $1000 account, BOTH actualBalance AND availableBalance equal $900
- After a $100 credit to a $500 account, BOTH actualBalance AND availableBalance equal $600

Also create a Flyway migration V1.0.20240101000000__fix_available_balance.sql that reconciles existing data:
  UPDATE banking_core_account SET available_balance = actual_balance;
```

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be resolved with minimal code changes and low risk of regressions.

### 1.1 Fix Exception Handler HTTP Status Codes (EH-1)

**Gap:** All exceptions return HTTP 400 regardless of type.

**Fix:** Map `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, generic `Exception` → 500.

**Devin Prompt:**
```
In the repository ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler in ALL services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return proper HTTP status codes:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity
- SimpleBankingGlobalException → 400 Bad Request
- Generic Exception → 500 Internal Server Error

Also add a timestamp field to the ErrorResponse DTO. Ensure existing tests still pass.
```

---

### 1.2 Remove Internal Details from Error Responses (EH-2)

**Gap:** Generic exception handler exposes `"Exception occur inside API " + e` to consumers.

**Fix:** Return a generic error message and log the full exception server-side.

**Devin Prompt:**
```
In all GlobalExceptionHandler classes across all services in ts-java-spring-boot-internet-banking-microservices, replace the generic Exception handler that returns "Exception occur inside API " + e with:
- Log the full exception at ERROR level with stack trace
- Return a generic ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred. Please try again later."
- Return HTTP 500 status code
```

---

### 1.3 Stop Logging Sensitive Data (OB-4)

**Gap:** User passwords and financial data logged via `toString()`.

**Fix:** Remove or redact sensitive fields from log statements.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, audit all log statements that log request objects using toString(). In particular:
1. internet-banking-user-service UserController: Remove password from log output by logging only the email field instead of the full request
2. internet-banking-fund-transfer-service FundTransferController: Log only fromAccount and toAccount, not the amount
3. internet-banking-utility-payment-service UtilityPaymentService: Log only the account and providerId

Also add @ToString.Exclude on the password field in the User DTO class.
```

---

### 1.4 Add Bean Validation to Request DTOs (EH-5, SE-3)

**Gap:** No input validation on any request bodies.

**Fix:** Add Jakarta Bean Validation annotations and `@Valid` on controller parameters.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation to all request DTOs:

1. FundTransferRequest (both core-banking and fund-transfer-service versions):
   - @NotBlank on fromAccount, toAccount
   - @NotNull @Positive on amount

2. UtilityPaymentRequest (both core-banking and utility-payment-service versions):
   - @NotNull on providerId
   - @NotNull @Positive on amount
   - @NotBlank on referenceNumber, account

3. User DTO (user-service):
   - @NotBlank @Email on email
   - @NotBlank on identification
   - @NotBlank @Size(min=8) on password

4. Add @Valid annotation on all @RequestBody parameters in controllers.

5. Add a MethodArgumentNotValidException handler to GlobalExceptionHandler that returns 400 with field-level error details.

Add spring-boot-starter-validation dependency to each service's build.gradle if not already present.
```

---

### 1.5 Add Feign Client Timeouts (RE-3)

**Gap:** No connect/read timeouts configured on Feign clients.

**Fix:** Configure timeouts in application configuration.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign client timeout configuration to all services that use Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service).

Add to each service's application.yml:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000

This ensures all Feign calls fail fast rather than hanging indefinitely.
```

---

### 1.6 Fix OpenAPI Dependency (AD-6)

**Gap:** Services use `springdoc-openapi-starter-webflux-ui` but are non-reactive (Servlet-based).

**Fix:** Replace with `springdoc-openapi-starter-webmvc-ui`.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, replace the OpenAPI dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service build.gradle files:

Replace: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With: implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

These services use Spring MVC (not WebFlux), so the webmvc variant is correct. Verify the /swagger-ui.html endpoint works by running each service locally.
```

---

### 1.7 Add Test Coverage Plugin (TE-4)

**Gap:** No JaCoCo or similar coverage reporting.

**Fix:** Add JaCoCo plugin to all service builds.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add the JaCoCo test coverage plugin to all 6 services:

1. Add to each build.gradle: id 'jacoco' in the plugins block
2. Add JaCoCo configuration:
   jacoco { toolVersion = "0.8.12" }
   jacocoTestReport {
     dependsOn test
     reports { xml.required = true; html.required = true }
   }
   test { finalizedBy jacocoTestReport }
3. Run ./gradlew test jacocoTestReport in core-banking-service to verify it works.
```

---

### 1.8 Remove Build Artifacts from VCS (CO-4)

**Gap:** Compiled `.class` files and JARs committed to the repository.

**Fix:** Add `build/` to `.gitignore` and remove tracked build directories.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:
1. Verify the root .gitignore includes 'build/' and '**/build/'
2. If any build/ directories are tracked by git, remove them: git rm -r --cached */build/
3. Ensure each service-level .gitignore also includes build/
4. Commit the .gitignore changes and removal of build artifacts.
```

---

## Phase 2: Important (3-6 weeks)

Structural improvements that significantly reduce risk and improve maintainability.

### 2.1 Add Circuit Breakers (RE-1)

**Gap:** No circuit breaker on Feign clients. Downstream failures cascade.

**Fix:** Add Resilience4j circuit breaker to all Feign clients.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breaker support to the fund-transfer-service and utility-payment-service:

1. Add dependencies to build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker on Feign: spring.cloud.openfeign.circuitbreaker.enabled=true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return error response with "Core banking service unavailable"
   - BankingCoreRestClientFallback (utility-payment): return error response with "Core banking service unavailable"

4. Register fallbacks: @FeignClient(name="core-banking-service", fallback=BankingCoreFeignClientFallback.class)

5. Configure circuit breaker properties:
   resilience4j.circuitbreaker.instances.core-banking-service:
     sliding-window-size: 10
     failure-rate-threshold: 50
     wait-duration-in-open-state: 30s
```

---

### 2.2 Secure Downstream Services (SE-1)

**Gap:** Business services accept unauthenticated requests when accessed directly.

**Fix:** Add Spring Security with JWT validation to downstream services, or enforce network-level isolation.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Security OAuth2 Resource Server to core-banking-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service:

1. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
2. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-security'

3. Create SecurityConfiguration in each service that:
   - Validates JWT tokens using the same Keycloak JWK URI
   - Permits actuator/health endpoints without auth
   - Requires authentication for all other endpoints

4. Update the Feign client configuration (CustomFeignClientConfiguration) to forward the Authorization header from incoming requests to outgoing Feign calls using a RequestInterceptor.

5. Update test configurations to disable security for unit tests.
```

---

### 2.3 Add Unit Tests for All Services (TE-1)

**Gap:** Three business services have zero meaningful tests.

**Fix:** Add unit tests for service layer logic.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test successful transfer, test Feign client failure handling, test entity status transitions

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test successful payment, test Feign client failure, test entity status transitions

3. internet-banking-user-service:
   - UserServiceTest: test createUser success, test duplicate email rejection, test user not found in core banking, test email mismatch, test updateUser approval flow, test readUsers with Keycloak enrichment

4. Use Mockito to mock Feign clients and repositories. Use @ExtendWith(MockitoExtension.class).
5. Ensure all tests pass with ./gradlew test
```

---

### 2.4 Add Retry Policies (RE-2)

**Gap:** No automatic retries for transient Feign failures.

**Fix:** Add Spring Retry or Resilience4j retry.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add retry policies to Feign clients in fund-transfer-service and utility-payment-service:

1. Add dependency: implementation 'org.springframework.retry:spring-retry'
2. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-aop'

3. Configure Resilience4j retry (works with the circuit breaker from 2.1):
   resilience4j.retry.instances.core-banking-service:
     max-attempts: 3
     wait-duration: 1s
     exponential-backoff-multiplier: 2
     retry-exceptions:
       - java.net.ConnectException
       - java.net.SocketTimeoutException
       - feign.RetryableException

4. Ensure retries do NOT apply to POST operations that are not idempotent (fund transfers) unless an idempotency key is present.
```

---

### 2.5 Add Idempotency to Fund Transfers (RE-6)

**Gap:** Fund transfer endpoint is not idempotent; retries can cause double transfers.

**Fix:** Add an idempotency key mechanism.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add idempotency support to the fund transfer flow:

1. Add an optional "idempotencyKey" field to FundTransferRequest in fund-transfer-service
2. Before processing, check if a fund_transfer record with this idempotencyKey already exists
3. If found, return the existing response (do not reprocess)
4. If not found, proceed with normal flow and store the key
5. Add a unique constraint on idempotencyKey column in the fund_transfer table
6. Add a Flyway migration for the new column
7. Document the idempotency key header in the OpenAPI annotations
```

---

### 2.6 Fix Balance Update Race Condition (RE-8)

**Gap:** Concurrent transfers can overdraw accounts due to read-then-write without locking.

**Fix:** Use optimistic locking (`@Version`) or pessimistic locking (`SELECT FOR UPDATE`).

**Devin Prompt:**
```
In core-banking-service in ts-java-spring-boot-internet-banking-microservices, fix the balance update race condition in TransactionService:

1. Add @Version field to BankAccountEntity for optimistic locking
2. Add a Flyway migration to add the version column: ALTER TABLE banking_core_account ADD COLUMN version BIGINT DEFAULT 0
3. Wrap balance operations in a retry loop that handles OptimisticLockException (retry up to 3 times)
4. Alternative approach: use @Lock(LockModeType.PESSIMISTIC_WRITE) on the repository method findByNumber()

Choose optimistic locking as the preferred approach since contention is expected to be low. Add a test that verifies the version field is incremented after a transfer.
```

---

### 2.7 Extract Shared Library (CO-1)

**Gap:** Common code duplicated across services.

**Fix:** Create a shared library module.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a shared library:

1. Create a new directory: internet-banking-common/
2. Set up build.gradle as a plain Java library (no spring-boot plugin, just java-library)
3. Move these common classes into it:
   - com.javatodev.finance.model.dto.AuditAware
   - com.javatodev.finance.model.mapper.BaseMapper
   - com.javatodev.finance.exception.ErrorResponse
   - com.javatodev.finance.exception.SimpleBankingGlobalException
   - com.javatodev.finance.exception.GlobalExceptionHandler
   - com.javatodev.finance.exception.EntityNotFoundException
   - com.javatodev.finance.configuration.filter.ApiRequestContext
   - com.javatodev.finance.configuration.filter.ApiRequestContextHolder
   - com.javatodev.finance.configuration.filter.AppAuthUserFilter
4. Create a root settings.gradle that includes all services and the common library
5. Update each service's build.gradle to depend on the common library: implementation project(':internet-banking-common')
6. Remove duplicated classes from individual services
7. Verify all services still compile: ./gradlew build
```

---

### 2.8 Add Structured Logging (OB-1)

**Gap:** Text-format logs not suitable for log aggregation.

**Fix:** Configure JSON logging output.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add structured JSON logging to all services:

1. Add dependency to all services: implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
2. Create src/main/resources/logback-spring.xml in each service with:
   - Console appender with pattern including traceId/spanId for local dev
   - JSON appender (LogstashEncoder) for docker/production profile
3. Include trace ID and span ID in log output for correlation with Zipkin
4. Use profile-based activation: text format for "dev", JSON for "docker"/"prod"
```

---

### 2.9 Externalize Secrets (SE-2)

**Gap:** Database passwords and admin credentials hardcoded in docker-compose files.

**Fix:** Use environment variables or Docker secrets.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/docker-compose:

1. Create a .env.example file with all required environment variables:
   MYSQL_ROOT_PASSWORD=
   MYSQL_APP_PASSWORD=
   KEYCLOAK_ADMIN_PASSWORD=
   KC_DB_PASSWORD=

2. Update docker-compose.yml to reference ${VARIABLE} syntax for all passwords
3. Update mysql/Dockerfile to use ARG/ENV from docker-compose
4. Add .env to .gitignore
5. Update README.md with instructions to copy .env.example to .env and fill in values
```

---

## Phase 3: Polish (6-12 weeks)

Improvements that enhance developer experience and operational maturity.

### 3.1 Add Integration Tests with Testcontainers (TE-2)

**Gap:** No integration tests verifying full request-response cycles.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers:

1. Add to each service's build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create integration test classes for core-banking-service:
   - AccountControllerIntegrationTest: test GET /api/v1/account/bank-account/{number}
   - TransactionControllerIntegrationTest: test POST /api/v1/transaction/fund-transfer

3. Use @SpringBootTest(webEnvironment = RANDOM_PORT) with TestRestTemplate
4. Use @Testcontainers with MySQLContainer for real database testing
5. Add test-specific application-test.yml with Testcontainer JDBC URL
```

---

### 3.2 Add Contract Tests (TE-3)

**Gap:** No consumer-driven contract tests between services.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests:

1. Add Spring Cloud Contract to core-banking-service (provider side):
   - Plugin: id 'org.springframework.cloud.contract' version '4.1.1'
   - Add contracts in src/test/resources/contracts/ for fund-transfer and utility-payment endpoints

2. Add contract stubs verification in consumer services:
   - fund-transfer-service: testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'
   - Write tests that verify BankingCoreFeignClient against the contract stubs

3. This ensures changes to core-banking APIs are caught before deployment.
```

---

### 3.3 Add Rate Limiting (RE-5)

**Gap:** No rate limiting on the API Gateway.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-api-gateway, add rate limiting:

1. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
2. Configure Spring Cloud Gateway RequestRateLimiter filter:
   - Rate limit by user principal (from JWT)
   - Default: 100 requests/minute per user
   - Fund transfer: 10 requests/minute per user (more restrictive)
3. Add Redis to docker-compose.yml
4. Configure fallback response (HTTP 429 Too Many Requests)
```

---

### 3.4 Add Pagination Metadata to Responses (AD-2, AD-4)

**Gap:** List endpoints return raw arrays without pagination metadata.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a consistent paginated response wrapper:

1. In the shared library, create:
   PagedResponse<T> {
     List<T> content;
     int page;
     int size;
     long totalElements;
     int totalPages;
     boolean last;
   }

2. Update all list endpoints in all services to return PagedResponse<T> instead of List<T>
3. Update the fund-transfer-service readAllTransfers() and utility-payment-service readPayments() to return the full Page metadata
4. Update OpenAPI annotations to document the response shape
```

---

### 3.5 Add Custom Health Indicators (OB-2)

**Gap:** Only default health checks, no visibility into downstream dependencies.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add custom health indicators:

1. core-banking-service: MySQL health indicator (already via Spring Data JPA, ensure it's exposed)
2. internet-banking-user-service: Keycloak health indicator - ping Keycloak server URL
3. internet-banking-fund-transfer-service: Core Banking Service health indicator - call actuator/health on core-banking-service
4. internet-banking-api-gateway: Composite health check for all downstream services

5. Expose health details: management.endpoint.health.show-details=when-authorized
6. Add liveness and readiness probes: management.endpoint.health.probes.enabled=true
```

---

### 3.6 Add Prometheus Metrics (OB-3, OB-6)

**Gap:** No custom business metrics or Prometheus integration.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. Add to all services: implementation 'io.micrometer:micrometer-registry-prometheus'
2. Expose prometheus endpoint: management.endpoints.web.exposure.include=health,info,prometheus
3. Add custom metrics in core-banking-service TransactionService:
   - Counter: banking.transfers.total (tags: status=success|failed)
   - Counter: banking.payments.total (tags: status=success|failed)
   - Timer: banking.transfers.duration
   - Gauge: banking.accounts.active.count
4. Add Prometheus and Grafana to docker-compose with a basic dashboard
```

---

### 3.7 Add Notification Service with RabbitMQ (Planned Feature)

**Gap:** Notification service referenced in architecture but not implemented.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, implement the notification service:

1. Create internet-banking-notification-service/ with Spring Boot + RabbitMQ
2. Add RabbitMQ to docker-compose.yml (image: rabbitmq:3-management, ports: 5672, 15672)
3. In fund-transfer-service and utility-payment-service:
   - Add spring-boot-starter-amqp dependency
   - After successful transaction, publish a notification event to RabbitMQ
   - Event: {type: "FUND_TRANSFER"|"UTILITY_PAYMENT", userId, amount, transactionId, timestamp}
4. Notification service consumes events and logs them (email/SMS integration can be added later)
5. Register notification service with Eureka
```

---

### 3.8 Add API Versioning Strategy (AD-1)

**Gap:** No documented strategy for API evolution.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, document and implement an API versioning strategy:

1. Create docs/API_VERSIONING.md documenting the chosen strategy (URL path versioning: /api/v1/, /api/v2/)
2. Define versioning rules:
   - Non-breaking changes (adding fields) do not require version bump
   - Breaking changes (removing/renaming fields, changing behavior) require new version
   - Previous version supported for 6 months after new version release
3. Add version header to all responses: X-API-Version: 1
4. Configure gateway routing to support version-based routing in the future
```

---

## Implementation Priority Matrix

```
         ┌─────────────────────────────────────────────────┐
         │              HIGH IMPACT                         │
         │                                                 │
   HIGH  │  RE-8 (Race condition)    SE-1 (Auth)          │
   RISK  │  RE-1 (Circuit breaker)   EH-2 (Info leak)    │
         │  RE-6 (Idempotency)       TE-1 (Tests)        │
         │                                                 │
         ├─────────────────────────────────────────────────┤
         │              MODERATE IMPACT                     │
         │                                                 │
   MED   │  EH-1 (Status codes)     SE-3 (Validation)    │
   RISK  │  RE-2 (Retries)          RE-3 (Timeouts)      │
         │  OB-4 (Sensitive logs)   CO-1 (Shared lib)    │
         │                                                 │
         ├─────────────────────────────────────────────────┤
         │              LOWER IMPACT                        │
         │                                                 │
   LOW   │  AD-6 (OpenAPI dep)      TE-4 (Coverage)      │
   RISK  │  CO-4 (Build artifacts)  OB-1 (JSON logs)     │
         │  SE-2 (Secrets)          AD-4 (Pagination)    │
         │                                                 │
         └─────────────────────────────────────────────────┘
```

---

## Success Criteria

| Phase | Metric | Target |
|-------|--------|--------|
| Phase 1 | All requests validated | 100% of POST endpoints have `@Valid` |
| Phase 1 | Proper HTTP status codes | Zero 400 responses for 404/500 scenarios |
| Phase 1 | No sensitive data in logs | Zero password/amount fields logged |
| Phase 2 | Unit test coverage | > 70% line coverage on service layer |
| Phase 2 | Circuit breaker active | All Feign clients wrapped with CB |
| Phase 2 | No race conditions | `@Version` on all balance-sensitive entities |
| Phase 3 | Integration test coverage | At least 1 integration test per endpoint |
| Phase 3 | Custom metrics | All business transactions tracked |
| Phase 3 | Rate limiting active | Gateway enforces per-user limits |
