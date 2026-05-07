# Remediation Roadmap

## Overview

This roadmap organizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort. Each item includes a sample Devin prompt that can be used to implement the remediation.

**Phase 1 — Quick Wins** (1-2 weeks): Critical and high-severity items with small effort. Immediate security and correctness fixes.

**Phase 2 — Important** (3-6 weeks): High and medium-severity items with medium effort. Foundational improvements to reliability and maintainability.

**Phase 3 — Polish** (6-12 weeks): Medium and low-severity items. Long-term improvements to testing, architecture, and developer experience.

---

## Phase 1: Quick Wins

High-impact, low-effort items that address critical security and correctness issues.

### 1.1 Fix Balance Calculation Bug (GAP-RE-05)

**Priority**: P0 — Data integrity risk in production
**Effort**: Small (focused code change)

The `availableBalance` is double-subtracted in `TransactionService.internalFundTransfer()` and `utilPayment()`. This is a correctness bug that causes incorrect account balances.

**Devin Prompt**:
```
In the core-banking-service, fix the balance calculation bug in TransactionService.java.

In the internalFundTransfer() method (around lines 88-90), the availableBalance is being 
set to actualBalance minus the amount AGAIN after actualBalance was already subtracted. 
The correct behavior is:
  fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance());
Do the same for toBankAccountEntity. Apply the same fix in the utilPayment() method.

Also add @Version annotation to BankAccountEntity for optimistic locking to prevent 
concurrent transfer race conditions.

Update the existing unit tests in TransactionServiceTest to verify the correct balance 
after transfers. Open a PR with the fix.
```

---

### 1.2 Add Request Body Validation (GAP-EH-03, GAP-SE-02)

**Priority**: P0 — Security and data integrity
**Effort**: Small

**Devin Prompt**:
```
Add Jakarta Bean Validation to all request DTOs and controller methods across all 4 
business services (core-banking, user-service, fund-transfer, utility-payment).

For each service:
1. Add @Valid annotation to all @RequestBody parameters in controllers
2. Add validation annotations to request DTOs:
   - FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
   - UtilityPaymentRequest: @NotNull on providerId, @NotBlank on account/referenceNumber, 
     @NotNull @Positive on amount
   - User (registration): @NotBlank on email, @Email on email, @NotBlank on identification, 
     @NotBlank on password
   - UserUpdateRequest: @NotNull on status
3. Add a MethodArgumentNotValidException handler in each GlobalExceptionHandler that returns 
   a 400 response with field-level error details in the standard ErrorResponse format.

Open a PR with these changes.
```

---

### 1.3 Remove Hardcoded Credentials (GAP-SE-01)

**Priority**: P0 — Security
**Effort**: Small

**Devin Prompt**:
```
Remove all hardcoded credentials from version-controlled files in this project.

1. In docker-compose/docker-compose.yml and docker-compose-support-apps.yml:
   - Replace MYSQL_ROOT_PASSWORD value with ${MYSQL_ROOT_PASSWORD}
   - Replace KEYCLOAK_ADMIN_PASSWORD value with ${KEYCLOAK_ADMIN_PASSWORD}
   - Replace KC_DB_PASSWORD value with ${KC_DB_PASSWORD}
   - Replace POSTGRES_PASSWORD value with ${POSTGRES_PASSWORD}
2. In docker-compose/mysql/Dockerfile:
   - Replace the hardcoded ENV value with ARG + ENV pattern
3. In docker-compose/mysql/privileges.sql:
   - This file runs at init time. Document that the password should be changed after 
     initial setup.
4. Create a docker-compose/.env.example file documenting all required environment variables 
   with placeholder values.
5. Add .env to .gitignore.
6. Remove the test credentials from README.md and move them to a separate non-committed file.

Open a PR with these changes.
```

---

### 1.4 Fix Inconsistent Error Responses (GAP-EH-01)

**Priority**: P1 — API reliability
**Effort**: Small

