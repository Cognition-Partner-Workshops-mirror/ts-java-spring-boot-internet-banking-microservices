# Internet Banking Microservices - Engineering Standards Gap Analysis

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

### GAP-01: No Shared Library Module

**Severity: Medium** | **Effort: Medium**

The following classes are duplicated across 3-4 services with identical or near-identical implementations:

| Class                    | Duplicated In                                              |
|--------------------------|------------------------------------------------------------|
| `AuditAware`             | fund-transfer, user-service, utility-payment               |
| `BaseMapper`             | fund-transfer, user-service, utility-payment               |
| `GlobalExceptionHandler` | core-banking, fund-transfer, user-service, utility-payment |
| `ErrorResponse`          | core-banking, fund-transfer, user-service, utility-payment |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, user-service, utility-payment |
| `AppAuthUserFilter`      | fund-transfer, user-service, utility-payment               |
| `ApiRequestContext`      | fund-transfer, user-service, utility-payment               |
| `ApiRequestContextHolder`| fund-transfer, user-service, utility-payment               |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment                     |

A shared library (e.g., `banking-common`) would reduce duplication, ensure consistency, and simplify maintenance.

### GAP-02: No Multi-Module Gradle Root Project

**Severity: Low** | **Effort: Small**

Each service has an independent `build.gradle` with no root `settings.gradle`. This means:
- No centralized dependency version management (BOM)
- No single command to build all services
- Duplicated Gradle plugin configurations across all services

### GAP-03: Inconsistent Package Naming for REST Clients

**Severity: Low** | **Effort: Small**

| Service               | Feign Client Package                                   |
|-----------------------|--------------------------------------------------------|
| fund-transfer-service | `service.rest.client.BankingCoreFeignClient`           |
| utility-payment-service | `service.rest.BankingCoreRestClient`                 |
| user-service          | `service.rest.BankingCoreRestClient`                   |

Different class names and package structures for functionally equivalent Feign clients.

### GAP-04: Mapper Instantiation Pattern

**Severity: Low** | **Effort: Small**

Mappers are instantiated inline (`private XMapper mapper = new XMapper()`) rather than being injected as Spring beans. This makes them untestable in isolation and prevents use of Spring-managed dependencies within mappers.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity: Critical** | **Effort: Small**

Every `GlobalExceptionHandler` (in all 4 business services) maps **all exceptions** to `ResponseEntity.badRequest()`:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

This means:
- `EntityNotFoundException` returns 400 instead of 404
- `InsufficientFundsException` returns 400 instead of 422
- Internal server errors return 400 instead of 500
- Stack traces leak in the response body (`"Exception occur inside API " + e`)

### GAP-06: Stack Trace Leakage in Error Responses

**Severity: Critical** | **Effort: Small**

The catch-all handler concatenates the full exception (including stack trace) into the response body. This exposes internal implementation details including class names, line numbers, and dependency information to API consumers.

### GAP-07: No Feign Error Decoder in Fund Transfer and Utility Payment Services

**Severity: High** | **Effort: Small**

Only `user-service` has a `CustomFeignErrorDecoder`. The `fund-transfer-service` and `utility-payment-service` use the default Feign error decoder, which wraps errors in `FeignException` and propagates raw upstream error responses to the caller.

### GAP-08: Inconsistent Error Response Format

**Severity: Medium** | **Effort: Small**

Two different error formats coexist:
1. Structured: `{ code: "...", message: "..." }` (for `SimpleBankingGlobalException`)
2. Unstructured: `"Exception occur inside API <stacktrace>"` (for all other exceptions)

No standard error envelope (e.g., `timestamp`, `path`, `status`, `errors[]`).

---

## 3. Testing

### GAP-09: Minimal Test Coverage - Only Core Banking Has Tests

**Severity: High** | **Effort: Large**

| Service                        | Test Classes                      | Real Tests |
|--------------------------------|-----------------------------------|------------|
| core-banking-service           | `TransactionServiceTest` (10), `AccountServiceTest` (6), `UserServiceTest` (3) | 19 unit tests |
| fund-transfer-service          | `InternetBankingFundTransferServiceApplicationTests` | Empty context load |
| user-service                   | `InternetBankingUserServiceApplicationTests` | Empty context load |
| utility-payment-service        | `InternetBankingUtilityPaymentServiceApplicationTests` | Empty context load |
| api-gateway                    | `InternetBankingApiGatewayApplicationTests` | Empty context load |
| config-server                  | `InternetBankingConfigServerApplicationTests` | Empty context load |
| service-registry               | `InternetBankingServiceRegistryApplicationTests` | Empty context load |

