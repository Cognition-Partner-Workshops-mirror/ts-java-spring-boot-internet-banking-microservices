# Engineering Standards Gap Analysis

This document compares the current state of the `ts-java-spring-boot-internet-banking-microservices` codebase against industry best practices. Each gap includes a severity rating and estimated remediation effort.

**Severity scale:** Critical > High > Medium > Low
**Effort scale:** Small (< 1 day) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Medium

Each service has an independent `build.gradle` with duplicated plugin versions, dependency management, and repository declarations. There is no root `settings.gradle` or `build.gradle` to unify common configuration.

**Impact:** Version drift, duplicated boilerplate, no single-command build for the entire project.

### GAP-ORG-02: Duplicated Code Across Services
**Severity:** High | **Effort:** Large

The following classes are copy-pasted identically across 3+ services with no shared library:
- `BaseMapper` (4 copies)
- `AuditAware` (3 copies)
- `AuditConfig` + `AuditorAwareConfig` (3 copies)
- `ApiRequestContext` + `ApiRequestContextHolder` + `AppAuthUserFilter` (3 copies)
- `SimpleBankingGlobalException` + `ErrorResponse` + `GlobalExceptionHandler` (4 copies)

**Impact:** Bug fixes must be applied in multiple places; inconsistencies creep in (e.g., fund-transfer's `ErrorResponse` uses constructor while core-banking's uses builder).

### GAP-ORG-03: Inconsistent Package Naming
**Severity:** Low | **Effort:** Small

Most services use `com.javatodev.finance` as the root package. Sub-packages vary:
- Core Banking: `repository/` at root
- Fund Transfer: `model/repository/`
- User Service: `model/repository/`
- Utility Payment: `repository/` at root

**Impact:** Mild developer confusion when navigating cross-service code.

### GAP-ORG-04: Missing `.gitignore` and Build Artifacts
**Severity:** Low | **Effort:** Small

No `.gitignore` file at the root or per-service level.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400
**Severity:** High | **Effort:** Medium

Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). Not-found entities should return 404, server errors should return 500.

**Current code (all services):**
```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:** Clients cannot distinguish between validation errors, not-found, and server-side failures. Stack traces leak to clients via the generic handler.

### GAP-ERR-02: Raw Exception Details Leaked to Clients
**Severity:** Critical | **Effort:** Small

The catch-all handler returns `"Exception occur inside API " + e`, which serializes the full exception (including stack trace) into the HTTP response body.

**Impact:** Information disclosure vulnerability — internal class names, SQL queries, and infrastructure details may be exposed.

### GAP-ERR-03: Inconsistent Error Response Shapes
**Severity:** Medium | **Effort:** Small

- Business exceptions return `ErrorResponse {code, message}`
- Generic exceptions return a plain `String`
- Fund Transfer's `ErrorResponse` uses an all-args constructor; others use `@Builder`

**Impact:** API consumers must handle two different response shapes for error cases.

### GAP-ERR-04: No Error Handling for Feign Client Failures
**Severity:** High | **Effort:** Medium

- Fund Transfer Service and Utility Payment Service make synchronous Feign calls to Core Banking but have **no circuit breaker, retry, or fallback**.
- Only the User Service implements a `CustomFeignErrorDecoder`.
- If Core Banking is down, exceptions propagate as unstructured 400 responses.

**Impact:** Cascading failures; poor observability of inter-service errors.

---

## 3. Testing

### GAP-TEST-01: Minimal Unit Test Coverage
**Severity:** High | **Effort:** Large

- **Core Banking:** 3 test classes with ~18 meaningful tests (AccountService, TransactionService, UserService)
- **All other services:** Only empty `contextLoads()` stubs (which will fail without a running Config Server/Eureka)
- **No tests** for: FundTransferService, UtilityPaymentService, UserService (user-service), any controller, any mapper

**Impact:** No safety net for regressions in 4 out of 6 services.

### GAP-TEST-02: No Integration Tests
**Severity:** High | **Effort:** Large

No `@SpringBootTest` tests with real HTTP calls, no Testcontainers for MySQL/Keycloak, no `@WebMvcTest` controller tests.

**Impact:** Service wiring, serialization, and database schema issues are only caught in production.

### GAP-TEST-03: No Contract Tests
**Severity:** Medium | **Effort:** Large

Three services communicate via Feign. There are no Pact or Spring Cloud Contract tests to verify API compatibility between consumer and provider.

**Impact:** Breaking changes in Core Banking API silently break Fund Transfer and Utility Payment services.

### GAP-TEST-04: Context-Load Tests Require Infrastructure
**Severity:** Medium | **Effort:** Small

The boilerplate `@SpringBootTest contextLoads()` tests in API Gateway, Config Server, and Service Registry will fail without running infrastructure (Eureka, Config Server). Test profiles disable Eureka/Config for some services but not all.

**Impact:** Tests are not independently runnable; CI will fail without Docker infrastructure.

---

## 4. Security

### GAP-SEC-01: No Input Validation
**Severity:** Critical | **Effort:** Medium

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, or `@Pattern` annotations on any request DTO. Examples:
- `FundTransferRequest`: `amount` could be null, zero, or negative
- `UtilityPaymentRequest`: `providerId` could be null
- User registration: `email` is not validated for format; `password` has no strength requirements

**Impact:** Malformed or malicious input passes directly to business logic and database layer.

### GAP-SEC-02: Hardcoded Credentials in Source Code
**Severity:** Critical | **Effort:** Small

- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KEYCLOAK_ADMIN_PASSWORD: password`
- `privileges.sql`: `IDENTIFIED BY 'oPItyPticIAt'`
- `application.yml` (test): Keycloak `client-secret: e8548d56-d743-45ef-8655-063c9cd96759`
- All database credentials in Docker Compose files

