# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry-standard engineering best practices. Each gap is rated by severity and estimated remediation effort.

**Severity scale:** Critical (production risk / security vulnerability) · High (significant quality issue) · Medium (maintainability / reliability concern) · Low (polish / best-practice deviation)

**Effort scale:** Small (< 1 day) · Medium (1–3 days) · Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Gap** | Each microservice is a standalone Gradle project with its own `settings.gradle`, `build.gradle`, and `gradle/wrapper`. There is no root `settings.gradle` or `build.gradle` to unify them. |
| **Impact** | Duplicated build configuration, no shared dependency version catalog, inconsistent plugin versions across services, no single command to build/test all services. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Massive Code Duplication Across Services

| | |
|---|---|
| **Gap** | The following classes are copy-pasted across 3+ services with near-identical implementations: `AuditAware`, `BaseMapper`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `CustomFeignClientConfiguration`. |
| **Impact** | Bug fixes and improvements must be applied in multiple places. Divergence over time leads to inconsistent behavior. |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 No Shared Library / Common Module

| | |
|---|---|
| **Gap** | No `common` or `shared` module exists for cross-cutting concerns (DTOs, exceptions, audit, filters, Feign config). Each service re-implements these independently. |
| **Impact** | Violates DRY principle, increases maintenance burden, and makes it easy for services to drift apart. |
| **Severity** | High |
| **Effort** | Medium |

### 1.4 Inconsistent Package Structure

| | |
|---|---|
| **Gap** | Package naming varies: core-banking uses `repository` while user-service uses `model.repository`. Fund-transfer uses `service.rest.client` while utility-payment uses `service.rest`. Exception packages differ between services. |
| **Impact** | Harder for developers to navigate across services; no convention to follow for new services. |
| **Severity** | Low |
| **Effort** | Small |

### 1.5 Mappers Instantiated Inline Instead of Injected

| | |
|---|---|
| **Gap** | All mapper classes (e.g., `UserMapper`, `FundTransferMapper`, `BankAccountMapper`) are instantiated with `new` directly in service classes rather than being Spring-managed beans. |
| **Impact** | Cannot be mocked in tests, not extensible, not consistent with the rest of the Spring DI pattern used throughout. |
| **Severity** | Low |
| **Effort** | Small |

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns Raw Exception String

| | |
|---|---|
| **Gap** | `GlobalExceptionHandler.handleException(Exception e)` returns `"Exception occur inside API " + e` as the response body — a raw string with the full exception stack trace. Present in all services that have a `GlobalExceptionHandler`. |
| **Impact** | **Information disclosure** — stack traces, class names, and internal details leak to clients. Also returns `400 Bad Request` for all unhandled exceptions regardless of actual cause. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.2 All Errors Return 400 Bad Request

| | |
|---|---|
| **Gap** | Both `handleGlobalException` and `handleException` in every `GlobalExceptionHandler` return `ResponseEntity.badRequest()` (HTTP 400). There is no differentiation for 404 (Not Found), 409 (Conflict), 500 (Internal Server Error), etc. |
| **Impact** | Clients cannot distinguish between validation errors, not-found errors, and server errors. Violates HTTP semantics. |
| **Severity** | High |
| **Effort** | Small |

### 2.3 No Validation on Request Bodies

| | |
|---|---|
| **Gap** | No `@Valid` / `@NotNull` / `@NotBlank` / `@Positive` annotations on any request DTO or controller parameter across any service. No Bean Validation (`jakarta.validation`) dependency. |
| **Impact** | Null pointer exceptions on malformed input. No user-friendly validation error messages. |
| **Severity** | High |
| **Effort** | Small |

### 2.4 Feign Error Handling Is Minimal

| | |
|---|---|
| **Gap** | `CustomFeignErrorDecoder` exists in fund-transfer and utility-payment services but simply throws a generic `SimpleBankingGlobalException` for all error responses. No status-code-specific handling. |
| **Impact** | Upstream errors (e.g., 404 from core banking) are all converted to the same exception, losing context. |
| **Severity** | Medium |
| **Effort** | Small |

### 2.5 Inconsistent Error Response Format

| | |
|---|---|
| **Gap** | Core banking's `GlobalExceptionHandler` returns `ErrorResponse { code, message }` for known exceptions but a raw string for unknown exceptions. The format is not documented or enforced via a shared contract. |
| **Impact** | API consumers cannot reliably parse error responses. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 3. Testing

### 3.1 Tests Only in Core Banking Service

