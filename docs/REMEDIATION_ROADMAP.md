# Internet Banking Microservices — Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases:

- **Phase 1 (Quick Wins):** High-severity / low-effort fixes that immediately improve safety and quality.
- **Phase 2 (Important):** High-severity / medium-effort improvements that require design decisions.
- **Phase 3 (Polish):** Medium/low-severity improvements that enhance maintainability and developer experience.

---

## Phase 1: Quick Wins (1–2 weeks)

These are high-impact changes with small effort — address immediately.

### 1.1 Fix Balance Calculation Bug (GAP-7.6)

**Priority:** P0 — Financial correctness bug

**Description:** Fix the double-subtract/add bug in `TransactionService.internalFundTransfer()` and `utilPayment()` where `availableBalance` is incorrectly computed after `actualBalance` was already modified.

**Devin Prompt:**
```
In core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java, fix the balance calculation bug:

1. In internalFundTransfer():
   - Line setting fromBankAccountEntity.setAvailableBalance should use the ORIGINAL actualBalance minus amount, not the already-reduced actualBalance.
   - Line setting toBankAccountEntity.setAvailableBalance should use the ORIGINAL actualBalance plus amount, not the already-increased actualBalance.
   
2. In utilPayment():
   - Same issue: availableBalance is set from already-modified actualBalance.

Fix: Set availableBalance BEFORE modifying actualBalance, or compute both from the original value. Add unit tests to verify correct final balances for both accounts after transfer.
```

---

### 1.2 Fix HTTP Status Codes in Error Handling (GAP-2.1, GAP-2.2)

**Priority:** P0 — API correctness

**Description:** Update `GlobalExceptionHandler` in all services to return appropriate HTTP status codes and stop leaking stack traces.

**Devin Prompt:**
```
Update GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service):

1. Map EntityNotFoundException to HTTP 404 Not Found.
2. Map InsufficientFundsException to HTTP 422 Unprocessable Entity.
3. Map UserAlreadyRegisteredException to HTTP 409 Conflict.
4. Map InvalidEmailException, InvalidBankingUserException to HTTP 400 Bad Request.
5. Map generic Exception to HTTP 500 Internal Server Error with a generic message (do NOT include the exception details in the response body).
6. Always return a structured ErrorResponse object with {code, message} — never return raw strings.
7. Add @Slf4j and log the full exception at ERROR level for the generic handler (for debugging), but do not expose to clients.
```

---

### 1.3 Add Request Body Validation (GAP-2.3, GAP-4.4)

**Priority:** P1 — Data integrity

**Description:** Add Jakarta Bean Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Devin Prompt:**
```
Add input validation across all services:

1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service.

2. Add validation constraints to request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount, @NotNull @Positive amount
   - UtilityPaymentRequest: @NotBlank account, @NotNull providerId, @NotNull @Positive amount, @NotBlank referenceNumber
   - User (registration): @NotBlank @Email email, @NotBlank password, @NotBlank identification

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler that returns HTTP 400 with field-level error details.
```

---

### 1.4 Remove Sensitive Data from Logs (GAP-6.4)

**Priority:** P1 — Security

**Description:** Stop logging full request objects that may contain passwords or financial data.

**Devin Prompt:**
```
Audit all log statements across all services and remove sensitive data exposure:

1. In internet-banking-user-service UserController/UserService: Remove logging of full User object (contains password). Log only email or userId.
2. In fund-transfer-service FundTransferController/FundTransferService: Log only fromAccount and amount, not full request.
3. In utility-payment-service: Log only account and amount.
4. In core-banking-service TransactionController: Log only transaction type and a masked account number.
5. Consider adding a @ToString.Exclude on password fields in DTOs.
```

---

### 1.5 Fix Feign Timeout Configuration (GAP-7.3)

**Priority:** P1 — Availability

**Description:** Add explicit timeout configuration for all Feign clients.

**Devin Prompt:**
```
Add Feign timeout configuration to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service:

1. In each service's application.yml (or bootstrap.yml), add:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connectTimeout: 5000
               readTimeout: 10000

2. Also add connection pool configuration via OkHttp or Apache HttpClient for production use.
```

---

### 1.6 Fix OpenAPI Dependency (GAP-5.1)

**Priority:** P2 — Developer experience

