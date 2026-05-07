# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices across seven dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale:**
- **Critical** — Security risk, data loss risk, or production outage risk
- **High** — Significant quality/reliability concern that should be addressed before production
- **Medium** — Notable deficiency that impacts maintainability or developer experience
- **Low** — Minor improvement opportunity, polish item

**Effort Scale:**
- **Small** — < 1 day, localized change
- **Medium** — 1-3 days, touches multiple files/services
- **Large** — 3+ days, architectural change or cross-cutting concern

---

## 1. Code Organization

### 1.1 No Shared Library / Common Module

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Identical classes are copy-pasted across 3-4 services:
- `BaseMapper` — duplicated in core-banking, fund-transfer, user-service, utility-payment (identical code)
- `AuditAware` — duplicated in fund-transfer, user-service, utility-payment
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — duplicated in fund-transfer, user-service, utility-payment
- `SimpleBankingGlobalException` / `ErrorResponse` / `GlobalExceptionHandler` — duplicated in all 4 business services
- `AuditConfig` / `AuditorAwareConfig` — duplicated in fund-transfer, user-service, utility-payment

**Impact:** Bug fixes or improvements must be applied to every copy. Drift between copies leads to inconsistencies (e.g., the fund-transfer `ErrorResponse` uses constructor while others use builder; user-service `GlobalErrorCode` has different error codes than core-banking).

**Recommendation:** Extract a `banking-common` Gradle module with shared exception classes, mappers, audit infrastructure, and filter code.

### 1.2 No Multi-Project Gradle Build

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Finding:** Each service has an independent `build.gradle` with no root `settings.gradle` or parent build file. No shared dependency version management.

**Impact:** Dependency versions could drift between services (currently aligned but manually maintained). No single command to build all services.

### 1.3 Inconsistent Package Structure

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Package organization varies across services:
- core-banking: `repository/` at top level
- user-service: `model/repository/`
- fund-transfer: `model/repository/`
- utility-payment: `repository/` at top level

Similarly, Feign client location varies:
- fund-transfer: `service/rest/client/`
- user-service: `service/rest/`
- utility-payment: `service/rest/`

Feign configuration also varies:
- fund-transfer: `configuration/CustomFeignClientConfiguration` (sets log level)
- user-service: `configuration/feign/CustomFeignClientConfiguration` (extends FeignClientConfiguration, adds error decoder)
- utility-payment: `configuration/CustomFeignClientConfiguration` (extends FeignClientConfiguration, empty)

### 1.4 Missing `.gitignore` Coverage

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** The `.gitignore` is minimal (6 lines). Build output directories (`build/`) and IDE files (`.DS_Store` is committed) should be excluded. `.DS_Store` file (14KB) is committed to the repository root.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** The `GlobalExceptionHandler` in every service catches generic `Exception.class` and returns a plain string:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This:
- Exposes internal exception details (stack traces, class names) to clients
- Returns inconsistent format (string vs. `ErrorResponse` JSON object)
- Always returns HTTP 400 regardless of the actual error type

**Impact:** Information leakage (security risk), poor client experience, makes debugging harder for API consumers.

### 2.2 All Errors Return HTTP 400

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** Both custom exceptions (`SimpleBankingGlobalException`) and catch-all `Exception` handlers return `ResponseEntity.badRequest()` (HTTP 400). No differentiation between:
- 404 Not Found (entity not found)
- 409 Conflict (user already registered)
- 422 Unprocessable Entity (insufficient funds)
- 500 Internal Server Error (unexpected failures)

### 2.3 No Error Codes in Fund Transfer / Utility Payment Services

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** While core-banking-service and user-service define `GlobalErrorCode` constants, the fund-transfer and utility-payment services do not have error code classes. Their `SimpleBankingGlobalException` uses ad-hoc code values.

### 2.4 Fund Transfer — No Failure Handling

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** In `FundTransferService.fundTransfer()`:
```java
entity.setStatus(TransactionStatus.PENDING);
FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
optFundTransfer.setStatus(TransactionStatus.SUCCESS);
```

If the Feign call fails (network error, core-banking failure), the local entity remains in `PENDING` status forever. No try-catch, no status update to `FAILED`, no retry mechanism. Same issue exists in `UtilityPaymentService`.

### 2.5 Feign Error Decoder Only in User Service

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Only the user-service implements `CustomFeignErrorDecoder` to properly deserialize error responses from core-banking. Fund-transfer and utility-payment services use default Feign error handling, losing structured error information.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding:** Test inventory across all services:

| Service | Test Files | Test Methods | Type |
|---|---|---|---|
| core-banking-service | 4 | ~20 | 3 unit test files + 1 context load |
| internet-banking-user-service | 1 | 1 | Context load only |
| internet-banking-fund-transfer-service | 1 | 1 | Context load only |
| internet-banking-utility-payment-service | 1 | 1 | Context load only |
| internet-banking-api-gateway | 1 | 1 | Context load only |
| internet-banking-config-server | 1 | 1 | Context load only |
| internet-banking-service-registry | 1 | 1 | Context load only |

