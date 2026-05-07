# Remediation Roadmap

This roadmap organizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 — Quick Wins:** Critical/High severity items with Small effort. Immediate safety and correctness fixes.
- **Phase 2 — Important:** Critical/High items with Medium effort, plus Medium-severity items. Structural improvements.
- **Phase 3 — Polish:** Low-severity items and large-effort improvements. Long-term quality investment.

Each item includes a sample Devin prompt you can use to implement the fix.

---

## Phase 1: Quick Wins (1-2 weeks)

These are high-impact, low-effort fixes that address critical bugs and security issues.

### 1.1 Fix Balance Calculation Bug (GAP-RE-06)

**Priority:** P0 — Data integrity bug actively corrupting balances

Fix the double-subtraction bug in `TransactionService.java` where `availableBalance` is computed from the already-reduced `actualBalance`, causing balances to drift incorrectly after every transaction.

**Devin Prompt:**
```
In core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java,
fix the balance calculation bug. In both fundTransfer (lines 90-91) and utilPayment (lines 63-64),
availableBalance is set by subtracting amount from the already-reduced actualBalance, causing a
double subtraction. Change the availableBalance assignment to use the same new value as
actualBalance. Also fix the credit side (lines 99-100) where the same pattern applies with addition.
Add unit tests to verify that after a fund transfer, both actualBalance and availableBalance
are correctly computed. Open a PR with the fix.
```

---

### 1.2 Fix Generic Exception Handler (GAP-EH-01)

**Priority:** P0 — Leaks stack traces to clients, returns wrong status codes

Update all `GlobalExceptionHandler` classes to return 500 for unexpected errors with a structured `ErrorResponse` body, never leaking exception details.

**Devin Prompt:**
```
In all 4 services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service),
update the GlobalExceptionHandler catch-all @ExceptionHandler({Exception.class}) method to:
1. Return HTTP 500 instead of 400
2. Use the structured ErrorResponse object with code="INTERNAL_ERROR" and a generic message
3. Log the full exception server-side at ERROR level
4. Never include exception details in the response body
Open a PR with the changes.
```

---

### 1.3 Add HTTP Status Code Differentiation (GAP-EH-02)

**Priority:** P1 — Incorrect HTTP semantics

Map each exception type to its correct HTTP status code.

**Devin Prompt:**
```
In all GlobalExceptionHandler classes across the 4 business services, add specific
@ExceptionHandler methods for:
- EntityNotFoundException -> 404 Not Found
- InsufficientFundsException -> 422 Unprocessable Entity
- UserAlreadyRegisteredException -> 409 Conflict
- InvalidEmailException / InvalidBankingUserException -> 400 Bad Request
Keep the SimpleBankingGlobalException handler as a fallback returning 400.
All responses should use the structured ErrorResponse(code, message) format.
Open a PR with the changes.
```

---

### 1.4 Remove Sensitive Data from Logs (GAP-OB-02)

**Priority:** P1 — Passwords and financial data in logs

Prevent sensitive fields from being logged via `toString()`.

**Devin Prompt:**
```
In internet-banking-user-service, add @ToString.Exclude on the password field in
model/dto/User.java. In all controllers across all services, review log statements that call
toString() on request objects. Replace toString() with explicit safe field logging
(e.g., log account numbers partially masked, never log amounts in production).
At minimum, ensure the User DTO password is never logged. Open a PR.
```

---

### 1.5 Externalize Docker Compose Credentials (GAP-SE-01)

**Priority:** P1 — Hardcoded secrets in version control

Move credentials to a `.env` file that is gitignored.

**Devin Prompt:**
```
In the docker-compose directory:
1. Create a .env.example file with placeholder values for MYSQL_ROOT_PASSWORD,
   KEYCLOAK_ADMIN_PASSWORD, KC_DB_PASSWORD, and POSTGRES_PASSWORD
2. Update docker-compose.yml and docker-compose-support-apps.yml to reference
   ${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD}, etc.
3. Add .env to .gitignore
4. Create a .env file with the current values for local development
5. Update README.md to document the .env setup
Open a PR.
```

---

### 1.6 Add Feign Client Timeouts (GAP-RE-03)

**Priority:** P1 — Unbounded timeouts can hang threads

Configure explicit connect and read timeouts for all Feign clients.

**Devin Prompt:**
```
In internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and
internet-banking-user-service, update each CustomFeignClientConfiguration to add a
Request.Options bean with connectTimeout=5000ms and readTimeout=30000ms. If the service
uses application.yml for Feign config, add the timeout properties there instead.
Open a PR.
```

