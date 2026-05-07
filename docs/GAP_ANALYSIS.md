# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Severity | Effort |
|---|---|
| Medium | Small |

Each service has an independent `build.gradle` and its own Gradle wrapper. There is no root-level `settings.gradle` or `build.gradle` to manage shared dependency versions, plugins, or common configuration. This leads to duplicated dependency declarations and version drift risk.

**Evidence:** All seven services independently declare `springCloudVersion = "2023.0.0"`, `spring-boot:3.2.4`, and identical tracing/config dependencies.

### 1.2 Duplicated Code Across Services

| Severity | High |
|---|---|
| High | Medium |

The following classes are copy-pasted across multiple services with minor variations:

- `AppAuthUserFilter` — identical in user-service, fund-transfer-service, utility-payment-service
- `ApiRequestContext` / `ApiRequestContextHolder` — identical across three services
- `AuditAware` / `AuditConfig` / `AuditorAwareConfig` — identical across three services
- `BaseMapper` — identical across all four business services
- `GlobalExceptionHandler` — nearly identical across all four business services (minor formatting differences)
- `ErrorResponse` — duplicated across all services
- `SimpleBankingGlobalException` — duplicated across all services

**Impact:** Bug fixes must be applied to every copy. Inconsistencies are already present (e.g., `ErrorResponse` uses `@Builder` in core-banking but constructor in fund-transfer).

### 1.3 Inconsistent Package Structure

| Severity | Low |
|---|---|
| Low | Small |

Package organization varies between services:
- Core banking: `repository/` at top level
- User service: `model/repository/`
- Fund transfer: `model/repository/`
- Utility payment: `repository/` at top level

Feign clients are located in different sub-packages (`service/rest/`, `service/rest/client/`).

### 1.4 DTO/Entity Separation Inconsistencies

| Severity | Medium |
|---|---|
| Medium | Small |

`AuditAware` is placed in `model/dto/` but annotated with `@MappedSuperclass` and `@EntityListeners` — it is effectively a JPA base entity, not a DTO. The `User` DTO in the user-service extends `AuditAware`, mixing persistence annotations into the DTO layer.

### 1.5 Mapper Instantiation Anti-Pattern

| Severity | Low |
|---|---|
| Low | Small |

Mappers are instantiated inline (`private UserMapper userMapper = new UserMapper()`) rather than injected as Spring beans. This makes them harder to test and violates dependency injection conventions.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns 400 for All Errors

| Severity | Critical |
|---|---|
| Critical | Small |

Every `GlobalExceptionHandler` has a catch-all `@ExceptionHandler({Exception.class})` that returns `400 Bad Request` with the raw exception `.toString()`:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Problems:**
- 500-class errors (NPE, DB connection failures) are returned as 400.
- Raw exception details (including stack traces, internal class names) are exposed to clients — information disclosure vulnerability.
- No structured error response for the generic handler.

### 2.2 Inconsistent Error Response Structures

| Severity | High |
|---|---|
| High | Small |

- `SimpleBankingGlobalException` handler returns `ErrorResponse{code, message}`.
- Generic `Exception` handler returns a raw `String`.
- Clients cannot rely on a uniform error envelope.

### 2.3 Missing HTTP Status Code Differentiation

| Severity | High |
|---|---|
| High | Small |

All error responses use `400 Bad Request`:
- `EntityNotFoundException` should return `404`.
- `InsufficientFundsException` could return `422 Unprocessable Entity`.
- `UserAlreadyRegisteredException` could return `409 Conflict`.
- Server errors should return `500`.

### 2.4 No Feign Error Decoding in Most Services

| Severity | Medium |
|---|---|
| Medium | Small |

Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services use `CustomFeignClientConfiguration` that only sets the log level — Feign errors from core-banking are not properly decoded or translated into domain exceptions.

### 2.5 No Request Validation

| Severity | Critical |
|---|---|
| Critical | Medium |

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, or `@Size` annotations exist on any request DTOs or controller method parameters. A fund transfer with a `null` amount, negative amount, or blank account number will pass through to the service layer.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Severity | Critical |
|---|---|
| Critical | Large |

