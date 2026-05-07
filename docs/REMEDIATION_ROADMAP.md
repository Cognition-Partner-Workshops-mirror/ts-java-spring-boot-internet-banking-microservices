# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes the gap reference, a brief description, and a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (Critical & High Severity, Small Effort)

These items address the most dangerous issues with the least effort. Complete these first.

### 1.1 Fix Utility Payment Double-Debit Bug (RE-8)

**Gap**: `TransactionService.utilPayment()` double-subtracts the amount from `availableBalance`.

**Impact**: Every utility payment deducts 2x the correct amount from the available balance.

**Fix**: Change line 64 in `TransactionService.java` from `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(...))` to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())` (since `actualBalance` was already reduced on the previous line).

```
Devin Prompt:
Fix the double-debit bug in core-banking-service TransactionService.utilPayment().
On line 64 of TransactionService.java, availableBalance is set to
actualBalance minus amount, but actualBalance was already reduced on line 63.
The availableBalance should be set to the already-reduced actualBalance value.
Apply the same fix pattern to internalFundTransfer() lines 91 and 100 where
availableBalance is similarly double-subtracted. Write unit tests to verify
the correct balance after each operation.
```

### 1.2 Stop Leaking Exception Details to Clients (EH-1)

**Gap**: The catch-all `handleException()` returns raw `"Exception occur inside API " + e` which exposes stack traces.

**Fix**: Return a structured `ErrorResponse` with a generic message and HTTP 500 status.

```
Devin Prompt:
In all services that have a GlobalExceptionHandler (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service), update the catch-all
handleException() method to:
1. Log the full exception at ERROR level (log.error("Unhandled exception", e))
2. Return HTTP 500 (not 400) with a structured ErrorResponse containing
   code="INTERNAL_ERROR" and message="An unexpected error occurred"
3. Never include the exception message or stack trace in the response body
```

### 1.3 Use Correct HTTP Status Codes (EH-2)

**Gap**: All errors return HTTP 400 regardless of the actual error type.

```
Devin Prompt:
Update GlobalExceptionHandler in all services to return appropriate HTTP
status codes:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity
- UserAlreadyRegisteredException → 409 Conflict
- InvalidEmailException, InvalidBankingUserException → 400 Bad Request
- SimpleBankingGlobalException (generic) → 400 Bad Request
- Exception (catch-all) → 500 Internal Server Error
Add separate @ExceptionHandler methods for each exception type.
```

### 1.4 Stop Logging Sensitive Data (OB-1)

**Gap**: `toString()` on request DTOs logs passwords, account numbers, and amounts.

```
Devin Prompt:
Audit all log statements across all services and redact sensitive data:
1. In internet-banking-user-service UserController.createUser(), do NOT log
   the full request (it contains the password). Log only the email.
2. In FundTransferController and FundTransferService, log only a masked
   version of account numbers (e.g., last 4 digits) and never log amounts
   at INFO level (use DEBUG).
3. In UtilityPaymentController and UtilityPaymentService, apply the same
   masking for account numbers.
4. Review all @Slf4j log.info() calls for PII or financial data.
```

### 1.5 Add Feign Client Timeouts (RE-3)

**Gap**: No HTTP client timeouts configured. Slow Core Banking responses block upstream threads indefinitely.

```
Devin Prompt:
Configure Feign client timeouts for all services that use OpenFeign
(internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service). Add the following to each
service's application configuration (via Spring Cloud Config or local
application.yml):

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000

Also configure OkHttp or Apache HttpClient connection pool settings if
the service uses feign-okhttp.
```

### 1.6 Add Spring Retry for Feign Calls (RE-2)

**Gap**: Transient network errors cause immediate failures with no retry.

```
Devin Prompt:
Add Spring Retry support to all Feign-calling services:
1. Add 'org.springframework.retry:spring-retry' and
   'org.springframework:spring-aspects' dependencies to build.gradle
2. Add @EnableRetry to each application's main class
3. Configure retry for GET requests only (reads are safe to retry):
   spring.cloud.openfeign.client.config.default.retryer with max 3 attempts
   and 1-second backoff