**Devin Prompt**:
```
Standardize error handling across all services to return consistent error responses with 
proper HTTP status codes.

1. Create a unified ErrorResponse format: { "code": "string", "message": "string", 
   "timestamp": "ISO-8601" }
2. In every service's GlobalExceptionHandler:
   - EntityNotFoundException should return 404 Not Found (not 400)
   - SimpleBankingGlobalException should return 400 Bad Request
   - InsufficientFundsException should return 422 Unprocessable Entity
   - Generic Exception handler should return 500 Internal Server Error with a safe message 
     (never expose exception details)
3. Fix the fund-transfer and utility-payment generic exception handlers that currently 
   return raw string "Exception occur inside API " + e — wrap them in ErrorResponse

Open a PR with these changes.
```

---

### 1.5 Prevent Sensitive Data in Logs (GAP-OB-02)

**Priority**: P1 — Security/compliance
**Effort**: Small

**Devin Prompt**:
```
Fix sensitive data logging issues across all services.

1. In internet-banking-user-service, the User DTO has a password field that could be 
   logged via toString(). Add @ToString.Exclude on the password field in User.java 
   (model.dto.User).
2. Review all log.info() statements in controllers and services. Replace request.toString() 
   logging with specific non-sensitive fields. For example:
   - UserController: log only email (masked) and identification
   - FundTransferController: log fromAccount and toAccount (last 4 digits only)
   - TransactionController: log account numbers (last 4 digits only)
3. Add @ToString.Exclude to any field containing credentials in the Keycloak configuration 
   classes.

Open a PR with these changes.
```

---

### 1.6 Restrict Actuator Endpoints (GAP-SE-03)

**Priority**: P1 — Security
**Effort**: Small

**Devin Prompt**:
```
Restrict actuator endpoint access in the API Gateway's SecurityConfiguration.

1. Change the actuator path matchers from permitAll() to only allow health and info:
   - .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
   - Remove the blanket /actuator/** permitAll()
2. In each service's application.yml (via config server), explicitly configure which 
   actuator endpoints are exposed:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
     endpoint:
       health:
         show-details: when-authorized

Open a PR with these changes.
```

---

### 1.7 Add Timeout Configuration (GAP-RE-03)

**Priority**: P1 — Resilience
**Effort**: Small

**Devin Prompt**:
```
Add explicit timeout configurations for all inter-service communication.

1. In each service that uses Feign clients (user-service, fund-transfer, utility-payment), 
   add to application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               connect-timeout: 5000
               read-timeout: 10000
             core-banking-service:
               connect-timeout: 5000
               read-timeout: 15000

2. In the API Gateway, configure route-level timeouts:
   spring:
     cloud:
       gateway:
         httpclient:
           connect-timeout: 5000
           response-timeout: 30s

3. Add graceful shutdown configuration to all services:
   server:
     shutdown: graceful
   spring:
     lifecycle:
       timeout-per-shutdown-phase: 30s

Note: These configurations should be added to the external Git config repository 
referenced by the config server. If that repo is not accessible, add them to each 
service's local application.yml as fallback defaults.

Open a PR with these changes.
```

---

### 1.8 Add Retry Policies (GAP-RE-02)

**Priority**: P1 — Resilience
**Effort**: Small

**Devin Prompt**:
```
Add retry policies for transient failures in Feign calls.

1. Add spring-retry dependency to fund-transfer, utility-payment, and user-service 
   build.gradle:
   implementation 'org.springframework.retry:spring-retry'

2. Configure Feign retry via application.yml:
   spring:
     cloud:
       openfeign:
         client:
           config:
             default:
               retryer: feign.Retryer.Default

3. Add @EnableRetry to each application's main class.

4. Add retry configuration for Spring Cloud Config bootstrap:
   spring:
     cloud:
       config:
         retry:
           initial-interval: 1000
           max-interval: 5000
           max-attempts: 5
         fail-fast: true

Open a PR with these changes.
```

---

## Phase 2: Important

Foundational improvements that require moderate effort but significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers (GAP-RE-01)

**Priority**: P1 — Resilience
**Effort**: Medium

