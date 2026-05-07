# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt that can be used to implement the fix.

---

## Phase 1: Quick Wins (1-2 weeks)

High-impact items that can be fixed quickly with minimal risk. Focus on critical bugs, security basics, and code quality foundations.

---

### 1.1 Fix Balance Calculation Bug

| | |
|---|---|
| **Gap Reference** | 7.6 — Balance Calculation Bug |
| **Severity** | Critical |
| **Effort** | Small |
| **Description** | `TransactionService.utilPayment()` and `internalFundTransfer()` double-subtract the transfer amount when computing `availableBalance`. After setting `actualBalance = actualBalance - amount`, the code then sets `availableBalance = actualBalance - amount` again, resulting in an extra deduction. |

**Devin Prompt:**
```
Fix the balance calculation bug in core-banking-service TransactionService.java.

In the `utilPayment()` method (lines 63-64), the available balance is double-subtracted.
The current code does:
  fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
  fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));

The second line should be:
  fromAccount.setAvailableBalance(fromAccount.getActualBalance());

Apply the same fix to the `internalFundTransfer()` method for both the source
(lines 90-91) and destination (lines 99-100) account balance calculations.

Add unit tests in TransactionServiceTest.java to verify that after a fund transfer
of 100 from an account with balance 500:
- Source actualBalance = 400
- Source availableBalance = 400
- Destination actualBalance = previous + 100
- Destination availableBalance = previous + 100
```

---

### 1.2 Add Input Validation to All Request DTOs

| | |
|---|---|
| **Gap Reference** | 2.4 / 4.1 — No Input Validation |
| **Severity** | Critical |
| **Effort** | Small |
| **Description** | No Bean Validation annotations exist on any request DTO. Invalid data (null amounts, negative transfers, blank account numbers) passes through to the database. |

**Devin Prompt:**
```
Add Bean Validation to all request DTOs and controller parameters across all services.

1. Add `spring-boot-starter-validation` dependency to build.gradle for:
   - core-banking-service
   - internet-banking-user-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service

2. Add validation annotations to these DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account
   - User (user-service): @NotBlank @Email email, @NotBlank identification,
     @NotBlank password
   - UserUpdateRequest: @NotNull status
   - Core banking FundTransferRequest/UtilityPaymentRequest: same as above

3. Add @Valid annotation to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler
   that returns HTTP 422 with field-level error details in the ErrorResponse format.

5. Add unit tests verifying that invalid requests return 422.
```

---

### 1.3 Fix Error Handling — Proper HTTP Status Codes

| | |
|---|---|
| **Gap Reference** | 2.1 / 2.2 — All Errors Return 400 |
| **Severity** | Critical |
| **Effort** | Small |
| **Description** | The `GlobalExceptionHandler` in all services returns HTTP 400 for every exception, including server errors. The catch-all handler exposes raw exception details. |

**Devin Prompt:**
```
Fix the GlobalExceptionHandler in all 4 services (core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to return proper HTTP status codes.

1. EntityNotFoundException → 404 Not Found
2. InsufficientFundsException → 422 Unprocessable Entity
3. InvalidEmailException → 400 Bad Request
4. UserAlreadyRegisteredException → 409 Conflict
5. InvalidBankingUserException → 404 Not Found
6. FeignException → 502 Bad Gateway (with sanitized message)
7. Generic Exception → 500 Internal Server Error

For the generic Exception handler:
- Return a standardized ErrorResponse { code: "INTERNAL_ERROR", message: "An unexpected
  error occurred. Please try again later." }
- Do NOT expose the exception message or stack trace to the client.
- Log the full exception at ERROR level.

Ensure all handlers return ErrorResponse (not raw strings) for consistency.
```

---

### 1.4 Fix Password Exposure in User DTO

| | |
|---|---|
| **Gap Reference** | 4.6 — Password in Response DTO |
| **Severity** | High |
| **Effort** | Small |
| **Description** | The `User` DTO contains a `password` field that may be serialized in API responses. |

**Devin Prompt:**
```
Fix the password exposure risk in internet-banking-user-service.

In the User DTO class (model/dto/User.java), add @JsonProperty(access =
JsonProperty.Access.WRITE_ONLY) to the `password` field so it is accepted in
requests but never included in responses.

Add a unit test that serializes a User object to JSON and verifies the
password field is absent from the output.
```

