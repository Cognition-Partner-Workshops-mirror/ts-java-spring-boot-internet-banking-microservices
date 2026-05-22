# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### 1.1 Project Structure Consistency

**Finding:** Services follow an inconsistent project structure. The fund-transfer and utility-payment services use an `AuditAware` base class with JPA auditing, while the core-banking service has no audit support. Package layouts differ across services — some place DTOs under `model.dto`, others under `model.rest.request`/`model.rest.response`.

| Gap | Severity | Effort |
|-----|----------|--------|
| Inconsistent package structure across services | Medium | Medium |

### 1.2 Shared Library / Common Module

**Finding:** There is no shared library (despite the blueprint referencing `banking-common`). Each service independently duplicates identical classes:
- `AuditAware` — copied in fund-transfer, utility-payment, and user services
- `BaseMapper` — copied in all 4 application services (identical abstract class)
- `ErrorResponse` — duplicated in 3 services with slight differences (builder vs constructor)
- `SimpleBankingGlobalException` — duplicated in 3 services
- `TransactionStatus` enum — duplicated in fund-transfer and utility-payment
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — duplicated in 3 services
- `CustomFeignClientConfiguration` — duplicated in fund-transfer and utility-payment

| Gap | Severity | Effort |
|-----|----------|--------|
| No shared library; significant code duplication across services | High | Medium |

### 1.3 Separation of Concerns

**Finding:** Generally well-structured with controller → service → repository layering. However:
- `AccountService` in core-banking manually instantiates mappers (`new BankAccountMapper()`) instead of using dependency injection, making testing harder
- `KeycloakProperties` uses a static singleton pattern for the Keycloak client, which is not thread-safe and bypasses Spring's lifecycle management
- DTOs (e.g., `User`) extend `AuditAware` (a JPA `@MappedSuperclass`), mixing persistence concerns into data transfer objects

| Gap | Severity | Effort |
|-----|----------|--------|
| Mappers manually instantiated instead of Spring beans | Low | Small |
| Static singleton for Keycloak client (not thread-safe) | Medium | Small |
| DTOs extend JPA entity base class (`AuditAware`) | Medium | Medium |

---

## 2. Error Handling

### 2.1 Centralized Error Handling

**Finding:** Each service that needs it has a `GlobalExceptionHandler` using `@ControllerAdvice`. This is a good pattern, but the implementation is duplicated (not shared) and has significant issues.

| Gap | Severity | Effort |
|-----|----------|--------|
| Duplicated GlobalExceptionHandler across 3 services | Medium | Small |

### 2.2 Consistent Error Response Format

**Finding:** The `ErrorResponse` class is inconsistent:
- Core banking uses `@Builder` pattern: `ErrorResponse.builder().code().message().build()`
- Fund transfer uses a constructor: `new ErrorResponse(code, message)`
- The generic catch-all handler returns a **plain string** (`"Exception occur inside API " + e`) instead of a structured `ErrorResponse`, leaking stack trace information

| Gap | Severity | Effort |
|-----|----------|--------|
| Inconsistent ErrorResponse construction patterns | Medium | Small |
| Generic exception handler returns plain string with stack trace | High | Small |

### 2.3 HTTP Status Codes

**Finding:** All errors return HTTP 400 (Bad Request) regardless of the actual error type:
- Entity not found → should be 404
- Insufficient funds → should be 422 (Unprocessable Entity)
- Unauthorized Feign calls → properly yields different codes in the decoder but are not surfaced correctly
- No 500 Internal Server Error handling — the catch-all returns 400

| Gap | Severity | Effort |
|-----|----------|--------|
| All exceptions return HTTP 400 instead of appropriate status codes | High | Small |

### 2.4 Feign Error Propagation

**Finding:** Only the user-service has a `CustomFeignErrorDecoder` that maps downstream errors. The fund-transfer and utility-payment services use the default Feign error decoder, meaning errors from core-banking are not properly propagated — they may surface as opaque `FeignException` with full HTTP response bodies.

| Gap | Severity | Effort |
|-----|----------|--------|
| Missing Feign error decoder in fund-transfer and utility-payment services | High | Small |

---

## 3. Testing

### 3.1 Unit Test Coverage

**Finding:** Test coverage is minimal:

| Service | Test Files | Content |
|---------|-----------|---------|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Good unit tests with Mockito — cover happy path and error cases |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load test only (empty `@SpringBootTest`) |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load test only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load test only |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load test only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | Context load test only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load test only |

Only 1 of 4 application services has meaningful unit tests. The other services have zero service-layer or controller tests.

| Gap | Severity | Effort |
|-----|----------|--------|
| 3 of 4 application services have no meaningful unit tests | Critical | Large |

