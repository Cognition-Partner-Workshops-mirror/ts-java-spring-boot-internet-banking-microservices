# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### GAP-01: No Shared Library — Duplicated Code Across Services

**Severity:** High | **Effort:** Medium

`BaseMapper`, `AuditAware`, `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`, `AppAuthUserFilter`, `ApiRequestContext`, and `ApiRequestContextHolder` are copy-pasted identically across 3–4 services. Any fix or enhancement must be repeated in every service, increasing maintenance burden and risk of divergence.

**Current State:** Each service has its own copy of these classes with identical implementations.

**Best Practice:** Extract shared classes into a `banking-common` library published to a local Maven repository or included as a Gradle composite build.

### GAP-02: Inconsistent Package Structure Across Services

**Severity:** Medium | **Effort:** Small

Package conventions vary across services:
- **core-banking-service:** `repository/` at top level
- **fund-transfer-service:** `model/repository/` nested under model
- **user-service:** `model/repository/` nested under model
- **utility-payment-service:** `repository/` at top level

Similarly, Feign client packages differ:
- fund-transfer: `service/rest/client/BankingCoreFeignClient`
- user-service: `service/rest/BankingCoreRestClient`
- utility-payment: `service/rest/BankingCoreRestClient`

**Best Practice:** Establish a consistent package structure convention (e.g., `controller/`, `service/`, `repository/`, `model/`, `exception/`, `configuration/`, `client/`) and apply it uniformly.

### GAP-03: No Multi-Module Gradle Root Project

**Severity:** Low | **Effort:** Medium

Each service has an independent Gradle build with no root `settings.gradle` or `build.gradle`. This means:
- No unified build command (`./gradlew build` from root)
- Dependency version management is duplicated across all `build.gradle` files
- No easy way to enforce consistent plugin/dependency versions

**Best Practice:** Create a root `build.gradle` with a `subprojects` block for shared configuration, or at minimum a Gradle version catalog (`libs.versions.toml`).

### GAP-04: Mappers Instantiated Inline Instead of Spring Beans

**Severity:** Low | **Effort:** Small

Mappers (e.g., `FundTransferMapper`, `UserMapper`) are instantiated directly with `new` in service classes rather than managed as Spring beans. This prevents dependency injection, makes testing harder, and is inconsistent with the rest of the Spring-based architecture.

**Current State:**
```java
private FundTransferMapper mapper = new FundTransferMapper();
```

**Best Practice:** Register mappers as `@Component` beans and inject them, or adopt MapStruct for compile-time mapping.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity:** Critical | **Effort:** Small

Every `GlobalExceptionHandler` (identical across 4 services) maps **all** exceptions to `ResponseEntity.badRequest()` (HTTP 400). This includes:
- Entity not found (should be 404)
- Insufficient funds (should be 422 or 409)
- Internal server errors (should be 500)
- Validation errors (should be 400 or 422)

**Current State:**
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Best Practice:** Map exceptions to appropriate HTTP status codes:
- `EntityNotFoundException` → 404
- `InsufficientFundsException` → 422
- `UserAlreadyRegisteredException` → 409
- Unexpected exceptions → 500

### GAP-06: Raw Exception Details Leaked to Clients

**Severity:** High | **Effort:** Small

The catch-all `Exception` handler returns the raw exception including stack trace information to the client: `"Exception occur inside API " + e`. This is a security risk (information disclosure) and poor UX.

**Best Practice:** Return a generic error message to clients. Log the full exception server-side.

### GAP-07: Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

- `SimpleBankingGlobalException` returns structured `ErrorResponse` (code + message)
- Generic `Exception` handler returns a plain String
- Different services have slightly different exception hierarchies (user-service has `EntityNotFoundException`, `InvalidEmailException`, `InvalidBankingUserException`, `UserAlreadyRegisteredException`; other services only have `SimpleBankingGlobalException`)

**Best Practice:** All error responses should use the same JSON structure (e.g., `{ code, message, timestamp, path }`).

### GAP-08: No Feign Error Decoder in Fund Transfer and Utility Payment Services

**Severity:** High | **Effort:** Small

The fund-transfer and utility-payment services include a `CustomFeignClientConfiguration` that is referenced in the `@FeignClient` annotation, but neither service has a `CustomFeignErrorDecoder` class. Only the user-service has one:

- `internet-banking-user-service`: Has `CustomFeignErrorDecoder` class
- `internet-banking-fund-transfer-service`: Has `CustomFeignClientConfiguration` but **no error decoder**
- `internet-banking-utility-payment-service`: Has `CustomFeignClientConfiguration` but **no error decoder**

This means Feign errors from core-banking propagate as raw `FeignException` to the client.

**Best Practice:** Implement a custom `ErrorDecoder` in all Feign-consuming services to translate downstream HTTP errors into appropriate domain exceptions.

---

## 3. Testing

### GAP-09: Minimal Test Coverage — Only Core Banking Has Real Unit Tests

**Severity:** Critical | **Effort:** Large

| Service | Test Files | Real Tests |
|---------|-----------|------------|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` + `ApplicationTests` | 19 test methods |
| internet-banking-api-gateway | `ApplicationTests` (empty contextLoads) | 0 |
| internet-banking-config-server | `ApplicationTests` (empty contextLoads) | 0 |
| internet-banking-fund-transfer-service | `ApplicationTests` (empty contextLoads) | 0 |
| internet-banking-service-registry | `ApplicationTests` (empty contextLoads) | 0 |
| internet-banking-user-service | `ApplicationTests` (empty contextLoads) | 0 |
| internet-banking-utility-payment-service | `ApplicationTests` (empty contextLoads) | 0 |

5 out of 6 application services have **zero meaningful tests**. The `contextLoads()` tests don't even pass standalone because they require Config Server / Eureka / MySQL connections.

**Best Practice:** Each service should have unit tests for service-layer logic, controller tests (MockMvc/WebTestClient), and repository tests (with H2/Testcontainers).

### GAP-10: No Integration Tests

**Severity:** High | **Effort:** Large

There are no integration tests that verify Feign client communication, database operations with real schemas, or end-to-end flows through the API Gateway.

**Best Practice:** Implement integration tests using Testcontainers for MySQL, WireMock for downstream services, and Spring Boot's `@SpringBootTest` with test profiles.

### GAP-11: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

With 3 services making Feign calls to core-banking, there are no contract tests (e.g., Spring Cloud Contract, Pact) to ensure API compatibility between producer and consumer.

**Best Practice:** Implement consumer-driven contract tests to catch breaking API changes early.

### GAP-12: ApplicationTests Require External Dependencies

**Severity:** Medium | **Effort:** Small

The `@SpringBootTest` context-load tests in most services fail in isolation because they attempt to connect to Config Server, Eureka, and MySQL. Test `application.yml` files disable Eureka but still rely on other infrastructure.

**Best Practice:** Configure test profiles that fully mock or disable all external dependencies.

---

## 4. Security

### GAP-13: No Input Validation on Any Request DTOs

**Severity:** Critical | **Effort:** Small

No request DTO across any service uses Jakarta Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Positive`, etc.). This means:
- Null `amount` in fund transfer → `NullPointerException`
- Negative `amount` → Money created from thin air
- Empty `fromAccount` / `toAccount` → Cryptic database errors
- Null `email` in user registration → Keycloak error

**Current State:**
```java
@PostMapping
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest fundTransferRequest) {
    // No @Valid annotation, no validation
}
```

**Best Practice:** Add `@Valid` to all `@RequestBody` parameters and annotate DTO fields with appropriate constraints.

### GAP-14: No Authentication on Downstream Services

**Severity:** Critical | **Effort:** Medium

JWT validation occurs **only at the API Gateway**. Downstream services (core-banking, fund-transfer, user-service, utility-payment) have **no Spring Security configuration** and accept requests from anyone who can reach their ports.

The only "auth" propagation is an `X-Auth-Id` header injected by the Gateway, which downstream services read via `AppAuthUserFilter`. This header is trivially spoofable by anyone with direct network access.

**Best Practice:** Either:
1. Propagate the JWT token downstream and validate it in each service, or
2. Use mutual TLS (mTLS) between services, or
3. At minimum, restrict network access so only the Gateway can reach downstream services

### GAP-15: Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

Credentials are hardcoded in multiple files:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`, `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`
- `docker-compose/mysql/Dockerfile`: `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`
- `docker-compose/mysql/privileges.sql`: `CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'`
- `test application.yml` files: Database credentials
- `user-service test config`: Keycloak `client-secret: e8548d56-d743-45ef-8655-063c9cd96759`

