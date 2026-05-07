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

### GAP-ORG-01: No Shared Library / Common Module

**Severity: Medium** | **Effort: Medium**

Multiple classes are copy-pasted across services with minor variations:
- `ErrorResponse` exists in `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service`
- `SimpleBankingGlobalException` is duplicated in all 4 business services
- `GlobalExceptionHandler` is duplicated in all 4 business services
- `AuditAware` is duplicated in `fund-transfer-service`, `user-service`, and `utility-payment-service`
- `BaseMapper` interface is duplicated in `user-service` and `utility-payment-service`
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are duplicated in `fund-transfer-service`, `user-service`, and `utility-payment-service`

**Impact:** Any bug fix or enhancement must be applied to each copy independently, increasing maintenance burden and risk of drift.

### GAP-ORG-02: No Multi-Project Gradle Build

**Severity: Low** | **Effort: Medium**

Each service has its own independent Gradle wrapper and `build.gradle`. There is no root `settings.gradle` for a unified build. This means:
- No single command to build all services
- Dependency versions are managed independently per service (risk of version drift)
- No shared dependency BOM or convention plugin

### GAP-ORG-03: Inconsistent Package Structure Across Services

**Severity: Low** | **Effort: Small**

Package structures vary between services:
- `core-banking-service`: `model/entity`, `model/dto`, `model/mapper`, `repository`, `service`, `controller`, `exception`
- `internet-banking-fund-transfer-service`: `model/entity`, `model/dto`, `model/mapper`, `model/repository`, `service/rest/client`, `controller`, `exception`, `configuration`
- `internet-banking-user-service`: `model/entity`, `model/dto`, `model/mapper`, `model/repository`, `service/rest`, `controller`, `exception`, `configuration/keycloak`, `configuration/feign`, `configuration/filter`, `configuration/audit`
- `internet-banking-utility-payment-service`: `model/rest/request`, `model/rest/response`, `repository`, `service/rest`, `controller`, `exception`, `configuration`

The `repository` package is sometimes inside `model` and sometimes at the top level. DTO packages use different conventions (`model/dto/request` vs `model/rest/request`).

### GAP-ORG-04: Mappers Instantiated Manually Instead of Spring-Managed

**Severity: Low** | **Effort: Small**

Mappers are created with `new` instead of being Spring beans:
```java
private UserMapper userMapper = new UserMapper();       // AccountService, UserService
private FundTransferMapper mapper = new FundTransferMapper();  // FundTransferService
```
This prevents injection of dependencies into mappers and makes testing harder.

---

## 2. Error Handling

### GAP-ERR-01: Generic Exception Fallback Returns 400 for All Errors

**Severity: High** | **Effort: Small**

