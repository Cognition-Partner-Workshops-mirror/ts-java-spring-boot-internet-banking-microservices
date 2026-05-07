# Remediation Roadmap

This roadmap prioritizes the 34 gaps identified in the [Gap Analysis](GAP_ANALYSIS.md) into three phases. Each item includes a Devin prompt you can use to kick off the remediation.

---

## Phase 1: Quick Wins (Critical/High + Small Effort)

These items have the highest impact-to-effort ratio. They address critical bugs and security issues with minimal code changes.

### 1.1 Fix Balance Calculation Bug

**Gap:** 7.6 | **Severity:** Critical | **Effort:** Small

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `utilPayment()`. After setting `actualBalance = actualBalance - amount`, the code then sets `availableBalance = actualBalance - amount`, effectively subtracting twice.

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Fix the balance calculation bug in core-banking-service TransactionService.
In both internalFundTransfer() and utilPayment(), the availableBalance is set
to actualBalance.subtract(amount) AFTER actualBalance has already been reduced.
This double-subtracts the amount from availableBalance.

Fix: after modifying actualBalance, set availableBalance = actualBalance
(not actualBalance.subtract(amount)) for both debit and credit sides.

Update the existing unit tests in TransactionServiceTest to assert correct
balance values after transactions.
```

### 1.2 Fix Generic Exception Handler to Return Proper Status Codes

**Gap:** 2.1, 2.2, 2.3 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Refactor the GlobalExceptionHandler in ALL four business services
(core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service,
internet-banking-utility-payment-service) to:

1. Return 404 Not Found for EntityNotFoundException
2. Return 422 Unprocessable Entity for InsufficientFundsException
3. Return 409 Conflict for UserAlreadyRegisteredException
4. Return 400 Bad Request for InvalidEmailException and InvalidBankingUserException
5. Return 500 Internal Server Error for the generic Exception catch-all
6. Always return a consistent JSON ErrorResponse { code, message } body
   (never a raw string)
7. Ensure ErrorResponse uses the same structure (code + message) across
   all services

Do not modify existing tests -- add new controller-level tests if needed.
```

### 1.3 Add Bean Validation to All Request DTOs

**Gap:** 2.4, 4.2 | **Severity:** Critical/High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add Jakarta Bean Validation to all request DTOs across all services:

1. Add spring-boot-starter-validation to each service's build.gradle
2. Add constraints to request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account
   - User (user-service): @NotBlank @Email email, @NotBlank identification,
     @NotBlank password
   - UserUpdateRequest: @NotNull status
3. Add @Valid annotation to all @RequestBody parameters in controllers
4. Add a MethodArgumentNotValidException handler in each
   GlobalExceptionHandler that returns 400 with field-level error details

Do not modify existing tests.
```

### 1.4 Stop Logging Sensitive Data

**Gap:** 4.6 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Fix password/sensitive data exposure in logs:

1. In internet-banking-user-service UserController, change the log in
   createUser() to NOT log the full request object (which contains the
   password). Log only the email instead.
2. Add @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) to the
   password field in User DTO so it is never serialized in responses.
3. Add @ToString.Exclude on the password field (or override toString)
   to prevent it from appearing in logs.
4. Review all other log.info() calls across services and ensure no
   sensitive data (full request bodies with financial amounts, account
   numbers) is logged at INFO level. Move detailed request logging to
   DEBUG level.
```

### 1.5 Add Feign Client Timeout Configuration

**Gap:** 7.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add explicit timeout configuration for all Feign clients:

1. In each service that uses Feign (user-service, fund-transfer-service,
   utility-payment-service), add the following to application.yml
   (or bootstrap config):
   - connectTimeout: 5000 (5 seconds)
   - readTimeout: 10000 (10 seconds)
2. Configure via spring.cloud.openfeign.client.config.default or
   per-client config.
3. Add a comment in each config explaining the timeout values.
```

### 1.6 Add Feign Retry Policies

**Gap:** 7.2 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add retry configuration for Feign clients:

1. Add a Retryer bean to the Feign configuration in each service
   (fund-transfer-service, utility-payment-service, user-service).
2. Configure: maxAttempts=3, period=1000ms, maxPeriod=3000ms.
3. Only retry on connection exceptions and 503 responses, NOT on
   4xx errors or POST requests that mutate state (fund transfers,
   payments). For mutation endpoints, retries should be disabled
   to avoid duplicate transactions.
4. Document the retry behavior in code comments.
```

