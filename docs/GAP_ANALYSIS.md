# Engineering Standards Gap Analysis

This document compares the current codebase against industry engineering best practices across seven dimensions. Each gap is rated by **Severity** and **Remediation Effort**.

**Severity Scale:**
- **Critical** — Production risk, security vulnerability, or data integrity issue
- **High** — Significant maintainability or reliability concern
- **Medium** — Deviation from best practice that impacts developer experience or operational readiness
- **Low** — Minor improvement opportunity

**Effort Scale:**
- **Small** — < 1 day, isolated change
- **Medium** — 1–3 days, cross-service or moderate refactor
- **Large** — 3+ days, architectural change or significant new infrastructure

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

**Gap:** Each microservice is a standalone Gradle project with its own `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds, enforce consistent dependency versions, or share plugins.

**Impact:** Dependency version drift between services. Today, all services use Spring Boot 3.2.4 and Spring Cloud 2023.0.0, but there is no mechanism to enforce this.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 1.2 Duplicated Code Across Services

**Gap:** Several classes are copy-pasted across multiple services with no shared library:

| Duplicated Class | Services |
|---|---|
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` / `AuditConfig` / `AuditorAwareConfig` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |

**Impact:** Bug fixes or improvements must be applied to every copy independently. Inconsistencies are already present — e.g., the fund-transfer `GlobalExceptionHandler` constructs `ErrorResponse` via constructor while others use the builder pattern.

| Severity | Effort |
|---|---|
| High | Medium |

### 1.3 Inconsistent Package Structure

**Gap:** Package layout varies across services:
- Core banking: `model.dto.request`, `model.dto.response`, `repository`
- User service: `model.rest.response`, `model.repository`, `configuration.keycloak`, `configuration.filter`, `configuration.feign`
- Fund transfer: `model.dto.request`, `model.dto.response`, `model.repository`, `service.rest.client`
- Utility payment: `model.rest.request`, `model.rest.response`, `repository`

The naming convention for Feign clients also differs: `BankingCoreRestClient` (user/utility) vs. `BankingCoreFeignClient` (fund-transfer).

| Severity | Effort |
|---|---|
| Medium | Medium |

### 1.4 Mapper Instantiation Anti-Pattern

**Gap:** All services instantiate mapper objects with `new` inside `@Service` classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than using Spring-managed beans or a mapping framework like MapStruct.

**Impact:** Mappers cannot be mocked in tests. Violates dependency injection principle.

| Severity | Effort |
|---|---|
| Low | Small |

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400 for Everything

**Gap:** Every service's `GlobalExceptionHandler` has a catch-all `@ExceptionHandler({Exception.class})` that returns HTTP 400 (Bad Request) with a raw string body:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Impact:**
- Server errors (NPE, DB connectivity) are reported as 400 instead of 500.
- Stack trace details leak to the client in the string representation of the exception.
- The response body is a plain string, not the structured `ErrorResponse` format.

| Severity | Effort |
|---|---|
| Critical | Small |

### 2.2 `EntityNotFoundException` Returns 400 Instead of 404

**Gap:** `EntityNotFoundException` extends `SimpleBankingGlobalException`, which is handled by `handleGlobalException()` returning HTTP 400. A "not found" error should return HTTP 404.

| Severity | Effort |
|---|---|
| High | Small |

### 2.3 No Feign Error Handling in Fund Transfer / Utility Payment

**Gap:** When the Feign call to core-banking fails (e.g., network error, 4xx, 5xx), the fund-transfer and utility-payment services save the entity with status `PENDING`/`PROCESSING` but never update it to `FAILED`. The Feign exception propagates up unhandled.

The `CustomFeignErrorDecoder` exists in the user-service but is **not present** in the fund-transfer or utility-payment services — they only have a `CustomFeignClientConfiguration` that sets the logger level.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 2.4 Raw `ResponseEntity` Without Type Parameters

**Gap:** Most controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This loses compile-time type safety and makes OpenAPI doc generation less accurate.

| Severity | Effort |
|---|---|
| Low | Small |

