# Internet Banking Microservices — Remediation Roadmap

This roadmap prioritizes the 31 gaps identified in `GAP_ANALYSIS.md` into three phases:

- **Phase 1 — Quick Wins**: High/Critical severity + Small/Medium effort. Immediate security and correctness fixes.
- **Phase 2 — Important**: High severity + Medium/Large effort. Structural improvements and test coverage.
- **Phase 3 — Polish**: Medium/Low severity improvements for long-term maintainability.

Each item includes a **Devin prompt** that can be executed directly to perform the remediation.

---

## Phase 1 — Quick Wins (1–2 Weeks)

These items address critical security vulnerabilities, data correctness bugs, and easy wins that significantly improve code quality with minimal effort.

### 1.1 Fix Generic Exception Handler — Proper HTTP Status Codes (GAP-2.1, GAP-2.2, GAP-2.3)

**Why first**: All errors returning 400 is both a security risk (information leakage) and an API correctness issue.

**Changes needed**:
- Map `EntityNotFoundException` → HTTP 404
- Map `InsufficientFundsException` → HTTP 422 (Unprocessable Entity)
- Map `UserAlreadyRegisteredException` → HTTP 409 (Conflict)
- Map generic `Exception` → HTTP 500 with safe message (no exception details)
- Add `timestamp`, `path`, and `correlationId` to `ErrorResponse`
- Apply across all 4 services with `GlobalExceptionHandler`

**Devin Prompt**:
```
In the internet-banking-microservices repo, refactor all GlobalExceptionHandler classes
across all services (core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, internet-banking-utility-payment-service).

Changes:
1. Map EntityNotFoundException to HTTP 404 Not Found
2. Map InsufficientFundsException to HTTP 422 Unprocessable Entity
3. Map UserAlreadyRegisteredException to HTTP 409 Conflict
4. Map InvalidEmailException and InvalidBankingUserException to HTTP 400 Bad Request
5. Change the generic Exception handler to return HTTP 500 with a safe message
   "An internal error occurred. Please contact support." — do NOT include exception details
6. Add timestamp (Instant) and path (String) fields to ErrorResponse
7. Ensure all services use the same consistent ErrorResponse format

Do not change any business logic. Run the existing tests to verify nothing breaks.
```

---

### 1.2 Remove Sensitive Data from Logs (GAP-6.4)

**Why now**: Passwords being logged in plaintext is a compliance and security violation.

**Devin Prompt**:
```
In the internet-banking-microservices repo, fix sensitive data logging:

1. In internet-banking-user-service UserController.java, change the log statement
   in createUser() to NOT log the full request (it contains password).
   Instead log: log.info("Creating user with identification {}", request.getIdentification());

2. In internet-banking-user-service UserService.java, ensure no password is logged anywhere.

3. Review all log statements across all services that log request.toString() and ensure
   no sensitive fields (passwords, account balances, full account numbers) are exposed.
   Log only identifiers (account number last 4 digits, user IDs, transaction IDs).

Do not change any business logic.
```

---

### 1.3 Add Input Validation to All API Endpoints (GAP-4.3)

**Why now**: Null/invalid inputs can cause NPEs and corrupt data.

**Devin Prompt**:
```
In the internet-banking-microservices repo, add Bean Validation to all request DTOs
and controller endpoints:

1. Add spring-boot-starter-validation dependency to build.gradle in:
   - core-banking-service
   - internet-banking-fund-transfer-service
   - internet-banking-user-service
   - internet-banking-utility-payment-service

2. Add validation annotations to request DTOs:
   - FundTransferRequest: @NotBlank fromAccount, @NotBlank toAccount,
     @NotNull @Positive amount
   - UtilityPaymentRequest: @NotNull providerId, @NotNull @Positive amount,
     @NotBlank referenceNumber, @NotBlank account
   - User (user-service): @NotBlank email, @Email email, @NotBlank identification,
     @NotBlank password (only on registration)
   - UserUpdateRequest: @NotNull status

3. Add @Valid annotation to all @RequestBody parameters in all controllers.

4. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that
   returns HTTP 400 with field-level error details.

Run existing tests to ensure nothing breaks.
```

---

### 1.4 Externalize Hardcoded Credentials (GAP-4.2)

**Why now**: Credentials in source code are a critical security vulnerability.

