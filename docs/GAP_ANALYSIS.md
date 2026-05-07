# Engineering Standards Gap Analysis

## Methodology

Each gap is assessed against industry best practices for production-grade Spring Boot microservices. Ratings:

- **Severity:** Critical / High / Medium / Low
- **Effort:** Small (< 1 day) / Medium (1-3 days) / Large (3+ days)

---

## 1. Code Organization

### GAP-CO-01: No Multi-Module Gradle Build

**Severity:** Medium | **Effort:** Medium

Each service has an independent `build.gradle` with no root `settings.gradle`. There is no shared parent build or convention plugin. This leads to duplicated dependency versions, plugin declarations, and configuration across all 6 services.

**Evidence:** All 6 `build.gradle` files independently declare `spring-boot:3.2.4`, `spring-cloud:2023.0.0`, `mysql-connector-j:8.4.0`, etc.

**Best Practice:** Use a Gradle multi-module build or a shared convention plugin to centralize dependency versions and common configuration.

---

### GAP-CO-02: Duplicated Code Across Services

**Severity:** High | **Effort:** Medium

The following classes are copy-pasted across 3+ services with minor variations:
- `AuditAware` (base DTO with audit fields)
- `BaseMapper` (generic mapper interface)
- `ErrorResponse` (error response DTO)
- `GlobalExceptionHandler` (exception handling)
- `SimpleBankingGlobalException` (base exception)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (auth filter chain)
- `CustomFeignClientConfiguration` (Feign token relay)

**Evidence:** `AuditAware.java` exists identically in `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`.

**Best Practice:** Extract shared code into a `banking-common` library module.

---

### GAP-CO-03: Inconsistent Package Structure

**Severity:** Low | **Effort:** Small

Package layout varies across services:
- `core-banking-service`: `repository/` at root level
- `internet-banking-user-service`: `model/repository/`
- `internet-banking-utility-payment-service`: `repository/` at root level
- Feign clients: `service/rest/client/` vs `service/rest/` vs directly on `service/rest/`

**Best Practice:** Standardize on a single package convention (e.g., `controller/`, `service/`, `repository/`, `model/entity/`, `model/dto/`, `config/`, `exception/`).

---

### GAP-CO-04: Mappers Instantiated Inline Instead of as Beans

**Severity:** Low | **Effort:** Small

Mapper objects are created as field initializers (`private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans. This prevents dependency injection into mappers and makes testing harder.

**Evidence:** `AccountService.java:21`, `UserService.java:20` (core-banking), `FundTransferService.java:29`, `UtilityPaymentService.java:29`.

**Best Practice:** Register mappers as `@Component` beans or use MapStruct for compile-time mapping.

---

## 2. Error Handling

### GAP-EH-01: Generic Catch-All Returns 400 for All Errors

**Severity:** Critical | **Effort:** Small

Every `GlobalExceptionHandler` catches `Exception.class` and returns HTTP 400 with an unstructured string body: `"Exception occur inside API " + e`. This:
- Leaks internal stack traces/class names to clients
- Returns 400 (client error) for server-side exceptions (should be 500)
- Does not use the structured `ErrorResponse` format

**Evidence:** All 4 services' `GlobalExceptionHandler.java` have identical catch-all:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Best Practice:** Return 500 for unexpected errors, use structured `ErrorResponse`, never leak exception details.

---

### GAP-EH-02: No HTTP Status Code Differentiation

**Severity:** High | **Effort:** Small

All handled exceptions return HTTP 400 regardless of the actual error type:
- `EntityNotFoundException` should return **404**
- `InsufficientFundsException` should return **422** (Unprocessable Entity)
- `UserAlreadyRegisteredException` should return **409** (Conflict)
- Validation errors should return **400**
- Unexpected errors should return **500**

**Evidence:** `GlobalExceptionHandler` uses `.badRequest()` (400) for all `SimpleBankingGlobalException` subclasses.

**Best Practice:** Map each exception type to its appropriate HTTP status code.

---

### GAP-EH-03: No Input Validation

**Severity:** Critical | **Effort:** Medium

No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` annotations on any request DTOs or controller parameters. Invalid input (null amounts, empty account numbers) passes through to service logic and causes `NullPointerException` or database errors.

**Evidence:** `FundTransferRequest`, `UtilityPaymentRequest`, `User` DTOs have no Bean Validation annotations. Controllers have no `@Valid` on `@RequestBody`.

**Best Practice:** Add Jakarta Bean Validation constraints to all DTOs and `@Valid` to controller parameters.

---

### GAP-EH-04: Inconsistent Error Response Structure

**Severity:** Medium | **Effort:** Small

The `ErrorResponse` class varies across services:
- `core-banking-service`: Uses `@Builder` pattern
- `internet-banking-fund-transfer-service`: Uses constructor `new ErrorResponse(code, message)`
- The catch-all handler returns a plain String, not `ErrorResponse`

**Best Practice:** Use a single, consistent error response structure across all services.

---

### GAP-EH-05: No Feign Error Handling in Fund Transfer / Utility Payment

**Severity:** High | **Effort:** Medium

When Feign calls to `core-banking-service` fail (network error, 4xx, 5xx), the exception propagates unhandled. Only the user-service has a `CustomFeignErrorDecoder`; the other services rely on default Feign behavior that throws `FeignException`, which the generic handler turns into a 400 with a stack trace.

**Evidence:** `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` have `CustomFeignClientConfiguration` but only for token relay, not error decoding.

**Best Practice:** Implement `ErrorDecoder` for all Feign clients to translate upstream errors into meaningful downstream exceptions.

---

## 3. Testing

### GAP-TE-01: Minimal Test Coverage

**Severity:** High | **Effort:** Large

| Service | Test Files | Coverage |
|---|---|---|
| core-banking-service | 3 unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest) + context load | Service layer only |
| internet-banking-user-service | 1 (context load only) | None |
| internet-banking-fund-transfer-service | 1 (context load only) | None |
| internet-banking-utility-payment-service | 1 (context load only) | None |
| internet-banking-api-gateway | 1 (context load only) | None |
| internet-banking-config-server | 1 (context load only) | None |
| internet-banking-service-registry | 1 (context load only) | None |