---

## 3. Testing

### 3.1 Tests Only in Core Banking Service

**Gap:** Unit tests exist **only** in the `core-banking-service`:
- `AccountServiceTest` — 6 tests (mock-based)
- `TransactionServiceTest` — 10 tests (mock-based)
- `UserServiceTest` — (exists but not reviewed in detail)

The remaining 5 services have **zero** tests beyond the auto-generated Spring Boot context-load test (`*ApplicationTests.java`), and those context-load tests likely fail without a running config server.

| Severity | Effort |
|---|---|
| High | Large |

### 3.2 No Integration Tests

**Gap:** There are no integration tests that verify the REST endpoints (e.g., `@WebMvcTest`, `@SpringBootTest` with `TestRestTemplate`), database interactions (e.g., `@DataJpaTest`), or Feign client behavior.

| Severity | Effort |
|---|---|
| High | Large |

### 3.3 No Contract Tests Between Services

**Gap:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). The Feign client interfaces could drift from the actual controller endpoints without detection.

| Severity | Effort |
|---|---|
| Medium | Large |

### 3.4 No Test Configuration for Config Server Dependency

**Gap:** Services depend on the Config Server at startup (bootstrap context). Test profiles (`src/test/resources/application.yml`) exist but may not properly override all bootstrap properties, making tests fragile.

| Severity | Effort |
|---|---|
| Medium | Small |

---

## 4. Security

### 4.1 CSRF Disabled Globally

**Gap:** The API Gateway disables CSRF protection entirely (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While acceptable for a stateless JWT-based API, this should be explicitly documented as an architectural decision.

| Severity | Effort |
|---|---|
| Low | Small |

### 4.2 No Input Validation

**Gap:** No request body validation exists anywhere in the codebase. None of the DTOs use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.). Examples:
- `FundTransferRequest.amount` — can be null, zero, or negative
- `User.email` — no email format validation
- `UtilityPaymentRequest.referenceNumber` — no length or format constraints

**Impact:** Invalid data reaches the database layer, causing opaque exceptions instead of clear 400 responses.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 4.3 Hardcoded Credentials in Docker Compose

**Gap:** Database passwords and Keycloak admin credentials are hardcoded in `docker-compose.yml`:
- `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `KEYCLOAK_ADMIN_PASSWORD: password`
- `KC_DB_PASSWORD: password`

| Severity | Effort |
|---|---|
| Medium | Small |

### 4.4 Keycloak Singleton Is Not Thread-Safe

**Gap:** `KeycloakProperties.getInstance()` uses a lazy-initialized static field without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { keycloakInstance = ...; }
    return keycloakInstance;
}
```
This is a classic double-checked-locking bug without `volatile` or synchronization.

| Severity | Effort |
|---|---|
| High | Small |

### 4.5 Sensitive Data Logging

**Gap:** Controllers log full request objects including potentially sensitive data:
- `log.info("Creating user with {}", request.toString())` — may log passwords
- `log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())` — logs account numbers and amounts

| Severity | Effort |
|---|---|
| High | Small |

### 4.6 No Authorization Enforcement in Downstream Services

**Gap:** The API Gateway enforces JWT authentication, but downstream services have **no authorization checks**. Any authenticated user can:
- Transfer funds from any account (not just their own)
- View any user's details
- Approve other users

The `X-Auth-Id` header is propagated but never used for authorization logic.

| Severity | Effort |
|---|---|
| Critical | Large |

### 4.7 Stack Trace Exposure in Error Responses

**Gap:** The generic exception handler returns `"Exception occur inside API " + e`, which includes the exception class name, message, and potentially partial stack trace in the `toString()` output. This leaks internal implementation details.

| Severity | Effort |
|---|---|
| High | Small |

---

## 5. API Design

### 5.1 Inconsistent URI Naming

