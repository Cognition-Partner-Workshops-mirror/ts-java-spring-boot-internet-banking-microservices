# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes an estimated effort and a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (Critical & High severity, Small effort)

These items address the most impactful issues with the least effort. They should be completed first to establish a secure, stable baseline.

---

### 1.1 Fix Exception Catch-All Handler (Gap 2.2)

**Severity:** Critical | **Effort:** Small

**Problem:** All `GlobalExceptionHandler` classes catch `Exception.class` and return `400 Bad Request` with a string that leaks the full exception message and stack trace.

**Fix:** Return `500 Internal Server Error` for unhandled exceptions with a generic error message. Use the standard `ErrorResponse` object for all error responses.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, update every GlobalExceptionHandler class
(in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and
internet-banking-utility-payment-service) so that:

1. The catch-all @ExceptionHandler({Exception.class}) method returns HTTP 500 (not 400).
2. The response body uses the ErrorResponse object with code="INTERNAL_SERVER_ERROR" and
   message="An unexpected error occurred. Please try again later." — never expose the raw exception.
3. Add a log.error("Unhandled exception", e) call so the details are still logged server-side.
4. Make sure all ResponseEntity return types use proper generics: ResponseEntity<ErrorResponse>.

Run the existing tests to verify nothing breaks.
```
</details>

---

### 1.2 Add Bean Validation to Request DTOs (Gaps 2.3 / 4.2)

**Severity:** Critical | **Effort:** Small

**Problem:** No `@Valid`, `@NotNull`, `@NotBlank`, or `@Min` annotations on any request DTO. Invalid data (negative amounts, null accounts) reaches business logic.

**Fix:** Add `spring-boot-starter-validation` dependency and annotate all request DTOs. Add `@Valid` to controller method parameters.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add input validation to all request DTOs:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to each service's build.gradle.
2. Annotate request DTO fields:
   - FundTransferRequest (core-banking-service): @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @DecimalMin("0.01") amount
   - FundTransferRequest (fund-transfer-service): same, plus @NotBlank authID
   - UtilityPaymentRequest (core-banking-service): @NotNull providerId,
     @NotNull @DecimalMin("0.01") amount, @NotBlank referenceNumber, @NotBlank account
   - UtilityPaymentRequest (utility-payment-service): same fields
   - User registration DTO in user-service: @NotBlank identification, @Email @NotBlank email
3. Add @Valid annotation before @RequestBody in all controller POST/PATCH methods.
4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns
   400 with a structured ErrorResponse listing the field-level validation errors.
5. Run existing tests to verify nothing breaks.
```
</details>

---

### 1.3 Externalize Hardcoded Credentials (Gap 4.1)

**Severity:** High | **Effort:** Small

**Problem:** Database passwords, Keycloak admin credentials, and client secrets are hardcoded in `docker-compose.yml`, `privileges.sql`, and test `application.yml`.

**Fix:** Use environment variables with `.env` files (git-ignored) for Docker Compose. Document required variables.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, externalize all hardcoded credentials:

1. Create a docker-compose/.env.example file listing all required variables with placeholder values:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD,
   KC_DB_USERNAME.
2. Update docker-compose/docker-compose.yml and docker-compose-support-apps.yml to use
   ${VARIABLE} syntax for all passwords.
3. Update docker-compose/mysql/Dockerfile and privileges.sql to reference env vars.
4. Add docker-compose/.env to .gitignore.
5. Create a docker-compose/.env file with the current default values for backward compatibility.
6. Update the README.md Installation section to mention copying .env.example to .env.
```
</details>

---

### 1.4 Add Feign Client Timeout Configuration (Gap 7.3)

**Severity:** High | **Effort:** Small

**Problem:** No explicit timeouts configured for Feign clients. Slow responses from core-banking-service can block caller threads indefinitely.

**Fix:** Configure connect and read timeouts via application properties.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add timeout configuration to all
Feign clients:

1. In each service that uses OpenFeign (user-service, fund-transfer-service, utility-payment-service),
   add the following to their respective config files (or application.yml if config server isn't
   available for local dev):

   spring.cloud.openfeign.client.config.default.connect-timeout: 5000
   spring.cloud.openfeign.client.config.default.read-timeout: 10000

2. Also add these as fallback properties in each service's src/main/resources/application.yml so they
   work even if the config server is unreachable.
3. Verify the application starts correctly with the new configuration.
```
</details>