Only `core-banking-service` has meaningful unit tests. All other services have only Spring context load tests (`@SpringBootTest` smoke tests).

**Best Practice:** Aim for 80%+ line coverage on service and controller layers. Every service should have unit tests for service classes and integration tests for controllers.

---

### GAP-TE-02: No Integration Tests

**Severity:** High | **Effort:** Large

No `@SpringBootTest` + `@AutoConfigureMockMvc` controller tests. No `@DataJpaTest` repository tests. No test containers for MySQL integration testing.

**Best Practice:** Add controller integration tests using MockMvc and repository tests using H2 or Testcontainers.

---

### GAP-TE-03: No Contract Tests

**Severity:** Medium | **Effort:** Large

No Spring Cloud Contract or Pact tests between services. Feign client interfaces are not verified against producer APIs.

**Best Practice:** Implement consumer-driven contract tests (Spring Cloud Contract or Pact) for all Feign client interactions.

---

### GAP-TE-04: Context Load Tests Will Fail Without Infrastructure

**Severity:** Medium | **Effort:** Small

The `@SpringBootTest` tests in user-service, fund-transfer, and utility-payment require Eureka and Config Server. The test `application.yml` files disable Flyway and use H2 but still reference Eureka, which will cause failures in isolated CI.

**Evidence:** `internet-banking-fund-transfer-service/src/test/resources/application.yml` references `eureka.client.service-url.defaultZone: http://localhost:8081/eureka`.

**Best Practice:** Disable Eureka registration in test profiles or use `@MockBean` for discovery client.

---

## 4. Security

### GAP-SE-01: Hardcoded Credentials in Docker Compose

**Severity:** Critical | **Effort:** Small

Production-ready credentials are hardcoded in `docker-compose.yml`:
- MySQL root password: `woVERANKliGharym`
- Keycloak admin password: `password`
- Keycloak DB password: `password`

**Evidence:** `docker-compose/docker-compose.yml:50-52`, `docker-compose/docker-compose.yml:20-23`.

**Best Practice:** Use Docker secrets, `.env` files (gitignored), or external secret managers. At minimum, use environment variable references.

---

### GAP-SE-02: Test Credentials in README

**Severity:** Medium | **Effort:** Small

Plaintext credentials in `README.md`: `ib_admin@javatodev.com / 5V7huE3G86uB`.

