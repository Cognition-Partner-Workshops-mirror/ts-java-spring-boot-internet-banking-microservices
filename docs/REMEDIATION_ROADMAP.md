# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to execute the remediation.

---

## Phase 1: Quick Wins (Critical & High severity, Small effort)

These items address active security risks and critical bugs with minimal code changes. They should be completed first to establish a safe baseline.

---

### 1.1 Fix Exception Catch-All Leaking Internal Details
**Gap Ref:** 2.2 | **Severity:** Critical | **Effort:** Small

The generic `@ExceptionHandler({Exception.class})` in all four `GlobalExceptionHandler` classes returns `"Exception occur inside API " + e`, leaking stack traces and internal class names to API consumers.

**Devin Prompt:**
```
In the repository ts-java-spring-boot-internet-banking-microservices, update the 
GlobalExceptionHandler class in all four services (core-banking-service, 
internet-banking-user-service, internet-banking-fund-transfer-service, 
internet-banking-utility-payment-service). Change the generic Exception handler to:
1. Return HTTP 500 Internal Server Error instead of 400 Bad Request
2. Return a structured ErrorResponse with code="INTERNAL_ERROR" and 
   message="An unexpected error occurred. Please try again later."
3. Log the full exception at ERROR level with log.error("Unexpected error", e)
4. Do NOT include any exception details in the response body
```

---

### 1.2 Remove Hardcoded Credentials from Source Code
**Gap Ref:** 4.1 | **Severity:** Critical | **Effort:** Small

Database passwords, Keycloak admin credentials, and test user credentials are hardcoded in docker-compose files and SQL scripts.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, replace all hardcoded 
credentials in docker-compose/docker-compose.yml, docker-compose/docker-compose-support-apps.yml, 
and docker-compose/mysql/privileges.sql with environment variable references. 
Create a docker-compose/.env.example file with placeholder values and add 
docker-compose/.env to .gitignore. The variables needed are:
- MYSQL_ROOT_PASSWORD
- MYSQL_APP_USER_PASSWORD (for javatodev_development user)
- KC_DB_PASSWORD
- KEYCLOAK_ADMIN_PASSWORD
Remove the test credentials from README.md and reference the .env.example file instead.
```

---

### 1.3 Fix Password Logging in User Registration
**Gap Ref:** 4.6 | **Severity:** High | **Effort:** Small

The user service controller logs `request.toString()` which includes the plaintext password due to Lombok's `@Data` annotation.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, 
fix the password logging issue:
1. In the User DTO class (model/dto/User.java), override toString() to exclude 
   the password field, or add @ToString.Exclude on the password field.
2. In UserController.java, change the log statement in createUser() to log only 
   the email: log.info("Creating user with email {}", request.getEmail())
3. Review all other log statements across all services that log request objects 
   containing sensitive data and fix them similarly.
```

---

### 1.4 Fix Keycloak Singleton Thread Safety
**Gap Ref:** 4.4 | **Severity:** High | **Effort:** Small

`KeycloakProperties.getInstance()` has a race condition in its lazy initialization.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service, 
fix the thread-safety issue in KeycloakProperties.java:
1. Remove the static Keycloak instance field and the getInstance() method.
2. Instead, create the Keycloak instance as a Spring @Bean in a @Configuration 
   class (e.g., KeycloakConfig.java) so Spring manages its lifecycle as a singleton.
3. Inject the Keycloak bean into KeycloakManager via constructor injection.
4. Update KeycloakManager to use the injected Keycloak instance instead of 
   calling keycloakProperties.getInstance().
```

---

### 1.5 Restrict Actuator Endpoints
**Gap Ref:** 6.3 | **Severity:** High | **Effort:** Small

All actuator endpoints are publicly accessible through the API Gateway without authentication.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-api-gateway, 
update SecurityConfiguration.java to restrict actuator access:
1. Only permit /actuator/health and /actuator/info without authentication.
2. Require authentication for all other actuator endpoints 
   (/actuator/env, /actuator/configprops, /actuator/metrics, etc.).
3. In each service's application config (via Spring Cloud Config), ensure only 
   health and info endpoints are exposed by default:
   management.endpoints.web.exposure.include=health,info
```

---

