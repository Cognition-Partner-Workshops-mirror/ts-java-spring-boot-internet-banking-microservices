# Remediation Roadmap

This roadmap organizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to implement the fix.

---

## Phase 1: Quick Wins (Critical Fixes & Low-Effort Improvements)

These items address production risks and can be completed quickly.

### 1.1 Fix Balance Calculation Bug
**Gap ref:** Knowledge Base 4.1, Gap Analysis 7.8 | **Severity: Critical** | **Effort: Small**

The `availableBalance` calculation in `TransactionService` double-deducts/double-adds amounts.

<details>
<summary>Sample Devin Prompt</summary>

> In `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`, fix the `internalFundTransfer` method. The current code sets `availableBalance = actualBalance - amount` AFTER `actualBalance` has already been reduced. The correct logic is:
> 1. For the FROM account: `fromBankAccount.setActualBalance(fromBankAccount.getActualBalance().subtract(fundTransferDto.getAmount()))` then `fromBankAccount.setAvailableBalance(fromBankAccount.getActualBalance())` (available = actual after debit).
> 2. For the TO account: `toBankAccount.setActualBalance(toBankAccount.getActualBalance().add(fundTransferDto.getAmount()))` then `toBankAccount.setAvailableBalance(toBankAccount.getActualBalance())`.
> Also fix the same pattern in `utilPayment`. Add unit tests for both methods to verify correct balance after transfer and payment.

</details>

### 1.2 Add Input Validation to All Request DTOs
**Gap ref:** 4.2 | **Severity: Critical** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Add Bean Validation annotations to all request DTOs across all services:
>
> 1. In `FundTransferRequest`: Add `@NotBlank` to `fromAccount` and `toAccount`, `@NotNull @DecimalMin("0.01")` to `amount`.
> 2. In `UtilityPaymentRequest`: Add `@NotNull` to `providerId`, `@NotNull @DecimalMin("0.01")` to `amount`, `@NotBlank` to `account`.
> 3. In `User` DTO (user-service): Add `@NotBlank @Email` to `email`, `@NotBlank` to `identification`, `@NotBlank @Size(min=8)` to `password`.
> 4. In all controllers, add `@Valid` before `@RequestBody` parameters.
> 5. Add `spring-boot-starter-validation` to `build.gradle` for each service if not already present.
> 6. Update `GlobalExceptionHandler` in each service to handle `MethodArgumentNotValidException` and return a 400 response with field-level error details.
> 7. Add unit tests for validation using `@WebMvcTest`.

</details>

### 1.3 Add Idempotency Keys to Financial Endpoints
**Gap ref:** 7.7 | **Severity: Critical** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Implement idempotency for fund transfer and utility payment endpoints:
>
> 1. Add an `idempotencyKey` field (UUID, unique) to `FundTransferEntity` and `UtilityPaymentEntity`.
> 2. Add a `@Column(unique = true)` constraint and a Flyway migration for each service to add the column.
> 3. In `FundTransferRequest` and `UtilityPaymentRequest`, add an optional `idempotencyKey` field. If not provided by the client, generate one server-side.
> 4. Before processing, check if a record with the same `idempotencyKey` already exists. If it does, return the existing result instead of processing again.
> 5. Add a unique index on `idempotency_key` in each table.
> 6. Write unit tests that verify: (a) first request processes normally, (b) duplicate request returns same result without reprocessing.

</details>

### 1.4 Fix HTTP Status Codes in Exception Handlers
**Gap ref:** 2.1 | **Severity: High** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Update `GlobalExceptionHandler` in all services (core-banking, user, fund-transfer, utility-payment):
>
> 1. Map `EntityNotFoundException` to `404 Not Found` (currently returns 400).
> 2. Map `InsufficientFundsException` to `422 Unprocessable Entity`.
> 3. Map `UserAlreadyRegisteredException` to `409 Conflict`.
> 4. Add a catch-all `@ExceptionHandler(Exception.class)` that returns `500 Internal Server Error` with a generic message (never expose stack traces).
> 5. Ensure all error responses use the structured `ErrorResponse` format (code + message), not raw strings.
> 6. Remove the existing `handleException(Exception e)` method that returns `"Exception occur inside API " + e`.
> 7. Verify existing unit tests still pass and add new tests for each exception type.