**Best Practice:** Reference credentials from a secured location, not committed source files.

---

### GAP-SE-03: CSRF Disabled Without Documentation

**Severity:** Medium | **Effort:** Small

CSRF protection is disabled in the API Gateway: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While appropriate for a stateless JWT API, there is no documentation explaining the security rationale.

**Evidence:** `SecurityConfiguration.java:35`.

**Best Practice:** Document the security design decision. Consider CSRF protection if the gateway ever serves browser-based sessions.

---

### GAP-SE-04: No Rate Limiting or Throttling

**Severity:** High | **Effort:** Medium

No rate limiting on any endpoint. Financial APIs (fund transfer, payments) are especially sensitive to abuse.

**Best Practice:** Implement Spring Cloud Gateway rate limiting filters (e.g., `RequestRateLimiter` with Redis).

---

### GAP-SE-05: Keycloak Client Singleton Not Thread-Safe

**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-synchronized, non-volatile lazy singleton pattern. Under concurrent access, multiple instances could be created.

**Evidence:** `KeycloakProperties.java:25-40`.

**Best Practice:** Use `synchronized` block, `volatile` keyword, or `@Bean` factory method for thread-safe initialization.

---

### GAP-SE-06: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar tool configured in the build. Vulnerabilities in transitive dependencies go undetected.

**Best Practice:** Add `org.owasp.dependencycheck` Gradle plugin or integrate with GitHub Dependabot.

---

### GAP-SE-07: Downstream Services Lack Authentication

**Severity:** High | **Effort:** Medium

The API Gateway validates JWT tokens, but downstream services (core-banking, user, fund-transfer, utility-payment) have **no security configuration**. They accept all requests on their direct ports (8083-8092), relying solely on network-level isolation via Docker networking.

**Evidence:** No Spring Security dependency or `SecurityConfiguration` in any service except the API Gateway.

**Best Practice:** Either add JWT validation to each service or ensure network policies strictly prevent direct access. Defense-in-depth requires both.

---

## 5. API Design

### GAP-AD-01: Raw `ResponseEntity` Without Type Parameters

**Severity:** Medium | **Effort:** Small

Most controller methods return raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This loses type safety and prevents accurate OpenAPI schema generation.

**Evidence:** `AccountController.java:27`, `TransactionController.java:29`, `FundTransferController.java:30`.

**Best Practice:** Always use parameterized `ResponseEntity<T>` for accurate API documentation and type safety.

---

### GAP-AD-02: No API Versioning Strategy

**Severity:** Low | **Effort:** Medium

While paths include `/api/v1/`, there is no formal versioning strategy, no `Accept` header versioning, and no plan documented for when v2 is needed.

**Best Practice:** Document the versioning strategy (URI path, header, or media type) and ensure gateway routing supports it.

---

### GAP-AD-03: Inconsistent Pagination Response

**Severity:** Medium | **Effort:** Small

Paginated endpoints accept Spring `Pageable` but return `List<T>` instead of `Page<T>`. Clients receive no metadata (total elements, total pages, current page).

**Evidence:** `UserController.readUsers()` returns `List<User>`, not `Page<User>`.

**Best Practice:** Return `Page<T>` or a custom paginated wrapper with `{ content, totalElements, totalPages, pageNumber }`.

---

### GAP-AD-04: No Filtering or Sorting Documentation

**Severity:** Low | **Effort:** Small

Endpoints that accept `Pageable` support sorting/filtering implicitly via Spring Data, but this is not documented in OpenAPI annotations.

**Best Practice:** Add `@Parameter` annotations documenting supported sort fields and filter criteria.

---

### GAP-AD-05: PATCH Semantics Not Followed for User Update

**Severity:** Low | **Effort:** Small

`PATCH /api/v1/bank-users/update/{id}` uses PATCH but the path includes a verb (`update`), which is not RESTful. The payload (`UserUpdateRequest`) only contains `status`, making it more of a state-transition action.

**Best Practice:** Use `PATCH /api/v1/bank-users/{id}` for partial updates or `POST /api/v1/bank-users/{id}/approve` for explicit state transitions.

---

### GAP-AD-06: OpenAPI Starter Mismatch

**Severity:** Medium | **Effort:** Small

