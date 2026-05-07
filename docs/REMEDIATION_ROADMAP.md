# Remediation Roadmap: Internet Banking Microservices

> Prioritized action plan derived from the [Gap Analysis](./GAP_ANALYSIS.md).
> Each item includes a ready-to-use **Devin prompt** for direct execution.

---

## Phase 1: Quick Wins (High Severity, Low Effort)

These items address critical and high-severity gaps that can be resolved with small, focused changes. Target: **1-2 weeks**.

---

### 1.1 Fix Balance Calculation Bug (Critical / Small)

**Gap Reference:** 7.6 -- Double-subtraction of amount from `availableBalance` in `TransactionService`.

**What to do:** In `internalFundTransfer()` and `utilPayment()`, after updating `actualBalance`, set `availableBalance` equal to the new `actualBalance` instead of subtracting again.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, fix the balance calculation bug in core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java.

In internalFundTransfer():
- Line ~91: Change `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` to `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance())`
- Line ~100: Change `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))` to `toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance())`

In utilPayment():
- Line ~64: Change `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()))` to `fromAccount.setAvailableBalance(fromAccount.getActualBalance())`

Update the existing unit tests in TransactionServiceTest.java to verify that availableBalance equals actualBalance after each transaction. Create a PR with the fix.
```
</details>

---

### 1.2 Fix Exception Handler Stack Trace Leak (Critical / Small)

**Gap Reference:** 2.1 -- `GlobalExceptionHandler.handleException()` returns `"Exception occur inside API " + e` exposing internals.

**What to do:** Replace the generic handler in all 4 services to return a structured `ErrorResponse` and log the exception server-side.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, fix the generic exception handler in all 4 business services to prevent stack trace leaks.

Files to modify:
- core-banking-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java
- internet-banking-user-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java
- internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java
- internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java

In each file, change the handleException method to:
1. Log the full exception with log.error("Unexpected error", e)
2. Return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.builder().code("INTERNAL_ERROR").message("An unexpected error occurred. Please try again later.").build())
3. Add @Slf4j annotation to the class if not present

Also update handleGlobalException to use appropriate HTTP status codes instead of always returning 400:
- EntityNotFoundException -> 404
- InsufficientFundsException -> 422
- UserAlreadyRegisteredException -> 409
- Other SimpleBankingGlobalException -> 400

Create a PR with these changes.
```
</details>

---

### 1.3 Add Input Validation (Critical / Small)

**Gap Reference:** 2.4 / 4.4 -- No `@Valid` or Bean Validation annotations anywhere.

**What to do:** Add `spring-boot-starter-validation` dependency, annotate DTOs, add `@Valid` to controller parameters.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation to all business services.

1. Add 'org.springframework.boot:spring-boot-starter-validation' to build.gradle in:
   - core-banking-service
   - internet-banking-user-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service

2. Add validation annotations to request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount, toAccount; @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotBlank on account, referenceNumber; @NotNull @Positive on amount, providerId
   - User (user-service): @NotBlank @Email on email; @NotBlank on identification; @NotBlank @Size(min=8) on password
   - UserUpdateRequest: @NotNull on status

3. Add @Valid annotation to all @RequestBody parameters in all controllers.

4. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns HTTP 400 with field-level error details.

Create a PR with these changes.
```
</details>

---

### 1.4 Secure Actuator Endpoints (High / Small)

**Gap Reference:** 4.3 -- All actuator endpoints are publicly accessible without authentication.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, restrict actuator endpoint access.

1. In internet-banking-api-gateway SecurityConfiguration.java, change the actuator path matchers from:
   exchanges.pathMatchers("/actuator/**").permitAll()
     .pathMatchers("/user/actuator/**").permitAll()
     .pathMatchers("/fund-transfer/actuator/**").permitAll()
     .pathMatchers("/banking-core/actuator/**").permitAll()
     .pathMatchers("/utility-payment/actuator/**").permitAll()
   to only allow health endpoints:
   exchanges.pathMatchers("/actuator/health/**").permitAll()
     .pathMatchers("/user/actuator/health/**").permitAll()
     .pathMatchers("/fund-transfer/actuator/health/**").permitAll()
     .pathMatchers("/banking-core/actuator/health/**").permitAll()
     .pathMatchers("/utility-payment/actuator/health/**").permitAll()

2. The remaining actuator endpoints will require authentication by the existing .anyExchange().authenticated() rule.

Create a PR with this change.
```
</details>

---

### 1.5 Add Feign Client Timeouts (High / Small)

**Gap Reference:** 7.3 -- No connection or read timeouts configured for Feign clients.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add Feign client timeout configuration to all services that use Feign.

