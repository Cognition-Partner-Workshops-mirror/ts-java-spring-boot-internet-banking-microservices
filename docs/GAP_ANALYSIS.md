# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps found across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is a standalone Gradle project with its own `gradlew`, `settings.gradle`, and `build.gradle`. There is no root-level `settings.gradle` or shared build configuration. This leads to:
- Duplicated dependency versions across 7 `build.gradle` files (e.g., Spring Boot 3.2.4, Spring Cloud 2023.0.0 repeated everywhere).
- No single command to build/test all services.
- Risk of version drift between services.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3+ services with identical or near-identical implementations:
- `BaseMapper` (4 copies: core, user, fund-transfer, utility-payment)
- `AuditAware` (3 copies: user, fund-transfer, utility-payment)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (3 copies)
- `SimpleBankingGlobalException` (4 copies with slight variations)
- `ErrorResponse` (3 copies with different implementations: some use `@Builder`, some don't)
- `GlobalExceptionHandler` (3 copies)
- `CustomFeignClientConfiguration` (3 copies with different implementations)

There is no shared library or common module to house cross-cutting concerns.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- Core Banking Service uses `repository/` at the top level; other services use `model/repository/`.
- Fund Transfer Service puts Feign clients under `service/rest/client/`; Utility Payment Service uses `service/rest/`; User Service uses `service/rest/`.
- DTO request/response packages vary: `model/dto/request/` vs `model/rest/request/`.

### 1.4 Mappers Instantiated Manually

**Severity: Low | Effort: Small**

Mappers (e.g., `UserMapper`, `FundTransferMapper`) are instantiated with `new` inside service classes rather than being Spring beans. This makes them untestable via DI and inconsistent with the rest of the codebase's DI patterns.

---

## 2. Error Handling

### 2.1 Inconsistent Exception Hierarchies

**Severity: High | Effort: Medium**

- Core Banking Service has `EntityNotFoundException`, `InsufficientFundsException`, and `GlobalErrorCode` constants.
- User Service has `EntityNotFoundException`, `InvalidBankingUserException`, `InvalidEmailException`, `UserAlreadyRegisteredException`, and `GlobalErrorCode`.
- Fund Transfer and Utility Payment services have only `SimpleBankingGlobalException`.
- `SimpleBankingGlobalException` has different constructor signatures across services.

### 2.2 Generic Exception Handlers Leak Internal Details

**Severity: Critical | Effort: Small**

All `GlobalExceptionHandler` classes have a catch-all handler:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest()
        .body("Exception occur inside API " + e);
}
```
This concatenates the full exception (including stack trace) into the HTTP response body, potentially exposing:
- Internal class names and line numbers
- Database connection strings
- Third-party service details

### 2.3 All Errors Return HTTP 400

**Severity: High | Effort: Small**

Every exception handler returns `400 Bad Request` regardless of the actual error type:
- `EntityNotFoundException` should return `404 Not Found`.
- `InsufficientFundsException` should return `422 Unprocessable Entity` or a domain-specific code.
- Unexpected exceptions should return `500 Internal Server Error`.
- Authentication/authorization failures should return `401`/`403`.

### 2.4 No Error Response Envelope Standard

**Severity: Medium | Effort: Small**

`ErrorResponse` implementations differ across services:
- Core Banking and User Service: `@Data` with `code` + `message` fields.
- Fund Transfer and Utility Payment: `@Builder` with `code` + `message` fields.
- The catch-all handler returns a plain `String`, not an `ErrorResponse` object.

There is no standard error envelope (e.g., RFC 7807 Problem Details).

### 2.5 No Feign Error Propagation in Fund Transfer / Utility Payment

**Severity: High | Effort: Medium**

- User Service has a `CustomFeignErrorDecoder` that extracts and re-throws domain exceptions from core-banking-service error responses.
- Fund Transfer Service's `CustomFeignClientConfiguration` only sets Feign log level; it has **no error decoder**.
- Utility Payment Service's `CustomFeignClientConfiguration` extends `FeignClientConfiguration` but adds **nothing**.
- When core-banking-service returns an error (e.g., insufficient funds), these services will get a generic Feign exception instead of the domain-specific error.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Files | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 files | 3 service-level unit test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) + 1 contextLoads |
| internet-banking-user-service | 1 file | contextLoads only |
| internet-banking-fund-transfer-service | 1 file | contextLoads only |
| internet-banking-utility-payment-service | 1 file | contextLoads only |
| internet-banking-api-gateway | 1 file | contextLoads only |
| internet-banking-config-server | 1 file | contextLoads only |
| internet-banking-service-registry | 1 file | contextLoads only |

Only the core-banking-service has actual unit tests. The other 6 services have **zero** business logic tests.

### 3.2 No Controller / Integration Tests

**Severity: High | Effort: Large**

- No `@WebMvcTest` or `MockMvc`-based controller tests in any service.
- No `@SpringBootTest` integration tests that actually exercise endpoints.
- No Testcontainers or Docker-based integration tests for database interactions.

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

- No Spring Cloud Contract or Pact tests between services.
- Feign client interfaces have no corresponding contract verification.
- Breaking API changes in core-banking-service would not be detected by fund-transfer or utility-payment services until runtime.

### 3.4 Context Load Tests Will Fail

**Severity: Medium | Effort: Small**

Several `@SpringBootTest` contextLoads tests (user-service, fund-transfer, utility-payment) require external dependencies (config server, Eureka, MySQL, Keycloak) and will fail when run in isolation. Test config files disable Eureka but don't fully mock all dependencies.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are hardcoded in version-controlled files:
- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`)
- `privileges.sql`: MySQL application user password (`oPItyPticIAt`)
- `Dockerfile` (MySQL): Root password in ENV
- `BANKING_CORE_MICROSERVICES_PROJECT.postman_environment.json`: Keycloak client secret
- `README.md`: Test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`)
- Test `application.yml` (user-service): Keycloak client secret

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Pattern`, or any Bean Validation annotations on any request DTOs across all services:
- `FundTransferRequest`: No validation on fromAccount, toAccount, amount (could be null, negative, or zero).
- `UtilityPaymentRequest`: No validation on providerId, amount, referenceNumber, account.
- `User` registration: No validation on email format, password strength, identification format.

