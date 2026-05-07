# Remediation Roadmap

> Phased plan to close the engineering gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md).

---

## Phase Overview

| Phase | Focus | Timeline | Items |
|-------|-------|----------|-------|
| **Phase 1** | Quick Wins | 1–2 weeks | High-impact, low-effort fixes that improve safety and correctness immediately |
| **Phase 2** | Important | 3–6 weeks | Structural improvements to resilience, testing, and security |
| **Phase 3** | Polish | 6–10 weeks | Code organization, observability, and API refinements |

---

## Phase 1: Quick Wins

High-severity items that can each be completed in under a day.

### 1.1 Fix HTTP Status Codes in Exception Handlers

**Gap Refs:** 2.1, 5.1
**Severity:** High | **Effort:** Small

Update all four `GlobalExceptionHandler` classes to return semantically correct HTTP status codes:
- `EntityNotFoundException` → `404 Not Found`
- `InsufficientFundsException` → `422 Unprocessable Entity`
- `InvalidEmailException`, `InvalidBankingUserException` → `400 Bad Request`
- `UserAlreadyRegisteredException` → `409 Conflict`
- Generic `Exception` catch-all → `500 Internal Server Error`
- `POST` endpoints returning new resources → `201 Created`

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update all
GlobalExceptionHandler classes across all 4 services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to return correct HTTP status codes:
- EntityNotFoundException → 404
- InsufficientFundsException → 422
- UserAlreadyRegisteredException → 409
- InvalidEmailException, InvalidBankingUserException → 400
- Generic Exception catch-all → 500
Also update all POST controller methods to return ResponseEntity with status 201
(HttpStatus.CREATED) instead of 200. Run the existing tests to make sure nothing breaks.
```

### 1.2 Stop Leaking Exception Details

**Gap Refs:** 2.2, 2.3
**Severity:** High | **Effort:** Small

Replace the generic exception handler's response body with a structured `ErrorResponse` instead of raw `Exception.toString()`.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update the generic
Exception handler in all 4 GlobalExceptionHandler classes. Replace the plain string
response "Exception occur inside API " + e with a structured ErrorResponse object
containing code="INTERNAL_ERROR" and message="An unexpected error occurred". Add
log.error("Unhandled exception", e) to capture the full stack trace server-side.
Ensure all services use the same ErrorResponse builder pattern for consistency.
```

### 1.3 Add Bean Validation to Request DTOs

**Gap Refs:** 2.4, 4.2
**Severity:** High | **Effort:** Medium

Add `spring-boot-starter-validation` dependency and annotate all request DTOs with Bean Validation constraints.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add input validation:
1. Add spring-boot-starter-validation to build.gradle in core-banking-service,
   internet-banking-user-service, internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service.
2. Add validation annotations to all request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount and toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
     @NotBlank on referenceNumber and account
   - User (registration): @NotBlank @Email on email, @NotBlank on password and identification
   - UserUpdateRequest: @NotNull on status
3. Add @Valid annotation to all @RequestBody parameters in controllers.
4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that
   returns 400 with field-level error details.
5. Run existing tests to confirm nothing breaks.
```

### 1.4 Add Feign Timeout Configuration

**Gap Refs:** 7.3
**Severity:** High | **Effort:** Small

Configure explicit connect and read timeouts for all Feign clients.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Feign timeout
configuration to internet-banking-user-service, internet-banking-fund-transfer-service,
and internet-banking-utility-payment-service. In each service's application.yml (or via
Spring Cloud Config), add:
  spring.cloud.openfeign.client.config.default.connect-timeout: 5000
  spring.cloud.openfeign.client.config.default.read-timeout: 10000
This ensures Feign calls fail fast (5s connect, 10s read) instead of using unbounded defaults.
```

### 1.5 Add Retry Policies to Feign Clients

**Gap Refs:** 7.2
**Severity:** High | **Effort:** Small

Add Spring Retry for transient Feign failures.

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add retry support to
all Feign clients:
1. Add spring-retry and spring-boot-starter-aop dependencies to build.gradle in
   internet-banking-user-service, internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service.
2. Enable retry via application.yml:
   spring.cloud.openfeign.client.config.default.retryer: feign.Retryer.Default
