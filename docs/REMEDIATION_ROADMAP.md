# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to implement the fix.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact fixes that are small in effort. These address critical security and reliability issues.

### 1.1 Fix Error Handling — Proper HTTP Status Codes

**Gaps:** 2.1, 2.2, 2.3, 2.5 | **Severity:** Critical | **Effort:** Small

Refactor all four `GlobalExceptionHandler` classes to:
- Return `404` for `EntityNotFoundException`
- Return `422` for `InsufficientFundsException`
- Return `409` for `UserAlreadyRegisteredException`
- Return `500` with a safe message for generic `Exception` (no stack trace leakage)
- Add `MethodArgumentNotValidException` handler returning `400` with field-level errors
- Ensure all error responses use the `ErrorResponse { code, message }` JSON structure

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, refactor 
the GlobalExceptionHandler class in all four business services (core-banking-service, 
internet-banking-user-service, internet-banking-fund-transfer-service, 
internet-banking-utility-payment-service) to return proper HTTP status codes: 404 for 
EntityNotFoundException, 422 for InsufficientFundsException, 409 for 
UserAlreadyRegisteredException, and 500 (with a safe generic message, no stack trace) 
for unhandled Exception. Add a MethodArgumentNotValidException handler that returns 400 
with field-level validation errors. All error responses must use the ErrorResponse JSON 
structure. Open a PR with the changes."
```

### 1.2 Add Input Validation to All Request DTOs

**Gaps:** 4.2 | **Severity:** Critical | **Effort:** Small

Add Jakarta Bean Validation annotations to all request DTOs:
- `FundTransferRequest`: `@NotBlank fromAccount/toAccount`, `@NotNull @Positive amount`
- `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank account/referenceNumber`
- `User` (registration): `@NotBlank @Email email`, `@NotBlank identification`, `@NotBlank @Size(min=8) password`
- Add `@Valid` to all `@RequestBody` parameters in controllers
- Add `spring-boot-starter-validation` dependency where missing

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta 
Bean Validation annotations to all request DTOs across all services: @NotBlank, @NotNull, 
@Positive, @Email, @Size as appropriate. Add @Valid to all @RequestBody controller 
parameters. Add spring-boot-starter-validation dependency to each service's build.gradle 
if not already present. Open a PR."
```

### 1.3 Stop Logging Sensitive Data

**Gaps:** 4.8 | **Severity:** High | **Effort:** Small

- Remove `request.toString()` from log statements in `UserController.createUser()`
- Replace with safe logging that excludes password fields
- Review all `log.info` statements in controllers for sensitive data exposure
- Exclude `password` field from `User.toString()` (override or use `@ToString.Exclude`)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, audit all 
log statements across all services for sensitive data exposure. In the user-service 
UserController, stop logging the full User object (which contains the password). Add 
@ToString.Exclude to the password field in the User DTO. Review and fix any other log 
statements that expose sensitive financial or personal data. Open a PR."
```

### 1.4 Externalize Hardcoded Credentials

**Gaps:** 4.1 | **Severity:** Critical | **Effort:** Small

- Replace all hardcoded passwords in `docker-compose.yml` and `docker-compose-support-apps.yml` with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD:-changeme}`)
- Add a `.env.example` file documenting required environment variables
- Add `docker-compose/.env` to `.gitignore`
- Remove test credentials from `README.md` or move to a separate non-committed file

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, replace all 
hardcoded credentials in docker-compose/docker-compose.yml and 
docker-compose/docker-compose-support-apps.yml with environment variable references 
(use ${VAR:-default} syntax). Create a .env.example file listing all required variables. 
Add docker-compose/.env to .gitignore. Open a PR."
```

### 1.5 Fix HTTP Status Codes for POST Endpoints

**Gaps:** 5.1 | **Severity:** High | **Effort:** Small

- Change all `POST` endpoints to return `201 Created` with a `Location` header where appropriate
- Change `ResponseEntity.ok()` to `ResponseEntity.status(HttpStatus.CREATED).body(...)` for creation operations

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, update all 
POST endpoints across all services to return HTTP 201 Created instead of 200 OK. Use 
ResponseEntity.status(HttpStatus.CREATED).body(...). This applies to user registration, 
fund transfer initiation, and utility payment processing. Open a PR."
```

### 1.6 Add Feign Timeout Configuration

**Gaps:** 7.3 | **Severity:** High | **Effort:** Small

