# Internet Banking Microservices — Remediation Roadmap

## Overview

This roadmap prioritizes the 38 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins** (1-2 weeks): Low-effort, high-impact fixes. Mostly Small effort items that address Critical and High severity gaps.
- **Phase 2 — Important** (3-6 weeks): Medium-effort items that significantly improve quality, security, and reliability.
- **Phase 3 — Polish** (6-12 weeks): Larger structural improvements and advanced capabilities.

Each item includes a **sample Devin prompt** that can be used to execute the remediation directly.

---

## Phase 1: Quick Wins

*Estimated timeline: 1-2 weeks*
*Focus: Fix critical security issues, stop leaking errors, fix logging bugs, add basic resilience*

### 1.1 Fix Exception Details Leaked to Clients (GAP-ERR-002)
**Severity: High | Effort: Small**

Replace the catch-all exception handler in all 4 services to return a generic error message instead of the exception object.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in all four services (`core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, `internet-banking-user-service`). Change the catch-all `@ExceptionHandler({Exception.class})` method to: (1) log the full exception server-side with `log.error("Unexpected error", e)`, (2) return HTTP 500 with an `ErrorResponse` body containing code `"INTERNAL_ERROR"` and message `"An unexpected error occurred. Please try again later."`. Do not expose the exception details in the response. Add `@Slf4j` annotation if not already present. Open a PR with the changes.

---

### 1.2 Standardize Error Response Format (GAP-ERR-003)
**Severity: High | Effort: Small**

Unify `ErrorResponse` across all services and add timestamp + path.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a consistent `ErrorResponse` class in each service's `exception` package with fields: `String code`, `String message`, `LocalDateTime timestamp`, `String path`. Use `@Builder` pattern consistently. Update all `GlobalExceptionHandler` classes to use this format. Ensure the `ErrorResponse` is identical across all services. Open a PR.

---

### 1.3 Map Exceptions to Correct HTTP Status Codes (GAP-ERR-001)
**Severity: Critical | Effort: Medium**

Update all `GlobalExceptionHandler` classes to return appropriate HTTP status codes.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` in all four services to map exceptions to proper HTTP status codes: `EntityNotFoundException` -> 404, `InsufficientFundsException` -> 422, `UserAlreadyRegisteredException` -> 409, `InvalidEmailException` / `InvalidBankingUserException` -> 400, `SimpleBankingGlobalException` (generic) -> 400, and unhandled `Exception` -> 500. Add specific `@ExceptionHandler` methods for each exception type with the correct `ResponseEntity.status()`. Open a PR.

---

### 1.4 Remove Hard-Coded Credentials from Docker Compose (GAP-SEC-001)
**Severity: Critical | Effort: Small**

Extract credentials to a `.env` file referenced by Docker Compose.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a `docker-compose/.env.example` file with placeholder values for `MYSQL_ROOT_PASSWORD`, `KC_DB_PASSWORD`, `KC_DB_USERNAME`, `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB`. Update `docker-compose.yml` and `docker-compose-support-apps.yml` to use `${VARIABLE_NAME}` syntax referencing these environment variables. Add `docker-compose/.env` to `.gitignore`. Remove the test credentials from `README.md` and replace with a note to copy `.env.example` to `.env`. Open a PR.

---

### 1.5 Remove Test Credentials from README (GAP-SEC-002)
**Severity: Critical | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, remove the plaintext test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`) from `README.md`. Replace the test data section with instructions to check the `.env.example` file and Keycloak realm import for credential setup. Open a PR.

---

### 1.6 Fix Logging Bugs — String Concatenation Instead of Placeholders (GAP-OBS-001)
**Severity: High | Effort: Small**

Fix the string concatenation bugs in log statements.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, find and fix all logging statements that use string concatenation (`+`) instead of SLF4J parameterized logging (`{}`). Specifically fix: (1) `FundTransferService.java` line `log.info("Sending fund transfer request {}" + request.toString())` should be `log.info("Sending fund transfer request {}", request)`, (2) `CustomFeignErrorDecoder.java` lines using `log.error("..." + e)` should use `log.error("...", e)`. Search for any other instances of `log.info/error/warn/debug("..." +` across the codebase and fix them all. Open a PR.

---

### 1.7 Add Feign Timeout Configuration (GAP-RES-003)
**Severity: High | Effort: Small**

Configure explicit connect and read timeouts for all Feign clients.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Feign timeout configuration to the `application.yml` (or bootstrap config) for `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service`. Set `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `spring.cloud.openfeign.client.config.default.read-timeout: 10000`. Open a PR.

---

### 1.8 Add Feign Retry Configuration (GAP-RES-002)
**Severity: High | Effort: Small**

Add retry policies for transient failures.

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a `Retryer` bean to the Feign configuration in `fund-transfer-service`, `utility-payment-service`, and `user-service`. Configure it with `new Retryer.Default(100, 1000, 3)` (100ms initial interval, 1s max interval, 3 max attempts). Ensure retries only apply to GET requests (idempotent). For POST requests, do NOT retry. Open a PR.

---

### 1.9 Document CSRF Disabled Decision (GAP-SEC-004)
**Severity: High | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a code comment above the `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)` line in `SecurityConfiguration.java` (api-gateway) explaining that CSRF is disabled because the API uses stateless JWT authentication and does not maintain server-side sessions. Open a PR.

---

### 1.10 Add Custom Health Check Indicators (GAP-OBS-002)
**Severity: High | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom Spring Boot `HealthIndicator` beans: (1) In `core-banking-service`, add a `DatabaseHealthIndicator` that runs a simple `SELECT 1` query. (2) In `internet-banking-user-service`, add a `KeycloakHealthIndicator` that pings the Keycloak server URL. (3) In `fund-transfer-service` and `utility-payment-service`, add a health indicator that checks if the `core-banking-service` Feign client is reachable. Configure `management.endpoint.health.show-details=always` in each service's config. Open a PR.

---

### 1.11 Fix Keycloak Singleton Thread Safety (GAP-SEC-006)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, fix the thread-safety issue in `KeycloakProperties.java` in the `internet-banking-user-service`. Replace the naive singleton pattern with a `@Bean` method in a `@Configuration` class that returns a `Keycloak` instance, letting Spring manage the lifecycle. Remove the static `keycloakInstance` field. Open a PR.

---

### 1.12 Add JaCoCo Test Coverage Reporting (GAP-TEST-004)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the JaCoCo Gradle plugin to all 6 service `build.gradle` files. Configure it to generate HTML and XML reports on `./gradlew test`. Add a `jacocoTestReport` task that depends on `test`. Set a minimum coverage threshold of 0% initially (so builds don't fail) with a TODO comment to increase it. Open a PR.

---

### 1.13 Fix OpenAPI Dependency Mismatch (GAP-API-004)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in the `build.gradle` of `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`, and `internet-banking-user-service` (since these are servlet-based, not WebFlux). Add a global OpenAPI configuration class in each service with `@OpenAPIDefinition` setting the title, description, and version. Open a PR.

---

### 1.14 Type ResponseEntity Generic Parameters (GAP-ERR-005)
**Severity: Low | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add type parameters to all raw `ResponseEntity` return types in controllers. For example, change `ResponseEntity` to `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc. across `core-banking-service`, `fund-transfer-service`, and `utility-payment-service` controllers. The `user-service` already uses typed responses — match that pattern. Open a PR.

---

### 1.15 Clean Up Build Artifacts from Repository (GAP-ORG-005)
**Severity: Low | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the root `.gitignore` to include `**/.gradle/`, `**/build/`, and `**/*.jar`. Then remove any tracked `.gradle/` and `build/` directories from git tracking with `git rm -r --cached`. Open a PR.

---

### 1.16 Isolate Test Configuration from External Services (GAP-TEST-005)
**Severity: Low | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update `core-banking-service/src/test/resources/application.yml` to disable Eureka client for tests by adding `eureka.client.enabled: false`. Do the same for any other service that has a test `application.yml`. Open a PR.

---

### 1.17 Add Dependency Vulnerability Scanning (GAP-SEC-007)
**Severity: Low | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the OWASP Dependency-Check Gradle plugin (`org.owasp.dependencycheck` version 9.x) to each service's `build.gradle`. Configure a `dependencyCheckAnalyze` task. Set `failBuildOnCVSS` to 9 (only fail on critical vulnerabilities initially). Open a PR.

---

## Phase 2: Important

*Estimated timeline: 3-6 weeks*
*Focus: Input validation, shared library, circuit breakers, RBAC, tests, pagination*

### 2.1 Add Bean Validation to All Request DTOs (GAP-SEC-003)
**Severity: Critical | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add `spring-boot-starter-validation` to the `build.gradle` of all four business services. Then add Jakarta Bean Validation annotations to all request DTOs: (1) `FundTransferRequest`: `@NotBlank fromAccount`, `@NotBlank toAccount`, `@NotNull @Positive amount`. (2) `UtilityPaymentRequest`: `@NotNull providerId`, `@NotNull @Positive amount`, `@NotBlank referenceNumber`, `@NotBlank account`. (3) `User` (user-service): `@NotBlank @Email email`, `@NotBlank identification`, `@NotBlank @Size(min=8) password`. (4) `UserUpdateRequest`: `@NotNull status`. Add `@Valid` annotation to all `@RequestBody` parameters in controllers. Update each `GlobalExceptionHandler` to handle `MethodArgumentNotValidException` and return a 400 with field-level error details. Add unit tests for each validated endpoint. Open a PR.

---

### 2.2 Extract Shared Library Module (GAP-ORG-002)
**Severity: High | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a new module `banking-common` with its own `build.gradle`. Move the following shared classes into it: `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` (base class), `AuditAware`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, and the audit configuration classes. Update each service's `build.gradle` to depend on `banking-common` as a project dependency. Remove the duplicated classes from each service. Ensure all services still compile and tests pass. Open a PR.

---

### 2.3 Create Multi-Project Gradle Build (GAP-ORG-001)
**Severity: High | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a root `settings.gradle` that includes all 6 service modules plus the new `banking-common` module. Create a root `build.gradle` with a `subprojects` block that applies common configuration: Java 21 source compatibility, shared repository declarations, Spring Cloud BOM version, and common dependency versions via a Gradle version catalog (`gradle/libs.versions.toml`). Migrate each service's `build.gradle` to reference the version catalog instead of hard-coded versions. Ensure `./gradlew build` from the root builds all services. Open a PR.

---

### 2.4 Add Circuit Breakers to Feign Clients (GAP-RES-001)
**Severity: Critical | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Resilience4j circuit breaker support to all three Feign client services (`fund-transfer-service`, `utility-payment-service`, `user-service`). Add `spring-cloud-starter-circuitbreaker-resilience4j` to each service's `build.gradle`. Enable `spring.cloud.openfeign.circuitbreaker.enabled=true` in each service's config. Create fallback classes for each Feign client that return appropriate error responses (e.g., `FundTransferFeignClientFallback`, `BankingCoreRestClientFallback`). Configure circuit breaker parameters: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=10s`. Add unit tests for the fallback behavior. Open a PR.

---

### 2.5 Implement Role-Based Access Control (GAP-SEC-005)
**Severity: High | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add role-based access control at the API Gateway level. Update `SecurityConfiguration.java` to: (1) Extract JWT realm roles from Keycloak tokens using a custom `ReactiveJwtAuthenticationConverter`. (2) Restrict `PATCH /user/api/v1/bank-users/update/**` to users with the `ADMIN` role. (3) Keep `POST /user/api/v1/bank-users/register` as public. (4) Require `USER` or `ADMIN` role for all other endpoints. Update the Keycloak realm export in `docker-compose/keycloak/` to include `ADMIN` and `USER` roles. Open a PR.

---

### 2.6 Standardize RESTful URL Naming (GAP-API-001)
**Severity: High | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, refactor API endpoints to follow RESTful conventions: (1) `POST /api/v1/bank-users/register` -> `POST /api/v1/bank-users` (2) `PATCH /api/v1/bank-users/update/{id}` -> `PATCH /api/v1/bank-users/{id}` (3) `GET /api/v1/account/util-account/{name}` -> `GET /api/v1/utility-accounts/{name}` (4) `POST /api/v1/transaction/fund-transfer` -> `POST /api/v1/transactions/fund-transfers` (5) `POST /api/v1/transaction/util-payment` -> `POST /api/v1/transactions/utility-payments`. Update the API Gateway route configuration and the SecurityConfiguration permitted paths accordingly. Update all Feign client endpoint paths. Update the Postman collection if present. Open a PR.

---

### 2.7 Add Pagination Metadata to List Responses (GAP-API-002)
**Severity: High | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a generic `PageResponse<T>` wrapper class in the shared library with fields: `List<T> content`, `int pageNumber`, `int pageSize`, `long totalElements`, `int totalPages`, `boolean last`. Update all paginated endpoints in `core-banking-service`, `fund-transfer-service`, `utility-payment-service`, and `user-service` to return `PageResponse<T>` instead of `List<T>`. Pass the Spring `Page` metadata through to the response. Open a PR.

---

### 2.8 Add Feign Error Decoder to All Services (GAP-ERR-004)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, the `internet-banking-user-service` already has a `CustomFeignErrorDecoder`. Copy this pattern (or extract it to the shared library) and add it to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Register it in each service's `CustomFeignClientConfiguration`. Ensure error responses from `core-banking-service` are properly deserialized and re-thrown as `SimpleBankingGlobalException`. Open a PR.

---

### 2.9 Standardize Package Structure (GAP-ORG-003)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, standardize the package structure across all services to follow this convention: `com.javatodev.finance.controller`, `com.javatodev.finance.service`, `com.javatodev.finance.service.rest` (for Feign clients), `com.javatodev.finance.model.entity`, `com.javatodev.finance.model.dto`, `com.javatodev.finance.model.dto.request`, `com.javatodev.finance.model.dto.response`, `com.javatodev.finance.model.mapper`, `com.javatodev.finance.repository`, `com.javatodev.finance.exception`, `com.javatodev.finance.configuration`. Move classes that are in non-standard locations. Ensure all services compile and tests pass. Open a PR.

---

### 2.10 Use MapStruct for Object Mapping (GAP-ORG-004)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, replace the hand-written mapper classes with MapStruct. Add `org.mapstruct:mapstruct:1.5.5.Final` as an implementation dependency and `org.mapstruct:mapstruct-processor:1.5.5.Final` as an annotation processor to each service's `build.gradle`. Create MapStruct `@Mapper(componentModel = "spring")` interfaces for each existing mapper: `BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`, `UtilityAccountMapper`. Remove the old manual mapper classes. Inject mappers as Spring beans. Open a PR.

---

### 2.11 Add Custom Business Metrics (GAP-OBS-003)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Micrometer custom metrics: (1) In `core-banking-service`, add counters for `banking.fund_transfer.total` and `banking.utility_payment.total` (tagged by status: success/failure) and a timer for `banking.transaction.duration`. (2) In `fund-transfer-service`, add a counter `fund_transfer.requests.total` tagged by status. (3) In `user-service`, add counters for `user.registration.total` and `user.approval.total`. (4) Add `management.endpoints.web.exposure.include=health,info,prometheus,metrics` to each service's config. (5) Add `io.micrometer:micrometer-registry-prometheus` dependency. Open a PR.

---

### 2.12 Add Trace ID to Log Output (GAP-OBS-004)
**Severity: Medium | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, configure structured logging with trace/span ID in all 6 services. Add `logging.pattern.level=%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]` to each service's `application.yml`. This ensures Micrometer Tracing (Brave) automatically populates MDC context. Verify the log output includes trace IDs. Open a PR.

---

### 2.13 Add Fallback Behavior for Feign Clients (GAP-RES-004)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement fallback factory classes for each Feign client using `@FeignClient(fallbackFactory = ...)`: (1) `BankingCoreFeignClientFallbackFactory` in `fund-transfer-service` — returns a 503 Service Unavailable response and logs the cause. (2) `BankingCoreRestClientFallbackFactory` in `utility-payment-service` — same pattern. (3) `BankingCoreRestClientFallbackFactory` in `user-service` — same pattern. Each fallback should throw a service-specific exception (e.g., `ServiceUnavailableException`) that the `GlobalExceptionHandler` maps to HTTP 503. Open a PR.

---

### 2.14 Add API Filtering and Search Support (GAP-API-005)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add filtering support to the paginated list endpoints: (1) `GET /api/v1/transfer` — add optional query parameters `fromAccount`, `toAccount`, `status`, `dateFrom`, `dateTo`. (2) `GET /api/v1/utility-payment` — add optional parameters `account`, `providerId`, `status`, `dateFrom`, `dateTo`. (3) Implement Spring Data JPA Specification-based filtering in each repository. (4) Update the controllers to accept these parameters and pass them to the service layer. Open a PR.

---

### 2.15 Define API Versioning Strategy (GAP-API-003)
**Severity: Medium | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, document the API versioning strategy in a new file `docs/API_VERSIONING.md`. The strategy should be: URL path versioning (already using `/api/v1/`). Document rules for when to increment to `v2`, backward compatibility requirements, and deprecation policy. Add an `@ApiVersion` annotation or similar mechanism that can be used to tag controllers with their version. Open a PR.

---

## Phase 3: Polish

*Estimated timeline: 6-12 weeks*
*Focus: Comprehensive testing, centralized logging, distributed transaction safety, advanced features*

### 3.1 Add Unit Tests for All Services (GAP-TEST-001)
**Severity: Critical | Effort: Large**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests for the `internet-banking-user-service`. Create test classes: `UserServiceTest` (mock KeycloakUserService, UserRepository, BankingCoreRestClient — test createUser success, duplicate email, invalid email, user not found in core; test readUsers; test readUser; test updateUser with APPROVED status), `KeycloakUserServiceTest` (mock KeycloakManager — test createUser, updateUser, readUserByEmail, readUser with not found). Target 80%+ line coverage for the service layer. Use Mockito for mocking. Open a PR.

**Devin Prompt (fund-transfer-service):**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add unit tests for `internet-banking-fund-transfer-service`. Create `FundTransferServiceTest` with tests: fundTransfer success (mock Feign client returns success), fundTransfer when core service returns error, readAllTransfers pagination. Create `FundTransferControllerTest` using `@WebMvcTest` with MockMvc to test the POST and GET endpoints. Target 80%+ line coverage. Open a PR.

**Devin Prompt (utility-payment-service):**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add unit tests for `internet-banking-utility-payment-service`. Create `UtilityPaymentServiceTest` with tests: utilPayment success, utilPayment when core service returns error, readPayments pagination. Create `UtilityPaymentControllerTest` using `@WebMvcTest`. Target 80%+ coverage. Open a PR.

---

### 3.2 Add Integration Tests (GAP-TEST-002)
**Severity: High | Effort: Large**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration tests for `core-banking-service`. Add Testcontainers (`org.testcontainers:mysql` and `org.testcontainers:junit-jupiter`) to the test dependencies. Create `AccountControllerIntegrationTest` and `TransactionControllerIntegrationTest` using `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real MySQL Testcontainer. Test the full request-response cycle including Flyway migrations, JPA persistence, and JSON serialization. Disable Eureka client in integration test profile. Open a PR.

---

### 3.3 Add Contract Tests Between Services (GAP-TEST-003)
**Severity: High | Effort: Large**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Spring Cloud Contract tests between `core-banking-service` (producer) and its consumers (`fund-transfer-service`, `utility-payment-service`, `user-service`). Add `spring-cloud-starter-contract-verifier` to `core-banking-service` and define contracts in `src/test/resources/contracts/` for each endpoint consumed by other services. Add `spring-cloud-starter-contract-stub-runner` to each consumer service's test dependencies and write consumer-side tests that verify Feign clients against the generated stubs. Open a PR.

---

### 3.4 Set Up Centralized Log Aggregation (GAP-OBS-005)
**Severity: Low | Effort: Medium**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add an ELK stack to the Docker Compose setup. Add Elasticsearch, Logstash, and Kibana services to `docker-compose.yml`. Configure each microservice to output logs in JSON format using Logback's `LogstashEncoder` (add `net.logstash.logback:logstash-logback-encoder:7.4` dependency). Add a `logback-spring.xml` to each service that uses `LogstashEncoder` for the `docker` profile and standard pattern for local development. Add a Logstash pipeline config that reads from Docker container logs and forwards to Elasticsearch. Open a PR.

---

### 3.5 Implement Saga Pattern for Distributed Transactions (GAP-RES-005)
**Severity: Low | Effort: Large**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement a choreography-based Saga pattern for fund transfers using RabbitMQ. Add `spring-boot-starter-amqp` to `fund-transfer-service` and `core-banking-service`. The flow should be: (1) `fund-transfer-service` publishes a `FundTransferRequested` event to RabbitMQ. (2) `core-banking-service` consumes it, processes the transfer, and publishes `FundTransferCompleted` or `FundTransferFailed`. (3) `fund-transfer-service` consumes the result and updates its local record. Add a compensating action: if the core transfer succeeds but the status update fails, a scheduled job retries the update. Add RabbitMQ to Docker Compose. Open a PR.

---

### 3.6 Add Inconsistent HTTP Method Cleanup (GAP-API-006)
**Severity: Low | Effort: Small**

**Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, the `/register` and `/update` verbs in the user-service URLs are redundant with the HTTP methods. This was already addressed in item 2.6 (RESTful URL naming). Verify the URLs follow REST conventions after that change is applied. No separate PR needed — merge with 2.6.

---

## Implementation Priority Matrix

```
                    ┌─────────────────────────────────────────────┐
                    │              EFFORT                          │
                    │    Small         Medium         Large        │
    ┌───────────────┼─────────────────────────────────────────────┤
    │ Critical      │ 1.4, 1.5       │ 1.3, 2.1     │ 3.1       │
S   │               │                │ 2.4          │            │
E   ├───────────────┼────────────────┼──────────────┼────────────┤
V   │ High          │ 1.1, 1.2, 1.6 │ 2.2, 2.3     │ 3.2, 3.3  │
E   │               │ 1.7, 1.8, 1.9 │ 2.5, 2.6     │            │
R   │               │ 1.10, 2.7     │              │            │
I   ├───────────────┼────────────────┼──────────────┼────────────┤
T   │ Medium        │ 1.11, 1.13    │ 2.9, 2.10   │            │
Y   │               │ 2.8, 2.12     │ 2.11, 2.13  │            │
    │               │ 1.12          │ 2.14, 2.15  │            │
    ├───────────────┼────────────────┼──────────────┼────────────┤
    │ Low           │ 1.14, 1.15    │ 3.4          │ 3.5       │
    │               │ 1.16, 1.17    │              │            │
    │               │ 3.6           │              │            │
    └───────────────┴────────────────┴──────────────┴────────────┘
```

---

## Recommended Execution Order

1. **Sprint 1** (Week 1): Items 1.1-1.5 — Fix critical security and error handling
2. **Sprint 2** (Week 2): Items 1.6-1.17 — Logging, timeouts, health checks, tooling
3. **Sprint 3-4** (Weeks 3-4): Items 2.1-2.4 — Validation, shared library, Gradle build, circuit breakers
4. **Sprint 5-6** (Weeks 5-6): Items 2.5-2.15 — RBAC, API design, metrics, filtering
5. **Sprint 7-10** (Weeks 7-10): Items 3.1-3.3 — Comprehensive testing
6. **Sprint 11-12** (Weeks 11-12): Items 3.4-3.6 — Logging infrastructure, Saga pattern
