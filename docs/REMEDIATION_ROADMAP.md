# Remediation Roadmap

Gaps from the [Gap Analysis](./GAP_ANALYSIS.md) are organized into three phases. Each item includes a severity rating, estimated effort, and a ready-to-use Devin prompt.

---

## Phase 1: Quick Wins (Critical & High Severity, Small Effort)

These items address the most impactful issues with the least effort. Target completion: **1-2 weeks**.

### 1.1 Fix Generic Exception Handler — Stop Leaking Stack Traces
**Gap**: 2.1 | **Severity**: Critical | **Effort**: Small

The catch-all `@ExceptionHandler(Exception.class)` returns raw exception details to clients and maps everything to HTTP 400.

**Devin Prompt**:
> In all four services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), update the `GlobalExceptionHandler` class:
> 1. Change the generic `@ExceptionHandler(Exception.class)` to return HTTP 500 with a structured `ErrorResponse` body containing a generic message like "An unexpected error occurred" and an error code like "INTERNAL_SERVER_ERROR". Never return the exception's toString() to the client.
> 2. Add a separate `@ExceptionHandler(EntityNotFoundException.class)` that returns HTTP 404.
> 3. Add `@ExceptionHandler(UserAlreadyRegisteredException.class)` in user-service returning HTTP 409 Conflict.
> 4. Ensure all handlers return `ResponseEntity<ErrorResponse>` (typed).
> 5. Add `@Slf4j` and `log.error("Unhandled exception", e)` in the generic handler so errors are still logged server-side.

---

### 1.2 Add Input Validation to All Request DTOs
**Gap**: 4.3 | **Severity**: Critical | **Effort**: Small-Medium

No Bean Validation annotations exist on any request DTO.

**Devin Prompt**:
> Add Jakarta Bean Validation annotations to all request DTOs across all services:
> 1. Add `spring-boot-starter-validation` to the `build.gradle` of core-banking-service, fund-transfer-service, utility-payment-service, and user-service.
> 2. In core-banking-service `FundTransferRequest`: add `@NotBlank` to `fromAccount` and `toAccount`, `@NotNull @Positive` to `amount`.
> 3. In core-banking-service `UtilityPaymentRequest`: add `@NotNull` to `providerId`, `@NotNull @Positive` to `amount`, `@NotBlank` to `referenceNumber` and `account`.
> 4. In fund-transfer-service `FundTransferRequest`: add `@NotBlank` to `fromAccount` and `toAccount`, `@NotNull @Positive` to `amount`.
> 5. In utility-payment-service `UtilityPaymentRequest`: add `@NotNull` to `providerId`, `@NotNull @Positive` to `amount`, `@NotBlank` to `referenceNumber` and `account`.
> 6. In user-service `User` DTO: add `@NotBlank` to `email`, `@Email` to `email`, `@NotBlank` to `password` and `identification`.
> 7. Add `@Valid` to all `@RequestBody` parameters in all controllers.
> 8. Add a `MethodArgumentNotValidException` handler in each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details.

---

### 1.3 Externalize Hardcoded Credentials
**Gap**: 4.1 | **Severity**: Critical | **Effort**: Small

Passwords are hardcoded in Docker Compose files and Dockerfiles.

