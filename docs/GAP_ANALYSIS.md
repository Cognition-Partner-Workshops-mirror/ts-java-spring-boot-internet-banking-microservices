# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps with severity ratings and remediation effort estimates.

---

## 1. Code Organization

### Current State
- Each service is a standalone Gradle project (no shared parent build or multi-module setup)
- All services use the same base package `com.javatodev.finance`
- Common patterns are duplicated across services: `AuditAware`, `BaseMapper`, `ErrorResponse`, `GlobalExceptionHandler`, `AppAuthUserFilter`, `ApiRequestContext`, `CustomFeignClientConfiguration`
- Consistent internal layering: `controller` -> `service` -> `repository` / `model`

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CO-1 | No shared library / common module | Medium | Medium | `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` are copy-pasted across 3+ services. Changes require updating each service independently. |
| CO-2 | No multi-project Gradle build | Low | Small | Each service has its own `gradlew`, `gradle/wrapper`, and `settings.gradle`. No root-level `settings.gradle` or `build.gradle` for unified builds, dependency management, or version alignment. |
| CO-3 | Inconsistent package naming for similar components | Low | Small | User Service places Feign config in `configuration.feign`, Fund Transfer in `configuration`, Utility Payment in `configuration`. REST clients are in `service.rest.client` vs `service.rest`. |
| CO-4 | Mappers instantiated with `new` instead of Spring beans | Low | Small | `UserMapper`, `FundTransferMapper`, `BankAccountMapper` etc. are created with `new` in service classes rather than injected, making them harder to test/mock. |
| CO-5 | No `.editorconfig` or formatting rules | Low | Small | No enforced code formatting. Inconsistent indentation (tabs vs spaces between services). |

---

## 2. Error Handling

