# Engineering Standards Gap Analysis

This document details engineering standards gaps found through static code analysis of all 7 services in the internet banking microservices platform. Each gap includes a severity rating and effort estimate.

**Severity Scale:** Critical > High > Medium > Low
**Effort Scale:** Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## Code Organization Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **No shared library / multi-module build** -- `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ErrorResponse`, `BaseMapper`, `AuditAware`, `AuditorAwareConfig`, `AuditConfig`, `AppAuthUserFilter`, `ApiRequestContextHolder` are all copy-pasted across 3-4 services. | High | Medium |
| 2 | **Inconsistent Feign client patterns** -- Fund transfer service uses `@FeignClient` interface (`BankingCoreFeignClient`), while user-service and utility-payment-service use a class-based `BankingCoreRestClient`. | Medium | Small |
| 3 | **Inconsistent package structure** -- Fund transfer service puts repositories under `model.repository`, utility payment puts them under `repository`. DTOs are under `model.dto` in some services and `model.rest.request`/`model.rest.response` in others. | Medium | Small |
| 4 | **No Gradle wrapper or root settings.gradle** -- Each service is a standalone Gradle project with no shared dependency management. | Medium | Medium |

---

## Error Handling Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **Generic catch-all returns 400 for everything** -- All `GlobalExceptionHandler` classes catch `Exception.class` and return `400 Bad Request` with a raw string body `"Exception occur inside API " + e`. This leaks stack traces and internal details. | Critical | Small |
| 2 | **Inconsistent error response format** -- The catch-all handler returns a plain string while `SimpleBankingGlobalException` handler returns an `ErrorResponse` object. | High | Small |
| 3 | **No handling for specific HTTP errors** -- No handlers for 404 (`EntityNotFoundException` returns 400 instead), 422, 409, 500. All errors map to 400. | High | Small |
| 4 | **No Feign error propagation** -- Only user-service has a `CustomFeignErrorDecoder`; fund-transfer and utility-payment services lack proper Feign error decoding. | High | Small |

---

## Testing Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **Only core-banking-service has unit tests** -- `AccountServiceTest` (6 tests), `TransactionServiceTest` (9 tests), `UserServiceTest` (3 tests). All other services have only empty `@SpringBootTest` context-load tests. | Critical | Large |
| 2 | **No integration tests** -- No tests that verify actual HTTP endpoints, database interactions, or Feign client behavior. | High | Large |
| 3 | **No contract tests** -- No Spring Cloud Contract or Pact tests between services. | High | Large |
| 4 | **No controller/API layer tests** -- No `@WebMvcTest` or MockMvc tests for any controller. | High | Medium |

---

## Security Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **Zero input validation** -- No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max` annotations anywhere in the codebase. All request bodies are accepted without validation. | Critical | Small |
| 2 | **Hardcoded secrets** -- MySQL root password (`woVERANKliGharym`), DB user password (`oPItyPticIAt`), Keycloak admin password (`password`), Keycloak DB password (`password`) all in plain text in docker-compose.yml and privileges.sql. | Critical | Medium |
| 3 | **No authentication on downstream services** -- Only the API Gateway enforces OAuth2. Core banking, fund transfer, utility payment services are directly accessible on their ports without any authentication. | Critical | Medium |
| 4 | **Password in User DTO** -- The `User` DTO in user-service includes a `password` field that could be serialized in responses. | High | Small |
| 5 | **CSRF disabled** -- CSRF protection is disabled in the gateway's `SecurityConfiguration`. | Medium | Small |
| 6 | **No dependency vulnerability scanning** -- No OWASP dependency-check, Snyk, or similar tool configured. | Medium | Small |

---

## API Design Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **Raw `ResponseEntity` without type parameters** -- Most controller methods return untyped `ResponseEntity` (no generics), making the API contract unclear. | Medium | Small |
| 2 | **No API versioning strategy** -- While endpoints use `/api/v1/`, there's no mechanism for version evolution. | Low | Medium |
| 3 | **No pagination metadata in responses** -- Paginated endpoints return `List<T>` instead of `Page<T>`, losing total count, page number, etc. | Medium | Small |
| 4 | **No filtering or sorting** -- List endpoints accept `Pageable` but no explicit filtering parameters. | Low | Medium |
| 5 | **OpenAPI/Swagger misconfigured** -- Services include `springdoc-openapi-starter-webflux-ui` (WebFlux variant) but are Spring MVC applications. Should use `springdoc-openapi-starter-webmvc-ui`. | Medium | Small |
| 6 | **Inconsistent REST conventions** -- User update uses `PATCH /update/{id}` instead of `PATCH /{id}`. Registration uses `POST /register` instead of `POST /`. | Low | Small |

---

## Observability Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **No structured logging** -- Services use `@Slf4j` with unstructured string messages. No JSON logging format configured. | Medium | Small |
| 2 | **No custom health checks** -- Only default Spring Boot Actuator health endpoint. No checks for MySQL connectivity, Keycloak availability, or Feign client health. | Medium | Small |
| 3 | **No metrics endpoints configured** -- Actuator is included but no Prometheus metrics exporter despite Prometheus being listed in the tech stack. | Medium | Small |
| 4 | **Tracing configuration is externalized** -- Tracing sampling rate and Zipkin URL are in external config repo, not visible in this codebase. | Low | Small |
| 5 | **Sensitive data in logs** -- `request.toString()` is logged in controllers, which could include passwords (e.g., User registration). | High | Small |

---

## Resilience Gaps

| # | Gap | Severity | Effort |
|---|---|---|---|
| 1 | **No circuit breakers** -- No Resilience4j, Hystrix, or any circuit breaker pattern on Feign clients. If core-banking-service goes down, all dependent services will cascade fail. | Critical | Medium |
| 2 | **No retry policies** -- No `@Retryable` or Feign retry configuration. Transient failures cause immediate errors. | High | Small |
| 3 | **No timeout configuration** -- No Feign client timeouts, no connection/read timeouts configured. | High | Small |
| 4 | **No fallback behavior** -- No fallback methods for Feign clients. | Medium | Medium |
| 5 | **No idempotency** -- Fund transfer and payment endpoints have no idempotency keys. Duplicate requests will create duplicate transactions. | Critical | Medium |
| 6 | **No database transaction isolation** -- `TransactionService.internalFundTransfer()` does two separate `bankAccountRepository.save()` calls. Race conditions could cause inconsistent balances. | Critical | Medium |

---

## Additional Critical Bug

**Double-subtraction bug in balance calculation** -- In `TransactionService.java` lines 63-64 and 90-91: `setActualBalance(actualBalance.subtract(amount))` followed by `setAvailableBalance(actualBalance.subtract(amount))` -- since `actualBalance` was already reduced, `availableBalance` gets double-subtracted.

- **Severity:** Critical
- **Effort:** Small

---

## Summary

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 3 | 0 | 4 |
| Error Handling | 1 | 3 | 0 | 0 | 4 |
| Testing | 1 | 3 | 0 | 0 | 4 |
| Security | 3 | 1 | 2 | 0 | 6 |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 0 | 1 | 3 | 1 | 5 |
| Resilience | 3 | 2 | 1 | 0 | 6 |
| Additional Bugs | 1 | 0 | 0 | 0 | 1 |
| **Total** | **9** | **11** | **12** | **4** | **36** |
