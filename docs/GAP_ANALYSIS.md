# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps found across all 6 microservices. Each gap is rated by severity and estimated remediation effort.

**Severity Scale:**
- **Critical** — Security vulnerability, data corruption risk, or production outage potential
- **High** — Significant quality or reliability issue that should be addressed before production
- **Medium** — Best practice violation that impacts maintainability or developer experience
- **Low** — Minor improvement that would polish the codebase

**Effort Scale:**
- **Small** — < 1 day of work
- **Medium** — 1-3 days of work
- **Large** — 3+ days of work

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Gap:** Each microservice has its own independent Gradle project with duplicated `gradlew`, `gradle-wrapper.jar`, `build.gradle`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to unify the build.

**Impact:** Dependency versions are duplicated across 6 `build.gradle` files. Upgrading Spring Boot or Spring Cloud requires editing every file individually, increasing risk of version drift.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 1.2 Duplicated Exception/Error Handling Classes

**Gap:** `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, and `GlobalErrorCode` are copy-pasted across `core-banking-service`, `internet-banking-fund-transfer-service`, `internet-banking-user-service`, and `internet-banking-utility-payment-service` with minor variations.

**Impact:** Bug fixes or format changes to error handling must be applied in 4 places. The `GlobalErrorCode` constants differ between services without a shared contract.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 1.3 Duplicated AuditAware / Filter / Configuration Classes

**Gap:** `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, and `CustomFeignClientConfiguration` are duplicated across fund-transfer and utility-payment services. The `AuditAware` class also serves double duty as both a JPA `@MappedSuperclass` and a DTO base class.

**Impact:** Audit and filter logic changes require synchronized updates. Using the same class for both entity and DTO layers violates separation of concerns.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 1.4 Inconsistent Package Structure

**Gap:** Services use slightly different package layouts:
- Core Banking: `model/dto`, `model/entity`, `model/mapper`, `repository`, `service`, `controller`, `exception`
- Fund Transfer: `model/dto`, `model/entity`, `model/mapper`, `model/repository`, `service/rest/client`, `configuration/audit`, `configuration/filter`
- User Service: `model/dto`, `model/entity`, `model/mapper`, `model/repository`, `model/rest/response`, `service/rest`, `configuration/keycloak`, `configuration/feign`, `configuration/filter`, `configuration/audit`
- Utility Payment: `model/dto`, `model/entity`, `model/mapper`, `model/rest`, `repository`, `service/rest`

**Impact:** No consistent convention for where repositories, REST clients, or configuration classes live. Makes onboarding developers harder.

| Severity | Effort |
|---|---|
| Low | Medium |

### 1.5 No Shared Library / Common Module

**Gap:** There is no shared library for common DTOs, exceptions, utilities, or Feign configurations. Each service independently defines its own versions of common types like `FundTransferRequest`, `FundTransferResponse`, `ErrorResponse`, etc.

**Impact:** Changes to shared contracts require coordinated updates across multiple services. No compile-time enforcement of API compatibility between producer and consumer.

| Severity | Effort |
|---|---|
| High | Large |

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400 (Bad Request)

**Gap:** Every `GlobalExceptionHandler` maps all exceptions — including `EntityNotFoundException` and generic `Exception` — to `400 Bad Request`. There is no use of `404 Not Found`, `500 Internal Server Error`, `409 Conflict`, `422 Unprocessable Entity`, or other appropriate status codes.

**Impact:** Clients cannot programmatically distinguish between "entity not found", "insufficient funds", "validation error", and "server error". Breaks REST conventions and makes debugging harder.

| Severity | Effort |
|---|---|
| High | Small |

### 2.2 Generic Exception Handler Leaks Internal Details

**Gap:** The catch-all `@ExceptionHandler({Exception.class})` returns the full exception object as a string:
```java
.body("Exception occur inside API " + e);
```
This exposes stack traces, class names, SQL errors, and other internal details to clients.

**Impact:** Information disclosure vulnerability. Attackers can learn about internal architecture, database structure, and technology stack.

| Severity | Effort |
|---|---|
| Critical | Small |

### 2.3 Inconsistent Error Response Format

**Gap:** Business exceptions return `ErrorResponse { code, message }` but the generic handler returns a plain string. Clients receive two completely different response shapes depending on the error type.

**Impact:** Client-side error handling becomes unreliable. Cannot parse error responses consistently.