**Devin Prompt**:
> Remove all hardcoded credentials from the repository:
> 1. In `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml`, replace all hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`, `${KEYCLOAK_ADMIN_PASSWORD}`, `${POSTGRES_PASSWORD}`).
> 2. Create a `docker-compose/.env.example` file documenting all required environment variables with placeholder values.
> 3. In `docker-compose/mysql/Dockerfile`, remove the `ENV MYSQL_ROOT_PASSWORD` line (it's already set in docker-compose).
> 4. In `docker-compose/mysql/privileges.sql`, use a variable or document that the password should be changed.
> 5. Add `.env` to `.gitignore`.
> 6. Remove the test credentials from README.md and reference the `.env.example` file instead.

---

### 1.4 Stop Logging Sensitive Data
**Gap**: 4.6 | **Severity**: High | **Effort**: Small

Controllers log full request objects which may include passwords and account numbers.

**Devin Prompt**:
> Audit and fix all logging statements across all services to prevent sensitive data exposure:
> 1. In user-service `UserController.createUser()`, change the log to only log the email (not the password): `log.info("Creating user with email {}", request.getEmail())`.
> 2. In fund-transfer `FundTransferController`, log only a summary: `log.info("Fund transfer request from {} to {}", request.getFromAccount(), request.getToAccount())`.
> 3. In utility-payment `UtilityPaymentController`, log only provider and account: `log.info("Utility payment for provider {} from account {}", request.getProviderId(), request.getAccount())`.
> 4. Ensure no `toString()` calls on request objects that may contain sensitive fields.
> 5. Add a `@ToString.Exclude` Lombok annotation to the `password` field in the user-service `User` DTO.

---

### 1.5 Fix Thread-Unsafe Keycloak Singleton
**Gap**: 4.4 | **Severity**: High | **Effort**: Small

`KeycloakProperties.getInstance()` has a race condition.

**Devin Prompt**:
> In internet-banking-user-service, refactor `KeycloakProperties` to be thread-safe:
> 1. Remove the static `keycloakInstance` field and the `getInstance()` method.
> 2. Create a new `@Configuration` class `KeycloakConfig` with a `@Bean` method that creates and returns a `Keycloak` instance using `KeycloakBuilder`.
> 3. Inject the `Keycloak` bean into `KeycloakManager` instead of calling `keycloakProperties.getInstance()`.
> 4. Keep the `@Value` properties in `KeycloakProperties` (rename it to `KeycloakConfigProperties` for clarity) and inject it into the `@Bean` method.

---

### 1.6 Fix the `availableBalance` Double-Deduction Bug
**Gap**: 7.5 | **Severity**: Critical | **Effort**: Small

In `TransactionService.internalFundTransfer()`, `availableBalance` is calculated incorrectly.

**Devin Prompt**:
> In core-banking-service `TransactionService`, fix the balance calculation bug in both `internalFundTransfer()` and `utilPayment()`:
> 1. In `internalFundTransfer()`: Change line `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())` (actualBalance is already debited on the previous line).
> 2. Same fix for `toBankAccountEntity.setAvailableBalance(...)`.
> 3. In `utilPayment()`: Fix `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(...))` — actualBalance is already debited, so availableBalance should match.
> 4. Add `@Version` (optimistic locking) to `BankAccountEntity` to prevent concurrent update race conditions.
> 5. Update the Flyway migration to add a `version` column to `banking_core_account`.
> 6. Update the existing unit tests in `TransactionServiceTest` to verify the corrected balance calculations.

---

### 1.7 Add Feign Error Decoders to All Feign Clients
**Gap**: 2.4 | **Severity**: Medium | **Effort**: Small

Fund-transfer and utility-payment services have no Feign error decoder.

**Devin Prompt**:
> Add a `CustomFeignErrorDecoder` to internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
> 1. Copy the pattern from user-service's `CustomFeignErrorDecoder`.
> 2. Map HTTP 404 from core-banking-service to `EntityNotFoundException`.
> 3. Map HTTP 400 to `SimpleBankingGlobalException` with the error code from the response body.
> 4. Register the decoder in each service's `CustomFeignClientConfiguration`.

---

### 1.8 Fix Broken Context-Load Tests
**Gap**: 3.4 | **Severity**: Medium | **Effort**: Small

Default `*ApplicationTests` fail without infrastructure.

**Devin Prompt**:
> Fix the context-load tests in all services:
> 1. Create `src/test/resources/application.yml` in internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, following the pattern from core-banking-service's test config (H2 in-memory DB, disabled Eureka, disabled config import).
> 2. Add `spring.cloud.config.enabled=false` and `eureka.client.enabled=false` to each test application.yml.
> 3. For the API gateway test, create a test config that disables Eureka and sets a dummy JWK URI.
> 4. For user-service, mock or disable the Keycloak dependency in the test config.
> 5. Verify all context-load tests pass with `./gradlew test` in each service.

---

### 1.9 Add Feign Timeout Configuration
**Gap**: 7.3 | **Severity**: High | **Effort**: Small

No explicit timeouts on Feign clients.

**Devin Prompt**:
> Add timeout configuration to all Feign clients:
> 1. In fund-transfer-service and utility-payment-service, add Feign timeout properties to `application.yml`:
>    ```yaml
>    spring:
>      cloud:
>        openfeign:
>          client:
>            config:
>              default:
>                connect-timeout: 5000
>                read-timeout: 10000
>    ```
> 2. In user-service, add the same configuration plus a longer read-timeout for the Keycloak admin client if applicable.
> 3. Document the timeout values and rationale in a comment in the config file.

---

## Phase 2: Important Improvements (High & Medium Severity, Medium Effort)

These items significantly improve reliability and maintainability. Target completion: **3-6 weeks**.

### 2.1 Add Circuit Breakers to All Feign Clients
**Gap**: 7.1 | **Severity**: Critical | **Effort**: Medium

No circuit breakers exist — a core-banking-service outage will cascade to all dependent services.

**Devin Prompt**:
> Add Resilience4j circuit breakers to all three Feign-consuming services:
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to the `build.gradle` of fund-transfer-service, utility-payment-service, and user-service.
> 2. Enable Feign circuit breaker integration in each service's `application.yml`: `spring.cloud.openfeign.circuitbreaker.enabled=true`.
> 3. Create fallback implementations for each Feign client:
>    - `BankingCoreFeignClientFallback` in fund-transfer-service: return an error response indicating the banking core is temporarily unavailable.
>    - `BankingCoreRestClientFallback` in utility-payment-service: same pattern.
>    - `BankingCoreRestClientFallback` in user-service: same pattern.
> 4. Configure circuit breaker parameters in application.yml (failure-rate-threshold: 50, wait-duration-in-open-state: 30s, sliding-window-size: 10).
> 5. Add retry configuration: max-attempts=3, wait-duration=1s, for transient failures.

---

### 2.2 Extract Shared Library Module
**Gap**: 1.2 | **Severity**: High | **Effort**: Medium

Duplicated code (exceptions, mappers, filters, audit) across services.

**Devin Prompt**:
> Create a shared library module and refactor duplicated code:
> 1. Create a new directory `shared-library/` at the repository root with its own `build.gradle` (Java library, no Spring Boot plugin).
> 2. Move the following classes into `shared-library/src/main/java/com/javatodev/finance/common/`:
>    - `BaseMapper` interface
>    - `AuditAware` base class
>    - `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalErrorCode`
>    - `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`
>    - `CustomFeignClientConfiguration`
>    - `AuditConfig`, `AuditorAwareConfig`
> 3. Publish the shared-library as a local Maven dependency (use `maven-publish` plugin).
> 4. Update each service's `build.gradle` to depend on the shared library.
> 5. Remove the duplicated classes from each service.
> 6. Create a root `settings.gradle` that includes all subprojects for a multi-project Gradle build.

---

### 2.3 Add Unit Tests for All Services
**Gap**: 3.1 | **Severity**: Critical | **Effort**: Large

Only core-banking-service has meaningful tests.

**Devin Prompt**:
> Add comprehensive unit tests for all business services:
> 1. **internet-banking-fund-transfer-service**: Create `FundTransferServiceTest` with tests for:
>    - Successful fund transfer (mock Feign client and repository).
>    - Feign client failure handling.
>    - Paginated read of transfers.
> 2. **internet-banking-utility-payment-service**: Create `UtilityPaymentServiceTest` with tests for:
>    - Successful utility payment.
>    - Feign client failure handling.
>    - Paginated read of payments.
> 3. **internet-banking-user-service**: Create `UserServiceTest` with tests for:
>    - Successful user registration (mock Keycloak and Feign).
>    - Duplicate email rejection.
>    - Email mismatch rejection.
>    - User not found in core banking.
>    - User approval workflow.
>    - Paginated user listing.
> 4. **internet-banking-user-service**: Create `KeycloakUserServiceTest` with tests for CRUD operations (mock KeycloakManager).
> 5. Use Mockito for all external dependencies. Target 80%+ line coverage for service classes.

---

### 2.4 Add Structured Logging
**Gap**: 6.1 | **Severity**: High | **Effort**: Medium

No structured logging or correlation ID propagation.

**Devin Prompt**:
> Implement structured JSON logging across all services:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` to all services' `build.gradle`.
> 2. Create a shared `logback-spring.xml` configuration with JSON output for non-local profiles and console output for local/dev profiles.
> 3. Include traceId and spanId from Micrometer Tracing in the MDC (already available via Brave bridge).
> 4. Add a custom MDC field `userId` populated from the `X-Auth-Id` header in `AppAuthUserFilter`.
> 5. Place the `logback-spring.xml` in each service's `src/main/resources/` (or in the shared library).

