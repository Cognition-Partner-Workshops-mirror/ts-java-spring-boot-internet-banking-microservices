# Internet Banking Microservices — Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven key categories. Each gap is rated by **Severity** (Critical/High/Medium/Low) and **Effort** to remediate (Small/Medium/Large).

---

## 1. Code Organization

### GAP-1.1: No Shared Library — Duplicated Code Across Services

**Description:** `BaseMapper`, `AuditAware`, `AuditorAwareConfig`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, and `CustomFeignClientConfiguration` are copy-pasted across 3–4 services with no shared module.

**Impact:** Bug fixes and improvements must be applied in multiple places; drift between copies is likely.

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### GAP-1.2: No Root Build File — Independent Gradle Builds

**Description:** Each service has its own `build.gradle` and `gradlew` with no parent project. There is no Gradle composite build or multi-module setup to coordinate versions, plugins, or dependencies.

**Impact:** Version drift between services; no single command to build/test the entire project.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### GAP-1.3: Inconsistent Package Structure

**Description:** Services follow slightly different package conventions:
- Core-banking: `model.mapper`, `model.entity`, `model.dto`, `repository`
- Fund-transfer: `model.mapper`, `model.entity`, `model.dto`, `model.repository`
- User-service: `model.mapper`, `model.entity`, `model.dto`, `model.repository`, `model.rest.response`
- Utility-payment: `model.mapper`, `model.entity`, `model.rest.request`, `model.rest.response`, `repository`

**Impact:** Harder for developers to navigate between services; no clear convention to follow.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

## 2. Error Handling

### GAP-2.1: All Errors Return HTTP 400 Bad Request

