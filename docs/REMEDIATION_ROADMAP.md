# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (Critical bugs + low-effort high-impact fixes)

These items address data integrity bugs, security vulnerabilities, and error handling issues that can be fixed quickly.

### 1.1 Fix Balance Calculation Bug in Core Banking
**Gap Ref:** 7.7 | **Severity:** Critical | **Effort:** Small

The `TransactionService.utilPayment()` and `internalFundTransfer()` methods double-subtract when computing `availableBalance` because they read `actualBalance` after it was already decremented.

**Devin Prompt:**
> Fix the balance calculation bug in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`. In both `utilPayment()` and `internalFundTransfer()`, the `setAvailableBalance()` call uses the already-decremented `actualBalance`, causing a double subtraction. After decrementing `actualBalance`, set `availableBalance` to match `actualBalance` (not subtract again). Add unit tests that verify the correct balances after a fund transfer and utility payment.

---

### 1.2 Add Input Validation to All Request DTOs
**Gap Ref:** 4.1 | **Severity:** Critical | **Effort:** Small

No request DTOs use Bean Validation annotations. Any payload is accepted.

**Devin Prompt:**
> Add `spring-boot-starter-validation` dependency to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.) to all request DTOs: `FundTransferRequest`, `UtilityPaymentRequest` (both core and service versions), and the user registration `User` DTO. Add `@Valid` to all `@RequestBody` parameters in controllers. Update `GlobalExceptionHandler` in each service to handle `MethodArgumentNotValidException` and return a structured `ErrorResponse` with field-level validation errors. Add unit tests for validation.

---

### 1.3 Fix Generic Exception Handler to Return Proper HTTP Status Codes
**Gap Ref:** 2.2, 2.3, 5.6 | **Severity:** Critical | **Effort:** Small

The catch-all exception handler returns 400 for all errors and leaks internal details.

**Devin Prompt:**
> Refactor `GlobalExceptionHandler` in all four business services (core-banking, fund-transfer, user-service, utility-payment). Change the generic `Exception.class` handler to return `500 Internal Server Error` with a generic message like `"An unexpected error occurred"` (no exception details). Add specific handlers for `EntityNotFoundException` returning `404 Not Found`. For `SimpleBankingGlobalException`, determine the appropriate HTTP status based on the error code. Ensure all error responses use the structured `ErrorResponse { code, message }` format — never return raw strings.

---

### 1.4 Update Fund Transfer / Utility Payment to Set FAILED Status on Error
**Gap Ref:** 2.6 | **Severity:** Critical | **Effort:** Small

When Feign calls fail, local records stay in PENDING/PROCESSING status forever.

**Devin Prompt:**
> In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, wrap the Feign call to core-banking in a try-catch. On any exception, update the local entity status to `FAILED` and save it before re-throwing the exception. Add unit tests that verify the entity is saved with `FAILED` status when the Feign call throws an exception.

---

### 1.5 Add Feign Error Decoder to Fund Transfer and Utility Payment Services
**Gap Ref:** 2.5 | **Severity:** High | **Effort:** Small

Only User Service has a Feign error decoder. Others lose upstream error context.

**Devin Prompt:**
> Extract the `CustomFeignErrorDecoder` from `internet-banking-user-service` and add equivalent implementations to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`. Register each decoder in the respective `CustomFeignClientConfiguration`. Ensure that upstream `ErrorResponse { code, message }` from core-banking is deserialized and re-thrown as a `SimpleBankingGlobalException` with the original code and message preserved.

---

### 1.6 Fix `ResponseEntity` Raw Type Warnings
**Gap Ref:** 5.1 | **Severity:** High | **Effort:** Small

All controllers use raw `ResponseEntity` without type parameters.

**Devin Prompt:**
> Add proper generic type parameters to all `ResponseEntity` return types in every controller across all services. For example, change `ResponseEntity` to `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`, `ResponseEntity<FundTransferResponse>`, etc. This improves type safety and allows Swagger/OpenAPI to auto-generate accurate response schemas.

---