### 1.7 Fix Raw ResponseEntity Generics

**Gap:** 5.1 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add proper generic types to all ResponseEntity return types in controllers:

1. core-banking-service AccountController: ResponseEntity<BankAccount>,
   ResponseEntity<UtilityAccount>
2. core-banking-service UserController: ResponseEntity<User>,
   ResponseEntity<List<User>>
3. core-banking-service TransactionController:
   ResponseEntity<FundTransferResponse>,
   ResponseEntity<UtilityPaymentResponse>
4. fund-transfer-service FundTransferController:
   ResponseEntity<FundTransferResponse>, ResponseEntity<List<FundTransfer>>
5. utility-payment-service UtilityPaymentController:
   ResponseEntity<List<UtilityPayment>>,
   ResponseEntity<UtilityPaymentResponse>

This improves type safety and enables proper OpenAPI documentation generation.
```

### 1.8 Fix OpenAPI Dependency (WebFlux vs Servlet)

**Gap:** 5.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Fix the OpenAPI/Swagger dependency in servlet-based services:

1. In core-banking-service, internet-banking-user-service,
   internet-banking-fund-transfer-service, and
   internet-banking-utility-payment-service, replace:
   springdoc-openapi-starter-webflux-ui
   with:
   springdoc-openapi-starter-webmvc-ui (same version 2.1.0)

2. The api-gateway should keep webflux-ui since it IS a reactive service.
3. Verify each service compiles and Swagger UI loads at /swagger-ui.html.
```

### 1.9 Return Page Metadata in Paginated Responses

**Gap:** 5.3 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Fix paginated endpoints to return full pagination metadata:

1. In all services that return paginated data (core-banking UserController,
   user-service UserController, fund-transfer FundTransferController,
   utility-payment UtilityPaymentController):
   - Change the return type from List<T> to Page<T>
   - Return the full Page object from the repository instead of calling
     .getContent()
2. This gives clients totalElements, totalPages, number, size, etc.
3. Update service methods accordingly.
```

### 1.10 Fix Keycloak Singleton Thread Safety

**Gap:** 4.4 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Fix the thread-safety issue in KeycloakProperties.getInstance():

Option A (preferred): Remove the static singleton pattern entirely.
Instead, create the Keycloak instance as a Spring @Bean in a
@Configuration class, letting Spring manage the singleton lifecycle.

Option B: If keeping the current approach, use double-checked locking
with a volatile field, or use a holder pattern.

Apply the fix in internet-banking-user-service
KeycloakProperties.java.
```

### 1.11 Add Prometheus Metrics Exporter

**Gap:** 6.3 | **Severity:** High | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add Prometheus metrics support to all services:

1. Add io.micrometer:micrometer-registry-prometheus to each service's
   build.gradle dependencies.
2. Ensure the actuator prometheus endpoint is exposed by adding to each
   service's config:
   management.endpoints.web.exposure.include=health,info,prometheus
3. Verify /actuator/prometheus returns metrics in Prometheus format.
```

### 1.12 Add Custom Health Checks

**Gap:** 6.2 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add custom health indicators to services with external dependencies:

1. internet-banking-user-service: Add a KeycloakHealthIndicator that
   calls keycloakManager.getKeyCloakInstanceWithRealm() and reports
   UP/DOWN based on connectivity.
2. For fund-transfer-service and utility-payment-service: Add a
   CoreBankingHealthIndicator that calls the core-banking-service
   actuator health endpoint via Feign.
3. Register each as a @Component implementing HealthIndicator.
```

---

## Phase 2: Important (High/Medium Severity + Medium Effort)

These items require more coordinated effort but significantly improve the system's reliability and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap:** 7.1, 7.4 | **Severity:** Critical/Medium | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add Resilience4j circuit breakers to all Feign client calls:

1. Add spring-cloud-starter-circuitbreaker-resilience4j to
   fund-transfer-service, utility-payment-service, and user-service
   build.gradle.
2. Configure circuit breakers in application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3
3. Add @CircuitBreaker annotations on Feign client methods with
   fallback methods that:
   - For read operations: return cached data or a clear error message
   - For write operations: mark the local record as FAILED and return
     a message asking the user to retry