Add the following to application.yml (or create a feign-defaults section) in:
- internet-banking-user-service
- internet-banking-fund-transfer-service  
- internet-banking-utility-payment-service

Configuration to add:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
            logger-level: BASIC

Create a PR with these changes.
```
</details>

---

### 1.6 Add Retry Policies (High / Small)

**Gap Reference:** 7.2 -- No retry configuration on Feign calls.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add Spring Retry support for Feign clients.

1. Add 'org.springframework.retry:spring-retry' to build.gradle in:
   - internet-banking-user-service
   - internet-banking-fund-transfer-service
   - internet-banking-utility-payment-service

2. Add @EnableRetry to each service's main application class.

3. Configure retry in application.yml for each service:
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            retryer: feign.Retryer.Default

Note: Only retry on GET (read) operations. Do not retry POST (write) operations to avoid duplicate transactions. Use a custom Retryer bean if needed.

Create a PR with these changes.
```
</details>

---

### 1.7 Add Prometheus Metrics (High / Small)

**Gap Reference:** 6.3 -- No Prometheus metrics despite being listed in the tech stack.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add Prometheus metrics support.

1. Add 'io.micrometer:micrometer-registry-prometheus' to build.gradle in all 6 services.

2. Add to each service's application.yml:
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      application: ${spring.application.name}

Create a PR with these changes.
```
</details>

---

### 1.8 Fix SLF4J Logging Bug (Medium / Small)

**Gap Reference:** 6.1 -- String concatenation instead of parameterized logging in `FundTransferService`.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, fix the SLF4J logging bug in internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java.

Change line 32 from:
log.info("Sending fund transfer request {}" + request.toString());
to:
log.info("Sending fund transfer request {}", request);

Create a PR with this fix.
```
</details>

---

### 1.9 Fix Swagger Dependency (Wrong webflux vs webmvc) (Medium / Small)

**Gap Reference:** 5.5 -- Non-gateway services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC apps.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, fix the incorrect Swagger/OpenAPI dependency.

In build.gradle for these 4 services:
- core-banking-service
- internet-banking-user-service
- internet-banking-fund-transfer-service
- internet-banking-utility-payment-service

Change:
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
to:
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

The API Gateway should keep its webflux dependency since it uses Spring WebFlux.

Create a PR with these changes.
```
</details>

---

### 1.10 Fix Keycloak Singleton Thread Safety (Medium / Small)

**Gap Reference:** 4.5 -- Static singleton without synchronization in `KeycloakProperties`.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, fix the thread-unsafe Keycloak singleton in internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java.

Replace the static singleton pattern with a Spring @Bean. Create a new @Configuration class KeycloakConfig that produces a @Bean Keycloak instance using the same KeycloakBuilder logic. Inject this bean into KeycloakManager instead of calling KeycloakProperties.getInstance().

Alternatively, if you prefer minimal change: add synchronized to the getInstance() method.

Create a PR with this fix.
```
</details>

---

### 1.11 Add Typed ResponseEntity Generics (Medium / Small)

**Gap Reference:** 5.1 -- Raw `ResponseEntity` without type parameters on most controllers.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add proper generic types to all ResponseEntity return types in controllers.

Files to update:
- core-banking-service AccountController: ResponseEntity<BankAccount>, ResponseEntity<UtilityAccount>
- core-banking-service TransactionController: ResponseEntity<FundTransferResponse>, ResponseEntity<UtilityPaymentResponse>
- core-banking-service UserController: ResponseEntity<User>, ResponseEntity<List<User>>
- internet-banking-fund-transfer-service FundTransferController: ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
- internet-banking-utility-payment-service UtilityPaymentController: ResponseEntity<List<UtilityPayment>>, ResponseEntity<UtilityPaymentResponse>

Create a PR with these changes.
```
</details>

---

## Phase 2: Important (High Severity, Medium Effort)

These items require more significant changes but are critical for production readiness. Target: **2-4 weeks**.

---

### 2.1 Add Circuit Breakers (Critical / Medium)

**Gap Reference:** 7.1 -- No circuit breakers on any inter-service calls.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers to all Feign clients.

1. Add dependencies to build.gradle in user-service, fund-transfer-service, utility-payment-service:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable circuit breaker for Feign in application.yml:
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

3. Configure circuit breaker defaults in application.yml:
resilience4j:
  circuitbreaker:
    instances:
      core-banking-service:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10000
        permitted-number-of-calls-in-half-open-state: 3

4. Create fallback classes for each Feign client that return meaningful error responses:
   - BankingCoreRestClient (user-service) -> BankingCoreRestClientFallback
   - BankingCoreFeignClient (fund-transfer) -> BankingCoreFeignClientFallback
   - BankingCoreRestClient (utility-payment) -> BankingCoreRestClientFallback

5. Register fallbacks with @FeignClient(fallbackFactory = ...)

Create a PR with these changes.
```
</details>