Add Feign client timeout configuration to each service that uses Feign:
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 5000
```

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add Feign 
client timeout configuration to the application.yml (or via Spring Cloud Config) for 
internet-banking-user-service, internet-banking-fund-transfer-service, and 
internet-banking-utility-payment-service. Set connect-timeout to 3000ms and read-timeout 
to 5000ms. Open a PR."
```

### 1.7 Add Retry Policies for Feign Clients

**Gaps:** 7.2 | **Severity:** High | **Effort:** Small

Add Spring Retry with Resilience4j retry for Feign calls:
- Add `spring-retry` and `resilience4j-spring-boot3` dependencies
- Configure retry for transient failures (5xx, connection errors) with exponential backoff
- Max 3 retries with 1s initial delay

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add retry 
policies for all Feign clients in internet-banking-user-service, 
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Add 
spring-retry dependency and configure Resilience4j retry with max 3 attempts, 1-second 
initial backoff, exponential multiplier of 2, and retry only on 5xx and connection errors. 
Open a PR."
```

### 1.8 Fix Raw ResponseEntity Types

**Gaps:** 5.3 | **Severity:** Medium | **Effort:** Small

Add generic type parameters to all `ResponseEntity` return types in controllers (e.g., `ResponseEntity<BankAccount>` instead of raw `ResponseEntity`).

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add proper 
generic type parameters to all ResponseEntity return types in all controllers across all 
services. For example, change ResponseEntity to ResponseEntity<BankAccount>. This improves 
type safety and OpenAPI documentation generation. Open a PR."
```

### 1.9 Fix OpenAPI Dependency

**Gaps:** 5.4 | **Severity:** Medium | **Effort:** Small

Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in all non-reactive services (core-banking, user, fund-transfer, utility-payment).

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, replace 
the springdoc-openapi-starter-webflux-ui dependency with 
springdoc-openapi-starter-webmvc-ui in core-banking-service, internet-banking-user-service, 
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service (all 
non-reactive services). Open a PR."
```

### 1.10 Fix Keycloak Singleton Thread Safety

**Gaps:** 4.6 | **Severity:** Medium | **Effort:** Small

Refactor `KeycloakProperties.getInstance()` to use a thread-safe initialization pattern (e.g., `synchronized` block, `volatile` field, or Spring `@Bean` lifecycle).

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, fix the 
thread-safety issue in internet-banking-user-service's KeycloakProperties.getInstance() 
method. Replace the non-thread-safe static singleton with a proper Spring @Bean approach 
or use synchronized/volatile for thread-safe lazy initialization. Open a PR."
```

### 1.11 Add Test Coverage Reporting

**Gaps:** 3.4 | **Severity:** Medium | **Effort:** Small

Add the JaCoCo Gradle plugin to all services:
```groovy
plugins {
    id 'jacoco'
}
jacocoTestReport {
    dependsOn test
}
```

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add the 
JaCoCo Gradle plugin to all 7 services' build.gradle files. Configure jacocoTestReport 
to depend on the test task and generate HTML + XML reports. Open a PR."
```

### 1.12 Add Dependency Vulnerability Scanning

**Gaps:** 4.7 | **Severity:** Medium | **Effort:** Small

Add the OWASP Dependency Check plugin to all `build.gradle` files.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add the 
OWASP Dependency Check Gradle plugin (id 'org.owasp.dependencycheck') to all services' 
build.gradle files. Configure it to fail the build on CVSS score >= 7. Open a PR."
```

---

## Phase 2: Important (3-6 weeks)

Structural improvements that require more effort but significantly improve reliability and maintainability.

### 2.1 Create Shared Library Module

**Gaps:** 1.1, 1.2, 1.3 | **Severity:** High | **Effort:** Medium

Create a multi-project Gradle build with a `shared-library` module containing:
- Common DTOs (`ErrorResponse`, `AuditAware`)
- Common exceptions (`SimpleBankingGlobalException`, `EntityNotFoundException`, etc.)
- Common `GlobalExceptionHandler`
- Common Feign configuration
- Common `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
- Common `BaseMapper`

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, create a 
multi-project Gradle build. Add a root settings.gradle that includes all 7 services. 
Create a shared-library module containing the duplicated classes: ErrorResponse, 
SimpleBankingGlobalException, EntityNotFoundException, GlobalExceptionHandler, AuditAware, 
AuditConfig, AuditorAwareConfig, BaseMapper, ApiRequestContext, ApiRequestContextHolder, 
AppAuthUserFilter, and CustomFeignClientConfiguration. Update all services to depend on 
the shared library and remove their local copies. Open a PR."
```