**Devin Prompt**:
```
Add Resilience4j circuit breakers to all Feign client calls.

1. Add dependencies to fund-transfer, utility-payment, and user-service build.gradle:
   implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'

2. Enable Feign circuit breaker integration in application.yml:
   spring:
     cloud:
       openfeign:
         circuitbreaker:
           enabled: true

3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback (fund-transfer): return error response with 
     "Core banking service unavailable"
   - BankingCoreRestClientFallback (utility-payment): similar
   - BankingCoreRestClientFallback (user-service): similar

4. Update @FeignClient annotations with fallback parameter.

5. Configure circuit breaker thresholds in application.yml:
   resilience4j:
     circuitbreaker:
       instances:
         core-banking-service:
           sliding-window-size: 10
           failure-rate-threshold: 50
           wait-duration-in-open-state: 10s
           permitted-number-of-calls-in-half-open-state: 3

6. Update the FundTransferService to set status=FAILED when the circuit breaker triggers 
   or core banking call fails, instead of leaving records as PENDING forever.

Open a PR with these changes.
```

---

### 2.2 Implement Feign Error Decoder (GAP-EH-02)

**Priority**: P1 — Error handling
**Effort**: Medium

**Devin Prompt**:
```
Implement a custom Feign ErrorDecoder that properly maps upstream HTTP errors to domain 
exceptions.

1. Create a shared CustomFeignErrorDecoder class (implement in each service for now):
   - 404 from core-banking → EntityNotFoundException
   - 422 from core-banking → InsufficientFundsException (parse error response body)
   - 400 from core-banking → SimpleBankingGlobalException
   - 5xx from core-banking → ServiceUnavailableException (new exception)
   - Default → SimpleBankingGlobalException with original status code

2. Register the error decoder in CustomFeignClientConfiguration in fund-transfer and 
   utility-payment services.

3. Fix the fund-transfer CustomFeignClientConfiguration to register the error decoder 
   alongside the logger level.

4. Fix the utility-payment CustomFeignClientConfiguration which currently extends 
   FeignClientConfiguration and adds nothing — make it register the error decoder.

5. Add ServiceUnavailableException handler to GlobalExceptionHandler returning 503.

Open a PR with these changes.
```

---

### 2.3 Extract Shared Library (GAP-CO-01)

**Priority**: P2 — Maintainability
**Effort**: Medium

**Devin Prompt**:
```
Create a shared library module to eliminate code duplication across services.

1. Create a new Gradle subproject: internet-banking-common/
   - build.gradle with spring-boot-starter-web, spring-data-jpa, lombok dependencies
   - Package: com.javatodev.finance.common

2. Move the following duplicated classes into the shared module:
   - BaseMapper<E, D>
   - AuditAware
   - SimpleBankingGlobalException
   - ErrorResponse
   - GlobalExceptionHandler (base version)
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - GlobalErrorCode

3. Add the shared module as a dependency in each service's build.gradle:
   implementation project(':internet-banking-common')

4. Create a root settings.gradle that includes all subprojects.

5. Remove the duplicated classes from each service and update imports.

6. Run all existing tests to ensure nothing is broken.

Open a PR with these changes.
```

---

### 2.4 Add Prometheus Metrics (GAP-OB-04)

**Priority**: P2 — Observability
**Effort**: Medium

**Devin Prompt**:
```
Add Prometheus metrics support to all services.

1. Add micrometer-registry-prometheus dependency to all service build.gradle files:
   implementation 'io.micrometer:micrometer-registry-prometheus'

2. Configure actuator to expose the prometheus endpoint in each service's application.yml:
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
     metrics:
       tags:
         application: ${spring.application.name}

3. Add custom business metrics in key services:
   - core-banking TransactionService: Counter for fund transfers (success/failure), 
     Counter for utility payments, Timer for transaction processing time
   - fund-transfer FundTransferService: Counter for transfer requests by status
   - utility-payment UtilityPaymentService: Counter for payment requests by status

4. Create a docker-compose service for Prometheus with a basic prometheus.yml config 
   that scrapes all service actuator endpoints.

Open a PR with these changes.
```

---

### 2.5 Fix OpenAPI Documentation (GAP-AD-01, GAP-AD-04)

**Priority**: P2 — API usability
**Effort**: Small

**Devin Prompt**:
```
Fix and complete the OpenAPI/Swagger documentation across all services.

1. In core-banking, fund-transfer, utility-payment, and user-service build.gradle:
   - Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui 
     (these are Spring MVC services, not WebFlux)

2. Add generic type parameters to all ResponseEntity returns:
   - ResponseEntity<BankAccount> getBankAccount(...)
   - ResponseEntity<FundTransferResponse> fundTransfer(...)
   - etc.

3. Add @OpenAPIDefinition to each service's main Application class with title, version, 
   and description.

4. Add @ApiResponse annotations to controller methods documenting success and error 
   responses with schema references.

5. Verify Swagger UI is accessible at /swagger-ui.html for each service.

Open a PR with these changes.
```