---

### 1.5 Fix Keycloak Singleton Thread Safety

| | |
|---|---|
| **Gap Reference** | 4.5 — Keycloak Singleton Not Thread-Safe |
| **Severity** | High |
| **Effort** | Small |
| **Description** | `KeycloakProperties.getInstance()` uses a non-thread-safe lazy initialization pattern. |

**Devin Prompt:**
```
Fix the thread-safety issue in internet-banking-user-service KeycloakProperties.

Replace the manual singleton pattern in KeycloakProperties.getInstance() with a
proper Spring @Bean definition:

1. Create a new @Configuration class KeycloakConfig.
2. Move the Keycloak builder logic into a @Bean method that returns a Keycloak instance.
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance().
4. Remove the static keycloakInstance field and getInstance() method from
   KeycloakProperties.
5. Keep KeycloakProperties as a simple @ConfigurationProperties class.
```

---

### 1.6 Add Feign Error Decoders to All Services

| | |
|---|---|
| **Gap Reference** | 2.3 — Missing Feign Error Decoder |
| **Severity** | High |
| **Effort** | Small |
| **Description** | Only the user service has a custom Feign error decoder. Other services let Feign throw generic exceptions for downstream failures. |

**Devin Prompt:**
```
Add a CustomFeignErrorDecoder to internet-banking-fund-transfer-service and
internet-banking-utility-payment-service, modeled after the one in
internet-banking-user-service.

The decoder should:
1. Read the response body from the downstream service.
2. Parse it as an ErrorResponse (code + message).
3. Map HTTP 404 → EntityNotFoundException
4. Map HTTP 400/422 → SimpleBankingGlobalException with the downstream error code
5. Map other errors → SimpleBankingGlobalException with a generic message
6. Register it in CustomFeignClientConfiguration.

Add unit tests for the error decoder.
```

---

### 1.7 Configure Feign Timeouts

| | |
|---|---|
| **Gap Reference** | 7.3 — No Timeout Configuration |
| **Severity** | High |
| **Effort** | Small |
| **Description** | No explicit timeouts on Feign clients, database connections, or Keycloak calls. |

**Devin Prompt:**
```
Add explicit timeout configuration for all Feign clients.

In each service's application.yml (or the remote config repo), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
          core-banking-service:
            connectTimeout: 5000
            readTimeout: 15000

Also configure HikariCP connection pool timeouts for MySQL:
spring:
  datasource:
    hikari:
      connection-timeout: 5000
      maximum-pool-size: 10

For the Keycloak client in user-service, configure connection and socket
timeouts on the Keycloak builder.
```

---

### 1.8 Fix OpenAPI Dependency (WebFlux → WebMVC)

| | |
|---|---|
| **Gap Reference** | 5.5 — Incorrect OpenAPI Dependency |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | WebMVC services incorrectly use the WebFlux OpenAPI starter. |

**Devin Prompt:**
```
Fix the OpenAPI dependency in these services:
- core-banking-service
- internet-banking-user-service
- internet-banking-fund-transfer-service
- internet-banking-utility-payment-service

Replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
With:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

Verify that /swagger-ui.html and /v3/api-docs endpoints work correctly.
Also add typed ResponseEntity<T> generics to all controller methods so the
generated OpenAPI schemas include response types.
```

---

### 1.9 Add Structured Logging

| | |
|---|---|
| **Gap Reference** | 6.1 — Inconsistent Logging |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | Logging uses string concatenation, logs sensitive data, and has no structured format. |

**Devin Prompt:**
```
Improve logging across all services:

1. Fix string concatenation in FundTransferService.fundTransfer():
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To: log.info("Sending fund transfer request from={} to={}", request.getFromAccount(), request.getToAccount())

2. Remove .toString() calls from all log statements that log request objects.
   Replace with specific non-sensitive fields.

3. Add logback-spring.xml to each service's src/main/resources/ with a JSON
   encoder (net.logstash.logback:logstash-logback-encoder) for production
   profile and console output for dev profile.

4. Add the logstash-logback-encoder dependency to each build.gradle.
```

---

### 1.10 Add Health Check Indicators

| | |
|---|---|
| **Gap Reference** | 6.2 — No Health Check Configuration |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | Health endpoints don't check critical dependencies. |

