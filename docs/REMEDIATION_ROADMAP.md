# Internet Banking Microservices — Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins:** High severity / Low effort items that can be addressed immediately
- **Phase 2 — Important:** High severity / Medium effort items that require more planning
- **Phase 3 — Polish:** Lower severity improvements and larger refactoring efforts

---

## Phase 1: Quick Wins

*Target: 1–2 weeks. High-impact, low-effort fixes.*

### 1.1 Fix Balance Calculation Bug (Critical / Small)

**Gap Reference:** 7.6 — `TransactionService.internalFundTransfer()` double-subtracts the amount when setting `availableBalance`. Same bug in `utilPayment()`.

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.

In `internalFundTransfer()`:
- Line `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` 
  should be `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())`
  because actualBalance was already decremented.
- Same fix for `toBankAccountEntity.setAvailableBalance(...)` — should just mirror actualBalance.

In `utilPayment()`:
- Same pattern — availableBalance should equal actualBalance after the debit.

Also update the corresponding test in TransactionServiceTest to verify availableBalance 
equals actualBalance after transfer. Open a PR with the fix.
```

### 1.2 Fix Error Handling — Proper HTTP Status Codes (Critical / Small)

**Gap Reference:** 2.1, 2.2, 2.4 — All exceptions return 400. `EntityNotFoundException` should be 404. Generic errors should be 500. Error response format should be consistent.

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all three services (core-banking-service, 
internet-banking-fund-transfer-service, internet-banking-utility-payment-service):

1. EntityNotFoundException should return HTTP 404 (Not Found)
2. InsufficientFundsException should return HTTP 400 (Bad Request) — this is correct
3. The generic Exception catch-all should return HTTP 500 (Internal Server Error) 
   with a structured ErrorResponse instead of a raw string
4. Do NOT leak exception details to the client in the generic handler — 
   log the full exception server-side and return a generic message
5. Add an @ExceptionHandler for MethodArgumentNotValidException returning 400 
   with validation error details (for future @Valid support)

Ensure all three services have consistent GlobalExceptionHandler implementations.
Open a PR.
```

### 1.3 Add Input Validation to Request DTOs (Critical / Small)

**Gap Reference:** 4.2 — No `@Valid` / Bean Validation annotations on any request DTO.

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. core-banking-service FundTransferRequest: @NotBlank on fromAccount and toAccount, 
   @NotNull @Positive on amount
2. core-banking-service UtilityPaymentRequest: @NotNull on providerId, 
   @NotNull @Positive on amount, @NotBlank on referenceNumber and account
3. internet-banking-fund-transfer-service FundTransferRequest: same as above plus authID
4. internet-banking-utility-payment-service UtilityPaymentRequest: same as core
5. internet-banking-user-service User (registration): @NotBlank @Email on email, 
   @NotBlank on identification and password

Add @Valid annotation to all @RequestBody parameters in controllers.
Add spring-boot-starter-validation dependency to build.gradle files if not present.
Write unit tests to verify validation works. Open a PR.
```

### 1.4 Externalize Hardcoded Credentials (Critical / Small)

**Gap Reference:** 4.1 — Database passwords and Keycloak admin passwords hardcoded in docker-compose.yml.

**Devin Prompt:**
```
Replace all hardcoded credentials in docker-compose/docker-compose.yml and 
docker-compose/docker-compose-support-apps.yml with environment variable references:

1. MYSQL_ROOT_PASSWORD → ${MYSQL_ROOT_PASSWORD}
2. KC_DB_PASSWORD → ${KC_DB_PASSWORD}
3. KC_DB_USERNAME → ${KC_DB_USERNAME}
4. KEYCLOAK_ADMIN → ${KEYCLOAK_ADMIN}
5. KEYCLOAK_ADMIN_PASSWORD → ${KEYCLOAK_ADMIN_PASSWORD}

Create a docker-compose/.env.example file with placeholder values and instructions.
Update docker-compose/mysql/privileges.sql to use a build argument or environment 
variable for the database user password. 
Update README.md with instructions to copy .env.example to .env before running.
Open a PR.
```

### 1.5 Restrict Actuator Endpoints (High / Small)

**Gap Reference:** 4.3 — All actuator endpoints are publicly exposed without authentication.

**Devin Prompt:**
```
Restrict actuator endpoint exposure in the API Gateway SecurityConfiguration:

1. Change the actuator pathMatchers to only allow /actuator/health and /actuator/info 
   without authentication
2. All other actuator endpoints should require authentication
3. In each service's application.yml (or config server configuration), configure:
   management.endpoints.web.exposure.include=health,info,prometheus
   management.endpoint.health.show-details=when-authorized
