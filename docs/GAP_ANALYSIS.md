# Engineering Standards Gap Analysis

This document compares the current codebase against industry best practices and identifies gaps across key engineering dimensions.

---

## 1. Code Organization

### Current State
- Each service is an independent Gradle project with no shared parent build file
- Package structure is consistent across services (`com.javatodev.finance` root package)
- Services follow a standard layered architecture: `controller → service → repository`
- Code duplication exists across services (e.g., `BaseMapper`, `AuditAware`, `ErrorResponse`, `GlobalExceptionHandler`, filter classes)
- No shared library module for common code

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| CO-1 | **No multi-project Gradle build** — each service is a standalone project with duplicated build configuration (Spring Boot version, dependency management, plugins). Version drift risk. | Medium | Medium |
| CO-2 | **Duplicated common code** — `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext/Holder`, `AppAuthUserFilter` are copy-pasted across services with minor variations. | High | Medium |
| CO-3 | **No shared DTO/API contract module** — Request/response DTOs (e.g., `FundTransferRequest`, `AccountResponse`) are duplicated in both the calling and called services. Changes require updates in multiple places. | High | Medium |
| CO-4 | **Inconsistent indentation style** — Some `build.gradle` files use tabs, others use spaces. | Low | Small |

---

## 2. Error Handling

