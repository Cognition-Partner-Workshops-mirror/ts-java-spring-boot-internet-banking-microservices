# Engineering Standards Gap Analysis

This document evaluates the Internet Banking Microservices codebase against industry-standard engineering best practices. Each gap is rated by **severity** (Critical / High / Medium / Low) and estimated **effort** to remediate (Small / Medium / Large).

## Table of Contents

- [Code Organization](#code-organization)
- [Error Handling](#error-handling)
- [Testing](#testing)
- [Security](#security)
- [API Design](#api-design)
- [Observability](#observability)
- [Resilience](#resilience)
- [Additional Critical Bugs](#additional-critical-bugs)
- [Summary Matrix](#summary-matrix)

---

## Code Organization

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| CO-1 | No shared library; code duplicated across services | `BaseMapper`, `AuditAware`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are copy-pasted across 3-4 services with minor variations | High | Large |
| CO-2 | No root Gradle multi-project build | Each service is a standalone Gradle project. No `settings.gradle` at root, no shared dependency version management | Medium | Medium |
| CO-3 | Inconsistent package structure for Feign clients | User service: `service.rest.BankingCoreRestClient`. Fund transfer: `service.rest.client.BankingCoreFeignClient`. Utility payment: `service.rest.BankingCoreRestClient` | Low | Small |
| CO-4 | Inconsistent Feign configuration | User service uses `CustomFeignErrorDecoder` in `configuration.feign` package. Fund transfer and utility payment use `CustomFeignClientConfiguration` (logger only) in `configuration` package. User service Feign client has no `configuration` attribute. | Medium | Small |
| CO-5 | Mappers instantiated with `new` instead of Spring beans | In `AccountService`, `UserService`, `FundTransferService`, `UtilityPaymentService`, mappers are created as `private XMapper mapper = new XMapper()` instead of being injected, making them untestable | Medium | Small |

---

## Error Handling

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| EH-1 | All exceptions return HTTP 400 | `GlobalExceptionHandler` in all services maps both `SimpleBankingGlobalException` and generic `Exception` to `ResponseEntity.badRequest()`. `EntityNotFoundException` (a 404 scenario) returns 400. | Critical | Small |
| EH-2 | Generic exception handler leaks stack traces | The catch-all `handleException` returns `"Exception occur inside API " + e` which includes the full exception `toString()` with stack trace in the response body | Critical | Small |
| EH-3 | Inconsistent error response construction | Fund transfer service uses `new ErrorResponse(code, message)` constructor. Core banking and user service use `ErrorResponse.builder().code().message().build()`. | Low | Small |
| EH-4 | No error handling for Feign failures in fund-transfer and utility-payment services | Only user-service has `CustomFeignErrorDecoder`. Fund transfer and utility payment services have no Feign error decoder — Feign failures will produce generic exceptions | High | Medium |
| EH-5 | Fund transfer has no rollback on core banking failure | `FundTransferService.fundTransfer()` saves entity with PENDING status, calls core banking, but if core banking fails after the call, the status is never updated to FAILED | High | Medium |

---

## Testing

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| T-1 | Only core-banking-service has unit tests | 3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`. Other 6 services have only empty `@SpringBootTest` context-load tests | Critical | Large |
| T-2 | No integration tests | No tests that verify actual HTTP endpoints, database interactions, or Feign client behavior | High | Large |
| T-3 | No contract tests between services | No Spring Cloud Contract, Pact, or similar consumer-driven contract testing | High | Large |
| T-4 | No controller-layer tests | No `@WebMvcTest` or MockMvc tests for any controller in any service | High | Medium |
| T-5 | Test configuration disables Flyway and uses H2 with `ddl-auto: none` | Tests won't create tables, meaning any test requiring DB interaction will fail unless it's purely mocked | Medium | Small |

---

## Security

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| S-1 | Hardcoded credentials in source code | MySQL root password (`woVERANKliGharym`) in docker-compose.yml and Dockerfile. Keycloak admin password (`password`). DB user password (`oPItyPticIAt`) in privileges.sql. Keycloak client-secret in test application.yml. | Critical | Medium |
| S-2 | No input validation on any request DTO | Zero `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size` annotations anywhere in the codebase. `FundTransferRequest` accepts null/negative amounts. | Critical | Small |
| S-3 | CSRF disabled on API Gateway | `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)` — acceptable for pure API but should be documented as an intentional decision | Low | Small |
| S-4 | Downstream services have no authentication | Only the API Gateway validates JWTs. Business services accept any request on their ports (8083-8092) without authentication. In Docker network this is mitigated but not in other deployments. | High | Medium |
| S-5 | Keycloak singleton is not thread-safe | `KeycloakProperties.getInstance()` uses a non-synchronized null check on a static field — classic double-checked locking bug without `volatile` keyword | High | Small |
| S-6 | Password included in User DTO response | `User` DTO in user-service contains `password` field with no `@JsonIgnore` or `@JsonProperty(access = WRITE_ONLY)` annotation, potentially leaking passwords in API responses | Critical | Small |

---

## API Design

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| A-1 | Raw `ResponseEntity` without type parameters | All controllers use `ResponseEntity` instead of `ResponseEntity<SpecificType>`, losing compile-time type safety and OpenAPI schema generation | Medium | Small |
| A-2 | No pagination metadata in responses | `readUsers`, `readFundTransfers`, `readPayments` return `List<T>` discarding page metadata (total elements, total pages, current page) from the `Page` object | High | Small |
| A-3 | Wrong OpenAPI dependency | All servlet-based services include `springdoc-openapi-starter-webflux-ui:2.1.0` instead of `springdoc-openapi-starter-webmvc-ui`. This means Swagger UI likely doesn't work. | Medium | Small |
| A-4 | No API versioning strategy | Version `v1` is hardcoded in URL paths but there's no mechanism for version negotiation or header-based versioning | Low | Small |
| A-5 | Inconsistent REST conventions | User registration is `POST /register` (verb in URL). User update uses `PATCH /update/{id}` (verb in URL). Should be `POST /` and `PATCH /{id}` | Medium | Small |
| A-6 | No HATEOAS or resource linking | Responses are plain DTOs with no links to related resources | Low | Medium |

---

## Observability

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| O-1 | No structured logging | Services use `log.info()` with string concatenation/formatting but no structured JSON logging configuration (no `logback-spring.xml`) | Medium | Small |
| O-2 | No custom health indicators | Actuator is included but no custom health checks for downstream dependencies (MySQL connectivity, Keycloak availability, core-banking reachability) | Medium | Medium |
| O-3 | No custom metrics | No Micrometer `@Timed`, counters, or gauges for business metrics (transfer count, payment amounts, error rates) | Medium | Medium |
| O-4 | Actuator endpoints publicly accessible | Gateway security config permits all `/actuator/**` paths without authentication, potentially exposing sensitive operational data | High | Small |
| O-5 | Log statements use string concatenation | `FundTransferService` line 32: `log.info("Sending fund transfer request {}" + request.toString())` — uses `+` instead of `{}` placeholder, defeating lazy evaluation | Low | Small |

---

## Resilience

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| R-1 | No circuit breakers | No Resilience4j or Hystrix dependency in any `build.gradle`. Feign clients will retry indefinitely or fail hard on downstream outages | Critical | Medium |
| R-2 | No retry policies | No `@Retryable` or Feign retry configuration. Transient failures cause immediate failure | High | Small |
| R-3 | No timeout configuration | No Feign timeouts, no connection/read timeouts configured. Requests can hang indefinitely | Critical | Small |
| R-4 | No fallback behavior | No Feign fallback classes or factory. Service degradation is not handled gracefully | High | Medium |
| R-5 | No rate limiting | API Gateway has no rate limiting configuration. Services are vulnerable to traffic spikes | Medium | Medium |
| R-6 | No bulkhead pattern | No thread pool isolation between different types of requests | Low | Medium |

---

## Additional Critical Bugs

| # | Gap | Evidence | Severity | Effort |
|---|-----|----------|----------|--------|
| B-1 | Balance double-subtraction bug | `TransactionService.internalFundTransfer()` lines 90-91: sets `actualBalance = actualBalance - amount`, then `availableBalance = actualBalance - amount` (actualBalance already reduced). Same bug in `utilPayment()` lines 63-64. | Critical | Small |
| B-2 | TransactionEntity uses @OneToOne with CascadeType.ALL for account | Should be `@ManyToOne`. Multiple transactions reference the same account. `CascadeType.ALL` means deleting a transaction could cascade-delete the account. | Critical | Small |
| B-3 | No CI/CD pipeline | `.github/` directory contains only `FUNDING.yml`. No automated build, test, or deployment pipeline | High | Medium |

---

## Summary Matrix

### By Severity

| Severity | Count | IDs |
|----------|-------|-----|
| **Critical** | 10 | EH-1, EH-2, S-1, S-2, S-6, T-1, R-1, R-3, B-1, B-2 |
| **High** | 12 | CO-1, EH-4, EH-5, S-4, S-5, T-2, T-3, T-4, A-2, O-4, R-2, R-4, B-3 |
| **Medium** | 11 | CO-2, CO-4, CO-5, T-5, A-1, A-3, A-5, O-1, O-2, O-3, R-5 |
| **Low** | 7 | CO-3, EH-3, S-3, A-4, A-6, O-5, R-6 |

### By Effort

| Effort | Count | IDs |
|--------|-------|-----|
| **Small** | 21 | EH-1, EH-2, EH-3, S-2, S-3, S-5, S-6, A-1, A-2, A-3, A-4, A-5, O-1, O-5, R-2, R-3, B-1, B-2, CO-3, CO-4, CO-5, T-5 |
| **Medium** | 13 | CO-2, EH-4, EH-5, S-1, S-4, T-4, O-2, O-3, O-4, R-1, R-4, R-5, R-6, A-6, B-3 |
| **Large** | 4 | CO-1, T-1, T-2, T-3 |

### Priority Quadrant

| | Small Effort | Medium Effort | Large Effort |
|---|---|---|---|
| **Critical** | B-1, B-2, EH-1, EH-2, S-2, S-6, R-3 | S-1, R-1 | T-1 |
| **High** | A-2, S-5, R-2, O-4 | EH-4, EH-5, S-4, R-4, B-3 | CO-1, T-2, T-3 |
| **Medium** | CO-4, CO-5, A-1, A-3, A-5, O-1, T-5 | CO-2, O-2, O-3, R-5 | — |
| **Low** | CO-3, EH-3, S-3, A-4, O-5 | A-6, R-6 | — |
