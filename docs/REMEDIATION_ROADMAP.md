# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact improvements that require small effort. These address critical security and correctness issues.

---

### 1.1 Add Input Validation to All Request DTOs

**Gap:** 4.1 (Critical, Small)

Add Bean Validation annotations to all request DTOs and `@Valid` on controller parameters.

**Files to modify:**
- All `*Request.java` DTOs across all services
- All `*Controller.java` classes (add `@Valid`)
- Add validation exception handler to `GlobalExceptionHandler`

**Devin Prompt:**
> Add Jakarta Bean Validation annotations to all request DTOs in the project. Add @NotNull, @NotBlank, @Positive, and @Size constraints as appropriate. Add @Valid to all @RequestBody parameters in controllers. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns a structured error response with field-level error details and HTTP 422 status.

---

### 1.2 Fix Error Response Structure and HTTP Status Codes

**Gaps:** 2.1 (Critical, Small), 2.2 (High, Small), 2.4 (Medium, Small)

Standardize error responses and return appropriate HTTP status codes.

**Devin Prompt:**
> Refactor the GlobalExceptionHandler in all services to: (1) Remove the generic Exception handler that returns a plain string — replace it with a structured ErrorResponse returning HTTP 500. (2) Map EntityNotFoundException to 404, InsufficientFundsException to 422 Unprocessable Entity, and SimpleBankingGlobalException to 400. (3) Create a unified ErrorResponse DTO with fields: timestamp, status, error, message, path. Never expose raw exception details in the response body.

---

### 1.3 Prevent Password Exposure in User API Responses

**Gap:** 4.6 (Critical, Small)

**Devin Prompt:**
> In internet-banking-user-service, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field in the User DTO class. Verify that the GET /api/v1/bank-users and GET /api/v1/bank-users/{id} endpoints no longer include the password field in their JSON responses.

---

### 1.4 Add Feign Client Error Handling

**Gap:** 2.3 (High, Small)

**Devin Prompt:**
> Implement a CustomFeignErrorDecoder for the BankingCoreFeignClient in both internet-banking-fund-transfer-service and internet-banking-utility-payment-service (similar to the one in user-service). The decoder should translate 404 responses into EntityNotFoundException, 422 into InsufficientFundsException, and other errors into a generic SimpleBankingGlobalException with the downstream error message.

---

### 1.5 Fix Swagger/OpenAPI Dependency

**Gap:** 5.5 (High, Small)

**Devin Prompt:**
> In the build.gradle files for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, replace the incorrect dependency `org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0` with `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0`. These services use Spring MVC (servlet), not WebFlux. Leave the API Gateway as-is since it uses WebFlux. Verify Swagger UI is accessible at /swagger-ui.html for each service.

---

### 1.6 Add Typed ResponseEntity Return Types

**Gap:** 5.1 (Medium, Small)

**Devin Prompt:**
> Add generic type parameters to all ResponseEntity return types in all controller classes across the project. For example, change `public ResponseEntity getBankAccount(...)` to `public ResponseEntity<BankAccount> getBankAccount(...)`. This improves compile-time safety and generates accurate OpenAPI schemas.

---

### 1.7 Configure Explicit Timeouts

**Gap:** 7.3 (High, Small)

**Devin Prompt:**
> Add explicit timeout configuration for all Feign clients and database connections. In each service's application.yml (or the centralized config repo), add: (1) Feign timeouts: `spring.cloud.openfeign.client.config.default.connect-timeout: 5000` and `read-timeout: 10000`. (2) HikariCP pool config: `spring.datasource.hikari.connection-timeout: 30000`, `maximum-pool-size: 10`, `minimum-idle: 5`. (3) API Gateway route timeouts: add `response-timeout: 10s` and `connect-timeout: 5s` to gateway route metadata.

---

### 1.8 Fix Broken Context Load Tests

**Gap:** 3.4 (Medium, Small)

**Devin Prompt:**
> Fix the *ApplicationTests.java classes in all services that attempt to load the full Spring context. Either: (1) Add a test profile (src/test/resources/application-test.yml) that disables Eureka, Config Server, and sets spring.cloud.config.enabled=false, spring.cloud.discovery.enabled=false, and use @ActiveProfiles("test"), OR (2) Replace them with focused @WebMvcTest or @DataJpaTest slice tests that don't require external infrastructure.

---

### 1.9 Fix Keycloak Singleton Thread Safety

**Gap:** 4.3 (Medium, Small)