4. Ensure the gateway routes only proxy health/info, not all actuator paths

Open a PR.
```

### 1.6 Add Feign Timeout Configuration (High / Small)

**Gap Reference:** 7.3 — No connection/read timeouts configured for Feign clients.

**Devin Prompt:**
```
Add Feign client timeout configuration to fund-transfer-service, user-service, 
and utility-payment-service:

1. In each service's application.yml, add:
   spring.cloud.openfeign.client.config.default.connect-timeout=5000
   spring.cloud.openfeign.client.config.default.read-timeout=10000
2. For the core-banking-service specific client, set:
   spring.cloud.openfeign.client.config.core-banking-service.connect-timeout=5000
   spring.cloud.openfeign.client.config.core-banking-service.read-timeout=15000
3. Add comments explaining the timeout values chosen

Open a PR.
```

### 1.7 Add Retry Policies (High / Small)

**Gap Reference:** 7.2 — No retry configuration for transient failures.

**Devin Prompt:**
```
Add Spring Retry support to fund-transfer-service, user-service, and 
utility-payment-service:

1. Add spring-retry and spring-boot-starter-aop dependencies to build.gradle
2. Add @EnableRetry to the application class
3. Configure Feign retry via application.yml:
   spring.cloud.openfeign.client.config.default.retryer=feign.Retryer.Default
4. Set max retries to 3, initial interval 1s, max interval 5s for Feign calls
5. Ensure retries only happen for 5xx responses and connection errors, 
   NOT for 4xx client errors

Open a PR.
```

### 1.8 Fix OpenAPI Dependency (Medium / Small)

**Gap Reference:** 4.6 — Wrong springdoc starter (webflux instead of webmvc).

**Devin Prompt:**
```
In core-banking-service, internet-banking-fund-transfer-service, 
internet-banking-user-service, and internet-banking-utility-payment-service:

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0'

These services use Spring MVC (spring-boot-starter-web), not WebFlux.
Verify Swagger UI is accessible at /swagger-ui/index.html after the change.
Open a PR.
```

### 1.9 Add Pagination Metadata to Responses (High / Small)

**Gap Reference:** 5.1 — List endpoints discard Page metadata.

**Devin Prompt:**
```
Fix paginated endpoints to return proper pagination metadata:

1. Create a shared PageResponse<T> wrapper class with fields: 
   content (List<T>), page (int), size (int), totalElements (long), 
   totalPages (int), last (boolean)
2. Update these endpoints to return PageResponse instead of List:
   - core-banking-service UserController.readUsers()
   - internet-banking-fund-transfer-service FundTransferController.readFundTransfers()
   - internet-banking-utility-payment-service UtilityPaymentController.readPayments()
   - internet-banking-user-service UserController.readUsers()
3. Update service methods to return the Page object properly
4. Update ResponseEntity return types to include generics

Open a PR.
```

### 1.10 Fix Keycloak Singleton Thread Safety (Medium / Small)

**Gap Reference:** 7.7 — Double-checked locking issue in `KeycloakProperties`.

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service 
KeycloakProperties.getInstance():

Replace the manual singleton pattern with a @Bean-based approach:
1. Remove the static keycloakInstance field
2. Create a @Configuration class with a @Bean method that builds and returns 
   the Keycloak instance (Spring manages singleton scope automatically)
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance()
4. Alternatively, make the static field volatile and use synchronized double-checked locking

Open a PR.
```

### 1.11 Add Consistent Logging and Prometheus (Medium / Small)

**Gap Reference:** 6.1, 6.4 — Inconsistent logging, no Prometheus registry.

**Devin Prompt:**
```
Improve observability across all services:

1. Add micrometer-registry-prometheus dependency to all service build.gradle files
2. Add management.endpoints.web.exposure.include=health,info,prometheus 
   to each service configuration
3. Add consistent request logging to all controllers that are missing it 
   (especially UtilityPaymentController)
4. Add MDC filter to include traceId/spanId in log output for correlation
5. Configure a common logback-spring.xml with JSON structured logging format 
   for production profile

Open a PR.
```

---

## Phase 2: Important

*Target: 3–6 weeks. Medium effort items requiring design decisions.*

### 2.1 Add Circuit Breakers with Resilience4j (Critical / Medium)

**Gap Reference:** 7.1 — No circuit-breaker protection on Feign calls.

