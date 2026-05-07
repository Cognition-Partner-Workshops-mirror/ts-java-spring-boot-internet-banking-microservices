# Remediation Roadmap

This document prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased remediation plan with actionable Devin prompts for each item.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be addressed with small, focused changes.

### 1.1 Fix Generic Exception Handler (Gap 2.1, 2.2, 2.3)

**Problem**: All errors return HTTP 400 with raw exception strings leaked to clients.

**Remediation**:
- Return proper HTTP status codes (404 for EntityNotFound, 500 for unexpected errors, 409 for duplicates).
- Always return structured `ErrorResponse` JSON.
- Never expose internal exception details.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Refactor the GlobalExceptionHandler in all services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to:
1. Return HTTP 404 for EntityNotFoundException
2. Return HTTP 409 for UserAlreadyRegisteredException
3. Return HTTP 422 for InsufficientFundsException and other business exceptions
4. Return HTTP 500 for unexpected Exception with a generic message (never expose stack traces)
5. Always return the structured ErrorResponse object with code and message fields
6. Add @ResponseStatus annotations where appropriate
Ensure all services use the same consistent error response format.
```

---

### 1.2 Add Input Validation (Gap 4.1)

**Problem**: No validation on any request DTO. Null, negative, or empty values are accepted.

**Remediation**:
- Add Jakarta Validation annotations to all request DTOs.
- Add `@Valid` to controller method parameters.
- Handle `MethodArgumentNotValidException` in the GlobalExceptionHandler.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add Jakarta Bean Validation to all request DTOs across all services:
1. FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
2. UtilityPaymentRequest (core-banking): @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account
3. UtilityPaymentRequest (utility-payment-service): same as above
4. User DTO (user-service): @NotBlank @Email email, @NotBlank identification, @NotBlank @Size(min=8) password
5. UserUpdateRequest: @NotNull status
Add @Valid annotation to all controller methods accepting request bodies.
Add a handler for MethodArgumentNotValidException in each GlobalExceptionHandler that returns HTTP 422 with field-level error details.
Add spring-boot-starter-validation dependency to each build.gradle if not already present.
```

---

### 1.3 Fix Password Exposure in User DTO (Gap 4.7)

**Problem**: The `password` field in the User DTO can be serialized in API responses.

**Remediation**:
- Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the password field.
- Or create separate request/response DTOs.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
In the internet-banking-user-service User DTO (src/main/java/com/javatodev/finance/model/dto/User.java), add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field to prevent it from being serialized in API responses. Add the necessary Jackson import.
```

---

### 1.4 Remove Sensitive Data from Logs (Gap 6.2)

**Problem**: `toString()` on request objects logs account numbers, amounts, and potentially passwords.

**Remediation**:
- Remove `toString()` calls on sensitive request objects in log statements.
- Log only non-sensitive identifiers (transaction IDs, user IDs).

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Audit all log statements across all services and remove sensitive data from logs:
1. Replace log messages that call .toString() on FundTransferRequest, UtilityPaymentRequest, and User objects
2. Instead log only non-sensitive identifiers like "Fund transfer initiated from account ending in {}" with last 4 digits
3. Never log amounts, full account numbers, passwords, or email addresses
4. In the User DTO, override toString() to exclude the password field or add @ToString.Exclude on it
```

---

### 1.5 Fix Untyped ResponseEntity (Gap 5.1)

**Problem**: Controllers return raw `ResponseEntity` without type parameters.

**Remediation**:
- Add type parameters to all controller method return types.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add type parameters to all ResponseEntity return types in all controller classes across all services. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. This improves compile-time safety and OpenAPI documentation generation. Do this for every controller method in AccountController, TransactionController, UserController (both services), FundTransferController, and UtilityPaymentController.
```

---

### 1.6 Fix OpenAPI Starter Dependency (Gap 5.6)

**Problem**: WebMVC services incorrectly use `springdoc-openapi-starter-webflux-ui`.

**Remediation**:
- Replace with `springdoc-openapi-starter-webmvc-ui` in core-banking, user, fund-transfer, and utility-payment services.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
In build.gradle for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace the incorrect dependency 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0' since these services use Spring MVC (not WebFlux). The API gateway correctly uses WebFlux and should remain unchanged.
```

---

### 1.7 Add Feign Timeout Configuration (Gap 7.3)

**Problem**: No connection or read timeouts configured for Feign clients.