**Devin Prompt:**
> In internet-banking-user-service, refactor KeycloakProperties to use a proper Spring-managed bean. Create a @Configuration class with a @Bean method that builds the Keycloak instance. Remove the static field and lazy initialization from KeycloakProperties. Inject the Keycloak bean where needed.

---

### 1.10 Add Health Check Indicators for Dependencies

**Gap:** 6.2 (Medium, Small)

**Devin Prompt:**
> Configure Spring Boot Actuator health checks to include downstream dependencies. In each service's configuration, add: `management.endpoint.health.show-details=always` and `management.health.db.enabled=true`. For the user service, add a custom HealthIndicator that checks Keycloak connectivity. Ensure the /actuator/health endpoint reports database and discovery client health.

---

## Phase 2: Important (3-6 Weeks)

Structural improvements that require moderate effort but significantly improve reliability and maintainability.

---

### 2.1 Implement Circuit Breakers with Resilience4j

**Gap:** 7.1 (Critical, Medium)

**Devin Prompt:**
> Add Resilience4j circuit breaker support to all Feign client calls. (1) Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to the build.gradle of fund-transfer, utility-payment, and user services. (2) Configure circuit breaker instances for the BankingCoreFeignClient with: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3. (3) Add @CircuitBreaker annotations or configure via application.yml. (4) Implement fallback methods that return meaningful error responses when the circuit is open.

---

### 2.2 Fix Transaction Integrity Issues

**Gap:** 7.6 (Critical, Large)

**Devin Prompt:**
> Fix the critical transaction integrity issues in core-banking-service: (1) Change TransactionEntity's @OneToOne relationship to BankAccountEntity to @ManyToOne (many transactions per account). Remove CascadeType.ALL. (2) Add a @Version field (Long) to BankAccountEntity for optimistic locking. (3) Fix the double-debit bug in TransactionService.internalFundTransfer() — availableBalance should be set independently from actualBalance subtraction, not chained. The same bug exists in utilPayment(). (4) Add validation to reject self-transfers (fromAccount == toAccount), negative amounts, and zero amounts.

---

### 2.3 Extract Shared Library Module

**Gap:** 1.2 (High, Medium)

**Devin Prompt:**
> Create a shared library module called `banking-common` at the project root. (1) Add a root settings.gradle that includes all service modules. (2) Move the following shared classes into banking-common: GlobalExceptionHandler, ErrorResponse, SimpleBankingGlobalException, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, AuditAware, AuditConfig, AuditorAwareConfig, BaseMapper, CustomFeignClientConfiguration. (3) Update each service's build.gradle to depend on `implementation project(':banking-common')`. (4) Remove the duplicated classes from each service.

---

### 2.4 Add Rate Limiting to API Gateway

**Gap:** 4.5 (High, Medium)

**Devin Prompt:**
> Add rate limiting to the internet-banking-api-gateway. (1) Add Redis dependency and Spring Cloud Gateway's RequestRateLimiter filter. (2) Configure a Redis-backed rate limiter with: replenishRate=10, burstCapacity=20 per user (keyed by principal name from JWT). (3) Add a Redis service to docker-compose.yml. (4) Configure a default rate limit for unauthenticated endpoints (registration) at a lower rate (5 requests/second). (5) Return HTTP 429 with a Retry-After header when rate limited.

---

### 2.5 Implement Retry Policies for Feign Clients

**Gap:** 7.2 (High, Medium)

**Devin Prompt:**
> Add retry configuration for Feign clients using Resilience4j retry. (1) Configure retry for GET (read) operations with: maxAttempts=3, waitDuration=500ms, exponentialBackoffMultiplier=2. (2) Do NOT add retry for POST (write) operations unless idempotency keys are implemented first. (3) Add `@Retryable` or configure via resilience4j.retry instances in application.yml. (4) Log retry attempts at WARN level.

---

### 2.6 Implement Structured JSON Logging

**Gap:** 6.1 (High, Medium)

**Devin Prompt:**
> Implement structured JSON logging across all services. (1) Add Logback JSON encoder dependency (logstash-logback-encoder). (2) Create a logback-spring.xml in each service's resources that outputs JSON with fields: timestamp, level, logger, message, traceId, spanId, service. (3) Remove all toString() calls in log statements that could expose PII. (4) Mask account numbers in logs (show only last 4 digits). (5) Add MDC context for userId from X-Auth-Id header.

---

### 2.7 Add Custom Business Metrics

**Gap:** 6.3 (Medium, Medium)

