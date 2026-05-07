# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry-standard engineering best practices. Each gap is rated by **severity** and estimated **remediation effort**.

**Severity Scale:**
- **Critical** - Security vulnerability, data-loss risk, or production-blocking deficiency
- **High** - Significant quality or reliability concern; should be addressed before production use
- **Medium** - Deviation from best practice that increases maintenance burden
- **Low** - Minor improvement opportunity; nice-to-have

**Effort Scale:**
- **Small** - < 1 day per service; configuration change or straightforward addition
- **Medium** - 1-3 days per service; moderate code changes, new classes, or library integration
- **Large** - > 3 days; architectural changes, significant refactoring, or cross-cutting concerns

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### 1.1 Inconsistent Project Structure Across Services

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** All microservices should follow a uniform package layout so developers can navigate any service predictably.

**Current State:** Package structures diverge across services:

| Service | Package Structure |
|---|---|
| core-banking-service | `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.dto.request`, `model.dto.response`, `exception` |
| internet-banking-user-service | `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.rest`, `configuration.feign`, `configuration.keycloak`, `exception` |
| internet-banking-fund-transfer-service | `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.rest`, `configuration`, `exception` |
| internet-banking-utility-payment-service | `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.rest`, `configuration`, `exception` |

**Gaps:**
- The `configuration` package is named `configuration.feign` + `configuration.keycloak` in user-service but just `configuration` in other services.
- REST client interfaces are in `model.rest` in internet-banking services but absent in core-banking-service (which has no Feign clients).
- DTO sub-packaging (`model.dto.request`, `model.dto.response`) exists only in core-banking-service.

### 1.2 No Shared Library for Common Code

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |

**Best Practice:** Cross-cutting code (exception classes, DTOs, error codes) should live in a shared library to avoid duplication.

**Current State:** The following classes are **duplicated identically or near-identically** across 3-4 services:
- `SimpleBankingGlobalException` (4 copies)
- `GlobalExceptionHandler` (4 copies with minor style differences)
- `ErrorResponse` (4 copies - some use `@Builder`, one uses `@AllArgsConstructor`)
- `EntityNotFoundException` (4 copies)
- `GlobalErrorCode` (4 copies)

There is no Gradle root project or shared module. Each service is an independent Gradle project with its own `settings.gradle` and `build.gradle`.

### 1.3 No Multi-Project Gradle Build

| Aspect | Rating |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Best Practice:** A root `build.gradle` / `settings.gradle` with subprojects enables consistent dependency versions, shared plugins, and single-command builds.

**Current State:** Each service has an independent `build.gradle` with hardcoded versions:
- Spring Boot `3.2.4` repeated in 7 `build.gradle` files
- Spring Cloud `2023.0.0` repeated in 7 files
- `io.spring.dependency-management` `1.1.4` repeated in 7 files
- `com.gorylenko.gradle-git-properties` `2.4.2` repeated in 6 files

Version drift risk is real: nothing enforces that all services use the same Spring Boot version.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Best Practice:** All API errors should return a consistent JSON envelope (e.g., RFC 7807 Problem Details) with fields like `type`, `title`, `status`, `detail`, `instance`.

**Current State:**
- **Business exceptions** return `{ "code": "...", "message": "..." }` via `ErrorResponse`.
- **Generic exceptions** return a raw string: `"Exception occur inside API " + e` — this leaks stack trace details to the client.
- The `ErrorResponse` class does not include an HTTP status code field.
- There is no timestamp, request path, or correlation ID in error responses.

**Evidence:**
```java
// GlobalExceptionHandler.java (all 4 services)
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);  // Leaks internals
}
```

### 2.2 All Errors Return HTTP 400

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Best Practice:** HTTP status codes should semantically match the error (404 for not found, 409 for conflicts, 422 for validation, 500 for server errors).

**Current State:** Every exception—including `EntityNotFoundException`, `InsufficientFundsException`, and unexpected server errors—returns **400 Bad Request**:

```java
// EntityNotFoundException → 400 (should be 404)
// InsufficientFundsException → 400 (could argue 409 or 422)
// NullPointerException → 400 (should be 500)
```

The generic `Exception.class` handler also returns 400, meaning an unhandled `NullPointerException` or database connection error is reported to the client as a "bad request."

### 2.3 No Feign Error Decoder in All Services

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** Each Feign client should have an error decoder that translates downstream HTTP errors into appropriate upstream exceptions.

