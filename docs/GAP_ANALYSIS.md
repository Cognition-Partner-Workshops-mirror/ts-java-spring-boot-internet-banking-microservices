# Engineering Standards Gap Analysis

> Codebase: Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## 1. Code Organization

### 1.1 No Root Multi-Project Build

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

Each service maintains its own standalone Gradle wrapper and `build.gradle` with duplicated plugin versions, Spring Boot/Cloud versions, and dependency declarations. There is no root `settings.gradle` or `build.gradle` to unify version management or run cross-service tasks.

**Evidence:** All 7 services independently declare `version '3.2.4'` for Spring Boot and `"2023.0.0"` for Spring Cloud.

### 1.2 Duplicated Code Across Services

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

Significant code duplication exists across services with no shared library:

- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` — copy-pasted in `core-banking-service`, `fund-transfer-service`, `user-service`, `utility-payment-service` with minor inconsistencies (e.g. fund-transfer uses constructor-based `ErrorResponse`, others use `@Builder`).
- `AuditAware` — duplicated in `fund-transfer-service`, `user-service`, `utility-payment-service`.
- `BaseMapper` — duplicated in `core-banking-service`, `fund-transfer-service`, `user-service`, `utility-payment-service`.
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — duplicated in `fund-transfer-service`, `user-service`, `utility-payment-service`.
- `CustomFeignClientConfiguration` — duplicated in `fund-transfer-service`, `user-service`, `utility-payment-service`.

**Note:** The environment blueprint references a `banking-common` shared library, but it does not exist in the repository.

### 1.3 Inconsistent Package Structure

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

- `fund-transfer-service` places its repository under `model.repository`, while `utility-payment-service` uses `repository` (top-level).
- `user-service` uses `model.rest.response` for Feign response DTOs, while `fund-transfer-service` uses `model.dto.response`.
- `utility-payment-service` places request DTOs under `model.rest.request` while `fund-transfer-service` uses `model.dto.request`.

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

Every `GlobalExceptionHandler` maps all exceptions — including `EntityNotFoundException` and generic `Exception` — to `400 Bad Request`. This is semantically incorrect:

- `EntityNotFoundException` should return `404 Not Found`.
- Unhandled exceptions should return `500 Internal Server Error`.
- `InsufficientFundsException` could arguably be `422 Unprocessable Entity`.

**Evidence:** All 4 services have identical handler: `return ResponseEntity.badRequest().body(...)`.

### 2.2 Inconsistent Error Response Format

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

- The `SimpleBankingGlobalException` handler returns a structured `ErrorResponse` (code + message).
- The generic `Exception` handler returns a raw string: `"Exception occur inside API " + e` — which leaks stack traces and internal class names to clients.

### 2.3 No Validation Error Handling

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

There is no `MethodArgumentNotValidException` handler. Since there are no `@Valid` annotations on request bodies either, invalid input is silently accepted (see Section 4.1).

### 2.4 Swallowed Exception in Keycloak Integration

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

In `KeycloakUserService.readUser()`, all exceptions are caught with a generic `catch (Exception e)` and re-thrown as `EntityNotFoundException`. This masks Keycloak connectivity issues, auth failures, etc.

---

## 3. Testing

### 3.1 Only core-banking-service Has Unit Tests

| Severity | Effort |
|----------|--------|
| **Critical** | **Large** |

- `core-banking-service` has 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with ~15 test methods covering key service logic.
- `fund-transfer-service`, `user-service`, `utility-payment-service` have **zero** service/controller unit tests — only empty Spring Boot application context tests.
- `api-gateway`, `config-server`, `service-registry` only have boilerplate `ApplicationTests` (context load).

### 3.2 No Integration Tests

| Severity | Effort |
|----------|--------|
| **High** | **Large** |

No `@SpringBootTest` integration tests, no Testcontainers usage, no `@WebMvcTest` controller tests. Feign client interactions are completely untested.

### 3.3 No Contract Tests

| Severity | Effort |
|----------|--------|
| **High** | **Large** |

No Spring Cloud Contract or Pact tests to validate the API contracts between:
- `fund-transfer-service` ↔ `core-banking-service`
- `utility-payment-service` ↔ `core-banking-service`
- `user-service` ↔ `core-banking-service`

### 3.4 Test Configuration Incomplete

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

- `core-banking-service` test config sets `flyway.enabled: false` and `ddl-auto: none`, so H2 tables aren't created — preventing `@DataJpaTest` or integration tests from working.
- Other services have minimal test `application.yml` files with just datasource config.

---

## 4. Security

### 4.1 No Input Validation

| Severity | Effort |
|----------|--------|
| **Critical** | **Small** |

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Jakarta Bean Validation annotations on any request DTO or controller parameter across all services.

**Risk:** Negative transfer amounts, null account numbers, empty emails, and zero-length strings are all accepted without error.

### 4.2 Hardcoded Credentials in Source Code

| Severity | Effort |
|----------|--------|
| **Critical** | **Small** |

- `docker-compose.yml` contains plaintext MySQL root password: `woVERANKliGharym`
- `docker-compose.yml` contains plaintext Keycloak admin password: `password`
- `privileges.sql` contains plaintext DB user password: `oPItyPticIAt`
- `README.md` contains test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.3 Missing CSRF Protection Analysis

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

CSRF is explicitly disabled in the API Gateway (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). This is acceptable for a pure API (JWT-based, no browser cookies), but should be documented as a conscious decision.

### 4.4 No Rate Limiting

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

The API Gateway has no rate limiting configuration. Financial APIs are high-value targets for abuse.

### 4.5 Keycloak Singleton Anti-Pattern

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

`KeycloakProperties` uses a static singleton for the Keycloak admin client instance. This is not thread-safe during initialization and prevents token refresh on credential rotation.

### 4.6 No Dependency Vulnerability Scanning

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

No OWASP Dependency Check, Snyk, or similar vulnerability scanning configured in any `build.gradle`.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

Most controller methods return raw `ResponseEntity` without generic type (e.g. `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This prevents OpenAPI from generating accurate response schemas.