**Best Practice:** Use environment variables, Docker secrets, or Vault for all credentials. Never commit secrets to version control.

### GAP-16: Overly Broad Database Permissions

**Severity:** High | **Effort:** Small

The `privileges.sql` grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` to the application user. This gives the application DDL privileges (`CREATE`, `ALTER`, `DROP`) on all databases.

**Best Practice:** Grant only `INSERT, UPDATE, DELETE, SELECT` on specific schemas needed by each service. Use separate database users per service.

### GAP-17: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

There is no OWASP Dependency Check, Snyk, or Dependabot configuration. Gradle dependencies are pinned but never scanned for known CVEs.

**Best Practice:** Add the OWASP Dependency Check Gradle plugin or enable GitHub Dependabot.

### GAP-18: CSRF Disabled Without Documentation

**Severity:** Low | **Effort:** Small

The API Gateway `SecurityConfiguration` disables CSRF:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While acceptable for a pure REST API, this should be documented with rationale.

---

## 5. API Design

### GAP-19: Raw `ResponseEntity` Without Type Parameters

**Severity:** Medium | **Effort:** Small

All controller methods return untyped `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`:

```java
public ResponseEntity readFundTransfers(Pageable pageable) { ... }
```

This loses compile-time type safety and prevents OpenAPI from auto-generating accurate response schemas.

**Best Practice:** Use typed response entities: `ResponseEntity<List<FundTransfer>>`.

### GAP-20: Wrong OpenAPI Dependency — WebFlux UI on MVC Services

**Severity:** Medium | **Effort:** Small

Four MVC-based services (core-banking, fund-transfer, user-service, utility-payment) include:
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

These services use Spring MVC (`spring-boot-starter-web`), not WebFlux. The correct dependency is `springdoc-openapi-starter-webmvc-ui`. The WebFlux dependency may cause classpath conflicts or Swagger UI to not render.

**Best Practice:** Use `springdoc-openapi-starter-webmvc-ui` for MVC services and `webflux-ui` only for reactive services (API Gateway).

### GAP-21: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

While endpoints use `/api/v1/`, there is no versioning strategy documented or enforced. No mechanism for running v1 and v2 side by side.

**Best Practice:** Document the versioning strategy (URL path, header, or content negotiation) and establish a deprecation policy.

### GAP-22: Pagination Responses Lack Metadata

**Severity:** Medium | **Effort:** Small

Paginated endpoints return `List<T>` instead of a wrapper with pagination metadata:

```java
public List<FundTransfer> readAllTransfers(Pageable pageable) {
    return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
}
```

The `Page` object is discarded — clients receive no information about total pages, total elements, or current page.

**Best Practice:** Return `Page<T>` or a custom wrapper with `{ content, totalElements, totalPages, currentPage, pageSize }`.

### GAP-23: No Filtering or Searching Capabilities

**Severity:** Low | **Effort:** Medium

List endpoints only support pagination (via Spring's `Pageable`). There is no ability to filter transfers by date range, status, account, amount range, etc.

**Best Practice:** Add query parameters for common filters (e.g., `?status=SUCCESS&fromDate=2024-01-01`).

### GAP-24: Inconsistent Endpoint Naming

**Severity:** Low | **Effort:** Small

- User service: `/api/v1/bank-users` (hyphenated, includes domain prefix)
- Fund transfer: `/api/v1/transfer` (singular)
- Utility payment: `/api/v1/utility-payment` (singular, hyphenated)
- Core banking user: `/api/v1/user` (singular)

**Best Practice:** Use consistent plural nouns (e.g., `/api/v1/users`, `/api/v1/transfers`, `/api/v1/payments`).

---

## 6. Observability

### GAP-25: No Structured Logging

**Severity:** Medium | **Effort:** Small

Services use Lombok's `@Slf4j` with plain text log messages:
```java
log.info("Sending fund transfer request {}" + request.toString());
```

Note the string concatenation bug above (`{}` is never interpolated because `+` is used instead of `,`).

No JSON logging format is configured, making log aggregation and parsing difficult.

**Best Practice:** Configure Logback with JSON encoder (e.g., `logstash-logback-encoder`) for structured logging. Fix the string concatenation patterns.

### GAP-26: No Custom Health Check Indicators

**Severity:** Low | **Effort:** Small

While `spring-boot-starter-actuator` is included in all services, there are no custom `HealthIndicator` implementations. Services don't report health based on database connectivity, Keycloak availability, or downstream service health.

**Best Practice:** Implement custom `HealthIndicator` beans for critical dependencies (database, Keycloak, downstream services).

### GAP-27: No Metrics Endpoints or Custom Metrics

**Severity:** Medium | **Effort:** Medium

While Micrometer is on the classpath (via actuator), there are no custom metrics. Key business metrics are not tracked:
- Fund transfer volume/amount
- Payment processing latency
- Failed transaction rates
- User registration rates

**Best Practice:** Add custom Micrometer counters/timers for business-critical operations. Expose Prometheus endpoint.

### GAP-28: Distributed Tracing Not Verified

**Severity:** Low | **Effort:** Small

Zipkin dependencies are included and the Docker Compose deploys a Zipkin server, but there is no configuration for sampling rate, trace propagation, or verification that traces are actually being sent. The Spring Cloud Config server would need to include `management.zipkin.tracing.endpoint` configuration.

**Best Practice:** Verify traces appear in Zipkin UI. Configure sampling rate. Add trace IDs to log output via MDC.

### GAP-29: Sensitive Data Logged

**Severity:** High | **Effort:** Small

`request.toString()` is logged in multiple controllers and services, which may include account numbers, amounts, and user identification in plain text logs:

```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Utility payment processing {}", paymentRequest.toString());
```

**Best Practice:** Implement a sanitized `toString()` or use structured logging with field-level masking for sensitive data (account numbers, amounts, PII).

---

## 7. Resilience

### GAP-30: No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

There are no circuit breakers (Resilience4j, Hystrix) on any Feign client. If core-banking-service becomes unavailable or slow:
- Fund-transfer-service blocks indefinitely on the Feign call
- Utility-payment-service blocks indefinitely
- User-service blocks indefinitely
- All thread pools exhaust → cascading failure across the entire platform

**Best Practice:** Add Resilience4j circuit breakers to all Feign clients with defined thresholds, fallback methods, and recovery strategies.

### GAP-31: No Retry Policies

**Severity:** High | **Effort:** Small

No retry configuration for transient failures (network blips, temporary unavailability). Feign calls fail immediately on first error.

**Best Practice:** Configure Resilience4j retry with exponential backoff for idempotent read operations. Be cautious with retries on write operations (requires idempotency keys).

### GAP-32: No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit timeout configuration for Feign clients, database connections, or HTTP requests. Default timeouts may be infinite or very long, causing thread pool exhaustion under failure conditions.

**Best Practice:** Configure explicit timeouts at all integration points:
- Feign client connection timeout: 5s
- Feign client read timeout: 10s
- Database connection timeout: 5s
- HikariCP connection pool max wait: 10s

### GAP-33: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

When downstream calls fail, the entire request fails with an unhandled exception. There is no graceful degradation:
- No fallback responses for read operations
- No queuing of writes for later retry
- No partial success handling

**Best Practice:** Define fallback methods for Feign clients that return cached data, default responses, or enqueue requests for async retry.

### GAP-34: No Idempotency Keys for Financial Transactions

**Severity:** Critical | **Effort:** Medium

Fund transfer and utility payment POST endpoints have no idempotency mechanism. If a client retries a request (due to timeout or network error), the same transfer/payment can be processed multiple times, causing double charges.

**Best Practice:** Accept an `Idempotency-Key` header, store it with the transaction, and return the existing result on duplicate requests.

### GAP-35: No Compensation/Rollback on Feign Failure

**Severity:** Critical | **Effort:** Large

In both fund-transfer and utility-payment services, the flow is:
1. Save entity with PENDING/PROCESSING status
2. Call core-banking via Feign
3. On success, update status to SUCCESS

If the Feign call fails at step 2, the entity remains in PENDING/PROCESSING **forever**. There is:
- No retry mechanism
- No compensation transaction
- No dead-letter queue
- No scheduled cleanup job
- No saga pattern implementation

**Best Practice:** Implement the Saga pattern with compensation actions, or use a transactional outbox pattern with a message broker for reliable state transitions.

### GAP-36: Double-Deduction Bug in Balance Calculations

**Severity:** Critical | **Effort:** Small

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

After line 1, `actualBalance` is already reduced. Line 2 subtracts `amount` again from the already-reduced `actualBalance`, resulting in `availableBalance` being debited twice. The same bug exists in `utilPayment()`.

**Best Practice:** Fix the calculation:
```java
BigDecimal newBalance = fromBankAccountEntity.getActualBalance().subtract(amount);
fromBankAccountEntity.setActualBalance(newBalance);
fromBankAccountEntity.setAvailableBalance(newBalance);
```

### GAP-37: No Rate Limiting

**Severity:** Medium | **Effort:** Medium

The API Gateway has no rate limiting configuration. All endpoints are exposed without throttling, making the system vulnerable to abuse or accidental overload.

**Best Practice:** Configure Spring Cloud Gateway rate limiter (e.g., `RequestRateLimiter` filter with Redis-backed `RedisRateLimiter`).

---

## Summary Table

| ID | Gap | Category | Severity | Effort |
|----|-----|----------|----------|--------|
| GAP-01 | No shared library — duplicated code | Code Organization | High | Medium |
| GAP-02 | Inconsistent package structure | Code Organization | Medium | Small |
| GAP-03 | No multi-module Gradle root | Code Organization | Low | Medium |
| GAP-04 | Mappers not Spring beans | Code Organization | Low | Small |
| GAP-05 | All errors return HTTP 400 | Error Handling | Critical | Small |
| GAP-06 | Raw exception details leaked | Error Handling | High | Small |
| GAP-07 | Inconsistent error response format | Error Handling | Medium | Small |
| GAP-08 | No Feign error decoder (2 services) | Error Handling | High | Small |
| GAP-09 | Minimal test coverage (5/6 services have 0 tests) | Testing | Critical | Large |
| GAP-10 | No integration tests | Testing | High | Large |
| GAP-11 | No contract tests | Testing | Medium | Large |
| GAP-12 | ApplicationTests require external deps | Testing | Medium | Small |
| GAP-13 | No input validation on DTOs | Security | Critical | Small |
| GAP-14 | No auth on downstream services | Security | Critical | Medium |
| GAP-15 | Hardcoded credentials in source | Security | Critical | Small |
| GAP-16 | Overly broad database permissions | Security | High | Small |
| GAP-17 | No dependency vulnerability scanning | Security | Medium | Small |
| GAP-18 | CSRF disabled without documentation | Security | Low | Small |
| GAP-19 | Raw ResponseEntity without type params | API Design | Medium | Small |
| GAP-20 | Wrong OpenAPI dependency (WebFlux on MVC) | API Design | Medium | Small |
| GAP-21 | No API versioning strategy | API Design | Medium | Medium |
| GAP-22 | Pagination lacks metadata | API Design | Medium | Small |
| GAP-23 | No filtering/searching capabilities | API Design | Low | Medium |
| GAP-24 | Inconsistent endpoint naming | API Design | Low | Small |
| GAP-25 | No structured logging | Observability | Medium | Small |
| GAP-26 | No custom health indicators | Observability | Low | Small |
| GAP-27 | No custom metrics | Observability | Medium | Medium |
| GAP-28 | Distributed tracing not verified | Observability | Low | Small |
| GAP-29 | Sensitive data logged | Observability | High | Small |
| GAP-30 | No circuit breakers | Resilience | Critical | Medium |
| GAP-31 | No retry policies | Resilience | High | Small |
| GAP-32 | No timeout configuration | Resilience | High | Small |
| GAP-33 | No fallback behavior | Resilience | Medium | Medium |
| GAP-34 | No idempotency keys | Resilience | Critical | Medium |
| GAP-35 | No compensation/rollback on failure | Resilience | Critical | Large |
| GAP-36 | Double-deduction bug in balance calc | Resilience | Critical | Small |
| GAP-37 | No rate limiting | Resilience | Medium | Medium |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 10 |
| High | 9 |
| Medium | 13 |
| Low | 5 |

### Effort Distribution

| Effort | Count |
|--------|-------|
| Small | 21 |
| Medium | 12 |
| Large | 4 |
