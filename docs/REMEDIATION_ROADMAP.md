# Remediation Roadmap

This roadmap prioritizes the 38 gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases. Each item includes a sample Devin prompt to kick off the remediation.

---

## Phase 1: Quick Wins (Critical bugs & small-effort fixes)

These items address critical correctness bugs and security issues that can be resolved with minimal code changes. They should be tackled first because they represent active defects or vulnerabilities.

| # | Gap Ref | Title | Severity | Effort | Description |
|---|---------|-------|----------|--------|-------------|
| 1 | 7.7 | Fix double-subtraction bug in balance updates | Critical | Small | `TransactionService.internalFundTransfer()` and `utilPayment()` set `availableBalance = actualBalance - amount` after `actualBalance` was already reduced, causing a double deduction. |
| 2 | 2.1 | Fix generic exception handler to return proper HTTP status codes | High | Small | Replace the catch-all `Exception` handler that returns 400 for everything. Return 500 for unexpected errors, use structured `ErrorResponse`, and stop leaking stack traces. |
| 3 | 2.2 | Return 404 for entity-not-found errors | High | Small | Add a dedicated `@ExceptionHandler` for `EntityNotFoundException` that returns HTTP 404 instead of 400. |
| 4 | 5.1 | Add type parameters to ResponseEntity | Medium | Small | Add generic type parameters to all `ResponseEntity` return types (e.g., `ResponseEntity<BankAccount>`). |
| 5 | 5.5 | Fix OpenAPI dependency (webflux → webmvc) | Medium | Small | Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in core-banking, fund-transfer, user, and utility-payment services. |
| 6 | 5.3 | Return pagination metadata from list endpoints | Medium | Small | Return `Page<T>` instead of `List<T>` from paginated endpoints so clients get total count, page size, etc. |
| 7 | 7.2 | Configure Feign retry policies | High | Small | Add Feign `Retryer` beans with sensible defaults (e.g., 3 retries, 100ms backoff) across all Feign client configurations. |
| 8 | 7.3 | Configure Feign timeouts | High | Small | Set explicit connect and read timeouts for all Feign clients via configuration properties. |
| 9 | 4.7 | Restrict actuator endpoint access | Medium | Small | Limit publicly exposed actuator endpoints to `/actuator/health` and `/actuator/info`. Require authentication for all others. |
| 10 | 6.1 | Fix insecure logging (remove .toString() on sensitive objects) | Medium | Small | Remove `request.toString()` from log statements that may contain passwords or account numbers. Use selective field logging. |
| 11 | 2.3 | Standardize error response format | Medium | Small | Ensure all exception handlers return the same `ErrorResponse` structure with code, message, timestamp, and path. |
| 12 | 4.3 | Fix Keycloak singleton thread safety | Medium | Small | Make `KeycloakProperties.keycloakInstance` volatile and use double-checked locking, or convert to a `@Bean` method. |
| 13 | 3.4 | Fix context-load tests to work without external dependencies | Medium | Small | Add test-specific `application.yml` profiles that disable Eureka, Config Server, and use H2 in-memory databases. |
| 14 | 2.4 | Fix ErrorResponse constructor inconsistency | Low | Small | Ensure all services use `ErrorResponse.builder()` consistently. |
| 15 | 1.3 | Standardize package structure | Low | Small | Move all `repository` packages to a consistent location across services. |
| 16 | 5.6 | Standardize URL naming conventions | Low | Small | Pick a consistent naming pattern (kebab-case, no abbreviations) and apply across all endpoints. |

### Devin Prompts — Phase 1

**#1 – Fix double-subtraction bug:**
```
In core-banking-service TransactionService.java, fix the balance update bug in
internalFundTransfer() and utilPayment(). The availableBalance is being set to
actualBalance.subtract(amount) AFTER actualBalance was already subtracted, causing
a double deduction. The fix: set availableBalance to the NEW actualBalance value
(without subtracting again). Apply the same fix to both the debit and credit sides.
Add unit tests to verify correct balance calculations.
```

**#2 – Fix generic exception handler:**
```
In all 4 business services (core-banking, fund-transfer, user, utility-payment),
update the GlobalExceptionHandler to:
1. Return HTTP 500 (not 400) for the generic Exception handler
2. Return a structured ErrorResponse (not a plain string) for all exceptions
3. Never include the raw exception in the response body (log it server-side instead)
4. Add a @ResponseStatus or explicit status code for each handler
```

