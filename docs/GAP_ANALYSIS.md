# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `gradlew`, `settings.gradle`, and `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` for shared dependency management. This means:
- Dependency versions are duplicated across 6 `build.gradle` files.
- Upgrading a shared dependency (e.g., Spring Boot) requires editing every service individually.
- No shared plugin configuration or dependency constraints.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted identically (or near-identically) across 3-4 services:
- `BaseMapper` — identical in all 4 business services
- `AuditAware` (MappedSuperclass) — identical in user, fund-transfer, and utility-payment services
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — identical in user, fund-transfer, and utility-payment services
- `AuditConfig`, `AuditorAwareConfig` — identical in user, fund-transfer, and utility-payment services
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` — identical pattern in all 4 business services
- `TransactionStatus` enum — duplicated in fund-transfer and utility-payment services

There is no shared library or common module to centralize these.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- Core Banking uses `repository` package directly under `com.javatodev.finance.repository`.
- User Service nests it as `com.javatodev.finance.model.repository`.
- Fund Transfer Service uses `com.javatodev.finance.model.repository`.
- Utility Payment Service uses `com.javatodev.finance.repository`.
- DTO packages vary: `model.dto.request/response` (core, fund-transfer) vs. `model.rest.request/response` (user, utility-payment).

### 1.4 Mappers Instantiated Manually

**Severity: Low | Effort: Small**

Mapper classes (e.g., `BankAccountMapper`, `FundTransferMapper`) are instantiated via `new` in service classes rather than being Spring-managed beans. This prevents dependency injection, mocking in tests, and AOP proxy support.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns 400 for Everything

**Severity: Critical | Effort: Small**

Every service's `GlobalExceptionHandler` catches `Exception.class` and returns `400 Bad Request` with a raw string body:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```
This means:
- **500-level server errors** (NPE, DB connection failures) are returned as `400`.
- The exception's full `toString()` (including stack trace fragments) is exposed to clients — a **security risk**.
- No distinction between `404 Not Found`, `422 Unprocessable Entity`, `409 Conflict`, or `500 Internal Server Error`.

### 2.2 EntityNotFoundException Returns 400 Instead of 404

**Severity: High | Effort: Small**

`EntityNotFoundException` extends `SimpleBankingGlobalException`, which is caught by the handler and returned as `400 Bad Request`. The correct HTTP status should be `404 Not Found`.

### 2.3 Inconsistent Error Response Structure

**Severity: Medium | Effort: Small**

- Custom exceptions return `ErrorResponse` (`{code, message}`).
- The generic catch-all returns a plain string.
- No timestamp, path, or trace ID in error responses.
- Clients cannot reliably parse error responses due to inconsistent shapes.

### 2.4 No Feign Error Decoder in Most Services

**Severity: Medium | Effort: Small**

- The User Service has a `CustomFeignErrorDecoder` that extracts structured error responses from Feign failures.
- The Fund Transfer Service has only a logging-level Feign configuration (`Logger.Level.FULL`).
- The Utility Payment Service has an empty `CustomFeignClientConfiguration`.
- Without a proper error decoder, Feign errors surface as generic `FeignException` with no meaningful error propagation.

---

## 3. Testing

### 3.1 Near-Zero Test Coverage on 3 of 4 Business Services

**Severity: Critical | Effort: Large**

| Service | Tests |
|---|---|
| Core Banking | 3 unit test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with meaningful coverage |
| User Service | 1 empty `@SpringBootTest` context-load test only |
| Fund Transfer Service | 1 empty `@SpringBootTest` context-load test only |
| Utility Payment Service | 1 empty `@SpringBootTest` context-load test only |
| API Gateway | 1 empty `@SpringBootTest` context-load test only |
| Config Server | 1 empty `@SpringBootTest` context-load test only |
| Service Registry | 1 empty `@SpringBootTest` context-load test only |

Only the Core Banking Service has actual unit tests. The other services have zero business logic test coverage.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller endpoints (MockMvc / WebTestClient)
- Repository queries against a real database
- Feign client interactions (WireMock, Spring Cloud Contract)
- End-to-end flows through the API Gateway

### 3.3 No Contract Tests Between Services

**Severity: High | Effort: Large**

With 3 services consuming the Core Banking API via Feign, there are no contract tests (e.g., Spring Cloud Contract, Pact) to ensure API compatibility. Breaking changes in Core Banking could silently break downstream services.

### 3.4 Context-Load Tests Likely Fail Without Keycloak

**Severity: Medium | Effort: Small**

