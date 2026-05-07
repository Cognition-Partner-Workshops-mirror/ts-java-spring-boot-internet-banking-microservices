# Engineering Standards Gap Analysis

This document compares the current codebase against industry engineering best practices and documents gaps with severity ratings and remediation effort estimates.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Gap** | Each microservice is an independent Gradle project with its own `settings.gradle`, `build.gradle`, and Gradle wrapper. There is no root-level build file to orchestrate builds, enforce consistent dependency versions, or share common configuration. |
| **Impact** | Dependency version drift across services, duplicated build configuration, inability to run all tests or builds from a single command. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Gap** | Several classes are copy-pasted across services with no shared library: `AuditAware` (identical in user-service, fund-transfer-service, utility-payment-service), `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` (duplicated in user-service and fund-transfer-service), `BaseMapper` (duplicated in all services), `ErrorResponse` / `SimpleBankingGlobalException` / `GlobalExceptionHandler` (duplicated with slight variations in all services), `CustomFeignClientConfiguration` (duplicated across fund-transfer and utility-payment services). |
| **Impact** | Bug fixes must be applied in multiple places; inconsistencies creep in over time (e.g., `ErrorResponse` in fund-transfer uses constructor while others use builder). |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Gap** | Core-banking-service uses `repository` package at root level while other services nest it under `model.repository`. User-service has `model.rest.response` for Feign response DTOs while fund-transfer-service places them under `model.dto.response`. Utility-payment-service uses `model.rest.request/response` while fund-transfer uses `model.dto.request/response`. |
| **Impact** | Confusing for developers; harder to navigate and maintain consistency when onboarding. |
| **Severity** | Low |
| **Effort** | Medium |

### 1.4 Mapper Instantiation Anti-Pattern

| | |
|---|---|
| **Gap** | Mappers (`UserMapper`, `BankAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` directly in service classes rather than being Spring beans or using a mapping framework like MapStruct. |
| **Impact** | Cannot be mocked independently in tests; bypasses Spring lifecycle; no compile-time mapping validation. |
| **Severity** | Low |
| **Effort** | Medium |

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| | |
|---|---|
| **Gap** | The `GlobalExceptionHandler` in every service has a catch-all `@ExceptionHandler(Exception.class)` that returns a plain string (`"Exception occur inside API " + e`) instead of the structured `ErrorResponse`. This means clients receive different response shapes depending on the exception type. |
| **Impact** | API consumers cannot reliably parse error responses; stack traces may leak in responses. |
| **Severity** | High |
| **Effort** | Small |

### 2.2 All Errors Return HTTP 400

| | |
|---|---|
| **Gap** | Both exception handlers unconditionally return `ResponseEntity.badRequest()` (HTTP 400). Entity-not-found errors should return 404, insufficient funds should return 422, authentication errors should return 401/403, and unexpected errors should return 500. |
| **Impact** | Violates HTTP semantics; clients cannot distinguish between input errors, missing resources, and server failures. |
| **Severity** | High |
| **Effort** | Small |

### 2.3 No Raw-Type Parameterization on ResponseEntity

| | |
|---|---|
| **Gap** | All controller methods return raw `ResponseEntity` instead of `ResponseEntity<T>` (e.g., `ResponseEntity<BankAccount>`). |
| **Impact** | No compile-time type safety; OpenAPI documentation cannot infer response types automatically. |
| **Severity** | Medium |
| **Effort** | Small |

### 2.4 Exception Leaks Sensitive Information

| | |
|---|---|
| **Gap** | The catch-all handler concatenates the full exception object into the response: `"Exception occur inside API " + e`. This can expose stack traces, class names, and internal details to external callers. |
| **Impact** | Security risk; information disclosure. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.5 No Error Handling for Feign Failures in Fund Transfer / Utility Payment

| | |
|---|---|
| **Gap** | `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` do not catch Feign exceptions. If the core-banking-service call fails, the local entity remains in `PENDING`/`PROCESSING` state but no rollback or `FAILED` status is set. Only the user-service has a `CustomFeignErrorDecoder`. The utility-payment-service's `CustomFeignClientConfiguration` is empty (no error decoder). |
| **Impact** | Inconsistent transaction states; orphaned records; no meaningful error propagation. |
| **Severity** | Critical |
| **Effort** | Medium |

---

## 3. Testing

### 3.1 Minimal Test Coverage

| | |
|---|---|
| **Gap** | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services have only auto-generated Spring Boot context-load tests (`*ApplicationTests`) that are empty or fail without infrastructure. |
| **Impact** | No test safety net for 4 out of 6 services; regressions go undetected. |
| **Severity** | Critical |
| **Effort** | Large |

### 3.2 No Integration Tests

| | |
|---|---|
| **Gap** | No integration tests exist for any service. No tests verify Feign client communication, database queries, or controller endpoint behavior with a running Spring context. |
| **Impact** | Serialization mismatches, query bugs, and routing issues are only caught in production. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests

| | |
|---|---|
| **Gap** | No consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) between services. The Feign client interfaces and DTO shapes are manually kept in sync with core-banking-service endpoints. |
| **Impact** | A change in core-banking-service's API can silently break downstream consumers. |
| **Severity** | High |
| **Effort** | Large |