---

### 1.7 Add Feign Retry for Read Operations (GAP-RE-02)

**Priority:** P1 — Transient failures cause immediate errors

Add retry logic for idempotent (GET) Feign calls.

**Devin Prompt:**
```
Add spring-retry dependency to internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service.
Configure a Feign Retryer bean in each CustomFeignClientConfiguration with
maxAttempts=3, period=1000ms, maxPeriod=3000ms. Ensure retries only apply to
GET/read operations — POST operations (fund transfers, payments) should NOT be retried
to avoid duplicate transactions. Open a PR.
```

---

### 1.8 Fix TransactionEntity CascadeType (GAP-RE-07)

**Priority:** P2 — Risk of accidental account data modification

Remove dangerous cascade on the transaction-to-account relationship.

**Devin Prompt:**
```
In core-banking-service TransactionEntity.java, change the @OneToOne annotation on the
'account' field from cascade = CascadeType.ALL to cascade = {} (no cascade).
Account entities should be managed independently. Run existing tests to verify nothing
breaks. Open a PR.
```

---

### 1.9 Fix Keycloak Singleton Thread Safety (GAP-SE-05)

**Priority:** P2 — Race condition on Keycloak client creation

Make the Keycloak instance creation thread-safe.

**Devin Prompt:**
```
In internet-banking-user-service KeycloakProperties.java, refactor the getInstance()
method to be thread-safe. The simplest approach: make the keycloakInstance field volatile
and use double-checked locking in getInstance(). Alternatively, convert the Keycloak
instance creation to a @Bean method in a @Configuration class so Spring manages the
singleton lifecycle. Open a PR.
```

---

### 1.10 Fix OpenAPI Starter Mismatch (GAP-AD-06)

**Priority:** P2 — Swagger UI may not work correctly

Switch servlet-based services to the correct OpenAPI starter.

**Devin Prompt:**
```
In the build.gradle files for core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service,
replace the dependency 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with
'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'. These services use Spring MVC
(servlet), not WebFlux. Build each service to verify compilation succeeds. Open a PR.
```

---

## Phase 2: Important (3-6 weeks)

Structural improvements that significantly improve maintainability, security, and resilience.

### 2.1 Add Input Validation (GAP-EH-03)

**Priority:** P0 — No validation on financial transaction inputs

Add Jakarta Bean Validation to all request DTOs and controllers.

**Devin Prompt:**
```
Add jakarta.validation constraints to all request DTOs across all services:

core-banking-service:
- FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

internet-banking-fund-transfer-service:
- FundTransferRequest: same constraints as above

internet-banking-utility-payment-service:
- UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber, @NotBlank account

internet-banking-user-service:
- User: @NotBlank email, @Email email, @NotBlank identification, @NotBlank password

Add @Valid annotation to all @RequestBody parameters in all controllers.
Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns
400 with field-level error details. Open a PR.
```

---

### 2.2 Add Circuit Breakers (GAP-RE-01)

**Priority:** P0 — No protection against cascading failures

Implement Resilience4j circuit breakers on all inter-service Feign calls.

**Devin Prompt:**
```
Add spring-cloud-starter-circuitbreaker-resilience4j to internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service.

Configure circuit breakers in each service's application.yml:
- slidingWindowSize: 10
- failureRateThreshold: 50
- waitDurationInOpenState: 30s
- permittedNumberOfCallsInHalfOpenState: 3

Apply @CircuitBreaker annotations to each Feign client method or configure via
Resilience4j Feign integration. Add fallback methods that return meaningful error
responses (e.g., "Core banking service is temporarily unavailable").
Open a PR.
```

---

### 2.3 Add Feign Error Decoders (GAP-EH-05)

**Priority:** P1 — Upstream errors propagate as cryptic 400s

Implement proper error decoding for all Feign clients.

**Devin Prompt:**
```
Create a shared FeignErrorDecoder class (or add it to each service) that implements
feign.codec.ErrorDecoder. The decoder should:
1. Parse the upstream ErrorResponse JSON from the response body
2. Map 404 responses to EntityNotFoundException
3. Map 422 responses to domain-specific exceptions (InsufficientFundsException, etc.)
4. Map 5xx responses to a ServiceUnavailableException
5. Log the full error details server-side

Register this decoder in CustomFeignClientConfiguration for internet-banking-fund-transfer-service,
internet-banking-utility-payment-service, and internet-banking-user-service.
Open a PR.
```