4. Do NOT retry POST requests (fund transfers and payments are not idempotent yet)
```

### 1.7 Fix Raw ResponseEntity Types (AP-1)

**Gap**: Controllers return `ResponseEntity` without type parameters, breaking OpenAPI schema generation.

```
Devin Prompt:
Add generic type parameters to all ResponseEntity return types across all
controllers:
- AccountController.getBankAccount() → ResponseEntity<BankAccount>
- AccountController.getUtilityAccount() → ResponseEntity<UtilityAccount>
- TransactionController.fundTransfer() → ResponseEntity<FundTransferResponse>
- TransactionController.utilPayment() → ResponseEntity<UtilityPaymentResponse>
- UserController (core) readUser() → ResponseEntity<User>
- UserController (core) readUsers() → ResponseEntity<List<User>>
- FundTransferController.sendFundTransfer() → ResponseEntity<FundTransferResponse>
- FundTransferController.readFundTransfers() → ResponseEntity<List<FundTransfer>>
- UtilityPaymentController methods similarly
Ensure the Swagger UI reflects the correct response schemas after this change.
```

### 1.8 Fix Swagger Dependency Mismatch (AP-7)

**Gap**: Spring MVC services incorrectly use `springdoc-openapi-starter-webflux-ui`.

```
Devin Prompt:
In core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and
internet-banking-utility-payment-service, replace the Swagger dependency:
  FROM: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
  TO:   implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
Keep the webflux variant ONLY in internet-banking-api-gateway (which uses
Spring WebFlux). Update the version to 2.5.0 for the gateway as well.
Verify Swagger UI loads at /swagger-ui.html for each service.
```

### 1.9 Add Prometheus Metrics (OB-4)

**Gap**: README lists Prometheus but no `micrometer-registry-prometheus` dependency exists.

```
Devin Prompt:
Add Prometheus metrics support to all services:
1. Add 'io.micrometer:micrometer-registry-prometheus' to each service's
   build.gradle dependencies
2. Ensure the actuator config exposes the prometheus endpoint:
   management.endpoints.web.exposure.include: health,info,prometheus
3. Verify /actuator/prometheus returns metrics in Prometheus exposition format
4. Add a sample prometheus.yml scrape config in docker-compose/ that scrapes
   all service metrics endpoints
