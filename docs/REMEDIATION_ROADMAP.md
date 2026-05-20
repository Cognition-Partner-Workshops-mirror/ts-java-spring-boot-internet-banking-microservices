# Remediation Roadmap

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Date:** 2026-05-20
> **Reference:** [Gap Analysis](./GAP_ANALYSIS.md)

---

## Phasing Strategy

| Phase | Focus | Criteria |
|---|---|---|
| **Phase 1 – Quick Wins** | High/Critical severity + Small effort | Immediate risk reduction with minimal code changes |
| **Phase 2 – Important** | High/Critical severity + Medium effort | Structural improvements requiring moderate refactoring |
| **Phase 3 – Polish** | Medium/Low severity items | Quality-of-life and long-term maintainability |

---

## Phase 1: Quick Wins

_Target: 1–2 sprints. High-impact fixes with small code footprint._

### 1.1 Fix Exception Handler Information Leakage (EH-3)

**Severity:** Critical | **Effort:** Small

Replace the generic exception handler in all services to return a safe error message instead of exposing internal exception details.

**Devin Prompt:**
```
In the repository ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler class in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Change the handleException(Exception e) method to return a structured ErrorResponse with a generic message like "An unexpected error occurred. Please contact support." and HTTP 500 status instead of HTTP 400. Log the full exception server-side at ERROR level. Do not expose the exception message or stack trace in the response body.
```

### 1.2 Fix HTTP Status Codes in Error Handlers (EH-1)

**Severity:** High | **Effort:** Small

Map exceptions to correct HTTP status codes: `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, validation errors → 400, generic → 500.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update GlobalExceptionHandler in all services to use proper HTTP status codes. Add a dedicated @ExceptionHandler for EntityNotFoundException that returns 404 Not Found. Add a handler for InsufficientFundsException returning 422 Unprocessable Entity. Keep SimpleBankingGlobalException as 400 Bad Request. Change the generic Exception handler to return 500 Internal Server Error with a safe message. Standardize the error response format to always use ErrorResponse(code, message) across all services.
```

### 1.3 Standardize Error Response Format (EH-2)

**Severity:** High | **Effort:** Small

Ensure all error handlers return `ErrorResponse { code, message }` JSON consistently.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, ensure all GlobalExceptionHandler classes across all services return ErrorResponse objects consistently. In fund-transfer-service, the ErrorResponse constructor is used directly instead of the builder. In all services, the generic Exception handler returns a plain string. Update all handlers to use ErrorResponse.builder().code("INTERNAL_ERROR").message("...").build() pattern. Make sure ErrorResponse class is identical across all services (or better, move to a shared library).
```

### 1.4 Add Input Validation to Request DTOs (SE-3)

**Severity:** Critical | **Effort:** Small–Medium

Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` on controller method parameters.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation annotations to all request DTOs across all services. Specifically:
- FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber/account
- User (user-service): @NotBlank @Email on email, @NotBlank on identification, @NotBlank on password
- UserUpdateRequest: @NotNull on status

Add @Valid annotation on all @RequestBody parameters in controllers. Add spring-boot-starter-validation dependency to each service's build.gradle. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler that returns 400 with field-level error details.
```

### 1.5 Configure Feign and Connection Timeouts (RE-2)

**Severity:** Critical | **Effort:** Small

Add timeout configuration for all Feign clients to prevent thread exhaustion.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign timeout configuration to the application.yml (or the external Spring Cloud Config repo) for all services that use Feign clients (user-service, fund-transfer-service, utility-payment-service). Set connect-timeout to 5000ms and read-timeout to 10000ms. Configuration example:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
Also add this to each service's test application.yml.
```

### 1.6 Add Circuit Breakers (RE-1)

**Severity:** Critical | **Effort:** Small–Medium

Add Resilience4j circuit breakers to Feign clients to prevent cascade failures.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breaker support to all services using Feign clients (user-service, fund-transfer-service, utility-payment-service). Add these dependencies to each service's build.gradle:
- implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

Enable circuit breaker for Feign in application.yml:
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

Add a fallback factory for each Feign client that returns a meaningful error response when the circuit is open. Configure circuit breaker properties: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s.
```

### 1.7 Externalize Hardcoded Credentials (SE-1)

**Severity:** Critical | **Effort:** Small

Replace hardcoded passwords in docker-compose files with environment variables.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml to use environment variables instead of hardcoded passwords. Replace:
- MYSQL_ROOT_PASSWORD: woVERANKliGharym → MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
- KC_DB_PASSWORD: password → KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
- KEYCLOAK_ADMIN_PASSWORD: password → KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
- POSTGRES_PASSWORD: password → POSTGRES_PASSWORD: ${POSTGRES_DB_PASSWORD:-changeme}

Also update docker-compose/mysql/Dockerfile to use ARG/ENV instead of hardcoded password. Add a .env.example file documenting the required environment variables.
```

