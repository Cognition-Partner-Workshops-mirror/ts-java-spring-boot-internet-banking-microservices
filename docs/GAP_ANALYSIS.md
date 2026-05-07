# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Scale:**
- **Critical** - Security vulnerability or data-loss risk; must fix before production
- **High** - Significant reliability/maintainability risk; fix in next sprint
- **Medium** - Technical debt that slows development; schedule within quarter
- **Low** - Polish item; address opportunistically

**Effort Scale:**
- **Small** - < 1 day per service
- **Medium** - 1-3 days per service
- **Large** - 1+ week per service or cross-cutting

---

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

### GAP-ORG-01: No Shared Library / Multi-Module Build

**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with no shared parent or common library. This results in:
- Duplicated exception classes (`SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ErrorResponse`) across 4 services
- Duplicated `AuditAware` base class across 3 services
- Duplicated mapper base class (`BaseMapper`) across services
- Duplicated `CustomFeignClientConfiguration` / `CustomFeignErrorDecoder`
- No shared DTO contracts for inter-service communication

**Current State:** Each service independently declares its own `build.gradle` with the same Spring Boot/Cloud versions.

**Recommendation:** Create a Gradle multi-module build with a `shared-lib` module for common exceptions, DTOs, and Feign configuration.

### GAP-ORG-02: Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package layout varies across services:
- Core Banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- User Service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `configuration.*`, `exception`
- Fund Transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `service`, `service.rest.client`, `controller`, `configuration`, `exception`

Notable inconsistencies:
- Repository location: `repository` (Core Banking) vs `model.repository` (User, Fund Transfer)
- Feign clients: `service.rest` (User) vs `service.rest.client` (Fund Transfer)
- Configuration: absent in Core Banking, `configuration.*` in User Service

### GAP-ORG-03: Mixed Schema Management Strategies

**Severity: Medium | Effort: Medium**

- Core Banking uses **Flyway** for versioned migrations (proper practice)
- User, Fund Transfer, and Utility Payment services use **JPA auto-DDL** (Hibernate `ddl-auto`)

JPA auto-DDL is unsuitable for production as it cannot handle column renames, data migrations, or rollbacks.

---

## 2. Error Handling

### GAP-ERR-01: All Errors Return HTTP 400

**Severity: High | Effort: Small**

`GlobalExceptionHandler` in every service returns `ResponseEntity.badRequest()` for all exceptions, including:
- Entity not found (should be **404**)
- Insufficient funds (should be **422** or **409**)
- Internal errors (should be **500**)
- Unauthorized (should be **401/403**)

The catch-all `Exception` handler in Core Banking returns a raw string body instead of structured `ErrorResponse`:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This leaks stack trace information and breaks API contract consistency.

### GAP-ERR-02: No Global Exception Handler in Some Services

**Severity: Medium | Effort: Small**

Fund Transfer and Utility Payment services have `GlobalExceptionHandler` stubs but do not handle Feign-specific exceptions (e.g., `FeignException`). When Core Banking returns an error, these services may propagate raw Feign exceptions to clients.

Only the User Service has a `CustomFeignErrorDecoder` to translate Feign errors.

### GAP-ERR-03: Exception Class Hierarchy Issues

**Severity: Low | Effort: Small**

