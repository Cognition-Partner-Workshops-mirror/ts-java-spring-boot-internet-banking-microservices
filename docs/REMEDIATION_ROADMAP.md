# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to begin remediation.

---

## Phase 1: Quick Wins (Critical/High severity, Small effort)

These items address security risks and quality gaps that can be fixed quickly with minimal code changes.

### 1.1 Fix Error Response Information Leakage (Gap 2.3)

**Severity: High | Effort: Small**

The generic exception handler exposes internal exception details to API consumers. Replace the raw exception string with a safe, generic error response.

> **Devin Prompt:**
> In all four services (`core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`), update the `GlobalExceptionHandler.handleException()` method to return a structured `ErrorResponse` with code `"INTERNAL_ERROR"` and message `"An unexpected error occurred. Please try again later."` instead of exposing the raw exception. Log the full exception at ERROR level for debugging. Use HTTP 500 instead of 400.

---

### 1.2 Externalize Hardcoded Credentials (Gap 4.2)

**Severity: Critical | Effort: Small**

Replace all hardcoded passwords in Docker Compose files with environment variable references.

> **Devin Prompt:**
> In `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml`, replace all hardcoded passwords (`MYSQL_ROOT_PASSWORD`, `KC_DB_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `POSTGRES_PASSWORD`) with `${VARIABLE_NAME}` references. Create a `.env.example` file documenting all required environment variables with placeholder values. Add `.env` to `.gitignore`. Update the README installation instructions to mention copying `.env.example` to `.env`.

---

### 1.3 Add Dependency Vulnerability Scanning (Gap 4.5)

**Severity: High | Effort: Small**

Add OWASP Dependency-Check to the Gradle builds.

> **Devin Prompt:**
> Add the `org.owasp.dependencycheck` Gradle plugin (latest version) to every service's `build.gradle`. Configure it to fail the build on CVSS score >= 7. Add a root-level `build.gradle` or script that runs `dependencyCheckAnalyze` across all services. Run it once and document any findings.

---

### 1.4 Add Feign Timeout Configuration (Gap 7.3)

**Severity: High | Effort: Small**

Configure explicit connection and read timeouts on all Feign clients.

> **Devin Prompt:**
> Add Feign timeout configuration to the `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Set connection timeout to 5 seconds and read timeout to 10 seconds. This can be done via `application.yml` properties under `spring.cloud.openfeign.client.config.default.connect-timeout` and `read-timeout`, or via the `CustomFeignClientConfiguration` class. Add equivalent test-profile overrides in `src/test/resources/application.yml`.

---

### 1.5 Add Feign Retry Policies (Gap 7.2)

**Severity: High | Effort: Small**

Configure retry for transient failures on Feign clients.

> **Devin Prompt:**
> Add a Spring `Retryer` bean to the `CustomFeignClientConfiguration` in `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Configure it with max 3 attempts, 100ms initial interval, and 1-second max interval. Only retry on connection exceptions, not on 4xx responses. Add a Feign `ErrorDecoder` to the fund-transfer-service's `CustomFeignClientConfiguration` (it currently only has a logger level) that properly decodes error responses from the core banking service.

---

### 1.6 Fix HTTP Status Codes (Gap 2.2)

**Severity: High | Effort: Medium**

Return proper HTTP status codes instead of 400 for everything.

> **Devin Prompt:**
> Update the `GlobalExceptionHandler` in all four services:
> - `EntityNotFoundException` → return HTTP 404
> - `InsufficientFundsException` → return HTTP 422
> - `UserAlreadyRegisteredException` → return HTTP 409
> - `InvalidEmailException`, `InvalidBankingUserException` → return HTTP 400
> - Generic `Exception` → return HTTP 500
> Make sure the `ErrorResponse` body is consistent across all handlers. Update the existing core-banking-service unit tests to verify the correct HTTP status codes are returned.

---

### 1.7 Add Test Coverage Reporting (Gap 3.4)

**Severity: Medium | Effort: Small**

Add JaCoCo plugin for coverage visibility.

> **Devin Prompt:**
> Add the `jacoco` plugin to every service's `build.gradle`. Configure it to generate HTML and XML reports after `test` task execution. Set a minimum coverage threshold of 0% initially (so it doesn't break the build) with a TODO comment to increase it as tests are added. Verify by running `./gradlew test jacocoTestReport` in the `core-banking-service` and confirm the report is generated at `build/reports/jacoco/test/html/index.html`.

---

### 1.8 Standardize Error Response Construction (Gap 2.1)

**Severity: Medium | Effort: Small**

Make error response construction consistent across all services.

> **Devin Prompt:**
> In `internet-banking-fund-transfer-service`, update the `ErrorResponse` class to use `@Builder` (like the other services) and update `GlobalExceptionHandler.handleGlobalException()` to use the builder pattern instead of the constructor. Verify all four services use the same `ErrorResponse` structure: `{ code: String, message: String }` with `@Builder`.

---

### 1.9 Fix Keycloak Singleton Thread Safety (Gap 4.4)

**Severity: Medium | Effort: Small**

Make the Keycloak instance creation thread-safe.

> **Devin Prompt:**
> In `internet-banking-user-service`, update `KeycloakProperties.getInstance()` to use either `synchronized` keyword or a `@PostConstruct` initialization pattern. The simplest fix: make the `getInstance()` method `synchronized`, or initialize `keycloakInstance` eagerly in a `@PostConstruct` method. Alternatively, annotate the method with `synchronized` or replace the lazy initialization with a `@Bean` method in a `@Configuration` class.

---

### 1.10 Add Consistent Logging (Gap 6.1)

**Severity: Medium | Effort: Small**

Standardize logging across all controllers and services.

> **Devin Prompt:**
> Ensure all controller classes have the `@Slf4j` annotation and log incoming requests at INFO level with a consistent format: `log.info("Received {} request: {}", "METHOD_NAME", sanitizedParams)`. Do NOT log sensitive fields like passwords or full account numbers — mask account numbers to show only the last 4 digits. Add structured logging configuration by adding a `logback-spring.xml` to each service's `src/main/resources/` that outputs JSON-formatted logs when the `docker` profile is active.

---

## Phase 2: Important (Critical/High severity, Medium/Large effort)

These items require more substantial code changes but are essential for production readiness.

### 2.1 Add Input Validation (Gap 4.1)

**Severity: Critical | Effort: Medium**

Add Jakarta Bean Validation to all request DTOs and controllers.

> **Devin Prompt:**
> Add `spring-boot-starter-validation` dependency to `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service` build.gradle files. Then add validation annotations to all request DTOs:
> - `FundTransferRequest`: `@NotBlank` on `fromAccount` and `toAccount`, `@NotNull @Positive` on `amount`
> - `UtilityPaymentRequest`: `@NotNull` on `providerId`, `@NotNull @Positive` on `amount`, `@NotBlank` on `referenceNumber` and `account`
> - `User` (user-service): `@Email @NotBlank` on `email`, `@NotBlank` on `identification` and `password`
> - `UserUpdateRequest`: `@NotNull` on `status`
>
> Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 422 with field-level error details.

---

### 2.2 Add Circuit Breakers (Gap 7.1)

**Severity: Critical | Effort: Medium**

Add Resilience4j circuit breakers on all inter-service Feign calls.

> **Devin Prompt:**
> Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Configure Resilience4j circuit breakers in each service's `application.yml` with:
> - Sliding window size: 10
> - Failure rate threshold: 50%
> - Wait duration in open state: 30 seconds
> - Permitted calls in half-open state: 3
>
> Enable Feign circuit breaker integration with `spring.cloud.openfeign.circuitbreaker.enabled=true`. Add fallback classes for each Feign client that return appropriate error responses when the circuit is open. Write unit tests for the fallback behavior.

---

### 2.3 Add Unit Tests for All Services (Gap 3.1)

**Severity: Critical | Effort: Large**

Write unit tests for services that currently have none.

> **Devin Prompt:**
> Write comprehensive unit tests for `internet-banking-user-service`:
> - `UserServiceTest`: Test `createUser()` happy path, duplicate email detection, core bank user not found, email mismatch, `readUsers()`, `readUser()`, and `updateUser()` with APPROVED status. Mock `KeycloakUserService`, `UserRepository`, and `BankingCoreRestClient`.
> - `KeycloakUserServiceTest`: Test `createUser()`, `updateUser()`, `readUserByEmail()`, and `readUser()` with mock `KeycloakManager`.
>
> Use Mockito for mocking. Follow the existing test patterns in `core-banking-service/src/test/java/`. Ensure tests run with the H2 test configuration.

> **Devin Prompt:**
> Write comprehensive unit tests for `internet-banking-fund-transfer-service`:
> - `FundTransferServiceTest`: Test `fundTransfer()` happy path (verify PENDING -> SUCCESS status transition, verify core banking Feign call), `fundTransfer()` when core banking returns error, and `readAllTransfers()` with pagination. Mock `FundTransferRepository` and `BankingCoreFeignClient`.
>
> Follow the existing test patterns in `core-banking-service/src/test/java/`.

> **Devin Prompt:**
> Write comprehensive unit tests for `internet-banking-utility-payment-service`:
> - `UtilityPaymentServiceTest`: Test `utilPayment()` happy path (verify PROCESSING -> SUCCESS status transition, verify core banking Feign call), `utilPayment()` when core banking returns error, and `readPayments()` with pagination. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`.
>
> Follow the existing test patterns in `core-banking-service/src/test/java/`.

---

### 2.4 Add Controller Tests (Gap 3.2)

**Severity: High | Effort: Medium**

Write `@WebMvcTest` tests for all controllers.

> **Devin Prompt:**
> Add `@WebMvcTest` controller tests for `core-banking-service`:
> - `AccountControllerTest`: Test `GET /api/v1/account/bank-account/{number}` returns 200 with valid account, returns 404 when not found.
> - `UserControllerTest`: Test `GET /api/v1/user/{identification}` and `GET /api/v1/user` pagination.
> - `TransactionControllerTest`: Test `POST /api/v1/transaction/fund-transfer` with valid request, insufficient funds, and invalid request.
>
> Use `@MockBean` for service dependencies and `MockMvc` for HTTP testing. Verify response status codes, response body structure, and content type.

---

### 2.5 Add Rate Limiting to API Gateway (Gap 4.6)

**Severity: Medium | Effort: Medium**

Configure Spring Cloud Gateway rate limiting.

> **Devin Prompt:**
> Add `spring-boot-starter-data-redis-reactive` dependency to `internet-banking-api-gateway`. Configure Spring Cloud Gateway's `RequestRateLimiter` filter with Redis-backed rate limiting. Set a default rate limit of 100 requests per second per user (keyed by JWT subject claim). Add a Redis service to `docker-compose.yml`. Configure the rate limiter in the gateway's route configuration (via the config server Git repo or local `application-docker.yml`).

---

### 2.6 Configure Prometheus Metrics Export (Gap 6.3)

**Severity: Medium | Effort: Medium**

Set up Prometheus metrics collection and export.

> **Devin Prompt:**
> Add `micrometer-registry-prometheus` dependency to all services' `build.gradle`. Configure the actuator to expose the `/actuator/prometheus` endpoint in each service's `application.yml`. Add a Prometheus container to `docker-compose.yml` with a `prometheus.yml` config that scrapes all service endpoints. Optionally add a Grafana container with a pre-configured Spring Boot dashboard. Update the API Gateway security configuration to permit `/actuator/prometheus` endpoints.

---

### 2.7 Add Fallback Behavior for Feign Clients (Gap 7.4)

**Severity: Medium | Effort: Medium**

Implement graceful degradation when downstream services fail.

> **Devin Prompt:**
> Create fallback classes for each Feign client:
> - `BankingCoreFeignClientFallback` in fund-transfer-service: Return a `FundTransferResponse` with status `FAILED` and message "Core banking service is temporarily unavailable"
> - `BankingCoreRestClientFallback` in utility-payment-service: Return a `UtilityPaymentResponse` with status `FAILED` and appropriate message
> - `BankingCoreRestClientFallback` in user-service: Throw a custom `ServiceUnavailableException` that the GlobalExceptionHandler maps to HTTP 503
>
> Register fallbacks on each `@FeignClient` annotation using `fallback = XxxFallback.class`. Write tests that verify fallback behavior when the Feign client throws an exception.

---

## Phase 3: Polish (Medium/Low severity, structural improvements)

These items improve long-term maintainability and developer experience.

### 3.1 Create Multi-Module Gradle Build (Gap 1.1)

**Severity: High | Effort: Large**

Consolidate services into a multi-module Gradle project with a shared library.

> **Devin Prompt:**
> Restructure the project as a multi-module Gradle build:
> 1. Create a root `settings.gradle` that includes all 7 services as subprojects.
> 2. Create a root `build.gradle` with shared configuration (Java version, Spring Boot/Cloud versions, common dependencies, common plugins).
> 3. Create a `shared-library` module containing the duplicated classes: `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`.
> 4. Update each service's `build.gradle` to inherit from the root and depend on `shared-library` where appropriate.
> 5. Remove the duplicated classes from each service.
> 6. Ensure all services still build and tests pass.

---

### 3.2 Add Contract Tests Between Services (Gap 3.3)

**Severity: High | Effort: Large**

Add Spring Cloud Contract or Pact tests for inter-service communication.

> **Devin Prompt:**
> Add Spring Cloud Contract tests for the `core-banking-service` as the producer:
> 1. Add `spring-cloud-starter-contract-verifier` to `core-banking-service`.
> 2. Write contract DSL files (Groovy) for each endpoint consumed by other services:
>    - `GET /api/v1/user/{identification}` (consumed by user-service)
>    - `GET /api/v1/account/bank-account/{account_number}` (consumed by fund-transfer and utility-payment services)
>    - `POST /api/v1/transaction/fund-transfer` (consumed by fund-transfer-service)
>    - `POST /api/v1/transaction/util-payment` (consumed by utility-payment-service)
> 3. Generate and publish stubs.
> 4. Add `spring-cloud-starter-contract-stub-runner` to consumer services and write stub-based integration tests.

---

### 3.3 Implement Saga Pattern for Distributed Transactions (Gap 7.6)

**Severity: Critical | Effort: Large**

Add compensation and idempotency for fund transfers and utility payments.

> **Devin Prompt:**
> Implement a choreography-based saga for the fund transfer flow:
> 1. Add an idempotency key (`transactionReference`) that is generated by the client or fund-transfer-service and passed to core-banking-service. Core banking should check for duplicate `transactionId` before processing.
> 2. Add a `COMPENSATING` status to `TransactionStatus`.
> 3. In `FundTransferService.fundTransfer()`, wrap the core banking call in a try-catch. If the call fails after the local record is saved, update the status to `FAILED`.
> 4. Add a scheduled job that scans for `PENDING` records older than 5 minutes and either retries or marks them as `FAILED`.
> 5. Add a compensation endpoint in core-banking-service: `POST /api/v1/transaction/fund-transfer/reverse` that reverses a transfer by `transactionId`.
> 6. Apply the same pattern to `UtilityPaymentService`.

---

### 3.4 Standardize Package Structure (Gap 1.2)

**Severity: Medium | Effort: Small**

Align package naming conventions across all services.

> **Devin Prompt:**
> Standardize the package structure across all services to follow this convention:
> - `controller/` — REST controllers
> - `service/` — Business logic
> - `service/rest/` — Feign clients
> - `model/entity/` — JPA entities
> - `model/dto/` — DTOs and request/response objects
> - `model/mapper/` — Entity-DTO mappers
> - `repository/` — Spring Data repositories
> - `configuration/` — Spring configuration classes
> - `exception/` — Exception classes and handlers
>
> Rename packages where they deviate (e.g., `model.repository` → `repository`, `model.rest.request` → `model.dto`). Update all import statements accordingly.

---

### 3.5 Add OpenAPI Global Configuration (Gap 5.5)

**Severity: Medium | Effort: Small**

Complete the Swagger/OpenAPI setup.

> **Devin Prompt:**
> For each of the four application services (core-banking, user-service, fund-transfer, utility-payment):
> 1. Replace the `springdoc-openapi-starter-webflux-ui` dependency with `springdoc-openapi-starter-webmvc-ui` (since these are servlet-based, not reactive).
> 2. Add an `OpenApiConfig` class with `@OpenAPIDefinition` annotation specifying API title, version, description, and security scheme (Bearer JWT).
> 3. Add `@Parameter`, `@ApiResponse`, and `@Schema` annotations to controllers and DTOs for complete documentation.
> 4. Add typed `ResponseEntity<T>` return types to all controller methods that currently use raw `ResponseEntity`.
> 5. Verify the Swagger UI is accessible at `http://localhost:{port}/swagger-ui.html` for each service.

---

### 3.6 Add Pagination Envelope (Gap 5.3)

**Severity: Low | Effort: Medium**

Wrap paginated responses in a standard envelope.

> **Devin Prompt:**
> Create a generic `PageResponse<T>` class (in the shared library or in each service) with fields: `content` (List<T>), `totalElements` (long), `totalPages` (int), `currentPage` (int), `pageSize` (int), `hasNext` (boolean), `hasPrevious` (boolean). Update all paginated controller methods to return `ResponseEntity<PageResponse<T>>` instead of `ResponseEntity<List<T>>`. Map the Spring Data `Page<T>` object to this DTO in each service method.

---

### 3.7 Add Custom Health Indicators (Gap 6.2)

**Severity: Low | Effort: Small**

Add meaningful health checks.

> **Devin Prompt:**
> Add custom `HealthIndicator` beans to services:
> - `core-banking-service`: `DatabaseHealthIndicator` that verifies a simple query runs against MySQL.
> - `internet-banking-user-service`: `KeycloakHealthIndicator` that checks Keycloak server info endpoint.
> - `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`: `CoreBankingHealthIndicator` that calls the core banking actuator health endpoint via Feign.
> Expose detailed health info only to authenticated admin requests via `management.endpoint.health.show-details=when-authorized`.

---

### 3.8 Register Mappers as Spring Beans (Gap 1.3)

**Severity: Low | Effort: Small**

Use dependency injection for mapper classes.

> **Devin Prompt:**
> In all services, add `@Component` annotation to all Mapper classes (`UserMapper`, `BankAccountMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`). Remove the field-level `new` instantiation in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) and replace with constructor injection via `@RequiredArgsConstructor`. Ensure the `BaseMapper` abstract class does not have `@Component`. Update any existing tests to mock the mapper if needed.

