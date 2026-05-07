# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is a standalone Gradle project with its own `settings.gradle` and `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds. This makes it impossible to run a single `./gradlew build` from the root to build all services and leads to duplicated dependency version declarations.

**Evidence:** Six independent `build.gradle` files all declare `springCloudVersion = "2023.0.0"`, `spring-boot:3.2.4`, `mysql-connector-j:8.4.0`, etc.

### 1.2 No Shared Library for Common Code

**Severity: High | Effort: Medium**

Identical classes are duplicated across multiple services:
- `BaseMapper` — identical in core-banking, user-service, fund-transfer, utility-payment
- `AuditAware` — identical in user-service, fund-transfer, utility-payment
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — identical in user-service, fund-transfer, utility-payment
- `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException` — duplicated with minor variations across all four business services

**Evidence:** `AppAuthUserFilter.java` is byte-for-byte identical in three services. `GlobalExceptionHandler` in fund-transfer uses a constructor (`new ErrorResponse(...)`) while the other three use a builder pattern — inconsistency caused by copy-paste.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package placement varies between services:
- `core-banking-service`: `model.mapper`, `repository`
- `user-service`: `model.mapper`, `model.repository`
- `fund-transfer-service`: `model.mapper`, `model.repository`

Feign clients are in different packages:
- `user-service`: `service.rest.BankingCoreRestClient`
- `fund-transfer-service`: `service.rest.client.BankingCoreFeignClient`
- `utility-payment-service`: `service.rest.BankingCoreRestClient`

### 1.4 No Gradle Wrapper Checked In

**Severity: Medium | Effort: Small**

The repository does not include `gradlew` / `gradle/wrapper/`. Builds depend on whatever Gradle version is installed locally, risking version mismatch failures across environments.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns Plain Strings

**Severity: High | Effort: Small**

Every service's `GlobalExceptionHandler.handleException(Exception)` returns a raw string:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This:
- Leaks stack traces and internal details to clients (security risk)
- Returns a plain-text body instead of the structured `ErrorResponse` JSON
- Always returns HTTP 400 regardless of the actual error type (e.g., 500 for NPE, 404 for not-found)

**Evidence:** `GlobalExceptionHandler.java` lines 23-28 in all four services.

### 2.2 All Errors Return HTTP 400

**Severity: High | Effort: Small**

Both custom exception handlers (`SimpleBankingGlobalException` and the generic `Exception` catch-all) return `ResponseEntity.badRequest()` (HTTP 400). Entity-not-found should return 404, insufficient-funds should arguably return 422, and unexpected errors should return 500.

**Evidence:** `EntityNotFoundException` extends `SimpleBankingGlobalException` and is caught by the same handler that returns 400.

### 2.3 No Feign Error Propagation Strategy

**Severity: Medium | Effort: Medium**

The `CustomFeignErrorDecoder` exists in user-service but its behavior is not visible in the config server's external configuration. The fund-transfer and utility-payment services use `CustomFeignClientConfiguration` which may or may not include error decoding. Feign errors from the core-banking-service (e.g., 404 account not found) may be swallowed or re-thrown as generic 500s.

### 2.4 Missing Validation on Request Bodies

**Severity: Critical | Effort: Small**

No `@Valid` / `@Validated` annotations on any `@RequestBody` parameter. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on any request DTO:

- `FundTransferRequest`: `fromAccount`, `toAccount`, `amount` — all nullable, no validation
- `UtilityPaymentRequest`: `providerId`, `amount`, `referenceNumber`, `account` — all nullable
- `User` (registration): `email`, `password`, `identification` — all nullable

This means null or empty values pass through to the service layer and cause `NullPointerException` instead of a user-friendly validation error.

**Evidence:** `FundTransferRequest.java`, `UtilityPaymentRequest.java`, `User.java` — no validation annotations present.

---

## 3. Testing

### 3.1 Only core-banking-service Has Meaningful Tests

**Severity: High | Effort: Large**

- **core-banking-service:** 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with 17 unit tests total covering service-layer logic.
- **All other services:** Only boilerplate `*ApplicationTests.java` context-load tests (which likely fail without infrastructure running).

No tests exist for:
- `internet-banking-user-service` service logic (registration, Keycloak integration)
- `internet-banking-fund-transfer-service` service logic (transfer orchestration)
- `internet-banking-utility-payment-service` service logic (payment orchestration)
- Any controller (no `@WebMvcTest` or `MockMvc` tests)
- Any repository (no `@DataJpaTest`)

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist. No use of `@SpringBootTest` with test containers or embedded infrastructure. The context-load tests in non-core services will fail because they require Config Server, Eureka, and MySQL to be running.

