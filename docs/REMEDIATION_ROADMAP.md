# Remediation Roadmap

This roadmap prioritizes the 40 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (Critical fixes & small-effort items)

These items address production-risk bugs, security vulnerabilities, and other critical issues that can be resolved quickly.

### 1.1 Fix Balance Calculation Bug (Critical | Small)

**Gap**: `TransactionService.internalFundTransfer()` and `utilPayment()` double-subtract from `availableBalance` because the subtraction uses the already-modified `actualBalance`.

**Devin Prompt**:
```
In core-banking-service TransactionService.java, fix the balance calculation bug in
internalFundTransfer() and utilPayment(). The availableBalance is currently set by
subtracting amount from the already-reduced actualBalance, causing a double-deduction.
Change it so availableBalance is set to the same value as the new actualBalance after
the single subtraction. Also update the credit side (add) to use the same pattern.
Update the existing unit tests to assert correct balance values after transfers.
```

---

### 1.2 Stop Leaking Stack Traces in Error Responses (Critical | Small)

**Gap**: The catch-all `@ExceptionHandler({Exception.class})` returns `"Exception occur inside API " + e`, leaking internal details.

**Devin Prompt**:
```
In all four GlobalExceptionHandler classes (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service), replace the catch-all Exception handler.
Instead of returning the raw exception string, return a structured ErrorResponse with
code "INTERNAL_SERVER_ERROR" and a generic message "An unexpected error occurred.
Please try again later." Return HTTP 500 instead of 400. Log the full exception at
ERROR level with the trace ID.
```

---

### 1.3 Add Input Validation to All Request DTOs (Critical | Small)

**Gap**: No `@Valid` annotations or Bean Validation constraints on any request body.

**Devin Prompt**:
```
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. core-banking-service FundTransferRequest: @NotBlank on fromAccount and toAccount,
   @NotNull @Positive on amount
2. core-banking-service UtilityPaymentRequest: @NotNull on providerId, @NotNull
   @Positive on amount, @NotBlank on referenceNumber and account
3. internet-banking-user-service User (registration): @NotBlank @Email on email,
   @NotBlank on identification and password
4. internet-banking-fund-transfer-service FundTransferRequest: same as #1 plus
   @NotBlank on authID
5. internet-banking-utility-payment-service UtilityPaymentRequest: same as #2

Add @Valid to all @RequestBody parameters in controllers. Add a
MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns
HTTP 422 with field-level error details in ErrorResponse format.
```

---

### 1.4 Prevent Password Leakage in User Response (Critical | Small)

**Gap**: The `password` field in the User DTO can be serialized in responses.

**Devin Prompt**:
```
In internet-banking-user-service, add @JsonProperty(access =
JsonProperty.Access.WRITE_ONLY) to the password field in the User DTO class
(model/dto/User.java). This ensures the password is accepted on registration requests
but never included in API responses.
```

---

### 1.5 Externalize Secrets from Docker Compose (Critical | Small)

**Gap**: Database passwords, Keycloak credentials are hardcoded in `docker-compose.yml` and `privileges.sql`.

**Devin Prompt**:
```
Replace all hardcoded passwords in docker-compose/docker-compose.yml and
docker-compose/docker-compose-support-apps.yml with environment variable references
using ${VAR_NAME} syntax. Create a docker-compose/.env.example file listing all
required variables with placeholder values. Update the README.md installation section
to mention copying .env.example to .env and setting values. Add .env to .gitignore.
Variables to extract: MYSQL_ROOT_PASSWORD, MYSQL_APP_USER_PASSWORD, KC_DB_PASSWORD,
KEYCLOAK_ADMIN_PASSWORD. Also update docker-compose/mysql/privileges.sql to use an
entrypoint script that reads from environment variables.
```

---

### 1.6 Use Correct HTTP Status Codes (High | Small)

**Gap**: All exceptions return HTTP 400 regardless of the error type.

