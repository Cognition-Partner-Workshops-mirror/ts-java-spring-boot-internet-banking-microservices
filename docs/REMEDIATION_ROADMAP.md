# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact, low-effort items that immediately improve reliability, security, and developer experience.

### 1.1 Fix Generic Error Handler — Return Proper HTTP Status Codes

**Gap:** 2.1, 2.2, 2.3 | **Severity:** Critical + High | **Effort:** Small

Refactor `GlobalExceptionHandler` in all 4 services to:
- Return structured `ErrorResponse` for all exceptions (not raw strings)
- Map `EntityNotFoundException` to 404, `InsufficientFundsException` to 422, `UserAlreadyRegisteredException` to 409
- Map unexpected exceptions to 500 with a generic message (no stack traces)

```
Devin Prompt: "In the ts-java-spring-boot-internet-banking-microservices repo, refactor the
GlobalExceptionHandler class in all four business services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service). Map EntityNotFoundException to 404,
InsufficientFundsException to 422, UserAlreadyRegisteredException to 409,
InvalidEmailException/InvalidBankingUserException to 400, and all other exceptions to 500
with a generic safe message. Always return the structured ErrorResponse {code, message}
object — never a raw string. Run the existing tests to make sure nothing breaks."
```

---

### 1.2 Add Bean Validation to All Request DTOs

**Gap:** 4.3 | **Severity:** Critical | **Effort:** Small-Medium

Add `jakarta.validation` annotations to all request DTOs and `@Valid` to controller parameters.

```
Devin Prompt: "Add Bean Validation (jakarta.validation) to all request DTOs in
ts-java-spring-boot-internet-banking-microservices. Specifically:
- FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
  @NotBlank on referenceNumber and account
- User registration DTO: @NotBlank on firstName/lastName, @Email on email, @NotBlank on
  password with @Size(min=8), @NotBlank on identification
- UserUpdateRequest: @NotNull on status
Add @Valid on all @RequestBody parameters in controllers. Add spring-boot-starter-validation
dependency to each service's build.gradle. Add a MethodArgumentNotValidException handler to
GlobalExceptionHandler that returns 400 with field-level error details."
```

---

### 1.3 Add Feign Timeout and Retry Configuration

**Gap:** 7.2, 7.3 | **Severity:** High | **Effort:** Small

```
Devin Prompt: "Add Feign client timeout and retry configuration to
internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and
internet-banking-user-service. Set connect timeout to 5 seconds and read timeout to 10
seconds. Add a Retryer bean that retries up to 3 times with 1-second intervals for
5xx responses only. Configure this in each service's application.yml or via a shared
FeignClientConfiguration class."
```

---

### 1.4 Externalize Hardcoded Credentials

**Gap:** 4.1 | **Severity:** Critical | **Effort:** Small

```
Devin Prompt: "In ts-java-spring-boot-internet-banking-microservices, replace all hardcoded
passwords in docker-compose.yml and docker-compose-support-apps.yml with environment
variables (e.g., ${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD},
${POSTGRES_PASSWORD}). Create a docker-compose/.env.example file documenting all required
variables with placeholder values. Update the README.md with instructions to copy
.env.example to .env and fill in real values. Add .env to .gitignore. Remove the test
credentials from README.md and reference the Postman environment instead."
```

---

### 1.5 Fix Raw ResponseEntity Types

**Gap:** 5.1 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "In ts-java-spring-boot-internet-banking-microservices, add proper generic
type parameters to all ResponseEntity return types in every controller across all services.
For example, change 'ResponseEntity' to 'ResponseEntity<BankAccount>' in
AccountController.getBankAccount(). This includes AccountController, TransactionController,
UserController (core-banking), FundTransferController, and UtilityPaymentController.
Run the build to verify compilation succeeds."
```

---

### 1.6 Fix Pagination Responses

**Gap:** 5.3 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "In ts-java-spring-boot-internet-banking-microservices, update all paginated
GET endpoints to return Spring's Page<T> wrapper (or a custom PageResponse DTO with
content, totalElements, totalPages, page, size) instead of raw List<T>. This affects:
- CoreBanking UserController.readUsers()
- UserService UserController.readUsers()
- FundTransferController.readFundTransfers()
- UtilityPaymentController.readPayments()
Update the service layer to return Page<T> from the repository and pass it through."
```

---

### 1.7 Remove Sensitive Data from Logs

