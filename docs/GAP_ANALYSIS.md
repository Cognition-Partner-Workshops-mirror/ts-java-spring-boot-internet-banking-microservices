# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by severity and estimated remediation effort.

**Severity Scale:**
- **Critical** — Security risk, data loss potential, or production-blocking issue
- **High** — Significant quality or reliability concern that should be addressed before production
- **Medium** — Important improvement for maintainability and operability
- **Low** — Nice-to-have polish item

**Effort Scale:**
- **Small** — < 1 day per service or < 2 days total
- **Medium** — 2–5 days total
- **Large** — 1–2 weeks total

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build

**Severity:** Medium | **Effort:** Small

Each service has its own standalone `build.gradle` with duplicated dependency versions, plugin declarations, and configurations. There is no root-level `settings.gradle` or `build.gradle` to manage shared configuration.

**Impact:** Version drift between services, duplicated boilerplate, harder dependency upgrades.

**Evidence:** All 6 services independently declare `springCloudVersion = "2023.0.0"`, `springBoot = 3.2.4`, and identical plugin blocks.

---

### GAP-ORG-02: Duplicated Code Across Services

**Severity:** Medium | **Effort:** Medium

The following classes are copy-pasted across 3+ services with identical or near-identical implementations:
- `BaseMapper` (fund-transfer, utility-payment, user-service)
- `AuditAware` (fund-transfer, utility-payment, user-service)
- `SimpleBankingGlobalException` (all 4 business services)
- `ErrorResponse` (all 4 business services)
- `GlobalExceptionHandler` (all 4 business services)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (fund-transfer, utility-payment, user-service)
- `CustomFeignClientConfiguration` (fund-transfer, utility-payment)

**Impact:** Bug fixes must be applied in every copy. Inconsistencies are likely to emerge.

---

### GAP-ORG-03: Inconsistent Package Structure

**Severity:** Low | **Effort:** Small

Package layout varies across services:
- **core-banking-service:** `repository/` at root package level
- **user-service:** `model/repository/` (nested under model)
- **fund-transfer-service:** `model/repository/`
- **utility-payment-service:** `repository/` at root package level

Feign client packages also differ:
- **user-service:** `service/rest/BankingCoreRestClient`
- **fund-transfer-service:** `service/rest/client/BankingCoreFeignClient`
- **utility-payment-service:** `service/rest/BankingCoreRestClient`

**Impact:** Developer confusion, harder onboarding.

---

## 2. Error Handling

### GAP-ERR-01: Raw `ResponseEntity` Return Types Without Generics

**Severity:** High | **Effort:** Medium

All controllers use raw `ResponseEntity` (no type parameter) for return types:

```java
public ResponseEntity fundTransfer(@RequestBody FundTransferRequest request) { ... }
```

**Impact:** No compile-time type safety, Swagger/OpenAPI cannot infer response schema, clients get untyped documentation.

**Affected:** Every controller across all 4 business services.

---

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details

**Severity:** Critical | **Effort:** Small

All `GlobalExceptionHandler` classes catch `Exception.class` and return the full exception in the response body:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest()
        .body("Exception occur inside API " + e);
}
```

**Impact:** Stack traces, class names, and internal details are exposed to external callers. This is a security vulnerability (CWE-209: Information Exposure Through an Error Message).

---

### GAP-ERR-03: All Errors Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

`GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `400 Bad Request`. There is no differentiation between:
- `404 Not Found` (entity not found)
- `409 Conflict` (duplicate registration)
- `422 Unprocessable Entity` (insufficient funds)
- `500 Internal Server Error` (unexpected failures)

**Impact:** Clients cannot programmatically distinguish error types. Violates REST conventions.

---

### GAP-ERR-04: No Error Handling for Feign Client Failures

**Severity:** High | **Effort:** Medium

Only `internet-banking-user-service` has a custom Feign error decoder (`CustomFeignErrorDecoder`). The fund-transfer and utility-payment services have `CustomFeignClientConfiguration` but it only propagates authentication headers — there is **no error decoder** for translating core-banking error responses.

**Impact:** When core-banking returns an error, fund-transfer and utility-payment services get a raw `FeignException` which is caught by the generic exception handler and leaks internal details.

---

## 3. Testing

### GAP-TEST-01: Minimal Test Coverage

**Severity:** High | **Effort:** Large

| Service | Test Files | Type | Coverage |
|---------|-----------|------|----------|
| core-banking-service | 4 files (ApplicationTests + 3 unit tests) | Unit tests with Mockito | Service layer only. No controller tests. |
| internet-banking-user-service | 1 file (ApplicationTests) | Context load only | No meaningful tests |
| internet-banking-fund-transfer-service | 1 file (ApplicationTests) | Context load only | No meaningful tests |
| internet-banking-utility-payment-service | 1 file (ApplicationTests) | Context load only | No meaningful tests |
| internet-banking-api-gateway | 1 file (ApplicationTests) | Context load only | No meaningful tests |
| internet-banking-service-registry | 1 file (ApplicationTests) | Context load only | No meaningful tests |

