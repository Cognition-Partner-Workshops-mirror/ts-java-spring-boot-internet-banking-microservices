# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity:** Medium | **Effort:** Medium

Each service has its own standalone `build.gradle` with duplicated plugin versions, dependency management blocks, and Spring Cloud BOM declarations. There is no root `settings.gradle` or `build.gradle` to unify version management.

**Impact:** Version drift between services is likely. Upgrading Spring Boot or Spring Cloud requires editing 7 separate files.

### 1.2 Duplicated Code Across Services

**Severity:** High | **Effort:** Medium

The following classes are copy-pasted across 3-4 services with minor variations:
- `AuditAware` / `AuditConfig` / `AuditorAwareConfig` (user, fund-transfer, utility-payment)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (user, fund-transfer, utility-payment)
- `BaseMapper` (all 4 business services)
- `ErrorResponse` / `SimpleBankingGlobalException` / `GlobalExceptionHandler` (all 4 business services)
- `GlobalErrorCode` (core-banking, user)
- `CustomFeignClientConfiguration` (fund-transfer, utility-payment)

**Impact:** Bug fixes or improvements must be applied in every copy. Inconsistencies already exist (e.g., `ErrorResponse` uses `@Builder` in some services but constructor in others).

### 1.3 No Shared Library Module

**Severity:** Medium | **Effort:** Medium

There is no `common` or `shared` module for cross-cutting concerns (DTOs, exceptions, mappers, audit config, Feign configuration). Each service independently defines overlapping models.

### 1.4 Inconsistent Package Structure

**Severity:** Low | **Effort:** Small

- Core Banking: `repository` package at top level
- User Service: `model.repository` sub-package
- Fund Transfer: `model.repository` sub-package
- Utility Payment: `repository` at top level
- DTOs: `model.dto` vs. `model.rest.request` / `model.rest.response` (inconsistent across services)

### 1.5 Mapper Instantiation Pattern

**Severity:** Low | **Effort:** Small

