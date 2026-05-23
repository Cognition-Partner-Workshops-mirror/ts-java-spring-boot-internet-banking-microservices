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

### GAP-CO-01: No Shared Library for Duplicated Code

**Severity: High** | **Effort: Medium**

Multiple classes are duplicated verbatim across services:
- `AuditAware` — identical in `fund-transfer-service`, `user-service`, `utility-payment-service`
- `BaseMapper` — identical in all four application services
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` — near-identical copies in every service
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — duplicated in `fund-transfer-service`, `user-service`, `utility-payment-service`
- `TransactionStatus` enum — duplicated in `fund-transfer-service` and `utility-payment-service`
- `CustomFeignClientConfiguration` — duplicated in `fund-transfer-service` and `utility-payment-service`

**Impact**: Any bug fix or behavioral change must be applied in multiple places, creating a high risk of inconsistency.

### GAP-CO-02: No Root Build File or Multi-Module Gradle Project

**Severity: Medium** | **Effort: Medium**

Each service has its own standalone `build.gradle` with a Gradle Wrapper. There is no root-level `settings.gradle` or multi-module build. This means:
- Dependency versions are duplicated (e.g., `mysql-connector-j:8.4.0`, `springdoc-openapi-starter-webflux-ui:2.1.0`).
- No single command to build/test all services.
- No Gradle version catalog or BOM for internal dependency alignment.

### GAP-CO-03: Inconsistent Package Structure Across Services

**Severity: Low** | **Effort: Small**

While all services use `com.javatodev.finance`, sub-package naming differs:
- `fund-transfer-service` uses `model.repository` while `utility-payment-service` uses `repository`
- `user-service` uses `model.repository` and `model.rest.response` while `fund-transfer-service` uses `model.dto.response`
- Feign clients: `service.rest.client.BankingCoreFeignClient` vs `service.rest.BankingCoreRestClient`

---

## 2. Error Handling

### GAP-EH-01: Generic Exception Catch-All Returns 400 for Everything

**Severity: High** | **Effort: Small**

All four `GlobalExceptionHandler` implementations have:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

**Issues**:
- **All unhandled exceptions return HTTP 400** (Bad Request), including 500-level server errors (NPE, DB connection failures, timeout exceptions).
- The response body leaks the full `Exception.toString()` output, potentially exposing stack traces, class names, and internal details.
- The response format is a raw string, inconsistent with the `ErrorResponse` JSON structure used for business exceptions.

### GAP-EH-02: Raw `ResponseEntity` Without Type Parameters

**Severity: Medium** | **Effort: Small**

Nearly all controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<T>`. Examples:
- `AccountController.getBankAccount()` returns `ResponseEntity` instead of `ResponseEntity<BankAccount>`
- `TransactionController.fundTransfer()` returns `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`

**Impact**: No compile-time type safety; makes it harder for tools (OpenAPI generators, IDE inspections) to infer response types.

### GAP-EH-03: No Specific HTTP Status Codes for Different Error Types

**Severity: Medium** | **Effort: Small**

- `EntityNotFoundException` results in HTTP 400 (via the base `SimpleBankingGlobalException` handler) instead of HTTP 404.
- `InsufficientFundsException` results in HTTP 400 instead of HTTP 422 (Unprocessable Entity).
- `UserAlreadyRegisteredException` results in HTTP 400 instead of HTTP 409 (Conflict).
- No handling for `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, etc.

### GAP-EH-04: No Error Handling for Feign Client Failures

**Severity: High** | **Effort: Medium**

When a Feign call to `core-banking-service` fails (network error, timeout, 500 response), the exception propagates unhandled to the global catch-all, returning HTTP 400 with the raw exception string. The `user-service` has a `CustomFeignErrorDecoder` class, but the `fund-transfer-service` and `utility-payment-service` only configure `Logger.Level.FULL` — they do not decode or wrap Feign errors.

---

## 3. Testing

### GAP-TE-01: Test Coverage Limited to core-banking-service Only

**Severity: High** | **Effort: Large**

Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` — totaling ~15 test methods). The other six services only have a boilerplate `*ApplicationTests.java` (Spring context load test) with no additional test classes.

