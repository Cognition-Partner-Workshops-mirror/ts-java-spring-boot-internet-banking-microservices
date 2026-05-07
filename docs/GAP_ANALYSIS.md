# Engineering Standards Gap Analysis

This document compares the current codebase against industry-standard engineering best practices and documents gaps found across the 6 microservices.

---

## 1. Code Organization

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 1.1 | No multi-project Gradle build | Medium | Small | Each service has an independent `build.gradle` with no shared root `settings.gradle`. Common dependencies and plugin versions are duplicated across all 6 services. |
| 1.2 | Duplicated code across services | High | Medium | `AuditAware`, `BaseMapper`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `ErrorResponse`, `GlobalExceptionHandler`, and `SimpleBankingGlobalException` are copy-pasted across fund-transfer, utility-payment, and user services with slight variations. |
| 1.3 | Inconsistent package structure | Medium | Small | Core banking uses `repository/` package; user service uses `model/repository/`; utility payment uses `repository/` at root. Mapper, DTO, and entity placement varies per service. |
| 1.4 | No shared library / common module | High | Medium | There is no `common` or `shared` library for cross-cutting concerns (DTOs, exceptions, filters, audit). Each service reinvents these independently. |
| 1.5 | Mapper instantiation outside DI | Low | Small | Mappers are instantiated with `new` rather than being Spring beans (e.g., `private FundTransferMapper mapper = new FundTransferMapper()`), bypassing dependency injection. |

---

## 2. Error Handling

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 2.1 | Generic exception handler leaks internal details | Critical | Small | All services have `handleException(Exception e)` that returns `"Exception occur inside API " + e`, exposing stack traces and internal class names to clients. |
| 2.2 | All errors return HTTP 400 | High | Small | Both business exceptions and unexpected errors use `ResponseEntity.badRequest()`. A 404 (EntityNotFoundException), 500 (server errors), and 409 (duplicate registration) should use appropriate status codes. |
| 2.3 | Inconsistent error response format | Medium | Small | The `SimpleBankingGlobalException` handler returns `ErrorResponse` (code + message), but the generic handler returns a raw string. Clients cannot reliably parse errors. |
| 2.4 | No error codes documentation | Low | Small | `GlobalErrorCode` enum exists but is not documented or exposed via API docs. Clients have no contract for error codes. |
| 2.5 | Fund transfer service has no failure handling | High | Medium | If the Feign call to core banking fails after persisting the `PENDING` record, the fund transfer entity is never updated to `FAILED`. There is no compensation/rollback logic. |
| 2.6 | Utility payment service same issue | High | Medium | Same as 2.5 — a failed Feign call leaves `PROCESSING` status permanently with no retry or failure transition. |

---

## 3. Testing

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 3.1 | Only core-banking-service has unit tests | Critical | Large | Fund transfer, utility payment, user service, API gateway, config server, and service registry have only empty application context tests. |
| 3.2 | No integration tests | Critical | Large | There are no tests that verify actual HTTP endpoints, database interactions (with embedded DB), or Feign client behavior. |
| 3.3 | No contract tests | High | Large | With 3 Feign clients calling core banking, there are no consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) to catch API drift. |
| 3.4 | No test containers setup | Medium | Medium | Tests rely on H2 for database but there is no Testcontainers configuration for MySQL-specific behavior validation. |
| 3.5 | Application context tests will fail without config server | High | Small | Default `*ApplicationTests` classes attempt to load full Spring context, which requires Config Server connectivity. No test profile overrides this. |
| 3.6 | Core banking tests are manual mocks (no @MockBean) | Low | Small | Tests use `mock()` directly rather than `@MockBean` with `@SpringBootTest`. This is functional but limits testing to isolated unit scope. |

---

## 4. Security

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 4.1 | No input validation | Critical | Medium | None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) have any `@Valid`, `@NotNull`, `@Positive`, or `@Size` annotations. Null amounts, negative transfers, and empty accounts are accepted. |
| 4.2 | Hardcoded credentials in Docker Compose | High | Small | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), and PostgreSQL password (`password`) are committed in plain text. |
| 4.3 | No authorization beyond authentication | High | Medium | The gateway enforces "is authenticated" but there is no role-based access control. Any authenticated user can approve other users, view all transfers, etc. |
| 4.4 | Keycloak singleton is not thread-safe | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern for the static `keycloakInstance` field, risking double initialization in concurrent requests. |
| 4.5 | CSRF disabled without justification | Low | Small | The gateway disables CSRF for all endpoints. While acceptable for pure-API services, it should be explicitly documented and limited. |
| 4.6 | No rate limiting | Medium | Medium | The API gateway has no rate limiting configuration. Banking APIs without rate limits are vulnerable to brute-force and abuse. |
| 4.7 | Password stored in User DTO response | Critical | Small | The `User` DTO includes a `password` field that could be serialized in responses. There is no `@JsonProperty(access = WRITE_ONLY)` or separate request/response DTOs. |
| 4.8 | No dependency vulnerability scanning | Medium | Small | No OWASP dependency-check, Snyk, or similar plugin configured in any build.gradle. |