**Gap:** 4.6 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "In ts-java-spring-boot-internet-banking-microservices, audit all log
statements across every service. Remove or mask any logging of full request objects that
may contain passwords, account numbers, or financial amounts. Replace toString() calls
with selective field logging (e.g., log only transaction ID, not amount). Specifically
check UserController.createUser(), FundTransferController, TransactionController, and
UtilityPaymentController."
```

---

### 1.8 Fix Keycloak Singleton Thread Safety

**Gap:** 4.4 | **Severity:** High | **Effort:** Small

```
Devin Prompt: "In internet-banking-user-service, fix the thread-safety issue in
KeycloakProperties.getInstance(). Replace the non-thread-safe static singleton pattern
with a proper Spring @Bean definition. Create a @Configuration class that provides the
Keycloak instance as a singleton Spring bean using KeycloakBuilder. Remove the static
field and getInstance() method from KeycloakProperties."
```

---

### 1.9 Fix Context Load Tests

**Gap:** 3.4 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "In ts-java-spring-boot-internet-banking-microservices, fix the
*ApplicationTests.java context-load tests in all services so they can run without
external infrastructure (Config Server, Eureka, MySQL). Create a test application.yml
(or application-test.yml) in each service's src/test/resources/ that:
- Disables Eureka client registration
- Disables Spring Cloud Config
- Uses H2 in-memory database
- Configures a test profile
Verify all tests pass with './gradlew test' in each service directory."
```

---

## Phase 2: Important Improvements (3-6 weeks)

Structural improvements that significantly enhance resilience, security, and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap:** 7.1, 7.4 | **Severity:** Critical | **Effort:** Medium

```
Devin Prompt: "Add Resilience4j circuit breakers to all Feign clients in
ts-java-spring-boot-internet-banking-microservices. Add the
spring-cloud-starter-circuitbreaker-resilience4j dependency to internet-banking-fund-
transfer-service, internet-banking-utility-payment-service, and internet-banking-user-
service. Configure circuit breakers with: failure-rate-threshold=50,
wait-duration-in-open-state=30s, sliding-window-size=10. Add fallback methods that return
appropriate error responses (e.g., 'Core banking service is temporarily unavailable').
Configure this via application.yml and @CircuitBreaker annotations on Feign client methods."
```

---

### 2.2 Fix Non-Atomic Balance Updates

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Medium

```
Devin Prompt: "In core-banking-service TransactionService, fix the non-atomic balance
update issue in internalFundTransfer() and utilPayment(). Problems to fix:
1. Add @Version field to BankAccountEntity for optimistic locking
2. Fix the double-subtraction bug where availableBalance is set to
   actualBalance.subtract(amount) AFTER actualBalance was already decremented
3. Add SELECT ... FOR UPDATE (pessimistic locking) or use optimistic locking with retry
   for concurrent transfer protection
4. Ensure the debit and credit operations are truly atomic — if credit fails, debit must
   roll back
Add unit tests that verify correct balance calculations after transfers."
```

---

### 2.3 Add Feign Error Decoder to All Services

**Gap:** 2.4 | **Severity:** High | **Effort:** Medium

```
Devin Prompt: "Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, similar to the one in internet-banking-user-
service. The decoder should parse the ErrorResponse body from core-banking-service and
re-throw appropriate domain exceptions (EntityNotFoundException, InsufficientFundsException,
etc.) instead of generic FeignException. Register it in the CustomFeignClientConfiguration
class in each service."
```

---

### 2.4 Handle Failed Feign Calls with Compensation

**Gap:** 2.5 | **Severity:** Critical | **Effort:** Medium

```
Devin Prompt: "In internet-banking-fund-transfer-service and internet-banking-utility-
payment-service, add proper error handling when Feign calls to core-banking-service fail.
When the Feign call throws an exception:
1. Update the local record status to FAILED (add FAILED to TransactionStatus enum)
2. Log the failure with full context (transaction ID, accounts, amount)
3. Consider adding a scheduled job that retries FAILED transactions
4. Return a meaningful error response to the client instead of a 400 catch-all
Wrap the Feign call in a try-catch within the service method."
```

---

### 2.5 Add RBAC (Role-Based Access Control)

**Gap:** 4.5 | **Severity:** High | **Effort:** Medium

```
Devin Prompt: "Add role-based access control to ts-java-spring-boot-internet-banking-
microservices. In the API Gateway SecurityConfiguration:
1. Configure JWT role extraction from Keycloak token claims
2. Restrict PATCH /user/api/v1/bank-users/update/{id} to ADMIN role
3. Restrict GET /user/api/v1/bank-users (list all users) to ADMIN role
4. Allow fund transfer and utility payment endpoints for USER role
5. Add the X-Auth-Id validation in downstream services to ensure users can only
   access their own resources
Update the Keycloak realm export to include ADMIN and USER roles."
```

---

### 2.6 Add Unit Tests for All Services

**Gap:** 3.1 | **Severity:** Critical | **Effort:** Large

