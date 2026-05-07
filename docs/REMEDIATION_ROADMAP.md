# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, effort, and dependency ordering.

---

## Phase 1: Quick Wins (Critical fixes, Small effort)

These items address critical or high-severity gaps that can be resolved quickly. Prioritized by risk to data integrity and security.

### 1.1 Fix Balance Calculation Bug
**Gap**: 7.7 | **Severity**: Critical | **Effort**: Small

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `utilPayment()`.

**Sample Devin Prompt**:
> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the balance calculation bug. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is being set to `actualBalance.subtract(amount)` AFTER `actualBalance` was already reduced. The `availableBalance` should be set equal to the new `actualBalance` value (or calculated independently). Fix this and update the existing unit tests in `TransactionServiceTest.java` to verify correct balance values after transfers and payments.

---

### 1.2 Add Input Validation to All Request DTOs
**Gap**: 4.1 | **Severity**: Critical | **Effort**: Small

**Sample Devin Prompt**:
> Add Bean Validation annotations to all request DTOs across all services. Specifically: (1) In `FundTransferRequest`: add `@NotBlank` to `fromAccount` and `toAccount`, `@NotNull @Positive` to `amount`. (2) In `UtilityPaymentRequest` (both core-banking and utility-payment-service versions): add `@NotNull` to `providerId`, `@NotNull @Positive` to `amount`, `@NotBlank` to `referenceNumber` and `account`. (3) In `User` DTO in user-service: add `@NotBlank @Email` to `email`, `@NotBlank` to `identification` and `password`. (4) Add `@Valid` annotation to all `@RequestBody` parameters in controllers. (5) Add `spring-boot-starter-validation` dependency to each service's `build.gradle` if not already present.

---

### 1.3 Fix Error Response Consistency and HTTP Status Codes
**Gap**: 2.1, 2.2, 2.4 | **Severity**: Critical/High | **Effort**: Small

**Sample Devin Prompt**:
> Refactor the `GlobalExceptionHandler` in all 4 services (core-banking, user, fund-transfer, utility-payment) to: (1) Return structured `ErrorResponse` objects for ALL exceptions (never raw strings). (2) Use correct HTTP status codes: 404 for `EntityNotFoundException`, 422 for `InsufficientFundsException` and business rule violations, 400 for validation errors, 500 for unexpected exceptions. (3) Remove the `toString()` of the exception from the catch-all handler to prevent information leakage - log the full exception server-side with `log.error()` but return only a generic message to the client. (4) Add `@ExceptionHandler(MethodArgumentNotValidException.class)` to handle Bean Validation errors with field-level detail.

---

### 1.4 Stop Exception Information Leakage
**Gap**: 2.4 | **Severity**: Critical | **Effort**: Small

(Covered by 1.3 above - the catch-all handler fix.)

---

### 1.5 Add Feign Timeout Configuration
**Gap**: 7.3 | **Severity**: High | **Effort**: Small

**Sample Devin Prompt**:
> Add Feign client timeout configuration for `fund-transfer-service` and `utility-payment-service`. In each service's `application.yml` (or via the Spring Cloud Config repo), add: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add these to the `core-banking-service` Feign client config in `CustomFeignClientConfiguration`: `connectTimeout = 5000` and `readTimeout = 10000`.

---

### 1.6 Add Feign Retry Policies
**Gap**: 7.2 | **Severity**: High | **Effort**: Small

**Sample Devin Prompt**:
> Add retry configuration for Feign clients in `fund-transfer-service` and `utility-payment-service`. Add a `Retryer` bean to each `CustomFeignClientConfiguration` that retries up to 3 times with 1-second initial interval and 5-second max interval: `@Bean public Retryer retryer() { return new Retryer.Default(1000, 5000, 3); }`. Only retry on GET requests (reads) - do not retry POST requests (transfers/payments) to avoid duplicate transactions.

---

### 1.7 Externalize Docker Compose Secrets
**Gap**: 4.2 | **Severity**: High | **Effort**: Small

