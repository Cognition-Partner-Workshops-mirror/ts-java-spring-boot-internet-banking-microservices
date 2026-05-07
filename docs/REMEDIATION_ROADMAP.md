# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort. Each item includes a sample Devin prompt to kick off the remediation.

## Table of Contents

- [Phase 1: Quick Wins (1-2 weeks)](#phase-1-quick-wins-1-2-weeks)
- [Phase 2: Important Improvements (3-6 weeks)](#phase-2-important-improvements-3-6-weeks)
- [Phase 3: Polish & Long-Term (6-12 weeks)](#phase-3-polish--long-term-6-12-weeks)
- [Phase Summary](#phase-summary)

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that are small effort — fix these first to immediately improve reliability and security.

---

### P1-1. Fix HTTP Status Codes in All Exception Handlers

**Gap**: [2.1] All exceptions return HTTP 400 Bad Request  
**Severity**: Critical | **Effort**: Small

**What to do**:
- `EntityNotFoundException` → return `404 Not Found`
- `InsufficientFundsException` → return `422 Unprocessable Entity`
- `InvalidEmailException`, `UserAlreadyRegisteredException`, `InvalidBankingUserException` → return `409 Conflict` or appropriate 4xx
- Generic `Exception` → return `500 Internal Server Error`
- Stop leaking exception details: replace `"Exception occur inside API " + e` with a generic error message

**Devin prompt**:
> Update the GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return correct HTTP status codes: EntityNotFoundException should return 404, InsufficientFundsException should return 422, and the generic Exception handler should return 500 with a safe error message (no stack trace or exception details leaked to the client). All error responses should use the ErrorResponse DTO with code and message fields. Run the existing tests to make sure nothing breaks.

---

### P1-2. Add Bean Validation to All Request DTOs

**Gap**: [2.4] No validation on request bodies  
**Severity**: Critical | **Effort**: Small

**What to do**:
- Add `spring-boot-starter-validation` dependency to all services that accept request bodies
- Add Jakarta Bean Validation annotations to all request DTOs:
  - `FundTransferRequest`: `@NotBlank fromAccount`, `@NotBlank toAccount`, `@NotNull @Positive amount`
  - `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank referenceNumber`, `@NotBlank account`
  - `User` (user-service): `@NotBlank @Email email`, `@NotBlank identification`, `@NotBlank password`
- Add `@Valid` to all `@RequestBody` parameters in controllers
- Add `MethodArgumentNotValidException` handler to `GlobalExceptionHandler`

**Devin prompt**:
> Add Jakarta Bean Validation to all request DTOs across the internet banking microservices. Add spring-boot-starter-validation dependency to core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service build.gradle files. Add @NotNull, @NotBlank, @Positive, and @Email annotations to FundTransferRequest, UtilityPaymentRequest, User (in user-service), and UserUpdateRequest. Add @Valid to all @RequestBody parameters in controllers. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details in the ErrorResponse format.

---

### P1-3. Fix the availableBalance Calculation Bug

**Gap**: [7.6] Part of the transaction atomicity issue — immediate data corruption  
**Severity**: Critical | **Effort**: Small

**What to do**:
In `TransactionService.internalFundTransfer()` and `TransactionService.utilPayment()`, the available balance is set incorrectly:
```java
// BUG: actualBalance is already decremented, then subtracted again
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
Should be:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance()); // same as new actual
```

Same bug exists for credit side and utility payment.

**Devin prompt**:
> Fix the availableBalance calculation bug in core-banking-service TransactionService. In both internalFundTransfer() and utilPayment() methods, the availableBalance is being double-subtracted (or double-added for credits) because it uses the already-updated actualBalance and subtracts the amount again. After updating actualBalance, set availableBalance to match actualBalance (not subtract amount again). Fix both debit and credit sides in internalFundTransfer() and the debit side in utilPayment(). Add unit tests that verify the correct balance after a transfer.

---

### P1-4. Configure Feign Client Timeouts

**Gap**: [7.3] No timeout configuration  
**Severity**: High | **Effort**: Small

**What to do**:
Add timeout configuration to each service's `application.yml` or bootstrap config:
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
```

**Devin prompt**:
> Add Feign client timeout configuration to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Set connectTimeout to 5000ms and readTimeout to 10000ms for all Feign clients. Add this to each service's application.yml under spring.cloud.openfeign.client.config.default. Since these services use Spring Cloud Config Server for their main config, also add fallback timeout values in the local bootstrap or application YAML.

---

### P1-5. Fix Keycloak Singleton Thread Safety

**Gap**: [4.3] Keycloak singleton is not thread-safe  
**Severity**: High | **Effort**: Small

**What to do**:
Replace the manual singleton pattern in `KeycloakProperties` with a Spring `@Bean`:

```java
@Configuration
public class KeycloakConfig {
    @Bean
    public Keycloak keycloakInstance(KeycloakProperties props) {
        return KeycloakBuilder.builder()
            .serverUrl(props.getServerUrl())
            .realm(props.getRealm())
            .grantType("client_credentials")
            .clientId(props.getClientId())
            .clientSecret(props.getClientSecret())
            .build();
    }
}
```

**Devin prompt**:
> Refactor the Keycloak client initialization in internet-banking-user-service to be thread-safe. Currently KeycloakProperties.getInstance() uses a non-thread-safe singleton pattern. Extract the Keycloak instance creation into a @Bean method in a new @Configuration class (KeycloakConfig). Inject the Keycloak bean into KeycloakManager instead of calling getInstance(). Remove the static keycloakInstance field and getInstance() method from KeycloakProperties. Make sure the existing tests still pass.

---

### P1-6. Add Typed ResponseEntity to All Controllers

**Gap**: [5.1] Raw ResponseEntity without generics  
**Severity**: Medium | **Effort**: Small

**What to do**:
Replace all raw `ResponseEntity` return types with `ResponseEntity<T>` using the correct DTO type:
```java
// Before
public ResponseEntity getBankAccount(...)
// After
public ResponseEntity<BankAccount> getBankAccount(...)
```

**Devin prompt**:
> Add generic type parameters to all ResponseEntity return types across all controllers in the internet banking microservices. In core-banking-service AccountController, TransactionController; in internet-banking-fund-transfer-service FundTransferController; and in internet-banking-utility-payment-service UtilityPaymentController — replace raw ResponseEntity with ResponseEntity<SpecificType> using the correct DTO types. This improves type safety and OpenAPI documentation generation.

---

### P1-7. Fix OpenAPI Dependency (WebFlux → WebMVC)

**Gap**: [5.5] OpenAPI documentation misconfigured  
**Severity**: Medium | **Effort**: Small

**What to do**:
In `build.gradle` for core-banking-service, user-service, fund-transfer-service, and utility-payment-service, change:
```groovy
// Wrong: these are MVC services, not WebFlux
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
// Correct:
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
```

**Devin prompt**:
> Fix the OpenAPI/Swagger dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. These are Spring MVC services but they incorrectly use springdoc-openapi-starter-webflux-ui. Change the dependency to springdoc-openapi-starter-webmvc-ui:2.1.0 in each service's build.gradle. Leave the API Gateway as-is since it actually uses WebFlux. Verify each service still compiles.

---

### P1-8. Add Pagination Metadata to List Endpoints

**Gap**: [5.4] No pagination metadata in responses  
**Severity**: Medium | **Effort**: Small

**What to do**:
Return `Page<T>` or a custom `PagedResponse` wrapper instead of `List<T>` from paginated endpoints.

**Devin prompt**:
> Update all paginated list endpoints across the internet banking microservices to return pagination metadata. Currently endpoints like GET /api/v1/bank-users, GET /api/v1/transfer, and GET /api/v1/utility-payment return List<T> and discard the Page metadata. Change the service methods to return Page<T> (or create a custom PagedResponse wrapper with content, page, size, totalElements, totalPages fields) so clients can implement proper pagination. Update the controllers to return the full paged response.

---

### P1-9. Externalize Docker Compose Credentials

**Gap**: [4.1] Hardcoded credentials in source code  
**Severity**: Critical | **Effort**: Small

**What to do**:
- Create a `.env.example` file with placeholder values
- Update `docker-compose.yml` to reference `${VARIABLE}` syntax
- Add `.env` to `.gitignore`
- Document the setup in README

**Devin prompt**:
> Externalize all hardcoded credentials in the docker-compose directory. Create a .env.example file with placeholder values for MYSQL_ROOT_PASSWORD, MYSQL_USER_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD, and KC_DB_PASSWORD. Update docker-compose.yml and docker-compose-support-apps.yml to use ${VARIABLE} references. Update docker-compose/mysql/Dockerfile and privileges.sql to use environment variables. Add .env to .gitignore. Update README.md with instructions to copy .env.example to .env and fill in values before running docker-compose.

---

## Phase 2: Important Improvements (3-6 weeks)

Higher-effort items that significantly improve reliability, maintainability, and operational readiness.

---

### P2-1. Add Circuit Breakers to All Feign Clients

**Gap**: [7.1] No circuit breakers  
**Severity**: Critical | **Effort**: Medium

**What to do**:
- Add `spring-cloud-starter-circuitbreaker-resilience4j` to fund-transfer, utility-payment, and user services
- Configure circuit breaker instances for each Feign client
- Add fallback factories that return meaningful error responses
- Configure sensible thresholds (50% failure rate, 10-call sliding window, 30s open duration)

**Devin prompt**:
> Add Resilience4j circuit breakers to all Feign clients in internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to each service. Create FallbackFactory implementations for each Feign client that return appropriate error responses when the circuit is open. Configure circuit breaker properties in application.yml: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3.

---

### P2-2. Extract Shared Library Module

**Gap**: [1.2] Duplicated code across services  
**Severity**: High | **Effort**: Medium

**What to do**:
- Create a `banking-common` module
- Move shared classes: `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `CustomFeignClientConfiguration`
- Update all services to depend on `banking-common`
- Set up a Gradle multi-module build

**Devin prompt**:
> Create a shared library module called banking-common in the internet banking microservices project. Set up a root-level settings.gradle that includes all existing services plus the new banking-common module. Move the following duplicated classes into banking-common: SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler (as a base class), BaseMapper, AuditAware, AuditConfig, AuditorAwareConfig, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, and CustomFeignClientConfiguration. Update all service build.gradle files to depend on banking-common. Remove the duplicated classes from each service. Make sure all services still compile and tests pass.

---

### P2-3. Add Feign Error Decoders to All Services

**Gap**: [2.3] No Feign error decoder in most services  
**Severity**: High | **Effort**: Medium

**What to do**:
- Create a `CustomFeignErrorDecoder` that maps downstream HTTP errors to domain exceptions
- Register it in `CustomFeignClientConfiguration` for all Feign clients
- Handle 404, 422, 400, and 5xx responses appropriately

**Devin prompt**:
> Add custom Feign error decoders to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. The user-service already has a CustomFeignErrorDecoder — use it as a reference. Create a FeignErrorDecoder that: maps 404 responses to EntityNotFoundException, maps 422 to InsufficientFundsException, maps other 4xx to SimpleBankingGlobalException with the error code from the response body, and maps 5xx to a ServiceUnavailableException. Register the decoder in each service's CustomFeignClientConfiguration. Add unit tests for the error decoder.

---

### P2-4. Add Role-Based Authorization

**Gap**: [4.4] No authorization beyond authentication  
**Severity**: High | **Effort**: Medium

**What to do**:
- Define roles in Keycloak realm: `ADMIN`, `USER`
- Map JWT roles to Spring Security authorities in the API Gateway
- Add path-based role restrictions:
  - `PATCH /user/api/v1/bank-users/update/**` → `ADMIN` only
  - `GET /user/api/v1/bank-users` (list all) → `ADMIN` only
  - Transfer and payment endpoints → `USER` or `ADMIN`
- Add resource ownership validation in service layers (user can only transfer from their own accounts)

**Devin prompt**:
> Add role-based authorization to the internet banking microservices. In the API Gateway SecurityConfiguration, configure path-based access rules: PATCH /user/api/v1/bank-users/update/** should require ADMIN role, GET /user/api/v1/bank-users (list all users) should require ADMIN role, and all transfer/payment endpoints should require USER or ADMIN role. Parse roles from the JWT token's realm_access.roles claim. Update the Keycloak realm export to include ADMIN and USER roles. Document the role assignments in the README.

---

### P2-5. Add Retry Policies for Idempotent Operations

**Gap**: [7.2] No retry policies  
**Severity**: High | **Effort**: Small

**What to do**:
- Add `spring-retry` dependency
- Configure retry for GET operations on Feign clients (read account, read user)
- Explicitly disable retry for POST operations (fund transfer, utility payment) to prevent duplicates
- Configure: max 3 attempts, 1s initial backoff, 2x multiplier

**Devin prompt**:
> Add Spring Retry to the internet banking Feign clients for idempotent GET operations only. Add spring-retry dependency to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Configure Feign retryer with maxAttempts=3, period=1000ms, maxPeriod=5000ms. Ensure that POST requests (fund transfers, utility payments) are NOT retried to prevent duplicate transactions — use a custom Retryer or ErrorDecoder that only retries on specific HTTP status codes (503, 429) for GET methods.

---

### P2-6. Write Unit Tests for All Service Classes

**Gap**: [3.1] Tests only exist in core banking service  
**Severity**: Critical | **Effort**: Large

**What to do**:
- **User Service**: Test `createUser()`, `updateUser()`, `readUsers()`, `readUser()` with mocked Keycloak and Feign clients
- **Fund Transfer Service**: Test `fundTransfer()` and `readAllTransfers()` with mocked Feign client
- **Utility Payment Service**: Test `utilPayment()` and `readPayments()` with mocked Feign client
- Target: Minimum 80% line coverage on service classes

**Devin prompt**:
> Write comprehensive unit tests for all service classes in internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For UserService, test createUser (happy path, duplicate email, user not found in core banking, email mismatch, Keycloak creation failure), updateUser (approve flow, entity not found), readUsers, and readUser. For FundTransferService, test fundTransfer (happy path, core banking failure, status transitions) and readAllTransfers. For UtilityPaymentService, test utilPayment (happy path, core banking failure) and readPayments. Use Mockito to mock all dependencies (repositories, Feign clients, KeycloakUserService). Target 80%+ line coverage.

---

### P2-7. Add Structured Logging and Trace Context

**Gap**: [6.1] Inconsistent logging patterns, [6.4] Distributed tracing not fully wired  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Add `logback-spring.xml` to all services with JSON output format
- Include Micrometer trace ID and span ID in log output via MDC
- Add consistent entry/exit logging to all controller and service methods
- Set `management.tracing.sampling.probability=1.0` for dev profile

**Devin prompt**:
> Add structured JSON logging to all internet banking microservices. Create a shared logback-spring.xml configuration that outputs JSON format using net.logstash.logback:logstash-logback-encoder. Include traceId and spanId fields from Micrometer MDC in every log line. Add the logstash-logback-encoder dependency to all services. Add management.tracing.sampling.probability=1.0 to application.yml for the dev and docker profiles. Ensure Zipkin endpoint is configured as a fallback in local application.yml files.

---

### P2-8. Add Prometheus Metrics and Health Indicators

**Gap**: [6.3] No metrics endpoints or dashboards, [6.2] Health checks not customized  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Add `micrometer-registry-prometheus` dependency
- Expose `/actuator/prometheus` endpoint
- Add custom health indicators for Keycloak, Config Server
- Add custom metrics counters for fund transfers, payments, user registrations
- Add Prometheus and Grafana containers to `docker-compose.yml`

**Devin prompt**:
> Add Prometheus metrics support to all internet banking microservices. Add io.micrometer:micrometer-registry-prometheus dependency to all service build.gradle files. Configure management.endpoints.web.exposure.include to include prometheus,health,info,metrics in each service's application.yml. Add custom Micrometer counters for: fund.transfer.initiated, fund.transfer.completed, fund.transfer.failed, utility.payment.initiated, utility.payment.completed, user.registration.initiated, user.registration.completed. Add a Prometheus container to docker-compose.yml with scrape configs for all services. Optionally add a Grafana container with a basic dashboard.

---

### P2-9. Add Dependency Vulnerability Scanning

**Gap**: [4.5] No dependency vulnerability scanning  
**Severity**: Medium | **Effort**: Small

**What to do**:
- Add the OWASP Dependency Check Gradle plugin
- Configure it to fail on CVSS >= 7.0
- Add a GitHub Actions workflow or CI step

**Devin prompt**:
> Add OWASP Dependency Check to the internet banking microservices. Add the org.owasp.dependencycheck Gradle plugin (version 9.x) to all service build.gradle files. Configure the plugin with failBuildOnCVSS=7.0 to fail the build on high-severity vulnerabilities. Add a Gradle task alias (e.g., ./gradlew dependencyCheckAnalyze) and document it in the README. If a root build.gradle is created, configure the plugin there instead.

---

## Phase 3: Polish & Long-Term (6-12 weeks)

Strategic improvements that require more planning and architectural changes.

---

### P3-1. Implement Saga Pattern for Distributed Transactions

**Gap**: [7.6] No transaction atomicity across services  
**Severity**: Critical | **Effort**: Large

**What to do**:
- Implement the **Choreography-based Saga** or **Orchestration-based Saga** pattern for fund transfers and utility payments
- Option A (simpler): Use the **Transactional Outbox pattern** — write domain events to an outbox table in the same transaction, then relay events via a polling publisher or CDC
- Option B: Use a saga orchestrator service
- Add compensation logic (reverse transfer if downstream fails)
- Add reconciliation job for stuck transactions

**Devin prompt**:
> Implement the Transactional Outbox pattern for fund transfers in the internet banking microservices. In internet-banking-fund-transfer-service, create an outbox_event table. When a fund transfer is initiated, save both the FundTransferEntity and an OutboxEvent in the same database transaction. Create a scheduled @Scheduled method that polls the outbox table, sends the event to core-banking-service via Feign, and marks the event as processed. Add compensation logic: if the core banking call fails after retries, mark the fund transfer as FAILED and create a reversal event. Add a similar pattern for internet-banking-utility-payment-service.

---

### P3-2. Add Integration Tests with Testcontainers

**Gap**: [3.2] No integration tests  
**Severity**: High | **Effort**: Large

**What to do**:
- Add Testcontainers dependency for MySQL
- Write `@SpringBootTest` tests for core-banking-service that test full request → DB → response flow
- Use WireMock for Feign client integration tests in user, fund-transfer, and utility-payment services
- Test Flyway migrations with a real MySQL container

**Devin prompt**:
> Add integration tests using Testcontainers to the internet banking microservices. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies to core-banking-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. For core-banking-service, create @SpringBootTest tests with a MySQL Testcontainer that verify: Flyway migrations run successfully, AccountController returns correct account data, TransactionController processes fund transfers end-to-end. For the other services, add WireMock-based integration tests that stub core-banking-service responses and verify the full request flow.

---

### P3-3. Add Contract Tests Between Services

**Gap**: [3.3] No contract tests  
**Severity**: Medium | **Effort**: Large

**What to do**:
- Add Spring Cloud Contract to core-banking-service (provider)
- Define contract stubs for all endpoints consumed by Feign clients
- Add contract verification tests in consumer services
- Fix the `FundTransferRequest` field mismatch (fund-transfer-service has `authID` field, core-banking-service does not)

**Devin prompt**:
> Add Spring Cloud Contract tests between the internet banking microservices. On the provider side (core-banking-service), add the spring-cloud-contract-verifier plugin and write contract definitions for: GET /api/v1/user/{identification}, GET /api/v1/account/bank-account/{account_number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment. Generate WireMock stubs from the contracts. On the consumer side (internet-banking-fund-transfer-service, internet-banking-utility-payment-service, internet-banking-user-service), add spring-cloud-contract-stub-runner and write tests that verify Feign clients work against the generated stubs. Fix the FundTransferRequest mismatch — the fund-transfer-service version has an authID field that core-banking-service's version does not.

---

### P3-4. Set Up Multi-Module Gradle Build

**Gap**: [1.1] No multi-module Gradle build  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Create root `settings.gradle` including all services
- Create root `build.gradle` with shared configuration
- Use `subprojects` or convention plugins for common dependencies and versions
- Centralize version management with a version catalog (`libs.versions.toml`)

**Devin prompt**:
> Convert the internet banking microservices project into a Gradle multi-module build. Create a root settings.gradle that includes all 7 service modules (core-banking-service, internet-banking-api-gateway, internet-banking-config-server, internet-banking-fund-transfer-service, internet-banking-service-registry, internet-banking-user-service, internet-banking-utility-payment-service). Create a root build.gradle that configures common settings via subprojects{}: Java 21 toolchain, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common test dependencies, and the JUnit platform. Create a gradle/libs.versions.toml version catalog for all dependency versions. Update each service's build.gradle to remove duplicated plugin/version declarations and reference the version catalog.

---

### P3-5. Add Rate Limiting to API Gateway

**Gap**: [7.5] No rate limiting  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Add Redis container to Docker Compose
- Configure Spring Cloud Gateway's `RequestRateLimiter` filter
- Set per-user rate limits (e.g., 100 requests/minute for fund transfers, 1000 requests/minute for reads)

**Devin prompt**:
> Add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's RequestRateLimiter filter with Redis. Add a Redis container to docker-compose.yml. Add spring-boot-starter-data-redis-reactive dependency to the API gateway. Configure rate limiting filters in the gateway routes: 100 requests/minute for POST endpoints (transfers, payments), 1000 requests/minute for GET endpoints. Use the authenticated principal (from JWT sub claim) as the rate limit key. Add a KeyResolver bean that extracts the user identity from the JWT token.

---

### P3-6. Add Fallback Behavior and Transaction Reconciliation

**Gap**: [7.4] No fallback behavior  
**Severity**: Medium | **Effort**: Medium

**What to do**:
- Add fallback methods to circuit breaker-wrapped Feign clients
- Create a `@Scheduled` reconciliation job that:
  - Finds `PENDING` fund transfers older than 5 minutes
  - Queries core-banking-service for the transaction status
  - Updates local status accordingly
- Same for `PROCESSING` utility payments

**Devin prompt**:
> Add a transaction reconciliation job to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Create a @Scheduled method that runs every 5 minutes. For fund transfers, find all records with status PENDING that are older than 5 minutes. For each, attempt to verify the transaction status by calling core-banking-service. If the core banking confirms the transfer completed, update status to SUCCESS. If core banking has no record, update status to FAILED. Add similar reconciliation for utility payments in PROCESSING status. Add logging for each reconciliation action. Make the schedule interval configurable via application.yml.

---

### P3-7. Standardize REST URL Patterns

**Gap**: [5.2] Inconsistent URL patterns  
**Severity**: Low | **Effort**: Small

**What to do**:
- Adopt plural resource names: `/accounts`, `/users`, `/transfers`, `/payments`
- Remove verbs from URLs: `PATCH /users/{id}` instead of `PATCH /users/update/{id}`
- Use consistent `kebab-case` for path segments
- Update API Gateway route mappings accordingly
- Keep old endpoints as deprecated aliases during transition

**Devin prompt**:
> Standardize all REST endpoint URLs across the internet banking microservices to follow consistent RESTful conventions. Use plural resource names: /api/v1/accounts (not /account), /api/v1/users, /api/v1/transfers, /api/v1/payments. Remove verbs from URLs: change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}. Use kebab-case consistently for multi-word path segments. Keep the old endpoints as deprecated aliases (@Deprecated annotation + X-Deprecated response header) for backward compatibility. Update the API Gateway route configuration to match.

---

### P3-8. Implement RabbitMQ Notification Service

**Gap**: [5.2 in Knowledge Base] RabbitMQ mentioned but not implemented  
**Severity**: Low | **Effort**: Large

**What to do**:
- Add RabbitMQ container to Docker Compose
- Add `spring-boot-starter-amqp` to fund-transfer and utility-payment services
- Publish notification events on successful transfers/payments
- Create a new `internet-banking-notification-service` that consumes events and sends notifications (email/SMS stubs)

**Devin prompt**:
> Implement the RabbitMQ notification system for the internet banking microservices. Add a RabbitMQ container to docker-compose.yml (rabbitmq:3-management, ports 5672 and 15672). Add spring-boot-starter-amqp dependency to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. After a successful fund transfer or utility payment, publish a notification event to a RabbitMQ exchange (banking.notifications) with routing keys (fund.transfer.completed, utility.payment.completed). Create a new internet-banking-notification-service that consumes these events, logs them, and has a stub email/SMS sender interface. Register the notification service with Eureka and Config Server.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| **Phase 1** (Quick Wins) | 9 | 4 | 2 | 3 | 0 |
| **Phase 2** (Important) | 9 | 2 | 4 | 3 | 0 |
| **Phase 3** (Polish) | 8 | 1 | 1 | 4 | 2 |
| **Total** | **26** | **7** | **7** | **10** | **2** |

### Recommended Execution Order

```
Week 1:   P1-3 (balance bug fix) → P1-1 (HTTP status codes) → P1-2 (validation)
Week 2:   P1-4 (timeouts) → P1-5 (Keycloak fix) → P1-9 (credentials) → P1-6, P1-7, P1-8
Week 3-4: P2-1 (circuit breakers) → P2-3 (error decoders) → P2-5 (retry)
Week 5-6: P2-2 (shared library) → P2-6 (unit tests)
Week 7-8: P2-4 (RBAC) → P2-7 (logging) → P2-8 (metrics) → P2-9 (vuln scanning)
Week 9+:  P3-1 (saga) → P3-2 (integration tests) → P3-3 (contract tests)
Week 12+: P3-4 (multi-module) → P3-5 (rate limiting) → P3-6 (reconciliation)
          P3-7 (URL patterns) → P3-8 (RabbitMQ notifications)
```

> **Note**: Each Devin prompt above is self-contained and can be executed independently. For best results, execute Phase 1 items sequentially (they build on each other), then Phase 2 items can be partially parallelized. Phase 3 items are independent of each other.
