# Engineering Standards Gap Analysis

## Summary

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale:**
- **Critical** — Security vulnerability or data-loss risk; must fix before production
- **High** — Significant architectural or reliability concern; fix soon
- **Medium** — Maintainability or operational concern; plan to fix
- **Low** — Polish or nice-to-have improvement

**Effort Scale:**
- **Small** — < 1 day per service / < 4 hours total
- **Medium** — 1-3 days
- **Large** — 1+ week

---

## 1. Code Organization

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 1.1 | No shared library / BOM for common code | Medium | Medium | Exception classes (`GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`), audit base classes (`AuditAware`), filter code (`AppAuthUserFilter`, `ApiRequestContext`), and `BaseMapper` are **copy-pasted** across 4 services with slight variations. No shared Gradle module or Maven BOM exists. |
| 1.2 | No multi-project Gradle build | Low | Small | Each service is an independent Gradle project with duplicated plugin/version declarations. A root `settings.gradle` with a shared BOM module would centralize version management. |
| 1.3 | Inconsistent package structure across services | Medium | Medium | Core banking uses `repository/` package; user service uses `model/repository/`; utility payment uses `repository/` at root. DTO package naming varies: `model.dto`, `model.rest.request`, `model.rest.response`. |
| 1.4 | Mappers instantiated inline rather than as Spring beans | Low | Small | `new BankAccountMapper()`, `new FundTransferMapper()` etc. are created as field initializers in service classes instead of being injected, making them harder to test and inconsistent with DI patterns. |
| 1.5 | Feign client package/naming inconsistency | Low | Small | Fund transfer uses `service.rest.client.BankingCoreFeignClient`; utility payment uses `service.rest.BankingCoreRestClient`; user service uses `service.rest.BankingCoreRestClient`. Different interface names for the same pattern. |

---

## 2. Error Handling

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 2.1 | Generic catch-all returns raw exception in response body | Critical | Small | `GlobalExceptionHandler.handleException()` returns `"Exception occur inside API " + e` — leaks stack traces, class names, and potentially sensitive info to callers. Present in ALL services. |
| 2.2 | All errors return HTTP 400 Bad Request | High | Small | Both custom and generic exceptions map to `400 BAD_REQUEST`. Missing proper HTTP status differentiation: 404 for not found, 500 for internal errors, 409 for conflicts, 422 for validation. |
| 2.3 | Inconsistent error response structure | Medium | Small | Core banking and user service use `ErrorResponse.builder()` (Lombok builder); fund transfer uses `new ErrorResponse(code, message)` constructor. Utility payment service's exception handler is different again. |
| 2.4 | No Feign error decoder in user service | Medium | Small | User service's `BankingCoreRestClient` has no custom `configuration` attribute — uses default Feign error handling. Fund transfer and utility payment have `CustomFeignClientConfiguration` but user service does not properly propagate upstream errors. |
| 2.5 | No validation error handling for request bodies | High | Small | No `@Valid` annotations on request DTOs, no `MethodArgumentNotValidException` handler. Invalid/null request fields reach business logic unchecked. |
| 2.6 | Swallowed exceptions in Keycloak integration | Medium | Small | `KeycloakUserService.readUser()` catches all exceptions with `catch (Exception e)` and only logs `e.toString()` — original stack trace lost, making debugging difficult. |

---

