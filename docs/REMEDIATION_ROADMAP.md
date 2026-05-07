# Remediation Roadmap

This roadmap organizes the gaps identified in [`GAP_ANALYSIS.md`](./GAP_ANALYSIS.md) into three prioritized phases. Each item includes a sample Devin prompt to execute the remediation.

---

## Phase 1: Quick Wins (Critical & High Severity, Small Effort)

These items address security vulnerabilities and reliability risks with minimal code changes. Target completion: **1–2 weeks**.

---

### 1.1 Fix Exception Handler Information Leakage (GAP-ERR-02)

**Severity:** Critical | **Effort:** Small

Replace the generic `Exception.class` handler in all 4 `GlobalExceptionHandler` classes to return a safe generic message instead of leaking exception details.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, update the GlobalExceptionHandler class in all 4 business services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Change the generic Exception handler to return HTTP 500 with a generic ErrorResponse body: code="INTERNAL_ERROR", message="An unexpected error occurred. Please try again later." Log the full exception at ERROR level but do not include it in the response body. Open a PR.

---

### 1.2 Add Proper HTTP Status Codes (GAP-ERR-03)

**Severity:** High | **Effort:** Small

Map exception types to appropriate HTTP status codes in all `GlobalExceptionHandler` classes.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, update all GlobalExceptionHandler classes to return proper HTTP status codes: EntityNotFoundException → 404, InsufficientFundsException → 422, UserAlreadyRegisteredException → 409, InvalidEmailException → 400, InvalidBankingUserException → 404. Keep SimpleBankingGlobalException as 400. Add the generic Exception handler as 500. Open a PR.

---

### 1.3 Externalize Secrets from Source Code (GAP-SEC-01)

**Severity:** Critical | **Effort:** Small

Replace all hardcoded credentials with environment variables.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, replace all hardcoded passwords and credentials in docker-compose/docker-compose.yml, docker-compose/docker-compose-support-apps.yml, and docker-compose/mysql/Dockerfile with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD}, ${KEYCLOAK_ADMIN_PASSWORD}). Create a docker-compose/.env.example file documenting all required variables with placeholder values. Update the README.md with instructions to copy .env.example to .env and fill in values. Open a PR.

---

### 1.4 Remove Sensitive Data from Logs (GAP-SEC-05)

**Severity:** High | **Effort:** Small

Stop logging full request objects that contain account numbers and financial data.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, audit all log statements across all services that log request objects via toString(). Replace them with safe logging that only logs non-sensitive fields (e.g., log the transaction type and a masked account number like "****3001" instead of the full object). Fix the typo "utitlity" → "utility" in AccountController. Open a PR.

---

### 1.5 Add Feign Client Timeouts (GAP-RES-03)

**Severity:** High | **Effort:** Small

Configure explicit timeouts for all Feign client calls.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Feign client timeout configuration to the application.yml (or bootstrap.yml) of internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Set connect timeout to 5 seconds and read timeout to 10 seconds. Use spring.cloud.openfeign.client.config.default.connect-timeout and read-timeout properties. Open a PR.

---

### 1.6 Add Feign Retry Policies (GAP-RES-02)

**Severity:** High | **Effort:** Small

Configure retry for transient failures on Feign calls.

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add a Spring Retry or Resilience4j retry configuration for Feign clients in fund-transfer-service, utility-payment-service, and user-service. Configure max 3 attempts with exponential backoff (initial interval 500ms, multiplier 2, max interval 5s). Only retry on connection exceptions and 5xx responses, never on 4xx. Add the spring-retry or resilience4j-spring-boot3 dependency to build.gradle. Open a PR.

---

### 1.7 Add Feign Error Decoder to Remaining Services (GAP-ERR-04)

**Severity:** High | **Effort:** Small (user-service already has one to reference)

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, the internet-banking-user-service already has a CustomFeignErrorDecoder. Port this same pattern to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Create a CustomFeignErrorDecoder class in each service's configuration package that extracts the ErrorResponse from core-banking-service error responses and throws the appropriate SimpleBankingGlobalException. Register it in the existing CustomFeignClientConfiguration. Open a PR.

---

### 1.8 Configure Actuator Endpoints (GAP-OBS-01)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, configure Spring Boot Actuator for all 6 services. In each service's application.yml, add management.endpoints.web.exposure.include=health,info,metrics,env and management.endpoint.health.show-details=when-authorized. Add application info properties (version, description) under the info.app key. Open a PR.

