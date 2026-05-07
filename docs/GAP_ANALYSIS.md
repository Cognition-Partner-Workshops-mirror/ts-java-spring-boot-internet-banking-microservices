# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices and documents gaps with severity ratings and effort estimates.

**Severity scale:** Critical > High > Medium > Low
**Effort scale:** Small (< 1 day per service) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Gap** | Each microservice is an independent Gradle project with its own `gradlew`, `gradle/wrapper`, `settings.gradle`, and `build.gradle`. There is no root-level `settings.gradle` or shared build configuration. |
| **Impact** | Dependency versions, plugins, and common configuration are duplicated across 6 services. Version drift is likely. Cannot run a single `./gradlew build` for the whole project. |
| **Severity** | **Medium** |
| **Effort** | **Medium** |

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Gap** | Exception classes (`SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`), audit infrastructure (`AuditAware`, `AuditConfig`, `AuditorAwareConfig`), filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`), and mapper base classes (`BaseMapper`) are copy-pasted across multiple services with minor variations. |
| **Impact** | Bug fixes must be applied in multiple places. Inconsistencies creep in (e.g., `ErrorResponse` uses `@Builder` in core-banking but constructor in fund-transfer). |
| **Severity** | **High** |
| **Effort** | **Medium** |

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Gap** | Package layout varies: core-banking uses `repository/` at the root, fund-transfer uses `model/repository/`, user-service uses `model/repository/`. Feign clients are in `service/rest/client/` (fund-transfer) vs `service/rest/` (user, utility-payment). Configuration packages differ (`configuration/feign/` vs `configuration/`). |
| **Impact** | Makes navigating the codebase harder for new developers. No established convention. |
| **Severity** | **Low** |
| **Effort** | **Small** |

### 1.4 Mapper Instantiation Anti-Pattern

| | |
|---|---|
| **Gap** | Mappers are instantiated inline (`private UserMapper userMapper = new UserMapper()`) instead of being injected as Spring beans. This bypasses dependency injection and makes testing harder. |
| **Impact** | Cannot mock or replace mappers in tests; violates IoC principle. |
| **Severity** | **Low** |
| **Effort** | **Small** |

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400 for Everything

| | |
|---|---|
| **Gap** | In all four `GlobalExceptionHandler` implementations, the catch-all `@ExceptionHandler({Exception.class})` returns `400 Bad Request` with a raw exception string: `"Exception occur inside API " + e`. This exposes internal stack traces to the client, and maps server errors (NPE, DB connection failures, etc.) as 400. |
| **Impact** | Information leakage (security risk). 5xx errors are masked as 4xx. Clients cannot distinguish client errors from server errors. |
| **Severity** | **Critical** |
| **Effort** | **Small** |

### 2.2 Missing HTTP Status Code Differentiation

| | |
|---|---|
| **Gap** | All custom exceptions return `400 Bad Request` regardless of semantics. `EntityNotFoundException` should return `404`, `InsufficientFundsException` should return `422 Unprocessable Entity` or a domain-specific code. |
| **Impact** | REST API clients cannot rely on HTTP status codes for error handling. |
| **Severity** | **High** |
| **Effort** | **Small** |

### 2.3 No Feign Error Decoding in Most Services

| | |
|---|---|
| **Gap** | Only `user-service` has a `CustomFeignErrorDecoder`. `fund-transfer-service` and `utility-payment-service` rely on default Feign error handling, which throws generic `FeignException`. Core banking error context (error codes, messages) is lost in transit. |
| **Impact** | When core-banking-service returns a business error (e.g., insufficient funds), the calling service cannot propagate the specific error to the client. |
| **Severity** | **High** |
| **Effort** | **Medium** |

### 2.4 No Validation on Request Bodies

| | |
|---|---|
| **Gap** | None of the `@RequestBody` parameters use Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, etc.). A fund transfer request with null `fromAccount`, null `toAccount`, or negative `amount` will pass through to the service layer and cause an unhandled NPE or unexpected behavior. |
| **Impact** | Bad input causes cryptic 400 responses (from the generic catch-all) instead of clear validation errors. |
| **Severity** | **Critical** |
| **Effort** | **Small** |

### 2.5 Inconsistent Error Response Format

| | |
|---|---|
| **Gap** | Custom exceptions return `ErrorResponse` (JSON with `code` and `message`). The generic catch-all returns a plain string. Clients must handle two different response shapes for the same endpoint. |
| **Impact** | Unreliable API contract; harder for frontend/mobile clients to parse errors. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

---

## 3. Testing

### 3.1 Tests Only Exist in core-banking-service

| | |
|---|---|
| **Gap** | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have only the default Spring Boot context-load test (`*ApplicationTests.java`), which likely fails without infrastructure. |
| **Impact** | No automated verification of business logic in fund-transfer, utility-payment, or user services. Regressions are undetectable. |
| **Severity** | **Critical** |
| **Effort** | **Large** |

### 3.2 No Integration Tests

| | |
|---|---|
| **Gap** | No integration tests exist. No `@SpringBootTest` tests with `TestContainers` or embedded databases to verify actual Spring context wiring, JPA queries, or Flyway migrations. |
| **Impact** | Wiring issues, query bugs, and migration errors are only caught in production or manual testing. |
| **Severity** | **High** |
| **Effort** | **Large** |

### 3.3 No Contract Tests Between Services

| | |
|---|---|
| **Gap** | No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between the services that communicate via Feign. |
| **Impact** | API changes in core-banking-service can silently break fund-transfer-service and utility-payment-service. |
| **Severity** | **Medium** |
| **Effort** | **Large** |

### 3.4 No Test Coverage Reporting

| | |
|---|---|
| **Gap** | No JaCoCo or similar coverage plugin is configured. No coverage thresholds enforced. |
| **Impact** | Cannot measure or enforce test quality. |
| **Severity** | **Low** |
| **Effort** | **Small** |

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose

| | |
|---|---|
| **Gap** | MySQL root password (`woVERANKliGharym`), Keycloak admin credentials (`admin`/`password`), and PostgreSQL credentials (`keycloak`/`password`) are hardcoded in `docker-compose.yml`. Test credentials are in the README. |
| **Impact** | If these files are used as-is in any non-local environment, credentials are exposed. |
| **Severity** | **High** |
| **Effort** | **Small** |

### 4.2 CSRF Disabled at Gateway

| | |
|---|---|
| **Gap** | `SecurityConfiguration` explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While common for stateless APIs, no CORS configuration is present either. |
| **Impact** | If the API is ever consumed by a browser-based client, it is vulnerable to CSRF attacks without additional protections. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 4.3 No Input Validation (see Error Handling 2.4)

| | |
|---|---|
| **Gap** | Absence of `@Valid` / Bean Validation on all request DTOs. No sanitization of user inputs. |
| **Impact** | Potential SQL injection (mitigated by JPA parameterized queries) and business logic abuse. |
| **Severity** | **Critical** |
| **Effort** | **Small** |

### 4.4 Stack Trace Exposure (see Error Handling 2.1)

| | |
|---|---|
| **Gap** | Generic exception handler concatenates the full exception object into the response body. |
| **Impact** | Exposes internal class names, SQL queries, and potentially sensitive data to API consumers. |
| **Severity** | **Critical** |
| **Effort** | **Small** |

### 4.5 No Rate Limiting

| | |
|---|---|
| **Gap** | No rate limiting at the API Gateway or service level. |
| **Impact** | Vulnerable to brute-force attacks (especially on registration endpoint which is unauthenticated) and denial-of-service. |
| **Severity** | **Medium** |
| **Effort** | **Medium** |

### 4.6 Keycloak Singleton Not Thread-Safe

| | |
|---|---|
| **Gap** | `KeycloakProperties.getInstance()` uses a lazy singleton pattern without synchronization. In a multi-threaded environment, multiple `Keycloak` instances could be created. |
| **Impact** | Potential resource leaks or inconsistent state. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 4.7 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Gap** | No OWASP Dependency-Check, Snyk, or similar tool is configured. |
| **Impact** | Known CVEs in transitive dependencies go undetected. |
| **Severity** | **High** |
| **Effort** | **Small** |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| | |
|---|---|
| **Gap** | Almost all controller methods return raw `ResponseEntity` instead of `ResponseEntity<SpecificType>`. Only `user-service/UserController` uses typed responses. |
| **Impact** | OpenAPI/Swagger documentation cannot infer response schemas. Clients must guess response shapes. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 5.2 No API Versioning Strategy

| | |
|---|---|
| **Gap** | Endpoints use `/api/v1/` prefix but there is no versioning strategy documented, no v2 migration path, and no header-based or content-type versioning. |
| **Impact** | Breaking changes to the API have no backward-compatible migration path. |
| **Severity** | **Low** |
| **Effort** | **Small** |

### 5.3 Inconsistent Pagination Response

| | |
|---|---|
| **Gap** | Paginated endpoints return raw `List<T>` instead of a `Page<T>` wrapper with total count, page number, etc. The `Pageable` parameter is accepted but the pagination metadata is discarded. |
| **Impact** | Clients cannot determine total results, current page, or whether more pages exist. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 5.4 No Filtering or Sorting on List Endpoints

| | |
|---|---|
| **Gap** | List endpoints (users, transfers, payments) accept `Pageable` but no filtering parameters (date range, status, account, etc.). |
| **Impact** | Clients must fetch all data and filter client-side, which is inefficient. |
| **Severity** | **Low** |
| **Effort** | **Medium** |

### 5.5 OpenAPI/Swagger Dependency Mismatch

| | |
|---|---|
| **Gap** | Services include `springdoc-openapi-starter-webflux-ui` but are built on Spring MVC (not WebFlux). Only the API Gateway uses WebFlux. The correct dependency for MVC services should be `springdoc-openapi-starter-webmvc-ui`. |
| **Impact** | Swagger UI may not render or function correctly on MVC-based services. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 5.6 Incomplete OpenAPI Annotations

| | |
|---|---|
| **Gap** | Controllers have `@Tag` and `@Operation` annotations but lack `@ApiResponse`, `@Schema`, and `@Parameter` annotations. Request/response models have no Swagger annotations. |
| **Impact** | Generated API documentation is incomplete -- no response codes, schemas, or parameter descriptions. |
| **Severity** | **Low** |
| **Effort** | **Medium** |

---

## 6. Observability

### 6.1 Inconsistent Logging

| | |
|---|---|
| **Gap** | Some controllers use `@Slf4j` and log at `INFO` level; others don't log at all. Service classes log inconsistently (some log requests, some don't). No structured logging (JSON format). No correlation IDs in log messages. |
| **Impact** | Difficult to trace requests across services using logs alone. Log aggregation tools cannot parse unstructured logs. |
| **Severity** | **Medium** |
| **Effort** | **Medium** |

### 6.2 No Custom Health Checks

| | |
|---|---|
| **Gap** | Services include `spring-boot-starter-actuator` but no custom `HealthIndicator` implementations for critical dependencies (MySQL, Keycloak, RabbitMQ, downstream services). |
| **Impact** | The `/actuator/health` endpoint only shows basic UP/DOWN without dependency health. Kubernetes/load-balancer health checks lack depth. |
| **Severity** | **Medium** |
| **Effort** | **Small** |

### 6.3 No Custom Metrics

| | |
|---|---|
| **Gap** | No custom Micrometer metrics (counters, gauges, timers) for business events (transfers completed, payments processed, user registrations). Prometheus is listed in the README tech stack but not configured as a dependency. |
| **Impact** | Cannot monitor business KPIs or detect anomalies via dashboards/alerts. |
| **Severity** | **Medium** |
| **Effort** | **Medium** |

### 6.4 Distributed Tracing Coverage Uncertain

| | |
|---|---|
| **Gap** | Zipkin dependencies are present in all services, but tracing configuration (sampling rate, propagation, custom spans) is externalized to the config server and not visible in the repo. No custom `@Observed` or manual span annotations exist. |
| **Impact** | Tracing may not cover all inter-service hops or internal method calls. |
| **Severity** | **Low** |
| **Effort** | **Small** |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Gap** | No circuit breaker library (Resilience4j, Spring Cloud Circuit Breaker) is present. When `core-banking-service` is down, `fund-transfer-service` and `utility-payment-service` will fail immediately with a Feign connection error and no fallback. |
| **Impact** | Cascading failures; one service outage brings down all dependent services. |
| **Severity** | **Critical** |
| **Effort** | **Medium** |

### 7.2 No Retry Policies

| | |
|---|---|
| **Gap** | No Spring Retry or Resilience4j retry configuration on Feign clients. Transient network issues cause immediate failure. |
| **Impact** | Temporary network blips or container restarts cause unnecessary transaction failures. |
| **Severity** | **High** |
| **Effort** | **Small** |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Gap** | No explicit Feign client timeouts, connection pool settings, or read/write timeouts configured (at least not in the visible codebase; may be in external config). Default Feign timeouts are very long (60s+). |
| **Impact** | Slow downstream services block calling threads indefinitely, leading to thread pool exhaustion. |
| **Severity** | **High** |
| **Effort** | **Small** |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Gap** | Feign clients have no `@FeignClient(fallback = ...)` or `fallbackFactory` configured. No graceful degradation strategy. |
| **Impact** | Any core-banking-service failure propagates directly to the end user with a generic error. |
| **Severity** | **Medium** |
| **Effort** | **Medium** |

### 7.5 No Idempotency on Write Operations

| | |
|---|---|
| **Gap** | Fund transfer and utility payment POST endpoints have no idempotency keys. If a network timeout occurs after the core banking service processes the request but before the response reaches the orchestrator, a retry will create a duplicate transaction. |
| **Impact** | Double-charging customers. Critical for a financial application. |
| **Severity** | **Critical** |
| **Effort** | **Medium** |

### 7.6 Transaction Entity Relationship Bug

| | |
|---|---|
| **Gap** | `TransactionEntity.account` uses `@OneToOne(cascade = CascadeType.ALL)` instead of `@ManyToOne`. This means saving a transaction cascades all changes to the account, and the relationship semantics are wrong (an account has many transactions). |
| **Impact** | Potential data corruption; unexpected cascade deletions or updates. |
| **Severity** | **Critical** |
| **Effort** | **Small** |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 2.1 | Generic exception returns 400 + stack trace | Error Handling | **Critical** | Small |
| 2.4 | No input validation on request bodies | Error Handling | **Critical** | Small |
| 3.1 | Tests only in core-banking-service | Testing | **Critical** | Large |
| 4.3 | No input validation (security) | Security | **Critical** | Small |
| 4.4 | Stack trace exposure | Security | **Critical** | Small |
| 7.1 | No circuit breakers | Resilience | **Critical** | Medium |
| 7.5 | No idempotency on write operations | Resilience | **Critical** | Medium |
| 7.6 | Transaction @OneToOne bug | Resilience | **Critical** | Small |
| 1.2 | Duplicated code across services | Code Org | **High** | Medium |
| 2.2 | Missing HTTP status code differentiation | Error Handling | **High** | Small |
| 2.3 | No Feign error decoding in most services | Error Handling | **High** | Medium |
| 3.2 | No integration tests | Testing | **High** | Large |
| 4.1 | Hardcoded credentials in Docker Compose | Security | **High** | Small |
| 4.7 | No dependency vulnerability scanning | Security | **High** | Small |
| 7.2 | No retry policies | Resilience | **High** | Small |
| 7.3 | No timeout configuration | Resilience | **High** | Small |
| 1.1 | No multi-project Gradle build | Code Org | **Medium** | Medium |
| 2.5 | Inconsistent error response format | Error Handling | **Medium** | Small |
| 3.3 | No contract tests | Testing | **Medium** | Large |
| 4.2 | CSRF disabled, no CORS | Security | **Medium** | Small |
| 4.5 | No rate limiting | Security | **Medium** | Medium |
| 4.6 | Keycloak singleton not thread-safe | Security | **Medium** | Small |
| 5.1 | Raw ResponseEntity without types | API Design | **Medium** | Small |
| 5.3 | Pagination metadata discarded | API Design | **Medium** | Small |
| 5.5 | OpenAPI webflux dependency in MVC services | API Design | **Medium** | Small |
| 6.1 | Inconsistent logging | Observability | **Medium** | Medium |
| 6.2 | No custom health checks | Observability | **Medium** | Small |
| 6.3 | No custom metrics | Observability | **Medium** | Medium |
| 7.4 | No fallback behavior | Resilience | **Medium** | Medium |
| 1.3 | Inconsistent package structure | Code Org | **Low** | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Org | **Low** | Small |
| 3.4 | No test coverage reporting | Testing | **Low** | Small |
| 5.2 | No API versioning strategy | API Design | **Low** | Small |
| 5.4 | No filtering/sorting on list endpoints | API Design | **Low** | Medium |
| 5.6 | Incomplete OpenAPI annotations | API Design | **Low** | Medium |
| 6.4 | Distributed tracing coverage uncertain | Observability | **Low** | Small |
