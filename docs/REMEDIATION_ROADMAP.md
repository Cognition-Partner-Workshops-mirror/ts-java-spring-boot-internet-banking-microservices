# Remediation Roadmap

## Phase 1: Quick Wins (Critical fixes, Small effort)

### 1. Fix balance calculation bugs

Fix double-subtraction in `TransactionService.internalFundTransfer()` (lines 91, 100) and `TransactionService.utilPayment()` (line 64) in `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`

**Devin prompt:** "Fix the balance calculation bugs in core-banking-service TransactionService. In internalFundTransfer(), lines 91 and 100 set availableBalance by subtracting from the already-reduced actualBalance — they should set availableBalance equal to actualBalance. Same bug in utilPayment() line 64. Add unit tests for the corrected calculations."

### 2. Fix UtilityPaymentRepository ID type

Change `JpaRepository<UtilityPaymentEntity, UtilityPayment>` to `JpaRepository<UtilityPaymentEntity, Long>` in `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/repository/UtilityPaymentRepository.java`

**Devin prompt:** "Fix UtilityPaymentRepository in internet-banking-utility-payment-service to use Long as the ID type instead of UtilityPayment DTO class."

### 3. Fix TransactionEntity relationship

Change `@OneToOne(cascade = CascadeType.ALL)` to `@ManyToOne` on the account field in `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`

**Devin prompt:** "Fix TransactionEntity in core-banking-service: change the account field from @OneToOne(cascade = CascadeType.ALL) to @ManyToOne (no cascade) since one account can have many transactions."

### 4. Fix exception handler information leakage

Update all 4 `GlobalExceptionHandler` classes to not expose raw exception details. Return proper HTTP status codes (404 for EntityNotFoundException, 409 for UserAlreadyRegisteredException, 500 for unexpected errors).

**Devin prompt:** "Update GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Remove the catch-all handler that returns 'Exception occur inside API' + raw exception. Instead return a generic 500 error with ErrorResponse format. Also make EntityNotFoundException return 404, and add proper HTTP status codes for each exception type."

### 5. Add input validation

Add Jakarta Validation annotations to all request DTOs and `@Valid` to controller parameters.

**Devin prompt:** "Add Jakarta Bean Validation to all request DTOs across all services. Add @NotNull, @NotBlank, @Positive, @Size annotations as appropriate. For FundTransferRequest: fromAccount and toAccount should be @NotBlank, amount should be @NotNull @Positive. For UtilityPaymentRequest: providerId @NotNull, amount @NotNull @Positive, account @NotBlank. For User registration: email @NotBlank @Email, password @NotBlank, identification @NotBlank. Add @Valid annotation to all controller method parameters that accept request bodies. Add MethodArgumentNotValidException handler to each GlobalExceptionHandler."

### 6. Add Feign timeout configuration

Add connection and read timeouts to all Feign clients.

**Devin prompt:** "Add Feign client timeout configuration to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Set connectTimeout to 5000ms and readTimeout to 10000ms. This can be done via application.yml properties: spring.cloud.openfeign.client.config.default.connectTimeout and readTimeout."

### 7. Fix OpenAPI dependency

Change `springdoc-openapi-starter-webflux-ui` to `springdoc-openapi-starter-webmvc-ui` in core-banking-service, user-service, fund-transfer-service, and utility-payment-service build.gradle files (these are WebMVC, not WebFlux services).

**Devin prompt:** "In build.gradle for core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service, change the springdoc dependency from 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' to 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' since these are Spring WebMVC services, not WebFlux."

### 8. Restrict actuator endpoints

Update SecurityConfiguration in the gateway to restrict actuator access.

**Devin prompt:** "Update SecurityConfiguration in internet-banking-api-gateway to restrict actuator endpoints. Only allow /actuator/health and /actuator/info publicly. All other actuator endpoints should require authentication."

## Phase 2: Important (High-severity, Medium effort)

### 9. Add circuit breakers and retry policies

Add Resilience4j to all Feign client services.