| Severity | Effort |
|---|---|
| High | Small |

### 2.4 No Error Handling for Feign Client Failures

**Gap:** None of the Feign clients have error decoders, fallbacks, or try-catch blocks around inter-service calls. If core-banking-service is down or returns an error, the raw Feign exception propagates to the caller.

**Impact:** Cascading failures between services. Poor error messages when downstream services fail. No graceful degradation.

| Severity | Effort |
|---|---|
| High | Medium |

---

## 3. Testing

### 3.1 Only Core Banking Service Has Unit Tests

**Gap:** Only `core-banking-service` has meaningful unit tests (`UserServiceTest`, `AccountServiceTest`, `TransactionServiceTest` — 20 test methods total). The other 5 services only have empty `*ApplicationTests.java` context load tests.

**Impact:** No automated verification of business logic in user registration, fund transfer orchestration, or utility payment orchestration services. Regressions in these services go undetected.

| Severity | Effort |
|---|---|
| High | Large |

### 3.2 No Integration Tests

**Gap:** No integration tests exist that test the actual HTTP layer (controllers), database operations with a real/embedded database, or multi-component interactions.

**Impact:** Cannot verify that controllers correctly map requests/responses, that JPA repositories generate correct queries, or that Spring wiring is correct.

| Severity | Effort |
|---|---|
| High | Large |

### 3.3 No Contract Tests Between Services

**Gap:** No contract tests (e.g., Spring Cloud Contract, Pact) verify that Feign client interfaces match the actual API contracts of the services they call. DTOs like `FundTransferRequest` are independently defined in both the producer and consumer services.

**Impact:** Breaking changes to a service's API can go undetected until runtime. Deployment order matters with no safety net.

| Severity | Effort |
|---|---|
| Medium | Large |

### 3.4 No Test Coverage Reporting

**Gap:** No JaCoCo or other coverage plugins configured. No coverage thresholds enforced.

**Impact:** Cannot measure or enforce minimum test coverage. No visibility into which code paths are untested.

| Severity | Effort |
|---|---|
| Low | Small |

### 3.5 Application Context Tests Will Fail Without Infrastructure

**Gap:** The `*ApplicationTests.java` files in non-core services use `@SpringBootTest` which attempts to load the full application context, requiring Config Server, Eureka, MySQL, and Keycloak to be available. These tests cannot run in a standard CI environment.

**Impact:** Tests are effectively useless — they will always fail without the full infrastructure stack.

| Severity | Effort |
|---|---|
| Medium | Small |

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Gap:** Multiple credentials are hardcoded in `docker-compose.yml` and `privileges.sql`:
- MySQL root password: `woVERANKliGharym`
- MySQL app user password: `oPItyPticIAt`
- Keycloak admin password: `password`
- Keycloak DB password: `password`
- Test user credentials in `README.md`: `ib_admin@javatodev.com / 5V7huE3G86uB`

**Impact:** Credentials are committed to version control. Anyone with repo access has database and admin access.

| Severity | Effort |
|---|---|
| Critical | Small |

### 4.2 No Input Validation on Request Bodies

**Gap:** No `@Valid` / `@NotNull` / `@NotBlank` / `@Positive` / `@Size` annotations on any request DTOs. No Bean Validation dependency included. Controllers accept arbitrary payloads without validation:
```java
public ResponseEntity fundTransfer(@RequestBody FundTransferRequest fundTransferRequest)
```

**Impact:** Null amounts, negative transfers, empty account numbers, and other invalid data reach business logic. Can cause `NullPointerException`, data corruption, or negative balance exploits.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 4.3 CSRF Disabled Without Justification

**Gap:** `SecurityConfiguration` disables CSRF protection:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

**Impact:** While acceptable for pure REST APIs using Bearer tokens, this should be explicitly documented. If any session-based authentication is added later, this becomes a vulnerability.

| Severity | Effort |
|---|---|
| Low | Small |

### 4.4 Internal Services Have No Authentication

**Gap:** Only the API Gateway enforces OAuth2/JWT authentication. The core-banking-service, fund-transfer-service, user-service, and utility-payment-service have no security configuration. If any service port is exposed directly (bypassing the gateway), all APIs are accessible without authentication.

**Impact:** In Docker Compose, all service ports are exposed. Any service can be called directly, bypassing all security.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 4.5 Keycloak Singleton Is Not Thread-Safe