**Impact:** No verification of controller behavior, request validation, error handling, or Feign integration. Regressions will not be caught.

---

### GAP-TEST-02: No Integration Tests

**Severity:** High | **Effort:** Large

There are no integration tests that verify:
- Database operations with real (or embedded) databases
- Feign client communication between services
- API Gateway routing and security
- End-to-end transaction flows

**Impact:** Service interactions are completely untested.

---

### GAP-TEST-03: No Contract Tests

**Severity:** Medium | **Effort:** Large

No Spring Cloud Contract, Pact, or similar consumer-driven contract testing. Feign clients define their own request/response DTOs that could diverge from the provider's actual API.

**Example:** `FundTransferResponse` in fund-transfer-service uses `@Data` (setters) while core-banking-service uses `@Builder` (no setters). If response shape changes in core-banking, fund-transfer-service would break silently.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

Multiple credentials are hardcoded in committed files:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | Keycloak DB password: `password` |
| `mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `mysql/Dockerfile` | MySQL root password (ENV) |
| `user-service/src/test/resources/application.yml` | Keycloak client secret |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact:** Anyone with repository access has full database and admin credentials.

---

### GAP-SEC-02: No Input Validation

**Severity:** Critical | **Effort:** Medium

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Bean Validation annotations on any request DTOs or controller parameters:

```java
// No validation at all
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest request) { ... }
```

Missing validations:
- Fund transfer amount (could be negative, zero, or null)
- Account numbers (could be null or empty)
- Email format (user registration)
- NIC format
- Provider ID existence

**Impact:** Invalid or malicious data can flow through the system unchecked.

---

### GAP-SEC-03: CSRF Disabled Without Justification

**Severity:** Medium | **Effort:** Small

API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`.

While acceptable for pure API services using Bearer tokens, there is no documentation or comment explaining why. If browser-based clients are ever added, this becomes a vulnerability.

---

### GAP-SEC-04: No Rate Limiting

**Severity:** Medium | **Effort:** Medium

No rate limiting on any endpoint, including:
- User registration (brute-force account creation)
- Fund transfers (potential abuse)
- Authentication endpoints (credential stuffing via Keycloak)

---

### GAP-SEC-05: Sensitive Data in Logs

**Severity:** High | **Effort:** Small

Controllers log full request objects using `toString()`:

```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
```

This logs account numbers and transfer amounts in plaintext. User service logs may also include email addresses.

---

### GAP-SEC-06: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No OWASP Dependency Check, Snyk, or similar tool configured. Dependencies are pinned but not audited for known CVEs.

---

## 5. API Design

### GAP-API-01: No API Versioning Strategy

**Severity:** Medium | **Effort:** Medium

While endpoints use `/api/v1/`, there is no documented versioning strategy, no mechanism for running multiple versions simultaneously, and no deprecation policy.

---

### GAP-API-02: Inconsistent Pagination

**Severity:** Medium | **Effort:** Small

Paginated endpoints accept Spring's `Pageable` parameter but:
- Return raw `List<T>` instead of `Page<T>` (losing total count, page number, etc.)
- No documented query parameters (`page`, `size`, `sort`)
- No maximum page size limit

**Evidence:** `FundTransferService.readAllTransfers()` returns `List<FundTransfer>`, not `Page<FundTransfer>`.

---

### GAP-API-03: Inconsistent Resource Naming

**Severity:** Low | **Effort:** Small

- User service: `/api/v1/bank-user` and `/api/v1/bank-users/register` (plural vs singular mismatch)
- Core banking: `/api/v1/user` (singular)
- Gateway routes use different prefixes for the same concept

---

### GAP-API-04: No OpenAPI Spec Export / Documentation Hosting

**Severity:** Medium | **Effort:** Small

`springdoc-openapi` dependency is included in 4 services, and `@Tag`/`@Operation` annotations exist on controllers, but:
- The dependency is `springdoc-openapi-starter-webflux-ui` even though 3 of 4 services are **servlet-based** (Spring MVC), not WebFlux.
- No verified export of OpenAPI spec files.
- No centralized API documentation portal.

---

### GAP-API-05: No Filtering or Search Capabilities

**Severity:** Low | **Effort:** Medium

List endpoints support only pagination, with no filtering by:
- Account status
- Transaction date range
- Transfer status
- User status

---

## 6. Observability

### GAP-OBS-01: Actuator Endpoints Not Configured

**Severity:** Medium | **Effort:** Small

`spring-boot-starter-actuator` is included in all services, but there is no explicit configuration of which endpoints are exposed. Default exposure is minimal (just `/actuator/health`).