---

### 2.6 Add Pagination Metadata (GAP-AD-05)

**Priority**: P2 — API usability
**Effort**: Small

**Devin Prompt**:
```
Return proper pagination metadata in all list endpoints.

1. In core-banking UserController.readUsers(): return Page<User> instead of List<User>.
   Update UserService.readUsers() to return the Page directly instead of extracting content.

2. In fund-transfer FundTransferController.readFundTransfers(): return Page<FundTransfer>.
   Update FundTransferService.readAllTransfers() accordingly.

3. In utility-payment UtilityPaymentController.readPayments(): return Page<UtilityPayment>.
   Update UtilityPaymentService.readPayments() accordingly.

4. In user-service UserController.readUsers(): return Page<User>.
   Update UserService.readUsers() accordingly.

This ensures API consumers receive totalElements, totalPages, pageNumber, and pageSize 
in responses.

Open a PR with these changes.
```

---

### 2.7 Add Structured Logging (GAP-OB-01)

**Priority**: P2 — Observability
**Effort**: Small

**Devin Prompt**:
```
Add JSON-structured logging to all services for production use.

1. Add logstash-logback-encoder dependency to all service build.gradle files:
   implementation 'net.logstash.logback:logstash-logback-encoder:7.4'

2. Create a logback-spring.xml in each service's src/main/resources/ with:
   - Console appender with pattern layout for local development (default profile)
   - JSON appender using LogstashEncoder for docker/production profile
   - Include service name, trace ID, and span ID in all log entries

3. Configure MDC to include request context (user auth ID, request ID).

Open a PR with these changes.
```

---

### 2.8 Fix Keycloak Singleton Pattern (GAP-SE-04)

**Priority**: P2 — Security/reliability
**Effort**: Small

**Devin Prompt**:
```
Refactor the Keycloak client in internet-banking-user-service to use proper Spring 
lifecycle management.

1. In KeycloakProperties.java:
   - Remove the static keycloakInstance field and manual singleton pattern
   - Create a @Bean method that returns a properly configured Keycloak instance
   - Use @Configuration class instead of @Component

2. Create a new KeycloakConfig.java configuration class:
   @Configuration
   public class KeycloakConfig {
       @Bean
       public Keycloak keycloak(KeycloakProperties properties) {
           return KeycloakBuilder.builder()
               .serverUrl(properties.getServerUrl())
               .realm(properties.getRealm())
               .grantType("client_credentials")
               .clientId(properties.getClientId())
               .clientSecret(properties.getClientSecret())
               .build();
       }
   }

3. Update KeycloakManager to inject the Keycloak bean directly instead of calling 
   properties.getInstance().

4. Update the test configuration to mock the Keycloak bean.

Open a PR with these changes.
```

---

### 2.9 Add CORS Configuration (GAP-SE-05)

**Priority**: P2 — Usability
**Effort**: Small

**Devin Prompt**:
```
Add CORS configuration to the API Gateway.

1. In SecurityConfiguration.java, add CORS configuration:
   httpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource()));

2. Create a CorsConfigurationSource bean:
   - Allow origins from configuration property (default: http://localhost:3000)
   - Allow methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
   - Allow headers: Authorization, Content-Type, X-Auth-Id
   - Allow credentials: true
   - Max age: 3600

3. Add the cors.allowed-origins property to the config server configuration.

Open a PR with these changes.
```

---

### 2.10 Add Dependency Vulnerability Scanning (GAP-SE-06)

**Priority**: P2 — Security
**Effort**: Small

**Devin Prompt**:
```
Add OWASP Dependency Check to the Gradle build for vulnerability scanning.

1. Add the OWASP dependency-check plugin to each service's build.gradle:
   plugins {
       id 'org.owasp.dependencycheck' version '9.0.9'
   }

2. Configure the plugin:
   dependencyCheck {
       failBuildOnCVSS = 7
       suppressionFile = "${rootDir}/owasp-suppressions.xml"
       formats = ['HTML', 'JSON']
   }

3. Create an owasp-suppressions.xml file for known false positives.

4. Add a Gradle task alias: ./gradlew dependencyCheckAnalyze

Open a PR with these changes.
```