| Service | Unit Tests | Notes |
|---|---|---|
| core-banking-service | 3 test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) | ~20 test methods covering service layer |
| internet-banking-user-service | 1 empty context-load test | No business logic tests |
| internet-banking-fund-transfer-service | 1 empty context-load test | No business logic tests |
| internet-banking-utility-payment-service | 1 empty context-load test | No business logic tests |
| internet-banking-api-gateway | 1 empty context-load test | No security config tests |
| internet-banking-config-server | 1 empty context-load test | N/A (infrastructure) |
| internet-banking-service-registry | 1 empty context-load test | N/A (infrastructure) |

### 3.2 No Integration Tests

| Severity | High |
|---|---|
| High | Large |

No `@SpringBootTest` with real database interactions, no `@WebMvcTest` controller tests, no Testcontainers for MySQL or Keycloak. The existing tests mock everything, so integration issues (serialization, JPA queries, Flyway migrations) are untested.

### 3.3 No Contract Tests

| Severity | High |
|---|---|
| High | Large |

No Spring Cloud Contract or Pact tests between services. Feign client contracts with core-banking are validated only at runtime. Breaking changes to core-banking APIs would not be caught until deployment.

### 3.4 No Test Coverage Tooling

| Severity | Medium |
|---|---|
| Medium | Small |

No JaCoCo or equivalent coverage plugin configured. No coverage thresholds enforced.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Severity | Critical |
|---|---|
| Critical | Small |

Multiple passwords and credentials are committed to the repository:

- `docker-compose.yml`: MySQL root password `woVERANKliGharym`, Keycloak admin password `password`, Keycloak DB password `password`
- `privileges.sql`: App DB user password `oPItyPticIAt`
- `README.md`: Test user credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.2 CSRF Disabled

| Severity | Medium |
|---|---|
| Medium | Small |

CSRF is disabled in the API Gateway's `SecurityConfiguration`: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for stateless JWT APIs, this should be explicitly documented and justified.

### 4.3 No Input Validation (Cross-reference with 2.5)

| Severity | Critical |
|---|---|
| Critical | Medium |

No Bean Validation annotations on any request DTOs. This creates risks for SQL injection (mitigated by JPA parameterized queries), XSS, and business logic bypass (e.g., transferring negative amounts).

### 4.4 No Rate Limiting

| Severity | High |
|---|---|
| High | Medium |

No rate limiting configured at the API Gateway or service level. Financial APIs (fund transfer, payments) are vulnerable to abuse.

### 4.5 Keycloak Singleton Anti-Pattern

| Severity | Medium |
|---|---|
| Medium | Small |

`KeycloakProperties` uses a static singleton for the Keycloak client instance. This is not thread-safe (double-checked locking is missing) and prevents proper lifecycle management and token refresh.

### 4.6 No Dependency Vulnerability Scanning

| Severity | High |
|---|---|
| High | Small |

No OWASP Dependency Check, Snyk, or equivalent plugin configured in any Gradle build.

### 4.7 Downstream Services Not Authenticated

| Severity | High |
|---|---|
| High | Medium |

Core Banking Service has no Spring Security dependency and no authentication. Any network-accessible client can call its APIs directly, bypassing the API Gateway's JWT validation entirely. The only trust signal is the `X-Auth-Id` header, which is trivially spoofable.

---

## 5. API Design

### 5.1 Raw ResponseEntity Without Generics

| Severity | Medium |
|---|---|
| Medium | Small |