| | |
|---|---|
| **Gap** | Only `core-banking-service` has unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other three business services (`user-service`, `fund-transfer-service`, `utility-payment-service`) have only empty `ApplicationTests` context-load stubs. |
| **Impact** | No automated verification of business logic in orchestration services. Regressions can be introduced silently. |
| **Severity** | High |
| **Effort** | Medium |

### 3.2 No Integration Tests

| | |
|---|---|
| **Gap** | No `@SpringBootTest` integration tests, no Testcontainers for MySQL, no WireMock for Feign clients. |
| **Impact** | Cannot verify that services start correctly, database operations work end-to-end, or Feign wiring is correct. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests Between Services

| | |
|---|---|
| **Gap** | No Spring Cloud Contract or Pact tests to verify Feign client interfaces match the provider's actual API. |
| **Impact** | API changes in core-banking-service can silently break all downstream consumers. |
| **Severity** | Medium |
| **Effort** | Large |

### 3.4 No Test Coverage Reporting

| | |
|---|---|
| **Gap** | No JaCoCo or similar coverage plugin configured. No coverage thresholds enforced. |
| **Impact** | No visibility into what is tested and what is not. Cannot enforce coverage standards. |
| **Severity** | Low |
| **Effort** | Small |

### 3.5 No CI Pipeline to Run Tests

| | |
|---|---|
| **Gap** | GitHub Actions workflows were removed. No CI configuration exists in the repository. |
| **Impact** | Tests, if they exist, are never automatically run on PRs or merges. |
| **Severity** | High |
| **Effort** | Medium |

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose and Source

| | |
|---|---|
| **Gap** | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`), and test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`) are all hardcoded in `docker-compose.yml`, `Dockerfile`, and `README.md`. |
| **Impact** | Credentials committed to source control. If these are used in any non-local environment, it is a direct security breach. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.2 CSRF Disabled Without Documentation

| | |
|---|---|
| **Gap** | `SecurityConfiguration` in the API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. No comment or documentation explains why. |
| **Impact** | CSRF protection is a default for a reason. While it may be acceptable for a pure API (no browser sessions), the decision should be documented. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.3 No Input Validation (see also 2.3)

| | |
|---|---|
| **Gap** | No `@Valid`, `@Size`, `@Pattern`, or custom validators on any request body. Financial amounts (`BigDecimal`) are not checked for null, negative, or zero values. |
| **Impact** | Potential for negative fund transfers, zero-amount transactions, or null pointer exceptions in financial operations. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.4 Downstream Services Have No Authentication

| | |
|---|---|
| **Gap** | Only the API Gateway enforces JWT authentication. The individual services (user, fund-transfer, utility-payment, core-banking) do not validate tokens or restrict access if called directly (bypassing the gateway). |
| **Impact** | If any service port is exposed (e.g., in a misconfigured network or Kubernetes cluster), unauthenticated access is possible. |
| **Severity** | High |
| **Effort** | Medium |

### 4.5 Keycloak Singleton Is Not Thread-Safe

| | |
|---|---|
| **Gap** | `KeycloakProperties.getInstance()` uses a classic double-check-less singleton pattern (`if (keycloakInstance == null)`) without synchronization or `volatile`. |
| **Impact** | Under concurrent requests, multiple Keycloak client instances could be created, or a partially constructed instance could be returned. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.6 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Gap** | No OWASP Dependency-Check, Snyk, or Dependabot configured. Some dependencies may have known CVEs. |
| **Impact** | Vulnerable dependencies can be exploited. No automated alerting for new vulnerabilities. |
| **Severity** | High |
| **Effort** | Small |

### 4.7 Sensitive Data Logged in Plain Text

| | |
|---|---|
| **Gap** | Controllers log full request objects: `log.info("Creating user with {}", request.toString())` — this includes passwords in the user registration flow. Fund transfer amounts and account numbers are also logged. |
| **Impact** | Passwords and financial data appear in log files in plain text. |
| **Severity** | Critical |
| **Effort** | Small |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| | |
|---|---|
| **Gap** | Most controller methods return `ResponseEntity` without generics (e.g., `public ResponseEntity getBankAccount(...)` instead of `ResponseEntity<BankAccount>`). |
| **Impact** | OpenAPI/Swagger cannot infer response schemas. API documentation is incomplete. Type safety is lost. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.2 No API Versioning Strategy

| | |
|---|---|
| **Gap** | While paths include `/v1/`, there is no documented versioning strategy, no mechanism for running v1 and v2 in parallel, and no content-type-based or header-based versioning. |
| **Impact** | Breaking changes will be difficult to roll out without disrupting clients. |
| **Severity** | Low |
| **Effort** | Small |

