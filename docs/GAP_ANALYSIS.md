# Internet Banking Microservices — Gap Analysis

This document assesses the codebase against engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### GAP-1.1: No Shared Library — Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3–4 services with identical or near-identical implementations:

- `BaseMapper<E,D>` interface — duplicated in core-banking, fund-transfer, user-service, utility-payment
- `AuditAware` (MappedSuperclass) — duplicated in fund-transfer, user-service, utility-payment
- `GlobalExceptionHandler` — duplicated in core-banking, fund-transfer, user-service, utility-payment
- `ErrorResponse` — duplicated in fund-transfer, user-service, utility-payment
- `SimpleBankingGlobalException` — duplicated in fund-transfer, user-service, utility-payment
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` — duplicated in fund-transfer, user-service, utility-payment
- `AuditorAwareConfig` / `AuditConfig` — duplicated in fund-transfer, user-service, utility-payment

**Impact**: Any bug fix or improvement must be applied in 3–4 places. Divergence is inevitable over time.

### GAP-1.2: No Multi-Module Gradle Root Project

**Severity: Medium | Effort: Medium**

Each service has an independent `build.gradle` with no shared root `settings.gradle`. This means:
- No unified `./gradlew build` across all services
- Dependency version drift between services (currently aligned, but no enforcement)
- No shared dependency version catalog

### GAP-1.3: Inconsistent Package Structure Across Services

**Severity: Low | Effort: Small**

Package organization varies:
- Fund-transfer: `model.repository`, `model.dto.request`, `model.dto.response`
- User-service: `model.repository`, `model.rest.response`
- Utility-payment: `repository`, `model.rest.request`, `model.rest.response`
- Core-banking: `repository`, `model.dto.request`, `model.dto.response`

---

## 2. Error Handling

### GAP-2.1: All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Small**

`GlobalExceptionHandler` maps every exception (including the catch-all `Exception.class`) to `ResponseEntity.badRequest()`. This means:
- 404 Not Found scenarios (e.g., `EntityNotFoundException`) return 400
- 500 Internal Server Errors return 400
- Clients cannot distinguish error types by HTTP status code

### GAP-2.2: Generic Exception Handler Leaks Internal Details

**Severity: High | Effort: Small**

The catch-all handler returns: `"Exception occur inside API " + e` — this exposes:
- Full exception class names and stack trace information
- Internal implementation details (package names, method chains)
- Potential security-sensitive information

### GAP-2.3: No Consistent Error Response Format

**Severity: Medium | Effort: Small**

- Business exceptions return structured `ErrorResponse` (code + message)
- The catch-all handler returns a raw string
- No correlation ID, timestamp, or path in error responses

### GAP-2.4: Missing Error Handling for Feign Client Failures

**Severity: High | Effort: Medium**

- Fund-transfer and utility-payment services do not handle Feign client exceptions gracefully
- If core-banking-service is unavailable or returns an error, the raw Feign exception propagates to the client
- Only user-service has a `CustomFeignErrorDecoder`; fund-transfer and utility-payment do not

---

## 3. Testing

### GAP-3.1: Minimal Unit Test Coverage

**Severity: Critical | Effort: Large**

- **core-banking-service**: 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) — reasonable coverage of service layer
- **All other 5 services**: Only empty `ApplicationTests` classes (Spring context load test)
- **0 controller/integration tests** across the entire codebase
- No tests for the API Gateway security configuration
- No tests for Feign client behavior, error decoding, or fallback

### GAP-3.2: No Integration or Contract Tests

**Severity: High | Effort: Large**

- No Spring Boot integration tests (`@SpringBootTest` with actual HTTP calls)
- No contract tests (e.g., Spring Cloud Contract or Pact) between Feign clients and providers
- No database integration tests (Testcontainers or similar)

### GAP-3.3: No Test Coverage Reporting

**Severity: Medium | Effort: Small**

- No JaCoCo or similar coverage tool configured in any `build.gradle`
- No coverage thresholds or gates

---

## 4. Security

### GAP-4.1: No Auth on Downstream Services

**Severity: Critical | Effort: Medium**

JWT validation occurs **only at the API Gateway**. Downstream services:
- Have no Spring Security configuration
- Trust the `X-Auth-Id` header blindly — any direct call to a downstream service bypasses all auth
- In a container escape or network compromise scenario, all services are fully open

### GAP-4.2: Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Plaintext credentials in version-controlled files:
- `docker-compose.yml`: MySQL root password `woVERANKliGharym`, Keycloak admin password `password`, PostgreSQL password `password`
- `privileges.sql`: MySQL app user password `oPItyPticIAt`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-4.3: No Input Validation on API Endpoints

**Severity: Critical | Effort: Medium**

- No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` annotations on any request DTOs
- No Bean Validation dependency in any service
- A `FundTransferRequest` with null `fromAccount`, null `toAccount`, or negative `amount` would pass to the service layer
- Same issue on all POST/PATCH endpoints across all services

