# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each service has its own standalone `build.gradle` with duplicated dependency declarations (Spring Boot 3.2.4, Spring Cloud 2023.0.0, Micrometer tracing, etc.). There is no root `settings.gradle` or `build.gradle` to manage shared dependencies, plugin versions, or common configuration.

**Impact**: Version drift risk, duplicated boilerplate, harder upgrades.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across multiple services with minor variations:
- `AuditAware` (fund-transfer, user-service, utility-payment)
- `BaseMapper` (core-banking, fund-transfer, user-service, utility-payment)
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` (all business services)
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` (user-service, utility-payment, fund-transfer)
- `CustomFeignClientConfiguration` (fund-transfer, utility-payment)

**Impact**: Bug fixes must be applied in multiple places; inconsistencies already exist (e.g., `ErrorResponse` uses `@Builder` in some services but constructor in others).

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package naming is mostly consistent (`com.javatodev.finance`) but sub-package conventions vary:
- Core banking: `model.dto`, `model.entity`, `model.mapper`, `repository`
- Fund transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`
- User service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`

The `repository` package is at different nesting levels across services.

### 1.4 Mapper Implementation Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated as non-final fields via `new XxxMapper()` rather than being Spring beans:
```java
private FundTransferMapper mapper = new FundTransferMapper();
```
This prevents injection, makes testing harder, and is inconsistent with the DI pattern used elsewhere.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns 400 for All Errors

**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` catches `Exception.class` and returns `400 Bad Request` with the exception's `toString()` as the body:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Problems**:
- Server errors (500) are misreported as client errors (400)
- Stack traces and internal details leak to clients
- No structured error response for generic exceptions
- `EntityNotFoundException` returns 400 instead of 404

### 2.2 No HTTP Status Code Differentiation

**Severity: High | Effort: Small**

All business exceptions map to `400 Bad Request` regardless of semantics:
- `EntityNotFoundException` → should be `404 Not Found`
- `InsufficientFundsException` → `400` is arguably correct but could be `422 Unprocessable Entity`
- `UserAlreadyRegisteredException` → should be `409 Conflict`
- Generic `Exception` → should be `500 Internal Server Error`

### 2.3 Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` subclasses return structured `ErrorResponse` (`{code, message}`)
- Generic exceptions return a raw string: `"Exception occur inside API " + e`
- No error response envelope with timestamp, path, or request ID for correlation

### 2.4 Raw ResponseEntity Without Type Parameters

**Severity: Low | Effort: Small**

