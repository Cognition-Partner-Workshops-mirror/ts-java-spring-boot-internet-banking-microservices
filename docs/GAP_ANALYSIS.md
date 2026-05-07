# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps with severity ratings and remediation effort estimates.

---

## 1. Code Organization

### Current State
- Each service is an independent Gradle project (no shared parent/multi-project build)
- Consistent base package (`com.javatodev.finance`) across all services
- Standard layered architecture: `controller` → `service` → `repository` / `model`
- Common patterns (AuditAware, BaseMapper, error handling classes) are **duplicated** across services rather than extracted into a shared library

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CO-1 | No shared library for common code | Medium | Medium | `AuditAware`, `BaseMapper`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ApiRequestContext`, `AppAuthUserFilter`, `CustomFeignClientConfiguration` are copy-pasted across 3+ services. Changes require updating all copies. |
| CO-2 | No Gradle multi-project build | Low | Medium | Each service has its own `gradlew`, `gradle/wrapper`, and `settings.gradle`. A root `settings.gradle` with included builds would reduce duplication and enable shared dependency version management. |
| CO-3 | Inconsistent package structure | Low | Small | User service uses `model.repository.UserRepository` while fund-transfer uses `model.repository.FundTransferRepository` and core-banking uses `repository.BankAccountRepository` (no `model` prefix). |
| CO-4 | Build artifacts committed to VCS | Medium | Small | `build/` directories with compiled `.class` files and JARs are committed to the repository (visible in `internet-banking-utility-payment-service/build/`). |
| CO-5 | Mappers instantiated inline instead of injected | Low | Small | `private UserMapper userMapper = new UserMapper()` pattern used instead of Spring-managed beans, making testing harder. |

---

## 2. Error Handling

### Current State
- Each service has a `GlobalExceptionHandler` using `@ControllerAdvice`
- Custom exception hierarchy: `SimpleBankingGlobalException` → `EntityNotFoundException`, `InsufficientFundsException`, etc.
- Error responses use a consistent `ErrorResponse` DTO with `code` and `message` fields

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| EH-1 | All exceptions return HTTP 400 (Bad Request) | High | Small | `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` for ALL exceptions, including `EntityNotFoundException` (should be 404), `InsufficientFundsException` (should be 422), and generic `Exception` (should be 500). |
| EH-2 | Generic exception handler leaks internal details | Critical | Small | `handleException(Exception e)` returns `"Exception occur inside API " + e` which exposes stack traces, class names, and internal details to API consumers. |
| EH-3 | No error handling for Feign client failures | High | Medium | No `@FeignClient` fallback, no error decoder (except in user-service which has `CustomFeignErrorDecoder`), no handling of connection timeouts or downstream service unavailability. Fund-transfer and utility-payment services will propagate raw Feign exceptions. |
| EH-4 | Raw `ResponseEntity` without type parameters | Low | Small | Controllers use `ResponseEntity` without generics (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`), losing type safety and OpenAPI documentation accuracy. |
| EH-5 | No request validation | High | Small | No `@Valid` / `@NotNull` / `@NotBlank` annotations on request DTOs. Empty or null amounts, account numbers, etc. are not validated before processing. |

---

## 3. Testing