## 3. Testing

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 3.1 | Extremely low test coverage | High | Large | Only `core-banking-service` has meaningful unit tests (3 test files: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have **only** the default Spring Boot application context test (empty `@SpringBootTest`). |
| 3.2 | No integration tests | High | Large | Zero integration tests. No `@SpringBootTest` with real/embedded database, no Testcontainers, no Feign client contract tests. |
| 3.3 | No contract tests between services | High | Large | Services communicate via OpenFeign but there are no Spring Cloud Contract or Pact tests to verify API compatibility between producer and consumer. |
| 3.4 | No controller/API layer tests | Medium | Medium | No `@WebMvcTest` or MockMvc-based tests for any controller. Only service-layer unit tests exist. |
| 3.5 | Test context loads full application (default tests) | Low | Small | Default `*ApplicationTests` classes attempt to load full Spring context, which requires running infrastructure (MySQL, Eureka, Config Server), making them fail in isolation. |
| 3.6 | No test configuration for Feign clients | Medium | Medium | No WireMock or mock server setup for testing services that depend on OpenFeign clients. |

---

## 4. Security

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 4.1 | No input validation on any request DTO | Critical | Small | `FundTransferRequest`, `UtilityPaymentRequest`, `User` — none have `@NotNull`, `@Min`, `@Size`, `@Email` annotations. Arbitrary values (negative amounts, null accounts) reach business logic. |
| 4.2 | Hardcoded credentials in Docker Compose | Critical | Small | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`), DB user password (`oPItyPticIAt`) are all in plain text in version control. |
| 4.3 | No authorization at service level | High | Medium | Downstream services (user, fund-transfer, utility-payment) have NO security configuration. They trust the `X-Auth-Id` header injected by the gateway. Any direct network access bypasses auth entirely. |
| 4.4 | Keycloak singleton not thread-safe | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized static field with a null-check pattern — classic double-check locking bug without `volatile`. |
| 4.5 | CSRF disabled without documentation | Low | Small | CSRF is disabled in the gateway `SecurityConfiguration`. While appropriate for stateless APIs, this should be explicitly documented as an architectural decision. |
| 4.6 | No rate limiting | Medium | Medium | No rate limiting at gateway or service level. Fund transfer and payment endpoints are vulnerable to abuse. |
| 4.7 | Password stored in plain text DTO transmission | High | Small | `User.password` field is sent in the registration request body and logged via `request.toString()`. Password is visible in logs. |
| 4.8 | No dependency vulnerability scanning | Medium | Medium | No OWASP Dependency Check, Snyk, or similar tool configured in the build pipeline. |

---

## 5. API Design

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 5.1 | Raw `ResponseEntity` without type parameters | Medium | Small | Controllers return `ResponseEntity` (raw type) instead of `ResponseEntity<BankAccount>`, losing compile-time type safety and OpenAPI schema generation accuracy. Only user-service partially uses typed responses. |
| 5.2 | No API versioning strategy beyond URL prefix | Low | Small | URL path versioning (`/api/v1/`) is used but there's no documented strategy for introducing v2 or handling breaking changes. |
| 5.3 | Inconsistent endpoint naming | Medium | Small | Fund transfer uses `/api/v1/transfer` (no noun pluralization); utility payment uses `/api/v1/utility-payment`; core banking uses `/api/v1/transaction/fund-transfer`. Naming conventions vary across services. |
| 5.4 | No pagination metadata in responses | Medium | Small | `readFundTransfers()` and `readPayments()` return `List<>` instead of a page wrapper with `totalElements`, `totalPages`, `currentPage`. Pagination info is lost. |
| 5.5 | No filtering or search capabilities | Low | Medium | List endpoints only support Spring's implicit `Pageable` (page/size/sort). No filtering by date range, status, account, or amount. |
| 5.6 | Swagger/OpenAPI dependency mismatch | Medium | Small | Services use `springdoc-openapi-starter-webflux-ui:2.1.0` but are **WebMVC** applications (not WebFlux). This is the wrong starter — should be `springdoc-openapi-starter-webmvc-ui`. May cause classpath issues. |
| 5.7 | No standardized response wrapper | Medium | Small | Successful responses return raw entities/DTOs. No consistent envelope like `{data: ..., meta: ..., errors: [...]}`. |
| 5.8 | PATCH used for status update but no proper partial update support | Low | Small | `PATCH /update/{id}` accepts `UserUpdateRequest{status}` — this is essentially a PUT to a sub-resource, not a true JSON Patch. |

---

## 6. Observability

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 6.1 | No structured logging | High | Medium | All logging uses unstructured SLF4J format strings (`log.info("Reading account by ID {}", ...)`). No JSON logging, no MDC correlation, no consistent log levels. |
| 6.2 | Sensitive data logged | Critical | Small | `log.info("Creating user with {}", request.toString())` — logs passwords. `log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())` — logs financial data. |
| 6.3 | No health check customization | Medium | Small | Default `/actuator/health` is exposed but no custom health indicators for database connectivity, Keycloak availability, or downstream service health. |
| 6.4 | No metrics endpoints configured | Medium | Medium | Actuator is included but Prometheus metrics endpoint (`/actuator/prometheus`) is not explicitly enabled or configured despite README mentioning Prometheus. |
| 6.5 | No alerting or monitoring configuration | Low | Medium | No Prometheus scrape config, no Grafana dashboards, no alerting rules defined. |
| 6.6 | Zipkin tracing has no sampling configuration | Low | Small | Default sampling rate is used. No explicit `management.tracing.sampling.probability` configuration visible (defaults to 0.1 in production). |
| 6.7 | No request/response logging at gateway level | Medium | Small | API Gateway doesn't log request/response metadata (method, path, status code, latency). No access log filter configured. |

---

## 7. Resilience

### Findings

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| 7.1 | No circuit breaker pattern | High | Medium | OpenFeign calls to core-banking-service have no circuit breaker (Resilience4j, Hystrix). If core banking is down, all upstream services will hang/cascade fail. |
| 7.2 | No retry policies on Feign clients | High | Small | No `@Retryable` or Feign retry configuration. Transient network errors cause immediate failure. |
| 7.3 | No timeout configuration on Feign clients | High | Small | Default Feign/HTTP timeouts are used (no explicit connect/read timeout). A slow downstream can block threads indefinitely. |
| 7.4 | No fallback behavior | Medium | Medium | No `@FeignClient(fallback = ...)` or `fallbackFactory` configured. Failed calls return raw errors instead of graceful degradation. |
| 7.5 | No bulkhead / thread pool isolation | Medium | Medium | All Feign calls share the same thread pool. A slow core-banking-service will exhaust all threads, affecting unrelated endpoints. |
| 7.6 | Transaction not marked FAILED on Feign error | High | Small | In `FundTransferService.fundTransfer()`, if `bankingCoreFeignClient.fundTransfer()` throws an exception, the entity remains in `PENDING` status forever — no catch block to update to `FAILED`. Same pattern in utility payment service. |
| 7.7 | No idempotency mechanism | High | Medium | Fund transfer and utility payment endpoints have no idempotency key. Network retries by clients can cause duplicate transactions. |
| 7.8 | wait-for-it.sh has finite timeout | Low | Small | 50-second timeout may not be sufficient for slow infrastructure startup. No exponential backoff or configurable retry. |
| 7.9 | No graceful shutdown configuration | Medium | Small | No `server.shutdown=graceful` or `spring.lifecycle.timeout-per-shutdown-phase` configured. In-flight requests may be terminated on container restart. |

---

## Gap Summary by Severity

| Severity | Count | Key Areas |
|----------|-------|-----------|
| **Critical** | 4 | Input validation, credential exposure, exception leakage, sensitive data logging |
| **High** | 11 | Test coverage, circuit breakers, timeouts, HTTP status codes, idempotency |
| **Medium** | 17 | Code organization, observability, error consistency, rate limiting |
| **Low** | 8 | Naming conventions, documentation, minor inconsistencies |

**Total Gaps Identified: 40**