All `GlobalExceptionHandler` implementations catch `Exception.class` and return HTTP 400 (Bad Request) with a plain string body:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```
**Issues:**
- Server errors (NullPointerException, database failures, etc.) return 400 instead of 500
- Exception toString is exposed to clients, potentially leaking stack traces and internal details
- No structured error response for the generic handler (returns plain string, not `ErrorResponse`)

### GAP-ERR-02: No HTTP Status Code Differentiation

**Severity: High** | **Effort: Small**

All custom exceptions return HTTP 400 regardless of the error type:
- `EntityNotFoundException` should return HTTP 404
- `InsufficientFundsException` should return HTTP 422 (Unprocessable Entity) or 409 (Conflict)
- `UserAlreadyRegisteredException` should return HTTP 409 (Conflict)
- Server-side errors should return HTTP 500

### GAP-ERR-03: Inconsistent Error Response Structures

**Severity: Medium** | **Effort: Small**

- `core-banking-service` and `utility-payment-service` use `ErrorResponse` with `@Builder`
- `fund-transfer-service` uses `ErrorResponse` with a constructor: `new ErrorResponse(code, message)`
- The generic `Exception` handler returns a plain `String` body instead of a structured `ErrorResponse`
- Some `ErrorResponse` classes have `code` + `message`, but the structure is not shared

### GAP-ERR-04: No Error Handling for Feign Client Failures

**Severity: High** | **Effort: Medium**

When Feign calls to `core-banking-service` fail (network timeout, 5xx, service unavailable), there is no error handling:
- `fund-transfer-service` saves a `PENDING` entity, calls Feign, then updates to `SUCCESS` — but if the Feign call throws, the entity stays `PENDING` forever with no retry or failure recording
- `utility-payment-service` has the same issue with `PROCESSING` status
- `CustomFeignErrorDecoder` exists in some services but only wraps Feign errors as `SimpleBankingGlobalException`, losing HTTP status context

---

## 3. Testing

### GAP-TEST-01: Virtually No Test Coverage

**Severity: Critical** | **Effort: Large**

Each service contains only a single boilerplate test that loads the Spring context:
```java
@SpringBootTest
class InternetBankingUserServiceApplicationTests {
    @Test
    void contextLoads() { }
}
```
**Missing:**
- **Unit tests:** Zero tests for services, controllers, mappers, or any business logic
- **Integration tests:** No tests for repository queries, Feign client contracts, or end-to-end flows
- **Contract tests:** No Spring Cloud Contract or Pact tests between services

The `TransactionService` contains a critical balance calculation bug that would be caught by even basic unit tests.

### GAP-TEST-02: No Test Configuration for Service Dependencies

**Severity: Medium** | **Effort: Medium**

While H2 is included as a test dependency, there is no test configuration for:
- Mocking Keycloak (user-service tests would fail without Keycloak)
- Mocking Feign clients (tests would attempt real HTTP calls)
- Testcontainers for MySQL integration testing
- WireMock for external service simulation

### GAP-TEST-03: Context Load Tests Likely Fail

**Severity: Medium** | **Effort: Small**

The existing `@SpringBootTest` tests require:
- A running Config Server (bootstrap context)
- A running Eureka Server
- A running MySQL database
- A running Keycloak server (for user-service)

Without these, the context load tests will fail, meaning even the minimal existing tests are broken in isolation.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code

**Severity: Critical** | **Effort: Small**

Plaintext credentials are committed to the repository:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: Keycloak admin password `KEYCLOAK_ADMIN_PASSWORD: password`
- `docker-compose.yml`: PostgreSQL password `POSTGRES_PASSWORD: password`
- `privileges.sql`: MySQL user password `oPItyPticIAt`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-SEC-02: No Input Validation on Request Bodies

**Severity: High** | **Effort: Small**

None of the `@RequestBody` parameters use Jakarta Bean Validation annotations:
- `FundTransferRequest`: No `@NotNull`, `@Positive`, `@NotBlank` on `fromAccount`, `toAccount`, `amount`
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `referenceNumber`, `account`
- `User` (registration): No `@Email`, `@NotBlank`, `@Size` on `email`, `password`, `identification`
- No `@Valid` annotation on any controller method parameter

Invalid or malicious input can reach the service layer unchecked.

### GAP-SEC-03: CSRF Disabled Without Documentation

**Severity: Medium** | **Effort: Small**

CSRF protection is explicitly disabled in the API Gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While this is common for stateless JWT APIs, it should be documented. If the gateway ever serves browser-based sessions, CSRF protection would be needed.

### GAP-SEC-04: Keycloak Singleton Not Thread-Safe

**Severity: Medium** | **Effort: Small**

`KeycloakProperties.getInstance()` uses a non-thread-safe lazy singleton:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
Under concurrent requests, multiple Keycloak instances could be created (broken double-checked locking without `volatile` or synchronization).

### GAP-SEC-05: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No OWASP Dependency Check, Snyk, or Dependabot configuration exists. Dependencies are not audited for known CVEs.

### GAP-SEC-06: Overly Broad Database Permissions

**Severity: Medium** | **Effort: Small**

`privileges.sql` grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` to the application user. The application should use least-privilege access (e.g., `INSERT, UPDATE, DELETE, SELECT` on specific databases only).

### GAP-SEC-07: Password Handling Concerns

**Severity: Medium** | **Effort: Small**