---

### 1.9 Add Dependency Vulnerability Scanning (GAP-SEC-06)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add the OWASP Dependency-Check Gradle plugin (version 9.x) to all 6 services. Add id 'org.owasp.dependencycheck' to the plugins block and configure it to fail the build on CVSS score >= 7. Add a root-level script or Makefile target that runs dependencyCheckAnalyze across all services. Open a PR.

---

### 1.10 Fix OpenAPI Dependency (GAP-API-04)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, the core-banking-service, fund-transfer-service, user-service, and utility-payment-service all use springdoc-openapi-starter-webflux-ui but they are Spring MVC (servlet) applications, not WebFlux. Change the dependency to org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0 in all 4 build.gradle files. Verify the Swagger UI is accessible at /swagger-ui.html. Open a PR.

---

## Phase 2: Important Improvements (High/Medium Severity, Medium Effort)

These items significantly improve reliability, maintainability, and security posture. Target completion: **3–6 weeks**.

---

### 2.1 Add Input Validation (GAP-SEC-02)

**Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Bean Validation (jakarta.validation) to all request DTOs across all services. Add spring-boot-starter-validation dependency to each service's build.gradle. Annotate FundTransferRequest fields: fromAccount (@NotBlank), toAccount (@NotBlank), amount (@NotNull @Positive). Annotate UtilityPaymentRequest: providerId (@NotNull), amount (@NotNull @Positive), referenceNumber (@NotBlank), account (@NotBlank). Annotate User registration: email (@NotBlank @Email), identification (@NotBlank), password (@NotBlank @Size(min=8)). Add @Valid to all @RequestBody parameters in controllers. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler returning 400 with field-level errors. Open a PR.

---

### 2.2 Add Circuit Breakers (GAP-RES-01)

**Severity:** High | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers to all Feign client calls. Add io.github.resilience4j:resilience4j-spring-boot3 and io.github.resilience4j:resilience4j-feign dependencies to fund-transfer-service, utility-payment-service, and user-service build.gradle files. Configure a circuit breaker named "coreBanking" with: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. Apply @CircuitBreaker annotations on the service methods that call Feign clients, with fallback methods that update the local entity status to FAILED and return an appropriate error response. Open a PR.

---

### 2.3 Add Typed ResponseEntity and Pagination Wrappers (GAP-ERR-01, GAP-API-02)

**Severity:** High / Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, update all controller methods across all 4 business services to use typed ResponseEntity<T> instead of raw ResponseEntity. For paginated endpoints (readUsers, readFundTransfers, readPayments), return ResponseEntity<Page<T>> wrapping the full Page object (not just the content list). Update the corresponding service methods to return Page<T> instead of List<T>. Add @RequestParam defaults for page=0, size=20, and enforce a maximum page size of 100. Open a PR.

---

### 2.4 Create Shared Library for Common Code (GAP-ORG-02)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, create a new module called banking-common-lib. Move the following duplicated classes into it: BaseMapper, AuditAware, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter, and AuditConfig/AuditorAwareConfig. Publish it as a local Gradle dependency. Create a root settings.gradle that includes all 7 modules. Update each service's build.gradle to use implementation project(':banking-common-lib') and remove the duplicated classes. Open a PR.

---

### 2.5 Add Structured Logging (GAP-OBS-02)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add JSON structured logging to all services. Add net.logstash.logback:logstash-logback-encoder:7.4 dependency to each service. Create a shared logback-spring.xml configuration that outputs JSON format in the 'docker' profile and plain text in default profile. Include fields: timestamp, level, service-name, traceId, spanId, logger, message. Ensure Micrometer trace context is propagated into log MDC. Open a PR.

---

### 2.6 Add Rate Limiting at API Gateway (GAP-SEC-04)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add rate limiting to the internet-banking-api-gateway using Spring Cloud Gateway's built-in RequestRateLimiter filter with Redis or an in-memory rate limiter. Configure: 10 requests/second for authenticated users, 2 requests/second for unauthenticated endpoints (user registration). Add spring-boot-starter-data-redis-reactive dependency and configure the rate limiter in the gateway route configuration. If Redis is not available, use Bucket4j as an alternative with in-memory storage. Open a PR.

