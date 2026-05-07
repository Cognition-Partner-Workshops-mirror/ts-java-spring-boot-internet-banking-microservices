# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale:**
- **Critical** — Production risk, data loss potential, or security vulnerability
- **High** — Significant quality or reliability issue; should be addressed before production
- **Medium** — Best-practice deviation that affects maintainability or developer experience
- **Low** — Minor improvement opportunity; nice-to-have

**Effort Scale:**
- **Small** — < 1 day per service, mostly mechanical changes
- **Medium** — 1–3 days, requires design decisions or cross-service coordination
- **Large** — 3+ days, significant refactoring or new infrastructure

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** Each service is a standalone Gradle project with its own `settings.gradle`, `build.gradle`, and Gradle wrapper. There is no root `settings.gradle` or `build.gradle` to coordinate builds, enforce consistent dependency versions, or share plugins.

**Impact:** Dependency version drift between services (e.g., one service could upgrade Spring Boot while others lag). No single command to build/test all services. Duplicated Gradle wrapper files (6 copies).

**Best Practice:** Use a Gradle multi-project build with a root `settings.gradle` that includes all services, and a root `build.gradle` with shared plugin/dependency versions via a version catalog or `subprojects {}` block.

---

### 1.2 Massive Code Duplication Across Services

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** The following classes are copy-pasted identically (or near-identically) across 3–4 services:
- `BaseMapper` (4 copies: core, user, fund-transfer, utility-payment)
- `AuditAware` (3 copies: user, fund-transfer, utility-payment)
- `SimpleBankingGlobalException` (4 copies)
- `ErrorResponse` (4 copies)
- `GlobalExceptionHandler` (4 copies, with minor variations)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (3 copies)
- `AuditConfig` / `AuditorAwareConfig` (3 copies)
- `CustomFeignClientConfiguration` (2 copies in different packages)

**Impact:** Bug fixes or improvements must be applied to every copy. Inconsistencies creep in (e.g., fund-transfer's `GlobalExceptionHandler` catches `Exception` and returns a plain string, while user-service's returns `ErrorResponse`).

**Best Practice:** Extract shared code into a `common` library module (e.g., `internet-banking-common`) published as a dependency.

---

### 1.3 Inconsistent Package Structure

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** Package layouts differ between services:
- User service: `configuration/feign/`, `configuration/keycloak/`, `model/dto/`, `model/entity/`, `model/rest/response/`
- Fund transfer: `configuration/` (flat), `model/dto/request/`, `model/dto/response/`
- Utility payment: `configuration/` (flat), `model/rest/request/`, `model/rest/response/`, `repository/` (top-level, not under `model/`)

**Impact:** Developers must learn different conventions per service. Harder to navigate.

**Best Practice:** Standardize on one package layout and document it (e.g., `controller/`, `service/`, `model/entity/`, `model/dto/`, `model/mapper/`, `repository/`, `configuration/`, `exception/`).

---

### 1.4 Mappers Not Managed by Spring

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** All mapper classes (e.g., `UserMapper`, `FundTransferMapper`, `BankAccountMapper`) are instantiated via `new` in service classes rather than being Spring-managed beans. Similarly, `AccountService` instantiates `BankAccountMapper` and `UtilityAccountMapper` directly.

**Impact:** Cannot leverage dependency injection, AOP, or testing mocks for mappers. Goes against Spring conventions.

**Best Practice:** Either annotate mappers with `@Component` and inject them, or switch to MapStruct for compile-time type-safe mapping.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** The generic `Exception` handler in `GlobalExceptionHandler` returns a **plain string** (`"Exception occur inside API " + e`) in the fund-transfer and core-banking services, while the user service returns `ErrorResponse`. The `SimpleBankingGlobalException` handler returns `ErrorResponse` in all services, but with raw `400 Bad Request` for every error type.

**Impact:** API consumers cannot reliably parse error responses. Stack traces are exposed to clients in the generic handler (potential information leakage).

**Best Practice:** All error handlers should return a consistent JSON error envelope (e.g., `{ code, message, timestamp, path }`). Never expose raw exception details.

---