---

### 2.2 Add Feign Error Handling to Fund Transfer & Utility Payment (Critical / Medium)

**Gap Reference:** 2.3 -- No error handling when Feign calls fail; orphaned PENDING records.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add proper error handling for Feign client failures in fund-transfer-service and utility-payment-service.

1. In FundTransferService.fundTransfer():
   - Wrap the bankingCoreFeignClient.fundTransfer() call in try-catch
   - On failure: update the entity status to FAILED, save it, then rethrow or return error response
   - Log the error with full context (fromAccount, toAccount, amount)

2. In UtilityPaymentService.utilPayment():
   - Wrap the bankingCoreRestClient.utilityPayment() call in try-catch
   - On failure: update the entity status to FAILED, save it, then rethrow or return error response
   - Log the error with full context

3. Add a CustomFeignErrorDecoder (similar to user-service) to both services that converts Feign error responses into meaningful exceptions.

4. Add FAILED to the TransactionStatus enum in both services if not already present.

5. Add unit tests for the failure scenarios.

Create a PR with these changes.
```
</details>

---

### 2.3 Extract Shared Library (High / Medium)

**Gap Reference:** 1.2 -- Duplicated code across 4 services.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, create a shared library module to eliminate code duplication.

1. Create a new directory internet-banking-common/ with its own build.gradle:
   - Apply java-library plugin
   - Include shared dependencies (lombok, jakarta.persistence, spring-web, spring-data-commons)

2. Move these duplicated classes into internet-banking-common:
   - model/dto/AuditAware.java
   - configuration/audit/AuditConfig.java
   - configuration/audit/AuditorAwareConfig.java
   - configuration/filter/AppAuthUserFilter.java
   - configuration/filter/ApiRequestContext.java
   - configuration/filter/ApiRequestContextHolder.java
   - exception/ErrorResponse.java
   - exception/SimpleBankingGlobalException.java
   - exception/GlobalExceptionHandler.java (as a base class)
   - model/mapper/BaseMapper.java

3. Update build.gradle in each consuming service to add:
   implementation project(':internet-banking-common')

4. Create a root settings.gradle that includes all modules.

5. Remove the duplicated files from each service and update imports.

Create a PR with these changes.
```
</details>

---

### 2.4 Add Rate Limiting at API Gateway (High / Medium)

**Gap Reference:** 4.6 -- No rate limiting on any endpoint.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add rate limiting to the API Gateway.

1. Add to internet-banking-api-gateway build.gradle:
   implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'

2. Add a Redis container to docker-compose.yml.

3. Configure rate limiting in the gateway routes (via Spring Cloud Config or local application.yml):
   - Global rate limit: 100 requests/second per IP
   - Registration endpoint: 5 requests/minute per IP (to prevent abuse)
   - Fund transfer endpoint: 10 requests/minute per user

4. Create a custom KeyResolver bean that resolves by IP address for unauthenticated endpoints and by user principal for authenticated endpoints.

5. Add Redis to docker-compose-support-apps.yml as well.

Create a PR with these changes.
```
</details>

---

### 2.5 Set Up CI/CD Pipeline (High / Large)

**Gap Reference:** 8.1 -- No CI/CD pipeline exists.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, create a GitHub Actions CI pipeline.

Create .github/workflows/ci.yml that:

1. Triggers on push to main and on pull requests
2. Uses Java 21 with Gradle
3. For each service (run as a matrix strategy):
   - Run ./gradlew build
   - Run ./gradlew test
   - Upload test results as artifacts
4. Add a Docker build step that builds the Docker image for each service (but does not push)
5. Cache Gradle dependencies between runs

The workflow should fail fast if any service fails to build or test.

Create a PR with this workflow.
```
</details>

---

### 2.6 Externalize Credentials from Source Code (Critical / Small)

**Gap Reference:** 4.1 -- Hardcoded passwords in docker-compose.yml and privileges.sql.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, externalize all hardcoded credentials.

1. Create docker-compose/.env.example with placeholder values:
   MYSQL_ROOT_PASSWORD=changeme
   MYSQL_APP_USER=javatodev_development
   MYSQL_APP_PASSWORD=changeme
   KEYCLOAK_ADMIN=admin
   KEYCLOAK_ADMIN_PASSWORD=changeme
   KC_DB_PASSWORD=changeme
   POSTGRES_PASSWORD=changeme