3. Configure max 3 retries with 100ms initial interval and 1s max interval.
4. Ensure POST calls to /api/v1/transaction/* are NOT retried (they are not idempotent)
   by using @Retryable only on GET operations or by configuring Feign method-level retry.
```

### 1.6 Add Dependency Vulnerability Scanning

**Gap Refs:** 4.6
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add the OWASP
dependency-check Gradle plugin to all services:
1. In each service's build.gradle, add: plugins { id 'org.owasp.dependencycheck' version '9.0.9' }
2. Configure it to fail the build on CVSS score >= 7.
3. Run ./gradlew dependencyCheckAnalyze in core-banking-service and report any
   critical vulnerabilities found.
```

### 1.7 Configure Trace Sampling

**Gap Refs:** 6.4
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, set trace sampling to
100% for all services. Add the following to each service's application.yml (or via
Spring Cloud Config):
  management.tracing.sampling.probability: 1.0
This ensures all requests are traced for full observability in a banking context.
```

### 1.8 Fix Inconsistent Logging

**Gap Refs:** 6.5
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, fix logging issues:
1. In FundTransferService.java, change:
   log.info("Sending fund transfer request {}" + request.toString())
   to: log.info("Sending fund transfer request {}", request)
2. Remove all .toString() calls inside log.info() {} placeholders across all services
   (SLF4J calls toString() automatically).
3. Standardize log message format: "ServiceName - action: {}" pattern.
```

---

## Phase 2: Important

Structural improvements requiring more effort but essential for production readiness.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap Refs:** 7.1, 7.4
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit
breakers to all Feign clients:
1. Add spring-cloud-starter-circuitbreaker-resilience4j to build.gradle in
   internet-banking-user-service, internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service.
2. Enable Feign circuit breaker: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreRestClientFallback in User Service
   - BankingCoreFeignClientFallback in Fund Transfer Service
   - BankingCoreRestClientFallback in Utility Payment Service
4. Configure circuit breaker: failure-rate-threshold=50, wait-duration-in-open-state=30s,
   sliding-window-size=10.
5. Fallbacks should return meaningful error responses (not exceptions) so the caller
   can handle gracefully.
```

### 2.2 Add Error Handling and Compensation to Fund Transfer Flow

**Gap Refs:** 2.5, 7.6
**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add proper error handling
to FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment():
1. Wrap the Feign call in try/catch:
   - On success: set status=SUCCESS with transactionReference
   - On FeignException: set status=FAILED, log the error, return error response
   - On any Exception: set status=FAILED, log the error, rethrow wrapped in service exception
2. Add a @Scheduled method that runs every 5 minutes to find records with status=PENDING
   older than 10 minutes and mark them as FAILED (stale transfer cleanup).
3. Add an idempotency key field to FundTransferEntity and UtilityPaymentEntity so
   retried requests don't create duplicate transfers.
4. Write unit tests for the error handling paths.
```

### 2.3 Externalize Credentials from Source Control

**Gap Refs:** 4.1
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, remove hard-coded
credentials from version control:
1. Create a docker-compose/.env.example file with placeholder values for all credentials:
   MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KC_ADMIN_PASSWORD, KC_DB_PASSWORD, etc.
2. Update docker-compose.yml to reference ${VARIABLE} syntax for all passwords.
3. Update mysql/privileges.sql to use a script that reads from environment variables.
4. Add .env to .gitignore.
5. Create a docker-compose/.env file locally with actual values (not committed).
6. Update the README with instructions for setting up the .env file.
7. Remove the Keycloak client secret from the Postman environment JSON or replace
   with a placeholder.
```

### 2.4 Add Unit Tests for User, Fund Transfer, and Utility Payment Services

**Gap Refs:** 3.1
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive unit
tests for the three orchestration services:

internet-banking-user-service:
- UserServiceTest: test createUser (success, duplicate email, invalid NIC, email mismatch),
  updateUser (approve, disable), readUsers, readUser
- KeycloakUserServiceTest: test createUser, updateUser, readUser, readUserByEmail

internet-banking-fund-transfer-service:
- FundTransferServiceTest: test fundTransfer (success, Feign failure), readAllTransfers

internet-banking-utility-payment-service:
- UtilityPaymentServiceTest: test utilPayment (success, Feign failure), readPayments

Use Mockito to mock Feign clients and repositories. Follow the same test patterns
established in core-banking-service/src/test/.
```

### 2.5 Add Rate Limiting to API Gateway

**Gap Refs:** 4.4
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting to
the API Gateway:
1. Add spring-boot-starter-data-redis-reactive dependency to internet-banking-api-gateway.
2. Add a Redis container to docker-compose.yml.
3. Configure RequestRateLimiter filter on gateway routes via Spring Cloud Config:
   - Registration endpoint: 5 requests/minute per IP
   - Authenticated endpoints: 100 requests/minute per user
4. Add a KeyResolver bean that resolves rate limit keys from JWT subject claim for
   authenticated requests and from IP for anonymous requests.
```

### 2.6 Add Custom Health Indicators

**Gap Refs:** 6.1
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health
indicators:
1. In internet-banking-user-service: add a KeycloakHealthIndicator that pings Keycloak's
   /health endpoint.
2. In internet-banking-fund-transfer-service and internet-banking-utility-payment-service:
   add a CoreBankingHealthIndicator that calls core-banking-service's /actuator/health
   via Feign.
3. Enable detailed health info: management.endpoint.health.show-details=always
4. Expose health and info endpoints: management.endpoints.web.exposure.include=health,info
```

### 2.7 Add Structured (JSON) Logging

**Gap Refs:** 6.2
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, configure structured
JSON logging for all services:
1. Add logstash-logback-encoder dependency to all services' build.gradle.
2. Create a logback-spring.xml in each service's src/main/resources/ with:
   - Console appender using LogstashEncoder for docker/production profiles
   - Console appender using PatternLayout for local development (default profile)
3. Include trace ID and span ID in log output for correlation with Zipkin.
4. Verify logs are valid JSON by running a service and checking output.
```

### 2.8 Expose Prometheus Metrics

**Gap Refs:** 6.3
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, enable Prometheus metrics:
1. Add micrometer-registry-prometheus dependency to all services' build.gradle.
2. Expose the prometheus actuator endpoint:
   management.endpoints.web.exposure.include=health,info,prometheus
3. Add custom business metrics using MeterRegistry:
   - core-banking-service: counter for fund_transfers_total, utility_payments_total
   - internet-banking-user-service: counter for user_registrations_total
4. Optionally add a Prometheus container and Grafana to docker-compose.yml for visualization.
```

---

## Phase 3: Polish

Code organization and API refinements for long-term maintainability.

### 3.1 Extract Shared Library

**Gap Refs:** 1.1, 1.2, 1.3
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a shared library
module called banking-common:
1. Create a new directory banking-common/ with build.gradle configured as a plain
   Java library (not a Spring Boot application).
2. Move the following shared classes into it:
   - exception/: SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler,
     GlobalErrorCode, EntityNotFoundException
   - audit/: AuditAware, AuditConfig, AuditorAwareConfig
   - filter/: AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - feign/: CustomFeignClientConfiguration, CustomFeignErrorDecoder
3. Add banking-common as a dependency in all 4 application services.
4. Delete the duplicated classes from each service.
5. Run all existing tests to confirm nothing breaks.
```

### 3.2 Convert to Gradle Multi-Project Build

**Gap Refs:** 1.4
**Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, convert to a Gradle
multi-project build:
1. Create a root-level settings.gradle that includes all 7 services and the new
   banking-common module.
2. Create a root-level build.gradle with shared configuration (Java 21, Spring Boot
   version, dependency management, common dependencies).
3. Simplify each service's build.gradle to only declare service-specific dependencies.
4. Remove individual gradlew/gradlew.bat and gradle/wrapper/ from each service (keep
   only the root-level wrapper).
5. Verify all services build with: ./gradlew clean build from the root.
```

### 3.3 Add Pagination Metadata to API Responses

**Gap Refs:** 5.3
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, update all paginated
endpoints to return Spring's Page object instead of raw List:
1. In core-banking-service UserController.readUsers(): return Page<User> instead of List<User>
2. In internet-banking-user-service UserController.readUsers(): return Page<User>
3. In internet-banking-fund-transfer-service FundTransferController.readFundTransfers():
   return Page<FundTransfer>
4. In internet-banking-utility-payment-service UtilityPaymentController.readPayments():
   return Page<UtilityPayment>
5. Update each service method to pass through the Page wrapper rather than calling
   .getContent().
The response will automatically include totalElements, totalPages, number, size, etc.
```

### 3.4 Add Typed ResponseEntity Generics

**Gap Refs:** 5.6
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add type parameters to
all ResponseEntity return types in controllers:
- ResponseEntity<User> instead of ResponseEntity
- ResponseEntity<FundTransferResponse> instead of ResponseEntity
- ResponseEntity<List<FundTransfer>> instead of ResponseEntity
- etc.
This improves OpenAPI documentation generation and compile-time type safety. Update all
controller methods across all 4 application services.
```

### 3.5 Enhance OpenAPI Documentation

**Gap Refs:** 5.5
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, enhance OpenAPI/Swagger
documentation:
1. Add an @OpenAPIDefinition annotation to each service's main application class with
   title, version, and description.
2. Add @Schema annotations with example values to all request/response DTOs.
3. Add @ApiResponse annotations to controller methods documenting success and error responses.
4. Verify the Swagger UI is accessible at /swagger-ui.html for each service.
```

### 3.6 Register Mappers as Spring Beans

**Gap Refs:** 1.5
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, convert all mapper
classes to Spring-managed beans:
1. Add @Component to BankAccountMapper, UserMapper, UtilityAccountMapper (core-banking),
   UserMapper (user-service), FundTransferMapper (fund-transfer), UtilityPaymentMapper
   (utility-payment).
2. Replace field instantiation (e.g., private BankAccountMapper mapper = new BankAccountMapper())
   with constructor injection via @RequiredArgsConstructor.
3. Update any tests that create mappers manually.
```

### 3.7 Add Integration Tests with Testcontainers

**Gap Refs:** 3.2
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests
using Testcontainers:
1. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies to
   core-banking-service's build.gradle (testImplementation).
2. Create an AbstractIntegrationTest base class that starts a MySQL container and
   configures Spring DataSource to point to it.
3. Create AccountControllerIT: test GET /api/v1/account/bank-account/{number} returns
   correct account data after Flyway migrations run.
4. Create TransactionControllerIT: test POST /api/v1/transaction/fund-transfer end-to-end
   (with real DB and Flyway migrations).
5. Enable Flyway in test config for integration tests.
```

### 3.8 Add Contract Tests

**Gap Refs:** 3.3
**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract
tests for the Feign client interfaces:
1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider side).
2. Write contract DSL files for:
   - GET /api/v1/user/{identification}
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Add spring-cloud-starter-contract-stub-runner to the consumer services (User, Fund
   Transfer, Utility Payment).
4. Write consumer-side tests that verify Feign clients work against the contract stubs.
```

### 3.9 Configure Keycloak for Production Mode

**Gap Refs:** 4.5
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, create a production-ready
Keycloak configuration:
1. Create docker-compose/docker-compose-prod.yml that runs Keycloak with:
   command: ["start", "--optimized"]
   (instead of start-dev)
2. Add TLS certificate configuration for Keycloak (self-signed for local dev).
3. Update the README with instructions for production Keycloak deployment.
4. Keep the existing docker-compose.yml with start-dev for local development.
```

### 3.10 Add HTTPS/TLS to API Gateway

**Gap Refs:** 4.7
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
In the ts-java-spring-boot-internet-banking-microservices repo, add TLS support to the
API Gateway:
1. Generate a self-signed certificate for local development using keytool.
2. Add SSL configuration to the API Gateway's application.yml:
   server.ssl.key-store, server.ssl.key-store-password, server.ssl.key-alias
3. Update the API Gateway Dockerfile to include the keystore.
4. Update docker-compose.yml to expose port 8443 for HTTPS.
5. Update the README and Postman collection to reference HTTPS URLs.
```

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │  Phase 1          │  Phase 2          │
    │  (Do First)       │  (Do Next)        │
    │                   │                   │
    │  • HTTP status    │  • Circuit breakers│
    │  • Leak fix       │  • Compensation   │
    │  • Validation     │  • Credentials    │
    │  • Timeouts       │  • Unit tests     │
    │  • Retries        │  • Rate limiting  │
────┼───────────────────┼───────────────────┤── EFFORT
    │                   │                   │       HIGH
    │  Phase 1          │  Phase 3          │
    │  (Easy Wins)      │  (Do Later)       │
    │                   │                   │
    │  • Vuln scanning  │  • Shared library │
    │  • Trace sampling │  • Multi-project  │
    │  • Log fixes      │  • Integration tests│
    │                   │  • Contract tests │
    │                   │  • TLS/HTTPS      │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```

---

## Progress Tracking

Use this checklist to track remediation progress:

- [ ] **Phase 1.1** — Fix HTTP status codes
- [ ] **Phase 1.2** — Stop leaking exception details
- [ ] **Phase 1.3** — Add bean validation
- [ ] **Phase 1.4** — Configure Feign timeouts
- [ ] **Phase 1.5** — Add retry policies
- [ ] **Phase 1.6** — Add vulnerability scanning
- [ ] **Phase 1.7** — Configure trace sampling
- [ ] **Phase 1.8** — Fix logging inconsistencies
- [ ] **Phase 2.1** — Add circuit breakers
- [ ] **Phase 2.2** — Add error handling and compensation
- [ ] **Phase 2.3** — Externalize credentials
- [ ] **Phase 2.4** — Add unit tests
- [ ] **Phase 2.5** — Add rate limiting
- [ ] **Phase 2.6** — Add custom health indicators
- [ ] **Phase 2.7** — Add structured logging
- [ ] **Phase 2.8** — Expose Prometheus metrics
- [ ] **Phase 3.1** — Extract shared library
- [ ] **Phase 3.2** — Convert to multi-project build
- [ ] **Phase 3.3** — Add pagination metadata
- [ ] **Phase 3.4** — Add typed ResponseEntity
- [ ] **Phase 3.5** — Enhance OpenAPI docs
- [ ] **Phase 3.6** — Register mappers as beans
- [ ] **Phase 3.7** — Add integration tests
- [ ] **Phase 3.8** — Add contract tests
- [ ] **Phase 3.9** — Production Keycloak config
- [ ] **Phase 3.10** — Add HTTPS/TLS