### 2.2 All Errors Return 400 Bad Request

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** Every exception — including `EntityNotFoundException`, `InsufficientFundsException`, and unexpected server errors — is mapped to HTTP 400. There is no differentiation between:
- 404 Not Found (entity not found)
- 409 Conflict (duplicate registration)
- 422 Unprocessable Entity (business rule violation like insufficient funds)
- 500 Internal Server Error (unexpected failures)

**Impact:** Clients cannot distinguish recoverable errors from server failures. Violates REST conventions.

**Best Practice:** Map domain exceptions to appropriate HTTP status codes. Use `@ResponseStatus` annotations or explicit `ResponseEntity.status(...)` calls.

---

### 2.3 No Error Handling for Feign Client Failures

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** Fund Transfer Service and Utility Payment Service call Core Banking via Feign but have **no try-catch blocks** around the Feign calls in `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`. If the Feign call fails (network error, 500, timeout), the local entity remains in `PENDING`/`PROCESSING` status forever — it is never updated to `FAILED`.

The user service has a `CustomFeignErrorDecoder` but the fund-transfer and utility-payment services do not decode Feign errors into meaningful domain exceptions.

**Impact:** Silent data inconsistency. Transfer records stuck in `PENDING` with no mechanism to recover them. Clients receive raw Feign/HTTP exceptions.

**Best Practice:** Wrap Feign calls in try-catch, update entity status to `FAILED` on any exception, and propagate a meaningful error to the caller. Implement a `CustomFeignErrorDecoder` for all Feign clients.

---

### 2.4 Core Banking Exception Hierarchy Missing in Downstream Services

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Core banking service has specific exceptions (`EntityNotFoundException`, `InsufficientFundsException`) with error codes. The user service replicates some of these (`EntityNotFoundException`, `InvalidEmailException`, `UserAlreadyRegisteredException`). Fund-transfer and utility-payment only have the base `SimpleBankingGlobalException`.

**Impact:** Downstream services cannot throw typed exceptions for domain-specific failures.

**Best Practice:** Move shared exceptions to the common library. Ensure Feign error decoders translate upstream error codes to local exception types.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Severity | Effort |
|----------|--------|
| Critical | Large |

**Finding:** Test suite is extremely thin:
- **Core Banking Service:** 3 meaningful test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with good unit tests using Mockito. Plus a `contextLoads` test.
- **All other services (5):** Only `contextLoads()` smoke tests that don't even run successfully without full infrastructure (Config Server, Eureka, MySQL, Keycloak).

**Coverage gaps:**
- Zero tests for `FundTransferService`, `UtilityPaymentService`, `UserService` (in the user microservice)
- Zero controller/integration tests
- Zero tests for Feign clients, mappers, exception handlers, or security configuration
- No test for the API Gateway's security rules

**Impact:** No safety net for refactoring. Business logic changes can introduce regressions undetected.

**Best Practice:** Aim for >80% line coverage. Every service should have unit tests for service classes, controller tests using `@WebMvcTest`, and integration tests using `@SpringBootTest` with test containers.

---

### 3.2 Context-Load Tests Require Full Infrastructure

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** The `@SpringBootTest` context-load tests in user, fund-transfer, utility-payment, api-gateway, and config-server services will fail in a standalone CI environment because they attempt to connect to Config Server, Eureka, MySQL, and Keycloak.

Only the core-banking service has a test `application.yml` with H2 in-memory database configuration.

**Impact:** Tests cannot run in CI without the full Docker Compose stack. Defeats the purpose of automated testing.

**Best Practice:** Add `src/test/resources/application.yml` with embedded database (H2), disabled Eureka registration, disabled Config Server, and mocked external services. Or disable context-load tests and replace with targeted `@WebMvcTest` / `@DataJpaTest` tests.

---

### 3.3 No Integration or Contract Tests

| Severity | Effort |
|----------|--------|
| High | Large |

**Finding:** There are no integration tests (e.g., using Testcontainers for MySQL), no contract tests (e.g., Spring Cloud Contract or Pact) between services, and no end-to-end API tests.

**Impact:** Feign client interfaces can drift from actual controller signatures without detection. Inter-service communication failures are only caught in production.

**Best Practice:** Implement consumer-driven contract tests using Spring Cloud Contract or Pact to verify Feign client / provider compatibility. Add Testcontainers-based integration tests for database interactions.

