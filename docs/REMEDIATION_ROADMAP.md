# Remediation Roadmap

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## Phasing Strategy

| Phase | Focus | Timeline | Selection Criteria |
|---|---|---|---|
| **Phase 1 — Quick Wins** | Critical severity + Small effort; High severity + Small effort | 1-2 weeks | Immediate risk reduction with minimal code change |
| **Phase 2 — Important** | Critical + Medium/Large effort; High + Medium effort; Medium severity items that block Phase 3 | 3-6 weeks | Structural improvements and foundational changes |
| **Phase 3 — Polish** | Remaining Medium items; Low severity items; Enhancements | 6-12 weeks | Production-grade maturity and operational excellence |

---

## Phase 1 — Quick Wins (1-2 Weeks)

### 1.1 Fix Balance Double-Debit Bug
**Gap:** GAP-SE-03 | **Severity:** Critical | **Effort:** Small

Fix the `availableBalance` calculation in `TransactionService.java` where the amount is subtracted twice due to using the already-decremented `actualBalance`.

**Files:** `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java`

**Sample Devin Prompt:**
```
In core-banking-service TransactionService.java, fix the balance double-debit bug.

In internalFundTransfer():
- Line 88: fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))
  should be: fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getAvailableBalance().subtract(amount))
- Line 95: toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount))
  should be: toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getAvailableBalance().add(amount))

Apply the same fix in utilPayment() (lines 63-64).

Also add unit tests in TransactionServiceTest that assert both actualBalance and availableBalance
after fund transfers and utility payments to prevent regression.
```

---

### 1.2 Stop Leaking Exception Stack Traces
**Gap:** GAP-EH-01 | **Severity:** Critical | **Effort:** Small

Replace the catch-all `Exception` handler in all four `GlobalExceptionHandler` classes to return a generic error message instead of `e.toString()`.

**Files:**
- `core-banking-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-user-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-fund-transfer-service/.../exception/GlobalExceptionHandler.java`
- `internet-banking-utility-payment-service/.../exception/GlobalExceptionHandler.java`

**Sample Devin Prompt:**
```
In all four GlobalExceptionHandler.java files across core-banking-service,
internet-banking-user-service, internet-banking-fund-transfer-service, and
internet-banking-utility-payment-service:

1. Change the catch-all handleException method to return a generic ErrorResponse
   instead of concatenating the exception message:
   - Return: new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred. Please try again.")
   - Log the full exception at ERROR level: log.error("Unhandled exception", e)
   - Return HTTP 500 instead of 400

2. Update handleGlobalException to return appropriate HTTP status codes:
   - EntityNotFoundException -> 404
   - InsufficientFundsException -> 409
   - Other SimpleBankingGlobalException -> 400

Add @Slf4j to each class if not already present.
```

---

### 1.3 Fix HTTP Status Codes in Error Responses
**Gap:** GAP-EH-02 | **Severity:** High | **Effort:** Small

All errors currently return HTTP 400. Map exceptions to correct HTTP status codes.

**Sample Devin Prompt:**
```
In all four GlobalExceptionHandler.java files, update the handleGlobalException method
to return appropriate HTTP status codes based on the error code:

- GlobalErrorCode.NOT_FOUND -> HttpStatus.NOT_FOUND (404)
- GlobalErrorCode.INSUFFICIENT_FUNDS -> HttpStatus.CONFLICT (409)
- GlobalErrorCode.DUPLICATE_ENTITY -> HttpStatus.CONFLICT (409)
- All other GlobalErrorCode values -> HttpStatus.BAD_REQUEST (400)

Make sure GlobalErrorCode enum exists with these values, or add them if missing.
Update ErrorResponse to include a timestamp field.
```

---

### 1.4 Add Type Parameters to ResponseEntity
**Gap:** GAP-AD-01 | **Severity:** High | **Effort:** Small

Add generic type parameters to all controller `ResponseEntity` return types.

