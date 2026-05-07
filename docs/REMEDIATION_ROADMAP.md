# Remediation Roadmap

This document provides a prioritized, phased plan for addressing the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md). Each item includes a sample Devin prompt that can be used to implement the remediation.

---

## Phase 1 — Quick Wins (1-2 days each, high impact)

These items are small-effort changes that immediately improve security, reliability, or correctness.

### 1.1 Add Input Validation to All Request DTOs

**Gap Ref:** 4.2 (Critical, Medium effort)

Add Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`) to all request DTOs and `@Valid` on controller method parameters.

**Files affected:** All `*Request` DTOs and controllers across 4 services.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Jakarta Bean Validation
to all request DTOs across all services. Specifically:

1. core-banking-service FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
2. core-banking-service UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber/account
3. internet-banking-fund-transfer-service FundTransferRequest: same as above plus authID optional
4. internet-banking-utility-payment-service UtilityPaymentRequest: same validations
5. internet-banking-user-service User DTO: @NotBlank @Email on email, @NotBlank on identification/password

Add @Valid annotation on all @RequestBody parameters in controllers. Add spring-boot-starter-validation
to build.gradle for each service if not already present. Do NOT modify existing tests.
```

</details>

### 1.2 Fix HTTP Status Codes in Error Handlers

**Gap Ref:** 2.2 (High, Small effort)

Map exceptions to appropriate HTTP status codes instead of always returning 400.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler
in all 4 business services (core-banking, user-service, fund-transfer, utility-payment):

1. EntityNotFoundException → return 404 Not Found
2. InsufficientFundsException → return 422 Unprocessable Entity
3. UserAlreadyRegisteredException → return 409 Conflict
4. InvalidEmailException / InvalidBankingUserException → keep 400 Bad Request
5. Generic Exception → return 500 Internal Server Error (NOT 400)
6. Remove the raw string body from the generic handler — return an ErrorResponse object instead
7. Never expose exception class names or stack traces in error responses

Apply the same pattern consistently across all 4 services. Do NOT modify existing tests.
```

</details>

### 1.3 Fix Password Logging Vulnerability

**Gap Ref:** 4.5 (High, Small effort)

Prevent passwords from appearing in log output.

<details>
<summary>Devin Prompt</summary>

```
In the internet-banking-user-service, fix the password logging vulnerability:

1. In the User DTO class, add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
   to the password field so it is excluded from serialization
2. Add @ToString.Exclude (Lombok) to the password field so toString() won't include it
3. In UserController.createUser(), change the log statement to not log the full request object.
   Use: log.info("Creating user with email {}", request.getEmail())
4. Verify the password field is still accepted on deserialization (POST request body)

Do NOT modify existing tests.
```

</details>

### 1.4 Secure Actuator Endpoints

**Gap Ref:** 6.5 (High, Small effort)

Restrict actuator access to health endpoint only.

<details>
<summary>Devin Prompt</summary>

```
In the internet-banking-api-gateway SecurityConfiguration, change the actuator security rules:

1. Change permitAll() for actuator to only allow /actuator/health and /*/actuator/health
2. All other actuator endpoints should require authentication (covered by .anyExchange().authenticated())
3. Update the security filter chain:
   - exchanges.pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
   - exchanges.pathMatchers("/user/actuator/health", "/fund-transfer/actuator/health",
     "/banking-core/actuator/health", "/utility-payment/actuator/health").permitAll()
   - Remove the broad /actuator/** permitAll rules

Do NOT modify existing tests.
```

</details>

### 1.5 Add Feign Timeout Configuration

**Gap Ref:** 7.3 (High, Small effort)

Configure sensible timeouts for all Feign clients.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Feign timeout configuration
to the 3 services that use Feign clients (fund-transfer, user-service, utility-payment):

1. In each service's application.yml (or bootstrap.yml), add:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connectTimeout: 5000
               readTimeout: 10000

2. Also add connection pool configuration:
   spring:
     cloud:
       openfeign:
         okhttp:
           enabled: true

This ensures no Feign call blocks indefinitely. Do NOT modify existing tests.
```

</details>

### 1.6 Externalize Docker Compose Credentials

**Gap Ref:** 4.1 (Critical, Small effort)

Replace hardcoded passwords with environment variables.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, externalize credentials
in docker-compose files:

1. Create a .env.example file with placeholder values for all credentials:
   MYSQL_ROOT_PASSWORD=changeme
   MYSQL_APP_USER=javatodev_development
   MYSQL_APP_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   POSTGRES_PASSWORD=changeme

2. Update docker-compose.yml to reference ${VARIABLE_NAME} for all passwords
3. Update docker-compose-support-apps.yml similarly
4. Update privileges.sql to use environment variable substitution or document
   that the password must match
5. Add .env to .gitignore
6. Add a note in README.md about copying .env.example to .env

Do NOT modify existing tests.
```

</details>

### 1.7 Fix Keycloak Singleton Thread Safety

**Gap Ref:** 4.6 (Medium, Small effort)

<details>
<summary>Devin Prompt</summary>

```
In internet-banking-user-service, fix the thread-safety issue in KeycloakProperties.getInstance():

Replace the lazy singleton pattern with a Spring @Bean approach:
1. Remove the static keycloakInstance field and getInstance() method from KeycloakProperties
2. Add a @Bean method in a new KeycloakConfig class (or in KeycloakProperties itself)
   that creates the Keycloak instance as a Spring-managed singleton
3. Inject the Keycloak bean into KeycloakManager instead of calling getInstance()
4. This ensures Spring handles singleton lifecycle and thread safety

Do NOT modify existing tests.
```

</details>

### 1.8 Fix OpenAPI Dependency (webflux → webmvc)

**Gap Ref:** 5.4 (Medium, Small effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, fix the OpenAPI dependency
in all services that use Spring MVC (NOT the api-gateway which uses WebFlux):

1. In core-banking-service, fund-transfer-service, user-service, utility-payment-service:
   Change: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
   To: implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'

2. Keep springdoc-openapi-starter-webflux-ui ONLY for internet-banking-api-gateway
   (which actually uses WebFlux)

3. Add typed ResponseEntity<T> return types to all controller methods where currently raw

Do NOT modify existing tests.
```

</details>

### 1.9 Add HikariCP Database Connection Pool Configuration

**Gap Ref:** 7.6 (Medium, Small effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add HikariCP connection pool
configuration for all 4 database-backed services via their application.yml (Spring Cloud Config):

Add to each service's configuration:
  spring:
    datasource:
      hikari:
        maximum-pool-size: 10
        minimum-idle: 5
        idle-timeout: 300000
        connection-timeout: 20000
        max-lifetime: 1200000

Since configuration is managed via Spring Cloud Config (remote Git repo), document the
recommended settings in a README section or in the docs/ directory.
```

</details>

---

## Phase 2 — Important Improvements (2-5 days each)

These items require more effort but significantly improve reliability, maintainability, and operational readiness.

### 2.1 Add Fund Transfer Failure Handling

**Gap Ref:** 2.4 (Critical, Medium effort)

Ensure failed Feign calls update the local entity status to FAILED.

<details>
<summary>Devin Prompt</summary>

```
In the internet-banking-fund-transfer-service FundTransferService, add proper failure handling:

1. Wrap the bankingCoreFeignClient.fundTransfer() call in a try-catch block
2. On any exception: update the FundTransferEntity status to FAILED, save it, then rethrow
3. Add the same pattern to internet-banking-utility-payment-service UtilityPaymentService

Example pattern:
  try {
      FundTransferResponse response = bankingCoreFeignClient.fundTransfer(request);
      entity.setTransactionReference(response.getTransactionId());
      entity.setStatus(TransactionStatus.SUCCESS);
  } catch (Exception e) {
      entity.setStatus(TransactionStatus.FAILED);
      fundTransferRepository.save(entity);
      throw e;
  }
  fundTransferRepository.save(entity);

Also add Feign error decoders (CustomFeignErrorDecoder) to fund-transfer and utility-payment
services, matching the pattern already in user-service (Gap 2.5).

Do NOT modify existing tests but add new unit tests for the failure scenarios.
```

</details>

### 2.2 Implement Circuit Breakers with Resilience4j

**Gap Ref:** 7.1 (High, Medium effort)

Add circuit breakers to all Feign clients to prevent cascading failures.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Resilience4j circuit breakers:

1. Add dependencies to fund-transfer, user-service, and utility-payment build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure circuit breaker defaults in each service's application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         coreBankingService:
           registerHealthIndicator: true
           slidingWindowSize: 10
           failureRateThreshold: 50
           waitDurationInOpenState: 10000
           permittedNumberOfCallsInHalfOpenState: 3

3. Add @CircuitBreaker annotations to Feign client interfaces or create fallback factories
4. Implement fallback methods that return meaningful error responses
5. Add retry configuration:
   resilience4j:
     retry:
       instances:
         coreBankingService:
           maxAttempts: 3
           waitDuration: 1000

Do NOT modify existing tests.
```

</details>

### 2.3 Extract Shared Banking-Common Module

**Gap Ref:** 1.1 (Medium, Medium effort)

Create a shared Gradle module to eliminate code duplication.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, create a shared library module:

1. Create a new directory: banking-common/
2. Add banking-common/build.gradle as a plain Java library (no Spring Boot plugin, just
   spring-boot-dependencies BOM for dependency management)
3. Move these shared classes into banking-common:
   - BaseMapper (from any service — they're identical)
   - AuditAware base entity class
   - ApiRequestContext / ApiRequestContextHolder / AppAuthUserFilter
   - SimpleBankingGlobalException / ErrorResponse / GlobalExceptionHandler
   - AuditConfig / AuditorAwareConfig

4. Create a root settings.gradle that includes all 7 services + banking-common
5. Add dependency in each service's build.gradle: implementation project(':banking-common')
6. Remove the duplicated classes from each service
7. Ensure all services still compile and tests pass

Do NOT modify existing tests.
```

</details>

### 2.4 Implement Consistent Error Response Format

**Gap Ref:** 2.1, 2.3 (High, Medium effort)

Standardize error responses across all services.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo (ideally after extracting
banking-common), create a standardized error response format:

1. Define a consistent ErrorResponse class:
   {
     "timestamp": "2024-01-01T00:00:00Z",
     "status": 404,
     "error": "Not Found",
     "code": "BANKING-CORE-1000",
     "message": "Requested entity not present in the DB.",
     "path": "/api/v1/account/bank-account/999"
   }

2. Add GlobalErrorCode constants to fund-transfer and utility-payment services
3. Update all GlobalExceptionHandler implementations to:
   - Include request path in error response
   - Include timestamp
   - Include HTTP status code
   - Handle MethodArgumentNotValidException for validation errors (return field-level details)
   - Never expose internal exception details
4. Ensure the generic Exception handler returns the same structured format

Do NOT modify existing tests.
```

</details>

### 2.5 Add Unit Tests for All Services

**Gap Ref:** 3.1 (High, Large effort)

Add meaningful unit tests beyond context load tests.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add unit tests for all services
that currently only have context load tests:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test successful transfer, Feign failure handling, status transitions
   - FundTransferControllerTest: test endpoint responses using @WebMvcTest

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test successful payment, failure handling, status transitions
   - UtilityPaymentControllerTest: test endpoint responses

3. internet-banking-user-service:
   - UserServiceTest: test createUser (happy path, duplicate email, invalid NIC, email mismatch)
   - UserServiceTest: test updateUser (approve flow enables Keycloak)
   - UserControllerTest: test endpoints
   - KeycloakUserServiceTest: test Keycloak operations with mocks

Use Mockito for mocking dependencies. Use @WebMvcTest for controller tests.
Target: minimum 80% line coverage for service and controller classes.

Do NOT modify existing tests — only add new test files.
```

</details>

### 2.6 Fix Pagination Response Format

**Gap Ref:** 5.3 (Medium, Medium effort)

Return pagination metadata in list endpoints.

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, fix paginated endpoints
to return pagination metadata:

1. Create a generic PagedResponse<T> wrapper class (in banking-common if extracted):
   {
     "content": [...],
     "page": 0,
     "size": 20,
     "totalElements": 150,
     "totalPages": 8
   }

2. Update these endpoints to return PagedResponse:
   - GET /api/v1/transfer (fund-transfer-service)
   - GET /api/v1/utility-payment (utility-payment-service)
   - GET /api/v1/bank-users (user-service)
   - GET /api/v1/user (core-banking-service)

3. Pass through Spring's Page metadata instead of calling .getContent() and discarding it

Do NOT modify existing tests.
```

</details>

### 2.7 Add Rate Limiting to API Gateway

**Gap Ref:** 4.4 (Medium, Medium effort)

<details>
<summary>Devin Prompt</summary>

```
In the internet-banking-api-gateway, add rate limiting using Spring Cloud Gateway's
RequestRateLimiter filter:

1. Add dependency: implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
2. Configure a Redis-based rate limiter (or in-memory for simplicity):
   spring:
     cloud:
       gateway:
         default-filters:
           - name: RequestRateLimiter
             args:
               redis-rate-limiter.replenishRate: 10
               redis-rate-limiter.burstCapacity: 20

3. Alternatively, if Redis is not desired, implement a simple in-memory rate limiter
   using a GlobalFilter with a token bucket per client IP
4. Apply stricter limits to the registration endpoint

Do NOT modify existing tests.
```

</details>

### 2.8 Improve Logging Consistency

**Gap Ref:** 6.1 (Medium, Medium effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, improve logging:

1. Fix the string concatenation bug in FundTransferService:
   Change: log.info("Sending fund transfer request {}" + request.toString())
   To: log.info("Sending fund transfer request {}", request)

2. Add @ToString.Exclude to all sensitive fields (passwords, secrets) in DTOs
3. Configure structured JSON logging by adding logback-spring.xml to each service with:
   - Console appender with JSON format for docker profile
   - Standard pattern for local development
   - Include traceId/spanId from Micrometer in log pattern

4. Standardize log levels: INFO for business events, DEBUG for technical details,
   WARN for recoverable issues, ERROR for failures requiring attention

Do NOT modify existing tests.
```

</details>

### 2.9 Add Prometheus Metrics

**Gap Ref:** 6.3 (Medium, Medium effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Prometheus metrics:

1. Add to all service build.gradle files:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Configure actuator to expose prometheus endpoint:
   management:
     endpoints:
       web:
         exposure:
           include: health,prometheus,info
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom business metrics using MeterRegistry in key services:
   - fund-transfer: counter for transfers (success/failure), timer for transfer duration
   - utility-payment: counter for payments, timer for payment duration
   - user-service: counter for registrations

Do NOT modify existing tests.
```

</details>

---

## Phase 3 — Polish & Advanced (5+ days each)

These items are longer-term investments for production hardiness and operational excellence.

### 3.1 Implement Saga Pattern for Distributed Transactions

**Gap Ref:** 7.5 (Critical, Large effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, implement a choreography-based
saga pattern for the fund transfer flow:

1. Add RabbitMQ or Kafka as a message broker (RabbitMQ is already in Docker Compose)
2. Replace synchronous Feign call for fund transfers with an event-driven flow:
   a. Fund-transfer-service publishes FundTransferRequested event
   b. Core-banking-service consumes, processes, publishes FundTransferCompleted or FundTransferFailed
   c. Fund-transfer-service consumes result and updates local status

3. Implement compensation logic:
   - If debit succeeds but credit fails, publish CompensationRequired event
   - Core-banking-service handles compensation by reversing the debit

4. Add an outbox table pattern to ensure event publishing is atomic with DB writes
5. Apply the same pattern to utility payments

This is a significant architectural change. Start with fund transfers as a proof of concept.

Do NOT modify existing tests but add new integration tests using Testcontainers.
```

</details>

### 3.2 Add Integration Tests with Testcontainers

**Gap Ref:** 3.2 (High, Large effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add integration tests
using Testcontainers:

1. Add to each service's build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. For core-banking-service:
   - Test full API endpoints with real MySQL database
   - Verify Flyway migrations run correctly
   - Test fund transfer end-to-end within the service
   - Test balance validation and insufficient funds scenarios

3. For user-service:
   - Add Keycloak testcontainer (dasniko/testcontainers-keycloak)
   - Test user registration → Keycloak creation → local DB save
   - Test user approval flow

4. For fund-transfer and utility-payment services:
   - Use WireMock for core-banking-service Feign calls
   - Test the full request-to-response flow with real MySQL

5. Fix existing context load tests to work in isolation (mock external dependencies)

Do NOT modify existing tests — only add new test files.
```

</details>

### 3.3 Add Consumer-Driven Contract Tests

**Gap Ref:** 3.3 (Medium, Large effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add Spring Cloud Contract
tests between services:

1. Add Spring Cloud Contract dependencies to core-banking-service (producer side):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'

2. Define contracts in core-banking-service for:
   - GET /api/v1/account/bank-account/{account_number} → returns BankAccount
   - POST /api/v1/transaction/fund-transfer → returns FundTransferResponse
   - POST /api/v1/transaction/util-payment → returns UtilityPaymentResponse
   - GET /api/v1/user/{identification} → returns User

3. Generate contract stubs JAR from core-banking-service

4. Add contract test dependencies to consumers (fund-transfer, user-service, utility-payment):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side contract tests that verify Feign clients work with the stubs

Do NOT modify existing tests — only add new test files and contract definitions.
```

</details>

### 3.4 Implement Multi-Project Gradle Build

**Gap Ref:** 1.2 (Low, Medium effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, convert to a Gradle
multi-project build:

1. Create a root settings.gradle.kts that includes all 7 services + banking-common
2. Create a root build.gradle.kts with shared configuration:
   - Common Java 21 toolchain
   - Common Spring Boot and Spring Cloud dependency management
   - Common test dependencies (JUnit 5, Mockito)
   - Common Lombok configuration
3. Simplify each service's build.gradle to only include service-specific dependencies
4. Ensure ./gradlew build from root builds all services
5. Ensure ./gradlew test from root runs all tests
6. Update Dockerfiles to reference the correct JAR paths in the multi-project layout

Do NOT modify existing tests.
```

</details>

### 3.5 Add Custom Health Indicators

**Gap Ref:** 6.2 (Medium, Small effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add custom health indicators:

1. internet-banking-user-service:
   - KeycloakHealthIndicator: calls Keycloak admin API to verify connectivity
   - CoreBankingHealthIndicator: checks if core-banking-service is reachable via Feign

2. internet-banking-fund-transfer-service:
   - CoreBankingHealthIndicator: checks core-banking-service Feign connectivity

3. internet-banking-utility-payment-service:
   - CoreBankingHealthIndicator: checks core-banking-service Feign connectivity

4. Configure health endpoint to show details:
   management:
     endpoint:
       health:
         show-details: when_authorized

Do NOT modify existing tests.
```

</details>

### 3.6 Add Transaction Filtering and Search

**Gap Ref:** 5.6 (Low, Medium effort)

<details>
<summary>Devin Prompt</summary>

```
In the ts-java-spring-boot-internet-banking-microservices repo, add filtering capabilities
to list endpoints:

1. Fund transfer list (GET /api/v1/transfer):
   - Filter by status (PENDING, SUCCESS, FAILED)
   - Filter by date range (fromDate, toDate)
   - Filter by fromAccount or toAccount

2. Utility payment list (GET /api/v1/utility-payment):
   - Filter by status
   - Filter by date range
   - Filter by providerId

3. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering
4. Accept filter parameters as @RequestParam with sensible defaults

Do NOT modify existing tests.
```

</details>

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    │  Phase 1          │  Phase 2          │
    │  (Do First)       │  (Do Next)        │
    │                   │                   │
    │  1.1 Validation   │  2.1 Failure      │
    │  1.2 HTTP codes   │      handling     │
    │  1.3 Password log │  2.2 Circuit      │
    │  1.4 Actuator     │      breakers     │
    │  1.5 Timeouts     │  2.5 Unit tests   │
    │  1.6 Credentials  │  2.3 Common       │
    │                   │      module       │
LOW ├───────────────────┼───────────────────┤ HIGH
EFF │                   │                   │ EFFORT
    │  Also Phase 1     │  Phase 3          │
    │  (Easy polish)    │  (Long-term)      │
    │                   │                   │
    │  1.7 Keycloak     │  3.1 Saga pattern │
    │      singleton    │  3.2 Integration  │
    │  1.8 OpenAPI fix  │      tests        │
    │  1.9 HikariCP     │  3.3 Contract     │
    │                   │      tests        │
    │                   │  3.4 Multi-project│
    │                   │      Gradle       │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```

## Execution Summary

| Phase | Items | Est. Total Effort | Key Outcomes |
|---|---|---|---|
| **Phase 1** | 9 items | 5-8 days | Security hardened, basic reliability, correct HTTP semantics |
| **Phase 2** | 9 items | 15-25 days | Production-ready resilience, maintainable codebase, observable services |
| **Phase 3** | 6 items | 20-30 days | Enterprise-grade reliability, comprehensive testing, advanced patterns |

**Recommended approach:** Execute Phase 1 items in parallel (they are independent). Phase 2 items have some dependencies (e.g., 2.3 common module before 2.4 error format). Phase 3 items are independent and can be prioritized based on operational needs.
