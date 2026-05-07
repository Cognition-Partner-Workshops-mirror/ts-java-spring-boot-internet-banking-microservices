# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by **severity** and **remediation effort**.

**Severity scale:**
- **Critical** — Production blocker; data loss, security vulnerability, or correctness issue
- **High** — Significant risk to reliability, maintainability, or security
- **Medium** — Deviation from best practice that impacts developer productivity or operational readiness
- **Low** — Polish item; nice-to-have improvement

**Effort scale:**
- **Small** — < 1 day per service; localized change
- **Medium** — 1–3 days; cross-cutting but straightforward
- **Large** — 3+ days; architectural change or significant refactor

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `gradlew` wrapper and `build.gradle`. There is no root `settings.gradle` or shared build configuration. This leads to:
- Duplicated dependency versions across 6 `build.gradle` files
- No single command to build/test all services
- Risk of version drift between services

**Current state:** 6 independent `build.gradle` files, each declaring `Spring Boot 3.2.4` and `Spring Cloud 2023.0.0` independently.

### GAP-ORG-02: Duplicated Code Across Services
**Severity: High | Effort: Medium**

The following classes are copy-pasted identically (or near-identically) across 3–4 services:
- `BaseMapper<E, D>` — identical in core-banking, user-service, fund-transfer, utility-payment
- `AuditAware` — identical in user-service, fund-transfer, utility-payment
- `SimpleBankingGlobalException` — identical in all 4 business services
- `ErrorResponse` — identical in all 4 business services
- `GlobalExceptionHandler` — near-identical in all 4 business services
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — identical in fund-transfer, utility-payment, user-service

**Impact:** Bug fixes or improvements must be applied in 4 places. Divergence is inevitable.

### GAP-ORG-03: Inconsistent Package Structure
**Severity: Low | Effort: Small**

- Core Banking: `repository/` package at top level
- User Service: `model/repository/` nested under model
- Fund Transfer: `model/repository/` nested under model
- Utility Payment: `repository/` at top level

Similarly, Feign clients are in:
- User Service: `service/rest/`
- Fund Transfer: `service/rest/client/`
- Utility Payment: `service/rest/`

### GAP-ORG-04: Mapper Classes Instantiated Manually
**Severity: Low | Effort: Small**