---

### 2.7 Add Prometheus Metrics (GAP-OBS-04)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics export to all 6 services. Add io.micrometer:micrometer-registry-prometheus dependency to each build.gradle. Configure management.endpoints.web.exposure.include to include prometheus. Add management.metrics.tags.application=${spring.application.name} for service identification. Verify /actuator/prometheus endpoint returns metrics. Open a PR.

---

### 2.8 Add Custom Health Checks (GAP-OBS-03)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add custom health indicators: (1) In user-service, add a KeycloakHealthIndicator that checks Keycloak connectivity via the admin client. (2) In fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator that calls GET /api/v1/account/bank-account/100015003000 on core-banking-service and reports UP/DOWN. (3) In api-gateway, add health indicators for each downstream service using the Eureka client. Register all health indicators as Spring beans. Open a PR.

---

### 2.9 Add Database Connection Pool Configuration (GAP-RES-06)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add explicit HikariCP connection pool configuration to all 4 database-backed services via their application.yml or Spring Cloud Config. Set: maximum-pool-size=10, minimum-idle=5, idle-timeout=300000, max-lifetime=600000, connection-timeout=30000. Add connection test query: SELECT 1. Open a PR.

---

## Phase 3: Polish & Long-Term Quality (Medium/Low Severity, Large Effort)

These items elevate the codebase to production-grade quality. Target completion: **2–3 months**.

---

### 3.1 Add Comprehensive Unit Tests (GAP-TEST-01)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add unit tests for all service and controller classes across all 4 business services. Target minimum 80% line coverage. For each service: (1) Add controller tests using MockMvc (or WebTestClient for gateway) that verify request mapping, validation, response status codes, and response bodies. (2) Add service layer tests with Mockito for all business logic paths including error cases. (3) Add mapper tests verifying entity-to-DTO and DTO-to-entity conversion. Use the existing core-banking-service tests as a reference pattern. Add JaCoCo plugin to all build.gradle files with a minimum coverage threshold of 80%. Open a PR.

---

### 3.2 Add Integration Tests (GAP-TEST-02)

**Severity:** High | **Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers for MySQL. For each database-backed service: (1) Create a base test class that starts a MySQL Testcontainer and configures the datasource. (2) Add repository integration tests that verify CRUD operations against a real database. (3) Add service integration tests that verify end-to-end flows with real database persistence. For the user-service, mock the Keycloak client and Feign client. For fund-transfer and utility-payment services, use WireMock to stub core-banking-service responses. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies. Open a PR.

---

### 3.3 Add Contract Tests (GAP-TEST-03)

**Severity:** Medium | **Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests between consumer and provider services. For core-banking-service (provider): add spring-cloud-contract-verifier plugin and define contracts for all endpoints consumed by other services (fund-transfer, utility-payment, user-service). Generate provider verification tests. For each consumer service: add spring-cloud-contract-stub-runner and write consumer-side contract tests that verify Feign clients work correctly against the generated stubs. Open a PR.

---

### 3.4 Implement Saga Pattern for Distributed Transactions (GAP-RES-05)

**Severity:** Critical | **Effort:** Large

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, implement a choreography-based saga pattern for the fund transfer flow to handle distributed transaction failures. (1) Add a status field COMPENSATION_REQUIRED to TransactionStatus enum. (2) In FundTransferService, wrap the Feign call in a try-catch: if the call succeeds but the local DB update fails, call a new compensation endpoint on core-banking-service to reverse the transaction. (3) Add a POST /api/v1/transaction/compensate/{transactionId} endpoint to core-banking-service that reverses a fund transfer by creating offsetting transactions. (4) Add idempotency keys: include a unique requestId in FundTransferRequest, store it in fund_transfer table, and reject duplicates in core-banking-service. (5) Add a scheduled job that scans for PENDING transfers older than 5 minutes and either retries or compensates them. Apply the same pattern to utility-payment-service. Open a PR.

---

### 3.5 Implement Fallback Behavior (GAP-RES-04)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add fallback methods for all Feign client calls in fund-transfer-service, utility-payment-service, and user-service. When core-banking-service is unavailable: (1) Fund transfer: save the transfer with status FAILED, return a response indicating the transfer could not be completed and the user should retry later. (2) Utility payment: same pattern with FAILED status. (3) User registration: return an error indicating the system is temporarily unavailable. Implement fallbacks as methods in the service classes annotated with @CircuitBreaker fallbackMethod or as Feign fallback factories. Open a PR.

