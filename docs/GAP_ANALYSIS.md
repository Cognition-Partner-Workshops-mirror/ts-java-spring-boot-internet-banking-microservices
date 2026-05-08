# Internet Banking Microservices — Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### GAP-01: No Shared Library — Duplicated Code Across Services

**Severity:** High | **Effort:** Medium

**Finding:** The following classes are copy-pasted across 3–4 services with identical or near-identical implementations:

| Class | Duplicated In |
|---|---|
| `BaseMapper<E, D>` | core-banking, fund-transfer, user-service, utility-payment |
| `AuditAware` | fund-transfer, user-service, utility-payment |
| `GlobalExceptionHandler` | core-banking, fund-transfer, user-service, utility-payment |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, user-service, utility-payment |
| `ErrorResponse` | core-banking, fund-transfer, user-service, utility-payment |
| `AppAuthUserFilter` | fund-transfer, user-service, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | fund-transfer, user-service, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact:** Bug fixes must be applied N times. Divergence between copies causes inconsistent behavior (e.g., the user-service has a `CustomFeignErrorDecoder` while fund-transfer and utility-payment do not).

**Recommendation:** Extract a `banking-common` shared Gradle module published to a local Maven repository or included as a composite build.

---

### GAP-02: No Multi-Module Gradle Root Project

**Severity:** Medium | **Effort:** Small

**Finding:** Each service has its own independent `build.gradle` with no root `settings.gradle` or `build.gradle`. This means:
- No way to build all services with a single command
- No shared dependency version catalog
- Spring Boot version (3.2.4), Spring Cloud version (2023.0.0), and plugin versions are hardcoded in each service independently

**Recommendation:** Create a root Gradle project with `settings.gradle.kts` that includes all services as subprojects and a `buildSrc` or version catalog for shared dependency versions.

---

### GAP-03: Inconsistent Package Structure Across Services

**Severity:** Low | **Effort:** Small

**Finding:** Package structures are similar but inconsistent:
- core-banking: `model.dto.request`, `model.dto.response`, `repository`
- user-service: `model.rest.response`, `model.repository`, `configuration.feign`, `configuration.keycloak`
- fund-transfer: `model.dto.request`, `model.dto.response`, `model.repository`, `service.rest.client`
- utility-payment: `model.rest.request`, `model.rest.response`, `service.rest`

The Feign client interface is in `service.rest.client` in fund-transfer but `service.rest` in user-service and utility-payment. Request/response DTOs are in `model.dto.*` in some services and `model.rest.*` in others.

**Recommendation:** Standardize on a single package convention (e.g., `model.dto`, `model.entity`, `repository`, `service`, `client`, `config`).

---

### GAP-04: Mapper Classes Instantiated Inline Instead of as Spring Beans

**Severity:** Low | **Effort:** Small

**Finding:** All mapper classes (e.g., `UserMapper`, `FundTransferMapper`, `BankAccountMapper`) are instantiated with `new` directly in service classes:
```java
private UserMapper userMapper = new UserMapper();
```
This bypasses Spring's dependency injection, making it harder to test and violating DI conventions.

**Recommendation:** Register mappers as `@Component` beans and inject them, or adopt MapStruct for compile-time mapping.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity:** Critical | **Effort:** Small