### Current State
- Each service has a `GlobalExceptionHandler` using `@ControllerAdvice`
- Custom exception hierarchy: `SimpleBankingGlobalException` as base, with `EntityNotFoundException`, `InsufficientFundsException`, etc.
- `ErrorResponse` DTO with `code` and `message` fields
- All errors return HTTP 400 (Bad Request) regardless of actual error type

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| EH-1 | **All exceptions return HTTP 400** — `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422. The catch-all `Exception` handler also returns 400 instead of 500. | Critical | Small |
| EH-2 | **Inconsistent error response format** — The catch-all handler returns a raw string (`"Exception occur inside API " + e`) instead of the structured `ErrorResponse` format. Stack trace leaks to clients. | Critical | Small |
| EH-3 | **No error handling for Feign client failures** — Only the user-service has a `CustomFeignErrorDecoder`. Fund-transfer and utility-payment services have no decoder, so downstream failures surface as opaque 500 errors. | High | Small |
| EH-4 | **Missing validation error handling** — No `MethodArgumentNotValidException` or `ConstraintViolationException` handler for input validation errors. | Medium | Small |
| EH-5 | **Exception message exposes internals** — The generic handler concatenates the exception object directly, potentially leaking sensitive information (stack traces, SQL errors). | High | Small |

---

## 3. Testing

### Current State
- Core Banking Service: 3 unit test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with proper Mockito usage
- Other services: Only auto-generated Spring Boot context-load tests (empty `*ApplicationTests` classes)
- Test dependencies include `spring-boot-starter-test` and H2 in-memory DB
- No integration tests, contract tests, or end-to-end tests

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| T-1 | **Minimal unit test coverage** — Only core-banking-service has meaningful tests. Fund-transfer, user-service, and utility-payment have zero business logic tests. | Critical | Large |
| T-2 | **No integration tests** — No tests verify database interactions, Feign client calls, or Spring context wiring with real dependencies. | High | Large |
| T-3 | **No contract tests** — No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. API changes can break consumers silently. | High | Large |
| T-4 | **No test coverage reporting** — No JaCoCo or similar plugin configured to measure and enforce coverage thresholds. | Medium | Small |
| T-5 | **No end-to-end tests** — No automated tests that exercise the full flow through the API gateway. | Medium | Large |

---

## 4. Security

### Current State
- API Gateway enforces OAuth2/JWT authentication via Keycloak
- User registration endpoint is publicly accessible (permit all)
- Actuator endpoints are publicly accessible (permit all)
- CSRF is disabled on the gateway
- `X-Auth-Id` header propagates user identity to downstream services
- Downstream services trust the header without re-validation

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| S-1 | **No input validation** — No `@Valid`, `@NotNull`, `@Size`, etc. annotations on any request DTOs. Arbitrary input is accepted. | Critical | Small |
| S-2 | **Hardcoded credentials in source code** — MySQL root password (`woVERANKliGharym`), DB user password (`oPItyPticIAt`), Keycloak admin password (`password`) are committed in `docker-compose.yml` and `privileges.sql`. | Critical | Medium |
| S-3 | **Downstream services trust X-Auth-Id header blindly** — Any request with the header is treated as authenticated. A direct call bypassing the gateway could spoof identity. | High | Medium |
| S-4 | **Actuator endpoints exposed without authentication** — Health, info, env, and other actuator endpoints are publicly accessible via gateway. Sensitive data exposure risk. | High | Small |
| S-5 | **No role-based access control (RBAC)** — All authenticated users can access all endpoints. No distinction between admin and regular user roles for operations like user approval. | High | Medium |
| S-6 | **Password stored in plain DTO** — `User.password` field is part of the response DTO (though not persisted locally). Risk of password logging/exposure. | Medium | Small |
| S-7 | **No dependency vulnerability scanning** — No OWASP dependency-check, Snyk, or similar tool in the build pipeline. | Medium | Small |
| S-8 | **CSRF disabled globally** — While acceptable for pure API services, the justification is not documented and no CORS policy is configured. | Low | Small |

---

## 5. API Design

### Current State
- RESTful URL patterns with version prefix (`/api/v1/...`)
- Standard HTTP methods (GET, POST, PATCH)
- Pagination support via Spring's `Pageable`
- OpenAPI/Swagger annotations present (`springdoc-openapi-starter-webflux-ui`)
- Raw `ResponseEntity` without generics in most controllers

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| A-1 | **No generic type on ResponseEntity** — Most controller methods return raw `ResponseEntity` without type parameters, losing compile-time safety and breaking Swagger documentation. | Medium | Small |
| A-2 | **Inconsistent response wrapping** — Some endpoints return raw entities, others return wrapper objects. No standard envelope (e.g., `{data, error, meta}`). | Medium | Medium |
| A-3 | **No pagination metadata in list responses** — `readAllTransfers()` returns `List<FundTransfer>` instead of a paginated response with total count, page number, etc. | Medium | Small |
| A-4 | **No filtering or search support** — List endpoints only support pagination, not filtering by date range, status, amount, etc. | Low | Medium |
| A-5 | **No API versioning strategy** — While `/v1/` is in the URL, there's no mechanism or documentation for introducing `/v2/` with backward compatibility. | Low | Small |
| A-6 | **Inconsistent naming conventions** — Mix of `snake_case` (`account_number`, `account_name`) and no case in path variables across services. | Low | Small |
| A-7 | **No HATEOAS or resource links** — Responses don't include navigational links to related resources. | Low | Medium |

---

## 6. Observability

### Current State
- Spring Boot Actuator included in all services (health, info endpoints)
- Micrometer tracing with Brave bridge + Zipkin reporter for distributed tracing
- `@Slf4j` logging annotation used in services and controllers
- Git properties plugin generates build metadata
- No structured logging format
- No metrics endpoint (Prometheus)

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| O-1 | **No Prometheus metrics endpoint** — Despite `spring-boot-starter-actuator`, no Micrometer Prometheus registry is configured. No custom business metrics. | High | Small |
| O-2 | **Inconsistent logging** — Some log statements include request data (`toString()`), others don't. No correlation ID in logs. No structured JSON logging for production. | Medium | Medium |
| O-3 | **Sensitive data in logs** — `request.toString()` in controllers may log passwords, account numbers, or other PII (e.g., `FundTransferRequest.toString()` logs account numbers). | High | Small |
| O-4 | **No health check customization** — Default health checks only. No custom health indicators for downstream dependencies (Keycloak connectivity, core-banking availability). | Medium | Small |
| O-5 | **No alerting or monitoring setup** — No Grafana dashboards, alerting rules, or runbook references. | Low | Large |
| O-6 | **Log levels not configurable at runtime** — No Spring Boot Admin or actuator log-level management endpoint exposed. | Low | Small |

---

## 7. Resilience

### Current State
- `wait-for-it.sh` scripts ensure services wait for dependencies on container startup
- No circuit breakers, retry policies, or timeout configuration
- Feign clients use default settings (no timeout, no retry)
- No fallback behavior defined for service failures
- Transactions are `@Transactional` in core-banking but not in calling services

### Gaps

| # | Gap | Severity | Effort |
|---|-----|----------|--------|
| R-1 | **No circuit breakers** — If core-banking-service is down, fund-transfer and utility-payment services will block indefinitely and exhaust thread pools. | Critical | Medium |
| R-2 | **No timeout configuration** — Feign clients have no connect/read timeouts. A slow downstream service will cascade failures. | Critical | Small |
| R-3 | **No retry policies** — Transient failures (network blips, temporary unavailability) are not retried. | High | Small |
| R-4 | **No fallback behavior** — No `@FeignClient(fallback=...)` or Resilience4j fallback methods defined. Users see raw errors on downstream failures. | High | Medium |
| R-5 | **Partial transaction on Feign failure** — In `FundTransferService.fundTransfer()`, a local entity is saved with `PENDING` status, then Feign is called. If Feign fails, the record stays `PENDING` forever with no recovery mechanism. | Critical | Medium |
| R-6 | **No rate limiting** — API gateway has no rate limiting. Vulnerable to abuse and DoS. | Medium | Medium |
| R-7 | **No bulkhead pattern** — All outgoing requests share the same thread pool. One slow service can consume all threads. | Medium | Medium |
| R-8 | **No dead letter queue for failed transactions** — Failed fund transfers and payments are silently left in `PENDING`/`PROCESSING` state with no reconciliation. | High | Large |

---

## Summary Matrix

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 2 | 1 | 1 |
| Error Handling | 2 | 2 | 1 | 0 |
| Testing | 1 | 2 | 2 | 0 |
| Security | 2 | 3 | 2 | 1 |
| API Design | 0 | 0 | 3 | 4 |
| Observability | 0 | 2 | 2 | 2 |
| Resilience | 3 | 2 | 2 | 0 |
| **Totals** | **8** | **13** | **13** | **8** |