**Devin Prompt:**
```
Add Resilience4j circuit breaker support to fund-transfer-service, user-service, 
and utility-payment-service:

1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency
2. Enable circuit breaker integration with Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback in fund-transfer-service
   - BankingCoreRestClientFallback in utility-payment-service
   - BankingCoreRestClientFallback in user-service
4. Configure circuit breaker parameters:
   slidingWindowSize=10, failureRateThreshold=50%, 
   waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=5
5. Add circuit breaker health indicator to actuator
6. Write tests verifying fallback behavior

Open a PR.
```

### 2.2 Add Idempotency to Financial Transactions (Critical / Medium)

**Gap Reference:** 7.5 — Duplicate requests create duplicate transfers.

**Devin Prompt:**
```
Add idempotency support to fund transfer and utility payment operations:

1. Add an idempotency-key header (X-Idempotency-Key) requirement for POST operations
2. In fund-transfer-service:
   - Add idempotencyKey column to FundTransferEntity with unique constraint
   - Before processing, check if idempotencyKey exists — if yes, return existing result
3. In utility-payment-service:
   - Same pattern for UtilityPaymentEntity
4. In core-banking-service:
   - Add transactionId uniqueness constraint to prevent duplicate processing
5. Create an IdempotencyFilter or use AOP to handle the check consistently
6. Return HTTP 409 (Conflict) if a duplicate is detected with different parameters
7. Write tests for idempotent behavior

Open a PR.
```

### 2.3 Add Feign Error Handling and Transaction Recovery (Critical / Medium)

**Gap Reference:** 2.5, 7.4 — No error handling on Feign calls, stuck transactions.

**Devin Prompt:**
```
Add proper Feign error handling and transaction recovery:

1. In fund-transfer-service FundTransferService.fundTransfer():
   - Wrap the Feign call in try-catch
   - On FeignException, set entity status to FAILED and save
   - Log the error with full context (from/to account, amount, transactionId)
   
2. In utility-payment-service UtilityPaymentService.utilPayment():
   - Same pattern — catch FeignException, set status to FAILED
   
3. Create a CustomFeignErrorDecoder in both services (similar to user-service) 
   that maps HTTP status codes to appropriate exceptions
   
4. Add a @Scheduled cleanup job to detect and handle stuck transactions 
   (PENDING/PROCESSING for > 5 minutes)

5. Write unit tests for error scenarios

Open a PR.
```

### 2.4 Extract Shared Library (High / Medium)

**Gap Reference:** 1.1 — Duplicated classes across services.

**Devin Prompt:**
```
Extract duplicated code into the banking-common shared library:

1. Move these classes to banking-common:
   - exception/SimpleBankingGlobalException
   - exception/GlobalExceptionHandler
   - exception/ErrorResponse
   - exception/EntityNotFoundException
   - exception/GlobalErrorCode
   - model/dto/AuditAware
   - model/mapper/BaseMapper
   - configuration/filter/AppAuthUserFilter
   - configuration/filter/ApiRequestContext
   - configuration/filter/ApiRequestContextHolder
   - configuration/CustomFeignClientConfiguration
   
2. Add banking-common as a dependency in each service's build.gradle
3. Remove the duplicate classes from each service
4. Ensure all services compile and tests pass after the refactoring

Open a PR.
```

### 2.5 Add Authentication to Downstream Services (High / Medium)

**Gap Reference:** 4.4 — Services behind the gateway have no auth.

**Devin Prompt:**
```
Add security to downstream services to prevent direct access:

1. Add spring-boot-starter-security dependency to core-banking-service, 
   fund-transfer-service, and utility-payment-service
2. Create a SecurityConfiguration in each service that:
   - Validates the X-Auth-Id header is present (reject if missing)
   - Alternatively, configure OAuth2 resource server with JWT validation
   - Allow actuator health/info endpoints without auth
3. Ensure Feign clients propagate the authorization token/header downstream
4. Update FeignClientConfiguration to forward the Authorization header
5. Write integration tests verifying unauthenticated requests are rejected

Open a PR.
```

### 2.6 Add Unit Tests for All Services (High / Large)

**Gap Reference:** 3.1 — Only core-banking has tests.

**Devin Prompt:**
```
Add comprehensive unit tests to services lacking test coverage:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer() success, Feign failure, 
     readAllTransfers() pagination
   - FundTransferControllerTest: MockMvc tests for POST and GET endpoints

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment() success, Feign failure, 
     readPayments() pagination
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

3. internet-banking-user-service:
   - UserServiceTest: test createUser() success, duplicate email, invalid email, 
     user not in core banking, readUsers(), readUser(), updateUser()
   - KeycloakUserServiceTest: mock Keycloak admin client calls
   - UserControllerTest: MockMvc tests for all endpoints

Target: >80% line coverage for service and controller layers.
Open a PR.
```