---

### 3.9 Add Bulkhead Isolation (Gap 7.5)

**Severity: Medium | Effort: Medium**

Isolate thread pools for different Feign clients.

> **Devin Prompt:**
> Configure Resilience4j bulkhead for each Feign client in `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Use thread-pool bulkheads with:
> - Max concurrent calls: 10
> - Max wait duration: 500ms
> Configure in `application.yml` under `resilience4j.bulkhead.instances.{feign-client-name}`. This requires the circuit breaker integration from Phase 2.2 to be in place first.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low |
|-------|-------|----------|------|--------|-----|
| **Phase 1: Quick Wins** | 10 | 1 | 5 | 4 | 0 |
| **Phase 2: Important** | 7 | 2 | 2 | 3 | 0 |
| **Phase 3: Polish** | 9 | 1 | 2 | 3 | 3 |
| **Total** | **26** | **4** | **9** | **10** | **3** |

> **Note:** Some gaps from the Gap Analysis are combined into single remediation items (e.g., Gap 5.1 "Raw ResponseEntity" is addressed within 3.5 "Add OpenAPI Global Configuration"). The total items here (26) may not match the gap count (32) due to this consolidation.

## Recommended Execution Order

Within each phase, the recommended order is:

**Phase 1 (estimated 1-2 weeks):**
1. Externalize credentials (1.2) — immediate security fix
2. Fix error response leakage (1.1) — immediate security fix
3. Fix HTTP status codes (1.6) — correctness
4. Standardize error responses (1.8) — consistency
5. Fix Keycloak thread safety (1.9) — correctness
6. Add Feign timeouts (1.4) — stability
7. Add Feign retry policies (1.5) — resilience
8. Add dependency scanning (1.3) — security
9. Add test coverage reporting (1.7) — visibility
10. Standardize logging (1.10) — observability

**Phase 2 (estimated 3-4 weeks):**
1. Add input validation (2.1) — security
2. Add circuit breakers (2.2) — resilience
3. Add unit tests for all services (2.3) — quality
4. Add controller tests (2.4) — quality
5. Add Feign fallbacks (2.7) — resilience
6. Configure Prometheus metrics (2.6) — observability
7. Add rate limiting (2.5) — security

**Phase 3 (estimated 4-6 weeks):**
1. Saga pattern for distributed transactions (3.3) — data integrity
2. Multi-module Gradle build (3.1) — maintainability
3. Contract tests (3.2) — quality
4. Standardize packages (3.4) — consistency
5. OpenAPI configuration (3.5) — documentation
6. Pagination envelope (3.6) — API quality
7. Custom health indicators (3.7) — observability
8. Register mappers as beans (3.8) — code quality
9. Bulkhead isolation (3.9) — resilience