### 3.4 ApplicationTests Require Infrastructure

| | |
|---|---|
| **Gap** | The `*ApplicationTests` classes attempt to load the full Spring context, which requires Eureka, Config Server, MySQL, and Keycloak to be running. They will fail in any CI environment without these dependencies. |
| **Impact** | Tests are effectively broken in CI; false negatives. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 4. Security

### 4.1 No Input Validation

| | |
|---|---|
| **Gap** | No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Bean Validation annotations on request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`). No `@Validated` on controllers. |
| **Impact** | Null/empty account numbers, negative amounts, and malformed emails pass through to business logic; potential for data corruption. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.2 Hardcoded Credentials in Docker Compose

| | |
|---|---|
| **Gap** | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), and PostgreSQL password (`password`) are hardcoded in `docker-compose.yml`, `docker-compose-support-apps.yml`, and the MySQL Dockerfile. |
| **Impact** | Credentials committed to source control; secrets visible to anyone with repo access. |
| **Severity** | High |
| **Effort** | Small |

### 4.3 Keycloak Client Secret in Code

| | |
|---|---|
| **Gap** | `KeycloakProperties` reads `app.config.keycloak.client-secret` from externalized config, but the config values are stored in a public Git repository (the config server's backing repo). |
| **Impact** | Secret exposure in a public repo. |
| **Severity** | High |
| **Effort** | Small |

### 4.4 Static Keycloak Instance (Not Thread-Safe)

| | |
|---|---|
| **Gap** | `KeycloakProperties.getInstance()` uses a static field with manual null-check (not `synchronized`). This is a classic double-checked locking bug without `volatile`. |
| **Impact** | Race condition during initialization; potential for multiple Keycloak instances or partially initialized state. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.5 No CORS Configuration

| | |
|---|---|
| **Gap** | The API Gateway has no explicit CORS configuration. If a frontend SPA were to consume the API, cross-origin requests would be blocked. |
| **Impact** | Would block any browser-based frontend integration. |
| **Severity** | Low |
| **Effort** | Small |

### 4.6 CSRF Disabled Without Documentation

| | |
|---|---|
| **Gap** | CSRF is disabled in the gateway's `SecurityConfiguration` (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`) without any comments explaining the justification. For stateless JWT-based APIs this is acceptable, but should be explicitly documented. |
| **Impact** | Minor; acceptable for API-only services but should be documented. |
| **Severity** | Low |
| **Effort** | Small |

### 4.7 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Gap** | No OWASP Dependency-Check, Snyk, or Dependabot configuration. No Gradle security scanning plugins. |
| **Impact** | Vulnerable transitive dependencies go undetected. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 5. API Design

### 5.1 No API Versioning Strategy

| | |
|---|---|
| **Gap** | While endpoints use `/api/v1/` prefix, there is no documented versioning strategy or mechanism for supporting multiple versions simultaneously. No content negotiation or header-based versioning. |
| **Impact** | Breaking changes would require coordinated client updates; no migration path. |
| **Severity** | Low |
| **Effort** | Small |

### 5.2 Incomplete OpenAPI Documentation

| | |
|---|---|
| **Gap** | SpringDoc OpenAPI is included as a dependency, and basic `@Tag` and `@Operation` annotations have been added. However, response schemas are not documented (raw `ResponseEntity` return types prevent auto-detection), error responses are not documented (`@ApiResponse`), and request body schemas lack validation constraints. The OpenAPI dependency used (`springdoc-openapi-starter-webflux-ui`) is the WebFlux variant, but the business services are servlet-based (spring-boot-starter-web). |
| **Impact** | Swagger UI may not work correctly; generated client SDKs will have incomplete types. |
| **Severity** | Medium |
| **Effort** | Medium |

### 5.3 No Pagination Metadata in Responses

| | |
|---|---|
| **Gap** | List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with total count, page number, and page size. The `readUsers()`, `readAllTransfers()`, and `readPayments()` methods extract `.getContent()` from the `Page` and discard pagination metadata. |
| **Impact** | Clients cannot implement pagination UI; no way to know total count or if more pages exist. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.4 No Filtering or Sorting Support

| | |
|---|---|
| **Gap** | List endpoints offer no query parameter filtering (e.g., by status, date range, account number) or explicit sort support beyond what Spring Data's `Pageable` provides by default. |
| **Impact** | Clients must fetch all records and filter client-side; poor performance at scale. |
| **Severity** | Low |
| **Effort** | Medium |

### 5.5 Inconsistent URL Naming Conventions

| | |
|---|---|
| **Gap** | Mixed naming: `bank-account` (kebab-case) vs `util-account` (abbreviated), `fund-transfer` vs `utility-payment`, `bank-users` vs `user`. No consistent pluralization strategy. |
| **Impact** | Confusing API surface for consumers; documentation inconsistencies. |
| **Severity** | Low |
| **Effort** | Small |

---

## 6. Observability

### 6.1 Inconsistent Logging

