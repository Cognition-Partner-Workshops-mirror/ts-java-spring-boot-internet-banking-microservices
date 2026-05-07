# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical & High severity, Small effort)

These items address the most impactful issues with minimal code changes. Most can be completed in under a day each.

### 1.1 Fix Available Balance Double-Deduction Bug
**Gap Ref**: 7.6 | **Severity**: Critical | **Effort**: Small

The `internalFundTransfer()` and `utilPayment()` methods in `TransactionService` subtract `amount` from `actualBalance` and then set `availableBalance = actualBalance - amount` again, causing a double deduction.

**Devin Prompt**:
> Fix the available balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, after updating `actualBalance`, the `availableBalance` should be set equal to the new `actualBalance` (not `actualBalance - amount` again). Also add unit tests for the corrected balance calculations.

---

### 1.2 Add Input Validation to All Request DTOs
**Gap Ref**: 4.1 | **Severity**: Critical | **Effort**: Small

No request DTOs use Bean Validation. Add `jakarta.validation` annotations and `@Valid` in controllers.

**Devin Prompt**:
> Add Bean Validation annotations to all request DTOs across all services. Specifically: (1) Add `spring-boot-starter-validation` to each service's `build.gradle`. (2) Add `@NotNull`, `@NotBlank`, `@Positive`, and `@Size` annotations as appropriate to `FundTransferRequest`, `UtilityPaymentRequest`, `User` (registration), and `UserUpdateRequest`. (3) Add `@Valid` to all `@RequestBody` parameters in controllers. (4) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 400 with structured validation error details.

---

### 1.3 Stop Logging Passwords
**Gap Ref**: 4.6 | **Severity**: Critical | **Effort**: Small

The user-service controller logs `request.toString()` which includes the plaintext password.

**Devin Prompt**:
> Fix the password logging issue in `internet-banking-user-service`. (1) In `UserController.createUser()`, change the log statement to not log the full request object — only log the email. (2) Add `@JsonIgnore` on the `password` field's getter in the `User` DTO or override `toString()` to exclude the password. (3) Audit all other controllers and services for similar `toString()` calls that may log sensitive data.

---

### 1.4 Fix Generic Exception Handler
**Gap Ref**: 2.1 | **Severity**: Critical | **Effort**: Small

The catch-all `Exception` handler in all services returns 400 with the raw exception string, exposing internals.

**Devin Prompt**:
> Fix the generic exception handlers in all 4 business services (`core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`). In each `GlobalExceptionHandler`: (1) Change the catch-all `@ExceptionHandler({Exception.class})` to return 500 Internal Server Error instead of 400. (2) Return a structured `ErrorResponse` with a generic message like "An unexpected error occurred" and an error code like "INTERNAL_ERROR". (3) Log the full exception server-side at ERROR level but do NOT include it in the response body.

---

### 1.5 Add Proper HTTP Status Codes
**Gap Ref**: 2.3 | **Severity**: High | **Effort**: Small

All custom exceptions return 400 regardless of type.

**Devin Prompt**:
> Improve HTTP status code mapping in all `GlobalExceptionHandler` classes. Add specific `@ExceptionHandler` methods: (1) `EntityNotFoundException` should return `404 Not Found`. (2) `InsufficientFundsException` should return `422 Unprocessable Entity`. (3) `UserAlreadyRegisteredException` and `InvalidEmailException` should return `409 Conflict`. (4) Keep `SimpleBankingGlobalException` as `400 Bad Request`. (5) Ensure all responses use the structured `ErrorResponse` format.

---

### 1.6 Fix TransactionEntity Relationship Mapping
**Gap Ref**: 7.7 | **Severity**: High | **Effort**: Small

`TransactionEntity.account` uses `@OneToOne(cascade = CascadeType.ALL)` but should be `@ManyToOne`.

**Devin Prompt**:
> Fix the JPA relationship in `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`. Change `@OneToOne(cascade = CascadeType.ALL)` to `@ManyToOne(fetch = FetchType.LAZY)` on the `account` field, and remove the `CascadeType.ALL` to prevent accidental account deletion. Verify the existing Flyway migrations are compatible or add a new migration if needed.

---

### 1.7 Add Feign Client Timeouts
**Gap Ref**: 7.3 | **Severity**: High | **Effort**: Small

No connection or read timeouts are configured for inter-service calls.