---

### 2.5 Add Idempotency Keys to Financial Transactions
**Gap**: 7.6 | **Severity**: High | **Effort**: Medium

Duplicate fund transfers and payments can occur on retries.

**Devin Prompt**:
> Add idempotency key support to fund-transfer and utility-payment services:
> 1. Add an `idempotencyKey` field (String, unique) to both `FundTransferEntity` and `UtilityPaymentEntity`.
> 2. Add an `idempotencyKey` field to `FundTransferRequest` and `UtilityPaymentRequest` DTOs.
> 3. In `FundTransferService.fundTransfer()`, check if a record with the same idempotency key exists before processing. If it does, return the existing result.
> 4. Same logic in `UtilityPaymentService.utilPayment()`.
> 5. Add a unique database constraint on the `idempotency_key` column in both tables.
> 6. Document the idempotency key requirement in the API documentation (clients must send a UUID in the `Idempotency-Key` header or request body).

---

### 2.6 Fix OpenAPI Dependency and Add API Documentation
**Gap**: 5.5, 5.1 | **Severity**: Medium | **Effort**: Small

Wrong springdoc dependency and untyped `ResponseEntity` returns.

**Devin Prompt**:
> Fix the OpenAPI/Swagger setup across all business services:
> 1. In core-banking-service, user-service, fund-transfer-service, and utility-payment-service, change the dependency from `springdoc-openapi-starter-webflux-ui` to `springdoc-openapi-starter-webmvc-ui:2.1.0`.
> 2. Add generic type parameters to all controller `ResponseEntity` return types (e.g., `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`).
> 3. Add `@ApiResponse` annotations to document success and error response codes on each endpoint.
> 4. Verify that Swagger UI loads at `/swagger-ui.html` on each service.