**Missing test coverage**:
- `FundTransferService` — no tests for the orchestration logic
- `UtilityPaymentService` — no tests for the orchestration logic
- `UserService` (user-service) — no tests for registration, approval, Keycloak integration
- `KeycloakUserService` — no tests
- All controller layers — no `@WebMvcTest` tests
- All Feign clients — no contract tests or WireMock tests

### GAP-TE-02: No Integration Tests

**Severity: High** | **Effort: Large**

There are no `@SpringBootTest` integration tests, no Testcontainers usage, and no end-to-end tests that verify the interaction between services. The only test configuration exists in `core-banking-service/src/test/resources/application.yml` (H2 in-memory).

### GAP-TE-03: No Contract Tests Between Services

**Severity: Medium** | **Effort: Large**

There are no Spring Cloud Contract tests, Pact tests, or any other form of consumer-driven contract testing between the Feign clients and the core-banking-service. If the core service changes an API response format, downstream services will break silently.

### GAP-TE-04: ApplicationTests Classes Fail Without Infrastructure

**Severity: Medium** | **Effort: Small**

The `*ApplicationTests.java` classes in `api-gateway`, `config-server`, `service-registry`, `user-service`, `fund-transfer-service`, and `utility-payment-service` attempt to load the full Spring context, which may require Eureka, Config Server, MySQL, or Keycloak to be running. Without proper test configuration, these tests are either skipped or failing.

---

## 4. Security

### GAP-SE-01: Database Credentials Hardcoded in Docker Compose and SQL Files

**Severity: Critical** | **Effort: Small**

- MySQL root password hardcoded in `docker-compose.yml` and `docker-compose/mysql/Dockerfile`: `woVERANKliGharym`
- Application database user/password in `docker-compose/mysql/privileges.sql`: `javatodev_development` / `oPItyPticIAt`
- Keycloak admin credentials in `docker-compose.yml`: `admin` / `password`
- Keycloak DB credentials: `keycloak` / `password`

These credentials are committed to source control in plaintext.

### GAP-SE-02: No Input Validation on API Endpoints

**Severity: High** | **Effort: Medium**

No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` annotations on any request bodies or path variables across any service. Examples:
- `FundTransferRequest` accepts `null` for `fromAccount`, `toAccount`, or `amount`
- `UtilityPaymentRequest` accepts `null` for `providerId` or `amount`
- User registration accepts empty email/password
- No request body size limits

### GAP-SE-03: CSRF Disabled Without Alternative Protection

**Severity: Medium** | **Effort: Small**

The API Gateway's `SecurityConfiguration` disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While this is standard for stateless REST APIs using JWT tokens, it should be explicitly documented. The gateway also doesn't enforce CORS policies.

### GAP-SE-04: No Rate Limiting

**Severity: Medium** | **Effort: Medium**

There is no rate limiting configured at the API Gateway or any service level. The application is vulnerable to brute-force attacks on the registration endpoint and denial-of-service via high-volume fund transfer requests.

### GAP-SE-05: Sensitive Data Logging

**Severity: Medium** | **Effort: Small**

Several controllers log request objects using `toString()`:
- `FundTransferController`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` — logs account numbers and amounts
- `UserController` (user-service): `log.info("Creating user with {}", request.toString())` — could log passwords
- `UtilityPaymentService`: `log.info("Utility payment processing {}", paymentRequest.toString())` — logs account numbers

### GAP-SE-06: Keycloak Client Secret Management

**Severity: Medium** | **Effort: Small**

The Keycloak client secret is stored in externalized Spring Cloud Config, but `KeycloakProperties` uses `@Value` annotations to inject it. There's no encryption (e.g., Spring Cloud Config encryption, Vault integration) for sensitive configuration values.

### GAP-SE-07: No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning is configured in any `build.gradle`. There is no CI pipeline to detect known CVEs in dependencies.

---

## 5. API Design

### GAP-AD-01: No OpenAPI/Swagger Configuration Beyond Annotations

**Severity: Medium** | **Effort: Small**

