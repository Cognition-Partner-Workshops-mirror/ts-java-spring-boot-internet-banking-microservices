# Engineering Standards Gap Analysis

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Assessed:** 2026-05-07
> **Methodology:** Manual code review against industry best practices for Java/Spring Boot microservices

---

## Severity & Effort Definitions

| Severity | Meaning |
|----------|---------|
| **Critical** | Security vulnerability, data corruption risk, or production outage potential |
| **High** | Significant maintainability or reliability issue affecting multiple services |
| **Medium** | Deviation from best practices that increases technical debt |
| **Low** | Minor improvement opportunity or cosmetic issue |

| Effort | Meaning |
|--------|---------|
| **Small** | < 1 day; localized change in one or two files |
| **Medium** | 1-3 days; touches multiple files or services |
| **Large** | 3+ days; architectural change, new infrastructure, or cross-cutting concern |

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** High | **Effort:** Medium

Each service is a standalone Gradle project with its own `gradlew`, `gradle/wrapper/`, `build.gradle`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to coordinate builds.

**Impact:** Cannot build/test all services with a single command. No shared dependency version management. Duplicated Gradle wrapper across 7 directories.

**Current state:**
```
core-banking-service/build.gradle
internet-banking-user-service/build.gradle
internet-banking-fund-transfer-service/build.gradle
...  (each independent)
```

**Recommended:** Create a Gradle multi-project build with a root `settings.gradle` and shared `buildSrc` or version catalog.

---

### GAP-ORG-02: Duplicated Code Across Services
**Severity:** High | **Effort:** Large

The following classes are copy-pasted (with minor variations) across 3-4 services:

| Class | Services |
|-------|----------|
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `BaseMapper` | core-banking, utility-payment |
| `AuditAware` / `AuditConfig` / `AuditorAwareConfig` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` | fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment (different implementations) |
| `TransactionStatus` enum | fund-transfer, utility-payment (duplicated) |

**Impact:** Bug fixes must be applied in multiple places. Inconsistencies creep in (e.g., `ErrorResponse` uses `@Builder` in some services but a constructor in others).

**Recommended:** Extract a shared `banking-common` library module.

---

### GAP-ORG-03: Inconsistent Package Structure
**Severity:** Medium | **Effort:** Small

- User Service places repositories under `model.repository`; Utility Payment Service uses a top-level `repository` package.
- Fund Transfer Service Feign client is in `service.rest.client`; User Service uses `service.rest`; Utility Payment Service uses `service.rest`.
- `CustomFeignClientConfiguration` has different implementations: Fund Transfer sets logger level; Utility Payment extends `FeignClientConfiguration`.

---

### GAP-ORG-04: No Shared DTO / API Contract Module
**Severity:** Medium | **Effort:** Medium

Request/response DTOs are duplicated across services. For example, `FundTransferRequest` exists in both `core-banking-service` and `internet-banking-fund-transfer-service` with potentially divergent field sets.

**Impact:** Breaking changes in Core Banking API silently break callers at runtime rather than compile time.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400
**Severity:** Critical | **Effort:** Medium

Every `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` (HTTP 400) for **all** exceptions, including:
- `EntityNotFoundException` (should be 404)
- `InsufficientFundsException` (should be 422)
- Generic `Exception` (should be 500)

```java
// Current - all errors return 400
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:** Clients cannot distinguish between client errors, missing resources, and server failures. Violates HTTP semantics.

---

### GAP-ERR-02: Stack Trace Leaked in Generic Exception Handler
**Severity:** Critical | **Effort:** Small

The generic `Exception` handler concatenates the exception object into the response body: `"Exception occur inside API " + e`. In production, this leaks stack traces, internal class names, and potentially sensitive data to external clients.

---

### GAP-ERR-03: Missing Raw Type Parameterization on ResponseEntity
**Severity:** Medium | **Effort:** Small

Most controller methods and exception handlers use raw `ResponseEntity` instead of `ResponseEntity<?>` or typed variants. This suppresses compile-time type checking.

```java
// Current
public ResponseEntity getBankAccount(...) { ... }

// Recommended
public ResponseEntity<BankAccount> getBankAccount(...) { ... }
```

---

### GAP-ERR-04: No Feign Error Decoder
**Severity:** High | **Effort:** Medium

OpenFeign clients have no custom `ErrorDecoder`. When Core Banking returns an error (e.g., 400 for insufficient funds), the calling service receives a raw `FeignException` which is then caught by the generic handler and returned as a vague 400 with a stack trace.