---

### 1.5 Add Prometheus Metrics Export (Gap 6.3)

**Severity:** High | **Effort:** Small

**Problem:** No metrics export despite Actuator being present. Prometheus is listed in the tech stack but not wired.

**Fix:** Add Micrometer Prometheus registry and expose the endpoint.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, enable Prometheus metrics for all services:

1. Add 'io.micrometer:micrometer-registry-prometheus' to the dependencies in build.gradle of:
   core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service,
   internet-banking-utility-payment-service, internet-banking-api-gateway.
2. In each service's application.yml, add:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
     metrics:
       export:
         prometheus:
           enabled: true
3. Verify the /actuator/prometheus endpoint returns metrics by running the application.
```
</details>

---

### 1.6 Fix Swagger/OpenAPI Dependency (Gap 5.5)

**Severity:** Medium | **Effort:** Small

**Problem:** Services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC (servlet) apps.

**Fix:** Switch to `springdoc-openapi-starter-webmvc-ui`.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the OpenAPI dependency:

1. In core-banking-service/build.gradle, internet-banking-user-service/build.gradle,
   internet-banking-fund-transfer-service/build.gradle, and
   internet-banking-utility-payment-service/build.gradle:
   Replace: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
   With:    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
2. Do NOT change the api-gateway (it uses WebFlux and should keep the webflux variant).
3. Verify each service starts without errors and that /swagger-ui.html loads.
```
</details>

---

### 1.7 Fix Raw ResponseEntity Generics (Gap 2.1)

**Severity:** Medium | **Effort:** Small

**Problem:** Controller methods return raw `ResponseEntity` without type parameters.

**Fix:** Add proper generic type parameters to all controller return types.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add proper generic types to all
controller ResponseEntity return types:

1. In every @RestController class across all services, replace raw ResponseEntity with the
   appropriate typed version:
   - ResponseEntity<BankAccount> for account lookups
   - ResponseEntity<FundTransferResponse> for fund transfers
   - ResponseEntity<List<FundTransfer>> for list endpoints
   - ResponseEntity<UtilityPaymentResponse> for utility payments
   - etc.
2. Fix any resulting compilation issues.
3. Run existing tests to verify nothing breaks.
```
</details>

---

### 1.8 Fix Application Context Tests (Gap 3.4)

**Severity:** Medium | **Effort:** Small

**Problem:** Default `@SpringBootTest` tests in most services fail because they try to connect to Config Server and Eureka.

**Fix:** Disable Config Server and Eureka client in test profiles; use `@SpringBootTest` with appropriate test slices.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the application context tests so
they pass without external infrastructure:

1. For each service (user-service, fund-transfer-service, utility-payment-service, api-gateway,
   config-server, service-registry), update src/test/resources/application.yml (create if missing):
   - Set spring.cloud.config.enabled: false
   - Set eureka.client.enabled: false
   - Set spring.autoconfigure.exclude to disable Keycloak auto-config where needed
2. For api-gateway, use @SpringBootTest(webEnvironment = RANDOM_PORT) and mock the JWT decoder.
3. Verify all context tests pass by running ./gradlew test in each service directory.
```
</details>

---

### 1.9 Add Dependency Vulnerability Scanning (Gap 4.7)

**Severity:** Medium | **Effort:** Small

**Problem:** No OWASP Dependency-Check or equivalent is configured.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add OWASP dependency-check to
all services:

1. In each service's build.gradle, add the plugin:
   plugins { id 'org.owasp.dependencycheck' version '9.0.10' }
2. Configure the plugin to fail on CVSS score >= 7:
   dependencyCheck { failBuildOnCVSS = 7.0 }