**Finding:** Every `GlobalExceptionHandler` in every service maps all exceptions — including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception` — to `ResponseEntity.badRequest()` (HTTP 400):

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:**
- 404 Not Found scenarios return 400
- 500 Internal Server Errors return 400
- Clients cannot distinguish between validation errors, not-found errors, and server failures
- The generic handler leaks exception stack traces to the client (`"Exception occur inside API " + e`)

**Recommendation:** Map exception types to appropriate HTTP status codes:
- `EntityNotFoundException` → 404
- `InsufficientFundsException` → 422 (Unprocessable Entity)
- `InvalidEmailException` / `UserAlreadyRegisteredException` → 409 (Conflict)
- `Exception` (catch-all) → 500 with sanitized message

---

### GAP-06: Exception Stack Traces Leaked to Clients

**Severity:** High | **Effort:** Small

**Finding:** The generic `Exception` handler in all services returns the full exception object as a string:
```java
.body("Exception occur inside API " + e);
```
This exposes internal implementation details (class names, SQL errors, file paths) to API consumers.

**Recommendation:** Return a generic error message for unhandled exceptions. Log the full stack trace server-side at ERROR level.

---

### GAP-07: No Feign Error Decoder in Fund-Transfer and Utility-Payment Services

**Severity:** High | **Effort:** Small

**Finding:** Only the `internet-banking-user-service` has a `CustomFeignErrorDecoder`. The `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service` have no error decoder. When core-banking returns an error, raw Feign exceptions (`FeignException`) propagate up and are caught by the generic handler, resulting in an unhelpful "Exception occur inside API" response with a 400 status.

**Recommendation:** Add `CustomFeignErrorDecoder` to fund-transfer and utility-payment services (or extract to shared library per GAP-01), mapping core-banking error responses to service-specific exceptions.

---

### GAP-08: Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

**Finding:** Two different error response formats exist:
1. **Structured:** `{ "code": "BANKING-CORE-SERVICE-1000", "message": "..." }` (for `SimpleBankingGlobalException`)
2. **Plain text:** `"Exception occur inside API <exception>"` (for all other exceptions)

Clients must handle both formats.

**Recommendation:** Standardize on a single envelope format for all error responses (e.g., `{ "error": { "code", "message", "timestamp", "path" } }`).

---

## 3. Testing

### GAP-09: Minimal Test Coverage — Only 1 of 6 Services Has Unit Tests

**Severity:** Critical | **Effort:** Large

**Finding:**

| Service | Test Files | Test Methods |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | ~17 tests |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` (empty context load) | 0 real tests |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` (empty context load) | 0 real tests |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` (empty context load) | 0 real tests |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` (empty context load) | 0 real tests |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` (empty context load) | 0 real tests |

5 of 6 services have only auto-generated Spring Boot test classes with a single `contextLoads()` test. The core-banking tests are pure unit tests with Mockito (no Spring context), which is good practice but covers only the service layer.

**Impact:** No tests for:
- Controller request/response mapping
- Feign client behavior and error handling
- User registration flow (Keycloak integration)
- Fund transfer and utility payment orchestration
- Gateway routing and security configuration

---

### GAP-10: No Integration Tests

**Severity:** High | **Effort:** Large

**Finding:** There are no integration tests using `@SpringBootTest`, `@WebMvcTest`, `@DataJpaTest`, or Testcontainers. The existing tests use pure Mockito mocks only. No test verifies actual database queries, JPA mappings, Flyway migrations, or HTTP endpoint behavior.

**Recommendation:** Add `@WebMvcTest` controller tests, `@DataJpaTest` repository tests, and `@SpringBootTest` with Testcontainers for MySQL integration tests.

---

### GAP-11: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

**Finding:** There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between the orchestrating services (user-service, fund-transfer, utility-payment) and core-banking-service. Feign client interfaces could drift from actual controller APIs without detection.

**Recommendation:** Implement Spring Cloud Contract or Pact to validate that Feign client expectations match core-banking controller implementations.

---

### GAP-12: Empty ApplicationTests Context Load Tests Will Fail

**Severity:** Medium | **Effort:** Small

**Finding:** The auto-generated `*ApplicationTests` classes attempt `@SpringBootTest` context loading but will fail at runtime because they require external dependencies (Eureka, Config Server, MySQL, Keycloak) that aren't available in a test environment. The core-banking test config (`src/test/resources/application.yml`) disables Flyway and uses H2, but other services lack test-specific configuration.

**Recommendation:** Add `src/test/resources/application.yml` to all services with embedded alternatives (H2, disabled Eureka/Config) or use `@SpringBootTest` profiles that bypass external dependencies.

---

## 4. Security

### GAP-13: No Input Validation on Any Request DTOs

**Severity:** Critical | **Effort:** Small