| | |
|---|---|
| **Gap** | Some controllers use `@Slf4j` and log incoming requests; others do not. No structured logging format (JSON). Log messages are inconsistent (e.g., `"Reading account by ID {}"` vs `"Got fund transfer request from API {}"`). Some log statements use string concatenation instead of parameterized logging (`"Sending fund transfer request {}" + request.toString()`). |
| **Impact** | Difficult to search/filter logs; performance overhead from string concatenation in log statements. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.2 No Health Check Customization

| | |
|---|---|
| **Gap** | While `spring-boot-starter-actuator` is included, there are no custom health indicators to verify critical dependencies (database connectivity, Keycloak availability, Feign client targets). The default `/actuator/health` only reports basic status. |
| **Impact** | Health checks may report UP even when critical dependencies are down. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Custom Metrics

| | |
|---|---|
| **Gap** | No custom Micrometer metrics for business operations (e.g., fund transfer count/duration, payment success/failure rates, user registration counts). Only default Spring Boot metrics are available. |
| **Impact** | No business-level dashboards or alerting; limited operational visibility. |
| **Severity** | Medium |
| **Effort** | Medium |

### 6.4 No Prometheus Endpoint Configuration

| | |
|---|---|
| **Gap** | Prometheus is listed in the technology stack but no `micrometer-registry-prometheus` dependency is present in any `build.gradle`. The Prometheus scrape endpoint (`/actuator/prometheus`) is not available. |
| **Impact** | Cannot integrate with Prometheus/Grafana monitoring stack as documented. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.5 Distributed Tracing Incomplete

| | |
|---|---|
| **Gap** | While Zipkin dependencies are present, the `internet-banking-config-server` and `internet-banking-service-registry` do not include tracing dependencies. No sampling configuration is visible (defaults to 10% sampling). No trace ID in log output (MDC not configured). |
| **Impact** | Cannot trace requests end-to-end through config/registry services; low default sampling misses most traces. |
| **Severity** | Low |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Gap** | No circuit breaker implementation (Resilience4j, Hystrix). Feign clients call core-banking-service synchronously with no fallback. If core-banking-service is down, all dependent services will block and eventually timeout. |
| **Impact** | Cascading failures; one service failure brings down the entire system. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.2 No Retry Policies

| | |
|---|---|
| **Gap** | No retry configuration on Feign clients or RestTemplate calls. Transient network errors cause immediate failure. |
| **Impact** | Reduced availability; transient errors propagate as permanent failures. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Gap** | No explicit connection or read timeouts configured on Feign clients. Defaults (typically infinite or very long) mean a slow core-banking-service response can hold threads indefinitely. |
| **Impact** | Thread pool exhaustion; cascading slowdown. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Gap** | No fallback methods defined for Feign clients. When core-banking-service is unavailable, users get raw exceptions. |
| **Impact** | Poor user experience; no graceful degradation. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 No Idempotency Protection

| | |
|---|---|
| **Gap** | Fund transfer and utility payment endpoints have no idempotency keys. A retry (network timeout, client retry) could result in duplicate transactions. |
| **Impact** | Double-charging risk; data integrity issues in a financial application. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.6 Non-Atomic Transaction Processing

| | |
|---|---|
| **Gap** | Fund transfers debit the source account and credit the destination in separate repository save calls. If the process crashes between the debit and credit, funds are lost. The `@Transactional` annotation is present on `TransactionService` but the Feign-orchestrated flow spans two services without saga or 2PC coordination. |
| **Impact** | Funds can be debited without being credited in failure scenarios; money "disappears." |
| **Severity** | Critical |
| **Effort** | Large |

### 7.7 Dangerous JPA Relationship on TransactionEntity

| | |
|---|---|
| **Gap** | `TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)` to `BankAccountEntity`. This is wrong in two ways: (1) **`@OneToOne` is semantically incorrect** — a single account has many transactions (a fund transfer creates two `TransactionEntity` records referencing the same accounts), so this should be `@ManyToOne`. (2) **`CascadeType.ALL` includes `REMOVE`** — deleting a transaction record would cascade-delete the associated `BankAccountEntity`. Since `UserEntity.accounts` also uses `CascadeType.ALL`, this can chain-delete all of a user's accounts and their transactions. One accidental transaction deletion could wipe out a customer's entire banking data. |
| **Impact** | Incorrect cardinality constraint; catastrophic cascade-delete risk on financial records. |
| **Severity** | Critical |
| **Effort** | Small |

### 7.8 Balance Calculation Bug

| | |
|---|---|
| **Gap** | In `TransactionService.internalFundTransfer()` and `utilPayment()`, the `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been reduced by `amount`. This double-subtracts, making `availableBalance` incorrect. |
| **Impact** | Account balances are wrong after every transaction. |
| **Severity** | Critical |
| **Effort** | Small |

---

## Summary Matrix

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 2 | 2 | 1 | 0 | 5 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 1 | 2 | 2 | 2 | 7 |
| API Design | 0 | 0 | 2 | 3 | 5 |
| Observability | 0 | 0 | 4 | 1 | 5 |
| Resilience | 5 | 2 | 1 | 0 | 8 |
| **Total** | **9** | **9** | **12** | **8** | **38** |