**Current State:**
- `internet-banking-user-service` has a `CustomFeignErrorDecoder` that parses downstream error responses and re-throws typed exceptions.
- `internet-banking-fund-transfer-service` has a `CustomFeignClientConfiguration` that sets Feign logging to FULL but **no error decoder**.
- `internet-banking-utility-payment-service` has an empty `CustomFeignClientConfiguration` with **no error decoder**.

---

## 3. Testing

### 3.1 Near-Zero Test Coverage on Internet-Banking Services

| Aspect | Rating |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |

**Best Practice:** Each service should have unit tests for business logic, integration tests for repository/database interactions, and API-level tests for controllers.

**Current State:**

| Service | Meaningful Tests | Coverage |
|---|---|---|
| core-banking-service | 16 unit tests (3 test classes) | Service layer only; no controller or repository tests |
| internet-banking-user-service | 0 | Context load test only |
| internet-banking-fund-transfer-service | 0 | Context load test only |
| internet-banking-utility-payment-service | 0 | Context load test only |
| internet-banking-api-gateway | 0 | Context load test only |

The three internet-banking services contain critical business logic (user registration via Keycloak, fund transfer orchestration, utility payment orchestration) with **zero** unit tests.

### 3.2 No Integration Tests

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Best Practice:** Integration tests verify that the service correctly interacts with its database, message broker, and external APIs using tools like Testcontainers or embedded databases.

**Current State:**
- The core-banking-service test profile uses H2 in-memory with Flyway disabled, but no tests actually exercise repository methods against the database.
- No Testcontainers usage anywhere.
- No Spring Boot `@SpringBootTest` integration tests with `WebEnvironment.RANDOM_PORT`.

### 3.3 No Contract Tests Between Services

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |

**Best Practice:** Consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) verify that API contracts between services remain compatible.

**Current State:** No contract testing framework is present. Feign client interfaces (`BankingCoreFeignClient`) are defined in consumer services but there is no mechanism to verify they match the producer's actual API.

### 3.4 No CI Pipeline to Enforce Testing

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Best Practice:** A CI pipeline should run all tests on every push/PR and block merges if tests fail.

**Current State:** No GitHub Actions, Jenkins, or any CI configuration exists. Tests can be skipped without consequence.

---

## 4. Security

### 4.1 Hardcoded Secrets in Source Control

| Aspect | Rating |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Best Practice:** Secrets should never be committed to source control. Use environment variables, vault solutions, or sealed secrets.

**Current State:** The following secrets are committed in plain text:

| Location | Secrets |
|---|---|
| `docker-compose/docker-compose.yml` | MySQL root password (`woVERANKliGharym`), app DB password (`oPItyPticIAt`), Keycloak admin password (`password`), Keycloak DB password (`password`) |
| `docker-compose/mysql/privileges.sql` | App DB user password |
| Spring Cloud Config (external Git repo) | Database credentials, Keycloak client secrets (externalized but in a public Git repo) |

### 4.2 No Input Validation

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Best Practice:** All API input should be validated using Bean Validation (`@Valid`, `@NotNull`, `@Size`, `@Pattern`, etc.) with clear error messages.

**Current State:**
- No `@Valid` annotation on any `@RequestBody` parameter across all controllers.
- No Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, etc.) on any DTO or request object.
- `spring-boot-starter-validation` dependency is **not included** in any `build.gradle`.

**Example:**
```java
// FundTransferController.java - no validation
@PostMapping
public ResponseEntity<FundTransferResponse> fundTransfer(
    @RequestBody FundTransferRequest fundTransferRequest) { ... }

// FundTransferRequest.java - no constraints
public class FundTransferRequest {
    private String fromAccount;   // Could be null or empty
    private String toAccount;     // Could be null or empty
    private BigDecimal amount;    // Could be null, zero, or negative
}
```

### 4.3 JWT Validation Only at Gateway

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** In a zero-trust architecture, each service should validate tokens independently, not rely solely on the gateway.

**Current State:**
- Only the API gateway has `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security`.
- Business services (core-banking, fund-transfer, utility-payment) have **no security dependencies** and accept any request on their ports.
- If a service is accessed directly (bypassing the gateway), there is no authentication or authorization.

### 4.4 Keycloak Admin Client Thread Safety

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** Keycloak admin client instances should be properly managed with token refresh and thread safety.

**Current State:** The `KeycloakUserService` creates a singleton `Keycloak` instance using lazy initialization that is not thread-safe:

```java
private Keycloak keycloak;

private Keycloak getKeycloak() {
    if (keycloak == null) {
        keycloak = KeycloakBuilder.builder()
            // ...
            .build();
    }
    return keycloak;
}
```