---

### 2.7 Return Pagination Metadata
**Gap**: 5.4 | **Severity**: Medium | **Effort**: Small

Paginated endpoints lose page metadata.

**Devin Prompt**:
> Update all paginated endpoints to return full pagination metadata:
> 1. Change the return type of paginated methods from `List<T>` to Spring's `Page<T>` (which serializes to `{ content: [...], totalElements, totalPages, number, size, ... }`).
> 2. In core-banking-service `UserController.readUsers()`, `UserService.readUsers()`: return `Page<User>` instead of `List<User>`.
> 3. In fund-transfer-service `FundTransferController.readFundTransfers()`, `FundTransferService.readAllTransfers()`: return `Page<FundTransfer>`.
> 4. In utility-payment-service `UtilityPaymentController.readPayments()`, `UtilityPaymentService.readPayments()`: return `Page<UtilityPayment>`.
> 5. In user-service `UserController.readUsers()`: return `Page<User>`.

---

### 2.8 Configure Actuator Endpoints and Health Checks
**Gap**: 6.2, 6.3 | **Severity**: Medium | **Effort**: Small

Actuator and Prometheus not properly configured.

**Devin Prompt**:
> Configure Spring Boot Actuator and add Prometheus metrics across all services:
> 1. Add `io.micrometer:micrometer-registry-prometheus` to the `build.gradle` of all services.
> 2. In each service's `application.yml` (or via config server), add:
>    ```yaml
>    management:
>      endpoints:
>        web:
>          exposure:
>            include: health,info,metrics,prometheus
>      endpoint:
>        health:
>          show-details: when-authorized
>      info:
>        git:
>          mode: full
>    ```
> 3. Add custom health indicators for database connectivity in each data-backed service.
> 4. Verify `/actuator/health`, `/actuator/info`, and `/actuator/prometheus` return expected data.

---

## Phase 3: Polish & Hardening (Medium & Low Severity, Larger Effort)

These items bring the codebase to production-grade quality. Target completion: **2-3 months**.

### 3.1 Implement Saga Pattern for Distributed Transactions
**Gap**: 7.7 | **Severity**: High | **Effort**: Large

Fund transfers are not atomic across services.

