# Engineering Standards Gap Analysis

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## Methodology

Each gap is rated on two axes:

- **Severity:** Critical / High / Medium / Low
- **Effort to Remediate:** Small (< 1 day) / Medium (1-3 days) / Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Root Project

**Severity: Medium | Effort: Small**

Each of the 6 microservices is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds, enforce common dependency versions, or share plugin configuration.

- Building all services requires running `./gradlew build` in each directory individually.
- Dependency version drift is likely — each service independently declares Spring Boot `3.2.4` and Spring Cloud `2023.0.0`.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

Significant code is copy-pasted across services with no shared library:

| Duplicated Class | Services |
|-----------------|----------|
| `BaseMapper<E, D>` | core-banking, fund-transfer, user, utility-payment |
| `AuditAware` (JPA base class) | fund-transfer, user, utility-payment |
| `ErrorResponse` | core-banking, fund-transfer, user, utility-payment |
| `SimpleBankingGlobalException` | core-banking, fund-transfer, user, utility-payment |
| `GlobalExceptionHandler` | core-banking, fund-transfer, user, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` | fund-transfer, user, utility-payment |
| `AuditConfig` / `AuditorAwareConfig` | fund-transfer, user, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |
| `TransactionStatus` enum | fund-transfer, utility-payment |
| `AccountResponse` DTO | fund-transfer, user, utility-payment (with slight differences) |

Any change to shared logic (e.g., error response format) requires updating 4 services independently.

### 1.3 Inconsistent Package Naming

**Severity: Low | Effort: Small**

- User Service places its repository in `model.repository` while other services use `repository` at the top level.
- Feign clients are at `service.rest.client` (fund-transfer), `service.rest` (user, utility-payment) — inconsistent paths.
- User Service has `configuration.feign` while Fund Transfer and Utility Payment have `configuration` directly.

### 1.4 DTO / Entity Coupling

**Severity: Medium | Effort: Medium**

- `AuditAware` is a JPA `@MappedSuperclass` placed in a `model.dto` package and used as a base class for both DTOs (`FundTransfer`, `UtilityPayment`, `User`) and entities. This couples persistence annotations to the DTO layer.
- `FundTransferRequest` in Core Banking differs from the one in Fund Transfer Service (different fields: core has no `authID`), which is fragile.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Leaks Internal Details

**Severity: Critical | Effort: Small**

All `GlobalExceptionHandler` implementations have a catch-all handler:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

This returns the full exception `toString()` (including stack trace fragments, class names, and internal details) directly to the client. In a banking application, this is a **security vulnerability** that could expose:
- Database schema details from SQL exceptions
- File paths from I/O exceptions
- Internal class hierarchy and library versions

### 2.2 All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Small**

Both the custom exception handler and the generic catch-all return `ResponseEntity.badRequest()` (HTTP 400) for **every** error type:

- `EntityNotFoundException` → should be **404 Not Found**
- `InsufficientFundsException` → could be **422 Unprocessable Entity**
- `UserAlreadyRegisteredException` → could be **409 Conflict**
- Unknown exceptions → should be **500 Internal Server Error**

Using 400 for everything makes it impossible for clients to distinguish between validation errors, not-found errors, and server errors.

### 2.3 No Error Handling for Feign Client Failures

**Severity: High | Effort: Medium**

- Only the User Service has a `CustomFeignErrorDecoder`. Fund Transfer and Utility Payment services have a `CustomFeignClientConfiguration` that only adds request interceptors — no error decoder.
- If Core Banking returns an error to Fund Transfer or Utility Payment, the Feign default error decoder will throw a generic `FeignException`, which gets caught by the catch-all and leaks internal details.
- Fund Transfer and Utility Payment services save an entity with `PENDING`/`PROCESSING` status but never update it to `FAILED` if the Core Banking call fails.

### 2.4 Missing Error Codes in Fund Transfer and Utility Payment Services

**Severity: Medium | Effort: Small**

- Core Banking and User Service define `GlobalErrorCode` constants for structured error responses.
- Fund Transfer and Utility Payment services have no `GlobalErrorCode` class — their `SimpleBankingGlobalException` instances would have `null` error codes.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Classes | Test Type | Coverage |
|---------|-------------|-----------|----------|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | Unit (Mockito) + context load | Service layer only; no controller tests |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load only | **No functional tests** |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only | **No functional tests** |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only | **No functional tests** |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only | **No functional tests** |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load only | **No functional tests** |

- Only Core Banking has meaningful unit tests.
- **Context load tests require external dependencies** (Eureka, Config Server, MySQL, Keycloak) and will fail in isolation — these are not true unit tests.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

- No `@SpringBootTest` with `@AutoConfigureMockMvc` or `@WebMvcTest` controller tests.
- No Testcontainers-based integration tests for database operations.
- No Feign client contract tests (e.g., Spring Cloud Contract or WireMock).

### 3.3 No End-to-End / Smoke Tests

**Severity: Medium | Effort: Large**

- No docker-compose-based E2E test suite.
- No Postman/Newman automated test runner (collection exists for manual use only).

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin` / `password` |
| `docker-compose.yml` | Keycloak DB: `keycloak` / `password` |
| `docker-compose/mysql/Dockerfile` | `MYSQL_ROOT_PASSWORD=woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `user-service test application.yml` | Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

- **No `@Valid` or `@Validated` annotations** on any controller request body.
- **No Bean Validation annotations** (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Positive`, `@Email`) on any request DTO.
- A fund transfer request with `amount=null`, `amount=-1`, or missing `fromAccount` will produce unhandled `NullPointerException` or proceed with invalid data.
- User registration accepts any string as `email` — no email format validation.

