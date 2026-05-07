# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, effort, and dependency ordering.

---

## Phase 1: Quick Wins (Critical Security & Stability Fixes)

These items address critical security vulnerabilities and stability issues that can be resolved with small, focused changes. Target: **1-2 weeks**.

---

### 1.1 Fix Generic Exception Handler Information Leak
**Gap:** GAP-ERR-02 | **Severity:** Critical | **Effort:** Small

The catch-all `Exception` handler in every service exposes internal stack traces to API consumers.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in all four services (`core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, `internet-banking-utility-payment-service`) so that the generic `@ExceptionHandler({Exception.class})` method returns an `ErrorResponse` object with a generic message like "An internal error occurred. Please try again later." and an appropriate error code, instead of exposing the raw exception. Return HTTP 500 instead of 400. Log the full exception server-side at ERROR level. Open a PR.

---

### 1.2 Fix HTTP Status Codes for Known Exceptions
**Gap:** GAP-ERR-01 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all `GlobalExceptionHandler` classes to return semantically correct HTTP status codes: `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422, `UserAlreadyRegisteredException` should return 409, `InvalidEmailException` and `InvalidBankingUserException` should return 400. `SimpleBankingGlobalException` should default to 400. The generic Exception handler should return 500. Open a PR.

---

### 1.3 Add Input Validation to All API Endpoints
**Gap:** GAP-SEC-02 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Jakarta Bean Validation annotations to all request DTOs across all services. Specifically: (1) `FundTransferRequest`: `@NotBlank` on fromAccount/toAccount, `@NotNull @Positive` on amount. (2) `UtilityPaymentRequest`: `@NotNull` on providerId, `@NotNull @Positive` on amount, `@NotBlank` on referenceNumber and account. (3) User registration `User` DTO: `@NotBlank @Email` on email, `@NotBlank` on identification and password. (4) Add `@Valid` on all `@RequestBody` parameters in controllers. (5) Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns 400 with field-level error details. Open a PR.

---

### 1.4 Remove Sensitive Data from Log Statements
**Gap:** GAP-OBS-05 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, audit all `log.info()` and `log.error()` calls across all services. Remove or mask sensitive data: (1) Never log full request DTOs that contain passwords, account numbers, or amounts — log only a request ID or reference. (2) Fix the string concatenation bug in `FundTransferService` (`"Sending fund transfer request {}" + request.toString()` should use `{}` placeholder). (3) Replace `request.toString()` in UserController with just the email or a masked version. Open a PR.

---

### 1.5 Externalize Docker Compose Credentials
**Gap:** GAP-SEC-01 | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml` to use environment variable references (e.g., `${MYSQL_ROOT_PASSWORD:-defaultpass}`) instead of hardcoded passwords for MySQL root password, MySQL app user password, Keycloak admin password, and Keycloak DB password. Create a `.env.example` file documenting the required variables. Update the `docker-compose/mysql/Dockerfile` to use `ARG`/`ENV` instead of hardcoded values. Open a PR.

---

### 1.6 Add `@Transactional` to Orchestrating Services
**Gap:** GAP-ERR-05 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add `@Transactional` annotation to `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` methods so that local database operations (saving the PENDING entity and updating to SUCCESS) are atomic. If the Feign call fails, the local entity should roll back to avoid orphaned PENDING records. Open a PR.

---

### 1.7 Add Feign Client Timeout Configuration
**Gap:** GAP-RES-03 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add explicit timeout configuration for all Feign clients. In each service that uses Feign (fund-transfer, user-service, utility-payment), add the following to `application.yml` (or the externalized config): `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Also add HikariCP connection pool settings: `spring.datasource.hikari.maximum-pool-size: 20`, `spring.datasource.hikari.connection-timeout: 5000`, `spring.datasource.hikari.leak-detection-threshold: 30000`. Open a PR.

---

### 1.8 Add Feign Client Retry Configuration
**Gap:** GAP-RES-02 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a Spring `Retryer` bean to each Feign client configuration class. Configure it with max 3 attempts, 100ms initial interval, and 1-second max interval. Only retry on connection exceptions and 503 responses, not on business errors (4xx). Update `CustomFeignClientConfiguration` in fund-transfer and utility-payment services. Open a PR.

---

### 1.9 Exclude Password from User Response DTO
**Gap:** GAP-SEC-07 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, in the `internet-banking-user-service`, add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field in the `User` DTO class. This ensures the password is accepted in registration requests but never serialized in API responses. Open a PR.

---

### 1.10 Fix Inconsistent Error Response Format
**Gap:** GAP-ERR-03 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, ensure all exception handlers in all `GlobalExceptionHandler` classes return the `ErrorResponse` object (with `code` and `message` fields) for every exception type — including the generic `Exception` handler. Add a `timestamp` and `status` field to `ErrorResponse` for richer error context. Open a PR.

---

## Phase 2: Important Improvements (Architecture & Reliability)

These items address structural issues, testing gaps, and resilience patterns. Target: **3-6 weeks**.

---

### 2.1 Add Circuit Breakers to All Feign Clients
**Gap:** GAP-RES-01 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Resilience4j circuit breaker support to all Feign clients. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to fund-transfer, user-service, and utility-payment `build.gradle` files. (2) Enable `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's config. (3) Create fallback classes for each Feign client that return appropriate error responses when the core-banking-service is unavailable. (4) Configure circuit breaker thresholds: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. Open a PR.

---

### 2.2 Create a Shared Library Module
**Gap:** GAP-ORG-01 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a new `internet-banking-common` module containing all duplicated code: `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditConfig`, `AuditorAwareConfig`, `ErrorResponse`, `SimpleBankingGlobalException`, and `GlobalExceptionHandler`. Set it up as a Gradle subproject. Update all consuming services to depend on this module instead of maintaining their own copies. Remove the duplicated classes from each service. Open a PR.

---

### 2.3 Add Unit Tests for Fund Transfer Service
**Gap:** GAP-TEST-01 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests for `FundTransferService` in the `internet-banking-fund-transfer-service`. Test cases: (1) successful fund transfer — verify entity saved as PENDING then updated to SUCCESS, (2) Feign client failure — verify entity stays PENDING and exception propagates, (3) null/invalid request handling. Use Mockito to mock `FundTransferRepository` and `BankingCoreFeignClient`. Follow the same pattern as existing `TransactionServiceTest` in core-banking-service. Open a PR.

---

### 2.4 Add Unit Tests for User Service
**Gap:** GAP-TEST-01 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests for `UserService` in the `internet-banking-user-service`. Test cases: (1) successful user creation flow, (2) duplicate email rejection, (3) email mismatch with core banking, (4) user not found in core banking, (5) user update with APPROVED status enabling Keycloak user, (6) readUser and readUsers pagination. Mock `KeycloakUserService`, `UserRepository`, and `BankingCoreRestClient`. Open a PR.

---

### 2.5 Add Unit Tests for Utility Payment Service
**Gap:** GAP-TEST-01 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests for `UtilityPaymentService` in the `internet-banking-utility-payment-service`. Test cases: (1) successful payment processing, (2) Feign client failure handling, (3) readPayments pagination. Mock `UtilityPaymentRepository` and `BankingCoreRestClient`. Open a PR.

---

### 2.6 Add Integration Tests with Testcontainers
**Gap:** GAP-TEST-02 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration tests for `core-banking-service` using Testcontainers for MySQL. (1) Add `org.testcontainers:mysql` and `org.testcontainers:junit-jupiter` test dependencies. (2) Create a base test class that starts a MySQL Testcontainer and configures Spring datasource properties. (3) Write integration tests for `AccountController` and `TransactionController` using `@SpringBootTest` with `TestRestTemplate` or MockMvc. (4) Verify that Flyway migrations run correctly and test data is seeded. Open a PR.

---

### 2.7 Add Rate Limiting to API Gateway
**Gap:** GAP-SEC-05, GAP-SEC-06 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add rate limiting to the `internet-banking-api-gateway`. (1) Add `spring-boot-starter-data-redis-reactive` dependency. (2) Configure `RequestRateLimiter` filter in the gateway routes with default rate of 100 requests/minute per user. (3) Apply a stricter limit (10 requests/minute per IP) to the `/user/api/v1/bank-users/register` endpoint. (4) Add Redis to `docker-compose.yml`. Open a PR.

---

### 2.8 Add Pagination Metadata to List Endpoints
**Gap:** GAP-API-03 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all paginated list endpoints to return `Page<T>` (or a custom `PageResponse<T>` wrapper with totalElements, totalPages, currentPage, size) instead of `List<T>`. Update `FundTransferService.readAllTransfers()`, `UtilityPaymentService.readPayments()`, `UserService.readUsers()` (in both core-banking and user-service). Open a PR.

---

### 2.9 Type-Parameterize All ResponseEntity Returns
**Gap:** GAP-API-01 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all controller methods across all services to use typed `ResponseEntity<T>` instead of raw `ResponseEntity`. For example, `ResponseEntity` should become `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc. This will improve Swagger documentation and compile-time safety. Open a PR.

---

### 2.10 Add Custom Health Checks
**Gap:** GAP-OBS-01 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom Spring Boot health indicators: (1) In user-service, add a `KeycloakHealthIndicator` that tests Keycloak connectivity. (2) In fund-transfer and utility-payment services, add a `CoreBankingHealthIndicator` that pings the core-banking-service actuator health endpoint. (3) Ensure all services expose `/actuator/health` with details by setting `management.endpoint.health.show-details=always` in configuration. Open a PR.

---

### 2.11 Fix Keycloak Client Singleton Thread Safety
**Gap:** GAP-SEC-04 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, refactor `KeycloakProperties.getInstance()` in the user-service to be thread-safe. Replace the manual static singleton with a Spring `@Bean` method that creates the `Keycloak` instance once via the Spring container. Remove the static field and the null-check pattern. This ensures thread safety and proper lifecycle management. Open a PR.

---

### 2.12 Add Dependency Vulnerability Scanning
**Gap:** GAP-SEC-08 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the OWASP Dependency Check Gradle plugin to each service's `build.gradle`. Add `id 'org.owasp.dependencycheck' version '9.0.9'` to the plugins block. Configure it to fail the build on CVSS score >= 7. Also update `springdoc-openapi-starter-webflux-ui` from 2.1.0 to the latest stable version. Open a PR.

---

### 2.13 Fix Logging Patterns
**Gap:** GAP-OBS-02 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, fix all logging statements across all services: (1) Replace string concatenation in log calls with SLF4J parameterized messages (e.g., change `"message {}" + value` to `"message {}", value`). (2) Ensure error log calls pass the exception as the last parameter for proper stack trace rendering: `log.error("Error: {}", message, exception)`. (3) Configure structured JSON logging by adding `logback-spring.xml` with a JSON encoder for the `docker` profile. Open a PR.

---

## Phase 3: Polish & Excellence

These items elevate the codebase from "working" to "production-grade." Target: **Ongoing / as capacity allows**.

---

### 3.1 Implement Saga Pattern for Distributed Transactions
**Gap:** GAP-RES-06 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement a choreography-based saga pattern for the fund transfer flow. (1) Add a `TransactionStatus.COMPENSATING` and `TransactionStatus.COMPENSATED` enum values. (2) When `FundTransferService.fundTransfer()` fails after the core-banking call succeeds, publish a compensation event. (3) Add a scheduled task that detects PENDING transfers older than 5 minutes and triggers reconciliation. (4) Document the saga flow in a sequence diagram. Open a PR.

---

### 3.2 Create Multi-Module Gradle Build
**Gap:** GAP-ORG-02 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a root `settings.gradle` and `build.gradle` that includes all services as subprojects. Create a Gradle version catalog (`gradle/libs.versions.toml`) to centralize dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector, Lombok, etc.). Update each service's `build.gradle` to reference the catalog instead of hardcoded versions. Ensure `./gradlew build` from the root builds all services. Open a PR.