All mapper classes (`UserMapper`, `BankAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` inside service classes rather than managed as Spring beans. This prevents dependency injection and makes testing harder.

```java
// Current pattern (in every service)
private FundTransferMapper mapper = new FundTransferMapper();
```

---

## 2. Error Handling

### GAP-ERR-01: All Errors Return HTTP 400
**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` maps all exceptions — including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception` — to `400 Bad Request`. Proper HTTP semantics should use:
- `404` for `EntityNotFoundException`
- `409` or `422` for `InsufficientFundsException`
- `500` for unhandled exceptions

**Current state (all services):**
```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);  // Always 400
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(...) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);  // Always 400, leaks stack info
}
```

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details
**Severity: Critical | Effort: Small**

The catch-all `Exception` handler in all 4 services returns the full exception object as a string to the client:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```
This exposes stack traces, class names, SQL errors, and internal details to external callers.

### GAP-ERR-03: Inconsistent Error Response Format
**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` handler returns `ErrorResponse { code, message }` (structured JSON)
- Generic `Exception` handler returns a plain string
- Fund Transfer and Utility Payment services return `ErrorResponse` via `new ErrorResponse(...)` while Core Banking and User Service use `ErrorResponse.builder()...build()`

There is no consistent error envelope (e.g., timestamp, path, trace ID).

### GAP-ERR-04: No Error Handling for Feign Client Failures
**Severity: High | Effort: Medium**

- Fund Transfer and Utility Payment services have **no Feign error decoder** — if Core Banking returns an error, the raw Feign exception propagates
- Only User Service has a `CustomFeignErrorDecoder` that maps HTTP status codes to exceptions
- No circuit breaker or fallback exists on any Feign call — a Core Banking failure cascades directly

### GAP-ERR-05: Missing Raw Type Parameterization on ResponseEntity
**Severity: Medium | Effort: Small**

Almost all controller methods return raw `ResponseEntity` instead of `ResponseEntity<T>`:
```java
public ResponseEntity sendFundTransfer(...) // raw type
```
This suppresses compile-time type checking and produces warnings. Only the User Service controller uses parameterized types (`ResponseEntity<User>`, `ResponseEntity<List<User>>`).

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage for 3 of 4 Business Services
**Severity: Critical | Effort: Large**

| Service | Test Classes | Meaningful Tests |
|---|---|---|
| `core-banking-service` | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | ~15 unit tests with Mockito |
| `internet-banking-user-service` | `InternetBankingUserServiceApplicationTests` | 1 context-load test (empty, will fail without Keycloak) |
| `internet-banking-fund-transfer-service` | `InternetBankingFundTransferServiceApplicationTests` | 1 context-load test (empty) |
| `internet-banking-utility-payment-service` | `InternetBankingUtilityPaymentServiceApplicationTests` | 1 context-load test (empty) |

Only `core-banking-service` has actual unit tests. The other three services have only a `contextLoads()` test that likely fails in CI without infrastructure.

### GAP-TEST-02: No Integration Tests
**Severity: High | Effort: Large**

No integration tests exist anywhere:
- No `@SpringBootTest` with real database tests (e.g., H2 in-memory)
- No Testcontainers usage for MySQL, Keycloak, or RabbitMQ
- No API-level tests (`MockMvc`, `WebTestClient`)
- No end-to-end tests through the API Gateway

### GAP-TEST-03: No Contract Tests Between Services
**Severity: Medium | Effort: Large**

Services communicate via Feign clients with implicit contracts (matching DTOs). There are no:
- Spring Cloud Contract tests
- Pact consumer-driven contract tests
- Shared API schemas or OpenAPI spec validation

A change to Core Banking's API could silently break Fund Transfer and Utility Payment services.

### GAP-TEST-04: Context Load Tests Will Fail Without Infrastructure
**Severity: Medium | Effort: Small**

The `@SpringBootTest` context-load tests in user-service, fund-transfer, and utility-payment require:
- A Config Server running (bootstrap.yml points to localhost:8090)
- A Eureka server
- A MySQL database
- Keycloak (user-service)

Test `application.yml` files disable Eureka and Flyway but don't mock all external dependencies. These tests are effectively broken for CI.

---

## 4. Security

### GAP-SEC-01: No Input Validation
**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Pattern`, or any Jakarta Validation annotations exist on any request DTO:

```java
// FundTransferRequest — no validation at all
@Data
public class FundTransferRequest {
    private String fromAccount;   // could be null
    private String toAccount;     // could be null
    private BigDecimal amount;    // could be null or negative
    private String authID;
}
```

An attacker can submit null accounts, negative amounts, or empty strings. The same applies to `UtilityPaymentRequest`, `User` (registration), and `UserUpdateRequest`.

### GAP-SEC-02: Hardcoded Credentials in Source Code
**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password`
- `privileges.sql`: `IDENTIFIED BY 'oPItyPticIAt'`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`
- Test `application.yml` files: `password: password`
- User Service test config: `client-secret: e8548d56-d743-45ef-8655-063c9cd96759`

### GAP-SEC-03: No Role-Based Access Control on Endpoints
**Severity: High | Effort: Medium**

The API Gateway authenticates all requests (except user registration and actuator) via JWT but performs **no role-based authorization**. Any authenticated user can:
- List all users
- Approve/disable other users
- Transfer funds from any account
- View all fund transfers and utility payments

There are no `@PreAuthorize`, `@RolesAllowed`, or method-level security annotations anywhere.

### GAP-SEC-04: Actuator Endpoints Publicly Accessible
**Severity: High | Effort: Small**

The API Gateway explicitly permits all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```
Depending on which actuator endpoints are enabled (env, configprops, beans, heapdump), this could expose sensitive configuration, secrets, and memory dumps.

### GAP-SEC-05: CSRF Disabled Without Documentation
**Severity: Low | Effort: Small**

CSRF is explicitly disabled in the Gateway security configuration. While acceptable for a pure API (no browser form submissions), this should be documented with justification.

### GAP-SEC-06: Keycloak Client Uses Static Singleton
**Severity: Medium | Effort: Small**

`KeycloakProperties` uses a static `keycloakInstance` singleton that is never refreshed. If the Keycloak token expires or the connection drops, the instance becomes stale with no recovery mechanism.

```java
private static Keycloak keycloakInstance = null;
// Once created, never refreshed
```

---

## 5. API Design

### GAP-API-01: No API Versioning Strategy
**Severity: Medium | Effort: Medium**

All endpoints use `/api/v1/` but there is no versioning mechanism (header-based, path-based with routing, or content negotiation). If the API evolves, there is no strategy for backward compatibility.

### GAP-API-02: No Pagination Metadata in Responses
**Severity: Medium | Effort: Small**

List endpoints accept Spring's `Pageable` parameter but return raw `List<T>` without pagination metadata:
```java
public ResponseEntity readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));  // Just a list, no total count
}
```
Clients cannot determine total pages, total elements, or current page.

### GAP-API-03: No Filtering or Sorting Parameters
**Severity: Low | Effort: Medium**

List endpoints support only basic pagination. There is no filtering (e.g., by status, date range, account) or explicit sorting support.

### GAP-API-04: Inconsistent Endpoint Naming
**Severity: Low | Effort: Small**

- User Service uses `/api/v1/bank-users/` (plural with prefix)
- Postman collection references `/api/v1/bank-user/` (singular, different from actual code)
- Core Banking User uses `/api/v1/user/` (singular)
- Fund Transfer uses `/api/v1/transfer` (no trailing slash)
- Utility Payment uses `/api/v1/utility-payment` (singular)

### GAP-API-05: OpenAPI/Swagger Only Partially Configured
**Severity: Medium | Effort: Small**

- Fund Transfer, Utility Payment, Core Banking, and User Service include `springdoc-openapi-starter-webflux-ui` dependency and have `@Tag` and `@Operation` annotations
- **However**, the `springdoc-openapi-starter-webflux-ui` artifact is incorrect for standard Spring MVC services — it should be `springdoc-openapi-starter-webmvc-ui`. The WebFlux variant may not render correctly with the servlet-based services
- API Gateway and infrastructure services have no OpenAPI
- No aggregated API documentation across services

### GAP-API-06: No Standard Response Envelope
**Severity: Medium | Effort: Medium**

Successful responses return raw DTOs while errors return `ErrorResponse`. There is no consistent wrapper (e.g., `{ success, data, error, timestamp }`) across all endpoints.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging
**Severity: Medium | Effort: Small**

- Log messages mix styles: string concatenation (`"Got fund transfer request from API {}" + request.toString()`) vs. SLF4J placeholders (`"Reading account by ID {}", accountNumber`)
- Sensitive data is logged: full request bodies (which contain account numbers and amounts) are logged at INFO level
- No structured logging (JSON format) is configured
- No correlation ID / trace ID in log messages (Zipkin handles distributed tracing, but log output does not include trace context)

### GAP-OBS-02: No Health Check Customization
**Severity: Low | Effort: Small**

All services include `spring-boot-starter-actuator` but rely on default health indicators. No custom health checks for:
- Database connectivity (beyond Spring's auto-configured one)
- Keycloak availability
- Config Server reachability
- Downstream service availability

### GAP-OBS-03: No Metrics Endpoints or Dashboards
**Severity: Medium | Effort: Medium**

- `spring-boot-starter-actuator` is included, which exposes `/actuator/metrics` by default
- Prometheus is listed in the README tech stack but **no Prometheus dependency** (`micrometer-registry-prometheus`) exists in any `build.gradle`
- No Grafana dashboards or alerting configuration

### GAP-OBS-04: Distributed Tracing Configuration is External-Only
**Severity: Low | Effort: Small**

Tracing dependencies (Micrometer Brave, Zipkin reporter) are present but all configuration (sampling rate, endpoint) lives in the external Config Server repository. Developers cannot easily verify tracing works locally without the full Docker stack.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity: Critical | Effort: Medium**

All inter-service calls via Feign have no circuit breaker (Resilience4j, Hystrix, etc.). If Core Banking Service goes down:
- Fund Transfer Service will block on every request until Feign times out
- Utility Payment Service will similarly block
- User Service's registration flow will fail

No `spring-cloud-starter-circuitbreaker-resilience4j` dependency exists.

### GAP-RES-02: No Retry Policies
**Severity: High | Effort: Small**

No retry configuration exists for Feign clients. Transient failures (network glitches, brief service restarts) result in immediate failure with no recovery attempt.

### GAP-RES-03: No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeouts are configured for:
- Feign client connections and read operations
- Database connections
- Keycloak API calls

Default timeouts may be excessively long, causing thread starvation under load.

### GAP-RES-04: No Fallback Behavior
**Severity: Medium | Effort: Medium**

When downstream services fail, there are no fallback strategies:
- No cached responses
- No graceful degradation
- No queue-and-retry patterns
- Errors propagate directly to the client

### GAP-RES-05: No Transactional Consistency Across Services
**Severity: Critical | Effort: Large**

The fund transfer flow spans two services (Fund Transfer Service → Core Banking Service) with no distributed transaction or saga pattern:
1. Fund Transfer Service saves entity with `PENDING` status
2. Calls Core Banking which debits/credits accounts
3. If the response is lost or Fund Transfer Service crashes after Core Banking commits, the accounts are modified but Fund Transfer still shows `PENDING`
4. There is no compensation logic, no idempotency keys, and no reconciliation process

Similarly, Utility Payment Service has the same consistency gap.

### GAP-RES-06: No Rate Limiting
**Severity: Medium | Effort: Small**

The API Gateway has no rate limiting configuration. Endpoints are vulnerable to abuse or accidental high-volume traffic.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Duplicated code across services | High | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Mappers not Spring-managed | Low | Small |
| GAP-ERR-01 | Error Handling | All errors return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Exception handler leaks internals | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-04 | Error Handling | No Feign error handling in 2 services | High | Medium |
| GAP-ERR-05 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage (3/4 services) | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | Context load tests broken for CI | Medium | Small |
| GAP-SEC-01 | Security | No input validation | Critical | Medium |
| GAP-SEC-02 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-03 | Security | No role-based access control | High | Medium |
| GAP-SEC-04 | Security | Actuator endpoints public | High | Small |
| GAP-SEC-05 | Security | CSRF disabled without docs | Low | Small |
| GAP-SEC-06 | Security | Keycloak static singleton | Medium | Small |
| GAP-API-01 | API Design | No versioning strategy | Medium | Medium |
| GAP-API-02 | API Design | No pagination metadata | Medium | Small |
| GAP-API-03 | API Design | No filtering/sorting | Low | Medium |
| GAP-API-04 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-API-05 | API Design | OpenAPI partially/incorrectly configured | Medium | Small |
| GAP-API-06 | API Design | No standard response envelope | Medium | Medium |
| GAP-OBS-01 | Observability | Inconsistent logging | Medium | Small |
| GAP-OBS-02 | Observability | No custom health checks | Low | Small |
| GAP-OBS-03 | Observability | No Prometheus metrics | Medium | Medium |
| GAP-OBS-04 | Observability | Tracing config external-only | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No distributed transaction safety | Critical | Large |
| GAP-RES-06 | Resilience | No rate limiting | Medium | Small |