Most controller methods return `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This weakens compile-time safety and degrades OpenAPI spec generation.

### 5.2 No API Versioning Strategy

| Severity | Low |
|---|---|
| Low | Medium |

URLs include `/v1/` but there is no mechanism for running multiple versions simultaneously, no header-based versioning, and no deprecation strategy.

### 5.3 Inconsistent URL Conventions

| Severity | Low |
|---|---|
| Low | Small |

- Mixed use of hyphens and underscores in path variables: `{account_number}` vs `{id}`
- Endpoint naming inconsistency: `/fund-transfer` vs `/util-payment` (abbreviated) vs `/utility-payment` (full name)
- `bank-account` vs `util-account` (inconsistent abbreviation)

### 5.4 No Pagination Metadata in Responses

| Severity | Medium |
|---|---|
| Medium | Small |

List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (totalElements, totalPages, current page). Clients cannot determine if more pages exist.

### 5.5 No Filtering or Sorting Parameters

| Severity | Low |
|---|---|
| Low | Medium |

List endpoints only support pagination via Spring's `Pageable`. No explicit filtering (by date, status, amount range) or custom sorting options are documented.

### 5.6 OpenAPI Dependency Mismatch

| Severity | Medium |
|---|---|
| Medium | Small |

Services use `springdoc-openapi-starter-webflux-ui` but run on Spring MVC (not WebFlux). The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause runtime issues or missing Swagger UI.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Severity | Medium |
|---|---|
| Medium | Medium |

- No structured logging (JSON format) configured — plain text logs are harder to parse in log aggregation systems.
- Sensitive data logged: `request.toString()` in controllers may log user passwords (User service's `createUser` logs the entire User DTO which contains the password field).
- No correlation IDs in log format beyond what Brave/Micrometer injects.

### 6.2 No Custom Health Checks

| Severity | Medium |
|---|---|
| Medium | Small |

While Spring Boot Actuator is included, no custom health indicators are defined. There are no health checks for critical dependencies like Keycloak connectivity, RabbitMQ availability, or inter-service Feign client reachability.

### 6.3 No Prometheus Metrics Endpoint

| Severity | Medium |
|---|---|
| Medium | Small |

Prometheus is listed in the README tech stack but `micrometer-registry-prometheus` is not included in any `build.gradle`. The `/actuator/prometheus` endpoint will not be available.

### 6.4 Incomplete Distributed Tracing

| Severity | Low |
|---|---|
| Low | Small |

Brave/Zipkin integration is present, but:
- No custom span annotations on key business methods.
- No trace IDs included in error responses for client-side debugging.
- Zipkin URL is not configurable per-service in the local configuration files (relies entirely on config server).

### 6.5 No Centralized Log Aggregation

| Severity | Medium |
|---|---|
| Medium | Medium |

No ELK/EFK stack, no Loki, no log shipping configuration. Each container logs to stdout but there is no aggregation solution defined.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Critical |
|---|---|
| Critical | Medium |

No Resilience4j or Spring Cloud Circuit Breaker dependency in any service. Feign calls to core-banking will cascade failures if core-banking is slow or unavailable. A degraded core-banking will bring down all upstream services.

### 7.2 No Retry Policies

| Severity | High |
|---|---|
| High | Small |

No Spring Retry or Resilience4j retry configuration. Transient failures (network blips, brief DB unavailability) will immediately fail requests.

### 7.3 No Timeout Configuration

| Severity | High |
|---|---|
| High | Small |

No explicit Feign timeouts, connection pool settings, or HTTP client timeout configuration. Default timeouts are often too long (or infinite), leading to thread pool exhaustion under failure conditions.

### 7.4 No Fallback Behavior

| Severity | Medium |
|---|---|
| Medium | Medium |

No `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)` defined. No graceful degradation strategy when downstream services are unavailable.

### 7.5 No Idempotency Keys for Financial Transactions

| Severity | Critical |
|---|---|
| Critical | Medium |

Fund transfers and utility payments have no idempotency mechanism. If a client retries a timed-out request (or a Feign call succeeds but the response is lost), the transaction may be executed twice. For a financial application, this is a critical gap.

### 7.6 Non-Atomic Balance Updates

| Severity | Critical |
|---|---|
| Critical | Medium |

In `TransactionService.internalFundTransfer()`:
- Two separate `bankAccountRepository.save()` calls update source and destination accounts.
- If the process fails between the debit and credit, money is lost.
- While `@Transactional` is present on the class, the dual `findByNumber` + `save` pattern is vulnerable to race conditions — no pessimistic locking or `SELECT ... FOR UPDATE` is used. Two concurrent transfers from the same account could both pass validation.

### 7.7 Incorrect Available Balance Calculation

| Severity | Critical |
|---|---|
| Critical | Small |

In `TransactionService.internalFundTransfer()` and `utilPayment()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The `availableBalance` is set by subtracting `amount` from the **already-subtracted** `actualBalance`, resulting in a double deduction of the available balance. This is a functional bug.

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 2 | 2 | 5 |
| Error Handling | 2 | 2 | 1 | 0 | 5 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 2 | 3 | 2 | 0 | 7 |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 0 | 0 | 4 | 1 | 5 |
| Resilience | 4 | 2 | 1 | 0 | 7 |
| **Total** | **9** | **10** | **14** | **6** | **39** |
