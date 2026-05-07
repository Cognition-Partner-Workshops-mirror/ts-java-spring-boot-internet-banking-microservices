# Engineering Standards Gap Analysis

## Table of Contents

- [Summary Dashboard](#summary-dashboard)
- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)

---

## Summary Dashboard

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 1 | 2 | 3 | 1 | 7 |
| Error Handling | 1 | 2 | 2 | 0 | 5 |
| Testing | 1 | 2 | 2 | 0 | 5 |
| Security | 2 | 3 | 2 | 0 | 7 |
| API Design | 0 | 2 | 3 | 1 | 6 |
| Observability | 0 | 1 | 3 | 1 | 5 |
| Resilience | 1 | 3 | 1 | 0 | 5 |
| **Total** | **6** | **15** | **16** | **3** | **40** |

---

## 1. Code Organization

### GAP-ORG-001: No Multi-Project Gradle Build
**Severity:** High | **Effort:** Medium

**Current State:** Each microservice is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to unify the build.

**Impact:** Cannot run a single command to build/test all services. Dependency versions are duplicated across 6 `build.gradle` files (e.g., Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector 8.4.0). Version drift risk is high.

**Best Practice:** Use a Gradle multi-project build with a root `settings.gradle` that includes all services and a shared `buildSrc` or version catalog for dependency management.

---

### GAP-ORG-002: Duplicated Code Across Services
**Severity:** High | **Effort:** Large

**Current State:** The following classes are copy-pasted across multiple services with minor variations:
- `AuditAware` (user-service, fund-transfer-service, utility-payment-service)
- `BaseMapper` (core-banking, user-service, fund-transfer-service, utility-payment-service)
- `SimpleBankingGlobalException` (all 4 business services)
- `ErrorResponse` (all 4 business services)
- `GlobalExceptionHandler` (all 4 business services)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (user-service, fund-transfer-service, utility-payment-service)
- `CustomFeignClientConfiguration` (fund-transfer-service, utility-payment-service)

**Impact:** Bug fixes or enhancements must be replicated manually across all copies. Already diverging (e.g., `ErrorResponse` uses `@Builder` in some services but constructor in fund-transfer).

**Best Practice:** Extract shared code into a `common-library` module published as a local dependency.

---

### GAP-ORG-003: Inconsistent Package Structure
**Severity:** Medium | **Effort:** Small

**Current State:** Package organization differs between services:
- Core Banking: `repository/` at top level, DTOs in `model.dto/`
- User Service: repository in `model.repository/`, DTOs in `model.dto/`, REST clients in `service.rest/`
- Fund Transfer: repository in `model.repository/`, REST client in `service.rest.client/`
- Utility Payment: repository in `repository/` (top level), REST client in `service.rest/`

**Impact:** Cognitive overhead when navigating between services. New developers cannot predict where to find classes.

**Best Practice:** Standardize on a single package convention across all services (e.g., `controller/`, `service/`, `repository/`, `model/entity/`, `model/dto/`, `client/`, `config/`, `exception/`).

---

### GAP-ORG-004: Mixed Concerns in DTO Layer
**Severity:** Medium | **Effort:** Medium

**Current State:** `AuditAware` is both a JPA `@MappedSuperclass` (with `@EntityListeners`) and a DTO base class. It lives in `model.dto` but is extended by both entities and DTOs. The `User` DTO in user-service extends `AuditAware`, making it both a request/response object and a JPA-aware class.

**Impact:** Audit fields leak into API responses (suppressed by `@JsonIgnore` but still conceptually wrong). Entity and transport concerns are coupled.

**Best Practice:** Separate entity base classes from DTO classes. Use distinct request/response DTOs that do not extend JPA superclasses.

---

### GAP-ORG-005: Mappers Instantiated Inline Instead of as Beans
**Severity:** Low | **Effort:** Small

