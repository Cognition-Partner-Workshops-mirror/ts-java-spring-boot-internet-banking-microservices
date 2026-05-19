# Internet Banking Microservices — Engineering Standards Gap Analysis

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

### GAP-01: No Shared Library for Duplicated Code

**Severity:** High | **Effort:** Medium

The following classes are copy-pasted across 3–4 services with near-identical implementations:

- `BaseMapper` — abstract generic mapper (core-banking, fund-transfer, user-service, utility-payment)
- `AuditAware` — JPA audit superclass (fund-transfer, user-service, utility-payment)
- `AuditorAwareConfig` + `AuditConfig` — JPA auditing configuration (fund-transfer, user-service, utility-payment)
- `GlobalExceptionHandler` — controller advice (core-banking, fund-transfer, user-service, utility-payment)
- `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` — auth filter chain (fund-transfer, user-service, utility-payment)
- `ErrorResponse` — error DTO (core-banking, fund-transfer, user-service, utility-payment)
- `SimpleBankingGlobalException` — base exception (core-banking, fund-transfer, user-service, utility-payment)
- `EntityNotFoundException` — not-found exception (core-banking, user-service)
- `GlobalErrorCode` — error code constants (core-banking, user-service)

**Impact:** Bug fixes or enhancements to shared logic must be applied manually in every service. Divergence between copies is already visible (e.g., `GlobalExceptionHandler` uses `ErrorResponse.builder()` in some services but `new ErrorResponse()` in fund-transfer).

### GAP-02: No Multi-Module Gradle Build

**Severity:** Medium | **Effort:** Medium

Each of the 7 services has its own independent `gradlew`, `build.gradle`, and `gradle/wrapper/`. There is no root-level `settings.gradle` or multi-module project structure. This means:

- No centralized dependency version management
- No shared build plugin configuration
- Each service must be built and tested individually
- Dependency version drift is likely over time

### GAP-03: Inconsistent Package Structure Across Services

**Severity:** Low | **Effort:** Small

While services follow a similar package layout (`controller`, `service`, `model`, `exception`), there are inconsistencies:

- `fund-transfer-service` uses `model.repository` while `utility-payment-service` uses `repository` (top-level)
- `fund-transfer-service` uses `model.dto.request/response` while `utility-payment-service` uses `model.rest.request/response`
- `user-service` has `configuration.keycloak` and `configuration.feign` sub-packages; `fund-transfer-service` puts `CustomFeignClientConfiguration` directly in `configuration`
- `core-banking-service` puts repositories in `repository` package; others use `model.repository`

### GAP-04: Request DTO Reuse Across Service Boundaries

**Severity:** Low | **Effort:** Small

`FundTransferRequest` and `UtilityPaymentRequest` DTOs are duplicated in both the orchestrating services (fund-transfer, utility-payment) and the core-banking service. These are structurally identical but maintained as separate classes. With a shared library (GAP-01), these could be shared across services.

---

## 2. Error Handling

### GAP-05: All Errors Return HTTP 400 Bad Request

**Severity:** Critical | **Effort:** Small

Every `GlobalExceptionHandler` across all 4 services returns `ResponseEntity.badRequest()` (HTTP 400) for **all** exceptions, including:

- Entity not found → should be **404 Not Found**
- Insufficient funds → should be **422 Unprocessable Entity** or **409 Conflict**
- Unexpected server errors → should be **500 Internal Server Error**
- Unauthorized requests → should be **401 Unauthorized** or **403 Forbidden**

This makes it impossible for API consumers to distinguish between client errors, business rule violations, and server failures.

### GAP-06: Generic Exception Catch-All Leaks Internal Details

**Severity:** High | **Effort:** Small

The catch-all `@ExceptionHandler({Exception.class})` returns the full exception `toString()` in the response body:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This leaks stack traces, class names, and internal implementation details to API consumers, which is both a security risk and poor UX.