---

### 2.4 Add Structured Logging (GAP-OB-01)

**Priority:** P1 — Cannot aggregate or search logs

Configure JSON structured logging with trace correlation.

**Devin Prompt:**
```
Add logback-spring.xml to all 6 services with:
1. JSON format using net.logstash.logback:logstash-logback-encoder (add dependency to each build.gradle)
2. Include traceId and spanId from Micrometer in MDC
3. Use a console appender with JSON format for Docker/production
4. Keep a plain-text appender for local development (activated via spring profile)
5. Set appropriate log levels (INFO for application, WARN for framework)
Open a PR.
```

---

### 2.5 Add Rate Limiting to API Gateway (GAP-SE-04)

**Priority:** P1 — Financial APIs vulnerable to abuse

Implement rate limiting on the API Gateway.

**Devin Prompt:**
```
Add rate limiting to internet-banking-api-gateway using Spring Cloud Gateway's built-in
RequestRateLimiter filter. Configure:
1. Add spring-boot-starter-data-redis-reactive dependency
2. Add a Redis container to docker-compose.yml
3. Configure RequestRateLimiter filter in gateway routes with:
   - replenishRate: 10 requests/second
   - burstCapacity: 20
   - Key resolver based on authenticated user principal
4. Add stricter limits for financial endpoints (fund-transfer, utility-payment):
   - replenishRate: 5/second, burstCapacity: 10
Open a PR.
```

---

### 2.6 Add Downstream Service Security (GAP-SE-07)

**Priority:** P1 — Services accept unauthenticated requests on direct ports

Add JWT validation or shared-secret authentication to downstream services.

**Devin Prompt:**
```
Add a lightweight security layer to core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

Option A (recommended for this architecture): Add a servlet filter that validates a shared
internal API key header (X-Internal-Api-Key) for service-to-service calls. Configure the
API key via Spring Cloud Config. The API Gateway should inject this header on proxied requests.

Option B: Add spring-boot-starter-oauth2-resource-server to each service and validate
JWT tokens directly.

Implement Option A for simplicity. Ensure actuator endpoints remain accessible for
health checks. Open a PR.
```

---

### 2.7 Extract Shared Library (GAP-CO-02)

**Priority:** P2 — Reduces maintenance burden for all future changes

Create a `banking-common` module with shared code.

**Devin Prompt:**
```
Create a new Gradle module called banking-common at the project root:
1. Add a root settings.gradle that includes all service modules and banking-common
2. Move these shared classes into banking-common:
   - AuditAware (base DTO)
   - BaseMapper interface
   - ErrorResponse
   - SimpleBankingGlobalException and subclasses
   - GlobalExceptionHandler
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
3. Update each service's build.gradle to depend on banking-common
4. Remove the duplicated classes from each service
5. Verify all services compile successfully
Open a PR.
```

---

### 2.8 Add Custom Health Checks (GAP-OB-03)

**Priority:** P2 — Cannot monitor dependency health

Add health indicators for critical dependencies.

**Devin Prompt:**
```
Add custom HealthIndicator beans to:
1. internet-banking-user-service: KeycloakHealthIndicator that pings Keycloak server URL
2. internet-banking-fund-transfer-service: CoreBankingHealthIndicator that calls
   core-banking-service actuator health endpoint via Feign
3. internet-banking-utility-payment-service: same CoreBankingHealthIndicator
4. All services: verify that the default DataSource health indicator is active

Register each as a @Component. Ensure they appear in /actuator/health output.
Open a PR.
```

---

### 2.9 Add Dependency Vulnerability Scanning (GAP-SE-06)

**Priority:** P2 — Unknown vulnerabilities in dependencies

Configure OWASP Dependency Check in the build.

**Devin Prompt:**
```
Add the OWASP Dependency Check Gradle plugin to all services:
1. Add plugin 'org.owasp.dependencycheck' version '9.0.9' to each build.gradle
2. Configure it to fail the build on CVSS score >= 7 (HIGH)
3. Add a root-level Gradle task that runs dependency check across all services
4. Run the check and document any current vulnerabilities found
5. Add suppressions for any known false positives
Open a PR.
```

---

### 2.10 Fix Pagination Responses (GAP-AD-03)

**Priority:** P2 — Clients cannot paginate effectively

Return `Page<T>` instead of `List<T>` from paginated endpoints.