5 of 6 services have **zero meaningful tests**. The `ApplicationTests` classes only verify Spring context loading.

### GAP-10: No Integration Tests

**Severity: High** | **Effort: Large**

No integration tests exist for:
- Database operations (repository layer)
- Feign client calls between services
- API Gateway routing rules
- End-to-end transaction flows

### GAP-11: No Contract Tests Between Services

**Severity: Medium** | **Effort: Large**

No Spring Cloud Contract, Pact, or similar contract testing framework. Feign client interfaces could silently drift from the actual core-banking-service API.

### GAP-12: No Controller / Web Layer Tests

**Severity: Medium** | **Effort: Medium**

No `@WebMvcTest` or `MockMvc`-based tests for any controller. Request/response serialization, path variable binding, and error handling are untested.

---

## 4. Security

### GAP-13: No Input Validation on Any Request DTO

**Severity: Critical** | **Effort: Small**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Positive`, or any Bean Validation annotation on any request body across the entire codebase. Examples:

- `FundTransferRequest.amount` can be null, zero, or negative
- `UtilityPaymentRequest.account` can be null or empty
- `User.email` can be any string (no format validation)
- `User.password` has no strength requirements

### GAP-14: Downstream Services Trust Unauthenticated Header

**Severity: Critical** | **Effort: Medium**

JWT validation happens **only at the API Gateway**. Downstream services rely on the `X-Auth-Id` header for identity:

```java
// GatewayConfiguration.java
exchange.getRequest().mutate().header(HTTP_HEADER_AUTH_USER_ID, principal).build();
```

If any downstream service is accessed directly (bypassing the gateway), the `X-Auth-Id` header can be freely spoofed. Downstream services have **no token validation** of their own.

### GAP-15: Hardcoded Credentials in Source Code

**Severity: Critical** | **Effort: Small**

| File                              | Credential                                |
|-----------------------------------|-------------------------------------------|
| `docker-compose.yml`             | `MYSQL_ROOT_PASSWORD: woVERANKliGharym`   |
| `docker-compose.yml`             | `KEYCLOAK_ADMIN_PASSWORD: password`       |
| `docker-compose.yml`             | `KC_DB_PASSWORD: password`                |
| `docker-compose/mysql/Dockerfile`| `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`|
| `docker-compose/mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'`       |

### GAP-16: CSRF Disabled on API Gateway

**Severity: Medium** | **Effort: Small**

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF protection is disabled globally. While acceptable for pure REST APIs with token-based auth, this should be explicitly documented as a design decision.

### GAP-17: Keycloak Singleton is Not Thread-Safe

