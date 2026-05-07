# Remediation Roadmap

This roadmap prioritizes the 39 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into four phases, ordered by risk reduction and business impact. Each phase is designed to be independently deployable.

---

## Phase 1: Critical Fixes (Week 1-2)

> **Goal:** Eliminate data corruption risks, security vulnerabilities, and production-blocking defects.

### 1.1 Fix Balance Calculation Bug
- **Gap:** 7.7 — Double-subtraction in `availableBalance` calculation
- **Severity:** Critical | **Effort:** Small
- **Action:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, change:
  ```java
  // BEFORE (buggy):
  entity.setActualBalance(entity.getActualBalance().subtract(amount));
  entity.setAvailableBalance(entity.getActualBalance().subtract(amount));

  // AFTER (correct):
  BigDecimal newBalance = entity.getActualBalance().subtract(amount);
  entity.setActualBalance(newBalance);
  entity.setAvailableBalance(newBalance);
  ```
- **Verification:** Add unit tests confirming balance correctness after transfers and payments.

### 1.2 Stop Leaking Internal Exception Details
- **Gap:** 2.2 — Generic exception handler exposes stack traces
- **Severity:** Critical | **Effort:** Small
- **Action:** Replace the catch-all handler in all `GlobalExceptionHandler` classes:
  ```java
  @ExceptionHandler({Exception.class})
  protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
      log.error("Unhandled exception", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ErrorResponse.builder()
              .code("INTERNAL_ERROR")
              .message("An unexpected error occurred")
              .build());
  }
  ```

### 1.3 Remove Sensitive Data from Logs
- **Gap:** 6.6 — Passwords and financial data logged via `toString()`
- **Severity:** Critical | **Effort:** Small
- **Action:**
  - Add `@JsonIgnore` and exclude `password` from `User.toString()` (or use `@ToString.Exclude` from Lombok)
  - Replace `request.toString()` in controller logs with specific safe fields: `log.info("Fund transfer from={} to={}", request.getFromAccount(), request.getToAccount())`
  - Audit all `log.info` calls in controllers for sensitive data

### 1.4 Add Input Validation
- **Gap:** 4.2 — No Bean Validation on request DTOs
- **Severity:** Critical | **Effort:** Medium
- **Action:**
  - Add `spring-boot-starter-validation` to all business service `build.gradle` files
  - Annotate request DTOs:
    ```java
    @NotBlank private String fromAccount;
    @NotBlank private String toAccount;
    @NotNull @Positive private BigDecimal amount;
    ```
  - Add `@Valid` to controller method parameters
  - Add a `MethodArgumentNotValidException` handler to `GlobalExceptionHandler`

### 1.5 Protect Password Field in User DTO
- **Gap:** 4.6 — Password returned in API responses
- **Severity:** Critical | **Effort:** Small
- **Action:** Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the `password` field in the User DTO, or split into separate `UserRegistrationRequest` and `UserResponse` DTOs.

### 1.6 Add Idempotency Keys to Financial Operations
- **Gap:** 7.5 — No idempotency controls on fund transfer/payment endpoints
- **Severity:** Critical | **Effort:** Medium
- **Action:**
  - Add an `idempotencyKey` field to `FundTransferRequest` and `UtilityPaymentRequest`
  - Store the key with the transaction record
  - Check for existing transactions with the same key before processing
  - Return the existing result if a duplicate is detected

### 1.7 Add Database Concurrency Controls
- **Gap:** 7.6 — No locking on concurrent balance modifications
- **Severity:** Critical | **Effort:** Medium
- **Action:**
  - Add `@Version` column to `BankAccountEntity` for optimistic locking
  - Or use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository `findByNumber` method
  - Add retry logic for optimistic lock failures
  - Set appropriate transaction isolation level

### 1.8 Externalize Secrets from Source Code
- **Gap:** 4.1 — Hardcoded credentials in docker-compose and SQL files
- **Severity:** Critical | **Effort:** Small
- **Action:**
  - Replace hardcoded values with environment variables in `docker-compose.yml`:
    ```yaml
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    ```
  - Create a `.env.example` file with placeholder values
  - Add `.env` to `.gitignore`
  - Document secret management in README

**Phase 1 Total: 8 items | Estimated: 1.5-2 weeks**

---

## Phase 2: Reliability & Error Handling (Week 3-4)

> **Goal:** Make the system resilient to failures and provide clear error responses.

