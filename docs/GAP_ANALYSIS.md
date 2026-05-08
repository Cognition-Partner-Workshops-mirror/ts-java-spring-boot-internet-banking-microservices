# Internet Banking Microservices — Engineering Standards Gap Analysis

> **Generated:** 2026-05-08 | **Assessed against:** Industry best practices for Java/Spring Boot microservices

---

## Rating Legend

| Severity | Meaning |
|---|---|
| **Critical** | Security vulnerability, data corruption risk, or production outage potential |
| **High** | Significant quality/reliability issue that will cause problems at scale |
| **Medium** | Deviation from best practices that increases maintenance burden |
| **Low** | Minor improvement opportunity, cosmetic or stylistic |

| Effort | Meaning |
|---|---|
| **Small** | < 1 day, localized change, low risk |
| **Medium** | 1–3 days, touches multiple files or services |
| **Large** | 3+ days, architectural change, cross-cutting concern |

---

## 1. Code Organization

### GAP-01: No shared library — duplicated code across services
**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3–4 services with near-identical implementations:
- `BaseMapper<E, D>` (core-banking, fund-transfer, user, utility-payment)
- `AuditAware` (fund-transfer, user, utility-payment)
- `GlobalExceptionHandler` (all 4 MVC services)
- `ErrorResponse` (all 4 MVC services)
- `SimpleBankingGlobalException` (all 4 MVC services)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (fund-transfer, user, utility-payment)
- `CustomFeignClientConfiguration` (fund-transfer, utility-payment)

**Impact:** Bug fixes must be applied in 3–4 places. Divergence is already occurring (e.g., `ErrorResponse` uses `@Builder` in some services and a constructor in others).

### GAP-02: No multi-module Gradle root project
**Severity: Medium | Effort: Medium**

Each service has a completely independent `build.gradle` with no root `settings.gradle` or shared dependency version management. This means:
- Dependency versions can drift between services (e.g., MySQL connector version).
- No single command to build/test all services.
- No shared `buildSrc` or version catalog for consistency.

### GAP-03: Inconsistent package structure across services
**Severity: Low | Effort: Small**

- `core-banking-service` uses `model.mapper`, `model.dto`, `model.entity`, `repository`
- `fund-transfer-service` uses `model.mapper`, `model.dto`, `model.entity`, `model.repository`
- `utility-payment-service` uses `model.mapper`, `model.entity`, `repository`, `model.rest.request`, `model.rest.response`
- `user-service` uses `model.mapper`, `model.dto`, `model.entity`, `model.repository`, `model.rest.response`

The `repository` package is sometimes inside `model` and sometimes at the top level. DTO/request/response naming is inconsistent.

### GAP-04: Mapper classes instantiated with `new` instead of Spring injection
**Severity: Low | Effort: Small**

All services create mapper instances directly (`private UserMapper userMapper = new UserMapper()`) instead of using Spring `@Component` injection. This prevents mocking in tests and breaks the DI convention.

---

## 2. Error Handling

