# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Root Project
**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `settings.gradle`, `build.gradle`, and Gradle wrapper. There is no root `settings.gradle` or root `build.gradle` to unify them. This means:
- No single `./gradlew build` to build all services.
- Dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, Lombok, MySQL connector, etc.) are duplicated across 6 `build.gradle` files.
- No shared version catalog or BOM for cross-service dependency alignment.

### 1.2 Duplicated Code Across Services
**Severity: High | Effort: Medium**

Significant code is copy-pasted across services with no shared library:
- **Exception handling classes** (`GlobalExceptionHandler`, `SimpleBankingGlobalException`, `ErrorResponse`, `EntityNotFoundException`, `GlobalErrorCode`) are duplicated in core-banking-service, user-service, fund-transfer-service, and utility-payment-service — totaling 4 near-identical copies.
- **`BaseMapper`** interface is duplicated across all 4 business services.
- **`AuditAware`** base class is duplicated in user-service, fund-transfer-service, and utility-payment-service.
- **`ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter`** are duplicated in user-service, fund-transfer-service, and utility-payment-service.

### 1.3 Inconsistent Package Structure
**Severity: Low | Effort: Small**

While all services use `com.javatodev.finance`, sub-package organization varies:
- User Service: `model.repository` vs. Core Banking: `repository` (top-level).
- User Service: `model.rest.response` vs. Fund Transfer: `model.dto.response`.
- User Service Feign: `service.rest.BankingCoreRestClient` vs. Fund Transfer Feign: `service.rest.client.BankingCoreFeignClient`.

### 1.4 Mapper Instantiation Anti-Pattern
**Severity: Low | Effort: Small**

Mappers are instantiated inline rather than injected via Spring:
```java
private UserMapper userMapper = new UserMapper();  // in multiple services
```
This bypasses Spring's lifecycle and makes testing harder. Should be `@Component`-annotated and constructor-injected.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns Plain String
**Severity: Critical | Effort: Small**

All four `GlobalExceptionHandler` implementations have a catch-all `Exception` handler that returns a raw string:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```
Problems:
- **Information leakage**: Full exception details (stack traces, internal class names) are exposed to clients.
- **Always returns 400**: Server errors (NPE, DB connection failures) should return 500, not 400.
- **Inconsistent response shape**: Business exceptions return `ErrorResponse` JSON, but generic exceptions return a plain string, breaking client parsing.

### 2.2 No Validation Error Handling
**Severity: High | Effort: Small**

There are no `@Valid` annotations on any request body and no `MethodArgumentNotValidException` handler. All request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) accept any input without validation. Invalid data (null amounts, empty account numbers) will propagate to the service/repository layer and fail with unclear database or NullPointer exceptions.

### 2.3 Missing Raw-Type Generics on ResponseEntity
**Severity: Medium | Effort: Small**

Most controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`:
```java
public ResponseEntity getBankAccount(...) // should be ResponseEntity<BankAccount>
```
This suppresses compile-time type checking and produces less useful OpenAPI documentation.

### 2.4 Inconsistent ErrorResponse Construction
**Severity: Low | Effort: Small**

Core Banking and User Service use `ErrorResponse.builder()`, while Fund Transfer Service uses `new ErrorResponse(code, message)`. The `ErrorResponse` class itself is duplicated but may have different constructors across services.

---

## 3. Testing

### 3.1 Only Core Banking Service Has Unit Tests
**Severity: Critical | Effort: Large**

Test coverage by service:
| Service | Test Files | Coverage |
|---|---|---|
| core-banking-service | `AccountServiceTest` (6 tests), `TransactionServiceTest` (10 tests), `UserServiceTest` | Service layer only |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` (empty context test) | None |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` (empty context test) | None |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` (empty context test) | None |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` (empty context test) | None |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` (empty context test) | None |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` (empty context test) | None |

5 of 7 services have zero meaningful tests — only Spring Boot context-load tests that likely fail without infrastructure.

### 3.2 No Integration Tests
**Severity: High | Effort: Large**