### 4.3 Missing Authorization on Downstream Services

**Severity: High | Effort: Medium**

- The API Gateway enforces JWT authentication, but downstream services (core-banking, user, fund-transfer, utility-payment) have **no security configuration**.
- Any service-to-service call bypasses authentication.
- If any service port is exposed (e.g., via misconfigured network), all endpoints are accessible without authentication.
- No role-based access control (RBAC) beyond Keycloak's basic realm roles.

### 4.4 Keycloak Singleton Anti-Pattern

**Severity: Medium | Effort: Small**

`KeycloakProperties` uses a static mutable singleton for the `Keycloak` instance:
```java
private static Keycloak keycloakInstance = null;
```
This is not thread-safe and cannot be refreshed if credentials rotate.

### 4.5 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning is configured in any build file.

### 4.6 CSRF Disabled Globally

**Severity: Low | Effort: Small**

API Gateway disables CSRF (`http.csrf(CsrfSpec::disable)`). Acceptable for a pure API service, but should be documented as a conscious decision.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics

**Severity: Medium | Effort: Small**

All controller methods return `ResponseEntity` without type parameters (e.g., `ResponseEntity<BankAccount>`). This:
- Suppresses compile-time type checking.
- Makes OpenAPI documentation incomplete (response schemas not generated).
- Produces Swagger UI with no response model information.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` but there is no documented versioning strategy, no content negotiation, and no mechanism for running multiple API versions simultaneously.

### 5.3 Pagination Not Standardized

**Severity: Medium | Effort: Small**

- Endpoints accept Spring's `Pageable` parameter but return raw `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (totalElements, totalPages, pageNumber, pageSize).
- Clients cannot determine if more pages exist.

### 5.4 No Filtering or Sorting on List Endpoints

**Severity: Low | Effort: Medium**

List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, `GET /api/v1/user`) only support basic pagination. No filtering by status, date range, account number, or custom sorting.

### 5.5 Inconsistent URL Naming

**Severity: Low | Effort: Small**

- Core Banking: `/api/v1/transaction/fund-transfer` and `/api/v1/transaction/util-payment`
- Fund Transfer Service: `/api/v1/transfer`
- Utility Payment Service: `/api/v1/utility-payment`
- User Service: `/api/v1/bank-user` with sub-paths `/register` and `/update/{id}`
- Gateway prefixes: `/core`, `/user`, `/fund-transfer`, `/payment`

No consistent naming convention (e.g., `util-payment` vs `utility-payment`; `bank-user` vs just `user`).

### 5.6 OpenAPI/Swagger Partially Configured

**Severity: Medium | Effort: Small**

- `springdoc-openapi-starter-webflux-ui` is included in core, user, fund-transfer, and utility-payment services.
- Controller-level `@Tag` and `@Operation` annotations are present.
- However, no `@ApiResponse`, `@Schema`, or response type documentation.
- No centralized Swagger aggregation at the gateway level.
- Incorrect Swagger dependency: `webflux-ui` is used in non-reactive (servlet) services.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log requests; others (e.g., `UtilityPaymentController`) have no `@Slf4j` and no logging.
- Log messages are inconsistent: some log the full request object (`toString()`), some log just an ID.
- FundTransferService has a bug in log formatting: `"Sending fund transfer request {}" + request.toString()` (string concatenation instead of SLF4J placeholder).
- No structured logging (JSON) configured.
- No correlation ID propagation in log messages (despite Zipkin being configured for tracing).