While `springdoc-openapi-starter-webflux-ui` is included as a dependency and basic `@Tag`/`@Operation` annotations are present, there is:
- No global OpenAPI configuration (API title, version, description, contact, license)
- No schema examples or detailed parameter documentation
- No explicit response code documentation (`@ApiResponse`)
- The dependency used (`springdoc-openapi-starter-webflux-ui`) is the WebFlux variant, which is incorrect for WebMVC services (core, user, fund-transfer, utility-payment are all WebMVC)

### GAP-AD-02: No Pagination Metadata in List Responses

**Severity: Medium** | **Effort: Small**

List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, `GET /api/v1/bank-users`, `GET /api/v1/user`) accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. The response lacks:
- Total number of elements
- Total number of pages
- Current page number
- Page size
- Whether there's a next/previous page

### GAP-AD-03: No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

All endpoints use `/api/v1/` prefix, but there's no documented versioning strategy, no mechanism for introducing `v2` endpoints, and no content-type versioning or header-based versioning.

### GAP-AD-04: Inconsistent REST Conventions

**Severity: Low** | **Effort: Small**

- `POST /api/v1/bank-users/register` — should be `POST /api/v1/bank-users` (the HTTP method already implies creation)
- `PATCH /api/v1/bank-users/update/{id}` — should be `PATCH /api/v1/bank-users/{id}` (the HTTP method already implies update)
- No `DELETE` endpoints exist for any resource
- No HATEOAS links in responses

### GAP-AD-05: No Filtering or Sorting Documentation

**Severity: Low** | **Effort: Small**

While `Pageable` is accepted (enabling `sort`, `page`, `size` query params via Spring Data), this is not documented or standardized. No explicit filtering parameters are available (e.g., filter transfers by date, status, or account).

---

## 6. Observability

### GAP-OB-01: No Structured Logging

**Severity: Medium** | **Effort: Medium**

All services use default Spring Boot logging (Logback with plain text format). There is:
- No JSON-formatted log output
- No consistent MDC (Mapped Diagnostic Context) enrichment with correlation IDs, user IDs, or service names
- No log aggregation configuration (e.g., ELK, Loki)

### GAP-OB-02: Health Check Endpoints Not Customized

**Severity: Low** | **Effort: Small**

All services include `spring-boot-starter-actuator`, which provides `/actuator/health`. However:
- No custom health indicators (e.g., for Keycloak connectivity, RabbitMQ, or downstream service availability)
- No readiness/liveness probe separation configured
- Health check details are not exposed (default is to hide them)

### GAP-OB-03: No Metrics Endpoints Beyond Actuator Defaults

**Severity: Medium** | **Effort: Medium**

While Micrometer is included for tracing, there is:
- No Prometheus metrics endpoint configured (`/actuator/prometheus`)
- No custom business metrics (e.g., transfer count, transfer amount, payment failures)
- No Grafana dashboards or alerting configuration

### GAP-OB-04: Distributed Tracing Configuration Externalized

**Severity: Low** | **Effort: Small**

Zipkin dependencies are included in all services, but the actual tracing configuration (sampling rate, endpoint URL) is externalized in the Config Server's Git repo. If the config repo is unavailable, tracing silently fails. There are no local fallback defaults.

### GAP-OB-05: Inconsistent Logging Across Services

**Severity: Low** | **Effort: Small**

- `UtilityPaymentController` lacks `@Slf4j` — no request logging
- `core-banking-service` controllers log incoming requests, but response outcomes are not logged
- No consistent log format for "request received → processing → completed/failed" flow

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

**Severity: High** | **Effort: Medium**

The three application services make synchronous Feign calls to `core-banking-service`. There are no circuit breakers (e.g., Resilience4j, Spring Cloud CircuitBreaker) configured. If the core service is down or slow:
- All fund transfer and utility payment requests will fail with timeouts
- The calling services will hold threads waiting for responses
- Cascading failures can bring down the entire system

### GAP-RE-02: No Retry Policies

**Severity: Medium** | **Effort: Small**

There are no retry policies configured on Feign clients or at the gateway level. Transient network errors or brief core-service unavailability will cause immediate failures instead of retrying.

### GAP-RE-03: No Timeout Configuration

**Severity: High** | **Effort: Small**

