# Engineering Standards Gap Analysis

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Assessment Date:** 2026-05-07
> **Methodology:** Manual code review of all 6 microservices against industry best practices

---

## Severity Ratings

| Rating | Definition |
|--------|-----------|
| **Critical** | Security vulnerability, data corruption risk, or production outage potential |
| **High** | Significant quality or maintainability issue that should be addressed soon |
| **Medium** | Best-practice deviation that increases tech debt over time |
| **Low** | Polish item; nice-to-have improvement |

## Effort Ratings

| Rating | Definition |
|--------|-----------|
| **Small** | < 1 day; localized change |
| **Medium** | 1-3 days; touches multiple files or services |
| **Large** | 3+ days; architectural change or cross-cutting concern |

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Small

Each service has an independent `build.gradle` with duplicated plugin versions (`spring-boot:3.2.4`, `dependency-management:1.1.4`, `spring-cloud:2023.0.0`). There is no root `settings.gradle` or `build.gradle` for the monorepo.

**Current state:** 6 independent Gradle projects with copy-pasted dependency declarations.
**Expected state:** A root `settings.gradle` including all subprojects with a `subprojects {}` block for shared configuration.

---

### GAP-ORG-02: Duplicated Code Across Services
**Severity:** High | **Effort:** Medium

The following classes are copy-pasted identically (or near-identically) across 3-4 services:
- `BaseMapper` (core-banking, user-service, fund-transfer, utility-payment)
- `AuditAware` DTO (user-service, fund-transfer, utility-payment)
- `AuditConfig` / `AuditorAwareConfig` (user-service, fund-transfer, utility-payment)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (user-service, fund-transfer, utility-payment)
- `ErrorResponse` / `SimpleBankingGlobalException` / `GlobalExceptionHandler` (all 4 business services)
- `GlobalErrorCode` (core-banking, user-service)

**Current state:** ~15 classes duplicated across services.
**Expected state:** Shared library module (e.g., `banking-common`) published to a local Maven repository or included as a Gradle composite build.

---

### GAP-ORG-03: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

Package organization varies between services:
- Core Banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `controller`, `service`, `exception`
- User Service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `configuration.keycloak`, `configuration.feign`, `configuration.filter`, `configuration.audit`
- Fund Transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.dto.request`, `model.dto.response`, `service.rest.client`
- Utility Payment: `model.rest.request`, `model.rest.response`, `model.dto`, `model.entity`, `model.mapper`, `repository`

**Current state:** Repository vs. model.repository; rest.response vs. dto.response; service.rest vs. service.rest.client.
**Expected state:** Consistent package naming convention across all services.

---

### GAP-ORG-04: Mapper Instantiation Anti-Pattern
**Severity:** Low | **Effort:** Small

Mappers are instantiated inline rather than being Spring-managed beans:

```java
private UserMapper userMapper = new UserMapper();  // in UserService
private FundTransferMapper mapper = new FundTransferMapper();  // in FundTransferService
```

**Current state:** Manual `new` instantiation of mappers alongside `@RequiredArgsConstructor` injection.
**Expected state:** Mappers as `@Component` beans injected via constructor, or use MapStruct for compile-time mapping.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400 Bad Request
**Severity:** High | **Effort:** Small

Every `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` (HTTP 400) for all exceptions, including:
- `EntityNotFoundException` (should be 404)
- `InsufficientFundsException` (should be 422 Unprocessable Entity)
- `UserAlreadyRegisteredException` (should be 409 Conflict)
- Generic `Exception` (should be 500 Internal Server Error)

**Current state:** All errors return 400 regardless of type.
**Expected state:** Proper HTTP status codes mapped per exception type.

---

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details
**Severity:** Critical | **Effort:** Small

All four services have:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

This exposes full exception stack traces, class names, and potentially sensitive information (SQL queries, connection strings, internal paths) to API consumers.

**Current state:** `Exception.toString()` returned in response body.
**Expected state:** Generic error message ("An internal error occurred") with a correlation ID; full details logged server-side only.

---

### GAP-ERR-03: Inconsistent Error Response Format
**Severity:** Medium | **Effort:** Small

- Business exceptions return `ErrorResponse { code, message }` (structured JSON).
- Generic exceptions return a plain string: `"Exception occur inside API " + e`.
- Fund Transfer Service constructs `ErrorResponse` via constructor: `new ErrorResponse(e.getCode(), e.getMessage())`.
- Other services use the builder pattern: `ErrorResponse.builder().code(...).message(...).build()`.

**Current state:** Two different response shapes for errors depending on the exception type.
**Expected state:** Uniform error envelope (e.g., `{ error: { code, message, timestamp, path } }`) for all error responses.

---

### GAP-ERR-04: Missing Raw Type Parameterization on ResponseEntity
**Severity:** Low | **Effort:** Small

Most controller methods return raw `ResponseEntity` instead of `ResponseEntity<T>`:
```java
public ResponseEntity getBankAccount(...)  // raw type
```

**Current state:** Raw types suppress compiler type checking.
**Expected state:** `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage Outside Core Banking
**Severity:** High | **Effort:** Large

