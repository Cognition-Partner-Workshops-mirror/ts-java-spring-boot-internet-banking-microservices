# Engineering Standards Gap Analysis

## Methodology

Each gap is evaluated against industry best practices for Java/Spring Boot microservice architectures. Gaps are rated by:

- **Severity:** Critical / High / Medium / Low
- **Effort:** Small (< 1 day) / Medium (1–3 days) / Large (3+ days)

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

**Severity: Medium | Effort: Medium**

Each service is a standalone Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no root-level `settings.gradle` that aggregates them into a multi-module build. This means:
- No single command to build/test all services
- Gradle wrapper is duplicated 7 times
- Spring Boot and Spring Cloud versions are repeated in every `build.gradle`

**Current state:** 7 independent Gradle projects with identical boilerplate.

### 1.2 Massive Code Duplication Across Services

**Severity: High | Effort: Large**

The following classes are copy-pasted identically (or near-identically) across 3–4 services with no shared library:

| Duplicated Class | Services |
|-----------------|----------|
| `BaseMapper<E, D>` | core-banking, fund-transfer, user, utility-payment |
| `AuditAware` | fund-transfer, user, utility-payment |
| `AuditConfig` | fund-transfer, user, utility-payment |
| `AuditorAwareConfig` | fund-transfer, user, utility-payment |
| `ApiRequestContext` | fund-transfer, user, utility-payment |
| `ApiRequestContextHolder` | fund-transfer, user, utility-payment |
| `AppAuthUserFilter` | fund-transfer, user, utility-payment |
| `SimpleBankingGlobalException` | all 4 business services |
| `ErrorResponse` | all 4 business services |
| `GlobalExceptionHandler` | all 4 business services |

**Impact:** Bug fixes or improvements must be applied to every copy independently, creating maintenance risk and inconsistency.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package organization varies across services:
- Core banking: `model.mapper`, `repository` (top-level)
- Fund transfer: `model.mapper`, `model.repository`, `service.rest.client`
- User service: `model.mapper`, `model.repository`, `service.rest`, `configuration.feign`, `configuration.keycloak`
- Utility payment: `model.mapper`, `repository` (top-level), `service.rest`

Repository packages are inconsistently placed at `repository` vs `model.repository`.

### 1.4 Mappers Instantiated Manually (Not Spring Beans)

**Severity: Low | Effort: Small**