---

## Phase 3: Polish

Longer-term improvements to testing, architecture, and developer experience.

### 3.1 Add Comprehensive Unit Tests (GAP-TE-01)

**Priority**: P2 — Quality
**Effort**: Large

**Devin Prompt**:
```
Add comprehensive unit tests for all business services. Target 70%+ line coverage.

For internet-banking-user-service:
1. UserServiceTest: test createUser (success, email already registered, email mismatch, 
   user not found in core banking), readUsers, readUser, updateUser (approve, disable)
2. KeycloakUserServiceTest: test createUser, updateUser, readUser, readUserByEmail
3. UserControllerTest: MockMvc tests for all 4 endpoints with validation error cases

For internet-banking-fund-transfer-service:
1. FundTransferServiceTest: test fundTransfer (success, core banking failure, update 
   failure), readAllTransfers
2. FundTransferControllerTest: MockMvc tests for POST and GET endpoints

For internet-banking-utility-payment-service:
1. UtilityPaymentServiceTest: test utilPayment (success, failure), readPayments
2. UtilityPaymentControllerTest: MockMvc tests for POST and GET endpoints

Use Mockito for mocking dependencies. Each test class should follow the existing 
pattern in core-banking-service tests.

Open a PR with these changes.
```

---

### 3.2 Add Integration Tests with Testcontainers (GAP-TE-02)

**Priority**: P2 — Quality
**Effort**: Large

**Devin Prompt**:
```
Add integration tests using Testcontainers for MySQL and WireMock for Feign clients.

1. Add test dependencies to all service build.gradle files:
   testImplementation 'org.testcontainers:mysql:1.19.7'
   testImplementation 'org.testcontainers:junit-jupiter:1.19.7'
   testImplementation 'org.wiremock:wiremock-standalone:3.4.2'

2. For core-banking-service:
   - Create AccountServiceIntegrationTest with @Testcontainers and MySQL container
   - Test actual database operations including Flyway migrations
   - Verify fund transfer creates correct transaction records

3. For fund-transfer-service:
   - Create FundTransferServiceIntegrationTest
   - Use WireMock to stub core-banking-service responses
   - Verify the full flow: save PENDING → call core banking → update SUCCESS

4. For utility-payment-service:
   - Similar pattern to fund-transfer

5. Fix the existing @SpringBootTest context-load tests to work without external 
   infrastructure (add @MockBean for external dependencies or use test profiles).

Open a PR with these changes.
```

---

### 3.3 Add Contract Tests (GAP-TE-03)

**Priority**: P3 — Quality
**Effort**: Large

**Devin Prompt**:
```
Add Spring Cloud Contract tests between consumer services and core-banking-service.

1. Add Spring Cloud Contract dependencies to core-banking-service (producer):
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-verifier'
   Add the spring-cloud-contract-verifier Gradle plugin.

2. Create contract DSL files in core-banking-service/src/test/resources/contracts/:
   - fundTransfer.groovy: defines the POST /api/v1/transaction/fund-transfer contract
   - readBankAccount.groovy: defines the GET /api/v1/account/bank-account/{number} contract
   - utilPayment.groovy: defines the POST /api/v1/transaction/util-payment contract
   - readUser.groovy: defines the GET /api/v1/user/{identification} contract

3. Create a base test class for contract verification.

4. In consumer services (fund-transfer, utility-payment, user-service), add stub runner:
   testImplementation 'org.springframework.cloud:spring-cloud-starter-contract-stub-runner'

5. Write consumer-side contract tests that verify Feign clients work correctly against 
   the stubs generated from core-banking contracts.

Open a PR with these changes.
```

---

### 3.4 Implement Multi-Project Gradle Build (GAP-CO-02)

**Priority**: P3 — Developer experience
**Effort**: Medium