Only core-banking-service has meaningful unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest). All other services have only the default Spring Boot context load test — and some of those will fail without infrastructure (Keycloak, Config Server, MySQL) since they don't mock dependencies.

### 3.2 No Integration Tests

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding:** No integration tests exist. No tests verify:
- Feign client calls between services
- Database operations with real/embedded databases
- API endpoint behavior end-to-end
- Error propagation across service boundaries

### 3.3 No Contract Tests

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |

**Finding:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between services. OpenFeign clients could break silently if core-banking-service API changes.

### 3.4 Context Load Tests May Fail Without Infrastructure

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Several `@SpringBootTest` context load tests will fail in isolation because they attempt to connect to Config Server, Eureka, or Keycloak. The test `application.yml` files for some services (fund-transfer, user-service, utility-payment) disable Eureka but don't account for all external dependencies.

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** Plaintext credentials in `docker-compose.yml` and `privileges.sql`:
- MySQL root password: `woVERANKliGharym`
- MySQL app user/password: `javatodev_development` / `oPItyPticIAt`
- Keycloak admin: `admin` / `password`
- PostgreSQL: `keycloak` / `password`
- Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` (in test application.yml)
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

**Impact:** Anyone with repo access has full database and admin access. These should be externalized via environment variables or a secrets manager.

### 4.2 No Input Validation

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** No `@Valid` annotations or Jakarta Bean Validation constraints on any request DTOs:
- `FundTransferRequest` — no validation that `fromAccount`, `toAccount` are non-null, `amount` > 0
- `UtilityPaymentRequest` — no validation on any field
- `User` registration — no email format validation at the DTO level (only checked against core-banking data)

No `@NotNull`, `@NotBlank`, `@Positive`, `@Email` annotations anywhere. Null or negative amounts could reach the business logic layer.

### 4.3 CSRF Disabled Without Documentation

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** API Gateway disables CSRF via `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is acceptable for a stateless JWT API but should be documented as a conscious decision.

### 4.4 No Rate Limiting

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** The API Gateway has no rate limiting configuration. The public registration endpoint (`/user/api/v1/bank-users/register`) is exposed without any throttling, making it vulnerable to abuse.

### 4.5 Password Handling Concerns

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** In the User registration flow, the password is:
1. Sent as a plain field in the `User` DTO
2. Logged via `log.info("Creating user with {}", request.toString())` — Lombok's `@Data` includes all fields in `toString()`
3. Transmitted from User Service to Keycloak Admin API

The password field in the `User` DTO should be write-only (excluded from serialization/logging).

### 4.6 Keycloak Singleton Pattern Is Not Thread-Safe

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** `KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This is a classic double-check locking issue. Multiple threads could create duplicate instances during startup.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...) // should be ResponseEntity<BankAccount>
```
Only the user-service `UserController` properly types some responses as `ResponseEntity<User>`.

**Impact:** No compile-time type safety, poor OpenAPI schema generation, IDE support degraded.

### 5.2 No API Versioning Strategy

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** While endpoints use `/api/v1/`, there is no documented versioning strategy, no version negotiation, and no plan for handling breaking changes.

### 5.3 Inconsistent Pagination Response Format

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Paginated endpoints (e.g., `GET /api/v1/transfer`, `GET /api/v1/utility-payment`) accept Spring's `Pageable` parameter but return a raw `List<>`, discarding pagination metadata (total elements, total pages, current page). Clients cannot implement proper pagination UI.

### 5.4 OpenAPI Documentation Present But Misconfigured

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Services include `springdoc-openapi-starter-webflux-ui:2.1.0` dependency, but:
- The **webflux** variant is used in **servlet** (Spring MVC) services — should be `springdoc-openapi-starter-webmvc-ui`
- No global API metadata (title, version, description) configured
- `@Operation` and `@Tag` annotations are present on controllers but response schemas are incomplete due to raw `ResponseEntity` types

### 5.5 Non-RESTful Endpoint Naming

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Some endpoints don't follow REST conventions:
- `POST /api/v1/bank-users/register` — action verb in URL; should be `POST /api/v1/bank-users`
- `PATCH /api/v1/bank-users/update/{id}` — action verb in URL; should be `PATCH /api/v1/bank-users/{id}`

### 5.6 No Filtering or Search Capabilities

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Finding:** List endpoints only support pagination via Spring's `Pageable`. No filtering by date range, status, account number, or amount — standard requirements for banking transaction history.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:**
- Logging levels are inconsistent: some methods use `log.info` for routine operations, others don't log at all
- String concatenation used in some log statements instead of parameterized logging: `log.info("Sending fund transfer request {}" + request.toString())` — this concatenates regardless of log level and produces malformed output
- Sensitive data logged (user passwords via `toString()`, full request objects)
- No structured logging format (JSON) configured
- No correlation ID / trace ID included in log messages by default