### GAP-05: All errors return HTTP 400 Bad Request
**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` across all 4 MVC services maps ALL exceptions (including `EntityNotFoundException`, `InsufficientFundsException`, and unhandled `Exception`) to `ResponseEntity.badRequest()` (HTTP 400). Correct mappings should be:
- `EntityNotFoundException` → 404 Not Found
- `InsufficientFundsException` → 422 Unprocessable Entity
- `UserAlreadyRegisteredException` → 409 Conflict
- Unexpected `Exception` → 500 Internal Server Error

### GAP-06: Catch-all exception handler leaks internal details
**Severity: High | Effort: Small**

The catch-all `@ExceptionHandler({Exception.class})` returns the raw exception object as a string: `"Exception occur inside API " + e`. This exposes stack traces, class names, and potentially sensitive internal state to API consumers.

### GAP-07: No Feign error decoder in fund-transfer and utility-payment services
**Severity: High | Effort: Small**

When core-banking-service returns an error, the Feign client propagates raw `FeignException` to the caller. There is no custom `ErrorDecoder` to translate upstream error responses into meaningful downstream exceptions. The user-service has a `CustomFeignErrorDecoder` class, but the other two Feign-consuming services do not.

### GAP-08: No compensation/rollback on Feign call failure
**Severity: Critical | Effort: Medium**

In both `FundTransferService` and `UtilityPaymentService`:
1. Entity is saved with status `PENDING`/`PROCESSING`.
2. Feign call is made to core-banking.
3. If the Feign call fails, the entity remains in `PENDING`/`PROCESSING` forever.

There is no try-catch around the Feign call, no compensation logic, no retry, and no scheduled job to clean up orphaned records.

### GAP-09: Inconsistent error response format
**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` handler returns `ErrorResponse { code, message }`.
- Catch-all `Exception` handler returns a plain string.
- Different services use different `ErrorResponse` construction patterns (builder vs. constructor).

---

## 3. Testing

### GAP-10: Minimal test coverage — only 1 of 6 services has unit tests
**Severity: High | Effort: Large**

| Service | Test Classes | Test Methods | Coverage |
|---|---|---|---|
| core-banking-service | 3 (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) | ~20 | Service layer only |
| internet-banking-api-gateway | 1 (empty `ApplicationTests`) | 0 | None |
| internet-banking-config-server | 1 (empty `ApplicationTests`) | 0 | None |
| internet-banking-fund-transfer-service | 1 (empty `ApplicationTests`) | 0 | None |
| internet-banking-service-registry | 1 (empty `ApplicationTests`) | 0 | None |
| internet-banking-user-service | 1 (empty `ApplicationTests`) | 0 | None |
| internet-banking-utility-payment-service | 1 (empty `ApplicationTests`) | 0 | None |

### GAP-11: No integration tests
**Severity: High | Effort: Large**

No `@SpringBootTest` integration tests exist. There are no tests that verify:
- Controller layer request/response serialization
- JPA repository queries against a test database
- Feign client behavior with WireMock or similar
- Spring Security filter chain behavior

### GAP-12: No contract tests between services
**Severity: Medium | Effort: Large**

With 3 services making Feign calls to core-banking-service, there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify API compatibility. A breaking change in core-banking-service would not be caught until runtime.

### GAP-13: No test coverage measurement
**Severity: Medium | Effort: Small**

No JaCoCo or similar coverage tool is configured in any `build.gradle`. There is no visibility into which code paths are tested.

---

## 4. Security

### GAP-14: No input validation on any request DTO
**Severity: Critical | Effort: Small**