**Devin Prompt**:
```
In the internet-banking-microservices repo, externalize all hardcoded credentials
in docker-compose/docker-compose.yml and docker-compose/mysql/privileges.sql:

1. Replace all hardcoded passwords with environment variable references using
   ${VARIABLE_NAME:-default_value} syntax in docker-compose.yml:
   - MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-changeme}
   - KC_DB_PASSWORD: ${KC_DB_PASSWORD:-changeme}
   - KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-changeme}
   - POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}

2. Create a docker-compose/.env.example file with all required variables
   listed (with placeholder values, not real passwords).

3. Add docker-compose/.env to .gitignore to prevent accidental commits.

4. Update privileges.sql to use a parameterized approach or document that
   the password should be changed post-deployment.

5. Add a note in README.md under Installation about copying .env.example
   to .env and setting real passwords before running docker-compose.
```

---

### 1.5 Fix Wrong OpenAPI Dependency (GAP-5.3)

**Devin Prompt**:
```
In the internet-banking-microservices repo, fix the OpenAPI/Swagger dependency in all
MVC-based services. Replace:
  springdoc-openapi-starter-webflux-ui:2.1.0
with:
  springdoc-openapi-starter-webmvc-ui:2.1.0

in the build.gradle of:
- core-banking-service
- internet-banking-fund-transfer-service
- internet-banking-user-service
- internet-banking-utility-payment-service

These services use Spring MVC (spring-boot-starter-web), not WebFlux.
The API Gateway correctly uses WebFlux and does not have this dependency.

Build all services to ensure they compile correctly after the change.
```

---

### 1.6 Add ResponseEntity Type Parameters (GAP-5.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add proper type parameters to all
ResponseEntity return types in all controllers. For example, change:

  public ResponseEntity readUsers(Pageable pageable)
to:
  public ResponseEntity<List<User>> readUsers(Pageable pageable)

Fix this across all controllers in:
- core-banking-service (AccountController, TransactionController, UserController)
- internet-banking-fund-transfer-service (FundTransferController)
- internet-banking-utility-payment-service (UtilityPaymentController)

The internet-banking-user-service already has proper type parameters.
Build all services to ensure they compile.
```

---

### 1.7 Fix TransactionEntity Mapping (GAP-7.6)

**Devin Prompt**:
```
In core-banking-service, fix TransactionEntity.java:

1. Change the @OneToOne mapping on the 'account' field to @ManyToOne.
   Multiple transactions can reference the same bank account.

2. Remove CascadeType.ALL from the mapping. Transactions should not cascade
   modifications to the account entity.

The corrected mapping should be:
  @ManyToOne
  @JoinColumn(name = "account_id", referencedColumnName = "id")
  private BankAccountEntity account;

Run existing TransactionServiceTest to verify nothing breaks.
```

---

### 1.8 Fix KeycloakProperties Thread Safety (GAP-4.6)

**Devin Prompt**:
```
In internet-banking-user-service, fix the thread-safety issue in
KeycloakProperties.java. The current lazy singleton pattern is not thread-safe.

Option A (preferred): Make the Keycloak instance a Spring @Bean:
- Create a @Bean method that builds the Keycloak instance
- Inject it where needed instead of using a static field

Option B (simpler): Add synchronized to getInstance() method.

Use Option A for cleaner Spring integration.
```

---

### 1.9 Fix Double-Deduction Bug in Utility Payment (GAP-7.5)

**Why now**: This is a data corruption bug — utility payments double-deduct the available balance.

**Devin Prompt**:
```
In core-banking-service TransactionService.java, fix the utilPayment() method.

Current code (lines 63-64):
  fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
  fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));

The bug: Line 64 reads the ALREADY-DECREMENTED actualBalance from line 63, then subtracts
the amount AGAIN. This double-deducts the available balance.

Fix to:
  BigDecimal newBalance = fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount());
  fromAccount.setActualBalance(newBalance);
  fromAccount.setAvailableBalance(newBalance);

Apply the same pattern check to internalFundTransfer() — it has the same bug on lines 90-91.

Add unit tests to verify the correct balance after both fund transfer and utility payment.
```

---

### 1.10 Add Feign Client Timeouts (GAP-7.3)

**Devin Prompt**:
```
In the internet-banking-microservices repo, configure explicit timeouts for all
Feign clients.

In each service that uses Feign (fund-transfer-service, utility-payment-service,
user-service), update the CustomFeignClientConfiguration (or create one if missing)
to include:

@Bean
public Request.Options requestOptions() {
    return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
}

This sets a 5-second connect timeout and 10-second read timeout.

Also add this to the application.yml / externalized config as a fallback:
spring.cloud.openfeign.client.config.default.connect-timeout=5000
spring.cloud.openfeign.client.config.default.read-timeout=10000
```

---

### 1.11 Reduce Database Privileges (GAP-4.5)

**Devin Prompt**:
```
In docker-compose/mysql/privileges.sql, reduce the privileges for the application user.

Replace the current broad grant:
  GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO 'javatodev_development'@'%';

With schema-specific minimal grants:
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_service.* TO 'javatodev_development'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_fund_transfer_service.* TO 'javatodev_development'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_user_service.* TO 'javatodev_development'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE ON banking_core_utility_payment_service.* TO 'javatodev_development'@'%';

Note: Flyway migrations in core-banking-service need CREATE/ALTER. Consider a separate
migration user or add CREATE, ALTER only on banking_core_service for the app user.
```

---

## Phase 2 — Important (2–4 Weeks)

These items require more effort but address significant architectural gaps.

### 2.1 Add Circuit Breakers with Resilience4j (GAP-7.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add Resilience4j circuit breakers to all
Feign client calls:

1. Add these dependencies to fund-transfer-service, utility-payment-service, and
   user-service build.gradle:
   - org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j

2. Add @CircuitBreaker annotations to each Feign client interface method with:
   - name matching the service (e.g., "coreBankingService")
   - fallbackMethod pointing to a fallback method

3. Implement fallback methods in dedicated fallback classes that return appropriate
   error responses (e.g., "Core banking service is currently unavailable")

4. Configure circuit breaker properties in application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 3

5. Add retry configuration:
   - maxAttempts: 3
   - waitDuration: 1s
   - retryExceptions: [FeignException.class]
```

---

### 2.2 Add Idempotency Protection (GAP-7.4)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add idempotency key support to write
operations in fund-transfer-service and utility-payment-service:

1. Add an "Idempotency-Key" header parameter to POST endpoints in both controllers.

2. Before processing, check if a transaction with the same idempotency key exists
   in the local database. If yes, return the existing result.

3. Add an idempotency_key column (unique index) to:
   - fund_transfer table
   - utility_payment table

4. Store the idempotency key when creating new records.

5. Add a global filter/interceptor that validates the Idempotency-Key header is
   present on all POST requests (return 400 if missing).
```

---

### 2.3 Add Downstream Service Authentication (GAP-4.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add authentication to downstream services
so they are not fully open if accessed directly (bypassing the gateway):

Option A (recommended for this project): Add a shared API key or internal JWT
validation:
1. Add a shared internal-service-secret to Spring Cloud Config
2. Create a servlet filter in each downstream service that validates an
   "X-Internal-Auth" header contains the expected secret
3. Configure the API Gateway to add this header to all proxied requests
4. Reject requests without the header (return 401)

Option B (full OAuth2): Add spring-boot-starter-oauth2-resource-server to each
downstream service and validate the JWT directly. This is more secure but adds
latency for token validation on every internal call.
```

---

### 2.4 Add Unit Tests for All Services (GAP-3.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add comprehensive unit tests for
the service layer of each service that currently lacks tests:

1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, Feign failure handling,
     status transitions (PENDING → SUCCESS)
   - Test readAllTransfers pagination

2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success, Feign failure handling,
     status transitions (PROCESSING → SUCCESS)
   - Test readPayments pagination

3. internet-banking-user-service:
   - UserServiceTest: test createUser success, duplicate email, invalid NIC,
     email mismatch, Keycloak failure
   - Test readUsers with Keycloak enrichment
   - Test updateUser approval flow (Keycloak enable)
   - KeycloakUserServiceTest: test CRUD operations with mocked KeycloakManager

4. internet-banking-api-gateway:
   - SecurityConfigurationTest: test that /register is open, /actuator is open,
     and other paths require auth

