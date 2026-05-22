# Gap Analysis — Internet Banking Microservices

This document identifies gaps in the codebase compared against engineering best practices. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## Code Organization

| Gap | Severity | Effort |
|-----|----------|--------|
| No shared library — BaseMapper, AuditAware, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler, AppAuthUserFilter, ApiRequestContextHolder are copy-pasted across 4 services | High | Large |
| Independent Gradle projects with no parent build — no enforced dependency versions, no common plugin config | Medium | Medium |
| Inconsistent package structure — core-banking uses `repository/` package, fund-transfer uses `model/repository/`, utility-payment uses `repository/` | Low | Small |
| Inconsistent Feign client patterns — user-service uses RestTemplate-based BankingCoreRestClient, fund-transfer uses Feign BankingCoreFeignClient, utility-payment uses RestTemplate-based BankingCoreRestClient | Medium | Medium |

---

## Error Handling

| Gap | Severity | Effort |
|-----|----------|--------|
| All exceptions return HTTP 400 Bad Request — including EntityNotFoundException (should be 404) and generic Exception (should be 500) | Critical | Small |
| Generic Exception handler returns raw string "Exception occur inside API " + e — leaks stack traces and internal details to clients | Critical | Small |
| ErrorResponse inconsistencies — core-banking uses @Builder pattern, fund-transfer uses constructor; both are copy-pasted | Medium | Small |
| No handling of MethodArgumentNotValidException, HttpMessageNotReadableException, etc. | Medium | Small |
| Fund transfer service has no error/rollback handling — if Feign call fails after local entity saved as PENDING, it stays PENDING forever | High | Medium |
| Utility payment service same issue — PROCESSING status never updated on failure | High | Medium |

---

## Testing

| Gap | Severity | Effort |
|-----|----------|--------|
| Only core-banking-service has real unit tests (3 test classes: AccountServiceTest, TransactionServiceTest, UserServiceTest) | High | Large |
| No unit tests for user-service, fund-transfer-service, or utility-payment-service business logic | High | Large |
| No controller/integration tests with MockMvc or WebTestClient anywhere | High | Medium |
| No contract tests (Spring Cloud Contract, Pact) between services | Medium | Large |
| No test for Feign client error handling or CustomFeignErrorDecoder | Medium | Medium |
| Domain service test configs exist (H2 + disabled Eureka/Flyway) but only smoke-test ApplicationContext loading | Low | Small |

---

## Security

| Gap | Severity | Effort |
|-----|----------|--------|
| Hardcoded database passwords in docker-compose.yml (MYSQL_ROOT_PASSWORD: woVERANKliGharym) and privileges.sql | Critical | Small |
| Hardcoded Keycloak admin password (admin/password) and DB password in docker-compose | Critical | Small |
| Keycloak client-secret hardcoded in test application.yml | High | Small |
| No @Valid annotation on any @RequestBody parameter — no input validation at all | Critical | Small |
| Downstream services have no authentication — anyone with network access can call core-banking-service directly bypassing the gateway | Critical | Medium |
| CSRF disabled in gateway SecurityConfiguration without documentation of rationale | Medium | Small |
| User registration endpoint is public (permitAll) which is correct, but no rate limiting exists | Medium | Medium |

---

## API Design

| Gap | Severity | Effort |
|-----|----------|--------|
| Raw ResponseEntity without type parameters on most controller methods in core-banking, fund-transfer, and utility-payment services — no compile-time type safety and poor OpenAPI docs | Medium | Small |
| No API versioning strategy beyond /api/v1/ in URL path — no headers, no documentation about versioning approach | Low | Small |
| No pagination defaults or max page size limits — clients can request unlimited page sizes | Medium | Small |
| Using springdoc-openapi-starter-webflux-ui in non-WebFlux services (core-banking, fund-transfer, user, utility-payment are all Spring MVC) — should use springdoc-openapi-starter-webmvc-ui | Medium | Small |
| No OpenAPI aggregation at gateway level | Low | Medium |
| Inconsistent DTO naming — core-banking uses `model.dto.request/response` packages, utility-payment uses `model.rest.request/response` | Low | Small |

---

## Observability

| Gap | Severity | Effort |
|-----|----------|--------|
| No structured logging — using default Spring Boot logging with ad-hoc log.info() calls | Medium | Medium |
| No custom health check indicators (e.g., checking DB connectivity, Keycloak reachability) | Medium | Small |
| No metrics endpoints beyond default Actuator — no custom business metrics (transfer count, payment volume, etc.) | Medium | Medium |
| Actuator endpoints publicly accessible (permitAll in gateway) without considering info exposure | High | Small |
| Tracing sampling rate not explicitly configured — defaults may not capture all traces | Low | Small |
| No log correlation IDs in log format — traceId/spanId not included in default log pattern | Medium | Small |

---

## Resilience

| Gap | Severity | Effort |
|-----|----------|--------|
| No circuit breakers on any Feign client or REST client calls | High | Medium |
| No retry policies configured for inter-service communication | High | Small |
| No timeout configuration on Feign clients or RestTemplate | High | Small |
| No fallback behavior defined — if core-banking-service is down, all dependent services fail with unhandled exceptions | High | Medium |
| No bulkhead pattern implementation | Medium | Medium |
| Fund transfer is not idempotent — no idempotency key to prevent duplicate transfers | High | Medium |
| Balance calculation bug in TransactionService.utilPayment — availableBalance is set to actualBalance.subtract(amount) AFTER actualBalance was already subtracted, causing double deduction (line 63-64 of TransactionService.java) | Critical | Small |

---

## Summary

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 2 | 1 | 4 |
| Error Handling | 2 | 2 | 2 | 0 | 6 |
| Testing | 0 | 3 | 2 | 1 | 6 |
| Security | 3 | 1 | 2 | 0 | 6 |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 0 | 1 | 4 | 1 | 6 |
| Resilience | 1 | 4 | 1 | 0 | 6 |
| **Totals** | **6** | **12** | **16** | **6** | **40** |