### 2.1 Fix HTTP Status Codes
- **Gap:** 2.1 — All exceptions return 400
- **Severity:** High | **Effort:** Small
- **Action:** Map exceptions to appropriate status codes:
  - `EntityNotFoundException` → `404 Not Found`
  - `InsufficientFundsException` → `422 Unprocessable Entity`
  - `UserAlreadyRegisteredException` → `409 Conflict`
  - `InvalidEmailException` / `InvalidBankingUserException` → `400 Bad Request`
  - Generic `Exception` → `500 Internal Server Error`

### 2.2 Standardize Error Response Format
- **Gap:** 2.3 — Inconsistent error response shapes
- **Severity:** High | **Effort:** Small
- **Action:** Ensure all exception handlers return the same `ErrorResponse { code, message, timestamp }` shape. Add `timestamp` field to `ErrorResponse`.

### 2.3 Add Feign Error Handling
- **Gap:** 2.4 — No error decoders or fallbacks on Feign clients
- **Severity:** High | **Effort:** Medium
- **Action:**
  - Implement a custom `ErrorDecoder` for Feign clients that translates HTTP errors into domain exceptions
  - Wrap Feign calls in service layer with try-catch for `FeignException`
  - Return meaningful error messages when downstream services fail

### 2.4 Add Circuit Breakers
- **Gap:** 7.1 — No circuit breakers on inter-service calls
- **Severity:** High | **Effort:** Medium
- **Action:**
  - Add `resilience4j-spring-boot3` dependency to fund-transfer, utility-payment, and user services
  - Annotate Feign client calls with `@CircuitBreaker`
  - Configure thresholds: failure rate 50%, wait duration 30s, sliding window 10 calls
  - Add circuit breaker state to `/actuator/health`

### 2.5 Configure Feign Timeouts
- **Gap:** 7.3 — No timeout configuration
- **Severity:** High | **Effort:** Small
- **Action:** Add to each service's configuration:
  ```yaml
  feign:
    client:
      config:
        default:
          connectTimeout: 5000
          readTimeout: 10000
  ```

### 2.6 Secure Internal Services
- **Gap:** 4.4 — Internal services have no authentication
- **Severity:** Critical | **Effort:** Medium
- **Action:**
  - Option A: Add Spring Security with JWT validation to each internal service (propagate tokens via Feign interceptors)
  - Option B: Remove direct port exposure in Docker Compose (only expose gateway port); use Docker network isolation
  - Recommended: Both — defense in depth

### 2.7 Add Dependency Vulnerability Scanning
- **Gap:** 4.7 — No CVE scanning
- **Severity:** High | **Effort:** Small
- **Action:**
  - Add OWASP dependency-check Gradle plugin to all services
  - Configure a CI job to run `dependencyCheckAnalyze`
  - Set failure threshold for CVSS score >= 7.0

### 2.8 Create Shared Common Library
- **Gap:** 1.5 — No shared library for common code
- **Severity:** High | **Effort:** Large
- **Action:**
  - Create a `banking-common` module with shared DTOs, exceptions, error handling, and Feign configuration
  - Publish to local Maven repository or include as a Gradle composite build
  - Migrate duplicated code from all services to the common module

**Phase 2 Total: 8 items | Estimated: 2-3 weeks**

---

## Phase 3: Testing & Quality (Week 5-8)

> **Goal:** Build a comprehensive test suite and improve code quality.

### 3.1 Add Unit Tests for All Services
- **Gap:** 3.1 — Only core-banking has unit tests
- **Severity:** High | **Effort:** Large
- **Action:**
  - Write unit tests for `FundTransferService` (fund-transfer-service)
  - Write unit tests for `UtilityPaymentService` (utility-payment-service)
  - Write unit tests for `UserService`, `KeycloakUserService` (user-service)
  - Target: 80%+ line coverage on service layer classes

### 3.2 Add Integration Tests
- **Gap:** 3.2 — No integration tests
- **Severity:** High | **Effort:** Large
- **Action:**
  - Add `@WebMvcTest` controller tests for all services
  - Add `@DataJpaTest` repository tests with H2 in-memory databases
  - Add Testcontainers for MySQL-specific integration tests
  - Test full request→controller→service→repository→database flow

### 3.3 Fix Application Context Tests
- **Gap:** 3.5 — Context tests require full infrastructure
- **Severity:** Medium | **Effort:** Small
- **Action:**
  - Add test-specific `application.yml` to each service with H2, disabled Eureka/Config
  - Mock Keycloak and Feign clients in test configuration
  - Ensure all `*ApplicationTests.java` pass in CI without external dependencies

