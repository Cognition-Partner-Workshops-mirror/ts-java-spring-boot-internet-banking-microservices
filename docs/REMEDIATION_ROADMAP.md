# Remediation Roadmap

## Overview

This roadmap prioritizes gaps from the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on risk, impact, and effort. Each item includes a ready-to-use Devin prompt.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact improvements requiring small effort. Focus on security, correctness, and immediate reliability gains.

### 1.1 Add Input Validation to All DTOs

**Gaps addressed:** GAP-ERR-004, GAP-SEC-002
**Severity:** Critical | **Effort:** Small

Add `jakarta.validation` constraints to all request DTOs and `@Valid` annotations to controller parameters.

**Devin Prompt:**
```
Add Bean Validation (jakarta.validation) to all request DTOs and controllers across all services in ts-java-spring-boot-internet-banking-microservices. For each @RequestBody parameter, add @Valid. For each DTO field, add appropriate constraints: @NotNull, @NotBlank for strings, @Positive or @Min(1) for amounts, @Email for email fields. Add spring-boot-starter-validation to each service's build.gradle if not present. Update GlobalExceptionHandler in each service to handle MethodArgumentNotValidException and return a structured error response with field-level error details and HTTP 400.
```

---

### 1.2 Fix Exception Handling — Proper HTTP Status Codes

**Gaps addressed:** GAP-ERR-001, GAP-ERR-002
**Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Refactor GlobalExceptionHandler in all 4 business services (core-banking, fund-transfer, user, utility-payment) of ts-java-spring-boot-internet-banking-microservices. Changes: (1) EntityNotFoundException should return 404 Not Found, (2) InsufficientFundsException should return 422 Unprocessable Entity, (3) UserAlreadyRegisteredException should return 409 Conflict, (4) The catch-all Exception handler should return 500 Internal Server Error with a generic message (never expose stack traces or exception details to clients), (5) All error responses must use the structured ErrorResponse format with code and message fields. Remove the raw string body from the generic exception handler.
```

---

### 1.3 Add Feign Timeout Configuration

**Gaps addressed:** GAP-RES-002
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add Feign client timeout configuration to all services that use OpenFeign (fund-transfer-service, user-service, utility-payment-service) in ts-java-spring-boot-internet-banking-microservices. In each service's application.yml (or via CustomFeignClientConfiguration), set: connectTimeout=5000ms, readTimeout=10000ms. Also add Spring Cloud Gateway route timeouts in the API gateway configuration: response-timeout=30s, connect-timeout=5s.
```

---

### 1.4 Add Retry Policies

**Gaps addressed:** GAP-RES-003
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Add Spring Retry with Resilience4j to the fund-transfer-service, user-service, and utility-payment-service in ts-java-spring-boot-internet-banking-microservices. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j' to each build.gradle. Configure retry for Feign calls: maxAttempts=3, waitDuration=1s, exponentialBackoff multiplier=2. Only retry on 5xx errors and connection timeouts, NOT on 4xx client errors. Add this configuration in application.yml under resilience4j.retry.instances.
```

---

### 1.5 Remove Hardcoded Credentials

**Gaps addressed:** GAP-SEC-001
**Severity:** Critical | **Effort:** Small

**Devin Prompt:**
```
Remove all hardcoded credentials from ts-java-spring-boot-internet-banking-microservices. In docker-compose.yml and docker-compose-support-apps.yml, replace hardcoded passwords with environment variable references (${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD}, etc.) and add a .env.example file documenting required variables. In privileges.sql, use a placeholder password. Add .env to .gitignore. Remove the test credentials from README.md and replace with a note to check .env.example.
```

---

### 1.6 Fix OpenAPI Documentation

**Gaps addressed:** GAP-API-005
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Fix OpenAPI/Swagger configuration in ts-java-spring-boot-internet-banking-microservices. In core-banking-service, fund-transfer-service, user-service, and utility-payment-service: (1) Replace the incorrect dependency 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' since these are servlet-based (not reactive) services. (2) Add @ApiResponse annotations to all controller methods documenting success and error response schemas. (3) Add typed ResponseEntity<T> return types to all controller methods instead of raw ResponseEntity.
```

---

### 1.7 Fix Keycloak Singleton Thread Safety

**Gaps addressed:** GAP-SEC-004
**Severity:** High | **Effort:** Small

**Devin Prompt:**
```
Fix the thread-unsafe Keycloak singleton in ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service. In KeycloakProperties.java, the static Keycloak instance uses a non-synchronized lazy initialization pattern. Replace this with a Spring @Bean definition: create a @Configuration class that produces a singleton Keycloak bean via KeycloakBuilder. Inject this bean into KeycloakManager instead of calling getInstance(). This ensures thread safety through Spring's container-managed singleton scope and proper lifecycle management.
```

