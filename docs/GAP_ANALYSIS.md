# Internet Banking Microservices - Engineering Standards Gap Analysis

## Overview

This document compares the codebase against industry engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and estimated **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### GAP-01: No Shared Library - Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copied verbatim across 3-4 services with no shared module:

| Class | Duplicated In |
|---|---|
| `AuditAware` (MappedSuperclass) | fund-transfer, utility-payment, user-service |
| `BaseMapper<E, D>` | fund-transfer, utility-payment, user-service, core-banking |
| `GlobalExceptionHandler` | core-banking, fund-transfer, utility-payment, user-service |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment, user-service |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, utility-payment, user-service |
| `AppAuthUserFilter` | fund-transfer, utility-payment, user-service |
| `ApiRequestContext` / `ApiRequestContextHolder` | fund-transfer, utility-payment, user-service |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact**: Bug fixes must be applied to every copy. Drift between copies is likely. Increases maintenance burden significantly.

### GAP-02: No Multi-Module Gradle Build

**Severity: Medium | Effort: Medium**

Each service has its own independent Gradle wrapper and build file. There is no root `settings.gradle` or `build.gradle` for dependency version management, shared plugins, or coordinated builds.

**Impact**: Dependency version drift across services, no single command to build all services, no enforced consistency.

### GAP-03: Inconsistent Package Structure

**Severity: Low | Effort: Small**

- core-banking-service uses `repository/` at the package root, other services use `model/repository/`
- user-service has `configuration/keycloak/` and `configuration/feign/`, others have flat `configuration/`
- DTOs are in `model/dto/` in some services and `model/rest/request/` + `model/rest/response/` in others

### GAP-04: Mapper Classes Instantiated Manually

**Severity: Low | Effort: Small**

All mapper classes (e.g., `BankAccountMapper`, `FundTransferMapper`) are instantiated with `new` in service classes rather than being Spring-managed beans. Example: `private FundTransferMapper mapper = new FundTransferMapper();`

**Impact**: Cannot use dependency injection in mappers, makes testing harder, inconsistent with Spring conventions.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` (in all 4 services) maps **all exceptions** to `ResponseEntity.badRequest()`:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

This means:
- `EntityNotFoundException` returns 400 instead of 404
- `InsufficientFundsException` returns 400 instead of 422
- Server errors (NPE, DB connection failure) return 400 instead of 500

### GAP-06: Exception Stack Trace Leaked to Clients

**Severity: High | Effort: Small**

The catch-all exception handler returns the full exception object as a string in the response body: `"Exception occur inside API " + e`. This exposes internal class names, stack traces, and potentially sensitive information to API consumers.

### GAP-07: Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` returns structured `ErrorResponse` with `code` and `message`
- The catch-all handler returns a plain string
- No standard error envelope (e.g., `timestamp`, `path`, `status`, `error`, `message`)

### GAP-08: No Feign Error Decoder in Fund Transfer and Utility Payment Services

**Severity: High | Effort: Small**

Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer-service and utility-payment-service have no custom Feign error handling. Raw Feign exceptions propagate and are caught by the generic exception handler, returning unhelpful 400 errors.

### GAP-09: Raw `ResponseEntity` Without Type Parameters

**Severity: Low | Effort: Small**

All controllers use raw `ResponseEntity` instead of typed `ResponseEntity<T>`. Only the user-service `UserController` uses generics partially (e.g., `ResponseEntity<User>`). This provides no compile-time type safety and generates warnings.

---

## 3. Testing

### GAP-10: Minimal Test Coverage - Only 1 of 6 Services Has Tests

**Severity: Critical | Effort: Large**

| Service | Test Files | Test Methods | Coverage |
|---|---|---|---|
| core-banking-service | 3 (AccountServiceTest, TransactionServiceTest, UserServiceTest) | 16 | Service layer only |
| internet-banking-api-gateway | 1 (empty contextLoads) | 0 meaningful | None |
| internet-banking-config-server | 1 (empty contextLoads) | 0 meaningful | None |
| internet-banking-fund-transfer-service | 1 (empty contextLoads) | 0 meaningful | None |
| internet-banking-service-registry | 1 (empty contextLoads) | 0 meaningful | None |
| internet-banking-user-service | 1 (empty contextLoads) | 0 meaningful | None |
| internet-banking-utility-payment-service | 1 (empty contextLoads) | 0 meaningful | None |

5 out of 6 services have **zero meaningful tests** (only auto-generated `contextLoads()` stubs). The core-banking-service has basic unit tests for service methods but no controller tests.

### GAP-11: No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- Database interactions with actual queries
- Feign client behavior between services
- End-to-end request flow through the API Gateway
- Keycloak authentication/authorization flows

### GAP-12: No Contract Tests Between Services

**Severity: Medium | Effort: Large**

No Spring Cloud Contract or Pact tests to verify API compatibility between services. Breaking changes to core-banking-service APIs would not be detected until runtime.

### GAP-13: Empty `contextLoads()` Tests Will Fail

**Severity: Medium | Effort: Small**

