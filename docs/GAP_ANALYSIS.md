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

### 1.1 Duplicated Code Across Services

**Severity: High | Effort: Medium**

Multiple classes are copy-pasted across services with minor variations rather than extracted into a shared library:

- `SimpleBankingGlobalException` — duplicated in core-banking, fund-transfer, user-service, utility-payment (each with slightly different constructors).
- `ErrorResponse` — duplicated across 4 services (some use `@Builder`, others use a constructor).
- `GlobalExceptionHandler` — near-identical in all 4 services.
- `AuditAware` (MappedSuperclass) — duplicated in fund-transfer, user-service, utility-payment.
- `BaseMapper` — duplicated in core-banking, fund-transfer, user-service, utility-payment.
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` — duplicated in fund-transfer, user-service, utility-payment.
- `AuditConfig`, `AuditorAwareConfig` — duplicated in fund-transfer, user-service, utility-payment.

The blueprint mentions a `banking-common` shared library, but the current codebase still has pervasive duplication.

### 1.2 Inconsistent Package/Class Naming

**Severity: Low | Effort: Small**

- DTOs use different patterns: some in `model.dto`, some in `model.rest.request`, `model.rest.response`, `model.dto.request`, `model.dto.response`.
- Feign clients are named differently: `BankingCoreFeignClient` (fund-transfer) vs `BankingCoreRestClient` (user-service, utility-payment).
- Repository packages differ: `model.repository` (fund-transfer, user-service) vs `repository` (core-banking, utility-payment).

### 1.3 Mapper Instantiation Anti-Pattern

**Severity: Medium | Effort: Small**

Mappers are instantiated inline (`private UserMapper userMapper = new UserMapper()`) rather than injected as Spring beans. This defeats testability and is inconsistent with the DI approach used everywhere else.

---

## 2. Error Handling

### 2.1 All Exceptions Return 400 Bad Request

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` for **all** exceptions, including:
- `EntityNotFoundException` → should return **404 Not Found**
- `InsufficientFundsException` → should return **422 Unprocessable Entity** or **409 Conflict**
- Generic `Exception` fallback → should return **500 Internal Server Error**

### 2.2 Generic Exception Handler Leaks Internal Details

**Severity: Critical | Effort: Small**

The catch-all handler returns: `"Exception occur inside API " + e` — this exposes stack traces, class names, and potentially sensitive data to the client.

### 2.3 Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- The `SimpleBankingGlobalException` handler returns a structured `ErrorResponse { code, message }`.
- The generic `Exception` handler returns a raw string — not the same shape.
- Clients cannot reliably parse error responses.

### 2.4 No Validation Error Handling

**Severity: High | Effort: Small**

No `@Valid` annotations on request bodies and no `MethodArgumentNotValidException` handler. Invalid requests pass through without validation.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Classes | Test Type | Coverage |
|---|---|---|---|
| core-banking-service | 4 (AccountServiceTest, TransactionServiceTest, UserServiceTest, ApplicationTests) | Unit (Mockito) | Service layer only |
| fund-transfer-service | 1 (ApplicationTests stub) | None meaningful | ~0% |
| user-service | 1 (ApplicationTests stub) | None meaningful | ~0% |
| utility-payment-service | 1 (ApplicationTests stub) | None meaningful | ~0% |
| api-gateway | 1 (ApplicationTests stub) | None meaningful | ~0% |
| config-server | 1 (ApplicationTests stub) | None meaningful | ~0% |
| service-registry | 1 (ApplicationTests stub) | None meaningful | ~0% |

Only the core-banking-service has real unit tests. The other 6 services have only the auto-generated Spring Boot application context test stubs.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist that verify:
- Database interactions with real DB (H2 or Testcontainers)
- Feign client contracts between services
- API gateway routing and security rules
- End-to-end flows

### 3.3 No Contract Tests

**Severity: Medium | Effort: Medium**

No Spring Cloud Contract or Pact tests to verify that Feign client interfaces match the provider's actual API. Breaking changes in core-banking-service would not be caught until runtime.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Credentials are committed directly in source-controlled files:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`, `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password`
- `privileges.sql`: `IDENTIFIED BY 'oPItyPticIAt'`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