### 3.3 No Contract Tests Between Services

**Severity: Medium | Effort: Large**

No Spring Cloud Contract or Pact tests. The Feign client interfaces in fund-transfer, utility-payment, and user-service have no verification that they match the actual core-banking-service API signatures. Breaking changes in core-banking-service would only be caught at runtime.

### 3.4 No Test Coverage Reporting

**Severity: Low | Effort: Small**

No JaCoCo or similar coverage plugin configured. No coverage thresholds enforced.

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose

**Severity: Critical | Effort: Small**

Plain-text passwords in `docker-compose.yml`:
- MySQL root: `woVERANKliGharym`
- Keycloak admin: `admin` / `password`
- Keycloak DB: `keycloak` / `password`

These should use environment variables or Docker secrets.

**Evidence:** `docker-compose.yml` lines 22-23, 42-43, 50-52.

### 4.2 CSRF Disabled on API Gateway

**Severity: Medium | Effort: Small**

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While this is common for stateless REST APIs using Bearer tokens, it should be explicitly documented as an intentional security decision.

### 4.3 No Input Validation (See 2.4)

**Severity: Critical | Effort: Small**

Covered in Section 2.4. Missing validation is also a security concern — it enables injection attacks and denial-of-service via malformed input.

### 4.4 Non-Thread-Safe Keycloak Singleton

**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic lazy-initialization pattern with a `static` field and no synchronization:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

Under concurrent requests, multiple threads could create separate Keycloak instances, causing connection leaks or auth failures.

**Evidence:** `KeycloakProperties.java` lines 25-40.

### 4.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting on any endpoint, including the public user registration endpoint (`/user/api/v1/bank-users/register`). This is a brute-force and abuse vector.

### 4.6 Sensitive Data Logging

**Severity: High | Effort: Small**

Controllers log full `toString()` output of request objects:
```java
log.info("Creating user with {}", request.toString());
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

These may include passwords, account numbers, and financial amounts in plain-text logs.

**Evidence:** `UserController.java:31`, `FundTransferController.java:31`, `TransactionController.java:31`.

### 4.7 Downstream Services Not Secured

**Severity: High | Effort: Medium**

Only the API Gateway enforces JWT authentication. The individual services (core-banking on 8092, user-service on 8083, etc.) have no security configuration and are accessible directly without authentication. In the Docker Compose setup, ports are exposed and services are reachable without going through the gateway.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return untyped `ResponseEntity` (raw type) instead of `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc. This prevents OpenAPI/Swagger from generating accurate response schemas.

**Evidence:** All controllers in core-banking-service and fund-transfer-service use raw `ResponseEntity`.

### 5.2 Inconsistent Endpoint Naming

**Severity: Low | Effort: Small**

- Core banking: `/api/v1/account/bank-account/{account_number}` (snake_case path variable)
- Core banking: `/api/v1/account/util-account/{account_name}` (abbreviated "util")
- User service: `/api/v1/bank-users/register` (hyphenated)
- Fund transfer: `/api/v1/transfer` (singular, no "fund-" prefix)
- Utility payment: `/api/v1/utility-payment` (singular, hyphenated)

No consistent naming convention for path variables (mix of snake_case and camelCase).

### 5.3 No API Versioning Strategy

**Severity: Low | Effort: Medium**

While `/api/v1/` is used, there is no mechanism for version negotiation, header-based versioning, or a documented strategy for introducing v2 endpoints.

### 5.4 Swagger/OpenAPI Misconfigured

**Severity: Medium | Effort: Small**

The Swagger dependency is `springdoc-openapi-starter-webflux-ui` but the business services (core-banking, user, fund-transfer, utility-payment) are **WebMVC** (not WebFlux) applications. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause the Swagger UI to not render correctly.

**Evidence:** `build.gradle` in all four business services declares `springdoc-openapi-starter-webflux-ui:2.1.0`.

### 5.5 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<>` instead of a `Page<>` wrapper. Clients have no way to know total count, total pages, or current page position.

**Evidence:** `UserController.readUsers()`, `FundTransferController.readFundTransfers()`, `UtilityPaymentController.readPayments()` all return `List`.

### 5.6 PATCH Used for State Transitions

**Severity: Low | Effort: Small**

`PATCH /api/v1/bank-users/update/{id}` is used for user approval/rejection. The URL includes the verb "update" which is not RESTful. A more RESTful design would be `PATCH /api/v1/bank-users/{id}` or `POST /api/v1/bank-users/{id}/approve`.

---

## 6. Observability

### 6.1 No Structured Logging

**Severity: Medium | Effort: Medium**

