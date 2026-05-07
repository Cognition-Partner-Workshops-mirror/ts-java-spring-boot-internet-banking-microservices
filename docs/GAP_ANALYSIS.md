# Engineering Standards Gap Analysis

This document compares the codebase against industry-standard engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

**Severity Scale:**
- **Critical** — Security risk, data-loss risk, or production-blocking issue
- **High** — Significant quality or reliability concern; should be addressed before production
- **Medium** — Best-practice deviation that impacts maintainability or developer experience
- **Low** — Polish item or minor inconsistency

**Effort Scale:**
- **Small** — < 1 day per service; configuration change or minor code edit
- **Medium** — 1–3 days; requires moderate refactoring or new component
- **Large** — > 3 days; cross-cutting concern or architectural change

---

## 1. Code Organization

### GAP-CO-01: No Gradle Multi-Project Build

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Each service is an independent Gradle project with its own `build.gradle`. There is no root `settings.gradle` or `build.gradle` to unify the build. This leads to duplicated dependency declarations, inconsistent plugin versions, and inability to run a single `./gradlew build` for the entire project.

**Evidence:** Seven separate `build.gradle` files with repeated `springCloudVersion`, `springBootVersion`, and plugin blocks.

---

### GAP-CO-02: Duplicated Code Across Services

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** Significant code is copy-pasted across services with no shared library:
- `BaseMapper` interface — identical in core-banking, user, fund-transfer, utility-payment (4 copies)
- `AuditAware` base class — identical in user, fund-transfer, utility-payment (3 copies)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — identical in user, fund-transfer, utility-payment (3 copies each)
- `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` — similar copies in all 4 business services
- `GlobalErrorCode` — duplicated in core-banking and user service (with different error codes)

**Impact:** Bug fixes or improvements must be applied in every copy. Drift between copies is likely.

---

### GAP-CO-03: Inconsistent Package Structure

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Package naming differs across services:
- core-banking: `repository/` at top level
- user-service: `model/repository/`
- fund-transfer: `model/repository/`
- utility-payment: `repository/` at top level
- Feign clients: `service/rest/` (user), `service/rest/client/` (fund-transfer), `service/rest/` (utility-payment)
- Feign config: `configuration/feign/` (user), `configuration/` (fund-transfer, utility-payment)

---

### GAP-CO-04: Mapper Instantiation Pattern

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Mappers are instantiated with `new` inside `@Service` classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans or using MapStruct. This bypasses dependency injection and makes testing harder.

---

## 2. Error Handling

### GAP-EH-01: All Exceptions Return HTTP 400

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding:** Every `GlobalExceptionHandler` across all four business services returns `400 Bad Request` for all exceptions — including `EntityNotFoundException` (should be `404`), `InsufficientFundsException` (could be `422`), and the generic `Exception` catch-all (should be `500`).

**Evidence:**
```java
// core-banking GlobalExceptionHandler
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...); // always 400
}
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e); // 400 for everything
}
```

---

### GAP-EH-02: Exception Details Leaked to Clients

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** The generic `Exception` handler returns the full exception object (including stack trace fragments) in the response body: `"Exception occur inside API " + e`. This exposes internal implementation details, class names, and potentially sensitive information.

**Evidence:** All four `GlobalExceptionHandler` classes contain this pattern.

---

### GAP-EH-03: Inconsistent Error Response Format

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Custom exceptions return a structured `ErrorResponse { code, message }`, but the generic catch-all returns a plain string. Clients cannot reliably parse error responses.

Additionally, `ErrorResponse` is duplicated in each service with slight variations (some use `@Builder`, others use a constructor).

---

### GAP-EH-04: No Error Handling for Feign Call Failures

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:**
- **Fund-transfer-service:** No Feign error decoder configured. If `core-banking-service` returns an error, the raw Feign exception propagates.
- **Utility-payment-service:** `CustomFeignClientConfiguration` extends `FeignClientConfiguration` but adds nothing — empty class.
- **User-service:** Has a `CustomFeignErrorDecoder` that parses the response body — the only service with proper Feign error handling.

**Impact:** Fund-transfer and utility-payment services will surface opaque 500 errors to clients when the core-banking service rejects a request.

---

## 3. Testing

### GAP-TE-01: Minimal Test Coverage

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding:** Only `core-banking-service` has meaningful unit tests (19 tests across 3 service classes). The other services have only the default Spring Boot context-load test (`*ApplicationTests.java`), which does not test any business logic.

| Service | Test Files | Meaningful Tests |
|---------|-----------|-----------------|
| core-banking-service | 4 | 19 unit tests |
| internet-banking-user-service | 1 | 0 (context load only) |
| internet-banking-fund-transfer-service | 1 | 0 (context load only) |
| internet-banking-utility-payment-service | 1 | 0 (context load only) |
| internet-banking-api-gateway | 1 | 0 (context load only) |
| internet-banking-config-server | 1 | 0 (context load only) |
| internet-banking-service-registry | 1 | 0 (context load only) |