### GAP-07: Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

- Business exceptions return a structured `ErrorResponse { code, message }` object
- Generic exceptions return a raw string: `"Exception occur inside API " + e`

API consumers cannot rely on a consistent error response schema for parsing and display.

### GAP-08: No Feign Error Decoder in Fund Transfer Service

**Severity:** High | **Effort:** Small

The `internet-banking-fund-transfer-service` has no `ErrorDecoder` configured for its Feign client (`BankingCoreFeignClient`). When `core-banking-service` returns an error:

- The raw Feign exception propagates up
- The `GlobalExceptionHandler` catches it as a generic `Exception` and leaks internals

In contrast, `internet-banking-user-service` has a proper `CustomFeignErrorDecoder` that extracts structured error responses. The `internet-banking-utility-payment-service` also lacks a custom error decoder.

### GAP-09: No Error Handling for Failed Feign Calls in Transaction Orchestration

**Severity:** Critical | **Effort:** Medium

In both `FundTransferService` and `UtilityPaymentService`:

- The entity is saved with `PENDING`/`PROCESSING` status before the Feign call
- If the Feign call fails, the entity is **never** updated — it stays in `PENDING`/`PROCESSING` forever
- There is no try-catch around the Feign call to handle failures
- No compensation logic or retry mechanism exists
- No dead-letter or alerting for stuck transactions

---

## 3. Testing

### GAP-10: Minimal Unit Test Coverage

**Severity:** High | **Effort:** Large

Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have only the auto-generated empty `ApplicationTests` context-load test.

| Service | Test Classes | Meaningful Tests |
|---------|-------------|-----------------|
| core-banking-service | 4 | 3 (AccountServiceTest, TransactionServiceTest, UserServiceTest) |
| internet-banking-api-gateway | 1 | 0 (empty context load) |
| internet-banking-config-server | 1 | 0 (empty context load) |
| internet-banking-fund-transfer-service | 1 | 0 (empty context load) |
| internet-banking-user-service | 1 | 0 (empty context load) |
| internet-banking-utility-payment-service | 1 | 0 (empty context load) |

### GAP-11: No Integration Tests

**Severity:** High | **Effort:** Large

There are no integration tests that verify:

- API endpoint behavior end-to-end (using `@SpringBootTest` with `WebEnvironment.RANDOM_PORT` or `MockMvc`)
- Database interactions with actual schema (Testcontainers or embedded DB)
- Feign client interactions between services
- Security configuration (JWT validation, public endpoint access)

### GAP-12: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between:

- `fund-transfer-service` ↔ `core-banking-service`
- `utility-payment-service` ↔ `core-banking-service`
- `user-service` ↔ `core-banking-service`

API changes in `core-banking-service` could silently break downstream consumers.

### GAP-13: No CI Pipeline to Run Tests

**Severity:** High | **Effort:** Medium

There is no CI/CD pipeline configured (no GitHub Actions, Jenkinsfile, or equivalent). Tests are only run manually. There is no automated gate to prevent merging code that breaks existing functionality.

---

## 4. Security

### GAP-14: No Input Validation on Any Request DTOs

**Severity:** Critical | **Effort:** Small

No request DTO uses `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Positive`, or any Bean Validation annotations. No controller method has `@Valid` on `@RequestBody` parameters.

Examples of unvalidated inputs:

- Fund transfer: `amount` can be null, zero, or negative
- Fund transfer: `fromAccount` and `toAccount` can be null or empty
- User registration: `email`, `identification`, `password` can be null or empty
- Utility payment: `providerId`, `amount`, `referenceNumber` can be null

This leads to NPEs, nonsensical transactions, and potential data corruption.

### GAP-15: No Authentication on Downstream Services

**Severity:** Critical | **Effort:** Medium

