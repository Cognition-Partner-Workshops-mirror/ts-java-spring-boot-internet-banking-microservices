# Internet Banking Microservices — Gap Analysis

This document evaluates the codebase against seven engineering best-practice categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and estimated **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### GAP-CO-01: No Shared Library — Duplicated Code Across Services
**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3–4 services with near-identical implementations:
- `BaseMapper` (core-banking, fund-transfer, user-service, utility-payment)
- `AuditAware` (fund-transfer, user-service, utility-payment)
- `GlobalExceptionHandler` (core-banking, fund-transfer, user-service, utility-payment)
- `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` (fund-transfer, user-service, utility-payment)
- `ErrorResponse` (core-banking, fund-transfer, user-service, utility-payment)
- `SimpleBankingGlobalException` (core-banking, fund-transfer, user-service, utility-payment)

There is no shared library or Gradle composite build to centralize these.

### GAP-CO-02: No Root Build File
**Severity: Medium | Effort: Small**

Each service has an independent `build.gradle` with no root `settings.gradle` for multi-project or composite builds. This makes it harder to enforce consistent dependency versions and build conventions.

### GAP-CO-03: Inconsistent Package Structure
**Severity: Low | Effort: Small**

Repository locations for model/DTO/mapper classes vary across services:
- core-banking: `model.mapper`, `model.entity`, `model.dto`
- fund-transfer: `model.mapper`, `model.entity`, `model.dto`, `model.repository`
- user-service: `model.mapper`, `model.entity`, `model.dto`, `model.repository`, `model.rest.response`
- utility-payment: `model.mapper`, `model.entity`, `model.dto`, `model.rest.request`, `model.rest.response`, `repository` (top-level)

Repository classes are in `repository` package in core-banking, `model.repository` in fund-transfer and user-service, and top-level `repository` in utility-payment.

### GAP-CO-04: Mappers Instantiated Inline Instead of as Spring Beans
**Severity: Low | Effort: Small**

Mappers (e.g., `BankAccountMapper`, `FundTransferMapper`) are instantiated with `new` inside service classes rather than being managed as Spring beans. This prevents injection of dependencies and makes testing harder.

---

## 2. Error Handling

### GAP-EH-01: All Errors Return HTTP 400 (Bad Request)
**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` in every service maps **all** exceptions — including `EntityNotFoundException`, `InsufficientFundsException`, and unhandled `Exception` — to `ResponseEntity.badRequest()` (HTTP 400). This means:
- Not-found errors return 400 instead of 404
- Server errors return 400 instead of 500
- Insufficient funds returns 400 instead of 422 or 409
- Clients cannot distinguish error types by HTTP status code

### GAP-EH-02: Generic Exception Catch-All Leaks Internal Details
**Severity: High | Effort: Small**

The catch-all `@ExceptionHandler({Exception.class})` returns the raw exception object as a string: `"Exception occur inside API " + e`. This leaks stack traces, class names, and potentially sensitive data to API consumers.

### GAP-EH-03: No Feign Error Decoder in Fund Transfer and Utility Payment
**Severity: High | Effort: Small**

Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services use default Feign error handling, which wraps downstream errors in opaque `FeignException`s. This means the `GlobalExceptionHandler` receives a raw `FeignException` and returns the full Feign error body to the client.

### GAP-EH-04: Inconsistent Error Response Format
**Severity: Medium | Effort: Small**

Business exceptions return `ErrorResponse { code, message }` but the generic exception handler returns a plain string. Clients must handle two different response shapes for the same endpoint.

### GAP-EH-05: No Rollback/Compensation on Feign Failure
**Severity: Critical | Effort: Large**

If the Feign call to core-banking fails after the local entity is saved:
- Fund transfers stay in `PENDING` forever
- Utility payments stay in `PROCESSING` forever

There is no retry logic, compensation transaction, or dead-letter queue. This is a data consistency issue in a financial system.

---

## 3. Testing

### GAP-TE-01: Minimal Unit Test Coverage
**Severity: High | Effort: Large**

Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other five services have only empty `ApplicationTests` classes that test nothing beyond Spring context loading (and most would fail to load without external dependencies).

| Service | Test Classes | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 | 3 (AccountServiceTest, TransactionServiceTest, UserServiceTest) |
| internet-banking-fund-transfer-service | 1 | 0 (empty ApplicationTests) |
| internet-banking-user-service | 1 | 0 (empty ApplicationTests) |
| internet-banking-utility-payment-service | 1 | 0 (empty ApplicationTests) |
| internet-banking-api-gateway | 1 | 0 (empty ApplicationTests) |
| internet-banking-config-server | 1 | 0 (empty ApplicationTests) |

### GAP-TE-02: No Integration Tests
**Severity: High | Effort: Large**

No integration tests exist for Feign client interactions, database operations with real MySQL, or end-to-end API flows. The test configurations use H2 in-memory database with Flyway disabled, so even existing tests don't verify real database behavior.

### GAP-TE-03: No Contract Tests
**Severity: Medium | Effort: Large**

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist for Feign client interfaces. Breaking changes in core-banking API would not be caught until runtime.

---

## 4. Security

### GAP-SE-01: No Auth on Downstream Services
**Severity: Critical | Effort: Medium**

JWT validation occurs only at the API Gateway. Downstream services (core-banking, fund-transfer, user-service, utility-payment) have **no security configuration**. They blindly trust the `X-Auth-Id` header passed by the gateway. Any service accessible on the Docker network (or if ports are exposed) can be called directly without authentication.

### GAP-SE-02: No Input Validation
**Severity: Critical | Effort: Small**

No `@Valid` annotations or Bean Validation constraints exist on any request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`). Null or negative values for `amount`, missing `fromAccount`/`toAccount`, etc. will cause `NullPointerException`s or corrupt data.