None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`, `UserUpdateRequest`) have Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). No controllers use `@Valid`. This means:
- Null amounts can cause `NullPointerException`
- Negative transfer amounts can be processed
- Empty account numbers bypass validation

### GAP-15: No authentication on downstream services
**Severity: Critical | Effort: Medium**

JWT validation occurs only at the API Gateway. Downstream services (core-banking, fund-transfer, user, utility-payment) have no Spring Security configuration. They blindly trust the `X-Auth-Id` header injected by the gateway. If any service is accessed directly (bypassing the gateway), there is zero authentication.

### GAP-16: Hardcoded credentials in source control
**Severity: Critical | Effort: Small**

The following secrets are hardcoded in `docker-compose.yml` and `privileges.sql`:
- MySQL root password: `woVERANKliGharym`
- MySQL app user password: `oPItyPticIAt`
- Keycloak admin password: `password`
- Keycloak DB password: `password`
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-17: Overly broad database privileges
**Severity: High | Effort: Small**

The application user `javatodev_development` is granted `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*`. This gives the application user DDL permissions (CREATE, ALTER, DROP) on ALL databases. In production, the app user should only have DML permissions on its specific schema.

### GAP-18: No CSRF protection bypass justification
**Severity: Low | Effort: Small**

CSRF is explicitly disabled in the gateway's `SecurityConfiguration` (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is common for API-only services using JWT, there is no documentation or comment explaining the rationale.

### GAP-19: Keycloak singleton is not thread-safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check-locking-free lazy singleton pattern that is not thread-safe. Multiple threads could create multiple Keycloak instances during startup.

### GAP-20: No dependency vulnerability scanning
**Severity: High | Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool is configured. There is no automated detection of known CVEs in dependencies.

---

## 5. API Design

### GAP-21: Raw `ResponseEntity` without type parameters
**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generics (e.g., `public ResponseEntity getBankAccount(...)` instead of `public ResponseEntity<BankAccount> getBankAccount(...)`). This:
- Prevents compile-time type checking
- Makes OpenAPI spec generation incomplete (response type unknown)

Only `UserController` in user-service uses typed `ResponseEntity<User>`.

### GAP-22: Pagination responses lack metadata
**Severity: Medium | Effort: Small**

All paginated endpoints (`GET /api/v1/transfer`, `GET /api/v1/bank-users`, etc.) return `List<T>` instead of a page wrapper with metadata (total count, page number, page size, total pages). Clients cannot determine if more data exists.

### GAP-23: No API versioning strategy
**Severity: Low | Effort: Medium**

While endpoints use `/api/v1/` prefix, there is no documented versioning strategy, no header-based versioning support, and no plan for v2 coexistence.

### GAP-24: Wrong OpenAPI dependency for MVC services
**Severity: Medium | Effort: Small**

Four MVC services (`core-banking-service`, `fund-transfer-service`, `user-service`, `utility-payment-service`) use `springdoc-openapi-starter-webflux-ui:2.1.0` instead of `springdoc-openapi-starter-webmvc-ui`. The WebFlux variant may not correctly scan MVC controllers, leading to incomplete or missing Swagger UI.

### GAP-25: No rate limiting
**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configuration. Financial APIs are high-value targets for abuse and should implement request rate limiting per client/IP.

### GAP-26: No idempotency keys on POST endpoints
**Severity: Critical | Effort: Medium**

Fund-transfer and utility-payment POST endpoints can process duplicate transactions if the client retries a request (e.g., due to network timeout). There is no idempotency key mechanism to detect and reject duplicate submissions.

---

## 6. Observability

### GAP-27: Inconsistent logging — no structured logging
**Severity: Medium | Effort: Medium**

- Services use `@Slf4j` with `log.info()` but log messages are unstructured plain strings.
- No MDC (Mapped Diagnostic Context) population for traceId, userId, or requestId.
- No JSON log format configured for machine parsing.
- Some log messages leak potentially sensitive data (e.g., `request.toString()` may include passwords in user-service).

### GAP-28: No custom health check endpoints
**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` is included, no custom `HealthIndicator` beans are configured to check:
- Database connectivity
- Keycloak reachability
- Core-banking-service availability (from downstream services)

Only the default Spring Boot health endpoint exists.

### GAP-29: No metrics endpoints or custom business metrics
**Severity: Medium | Effort: Medium**

Despite including `micrometer-tracing-bridge-brave`, no custom Micrometer metrics are defined for business KPIs (e.g., transfer count, payment volume, registration rate, error rates by type).

### GAP-30: Zipkin tracing configuration not verified
**Severity: Low | Effort: Small**

Tracing dependencies are present, but the Zipkin endpoint URL and sampling rate are presumably configured in the external config repo. No local fallback configuration exists if the config server is unavailable.

---

## 7. Resilience

### GAP-31: No circuit breakers
**Severity: Critical | Effort: Medium**

No Resilience4j or Hystrix circuit breaker is configured on any Feign client. If core-banking-service becomes slow or unresponsive:
- Fund-transfer and utility-payment services will exhaust their thread pools.
- The gateway will accumulate pending requests.
- Cascading failure will bring down all services.

### GAP-32: No retry policies
**Severity: High | Effort: Small**