### 1.6 Configure Feign Timeouts
**Gap Ref:** 7.3 | **Severity:** High | **Effort:** Small

No connection or read timeouts are configured for inter-service Feign calls.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign timeout 
configuration to all three Feign-consuming services (user-service, 
fund-transfer-service, utility-payment-service). Add the following properties 
to each service's Spring Cloud Config:

spring.cloud.openfeign.client.config.default.connect-timeout=5000
spring.cloud.openfeign.client.config.default.read-timeout=10000

Also add connection pool configuration for the OkHttp client in fund-transfer 
and utility-payment services.
```

---

## Phase 2: Important Improvements (Critical/High severity with Medium effort, plus Medium severity items)

These items address significant architectural gaps and bring the codebase to a professional standard.

---

### 2.1 Fix HTTP Status Codes in Error Handling
**Gap Ref:** 2.1 | **Severity:** Critical | **Effort:** Medium

All exceptions incorrectly return HTTP 400. Each exception type needs a proper HTTP status code.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, refactor the 
GlobalExceptionHandler in all four services to return correct HTTP status codes:
- EntityNotFoundException → 404 Not Found
- InsufficientFundsException → 422 Unprocessable Entity  
- UserAlreadyRegisteredException → 409 Conflict
- InvalidEmailException → 400 Bad Request
- InvalidBankingUserException → 404 Not Found
- SimpleBankingGlobalException → 400 Bad Request (keep as-is)
- Exception (catch-all) → 500 Internal Server Error

All error responses must use the structured ErrorResponse format with code and 
message fields. Add @ResponseStatus annotations to each exception class as well.
Ensure the error response format is consistent across all services.
```

---

### 2.2 Add Bean Validation to All Request DTOs
**Gap Ref:** 4.2 | **Severity:** Critical | **Effort:** Medium

No input validation exists on any API endpoint.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation 
to all request DTOs across all services:

1. Add spring-boot-starter-validation dependency to each service's build.gradle
2. Add validation annotations to DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, 
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount, 
     @NotBlank referenceNumber, @NotBlank account
   - User (user-service): @NotBlank @Email email, @NotBlank identification, 
     @NotBlank @Size(min=8) password
3. Add @Valid to all @RequestBody parameters in controllers
4. Add a MethodArgumentNotValidException handler to GlobalExceptionHandler that 
   returns 400 with field-level error details
5. Add corresponding unit tests for validation
```

---

### 2.3 Add Circuit Breakers
**Gap Ref:** 7.1 | **Severity:** High | **Effort:** Medium

No circuit breaker protection exists for inter-service calls.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit 
breakers to all Feign client calls:

1. Add these dependencies to user-service, fund-transfer-service, and 
   utility-payment-service build.gradle files:
   - org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j
2. Enable circuit breaker for Feign:
   spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each FeignClient:
   - BankingCoreRestClientFallback for user-service
   - BankingCoreFeignClientFallback for fund-transfer-service  
   - BankingCoreRestClientFallback for utility-payment-service
4. Configure circuit breaker thresholds:
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - slidingWindowSize: 10
5. Add unit tests for fallback behavior
```

---

### 2.4 Add Unit Tests for Internet Banking Services
**Gap Ref:** 3.1 | **Severity:** High | **Effort:** Medium (per service)

Four services have zero meaningful tests.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests 
for the internet banking services. For each service, create test classes with 
Mockito-based unit tests:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (happy path, duplicate email, invalid 
     identification, email mismatch), readUsers, readUser, updateUser (approve flow)
   - KeycloakUserServiceTest: test CRUD operations with mocked KeycloakManager

2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (happy path, core banking error), 
     readAllTransfers

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (happy path, core banking error), 
     readPayments

Target: minimum 80% line coverage for service classes.
```

---

### 2.5 Fix OpenAPI Dependency and Add Documentation
**Gap Ref:** 5.5 | **Severity:** Medium | **Effort:** Small

Services use the wrong Swagger starter (webflux instead of webmvc).

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, fix OpenAPI configuration:

1. In core-banking-service, user-service, fund-transfer-service, and 
   utility-payment-service build.gradle files, replace:
   springdoc-openapi-starter-webflux-ui:2.1.0
   with:
   springdoc-openapi-starter-webmvc-ui:2.5.0

2. Add type parameters to all raw ResponseEntity returns in controllers 
   (e.g., ResponseEntity<BankAccount> instead of ResponseEntity).

3. Ensure all controllers have proper @Tag and @Operation annotations 
   (most already do, verify completeness).

4. Verify Swagger UI is accessible at /swagger-ui.html for each service.
```

