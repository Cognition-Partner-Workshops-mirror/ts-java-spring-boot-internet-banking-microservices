# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1–2 weeks): High-impact fixes with small effort. Addresses critical security and reliability issues.
- **Phase 2 — Important** (3–6 weeks): Structural improvements to error handling, resilience, testing, and API design.
- **Phase 3 — Polish** (6–12 weeks): Consistency, observability, and long-term maintainability improvements.

Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins

These items address critical security vulnerabilities, data integrity risks, and low-effort high-impact improvements.

### 1.1 Remove Hardcoded Credentials from Source Code
**Gap:** GAP-SEC-01 | **Severity:** Critical | **Effort:** Small

**What to do:**
- Replace all hardcoded passwords in `docker-compose.yml`, `docker-compose-support-apps.yml`, and `docker-compose/mysql/Dockerfile` with environment variable references (`${MYSQL_ROOT_PASSWORD}`, etc.)
- Create a `.env.example` file documenting required variables
- Add `.env` to `.gitignore`
- Remove test credentials from `README.md` or move them to a separate non-committed file

**Sample Devin Prompt:**
> Replace all hardcoded credentials in docker-compose files and the mysql Dockerfile with environment variable references. Create a `.env.example` file listing all required variables with placeholder values. Add `.env` to `.gitignore`. Remove the plaintext test credentials from README.md and reference the .env.example file instead. Open a PR.

---

### 1.2 Add @Transactional to Critical Business Operations
**Gap:** GAP-ERR-04 | **Severity:** Critical | **Effort:** Small

**What to do:**
- Add `@Transactional` to `TransactionService.fundTransfer()`, `TransactionService.utilPayment()`, and `TransactionService.internalFundTransfer()` in core-banking-service
- Add `@Transactional` to `FundTransferService.fundTransfer()` in fund-transfer-service
- Add `@Transactional` to `UtilityPaymentService.utilPayment()` in utility-payment-service
- Add try/catch around Feign calls to set status to `FAILED` on error

**Sample Devin Prompt:**
> In core-banking-service, add @Transactional annotations to all methods in TransactionService that modify account balances or create transactions. In fund-transfer-service and utility-payment-service, wrap the Feign calls in try/catch blocks: on failure, update the local entity status to FAILED before rethrowing. Add @Transactional to these service methods as well. Write unit tests to verify rollback behavior. Open a PR.

---

### 1.3 Fix HTTP Status Codes in Error Handlers
**Gap:** GAP-ERR-01 | **Severity:** High | **Effort:** Small

**What to do:**
- In all 4 `GlobalExceptionHandler` classes:
  - `EntityNotFoundException` → return **404 Not Found**
  - `InsufficientFundsException` → return **422 Unprocessable Entity**
  - `SimpleBankingGlobalException` (generic) → return **400 Bad Request**
  - `Exception` (catch-all) → return **500 Internal Server Error** with a generic message (no stack trace)

**Sample Devin Prompt:**
> Update the GlobalExceptionHandler in all 4 business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Add specific @ExceptionHandler methods for EntityNotFoundException (return 404), InsufficientFundsException (return 422), and change the catch-all Exception handler to return 500 with a generic error message instead of exposing exception details. Ensure all handlers return the structured ErrorResponse object. Open a PR.

---

### 1.4 Stop Logging Sensitive Data
**Gap:** GAP-OBS-02 | **Severity:** High | **Effort:** Small

**What to do:**
- Remove or redact account numbers, amounts, and auth IDs from log statements in:
  - `FundTransferController` / `FundTransferService`
  - `UtilityPaymentController` / `UtilityPaymentService`
  - `TransactionController` (core-banking)
- Replace `toString()` calls with safe summary strings (e.g., log only transfer ID or masked account numbers)

**Sample Devin Prompt:**
> Audit all log statements across fund-transfer-service, utility-payment-service, and core-banking-service for sensitive data exposure. Replace any logging of full request objects (using toString()) with safe summaries that mask account numbers (show only last 4 digits) and omit auth IDs. For example, change `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` to log only the transfer type and masked accounts. Open a PR.

---

### 1.5 Configure Feign Client Timeouts
**Gap:** GAP-RES-03 | **Severity:** High | **Effort:** Small