JWT validation occurs **only** at the API Gateway. Downstream services (`core-banking`, `fund-transfer`, `user-service`, `utility-payment`) have **no authentication** configured. They trust the `X-Auth-Id` header injected by the gateway, which can be spoofed by anyone with direct network access to the services.

In production, any service on the Docker network (or anyone who can reach ports 8083–8092) can call downstream APIs without authentication.

### GAP-16: Hardcoded Credentials in Source Code

**Severity:** Critical | **Effort:** Small

The following credentials are hardcoded in version-controlled files:

- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`)
- `docker-compose.yml`: Keycloak admin password (`password`)
- `docker-compose.yml`: Keycloak DB password (`password`)
- `privileges.sql`: Application DB user password (`oPItyPticIAt`)
- Test `application.yml` files: Keycloak client secret (`e8548d56-d743-45ef-8655-063c9cd96759`)
- README: Test user credentials (`ib_admin@javatodev.com` / `5V7huE3G86uB`)

### GAP-17: Keycloak Singleton Instance is Not Thread-Safe

**Severity:** Medium | **Effort:** Small

`KeycloakProperties.getInstance()` uses a lazy-initialization pattern without synchronization:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

In a multi-threaded environment (concurrent HTTP requests), this can lead to race conditions and multiple Keycloak client instances being created.

### GAP-18: CSRF Disabled on API Gateway

**Severity:** Low | **Effort:** Small

CSRF protection is explicitly disabled: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is acceptable for a pure REST API consumed by non-browser clients, but should be documented as a conscious decision.

### GAP-19: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

No dependency vulnerability scanning is configured (e.g., OWASP Dependency-Check Gradle plugin, Snyk, or GitHub Dependabot). Vulnerable transitive dependencies could go undetected.

---

## 5. API Design

### GAP-20: Raw ResponseEntity Without Generic Types

**Severity:** Medium | **Effort:** Small

Most controller methods return `ResponseEntity` without type parameters (raw type):

```java
public ResponseEntity getBankAccount(...) { ... }
public ResponseEntity fundTransfer(...) { ... }
```

This loses compile-time type safety and makes it harder for OpenAPI/Swagger to generate accurate documentation. Should be `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc.

### GAP-21: Wrong OpenAPI Dependency for MVC Services

**Severity:** Medium | **Effort:** Small