### 1.8 Stop Logging Sensitive Data (OB-7)

**Severity:** High | **Effort:** Small

Remove or mask account numbers and amounts from log statements.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, audit all log.info() and log.error() calls across all services for sensitive data exposure. In FundTransferController, FundTransferService, UtilityPaymentController, UtilityPaymentService, and TransactionController: replace logging of full request.toString() (which includes account numbers and amounts) with a safe summary like the transaction reference or a masked account number. Also fix string concatenation in log statements (e.g., log.info("..." + request.toString())) to use parameterized logging (log.info("...", maskedValue)).
```

### 1.9 Enable Structured JSON Logging (OB-1)

**Severity:** High | **Effort:** Small

Add Logstash Logback encoder for structured logging.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add structured JSON logging to all 6 services. Add this dependency to each service's build.gradle:
- implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

Create a shared logback-spring.xml in each service's src/main/resources/ that uses LogstashEncoder for non-local profiles and PatternLayoutEncoder for local development. Include traceId and spanId in the JSON output using MDC.
```

### 1.10 Add Dependency Vulnerability Scanning (SE-7)

**Severity:** High | **Effort:** Small

Add OWASP Dependency-Check Gradle plugin.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add the OWASP Dependency-Check Gradle plugin to all services. Add to each build.gradle:
plugins {
    id 'org.owasp.dependencycheck' version '9.0.10'
}
Configure it to fail the build on CVSS score >= 7. Add a GitHub Actions workflow that runs dependency-check on PRs.
```

### 1.11 Fix Springdoc Dependency (AD-7)

**Severity:** Medium | **Effort:** Small

Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in servlet-based services.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, replace the incorrect Springdoc dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Change:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
These services use spring-boot-starter-web (servlet), not webflux. Also update the version to 2.5.0 for latest fixes.
```

### 1.12 Add Test Coverage Reporting (TE-6)

**Severity:** Medium | **Effort:** Small

Configure JaCoCo for coverage reporting.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add JaCoCo test coverage reporting to all services. Add to each build.gradle:
plugins {
    id 'jacoco'
}
jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
Add a minimum coverage threshold of 50% initially (to be raised over time) using jacocoTestCoverageVerification.
```

---

## Phase 2: Important

_Target: 2–4 sprints. Structural improvements requiring moderate refactoring._

### 2.1 Create Shared Library (CO-1, EH-4)

**Severity:** High | **Effort:** Medium

Extract duplicated code into a `banking-common` Gradle module.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a new Gradle module called 'banking-common' with group 'com.javatodev.finance'. Extract these classes into it:
- BaseMapper (model.mapper)
- AuditAware (model.dto)
- ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter (configuration.filter)
- AuditorAwareConfig, AuditConfig (configuration.audit)
- ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler (exception)

Publish it as a local dependency. Update all services to depend on banking-common instead of maintaining their own copies. Use Gradle composite builds (includeBuild) so services can reference banking-common without publishing to a Maven repository.
```

### 2.2 Add Unit Tests for Internet Banking Services (TE-1)

**Severity:** Critical | **Effort:** Large

Write comprehensive unit tests for all business logic in the 3 orchestration services.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add unit tests for:

1. internet-banking-fund-transfer-service: FundTransferService - test fundTransfer() success, Feign failure handling, readAllTransfers(). FundTransferController - test endpoint mapping and response.

2. internet-banking-utility-payment-service: UtilityPaymentService - test utilPayment() success, Feign failure handling, readPayments(). UtilityPaymentController - test endpoint mapping and response.

3. internet-banking-user-service: UserService - test createUser() success, duplicate email, invalid email, user not found in core. Test readUsers(), readUser(), updateUser() with approval flow. Mock KeycloakUserService and BankingCoreRestClient using Mockito.

Use @ExtendWith(MockitoExtension.class) and mock all dependencies. Aim for >80% line coverage on service classes.
```

### 2.3 Add Error Recovery for Orchestration Services (EH-6, RE-6)

**Severity:** High | **Effort:** Medium

Add try-catch around Feign calls and update entity status to FAILED on errors.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment() to handle Feign client failures:

1. Wrap the bankingCoreFeignClient call in a try-catch block
2. On failure, update the local entity status to FAILED
3. Log the error with the transaction reference
4. Throw a service-specific exception with the failure details
5. Add a @Scheduled method (or a separate endpoint) to retry FAILED transactions

Also add a Feign error decoder to fund-transfer-service (similar to the one in user-service) that converts Feign error responses into domain exceptions.
```