---

## 4. Security

### 4.1 No Input Validation

| Severity | Effort |
|----------|--------|
| Critical | Small |

**Finding:** No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Positive`, or any Bean Validation annotations are used on any request DTO across any service. The `FundTransferRequest`, `UtilityPaymentRequest`, and `User` DTOs accept any values without validation.

**Examples of unvalidated inputs:**
- Transfer amount could be negative, zero, or null
- Account numbers could be empty strings
- Email could be any string (user registration)
- Provider ID could be null

**Impact:** Invalid data enters the system unchecked. Potential for negative transfers, null pointer exceptions, or data corruption.

**Best Practice:** Add Jakarta Validation annotations to all request DTOs. Add `@Valid` to controller method parameters. Add `spring-boot-starter-validation` dependency.

---

### 4.2 Hardcoded Database Credentials in Docker Compose

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** Database passwords are hardcoded in `docker-compose.yml`, `Dockerfile`, and the MySQL init script:
- MySQL root password: `woVERANKliGharym`
- MySQL app user password: `oPItyPticIAt`
- Keycloak admin password: `password`
- Keycloak DB password: `password`

**Impact:** Credentials are committed to version control. Anyone with repo access has database credentials.

**Best Practice:** Use Docker Compose environment variables (`${MYSQL_ROOT_PASSWORD}`) with a `.env` file (git-ignored) or Docker secrets. For production, use a secrets manager (Vault, AWS Secrets Manager).

---

### 4.3 No Role-Based Access Control on Downstream Services

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** The API Gateway validates JWT tokens and extracts the principal name, but downstream services (user, fund-transfer, utility-payment, core-banking) have **no security configuration** at all — no Spring Security dependency, no authentication checks, no role verification. They only read the `X-Auth-Id` header passively for auditing.

**Impact:** If a service is accessed directly (bypassing the gateway), there is no authentication or authorization. Any user can perform admin operations (e.g., approve users via PATCH). No role-based restrictions (e.g., only admins should approve users).

**Best Practice:** Either add Spring Security + JWT validation to each downstream service, or ensure network-level isolation (only gateway can reach downstream services) AND add method-level authorization (`@PreAuthorize`) for sensitive operations.

---

### 4.4 CSRF Disabled Without Documentation

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** CSRF protection is disabled in the API Gateway's `SecurityConfiguration` (`http.csrf(CsrfSpec::disable)`). This is common for stateless API-only backends using JWT, but there is no documentation explaining the rationale.

**Impact:** Minor — appropriate for a stateless REST API, but should be documented.

---

### 4.5 No Dependency Vulnerability Scanning

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** No dependency vulnerability scanning is configured (no OWASP Dependency-Check, Snyk, or Dependabot configuration). The project uses several third-party libraries that may have known vulnerabilities.

**Best Practice:** Add the OWASP Dependency-Check Gradle plugin or configure GitHub Dependabot/Snyk for automated CVE scanning.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** All controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`. For example:

```java
public ResponseEntity getBankAccount(...) { ... }
// Should be: public ResponseEntity<BankAccount> getBankAccount(...) { ... }
```

**Impact:** OpenAPI/Swagger documentation cannot infer response types. Clients have no compile-time type information. IDE warnings for raw types.

**Best Practice:** Always parameterize `ResponseEntity<T>` with the response type.

---

### 5.2 No API Versioning Strategy

| Severity | Effort |
|----------|--------|
| Low | Medium |

**Finding:** All APIs use `/api/v1/` prefix, suggesting awareness of versioning, but there is no mechanism to support multiple API versions simultaneously. No documentation on versioning strategy.

**Impact:** Minor for current state, but becomes important when breaking changes are needed.

**Best Practice:** Document the versioning strategy (URL-based is already in use). Plan for v2 coexistence when needed.

---

### 5.3 OpenAPI Documentation Incomplete

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** `springdoc-openapi-starter-webflux-ui` is included in dependencies for core-banking, user, fund-transfer, and utility-payment services. Some controllers have `@Tag` and `@Operation` annotations, but:
- Response schemas are undocumented (due to raw `ResponseEntity`)
- Error responses are not documented
- Request body schemas lack field descriptions
- The webflux UI dependency is used in servlet-based services (should be `springdoc-openapi-starter-webmvc-ui`)