Mappers are instantiated via `new` inside services (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans. This bypasses dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400

**Severity:** Critical | **Effort:** Small

Every `GlobalExceptionHandler` maps **all** exceptions (including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception`) to `400 Bad Request`. This means:
- A missing resource returns 400 instead of **404**
- A server error returns 400 instead of **500**
- Insufficient funds returns 400 instead of **422** or **409**

### 2.2 Generic Exception Handler Leaks Stack Traces

**Severity:** Critical | **Effort:** Small

The catch-all handler returns the raw exception string to the client:
```java
.body("Exception occur inside API " + e);
```
This exposes internal class names, stack traces, and potentially sensitive information (database connection strings, file paths) to external consumers.

### 2.3 Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

- Known exceptions return `ErrorResponse { code, message }` (structured JSON)
- Unknown exceptions return a plain string (`"Exception occur inside API ..."`)

Clients cannot reliably parse error responses because the shape changes depending on the exception type.

### 2.4 No Feign Error Decoder in Core Banking / User Service Feign Client

**Severity:** High | **Effort:** Small

The User Service's `BankingCoreRestClient` Feign client does not specify a `configuration` class (unlike Fund Transfer and Utility Payment services which use `CustomFeignClientConfiguration`). Feign errors from Core Banking will surface as opaque `FeignException` instances.

### 2.5 No Validation Error Handling

**Severity:** Medium | **Effort:** Small

No `MethodArgumentNotValidException` handler exists. If `@Valid` annotations are added to request bodies (currently missing), Spring's default validation error format would bypass the custom exception handler.

---

## 3. Testing

### 3.1 Tests Only Exist in Core Banking Service

**Severity:** Critical | **Effort:** Large

Only `core-banking-service` has meaningful unit tests (3 test classes, ~20 test methods). The other 3 business services and 3 infrastructure services have **no tests** beyond empty Spring context loaders.

| Service | Test Files | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 | 20 test methods |
| internet-banking-user-service | 1 | 0 (context load only) |
| internet-banking-fund-transfer-service | 1 | 0 (context load only) |
| internet-banking-utility-payment-service | 1 | 0 (context load only) |
| internet-banking-api-gateway | 1 | 0 (context load only) |
| internet-banking-service-registry | 1 | 0 (context load only) |
| internet-banking-config-server | 1 | 0 (context load only) |

### 3.2 No Integration Tests

**Severity:** High | **Effort:** Large

No integration tests exist that:
- Test REST controllers with `@WebMvcTest` or `@SpringBootTest`
- Test repository queries with `@DataJpaTest`
- Test the full service flow with a real database (H2 dependency is present but unused)

### 3.3 No Contract Tests Between Services

**Severity:** High | **Effort:** Large

No Spring Cloud Contract, Pact, or similar consumer-driven contract tests exist between the Feign clients and their provider services. Breaking changes to Core Banking's API would not be caught until runtime.

### 3.4 No Test Coverage Reporting

**Severity:** Medium | **Effort:** Small

No JaCoCo or similar coverage plugin is configured. There is no visibility into how much of the codebase is covered.

### 3.5 Context Load Tests Will Fail Without Infrastructure

**Severity:** Low | **Effort:** Small

The empty context load tests (e.g., `InternetBankingUserServiceApplicationTests`) require Keycloak, MySQL, Eureka, and Config Server to be running. These tests will fail in CI without test containers or profiles.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin credentials (`admin/password`), PostgreSQL credentials (`keycloak/password`)
- `privileges.sql`: Application DB user credentials (`javatodev_development/oPItyPticIAt`)
- `README.md`: Test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`)

### 4.2 No Input Validation on Request Bodies

**Severity:** Critical | **Effort:** Small

No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`, or `@Positive` annotations exist on any request DTOs. This means:
- Fund transfers can have `null` or negative amounts
- User registration accepts any string as email
- Account numbers are not validated
- No protection against malformed requests

### 4.3 CSRF Disabled on API Gateway

**Severity:** Low | **Effort:** Small

`ServerHttpSecurity.CsrfSpec::disable` is appropriate for a stateless API with JWT tokens (no session cookies). This is acceptable but should be documented.

### 4.4 Internal Services Have No Authentication

**Severity:** High | **Effort:** Medium

The Core Banking Service endpoints (`/api/v1/account/*`, `/api/v1/user/*`, `/api/v1/transaction/*`) have no security configuration. Any service on the Docker network can call them directly without authentication. In a production environment, these should be restricted to only accept requests from known services.

### 4.5 X-Auth-Id Header Injection Without Verification

**Severity:** High | **Effort:** Small

The API Gateway extracts the principal name from the JWT and injects it as `X-Auth-Id` header. However, downstream services blindly trust this header. If a service is called directly (bypassing the gateway), any value can be injected.

### 4.6 Keycloak Singleton is Not Thread-Safe

**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses a classic double-check-locking anti-pattern without `synchronized` or `volatile`. Under concurrent access, multiple Keycloak client instances could be created, leading to resource leaks.

### 4.7 No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar plugin is configured in any `build.gradle`. Known CVEs in transitive dependencies will go undetected.

### 4.8 Password Logged in Plain Text

**Severity:** High | **Effort:** Small

`UserController.createUser()` logs the entire `User` request object (`log.info("Creating user with {}", request.toString())`), which includes the `password` field. Similar logging of `FundTransferRequest.toString()` may expose sensitive financial data.

---

## 5. API Design

### 5.1 Non-Standard HTTP Status Codes

**Severity:** High | **Effort:** Small

- `POST` endpoints return `200 OK` instead of `201 Created`
- All errors return `400 Bad Request` (covered in Error Handling section)
- No `204 No Content` for empty responses
- No `404 Not Found` for missing resources

### 5.2 No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

While endpoints use `/api/v1/`, there is no documented versioning strategy (header-based, URL-based, content negotiation). No mechanism exists for introducing `/api/v2/` without breaking existing clients.

### 5.3 Raw `ResponseEntity` Without Type Parameters

**Severity:** Medium | **Effort:** Small

Most controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This prevents proper OpenAPI schema generation and removes compile-time type safety.

### 5.4 OpenAPI/Swagger Dependency But Incomplete Configuration

**Severity:** Medium | **Effort:** Small

`springdoc-openapi-starter-webflux-ui` is included in all business services, and basic `@Tag` and `@Operation` annotations exist, but:
- The `webflux` variant is used in `spring-web` (non-reactive) services — this is the **wrong** dependency (should be `springdoc-openapi-starter-webmvc-ui`)
- No `@ApiResponse`, `@Schema`, or `@Parameter` annotations for detailed documentation
- No global OpenAPI configuration (info, servers, security schemes)

### 5.5 Pagination Returns Raw Lists

**Severity:** Medium | **Effort:** Small

Paginated endpoints (e.g., `GET /api/v1/bank-users`, `GET /api/v1/transfer`) return `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (`totalElements`, `totalPages`, `page`, `size`). Clients have no way to know if more pages exist.

### 5.6 No Filtering or Sorting Support

**Severity:** Low | **Effort:** Medium

While `Pageable` is accepted (providing `page` and `size`), there is no support for filtering (e.g., by date range, status, account) or explicit sort parameters on any listing endpoint.

### 5.7 Inconsistent URL Conventions

**Severity:** Low | **Effort:** Small

- Core Banking uses underscores: `/bank-account/{account_number}`, `/util-account/{account_name}`
- Path variables mix styles: `{account_number}` (snake_case) vs. `{id}` (plain)
- Some paths use abbreviations: `/util-account` vs. `/utility-payment`

---

## 6. Observability

### 6.1 Logging is Sparse and Inconsistent

**Severity:** Medium | **Effort:** Medium

- Core Banking `AccountService`, `UserService`: no log statements at all
- Controllers log incoming requests but not outcomes or errors
- No structured logging (JSON format) configured
- No correlation IDs in log messages beyond what Micrometer injects automatically
- Sensitive data logged (passwords, full request objects)

### 6.2 Health Checks Are Default Only

**Severity:** Medium | **Effort:** Small

Actuator health endpoints exist but only provide default checks. No custom health indicators for:
- Keycloak connectivity
- Database connection pool health
- Downstream service availability (Feign client targets)

### 6.3 No Metrics Endpoints Beyond Actuator Defaults

**Severity:** Medium | **Effort:** Medium

No custom metrics are defined for:
- Transaction counts/amounts
- Fund transfer success/failure rates
- API latency percentiles
- Active user sessions

Prometheus is listed in the tech stack but no `micrometer-registry-prometheus` dependency exists.

### 6.4 Distributed Tracing Coverage is Automatic Only

**Severity:** Low | **Effort:** Small

Zipkin integration is present via `micrometer-tracing-bridge-brave`, providing automatic span creation for HTTP requests and Feign calls. However:
- No custom spans for business logic
- No span tags for business context (account numbers, transaction IDs)
- No sampling rate configuration visible in local config

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

No Resilience4j, Hystrix, or Spring Cloud Circuit Breaker dependency or configuration exists. If Core Banking Service goes down:
- Fund Transfer Service will keep sending requests and failing
- Utility Payment Service will keep sending requests and failing
- User registration will fail with opaque errors
- No fallback behavior is defined anywhere

### 7.2 No Retry Policies

**Severity:** High | **Effort:** Small

No Spring Retry or Resilience4j retry configuration exists for Feign clients. Transient failures (network blips, temporary 503s) will immediately propagate as errors to the end user.

### 7.3 No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit timeout configuration for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Keycloak Admin API call timeouts

Default Feign timeouts (10s connect, 60s read) may be too generous for a banking application.

### 7.4 No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

No `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)` is configured. When downstream services are unavailable, the error propagates directly to the client without any graceful degradation.

### 7.5 No Rate Limiting

**Severity:** Medium | **Effort:** Medium

The API Gateway has no rate limiting configuration. A single client could overwhelm the system with requests. Spring Cloud Gateway supports `RequestRateLimiter` filter with Redis, but it is not configured.

### 7.6 Transaction Atomicity Concerns

**Severity:** Critical | **Effort:** Large

The fund transfer flow involves two separate services and databases:
1. Fund Transfer Service saves a `PENDING` record to its DB
2. Core Banking Service debits/credits accounts in its DB
3. Fund Transfer Service updates its record to `SUCCESS`

If step 3 fails (e.g., network error after Core Banking succeeds), the funds are transferred in Core Banking but the Fund Transfer Service record remains `PENDING` — leading to an **inconsistent state**. There is no:
- Distributed transaction (Saga pattern)
- Compensation/rollback mechanism
- Idempotency keys to safely retry
- Outbox pattern for reliable messaging

### 7.7 No Bulkhead Isolation

**Severity:** Low | **Effort:** Medium

All Feign calls share the default thread pool/connection pool. A slow response from one downstream service can exhaust the thread pool and impact all other operations.

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|---|---|---|---|
| 2.1 | All errors return HTTP 400 | Critical | Small | Error Handling |
| 2.2 | Generic handler leaks stack traces | Critical | Small | Error Handling |
| 3.1 | Tests only in core-banking-service | Critical | Large | Testing |
| 4.1 | Hardcoded credentials in source | Critical | Small | Security |
| 4.2 | No input validation | Critical | Small | Security |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.6 | Transaction atomicity / no Saga | Critical | Large | Resilience |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 2.4 | Missing Feign error decoder | High | Small | Error Handling |
| 3.2 | No integration tests | High | Large | Testing |
| 3.3 | No contract tests | High | Large | Testing |
| 4.4 | Internal services have no auth | High | Medium | Security |
| 4.5 | X-Auth-Id header trusted blindly | High | Small | Security |
| 4.8 | Password logged in plain text | High | Small | Security |
| 5.1 | Non-standard HTTP status codes | High | Small | API Design |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 1.1 | No multi-project Gradle build | Medium | Medium | Code Organization |
| 1.3 | No shared library module | Medium | Medium | Code Organization |
| 2.3 | Inconsistent error response format | Medium | Small | Error Handling |
| 2.5 | No validation error handling | Medium | Small | Error Handling |
| 3.4 | No test coverage reporting | Medium | Small | Testing |
| 4.6 | Keycloak singleton not thread-safe | Medium | Small | Security |
| 4.7 | No dependency vulnerability scanning | Medium | Small | Security |
| 5.2 | No API versioning strategy | Medium | Medium | API Design |
| 5.3 | Raw ResponseEntity types | Medium | Small | API Design |
| 5.4 | Wrong OpenAPI dependency | Medium | Small | API Design |
| 5.5 | Pagination returns raw lists | Medium | Small | API Design |
| 6.1 | Sparse and inconsistent logging | Medium | Medium | Observability |
| 6.2 | Health checks are default only | Medium | Small | Observability |
| 6.3 | No custom metrics / no Prometheus | Medium | Medium | Observability |
| 7.4 | No fallback behavior | Medium | Medium | Resilience |
| 7.5 | No rate limiting | Medium | Medium | Resilience |
| 1.4 | Inconsistent package structure | Low | Small | Code Organization |
| 1.5 | Mapper instantiation via new | Low | Small | Code Organization |
| 3.5 | Context load tests fail without infra | Low | Small | Testing |
| 4.3 | CSRF disabled (acceptable for JWT) | Low | Small | Security |
| 5.6 | No filtering or sorting | Low | Medium | API Design |
| 5.7 | Inconsistent URL conventions | Low | Small | API Design |
| 6.4 | Tracing is automatic only | Low | Small | Observability |
| 7.7 | No bulkhead isolation | Low | Medium | Resilience |