**Sample Devin Prompt**:
> Refactor `docker-compose/docker-compose.yml` and `docker-compose-support-apps.yml` to use environment variables instead of hardcoded credentials. Replace `MYSQL_ROOT_PASSWORD: woVERANKliGharym` with `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`, `KEYCLOAK_ADMIN_PASSWORD: password` with `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}`, and similarly for `KC_DB_PASSWORD` and `POSTGRES_PASSWORD`. Create a `.env.example` file with placeholder values and add `.env` to `.gitignore`. Also update the MySQL Dockerfile to remove the hardcoded `ENV MYSQL_ROOT_PASSWORD`.

---

### 1.8 Fix OpenAPI Dependency (WebFlux vs Servlet)
**Gap**: 5.2 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> In `core-banking-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, `internet-banking-user-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` since these services use Spring MVC (spring-boot-starter-web), not WebFlux. The WebFlux variant is only appropriate for the API Gateway.

---

### 1.9 Fix Raw ResponseEntity Types
**Gap**: 2.3 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Add generic type parameters to all `ResponseEntity` return types across all controllers. For example, in `AccountController`: change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do the same for all controllers in core-banking-service, user-service, fund-transfer-service, and utility-payment-service. This enables proper OpenAPI schema generation and compile-time type safety.

---

### 1.10 Return Pagination Metadata
**Gap**: 5.3 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Modify all list endpoints to return pagination metadata instead of raw lists. In `core-banking-service UserController.readUsers()`, `fund-transfer-service FundTransferController.readFundTransfers()`, and `utility-payment-service UtilityPaymentController.readPayments()`: return the `Page<T>` object directly (or a DTO wrapper with `content`, `totalElements`, `totalPages`, `pageNumber`, `pageSize` fields) instead of extracting `.getContent()`. Update the corresponding service methods to return `Page<DTO>` instead of `List<DTO>`.

---

### 1.11 Fix ApplicationTests to Not Require Infrastructure
**Gap**: 3.4 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Fix the `*ApplicationTests` classes across all services so they don't fail when external infrastructure (Eureka, Config Server, MySQL, Keycloak) is unavailable. For each test class: (1) Add `@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.cloud.config.enabled=false"})`. (2) For services with databases, ensure the H2 test dependency and a `src/test/resources/application.yml` with `spring.datasource.url: jdbc:h2:mem:testdb` is configured. (3) For user-service, mock the Keycloak beans with `@MockBean`.

---

## Phase 2: Important (High-impact, Medium effort)

These items address structural and resilience concerns that require more substantial changes.

### 2.1 Add Circuit Breakers
**Gap**: 7.1 | **Severity**: Critical | **Effort**: Medium