**Impact:** Error context from downstream services is lost. Clients see unhelpful error messages.

---

### GAP-ERR-05: No Handling of Failed Downstream Calls in Fund Transfer / Utility Payment
**Severity:** High | **Effort:** Medium

Both `FundTransferService` and `UtilityPaymentService` save a local entity with `PENDING`/`PROCESSING` status, then call Core Banking. If the Feign call fails, the local entity is never updated to `FAILED` — it remains in `PENDING`/`PROCESSING` forever.

```java
// FundTransferService.java
entity.setStatus(TransactionStatus.PENDING);
FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);
// If this throws, status stays PENDING forever:
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
```

---

## 3. Testing

### GAP-TEST-01: Minimal Test Coverage
**Severity:** High | **Effort:** Large

| Service | Test Classes | Unit Tests | Integration Tests |
|---------|-------------|------------|-------------------|
| core-banking-service | 4 | ~16 | 0 |
| internet-banking-user-service | 1 (context load only) | 0 | 0 |
| internet-banking-fund-transfer-service | 0 | 0 | 0 |
| internet-banking-utility-payment-service | 1 (context load only) | 0 | 0 |
| internet-banking-api-gateway | 0 | 0 | 0 |
| internet-banking-service-registry | 1 (context load only) | 0 | 0 |
| internet-banking-config-server | 0 | 0 | 0 |

Only `core-banking-service` has meaningful unit tests. No integration tests, no controller/API tests, no contract tests exist anywhere.

---

### GAP-TEST-02: No Contract Tests Between Services
**Severity:** High | **Effort:** Large

Services communicate via Feign clients that call specific REST endpoints on Core Banking. There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to verify that API changes don't break callers.

---

### GAP-TEST-03: Context Load Tests Will Fail Without Infrastructure
**Severity:** Low | **Effort:** Small

`InternetBankingUserServiceApplicationTests` and similar classes annotate with `@SpringBootTest` but require MySQL, Keycloak, Eureka, and Config Server to be running. They will fail in CI without Docker or test containers.

---

### GAP-TEST-04: No Test Configuration for H2 / Testcontainers
**Severity:** Medium | **Effort:** Medium

H2 is included as a test dependency in most services, but there are no `application-test.yml` profiles to actually use it. Test resource files (`src/test/resources/application.yml`) are minimal or empty.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Docker Compose
**Severity:** Critical | **Effort:** Small

Database passwords and Keycloak admin credentials are hardcoded in `docker-compose.yml`:

```yaml
MYSQL_ROOT_PASSWORD: woVERANKliGharym
KEYCLOAK_ADMIN_PASSWORD: password
KC_DB_PASSWORD: password
```

Also, test credentials are in the README: `ib_admin@javatodev.com / 5V7huE3G86uB`.

**Impact:** Credentials committed to source control. Anyone with repo access has database root access.

---

### GAP-SEC-02: No Input Validation on API Requests
**Severity:** Critical | **Effort:** Medium

No `@Valid` / `@NotNull` / `@Min` / `@Size` annotations on any request DTO. No Bean Validation dependency (`spring-boot-starter-validation`) in any `build.gradle`.

```java
// Current - no validation
public ResponseEntity fundTransfer(@RequestBody FundTransferRequest fundTransferRequest) { ... }

// Missing: @Valid, and FundTransferRequest has no constraints
```

**Impact:** Null amounts, empty account numbers, and negative transfer values are accepted without validation. The `BigDecimal.compareTo()` in `validateBalance()` would NPE on null amount.

---

### GAP-SEC-03: CSRF Disabled Without Documentation
**Severity:** Medium | **Effort:** Small

The API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is common for stateless JWT APIs but should be documented. If browser-based clients are added, this becomes a vulnerability.

---

### GAP-SEC-04: Keycloak Singleton Not Thread-Safe
**Severity:** High | **Effort:** Small

`KeycloakProperties.getInstance()` uses a classic double-check-locking anti-pattern without `synchronized` or `volatile`:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Multiple Keycloak clients could be created under concurrent requests, or a partially constructed object could be returned.

---

### GAP-SEC-05: No Rate Limiting on Public Endpoints
**Severity:** Medium | **Effort:** Medium

The registration endpoint (`/user/api/v1/bank-users/register`) is open to unauthenticated traffic with no rate limiting. Susceptible to brute-force or enumeration attacks.

---

### GAP-SEC-06: No Dependency Vulnerability Scanning
**Severity:** Medium | **Effort:** Small

