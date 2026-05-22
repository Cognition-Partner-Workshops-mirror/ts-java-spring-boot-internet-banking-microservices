# Remediation Roadmap — Internet Banking Microservices

This roadmap prioritizes gaps into three phases based on severity and effort. Each item includes an actionable Devin prompt for direct execution.

---

## Phase 1: Quick Wins (Critical/High severity, Small effort)

### 1. Fix balance calculation bug in TransactionService.utilPayment

- **File**: `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java` lines 63-64
- **Problem**: The `availableBalance` is double-subtracting. `actualBalance` is already modified on line 63 before being used on line 64 to compute `availableBalance`.
- **Fix**: Set `availableBalance` before modifying `actualBalance`, or compute both from the original balance.

**Devin prompt:**
> "Fix the balance calculation bug in core-banking-service TransactionService.utilPayment() method. Lines 63-64 double-subtract the amount from availableBalance because actualBalance is already modified on line 63 before being used on line 64. Both actualBalance and availableBalance should be reduced by the payment amount from their original values. Add a unit test to verify correct balance after utility payment."

---

### 2. Add @Valid annotations to all controller @RequestBody parameters

- **Files**: All 6 controller files across the 4 domain services
- **Problem**: No input validation exists anywhere — any malformed request is accepted.

**Devin prompt:**
> "Add Bean Validation to all REST endpoints across all services. Add @Valid to every @RequestBody parameter in every controller. Add @NotNull, @NotBlank, @Positive, @Size constraints to all request DTO fields: FundTransferRequest (fromAccount, toAccount, amount), UtilityPaymentRequest (providerId, amount, referenceNumber, account), User (email, password, identification). Add the spring-boot-starter-validation dependency to each service's build.gradle. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns a 422 with field-level error details."

---

### 3. Fix error handling — correct HTTP status codes

- **Files**: `GlobalExceptionHandler.java` in all 4 services
- **Problem**: EntityNotFoundException returns 400 (should be 404), generic Exception returns 400 (should be 500), raw strings are leaked to clients.

**Devin prompt:**
> "Fix GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service). Map EntityNotFoundException to HTTP 404, InsufficientFundsException to HTTP 422, SimpleBankingGlobalException to HTTP 400, and generic Exception to HTTP 500. Remove the raw string response from the generic handler — return a proper ErrorResponse object. Never leak exception details to clients in production."

---

### 4. Remove hardcoded secrets from docker-compose files

- **Files**: `docker-compose/docker-compose.yml`, `docker-compose/docker-compose-support-apps.yml`, `docker-compose/mysql/privileges.sql`
- **Problem**: Database passwords, Keycloak admin credentials hardcoded in version control.

**Devin prompt:**
> "Replace all hardcoded passwords in docker-compose/docker-compose.yml, docker-compose/docker-compose-support-apps.yml, and docker-compose/mysql/privileges.sql with environment variable references using Docker Compose variable substitution syntax. Create a .env.example file documenting all required variables. Add .env to .gitignore."

---

### 5. Fix wrong SpringDoc dependency — use webmvc instead of webflux

- **Files**: `build.gradle` in core-banking-service, fund-transfer-service, user-service, utility-payment-service
- **Problem**: Spring MVC services incorrectly use the WebFlux SpringDoc starter.

**Devin prompt:**
> "In the build.gradle files for core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service, replace the dependency 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'. These services use Spring MVC (spring-boot-starter-web), not WebFlux."

---

### 6. Restrict Actuator endpoint exposure

- **Problem**: Actuator endpoints are publicly accessible via `permitAll` in gateway security config — exposes internal application details.

**Devin prompt:**
> "Configure Actuator endpoints across all services to only expose health, info, and prometheus endpoints publicly. In each service's application.yml (or the config server's centralized config), add management.endpoints.web.exposure.include=health,info,prometheus and management.endpoint.health.show-details=when-authorized. Update the gateway SecurityConfiguration to restrict /actuator/** paths to only /actuator/health being permitAll."

---

### 7. Add log correlation IDs

- **Problem**: Log output does not include traceId/spanId despite Micrometer Brave being on the classpath.