**Devin Prompt:**
> Add custom Micrometer metrics for key business operations. (1) In core-banking-service: add counters for fund_transfer_total (tagged by status: success/failed), utility_payment_total, and a gauge for active_accounts. (2) In fund-transfer-service: add a timer for fund_transfer_duration and a counter for fund_transfer_errors. (3) In user-service: add counters for user_registrations and user_approvals. (4) Configure Prometheus endpoint: management.endpoints.web.exposure.include=health,info,metrics,prometheus.

---

### 2.8 Create Multi-Project Gradle Build

**Gap:** 1.1 (Medium, Medium)

**Devin Prompt:**
> Convert the project to a Gradle multi-project build. (1) Create a root build.gradle with shared configuration (Java 21, Spring Boot 3.2.4 plugin, Spring Cloud BOM, common dependencies). (2) Create a root settings.gradle that includes all 6 service modules plus the banking-common module. (3) Move common plugin configuration and dependency versions to a `buildSrc/src/main/groovy/banking-conventions.gradle` convention plugin. (4) Simplify each service's build.gradle to only declare service-specific dependencies. (5) Verify all services still build independently with `./gradlew :core-banking-service:build`.

---

### 2.9 Add Fallback Behavior for Read Operations

**Gap:** 7.4 (Medium, Medium)

**Devin Prompt:**
> Implement fallback behavior for non-critical read operations. (1) In the fund-transfer-service, add a @CircuitBreaker fallback that returns a cached/empty response with a warning message when core-banking is unavailable for GET (read) operations. (2) In the user-service readUsers endpoint, fall back to returning local DB records without Keycloak enrichment if Keycloak is down. (3) Add appropriate HTTP headers (e.g., X-Degraded-Response: true) to signal partial responses.

---

## Phase 3: Polish (6-12 Weeks)

Comprehensive improvements for production-readiness, long-term maintainability, and operational excellence.

---

### 3.1 Add Comprehensive Unit Test Coverage

**Gap:** 3.1 (Critical, Large)

**Devin Prompt:**
> Add unit tests for all service classes in internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Target 80%+ line coverage for service layer classes. Use Mockito to mock repository and Feign client dependencies. Test happy path, error cases (exceptions), and edge cases (null inputs, empty lists, boundary values). Add @WebMvcTest controller tests that verify request/response serialization, validation, and HTTP status codes.

---

### 3.2 Add Integration Tests with Testcontainers

**Gap:** 3.2 (High, Large)

**Devin Prompt:**
> Add integration tests using Testcontainers for MySQL. (1) Add testcontainers dependency to each service's build.gradle. (2) Create integration test classes annotated with @SpringBootTest that use @Testcontainers and @Container for MySQL. (3) Test the full request flow: controller -> service -> repository -> MySQL. (4) For services with Feign clients, use WireMock to stub core-banking-service responses. (5) Add a test for the fund transfer flow that verifies account balances are correctly updated in the database.

---

### 3.3 Add Consumer-Driven Contract Tests

**Gap:** 3.3 (Medium, Large)

**Devin Prompt:**
> Implement Spring Cloud Contract tests between services. (1) In core-banking-service (provider), add spring-cloud-starter-contract-verifier. Define contracts for all API endpoints consumed by other services. (2) In fund-transfer-service and utility-payment-service (consumers), add spring-cloud-starter-contract-stub-runner. Write consumer contract tests that verify the Feign client expectations match the provider's contract. (3) Generate and publish contract stubs to a local Maven repository. (4) Add contract verification to the build pipeline.

---

### 3.4 Implement Saga Pattern for Fund Transfers

**Gap:** 7.6 (Critical, Large) — continued

**Devin Prompt:**
> Implement the Saga pattern with compensation for fund transfers. (1) Add a state machine to FundTransferEntity with states: INITIATED, DEBITED, COMPLETED, COMPENSATING, FAILED. (2) When the fund-transfer-service calls core-banking and the debit succeeds but the credit fails, automatically trigger a compensation (reverse debit). (3) Add an idempotency key (UUID) to FundTransferRequest that prevents duplicate transfers. Store the key and reject duplicate requests. (4) Add a scheduled job that detects INITIATED transfers older than 5 minutes and marks them as FAILED.

---

### 3.5 Implement Async Notifications with RabbitMQ

**Gap:** Not in gap analysis — planned feature from README