No dependency vulnerability scanning tool (OWASP Dependency Check, Snyk, Dependabot) is configured. Several dependencies may have known CVEs (e.g., older `springdoc-openapi` version `2.1.0`).

---

### GAP-SEC-07: Downstream Services Not Authenticated
**Severity:** High | **Effort:** Medium

Core Banking Service exposes its REST API without any authentication. The API Gateway enforces JWT, but if Core Banking is accessed directly (bypassing the gateway), there is no auth check. Internal service-to-service calls via Feign also carry no credentials.

---

## 5. API Design

### GAP-API-01: Inconsistent REST Conventions
**Severity:** Medium | **Effort:** Medium

- Registration is `POST /register` (verb in URL, not RESTful)
- Update is `PATCH /update/{id}` (verb in URL)
- Fund transfer uses `POST /api/v1/transfer` (resource-oriented, good)
- Core Banking uses `POST /api/v1/transaction/fund-transfer` and `POST /api/v1/transaction/util-payment` (verb-based sub-resources)
- Path parameters use underscores (`{account_number}`) vs no convention

**Recommended:** Follow resource-oriented naming: `POST /api/v1/bank-users` for create, `PATCH /api/v1/bank-users/{id}` for update.

---

### GAP-API-02: No API Versioning Strategy Beyond v1
**Severity:** Low | **Effort:** Small

All endpoints use `/api/v1/`. No breaking-change or versioning strategy is documented. When v2 is needed, there's no pattern to follow.

---

### GAP-API-03: Inconsistent Pagination Response Format
**Severity:** Medium | **Effort:** Small

Paginated endpoints return `List<T>` instead of a standard page wrapper. Clients have no way to know total pages, total elements, or current page.

```java
// Current - returns plain list
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

**Recommended:** Return Spring's `Page<T>` or a custom wrapper with `totalElements`, `totalPages`, `currentPage`.

---

### GAP-API-04: No Filtering or Sorting Parameters Documented
**Severity:** Low | **Effort:** Small

While Spring's `Pageable` auto-binds `sort` parameter, no documentation or OpenAPI annotations describe available sort fields or filter criteria.

---

### GAP-API-05: Wrong OpenAPI Dependency
**Severity:** Medium | **Effort:** Small

All servlet-based services (Core Banking, User, Fund Transfer, Utility Payment) include `springdoc-openapi-starter-webflux-ui:2.1.0` instead of `springdoc-openapi-starter-webmvc-ui`. The webflux variant is for reactive stacks. This may cause Swagger UI to not render correctly.

---

### GAP-API-06: No Standard Error Response Envelope
**Severity:** Medium | **Effort:** Medium

The generic exception handler returns a plain string (`"Exception occur inside API " + e`) while business exceptions return `ErrorResponse{code, message}`. There is no consistent response envelope (e.g., with timestamp, path, error code).

---

## 6. Observability

### GAP-OBS-01: Actuator Endpoints Not Configured
**Severity:** Medium | **Effort:** Small

While `spring-boot-starter-actuator` is included in all services, there is no explicit configuration of which endpoints are exposed. Defaults in Spring Boot 3.x expose only `/actuator/health`. Metrics, prometheus, loggers, and other endpoints are likely not exposed.

---

### GAP-OBS-02: No Structured Logging
**Severity:** Medium | **Effort:** Medium

Services use default Logback with plain-text format. No JSON logging format is configured for production, making log aggregation (ELK, Loki, CloudWatch) difficult.

---

### GAP-OBS-03: Sensitive Data in Logs
**Severity:** High | **Effort:** Small

Controllers log full request objects using `toString()`:

```java
log.info("Creating user with {}", request.toString());
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

If DTOs contain passwords, account numbers, or PII, these are written to logs in plaintext. The `User` DTO includes `password` and `email` fields.

---

### GAP-OBS-04: No Health Check for External Dependencies
**Severity:** Medium | **Effort:** Small

Actuator health checks don't verify connectivity to Keycloak, RabbitMQ (when implemented), or inter-service health. Only default indicators (DB, disk) are active.

---

### GAP-OBS-05: No Prometheus Metrics Endpoint
**Severity:** Medium | **Effort:** Small

README mentions Prometheus but no `micrometer-registry-prometheus` dependency is present in any `build.gradle`. Metrics cannot be scraped.

---

### GAP-OBS-06: Zipkin Tracing Configuration Not Visible
**Severity:** Low | **Effort:** Small