**Devin Prompt:**
```
Update all paginated endpoints across all services to return Page<T> or a custom
PageResponse wrapper instead of List<T>:

1. core-banking-service UserController.readUsers() -> return Page<User>
2. internet-banking-user-service UserController.readUsers() -> return Page<User>
3. internet-banking-fund-transfer-service FundTransferController.readFundTransfers() -> return Page<FundTransfer>
4. internet-banking-utility-payment-service UtilityPaymentController.readPayments() -> return Page<UtilityPayment>

Update the service layer methods to return Page<T> instead of List<T>.
Update ResponseEntity to use the parameterized type: ResponseEntity<Page<T>>.
Open a PR.
```

---

### 2.11 Add Bulkhead Isolation (GAP-RE-05)

**Priority:** P2 — Thread pool exhaustion risk

Configure Resilience4j bulkheads for inter-service calls.

**Devin Prompt:**
```
In internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and
internet-banking-user-service, configure Resilience4j bulkhead isolation:

1. Add resilience4j-bulkhead dependency (included with spring-cloud-starter-circuitbreaker-resilience4j)
2. Configure thread-pool bulkheads in application.yml:
   - maxConcurrentCalls: 25
   - maxWaitDuration: 500ms
3. Apply @Bulkhead annotations alongside @CircuitBreaker on Feign client calls
Open a PR.
```

---

### 2.12 Add Fallback Behavior (GAP-RE-04)

**Priority:** P2 — No graceful degradation

Implement Feign fallback factories.

**Devin Prompt:**
```
For each Feign client interface, create a FallbackFactory implementation:

1. internet-banking-fund-transfer-service BankingCoreFeignClient:
   - fundTransfer fallback: throw a ServiceUnavailableException with a user-friendly message
   - readAccount fallback: throw ServiceUnavailableException

2. internet-banking-utility-payment-service BankingCoreRestClient:
   - utilityPayment fallback: throw ServiceUnavailableException
   - readAccount fallback: throw ServiceUnavailableException

3. internet-banking-user-service BankingCoreRestClient:
   - readUser fallback: throw ServiceUnavailableException

Register each fallback factory in the @FeignClient annotation.
Add a ServiceUnavailableException handler in GlobalExceptionHandler returning 503.
Open a PR.
```

---

## Phase 3: Polish (2-3 months)

Long-term quality investments for production readiness.

### 3.1 Add Comprehensive Unit Tests (GAP-TE-01)

**Priority:** P1 — Low test confidence across most services

Add unit tests for all service and controller layers.

**Devin Prompt:**
```
Add comprehensive unit tests for all services that currently lack them:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid NIC, email mismatch),
     readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: test createUser, readUser, readUserByEmail, updateUser
   - UserControllerTest: MockMvc tests for all 4 endpoints

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (success, core-banking failure),
     readAllTransfers
   - FundTransferControllerTest: MockMvc tests for POST and GET

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (success, core-banking failure),
     readPayments
   - UtilityPaymentControllerTest: MockMvc tests for POST and GET

Use Mockito for mocking dependencies. Aim for >80% coverage on service classes.
Open a PR.
```

---

### 3.2 Add Integration Tests (GAP-TE-02)

**Priority:** P1 — No end-to-end verification

Add integration tests using Testcontainers.

**Devin Prompt:**
```
Add integration tests for core-banking-service using Testcontainers:
1. Add org.testcontainers:mysql dependency to core-banking-service build.gradle
2. Create an integration test profile with Testcontainers MySQL configuration
3. Write integration tests for:
   - AccountController: GET bank account, GET utility account
   - TransactionController: POST fund transfer (success + insufficient funds)
   - UserController: GET users
4. Verify Flyway migrations run successfully against real MySQL
5. Add a CI-friendly test configuration

Repeat the pattern for internet-banking-user-service (with MySQL Testcontainer).
Open a PR.
```

---

### 3.3 Add Contract Tests (GAP-TE-03)

**Priority:** P2 — No verification of inter-service API compatibility

Implement consumer-driven contract tests.

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. Producer side (core-banking-service):
   - Add spring-cloud-starter-contract-verifier dependency
   - Write contract DSLs for:
     - GET /api/v1/account/bank-account/{number} -> BankAccount response
     - POST /api/v1/transaction/fund-transfer -> FundTransferResponse
     - POST /api/v1/transaction/util-payment -> UtilityPaymentResponse
     - GET /api/v1/user/{identification} -> User response
   - Generate and publish stubs

2. Consumer side (fund-transfer-service, utility-payment-service, user-service):
   - Add spring-cloud-contract-stub-runner dependency
   - Write consumer contract tests verifying Feign clients against stubs