**Devin Prompt**:
```
Convert the project to a Gradle multi-project build.

1. Create a root settings.gradle that includes all service subprojects:
   rootProject.name = 'internet-banking-microservices'
   include 'core-banking-service'
   include 'internet-banking-api-gateway'
   include 'internet-banking-config-server'
   include 'internet-banking-fund-transfer-service'
   include 'internet-banking-service-registry'
   include 'internet-banking-user-service'
   include 'internet-banking-utility-payment-service'
   include 'internet-banking-common'

2. Create a root build.gradle with shared configuration:
   - Common Java 21 sourceCompatibility
   - Common Spring Boot and Spring Cloud versions
   - Common test configuration
   - Common dependency versions in ext block

3. Simplify each service's build.gradle to only declare service-specific dependencies.

4. Remove individual settings.gradle and gradle wrapper from each service 
   (use the root wrapper).

5. Verify all services build successfully with: ./gradlew build

Open a PR with these changes.
```

---

### 3.5 Standardize Package Structure (GAP-CO-03)

**Priority**: P3 — Maintainability
**Effort**: Small

**Devin Prompt**:
```
Standardize the package structure across all services to follow a consistent convention.

Target structure for each service:
  com.javatodev.finance
  ├── configuration/       # Spring @Configuration classes
  ├── controller/          # REST controllers
  ├── exception/           # Exception classes and handlers
  ├── model/
  │   ├── dto/            # Data Transfer Objects
  │   │   ├── request/    # Request DTOs
  │   │   └── response/   # Response DTOs
  │   ├── entity/         # JPA entities
  │   └── mapper/         # Entity-DTO mappers
  ├── repository/          # Spring Data repositories
  └── service/
      └── rest/           # Feign clients

Specific changes needed:
1. user-service: move model.repository → repository, model.rest.response → model.dto.response
2. utility-payment: move model.rest.request → model.dto.request, 
   model.rest.response → model.dto.response, move repository up from model
3. fund-transfer: move model.repository → repository, 
   service.rest.client → service.rest

Update all imports accordingly. Run tests to verify.

Open a PR with these changes.
```

---

### 3.6 Add Fallback and Reconciliation (GAP-RE-04)

**Priority**: P2 — Resilience
**Effort**: Medium

**Devin Prompt**:
```
Add fallback behavior and transaction reconciliation for failed inter-service calls.

1. In fund-transfer-service:
   - Update FundTransferService.fundTransfer() to catch exceptions from core banking 
     and set the record status to FAILED instead of leaving it as PENDING
   - Add a @Scheduled reconciliation job that:
     a. Queries all PENDING fund transfers older than 5 minutes
     b. Attempts to verify their status with core banking
     c. Updates status to SUCCESS or FAILED accordingly

2. In utility-payment-service:
   - Apply the same error handling pattern to UtilityPaymentService.utilPayment()
   - Add a similar @Scheduled reconciliation job for PROCESSING payments

3. Add @EnableScheduling to both service application classes.

4. Configure scheduling properties in application.yml:
   reconciliation:
     cron: "0 */5 * * * *"  # Every 5 minutes

Open a PR with these changes.
```

---

### 3.7 Add Custom Health Checks (GAP-OB-03)

**Priority**: P3 — Observability
**Effort**: Small

**Devin Prompt**:
```
Add custom health indicators for critical service dependencies.

1. In user-service, create KeycloakHealthIndicator:
   - Attempt to connect to Keycloak server URL
   - Return Health.up() or Health.down() with error details

2. In fund-transfer-service and utility-payment-service, create 
   CoreBankingHealthIndicator:
   - Use the Feign client to call core banking actuator health endpoint
   - Return appropriate health status

3. In core-banking-service, the default DataSourceHealthIndicator from Spring Boot 
   should suffice for MySQL.

4. Register all health indicators as @Component beans.

Open a PR with these changes.
```

---

### 3.8 Convert Mappers to Spring Beans (GAP-CO-04)

**Priority**: P3 — Code quality
**Effort**: Small

**Devin Prompt**:
```
Convert all mapper classes from manual instantiation to Spring-managed @Component beans.

1. Add @Component annotation to all mapper classes:
   - core-banking: BankAccountMapper, UserMapper, UtilityAccountMapper
   - user-service: UserMapper
   - fund-transfer: FundTransferMapper
   - utility-payment: UtilityPaymentMapper

2. Update all service classes to inject mappers via constructor injection instead of 
   field initialization:
   Before: private UserMapper userMapper = new UserMapper();
   After:  private final UserMapper userMapper;  // injected via @RequiredArgsConstructor

3. Update tests to mock or instantiate mappers appropriately.

Open a PR with these changes.
```