---

### 1.8 Add Structured Logging with Trace Correlation

**Gaps addressed:** GAP-OBS-001
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Configure structured JSON logging with trace ID correlation in all services of ts-java-spring-boot-internet-banking-microservices. (1) Add logback-spring.xml to each service's src/main/resources with a JSON encoder (logstash-logback-encoder). (2) Include traceId and spanId from Brave/Micrometer in the log pattern. (3) Remove all toString() logging of request bodies in controllers (security risk - logs account numbers). Replace with selective field logging or request IDs only. (4) Add the logstash-logback-encoder dependency to each build.gradle.
```

---

### 1.9 Add Pagination Metadata to List Responses

**Gaps addressed:** GAP-API-003
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add pagination metadata to all paginated endpoints in ts-java-spring-boot-internet-banking-microservices. Create a generic PageResponse<T> wrapper class with fields: content (List<T>), totalElements, totalPages, currentPage, pageSize. Update these endpoints to return PageResponse instead of raw List: (1) FundTransferController.readFundTransfers, (2) UtilityPaymentController.readPayments, (3) UserController.readUsers (in both core-banking and user service). The services should pass through the Page metadata from Spring Data rather than calling .getContent() and discarding it.
```

---

## Phase 2: Important (3–6 weeks)

Structural improvements that require more effort but significantly reduce operational risk.

### 2.1 Add Circuit Breakers

**Gaps addressed:** GAP-RES-001, GAP-RES-004
**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
Add Resilience4j circuit breakers to all Feign clients in ts-java-spring-boot-internet-banking-microservices. (1) Add spring-cloud-starter-circuitbreaker-resilience4j dependency. (2) Configure circuit breaker instances for each Feign client: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3. (3) Add fallback methods for each Feign operation that return appropriate error responses (e.g., FundTransferResponse with status=FAILED and message="Core banking service unavailable"). (4) Configure in application.yml under resilience4j.circuitbreaker.instances. (5) Enable Feign + CircuitBreaker integration via feign.circuitbreaker.enabled=true.
```

---

### 2.2 Add Feign Error Decoder to All Services

**Gaps addressed:** GAP-ERR-003
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Implement a consistent Feign error decoder across all services with Feign clients in ts-java-spring-boot-internet-banking-microservices (fund-transfer-service, user-service, utility-payment-service). Create a shared CustomFeignErrorDecoder that: (1) Parses the ErrorResponse JSON from downstream services, (2) Maps HTTP 404 to EntityNotFoundException, (3) Maps HTTP 422 to a new ServiceException with the downstream error code, (4) Maps HTTP 5xx to a ServiceUnavailableException, (5) Logs the full error response at WARN level. Register this decoder in each service's CustomFeignClientConfiguration.
```

---

### 2.3 Create Shared Library Module

**Gaps addressed:** GAP-ORG-002, GAP-ORG-004
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Create a shared common library module in ts-java-spring-boot-internet-banking-microservices. (1) Create a new directory 'internet-banking-common' with its own build.gradle that produces a JAR (no Spring Boot plugin, just java-library). (2) Move these duplicated classes into the common module: BaseMapper, AuditAware, ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler (as a base class), AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder. (3) Create a root settings.gradle that includes all projects. (4) Have each service depend on the common module via 'implementation project(":internet-banking-common")'. (5) Remove the duplicated classes from each service.
```

---

### 2.4 Add Unit Tests for All Services

**Gaps addressed:** GAP-TEST-001
**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Add comprehensive unit tests to the fund-transfer-service, user-service, and utility-payment-service in ts-java-spring-boot-internet-banking-microservices. For each service, create tests for: (1) The service layer — mock the repository and Feign client, test all business logic paths including happy path, validation failures, and downstream errors. (2) The controller layer — use @WebMvcTest with MockMvc to test request/response serialization, validation, and error handling. Target at minimum: FundTransferService (5 tests), FundTransferController (4 tests), UserService (6 tests), UserController (5 tests), UtilityPaymentService (4 tests), UtilityPaymentController (3 tests). Use JUnit 5, Mockito, and AssertJ.
```

---

### 2.5 Add Integration Tests with Testcontainers