**Devin prompt:**
> "Configure structured logging with trace correlation across all services. Add the logging.pattern.level property to include traceId and spanId in log output. Since the project already uses micrometer-tracing-bridge-brave, add the following to each service's application.yml (or centralized config): logging.pattern.level: '%5p [${spring.application.name},%X{traceId},%X{spanId}]'. This ensures all log lines include distributed tracing context."

---

## Phase 2: Important (High severity, Medium effort)

### 8. Extract shared library module

- **Problem**: BaseMapper, AuditAware, GlobalExceptionHandler, AppAuthUserFilter, and other classes are duplicated across 4 services.

**Devin prompt:**
> "Create a new shared Gradle module called 'banking-common' at the repository root. Move the following duplicated classes into it: BaseMapper, AuditAware, AuditorAwareConfig, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler (as an abstract base), AppAuthUserFilter, ApiRequestContextHolder. Set up a settings.gradle at the root to create a multi-module Gradle build. Update each service's build.gradle to depend on banking-common. Remove the duplicated classes from each service."

---

### 9. Add circuit breakers and timeouts to Feign clients

- **Problem**: No circuit breakers, timeouts, or retry policies on any inter-service communication. If core-banking-service is down, all dependent services fail with unhandled exceptions.

**Devin prompt:**
> "Add Spring Cloud Circuit Breaker with Resilience4j to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Add 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j' to each build.gradle. Configure default Feign client timeouts (connectTimeout: 5000, readTimeout: 10000) and circuit breaker settings (slidingWindowSize: 10, failureRateThreshold: 50, waitDurationInOpenState: 30s) in each service's application.yml. Add @CircuitBreaker annotations with fallback methods to the Feign client interfaces or service classes."

---

### 10. Add unit tests for domain services

- **Problem**: Only core-banking-service has unit tests. No tests exist for user-service, fund-transfer-service, or utility-payment-service business logic.

**Devin prompt:**
> "Add comprehensive unit tests for internet-banking-fund-transfer-service FundTransferService, internet-banking-user-service UserService, and internet-banking-utility-payment-service UtilityPaymentService. Use JUnit 5 and Mockito. For FundTransferService: test successful transfer flow, test Feign client failure handling, test that PENDING status is set before Feign call. For UserService: test createUser happy path, test duplicate email rejection, test NIC not found, test updateUser approval flow with Keycloak integration. For UtilityPaymentService: test successful payment, test Feign failure handling. Mock all external dependencies (repositories, Feign clients, KeycloakUserService)."

---

### 11. Add failure handling and compensating transactions

- **Problem**: If the Feign call fails after the local entity is saved as PENDING/PROCESSING, it stays in that status forever with no recovery.

**Devin prompt:**
> "Add proper error handling to FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(). Wrap the Feign client calls in try-catch blocks. On failure, update the local entity status to FAILED instead of leaving it as PENDING/PROCESSING. In FundTransferService, catch exceptions from bankingCoreFeignClient.fundTransfer(), set entity status to FAILED, save it, and rethrow a descriptive exception. Apply the same pattern to UtilityPaymentService. Add unit tests verifying the FAILED status is persisted on downstream failures."

---

### 12. Secure downstream services

- **Problem**: JWT validation only happens at the gateway. Downstream services trust the spoofable `X-Auth-Id` header without verification.

**Devin prompt:**
> "Add authentication to the downstream domain services (core-banking-service, user-service, fund-transfer-service, utility-payment-service) so they cannot be accessed directly without going through the gateway. Add spring-boot-starter-security to core-banking-service's build.gradle. Configure it to validate the X-Auth-Id header or implement a shared API key mechanism for service-to-service communication. At minimum, configure each service to only accept requests from the gateway's network or validate a shared secret header."

---

### 13. Add idempotency to fund transfers

- **Problem**: No idempotency key — duplicate POST requests can result in duplicate fund transfers.

**Devin prompt:**
> "Add idempotency support to the fund transfer flow. Add an idempotencyKey field to FundTransferRequest. In FundTransferService.fundTransfer(), check if a transfer with the same idempotencyKey already exists before processing. If it does, return the existing result. Add a unique constraint on the idempotencyKey column in the fund_transfer table. Add a Flyway migration for the schema change. Add unit tests for the idempotency check."

---

## Phase 3: Polish (Medium/Low severity)

### 14. Add CI/CD pipeline with GitHub Actions

- **Problem**: No CI/CD pipeline exists in the repository.