</details>

### 1.5 Add Feign Error Decoders to All Services
**Gap ref:** 2.4 | **Severity: High** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> The user-service already has a `CustomFeignErrorDecoder` that extracts structured errors from core-banking responses. Port this to the other two Feign-consuming services:
>
> 1. Copy `CustomFeignErrorDecoder` from `internet-banking-user-service` to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`.
> 2. Register it in each service's `CustomFeignClientConfiguration` as a `@Bean`.
> 3. Fix the NPE bug: in `extractBankingCoreGlobalException()`, if `exceptionMessage` is null after parsing, throw a fallback exception with the HTTP status code and raw body.
> 4. Ensure the `@FeignClient` annotations reference the configuration class.
> 5. Add unit tests for the error decoder.

</details>

### 1.6 Handle Failed Feign Calls in Transfer/Payment Services
**Gap ref:** 2.5 | **Severity: Critical** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`:
>
> 1. Wrap the Feign client call in a try-catch block.
> 2. On any exception, update the local entity status to `FAILED` and persist the error message.
> 3. Add a `failureReason` column (VARCHAR 500) to both `fund_transfer` and `utility_payment` tables via Flyway migration.
> 4. Re-throw the exception after updating the status so the controller can return the appropriate error response.
> 5. Write unit tests that mock a Feign failure and verify the entity is updated to FAILED.

</details>

### 1.7 Move Secrets to Environment Variables
**Gap ref:** 4.1 | **Severity: Critical** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Remove all hardcoded credentials from the repository:
>
> 1. In `docker-compose/docker-compose.yml`, replace all hardcoded passwords with environment variable references:
>    - `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`
>    - `KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}`
>    - etc.
> 2. Create a `docker-compose/.env.example` file listing all required variables with placeholder values and comments.
> 3. Add `.env` to `.gitignore`.
> 4. In `docker-compose/mysql/privileges.sql`, use a placeholder that is replaced at startup, or document that the password should be changed.
> 5. Update the README with instructions for setting up the `.env` file.
> 6. Do NOT commit any real credentials.

</details>

### 1.8 Configure Feign Timeouts
**Gap ref:** 7.3 | **Severity: High** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Add Feign timeout configuration to all services that use Feign clients (fund-transfer, utility-payment, user-service):
>
> 1. In each service's `application.yml` (or the Config Server's configuration), add:
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
> 2. For core-banking-service calls specifically, set a read-timeout of 10 seconds (financial operations).
> 3. Add these to the Config Server's Git-backed configuration files so they are centrally managed.

</details>

### 1.9 Add Retry Policies for Feign Clients
**Gap ref:** 7.2 | **Severity: High** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Add retry configuration for Feign clients. Be careful with retries on financial operations (fund transfers should NOT be retried unless idempotency keys are in place):
>
> 1. Add `spring-retry` dependency to `build.gradle` for fund-transfer, utility-payment, and user-service.
> 2. In each service's Feign configuration, add a `Retryer` bean:
>    ```java
>    @Bean
>    public Retryer retryer() {
>        return new Retryer.Default(100, 1000, 3); // 100ms initial, 1s max, 3 attempts
>    }
>    ```
> 3. Only enable retries for GET (read) operations. For POST (write) operations, only retry if idempotency keys are implemented (see 1.3).
> 4. Add a test that verifies retry behavior on transient failure.

</details>