**Impact:** Auto-generated Swagger docs are incomplete and may not work correctly due to the wrong springdoc starter.

**Best Practice:** Switch to `springdoc-openapi-starter-webmvc-ui` for servlet-based services. Add `@Schema` annotations to DTOs. Document error responses with `@ApiResponse`.

---

### 5.4 No Pagination Metadata in List Responses

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Paginated endpoints accept Spring's `Pageable` parameter but return raw `List<T>` — discarding page metadata (total elements, total pages, current page number).

```java
public ResponseEntity readFundTransfers(Pageable pageable) {
    return ResponseEntity.ok(fundTransferService.readAllTransfers(pageable)); // returns List<FundTransfer>
}
```

**Impact:** Clients cannot implement pagination controls (next/previous, total count) without the metadata.

**Best Practice:** Return `Page<T>` or a custom wrapper with `{ content, totalElements, totalPages, number, size }`.

---

### 5.5 No Filtering or Sorting Documentation

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** While Spring's `Pageable` supports sorting via query parameters, this is not documented in the API or OpenAPI specs. No custom filtering (e.g., filter transfers by status, date range) is implemented.

**Best Practice:** Document supported sort fields. Add filtering capabilities for list endpoints.

---

## 6. Observability

### 6.1 Inconsistent Logging Practices

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:**
- Logging is ad-hoc: some methods log entry/exit, others don't.
- `FundTransferService` has a logging bug: `log.info("Sending fund transfer request {}" + request.toString())` — uses string concatenation instead of `{}` placeholder, defeating lazy evaluation.
- No structured logging (JSON format) for log aggregation tools.
- No correlation ID propagation in log messages (Zipkin trace IDs are not included in log format).

**Impact:** Difficult to trace requests through logs. Performance impact from eager string concatenation.

**Best Practice:** Use SLF4J placeholders consistently. Configure structured JSON logging (Logback + logstash-logback-encoder). Include trace ID and span ID in the log pattern via MDC.

---

### 6.2 Actuator Endpoints Not Configured

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** While `spring-boot-starter-actuator` is included in all services, there is no configuration for which endpoints are exposed, what information they reveal, or any custom health indicators. Default actuator configuration may expose too much (or too little) information.

**Impact:** Health checks may not reflect true service health (e.g., database connectivity, Eureka registration). Metrics endpoints may not be exposed for Prometheus scraping.

**Best Practice:** Explicitly configure `management.endpoints.web.exposure.include` and add custom health indicators for critical dependencies (database, Keycloak, downstream services). Configure Prometheus metrics endpoint.

---

### 6.3 No Custom Health Checks

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** No custom `HealthIndicator` beans are defined. Services rely solely on auto-configured health indicators.

**Impact:** Health endpoint may report UP even when critical dependencies (Keycloak, downstream services) are unreachable.

**Best Practice:** Add custom health indicators for Keycloak connectivity (user service), Core Banking reachability (fund-transfer, utility-payment), and database connectivity validation.

---

### 6.4 No Metrics Collection for Business Operations

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** No custom Micrometer metrics for business-critical operations:
- No counter for successful/failed fund transfers
- No histogram for transfer amounts
- No timer for transaction processing duration
- No gauge for pending transactions

**Impact:** No visibility into business-level health. Cannot detect anomalies like sudden drops in transfer volume.

**Best Practice:** Add `@Timed` annotations or manual Micrometer counter/timer/histogram metrics for key operations.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** No circuit breaker library (Resilience4j, Hystrix) is configured. All Feign calls are fire-and-forget with no fallback behavior. If Core Banking Service goes down:
- Fund Transfer Service will fail every request and leave records in `PENDING` state
- Utility Payment Service will fail every request and leave records in `PROCESSING` state
- User Service registration will fail (cannot verify user against core banking)

**Impact:** Cascading failures. A single service failure brings down all dependent services. No graceful degradation.

**Best Practice:** Add `spring-cloud-starter-circuitbreaker-resilience4j`. Configure circuit breakers on all Feign clients with fallback methods.

---

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** No retry configuration for Feign clients or any transient failure scenarios. A single network glitch causes a permanent failure.

