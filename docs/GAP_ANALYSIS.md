# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven dimensions. Each gap is rated by severity and estimated remediation effort.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is a fully independent Gradle project with its own `settings.gradle`, Gradle wrapper, and duplicated plugin configuration. There is no root-level `settings.gradle` or `build.gradle` to unify the build.

**Impact**: Inconsistent dependency versions across services, duplicated build logic, inability to run a single `./gradlew build` across the whole project.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted identically across 3+ services with no shared library:

| Class | Duplicated In |
|---|---|
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` | user-service, fund-transfer, utility-payment |
| `AuditorAwareConfig` / `AuditConfig` | user-service, fund-transfer, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment (slightly different in each) |
| `ErrorResponse` | core-banking, fund-transfer, utility-payment |
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |

**Impact**: Bug fixes must be applied to every copy. Divergent implementations emerge over time (already visible in `GlobalExceptionHandler` variations).

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- core-banking uses `com.javatodev.finance.repository` while other services use `com.javatodev.finance.model.repository`.
- DTO/request/response classes are organized differently: core-banking uses `model.dto.request/response`, user-service uses `model.rest.response`, utility-payment uses `model.rest.request/response`.

### 1.4 Mapper Classes Are Not Spring Beans

**Severity: Low | Effort: Small**

All mappers (e.g. `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are instantiated with `new` rather than managed by Spring. They are assigned to non-final fields in `@RequiredArgsConstructor` services, which means the `@RequiredArgsConstructor` pattern is inconsistently applied (Lombok generates the constructor only for `final` fields).

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

**Severity: High | Effort: Small**

- `GlobalExceptionHandler` for generic `Exception.class` returns a raw string: `"Exception occur inside API " + e` in fund-transfer and utility-payment services.
- core-banking-service's handler returns a structured `ErrorResponse` for `SimpleBankingGlobalException` but returns inconsistent responses for other exceptions.
- user-service has a richer exception hierarchy (`InvalidEmailException`, `UserAlreadyRegisteredException`, `InvalidBankingUserException`, `EntityNotFoundException`) but these are not shared with other services.

**Impact**: API consumers cannot rely on a consistent error schema. Raw exception messages leak internal details.

### 2.2 Missing HTTP Status Code Differentiation

**Severity: Medium | Effort: Small**

All exceptions return `400 Bad Request` regardless of the actual error:
- `EntityNotFoundException` should return `404 Not Found`.
- `InsufficientFundsException` could return `422 Unprocessable Entity`.
- Generic `Exception` catch-all should return `500 Internal Server Error`.

### 2.3 Exception Information Leakage

**Severity: High | Effort: Small**

The catch-all handler `"Exception occur inside API " + e` exposes full stack traces and internal class names to API consumers. This is a security concern.

### 2.4 Raw `ResponseEntity` Without Generics

**Severity: Low | Effort: Small**

Most controller methods return `ResponseEntity` without type parameters (e.g. `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This produces warnings and reduces type safety and Swagger documentation accuracy.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Meaningful Tests | Coverage |
|---|---|---|
| core-banking-service | 15 unit tests (AccountService, TransactionService, UserService) | Service layer only |
| internet-banking-user-service | 0 (only `contextLoads()`) | None |
| internet-banking-fund-transfer-service | 0 (only `contextLoads()`) | None |
| internet-banking-utility-payment-service | 0 (only `contextLoads()`) | None |
| internet-banking-api-gateway | 0 (only `contextLoads()`) | None |
| internet-banking-config-server | 0 (only `contextLoads()`) | None |
| internet-banking-service-registry | 0 (only `contextLoads()`) | None |

**Impact**: No safety net for regressions. The fund-transfer and utility-payment orchestration logic is entirely untested.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No tests verify the actual HTTP layer (controllers), database queries (repositories), or Feign client communication. The `contextLoads()` tests fail without full infrastructure (Eureka, Config Server, Keycloak) unless the test profile disables them.

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

With 3 Feign clients calling core-banking-service, there are no consumer-driven contract tests (e.g. Spring Cloud Contract or Pact) to verify API compatibility.

### 3.4 No Test Coverage Reporting

**Severity: Low | Effort: Small**

No JaCoCo or similar code coverage plugin is configured.

---

## 4. Security

### 4.1 Hard-Coded Credentials in Source Code

**Severity: Critical | Effort: Small**

| Location | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | Keycloak DB password: `password` |
| `docker-compose/mysql/privileges.sql` | App DB password: `oPItyPticIAt` |
| `user-service test application.yml` | Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` |
| `README.md` | Test user credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact**: Credentials committed to version control. Anyone with repo access has full admin access to all infrastructure.

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No request DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). Endpoints accept any payload without validation:

- `FundTransferRequest`: `amount` could be null, zero, or negative.
- `UtilityPaymentRequest`: `providerId`, `amount`, `referenceNumber` are all unchecked.
- `User` (registration): `email`, `identification`, `password` have no format or length constraints.

### 4.3 No Rate Limiting

**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting. The user registration endpoint (`/user/api/v1/bank-users/register`) is publicly accessible without authentication and has no protection against brute force or abuse.

### 4.4 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF protection is disabled in the API Gateway's `SecurityConfiguration`. While acceptable for a pure API backend, it should be documented.

### 4.5 Keycloak Client Uses Static Singleton

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static field. This instance never refreshes tokens and is not thread-safe during initialization (classic double-checked locking issue without `volatile`).

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured. The project uses `springdoc-openapi-starter-webflux-ui:2.1.0` which may have known vulnerabilities (version is from mid-2023).

---

## 5. API Design

### 5.1 Inconsistent URL Naming

**Severity: Low | Effort: Small**

