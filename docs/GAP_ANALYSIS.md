# Engineering Standards Gap Analysis

## Summary

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

### Severity Legend

| Rating | Meaning |
|---|---|
| **Critical** | Production risk, security vulnerability, or data integrity issue |
| **High** | Significant code quality or reliability problem |
| **Medium** | Maintainability, developer experience, or moderate risk |
| **Low** | Polish, convention, or minor improvement |

### Effort Legend

| Rating | Meaning |
|---|---|
| **Small** | < 1 day, localized change |
| **Medium** | 1-3 days, touches multiple files/services |
| **Large** | 3+ days, architectural change or cross-cutting |

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Module Gradle Build

**Severity: Medium** | **Effort: Medium**

Each service has its own standalone `build.gradle` with duplicated plugin declarations, dependency management, and Spring Cloud BOM imports. There is no root `settings.gradle` or parent build to manage shared configuration.

**Impact:** Version drift risk, duplicated build logic across 7 `build.gradle` files, no single command to build/test all services.

**Current state:** 7 independent Gradle projects with identical plugin versions (`spring-boot 3.2.4`, `dependency-management 1.1.4`, `git-properties 2.4.2`).

### GAP-ORG-02: Duplicated Code Across Services

**Severity: High** | **Effort: Large**

The following classes are copy-pasted across 3-4 services with identical or near-identical implementations:

| Duplicated Class | Services |
|---|---|
| `BaseMapper<E, D>` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` (MappedSuperclass) | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `AuditConfig` / `AuditorAwareConfig` | user-service, fund-transfer, utility-payment |
| `ErrorResponse` | all 4 business services |
| `SimpleBankingGlobalException` | all 4 business services |
| `GlobalExceptionHandler` | all 4 business services |
| `TransactionStatus` enum | fund-transfer, utility-payment |

**Impact:** Bug fixes must be applied to 3-4 copies. Inconsistency risk is high (e.g., the fund-transfer `GlobalExceptionHandler` uses `new ErrorResponse()` constructor while others use the builder pattern).

### GAP-ORG-03: Inconsistent Package Structure

**Severity: Low** | **Effort: Small**

- core-banking-service uses `repository` package at root level.
- user-service nests repositories under `model.repository`.
- utility-payment-service uses `repository` at root level.
- fund-transfer-service uses `model.repository`.
- DTOs live under `model.dto` in some services and `model.rest.request`/`model.rest.response` in others.

**Impact:** Cognitive overhead when navigating across services.

### GAP-ORG-04: Inconsistent Indentation Style

**Severity: Low** | **Effort: Small**

Some files use tabs (utility-payment-service `build.gradle`), others use spaces (core-banking-service `build.gradle`). No `.editorconfig` file exists.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400

**Severity: Critical** | **Effort: Medium**

Every `GlobalExceptionHandler` across all services returns `ResponseEntity.badRequest()` (HTTP 400) for all exceptions, including:
- `EntityNotFoundException` → should return **404 Not Found**
- `InsufficientFundsException` → should return **422 Unprocessable Entity** or **409 Conflict**
- Generic `Exception` → should return **500 Internal Server Error**

**Current code (all services):**
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

### GAP-ERR-02: Exception Details Leaked to Clients

**Severity: Critical** | **Effort: Small**

The generic exception handler concatenates the full exception object (including stack trace) into the response body: `"Exception occur inside API " + e`. This leaks internal implementation details, class names, and potentially sensitive information to API consumers.

### GAP-ERR-03: No Error Response Envelope Consistency

**Severity: Medium** | **Effort: Small**

- `SimpleBankingGlobalException` returns `ErrorResponse { code, message }`.
- Generic `Exception` returns a raw string `"Exception occur inside API ..."`.
- No consistent error envelope (e.g., no timestamp, no path, no request ID).

### GAP-ERR-04: Missing Raw Type Parameterization on ResponseEntity

**Severity: Medium** | **Effort: Small**

All controller methods and exception handlers return raw `ResponseEntity` instead of parameterized `ResponseEntity<?>` or specific types. This suppresses compile-time type checking.

**Example:** `public ResponseEntity sendFundTransfer(...)` should be `public ResponseEntity<FundTransferResponse> sendFundTransfer(...)`.

### GAP-ERR-05: No Feign Error Decoder in Fund Transfer and Utility Payment Services

**Severity: High** | **Effort: Small**

Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services use the default Feign error decoder, which will wrap all Feign errors as generic `FeignException` rather than mapping them to domain-specific exceptions.

### GAP-ERR-06: No Transaction Failure Handling in Orchestration Services

**Severity: Critical** | **Effort: Medium**

In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`, if the OpenFeign call to core-banking-service fails, the local entity remains in `PENDING`/`PROCESSING` status with no retry, compensation, or status update to `FAILED`. There is no try-catch around the Feign call.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage on Non-Core Services

