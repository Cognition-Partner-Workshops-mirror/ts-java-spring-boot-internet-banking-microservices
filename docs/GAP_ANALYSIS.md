# Engineering Standards Gap Analysis

This document compares the current codebase against industry best practices across seven engineering dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Gradle Multi-Project Build
**Severity: Medium | Effort: Medium**

Each service has an independent `build.gradle` with duplicated plugin versions, dependency management blocks, and Spring Cloud BOM declarations. There is no root `settings.gradle` or `build.gradle` to unify versions and shared configuration. This leads to version drift risk (e.g., one service upgrading Spring Boot while others stay behind).

### 1.2 Duplicated Code Across Services
**Severity: High | Effort: Large**

Significant code duplication exists across services with no shared library:
- `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig` — copied identically into user-service, fund-transfer-service, and utility-payment-service.
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` — duplicated in user-service, fund-transfer-service, and utility-payment-service.
- `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` — duplicated across all 4 data-bearing services with minor inconsistencies (some use `@Builder`, some use constructor).
- `CustomFeignClientConfiguration` — duplicated in fund-transfer and utility-payment services.

### 1.3 Inconsistent Package Structure
**Severity: Low | Effort: Small**

Package layouts are similar but not identical:
- Core banking uses `repository/` at the top level; user-service uses `model/repository/`.
- Feign clients are in `service/rest/client/` (fund-transfer) vs. `service/rest/` (user-service, utility-payment).
- Configuration classes vary: `configuration/feign/` (user-service) vs. `configuration/` (others).

### 1.4 Mapper Instantiation Anti-Pattern
**Severity: Low | Effort: Small**

Mappers are instantiated with `new` directly in service classes (e.g., `private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans or using a mapping framework like MapStruct. This bypasses Spring's dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400
**Severity: Critical | Effort: Medium**

The `GlobalExceptionHandler` in every service maps **all** exceptions — including `EntityNotFoundException` and generic `Exception` — to `400 Bad Request`. This violates HTTP semantics:
- `EntityNotFoundException` should return `404 Not Found`.
- Unexpected exceptions should return `500 Internal Server Error`.
- `InsufficientFundsException` arguably warrants `422 Unprocessable Entity` or `409 Conflict`.

### 2.2 Inconsistent Error Response Format
**Severity: High | Effort: Small**

The generic `Exception` handler returns a raw string (`"Exception occur inside API " + e`) instead of the structured `ErrorResponse` object. This means clients receive two different response shapes depending on the error type. Additionally, the raw exception `toString()` may leak internal implementation details (stack traces, class names, SQL queries).

### 2.3 No Validation Error Handling
**Severity: High | Effort: Medium**

There are no `@Valid` annotations on any `@RequestBody` parameters, no Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Min`, etc.) on request DTOs, and no `MethodArgumentNotValidException` handler. Invalid input (null amounts, empty account numbers) will propagate to business logic or the database layer before failing.

### 2.4 Exception Hierarchy Inconsistencies
**Severity: Medium | Effort: Small**

The `SimpleBankingGlobalException` base class has both `@AllArgsConstructor` and a manual constructor, and it shadows `Throwable.message` with its own `message` field. The `ErrorResponse` class uses `@Builder` in some services but a plain constructor in others (fund-transfer-service).

### 2.5 No Error Codes Documentation
**Severity: Low | Effort: Small**

`GlobalErrorCode` contains only 2 codes (`BANKING-CORE-SERVICE-1000`, `BANKING-CORE-SERVICE-1001`). Other services throw exceptions without any error codes. There is no error code catalog for clients.

---

## 3. Testing

### 3.1 Minimal Test Coverage — Only Core Banking Service Has Tests
**Severity: Critical | Effort: Large**

Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `UserServiceTest`, `TransactionServiceTest`). The remaining 5 services have only empty Spring application context tests (`@SpringBootTest` class with no test methods). There are zero tests for:
- `internet-banking-user-service` (Keycloak integration, user registration logic)
- `internet-banking-fund-transfer-service` (fund transfer orchestration)
- `internet-banking-utility-payment-service` (payment orchestration)
- API Gateway routing and security configuration
- Any controller-layer tests (no `@WebMvcTest` or MockMvc usage)

### 3.2 No Integration Tests
**Severity: High | Effort: Large**

There are no integration tests that verify actual database operations, Feign client calls, or end-to-end flows. The existing tests mock all dependencies, which means bugs in JPA queries, Flyway migrations, Feign serialization, or Spring wiring are undetectable.

### 3.3 No Contract Tests Between Services
**Severity: High | Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between services. Changes to core-banking-service API could break fund-transfer or utility-payment services without detection until runtime.

### 3.4 No Test Coverage Reporting
**Severity: Medium | Effort: Small**

No code coverage tools (JaCoCo, SonarQube) are configured. There is no coverage threshold enforcement in the build.

---

## 4. Security

### 4.1 Hardcoded Database Credentials in Docker Compose and Source
**Severity: Critical | Effort: Medium**

Multiple credentials are hardcoded in version-controlled files:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `mysql/Dockerfile`)
- MySQL app user: `javatodev_development` / `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin: `admin` / `password` (in `docker-compose.yml`)
- Keycloak DB: `keycloak` / `password` (in `docker-compose.yml`)
- Test credentials in README: `ib_admin@javatodev.com` / `5V7huE3G86uB`