### 1.10 Stop Logging Sensitive Data
**Gap ref:** 4.7 | **Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Remove sensitive data from log statements across all services:
>
> 1. In `FundTransferController`, change `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` to `log.info("Received fund transfer request from={} to={}", fundTransferRequest.getFromAccount(), fundTransferRequest.getToAccount())`.
> 2. In `UserController` (user-service), remove the log that prints the full user registration request (which includes the password).
> 3. In `UtilityPaymentController`, log only the payment provider and account, not the full request object.
> 4. Search all files for `toString()` in log statements and replace with safe field selections.

</details>

### 1.11 Fix OpenAPI Dependency
**Gap ref:** 5.4 | **Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Fix the OpenAPI/Swagger dependency mismatch:
>
> 1. In `core-banking-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, `internet-banking-user-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`: Change `springdoc-openapi-starter-webflux-ui:2.1.0` to `springdoc-openapi-starter-webmvc-ui:2.5.0`.
> 2. In `internet-banking-api-gateway/build.gradle`: Add `springdoc-openapi-starter-webflux-ui:2.5.0` (gateway IS WebFlux-based).
> 3. Verify each service starts and Swagger UI is accessible at `/swagger-ui.html`.
> 4. Update the version to match the latest compatible with Spring Boot 3.2.4.

</details>

---

## Phase 2: Important (Architectural & Quality Improvements)

These items improve maintainability, reliability, and observability.

### 2.1 Add Circuit Breakers with Resilience4j
**Gap ref:** 7.1 | **Severity: Critical** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Add Resilience4j circuit breakers to all Feign client calls:
>
> 1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to `build.gradle` for fund-transfer, utility-payment, and user-service.
> 2. Enable circuit breaker for Feign: `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's config.
> 3. Create fallback classes for each Feign client:
>    - `BankingCoreFeignClientFallback` -- returns a meaningful error response instead of propagating the exception.
> 4. Configure circuit breaker parameters in `application.yml`:
>    ```yaml
>    resilience4j:
>      circuitbreaker:
>        instances:
>          core-banking-service:
>            sliding-window-size: 10
>            failure-rate-threshold: 50
>            wait-duration-in-open-state: 30s
>    ```
> 5. Add health indicator for circuit breaker state.
> 6. Write integration tests that verify circuit opens after failures and returns fallback response.

</details>

### 2.2 Create a Shared Common Library
**Gap ref:** 1.2, 1.3 | **Severity: High** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

> Create a shared `internet-banking-common` module to eliminate code duplication:
>
> 1. Create a new directory `internet-banking-common/` with its own `build.gradle` as a Java library (no Spring Boot plugin, just `java-library` plugin).
> 2. Move these duplicated classes into the common module:
>    - `BaseMapper<E, D>`
>    - `AuditAware` (base entity with audit fields)
>    - `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`
>    - `AuditConfig`, `AuditorAwareConfig`
>    - `ErrorResponse`
>    - `SimpleBankingGlobalException`, `GlobalExceptionHandler`
>    - `GlobalErrorCode`
>    - `TransactionStatus` enum
>    - `CustomFeignClientConfiguration`
> 3. Create a root `settings.gradle` that includes all 7 modules (6 services + common).
> 4. In each service's `build.gradle`, add `implementation project(':internet-banking-common')`.
> 5. Remove the duplicated classes from each service.
> 6. Run all existing tests to verify nothing is broken.

</details>

### 2.3 Add Unit Tests for All Services
**Gap ref:** 3.1 | **Severity: High** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

> Add comprehensive unit tests for the three business services that currently lack them:
>
> **Fund Transfer Service:**
> 1. `FundTransferServiceTest` -- test `fundTransfer()` with mocked Feign client:
>    - Happy path: verify entity saved with SUCCESS, transaction reference stored
>    - Feign failure: verify entity saved with FAILED status
>    - Invalid input: verify validation exception
>
> **Utility Payment Service:**
> 1. `UtilityPaymentServiceTest` -- test `utilPayment()` with mocked Feign client:
>    - Happy path: verify entity saved with SUCCESS
>    - Feign failure: verify entity saved with FAILED status
>
> **User Service:**
> 1. `UserServiceTest` -- test all methods with mocked Keycloak and Feign:
>    - `createUser()`: happy path, duplicate email, email mismatch, user not found
>    - `readUsers()`: paginated results
>    - `updateUser()`: approval flow enables Keycloak account
>
> Use `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`. Follow the test patterns already established in `core-banking-service/src/test/`.