The `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service` are Spring MVC (Servlet) applications but use the **WebFlux** OpenAPI dependency:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```

The correct dependency for MVC services is:

```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
```

This may cause Swagger UI to fail to load or behave incorrectly.

### GAP-22: No API Versioning Strategy

**Severity:** Low | **Effort:** Small

While all APIs use `/api/v1/` prefix, there is no documented versioning strategy for handling breaking changes. No headers (`Accept-Version`), query parameters, or content negotiation are used for version management.

### GAP-23: Pagination Response Missing Metadata

**Severity:** Medium | **Effort:** Small

Paginated endpoints (e.g., `GET /api/v1/transfer`, `GET /api/v1/bank-users`) return raw `List<T>` instead of a pagination wrapper. The response does not include:

- Total number of elements
- Total number of pages
- Current page number
- Page size
- Has next/previous page indicators

API consumers cannot implement proper pagination UI without this metadata.

### GAP-24: No Filtering or Sorting Documentation

**Severity:** Low | **Effort:** Small

While Spring's `Pageable` parameter supports `sort` query parameters, there is no documentation of which fields are sortable or how to filter results. No dedicated filter parameters exist (e.g., filter by status, date range, account number).

### GAP-25: Inconsistent Resource Naming

**Severity:** Low | **Effort:** Small

- Fund transfer: `/api/v1/transfer` (singular)
- Utility payment: `/api/v1/utility-payment` (singular with hyphen)
- Users: `/api/v1/bank-users` (plural with hyphen)
- Core accounts: `/api/v1/account` (singular)
- Convention should be consistent — REST best practice favors plural nouns (e.g., `/transfers`, `/payments`, `/users`, `/accounts`)

---

## 6. Observability

### GAP-26: No Structured Logging

**Severity:** Medium | **Effort:** Small

All services use default Spring Boot logging (Logback with plain-text format). For production environments, structured JSON logging (e.g., using `logstash-logback-encoder`) would enable better log aggregation and searching in tools like ELK or Loki.

### GAP-27: Sensitive Data in Log Statements

**Severity:** High | **Effort:** Small

Several log statements output full request DTOs using `toString()`:

```java
log.info("Creating user with {}", request.toString());
log.info("Sending fund transfer request {}" + request.toString());
log.info("Utility payment processing {}", paymentRequest.toString());
```

These may log sensitive data including passwords (in `User` DTO for registration), account numbers, and transaction amounts.

### GAP-28: Health Check Endpoints Not Customized

**Severity:** Low | **Effort:** Small

All services include `spring-boot-starter-actuator` and expose `/actuator/**` (allowed through security), but:

- No custom health indicators for critical dependencies (database connectivity, Keycloak reachability, Eureka registration)
- No readiness/liveness probe configuration for container orchestration
- No info endpoint customization beyond `git.properties`

### GAP-29: No Metrics Endpoints or Dashboards

**Severity:** Medium | **Effort:** Medium

While Micrometer is included (via actuator), there are:

- No custom business metrics (e.g., transfer count, payment volume, error rates)
- No Prometheus scrape configuration
- No Grafana dashboards defined
- No alerting rules for SLA violations

### GAP-30: Distributed Tracing Coverage Gaps

**Severity:** Low | **Effort:** Small

Zipkin integration is set up with Brave bridge and feign-micrometer, but:

- No custom span annotations for business-critical operations
- No trace ID propagation to error responses (useful for debugging)
- Tracing sampling rate is not explicitly configured (defaults to 10%)

---

## 7. Resilience

### GAP-31: No Circuit Breakers

**Severity:** Critical | **Effort:** Medium

No circuit breaker library is configured (Resilience4j, Hystrix, or Spring Cloud Circuit Breaker). If `core-banking-service` goes down:

- `fund-transfer-service` will hang or throw exceptions on every request
- `utility-payment-service` will hang or throw exceptions on every request
- `user-service` will fail on registration and user listing
- Cascading failures will propagate to all consumers via the gateway

### GAP-32: No Retry Policies for Feign Clients

**Severity:** High | **Effort:** Small

No retry configuration exists for Feign clients. Transient network failures (brief connectivity blips, DNS resolution delays, connection resets) will immediately fail the request without any retry attempt.

Spring Cloud OpenFeign supports configurable `Retryer` beans, but none are defined.

### GAP-33: No Timeout Configuration for Feign Clients

**Severity:** High | **Effort:** Small

No explicit timeout configuration for Feign client connections or reads. The default timeouts are:

- Connect timeout: 10 seconds
- Read timeout: 60 seconds

These defaults are too generous for an internal microservice call and could lead to thread pool exhaustion if `core-banking-service` becomes slow.

### GAP-34: No Fallback Behavior

**Severity:** Medium | **Effort:** Medium

When downstream services are unavailable, there are no fallback mechanisms:

- No cached responses for read operations
- No graceful degradation (e.g., returning partial data)
- No queue-based fallback for write operations
- Error messages provide no actionable guidance to clients

### GAP-35: No Idempotency Keys for Financial Transactions

**Severity:** Critical | **Effort:** Medium

The `POST /api/v1/transfer` and `POST /api/v1/utility-payment` endpoints have no idempotency protection. If a client retries a failed request (due to timeout or network error), the same transaction can be processed multiple times, causing double charges.

Financial transaction endpoints must support idempotency keys (e.g., client-generated UUID in a header) to prevent duplicate processing.

### GAP-36: No Database Transaction Boundaries in Orchestration Services

**Severity:** High | **Effort:** Small

`FundTransferService` and `UtilityPaymentService` are not annotated with `@Transactional`. The entity save and status update happen in separate database transactions. If the application crashes between the initial save and the status update, data inconsistency occurs.

### GAP-37: wait-for-it.sh Timeout Too Short for Cold Starts

**Severity:** Low | **Effort:** Small

The `wait-for-it.sh` timeout is 50 seconds per dependency. During cold starts (first-time MySQL initialization with Flyway migrations and Keycloak realm import), 50 seconds may not be sufficient, causing application services to start before their dependencies are ready.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-01 | Code Organization | No shared library for duplicated code | High | Medium |
| GAP-02 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-04 | Code Organization | Request DTO reuse across boundaries | Low | Small |
| GAP-05 | Error Handling | All errors return HTTP 400 | Critical | Small |
| GAP-06 | Error Handling | Generic exception catch-all leaks internals | High | Small |
| GAP-07 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-08 | Error Handling | No Feign error decoder in fund-transfer service | High | Small |
| GAP-09 | Error Handling | No error handling for failed Feign calls | Critical | Medium |
| GAP-10 | Testing | Minimal unit test coverage | High | Large |
| GAP-11 | Testing | No integration tests | High | Large |
| GAP-12 | Testing | No contract tests between services | Medium | Large |
| GAP-13 | Testing | No CI pipeline | High | Medium |
| GAP-14 | Security | No input validation on request DTOs | Critical | Small |
| GAP-15 | Security | No authentication on downstream services | Critical | Medium |
| GAP-16 | Security | Hardcoded credentials in source code | Critical | Small |
| GAP-17 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-18 | Security | CSRF disabled (intentional for REST API) | Low | Small |
| GAP-19 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-20 | API Design | Raw ResponseEntity without generic types | Medium | Small |
| GAP-21 | API Design | Wrong OpenAPI dependency (webflux vs webmvc) | Medium | Small |
| GAP-22 | API Design | No API versioning strategy | Low | Small |
| GAP-23 | API Design | Pagination response missing metadata | Medium | Small |
| GAP-24 | API Design | No filtering or sorting documentation | Low | Small |
| GAP-25 | API Design | Inconsistent resource naming | Low | Small |
| GAP-26 | Observability | No structured logging | Medium | Small |
| GAP-27 | Observability | Sensitive data in log statements | High | Small |
| GAP-28 | Observability | Health checks not customized | Low | Small |
| GAP-29 | Observability | No metrics or dashboards | Medium | Medium |
| GAP-30 | Observability | Distributed tracing coverage gaps | Low | Small |
| GAP-31 | Resilience | No circuit breakers | Critical | Medium |
| GAP-32 | Resilience | No retry policies for Feign clients | High | Small |
| GAP-33 | Resilience | No timeout configuration for Feign clients | High | Small |
| GAP-34 | Resilience | No fallback behavior | Medium | Medium |
| GAP-35 | Resilience | No idempotency keys for financial transactions | Critical | Medium |
| GAP-36 | Resilience | No @Transactional in orchestration services | High | Small |
| GAP-37 | Resilience | wait-for-it.sh timeout too short | Low | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 7 |
| High | 11 |
| Medium | 12 |
| Low | 7 |
| **Total** | **37** |

### Critical Gaps Requiring Immediate Attention

1. **GAP-05:** All errors return HTTP 400 — impossible for clients to handle errors correctly
2. **GAP-09:** No error handling for failed Feign calls — stuck transactions with no recovery
3. **GAP-14:** No input validation — null/negative amounts can corrupt financial data
4. **GAP-15:** No auth on downstream services — anyone on the network can call internal APIs
5. **GAP-16:** Hardcoded credentials — secrets in version control
6. **GAP-31:** No circuit breakers — single service failure cascades to entire system
7. **GAP-35:** No idempotency — duplicate financial transactions possible
