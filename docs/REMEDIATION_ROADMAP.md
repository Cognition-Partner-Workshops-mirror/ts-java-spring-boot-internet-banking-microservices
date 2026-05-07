# Remediation Roadmap

This roadmap organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, impact, and effort. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (Critical Fixes & Small Effort)

These items address data-integrity bugs, security vulnerabilities, and low-effort improvements that deliver immediate value. Estimated total: **1-2 weeks**.

### 1.1 Fix Balance Calculation Bug
**Gaps:** GAP-RES-006 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted/added during fund transfers and utility payments due to reading the already-mutated `actualBalance`.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `internalFundTransfer()` and `utilPayment()`, the `availableBalance` is being set after `actualBalance` has already been modified, causing a double subtraction/addition. The `availableBalance` should be set to the same value as `actualBalance` after the adjustment (i.e., `setAvailableBalance(getActualBalance())` after the subtraction/addition), or both should be computed from the original values before mutation. Also add unit tests to verify correct balance after transfer.

---

### 1.2 Add Input Validation to All Request DTOs
**Gaps:** GAP-SEC-003 | **Severity:** Critical | **Effort:** Small-Medium

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs across all services. Specifically: (1) In `core-banking-service`, add `@NotBlank` to `FundTransferRequest.fromAccount` and `toAccount`, `@NotNull @Positive` to `amount`. Add similar validations to `UtilityPaymentRequest`. (2) In `internet-banking-fund-transfer-service`, validate `FundTransferRequest` with same constraints. (3) In `internet-banking-utility-payment-service`, validate `UtilityPaymentRequest`. (4) In `internet-banking-user-service`, validate `User` registration (add `@Email` to email, `@NotBlank` to identification and password). (5) Add `@Valid` annotation to all `@RequestBody` parameters in controllers. (6) Add `spring-boot-starter-validation` dependency to each service's `build.gradle` if not present.

---