**Description:** Replace incorrect webflux OpenAPI dependency with webmvc.

**Devin Prompt:**
```
In build.gradle of core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service:

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'

With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'

The webflux variant is for reactive applications; these services use Spring MVC (servlet).
```

---

### 1.7 Add Typed ResponseEntity and Pagination Metadata (GAP-5.2, GAP-5.4)

**Priority:** P2 — API quality

**Devin Prompt:**
```
Across all controllers in all services:

1. Add type parameters to all ResponseEntity return types (e.g., ResponseEntity<BankAccount> instead of raw ResponseEntity).

2. For paginated endpoints (those accepting Pageable), return a Page wrapper instead of List:
   - Change service methods to return Page<T> from the repository directly.
   - Return ResponseEntity<Page<T>> from controllers.
   - This automatically includes totalElements, totalPages, size, number in the JSON response.
```

---

### 1.8 Fix Thread-Unsafe Keycloak Singleton (GAP-4.5)

**Priority:** P2 — Correctness

**Devin Prompt:**
```
In internet-banking-user-service KeycloakProperties.java:

Replace the thread-unsafe lazy singleton with a proper Spring @Bean:

1. Remove the static keycloakInstance field and the getInstance() method.
2. Create a @Configuration class KeycloakConfig that produces a @Bean Keycloak instance using KeycloakBuilder.
3. Inject the Keycloak bean into KeycloakManager instead of calling keycloakProperties.getInstance().
```

---

### 1.9 Add Retry Configuration (GAP-7.2)

**Priority:** P2 — Availability

**Devin Prompt:**
```
Add Spring Retry support to Feign clients in fund-transfer-service, user-service, and utility-payment-service:

1. Add dependency: implementation 'org.springframework.retry:spring-retry'
2. Add dependency: implementation 'org.springframework:spring-aspects'
3. Add @EnableRetry to the main application class.
4. Configure Feign retryer in application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               retryer: feign.Retryer.Default

Or use Resilience4j retry (preferred if adding circuit breakers in Phase 2).
```

---

## Phase 2: Important (2–4 weeks)

These require more design thought and cross-cutting changes.

### 2.1 Add Circuit Breakers (GAP-7.1, GAP-7.4)

**Priority:** P1 — System reliability

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies to fund-transfer-service, user-service, utility-payment-service build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Create fallback classes for each Feign client that return appropriate error responses.
4. Configure circuit breaker parameters (sliding window, failure threshold, wait duration).
5. Add a /actuator/circuitbreakers endpoint for monitoring.
```

---

### 2.2 Add Idempotency to Write Operations (GAP-5.5)

**Priority:** P0 — Financial safety

**Devin Prompt:**
```
Implement idempotency for fund transfer and utility payment endpoints:

1. Add an 'Idempotency-Key' header requirement to POST /api/v1/transfer and POST /api/v1/utility-payment.
2. Create an idempotency_key table (or add a unique column) in each service's schema.
3. Before processing, check if the idempotency key already exists:
   - If yes, return the cached response.
   - If no, process the request and store the key + response.
4. Add a unique constraint on the idempotency key column.
5. Return HTTP 409 Conflict if a duplicate key is detected with a different request body.
6. Document the idempotency mechanism in the API docs.
```

---

### 2.3 Secure Downstream Services (GAP-4.1)

**Priority:** P0 — Security

**Devin Prompt:**
```
Add service-to-service authentication for downstream services:

Option A (Recommended - Internal JWT propagation):
1. Configure core-banking-service, fund-transfer-service, user-service, and utility-payment-service as OAuth2 resource servers.
2. Have the API Gateway forward the original JWT token to downstream services.
3. Add Feign interceptor to propagate the Authorization header.
4. Remove reliance on the X-Auth-Id header (or validate it matches the JWT subject).

Option B (Service mesh / mTLS):
1. Add mutual TLS between services in the Docker network.
2. Each service verifies the client certificate of the caller.

Implement Option A first as it requires the least infrastructure change.
```

---

### 2.4 Externalize Secrets (GAP-4.2, GAP-4.3)

**Priority:** P1 — Security

**Devin Prompt:**
```
Remove hardcoded credentials from source control:

1. In docker-compose.yml and docker-compose-support-apps.yml:
   - Replace all hardcoded passwords with environment variable references: ${MYSQL_ROOT_PASSWORD}, ${KC_DB_PASSWORD}, etc.
   - Create a .env.example file with placeholder values.
   - Add .env to .gitignore.

2. In privileges.sql:
   - Replace hardcoded password with a variable or make it configurable.

3. Move Keycloak client secret to a non-public configuration source:
   - Use environment variables or a secrets manager reference in Spring Cloud Config.

4. Document the required environment variables in the README.
```

---

### 2.5 Create Shared Library Module (GAP-1.1)

**Priority:** P1 — Maintainability

**Devin Prompt:**
```
Create a shared library module (banking-common) containing duplicated code:

1. Create a new Gradle subproject 'banking-common' with:
   - BaseMapper interface
   - AuditAware base entity class
   - AuditorAwareConfig
   - GlobalExceptionHandler (with proper HTTP status mapping)
   - ErrorResponse DTO
   - SimpleBankingGlobalException
   - AppAuthUserFilter + ApiRequestContext + ApiRequestContextHolder
   - CustomFeignClientConfiguration

2. Publish as a local Maven artifact or use Gradle composite build.
3. Update all service build.gradle files to depend on banking-common.
4. Remove duplicated classes from individual services.
5. Set up a root settings.gradle with includeBuild or include for all subprojects.
```

---

### 2.6 Add Unit Tests for All Services (GAP-3.1)

**Priority:** P1 — Quality

**Devin Prompt:**
```
Add comprehensive unit tests for:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test successful transfer, test Feign client failure handling, test entity mapping.

2. internet-banking-user-service:
   - UserServiceTest: test createUser happy path, test duplicate email, test invalid NIC, test updateUser status transitions.
   - KeycloakUserServiceTest: mock KeycloakManager and test CRUD operations.

3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test successful payment, test Feign failure, test entity mapping.

Use Mockito for mocking dependencies. Target >80% line coverage for service layer.
```

---

### 2.7 Add Transaction Compensation (GAP-7.5)

**Priority:** P0 — Data consistency

**Devin Prompt:**
```
Implement compensation logic for distributed transactions:

1. In FundTransferService.fundTransfer():
   - Wrap the Feign call in a try-catch.
   - If the Feign call fails AFTER saving the PENDING record, update status to FAILED.
   - If the Feign call succeeds but the final save fails, log a critical alert (manual reconciliation needed).
   - Consider implementing a simple Saga pattern with status tracking.

2. In UtilityPaymentService.utilPayment():
   - Same pattern: catch Feign failures and mark local record as FAILED.
   
3. Add a scheduled job to reconcile PENDING records older than X minutes.
4. Add alerting/metrics for failed transactions that need manual review.
```

---

## Phase 3: Polish (4–8 weeks)

### 3.1 Add Structured Logging (GAP-6.1)

**Devin Prompt:**
```
Configure structured JSON logging across all services:

1. Add logback-spring.xml to each service's src/main/resources with:
   - JSON encoder (net.logstash.logback:logstash-logback-encoder)
   - Include traceId, spanId, serviceName in every log line.
   - Profile-specific: use JSON in docker/prod, use console pattern in dev.

2. Add dependency: implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

3. Ensure MDC context propagation for X-Auth-Id across all services.
```

---

### 3.2 Configure Health Checks (GAP-6.2)

**Devin Prompt:**
```
Configure Spring Boot Actuator health endpoints for all services:

1. In application.yml for each service:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus
     endpoint:
       health:
         show-details: when-authorized
         probes:
           enabled: true
     health:
       db:
         enabled: true

2. Add health groups for Kubernetes probes:
   management.endpoint.health.group.liveness.include=ping
   management.endpoint.health.group.readiness.include=db,diskSpace

3. Update Docker Compose with healthcheck directives for each service.
```

---

### 3.3 Add Custom Business Metrics (GAP-6.3)

**Devin Prompt:**
```
Add Micrometer custom metrics to all services:

1. In core-banking-service TransactionService:
   - Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT, status=SUCCESS|FAILED)
   - Timer: banking.transactions.duration
   - Gauge: banking.accounts.balance.total

2. In fund-transfer-service:
   - Counter: fund_transfer.requests.total (tags: status=SUCCESS|FAILED)
   - Timer: fund_transfer.processing.duration