**#3 – Return 404 for entity-not-found:**
```
In core-banking-service and internet-banking-user-service, add a separate
@ExceptionHandler for EntityNotFoundException that returns HTTP 404 with a
structured ErrorResponse. Currently it falls through to the
SimpleBankingGlobalException handler which returns 400.
```

**#4 – Add ResponseEntity type parameters:**
```
Across all controllers in all services, add generic type parameters to
ResponseEntity return types. For example, change:
  public ResponseEntity getBankAccount(...)
to:
  public ResponseEntity<BankAccount> getBankAccount(...)
This improves type safety and OpenAPI documentation accuracy.
```

**#5 – Fix OpenAPI dependency:**
```
In build.gradle for core-banking-service, internet-banking-fund-transfer-service,
internet-banking-user-service, and internet-banking-utility-payment-service, replace:
  implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
with:
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
These are Spring MVC services, not WebFlux. Only the API gateway should use webflux.
```

**#6 – Return pagination metadata:**
```
In FundTransferController, UtilityPaymentController, and the UserControllers (both
core-banking and user-service), change list endpoints to return Page<T> instead of
List<T>. For example in FundTransferService.readAllTransfers(), return the Page
object directly instead of calling .getContent().
```

**#7 – Configure Feign retry:**
```
Add Feign retry configuration to all services that use Feign clients (fund-transfer,
user, utility-payment). Create a @Bean Retryer in each CustomFeignClientConfiguration:
  @Bean
  public Retryer retryer() {
      return new Retryer.Default(100, 1000, 3);
  }
This retries failed requests up to 3 times with exponential backoff.
```

**#8 – Configure Feign timeouts:**
```
Add timeout configuration for all Feign clients. In each service's application.yml
(or via Spring Cloud Config), add:
  spring.cloud.openfeign.client.config.default.connect-timeout: 5000
  spring.cloud.openfeign.client.config.default.read-timeout: 10000
```

**#9 – Restrict actuator endpoints:**
```
In the API gateway SecurityConfiguration, change the actuator permit rules to only
allow /actuator/health and /actuator/info publicly. Require authentication for all
other actuator endpoints. Also add management.endpoints.web.exposure.include=health,info
to each service's configuration.
```

**#10 – Fix insecure logging:**
```
Across all controllers and services, replace log statements that call .toString()
on request objects with selective field logging. For example, change:
  log.info("Creating user with {}", request.toString());
to:
  log.info("Creating user with email {}", request.getEmail());
Never log passwords, full account numbers, or other PII.
```

**#11 – Standardize error response format:**
```
Update the ErrorResponse class in all services to include additional fields:
timestamp (Instant), path (String), and traceId (String). Update all
GlobalExceptionHandler methods to populate these fields. Ensure every
exception handler returns this same structure.
```

**#12 – Fix Keycloak singleton thread safety:**
```
In internet-banking-user-service KeycloakProperties.java, fix the non-thread-safe
singleton. Either:
(a) Make keycloakInstance volatile and use synchronized double-checked locking, or
(b) Better: convert getInstance() to a @Bean method in a @Configuration class so
    Spring manages the lifecycle.
```

**#13 – Fix context-load tests:**
```
For all services, create a src/test/resources/application.yml that:
1. Disables Eureka client: eureka.client.enabled=false
2. Uses H2 in-memory database for JPA services
3. Disables Spring Cloud Config bootstrap: spring.cloud.config.enabled=false
4. For user-service: mock or disable Keycloak
Verify all contextLoads() tests pass with ./gradlew test.
```

**#14 – Fix ErrorResponse inconsistency:**
```
In internet-banking-fund-transfer-service GlobalExceptionHandler, change:
  .body(new ErrorResponse(e.getCode(), e.getMessage()))
to:
  .body(ErrorResponse.builder().code(e.getCode()).message(e.getMessage()).build())
to match the pattern used by all other services.
```

**#15 – Standardize package structure:**
```
In internet-banking-fund-transfer-service, move the repository interface from
com.javatodev.finance.model.repository to com.javatodev.finance.repository
to match the convention used by core-banking-service and utility-payment-service.
Similarly standardize the Feign client package locations.
```