---

### GAP-TE-02: No Integration Tests

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding:** No integration tests exist. There are no tests that:
- Start the Spring context and test controller endpoints
- Use `@SpringBootTest` with `@AutoConfigureMockMvc` or `WebTestClient`
- Test Feign client interactions (even with WireMock)
- Test database operations with an actual database (H2 dependency exists but is unused for integration tests)

---

### GAP-TE-03: No Contract Tests

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Large |

**Finding:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between services. Given the tight Feign coupling between user/fund-transfer/utility-payment services and core-banking-service, API changes in core-banking could silently break consumers.

---

### GAP-TE-04: Context-Load Tests May Fail Without Infrastructure

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** The `*ApplicationTests` classes attempt to load the full Spring context, which requires Config Server, Eureka, MySQL, and Keycloak to be available. These tests will fail in CI without infrastructure or proper test profiles to disable cloud-config and Eureka.

---

## 4. Security

### GAP-SE-01: Hardcoded Credentials in Source Code

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** Credentials are hardcoded in version-controlled files:

| File | Credential |
|------|-----------|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose/docker-compose.yml` | Keycloak DB password: `password` |
| `docker-compose/mysql/Dockerfile` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | DB user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

---

### GAP-SE-02: No Input Validation

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** No `@Valid` or `@Validated` annotations on any `@RequestBody` parameters. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.) on any DTO fields.

**Impact:**
- Fund transfers can be initiated with `null` or negative amounts
- Users can register with empty or malformed emails
- Utility payments can be submitted without a provider or reference number

---

### GAP-SE-03: CSRF Disabled Without Documentation

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** CSRF protection is explicitly disabled in the API Gateway's `SecurityConfiguration`: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a stateless REST API using JWT bearer tokens, this decision is not documented or explained in configuration.

---

### GAP-SE-04: Downstream Services Have No Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** Only the API Gateway enforces OAuth2/JWT authentication. The four business services (user, fund-transfer, utility-payment, core-banking) have no security configuration — if they are network-accessible, they can be called directly without authentication.

**Mitigating Factor:** In Docker Compose, services are on a private network, but in Kubernetes or any other deployment, this requires proper network policies or service-level auth.

---

### GAP-SE-05: Keycloak Client Singleton Not Thread-Safe

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** `KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton for the `Keycloak` client. In a multi-threaded environment, multiple instances could be created during startup.

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

---

### GAP-SE-06: No Dependency Vulnerability Scanning

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** No dependency vulnerability scanning is configured. There is no OWASP Dependency Check plugin, Snyk, or similar tool in the build pipeline.

---

## 5. API Design

### GAP-AD-01: Inconsistent URL Patterns

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:**
- Core-banking uses `snake_case` in path params: `/bank-account/{account_number}`, `/util-account/{account_name}`
- User-service uses plain identifiers: `/bank-users/{id}`, `/bank-users/update/{id}`
- The PATCH endpoint uses `/update/{id}` — the verb is redundant in REST (PATCH already implies update)
- Mixing of `kebab-case` and abbreviations: `util-account` vs `utility-payment`

---

### GAP-AD-02: Raw `ResponseEntity` Without Generic Types

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Most controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This loses type safety and makes OpenAPI documentation less accurate. The user-service is the exception — it properly uses generics.

---

### GAP-AD-03: No API Versioning Strategy

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** While APIs use `/api/v1/` prefix, there is no strategy or infrastructure for supporting multiple API versions simultaneously. No documentation of what constitutes a breaking change or how consumers would migrate.

---

