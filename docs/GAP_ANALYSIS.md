# Engineering Gap Analysis

> **Repository:** ts-java-spring-boot-internet-banking-microservices  
> **Date:** 2026-05-07  
> **Scope:** All 6 microservices assessed against engineering best practices

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Table](#8-summary-table)

---

## 1. Code Organization

### GAP-01: No Shared Library — Cross-Cutting Code Duplicated Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3-4 services with only package name differences:

| Duplicated Class | Copies | Services |
|---|---|---|
| `GlobalExceptionHandler` | 4 | core-banking, fund-transfer, utility-payment, user-service |
| `SimpleBankingGlobalException` | 4 | core-banking, fund-transfer, utility-payment, user-service |
| `ErrorResponse` | 4 | core-banking, fund-transfer, utility-payment, user-service |
| `AuditAware` | 3 | fund-transfer, utility-payment, user-service |
| `AuditConfig` + `AuditorAwareConfig` | 3 | fund-transfer, utility-payment, user-service |
| `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` | 3 | fund-transfer, utility-payment, user-service |
| `BaseMapper<E,D>` | 4 | core-banking, fund-transfer, utility-payment, user-service |
| `CustomFeignClientConfiguration` | 2 | fund-transfer, utility-payment |

A bug fix or enhancement requires coordinated changes across all copies.

### GAP-02: No Multi-Module Gradle Root Project

**Severity: Medium | Effort: Small**

Each service has its own independent `build.gradle` with no root `settings.gradle` or `build.gradle` to unify dependency versions, plugin versions, or shared configuration. Spring Boot `3.2.4`, Spring Cloud `2023.0.0`, and MySQL Connector `8.4.0` versions are hardcoded independently in each file.

### GAP-03: Duplicate DTO Definitions Across Services

**Severity: High | Effort: Medium**

Payment DTOs are independently defined in multiple services:

| DTO | Defined In |
|---|---|
| `FundTransferRequest` | fund-transfer-service (4 fields) AND core-banking-service (3 fields — missing `authID`) |
| `UtilityPaymentRequest` | fund-transfer-service, utility-payment-service, AND core-banking-service |
| `FundTransferResponse` | fund-transfer-service (Lombok `@Data`) AND core-banking-service (Lombok `@Builder`) |
| `UtilityPaymentResponse` | fund-transfer-service (**empty class**), utility-payment-service, AND core-banking-service |
| `AccountResponse` | fund-transfer-service AND utility-payment-service |

Subtle field differences (e.g., `authID` dropped at boundary) risk silent data loss.

### GAP-04: Dead/Incomplete Code

**Severity: Low | Effort: Small**

- `UtilityPaymentResponse` in fund-transfer-service is an empty class (0 fields)
- `UtilityPaymentRequest` is defined in fund-transfer-service but never used there — fund-transfer-service only handles fund transfers
- Comment in `TransactionService.utilPayment()`: `"we can call third party API to process UTIL payment from payment provider from here"` — not implemented

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` (in all 4 services) maps all exceptions to `ResponseEntity.badRequest()`:

```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

- `EntityNotFoundException` → 400 (should be 404)
- `InsufficientFundsException` → 400 (should be 422 or 409)
- `NullPointerException` → 400 (should be 500)
- Internal server errors → 400 (should be 500)

### GAP-06: Generic Exception Handler Leaks Stack Traces

**Severity: High | Effort: Small**

The catch-all handler returns `"Exception occur inside API " + e`, which includes the full exception class name and message. In production, this leaks implementation details (class names, SQL queries in JPA exceptions, etc.).

### GAP-07: Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` handlers return `ErrorResponse` objects: `{ code, message }`
- Generic `Exception` handlers return a plain string: `"Exception occur inside API ..."`
- Feign client errors from downstream services are not decoded — they propagate as raw `FeignException` with the downstream's response body embedded

### GAP-08: No Feign Error Decoder in Fund Transfer or Utility Payment Services

**Severity: High | Effort: Small**

When core-banking returns an error (e.g., `InsufficientFundsException` → 400), the fund-transfer and utility-payment Feign clients throw a raw `FeignException`. The user-service has a `CustomFeignErrorDecoder` but the payment services do not. This means:
- Error messages from core-banking are lost
- HTTP status codes are not propagated correctly
- The orchestration service cannot distinguish between "insufficient funds" and "account not found"

---

## 3. Testing

### GAP-09: Minimal Test Coverage — Only Core-Banking Has Meaningful Tests

**Severity: Critical | Effort: Large**

| Service | Test Files | Content |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | Unit tests for service layer (mocked repositories) |
| fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Empty context-load test only |
| utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Empty context-load test only |
| user-service | `InternetBankingUserServiceApplicationTests` | Empty context-load test only |
| api-gateway | `InternetBankingApiGatewayApplicationTests` | Empty context-load test only |
| service-registry | `InternetBankingServiceRegistryApplicationTests` | Empty context-load test only |

5 of 6 services have zero meaningful tests.

### GAP-10: No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist that verify:
- Feign client contracts between services
- Database operations with real or embedded databases
- End-to-end payment flows
- Keycloak integration

### GAP-11: No Contract Tests

**Severity: Medium | Effort: Large**

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist. Since DTOs are duplicated across services with subtle differences (GAP-03), contract drift is a real risk.

---

## 4. Security

### GAP-12: No Input Validation on Any Request DTO

**Severity: Critical | Effort: Small**

Zero Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`, `@Email`) on any request DTO across all services. No `@Valid` annotation on any controller method parameter.

Consequences:
- `null` amount → `NullPointerException` in balance comparison
- Negative amount → debits reversed (sender gains, receiver loses)
- Empty account string → `EntityNotFoundException` (cryptic error)
- Zero amount → meaningless transaction created and recorded

### GAP-13: No Auth on Downstream Services — JWT Validated Only at Gateway

**Severity: High | Effort: Medium**

JWT validation happens exclusively at the API Gateway. Downstream services (core-banking, fund-transfer, utility-payment, user-service) have **no Spring Security configuration** — they trust the `X-Auth-Id` header injected by the gateway.

If any service is accessed directly (bypassing gateway), there is no authentication. The `X-Auth-Id` header can be spoofed by any caller.

### GAP-14: Hardcoded Credentials in Source Control

**Severity: Critical | Effort: Small**

| File | Credentials |
|---|---|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym`, Keycloak admin: `admin`/`password`, PostgreSQL: `keycloak`/`password` |
| `docker-compose/mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | MySQL user: `javatodev_development`, password: `oPItyPticIAt` |

These are committed to source control. While acceptable for local dev, the passwords should be externalized via `.env` files or Docker secrets.

### GAP-15: Overly Broad Database Privileges

**Severity: Medium | Effort: Small**

`privileges.sql` grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` to the application user. This includes `DROP` on all databases — the application should only have DML privileges on its own schemas.

### GAP-16: No Account Status Validation Before Transaction

**Severity: High | Effort: Small**

`AccountStatus` enum exists with values `PENDING`, `ACTIVE`, `DORMANT`, `BLOCKED`, but `TransactionService` never checks account status. Transfers and payments proceed on BLOCKED or DORMANT accounts.

### GAP-17: Password Handling in User DTO

**Severity: Medium | Effort: Small**

The `User` DTO includes a `password` field that flows through the registration endpoint. This password is sent in clear text in the request body and is logged via `log.info("Creating user with {}", request.toString())` — Lombok `@Data` generates `toString()` that includes all fields including password.

---

## 5. API Design

### GAP-18: Wrong OpenAPI Dependency — WebFlux UI on MVC Services

**Severity: Medium | Effort: Small**

All 4 MVC-based services (core-banking, fund-transfer, utility-payment, user-service) use:
```groovy
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

These services use Spring MVC (not WebFlux). The correct dependency is `springdoc-openapi-starter-webmvc-ui`. Swagger UI may not render correctly or at all.

### GAP-19: No Pagination Metadata in List Responses

**Severity: Medium | Effort: Small**

List endpoints (fund transfers, utility payments, users) accept `Pageable` parameters but return raw `List<T>` instead of a `Page<T>` wrapper. Clients receive no pagination metadata (total elements, total pages, current page, has next/previous).

Example in `FundTransferService`:
```java
return mapper.convertToDtoList(fundTransferRepository.findAll(pageable).getContent());
```

The `Page` object is fetched but only `.getContent()` (the list) is returned — metadata is discarded.

### GAP-20: Inconsistent API Path Naming

**Severity: Low | Effort: Small**

| Service | Path Style |
|---|---|
| Fund Transfer | `/api/v1/transfer` (noun, singular) |
| Utility Payment | `/api/v1/utility-payment` (noun, singular) |
| User Service | `/api/v1/bank-users` (noun, plural) |
| Core Banking Accounts | `/api/v1/account/bank-account/{n}` (nested singular) |
| Core Banking Transactions | `/api/v1/transaction/fund-transfer` (nested action) |
| Core Banking Users | `/api/v1/user/{id}` (singular) |

No consistent plural/singular convention. Mixed use of nouns and actions.

### GAP-21: No API Versioning Strategy

**Severity: Low | Effort: Small**

All APIs use `/api/v1/` but there is no mechanism for version negotiation, no content-type versioning, and no strategy documented for introducing `v2`.

### GAP-22: No Filtering or Sorting on List Endpoints

**Severity: Low | Effort: Medium**

List endpoints accept Spring's default `Pageable` (page, size, sort) but provide no domain-specific filtering (e.g., filter transfers by date range, account, status; filter payments by provider).

### GAP-23: Raw `ResponseEntity` Without Type Parameters

**Severity: Low | Effort: Small**

Most controller methods return `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This reduces OpenAPI documentation accuracy and IDE support.

---

## 6. Observability

### GAP-24: Sensitive Data Logged in Plain Text

**Severity: High | Effort: Small**

Multiple controllers log full request objects including potentially sensitive data:
- `FundTransferController`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` — logs account numbers and amounts
- `UtilityPaymentService`: `log.info("Utility payment processing {}", paymentRequest.toString())` — logs account and payment details
- `UserController`: `log.info("Creating user with {}", request.toString())` — logs password (via Lombok `@Data` toString)
- `TransactionController`: `log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString())`

### GAP-25: No Structured Logging

**Severity: Medium | Effort: Medium**

All logging uses unstructured `Slf4j` string interpolation. No MDC context enrichment for trace IDs, user IDs, or transaction IDs. Log aggregation and search would be difficult without structured JSON logging.

### GAP-26: No Custom Health Check Indicators

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` for health endpoints but define no custom `HealthIndicator` beans. Health checks do not verify:
- Database connectivity (default Spring Boot auto-config may cover this partially)
- Feign client reachability to core-banking
- Keycloak availability (for user-service)
- Config Server availability