All mapper instances (`BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are created via `new` in service classes rather than being managed as Spring beans. This bypasses dependency injection and makes testing harder.

```java
// Current pattern in every service
private UserMapper userMapper = new UserMapper();
```

### 1.5 No Shared Library / Common Module

**Severity: High | Effort: Large**

There is no `common` or `shared` module for cross-cutting concerns (DTOs, exceptions, filters, audit, mappers). Each service reinvents its own copies.

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400 for Everything

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` has a catch-all that returns HTTP 400 for *all* unhandled exceptions, including 500-class errors:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

**Issues:**
- Internal server errors (NPE, DB connection failures) are masked as client errors (400)
- Exception details including stack traces leak to the client (`"Exception occur inside API " + e`)
- No structured error response — returns a raw string instead of `ErrorResponse`

### 2.2 Entity Not Found Returns 400 Instead of 404

**Severity: High | Effort: Small**

`EntityNotFoundException` extends `SimpleBankingGlobalException`, which is caught by the global handler and always returns HTTP 400 (Bad Request). It should return HTTP 404 (Not Found).

### 2.3 Inconsistent Error Response Formats

**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` returns `ErrorResponse { code, message }` — structured
- Generic `Exception` handler returns a plain string — unstructured
- No timestamp, request path, or trace ID in error responses

### 2.4 Fund Transfer ErrorResponse Uses Constructor Instead of Builder

**Severity: Low | Effort: Small**

The fund-transfer-service's `GlobalExceptionHandler` constructs `ErrorResponse` via `new ErrorResponse(code, message)` but the class uses `@Builder`. Other services use `ErrorResponse.builder()`. This inconsistency can cause compilation issues since `@Builder` doesn't always generate an all-args constructor.

### 2.5 No Validation Exception Handling

**Severity: Medium | Effort: Small**

There is no handler for `MethodArgumentNotValidException` or `ConstraintViolationException`. Since no `@Valid` annotations exist (see Section 4.1), this is currently academic but will be needed once validation is added.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Files | Real Tests | Notes |
|---------|-----------|------------|-------|
| core-banking-service | 4 | 3 service test classes + 1 context load | Good: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` with ~20 test methods |
| internet-banking-api-gateway | 1 | 0 (context load only) | No tests for routing, security, or filter logic |
| internet-banking-config-server | 1 | 0 (context load only) | |
| internet-banking-fund-transfer-service | 1 | 0 (context load only) | No tests for `FundTransferService` orchestration logic |
| internet-banking-service-registry | 1 | 0 (context load only) | |
| internet-banking-user-service | 1 | 0 (context load only) | No tests for user registration flow, Keycloak integration |
| internet-banking-utility-payment-service | 1 | 0 (context load only) | No tests for payment orchestration logic |

**5 of 7 services have zero meaningful tests.** Only `core-banking-service` has unit tests for its service layer.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that:
- Test REST controllers with `@WebMvcTest` or `MockMvc`
- Test repositories with `@DataJpaTest`
- Test the full application with `@SpringBootTest` + test containers
- Verify Feign client behavior

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. Changes to core-banking-service's API can silently break fund-transfer and utility-payment services.

### 3.4 Context Load Tests Likely Fail

**Severity: Medium | Effort: Small**

The `@SpringBootTest` context-load tests in most services will fail because they require:
- A running Config Server (bootstrap configuration)
- A running Eureka Server (Eureka client auto-registration)
- A running MySQL database (JPA auto-configuration)
- A running Keycloak instance (user-service)

These tests should either disable external dependencies or use test profiles.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No request DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Email`, etc.). No controllers use `@Valid` or `@Validated`. Examples:

- `FundTransferRequest`: No validation that `amount > 0`, no null checks on `fromAccount`/`toAccount`
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `account`
- `User` (registration): No email format validation, no password strength requirements

A client can submit `{ "amount": -1000 }` and the system will process it.

### 4.2 Hardcoded Credentials in Source Control

**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:

| Location | Credential |
|----------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | PostgreSQL password: `password` |
| `docker-compose/mysql/privileges.sql` | App DB password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

### 4.3 Keycloak Client Singleton Is Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized, non-volatile static singleton pattern:

```java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

In a multi-threaded servlet container, this can result in multiple instances being created or partially constructed objects being returned.

### 4.4 No CORS Configuration

**Severity: Medium | Effort: Small**

The API gateway has no explicit CORS configuration. If a web frontend is added, cross-origin requests will be blocked.

### 4.5 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the gateway's security configuration (`http.csrf().disable()`). This is standard for stateless JWT APIs but should be documented as an intentional decision.

### 4.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

There is no rate limiting at the API gateway or service level. Financial APIs are high-value targets for abuse.

### 4.7 Actuator Endpoints Publicly Exposed

**Severity: Medium | Effort: Small**

All actuator endpoints (`/actuator/**`) are permitted without authentication across all services. This can expose sensitive operational data (environment variables, configuration properties, health details).

### 4.8 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP dependency-check, Snyk, or similar tool is configured in the build. No GitHub Dependabot configuration exists.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Almost all controller methods return raw `ResponseEntity` without generic type parameters:

```java
public ResponseEntity getBankAccount(...) { ... }  // Should be ResponseEntity<BankAccount>
```

This loses compile-time type safety and makes OpenAPI documentation less informative. The user-service is the sole exception — it correctly uses `ResponseEntity<User>` and `ResponseEntity<List<User>>`.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

While endpoints include `/v1/` in the path, there is no documented versioning strategy, no content-type versioning, and no mechanism to run multiple versions simultaneously.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`, discarding pagination metadata (total elements, total pages, current page). Clients cannot implement proper pagination.

```java
// Current: returns raw list, pagination info lost
public List<FundTransfer> readAllTransfers(Pageable pageable) {
    return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
}
```

### 5.4 No Filtering or Search Capabilities

**Severity: Low | Effort: Medium**

List endpoints support pagination via `Pageable` but have no filtering, searching, or sorting parameters (e.g., filter transfers by date range, status, account).

### 5.5 OpenAPI/Swagger Dependency Mismatch

**Severity: Medium | Effort: Small**

The core-banking, fund-transfer, user, and utility-payment services include `springdoc-openapi-starter-webflux-ui` but are **Spring MVC** (not WebFlux) applications. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. Only the API gateway (which is WebFlux-based) should use the webflux variant.

### 5.6 Inconsistent URL Naming

**Severity: Low | Effort: Small**

- Core banking uses underscores: `/bank-account/{account_number}`, `/util-account/{account_name}`
- Path variable names use `snake_case` in annotations but `camelCase` in Java: `@PathVariable("account_number") String accountNumber`
- Some endpoints use abbreviations inconsistently: `util-account` vs `utility-payment`

### 5.7 No HATEOAS / Hypermedia Links

**Severity: Low | Effort: Large**

REST responses contain no links to related resources. This is acceptable for most APIs but worth noting for completeness.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request details; others do not
- Log messages contain `.toString()` calls on request objects which can leak sensitive data (passwords, account numbers)
- No structured logging format (JSON) — all services use default Spring Boot plaintext logging
- No correlation ID / trace ID in log messages (despite Zipkin being configured)

```java
// Logs potentially sensitive data
log.info("Creating user with {}", request.toString());
```

### 6.2 No Custom Health Indicators

**Severity: Low | Effort: Small**

Services rely on default Spring Boot Actuator health checks. There are no custom health indicators for:
- Database connectivity verification
- Keycloak availability (user-service)
- Feign client target availability
- Config server reachability

### 6.3 No Metrics Beyond Defaults

**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, there are:
- No custom business metrics (e.g., fund transfers per minute, payment failure rate)
- No Prometheus scrape endpoint configured (despite README mentioning Prometheus)
- No Grafana dashboards

### 6.4 Zipkin Tracing Configuration Is External

**Severity: Low | Effort: Small**

Tracing configuration (sampling probability, Zipkin URL) is entirely in the external config repo. While this is the pattern for Spring Cloud Config, it means tracing behavior can't be verified by inspecting this repository alone.

### 6.5 No Centralized Log Aggregation

**Severity: Medium | Effort: Large**

There is no ELK stack, Loki, or similar centralized logging solution. In a multi-service environment, debugging requires SSHing into individual containers.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service communication uses OpenFeign with no circuit breaker (Resilience4j, Hystrix). If core-banking-service goes down:
- Fund-transfer-service will hang or throw unhandled exceptions
- Utility-payment-service will hang or throw unhandled exceptions
- User-service will fail to register/read users
- No fallback behavior is defined

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

Feign clients have no retry configuration. Transient network errors will cause immediate failures with no recovery attempt.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

Feign clients use default timeout settings with no explicit configuration. Default Feign timeouts are generous (10s connect, 60s read), which means a single slow request can tie up a thread for a long time.

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

There is no fallback behavior defined for any Feign client. Options include:
- Returning cached data
- Returning a degraded response
- Queuing the operation for retry

### 7.5 No Bulkhead Pattern

**Severity: Medium | Effort: Medium**

There is no thread pool isolation or bulkhead pattern. A failure in one downstream service can exhaust the thread pool and cascade to affect all other requests.

### 7.6 Non-Atomic Fund Transfer

**Severity: Critical | Effort: Large**

The fund transfer flow is not atomic across services:

1. Fund-transfer-service saves a local record (`PENDING`)
2. Fund-transfer-service calls core-banking via Feign
3. If the Feign call succeeds, update local record to `SUCCESS`

**If step 3 fails** (e.g., network timeout after core-banking processed the transfer), the local record remains `PENDING` but the money has already moved. There is no:
- Saga pattern / compensation logic
- Idempotency keys to prevent duplicate transfers
- Reconciliation process

### 7.7 Double-Subtraction Bug in Balance Updates

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The `availableBalance` is set to `actualBalance - amount` *after* `actualBalance` has already been reduced by `amount`. This results in `availableBalance = originalBalance - 2 * amount`. The same bug affects the credit side (double-addition).

---

## Summary Table

| # | Category | Gap | Severity | Effort |
|---|----------|-----|----------|--------|
| 1.1 | Code Organization | No multi-module Gradle build | Medium | Medium |
| 1.2 | Code Organization | Massive code duplication across services | High | Large |
| 1.3 | Code Organization | Inconsistent package structure | Low | Small |
| 1.4 | Code Organization | Mappers not Spring beans | Low | Small |
| 1.5 | Code Organization | No shared library | High | Large |
| 2.1 | Error Handling | Generic catch-all returns 400 for all errors | High | Small |
| 2.2 | Error Handling | Entity not found returns 400 instead of 404 | High | Small |
| 2.3 | Error Handling | Inconsistent error response format | Medium | Small |
| 2.4 | Error Handling | ErrorResponse constructor inconsistency | Low | Small |
| 2.5 | Error Handling | No validation exception handling | Medium | Small |
| 3.1 | Testing | Minimal test coverage (5/7 services have zero tests) | Critical | Large |
| 3.2 | Testing | No integration tests | High | Large |
| 3.3 | Testing | No contract tests | Medium | Large |
| 3.4 | Testing | Context load tests likely fail | Medium | Small |
| 4.1 | Security | No input validation | Critical | Medium |
| 4.2 | Security | Hardcoded credentials in source control | Critical | Small |
| 4.3 | Security | Keycloak singleton not thread-safe | Medium | Small |
| 4.4 | Security | No CORS configuration | Medium | Small |
| 4.5 | Security | CSRF disabled without documentation | Low | Small |
| 4.6 | Security | No rate limiting | Medium | Medium |
| 4.7 | Security | Actuator endpoints publicly exposed | Medium | Small |
| 4.8 | Security | No dependency vulnerability scanning | Medium | Small |
| 5.1 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| 5.2 | API Design | No API versioning strategy | Low | Medium |
| 5.3 | API Design | No pagination metadata in responses | Medium | Small |
| 5.4 | API Design | No filtering or search capabilities | Low | Medium |
| 5.5 | API Design | OpenAPI dependency mismatch (webflux vs webmvc) | Medium | Small |
| 5.6 | API Design | Inconsistent URL naming | Low | Small |
| 5.7 | API Design | No HATEOAS | Low | Large |
| 6.1 | Observability | Inconsistent/insecure logging | Medium | Small |
| 6.2 | Observability | No custom health indicators | Low | Small |
| 6.3 | Observability | No custom metrics or Prometheus endpoint | Medium | Medium |
| 6.4 | Observability | Tracing config only in external repo | Low | Small |
| 6.5 | Observability | No centralized log aggregation | Medium | Large |
| 7.1 | Resilience | No circuit breakers | Critical | Medium |
| 7.2 | Resilience | No retry policies | High | Small |
| 7.3 | Resilience | No timeout configuration | High | Small |
| 7.4 | Resilience | No fallback behavior | High | Medium |
| 7.5 | Resilience | No bulkhead pattern | Medium | Medium |
| 7.6 | Resilience | Non-atomic fund transfer (no saga) | Critical | Large |
| 7.7 | Resilience | Double-subtraction bug in balance updates | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 8 |
| Medium | 16 |
| Low | 8 |
| **Total** | **38** |