**#16 – Standardize URL naming:**
```
Rename path variables from snake_case to kebab-case or camelCase consistently.
For example, change {account_number} to {accountNumber} in @PathVariable and
@GetMapping annotations across all controllers. Pick one convention and apply
it everywhere.
```

---

## Phase 2: Important (Security, testing, resilience foundations)

These items require more effort but are essential for a production-grade system. They address security fundamentals, test coverage, and resilience patterns.

| # | Gap Ref | Title | Severity | Effort | Description |
|---|---------|-------|----------|--------|-------------|
| 17 | 4.1 | Add input validation to all request DTOs | Critical | Medium | Add Jakarta Bean Validation annotations and `@Valid` on all controller parameters. |
| 18 | 7.1 | Add circuit breakers (Resilience4j) | Critical | Medium | Add Resilience4j circuit breakers to all Feign clients with fallback methods. |
| 19 | 3.1 | Add unit tests for fund-transfer, user, and utility-payment services | Critical | Large | Write unit tests for all service classes in the 5 services that currently have zero tests. |
| 20 | 4.2 | Remove hardcoded credentials from source control | Critical | Small | Move all passwords to environment variables or Docker secrets. Update compose files to use `${VAR}` syntax. |
| 21 | 7.4 | Add fallback behavior for Feign clients | High | Medium | Define `@FeignClient(fallback = ...)` or `fallbackFactory` classes that return sensible defaults when downstream services are unavailable. |
| 22 | 1.2 | Extract shared library for common code | High | Large | Create a `banking-common` module with shared classes: BaseMapper, AuditAware, AuditConfig, ApiRequestContext, AppAuthUserFilter, exception classes, ErrorResponse. |
| 23 | 1.5 | Create multi-module Gradle build | High | Large | Add a root `settings.gradle` that includes all services. Extract common Spring Boot/Cloud versions to a root `build.gradle`. |
| 24 | 3.2 | Add integration tests | High | Large | Add `@WebMvcTest` controller tests and `@DataJpaTest` repository tests for each service. |
| 25 | 2.5 | Add validation exception handling | Medium | Small | Add `@ExceptionHandler` for `MethodArgumentNotValidException` that returns field-level error details. |
| 26 | 4.4 | Add CORS configuration | Medium | Small | Configure CORS in the API gateway for expected frontend origins. |
| 27 | 4.6 | Add rate limiting at the API gateway | Medium | Medium | Add Spring Cloud Gateway `RequestRateLimiter` filter using Redis or in-memory rate limiting. |
| 28 | 4.8 | Add dependency vulnerability scanning | Medium | Small | Add OWASP dependency-check Gradle plugin and/or GitHub Dependabot configuration. |
| 29 | 6.3 | Add Prometheus metrics endpoint | Medium | Medium | Configure `micrometer-registry-prometheus` and expose `/actuator/prometheus`. Add custom business metrics. |
| 30 | 1.4 | Convert mappers to Spring beans | Low | Small | Annotate mapper classes with `@Component` and inject them instead of using `new`. |

### Devin Prompts — Phase 2

**#17 – Add input validation:**
```
Add Jakarta Bean Validation to all request DTOs across all services:

1. FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
2. UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount,
   @NotBlank on account
3. User (registration): @Email @NotBlank on email, @NotBlank on identification,
   @NotBlank @Size(min=8) on password
4. UserUpdateRequest: @NotNull on status

Add @Valid annotation to all controller method parameters that accept request bodies.
Add spring-boot-starter-validation dependency to each service's build.gradle.
Add a MethodArgumentNotValidException handler in GlobalExceptionHandler that returns
field-level validation errors.
```

**#18 – Add circuit breakers:**
```
Add Resilience4j circuit breakers to all Feign clients:

1. Add dependencies: resilience4j-spring-boot3, spring-cloud-circuitbreaker-resilience4j
2. For each Feign client, create a fallback factory class that logs the error and
   returns a sensible error response
3. Configure circuit breaker properties:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
   - permittedNumberOfCallsInHalfOpenState: 5
4. Add @CircuitBreaker annotations or configure via properties
```