**Devin Prompt**:
> Implement a choreography-based saga pattern for fund transfers:
> 1. Add `spring-boot-starter-amqp` (RabbitMQ) to fund-transfer-service and core-banking-service.
> 2. Define saga states: `INITIATED → DEBITED → CREDITED → COMPLETED` (or `FAILED` at any step).
> 3. In fund-transfer-service: publish a `FundTransferInitiated` event to RabbitMQ after saving the PENDING record.
> 4. In core-banking-service: consume the event, perform the debit, publish `AccountDebited` (or `DebitFailed`).
> 5. In core-banking-service: after debit, perform the credit, publish `TransferCompleted` (or `CreditFailed` with compensation).
> 6. In fund-transfer-service: consume the completion/failure events and update the local record status.
> 7. Add compensation logic: if credit fails, reverse the debit.
> 8. Add the RabbitMQ container to docker-compose if not already present.

---

### 3.2 Add Integration Tests with Testcontainers
**Gap**: 3.2 | **Severity**: High | **Effort**: Large

No integration tests exist.

**Devin Prompt**:
> Add integration tests using Testcontainers for all data-backed services:
> 1. Add `org.testcontainers:mysql:1.19.7` and `org.testcontainers:junit-jupiter:1.19.7` to the test dependencies of core-banking-service, user-service, fund-transfer-service, and utility-payment-service.
> 2. Create an abstract `BaseIntegrationTest` class that starts a MySQL Testcontainer and configures the datasource.
> 3. For core-banking-service: write `@SpringBootTest` tests for `AccountController` and `TransactionController` that test the full HTTP → service → repository → DB flow.
> 4. For user-service: add a Keycloak Testcontainer (`dasniko/testcontainers-keycloak`) for testing the registration and approval workflows end-to-end.
> 5. For fund-transfer-service and utility-payment-service: use WireMock to mock core-banking-service responses and test the Feign integration.
> 6. Target: at least 3 integration tests per service covering happy path, error handling, and edge cases.

---

### 3.3 Add Contract Tests for Feign Clients
**Gap**: 3.3 | **Severity**: Medium | **Effort**: Large

No consumer-driven contract tests.

**Devin Prompt**:
> Implement Spring Cloud Contract tests between services:
> 1. Add `spring-cloud-starter-contract-verifier` to core-banking-service (provider).
> 2. Add `spring-cloud-starter-contract-stub-runner` to fund-transfer-service, utility-payment-service, and user-service (consumers).
> 3. Write contracts in core-banking-service for each API endpoint consumed by other services:
>    - `GET /api/v1/account/bank-account/{account_number}` (used by fund-transfer and utility-payment).
>    - `POST /api/v1/transaction/fund-transfer` (used by fund-transfer).
>    - `POST /api/v1/transaction/util-payment` (used by utility-payment).
>    - `GET /api/v1/user/{identification}` (used by user-service).
> 4. Generate stubs and publish them as test dependencies.
> 5. Write consumer-side tests that verify the Feign clients work against the stubs.

---

### 3.4 Add Rate Limiting at the API Gateway
**Gap**: 4.5 | **Severity**: Medium | **Effort**: Medium

No rate limiting on any endpoint.

**Devin Prompt**:
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter:
> 1. Add `spring-boot-starter-data-redis-reactive` to the API gateway's `build.gradle`.
> 2. Add a Redis container to `docker-compose.yml`.
> 3. Configure rate limiting in the gateway's route definitions:
>    - Registration endpoint (`/user/api/v1/bank-users/register`): 5 requests per minute per IP.
>    - Fund transfer endpoint: 10 requests per minute per user.
>    - General API: 60 requests per minute per user.
> 4. Use `RedisRateLimiter` with token bucket algorithm.
> 5. Return HTTP 429 Too Many Requests with a `Retry-After` header when rate limit is exceeded.

---

### 3.5 Add Dependency Vulnerability Scanning
**Gap**: 4.7 | **Severity**: Medium | **Effort**: Small

No security scanning for dependencies.

**Devin Prompt**:
> Set up automated dependency vulnerability scanning:
> 1. Add the OWASP Dependency-Check Gradle plugin (`org.owasp.dependencycheck`) to each service's `build.gradle`.
> 2. Configure it to fail the build on CVSS score >= 7 (High severity).
> 3. Create a GitHub Actions workflow (`.github/workflows/dependency-check.yml`) that runs the check on every PR and weekly on the main branch.
> 4. Add a Dependabot configuration (`.github/dependabot.yml`) to automatically create PRs for dependency updates.

---

### 3.6 Create a CI/CD Pipeline
**Gap**: 6.4 (general) | **Severity**: Medium | **Effort**: Medium

