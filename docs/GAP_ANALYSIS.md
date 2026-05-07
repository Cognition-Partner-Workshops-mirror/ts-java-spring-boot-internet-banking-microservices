# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

---

## 1. Code Organization

### 1.1 Inconsistent Package Structure Across Services

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** While all services share a base package (`com.javatodev.finance`), the sub-package layout varies:

- **Core Banking:** `controller`, `service`, `model.entity`, `model.dto`, `model.mapper`, `repository`, `exception`
- **User Service:** `controller`, `service`, `service.rest`, `model.entity`, `model.dto`, `model.mapper`, `model.repository`, `model.rest.response`, `configuration.keycloak`, `configuration.filter`, `configuration.audit`, `configuration.feign`
- **Fund Transfer:** `controller`, `service`, `service.rest.client`, `model.entity`, `model.dto`, `model.mapper`, `model.repository`, `model.dto.request`, `model.dto.response`, `configuration`, `configuration.filter`, `configuration.audit`
- **Utility Payment:** `controller`, `service`, `service.rest`, `model.entity`, `model.dto`, `model.mapper`, `model.rest.request`, `model.rest.response`, `repository`, `configuration`, `configuration.filter`, `configuration.audit`

The repository interfaces are in `repository` (core, utility-payment), `model.repository` (user, fund-transfer). DTO request/response packages differ (`model.dto.request` vs `model.rest.request`).

**Impact:** Developers must re-learn conventions when switching services. Harder to extract shared libraries.

### 1.2 No Shared Library / Common Module

| Severity | Effort |
|----------|--------|
| Medium | Large |

**Finding:** Common code (e.g., `AuditAware`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `ErrorResponse`, `GlobalExceptionHandler`) is duplicated across services rather than extracted into a shared library.

**Impact:** Bug fixes and pattern changes must be applied N times. Drift between implementations.

### 1.3 Mapper Implementation

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** All mappers extend a custom `BaseMapper<E,D>` interface with manual implementations. No use of MapStruct or ModelMapper for compile-time safety.

**Impact:** Manual mapping is error-prone and verbose. Field additions require touching mapper code.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** 
- Core Banking returns: `{ "code": "string", "message": "string" }` for known exceptions and raw strings for unknown exceptions.
- User Service returns the same `ErrorResponse` shape but with different error code prefixes (`USER-SERVICE-1xxx` vs `BANKING-CORE-SERVICE-1xxx`).
- Fund Transfer and Utility Payment services have `GlobalExceptionHandler` classes but with different implementations.
- The gateway has no error handling customization — downstream errors may be wrapped inconsistently.

**Impact:** Clients cannot reliably parse error responses. No unified error contract.

### 2.2 Missing HTTP Status Code Differentiation

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** Almost all error cases return HTTP 400 (Bad Request) regardless of the actual error type:
- Entity not found → should be 404
- Insufficient funds → should be 422 (Unprocessable Entity)
- Already registered → should be 409 (Conflict)
- The generic catch-all `Exception.class` handler also returns 400

**Impact:** Clients cannot distinguish error categories by status code. Breaks RESTful conventions.

### 2.3 Raw Controller Return Types

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Most controllers use raw `ResponseEntity` without generic type parameters (e.g., `public ResponseEntity fundTransfer(...)` instead of `public ResponseEntity<FundTransferResponse> fundTransfer(...)`).

**Impact:** No compile-time type safety. Swagger/OpenAPI documentation cannot infer response types automatically.

---

## 3. Testing

### 3.1 Minimal Unit Test Coverage

| Severity | Effort |
|----------|--------|
| Critical | Large |

**Finding:** 
- **Core Banking:** Has 3 test files (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) + application context test.
- **User Service:** Only application context test (`InternetBankingUserServiceApplicationTests`).
- **Fund Transfer Service:** Only application context test.
- **Utility Payment Service:** Only application context test.
- **API Gateway, Config Server, Service Registry:** Only application context tests.