All controllers return raw `ResponseEntity` without generics (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This eliminates compile-time type safety and degrades OpenAPI documentation quality.

### 2.5 Missing Feign Error Decoder

**Severity: High | Effort: Medium**

The user service has a `CustomFeignErrorDecoder` class, but the fund transfer and utility payment services do not. When a Feign call to core banking fails, the default decoder wraps the error in a `FeignException`, which the `GlobalExceptionHandler` catches as a generic `Exception` and returns as `400` with a stack trace.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Classes | Unit Tests | Integration Tests |
|---|---|---|---|
| `core-banking-service` | 4 (1 context + 3 service) | ~15 tests | 0 |
| `internet-banking-api-gateway` | 1 (context load only) | 0 | 0 |
| `internet-banking-config-server` | 1 (context load only) | 0 | 0 |
| `internet-banking-service-registry` | 1 (context load only) | 0 | 0 |
| `internet-banking-fund-transfer-service` | 0 | 0 | 0 |
| `internet-banking-user-service` | 1 (context load only) | 0 | 0 |
| `internet-banking-utility-payment-service` | 0 | 0 | 0 |

Only `core-banking-service` has meaningful unit tests. Fund transfer, user, and utility payment services have **zero** business logic tests.

### 3.2 No Integration or E2E Tests

**Severity: High | Effort: Large**

No `@SpringBootTest` integration tests, no Testcontainers for MySQL, no contract tests between services (Feign client ↔ controller), no API-level tests using MockMvc or WebTestClient.

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

With 3 Feign clients calling the core banking service, there are no Spring Cloud Contract or Pact tests to verify API compatibility. Breaking changes in core-banking-service endpoints would not be caught until runtime.

### 3.4 Context Load Tests May Fail

**Severity: Low | Effort: Small**

The default `@SpringBootTest` context load tests for gateway, config-server, user-service, and utility-payment-service likely fail without a running Config Server and Eureka (no test profiles configured to disable these).

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml`)
- MySQL app user: `javatodev_development` / `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin: `admin` / `password` (in `docker-compose.yml`)
- Keycloak DB: `keycloak` / `password` (in `docker-compose.yml`)
- Keycloak client secret: `0efd3e37-258e-4488-96ae-1dfe34679c9d` (in Postman environment)
- Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` (in README)

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Bean Validation annotations on any request DTOs:
- `FundTransferRequest`: No validation on `fromAccount`, `toAccount`, or `amount` (could be null or negative)
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `referenceNumber`, or `account`
- `User` registration: No email format validation, no password strength requirements

### 4.3 CSRF Disabled Without Justification

**Severity: Medium | Effort: Small**

The API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for stateless JWT-based APIs, this should be documented and ideally paired with proper CORS configuration (none present).

### 4.4 No CORS Configuration

**Severity: Medium | Effort: Small**

No CORS policy is defined anywhere. When a frontend client is added, this will cause cross-origin request failures.

### 4.5 Keycloak Singleton Is Not Thread-Safe

**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic lazy singleton without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
Under concurrent requests, multiple instances could be created (race condition).

### 4.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting at the API Gateway or service level. Financial APIs (fund transfer, payment) are vulnerable to abuse.

### 4.7 Downstream Services Have No Authentication

**Severity: High | Effort: Medium**

Only the API Gateway enforces JWT authentication. Individual services (core-banking, user-service, fund-transfer, utility-payment) have no security configuration. If accessed directly (bypassing the gateway), they are completely open.

### 4.8 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool configured. Dependencies include older versions that may have known CVEs.

---

## 5. API Design

### 5.1 Inconsistent URL Path Naming

**Severity: Medium | Effort: Small**

- User service controller: `/api/v1/bank-users` (plural with hyphen)
- Postman collection references: `/api/v1/bank-user` (singular) — mismatch with actual code
- Core banking user: `/api/v1/user` (singular)
- Path variables use underscores in some places (`{account_number}`) and camelCase in Java

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/` but there's no documented versioning strategy, no content negotiation, and no header-based versioning support.

### 5.3 Missing OpenAPI Documentation

**Severity: Medium | Effort: Small**

The `springdoc-openapi-starter-webflux-ui` dependency is included in some services, and basic `@Tag` and `@Operation` annotations exist on controllers. However:
- Core banking uses `webflux-ui` even though it's a servlet-based (non-reactive) application — this is the wrong dependency
- No `@Schema` annotations on DTOs
- No response code documentation (`@ApiResponse`)
- No aggregated API docs across services

### 5.4 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Paginated endpoints accept `Pageable` but return raw `List<T>` instead of `Page<T>`, losing total count, page number, and page size metadata.

### 5.5 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

Spring Data's `Pageable` supports sorting via query params, but this isn't documented or constrained. No explicit filtering endpoints exist.

### 5.6 Missing DELETE and Full PUT Endpoints

**Severity: Low | Effort: Medium**

The API surface is limited to read and create operations with a single PATCH for user updates. No ability to close accounts, cancel transfers, or delete users.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request entry points; others don't
- Log messages expose full `toString()` of request objects (potential PII exposure: emails, account numbers)
- No structured logging (JSON format) — defaults to plain text
- No MDC context for request correlation
- Fund transfer service has a logging bug: `"Sending fund transfer request {}" + request.toString()` — the `{}` placeholder is concatenated, not substituted

### 6.2 Health Checks Are Default Only

**Severity: Low | Effort: Small**

Spring Boot Actuator provides `/actuator/health`, but no custom health indicators for:
- Database connectivity (auto-configured by Spring Data JPA but could be enhanced)
- Keycloak reachability
- Config Server connectivity
- Downstream service availability

### 6.3 No Metrics Endpoints Exposed

**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, the Prometheus metrics endpoint (`/actuator/prometheus`) is not explicitly configured. The `management.endpoints.web.exposure.include` property is likely set in the external config but not visible in the repo.

### 6.4 No Centralized Log Aggregation

**Severity: Medium | Effort: Medium**

No ELK stack, Loki, or CloudWatch integration. In a Docker Compose environment with 10+ containers, troubleshooting requires accessing individual container logs.

### 6.5 Tracing Dependencies Present but Unverified

**Severity: Low | Effort: Small**

All services include Zipkin/Brave tracing dependencies, but there's no test or verification that trace context propagates correctly across Feign calls, especially through the API Gateway.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No Resilience4j or Hystrix circuit breakers on any Feign client. If `core-banking-service` goes down:
- Fund transfer requests will hang until Feign timeout (default: no timeout configured)
- Cascading failures will propagate to the gateway
- No fallback behavior defined

### 7.2 No Timeout Configuration

**Severity: Critical | Effort: Small**

No explicit timeout configuration for:
- Feign clients (connection timeout, read timeout)
- Database connections (HikariCP pool settings not configured)
- API Gateway route timeouts

Default Feign has no connect/read timeout, meaning requests can hang indefinitely.

### 7.3 No Retry Policies

**Severity: High | Effort: Small**

No Spring Retry or Resilience4j retry configuration on Feign clients. Transient network failures between services will immediately fail the request.

### 7.4 No Bulkhead Isolation

**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow downstream service can exhaust threads and block unrelated requests.

### 7.5 No Graceful Degradation

**Severity: High | Effort: Medium**

No fallback methods defined for any Feign client. When core-banking-service is unavailable, services should degrade gracefully (e.g., return cached data, queue requests for later processing). Currently, they throw unhandled exceptions.

### 7.6 Transaction Atomicity Issues

**Severity: Critical | Effort: Large**

The fund transfer flow spans two services (fund-transfer-service → core-banking-service) without distributed transaction support:
- If the Feign call to core-banking succeeds but the local DB update in fund-transfer-service fails, the core banking ledger is debited but the fund-transfer record remains `PENDING`
- No compensation/saga pattern implemented
- The `@Transactional` on `TransactionService` only covers the local database; it does not provide atomicity across the HTTP call

### 7.7 Double-Deduction Bug in Utility Payment

**Severity: Critical | Effort: Small**

In `TransactionService.utilPayment()`:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
```
Line 2 subtracts from the *already-debited* `actualBalance`, causing `availableBalance` to be double-debited. The same bug exists in `internalFundTransfer()`.

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 1 | 2 | 1 | 1 | 5 |
| Testing | 1 | 1 | 1 | 1 | 4 |
| Security | 2 | 2 | 3 | 0 | 7 |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 0 | 0 | 3 | 2 | 5 |
| Resilience | 4 | 2 | 1 | 0 | 7 |
| **Total** | **8** | **8** | **13** | **9** | **38** |
