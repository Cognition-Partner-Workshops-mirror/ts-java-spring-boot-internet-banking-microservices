# Remediation Roadmap

## Internet Banking Microservices — Prioritized Gap Remediation Plan

This roadmap organizes the 35 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact, low-effort items that address critical security and correctness issues.

### 1.1 Add Input Validation to All Request DTOs

**Gap:** 4.2 — No Input Validation | **Severity:** Critical | **Effort:** Small

Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, `@Email`, `@Size`) to all request DTO fields and `@Valid` to all `@RequestBody` parameters.

**Devin Prompt:**
```
Add Jakarta Bean Validation annotations to all request DTOs across the project:
- FundTransferRequest (core-banking & fund-transfer): @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- UtilityPaymentRequest (core-banking & utility-payment): @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber and account
- User DTO (user-service): @NotBlank @Email on email, @NotBlank on identification and password, @Size(min=8) on password
- UserUpdateRequest: @NotNull on status
Add @Valid annotation to all @RequestBody parameters in all controllers.
Add spring-boot-starter-validation dependency to each service's build.gradle if not present.
Run all existing tests to verify nothing breaks.
```

### 1.2 Fix HTTP Status Codes in Exception Handlers

**Gap:** 2.2 — All Exceptions Return HTTP 400 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Refactor GlobalExceptionHandler in all four business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service):
1. EntityNotFoundException should return 404 Not Found
2. InsufficientFundsException should return 422 Unprocessable Entity
3. UserAlreadyRegisteredException should return 409 Conflict
4. InvalidEmailException and InvalidBankingUserException should remain 400 Bad Request
5. The catch-all Exception handler should return 500 Internal Server Error with a generic message (do NOT expose exception details or stack traces)
6. Use ResponseEntity.status(HttpStatus.XXX) instead of ResponseEntity.badRequest() for each case
7. Ensure all handlers return ErrorResponse objects (not raw strings)
Keep the existing error code conventions. Run tests after changes.
```

### 1.3 Fix Password Logging and Serialization

**Gap:** 4.5 — Password Handling in User DTO | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
In internet-banking-user-service, fix the password handling security issue:
1. In User.java DTO, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field
2. In UserController.createUser(), change the log statement to NOT log the full request object. Instead log only the email: log.info("Creating user with email {}", request.getEmail())
3. Add @ToString.Exclude on the password field in User.java to prevent password appearing in toString()
4. Verify the fix by confirming password is never serialized in responses and never logged
```

### 1.4 Move Credentials to Environment Variables

**Gap:** 4.1 — Hardcoded Credentials | **Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Remove hardcoded credentials from version-controlled files and replace with environment variable references:
1. docker-compose/docker-compose.yml: Replace MySQL root password with ${MYSQL_ROOT_PASSWORD} and Keycloak passwords with ${KEYCLOAK_ADMIN_PASSWORD} and ${KC_DB_PASSWORD}
2. docker-compose/mysql/Dockerfile: Use ARG/ENV pattern with ${MYSQL_ROOT_PASSWORD}
3. docker-compose/mysql/privileges.sql: Replace the hardcoded dev user password with a placeholder and document that it should be set via environment variable
4. Create a docker-compose/.env.example file documenting all required environment variables with placeholder values
5. Add docker-compose/.env to .gitignore
6. Update README.md to document the new environment variable setup
Do NOT change the test application.yml files — those use H2 and are acceptable for tests.
```

### 1.5 Fix Raw ResponseEntity Types

**Gap:** 2.1 — Raw ResponseEntity Return Types | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add type parameters to all raw ResponseEntity return types across all controllers:
- core-banking AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- core-banking TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- core-banking UserController: ResponseEntity<User>, ResponseEntity<List<User>>
- fund-transfer FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- utility-payment UtilityPaymentController: ResponseEntity<List<UtilityPayment>>, ResponseEntity<UtilityPaymentResponse>
This will also fix Swagger/OpenAPI schema generation. Run the build to verify compilation.
```

### 1.6 Fix OpenAPI/Swagger Dependency