</details>

### 2.4 Add Integration Tests with Testcontainers
**Gap ref:** 3.2 | **Severity: High** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

> Add integration tests for the core-banking-service using Testcontainers:
>
> 1. Add `testcontainers` and `mysql-testcontainers` dependencies to `build.gradle`.
> 2. Create an `AbstractIntegrationTest` base class with `@Testcontainers`, `@Container MySQLContainer`, and `@DynamicPropertySource` to inject the test database URL.
> 3. Write `AccountControllerIntegrationTest` using `@SpringBootTest` + `TestRestTemplate`:
>    - Create account via SQL, then GET `/api/v1/account/bank-account/{number}` and verify response.
> 4. Write `TransactionControllerIntegrationTest`:
>    - Set up two accounts with balances, POST a fund transfer, verify both balances changed correctly.
> 5. Write `UserControllerIntegrationTest`:
>    - Insert a user, GET `/api/v1/user/{identification}`, verify response includes bank accounts.
> 6. Verify Flyway migrations run against the test MySQL container.

</details>

### 2.5 Add Structured Logging
**Gap ref:** 6.1 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Switch all services to structured JSON logging:
>
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` to each service's `build.gradle`.
> 2. Create a `logback-spring.xml` in each service's `src/main/resources/`:
>    ```xml
>    <configuration>
>      <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
>        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
>          <includeMdcKeyName>traceId</includeMdcKeyName>
>          <includeMdcKeyName>spanId</includeMdcKeyName>
>        </encoder>
>      </appender>
>      <root level="INFO">
>        <appender-ref ref="CONSOLE" />
>      </root>
>    </configuration>
>    ```
> 3. Keep a plain-text profile for local development: `<springProfile name="local">` with pattern encoder.
> 4. Verify logs include trace IDs from Micrometer/Brave.

</details>

### 2.6 Add Custom Business Metrics
**Gap ref:** 6.3 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Add custom Micrometer metrics to track key business events:
>
> 1. In `core-banking-service`, add a `MeterRegistry` bean and instrument:
>    - `banking.transfers.total` (counter, tagged by status: success/failed)
>    - `banking.transfers.amount` (distribution summary)
>    - `banking.payments.total` (counter, tagged by status)
>    - `banking.accounts.balance` (gauge, per account type)
> 2. In `internet-banking-user-service`:
>    - `banking.users.registrations` (counter)
>    - `banking.users.approvals` (counter)
> 3. Expose metrics via Actuator at `/actuator/prometheus` by adding `micrometer-registry-prometheus` dependency.
> 4. Verify metrics appear at the Prometheus endpoint.

</details>

### 2.7 Add Rate Limiting to API Gateway
**Gap ref:** 4.6 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Add rate limiting to the Spring Cloud Gateway:
>
> 1. Add `spring-boot-starter-data-redis-reactive` to the API gateway's `build.gradle`.
> 2. Add Redis to `docker-compose.yml` as a new service.
> 3. Configure the `RequestRateLimiter` filter in the gateway routes:
>    ```yaml
>    spring:
>      cloud:
>        gateway:
>          routes:
>            - id: fund-transfer-service
>              uri: lb://internet-banking-fund-transfer-service
>              predicates:
>                - Path=/fund-transfer/**
>              filters:
>                - name: RequestRateLimiter
>                  args:
>                    redis-rate-limiter.replenishRate: 10
>                    redis-rate-limiter.burstCapacity: 20
>                    key-resolver: "#{@userKeyResolver}"
>    ```
> 4. Create a `UserKeyResolver` bean that extracts the user ID from the JWT token.
> 5. Apply appropriate rate limits per route (lower for financial operations, higher for reads).

</details>

### 2.8 Proper Pagination Responses
**Gap ref:** 5.1 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Fix all paginated endpoints to return proper pagination metadata:
>
> 1. Create a generic `PagedResponse<T>` class in the common module:
>    ```java
>    public class PagedResponse<T> {
>        private List<T> content;
>        private int pageNumber;
>        private int pageSize;
>        private long totalElements;
>        private int totalPages;
>        private boolean last;
>    }
>    ```
> 2. Update all service methods that return `List<T>` from paginated queries to return `PagedResponse<T>` instead.
> 3. In each controller, convert the `Page<Entity>` result to `PagedResponse<DTO>`.
> 4. Update existing tests if any assert on the response format.

</details>

### 2.9 Add Dependency Vulnerability Scanning
**Gap ref:** 4.8 | **Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Add OWASP Dependency-Check to the Gradle build:
>
> 1. In each service's `build.gradle`, add the plugin:
>    ```gradle
>    plugins {
>        id 'org.owasp.dependencycheck' version '9.0.9'
>    }
>    ```
> 2. Configure the plugin to fail the build on CVSS score >= 7:
>    ```gradle
>    dependencyCheck {
>        failBuildOnCVSS = 7.0f
>        formats = ['HTML', 'JSON']
>    }
>    ```
> 3. Run `./gradlew dependencyCheckAnalyze` for each service and document any existing vulnerabilities.
> 4. Update `springdoc-openapi` to the latest version compatible with Spring Boot 3.2.4.

</details>

### 2.10 Configure Distributed Tracing Sampling
**Gap ref:** 6.4 | **Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Ensure distributed tracing is fully configured:
>
> 1. In each service's configuration (or Config Server), set:
>    ```yaml
>    management:
>      tracing:
>        sampling:
>          probability: 1.0  # 100% in dev/staging, reduce in production
>      zipkin:
>        tracing:
>          endpoint: http://zipkin:9411/api/v2/spans
>    ```
> 2. Add `logging.pattern.correlation` to include trace IDs in log output:
>    ```yaml
>    logging:
>      pattern:
>        correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}] "
>    ```
> 3. Verify trace propagation works end-to-end: make a request through the gateway, check Zipkin shows the full trace across all services involved.

</details>

---

## Phase 3: Polish (Best Practices & Long-Term Quality)

These items bring the codebase to production-grade standards.

### 3.1 Add Contract Tests Between Services
**Gap ref:** 3.3 | **Severity: Medium** | **Effort: Large**

<details>
<summary>Sample Devin Prompt</summary>

> Implement Spring Cloud Contract tests between services:
>
> 1. Add `spring-cloud-starter-contract-verifier` to `core-banking-service` (provider).
> 2. Write contract DSL files in `src/test/resources/contracts/` for each endpoint:
>    - `fundTransfer.groovy` -- POST /api/v1/transaction/fund-transfer
>    - `utilPayment.groovy` -- POST /api/v1/transaction/util-payment
>    - `readUser.groovy` -- GET /api/v1/user/{identification}
> 3. Generate and run contract tests: `./gradlew contractTest`.
> 4. Publish stubs to a local Maven repository.
> 5. In consumer services (fund-transfer, utility-payment, user), add `spring-cloud-starter-contract-stub-runner` and write `@AutoConfigureStubRunner` tests that verify Feign clients work against the stubs.

</details>

### 3.2 Implement Bulkhead Pattern
**Gap ref:** 7.5 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Add bulkhead isolation for Feign client calls using Resilience4j:
>
> 1. In each service that uses Feign clients, configure thread pool bulkheads:
>    ```yaml
>    resilience4j:
>      thread-pool-bulkhead:
>        instances:
>          core-banking-service:
>            max-thread-pool-size: 10
>            core-thread-pool-size: 5
>            queue-capacity: 20
>    ```
> 2. Annotate Feign client methods with `@Bulkhead(name = "core-banking-service", type = Bulkhead.Type.THREADPOOL)`.
> 3. Add metrics for bulkhead state (active threads, queue size).
> 4. Write a load test that verifies the bulkhead limits concurrent calls.

</details>

### 3.3 Set Up CI/CD Pipeline
**Gap ref:** 6.4 (build) | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Create a GitHub Actions CI/CD pipeline:
>
> 1. Create `.github/workflows/ci.yml` with the following stages:
>    - **Build:** `./gradlew build` for each service
>    - **Test:** `./gradlew test` for each service with test reports
>    - **Dependency Check:** `./gradlew dependencyCheckAnalyze`
>    - **Docker Build:** Build Docker images for each service
> 2. Use a matrix strategy to parallelize builds across services.
> 3. Set up MySQL and Keycloak as GitHub Actions services for integration tests.
> 4. Add caching for Gradle dependencies (`~/.gradle/caches`).
> 5. Add a deployment stage (to a staging environment) that triggers on merge to main.

</details>

### 3.4 Add Filtering and Sorting to List Endpoints
**Gap ref:** 5.5 | **Severity: Low** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Add filtering and sorting to list/paginated endpoints:
>
> 1. In `core-banking-service`, add query parameters to `/api/v1/user`:
>    - `?email=`, `?firstName=`, `?lastName=`
>    - Use Spring Data JPA Specifications or `@Query` with optional parameters.
> 2. In `fund-transfer-service`, add filtering to `/api/v1/transfer`:
>    - `?status=`, `?fromAccount=`, `?toAccount=`, `?dateFrom=`, `?dateTo=`
> 3. In `utility-payment-service`, add filtering to `/api/v1/utility-payment`:
>    - `?status=`, `?providerId=`, `?dateFrom=`, `?dateTo=`
> 4. Document all filter parameters in OpenAPI annotations (`@Parameter`).
> 5. Write tests for each filter combination.

</details>

### 3.5 Fix RESTful URL Conventions
**Gap ref:** 5.3 | **Severity: Low** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Update API endpoints to follow REST conventions:
>
> 1. In `UserController` (user-service):
>    - Change `POST /api/v1/bank-users/register` to `POST /api/v1/bank-users`
>    - Change `PATCH /api/v1/bank-users/update/{id}` to `PATCH /api/v1/bank-users/{id}`
> 2. In `FundTransferController`:
>    - Change `POST /api/v1/transfer` to `POST /api/v1/transfers` (plural)
>    - Change `GET /api/v1/transfer` to `GET /api/v1/transfers`
> 3. In `UtilityPaymentController`:
>    - Change `POST /api/v1/utility-payment` to `POST /api/v1/utility-payments`
>    - Change `GET /api/v1/utility-payment` to `GET /api/v1/utility-payments`
> 4. Update API Gateway route predicates to match new paths.
> 5. Update Postman collection and any documentation.
> 6. Return `201 Created` for all POST endpoints that create resources.

</details>

### 3.6 Add Custom Health Indicators
**Gap ref:** 6.2 | **Severity: Low** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Add custom health indicators to each service:
>
> 1. In services with database access, verify the default `DataSourceHealthIndicator` is enabled in Actuator.
> 2. In `internet-banking-user-service`, add a `KeycloakHealthIndicator` that checks Keycloak connectivity:
>    ```java
>    @Component
>    public class KeycloakHealthIndicator implements HealthIndicator {
>        @Override
>        public Health health() {
>            // Try to read realm info from Keycloak
>            // Return Health.up() or Health.down()
>        }
>    }
>    ```
> 3. In services with Feign clients, add a health indicator that checks if the target service is registered in Eureka.
> 4. Expose health details: `management.endpoint.health.show-details=always` in non-production profiles.

</details>

### 3.7 Make Mappers Spring-Managed Beans
**Gap ref:** 1.6 | **Severity: Low** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Convert all mapper classes to Spring beans:
>
> 1. Add `@Component` to `BankAccountMapper`, `UserMapper`, `UtilityAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`.
> 2. In all service classes, replace `private XMapper mapper = new XMapper()` with `private final XMapper mapper` injected via constructor (already using `@RequiredArgsConstructor`).
> 3. Consider migrating from manual mappers to MapStruct for compile-time type-safe mapping. If so:
>    - Add MapStruct dependency and annotation processor to `build.gradle`.
>    - Create `@Mapper(componentModel = "spring")` interfaces.
> 4. Run all existing tests to verify mappings still work.

</details>

### 3.8 Thread-Safe Keycloak Configuration
**Gap ref:** 4.5 | **Severity: Medium** | **Effort: Small**

<details>
<summary>Sample Devin Prompt</summary>

> Fix the thread-safety issue in `KeycloakProperties`:
>
> 1. In `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`:
>    - Replace the manual singleton `getInstance()` with a proper Spring `@Configuration` + `@Bean` approach.
>    - Or at minimum, make the `keycloak` field `volatile` and use double-checked locking.
> 2. Better approach: Convert `KeycloakManager` to use `@ConfigurationProperties` for Keycloak settings and inject the `Keycloak` instance as a Spring bean.
> 3. Remove the static `getInstance()` pattern entirely.
> 4. Verify Keycloak operations still work in integration tests.

</details>

### 3.9 Unify Build with Root Gradle Project
**Gap ref:** 1.1 | **Severity: Medium** | **Effort: Medium**

<details>
<summary>Sample Devin Prompt</summary>

> Create a root Gradle multi-module build:
>
> 1. Create a root `settings.gradle` that includes all modules:
>    ```gradle
>    rootProject.name = 'internet-banking-microservices'
>    include 'internet-banking-common'
>    include 'core-banking-service'
>    include 'internet-banking-api-gateway'
>    include 'internet-banking-config-server'
>    include 'internet-banking-fund-transfer-service'
>    include 'internet-banking-service-registry'
>    include 'internet-banking-user-service'
>    include 'internet-banking-utility-payment-service'
>    ```
> 2. Create a root `build.gradle` with shared configuration:
>    ```gradle
>    subprojects {
>        group = 'com.javatodev.finance'
>        version = '0.0.1-SNAPSHOT'
>        repositories { mavenCentral() }
>    }
>    ```
> 3. Move common dependency versions to the root `build.gradle` using `ext` or a version catalog.
> 4. Remove individual `gradlew` wrappers from sub-projects (keep only the root one).
> 5. Verify `./gradlew build` from root builds all services.

</details>

---

## Summary

| Phase | Items | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| **Phase 1: Quick Wins** | 11 | 4 | 4 | 2 | 1 |
| **Phase 2: Important** | 10 | 1 | 3 | 6 | 0 |
| **Phase 3: Polish** | 9 | 0 | 0 | 4 | 5 |
| **Total** | **30** | **5** | **7** | **12** | **6** |

### Recommended Execution Order

1. **1.7** Move secrets to env vars (unblocks team immediately)
2. **1.1** Fix balance calculation bug (data integrity)
3. **1.2** Add input validation (security)
4. **1.6** Handle failed Feign calls (data consistency)
5. **1.3** Add idempotency keys (financial safety)
6. **1.4** Fix HTTP status codes
7. **1.5** Add Feign error decoders
8. **1.8** Configure timeouts
9. **1.9** Add retry policies
10. **1.10** Stop logging sensitive data
11. **1.11** Fix OpenAPI dependency
12. **2.1** Add circuit breakers
13. **2.2** Create shared library
14. **2.3-2.4** Add tests
15. **2.5-2.10** Observability and API improvements
16. **3.1-3.9** Polish items