---

### 2.6 Add Pagination Metadata to List Responses
**Gap Ref:** 5.2 | **Severity:** Medium | **Effort:** Medium

List endpoints discard pagination metadata.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a generic 
PagedResponse<T> wrapper class in a shared location and use it for all list 
endpoints:

public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

Update these endpoints to return PagedResponse instead of List:
- GET /api/v1/user (core-banking)
- GET /api/v1/bank-users (user-service)
- GET /api/v1/transfer (fund-transfer-service)
- GET /api/v1/utility-payment (utility-payment-service)
```

---

### 2.7 Add Structured Logging
**Gap Ref:** 6.1, 6.2 | **Severity:** Medium | **Effort:** Small

Logging is inconsistent and unstructured.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, improve logging across 
all services:

1. Add logback-spring.xml to each service's src/main/resources with JSON-formatted 
   output for non-local profiles (use logstash-logback-encoder).
2. Fix the string concatenation bug in FundTransferService:
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To: log.info("Sending fund transfer request {}", request)
3. Fix the typo "utitlity" → "utility" in AccountController.
4. Add @Slf4j to UtilityPaymentController (currently missing).
5. Ensure all controllers log the incoming request at INFO level consistently.
```

---

### 2.8 Configure Health Check Dependencies
**Gap Ref:** 6.4 | **Severity:** Medium | **Effort:** Small

Health endpoints don't check downstream dependencies.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, configure actuator health 
checks for all services via Spring Cloud Config:

1. Enable detailed health information:
   management.endpoint.health.show-details=when-authorized
   management.endpoint.health.show-components=when-authorized

2. For database-connected services (core-banking, user, fund-transfer, 
   utility-payment), ensure DataSourceHealthIndicator is active.

3. For Eureka-connected services, ensure 
   EurekaHealthIndicator is active.

4. Add readiness and liveness probe endpoints:
   management.endpoint.health.probes.enabled=true
   management.health.livenessstate.enabled=true
   management.health.readinessstate.enabled=true
```

---

### 2.9 Add Feign Retry Policies
**Gap Ref:** 7.2 | **Severity:** Medium | **Effort:** Small

No retry configuration exists for Feign calls.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add retry configuration 
for Feign clients in user-service, fund-transfer-service, and 
utility-payment-service:

1. Add a Retryer bean to each service's Feign configuration:
   @Bean
   public Retryer retryer() {
       return new Retryer.Default(100, 1000, 3);
   }
   This retries up to 3 times with 100ms initial interval and 1s max interval.

2. Ensure retries only apply to idempotent operations (GET requests). 
   For POST requests (fund transfers, payments), do NOT retry as they are 
   not idempotent. Use feign.Request.Options or a custom ErrorDecoder to 
   distinguish retryable vs non-retryable errors.
```

---

### 2.10 Add Dependency Vulnerability Scanning
**Gap Ref:** 4.5 | **Severity:** Medium | **Effort:** Small

No automated vulnerability scanning is configured.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices:

1. Add the OWASP Dependency Check Gradle plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.10'
   }

2. Create a GitHub Actions workflow (.github/workflows/dependency-check.yml) 
   that runs dependency-check on all services weekly and on PRs.

3. Add a .github/dependabot.yml configuration to monitor Gradle dependencies 
   for version updates.