---

### 3.6 Create Multi-Project Gradle Build (GAP-ORG-01)

**Severity:** Medium | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, create a root-level settings.gradle that includes all 6 services (and banking-common-lib if it exists). Create a root build.gradle that defines shared configuration in a subprojects block: common repositories (mavenCentral), Java 21 sourceCompatibility, springCloudVersion, springBootVersion, and shared test configuration. Remove duplicated version declarations from each service's build.gradle, inheriting from the root instead. Ensure ./gradlew build from root builds all services. Open a PR.

---

### 3.7 Standardize Package Structure (GAP-ORG-03)

**Severity:** Low | **Effort:** Small

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, standardize the package structure across all services to follow this convention: com.javatodev.finance.{controller, service, repository, model.entity, model.dto, model.mapper, configuration, exception}. Move user-service's model/repository/UserRepository to repository/UserRepository. Rename fund-transfer-service's model/repository to just repository. Standardize Feign client package to service/rest/ across all services. Update all imports. Open a PR.

---

### 3.8 Add CI/CD Pipeline

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, create a GitHub Actions CI/CD pipeline in .github/workflows/ci.yml. The pipeline should: (1) Trigger on push to main and pull requests. (2) Set up Java 21 with Gradle caching. (3) Run ./gradlew build for each service (or from root if multi-project build exists). (4) Run tests with JaCoCo coverage report. (5) Run OWASP dependency check. (6) Build Docker images for each service. (7) Push images to GitHub Container Registry on main branch merges. Add a badge to README.md showing build status. Open a PR.

---

### 3.9 Add API Versioning Strategy (GAP-API-01)

**Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, document and implement a formal API versioning strategy. Create a docs/API_VERSIONING.md that defines: URL path versioning (/api/v1/, /api/v2/), backward compatibility rules, and deprecation policy (minimum 6 months notice). Add a custom @ApiVersion annotation that can be applied to controllers. Add response headers X-API-Version and X-API-Deprecated-By to all responses. Open a PR.

---

### 3.10 Add Filtering and Search to List Endpoints (GAP-API-05)

**Severity:** Low | **Effort:** Medium

**Devin Prompt:**
> In ts-java-spring-boot-internet-banking-microservices, add filtering capabilities to all list/paginated endpoints. For fund transfers: add optional query params status, fromAccount, toAccount, dateFrom, dateTo. For utility payments: add optional params status, account, providerId, dateFrom, dateTo. For users (core-banking): add optional params email, identificationNumber. For users (user-service): add optional param status. Implement using Spring Data JPA Specifications or QueryDSL. Ensure filters compose (AND logic). Open a PR.

---

## Phase Summary

| Phase | Items | Total Effort | Key Outcomes |
|-------|-------|-------------|--------------|
| **Phase 1** | 10 items | ~2 weeks | Security vulnerabilities fixed, resilience basics in place, observability configured |
| **Phase 2** | 9 items | ~4–6 weeks | Input validation, circuit breakers, shared library, structured logging, metrics |
| **Phase 3** | 10 items | ~6–10 weeks | Comprehensive testing, saga pattern, CI/CD, code organization polish |

---

## Priority Matrix

```
                    Low Effort ◄──────────────────► High Effort
                    │                                │
   Critical ───────┤ GAP-ERR-02, GAP-SEC-01         │ GAP-RES-05
                    │                                │
                    │                                │
   High ───────────┤ GAP-ERR-03, GAP-SEC-05,        │ GAP-TEST-01, GAP-TEST-02
                    │ GAP-RES-02, GAP-RES-03         │
                    │ GAP-ERR-04                     │
                    │                                │
   Medium ─────────┤ GAP-OBS-01, GAP-OBS-04,        │ GAP-ORG-02, GAP-OBS-02,
                    │ GAP-API-04, GAP-SEC-06,        │ GAP-SEC-04, GAP-TEST-03
                    │ GAP-RES-06, GAP-OBS-03         │
                    │                                │
   Low ────────────┤ GAP-ORG-03, GAP-OBS-05,        │ GAP-API-05
                    │ GAP-API-03                     │
                    │                                │
```