**Sample Devin Prompt**:
> Add Resilience4j circuit breaker support to `fund-transfer-service` and `utility-payment-service`. (1) Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to each `build.gradle`. (2) Enable Feign circuit breaker integration by setting `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's config. (3) Create fallback classes for `BankingCoreFeignClient` in each service that return a meaningful error response (e.g., set the local entity status to FAILED and return an error message). (4) Add the fallback to the `@FeignClient` annotation: `@FeignClient(name = "core-banking-service", fallback = BankingCoreFallback.class)`. (5) Configure circuit breaker thresholds in application config.

---

### 2.2 Add Idempotency Protection for Financial Operations
**Gap**: 7.5 | **Severity**: Critical | **Effort**: Medium

**Sample Devin Prompt**:
> Add idempotency key support to `POST /api/v1/transfer` and `POST /api/v1/utility-payment`. (1) Add an `X-Idempotency-Key` header parameter to both POST controllers. (2) Create an `idempotency_key` column (unique index) on `fund_transfer` and `utility_payment` tables. (3) Before processing, check if a record with the same idempotency key exists - if so, return the existing result. (4) If processing, save the idempotency key with the initial entity. This prevents duplicate transactions on client retries.

---

### 2.3 Add Feign Error Handling to Fund Transfer and Utility Payment Services
**Gap**: 2.5 | **Severity**: Critical | **Effort**: Medium

**Sample Devin Prompt**:
> Add proper Feign error handling to `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`. (1) Wrap the Feign call in a try-catch block. (2) On failure, update the local entity status to `FAILED` and save it. (3) Port the `CustomFeignErrorDecoder` from user-service to fund-transfer-service and utility-payment-service. (4) Make sure the utility-payment-service's empty `CustomFeignClientConfiguration` actually registers an error decoder bean. (5) Re-throw a meaningful exception after updating the entity status.

---

### 2.4 Extract Shared Library
**Gap**: 1.2 | **Severity**: High | **Effort**: Medium

**Sample Devin Prompt**:
> Create a shared library module `banking-common` at the root of the project. Move the following duplicated classes into it: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `CustomFeignClientConfiguration`, `AuditConfig`, `AuditorAwareConfig`. Update all services' `build.gradle` to depend on `implementation project(':banking-common')`. Convert the project to a Gradle multi-project build with a root `settings.gradle` that includes all services and the common module.

---

### 2.5 Set Up Gradle Multi-Project Build
**Gap**: 1.1 | **Severity**: Medium | **Effort**: Medium

(Covered by 2.4 above - extracting the shared library naturally requires converting to a multi-project build.)

---

### 2.6 Add Unit Tests for All Services
**Gap**: 3.1 | **Severity**: Critical | **Effort**: Large

**Sample Devin Prompt**:
> Add unit tests for `internet-banking-user-service`, targeting the `UserService` class. Test the following scenarios: (1) `createUser` happy path: Keycloak returns no existing user, core-banking returns valid user, Keycloak user creation returns 201 - verify entity is saved with PENDING status. (2) `createUser` with already registered email - verify `UserAlreadyRegisteredException`. (3) `createUser` with email mismatch - verify `InvalidEmailException`. (4) `createUser` with unknown identification - verify `InvalidBankingUserException`. (5) `readUsers` with pagination. (6) `readUser` found and not found. (7) `updateUser` with APPROVED status - verify Keycloak user is enabled. Mock `KeycloakUserService`, `UserRepository`, and `BankingCoreRestClient` with Mockito.

**Sample Devin Prompt** (Fund Transfer Service):
> Add unit tests for `internet-banking-fund-transfer-service`. Create `FundTransferServiceTest` testing: (1) `fundTransfer` happy path - verify entity saved with PENDING then updated to SUCCESS. (2) `fundTransfer` when Feign call fails - verify error handling. (3) `readAllTransfers` pagination. Mock `FundTransferRepository` and `BankingCoreFeignClient`.

**Sample Devin Prompt** (Utility Payment Service):
> Add unit tests for `internet-banking-utility-payment-service`. Create `UtilityPaymentServiceTest` testing: (1) `utilPayment` happy path - verify entity saved with PROCESSING then updated to SUCCESS. (2) `utilPayment` when Feign call fails. (3) `readPayments` pagination. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`.

---

### 2.7 Add Integration Tests
**Gap**: 3.2 | **Severity**: High | **Effort**: Large

**Sample Devin Prompt**:
> Add integration tests for `core-banking-service` using `@SpringBootTest` with H2 in-memory database. Create `AccountControllerIntegrationTest` using `@AutoConfigureMockMvc`: (1) Test `GET /api/v1/account/bank-account/{number}` returns 200 with valid account. (2) Test 404 for non-existent account. Create `TransactionControllerIntegrationTest`: (1) Test fund transfer returns 200 and updates balances. (2) Test insufficient funds returns 422. Use Flyway migrations to set up test data. Disable Eureka and Config Server in test profile.

---

### 2.8 Add Custom Health Indicators
**Gap**: 6.2 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Add custom health indicators for each service. (1) In `core-banking-service`: add a `DatabaseHealthIndicator` that verifies MySQL connectivity. (2) In `user-service`: add a `KeycloakHealthIndicator` that calls Keycloak's health endpoint. (3) In `fund-transfer-service` and `utility-payment-service`: add a health indicator that verifies `core-banking-service` is reachable via the Feign client. Enable detailed health info in actuator: `management.endpoint.health.show-details=always`.

---

### 2.9 Add Prometheus Metrics Support
**Gap**: 6.4 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Add Prometheus metrics support to all services. (1) Add `io.micrometer:micrometer-registry-prometheus` to each service's `build.gradle`. (2) Expose the Prometheus endpoint: add `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to each service's configuration. (3) Verify the `/actuator/prometheus` endpoint returns metrics in Prometheus format.

---