---

### 3.3 Add Consumer-Driven Contract Tests
**Gap:** GAP-TEST-03 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Spring Cloud Contract tests between services. (1) In core-banking-service, define contracts for the fund-transfer and utility-payment Feign client endpoints. (2) Generate contract stubs that downstream services can use in their tests. (3) In fund-transfer-service, write stub-based tests that verify the Feign client works correctly against the contract. (4) Add `spring-cloud-starter-contract-verifier` and `spring-cloud-starter-contract-stub-runner` dependencies as appropriate. Open a PR.

---

### 3.4 Standardize Package Structure
**Gap:** GAP-ORG-03 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, standardize the package structure across all services to follow a consistent convention: `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.dto.request`, `model.dto.response`, `model.mapper`, `exception`, `configuration`. Rename `BankingCoreRestClient` to `BankingCoreFeignClient` in user-service and utility-payment-service for consistency. Move `UtilityPaymentRepository` under `model.repository` in utility-payment-service. Open a PR.

---

### 3.5 Add OpenAPI Configuration
**Gap:** GAP-API-04 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add `@OpenAPIDefinition` configuration to each business service with title, description, version, and contact info. Add `@SecurityScheme` for OAuth2/JWT bearer token on the API gateway. Ensure all endpoint `@Operation` annotations include response schemas (`@ApiResponse` with `@Content` and `@Schema`). Open a PR.

