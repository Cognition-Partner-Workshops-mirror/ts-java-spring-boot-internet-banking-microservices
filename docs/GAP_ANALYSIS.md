# Internet Banking Microservices — Engineering Standards Gap Analysis

## Methodology

Each gap is rated on two dimensions:

- **Severity**: How much risk or technical debt the gap introduces.
  - **Critical** — Security vulnerability or data-loss risk in production.
  - **High** — Significant maintainability, reliability, or correctness issue.
  - **Medium** — Noticeable quality gap that increases development friction.
  - **Low** — Cosmetic or minor improvement opportunity.

- **Effort**: Estimated developer time to remediate.
  - **Small** — ≤ 1 day per service.
  - **Medium** — 1–3 days per service.
  - **Large** — > 3 days per service, or cross-cutting refactor.

---

## 1. Code Organization

### GAP-ORG-01: No shared library for common code — duplicate classes across services

**Severity: High | Effort: Medium**

The following classes are duplicated (with minor variations) across 3–4 services:
- `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`
- `AuditAware`, `BaseMapper`
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`

A `banking-common` module is referenced in the build blueprint but only provides Gradle build infrastructure — it does not contain these shared domain classes. Each service re-implements them independently, leading to drift (e.g., `ErrorResponse` uses `@Builder` in core-banking but a constructor in fund-transfer).

### GAP-ORG-02: Inconsistent package structure across services

**Severity: Medium | Effort: Small**

Package layout differs by service:
- core-banking-service: `model.entity`, `model.dto`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- user-service: `model.entity`, `model.dto`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `configuration.keycloak`, `configuration.feign`, `configuration.filter`, `configuration.audit`
- fund-transfer-service: `model.entity` (under `model.repository` path), `model.dto`, `model.mapper`, `model.repository`, `service.rest.client`

Repository interfaces live under `repository` in some services and `model.repository` in others. REST client packages vary (`service.rest.client` vs `service.rest`).

### GAP-ORG-03: No root Gradle build or multi-module project setup

**Severity: Low | Effort: Medium**

Each service is a standalone Gradle project with its own `gradlew`. There is no root `settings.gradle` or `build.gradle` to build all services at once, enforce consistent dependency versions, or share plugin configuration. Version alignment (Spring Boot 3.2.4, Spring Cloud 2023.0.0) is manually repeated in every `build.gradle`.

---

## 2. Error Handling

### GAP-ERR-01: All exceptions return HTTP 400 — no proper HTTP status code mapping

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). Entity-not-found errors should return 404, server errors should return 500, and validation errors should return 422. The catch-all `Exception` handler also returns 400 with a raw string body — leaking internal exception details.

### GAP-ERR-02: Inconsistent error response format

**Severity: Medium | Effort: Small**

- core-banking-service: Returns `ErrorResponse { code, message }` for known exceptions but a plain string for unknown exceptions.
- fund-transfer-service: Returns `ErrorResponse` (constructor-based) for known exceptions, plain string for others.
- user-service: Has a custom `FeignErrorDecoder` that extracts `SimpleBankingGlobalException` from Feign responses, but the local `GlobalExceptionHandler` still returns inconsistent formats.
- The catch-all handler across all services: `"Exception occur inside API " + e` — exposes stack trace information in the response body.

### GAP-ERR-03: No validation-error handling

**Severity: Medium | Effort: Small**

No `@Valid` or `@Validated` annotations on request body parameters. No `MethodArgumentNotValidException` handler exists. Invalid inputs are passed directly to the service layer, which may cause uncontrolled exceptions.

---

## 3. Testing

### GAP-TEST-01: Minimal test coverage — only core-banking-service has meaningful tests

**Severity: High | Effort: Large**

| Service | Test Files | Coverage |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` + context test | Service-layer unit tests with Mockito |
| fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` only | Context load test only |
| user-service | `InternetBankingUserServiceApplicationTests` only | Context load test only |
| utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` only | Context load test only |
| api-gateway | `InternetBankingApiGatewayApplicationTests` only | Context load test only |
| config-server | `InternetBankingConfigServerApplicationTests` only | Context load test only |
| service-registry | `InternetBankingServiceRegistryApplicationTests` only | Context load test only |

Three out of four business services have zero service-layer or controller-layer tests.

### GAP-TEST-02: No integration tests

**Severity: High | Effort: Large**