```

### 1.10 Add Docker Compose Health Checks (RE-9)

**Gap**: No `healthcheck` definitions; unhealthy containers are not restarted.

```
Devin Prompt:
Add healthcheck definitions to docker-compose.yml for all application services.
Use Spring Boot Actuator health endpoints:
  healthcheck:
    test: ["CMD", "wget", "--spider", "-q", "http://localhost:<port>/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 60s
Add appropriate healthchecks for MySQL (mysqladmin ping), Keycloak (curl),
and Zipkin as well. Add restart: on-failure policy to all service containers.
```

---

## Phase 2: Important (Critical & High Severity, Medium/Large Effort)

These items require more effort but address critical risks.

### 2.1 Add Input Validation to All DTOs (SE-1)

**Gap**: No Bean Validation annotations anywhere. Any malformed data is accepted.

```
Devin Prompt:
Add Jakarta Bean Validation annotations to all request DTOs across all services:

1. FundTransferRequest (both core-banking and fund-transfer-service versions):
   - fromAccount: @NotBlank
   - toAccount: @NotBlank
   - amount: @NotNull @DecimalMin("0.01")

2. UtilityPaymentRequest (both versions):
   - providerId: @NotNull
   - amount: @NotNull @DecimalMin("0.01")
   - referenceNumber: @NotBlank
   - account: @NotBlank

3. User (user-service):
   - email: @NotBlank @Email
   - password: @NotBlank @Size(min=8)
   - identification: @NotBlank

4. UserUpdateRequest:
   - status: @NotNull

Add @Valid annotation to all @RequestBody parameters in controllers.
Add a MethodArgumentNotValidException handler to GlobalExceptionHandler
that returns HTTP 400 with field-level error details.
Add 'spring-boot-starter-validation' dependency if not already present.
```

### 2.2 Add Circuit Breakers (RE-1)

**Gap**: No circuit breakers. Core Banking failure cascades to all upstream services.

```
Devin Prompt:
Add Resilience4j circuit breakers to all Feign-calling services:

1. Add dependencies to build.gradle:
   - 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure default circuit breaker settings in application.yml:
   resilience4j.circuitbreaker.instances.coreBanking:
     slidingWindowSize: 10
     failureRateThreshold: 50
     waitDurationInOpenState: 30s
     permittedNumberOfCallsInHalfOpenState: 3

3. Add @CircuitBreaker annotations to Feign client methods or configure
   via Spring Cloud CircuitBreaker with Feign integration.

4. Create fallback classes for each Feign client that return meaningful
   error responses (e.g., "Core Banking service is currently unavailable").

5. Add unit tests verifying fallback behavior when the circuit is open.
```

### 2.3 Add Idempotency Keys for Financial Operations (RE-6)

**Gap**: Retried requests can cause double-debits.

```
Devin Prompt:
Implement idempotency for fund transfer and utility payment endpoints:

1. Add an 'Idempotency-Key' header requirement to POST endpoints in
   FundTransferController and UtilityPaymentController.

2. Create an idempotency_key table in each service's database:
   CREATE TABLE idempotency_key (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     key_value VARCHAR(255) UNIQUE NOT NULL,
     response TEXT,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

3. Before processing, check if the key already exists. If so, return
   the stored response. If not, process the request and store the result.

4. Add a filter or interceptor that extracts and validates the header.

5. Return HTTP 409 Conflict if a duplicate key is detected with a
   different request body.

6. Add a scheduled job to clean up keys older than 24 hours.
```

### 2.4 Fix Concurrent Balance Update Race Condition (RE-7)

**Gap**: No locking on balance reads and updates allows concurrent transfers to corrupt balances.

```
Devin Prompt:
Fix the race condition in core-banking-service TransactionService:

1. Add @Version field to BankAccountEntity for optimistic locking:
   @Version
   private Long version;

2. Add a corresponding 'version' BIGINT column to banking_core_account
   via a new Flyway migration.

3. Alternatively (preferred for financial systems), use pessimistic locking:
   - Add a custom query to BankAccountRepository:
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT b FROM BankAccountEntity b WHERE b.number = :number")
     Optional<BankAccountEntity> findByNumberForUpdate(@Param("number") String number);
   - Use this method in TransactionService instead of findByNumber()

4. Wrap the entire fund transfer operation in a single @Transactional method
   (already present, but verify it covers all reads and writes).

5. Write a concurrent test using multiple threads to verify no balance
   corruption occurs.
```

### 2.5 Externalize Secrets from Version Control (SE-2)

**Gap**: Database passwords, Keycloak credentials, and test user credentials are hardcoded.

```
Devin Prompt:
Remove all hardcoded credentials from the codebase:

1. In docker-compose.yml, replace hardcoded passwords with environment
   variable references:
   MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
   KC_DB_PASSWORD: ${KC_DB_PASSWORD}
   KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}

2. Create a docker-compose/.env.example file with placeholder values
   and add docker-compose/.env to .gitignore.

3. In mysql/privileges.sql, parameterize the password or document that
   it must be changed before deployment.

4. Remove test credentials from README.md or move them to a separate
   document that is gitignored.

5. Add a SECURITY.md documenting the secrets management approach.
```

### 2.6 Add Role-Based Access Control (SE-9)

**Gap**: Any authenticated user can perform any operation.

```
Devin Prompt:
Implement RBAC at the API Gateway level using Keycloak roles:

1. Define roles in Keycloak realm: BANK_ADMIN, BANK_USER

2. Update SecurityConfiguration in the API Gateway to enforce roles:
   - /user/api/v1/bank-users/update/** → requires BANK_ADMIN role
   - /fund-transfer/** → requires BANK_USER role
   - /utility-payment/** → requires BANK_USER role
   - /banking-core/** → requires BANK_ADMIN role (internal APIs)

3. Extract roles from the JWT token claims (realm_access.roles)

4. Create a custom JwtAuthenticationConverter that maps Keycloak roles
   to Spring Security GrantedAuthority objects.

5. Update the Keycloak realm export JSON to include the new roles and
   assign BANK_ADMIN to the test user.

6. Write integration tests verifying that unauthorized role access
   returns HTTP 403.
```

### 2.7 Add Rate Limiting at the Gateway (SE-4)

**Gap**: No rate limiting on any endpoint.

```
Devin Prompt:
Add rate limiting to the API Gateway using Spring Cloud Gateway's
built-in RequestRateLimiter filter:

1. Add 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
   dependency to the gateway's build.gradle.

2. Add a Redis container to docker-compose.yml.

3. Configure rate limiting in the gateway routes:
   - Fund transfer: 10 requests per minute per user
   - Utility payment: 10 requests per minute per user
   - User registration: 5 requests per minute per IP
   - Read endpoints: 60 requests per minute per user

4. Configure the KeyResolver to extract the user ID from the JWT token.

5. Return HTTP 429 Too Many Requests with a Retry-After header when
   the rate limit is exceeded.
```

### 2.8 Create a Shared Common Library (CO-1)

**Gap**: Exception classes, filters, audit configs, and mappers are copy-pasted across services.

```
Devin Prompt:
Create a shared common library module:

1. Create a new directory 'internet-banking-common' at the project root.

2. Initialize it as a Gradle library project (no Spring Boot plugin,
   just java-library).

3. Move these shared classes into the common module:
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler
   - BaseMapper
   - AuditAware, AuditConfig, AuditorAwareConfig
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - TransactionStatus enum

4. Publish the common module to the local Maven repository or use
   Gradle composite builds.

5. Update each service's build.gradle to depend on the common module:
   implementation project(':internet-banking-common')

6. Create a root-level settings.gradle that includes all modules.

7. Remove the duplicated classes from each service.
```

### 2.9 Add Feign Error Handling (EH-3)

**Gap**: Feign client errors propagate unhandled in most services.

```
Devin Prompt:
Add consistent Feign error handling to all Feign-calling services:

1. Create a shared FeignErrorDecoder (in the common library from CO-1,
   or copy to each service if the common library isn't created yet):
   - 404 → throw EntityNotFoundException
   - 400 → parse ErrorResponse from body, throw SimpleBankingGlobalException
   - 422 → throw appropriate business exception
   - 5xx → throw a ServiceUnavailableException (new exception)

2. Register the error decoder in CustomFeignClientConfiguration.

3. Add @ExceptionHandler for ServiceUnavailableException in
   GlobalExceptionHandler returning HTTP 503.

4. User Service already has CustomFeignErrorDecoder — use it as the
   reference implementation and replicate to Fund Transfer and
   Utility Payment services.
```

### 2.10 Add Unit Tests for Untested Services (TE-1)

**Gap**: 5 of 6 services have zero business logic tests.

```
Devin Prompt:
Add comprehensive unit tests for the three business services:

1. internet-banking-user-service:
   - UserServiceTest: test createUser() happy path, duplicate email,
     invalid email, user not found in core banking, Keycloak failure
   - KeycloakUserServiceTest: test CRUD operations with mocked KeycloakManager
   - UserControllerTest: test all endpoints with @WebMvcTest

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer() happy path, Core Banking
     failure, readAllTransfers()
   - FundTransferControllerTest: test POST and GET with @WebMvcTest

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment() happy path, Core Banking
     failure, readPayments()
   - UtilityPaymentControllerTest: test POST and GET with @WebMvcTest

Use Mockito for service dependencies. Use @WebMvcTest + MockMvc for
controller tests. Target >80% line coverage for business logic.
```

---

## Phase 3: Polish (Medium & Low Severity, Quality of Life)

These items improve developer experience and operational maturity.

### 3.1 Add Structured JSON Logging (OB-2)

```
Devin Prompt:
Configure structured JSON logging for all services:
1. Add 'net.logstash.logback:logstash-logback-encoder:7.4' to each
   service's build.gradle.
2. Create a logback-spring.xml in each service's src/main/resources/:
   - Console appender with LogstashEncoder for Docker/production
   - Pattern appender for local development (activated by 'dev' profile)
3. Include traceId and spanId from Micrometer in the MDC fields.
4. Ensure all log output is valid JSON when not in dev profile.
```

### 3.2 Add Trace/Span IDs to Log Output (OB-6)

```
Devin Prompt:
Ensure Micrometer traceId and spanId appear in all log output:
1. Add to each service's application.yml:
   logging.pattern.level: "%5p [${spring.application.name},%X{traceId},%X{spanId}]"
2. Verify that Feign calls propagate trace context headers.
3. Test by making a request through the Gateway and checking that all
   service logs share the same traceId.
```

### 3.3 Add Custom Health Indicators (OB-3)

```
Devin Prompt:
Add custom health indicators to services:
1. Core Banking: DatabaseHealthIndicator (verify MySQL connection)
2. User Service: KeycloakHealthIndicator (verify Keycloak reachability)
3. Fund Transfer & Utility Payment: CoreBankingHealthIndicator
   (verify Core Banking actuator/health is UP)
4. Register all indicators as Spring beans.
5. Expose health details: management.endpoint.health.show-details: always
```

### 3.4 Add Gateway Access Logging (OB-7)

```
Devin Prompt:
Add access logging to the API Gateway:
1. Create a GlobalFilter that logs: method, path, status code,
   response time, and user ID (from X-Auth-Id header).
2. Use structured logging format (JSON).
3. Do NOT log request/response bodies (they may contain sensitive data).
4. Add the filter with Ordered.LOWEST_PRECEDENCE to capture the final
   response status.
```

### 3.5 Standardize Package Structure (CO-2)

```
Devin Prompt:
Standardize the package structure across all services to follow this pattern:
  com.javatodev.finance/
    configuration/     (Spring configs, security, Feign)
    controller/        (REST controllers)
    exception/         (exception classes and handlers)
    model/
      dto/             (DTOs, request/response objects)
      entity/          (JPA entities)
      mapper/          (entity-DTO mappers)
    repository/        (Spring Data repositories)
    service/           (business logic)
      rest/            (Feign clients)

Move files that don't match this pattern to the correct packages.
Update all import statements accordingly.
```

### 3.6 Add Graceful Shutdown (RE-10)

```
Devin Prompt:
Configure graceful shutdown for all services:
1. Add to each service's application.yml:
   server.shutdown: graceful
   spring.lifecycle.timeout-per-shutdown-phase: 30s
2. For Docker deployments, ensure SIGTERM is properly forwarded
   (already handled by the java -jar entrypoint).
3. Test by sending a request during shutdown and verifying it completes.
```

### 3.7 Add CORS Configuration (SE-6)

```
Devin Prompt:
Add CORS configuration to the API Gateway:
1. Add a CorsWebFilter bean in SecurityConfiguration:
   - Allowed origins: configurable via application.yml property
   - Allowed methods: GET, POST, PATCH, PUT, DELETE, OPTIONS
   - Allowed headers: Authorization, Content-Type, X-Requested-With
   - Max age: 3600 seconds
2. Ensure preflight OPTIONS requests are not blocked by OAuth2.
3. Add cors.allowed-origins property to the external config with
   sensible defaults for development (http://localhost:3000).
```

### 3.8 Add Dependency Vulnerability Scanning (SE-7)

```
Devin Prompt:
Add automated dependency vulnerability scanning:
1. Add the OWASP Dependency Check Gradle plugin to each service:
   id 'org.owasp.dependencycheck' version '9.0.10'
2. Configure it to fail the build on CVSS score >= 7.
3. Add a GitHub Actions workflow that runs dependency-check on
   every PR and weekly on main.
4. Alternatively, add a Dependabot configuration (.github/dependabot.yml)
   for automated dependency update PRs.
```

### 3.9 Add Integration Tests (TE-2)

```
Devin Prompt:
Add integration tests for each service:
1. Use @SpringBootTest with TestRestTemplate for full stack tests.
2. Use Testcontainers for MySQL integration:
   - Add 'org.testcontainers:mysql' and 'org.testcontainers:junit-jupiter'
     to testImplementation dependencies.
3. For Core Banking: test the full flow of creating a fund transfer
   via the REST API and verifying the database state.
4. For User Service: mock the Keycloak server using WireMock and test
   the registration flow end-to-end.
5. Use @DirtiesContext to isolate test state between tests.
```

### 3.10 Add Contract Tests (TE-3)

```
Devin Prompt:
Add Spring Cloud Contract tests between services:
1. Add 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   to core-banking-service (the provider).
2. Define contracts in core-banking-service/src/test/resources/contracts/
   for each endpoint consumed by other services.
3. Generate stubs JAR from core-banking-service contracts.
4. In consumer services (fund-transfer, utility-payment, user-service),
   add 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'
   and write tests that use the stub JAR to verify Feign client compatibility.
5. Integrate contract verification into the CI pipeline.
```

### 3.11 Add EditorConfig and Code Formatting (CO-6)

```
Devin Prompt:
Add consistent code formatting to the project:
1. Create a .editorconfig at the project root:
   root = true
   [*]
   indent_style = space
   indent_size = 4
   charset = utf-8
   end_of_line = lf
   trim_trailing_whitespace = true
   insert_final_newline = true
2. Add the Spotless Gradle plugin to enforce formatting:
   id 'com.diffplug.spotless' version '6.25.0'
3. Configure Spotless with Google Java Format or Palantir Java Format.
4. Run spotlessApply to fix existing formatting issues.
5. Add a CI step that runs spotlessCheck on PRs.
```

### 3.12 Add Test Coverage Reporting (TE-6)

```
Devin Prompt:
Add JaCoCo test coverage reporting to all services:
1. Add the JaCoCo plugin to each build.gradle:
   id 'jacoco'
2. Configure the jacocoTestReport task to generate HTML and XML reports.
3. Set minimum coverage thresholds:
   jacocoTestCoverageVerification {
     violationRules {
       rule { limit { minimum = 0.60 } }
     }
   }
4. Add a CI step that publishes coverage reports and fails if below threshold.
5. Add a coverage badge to README.md.
```

---

## Phase Summary

| Phase | Items | Focus Area | Estimated Effort |
|-------|-------|------------|-----------------|
| **Phase 1** | 10 items | Bugs, security basics, error handling, observability foundations | 1-2 sprints |
| **Phase 2** | 10 items | Financial integrity, resilience, access control, shared code, testing | 3-4 sprints |
| **Phase 3** | 12 items | Developer experience, operational maturity, CI/CD, polish | 2-3 sprints |

## Recommended Execution Order Within Each Phase

### Phase 1 Priority Order
1. RE-8 (fix active bug)
2. RE-3 (add timeouts — prevents cascading failures)
3. EH-1 + EH-2 (stop leaking errors, fix status codes)
4. OB-1 (stop logging sensitive data)
5. SE-1 (input validation — prevents malformed data)
6. AP-7 (fix Swagger dependency — unblocks API documentation)
7. AP-1 (type-safe ResponseEntity — improves OpenAPI docs)
8. RE-2 (add retry — quick resilience win)
9. OB-4 (Prometheus metrics)
10. RE-9 (Docker health checks)

### Phase 2 Priority Order
1. RE-7 (fix race condition — data integrity)
2. RE-6 (idempotency keys — prevent double-processing)
3. RE-1 (circuit breakers — prevent cascade failures)
4. SE-2 (externalize secrets)
5. SE-9 (RBAC)
6. EH-3 (Feign error handling)
7. SE-4 (rate limiting)
8. CO-1 (shared library)
9. TE-1 (unit tests)
10. RE-4 (fallback behavior — pairs with circuit breakers)

### Phase 3 Priority Order
1. OB-2 + OB-6 (structured logging with trace IDs)
2. TE-2 (integration tests)
3. SE-7 (dependency scanning)
4. OB-3 (custom health indicators)
5. CO-2 (standardize packages)
6. OB-7 (gateway access logging)
7. RE-10 (graceful shutdown)
8. SE-6 (CORS)
9. TE-3 (contract tests)
10. CO-6 (formatting)
11. TE-6 (coverage reporting)
12. AP-3 + AP-4 (response envelope, filtering)