**Severity: High** | **Effort: Large**

| Service | Test Files | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 files | `AccountServiceTest` (6 tests), `TransactionServiceTest` (8 tests), `UserServiceTest` (3 tests), `contextLoads` |
| internet-banking-user-service | 1 file | `contextLoads` only |
| internet-banking-fund-transfer-service | 1 file | `contextLoads` only |
| internet-banking-utility-payment-service | 1 file | `contextLoads` only |
| internet-banking-api-gateway | 1 file | `contextLoads` only |
| internet-banking-config-server | 1 file | `contextLoads` only |
| internet-banking-service-registry | 1 file | `contextLoads` only |

**Impact:** 5 of 7 services have zero business logic tests. The `contextLoads` tests will likely fail without a running config server/Eureka/MySQL.

### GAP-TEST-02: No Integration Tests

**Severity: High** | **Effort: Large**

No `@SpringBootTest` with embedded databases, no Testcontainers usage, no API integration tests using `MockMvc` or `WebTestClient`. The existing `contextLoads` tests require full infrastructure to run.

### GAP-TEST-03: No Contract Tests Between Services

**Severity: Medium** | **Effort: Large**

No Spring Cloud Contract or Pact tests exist. OpenFeign client interfaces can silently drift from the actual provider API (e.g., if core-banking-service changes an endpoint path or response shape).

### GAP-TEST-04: No Test Configuration for Isolated Testing

**Severity: Medium** | **Effort: Small**

Only `internet-banking-user-service` has a `src/test/resources/application.yml`. Other services have no test-specific configuration, meaning tests require external infrastructure (Config Server, Eureka, MySQL).

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Control

**Severity: Critical** | **Effort: Small**

The following credentials are committed to the repository:

| File | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | PostgreSQL password: `password` |
| `docker-compose/mysql/privileges.sql` | App DB password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |

### GAP-SEC-02: No Input Validation