3. Run ./gradlew dependencyCheckAnalyze in one service to verify it works.
4. Document the command in the README under a new "Security Scanning" section.
```
</details>

---

### 1.10 Add Pagination Wrapper Response (Gap 5.3)

**Severity:** Medium | **Effort:** Small

**Problem:** Paginated endpoints return `List<T>`, losing total count and page metadata.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, fix pagination responses:

1. In the user-service, fund-transfer-service, and utility-payment-service, change the service
   methods that call findAll(pageable) to return Page<T> instead of List<T>.
2. Update the controllers to return ResponseEntity<Page<T>>.
3. Similarly, update core-banking-service UserController.readUsers to return Page<User>.
4. Verify the paginated endpoints now return JSON with content[], totalElements, totalPages,
   number, size fields.
```
</details>

---

## Phase 2: Important (High severity + Medium effort, or structural improvements)

These items require more effort but significantly improve reliability, security, and maintainability.

---

### 2.1 Extract Shared Library (Gap 1.2)

**Severity:** High | **Effort:** Medium

**Problem:** ~10 classes are copy-pasted identically across 3–4 services.

**Fix:** Create a shared Gradle module (or internal Maven library) with common classes.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, extract duplicated code into a
shared library:

1. Create a new Gradle subproject: internet-banking-common/
2. Move these classes into the common module under com.javatodev.finance.common:
   - BaseMapper
   - AuditAware, AuditConfig, AuditorAwareConfig
   - ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
3. Set up a root settings.gradle that includes all services + the common module.
4. Add 'implementation project(":internet-banking-common")' to each service's build.gradle.
5. Remove the duplicate classes from each service.
6. Run all tests to verify everything still compiles and passes.
```
</details>

---

### 2.2 Add Error Compensation in Orchestrator Services (Gap 2.4)

**Severity:** High | **Effort:** Medium

**Problem:** If the Feign call to core-banking-service fails, the local `fund_transfer`/`utility_payment` record stays in `PENDING`/`PROCESSING` forever.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add error handling to the
orchestrator services:

1. In FundTransferService.fundTransfer():
   - Wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch.
   - On exception: update the FundTransferEntity status to FAILED, save it,
     then re-throw a service-specific exception.
   - Log the failure with the entity ID for traceability.

2. In UtilityPaymentService.utilPayment():
   - Same pattern: wrap the bankingCoreRestClient.utilityPayment() call in try-catch.
   - On exception: update UtilityPaymentEntity status to FAILED, save, re-throw.

3. Add unit tests for the failure scenarios in both services.
```
</details>

---

### 2.3 Add Circuit Breakers (Gap 7.1)

**Severity:** High | **Effort:** Medium

**Problem:** No circuit breakers; core-banking-service failure cascades to all upstream services.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breakers:

1. Add these dependencies to fund-transfer-service, utility-payment-service, and user-service:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure default circuit breaker settings in application.yml:
   resilience4j.circuitbreaker.instances.coreBankingService:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     permittedNumberOfCallsInHalfOpenState: 3

3. Annotate the Feign client calls (or service methods that call Feign) with
   @CircuitBreaker(name = "coreBankingService", fallbackMethod = "...").

4. Implement fallback methods that return meaningful error responses.

5. Add retry configuration:
   resilience4j.retry.instances.coreBankingService:
     maxAttempts: 3
     waitDuration: 1s
```
</details>

---

### 2.4 Add Rate Limiting at API Gateway (Gap 4.3)

**Severity:** High | **Effort:** Medium

**Problem:** No rate limiting; endpoints are vulnerable to abuse.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting to the API gateway:

1. Add Spring Cloud Gateway's built-in RequestRateLimiter filter.
2. Since Redis may not be available, use an in-memory rate limiter implementation
   (e.g., Bucket4j with spring-boot-starter-cache) or configure the
   RedisRateLimiter if Redis can be added to docker-compose.
3. Apply rate limits:
   - POST endpoints (fund-transfer, utility-payment, user registration): 10 requests/minute per user
   - GET endpoints: 60 requests/minute per user
4. Return HTTP 429 (Too Many Requests) when limits are exceeded.
5. Add the rate limiter configuration to the gateway's application.yml.
```
</details>

---

### 2.5 Add Authentication to Internal Services (Gap 4.5)

**Severity:** High | **Effort:** Medium

**Problem:** Internal microservices accept unauthenticated requests if accessed directly (bypassing gateway).

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add security to internal services:

Option A (simpler): Add a shared API key mechanism:
1. Define a shared internal API key as an environment variable.
2. Create a servlet filter in the common library that validates an X-Internal-Api-Key header.
3. The API gateway adds this header when proxying requests.
4. Internal services reject requests without the valid header (except health checks).

Option B (more robust): Propagate JWT tokens:
1. Add spring-boot-starter-oauth2-resource-server to each internal service.
2. Configure each service to validate JWTs against Keycloak's JWK Set URI.
3. Configure Feign clients to forward the Authorization header from incoming requests.

Implement Option A for simplicity, with documentation on migrating to Option B later.
```
</details>

---

### 2.6 Add Idempotency Keys (Gap 7.6)

**Severity:** High | **Effort:** Medium

**Problem:** Fund transfer and payment endpoints have no idempotency protection.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add idempotency key support:

1. Create an IdempotencyKey mechanism:
   - Clients send an X-Idempotency-Key header with POST requests.
   - Before processing, the service checks if this key has been seen before.
   - If yes, return the cached response. If no, process and cache the response.

2. Implement in fund-transfer-service:
   - Add an idempotency_key column to the fund_transfer table (unique constraint).
   - In FundTransferController, extract the header and pass it to the service.
   - FundTransferService checks for existing record with this key before processing.

3. Implement the same pattern in utility-payment-service.

4. Return HTTP 409 Conflict if an idempotency key is reused with different request parameters.
```
</details>

---

### 2.7 Set Up Multi-Project Gradle Build (Gap 1.1)

**Severity:** Medium | **Effort:** Medium

**Problem:** No unified build; each service is independently built.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, create a multi-project Gradle build:

1. Create a root-level settings.gradle that includes all service directories:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service', 'internet-banking-user-service', etc.

2. Create a root-level build.gradle with shared configuration:
   - Common Java 21 sourceCompatibility
   - Common Spring Boot and Spring Cloud versions
   - Common test configuration
   - Common dependency versions in an ext block or version catalog

3. Simplify each service's build.gradle to inherit from the root.
4. Remove individual settings.gradle from each service.
5. Verify ./gradlew build from the root compiles and tests all services.
6. Update the README with the new build commands.
```
</details>

---

### 2.8 Improve Logging Consistency (Gap 6.1)

**Severity:** Medium | **Effort:** Medium

**Problem:** Inconsistent log formats; sensitive data logged; no structured logging.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, standardize logging:

1. Add structured JSON logging using logback-spring.xml in each service:
   - Use net.logstash.logback:logstash-logback-encoder for JSON output.
   - Include MDC fields: traceId, spanId, service name.

2. Audit all log.info/log.error calls:
   - Remove toString() on request objects that contain account numbers or PII.
   - Use masked representations for sensitive fields (e.g., last 4 digits of account).

3. Standardize log levels:
   - INFO: request received, request completed
   - WARN: validation failures, business rule violations
   - ERROR: unhandled exceptions, integration failures

4. Add a logback-spring.xml template to the shared common module.
```
</details>

---

## Phase 3: Polish (Medium/Low severity, or Large effort items)

These items round out the codebase to production-grade quality.

---

### 3.1 Add Comprehensive Unit Tests (Gap 3.1)

**Severity:** High | **Effort:** Large

**Problem:** Only core-banking-service has unit tests. Other services have zero test coverage.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add unit tests for all services:

1. internet-banking-user-service:
   - Test UserService: register (happy path, duplicate email, invalid email, core-banking Feign failure)
   - Test KeycloakUserService: user creation, user retrieval
   - Mock BankingCoreRestClient and KeycloakManager

2. internet-banking-fund-transfer-service:
   - Test FundTransferService: fundTransfer (happy path, Feign failure, compensation logic)
   - Test readAllTransfers with pagination
   - Mock BankingCoreFeignClient and FundTransferRepository

3. internet-banking-utility-payment-service:
   - Test UtilityPaymentService: utilPayment (happy path, Feign failure, compensation logic)
   - Test readPayments with pagination
   - Mock BankingCoreRestClient and UtilityPaymentRepository