---

### 3.9 Improve REST URL Patterns (GAP-AD-02)

**Priority**: P3 — API design
**Effort**: Small

**Devin Prompt**:
```
Improve REST endpoint naming to follow RESTful conventions. Note: this is a breaking 
API change, so update the API Gateway route configuration and Feign clients accordingly.

Changes:
1. user-service: PATCH /api/v1/bank-users/update/{id} → PATCH /api/v1/bank-users/{id}
2. core-banking: POST /api/v1/transaction/fund-transfer → keep as-is (internal API)
3. core-banking: POST /api/v1/transaction/util-payment → keep as-is (internal API)

Only change the user-facing endpoints (those exposed through the API Gateway). 
Internal service-to-service endpoints can remain as-is since they're not public.

Update the Postman collection if it references the changed endpoints.

Open a PR with these changes.
```

---

### 3.10 Add Graceful Shutdown and Tracing Defaults (GAP-RE-06, GAP-OB-05)

**Priority**: P3 — Operational
**Effort**: Small

**Devin Prompt**:
```
Add graceful shutdown configuration and tracing defaults to all services.

1. In each service's application.yml, add:
   server:
     shutdown: graceful
   spring:
     lifecycle:
       timeout-per-shutdown-phase: 30s

2. Add default tracing configuration as fallback (in case config server is unavailable):
   management:
     tracing:
       sampling:
         probability: 1.0
     zipkin:
       tracing:
         endpoint: http://localhost:9411/api/v2/spans

3. Add spring.application.name to bootstrap.yml if not already present (needed for 
   trace service name).

Open a PR with these changes.
```

---

## Summary Timeline

```
Week 1-2 (Phase 1: Quick Wins)
├── 1.1  Fix balance calculation bug         [P0, Small]
├── 1.2  Add request body validation         [P0, Small]
├── 1.3  Remove hardcoded credentials        [P0, Small]
├── 1.4  Fix inconsistent error responses    [P1, Small]
├── 1.5  Prevent sensitive data in logs      [P1, Small]
├── 1.6  Restrict actuator endpoints         [P1, Small]
├── 1.7  Add timeout configuration           [P1, Small]
└── 1.8  Add retry policies                  [P1, Small]

Week 3-6 (Phase 2: Important)
├── 2.1  Add circuit breakers                [P1, Medium]
├── 2.2  Implement Feign error decoder       [P1, Medium]
├── 2.3  Extract shared library              [P2, Medium]
├── 2.4  Add Prometheus metrics              [P2, Medium]
├── 2.5  Fix OpenAPI documentation           [P2, Small]
├── 2.6  Add pagination metadata             [P2, Small]
├── 2.7  Add structured logging              [P2, Small]
├── 2.8  Fix Keycloak singleton              [P2, Small]
├── 2.9  Add CORS configuration              [P2, Small]
└── 2.10 Add dependency vulnerability scan   [P2, Small]

Week 7-12 (Phase 3: Polish)
├── 3.1  Add comprehensive unit tests        [P2, Large]
├── 3.2  Add integration tests               [P2, Large]
├── 3.3  Add contract tests                  [P3, Large]
├── 3.4  Multi-project Gradle build          [P3, Medium]
├── 3.5  Standardize package structure       [P3, Small]
├── 3.6  Add fallback and reconciliation     [P2, Medium]
├── 3.7  Add custom health checks            [P3, Small]
├── 3.8  Convert mappers to Spring beans     [P3, Small]
├── 3.9  Improve REST URL patterns           [P3, Small]
└── 3.10 Graceful shutdown + tracing         [P3, Small]
```

---

## Risk Notes

1. **Phase 1 items 1.1 and 1.2 are the highest priority** — the balance calculation bug can cause financial data corruption, and missing validation allows malformed requests.
2. **Hardcoded credentials (1.3)** should be addressed before any production deployment.
3. **Circuit breakers (2.1) and error decoders (2.2)** are the most impactful Phase 2 items for production stability.
4. **Test coverage (3.1, 3.2)** is a large effort but critical for safe refactoring of shared code (2.3) and package restructuring (3.5).
5. The **external config server** (Git-backed) means some configuration changes (timeouts, tracing, actuator) may need to be applied in the [external config repository](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git) rather than in-repo YAML files.