**Gap:** `KeycloakProperties.getInstance()` uses a classic double-check locking antipattern without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Race condition during concurrent requests can create multiple Keycloak client instances, potentially causing resource leaks or inconsistent state.

| Severity | Effort |
|---|---|
| Medium | Small |

### 4.6 Password Included in User DTO Response

**Gap:** The `User` DTO in user-service includes a `password` field. The same DTO is used for both input (registration) and output (read user). The password field is not annotated with `@JsonIgnore` for serialization.

**Impact:** User passwords may be returned in API responses, violating basic security principles.

| Severity | Effort |
|---|---|
| Critical | Small |

### 4.7 No Dependency Vulnerability Scanning

**Gap:** No OWASP dependency-check plugin, Snyk, or Dependabot configuration. No automated scanning for known CVEs in dependencies.

**Impact:** Vulnerable dependencies (e.g., in transitive dependencies of Spring Boot, Keycloak admin client, MySQL connector) may go undetected.

| Severity | Effort |
|---|---|
| High | Small |

---

## 5. API Design

### 5.1 Raw ResponseEntity Without Type Parameters

**Gap:** Most controller methods return `ResponseEntity` without type parameters:
```java
public ResponseEntity readUser(@PathVariable("identification") String identification)
```

**Impact:** No compile-time type safety. OpenAPI/Swagger documentation cannot infer response types, producing incomplete API docs.

| Severity | Effort |
|---|---|
| Medium | Small |

### 5.2 No API Versioning Strategy

**Gap:** All endpoints use `/api/v1/` but there is no documented versioning strategy, no mechanism for running multiple versions simultaneously, and no plan for deprecation.

**Impact:** Breaking changes will require either coordinated big-bang deployments or ad-hoc workarounds.

| Severity | Effort |
|---|---|
| Low | Small |

### 5.3 No Pagination Metadata in Responses

**Gap:** List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (totalElements, totalPages, pageNumber, pageSize).

**Impact:** Clients cannot determine total result count, whether more pages exist, or navigate paginated results reliably.

| Severity | Effort |
|---|---|
| Medium | Small |

### 5.4 No Filtering or Sorting Parameters

**Gap:** No query parameters for filtering results (e.g., by status, date range, amount range) or explicit sorting options. Only basic Pageable is supported.

**Impact:** Clients must fetch all data and filter client-side, which is inefficient for large datasets.

| Severity | Effort |
|---|---|
| Low | Medium |

### 5.5 Inconsistent REST Conventions

**Gap:**
- `POST /api/v1/bank-users/register` — verb in URL (should be `POST /api/v1/bank-users`)
- `PATCH /api/v1/bank-users/update/{id}` — verb in URL (should be `PATCH /api/v1/bank-users/{id}`)
- `POST /api/v1/transfer` — resource name is not a noun
- No use of `201 Created` for POST operations; all return `200 OK`

**Impact:** Non-standard REST conventions make the API harder to learn and use for external consumers.

| Severity | Effort |
|---|---|
| Low | Small |

### 5.6 OpenAPI/Swagger Configuration Uses Wrong Starter

**Gap:** Business services include `springdoc-openapi-starter-webflux-ui` but are Spring MVC (not WebFlux) applications. Only the API Gateway uses WebFlux.

**Impact:** Swagger UI may not work correctly or may conflict with the servlet-based stack. Should use `springdoc-openapi-starter-webmvc-ui` for MVC services.

| Severity | Effort |
|---|---|
| Medium | Small |

---

## 6. Observability

### 6.1 Inconsistent Logging Practices

**Gap:** Logging is ad-hoc. Some methods log inputs (`log.info("Fund transfer initiated...")`), some don't. No structured logging format (JSON). No correlation IDs in log messages. Log levels are not standardized.

**Impact:** Difficult to trace requests across services via log analysis. Log aggregation tools cannot parse unstructured logs effectively.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 6.2 No Custom Health Checks

**Gap:** Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Database connectivity with meaningful details
- Keycloak availability
- Config Server reachability
- Downstream service health

**Impact:** The `/actuator/health` endpoint provides generic status but doesn't validate critical dependencies are actually functional.

| Severity | Effort |
|---|---|
| Medium | Small |

### 6.3 No Custom Metrics

**Gap:** No `@Timed`, `@Counted`, or custom `MeterRegistry` metrics. No business metrics tracked (e.g., transfers per minute, payment success/failure rates, registration counts).