**Devin Prompt**:
```
Update all four GlobalExceptionHandler classes to return appropriate HTTP status codes:
- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException, InvalidBankingUserException -> 400 Bad Request
- SimpleBankingGlobalException (general) -> 400 Bad Request
- Exception (catch-all) -> 500 Internal Server Error

Ensure all handlers return the structured ErrorResponse format consistently (never a
plain string).
```

---

### 1.7 Add Retry and Timeout Configuration for Feign Clients (High | Small)

**Gap**: No timeout or retry configuration on Feign clients.

**Devin Prompt**:
```
Add Feign client timeout and retry configuration to all services that use OpenFeign
(internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service).

In each service's application.yml (or bootstrap.yml), add:
  spring.cloud.openfeign.client.config.default.connect-timeout: 5000
  spring.cloud.openfeign.client.config.default.read-timeout: 10000

Add spring-retry as a dependency and configure a Retryer bean with max 3 attempts,
100ms initial interval, 1s max interval. Only retry on 5xx errors and connection
failures, never on 4xx.
```

---

### 1.8 Fix Logging Issues (High | Small)

**Gap**: String concatenation in log statement, potential sensitive data logging, inconsistent log levels.

**Devin Prompt**:
```
Fix logging issues across all services:

1. In FundTransferService.java line 32, change the string concatenation bug:
   log.info("Sending fund transfer request {}" + request.toString())
   to: log.info("Sending fund transfer request from {} to {}", request.getFromAccount(), request.getToAccount())

2. In all controllers, replace request.toString() logging with specific non-sensitive
   fields only (e.g., log account numbers but never log amounts, passwords, or full
   request bodies)

3. In KeycloakUserService.readUser(), change log.error to properly log the exception:
   log.error("User not found under given ID {}", authId, e)

4. Add @Slf4j to UtilityPaymentController
```

---

### 1.9 Fix OpenAPI Dependency (Medium | Small)

**Gap**: MVC services incorrectly use the WebFlux OpenAPI starter.

**Devin Prompt**:
```
In the build.gradle files of core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service,
replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

These services use Spring MVC, not WebFlux. Only the API Gateway should use the
webflux variant (and it doesn't currently include OpenAPI).
```

---

### 1.10 Add Raw ResponseEntity Type Parameters (Medium | Small)

**Gap**: Controllers return raw `ResponseEntity` without generic type parameters.

**Devin Prompt**:
```
Add generic type parameters to all ResponseEntity return types across all controllers:

- core-banking AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- core-banking UserController: ResponseEntity<User>, ResponseEntity<List<User>>
- core-banking TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- fund-transfer FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- utility-payment UtilityPaymentController: ResponseEntity<List<UtilityPayment>>, ResponseEntity<UtilityPaymentResponse>

This improves OpenAPI documentation accuracy and eliminates compiler warnings.
```

---

### 1.11 Add Dependency Vulnerability Scanning (High | Small)

**Gap**: No automated dependency vulnerability scanning.

**Devin Prompt**:
```
Add the OWASP Dependency Check Gradle plugin to all services. In each build.gradle, add:
  plugins { id 'org.owasp.dependencycheck' version '9.0.9' }

Configure it to fail the build on CVSS score >= 7. Add a GitHub Actions workflow
.github/workflows/dependency-check.yml that runs ./gradlew dependencyCheckAnalyze on
each service on push to main and on PRs.
```

---

### 1.12 Add Consistent Error Response with Trace ID (Medium | Small)

**Gap**: Error responses don't include trace ID for correlation.

**Devin Prompt**:
```
Extend the ErrorResponse class in all services to include a traceId field. In each
GlobalExceptionHandler, inject the Tracer from Micrometer and populate the traceId
from the current span context. Return the traceId in every error response so consumers
can correlate errors with distributed traces in Zipkin.
```

---

## Phase 2: Important (High-impact structural improvements)

These items require more effort but significantly improve maintainability, reliability, and developer experience.

### 2.1 Add Circuit Breakers to All Feign Clients (Critical | Medium)

**Gap**: No circuit breaker protection on inter-service calls.