### 2.2 Add Circuit Breakers

**Gaps:** 7.1, 7.4 | **Severity:** Critical | **Effort:** Medium

Add Resilience4j Circuit Breaker to all Feign clients:
- Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency
- Configure circuit breaker per Feign client (failure threshold: 50%, wait duration: 30s)
- Add fallback implementations that return meaningful error responses

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add 
Resilience4j circuit breakers to all Feign clients in internet-banking-user-service, 
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. 
Add spring-cloud-starter-circuitbreaker-resilience4j dependency. Configure circuit 
breakers with 50% failure rate threshold, 30-second wait duration in open state, and 
sliding window of 10 calls. Add fallback classes that return appropriate error responses 
when the circuit is open. Open a PR."
```

### 2.3 Add Unit Tests for All Services

**Gaps:** 3.1 | **Severity:** Critical | **Effort:** Large

Write unit tests for:
- `internet-banking-user-service`: `UserService`, `KeycloakUserService`, `UserController`
- `internet-banking-fund-transfer-service`: `FundTransferService`, `FundTransferController`
- `internet-banking-utility-payment-service`: `UtilityPaymentService`, `UtilityPaymentController`
- `internet-banking-api-gateway`: `SecurityConfiguration`, `GatewayConfiguration`

Target: 80% line coverage on business logic.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, write 
comprehensive unit tests for internet-banking-user-service (UserService, 
KeycloakUserService, UserController), internet-banking-fund-transfer-service 
(FundTransferService, FundTransferController), and 
internet-banking-utility-payment-service (UtilityPaymentService, 
UtilityPaymentController). Use Mockito for mocking dependencies and JUnit 5. Test both 
success paths and error paths. Target 80% line coverage. Open a PR."
```

### 2.4 Secure Internal Service Communication

**Gaps:** 4.4, 4.5 | **Severity:** High | **Effort:** Medium