3. In utility-payment-service:
   - Counter: utility_payment.requests.total (tags: status=SUCCESS|FAILED)

4. Add Prometheus endpoint: management.endpoints.web.exposure.include=prometheus
5. Inject MeterRegistry and use Counter.builder() / Timer.builder() patterns.
```

---

### 3.4 Add Integration Tests with Testcontainers (GAP-3.2)

**Devin Prompt:**
```
Add integration tests using Testcontainers:

1. Add to build.gradle of each service:
   testImplementation 'org.testcontainers:testcontainers:1.19.7'
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create @SpringBootTest integration tests that:
   - Start a MySQL Testcontainer.
   - Use WireMock for external service calls (Keycloak, core-banking).
   - Test the full request→service→repository→response flow.
   - Verify Flyway migrations run successfully.

3. For core-banking-service: test full fund transfer and utility payment flows with real DB.
4. For user-service: mock Keycloak responses and test user lifecycle.
```

---

### 3.5 Add Contract Tests (GAP-3.3)

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. In core-banking-service (provider):
   - Add spring-cloud-starter-contract-verifier.
   - Write contracts for:
     - GET /api/v1/account/bank-account/{number}
     - GET /api/v1/user/{identification}
     - POST /api/v1/transaction/fund-transfer
     - POST /api/v1/transaction/util-payment

2. In consumer services (fund-transfer, user-service, utility-payment):
   - Add spring-cloud-starter-contract-stub-runner.
   - Write consumer-side tests that use generated stubs.

3. Publish stubs to local Maven repository for consumer use.
```

---

### 3.6 Set Up Multi-Module Gradle Build (GAP-1.2)

**Devin Prompt:**
```
Create a root-level Gradle multi-project build:

1. Create root settings.gradle.kts:
   rootProject.name = "internet-banking-microservices"
   include(":banking-common", ":core-banking-service", ":internet-banking-api-gateway", 
           ":internet-banking-config-server", ":internet-banking-service-registry",
           ":internet-banking-fund-transfer-service", ":internet-banking-user-service",
           ":internet-banking-utility-payment-service")

2. Create root build.gradle.kts with shared plugin versions and dependency management.
3. Move common plugin versions and repository declarations to root.
4. Keep service-specific dependencies in each service's build.gradle.
5. Ensure './gradlew build' at root builds everything.
```

---

### 3.7 Add CI/CD Pipeline

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml:
   - Trigger on push to main and pull requests.
   - Matrix build: all 7 services in parallel.
   - Steps: checkout, setup JDK 21, Gradle build, run tests, upload test reports.
   - Cache Gradle dependencies between runs.

2. Create .github/workflows/docker-build.yml:
   - Trigger on release tags.
   - Build Docker images for all services.
   - Push to container registry.

3. Add branch protection rules documentation.
```

---

## Priority Matrix

| Phase | Items | Key Outcomes |
|-------|-------|-------------|
| Phase 1 | 9 items | Fix critical bugs, add validation, secure logs, configure timeouts |
| Phase 2 | 7 items | Add resilience patterns, security hardening, test coverage, shared library |
| Phase 3 | 7 items | Observability, advanced testing, project structure, CI/CD |

## Recommended Execution Order (within phases)

### Phase 1 Sequence:
1. GAP-7.6 — Balance bug fix (immediate financial risk)
2. GAP-2.1/2.2 — Error handling (blocks all other testing)
3. GAP-2.3/4.4 — Validation (prevents bad data)
4. GAP-6.4 — Sensitive data in logs (security)
5. GAP-7.3 — Timeouts (availability)
6. GAP-5.1 — OpenAPI fix (developer tooling)
7. GAP-5.2/5.4 — Response types and pagination
8. GAP-4.5 — Keycloak singleton
9. GAP-7.2 — Retry configuration

### Phase 2 Sequence:
1. GAP-5.5 — Idempotency (financial safety)
2. GAP-7.5 — Transaction compensation (data consistency)
3. GAP-4.1 — Service authentication (security)
4. GAP-4.2/4.3 — Externalize secrets
5. GAP-1.1 — Shared library (unblocks consistent fixes)
6. GAP-7.1/7.4 — Circuit breakers + fallbacks
7. GAP-3.1 — Unit tests