Feign client timeouts are not explicitly configured in any service's `build.gradle` or `application.yml`. The defaults are used, which may be too long for a financial application, leading to:
- Thread pool exhaustion under high load
- Poor user experience with long-hanging requests

### GAP-RE-04: No Fallback Behavior

**Severity: Medium** | **Effort: Medium**

There are no Feign fallback implementations (`@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)`). When the core banking service is unavailable, clients receive raw error responses instead of graceful degradation messages.

### GAP-RE-05: No Idempotency Protection

**Severity: High** | **Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency keys or duplicate request detection:
- If a client retries a `POST /api/v1/transfer` due to a timeout, the same transfer could be executed twice.
- There are no unique constraints on `(fromAccount, toAccount, amount, timestamp)` combinations.
- The `transactionReference` is set after the core banking call succeeds, so a failure between the core call and the local DB update could leave orphaned transactions.

### GAP-RE-06: Non-Atomic Distributed Transaction

**Severity: High** | **Effort: Large**

The fund transfer and utility payment flows involve two database writes (local service DB + core banking DB) without a distributed transaction or saga pattern:
1. Local entity saved with status `PENDING`/`PROCESSING`
2. Feign call to core banking (commits in core DB)
3. Local entity updated to `SUCCESS`

If step 3 fails (e.g., network error after core banking commits), the money has been moved in core but the local status remains `PENDING`. There is no compensation logic or scheduled reconciliation.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-CO-01 | Code Organization | No shared library for duplicated code | High | Medium |
| GAP-CO-02 | Code Organization | No root build file or multi-module project | Medium | Medium |
| GAP-CO-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-EH-01 | Error Handling | Generic catch-all returns 400 for all errors | High | Small |
| GAP-EH-02 | Error Handling | Raw `ResponseEntity` without type parameters | Medium | Small |
| GAP-EH-03 | Error Handling | No specific HTTP status codes per error type | Medium | Small |
| GAP-EH-04 | Error Handling | No error handling for Feign client failures | High | Medium |
| GAP-TE-01 | Testing | Test coverage limited to core-banking-service | High | Large |
| GAP-TE-02 | Testing | No integration tests | High | Large |
| GAP-TE-03 | Testing | No contract tests between services | Medium | Large |
| GAP-TE-04 | Testing | ApplicationTests fail without infrastructure | Medium | Small |
| GAP-SE-01 | Security | Database credentials hardcoded in source | Critical | Small |
| GAP-SE-02 | Security | No input validation on API endpoints | High | Medium |
| GAP-SE-03 | Security | CSRF disabled without CORS policy | Medium | Small |
| GAP-SE-04 | Security | No rate limiting | Medium | Medium |
| GAP-SE-05 | Security | Sensitive data in logs | Medium | Small |
| GAP-SE-06 | Security | Keycloak secrets not encrypted | Medium | Small |
| GAP-SE-07 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-AD-01 | API Design | No OpenAPI configuration; wrong Springdoc artifact | Medium | Small |
| GAP-AD-02 | API Design | No pagination metadata in list responses | Medium | Small |
| GAP-AD-03 | API Design | No API versioning strategy documented | Low | Medium |
| GAP-AD-04 | API Design | Inconsistent REST endpoint naming | Low | Small |
| GAP-AD-05 | API Design | No filtering or sorting documentation | Low | Small |
| GAP-OB-01 | Observability | No structured (JSON) logging | Medium | Medium |
| GAP-OB-02 | Observability | Health checks not customized | Low | Small |
| GAP-OB-03 | Observability | No Prometheus metrics or custom business metrics | Medium | Medium |
| GAP-OB-04 | Observability | Tracing config fully externalized, no fallback | Low | Small |
| GAP-OB-05 | Observability | Inconsistent logging across services | Low | Small |
| GAP-RE-01 | Resilience | No circuit breakers on Feign clients | High | Medium |
| GAP-RE-02 | Resilience | No retry policies | Medium | Small |
| GAP-RE-03 | Resilience | No timeout configuration | High | Small |
| GAP-RE-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RE-05 | Resilience | No idempotency protection on transfers/payments | High | Medium |
| GAP-RE-06 | Resilience | Non-atomic distributed transaction (no saga) | High | Large |