No `@SpringBootTest` tests with embedded database, no Testcontainers usage, no `@WebMvcTest` or `@DataJpaTest` tests exist. The core-banking-service tests use pure Mockito without Spring context. There are no tests that verify JPA mappings, Flyway migrations, or API contract behavior.

### GAP-TEST-03: No contract tests between services

**Severity: Medium | Effort: Large**

Services communicate via Feign clients, but there are no Spring Cloud Contract or Pact tests to verify that the Feign client interfaces match the actual controller signatures. Breaking changes in core-banking-service would not be caught until runtime.

---

## 4. Security

### GAP-SEC-01: Hardcoded database credentials in Docker Compose and SQL files

**Severity: Critical | Effort: Small**

- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password`
- `privileges.sql`: `CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

These should be externalized to environment variables or a secrets manager.

### GAP-SEC-02: No input validation on any API endpoint

**Severity: Critical | Effort: Small**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Jakarta Bean Validation annotations are used on any request DTO. A fund transfer with a negative amount, null accounts, or zero amount would be processed without validation.

### GAP-SEC-03: Raw `ResponseEntity` without type parameters

**Severity: Medium | Effort: Small**

All controllers return unparameterized `ResponseEntity` (raw type), which:
- Disables compile-time type safety
- Makes OpenAPI documentation inaccurate (response type shown as `Object`)
- Allows accidental type mismatches

### GAP-SEC-04: Keycloak singleton pattern is not thread-safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized `static` field check-and-set pattern (classic double-check locking bug without `volatile`). Under concurrent requests, this could create multiple Keycloak instances or expose a partially constructed object.

### GAP-SEC-05: User-service Feign client does not propagate authorization headers

**Severity: Medium | Effort: Small**

The user-service's `BankingCoreRestClient` does not include a `CustomFeignClientConfiguration` (unlike fund-transfer and utility-payment services). If core-banking-service requires authentication in the future, inter-service calls from user-service would fail.

### GAP-SEC-06: No dependency vulnerability scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or Dependabot configuration exists. Dependencies are manually managed with hardcoded versions.

---

## 5. API Design

### GAP-API-01: No pagination metadata in list responses

**Severity: Medium | Effort: Small**

Endpoints that accept `Pageable` (e.g., `GET /api/v1/transfer`, `GET /api/v1/bank-users`) return a bare `List<T>` instead of a `Page<T>` or a wrapper with `totalElements`, `totalPages`, `pageNumber`, `pageSize`. Clients cannot determine total result count or navigate pages.