**Sample Devin Prompt:**
```
Across all controller classes in core-banking-service (AccountController, TransactionController,
UserController), internet-banking-fund-transfer-service (FundTransferController),
internet-banking-utility-payment-service (UtilityPaymentController), and
internet-banking-user-service (UserController):

Add explicit type parameters to all ResponseEntity return types. For example:
- ResponseEntity getBankAccount(...) -> ResponseEntity<BankAccount> getBankAccount(...)
- ResponseEntity getUsers(...) -> ResponseEntity<List<User>> getUsers(...)
- ResponseEntity fundTransfer(...) -> ResponseEntity<FundTransferResponse> fundTransfer(...)

This ensures correct OpenAPI documentation generation and compile-time type safety.
```

---

### 1.5 Restrict Actuator Endpoints
**Gap:** GAP-SE-05 | **Severity:** High | **Effort:** Small

Remove permitAll rules for actuator endpoints in the API Gateway security config.

**Files:** `internet-banking-api-gateway/.../configuration/security/SecurityConfiguration.java`

**Sample Devin Prompt:**
```
In internet-banking-api-gateway SecurityConfiguration.java, update the security
configuration to restrict actuator endpoints:

1. Remove the permitAll rules for:
   - /actuator/**
   - /user/actuator/**
   - /fund-transfer/actuator/**
   - /banking-core/actuator/**
   - /utility-payment/actuator/**

2. Only permit /actuator/health and /actuator/info publicly:
   exchanges.pathMatchers("/actuator/health", "/actuator/info").permitAll()

3. All other actuator endpoints should require authentication (covered by anyExchange().authenticated()).
```

---

### 1.6 Add @JsonProperty(WRITE_ONLY) to Password Field
**Gap:** GAP-SE-04 | **Severity:** High | **Effort:** Small

Prevent password from appearing in API responses.

**Files:** `internet-banking-user-service/.../model/dto/User.java`

**Sample Devin Prompt:**
```
In internet-banking-user-service User.java DTO, add @JsonProperty annotation to the
password field to prevent it from being serialized in API responses:

import com.fasterxml.jackson.annotation.JsonProperty;

@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String password;

This ensures the password is accepted during registration (deserialization)
but never returned in GET responses (serialization).
```

---

### 1.7 Configure Feign Timeouts
**Gap:** GAP-RE-03 | **Severity:** High | **Effort:** Small

Add explicit timeout configuration for all Feign clients.

**Sample Devin Prompt:**
```
Add Feign timeout configuration to each service that uses Feign clients.

In the Spring Cloud Config repository (or in each service's application.yml if config
repo is not writable), add:

spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 5000
            logger-level: BASIC

Apply this to: internet-banking-user-service, internet-banking-fund-transfer-service,
and internet-banking-utility-payment-service.

Also add a global gateway timeout in the API Gateway configuration:

spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 2000
        response-timeout: 10s
```

---

### 1.8 Add Retry Policies for Idempotent Operations
**Gap:** GAP-RE-02 | **Severity:** High | **Effort:** Small

Configure retry for GET requests only.

**Sample Devin Prompt:**
```
Add Resilience4j retry support to the three Feign-consumer services
(user-service, fund-transfer-service, utility-payment-service).

1. Add dependency to each service's build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Configure retry for GET (read) operations only in application.yml:

resilience4j:
  retry:
    instances:
      bankingCoreRead:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - feign.RetryableException

Do NOT configure retry for POST operations (fund transfers, payments) as they
are not idempotent. These will be addressed separately with idempotency keys.
```

---

### 1.9 Set Pagination Limits
**Gap:** GAP-AD-02 | **Severity:** High | **Effort:** Small

Prevent unbounded page sizes on list endpoints.

**Sample Devin Prompt:**
```
Add pagination limits to all services that expose paginated endpoints.

In each service's application.yml (core-banking, user-service, fund-transfer, utility-payment), add:

spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100

This prevents clients from requesting unbounded page sizes
(e.g., ?size=1000000) which could cause OOM errors.
```

---

### 1.10 Mask Sensitive Data in Logs
**Gap:** GAP-OB-05 | **Severity:** Low | **Effort:** Small

Reduce Feign log level and exclude sensitive fields from toString.