**Devin Prompt:**
```
Configure actuator health checks in all services:

1. In application.yml for each service, expose health details:
   management:
     endpoint:
       health:
         show-details: always
     endpoints:
       web:
         exposure:
           include: health,info,prometheus

2. Spring Boot auto-configures health indicators for:
   - DataSource (db) — already available via JPA
   - Eureka (discoveryComposite) — already available via Eureka client

3. Add docker-compose healthcheck directives to docker-compose.yml for each
   service using: curl -f http://localhost:{port}/actuator/health || exit 1

4. For internet-banking-user-service, add a custom HealthIndicator that
   checks Keycloak connectivity.
```

---

### 1.11 Add Prometheus Metrics

| | |
|---|---|
| **Gap Reference** | 6.3 — No Metrics Endpoints |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | Prometheus is listed as a technology but the registry dependency is missing. |

**Devin Prompt:**
```
Add Prometheus metrics support to all services:

1. Add to each build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose the prometheus endpoint in application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus

3. Add custom business metrics in key services:
   - core-banking-service: Counter for fund transfers and utility payments
   - Fund transfer service: Timer for end-to-end transfer duration
   - Utility payment service: Counter for payment successes/failures

4. Add a prometheus service to docker-compose.yml with a scrape config
   targeting all service actuator endpoints.
```

---

### 1.12 Externalize Docker Compose Credentials

| | |
|---|---|
| **Gap Reference** | 4.3 — Hardcoded Credentials |
| **Severity** | High |
| **Effort** | Small |
| **Description** | Database and Keycloak credentials are hardcoded in docker-compose.yml. |

**Devin Prompt:**
```
Externalize credentials from docker-compose.yml:

1. Create a .env.example file in the docker-compose/ directory with:
   MYSQL_ROOT_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   KC_DB_PASSWORD=changeme
   POSTGRES_PASSWORD=changeme

2. Update docker-compose.yml to reference environment variables:
   environment:
     MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}

3. Add .env to .gitignore.

4. Add a "Getting Started" section to the README explaining how to
   copy .env.example to .env and set passwords before running
   docker-compose up.
```

---

### 1.13 Add Dependency Vulnerability Scanning

| | |
|---|---|
| **Gap Reference** | 4.7 — No Dependency Vulnerability Scanning |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | No CVE scanning is configured for project dependencies. |

**Devin Prompt:**
```
Add OWASP Dependency-Check to the project:

1. Add the plugin to each service's build.gradle:
   plugins {
     id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure suppression file path and fail-on-CVSS threshold:
   dependencyCheck {
     failBuildOnCVSS = 7.0f
     suppressionFile = "${rootDir}/owasp-suppressions.xml"
   }

3. Create a GitHub Actions workflow (.github/workflows/dependency-check.yml)
   that runs the check on PRs targeting main.

4. Run the check once and fix or suppress any critical findings.
```

---

## Phase 2: Important (2-4 weeks)

Foundational improvements that require more planning and effort. Focus on security, code organization, and resilience.

---

### 2.1 Implement Role-Based Access Control

| | |
|---|---|
| **Gap Reference** | 4.2 — No RBAC |
| **Severity** | Critical |
| **Effort** | Medium |
| **Description** | Any authenticated user can access any endpoint, including admin operations. |

**Devin Prompt:**
```
Implement role-based access control across the banking application:

1. In Keycloak realm configuration (realm-export.json), create roles:
   - ROLE_ADMIN: Can approve users, view all users
   - ROLE_USER: Can transfer funds, make payments from own accounts only

2. Update SecurityConfiguration in the API Gateway to enforce roles:
   - /user/api/v1/bank-users/register → permitAll
   - /user/api/v1/bank-users/update/** → hasRole('ADMIN')
   - /user/api/v1/bank-users → hasRole('ADMIN')
   - /fund-transfer/** → hasRole('USER')
   - /utility-payment/** → hasRole('USER')

3. In the GatewayConfiguration GlobalFilter, extract roles from the JWT
   and forward them as X-Auth-Roles header.

4. In fund-transfer and utility-payment services, add an ownership check:
   the authenticated user (from X-Auth-Id) must own the source account.
   Call core-banking-service to verify account ownership.

5. Update Keycloak test data to include users with different roles.
```

---

### 2.2 Add Circuit Breakers with Resilience4j

