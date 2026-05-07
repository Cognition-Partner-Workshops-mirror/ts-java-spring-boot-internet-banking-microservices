# Engineering Standards Gap Analysis

This document compares the current codebase against industry best practices across seven engineering dimensions. Each gap is rated by **Severity** and **Remediation Effort**.

> **Severity**: Critical = production risk / security vulnerability; High = significant maintainability or reliability concern; Medium = non-trivial quality issue; Low = polish / best-practice nicety.
>
> **Effort**: Small = < 1 day per service; Medium = 1-3 days; Large = 1+ week.

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Severity | Effort |
|----------|--------|
| Medium | Medium |

Each of the 7 services has its own standalone `build.gradle` and `settings.gradle`. There is no root-level Gradle wrapper or parent build. This means:
- Dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0) are duplicated in every `build.gradle`.
- Plugin versions are duplicated.
- No single command to build all services.

**Best practice**: A Gradle multi-project build with a root `settings.gradle` including all subprojects and a `buildSrc` or convention plugin for shared configuration.

### 1.2 Duplicated Code Across Services

| Severity | Effort |
|----------|--------|
| High | Medium |

Significant code duplication exists across services:
- **`BaseMapper`** interface (identical in 4 services).
- **`AuditAware`** base class (identical in 3 services).
- **`AppAuthUserFilter`**, **`ApiRequestContext`**, **`ApiRequestContextHolder`** (identical in 3 services).
- **`SimpleBankingGlobalException`**, **`ErrorResponse`**, **`GlobalExceptionHandler`** (nearly identical in 4 services).
- **`CustomFeignClientConfiguration`** (duplicated in fund-transfer and utility-payment services).
- **`AuditConfig`** / **`AuditorAwareConfig`** (duplicated in 3 services).

**Best practice**: Extract shared code into a `common` or `shared-library` module.

### 1.3 Inconsistent Package Structure

| Severity | Effort |
|----------|--------|
| Low | Small |

Most services follow `com.javatodev.finance.{controller|service|model|exception|configuration}`, but there are minor inconsistencies:
- core-banking-service uses `repository` at root level; user-service uses `model.repository`.
- utility-payment-service uses `model.rest.request` / `model.rest.response`; fund-transfer-service uses `model.dto.request` / `model.dto.response`.
- user-service has `configuration.feign`, `configuration.filter`, `configuration.keycloak`; other services have flat `configuration`.

### 1.4 Mapper Instantiation Outside DI

| Severity | Effort |
|----------|--------|
| Low | Small |

Mappers are instantiated directly as field initializers (`private UserMapper userMapper = new UserMapper()`) rather than being Spring beans. This bypasses dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns HTTP 400 for Everything

| Severity | Effort |
|----------|--------|
| Critical | Small |

All four `GlobalExceptionHandler` implementations have:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Issues**:
- Every unhandled exception (including 500-class server errors) returns HTTP **400 Bad Request**.
- The exception's `toString()` is returned to the client, potentially **leaking stack traces, class names, and internal details**.
- No structured error response body for the generic handler (returns a plain string).

### 2.2 Inconsistent Error Response Format

| Severity | Effort |
|----------|--------|
| High | Small |

- `SimpleBankingGlobalException` handlers return `ErrorResponse { code, message }`.
- The generic `Exception` handler returns a raw `String`.
- No HTTP status code in the response body.
- No timestamp, path, or request correlation ID.

### 2.3 Missing HTTP Status Code Differentiation

| Severity | Effort |
|----------|--------|
| High | Small |

- `EntityNotFoundException` returns HTTP 400 instead of **404 Not Found**.
- `InsufficientFundsException` returns HTTP 400, which is defensible but could be **422 Unprocessable Entity**.
- There is no **409 Conflict** for `UserAlreadyRegisteredException`.
- No **500 Internal Server Error** path for unexpected failures.

### 2.4 No Feign Error Decoder in Fund-Transfer and Core Services

| Severity | Effort |
|----------|--------|
| Medium | Small |

- user-service has `CustomFeignErrorDecoder` but fund-transfer-service and utility-payment-service do not.
- Feign 4xx/5xx errors from core-banking-service will throw generic `FeignException` with raw HTTP status, bypassing the exception hierarchy.

### 2.5 `SimpleBankingGlobalException` Overrides `message` Field

| Severity | Effort |
|----------|--------|
| Medium | Small |

`SimpleBankingGlobalException` declares its own `private String message` field while also extending `RuntimeException` which has its own `message` via `Throwable`. The `@AllArgsConstructor` sets the field but not the `Throwable.message`, causing `getMessage()` to return `null` in some code paths while the Lombok getter returns the field value.

---

## 3. Testing

### 3.1 Tests Only in core-banking-service