**Devin Prompt:**
> Implement the RabbitMQ notification system referenced in the README. (1) Add spring-boot-starter-amqp to fund-transfer-service and utility-payment-service. (2) Create a RabbitMQ exchange and queues for transfer and payment notifications. (3) After successful fund transfers and utility payments, publish a notification event to RabbitMQ. (4) Create a new notification-service that consumes these messages and logs them (placeholder for email/SMS integration). (5) Add RabbitMQ to docker-compose.yml. (6) Add RabbitMQ health indicator.

---

### 3.6 Add Pagination Metadata to List Endpoints

**Gap:** 5.3 (Medium, Small)

**Devin Prompt:**
> Wrap all paginated list endpoints in a standard page response. Create a generic PageResponse<T> class with fields: content (List<T>), page (number, size, totalElements, totalPages, isFirst, isLast). Update all GET list endpoints in UserController, FundTransferController, UtilityPaymentController, and core UserController to return PageResponse instead of raw List. Use Spring Data's Page object to populate the metadata.

---

### 3.7 Set Up CI/CD Pipeline

**Gap:** No active CI pipeline

**Devin Prompt:**
> Create a GitHub Actions CI workflow (.github/workflows/ci.yml) that: (1) Runs on push to main and on pull requests. (2) Sets up Java 21 with Gradle caching. (3) Runs `./gradlew build` for all services. (4) Runs unit tests with test reports. (5) Performs a Docker Compose build to verify all Dockerfiles. (6) Publishes test results and coverage reports as PR comments. (7) Fails the build if any test fails or if coverage drops below the threshold.

---

### 3.8 Add Docker Compose Environment Variable Management

**Gap:** 4.2 (Medium, Small)

**Devin Prompt:**
> Refactor docker-compose.yml to use environment variable substitution for all credentials. (1) Create a .env.example file with placeholder values for all secrets (MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, etc.). (2) Update docker-compose.yml to reference these variables: `${MYSQL_ROOT_PASSWORD}`. (3) Add .env to .gitignore. (4) Update the README with instructions to copy .env.example to .env and fill in values.

---

### 3.9 Implement API Documentation Strategy

**Gaps:** 5.2 (Low, Small), 5.4 (Low, Medium)

**Devin Prompt:**
> Enhance API documentation across all services. (1) Add springdoc-openapi configuration in each service to set API title, version, description, and contact info. (2) Add @Schema annotations to all DTOs with descriptions and examples. (3) Add @ApiResponse annotations to controller methods documenting all possible response codes. (4) Configure the API Gateway to aggregate all downstream OpenAPI specs into a single unified spec at /v3/api-docs. (5) Document the versioning strategy (URL path-based, v1 current) in a comment in each controller.

---

### 3.10 Improve Startup Resilience

**Gap:** 7.5 (Medium, Small)

**Devin Prompt:**
> Improve service startup resilience. (1) Add Spring Cloud Config retry configuration to all services: spring.cloud.config.retry.max-attempts=6, initial-interval=1000, multiplier=1.5. (2) Add `restart: on-failure` with `max_attempts: 3` to all application services in docker-compose.yml. (3) Replace wait-for-it.sh with Docker Compose `depends_on` with health checks (healthcheck configuration for MySQL, Config Server, and Eureka). (4) Add spring.cloud.config.fail-fast=true with retry enabled so services retry config fetching instead of dying immediately.

---

## Summary

| Phase | Items | Estimated Duration | Key Outcomes |
|-------|-------|-------------------|--------------|
| **Phase 1** | 10 items | 1-2 weeks | Security hardened, basic correctness fixed, errors standardized |
| **Phase 2** | 9 items | 3-6 weeks | Resilient, observable, well-structured codebase |
| **Phase 3** | 10 items | 6-12 weeks | Production-ready, fully tested, CI/CD enabled |

### Priority Order Within Each Phase

Items within each phase are already ordered by priority (highest first). If time is constrained, focus on items numbered 1-5 in each phase before addressing 6+.

### Dependencies Between Items

```
Phase 1.2 (Error structure) ──► Phase 1.4 (Feign error handling)
Phase 2.3 (Shared library) ──► Phase 2.8 (Multi-project build)
Phase 2.1 (Circuit breakers) ──► Phase 2.5 (Retry policies) ──► Phase 2.9 (Fallbacks)
Phase 2.2 (Transaction fixes) ──► Phase 3.4 (Saga pattern)
Phase 1.8 (Fix test infra) ──► Phase 3.1 (Unit tests) ──► Phase 3.2 (Integration tests)
```