No Spring Retry or Resilience4j retry is configured on Feign clients. Transient network failures cause immediate hard failures.

### GAP-33: No timeout configuration
**Severity: High | Effort: Small**

No Feign client timeouts (connect timeout, read timeout) are explicitly configured. Default timeouts may be too long, causing thread pool exhaustion under load.

### GAP-34: No fallback behavior
**Severity: Medium | Effort: Medium**

No Feign fallback classes or factory implementations are defined. When upstream services fail, consumers receive raw exceptions instead of graceful degradation.

### GAP-35: No bulkhead isolation
**Severity: Medium | Effort: Medium**

All Feign calls share the default thread pool. A slow core-banking-service endpoint can starve all other requests in the same service.

### GAP-36: Double-deduction bug in balance calculations
**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` (line 90-91):
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The second line reads `getActualBalance()` AFTER it was already debited on the first line, causing `availableBalance` to be debited twice. The same bug exists in `utilPayment()` (lines 63-64). This is a **data corruption bug** that causes incorrect account balances.

### GAP-37: Transaction entity uses `@OneToOne` with `CascadeType.ALL` for account
**Severity: High | Effort: Small**

`TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)`. This means deleting a transaction would cascade-delete the bank account. The correct mapping should be `@ManyToOne` with no cascade (or `CascadeType.PERSIST` at most), since many transactions reference the same account.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | Code Organization | Duplicated code across services (no shared library) | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle root project | Medium | Medium |
| GAP-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-04 | Code Organization | Mappers instantiated with `new` instead of DI | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-06 | Error Handling | Catch-all handler leaks internal details | High | Small |
| GAP-07 | Error Handling | No Feign error decoder in 2 services | High | Small |
| GAP-08 | Error Handling | No compensation/rollback on Feign failure | Critical | Medium |
| GAP-09 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-10 | Testing | Only 1 of 6 services has unit tests | High | Large |
| GAP-11 | Testing | No integration tests | High | Large |
| GAP-12 | Testing | No contract tests between services | Medium | Large |
| GAP-13 | Testing | No test coverage measurement (JaCoCo) | Medium | Small |
| GAP-14 | Security | No input validation on any request DTO | Critical | Small |
| GAP-15 | Security | No authentication on downstream services | Critical | Medium |
| GAP-16 | Security | Hardcoded credentials in source control | Critical | Small |
| GAP-17 | Security | Overly broad database privileges | High | Small |
| GAP-18 | Security | No CSRF disable justification | Low | Small |
| GAP-19 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-20 | Security | No dependency vulnerability scanning | High | Small |
| GAP-21 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-22 | API Design | Pagination responses lack metadata | Medium | Small |
| GAP-23 | API Design | No API versioning strategy | Low | Medium |
| GAP-24 | API Design | Wrong OpenAPI dependency (webflux instead of webmvc) | Medium | Small |
| GAP-25 | API Design | No rate limiting | Medium | Medium |
| GAP-26 | API Design | No idempotency keys on POST endpoints | Critical | Medium |
| GAP-27 | Observability | No structured logging | Medium | Medium |
| GAP-28 | Observability | No custom health checks | Medium | Small |
| GAP-29 | Observability | No custom business metrics | Medium | Medium |
| GAP-30 | Observability | Zipkin tracing config not verified | Low | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies | High | Small |
| GAP-33 | Resilience | No timeout configuration | High | Small |
| GAP-34 | Resilience | No fallback behavior | Medium | Medium |
| GAP-35 | Resilience | No bulkhead isolation | Medium | Medium |
| GAP-36 | Resilience | Double-deduction bug in balance calculations | Critical | Small |
| GAP-37 | Resilience | Transaction entity uses wrong JPA mapping | High | Small |

### Gap Distribution

| Severity | Count |
|---|---|
| Critical | 8 |
| High | 11 |
| Medium | 14 |
| Low | 4 |
| **Total** | **37** |