### GAP-27: No Custom Metrics

**Severity: Low | Effort: Medium**

No custom Micrometer metrics defined. Business metrics not tracked:
- Fund transfer count/amount per time window
- Utility payment count/amount per provider
- User registration rate
- Failed transaction rate
- Feign call latency percentiles

---

## 7. Resilience

### GAP-28: No Circuit Breakers

**Severity: Critical | Effort: Medium**

No Resilience4j, Hystrix, or any circuit breaker library in any `build.gradle`. If core-banking-service becomes slow or unavailable:
- Fund-transfer-service threads block indefinitely on Feign calls
- Utility-payment-service threads block indefinitely
- User-service registration blocks on core-banking lookup
- Thread pool exhaustion cascades to API Gateway

### GAP-29: No Retry Policies

**Severity: High | Effort: Small**

No Feign retry configuration. Transient failures (network hiccups, temporary 503s) immediately fail the entire operation. Spring Cloud OpenFeign's default retryer is `Retryer.NEVER_RETRY`.

### GAP-30: No Timeouts Configured on Feign Clients

**Severity: High | Effort: Small**

No `connectTimeout` or `readTimeout` specified in Feign client configuration. Default Feign timeouts apply (10s connect, 60s read), which are too generous for an interactive banking application.

### GAP-31: No Fallback Behavior