```

---

## Phase 3: Polish & Long-Term Improvements

These items improve the system's maturity and prepare it for production scale. They require more effort and can be addressed after the critical and high-severity gaps are resolved.

---

### 3.1 Create Shared Library for Common Code
**Gap Ref:** 1.2 | **Severity:** High | **Effort:** Large

Duplicated code across services should be extracted into a shared library.

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a shared library 
module (banking-common) and extract duplicated code:

1. Create a new Gradle module: banking-common/
2. Move these shared classes into it:
   - exception/GlobalExceptionHandler, ErrorResponse, SimpleBankingGlobalException, 
     EntityNotFoundException
   - model/mapper/BaseMapper
   - model/dto/AuditAware
   - configuration/filter/AppAuthUserFilter, ApiRequestContext, 
     ApiRequestContextHolder
   - configuration/CustomFeignClientConfiguration
3. Create a root settings.gradle that includes all modules.
4. Update each service's build.gradle to depend on banking-common.
5. Remove the duplicated classes from each service.
6. Ensure all existing tests still pass.
```

---

### 3.2 Create Multi-Project Gradle Build
**Gap Ref:** 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, create a multi-project 
Gradle build structure:

1. Create a root build.gradle with common configuration:
   - Shared plugin versions (Spring Boot 3.2.4, dependency-management 1.1.4)
   - Shared dependency versions in ext block
   - Common test configuration (useJUnitPlatform)
   - Common repository configuration (mavenCentral)
2. Create a root settings.gradle that includes all 6 service modules and 
   the banking-common module.
3. Simplify each service's build.gradle to only declare service-specific 
   dependencies.
4. Add a root gradlew wrapper.
5. Verify all services build successfully from the root: ./gradlew build
```

---

### 3.3 Add Integration Tests with Testcontainers
**Gap Ref:** 3.2 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests 
using Testcontainers for the core-banking-service:

1. Add testcontainers and mysql-testcontainer dependencies to build.gradle.
2. Create an AbstractIntegrationTest base class that starts a MySQL container 
   and configures Spring datasource properties.
3. Write integration tests for:
   - AccountController: verify GET endpoints return correct data from the database
   - TransactionController: verify fund transfer end-to-end with real DB
   - Flyway migrations: verify all migrations apply successfully
4. Use @SpringBootTest with a test profile that disables Eureka and Config Server.
5. Aim for tests that validate the full request→controller→service→repository→DB 
   path.
```

---

### 3.4 Add Contract Tests Between Services
**Gap Ref:** 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract 
tests between the internet banking services and core-banking-service:

1. Add spring-cloud-contract-verifier to core-banking-service (producer side).
2. Define contracts for:
   - GET /api/v1/account/bank-account/{account_number}
   - GET /api/v1/user/{identification}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Generate stubs JAR from core-banking-service contracts.
4. Add spring-cloud-contract-stub-runner to consumer services 
   (user-service, fund-transfer-service, utility-payment-service).
5. Write consumer contract tests that verify Feign clients work against 
   the generated stubs.
```

---

### 3.5 Implement Transaction Compensation (Saga Pattern)
**Gap Ref:** 7.5 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, implement a saga pattern 
for fund transfers and utility payments to handle partial failures:

Option A (Choreography-based with RabbitMQ):
1. Add spring-boot-starter-amqp to fund-transfer-service and 
   utility-payment-service.
2. Instead of synchronous Feign calls, publish a TransferRequestedEvent to 
   RabbitMQ.
3. Core-banking-service consumes the event, processes the transaction, and 
   publishes TransferCompletedEvent or TransferFailedEvent.
4. The originating service listens for completion/failure events and updates 
   its local record accordingly.
5. Add a reconciliation job that detects PENDING records older than X minutes 
   and flags them for manual review.

Option B (Simpler — compensating transaction):
1. Wrap the Feign call in a try-catch.
2. If the local update fails after a successful Feign call, call a compensating 
   endpoint on core-banking-service to reverse the transaction.
3. Add a /api/v1/transaction/reverse/{transactionId} endpoint to 
   core-banking-service.
```

---

### 3.6 Add Rate Limiting to API Gateway
**Gap Ref:** 7.6 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-api-gateway, 
add rate limiting using Spring Cloud Gateway's built-in RequestRateLimiter:

1. Add spring-boot-starter-data-redis-reactive dependency.
2. Add Redis to docker-compose.yml.
3. Configure RequestRateLimiter filter on gateway routes:
   - Default: 10 requests/second per user
   - Fund transfer: 5 requests/second per user
   - Registration: 3 requests/minute per IP