### 5.3 Inconsistent URL Naming Conventions

| | |
|---|---|
| **Gap** | Core banking uses `bank-account`, `util-account`, `fund-transfer`, `util-payment`. User service uses `bank-users`. Fund transfer service uses `transfer`. Utility payment service uses `utility-payment`. Mixing abbreviations and full words. |
| **Impact** | Confusing for API consumers; no predictable URL pattern. |
| **Severity** | Low |
| **Effort** | Small |

### 5.4 Pagination Response Does Not Include Metadata

| | |
|---|---|
| **Gap** | Paginated endpoints (e.g., `GET /api/v1/bank-users`) return a raw `List<T>` instead of a page wrapper with `totalElements`, `totalPages`, `page`, `size`. |
| **Impact** | Clients cannot determine total result count or navigate pages. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.5 No Filtering or Sorting Documentation

| | |
|---|---|
| **Gap** | While Spring Data's `Pageable` supports `sort` and `page`/`size` parameters implicitly, these are not documented in OpenAPI annotations or README. |
| **Impact** | Clients must guess which sort fields are available. |
| **Severity** | Low |
| **Effort** | Small |

### 5.6 PATCH Endpoint Uses Wrong Semantics

| | |
|---|---|
| **Gap** | `PATCH /api/v1/bank-users/update/{id}` uses `PATCH` but the URL includes the verb `update`, violating REST conventions. `PATCH` should be at `/api/v1/bank-users/{id}`. |
| **Impact** | Non-RESTful URL design; confusing for API consumers. |
| **Severity** | Low |
| **Effort** | Small |

### 5.7 OpenAPI Dependency Mismatch

| | |
|---|---|
| **Gap** | Services use `springdoc-openapi-starter-webflux-ui:2.1.0` but core-banking, user, fund-transfer, and utility-payment services are all **Spring MVC** (not WebFlux). Only the API Gateway is WebFlux-based. |
| **Impact** | The WebFlux OpenAPI starter may not correctly scan MVC controllers, leading to incomplete or missing Swagger docs. Should use `springdoc-openapi-starter-webmvc-ui` for MVC services. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 6. Observability

### 6.1 No Structured Logging

| | |
|---|---|
| **Gap** | All services use default Spring Boot logging (Logback with console pattern). No JSON/structured logging format configured. No MDC enrichment with trace IDs, user IDs, or request IDs. |
| **Impact** | Logs are not easily parseable by log aggregation tools (ELK, Splunk, CloudWatch). Correlation across services is difficult. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.2 Health Check Endpoints Not Customized

| | |
|---|---|
| **Gap** | Services include `spring-boot-starter-actuator` but there is no configuration to enable detailed health checks (database health, Keycloak connectivity, Eureka status). Default actuator exposure is likely limited. |
| **Impact** | Cannot determine if a service is truly healthy (connected to its dependencies) vs. just running. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Prometheus Metrics Endpoint

| | |
|---|---|
| **Gap** | README mentions Prometheus but no `micrometer-registry-prometheus` dependency exists in any `build.gradle`. The `/actuator/prometheus` endpoint is not available. |
| **Impact** | Cannot scrape metrics for dashboards or alerting. README is misleading. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.4 Incomplete Distributed Tracing Coverage

| | |
|---|---|
| **Gap** | While Micrometer Brave + Zipkin reporter are included, there is no configuration for sampling rate, no custom span annotations, and no verification that trace context propagates through Feign clients and the API Gateway. |
| **Impact** | Tracing may be incomplete or not functioning as expected. |
| **Severity** | Low |
| **Effort** | Small |

### 6.5 No Logging Level Configuration

| | |
|---|---|
| **Gap** | No `logging.level.*` configuration in any application YAML. All services run at default INFO level with no ability to adjust per-package logging without redeployment (no Spring Cloud Config dynamic refresh). |
| **Impact** | Cannot increase debug logging for troubleshooting without restarting services. |
| **Severity** | Low |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Gap** | No Resilience4j or Hystrix dependency. No `@CircuitBreaker` annotations. If core-banking-service is down, all upstream services will block and eventually time out on Feign calls. |
| **Impact** | Cascading failures: one service outage brings down the entire system. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.2 No Retry Policies