Under concurrent requests, multiple instances could be created (double-checked locking issue) and the token lifecycle is not explicitly managed.

### 4.5 No Dependency Vulnerability Scanning

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** Use tools like OWASP Dependency-Check, Snyk, or GitHub Dependabot to monitor for known vulnerabilities in dependencies.

**Current State:** No vulnerability scanning is configured. No Dependabot configuration (`.github/dependabot.yml`), no OWASP plugin in Gradle, no Snyk integration.

---

## 5. API Design

### 5.1 No API Versioning

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** APIs should be versioned (URL path, header, or content-type negotiation) to allow backward-compatible evolution.

**Current State:** All endpoints use unversioned paths:
- `/api/v1/user` (user-service has `v1` but only superficially — there is no `v2` or version negotiation)
- `/api/v1/fund-transfer` (similarly superficial)
- `/api/v1/bank-account` (core-banking-service uses versioned path)

While the URL paths contain `/v1/`, there is no infrastructure for routing to different versions or maintaining backward compatibility.

### 5.2 No Pagination on List Endpoints

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** List endpoints should support pagination, sorting, and filtering to prevent unbounded result sets.

**Current State:**
- `GET /api/v1/bank-account` returns all accounts without pagination.
- `GET /api/v1/utility-account` returns all utility accounts without pagination.
- `GET /api/v1/user` in core-banking-service accepts `Pageable` (Spring Data pagination) — this is the only endpoint with pagination support.
- Internet-banking service endpoints do not expose pagination parameters to the client.

### 5.3 No OpenAPI / Swagger Documentation

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Best Practice:** APIs should be documented with OpenAPI 3.x specification, auto-generated from code annotations, and exposed via Swagger UI.

**Current State:** 
- No `springdoc-openapi` or `springfox` dependency in any service.
- No `@Operation`, `@ApiResponse`, `@Schema` annotations on controllers or DTOs.
- No `/swagger-ui.html` or `/v3/api-docs` endpoint available.
- The only API documentation is the Postman collection (`postman_collection/`).

### 5.4 Inconsistent Response Envelopes

| Aspect | Rating |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Best Practice:** All API responses should follow a consistent envelope structure (e.g., `{ "data": ..., "meta": ... }`).

**Current State:**
- Success responses return entity DTOs directly (no wrapper).
- Some responses return `String` (e.g., `"User with identification number ... created"`).
- Error responses use `ErrorResponse { code, message }` or raw strings.
- No consistent pagination metadata in list responses.

### 5.5 Non-RESTful Response Patterns

| Aspect | Rating |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Best Practice:** POST endpoints that create resources should return 201 Created with a `Location` header.

**Current State:**
- User creation returns 200 OK with a string message.
- Fund transfer creation returns 200 OK with a response body.
- No `Location` headers on any creation endpoint.

---

## 6. Observability

### 6.1 No Structured Logging

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** Logs should be structured (JSON format) with consistent fields (timestamp, level, service, traceId, spanId, message) for aggregation and search.

**Current State:**
- Default Spring Boot log format (plain text, pattern-based).
- No JSON log encoder configured (no Logback `logstash-logback-encoder` or similar).
- Trace/span IDs are injected into MDC by Micrometer Tracing but the log pattern may not include them by default.
- Log levels are configured externally via Spring Cloud Config but the format is not standardized.

### 6.2 Incomplete Health Check Configuration

| Aspect | Rating |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Best Practice:** All services should expose detailed health checks (`/actuator/health`) including database, disk space, and downstream service health indicators.

**Current State:**
- All services include `spring-boot-starter-actuator`.
- Default Actuator health endpoint is available.
- No custom health indicators for downstream dependencies (Keycloak availability, Eureka connectivity, core-banking-service reachability from internet-banking services).
- No liveness/readiness probe differentiation for Kubernetes readiness.

### 6.3 No Centralized Log Aggregation

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** Logs from all services should be aggregated in a central system (ELK, Loki, CloudWatch) for correlation and alerting.

**Current State:** No log aggregation infrastructure. Each container logs to stdout, viewable only via `docker-compose logs`. No Fluentd, Logstash, or similar sidecar is configured.

### 6.4 No Metrics Collection

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** Services should export metrics (request rates, latencies, error rates, JVM stats) to a monitoring system like Prometheus/Grafana.

**Current State:**
- `micrometer-tracing-bridge-brave` is present (for tracing) but no `micrometer-registry-prometheus` or similar metrics registry.
- Actuator `/actuator/metrics` is available by default but not scraped by any monitoring system.
- No Prometheus endpoint (`/actuator/prometheus`) configured.
- No Grafana dashboards or alerting rules.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Best Practice:** Calls to downstream services should be wrapped in circuit breakers (e.g., Resilience4j) to prevent cascade failures.

