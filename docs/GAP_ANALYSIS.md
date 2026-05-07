# Engineering Standards Gap Analysis

This document assesses the codebase against industry-standard engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

**Severity Scale:**
- **Critical** - Security vulnerability or production reliability risk
- **High** - Significant maintainability/quality issue
- **Medium** - Important improvement for team productivity
- **Low** - Nice-to-have polish item

**Effort Scale:**
- **Small** - < 1 day per service
- **Medium** - 1-3 days per service
- **Large** - 1+ week of work

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Small

Each service has an independent `build.gradle` with duplicated plugin versions, dependency management, and Spring Cloud BOM declarations. There is no root `settings.gradle` or `build.gradle` to manage shared configurations.

**Impact:** Version drift between services, duplicated maintenance effort when upgrading dependencies.

---

### GAP-ORG-02: Duplicated Code Across Services
**Severity:** Medium | **Effort:** Medium

The following classes are copy-pasted across multiple services with minor variations:
- `AuditAware` (mapped superclass with audit fields)
- `BaseMapper` (generic mapper interface)
- `GlobalExceptionHandler` (exception handling)
- `ErrorResponse` (error DTO)
- `SimpleBankingGlobalException` (base exception)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder`
- `CustomFeignClientConfiguration`

**Impact:** Bug fixes must be applied to each copy independently; inconsistencies emerge over time.

---

### GAP-ORG-03: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

Package structure differs between services:
- Fund Transfer: `model.dto.request`, `model.dto.response`, `model.repository`, `service.rest.client`
- Utility Payment: `model.rest.request`, `model.rest.response`, `repository`, `service.rest`
- User Service: `model.dto`, `model.repository`, `model.rest.response`, `service.rest`

**Impact:** Cognitive overhead for developers switching between services.

---

### GAP-ORG-04: Model Classes Mixed Between DTOs and Entities
**Severity:** Low | **Effort:** Medium

`AuditAware` is a JPA `@MappedSuperclass` that is also used as a base class for DTOs (`User extends AuditAware`). This mixes persistence concerns into the DTO/API layer. The `User` DTO contains a `password` field alongside a JPA `@Version` field.

**Impact:** Sensitive data exposure risk; tight coupling between API contracts and persistence layer.

---

## 2. Error Handling

### GAP-ERR-01: Generic Exception Handler Returns 400 for All Errors
**Severity:** High | **Effort:** Small

All `GlobalExceptionHandler` implementations catch `Exception.class` and return HTTP 400 (Bad Request) with a raw exception string:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Impact:** 
- Internal exception details leaked to clients (security risk)
- All errors appear as client errors (400) even for server-side failures (should be 500)
- No structured error format for generic exceptions

---

### GAP-ERR-02: No HTTP Status Code Differentiation
**Severity:** High | **Effort:** Small

The `GlobalExceptionHandler` only uses `400 Bad Request` regardless of the error type:
- `EntityNotFoundException` should return `404 Not Found`
- `InsufficientFundsException` should return `422 Unprocessable Entity`
- Internal errors should return `500 Internal Server Error`

**Impact:** Clients cannot programmatically distinguish error types.

---

### GAP-ERR-03: No Feign Error Handling in Fund Transfer / Utility Payment
**Severity:** High | **Effort:** Medium

The `FundTransferService` and `UtilityPaymentService` call the core banking Feign client without any error handling. If the Feign call fails:
- The entity is saved with `PENDING`/`PROCESSING` status and never updated to `FAILED`
- No try-catch around `bankingCoreFeignClient.fundTransfer(request)`
- Only the User Service has a `CustomFeignErrorDecoder`

**Impact:** Orphaned records in intermediate states; no user-facing error messaging for downstream failures.

---

### GAP-ERR-04: Inconsistent Error Response Formats
**Severity:** Medium | **Effort:** Small

- Core Banking & User Service use `ErrorResponse.builder().code().message().build()`
- Fund Transfer uses `new ErrorResponse(e.getCode(), e.getMessage())`
- Generic exceptions return a plain String instead of structured JSON

**Impact:** API consumers must handle multiple error response shapes.

---

## 3. Testing

### GAP-TEST-01: Virtually No Test Coverage
**Severity:** Critical | **Effort:** Large

All test classes contain only a single empty `contextLoads()` test:
```java
@SpringBootTest
class InternetBankingFundTransferServiceApplicationTests {
    @Test
    void contextLoads() { }
}
```

**No unit tests exist** for:
- Service layer business logic
- Controller layer (MockMvc/WebTestClient tests)
- Repository layer (DataJpaTest)
- Mapper classes

**Impact:** No regression protection; cannot refactor safely; no validation of business rules.

---

### GAP-TEST-02: No Integration Tests
**Severity:** High | **Effort:** Large

No integration tests verify the interaction between services (e.g., Feign client calls, end-to-end fund transfer flow). No Testcontainers or WireMock usage for external dependency simulation.

**Impact:** Inter-service contract breaks go undetected until deployment.

---

### GAP-TEST-03: No Contract Tests
**Severity:** Medium | **Effort:** Large

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between:
- Fund Transfer Service <-> Core Banking Service
- Utility Payment Service <-> Core Banking Service
- User Service <-> Core Banking Service

**Impact:** API changes in Core Banking can silently break downstream consumers.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Control
**Severity:** Critical | **Effort:** Small

The following credentials are committed to the repository:
- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`)
- `docker-compose.yml`: Keycloak admin password (`password`)
- `docker-compose.yml`: PostgreSQL password (`password`)
- `privileges.sql`: MySQL user password (`oPItyPticIAt`)
- `README.md`: Test user credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`)

**Impact:** Credential exposure if repository is public or leaked.

---

### GAP-SEC-02: No Input Validation on API Requests
**Severity:** Critical | **Effort:** Small

No `@Valid` annotations or Bean Validation constraints on any request DTOs:
- `FundTransferRequest`: No null checks on `fromAccount`, `toAccount`; no minimum amount validation
- `UtilityPaymentRequest`: No null checks on `providerId`, `account`; no amount validation
- `User` (registration): No email format validation, no password strength rules

**Impact:** Null pointer exceptions, negative amount transfers, malformed data persistence.

---

### GAP-SEC-03: Password Exposed in API Response
**Severity:** Critical | **Effort:** Small

The `User` DTO in the User Service contains a `password` field. Since this same class is used as both request and response body, the password could be serialized in API responses (the `createUser` method returns the full `User` object).

**Impact:** Password leakage in API responses.

---

### GAP-SEC-04: CSRF Disabled Without Documentation
**Severity:** Medium | **Effort:** Small

CSRF protection is explicitly disabled in `SecurityConfiguration`:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While acceptable for stateless JWT APIs, this should be documented with rationale.

**Impact:** Low risk given JWT-based auth, but violates security audit expectations.

---

### GAP-SEC-05: Keycloak Singleton Not Thread-Safe
**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern for lazy initialization:
```java
if (keycloakInstance == null) {
    keycloakInstance = KeycloakBuilder.builder()...build();
}
```

**Impact:** Race condition could create multiple Keycloak instances during startup.

---

### GAP-SEC-06: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

No rate limiting is configured at the API Gateway or service level. The registration endpoint (`/user/api/v1/bank-users/register`) is publicly accessible without throttling.

**Impact:** Susceptible to brute force attacks, credential stuffing, and denial of service.

---

## 5. API Design

### GAP-API-01: Raw ResponseEntity Without Type Parameters
**Severity:** Medium | **Effort:** Small

Most controller methods return untyped `ResponseEntity` (raw type):
```java
public ResponseEntity getBankAccount(...) { }
public ResponseEntity fundTransfer(...) { }
```

Only the User Service properly types some responses: `ResponseEntity<User>`.

**Impact:** No compile-time type safety; OpenAPI documentation cannot infer response schemas automatically.

---

### GAP-API-02: No API Versioning Strategy
**Severity:** Medium | **Effort:** Medium

While endpoints use `/api/v1/` prefix, there is no mechanism for supporting multiple API versions simultaneously, no version negotiation, and no deprecation policy.

**Impact:** Breaking changes require all clients to update simultaneously.

---

### GAP-API-03: Inconsistent Pagination Response
**Severity:** Medium | **Effort:** Small

Paginated endpoints return `List<T>` without pagination metadata (total count, page number, total pages). Spring's `Pageable` is accepted as input but the `Page` wrapper is discarded:
```java
return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
```

**Impact:** Clients cannot implement proper pagination UIs without knowing total count.

---

### GAP-API-04: No OpenAPI/Swagger Proper Configuration
**Severity:** Low | **Effort:** Small

While `springdoc-openapi` dependency is included and basic `@Operation`/`@Tag` annotations exist, the wrong starter is used (`springdoc-openapi-starter-webflux-ui` in non-WebFlux services). No global API metadata, security scheme definitions, or response schema documentation.

**Impact:** Swagger UI may not work correctly; auto-generated docs are incomplete.

---

### GAP-API-05: No Filtering/Search Capabilities
**Severity:** Low | **Effort:** Medium

List endpoints only support pagination via Spring's `Pageable`. No filtering by date range, status, account number, or other business-relevant criteria.

**Impact:** Clients must fetch all data and filter client-side.

---

## 6. Observability

### GAP-OBS-01: No Structured Logging
**Severity:** Medium | **Effort:** Small

Logging uses default Spring Boot format with `@Slf4j`. No structured JSON logging configured, no correlation ID propagation, no MDC context enrichment.

**Impact:** Log aggregation and searching in production environments is difficult.

---

### GAP-OBS-02: Sensitive Data in Logs
**Severity:** High | **Effort:** Small

Controllers log full request objects via `.toString()`:
```java
log.info("Creating user with {}", request.toString());
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