Missing configuration for:
- `/actuator/info`
- `/actuator/prometheus` (metrics)
- `/actuator/env`
- Custom health indicators

---

### GAP-OBS-02: No Structured Logging

**Severity:** Medium | **Effort:** Medium

All services use default Spring Boot logging (Logback with plain text format). No JSON structured logging, no correlation IDs in log output, no log aggregation configuration.

**Impact:** Logs are hard to query and correlate in production environments.

---

### GAP-OBS-03: No Custom Health Checks

**Severity:** Medium | **Effort:** Small

No custom health indicators for:
- Database connectivity (beyond auto-configured DataSource health)
- Keycloak connectivity
- Feign client target availability
- Config server availability

---

### GAP-OBS-04: No Metrics Endpoints

**Severity:** Medium | **Effort:** Small

No Prometheus metrics exporter configured. While `micrometer-tracing-bridge-brave` is included for tracing, there is no `micrometer-registry-prometheus` dependency for metrics export.

The README mentions Prometheus in the technology stack, but it is not actually configured.

---

### GAP-OBS-05: Inconsistent Logging Patterns

**Severity:** Low | **Effort:** Small

Logging inconsistencies:
- Some controllers use `@Slf4j`, others don't log at all.
- Service-layer logging varies from verbose to none.
- No standardized log format for request/response logging.
- Typo in log message: "Reading utitlity account" (AccountController).

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity:** High | **Effort:** Medium

No Resilience4j or Hystrix circuit breakers on any Feign client call. If core-banking-service is down or slow:
- Fund transfer and utility payment services will block indefinitely (no timeout).
- Cascading failures will propagate to the API gateway.
- All downstream services will exhaust thread pools.

---

### GAP-RES-02: No Retry Policies

**Severity:** High | **Effort:** Small

No retry configuration for:
- Feign client calls (transient network errors)
- Database connections
- Config server bootstrap

A temporary network glitch will cause immediate failure.

---

### GAP-RES-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

No explicit timeout configuration found for:
- Feign client read/connect timeouts
- Database connection pool timeouts
- API Gateway route timeouts

Default Feign timeout is 60 seconds, which is far too long for a banking API.

---

### GAP-RES-04: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

No fallback methods defined for any Feign client call. When core-banking-service is unavailable:
- No cached responses
- No degraded mode
- No queuing for later retry
- Status remains `PENDING`/`PROCESSING` with no recovery mechanism

---

### GAP-RES-05: Non-Atomic Distributed Transactions

**Severity:** Critical | **Effort:** Large

The fund transfer and utility payment flows involve two databases (local service DB + core-banking DB) but use no distributed transaction mechanism:

1. Local entity saved with status `PENDING`.
2. Feign call to core-banking (money moves).
3. Local entity updated to `SUCCESS`.

If step 3 fails (network error, service crash), money has moved in core-banking but local status stays `PENDING`. There is:
- No saga pattern
- No outbox pattern
- No compensation/rollback logic
- No idempotency keys for retry safety

---

### GAP-RES-06: No Database Connection Pooling Configuration

**Severity:** Medium | **Effort:** Small

No HikariCP configuration (pool size, max lifetime, connection timeout). Spring Boot defaults apply, which may not be appropriate for a banking application under load.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | Medium | Small |
| GAP-ORG-02 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ERR-01 | Error Handling | Raw ResponseEntity types | High | Medium |
| GAP-ERR-02 | Error Handling | Exception handler leaks internal details | Critical | Small |
| GAP-ERR-03 | Error Handling | All errors return HTTP 400 | High | Small |
| GAP-ERR-04 | Error Handling | No Feign error decoder in 2 services | High | Medium |
| GAP-TEST-01 | Testing | Minimal test coverage | High | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-SEC-01 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled without justification | Medium | Small |
| GAP-SEC-04 | Security | No rate limiting | Medium | Medium |
| GAP-SEC-05 | Security | Sensitive data in logs | High | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | No versioning strategy | Medium | Medium |
| GAP-API-02 | API Design | Inconsistent pagination | Medium | Small |
| GAP-API-03 | API Design | Inconsistent resource naming | Low | Small |
| GAP-API-04 | API Design | Wrong OpenAPI dependency / no doc hosting | Medium | Small |
| GAP-API-05 | API Design | No filtering or search | Low | Medium |
| GAP-OBS-01 | Observability | Actuator not configured | Medium | Small |
| GAP-OBS-02 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-03 | Observability | No custom health checks | Medium | Small |
| GAP-OBS-04 | Observability | No metrics endpoints | Medium | Small |
| GAP-OBS-05 | Observability | Inconsistent logging | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | Non-atomic distributed transactions | Critical | Large |
| GAP-RES-06 | Resilience | No connection pool configuration | Medium | Small |