These should use environment variables or a secrets manager.

### 4.2 No Input Validation
**Severity: Critical | Effort: Medium**

No Bean Validation annotations exist on any request DTO. An attacker could submit:
- Negative transfer amounts
- Null or empty account numbers
- Extremely large amounts (potential overflow)
- SQL injection via string fields (mitigated by JPA parameterization but still a defense-in-depth concern)

### 4.3 CSRF Disabled Without Justification
**Severity: Medium | Effort: Small**

The API gateway disables CSRF protection (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). This is acceptable for a pure API (no browser form submissions with cookies), but should be explicitly documented.

### 4.4 No Rate Limiting
**Severity: High | Effort: Medium**

There is no rate limiting on any endpoint. The fund transfer and payment endpoints could be abused for denial-of-service or rapid fund extraction.

### 4.5 Keycloak Singleton Not Thread-Safe
**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern (`if (keycloakInstance == null)`). In a multi-threaded environment, this could create multiple Keycloak client instances with potential resource leaks.

### 4.6 No Authorization Beyond Authentication
**Severity: High | Effort: Medium**

The API gateway authenticates users but does not enforce any role-based access control. All authenticated users can access all endpoints, including admin-only operations like `PATCH /bank-users/update/{id}` (user approval). There are no `@PreAuthorize`, `@Secured`, or role checks.

### 4.7 Sensitive Data in Logs
**Severity: Medium | Effort: Small**

Controllers log full request objects using `toString()` (e.g., `log.info("Creating user with {}", request.toString())`). The `User` DTO includes the `password` field, which would be written to logs in plaintext.

### 4.8 No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured. Vulnerabilities in transitive dependencies would go undetected.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics
**Severity: Medium | Effort: Small**

Most controller methods return `ResponseEntity` without type parameters (e.g., `public ResponseEntity getBankAccount(...)` instead of `ResponseEntity<BankAccount>`). This loses compile-time type safety and produces incomplete OpenAPI documentation.

### 5.2 No API Versioning Strategy
**Severity: Medium | Effort: Medium**

While endpoints use `/api/v1/`, there is no documented versioning strategy, no mechanism for deprecation, and no consideration for backward compatibility. URL-based versioning is in place but not formalized.

### 5.3 Inconsistent Endpoint Naming
**Severity: Low | Effort: Small**

- Core banking uses underscores in path variables: `{account_number}`, `{account_name}`
- User service uses `{id}` and `{identification}`
- Fund transfer uses `POST /api/v1/transfer` (no sub-path) while core banking uses `POST /api/v1/transaction/fund-transfer`
- Mixed pluralization: `/bank-users` vs. `/user` vs. `/transfer`

### 5.4 No Pagination Metadata in Responses
**Severity: Medium | Effort: Small**