```
Devin Prompt: "Add comprehensive unit tests for all services in
ts-java-spring-boot-internet-banking-microservices. For each service, create test classes
for every service-layer class using Mockito. Target coverage:
- internet-banking-user-service: UserService (createUser, updateUser, readUser, readUsers),
  KeycloakUserService
- internet-banking-fund-transfer-service: FundTransferService (fundTransfer, readAllTransfers)
- internet-banking-utility-payment-service: UtilityPaymentService (utilPayment, readPayments)
Use H2 in-memory database for any data tests. Mock all Feign clients and Keycloak
dependencies. Verify both happy path and error cases."
```

---

### 2.7 Add Prometheus Metrics Export

**Gap:** 6.3 | **Severity:** Medium | **Effort:** Medium

```
Devin Prompt: "Add Prometheus metrics export to all services in
ts-java-spring-boot-internet-banking-microservices. Add micrometer-registry-prometheus
dependency to each service's build.gradle. Configure actuator to expose the prometheus
endpoint in application.yml:
  management.endpoints.web.exposure.include: health,info,prometheus,metrics
Add custom business metrics using MeterRegistry:
- Counter for fund transfers (success/failure)
- Counter for utility payments (success/failure)
- Timer for Feign client call duration
- Gauge for active transactions in PENDING state"
```

---

### 2.8 Add Rate Limiting to API Gateway

**Gap:** 7.5 | **Severity:** Medium | **Effort:** Medium

```
Devin Prompt: "Add rate limiting to the API Gateway in
ts-java-spring-boot-internet-banking-microservices. Use Spring Cloud Gateway's built-in
RequestRateLimiter filter with Redis (or an in-memory rate limiter for simplicity).
Configure:
- 10 requests/second for fund transfer endpoints
- 10 requests/second for utility payment endpoints
- 20 requests/second for read-only endpoints
- 5 requests/second for user registration (to prevent abuse)
Rate limit by authenticated user ID (from JWT) or by IP for unauthenticated endpoints."
```

---

### 2.9 Extract Shared Library Module

**Gap:** 1.1 | **Severity:** Medium | **Effort:** Medium

```
Devin Prompt: "Refactor ts-java-spring-boot-internet-banking-microservices to use a
Gradle multi-module build with a shared common library. Create a new module called
'internet-banking-common' that contains:
- BaseMapper<E, D>
- AuditAware entity superclass
- AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
- ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler base class
- GlobalErrorCode constants
Update all services to depend on this common module and remove their duplicated copies.
Create a settings.gradle at the root that includes all modules."
```

---

## Phase 3: Polish & Production Readiness (6-12 weeks)

Items that bring the system to production-grade quality.

### 3.1 Add Integration Tests

**Gap:** 3.2 | **Severity:** High | **Effort:** Large

```
Devin Prompt: "Add integration tests for all business services in
ts-java-spring-boot-internet-banking-microservices. For each service:
1. Add @WebMvcTest controller tests that verify HTTP methods, status codes, content types,
   and validation error responses
2. Add @DataJpaTest repository tests with H2 for custom query methods
3. Add @SpringBootTest full-flow tests using WireMock to mock downstream Feign clients
Create a test profile that uses H2, disables Eureka, and disables Config Server.
Ensure all tests pass with './gradlew test'."
```

---

### 3.2 Add Contract Tests Between Services

**Gap:** 3.3 | **Severity:** Medium | **Effort:** Large

```
Devin Prompt: "Add Spring Cloud Contract tests between services in
ts-java-spring-boot-internet-banking-microservices. Set up contract tests for:
1. Fund Transfer Service <-> Core Banking Service (fund-transfer endpoint, account lookup)
2. Utility Payment Service <-> Core Banking Service (util-payment endpoint, account lookup)
3. User Service <-> Core Banking Service (user lookup by identification)
The producer side (core-banking-service) should define contracts and generate stubs.
Consumer services should use stub runners to verify their Feign clients match the contracts.
Add spring-cloud-contract-verifier to core-banking-service and
spring-cloud-contract-stub-runner to consumer services."
```

---

### 3.3 Enhance OpenAPI Documentation

**Gap:** 5.5 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "Enhance OpenAPI/Swagger documentation in
ts-java-spring-boot-internet-banking-microservices. For each service:
1. Add @OpenAPIDefinition with title, version, description, and contact info
2. Add @Schema annotations to all DTOs with field descriptions and examples
3. Add @ApiResponse annotations to controllers for success and error responses
4. Add security scheme definition for Bearer JWT
5. Verify Swagger UI is accessible at /swagger-ui.html for each service
6. Consider aggregating all service specs at the API Gateway level."
```

---

### 3.4 Add Structured JSON Logging

**Gap:** 6.1 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "Add structured JSON logging to all services in
ts-java-spring-boot-internet-banking-microservices. Add logstash-logback-encoder dependency
to each service. Create a logback-spring.xml in src/main/resources/ that outputs JSON format
with fields: timestamp, level, service, traceId, spanId, message. Ensure Zipkin trace IDs
are automatically included in all log entries. Add an MDC filter that includes the
X-Auth-Id as a log field for request tracing."
```