**Impact:** Cannot monitor business KPIs or set alerts on meaningful thresholds. Rely only on JVM/HTTP default metrics.

| Severity | Effort |
|---|---|
| Medium | Medium |

### 6.4 Distributed Tracing Not Fully Configured

**Gap:** Tracing dependencies are included but no explicit sampling configuration. The default sampling rate may not capture all traces. No custom span annotations on critical business operations.

**Impact:** May miss traces for important transactions. No business-context spans in traces.

| Severity | Effort |
|---|---|
| Low | Small |

### 6.5 No Centralized Log Aggregation

**Gap:** No ELK stack, Loki, or CloudWatch integration. Services log to stdout only. No log shipping configured in Docker Compose.

**Impact:** In a multi-container deployment, logs are scattered across containers. Cannot search or correlate logs across services.

| Severity | Effort |
|---|---|
| Medium | Large |

### 6.6 Sensitive Data Logged

**Gap:** Controllers log full request objects including potentially sensitive data:
```java
log.info("Creating user with {}", request.toString());
log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
```
The `User` DTO includes a `password` field, so passwords may be written to logs.

**Impact:** Passwords and financial data in logs violate data protection requirements.

| Severity | Effort |
|---|---|
| Critical | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap:** No Resilience4j or Hystrix circuit breakers on any Feign client calls. If core-banking-service becomes slow or unresponsive, all calling services will block indefinitely on thread pools.

**Impact:** A single slow service can cascade failures across the entire system, exhausting thread pools and causing system-wide outage.

| Severity | Effort |
|---|---|
| High | Medium |

### 7.2 No Retry Policies

**Gap:** No retry configuration on Feign clients or RestTemplate calls. Transient network failures cause immediate failure with no recovery attempt.

**Impact:** Temporary network blips or brief service restarts cause user-visible errors that could have been automatically recovered.

| Severity | Effort |
|---|---|
| Medium | Small |

### 7.3 No Timeout Configuration

**Gap:** No explicit connection or read timeouts configured on Feign clients. Default timeouts may be excessively long or non-existent, depending on the HTTP client implementation.

**Impact:** Hung downstream services will block calling threads indefinitely, eventually exhausting the thread pool.

| Severity | Effort |
|---|---|
| High | Small |

### 7.4 No Fallback Behavior

**Gap:** No fallback methods or default responses when downstream services are unavailable. Every failure propagates directly to the client as an error.

**Impact:** Users see raw errors instead of graceful degradation (e.g., "service temporarily unavailable, please try again").

| Severity | Effort |
|---|---|
| Medium | Medium |

### 7.5 No Idempotency Controls

**Gap:** The `POST` endpoints for fund transfer and utility payment have no idempotency keys. If a client retries a request (due to timeout or network error), the same transfer could be processed multiple times.

**Impact:** Duplicate fund transfers or payments could occur, causing financial discrepancies.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 7.6 No Database Transaction Isolation Strategy

**Gap:** `TransactionService` uses `@Transactional` on the class level but doesn't specify isolation level. The `internalFundTransfer` method reads, modifies, and saves account balances without optimistic or pessimistic locking on `BankAccountEntity`.

**Impact:** Concurrent fund transfers from the same account could result in race conditions, leading to incorrect balances or overdrafts.

| Severity | Effort |
|---|---|
| Critical | Medium |

### 7.7 Balance Calculation Bug

**Gap:** In `TransactionService.internalFundTransfer()` and `utilPayment()`, the available balance is calculated by subtracting from the *already-updated* actual balance:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
This double-subtracts from `availableBalance`, making it `actualBalance - 2*amount` instead of `actualBalance - amount`.

**Impact:** Account balances become incorrect after the first transaction. Available balance diverges from actual balance.

| Severity | Effort |
|---|---|
| Critical | Small |

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 3 | 1 | 5 |
| Error Handling | 1 | 2 | 0 | 0 | 3 + 1 = 4 |
| Testing | 0 | 2 | 2 | 1 | 5 |
| Security | 4 | 1 | 1 | 1 | 7 |
| API Design | 0 | 0 | 3 | 3 | 6 |
| Observability | 1 | 0 | 4 | 1 | 6 |
| Resilience | 3 | 2 | 2 | 0 | 7 |
| **Total** | **9** | **8** | **15** | **7** | **39** |