**Gap:** 5.5 — OpenAPI/Swagger Misconfiguration | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
In all four servlet-based services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service), replace the incorrect Swagger dependency:
- Remove: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
- Add: implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
These services use Spring MVC (not WebFlux), so the webmvc variant is correct.
The API Gateway (which IS WebFlux-based) does not currently use springdoc, so no change needed there.
Build all services to verify the change compiles correctly.
```

### 1.7 Fix Logging Anti-Patterns

**Gap:** 6.1 — Inconsistent Logging | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix logging anti-patterns across all services:
1. FundTransferService.java: Change log.info("Sending fund transfer request {}" + request.toString()) to log.info("Sending fund transfer request {}", request)
2. CustomFeignErrorDecoder.java (user-service): Change log.error("IO Exception..." + e) to log.error("IO Exception...", e) (use parameterized logging with exception as last arg)
3. AppAuthUserFilter.java (all three services): Change log level from info to debug for "Incoming Request From {}" log
4. Ensure no other string concatenation is used in log statements across the codebase
```

### 1.8 Return Page Instead of List for Paginated Endpoints

**Gap:** 5.3 — Pagination Returns Raw Lists | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Change all paginated GET endpoints to return Page<T> instead of List<T> so clients receive pagination metadata (totalElements, totalPages, number, size):
1. core-banking UserController.readUsers: return ResponseEntity<Page<User>>
2. core-banking UserService.readUsers: return Page<User> by mapping the Page directly instead of extracting .getContent()
3. fund-transfer FundTransferController.readFundTransfers: return ResponseEntity<Page<FundTransfer>>
4. fund-transfer FundTransferService.readAllTransfers: return Page<FundTransfer>
5. utility-payment UtilityPaymentController.readPayments: return ResponseEntity<Page<UtilityPayment>>
6. utility-payment UtilityPaymentService.readPayments: return Page<UtilityPayment>
Use Page.map() to convert entity pages to DTO pages. Run existing tests to verify.
```

---

## Phase 2: Important (3–6 weeks)

Structural improvements that significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers to Feign Clients

**Gap:** 7.1 — No Circuit Breakers | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign client calls across the three services that call core-banking-service:
1. Add dependencies to each service's build.gradle:
   - implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
   - implementation 'io.github.resilience4j:resilience4j-spring-boot3'
2. Configure circuit breaker defaults in application.yml (or via Config Server):
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - slowCallDurationThreshold: 3s
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer service)
   - BankingCoreRestClientFallback (user-service and utility-payment)
4. Add @CircuitBreaker annotations or configure via Feign + Resilience4j integration
5. Fallback behavior: return meaningful error responses indicating the downstream service is unavailable
6. Add unit tests for the fallback behavior
```

### 2.2 Add Timeouts and Retry Configuration

**Gap:** 7.2, 7.3 — No Retry Policies, No Timeout Configuration | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Configure timeouts and retry policies for all inter-service communication:
1. Add Feign client timeout configuration in each service's application.yml (or Config Server):
   spring.cloud.openfeign.client.config.default:
     connectTimeout: 5000
     readTimeout: 10000