**Devin Prompt**:
```
Add Resilience4j circuit breaker support to all Feign clients:

1. Add dependencies to fund-transfer, utility-payment, and user-service build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breakers in application.yml:
   spring.cloud.openfeign.circuitbreaker.enabled: true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback: return error responses indicating core banking is unavailable
   - BankingCoreRestClientFallback: same pattern

4. Configure circuit breaker parameters:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3

5. Add @FeignClient(fallback = ...) to each Feign interface
```

---

### 2.2 Add Idempotency Keys for Financial Transactions (Critical | Medium)

**Gap**: No idempotency protection on transaction endpoints.

**Devin Prompt**:
```
Implement idempotency for fund transfer and utility payment endpoints:

1. Add an optional X-Idempotency-Key header to POST /api/v1/transfer and POST
   /api/v1/utility-payment endpoints

2. In FundTransferService and UtilityPaymentService, before processing:
   - Check if a record with the same idempotency key already exists
   - If found and status is SUCCESS, return the cached response
   - If found and status is PENDING/PROCESSING, return 409 Conflict
   - If not found, proceed with normal processing

3. Add an idempotency_key column to fund_transfer and utility_payment tables
   with a unique index

4. If no idempotency key is provided, generate one server-side (UUID) and return
   it in the response header
```

---

### 2.3 Fix Transaction Consistency (Saga Pattern) (Critical | Large)

**Gap**: Fund transfer and utility payment orchestration can leave inconsistent state if the second save fails.

**Devin Prompt**:
```
Implement a basic saga pattern for fund transfer and utility payment orchestration:

1. In FundTransferService.fundTransfer():
   - Wrap the Feign call in a try-catch
   - On Feign success: update local entity to SUCCESS
   - On Feign failure: update local entity to FAILED, log the error
   - On any exception after Feign success (e.g., local save failure): log a
     CRITICAL alert for manual reconciliation

2. Same pattern for UtilityPaymentService.utilPayment()

3. Add a FAILED status handling path that includes the error message

4. Create a scheduled reconciliation job that checks for PENDING records older
   than 5 minutes and marks them for investigation

This is a simplified approach. Document that a full distributed transaction solution
(e.g., Saga with event sourcing) should be considered for production.
```

---

### 2.4 Create a Shared Common Module (High | Medium)

**Gap**: Duplicated code across services with no shared library.

**Devin Prompt**:
```
Create a new Gradle module called 'internet-banking-common' at the project root:

1. Create a root settings.gradle that includes all services as subprojects
2. Move these shared classes into the common module:
   - AuditAware
   - BaseMapper
   - ErrorResponse
   - SimpleBankingGlobalException
   - EntityNotFoundException
   - GlobalExceptionHandler (as a base class)
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter

3. Publish the common module as a local dependency
4. Update each service's build.gradle to depend on the common module:
   implementation project(':internet-banking-common')
5. Remove the duplicated classes from each service
6. Verify all services still compile and tests pass
```

---

### 2.5 Add Pagination Response Wrapper (High | Small)

**Gap**: Paginated endpoints return raw lists without metadata.

**Devin Prompt**:
```
Create a generic PagedResponse<T> wrapper class in the common module (or in each
service until the common module exists):

public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}

Update all paginated GET endpoints (UserController.readUsers, FundTransferController
.readFundTransfers, UtilityPaymentController.readPayments, core-banking
UserController.readUsers) to return PagedResponse<T> instead of List<T>. Map from
Spring Data's Page<Entity> to PagedResponse<DTO>.
```

---

### 2.6 Add Unit Tests for All Services (Critical | Large)

**Gap**: Only core-banking-service has meaningful tests.