### 6.2 Health Checks Are Default Only

**Severity: Low | Effort: Small**

- Spring Boot Actuator is included in all services, providing `/actuator/health`.
- No custom health indicators for critical dependencies (MySQL connectivity, Keycloak availability, Eureka registration status).
- No readiness/liveness probe distinction.

### 6.3 No Metrics Endpoints

**Severity: Medium | Effort: Small**

- Actuator is present, and Micrometer is a transitive dependency, but no Prometheus endpoint is configured (`/actuator/prometheus`).
- README mentions Prometheus but it's not in docker-compose or any service configuration.
- No custom business metrics (e.g., transfer count, payment amount, error rate).

### 6.4 Zipkin Tracing Configuration Is Externalized

**Severity: Low | Effort: Small**

- Tracing libraries are included in all business services.
- Actual Zipkin URL and sampling rate configuration is managed via the external config server Git repo, making it invisible from the codebase alone.
- No fallback if Zipkin is unavailable (tracing will silently fail, which is fine, but not documented).

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

- All inter-service communication is synchronous via Feign.
- If core-banking-service is down, fund-transfer and utility-payment services will block on Feign calls indefinitely or until TCP timeout.
- No Resilience4j circuit breakers, bulkheads, or rate limiters.
- No `spring-cloud-starter-circuitbreaker-resilience4j` dependency in any service.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

- No Feign retryer configuration.
- No Spring Retry dependency or `@Retryable` annotations.
- Transient network failures (e.g., DNS hiccup, temporary core-banking unavailability) cause immediate failure.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

- No Feign timeout configuration visible in the codebase (connect timeout, read timeout).
- Default Feign timeouts are effectively infinite (or JVM default socket timeout).
- No gateway timeout configuration for downstream calls.
- `wait-for-it.sh` uses 50-second timeout for startup dependencies, but there are no runtime timeouts.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

- No `@FeignClient(fallback = ...)` or `fallbackFactory` configured.
- When core-banking-service fails, orchestrating services propagate the error directly to the client.
- No graceful degradation (e.g., queuing failed transfers for later retry).

### 7.5 No Idempotency Protection

**Severity: High | Effort: Medium**

- Fund transfers and utility payments have no idempotency key.
- If a client retries a request (e.g., due to timeout), the same transfer/payment could be processed multiple times.
- No duplicate detection mechanism.

### 7.6 Non-Atomic Distributed Operations

**Severity: Critical | Effort: Large**

- Fund transfer flow spans two databases (fund-transfer service DB + core-banking DB) in a single synchronous call chain.
- If core-banking-service succeeds but the fund-transfer service fails to update its local record (e.g., network issue on the return path), the data becomes inconsistent.
- No Saga pattern, compensation logic, or outbox pattern.
- Utility payment has the same issue.

### 7.7 Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` was already reduced. This means `availableBalance = originalActualBalance - 2 * amount`. The same double-subtraction bug exists for the credit side (double-addition).

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.2 | Exception handlers leak internal details | Error Handling | Critical | Small |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small |
| 4.2 | No input validation on any request DTO | Security | Critical | Medium |
| 7.1 | No circuit breakers on Feign calls | Resilience | Critical | Medium |
| 7.6 | Non-atomic distributed operations | Resilience | Critical | Large |
| 7.7 | Balance calculation bug (double subtract) | Resilience | Critical | Small |
| 3.1 | Minimal test coverage (only core-banking has tests) | Testing | Critical | Large |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 2.1 | Inconsistent exception hierarchies | Error Handling | High | Medium |
| 2.3 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.5 | No Feign error decoder in fund-transfer / utility-payment | Error Handling | High | Medium |
| 3.2 | No controller or integration tests | Testing | High | Large |
| 4.3 | No auth on downstream services | Security | High | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.5 | No idempotency protection | Resilience | High | Medium |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 2.4 | No error response envelope standard | Error Handling | Medium | Small |
| 3.3 | No contract tests between services | Testing | Medium | Large |
| 3.4 | Context load tests will fail in isolation | Testing | Medium | Small |
| 4.4 | Keycloak singleton anti-pattern | Security | Medium | Small |
| 4.5 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without generics | API Design | Medium | Small |
| 5.3 | Pagination not standardized | API Design | Medium | Small |
| 5.6 | OpenAPI/Swagger partially configured | API Design | Medium | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.3 | No Prometheus metrics endpoint | Observability | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers instantiated manually | Code Organization | Low | Small |
| 4.6 | CSRF disabled without documentation | Security | Low | Small |
| 5.2 | No API versioning strategy | API Design | Low | Medium |
| 5.4 | No filtering or sorting on list endpoints | API Design | Low | Medium |
| 5.5 | Inconsistent URL naming | API Design | Low | Small |
| 6.2 | Health checks are default only | Observability | Low | Small |
| 6.4 | Zipkin config externalized and undocumented | Observability | Low | Small |