**#19 – Add unit tests:**
```
Add comprehensive unit tests for these services (aim for >80% line coverage):

1. internet-banking-fund-transfer-service: FundTransferService (test transfer flow,
   error cases, status transitions)
2. internet-banking-user-service: UserService (test registration, update, read flows),
   KeycloakUserService (mock Keycloak admin client)
3. internet-banking-utility-payment-service: UtilityPaymentService (test payment flow,
   error cases)
4. internet-banking-api-gateway: GatewayConfiguration (test header propagation),
   SecurityConfiguration (test auth rules)

Use Mockito to mock Feign clients and repositories. Follow the existing test patterns
in core-banking-service as a reference.
```

**#20 – Remove hardcoded credentials:**
```
1. In docker-compose.yml and docker-compose-support-apps.yml, replace all hardcoded
   passwords with environment variable references (e.g., ${MYSQL_ROOT_PASSWORD})
2. Create a .env.example file documenting required environment variables
3. Add .env to .gitignore
4. In docker-compose/mysql/privileges.sql, use a template or Docker entrypoint script
   that reads the password from an environment variable
5. Remove test credentials from README.md
```

**#21 – Add Feign fallbacks:**
```
For each Feign client interface, create a fallback factory:

1. BankingCoreFeignClient (fund-transfer): return FundTransferResponse with error
   message when core-banking is unavailable
2. BankingCoreRestClient (user-service): throw a service-unavailable exception with
   a clear message
3. BankingCoreRestClient (utility-payment): return UtilityPaymentResponse with error
   message

Register each fallback factory as a Spring bean and reference it in @FeignClient(
fallbackFactory = ...).
```

**#22 – Extract shared library:**
```
Create a new Gradle module called banking-common:
1. Move these classes into it: BaseMapper, AuditAware, AuditConfig, AuditorAwareConfig,
   ApiRequestContext, ApiRequestContextHolder, AppAuthUserFilter, SimpleBankingGlobalException,
   ErrorResponse, GlobalExceptionHandler
2. Publish it as a local dependency
3. Update all services to depend on banking-common instead of maintaining their own copies
4. Delete the duplicated classes from each service
```

**#23 – Create multi-module Gradle build:**
```
1. Create a root settings.gradle that includes all 7 services + banking-common
2. Create a root build.gradle with shared configuration (Java version, Spring Boot
   version, Spring Cloud BOM, common test dependencies)
3. Simplify each service's build.gradle to only declare service-specific dependencies
4. Remove duplicate gradle wrappers — use only the root wrapper
5. Verify all services build with: ./gradlew build from the root
```

**#24 – Add integration tests:**
```
Add integration tests for each service:

1. core-banking-service: @WebMvcTest for AccountController, TransactionController,
   UserController. @DataJpaTest for all repositories.
2. fund-transfer-service: @WebMvcTest for FundTransferController with mocked
   FundTransferService.
3. user-service: @WebMvcTest for UserController with mocked UserService.
4. utility-payment-service: @WebMvcTest for UtilityPaymentController.

Use @MockBean for Feign clients and external dependencies. Use Testcontainers
for MySQL integration tests if needed.
```

**#25 – Add validation exception handling:**
```
In the GlobalExceptionHandler of each service, add:

@ExceptionHandler(MethodArgumentNotValidException.class)
protected ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex) {
    String errors = ex.getBindingResult().getFieldErrors().stream()
        .map(e -> e.getField() + ": " + e.getDefaultMessage())
        .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest()
        .body(ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(errors)
            .build());
}
```

**#26 – Add CORS configuration:**
```
In the API gateway SecurityConfiguration, add CORS configuration:

@Bean
public CorsWebFilter corsWebFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.addAllowedOrigin("http://localhost:3000");
    config.addAllowedHeader("*");
    config.addAllowedMethod("*");
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsWebFilter(source);
}
```

**#27 – Add rate limiting:**
```
Add rate limiting to the API gateway:
1. Add spring-boot-starter-data-redis-reactive dependency
2. Configure RequestRateLimiter filter in the gateway routes
3. Set limits: 100 requests/second per user for general endpoints,
   10 requests/second for fund transfer and payment endpoints
4. Add Redis to docker-compose.yml
```

**#28 – Add dependency vulnerability scanning:**
```
1. Add the OWASP dependency-check Gradle plugin to the root build.gradle:
   id 'org.owasp.dependencycheck' version '9.0.10'
2. Create .github/dependabot.yml to enable GitHub Dependabot for Gradle dependencies
3. Run ./gradlew dependencyCheckAnalyze and document any findings
```