| | |
|---|---|
| **Gap Reference** | 7.1 / 7.2 — No Circuit Breakers or Retry |
| **Severity** | Critical |
| **Effort** | Medium |
| **Description** | No resilience patterns protect against cascading failures. |

**Devin Prompt:**
```
Add Resilience4j circuit breakers and retry to all Feign clients:

1. Add dependencies to fund-transfer, utility-payment, and user services:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
   implementation 'io.github.resilience4j:resilience4j-feign'

2. Configure circuit breaker defaults in application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 30s
           permittedNumberOfCallsInHalfOpenState: 3
     retry:
       instances:
         coreBankingService:
           maxAttempts: 3
           waitDuration: 1s
           retryExceptions:
             - java.net.ConnectException
             - feign.RetryableException

3. Create FeignClient fallback factories for each service that return
   meaningful error responses when the circuit is open.

4. IMPORTANT: Do NOT retry fund transfer or utility payment POST calls
   (non-idempotent). Only retry GET calls (account lookups, user lookups).

5. Add actuator endpoints for circuit breaker monitoring.
```

---

### 2.3 Extract Shared Library Module

| | |
|---|---|
| **Gap Reference** | 1.2 — Duplicated Code |
| **Severity** | High |
| **Effort** | Medium |
| **Description** | Exception classes, audit config, filters, mappers, and Feign config are copy-pasted across services. |

**Devin Prompt:**
```
Create a shared library module and convert to a Gradle multi-project build:

1. Create a root settings.gradle that includes all 7 service modules plus
   a new `internet-banking-common` module.

2. Create a root build.gradle with shared configuration (Java 21,
   Spring Boot 3.2.4, Spring Cloud 2023.0.0, common dependencies).

3. Move these classes to internet-banking-common:
   - exception/ErrorResponse.java
   - exception/SimpleBankingGlobalException.java
   - exception/GlobalExceptionHandler.java
   - exception/EntityNotFoundException.java
   - model/dto/AuditAware.java
   - model/TransactionStatus.java
   - model/mapper/BaseMapper.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/CustomFeignClientConfiguration.java
   - configuration/audit/AuditConfig.java
   - configuration/audit/AuditorAwareConfig.java

4. Add `implementation project(':internet-banking-common')` to each
   service's build.gradle.

5. Remove the duplicate classes from each service.

6. Verify all services compile and tests pass.
```

---

### 2.4 Convert to Multi-Project Gradle Build

| | |
|---|---|
| **Gap Reference** | 1.1 — No Multi-Project Build |
| **Severity** | Medium |
| **Effort** | Medium |
| **Description** | Each service has its own independent Gradle build with duplicated configuration. |

> **Note:** This item should be done together with 2.3 (shared library extraction).

**Devin Prompt:**
```
Convert the project to a Gradle multi-project build:

1. Create a root settings.gradle.kts:
   rootProject.name = "internet-banking-microservices"
   include(
     "internet-banking-common",
     "core-banking-service",
     "internet-banking-api-gateway",
     "internet-banking-config-server",
     "internet-banking-service-registry",
     "internet-banking-user-service",
     "internet-banking-fund-transfer-service",
     "internet-banking-utility-payment-service"
   )

2. Create a root build.gradle.kts with subprojects {} block that sets:
   - Java 21 toolchain
   - Common repositories (mavenCentral)
   - Spring dependency management BOM
   - Common test configuration

3. Simplify each service's build.gradle to only declare service-specific
   dependencies.

4. Remove individual Gradle wrappers from each service (keep only root).

5. Verify `./gradlew build` from root builds all services.
```

---

### 2.5 Add Unit Tests for User, Fund Transfer, and Utility Payment Services

| | |
|---|---|
| **Gap Reference** | 3.1 — Minimal Test Coverage |
| **Severity** | Critical |
| **Effort** | Large |
| **Description** | Only core-banking-service has meaningful tests. The other 3 business services have no tests. |