---

### 3.6 Add Custom Business Metrics
**Gap:** GAP-OBS-03 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Micrometer custom metrics: (1) Add `micrometer-registry-prometheus` dependency to all services. (2) In core-banking-service: add a counter `banking.transactions.total` tagged by type (FUND_TRANSFER/UTILITY_PAYMENT) and status (SUCCESS/FAILED), and a histogram `banking.transactions.amount`. (3) In user-service: add a counter `banking.users.registered` and `banking.users.approved`. (4) Expose Prometheus metrics at `/actuator/prometheus`. (5) Add Prometheus to docker-compose. Open a PR.

---

### 3.7 Register Mappers as Spring Beans
**Gap:** GAP-ORG-04 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, convert all mapper classes to Spring `@Component` beans instead of being manually instantiated with `new`. Add `@Component` to `FundTransferMapper`, `UserMapper` (both services), `BankAccountMapper`, `UtilityAccountMapper`, `UtilityPaymentMapper`. Inject them via constructor injection in the corresponding service classes. Remove the `new XxxMapper()` field initializations. Open a PR.

---

### 3.8 Add Fallback Behavior for Feign Clients
**Gap:** GAP-RES-04 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement Feign fallback classes for graceful degradation. (1) Create `BankingCoreFeignClientFallback` in fund-transfer-service that returns a `FundTransferResponse` with message "Service temporarily unavailable. Please try again later." (2) Create similar fallbacks in user-service and utility-payment-service. (3) Register fallbacks in the `@FeignClient` annotations. (4) Add appropriate logging in fallback methods to alert on degraded state. Open a PR.

