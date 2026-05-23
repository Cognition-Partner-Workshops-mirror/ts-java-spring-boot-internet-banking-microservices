# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins**: High severity / small effort items that immediately improve security and reliability.
- **Phase 2 — Important**: High-to-medium severity / medium effort items that strengthen the architecture.
- **Phase 3 — Polish**: Lower severity or large effort items that bring the system to production-grade maturity.

Each item includes a sample **Devin prompt** that can be used to execute the remediation.

---

## Phase 1: Quick Wins

> Focus: Security fixes, error handling corrections, and configuration hardening that can each be completed in a single focused session.

### 1.1 Fix Generic Error Handler — Proper HTTP Status Codes (GAP-EH-01, GAP-EH-03)

**Severity: High | Effort: Small**

Update `GlobalExceptionHandler` in all four services to:
- Return HTTP 404 for `EntityNotFoundException`
- Return HTTP 422 for `InsufficientFundsException`
- Return HTTP 409 for `UserAlreadyRegisteredException`
- Return HTTP 500 for unhandled `Exception` (not 400)
- Always return a structured `ErrorResponse` JSON body (never raw strings)
- Never leak stack traces or exception class names in the response

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, update the GlobalExceptionHandler in all four services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). For each handler:
1. Change the generic Exception handler to return HTTP 500 with a structured ErrorResponse body (code + message), not HTTP 400 with a raw string.
2. Add a dedicated handler for EntityNotFoundException that returns HTTP 404.
3. Add a dedicated handler for InsufficientFundsException that returns HTTP 422.
4. Add a dedicated handler for UserAlreadyRegisteredException (user-service only) that returns HTTP 409.
5. Ensure the response body is always a JSON ErrorResponse object, never a plain string.
6. Run tests to verify changes don't break existing tests.
```

### 1.2 Externalize Hardcoded Credentials (GAP-SE-01)

**Severity: Critical | Effort: Small**

Replace hardcoded database and Keycloak credentials in Docker Compose files with environment variables.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, externalize all hardcoded credentials in Docker Compose files:
1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml, replace all hardcoded passwords with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD}, etc.).
2. Create a docker-compose/.env.example file documenting all required environment variables with placeholder values.
3. Add docker-compose/.env to .gitignore.
4. Update docker-compose/mysql/Dockerfile to use build args instead of hardcoded ENV values.
5. Update docker-compose/mysql/privileges.sql to use environment-variable-based credentials where possible, or document the manual step.
6. Add a note to the README about copying .env.example to .env before running docker-compose.
```

### 1.3 Add Input Validation to All API Endpoints (GAP-SE-02)

**Severity: High | Effort: Medium**

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to controller method parameters.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add input validation to all API endpoints:
1. Add spring-boot-starter-validation dependency to core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service build.gradle files.
2. Add @NotNull, @NotBlank, @Positive, @Size annotations to all request DTO fields:
   - FundTransferRequest (both core and fund-transfer-service versions): fromAccount (@NotBlank), toAccount (@NotBlank), amount (@NotNull @Positive)
   - UtilityPaymentRequest (both core and utility-payment versions): providerId (@NotNull), amount (@NotNull @Positive), referenceNumber (@NotBlank), account (@NotBlank)
   - User (user-service): email (@NotBlank @Email), identification (@NotBlank), password (@NotBlank @Size(min=8))
3. Add @Valid annotation to all @RequestBody parameters in controllers.
4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns HTTP 400 with field-level error details.
5. Run tests to verify.
```

### 1.4 Remove Sensitive Data from Log Statements (GAP-SE-05)

**Severity: Medium | Effort: Small**

Sanitize log statements across all controllers and services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, fix sensitive data logging:
1. In FundTransferController.java (fund-transfer-service), replace toString() logging with log that only includes non-sensitive identifiers (e.g., fromAccount masked, toAccount masked).
2. In UserController.java (user-service), remove the password field from log output. Consider logging only the email or identification.
3. In UtilityPaymentService.java, mask account numbers in log statements.
4. In TransactionController.java (core-banking-service), mask account numbers in log output.
5. Consider creating a utility method for masking account numbers (show only last 4 digits).
```

### 1.5 Configure Feign Client Timeouts (GAP-RE-03)

**Severity: High | Effort: Small**

