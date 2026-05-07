# Remediation Roadmap

This document provides a phased remediation plan for the internet banking microservices platform based on the findings in the [Gap Analysis](./GAP_ANALYSIS.md). Each item includes a description and a ready-to-use Devin prompt.

---

## Phase 1: Quick Wins (Small effort, high impact)

### 1.1 Fix double-subtraction balance bug

**Location:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java` lines 63-64 and 90-91.

**Problem:** `availableBalance` is computed from already-debited `actualBalance`, causing double subtraction.

**Fix:** Store the new balance in a variable before setting both fields.

> **Devin prompt:** "Fix the balance calculation bug in core-banking-service TransactionService. In both utilPayment() and internalFundTransfer(), the availableBalance is being double-subtracted because it uses actualBalance after it was already reduced. Calculate the new balance once and set both fields from that value."

---

### 1.2 Add input validation

**Problem:** No `@Valid` or Bean Validation annotations anywhere in the codebase. Null/negative values cause NPEs.

**Fix:** Add `spring-boot-starter-validation` dependency and apply constraints to all request DTOs.

> **Devin prompt:** "Add Bean Validation to all 4 business services. Add spring-boot-starter-validation to each build.gradle. Add @Valid annotations to all @RequestBody parameters in controllers. Add @NotNull, @NotBlank, @NotEmpty, @Positive constraints to all request DTO fields (FundTransferRequest, UtilityPaymentRequest, User, UserUpdateRequest). Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details."

---

### 1.3 Fix error handling

**Problem:** All exceptions map to 400 Bad Request. Stack traces leak to clients.

**Fix:** Map specific exceptions to proper HTTP status codes.

> **Devin prompt:** "Fix GlobalExceptionHandler in all 4 services. Map EntityNotFoundException to 404 Not Found. Map InsufficientFundsException to 422 Unprocessable Entity. Change the generic Exception handler to return 500 Internal Server Error with a safe message (no stack trace). Ensure all error responses use the ErrorResponse DTO format consistently."

---

### 1.4 Fix OpenAPI dependency

**Problem:** MVC services incorrectly use the WebFlux variant of springdoc-openapi.

**Fix:** Replace with the correct WebMVC variant.

> **Devin prompt:** "In all 4 business service build.gradle files, replace the dependency 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0' since these are Spring MVC (not WebFlux) applications."

---

### 1.5 Fix TransactionEntity relationship

**Problem:** `@OneToOne(cascade = CascadeType.ALL)` on the `account` field is incorrect -- multiple transactions reference the same bank account.

**Fix:** Change to `@ManyToOne` without cascade.

> **Devin prompt:** "In core-banking-service TransactionEntity.java, change the @OneToOne(cascade = CascadeType.ALL) annotation on the 'account' field to @ManyToOne (no cascade). Multiple transactions reference the same bank account, so OneToOne is incorrect and CascadeType.ALL is dangerous as it could cascade deletes to the account."

---

### 1.6 Remove password from User response

**Problem:** The `User` DTO in user-service includes a `password` field that could be serialized in API responses.

**Fix:** Mark as write-only.

> **Devin prompt:** "In internet-banking-user-service User.java DTO, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field so it is accepted in requests but never serialized in responses."

---

### 1.7 Fix pagination responses

**Problem:** Paginated endpoints return `List<T>` instead of `Page<T>`, losing pagination metadata.

**Fix:** Return `Page<T>` or a custom wrapper with metadata.

> **Devin prompt:** "Update all paginated endpoints across all services to return Page<T> (or a custom PageResponse wrapper) instead of List<T>. This applies to: core-banking UserController.readUsers(), user-service UserController.readUsers(), fund-transfer FundTransferController.readFundTransfers(), utility-payment UtilityPaymentController.readPayments(). The response should include totalElements, totalPages, pageNumber, and pageSize."

---

### 1.8 Mask sensitive data in logs

**Problem:** `request.toString()` in user registration controller logs the user's password.

**Fix:** Exclude sensitive fields from logging.

> **Devin prompt:** "Audit all log statements across all services for sensitive data exposure. In internet-banking-user-service UserController.createUser(), the request.toString() call will log the user's password. Either exclude the password field from toString() using @ToString.Exclude on the password field in User.java, or log only non-sensitive fields."

---

## Phase 2: Important (Medium effort, high impact)

### 2.1 Add resilience patterns

**Problem:** No circuit breakers, retries, or timeouts on Feign clients. Core-banking failure cascades to all services.

> **Devin prompt:** "Add Resilience4j to internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service. Add spring-cloud-starter-circuitbreaker-resilience4j dependency. Configure circuit breakers on all Feign client calls to core-banking-service with: failure-rate-threshold=50, wait-duration-in-open-state=30s, sliding-window-size=10. Add timeout configuration: connectTimeout=5000ms, readTimeout=10000ms. Add retry configuration: maxAttempts=3, waitDuration=1s, retryExceptions=IOException,TimeoutException."

---

### 2.2 Create shared library

**Problem:** Exception classes, error handling, audit config, base mapper, DTOs are copy-pasted across 3-4 services.

> **Devin prompt:** "Create a new Gradle module called 'banking-common' at the repository root. Move the following shared classes into it: SimpleBankingGlobalException, GlobalExceptionHandler, ErrorResponse, GlobalErrorCode, EntityNotFoundException, BaseMapper, AuditAware, AuditorAwareConfig, AuditConfig, AppAuthUserFilter, ApiRequestContextHolder. Update all 4 business services to depend on this shared module. Convert the project to a Gradle multi-project build with a root settings.gradle."

---

### 2.3 Add authentication to downstream services

**Problem:** Only the API Gateway enforces OAuth2. Downstream services are directly accessible without authentication.

> **Devin prompt:** "Secure the downstream microservices so they cannot be accessed directly without authentication. Option: Add spring-boot-starter-oauth2-resource-server to core-banking-service, user-service, fund-transfer-service, and utility-payment-service. Configure JWT validation against Keycloak. Alternatively, update docker-compose.yml to not expose ports 8083, 8084, 8085, 8092 to the host -- only expose them within the Docker network."

---

### 2.4 Add unit tests for all services

**Problem:** Only core-banking-service has unit tests. Other services have only empty context-load tests.

> **Devin prompt:** "Add comprehensive unit tests for: 1) internet-banking-user-service UserService (test createUser success, createUser duplicate email, createUser invalid email, createUser user not found in core, readUsers, readUser, updateUser approval flow). 2) internet-banking-fund-transfer-service FundTransferService (test fundTransfer success, fundTransfer core service failure, readAllTransfers). 3) internet-banking-utility-payment-service UtilityPaymentService (test utilPayment success, utilPayment core service failure, readPayments). Mock all Feign clients and repositories. Use JUnit 5 and Mockito."

---

### 2.5 Add idempotency for financial transactions

**Problem:** Fund transfer and payment endpoints have no idempotency keys. Duplicate requests create duplicate transactions.

> **Devin prompt:** "Add idempotency support to POST /api/v1/transfer and POST /api/v1/utility-payment. Accept an X-Idempotency-Key header. Before processing, check if a transaction with that key already exists. If so, return the existing result. Store the idempotency key in FundTransferEntity and UtilityPaymentEntity. Add a unique constraint on the idempotency key column."

---

### 2.6 Externalize secrets

**Problem:** All passwords are hardcoded in docker-compose.yml and privileges.sql.

> **Devin prompt:** "Replace all hardcoded passwords in docker-compose/docker-compose.yml and docker-compose/mysql/privileges.sql with environment variable references. Create a .env.example file documenting all required environment variables: MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD. Add .env to .gitignore."

---

### 2.7 Fix database transaction isolation

**Problem:** `TransactionService.internalFundTransfer()` does two separate `bankAccountRepository.save()` calls with no locking. Race conditions could cause inconsistent balances.

> **Devin prompt:** "In core-banking-service TransactionService.internalFundTransfer(), add pessimistic locking on the bank account reads using @Lock(LockModeType.PESSIMISTIC_WRITE) on the BankAccountRepository.findByNumber() method, or use SELECT FOR UPDATE. This prevents race conditions where concurrent transfers could read stale balances. Also verify the @Transactional annotation is present and uses the appropriate isolation level."

---

## Phase 3: Polish (Medium-Large effort, medium impact)

### 3.1 Add integration tests

**Problem:** No tests verify actual HTTP endpoints, database interactions, or Feign client behavior.

> **Devin prompt:** "Add integration tests using Testcontainers and Spring Boot Test for each business service. For core-banking-service: test the full REST API with a real MySQL container. For user-service, fund-transfer-service, utility-payment-service: use WireMock to mock core-banking-service responses and test the full request flow. Add Testcontainers MySQL and WireMock dependencies to each build.gradle."

---

### 3.2 Add contract tests

**Problem:** No Spring Cloud Contract or Pact tests between services.

> **Devin prompt:** "Add Spring Cloud Contract tests between the business services and core-banking-service. Define contracts for: GET /api/v1/account/bank-account/{number}, POST /api/v1/transaction/fund-transfer, POST /api/v1/transaction/util-payment, GET /api/v1/user/{identification}. Generate stubs from core-banking-service contracts and use them in consumer service tests."

---

### 3.3 Add structured logging and observability

**Problem:** Unstructured logging, no Prometheus metrics, no custom health checks.

> **Devin prompt:** "Add structured JSON logging to all services using logback-spring.xml with a JSON encoder (logstash-logback-encoder). Add Prometheus metrics exporter by adding micrometer-registry-prometheus dependency and configuring management.endpoints.web.exposure.include=health,info,prometheus. Add custom health indicators for MySQL connectivity and Feign client availability in each service."

---

### 3.4 Add CI/CD pipeline

**Problem:** No GitHub Actions, Jenkinsfile, or other CI configuration exists.

> **Devin prompt:** "Create a .github/workflows/ci.yml GitHub Actions workflow that: 1) Builds all 7 services with Gradle. 2) Runs all unit tests. 3) Runs integration tests. 4) Builds Docker images for each service. 5) Pushes images to GitHub Container Registry on main branch merges. Use a matrix strategy to build services in parallel."

---

### 3.5 Standardize Feign client patterns

**Problem:** Inconsistent Feign client implementations across services.

> **Devin prompt:** "Refactor internet-banking-user-service and internet-banking-utility-payment-service to use @FeignClient interfaces instead of the class-based BankingCoreRestClient. Create BankingCoreFeignClient interfaces matching the pattern in internet-banking-fund-transfer-service. Remove the BankingCoreRestClient classes. Ensure all Feign clients use the shared CustomFeignClientConfiguration."

---

### 3.6 Add API documentation

**Problem:** OpenAPI is misconfigured and endpoints lack documentation annotations.

> **Devin prompt:** "Configure springdoc-openapi properly in all business services. Add @OpenAPIDefinition with title, version, and description to each application class. Ensure all @Operation annotations have complete summary and description. Add @ApiResponse annotations for all possible response codes (200, 400, 404, 422, 500). Add @Schema annotations to all DTOs with field descriptions and examples."

---

## Priority Matrix

```
                    Small Effort          Medium Effort         Large Effort
                ┌───────────────────┬───────────────────┬───────────────────┐
Critical        │ 1.1 Balance bug   │ 2.5 Idempotency   │                   │
Impact          │ 1.2 Validation    │ 2.7 Tx isolation   │                   │
                │ 1.3 Error handling│ 2.3 Auth downstream│                   │
                │                   │ 2.1 Resilience     │                   │
                ├───────────────────┼───────────────────┼───────────────────┤
High            │ 1.5 Entity fix    │ 2.2 Shared library │ 2.4 Unit tests    │
Impact          │ 1.6 Password mask │ 2.6 Secrets        │ 3.1 Integration   │
                │ 1.8 Log masking   │                   │     tests          │
                │ 1.7 Pagination    │                   │ 3.2 Contract tests │
                ├───────────────────┼───────────────────┼───────────────────┤
Medium          │ 1.4 OpenAPI fix   │ 3.5 Feign patterns │ 3.4 CI/CD         │
Impact          │                   │ 3.6 API docs       │                   │
                │                   │ 3.3 Observability  │                   │
                └───────────────────┴───────────────────┴───────────────────┘
```