None of the request DTOs use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). This means:
- Fund transfers can be submitted with null or negative amounts.
- User registration accepts blank emails or identifications.
- No protection against malformed or malicious input.

### 4.3 No Authorization Beyond Authentication

**Severity: High | Effort: Medium**

- The API gateway checks for a valid JWT but does not enforce roles or permissions.
- Any authenticated user can:
  - Approve/disable other users
  - Transfer funds from any account
  - View all users and all transactions
- No method-level security (`@PreAuthorize`) exists anywhere.

### 4.4 CSRF Disabled Globally

**Severity: Low | Effort: Small**

CSRF is disabled in the gateway's `SecurityConfiguration`. While acceptable for a stateless REST API using bearer tokens, this should be explicitly documented and validated against the threat model.

### 4.5 Keycloak Client Secret in Config Server

**Severity: Medium | Effort: Small**

Keycloak `client-secret` is stored in the externalized Git config repo. The Config Server does not enable encryption for sensitive properties.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar plugin is configured in any `build.gradle`.

---

## 5. API Design

### 5.1 No OpenAPI Documentation Accessible

**Severity: Medium | Effort: Small**

Services include `springdoc-openapi-starter-webflux-ui` but:
- This is the **WebFlux** variant, yet all business services use Spring MVC (Web). The incorrect dependency means Swagger UI likely doesn't work.
- The API gateway does not aggregate OpenAPI specs from downstream services.

### 5.2 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

Endpoints use `/api/v1/` prefix, but there is no strategy for:
- Running multiple versions simultaneously
- Version negotiation via headers
- Deprecation policy

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Paginated endpoints (`GET /api/v1/transfer`, `GET /api/v1/bank-users`, etc.) accept `Pageable` but return raw `List<T>` instead of a `Page<T>` response with:
- Total elements / total pages
- Current page number and size
- Navigation links (HATEOAS)

### 5.4 Inconsistent URL Patterns

**Severity: Low | Effort: Small**

- Core banking uses underscores: `/bank-account/{account_number}`, `/util-account/{account_name}`
- Path variable naming mixes snake_case (`account_number`) with the Java convention

### 5.5 Missing Response Type Parameterization

**Severity: Low | Effort: Small**

Controllers return raw `ResponseEntity` (no generic type parameter) — e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`. This loses compile-time type safety and degrades OpenAPI spec generation.

---

## 6. Observability

### 6.1 Unstructured Logging

**Severity: Medium | Effort: Medium**

- All logging uses unstructured `log.info("Reading account by ID {}", ...)` style.
- No JSON log format configured for production.
- No MDC enrichment with correlation IDs, user context, or service name.
- Sensitive data (user emails, account numbers) is logged without redaction.

### 6.2 No Custom Health Checks

**Severity: Medium | Effort: Small**

Spring Boot Actuator provides default health checks, but no custom health indicators exist for:
- Database connectivity verification
- Keycloak reachability
- Config Server availability
- Downstream service health (Feign client targets)

### 6.3 No Metrics Endpoints Configuration

**Severity: Medium | Effort: Small**

Actuator is included in all services but there is no explicit metrics export configuration. No custom business metrics (e.g., transfer counts, payment amounts, error rates) are recorded.

### 6.4 Distributed Tracing Not Verified

**Severity: Low | Effort: Small**

While tracing libraries are included in all services, there is no configuration ensuring:
- Trace context propagation across Feign calls
- Appropriate sampling rate for production
- Span naming conventions

### 6.5 Logging Sensitive Data

**Severity: High | Effort: Small**

Several controllers and services log full request objects via `.toString()`:
- `FundTransferController`: logs the full fund transfer request (account numbers, amounts)
- `UserController` (user-service): logs user creation requests (potentially includes passwords)
- This violates PII/PCI data handling requirements.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All Feign clients call core-banking-service synchronously with no circuit breaker (Resilience4j, Hystrix, etc.). If core-banking-service goes down:
- Fund transfer and utility payment services will hang or throw unhandled exceptions.
- No fallback behavior defined.
- Cascading failures will propagate to the API gateway and clients.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration on Feign clients. Transient network failures or temporary service unavailability will immediately fail requests.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

Feign clients use default timeouts (no explicit `connectTimeout` or `readTimeout`). Default timeouts are often too generous (60+ seconds), leading to resource exhaustion under failure conditions.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)` defined. There is no degraded experience — failures result in raw error responses.