There are no:
- `@SpringBootTest` integration tests with real HTTP calls.
- Testcontainers-based tests for MySQL or Keycloak.
- End-to-end tests validating cross-service flows.

### 3.3 No Contract Tests
**Severity: High | Effort: Large**

With 3 services depending on Core Banking Service via Feign, there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). API changes in Core Banking could silently break consumers.

### 3.4 No Controller Layer Tests
**Severity: Medium | Effort: Medium**

No `@WebMvcTest` tests exist for any controller. Request/response serialization, path mappings, and HTTP status codes are untested.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code
**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`, `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`.
- `docker-compose/mysql/privileges.sql`: `'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'`.
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`.

Even for development environments, these should be externalized to `.env` files (gitignored) or Docker secrets.

### 4.2 No Input Validation on API Endpoints
**Severity: Critical | Effort: Small**

No `@Valid` / Bean Validation annotations on any request DTO. An attacker could submit:
- Negative transfer amounts.
- Null account numbers.
- Extremely large amounts (no max limit).
- SQL injection via string fields (mitigated by JPA parameterized queries, but defense-in-depth is missing).

### 4.3 CSRF Disabled Without Documentation
**Severity: Medium | Effort: Small**

`SecurityConfiguration` disables CSRF globally: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. For a REST API with Bearer tokens this is acceptable, but it should be documented as an intentional security decision.

### 4.4 Keycloak Singleton Is Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic broken double-check pattern (no synchronization):
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
Under concurrent requests, multiple Keycloak instances could be created, causing resource leaks.

### 4.5 Overly Broad Database Permissions
**Severity: Medium | Effort: Small**

The MySQL application user has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` — full DDL/DML on all databases. Production should use least-privilege grants per service database.

### 4.6 No Rate Limiting
**Severity: Medium | Effort: Medium**

No rate limiting at the API Gateway or any service level. Financial endpoints (fund transfer, payments) are vulnerable to abuse.

### 4.7 Password Handling Concerns
**Severity: High | Effort: Small**

The `User` DTO includes a `password` field that is passed in the request body and logged:
```java
log.info("Creating user with {}", request.toString());
```
Lombok's `@Data` generates `toString()` including all fields, so passwords may appear in logs.

---

## 5. API Design

### 5.1 No API Versioning Strategy
**Severity: Medium | Effort: Medium**

While all endpoints use `/api/v1/`, there is no infrastructure for supporting multiple API versions simultaneously. No versioning via headers, content negotiation, or URL-based multi-version routing.

### 5.2 Inconsistent REST Conventions
**Severity: Medium | Effort: Small**

- `PATCH /api/v1/bank-users/update/{id}` — the `/update` is redundant for a PATCH method.
- `POST /api/v1/bank-users/register` — the `/register` is redundant for a POST to a users collection.
- `GET /api/v1/account/util-account/{account_name}` — inconsistent naming (`util-account` vs `utility-payment`).
- Fund transfer uses `POST /api/v1/transfer` (root) while utility payment uses `POST /api/v1/utility-payment` (root) — different resource naming styles.

### 5.3 Pagination Returns List Instead of Page
**Severity: Medium | Effort: Small**

All paginated endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`, losing total count, page number, and page size metadata. Clients cannot determine if more pages exist.

### 5.4 No Filtering or Sorting Parameters
**Severity: Low | Effort: Medium**

No endpoints support query-based filtering (e.g., filter transfers by date range, status) or explicit sort parameters beyond Spring's default `Pageable` support.

### 5.5 OpenAPI Documentation Incomplete
**Severity: Low | Effort: Small**

While `springdoc-openapi` is included, controllers use raw `ResponseEntity` return types, so generated OpenAPI specs lack proper response schemas. Additionally, `springdoc-openapi-starter-webflux-ui` is used in non-WebFlux services (user-service, fund-transfer-service, core-banking-service are Spring MVC), which is a dependency mismatch.

### 5.6 No Standard Error Response Envelope
**Severity: Medium | Effort: Small**