### 2.10 Fix Keycloak Singleton Thread Safety
**Gap**: 4.4 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the manual static singleton with a Spring `@Bean` method. Create a `KeycloakConfig` configuration class that produces a `@Bean Keycloak keycloak()` using `KeycloakBuilder`. Remove the static `keycloakInstance` field and `getInstance()` method from `KeycloakProperties`. Update `KeycloakManager` to inject the `Keycloak` bean directly.

---

### 2.11 Add Structured Logging
**Gap**: 6.1 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Standardize logging across all services. (1) Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`. (2) Create a shared `logback-spring.xml` that outputs JSON-formatted logs with trace/span IDs, service name, and timestamp. (3) Fix the string concatenation in `FundTransferService` line 32: change `"Sending fund transfer request {}" + request.toString()` to `"Sending fund transfer request {}", request`. (4) Add consistent request/response logging to all controllers using an MDC filter that logs the request method, path, and response status.

---

## Phase 3: Polish (Lower priority, longer-term improvements)

### 3.1 Add Contract Tests Between Services
**Gap**: 3.3 | **Severity**: High | **Effort**: Large

**Sample Devin Prompt**:
> Add Spring Cloud Contract tests between `core-banking-service` (provider) and its consumers (`fund-transfer-service`, `utility-payment-service`, `user-service`). (1) Add `spring-cloud-starter-contract-verifier` to core-banking-service. (2) Create contract DSL files for each endpoint consumed by Feign clients: `/api/v1/transaction/fund-transfer`, `/api/v1/transaction/util-payment`, `/api/v1/account/bank-account/{number}`, `/api/v1/user/{identification}`. (3) Add `spring-cloud-starter-contract-stub-runner` to each consumer service's test dependencies. (4) Write consumer-side tests that use stubs generated from the contracts.

---

### 3.2 Add Saga Pattern for Distributed Transactions
**Gap**: 7.6 | **Severity**: Critical | **Effort**: Large

**Sample Devin Prompt**:
> Implement a compensating transaction (saga) pattern for the fund transfer flow. Currently, the fund-transfer-service calls core-banking-service synchronously; if the process fails mid-way, the local entity is stuck in PENDING but core-banking may have partially processed. (1) Add a `status` state machine to `FundTransferEntity`: PENDING -> CORE_PROCESSING -> SUCCESS / FAILED / COMPENSATING -> COMPENSATED. (2) If the Feign call to core-banking fails after saving PENDING, set status to FAILED. (3) Add a compensation endpoint to core-banking-service that reverses a transaction by `transactionId`. (4) Add a scheduled job that finds PENDING transfers older than 5 minutes and either retries or compensates them. Consider using Spring State Machine or a lightweight saga orchestrator.

---

### 3.3 Add Custom Business Metrics
**Gap**: 6.3 | **Severity**: Medium | **Effort**: Medium

**Sample Devin Prompt**:
> Add custom Micrometer metrics for key business operations. (1) In `core-banking-service TransactionService`: add a counter `banking.fund.transfer.total` with tags `status=success|failed`, a timer `banking.fund.transfer.duration`, and a counter `banking.utility.payment.total`. (2) In `user-service UserService`: add counters for `banking.user.registration.total` (tagged by outcome) and `banking.user.approval.total`. (3) In `fund-transfer-service` and `utility-payment-service`: add counters and timers for the orchestration layer. Use `MeterRegistry` injected via constructor.

---

### 3.4 Normalize Package Structure
**Gap**: 1.3 | **Severity**: Low | **Effort**: Medium

**Sample Devin Prompt**:
> Normalize the package structure across all services to follow a consistent pattern: `com.javatodev.finance.{service-name}.controller`, `.service`, `.model.entity`, `.model.dto.request`, `.model.dto.response`, `.model.mapper`, `.repository`, `.configuration`, `.exception`. Specifically: (1) Move core-banking-service's `repository` package from root level into the existing structure. (2) Standardize user-service's `model.rest.response` to `model.dto.response`. (3) Ensure all Feign client interfaces are under `.service.rest.client`.

---

### 3.5 Adopt MapStruct for Object Mapping
**Gap**: 1.4 | **Severity**: Low | **Effort**: Medium

**Sample Devin Prompt**:
> Replace the manual `BaseMapper` pattern with MapStruct across all services. (1) Add MapStruct dependencies: `implementation 'org.mapstruct:mapstruct:1.5.5.Final'` and `annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'` to each `build.gradle`. (2) Convert each mapper to a MapStruct interface: e.g., `@Mapper(componentModel = "spring") public interface BankAccountMapper { BankAccount toDto(BankAccountEntity entity); }`. (3) Remove the `BaseMapper` abstract class and all manual `BeanUtils.copyProperties` calls. (4) Inject mappers as Spring beans in services.