The `@SpringBootTest` annotated `contextLoads()` tests in fund-transfer, utility-payment, and user-service require database connections and Config Server to run. They will fail in CI without infrastructure. The core-banking-service has a proper test `application.yml` with H2; others do not have adequate test configurations.

---

## 4. Security

### GAP-14: No Input Validation on Any Request DTO

**Severity: Critical | Effort: Small**

No `@Valid` annotation on any `@RequestBody` parameter. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.) on any DTO class across the entire codebase.

**Impact**: Null amounts, negative transfers, empty account numbers, and other invalid inputs are accepted and cause `NullPointerException` or incorrect behavior deep in the business logic.

Examples:
- `FundTransferRequest` has no validation: `fromAccount`, `toAccount`, `amount` can all be null
- `UtilityPaymentRequest` has no validation: negative `amount` would process
- `User` registration: no email format validation, no password strength rules

### GAP-15: Downstream Services Trust Spoofable Header

**Severity: Critical | Effort: Medium**

JWT validation occurs **only at the API Gateway**. Downstream services (core-banking, fund-transfer, utility-payment, user-service) have no authentication mechanism. They blindly trust the `X-Auth-Id` header set by the gateway.

Any client that bypasses the gateway and reaches a downstream service directly (which is possible in the Docker network) can forge the `X-Auth-Id` header and impersonate any user. No service verifies JWT tokens independently.

### GAP-16: Hardcoded Credentials in Source Control

**Severity: Critical | Effort: Small**

| File | Credential |
|---|---|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `docker-compose.yml` | `POSTGRES_PASSWORD: password` |
| `privileges.sql` | `CREATE USER 'javatodev_development' IDENTIFIED BY 'oPItyPticIAt'` |
| `mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `README.md` | Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` |

### GAP-17: CSRF Disabled Without Justification

**Severity: Medium | Effort: Small**

`SecurityConfiguration` disables CSRF globally: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While common for stateless APIs, there is no documentation justifying the decision, and the registration endpoint is public.

### GAP-18: Keycloak Client Uses Non-Thread-Safe Singleton

**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a manual singleton pattern without synchronization:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

Under concurrent requests, multiple instances could be created, or a partially-constructed instance could be returned.

### GAP-19: No Authorization Checks Beyond Authentication

**Severity: High | Effort: Medium**

The gateway enforces authentication (valid JWT required) but there are no role-based access controls. Any authenticated user can:
- Approve other users (`PATCH /bank-users/update/{id}`)
- List all users
- Transfer funds from any account
- Access core-banking endpoints directly

### GAP-20: No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning is configured. Dependencies are pinned to specific versions but never audited.

---

## 5. API Design

### GAP-21: Pagination Returns Raw List Without Metadata

**Severity: Medium | Effort: Small**

All paginated endpoints return `List<T>` instead of a pagination envelope:

```java
public ResponseEntity readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

Missing: `totalElements`, `totalPages`, `pageNumber`, `pageSize`, `hasNext`, `hasPrevious`. Clients cannot navigate pages.

### GAP-22: No API Versioning Strategy

**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` prefix, but there is no documented versioning strategy or mechanism to support multiple versions simultaneously.

### GAP-23: Wrong OpenAPI Dependency - WebFlux Instead of WebMVC

**Severity: Medium | Effort: Small**

All MVC-based services (core-banking, fund-transfer, utility-payment, user-service) use:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

They should use `springdoc-openapi-starter-webmvc-ui` since they are Spring MVC applications. The WebFlux dependency may cause classpath conflicts and incorrect Swagger UI behavior.

### GAP-24: Inconsistent REST Conventions

**Severity: Low | Effort: Small**

- User registration is `POST /register` (verb in URL, should be `POST /bank-users`)
- User update is `PATCH /update/{id}` (verb in URL, should be `PATCH /bank-users/{id}`)
- Utility account lookup is by `account_name` but parameter is actually `providerName`
- Fund transfer uses `POST /api/v1/transfer` (resource-based, good)
- No HATEOAS links in responses

### GAP-25: No Request/Response Logging Middleware

**Severity: Low | Effort: Small**

Individual controllers log request objects manually (e.g., `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())`). No centralized request/response logging filter exists. Logging `.toString()` on request objects could expose sensitive data.

---

## 6. Observability

### GAP-26: No Structured Logging

**Severity: Medium | Effort: Medium**

All services use default Logback with unstructured log output. No JSON logging format configured, making log aggregation and querying difficult. Log messages are ad-hoc strings without consistent structure.

### GAP-27: Default Actuator Configuration - No Health Details

**Severity: Medium | Effort: Small**

Services include `spring-boot-starter-actuator` but there is no explicit configuration for:
- Health check details (database connectivity, Keycloak availability, downstream service health)
- Custom health indicators
- Info endpoint enrichment
- Metrics export configuration

Only the `git-properties` plugin provides basic build info.

### GAP-28: No Prometheus Metrics Export

**Severity: Medium | Effort: Small**

Prometheus is listed in the README's technology stack but no `micrometer-registry-prometheus` dependency exists in any service. No `/actuator/prometheus` endpoint is available.