### 3.4 Add Test Coverage Reporting
- **Gap:** 3.4 — No coverage metrics
- **Severity:** Low | **Effort:** Small
- **Action:**
  - Add JaCoCo plugin to all `build.gradle` files
  - Configure minimum coverage thresholds (e.g., 70% line coverage)
  - Generate HTML coverage reports as part of the build

### 3.5 Set Up Multi-Project Gradle Build
- **Gap:** 1.1 — Independent Gradle projects
- **Severity:** Medium | **Effort:** Medium
- **Action:**
  - Create root `settings.gradle` including all services
  - Create root `build.gradle` with shared dependency versions via `subprojects {}`
  - Remove duplicated `gradle-wrapper.jar` from individual services
  - Maintain per-service `build.gradle` for service-specific dependencies

### 3.6 Consolidate Duplicated Classes
- **Gap:** 1.2, 1.3 — Copy-pasted exception/audit/filter classes
- **Severity:** Medium | **Effort:** Medium
- **Action:** Move to `banking-common` module (from Phase 2):
  - `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`
  - `AuditAware`, `AuditConfig`, `AuditorAwareConfig`
  - `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
  - `CustomFeignClientConfiguration`

### 3.7 Add Contract Tests
- **Gap:** 3.3 — No contract tests between services
- **Severity:** Medium | **Effort:** Large
- **Action:**
  - Add Spring Cloud Contract to core-banking-service (producer)
  - Generate contract stubs for Feign client consumers
  - Run consumer contract tests in fund-transfer, user, and utility-payment services

### 3.8 Add Retry Policies
- **Gap:** 7.2 — No retries on transient failures
- **Severity:** Medium | **Effort:** Small
- **Action:**
  - Configure Resilience4j retry on Feign calls: 3 attempts, 500ms backoff, exponential
  - Only retry on `5xx` and connection errors, not `4xx`

### 3.9 Add Fallback Behavior
- **Gap:** 7.4 — No fallbacks when services are down
- **Severity:** Medium | **Effort:** Medium
- **Action:**
  - Implement Feign fallback factories for each client
  - Return cached responses or meaningful error messages instead of raw exceptions

**Phase 3 Total: 9 items | Estimated: 3-4 weeks**

---

## Phase 4: Polish & Operational Excellence (Week 9-12)

> **Goal:** Improve observability, API design, and operational maturity.

### 4.1 Add Structured JSON Logging
- **Gap:** 6.1 — Unstructured, inconsistent logging
- **Severity:** Medium | **Effort:** Medium
- **Action:**
  - Add `logback-encoder` (Logstash) dependency
  - Configure JSON log format with fields: `timestamp`, `level`, `service`, `traceId`, `spanId`, `message`
  - Standardize log levels: DEBUG for detailed flow, INFO for business events, WARN for recoverable issues, ERROR for failures

### 4.2 Add Custom Health Checks
- **Gap:** 6.2 — Default health indicators only
- **Severity:** Medium | **Effort:** Small
- **Action:**
  - Implement `HealthIndicator` beans for database, Keycloak, and downstream services
  - Configure health endpoint to include component details: `management.endpoint.health.show-details=always`

### 4.3 Add Business Metrics
- **Gap:** 6.3 — No custom metrics
- **Severity:** Medium | **Effort:** Medium
- **Action:**
  - Add `@Timed` annotations to key service methods
  - Track: transfers per minute, payment success/failure rates, registration counts, average transfer amount
  - Expose via `/actuator/prometheus` endpoint

### 4.4 Fix OpenAPI Swagger Configuration
- **Gap:** 5.6 — Wrong starter (webflux instead of webmvc)
- **Severity:** Medium | **Effort:** Small
- **Action:** Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in core-banking, fund-transfer, user, and utility-payment services.

### 4.5 Add Response Type Parameters
- **Gap:** 5.1 — Raw `ResponseEntity` without type parameters
- **Severity:** Medium | **Effort:** Small
- **Action:** Add type parameters to all controller return types:
  ```java
  public ResponseEntity<User> readUser(...)
  public ResponseEntity<List<FundTransfer>> readFundTransfers(...)
  ```

### 4.6 Return Pagination Metadata
- **Gap:** 5.3 — List endpoints return `List<T>` without page info
- **Severity:** Medium | **Effort:** Small
- **Action:** Return `Page<T>` or a custom wrapper with `totalElements`, `totalPages`, `pageNumber`, `pageSize`.

### 4.7 Fix Thread-Safety in KeycloakProperties
- **Gap:** 4.5 — Keycloak singleton race condition
- **Severity:** Medium | **Effort:** Small
- **Action:** Replace with a Spring `@Bean` method in a `@Configuration` class, or use `synchronized` / `volatile` keyword.

### 4.8 Standardize Package Structure
- **Gap:** 1.4 — Inconsistent package layouts
- **Severity:** Low | **Effort:** Medium
- **Action:** Define and enforce a standard structure across all services:
  ```
  com.javatodev.finance/
    config/          (all configuration, security, feign)
    controller/      (REST controllers)
    service/         (business logic)
    client/          (Feign clients)
    model/
      entity/        (JPA entities)
      dto/           (request/response DTOs)
      mapper/        (entity-DTO mappers)
    repository/      (Spring Data repositories)
    exception/       (exceptions and handlers)
  ```

### 4.9 Fix REST Conventions
- **Gap:** 5.5 — Verbs in URLs, wrong status codes for POST
- **Severity:** Low | **Effort:** Small
- **Action:**
  - Rename `/bank-users/register` → `POST /bank-users`
  - Rename `/bank-users/update/{id}` → `PATCH /bank-users/{id}`
  - Return `201 Created` for POST operations with `Location` header

### 4.10 Add Filtering/Sorting Support
- **Gap:** 5.4 — No query parameters for filtering
- **Severity:** Low | **Effort:** Medium
- **Action:** Add query parameter support for status, date range, and amount filters on list endpoints. Use Spring Data `Specification` for dynamic queries.

### 4.11 Configure Distributed Tracing Sampling
- **Gap:** 6.4 — Default tracing configuration
- **Severity:** Low | **Effort:** Small
- **Action:** Set explicit sampling rate (e.g., `management.tracing.sampling.probability=1.0` for dev, `0.1` for production). Add custom spans for business operations.

### 4.12 Set Up Centralized Log Aggregation
- **Gap:** 6.5 — No log shipping
- **Severity:** Medium | **Effort:** Large
- **Action:**
  - Add ELK stack (Elasticsearch, Logstash, Kibana) or Loki+Grafana to Docker Compose
  - Configure log shipping from all containers
  - Create dashboards for key business and operational metrics

### 4.13 Document API Versioning Strategy
- **Gap:** 5.2 — No versioning strategy documented
- **Severity:** Low | **Effort:** Small
- **Action:** Document the chosen versioning approach (URL path `/api/v1/`) and deprecation policy in the API documentation.

### 4.14 Set Up CI/CD Pipeline
- **Gap:** No active CI/CD (workflows were removed)
- **Severity:** Medium | **Effort:** Medium
- **Action:**
  - Create GitHub Actions workflows for build, test, lint, and security scanning
  - Add branch protection rules requiring passing CI
  - Add Docker image build and push on tagged releases

**Phase 4 Total: 14 items | Estimated: 3-4 weeks**

---

## Roadmap Summary

| Phase | Focus | Items | Critical Fixed | Estimated Duration |
|---|---|---|---|---|
| **Phase 1** | Critical Fixes | 8 | 9 of 9 Critical | Weeks 1-2 |
| **Phase 2** | Reliability & Error Handling | 8 | Remaining High items | Weeks 3-4 |
| **Phase 3** | Testing & Quality | 9 | Medium items + test coverage | Weeks 5-8 |
| **Phase 4** | Polish & Operations | 14 | Low items + operational excellence | Weeks 9-12 |
| **Total** | | **39** | | **~12 weeks** |

### Priority Matrix

```
         HIGH IMPACT
              │
    Phase 1   │   Phase 2
   (Critical) │  (Reliability)
              │
LOW EFFORT ───┼─── HIGH EFFORT
              │
    Phase 4   │   Phase 3
    (Polish)  │   (Testing)
              │
         LOW IMPACT
```

### Key Dependencies

- **Phase 2.8** (shared library) should be started early as **Phase 3.6** (consolidate duplicated classes) depends on it
- **Phase 3.1-3.2** (tests) should be written alongside **Phase 1** fixes to verify correctness
- **Phase 4.14** (CI/CD) is a prerequisite for enforcing quality gates in all subsequent work
- **Phase 4.1** (structured logging) should precede **Phase 4.12** (log aggregation)

### Quick Wins (Can Start Immediately)

These items require minimal effort and have disproportionate impact:
1. Fix balance calculation bug (Phase 1.1) — **1 hour**
2. Stop leaking exception details (Phase 1.2) — **1 hour**
3. Remove passwords from logs (Phase 1.3) — **1 hour**
4. Protect password in User DTO (Phase 1.5) — **30 minutes**
5. Externalize secrets (Phase 1.8) — **2 hours**
6. Fix HTTP status codes (Phase 2.1) — **2 hours**
7. Configure Feign timeouts (Phase 2.5) — **1 hour**