Add explicit timeout configuration for all Feign clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, configure Feign client timeouts for all three services that use Feign (fund-transfer-service, user-service, utility-payment-service):
1. In each service's application.yml (or create one if config is externalized), add:
   spring.cloud.openfeign.client.config.default.connect-timeout: 5000
   spring.cloud.openfeign.client.config.default.read-timeout: 10000
2. For the core-banking-service Feign client specifically, add:
   spring.cloud.openfeign.client.config.core-banking-service.connect-timeout: 5000
   spring.cloud.openfeign.client.config.core-banking-service.read-timeout: 10000
3. Run tests to verify the configuration is loaded correctly.
```

### 1.6 Fix Raw ResponseEntity Types (GAP-EH-02)

**Severity: Medium | Effort: Small**

Add generic type parameters to all `ResponseEntity` return types.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add type parameters to all ResponseEntity return types in controllers:
1. AccountController: ResponseEntity<BankAccount> for getBankAccount, ResponseEntity<UtilityAccount> for getUtilityAccount
2. TransactionController: ResponseEntity<FundTransferResponse> for fundTransfer, ResponseEntity<UtilityPaymentResponse> for utilPayment
3. UserController (core): ResponseEntity<User> for readUser, ResponseEntity<List<User>> for readUsers
4. FundTransferController: ResponseEntity<FundTransferResponse> for sendFundTransfer, ResponseEntity<List<FundTransfer>> for readFundTransfers
5. UtilityPaymentController: ResponseEntity<List<UtilityPayment>> for readPayments, ResponseEntity<UtilityPaymentResponse> for processPayment
6. Also update all GlobalExceptionHandler methods to use ResponseEntity<ErrorResponse> or ResponseEntity<Object>.
7. Run build to verify compilation.
```

### 1.7 Add Dependency Vulnerability Scanning (GAP-SE-07)

**Severity: Medium | Effort: Small**

Add OWASP Dependency Check Gradle plugin to all services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add OWASP Dependency Check plugin:
1. Add the plugin 'org.owasp.dependencycheck' version '9.0.9' to each service's build.gradle.
2. Configure it to fail the build on CVSS score >= 7.
3. Run ./gradlew dependencyCheckAnalyze in one service to verify it works.
4. Document in README.md how to run the vulnerability scan.
```

---

## Phase 2: Important

> Focus: Architectural improvements — resilience, shared code, better testing, and observability — that require moderate effort but significantly improve system stability.

### 2.1 Add Circuit Breakers to Feign Clients (GAP-RE-01)

**Severity: High | Effort: Medium**

Integrate Resilience4j circuit breakers on all Feign clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add Resilience4j circuit breakers:
1. Add spring-cloud-starter-circuitbreaker-resilience4j dependency to fund-transfer-service, user-service, and utility-payment-service.
2. Enable Feign circuit breaker support: spring.cloud.openfeign.circuitbreaker.enabled=true in each service's application.yml.
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClient in fund-transfer-service: return a FundTransferResponse with failure message
   - BankingCoreRestClient in user-service: return a UserResponse with null ID (indicating unavailability)
   - BankingCoreRestClient in utility-payment-service: return a UtilityPaymentResponse with failure message
4. Register fallbacks using @FeignClient(fallbackFactory = ...).
5. Configure circuit breaker thresholds in application.yml (e.g., failure-rate-threshold: 50, wait-duration-in-open-state: 30s).
6. Write unit tests for each fallback class.
```

### 2.2 Extract Shared Library Module (GAP-CO-01)

**Severity: High | Effort: Medium**

Create a `banking-common` shared module for duplicated code.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, create a shared library:
1. Create a new Gradle module banking-common/ with its own build.gradle (plain Java library, not Spring Boot).
2. Move these shared classes into banking-common:
   - AuditAware (from fund-transfer-service or user-service)
   - BaseMapper (from any service)
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler base class
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - TransactionStatus enum
3. Add banking-common as a dependency in each service's build.gradle using includeBuild or composite builds.
4. Create a root settings.gradle that includes all services.
5. Delete the duplicated classes from each service and update imports.
6. Build all services to verify compilation.
7. Run all tests to verify nothing is broken.
```

### 2.3 Create Multi-Module Gradle Build (GAP-CO-02)

**Severity: Medium | Effort: Medium**

Set up a root Gradle project with a version catalog.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, create a multi-module Gradle build:
1. Create a root settings.gradle.kts that includes all service modules.
2. Create a root build.gradle.kts with shared configuration (Java 21 source compat, common repositories, shared dependency versions).
3. Create gradle/libs.versions.toml as a version catalog defining all shared dependency versions (Spring Boot, Spring Cloud, MySQL connector, Springdoc, etc.).
4. Update each service's build.gradle to reference the version catalog instead of hardcoding versions.
5. Ensure all services build with './gradlew build' from the root.
6. Run all tests from the root with './gradlew test'.
```