`User.toString()` includes the password field (via Lombok `@Data`).

**Impact:** Passwords and financial data written to log files.

---

### GAP-OBS-03: No Health Check Customization
**Severity:** Low | **Effort:** Small

All services include `spring-boot-starter-actuator` but no custom health indicators for:
- Database connectivity
- Keycloak availability
- Downstream service availability (core-banking reachability)

Default `/actuator/health` is available but lacks business-specific health checks.

**Impact:** Inability to detect partial failures (e.g., database up but Keycloak down).

---

### GAP-OBS-04: No Metrics Endpoints
**Severity:** Medium | **Effort:** Small

While Micrometer is included for tracing, no custom business metrics are defined:
- No transaction count/rate metrics
- No transfer amount histograms
- No error rate counters
- No Prometheus endpoint configuration visible

**Impact:** No visibility into business KPIs or performance trends.

---

### GAP-OBS-05: Zipkin Tracing Configuration Not Visible
**Severity:** Low | **Effort:** Small

Tracing dependencies are included in all services but sampling rate, propagation format, and Zipkin endpoint configuration are not visible in the local config (likely in external config server). No verification that traces are properly correlated across services.

**Impact:** Tracing may not be working as expected without explicit configuration.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

No circuit breaker pattern (Resilience4j, Hystrix) is implemented on any Feign client or inter-service call. If core-banking-service goes down:
- Fund Transfer Service blocks indefinitely on Feign calls
- Utility Payment Service blocks indefinitely
- Cascading failure propagates to all upstream services

