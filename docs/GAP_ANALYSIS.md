# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across 7 dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### Current State

Each service follows a similar package structure under `com.javatodev.finance`:
```
controller/
configuration/
exception/
model/
  dto/
  entity/
  mapper/
  repository/ (or repository/ at top level)
service/
  rest/ (Feign clients)
```

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CO-1 | **No shared library / common module** | High | Medium | Exception classes (`SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`), base mapper (`BaseMapper`), audit entity (`AuditAware`), filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`), and DTOs are copy-pasted across 4 services. Any bug fix or change must be applied independently in each service. |
| CO-2 | **No multi-project Gradle build** | Medium | Medium | Each service is a standalone Gradle project with its own `build.gradle` and `settings.gradle`. There is no root `settings.gradle` for coordinated builds, version alignment, or dependency locking. |
| CO-3 | **Inconsistent package placement** | Low | Small | Repository interfaces are under `model.repository` in some services (user-service, fund-transfer-service) but under `repository` in others (core-banking-service). |
| CO-4 | **Mapper instantiation inconsistency** | Low | Small | Mappers are instantiated inline (`new FundTransferMapper()`) rather than injected as Spring beans, mixing DI and manual construction within `@Service` classes that otherwise use constructor injection. |
| CO-5 | **`build/` and `bin/` directories committed** | Medium | Small | Compiled output directories (`build/resources/main/`, `bin/main/`) are checked into version control. The `.gitignore` does not exclude them. |
| CO-6 | **`.DS_Store` committed** | Low | Small | macOS metadata file `.DS_Store` is tracked in git. |

---

## 2. Error Handling

### Current State

Each business service has a `@ControllerAdvice` (`GlobalExceptionHandler`) that catches `SimpleBankingGlobalException` and a generic `Exception` fallback. Custom exceptions extend `SimpleBankingGlobalException`.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| EH-1 | **All errors return HTTP 400 Bad Request** | Critical | Small | The generic `Exception` handler and all custom exception handlers return `400 Bad Request` regardless of the actual error. `EntityNotFoundException` should return `404`, server errors should return `500`, validation errors `422`, etc. |
| EH-2 | **Generic exception handler leaks stack traces** | Critical | Small | The catch-all handler returns `"Exception occur inside API " + e`, which serializes the full exception (including stack trace) to the client. This is an information disclosure vulnerability. |
| EH-3 | **Inconsistent `ErrorResponse` construction** | Medium | Small | Core-banking and utility-payment services use `ErrorResponse.builder()`, fund-transfer service uses `new ErrorResponse(code, message)`. The `ErrorResponse` class uses `@Builder` in some copies and constructor in others. |
| EH-4 | **No error response for Feign failures** | High | Medium | When a Feign call fails (e.g., core-banking-service is down), the exception propagates as a `FeignException` which is caught by the generic handler and returned as a raw string with HTTP 400. There is no `FeignErrorDecoder` in fund-transfer or utility-payment services (only a `CustomFeignErrorDecoder` exists in user-service but is not applied). |
| EH-5 | **`SimpleBankingGlobalException` shadows `RuntimeException.message`** | Medium | Small | The class declares its own `private String message` field which shadows `Throwable.message`. The single-arg constructor calls `super(message)` but doesn't set the field, causing inconsistent behavior between `getMessage()` (Lombok getter) and `super.getMessage()`. |
| EH-6 | **No validation error handling** | High | Small | No `@Valid` annotations on request bodies, no `MethodArgumentNotValidException` handler. Invalid input passes through unchecked. |
| EH-7 | **Raw `ResponseEntity` without type parameters** | Low | Small | All controller methods return raw `ResponseEntity` instead of typed `ResponseEntity<T>`, losing compile-time type safety and OpenAPI schema generation accuracy. |

---

## 3. Testing

### Current State

Only **core-banking-service** has meaningful unit tests (3 test classes, ~20 test methods covering `AccountService`, `TransactionService`, and `UserService`). All other services have only empty `ApplicationTests` classes that load the Spring context.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| TE-1 | **No tests for 5 of 6 services** | Critical | Large | internet-banking-user-service, fund-transfer-service, utility-payment-service, api-gateway, and config-server have zero functional tests. Only empty context-load tests exist. |
| TE-2 | **No integration tests** | High | Large | No tests verify actual HTTP endpoints (MockMvc / WebTestClient), database queries, or Feign client behavior. All existing tests are pure unit tests with mocks. |
| TE-3 | **No contract tests between services** | High | Large | Services communicate via Feign but there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to verify API compatibility. Breaking changes in core-banking-service could silently break all consumers. |
| TE-4 | **No test coverage reporting** | Medium | Small | No JaCoCo or similar coverage plugin configured. No way to measure or enforce coverage thresholds. |
| TE-5 | **Context-load tests will fail without infrastructure** | Medium | Small | The empty `ApplicationTests` classes attempt to load the full Spring context, which requires Eureka, Config Server, MySQL, and Keycloak to be running. These tests will fail in CI without infrastructure. The core-banking-service test profile disables some dependencies, but other services lack test profiles entirely. |
| TE-6 | **No test profiles for most services** | Medium | Small | Only core-banking-service has `src/test/resources/application.yml`. Other services have no test configuration, meaning tests would try to connect to real MySQL and Eureka. |

---

## 4. Security

### Current State

The API Gateway enforces OAuth2 JWT authentication via Keycloak. The user registration endpoint is publicly accessible. Actuator endpoints are publicly accessible. Individual microservices behind the gateway have no security of their own.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| SE-1 | **Downstream services have no authentication** | Critical | Medium | If any service port is exposed (e.g., 8083-8092), requests bypass the gateway and hit services directly with no auth check. Services trust the `X-Auth-Id` header blindly. |
| SE-2 | **No input validation** | Critical | Small | No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size` or any Bean Validation annotations on request DTOs. A fund transfer with negative amount or null accounts would be processed. |
| SE-3 | **Hardcoded credentials in source code** | Critical | Small | `docker-compose.yml` contains MySQL root password (`woVERANKliGharym`), Keycloak admin credentials (`admin`/`password`), and database user credentials (`javatodev_development`/`oPItyPticIAt`). `privileges.sql` also contains credentials. |
| SE-4 | **CSRF disabled at gateway** | Medium | Small | `ServerHttpSecurity.CsrfSpec::disable` - acceptable for pure API (no browser forms), but should be documented as an intentional decision. |
| SE-5 | **No role-based access control** | High | Medium | All authenticated users can access all endpoints. No `@PreAuthorize`, no role checks. Any authenticated user can approve other users, initiate transfers on any account, etc. |
| SE-6 | **Keycloak singleton is not thread-safe** | Medium | Small | `KeycloakProperties.getInstance()` uses a classic double-check-less singleton pattern (`if (keycloakInstance == null)`) which is not thread-safe. Could create multiple instances under concurrent access. |
| SE-7 | **Password sent in plain JSON** | Medium | Small | User registration accepts `password` in the JSON body. While HTTPS should protect it in transit, the password could be logged (see logging concerns) or stored in request logs. |
| SE-8 | **Actuator endpoints publicly accessible** | Medium | Small | All actuator endpoints (`/actuator/**`) are permitted without authentication at the gateway level. This exposes health, info, env, and potentially sensitive endpoints. |
| SE-9 | **No dependency vulnerability scanning** | Medium | Small | No OWASP Dependency Check, Snyk, or Dependabot configured. No mechanism to detect known CVEs in dependencies. |
| SE-10 | **Single database user with broad privileges** | Medium | Small | All services share one MySQL user (`javatodev_development`) with `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on all databases. Services should have scoped credentials. |

---

## 5. API Design

### Current State

APIs follow basic REST conventions with versioned paths (`/api/v1/...`). OpenAPI annotations are present on controllers. Pagination is supported via Spring's `Pageable`.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| AD-1 | **Inconsistent resource naming** | Medium | Small | Core-banking uses `/api/v1/account/bank-account/{account_number}` (hyphenated with nested resource), user-service uses `/api/v1/bank-users/register` (verb as resource). REST convention prefers nouns. |
| AD-2 | **No consistent response envelope** | Medium | Medium | Success responses return raw entities; error responses return `ErrorResponse` or raw strings. No standard envelope (e.g., `{ data: ..., errors: ..., meta: ... }`). |
| AD-3 | **POST for user registration doesn't return 201** | Low | Small | `POST /api/v1/bank-users/register` returns `200 OK` instead of `201 Created` with a `Location` header. |
| AD-4 | **No API versioning strategy beyond URL prefix** | Low | Small | `/api/v1/` is hardcoded in each controller. No mechanism for running v1 and v2 simultaneously or header-based versioning. Current approach is acceptable but should be documented. |
| AD-5 | **No filtering or search on list endpoints** | Medium | Small | List endpoints only support pagination (via Spring `Pageable`). No filtering by status, date range, account number, etc. |
| AD-6 | **Swagger dependency mismatch** | Medium | Small | Services use `springdoc-openapi-starter-webflux-ui` but most services are **Spring MVC** (not WebFlux). Only the API Gateway is WebFlux-based. Should use `springdoc-openapi-starter-webmvc-ui` for MVC services. |
| AD-7 | **Raw `ResponseEntity` obscures OpenAPI schemas** | Medium | Small | Without type parameters on `ResponseEntity`, Springdoc cannot generate accurate response schemas automatically. |
| AD-8 | **No HATEOAS / hypermedia links** | Low | Medium | No links to related resources in responses (e.g., fund transfer response doesn't link to the transaction or accounts). |

---

## 6. Observability

### Current State

All services include Spring Boot Actuator and Micrometer tracing with Zipkin reporter. The API Gateway has a `GatewayConfiguration` that propagates user identity via `X-Auth-Id` header. Services use `@Slf4j` for logging.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| OB-1 | **Inconsistent logging** | Medium | Small | Some controllers log request details, others don't. `FundTransferService` has a string concatenation bug in logging: `"Sending fund transfer request {}" + request.toString()` (should use `{}` placeholder only). |
| OB-2 | **No structured logging** | Medium | Medium | All logging uses plain text format. No JSON logging configuration for production, making log aggregation and parsing difficult. |
| OB-3 | **Sensitive data in logs** | High | Small | User registration logs the entire `User` object (`request.toString()`) which may include the password field. Fund transfer logs include account numbers. No log redaction. |
| OB-4 | **No custom health checks** | Medium | Small | Services rely on default Spring Boot health indicators. No custom health checks for downstream dependencies (e.g., "can we reach core-banking-service?", "is Keycloak reachable?"). |
| OB-5 | **No Prometheus metrics endpoint** | Medium | Small | Despite Prometheus being listed in the tech stack (README), there is no `micrometer-registry-prometheus` dependency in any `build.gradle`. Metrics are collected but not exposed for Prometheus scraping. |
| OB-6 | **No custom business metrics** | Medium | Medium | No counters or gauges for business events (e.g., transfers per minute, payment success/failure rates, registration counts). |
| OB-7 | **No request/response logging at gateway** | Low | Small | The gateway's `GatewayConfiguration` only extracts the principal. No access logging of request path, response status, or latency. |
| OB-8 | **No correlation ID propagation** | Medium | Small | While Zipkin trace IDs exist, there is no explicit correlation ID in API responses or logs that clients can use for support requests. |

---

## 7. Resilience

### Current State

Services communicate synchronously via Feign. Startup ordering is handled by `wait-for-it.sh` scripts in Docker. No resilience libraries are configured.

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| RE-1 | **No circuit breakers** | Critical | Medium | If core-banking-service goes down, fund-transfer and utility-payment services will hang on Feign calls until timeout, potentially exhausting thread pools and cascading failures. No Resilience4j or Spring Cloud Circuit Breaker configured. |
| RE-2 | **No retry policies** | High | Small | Transient failures (network blips, temporary unavailability) cause immediate failure. No Spring Retry or Resilience4j retry configured on Feign clients. |
| RE-3 | **No timeout configuration** | High | Small | Feign clients use default timeouts (which may be infinite or very long). No explicit `connectTimeout` or `readTimeout` configured. A slow downstream service will block the caller indefinitely. |
| RE-4 | **No fallback behavior** | High | Medium | When Feign calls fail, exceptions propagate directly to the client. No fallback responses (e.g., cached data, graceful degradation). |
| RE-5 | **No bulkhead / thread pool isolation** | Medium | Medium | All Feign calls share the same thread pool. A slow core-banking-service will exhaust all threads, preventing the service from handling any requests. |
| RE-6 | **No rate limiting** | Medium | Medium | No rate limiting at the gateway or service level. A single client can overwhelm the system. |
| RE-7 | **No idempotency on write operations** | High | Medium | `POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency keys. Network retries or client retries could cause duplicate transactions. |
| RE-8 | **Transaction integrity across services** | Critical | Large | Fund transfer and utility payment involve state changes in two services (e.g., fund-transfer-service saves `PENDING` then calls core-banking-service). If the Feign call succeeds but the status update to `SUCCESS` fails, the local record stays `PENDING` while money has moved. No saga pattern or outbox pattern implemented. |
| RE-9 | **`wait-for-it.sh` is a deployment-time only solution** | Low | Small | Runtime failures (service restart, network partition) are not handled. `wait-for-it.sh` only ensures startup ordering but provides no runtime resilience. |
| RE-10 | **No graceful shutdown** | Medium | Small | No `server.shutdown=graceful` configuration. In-flight requests may be dropped during deployment. |

---

## Summary Matrix

| Dimension | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 2 | 3 | 6 |
| Error Handling | 2 | 2 | 2 | 1 | 7 |
| Testing | 1 | 2 | 3 | 0 | 6 |
| Security | 3 | 1 | 4 | 0 | 8* |
| API Design | 0 | 0 | 4 | 3 | 7* |
| Observability | 0 | 1 | 5 | 2 | 8 |
| Resilience | 2 | 3 | 3 | 1 | 9* |
| **Total** | **8** | **10** | **23** | **10** | **51** |

*SE has 10 items, AD has 8 items, RE has 10 items - counts reflect the numbered gaps above.