**#29 – Add Prometheus metrics:**
```
1. Add micrometer-registry-prometheus dependency to all services
2. Expose /actuator/prometheus endpoint
3. Add custom metrics in key service methods:
   - counter: fund_transfers_total (tags: status)
   - counter: utility_payments_total (tags: status)
   - counter: user_registrations_total
   - timer: fund_transfer_duration_seconds
4. Add Prometheus and Grafana to docker-compose.yml with pre-configured dashboards
```

**#30 – Convert mappers to Spring beans:**
```
In all services, annotate mapper classes with @Component:
  @Component
  public class FundTransferMapper extends BaseMapper<...> { }

Then inject them in service classes:
  private final FundTransferMapper mapper;
instead of:
  private FundTransferMapper mapper = new FundTransferMapper();
```

---

## Phase 3: Polish (Advanced patterns & operational excellence)

These items add sophisticated patterns and operational tooling. They are important for a mature production system but depend on Phase 1 and Phase 2 foundations.

| # | Gap Ref | Title | Severity | Effort | Description |
|---|---------|-------|----------|--------|-------------|
| 31 | 7.6 | Implement saga pattern for fund transfers | Critical | Large | Add compensation logic, idempotency keys, and reconciliation for the distributed fund transfer flow. |
| 32 | 3.3 | Add contract tests between services | Medium | Large | Implement Spring Cloud Contract or Pact tests for all Feign client interfaces. |
| 33 | 7.5 | Add bulkhead pattern | Medium | Medium | Configure Resilience4j bulkheads to isolate thread pools for each downstream service call. |
| 34 | 6.5 | Add centralized log aggregation | Medium | Large | Add ELK stack or Loki + Grafana to Docker Compose. Configure structured JSON logging. |
| 35 | 5.4 | Add filtering and search to list endpoints | Low | Medium | Add query parameters for filtering by date range, status, account, etc. |
| 36 | 5.2 | Document API versioning strategy | Low | Medium | Define and document the versioning approach. Add content negotiation or path-based versioning support. |
| 37 | 6.2 | Add custom health indicators | Low | Small | Create health indicators for database, Keycloak, and Feign client targets. |
| 38 | 5.7 | Evaluate HATEOAS | Low | Large | Consider adding Spring HATEOAS for discoverable REST APIs. |

### Devin Prompts — Phase 3

**#31 – Implement saga pattern:**
```
Refactor the fund transfer flow to use a saga pattern:

1. Add an idempotency key (UUID) to FundTransferRequest that clients must provide
2. Check for duplicate idempotency keys before processing
3. Implement a state machine for fund transfers: INITIATED → DEBITED → CREDITED → COMPLETED
4. Add compensation logic: if the credit step fails after debit, trigger a reversal
5. Add a scheduled reconciliation job that checks for stuck transfers (DEBITED but
   not CREDITED for > 5 minutes) and either completes or reverses them
6. Consider using Spring State Machine or a process table pattern
```

**#32 – Add contract tests:**
```
Add Spring Cloud Contract tests:

1. In core-banking-service, define contracts for:
   - GET /api/v1/account/bank-account/{account_number}
   - POST /api/v1/transaction/fund-transfer
   - POST /api/v1/transaction/util-payment
   - GET /api/v1/user/{identification}
2. Generate stubs from contracts
3. In fund-transfer-service and utility-payment-service, write consumer tests
   that use the generated stubs
4. Integrate into CI pipeline to catch breaking API changes
```

**#33 – Add bulkhead pattern:**
```
Configure Resilience4j bulkheads for each Feign client:

1. Add resilience4j-bulkhead dependency
2. Configure thread pool bulkheads in application.yml:
   resilience4j.thread-pool-bulkhead.instances.coreBanking:
     maxThreadPoolSize: 10
     coreThreadPoolSize: 5
     queueCapacity: 20
3. Apply @Bulkhead annotations to service methods that call Feign clients
```

**#34 – Add centralized logging:**
```
1. Add Logstash logback encoder dependency to all services
2. Configure logback-spring.xml with JSON output format including traceId, spanId
3. Add ELK stack (Elasticsearch, Logstash, Kibana) to docker-compose.yml
4. Configure Logstash to receive logs and index in Elasticsearch
5. Create Kibana dashboards for error tracking, request tracing, and business metrics
```

