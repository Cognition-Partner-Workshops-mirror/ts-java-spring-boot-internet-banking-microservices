# Remediation Roadmap

This roadmap prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact, low-effort items that address critical security and reliability issues.

### 1.1 Fix Exception Leaking to Clients (EH-1, EH-2)
**Gap**: Catch-all exception handler returns 400 for all errors and leaks stack traces to clients.
**Severity**: Critical | **Effort**: Small

**Remediation**: Update all `GlobalExceptionHandler` classes to return `500 Internal Server Error` for unhandled exceptions with a generic error message. Never expose exception details.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler in all four services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Change the catch-all `handleException(Exception e)` method to return 500 Internal Server Error with a generic ErrorResponse body containing code='INTERNAL_ERROR' and message='An unexpected error occurred. Please try again later.' instead of leaking exception details. Also add proper HTTP status mapping: EntityNotFoundException should return 404, InsufficientFundsException should return 422, and UserAlreadyRegisteredException should return 409. Open a PR with these changes."*

---

### 1.2 Add Input Validation to Request DTOs (SE-3)
**Gap**: No Bean Validation annotations on any request DTO.
**Severity**: Critical | **Effort**: Small-Medium

**Remediation**: Add `spring-boot-starter-validation` dependency, annotate request DTOs with `@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, etc. Add a validation error handler in `GlobalExceptionHandler`.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add input validation across all services: (1) Add 'org.springframework.boot:spring-boot-starter-validation' to each service's build.gradle, (2) Add @NotBlank, @NotNull, @Email, @Positive annotations to all request DTOs (User, FundTransferRequest, UtilityPaymentRequest, UserUpdateRequest), (3) Add @Valid annotation on all @RequestBody parameters in controllers, (4) Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns 422 with field-level error details. Open a PR."*

---

### 1.3 Externalize Hardcoded Credentials (SE-1)
**Gap**: Database passwords, Keycloak admin credentials hardcoded in Docker Compose and SQL files.
**Severity**: Critical | **Effort**: Small

**Remediation**: Replace hardcoded values with environment variables and provide a `.env.example` template.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, externalize all hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml: Replace MYSQL_ROOT_PASSWORD, POSTGRES_PASSWORD, KC_DB_PASSWORD, KEYCLOAK_ADMIN_PASSWORD values with ${VARIABLE_NAME} references. Create a .env.example file with placeholder values and documentation comments. Update docker-compose/mysql/privileges.sql to use environment variable substitution or document the manual step. Add .env to .gitignore. Open a PR."*

---

### 1.4 Fix Sensitive Data in Logs (OB-2)
**Gap**: Request objects including passwords are logged via `toString()`.
**Severity**: Critical | **Effort**: Small

**Remediation**: Remove or mask sensitive fields from log statements. Exclude password fields from Lombok `@ToString`.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, fix sensitive data logging: (1) Add @ToString.Exclude on the password field in internet-banking-user-service's User DTO, (2) Replace all log statements that log full request objects (request.toString()) with log statements that only log non-sensitive fields like IDs or reference numbers. For example, change log.info('Creating user with {}', request.toString()) to log.info('Creating user with email {}', request.getEmail()). Do this across all services. Open a PR."*

---

### 1.5 Secure Actuator Endpoints (OB-7)
**Gap**: All actuator endpoints are publicly accessible without authentication.
**Severity**: High | **Effort**: Small

**Remediation**: Restrict actuator endpoint exposure to only `/health` and `/info` publicly. Require authentication for sensitive endpoints.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, secure actuator endpoints: (1) In the API gateway SecurityConfiguration, change the actuator permitAll rules to only allow '/*/actuator/health' and '/*/actuator/info'. All other actuator paths should require authentication, (2) Add management.endpoints.web.exposure.include=health,info,prometheus to each service's application.yml (via the config server git repo or local config). Open a PR."*

---

### 1.6 Add Proper HTTP Status Codes (EH-6, AD-6)
**Gap**: All errors return 400; resource creation returns 200 instead of 201.
**Severity**: High | **Effort**: Small

**Remediation**: Map exception types to correct HTTP status codes. Return 201 for creation endpoints.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, fix HTTP status codes: (1) Change UserController.createUser() to return ResponseEntity.status(HttpStatus.CREATED).body(...) instead of ResponseEntity.ok(...), (2) In all GlobalExceptionHandler classes, add specific handlers: @ExceptionHandler(EntityNotFoundException.class) returning 404, @ExceptionHandler(InsufficientFundsException.class) returning 422, @ExceptionHandler(UserAlreadyRegisteredException.class) returning 409. Open a PR."*

---

### 1.7 Fix Raw ResponseEntity Types (AD-1)
**Gap**: Controllers return untyped `ResponseEntity` breaking OpenAPI generation.
**Severity**: High | **Effort**: Small

**Remediation**: Add generic type parameters to all `ResponseEntity` return types.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add proper generic types to all controller ResponseEntity return types. For example, change 'public ResponseEntity sendFundTransfer(...)' to 'public ResponseEntity<FundTransferResponse> sendFundTransfer(...)'. Do this for all controller methods across all four business services (core-banking, user, fund-transfer, utility-payment). This ensures correct OpenAPI schema generation. Open a PR."*

---

### 1.8 Add Feign Client Timeouts (RE-3)
**Gap**: No HTTP client timeouts on Feign clients.
**Severity**: High | **Effort**: Small

**Remediation**: Configure connect and read timeouts for all Feign clients.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add Feign client timeout configuration. In each service that uses Feign (user-service, fund-transfer-service, utility-payment-service), add the following to application.yml (or the centralized config): spring.cloud.openfeign.client.config.default.connect-timeout=5000 and spring.cloud.openfeign.client.config.default.read-timeout=10000. Open a PR."*

---

### 1.9 Fix Swagger Dependency Mismatch (AD-8)
**Gap**: WebFlux Swagger dependency used in Servlet-based services.
**Severity**: Low | **Effort**: Small

**Remediation**: Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in Servlet-based services.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, fix the Swagger dependency in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Change 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' to 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' in each build.gradle. These services are Spring MVC (Servlet-based), not WebFlux. Verify each service still compiles after the change. Open a PR."*

---

## Phase 2: Important Improvements (3-6 Weeks)

Structural improvements that significantly improve reliability, maintainability, and security.

### 2.1 Add Circuit Breakers and Retry Policies (RE-1, RE-2, RE-4)
**Gap**: No circuit breakers, retries, or fallbacks on inter-service calls.
**Severity**: Critical | **Effort**: Medium

**Remediation**: Add Resilience4j with circuit breakers, retry, and fallback on all Feign clients.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breakers and retry policies: (1) Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j' to build.gradle of user-service, fund-transfer-service, and utility-payment-service, (2) Configure default circuit breaker settings in application.yml (slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s), (3) Configure retry settings (maxAttempts=3, waitDuration=1s), (4) Add @CircuitBreaker and fallback methods to each Feign client interface using Resilience4j annotations or a FallbackFactory. Fallbacks should return meaningful error responses rather than propagating exceptions. Open a PR."*

---

### 2.2 Extract Shared Library (CO-1, CO-2)
**Gap**: Duplicated exception classes, DTOs, filters, and mappers across services.
**Severity**: High | **Effort**: Large

**Remediation**: Create a shared `common-library` module and convert to a Gradle multi-project build.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, create a shared common library: (1) Create a root settings.gradle that includes all services as sub-projects, (2) Create a new 'banking-common' module containing shared code: GlobalExceptionHandler, ErrorResponse, SimpleBankingGlobalException, EntityNotFoundException, BaseMapper, AuditAware, AuditorAwareConfig, AuditConfig, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, (3) Publish it as a local dependency, (4) Update each service's build.gradle to depend on 'banking-common' and remove the duplicated classes, (5) Create a root build.gradle with shared plugin versions and Spring Cloud BOM. Ensure all services still compile and tests pass. Open a PR."*

---

### 2.3 Secure Downstream Services (SE-2, SE-4)
**Gap**: Downstream services have no authentication; X-Auth-Id header can be spoofed.
**Severity**: Critical | **Effort**: Medium

**Remediation**: Add JWT validation or internal service-to-service authentication to downstream services.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add security to downstream services: (1) Add 'spring-boot-starter-security' and 'spring-boot-starter-oauth2-resource-server' to core-banking-service, user-service, fund-transfer-service, and utility-payment-service, (2) Configure each service to validate JWT tokens from Keycloak (same JWK URI as the gateway), (3) Update Feign client configurations to propagate the Authorization header from incoming requests using a RequestInterceptor, (4) Add a SecurityConfiguration to each service that requires authentication for all endpoints except /actuator/health. Open a PR."*

---

### 2.4 Add Unit and Integration Tests (TE-1, TE-2)
**Gap**: 4 of 6 services have zero business logic tests; no integration tests anywhere.
**Severity**: Critical | **Effort**: Large

**Remediation**: Add unit tests for service layer logic and integration tests for controller endpoints.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive tests to internet-banking-user-service: (1) Create UserServiceTest with Mockito mocks for UserRepository, KeycloakUserService, and BankingCoreRestClient — test createUser (happy path, duplicate email, invalid email, user not found), readUsers, readUser, updateUser, (2) Create UserControllerTest using @WebMvcTest with MockMvc — test all endpoints with valid and invalid inputs, (3) Create KeycloakUserServiceTest mocking KeycloakManager. Aim for 80%+ line coverage of the service package. Run tests to ensure they pass. Open a PR."*

> **Devin Prompt (follow-up)**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add comprehensive tests to internet-banking-fund-transfer-service and internet-banking-utility-payment-service following the same patterns established in the user-service tests. For each service: (1) unit tests for the service layer with mocked Feign clients and repositories, (2) controller tests using @WebMvcTest with MockMvc. Open a PR."*

---

### 2.5 Add Transaction Safety and Idempotency (RE-5, RE-7)
**Gap**: No idempotency keys; inconsistent transaction state on failure.
**Severity**: Critical | **Effort**: Medium

**Remediation**: Add idempotency keys to financial operations and implement proper transaction management.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add idempotency and transaction safety: (1) Add an 'Idempotency-Key' header parameter to FundTransferController.sendFundTransfer() and UtilityPaymentController.processPayment(), (2) Store the idempotency key in the entity (FundTransferEntity, UtilityPaymentEntity) with a unique constraint, (3) Before processing, check if a record with the same idempotency key exists and return the existing result, (4) Add @Transactional to FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(), (5) Add error handling: if the core-banking Feign call fails, update the local record status to FAILED. Open a PR."*

---

### 2.6 Add Structured Logging (OB-1, OB-5)
**Gap**: Plain text logging, no consistent format across services.
**Severity**: High | **Effort**: Medium

**Remediation**: Configure JSON structured logging with correlation IDs.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add structured JSON logging: (1) Add 'net.logstash.logback:logstash-logback-encoder:7.4' to each service's build.gradle, (2) Create a shared logback-spring.xml in each service's src/main/resources that outputs JSON format with fields: timestamp, level, logger, message, traceId, spanId, service, (3) Ensure Micrometer tracing context (traceId, spanId) is automatically included via MDC. Open a PR."*

---

### 2.7 Add Test Coverage Reporting (TE-4)
**Gap**: No coverage metrics or enforcement.
**Severity**: Medium | **Effort**: Small

**Remediation**: Add JaCoCo plugin to all services.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add JaCoCo test coverage reporting: (1) Add the 'jacoco' plugin to each service's build.gradle, (2) Configure jacocoTestReport to generate HTML and XML reports, (3) Add jacocoTestCoverageVerification with minimum 60% line coverage threshold for the com.javatodev.finance package (excluding model/entity and configuration packages), (4) Make the 'check' task depend on jacocoTestCoverageVerification. Run './gradlew test jacocoTestReport' in core-banking-service to verify it works. Open a PR."*

---

### 2.8 Add Config Server Resilience (RE-8)
**Gap**: Config Server is a single point of failure at startup.
**Severity**: High | **Effort**: Small-Medium

**Remediation**: Add fail-fast with retry for config server bootstrap.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add resilience to config server bootstrap: (1) Add 'spring-retry' and 'spring-boot-starter-aop' dependencies to each client service's build.gradle, (2) In each service's bootstrap.yml, add spring.cloud.config.fail-fast=true, spring.cloud.config.retry.max-attempts=5, spring.cloud.config.retry.initial-interval=2000, spring.cloud.config.retry.max-interval=10000. This ensures services retry the config server connection on startup instead of failing immediately. Open a PR."*

---

### 2.9 Add Feign Error Decoder to All Services (EH-5)
**Gap**: Only user-service has a custom Feign error decoder.
**Severity**: High | **Effort**: Small-Medium

**Remediation**: Add consistent Feign error decoders across all services that use Feign clients.

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add a custom Feign ErrorDecoder to fund-transfer-service and utility-payment-service. Follow the pattern from user-service's CustomFeignErrorDecoder: decode the downstream error response, extract the ErrorResponse body, and throw a SimpleBankingGlobalException with the appropriate code and message. Register it in each service's CustomFeignClientConfiguration. This ensures downstream errors are properly translated rather than wrapped in raw FeignException. Open a PR."*

---

### 2.10 Fix Keycloak Singleton Thread Safety (SE-5)
**Gap**: `KeycloakProperties.getInstance()` is not thread-safe.
**Severity**: Medium | **Effort**: Small

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, fix the thread-safety issue in internet-banking-user-service's KeycloakProperties class. Replace the manual lazy-initialization singleton pattern with a @Bean method in a @Configuration class. Create a KeycloakConfig class that defines a @Bean Keycloak method using KeycloakBuilder, and inject it where needed instead of calling KeycloakProperties.getInstance(). Remove the static keycloakInstance field. Open a PR."*

---

## Phase 3: Polish & Maturity (6-12 Weeks)

Long-term improvements for production readiness and operational excellence.

### 3.1 Add Consumer-Driven Contract Tests (TE-3)
**Gap**: No contract tests between services communicating via Feign.
**Severity**: High | **Effort**: Large

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract tests between core-banking-service (producer) and its consumers (fund-transfer-service, utility-payment-service, user-service): (1) Add spring-cloud-starter-contract-verifier to core-banking-service, (2) Write contract DSL files for the /api/v1/account/bank-account/{account_number}, /api/v1/transaction/fund-transfer, and /api/v1/transaction/util-payment endpoints, (3) Generate and run producer-side tests, (4) Add spring-cloud-starter-contract-stub-runner to consumer services and write consumer-side integration tests that verify Feign clients work against the stubs. Open a PR."*

---

### 3.2 Implement Notification Service with RabbitMQ (Architecture Gap)
**Gap**: Notification service is planned but not implemented; RabbitMQ is mentioned but not integrated.
**Severity**: Medium | **Effort**: Large

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, implement the planned Notification Service: (1) Create a new 'internet-banking-notification-service' Gradle project following existing service patterns, (2) Add 'spring-boot-starter-amqp' (RabbitMQ) dependency to the notification service, fund-transfer-service, and utility-payment-service, (3) Add a RabbitMQ service to docker-compose.yml, (4) In fund-transfer and utility-payment services, publish a message to a RabbitMQ exchange after successful transactions, (5) In the notification service, consume messages and log them (placeholder for email/SMS). Include build.gradle, Dockerfile, application.yml, and register with Eureka. Open a PR."*

---

### 3.3 Add Prometheus Metrics and Grafana Dashboards (OB-4, OB-6)
**Gap**: No Prometheus exposition format; no dashboards or alerting.
**Severity**: Medium | **Effort**: Medium-Large

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics and monitoring: (1) Add 'io.micrometer:micrometer-registry-prometheus' to each service's build.gradle, (2) Expose the /actuator/prometheus endpoint in each service's actuator config, (3) Add a Prometheus container to docker-compose.yml with a prometheus.yml scrape config targeting all services, (4) Add a Grafana container with a pre-configured datasource pointing to Prometheus, (5) Create a basic Grafana dashboard JSON with panels for: request rate, error rate, response time percentiles, JVM memory, and thread pool usage. Open a PR."*

---

### 3.4 Add Dependency Vulnerability Scanning (SE-7)
**Gap**: No automated CVE detection in dependencies.
**Severity**: Medium | **Effort**: Small

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add OWASP Dependency Check: (1) Add the 'org.owasp:dependency-check-gradle:9.0.9' plugin to each service's build.gradle, (2) Configure it to fail the build on CVSS score >= 7, (3) Run './gradlew dependencyCheckAnalyze' on one service to verify it works and document any existing vulnerabilities. Open a PR with the configuration and a summary of findings."*

---

### 3.5 Add End-to-End Tests (TE-6)
**Gap**: No automated tests for the full request flow.
**Severity**: High | **Effort**: Large

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, create an end-to-end test module: (1) Create a new 'e2e-tests' Gradle project with dependencies on RestAssured and JUnit 5, (2) Write tests that start the full Docker Compose stack and execute the golden path: authenticate via Keycloak, register a user, perform a fund transfer, make a utility payment, verify account balances, (3) Add a docker-compose.e2e.yml that starts all services and runs the test suite. Open a PR."*

---

### 3.6 Add Rate Limiting (SE-6)
**Gap**: No rate limiting at any layer.
**Severity**: Medium | **Effort**: Medium

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add rate limiting at the API gateway: (1) Add Spring Cloud Gateway's built-in RequestRateLimiter filter using an in-memory rate limiter (or Redis-backed if Redis is available), (2) Configure default rate limits of 100 requests/second per client IP, (3) Add stricter limits for sensitive endpoints like /user/api/v1/bank-users/register (10 requests/minute) and /*/api/v1/transfer (50 requests/minute), (4) Return 429 Too Many Requests with a Retry-After header when limits are exceeded. Open a PR."*

---

### 3.7 Add CI/CD Pipeline (Build Gap)
**Gap**: No CI/CD pipeline exists.
**Severity**: Medium | **Effort**: Medium

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add a GitHub Actions CI pipeline: (1) Create .github/workflows/ci.yml that triggers on push and PR to main, (2) Set up Java 21, (3) Run './gradlew build test' for each service in parallel using a matrix strategy, (4) Upload JaCoCo test coverage reports as artifacts, (5) Add a Docker build step that builds all service images to verify Dockerfiles are valid (without pushing). Open a PR."*

---

### 3.8 Add Database Migrations to All Services (Data Gap)
**Gap**: Only core-banking-service uses Flyway; other services rely on Hibernate auto-DDL.
**Severity**: Medium | **Effort**: Medium

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add Flyway database migrations to user-service, fund-transfer-service, and utility-payment-service: (1) Add 'org.flywaydb:flyway-core' and 'org.flywaydb:flyway-mysql' to each service's build.gradle, (2) Generate initial migration scripts by examining the JPA entities in each service and creating the corresponding CREATE TABLE statements, (3) Disable Hibernate auto-DDL (spring.jpa.hibernate.ddl-auto=validate), (4) Verify migrations run correctly against H2 in tests. Open a PR."*

---

### 3.9 Implement Graceful Shutdown (RE-9)
**Gap**: No graceful shutdown configuration.
**Severity**: Low | **Effort**: Small

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add graceful shutdown to all services: (1) Add server.shutdown=graceful to each service's application.yml, (2) Add spring.lifecycle.timeout-per-shutdown-phase=30s, (3) Update each Dockerfile to use exec form ENTRYPOINT (already done) and add STOPSIGNAL SIGTERM, (4) Update docker-compose.yml to add stop_grace_period: 30s to each service. Open a PR."*

---

### 3.10 Add Custom Health Checks (OB-3)
**Gap**: No custom health indicators for critical dependencies.
**Severity**: Medium | **Effort**: Small

> **Devin Prompt**: *"In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators: (1) In user-service, create a KeycloakHealthIndicator that checks Keycloak connectivity by calling the realm endpoint, (2) In each database-connected service, ensure the default DataSourceHealthIndicator is active, (3) In the API gateway, create a health indicator that checks Eureka connectivity, (4) Add management.endpoint.health.show-details=when-authorized to each service. Open a PR."*

---

## Summary Timeline

| Phase | Duration | Items | Focus |
|---|---|---|---|
| **Phase 1** | Weeks 1-2 | 9 items | Critical security fixes, error handling, quick API improvements |
| **Phase 2** | Weeks 3-8 | 10 items | Resilience patterns, shared library, testing, logging |
| **Phase 3** | Weeks 9-14+ | 10 items | Contract tests, monitoring, CI/CD, operational maturity |

## Priority Matrix

```
                    High Impact
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │  Phase 2          │  Phase 1          │
    │  (Important)      │  (Quick Wins)     │
    │                   │                   │
    │  Circuit breakers │  Fix exceptions   │
    │  Shared library   │  Input validation │
    │  Service security │  Externalize creds│
    │  Unit tests       │  Fix logging PII  │
    │  Idempotency      │  Secure actuator  │
Low ├───────────────────┼───────────────────┤ High
Effort                  │                     Effort
    │                   │                   │
    │  Phase 1/3        │  Phase 3          │
    │  (Quick Polish)   │  (Long-term)      │
    │                   │                   │
    │  Fix ResponseEntity│  Contract tests  │
    │  Swagger dep fix  │  E2E tests       │
    │  Graceful shutdown│  Notification svc │
    │  Health checks    │  CI/CD pipeline   │
    │                   │                   │
    └───────────────────┼───────────────────┘
                        │
                    Low Impact
```