**Gap:** URI conventions are not consistent:
- Core banking: `/api/v1/account/bank-account/{account_number}` (uses underscore in path variable)
- Core banking: `/api/v1/account/util-account/{account_name}` (uses hyphenated path)
- User service: `/api/v1/bank-users/register` (verb as path segment, non-RESTful)
- User service: `/api/v1/bank-users/update/{id}` (verb as path segment)
- Fund transfer: `/api/v1/transfer` (noun-based, good)

RESTful convention prefers nouns and HTTP verbs: `POST /api/v1/bank-users` (not `/register`), `PATCH /api/v1/bank-users/{id}` (not `/update/{id}`).

| Severity | Effort |
|---|---|
| Medium | Medium |

### 5.2 No Pagination Metadata in Responses

**Gap:** List endpoints accept `Pageable` but return `List<T>`, discarding pagination metadata (total elements, total pages, current page). Clients have no way to implement proper pagination.

| Severity | Effort |
|---|---|
| High | Small |

### 5.3 No API Versioning Strategy

**Gap:** All endpoints use `/api/v1/` but there is no documented versioning strategy, header-based versioning, or media-type versioning. The path prefix is hardcoded.

| Severity | Effort |
|---|---|
| Low | Small |

### 5.4 OpenAPI/Swagger Annotations Are Minimal

**Gap:** While `springdoc-openapi` is included as a dependency and basic `@Tag` and `@Operation` annotations exist on controllers, there are no `@ApiResponse`, `@Parameter`, or `@Schema` annotations. Request/response DTOs lack documentation. The Swagger UI will show limited information.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 5.5 Inconsistent Use of HTTP Methods

**Gap:** User update uses `PATCH` (correct for partial updates) but the gateway configuration and Postman collection may not reflect this. The `UserUpdateRequest` only carries a `status` field, which is fine for PATCH semantics.

| Severity | Effort |
|---|---|
| Low | Small |

### 5.6 No Filtering or Search Capabilities

**Gap:** List endpoints only support pagination via Spring `Pageable`. There are no query parameters for filtering (e.g., by status, date range, account number).

| Severity | Effort |
|---|---|
| Medium | Medium |

---

## 6. Observability

### 6.1 Inconsistent Logging

**Gap:**
- Some controllers use `@Slf4j` and log on entry; others do not (utility-payment controller has no `@Slf4j` annotation).
- No structured logging format (JSON). Logs are unstructured text.
- No correlation ID logging (the tracing IDs from Micrometer/Brave are not explicitly included in log patterns).
- No log-level configuration visible in the application configs.

| Severity | Effort |
|---|---|
| Medium | Small |

### 6.2 Health Check Endpoints Not Customized

**Gap:** Spring Boot Actuator is included in all services, providing `/actuator/health`. However:
- No custom health indicators for critical dependencies (MySQL connectivity, Keycloak availability, Config Server).
- Health endpoint exposure and detail level are not configured.
- No readiness/liveness probe differentiation for Kubernetes deployments.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 6.3 No Metrics Endpoints or Dashboards

**Gap:** While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are present, there is no Prometheus scrape endpoint configuration (`/actuator/prometheus`) and no Grafana dashboards. The README mentions Prometheus but no Prometheus configuration exists.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 6.4 Zipkin Tracing Configuration Not Visible

**Gap:** Zipkin dependencies are present, but the actual tracing configuration (sampling rate, endpoint URL) is externalized in the Config Server's Git repository and not visible in this codebase. There is no way to verify the tracing coverage without accessing the external config repo.

| Severity | Effort |
|---|---|
| Low | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap:** There are no circuit breaker implementations (e.g., Resilience4j, Spring Cloud Circuit Breaker) on any Feign client calls. If the core-banking-service becomes unavailable, all dependent services will block on Feign timeouts and eventually exhaust their thread pools.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 7.2 No Retry Policies

**Gap:** No retry configuration exists on Feign clients. Transient network failures will immediately fail the request. Spring Retry or Resilience4j Retry is not configured.

| Severity | Effort |
|---|---|
| High | Small |

### 7.3 No Timeout Configuration

**Gap:** Feign client timeouts are not explicitly configured. Default timeouts may be too long (or infinite in some configurations), leading to thread starvation under load.