**Gaps addressed:** GAP-TEST-002
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add integration tests using Testcontainers for the core-banking-service and fund-transfer-service in ts-java-spring-boot-internet-banking-microservices. (1) Add testcontainers and testcontainers-mysql dependencies. (2) Create @SpringBootTest integration tests that spin up a real MySQL container and verify: database migration runs correctly, CRUD operations work end-to-end, transactional behavior (fund transfer atomicity). (3) For fund-transfer-service, use WireMock to stub the core-banking-service responses and test the full orchestration flow. (4) Configure a separate 'integration-test' Gradle task that runs these tests independently from unit tests.
```

---

### 2.6 Secure Internal Service Communication

**Gaps addressed:** GAP-SEC-006
**Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
Add authentication to internal service-to-service communication in ts-java-spring-boot-internet-banking-microservices. (1) Add spring-boot-starter-security to core-banking-service. (2) Configure it to require a valid service token (shared secret or mTLS) for all API endpoints except /actuator/health. (3) Add a Feign RequestInterceptor in each calling service (fund-transfer, user, utility-payment) that attaches the service token as a Bearer header. (4) The service token should be configurable via Spring Cloud Config (not hardcoded). (5) Alternatively, implement mutual TLS between services using self-signed certificates managed in the Docker Compose setup.
```

---

### 2.7 Add Prometheus Metrics

**Gaps addressed:** GAP-OBS-003
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add Prometheus metrics to all services in ts-java-spring-boot-internet-banking-microservices. (1) Add 'io.micrometer:micrometer-registry-prometheus' to each service's build.gradle. (2) Expose the /actuator/prometheus endpoint by adding 'management.endpoints.web.exposure.include=health,info,prometheus' to each application.yml. (3) Add custom business metrics using Micrometer's Counter and Timer: fund_transfer_total (counter with status tag), utility_payment_total (counter with provider tag), fund_transfer_duration_seconds (timer). (4) Add a prometheus.yml scrape config to the docker-compose setup with a Prometheus container.
```

---

### 2.8 Add Rate Limiting to API Gateway

**Gaps addressed:** GAP-RES-005
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Add rate limiting to the API Gateway in ts-java-spring-boot-internet-banking-microservices. (1) Add spring-boot-starter-data-redis-reactive and spring-cloud-gateway-filter-ratelimiter dependencies. (2) Add a Redis container to docker-compose.yml. (3) Configure RequestRateLimiter filter on all routes with: replenishRate=10, burstCapacity=20, using the principal name (from JWT) as the key resolver. (4) Add a separate, lower rate limit for the /user/api/v1/bank-users/register endpoint (replenishRate=3, burstCapacity=5) to prevent registration abuse. (5) Return HTTP 429 with a Retry-After header when rate limited.
```

---

## Phase 3: Polish (6–12 weeks)

Long-term architectural improvements for maintainability and operational excellence.

### 3.1 Implement Saga Pattern for Fund Transfers

**Gaps addressed:** GAP-RES-006
**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
Implement a saga/compensation pattern for the fund transfer flow in ts-java-spring-boot-internet-banking-microservices. Currently, if the fund-transfer-service saves to its local DB but the core-banking call fails (or vice versa), the system is left in an inconsistent state. (1) Introduce a state machine in FundTransferEntity: INITIATED → CORE_PROCESSING → COMPLETED / COMPENSATION_REQUIRED → COMPENSATED. (2) Add a compensation endpoint in core-banking-service that reverses a transaction by transactionId. (3) Add a scheduled job in fund-transfer-service that scans for CORE_PROCESSING records older than 30 seconds and triggers compensation. (4) Add idempotency keys to prevent duplicate transfer processing.
```

---

### 3.2 Add Contract Tests

**Gaps addressed:** GAP-TEST-003
**Severity:** High | **Effort:** Large

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services in ts-java-spring-boot-internet-banking-microservices. (1) Add spring-cloud-starter-contract-verifier to core-banking-service (producer). (2) Write contract DSL files (.groovy or .yml) for all core-banking API endpoints that are consumed by other services: GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}. (3) Add spring-cloud-starter-contract-stub-runner to fund-transfer-service, user-service, and utility-payment-service (consumers). (4) Write consumer-side tests that verify Feign clients work correctly against the generated stubs. (5) Configure the contract tests to run as part of the standard Gradle test task.
```

---

### 3.3 Multi-Project Gradle Build