### Current State
- Each service has a `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler`
- Custom exception hierarchy: `SimpleBankingGlobalException` -> `EntityNotFoundException`, `InsufficientFundsException`, etc.
- `ErrorResponse` DTO with `code` and `message` fields

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| EH-1 | All errors return HTTP 400 Bad Request | High | Small | `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` for ALL exceptions including `EntityNotFoundException` (should be 404), server errors (should be 500), and business logic violations. |
| EH-2 | Generic exception handler leaks internal details | Critical | Small | `handleException(Exception e)` returns `"Exception occur inside API " + e` which exposes stack traces, class names, and internal state to API consumers. |
| EH-3 | Inconsistent error response format | Medium | Small | `SimpleBankingGlobalException` returns structured `ErrorResponse`; generic exceptions return a plain string. The Fund Transfer service uses `new ErrorResponse()` constructor while others use builder pattern. |
| EH-4 | No error handling for Feign client failures | High | Medium | If Core Banking Service is down or returns errors, Feign throws unhandled exceptions. No Feign error decoder in Fund Transfer or Utility Payment services (User Service has `CustomFeignErrorDecoder` but others don't). |
| EH-5 | Missing validation error handling | Medium | Small | No `@Valid` / Bean Validation annotations on request DTOs. No `MethodArgumentNotValidException` handler. |
| EH-6 | Raw `ResponseEntity` without type parameters | Low | Small | Controllers return `ResponseEntity` without generics (e.g., `ResponseEntity<FundTransferResponse>`), losing compile-time type safety and OpenAPI documentation accuracy. |

---

## 3. Testing

### Current State
- Core Banking Service has unit tests: `AccountServiceTest` (6 tests), `TransactionServiceTest` (9 tests), `UserServiceTest` (referenced)
- Other services have only empty `*ApplicationTests` classes (Spring context load tests)
- Tests use Mockito for mocking
- H2 in-memory database configured for test profiles

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| TE-1 | No tests for 4 of 6 services | Critical | Large | Fund Transfer, Utility Payment, User Service, and API Gateway have zero meaningful tests. Only skeleton `@SpringBootTest` classes exist. |
| TE-2 | No integration tests | High | Large | No tests that verify Feign client calls, database operations with real DB, or end-to-end flows. |
| TE-3 | No contract tests between services | High | Large | Services communicate via Feign with shared DTOs but no Pact or Spring Cloud Contract tests verify API compatibility. |
| TE-4 | No controller/API tests | Medium | Medium | No `@WebMvcTest` or MockMvc tests to verify HTTP endpoint behavior, status codes, or serialization. |
| TE-5 | No test for Keycloak integration | Medium | Medium | `KeycloakUserService` and `UserService.createUser()` are untested. Complex registration logic has no coverage. |
| TE-6 | No CI pipeline to enforce testing | High | Medium | No GitHub Actions, Jenkins, or any CI configuration to run tests on PRs. |

---

## 4. Security

### Current State
- API Gateway enforces OAuth2/JWT via Keycloak (resource server with JWK validation)
- User registration endpoint is publicly accessible (by design)
- Actuator endpoints are publicly accessible
- `X-Auth-Id` header propagated from gateway to services
- CSRF disabled on API Gateway

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| SE-1 | No input validation on any request DTO | Critical | Medium | `FundTransferRequest`, `UtilityPaymentRequest`, `User` DTOs have no `@NotNull`, `@Min`, `@Size`, or other Bean Validation annotations. Accepts null amounts, empty accounts, negative values. |
| SE-2 | Hardcoded credentials in source code | Critical | Small | MySQL root password (`woVERANKliGharym`), DB user credentials (`oPItyPticIAt`), Keycloak admin password (`password`) are hardcoded in Docker Compose and SQL files committed to Git. |
| SE-3 | Downstream services have no authentication | High | Medium | Core Banking Service, Fund Transfer Service, and Utility Payment Service accept requests without any JWT/token validation. Only the API Gateway enforces auth; any direct access bypasses security entirely. |
| SE-4 | No authorization / role-based access control | High | Medium | All authenticated users can access all endpoints. No role differentiation between admin (approve users) and regular users (transfer funds). |
| SE-5 | Actuator endpoints exposed without authentication | Medium | Small | All `/actuator/**` endpoints are permitted. Exposes health, env, beans, metrics to unauthenticated users. Potentially leaks config details. |
| SE-6 | Keycloak singleton is not thread-safe | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized lazy initialization pattern (classic double-check locking bug). |
| SE-7 | No rate limiting | Medium | Medium | No rate limiting on any endpoint. Registration and fund transfer APIs are vulnerable to abuse. |
| SE-8 | No dependency vulnerability scanning | Medium | Small | No OWASP dependency-check, Snyk, or Dependabot configured. |
| SE-9 | Test credentials in README | Low | Small | Production-like credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`) are documented in the README. |

---

## 5. API Design

### Current State
- RESTful URL patterns with `/api/v1/` prefix
- GET for reads, POST for creates, PATCH for updates
- Pagination support via Spring Data `Pageable`
- OpenAPI/Swagger annotations present (`@Tag`, `@Operation`)
- SpringDoc OpenAPI dependency included

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| AD-1 | No API versioning strategy beyond URL prefix | Low | Small | `/api/v1/` exists but no plan or infrastructure for v2. Acceptable for now. |
| AD-2 | Inconsistent response wrapping | Medium | Medium | Some endpoints return raw entities, others return DTOs. No standard envelope (e.g., `{data: ..., meta: ...}`). Pagination responses are raw lists without total count or page metadata. |
| AD-3 | Missing OpenAPI schema annotations on DTOs | Medium | Small | DTOs lack `@Schema` annotations. Auto-generated OpenAPI docs will have minimal field descriptions. |
| AD-4 | Wrong SpringDoc dependency | Medium | Small | Services use `springdoc-openapi-starter-webflux-ui` but are WebMVC (servlet-based, not WebFlux). Should use `springdoc-openapi-starter-webmvc-ui`. Only API Gateway is WebFlux. |
| AD-5 | No standard error response schema in OpenAPI | Medium | Small | Error responses are not documented in the OpenAPI spec. Consumers cannot see error shapes. |
| AD-6 | PATCH endpoint uses wrong semantics | Low | Small | `PATCH /update/{id}` includes "update" in the path (redundant with PATCH method). Should be `PATCH /bank-users/{id}`. |
| AD-7 | No HATEOAS or resource linking | Low | Large | No hypermedia links in responses. Clients must hardcode URL structures. |

---

## 6. Observability

### Current State
- All services include Spring Boot Actuator
- Micrometer tracing with Brave bridge + Zipkin reporter configured
- `@Slf4j` logging on all controllers and services
- Git properties plugin generates build info

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| OB-1 | No structured logging | Medium | Medium | Uses default Spring Boot logback with unstructured text format. No JSON logging for log aggregation tools. |
| OB-2 | Sensitive data logged | High | Small | Controllers log full request objects (`request.toString()`) which may contain passwords (User registration logs password in `User.toString()`). |
| OB-3 | No custom health indicators | Low | Small | Only default Spring Boot health checks. No custom health checks for Keycloak connectivity, MySQL reachability, or Feign client health. |
| OB-4 | No Prometheus metrics endpoint configured | Medium | Small | Actuator dependency is included but no `management.endpoints.web.exposure` or Prometheus registry configured in the visible config. Metrics may not be exposed. |
| OB-5 | No centralized log aggregation setup | Medium | Medium | No ELK, Loki, or CloudWatch integration. Logs are only in container stdout. |
| OB-6 | No alerting or monitoring dashboards | Medium | Large | No Grafana dashboards, alerts, or runbooks defined. |
| OB-7 | Tracing coverage unknown | Low | Small | Feign calls are traced but no custom spans for business logic (e.g., Keycloak calls, DB transactions). |

---

## 7. Resilience

### Current State
- `wait-for-it.sh` scripts handle startup ordering in Docker
- Optimistic locking (`@Version`) on audited entities
- `@Transactional` on `TransactionService` for fund transfers

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| RE-1 | No circuit breakers | High | Medium | Feign calls to Core Banking Service have no circuit breaker (Resilience4j or Hystrix). If core service is slow/down, all upstream services hang or fail. |
| RE-2 | No retry policies | High | Small | No retry logic on Feign calls. Transient network issues cause immediate failure. |
| RE-3 | No timeout configuration | High | Small | No Feign client timeouts, connection timeouts, or read timeouts configured. Default infinite/very long timeouts risk thread exhaustion. |
| RE-4 | No fallback behavior | Medium | Medium | No graceful degradation. If Core Banking is down, Fund Transfer and Utility Payment services return raw exceptions. |
| RE-5 | Fund transfer is not idempotent | Critical | Medium | No idempotency key on fund transfers. Retrying a failed request could result in double-debit. Each call creates a new transfer entity unconditionally. |
| RE-6 | No distributed transaction management | High | Large | Fund Transfer Service saves local record, then calls Core Banking. If the Feign call fails after local save, the local record shows `PENDING` forever with no compensation/saga. |
| RE-7 | No database connection pooling configuration | Medium | Small | No HikariCP tuning (pool size, timeout, leak detection). Using Spring Boot defaults which may be inadequate under load. |
| RE-8 | Balance calculation race condition | Critical | Medium | `TransactionService.internalFundTransfer()` reads balance, then updates in separate steps without pessimistic locking. Concurrent transfers can overdraw accounts. |
| RE-9 | No dead letter queue or failed transaction recovery | Medium | Large | Failed transactions (status `PENDING`/`PROCESSING`) have no automated retry, reconciliation, or alerting mechanism. |

---

## Summary by Severity

| Severity | Count | Key Items |
|----------|-------|-----------|
| **Critical** | 5 | Input validation missing (SE-1), credential exposure (SE-2), exception info leak (EH-2), no tests for most services (TE-1), race condition on balance (RE-8), non-idempotent transfers (RE-5) |
| **High** | 9 | Wrong HTTP status codes (EH-1), no Feign error handling (EH-4), no integration/contract tests (TE-2/3), no downstream auth (SE-3), no RBAC (SE-4), no circuit breakers (RE-1), no retries/timeouts (RE-2/3), no CI pipeline (TE-6), no saga pattern (RE-6) |
| **Medium** | 16 | Duplicated code (CO-1), inconsistent errors (EH-3), structured logging (OB-1), metrics (OB-4), rate limiting (SE-7), API response format (AD-2), various others |
| **Low** | 9 | No multi-project build (CO-2), formatting (CO-5), HATEOAS (AD-7), custom health checks (OB-3), etc. |

**Total gaps identified: 39**