Tracing libraries are included but the sampling rate, Zipkin URL, and propagation format are presumably in the external Config Server repo. No local fallback or documentation of trace configuration exists in this repository.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** High | **Effort:** Medium

All inter-service calls are synchronous via Feign with no circuit breaker (Resilience4j, Hystrix). If Core Banking goes down, all upstream services will block and eventually fail with connection timeouts, cascading the failure.

---

### GAP-RES-02: No Retry Policies
**Severity:** High | **Effort:** Small

No Feign or Spring Retry configuration exists. Transient network failures (DNS blips, connection resets) cause immediate failure rather than retrying.

---

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

No explicit timeout configuration for Feign clients, RestTemplate, or HTTP connections. Default timeouts (often infinite or very long) mean a hung downstream service will block the calling thread indefinitely.

---

### GAP-RES-04: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

When downstream services fail, there is no fallback logic. For read operations (e.g., listing transfers), a cached or empty response could be returned instead of an error.

---

### GAP-RES-05: Non-Atomic Balance Updates (Data Integrity Risk)
**Severity:** Critical | **Effort:** Medium

In `TransactionService.internalFundTransfer()`, the debit and credit operations are two separate `bankAccountRepository.save()` calls. While `@Transactional` is present, the method uses `@Transactional` from `jakarta.transaction` (JTA) rather than `org.springframework.transaction.annotation.Transactional`. Additionally, the balance calculation has a bug:

```java
// Line 90-91: availableBalance is set incorrectly (double subtraction)
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^^^ uses already-subtracted actualBalance, subtracting amount AGAIN
```

The same double-subtraction bug exists in `utilPayment()` at lines 63-64.

**Impact:** Available balance is always `amount` less than actual balance after each transaction. Money effectively "disappears" from the available balance.

---

### GAP-RES-06: No Idempotency for Write Operations
**Severity:** High | **Effort:** Medium

`POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency keys. Network retries or client timeouts can cause duplicate transactions.

---

### GAP-RES-07: No Database Connection Pooling Configuration
**Severity:** Medium | **Effort:** Small

No HikariCP pool configuration (max pool size, connection timeout, idle timeout) is visible. Defaults may be insufficient for production load.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | High | Medium |
| GAP-ORG-02 | Code Organization | Duplicated code across services | High | Large |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Medium | Small |
| GAP-ORG-04 | Code Organization | No shared DTO/API contract module | Medium | Medium |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | Critical | Medium |
| GAP-ERR-02 | Error Handling | Stack trace leaked in error responses | Critical | Small |
| GAP-ERR-03 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-ERR-04 | Error Handling | No Feign error decoder | High | Medium |
| GAP-ERR-05 | Error Handling | Failed downstream calls leave stale status | High | Medium |
| GAP-TEST-01 | Testing | Minimal test coverage | High | Large |
| GAP-TEST-02 | Testing | No contract tests | High | Large |
| GAP-TEST-03 | Testing | Context load tests require infrastructure | Low | Small |
| GAP-TEST-04 | Testing | No test configuration for H2 | Medium | Medium |
| GAP-SEC-01 | Security | Hardcoded credentials in Docker Compose | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | High | Small |
| GAP-SEC-05 | Security | No rate limiting on public endpoints | Medium | Medium |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-07 | Security | Downstream services not authenticated | High | Medium |
| GAP-API-01 | API Design | Inconsistent REST conventions | Medium | Medium |
| GAP-API-02 | API Design | No versioning strategy | Low | Small |
| GAP-API-03 | API Design | Inconsistent pagination format | Medium | Small |
| GAP-API-04 | API Design | No filtering/sorting documentation | Low | Small |
| GAP-API-05 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-API-06 | API Design | No standard error response envelope | Medium | Medium |
| GAP-OBS-01 | Observability | Actuator endpoints not configured | Medium | Small |
| GAP-OBS-02 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-03 | Observability | Sensitive data in logs | High | Small |
| GAP-OBS-04 | Observability | No health checks for external deps | Medium | Small |
| GAP-OBS-05 | Observability | No Prometheus metrics endpoint | Medium | Small |
| GAP-OBS-06 | Observability | Zipkin config not documented locally | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | Non-atomic balance updates / double-subtraction bug | Critical | Medium |
| GAP-RES-06 | Resilience | No idempotency for writes | High | Medium |
| GAP-RES-07 | Resilience | No DB connection pool configuration | Medium | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 15 |
| Medium | 15 |
| Low | 4 |
| **Total** | **39** |