### GAP-SE-03: Hardcoded Credentials in Source Control
**Severity: High | Effort: Small**

Multiple credentials are committed to the repository:
- MySQL root password: `woVERANKliGharym` (docker-compose.yml)
- MySQL app user password: `oPItyPticIAt` (privileges.sql)
- Keycloak admin password: `password` (docker-compose.yml)
- Keycloak DB password: `password` (docker-compose.yml)
- Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` (test application.yml)
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-SE-04: CSRF Disabled at Gateway
**Severity: Medium | Effort: Small**

`SecurityConfiguration` explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While common for API-only services using Bearer tokens, this should be a conscious, documented decision.

### GAP-SE-05: Keycloak Singleton Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern. In a multi-threaded environment, multiple `Keycloak` instances could be created during initialization.

### GAP-SE-06: Overly Broad Database Privileges
**Severity: Medium | Effort: Small**

The `javatodev_development` MySQL user has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` (all databases). Each service should have a dedicated user with minimal privileges scoped to its own schema.

### GAP-SE-07: No Rate Limiting
**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configured. Financial endpoints (fund transfer, payments) are vulnerable to abuse.

---

## 5. API Design

### GAP-AD-01: Raw `ResponseEntity` Without Type Parameters
**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity<BankAccount>`). This prevents compile-time type safety and breaks OpenAPI schema generation.

### GAP-AD-02: Wrong OpenAPI Dependency
**Severity: Medium | Effort: Small**

All four MVC-based services (core-banking, fund-transfer, user-service, utility-payment) use `springdoc-openapi-starter-webflux-ui:2.1.0` — the **WebFlux** variant. They should use `springdoc-openapi-starter-webmvc-ui` since they run on Spring MVC (Servlet stack). This likely causes runtime issues or silent failures with Swagger UI.

### GAP-AD-03: No Pagination Metadata in List Responses
**Severity: Medium | Effort: Small**

List endpoints (e.g., `GET /api/v1/transfer`, `GET /api/v1/utility-payment`) accept `Pageable` but return `List<T>`. The response contains no pagination metadata (total elements, total pages, page number, page size), making it unusable for paginated UIs.

### GAP-AD-04: No API Versioning Strategy
**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` prefix, but there is no documented strategy or infrastructure for version migration. Header-based or content-type versioning is not implemented.

### GAP-AD-05: Inconsistent Endpoint Naming
**Severity: Low | Effort: Small**

- Core banking uses `/api/v1/transaction/fund-transfer` and `/api/v1/transaction/util-payment` (verb-noun)
- Fund transfer service uses `/api/v1/transfer` (noun)
- Utility payment uses `/api/v1/utility-payment` (noun)
- User service uses `/api/v1/bank-users` (noun) with sub-paths `/register`, `/update/{id}`

### GAP-AD-06: No HATEOAS or Self-Links
**Severity: Low | Effort: Medium**

Responses contain no hypermedia links for discoverability. For a banking API, this limits client flexibility.

---

## 6. Observability

### GAP-OB-01: No Structured Logging
**Severity: Medium | Effort: Small**

All services use default Spring Boot logging with unstructured text output. In a microservices architecture, structured JSON logging (e.g., via Logback JSON encoder) is essential for log aggregation and search.

### GAP-OB-02: No Custom Health Checks
**Severity: Medium | Effort: Small**

While Spring Boot Actuator is included in all services, no custom health indicators exist for:
- Database connectivity checks
- Keycloak connectivity (user-service)
- Downstream service availability

### GAP-OB-03: No Custom Metrics
**Severity: Low | Effort: Medium**

No business-level Micrometer metrics are defined (e.g., transfer count, payment amount, error rates). Only default Actuator metrics are available.

### GAP-OB-04: Sensitive Data in Log Messages
**Severity: Medium | Effort: Small**