There is no consistent API response envelope. Success responses return raw DTOs, while error responses return either `ErrorResponse {code, message}` or plain strings, depending on the exception type.

---

## 6. Observability

### 6.1 Inconsistent Logging
**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` logging; others don't (e.g., `UtilityPaymentController` has no `@Slf4j`).
- Log messages include potentially sensitive data (user emails, full request objects including passwords via `toString()`).
- No structured logging format (JSON). Default Spring Boot text logging makes log aggregation difficult.
- No correlation ID propagation in log messages (despite Micrometer tracing being configured).

### 6.2 Health Checks Are Defaults Only
**Severity: Low | Effort: Small**

While `spring-boot-starter-actuator` is included in all services, there are no custom health indicators for:
- Database connectivity.
- Keycloak availability (User Service).
- Core Banking Service availability (from consumer services).
- Feign client health.

### 6.3 No Custom Metrics
**Severity: Medium | Effort: Medium**

No custom business metrics are exposed (e.g., transfer count, payment volume, error rates by type). Only default Micrometer metrics from Spring Boot Actuator are available.

### 6.4 Distributed Tracing May Be Incomplete
**Severity: Low | Effort: Small**

While Zipkin dependencies are present, the `spring-cloud-sleuth` mentioned in the README is no longer used (replaced by Micrometer Tracing in Spring Boot 3.x). Feign client tracing requires `feign-micrometer` (present) but proper span propagation should be verified end-to-end.

### 6.5 No Alerting or Monitoring Configuration
**Severity: Medium | Effort: Medium**

Prometheus is listed in the technology stack but no `prometheus.yml` or scrape configuration exists in the repository. No Grafana dashboards or alerting rules are defined.

---

## 7. Resilience

### 7.1 No Circuit Breakers
**Severity: Critical | Effort: Medium**

All inter-service calls via Feign are direct synchronous calls with no circuit breaker (e.g., Resilience4j). If Core Banking Service goes down:
- Fund Transfer Service will hang on Feign calls until timeout, eventually returning errors.
- User Service registration will fail completely.
- Cascading failures will propagate to the API Gateway.

### 7.2 No Retry Policies
**Severity: High | Effort: Small**

No Feign or Spring Retry configuration exists. Transient network failures (brief connectivity issues, DNS hiccups) will immediately fail the request. For idempotent GET operations, retries would significantly improve reliability.

### 7.3 No Timeout Configuration
**Severity: High | Effort: Small**

No explicit Feign client timeouts are configured (connect timeout, read timeout). Default Feign timeouts may be too long (or infinite), causing threads to be blocked indefinitely during downstream outages.

### 7.4 No Fallback Behavior
**Severity: High | Effort: Medium**

No Feign fallback classes or factory implementations exist. When Core Banking Service is unavailable, consumers return raw Feign exceptions (which are caught by the generic `GlobalExceptionHandler` and returned as 400 errors with exception details leaked).

### 7.5 No Bulkhead / Thread Pool Isolation
**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow downstream service can exhaust the thread pool and block all other requests, including those to different services.

### 7.6 Fund Transfer Is Not Idempotent
**Severity: High | Effort: Medium**

The `POST /api/v1/transfer` endpoint has no idempotency key. If a client retries a failed (but actually processed) transfer due to network issues, the transfer may be executed twice. For financial transactions, this is a significant risk.

### 7.7 Balance Calculation Bug
**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`, the available balance is calculated incorrectly:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been decremented, effectively double-subtracting the amount from `availableBalance`. The same bug exists for credit operations.

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 1 | 1 | 1 | 1 | 4 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 2 | 1 | 3 | 0 | 6 (+1 High) |
| API Design | 0 | 0 | 3 | 2 | 5 (+1 Medium) |
| Observability | 0 | 0 | 3 | 2 | 5 |
| Resilience | 2 | 3 | 1 | 0 | 6 (+1 High) |
| **Total** | **6** | **8** | **12** | **7** | **33** |
