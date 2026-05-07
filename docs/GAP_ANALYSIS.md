# Engineering Standards Gap Analysis

This document compares the internet banking microservices codebase against industry engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Shared Library — Duplicated Code Across Services

**Severity: High | Effort: Medium**

The `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, and `AppAuthUserFilter` classes are **copy-pasted identically** across 3–4 services. There is no shared library or parent module. Any bug fix or improvement must be applied to each service independently.

**Evidence:**
- `BaseMapper.java` — identical in core-banking-service, fund-transfer-service, user-service, utility-payment-service
- `AuditAware.java` — identical in fund-transfer-service, user-service, utility-payment-service
- `ErrorResponse.java` / `SimpleBankingGlobalException.java` / `GlobalExceptionHandler.java` — identical in all 4 business services
- `AppAuthUserFilter.java` / `ApiRequestContext.java` / `ApiRequestContextHolder.java` — identical in fund-transfer, user, and utility-payment services

### 1.2 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each service has its own independent `settings.gradle` and `build.gradle` with duplicated dependency declarations and plugin versions. There is no root Gradle wrapper, no shared version catalog, and no BOM for internal dependencies. Plugin versions (e.g., Spring Boot `3.2.4`, `gorylenko.gradle-git-properties:2.4.2`) are hardcoded in every `build.gradle`.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package naming varies across services:
- Core Banking: `repository/` at top level, DTOs in `model/dto/`
- Fund Transfer: `model/repository/`, DTOs in `model/dto/`
- User Service: `model/repository/`, Feign config in `configuration/feign/`
- Utility Payment: `repository/` at top level, Feign config in `configuration/` (no sub-package)

### 1.4 Mappers Instantiated as Fields Instead of Spring Beans

**Severity: Low | Effort: Small**

All mapper classes (e.g., `FundTransferMapper`, `UserMapper`) are instantiated via `new` in service classes instead of being managed as Spring beans. This prevents dependency injection, makes testing harder, and is inconsistent with the rest of the Spring-managed architecture.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` in every service returns `400 Bad Request` for **all** exceptions — including `EntityNotFoundException` (should be `404`), `InsufficientFundsException` (could be `422`), and generic `Exception` (should be `500`). The catch-all handler also exposes raw exception details to the client.

**Evidence:**
```java
// GlobalExceptionHandler.java (identical in all services)
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);  // Always 400
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);  // Leaks stack info
}
```

### 2.2 Exception Details Leaked to Clients

**Severity: High | Effort: Small**

The generic exception handler concatenates the full exception object into the response body (`"Exception occur inside API " + e`). This can expose internal class names, stack traces, SQL queries, and infrastructure details to API consumers.

### 2.3 Raw ResponseEntity Without Type Parameters

**Severity: Medium | Effort: Small**

Almost all controller methods return `ResponseEntity` without generic type parameters (raw type). This suppresses compile-time type safety and produces less informative OpenAPI documentation.

**Evidence:**
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)    // should be ResponseEntity<FundTransferResponse>
```

### 2.4 No Error Handling for Feign Client Failures

**Severity: High | Effort: Medium**

- The Fund Transfer Service has **no Feign error decoder** — Feign failures propagate as raw `FeignException` and are caught by the generic handler, which returns the exception stacktrace.
- The User Service has a `CustomFeignErrorDecoder` that handles 400/401/404 cases, but the Utility Payment Service's `CustomFeignClientConfiguration` is empty (extends `FeignClientConfiguration` but adds no beans).
- No circuit breaker or fallback is configured on any Feign client.

### 2.5 No Transactional Rollback on Feign Failure in Orchestration Services

**Severity: Critical | Effort: Medium**

In `FundTransferService.fundTransfer()`, the local entity is saved with `PENDING` status, then the Feign call is made. If the Feign call succeeds but the subsequent `save()` fails, or if the Feign call throws an exception, the local entity remains in `PENDING` status forever with no mechanism for retry or cleanup. The same pattern exists in `UtilityPaymentService`.

---

## 3. Testing

### 3.1 Minimal Test Coverage — Only Core Banking Service Has Unit Tests

**Severity: Critical | Effort: Large**

| Service | Test Files | Test Type |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` + context load | Unit tests (Mockito) |
| internet-banking-fund-transfer-service | Context load test only | — |
| internet-banking-user-service | Context load test only | — |
| internet-banking-utility-payment-service | Context load test only | — |
| internet-banking-api-gateway | Context load test only | — |
| internet-banking-config-server | Context load test only | — |
| internet-banking-service-registry | Context load test only | — |