### 7.5 No Idempotency for Financial Operations

**Severity: Critical | Effort: Medium**

Fund transfer and utility payment operations are **not idempotent**:
- No idempotency key in request headers.
- If a Feign call to core-banking succeeds but the response is lost (network timeout), the client will retry and execute the transaction again.
- The `transactionId` is generated server-side in the core-banking-service, so the caller cannot detect duplicates.
- This can lead to **double-charging** customers.

### 7.6 Transaction Safety Issues

**Severity: Critical | Effort: Medium**

In `TransactionService.internalFundTransfer()`:
- Debit and credit are performed in separate repository save calls within a single `@Transactional` method. While the `@Transactional` annotation should protect this, the method is public and can be called independently.
- `availableBalance` is set to `actualBalance.subtract(amount)` after `actualBalance` is already updated, resulting in **incorrect available balance** (double subtraction for debit, double addition for credit).
- No row-level locking (`@Lock(PESSIMISTIC_WRITE)`) — concurrent transfers on the same account can result in lost updates.

---

## Summary Table

| # | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| 1.1 | Code Organization | Duplicated code across services | High | Medium |
| 1.2 | Code Organization | Inconsistent package/class naming | Low | Small |
| 1.3 | Code Organization | Mapper instantiation anti-pattern | Medium | Small |
| 2.1 | Error Handling | All exceptions return 400 | High | Small |
| 2.2 | Error Handling | Generic handler leaks internal details | Critical | Small |
| 2.3 | Error Handling | Inconsistent error response format | Medium | Small |
| 2.4 | Error Handling | No validation error handling | High | Small |
| 3.1 | Testing | Minimal test coverage (only core-banking has real tests) | Critical | Large |
| 3.2 | Testing | No integration tests | High | Large |
| 3.3 | Testing | No contract tests | Medium | Medium |
| 4.1 | Security | Hardcoded credentials in source | Critical | Small |
| 4.2 | Security | No input validation | Critical | Medium |
| 4.3 | Security | No authorization beyond authentication | High | Medium |
| 4.4 | Security | CSRF disabled without documentation | Low | Small |
| 4.5 | Security | Keycloak secret in plain-text config | Medium | Small |
| 4.6 | Security | No dependency vulnerability scanning | Medium | Small |
| 5.1 | API Design | Wrong OpenAPI dependency (WebFlux vs Web) | Medium | Small |
| 5.2 | API Design | No API versioning strategy | Medium | Medium |
| 5.3 | API Design | No pagination metadata in responses | Medium | Small |
| 5.4 | API Design | Inconsistent URL patterns | Low | Small |
| 5.5 | API Design | Missing ResponseEntity type parameters | Low | Small |
| 6.1 | Observability | Unstructured logging | Medium | Medium |
| 6.2 | Observability | No custom health checks | Medium | Small |
| 6.3 | Observability | No metrics export configuration | Medium | Small |
| 6.4 | Observability | Distributed tracing not verified | Low | Small |
| 6.5 | Observability | Logging sensitive data | High | Small |
| 7.1 | Resilience | No circuit breakers | Critical | Medium |
| 7.2 | Resilience | No retry policies | High | Small |
| 7.3 | Resilience | No timeout configuration | High | Small |
| 7.4 | Resilience | No fallback behavior | Medium | Medium |
| 7.5 | Resilience | No idempotency for financial operations | Critical | Medium |
| 7.6 | Resilience | Transaction safety issues (balance bug, no locking) | Critical | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 8 |
| High | 8 |
| Medium | 12 |
| Low | 4 |