**Severity: Critical** | **Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Positive`, or any Bean Validation annotations exist on any request DTOs or controller parameters. All user input is accepted without validation:

- `FundTransferRequest`: No validation on `fromAccount`, `toAccount`, or `amount` (could be null, negative, or zero).
- `UtilityPaymentRequest`: No validation on any field.
- `User` (registration): No validation on `email`, `identification`, or `password`.

### GAP-SEC-03: CSRF Disabled Without Justification

**Severity: Medium** | **Effort: Small**

CSRF is disabled in `SecurityConfiguration`: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a pure REST API, no comment or documentation explains this decision.

### GAP-SEC-04: Keycloak Singleton Is Not Thread-Safe

**Severity: High** | **Effort: Small**

`KeycloakProperties.getInstance()` uses a static field without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This is a classic double-checked locking bug (without `volatile` or `synchronized`). Under concurrent access during startup, multiple Keycloak instances could be created.

### GAP-SEC-05: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool is configured in any `build.gradle`. No GitHub Dependabot configuration exists (`.github/dependabot.yml` is absent).

### GAP-SEC-06: Password Exposed in User DTO

**Severity: High** | **Effort: Small**

The `User` DTO in user-service includes a `password` field that is used for both input (registration) and output (response). The password could be serialized back in API responses since there is no `@JsonProperty(access = WRITE_ONLY)` or separate request/response DTOs.

### GAP-SEC-07: No Rate Limiting on Authentication Endpoints

**Severity: Medium** | **Effort: Medium**

The `/user/api/v1/bank-users/register` endpoint is publicly accessible (`permitAll()`) with no rate limiting, making it vulnerable to abuse.

---

## 5. API Design

### GAP-API-01: No Pagination Metadata in Responses

**Severity: Medium** | **Effort: Small**

List endpoints accept Spring's `Pageable` parameter but return raw `List<T>` without pagination metadata (total elements, total pages, current page, page size). Clients have no way to know if more data exists.

### GAP-API-02: No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

All endpoints use `/api/v1/` but there is no versioning mechanism (header-based, URI-based with multiple versions) or documentation about how to introduce v2 endpoints while maintaining backward compatibility.

### GAP-API-03: Inconsistent URI Naming

**Severity: Low** | **Effort: Small**

- `/api/v1/bank-users/register` (user-service) — uses verb
- `/api/v1/transfer` (fund-transfer) — noun, no sub-resource
- `/api/v1/utility-payment` (utility-payment) — noun
- `/api/v1/transaction/fund-transfer` (core-banking) — nested resource
- `/api/v1/account/bank-account/{account_number}` — redundant nesting
- Path variables use `snake_case` (`account_number`) while Java uses `camelCase`

### GAP-API-04: OpenAPI/Swagger Dependency Mismatch

**Severity: Medium** | **Effort: Small**

All services declare `springdoc-openapi-starter-webflux-ui:2.1.0` but only the API gateway uses WebFlux. The other 4 services are servlet-based (Spring MVC) and should use `springdoc-openapi-starter-webmvc-ui` instead. The webflux dependency likely won't render Swagger UI correctly on servlet-based services.

### GAP-API-05: No Response Envelope / HATEOAS

**Severity: Low** | **Effort: Medium**

Responses return raw entity objects without any wrapper. No HAL/HATEOAS links, no standard envelope structure.

### GAP-API-06: Missing HTTP Status Codes for POST Operations

**Severity: Low** | **Effort: Small**

All POST endpoints return HTTP 200 (via `ResponseEntity.ok()`). RESTful convention dictates returning **201 Created** for resource creation operations (user registration, fund transfer initiation).

---

## 6. Observability

### GAP-OBS-01: No Custom Health Indicators

**Severity: Medium** | **Effort: Small**

Spring Boot Actuator is included but no custom health indicators exist for critical dependencies (MySQL connectivity from the service perspective, Keycloak availability, Feign client health).

### GAP-OBS-02: Logging Inconsistency

**Severity: Medium** | **Effort: Small**

- Some handlers log using string concatenation: `log.error("IO Exception..." + e)` — should use parameterized logging: `log.error("IO Exception...", e)`.
- `FundTransferService` uses `log.info("Sending fund transfer request {}" + request.toString())` — the `{}` placeholder and `+` concatenation are mixed.
- No structured logging (JSON format) is configured.
- No correlation ID propagation in log messages (Zipkin traces exist but logs don't include trace/span IDs).

### GAP-OBS-03: No Metrics Endpoints

**Severity: Medium** | **Effort: Small**

While `spring-boot-starter-actuator` is included, there is no Prometheus or Micrometer metrics exporter configured. The README mentions Prometheus but no `/actuator/prometheus` endpoint configuration exists.

### GAP-OBS-04: Distributed Tracing Coverage Gaps

**Severity: Low** | **Effort: Small**

Zipkin dependencies are included and configured, but:
- No custom span annotations for business operations.
- No sampling rate configuration visible in local configs (deferred to Config Server).
- The config-server and service-registry do not include tracing dependencies.

### GAP-OBS-05: Actuator Endpoints Publicly Accessible

**Severity: High** | **Effort: Small**

The gateway security configuration explicitly permits all `/actuator/**` paths without authentication. This exposes sensitive actuator endpoints (env, beans, configprops, heapdump, threaddump) to unauthenticated users.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

No circuit breaker implementation (Resilience4j or Spring Cloud Circuit Breaker) exists on any OpenFeign client. If `core-banking-service` goes down, all upstream services will cascade fail with unhandled exceptions and thread pool exhaustion.

### GAP-RES-02: No Retry Policies

**Severity: High** | **Effort: Small**

No Spring Retry or Resilience4j retry configuration on Feign clients. Transient network failures will immediately propagate as errors.

### GAP-RES-03: No Timeout Configuration

**Severity: High** | **Effort: Small**

No explicit connection or read timeouts on OpenFeign clients. Default Feign timeouts (potentially infinite) could cause thread starvation under load.

### GAP-RES-04: No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

No `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)` defined. No graceful degradation path exists when downstream services are unavailable.

### GAP-RES-05: No Database Connection Pooling Configuration

**Severity: Medium** | **Effort: Small**

No explicit HikariCP pool configuration (pool size, connection timeout, idle timeout). Relies on Spring Boot defaults which may not be appropriate for production.

### GAP-RES-06: Potential Balance Calculation Bug

**Severity: Critical** | **Effort: Small**

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
After setting `actualBalance = actualBalance - amount`, it then sets `availableBalance = (new actualBalance) - amount`, effectively double-deducting from the available balance. The same issue exists for credit operations and in `utilPayment()`.

### GAP-RES-07: Non-Atomic Balance Updates

**Severity: High** | **Effort: Medium**

The `TransactionEntity` to `BankAccountEntity` relationship is `@OneToOne(cascade = CascadeType.ALL)`. Combined with separate `save()` calls for the from-account and to-account, there is a risk of partial updates if the application crashes between saves. The `@Transactional` annotation helps but the `@OneToOne` cascade could cause unintended cascading updates.

---

## Gap Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Duplicated code across services | High | Large |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Inconsistent indentation style | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | Critical | Medium |
| GAP-ERR-02 | Error Handling | Exception details leaked to clients | Critical | Small |
| GAP-ERR-03 | Error Handling | No consistent error response envelope | Medium | Small |
| GAP-ERR-04 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-ERR-05 | Error Handling | Missing Feign error decoder in 2 services | High | Small |
| GAP-ERR-06 | Error Handling | No failure handling in orchestration | Critical | Medium |
| GAP-TEST-01 | Testing | Near-zero test coverage on 5/7 services | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | No test configuration for isolated testing | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled without justification | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | High | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-06 | Security | Password exposed in User DTO | High | Small |
| GAP-SEC-07 | Security | No rate limiting on public endpoints | Medium | Medium |
| GAP-API-01 | API Design | No pagination metadata | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Medium |
| GAP-API-03 | API Design | Inconsistent URI naming | Low | Small |
| GAP-API-04 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-API-05 | API Design | No response envelope / HATEOAS | Low | Medium |
| GAP-API-06 | API Design | POST returns 200 instead of 201 | Low | Small |
| GAP-OBS-01 | Observability | No custom health indicators | Medium | Small |
| GAP-OBS-02 | Observability | Logging inconsistency | Medium | Small |
| GAP-OBS-03 | Observability | No metrics endpoints (Prometheus) | Medium | Small |
| GAP-OBS-04 | Observability | Distributed tracing coverage gaps | Low | Small |
| GAP-OBS-05 | Observability | Actuator endpoints publicly accessible | High | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No database connection pool config | Medium | Small |
| GAP-RES-06 | Resilience | Balance calculation double-deduction bug | Critical | Small |
| GAP-RES-07 | Resilience | Non-atomic balance updates risk | High | Medium |