**Sample Devin Prompt:**
```
1. In internet-banking-user-service User.java DTO, add Lombok @ToString.Exclude
   to the password field:

   @ToString.Exclude
   private String password;

2. In internet-banking-fund-transfer-service CustomFeignClientConfiguration.java,
   change Feign logger level from FULL to BASIC:

   return Logger.Level.BASIC;

3. In FundTransferService.java line 32, fix the logging bug:
   Change: log.info("Sending fund transfer request {}" + request.toString());
   To:     log.info("Sending fund transfer request {}", request);
```

---

## Phase 2 — Important (3-6 Weeks)

### 2.1 Add Input Validation to All Endpoints
**Gap:** GAP-SE-02 | **Severity:** Critical | **Effort:** Medium

Add Bean Validation across all DTOs and controller methods.

**Sample Devin Prompt:**
```
Add input validation to all request DTOs across all services.

1. Add dependency to each service's build.gradle:
   implementation 'org.springframework.boot:spring-boot-starter-validation'

2. Add validation annotations to DTOs:

   Core Banking FundTransferRequest:
     @NotBlank private String fromAccount;
     @NotBlank private String toAccount;
     @NotNull @Positive private BigDecimal amount;

   Core Banking UtilityPaymentRequest:
     @NotNull private Long providerId;
     @NotNull @Positive private BigDecimal amount;
     @NotBlank private String referenceNumber;
     @NotBlank private String account;

   User Service User (registration):
     @NotBlank @Email private String email;
     @NotBlank @Size(min=8) private String password;
     @NotBlank private String identification;

   Fund Transfer Service FundTransferRequest:
     @NotBlank private String fromAccount;
     @NotBlank private String toAccount;
     @NotNull @Positive private BigDecimal amount;

   Utility Payment Service UtilityPaymentRequest:
     @NotNull private Long providerId;
     @NotNull @Positive private BigDecimal amount;
     @NotBlank private String referenceNumber;
     @NotBlank private String account;

3. Add @Valid to all @RequestBody parameters in controllers.

4. Add a MethodArgumentNotValidException handler in GlobalExceptionHandler
   that returns HTTP 422 with field-level error details.
```

---

### 2.2 Implement Circuit Breakers
**Gap:** GAP-RE-01 | **Severity:** Critical | **Effort:** Medium

Add Resilience4j circuit breakers on all Feign clients.

**Sample Devin Prompt:**
```
Add Resilience4j circuit breakers to the three Feign-consumer services.

1. Ensure dependency is present (added in Phase 1 retry step):
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable CircuitBreaker for Feign in application.yml:

spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

resilience4j:
  circuitbreaker:
    instances:
      bankingCoreService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-rate-threshold: 80
        slow-call-duration-threshold: 3s

3. Add @CircuitBreaker annotations or configure via Feign client names.

4. Implement fallback classes for each Feign client that return meaningful
   error responses (e.g., "Core Banking service is temporarily unavailable").
```

---

### 2.3 Externalize All Secrets
**Gap:** GAP-SE-01 | **Severity:** Critical | **Effort:** Medium

Move all hardcoded credentials to environment variables or Docker secrets.

**Sample Devin Prompt:**
```
Remove all hardcoded credentials from version control and replace with environment variables.

1. docker-compose/mysql/Dockerfile:
   Change: ENV MYSQL_ROOT_PASSWORD woVERANKliGharym
   To:     ENV MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}

2. docker-compose/mysql/privileges.sql:
   Replace with a template that reads from environment variables,
   or move user creation to an entrypoint script:
   CREATE USER '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASSWORD}';

3. docker-compose/docker-compose.yml:
   Replace all hardcoded passwords with ${VARIABLE} references.
   Create a .env.example file with placeholder values.
   Add .env to .gitignore.

4. Create .env.example:
   MYSQL_ROOT_PASSWORD=changeme
   DB_USER=javatodev_development
   DB_PASSWORD=changeme
   KEYCLOAK_ADMIN_PASSWORD=changeme
   KEYCLOAK_DB_PASSWORD=changeme

5. Add .env to .gitignore if not already there.

6. Update README.md to document the new setup:
   cp .env.example .env
   # Edit .env with real values
   docker-compose up
```