Use Mockito for mocking. Follow the pattern established in core-banking-service tests.
Target: >80% line coverage for service layer classes.
```

---

### 2.5 Add Feign Error Decoders (GAP-2.4)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add proper Feign error decoding to
fund-transfer-service and utility-payment-service (user-service already has one):

1. Create a CustomFeignErrorDecoder class in each service that implements
   feign.codec.ErrorDecoder.

2. Map HTTP status codes from core-banking-service to appropriate exceptions:
   - 404 → EntityNotFoundException (e.g., account not found)
   - 422 → InsufficientFundsException
   - 400 → SimpleBankingGlobalException
   - 5xx → a ServiceUnavailableException (new exception)

3. Register the error decoder in CustomFeignClientConfiguration.

4. This ensures that errors from core-banking-service are properly translated
   rather than surfacing as raw FeignException to the client.
```

---

### 2.6 Extract Shared Library (GAP-1.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, create a shared library module to
eliminate code duplication:

1. Create a new module: internet-banking-common
2. Move these shared classes into it:
   - BaseMapper<E,D> interface
   - AuditAware MappedSuperclass
   - AuditorAwareConfig and AuditConfig
   - GlobalExceptionHandler (parameterized)
   - ErrorResponse DTO
   - SimpleBankingGlobalException
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
3. Publish to local Maven repo or use Gradle composite builds
4. Update all services to depend on internet-banking-common
5. Remove the duplicated classes from each service
6. Build all services and run all tests to verify
```

---

### 2.7 Add Pagination Metadata to List Endpoints (GAP-5.2)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add proper pagination metadata to
all list endpoints:

1. Create a generic PageResponse<T> DTO in the shared library (or in each service):
   - content: List<T>
   - page: int
   - size: int
   - totalElements: long
   - totalPages: int

2. Update all services to return PageResponse instead of raw List:
   - core-banking-service: GET /api/v1/user
   - fund-transfer-service: GET /api/v1/transfer
   - utility-payment-service: GET /api/v1/utility-payment
   - user-service: GET /api/v1/bank-users

3. Use the Spring Data Page object to populate the metadata.
```

---

## Phase 3 — Polish (4–8 Weeks)

These items improve long-term maintainability and operational excellence.

### 3.1 Add Structured JSON Logging (GAP-6.1)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add structured JSON logging:

1. Add logstash-logback-encoder dependency to all services
2. Create a logback-spring.xml in each service's resources that:
   - Outputs JSON format in Docker/production profile
   - Outputs plain text in local/dev profile
3. Include trace ID, span ID, service name, and timestamp in every log line
4. Review and standardize all log statements across the codebase
```

---

### 3.2 Add Custom Health Checks (GAP-6.2)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add custom health indicators:

1. In core-banking-service: add a DataSource health indicator (auto from Actuator)
   and expose management.endpoints.web.exposure.include=health,info,prometheus

2. In fund-transfer-service and utility-payment-service: add a custom
   HealthIndicator that checks core-banking-service availability via Feign

3. In user-service: add health indicators for both core-banking-service
   and Keycloak availability

4. In api-gateway: add a composite health check for all downstream services

5. Configure all health endpoints in the externalized config repo
```

---

### 3.3 Add Business Metrics (GAP-6.3)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add custom Micrometer metrics:

1. Add Prometheus registry: micrometer-registry-prometheus to all services
2. In core-banking-service:
   - Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT)
   - Counter: banking.transactions.failed
   - Histogram: banking.transaction.amount
3. In fund-transfer-service:
   - Counter: fund_transfer.requests.total (tags: status=SUCCESS|FAILED)
4. In utility-payment-service:
   - Counter: utility_payment.requests.total (tags: status=SUCCESS|FAILED)
5. In user-service:
   - Counter: user.registrations.total (tags: status=SUCCESS|FAILED)
6. Add a docker-compose service for Prometheus with scrape config for all services
```

---

### 3.4 Add JaCoCo Test Coverage Reporting (GAP-3.3)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add JaCoCo coverage reporting:

1. Add the JaCoCo Gradle plugin to each service's build.gradle
2. Configure minimum coverage thresholds:
   - Line coverage: 70% (service layer)
   - Branch coverage: 60%
3. Add jacocoTestReport and jacocoTestCoverageVerification tasks
4. Configure HTML and XML report generation
5. Exclude auto-generated classes (Lombok, MapStruct) from coverage
```

---

### 3.5 Create Multi-Module Gradle Build (GAP-1.2)