No CI/CD pipeline exists.

**Devin Prompt**:
> Create a GitHub Actions CI/CD pipeline:
> 1. Create `.github/workflows/ci.yml` with jobs for:
>    - `build`: Run `./gradlew build` for each service in parallel.
>    - `test`: Run `./gradlew test` for each service.
>    - `docker`: Build Docker images for each service (create Dockerfiles first).
> 2. Create a `Dockerfile` for each service using a multi-stage build:
>    - Stage 1: Gradle build with `eclipse-temurin:21-jdk` base.
>    - Stage 2: Runtime with `eclipse-temurin:21-jre` base, copy the JAR.
> 3. Create `.github/workflows/release.yml` for tagged releases that builds and pushes images to a container registry.
> 4. Include the OWASP dependency check and test coverage reporting in the CI pipeline.

---

### 3.7 Normalize Project Structure to Multi-Project Gradle Build
**Gap**: 1.1 | **Severity**: Medium | **Effort**: Medium

No unified build.

**Devin Prompt**:
> Convert the repository to a Gradle multi-project build:
> 1. Create a root `settings.gradle` that includes all 7 services and the shared-library as subprojects.
> 2. Create a root `build.gradle` with a `subprojects` block that configures shared properties (Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common test dependencies).
> 3. Move each service's `build.gradle` to only contain service-specific dependencies.
> 4. Add a root `gradle/` directory with the Gradle wrapper (shared across all services).
> 5. Remove the individual `gradle/` directories and `gradlew` scripts from each service.
> 6. Verify that `./gradlew build` from the root builds all services.

---

### 3.8 Add Kubernetes Deployment Manifests
**Gap**: README claims Kubernetes support | **Severity**: Low | **Effort**: Large

Kubernetes is listed in the tech stack but no manifests exist.

**Devin Prompt**:
> Create Kubernetes deployment manifests for all services:
> 1. Create a `k8s/` directory at the repository root.
> 2. For each service, create:
>    - `deployment.yaml` with resource limits, readiness/liveness probes, and environment variables.
>    - `service.yaml` with ClusterIP type.
> 3. Create `configmap.yaml` for shared configuration.
> 4. Create `secret.yaml` templates for database credentials and Keycloak secrets.
> 5. Create an `ingress.yaml` for the API gateway.
> 6. Create a `kustomization.yaml` for easy deployment.
> 7. Add a `k8s/README.md` with deployment instructions.

---

## Summary Timeline

| Phase | Items | Timeline | Key Outcomes |
|-------|-------|----------|-------------|
| **Phase 1** | 9 items | Weeks 1-2 | Security vulnerabilities fixed, data integrity bugs resolved, basic error handling standardized |
| **Phase 2** | 8 items | Weeks 3-8 | Resilience patterns in place, test coverage meaningful, observability operational, API quality improved |
| **Phase 3** | 8 items | Weeks 9-16 | Production-grade CI/CD, distributed transaction safety, contract testing, Kubernetes readiness |

## Priority Matrix

```
                    Low Effort          Medium Effort         Large Effort
                ┌──────────────────┬──────────────────┬──────────────────┐
  Critical      │ 1.1 Exception    │ 2.1 Circuit      │ 2.3 Unit tests   │
                │ 1.2 Validation   │   breakers       │                  │
                │ 1.3 Credentials  │                  │                  │
                │ 1.6 Balance bug  │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
  High          │ 1.4 Log masking  │ 2.2 Shared lib   │ 3.1 Saga pattern │
                │ 1.5 Keycloak     │ 2.4 Structured   │ 3.2 Integration  │
                │ 1.9 Timeouts     │   logging        │   tests          │
                │                  │ 2.5 Idempotency  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
  Medium        │ 1.7 Feign decode │ 2.8 Actuator     │ 3.3 Contract     │
                │ 1.8 Fix tests    │ 3.4 Rate limiting│   tests          │
                │ 2.6 OpenAPI fix  │ 3.6 CI/CD        │                  │
                │ 2.7 Pagination   │ 3.7 Multi-project│                  │
                │ 3.5 Dep scanning │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
  Low           │                  │                  │ 3.8 Kubernetes   │
                │                  │                  │                  │
                └──────────────────┴──────────────────┴──────────────────┘
```
