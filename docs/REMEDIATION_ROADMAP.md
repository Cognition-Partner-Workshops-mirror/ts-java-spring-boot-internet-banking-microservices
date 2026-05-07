# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on severity and effort. Each item includes a sample Devin prompt that can be used to execute the remediation directly.

## Table of Contents

- [Phase 1: Quick Wins](#phase-1-quick-wins-critical-fixes-small-effort) — Critical fixes, Small effort
- [Phase 2: Important](#phase-2-important-high-severity-medium-effort) — High severity, Medium effort
- [Phase 3: Polish](#phase-3-polish-mediumlow-severity) — Medium/Low severity improvements

---

## Phase 1: Quick Wins (Critical fixes, Small effort)

These items address critical bugs and security vulnerabilities that can be fixed with minimal code changes.

---

### 1.1 Fix Balance Double-Subtraction Bug

**Gap Reference**: B-1
**Files**: `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`
**Lines**: 90-91 (`internalFundTransfer`) and 63-64 (`utilPayment`)

**Problem**: After setting `actualBalance = actualBalance - amount`, the code sets `availableBalance = actualBalance - amount` using the already-reduced `actualBalance`, causing a double-subtraction.

**Fix**: Change `setAvailableBalance(getActualBalance().subtract(amount))` to `setAvailableBalance(getActualBalance())` since `actualBalance` was already subtracted.

**Sample Devin Prompt**:
> Fix the balance calculation bug in TransactionService.java. In both internalFundTransfer() and utilPayment() methods, the availableBalance is being double-subtracted. After setting actualBalance = actualBalance - amount, the availableBalance should be set equal to the new actualBalance, not actualBalance - amount again. Apply the same logic to the receiver side in internalFundTransfer where availableBalance gets a double-addition.

---

### 1.2 Fix Error Handling — Proper HTTP Status Codes

**Gap Reference**: EH-1, EH-2
**Files**: `GlobalExceptionHandler.java` in all 4 business services:
- `core-banking-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-user-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`
- `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java`

**Problem**: All exceptions return HTTP 400. The generic handler leaks stack traces via `"Exception occur inside API " + e`.

**Fix**: Map `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, keep `SimpleBankingGlobalException` → 400, generic `Exception` → 500 with a safe error message.

**Sample Devin Prompt**:
> Update GlobalExceptionHandler in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). Add a dedicated @ExceptionHandler for EntityNotFoundException returning 404. Change the generic Exception handler to return 500 with a safe error message (no stack trace). Add handler for InsufficientFundsException returning 422.

---

### 1.3 Add Input Validation to Request DTOs

**Gap Reference**: S-2
**Files**: All `*Request.java` DTOs across all services

**Problem**: Zero `@Valid`, `@NotNull`, `@NotBlank`, `@Positive` annotations anywhere. `FundTransferRequest` accepts null/negative amounts.

**Fix**: Add `jakarta.validation` annotations to all request DTOs and `@Valid` on controller method parameters.

**Sample Devin Prompt**:
> Add Jakarta Bean Validation annotations to all request DTOs across all services. Add @NotBlank to string fields like fromAccount, toAccount, account, referenceNumber. Add @NotNull @Positive to BigDecimal amount fields. Add @NotNull to providerId. Add @Valid annotation to all @RequestBody parameters in controllers. Add spring-boot-starter-validation dependency to build.gradle files.

---

### 1.4 Fix TransactionEntity Relationship Mapping

**Gap Reference**: B-2
**File**: `core-banking-service/src/main/java/com/javatodev/finance/model/entity/TransactionEntity.java`

**Problem**: `@OneToOne(cascade = CascadeType.ALL)` on the account field. Multiple transactions reference the same account, and `CascadeType.ALL` means deleting a transaction could cascade-delete the account.

**Fix**: Change to `@ManyToOne(fetch = FetchType.LAZY)` with no cascade.

**Sample Devin Prompt**:
> Fix TransactionEntity.java in core-banking-service. Change the @OneToOne(cascade = CascadeType.ALL) annotation on the account field to @ManyToOne(fetch = FetchType.LAZY) with no cascade. Multiple transactions reference the same bank account, so this must be ManyToOne.

---

### 1.5 Fix Password Leak in User DTO

**Gap Reference**: S-6
**File**: `internet-banking-user-service/src/main/java/com/javatodev/finance/model/dto/User.java`

**Problem**: `User` DTO contains a `password` field with no serialization protection, potentially leaking passwords in API responses.

**Fix**: Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to the password field.

**Sample Devin Prompt**:
> In internet-banking-user-service, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the password field in model/dto/User.java to prevent passwords from being included in API responses.

---

### 1.6 Fix Keycloak Singleton Thread Safety

**Gap Reference**: S-5
**File**: `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java`

**Problem**: `getInstance()` uses a non-synchronized null check on a static field — classic double-checked locking bug without `volatile`.

**Fix**: Replace manual singleton with a `@Bean` method in a `@Configuration` class, or use `synchronized` + `volatile`.

**Sample Devin Prompt**:
> Fix the thread-unsafe Keycloak singleton in KeycloakProperties.java in internet-banking-user-service. Convert the getInstance() method to a proper Spring @Bean definition in a @Configuration class, or at minimum add synchronized keyword to the method and make the static field volatile.

---

### 1.7 Fix OpenAPI Dependency

**Gap Reference**: A-3
**Files**: `build.gradle` in all 4 business services

**Problem**: All servlet-based (Spring MVC) services include `springdoc-openapi-starter-webflux-ui:2.1.0` instead of `springdoc-openapi-starter-webmvc-ui`.

**Fix**: Change `webflux-ui` to `webmvc-ui`.

**Sample Devin Prompt**:
> In all 4 business service build.gradle files, change the springdoc dependency from 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0' to 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'. These are servlet-based (Spring MVC) services, not WebFlux.

---

### 1.8 Add Feign Timeout Configuration

**Gap Reference**: R-3
**Files**: `application.yml` in fund-transfer-service, user-service, utility-payment-service (or their config server equivalents)

**Problem**: No Feign timeouts, no connection/read timeouts configured. Requests can hang indefinitely.

**Fix**: Add default Feign client timeout configuration.

**Sample Devin Prompt**:
> Add Feign client timeout configuration to all 3 services that use Feign (internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service). In each service's application.yml, add: spring.cloud.openfeign.client.config.default.connect-timeout=5000 and spring.cloud.openfeign.client.config.default.read-timeout=10000.

---

## Phase 2: Important (High severity, Medium effort)

These items address significant architectural and reliability gaps that require more substantial changes.

---

### 2.1 Add Resilience4j Circuit Breakers

**Gap Reference**: R-1, R-4
**Services**: fund-transfer, user, utility-payment

**Sample Devin Prompt**:
> Add Resilience4j circuit breaker support to internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. Add spring-cloud-starter-circuitbreaker-resilience4j dependency. Create fallback classes for each Feign client. Configure circuit breaker with slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s in application.yml.

---

### 2.2 Extract Shared Library

**Gap Reference**: CO-1, CO-2
**Scope**: New `banking-common` module + root Gradle configuration

**Sample Devin Prompt**:
> Create a shared library module called 'banking-common' at the repository root. Move the following duplicated classes into it: BaseMapper, AuditAware, ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler, ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter. Create a root settings.gradle that includes all 7 service modules plus banking-common. Update each service's build.gradle to depend on banking-common.

---

### 2.3 Add Unit Tests for All Services

**Gap Reference**: T-1, T-4
**Services**: user-service, fund-transfer-service, utility-payment-service

**Sample Devin Prompt**:
> Add comprehensive unit tests for internet-banking-fund-transfer-service, internet-banking-user-service, and internet-banking-utility-payment-service. For each service, create: (1) Service layer tests using Mockito to mock repositories and Feign clients, testing happy paths and error cases. (2) Controller layer tests using @WebMvcTest and MockMvc. Follow the same patterns used in core-banking-service/src/test/java/com/javatodev/finance/service/ tests.

---

### 2.4 Add Feign Error Decoder to Fund-Transfer and Utility-Payment Services

**Gap Reference**: EH-4
**Source**: Port from `internet-banking-user-service`

**Sample Devin Prompt**:
> Add CustomFeignErrorDecoder to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Copy the CustomFeignErrorDecoder from internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/feign/CustomFeignErrorDecoder.java. Register it as a @Bean in each service's CustomFeignClientConfiguration class.

---

### 2.5 Add Transaction Failure Handling

**Gap Reference**: EH-5
**Services**: fund-transfer-service, utility-payment-service

**Sample Devin Prompt**:
> Add error handling to FundTransferService.fundTransfer() and UtilityPaymentService.utilPayment(). Wrap the Feign client calls in try-catch blocks. On any exception, update the entity status to FAILED, save it, and re-throw the exception. This ensures the local database accurately reflects failed transactions.

---

### 2.6 Secure Actuator Endpoints

**Gap Reference**: O-4
**File**: `internet-banking-api-gateway` SecurityConfiguration

**Sample Devin Prompt**:
> Update SecurityConfiguration.java in internet-banking-api-gateway to restrict actuator access. Change the permitAll() rules for actuator to only allow /actuator/health and /actuator/info without authentication. All other actuator endpoints should require authentication.

---

### 2.7 Externalize Secrets

**Gap Reference**: S-1
**Files**: `docker-compose/docker-compose.yml`, `docker-compose/mysql/Dockerfile`, `docker-compose/mysql/privileges.sql`

**Sample Devin Prompt**:
> Externalize all hardcoded secrets in docker-compose/docker-compose.yml and docker-compose/mysql/Dockerfile. Replace hardcoded passwords with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD}). Create a .env.example file with placeholder values. Add .env to .gitignore. Update docker-compose/mysql/Dockerfile to use ARG/ENV pattern instead of hardcoded password.

---

### 2.8 Add Pagination Metadata to List Endpoints

**Gap Reference**: A-2
**Scope**: All list endpoints across all services

**Sample Devin Prompt**:
> Update all paginated list endpoints across all services to return pagination metadata. Instead of extracting .getContent() from the Page object and returning List<T>, return a wrapper DTO containing: content (the list), totalElements, totalPages, currentPage, and pageSize. Apply to: CoreBanking UserController.readUsers(), FundTransferController.readFundTransfers(), UtilityPaymentController.readPayments(), and UserService UserController.readUsers().

---

## Phase 3: Polish (Medium/Low severity)

These items improve overall code quality, observability, and long-term maintainability.

---

### 3.1 Add CI/CD Pipeline

**Gap Reference**: B-3
**File**: `.github/workflows/ci.yml` (new)

**Sample Devin Prompt**:
> Create a GitHub Actions CI pipeline at .github/workflows/ci.yml. The pipeline should: (1) Trigger on push to main and on pull requests. (2) Use Java 21. (3) Build and test each of the 7 services using Gradle. (4) Run tests with H2 in-memory database. (5) Build Docker images for each service (without pushing).

---

### 3.2 Add Structured JSON Logging

**Gap Reference**: O-1, O-5
**Scope**: All services

**Sample Devin Prompt**:
> Add structured JSON logging to all services. Create a logback-spring.xml in each service's src/main/resources/ directory. Configure console output with pattern layout for local development and JSON layout (using logstash-logback-encoder) for the docker profile. Add net.logstash.logback:logstash-logback-encoder dependency to each build.gradle.

---

### 3.3 Add Custom Health Indicators

**Gap Reference**: O-2
**Scope**: Business services

**Sample Devin Prompt**:
> Add custom Spring Boot health indicators to business services. In user-service, add a KeycloakHealthIndicator that checks Keycloak connectivity. In fund-transfer-service and utility-payment-service, add a CoreBankingHealthIndicator that pings the core banking service health endpoint. Register them as @Component beans.

---

### 3.4 Add Custom Micrometer Metrics

**Gap Reference**: O-3
**Scope**: Business services

**Sample Devin Prompt**:
> Add custom Micrometer metrics to business services. Add @Timed annotations to all service methods in FundTransferService, UtilityPaymentService, and UserService. Add Counter metrics for successful/failed fund transfers and utility payments. Configure a MeterRegistryCustomizer bean to add common tags (service name, environment).

---

### 3.5 Fix REST URL Conventions

**Gap Reference**: A-5
**Service**: `internet-banking-user-service`

**Sample Devin Prompt**:
> Refactor UserController in internet-banking-user-service to follow REST conventions. Change POST /api/v1/bank-users/register to POST /api/v1/bank-users. Change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}. Update the API Gateway security configuration to match the new registration endpoint path.

---

### 3.6 Add Integration Tests with Testcontainers

**Gap Reference**: T-2
**Service**: `core-banking-service` (start here, expand to others)

**Sample Devin Prompt**:
> Add integration tests using Testcontainers to core-banking-service. Add org.testcontainers:mysql and org.testcontainers:junit-jupiter dependencies. Create an integration test class that starts a MySQL container, runs Flyway migrations, and tests the full request flow through controllers using MockMvc with a real database.

---

### 3.7 Add Contract Tests

**Gap Reference**: T-3
**Scope**: Core banking service (provider) + consumer services

**Sample Devin Prompt**:
> Add Spring Cloud Contract tests for core-banking-service. Define contracts for the endpoints consumed by fund-transfer-service, user-service, and utility-payment-service. Generate stubs that consumer services can use in their tests. Add spring-cloud-starter-contract-verifier to core-banking-service and spring-cloud-starter-contract-stub-runner to consumer services.

---

### 3.8 Convert Mappers to Spring Beans

**Gap Reference**: CO-5
**Scope**: All services with mapper classes

**Sample Devin Prompt**:
> Convert all mapper classes to Spring-managed beans. Add @Component to BaseMapper subclasses (BankAccountMapper, UserMapper, UtilityAccountMapper, FundTransferMapper, UtilityPaymentMapper) in all services. Remove the `private XMapper mapper = new XMapper()` field initializations in service classes and replace with constructor injection via @RequiredArgsConstructor.

---

## Implementation Order Summary

```
Phase 1 (Quick Wins) — Target: 1-2 sprints
├── 1.1  Fix balance double-subtraction bug          [Critical, Small]
├── 1.2  Fix error handling + HTTP status codes       [Critical, Small]
├── 1.3  Add input validation to DTOs                 [Critical, Small]
├── 1.4  Fix TransactionEntity relationship           [Critical, Small]
├── 1.5  Fix password leak in User DTO                [Critical, Small]
├── 1.6  Fix Keycloak singleton thread safety          [High, Small]
├── 1.7  Fix OpenAPI dependency                       [Medium, Small]
└── 1.8  Add Feign timeout configuration              [Critical, Small]

Phase 2 (Important) — Target: 2-4 sprints
├── 2.1  Add Resilience4j circuit breakers            [Critical, Medium]
├── 2.2  Extract shared library                       [High, Large]
├── 2.3  Add unit tests for all services              [Critical, Large]
├── 2.4  Add Feign error decoder                      [High, Medium]
├── 2.5  Add transaction failure handling             [High, Medium]
├── 2.6  Secure actuator endpoints                    [High, Small]
├── 2.7  Externalize secrets                          [Critical, Medium]
└── 2.8  Add pagination metadata                      [High, Small]

Phase 3 (Polish) — Target: 3-6 sprints
├── 3.1  Add CI/CD pipeline                           [High, Medium]
├── 3.2  Add structured JSON logging                  [Medium, Small]
├── 3.3  Add custom health indicators                 [Medium, Medium]
├── 3.4  Add custom Micrometer metrics                [Medium, Medium]
├── 3.5  Fix REST URL conventions                     [Medium, Small]
├── 3.6  Add integration tests (Testcontainers)       [High, Large]
├── 3.7  Add contract tests                           [High, Large]
└── 3.8  Convert mappers to Spring beans              [Medium, Small]
```