**Impact:** Credential exposure if repository is public or cloned by unauthorized users.

### GAP-SEC-03: CSRF Disabled on API Gateway
**Severity:** Low | **Effort:** Small

`SecurityConfiguration` disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is acceptable for stateless JWT-based APIs but should be documented as an intentional decision.

### GAP-SEC-04: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

The API Gateway has no rate limiting or throttling configuration. Financial APIs are prime targets for abuse.

### GAP-SEC-05: Actuator Endpoints Publicly Accessible
**Severity:** High | **Effort:** Small

Gateway security config permits all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

**Impact:** Health, environment, beans, and potentially sensitive actuator endpoints are exposed without authentication.

### GAP-SEC-06: No Dependency Vulnerability Scanning
**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar scanning tool in the build pipeline.

### GAP-SEC-07: Keycloak Singleton Not Thread-Safe
**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Race condition on startup could create multiple Keycloak client instances.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters
**Severity:** Medium | **Effort:** Small

Most controller methods return `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)
public ResponseEntity fundTransfer(...)
```

Only the User Service uses typed responses: `ResponseEntity<User>`.

**Impact:** Swagger/OpenAPI cannot infer response schemas; compile-time type safety is lost.

### GAP-API-02: No API Versioning Strategy
**Severity:** Low | **Effort:** Small

Endpoints use `/api/v1/` prefix, which is good. However, there is no documented versioning strategy, no version negotiation, and no plan for v2.

### GAP-API-03: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Medium

List endpoints accept Spring's `Pageable` but return raw `List<T>`, discarding pagination metadata (total elements, total pages, current page). Example:
```java
public List<User> readUsers(Pageable pageable) {
    return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
}
```

**Impact:** Clients cannot navigate paginated results or know total result count.

### GAP-API-04: Incomplete OpenAPI Documentation
**Severity:** Medium | **Effort:** Medium