**Remediation**:
- Configure default Feign timeouts in application configuration.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add Feign client timeout configuration to the application.yml (or bootstrap.yml) of internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Set connect timeout to 5000ms and read timeout to 10000ms. Use the spring.cloud.openfeign.client.config.default pattern:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
```

---

### 1.8 Move Credentials to Environment Variables (Gap 4.2)

**Problem**: Database passwords hardcoded in docker-compose files.

**Remediation**:
- Use environment variable references with `.env` file.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Create a docker-compose/.env.example file with placeholder values for all secrets currently hardcoded in docker-compose.yml and docker-compose-support-apps.yml. Then update both docker-compose files to reference environment variables:
- MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
- KC_DB_PASSWORD=${KC_DB_PASSWORD}
- KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
- POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
Add a .gitignore entry for docker-compose/.env and document the setup in the README.
```

---

## Phase 2: Important (3-6 weeks)

Structural improvements that significantly improve reliability and maintainability.

### 2.1 Add Unit Tests to All Services (Gap 3.1)

**Problem**: Only core-banking-service has unit tests. Other services have none.

**Remediation**:
- Add unit tests for service layer classes in all services.
- Target 80% line coverage on service and controller layers.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add comprehensive unit tests for internet-banking-fund-transfer-service:
1. FundTransferServiceTest: test fundTransfer success, Feign client failure handling, readAllTransfers pagination
2. FundTransferControllerTest: use @WebMvcTest to test endpoint routing, request validation, response serialization
Use Mockito for mocking dependencies. Add the same level of testing for internet-banking-utility-payment-service (UtilityPaymentServiceTest, UtilityPaymentControllerTest) and internet-banking-user-service (UserServiceTest, UserControllerTest, KeycloakUserServiceTest).
```

---

### 2.2 Create Shared Common Library (Gap 1.2, 1.4)

**Problem**: Cross-cutting code is duplicated across 4 services.

**Remediation**:
- Create a `common` Gradle module with shared DTOs, exceptions, filters, and audit config.
- Publish as a local dependency or use Gradle composite builds.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Create a shared common module (internet-banking-common) as a new Gradle project:
1. Create a root settings.gradle that includes all services as subprojects
2. Move the following shared code into the common module:
   - AuditAware, AuditConfig, AuditorAwareConfig
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler, GlobalErrorCode
   - BaseMapper interface
3. Have each service depend on the common module: implementation project(':internet-banking-common')
4. Remove the duplicated classes from each service
5. Ensure all services still compile and tests pass
```

---

### 2.3 Add Circuit Breakers (Gap 7.1)

**Problem**: No circuit breakers on Feign calls to core-banking-service.

**Remediation**:
- Add Resilience4j circuit breaker to all Feign clients.
- Configure open/half-open thresholds and fallback methods.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add Resilience4j circuit breaker support to internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
1. Add dependencies: spring-cloud-starter-circuitbreaker-resilience4j
2. Enable circuit breaker for Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for BankingCoreFeignClient and BankingCoreRestClient that return appropriate error responses when core-banking is unavailable
4. Configure circuit breaker parameters: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=5
5. Add the configuration to application.yml under resilience4j.circuitbreaker.instances
```

---

### 2.4 Add Retry Policies (Gap 7.2)

**Problem**: Transient Feign failures immediately fail with no retry.

**Remediation**:
- Configure Feign retry with exponential backoff for transient errors.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add retry configuration to all Feign clients:
1. Add Resilience4j retry dependency if not already present
2. Configure retry for transient failures (5xx responses, connection timeouts): maxAttempts=3, waitDuration=1s, exponentialBackoffMultiplier=2
3. Ensure retries do NOT fire for 4xx client errors (non-retryable)
4. Add retry configuration to application.yml under resilience4j.retry.instances
5. Log each retry attempt with the attempt number
```

---

### 2.5 Add Fund Transfer Failure Handling (Gap 2.5, 2.6)

**Problem**: Failed Feign calls leave records stuck in PENDING/PROCESSING forever.

**Remediation**:
- Wrap Feign calls in try-catch and update status to FAILED on exception.
- Add a scheduled job to detect and alert on stale pending records.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add proper failure handling to FundTransferService and UtilityPaymentService:
1. Wrap the Feign client call in a try-catch block
2. On any exception (FeignException, network errors), update the entity status to FAILED and save it
3. Re-throw a meaningful business exception with the transaction reference for client notification
4. Add a FAILED value to the TransactionStatus enum
5. Add a @Scheduled method that runs every 5 minutes to find records stuck in PENDING/PROCESSING for more than 10 minutes and logs a warning
6. Add @EnableScheduling to the application class
```

---

### 2.6 Add Structured Logging (Gap 6.1, 6.6)

**Problem**: Unstructured logs make aggregation and searching difficult.

**Remediation**:
- Configure JSON log output format.
- Include trace/span IDs in log patterns.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add structured JSON logging to all services:
1. Add dependency: net.logstash.logback:logstash-logback-encoder:7.4
2. Create a shared logback-spring.xml in each service's resources/ with:
   - Console appender with pattern including traceId and spanId: %d{ISO8601} [%thread] [%X{traceId}/%X{spanId}] %-5level %logger - %msg%n
   - A JSON appender (LogstashEncoder) that can be activated with a 'json' profile for production
3. Ensure MDC propagation of traceId/spanId from Micrometer Tracing
```