---

### 2.4 Implement Role-Based Access Control
**Gap:** GAP-SE-06 | **Severity:** High | **Effort:** Medium

Add RBAC using Keycloak roles propagated through JWT.

**Sample Devin Prompt:**
```
Implement role-based access control using Keycloak realm roles in the JWT.

1. In the API Gateway SecurityConfiguration.java, add role-based route protection:

   exchanges.pathMatchers("/user/api/v1/bank-users/register").permitAll()
   exchanges.pathMatchers(HttpMethod.PATCH, "/user/api/v1/bank-users/update/**")
       .hasRole("ADMIN")
   exchanges.pathMatchers(HttpMethod.GET, "/user/api/v1/bank-users")
       .hasRole("ADMIN")
   exchanges.anyExchange().authenticated()

2. Add a JwtGrantedAuthoritiesConverter that extracts Keycloak realm roles
   from the JWT claim "realm_access.roles":

   @Bean
   public ReactiveJwtDecoder jwtDecoder() { ... }

   @Bean
   public Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
       JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
       converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
       return new ReactiveJwtAuthenticationConverterAdapter(converter);
   }

3. Create KeycloakRealmRoleConverter class that reads realm_access.roles
   from the JWT and maps them to Spring Security GrantedAuthority.

4. Configure Keycloak realm-export.json to include ADMIN and USER roles.
```

---

### 2.5 Add Unit Tests to All Services
**Gap:** GAP-TS-01 | **Severity:** Critical | **Effort:** Large

Write unit tests for all service-layer classes.

**Sample Devin Prompt:**
```
Add comprehensive unit tests for all service classes across the application.
Use JUnit 5, Mockito, and AssertJ. Target 80%+ line coverage on service classes.

Priority order:
1. internet-banking-fund-transfer-service FundTransferService:
   - Test successful fund transfer flow
   - Test Feign client error handling (mock BankingCoreFeignClient)
   - Verify entity status transitions (PENDING -> SUCCESS)
   - Verify transaction reference is saved

2. internet-banking-utility-payment-service UtilityPaymentService:
   - Test successful payment flow
   - Test Feign client error handling
   - Verify entity status transitions (PROCESSING -> SUCCESS)

3. internet-banking-user-service UserService:
   - Test registration flow (happy path)
   - Test duplicate email rejection
   - Test user not found in Core Banking
   - Test email mismatch
   - Test user approval flow
   - Test readUsers with Keycloak enrichment

4. internet-banking-user-service KeycloakUserService:
   - Test user creation in Keycloak
   - Test user read
   - Test user enable/email verify

For each test class, use @ExtendWith(MockitoExtension.class), mock all
dependencies with @Mock, and inject via @InjectMocks.
```

---

### 2.6 Add Integration Tests with Testcontainers
**Gap:** GAP-TS-02 | **Severity:** High | **Effort:** Large

Write integration tests that verify JPA repositories and controller endpoints.

**Sample Devin Prompt:**
```
Add integration tests to core-banking-service using Testcontainers for MySQL.

1. Add test dependencies to build.gradle:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'

2. Create a base test class with MySQL Testcontainer:

   @Testcontainers
   @SpringBootTest
   abstract class BaseIntegrationTest {
       @Container
       static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
           .withDatabaseName("banking_core_service");

       @DynamicPropertySource
       static void configureProperties(DynamicPropertyRegistry registry) {
           registry.add("spring.datasource.url", mysql::getJdbcUrl);
           registry.add("spring.datasource.username", mysql::getUsername);
           registry.add("spring.datasource.password", mysql::getPassword);
       }
   }

3. Write integration tests for:
   - AccountController: GET account by number, account not found
   - TransactionController: fund transfer end-to-end with balance verification
   - UserController: get user, user not found, paginated list

4. Use @AutoConfigureMockMvc and MockMvc to test controller layer with
   real database and Flyway migrations.
```

---

### 2.7 Create Shared Library Module
**Gap:** GAP-CO-01, GAP-CO-02 | **Severity:** High | **Effort:** Large