- Add Spring Security to Core Banking Service requiring a service-to-service token or mutual TLS
- Alternatively, add a shared secret header (`X-Internal-Service-Key`) that Feign clients inject and Core Banking validates
- Validate `X-Auth-Id` header against the JWT token in downstream services (don't blindly trust)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, secure 
internal service communication. Add Spring Security to core-banking-service with a 
filter that validates an X-Internal-Service-Key header on all incoming requests. Configure 
all Feign clients (in user-service, fund-transfer-service, utility-payment-service) to 
inject this header via their Feign configuration. Store the key in Spring Cloud Config. 
Open a PR."
```

### 2.5 Add Integration Tests

**Gaps:** 3.2, 3.5 | **Severity:** High | **Effort:** Large

Add integration tests using Testcontainers and `@SpringBootTest`:
- Test REST controllers end-to-end with MockMvc
- Test JPA repositories with `@DataJpaTest` against H2 or Testcontainers MySQL
- Use test profiles that disable Eureka, Config Server, and Keycloak dependencies
- Fix context load tests to work without external infrastructure

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add 
integration tests for all business services. Use @SpringBootTest with MockMvc to test 
controller endpoints end-to-end. Use @DataJpaTest with H2 for repository tests. Create 
test application.yml profiles that disable Eureka registration, Config Server fetching, 
and Keycloak connectivity. Add Testcontainers dependency for MySQL integration tests. 
Open a PR."
```

### 2.6 Add Custom Health Indicators

**Gaps:** 6.2 | **Severity:** Medium | **Effort:** Small

Add custom `HealthIndicator` beans for:
- Keycloak connectivity (user service)
- Core Banking Service availability (via Feign, in fund-transfer and utility-payment)
- MySQL connection (already handled by Spring Boot auto-config, but verify)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add custom 
Spring Boot HealthIndicator beans: a KeycloakHealthIndicator in user-service that checks 
Keycloak realm accessibility, and CoreBankingHealthIndicator in fund-transfer-service and 
utility-payment-service that checks core-banking-service availability. Register them as 
Spring beans so they appear in /actuator/health. Open a PR."
```

### 2.7 Return Pagination Metadata

**Gaps:** 5.5 | **Severity:** Medium | **Effort:** Small

Change all paginated endpoints to return `Page<T>` or a wrapper DTO with pagination info instead of raw `List<T>`.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, update all 
paginated GET endpoints (in core-banking UserController, user-service UserController, 
fund-transfer FundTransferController, and utility-payment UtilityPaymentController) to 
return Page<T> or a PageResponse wrapper with totalElements, totalPages, page, and size 
instead of raw List<T>. Open a PR."
```

### 2.8 Add Rate Limiting to API Gateway

**Gaps:** 7.5 | **Severity:** Medium | **Effort:** Medium

Configure Spring Cloud Gateway's `RequestRateLimiter` filter with Redis or in-memory rate limiting.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add rate 
limiting to the internet-banking-api-gateway using Spring Cloud Gateway's 
RequestRateLimiter filter. Use an in-memory rate limiter (or add Redis if preferred). 
Configure a default rate of 100 requests per second per client IP. Add Redis to 
docker-compose if needed. Open a PR."
```

### 2.9 Improve Logging and Add Structured Format

**Gaps:** 6.1 | **Severity:** Medium | **Effort:** Medium

- Add structured JSON logging (Logback JSON encoder)
- Add log statements for service outcomes (success/failure with transaction IDs)
- Remove sensitive data from logs
- Ensure consistent log levels (INFO for business events, ERROR for failures, DEBUG for details)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, configure 
structured JSON logging across all services using Logback's JsonEncoder or 
logstash-logback-encoder. Add appropriate log statements to all service methods: INFO for 
business events (transfer initiated, payment completed), ERROR for failures with exception 
details (but not stack traces for expected errors), DEBUG for request/response details. 
Ensure no sensitive data (passwords, full account numbers) is logged. Open a PR."
```

### 2.10 Add Prometheus Metrics

**Gaps:** 6.3 | **Severity:** Medium | **Effort:** Medium

- Add `micrometer-registry-prometheus` dependency to all services
- Expose Prometheus endpoint at `/actuator/prometheus`
- Add custom metrics for business KPIs (transaction count, transfer amounts, error rates)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add 
micrometer-registry-prometheus dependency to all services. Expose the /actuator/prometheus 
endpoint. Add custom Micrometer counters and timers for: fund_transfer_total (with 
status tag), utility_payment_total (with status tag), user_registration_total, and 
transaction_amount_total (as a distribution summary). Open a PR."
```

---

## Phase 3: Polish (6-12 weeks)

Lower-priority improvements that add robustness and operational maturity.

### 3.1 Implement Saga Pattern for Fund Transfers

**Gaps:** 7.6 | **Severity:** Critical | **Effort:** Large

Implement a choreography-based Saga (or orchestration-based with a Saga orchestrator) for the fund transfer flow:
- Use RabbitMQ (already planned) for async communication
- Implement compensating transactions for rollback on failure
- Add idempotency keys to all transaction endpoints
- Implement the Outbox pattern for reliable event publishing

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, implement 
a Saga pattern for fund transfers to ensure transaction atomicity across services. Add 
RabbitMQ dependency and docker-compose configuration. Implement choreography-based saga: 
fund-transfer-service publishes a FundTransferRequested event, core-banking-service 
processes it and publishes FundTransferCompleted or FundTransferFailed, 
fund-transfer-service updates its record accordingly. Add compensating transactions for 
rollback. Add idempotency keys to prevent duplicate processing. Open a PR."
```

### 3.2 Add Contract Tests

**Gaps:** 3.3 | **Severity:** High | **Effort:** Large

Add Spring Cloud Contract tests between:
- Fund Transfer Service (consumer) ↔ Core Banking Service (provider)
- Utility Payment Service (consumer) ↔ Core Banking Service (provider)
- User Service (consumer) ↔ Core Banking Service (provider)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add Spring 
Cloud Contract tests. Define contracts for the Core Banking Service API endpoints consumed 
by fund-transfer-service, utility-payment-service, and user-service. Generate provider 
verification tests in core-banking-service and consumer stubs for the other services. 
Open a PR."
```

### 3.3 Set Up CI/CD Pipeline

**Severity:** Medium | **Effort:** Medium

Create a GitHub Actions workflow that:
- Builds all services
- Runs all tests
- Generates test coverage reports
- Runs OWASP dependency check
- Builds Docker images
- Pushes to a container registry

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, create a 
GitHub Actions CI/CD pipeline (.github/workflows/ci.yml). The pipeline should: build all 
7 services with Gradle, run all tests, generate JaCoCo coverage reports, run OWASP 
dependency check, build Docker images for each service, and upload test reports as 
artifacts. Trigger on push to main and on pull requests. Open a PR."
```

### 3.4 Fix Balance Calculation Bug

**Severity:** High | **Effort:** Small

Fix the double-subtraction bug in `TransactionService`:
```java
// Current (buggy):
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
// Fixed:
fromAccount.setAvailableBalance(fromAccount.getActualBalance());
```

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, fix the 
balance calculation bug in core-banking-service TransactionService. In both 
internalFundTransfer() and utilPayment(), the availableBalance is being double-subtracted 
because it subtracts amount from the already-subtracted actualBalance. After setting 
actualBalance, set availableBalance to match actualBalance (or to the correct intended 
value). Add unit tests to verify correct balance calculations. Open a PR."
```

### 3.5 Standardize Package Structure

**Gaps:** 1.4, 1.5 | **Severity:** Low | **Effort:** Small

- Align all services to a consistent package layout
- Convert mapper instantiation from `new` to Spring `@Component` beans

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, standardize 
the package structure across all services. Use a consistent layout: controller, service, 
model.entity, model.dto, model.mapper, repository, configuration, exception. Move any 
misplaced classes. Also convert all Mapper classes from new-instantiation to Spring 
@Component beans injected via constructor. Open a PR."
```

### 3.6 Add Filtering and Sorting to List Endpoints

**Gaps:** 5.6 | **Severity:** Low | **Effort:** Medium

Add query parameters for filtering and sorting to all paginated list endpoints.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add 
filtering and sorting support to all paginated GET endpoints. For fund transfers: filter 
by status, fromAccount, toAccount, date range. For utility payments: filter by status, 
providerId, date range. For users: filter by status, identification. Support Pageable 
sort parameters. Open a PR."
```

### 3.7 Add Custom Tracing Spans

**Gaps:** 6.4 | **Severity:** Low | **Effort:** Small

Add custom Micrometer observation spans for key business operations with contextual tags.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add custom 
Micrometer observation spans to key business operations: fund transfer processing, utility 
payment processing, user registration, and Keycloak user creation. Add span tags for 
business context (transaction ID, account number prefix, operation status). Open a PR."
```

### 3.8 Add Bulkhead Isolation

**Gaps:** 7.7 | **Severity:** Low | **Effort:** Medium

Configure Resilience4j Bulkhead to isolate Feign client thread pools per downstream service.

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, add 
Resilience4j Bulkhead configuration to isolate Feign client calls per downstream service. 
Configure a thread pool bulkhead with max concurrent calls of 10 and max wait duration 
of 500ms for each Feign client. This prevents a slow Core Banking Service from exhausting 
threads needed for other operations. Open a PR."
```

---

## Summary Timeline

| Phase | Duration | Items | Key Outcomes |
|---|---|---|---|
| **Phase 1** | Weeks 1-2 | 12 items | Critical security holes patched, proper error handling, basic resilience (timeouts + retry), input validation |
| **Phase 2** | Weeks 3-8 | 10 items | Shared library, circuit breakers, comprehensive tests, observability, internal auth |
| **Phase 3** | Weeks 9-16 | 8 items | Saga pattern for consistency, contract tests, CI/CD, operational polish |

### Priority Order Within Each Phase

**Phase 1 (do first → last):**
1. Externalize credentials (1.4) — security compliance
2. Stop logging passwords (1.3) — data leak prevention
3. Fix error handling (1.1) — API reliability
4. Add input validation (1.2) — security
5. Add timeouts (1.6) and retries (1.7) — resilience
6. Fix HTTP status codes (1.5) and ResponseEntity types (1.8)
7. Fix OpenAPI dependency (1.9) and Keycloak singleton (1.10)
8. Add JaCoCo (1.11) and OWASP check (1.12)

**Phase 2 (do first → last):**
1. Circuit breakers (2.2) — prevents cascading failures
2. Shared library (2.1) — reduces maintenance burden
3. Unit tests (2.3) — safety net for all subsequent changes
4. Secure internal APIs (2.4) — defense in depth
5. Integration tests (2.5) — end-to-end confidence
6. Remaining items (2.6-2.10) in any order

**Phase 3 (do first → last):**
1. Fix balance bug (3.4) — correctness issue
2. Saga pattern (3.1) — data consistency
3. Contract tests (3.2) — cross-service safety
4. CI/CD pipeline (3.3) — automation
5. Remaining items (3.5-3.8) in any order