**Devin Prompt:**
```
Add comprehensive unit tests for the three untested business services.

For internet-banking-user-service (UserService):
- Test createUser() happy path: email not in Keycloak, user found in core
  banking, Keycloak returns 201, verify entity saved with PENDING status
- Test createUser() with duplicate email → UserAlreadyRegisteredException
- Test createUser() with email mismatch → InvalidEmailException
- Test createUser() with invalid identification → InvalidBankingUserException
- Test readUsers() with pagination
- Test readUser() by ID, found and not found
- Test updateUser() with APPROVED status → Keycloak user enabled

For internet-banking-fund-transfer-service (FundTransferService):
- Test fundTransfer() happy path: entity saved as PENDING, Feign call
  succeeds, entity updated to SUCCESS
- Test fundTransfer() when Feign call fails → exception propagated,
  entity remains PENDING
- Test readAllTransfers() with pagination

For internet-banking-utility-payment-service (UtilityPaymentService):
- Test utilPayment() happy path
- Test utilPayment() when Feign call fails
- Test readPayments() with pagination

Use Mockito to mock Feign clients and repositories.
Add H2 test configuration where missing.
```

---

### 2.6 Implement Saga Pattern for Financial Transactions

| | |
|---|---|
| **Gap Reference** | 7.5 — Non-Atomic Financial Transactions |
| **Severity** | Critical |
| **Effort** | Large |
| **Description** | Fund transfers span two services without distributed transaction management. |

**Devin Prompt:**
```
Implement a compensation-based saga pattern for fund transfers:

1. Add an idempotency key (UUID) to FundTransferRequest. The fund-transfer
   service generates this before calling core-banking. Core-banking uses
   it to deduplicate requests.

2. In core-banking-service TransactionService.fundTransfer():
   - Check if a transaction with the given idempotency key already exists.
     If so, return the existing result.
   - Wrap the entire operation in a database transaction (already has
     @Transactional).

3. In fund-transfer-service FundTransferService.fundTransfer():
   - Save entity as PENDING.
   - Call core-banking. On success → update to SUCCESS.
   - On failure → update to FAILED with error details.
   - Add a compensating endpoint: POST /api/v1/transfer/{id}/reverse
     that calls a new core-banking reversal endpoint.

4. Add a scheduled job that checks for PENDING transfers older than 5
   minutes and marks them as FAILED (with alerting).

5. Apply the same pattern to utility payments.
```

---

### 2.7 Add Pagination Metadata to List Endpoints

| | |
|---|---|
| **Gap Reference** | 5.2 — No Pagination Metadata |
| **Severity** | Medium |
| **Effort** | Small |
| **Description** | List endpoints return raw lists without total counts or page info. |

**Devin Prompt:**
```
Update all list endpoints to return pagination metadata:

1. Change controller return types from List<T> to Page<T> or create a
   custom PageResponse<T> wrapper with:
   - content: List<T>
   - totalElements: long
   - totalPages: int
   - pageNumber: int
   - pageSize: int

2. Update these endpoints:
   - GET /api/v1/bank-users → return Page<User>
   - GET /api/v1/transfer → return Page<FundTransfer>
   - GET /api/v1/utility-payment → return Page<UtilityPayment>
   - GET /api/v1/user (core-banking) → return Page<User>

3. Update service methods to return Page<T> instead of List<T>.

4. Update Feign clients if any list endpoints are called via Feign.
```

---

### 2.8 Add Feign Retry with Idempotency Protection

| | |
|---|---|
| **Gap Reference** | 7.2 — No Retry Policies |
| **Severity** | High |
| **Effort** | Small |
| **Description** | No retry configuration for transient failures on Feign calls. |

> **Note:** This should be implemented together with 2.2 (circuit breakers).

**Devin Prompt:**
```
Add retry policies to Feign clients, but ONLY for safe (idempotent) operations:

1. Configure Resilience4j retry for GET operations:
   - Account lookups: retry 3 times with 500ms backoff
   - User lookups: retry 3 times with 500ms backoff

2. Do NOT add retry to POST operations (fund transfers, utility payments)
   unless idempotency keys are implemented (see item 2.6).

3. Add Feign Retryer.NEVER_RETRY as the default to prevent Feign's built-in
   retry from conflicting with Resilience4j.

4. Log retry attempts at WARN level.
```

---

## Phase 3: Polish (4-8 weeks)

Nice-to-have improvements for long-term maintainability and operational excellence.

---

### 3.1 Add Integration Tests with TestContainers

| | |
|---|---|
| **Gap Reference** | 3.2 — No Integration Tests |
| **Severity** | High |
| **Effort** | Large |
| **Description** | No integration tests verify database interactions, Feign behavior, or end-to-end request handling. |