Extract common code into a shared library and create a multi-module build.

**Sample Devin Prompt:**
```
Create a shared library module and convert the project to a multi-module Gradle build.

1. Create a root settings.gradle that includes all subprojects:
   rootProject.name = 'internet-banking-microservices'
   include 'banking-common'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-user-service'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-utility-payment-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-config-server'

2. Create banking-common/build.gradle as a plain Java library.

3. Move these classes to banking-common:
   - exception/GlobalExceptionHandler.java
   - exception/SimpleBankingGlobalException.java
   - model/dto/ErrorResponse.java
   - model/mapper/BaseMapper.java
   - model/dto/AuditAware.java
   - configuration/AuditConfig.java + AuditorAwareConfig.java
   - filter/AppAuthUserFilter.java
   - filter/ApiRequestContext.java + ApiRequestContextHolder.java

4. Add dependency in each service's build.gradle:
   implementation project(':banking-common')

5. Remove the duplicated classes from each service.
6. Verify all services compile and tests pass.
```

---

### 2.8 Migrate from Bootstrap to Config Import
**Gap:** GAP-CO-05 | **Severity:** Medium | **Effort:** Medium

Replace deprecated `spring-cloud-starter-bootstrap` with `spring.config.import`.

**Sample Devin Prompt:**
```
Migrate all services from Spring Cloud bootstrap context to spring.config.import.

For each service (user-service, fund-transfer-service, utility-payment-service, core-banking-service, api-gateway):

1. Remove dependency: implementation 'org.springframework.cloud:spring-cloud-starter-bootstrap'

2. Move Config Server connection settings from bootstrap.yml to application.yml:

   spring:
     config:
       import: "configserver:http://config-server:8090"
     cloud:
       config:
         fail-fast: true

3. Move the spring.application.name from bootstrap.yml to application.yml.

4. Delete the bootstrap.yml and bootstrap-*.yml files.

5. Verify each service starts correctly and loads configuration from Config Server.
```

---

### 2.9 Add Centralized Logging with JSON Format
**Gap:** GAP-OB-01 | **Severity:** High | **Effort:** Medium

Add structured JSON logging with trace correlation.

**Sample Devin Prompt:**
```
Add structured JSON logging to all services for centralized log aggregation.

1. Add dependency to each service's build.gradle:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create src/main/resources/logback-spring.xml in each service:

   <configuration>
     <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
       <encoder class="net.logstash.logback.encoder.LogstashEncoder">
         <includeMdcKeyName>traceId</includeMdcKeyName>
         <includeMdcKeyName>spanId</includeMdcKeyName>
       </encoder>
     </appender>
     <root level="INFO">
       <appender-ref ref="JSON"/>
     </root>
   </configuration>

3. This integrates with the existing Micrometer tracing to include traceId
   and spanId in every log line for distributed trace correlation.
```

---

### 2.10 Add Prometheus Metrics
**Gap:** GAP-OB-02 | **Severity:** Medium | **Effort:** Small

Enable Prometheus metrics endpoint for all services.

**Sample Devin Prompt:**
```
Add Prometheus metrics support to all services.

1. Add dependency to each service's build.gradle:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Expose Prometheus endpoint in application.yml:

   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
     metrics:
       tags:
         application: ${spring.application.name}

3. Add a Prometheus + Grafana stack to docker-compose-support-apps.yml:

   prometheus:
     image: prom/prometheus:v2.51.0
     ports:
       - "9090:9090"
     volumes:
       - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

   grafana:
     image: grafana/grafana:10.4.0
     ports:
       - "3000:3000"

4. Create docker-compose/prometheus/prometheus.yml with scrape configs
   for all services.
```

---

### 2.11 Add Contract Tests
**Gap:** GAP-TS-03 | **Severity:** High | **Effort:** Medium

Implement consumer-driven contract tests between services.