### 6.2 No Custom Health Checks

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** While Spring Boot Actuator is included in all services, no custom health indicators are defined for:
- Database connectivity (default exists but not customized)
- Keycloak availability (user-service)
- Downstream service availability (Feign targets)
- RabbitMQ connectivity (when implemented)

### 6.3 No Metrics Endpoints Beyond Defaults

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Prometheus is listed in the technology stack but no `micrometer-registry-prometheus` dependency is included. No custom business metrics are defined (e.g., transactions per second, transfer amounts, error rates).

### 6.4 Distributed Tracing Dependencies Present But Untested

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Micrometer tracing and Zipkin reporter dependencies are included. The tracing integration should work automatically with Spring Boot 3.x auto-configuration, but there are no tests or verification that trace context is properly propagated through Feign calls and across the gateway.

### 6.5 Actuator Endpoints Publicly Accessible

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** The API Gateway security configuration allows all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

Actuator endpoints can expose sensitive information (environment variables, configuration properties, heap dumps). At minimum, only `/actuator/health` should be public.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** No circuit breaker pattern (e.g., Resilience4j, Spring Cloud Circuit Breaker) is implemented. If core-banking-service becomes slow or unavailable, all upstream services (fund-transfer, utility-payment, user) will:
- Block threads waiting for Feign timeout
- Cascade the failure to the API Gateway
- Eventually exhaust thread pools

### 7.2 No Retry Policies

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** No retry configuration for Feign clients. Transient network failures (brief DNS hiccups, container restarts) will immediately fail the request. Spring Retry or Resilience4j retry could handle these gracefully.

### 7.3 No Timeout Configuration

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No explicit timeout configuration for:
- Feign clients (connection timeout, read timeout) — defaults to no timeout in some versions
- Database connections (connection pool timeouts)
- Keycloak Admin Client calls

Without timeouts, a slow dependency can block threads indefinitely.

### 7.4 No Fallback Behavior

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** No fallback methods for any Feign client. If core-banking-service is down:
- Fund transfers fail with unstructured Feign exceptions
- Utility payments fail similarly
- User registration fails
- No graceful degradation or queuing

### 7.5 No Transaction Compensation / Saga Pattern

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |

**Finding:** The fund transfer flow spans two services (fund-transfer-service and core-banking-service) without distributed transaction management:

1. Fund-transfer-service saves entity with PENDING status
2. Calls core-banking-service to execute the transfer
3. If step 2 succeeds but the update to SUCCESS in step 3 fails (network issue, service crash), the local state is inconsistent
4. If step 2 partially succeeds (debit done, credit fails), there's no compensation

No saga pattern, no outbox pattern, no eventual consistency mechanism.

### 7.6 No Database Connection Pooling Configuration

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** No explicit database connection pool configuration (HikariCP settings). All services use Spring Boot defaults, which may not be appropriate for production workloads. No max pool size, connection timeout, or idle timeout configured.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No shared library / common module | Code Organization | Medium | Medium |
| 1.2 | No multi-project Gradle build | Code Organization | Low | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Missing .gitignore coverage | Code Organization | Low | Small |
| 2.1 | Inconsistent error response format | Error Handling | High | Medium |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.3 | No error codes in fund-transfer / utility-payment | Error Handling | Medium | Small |
| 2.4 | Fund transfer — no failure handling | Error Handling | Critical | Medium |
| 2.5 | Feign error decoder only in user service | Error Handling | Medium | Small |
| 3.1 | Minimal test coverage | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context load tests may fail | Testing | Medium | Medium |
| 4.1 | Hardcoded credentials in Docker Compose | Security | Critical | Small |
| 4.2 | No input validation | Security | Critical | Medium |
| 4.3 | CSRF disabled without documentation | Security | Low | Small |
| 4.4 | No rate limiting | Security | Medium | Medium |
| 4.5 | Password handling concerns | Security | High | Small |
| 4.6 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without type parameters | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Small |
| 5.3 | Inconsistent pagination response format | API Design | Medium | Medium |
| 5.4 | OpenAPI misconfigured (webflux in servlet) | API Design | Medium | Small |
| 5.5 | Non-RESTful endpoint naming | API Design | Low | Small |
| 5.6 | No filtering or search capabilities | API Design | Low | Medium |
| 6.1 | Inconsistent logging | Observability | Medium | Medium |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.3 | No Prometheus metrics | Observability | Medium | Medium |
| 6.4 | Tracing untested | Observability | Low | Small |
| 6.5 | Actuator endpoints publicly accessible | Observability | High | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.2 | No retry policies | Resilience | Medium | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No transaction compensation / saga | Resilience | Critical | Large |
| 7.6 | No DB connection pooling config | Resilience | Medium | Small |