Three out of four business services have **zero** unit tests for their service or controller layers.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify the full request-response cycle (e.g., using `@SpringBootTest` with `MockMvc` or `WebTestClient`). No Testcontainers usage for MySQL or Keycloak integration testing.

### 3.3 No Contract Tests Between Services

**Severity: Medium | Effort: Large**

Services communicate via OpenFeign, but there are no consumer-driven contract tests (e.g., Spring Cloud Contract or Pact). Changes to Core Banking Service's API could break Fund Transfer, User, or Utility Payment services without detection until runtime.

### 3.4 Context Load Tests Will Fail Without Infrastructure

**Severity: Medium | Effort: Small**

The context load tests (`*ApplicationTests.java`) for Fund Transfer, User, and Utility Payment services will fail because they require Eureka, Config Server, and MySQL to be running. The test `application.yml` files disable Eureka client but do not mock Feign clients or Keycloak.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are hardcoded in source-controlled files:

| File | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin` / `password` |
| `docker-compose.yml` | Keycloak DB: `keycloak` / `password` |
| `docker-compose/mysql/privileges.sql` | App DB user: `javatodev_development` / `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |
| `internet-banking-user-service/src/test/resources/application.yml` | Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` |

### 4.2 No Input Validation on Request Bodies

**Severity: High | Effort: Small**

No `@Valid` / `@NotNull` / `@NotBlank` / `@Positive` annotations are used on any request DTO. Controllers accept arbitrary payloads without validation. For example, a fund transfer with a **negative amount** or a **null account number** would proceed to the service layer.

**Evidence:**
```java
@PostMapping
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest fundTransferRequest) {
    // No @Valid annotation — no validation whatsoever
}
```

### 4.3 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the API Gateway's `SecurityConfiguration` (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is standard for stateless REST APIs with JWT, it should be documented as a deliberate decision.

### 4.4 Downstream Services Have No Authentication

**Severity: High | Effort: Medium**

Only the API Gateway enforces OAuth2/JWT authentication. The downstream services (User, Fund Transfer, Utility Payment, Core Banking) have **no security configuration** — they are accessible without any authentication if reached directly (bypassing the gateway). In a Docker Compose setup with a private network this is partially mitigated, but in Kubernetes or any network where services are reachable, this is a significant risk.

### 4.5 Password Logged in User Registration

**Severity: High | Effort: Small**

The User Controller logs the full `User` request object during registration: `log.info("Creating user with {}", request.toString())`. Since the `User` DTO includes a `password` field and uses Lombok's `@Data` (which generates `toString()` including all fields), the plaintext password is written to logs.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanner is configured in the build. The `springdoc-openapi-starter-webflux-ui:2.1.0` dependency is outdated (latest is 2.5.x+), and `commons-lang:commons-lang` (used via `StringUtils`) is the legacy version, not `commons-lang3`.

---

## 5. API Design

### 5.1 Inconsistent URL Naming Conventions

**Severity: Medium | Effort: Small**

| Service | Base Path | Style |
|---|---|---|
| Core Banking - Accounts | `/api/v1/account/bank-account/` | Singular with sub-resource |
| Core Banking - Transactions | `/api/v1/transaction/fund-transfer` | Singular |
| Core Banking - Users | `/api/v1/user/` | Singular |
| User Service | `/api/v1/bank-users/` | Plural with prefix |
| Fund Transfer | `/api/v1/transfer` | Singular, no noun |
| Utility Payment | `/api/v1/utility-payment` | Singular with hyphen |

REST convention recommends **plural nouns** consistently (e.g., `/api/v1/accounts`, `/api/v1/transfers`, `/api/v1/users`).

### 5.2 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept Spring's `Pageable` parameters but return raw `List<T>` instead of a paginated wrapper (e.g., Spring's `Page<T>` or a custom wrapper with `totalElements`, `totalPages`, `currentPage`). Clients have no way to know if there are more pages.

### 5.3 No API Versioning Strategy Beyond `/v1`

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/`, but there is no versioning strategy documented. No content negotiation versioning or header-based versioning is configured.

### 5.4 OpenAPI Annotations Are Minimal

**Severity: Low | Effort: Small**

While `@Tag` and `@Operation` annotations are present on controllers, there are no `@ApiResponse`, `@Schema`, or `@Parameter` annotations. Response shapes, error responses, and parameter descriptions are not documented in the OpenAPI spec. The `springdoc-openapi-starter-webflux-ui` dependency is also the wrong variant for servlet-based services (should be `springdoc-openapi-starter-webmvc-ui`).