The User Service's `@SpringBootTest` context-load test depends on Keycloak configuration (`app.config.keycloak.*`). While test `application.yml` provides dummy values, the Keycloak admin client initialization may fail without a running server, making the test fragile.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`).
- `privileges.sql`: Application database password (`oPItyPticIAt`).
- `README.md`: Test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`).
- Test `application.yml` (user-service): Keycloak client secret (`e8548d56-d743-45ef-8655-063c9cd96759`).

While some of these are acceptable for local development, the Keycloak client secret in test config and the MySQL password in `privileges.sql` should use environment variables.

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No `@RequestBody` DTO has any Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.):
- `FundTransferRequest`: `amount` could be null, zero, or negative.
- `UtilityPaymentRequest`: no validation on any field.
- `User` registration: no email format validation (beyond Keycloak rejection), no password strength rules.
- Account numbers are not validated for format or length.

### 4.3 No Rate Limiting

**Severity: High | Effort: Medium**

The API Gateway has no rate limiting configured. Financial endpoints (fund transfer, utility payment) are vulnerable to abuse.

### 4.4 CSRF Disabled Without Justification

**Severity: Low | Effort: Small**

CSRF is disabled in the Gateway's `SecurityConfiguration`. While this is standard for stateless JWT-based APIs, there is no documentation explaining the decision.

### 4.5 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool is configured in the build. Known CVEs in transitive dependencies would go undetected.

### 4.6 Raw ResponseEntity Without Type Parameters

**Severity: Low | Effort: Small**

Most controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<?>` or `ResponseEntity<SpecificType>`. This bypasses compile-time type safety and can leak unintended data.

---

## 5. API Design

### 5.1 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While all endpoints use `/api/v1/`, there is no documented versioning strategy, no mechanism for deprecation notices, and no content-type versioning headers.

### 5.2 Inconsistent URL Naming Conventions

**Severity: Medium | Effort: Small**

- Core Banking uses `snake_case` path variables: `/bank-account/{account_number}`, `/util-account/{account_name}`.
- User Service uses `/bank-users/update/{id}` (verb in URL — not RESTful).
- Fund Transfer uses `/transfer` (root collection).
- Utility Payment uses `/utility-payment` (root collection).
- PATCH endpoint uses `/update/{id}` instead of simply `/{id}`.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Controllers accept Spring's `Pageable` parameters but return bare `List<T>` instead of `Page<T>` or a wrapper with total count, page size, and navigation links. Clients have no way to know the total number of records.

### 5.4 OpenAPI Documentation Partially Configured

**Severity: Medium | Effort: Small**

- All 4 business services include `springdoc-openapi-starter-webflux-ui:2.1.0` as a dependency.
- **Wrong starter:** The non-gateway services use Spring MVC (not WebFlux), so the WebFlux starter may not generate correct docs. The correct dependency is `springdoc-openapi-starter-webmvc-ui`.
- `@Tag` and `@Operation` annotations are present on controllers but `ResponseEntity` raw types prevent Springdoc from inferring response schemas.

### 5.5 No HATEOAS / Hypermedia Links

**Severity: Low | Effort: Medium**

API responses contain no links for related resources or navigation. This is common but limits API discoverability.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request entries; others do not.
- Log messages use string concatenation in some places (`"Sending fund transfer request {}" + request.toString()`) instead of parameterized logging.
- No structured logging format (JSON) is configured.
- `Exception.toString()` is logged and returned to clients in error handlers.

### 6.2 Health Checks Not Customized

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` and expose `/actuator/health`, but there are no custom health indicators for:
- Database connectivity (default Spring Boot auto-configured, but not verified)
- Feign client target availability
- Keycloak reachability (User Service)

### 6.3 No Metrics Endpoints Beyond Defaults

**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` provides Micrometer metrics, there are:
- No custom business metrics (e.g., transfer count, payment amounts, error rates).
- No Prometheus endpoint configured (despite Prometheus being listed in the tech stack).
- No Grafana dashboards or alerting rules.

### 6.4 Distributed Tracing Configured but Unverified

**Severity: Low | Effort: Small**

Zipkin dependencies and configuration are present, but:
- Sampling rate is not explicitly set (defaults to 10% in production).
- No span annotations for key business operations.
- No verification that trace context propagates correctly through Feign calls.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service calls via Feign are fire-and-forget with no circuit breaker pattern. If Core Banking Service goes down:
- Fund Transfer Service will block and eventually timeout on every request.
- Utility Payment Service will similarly fail.
- User Service's registration flow will fail.
- No fallback behavior is defined — failures cascade directly to clients.

Spring Cloud Circuit Breaker (Resilience4j) is not included in any `build.gradle`.

### 7.2 No Timeout Configuration