### 1.7 Add Pagination Metadata to List Endpoints
**Gap Ref:** 5.3 | **Severity:** High | **Effort:** Small

Paginated endpoints discard page metadata and return raw lists.

**Devin Prompt:**
> Modify all paginated `GET` endpoints across all services to return `Page<T>` (or a custom `PageResponse<T>` wrapper with `content`, `totalElements`, `totalPages`, `number`, `size`, `hasNext` fields) instead of `List<T>`. Update the service layer to return the full `Page` object from the repository instead of calling `.getContent()` and discarding metadata.

---

### 1.8 Fix Incorrect Swagger Dependency
**Gap Ref:** 5.4 | **Severity:** Medium | **Effort:** Small

MVC services incorrectly use the WebFlux Swagger dependency.

**Devin Prompt:**
> In `core-banking-service/build.gradle`, `internet-banking-fund-transfer-service/build.gradle`, `internet-banking-user-service/build.gradle`, and `internet-banking-utility-payment-service/build.gradle`, replace `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0`. Verify Swagger UI is accessible at `/swagger-ui.html` for each service.

---

### 1.9 Stop Logging Sensitive Data
**Gap Ref:** 6.5 | **Severity:** High | **Effort:** Small

Controllers log full request objects including account numbers and amounts.

**Devin Prompt:**
> Audit all `log.info()` and `log.error()` calls across every service. Remove or redact sensitive data from log statements. Replace `log.info("Got fund transfer request from API {}", request.toString())` with a safe version that only logs non-sensitive metadata (e.g., a request ID or masked account number). Do not log passwords, full account numbers, or financial amounts at INFO level.

---

### 1.10 Add Feign Timeout Configuration
**Gap Ref:** 7.3 | **Severity:** High | **Effort:** Small

Feign clients use default (potentially infinite) timeouts.