**Severity: Medium** | **Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern with a static field. Under concurrent requests, multiple Keycloak client instances could be created (race condition):

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {   // not thread-safe
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

### GAP-18: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool is configured in any build file or CI pipeline.

### GAP-19: Overly Broad Database Permissions

**Severity: Medium** | **Effort: Small**

The `javatodev_development` database user is granted `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` (all databases). Application users should have minimal privileges scoped to their specific schemas.

---

## 5. API Design

### GAP-20: Raw ResponseEntity Without Generic Types

**Severity: Medium** | **Effort: Small**

Most controller methods return raw `ResponseEntity` without type parameters:

```java
public ResponseEntity readFundTransfers(Pageable pageable) { ... }
```

This prevents OpenAPI/Swagger from generating accurate response schemas and loses compile-time type safety.

### GAP-21: No Pagination Metadata in Responses

**Severity: Medium** | **Effort: Medium**

Paginated endpoints (GET lists) return only the content array without pagination metadata:

```java
return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
```

Callers have no way to know `totalElements`, `totalPages`, `currentPage`, or `hasNext`.

### GAP-22: Non-Standard REST URL Patterns

**Severity: Low** | **Effort: Small**

| Issue                                    | Example                                       |
|------------------------------------------|-----------------------------------------------|
| Verb in URL                              | `/api/v1/bank-users/register` (should be `POST /api/v1/bank-users`) |
| Verb in URL                              | `/api/v1/bank-users/update/{id}` (should be `PATCH /api/v1/bank-users/{id}`) |
| Inconsistent param naming                | `{account_number}` (snake_case) vs `{id}` (camelCase) |
| Utility account lookup by name in path   | `/api/v1/account/util-account/{account_name}` (should use query param) |

### GAP-23: Wrong OpenAPI Dependency (WebFlux Instead of WebMVC)

**Severity: Medium** | **Effort: Small**

All MVC-based services (core-banking, fund-transfer, user-service, utility-payment) use:
```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

They should use `springdoc-openapi-starter-webmvc-ui` since they are Spring MVC (not WebFlux) applications. This may cause Swagger UI to fail or produce incorrect documentation.

### GAP-24: No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

While endpoints use `/api/v1/` prefix, there is no documented versioning strategy, no version negotiation via headers, and no plan for backward compatibility when v2 is needed.

### GAP-25: Missing Standard HTTP Methods

**Severity: Low** | **Effort: Small**

- No `DELETE` endpoints (e.g., cannot deactivate a user or cancel a transfer)
- No `PUT` endpoints (full resource replacement)
- `PATCH` is only used for user status update

---

## 6. Observability

### GAP-26: Inconsistent Logging Patterns

**Severity: Medium** | **Effort: Small**

- String concatenation in log statements instead of parameterized logging:
  ```java
  log.info("Sending fund transfer request {}" + request.toString()); // Wrong
  log.info("Got fund transfer request from API {}", fundTransferRequest.toString()); // Better but .toString() is redundant
  ```
- `toString()` on request objects may expose sensitive data (passwords, account numbers) in logs
- No structured logging (JSON format) configured
- Log levels not standardized across services

### GAP-27: No Custom Health Check Indicators

**Severity: Low** | **Effort: Small**

Services include `spring-boot-starter-actuator` but have no custom health indicators for:
- Database connectivity health
- Keycloak connectivity health
- Downstream service availability
- Disk space or memory thresholds

Default Spring Boot health endpoints exist but provide only basic checks.

### GAP-28: No Custom Metrics Endpoints

**Severity: Low** | **Effort: Medium**

No custom Micrometer metrics for business-relevant measurements:
- Transaction volumes, success/failure rates
- Transfer amounts (histogram)
- Keycloak operation latency
- Feign call error rates

### GAP-29: Distributed Tracing Not Verified End-to-End

**Severity: Low** | **Effort: Small**

While Zipkin dependencies are included in all services and `feign-micrometer` is present for trace propagation, there is no configuration in the local `application.yml` files for sampling rate, trace export URL, or service name tagging. These are presumably in the external config server, but cannot be verified from the repository alone.

---

## 7. Resilience

### GAP-30: No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

No Resilience4j, Hystrix, or Spring Circuit Breaker is configured anywhere. All three business services make synchronous Feign calls to `core-banking-service`. If core-banking goes down:
- `fund-transfer-service` blocks and fails on every request
- `utility-payment-service` blocks and fails on every request
- `user-service` blocks and fails on registration
- Thread pools exhaust, causing cascading failure across all services

### GAP-31: No Retry Policies on Feign Clients

**Severity: High** | **Effort: Small**

No Feign `Retryer` is configured. Transient network failures (brief DNS issues, connection resets) cause immediate failure with no recovery attempt.

### GAP-32: No Timeout Configuration on Feign Clients

**Severity: High** | **Effort: Small**

No explicit `connectTimeout` or `readTimeout` configured on Feign clients. Defaults depend on the HTTP client implementation and may allow requests to hang indefinitely.

### GAP-33: No Fallback Behavior

**Severity: High** | **Effort: Medium**

No Feign fallback classes or factory methods defined. When core-banking is unavailable, services return raw Feign exceptions rather than graceful degradation responses.

### GAP-34: No Idempotency Keys for Financial Transactions

**Severity: Critical** | **Effort: Medium**

`POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency mechanism. Network retries, client retries, or duplicate submissions can cause:
- Double fund transfers (money debited twice)
- Double utility payments

This is especially dangerous for financial operations.

### GAP-35: No Compensation / Rollback on Feign Failure

**Severity: Critical** | **Effort: Medium**

If the Feign call to core-banking fails **after** the local entity is saved:
- `fund-transfer-service`: Entity stays `PENDING` forever, no retry or compensation
- `utility-payment-service`: Entity stays `PROCESSING` forever, no retry or compensation

There is no saga pattern, no dead-letter queue, no scheduled retry, and no manual reconciliation mechanism.

### GAP-36: Double-Deduction Bug in Balance Calculation

**Severity: Critical** | **Effort: Small**

In `TransactionService.internalFundTransfer()` (lines 90-91):
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

`availableBalance` is computed from the **already-debited** `actualBalance`, causing double subtraction. If a user transfers $100 from a $200 balance:
- `actualBalance` = 200 - 100 = **100** (correct)
- `availableBalance` = 100 - 100 = **0** (wrong; should be 100)

Same bug exists in `utilPayment()` (lines 63-64) and on the credit side (lines 99-100).

### GAP-37: Transaction Entity Uses @OneToOne with CascadeType.ALL

**Severity: High** | **Effort: Small**

`TransactionEntity.account` is mapped as `@OneToOne(cascade = CascadeType.ALL)`. This means:
- Deleting a transaction would cascade-delete the bank account
- The relationship should be `@ManyToOne` (many transactions per account)
- CascadeType should be `NONE` or at most `MERGE`

---

## Summary Table

| ID     | Category          | Gap Description                                    | Severity | Effort |
|--------|-------------------|----------------------------------------------------|----------|--------|
| GAP-01 | Code Organization | No shared library module                           | Medium   | Medium |
| GAP-02 | Code Organization | No multi-module Gradle root project                | Low      | Small  |
| GAP-03 | Code Organization | Inconsistent Feign client package naming           | Low      | Small  |
| GAP-04 | Code Organization | Mapper instantiation pattern (not Spring beans)    | Low      | Small  |
| GAP-05 | Error Handling    | All errors return HTTP 400                         | Critical | Small  |
| GAP-06 | Error Handling    | Stack trace leakage in error responses             | Critical | Small  |
| GAP-07 | Error Handling    | No Feign error decoder in 2 services               | High     | Small  |
| GAP-08 | Error Handling    | Inconsistent error response format                 | Medium   | Small  |
| GAP-09 | Testing           | Only 1 of 6 services has real tests                | High     | Large  |
| GAP-10 | Testing           | No integration tests                               | High     | Large  |
| GAP-11 | Testing           | No contract tests between services                 | Medium   | Large  |
| GAP-12 | Testing           | No controller / web layer tests                    | Medium   | Medium |
| GAP-13 | Security          | No input validation on any request DTO             | Critical | Small  |
| GAP-14 | Security          | Downstream services trust unauthenticated header   | Critical | Medium |
| GAP-15 | Security          | Hardcoded credentials in source code               | Critical | Small  |
| GAP-16 | Security          | CSRF disabled globally                             | Medium   | Small  |
| GAP-17 | Security          | Keycloak singleton not thread-safe                 | Medium   | Small  |
| GAP-18 | Security          | No dependency vulnerability scanning               | Medium   | Small  |
| GAP-19 | Security          | Overly broad database permissions                  | Medium   | Small  |
| GAP-20 | API Design        | Raw ResponseEntity without generic types           | Medium   | Small  |
| GAP-21 | API Design        | No pagination metadata in responses                | Medium   | Medium |
| GAP-22 | API Design        | Non-standard REST URL patterns                     | Low      | Small  |
| GAP-23 | API Design        | Wrong OpenAPI dependency (WebFlux vs WebMVC)       | Medium   | Small  |
| GAP-24 | API Design        | No API versioning strategy documented              | Low      | Medium |
| GAP-25 | API Design        | Missing standard HTTP methods (DELETE, PUT)         | Low      | Small  |
| GAP-26 | Observability     | Inconsistent logging patterns                      | Medium   | Small  |
| GAP-27 | Observability     | No custom health check indicators                  | Low      | Small  |
| GAP-28 | Observability     | No custom metrics endpoints                        | Low      | Medium |
| GAP-29 | Observability     | Distributed tracing config not verifiable          | Low      | Small  |
| GAP-30 | Resilience        | No circuit breakers                                | Critical | Medium |
| GAP-31 | Resilience        | No retry policies on Feign clients                 | High     | Small  |
| GAP-32 | Resilience        | No timeout configuration on Feign clients          | High     | Small  |
| GAP-33 | Resilience        | No fallback behavior                               | High     | Medium |
| GAP-34 | Resilience        | No idempotency keys for financial transactions     | Critical | Medium |
| GAP-35 | Resilience        | No compensation / rollback on Feign failure        | Critical | Medium |
| GAP-36 | Resilience        | Double-deduction bug in balance calculation        | Critical | Small  |
| GAP-37 | Resilience        | @OneToOne with CascadeType.ALL on transactions     | High     | Small  |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 10    |
| High     | 8     |
| Medium   | 13    |
| Low      | 6     |

### Category Distribution

| Category          | Critical | High | Medium | Low | Total |
|-------------------|----------|------|--------|-----|-------|
| Code Organization | 0        | 0    | 1      | 3   | 4     |
| Error Handling    | 2        | 1    | 1      | 0   | 4     |
| Testing           | 0        | 2    | 2      | 0   | 4     |
| Security          | 3        | 0    | 4      | 0   | 7     |
| API Design        | 0        | 0    | 3      | 3   | 6     |
| Observability     | 0        | 0    | 1      | 3   | 4     |
| Resilience        | 5        | 5    | 0      | 0   | 8     |  <!-- note: GAP-37 is High not Critical -->