**Finding:** No request DTO uses Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size`, etc.) and no controller parameter uses `@Valid`:

```java
// FundTransferRequest — no validation
@Data
public class FundTransferRequest {
    private String fromAccount;   // could be null
    private String toAccount;     // could be null
    private BigDecimal amount;    // could be null or negative
}
```

```java
// Controller — no @Valid annotation
@PostMapping
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest request) { ... }
```

**Impact:** Null or negative amounts cause `NullPointerException` deep in service logic. Blank account numbers cause `EntityNotFoundException` with misleading error messages.

**Recommendation:** Add `spring-boot-starter-validation`, annotate DTOs with constraints, and add `@Valid` to all `@RequestBody` parameters.

---

### GAP-14: No Authentication on Downstream Services — JWT Validated Only at Gateway

**Severity:** Critical | **Effort:** Medium

**Finding:** JWT validation occurs only at the API Gateway. Downstream services (core-banking, user-service, fund-transfer, utility-payment) have no Spring Security configuration and accept any request. The Gateway forwards the principal name as an `X-Auth-Id` header, but:

1. If any service is accessed directly (bypassing the gateway), there is no authentication
2. The `X-Auth-Id` header can be trivially spoofed
3. `AppAuthUserFilter` reads the header but does not verify it came from a trusted source

**Impact:** Any client with network access to downstream service ports can make unauthenticated requests to all business APIs.

**Recommendation:** Add Spring Security with JWT validation to each downstream service, or implement a shared token/secret between Gateway and services to verify the header's authenticity.

---

### GAP-15: Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

**Finding:** Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|---|---|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose/docker-compose.yml` | PostgreSQL password: `password` |
| `docker-compose/mysql/Dockerfile` | MySQL root password (ENV) |
| `docker-compose/mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Recommendation:** Use Docker secrets, `.env` files (gitignored), or a secrets manager. Replace hardcoded passwords with environment variable references.

---

### GAP-16: Overly Broad Database Privileges

**Severity:** High | **Effort:** Small

**Finding:** The `privileges.sql` init script grants the application user `javatodev_development` broad privileges on ALL databases:
```sql
GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO 'javatodev_development'@'%';
```

This includes `DROP` and `ALTER` on every database, far beyond what the application needs.

**Recommendation:** Scope grants to specific schemas and restrict to only `INSERT`, `UPDATE`, `DELETE`, `SELECT` per schema. Remove `CREATE`, `ALTER`, `DROP` from production grants.

---

### GAP-17: Keycloak Singleton Not Thread-Safe

**Severity:** Medium | **Effort:** Small

**Finding:** `KeycloakProperties.getInstance()` uses a check-then-act pattern without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {          // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

Multiple threads could create multiple instances during startup.

**Recommendation:** Use a `@Bean` method in a `@Configuration` class (Spring handles singleton lifecycle) or add `synchronized`.

---

### GAP-18: CSRF Disabled at Gateway

**Severity:** Medium | **Effort:** Small

**Finding:** The Gateway's `SecurityConfiguration` explicitly disables CSRF:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While CSRF protection is often disabled for stateless REST APIs (JWT-based), this should be a documented, deliberate decision.

**Recommendation:** Document the rationale. If the API is consumed by browser-based SPAs with cookie-based sessions, CSRF protection may be needed.

---

### GAP-19: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

**Finding:** No dependency vulnerability scanning plugin (OWASP Dependency-Check, Snyk, Dependabot) is configured in any `build.gradle`. The project uses several dependencies with known CVE histories (e.g., Jackson, Spring Framework, Keycloak Admin Client).

**Recommendation:** Add the OWASP Dependency-Check Gradle plugin or enable GitHub Dependabot alerts.

---

## 5. API Design

### GAP-20: Pagination Returns Raw Lists Without Metadata

**Severity:** High | **Effort:** Small

**Finding:** All paginated endpoints (e.g., `GET /api/v1/bank-users`, `GET /api/v1/transfer`) return raw `List<T>` instead of a paginated response envelope. The `Page` object from Spring Data is converted to a list, discarding:
- Total element count
- Total page count
- Current page number
- Page size
- Has-next/has-previous indicators

```java
public List<User> readUsers(Pageable pageable) {
    Page<UserEntity> allUsersInDb = userRepository.findAll(pageable);
    return userMapper.convertToDtoList(allUsersInDb.getContent()); // metadata lost
}
```

**Recommendation:** Return a standard page envelope: `{ content: [...], totalElements, totalPages, page, size }`.

---

### GAP-21: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

**Finding:** All endpoints use `/api/v1/` prefix but there is no mechanism for version negotiation, deprecation headers, or running multiple versions simultaneously. Version is hardcoded in URL paths.

**Recommendation:** Document the versioning strategy. Consider header-based versioning (`Accept: application/vnd.banking.v1+json`) for flexibility, or continue with URL-based but add sunset headers for deprecated endpoints.

---

### GAP-22: Wrong OpenAPI Dependency — WebFlux UI on MVC Services

**Severity:** Medium | **Effort:** Small

**Finding:** All four business services (core-banking, fund-transfer, user-service, utility-payment) use:
```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