**Impact:** Transient failures (momentary network issues, brief service restarts) result in failed transactions that would succeed on retry.

**Best Practice:** Configure Spring Retry or Resilience4j retry with exponential backoff for Feign clients. Be careful with idempotency — fund transfer retries must be idempotent to avoid double-charging.

---

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** No explicit timeouts configured for:
- Feign client connections and reads (defaults to no timeout or very long timeouts)
- Database connection pool
- Keycloak admin client operations

**Impact:** A slow or hung Core Banking Service can cause thread exhaustion in upstream services as threads block indefinitely waiting for responses.

**Best Practice:** Configure `feign.client.config.default.connectTimeout` and `readTimeout`. Set database connection pool timeouts. Set Keycloak client timeouts.

---

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** No fallback logic exists for any service interaction:
- If Core Banking is down, there is no cached response or degraded mode
- If Keycloak is down, user registration fails with an unhandled exception
- No dead-letter queue for failed transactions

**Impact:** System is fragile — any downstream failure is immediately visible to the end user with no grace period.

**Best Practice:** Implement circuit breaker fallbacks. Consider a transaction outbox pattern or dead-letter queue for failed transfers. Cache frequently-read data (like utility account lookups).

---

### 7.5 No Idempotency Keys

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** Fund transfer and utility payment operations have no idempotency mechanism. If a client retries a request (e.g., due to network timeout), the same transfer could be processed twice.

**Impact:** Potential double-charging of customer accounts.

**Best Practice:** Accept an idempotency key in the request header or body. Check for existing transactions with the same key before processing.

---

### 7.6 Transaction Management Gaps

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** In `FundTransferService.fundTransfer()`:
1. Entity is saved with `PENDING` status (local DB)
2. Feign call to Core Banking (remote call)
3. Entity updated to `SUCCESS` (local DB)

If the Feign call succeeds but step 3 fails (e.g., DB write error), the money is transferred in Core Banking but the local record still shows `PENDING`. There is no compensating transaction or saga pattern.

Similarly, `TransactionService.internalFundTransfer()` uses `@Transactional` but performs multiple `bankAccountRepository.save()` calls — if the second save fails, the first debit is committed but the credit is not.

**Impact:** Data inconsistency between services. Potential for lost funds in the ledger.

**Best Practice:** Implement the Saga pattern or Outbox pattern for distributed transactions. At minimum, wrap the entire Core Banking transfer in a single `@Transactional` method and add compensating logic in the orchestrating service.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No multi-project Gradle build | Code Org | Medium | Medium |
| 1.2 | Massive code duplication | Code Org | High | Medium |
| 1.3 | Inconsistent package structure | Code Org | Low | Small |
| 1.4 | Mappers not Spring-managed | Code Org | Low | Small |
| 2.1 | Inconsistent error response format | Error Handling | High | Small |
| 2.2 | All errors return 400 | Error Handling | High | Small |
| 2.3 | No Feign failure handling | Error Handling | Critical | Medium |
| 2.4 | Incomplete exception hierarchy | Error Handling | Medium | Small |
| 3.1 | Minimal test coverage | Testing | Critical | Large |
| 3.2 | Context-load tests need infra | Testing | Medium | Small |
| 3.3 | No integration/contract tests | Testing | High | Large |
| 4.1 | No input validation | Security | Critical | Small |
| 4.2 | Hardcoded credentials | Security | High | Small |
| 4.3 | No downstream RBAC | Security | Medium | Medium |
| 4.4 | CSRF disabled undocumented | Security | Low | Small |
| 4.5 | No vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity types | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Medium |
| 5.3 | Incomplete OpenAPI docs | API Design | Medium | Small |
| 5.4 | No pagination metadata | API Design | Medium | Small |
| 5.5 | No filtering/sorting docs | API Design | Low | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | Actuator not configured | Observability | Medium | Small |
| 6.3 | No custom health checks | Observability | Medium | Small |
| 6.4 | No business metrics | Observability | Medium | Medium |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency keys | Resilience | High | Medium |
| 7.6 | Transaction management gaps | Resilience | Critical | Medium |

**Totals by Severity:**
- Critical: 5 gaps
- High: 8 gaps
- Medium: 12 gaps
- Low: 5 gaps