**Devin Prompt:**
```
Add integration tests using TestContainers and WireMock:

1. Add testImplementation dependencies to all services:
   - org.testcontainers:mysql
   - org.testcontainers:junit-jupiter
   - org.wiremock:wiremock-standalone

2. For core-banking-service:
   - Create an integration test that starts a MySQL TestContainer
   - Run Flyway migrations
   - Test the full request flow: create account → fund transfer → verify
     balances via API

3. For internet-banking-fund-transfer-service:
   - Use WireMock to simulate core-banking-service responses
   - Test happy path and error scenarios end-to-end
   - Verify that fund_transfer entities are persisted correctly

4. For internet-banking-user-service:
   - Use WireMock for core-banking-service
   - Mock Keycloak admin client
   - Test user registration end-to-end

5. Add a Gradle task alias `integrationTest` that runs only integration tests.
```

---

### 3.2 Add Contract Tests Between Services

| | |
|---|---|
| **Gap Reference** | 3.3 — No Contract Tests |
| **Severity** | Medium |
| **Effort** | Large |
| **Description** | No consumer-driven contracts verify API compatibility between services. |

**Devin Prompt:**
```
Add Spring Cloud Contract tests between services:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   - spring-cloud-starter-contract-verifier (test)
   - spring-cloud-contract-gradle-plugin

2. Write contract definitions (Groovy DSL) in
   core-banking-service/src/test/resources/contracts/ for:
   - GET /api/v1/user/{identification} → success and 404
   - GET /api/v1/account/bank-account/{number} → success and 404
   - POST /api/v1/transaction/fund-transfer → success and insufficient funds
   - POST /api/v1/transaction/util-payment → success

3. Generate WireMock stubs from contracts.

4. In consumer services (user, fund-transfer, utility-payment), add
   spring-cloud-contract-stub-runner tests that verify Feign clients
   work correctly against the generated stubs.
```

---

### 3.3 Implement CI/CD Pipeline

| | |
|---|---|
| **Gap Reference** | 6.5 (Knowledge Base) — No CI/CD |
| **Severity** | Medium |
| **Effort** | Medium |
| **Description** | No CI/CD pipeline exists. Builds, tests, and Docker image creation are manual. |

**Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline:

1. Create .github/workflows/ci.yml with:
   - Trigger: push to main, PRs targeting main
   - Java 21 setup
   - Gradle build (all services)
   - Run unit tests
   - Run integration tests (with TestContainers)
   - OWASP dependency check
   - Build Docker images
   - Push images to GitHub Container Registry (ghcr.io)

2. Create .github/workflows/deploy.yml with:
   - Trigger: release tags (v*)
   - Build and push production Docker images
   - Tag with version and latest

3. Add build status badges to README.md.

4. Add branch protection rules documentation for main branch.
```

---

### 3.4 Standardize Package Structure

| | |
|---|---|
| **Gap Reference** | 1.3 — Inconsistent Package Structure |
| **Severity** | Low |
| **Effort** | Small |
| **Description** | Package naming varies between services. |

**Devin Prompt:**
```
Standardize the package structure across all services to follow this convention:

com.javatodev.finance
├── configuration/         # Spring @Configuration classes
│   ├── audit/
│   ├── feign/
│   ├── filter/
│   └── security/
├── controller/            # REST controllers
├── exception/             # Exception classes and handlers
├── model/
│   ├── dto/               # Data Transfer Objects
│   │   ├── request/
│   │   └── response/
│   ├── entity/            # JPA entities
│   └── mapper/            # DTO ↔ Entity mappers
├── repository/            # Spring Data repositories
└── service/               # Business logic
    └── rest/              # Feign clients

Apply this to all services, moving classes as needed:
- utility-payment: move repository/ to model/repository/
- utility-payment: move model/rest/ to model/dto/
- fund-transfer: rename service/rest/client/ to service/rest/
```

---

### 3.5 Add Fallback Behavior for Downstream Failures

| | |
|---|---|
| **Gap Reference** | 7.4 — No Fallback Behavior |
| **Severity** | Medium |
| **Effort** | Medium |
| **Description** | No graceful degradation when downstream services are unavailable. |

**Devin Prompt:**
```
Implement Feign fallback factories for graceful degradation:

1. For fund-transfer-service BankingCoreFeignClient:
   - Create BankingCoreFeignClientFallbackFactory
   - On readAccount() failure: throw a specific ServiceUnavailableException
   - On fundTransfer() failure: save entity as FAILED, return error response