### 1.3 Fix Error Response Stack Trace Exposure
**Gaps:** GAP-SEC-004, GAP-ERR-001 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Fix the generic exception handler in all services (`GlobalExceptionHandler.java` in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Replace the catch-all `handleException` method body that currently returns `"Exception occur inside API " + e` with a safe `ErrorResponse` that does not expose stack traces. Return an `ErrorResponse` with a generic code like `"INTERNAL_ERROR"` and message `"An unexpected error occurred"`. Log the full exception at ERROR level instead.

---

### 1.4 Add Proper HTTP Status Codes
**Gaps:** GAP-ERR-002 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Update `GlobalExceptionHandler` in all 4 business services to return appropriate HTTP status codes: (1) `EntityNotFoundException` should return 404 Not Found. (2) `InsufficientFundsException` should return 422 Unprocessable Entity. (3) `UserAlreadyRegisteredException` should return 409 Conflict. (4) `InvalidEmailException` and `InvalidBankingUserException` should return 400 Bad Request. (5) The catch-all `Exception` handler should return 500 Internal Server Error. Ensure all responses use the `ErrorResponse` format.

---

### 1.5 Secure Actuator Endpoints
**Gaps:** GAP-OBS-004 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Update the `SecurityConfiguration` in the API Gateway to restrict actuator endpoint access. Change the actuator path matchers from `permitAll()` to require authentication, or restrict to specific safe endpoints only (health, info). Specifically, change `/actuator/**` to only permit `/actuator/health` and `/actuator/info` without authentication, and require authentication for all other actuator endpoints.

---

### 1.6 Fix Keycloak Singleton Thread Safety
**Gaps:** GAP-SEC-006 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Fix the thread-safety issue in `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`. The `getInstance()` method uses a non-synchronized lazy initialization pattern. Either add `synchronized` to the method, use double-checked locking with `volatile`, or refactor to initialize the Keycloak instance as a Spring `@Bean` in a `@Configuration` class.

---

### 1.7 Add Feign Timeout Configuration
**Gaps:** GAP-RES-003 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add explicit timeout configuration for all Feign clients in the fund-transfer, utility-payment, and user services. Add Feign timeout properties via the externalized config (or in `application.yml` fallback): `spring.cloud.openfeign.client.config.default.connect-timeout=5000` and `spring.cloud.openfeign.client.config.default.read-timeout=10000`. Also add gateway route timeouts in the API Gateway configuration.

---

### 1.8 Add Feign Retry Configuration
**Gaps:** GAP-RES-002 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Add a Feign `Retryer` bean to the `CustomFeignClientConfiguration` in the fund-transfer and utility-payment services. Configure it with: initial interval 100ms, max interval 1s, max attempts 3. Example: `@Bean public Retryer retryer() { return new Retryer.Default(100, 1000, 3); }`. Only retry on connection errors and 5xx responses, not on 4xx.

---

### 1.9 Fix Logging Bug and Standardize Logging
**Gaps:** GAP-OBS-001 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Fix the SLF4J logging bug in `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java` line 32. Change `log.info("Sending fund transfer request {}" + request.toString())` to `log.info("Sending fund transfer request {}", request)` (use parameterized logging). Also review all log statements across all services for similar concatenation issues and fix them.

---

### 1.10 Add Type Parameters to ResponseEntity
**Gaps:** GAP-API-001 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add generic type parameters to all `ResponseEntity` return types in controllers across all services. For example, in `AccountController.getBankAccount()`, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. Do this for all controller methods in core-banking-service (AccountController, UserController, TransactionController), fund-transfer-service (FundTransferController), and utility-payment-service (UtilityPaymentController).

---

### 1.11 Fix TransactionEntity JPA Constructors
**Gaps:** GAP-RES-008 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Add `@NoArgsConstructor` and `@AllArgsConstructor` annotations to `TransactionEntity` in `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`. The entity currently has `@Builder` but no explicit constructors, which can cause issues with JPA entity instantiation.

---

### 1.12 Fix OpenAPI Dependency
**Gaps:** GAP-API-006 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In `core-banking-service/build.gradle`, `internet-banking-user-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`, replace `springdoc-openapi-starter-webflux-ui:2.1.0` with `springdoc-openapi-starter-webmvc-ui:2.1.0` since these services use Spring MVC, not WebFlux. The WebFlux variant should only be used in the API Gateway.

---

### 1.13 Return Pagination Metadata
**Gaps:** GAP-API-004 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> Update all paginated list endpoints to return `Page<T>` instead of `List<T>`. In `core-banking-service` `UserController.readUsers()`, return `ResponseEntity<Page<User>>` and change `UserService.readUsers()` to return `Page`. Do the same for `FundTransferController.readFundTransfers()` and `UtilityPaymentController.readPayments()`. This gives clients access to `totalElements`, `totalPages`, `number`, and `size` in the response.

---

## Phase 2: Important (Architectural & Reliability)

These items address significant structural and reliability concerns. Estimated total: **3-6 weeks**.

### 2.1 Add Transaction Failure Handling
**Gaps:** GAP-ERR-005 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Add proper error handling to `FundTransferService.fundTransfer()` in the fund-transfer service. Wrap the Feign call to core-banking in a try-catch block. On any exception: (1) update the `FundTransferEntity` status to `FAILED`, (2) log the error, (3) throw a service-specific exception that will be handled by the GlobalExceptionHandler. Apply the same pattern to `UtilityPaymentService.utilPayment()` in the utility-payment service.

---

### 2.2 Implement Circuit Breakers
**Gaps:** GAP-RES-001 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Add Resilience4j circuit breakers to the fund-transfer and utility-payment services for their Feign calls to core-banking-service. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` to both `build.gradle` files. (2) Configure a circuit breaker with: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. (3) Add a fallback method that updates the transaction status to `FAILED` and returns an error response. (4) Apply the circuit breaker to the Feign client calls using `@CircuitBreaker` annotation or programmatic API.

---

### 2.3 Add Idempotency Protection
**Gaps:** GAP-RES-005 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Implement idempotency protection for fund transfer and utility payment endpoints. (1) Add an `idempotencyKey` field (String) to `FundTransferRequest` and `UtilityPaymentRequest`. (2) In `FundTransferService`, before processing, check if a `FundTransferEntity` with the same idempotency key already exists. If it does, return the existing result instead of processing again. (3) Add a unique constraint on the idempotency key column. (4) Apply the same pattern to `UtilityPaymentService`. (5) Document the idempotency key requirement in the API documentation.

---

### 2.4 Externalize Secrets
**Gaps:** GAP-SEC-001 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> Remove all hardcoded credentials from the codebase and Docker configuration. (1) In `docker-compose.yml` and `docker-compose-support-apps.yml`, replace hardcoded passwords with environment variable references (e.g., `${MYSQL_ROOT_PASSWORD}`). (2) Create a `.env.example` file documenting all required environment variables. (3) Add `.env` to `.gitignore`. (4) Update the `docker-compose/mysql/Dockerfile` to not embed the password via ENV. (5) Remove the test credentials from `README.md` or move them to a separate setup guide that references env vars.

---

### 2.5 Add Feign Error Decoders
**Gaps:** GAP-ERR-004 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add a `CustomFeignErrorDecoder` to both `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`, similar to the one in user-service. The decoder should: (1) Parse the error response body from core-banking-service. (2) Map 404 responses to `EntityNotFoundException`. (3) Map 422 responses to domain-specific exceptions. (4) Map other 4xx/5xx to appropriate exceptions. (5) Register the decoder in `CustomFeignClientConfiguration`.

---

### 2.6 Add Resource-Level Authorization
**Gaps:** GAP-SEC-007 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Implement resource-level authorization to ensure users can only access their own data. (1) In the fund-transfer and utility-payment services, validate that the `X-Auth-Id` header matches the account owner before processing transfers/payments. (2) In the user-service, restrict user profile access to the authenticated user (except for admin operations). (3) Add role-based access control using Keycloak roles: define `ROLE_USER` and `ROLE_ADMIN` in the Keycloak realm, and use `@PreAuthorize` or path-based security rules.

---

### 2.7 Secure Internal Service Communication
**Gaps:** GAP-SEC-008 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
> Add authentication for internal service-to-service communication. The core-banking-service endpoints should not be publicly accessible without authentication. Options: (1) Add Spring Security to core-banking-service with JWT validation for the `X-Auth-Id` header or a shared service token. (2) Alternatively, propagate the original JWT from the API Gateway through Feign calls using a `RequestInterceptor` that adds the Authorization header. Update `CustomFeignClientConfiguration` in all Feign clients to include this interceptor.

---

### 2.8 Create Shared Common Library
**Gaps:** GAP-ORG-002 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Create a shared `banking-common` library module and extract duplicated code. (1) Create a new directory `banking-common/` with its own `build.gradle` configured as a plain Java library (not a Spring Boot application). (2) Move common classes into it: `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `AuditAware`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `CustomFeignClientConfiguration`. (3) Update all services' `build.gradle` to depend on `banking-common`. (4) Remove the duplicated classes from individual services.

---

### 2.9 Add Unit and Integration Tests
**Gaps:** GAP-TEST-001, GAP-TEST-002 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
> Add comprehensive tests to all services. For each of the 4 business services: (1) Add controller tests using `@WebMvcTest` with MockMvc to test request validation, response serialization, and error handling. (2) Add service-layer unit tests with mocked dependencies. (3) Add repository integration tests using `@DataJpaTest` with H2 for core-banking-service (already has H2 in test deps). (4) For Feign clients, add tests using WireMock to verify request/response mapping. Target at least 70% line coverage for service and controller layers.

---

### 2.10 Add Transaction Boundaries to Orchestration Services
**Gaps:** GAP-RES-007 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
> Review and fix transaction management in the orchestration services. In `FundTransferService`, the local DB save and the Feign call should be properly coordinated: (1) Save the initial `PENDING` entity in a separate transaction before the Feign call. (2) Update the status in a separate transaction after the Feign call succeeds or fails. This prevents the Feign call from being executed inside a DB transaction (which holds connections during external calls). Consider using `TransactionTemplate` or `@Transactional(propagation = REQUIRES_NEW)` for the save operations.

---

### 2.11 Add Rate Limiting
**Gaps:** GAP-SEC-005 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's built-in `RequestRateLimiter` filter. (1) Add `spring-boot-starter-data-redis-reactive` dependency to the gateway. (2) Configure a Redis-backed rate limiter with: replenish rate = 10 requests/second, burst capacity = 20. (3) Apply rate limiting to sensitive endpoints: `/fund-transfer/**` and `/utility-payment/**`. (4) Add Redis to the docker-compose configuration.

---

### 2.12 Add Custom Business Metrics
**Gaps:** GAP-OBS-003 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Add Micrometer custom metrics to business services. (1) Add `micrometer-registry-prometheus` dependency to all services. (2) In core-banking TransactionService, add counters for `banking.transfers.total` (tagged by status: success/failed) and `banking.payments.total`. Add a timer for `banking.transfers.duration`. (3) In fund-transfer and utility-payment services, add similar counters. (4) Configure Prometheus scrape endpoints via Actuator. (5) Add a `prometheus.yml` configuration file for a Prometheus container in docker-compose.

---

## Phase 3: Polish (Quality of Life & Long-term)

These items improve developer experience, documentation, and long-term maintainability. Estimated total: **4-8 weeks**.

### 3.1 Set Up Multi-Module Gradle Build
**Gaps:** GAP-ORG-001 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Convert the project from 6 independent Gradle projects to a single multi-module Gradle build. (1) Create a root `settings.gradle` that includes all services and the common library as subprojects. (2) Create a root `build.gradle` with shared configuration (Java version, Spring Boot version, common dependencies). (3) Remove individual `gradlew` scripts and `gradle/wrapper` directories from subprojects. (4) Ensure `./gradlew build` from the root builds all services.

---

### 3.2 Add Contract Tests
**Gaps:** GAP-TEST-003 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Add Spring Cloud Contract tests between services. (1) In core-banking-service (the provider), add `spring-cloud-starter-contract-verifier` and define contracts for: GET bank-account, GET user, POST fund-transfer, POST util-payment. (2) In fund-transfer-service and utility-payment-service (consumers), add `spring-cloud-starter-contract-stub-runner` and write consumer-side tests that verify Feign clients work with the contract stubs. (3) Configure the contract test plugin to generate stubs that can be published for consumer testing.

---

### 3.3 Add CI/CD Pipeline
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline. Add `.github/workflows/ci.yml` that: (1) Triggers on push to main and pull requests. (2) Sets up JDK 21 and Gradle. (3) Runs `./gradlew build` for all services (or from root if multi-module). (4) Runs unit and integration tests. (5) Publishes test results. (6) Builds Docker images. (7) Optionally pushes images to a container registry on main branch.

---

### 3.4 Add Fallback Behavior for Degraded Mode
**Gaps:** GAP-RES-004 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> Implement fallback behavior for when core-banking-service is unavailable. (1) For read operations (account lookup), consider adding a local cache (Spring Cache with Caffeine) that serves recently fetched account data when core-banking is down. (2) For write operations (fund transfer, utility payment), the circuit breaker fallback should queue the request (in-memory or persistent queue) and return a `PENDING` response with a reference ID. (3) Add a scheduled job to process queued requests when core-banking becomes available.

---

### 3.5 Implement RabbitMQ Notification Service
**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Implement the pending Notification Service using RabbitMQ. (1) Add `spring-boot-starter-amqp` to fund-transfer and utility-payment services. (2) After successful fund transfer or utility payment, publish a notification event to a RabbitMQ exchange. (3) Create a new `internet-banking-notification-service` that consumes messages from the queue and logs notifications (as a placeholder for email/SMS). (4) Add RabbitMQ to docker-compose. (5) Configure the notification service to register with Eureka and use the Config Server.

---

### 3.6 Standardize API Resource Naming
**Gaps:** GAP-API-003 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize API resource naming conventions across all services to follow REST best practices. Rename endpoints: (1) `/api/v1/bank-users` -> `/api/v1/users` (2) `/api/v1/transfer` -> `/api/v1/transfers` (3) `/api/v1/utility-payment` -> `/api/v1/utility-payments` (4) `/api/v1/account/bank-account` -> `/api/v1/accounts/bank` (5) `/api/v1/account/util-account` -> `/api/v1/accounts/utility`. Update all Feign clients and API Gateway routes accordingly. Add redirect rules for backward compatibility.

---

### 3.7 Add Filtering and Search to List Endpoints
**Gaps:** GAP-API-005 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
> Add query parameter-based filtering to all list endpoints. (1) For fund transfers: filter by `fromAccount`, `toAccount`, `status`, `dateRange` (createdDate between). (2) For utility payments: filter by `account`, `providerId`, `status`, `dateRange`. (3) For users: filter by `status`, `email` (partial match). (4) Use Spring Data JPA Specifications or QueryDSL for dynamic query building. (5) Update OpenAPI annotations to document the filter parameters.

---

### 3.8 Add Centralized Log Aggregation
**Gaps:** GAP-OBS-005 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> Set up centralized log aggregation using the ELK stack. (1) Add Elasticsearch and Kibana containers to docker-compose. (2) Add Logstash or Filebeat for log collection. (3) Configure all services to output structured JSON logs using Logback's `LogstashEncoder`. (4) Add a `logback-spring.xml` configuration file to each service. (5) Include trace ID and span ID in log output for correlation with Zipkin.

---

### 3.9 Standardize Package Structure
**Gaps:** GAP-ORG-003 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
> Standardize the package structure across all services to follow a consistent pattern. For each service, use: `controller/`, `service/`, `repository/`, `model/entity/`, `model/dto/`, `model/mapper/`, `configuration/`, `exception/`. Move the `model/repository/` classes in user-service and fund-transfer-service to top-level `repository/` package. Standardize Feign client location to `client/` package.

---

### 3.10 Use MapStruct for Object Mapping
**Gaps:** GAP-ORG-004 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
> Replace the manual mapper classes with MapStruct. (1) Add MapStruct dependency to all services: `implementation 'org.mapstruct:mapstruct:1.5.5.Final'` and `annotationProcessor 'org.mapstruct:mapstruct-processor:1.5.5.Final'`. (2) Convert `BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`, and `UtilityAccountMapper` to MapStruct interfaces using `@Mapper` annotation. (3) Remove the manual `BaseMapper` abstract class. (4) Register MapStruct mappers as Spring beans using `componentModel = "spring"`.

---

## Phase Summary

| Phase | Items | Critical | High | Medium | Low | Estimated Duration |
|-------|-------|----------|------|--------|-----|--------------------|
| **Phase 1: Quick Wins** | 13 | 2 | 5 | 6 | 0 | 1-2 weeks |
| **Phase 2: Important** | 12 | 3 | 6 | 3 | 0 | 3-6 weeks |
| **Phase 3: Polish** | 10 | 0 | 0 | 6 | 4 | 4-8 weeks |
| **Total** | **35** | **5** | **11** | **15** | **4** | **8-16 weeks** |

---

## Recommended Execution Order

Within each phase, prioritize in this order:

**Phase 1 (first week):**
1. Fix balance calculation bug (GAP-RES-006) - **financial data integrity**
2. Add input validation (GAP-SEC-003) - **injection prevention**
3. Fix error response stack trace exposure (GAP-SEC-004) - **information leak**
4. Add proper HTTP status codes (GAP-ERR-002) - **API correctness**
5. Secure actuator endpoints (GAP-OBS-004) - **information leak**
6. Fix Keycloak thread safety (GAP-SEC-006) - **race condition**

**Phase 1 (second week):**
7. Add Feign timeouts (GAP-RES-003)
8. Add Feign retry (GAP-RES-002)
9. Fix logging bug (GAP-OBS-001)
10. Add ResponseEntity types (GAP-API-001)
11. Fix TransactionEntity constructors (GAP-RES-008)
12. Fix OpenAPI dependency (GAP-API-006)
13. Return pagination metadata (GAP-API-004)

**Phase 2 (weeks 3-8):**
1. Transaction failure handling (GAP-ERR-005)
2. Circuit breakers (GAP-RES-001)
3. Idempotency protection (GAP-RES-005)
4. Externalize secrets (GAP-SEC-001)
5. Feign error decoders (GAP-ERR-004)
6. Resource-level authorization (GAP-SEC-007)
7. Secure internal communication (GAP-SEC-008)
8. Shared common library (GAP-ORG-002)
9. Unit and integration tests (GAP-TEST-001/002)
10. Transaction boundaries (GAP-RES-007)
11. Rate limiting (GAP-SEC-005)
12. Custom business metrics (GAP-OBS-003)

**Phase 3 (weeks 9-16):**
- Multi-module build, contract tests, CI/CD, fallback behavior, notification service, API naming, filtering, log aggregation, package structure, MapStruct.