**Description:** `GlobalExceptionHandler` maps ALL exceptions (including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception`) to `ResponseEntity.badRequest()`. There is no differentiation between 404 (Not Found), 409 (Conflict), 422 (Unprocessable), or 500 (Internal Server Error).

**Impact:** Clients cannot distinguish between user errors and server failures; makes debugging and monitoring extremely difficult.

| Severity | Effort |
|----------|--------|
| Critical | Small |

---

### GAP-2.2: Generic Exception Handler Leaks Stack Traces

**Description:** The catch-all `@ExceptionHandler({Exception.class})` returns the raw exception object as a string (`"Exception occur inside API " + e`), which exposes internal implementation details and potential security-sensitive information.

**Impact:** Information disclosure vulnerability; unhelpful error messages for clients.

| Severity | Effort |
|----------|--------|
| High | Small |

---

### GAP-2.3: No Validation on Request Bodies

**Description:** No `@Valid` annotation or Jakarta Bean Validation constraints exist on any `@RequestBody` parameter. Users can submit empty or malformed requests that will fail at the database level with unhelpful errors.

**Impact:** Poor user experience; unexpected database errors; potential data corruption.

| Severity | Effort |
|----------|--------|
| High | Small |

---

## 3. Testing

### GAP-3.1: Minimal Unit Test Coverage

**Description:** Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have only empty `ApplicationTests` classes that simply load the Spring context.

**Impact:** Regressions will not be caught; refactoring is risky.

| Severity | Effort |
|----------|--------|
| High | Large |

---

### GAP-3.2: No Integration Tests

**Description:** No integration tests exist (e.g., `@SpringBootTest` with embedded containers, Testcontainers, or WireMock for Feign clients). The system can only be tested end-to-end via Docker Compose.

**Impact:** Cannot verify service interactions without full stack deployment.

| Severity | Effort |
|----------|--------|
| High | Large |

---

### GAP-3.3: No Contract Tests Between Services

**Description:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between the Feign clients and their providers.

**Impact:** Breaking API changes in core-banking-service will not be detected until runtime.

| Severity | Effort |
|----------|--------|
| Medium | Large |

---

## 4. Security

### GAP-4.1: No Authentication on Downstream Services

**Description:** JWT validation occurs only at the API Gateway. Downstream services (core-banking, fund-transfer, utility-payment) have no authentication mechanism. They trust the `X-Auth-Id` header, which is trivially spoofable if services are accessed directly (bypassing the gateway).

**Impact:** Any network-accessible path to downstream services bypasses all authentication.

| Severity | Effort |
|----------|--------|
| Critical | Medium |

---

### GAP-4.2: Hardcoded Credentials in Source Control

**Description:** MySQL root password (`woVERANKliGharym`), database user password (`oPItyPticIAt`), Keycloak admin password (`password`), and PostgreSQL password (`password`) are committed in `docker-compose.yml` and `privileges.sql`.

**Impact:** Credential exposure; secrets visible to anyone with repo access.

| Severity | Effort |
|----------|--------|
| High | Small |

---

### GAP-4.3: Keycloak Client Secret in Configuration

**Description:** The Keycloak client secret is provided via Spring Cloud Config (externalized), but the Config Server's Git repo is public. Anyone can read the client secret.

**Impact:** Potential unauthorized access to the Keycloak admin API.

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### GAP-4.4: No Input Sanitization or Validation

**Description:** No `@Valid`, `@NotNull`, `@Size`, `@Positive` or similar constraints on any DTO. No sanitization of user-provided strings before persistence or logging.

**Impact:** Potential for SQL injection (mitigated by JPA), log injection, and processing invalid data.

| Severity | Effort |
|----------|--------|
| High | Small |

---

### GAP-4.5: Singleton Keycloak Client — Thread Safety Issue

**Description:** `KeycloakProperties` uses a static `keycloakInstance` field initialized via lazy singleton pattern without synchronization (`if (keycloakInstance == null)`). This is not thread-safe.

**Impact:** Potential race condition during application startup under load.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

## 5. API Design

### GAP-5.1: Wrong OpenAPI Dependency

**Description:** All MVC-based services (core-banking, fund-transfer, user, utility-payment) use `springdoc-openapi-starter-webflux-ui` instead of `springdoc-openapi-starter-webmvc-ui`. The webflux variant is for reactive (WebFlux) applications; these are servlet (Spring MVC) applications.

**Impact:** OpenAPI/Swagger UI may not generate correct documentation or may fail to render.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-5.2: No Pagination Metadata in Responses

**Description:** Endpoints that accept `Pageable` parameters return raw `List<T>` instead of a page wrapper with total count, page size, page number, and total pages.

**Impact:** Clients cannot implement proper pagination UI; no way to know total results.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-5.3: No API Versioning Strategy

**Description:** While endpoints use `/api/v1/` prefix, there is no documented versioning strategy, no mechanism for v2, and no content-type negotiation.

**Impact:** Minor — current state is acceptable for initial development, but will need attention as the API evolves.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

### GAP-5.4: Raw `ResponseEntity` Without Type Parameters

**Description:** Most controller methods return `ResponseEntity` without generic type parameter (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This prevents proper OpenAPI type generation.

**Impact:** Generated API documentation lacks response schema information.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-5.5: No Idempotency for Write Operations

**Description:** Fund transfer and utility payment POST endpoints have no idempotency key mechanism. Duplicate submissions will create duplicate transactions.

**Impact:** Financial risk — duplicate debits possible on network retries.

| Severity | Effort |
|----------|--------|
| Critical | Medium |

---

## 6. Observability

### GAP-6.1: No Structured Logging

**Description:** Services use basic SLF4J logging with default logback pattern. No JSON structured logging format for machine parsing. Log messages are inconsistent across services.

**Impact:** Difficult to aggregate and search logs in production monitoring tools.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-6.2: No Health Check Configuration

**Description:** While Spring Boot Actuator is included as a dependency, there is no explicit configuration of health indicators, liveness/readiness probes, or health group endpoints beyond defaults.

**Impact:** Container orchestrators (K8s) cannot properly determine service health for restart/routing decisions.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-6.3: No Custom Metrics

**Description:** No custom Micrometer metrics (counters, timers, gauges) are defined for business operations (e.g., transfer count, payment amounts, error rates).

**Impact:** No business-level observability; only infrastructure metrics available.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### GAP-6.4: Sensitive Data in Log Messages

**Description:** Several service methods log full request objects via `toString()` (e.g., `log.info("Creating user with {}", request.toString())`). These may contain passwords or sensitive financial data.

**Impact:** Sensitive data exposed in log files.

| Severity | Effort |
|----------|--------|
| High | Small |

---

## 7. Resilience

### GAP-7.1: No Circuit Breakers

**Description:** Feign clients have no circuit breaker configuration (e.g., Resilience4j). If core-banking-service is down, all upstream services will block on connection timeouts.

**Impact:** Cascading failures across the entire system when one service is unavailable.

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### GAP-7.2: No Retry Policies

**Description:** No retry configuration on Feign clients or RestTemplate calls. Transient network errors will immediately fail the request.

**Impact:** Unnecessary failures for recoverable transient errors.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### GAP-7.3: No Timeout Configuration

**Description:** Feign clients use default timeouts (no explicit `connectTimeout` or `readTimeout` configured). Default timeouts may be too long, tying up threads during outages.

**Impact:** Thread pool exhaustion during downstream outages.

| Severity | Effort |
|----------|--------|
| High | Small |

---

### GAP-7.4: No Fallback Behavior

**Description:** No fallback methods defined for Feign client failures. Users receive raw Feign exception responses.

**Impact:** Poor user experience during partial outages; no graceful degradation.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### GAP-7.5: No Transaction Compensation/Saga Pattern

**Description:** Fund transfer service saves a `PENDING` record, then calls core-banking. If the Feign call succeeds but the subsequent database update fails, the local record remains PENDING while money has been moved. No compensation logic exists.

**Impact:** Data inconsistency between services; potential "lost" transactions.

| Severity | Effort |
|----------|--------|
| Critical | Large |

---

### GAP-7.6: Balance Calculation Bug

**Description:** In `TransactionService.internalFundTransfer()`, available balance is set to `actualBalance.subtract(amount)` AFTER actualBalance was already reduced. This double-subtracts from availableBalance. Similarly, the credit side sets `availableBalance = actualBalance.add(amount)` AFTER actualBalance was already increased, resulting in double-add.

**Impact:** Incorrect balance display to users; potential overdraft or inflated balance.

| Severity | Effort |
|----------|--------|
| Critical | Small |

### GAP-7.7: Race Condition — No Pessimistic Locking on Balance Updates

**Description:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, the balance is read via `accountService.readBankAccount()`, validated in application code, and then updated in a separate database call. Between the read and the write, another concurrent request can read the same stale balance and also pass validation. Both transactions proceed, resulting in an overdraft. There is no `SELECT ... FOR UPDATE` (pessimistic lock) or optimistic locking (`@Version` column) on `BankAccountEntity` to prevent this race condition. In a banking system handling concurrent transfers from the same account, this is a critical financial safety issue.

**Impact:** Two concurrent transfers from the same account can both succeed even when only one should, allowing the account balance to go negative (overdraft).

| Severity | Effort |
|----------|--------|
| Critical | Small |

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-1.1 | Code Organization | Duplicated code across services (no shared library) | High | Medium |
| GAP-1.2 | Code Organization | No root build file / independent Gradle builds | Medium | Medium |
| GAP-1.3 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-2.1 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-2.2 | Error Handling | Generic exception handler leaks stack traces | High | Small |
| GAP-2.3 | Error Handling | No validation on request bodies | High | Small |
| GAP-3.1 | Testing | Minimal unit test coverage (only core-banking) | High | Large |
| GAP-3.2 | Testing | No integration tests | High | Large |
| GAP-3.3 | Testing | No contract tests between services | Medium | Large |
| GAP-4.1 | Security | No authentication on downstream services | Critical | Medium |
| GAP-4.2 | Security | Hardcoded credentials in source control | High | Small |
| GAP-4.3 | Security | Keycloak client secret in public config repo | High | Medium |
| GAP-4.4 | Security | No input validation or sanitization | High | Small |
| GAP-4.5 | Security | Thread-unsafe Keycloak singleton | Medium | Small |
| GAP-5.1 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-5.2 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-5.3 | API Design | No API versioning strategy | Low | Small |
| GAP-5.4 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-5.5 | API Design | No idempotency for write operations | Critical | Medium |
| GAP-6.1 | Observability | No structured logging | Medium | Small |
| GAP-6.2 | Observability | No health check configuration | Medium | Small |
| GAP-6.3 | Observability | No custom business metrics | Medium | Medium |
| GAP-6.4 | Observability | Sensitive data in log messages | High | Small |
| GAP-7.1 | Resilience | No circuit breakers | High | Medium |
| GAP-7.2 | Resilience | No retry policies | Medium | Small |
| GAP-7.3 | Resilience | No timeout configuration | High | Small |
| GAP-7.4 | Resilience | No fallback behavior | Medium | Medium |
| GAP-7.5 | Resilience | No transaction compensation/saga pattern | Critical | Large |
| GAP-7.6 | Resilience | Balance calculation bug (double-subtract/add) | Critical | Small |
| GAP-7.7 | Resilience | Race condition — no pessimistic locking on balance updates | Critical | Small |

### Severity Distribution

- **Critical:** 7 gaps (GAP-2.1, GAP-4.1, GAP-5.5, GAP-7.5, GAP-7.6, GAP-7.7)
- **High:** 12 gaps
- **Medium:** 9 gaps
- **Low:** 2 gaps