**Devin Prompt**:
> Add timeout configuration for all Feign clients. In each service that uses OpenFeign (`internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`), add the following to `application.yml` (or the config server's configuration): `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also configure connection pool settings for the HTTP client.

---

### 1.8 Add Feign Retry Policies
**Gap Ref**: 7.2 | **Severity**: High | **Effort**: Small

No retry configuration exists for transient failures.

**Devin Prompt**:
> Add retry policies for Feign clients in all services that call core-banking-service. Add a Spring `Retryer` bean in each `CustomFeignClientConfiguration` class: `@Bean public Retryer feignRetryer() { return new Retryer.Default(100, 1000, 3); }`. This will retry up to 3 times with 100ms initial interval and 1s max interval. Also add the `feign.okhttp` dependency for connection pooling if not already present.

---

### 1.9 Externalize Database Credentials
**Gap Ref**: 4.2 | **Severity**: High | **Effort**: Small

Passwords are hardcoded in `docker-compose.yml` and `privileges.sql`.

**Devin Prompt**:
> Externalize all hardcoded credentials in `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml`. (1) Create a `.env.example` file with placeholder values for `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_PASSWORD`, `KC_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`. (2) Update docker-compose files to reference `${VARIABLE}` syntax. (3) Add `.env` to `.gitignore`. (4) Update `docker-compose/mysql/privileges.sql` to use an environment variable for the password. (5) Update README with instructions to copy `.env.example` to `.env`.

---

### 1.10 Restrict Database Permissions
**Gap Ref**: 4.3 | **Severity**: High | **Effort**: Small

The application database user has `*.*` privileges.

**Devin Prompt**:
> Restrict the MySQL user permissions in `docker-compose/mysql/privileges.sql`. Create separate database users per service with minimal privileges: (1) Each user should only have `INSERT, UPDATE, DELETE, SELECT` on its own database. (2) Remove `CREATE, ALTER, DROP` from application users — those should only be available to migration users or root. (3) Update the service configurations to use the service-specific database user.

---

### 1.11 Fix Inconsistent Error Response Structure
**Gap Ref**: 2.2 | **Severity**: High | **Effort**: Small

`ErrorResponse` class varies across services.

**Devin Prompt**:
> Standardize the `ErrorResponse` class across all services. Use this structure in all services: `ErrorResponse { String code, String message, Instant timestamp }` with `@Builder` and the timestamp defaulting to `Instant.now()`. Update all `GlobalExceptionHandler` classes to use this consistent format. Also fix the `SimpleBankingGlobalException` constructor issue (Gap 2.5) — ensure the `super(message)` call is made in the constructor.

---

### 1.12 Fix OpenAPI Dependency
**Gap Ref**: 5.5 | **Severity**: Medium | **Effort**: Small

Services use `springdoc-openapi-starter-webflux-ui` but only the gateway uses WebFlux.

**Devin Prompt**:
> Fix the OpenAPI/Swagger dependency in `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`. Change `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` to `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0` since these services use Spring MVC, not WebFlux. Verify the Swagger UI loads at `/swagger-ui.html` for each service.

---

### 1.13 Add ResponseEntity Type Parameters
**Gap Ref**: 5.1 | **Severity**: Medium | **Effort**: Small

Controllers return raw `ResponseEntity` without generics.

**Devin Prompt**:
> Add generic type parameters to all `ResponseEntity` return types in all controllers. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do this across all controllers in all services. This improves OpenAPI documentation generation and compile-time type safety.

---

### 1.14 Return Pagination Metadata
**Gap Ref**: 5.4 | **Severity**: Medium | **Effort**: Small

List endpoints return `List<T>` instead of `Page<T>`.

**Devin Prompt**:
> Update all paginated endpoints to return `Page<T>` instead of `List<T>`. In each service: (1) Change the controller return type from `ResponseEntity<List<T>>` to `ResponseEntity<Page<T>>`. (2) Update the service methods to return `Page<T>`. (3) This applies to: `UserController.readUsers()` in both core-banking and user-service, `FundTransferController.readFundTransfers()`, and `UtilityPaymentController.readPayments()`.

---

### 1.15 Fix Keycloak Singleton Thread Safety
**Gap Ref**: 4.4 | **Severity**: Medium | **Effort**: Small

The Keycloak instance uses an unsynchronized check-then-act pattern.

**Devin Prompt**:
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. Replace the lazy singleton with a `@PostConstruct` initialization or a `@Bean` method in a `@Configuration` class. Remove the static `keycloakInstance` field and instead create the Keycloak instance as a Spring bean so it is properly managed by the container lifecycle.

---

## Phase 2: Important (Critical/High severity with Medium/Large effort, or structural improvements)

### 2.1 Add Circuit Breakers with Resilience4j
**Gap Ref**: 7.1 | **Severity**: Critical | **Effort**: Medium

No circuit breakers protect inter-service calls from cascading failures.

**Devin Prompt**:
> Add Resilience4j circuit breakers to all Feign clients. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to the `build.gradle` of `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. (2) Enable circuit breakers for Feign: `spring.cloud.openfeign.circuitbreaker.enabled: true`. (3) Create fallback classes for each Feign client that return appropriate error responses. (4) Configure circuit breaker parameters: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`. (5) Add unit tests for the fallback behavior.

---

### 2.2 Implement Proper Feign Error Decoding Across All Services
**Gap Ref**: 2.4 | **Severity**: Critical | **Effort**: Medium

Only user-service has a `CustomFeignErrorDecoder`.

**Devin Prompt**:
> Standardize Feign error handling across all services. (1) Create a shared `CustomFeignErrorDecoder` pattern (or extract to a shared library — see 2.5) that properly maps HTTP status codes from downstream services to appropriate exceptions. (2) Apply it in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. (3) Ensure 4xx errors from core-banking propagate as client errors and 5xx errors trigger circuit breakers. (4) Add unit tests for the error decoder.

---

### 2.3 Add Unit Tests for All Business Services
**Gap Ref**: 3.1 | **Severity**: Critical | **Effort**: Large

Only core-banking-service has unit tests. Other services have no meaningful tests.

**Devin Prompt**:
> Add comprehensive unit tests for all business services. For each service: (1) `internet-banking-user-service`: Test `UserService.createUser()` (happy path, duplicate email, invalid email, user not found in core banking), `updateUser()` (approve, disable), `readUser()`, `readUsers()`. Mock `KeycloakUserService` and `BankingCoreRestClient`. (2) `internet-banking-fund-transfer-service`: Test `FundTransferService.fundTransfer()` (happy path, core banking failure, insufficient funds propagation), `readAllTransfers()`. Mock `BankingCoreFeignClient`. (3) `internet-banking-utility-payment-service`: Test `UtilityPaymentService.utilPayment()` and `readPayments()`. Mock `BankingCoreRestClient`. Use JUnit 5 and Mockito. Target >80% line coverage for service classes.

---

### 2.4 Add Rate Limiting to API Gateway
**Gap Ref**: 4.5 | **Severity**: High | **Effort**: Medium

No rate limiting protects the public registration endpoint or authenticated endpoints.

**Devin Prompt**:
> Add rate limiting to the API Gateway. (1) Add `spring-boot-starter-data-redis-reactive` and configure a `RequestRateLimiter` filter in the gateway routes. (2) Add Redis to docker-compose. (3) Configure rate limits: 10 requests/second for the public registration endpoint, 50 requests/second for authenticated endpoints. (4) Use the `KeyResolver` to resolve limits by IP for unauthenticated requests and by user principal for authenticated ones. (5) Return `429 Too Many Requests` with a `Retry-After` header.

---

### 2.5 Extract Shared Library
**Gap Ref**: 1.2 | **Severity**: High | **Effort**: Medium

Duplicated code across services should be extracted to a shared module.

**Devin Prompt**:
> Create a shared library module for common code. (1) Create a new Gradle module `banking-common` with shared classes: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `CustomFeignClientConfiguration`, and `TransactionStatus`. (2) Create a root `settings.gradle` that includes all services and the common module. (3) Update each service's `build.gradle` to depend on `project(':banking-common')`. (4) Remove the duplicated classes from each service. (5) Ensure all services still compile and tests pass.

---

### 2.6 Set Up Multi-Project Gradle Build
**Gap Ref**: 1.1 | **Severity**: Medium | **Effort**: Medium

No unified build exists.

**Devin Prompt**:
> Convert the project to a Gradle multi-project build. (1) Create a root `settings.gradle` that includes all service subprojects. (2) Create a root `build.gradle` with shared configuration: Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.0, common dependencies, and test configuration. (3) Simplify each service's `build.gradle` to only declare service-specific dependencies. (4) Remove the individual Gradle wrapper from each service (keep one at the root). (5) Verify `./gradlew build` at the root builds all services and `./gradlew :core-banking-service:test` runs tests for a single service.

---

### 2.7 Add Custom Health Indicators
**Gap Ref**: 6.2 | **Severity**: Medium | **Effort**: Small

Default health checks don't reflect actual service readiness.

**Devin Prompt**:
> Add custom health indicators for each service. (1) In services with MySQL: Add a health indicator that checks database connectivity via a simple query. (2) In `internet-banking-user-service`: Add a health indicator for Keycloak availability. (3) In all services: Ensure the Eureka client status is reflected in the health endpoint. (4) Configure `management.endpoint.health.show-details: always` for detailed health info. (5) Add liveness and readiness probes configuration for Kubernetes compatibility.

---

### 2.8 Standardize Logging with Structured JSON
**Gap Ref**: 6.1 | **Severity**: Medium | **Effort**: Small

Inconsistent logging with sensitive data exposure.

**Devin Prompt**:
> Standardize logging across all services. (1) Add `logstash-logback-encoder` dependency. (2) Configure `logback-spring.xml` with JSON output format including trace ID, span ID, service name, and log level. (3) Audit all log statements: remove any that log sensitive data (passwords, credentials, account numbers). (4) Ensure all services log at consistent levels: INFO for requests, DEBUG for detailed operations, ERROR for exceptions. (5) Add MDC context for request correlation (user ID, transaction ID).

---

### 2.9 Add Prometheus Metrics
**Gap Ref**: 6.3 | **Severity**: Medium | **Effort**: Medium

No metrics collection or dashboards despite Micrometer dependencies.

**Devin Prompt**:
> Enable Prometheus metrics across all services. (1) Add `micrometer-registry-prometheus` to each service's `build.gradle`. (2) Configure the actuator to expose the Prometheus endpoint: `management.endpoints.web.exposure.include: health,info,prometheus,metrics`. (3) Add a Prometheus container to docker-compose with scrape configuration for all services. (4) Add custom metrics: fund transfer count/duration, payment count/duration, user registration count. (5) Create a basic Grafana dashboard JSON for service health and business metrics.

---

### 2.10 Add Dependency Vulnerability Scanning
**Gap Ref**: 4.7 | **Severity**: Medium | **Effort**: Small

No automated vulnerability detection.

**Devin Prompt**:
> Add OWASP dependency-check to the Gradle build. (1) Add the `org.owasp.dependencycheck` Gradle plugin to the root `build.gradle`. (2) Configure it to fail the build on CVSS score >= 7. (3) Add a Gradle task `dependencyCheckAnalyze` that can be run manually or in CI. (4) Run an initial scan and document any existing vulnerabilities with a plan to address them.

---

## Phase 3: Polish (Medium/Low severity, structural improvements)

### 3.1 Add Integration Tests with Testcontainers
**Gap Ref**: 3.2 | **Severity**: High | **Effort**: Large

No integration tests verify database or inter-service interactions.

**Devin Prompt**:
> Add integration tests using Testcontainers for all services with database dependencies. (1) Add `testcontainers` and `testcontainers-mysql` to test dependencies. (2) For `core-banking-service`: Write tests that verify the full flow — create user, create account, fund transfer, utility payment — against a real MySQL instance. (3) For `internet-banking-fund-transfer-service`: Write tests with WireMock mocking the core-banking-service Feign calls. (4) For `internet-banking-user-service`: Write tests with WireMock for core-banking and a Keycloak testcontainer. (5) Fix the `@SpringBootTest` context loading issues by adding proper test profiles.

---

### 3.2 Add Contract Tests
**Gap Ref**: 3.3 | **Severity**: High | **Effort**: Large

No contract verification between Feign clients and providers.

**Devin Prompt**:
> Add Spring Cloud Contract tests between services. (1) Add `spring-cloud-starter-contract-verifier` to `core-banking-service` (the provider). (2) Write contract DSL files for each endpoint that fund-transfer and utility-payment services consume. (3) Add `spring-cloud-starter-contract-stub-runner` to the consumer services' test dependencies. (4) Write consumer-side tests that verify the Feign clients work against the generated stubs. (5) This ensures that changes to core-banking-service's API are caught before deployment.

---

### 3.3 Implement Saga Pattern for Distributed Transactions
**Gap Ref**: 7.5 | **Severity**: Critical | **Effort**: Large

Fund transfers span two services with no compensation logic.

**Devin Prompt**:
> Implement a saga pattern for the fund transfer flow. (1) Add an idempotency key (UUID) to `FundTransferRequest` that is generated by the caller and passed through to core-banking. (2) In `FundTransferService`, implement a state machine: INITIATED → PROCESSING → SUCCESS/FAILED. (3) If the core-banking call fails after the local entity is saved, update status to FAILED. (4) Add a compensation endpoint in core-banking-service to reverse a transaction by its ID. (5) Add a scheduled job to detect and retry/compensate stuck PROCESSING transfers. (6) Add idempotency checks in core-banking to prevent duplicate transfers with the same key.

---

### 3.4 Add Fallback Behavior
**Gap Ref**: 7.4 | **Severity**: Medium | **Effort**: Medium

No graceful degradation when downstream services fail.

**Devin Prompt**:
> Add fallback responses for all Feign clients. (1) Create `FeignClientFallback` classes for `BankingCoreFeignClient` and `BankingCoreRestClient`. (2) In the fund-transfer fallback: return a response indicating the transfer is queued for retry. (3) In the utility-payment fallback: return a response indicating the payment is pending. (4) For read operations (account lookup, user lookup): return appropriate error responses rather than failing silently. (5) Wire the fallbacks into the `@FeignClient` annotations.

---

### 3.5 Set Up CI/CD Pipeline
**Gap Ref**: 6.4, overall | **Severity**: Medium | **Effort**: Medium

No CI/CD pipeline exists.

**Devin Prompt**:
> Create a GitHub Actions CI pipeline. (1) Add `.github/workflows/ci.yml` that triggers on push and PR. (2) Steps: checkout, set up Java 21, run `./gradlew build` for all services, run `./gradlew test` with test reports, run OWASP dependency check. (3) Add Docker image build steps for each service. (4) Add a job that runs integration tests with docker-compose (using the support apps compose file). (5) Add status badges to README.md.

---

### 3.6 Standardize REST Conventions
**Gap Ref**: 5.3 | **Severity**: Low | **Effort**: Small

Inconsistent naming and REST patterns.

**Devin Prompt**:
> Standardize REST API conventions across all services. (1) Use consistent plural nouns: `/accounts`, `/users`, `/transfers`, `/payments`. (2) Use kebab-case for multi-word paths: `/bank-accounts`, `/utility-payments`. (3) Standardize path parameter naming to kebab-case: `{account-number}` instead of `{account_number}`. (4) Use PATCH semantics correctly or switch to PUT for full replacements. (5) Document the conventions in a new `docs/API_CONVENTIONS.md`.

---

### 3.7 Normalize Package Structure
**Gap Ref**: 1.3 | **Severity**: Low | **Effort**: Small

Inconsistent package layouts across services.

**Devin Prompt**:
> Standardize the package structure across all services to follow this convention: `com.javatodev.finance.{service-name}.controller`, `com.javatodev.finance.{service-name}.service`, `com.javatodev.finance.{service-name}.repository`, `com.javatodev.finance.{service-name}.model.entity`, `com.javatodev.finance.{service-name}.model.dto`, `com.javatodev.finance.{service-name}.model.mapper`, `com.javatodev.finance.{service-name}.config`, `com.javatodev.finance.{service-name}.exception`. Update imports and Spring component scan as needed.

---

## Summary Timeline

| Phase | Items | Estimated Duration | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 15 items | 1-2 weeks | Fix critical bugs, secure the app, standardize error handling, add validation |
| **Phase 2** | 10 items | 3-4 weeks | Resilience patterns, test coverage, shared library, observability |
| **Phase 3** | 7 items | 4-6 weeks | Integration/contract tests, saga pattern, CI/CD, API polish |

## Priority Order Within Phase 1

If resources are limited, address Phase 1 items in this order:

1. **1.1** Fix balance bug (data integrity — actively corrupting data)
2. **1.3** Stop logging passwords (active security leak)
3. **1.2** Add input validation (critical security gap)
4. **1.4** Fix generic exception handler (information disclosure)
5. **1.6** Fix TransactionEntity mapping (data model risk)
6. **1.5** Add proper HTTP status codes
7. **1.7** Add Feign timeouts
8. **1.8** Add Feign retry policies
9. **1.9** Externalize credentials
10. **1.10** Restrict DB permissions
11. **1.11** Fix error response structure
12. **1.12** Fix OpenAPI dependency
13. **1.13** Add ResponseEntity type params
14. **1.14** Return pagination metadata
15. **1.15** Fix Keycloak thread safety