**Devin Prompt**:
```
Add comprehensive unit tests for all service classes:

1. internet-banking-user-service UserServiceTest:
   - Test createUser happy path (new user, valid core banking record)
   - Test createUser with already registered email
   - Test createUser with mismatched email
   - Test createUser with invalid identification
   - Test readUsers pagination
   - Test readUser by ID
   - Test updateUser approval flow (verify Keycloak enable/verify calls)

2. internet-banking-fund-transfer-service FundTransferServiceTest:
   - Test fundTransfer happy path
   - Test fundTransfer when core banking returns error
   - Test readAllTransfers pagination

3. internet-banking-utility-payment-service UtilityPaymentServiceTest:
   - Test utilPayment happy path
   - Test utilPayment when core banking returns error
   - Test readPayments pagination

Use Mockito to mock Feign clients and repositories. Target 80%+ line coverage
for all service classes.
```

---

### 2.7 Add Rate Limiting to API Gateway (High | Medium)

**Gap**: No rate limiting on financial transaction endpoints.

**Devin Prompt**:
```
Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in
RequestRateLimiter filter:

1. Add Redis dependency:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Add a Redis container to docker-compose.yml

3. Configure rate limiting in the gateway's application.yml for:
   - /fund-transfer/**: 10 requests/second per user
   - /utility-payment/**: 10 requests/second per user
   - /user/api/v1/bank-users/register: 3 requests/minute per IP

4. Use the authenticated principal (JWT subject) as the key resolver for
   authenticated endpoints, and client IP for unauthenticated endpoints

5. Return HTTP 429 with a Retry-After header when limits are exceeded
```

---

### 2.8 Add Structured JSON Logging (Medium | Small)

**Gap**: Plain-text logging not suitable for log aggregation.

**Devin Prompt**:
```
Add structured JSON logging to all services:

1. Add to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a shared logback-spring.xml that:
   - Uses LogstashEncoder for JSON output in docker/production profiles
   - Uses standard ConsoleAppender for local development
   - Includes MDC fields: traceId, spanId, serviceName

3. Place the logback-spring.xml in each service's src/main/resources/

4. Verify that Zipkin trace IDs appear in the JSON log output
```

---

### 2.9 Add Custom Business Metrics (Medium | Medium)

**Gap**: No custom Micrometer metrics for business-critical operations.

**Devin Prompt**:
```
Add custom Micrometer metrics to track key business operations:

1. In core-banking TransactionService:
   - Counter: banking.fund.transfer.total (tags: status=success|failure)
   - Counter: banking.utility.payment.total (tags: status=success|failure)
   - Timer: banking.fund.transfer.duration
   - Gauge: banking.account.balance (per account type)

2. In internet-banking-user-service UserService:
   - Counter: banking.user.registration.total (tags: status=success|failure)
   - Counter: banking.user.approval.total

3. In fund-transfer and utility-payment services:
   - Timer: banking.feign.core.banking.duration (track Feign call latency)

Inject MeterRegistry into each service class and record metrics at appropriate
points. Verify metrics appear at /actuator/prometheus endpoint.
```

---

### 2.10 Add Integration Tests with TestContainers (High | Large)

**Gap**: No integration tests exist.

**Devin Prompt**:
```
Add integration tests using TestContainers for core-banking-service:

1. Add testImplementation dependencies:
   - org.testcontainers:mysql:1.19.7
   - org.testcontainers:junit-jupiter:1.19.7

2. Create a base test class with @Testcontainers that starts a MySQL container
   and configures Spring datasource properties

3. Write integration tests for:
   - AccountController: GET bank account, GET utility account (happy + not found)
   - TransactionController: POST fund transfer (happy + insufficient funds)
   - UserController: GET user by identification, GET users paginated

4. Verify Flyway migrations run correctly against real MySQL

5. Add a similar pattern for internet-banking-user-service (with MySQL container
   + WireMock for Keycloak and core-banking Feign stubs)
```

---

## Phase 3: Polish (Maintainability, DX, and long-term improvements)

### 3.1 Establish Multi-Project Gradle Build (Medium | Medium)

**Gap**: No unified build configuration.