Services use `springdoc-openapi-starter-webflux-ui` but are servlet-based (Spring MVC). Only the API Gateway (which uses WebFlux) should use the webflux starter. Servlet services should use `springdoc-openapi-starter-webmvc-ui`.

**Evidence:** All 4 business services' `build.gradle` include `springdoc-openapi-starter-webflux-ui:2.1.0`.

**Best Practice:** Use `springdoc-openapi-starter-webmvc-ui` for servlet-based services.

---

## 6. Observability

### GAP-OB-01: No Structured Logging

**Severity:** High | **Effort:** Medium

All services use default Spring Boot logging with unstructured text output. No JSON log format, no MDC correlation IDs, no log aggregation configuration.

**Evidence:** `@Slf4j` used throughout but no `logback-spring.xml` or logging configuration files in any service.

**Best Practice:** Configure JSON logging (Logstash encoder), include trace IDs in MDC, and configure log aggregation (ELK/Loki).

---

### GAP-OB-02: Sensitive Data in Logs

**Severity:** High | **Effort:** Small

Request DTOs are logged via `toString()`, potentially exposing sensitive data:
- `UserController.java:31`: `log.info("Creating user with {}", request.toString())` - may log passwords
- `FundTransferController.java:31`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` - logs account numbers and amounts

**Evidence:** `User` DTO includes a `password` field and its `@Data` annotation generates a `toString()` that includes all fields.

**Best Practice:** Exclude sensitive fields from `toString()` using `@ToString.Exclude` or use dedicated log-safe representations.

---

### GAP-OB-03: No Custom Health Checks

**Severity:** Medium | **Effort:** Small

Services include `spring-boot-starter-actuator` but rely only on default health indicators. No custom health checks for:
- Keycloak connectivity (user-service)
- Core-banking-service availability (fund-transfer, utility-payment)
- Database connectivity beyond default DataSource check

**Best Practice:** Add custom `HealthIndicator` beans for critical dependencies.

---

### GAP-OB-04: No Metrics Dashboards or Alerting

**Severity:** Medium | **Effort:** Medium

README mentions Prometheus but no Prometheus configuration, Grafana dashboards, or alerting rules exist. Micrometer metrics are collected but not exported to any monitoring backend.

**Best Practice:** Add `micrometer-registry-prometheus` and provide Grafana dashboard JSON files.

---

### GAP-OB-05: Tracing Coverage Gaps

**Severity:** Low | **Effort:** Small

While Zipkin dependencies are present in all services, there is no explicit span creation for important business operations. Default auto-instrumentation covers HTTP calls but misses:
- Database operations within `@Transactional` methods
- Keycloak admin API calls

**Best Practice:** Add custom spans for critical business operations using `@NewSpan` or `Tracer`.

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

No circuit breaker (Resilience4j, Hystrix) on any Feign client call. If `core-banking-service` becomes unavailable, fund-transfer and utility-payment services will:
- Block threads waiting for TCP timeout
- Return cryptic errors to clients
- Potentially cascade failures

**Evidence:** No `spring-cloud-starter-circuitbreaker-resilience4j` in any `build.gradle`. No `@CircuitBreaker` annotations.

**Best Practice:** Add Resilience4j circuit breakers on all inter-service calls with appropriate fallback behavior.

---

### GAP-RE-02: No Retry Policies

**Severity:** High | **Effort:** Small

No retry configuration on Feign clients. Transient network errors cause immediate failure without retry.

**Evidence:** No `spring-retry` dependency. No `Retryer` configuration in Feign client config.

**Best Practice:** Configure Feign retry with idempotent-safe policies (retry GET/read operations, not POST/write operations).

---

### GAP-RE-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit connect/read timeouts on Feign clients or HTTP connections. Default JVM socket timeouts apply (potentially infinite).

**Evidence:** `CustomFeignClientConfiguration` only configures `RequestInterceptor` for token relay; no `Request.Options` for timeouts.

**Best Practice:** Set explicit `connectTimeout` and `readTimeout` on all Feign clients (e.g., 5s connect, 30s read).

---

### GAP-RE-04: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

When inter-service calls fail, no graceful degradation occurs. All errors propagate directly to the client as 400 responses.

**Best Practice:** Implement Feign fallback factories that return meaningful error responses (e.g., "Service temporarily unavailable, please retry").

---

### GAP-RE-05: No Bulkhead Isolation

**Severity:** Medium | **Effort:** Medium

All Feign calls share the same thread pool. A slow `core-banking-service` could exhaust all threads, blocking unrelated requests.

**Best Practice:** Configure Resilience4j bulkheads (thread pool or semaphore) to isolate inter-service call resources.

---

### GAP-RE-06: Balance Calculation Bug (Data Integrity)

**Severity:** Critical | **Effort:** Small

In `TransactionService.java`, the `availableBalance` calculation is incorrect. After subtracting the amount from `actualBalance`, it subtracts again from the already-reduced `actualBalance` for `availableBalance`:

```java
// Line 90-91: Fund Transfer debit
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^ BUG: actualBalance already reduced, so availableBalance = actualBalance - 2*amount