### 2.4 Add Feign Error Handling (GAP-EH-04)

**Severity: High | Effort: Medium**

Implement proper Feign error decoders for all services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add Feign error handling:
1. Create a CustomFeignErrorDecoder in fund-transfer-service and utility-payment-service (user-service already has one — use it as reference).
2. The decoder should:
   - Read the response body and attempt to deserialize it as an ErrorResponse
   - Map HTTP 404 to EntityNotFoundException
   - Map HTTP 422 to InsufficientFundsException (or a new ServiceUnavailableException)
   - Map HTTP 5xx to a RetryableException or ServiceUnavailableException
   - Pass through the original error message from the core service
3. Register the error decoder in CustomFeignClientConfiguration.
4. Write unit tests for the error decoder with various HTTP status codes.
```

### 2.5 Add Unit Tests for All Services (GAP-TE-01)

**Severity: High | Effort: Large**

Expand test coverage to all service layers.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add unit tests for all services that currently lack them:

For internet-banking-fund-transfer-service:
1. Create FundTransferServiceTest that tests: successful transfer, Feign client failure handling, entity persistence.
2. Create FundTransferControllerTest using @WebMvcTest that tests: POST and GET endpoints, request validation.

For internet-banking-utility-payment-service:
1. Create UtilityPaymentServiceTest that tests: successful payment, Feign client failure, entity persistence.
2. Create UtilityPaymentControllerTest using @WebMvcTest.

For internet-banking-user-service:
1. Create UserServiceTest that tests: user creation flow, duplicate email detection, invalid email, user approval.
2. Create KeycloakUserServiceTest with mocked KeycloakManager.
3. Create UserControllerTest using @WebMvcTest.

Use Mockito to mock all dependencies. Add test application.yml files where needed (H2, disabled Eureka/Config).
```

### 2.6 Add Idempotency Protection (GAP-RE-05)

**Severity: High | Effort: Medium**

Implement idempotency keys for fund transfer and utility payment endpoints.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add idempotency protection:
1. Add an X-Idempotency-Key header requirement for POST /api/v1/transfer and POST /api/v1/utility-payment.
2. In fund-transfer-service:
   - Add an idempotencyKey column to FundTransferEntity with a unique constraint.
   - Before processing, check if a transfer with the given idempotency key already exists; if so, return the existing result.
3. In utility-payment-service:
   - Add an idempotencyKey column to UtilityPaymentEntity with a unique constraint.
   - Same duplicate check logic.
4. Update the API Gateway to forward the X-Idempotency-Key header to downstream services.
5. Add unit tests verifying that duplicate requests return the original response without re-processing.
```

### 2.7 Add Retry Policies and Fallback Behavior (GAP-RE-02, GAP-RE-04)

**Severity: Medium | Effort: Medium**

Configure retry policies on Feign clients with exponential backoff.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add Feign retry policies:
1. Add spring-retry dependency to fund-transfer-service, user-service, and utility-payment-service.
2. Configure Feign retryer in each service's application.yml:
   - Max attempts: 3
   - Initial interval: 100ms
   - Max interval: 1000ms
   - Only retry on 5xx responses and connection errors, NOT on 4xx
3. Ensure retry works correctly with the circuit breaker (retry inside circuit breaker).
4. Add a @Retryable annotation on the Feign client methods if Spring Retry is preferred over Feign Retryer.
5. Test retry behavior with unit tests using WireMock or mock responses.
```

### 2.8 Add Rate Limiting at API Gateway (GAP-SE-04)

**Severity: Medium | Effort: Medium**

Configure Spring Cloud Gateway rate limiting.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add rate limiting to the API Gateway:
1. Add spring-boot-starter-data-redis-reactive dependency to the API gateway.
2. Add a Redis service to docker-compose.yml.
3. Configure RequestRateLimiter filter in the gateway routes (via Spring Cloud Config or local application.yml):
   - Default: 10 requests/second per user
   - Registration endpoint: 3 requests/minute per IP
   - Fund transfer: 5 requests/second per user