**What to do:**
- Add Feign timeout configuration to each service that uses Feign clients:
  ```yaml
  spring:
    cloud:
      openfeign:
        client:
          config:
            default:
              connect-timeout: 5000
              read-timeout: 10000
  ```
- Alternatively, add this to the external config repository for the `docker` profile

**Sample Devin Prompt:**
> Add Feign client timeout configuration to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Set connection timeout to 5 seconds and read timeout to 10 seconds. Add the configuration to each service's application.yml. Open a PR.

---

### 1.6 Fix Incorrect Swagger Dependency
**Gap:** GAP-API-05 | **Severity:** Medium | **Effort:** Small

**What to do:**
- In `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`:
  - Replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0`

**Sample Devin Prompt:**
> In all 4 business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), replace the incorrect springdoc-openapi-starter-webflux-ui dependency with springdoc-openapi-starter-webmvc-ui since these are Spring MVC (servlet) applications. Keep the same version (2.1.0). Verify each service still builds successfully. Open a PR.

---

### 1.7 Add Typed ResponseEntity to Controllers
**Gap:** GAP-API-01 | **Severity:** Medium | **Effort:** Small

**What to do:**
- Add generic type parameters to all `ResponseEntity` return types across all controllers
- Example: `ResponseEntity<FundTransferResponse>` instead of raw `ResponseEntity`

**Sample Devin Prompt:**
> Update all controller methods across all 4 business services to use typed ResponseEntity return values instead of raw ResponseEntity. For example, change `public ResponseEntity sendFundTransfer(...)` to `public ResponseEntity<FundTransferResponse> sendFundTransfer(...)`. This will improve Swagger/OpenAPI documentation generation. Open a PR.

---

### 1.8 Suppress Exception Details in Error Responses
**Gap:** GAP-SEC-05 | **Severity:** Medium | **Effort:** Small

**What to do:**
- Change the catch-all `Exception` handler in all services to return a generic message:
  ```java
  @ExceptionHandler({Exception.class})
  protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
      log.error("Unexpected error", e);  // Log full details server-side
      return ResponseEntity.internalServerError()
          .body(ErrorResponse.builder()
              .code("INTERNAL_ERROR")
              .message("An unexpected error occurred")
              .build());
  }
  ```

**Sample Devin Prompt:**
> In all GlobalExceptionHandler classes across the 4 business services, update the catch-all Exception handler to: (1) log the full exception at ERROR level for debugging, and (2) return a generic ErrorResponse with code "INTERNAL_ERROR" and message "An unexpected error occurred" with HTTP 500 status. Never expose raw exception details to the client. Open a PR.

---

## Phase 2: Important

These items require more effort but significantly improve reliability, security, and maintainability.

### 2.1 Add Input Validation to All Request DTOs
**Gap:** GAP-SEC-02 | **Severity:** High | **Effort:** Medium

**What to do:**
- Add `spring-boot-starter-validation` dependency to all business services
- Add Bean Validation annotations to all request DTOs:
  - `FundTransferRequest`: `@NotBlank fromAccount/toAccount`, `@NotNull @Positive amount`
  - `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank account`
  - `User` (registration): `@NotBlank @Email email`, `@NotBlank identification`, `@NotBlank @Size(min=8) password`
- Add `@Valid` annotation to all `@RequestBody` parameters in controllers
- Add `MethodArgumentNotValidException` handler to `GlobalExceptionHandler`

**Sample Devin Prompt:**
> Add Jakarta Bean Validation to all 4 business services. First add spring-boot-starter-validation dependency to each build.gradle. Then add validation annotations to all request DTOs: FundTransferRequest (NotBlank on fromAccount/toAccount, NotNull and Positive on amount), UtilityPaymentRequest (NotNull on providerId, Positive on amount, NotBlank on account/referenceNumber), User registration DTO (Email on email, NotBlank on identification and password, Size min=8 on password), UserUpdateRequest (NotNull on status). Add @Valid to all @RequestBody parameters. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details. Write unit tests for validation. Open a PR.

---

### 2.2 Add Circuit Breakers with Resilience4j
**Gap:** GAP-RES-01 | **Severity:** High | **Effort:** Medium

**What to do:**
- Add `spring-cloud-starter-circuitbreaker-resilience4j` to fund-transfer-service, utility-payment-service, and user-service
- Configure circuit breakers for all Feign client calls to core-banking-service
- Define fallback methods that return meaningful error responses
- Configure thresholds: failure rate threshold 50%, wait duration in open state 30s, sliding window size 10

**Sample Devin Prompt:**
> Add Resilience4j circuit breakers to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Add spring-cloud-starter-circuitbreaker-resilience4j dependency. Configure a circuit breaker named "coreBankingService" with failure rate threshold of 50%, wait duration of 30 seconds, and sliding window of 10 calls. Add @CircuitBreaker annotations to the Feign client interface methods with fallback methods that return appropriate error responses. Write tests verifying circuit breaker behavior. Open a PR.

---

### 2.3 Add Feign Error Decoders to All Consumer Services
**Gap:** GAP-ERR-03 | **Severity:** High | **Effort:** Medium

**What to do:**
- Create a proper `FeignErrorDecoder` for fund-transfer-service and utility-payment-service (user-service already has one)
- Map core-banking-service error responses back to appropriate exceptions
- Ensure Feign errors are translated into meaningful error responses, not opaque 500s

**Sample Devin Prompt:**
> Add custom Feign error decoders to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Create a CustomFeignErrorDecoder class in each service that: (1) reads the error response body from core-banking-service, (2) maps 404 responses to EntityNotFoundException, (3) maps 422 responses to domain-specific exceptions, (4) maps other errors to SimpleBankingGlobalException with the original error message. Register the decoder in the existing CustomFeignClientConfiguration. Review and improve the existing decoder in internet-banking-user-service. Write unit tests. Open a PR.

---

### 2.4 Add Service-Level Security
**Gap:** GAP-SEC-03, GAP-SEC-04 | **Severity:** High | **Effort:** Medium

**What to do:**
- Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to all business services
- Configure JWT validation at each service (not just the gateway)
- Add role-based access control: admin-only endpoints (user approval, list all users) vs user endpoints
- Alternatively, enforce that services only accept requests from the internal Docker network

**Sample Devin Prompt:**
> Add OAuth2 resource server security to core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Add spring-boot-starter-security and spring-boot-starter-oauth2-resource-server dependencies. Configure JWT validation using the same Keycloak JWKS endpoint as the gateway. Create a SecurityConfiguration class in each service that: (1) permits actuator endpoints, (2) requires authentication for all other endpoints, (3) in user-service, restricts PUT /api/v1/bank-users/{id} to users with the "admin" role. Update test configurations to disable security for unit tests. Open a PR.

---

### 2.5 Add Pagination Metadata to List Responses
**Gap:** GAP-API-03 | **Severity:** Medium | **Effort:** Small

**What to do:**
- Return `Page<T>` or a custom page wrapper instead of `List<T>` from all list endpoints
- Include `totalElements`, `totalPages`, `number`, `size` in the response

**Sample Devin Prompt:**
> Update all paginated list endpoints across the 4 business services to return Spring's Page wrapper instead of raw List. In each service method that accepts a Pageable, return the Page object directly instead of calling .getContent(). Update controllers to return ResponseEntity<Page<T>>. This will automatically include totalElements, totalPages, number, size, and content fields in the JSON response. Open a PR.

---

### 2.6 Add Retry Policies for Feign Calls
**Gap:** GAP-RES-02 | **Severity:** Medium | **Effort:** Small

**What to do:**
- Add `spring-retry` dependency to Feign consumer services
- Configure retry for transient failures (5xx, connection errors) with exponential backoff
- Do NOT retry on 4xx errors (client errors are not transient)
- Max retries: 3, initial interval: 1s, max interval: 5s

**Sample Devin Prompt:**
> Add Spring Retry support to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Add spring-retry dependency. Configure Feign client retry with: max attempts 3, initial backoff 1 second, max backoff 5 seconds, multiplier 2.0. Only retry on 5xx responses and connection exceptions, not on 4xx errors. Add the retry configuration to application.yml. Open a PR.

---

### 2.7 Add Idempotency Protection for Financial Operations
**Gap:** GAP-RES-05 | **Severity:** High | **Effort:** Medium

**What to do:**
- Accept an `Idempotency-Key` header on POST endpoints for fund transfers and utility payments
- Store the key with the transaction record
- On duplicate requests, return the original response instead of processing again
- Add a unique constraint on the idempotency key column

**Sample Devin Prompt:**
> Add idempotency protection to POST /api/v1/transfer in fund-transfer-service and POST /api/v1/utility-payment in utility-payment-service. Accept an "Idempotency-Key" request header. Add an idempotency_key column (with unique constraint) to FundTransferEntity and UtilityPaymentEntity. Before processing a request, check if a record with the same idempotency key exists — if so, return the existing response. If not, process normally and store the key. Add a filter or interceptor that validates the header is present on POST requests. Write tests for duplicate request handling. Open a PR.

---

### 2.8 Add Unit Tests for User, Fund Transfer, and Utility Payment Services
**Gap:** GAP-TEST-01 | **Severity:** High | **Effort:** Medium

**What to do:**
- Write unit tests for `UserService`, `FundTransferService`, and `UtilityPaymentService`
- Mock Feign clients and repositories
- Test happy paths and error scenarios (validation failures, Feign errors, not found)
- Target: at least 80% line coverage for service classes

**Sample Devin Prompt:**
> Write comprehensive unit tests for the service layer of internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. For each service: (1) Mock all dependencies (repositories, Feign clients, KeycloakUserService). (2) Test the happy path for each method. (3) Test error scenarios: entity not found, Feign client failure, validation errors. (4) Use JUnit 5 and Mockito. Place tests in the standard test directory structure. Target at least 80% line coverage on service classes. Open a PR.

---

### 2.9 Add Rate Limiting at the API Gateway
**Gap:** GAP-RES-04 | **Severity:** Medium | **Effort:** Medium

**What to do:**
- Add `spring-cloud-gateway-ratelimiter` or a Redis-backed rate limiter to the API Gateway
- Configure rate limits per route: fund transfer and utility payment endpoints should have stricter limits
- Return HTTP 429 Too Many Requests when limits are exceeded

**Sample Devin Prompt:**
> Add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's built-in RequestRateLimiter filter. Add spring-boot-starter-data-redis-reactive dependency. Configure rate limits: 10 requests/second for fund transfer and utility payment routes, 50 requests/second for other routes. Use the authenticated user's principal as the rate limit key. Add Redis to docker-compose.yml. Configure the rate limiter to return HTTP 429 with a JSON error response. Open a PR.

---

## Phase 3: Polish

These items improve long-term maintainability, developer experience, and operational maturity.

### 3.1 Create a Shared Library Module
**Gap:** GAP-ORG-02 | **Severity:** Medium | **Effort:** Medium

**What to do:**
- Create a `banking-commons` module containing shared code:
  - `BaseMapper`, `AuditAware`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`
  - `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
  - Common DTOs (`AccountResponse`)
- Publish as a local Gradle dependency or convert to a multi-module Gradle project

**Sample Devin Prompt:**
> Create a shared library module called "banking-commons" at the root of the repository. Set up a root settings.gradle that includes banking-commons and all 6 service projects as subprojects. Move the following duplicated classes into banking-commons: BaseMapper, AuditAware, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder. Update all services to depend on banking-commons and remove their local copies. Verify all services still compile and tests pass. Open a PR.

---

### 3.2 Unify Gradle Build with Version Catalog
**Gap:** GAP-ORG-01 | **Severity:** Medium | **Effort:** Small

**What to do:**
- Create a root `settings.gradle` with a Gradle version catalog (`libs.versions.toml`)
- Define all dependency versions in one place
- Update all service `build.gradle` files to reference the catalog

**Sample Devin Prompt:**
> Convert the project to use a Gradle version catalog for centralized dependency management. Create a gradle/libs.versions.toml file defining versions for: Spring Boot (3.2.4), Spring Cloud (2023.0.0), MySQL connector (8.4.0), Lombok, H2, springdoc-openapi (2.1.0), and Flyway (10.12.0). Create a root settings.gradle that includes all 6 services as subprojects. Update each service's build.gradle to use version catalog references (e.g., libs.spring.boot.starter.web) instead of hardcoded version strings. Verify all services build successfully. Open a PR.

---

### 3.3 Standardize Package Structure
**Gap:** GAP-ORG-03 | **Severity:** Low | **Effort:** Small

**What to do:**
- Adopt a consistent package layout across all services:
  ```
  com.javatodev.finance/
  ├── configuration/
  ├── controller/
  ├── exception/
  ├── model/
  │   ├── dto/
  │   ├── entity/
  │   └── mapper/
  ├── repository/
  └── service/
      └── rest/
  ```

**Sample Devin Prompt:**
> Standardize the package structure across all 4 business services to follow a consistent layout. Move classes so each service uses: configuration/ (all config classes), controller/, exception/, model/dto/ (all DTOs), model/entity/, model/mapper/, repository/ (at the same level as model, not nested inside), and service/rest/ (Feign clients). Update all import statements accordingly. Ensure all services compile and tests pass. Open a PR.

---

### 3.4 Add Structured JSON Logging
**Gap:** GAP-OBS-01 | **Severity:** Medium | **Effort:** Medium

**What to do:**
- Add `logstash-logback-encoder` dependency to all services
- Configure `logback-spring.xml` with JSON output format
- Include trace ID, span ID, service name, and log level in structured output
- Use different configurations for local (human-readable) and Docker (JSON) profiles

**Sample Devin Prompt:**
> Add structured JSON logging to all 6 services. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency to each build.gradle. Create a logback-spring.xml in each service's resources directory that: (1) uses console appender with pattern layout for the default profile, (2) uses LogstashEncoder for JSON output in the docker profile, (3) includes fields: timestamp, level, logger, message, traceId, spanId, service name. Open a PR.

---

### 3.5 Add Custom Health Checks
**Gap:** GAP-OBS-03 | **Severity:** Low | **Effort:** Small

**What to do:**
- Add custom `HealthIndicator` beans to check:
  - Database connectivity (already provided by Spring Boot, but verify it's enabled)
  - Downstream service reachability via Feign (in user-service, fund-transfer-service, utility-payment-service)
  - Keycloak connectivity (in user-service)

**Sample Devin Prompt:**
> Add custom health indicators to business services. In internet-banking-user-service, add a KeycloakHealthIndicator that checks connectivity to the Keycloak server. In fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator that pings core-banking-service's actuator/health endpoint. Ensure spring.boot.actuator endpoints include health details (management.endpoint.health.show-details=when-authorized). Verify the /actuator/health endpoint reflects the new indicators. Open a PR.

---

### 3.6 Add Prometheus Metrics
**Gap:** GAP-OBS-04 | **Severity:** Low | **Effort:** Medium

**What to do:**
- Add `micrometer-registry-prometheus` dependency to all services
- Expose `/actuator/prometheus` endpoint
- Add custom counters/timers for business operations (fund transfers, payments, user registrations)
- Provide a sample Prometheus scrape configuration and Grafana dashboard JSON

**Sample Devin Prompt:**
> Add Prometheus metrics support to all 6 services. Add io.micrometer:micrometer-registry-prometheus dependency. Expose the /actuator/prometheus endpoint. In core-banking-service, add custom metrics: fund_transfer_total (counter), utility_payment_total (counter), fund_transfer_duration (timer), utility_payment_duration (timer). In the API Gateway, add metrics for request count by route. Add a prometheus.yml scrape configuration to the docker-compose directory. Add a sample Grafana dashboard JSON for the key metrics. Open a PR.

---

### 3.7 Add Integration Tests with Testcontainers
**Gap:** GAP-TEST-02 | **Severity:** Medium | **Effort:** Large

**What to do:**
- Add Testcontainers dependency (MySQL, Keycloak) to test configurations
- Write integration tests that start real database and Keycloak containers
- Test the full request lifecycle through controllers → services → repositories
- Test Feign client interactions using WireMock for downstream services

**Sample Devin Prompt:**
> Add integration tests using Testcontainers to core-banking-service. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter test dependencies. Create an integration test base class that starts a MySQL Testcontainer and configures Spring Boot to use it. Write integration tests for: (1) AccountController — GET bank account by number, (2) TransactionController — POST fund transfer with sufficient and insufficient funds, (3) UserController — GET user by identification. Use @SpringBootTest with @AutoConfigureMockMvc. Verify Flyway migrations run against the test container. Open a PR.

---

### 3.8 Add Contract Tests Between Services
**Gap:** GAP-TEST-03 | **Severity:** Medium | **Effort:** Large

**What to do:**
- Add Spring Cloud Contract to core-banking-service (producer)
- Define contracts for all Feign client endpoints
- Generate stubs for consumer services
- Add contract verification tests in consumer services

**Sample Devin Prompt:**
> Add Spring Cloud Contract tests between core-banking-service (provider) and its consumers (fund-transfer-service, utility-payment-service, user-service). In core-banking-service: add spring-cloud-starter-contract-verifier dependency, create contract DSL files for all API endpoints used by consumers (GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}). Generate and publish stubs. In each consumer service: add spring-cloud-starter-contract-stub-runner test dependency, write tests that verify Feign clients work correctly against the generated stubs. Open a PR.

---

### 3.9 Make Mappers Spring-Managed Beans
**Gap:** GAP-ORG-04 | **Severity:** Low | **Effort:** Small

**What to do:**
- Add `@Component` to all Mapper classes
- Inject mappers via constructor injection instead of `new MapperClass()`
- This enables proper testing and future configuration

**Sample Devin Prompt:**
> Convert all Mapper classes across the 4 business services to Spring-managed beans. Add @Component annotation to FundTransferMapper, UserMapper (in both user-service and core-banking-service), UtilityPaymentMapper, BankAccountMapper, and UtilityAccountMapper. In service classes, replace `private XMapper mapper = new XMapper()` with constructor-injected `private final XMapper mapper`. Update any existing tests to mock or provide the mapper. Verify all services compile and tests pass. Open a PR.

---

### 3.10 Standardize REST Resource Naming
**Gap:** GAP-API-06 | **Severity:** Low | **Effort:** Small

**What to do:**
- Rename endpoints to follow REST conventions (plural nouns, no verbs):
  - `/api/v1/bank-users/register` → `POST /api/v1/bank-users` (registration is the POST action)
  - `/api/v1/account/bank-account/{id}` → `/api/v1/accounts/{id}`
  - `/api/v1/utility-payment` → `/api/v1/utility-payments`
  - `/api/v1/transfer` → `/api/v1/transfers`

> **Note:** This is a breaking change if clients depend on current URLs. Consider keeping old endpoints as aliases during a transition period.

**Sample Devin Prompt:**
> Standardize REST endpoint naming across all services to use plural nouns without verbs. Rename: (1) user-service: merge /register into POST /api/v1/bank-users, (2) core-banking-service: /api/v1/account/bank-account/{id} to /api/v1/accounts/{id} and /api/v1/account/util-account/{name} to /api/v1/utility-accounts/{name}, (3) utility-payment-service: /api/v1/utility-payment to /api/v1/utility-payments, (4) fund-transfer-service: /api/v1/transfer to /api/v1/transfers. Keep the old endpoints as deprecated aliases for backward compatibility. Update Feign clients in consumer services to use the new paths. Update API Gateway routes. Open a PR.

---

## Priority Summary

| Phase | Items | Key Outcomes |
|-------|-------|-------------|
| **Phase 1** | 1.1–1.8 (8 items) | No credentials in source, data integrity via transactions, proper HTTP semantics, no sensitive data in logs, Feign timeouts, correct Swagger, typed APIs |
| **Phase 2** | 2.1–2.9 (9 items) | Input validation, circuit breakers, Feign error handling, service-level security, pagination, retries, idempotency, unit tests, rate limiting |
| **Phase 3** | 3.1–3.10 (10 items) | Shared library, unified build, consistent structure, JSON logging, health checks, Prometheus, integration tests, contract tests, Spring-managed mappers, REST naming |

---

## Effort Estimation Summary

| Phase | Estimated Duration | Items |
|-------|-------------------|-------|
| Phase 1 | 1–2 weeks | 8 quick wins (mostly Small effort) |
| Phase 2 | 3–6 weeks | 9 important items (mix of Small and Medium effort) |
| Phase 3 | 6–12 weeks | 10 polish items (mix of Small, Medium, and Large effort) |
| **Total** | **~10–20 weeks** | **27 remediation items** |