---

## 5. API Design

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 5.1 | Raw `ResponseEntity` without type parameters | Medium | Small | All controller methods return untyped `ResponseEntity` (not `ResponseEntity<T>`), losing compile-time type safety and degrading OpenAPI generation. |
| 5.2 | No API versioning strategy | Medium | Small | While paths include `/v1/`, there is no documented versioning strategy, and no infrastructure for running multiple versions simultaneously. |
| 5.3 | Inconsistent resource naming | Low | Small | `/bank-account/{account_number}` uses underscores in path variable; `/bank-users/register` uses hyphens. Mixed conventions (`util-account` vs `utility-payment`). |
| 5.4 | No pagination metadata in responses | Medium | Small | List endpoints accept `Pageable` but return `List<T>` instead of `Page<T>`, losing total count, page number, and navigation metadata. |
| 5.5 | No filtering or sorting parameters | Low | Medium | List endpoints only support basic pagination. No query parameters for filtering by status, date range, amount, etc. |
| 5.6 | OpenAPI documentation incomplete | Medium | Small | Swagger/OpenAPI is included as a dependency but uses the wrong starter (`springdoc-openapi-starter-webflux-ui` for WebMVC services). Response types are not documented in annotations. |
| 5.7 | POST `/register` should return 201 | Low | Small | User registration returns HTTP 200 instead of 201 Created with a Location header. |
| 5.8 | No HATEOAS or resource links | Low | Large | Responses contain no hypermedia links for discoverability. |

---

## 6. Observability

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 6.1 | No structured logging | High | Medium | Services use default logback with unstructured string messages. No JSON logging format for log aggregation tools (ELK, Datadog, etc.). |
| 6.2 | Sensitive data in log messages | Critical | Small | `fundTransferRequest.toString()` logs include amount and account numbers. `user.toString()` may log passwords. |
| 6.3 | No health check customization | Medium | Small | While `spring-boot-starter-actuator` is included, there are no custom health indicators for database connectivity, Keycloak availability, or Feign client reachability. |
| 6.4 | No metrics endpoints configured | Medium | Small | Actuator is present but no Prometheus or Micrometer metrics configuration exists beyond the default. |
| 6.5 | Tracing coverage unclear | Low | Small | Zipkin dependencies are present in all services, but there is no explicit configuration for sampling rate, custom spans, or baggage propagation. |
| 6.6 | No log correlation IDs | Medium | Small | While Zipkin tracing exists, log messages don't include trace/span IDs in the log pattern for correlation. |
| 6.7 | No alerting or monitoring configuration | Medium | Medium | No Grafana dashboards, alerting rules, or monitoring configuration is provided. |

---

## 7. Resilience

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 7.1 | No circuit breakers | Critical | Medium | Feign clients call core-banking-service synchronously with no circuit breaker (Resilience4j or Hystrix). A core banking outage cascades to all dependent services. |
| 7.2 | No retry policies | High | Small | Feign clients have no retry configuration. Transient network failures immediately fail the request. |
| 7.3 | No timeout configuration | High | Small | No explicit connection/read timeouts configured for Feign clients or RestTemplate calls. Requests can hang indefinitely. |
| 7.4 | No fallback behavior | High | Medium | When core-banking-service is unavailable, fund transfer and utility payment services throw unhandled exceptions with no graceful degradation. |
| 7.5 | No idempotency keys | High | Medium | Fund transfer and utility payment endpoints have no idempotency mechanism. Retried requests (e.g., due to network timeout) can result in duplicate transactions. |
| 7.6 | Transaction boundaries are too narrow | High | Medium | The `@Transactional` on core banking's `TransactionService` covers only the local DB operations. The distributed transaction across services (Feign call + local DB save) has no saga or outbox pattern. |
| 7.7 | No dead letter / compensation mechanism | Medium | Large | Failed transactions (stuck in `PENDING`/`PROCESSING`) have no scheduled job, dead letter queue, or manual recovery mechanism. |
| 7.8 | No bulkhead isolation | Medium | Medium | All Feign calls share the same thread pool. A slow response from core banking can exhaust threads and block all operations. |
| 7.9 | `wait-for-it.sh` with fixed timeouts | Low | Small | Docker startup uses 50-second timeouts. If infrastructure takes longer, services fail without retry. |

---

## Summary Matrix

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 2 | 2 | 1 |
| Error Handling | 1 | 3 | 1 | 1 |
| Testing | 2 | 2 | 1 | 1 |
| Security | 2 | 2 | 3 | 1 |
| API Design | 0 | 0 | 4 | 4 |
| Observability | 1 | 1 | 4 | 1 |
| Resilience | 1 | 5 | 2 | 1 |
| **Total** | **7** | **15** | **17** | **10** |