### Current State
- JUnit 5 configured in all services
- H2 in-memory database available for test profiles
- **Only `core-banking-service`** has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`)
- Other services have only empty `ApplicationTests` context-loading tests

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| TE-1 | No tests for user-service, fund-transfer-service, utility-payment-service | Critical | Large | Three business services have zero unit tests for their service/controller logic. Only placeholder `@SpringBootTest` tests that verify context loads. |
| TE-2 | No integration tests | High | Large | No tests that verify actual HTTP endpoints, database interactions with test containers, or end-to-end flows. |
| TE-3 | No contract tests between services | Medium | Large | Feign client interfaces are not validated against provider contracts. Changes to core-banking APIs could silently break consumers. |
| TE-4 | No test coverage reporting | Medium | Small | No JaCoCo or similar coverage plugin configured. No visibility into actual coverage percentage. |
| TE-5 | Test data tightly coupled to migrations | Low | Small | Tests rely on seed data from Flyway migrations rather than creating their own test fixtures, making tests fragile. |

---

## 4. Security

### Current State
- API Gateway enforces OAuth2 JWT validation via Keycloak
- User registration endpoint is publicly accessible (correct)
- Actuator endpoints are explicitly permitted (no auth required)
- `X-Auth-Id` header propagated from gateway to downstream services
- CSRF disabled on the gateway

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| SE-1 | Downstream services have no authentication | Critical | Medium | Fund-transfer, utility-payment, and core-banking services have no Spring Security dependency and accept any request. If accessed directly (bypassing gateway), they are completely unprotected. |
| SE-2 | Hard-coded credentials in Docker Compose | High | Small | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), DB user password (`oPItyPticIAt`) are committed in plaintext in `docker-compose.yml` and `privileges.sql`. |
| SE-3 | No input validation / sanitization | High | Medium | No Bean Validation annotations on request DTOs. SQL injection risk is mitigated by JPA parameterized queries, but business logic can receive invalid data (negative amounts, null accounts). |
| SE-4 | Keycloak singleton is not thread-safe | Medium | Small | `KeycloakProperties.getInstance()` uses lazy initialization without synchronization. Race condition possible during startup. |
| SE-5 | Password sent in plain JSON body | Medium | Small | User registration endpoint accepts password in request body without TLS enforcement at the application level (relies on infrastructure). No password complexity validation. |
| SE-6 | No dependency vulnerability scanning | Medium | Small | No OWASP Dependency Check, Snyk, or similar plugin configured in Gradle builds. |
| SE-7 | Actuator endpoints exposed without authentication | Medium | Small | All actuator endpoints (health, info, env, beans, etc.) are explicitly permitted in SecurityConfiguration, potentially exposing sensitive operational data. |

---

## 5. API Design

### Current State
- RESTful URL patterns with `/api/v1/` prefix
- Swagger/OpenAPI configured via `springdoc-openapi-starter-webflux-ui`
- Pagination support via Spring Data `Pageable`
- `@Operation` and `@Tag` annotations present on controllers

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| AD-1 | No API versioning strategy beyond URL prefix | Low | Medium | Version `v1` is hardcoded in paths. No documented strategy for introducing v2 or handling breaking changes. |
| AD-2 | Inconsistent response wrapping | Medium | Small | Some endpoints return raw entities, others return response DTOs. No consistent envelope (e.g., `{data, meta, errors}`). |
| AD-3 | No HATEOAS / hypermedia links | Low | Medium | Responses don't include links to related resources. Clients must hardcode URLs. |
| AD-4 | Paginated responses don't include metadata | Medium | Small | `GET /api/v1/transfer` returns `List<FundTransfer>` without total count, page number, or page size metadata. |
| AD-5 | No filtering or sorting on list endpoints | Low | Medium | List endpoints only support pagination via `Pageable`, no documented query parameters for filtering by status, date range, etc. |
| AD-6 | Wrong OpenAPI dependency | Medium | Small | Uses `springdoc-openapi-starter-webflux-ui` in non-reactive (Servlet) services. Should use `springdoc-openapi-starter-webmvc-ui` for Spring MVC services. |
| AD-7 | User registration endpoint permits method confusion | Low | Small | `POST /register` nested under `/bank-users` but follows different semantics than the CRUD operations on the same controller. |

---

## 6. Observability

### Current State
- Spring Boot Actuator included in all services
- Micrometer tracing with Brave bridge and Zipkin reporter configured
- Feign calls instrumented via `feign-micrometer`
- SLF4J logging with `@Slf4j` (Lombok)
- `gradle-git-properties` plugin generates build info

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| OB-1 | No structured logging (JSON) | Medium | Small | Default logback text format. Not suitable for log aggregation platforms (ELK, Datadog, etc.). |
| OB-2 | No custom health indicators | Low | Small | Only default health checks. No custom checks for MySQL connectivity, Keycloak availability, or downstream service health. |
| OB-3 | No metrics endpoints beyond defaults | Low | Small | No custom business metrics (e.g., transfer count, payment success rate, average transfer amount). |
| OB-4 | Logging sensitive data | High | Small | `log.info("Creating user with {}", request.toString())` in UserController logs potentially sensitive user data including passwords. `log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())` logs financial data. |
| OB-5 | No log correlation IDs visible in application logs | Medium | Small | While tracing is configured, log patterns don't include trace/span IDs, making it hard to correlate logs with traces. |
| OB-6 | No alerting/monitoring configuration | Medium | Medium | Prometheus is listed in the tech stack but no `prometheus` actuator endpoint exposure or Grafana dashboards are configured. |

---

## 7. Resilience

### Current State
- `wait-for-it.sh` scripts handle startup ordering in Docker
- `@Transactional` on core banking transaction operations
- Optimistic locking (`@Version`) on audited entities

### Gaps

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| RE-1 | No circuit breaker pattern | Critical | Medium | Feign clients have no Resilience4j/Hystrix circuit breaker. If core-banking-service goes down, all dependent services will hang and eventually exhaust thread pools. |
| RE-2 | No retry policies | High | Small | No automatic retries for transient failures (network blips, connection resets). Single Feign call failure = immediate user-facing error. |
| RE-3 | No timeout configuration | High | Small | No explicit connect/read timeouts on Feign clients or RestTemplate. Default infinite/very long timeouts can cause thread starvation. |
| RE-4 | No fallback behavior | Medium | Medium | No graceful degradation. If any downstream service is unavailable, the error bubbles up directly to the user with no fallback response. |
| RE-5 | No rate limiting | Medium | Medium | API Gateway has no rate limiting configured. A single client could overwhelm the system with requests. |
| RE-6 | Fund transfer not idempotent | High | Medium | `POST /api/v1/transfer` has no idempotency key. Network retries or duplicate submissions could result in double transfers. |
| RE-7 | No dead letter queue / compensation logic | Medium | Large | If the fund-transfer-service saves `PENDING` but the core-banking call fails partway through, there's no mechanism to detect and resolve stuck transactions. |
| RE-8 | Balance update race condition | Critical | Medium | `TransactionService.internalFundTransfer()` reads balance, then writes new balance without optimistic locking or `SELECT ... FOR UPDATE`. Concurrent transfers from the same account can overdraw. |
| RE-9 | **Balance calculation bug — `availableBalance` double-deducted/credited** | Critical | Small | In `TransactionService.internalFundTransfer()` (lines 90-91): `setActualBalance(actual - amount)` then `setAvailableBalance(actualBalance - amount)` — but `actualBalance` was *already mutated* on the previous line, so `availableBalance` ends up as `original - 2*amount`. Same bug on the credit side (lines 99-100) where the destination gets `original + 2*amount`. Identical issue in `utilPayment()` (lines 63-64). **Every transaction corrupts account balances.** This is a live data-integrity bug in the core financial engine. |

---

## 8. Correctness (Bonus — Author's Pick)

### Current State
The balance update logic in `TransactionService` is the heart of the banking system. It handles all fund movements.

### Gap

| # | Gap | Severity | Effort | Details |
|---|-----|----------|--------|---------|
| CX-1 | **Available balance double-mutation bug** | Critical | Small | This is the single most important finding in this assessment. In `core-banking-service/src/main/java/.../service/TransactionService.java`, the pattern `setActualBalance(X.subtract(amount)); setAvailableBalance(getActualBalance().subtract(amount))` reads the **already-updated** `actualBalance` and subtracts `amount` again. After a $100 transfer from an account with $1000: `actualBalance` correctly becomes $900, but `availableBalance` becomes $800 (should be $900). The bug compounds: after 5 transfers of $100, `actualBalance` = $500 (correct) but `availableBalance` = $0 (should be $500). This affects `internalFundTransfer()` debit, credit, AND `utilPayment()`. Fixing this is a 3-line change but requires a data migration to correct all affected account balances in production. |

**Why I care about this one specifically:** This isn't a "best practice gap" — it's a **correctness bug** that silently corrupts financial data on every single transaction. It's the kind of bug that passes code review because the two lines *look* like they're doing the same thing, but the order-of-mutation makes them semantically different. It would cause customer-visible issues (available balance showing less than actual balance, potentially blocking legitimate transactions due to false insufficient-funds checks).

---

## Summary Table

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 0 | 2 | 3 |
| Error Handling | 1 | 2 | 0 | 2 |
| Testing | 1 | 1 | 2 | 1 |
| Security | 1 | 2 | 3 | 0 |
| API Design | 0 | 0 | 3 | 4 |
| Observability | 0 | 1 | 3 | 2 |
| Resilience | 3 | 3 | 3 | 0 |
| Correctness | 1 | 0 | 0 | 0 |
| **Total** | **7** | **9** | **16** | **12** |