### GAP-AD-04: No Pagination Metadata in Responses

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Paginated endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>`. Clients receive no metadata about total elements, total pages, or current page position.

**Evidence:** `UserService.readUsers()` calls `userRepository.findAll(pageable).getContent()` — discarding all pagination metadata.

---

### GAP-AD-05: OpenAPI Spec Uses Wrong Starter

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Four business services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but they are **Spring MVC** (servlet-based) applications, not WebFlux. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. The webflux starter may work partially but is not the intended usage.

---

## 6. Observability

### GAP-OB-01: No Structured Logging

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** All logging uses unstructured `log.info()` / `log.error()` with string concatenation. No JSON structured logging is configured. In a microservices environment, structured logs are essential for aggregation and searching.

**Examples:**
- `log.info("Reading account by ID {}", accountNumber)` — no correlation ID or request context
- `log.error("IO Exception on reading exception message feign client" + e)` — string concatenation (should use `{}` placeholder)

---

### GAP-OB-02: Actuator Endpoints Not Fully Configured

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Actuator is included in all services, but there is no explicit configuration of which endpoints are exposed, what health indicators are included, or whether Prometheus metrics are enabled. The `spring-boot-starter-actuator` dependency exists, but configuration is deferred to the external config repo.

---

### GAP-OB-03: No Metrics Endpoint / Prometheus Integration

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** While the README mentions Prometheus and Micrometer dependencies exist for tracing, there is no `micrometer-registry-prometheus` dependency in any `build.gradle`. Services cannot expose a `/actuator/prometheus` metrics endpoint for scraping.

---

### GAP-OB-04: Distributed Tracing Has Gaps

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:**
- Tracing libraries are included and Zipkin is in Docker Compose, but trace IDs are not included in log output (no MDC pattern configured in logback)
- The `internet-banking-service-registry` and `internet-banking-config-server` do not include tracing dependencies
- No custom span annotations for business-critical operations

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** No circuit breaker library (Resilience4j, Hystrix) is configured. All inter-service calls via OpenFeign are fire-and-forget. If `core-banking-service` becomes slow or unresponsive:
- `fund-transfer-service` will block indefinitely (no timeout)
- `utility-payment-service` will block indefinitely
- `user-service` will block on Keycloak and core-banking calls

**Impact:** A single slow service cascades failures to all upstream services.

---

### GAP-RE-02: No Retry Policies

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** No retry configuration exists for Feign clients. Transient network errors or temporary service unavailability will immediately fail the request.

---

### GAP-RE-03: No Timeout Configuration

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No connection or read timeouts are configured for:
- Feign clients (default infinite timeout)
- Database connections (no pool configuration visible)
- Keycloak admin client

---

### GAP-RE-04: No Fallback Behavior

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** No fallback logic exists for any inter-service call. When core-banking-service is unavailable:
- Fund transfers fail with an unhandled exception
- Utility payments fail with an unhandled exception
- User registration fails (Feign call to verify user)

No graceful degradation patterns (cached responses, queued processing, default values) are implemented.

---

### GAP-RE-05: Non-Atomic Fund Transfer Operation

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Large |

**Finding:** The fund transfer flow spans two services without distributed transaction support:

1. `fund-transfer-service` saves entity with status `PENDING`
2. `fund-transfer-service` calls `core-banking-service` via Feign (HTTP)
3. `core-banking-service` debits source, credits destination (local `@Transactional`)
4. `fund-transfer-service` updates entity to `SUCCESS`

**Failure scenarios:**
- If step 4 fails (network error after core-banking completes), money is transferred but fund-transfer-service shows `PENDING` forever
- If `fund-transfer-service` crashes between steps 2 and 4, the transfer is completed but never recorded
- No compensation/reversal mechanism exists
- No idempotency key to prevent duplicate transfers on retry

---

### GAP-RE-06: Balance Calculation Bug

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** In `TransactionService.internalFundTransfer()`, the available balance is calculated incorrectly:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^^ subtracts amount TWICE — actualBalance is already reduced
```

The same bug exists in `utilPayment()`:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```

**Impact:** Available balance will always be `amount` less than actual balance after any transaction. This is a correctness bug in financial logic.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-CO-01 | Code Organization | No Gradle multi-project build | Medium | Medium |
| GAP-CO-02 | Code Organization | Duplicated code across services | High | Medium |
| GAP-CO-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-CO-04 | Code Organization | Mapper instantiation pattern | Low | Small |
| GAP-EH-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-EH-02 | Error Handling | Exception details leaked to clients | Critical | Small |
| GAP-EH-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-EH-04 | Error Handling | No Feign error handling (fund-transfer, utility-payment) | High | Medium |
| GAP-TE-01 | Testing | Minimal test coverage | High | Large |
| GAP-TE-02 | Testing | No integration tests | High | Large |
| GAP-TE-03 | Testing | No contract tests | Medium | Large |
| GAP-TE-04 | Testing | Context-load tests fail without infra | Medium | Small |
| GAP-SE-01 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-SE-02 | Security | No input validation | Critical | Medium |
| GAP-SE-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SE-04 | Security | Downstream services have no auth | High | Medium |
| GAP-SE-05 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SE-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-AD-01 | API Design | Inconsistent URL patterns | Low | Small |
| GAP-AD-02 | API Design | Raw ResponseEntity without generics | Low | Small |
| GAP-AD-03 | API Design | No API versioning strategy | Medium | Medium |
| GAP-AD-04 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-AD-05 | API Design | OpenAPI uses wrong starter (webflux vs webmvc) | Medium | Small |
| GAP-OB-01 | Observability | No structured logging | Medium | Medium |
| GAP-OB-02 | Observability | Actuator not fully configured | Low | Small |
| GAP-OB-03 | Observability | No Prometheus metrics endpoint | Medium | Small |
| GAP-OB-04 | Observability | Distributed tracing gaps | Low | Small |
| GAP-RE-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RE-02 | Resilience | No retry policies | Medium | Small |
| GAP-RE-03 | Resilience | No timeout configuration | High | Small |
| GAP-RE-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RE-05 | Resilience | Non-atomic fund transfer operation | Critical | Large |
| GAP-RE-06 | Resilience | Balance calculation bug | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 8 |
| Medium | 13 |
| Low | 6 |
| **Total** | **32** |