`FundTransferRequest.toString()` and `UtilityPaymentRequest.toString()` (generated by Lombok `@Data`) are logged directly, potentially exposing account numbers and amounts in plain text logs. Example: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())`.

### GAP-OB-05: Zipkin Tracing Configuration Not Verified
**Severity: Low | Effort: Small**

While Zipkin dependencies are included, the sampling rate and trace propagation configuration are managed entirely through externalized config (GitHub repo). Local verification is not possible without running the full stack.

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers
**Severity: Critical | Effort: Medium**

No Resilience4j or Hystrix circuit breakers are configured for Feign clients. If `core-banking-service` becomes slow or unavailable, all dependent services (fund-transfer, user-service, utility-payment) will exhaust their thread pools and cascade-fail.

### GAP-RE-02: No Retry Policies
**Severity: High | Effort: Small**

No automatic retry is configured for transient Feign failures (network blips, temporary 503s). A single failed HTTP call causes the entire operation to fail.

### GAP-RE-03: No Timeout Configuration
**Severity: High | Effort: Small**

No explicit read/connect timeouts are configured on Feign clients or RestTemplate. Default infinite or very long timeouts can lead to thread exhaustion under failure scenarios.

### GAP-RE-04: No Idempotency Keys
**Severity: Critical | Effort: Medium**

Fund transfer and utility payment `POST` endpoints have no idempotency mechanism. Network retries, client double-submits, or Feign retries can cause duplicate financial transactions. This is unacceptable for a banking system.

### GAP-RE-05: No Fallback Behavior
**Severity: Medium | Effort: Medium**

Feign clients have no `@FeignClient(fallback = ...)` or `fallbackFactory` defined. There is no graceful degradation — any downstream failure is propagated directly to the client.

### GAP-RE-06: No Database Connection Pool Configuration
**Severity: Medium | Effort: Small**

No explicit HikariCP pool configuration (max-pool-size, connection-timeout, idle-timeout) is visible in application configurations. Spring Boot defaults may not be appropriate for production load.

### GAP-RE-07: TransactionEntity Uses cascade=ALL with OneToOne
**Severity: Medium | Effort: Small**

`TransactionEntity.account` uses `@OneToOne(cascade = CascadeType.ALL)`. Saving a transaction could unintentionally cascade operations to the bank account entity, causing unexpected side effects.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| GAP-CO-01 | No shared library — duplicated code | Code Organization | High | Medium |
| GAP-CO-02 | No root build file | Code Organization | Medium | Small |
| GAP-CO-03 | Inconsistent package structure | Code Organization | Low | Small |
| GAP-CO-04 | Mappers not Spring beans | Code Organization | Low | Small |
| GAP-EH-01 | All errors return HTTP 400 | Error Handling | Critical | Small |
| GAP-EH-02 | Generic handler leaks internals | Error Handling | High | Small |
| GAP-EH-03 | No Feign error decoder (2 services) | Error Handling | High | Small |
| GAP-EH-04 | Inconsistent error response format | Error Handling | Medium | Small |
| GAP-EH-05 | No rollback/compensation on Feign failure | Error Handling | Critical | Large |
| GAP-TE-01 | Minimal unit test coverage | Testing | High | Large |
| GAP-TE-02 | No integration tests | Testing | High | Large |
| GAP-TE-03 | No contract tests | Testing | Medium | Large |
| GAP-SE-01 | No auth on downstream services | Security | Critical | Medium |
| GAP-SE-02 | No input validation | Security | Critical | Small |
| GAP-SE-03 | Hardcoded credentials in source | Security | High | Small |
| GAP-SE-04 | CSRF disabled at gateway | Security | Medium | Small |
| GAP-SE-05 | Keycloak singleton not thread-safe | Security | Medium | Small |
| GAP-SE-06 | Overly broad database privileges | Security | Medium | Small |
| GAP-SE-07 | No rate limiting | Security | Medium | Medium |
| GAP-AD-01 | Raw ResponseEntity (no generics) | API Design | Medium | Small |
| GAP-AD-02 | Wrong OpenAPI dependency (webflux vs webmvc) | API Design | Medium | Small |
| GAP-AD-03 | No pagination metadata | API Design | Medium | Small |
| GAP-AD-04 | No API versioning strategy | API Design | Low | Medium |
| GAP-AD-05 | Inconsistent endpoint naming | API Design | Low | Small |
| GAP-AD-06 | No HATEOAS | API Design | Low | Medium |
| GAP-OB-01 | No structured logging | Observability | Medium | Small |
| GAP-OB-02 | No custom health checks | Observability | Medium | Small |
| GAP-OB-03 | No custom metrics | Observability | Low | Medium |
| GAP-OB-04 | Sensitive data in logs | Observability | Medium | Small |
| GAP-OB-05 | Zipkin config not verified | Observability | Low | Small |
| GAP-RE-01 | No circuit breakers | Resilience | Critical | Medium |
| GAP-RE-02 | No retry policies | Resilience | High | Small |
| GAP-RE-03 | No timeout configuration | Resilience | High | Small |
| GAP-RE-04 | No idempotency keys | Resilience | Critical | Medium |
| GAP-RE-05 | No fallback behavior | Resilience | Medium | Medium |
| GAP-RE-06 | No DB connection pool config | Resilience | Medium | Small |
| GAP-RE-07 | Cascade ALL on TransactionEntity | Resilience | Medium | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 7 |
| High | 9 |
| Medium | 16 |
| Low | 5 |
| **Total** | **37** |