**Severity: High | Effort: Small**

Feign client timeouts are not configured anywhere in the codebase. Default Feign timeouts (10 seconds connect, 60 seconds read) may be too generous for a banking application, leading to thread pool exhaustion under load.

### 7.3 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for Feign calls or database operations. Transient network failures or database deadlocks result in immediate failure with no recovery attempt.

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

When downstream services are unavailable:
- No cached responses or degraded-mode behavior.
- No queue-based deferred processing for fund transfers or payments.
- The system is fully synchronous with no buffering.

### 7.5 Transaction Status Not Updated on Failure

**Severity: Critical | Effort: Small**

In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`:
- If the Feign call to Core Banking fails, the entity remains in `PENDING`/`PROCESSING` status forever.
- There is no `catch` block to set `status=FAILED`.
- No compensation or saga pattern for partial failures.

### 7.6 Potential Double-Deduction Bug in Balance Calculation

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` (core-banking-service, line 90-91):
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been reduced. This means `availableBalance = originalBalance - amount - amount` — a double deduction. The same bug exists for the credit side (lines 99-100) and in `utilPayment()` (lines 63-64).

### 7.7 No Idempotency Protection

**Severity: High | Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a failed request (e.g., due to network timeout), the same transfer could be processed multiple times, resulting in duplicate debits.

### 7.8 No Database-Level Locking on Concurrent Balance Updates

**Severity: Critical | Effort: Small**

This is arguably the most dangerous gap in the entire codebase. `TransactionService` performs a read-then-write on account balances with **no pessimistic or optimistic locking**:

```java
// internalFundTransfer(), lines 87-92
BankAccountEntity fromBankAccountEntity = bankAccountRepository.findByNumber(fromBankAccount.getNumber())...;
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
bankAccountRepository.save(fromBankAccountEntity);
```

`BankAccountEntity` has no `@Version` column for optimistic locking, and `BankAccountRepository.findByNumber()` uses no `@Lock(LockModeType.PESSIMISTIC_WRITE)`. If two fund transfers from the same account execute concurrently:

1. Thread A reads balance = $1000
2. Thread B reads balance = $1000 (same stale value)
3. Thread A subtracts $600, saves balance = $400
4. Thread B subtracts $500, saves balance = $500 (overwrites Thread A's write)

Result: $1,100 was debited from a $1,000 account, but the final balance shows $500. The $600 transfer is silently lost. In a banking application, this is a **money-losing data corruption bug** that gets worse under load. Even with `@Transactional` at the class level, the default isolation level (`READ_COMMITTED`) does not prevent this — it only guarantees each individual SQL statement sees committed data, not that the read-modify-write sequence is atomic.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | Generic exception handler returns 400 for everything | Error Handling | Critical | Small |
| 3.1 | Near-zero test coverage on 3/4 business services | Testing | Critical | Large |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small |
| 4.2 | No input validation on any DTO | Security | Critical | Medium |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.5 | Transaction status not updated on failure | Resilience | Critical | Small |
| 7.6 | Double-deduction bug in balance calculation | Resilience | Critical | Small |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 2.2 | EntityNotFoundException returns 400 instead of 404 | Error Handling | High | Small |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | High | Large |
| 4.3 | No rate limiting | Security | High | Medium |
| 7.2 | No timeout configuration for Feign | Resilience | High | Small |
| 7.3 | No retry policies | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | High | Medium |
| 7.7 | No idempotency protection | Resilience | High | Medium |
| 7.8 | No database-level locking on concurrent balance updates | Resilience | Critical | Small |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 2.3 | Inconsistent error response structure | Error Handling | Medium | Small |
| 2.4 | No Feign error decoder in most services | Error Handling | Medium | Small |
| 3.4 | Context-load tests fragile without Keycloak | Testing | Medium | Small |
| 4.5 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | No API versioning strategy | API Design | Medium | Medium |
| 5.2 | Inconsistent URL naming conventions | API Design | Medium | Small |
| 5.3 | No pagination metadata in responses | API Design | Medium | Small |
| 5.4 | OpenAPI docs: wrong starter + raw ResponseEntity | API Design | Medium | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.3 | No custom metrics or Prometheus endpoint | Observability | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers instantiated manually | Code Organization | Low | Small |
| 4.4 | CSRF disabled without documentation | Security | Low | Small |
| 4.6 | Raw ResponseEntity without type parameters | Security | Low | Small |
| 5.5 | No HATEOAS / hypermedia links | API Design | Low | Medium |
| 6.2 | Health checks not customized | Observability | Low | Small |
| 6.4 | Distributed tracing unverified | Observability | Low | Small |