---

### 3.5 Add Custom Health Indicators

**Gap:** 6.2 | **Severity:** Low | **Effort:** Small

```
Devin Prompt: "Add custom health indicators to services in
ts-java-spring-boot-internet-banking-microservices:
1. In internet-banking-user-service: add a Keycloak health indicator that checks connectivity
2. In all database services: verify the default DataSource health indicator is active
3. In services with Feign clients: add a health indicator that checks core-banking-service
   availability
4. Configure separate liveness and readiness probes in application.yml:
   management.endpoint.health.probes.enabled=true
   management.health.livenessstate.enabled=true
   management.health.readinessstate.enabled=true"
```

---

### 3.6 Add Dependency Vulnerability Scanning

**Gap:** 4.7 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "Add OWASP Dependency-Check to the Gradle build in
ts-java-spring-boot-internet-banking-microservices. Add the
org.owasp.dependencycheck plugin to each service's build.gradle. Configure it to fail
the build on CVSS score >= 7.0. Run './gradlew dependencyCheckAnalyze' and document
any findings. Consider also adding the Gradle Versions plugin to identify outdated
dependencies."
```

---

### 3.7 Normalize REST API Conventions

**Gap:** 5.4 | **Severity:** Medium | **Effort:** Small

```
Devin Prompt: "Normalize REST API conventions across all services in
ts-java-spring-boot-internet-banking-microservices:
1. Rename POST /register to POST /bank-users (standard resource creation)
2. Rename PATCH /update/{id} to PATCH /bank-users/{id} (remove verb from URL)
3. Use consistent path variable naming: kebab-case for paths, camelCase for params
4. Return 201 Created (with Location header) for POST endpoints that create resources
5. Return 204 No Content for successful deletes (if any are added)
Make sure the API Gateway routes are updated to match any path changes.
Update the Postman collection if it exists in the repo."
```

---

### 3.8 Add Custom Tracing Spans

**Gap:** 6.4 | **Severity:** Low | **Effort:** Small

```
Devin Prompt: "Add custom tracing spans to critical business operations in
ts-java-spring-boot-internet-banking-microservices. Using Micrometer Observation API:
1. In TransactionService: add spans for fundTransfer and utilPayment with tags for
   fromAccount, toAccount, amount, and transactionId
2. In FundTransferService: add a span for the full transfer orchestration
3. In UtilityPaymentService: add a span for the full payment orchestration
4. In KeycloakUserService: add spans for Keycloak API calls
This will improve visibility in Zipkin for troubleshooting transaction flows."
```

---

### 3.9 Implement Notification Service

**Gap:** Mentioned in README as PENDING | **Effort:** Large

```
Devin Prompt: "Implement the notification service mentioned in the README for
ts-java-spring-boot-internet-banking-microservices. Create a new service module
'internet-banking-notification-service' that:
1. Consumes messages from RabbitMQ queues
2. Processes fund transfer and utility payment notification events
3. Sends email notifications (use a mock SMTP server for development)
4. Registers with Eureka and fetches config from Config Server
Add RabbitMQ to docker-compose.yml. Publish events from FundTransferService and
UtilityPaymentService after successful transactions. Follow the same project structure
as the existing services."
```

---

## Phase Summary

| Phase | Items | Critical Fixes | Estimated Effort |
|---|---|---|---|
| **Phase 1: Quick Wins** | 9 items | 4 (error handling, validation, credentials, context tests) | 1-2 weeks |
| **Phase 2: Important** | 9 items | 3 (circuit breakers, atomicity, compensation) | 3-6 weeks |
| **Phase 3: Polish** | 9 items | 0 | 6-12 weeks |

### Recommended Order Within Phase 1
1. Fix error handlers (1.1) — immediate reliability improvement
2. Add input validation (1.2) — security quick win
3. Externalize credentials (1.4) — security hygiene
4. Fix Keycloak singleton (1.8) — concurrency fix
5. Add Feign timeouts (1.3) — prevents thread exhaustion
6. Fix raw ResponseEntity types (1.5) — compile safety
7. Remove sensitive log data (1.7) — compliance
8. Fix pagination responses (1.6) — API quality
9. Fix context load tests (1.9) — CI enablement

### Recommended Order Within Phase 2
1. Fix non-atomic balance updates (2.2) — data integrity
2. Add circuit breakers (2.1) — resilience
3. Add Feign error decoders (2.3) — error propagation
4. Handle failed Feign calls (2.4) — transaction integrity
5. Add RBAC (2.5) — authorization security
6. Add unit tests (2.6) — regression safety
7. Extract shared library (2.9) — maintainability
8. Add Prometheus metrics (2.7) — observability
9. Add rate limiting (2.8) — abuse protection