| | |
|---|---|
| **Gap** | No `@Retry` annotations, no Feign retry configuration, no Spring Retry dependency. Transient network failures cause immediate error responses. |
| **Impact** | Transient errors (network blips, brief service restarts) are not tolerated. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Gap** | No explicit Feign timeouts, no `connectTimeout` / `readTimeout` settings. Default Feign/OkHttp timeouts apply (which may be very long or infinite). |
| **Impact** | A hung downstream service can cause thread pool exhaustion in the caller, eventually bringing it down too. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Gap** | No Feign fallback classes or factory implementations. No degraded-mode responses when dependencies are unavailable. |
| **Impact** | Users receive raw error responses instead of graceful degradation (e.g., "Service temporarily unavailable, please try again"). |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 No Idempotency on Financial Operations

| | |
|---|---|
| **Gap** | Fund transfer and utility payment endpoints have no idempotency key. If a client retries a request (e.g., due to timeout), the transaction may be executed twice. |
| **Impact** | **Double-charging** — a fund transfer or payment could be processed multiple times. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.6 Transaction / Account Entity Relationship Bug

| | |
|---|---|
| **Gap** | `TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)` but the actual relationship is Many-to-One (many transactions per account). The `CascadeType.ALL` means deleting a transaction would cascade-delete the associated bank account. |
| **Impact** | Data integrity risk. Hibernate may produce incorrect SQL or unexpected cascade behavior. |
| **Severity** | Critical |
| **Effort** | Small |

### 7.7 Available Balance Calculation Bug

| | |
|---|---|
| **Gap** | In `TransactionService.internalFundTransfer()` and `utilPayment()`, the available balance is set as `actualBalance - amount` AFTER `actualBalance` was already reduced by `amount`. This means `availableBalance = actualBalance - 2*amount` effectively. |
| **Impact** | **Incorrect account balances** after every transaction — available balance is doubly decremented. |
| **Severity** | Critical |
| **Effort** | Small |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | Generic catch-all leaks stack traces | Error Handling | **Critical** | Small |
| 4.1 | Hardcoded credentials in source | Security | **Critical** | Small |
| 4.3 | No input validation on financial operations | Security | **Critical** | Small |
| 4.7 | Passwords logged in plain text | Security | **Critical** | Small |
| 7.1 | No circuit breakers | Resilience | **Critical** | Medium |
| 7.5 | No idempotency on financial operations | Resilience | **Critical** | Medium |
| 7.6 | Transaction-Account `@OneToOne` / cascade bug | Resilience | **Critical** | Small |
| 7.7 | Available balance double-decrement bug | Resilience | **Critical** | Small |
| 1.2 | Massive code duplication across services | Code Org | High | Medium |
| 1.3 | No shared library / common module | Code Org | High | Medium |
| 2.2 | All errors return 400 Bad Request | Error Handling | High | Small |
| 2.3 | No validation on request bodies | Error Handling | High | Small |
| 3.1 | Tests only in core-banking-service | Testing | High | Medium |
| 3.2 | No integration tests | Testing | High | Large |
| 3.5 | No CI pipeline | Testing | High | Medium |
| 4.4 | Downstream services have no authentication | Security | High | Medium |
| 4.6 | No dependency vulnerability scanning | Security | High | Small |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 1.1 | No multi-project Gradle build | Code Org | Medium | Medium |
| 2.4 | Feign error handling is minimal | Error Handling | Medium | Small |
| 2.5 | Inconsistent error response format | Error Handling | Medium | Small |
| 3.3 | No contract tests | Testing | Medium | Large |
| 4.2 | CSRF disabled without documentation | Security | Medium | Small |
| 4.5 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without type parameters | API Design | Medium | Small |
| 5.4 | Pagination missing metadata | API Design | Medium | Small |
| 5.7 | OpenAPI dependency mismatch (WebFlux vs MVC) | API Design | Medium | Small |
| 6.1 | No structured logging | Observability | Medium | Small |
| 6.2 | Health checks not customized | Observability | Medium | Small |
| 6.3 | No Prometheus metrics endpoint | Observability | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 1.4 | Inconsistent package structure | Code Org | Low | Small |
| 1.5 | Mappers instantiated inline | Code Org | Low | Small |
| 3.4 | No test coverage reporting | Testing | Low | Small |
| 5.2 | No API versioning strategy | API Design | Low | Small |
| 5.3 | Inconsistent URL naming | API Design | Low | Small |
| 5.5 | No filtering/sorting documentation | API Design | Low | Small |
| 5.6 | PATCH endpoint uses wrong semantics | API Design | Low | Small |
| 6.4 | Incomplete tracing coverage | Observability | Low | Small |
| 6.5 | No logging level configuration | Observability | Low | Small |

**Totals:** 8 Critical · 10 High · 13 Medium · 9 Low