**Devin prompt:**
> "Create a GitHub Actions CI pipeline in .github/workflows/ci.yml. The pipeline should: checkout code, set up Java 21, run './gradlew build' for each service (or configure a root settings.gradle for unified build), run tests, and report test results. Add a step to build Docker images for each service. Add a badge to README.md."

---

### 15. Add pagination defaults and limits

- **Problem**: Clients can request unlimited page sizes; no default pagination configuration.

**Devin prompt:**
> "Add default and maximum pagination configuration across all services. In each service's application.yml, add spring.data.web.pageable.default-page-size=20 and spring.data.web.pageable.max-page-size=100. Add type parameters to all raw ResponseEntity returns in controllers. Update controller methods to return proper Page<T> wrapper objects instead of List<T>."

---

### 16. Add contract tests between services

- **Problem**: No contract tests ensure that upstream services' assumptions about downstream APIs remain valid.

**Devin prompt:**
> "Add Spring Cloud Contract tests for the core-banking-service API. Define contracts for the fund-transfer and utility-payment endpoints that the upstream services consume. Add the spring-cloud-starter-contract-verifier plugin to core-banking-service's build.gradle. Create contract DSL files in src/test/resources/contracts/ for: GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}. Generate stubs that fund-transfer-service and utility-payment-service can use in their tests."

---

### 17. Add structured logging

- **Problem**: Using default Spring Boot logging with ad-hoc `log.info()` calls; no JSON output for log aggregation systems.

**Devin prompt:**
> "Add structured JSON logging to all services. Add 'net.logstash.logback:logstash-logback-encoder:7.4' dependency to each service's build.gradle. Create a shared logback-spring.xml configuration that outputs JSON-formatted logs with fields: timestamp, level, service, traceId, spanId, message, logger, thread. Place it in each service's src/main/resources/ directory."

---

### 18. Add custom health indicators and business metrics

- **Problem**: No custom health checks or business metrics beyond default Actuator endpoints.

**Devin prompt:**
> "Add custom health indicators and Micrometer metrics to each domain service. In core-banking-service, add a custom HealthIndicator that checks MySQL connectivity. In user-service, add one that checks Keycloak reachability. Add custom Micrometer counters for: fund transfers initiated/completed/failed, utility payments processed, user registrations. Expose these via the /actuator/prometheus endpoint."

---

### 19. Aggregate OpenAPI documentation at gateway

- **Problem**: No centralized API documentation; each service has its own Swagger UI.

**Devin prompt:**
> "Configure OpenAPI documentation aggregation at the API gateway level. Add springdoc-openapi-starter-webflux-ui to the gateway's build.gradle (it already uses WebFlux). Configure springdoc to aggregate the OpenAPI specs from all downstream services using their Eureka service names. Add springdoc.swagger-ui.urls configuration entries for each service."

---

### 20. Standardize DTO package structure

- **Problem**: Inconsistent naming conventions across services — `model.dto.request/response` vs `model.rest.request/response` vs `repository/` vs `model/repository/`.

**Devin prompt:**
> "Standardize the package structure for DTOs across all services. Currently core-banking uses model.dto.request/response, utility-payment uses model.rest.request/response, and others vary. Adopt a consistent pattern: model.dto for all DTOs, model.dto.request for request objects, model.dto.response for response objects. Rename packages in internet-banking-utility-payment-service to match. Also standardize repository package location — move fund-transfer-service's model.repository.FundTransferRepository to repository.FundTransferRepository to match other services."

---

## Implementation Priority Matrix

| Phase | Items | Estimated Total Effort | Impact |
|-------|-------|------------------------|--------|
| Phase 1 | 7 items | ~2-3 days | Fixes critical bugs, security holes, and correctness issues |
| Phase 2 | 6 items | ~1-2 weeks | Improves reliability, testability, and security posture |
| Phase 3 | 7 items | ~2-3 weeks | Polishes developer experience, documentation, and operations |

---

## Execution Notes

- Phase 1 items can be executed independently and in parallel.
- Phase 2 item #8 (shared library) should be done before #10 (unit tests) to avoid testing duplicated code.
- Phase 2 item #9 (circuit breakers) and #11 (failure handling) are complementary and should be done together.
- Phase 3 items are independent and can be prioritized based on team preference.
- All Devin prompts above are self-contained and can be executed as individual tasks.