2. Update docker-compose.yml to use ${VARIABLE} syntax for all passwords.

3. Update docker-compose/mysql/privileges.sql to use environment variable substitution or move user creation to an entrypoint script.

4. Add .env to .gitignore to prevent committing actual credentials.

5. Update README.md installation instructions to include copying .env.example to .env and filling in values.

Create a PR with these changes.
```
</details>

---

## Phase 3: Polish (Medium/Low Severity, Various Effort)

These items improve quality and developer experience. Target: **4-8 weeks**.

---

### 3.1 Add Comprehensive Unit Tests (High / Large)

**Gap Reference:** 3.1 -- Only core-banking-service has unit tests.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add unit tests for all business services.

For internet-banking-user-service, create tests for:
- UserService: createUser (happy path, email already registered, user not found in core banking, email mismatch, keycloak creation failure), readUsers, readUser, updateUser (approve flow, entity not found)
- KeycloakUserService: createUser, readUser, readUserByEmail, updateUser
- UserController: test all endpoints with MockMvc

For internet-banking-fund-transfer-service, create tests for:
- FundTransferService: fundTransfer (happy path, core banking failure), readAllTransfers
- FundTransferController: test all endpoints with MockMvc

For internet-banking-utility-payment-service, create tests for:
- UtilityPaymentService: utilPayment (happy path, core banking failure), readPayments
- UtilityPaymentController: test all endpoints with MockMvc

Use Mockito for mocking dependencies. Follow the same pattern as the existing core-banking-service tests. Add H2 test configuration where needed.

Create a PR with these tests.
```
</details>

---

### 3.2 Add Integration Tests with Testcontainers (High / Large)

**Gap Reference:** 3.2 -- No integration tests exist.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add integration tests using Testcontainers for core-banking-service.

1. Add to core-banking-service build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create an integration test class CoreBankingIntegrationTest with @SpringBootTest and @Testcontainers:
   - Start a MySQL Testcontainer
   - Configure dynamic properties for datasource
   - Test the full flow: create user data via SQL, call fund transfer endpoint, verify balances
   - Test utility payment end-to-end
   - Test insufficient funds scenario

3. Add Feign client contract tests using WireMock:
   - For user-service: mock core-banking-service responses
   - For fund-transfer-service: mock core-banking-service responses
   - For utility-payment-service: mock core-banking-service responses

Create a PR with these tests.
```
</details>

---

### 3.3 Add Test Coverage Tooling (Medium / Small)

**Gap Reference:** 3.3 -- No JaCoCo configured.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add JaCoCo test coverage reporting to all services.

Add to each service's build.gradle:

plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.12"
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.60
            }
        }
    }
}

check.dependsOn jacocoTestCoverageVerification

Create a PR with these changes.
```
</details>

---

### 3.4 Implement Saga Pattern for Financial Transactions (Critical / Large)

**Gap Reference:** 7.5 -- Non-atomic distributed transactions with no compensation logic.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, implement the Saga pattern for fund transfer and utility payment flows.

This is a significant architectural change. Implement orchestration-based saga:

1. In fund-transfer-service FundTransferService:
   - Save initial entity as PENDING
   - Call core-banking fund-transfer endpoint
   - On success: update to SUCCESS
   - On failure: update to FAILED, log compensation needed
   - Add a @Scheduled job that retries FAILED transfers or flags them for manual review

2. In utility-payment-service UtilityPaymentService:
   - Same pattern as above

3. In core-banking-service TransactionService:
   - Add a reverseTransaction() method that can undo a fund transfer
   - Expose it as a new REST endpoint: POST /api/v1/transaction/reverse/{transactionId}

4. Add a new TransactionStatus: COMPENSATION_REQUIRED, COMPENSATED

5. Add idempotency keys to prevent duplicate transactions:
   - Accept an idempotency key header
   - Check if a transaction with that key already exists before processing

Create a PR with these changes.
```
</details>

---

### 3.5 Add Pagination Metadata to List Responses (Medium / Small)

**Gap Reference:** 5.3 -- Pagination metadata discarded.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add pagination metadata to all list endpoints.

1. Create a generic PageResponse<T> DTO in each service (or in the shared library):
   - List<T> content
   - int page
   - int size
   - long totalElements
   - int totalPages
   - boolean last

2. Update all service methods that accept Pageable to return PageResponse instead of List:
   - core-banking-service UserService.readUsers()
   - internet-banking-user-service UserService.readUsers()
   - internet-banking-fund-transfer-service FundTransferService.readAllTransfers()
   - internet-banking-utility-payment-service UtilityPaymentService.readPayments()

3. Update the corresponding controllers to return ResponseEntity<PageResponse<T>>.

Create a PR with these changes.
```
</details>