---

### 3.9 Document API Versioning Strategy
**Gap:** GAP-API-02 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a `docs/API_VERSIONING.md` document that defines the API versioning strategy: URI path versioning (`/api/v1/`, `/api/v2/`), rules for introducing breaking changes, deprecation policy, and backward compatibility guidelines. Add a note in each service's README referencing this document. Open a PR.

---

### 3.10 Add HikariCP Connection Pool Configuration
**Gap:** GAP-RES-05 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add explicit HikariCP configuration to all database-connected services via their externalized configuration. Set `spring.datasource.hikari.maximum-pool-size=20`, `minimum-idle=5`, `connection-timeout=5000`, `idle-timeout=300000`, `max-lifetime=600000`, `leak-detection-threshold=30000`, `validation-timeout=3000`, `connection-test-query=SELECT 1`. Open a PR.

---

## Roadmap Summary

| Phase | Items | Critical Fixes | Target Timeline |
|-------|-------|---------------|-----------------|
| **Phase 1: Quick Wins** | 10 items | 4 (ERR-02, SEC-01, SEC-02, ERR-01) | 1-2 weeks |
| **Phase 2: Important** | 13 items | 4 (RES-01, TEST-01 x3) | 3-6 weeks |
| **Phase 3: Polish** | 10 items | 0 | Ongoing |

### Dependency Graph

```
Phase 1 (no dependencies — all can be done in parallel)
  │
  ├── 1.1 Fix exception info leak
  ├── 1.2 Fix HTTP status codes
  ├── 1.3 Add input validation
  ├── 1.4 Remove sensitive log data
  ├── 1.5 Externalize credentials
  ├── 1.6 Add @Transactional
  ├── 1.7 Add timeouts
  ├── 1.8 Add retries
  ├── 1.9 Exclude password from response
  └── 1.10 Fix error response format
        │
Phase 2 (some dependencies)
  │
  ├── 2.1 Circuit breakers (after 2.2 ideally, but can be done independently)
  ├── 2.2 Shared library ◄── should be done before 2.3-2.5 for cleaner tests
  ├── 2.3 Fund transfer unit tests
  ├── 2.4 User service unit tests
  ├── 2.5 Utility payment unit tests
  ├── 2.6 Integration tests (after 2.2)
  ├── 2.7 Rate limiting
  ├── 2.8 Pagination metadata
  ├── 2.9 Type-parameterize ResponseEntity
  ├── 2.10 Custom health checks
  ├── 2.11 Fix Keycloak singleton
  ├── 2.12 Dependency scanning
  └── 2.13 Fix logging
        │
Phase 3 (after Phase 2)
  │
  ├── 3.1 Saga pattern (after 2.1 circuit breakers)
  ├── 3.2 Multi-module build (after 2.2 shared library)
  ├── 3.3 Contract tests (after 2.3-2.5 unit tests)
  └── ... remaining items (independent)
```
