# Engineering Standards Gap Analysis

This document compares the current codebase against industry best practices for Java/Spring Boot microservice architectures. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale:**
- **Critical** — Security vulnerability, data-loss risk, or production-blocking defect
- **High** — Significant architectural concern that impairs reliability, maintainability, or scalability
- **Medium** — Deviation from best practice that increases technical debt
- **Low** — Cosmetic or minor improvement opportunity

**Effort Scale:**
- **Small** — < 1 day; config change or minor refactor
- **Medium** — 1–3 days; moderate refactoring or new component
- **Large** — 3+ days; significant architecture change or cross-cutting work

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Gap** | Each service is an independent Gradle project with its own `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to coordinate builds, enforce consistent dependency versions, or share common plugins. |
| **Impact** | Dependency version drift between services (e.g., different MySQL connector or Lombok versions), duplicated build logic, no single `./gradlew build` to verify the whole project. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Gap** | Identical classes are copy-pasted across multiple services: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`. |
| **Impact** | Changes must be applied to 3–4 copies. Bug fixes can be missed in one copy. Inconsistencies already exist (e.g., fund-transfer-service uses constructor-based `ErrorResponse`, while others use `@Builder`). |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Gap** | Package layout varies across services. For example: `model.repository` (user-service, fund-transfer-service) vs. `repository` (core-banking-service, utility-payment-service). `model.rest.request`/`model.rest.response` (utility-payment-service) vs. `model.dto.request`/`model.dto.response` (fund-transfer-service, core-banking-service). Configuration class placement differs (e.g., `configuration.feign` in user-service vs. top-level `configuration` in fund-transfer-service). |
| **Impact** | Cognitive overhead for developers switching between services; harder onboarding. |
| **Severity** | Low |
| **Effort** | Medium |

### 1.4 Mappers Instantiated Manually (Not Spring Beans)

| | |
|---|---|
| **Gap** | All mapper classes (`UserMapper`, `BankAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` inside service classes rather than being managed as Spring beans. |
| **Impact** | Cannot be mocked in unit tests, no lifecycle management, inconsistent with Spring dependency injection patterns used everywhere else. |
| **Severity** | Low |
| **Effort** | Small |

---

## 2. Error Handling

### 2.1 Raw `ResponseEntity` Return Types (No Generics)

| | |
|---|---|
| **Gap** | Every controller method returns raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). Compiler cannot verify response body types. |
| **Impact** | No compile-time safety, misleading OpenAPI documentation, IDE warnings suppressed. |
| **Severity** | Medium |
| **Effort** | Small |

### 2.2 Generic Exception Catch-All Returns 400 Bad Request

| | |
|---|---|
| **Gap** | All `GlobalExceptionHandler` implementations catch `Exception.class` and return `400 Bad Request` with a string body: `"Exception occur inside API " + e`. This exposes internal stack trace information and uses the wrong HTTP status code for server errors. |
| **Impact** | Security risk (leaks exception details to clients), incorrect HTTP semantics (should be 500 for unexpected errors), inconsistent error response format (string vs. `ErrorResponse` object). |
| **Severity** | Critical |
| **Effort** | Small |

### 2.3 No Validation on Request Bodies