Most non-core services have zero business logic tests.

**Impact:** No regression safety net. Refactoring is risky. Bugs ship to production undetected.

### 3.2 No Integration Tests

| Severity | Effort |
|----------|--------|
| High | Large |

**Finding:** No integration tests with Testcontainers, WireMock, or similar tools to verify:
- Feign client calls between services
- Database operations with real MySQL
- Keycloak integration
- End-to-end flows through the gateway

**Impact:** Confidence in cross-service behavior relies entirely on manual testing.

### 3.3 No Contract Tests

| Severity | Effort |
|----------|--------|
| Medium | Large |

**Finding:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. Feign clients could break silently if a provider changes its API.

**Impact:** Breaking changes in one service may not be detected until runtime.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source

| Severity | Effort |
|----------|--------|
| Critical | Small |

**Finding:** Multiple credentials are committed to the repository:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `Dockerfile`)
- MySQL app credentials: `javatodev_development` / `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin: `admin` / `password` (in `docker-compose.yml`)
- Keycloak DB: `keycloak` / `password` (in `docker-compose.yml`)
- Keycloak client secret: `0efd3e37-258e-4488-96ae-1dfe34679c9d` (in Postman environment)
- Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` (in `README.md`)

**Impact:** Secrets exposed in version control. Anyone with repo access has full database/Keycloak access.

### 4.2 No Input Validation

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Email` annotations on any request DTOs. No Bean Validation dependency in any service. Examples:
- `FundTransferRequest.amount` accepts null or negative values
- `User.email` has no format validation
- `UtilityPaymentRequest.referenceNumber` has no length constraints

**Impact:** Invalid data can corrupt the database and cause unexpected NullPointerExceptions deep in business logic.

### 4.3 No Rate Limiting

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** The API Gateway has no rate limiting configuration. No Spring Cloud Gateway rate limiter filter is configured.

**Impact:** Susceptible to brute-force attacks and denial-of-service.

### 4.4 CSRF Disabled Without Justification

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)` — acceptable for a stateless API but no security headers (CORS, HSTS, X-Frame-Options) are configured.

**Impact:** Missing defense-in-depth headers.

### 4.5 No Dependency Vulnerability Scanning

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** No OWASP Dependency Check, Snyk, or Trivy integration in the build pipeline.

**Impact:** Known CVEs in dependencies go undetected.

---

## 5. API Design

### 5.1 Non-standard Endpoint Naming

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** 
- `PATCH /api/v1/bank-users/update/{id}` — the verb `update` is redundant (PATCH already implies update).
- `/api/v1/account/util-account/{account_name}` — inconsistent abbreviation.
- `/api/v1/transaction/util-payment` — mixes abbreviation styles.

**Impact:** Minor inconsistency; may confuse API consumers.

### 5.2 No Filtering or Sorting on List Endpoints

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** All list endpoints accept only `Pageable` (page, size, sort) but no domain-specific filtering (e.g., filter transfers by status, date range, account).

**Impact:** Clients must fetch all data and filter client-side, increasing bandwidth and latency.

### 5.3 No HATEOAS or Pagination Metadata

| Severity | Effort |
|----------|--------|
| Low | Medium |

**Finding:** List endpoints return raw `List<T>` without pagination metadata (totalElements, totalPages, current page, links).

**Impact:** Clients cannot implement proper pagination UIs without knowing total counts.

### 5.4 OpenAPI/Swagger Only Partially Configured

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** The `springdoc-openapi-starter-webflux-ui` dependency is included but:
- Controller methods use raw `ResponseEntity` without type parameters.
- No `@ApiResponse` annotations for error cases.
- No global OpenAPI configuration (title, version, description, security schemes).
- Uses `webflux-ui` variant in non-reactive services (should be `webmvc-ui`).