However, these services use Spring MVC (`spring-boot-starter-web`), not WebFlux. The correct dependency is:
```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
```

The WebFlux dependency may work partially due to classpath scanning but produces incorrect auto-configuration.

**Recommendation:** Replace `webflux-ui` with `webmvc-ui` in all MVC services.

---

### GAP-23: No Filtering or Search on List Endpoints

**Severity:** Low | **Effort:** Medium

**Finding:** List endpoints only support Spring Data's default pagination parameters (`page`, `size`, `sort`). There is no filtering by date range, status, account number, or amount. Clients must fetch all data and filter client-side.

**Recommendation:** Add query parameters for common filters (e.g., `?status=SUCCESS&fromDate=2024-01-01&accountNumber=123`).

---

### GAP-24: Non-RESTful URL Patterns

**Severity:** Low | **Effort:** Small

**Finding:** Several URL patterns deviate from REST conventions:
- `POST /api/v1/bank-users/register` — action verb in URL (should be `POST /api/v1/bank-users`)
- `PATCH /api/v1/bank-users/update/{id}` — action verb in URL (should be `PATCH /api/v1/bank-users/{id}`)
- `GET /api/v1/account/util-account/{account_name}` — abbreviated "util" inconsistent with "utility-payment" elsewhere

**Recommendation:** Use noun-based resource URLs. Map HTTP verbs to operations: `POST` = create, `PATCH` = update, `GET` = read.

---

### GAP-25: Raw ResponseEntity Without Type Parameters

**Severity:** Low | **Effort:** Small

**Finding:** Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...) { ... }
```
instead of:
```java
public ResponseEntity<BankAccount> getBankAccount(...) { ... }
```

This prevents OpenAPI from generating accurate response schemas.

**Recommendation:** Add type parameters to all `ResponseEntity` returns.

---

## 6. Observability

### GAP-26: No Structured Logging

**Severity:** Medium | **Effort:** Small

**Finding:** All services use default Spring Boot logging (Logback with pattern layout). Log messages are unstructured text:
```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

**Impact:** Log aggregation tools (ELK, CloudWatch, Datadog) cannot easily parse, search, or alert on specific fields.

**Recommendation:** Configure Logback to output JSON format (e.g., `logstash-logback-encoder`). Include trace ID, span ID, service name, and request context in every log line.

---

### GAP-27: Logging Sensitive Data

**Severity:** High | **Effort:** Small

**Finding:** Several log statements output full request DTOs including potentially sensitive data:
```java
log.info("Creating user with {}", request.toString());  // includes password field
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());  // includes account numbers and amounts
```

The `User` DTO's `toString()` (generated by Lombok `@Data`) includes the `password` field.

**Recommendation:** Exclude sensitive fields from `toString()` (use `@ToString.Exclude` on password fields). Mask account numbers in logs. Never log financial amounts in INFO level.

---

### GAP-28: No Custom Health Checks

**Severity:** Medium | **Effort:** Small

**Finding:** Services include `spring-boot-starter-actuator` but rely solely on default health indicators. There are no custom health checks for:
- Database connectivity (beyond JPA auto-configuration)
- Keycloak availability
- Core-banking service reachability (for dependent services)
- Config Server connectivity