// Line 63-64: Utility Payment debit - same bug
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```

This double-subtraction causes `availableBalance` to drift lower than `actualBalance` after every transaction.

**Best Practice:** Set `availableBalance` to the new `actualBalance` value, or compute both from the original balance independently.

---

### GAP-RE-07: Transaction Entity Uses CascadeType.ALL on OneToOne

**Severity:** Medium | **Effort:** Small

`TransactionEntity.account` has `@OneToOne(cascade = CascadeType.ALL)` which means saving a transaction could cascade updates/deletes to the bank account entity. This is dangerous for a financial system where account records should not be accidentally modified or deleted through transaction operations.

**Evidence:** `TransactionEntity.java:32-34`.

**Best Practice:** Remove cascade or use `CascadeType.PERSIST` only. Explicitly manage account entity lifecycle.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-CO-01 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-CO-02 | Code Organization | Duplicated code across services | High | Medium |
| GAP-CO-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-CO-04 | Code Organization | Mappers instantiated inline | Low | Small |
| GAP-EH-01 | Error Handling | Generic catch-all returns 400 with stack trace | Critical | Small |
| GAP-EH-02 | Error Handling | No HTTP status code differentiation | High | Small |
| GAP-EH-03 | Error Handling | No input validation | Critical | Medium |
| GAP-EH-04 | Error Handling | Inconsistent error response structure | Medium | Small |
| GAP-EH-05 | Error Handling | No Feign error handling in fund-transfer/utility-payment | High | Medium |
| GAP-TE-01 | Testing | Minimal test coverage | High | Large |
| GAP-TE-02 | Testing | No integration tests | High | Large |
| GAP-TE-03 | Testing | No contract tests | Medium | Large |
| GAP-TE-04 | Testing | Context load tests fail without infrastructure | Medium | Small |
| GAP-SE-01 | Security | Hardcoded credentials in Docker Compose | Critical | Small |
| GAP-SE-02 | Security | Test credentials in README | Medium | Small |
| GAP-SE-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SE-04 | Security | No rate limiting | High | Medium |
| GAP-SE-05 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SE-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SE-07 | Security | Downstream services lack authentication | High | Medium |
| GAP-AD-01 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-AD-02 | API Design | No formal API versioning strategy | Low | Medium |
| GAP-AD-03 | API Design | Inconsistent pagination response | Medium | Small |
| GAP-AD-04 | API Design | No filtering/sorting documentation | Low | Small |
| GAP-AD-05 | API Design | PATCH semantics not followed | Low | Small |
| GAP-AD-06 | API Design | OpenAPI starter mismatch (webflux vs webmvc) | Medium | Small |
| GAP-OB-01 | Observability | No structured logging | High | Medium |
| GAP-OB-02 | Observability | Sensitive data in logs | High | Small |
| GAP-OB-03 | Observability | No custom health checks | Medium | Small |
| GAP-OB-04 | Observability | No metrics dashboards or alerting | Medium | Medium |
| GAP-OB-05 | Observability | Tracing coverage gaps | Low | Small |
| GAP-RE-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RE-02 | Resilience | No retry policies | High | Small |
| GAP-RE-03 | Resilience | No timeout configuration | High | Small |
| GAP-RE-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RE-05 | Resilience | No bulkhead isolation | Medium | Medium |
| GAP-RE-06 | Resilience | Balance calculation bug (double subtraction) | Critical | Small |
| GAP-RE-07 | Resilience | CascadeType.ALL on TransactionEntity | Medium | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 5 |
| High | 11 |
| Medium | 15 |
| Low | 6 |