### 5.5 No Filtering or Sorting Support on List Endpoints

**Severity: Low | Effort: Medium**

List endpoints (fund transfers, payments, users) support only basic pagination (`page`, `size`, `sort`) but offer no domain-specific filtering (e.g., by status, date range, account number).

---

## 6. Observability

### 6.1 Logging Is Inconsistent and Incomplete

**Severity: Medium | Effort: Small**

- Some controllers log request entry; others do not.
- No structured logging format (JSON). Default Spring Boot log format is used.
- No correlation ID / trace ID in log messages (though Zipkin tracing is configured, it's not surfaced in logs).
- Several log messages contain typos (e.g., "Reading utitlity account").

### 6.2 No Health Check Customization

**Severity: Low | Effort: Small**

Spring Boot Actuator is included in all services, providing default `/actuator/health` endpoints. However, no custom health indicators are defined for critical dependencies (e.g., MySQL connectivity, Keycloak reachability, Feign client health).

### 6.3 No Metrics Beyond Defaults

**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are present, no custom business metrics are defined (e.g., fund transfer count/amount, payment success/failure rate, user registration rate). No Prometheus scrape configuration is provided despite Prometheus being listed in the technology stack.

### 6.4 Distributed Tracing Configuration Is Implicit

**Severity: Low | Effort: Small**

Tracing dependencies are included but there is no explicit configuration for sampling rate, service name propagation, or trace ID logging. The default sampling rate (10%) may miss important traces in production.

---

## 7. Resilience

### 7.1 No Circuit Breakers on Feign Clients

**Severity: Critical | Effort: Medium**

All inter-service calls use OpenFeign without any circuit breaker (e.g., Resilience4j, Spring Cloud Circuit Breaker). If Core Banking Service becomes slow or unavailable, all upstream services will block and eventually exhaust their thread pools, causing a cascade failure.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for Feign clients or any inter-service communication. Transient failures (network blips, brief service restarts) immediately result in errors returned to clients.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit connection or read timeouts are configured on Feign clients. The default timeouts are very long (10s connect, 60s read), which means a slow downstream service can tie up resources for extended periods.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback responses are defined for any inter-service call. If the Core Banking Service is down, fund transfers and payments fail with unhelpful error messages instead of graceful degradation.

### 7.5 No Idempotency Protection on Write Operations

**Severity: High | Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a failed request (e.g., due to network timeout), a duplicate transfer/payment could be processed. There is also no duplicate detection mechanism.

### 7.6 Fund Transfer Is Not Atomic Across Services

**Severity: Critical | Effort: Large**

The fund transfer flow involves two services (Fund Transfer Service saves locally, then calls Core Banking Service). There is no saga pattern, no compensating transaction, and no outbox pattern. If the Feign call to Core Banking succeeds but the local status update fails, or vice versa, the system ends up in an inconsistent state.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No shared library — duplicated code | Code Organization | High | Medium |
| 1.2 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers not Spring-managed | Code Organization | Low | Small |
| 2.1 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.2 | Exception details leaked to clients | Error Handling | High | Small |
| 2.3 | Raw `ResponseEntity` without generics | Error Handling | Medium | Small |
| 2.4 | No Feign error decoder in most services | Error Handling | High | Medium |
| 2.5 | No rollback on Feign failure | Error Handling | Critical | Medium |
| 3.1 | Only core-banking has unit tests | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context load tests fail without infra | Testing | Medium | Small |
| 4.1 | Hardcoded credentials in source | Security | Critical | Small |
| 4.2 | No input validation | Security | High | Small |
| 4.3 | CSRF disabled without documentation | Security | Low | Small |
| 4.4 | Downstream services unauthenticated | Security | High | Medium |
| 4.5 | Password logged in registration | Security | High | Small |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Inconsistent URL naming | API Design | Medium | Small |
| 5.2 | No pagination metadata | API Design | Medium | Small |
| 5.3 | No versioning strategy documented | API Design | Low | Small |
| 5.4 | Minimal OpenAPI annotations | API Design | Low | Small |
| 5.5 | No filtering/sorting on lists | API Design | Low | Medium |
| 6.1 | Inconsistent/unstructured logging | Observability | Medium | Small |
| 6.2 | No custom health indicators | Observability | Low | Small |
| 6.3 | No custom business metrics | Observability | Medium | Medium |
| 6.4 | Tracing config is implicit | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency protection | Resilience | High | Medium |
| 7.6 | Fund transfer not atomic | Resilience | Critical | Large |