**Recommendation:** Add custom `HealthIndicator` implementations for critical dependencies, especially for the Keycloak connection in user-service and Feign client targets.

---

### GAP-29: No Metrics Endpoints or Custom Metrics

**Severity:** Medium | **Effort:** Medium

**Finding:** While `micrometer-tracing-bridge-brave` is included for distributed tracing, there are no custom business metrics:
- No transaction count meters
- No transfer amount histograms
- No error rate counters
- No Feign call latency metrics

The default Actuator `/metrics` endpoint provides JVM and HTTP metrics, but no business-specific instrumentation.

**Recommendation:** Add Micrometer `Counter`, `Timer`, and `DistributionSummary` beans for fund transfers, utility payments, and user registrations. Expose Prometheus endpoint for scraping.

---

### GAP-30: Distributed Tracing Configuration Not Verified

**Severity:** Low | **Effort:** Small

**Finding:** All services include Zipkin tracing dependencies, but the trace sampling rate and Zipkin endpoint are configured externally in the Config Server's Git repository (not in this repo). Without verifying the remote configuration, it's unclear if tracing is active or if the sampling rate is appropriate for production.

**Recommendation:** Document expected tracing configuration. Add fallback defaults in local `application.yml` for development.

---

## 7. Resilience

### GAP-31: No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

**Finding:** No circuit breaker library (Resilience4j, Spring Cloud Circuit Breaker) is present in any `build.gradle`. If core-banking-service becomes slow or unavailable:
- All fund-transfer requests will hang waiting for Feign timeout
- All utility-payment requests will hang
- All user registration requests will hang
- Gateway will accumulate blocked threads → cascading failure across the entire system

**Recommendation:** Add `spring-cloud-starter-circuitbreaker-resilience4j` and configure circuit breakers on all Feign clients. Define fallback responses for degraded operation.

---

### GAP-32: No Retry Policies on Feign Clients

**Severity:** High | **Effort:** Small

**Finding:** No Feign retry configuration exists. Transient failures (network blips, temporary service unavailability) immediately fail without retry. The default Feign retryer is `Retryer.NEVER_RETRY`.

**Recommendation:** Configure `Retryer` beans with exponential backoff for idempotent GET operations. Do NOT retry non-idempotent POST operations without idempotency keys (see GAP-34).

---

### GAP-33: No Timeout Configuration

**Severity:** High | **Effort:** Small

**Finding:** No explicit timeout configuration exists for Feign clients, database connections, or HTTP clients. Default timeouts vary:
- Feign default: 10s connect, 60s read
- JDBC connection pool: varies by provider defaults

A slow core-banking response could block a fund-transfer thread for 60+ seconds.

**Recommendation:** Set explicit timeouts in configuration:
```yaml
spring.cloud.openfeign.client.config.default.connect-timeout: 5000
spring.cloud.openfeign.client.config.default.read-timeout: 10000
```

---

### GAP-34: No Idempotency Keys on Transaction Endpoints

**Severity:** Critical | **Effort:** Medium

**Finding:** Fund transfer (`POST /api/v1/transfer`) and utility payment (`POST /api/v1/utility-payment`) endpoints have no idempotency mechanism. If a client retries a request (due to timeout, network error, or user double-click):
- A duplicate fund transfer will be processed, debiting the account twice
- A duplicate utility payment will be processed

**Recommendation:** Require an `Idempotency-Key` header on POST endpoints. Store processed keys in a database table and return the original response for duplicate keys.

---

### GAP-35: No Fallback Behavior on Service Failure

**Severity:** High | **Effort:** Medium

**Finding:** When a Feign call to core-banking fails, the calling service (fund-transfer, utility-payment) saves an entity in `PENDING`/`PROCESSING` status but never retries, compensates, or marks it as `FAILED`. These orphaned records accumulate silently.

**Recommendation:** Implement:
1. A `FAILED` status with error details
2. Exception handling around Feign calls to update status on failure
3. A scheduled job to retry or alert on stuck `PENDING`/`PROCESSING` records