In `UserService.createUser()`, the user's password is received in the `User` DTO and directly set on the Keycloak `CredentialRepresentation`. While Keycloak handles hashing, the password transits through the application layer in plain text and could be logged via `log.info("Creating user with {}", request.toString())` since `User` is a `@Data` class (includes password in `toString()`).

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters

**Severity: Medium** | **Effort: Small**

Most controller methods return raw `ResponseEntity` without generics:
```java
public ResponseEntity getBankAccount(...) { ... }  // Should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...) { ... }     // Should be ResponseEntity<FundTransferResponse>
```
This loses compile-time type safety and produces incomplete OpenAPI documentation.

### GAP-API-02: No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

While all endpoints include `/api/v1/` in the path, there is no strategy or infrastructure for:
- Supporting multiple API versions simultaneously
- Version negotiation via headers
- Deprecation mechanism

### GAP-API-03: No Pagination Metadata in Responses

**Severity: Medium** | **Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<T>` instead of Spring's `Page<T>`:
```java
public List<User> readUsers(Pageable pageable) {
    return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
}
```
Clients receive no information about total elements, total pages, or current page number.

### GAP-API-04: No Filtering or Sorting Parameters

**Severity: Low** | **Effort: Medium**

List endpoints provide basic pagination via `Pageable` but no explicit filtering:
- Cannot filter fund transfers by status, date range, or account
- Cannot filter utility payments by provider, status, or date range
- Cannot search users by name or email

### GAP-API-05: Inconsistent Endpoint Naming

**Severity: Low** | **Effort: Small**

- `core-banking-service`: Uses `bank-account` and `util-account` (abbreviated)
- `internet-banking-user-service`: Uses `bank-users`
- `internet-banking-fund-transfer-service`: Uses `transfer`
- `internet-banking-utility-payment-service`: Uses `utility-payment`

Resource naming is inconsistent — some use plural, some singular; some abbreviate, some don't.

### GAP-API-06: PATCH Endpoint Does Not Follow RFC 7396

**Severity: Low** | **Effort: Small**

`PATCH /api/v1/bank-users/update/{id}` includes `update` in the path, which is redundant (PATCH already implies update). The standard RESTful pattern is `PATCH /api/v1/bank-users/{id}`.

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging Practices

**Severity: Medium** | **Effort: Small**

- Some services use `@Slf4j` and log at `info` level, others don't log at all
- `core-banking-service` controllers have misspellings: `"Reading utitlity account"`
- `FundTransferService` has a broken log statement: `log.info("Sending fund transfer request {}" + request.toString())` — string concatenation instead of parameterized logging
- Sensitive data (full request objects including potential PII) is logged at `info` level

### GAP-OBS-02: No Structured Logging Configuration

**Severity: Medium** | **Effort: Small**

No `logback-spring.xml` or logging configuration files exist. All logging uses Spring Boot defaults. For production:
- JSON-formatted logs are preferred for aggregation tools
- Log levels should be configurable per environment
- Correlation IDs (trace IDs) should be included in log output

### GAP-OBS-03: No Health Check Customization

**Severity: Low** | **Effort: Small**

While `spring-boot-starter-actuator` is included in all services, there are no custom health indicators for:
- Database connectivity
- Keycloak connectivity (user-service)
- Downstream service availability
- Config server connectivity

Default actuator health endpoints exist but may not expose enough detail.

### GAP-OBS-04: No Metrics Export Configuration

**Severity: Medium** | **Effort: Small**

Despite the README mentioning Prometheus, there is no `micrometer-registry-prometheus` dependency in any service. No custom business metrics are defined (e.g., transfer count, payment success rate, registration rate).

### GAP-OBS-05: No Alerting or Dashboard Configuration

**Severity: Low** | **Effort: Medium**

No Grafana dashboards, Prometheus alert rules, or any monitoring configuration is present. Zipkin is configured for tracing but there are no trace-based alerts.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

No circuit breaker pattern is implemented (no Resilience4j, Hystrix, or Spring Cloud Circuit Breaker dependencies). If `core-banking-service` goes down:
- `fund-transfer-service` will fail on every request with connection timeouts
- `utility-payment-service` will fail on every request
- `user-service` registration will fail
- No fallback behavior, no request shedding, no bulkhead isolation

### GAP-RES-02: No Retry Policies

**Severity: High** | **Effort: Small**

No retry configuration exists for Feign clients or any external calls:
- Transient network failures cause immediate failure
- No configurable retry counts, backoff strategies, or retry conditions
- Spring Retry is not included as a dependency

### GAP-RES-03: No Timeout Configuration

**Severity: High** | **Effort: Small**

No explicit timeout configuration for:
- Feign client connection and read timeouts
- Database connection pool timeouts
- Keycloak admin client timeouts

Default timeouts may be too long (or infinite), causing thread exhaustion under load when downstream services are slow.

### GAP-RES-04: No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

When downstream services fail, there is no graceful degradation:
- No cached responses
- No default fallback values
- No partial response capability
- Failed Feign calls propagate as unhandled exceptions

### GAP-RES-05: No Rate Limiting

**Severity: Medium** | **Effort: Medium**

The API Gateway has no rate limiting configuration. A single client can exhaust resources with unlimited requests. Spring Cloud Gateway supports `RequestRateLimiter` filter but it is not configured.

### GAP-RES-06: Transaction Integrity Issues

**Severity: Critical** | **Effort: Medium**

The fund transfer flow has no distributed transaction coordination:
1. `fund-transfer-service` saves entity with `PENDING`
2. Calls `core-banking-service` which debits source and credits destination
3. If the response is lost (network partition after core banking commits), the fund-transfer entity stays `PENDING` even though money was moved
4. No compensating transaction or saga pattern
5. No idempotency keys to prevent duplicate transfers on retry

The `core-banking-service.TransactionService.internalFundTransfer()` is `@Transactional` but only covers the local database — if the application crashes between debiting the source and crediting the destination, partial state is possible (though unlikely within a single `@Transactional` method).

### GAP-RES-07: Balance Calculation Bug

**Severity: Critical** | **Effort: Small**

In `TransactionService.internalFundTransfer()` (lines 90-91):
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` was already decremented, resulting in a double subtraction. The same pattern exists for credits (lines 99-100) and in `utilPayment()` (lines 63-64). This means every transaction incorrectly reduces/increases the available balance by 2x the amount.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No shared library / common module | Medium | Medium |
| GAP-ORG-02 | Code Organization | No multi-project Gradle build | Low | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Mappers instantiated manually | Low | Small |
| GAP-ERR-01 | Error Handling | Generic exception returns 400 for all errors | High | Small |
| GAP-ERR-02 | Error Handling | No HTTP status code differentiation | High | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response structures | Medium | Small |
| GAP-ERR-04 | Error Handling | No error handling for Feign client failures | High | Medium |
| GAP-TEST-01 | Testing | Virtually no test coverage | Critical | Large |
| GAP-TEST-02 | Testing | No test configuration for service dependencies | Medium | Medium |
| GAP-TEST-03 | Testing | Context load tests likely fail in isolation | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-SEC-02 | Security | No input validation on request bodies | High | Small |
| GAP-SEC-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-06 | Security | Overly broad database permissions | Medium | Small |
| GAP-SEC-07 | Security | Password in toString() / logged | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Medium |
| GAP-API-03 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-API-04 | API Design | No filtering or sorting parameters | Low | Medium |
| GAP-API-05 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-API-06 | API Design | PATCH endpoint includes redundant "update" | Low | Small |
| GAP-OBS-01 | Observability | Inconsistent logging practices | Medium | Small |
| GAP-OBS-02 | Observability | No structured logging configuration | Medium | Small |
| GAP-OBS-03 | Observability | No health check customization | Low | Small |
| GAP-OBS-04 | Observability | No metrics export configuration | Medium | Small |
| GAP-OBS-05 | Observability | No alerting or dashboard configuration | Low | Medium |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No rate limiting | Medium | Medium |
| GAP-RES-06 | Resilience | Transaction integrity issues | Critical | Medium |
| GAP-RES-07 | Resilience | Balance calculation bug (double subtraction) | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 5 |
| High | 6 |
| Medium | 17 |
| Low | 8 |
| **Total** | **36** |