**Devin Prompt**:
```
Convert the project to a Gradle multi-project build:

1. Create a root build.gradle with shared configuration:
   - Common plugin versions (Spring Boot 3.2.4, dependency-management 1.1.4)
   - Common Java 21 source compatibility
   - Common test configuration (useJUnitPlatform)
   - Common repositories (mavenCentral)

2. Create a root settings.gradle including all 7 services + the common module

3. Update each service's build.gradle to remove duplicated configuration and
   inherit from the root

4. Verify all services build from the root: ./gradlew build

5. Add a root .gitignore covering all Gradle build directories
```

---

### 3.2 Standardize Package Structure (Low | Small)

**Gap**: Inconsistent package naming across services.

**Devin Prompt**:
```
Standardize package structure across all services to follow this convention:
  com.javatodev.finance
    /configuration  - Spring config classes, filters, security
    /controller     - REST controllers
    /service        - Business logic
    /service/rest   - Feign clients
    /model/entity   - JPA entities
    /model/dto      - Request/response DTOs
    /model/mapper   - Entity-DTO mappers
    /model/enums    - Enum types
    /repository     - Spring Data repositories
    /exception      - Exception classes and handlers

Move classes that don't follow this convention. Update all import statements.
Verify compilation and tests pass.
```

---

### 3.3 Normalize RESTful URL Patterns (Medium | Medium)

**Gap**: Non-standard URL patterns.

**Devin Prompt**:
```
Refactor URL patterns to follow REST conventions:

1. internet-banking-user-service:
   - POST /api/v1/bank-users/register -> POST /api/v1/bank-users
   - PATCH /api/v1/bank-users/update/{id} -> PATCH /api/v1/bank-users/{id}

2. core-banking-service:
   - GET /api/v1/account/bank-account/{account_number} -> GET /api/v1/accounts/{accountNumber}
   - GET /api/v1/account/util-account/{account_name} -> GET /api/v1/utility-accounts/{providerName}

3. Update all Feign client interfaces and API Gateway route configurations
   to use the new paths

4. Update the Postman collection if present

5. Consider adding @Deprecated on old endpoints temporarily for backward
   compatibility if there are external consumers
```

---

### 3.4 Add Contract Tests Between Services (High | Large)

**Gap**: No contract tests for Feign client compatibility.

**Devin Prompt**:
```
Add Spring Cloud Contract tests between services:

1. In core-banking-service (provider side):
   - Add spring-cloud-starter-contract-verifier dependency
   - Write contracts in src/test/resources/contracts/ for:
     - GET /api/v1/user/{identification} (found + not found)
     - GET /api/v1/account/bank-account/{account_number} (found + not found)
     - POST /api/v1/transaction/fund-transfer (success + insufficient funds)
     - POST /api/v1/transaction/util-payment (success + insufficient funds)
   - Generate and run contract tests

2. In consumer services (fund-transfer, utility-payment, user-service):
   - Add spring-cloud-contract-stub-runner dependency
   - Write @AutoConfigureStubRunner integration tests that verify Feign clients
     work correctly against the contract stubs

3. Set up stub artifact publishing so consumer tests can pull stubs automatically
```

---

### 3.5 Add a Standard Response Envelope (Medium | Medium)

**Gap**: Inconsistent response structure between success and error cases.

**Devin Prompt**:
```
Create a standard API response wrapper:

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorResponse error;
    private String traceId;
    private Instant timestamp;
}

1. Create this class in the common module
2. Create a utility method: ApiResponse.ok(data) and ApiResponse.error(code, message)
3. Update all controllers to wrap responses in ApiResponse
4. Update all GlobalExceptionHandlers to return ApiResponse with error details
5. Update integration tests and Postman collection for the new response format
```

---

### 3.6 Fix Keycloak Singleton Thread Safety (Medium | Small)

**Gap**: Non-thread-safe static singleton for Keycloak client.

**Devin Prompt**:
```
In internet-banking-user-service KeycloakProperties.java, replace the
manual static singleton pattern with a proper Spring @Bean configuration:

1. Remove the static keycloakInstance field and getInstance() method
2. Create a new @Configuration class KeycloakConfig that produces a @Bean
   Keycloak instance using KeycloakBuilder
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance()
4. This leverages Spring's singleton scope for thread-safe initialization
```