**Devin prompt:** "Add Resilience4j circuit breaker and retry support to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Add spring-cloud-starter-circuitbreaker-resilience4j dependency. Configure circuit breakers for each Feign client with sensible defaults (failureRateThreshold=50, waitDurationInOpenState=30s, slidingWindowSize=10). Add retry config with maxAttempts=3 and waitDuration=1s. Add fallback methods that return meaningful error responses."

### 10. Extract shared library

Create a shared module for duplicated code.

**Devin prompt:** "Create a new Gradle subproject called 'banking-common' containing the shared classes that are currently copy-pasted across services: SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, GlobalErrorCode, AuditAware, BaseMapper, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter. Update all services to depend on this shared module. Convert the project to a Gradle multi-project build with a root settings.gradle."

### 11. Add unit tests for all services

Currently only core-banking-service has tests.

**Devin prompt:** "Add comprehensive unit tests for internet-banking-user-service (UserService, KeycloakUserService), internet-banking-fund-transfer-service (FundTransferService), and internet-banking-utility-payment-service (UtilityPaymentService). Mock all external dependencies (Feign clients, repositories, KeycloakManager). Test happy paths, error cases, and edge cases. Aim for >80% line coverage on service classes."

### 12. Externalize secrets

Remove hardcoded credentials from source code.

**Devin prompt:** "Remove all hardcoded credentials from the codebase. In docker-compose.yml, replace hardcoded passwords with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD}). Create a .env.example file documenting all required environment variables. Update the MySQL Dockerfile to not hardcode the root password. Add .env to .gitignore. Document the setup in README.md."

### 13. Add idempotency to fund transfers

Prevent duplicate transfers on retry.

**Devin prompt:** "Add idempotency support to the fund transfer flow. Add an idempotencyKey field to FundTransferRequest and FundTransferEntity. Before processing a transfer, check if a transfer with the same idempotency key already exists. If it does, return the existing result. Add a unique constraint on the idempotencyKey column."

### 14. Add Feign error decoders to all services

Only user-service has one currently.

**Devin prompt:** "Add CustomFeignErrorDecoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service, similar to the one in internet-banking-user-service. The decoder should parse ErrorResponse from the core-banking-service and throw appropriate SimpleBankingGlobalException subclasses. Update the Feign client configurations to use the error decoder."

## Phase 3: Polish (Medium/Low severity, various effort)

### 15. Add CI/CD pipeline

Create GitHub Actions workflows.

**Devin prompt:** "Create a GitHub Actions CI pipeline in .github/workflows/ci.yml that builds all 7 services, runs tests, and reports test results. Use a matrix strategy to build each service in parallel. Include steps for: checkout, setup Java 21, Gradle build, test, and upload test reports."

### 16. Add structured logging

Configure JSON logging with correlation IDs.

**Devin prompt:** "Add structured JSON logging to all services. Add logstash-logback-encoder dependency and configure logback-spring.xml in each service to output JSON format. Include traceId and spanId from Micrometer in log output via MDC."

### 17. Add integration tests

Test service interactions with Testcontainers.

**Devin prompt:** "Add integration tests to core-banking-service using Testcontainers for MySQL. Test the full flow of fund transfer and utility payment through the controller layer using @SpringBootTest and MockMvc. Verify database state after operations."

### 18. Add Prometheus metrics

The README mentions Prometheus but it's not configured.

**Devin prompt:** "Add Prometheus metrics support to all services. Add micrometer-registry-prometheus dependency to each build.gradle. Expose /actuator/prometheus endpoint. Add custom business metrics: fund transfer count, payment count, transfer amount histogram."

### 19. Standardize API response envelope

Create a consistent response wrapper.

**Devin prompt:** "Create a standard API response envelope class in the shared library with fields: success (boolean), data (generic), error (ErrorResponse), timestamp, path. Update all controllers to use this envelope. Update GlobalExceptionHandler to return errors in the same envelope format."

### 20. Add pagination response metadata

Return total count and page info.

**Devin prompt:** "Update all paginated endpoints across all services to return pagination metadata (totalElements, totalPages, currentPage, pageSize) alongside the data list. Create a PagedResponse wrapper class in the shared library."