4. Configure the KeyResolver to use the JWT subject claim (authenticated user ID) or remote IP for unauthenticated endpoints.
5. Test the rate limiter with a load test or curl script.
```

### 2.9 Add Structured Logging (GAP-OB-01)

**Severity: Medium | Effort: Medium**

Switch to JSON-formatted logging with correlation IDs.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add structured JSON logging:
1. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency to all services.
2. Create a logback-spring.xml in each service's src/main/resources/ that:
   - Uses LogstashEncoder for JSON output in non-dev profiles
   - Keeps human-readable console output for the dev profile
   - Includes MDC fields: traceId, spanId, serviceName, userId
3. Ensure Micrometer's Brave integration propagates traceId/spanId into MDC automatically.
4. Add the X-Auth-Id (user ID) to MDC in the AppAuthUserFilter.
5. Verify JSON log output by running a service with the docker profile.
```

### 2.10 Configure OpenAPI Documentation (GAP-AD-01)

**Severity: Medium | Effort: Small**

Fix Springdoc dependency and add global API metadata.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, fix and improve OpenAPI documentation:
1. In core-banking-service, fund-transfer-service, user-service, and utility-payment-service build.gradle:
   - Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui:2.1.0 (these are WebMVC services, not WebFlux)
2. Create an OpenApiConfig.java class in each service with @OpenAPIDefinition providing:
   - Title, version, description, contact info
3. Add @ApiResponse annotations to all controller methods documenting success and error responses.
4. Verify Swagger UI is accessible at /swagger-ui.html for each service.
```

### 2.11 Return Pagination Metadata (GAP-AD-02)

**Severity: Medium | Effort: Small**

Return `Page<T>` from list endpoints instead of `List<T>`.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, return pagination metadata from all list endpoints:
1. In FundTransferController (fund-transfer-service): change readFundTransfers to return ResponseEntity<Page<FundTransfer>>.
2. In UtilityPaymentController: change readPayments to return ResponseEntity<Page<UtilityPayment>>.
3. In UserController (user-service): change readUsers to return ResponseEntity<Page<User>>.
4. In UserController (core-banking-service): change readUsers to return ResponseEntity<Page<User>>.
5. Update corresponding service methods to return Page<T> instead of List<T>.
6. Update existing tests to verify pagination metadata is present in responses.
```

---

## Phase 3: Polish

> Focus: Production hardening, advanced testing, and long-term architectural improvements.

### 3.1 Implement Saga Pattern for Distributed Transactions (GAP-RE-06)

**Severity: High | Effort: Large**

Replace the current two-phase pattern with a proper saga for fund transfers and utility payments.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, implement a saga pattern for fund transfers:
1. Design a choreography-based saga for the fund transfer flow:
   - Step 1: Create local FundTransferEntity with PENDING status
   - Step 2: Call core-banking-service to execute the transfer
   - Step 3: On success → update to SUCCESS; On failure → update to FAILED
   - Compensation: If step 3 fails after step 2 succeeds, implement a reversal API in core-banking-service
2. Add a /api/v1/transaction/fund-transfer/reverse endpoint to core-banking-service.
3. Add a scheduled job in fund-transfer-service to reconcile PENDING transfers older than 5 minutes.
4. Implement the same pattern for utility payments.
5. Add integration tests using Testcontainers to verify the saga flow.
```

### 3.2 Add Integration Tests with Testcontainers (GAP-TE-02)

**Severity: High | Effort: Large**

Create `@SpringBootTest` integration tests with real MySQL and mocked Feign clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add integration tests:
1. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies to core-banking-service, fund-transfer-service, user-service, and utility-payment-service.
2. For core-banking-service:
   - Create an integration test class that starts a MySQL Testcontainer.
   - Test the full flow: create user → create account → fund transfer → verify balances.
3. For fund-transfer-service:
   - Create an integration test with MySQL Testcontainer + WireMock for core-banking-service.
   - Test: submit transfer → verify local entity created → verify Feign call made → verify status updated.
4. For utility-payment-service:
   - Same pattern as fund-transfer with WireMock.
5. For user-service:
   - Integration test with MySQL Testcontainer + WireMock for both core-banking and Keycloak.
6. Add a Gradle test task alias 'integrationTest' that runs only integration test classes.
```

### 3.3 Add Contract Tests (GAP-TE-03)

**Severity: Medium | Effort: Large**