---

### 3.7 Implement Notification Service (Medium | Large)

**Gap**: Notification service is referenced in README but not implemented.

**Devin Prompt**:
```
Create the internet-banking-notification-service:

1. Create a new Spring Boot service with RabbitMQ consumer support:
   - spring-boot-starter-amqp
   - spring-boot-starter-mail (for email notifications)

2. Add RabbitMQ to docker-compose.yml

3. In fund-transfer and utility-payment services, publish messages to
   RabbitMQ after successful transactions:
   - Queue: banking.notifications
   - Message: { type, transactionId, recipientEmail, amount, timestamp }

4. In notification-service, consume messages and send email notifications

5. Register with Eureka and Config Server like other services

6. Add to docker-compose.yml with appropriate wait-for-it dependencies
```

---

### 3.8 Add CI/CD Pipeline (Medium | Medium)

**Gap**: No GitHub Actions workflows exist.

**Devin Prompt**:
```
Create a GitHub Actions CI pipeline in .github/workflows/ci.yml:

1. Trigger on push to main and pull requests
2. Matrix strategy for all 7 services
3. Steps per service:
   - Checkout code
   - Set up JDK 21 (Eclipse Temurin)
   - Cache Gradle dependencies
   - Run ./gradlew build (compile + test)
   - Run ./gradlew dependencyCheckAnalyze (if OWASP plugin is added)
   - Upload test reports as artifacts

4. Add a Docker build step that builds images for all services
5. Add branch protection rules recommendation in the PR description
```

---

### 3.9 Add Health Check Configuration (Medium | Small)

**Gap**: Actuator health endpoints not explicitly configured.

**Devin Prompt**:
```
Configure Spring Boot Actuator health checks for all services:

1. In each service's application.yml, add:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus
     endpoint:
       health:
         show-details: when-authorized
         probes:
           enabled: true
     health:
       db:
         enabled: true
       diskspace:
         enabled: true

2. For services with database connections, verify the DB health indicator works
3. For the API Gateway, add a custom health indicator that checks Eureka connectivity
4. Add healthcheck directives to docker-compose.yml for each service container
```

---

### 3.10 Document API Versioning Strategy (Low | Small)

**Gap**: No documented versioning strategy.

**Devin Prompt**:
```
Create an ADR (Architecture Decision Record) at docs/adr/001-api-versioning.md
documenting the API versioning strategy:

1. Decision: URL path versioning (/api/v1/, /api/v2/)
2. Rules:
   - Breaking changes require a new version
   - Old versions deprecated with X-API-Deprecated header
   - Minimum 6-month deprecation window
   - Non-breaking additions (new fields, new endpoints) don't require versioning
3. Migration guide template for consumers

Also add an ADR template at docs/adr/000-template.md for future decisions.
```

---

## Summary Timeline

| Phase | Items | Critical Fixes | Estimated Effort |
|-------|-------|---------------|------------------|
| **Phase 1: Quick Wins** | 12 items | 5 | 1-2 weeks |
| **Phase 2: Important** | 10 items | 3 | 3-5 weeks |
| **Phase 3: Polish** | 10 items | 0 | 4-6 weeks |
| **Total** | **32 items** | **8** | **8-13 weeks** |

### Recommended Execution Order Within Phase 1

1. **1.1** Fix balance calculation bug (functional correctness)
2. **1.4** Prevent password leakage (security)
3. **1.2** Stop leaking stack traces (security)
4. **1.3** Add input validation (security)
5. **1.6** Use correct HTTP status codes (API correctness)
6. **1.5** Externalize secrets (security hygiene)
7. **1.8** Fix logging issues (observability)
8. **1.7** Add timeouts and retries (resilience)
9. **1.9** Fix OpenAPI dependency (correctness)
10. **1.10** Add ResponseEntity type parameters (code quality)
11. **1.11** Add vulnerability scanning (security)
12. **1.12** Add trace ID to error responses (observability)