**Devin Prompt:**
> Add Feign timeout configuration to `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. Set `connectTimeout: 5000` and `readTimeout: 10000` (milliseconds) in each service's `application.yml` under `spring.cloud.openfeign.client.config.default`. These values should be appropriate for inter-service calls within a Docker network.

---

### 1.11 Add Test Coverage Reporting (JaCoCo)
**Gap Ref:** 3.5 | **Severity:** Medium | **Effort:** Small

No coverage reporting exists.

**Devin Prompt:**
> Add the JaCoCo Gradle plugin to all six services. Configure it to generate HTML and XML reports on `./gradlew test`. Add a `jacocoTestCoverageVerification` task with a minimum line coverage threshold of 50% (to start). Ensure the reports are generated in `build/reports/jacoco/`.

---

### 1.12 Add OWASP Dependency Check
**Gap Ref:** 4.5 | **Severity:** Medium | **Effort:** Small

No dependency vulnerability scanning is configured.

**Devin Prompt:**
> Add the OWASP Dependency Check Gradle plugin (`org.owasp.dependencycheck`) to all services. Configure it to fail the build on CVEs with CVSS score >= 7. Add a `dependencyCheckAnalyze` task. Document how to run it in the README.

---

### 1.13 Add Prometheus Metrics Endpoint
**Gap Ref:** 6.3 | **Severity:** Medium | **Effort:** Small

README mentions Prometheus but the dependency is missing.

**Devin Prompt:**
> Add `io.micrometer:micrometer-registry-prometheus` dependency to all business services (core-banking, fund-transfer, user-service, utility-payment). Expose the `/actuator/prometheus` endpoint by adding `management.endpoints.web.exposure.include: health,info,prometheus,metrics` to each service's configuration. Verify the endpoint returns Prometheus-format metrics.

---

## Phase 2: Important (Structural improvements requiring more effort)

### 2.1 Extract Shared Library for Common Code
**Gap Ref:** 1.2 | **Severity:** High | **Effort:** Medium

Massive code duplication across services.

**Devin Prompt:**
> Create a new Gradle subproject `shared-banking-lib` at the repository root. Extract the following duplicated classes into it: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `TransactionStatus`. Publish it as a local dependency. Update all four business services to depend on `shared-banking-lib` and remove the duplicated classes. Set up a root `settings.gradle` for multi-module builds.

---

### 2.2 Introduce a Root Multi-Module Gradle Build
**Gap Ref:** 1.1 | **Severity:** Medium | **Effort:** Medium

Each service builds independently with inconsistent versions.

**Devin Prompt:**
> Create a root `settings.gradle` and `build.gradle` that includes all 6 services as subprojects. Define shared dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, Lombok, etc.) in the root `build.gradle` using a `subprojects {}` block or a Gradle version catalog (`libs.versions.toml`). Ensure `./gradlew build` from the root compiles, tests, and packages all services. Keep individual `build.gradle` files for service-specific dependencies.

---

### 2.3 Add Circuit Breakers to Feign Clients
**Gap Ref:** 7.1 | **Severity:** Critical | **Effort:** Medium

No circuit breakers — cascading failure risk.

**Devin Prompt:**
> Add Resilience4j circuit breaker support to `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`. Add dependencies: `spring-cloud-starter-circuitbreaker-resilience4j` and configure `spring.cloud.openfeign.circuitbreaker.enabled: true`. Create fallback factory classes for each Feign client that return meaningful error responses when the circuit is open. Configure circuit breaker parameters: `slidingWindowSize: 10`, `failureRateThreshold: 50`, `waitDurationInOpenState: 10s`. Add integration tests that verify fallback behavior.

---

### 2.4 Add Retry Policies to Feign Clients
**Gap Ref:** 7.2 | **Severity:** High | **Effort:** Small

No retry on transient failures.

**Devin Prompt:**
> Add Spring Retry support to all Feign clients. Add `spring-retry` dependency to fund-transfer, user-service, and utility-payment services. Configure Feign retry: `spring.cloud.openfeign.client.config.default.retryer: feign.Retryer.Default` with `period: 1000`, `maxPeriod: 5000`, `maxAttempts: 3`. Ensure retries only occur on `5xx` errors and connection failures, not on `4xx` client errors.

---

### 2.5 Secure Downstream Services
**Gap Ref:** 4.3 | **Severity:** Critical | **Effort:** Large

Downstream services accept unauthenticated requests.

**Devin Prompt:**
> Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Configure each as an OAuth2 resource server validating JWTs from Keycloak. The API Gateway should forward the original JWT token (not just `X-Auth-Id` header) to downstream services. Update Feign client configurations to propagate the `Authorization` header. Add a security configuration class to each service that permits actuator endpoints and requires authentication for all `/api/**` paths.

---

### 2.6 Write Comprehensive Unit Tests for All Services
**Gap Ref:** 3.1 | **Severity:** Critical | **Effort:** Large

Only core-banking has meaningful tests.

**Devin Prompt:**
> Write unit tests for all service classes in `internet-banking-user-service` (UserService, KeycloakUserService), `internet-banking-fund-transfer-service` (FundTransferService), and `internet-banking-utility-payment-service` (UtilityPaymentService). Use Mockito to mock Feign clients, repositories, and Keycloak. Test happy paths, error paths (Feign failures, entity not found, validation errors), and edge cases. Target at least 80% line coverage for service classes.

---

### 2.7 Add Integration Tests with MockMvc
**Gap Ref:** 3.2 | **Severity:** High | **Effort:** Large

No integration tests exist.

**Devin Prompt:**
> Add `@WebMvcTest` integration tests for all controllers in core-banking-service, fund-transfer-service, user-service, and utility-payment-service. Use `MockMvc` to test REST endpoints with mocked service layers. Test request validation, HTTP status codes, response formats, and error handling. For API Gateway, add `@SpringBootTest` tests with `WebTestClient`. Use `@MockBean` for Feign clients and external dependencies. Add H2 test profiles where database testing is needed.

---

### 2.8 Add Structured JSON Logging
**Gap Ref:** 6.1 | **Severity:** Medium | **Effort:** Medium

Logging is inconsistent and not machine-parseable.

**Devin Prompt:**
> Add `net.logstash.logback:logstash-logback-encoder` to all services. Create a shared `logback-spring.xml` that outputs JSON-format logs including `timestamp`, `level`, `logger`, `message`, `traceId`, `spanId`, and `service` fields. Ensure the Micrometer tracing context (trace ID, span ID) is automatically included in log output via MDC. Configure different log levels for dev (console, human-readable) and docker (JSON) profiles.

---

### 2.9 Add Custom Health Indicators
**Gap Ref:** 6.2 | **Severity:** Medium | **Effort:** Small

No custom health checks beyond defaults.

**Devin Prompt:**
> Add custom health indicators to each business service. For core-banking-service, add a `DatabaseHealthIndicator` that verifies the MySQL connection. For fund-transfer, user-service, and utility-payment services, add a `CoreBankingHealthIndicator` that pings the core banking service's actuator health endpoint via Feign. Configure `management.endpoint.health.show-details: always` for detailed health responses. Expose health endpoints in the actuator configuration.

---

### 2.10 Add Rate Limiting to API Gateway
**Gap Ref:** 7.5 | **Severity:** Medium | **Effort:** Medium

No rate limiting configured.

**Devin Prompt:**
> Add rate limiting to the API Gateway using Spring Cloud Gateway's `RequestRateLimiter` filter. Add `spring-boot-starter-data-redis-reactive` dependency and configure a Redis-backed rate limiter. Set default limits of 100 requests per second per user (keyed by JWT subject claim). Add a Redis service to `docker-compose.yml`. Configure different rate limits for different route groups (e.g., lower limits for fund transfers, higher for reads).

---

## Phase 3: Polish (Enhancements and long-term improvements)

### 3.1 Implement Saga Pattern for Distributed Transactions
**Gap Ref:** 7.6 | **Severity:** Critical | **Effort:** Large

No compensation mechanism for distributed transactions.

**Devin Prompt:**
> Implement a choreography-based saga pattern for the fund transfer flow. Add RabbitMQ (`spring-boot-starter-amqp`) to fund-transfer-service and core-banking-service. Replace the synchronous Feign call with an event-driven flow: (1) Fund Transfer Service publishes a `FundTransferRequested` event, (2) Core Banking consumes it, processes the transfer, and publishes `FundTransferCompleted` or `FundTransferFailed`, (3) Fund Transfer Service consumes the result and updates its local record. Add a `RabbitMQ` service to docker-compose. Implement dead-letter queues for failed messages. Add idempotency keys to prevent duplicate processing.

---

### 3.2 Add Contract Tests Between Services
**Gap Ref:** 3.3 | **Severity:** Medium | **Effort:** Large

No consumer-driven contract tests.

**Devin Prompt:**
> Add Spring Cloud Contract to core-banking-service (producer side). Define contracts for all endpoints consumed by fund-transfer, user-service, and utility-payment services. Generate contract tests that verify the producer's API. On the consumer side, add contract stubs and use `@AutoConfigureStubRunner` to verify Feign clients work against the contract stubs. This ensures API compatibility across services.

---

### 3.3 Externalize Secrets with Vault or Environment Variables
**Gap Ref:** 4.2 | **Severity:** High | **Effort:** Small

Credentials are hardcoded in source.

**Devin Prompt:**
> Replace all hardcoded credentials in `docker-compose.yml`, `privileges.sql`, and configuration files with environment variables. Create a `.env.example` file documenting all required environment variables (MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, etc.). Update `docker-compose.yml` to reference `${VARIABLE}` syntax. Add `.env` to `.gitignore`. Document the setup in `README.md`. For production, document integration with HashiCorp Vault or cloud secret managers.

---

### 3.4 Standardize Package Structure Across Services
**Gap Ref:** 1.3 | **Severity:** Low | **Effort:** Small

Inconsistent package naming conventions.

**Devin Prompt:**
> Standardize the package structure across all business services to follow this convention: `com.javatodev.finance.{controller, service, repository, model.entity, model.dto, model.mapper, configuration, exception, client}`. Move repositories out of `model/repository/` to top-level `repository/`. Move Feign clients to a `client/` package. Ensure all services follow the same layout. Update all imports accordingly.

---

### 3.5 Add CI/CD Pipeline
**Gap Ref:** 6.5 (build pipeline) | **Severity:** Medium | **Effort:** Medium

No CI/CD pipeline exists.

**Devin Prompt:**
> Create a GitHub Actions CI/CD pipeline in `.github/workflows/ci.yml`. The pipeline should: (1) Run on push to `main` and all PRs, (2) Set up Java 21 with Gradle caching, (3) Build all services in parallel, (4) Run unit tests and integration tests, (5) Generate JaCoCo coverage reports, (6) Run OWASP dependency check, (7) Build Docker images, (8) Push images to a container registry on merge to `main`. Add status badges to README.md.

---

### 3.6 Add Custom Business Metrics
**Gap Ref:** 6.4 | **Severity:** Low | **Effort:** Medium

No custom tracing spans or business metrics.

**Devin Prompt:**
> Add custom Micrometer metrics to all business services. In core-banking: add counters for `banking.transfers.total`, `banking.payments.total`, `banking.transfers.failed`, and a timer for `banking.transfer.duration`. In user-service: add `banking.users.registered`, `banking.users.approved`. Add a `@Timed` annotation or manual `MeterRegistry` instrumentation to key service methods. Create custom tracing spans for business operations like "validate-balance" and "create-keycloak-user" using Micrometer's `Observation` API.

---

### 3.7 Add Fallback Behavior for Feign Clients
**Gap Ref:** 7.4 | **Severity:** Medium | **Effort:** Medium

No degraded responses when downstream services are unavailable.

**Devin Prompt:**
> Create fallback factory implementations for all Feign clients. For read operations (e.g., `readAccount`, `readUser`), return cached data or a clear error DTO. For write operations (e.g., `fundTransfer`), the fallback should save the request for later retry and return a "processing" status. Register fallback factories in the `@FeignClient` annotation using the `fallbackFactory` attribute. Add tests verifying fallback behavior.

---

### 3.8 Improve API Documentation
**Gap Ref:** 5.5, 5.2 | **Severity:** Low | **Effort:** Small

No error response documentation in Swagger.

**Devin Prompt:**
> Add `@ApiResponse` annotations to all controller methods documenting success and error responses (400, 404, 500) with example payloads. Add `@Schema` annotations to all DTOs for field descriptions. Create an `OpenApiConfig` class in each service that sets API metadata (title, version, description, contact). Group endpoints with `@Tag` annotations (already partially done). Generate and commit OpenAPI spec files (`openapi.json`) for each service.

---

## Priority Matrix

```
                    LOW EFFORT ◄──────────────────► HIGH EFFORT
                    │                                         │
  HIGH IMPACT ──►   │ Phase 1 (1.1-1.13)    │ Phase 2 (2.3,  │
                    │ Fix bugs, validation,  │ 2.5, 2.6, 2.7) │
                    │ error handling,        │ Circuit breakers│
                    │ timeouts, security     │ Auth, Tests     │
                    │                        │                 │
                    ├────────────────────────┤─────────────────┤
                    │                        │                 │
  LOW IMPACT  ──►   │ Phase 3 (3.3, 3.4,    │ Phase 3 (3.1,   │
                    │ 3.8)                   │ 3.2, 3.5)       │
                    │ Secrets, packages,     │ Sagas, contracts│
                    │ API docs               │ CI/CD           │
                    │                        │                 │
                    └────────────────────────┴─────────────────┘
```

## Execution Notes

1. **Phase 1** items are independent — they can be executed in any order or in parallel.
2. **Phase 2** item 2.1 (shared library) should be done before 2.2 (multi-module build).
3. **Phase 2** item 2.5 (secure downstream) should be done before 2.7 (integration tests) so tests cover the security layer.
4. **Phase 3** item 3.1 (saga pattern) is the most complex change and may require architectural review before implementation.
5. Each Devin prompt above is self-contained and can be used as-is to create a focused PR.