**Exception:** `internet-banking-user-service` correctly types its responses as `ResponseEntity<User>` and `ResponseEntity<List<User>>`.

### 5.2 No API Versioning Strategy

| Severity | Effort |
|----------|--------|
| **Low** | **Medium** |

All endpoints use `/api/v1/` but there is no mechanism for version negotiation, deprecation headers, or running multiple versions concurrently.

### 5.3 Pagination Response Missing Metadata

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

Paginated endpoints (e.g. `GET /api/v1/transfer`, `GET /api/v1/bank-users`) return a raw `List<T>` instead of a page wrapper with `totalElements`, `totalPages`, `pageNumber`, `pageSize`. Clients cannot determine if more pages exist.

### 5.4 Wrong OpenAPI Dependency

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

Services use `springdoc-openapi-starter-webflux-ui:2.1.0`, but `core-banking-service`, `fund-transfer-service`, `user-service`, and `utility-payment-service` are **servlet-based** (Spring MVC), not WebFlux. The correct dependency is `springdoc-openapi-starter-webmvc-ui`. This may cause Swagger UI issues.

### 5.5 Inconsistent Resource Naming

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

- `core-banking-service` uses `/api/v1/account/bank-account/` and `/api/v1/account/util-account/`
- Path variables are inconsistent: `{account_number}` (snake_case) vs `{identification}` (camelCase) vs `{id}` (short)

### 5.6 No OpenAPI Specification File

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

While `@Tag` and `@Operation` annotations are present on controllers, there is no generated or committed `openapi.yaml` / `openapi.json` file. The Swagger UI dependency issue (5.4) may prevent runtime generation from working correctly.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

- Most controllers log incoming requests but use `toString()` on request objects, which may leak sensitive data (e.g. passwords in `User.toString()`).
- No structured logging (JSON format) configured — default Spring Boot console output.
- No MDC correlation ID setup beyond Micrometer tracing.

### 6.2 No Custom Health Checks

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

Services depend on Spring Boot Actuator's default health endpoint but do not define custom health indicators for critical dependencies (MySQL connectivity, Keycloak reachability, Feign client target availability).

### 6.3 No Metrics Endpoints Beyond Default

| Severity | Effort |
|----------|--------|
| **Low** | **Medium** |

- `spring-boot-starter-actuator` is included but no Prometheus endpoint (`/actuator/prometheus`) is configured.
- No custom business metrics (e.g. transfer count, payment volume, error rates).
- README mentions Prometheus in the tech stack but it is not present in any configuration or Docker Compose.