### GAP-API-02: No API versioning strategy beyond URL prefix

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/` but there is no documented versioning policy, no content-type versioning, and no mechanism for deprecation.

### GAP-API-03: OpenAPI/Swagger dependency is incorrect

**Severity: Medium | Effort: Small**

All services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but only the API Gateway uses WebFlux. The other services use Spring MVC (servlet-based). The correct dependency for MVC services is `springdoc-openapi-starter-webmvc-ui`. This mismatch may cause Swagger UI to not work on business services.

### GAP-API-04: No filtering or search capabilities

**Severity: Low | Effort: Medium**

List endpoints support pagination (via Spring `Pageable`) but no filtering by status, date range, account number, or other business attributes.

### GAP-API-05: Inconsistent use of HTTP methods

**Severity: Low | Effort: Small**

`POST /api/v1/bank-users/register` could be simply `POST /api/v1/bank-users` per REST conventions. The `register` verb in the URL is non-RESTful. Similarly, `PATCH /api/v1/bank-users/update/{id}` should be `PATCH /api/v1/bank-users/{id}` — the `update` verb is redundant.

---

## 6. Observability

### GAP-OBS-01: No structured logging

**Severity: Medium | Effort: Small**

All services use Slf4j with default Logback configuration (plain-text log lines). For production-grade observability, logs should be structured (JSON format) to integrate with log aggregation tools (ELK, Splunk, Datadog).

### GAP-OBS-02: Logging includes potentially sensitive data

**Severity: High | Effort: Small**

Several log statements output full request objects via `toString()`:
- `FundTransferController`: `"Got fund transfer request from API {}" + request.toString()` — logs account numbers and amounts.
- `FundTransferService`: `"Sending fund transfer request {}" + request.toString()` — also has a string concatenation bug (uses `+` instead of `{}`).
- `UserController`: `"Creating user with {}" + request.toString()` — could log passwords.

### GAP-OBS-03: No custom health check endpoints

**Severity: Low | Effort: Small**

All services include `spring-boot-starter-actuator`, so `/actuator/health` is available. However, there are no custom `HealthIndicator` beans to check critical dependencies (database connectivity, Keycloak availability, config server connectivity).

### GAP-OBS-04: No metrics or alerting configuration

**Severity: Medium | Effort: Medium**

While Micrometer is included for tracing, there are no custom metrics (counters for transactions, gauges for queue depth, timers for Feign calls). Prometheus scraping is mentioned in the README tech stack but no Prometheus endpoint is configured.

---

## 7. Resilience

### GAP-RES-01: No circuit breaker on Feign clients

**Severity: High | Effort: Medium**

All inter-service communication is synchronous via Feign with no circuit breaker (Resilience4j or Hystrix). If core-banking-service goes down, all dependent services (fund-transfer, utility-payment, user) will cascade-fail with connection timeouts.

### GAP-RES-02: No retry policy on Feign clients

**Severity: Medium | Effort: Small**

Feign clients do not configure retry policies. Transient network failures or momentary service unavailability will immediately fail the request.

### GAP-RES-03: No timeout configuration on Feign clients

**Severity: High | Effort: Small**

No explicit `connectTimeout` or `readTimeout` is configured on any Feign client. Default timeouts (typically 10 seconds connect, 60 seconds read) may cause requests to hang for unacceptable periods.

### GAP-RES-04: No fallback behavior

**Severity: Medium | Effort: Medium**

There are no fallback methods on any Feign client. When an inter-service call fails, the exception propagates directly to the caller as an HTTP 400 (due to GAP-ERR-01) with no graceful degradation.

### GAP-RES-05: Fund transfer is not idempotent

**Severity: High | Effort: Medium**

The fund-transfer flow does not check for duplicate requests. If a client retries (due to timeout or network error), the same transfer could be executed twice. There is no idempotency key mechanism.

### GAP-RES-06: Balance update in utility payment has a double-subtraction bug

**Severity: Critical | Effort: Small**

In `TransactionService.utilPayment()`:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```
The `availableBalance` is set by subtracting from the **already-subtracted** `actualBalance`, effectively deducting the amount twice from `availableBalance`. The same bug exists in `internalFundTransfer()` for both source and destination accounts.

### GAP-RES-07: No distributed transaction or saga pattern

**Severity: High | Effort: Large**

Fund transfer and utility payment involve two services (orchestrator + core-banking), but there is no saga pattern, compensation logic, or outbox pattern. If fund-transfer-service saves `PENDING` status but core-banking-service fails, the status is never updated to `FAILED` — it remains `PENDING` indefinitely.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | Duplicate common classes across services | High | Medium |
| GAP-ORG-02 | Code Organization | Inconsistent package structure | Medium | Small |
| GAP-ORG-03 | Code Organization | No root multi-module build | Low | Medium |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-03 | Error Handling | No validation-error handling | Medium | Small |
| GAP-TEST-01 | Testing | Only core-banking has meaningful tests | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests between services | Medium | Large |
| GAP-SEC-01 | Security | Hardcoded credentials in Docker/SQL | Critical | Small |
| GAP-SEC-02 | Security | No input validation on any endpoint | Critical | Small |
| GAP-SEC-03 | Security | Raw ResponseEntity without type params | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-05 | Security | User-service Feign missing auth header propagation | Medium | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Small |
| GAP-API-03 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-API-04 | API Design | No filtering or search capabilities | Low | Medium |
| GAP-API-05 | API Design | Non-RESTful URL verbs | Low | Small |
| GAP-OBS-01 | Observability | No structured logging | Medium | Small |
| GAP-OBS-02 | Observability | Logging sensitive data | High | Small |
| GAP-OBS-03 | Observability | No custom health checks | Low | Small |
| GAP-OBS-04 | Observability | No metrics/alerting configuration | Medium | Medium |
| GAP-RES-01 | Resilience | No circuit breaker on Feign clients | High | Medium |
| GAP-RES-02 | Resilience | No retry policy on Feign clients | Medium | Small |
| GAP-RES-03 | Resilience | No timeout configuration on Feign clients | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | Fund transfer not idempotent | High | Medium |
| GAP-RES-06 | Resilience | Double-subtraction balance bug | Critical | Small |
| GAP-RES-07 | Resilience | No saga/compensation for distributed transactions | High | Large |