2. Add Spring Retry dependency and configure retry for Feign clients:
   - maxAttempts: 3
   - backoff: 1000ms initial, 2x multiplier
   - retryableExceptions: ConnectException, SocketTimeoutException
   - nonRetryableExceptions: SimpleBankingGlobalException (don't retry business errors)
3. Configure Keycloak admin client timeout in KeycloakProperties.java (user-service)
4. Add connection pool and timeout settings for database connections via HikariCP config
```

### 2.3 Add Idempotency to Fund Transfers

**Gap:** 7.6 — Fund Transfer Not Idempotent | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Implement idempotency for the fund transfer endpoint to prevent duplicate transactions:
1. Add an idempotencyKey field to FundTransferRequest
2. Add a unique constraint on idempotency_key column in the fund_transfer table
3. Before processing a transfer, check if a record with the same idempotency_key already exists:
   - If found with status SUCCESS, return the existing response
   - If found with status PENDING/PROCESSING, return 409 Conflict
   - If not found, proceed with the transfer
4. Add the same pattern to the utility payment service
5. Update the Postman collection with sample idempotency keys
6. Write unit tests covering: new transfer, duplicate transfer (returns cached), and concurrent duplicate handling
```

### 2.4 Create Shared Library Module

**Gap:** 1.2, 1.4 — Duplicated Code, No Shared Library | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a shared common library module to eliminate code duplication:
1. Create a new directory: internet-banking-common/
2. Create a root-level settings.gradle that includes all service modules
3. Move these shared classes into internet-banking-common:
   - BaseMapper (from any service — they're identical)
   - AuditAware (MappedSuperclass)
   - AuditConfig + AuditorAwareConfig
   - ApiRequestContext + ApiRequestContextHolder + AppAuthUserFilter
   - SimpleBankingGlobalException + ErrorResponse + GlobalExceptionHandler
   - TransactionStatus enum
4. Configure the common module as a plain Java library (no Spring Boot plugin, just java-library plugin)
5. Add the common module as a dependency in each service's build.gradle:
   implementation project(':internet-banking-common')
6. Remove the duplicated classes from each service
7. Run all tests to verify nothing breaks
```

### 2.5 Add Comprehensive Unit Tests

**Gap:** 3.1 — Minimal Test Coverage | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests to the three business services that currently have no meaningful tests:

internet-banking-user-service:
- UserServiceTest: test createUser (happy path, duplicate email, invalid email, user not found in core banking), readUsers, readUser, updateUser (approve flow, entity not found)
- KeycloakUserServiceTest: test createUser, updateUser, readUserByEmail, readUser (found, not found)
- UserControllerTest: MockMvc tests for all four endpoints including validation errors

internet-banking-fund-transfer-service:
- FundTransferServiceTest: test fundTransfer (happy path, core banking failure, save failure), readAllTransfers
- FundTransferControllerTest: MockMvc tests for POST and GET endpoints

internet-banking-utility-payment-service:
- UtilityPaymentServiceTest: test utilPayment (happy path, core banking failure), readPayments
- UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

Use Mockito for mocking Feign clients and repositories. Use @WebMvcTest for controller tests.
Fix the test application.yml files to use ddl-auto: create-drop so H2 tables are created.
Ensure all tests pass with ./gradlew test.
```

### 2.6 Add Authentication to Downstream Services

**Gap:** 4.6 — Downstream Services Have No Authentication | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add service-to-service authentication to protect downstream services:
1. Add spring-boot-starter-security and spring-boot-starter-oauth2-resource-server to core-banking-service, user-service, fund-transfer-service, and utility-payment-service
2. Configure each service as an OAuth2 Resource Server that validates JWTs from Keycloak (same JWK URI as the gateway)
3. Configure the X-Auth-Id header to be extracted from the JWT claims instead of trusting it blindly
4. Ensure Feign clients propagate the JWT token from incoming requests to outgoing calls using a RequestInterceptor
5. Keep actuator endpoints accessible without authentication
6. Update test configurations to disable security for unit tests
7. Test the full flow: gateway authenticates → passes JWT → downstream validates JWT
```

### 2.7 Set Up CI Pipeline

**Gap:** 3.3 — No CI Pipeline | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a GitHub Actions CI pipeline for the project:
1. Create .github/workflows/ci.yml with:
   - Trigger: push to main, pull_request to main
   - Java 21 setup with Gradle caching
   - Build all 7 services (parallel where possible)
   - Run tests for all services
   - Upload test reports as artifacts
2. Add a Gradle wrapper consistency check
3. Add a step for dependency vulnerability scanning (using gradle dependencyCheckAnalyze or similar)
4. Ensure the workflow handles the multi-project structure (each service is independent)
5. Add a badge to README.md showing build status
```

### 2.8 Configure Prometheus Metrics

**Gap:** 6.3 — Metrics Not Explicitly Configured | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add Prometheus metrics support to all services:
1. Add micrometer-registry-prometheus dependency to each service's build.gradle
2. Configure actuator to expose prometheus, health, info, and metrics endpoints:
   management.endpoints.web.exposure.include: health,info,metrics,prometheus
3. Add custom business metrics using MeterRegistry:
   - core-banking: counter for fund_transfers_total, utility_payments_total; timer for transaction_processing_duration
   - fund-transfer: counter for transfers_initiated, transfers_completed, transfers_failed
   - utility-payment: counter for payments_initiated, payments_completed, payments_failed
4. Add a prometheus.yml scrape configuration in docker-compose/ for local development
5. Optionally add a Grafana container with a basic dashboard
```

### 2.9 Add Custom Health Indicators

**Gap:** 6.2 — Health Checks Are Default Only | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add custom health indicators to each service:
1. core-banking-service: DatabaseHealthIndicator (verify MySQL connection), add to /actuator/health
2. user-service: KeycloakHealthIndicator (verify Keycloak is reachable), DatabaseHealthIndicator
3. fund-transfer-service: CoreBankingHealthIndicator (verify core-banking-service is reachable via Eureka)
4. utility-payment-service: CoreBankingHealthIndicator
5. Configure actuator to show health details: management.endpoint.health.show-details: always
6. Add readiness and liveness probe configurations for Kubernetes:
   management.endpoint.health.probes.enabled: true
   management.health.readinessState.enabled: true
   management.health.livenessState.enabled: true
```

---

## Phase 3: Polish (6–12 weeks)

Improvements for long-term maintainability, developer experience, and operational excellence.

### 3.1 Implement Saga Pattern for Distributed Transactions

**Gap:** 7.7 — No Compensation / Saga Pattern | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Implement the Transactional Outbox pattern with a saga orchestrator for fund transfers and utility payments:
1. Add an outbox_event table to fund-transfer and utility-payment service databases
2. When a transfer/payment is initiated, write the request to the outbox table in the same local transaction
3. Add a scheduled task (or use Debezium CDC) to poll the outbox and send events to RabbitMQ
4. Core banking service consumes from the queue, processes the transaction, and publishes a result event
5. Fund transfer / utility payment service consumes the result and updates the local entity status
6. Add compensation logic: if the core banking call fails, update local entity to FAILED and publish a compensation event
7. Add a reconciliation job that checks for stuck PENDING/PROCESSING records older than a configurable threshold
Consider using Spring State Machine or Axon Framework for saga orchestration if complexity warrants it.
```

### 3.2 Add Contract Tests Between Services

**Gap:** 3.1 (extended) — No Contract Tests | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:
1. Add spring-cloud-contract-verifier to core-banking-service (as the provider)
2. Write contract DSL files for:
   - GET /api/v1/account/bank-account/{account_number} (found, not found)
   - POST /api/v1/transaction/fund-transfer (success, insufficient funds)
   - POST /api/v1/transaction/util-payment (success, account not found)
   - GET /api/v1/user/{identification} (found, not found)
3. Generate contract stubs JAR from core-banking-service
4. Add spring-cloud-contract-stub-runner to consumer services (user, fund-transfer, utility-payment)
5. Write consumer-side tests that use the generated stubs
6. Integrate contract test generation into the Gradle build and CI pipeline
```

### 3.3 Implement RabbitMQ Notification Service

**Gap:** Listed as PENDING in README | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Implement the notification service that was planned but never built:
1. Create a new internet-banking-notification-service module
2. Add RabbitMQ dependency (spring-boot-starter-amqp) to fund-transfer and utility-payment services
3. Define exchange, queue, and routing key configuration for:
   - fund.transfer.completed
   - fund.transfer.failed
   - utility.payment.completed
   - utility.payment.failed
4. In fund-transfer and utility-payment services, publish events to RabbitMQ after transaction completion
5. In notification-service, consume events and log them (email/SMS integration can be added later)
6. Add RabbitMQ to docker-compose.yml
7. Register notification-service with Eureka
8. Add health indicator for RabbitMQ connectivity
```

### 3.4 Add Rate Limiting to API Gateway

**Gap:** 4.4 — No Rate Limiting | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the Spring Cloud Gateway:
1. Add spring-boot-starter-data-redis-reactive dependency to the API Gateway
2. Add Redis to docker-compose.yml
3. Configure RequestRateLimiter filter in gateway routes:
   - Default: 10 requests/second per user
   - Fund transfer: 5 requests/second per user (stricter for financial operations)
   - User registration: 3 requests/minute per IP (prevent account spam)
4. Use KeyResolver beans to resolve rate limit keys:
   - Authenticated endpoints: resolve by JWT subject claim
   - Public endpoints: resolve by IP address
5. Configure appropriate 429 Too Many Requests response with Retry-After header
6. Add rate limit metrics to Prometheus
```

### 3.5 Add Dependency Vulnerability Scanning

**Gap:** 4.7 — No Dependency Vulnerability Scanning | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency Check to the Gradle build:
1. Add the org.owasp.dependencycheck plugin to each service's build.gradle (or root build.gradle if multi-project is set up)
2. Configure the plugin:
   - failBuildOnCVSS: 7 (fail build on High+ vulnerabilities)
   - suppressionFile: point to a shared suppression file for known false positives
3. Add a dependencyCheckAnalyze task to the CI pipeline
4. Run the check once and document any existing vulnerabilities that need attention
5. Add a scheduled CI job (weekly) to check for new vulnerabilities
```

### 3.6 Standardize Package Structure

**Gap:** 1.3 — Inconsistent Package Structure | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Standardize the package structure across all four business services to follow this convention:
  com.javatodev.finance
  ├── controller/         # REST controllers
  ├── service/            # Business logic
  │   └── client/         # Feign clients (if applicable)
  ├── model/
  │   ├── entity/         # JPA entities
  │   ├── dto/            # Data transfer objects
  │   │   ├── request/    # Request DTOs
  │   │   └── response/   # Response DTOs
  │   ├── mapper/         # Entity-DTO mappers
  │   └── enums/          # Enumerations
  ├── repository/         # Spring Data repositories
  ├── configuration/      # Spring configuration classes
  │   ├── audit/
  │   ├── feign/
  │   ├── filter/
  │   └── security/
  └── exception/          # Exception classes
Move classes to match this structure, update all imports, and verify compilation.
```

### 3.7 Add Bulkhead Pattern

**Gap:** 7.5 — No Bulkhead Pattern | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add thread pool bulkheads using Resilience4j to isolate Feign client calls:
1. Add resilience4j-bulkhead dependency (should already be included with the circuit breaker starter from Phase 2)
2. Configure separate thread pool bulkheads for each Feign client:
   resilience4j.thread-pool-bulkhead.instances:
     coreBankingService:
       maxThreadPoolSize: 10
       coreThreadPoolSize: 5
       queueCapacity: 20
3. Apply the bulkhead to each Feign client call alongside the circuit breaker
4. Add bulkhead metrics to Prometheus monitoring
5. Write tests verifying that a slow core-banking-service doesn't exhaust all threads
```

### 3.8 Add Structured Logging

**Gap:** 6.1 (extended) — No Structured Logging | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add structured JSON logging for production readiness:
1. Add logstash-logback-encoder dependency to each service
2. Create a logback-spring.xml in each service's src/main/resources:
   - Console appender with pattern layout for local development (default profile)
   - Console appender with JSON layout for docker/production profiles
3. Configure MDC fields: traceId, spanId, serviceName, userId (from X-Auth-Id)
4. Update AppAuthUserFilter to set userId in MDC
5. Verify JSON logs include trace context from Micrometer Tracing
```

---

## Summary Timeline

| Phase | Items | Estimated Duration | Key Outcomes |
|-------|-------|-------------------|--------------|
| **Phase 1** | 8 items | 1–2 weeks | Critical security fixes, input validation, proper HTTP status codes, credential management, corrected dependencies |
| **Phase 2** | 9 items | 3–6 weeks | Circuit breakers, timeouts, idempotency, shared library, comprehensive tests, CI pipeline, downstream auth, metrics, health checks |
| **Phase 3** | 8 items | 6–12 weeks | Saga pattern, contract tests, notification service, rate limiting, vulnerability scanning, standardized structure, bulkheads, structured logging |

**Recommended starting order within each phase:** Address items in the numbered order shown — they are sequenced to minimize rework and maximize incremental value.