List endpoints accept Spring `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (total count, total pages, current page). Clients have no way to know if more data exists.

### 5.5 No Filtering or Sorting Documentation
**Severity: Low | Effort: Small**

Pageable parameters (`page`, `size`, `sort`) work implicitly via Spring Data but are not documented in the OpenAPI spec or controller annotations.

### 5.6 OpenAPI/Swagger Configuration Incomplete
**Severity: Medium | Effort: Small**

While `springdoc-openapi-starter-webflux-ui` is included as a dependency and basic `@Tag` and `@Operation` annotations exist, there are no `@ApiResponse` annotations documenting error responses, and the raw `ResponseEntity` return types prevent automatic schema generation. The wrong Springdoc artifact is used — data-bearing services use Spring MVC (not WebFlux), so `springdoc-openapi-starter-webmvc-ui` should be used instead.

---

## 6. Observability

### 6.1 No Structured Logging
**Severity: Medium | Effort: Medium**

Services use default Logback text format. There is no JSON-structured logging, no MDC correlation IDs, and no standard log format across services. This makes log aggregation and searching difficult in production.

### 6.2 Health Checks Not Customized
**Severity: Low | Effort: Small**

Spring Boot Actuator is included and provides default `/actuator/health`, but there are no custom health indicators for critical dependencies (MySQL connectivity, Keycloak reachability, Eureka registration status).

### 6.3 No Prometheus Metrics Endpoint
**Severity: Medium | Effort: Small**

The README lists Prometheus as part of the tech stack, but `micrometer-registry-prometheus` is not in any `build.gradle`. The `/actuator/prometheus` endpoint is not available.

### 6.4 Actuator Endpoints Publicly Exposed
**Severity: High | Effort: Small**

The API gateway explicitly permits all actuator endpoints without authentication (`exchanges.pathMatchers("/actuator/**").permitAll()`). This exposes potentially sensitive information (environment variables, configuration properties, heap dumps) to unauthenticated users.

### 6.5 Distributed Tracing Configuration Incomplete
**Severity: Medium | Effort: Small**

While tracing dependencies are included, there is no explicit sampling rate configuration, no custom span names for business operations, and the Zipkin URL is only configured via the external config server (not visible in the repo).

### 6.6 No Alerting or Monitoring Configuration
**Severity: Low | Effort: Medium**

There are no Grafana dashboards, alert rules, or SLO definitions. Monitoring is limited to Zipkin trace viewing.

---

## 7. Resilience

### 7.1 No Circuit Breakers
**Severity: Critical | Effort: Medium**

Feign clients make synchronous calls to core-banking-service with no circuit breaker (Resilience4j, Hystrix). If core-banking-service is down or slow, all upstream services will hang indefinitely and exhaust their thread pools, causing a cascading failure.

### 7.2 No Retry Policies
**Severity: High | Effort: Small**

There are no retry configurations for Feign clients. Transient network failures (DNS hiccups, connection resets) will immediately fail the request instead of retrying.

### 7.3 No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeouts are configured for Feign clients, database connections, or Keycloak calls. Default timeouts may be excessively long (or infinite), leading to thread starvation under load.

### 7.4 No Fallback Behavior
**Severity: Medium | Effort: Medium**

When core-banking-service is unavailable, there is no fallback behavior (cached responses, graceful degradation, meaningful error messages). Feign exceptions propagate up as-is and are caught by the generic `Exception` handler returning a 400 with raw exception details.

### 7.5 Non-Atomic Fund Transfer Operations
**Severity: Critical | Effort: Large**

The fund transfer flow spans two services without distributed transaction support:
1. Fund-transfer-service saves a `PENDING` record.
2. Core-banking-service debits source and credits destination within a local `@Transactional`.
3. Fund-transfer-service updates to `SUCCESS`.

If step 3 fails (network error after step 2 completes), the money has moved in core banking but the fund-transfer record stays `PENDING`. There is no compensating transaction, saga pattern, or idempotency mechanism to handle partial failures. The same risk applies to utility payments.

### 7.6 No Idempotency Keys
**Severity: High | Effort: Medium**

Transaction endpoints accept no idempotency key. If a client retries a fund transfer due to a timeout, the transfer could be executed twice, resulting in double-debit.

### 7.7 Balance Calculation Bug
**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`, `availableBalance` is set by subtracting from `actualBalance` **after** `actualBalance` has already been modified:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
This double-subtracts: if the original balance is 200 and the transfer is 100, `actualBalance` becomes 100, then `availableBalance` becomes 0 (instead of 100). This bug exists in both fund transfer and utility payment code paths.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | All errors return HTTP 400 | Error Handling | Critical | Medium |
| 3.1 | Only core-banking-service has tests | Testing | Critical | Large |
| 4.1 | Hardcoded credentials in source | Security | Critical | Medium |
| 4.2 | No input validation | Security | Critical | Medium |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.5 | Non-atomic cross-service transactions | Resilience | Critical | Large |
| 7.7 | Balance calculation bug (double-subtract) | Resilience | Critical | Small |
| 1.2 | Duplicated code, no shared library | Code Organization | High | Large |
| 2.2 | Inconsistent error response format | Error Handling | High | Small |
| 2.3 | No Bean Validation on request DTOs | Error Handling | High | Medium |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | High | Large |
| 4.4 | No rate limiting | Security | High | Medium |
| 4.5 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.6 | No role-based access control | Security | High | Medium |
| 6.4 | Actuator endpoints publicly exposed | Observability | High | Small |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.6 | No idempotency keys | Resilience | High | Medium |
| 1.1 | No Gradle multi-project build | Code Organization | Medium | Medium |
| 2.4 | Exception hierarchy inconsistencies | Error Handling | Medium | Small |
| 3.4 | No test coverage reporting | Testing | Medium | Small |
| 4.3 | CSRF disabled without documentation | Security | Medium | Small |
| 4.7 | Sensitive data in logs | Security | Medium | Small |
| 4.8 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without generics | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Medium | Medium |
| 5.4 | No pagination metadata in responses | API Design | Medium | Small |
| 5.6 | OpenAPI/Swagger misconfigured | API Design | Medium | Small |
| 6.1 | No structured logging | Observability | Medium | Medium |
| 6.3 | No Prometheus metrics endpoint | Observability | Medium | Small |
| 6.5 | Tracing configuration incomplete | Observability | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 2.5 | No error codes documentation | Error Handling | Low | Small |
| 5.3 | Inconsistent endpoint naming | API Design | Low | Small |
| 5.5 | No filtering/sorting documentation | API Design | Low | Small |
| 6.2 | Health checks not customized | Observability | Low | Small |
| 6.6 | No alerting/monitoring config | Observability | Low | Medium |