| Service | Tests |
|---------|-------|
| core-banking-service | 3 test classes, ~17 test methods (AccountServiceTest, TransactionServiceTest, UserServiceTest) |
| internet-banking-user-service | 1 empty context test (`InternetBankingUserServiceApplicationTests`) |
| internet-banking-fund-transfer-service | 1 empty context test |
| internet-banking-utility-payment-service | 1 empty context test |
| internet-banking-api-gateway | 1 empty context test |
| internet-banking-config-server | 1 empty context test |
| internet-banking-service-registry | 1 empty context test |

**Current state:** Only core-banking-service has meaningful unit tests. Other services have zero business logic tests.
**Expected state:** Unit tests for all service layers, controller tests with MockMvc, and repository tests.

---

### GAP-TEST-02: No Integration Tests
**Severity:** High | **Effort:** Large

No tests verify the interaction between services (e.g., Fund Transfer Service -> Core Banking Service Feign call).

**Current state:** No integration tests, no `@SpringBootTest` with `TestRestTemplate`/`WebTestClient`, no WireMock for Feign clients.
**Expected state:** Integration tests per service using WireMock for downstream dependencies; optionally, end-to-end tests with Testcontainers.

---

### GAP-TEST-03: No Contract Tests
**Severity:** Medium | **Effort:** Large

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to ensure API compatibility between services.

**Current state:** No contract testing framework.
**Expected state:** At minimum, contract tests for the Core Banking Service API since it is consumed by 3 other services.

---

### GAP-TEST-04: Context Load Tests Will Fail Without Infrastructure
**Severity:** Low | **Effort:** Small

The empty `@SpringBootTest` context tests in user-service, fund-transfer-service, and utility-payment-service will fail without a running MySQL, Eureka, and Config Server because they don't have proper test profiles with H2 or embedded alternatives.

**Current state:** Context tests require external infrastructure.
**Expected state:** Test profiles with embedded databases and disabled Eureka/Config Server for isolated testing.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code
**Severity:** Critical | **Effort:** Medium

Credentials are committed to the repository:

| File | Credential |
|------|-----------|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | Keycloak admin password: `password`, DB password: `password` |
| `docker-compose/mysql/Dockerfile` | MySQL root password (ENV) |
| `docker-compose/mysql/privileges.sql` | App user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Current state:** Secrets in version control.
**Expected state:** Credentials externalized via `.env` files (gitignored), Docker secrets, or a vault.

---

### GAP-SEC-02: No Input Validation
**Severity:** Critical | **Effort:** Medium

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or other Bean Validation annotations on any request DTOs:

```java
@Data
public class FundTransferRequest {
    private String fromAccount;   // no validation
    private String toAccount;     // no validation
    private BigDecimal amount;    // no validation - could be null, negative, or zero
}
```

**Current state:** All request fields are nullable and unconstrained. Negative or zero transfer amounts would be processed.
**Expected state:** Jakarta Bean Validation annotations on all request DTOs; `@Valid` on controller method parameters.

---

### GAP-SEC-03: No Authorization Beyond Authentication
**Severity:** High | **Effort:** Medium

The API Gateway authenticates JWT tokens but there is no role-based or attribute-based authorization:
- Any authenticated user can approve other users (`PATCH /update/{id}` with `status: APPROVED`).
- Any authenticated user can initiate transfers from any account.
- No ownership verification (user A can transfer from user B's account).

**Current state:** Authentication only; no authorization checks.
**Expected state:** Role-based access control (e.g., `ADMIN` for user approval), resource-level ownership verification for transfers/payments.

---

### GAP-SEC-04: CSRF Disabled Without Justification
**Severity:** Low | **Effort:** Small

`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)` is set in the Gateway's `SecurityConfiguration`. For a pure API (no browser forms), this is acceptable, but it should be documented as a deliberate decision.

**Current state:** CSRF disabled without comment.
**Expected state:** Comment explaining the rationale, or CSRF enabled if browser-based clients are expected.

---

### GAP-SEC-05: Keycloak Client Singleton is Not Thread-Safe
**Severity:** High | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Current state:** Race condition under concurrent requests; potential for multiple Keycloak client instances.
**Expected state:** Use `@Bean` to create a Spring-managed singleton, or use `synchronized` / double-checked locking.

---

### GAP-SEC-06: Actuator Endpoints Publicly Accessible
**Severity:** Medium | **Effort:** Small

The Gateway security configuration allows all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

**Current state:** `/actuator/env`, `/actuator/configprops`, `/actuator/beans`, etc. are publicly accessible and may expose sensitive configuration.
**Expected state:** Only `/actuator/health` and `/actuator/info` should be public; other endpoints should require authentication or be disabled.

---

### GAP-SEC-07: No Dependency Vulnerability Scanning
**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar tool is configured in the build pipeline.

**Current state:** No automated vulnerability scanning.
**Expected state:** OWASP Dependency Check Gradle plugin or GitHub Dependabot configured.

---

## 5. API Design

### GAP-API-01: No API Versioning Strategy
**Severity:** Medium | **Effort:** Medium

APIs use `/api/v1/` prefix but there is no documented versioning strategy, no content negotiation, and no plan for v2 migration.

**Current state:** Hard-coded `/api/v1/` in path.
**Expected state:** Documented API versioning strategy (URL path, header, or media type).

---

### GAP-API-02: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Small

List endpoints accept `Pageable` parameters but return raw `List<T>` without pagination metadata:
```java
return ResponseEntity.ok(userService.readUsers(pageable));
// Returns List<User> -- no totalElements, totalPages, currentPage, etc.
```

**Current state:** Clients cannot determine total results, current page, or if more pages exist.
**Expected state:** Return `Page<T>` or a wrapper DTO with `{ content, totalElements, totalPages, pageNumber, pageSize }`.

---

### GAP-API-03: No Filtering or Sorting Support
**Severity:** Low | **Effort:** Medium

List endpoints only support basic pagination; no filtering (e.g., by status, date range) or explicit sort parameters.

**Current state:** Only `page`, `size`, `sort` via Spring's `Pageable`.
**Expected state:** Query parameter-based filtering for key business attributes.

---

### GAP-API-04: Inconsistent Endpoint Naming
**Severity:** Low | **Effort:** Small

- Core Banking: `/api/v1/account/bank-account/{account_number}`, `/api/v1/account/util-account/{account_name}`
- User Service: `/api/v1/bank-users/register`, `/api/v1/bank-users/update/{id}`
- Fund Transfer: `/api/v1/transfer`
- Utility Payment: `/api/v1/utility-payment`

Mixing `bank-account`, `util-account`, `bank-users`; action verbs in URLs (`register`, `update`) instead of pure resource-based design.

**Current state:** Inconsistent naming conventions.
**Expected state:** Consistent RESTful resource naming (nouns, plural, no verbs in paths).

---

### GAP-API-05: Missing OpenAPI Specification Export
**Severity:** Low | **Effort:** Small

While `springdoc-openapi` is in the dependencies, there is no verified access to `/v3/api-docs` or `/swagger-ui.html`, and no aggregated API documentation across services.

**Current state:** Swagger UI dependency present but no aggregation or gateway-level doc.
**Expected state:** Gateway-level aggregated OpenAPI documentation or per-service verified Swagger UI access.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging Practices
**Severity:** Medium | **Effort:** Small

- Some controllers log request details (`log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())`), others do not.
- `fundTransferRequest.toString()` may log sensitive data (account numbers, amounts).
- No structured logging (JSON format) configured.
- No correlation ID logging from trace context.

**Current state:** Ad-hoc `log.info` calls; no structured format; potential PII exposure in logs.
**Expected state:** Structured JSON logging (Logback JSON encoder), trace/span ID in MDC, sensitive field masking.

---

### GAP-OBS-02: No Health Check Customization
**Severity:** Low | **Effort:** Small

Services include `spring-boot-starter-actuator` but no custom health indicators for critical dependencies (MySQL connectivity, Keycloak reachability, Eureka registration status).

**Current state:** Default health endpoint only.
**Expected state:** Custom health indicators for database, Keycloak, and downstream service dependencies.

---

### GAP-OBS-03: No Metrics/Prometheus Endpoint Configuration
**Severity:** Medium | **Effort:** Small

While actuator is included, there is no `micrometer-registry-prometheus` dependency for Prometheus scraping, despite Prometheus being listed in the technology stack.

**Current state:** Prometheus listed in README but not in dependencies.
**Expected state:** `micrometer-registry-prometheus` added; `/actuator/prometheus` endpoint enabled.

---

### GAP-OBS-04: Zipkin Configuration Not Verified
**Severity:** Low | **Effort:** Small

Tracing dependencies are included but no `management.tracing.*` or `management.zipkin.*` properties are visible in local config files. These may exist in the external Config Server repository but are not verifiable from this codebase alone.

**Current state:** Tracing likely configured via external Config Server; not auditable locally.
**Expected state:** Tracing configuration documented or included in local config with overrides.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** High | **Effort:** Medium

Feign clients call Core Banking Service without any circuit breaker. If the core service is down, all dependent services will block and eventually exhaust their thread pools.

**Current state:** No Resilience4j, Hystrix, or Spring Cloud Circuit Breaker dependency.
**Expected state:** Circuit breakers on all Feign clients with fallback behavior.

---

### GAP-RES-02: No Retry Policies
**Severity:** Medium | **Effort:** Small

No retry configuration for Feign clients. Transient network failures (e.g., during rolling deployments) will cause immediate failures.

**Current state:** No Spring Retry or Resilience4j Retry configuration.
**Expected state:** Configurable retry policies with exponential backoff for idempotent operations.

---

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

No explicit timeouts configured for Feign clients or database connections. Default timeouts (often infinite or very long) can cause cascading failures.

**Current state:** No `connectTimeout`, `readTimeout` on Feign clients; no connection pool timeout on datasources.
**Expected state:** Explicit timeouts on all outbound connections (Feign, database, Keycloak admin client).

---

### GAP-RES-04: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

When Core Banking Service is unavailable:
- Fund Transfer Service: Feign call fails, exception propagates, client gets HTTP 400.
- Utility Payment Service: Same behavior.
- User Service (registration): Feign call fails, user cannot register.

No graceful degradation or queuing of requests.

**Current state:** Hard failure on any downstream outage.
**Expected state:** Fallback responses or message-based queuing for eventual consistency.

---

### GAP-RES-05: Non-Atomic Transaction Processing
**Severity:** Critical | **Effort:** Large

The fund transfer flow spans two services without distributed transaction support:

1. Fund Transfer Service saves entity with `PENDING` status.
2. Calls Core Banking Service which debits/credits accounts.
3. Fund Transfer Service updates status to `SUCCESS`.

If step 3 fails (e.g., Fund Transfer Service crashes after Core Banking completes), the money is transferred but the Fund Transfer record remains `PENDING` -- there is no compensation/rollback mechanism.

**Current state:** No saga pattern, no outbox pattern, no idempotency keys.
**Expected state:** Saga pattern with compensation, or transactional outbox with guaranteed delivery.

---

### GAP-RES-06: Double-Subtraction Bug in Utility Payment
**Severity:** Critical | **Effort:** Small

In `TransactionService.utilPayment()`:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```

`availableBalance` is set to `actualBalance - amount` **after** `actualBalance` was already reduced, resulting in double subtraction. If a user pays 50 from a 100 balance:
- `actualBalance` becomes 50 (correct)
- `availableBalance` becomes 0 (incorrect; should be 50)

The same bug pattern exists in `internalFundTransfer()`.

**Current state:** Available balance is incorrectly calculated for every transaction.
**Expected state:** `availableBalance` should equal `actualBalance` after the debit, or be set to `actualBalance - amount` using the **original** balance.

---

### GAP-RES-07: Transaction-Account Relationship Modeled as @OneToOne
**Severity:** Medium | **Effort:** Small

`TransactionEntity` has `@OneToOne` with `BankAccountEntity`, but an account will have many transactions. This should be `@ManyToOne`.

```java
@OneToOne(cascade = CascadeType.ALL)  // incorrect
@JoinColumn(name = "account_id", referencedColumnName = "id")
private BankAccountEntity account;
```

Additionally, `CascadeType.ALL` on this relationship means deleting a transaction would delete the bank account.

**Current state:** Wrong cardinality and dangerous cascade.
**Expected state:** `@ManyToOne` with no cascade (or `CascadeType.PERSIST` at most).

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Organization | No multi-project Gradle build | Medium | Small |
| GAP-ORG-02 | Organization | Duplicated code across services | High | Medium |
| GAP-ORG-03 | Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Organization | Mapper instantiation anti-pattern | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic handler leaks internal details | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-04 | Error Handling | Raw ResponseEntity types | Low | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage outside core | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | Context load tests fail without infra | Low | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source | Critical | Medium |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | No authorization beyond authentication | High | Medium |
| GAP-SEC-04 | Security | CSRF disabled without justification | Low | Small |
| GAP-SEC-05 | Security | Keycloak singleton not thread-safe | High | Small |
| GAP-SEC-06 | Security | Actuator endpoints publicly accessible | Medium | Small |
| GAP-SEC-07 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-02 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-API-03 | API Design | No filtering or sorting support | Low | Medium |
| GAP-API-04 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-API-05 | API Design | Missing OpenAPI spec export | Low | Small |
| GAP-OBS-01 | Observability | Inconsistent logging practices | Medium | Small |
| GAP-OBS-02 | Observability | No health check customization | Low | Small |
| GAP-OBS-03 | Observability | No Prometheus endpoint | Medium | Small |
| GAP-OBS-04 | Observability | Zipkin config not verifiable locally | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | Medium | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | Non-atomic transaction processing | Critical | Large |
| GAP-RES-06 | Resilience | Double-subtraction balance bug | Critical | Small |
| GAP-RES-07 | Resilience | Wrong @OneToOne on Transaction-Account | Medium | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 8 |
| Medium | 13 |
| Low | 8 |
| **Total** | **34** |