**Current State:** Mapper classes (e.g., `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are instantiated with `new` inside service classes rather than managed as Spring beans.

```java
private UserMapper userMapper = new UserMapper();  // Not a Spring bean
```

**Impact:** Cannot leverage dependency injection, AOP, or testing features. Inconsistent with Spring conventions.

**Best Practice:** Annotate mappers with `@Component` and inject them, or use MapStruct with Spring integration.

---

### GAP-ORG-006: Config Server Sources from Public GitHub Repo
**Severity:** Critical | **Effort:** Small

**Current State:** The Config Server fetches configuration from a **public** GitHub repository (`https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git`). This external repo contains database credentials, Keycloak secrets, and other sensitive configuration.

**Impact:** Configuration is not under the team's control. Secrets are exposed publicly. Any change to the external repo could break all services.

**Best Practice:** Host configuration in a private repository or use Spring Cloud Config with encryption. Store secrets in a vault (HashiCorp Vault, AWS Secrets Manager).

---

### GAP-ORG-007: Inconsistent Indentation Style
**Severity:** Medium | **Effort:** Small

**Current State:** Some `build.gradle` files use tabs (user-service, utility-payment-service), others use spaces (core-banking). Java files also show mixed indentation. No `.editorconfig` or formatter configuration exists.

**Impact:** Noisy diffs, inconsistent code appearance.

**Best Practice:** Add `.editorconfig` and a Gradle Spotless plugin configuration for automated formatting.

---

## 2. Error Handling

### GAP-ERR-001: All Exceptions Return HTTP 400 Bad Request
**Severity:** Critical | **Effort:** Medium

**Current State:** Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). The catch-all `Exception.class` handler also returns 400 with a raw exception string.

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:** Clients cannot distinguish between not-found (404), validation errors (422), server errors (500), or authorization errors (403). Violates HTTP semantics. The catch-all handler leaks internal stack traces.

**Best Practice:** Map exceptions to appropriate HTTP status codes:
- `EntityNotFoundException` -> 404
- Validation errors -> 422
- `InsufficientFundsException` -> 422 or 409
- Unexpected exceptions -> 500 (with sanitized message)

---

### GAP-ERR-002: Exception Message Leaks Internal Details
**Severity:** High | **Effort:** Small

**Current State:** The catch-all exception handler returns the full exception object as a string: `"Exception occur inside API " + e`. This exposes class names, stack traces, and potentially sensitive information.

**Impact:** Information disclosure vulnerability. Attackers can learn about internal architecture, library versions, and database structure.

**Best Practice:** Return a generic error message for unexpected exceptions. Log the full exception server-side.

---

### GAP-ERR-003: Inconsistent Error Response Format
**Severity:** High | **Effort:** Medium

**Current State:** 
- `SimpleBankingGlobalException` subclasses return `ErrorResponse { code, message }`.
- The catch-all handler returns a raw `String`.
- Fund Transfer service uses constructor-based `ErrorResponse`; others use `@Builder`.

**Impact:** Clients must handle multiple error response shapes. Cannot reliably parse errors.

**Best Practice:** Standardize on a single error response envelope (e.g., RFC 7807 Problem Details) across all services.

---

### GAP-ERR-004: No Feign Error Propagation
**Severity:** Medium | **Effort:** Medium

**Current State:** `CustomFeignErrorDecoder` exists in the user-service but its implementation was not fully reviewed. The fund-transfer and utility-payment services configure `CustomFeignClientConfiguration` but rely on default Feign error decoding for most scenarios. When core-banking returns an error, the calling service may not properly translate or propagate it.

**Impact:** Inter-service errors may be swallowed or wrapped in generic `FeignException`, losing the original error context and code.

**Best Practice:** Implement a consistent `ErrorDecoder` that extracts the upstream `ErrorResponse` and re-throws an appropriate local exception.

---

### GAP-ERR-005: SimpleBankingGlobalException Has Constructor Bug
**Severity:** Medium | **Effort:** Small

**Current State:** `SimpleBankingGlobalException` has three constructors:
1. `@AllArgsConstructor` (code, message) from Lombok
2. `@NoArgsConstructor` from Lombok
3. Manual `SimpleBankingGlobalException(String message)` that calls `super(message)` but does NOT set the `message` field

The `@AllArgsConstructor` constructor sets the `message` field but does NOT call `super(message)`, so `getMessage()` from `RuntimeException` will return `null` while `this.message` has the value. This creates confusion about which `getMessage()` is invoked.

**Impact:** Error messages may be inconsistent depending on how the exception is caught and logged.

**Best Practice:** Remove Lombok constructors and write explicit constructors that properly call `super(message)` and set fields.

---

## 3. Testing

### GAP-TEST-001: Only Core Banking Service Has Unit Tests
**Severity:** Critical | **Effort:** Large

**Current State:** 
- **Core Banking Service:** 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with ~20 test methods covering service layer logic.
- **All Other Services:** Only empty/skeleton `*ApplicationTests.java` files that test Spring context loading (and would fail without infrastructure running).

**Impact:** No automated verification of business logic in user-service, fund-transfer-service, or utility-payment-service. Regressions will only be caught in production.

**Best Practice:** Each service should have unit tests for its service layer at minimum, with mocked dependencies.

---

### GAP-TEST-002: No Integration Tests
**Severity:** High | **Effort:** Large

**Current State:** No `@SpringBootTest` tests with real HTTP calls, no Testcontainers setup, no Spring MockMvc/WebTestClient tests for controllers.

**Impact:** Controller routing, request serialization, response serialization, JPA queries, and Flyway migrations are untested in an automated fashion.

**Best Practice:** Add integration tests using Testcontainers (MySQL) and MockMvc/WebTestClient for controller layer testing.

---

### GAP-TEST-003: No Contract Tests Between Services
**Severity:** High | **Effort:** Large

**Current State:** No Spring Cloud Contract, Pact, or any other consumer-driven contract testing framework. Feign client interfaces are not verified against provider APIs.

**Impact:** Breaking changes in core-banking-service APIs will not be detected until runtime. Service-to-service contracts are implicit and undocumented.

**Best Practice:** Implement Spring Cloud Contract or Pact for consumer-driven contract testing between fund-transfer/utility-payment/user services and core-banking.

---

### GAP-TEST-004: Application Context Tests Require Infrastructure
**Severity:** Medium | **Effort:** Small

**Current State:** `*ApplicationTests.java` files in all services attempt to load the full Spring context, which requires Config Server, Eureka, MySQL, and Keycloak to be running.

**Impact:** Tests cannot run in CI without the full infrastructure stack. Tests are effectively unusable.

**Best Practice:** Use `@SpringBootTest` with test profiles that disable Eureka, Config Server, and use H2/Testcontainers for databases (like core-banking's test config does).

---

### GAP-TEST-005: No Test Coverage Measurement
**Severity:** Medium | **Effort:** Small

**Current State:** No JaCoCo or other code coverage plugin configured in any `build.gradle`. No coverage thresholds enforced.

**Impact:** Cannot measure or track test coverage over time. No objective baseline for testing completeness.

**Best Practice:** Add JaCoCo Gradle plugin with minimum coverage thresholds (e.g., 70% line coverage for service layer).

---

## 4. Security

### GAP-SEC-001: Hardcoded Credentials in Source Code
**Severity:** Critical | **Effort:** Medium

**Current State:** Multiple credentials are hardcoded in committed files:
- `docker-compose.yml`: MySQL root password `woVERANKliGharym`
- `docker-compose.yml`: Keycloak admin password `password`, DB password `password`
- `mysql/privileges.sql`: App user password `oPItyPticIAt`
- `mysql/Dockerfile`: MySQL root password in `ENV`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

**Impact:** Secrets are in version control history permanently. Anyone with repo access has database and admin credentials.

**Best Practice:** Use Docker secrets, environment variable files (`.env` excluded from Git), or a secrets manager. Never commit passwords.

---

### GAP-SEC-002: No Input Validation on API Endpoints
**Severity:** Critical | **Effort:** Medium

**Current State:** No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, or any Jakarta Bean Validation annotations on any request body or path variable across all services.

Examples:
- Fund transfer accepts negative amounts
- Fund transfer allows same-account transfers
- User registration accepts empty email/password
- Utility payment accepts zero or negative amounts

**Impact:** Invalid or malicious data can enter the system. Negative transfers could credit the source account. Empty fields cause cryptic NPEs.

**Best Practice:** Add `spring-boot-starter-validation` dependency and annotate all request DTOs with validation constraints. Add `@Valid` to controller method parameters.

---

### GAP-SEC-003: CSRF Disabled Without Documentation
**Severity:** Medium | **Effort:** Small

**Current State:** API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is common for stateless APIs but there is no comment explaining the rationale.

**Impact:** Low risk for pure REST API (JWT-based), but should be documented as a conscious decision.

**Best Practice:** Add a comment explaining why CSRF is disabled (stateless JWT authentication).

---

### GAP-SEC-004: No Rate Limiting
**Severity:** High | **Effort:** Medium

**Current State:** No rate limiting configured at the API Gateway or any service level.

**Impact:** Vulnerable to brute-force attacks on authentication, DDoS, and resource exhaustion.

**Best Practice:** Configure Spring Cloud Gateway rate limiting using `RequestRateLimiter` filter with Redis-backed token bucket.

---

### GAP-SEC-005: Keycloak Singleton Not Thread-Safe
**Severity:** High | **Effort:** Small

**Current State:** `KeycloakProperties.getInstance()` uses a classic double-check locking pattern without synchronization:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Race condition on startup could create multiple Keycloak instances, leading to resource leaks or authentication failures.

**Best Practice:** Use `@Bean` method in a `@Configuration` class (Spring guarantees singleton scope) or use `volatile` + `synchronized`.

---

### GAP-SEC-006: No Dependency Vulnerability Scanning
**Severity:** High | **Effort:** Small

**Current State:** No OWASP Dependency-Check, Snyk, or Dependabot configured. No `gradle dependencyCheckAnalyze` task.

**Impact:** Known CVEs in dependencies (Keycloak client, MySQL connector, Spring libraries) go undetected.

**Best Practice:** Add OWASP Dependency-Check Gradle plugin or enable Dependabot/Snyk in CI.

---

### GAP-SEC-007: Password Sent in Plain Text in User DTO
**Severity:** Medium | **Effort:** Small

**Current State:** The `User` DTO in user-service includes a `password` field that is used for both request and response. There is no `@JsonProperty(access = WRITE_ONLY)` annotation.

**Impact:** Password could be included in API responses or logged via `toString()`.

**Best Practice:** Use separate request/response DTOs. Mark password as write-only. Never include password in response payloads.

---

## 5. API Design

### GAP-API-001: Raw ResponseEntity Without Type Parameters
**Severity:** High | **Effort:** Medium

**Current State:** Most controller methods return raw `ResponseEntity` without generic type parameters:

```java
public ResponseEntity getBankAccount(...)  // Should be ResponseEntity<BankAccount>
```

Only user-service's `UserController` uses typed responses (`ResponseEntity<User>`).

**Impact:** No compile-time type safety. OpenAPI/Swagger documentation cannot infer response schemas. IDE auto-completion is degraded.

**Best Practice:** Always specify generic types: `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`, etc.

---

### GAP-API-002: No API Versioning Strategy
**Severity:** High | **Effort:** Medium

**Current State:** All endpoints use `/api/v1/` prefix, but there is no mechanism for introducing v2 endpoints, deprecating v1, or handling version negotiation.

**Impact:** Future breaking changes will require coordination across all consumers or result in breaking backwards compatibility.

**Best Practice:** Document the versioning strategy (URI path is acceptable). Add infrastructure for running multiple API versions simultaneously.

---

### GAP-API-003: Pagination Response Missing Metadata
**Severity:** Medium | **Effort:** Small

**Current State:** List endpoints accept Spring's `Pageable` parameter but return `List<T>` instead of a `Page<T>` wrapper:

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

**Impact:** Clients cannot determine total count, total pages, current page, or if more data exists.

**Best Practice:** Return a page wrapper (Spring's `Page<T>` or a custom `PageResponse<T>`) that includes `totalElements`, `totalPages`, `number`, `size`.

---

### GAP-API-004: No Filtering or Sorting Documentation
**Severity:** Medium | **Effort:** Small

**Current State:** While Spring's `Pageable` supports sort parameters, there is no documentation or explicit support for filtering endpoints (e.g., by status, date range, account number).

**Impact:** Clients must fetch all data and filter client-side, which is inefficient for large datasets.

**Best Practice:** Add query parameters for common filters. Document supported sort fields.

---

### GAP-API-005: Inconsistent URL Naming Conventions
**Severity:** Medium | **Effort:** Small

**Current State:** 
- Core Banking: `/api/v1/account/bank-account/{account_number}` (kebab-case with underscore param)
- Core Banking: `/api/v1/account/util-account/{account_name}` (abbreviated `util`)
- User Service: `/api/v1/bank-users/register` (noun-verb mix)
- Fund Transfer: `/api/v1/transfer` (resource name)
- Utility Payment: `/api/v1/utility-payment` (resource name)

**Impact:** Inconsistent API design makes the API harder to learn and use.

**Best Practice:** Standardize on kebab-case resource names, consistent pluralization, and RESTful verb conventions.

---

### GAP-API-006: OpenAPI/Swagger Dependency Mismatch
**Severity:** Low | **Effort:** Small

**Current State:** Services include `springdoc-openapi-starter-webflux-ui:2.1.0` even though only the API Gateway uses WebFlux. The business services use Spring MVC (servlet-based), so they should use `springdoc-openapi-starter-webmvc-ui`.

**Impact:** Swagger UI may not function correctly on servlet-based services. Dependency mismatch could cause runtime errors.

**Best Practice:** Use `springdoc-openapi-starter-webmvc-ui` for servlet services and `springdoc-openapi-starter-webflux-ui` only for the API Gateway.

---

## 6. Observability

### GAP-OBS-001: No Structured Logging
**Severity:** High | **Effort:** Medium

**Current State:** Services use SLF4J with default Logback configuration (no custom `logback-spring.xml`). Log output is plain text with default patterns. No JSON logging format.

**Impact:** Logs are difficult to parse in centralized logging systems (ELK, Datadog, CloudWatch). Cannot easily correlate logs with trace IDs.

**Best Practice:** Configure structured JSON logging with Logback or use Spring Boot's built-in structured logging support. Include trace ID, span ID, service name in every log line.

---

### GAP-OBS-002: Logging Sensitive Data
**Severity:** Medium | **Effort:** Small

**Current State:** Controllers log request objects using `toString()`:

```java
log.info("Creating user with {}", request.toString());  // User DTO contains password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

**Impact:** Passwords, account numbers, and financial amounts may be written to log files in plain text.

**Best Practice:** Exclude sensitive fields from `toString()` (use Lombok's `@ToString.Exclude`) or log only non-sensitive identifiers.

---

### GAP-OBS-003: No Custom Health Checks
**Severity:** Medium | **Effort:** Small

**Current State:** All services include `spring-boot-starter-actuator` but rely only on default health indicators. No custom health checks for critical dependencies (Keycloak connectivity, Config Server availability, Eureka registration).

**Impact:** Health endpoints report `UP` even when critical integrations are failing.

**Best Practice:** Add custom `HealthIndicator` beans for Keycloak, database connectivity, and downstream service availability.

---

### GAP-OBS-004: No Metrics Endpoints or Dashboards
**Severity:** Medium | **Effort:** Medium

**Current State:** Actuator is included but no Prometheus metrics exporter is configured (despite Prometheus being listed in the tech stack). No Grafana dashboards exist.

**Impact:** No runtime performance visibility. Cannot detect latency spikes, error rate increases, or resource exhaustion.

**Best Practice:** Add `micrometer-registry-prometheus` dependency, expose `/actuator/prometheus` endpoint, and create Grafana dashboards.

---

### GAP-OBS-005: Actuator Endpoints Publicly Accessible
**Severity:** Low | **Effort:** Small

**Current State:** The API Gateway security configuration permits all actuator endpoints without authentication:

```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

**Impact:** Information disclosure via actuator endpoints (env, beans, mappings, health details).

**Best Practice:** Restrict actuator access to internal networks or require authentication. Limit exposed endpoints via `management.endpoints.web.exposure.include`.

---

## 7. Resilience

### GAP-RES-001: No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

**Current State:** No Resilience4j, Hystrix, or Spring Cloud CircuitBreaker dependency in any service. All Feign calls to core-banking-service are unprotected.

**Impact:** If core-banking-service goes down, all upstream services (user, fund-transfer, utility-payment) will cascade-fail with connection timeouts. No graceful degradation.

**Best Practice:** Add `spring-cloud-starter-circuitbreaker-resilience4j` and configure circuit breakers on all Feign clients with appropriate thresholds.

---

### GAP-RES-002: No Retry Policies
**Severity:** High | **Effort:** Small

**Current State:** No Spring Retry or Resilience4j retry configuration. Transient failures (network blips, temporary DB unavailability) cause immediate failure.

**Impact:** Transient errors propagate to end users unnecessarily.

**Best Practice:** Add retry policies for idempotent operations (GET requests, read operations). Configure with exponential backoff.

---

### GAP-RES-003: No Timeout Configuration
**Severity:** High | **Effort:** Small

**Current State:** No explicit timeouts configured for:
- Feign client connections and read timeouts
- Database connection pool timeouts
- HTTP client timeouts

Default timeouts (often infinite or very long) apply.

**Impact:** A slow downstream service can tie up thread pools indefinitely, causing cascading resource exhaustion.

**Best Practice:** Configure explicit timeouts for all external calls:
- Feign: `connectTimeout: 5000`, `readTimeout: 10000`
- HikariCP: `connectionTimeout: 30000`, `maximumPoolSize: 10`

---

### GAP-RES-004: No Fallback Behavior
**Severity:** High | **Effort:** Medium

**Current State:** When inter-service calls fail, the exception propagates directly to the client. No fallback responses, cached data, or degraded functionality.

**Impact:** Any single service failure results in a complete failure for the end user, even when partial functionality could be maintained.

**Best Practice:** Implement fallback methods for Feign clients that return cached data, default responses, or meaningful error messages.

---

### GAP-RES-005: Fund Transfer Not Idempotent
**Severity:** Medium | **Effort:** Medium

**Current State:** Fund transfer operations do not use idempotency keys. If a client retries a failed request, the transfer may be executed twice.

**Impact:** Duplicate fund transfers could result in incorrect account balances.

**Best Practice:** Accept an idempotency key in the request header or body. Check for existing transactions with the same key before processing.

---

## Appendix: Balance Calculation Bug

**Location:** `core-banking-service` > `TransactionService.java` lines 63-64, 90-91, 99-100

**Bug:** Available balance is calculated by subtracting from `actualBalance` *after* `actualBalance` has already been decremented:

```java
// Line 63-64 (utilPayment)
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));  // BUG: double subtraction

// Line 90-91 (internalFundTransfer - debit)
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));  // BUG

// Line 99-100 (internalFundTransfer - credit)
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));  // BUG: double addition
```

**Impact:** After a $100 transfer from an account with $200:
- `actualBalance` correctly becomes $100
- `availableBalance` incorrectly becomes $0 (should be $100)

This is a **data integrity bug** that affects every transaction.

**Severity:** Critical | **Effort:** Small