| | |
|---|---|
| **Gap** | No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` annotations on any request DTO. `FundTransferRequest`, `UtilityPaymentRequest`, and user registration requests accept any payload, including null or negative amounts. |
| **Impact** | Invalid data reaches business logic and database; unclear error messages for malformed requests. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.4 No Compensation / Rollback in Orchestrator Services

| | |
|---|---|
| **Gap** | `FundTransferService` and `UtilityPaymentService` persist a local record with status `PENDING`/`PROCESSING`, then call core-banking-service. If the Feign call fails, the local entity is never updated to `FAILED` — it remains in a stale intermediate state forever. |
| **Impact** | Orphaned records in `fund_transfer` / `utility_payment` tables; no retry or compensation logic; inconsistent distributed state. |
| **Severity** | High |
| **Effort** | Medium |

### 2.5 Inconsistent Error Response Structures

| | |
|---|---|
| **Gap** | `ErrorResponse` is duplicated in 4 services with slightly different implementations (builder pattern vs. constructor). The catch-all handler returns a plain `String` while the business exception handler returns an `ErrorResponse` object. Clients cannot reliably parse error responses. |
| **Impact** | API consumers must handle multiple error formats; harder to build a consistent frontend error handling layer. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 3. Testing

### 3.1 Minimal Test Coverage

| | |
|---|---|
| **Gap** | Only `core-banking-service` has real unit tests (3 test classes covering `AccountService`, `TransactionService`, `UserService`). The other 5 services have only default Spring Boot application context tests (`@SpringBootTest` with empty body) that **would fail** when run because they require Config Server/Eureka connectivity. |
| **Impact** | No confidence in correctness of user-service, fund-transfer-service, or utility-payment-service business logic. Regressions go undetected. |
| **Severity** | High |
| **Effort** | Large |

### 3.2 No Integration Tests

| | |
|---|---|
| **Gap** | No tests verify actual HTTP endpoint behavior (e.g., `@WebMvcTest`, `MockMvc`, `TestRestTemplate`). No tests verify Feign client behavior, database queries, or Flyway migration execution. |
| **Impact** | Controller-level bugs (incorrect request mapping, missing serialization annotations, wrong status codes) are undetected. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests Between Services

| | |
|---|---|
| **Gap** | No Spring Cloud Contract, Pact, or similar consumer-driven contract tests. The Feign client interfaces (e.g., `BankingCoreFeignClient`) define contracts implicitly but these are never verified against the actual provider endpoints. |
| **Impact** | Provider API changes can silently break consumers. DTO mismatches between services (e.g., `AccountResponse.number` is `Long` in fund-transfer-service but `String` in core-banking-service). |
| **Severity** | Medium |
| **Effort** | Large |

### 3.4 Application Context Tests Will Fail

| | |
|---|---|
| **Gap** | Default `@SpringBootTest` tests in user-service, fund-transfer-service, utility-payment-service, api-gateway, and config-server attempt to load the full application context, which requires Config Server and Eureka to be running. The test `application.yml` files disable Eureka but don't fully stub all dependencies (e.g., Keycloak in user-service). |
| **Impact** | CI pipeline cannot run these tests without infrastructure; tests are effectively dead code. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| | |
|---|---|
| **Gap** | Database passwords are hardcoded in `docker-compose.yml` (`MYSQL_ROOT_PASSWORD: woVERANKliGharym`, `POSTGRES_PASSWORD: password`), `privileges.sql` (`IDENTIFIED BY 'oPItyPticIAt'`), and Keycloak admin credentials (`KEYCLOAK_ADMIN_PASSWORD: password`). Test Keycloak client secrets are committed in `application.yml`. |
| **Impact** | If any of these credentials are reused in production, they are exposed in version control. Even for development, this sets a bad precedent. |
| **Severity** | High |
| **Effort** | Small |

### 4.2 No Input Validation

| | |
|---|---|
| **Gap** | (Same as 2.3) No Bean Validation annotations on any DTO. Negative transfer amounts, empty account numbers, and missing fields are silently accepted. |
| **Impact** | Potential for negative balance manipulation, NullPointerException in business logic, SQL injection risk (though mitigated by JPA parameterization). |
| **Severity** | Critical |
| **Effort** | Small |

### 4.3 No Rate Limiting or Throttling

| | |
|---|---|
| **Gap** | The API gateway has no rate limiting configuration. There are no request throttling mechanisms at any layer. |
| **Impact** | Vulnerable to brute-force attacks, denial of service, and abuse of fund transfer/payment endpoints. |
| **Severity** | High |
| **Effort** | Medium |

### 4.4 CSRF Disabled Without Justification

| | |
|---|---|
| **Gap** | `SecurityConfiguration` in the API gateway explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a pure API (no browser-rendered forms), this is done without any code comment or documented justification. |
| **Impact** | Low risk for a REST API, but if the gateway ever serves web content, CSRF protection would be missing. |
| **Severity** | Low |
| **Effort** | Small |

### 4.5 Internal Services Have No Authentication

| | |
|---|---|
| **Gap** | Only the API gateway enforces OAuth2/JWT authentication. The individual microservices (core-banking-service, user-service, fund-transfer-service, utility-payment-service) have **no security configuration** and accept requests from any caller. |
| **Impact** | If services are exposed (intentionally or via misconfiguration), they can be accessed without authentication. Defense-in-depth is absent. |
| **Severity** | High |
| **Effort** | Medium |

### 4.6 Keycloak Singleton Anti-Pattern

| | |
|---|---|
| **Gap** | `KeycloakProperties` uses a `static` singleton for the Keycloak client instance. This is not thread-safe (double-checked locking is missing), cannot be refreshed if credentials rotate, and prevents proper lifecycle management. |
| **Impact** | Potential race condition during initialization; stale Keycloak connections. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.7 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Gap** | No OWASP Dependency-Check, Snyk, or similar dependency scanning is configured in the build. |
| **Impact** | Known CVEs in transitive dependencies go undetected. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 5. API Design

### 5.1 No API Versioning Strategy

| | |
|---|---|
| **Gap** | While endpoints use `/api/v1/`, there is no documented versioning strategy, no mechanism for running multiple versions simultaneously, and no content-type negotiation. |
| **Impact** | Future breaking changes will require ad-hoc migration; no backward compatibility guarantee. |
| **Severity** | Low |
| **Effort** | Small |

### 5.2 Inconsistent Resource Naming

| | |
|---|---|
| **Gap** | Resource names are inconsistent: `/bank-users` (user-service) vs. `/user` (core-banking-service), `/transfer` (fund-transfer-service) vs. `/transaction/fund-transfer` (core-banking-service), `/util-account` uses abbreviation while `/utility-payment` spells it out. |
| **Impact** | Confusing API surface; harder for consumers to predict endpoint patterns. |
| **Severity** | Low |
| **Effort** | Small |

### 5.3 Pagination Returns Raw Lists

| | |
|---|---|
| **Gap** | Paginated endpoints in user-service, fund-transfer-service, and utility-payment-service return `List<T>` instead of a `Page<T>` wrapper. The total count, page number, and page size are lost. Only core-banking-service endpoints accept `Pageable` parameters but also return raw lists. |
| **Impact** | Clients cannot implement proper pagination UI; they don't know total pages or total element count. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.4 No Filtering or Sorting Documentation

| | |
|---|---|
| **Gap** | While `Pageable` supports `sort` query parameters, there is no documentation or OpenAPI specification for available sort fields or filtering capabilities. |
| **Impact** | API consumers must guess or read source code to understand query capabilities. |
| **Severity** | Low |
| **Effort** | Small |

### 5.5 OpenAPI/Swagger Misconfiguration

| | |
|---|---|
| **Gap** | Services include `springdoc-openapi-starter-webflux-ui` (WebFlux variant) despite being traditional Spring MVC (servlet-based) applications. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This likely causes the Swagger UI to not render correctly. |
| **Impact** | Swagger UI may not work; incorrect dependency for the application stack. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.6 No Standard Response Envelope

| | |
|---|---|
| **Gap** | Success responses return the raw DTO. Error responses return either an `ErrorResponse` object or a plain string. There is no standard wrapper (e.g., `{ data: ..., errors: [...], meta: {...} }`). |
| **Impact** | Inconsistent client parsing logic; harder to add metadata (pagination, timestamps, request IDs). |
| **Severity** | Medium |
| **Effort** | Medium |

---

## 6. Observability

### 6.1 Inconsistent Logging

| | |
|---|---|
| **Gap** | Some controllers use `@Slf4j` with `log.info()` while others have no logging. Log messages vary widely in format. Sensitive data (request bodies with account numbers) is logged via `toString()`. No structured logging (JSON) is configured. |
| **Impact** | Difficult to correlate logs across services; potential PII exposure in logs; log aggregation is harder without structured format. |
| **Severity** | Medium |
| **Effort** | Medium |

### 6.2 No Custom Health Checks

| | |
|---|---|
| **Gap** | Spring Boot Actuator is included but only default health indicators are active. No custom health checks for: database connectivity readiness, Keycloak availability, Feign client target health, or downstream service liveness. |
| **Impact** | Health endpoint may report `UP` even when critical dependencies (Keycloak, MySQL, core-banking-service) are down. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Metrics Endpoints

| | |
|---|---|
| **Gap** | While `spring-boot-starter-actuator` is included, there is no Prometheus or Micrometer metrics export configured. The README lists Prometheus in the tech stack, but no `micrometer-registry-prometheus` dependency is present and no `/actuator/prometheus` endpoint is configured. |
| **Impact** | No quantitative observability: no request rates, error rates, latency percentiles, JVM metrics, or business metrics. |
| **Severity** | High |
| **Effort** | Small |

### 6.4 Distributed Tracing Configuration Is Externalized

| | |
|---|---|
| **Gap** | Zipkin dependencies are present, but the actual Zipkin URL and sampling configuration are not in any committed config file — they're assumed to be in the external Git-backed config. If the config repo is unavailable or misconfigured, tracing silently fails. |
| **Impact** | Tracing may not work out of the box; no visibility into whether traces are being sampled. |
| **Severity** | Low |
| **Effort** | Small |

### 6.5 No Request/Response Logging at Gateway

| | |
|---|---|
| **Gap** | The API gateway's `GatewayConfiguration` injects the `X-Auth-Id` header but has no access logging, request/response body logging, or correlation ID generation. |
| **Impact** | No audit trail at the entry point; difficult to debug request flow issues. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Gap** | No circuit breaker library (Resilience4j, Hystrix, Spring Cloud Circuit Breaker) is present in any service. Feign clients have no fallback methods. |
| **Impact** | A failure in core-banking-service will cascade to all upstream services. Latency spikes in one service will propagate as thread pool exhaustion in callers. |
| **Severity** | High |
| **Effort** | Medium |

### 7.2 No Retry Policies

| | |
|---|---|
| **Gap** | No retry configuration on Feign clients or at the gateway level. Transient network failures cause immediate request failure with no recovery attempt. |
| **Impact** | Reduced availability during transient network issues; poor user experience for intermittent failures. |
| **Severity** | Medium |
| **Effort** | Small |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Gap** | Feign client timeouts are not configured (defaults to infinite read timeout in some versions). Gateway route timeouts are not set. There are no explicit connection or read timeout properties in any service. |
| **Impact** | A slow core-banking-service response will hold caller threads indefinitely; potential thread starvation and cascading failures. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Gap** | No Feign fallback factories, no graceful degradation patterns. If the core-banking-service is down, fund transfer and utility payment endpoints return raw Feign exception details. |
| **Impact** | Users see cryptic error messages; no graceful degradation or queued-for-retry behavior. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 No Bulkhead Isolation

| | |
|---|---|
| **Gap** | No thread pool or semaphore bulkhead isolation between Feign client calls. All Feign calls share the default thread pool. |
| **Impact** | A slow endpoint in core-banking-service can consume all available threads, blocking unrelated API calls. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.6 No Idempotency Keys

| | |
|---|---|
| **Gap** | Fund transfer and utility payment endpoints have no idempotency mechanism. The same request submitted twice will create two transfers/payments. |
| **Impact** | Duplicate transactions in case of network retries, user double-clicks, or client-side retry logic. |
| **Severity** | High |
| **Effort** | Medium |

### 7.7 `wait-for-it.sh` Used for Startup Ordering

| | |
|---|---|
| **Gap** | Docker Compose uses `wait-for-it.sh` to block service startup until dependencies are listening on TCP ports. This only checks port availability, not application readiness (e.g., config server may accept TCP connections before serving configuration). |
| **Impact** | Race conditions during startup; services may fail to fetch configuration or register with Eureka despite `wait-for-it.sh` passing. |
| **Severity** | Low |
| **Effort** | Small |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.2 | Exception catch-all leaks internals, returns 400 | Error Handling | **Critical** | Small |
| 2.3 / 4.2 | No input validation on request DTOs | Security / Error Handling | **Critical** | Small |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 2.4 | No compensation/rollback in orchestrators | Error Handling | High | Medium |
| 3.1 | Minimal test coverage (only core-banking) | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 4.1 | Hardcoded credentials in source | Security | High | Small |
| 4.3 | No rate limiting | Security | High | Medium |
| 4.5 | Internal services have no authentication | Security | High | Medium |
| 6.3 | No metrics/Prometheus endpoints | Observability | High | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.6 | No idempotency keys | Resilience | High | Medium |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 2.1 | Raw `ResponseEntity` (no generics) | Error Handling | Medium | Small |
| 2.5 | Inconsistent error response structures | Error Handling | Medium | Small |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Application context tests will fail | Testing | Medium | Small |
| 4.6 | Keycloak singleton anti-pattern | Security | Medium | Small |
| 4.7 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.3 | Pagination returns raw lists | API Design | Medium | Small |
| 5.5 | OpenAPI/Swagger wrong dependency | API Design | Medium | Small |
| 5.6 | No standard response envelope | API Design | Medium | Medium |
| 6.1 | Inconsistent logging | Observability | Medium | Medium |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.5 | No request/response logging at gateway | Observability | Medium | Small |
| 7.2 | No retry policies | Resilience | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No bulkhead isolation | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Medium |
| 1.4 | Mappers not Spring beans | Code Organization | Low | Small |
| 4.4 | CSRF disabled without justification | Security | Low | Small |
| 5.1 | No versioning strategy documented | API Design | Low | Small |
| 5.2 | Inconsistent resource naming | API Design | Low | Small |
| 5.4 | No filtering/sorting documentation | API Design | Low | Small |
| 6.4 | Tracing config externalized (no defaults) | Observability | Low | Small |
| 7.7 | `wait-for-it.sh` only checks TCP port | Resilience | Low | Small |