### 2.4 Add Pagination Metadata to Responses (AD-2)

**Severity:** High | **Effort:** Medium

Return `Page<T>` or a wrapper DTO with pagination info instead of plain lists.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, update all paginated GET endpoints to return pagination metadata. Create a generic PageResponse<T> DTO in the shared library:
public class PageResponse<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
Update these endpoints to return PageResponse:
- GET /api/v1/user (core-banking)
- GET /api/v1/bank-users (user-service)
- GET /api/v1/transfer (fund-transfer-service)
- GET /api/v1/utility-payment (utility-payment-service)
```

### 2.5 Add Role-Based Access Control (SE-4)

**Severity:** High | **Effort:** Medium

Implement RBAC so admin operations are restricted to authorized users.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, implement role-based access control:

1. In Keycloak realm configuration, add roles: ROLE_USER, ROLE_ADMIN
2. Update API Gateway SecurityConfiguration to pass JWT roles to downstream services
3. In user-service, restrict PATCH /update/{id} to ROLE_ADMIN
4. In user-service, restrict GET /bank-users (list all) to ROLE_ADMIN
5. Keep POST /register as public, GET /{id} accessible to ROLE_USER (own profile only)
6. Add @PreAuthorize annotations or gateway-level route security rules
```

### 2.6 Add Idempotency for Write Operations (RE-5)

**Severity:** High | **Effort:** Medium

Implement idempotency keys for POST endpoints to prevent duplicate transactions.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add idempotency support for fund transfer and utility payment POST endpoints:

1. Accept an optional X-Idempotency-Key header
2. Before processing, check if a transaction with that key already exists
3. If found, return the existing response (don't process again)
4. If not found, process normally and store the key with the transaction
5. Add a unique constraint on the idempotency key column
6. Implement this in both fund-transfer-service and utility-payment-service
```

### 2.7 Add Integration Tests (TE-2)

**Severity:** High | **Effort:** Medium–Large

Write integration tests using `@SpringBootTest` with embedded H2 database.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests for core-banking-service:

1. Create @SpringBootTest tests that use H2 in-memory database
2. Test the full request lifecycle: controller → service → repository → database
3. Use @AutoConfigureMockMvc to test through the HTTP layer
4. Test AccountController: GET bank account, GET utility account (success + not found)
5. Test TransactionController: POST fund transfer (success, insufficient funds), POST utility payment
6. Test UserController: GET user by identification, GET users paginated
7. Use Flyway test migrations or JPA ddl-auto=create for test schema

Ensure Eureka client is disabled and Spring Cloud Config is not required in tests.
```

### 2.8 Add Prometheus Metrics (OB-4)

**Severity:** Medium | **Effort:** Small

Add Micrometer Prometheus registry for metrics scraping.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics support to all services:

1. Add to each build.gradle: implementation 'io.micrometer:micrometer-registry-prometheus'
2. Configure actuator to expose prometheus endpoint in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
3. Add custom metrics for business operations (fund transfer count, payment count, error count) using MeterRegistry.
```

### 2.9 Add Correlation ID to Logs and Responses (OB-6)

**Severity:** Medium | **Effort:** Medium

Include trace/span IDs in log output and response headers.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add correlation ID propagation:

1. Update logback-spring.xml to include traceId and spanId in log patterns: [%X{traceId}/%X{spanId}]
2. Add a response filter in each service (or in the API Gateway) that copies the traceId into a X-Trace-Id response header
3. Ensure the gateway propagates trace context headers (b3 or W3C traceparent) to downstream services
4. This helps clients correlate their requests with server-side logs
```

---

## Phase 3: Polish

_Target: Ongoing. Lower severity items for long-term quality._

### 3.1 Create Root Gradle Composite Build (CO-3)

**Severity:** Medium | **Effort:** Medium

Add a root `settings.gradle` that includes all services for single-command builds.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a root settings.gradle that includes all service projects as a composite build:

includeBuild 'banking-common'
includeBuild 'internet-banking-service-registry'
includeBuild 'internet-banking-config-server'
includeBuild 'internet-banking-api-gateway'
includeBuild 'internet-banking-user-service'
includeBuild 'internet-banking-fund-transfer-service'
includeBuild 'internet-banking-utility-payment-service'
includeBuild 'core-banking-service'

Add a root build.gradle with common tasks: buildAll, testAll, cleanAll. This enables developers to build and test all services with a single command.
```

### 3.2 Unify Feign Client Configuration (CO-2)

**Severity:** Medium | **Effort:** Small

Standardize Feign error decoding and logging across all services.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, standardize Feign client configuration across all services. Move the CustomFeignErrorDecoder from user-service into the shared banking-common library. Apply it to all Feign clients in fund-transfer-service and utility-payment-service. Configure consistent Feign logging level (BASIC for production, FULL for development) via Spring Cloud Config profiles.
```

### 3.3 Consistent Package Naming (CO-5)

**Severity:** Low | **Effort:** Small

Align DTO packages across all services to use `model.dto.request` / `model.dto.response`.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, refactor the utility-payment-service package structure to align with other services. Move:
- com.javatodev.finance.model.rest.request → com.javatodev.finance.model.dto.request
- com.javatodev.finance.model.rest.response → com.javatodev.finance.model.dto.response
Update all import statements accordingly.
```

### 3.4 Make Mappers Spring Beans (CO-4)

**Severity:** Low | **Effort:** Small

Annotate mappers with `@Component` and inject them instead of using `new`.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, refactor all mapper classes to be Spring-managed beans. Add @Component annotation to BaseMapper subclasses (BankAccountMapper, UserMapper, FundTransferMapper, UtilityPaymentMapper, UtilityAccountMapper). In service classes, replace 'private XMapper mapper = new XMapper()' with '@Autowired private XMapper mapper' (or constructor injection). This enables proper lifecycle management and easier testing.
```

### 3.5 Add Contract Tests (TE-3)

**Severity:** High | **Effort:** Large

Add Spring Cloud Contract or Pact tests for Feign client interfaces.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests for the Feign client interfaces:

1. Add spring-cloud-starter-contract-verifier to core-banking-service (producer)
2. Write contract definitions for all endpoints consumed by user-service, fund-transfer-service, and utility-payment-service
3. Add spring-cloud-starter-contract-stub-runner to the consumer services
4. Write consumer-side tests that verify Feign clients work against the contract stubs
5. This ensures changes to core-banking-service API don't break consumers
```

### 3.6 Add API Gateway Security Tests (TE-4)

**Severity:** Medium | **Effort:** Medium

Test that security rules are correctly applied at the gateway.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add tests for the API Gateway security configuration:

1. Test that /user/api/v1/bank-users/register is accessible without authentication
2. Test that /actuator/** endpoints are accessible without authentication
3. Test that all other endpoints return 401 without a valid JWT
4. Test that valid JWT tokens are accepted and requests are proxied
5. Use @SpringBootTest with WebTestClient and mock JWT tokens (spring-security-test)
```

### 3.7 Add Custom Health Indicators (OB-3)

**Severity:** Medium | **Effort:** Small

Add health checks for downstream dependencies.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add custom health indicators:

1. In user-service: Add a KeycloakHealthIndicator that checks Keycloak availability
2. In fund-transfer-service and utility-payment-service: Add a CoreBankingHealthIndicator that checks if core-banking-service is reachable via Eureka
3. In core-banking-service: Database health is already covered by Spring Boot auto-config, but add a custom indicator for the config server connection
4. Implement by extending AbstractHealthIndicator and checking connectivity
```

### 3.8 Add Rate Limiting (RE-8)

**Severity:** Medium | **Effort:** Medium

Add rate limiting at the API Gateway level.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add rate limiting to the API Gateway:

1. Add spring-boot-starter-data-redis-reactive dependency
2. Configure Spring Cloud Gateway RequestRateLimiter filter using Redis
3. Set default rate limits: 100 requests/second per user (identified by JWT subject)
4. Set stricter limits for write endpoints: 10 requests/second for POST /transfer and /utility-payment
5. Return 429 Too Many Requests with Retry-After header when limit is exceeded
6. Add Redis to docker-compose.yml
```

### 3.9 Add Bulkhead Pattern (RE-9)

**Severity:** Medium | **Effort:** Medium

Isolate Feign client thread pools to prevent cascading thread exhaustion.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j bulkhead configuration for Feign clients:

1. Configure thread pool bulkheads for each Feign client
2. Set maxConcurrentCalls and maxWaitDuration
3. Example configuration:
   resilience4j:
     bulkhead:
       instances:
         core-banking-service:
           maxConcurrentCalls: 25
           maxWaitDuration: 500ms
4. This ensures a slow downstream service doesn't consume all threads
```

### 3.10 Add CI/CD Pipeline (General)

**Severity:** Medium | **Effort:** Medium

Create a GitHub Actions workflow for build, test, and deploy.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a GitHub Actions CI/CD pipeline at .github/workflows/ci.yml:

1. Trigger on push to main and pull requests
2. Set up Java 21 and Gradle
3. Build all services: banking-common first, then all others in parallel
4. Run tests for all services
5. Run OWASP dependency check
6. Generate JaCoCo coverage report
7. Build Docker images for each service
8. Optionally push to Docker Hub on main branch
9. Add a badge to README.md showing build status
```

### 3.11 Fix Keycloak Singleton Thread Safety (RE-7)

**Severity:** Medium | **Effort:** Small

Make the Keycloak client instance thread-safe.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, fix the thread-safety issue in KeycloakProperties.getInstance(). Replace the non-synchronized singleton with either:
1. A @Bean method in a @Configuration class that creates the Keycloak instance once (Spring manages thread safety)
2. Or use volatile + double-checked locking pattern
Option 1 is preferred as it's more idiomatic Spring.
```

### 3.12 Add Global OpenAPI Configuration (AD-8)

**Severity:** Low | **Effort:** Small

Add `@OpenAPIDefinition` annotations for complete API documentation.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add OpenAPI configuration to each service. Create an OpenApiConfig class with @OpenAPIDefinition:
- Title: "<Service Name> API"
- Version: "1.0.0"
- Description: Brief service description
- Contact info
- Security scheme: Bearer JWT
Add @SecurityRequirement(name = "bearerAuth") on protected endpoints.
```

### 3.13 Consistent Resource Naming (AD-4)

**Severity:** Medium | **Effort:** Small

Standardize URL paths to use plural nouns.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, standardize REST resource naming:
- user-service: /api/v1/bank-users → /api/v1/users (keep backward compatibility with redirect or alias)
- core-banking: /api/v1/user → /api/v1/users
- fund-transfer: /api/v1/transfer → /api/v1/transfers
- utility-payment: /api/v1/utility-payment → /api/v1/utility-payments
Update Feign clients, Postman collection, and any other references accordingly. Consider versioning the API to v2 for breaking changes.
```

### 3.14 POST Registration Returns 201 (AD-5)

**Severity:** Medium | **Effort:** Small

Return `201 Created` for resource creation.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, update UserController.createUser() to return ResponseEntity.created(URI) with status 201 instead of ResponseEntity.ok(). Use ServletUriComponentsBuilder to build the Location header pointing to the newly created user resource.
```

---

## Implementation Priority Matrix

```
                    Small Effort          Medium Effort          Large Effort
                ┌──────────────────┬──────────────────────┬──────────────────┐
   Critical     │ EH-3, RE-2,     │ SE-3, RE-1           │ TE-1             │
                │ SE-1             │                      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   High         │ EH-1, EH-2,     │ EH-6/RE-6, AD-2,    │ TE-2, TE-3      │
                │ OB-1, OB-7,     │ SE-4, SE-5, RE-5     │                  │
                │ SE-7, RE-3,     │ RE-4                  │                  │
                │ SE-2             │                      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   Medium       │ AD-7, CO-2,     │ CO-1/EH-4, CO-3,    │                  │
                │ OB-2, OB-4,     │ OB-6, AD-3, RE-8,   │                  │
                │ OB-5, RE-7,     │ RE-9, TE-4           │                  │
                │ AD-4, AD-5,     │                      │                  │
                │ TE-5, TE-6      │                      │                  │
                ├──────────────────┼──────────────────────┼──────────────────┤
   Low          │ CO-4, CO-5,     │                      │                  │
                │ AD-6, AD-8,     │                      │                  │
                │ SE-8             │                      │                  │
                └──────────────────┴──────────────────────┴──────────────────┘
```

---

## Quick Reference: All Gap IDs by Phase

| Phase | Gap IDs |
|---|---|
| **Phase 1** | EH-3, EH-1, EH-2, SE-3, RE-2, RE-1, SE-1, OB-7, OB-1, SE-7, AD-7, TE-6, SE-2 |
| **Phase 2** | CO-1, EH-4, TE-1, EH-6, RE-6, AD-2, SE-4, RE-5, TE-2, OB-4, OB-6, RE-3, RE-4 |
| **Phase 3** | CO-3, CO-2, CO-5, CO-4, TE-3, TE-4, TE-5, OB-2, OB-3, OB-5, RE-8, RE-9, RE-7, AD-4, AD-5, AD-6, AD-8, SE-5, SE-6, SE-8 |