| Severity | Effort |
|----------|--------|
| Critical | Large |

- **core-banking-service**: 3 test classes with ~20 unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest). Reasonable coverage for the service layer.
- **All other services**: Only have empty `ApplicationTests` context-load tests that will fail without infrastructure (config server, Eureka, MySQL).
- **No tests at all** for: user-service business logic, fund-transfer-service, utility-payment-service, API gateway security configuration.

### 3.2 No Integration Tests

| Severity | Effort |
|----------|--------|
| High | Large |

- No `@SpringBootTest` integration tests that test controllers end-to-end.
- No `@WebMvcTest` / `@WebFluxTest` for controller layer testing.
- No `@DataJpaTest` for repository testing.
- No Testcontainers setup for MySQL or Keycloak integration testing.

### 3.3 No Contract Tests

| Severity | Effort |
|----------|--------|
| Medium | Large |

- No Spring Cloud Contract or Pact consumer/provider tests for the Feign client interfaces.
- Breaking API changes in core-banking-service would not be caught until runtime.

### 3.4 Context-Load Tests Will Fail

| Severity | Effort |
|----------|--------|
| Medium | Small |

The default `*ApplicationTests` in user-service, fund-transfer-service, utility-payment-service, and API gateway attempt to load the full Spring context. They will fail because they require:
- A running Config Server (bootstrap.yml points to `localhost:8090`).
- A running Eureka server.
- A running MySQL instance.

Test application.yml overrides are only present in core-banking-service.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Severity | Effort |
|----------|--------|
| Critical | Small |

- `docker-compose.yml`: MySQL root password `woVERANKliGharym`, Keycloak admin password `password`, PostgreSQL password `password`.
- `docker-compose/mysql/Dockerfile`: `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`.
- `docker-compose/mysql/privileges.sql`: Database user password `oPItyPticIAt`.
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`.

These should use environment variables or Docker secrets.

### 4.2 CSRF Disabled Without Documentation

| Severity | Effort |
|----------|--------|
| Medium | Small |

`SecurityConfiguration` disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While acceptable for a stateless JWT API, this should be documented with a rationale.

### 4.3 No Input Validation

| Severity | Effort |
|----------|--------|
| Critical | Medium |

No `@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, or any Bean Validation annotations on any request DTOs across any service. Examples:
- `FundTransferRequest`: `amount` could be null, zero, or negative.
- `User` registration: `email` is not validated for format, `password` has no strength requirements.
- `UtilityPaymentRequest`: `providerId`, `amount`, `referenceNumber` have no constraints.

### 4.4 Thread-Unsafe Keycloak Singleton

| Severity | Effort |
|----------|--------|
| High | Small |

`KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern on a static field:
```java
if (keycloakInstance == null) {
    keycloakInstance = KeycloakBuilder.builder()...build();
}
```
Under concurrent requests, this could create multiple instances. Should use `@Bean` scope or `synchronized` / `volatile`.

### 4.5 No Rate Limiting

| Severity | Effort |
|----------|--------|
| Medium | Medium |

No rate limiting on any endpoint, including:
- User registration (brute-force risk).
- Fund transfer (potential for abuse).
- Authentication endpoint.

### 4.6 Sensitive Data in Logs

| Severity | Effort |
|----------|--------|
| High | Small |

Multiple controllers log full request objects: `log.info("Creating user with {}", request.toString())`. For `User` objects, this could log passwords. For fund transfers, this logs account numbers and amounts. No log masking is in place.

### 4.7 No Dependency Vulnerability Scanning

| Severity | Effort |
|----------|--------|
| Medium | Small |

No OWASP dependency-check plugin, Snyk, or Dependabot configuration.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics

| Severity | Effort |
|----------|--------|
| Medium | Small |

Most controller methods return raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This:
- Loses compile-time type safety.
- Produces incomplete OpenAPI documentation (response schemas show as `object`).

### 5.2 Inconsistent URL Naming

| Severity | Effort |
|----------|--------|
| Low | Small |

- user-service: `/api/v1/bank-users` (plural with prefix).
- core-banking: `/api/v1/user` (singular, no prefix).
- fund-transfer: `/api/v1/transfer` (verb-like noun).
- utility-payment: `/api/v1/utility-payment` (singular).
- Path variables use `snake_case` (`{account_number}`) mixed with camelCase in Java.

### 5.3 No API Versioning Strategy

| Severity | Effort |
|----------|--------|
| Low | Small |

The URL prefix `/api/v1/` exists but there is no documented versioning strategy, no header-based versioning, and no plan for deprecation.

### 5.4 No Pagination Metadata in Responses

| Severity | Effort |
|----------|--------|
| Medium | Small |

Paginated endpoints accept Spring `Pageable` parameters but return `List<T>` instead of `Page<T>`. Clients receive no information about total elements, total pages, or current page.

### 5.5 Wrong OpenAPI Dependency

| Severity | Effort |
|----------|--------|
| Medium | Small |

Business services (core, user, fund-transfer, utility-payment) use `springdoc-openapi-starter-webflux-ui:2.1.0` but they are **Spring MVC (servlet-based)** applications, not WebFlux. The correct dependency is `springdoc-openapi-starter-webmvc-ui`. This may cause runtime issues or the Swagger UI not loading.

### 5.6 No DELETE or Full CRUD Operations

| Severity | Effort |
|----------|--------|
| Low | Small |

Most resources only support GET and POST. No DELETE, PUT, or full lifecycle management for accounts, users, or transactions.

---

## 6. Observability

### 6.1 No Structured Logging

| Severity | Effort |
|----------|--------|
| High | Medium |

- Logs use unstructured text format.
- No JSON log output for log aggregation tools (ELK, Loki, CloudWatch).
- No MDC correlation ID propagation between services.
- No consistent log format across services.

### 6.2 Actuator Endpoints Not Configured

| Severity | Effort |
|----------|--------|
| Medium | Small |

While `spring-boot-starter-actuator` is included in all services, there is no explicit configuration of:
- Which endpoints are exposed (defaults to only `/actuator/health`).
- Health indicator details (show-details).
- Custom health checks (database, Keycloak, RabbitMQ readiness).
- Info endpoint configuration.

### 6.3 No Prometheus Metrics Endpoint

| Severity | Effort |
|----------|--------|
| Medium | Small |

Prometheus is listed in the technology stack (README) but `micrometer-registry-prometheus` is not in any `build.gradle`. No `/actuator/prometheus` endpoint is available.

### 6.4 Zipkin Configuration Unclear

| Severity | Effort |
|----------|--------|
| Low | Small |

Tracing dependencies are present but the sampling rate, Zipkin endpoint URL, and service name propagation are configured externally via the config server. No local fallback configuration exists.

### 6.5 No Alerting or Monitoring Configuration

| Severity | Effort |
|----------|--------|
| Low | Medium |

No Grafana dashboards, alert rules, or monitoring configuration files are included.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| Critical | Medium |

No Resilience4j or Hystrix circuit breakers on any Feign client call. If core-banking-service goes down:
- fund-transfer-service will cascade-fail with Feign connection timeouts.
- utility-payment-service will cascade-fail.
- user-service (registration flow) will cascade-fail.

All three services will exhaust their thread pools waiting on unresponsive calls.

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| High | Small |

No Spring Retry or Resilience4j retry configuration on Feign clients. Transient network failures will immediately fail the request.

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| High | Small |

No explicit timeout configuration on:
- Feign client connect/read timeouts (defaults vary by HTTP client).
- Database connection pool timeouts.
- Keycloak admin client timeouts.

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| Medium | Medium |

No Feign fallback implementations. When a downstream service is unavailable, the user receives raw Feign exceptions rather than graceful degradation.

### 7.5 No Database Transaction Isolation Guarantees

| Severity | Effort |
|----------|--------|
| Critical | Medium |

The `TransactionService.internalFundTransfer()` method performs debit and credit as separate `save()` calls. While `@Transactional` is present at the class level, there are concerns:
- The balance update uses a read-then-write pattern without optimistic locking (`@Version`), creating a race condition under concurrent transfers.
- `availableBalance` is calculated incorrectly: `fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount))` subtracts `amount` from the already-debited `actualBalance`, effectively double-deducting.

### 7.6 No Idempotency

| Severity | Effort |
|----------|--------|
| High | Medium |

Fund transfers and utility payments have no idempotency keys. If a client retries a failed request (e.g., network timeout after the server processed it), the transaction will be executed twice.

### 7.7 Fund Transfer Not Truly Atomic Across Services

| Severity | Effort |
|----------|--------|
| High | Large |

The fund-transfer-service saves a `PENDING` record, then calls core-banking-service via Feign. If the Feign call succeeds but the subsequent `save(SUCCESS)` fails (e.g., local DB outage), the money is moved in core-banking but the local record remains `PENDING`. There is no saga pattern, compensation logic, or distributed transaction coordination.

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 1 | 2 | 2 | 0 | 5 |
| Testing | 1 | 1 | 2 | 0 | 4 |
| Security | 2 | 2 | 2 | 0 | 6 (+1 Low from 4.7 = 7) |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 0 | 1 | 2 | 2 | 5 |
| Resilience | 2 | 3 | 1 | 0 | 6 (+1 High from 7.7 = 7) |
| **Totals** | **6** | **10** | **13** | **7** | **36** |