### 6.4 Distributed Tracing Coverage Incomplete

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

- Zipkin integration is configured with `micrometer-tracing-bridge-brave` across all services.
- `feign-micrometer` is included for span propagation across Feign calls.
- However, `config-server` and `service-registry` lack tracing dependencies, creating gaps in the trace chain.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| **Critical** | **Medium** |

No Resilience4j or Spring Cloud Circuit Breaker dependency in any service. If `core-banking-service` goes down:
- `fund-transfer-service` will block on Feign calls and propagate `500` errors to clients.
- `utility-payment-service` will behave identically.
- `user-service` will fail on registration flows.

Cascading failures across services are unmitigated.

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

No Spring Retry or Resilience4j retry configuration for Feign clients. Transient network failures between services will result in immediate failure.

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

No explicit Feign client timeout, connection timeout, or read timeout configured. Default Feign timeouts (10 seconds connect, 60 seconds read) are used, which are too generous for a banking application and could tie up threads.

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

No `@FallbackFactory` or fallback classes on any `@FeignClient`. When a downstream service is unavailable, clients receive raw Feign error responses rather than meaningful degraded responses.

### 7.5 No Idempotency Protection

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

Fund transfer and utility payment operations are not idempotent:
- No idempotency key in request headers or bodies.
- No duplicate detection before processing.
- A retry (by client or infrastructure) will create duplicate transactions and double-deduct balances.

### 7.6 Non-Atomic Transaction Processing

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

In `FundTransferService.fundTransfer()`:
1. A local `FundTransferEntity` is saved with `PENDING` status.
2. The Feign call to core-banking is made.
3. On success, the local record is updated to `SUCCESS`.

If the application crashes between steps 2 and 3, the core banking transaction is completed but the local record remains `PENDING`. There is no compensation or reconciliation mechanism. The same pattern exists in `UtilityPaymentService`.

---

## Summary Table

| # | Category | Gap | Severity | Effort |
|---|----------|-----|----------|--------|
| 1.1 | Code Organization | No root multi-project build | Medium | Medium |
| 1.2 | Code Organization | Duplicated code across services (no shared library) | High | Medium |
| 1.3 | Code Organization | Inconsistent package structure | Low | Small |
| 2.1 | Error Handling | All exceptions return HTTP 400 | High | Small |
| 2.2 | Error Handling | Inconsistent error response format | Medium | Small |
| 2.3 | Error Handling | No validation error handling | High | Small |
| 2.4 | Error Handling | Swallowed exception in Keycloak integration | Medium | Small |
| 3.1 | Testing | Only core-banking-service has unit tests | Critical | Large |
| 3.2 | Testing | No integration tests | High | Large |
| 3.3 | Testing | No contract tests | High | Large |
| 3.4 | Testing | Test configuration incomplete | Medium | Small |
| 4.1 | Security | No input validation | Critical | Small |
| 4.2 | Security | Hardcoded credentials in source code | Critical | Small |
| 4.3 | Security | CSRF disabled (acceptable but undocumented) | Low | Small |
| 4.4 | Security | No rate limiting | Medium | Medium |
| 4.5 | Security | Keycloak singleton anti-pattern | Medium | Small |
| 4.6 | Security | No dependency vulnerability scanning | Medium | Small |
| 5.1 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| 5.2 | API Design | No API versioning strategy | Low | Medium |
| 5.3 | API Design | Pagination response missing metadata | Medium | Small |
| 5.4 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| 5.5 | API Design | Inconsistent resource naming | Low | Small |
| 5.6 | API Design | No generated OpenAPI specification file | Low | Small |
| 6.1 | Observability | Inconsistent logging / sensitive data in logs | Medium | Small |
| 6.2 | Observability | No custom health checks | Medium | Small |
| 6.3 | Observability | No metrics endpoints beyond default | Low | Medium |
| 6.4 | Observability | Distributed tracing coverage incomplete | Low | Small |
| 7.1 | Resilience | No circuit breakers | Critical | Medium |
| 7.2 | Resilience | No retry policies | High | Medium |
| 7.3 | Resilience | No timeout configuration | High | Small |
| 7.4 | Resilience | No fallback behavior | Medium | Medium |
| 7.5 | Resilience | No idempotency protection | High | Medium |
| 7.6 | Resilience | Non-atomic transaction processing | High | Medium |