**#35 – Add filtering to list endpoints:**
```
Add query parameter filtering to all list endpoints:

1. Fund transfers: filter by status, fromAccount, toAccount, date range, amount range
2. Utility payments: filter by status, providerId, account, date range
3. Users: filter by status, email (partial match)
4. Use Spring Data JPA Specifications or QueryDSL for dynamic filtering
5. Document filters in OpenAPI annotations
```

**#36 – Document API versioning:**
```
1. Create an API versioning policy document
2. Current approach: path-based versioning (/api/v1/)
3. Define rules for when to bump versions
4. Add versioning info to OpenAPI configuration
5. Consider implementing content-type versioning as an alternative
```

**#37 – Add custom health indicators:**
```
Add custom health indicators to relevant services:

1. All services: DatabaseHealthIndicator (verify query works)
2. User service: KeycloakHealthIndicator (verify realm accessible)
3. Fund transfer / utility payment: CoreBankingHealthIndicator (verify core
   service is registered in Eureka and responding)
4. Register indicators in actuator health endpoint
```

**#38 – Evaluate HATEOAS:**
```
Evaluate adding Spring HATEOAS to REST responses:
1. Add spring-boot-starter-hateoas dependency
2. Convert response DTOs to RepresentationModel subclasses
3. Add links: self, related resources, actions
4. Example: FundTransfer response includes link to source account, destination
   account, and transaction details
5. Document the decision on whether to adopt or skip
```

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        │
     Phase 1            │           Phase 2
  ┌─────────────────────┼─────────────────────┐
  │ #1  Fix balance bug │ #17 Input validation │
  │ #2  Fix error codes │ #18 Circuit breakers │
  │ #3  Fix 404 status  │ #19 Unit tests       │
  │ #7  Feign retry     │ #20 Remove creds     │
  │ #8  Feign timeouts  │ #21 Fallbacks        │
  │                     │ #22 Shared library   │
  │                     │ #23 Multi-module     │
  LOW ──────────────────┼──────────────────────── HIGH
  EFFORT                │                      EFFORT
  │ #4  ResponseEntity  │ #31 Saga pattern     │
  │ #5  OpenAPI dep     │ #32 Contract tests   │
  │ #6  Pagination meta │ #34 Log aggregation  │
  │ #10 Secure logging  │                      │
  │ #12 KC thread-safe  │        Phase 3       │
  │ #13 Fix tests       │                      │
  └─────────────────────┼─────────────────────┘
                        │
                    LOW IMPACT
```

---

## Estimated Timeline

| Phase | Items | Estimated Duration | Prerequisites |
|-------|-------|-------------------|---------------|
| **Phase 1** | 16 items | 1–2 weeks | None |
| **Phase 2** | 14 items | 3–5 weeks | Phase 1 complete |
| **Phase 3** | 8 items | 3–5 weeks | Phase 2 complete |

**Total estimated effort: 7–12 weeks** for a single developer working full-time.

---

## Recommended Execution Order Within Phases

### Phase 1 (in order)
1. Fix the balance bug (#1) — active correctness issue
2. Fix error handling (#2, #3, #11, #14) — group related changes
3. Fix OpenAPI dependency (#5) — unblocks Swagger UI
4. Add ResponseEntity types (#4) and pagination metadata (#6)
5. Configure Feign timeouts and retries (#7, #8)
6. Security quick fixes (#9, #10, #12)
7. Fix tests (#13) and code organization (#15, #16)

### Phase 2 (in order)
1. Remove hardcoded credentials (#20) — security prerequisite
2. Add input validation (#17, #25) — security foundation
3. Add circuit breakers and fallbacks (#18, #21) — resilience foundation
4. Extract shared library (#22, #23) — reduces maintenance burden for subsequent work
5. Add unit and integration tests (#19, #24) — validate all changes
6. Add remaining items (#26, #27, #28, #29, #30)

### Phase 3 (in order)
1. Saga pattern (#31) — highest-impact item
2. Contract tests (#32) — validates service interfaces
3. Bulkhead and operational items (#33, #34, #35, #36, #37)
4. HATEOAS evaluation (#38) — lowest priority
