# Engineering Standards Gap Analysis

This document compares the current codebase against industry engineering best practices and documents gaps with severity ratings and effort estimates.

---

## 1. Code Organization

### Current State
- Each microservice follows a consistent package structure: `controller`, `service`, `model` (entity/dto/mapper), `repository`, `exception`, `configuration`.
- No shared library (common module) exists; DTOs, exceptions, mappers, and audit classes are **duplicated** across services.
- No Gradle multi-module build; each service is an independent Gradle project with its own `build.gradle`.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CO-1 | Duplicated code across services | High | Medium | `AuditAware`, `BaseMapper`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, filter classes, and Feign config are copy-pasted across 3-4 services with minor variations. |
| CO-2 | No multi-module Gradle build | Medium | Medium | Each service manages its own dependencies independently, leading to version drift risk and no dependency locking. |
| CO-3 | Inconsistent error response construction | Medium | Small | Core Banking uses `ErrorResponse.builder()`, Fund Transfer uses `new ErrorResponse(...)` constructor - different instantiation patterns for the same concept. |
| CO-4 | Mappers instantiated as fields, not beans | Low | Small | Mappers like `BankAccountMapper`, `FundTransferMapper` are `new`-ed inline instead of being Spring-managed beans, preventing injection and testing. |
| CO-5 | `AuditAware` class placed in `model.dto` but is a JPA `@MappedSuperclass` | Low | Small | Confusing package placement - it's an entity concern, not a DTO concern. |

---

## 2. Error Handling

