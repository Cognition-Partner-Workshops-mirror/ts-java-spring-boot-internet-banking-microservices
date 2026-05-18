# Internet Banking Microservices — Engineering Standards Gap Analysis

<!-- Comprehensive gap analysis comparing the codebase against engineering best practices. -->
<!-- Each gap is rated by Severity (Critical/High/Medium/Low) and Effort to remediate (Small/Medium/Large). -->

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [8. Summary Table](#8-summary-table)

---

## 1. Code Organization

### GAP-01: No Shared Library for Duplicated Code

<!-- BaseMapper, AuditAware, GlobalExceptionHandler, ErrorResponse, SimpleBankingGlobalException, AppAuthUserFilter, -->
<!-- ApiRequestContext, ApiRequestContextHolder, and audit config classes are copy-pasted across 3-4 services. -->

**Severity: High** | **Effort: Medium**

The following classes are duplicated verbatim across 3-4 services with no shared library:

| Duplicated Class | Found In |
|---|---|
| `BaseMapper<E, D>` | core-banking, fund-transfer, utility-payment, user-service |
| `AuditAware` (MappedSuperclass) | fund-transfer, utility-payment, user-service |
| `GlobalExceptionHandler` | core-banking, fund-transfer, utility-payment, user-service |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment, user-service |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, utility-payment, user-service |
| `AppAuthUserFilter` | fund-transfer, utility-payment, user-service |
| `ApiRequestContext` / `ApiRequestContextHolder` | fund-transfer, utility-payment, user-service |
| `AuditConfig` / `AuditorAwareConfig` | fund-transfer, utility-payment, user-service |

**Impact:** Any bug fix or improvement must be applied to each copy independently, leading to drift and inconsistency.

### GAP-02: No Multi-Module Gradle Root Project

**Severity: Medium** | **Effort: Medium**

Each service has its own independent `build.gradle` with no root `settings.gradle` or `build.gradle` for the repository. This means:
- No way to run `./gradlew build` from the repo root to build all services
- Dependency versions are repeated across services (e.g., `springdoc-openapi-starter-webflux-ui:2.1.0`, `mysql-connector-j:8.4.0`)
- No centralized dependency version management (BOM or version catalog)

### GAP-03: Inconsistent Package Structure Across Services

**Severity: Low** | **Effort: Small**

While most services follow `com.javatodev.finance`, sub-package naming varies:
- Fund-transfer: `model.repository.FundTransferRepository`
- Utility-payment: `repository.UtilityPaymentRepository` (no `model` prefix)
- User-service Feign: `configuration.feign.CustomFeignClientConfiguration`
- Fund-transfer/Utility-payment Feign: `configuration.CustomFeignClientConfiguration` (no `feign` sub-package)
- User-service Feign client: `service.rest.BankingCoreRestClient`
- Fund-transfer Feign client: `service.rest.client.BankingCoreFeignClient` (extra `client` package)

### GAP-04: Mapper Instantiation Outside DI Container

**Severity: Low** | **Effort: Small**

<!-- Mappers are instantiated with `new` instead of being Spring beans. -->

All mappers (`BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated directly with `new` in service classes rather than being managed as Spring beans:

```java
private UserMapper userMapper = new UserMapper(); // in UserService
private FundTransferMapper mapper = new FundTransferMapper(); // in FundTransferService
```

This prevents dependency injection, makes testing harder, and is inconsistent with the Spring paradigm used everywhere else.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

<!-- Every GlobalExceptionHandler in every service maps all exceptions to ResponseEntity.badRequest(). -->

**Severity: Critical** | **Effort: Small**

Every `GlobalExceptionHandler` across all four services maps **every exception** to `400 Bad Request`:

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

**Issues:**
- `EntityNotFoundException` returns 400 instead of 404
- `InsufficientFundsException` returns 400 instead of 422
- Internal server errors return 400 instead of 500
- The generic `Exception` handler leaks the full exception object (including stack trace) to the client

### GAP-06: No Structured Error Response for Generic Exceptions

**Severity: High** | **Effort: Small**

The generic `Exception` handler returns a plain string (`"Exception occur inside API " + e`) instead of the structured `ErrorResponse` object. This means:
- Clients cannot programmatically parse error responses
- Internal exception details (class names, messages, potentially stack traces) are exposed to the client
- Inconsistent response format between business exceptions and unexpected exceptions

### GAP-07: Missing Exception Types

**Severity: Medium** | **Effort: Small**

Only `core-banking-service` and `user-service` define specific exception classes. Fund-transfer and utility-payment services only have the generic `SimpleBankingGlobalException`. Missing exception types include:
- `DuplicateTransactionException` — for idempotency violations
- `ServiceUnavailableException` — for Feign client failures
- `ValidationException` — for input validation failures
- `ForbiddenException` — for authorization failures

### GAP-08: No Feign Error Decoder in Fund-Transfer and Utility-Payment

**Severity: High** | **Effort: Small**

<!-- Only user-service has CustomFeignErrorDecoder. Fund-transfer and utility-payment have no Feign error handling. -->

Only `user-service` implements a `CustomFeignErrorDecoder` to parse error responses from core-banking-service. The `fund-transfer-service` and `utility-payment-service` have **no custom Feign error decoder**, meaning:
- Raw `FeignException` propagates to the client as a 400 with the full exception string
- No structured error information from downstream failures
- Clients cannot distinguish between a validation error and a Feign communication failure

---

## 3. Testing

### GAP-09: Minimal Test Coverage — Only 1 of 6 Services Has Real Tests

**Severity: Critical** | **Effort: Large**

| Service | Test Classes | Real Test Methods | Coverage |
|---|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `ApplicationTests` | ~10 unit tests | Low — service layer only |
| internet-banking-fund-transfer-service | `ApplicationTests` (empty) | 0 | None |
| internet-banking-utility-payment-service | `ApplicationTests` (empty) | 0 | None |
| internet-banking-user-service | `ApplicationTests` (empty) | 0 | None |
| internet-banking-api-gateway | `ApplicationTests` (empty) | 0 | None |
| internet-banking-service-registry | `ApplicationTests` (empty) | 0 | None |

5 out of 6 services have **zero functional tests** — only the default Spring Boot `contextLoads()` stub.

### GAP-10: No Integration Tests

**Severity: High** | **Effort: Large**

No integration tests exist anywhere in the codebase:
- No `@SpringBootTest` with real database (or Testcontainers)
- No Feign client contract tests
- No API-level tests (`@WebMvcTest`, `MockMvc`)
- No end-to-end tests validating cross-service flows

### GAP-11: No Contract Tests Between Services

**Severity: High** | **Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to validate that:
- Feign client interfaces match actual controller endpoints
- Request/response DTOs are compatible across service boundaries
- Breaking API changes are detected before deployment

### GAP-12: No Test Coverage Reporting

**Severity: Medium** | **Effort: Small**

No code coverage tool (JaCoCo, Cobertura) is configured in any `build.gradle`. There is no way to measure or enforce coverage thresholds.

---

## 4. Security

### GAP-13: No Input Validation on Any Request DTO

**Severity: Critical** | **Effort: Small**

<!-- No @Valid annotation on any controller method. No Bean Validation constraints on any DTO. -->

**Zero** request DTOs use Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.). No controller method uses `@Valid` or `@Validated`:

```java
// FundTransferRequest — no validation
@Data
public class FundTransferRequest {
    private String fromAccount;   // can be null
    private String toAccount;     // can be null
    private BigDecimal amount;    // can be null or negative
    private String authID;        // can be null
}
```

**Impact:** Null values in amount fields cause `NullPointerException` in `TransactionService`. Negative amounts allow money to flow in the wrong direction. Empty account numbers cause opaque `EntityNotFoundException`.

### GAP-14: No Authentication on Downstream Services (Perimeter-Only Security)

**Severity: Critical** | **Effort: Medium**

<!-- JWT is validated only at the API Gateway. Downstream services trust the spoofable X-Auth-Id header. -->

JWT authentication is enforced **only at the API Gateway**. All downstream services (core-banking, fund-transfer, utility-payment, user-service) have **no security configuration** and accept all incoming requests unconditionally. The `X-Auth-Id` header forwarded by the gateway:
- Can be spoofed by any caller that bypasses the gateway (e.g., direct service-to-service on Docker network)
- Is not cryptographically verified by downstream services
- Is only used for audit logging, not authorization

### GAP-15: Hardcoded Credentials in Source Code

**Severity: Critical** | **Effort: Small**

Sensitive credentials are committed in plain text:

| File | Credential |
|---|---|
| `docker-compose/docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose/mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |

### GAP-16: Overly Broad Database Privileges

**Severity: Medium** | **Effort: Small**

The `javatodev_development` user is granted `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` (all databases). Each service should have a dedicated user with least-privilege access to its own schema only.

### GAP-17: Keycloak Singleton Not Thread-Safe

**Severity: Medium** | **Effort: Small**

<!-- KeycloakProperties.getInstance() uses a non-synchronized static field. -->

`KeycloakProperties.getInstance()` uses a classic non-thread-safe singleton pattern:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

Multiple threads could create duplicate Keycloak instances during startup.

### GAP-18: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No dependency vulnerability scanning is configured (no OWASP Dependency Check, Snyk, or Dependabot). Given the use of `springdoc-openapi-starter-webflux-ui:2.1.0` (pinned, potentially outdated) and other pinned versions, vulnerabilities may go undetected.

### GAP-19: Password Handling in Registration

**Severity: High** | **Effort: Small**

The `User` DTO used for registration includes the `password` field and is also used as the response DTO. While Lombok's `@Data` doesn't inherently expose it, the password travels through the entire response chain. There is no `@JsonProperty(access = WRITE_ONLY)` or separate request/response DTO to prevent password leakage.

---

## 5. API Design

### GAP-20: Raw `ResponseEntity` Without Type Parameters

**Severity: Medium** | **Effort: Small**

<!-- Most controller methods return raw ResponseEntity instead of ResponseEntity<T>. -->

Most controller methods in core-banking, fund-transfer, and utility-payment services return raw `ResponseEntity` without type parameters:

```java
public ResponseEntity getBankAccount(...) { ... }  // should be ResponseEntity<BankAccount>
```

This suppresses compile-time type checking and generates poor OpenAPI documentation. Only `user-service` correctly uses `ResponseEntity<User>`.

### GAP-21: Wrong OpenAPI Dependency — WebFlux UI on MVC Services

**Severity: Medium** | **Effort: Small**

<!-- All MVC services use springdoc-openapi-starter-webflux-ui instead of webmvc-ui. -->

All four MVC-based services (core-banking, fund-transfer, utility-payment, user-service) declare:

```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

They should use `springdoc-openapi-starter-webmvc-ui` since they are Spring MVC applications, not WebFlux. The webflux dependency may partially work but is incorrect and may cause classloading issues.

### GAP-22: No API Versioning Strategy

**Severity: Medium** | **Effort: Medium**

While endpoints use `/api/v1/` prefix, there is no actual versioning mechanism. No strategy is defined for:
- How breaking changes will be handled
- Whether URL-based, header-based, or content-type versioning will be used
- How deprecated endpoints will be communicated

### GAP-23: Incomplete Pagination Response

**Severity: Medium** | **Effort: Small**

<!-- Paginated endpoints return List<T> instead of Page<T>, losing pagination metadata. -->

All paginated endpoints accept `Pageable` but return `List<T>` instead of `Page<T>`:

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));  // loses total count, page info
}
```

Clients receive no pagination metadata (total elements, total pages, current page, page size), making it impossible to implement proper pagination UI.

### GAP-24: No Filtering or Sorting Parameters

**Severity: Low** | **Effort: Medium**

List endpoints (users, transfers, payments) support only basic `Pageable` (page/size/sort) via query parameters. No custom filtering parameters (e.g., by date range, status, account number) are supported.

### GAP-25: Inconsistent Endpoint Naming

**Severity: Low** | **Effort: Small**

Gateway route prefixes don't consistently map to service path conventions:
- Gateway: `/payment/**` but service uses `/api/v1/utility-payment`
- Gateway: `/core/**` but service uses `/api/v1/account`, `/api/v1/transaction`, `/api/v1/user` (multiple path roots)

---

## 6. Observability

### GAP-26: No Structured Logging

**Severity: Medium** | **Effort: Medium**

All services use default Spring Boot logging with plain-text format. There is no:
- JSON structured logging (e.g., Logstash Logback Encoder)
- Consistent log format across services
- Correlation ID propagation in log messages
- MDC (Mapped Diagnostic Context) integration with trace IDs

### GAP-27: Sensitive Data in Logs

**Severity: High** | **Effort: Small**

<!-- Request DTOs containing potentially sensitive data are logged via toString(). -->

Multiple controllers and services log full request DTOs using `toString()`:

```java
log.info("Creating user with {}", request.toString());  // may include password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Incoming Request From {}", userAuthId);  // logs auth identity
```

The user registration request includes the password field, which gets logged in plaintext.

### GAP-28: No Custom Health Indicators

**Severity: Low** | **Effort: Small**

While Spring Boot Actuator is included in all services providing `/actuator/health`, there are no custom health indicators for:
- Database connectivity (relies on auto-configured indicator)
- Keycloak availability (user-service)
- Core-banking-service availability (for dependent services)
- Feign client health

### GAP-29: No Metrics Endpoints Beyond Defaults

**Severity: Low** | **Effort: Medium**

Micrometer is included for tracing but no custom business metrics are defined:
- No transaction count/rate metrics
- No fund transfer success/failure rate
- No payment processing latency metrics
- No active user count metrics

### GAP-30: Zipkin Tracing Configuration Not in Source

**Severity: Low** | **Effort: Small**

Tracing dependencies are included in `build.gradle` but the Zipkin endpoint configuration, sampling rate, and other tracing properties are externalized to the config server. If the config server is unavailable or misconfigured, tracing silently fails with no fallback configuration in the service's own `application.yml`.

---

## 7. Resilience

### GAP-31: No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

<!-- No Resilience4j, Hystrix, or any circuit breaker implementation. -->

There is **no circuit breaker** implementation anywhere in the codebase. No Resilience4j, Spring Cloud Circuit Breaker, or Hystrix dependency exists. If `core-banking-service` goes down:
- `fund-transfer-service` will hang on every request until Feign timeout
- `utility-payment-service` will hang on every request until Feign timeout
- `user-service` will hang on every registration/list request
- All pending requests will consume thread pool resources, causing cascading failure across all services

### GAP-32: No Retry Policies

**Severity: High** | **Effort: Small**

No retry configuration exists for Feign clients or any other outbound calls. Transient network failures or brief core-banking-service restarts cause immediate hard failures with no recovery attempt.

### GAP-33: No Timeout Configuration

**Severity: High** | **Effort: Small**

<!-- No Feign timeouts, no RestTemplate timeouts, no connection pool configuration. -->

No explicit timeout configuration exists for:
- Feign client connection timeout
- Feign client read timeout
- Database connection pool timeouts
- Keycloak admin client timeouts

Default Feign timeouts (10s connect, 60s read) may be too generous for a banking application, causing slow failures to cascade.

### GAP-34: No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

No fallback mechanisms exist when downstream services are unavailable:
- No cached responses for read operations
- No graceful degradation for non-critical operations
- No dead-letter queue for failed transactions
- Transaction orchestration has no compensation logic

### GAP-35: No Idempotency Keys for Financial Operations

**Severity: Critical** | **Effort: Medium**

<!-- POST endpoints for fund-transfer and utility-payment have no idempotency protection. -->

Fund transfer (`POST /api/v1/transfer`) and utility payment (`POST /api/v1/utility-payment`) endpoints have **no idempotency keys**. Network retries, client-side double-clicks, or proxy retransmissions can cause:
- Duplicate fund transfers — money debited/credited multiple times
- Duplicate utility payments — bills paid multiple times
- No mechanism to detect or prevent duplicate processing

### GAP-36: No Transaction Compensation (Saga Pattern)

**Severity: Critical** | **Effort: Large**

<!-- If core-banking Feign call fails after entity saved as PENDING/PROCESSING, no rollback occurs. -->

Fund transfer and utility payment flows follow a two-phase pattern (save PENDING -> call core-banking -> update SUCCESS) but have **no compensation logic**:
- If the Feign call to core-banking fails after saving PENDING, the entity stays PENDING forever
- If the Feign call succeeds but the subsequent status update fails, the entity stays PENDING while money has already moved
- No scheduled cleanup, retry mechanism, or manual reconciliation process
- No saga orchestrator or choreography pattern

### GAP-37: Double-Deduction Bug in Core Banking Balance Calculations

**Severity: Critical** | **Effort: Small**

<!-- TransactionService.internalFundTransfer() and utilPayment() compute availableBalance incorrectly. -->

In `TransactionService.internalFundTransfer()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// Bug: availableBalance is now actualBalance - amount - amount (double deduction)
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

After line 1, `actualBalance` is already reduced by `amount`. Line 2 subtracts `amount` again from the new `actualBalance`, resulting in a **double deduction** on `availableBalance`. The same bug affects:
- `internalFundTransfer()` — both debit and credit sides
- `utilPayment()` — debit side

---

## 8. Summary Table

<!-- Master table of all identified gaps with severity and effort ratings. -->

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | Code Organization | No shared library for duplicated code | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle root project | Medium | Medium |
| GAP-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-04 | Code Organization | Mapper instantiation outside DI | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-06 | Error Handling | No structured error response for generic exceptions | High | Small |
| GAP-07 | Error Handling | Missing exception types | Medium | Small |
| GAP-08 | Error Handling | No Feign error decoder in fund-transfer/utility-payment | High | Small |
| GAP-09 | Testing | Only 1 of 6 services has real tests | Critical | Large |
| GAP-10 | Testing | No integration tests | High | Large |
| GAP-11 | Testing | No contract tests between services | High | Large |
| GAP-12 | Testing | No test coverage reporting | Medium | Small |
| GAP-13 | Security | No input validation on any request DTO | Critical | Small |
| GAP-14 | Security | No auth on downstream services (perimeter-only) | Critical | Medium |
| GAP-15 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-16 | Security | Overly broad database privileges | Medium | Small |
| GAP-17 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-18 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-19 | Security | Password field in response DTO | High | Small |
| GAP-20 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-21 | API Design | Wrong OpenAPI dependency (WebFlux on MVC) | Medium | Small |
| GAP-22 | API Design | No API versioning strategy | Medium | Medium |
| GAP-23 | API Design | Incomplete pagination response | Medium | Small |
| GAP-24 | API Design | No filtering or sorting parameters | Low | Medium |
| GAP-25 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-26 | Observability | No structured logging | Medium | Medium |
| GAP-27 | Observability | Sensitive data in logs | High | Small |
| GAP-28 | Observability | No custom health indicators | Low | Small |
| GAP-29 | Observability | No metrics beyond defaults | Low | Medium |
| GAP-30 | Observability | Zipkin config not in source | Low | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies | High | Small |
| GAP-33 | Resilience | No timeout configuration | High | Small |
| GAP-34 | Resilience | No fallback behavior | Medium | Medium |
| GAP-35 | Resilience | No idempotency keys for financial operations | Critical | Medium |
| GAP-36 | Resilience | No transaction compensation (saga) | Critical | Large |
| GAP-37 | Resilience | Double-deduction bug in balance calculations | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 10 |
| High | 9 |
| Medium | 12 |
| Low | 6 |
| **Total** | **37** |