- core-banking: `/api/v1/account/bank-account/{account_number}` (uses underscores in path variable).
- core-banking: `/api/v1/account/util-account/{account_name}` (abbreviation `util`).
- user-service: `/api/v1/bank-users/register` and `/api/v1/bank-users/update/{id}`.
- `PATCH /update/{id}` is not RESTful — should be `PATCH /api/v1/bank-users/{id}`.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Small**

All APIs use `/api/v1/` but there is no documented versioning strategy or mechanism for introducing v2 endpoints.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Paginated endpoints (users, fund transfers, utility payments) accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>`, losing total count, total pages, and navigation metadata.

### 5.4 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

While Spring Data's `Pageable` supports `sort` parameters, there is no documentation of which fields are sortable or filterable.

### 5.5 OpenAPI Spec Uses Wrong Starter

**Severity: Medium | Effort: Small**

All services (except the gateway) are Spring MVC (servlet-based) but use `springdoc-openapi-starter-webflux-ui` (the WebFlux variant). The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause runtime conflicts or incorrect Swagger UI behavior.

---

## 6. Observability

### 6.1 No Structured Logging

**Severity: Medium | Effort: Medium**

All services use default Spring Boot logging with unstructured text format. Log messages use string concatenation in some places (e.g. `"Sending fund transfer request {}" + request.toString()`), mixing SLF4J parameterized logging with string concatenation.

### 6.2 Sensitive Data in Logs

**Severity: High | Effort: Small**

Controllers log full request objects: `log.info("Creating user with {}", request.toString())` which may include passwords. `log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())` logs financial transaction details.

### 6.3 No Custom Health Checks

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but no custom health indicators check critical dependencies (database connectivity, Keycloak availability, Feign client reachability).

### 6.4 No Metrics Endpoints

**Severity: Medium | Effort: Small**

README mentions Prometheus but no Prometheus dependencies (`micrometer-registry-prometheus`) are included in any `build.gradle`. No custom business metrics are defined.

### 6.5 Incomplete Distributed Tracing

**Severity: Low | Effort: Small**

Tracing dependencies are included, but there is no configuration visible for sampling rate, custom span names, or baggage propagation. The config-server and service-registry do not include tracing dependencies.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service calls use plain Feign clients with no circuit breaker. If core-banking-service goes down, all dependent services will block on HTTP timeouts and eventually exhaust their thread pools.

**Impact**: A single service failure cascades into a full system outage.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No Feign retry configuration or Spring Retry is configured. Transient network failures result in immediate request failure.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit connection or read timeouts are configured for Feign clients. Default timeouts may be excessively long (or infinite), tying up threads during downstream failures.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback responses are defined for any Feign client. When core-banking-service is unavailable, the fund-transfer and utility-payment services propagate the raw Feign exception.

### 7.5 No Transaction Rollback on Partial Failure

**Severity: Critical | Effort: Medium**

The fund-transfer-service creates a local `FundTransferEntity` (status `PENDING`), then calls core-banking. If the core-banking call succeeds but the subsequent local update fails, the local record remains `PENDING` while the money has already moved. There is no compensation/saga pattern to handle this distributed transaction inconsistency.

Similarly, in `TransactionService.utilPayment()`, the `fromAccount` balance is set to `actualBalance - amount` and then `availableBalance` is set to the _already-reduced_ `actualBalance - amount` again (double deduction bug), and there is no rollback if the transaction record save fails.

### 7.6 No Bulkhead Isolation

**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow response from one endpoint (e.g. fund-transfer) could exhaust threads and block unrelated requests (e.g. utility payments).

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|---|---|---|---|
| 4.1 | Hard-coded credentials in source | Critical | Small | Security |
| 4.2 | No input validation | Critical | Medium | Security |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.5 | No transaction rollback on partial failure | Critical | Medium | Resilience |
| 3.1 | Minimal test coverage | Critical | Large | Testing |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 2.1 | Inconsistent error response format | High | Small | Error Handling |
| 2.3 | Exception information leakage | High | Small | Error Handling |
| 3.2 | No integration tests | High | Large | Testing |
| 6.2 | Sensitive data in logs | High | Small | Observability |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 2.2 | Missing HTTP status code differentiation | Medium | Small | Error Handling |
| 3.3 | No contract tests | Medium | Large | Testing |
| 4.3 | No rate limiting | Medium | Medium | Security |
| 4.5 | Keycloak singleton thread-safety | Medium | Small | Security |
| 4.6 | No dependency vulnerability scanning | Medium | Small | Security |
| 5.3 | No pagination metadata | Medium | Small | API Design |
| 5.5 | Wrong OpenAPI starter (webflux vs webmvc) | Medium | Small | API Design |
| 6.1 | No structured logging | Medium | Medium | Observability |
| 6.4 | No Prometheus metrics | Medium | Small | Observability |
| 7.4 | No fallback behavior | Medium | Medium | Resilience |
| 7.6 | No bulkhead isolation | Medium | Medium | Resilience |
| 1.1 | No multi-project Gradle build | Medium | Medium | Code Organization |
| 1.3 | Inconsistent package structure | Low | Small | Code Organization |
| 1.4 | Mappers not Spring beans | Low | Small | Code Organization |
| 2.4 | Raw ResponseEntity without generics | Low | Small | Error Handling |
| 3.4 | No test coverage reporting | Low | Small | Testing |
| 4.4 | CSRF disabled without documentation | Low | Small | Security |
| 5.1 | Inconsistent URL naming | Low | Small | API Design |
| 5.2 | No versioning strategy | Low | Small | API Design |
| 5.4 | No filtering/sorting documentation | Low | Small | API Design |
| 6.3 | No custom health checks | Low | Small | Observability |
| 6.5 | Incomplete distributed tracing | Low | Small | Observability |