### GAP-29: Zipkin Tracing Configuration Not Verified

**Severity: Low | Effort: Small**

All services include Zipkin/Micrometer tracing dependencies but the sampling rate and Zipkin URL configuration rely entirely on the external Config Server repository. No fallback configuration exists if the Config Server is unavailable.

### GAP-30: Sensitive Data Logged in Plain Text

**Severity: High | Effort: Small**

Controllers log request DTOs directly: `log.info("Creating user with {}", request.toString())`. The `User` DTO contains `password`, which would be logged in plain text. Similarly, fund transfer requests containing account numbers are logged without masking.

---

## 7. Resilience

### GAP-31: No Circuit Breakers

**Severity: Critical | Effort: Medium**

No Resilience4j or Hystrix circuit breakers on any Feign client. If core-banking-service becomes slow or unavailable:
- Fund-transfer-service will block indefinitely on Feign calls
- Utility-payment-service will block indefinitely
- User-service will block on user lookup calls
- Thread pool exhaustion will cascade to the API Gateway

### GAP-32: No Retry Policies

**Severity: High | Effort: Small**

No retry configuration on Feign clients. Transient network failures or temporary service unavailability causes immediate failure with no recovery attempt. Spring Retry is not included as a dependency.

### GAP-33: No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign client connection and read timeouts
- Database connection pool timeouts
- Keycloak admin client timeouts

Default timeouts (often infinite or very long) risk hanging requests.

### GAP-34: No Compensation/Rollback on Feign Failures

**Severity: Critical | Effort: Medium**

In both `FundTransferService` and `UtilityPaymentService`, if the Feign call to core-banking-service fails **after** the local entity is saved:
- Fund transfer stays `PENDING` forever
- Utility payment stays `PROCESSING` forever
- No scheduled job to clean up stale records
- No retry mechanism
- No compensation logic (e.g., saga pattern)

This creates **orphaned transactions** that are never resolved.

### GAP-35: No Idempotency Keys

**Severity: High | Effort: Medium**

`POST` endpoints for fund transfer and utility payment have no idempotency mechanism. Network retries or client double-submits can result in **duplicate financial transactions**. No `Idempotency-Key` header or request deduplication logic exists.

### GAP-36: No Rate Limiting

**Severity: Medium | Effort: Small**

The API Gateway has no rate limiting configuration. APIs are vulnerable to abuse and denial-of-service.

### GAP-37: Double-Deduction Bug in Balance Calculations

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()`:

```java
// Line 90-91: Bug - double deduction
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

After line 90, `actualBalance` is already reduced. Line 91 subtracts `amount` again from the already-reduced value. Same pattern in `utilPayment()` at lines 63-64. For a $100 transfer from a $200 account: `actualBalance` correctly becomes $100, but `availableBalance` becomes $0 instead of $100.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | Code Organization | No shared library - duplicated code | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-04 | Code Organization | Mappers not Spring-managed | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-06 | Error Handling | Exception stack trace leaked | High | Small |
| GAP-07 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-08 | Error Handling | No Feign error decoder in 2 services | High | Small |
| GAP-09 | Error Handling | Raw ResponseEntity without generics | Low | Small |
| GAP-10 | Testing | Only 1 of 6 services has tests | Critical | Large |
| GAP-11 | Testing | No integration tests | High | Large |
| GAP-12 | Testing | No contract tests | Medium | Large |
| GAP-13 | Testing | Empty contextLoads tests will fail | Medium | Small |
| GAP-14 | Security | No input validation | Critical | Small |
| GAP-15 | Security | Downstream services trust spoofable header | Critical | Medium |
| GAP-16 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-17 | Security | CSRF disabled without justification | Medium | Small |
| GAP-18 | Security | Non-thread-safe Keycloak singleton | High | Small |
| GAP-19 | Security | No authorization beyond authentication | High | Medium |
| GAP-20 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-21 | API Design | Pagination without metadata | Medium | Small |
| GAP-22 | API Design | No API versioning strategy | Low | Medium |
| GAP-23 | API Design | Wrong OpenAPI dependency (webflux) | Medium | Small |
| GAP-24 | API Design | Inconsistent REST conventions | Low | Small |
| GAP-25 | API Design | No centralized request/response logging | Low | Small |
| GAP-26 | Observability | No structured logging | Medium | Medium |
| GAP-27 | Observability | Default actuator - no health details | Medium | Small |
| GAP-28 | Observability | No Prometheus metrics export | Medium | Small |
| GAP-29 | Observability | Zipkin config not verified | Low | Small |
| GAP-30 | Observability | Sensitive data logged in plain text | High | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies | High | Small |
| GAP-33 | Resilience | No timeout configuration | High | Small |
| GAP-34 | Resilience | No compensation on Feign failures | Critical | Medium |
| GAP-35 | Resilience | No idempotency keys | High | Medium |
| GAP-36 | Resilience | No rate limiting | Medium | Small |
| GAP-37 | Resilience | Double-deduction balance bug | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 8 |
| High | 10 |
| Medium | 12 |
| Low | 7 |
| **Total** | **37** |