### 3.2 Integration Tests

**Finding:** No integration tests exist. No `@SpringBootTest` tests with loaded application contexts that test actual HTTP endpoints. No test containers usage for MySQL or Keycloak.

| Gap | Severity | Effort |
|-----|----------|--------|
| No integration tests for any service | High | Large |

### 3.3 Contract Tests

**Finding:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between services. Given the tight Feign coupling between 3 services and core-banking, API contract breakage is a significant risk.

| Gap | Severity | Effort |
|-----|----------|--------|
| No contract tests between services | High | Large |

### 3.4 Controller Tests

**Finding:** No `@WebMvcTest` or `MockMvc`-based controller tests exist in any service. Controller input validation, serialization, and HTTP status codes are untested.

| Gap | Severity | Effort |
|-----|----------|--------|
| No controller-layer tests | High | Medium |

---

## 4. Security

### 4.1 Input Validation

**Finding:** No input validation exists on any request body:
- `FundTransferRequest` — no `@NotNull`, `@NotBlank`, `@Positive`, `@Valid` annotations
- `UtilityPaymentRequest` — no validation annotations
- `User` registration — no email format validation, no password strength rules
- No `@Valid` annotation on any `@RequestBody` parameter in controllers
- No `spring-boot-starter-validation` dependency in any build file

| Gap | Severity | Effort |
|-----|----------|--------|
| No input validation on any API endpoint | Critical | Medium |

### 4.2 Authentication/Authorization Patterns

**Finding:** 
- API Gateway correctly enforces OAuth2 JWT authentication via Keycloak
- User registration endpoint is properly excluded from auth
- Actuator endpoints are permitted without auth (intentional for health checks)
- **However:** Downstream services have NO security configuration — they are fully open if accessed directly (bypassing the gateway). There is no service-to-service authentication.
- No role-based access control (RBAC) — any authenticated user can perform admin operations (e.g., user approval)

| Gap | Severity | Effort |
|-----|----------|--------|
| Downstream services unprotected (no service-to-service auth) | Critical | Medium |
| No role-based access control (RBAC) | High | Medium |

### 4.3 Secrets Management

**Finding:** Multiple hardcoded secrets found in source:
- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), DB credentials
- `docker-compose/mysql/Dockerfile`: MySQL root password in `ENV`
- `privileges.sql`: Database user password (`oPItyPticIAt`)
- `internet-banking-user-service/src/test/resources/application.yml`: Keycloak client-secret in plain text
- No `.env` file usage, no vault integration, no secrets manager

| Gap | Severity | Effort |
|-----|----------|--------|
| Hardcoded secrets in Docker Compose, SQL, and test configs | Critical | Medium |

### 4.4 Dependency Vulnerabilities

**Finding:**
- `springdoc-openapi-starter-webflux-ui:2.1.0` is outdated (current: 2.6+) — may have known CVEs
- `keycloak-admin-client:24.0.4` — should be checked against latest security patches
- No dependency vulnerability scanning tool configured (no OWASP Dependency Check, Snyk, or Dependabot)

| Gap | Severity | Effort |
|-----|----------|--------|
| No dependency vulnerability scanning configured | High | Small |
| Outdated springdoc-openapi dependency | Medium | Small |

### 4.5 Sensitive Data in Logs