### GAP-4.4: Keycloak Client Secret in Externalized Config (GitHub)

**Severity: High | Effort: Small**

The Keycloak `client-secret` is stored in the Spring Cloud Config GitHub repository, which is public. This is effectively a hardcoded secret in a public repo.

### GAP-4.5: Overly Broad Database Privileges

**Severity: Medium | Effort: Small**

`privileges.sql` grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` to the application user. The application only needs DML operations on its own schemas.

### GAP-4.6: KeycloakProperties Uses Non-Thread-Safe Singleton

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check-locking-absent lazy singleton (`if (keycloakInstance == null)`) that is not thread-safe. Multiple threads could create separate Keycloak client instances.

---

## 5. API Design

### GAP-5.1: Missing Response Type Parameters on ResponseEntity

**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without type parameters:
```java
public ResponseEntity readUsers(Pageable pageable)  // should be ResponseEntity<List<User>>
```
This prevents compile-time type safety and degrades OpenAPI documentation quality.

### GAP-5.2: No Pagination Metadata in List Responses

**Severity: Medium | Effort: Medium**

All paginated endpoints (`GET /api/v1/user`, `GET /api/v1/transfer`, `GET /api/v1/utility-payment`) return a raw `List<T>`. They accept `Pageable` parameters but discard page metadata (total elements, total pages, page number, page size). Clients cannot implement proper pagination UI.

### GAP-5.3: Wrong OpenAPI Dependency

**Severity: Medium | Effort: Small**

All MVC-based services (core-banking, fund-transfer, user-service, utility-payment) use:
```
springdoc-openapi-starter-webflux-ui:2.1.0
```
They should use `springdoc-openapi-starter-webmvc-ui` since these services run on Spring MVC (Tomcat), not WebFlux (Netty). This may cause Swagger UI to malfunction or not load.

### GAP-5.4: Inconsistent URL Path Conventions

**Severity: Low | Effort: Small**

- Core banking uses `util-account` and `util-payment` (abbreviated)
- Gateway routes use `/payment/**` for utility payments and `/banking-core/**` for core
- Fund-transfer service uses `/api/v1/transfer` (no hyphen)
- No consistent naming convention across services

### GAP-5.5: No API Versioning Strategy Beyond URL Prefix

**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` prefix but there is no strategy documented for handling v2 endpoints, backward compatibility, or deprecation.

---

## 6. Observability

### GAP-6.1: Unstructured Logging

**Severity: Medium | Effort: Medium**

- All services use SLF4J with default logback (plain text format)
- No JSON structured logging configured
- Log statements are inconsistent: some log request objects via `toString()`, others log just IDs
- Sensitive data may be logged (e.g., `request.toString()` on User objects that contain passwords)

### GAP-6.2: No Health Check Endpoints Configured

**Severity: Medium | Effort: Small**

- Spring Boot Actuator is included in all services but no explicit health indicators are configured
- No custom health checks for downstream dependencies (MySQL connectivity, Keycloak reachability, Eureka registration status)
- Actuator endpoints are all permitted in the gateway but their actual exposure (`management.endpoints.web.exposure.include`) depends on externalized config

### GAP-6.3: No Metrics Collection Beyond Defaults

**Severity: Medium | Effort: Medium**

- Micrometer is included for tracing but no custom business metrics are defined
- No counters for transactions processed, transfers completed, payments made
- No histograms for response times per endpoint
- No Prometheus or Grafana integration

### GAP-6.4: Potential Sensitive Data in Logs

**Severity: High | Effort: Small**

Multiple controllers and services log full request objects:
```java
log.info("Creating user with {}", request.toString());  // User DTO contains password field
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```
The user-service `User` DTO includes a `password` field that would be logged in plaintext.

---

## 7. Resilience

### GAP-7.1: No Circuit Breakers

**Severity: High | Effort: Medium**

- No Resilience4j or Hystrix dependencies in any service
- If core-banking-service goes down, fund-transfer and utility-payment services will cascade-fail with Feign timeouts
- No fallback behavior defined for any inter-service call

### GAP-7.2: No Retry Policies

**Severity: Medium | Effort: Small**

- No Spring Retry or Resilience4j Retry configured on Feign clients
- Transient failures (network blips, temporary DB unavailability) result in immediate failure

### GAP-7.3: No Explicit Timeouts on Feign Clients

**Severity: Medium | Effort: Small**

- No `connectTimeout` or `readTimeout` configured in Feign client configuration
- Default timeouts may be too long, leading to thread pool exhaustion under load
- `CustomFeignClientConfiguration` only sets log level to `FULL`, not timeout values

### GAP-7.4: No Idempotency Protection on Write Operations

**Severity: High | Effort: Medium**

- `POST /api/v1/transfer` and `POST /api/v1/utility-payment` can process duplicate transactions if retried
- No idempotency key header or duplicate detection mechanism
- If a client retries due to timeout (but the first request actually succeeded), money is transferred/paid twice

### GAP-7.5: Non-Atomic Transaction Processing

**Severity: High | Effort: Medium**

In `TransactionService.internalFundTransfer()`:
- Sender is debited and saved (`bankAccountRepository.save(fromBankAccountEntity)`)
- If the application crashes before the receiver is credited, funds are lost
- The `@Transactional` annotation wraps the entire service class but the `save()` calls may flush at different points

In `TransactionService.utilPayment()`:
- `availableBalance` is set to `actualBalance - amount` instead of `availableBalance - amount`, creating a double-deduction bug on the available balance

### GAP-7.6: TransactionEntity Uses `@OneToOne` Instead of `@ManyToOne`

**Severity: Medium | Effort: Small**

`TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)` to `BankAccountEntity`. This means:
- Only one transaction can reference an account (incorrect — multiple transactions per account is the norm)
- `CascadeType.ALL` on a read-mostly lookup could cause unintended account modifications

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-1.1 | Code Organization | No shared library — duplicated code across services | High | Medium |
| GAP-1.2 | Code Organization | No multi-module Gradle root project | Medium | Medium |
| GAP-1.3 | Code Organization | Inconsistent package structure across services | Low | Small |
| GAP-2.1 | Error Handling | All errors return HTTP 400 Bad Request | High | Small |
| GAP-2.2 | Error Handling | Generic exception handler leaks internal details | High | Small |
| GAP-2.3 | Error Handling | No consistent error response format | Medium | Small |
| GAP-2.4 | Error Handling | Missing error handling for Feign client failures | High | Medium |
| GAP-3.1 | Testing | Minimal unit test coverage (only core-banking has tests) | Critical | Large |
| GAP-3.2 | Testing | No integration or contract tests | High | Large |
| GAP-3.3 | Testing | No test coverage reporting (JaCoCo) | Medium | Small |
| GAP-4.1 | Security | No auth on downstream services (JWT only at gateway) | Critical | Medium |
| GAP-4.2 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-4.3 | Security | No input validation on API endpoints | Critical | Medium |
| GAP-4.4 | Security | Keycloak client secret in public GitHub config repo | High | Small |
| GAP-4.5 | Security | Overly broad database privileges | Medium | Small |
| GAP-4.6 | Security | KeycloakProperties non-thread-safe singleton | Medium | Small |
| GAP-5.1 | API Design | Missing response type parameters on ResponseEntity | Medium | Small |
| GAP-5.2 | API Design | No pagination metadata in list responses | Medium | Medium |
| GAP-5.3 | API Design | Wrong OpenAPI dependency (webflux-ui on MVC services) | Medium | Small |
| GAP-5.4 | API Design | Inconsistent URL path conventions | Low | Small |
| GAP-5.5 | API Design | No API versioning strategy | Low | Medium |
| GAP-6.1 | Observability | Unstructured logging (no JSON format) | Medium | Medium |
| GAP-6.2 | Observability | No custom health check endpoints | Medium | Small |
| GAP-6.3 | Observability | No custom business metrics | Medium | Medium |
| GAP-6.4 | Observability | Sensitive data (passwords) in logs | High | Small |
| GAP-7.1 | Resilience | No circuit breakers | High | Medium |
| GAP-7.2 | Resilience | No retry policies | Medium | Small |
| GAP-7.3 | Resilience | No explicit Feign client timeouts | Medium | Small |
| GAP-7.4 | Resilience | No idempotency protection on write operations | High | Medium |
| GAP-7.5 | Resilience | Non-atomic transaction processing / double-deduction bug | High | Medium |
| GAP-7.6 | Resilience | TransactionEntity uses @OneToOne instead of @ManyToOne | Medium | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 4 |
| High | 12 |
| Medium | 12 |
| Low | 3 |
| **Total** | **31** |