### Current State
- Each service has a `GlobalExceptionHandler` annotated with `@ControllerAdvice`.
- Custom exception hierarchy: `SimpleBankingGlobalException` -> `EntityNotFoundException`, `InsufficientFundsException`, etc.
- Error response format: `{code, message}`.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| EH-1 | Generic catch-all returns raw exception as string | Critical | Small | `handleException(Exception e)` returns `"Exception occur inside API " + e` which leaks stack traces, class names, and internal details to clients. |
| EH-2 | All custom errors return HTTP 400 Bad Request | High | Small | `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422. Currently everything is 400. |
| EH-3 | No error response for validation failures | High | Medium | No `@Valid` annotations on request bodies, no `MethodArgumentNotValidException` handler. Invalid data passes through unchecked. |
| EH-4 | Feign error decoder is incomplete | Medium | Small | `CustomFeignErrorDecoder` exists in user-service but not used consistently. No mapping of downstream HTTP errors to appropriate upstream responses. |
| EH-5 | No correlation/trace ID in error responses | Medium | Small | Error responses don't include a trace ID for debugging, despite Zipkin being configured. |
| EH-6 | Inconsistent exception class hierarchy across services | Medium | Small | User service has `InvalidEmailException`, `UserAlreadyRegisteredException`, etc. but fund-transfer and utility-payment services have no service-specific exceptions. |

---

## 3. Testing

### Current State
- Core Banking Service has unit tests for `AccountService`, `TransactionService`, and `UserService` (using Mockito, no Spring context).
- All other services only have the default `*ApplicationTests` class (Spring context load test).
- Test database: H2 in-memory.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| T-1 | No tests for User Service, Fund Transfer Service, or Utility Payment Service | Critical | Large | These services contain critical business logic (Keycloak integration, inter-service calls) with zero test coverage. |
| T-2 | No integration tests | High | Large | No tests verify actual HTTP endpoints, database interactions with real schemas, or Feign client behavior. |
| T-3 | No contract tests between services | High | Large | OpenFeign clients have no consumer-driven contract tests (e.g., Spring Cloud Contract or Pact). Breaking changes in Core Banking API would not be caught. |
| T-4 | No test for API Gateway routing or security | High | Medium | Gateway security rules (permitAll, authenticated) are untested. |
| T-5 | Core Banking tests don't test controller layer | Medium | Medium | Only service-layer unit tests exist; no `@WebMvcTest` or MockMvc tests for controllers. |
| T-6 | No test coverage reporting | Medium | Small | No JaCoCo or equivalent coverage plugin configured. |
| T-7 | H2 dialect differences may mask MySQL-specific issues | Low | Medium | Tests run on H2 but production uses MySQL; SQL dialect differences could cause production failures. |

---

## 4. Security

### Current State
- API Gateway enforces OAuth2 JWT validation (Keycloak).
- User registration endpoint is publicly accessible (`permitAll`).
- Actuator endpoints are publicly accessible.
- Gateway propagates JWT principal as `X-Auth-Id` header to downstream services.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| S-1 | No input validation on any request body | Critical | Medium | No `@Valid`, `@NotNull`, `@Size`, `@Min` annotations. Null amounts, empty account numbers, and negative values can be submitted. |
| S-2 | Actuator endpoints exposed without authentication | High | Small | `/actuator/**` is `permitAll` - exposes health, env, beans, configprops to unauthenticated users. Should restrict to management-only endpoints. |
| S-3 | Downstream services have no authentication | High | Medium | User, Fund Transfer, Utility Payment, and Core Banking services accept any request without verifying the `X-Auth-Id` header's authenticity. A direct call bypassing the gateway succeeds. |
| S-4 | Hardcoded credentials in docker-compose | High | Small | MySQL root password (`woVERANKliGharym`), app user password (`oPItyPticIAt`), Keycloak admin password (`password`) are in version control. |
| S-5 | Keycloak singleton not thread-safe | Medium | Small | `KeycloakProperties.getInstance()` has a classic double-check locking issue (no synchronization, non-volatile static field). |
| S-6 | Password sent in plain text in User DTO | Medium | Small | The `User` DTO includes a `password` field that could be serialized in responses if not carefully excluded. |
| S-7 | No rate limiting | Medium | Medium | No rate limiting on any endpoint, including registration and fund transfer. |
| S-8 | CSRF disabled globally | Low | Small | While acceptable for a pure REST API, it should be documented as intentional. |
| S-9 | No dependency vulnerability scanning | Medium | Small | No OWASP dependency-check or Snyk integration in the build pipeline. |

---

## 5. API Design

### Current State
- RESTful URL patterns with `/api/v1` prefix.
- JSON request/response bodies.
- Paginated list endpoints via Spring Data `Pageable`.
- OpenAPI/Swagger annotations (`springdoc-openapi`) present on controllers.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| AD-1 | No API versioning strategy beyond URL prefix | Medium | Medium | V1 prefix exists but no mechanism for introducing V2 without breaking changes. |
| AD-2 | Inconsistent response typing | Medium | Small | Controllers return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`), defeating compile-time type safety and OpenAPI generation. |
| AD-3 | No standard envelope/wrapper for responses | Medium | Medium | Successful responses return raw objects; there's no consistent `{data, meta, pagination}` envelope. |
| AD-4 | PATCH endpoint uses wrong semantics | Low | Small | `PATCH /update/{id}` includes "update" in the URL which is redundant; PATCH verb already implies update. Should be `PATCH /{id}`. |
| AD-5 | No filtering on list endpoints | Low | Medium | List endpoints support pagination but not filtering (e.g., by status, date range, account). |
| AD-6 | OpenAPI spec not auto-generated/published | Medium | Small | `springdoc-openapi-starter-webflux-ui` is included but uses the wrong module for MVC-based services (should be `webmvc-ui`). Swagger UI may not work correctly. |
| AD-7 | No HATEOAS links | Low | Large | No hypermedia links for discoverability between related resources. |

---

## 6. Observability

### Current State
- Spring Boot Actuator included in all services.
- Micrometer tracing with Brave bridge for Zipkin integration.
- Feign calls instrumented via `feign-micrometer`.
- `@Slf4j` logging present in controllers and services.
- Git properties plugin for build info.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| O-1 | No structured logging (JSON) | High | Small | Log output is unstructured text, making it difficult to parse in log aggregation systems (ELK, CloudWatch, etc.). |
| O-2 | Sensitive data logged | High | Small | `request.toString()` logs include passwords (User DTO), account numbers, and amounts in plain text. |
| O-3 | No custom health indicators | Medium | Small | Health checks only report UP/DOWN without checking database connectivity, Keycloak reachability, or downstream service health. |
| O-4 | No Prometheus metrics endpoint | Medium | Small | Despite Actuator + Micrometer, no `micrometer-registry-prometheus` dependency is included for metrics scraping. |
| O-5 | No correlation ID in log messages | Medium | Small | While Zipkin traces exist, MDC-based trace/span IDs aren't consistently included in log patterns. |
| O-6 | No alerting thresholds or SLIs defined | Low | Medium | No documented service-level indicators (latency, error rate, throughput). |
| O-7 | Inconsistent log levels | Low | Small | Some operations log at INFO that should be DEBUG (e.g., "Reading account by ID"). |

---

## 7. Resilience

### Current State
- `wait-for-it.sh` scripts handle startup ordering in Docker.
- `@Transactional` on Core Banking transaction operations.
- Optimistic locking (`@Version`) on audit-aware entities.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| R-1 | No circuit breaker on Feign clients | Critical | Medium | If Core Banking Service is down, Fund Transfer and Utility Payment services will hang indefinitely or throw unhandled exceptions. No Resilience4j or Hystrix integration. |
| R-2 | No timeout configuration on Feign clients | Critical | Small | Default Feign timeouts are very long (60s connect, 60s read). No explicit timeout configuration. |
| R-3 | No retry policy on inter-service calls | High | Small | Transient network failures cause immediate failure with no retry. |
| R-4 | No fallback behavior for degraded services | High | Medium | No graceful degradation - if any downstream service fails, the entire operation fails with a raw error. |
| R-5 | No idempotency on write operations | High | Medium | Fund transfers and payments have no idempotency key. Network retries could cause duplicate transactions. |
| R-6 | Balance calculation has race condition | Critical | Medium | `TransactionService.internalFundTransfer()` reads balance, then updates without pessimistic locking. Concurrent transfers can overdraw accounts. |
| R-7 | No dead letter queue for failed async operations | Medium | Medium | Although RabbitMQ is planned, there's no error handling strategy for failed message processing. |
| R-8 | Available balance calculated incorrectly | High | Small | In `utilPayment()`, line 64 subtracts from `actualBalance` (already reduced on line 63) instead of the original balance, causing double-subtraction on `availableBalance`. |

---

## Summary Statistics

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 1 | 2 | 2 |
| Error Handling | 1 | 2 | 3 | 0 |
| Testing | 1 | 3 | 2 | 1 |
| Security | 1 | 3 | 3 | 2 |
| API Design | 0 | 0 | 4 | 3 |
| Observability | 0 | 2 | 3 | 2 |
| Resilience | 3 | 3 | 2 | 0 |
| **Totals** | **6** | **14** | **19** | **10** |

### Critical Issues Requiring Immediate Attention

1. **R-6:** Race condition in balance updates can cause account overdraft
2. **R-1:** No circuit breakers - cascading failures possible
3. **R-2:** No Feign timeouts - thread pool exhaustion risk
4. **EH-1:** Stack traces leaked to API consumers
5. **S-1:** Zero input validation - malformed data accepted
6. **T-1:** Critical business logic has no test coverage