---

### 3.6 Add Filtering and Sorting to List Endpoints
**Gap**: 5.4 | **Severity**: Low | **Effort**: Medium

**Sample Devin Prompt**:
> Add query parameter filtering to list endpoints. (1) In `fund-transfer-service GET /api/v1/transfer`: add optional `status`, `fromAccount`, `toAccount`, `dateFrom`, `dateTo` query parameters. Use Spring Data JPA Specifications or `@Query` methods. (2) In `utility-payment-service GET /api/v1/utility-payment`: add optional `status`, `account`, `providerId`, `dateFrom`, `dateTo` filters. (3) In `core-banking-service GET /api/v1/user`: add optional `email`, `firstName`, `lastName` filters. Ensure sorting is supported via `Pageable` sort parameters.

---

### 3.7 Normalize API URL Conventions
**Gap**: 5.5 | **Severity**: Low | **Effort**: Small

**Sample Devin Prompt**:
> Normalize API URL naming to consistent kebab-case and plural nouns. Create a mapping of old to new endpoints: `/api/v1/account/bank-account/{number}` -> `/api/v1/accounts/bank/{number}`, `/api/v1/account/util-account/{name}` -> `/api/v1/accounts/utility/{name}`, `/api/v1/user/{id}` -> `/api/v1/users/{id}`, `/api/v1/bank-users` -> `/api/v1/users`. Update the gateway route configuration accordingly. Consider keeping the old endpoints temporarily with `@Deprecated` for backward compatibility.

---

### 3.8 Add Dependency Vulnerability Scanning
**Gap**: 4.7 | **Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
> Add OWASP Dependency-Check to the Gradle build. (1) Add the plugin to each service's `build.gradle`: `id 'org.owasp.dependencycheck' version '9.0.9'`. (2) Configure it to fail on CVSS score >= 7: `dependencyCheck { failBuildOnCVSS = 7 }`. (3) Add a GitHub Actions workflow that runs `./gradlew dependencyCheckAnalyze` on PRs and uploads the HTML report as an artifact. (4) Alternatively, enable GitHub Dependabot by adding a `.github/dependabot.yml` file.

---

### 3.9 Add CORS Configuration
**Gap**: 4.5 | **Severity**: Low | **Effort**: Small

**Sample Devin Prompt**:
> Add CORS configuration to the API Gateway. In `SecurityConfiguration.java`, add a `CorsConfigurationSource` bean that allows configurable origins (default to `http://localhost:3000` for development), methods `GET, POST, PUT, DELETE, OPTIONS`, and headers `Authorization, Content-Type, X-Idempotency-Key`. Apply it to the `SecurityWebFilterChain`: `httpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource()))`. Make the allowed origins configurable via properties.

---

### 3.10 Configure Distributed Tracing Sampling
**Gap**: 6.5 | **Severity**: Low | **Effort**: Small

**Sample Devin Prompt**:
> Configure distributed tracing sampling and log correlation. (1) Set sampling rate to 100% in non-production: add `management.tracing.sampling.probability=1.0` to each service's config. (2) Add trace ID to log output by adding `logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]` to each service's configuration. (3) Add tracing dependencies to `internet-banking-config-server` and `internet-banking-service-registry` so the full request chain is traced.

---

## Summary Timeline

| Phase | Items | Estimated Effort | Focus |
|-------|-------|-----------------|-------|
| **Phase 1** | 11 items | ~5-7 days | Fix critical bugs, security holes, and basic quality |
| **Phase 2** | 11 items | ~10-15 days | Resilience, shared library, testing, observability |
| **Phase 3** | 10 items | ~10-15 days | Contract tests, saga pattern, API polish, DX improvements |

**Recommended starting order within Phase 1**: 1.1 (balance bug) -> 1.2 (validation) -> 1.3 (error handling) -> 1.5/1.6 (timeouts/retries) -> 1.7 (secrets) -> remaining items.