---

### 3.6 Add Multi-Stage Docker Builds (Medium / Medium)

**Gap Reference:** 8.2 -- Dockerfiles depend on pre-built JARs.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, convert all Dockerfiles to multi-stage builds.

Replace each Dockerfile with a multi-stage pattern:

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21.0.2_13-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
COPY wait-for-it.sh wait-for-it.sh
RUN chmod +x wait-for-it.sh && apk add --no-cache bash
EXPOSE <port>
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "app.jar"]

Apply this to all 7 Dockerfiles (6 services + any other).

Create a PR with these changes.
```
</details>

---

### 3.7 Add Health Check Configuration (Medium / Small)

**Gap Reference:** 6.2 -- No custom health indicators or Docker health checks.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, add health check configuration.

1. Add to each service's application.yml:
management:
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized
  health:
    db:
      enabled: true

2. Add Docker Compose healthcheck directives for each service:
healthcheck:
  test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:<port>/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s

3. Replace wait-for-it.sh entrypoints with depends_on + healthcheck conditions where possible.

Create a PR with these changes.
```
</details>

---

### 3.8 Standardize REST Conventions (Low / Small)

**Gap Reference:** 5.4 -- Verbs in URLs, inconsistent naming.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, standardize REST API conventions.

1. In internet-banking-user-service UserController:
   - Change POST /api/v1/bank-users/register to POST /api/v1/bank-users (standard resource creation)
   - Change PATCH /api/v1/bank-users/update/{id} to PATCH /api/v1/bank-users/{id}

2. In core-banking-service AccountController:
   - Change path variable from {account_number} to {accountNumber} (or use kebab-case {account-number})
   - Standardize to kebab-case across all services

3. Update the API Gateway security config to match the new paths (especially the registration endpoint permitAll rule).

4. Update any Feign clients that reference the changed paths.

Create a PR with these changes.
```
</details>

---

### 3.9 Remove Deprecated Docker Compose Version & Static IPs (Low / Small)

**Gap Reference:** 8.3, 8.4 -- Deprecated version key and unnecessary static IPs.

<details>
<summary>Devin Prompt</summary>

```
In the repo ts-java-spring-boot-internet-banking-microservices, modernize Docker Compose files.

1. Remove the 'version: 3.6' line from both:
   - docker-compose/docker-compose.yml
   - docker-compose/docker-compose-support-apps.yml

2. Remove all static IP assignments (ipv4_address lines) and the IPAM configuration from the network section. Keep the custom network but use Docker's built-in DNS resolution via container names (which is already in use for service-to-service communication).

3. Add resource limits to each service:
deploy:
  resources:
    limits:
      memory: 512M
    reservations:
      memory: 256M

Create a PR with these changes.
```
</details>

---

## Priority Summary

| Phase | Items | Timeline | Impact |
|---|---|---|---|
| **Phase 1** | 11 quick wins (balance bug, security, validation, timeouts, metrics, logging fixes) | 1-2 weeks | Fixes all Critical/Small and High/Small gaps |
| **Phase 2** | 6 important items (circuit breakers, error handling, shared library, rate limiting, CI/CD, credentials) | 2-4 weeks | Production hardening |
| **Phase 3** | 9 polish items (comprehensive tests, Saga pattern, pagination, Docker, health checks, REST conventions) | 4-8 weeks | Engineering excellence |

### Execution Order Within Each Phase

**Phase 1 recommended order:**
1. Fix balance calculation bug (1.1) -- correctness issue
2. Fix exception handler leak (1.2) -- security issue
3. Add input validation (1.3) -- security issue
4. Externalize credentials (2.6, moved up) -- security issue
5. Secure actuator endpoints (1.4)
6. Add timeouts (1.5) + retries (1.6)
7. Add Prometheus metrics (1.7)
8. Fix logging bug (1.8), Swagger dep (1.9), Keycloak singleton (1.10), ResponseEntity generics (1.11)

**Phase 2 recommended order:**
1. Circuit breakers (2.1) -- resilience
2. Feign error handling (2.2) -- correctness
3. CI/CD pipeline (2.5) -- developer productivity
4. Shared library (2.3) -- maintainability
5. Rate limiting (2.4) -- security

**Phase 3 recommended order:**
1. Unit tests (3.1) -- foundation for all further changes
2. Test coverage tooling (3.3) -- visibility
3. Integration tests (3.2) -- confidence
4. Saga pattern (3.4) -- correctness at scale
5. Remaining items (3.5-3.9) -- polish