Target: >= 80% line coverage for service classes.
```
</details>

---

### 3.2 Add Integration Tests (Gap 3.2)

**Severity:** High | **Effort:** Large

**Problem:** No tests verify HTTP endpoint behavior, serialization, or database queries.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests:

1. For core-banking-service:
   - Use @SpringBootTest with H2 database and Flyway enabled.
   - Test AccountController: GET /api/v1/account/bank-account/{number} returns correct JSON.
   - Test TransactionController: POST /api/v1/transaction/fund-transfer processes correctly.
   - Test UserController: GET /api/v1/user/{id} returns user with accounts.
   - Verify proper HTTP status codes for error cases (404, 400, 500).

2. For user-service, fund-transfer-service, utility-payment-service:
   - Use @SpringBootTest with @MockBean for Feign clients.
   - Use MockMvc to test controller endpoints.
   - Verify request validation (invalid input returns 400 with field errors).
   - Verify response serialization matches expected JSON structure.
```
</details>

---

### 3.3 Add Contract Tests (Gap 3.3)

**Severity:** Medium | **Effort:** Large

**Problem:** No consumer-driven contract tests. Feign client DTOs can silently diverge from provider APIs.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests:

1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider side).
2. Define contracts in core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/account/bank-account/{number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}

3. Generate and publish contract stubs.

4. Add spring-cloud-starter-contract-stub-runner to consumer services
   (user-service, fund-transfer-service, utility-payment-service).
5. Write consumer contract tests that verify Feign clients work with the stubs.
```
</details>

---

### 3.4 Add Custom Health Checks (Gap 6.2)

**Severity:** Medium | **Effort:** Small

**Problem:** Default health indicators don't cover critical dependencies.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators:

1. In user-service, add a KeycloakHealthIndicator that checks Keycloak connectivity.
2. In fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator
   that pings core-banking-service's actuator/health endpoint via Feign.
3. In core-banking-service, the default DataSourceHealthIndicator should suffice,
   but verify it's enabled.
4. Configure health endpoint to show details:
   management.endpoint.health.show-details: always
```
</details>

---

### 3.5 Add Gateway Access Logging (Gap 6.5)

**Severity:** Medium | **Effort:** Small

**Problem:** No audit trail or access logging at the API gateway.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add access logging to the gateway:

1. Add a GlobalFilter that logs: timestamp, HTTP method, path, response status,
   latency, authenticated user ID (from X-Auth-Id header), and trace ID.
2. Use structured JSON logging format.
3. Do NOT log request/response bodies (security concern).
4. Add a correlation ID (X-Correlation-Id) header to all requests if not already present.
```
</details>

---

### 3.6 Add Fallback Behavior for Feign Clients (Gap 7.4)

**Severity:** Medium | **Effort:** Medium

**Problem:** Feign client failures return raw exception details to the user.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Feign fallback factories:

1. For each Feign client interface, create a FallbackFactory:
   - BankingCoreFeignClientFallback (in fund-transfer-service)
   - BankingCoreRestClientFallback (in utility-payment-service)
   - BankingCoreRestClientFallback (in user-service)

2. Fallback behavior:
   - Log the cause of the failure.
   - Throw a user-friendly SimpleBankingGlobalException with a clear message like
     "Banking core service is temporarily unavailable. Please try again later."
   - Use appropriate error codes.

3. Register the fallback factories in the @FeignClient annotation.
```
</details>

---

### 3.7 Add Bulkhead Isolation (Gap 7.5)

**Severity:** Medium | **Effort:** Medium

**Problem:** All Feign calls share the default thread pool; one slow endpoint can starve others.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add bulkhead isolation:

1. Add Resilience4j bulkhead configuration to each service that calls core-banking-service:
   resilience4j.bulkhead.instances.coreBankingService:
     maxConcurrentCalls: 20
     maxWaitDuration: 500ms

2. Annotate Feign client methods or service methods with @Bulkhead.

3. Configure separate bulkheads for different operation types if needed
   (e.g., read operations vs. write operations).