### 2.7 Add Custom Health Checks (Medium / Small)

**Gap Reference:** 6.2 — No custom health indicators.

**Devin Prompt:**
```
Add custom health indicators to key services:

1. core-banking-service: DatabaseHealthIndicator (verify MySQL is reachable)
2. internet-banking-user-service: KeycloakHealthIndicator (verify Keycloak is reachable)
3. internet-banking-fund-transfer-service: CoreBankingHealthIndicator 
   (verify core-banking-service is reachable via Feign)
4. internet-banking-utility-payment-service: same CoreBankingHealthIndicator
5. internet-banking-api-gateway: custom health check that verifies all downstream 
   services are registered in Eureka

Each health indicator should implement HealthIndicator and return UP/DOWN status.
Open a PR.
```

### 2.8 Add Filtering Parameters to List Endpoints (Medium / Medium)

**Gap Reference:** 5.4 — List endpoints only support pagination.

**Devin Prompt:**
```
Add query parameter filtering to list endpoints:

1. GET /api/v1/transfer — add filters: status, fromAccount, toAccount, 
   dateFrom, dateTo, minAmount, maxAmount
2. GET /api/v1/utility-payment — add filters: status, providerId, account, 
   dateFrom, dateTo
3. GET /api/v1/bank-users — add filters: status, email (partial match)
4. GET /api/v1/user (core-banking) — add filters: email (partial match), 
   firstName, lastName

Use Spring Data JPA Specifications or QueryDSL for dynamic query building.
Add tests for filtering behavior. Open a PR.
```

---

## Phase 3: Polish

*Target: 6–12 weeks. Lower severity and larger architecture improvements.*

### 3.1 Add Integration Tests with Testcontainers (High / Large)

**Gap Reference:** 3.2 — No integration tests.

**Devin Prompt:**
```
Add integration tests using Testcontainers for all services with databases:

1. Add testcontainers and testcontainers-mysql dependencies to each service
2. Create base test configuration with @Testcontainers and MySQL container
3. core-banking-service: end-to-end tests for fund transfer and utility payment 
   flows with real database, verify Flyway migrations work
4. fund-transfer-service: integration test with WireMock for core-banking-service 
   Feign responses
5. utility-payment-service: same WireMock-based integration tests
6. user-service: integration test with WireMock for core-banking and 
   Keycloak mock server

Target: Cover full happy-path and key error scenarios. Open a PR.
```

### 3.2 Add Contract Tests (Medium / Large)

**Gap Reference:** 3.3 — No contract tests between services.

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. core-banking-service (producer):
   - Define contracts for all endpoints consumed by other services
   - Generate contract stubs JAR
   
2. fund-transfer-service (consumer):
   - Add consumer-driven contract tests using generated stubs
   - Verify Feign client calls match contract
   
3. utility-payment-service (consumer):
   - Same pattern as fund-transfer-service
   
4. user-service (consumer):
   - Contract tests for /api/v1/user/{identification} endpoint

Open a PR.
```

### 3.3 Implement CI/CD Pipeline (Medium / Medium)

**Gap Reference:** No CI/CD configured.

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml:
   - Trigger on push and PR to main
   - Matrix build for all services
   - Steps: checkout, setup JDK 21, gradle build, run tests, 
     upload test reports as artifacts
   
2. Create .github/workflows/docker.yml:
   - Trigger on tag push (v*)
   - Build Docker images for all services
   - Push to container registry (configure as repo secret)
   
3. Add build status badges to README.md
4. Add branch protection rules recommendation

Open a PR.
```

### 3.4 Standardize Project Structure (Low / Medium)

**Gap Reference:** 1.2, 1.4 — No multi-module build, inconsistent package structure.

**Devin Prompt:**
```
Create a root-level Gradle multi-module build:

1. Create root settings.gradle including all 7 services as subprojects
2. Create root build.gradle with:
   - Common plugin declarations (Spring Boot, dependency management)
   - Shared dependency versions in ext block
   - Common test configuration
3. Each service's build.gradle should inherit from root and only declare 
   service-specific dependencies
4. Standardize package naming: add service-specific sub-package 
   (e.g., com.javatodev.finance.core, com.javatodev.finance.transfer)
5. Ensure all services still build independently via their own gradlew

Open a PR.
```

### 3.5 Add Flyway Migrations to All Services (Medium / Small)

**Gap Reference:** Only core-banking uses Flyway; others use Hibernate auto-DDL.