**Sample Devin Prompt:**
```
Add Spring Cloud Contract tests between consumer services and Core Banking (provider).

1. In core-banking-service, add Spring Cloud Contract Verifier:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'

2. Create contract definitions in core-banking-service/src/test/resources/contracts/:
   - fundTransfer.groovy: POST /api/v1/transaction/fund-transfer
   - utilPayment.groovy: POST /api/v1/transaction/util-payment
   - getBankAccount.groovy: GET /api/v1/account/bank-account/{number}

3. Generate and run provider-side tests from contracts.

4. In consumer services (fund-transfer, utility-payment, user-service), add:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side tests that use stub runner to verify Feign client
   compatibility with the provider contract stubs.
```

---

## Phase 3 — Polish (6-12 Weeks)

### 3.1 Implement Saga/Outbox Pattern for Distributed Transactions
**Gap:** GAP-RE-04 | **Severity:** Medium | **Effort:** Large

Add compensation logic for distributed transaction failures.

**Sample Devin Prompt:**
```
Implement the Transactional Outbox pattern for fund transfer and utility payment services
to handle distributed transaction failures.

1. Create an outbox table in each consumer service's database:
   CREATE TABLE outbox_event (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     aggregate_type VARCHAR(255) NOT NULL,
     aggregate_id VARCHAR(255) NOT NULL,
     event_type VARCHAR(255) NOT NULL,
     payload JSON NOT NULL,
     status VARCHAR(20) DEFAULT 'PENDING',
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     processed_at TIMESTAMP NULL
   );

2. In FundTransferService.fundTransfer():
   - Save the FundTransferEntity and an OutboxEvent in the same transaction
   - A scheduled job polls for PENDING outbox events and calls Core Banking
   - On success: update both outbox event and fund transfer entity
   - On failure: mark outbox event as FAILED, set fund transfer to FAILED

3. Add a @Scheduled reconciliation job that:
   - Finds PENDING fund transfers older than 5 minutes
   - Checks Core Banking for the transaction status
   - Updates accordingly

4. Apply the same pattern to UtilityPaymentService.
```

---

### 3.2 Add Idempotency Keys
**Gap:** GAP-RE-05 | **Severity:** Medium | **Effort:** Medium

Prevent duplicate transactions from client retries.

**Sample Devin Prompt:**
```
Add idempotency key support to fund transfer and utility payment endpoints.

1. Create an idempotency_key table in each consumer service:
   CREATE TABLE idempotency_key (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     key_value VARCHAR(255) NOT NULL UNIQUE,
     response_body TEXT,
     http_status INT,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     expires_at TIMESTAMP NOT NULL
   );

2. Add an IdempotencyKeyFilter (or Spring interceptor) that:
   - Reads X-Idempotency-Key header from request
   - If key exists and has a stored response, return the cached response
   - If key is new, proceed with request and store the response
   - If no header is present, proceed normally (backward compatible)

3. Apply the filter to:
   - POST /api/v1/transfer (fund-transfer-service)
   - POST /api/v1/utility-payment (utility-payment-service)

4. Add a scheduled cleanup job that deletes expired keys (older than 24h).
```

---

### 3.3 Add Filtering and Sorting to List Endpoints
**Gap:** GAP-AD-06 | **Severity:** Low | **Effort:** Medium

Enhance list endpoints with query-based filtering.

**Sample Devin Prompt:**
```
Add filtering and sorting support to all paginated list endpoints.

1. Fund Transfer Service GET /api/v1/transfer:
   Add query params: status, fromAccount, toAccount, minAmount, maxAmount, fromDate, toDate
   Use Spring Data JPA Specifications for dynamic query building.

2. Utility Payment Service GET /api/v1/utility-payment:
   Add query params: status, providerId, account, fromDate, toDate

3. Core Banking Service GET /api/v1/user:
   Add query params: email, firstName, lastName

4. User Service GET /api/v1/bank-users:
   Add query params: status, identification

For each, create a Specification class that builds dynamic WHERE clauses
from the query parameters. Use Pageable sort parameter support
(e.g., ?sort=createdDate,desc).
```

---

### 3.4 Add CORS Configuration
**Gap:** GAP-SE-08 | **Severity:** Medium | **Effort:** Small

Configure CORS properly alongside disabled CSRF.