**Gaps addressed:** GAP-ORG-001
**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
Convert ts-java-spring-boot-internet-banking-microservices from independent Gradle projects to a unified multi-project build. (1) Create a root settings.gradle that includes all service modules. (2) Create a root build.gradle with a subprojects {} block that defines shared: java sourceCompatibility=21, Spring Boot BOM version, Spring Cloud version, common dependencies (lombok, actuator, tracing), common test dependencies (JUnit 5, Mockito, H2). (3) Simplify each service's build.gradle to only declare its unique dependencies. (4) Add a gradle.properties with shared version variables. (5) Ensure all services still build independently with ./gradlew :core-banking-service:build syntax.
```

---

### 3.4 Add JaCoCo Test Coverage Enforcement

**Gaps addressed:** GAP-TEST-004
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add JaCoCo test coverage reporting and enforcement to all services in ts-java-spring-boot-internet-banking-microservices. (1) Add the JaCoCo Gradle plugin to each service's build.gradle. (2) Configure jacocoTestReport to generate HTML and XML reports. (3) Add jacocoTestCoverageVerification with minimum thresholds: line coverage >= 60% for service layer packages, branch coverage >= 50%. (4) Make the 'check' task depend on jacocoTestCoverageVerification so builds fail if coverage drops below threshold. (5) Add a root-level task that aggregates coverage reports across all services.
```

---

### 3.5 Add Dependency Vulnerability Scanning

**Gaps addressed:** GAP-SEC-005
**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
Add OWASP Dependency-Check to ts-java-spring-boot-internet-banking-microservices. (1) Add the 'org.owasp.dependencycheck' Gradle plugin to each service's build.gradle (or to the root build if multi-project is set up). (2) Configure it with failBuildOnCVSS=7 to fail builds on high-severity vulnerabilities. (3) Add a suppressions.xml file for any known false positives. (4) Create a root-level Gradle task 'dependencyCheckAggregate' that produces a single report across all modules. (5) Add a .github/dependabot.yml configuration for automated dependency update PRs (Gradle ecosystem).
```

---

### 3.6 Normalize Project Structure

**Gaps addressed:** GAP-ORG-003, GAP-API-004
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Normalize the package structure across all services in ts-java-spring-boot-internet-banking-microservices to follow a consistent pattern: com.javatodev.finance.{controller, service, model.entity, model.dto, model.mapper, repository, configuration, exception}. (1) In core-banking-service, move repository/ into model/repository/ to match other services. (2) Standardize Feign client packages to service/client/ across all services. (3) Rename the user-service endpoint '/api/v1/bank-users/update/{id}' to use PATCH on '/api/v1/bank-users/{id}' (RESTful convention — the HTTP method implies the action). (4) Standardize path variable naming to camelCase: {accountNumber} instead of {account_number}.
```

---

### 3.7 Add Health Indicators and Readiness Probes

**Gaps addressed:** GAP-OBS-002
**Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
Add custom health indicators and Kubernetes-ready probes to all services in ts-java-spring-boot-internet-banking-microservices. (1) In each service, configure actuator to expose liveness and readiness probe groups: management.endpoint.health.probes.enabled=true, management.health.livenessState.enabled=true, management.health.readinessState.enabled=true. (2) In user-service, add a custom HealthIndicator that checks Keycloak connectivity. (3) In services with Feign clients, add a health indicator that pings the core-banking-service /actuator/health endpoint. (4) Add healthcheck configurations to each service in docker-compose.yml using the /actuator/health/readiness endpoint.
```

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │  Phase 1          │  Phase 2          │
    │  (Do First)       │  (Plan Next)      │
    │                   │                   │
    │  • Input Valid.   │  • Circuit Breakers│
    │  • Error Handling │  • Shared Library  │
    │  • Timeouts       │  • Unit Tests     │
    │  • Credentials    │  • Integration    │
    │  • Retry Policies │    Tests          │
    │                   │  • Internal Auth  │
────┼───────────────────┼───────────────────┼──── EFFORT
    │                   │                   │
    │  Quick Fixes      │  Phase 3          │
    │  (Do Alongside)   │  (Long-term)      │
    │                   │                   │
    │  • OpenAPI Fix    │  • Saga Pattern   │
    │  • Keycloak Fix   │  • Contract Tests │
    │  • Logging        │  • Multi-Project  │
    │  • Pagination     │  • Coverage Enf.  │
    │                   │                   │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```

---

## Success Metrics

| Phase | Key Outcome | Measurable Target |
|-------|-------------|-------------------|
| Phase 1 | System stability | Zero unhandled exceptions reaching clients; all errors return structured JSON |
| Phase 1 | Security baseline | No secrets in source; all inputs validated |
| Phase 2 | Resilience | System survives single-service outages; P99 latency < 5s under failure |
| Phase 2 | Quality confidence | >60% line coverage; integration tests pass in CI |
| Phase 3 | Operational maturity | Full observability; automated vulnerability detection; contract-verified APIs |