**Devin Prompt:**
```
Add Flyway database migrations to the three services currently using auto-DDL:

1. internet-banking-user-service:
   - Add flyway-core and flyway-mysql dependencies
   - Create V1 migration from current UserEntity schema
   - Disable hibernate auto-ddl in application config

2. internet-banking-fund-transfer-service:
   - Same pattern for FundTransferEntity schema

3. internet-banking-utility-payment-service:
   - Same pattern for UtilityPaymentEntity schema

4. Update test configurations to use Flyway with H2 compatibility mode

Open a PR.
```

### 3.6 Add Business Metrics (Medium / Medium)

**Gap Reference:** 6.3 — No custom Micrometer metrics.

**Devin Prompt:**
```
Add custom Micrometer business metrics:

1. core-banking-service:
   - Counter: fund_transfer_total (tags: status=success|failed)
   - Counter: utility_payment_total (tags: status=success|failed)
   - Histogram: fund_transfer_amount
   - Gauge: active_accounts_total

2. fund-transfer-service:
   - Counter: fund_transfer_requests_total
   - Timer: fund_transfer_duration_seconds
   - Counter: feign_call_failures_total

3. utility-payment-service:
   - Same pattern as fund-transfer-service

4. user-service:
   - Counter: user_registrations_total
   - Counter: user_approvals_total

Inject MeterRegistry and register metrics in service classes.
Open a PR.
```

### 3.7 Add DELETE/Cancel Operations (Low / Medium)

**Gap Reference:** 5.5 — Missing DELETE/PUT operations.

**Devin Prompt:**
```
Add cancel/reversal operations for financial transactions:

1. fund-transfer-service:
   - POST /api/v1/transfer/{id}/cancel — cancel a PENDING transfer
   - Business rule: only PENDING transfers can be cancelled

2. utility-payment-service:
   - POST /api/v1/utility-payment/{id}/cancel — cancel a PROCESSING payment

3. user-service:
   - DELETE /api/v1/bank-users/{id} — disable user account 
     (soft delete: set status to DISABLED, disable in Keycloak)

4. Add appropriate tests for each new endpoint

Open a PR.
```

### 3.8 Standardize Resource Naming (Low / Small)

**Gap Reference:** 5.3 — Inconsistent endpoint naming.

**Devin Prompt:**
```
Standardize REST endpoint naming across all services:

1. core-banking-service:
   - /api/v1/account/bank-account/{number} → keep as-is (plural would be /accounts)
   - /api/v1/account/util-account/{name} → rename to /api/v1/utility-accounts/{name}
   
2. Ensure all resources use plural nouns: /transfers, /payments, /users
3. Update API Gateway routing to match any renamed paths
4. Add @Deprecated annotations to old endpoints with redirect guidance
5. Update Postman collection if present

Open a PR.
```

---

## Implementation Priority Matrix

```
                    Low Effort          Medium Effort       Large Effort
                ┌──────────────────┬──────────────────┬──────────────────┐
Critical        │ 1.1 Balance bug  │ 2.1 Circuit      │ 2.3 Feign error  │
                │ 1.2 Error codes  │      breakers    │     handling     │
                │ 1.3 Validation   │ 2.2 Idempotency  │                  │
                │ 1.4 Credentials  │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
High            │ 1.5 Actuator     │ 2.4 Shared lib   │ 2.6 Unit tests   │
                │ 1.6 Timeouts     │ 2.5 Downstream   │ 3.1 Integration  │
                │ 1.7 Retries      │      auth        │      tests       │
                │ 1.9 Pagination   │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
Medium          │ 1.8 OpenAPI fix  │ 2.8 Filtering    │ 3.2 Contract     │
                │ 1.10 Keycloak    │ 3.3 CI/CD        │      tests       │
                │ 1.11 Logging     │ 3.6 Metrics      │                  │
                │ 2.7 Health checks│                  │                  │
                │ 3.5 Flyway       │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
Low             │ 3.8 Naming       │ 3.4 Multi-module │                  │
                │                  │ 3.7 DELETE ops   │                  │
                └──────────────────┴──────────────────┴──────────────────┘
```

## Suggested Execution Order

1. **Week 1:** Items 1.1, 1.2, 1.3, 1.4 (fix critical bugs and security holes)
2. **Week 2:** Items 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11 (remaining quick wins)
3. **Weeks 3–4:** Items 2.1, 2.2, 2.3 (resilience and reliability)
4. **Weeks 5–6:** Items 2.4, 2.5, 2.6 (shared library, security, tests)
5. **Weeks 7–8:** Items 2.7, 2.8, 3.3, 3.5 (observability and CI/CD)
6. **Weeks 9–12:** Items 3.1, 3.2, 3.4, 3.6, 3.7, 3.8 (polish and long-term improvements)