### 4.3 No CSRF Protection on Non-Gateway Services

**Severity: Medium | Effort: Small**

- The API Gateway disables CSRF (`http.csrf(ServerHttpSecurity.CsrfSpec::disable)`) — acceptable for a stateless JWT-based API.
- Downstream services don't configure security at all — they rely entirely on the gateway for authentication.
- If a downstream service is accessed directly (bypassing the gateway), there is **zero authentication or authorization**.

### 4.4 Overly Broad Database Privileges

**Severity: Medium | Effort: Small**

The MySQL user `javatodev_development` is granted `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` — all databases. Each service should use a dedicated user scoped to its own database.

### 4.5 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

- No OWASP Dependency-Check, Snyk, or Dependabot configuration.
- Dependencies are pinned to specific versions (good) but never checked for known CVEs.

### 4.6 `X-Auth-Id` Header Spoofable Without Gateway

**Severity: Medium | Effort: Small**

Downstream services trust the `X-Auth-Id` header blindly. If a service is accessed directly (not through the gateway), an attacker can set this header to any value and impersonate any user.

---

## 5. API Design

### 5.1 Missing Response Type Generics

**Severity: Medium | Effort: Small**

All controller methods return raw `ResponseEntity` without type parameters:

```java
public ResponseEntity getBankAccount(...) { ... }
```

This means:
- No compile-time type safety
- OpenAPI/Swagger documentation cannot infer response schemas
- API clients cannot auto-generate typed code