**Current State:**
- No `resilience4j-spring-boot3` or `spring-cloud-starter-circuitbreaker-resilience4j` dependency.
- Feign clients call core-banking-service synchronously with no fallback mechanism.
- If core-banking-service becomes unavailable, all internet-banking services will hang on Feign calls until TCP timeout.

### 7.2 No Retry Policies

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Best Practice:** Transient failures (network blips, temporary unavailability) should be retried with exponential backoff and jitter.

**Current State:**
- No `@Retryable` annotations or Resilience4j retry configuration.
- No Spring Retry dependency in any service.
- Feign calls fail immediately on any error (no retry).

### 7.3 No Timeout Configuration

| Aspect | Rating |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Best Practice:** All network calls should have explicit connect and read timeouts to prevent thread starvation.

**Current State:**
- No Feign timeout configuration (`connectTimeout`, `readTimeout`) in any `application.yml` or Feign configuration class.
- No `RestTemplate` or `WebClient` timeout configuration.
- Default Feign timeouts are used (10 seconds connect, 60 seconds read), which are excessively long for an internal microservice call.
- Database connection pool timeouts are not explicitly configured (relying on HikariCP defaults).

### 7.4 No Fallback Behavior

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** When a downstream service is unavailable, the calling service should degrade gracefully (cached response, default value, partial response).

**Current State:**
- No `@FeignClient(fallback = ...)` or `@FeignClient(fallbackFactory = ...)` defined on any Feign client.
- No cache layer for read operations that could serve stale data during outages.
- A failure in core-banking-service causes a raw exception to propagate to the end user.

### 7.5 No Rate Limiting

| Aspect | Rating |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Best Practice:** APIs should implement rate limiting to prevent abuse and ensure fair resource allocation.

**Current State:**
- No rate limiting on the API gateway or any individual service.
- Spring Cloud Gateway supports `RequestRateLimiter` filter but it is not configured.
- No Redis or in-memory rate limiter implementation.

---

## Summary Matrix

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 4.1 | Hardcoded secrets in source control | Security | **Critical** | Small |
| 3.1 | Near-zero test coverage on internet-banking services | Testing | **Critical** | Large |
| 2.1 | Inconsistent error response format (leaks internals) | Error Handling | **High** | Small |
| 2.2 | All errors return HTTP 400 | Error Handling | **High** | Small |
| 3.2 | No integration tests | Testing | **High** | Large |
| 3.4 | No CI pipeline | Testing | **High** | Small |
| 4.2 | No input validation | Security | **High** | Medium |
| 5.3 | No OpenAPI / Swagger documentation | API Design | **High** | Small |
| 7.1 | No circuit breakers | Resilience | **High** | Medium |
| 7.3 | No timeout configuration | Resilience | **High** | Small |
| 1.1 | Inconsistent project structure | Code Organization | **Medium** | Medium |
| 1.2 | No shared library for common code | Code Organization | **Medium** | Large |
| 2.3 | No Feign error decoder in all services | Error Handling | **Medium** | Small |
| 3.3 | No contract tests | Testing | **Medium** | Large |
| 4.3 | JWT validation only at gateway | Security | **Medium** | Medium |
| 4.4 | Keycloak admin client thread safety | Security | **Medium** | Small |
| 4.5 | No dependency vulnerability scanning | Security | **Medium** | Small |
| 5.1 | No API versioning | API Design | **Medium** | Medium |
| 5.2 | No pagination on list endpoints | API Design | **Medium** | Small |
| 6.1 | No structured logging | Observability | **Medium** | Medium |
| 6.3 | No centralized log aggregation | Observability | **Medium** | Medium |
| 6.4 | No metrics collection | Observability | **Medium** | Small |
| 7.2 | No retry policies | Resilience | **Medium** | Small |
| 7.4 | No fallback behavior | Resilience | **Medium** | Medium |
| 7.5 | No rate limiting | Resilience | **Medium** | Medium |
| 1.3 | No multi-project Gradle build | Code Organization | **Low** | Medium |
| 5.4 | Inconsistent response envelopes | API Design | **Low** | Small |
| 5.5 | Non-RESTful response patterns | API Design | **Low** | Small |
| 6.2 | Incomplete health check configuration | Observability | **Low** | Small |

**Totals:** 29 gaps identified
- Critical: 2
- High: 8
- Medium: 16
- Low: 3