**Impact:** Auto-generated API documentation is incomplete and potentially non-functional.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:**
- Not all methods log entry/exit.
- Log messages vary in format (some include request details, some don't).
- No structured logging (JSON) configured — defaults to plain text.
- No MDC correlation ID propagation beyond Zipkin trace IDs.

**Impact:** Troubleshooting production issues requires manual log correlation.

### 6.2 Health Checks Not Customized

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** Services rely on default Spring Boot Actuator health checks. No custom health indicators for:
- Keycloak connectivity
- Feign client target availability
- Config server reachability post-startup

**Impact:** Health endpoint may report UP while critical dependencies are down.

### 6.3 No Alerting or Metrics Dashboards

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** While Micrometer dependencies are included, there is no:
- Prometheus scrape configuration
- Grafana dashboards
- Custom business metrics (e.g., transfer count, payment volume)

**Impact:** No operational visibility into system behavior beyond traces.

### 6.4 Zipkin Tracing Coverage Gaps

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** Tracing libraries are included in all services, but sampling rate and propagation configuration are externalized to the config server and not visible in the repo. No verification that database queries or RabbitMQ (future) interactions are traced.

**Impact:** Potential blind spots in trace data.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** No Resilience4j or Spring Cloud Circuit Breaker dependency in any service. All Feign calls to Core Banking are fire-and-forget — if Core Banking is slow or down, callers hang indefinitely or fail hard.

**Impact:** Cascading failures. A single slow service can take down the entire system.

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** No `@Retry` annotations or Feign Retryer configuration. Transient network failures cause immediate hard failures.

**Impact:** Intermittent issues (network blips, brief GC pauses) surface as user-facing errors.

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** No explicit timeout configuration for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Gateway route timeouts

Default timeouts (often infinite or very long) are inherited.

**Impact:** Thread exhaustion under load. Requests hang indefinitely if a dependency is unresponsive.

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** No `@FeignClient(fallback = ...)` or `@CircuitBreaker(fallbackMethod = ...)` implementations. When Core Banking is unavailable, users see raw 500 errors.

**Impact:** Poor user experience during partial outages. No graceful degradation.

### 7.5 No Bulkhead Isolation

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** All Feign calls share the same thread pool. A slow endpoint in Core Banking consumes threads needed by other operations.

**Impact:** Resource contention between independent operations.

---

## Summary Table

| Category | Gap | Severity | Effort |
|----------|-----|----------|--------|
| Code Organization | Inconsistent package structure | Medium | Medium |
| Code Organization | No shared library | Medium | Large |
| Code Organization | Manual mappers | Low | Small |
| Error Handling | Inconsistent error format | High | Medium |
| Error Handling | Wrong HTTP status codes | High | Small |
| Error Handling | Raw ResponseEntity types | Medium | Small |
| Testing | Minimal unit test coverage | Critical | Large |
| Testing | No integration tests | High | Large |
| Testing | No contract tests | Medium | Large |
| Security | Hardcoded credentials | Critical | Small |
| Security | No input validation | Critical | Medium |
| Security | No rate limiting | Medium | Medium |
| Security | Missing security headers | Low | Small |
| Security | No dependency scanning | Medium | Small |
| API Design | Non-standard endpoint names | Low | Small |
| API Design | No filtering/sorting | Medium | Medium |
| API Design | No pagination metadata | Low | Medium |
| API Design | Incomplete OpenAPI config | Medium | Small |
| Observability | Inconsistent logging | Medium | Small |
| Observability | No custom health checks | Low | Small |
| Observability | No metrics dashboards | Medium | Medium |
| Observability | Tracing coverage gaps | Low | Small |
| Resilience | No circuit breakers | High | Medium |
| Resilience | No retry policies | High | Small |
| Resilience | No timeout configuration | High | Small |
| Resilience | No fallback behavior | Medium | Medium |
| Resilience | No bulkhead isolation | Medium | Medium |