- `SimpleBankingGlobalException` extends `RuntimeException` but also declares a `message` field via Lombok, shadowing `Throwable.message`. The two-arg constructor sets only the Lombok field, not the parent's.
- `EntityNotFoundException` in the User Service reuses the same class name as in Core Banking but extends a different base (service's own copy of `SimpleBankingGlobalException`).
- No correlation ID or timestamp in error responses.

### GAP-ERR-04: No Feign Error Propagation in Fund Transfer / Utility Payment

**Severity: High | Effort: Small**

Fund Transfer and Utility Payment services call Core Banking via Feign but have no `FeignErrorDecoder`. If Core Banking returns an error (e.g., insufficient funds), the raw `FeignException` propagates as a 500 Internal Server Error to the client, losing the original error message and code.

Only the User Service properly decodes Feign errors via `CustomFeignErrorDecoder`.

---

## 3. Testing

### GAP-TEST-01: Only Core Banking Has Unit Tests

**Severity: High | Effort: Large**

Test coverage:
| Service | Test Files | Coverage |
|---------|-----------|----------|
| Core Banking | 4 files (AccountServiceTest, TransactionServiceTest, UserServiceTest, ApplicationTests) | Service-layer unit tests with Mockito |
| User Service | 1 file (ApplicationTests - empty context load) | No business logic tests |
| Fund Transfer | 0 files | No tests |
| Utility Payment | 0 files | No tests |
| API Gateway | 0 files | No tests |
| Service Registry | 1 file (ApplicationTests - empty context load) | No tests |
| Config Server | 0 files | No tests |

### GAP-TEST-02: No Integration Tests

**Severity: High | Effort: Large**

No `@SpringBootTest` integration tests with real databases (H2 is on test classpath but unused). No tests verify:
- Feign client contracts between services
- Database migration correctness
- API Gateway routing rules
- Keycloak integration flows

### GAP-TEST-03: No Contract Tests

**Severity: Medium | Effort: Large**

No Spring Cloud Contract or Pact tests to verify inter-service API compatibility. Given that:
- 3 services depend on Core Banking's API
- DTOs are duplicated (not shared)
- No OpenAPI spec enforcement

Breaking changes in Core Banking could silently break downstream services.

### GAP-TEST-04: No Test Configuration for H2

**Severity: Low | Effort: Small**

H2 is declared as a test dependency in all 4 business services, but test `application.yml` files are either empty or minimal. No test profiles configure H2 as an in-memory database replacement for MySQL.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` |

### GAP-SEC-02: No Input Validation

**Severity: Critical | Effort: Medium**

None of the request DTOs use Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.):

- `FundTransferRequest`: No validation on `fromAccount`, `toAccount`, `amount` (could be null, negative, or zero)
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `referenceNumber`, `account`
- `User` (registration): No validation on `email`, `identification`, `password`
- Controller methods lack `@Valid` annotation on `@RequestBody` parameters

### GAP-SEC-03: Password Handling Concerns

**Severity: High | Effort: Small**

- `User` DTO includes `password` field that is serialized in API responses (no `@JsonProperty(access = WRITE_ONLY)`)
- Password is logged in `createUser`: `log.info("Creating user with {}", request.toString())` - Lombok `@Data` includes all fields in `toString()`
- No password complexity requirements enforced

### GAP-SEC-04: Keycloak Client Uses Static Singleton

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a naive non-thread-safe singleton pattern (`keycloakInstance == null` check without synchronization). In a multi-threaded web server, this could create multiple Keycloak instances or expose partially-constructed objects.

### GAP-SEC-05: No CORS Configuration

**Severity: Medium | Effort: Small**

No explicit CORS configuration found on any service or the API Gateway. Browser-based clients would be blocked by default CORS policy.

### GAP-SEC-06: Internal Services Not Secured

**Severity: High | Effort: Medium**

Only the API Gateway enforces OAuth2 authentication. Internal services (Core Banking, User Service, Fund Transfer, Utility Payment) have no authentication/authorization. Any network-adjacent actor can directly call Core Banking endpoints to transfer funds.

### GAP-SEC-07: No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning configured in build files or CI pipeline.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controllers return raw `ResponseEntity` without generic types:
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)    // should be ResponseEntity<FundTransferResponse>
```

Only User Service controller uses typed `ResponseEntity<User>`. This breaks OpenAPI/Swagger documentation generation and compile-time type safety.

### GAP-API-02: No API Versioning Strategy

**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` prefix but there is no versioning strategy for handling breaking changes:
- No content negotiation versioning
- No header-based versioning
- No plan documented for v2 migration

### GAP-API-03: Inconsistent Pagination Implementation

**Severity: Low | Effort: Small**

All list endpoints accept Spring's `Pageable` parameter but:
- Return `List<T>` instead of `Page<T>`, discarding total count and pagination metadata
- No documented default page size
- No maximum page size enforced

### GAP-API-04: No OpenAPI/Swagger UI Properly Configured

**Severity: Medium | Effort: Small**

`springdoc-openapi-starter-webflux-ui` is declared in build.gradle for Core Banking, User, Fund Transfer, and Utility Payment services. However:
- The `webflux-ui` variant is used in `spring-boot-starter-web` (servlet) services, which is a mismatch; should use `springdoc-openapi-starter-webmvc-ui`
- No custom OpenAPI configuration class to set title, version, description, server URLs
- Swagger annotations (`@Tag`, `@Operation`) are partially applied but have minimal detail

### GAP-API-05: Inconsistent URL Patterns

**Severity: Low | Effort: Small**

- Core Banking: `snake_case` path variables (`{account_number}`, `{account_name}`)
- User Service: positional `{id}` paths
- Mixed resource naming: `/bank-users/register` (verb in URL), `/transfer` (no noun), `/utility-payment` (noun)

### GAP-API-06: No HATEOAS or Hypermedia Links

**Severity: Low | Effort: Medium**

REST responses contain no links for navigation (e.g., self links, related resource links). This is acceptable for internal APIs but limits API discoverability for external consumers.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging

**Severity: Medium | Effort: Small**

- Log levels are not configurable per environment (no log level configuration in config files)
- Sensitive data logged: `request.toString()` in multiple controllers logs full request bodies including passwords
- No structured logging (JSON format) configured
- No MDC (Mapped Diagnostic Context) usage for request correlation beyond Zipkin trace IDs

### GAP-OBS-02: No Health Check Customization

**Severity: Low | Effort: Small**

`spring-boot-starter-actuator` is included in all services, providing `/actuator/health`. However:
- No custom health indicators for database connectivity, Keycloak reachability, or external service availability
- Actuator endpoints may not be exposed (default Spring Boot 3.x only exposes `/actuator/health`)
- No readiness/liveness probes configured for Kubernetes/container orchestration

### GAP-OBS-03: No Metrics Endpoints

**Severity: Medium | Effort: Small**

While `micrometer-tracing-bridge-brave` is included for distributed tracing, there is:
- No Prometheus metrics endpoint configured (`/actuator/prometheus`)
- No custom business metrics (e.g., transfer count, payment amount histograms)
- No Micrometer registry for metrics collection (Prometheus, Datadog, etc.)

### GAP-OBS-04: Zipkin Configuration Not Validated

**Severity: Low | Effort: Small**

Zipkin dependencies are declared but tracing configuration (sample rate, endpoint URL) is delegated to the external config repo. There is no fallback or circuit breaker if Zipkin is unavailable, which could cause startup delays or log noise.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity: High | Effort: Medium**

All inter-service calls via OpenFeign have no circuit breaker protection. If Core Banking Service is down or slow:
- Fund Transfer Service blocks indefinitely on Feign call
- Utility Payment Service blocks indefinitely on Feign call
- User Service blocks indefinitely on Core Banking or Keycloak calls
- Cascading failures propagate through the entire system

No Resilience4j, Hystrix, or Spring Cloud CircuitBreaker dependencies found.

### GAP-RES-02: No Retry Policies

**Severity: High | Effort: Small**

No retry configuration for:
- Feign client calls (transient network errors)
- Database connections
- Keycloak admin API calls

Spring Retry is not included as a dependency.

### GAP-RES-03: No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeouts configured for:
- Feign client connections and read timeouts
- Database connection pool timeouts
- Keycloak HTTP client timeouts
- API Gateway route timeouts

Default Spring Boot timeouts may be too generous (30s+ for connection, no read timeout), allowing slow dependencies to exhaust thread pools.

### GAP-RES-04: No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback mechanisms for degraded operation:
- If Core Banking is down, Fund Transfer returns raw 500 error
- If Keycloak is down, user list endpoint fails entirely (even though local data exists)
- No cached responses or graceful degradation patterns

### GAP-RES-05: No Idempotency Protection

**Severity: High | Effort: Medium**

Fund transfer and utility payment operations have no idempotency keys:
- If a client retries a failed request, the same transfer could be executed twice
- No duplicate detection based on reference numbers or request IDs
- `@Transactional` only protects single-database operations; the two-phase pattern (save PENDING, call Core Banking, update SUCCESS) has no compensation if the update fails after Core Banking succeeds

### GAP-RES-06: Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`, the available balance is set incorrectly:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
//                                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                                        This is ALREADY the reduced balance!
```

The available balance is set to `actualBalance - amount - amount` (double deduction). After a $100 transfer from a $200 account:
- `actualBalance` = $100 (correct)
- `availableBalance` = $0 (incorrect; should be $100)

This same bug exists in `utilPayment()`.

### GAP-RES-07: Transaction Entity Relationship Issue

**Severity: Medium | Effort: Small**

`TransactionEntity` uses `@OneToOne(cascade = CascadeType.ALL)` for the account relationship, but one account can have many transactions. This should be `@ManyToOne`. The `CascadeType.ALL` is also dangerous as it could cascade deletes from transactions to accounts.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No shared library / multi-module build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-03 | Code Organization | Mixed schema management (Flyway vs auto-DDL) | Medium | Medium |
| GAP-ERR-01 | Error Handling | All errors return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Missing exception handlers in some services | Medium | Small |
| GAP-ERR-03 | Error Handling | Exception class hierarchy issues | Low | Small |
| GAP-ERR-04 | Error Handling | No Feign error decoder in Fund Transfer / Utility Payment | High | Small |
| GAP-TEST-01 | Testing | Only Core Banking has unit tests | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | No test configuration for H2 | Low | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-SEC-02 | Security | No input validation on request DTOs | Critical | Medium |
| GAP-SEC-03 | Security | Password exposed in logs and API responses | High | Small |
| GAP-SEC-04 | Security | Keycloak client non-thread-safe singleton | Medium | Small |
| GAP-SEC-05 | Security | No CORS configuration | Medium | Small |
| GAP-SEC-06 | Security | Internal services have no auth | High | Medium |
| GAP-SEC-07 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Medium |
| GAP-API-03 | API Design | Pagination returns List instead of Page | Low | Small |
| GAP-API-04 | API Design | Incorrect OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-API-05 | API Design | Inconsistent URL patterns | Low | Small |
| GAP-API-06 | API Design | No HATEOAS / hypermedia links | Low | Medium |
| GAP-OBS-01 | Observability | Inconsistent / insecure logging | Medium | Small |
| GAP-OBS-02 | Observability | No custom health checks | Low | Small |
| GAP-OBS-03 | Observability | No metrics endpoints (Prometheus) | Medium | Small |
| GAP-OBS-04 | Observability | Zipkin config not validated | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | High | Medium |
| GAP-RES-06 | Resilience | Balance calculation double-deduction bug | Critical | Small |
| GAP-RES-07 | Resilience | Transaction @OneToOne should be @ManyToOne | Medium | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 3 |
| High | 11 |
| Medium | 13 |
| Low | 8 |
| **Total** | **35** |