2. For utility-payment-service BankingCoreRestClient:
   - Create BankingCoreRestClientFallbackFactory
   - Similar behavior as above

3. For user-service BankingCoreRestClient:
   - On readUser() failure: return a clear error message that core banking
     is unavailable

4. Register fallback factories in @FeignClient annotations:
   @FeignClient(name = "core-banking-service",
     fallbackFactory = BankingCoreFeignClientFallbackFactory.class)
```

---

### 3.6 Register Mappers as Spring Beans

| | |
|---|---|
| **Gap Reference** | 1.4 — Mappers Not Spring Beans |
| **Severity** | Low |
| **Effort** | Small |
| **Description** | Mapper classes are instantiated directly instead of managed by Spring. |

**Devin Prompt:**
```
Convert all mapper classes to Spring-managed beans:

1. Add @Component to each mapper class:
   - core-banking-service: UserMapper, BankAccountMapper, UtilityAccountMapper
   - user-service: UserMapper
   - fund-transfer-service: FundTransferMapper
   - utility-payment-service: UtilityPaymentMapper

2. Remove the `new XxxMapper()` field initializations from service classes.
3. Add the mapper as a constructor-injected dependency (via @RequiredArgsConstructor).
4. Update tests to mock or instantiate the mapper as needed.

Alternatively, consider migrating to MapStruct for compile-time type-safe mapping.
```

---

### 3.7 Document API Versioning Strategy

| | |
|---|---|
| **Gap Reference** | 5.3 / 5.4 — API Versioning and REST Conventions |
| **Severity** | Low |
| **Effort** | Small |
| **Description** | No documented versioning strategy. Some URLs contain verbs. |

**Devin Prompt:**
```
Document and enforce RESTful API conventions:

1. Create docs/API_CONVENTIONS.md documenting:
   - URL path versioning (already using /api/v1/)
   - Noun-based resource URLs
   - HTTP method semantics
   - Standard error response format
   - Pagination conventions

2. Rename non-RESTful endpoints:
   - POST /api/v1/bank-users/register → POST /api/v1/bank-users
   - PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
   (Add redirects or aliases for backward compatibility)

3. Update the Postman collection to reflect the new URLs.
```

---

### 3.8 Verify and Document Distributed Tracing

| | |
|---|---|
| **Gap Reference** | 6.4 — Distributed Tracing Not Verified |
| **Severity** | Low |
| **Effort** | Small |
| **Description** | Tracing dependencies exist but propagation is not verified. |

**Devin Prompt:**
```
Verify and document distributed tracing configuration:

1. Start the full Docker Compose stack.
2. Make a fund transfer request through the API Gateway.
3. Open Zipkin UI (localhost:9411) and find the trace.
4. Verify the trace spans all services:
   API Gateway → Fund Transfer Service → Core Banking Service
5. Take a screenshot and add it to the docs.
6. Document the tracing configuration:
   - Sampling rate
   - Propagation format (B3 vs W3C)
   - How to access Zipkin UI
   - How to search for traces
7. Add trace ID to error responses so clients can reference it in
   support requests.
```

---

## Summary

| Phase | Items | Focus Areas |
|---|---|---|
| **Phase 1** (Quick Wins) | 13 items | Bug fix, input validation, error handling, security basics, observability |
| **Phase 2** (Important) | 8 items | RBAC, circuit breakers, shared library, test coverage, saga pattern |
| **Phase 3** (Polish) | 8 items | Integration/contract tests, CI/CD, package cleanup, fallbacks, docs |

### Recommended Execution Order

1. **1.1** Fix balance bug (immediate — financial correctness)
2. **1.2** Add input validation (immediate — security)
3. **1.3** Fix error handling (immediate — operational clarity)
4. **1.4-1.6** Security quick fixes (password, Keycloak, Feign decoders)
5. **1.7** Timeouts (resilience foundation)
6. **1.8-1.13** Observability and code quality quick wins
7. **2.1** RBAC (security milestone)
8. **2.2** Circuit breakers (resilience milestone)
9. **2.3-2.4** Shared library and multi-project build (code organization milestone)
10. **2.5-2.6** Tests and saga pattern (quality milestone)
11. **Phase 3** items in any order based on team priorities