**Sample Devin Prompt:**
```
Add CORS configuration to the API Gateway since CSRF is disabled.

In SecurityConfiguration.java, add a CorsConfigurationSource bean:

@Bean
public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("${app.cors.allowed-origins}"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
}

Add app.cors.allowed-origins to the config server properties so it can be
configured per environment.
```

---

### 3.5 Make Config Server Use Private Repository
**Gap:** GAP-SE-07 | **Severity:** Medium | **Effort:** Small

Move configurations to a private repository with authentication.

**Sample Devin Prompt:**
```
Migrate Config Server from public GitHub repo to a private repository.

1. Create a private Git repository for configurations.

2. Update internet-banking-config-server application.yml:

   spring:
     cloud:
       config:
         server:
           git:
             uri: ${CONFIG_REPO_URI}
             username: ${CONFIG_REPO_USERNAME}
             password: ${CONFIG_REPO_TOKEN}
             search-paths: configuration
             default-label: main

3. Add CONFIG_REPO_URI, CONFIG_REPO_USERNAME, CONFIG_REPO_TOKEN to the
   .env file (and .env.example with placeholders).

4. Enable Config Server encryption for sensitive properties:
   encrypt:
     key: ${ENCRYPT_KEY}

5. Encrypt database passwords and Keycloak secrets in the config files
   using: curl -X POST http://config-server:8090/encrypt -d 'plaintext'
```

---

### 3.6 Add Custom Health Indicators
**Gap:** GAP-OB-04 | **Severity:** Medium | **Effort:** Small

Add health checks for critical dependencies.

**Sample Devin Prompt:**
```
Add custom health indicators to verify connectivity to critical dependencies.

1. In internet-banking-user-service, add KeycloakHealthIndicator:
   @Component
   public class KeycloakHealthIndicator implements HealthIndicator {
       @Override
       public Health health() {
           // Try to get Keycloak server info
           // Return Health.up() or Health.down()
       }
   }

2. In all services, add ConfigServerHealthIndicator that verifies
   config server is reachable.

3. Add Docker Compose healthcheck blocks:
   healthcheck:
     test: ["CMD", "curl", "-f", "http://localhost:PORT/actuator/health"]
     interval: 30s
     timeout: 10s
     retries: 3
     start_period: 60s

4. Configure management.endpoint.health.show-details=when-authorized
   so health details are visible to authenticated users.
```

---

### 3.7 Add Custom Business Metrics
**Gap:** GAP-OB-03 | **Severity:** Medium | **Effort:** Small

Instrument key business operations for monitoring.

**Sample Devin Prompt:**
```
Add custom Micrometer metrics to track key business operations.

1. In core-banking-service TransactionService, add:
   - Counter: banking.fund_transfer.count (tags: status=success|failed)
   - Counter: banking.utility_payment.count (tags: status=success|failed)
   - Timer: banking.fund_transfer.duration
   - Gauge: banking.accounts.total

2. In internet-banking-user-service UserService, add:
   - Counter: banking.user.registration.count (tags: status=success|failed)
   - Counter: banking.user.approval.count

3. Inject MeterRegistry into service classes:
   private final MeterRegistry meterRegistry;

   meterRegistry.counter("banking.fund_transfer.count", "status", "success").increment();

4. Create a Grafana dashboard JSON that visualizes these metrics
   alongside the default JVM and HTTP metrics.
```

---

### 3.8 Fix OpenAPI Dependency and Add Complete Annotations
**Gap:** GAP-CO-04, GAP-AD-07 | **Severity:** Low | **Effort:** Small

Use the correct OpenAPI dependency and add comprehensive API documentation.

**Sample Devin Prompt:**
```
Fix OpenAPI dependencies and add complete API documentation annotations.

1. In core-banking-service, internet-banking-user-service,
   internet-banking-fund-transfer-service, internet-banking-utility-payment-service:

   Change: implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
   To:     implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.4.0'

   (These are servlet/MVC services, not WebFlux.)

2. Add @Tag annotations to all controller classes:
   @Tag(name = "Fund Transfer", description = "Fund transfer operations")

3. Add @Operation annotations to all endpoints with:
   - summary
   - description
   - @ApiResponse for success and error cases

4. Add @Schema annotations to DTOs with descriptions and examples.

5. Add OpenAPI configuration class with API metadata (title, version, description).
```