**Devin Prompt**:
```
In the internet-banking-microservices repo, create a root Gradle multi-module build:

1. Create a root settings.gradle.kts that includes all 6 services + shared library
2. Create a root build.gradle.kts with:
   - Shared plugin versions in plugins block
   - Common dependencies in subprojects block
   - Shared repository configuration
3. Create a libs.versions.toml version catalog for dependency management
4. Verify: ./gradlew build from root builds all services
5. Verify: ./gradlew test from root runs all tests
```

---

### 3.6 Add Integration Tests (GAP-3.2)

**Devin Prompt**:
```
In the internet-banking-microservices repo, add integration tests:

1. Add Testcontainers dependency to services that use MySQL:
   - core-banking-service
   - fund-transfer-service
   - user-service
   - utility-payment-service

2. Create @SpringBootTest integration tests for core-banking-service:
   - Test full fund transfer flow against real MySQL
   - Test full utility payment flow
   - Test account lookup endpoints
   - Verify Flyway migrations run successfully

3. Create WireMock-based integration tests for fund-transfer-service:
   - Mock core-banking-service responses
   - Test happy path and error scenarios

4. Add a spring-cloud-contract test between fund-transfer-service and
   core-banking-service to verify API compatibility.
```

---

### 3.7 Standardize Package Structure (GAP-1.3)

**Devin Prompt**:
```
In the internet-banking-microservices repo, standardize the package structure
across all services to follow this convention:

com.javatodev.finance/
├── configuration/     (Spring beans, filters, Feign config)
├── controller/        (REST controllers)
├── exception/         (Exception classes, handler)
├── model/
│   ├── dto/           (Data transfer objects)
│   │   ├── request/   (Request DTOs)
│   │   └── response/  (Response DTOs)
│   ├── entity/        (JPA entities)
│   └── mapper/        (Entity-DTO mappers)
├── repository/        (Spring Data repositories)
└── service/           (Business logic)
    └── rest/          (Feign clients)

Refactor utility-payment-service and user-service packages to match this structure.
Update all imports. Build and test to verify.
```

---

### 3.8 Set Up CI/CD Pipeline

**Devin Prompt**:
```
In the internet-banking-microservices repo, create a GitHub Actions CI pipeline:

1. Create .github/workflows/ci.yml that:
   - Triggers on push to main and pull requests
   - Uses Java 21 and Gradle
   - Runs ./gradlew build for all services
   - Runs ./gradlew test for all services
   - Generates JaCoCo coverage reports
   - Uploads test reports as artifacts
   - Builds Docker images (without pushing)

2. Add a Dependabot configuration (.github/dependabot.yml) for:
   - Gradle dependencies (weekly)
   - Docker base images (weekly)
   - GitHub Actions (weekly)
```

---

## Priority Matrix

```
                    Small Effort       Medium Effort       Large Effort
                ┌──────────────────┬──────────────────┬──────────────────┐
   Critical     │ GAP-4.2 Creds    │ GAP-4.1 Auth     │ GAP-3.1 Tests   │
                │                  │ GAP-4.3 Validate │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
   High         │ GAP-2.1 HTTP 400 │ GAP-2.4 Feign Err│ GAP-3.2 Integ.  │
                │ GAP-2.2 Leak     │ GAP-7.1 Circuit  │                  │
                │ GAP-6.4 PW Log   │ GAP-7.4 Idempot. │                  │
                │ GAP-4.4 Secrets  │ GAP-7.5 Atomicity│                  │
                │                  │ GAP-1.1 Shared   │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
   Medium       │ GAP-5.1 Types    │ GAP-5.2 Paginate │ GAP-6.1 JSON Log│
                │ GAP-5.3 OpenAPI  │ GAP-6.3 Metrics  │                  │
                │ GAP-7.2 Retry    │ GAP-1.2 Gradle   │                  │
                │ GAP-7.3 Timeout  │                  │                  │
                │ GAP-4.5 DB Privs │                  │                  │
                │ GAP-4.6 Thread   │                  │                  │
                │ GAP-6.2 Health   │                  │                  │
                │ GAP-7.6 OneToOne │                  │                  │
                │ GAP-3.3 JaCoCo   │                  │                  │
                │ GAP-2.3 ErrorFmt │                  │                  │
                ├──────────────────┼──────────────────┼──────────────────┤
   Low          │ GAP-1.3 Packages │ GAP-5.5 Version  │                  │
                │ GAP-5.4 URLs     │                  │                  │
                └──────────────────┴──────────────────┴──────────────────┘
```