Should be: `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

- APIs use `/api/v1/` prefix, which is good.
- However, there is no documented versioning strategy, no `v2` plan, and no content negotiation or header-based versioning.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

- List endpoints accept `Pageable` parameters but return `List<T>` — losing pagination metadata (total elements, total pages, current page).
- Clients cannot build proper pagination UIs without knowing the total count.

### 5.4 No Filtering or Search Capabilities

**Severity: Low | Effort: Medium**

- No query parameter filtering on list endpoints (e.g., filter transfers by status, date range, account).
- No search endpoint for transactions.

### 5.5 Inconsistent OpenAPI Documentation

**Severity: Medium | Effort: Small**

- Core Banking, Fund Transfer, and Utility Payment services include `springdoc-openapi-starter-webflux-ui:2.1.0` — but this is the **WebFlux** starter on **WebMVC** services (incorrect dependency).
- User Service and other services include the same incorrect dependency.
- `@Tag` and `@Operation` annotations are present but response schemas are undocumented due to raw `ResponseEntity` usage.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some services use `@Slf4j` (Lombok) on controllers and services; others don't.
- Fund Transfer Service has a logging bug: `log.info("Sending fund transfer request {}" + request.toString())` — uses string concatenation instead of SLF4J placeholders, which bypasses lazy evaluation.
- No structured logging (JSON format) configured.
- No MDC (Mapped Diagnostic Context) enrichment with trace IDs, user IDs, or request IDs beyond what Micrometer provides.

### 6.2 No Health Check Customization

**Severity: Low | Effort: Small**

- `spring-boot-starter-actuator` is included in all services.
- No custom health indicators for critical dependencies (Keycloak connectivity, Core Banking availability, database connectivity beyond the default).
- Actuator endpoints are exposed but not documented or secured (beyond the gateway's permit-all rule for `/actuator/**`).

### 6.3 No Metrics Endpoints Configuration

**Severity: Medium | Effort: Small**

- README mentions Prometheus but no `micrometer-registry-prometheus` dependency exists in any `build.gradle`.
- No custom business metrics (transfer count, payment volume, error rates).

### 6.4 Distributed Tracing Partially Configured

**Severity: Low | Effort: Small**

- Tracing dependencies are present in all business services.
- Config Server does NOT have tracing dependencies.
- No explicit sampling rate configuration (defaults to 10%).
- Zipkin URL configuration is externalized (good), but no fallback if Zipkin is unavailable.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

- No Spring Cloud Circuit Breaker, Resilience4j, or Hystrix dependency in any service.
- If Core Banking Service goes down, Fund Transfer and Utility Payment services will fail with unhandled exceptions.
- Cascading failures could bring down the entire system.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

- No Spring Retry or Resilience4j retry configuration on Feign clients.
- Transient network errors between services cause immediate failures.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

- No Feign client timeout configuration (connect timeout, read timeout).
- No Spring Cloud Gateway route-level timeout configuration.
- Default timeouts may be too long, causing thread exhaustion under load.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

- No fallback responses when downstream services are unavailable.
- No graceful degradation — if any downstream service fails, the entire request fails with an unhandled exception.

### 7.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

- No rate limiting at the API Gateway level.
- No protection against brute-force attacks on authentication or fund transfer endpoints.

### 7.6 No Idempotency Keys for Financial Transactions

**Severity: High | Effort: Medium**

- Fund transfer and utility payment endpoints have no idempotency mechanism.
- A network retry could cause double-debiting an account.
- `transactionId` is generated server-side, so clients cannot use it to prevent duplicates.

### 7.7 Transaction Integrity Issues

**Severity: Critical | Effort: Medium**

- Fund Transfer Service saves a local `PENDING` entity, then calls Core Banking. If the Core Banking call succeeds but the subsequent local `SUCCESS` update fails, the local state is inconsistent.
- No compensating transaction or Saga pattern for distributed transactions.
- Core Banking's `internalFundTransfer` uses `@Transactional` but `TransactionEntity` has `@OneToOne(cascade = CascadeType.ALL)` on `account`, meaning saving a transaction could unintentionally cascade changes to the account entity.
- The `availableBalance` calculation has a bug: it uses `actualBalance` after subtraction, causing a double-debit effect:
  ```java
  fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
  fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
  // availableBalance = actualBalance - amount - amount (double subtraction)
  ```

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|-----|----------|--------|----------|
| 2.1 | Exception handler leaks internals | Critical | Small | Error Handling |
| 3.1 | Minimal test coverage | Critical | Large | Testing |
| 4.1 | Hardcoded credentials | Critical | Small | Security |
| 4.2 | No input validation | Critical | Medium | Security |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.7 | Transaction integrity / double-debit bug | Critical | Medium | Resilience |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 2.2 | All errors return HTTP 400 | High | Small | Error Handling |
| 2.3 | No Feign error handling (transfer/payment) | High | Medium | Error Handling |
| 3.2 | No integration tests | High | Large | Testing |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 7.6 | No idempotency for financial transactions | High | Medium | Resilience |
| 1.1 | No multi-module Gradle root | Medium | Small | Code Organization |
| 1.4 | DTO/entity coupling | Medium | Medium | Code Organization |
| 2.4 | Missing error codes in some services | Medium | Small | Error Handling |
| 3.3 | No E2E tests | Medium | Large | Testing |
| 4.3 | No auth on downstream services | Medium | Small | Security |
| 4.4 | Overly broad DB privileges | Medium | Small | Security |
| 4.5 | No dependency vulnerability scanning | Medium | Small | Security |
| 4.6 | X-Auth-Id header spoofable | Medium | Small | Security |
| 5.1 | Missing ResponseEntity generics | Medium | Small | API Design |
| 5.3 | No pagination metadata | Medium | Small | API Design |
| 5.5 | Incorrect OpenAPI dependency | Medium | Small | API Design |
| 6.1 | Inconsistent logging | Medium | Small | Observability |
| 6.3 | No Prometheus metrics | Medium | Small | Observability |
| 7.4 | No fallback behavior | Medium | Medium | Resilience |
| 7.5 | No rate limiting | Medium | Medium | Resilience |
| 1.3 | Inconsistent package naming | Low | Small | Code Organization |
| 5.2 | No versioning strategy | Low | Medium | API Design |
| 5.4 | No filtering/search | Low | Medium | API Design |
| 6.2 | No custom health checks | Low | Small | Observability |
| 6.4 | Tracing partially configured | Low | Small | Observability |