---

### 2.7 Add Role-Based Access Control (Gap 4.3)

**Problem**: Any authenticated user can perform any operation including admin actions.

**Remediation**:
- Define roles in Keycloak (USER, ADMIN).
- Enforce role-based access at the gateway and service levels.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add role-based access control:
1. In the API gateway SecurityConfiguration, add role-based path matchers:
   - /user/api/v1/bank-users/update/** requires ADMIN role
   - /user/api/v1/bank-users (GET list) requires ADMIN role
   - Fund transfer and utility payment endpoints require USER role
2. Configure JWT role extraction from Keycloak's realm_access.roles claim using a custom JwtGrantedAuthoritiesConverter
3. Update the Keycloak realm export in docker-compose/keycloak/ to include USER and ADMIN roles
4. Document the role mapping in the README
```

---

### 2.8 Add Idempotency Keys (Gap 7.5)

**Problem**: Duplicate requests can create duplicate transactions.

**Remediation**:
- Accept an `X-Idempotency-Key` header on POST endpoints.
- Store and check the key before processing.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add idempotency key support to fund-transfer and utility-payment services:
1. Accept an optional X-Idempotency-Key header in POST endpoints
2. Before processing a request, check if a record with the same idempotency key exists in the database
3. If found, return the existing response (HTTP 200) without reprocessing
4. If not found, proceed with processing and store the key with the record
5. Add an idempotency_key column to fund_transfer and utility_payment tables
6. Add a unique constraint on the idempotency_key column
7. Handle the duplicate key constraint violation gracefully
```

---

### 2.9 Fix Thread Safety in Keycloak Singleton (Gap 4.4)

**Problem**: Double-checked locking without `volatile` or synchronization.

**Remediation**:
- Use proper singleton pattern or Spring bean lifecycle.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Refactor KeycloakProperties in internet-banking-user-service to use a proper thread-safe pattern. Replace the static keycloakInstance field with a @Bean method in a @Configuration class that returns a singleton Keycloak instance managed by Spring's container. This eliminates the thread-safety issue and follows Spring conventions. The Keycloak instance should be created once at application startup using the configured server-url, realm, clientId, and client-secret properties.
```

---

## Phase 3: Polish (6-12 weeks)

Improvements that enhance developer experience and operational maturity.

### 3.1 Add Integration Tests (Gap 3.2)

**Problem**: No tests verify end-to-end HTTP behavior or database interactions.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add integration tests using @SpringBootTest and Testcontainers:
1. Add Testcontainers MySQL dependency to core-banking-service, fund-transfer, utility-payment, and user service
2. Create a base test class that spins up a MySQL container
3. For core-banking-service: test the full HTTP flow of creating accounts, performing fund transfers, and verifying balances
4. For user-service: mock Keycloak with WireMock and test the registration flow end-to-end
5. For fund-transfer and utility-payment: use WireMock to simulate core-banking responses and test the full lifecycle (PENDING -> SUCCESS/FAILED)
6. Add a test application.yml profile that uses Testcontainers-provided connection strings
```

---

### 3.2 Add Contract Tests (Gap 3.3)

**Problem**: No contract tests between Feign consumers and core-banking provider.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add Spring Cloud Contract tests between services:
1. In core-banking-service (provider), add spring-cloud-starter-contract-verifier
2. Define contracts for: GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}
3. In consumer services (fund-transfer, utility-payment, user), add spring-cloud-starter-contract-stub-runner
4. Write consumer-side tests that verify Feign clients work against the contract stubs
5. Configure the contract verification to run as part of the build pipeline
```

---

### 3.3 Add Rate Limiting (Gap 4.6)

**Problem**: No rate limiting on banking API endpoints.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add rate limiting to the API gateway:
1. Add spring-boot-starter-data-redis-reactive dependency
2. Add Redis to docker-compose
3. Configure Spring Cloud Gateway's built-in RequestRateLimiter filter
4. Set limits per user (based on JWT subject): 100 requests/minute for read operations, 10 requests/minute for fund transfers
5. Return HTTP 429 with a Retry-After header when limits are exceeded
6. Add rate limit headers to responses: X-RateLimit-Remaining, X-RateLimit-Limit
```

---

### 3.4 Add Pagination Metadata (Gap 5.4)

**Problem**: List endpoints lose pagination metadata by returning `List<T>`.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Update all list/paginated endpoints to return proper pagination metadata:
1. Create a generic PageResponse<T> wrapper class with fields: content, page, size, totalElements, totalPages
2. Update FundTransferController.readFundTransfers to return ResponseEntity<PageResponse<FundTransfer>>
3. Update UtilityPaymentController.readPayments to return ResponseEntity<PageResponse<UtilityPayment>>
4. Update UserController.readUsers (both services) to return ResponseEntity<PageResponse<User>>
5. Update the corresponding service methods to return Page<T> and map to PageResponse in the controller
```

---

### 3.5 Add Health Check Indicators (Gap 6.3)

**Problem**: Default actuator health checks without service-specific indicators.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add custom health indicators to each service:
1. In core-banking-service: add a DatabaseHealthIndicator that checks MySQL connectivity
2. In user-service: add a KeycloakHealthIndicator that pings the Keycloak server
3. In fund-transfer and utility-payment: add a CoreBankingHealthIndicator that checks if core-banking-service is reachable via Feign
4. Configure management.endpoint.health.show-details=when-authorized in actuator
5. Add management.health.circuitbreakers.enabled=true to expose circuit breaker state in health
```

---

### 3.6 Add Dependency Vulnerability Scanning (Gap 4.8)

**Problem**: No automated vulnerability scanning in the build pipeline.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add OWASP dependency-check to the build:
1. Add the org.owasp.dependencycheck Gradle plugin (version 9.x) to each service's build.gradle
2. Configure it to fail the build on CVSS score >= 7 (HIGH severity)
3. Add a GitHub Actions workflow that runs dependency-check on PRs and main branch
4. Generate HTML and JSON reports in build/reports/dependency-check/
5. Add suppressions file for known false positives if any arise
```

---

### 3.7 Implement Outbox Pattern for Distributed Transactions (Gap 7.6, 7.7)

**Problem**: No reliable mechanism for cross-service data consistency.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Implement the transactional outbox pattern for fund transfers:
1. Add an outbox_event table to the fund-transfer service database with columns: id, aggregate_id, event_type, payload (JSON), status (PENDING/PUBLISHED), created_at
2. When a fund transfer is initiated, save the transfer entity AND an outbox event in the same local transaction
3. Add a scheduled poller (every 5 seconds) that reads PENDING outbox events and publishes them (initially via direct Feign call to core-banking; later can be replaced with RabbitMQ)
4. Mark events as PUBLISHED after successful delivery
5. Add a retry counter and move to DEAD_LETTER after 5 failed attempts
6. This ensures the local state and the remote call are eventually consistent
```

---

### 3.8 Add Bulkhead Isolation (Gap 7.8)

**Problem**: Shared thread pool for all Feign calls risks cascading failures.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Add Resilience4j bulkhead configuration to isolate Feign calls:
1. Add resilience4j-bulkhead dependency to fund-transfer and utility-payment services
2. Configure thread pool bulkhead for core-banking Feign client: maxConcurrent=10, maxWait=500ms
3. Configure separate bulkhead instances for fund-transfer and utility-payment operations
4. Add fallback behavior when bulkhead is full (return HTTP 503 with retry-after header)
5. Expose bulkhead metrics via actuator for monitoring
```

---

### 3.9 Multi-Project Gradle Build (Gap 1.1)

**Problem**: No unified build system across services.

**Devin Prompt**:
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices
Create a root-level multi-project Gradle build:
1. Add a root settings.gradle that includes all 7 service projects (including the new common module)
2. Add a root build.gradle with shared configuration (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common repositories, test configuration)
3. Move shared plugin versions and dependency management to the root build
4. Each service's build.gradle should only declare its specific dependencies
5. Add a root-level ./gradlew build command that builds all services
6. Ensure docker builds still work with the new structure
```

---

## Priority Summary

| Phase | Items | Timeline | Key Outcome |
|-------|-------|----------|-------------|
| Phase 1 | 8 items | 1-2 weeks | Security hardened, errors fixed, basic resilience |
| Phase 2 | 9 items | 3-6 weeks | Reliable, testable, observable system |
| Phase 3 | 9 items | 6-12 weeks | Production-grade, fully resilient architecture |

---

## Dependencies Between Items

```
Phase 1.1 (Error Handling) ──▶ Phase 2.1 (Unit Tests) - tests need stable error contract
Phase 1.2 (Validation) ──▶ Phase 2.1 (Unit Tests) - tests verify validation
Phase 2.2 (Common Library) ──▶ Phase 3.9 (Multi-Project Build) - common module needs root build
Phase 2.3 (Circuit Breakers) ──▶ Phase 3.8 (Bulkhead) - both use Resilience4j
Phase 2.5 (Failure Handling) ──▶ Phase 3.7 (Outbox Pattern) - outbox replaces simple try-catch
```