All logging uses unstructured `log.info("message {}", value)` format. No JSON logging configuration. In a microservices environment, structured (JSON) logs are essential for aggregation in ELK/Loki/CloudWatch.

### 6.2 No Health Check Customization

**Severity: Low | Effort: Small**

Spring Boot Actuator is included in all services, providing `/actuator/health`. However, no custom health indicators are configured for downstream dependencies (MySQL connectivity, Keycloak reachability, Config Server availability).

### 6.3 No Metrics Endpoints Beyond Default

**Severity: Low | Effort: Small**

Actuator provides default metrics, but no custom business metrics (e.g., fund transfers per minute, failed payment count, registration rate) are instrumented.

### 6.4 Distributed Tracing Partially Configured

**Severity: Medium | Effort: Small**

Micrometer Tracing + Zipkin reporter dependencies are included in all business services. However:
- The `core-banking-service` does not include `spring-cloud-starter-openfeign` so feign-micrometer may be a no-op.
- Tracing sampling rate and propagation configuration are not visible in the local `application.yml` (may be in the external config repo).

### 6.5 Inconsistent Logging Across Services

**Severity: Low | Effort: Small**

Some controllers use `@Slf4j` and log request details; others don't. `UtilityPaymentController` has no `@Slf4j` and no logging at all. Service-layer logging is sparse.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: High | Effort: Medium**

No Resilience4j or Hystrix circuit breakers configured on any Feign client. If `core-banking-service` goes down, all dependent services (user, fund-transfer, utility-payment) will hang or fail with cascading errors and no graceful degradation.

**Evidence:** No `resilience4j` dependency in any `build.gradle`. No `@CircuitBreaker` annotations.

### 7.2 No Retry Policies

**Severity: Medium | Effort: Small**

No retry configuration on Feign clients. Transient network failures will immediately fail the request instead of retrying.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration on Feign clients or `RestTemplate`. Default timeouts (often infinite or very long) can cause thread pool exhaustion under load.

**Evidence:** No `feign.client.config` or `connectTimeout`/`readTimeout` properties visible.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback methods defined for any Feign client. When downstream services are unavailable, errors propagate directly to the end user.

### 7.5 No Idempotency on Financial Operations

**Severity: Critical | Effort: Medium**

`POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency key. Network retries or duplicate submissions can result in double fund transfers or double payments. This is especially dangerous for financial transactions.

### 7.6 Balance Calculation Bug (Double Subtraction/Addition)

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` was already reduced. This means `availableBalance = original - amount - amount` (double subtraction). The same bug exists for the credit side (double addition) and in `utilPayment()`.

**Evidence:** `TransactionService.java` lines 90-91, 99-100, 63-64.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 1.2 | No shared library for common code | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | No Gradle wrapper | Code Organization | Medium | Small |
| 2.1 | Generic exception handler leaks internals | Error Handling | High | Small |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.3 | No Feign error propagation strategy | Error Handling | Medium | Medium |
| 2.4 | Missing validation on request bodies | Error Handling | Critical | Small |
| 3.1 | Only core-banking has meaningful tests | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | No test coverage reporting | Testing | Low | Small |
| 4.1 | Hardcoded credentials in Docker Compose | Security | Critical | Small |
| 4.2 | CSRF disabled without documentation | Security | Medium | Small |
| 4.3 | No input validation (security impact) | Security | Critical | Small |
| 4.4 | Non-thread-safe Keycloak singleton | Security | High | Small |
| 4.5 | No rate limiting | Security | Medium | Medium |
| 4.6 | Sensitive data in logs | Security | High | Small |
| 4.7 | Downstream services not secured | Security | High | Medium |
| 5.1 | Raw `ResponseEntity` types | API Design | Medium | Small |
| 5.2 | Inconsistent endpoint naming | API Design | Low | Small |
| 5.3 | No API versioning strategy | API Design | Low | Medium |
| 5.4 | Wrong Swagger dependency (WebFlux vs WebMVC) | API Design | Medium | Small |
| 5.5 | No pagination metadata | API Design | Medium | Small |
| 5.6 | Non-RESTful PATCH endpoint | API Design | Low | Small |
| 6.1 | No structured logging | Observability | Medium | Medium |
| 6.2 | No custom health indicators | Observability | Low | Small |
| 6.3 | No custom business metrics | Observability | Low | Small |
| 6.4 | Distributed tracing partially configured | Observability | Medium | Small |
| 6.5 | Inconsistent logging | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.2 | No retry policies | Resilience | Medium | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency on financial operations | Resilience | Critical | Medium |
| 7.6 | Balance calculation bug (double sub/add) | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 5 |
| High | 11 |
| Medium | 12 |
| Low | 8 |