Implement Spring Cloud Contract tests between consumers and the core-banking-service provider.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add Spring Cloud Contract tests:
1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider side).
2. Write contracts (in Groovy DSL or YAML) for all core-banking-service endpoints:
   - GET /api/v1/account/bank-account/{number} — success and not-found
   - GET /api/v1/user/{identification} — success and not-found
   - POST /api/v1/transaction/fund-transfer — success and insufficient-funds
   - POST /api/v1/transaction/util-payment — success and insufficient-funds
3. Generate the contract test stubs JAR from core-banking-service.
4. Add spring-cloud-starter-contract-stub-runner to fund-transfer-service, user-service, and utility-payment-service.
5. Write consumer contract tests in each service that verify Feign clients against the stubs.
6. Run all contract tests and verify they pass.
```

### 3.4 Fix ApplicationTests to Work Without Infrastructure (GAP-TE-04)

**Severity: Medium | Effort: Small**

Add proper test configurations so context-load tests work standalone.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, fix ApplicationTests:
1. For each service (api-gateway, config-server, service-registry, user-service, fund-transfer-service, utility-payment-service):
   - Create or update src/test/resources/application.yml with:
     - H2 in-memory database (for DB services)
     - Disabled Eureka client (eureka.client.enabled: false)
     - Disabled Config Server import (spring.cloud.config.enabled: false)
     - Mock or disabled Keycloak (for user-service)
   - Ensure the *ApplicationTests context loads successfully with './gradlew test'.
2. Run all tests and verify green results.
```

### 3.5 Add Prometheus Metrics and Custom Business Metrics (GAP-OB-03)

**Severity: Medium | Effort: Medium**

Expose Prometheus-compatible metrics with custom counters.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add Prometheus metrics:
1. Add io.micrometer:micrometer-registry-prometheus dependency to all services.
2. Expose /actuator/prometheus endpoint (management.endpoints.web.exposure.include: health,info,prometheus).
3. Add custom metrics using MeterRegistry in:
   - core-banking-service: Counter for fund_transfers_total (success/failure), utility_payments_total, Gauge for active_accounts
   - fund-transfer-service: Counter for transfer_requests_total, Timer for transfer_processing_duration
   - utility-payment-service: Counter for payment_requests_total, Timer for payment_processing_duration
   - user-service: Counter for user_registrations_total, user_approvals_total
4. Add a Prometheus service to docker-compose.yml that scrapes all service metrics endpoints.
5. Optionally add a Grafana service with a pre-configured dashboard JSON.
```

### 3.6 Add Custom Health Indicators (GAP-OB-02)

**Severity: Low | Effort: Small**

Create service-specific health checks.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add custom health indicators:
1. In user-service: create a KeycloakHealthIndicator that checks Keycloak connectivity.
2. In fund-transfer-service and utility-payment-service: create a CoreBankingHealthIndicator that calls a lightweight endpoint on core-banking-service.
3. In all services: expose health details (management.endpoint.health.show-details: always).
4. Configure readiness and liveness probes in application.yml:
   - management.endpoint.health.probes.enabled: true
   - management.health.livenessState.enabled: true
   - management.health.readinessState.enabled: true
5. Update Docker Compose with healthcheck configurations for each service.
```

### 3.7 Standardize REST Endpoint Naming (GAP-AD-04)

**Severity: Low | Effort: Small**

Fix non-standard REST endpoint paths.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, standardize REST endpoints:
1. In user-service UserController:
   - Change POST /api/v1/bank-users/register to POST /api/v1/bank-users
   - Change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
2. Update the API Gateway security configuration to permit POST /user/api/v1/bank-users (instead of the /register path).
3. Update the API Gateway route configuration if path-based routing is affected.
4. Update the Postman collection if it exists in the repo.
5. Run tests and verify the gateway routes correctly.
```

### 3.8 Normalize Package Structure (GAP-CO-03)

**Severity: Low | Effort: Small**

Align package naming conventions across all services.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, normalize package structure:
1. Adopt this standard structure for all services:
   - com.javatodev.finance.controller
   - com.javatodev.finance.service
   - com.javatodev.finance.model.entity
   - com.javatodev.finance.model.dto
   - com.javatodev.finance.model.dto.request
   - com.javatodev.finance.model.dto.response
   - com.javatodev.finance.model.mapper
   - com.javatodev.finance.repository
   - com.javatodev.finance.configuration
   - com.javatodev.finance.exception
2. In utility-payment-service: move com.javatodev.finance.repository to com.javatodev.finance.model.repository (or vice versa — pick one convention).
3. In utility-payment-service: move model.rest.request to model.dto.request and model.rest.response to model.dto.response.
4. Rename BankingCoreRestClient to BankingCoreFeignClient for consistency.
5. Update all imports and verify compilation.
```