```
</details>

---

### 3.8 Standardize Response Envelope (Gap 5.6)

**Severity:** Medium | **Effort:** Medium

**Problem:** No consistent wrapper for API responses.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, create a standard API response wrapper:

1. Create an ApiResponse<T> class in the shared common module:
   - T data (the payload)
   - List<ApiError> errors (for error responses)
   - Map<String, Object> meta (for pagination, timestamps, request IDs)

2. Create a ResponseAdvice (@RestControllerAdvice) that automatically wraps
   all controller responses in ApiResponse.

3. Update GlobalExceptionHandler to return ApiResponse<Void> with error details.

4. Ensure backward compatibility by allowing clients to opt out via a header
   (e.g., X-Raw-Response: true) during the transition period.
```
</details>

---

### 3.9 Standardize Package Structure (Gap 1.3)

**Severity:** Low | **Effort:** Medium

**Problem:** Package naming conventions differ across services.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, standardize the package structure
across all services to follow this convention:

com.javatodev.finance.{service-name}
  ├── controller/
  ├── service/
  │   └── client/          (Feign clients)
  ├── model/
  │   ├── entity/
  │   ├── dto/
  │   │   ├── request/
  │   │   └── response/
  │   ├── mapper/
  │   └── enums/
  ├── repository/
  ├── configuration/
  │   ├── security/
  │   ├── feign/
  │   └── audit/
  └── exception/

Rename and move classes to match this structure in all services.
Update all imports accordingly. Run tests to verify nothing breaks.
```
</details>

---

### 3.10 Add Retry Policies (Gap 7.2)

**Severity:** Medium | **Effort:** Small

**Problem:** No retry on transient Feign client failures.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add retry configuration:

1. Add Spring Retry to each service that uses Feign:
   implementation 'org.springframework.retry:spring-retry'

2. Configure Feign-level retries in application.yml:
   spring.cloud.openfeign.client.config.default:
     retryer: feign.Retryer.Default
     # Retries up to 3 times with 100ms initial interval, 1s max interval

3. Ensure retries only apply to idempotent operations (GET requests).
   POST requests should NOT be retried automatically (use idempotency keys from 2.6 instead).
```
</details>

---

## Implementation Priority Summary

| Phase | Item | Gap Ref | Severity | Effort |
|---|---|---|---|---|
| **1** | Fix exception catch-all handler | 2.2 | Critical | Small |
| **1** | Add Bean Validation | 2.3/4.2 | Critical | Small |
| **1** | Externalize credentials | 4.1 | High | Small |
| **1** | Add Feign timeouts | 7.3 | High | Small |
| **1** | Add Prometheus metrics | 6.3 | High | Small |
| **1** | Fix Swagger dependency | 5.5 | Medium | Small |
| **1** | Fix ResponseEntity generics | 2.1 | Medium | Small |
| **1** | Fix application context tests | 3.4 | Medium | Small |
| **1** | Add dependency vulnerability scan | 4.7 | Medium | Small |
| **1** | Add pagination wrapper | 5.3 | Medium | Small |
| **2** | Extract shared library | 1.2 | High | Medium |
| **2** | Add error compensation | 2.4 | High | Medium |
| **2** | Add circuit breakers | 7.1 | High | Medium |
| **2** | Add rate limiting | 4.3 | High | Medium |
| **2** | Secure internal services | 4.5 | High | Medium |
| **2** | Add idempotency keys | 7.6 | High | Medium |
| **2** | Multi-project Gradle build | 1.1 | Medium | Medium |
| **2** | Improve logging | 6.1 | Medium | Medium |
| **3** | Add unit tests | 3.1 | High | Large |
| **3** | Add integration tests | 3.2 | High | Large |
| **3** | Add contract tests | 3.3 | Medium | Large |
| **3** | Add custom health checks | 6.2 | Medium | Small |
| **3** | Add gateway access logging | 6.5 | Medium | Small |
| **3** | Add Feign fallbacks | 7.4 | Medium | Medium |
| **3** | Add bulkhead isolation | 7.5 | Medium | Medium |
| **3** | Standardize response envelope | 5.6 | Medium | Medium |
| **3** | Standardize package structure | 1.3 | Low | Medium |
| **3** | Add retry policies | 7.2 | Medium | Small |