**Finding:** Controllers log full request objects via `toString()`:
- `FundTransferController`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` — exposes account numbers and amounts
- `UserController`: `log.info("Creating user with {}", request.toString())` — exposes passwords (User DTO has a `password` field)
- The User DTO `password` field has no `@JsonIgnore` for responses and no log masking

| Gap | Severity | Effort |
|-----|----------|--------|
| Password and sensitive data logged in plain text | Critical | Small |

---

## 5. API Design

### 5.1 RESTful Conventions

**Finding:** Generally follows REST conventions with appropriate HTTP methods. Issues:
- `PATCH /api/v1/bank-users/update/{id}` — the `update` path segment is redundant (PATCH already implies update)
- `POST /api/v1/transfer` returns 200 instead of 201 (Created) for resource creation
- `POST /api/v1/utility-payment` returns 200 instead of 201
- `POST /api/v1/bank-users/register` returns 200 instead of 201
- No `Location` header returned for created resources

| Gap | Severity | Effort |
|-----|----------|--------|
| POST endpoints return 200 instead of 201 Created | Low | Small |
| Redundant path segments in URL design | Low | Small |

### 5.2 Pagination

**Finding:** Pagination is supported via Spring Data's `Pageable` parameter, but:
- Endpoints return raw `List<T>` instead of `Page<T>` wrapper — total count, total pages, and current page metadata are lost
- No consistent pagination response envelope

| Gap | Severity | Effort |
|-----|----------|--------|
| Pagination metadata stripped (no total count, page info in response) | Medium | Small |

### 5.3 Filtering and Sorting

**Finding:** No filtering capabilities exist beyond pagination. No query parameters for filtering by status, date range, account number, etc. Sorting is only available through Pageable's `sort` parameter.

| Gap | Severity | Effort |
|-----|----------|--------|
| No filtering capabilities on list endpoints | Medium | Medium |

### 5.4 API Versioning

**Finding:** URL path versioning (`/api/v1/`) is consistently used across all services. This is a good practice. No versioning strategy issues detected.

**Status:** ✓ Adequate

### 5.5 OpenAPI Documentation

**Finding:** 
- `springdoc-openapi-starter-webflux-ui` is included as a dependency in 4 services
- Swagger `@Tag` and `@Operation` annotations are present on all controllers
- **However:** `ResponseEntity` return types are used without generics (raw types) in core-banking, fund-transfer, and utility-payment controllers, which means OpenAPI cannot infer response schemas
- No centralized/aggregated API documentation across services

| Gap | Severity | Effort |
|-----|----------|--------|
| Raw `ResponseEntity` types prevent OpenAPI schema generation | Medium | Small |
| No aggregated API documentation across services | Low | Medium |

---

## 6. Observability

### 6.1 Logging Consistency

**Finding:**
- Lombok `@Slf4j` is used consistently across services
- Log levels are not configurable per-service (rely on centralized config)
- No structured logging (JSON format) — all logs use plain text
- Inconsistent log message format — some use `{}` placeholders, one uses string concatenation (`"Sending fund transfer request {}" + request.toString()`)

| Gap | Severity | Effort |
|-----|----------|--------|
| No structured (JSON) logging for log aggregation | Medium | Small |
| Inconsistent log formatting (mix of `{}` and string concatenation) | Low | Small |

### 6.2 Health Checks

**Finding:** `spring-boot-starter-actuator` is included in all services, providing `/actuator/health` by default. The API Gateway permits actuator endpoints without auth. However:
- No custom health indicators for external dependencies (MySQL connectivity, Keycloak reachability, Eureka registration status)
- No readiness/liveness probe configuration for Kubernetes

| Gap | Severity | Effort |
|-----|----------|--------|
| No custom health indicators for external dependencies | Medium | Small |
| No Kubernetes readiness/liveness probe config | Low | Small |

### 6.3 Metrics Endpoints

**Finding:** Spring Boot Actuator provides basic metrics via `/actuator/metrics`. Micrometer is present (for tracing). However:
- No Prometheus metrics endpoint configured (`/actuator/prometheus`)
- No custom business metrics (e.g., transfer count, payment volume, error rates)
- README mentions Prometheus in the tech stack, but `micrometer-registry-prometheus` is not in any build.gradle

| Gap | Severity | Effort |
|-----|----------|--------|
| No Prometheus metrics endpoint despite being listed in tech stack | Medium | Small |
| No custom business metrics | Low | Medium |

### 6.4 Distributed Tracing

**Finding:** Micrometer Tracing with Brave bridge and Zipkin reporter are correctly configured in all services. Feign client tracing is also instrumented via `feign-micrometer`. This provides good distributed tracing coverage.

**Status:** ✓ Adequate (though Zipkin URL configuration depends on external config server)

---

## 7. Resilience

### 7.1 Circuit Breakers

**Finding:** No circuit breaker pattern is implemented. All inter-service calls (via Feign) have no fallback mechanism. If core-banking-service goes down, both fund-transfer and utility-payment services will fail with unhandled exceptions. Spring Cloud Circuit Breaker / Resilience4j is not included in any dependency.

| Gap | Severity | Effort |
|-----|----------|--------|
| No circuit breakers on any inter-service call | Critical | Medium |

### 7.2 Retry Policies

**Finding:** No retry policies are configured for Feign clients. Transient network failures will immediately fail the request. No Spring Retry or Resilience4j Retry configuration.

| Gap | Severity | Effort |
|-----|----------|--------|
| No retry policies for inter-service communication | High | Small |

### 7.3 Timeouts

**Finding:** No explicit timeouts configured for:
- Feign client connections and reads (uses default — which may be very long or infinite)
- Database connection pools
- Keycloak admin client connections
- The only timeout present is `wait-for-it.sh --timeout=50` in Docker entrypoints

| Gap | Severity | Effort |
|-----|----------|--------|
| No explicit timeouts on Feign clients, DB connections, or Keycloak calls | High | Small |

### 7.4 Fallback Behavior

**Finding:** No fallback methods or degraded-mode behavior. If any downstream dependency fails, the entire request chain fails. The fund-transfer service saves a `PENDING` record but never updates it to `FAILED` if the core-banking call fails.

| Gap | Severity | Effort |
|-----|----------|--------|
| No fallback behavior; PENDING records never marked FAILED on errors | High | Medium |

### 7.5 Idempotency

**Finding:** No idempotency mechanisms exist:
- Fund transfer `POST` has no idempotency key — retrying creates duplicate transfers
- Utility payment `POST` has no idempotency key
- No deduplication logic based on transaction reference
- Core banking transfer is not idempotent — re-execution double-debits the source account

| Gap | Severity | Effort |
|-----|----------|--------|
| No idempotency protection on financial transactions | Critical | Medium |

### 7.6 Transaction Safety

**Finding:** The `TransactionService.internalFundTransfer()` performs debit and credit in separate repository calls. While `@Transactional` is used, there are bugs:
- `availableBalance` is calculated incorrectly: set to `actualBalance - amount` after `actualBalance` has already been decremented (double subtraction bug)
- Same bug exists in `utilPayment()`: `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(...))` after `actualBalance` was already decremented

| Gap | Severity | Effort |
|-----|----------|--------|
| Balance calculation bug: double subtraction in availableBalance | Critical | Small |

---

## Summary Table

| # | Category | Gap Description | Severity | Effort |
|---|----------|----------------|----------|--------|
| 1 | Code Organization | Inconsistent package structure across services | Medium | Medium |
| 2 | Code Organization | No shared library; significant code duplication | High | Medium |
| 3 | Code Organization | Mappers manually instantiated instead of Spring beans | Low | Small |
| 4 | Code Organization | Static singleton for Keycloak client (not thread-safe) | Medium | Small |
| 5 | Code Organization | DTOs extend JPA entity base class | Medium | Medium |
| 6 | Error Handling | Duplicated GlobalExceptionHandler across services | Medium | Small |
| 7 | Error Handling | Inconsistent ErrorResponse construction patterns | Medium | Small |
| 8 | Error Handling | Generic exception handler returns plain string with stack trace | High | Small |
| 9 | Error Handling | All exceptions return HTTP 400 | High | Small |
| 10 | Error Handling | Missing Feign error decoder in 2 services | High | Small |
| 11 | Testing | 3 of 4 application services have no meaningful unit tests | Critical | Large |
| 12 | Testing | No integration tests | High | Large |
| 13 | Testing | No contract tests between services | High | Large |
| 14 | Testing | No controller-layer tests | High | Medium |
| 15 | Security | No input validation on any API endpoint | Critical | Medium |
| 16 | Security | Downstream services unprotected (no service-to-service auth) | Critical | Medium |
| 17 | Security | No role-based access control (RBAC) | High | Medium |
| 18 | Security | Hardcoded secrets in Docker Compose, SQL, and test configs | Critical | Medium |
| 19 | Security | No dependency vulnerability scanning | High | Small |
| 20 | Security | Outdated springdoc-openapi dependency | Medium | Small |
| 21 | Security | Password and sensitive data logged in plain text | Critical | Small |
| 22 | API Design | POST endpoints return 200 instead of 201 | Low | Small |
| 23 | API Design | Redundant path segments in URL design | Low | Small |
| 24 | API Design | Pagination metadata stripped from responses | Medium | Small |
| 25 | API Design | No filtering capabilities on list endpoints | Medium | Medium |
| 26 | API Design | Raw ResponseEntity types prevent OpenAPI schema generation | Medium | Small |
| 27 | API Design | No aggregated API documentation | Low | Medium |
| 28 | Observability | No structured (JSON) logging | Medium | Small |
| 29 | Observability | Inconsistent log formatting | Low | Small |
| 30 | Observability | No custom health indicators | Medium | Small |
| 31 | Observability | No Kubernetes readiness/liveness probe config | Low | Small |
| 32 | Observability | No Prometheus metrics endpoint | Medium | Small |
| 33 | Observability | No custom business metrics | Low | Medium |
| 34 | Resilience | No circuit breakers on inter-service calls | Critical | Medium |
| 35 | Resilience | No retry policies | High | Small |
| 36 | Resilience | No explicit timeouts on clients | High | Small |
| 37 | Resilience | No fallback behavior; PENDING records never fail | High | Medium |
| 38 | Resilience | No idempotency protection on financial transactions | Critical | Medium |
| 39 | Resilience | Balance calculation bug: double subtraction | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 9 |
| High | 14 |
| Medium | 12 |
| Low | 4 |
| **Total** | **39** |