**Severity: High | Effort: Medium**

No Feign fallback classes or factory implementations. When core-banking is unavailable:
- Payment services fail with raw `FeignException`
- No graceful degradation, cached responses, or queued-for-later patterns

### GAP-32: No Idempotency Keys on Payment Endpoints

**Severity: Critical | Effort: Medium**

No client-supplied idempotency token on `POST /transfer` or `POST /utility-payment`. If a client retries a timed-out request:
- The payment is processed a second time
- The customer is double-debited
- Two separate transactions are created with different UUIDs

### GAP-33: No Compensation / Saga for Distributed Transactions

**Severity: High | Effort: Large**

The fund-transfer flow spans two services (fund-transfer → core-banking) with two databases. If the Feign call to core-banking succeeds but the subsequent status update in fund-transfer-service fails:
- Core-banking has executed the transfer (balances changed)
- Fund-transfer-service still shows `PENDING`
- No reconciliation or compensation mechanism exists

### GAP-34: Available Balance Double-Deduction Bug

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` (lines 90-91):
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
After line 90, `actualBalance` is already reduced. Line 91 then subtracts `amount` again from the already-reduced `actualBalance` to set `availableBalance`. Same bug exists in `utilPayment()` (lines 63-64) and in the credit side (lines 99-100).

### GAP-35: No Amount Limits

**Severity: High | Effort: Small**

No minimum amount, maximum amount, or daily cumulative limits on any payment endpoint. A user could transfer their entire balance in a single transaction or make unlimited micro-transactions.

### GAP-36: No Duplicate Detection

**Severity: High | Effort: Medium**

Beyond idempotency keys (GAP-32), there is no server-side duplicate detection. No checks for:
- Same sender + receiver + amount within a time window
- Same utility payment + reference number within a time window
- Transaction reference uniqueness constraints in the database

### GAP-37: `@Transactional` Missing on Orchestration Services

**Severity: High | Effort: Small**

`FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` are not annotated with `@Transactional`. The sequence save-PENDING → Feign-call → save-SUCCESS is not atomic within the orchestration service's own database. A failure between the Feign response and the status update leaves the record in an inconsistent state.

---

## 8. Summary Table

| ID | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| GAP-01 | No shared library — duplicated cross-cutting code | Code Organization | High | Medium |
| GAP-02 | No multi-module Gradle root project | Code Organization | Medium | Small |
| GAP-03 | Duplicate DTO definitions across services | Code Organization | High | Medium |
| GAP-04 | Dead/incomplete code | Code Organization | Low | Small |
| GAP-05 | All errors return HTTP 400 | Error Handling | High | Small |
| GAP-06 | Generic exception handler leaks stack traces | Error Handling | High | Small |
| GAP-07 | Inconsistent error response format | Error Handling | Medium | Small |
| GAP-08 | No Feign error decoder in payment services | Error Handling | High | Small |
| GAP-09 | Minimal test coverage (5/6 services untested) | Testing | Critical | Large |
| GAP-10 | No integration tests | Testing | High | Large |
| GAP-11 | No contract tests | Testing | Medium | Large |
| GAP-12 | No input validation on any request DTO | Security | Critical | Small |
| GAP-13 | No auth on downstream services | Security | High | Medium |
| GAP-14 | Hardcoded credentials in source control | Security | Critical | Small |
| GAP-15 | Overly broad database privileges | Security | Medium | Small |
| GAP-16 | No account status validation before transaction | Security | High | Small |
| GAP-17 | Password logged in plain text via toString() | Security | Medium | Small |
| GAP-18 | Wrong OpenAPI dependency (WebFlux on MVC) | API Design | Medium | Small |
| GAP-19 | No pagination metadata in list responses | API Design | Medium | Small |
| GAP-20 | Inconsistent API path naming | API Design | Low | Small |
| GAP-21 | No API versioning strategy | API Design | Low | Small |
| GAP-22 | No filtering or sorting on list endpoints | API Design | Low | Medium |
| GAP-23 | Raw ResponseEntity without type parameters | API Design | Low | Small |
| GAP-24 | Sensitive data logged in plain text | Observability | High | Small |
| GAP-25 | No structured logging | Observability | Medium | Medium |
| GAP-26 | No custom health check indicators | Observability | Low | Small |
| GAP-27 | No custom metrics | Observability | Low | Medium |
| GAP-28 | No circuit breakers | Resilience | Critical | Medium |
| GAP-29 | No retry policies | Resilience | High | Small |
| GAP-30 | No timeouts on Feign clients | Resilience | High | Small |
| GAP-31 | No fallback behavior | Resilience | High | Medium |
| GAP-32 | No idempotency keys on payment endpoints | Resilience | Critical | Medium |
| GAP-33 | No compensation / saga pattern | Resilience | High | Large |
| GAP-34 | Available balance double-deduction bug | Resilience | Critical | Small |
| GAP-35 | No amount limits | Resilience | High | Small |
| GAP-36 | No duplicate detection | Resilience | High | Medium |
| GAP-37 | `@Transactional` missing on orchestration services | Resilience | High | Small |

**Totals by severity:** Critical: 7 | High: 17 | Medium: 8 | Low: 5