---

### GAP-36: Double-Deduction Bug in Balance Calculations

**Severity:** Critical | **Effort:** Small

**Finding:** In `TransactionService.internalFundTransfer()` (core-banking-service), the available balance is computed from the already-debited actual balance:

```java
// Line 90-91: Debit side
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^^^ actualBalance is already reduced, so this double-subtracts

// Line 99-100: Credit side — same bug, double-adds
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
```

The same bug exists in `utilPayment()` (lines 63-64).

**Example:** Transfer $100 from account with $1000 balance:
- Expected: actualBalance=$900, availableBalance=$900
- Actual: actualBalance=$900, availableBalance=$800 (double deduction)

**Recommendation:** Set `availableBalance` directly: `setAvailableBalance(getAvailableBalance().subtract(amount))`.

---

### GAP-37: No Database Transaction Isolation for Financial Operations

**Severity:** High | **Effort:** Medium

**Finding:** `TransactionService` uses `@Transactional` at the class level (default isolation = `READ_COMMITTED`), but fund transfers involve reading and writing to two different accounts. Without `SERIALIZABLE` or explicit pessimistic locking:
- Concurrent transfers from the same account could both pass the balance check
- Race condition: two transfers of $900 from a $1000 account could both succeed

**Recommendation:** Use pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) on account lookups during transfers, or set transaction isolation to `SERIALIZABLE` for financial operations.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | Code Organization | No shared library — duplicated code across services | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle root project | Medium | Small |
| GAP-03 | Code Organization | Inconsistent package structure across services | Low | Small |
| GAP-04 | Code Organization | Mapper classes instantiated inline instead of as Spring beans | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 Bad Request | Critical | Small |
| GAP-06 | Error Handling | Exception stack traces leaked to clients | High | Small |
| GAP-07 | Error Handling | No Feign error decoder in fund-transfer and utility-payment | High | Small |
| GAP-08 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-09 | Testing | Minimal test coverage — only 1 of 6 services has unit tests | Critical | Large |
| GAP-10 | Testing | No integration tests | High | Large |
| GAP-11 | Testing | No contract tests between services | Medium | Large |
| GAP-12 | Testing | Empty ApplicationTests will fail without test config | Medium | Small |
| GAP-13 | Security | No input validation on any request DTOs | Critical | Small |
| GAP-14 | Security | No authentication on downstream services | Critical | Medium |
| GAP-15 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-16 | Security | Overly broad database privileges | High | Small |
| GAP-17 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-18 | Security | CSRF disabled at gateway (undocumented) | Medium | Small |
| GAP-19 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-20 | API Design | Pagination returns raw lists without metadata | High | Small |
| GAP-21 | API Design | No API versioning strategy | Medium | Medium |
| GAP-22 | API Design | Wrong OpenAPI dependency (WebFlux on MVC services) | Medium | Small |
| GAP-23 | API Design | No filtering or search on list endpoints | Low | Medium |
| GAP-24 | API Design | Non-RESTful URL patterns | Low | Small |
| GAP-25 | API Design | Raw ResponseEntity without type parameters | Low | Small |
| GAP-26 | Observability | No structured logging | Medium | Small |
| GAP-27 | Observability | Logging sensitive data (passwords, account numbers) | High | Small |
| GAP-28 | Observability | No custom health checks | Medium | Small |
| GAP-29 | Observability | No metrics endpoints or custom metrics | Medium | Medium |
| GAP-30 | Observability | Distributed tracing configuration not verified | Low | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies on Feign clients | High | Small |
| GAP-33 | Resilience | No timeout configuration | High | Small |
| GAP-34 | Resilience | No idempotency keys on transaction endpoints | Critical | Medium |
| GAP-35 | Resilience | No fallback behavior on service failure | High | Medium |
| GAP-36 | Resilience | Double-deduction bug in balance calculations | Critical | Small |
| GAP-37 | Resilience | No database transaction isolation for financial operations | High | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 8 |
| High | 13 |
| Medium | 12 |
| Low | 4 |
| **Total** | **37** |