---

### 3.9 CI/CD Pipeline Setup
**Gap:** (Not in formal gap list but identified in Knowledge Base) | **Effort:** Large

Create a CI/CD pipeline for automated build, test, and deploy.

**Sample Devin Prompt:**
```
Create a GitHub Actions CI/CD pipeline for the microservices project.

1. Create .github/workflows/ci.yml:

   name: CI
   on: [push, pull_request]
   jobs:
     build-and-test:
       runs-on: ubuntu-latest
       strategy:
         matrix:
           service:
             - core-banking-service
             - internet-banking-api-gateway
             - internet-banking-user-service
             - internet-banking-fund-transfer-service
             - internet-banking-utility-payment-service
             - internet-banking-service-registry
             - internet-banking-config-server
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with:
             java-version: '21'
             distribution: 'temurin'
         - name: Build and test
           run: cd ${{ matrix.service }} && ./gradlew build
         - name: Upload test results
           uses: actions/upload-artifact@v4
           if: always()
           with:
             name: test-results-${{ matrix.service }}
             path: ${{ matrix.service }}/build/reports/tests/

2. Add a Docker build job that runs after tests pass.

3. Add dependabot.yml for automated dependency updates.
```

---

## Summary Matrix

| Item | Gap IDs | Phase | Severity | Effort |
|---|---|---|---|---|
| Fix balance double-debit bug | GAP-SE-03 | 1 | Critical | Small |
| Stop exception stack trace leakage | GAP-EH-01 | 1 | Critical | Small |
| Fix HTTP status codes | GAP-EH-02, GAP-EH-03 | 1 | High | Small |
| Add ResponseEntity type params | GAP-AD-01 | 1 | High | Small |
| Restrict actuator endpoints | GAP-SE-05 | 1 | High | Small |
| Hide password in responses | GAP-SE-04 | 1 | High | Small |
| Configure Feign timeouts | GAP-RE-03 | 1 | High | Small |
| Add retry policies | GAP-RE-02 | 1 | High | Small |
| Set pagination limits | GAP-AD-02 | 1 | High | Small |
| Mask sensitive data in logs | GAP-OB-05, GAP-EH-04 | 1 | Low-Medium | Small |
| Add input validation | GAP-SE-02 | 2 | Critical | Medium |
| Implement circuit breakers | GAP-RE-01 | 2 | Critical | Medium |
| Externalize secrets | GAP-SE-01 | 2 | Critical | Medium |
| Implement RBAC | GAP-SE-06 | 2 | High | Medium |
| Add unit tests | GAP-TS-01 | 2 | Critical | Large |
| Add integration tests | GAP-TS-02 | 2 | High | Large |
| Create shared library | GAP-CO-01, GAP-CO-02 | 2 | High | Large |
| Migrate bootstrap -> config import | GAP-CO-05 | 2 | Medium | Medium |
| Add centralized logging | GAP-OB-01 | 2 | High | Medium |
| Add Prometheus metrics | GAP-OB-02 | 2 | Medium | Small |
| Add contract tests | GAP-TS-03 | 2 | High | Medium |
| Saga/outbox pattern | GAP-RE-04 | 3 | Medium | Large |
| Idempotency keys | GAP-RE-05 | 3 | Medium | Medium |
| Filtering and sorting | GAP-AD-06 | 3 | Low | Medium |
| CORS configuration | GAP-SE-08 | 3 | Medium | Small |
| Private config repo | GAP-SE-07 | 3 | Medium | Small |
| Custom health indicators | GAP-OB-04 | 3 | Medium | Small |
| Custom business metrics | GAP-OB-03 | 3 | Medium | Small |
| Fix OpenAPI + annotations | GAP-CO-04, GAP-AD-07 | 3 | Low | Small |
| CI/CD pipeline | — | 3 | High | Large |