| Severity | Effort |
|---|---|
| High | Small |

### 7.4 No Fallback Behavior

**Gap:** When the core-banking-service call fails, there is no graceful degradation. The fund-transfer service saves a `PENDING` record but never transitions it to `FAILED`, leaving orphaned records.

| Severity | Effort |
|---|---|
| High | Medium |

### 7.5 No Idempotency for Financial Transactions

**Gap:** Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a failed request (e.g., due to network timeout), the same transfer could be processed multiple times, leading to double-debits.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 7.6 Balance Calculation Bug

**Gap:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, the `availableBalance` is computed incorrectly:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(amount));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` was already decremented, resulting in a double-subtraction. For a 100,000 balance with a 10,000 transfer, `actualBalance` becomes 90,000 and `availableBalance` becomes 80,000.

| Severity | Effort |
|---|---|
| Critical | Small |

### 7.7 Non-Atomic Transaction Processing

**Gap:** In `TransactionService.internalFundTransfer()`, the debit and credit operations use separate `bankAccountRepository.save()` calls. While the method is `@Transactional`, the `TransactionEntity` to `BankAccountEntity` relationship is `@OneToOne(cascade = CascadeType.ALL)`, which could cause unexpected cascading behavior. Additionally, the same `transactionId` is used for both the debit and credit transaction records but with different `referenceNumber` values.

| Severity | Effort |
|---|---|
| High | Medium |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | Generic catch-all returns 400 + leaks stack trace | Error Handling | Critical | Small |
| 2.3 | No Feign error handling in fund-transfer/utility-payment | Error Handling | Critical | Medium |
| 4.2 | No input validation on any DTO | Security | Critical | Medium |
| 4.6 | No authorization enforcement in downstream services | Security | Critical | Large |
| 7.1 | No circuit breakers on Feign clients | Resilience | Critical | Medium |
| 7.5 | No idempotency for financial transactions | Resilience | Critical | Medium |
| 7.6 | Balance calculation double-subtraction bug | Resilience | Critical | Small |
| 1.2 | Duplicated code across services (no shared library) | Code Organization | High | Medium |
| 2.2 | EntityNotFoundException returns 400 instead of 404 | Error Handling | High | Small |
| 3.1 | Tests only in core-banking-service | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 4.4 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.5 | Sensitive data logging (passwords, account numbers) | Security | High | Small |
| 4.7 | Stack trace exposure in error responses | Security | High | Small |
| 5.2 | No pagination metadata in list responses | API Design | High | Small |
| 7.2 | No retry policies on Feign clients | Resilience | High | Small |
| 7.3 | No timeout configuration on Feign clients | Resilience | High | Small |
| 7.4 | No fallback behavior for failed Feign calls | Resilience | High | Medium |
| 7.7 | Non-atomic transaction processing concerns | Resilience | High | Medium |
| 1.1 | No multi-module Gradle build | Code Organization | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Medium | Medium |
| 3.3 | No contract tests between services | Testing | Medium | Large |
| 3.4 | No test config for config server dependency | Testing | Medium | Small |
| 4.3 | Hardcoded credentials in Docker Compose | Security | Medium | Small |
| 5.1 | Inconsistent URI naming | API Design | Medium | Medium |
| 5.4 | Minimal OpenAPI/Swagger annotations | API Design | Medium | Medium |
| 5.6 | No filtering or search capabilities | API Design | Medium | Medium |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | Health checks not customized | Observability | Medium | Medium |
| 6.3 | No metrics endpoints or dashboards | Observability | Medium | Medium |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 2.4 | Raw ResponseEntity without type parameters | Error Handling | Low | Small |
| 4.1 | CSRF disabled (acceptable but undocumented) | Security | Low | Small |
| 5.3 | No API versioning strategy documented | API Design | Low | Small |
| 5.5 | Inconsistent HTTP method usage | API Design | Low | Small |
| 6.4 | Zipkin config not in-repo | Observability | Low | Small |

**Totals:** 7 Critical, 12 High, 12 Medium, 6 Low