Open a PR.
```

---

### 3.4 Fix Context Load Tests (GAP-TE-04)

**Priority:** P2 — Tests fail in isolation

Make all `@SpringBootTest` tests runnable without external infrastructure.

**Devin Prompt:**
```
Update test configuration for all services to work without Eureka or Config Server:

1. In each service's src/test/resources/application.yml, add:
   - eureka.client.enabled: false
   - spring.cloud.config.enabled: false
   - spring.cloud.discovery.enabled: false

2. For services with Feign clients, add @MockBean for the Feign client interfaces
   in the application context test.

3. Verify all @SpringBootTest tests pass with: ./gradlew test (in each service directory)
Open a PR.
```

---

### 3.5 Set Up Gradle Multi-Module Build (GAP-CO-01)

**Priority:** P2 — Centralize dependency management

Create a proper multi-module Gradle project.

**Devin Prompt:**
```
Convert the project to a Gradle multi-module build:

1. Create a root build.gradle with:
   - Common repository declarations
   - Shared dependency versions via ext or version catalog (libs.versions.toml)
   - Common test configuration

2. Create a root settings.gradle including all 7 modules:
   - banking-common (new shared library)
   - core-banking-service
   - internet-banking-api-gateway
   - internet-banking-config-server
   - internet-banking-fund-transfer-service
   - internet-banking-service-registry
   - internet-banking-user-service
   - internet-banking-utility-payment-service

3. Simplify each service's build.gradle to inherit from the root
4. Verify all services build from the root: ./gradlew build
Open a PR.
```

---

### 3.6 Add Metrics and Monitoring (GAP-OB-04)

**Priority:** P2 — No operational visibility

Set up Prometheus metrics and Grafana dashboards.

**Devin Prompt:**
```
Add monitoring infrastructure:

1. Add micrometer-registry-prometheus dependency to all services' build.gradle
2. Enable the Prometheus actuator endpoint in each service's configuration
3. Add a Prometheus container to docker-compose.yml with scrape configs for all services
4. Add a Grafana container to docker-compose.yml with:
   - Prometheus as a preconfigured datasource
   - A dashboard JSON file for JVM metrics (heap, GC, threads)
   - A dashboard JSON file for HTTP request metrics (rate, latency, errors)
   - A dashboard JSON file for Feign client metrics
5. Document access URLs in README.md
Open a PR.
```

---

### 3.7 Standardize Package Structure (GAP-CO-03)

**Priority:** P3 — Consistency improvement

Normalize package layout across all services.

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:
- com.javatodev.finance.controller/
- com.javatodev.finance.service/
- com.javatodev.finance.service.client/ (for Feign clients)
- com.javatodev.finance.repository/
- com.javatodev.finance.model.entity/
- com.javatodev.finance.model.dto/
- com.javatodev.finance.model.mapper/
- com.javatodev.finance.config/
- com.javatodev.finance.exception/

Move classes to match this structure in all services. Update all imports.
Verify compilation and tests pass. Open a PR.
```

---

### 3.8 Register Mappers as Spring Beans (GAP-CO-04)

**Priority:** P3 — Minor testability improvement

Convert manually instantiated mappers to Spring beans.

**Devin Prompt:**
```
In all services, annotate mapper classes (UserMapper, BankAccountMapper,
FundTransferMapper, UtilityPaymentMapper, UtilityAccountMapper) with @Component.
Remove the inline field initialization (e.g., private UserMapper mapper = new UserMapper())
and replace with constructor injection via @RequiredArgsConstructor.
Verify all services compile and tests pass. Open a PR.
```

---

### 3.9 Add ResponseEntity Type Parameters (GAP-AD-01)

**Priority:** P3 — Improves OpenAPI generation

Add generic type parameters to all controller return types.

**Devin Prompt:**
```
In all controllers across all services, add type parameters to ResponseEntity return types:
- ResponseEntity -> ResponseEntity<BankAccount>
- ResponseEntity -> ResponseEntity<List<User>>
- ResponseEntity -> ResponseEntity<FundTransferResponse>
etc.

This ensures OpenAPI/Swagger correctly documents response schemas.
Verify all services compile. Open a PR.
```

---

### 3.10 Document Security Design Decisions (GAP-SE-03)

**Priority:** P3 — Audit trail for security choices

Add security documentation.

