# Engineering Standards Gap Analysis

This document compares the current codebase against industry-standard engineering best practices and identifies specific gaps with severity ratings and remediation effort estimates.

---

## 1. Code Organization

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 1.1 | No multi-project Gradle build | Medium | Medium | Each service is a standalone Gradle project with its own `build.gradle`. No root `settings.gradle` or shared dependency version catalog. Leads to version drift and duplicated build configuration. |
| 1.2 | Duplicated code across services | High | Medium | Exception classes (`GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`), audit classes (`AuditAware`, `AuditConfig`, `AuditorAwareConfig`), filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`), and mapper base classes (`BaseMapper`) are copy-pasted across 3-4 services with minor variations. |
| 1.3 | Inconsistent package structure | Medium | Small | Core banking uses `repository/` package; user service uses `model/repository/`; utility payment uses `repository/` at root; fund-transfer uses `model/repository/`. No consistent convention. |
| 1.4 | Inconsistent code formatting | Low | Small | Mixed tab/space indentation across `build.gradle` files. Some services use tabs (utility-payment), others use spaces (core-banking). |
| 1.5 | DTOs in wrong packages | Low | Small | `AuditAware` is a JPA `@MappedSuperclass` placed in `model.dto` package instead of `model.entity` in fund-transfer and utility-payment services. |
| 1.6 | No shared library/module | High | Large | Common models, exceptions, and configurations should be extracted into a shared library to eliminate duplication and ensure consistency. |

### Summary
The codebase lacks a shared library strategy. Each service reinvents common patterns leading to code duplication and inconsistency. The absence of a Gradle multi-project build makes coordinated changes difficult.

---

## 2. Error Handling

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 2.1 | Generic exception handler returns raw exception | Critical | Small | All `GlobalExceptionHandler` classes have a catch-all `@ExceptionHandler({Exception.class})` that returns `"Exception occur inside API " + e` — this **leaks stack traces and internal details** to clients. |
| 2.2 | All errors return HTTP 400 | High | Small | Both business exceptions and unexpected errors return `400 Bad Request`. Missing proper HTTP status mapping (404 for not found, 500 for internal errors, 422 for validation, etc.). |
| 2.3 | Inconsistent error response format | Medium | Small | Core-banking and user-service use `ErrorResponse.builder()`, fund-transfer uses `new ErrorResponse(code, message)`. Different construction patterns suggest structural inconsistency. |
| 2.4 | No error response for Feign failures | High | Medium | When a Feign call fails (e.g., core-banking is down), the exception propagates without meaningful mapping. `CustomFeignErrorDecoder` exists only in user-service with limited implementation. |
| 2.5 | Missing validation error handling | Medium | Small | No `@Valid` annotations on request DTOs. No `MethodArgumentNotValidException` handler for bean validation failures. |
| 2.6 | No correlation ID in error responses | Medium | Small | Error responses don't include trace/correlation IDs, making it difficult to link client errors to server-side traces. |

### Summary
Error handling is the most critical area. Stack trace leakage is a security vulnerability. The uniform 400 status code makes it impossible for clients to differentiate between user errors and system failures.

---

## 3. Testing

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 3.1 | Minimal test coverage | High | Large | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services have only the auto-generated Spring context load test. |
| 3.2 | No integration tests | High | Large | No tests that verify Feign client interactions, database queries with real DB, or end-to-end flows. |
| 3.3 | No contract tests | Medium | Large | No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. Breaking API changes won't be detected until runtime. |
| 3.4 | No controller/API layer tests | Medium | Medium | No `@WebMvcTest` or `MockMvc`-based tests to verify endpoint mappings, request validation, or response serialization. |
| 3.5 | Test configuration disables Flyway | Low | Small | `core-banking-service` test profile disables Flyway and uses `ddl-auto: none` with H2 — schema must be manually maintained or tests use blank DB. |
| 3.6 | No CI pipeline to run tests | High | Medium | No GitHub Actions, Jenkins, or any CI configuration. Tests are never automatically verified on push or PR. |

### Summary
Test coverage is critically low. Only the core-banking service has any business logic tests. The absence of CI means even existing tests may silently break.

---

## 4. Security

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 4.1 | Hardcoded credentials in source code | Critical | Small | MySQL root password (`woVERANKliGharym`), DB user password (`oPItyPticIAt`), Keycloak admin credentials (`admin/password`), test user credentials — all committed in `docker-compose.yml` and `privileges.sql`. |
| 4.2 | No input validation | Critical | Medium | Request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) have no `@NotNull`, `@Min`, `@Size`, or any Jakarta Bean Validation annotations. Attackers can submit negative amounts, null accounts, etc. |
| 4.3 | CSRF disabled without justification | Medium | Small | API Gateway disables CSRF (`csrf.disable()`). While appropriate for stateless JWT APIs, there's no documentation or comment explaining the security decision. |
| 4.4 | No rate limiting | Medium | Medium | No rate limiting at gateway or service level. APIs are vulnerable to brute-force and DDoS attacks. |
| 4.5 | No authorization beyond authentication | High | Medium | Gateway only checks "is the user authenticated?" No role-based access control. Any authenticated user can approve other users, view all accounts, or initiate transfers from any account. |
| 4.6 | Sensitive data in logs | Medium | Small | Controllers log full request objects via `toString()` (e.g., `log.info("Creating user with {}", request.toString())`) — may log passwords, account numbers, and PII. |
| 4.7 | No dependency vulnerability scanning | Medium | Small | No OWASP dependency-check, Snyk, or similar tool configured. |
| 4.8 | Static Keycloak singleton (thread-safety) | Medium | Small | `KeycloakProperties` uses a static `keycloakInstance` with lazy initialization without synchronization — potential race condition in concurrent scenarios. |

### Summary
Multiple critical security gaps exist. Hardcoded credentials and lack of input validation are immediate risks. The absence of authorization means any authenticated user has full system access.

---

## 5. API Design

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 5.1 | Raw `ResponseEntity` without type parameters | Medium | Small | Most controller methods return `ResponseEntity` (raw type) without generic type parameter, losing compile-time type safety and OpenAPI documentation accuracy. |
| 5.2 | No API versioning strategy | Low | Small | APIs use `/api/v1/` prefix but there's no documented versioning policy, no header-based versioning support, and no migration strategy. |
| 5.3 | Inconsistent resource naming | Medium | Small | Mixed naming: `/bank-account/{account_number}` (snake_case path param), `/bank-users/register` (verb in URL), `/util-account/` (abbreviation). REST conventions prefer consistent kebab-case nouns. |
| 5.4 | No pagination metadata in responses | Medium | Small | List endpoints accept `Pageable` but return raw `List<>` — clients don't receive total count, page number, or navigation links. |
| 5.5 | No filtering or sorting | Low | Medium | List endpoints only support basic pagination. No query parameters for filtering by status, date range, amount range, etc. |
| 5.6 | OpenAPI/Swagger misconfigured | Medium | Small | Uses `springdoc-openapi-starter-webflux-ui` in non-WebFlux services (web-mvc services). Should use `springdoc-openapi-starter-webmvc-ui`. May not render correctly. |
| 5.7 | No standardized response envelope | Medium | Medium | Success responses return raw entities. No consistent wrapper like `{data: ..., meta: {...}}` for uniform client handling. |
| 5.8 | PATCH endpoint without proper partial update semantics | Low | Small | User update uses `@PatchMapping` but accepts a full `UserUpdateRequest` — not true PATCH semantics (JSON Patch or JSON Merge Patch). |

### Summary
API design has several inconsistencies that would impact client developer experience. The raw ResponseEntity usage loses type safety and documentation quality.

---

## 6. Observability

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 6.1 | No structured logging | Medium | Medium | All services use default Logback with unstructured text format. No JSON logging for log aggregation tools (ELK, CloudWatch, etc.). |
| 6.2 | No custom health indicators | Low | Small | Services only expose default Spring Boot health checks. No custom indicators for database connectivity, Keycloak availability, or downstream service health. |
| 6.3 | No metrics endpoints beyond defaults | Medium | Small | Actuator is included but no custom metrics (e.g., transfer success/failure rates, payment processing times, active users). |
| 6.4 | Logging exposes sensitive data | Medium | Small | Request objects logged with `toString()` may contain passwords and financial data. No masking or sanitization. |
| 6.5 | No alerting configuration | Medium | Medium | No Prometheus alerting rules, no Grafana dashboards defined, despite Prometheus being listed in the tech stack. |
| 6.6 | Incomplete tracing coverage | Low | Small | Zipkin integration is configured but Feign client tracing may not capture full request/response details without additional configuration. |
| 6.7 | No request/response logging at gateway | Low | Small | API Gateway doesn't log inbound/outbound requests for audit trail purposes. |

### Summary
Basic tracing infrastructure exists (Zipkin + Micrometer) but observability is otherwise minimal. No structured logging, custom metrics, or alerting makes production debugging difficult.

---

## 7. Resilience

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 7.1 | No circuit breakers | Critical | Medium | Services call each other synchronously via Feign with no circuit breaker (Resilience4j, Hystrix). A core-banking outage cascades to all dependent services. |
| 7.2 | No retry policies | High | Small | Feign clients have no retry configuration. Transient network failures cause immediate request failure. |
| 7.3 | No timeout configuration | High | Small | No explicit timeouts on Feign clients or HTTP connections. Default infinite/very long timeouts can cause thread starvation under load. |
| 7.4 | No fallback behavior | Medium | Medium | No fallback responses when downstream services are unavailable. Users get raw error responses. |
| 7.5 | No bulkhead pattern | Medium | Medium | No thread pool isolation between different types of outbound calls. A slow core-banking response blocks all threads. |
| 7.6 | Single database instance | High | Large | All services share one MySQL instance. No read replicas, no connection pooling configuration (HikariCP defaults used without tuning). |
| 7.7 | No graceful degradation | Medium | Medium | If Keycloak is down, user registration fails completely. No queuing or async processing for non-critical operations. |
| 7.8 | No health-based routing in Eureka | Low | Small | Eureka configuration doesn't leverage health check integration for intelligent routing away from unhealthy instances. |

### Summary
Resilience is the second-most critical gap area. The synchronous call chain without circuit breakers means a single service failure cascades system-wide. For a banking application, this represents an availability risk.

---

## Severity Summary

| Severity | Count | Key Areas |
|----------|-------|-----------|
| **Critical** | 4 | Stack trace leakage, hardcoded credentials, no input validation, no circuit breakers |
| **High** | 9 | Code duplication, wrong HTTP status codes, Feign error handling, minimal tests, no CI, no authorization, no retries, no timeouts, single DB |
| **Medium** | 18 | Various consistency, observability, API design, and resilience gaps |
| **Low** | 8 | Formatting, minor naming issues, documentation |

---

## Effort Summary

| Effort | Count | Description |
|--------|-------|-------------|
| **Small** | 16 | Configuration changes, annotation additions, minor code fixes (< 1 day each) |
| **Medium** | 14 | New components, moderate refactoring, new test suites (1-3 days each) |
| **Large** | 5 | Shared library extraction, comprehensive test coverage, contract tests, CI pipeline, DB HA (1+ week each) |