`springdoc-openapi-starter-webflux-ui` is included as a dependency, and `@Operation` / `@Tag` annotations are present. However:
- No `@ApiResponse` annotations to document error responses
- No `@Schema` annotations on DTOs
- Request body validation constraints are not reflected (because they don't exist)
- API Gateway does not aggregate Swagger from downstream services

### GAP-API-05: Inconsistent REST Conventions
**Severity:** Low | **Effort:** Small

- POST `/api/v1/transfer` (fund transfer) — should be `/api/v1/transfers` (plural)
- PATCH `/api/v1/bank-users/update/{id}` — redundant "update" in path; PATCH on `/api/v1/bank-users/{id}` suffices
- No DELETE endpoints
- No standard `Location` header on resource creation (POST should return 201)

### GAP-API-06: No Filtering or Sorting on List Endpoints
**Severity:** Low | **Effort:** Medium

List endpoints only support pagination via `Pageable`. No filtering by status, date range, account, or amount.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging
**Severity:** Medium | **Effort:** Medium

- Some services use `@Slf4j` with parameterized logging: `log.info("Reading account by ID {}", accountNumber)`
- `FundTransferService` uses string concatenation in log: `log.info("Sending fund transfer request {}" + request.toString())` — this is both a bug (wrong format) and a performance issue
- No structured logging (JSON format) configured
- No correlation ID propagation in logs beyond what Micrometer provides

### GAP-OBS-02: No Health Check Customization
**Severity:** Low | **Effort:** Small

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Database connectivity
- Keycloak availability
- Config Server reachability
- Eureka registration status

### GAP-OBS-03: No Metrics Endpoints Beyond Defaults
**Severity:** Low | **Effort:** Medium

No custom Micrometer metrics for business operations (e.g., transfer count, payment success/failure rates, transfer amounts). No Prometheus endpoint configured despite Prometheus being listed in the technology stack.

### GAP-OBS-04: Zipkin Configuration Externalized but Not Validated
**Severity:** Low | **Effort:** Small

Zipkin URL is in the externalized config server. If Zipkin is unavailable, trace reporting silently fails. No alerting or fallback.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

Fund Transfer, Utility Payment, and User services make synchronous Feign calls to Core Banking. There is **no** Resilience4j or Hystrix circuit breaker configured. If Core Banking becomes slow or unresponsive:
- Thread pools in calling services exhaust
- Cascading failure across all dependent services
- Gateway times out, impacting all users

### GAP-RES-02: No Retry Policies
**Severity:** High | **Effort:** Small

No Spring Retry or Resilience4j retry configuration on any Feign client. Transient network errors (DNS hiccups, connection resets) cause immediate failure.

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

Feign clients use default timeouts (no explicit `connectTimeout` or `readTimeout` in configuration). Default Feign timeouts may be too long, blocking threads unnecessarily.

### GAP-RES-04: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

No Feign fallback classes or factory methods defined. When Core Banking is unavailable:
- Fund Transfer: throws unstructured exception
- Utility Payment: throws unstructured exception
- User Registration: throws generic exception

### GAP-RES-05: No Idempotency Protection
**Severity:** High | **Effort:** Medium

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a request (e.g., network timeout), the same transfer/payment may be processed twice.

### GAP-RES-06: Non-Atomic Balance Updates
**Severity:** Critical | **Effort:** Medium

`TransactionService.internalFundTransfer()` performs debit and credit as separate `save()` calls:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
bankAccountRepository.save(fromBankAccountEntity);
// ... credit side ...
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
bankAccountRepository.save(toBankAccountEntity);
```

While `@Transactional` wraps the method, there is **no pessimistic locking** on account rows. Concurrent transfers from the same account can produce incorrect balances due to lost updates.

### GAP-RES-07: Incorrect Available Balance Calculation
**Severity:** Critical | **Effort:** Small

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
This subtracts `amount` from `actualBalance` **after** `actualBalance` was already reduced, effectively double-deducting. The same bug exists in `utilPayment()`.

**Impact:** Available balance will be incorrect after every transaction.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Organization | No multi-project Gradle build | Medium | Medium |
| GAP-ORG-02 | Organization | Duplicated code across services | High | Large |
| GAP-ORG-03 | Organization | Inconsistent package naming | Low | Small |
| GAP-ORG-04 | Organization | Missing `.gitignore` | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Medium |
| GAP-ERR-02 | Error Handling | Raw exception details leaked to clients | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response shapes | Medium | Small |
| GAP-ERR-04 | Error Handling | No error handling for Feign failures | High | Medium |
| GAP-TEST-01 | Testing | Minimal unit test coverage | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | Context-load tests require infrastructure | Medium | Small |
| GAP-SEC-01 | Security | No input validation | Critical | Medium |
| GAP-SEC-02 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-03 | Security | CSRF disabled (documented intent needed) | Low | Small |
| GAP-SEC-04 | Security | No rate limiting | Medium | Medium |
| GAP-SEC-05 | Security | Actuator endpoints publicly accessible | High | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-07 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without type params | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Small |
| GAP-API-03 | API Design | No pagination metadata in responses | Medium | Medium |
| GAP-API-04 | API Design | Incomplete OpenAPI documentation | Medium | Medium |
| GAP-API-05 | API Design | Inconsistent REST conventions | Low | Small |
| GAP-API-06 | API Design | No filtering/sorting on list endpoints | Low | Medium |
| GAP-OBS-01 | Observability | Inconsistent logging | Medium | Medium |
| GAP-OBS-02 | Observability | No health check customization | Low | Small |
| GAP-OBS-03 | Observability | No custom metrics | Low | Medium |
| GAP-OBS-04 | Observability | Zipkin config not validated | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | High | Medium |
| GAP-RES-06 | Resilience | Non-atomic balance updates | Critical | Medium |
| GAP-RES-07 | Resilience | Incorrect available balance calculation | Critical | Small |