**Devin Prompt:**
```
Create a docs/SECURITY.md file documenting:
1. Authentication architecture (Keycloak -> API Gateway -> JWT validation)
2. Why CSRF is disabled (stateless JWT API, no session cookies)
3. Network isolation strategy (Docker bridge network, no direct port exposure in production)
4. Service-to-service authentication approach
5. Secrets management strategy
6. Keycloak realm configuration overview
Open a PR.
```

---

### 3.11 Add Custom Tracing Spans (GAP-OB-05)

**Priority:** P3 — Improve debugging for business operations

Add explicit tracing to key operations.

**Devin Prompt:**
```
Add custom tracing spans for important business operations:
1. In core-banking-service TransactionService: add @NewSpan("fundTransfer.execute")
   and @NewSpan("utilPayment.execute") annotations
2. In internet-banking-user-service KeycloakUserService: add @NewSpan("keycloak.createUser"),
   @NewSpan("keycloak.readUser"), @NewSpan("keycloak.updateUser")
3. Add @SpanTag annotations for key parameters (account numbers, transaction IDs)
Verify tracing still works with Zipkin. Open a PR.
```

---

### 3.12 Add API Documentation Enhancements (GAP-AD-02, GAP-AD-04, GAP-AD-05)

**Priority:** P3 — API design polish

Improve API conventions and documentation.

**Devin Prompt:**
```
Improve API design across all services:
1. Document the API versioning strategy in docs/API_CONVENTIONS.md
2. Add @Parameter annotations to all paginated endpoints documenting supported
   sort fields and page size limits
3. Rename PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}
   (remove verb from URL). Update gateway routing if needed.
4. Add @ApiResponse annotations documenting error responses (400, 404, 422, 500)
   to all controller methods
Open a PR.
```

---

## Summary Timeline

| Phase | Duration | Items | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 1-2 weeks | 10 items | Fix critical bugs, secure credentials, add timeouts, fix error handling |
| **Phase 2** | 3-6 weeks | 12 items | Input validation, circuit breakers, structured logging, shared library, rate limiting |
| **Phase 3** | 2-3 months | 12 items | Comprehensive tests, monitoring, contract tests, code organization polish |

## Gap Coverage Matrix

| Gap ID | Phase | Roadmap Item |
|---|---|---|
| GAP-RE-06 | Phase 1 | 1.1 |
| GAP-EH-01 | Phase 1 | 1.2 |
| GAP-EH-02 | Phase 1 | 1.3 |
| GAP-OB-02 | Phase 1 | 1.4 |
| GAP-SE-01 | Phase 1 | 1.5 |
| GAP-RE-03 | Phase 1 | 1.6 |
| GAP-RE-02 | Phase 1 | 1.7 |
| GAP-RE-07 | Phase 1 | 1.8 |
| GAP-SE-05 | Phase 1 | 1.9 |
| GAP-AD-06 | Phase 1 | 1.10 |
| GAP-EH-03 | Phase 2 | 2.1 |
| GAP-RE-01 | Phase 2 | 2.2 |
| GAP-EH-05 | Phase 2 | 2.3 |
| GAP-OB-01 | Phase 2 | 2.4 |
| GAP-SE-04 | Phase 2 | 2.5 |
| GAP-SE-07 | Phase 2 | 2.6 |
| GAP-CO-02 | Phase 2 | 2.7 |
| GAP-OB-03 | Phase 2 | 2.8 |
| GAP-SE-06 | Phase 2 | 2.9 |
| GAP-AD-03 | Phase 2 | 2.10 |
| GAP-RE-05 | Phase 2 | 2.11 |
| GAP-RE-04 | Phase 2 | 2.12 |
| GAP-TE-01 | Phase 3 | 3.1 |
| GAP-TE-02 | Phase 3 | 3.2 |
| GAP-TE-03 | Phase 3 | 3.3 |
| GAP-TE-04 | Phase 3 | 3.4 |
| GAP-CO-01 | Phase 3 | 3.5 |
| GAP-OB-04 | Phase 3 | 3.6 |
| GAP-CO-03 | Phase 3 | 3.7 |
| GAP-CO-04 | Phase 3 | 3.8 |
| GAP-AD-01 | Phase 3 | 3.9 |
| GAP-SE-03 | Phase 3 | 3.10 |
| GAP-OB-05 | Phase 3 | 3.11 |
| GAP-AD-02, GAP-AD-04, GAP-AD-05 | Phase 3 | 3.12 |
| GAP-EH-04 | Phase 2 | 2.7 (resolved by shared library extraction) |
| GAP-SE-02 | Phase 1 | 1.5 (addressed alongside credential externalization) |