**Impact:** Single service failure can bring down the entire system.

---

### GAP-RES-02: No Retry Policies
**Severity:** High | **Effort:** Small

No retry configuration on Feign clients for transient failures (network blips, connection resets). No Spring Retry or Resilience4j retry decorators.

**Impact:** Transient failures cause permanent transaction failures.

---

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

No explicit timeout configuration on:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Gateway route timeouts

Default timeouts may be infinite or excessively long.

**Impact:** Thread pool exhaustion during downstream slowdowns.

---

### GAP-RES-04: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

No fallback methods or graceful degradation patterns when downstream services are unavailable:
- No cached responses for account lookups
- No queuing mechanism for failed transfers
- No partial availability modes

**Impact:** All-or-nothing availability model.

---

### GAP-RES-05: No Idempotency Controls
**Severity:** High | **Effort:** Medium

Fund transfer and utility payment endpoints have no idempotency keys:
- Duplicate POST requests create duplicate transactions
- No deduplication mechanism based on client-supplied reference
- Network retries can cause double-spending

**Impact:** Financial data integrity risk from duplicate transactions.

---

### GAP-RES-06: Transaction Integrity Issues in Core Banking
**Severity:** Critical | **Effort:** Medium

The `internalFundTransfer` method has a potential issue:
```java
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
This sets `availableBalance = actualBalance - amount` AFTER `actualBalance` was already decremented, effectively double-subtracting from `availableBalance`.

Additionally, the `utilPayment` method has the same bug pattern.

**Impact:** Incorrect account balances after transactions; financial data corruption.

---

### GAP-RES-07: No Database Connection Pooling Configuration
**Severity:** Medium | **Effort:** Small

No explicit HikariCP connection pool configuration (max pool size, connection timeout, idle timeout). Default settings may not be appropriate for production workloads.

**Impact:** Connection exhaustion under load; slow recovery from database restarts.

---

## Summary Table

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 0 | 2 | 2 |
| Error Handling | 0 | 3 | 1 | 0 |
| Testing | 1 | 1 | 1 | 0 |
| Security | 3 | 0 | 3 | 0 |
| API Design | 0 | 0 | 3 | 2 |
| Observability | 0 | 1 | 2 | 2 |
| Resilience | 2 | 3 | 2 | 0 |
| **Total** | **6** | **8** | **14** | **6** |