### 3.9 Add CORS Configuration (GAP-SE-03)

**Severity: Medium | Effort: Small**

Configure CORS policies at the API Gateway.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add CORS configuration to the API Gateway:
1. In SecurityConfiguration.java, add a CorsConfigurationSource bean that:
   - Allows specific origins (configurable via application.yml)
   - Allows methods: GET, POST, PATCH, PUT, DELETE, OPTIONS
   - Allows headers: Authorization, Content-Type, X-Idempotency-Key
   - Exposes headers: X-Request-Id
   - Max age: 3600 seconds
2. Enable CORS in the SecurityWebFilterChain: httpSecurity.cors(cors -> cors.configurationSource(...))
3. Add the allowed origins to the externalized configuration.
4. Test CORS headers with a curl preflight request.
```

### 3.10 Add Tracing Fallback Defaults (GAP-OB-04)

**Severity: Low | Effort: Small**

Add local fallback tracing configuration.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repository, add tracing fallback defaults:
1. In each service's application.yml (not just the external config), add default tracing properties:
   management.tracing.sampling.probability: 1.0
   management.zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans
2. These defaults will be overridden by the Config Server when available, but provide a working fallback for local development.
3. Add a note in README about Zipkin being optional for local development.
```

---

## Summary Matrix

| Phase | ID | Gap Reference | Item | Severity | Effort |
|-------|----|---------------|------|----------|--------|
| 1 | 1.1 | GAP-EH-01, GAP-EH-03 | Fix error handler status codes | High | Small |
| 1 | 1.2 | GAP-SE-01 | Externalize hardcoded credentials | Critical | Small |
| 1 | 1.3 | GAP-SE-02 | Add input validation | High | Medium |
| 1 | 1.4 | GAP-SE-05 | Remove sensitive data from logs | Medium | Small |
| 1 | 1.5 | GAP-RE-03 | Configure Feign timeouts | High | Small |
| 1 | 1.6 | GAP-EH-02 | Fix raw ResponseEntity types | Medium | Small |
| 1 | 1.7 | GAP-SE-07 | Add dependency vulnerability scanning | Medium | Small |
| 2 | 2.1 | GAP-RE-01 | Add circuit breakers | High | Medium |
| 2 | 2.2 | GAP-CO-01 | Extract shared library | High | Medium |
| 2 | 2.3 | GAP-CO-02 | Create multi-module Gradle build | Medium | Medium |
| 2 | 2.4 | GAP-EH-04 | Add Feign error handling | High | Medium |
| 2 | 2.5 | GAP-TE-01 | Add unit tests for all services | High | Large |
| 2 | 2.6 | GAP-RE-05 | Add idempotency protection | High | Medium |
| 2 | 2.7 | GAP-RE-02, GAP-RE-04 | Add retry policies and fallbacks | Medium | Medium |
| 2 | 2.8 | GAP-SE-04 | Add rate limiting | Medium | Medium |
| 2 | 2.9 | GAP-OB-01 | Add structured logging | Medium | Medium |
| 2 | 2.10 | GAP-AD-01 | Configure OpenAPI documentation | Medium | Small |
| 2 | 2.11 | GAP-AD-02 | Return pagination metadata | Medium | Small |
| 3 | 3.1 | GAP-RE-06 | Implement saga pattern | High | Large |
| 3 | 3.2 | GAP-TE-02 | Add integration tests (Testcontainers) | High | Large |
| 3 | 3.3 | GAP-TE-03 | Add contract tests | Medium | Large |
| 3 | 3.4 | GAP-TE-04 | Fix ApplicationTests | Medium | Small |
| 3 | 3.5 | GAP-OB-03 | Add Prometheus metrics | Medium | Medium |
| 3 | 3.6 | GAP-OB-02 | Add custom health indicators | Low | Small |
| 3 | 3.7 | GAP-AD-04 | Standardize REST endpoint naming | Low | Small |
| 3 | 3.8 | GAP-CO-03 | Normalize package structure | Low | Small |
| 3 | 3.9 | GAP-SE-03 | Add CORS configuration | Medium | Small |
| 3 | 3.10 | GAP-OB-04 | Add tracing fallback defaults | Low | Small |