4. Add Resilience4j actuator endpoints for monitoring circuit state.
```

### 2.2 Add Idempotency Protection for Financial Operations

**Gap:** 7.5 | **Severity:** Critical | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Implement idempotency for fund transfers and utility payments:

1. Add an idempotencyKey field (UUID) to FundTransferRequest and
   UtilityPaymentRequest.
2. Add a unique constraint on idempotency_key in fund_transfer and
   utility_payment tables.
3. In FundTransferService.fundTransfer() and
   UtilityPaymentService.utilPayment():
   - Before processing, check if a record with the same idempotencyKey
     already exists
   - If it exists and is SUCCESS, return the existing response
   - If it exists and is FAILED, allow retry
   - If it doesn't exist, proceed normally
4. Return 409 Conflict if a duplicate in-progress request is detected.
5. Add appropriate Flyway migrations for the new column.
```

### 2.3 Add Role-Based Access Control

**Gap:** 4.5 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Implement role-based authorization:

1. In the API gateway SecurityConfiguration, add role-based path matchers:
   - /user/api/v1/bank-users/update/** -> require ROLE_ADMIN
   - /user/api/v1/bank-users (GET) -> require ROLE_ADMIN
   - /fund-transfer/** -> require ROLE_USER
   - /utility-payment/** -> require ROLE_USER
   - /banking-core/** -> require ROLE_ADMIN (internal service)
2. Configure Keycloak realm roles (ADMIN, USER) and map them to
   JWT claims.
3. Update the gateway's JWT configuration to extract roles from the
   Keycloak JWT (realm_access.roles claim).
4. Document the role assignments in a comment or config file.
```

### 2.4 Extract Shared Library for Common Code

**Gap:** 1.2 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Create a shared library module to eliminate code duplication:

1. Create a new module: internet-banking-common
2. Move these shared classes into it:
   - exception/SimpleBankingGlobalException
   - exception/EntityNotFoundException
   - exception/GlobalExceptionHandler (base class)
   - exception/ErrorResponse
   - exception/GlobalErrorCode
   - model/dto/AuditAware
   - configuration/audit/AuditConfig + AuditorAwareConfig
   - configuration/filter/AppAuthUserFilter + ApiRequestContext +
     ApiRequestContextHolder
   - model/mapper/BaseMapper
3. Publish as a local dependency and add to each service's build.gradle.
4. Remove the duplicated classes from each service.
5. Create a root settings.gradle that includes all modules.
```

### 2.5 Create Multi-Project Gradle Build

**Gap:** 1.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Convert the project to a Gradle multi-project build:

1. Create a root build.gradle with:
   - Shared plugin versions (Spring Boot, dependency management,
     git-properties)
   - Shared Java 21 sourceCompatibility
   - Shared Spring Cloud BOM version
   - Shared test configuration (useJUnitPlatform)
   - Shared repositories (mavenCentral)
2. Create a root settings.gradle that includes all service modules.
3. Simplify each service's build.gradle to only declare
   service-specific dependencies.
4. Verify all services still build: ./gradlew build
```

### 2.6 Externalize Credentials from Source Code

**Gap:** 4.1 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Externalize hardcoded credentials from docker-compose and SQL files:

1. Create a .env.example file in docker-compose/ with placeholder values
   for all credentials (MYSQL_ROOT_PASSWORD, KEYCLOAK_ADMIN_PASSWORD,
   DB_USER_PASSWORD, etc.).
2. Update docker-compose.yml to reference env vars: ${MYSQL_ROOT_PASSWORD}
3. Update mysql/privileges.sql to use a parameterized password or
   document that it should be changed.
4. Add .env to .gitignore.
5. Create a .env file with development defaults (not committed).
6. Update README.md with instructions for configuring credentials.
```

### 2.7 Standardize Logging Across Services

**Gap:** 6.1 | **Severity:** High | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Standardize logging across all services:

1. Add a shared logback-spring.xml to each service's resources with:
   - JSON structured logging format for production profile
   - Human-readable format for local/dev profile
   - Include traceId and spanId from Micrometer in every log line
2. Establish logging guidelines:
   - DEBUG: detailed request/response data (excluding sensitive fields)
   - INFO: business events (transfer initiated, payment processed)
   - WARN: recoverable issues (retry attempts, degraded service)
   - ERROR: unrecoverable failures
3. Remove all log.info() calls that include full request objects.
4. Add MDC context for userId/authId from the X-Auth-Id header.
```

### 2.8 Add Feign Error Decoders to All Services

**Gap:** 2.5 | **Severity:** Medium | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add consistent Feign error decoders to fund-transfer-service and
utility-payment-service:

1. Create a CustomFeignErrorDecoder (similar to user-service's existing
   one) that:
   - Reads the ErrorResponse JSON from the response body
   - Maps 404 to EntityNotFoundException
   - Maps 422 to InsufficientFundsException (or a new
     CoreBankingException)
   - Maps other 4xx/5xx to SimpleBankingGlobalException
2. Register the decoder in CustomFeignClientConfiguration.
3. This ensures downstream errors from core-banking-service are
   translated into meaningful domain exceptions.
```

### 2.9 Add Rate Limiting to API Gateway

**Gap:** 5.6 | **Severity:** Medium | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add rate limiting to the API gateway:

1. Add spring-boot-starter-data-redis-reactive to the gateway's
   build.gradle.
2. Configure a RequestRateLimiter filter in the gateway routes:
   - Default: 10 requests/second per user
   - Fund transfer: 5 requests/second per user
   - User registration: 3 requests/minute per IP
3. Add a Redis container to docker-compose.yml.
4. Configure a KeyResolver that uses the JWT subject claim for
   authenticated requests and IP address for unauthenticated requests.
5. Return 429 Too Many Requests with a Retry-After header.
```

---

## Phase 3: Polish (Lower Severity or Large Effort)

These items improve overall quality and maturity but are less urgent.

### 3.1 Add Unit Tests to All Services

**Gap:** 3.1 | **Severity:** Critical | **Effort:** Large

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add unit tests to services that currently have none:

1. internet-banking-user-service:
   - UserServiceTest: test createUser (happy path, duplicate email,
     invalid email, user not found in core bank), readUsers, readUser,
     updateUser (approve, disable)
   - KeycloakUserServiceTest: test createUser, readUser, readUserByEmail
     (mock KeycloakManager)
2. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer (happy path, core bank
     failure), readAllTransfers
3. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment (happy path, core bank
     failure), readPayments

Use Mockito to mock Feign clients and repositories. Add test
application.yml with H2 and disabled Eureka/Config Server for each
service.
```

### 3.2 Add Integration Tests

**Gap:** 3.2, 3.4 | **Severity:** High | **Effort:** Large

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add integration tests for each service:

1. Create test application.yml for each service with:
   - H2 in-memory database
   - Flyway disabled
   - Eureka client disabled (eureka.client.enabled=false)
   - Config server disabled (spring.cloud.config.enabled=false)
2. Add @WebMvcTest controller tests for each service:
   - Verify JSON serialization/deserialization
   - Verify HTTP status codes for success and error cases
   - Verify validation error responses
3. Add @SpringBootTest integration tests with @MockBean for
   Feign clients.
4. Use WireMock or MockServer for simulating core-banking-service
   responses in fund-transfer and utility-payment service tests.
```

### 3.3 Add Contract Tests Between Services

**Gap:** 3.3 | **Severity:** Medium | **Effort:** Large

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add Spring Cloud Contract tests for inter-service communication:

1. Add spring-cloud-starter-contract-verifier to core-banking-service
   (the provider).
2. Define contracts in core-banking-service/src/test/resources/contracts/
   for each endpoint consumed by downstream services:
   - GET /api/v1/user/{identification}
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
3. Add spring-cloud-starter-contract-stub-runner to consumer services
   and write contract verification tests.
4. This ensures API changes in core-banking-service don't silently
   break downstream consumers.
```

### 3.4 Standardize Package Structure

**Gap:** 1.3 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Standardize package structure across all services to follow a
consistent convention:

- controller/       (REST controllers)
- service/           (business logic)
- service/rest/      (Feign clients)
- model/dto/         (DTOs and request/response objects)
- model/entity/      (JPA entities)
- model/mapper/      (object mappers)
- repository/        (Spring Data repositories)
- configuration/     (Spring config classes)
- exception/         (exception classes and handlers)

Rename packages where they deviate (e.g., model.repository -> repository,
service.rest.client -> service.rest).
```

### 3.5 Register Mappers as Spring Beans

**Gap:** 1.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Convert manually instantiated mapper classes to Spring beans:

1. Add @Component to each mapper class (BankAccountMapper, UserMapper,
   UtilityAccountMapper, FundTransferMapper, UtilityPaymentMapper).
2. In service classes, replace `private XMapper mapper = new XMapper()`
   with constructor injection via @RequiredArgsConstructor.
3. This enables future migration to MapStruct and makes mappers
   testable via DI.
```

### 3.6 Document CSRF and API Versioning Decisions

**Gap:** 4.3, 5.2 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add architectural decision records:

1. Create docs/adr/ directory.
2. ADR-001: Document why CSRF is disabled (stateless JWT-based API,
   no cookie-based sessions).
3. ADR-002: Document API versioning strategy (URL-based /api/v1/,
   plan for v2 when breaking changes are needed).
4. ADR-003: Document the choice of eventual consistency between
   internet-banking services and core-banking-service.
```

### 3.7 Add Distributed Tracing Custom Spans

**Gap:** 6.4 | **Severity:** Low | **Effort:** Small

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Enhance distributed tracing with custom spans:

1. Add custom spans (using Micrometer's Observation API) around key
   business operations:
   - FundTransferService.fundTransfer(): span with fromAccount,
     toAccount, amount tags
   - UtilityPaymentService.utilPayment(): span with providerId,
     amount tags
   - UserService.createUser(): span with identification tag
2. Configure trace sampling rate explicitly (e.g., 1.0 for dev, 0.1
   for production) in application.yml.
3. Verify traces appear in Zipkin with the custom span names.
```

### 3.8 Add HATEOAS Support (Optional)

**Gap:** 5.5 | **Severity:** Low | **Effort:** Medium

**Devin Prompt:**
```
@Cognition-Partner-Workshops-mirror/ts-java-spring-boot-internet-banking-microservices

Add Spring HATEOAS support for resource linking (optional enhancement):

1. Add spring-boot-starter-hateoas to services that expose
   public-facing APIs.
2. Convert key response DTOs to extend RepresentationModel.
3. Add links to related resources (e.g., user response includes
   link to their accounts, transfer response includes link to
   transaction details).
```

---

## Implementation Priority Matrix

```
                    Small Effort          Medium Effort         Large Effort
                ┌───────────────────┬───────────────────┬───────────────────┐
  Critical/High │ Phase 1           │ Phase 2           │ Phase 3           │
                │ 1.1 Balance bug   │ 2.1 Circuit break │ 3.1 Unit tests   │
                │ 1.2 Exception hdl │ 2.2 Idempotency   │ 3.2 Integration  │
                │ 1.3 Validation    │ 2.3 RBAC           │ 3.3 Contract     │
                │ 1.4 Log passwords │ 2.4 Shared lib     │                   │
                │ 1.5 Timeouts      │ 2.5 Gradle multi   │                   │
                │ 1.6 Retry         │ 2.7 Logging         │                   │
                │ 1.11 Prometheus   │                     │                   │
                ├───────────────────┼───────────────────┼───────────────────┤
  Medium/Low    │ 1.7 Generics     │ 2.6 Credentials   │ 3.8 HATEOAS      │
                │ 1.8 OpenAPI dep  │ 2.8 Feign decoder │                   │
                │ 1.9 Pagination   │ 2.9 Rate limiting │                   │
                │ 1.10 Keycloak    │                     │                   │
                │ 1.12 Health chks │                     │                   │
                │ 3.4 Packages     │                     │                   │
                │ 3.5 Mapper beans │                     │                   │
                │ 3.6 ADRs         │                     │                   │
                │ 3.7 Tracing      │                     │                   │
                └───────────────────┴───────────────────┴───────────────────┘
```

## Estimated Total Effort

| Phase | Items | Estimated Duration |
|---|---|---|
| Phase 1 (Quick Wins) | 12 items | 5-7 days |
| Phase 2 (Important) | 9 items | 10-14 days |
| Phase 3 (Polish) | 8 items | 8-12 days |
| **Total** | **29 items** | **23-33 days** |

## Recommended Execution Order Within Phase 1

1. **1.1** Fix balance calculation bug (data integrity is paramount)
2. **1.3** Add input validation (prevent bad data from entering the system)
3. **1.2** Fix exception handlers (proper error reporting)
4. **1.4** Stop logging passwords (security)
5. **1.5 + 1.6** Timeouts and retries (resilience basics)
6. **1.7 + 1.8 + 1.9** API cleanup (type safety, OpenAPI, pagination)
7. **1.10** Keycloak thread safety
8. **1.11 + 1.12** Observability (Prometheus + health checks)