4. Configure a KeyResolver bean that extracts the user identity from the JWT.
5. Add appropriate 429 Too Many Requests error response formatting.
```

---

### 3.7 Add Metrics and Monitoring
**Gap Ref:** 6.5 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics 
and Grafana dashboards:

1. Enable Prometheus endpoint in all services:
   management.endpoints.web.exposure.include=health,info,prometheus
2. Add micrometer-registry-prometheus dependency to each service.
3. Add a Prometheus container to docker-compose.yml with scrape configs 
   for all services.
4. Add a Grafana container with pre-built dashboards for:
   - JVM metrics (heap, GC, threads)
   - HTTP request rates and latencies per endpoint
   - Database connection pool metrics
   - Feign client call metrics
5. Create custom metrics for business KPIs:
   - Fund transfers per minute
   - Utility payments per minute
   - Failed transaction rate
```

---

### 3.8 Add Filtering and Search to List Endpoints
**Gap Ref:** 5.6 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, add filtering capabilities 
to list endpoints:

1. Fund transfers (GET /api/v1/transfer):
   - Filter by: fromAccount, toAccount, status, dateRange (createdAfter/createdBefore)
   - Use Spring Data JPA Specifications for dynamic query building

2. Utility payments (GET /api/v1/utility-payment):
   - Filter by: providerId, status, account, dateRange

3. Users (GET /api/v1/bank-users):
   - Filter by: status, email (partial match)

4. Core banking accounts (GET /api/v1/user):
   - Add a search endpoint: GET /api/v1/user/search?email=&name=

Use @RequestParam with Optional types for all filter parameters.
```

---

### 3.9 Standardize Package Structure
**Gap Ref:** 1.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In ts-java-spring-boot-internet-banking-microservices, standardize the package 
structure across all services to follow this convention:

com.javatodev.finance/
├── configuration/       # Spring @Configuration classes
│   ├── audit/
│   ├── feign/
│   ├── filter/
│   └── security/
├── controller/          # REST controllers
├── exception/           # Exception classes and handlers
├── model/
│   ├── dto/             # Data Transfer Objects
│   │   ├── request/
│   │   └── response/
│   ├── entity/          # JPA entities
│   └── mapper/          # Entity-DTO mappers
├── repository/          # Spring Data repositories
└── service/
    └── rest/            # Feign clients and external API calls

Move classes as needed in utility-payment-service and user-service to match 
this structure. Update all imports accordingly. Ensure all tests still pass.
```

---

## Implementation Priority Matrix

```
                    ║ Small Effort │ Medium Effort │ Large Effort
════════════════════╬══════════════╪═══════════════╪══════════════
Critical Severity   ║ 1.1, 1.2     │ 2.1, 2.2      │
────────────────────╫──────────────┼───────────────┼──────────────
High Severity       ║ 1.3–1.6      │ 2.3, 2.4      │ 3.1, 3.3, 3.5
────────────────────╫──────────────┼───────────────┼──────────────
Medium Severity     ║ 2.5,2.7–2.10 │ 2.6, 3.6      │ 3.2, 3.4
────────────────────╫──────────────┼───────────────┼──────────────
Low Severity        ║ 3.9          │ 3.7, 3.8      │
```

---

## Estimated Total Effort

| Phase | Items | Estimated Duration |
|---|---|---|
| Phase 1 (Quick Wins) | 6 items | 3–5 days |
| Phase 2 (Important) | 10 items | 2–3 weeks |
| Phase 3 (Polish) | 9 items | 4–6 weeks |
| **Total** | **25 items** | **7–10 weeks** |

---

## Recommended Execution Order

1. **Week 1**: Phase 1 items 1.1–1.6 (security fixes, error handling, timeouts)
2. **Week 2**: Phase 2 items 2.1–2.2 (HTTP status codes, input validation)
3. **Week 3**: Phase 2 items 2.3–2.4 (circuit breakers, unit tests)
4. **Week 4**: Phase 2 items 2.5–2.10 (OpenAPI, logging, health checks, retries)
5. **Weeks 5–6**: Phase 3 items 3.1–3.2 (shared library, multi-project build)
6. **Weeks 7–8**: Phase 3 items 3.3–3.5 (integration tests, contracts, saga)
7. **Weeks 9–10**: Phase 3 items 3.6–3.9 (rate limiting, metrics, filtering, packages)
