# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **severity** (Critical / High / Medium / Low) and **estimated remediation effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to manage shared configuration (plugin versions, dependency versions, common repositories, Java toolchain).

**Impact:** Plugin and dependency versions must be synchronized manually across 6 services. For example, Spring Boot `3.2.4`, Spring Cloud `2023.0.0`, and `mysql-connector-j:8.4.0` are repeated in every `build.gradle`.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across multiple services with near-identical implementations:

| Duplicated Class | Services |
|-----------------|----------|
| `AppAuthUserFilter` | User Service, Fund Transfer Service, Utility Payment Service |
| `ApiRequestContext` / `ApiRequestContextHolder` | User Service, Fund Transfer Service, Utility Payment Service |
| `AuditAware` / `AuditConfig` / `AuditorAwareConfig` | User Service, Fund Transfer Service, Utility Payment Service |
| `BaseMapper<E, D>` | All 4 business services |
| `ErrorResponse` | All 4 business services |
| `GlobalExceptionHandler` | All 4 business services |
| `SimpleBankingGlobalException` | All 4 business services |
| `CustomFeignClientConfiguration` | Fund Transfer Service, Utility Payment Service |

**Impact:** Bug fixes and improvements must be replicated manually. Divergence risk is high (the `ErrorResponse` in Fund Transfer already differs from Core Banking's version in its constructor usage).

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- Core Banking Service places repositories under `com.javatodev.finance.repository` while other services use `com.javatodev.finance.model.repository`.
- User Service places Feign clients under `com.javatodev.finance.service.rest` while Fund Transfer uses `com.javatodev.finance.service.rest.client`.
- Core Banking has no `configuration` package (no audit, filter, or Feign config).

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated as non-final fields using `new` rather than being Spring-managed beans:

```java
private UserMapper userMapper = new UserMapper(); // in 3+ services
```

This bypasses Spring's lifecycle, makes testing harder, and is inconsistent with the `@RequiredArgsConstructor` dependency injection pattern used everywhere else.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns HTTP 400 for All Errors

**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` has this pattern:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

**Problems:**
- Returns `400 Bad Request` for server errors (NPE, DB connection failures, etc.) instead of `500`.
- Leaks internal exception details (stack traces, class names) to clients.
- Returns a plain string instead of the structured `ErrorResponse` format.

### 2.2 Inconsistent Error Response Formats

**Severity: High | Effort: Small**

- `SimpleBankingGlobalException` subclasses return structured `ErrorResponse { code, message }`.
- The catch-all `Exception` handler returns a raw string: `"Exception occur inside API " + e`.
- This means clients cannot reliably parse error responses.

### 2.3 Missing HTTP Status Code Differentiation

**Severity: High | Effort: Small**

All handled exceptions return `400 Bad Request`:
- `EntityNotFoundException` should return `404 Not Found`.
- `InsufficientFundsException` could return `422 Unprocessable Entity`.
- `UserAlreadyRegisteredException` should return `409 Conflict`.
- Unhandled exceptions should return `500 Internal Server Error`.

### 2.4 No Feign Error Handling in Fund Transfer / Utility Payment

**Severity: High | Effort: Medium**

- Fund Transfer Service calls `bankingCoreFeignClient.fundTransfer(request)` with no error handling. If Core Banking returns an error, the raw Feign exception propagates.
- User Service has a `CustomFeignErrorDecoder` but Fund Transfer and Utility Payment services do not decode Feign errors into domain exceptions.
- If the Core Banking Service is down, users receive an opaque 400 error instead of a meaningful "service unavailable" response.

### 2.5 No Validation Error Handling

**Severity: Medium | Effort: Small**

No handler for `MethodArgumentNotValidException` or `ConstraintViolationException`, meaning if validation annotations were added, Spring's default error format would be returned instead of the application's `ErrorResponse` format.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Unit Tests | Integration Tests | Contract Tests |
|---------|-----------|-------------------|----------------|
| Core Banking | 3 test classes (AccountService, TransactionService, UserService) | None | None |
| User Service | Context load test only | None | None |
| Fund Transfer | Context load test only | None | None |
| Utility Payment | Context load test only | None | None |
| API Gateway | Context load test only | None | None |
| Config Server | Context load test only | None | None |
| Service Registry | Context load test only | None | None |

**Impact:** Only Core Banking Service has meaningful tests. The three internet-banking services (User, Fund Transfer, Utility Payment) have zero business logic tests.

### 3.2 No Controller / API Layer Tests

**Severity: High | Effort: Medium**

No `@WebMvcTest` or `MockMvc`-based tests exist. API contracts (request/response shapes, status codes, error handling) are completely untested.

### 3.3 No Integration Tests

**Severity: High | Effort: Large**

No `@SpringBootTest` integration tests with real (or Testcontainers-based) databases. The existing Core Banking tests use pure Mockito without Spring context.

### 3.4 No Contract Tests Between Services

**Severity: Medium | Effort: Large**

Services communicate via OpenFeign but there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). Changes to Core Banking API could silently break Fund Transfer and Utility Payment services.

### 3.5 Context Load Tests Will Fail Without External Dependencies

**Severity: Medium | Effort: Small**

The context load tests (e.g., `InternetBankingUserServiceApplicationTests`) require Config Server, Eureka, MySQL, and Keycloak to be running. They will fail in CI without these dependencies or proper test profiles.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

| Location | Credential |
|----------|-----------|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | `Test Credentials: ib_admin@javatodev.com / 5V7huE3G86uB` |

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No request DTOs have Jakarta Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, etc.):

```java
// FundTransferRequest - no validation at all
public class FundTransferRequest {
    private String fromAccount;  // could be null
    private String toAccount;    // could be null
    private BigDecimal amount;   // could be null, negative, or zero
}
```

**Impact:** Null account numbers, negative transfer amounts, and empty fields will reach the database layer and cause unhandled exceptions.

### 4.3 CSRF Disabled Without Explanation

**Severity: Medium | Effort: Small**

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF is disabled in the API Gateway. While this is common for stateless JWT APIs, there is no documentation or security review confirming this is intentional and safe.

### 4.4 Keycloak Singleton is Not Thread-Safe

**Severity: High | Effort: Small**

```java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

The lazy singleton in `KeycloakProperties` has a classic double-checked locking problem without `synchronized` or `volatile`. Multiple Keycloak instances could be created under concurrent load.

### 4.5 Password Accepted in Plain Text, Logged in DTO

**Severity: High | Effort: Small**

The `User` DTO includes a `password` field that is:
- Accepted in the registration request body.
- Logged via `log.info("Creating user with {}", request.toString())` which uses Lombok's `@Data` (includes all fields in `toString()`).
- Passed directly to Keycloak without any server-side validation of password strength.

### 4.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured on the API Gateway or individual services. The user registration endpoint is public and unprotected.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar tool is configured in the Gradle builds or CI pipeline.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return untyped `ResponseEntity` instead of `ResponseEntity<T>`:

```java
public ResponseEntity getBankAccount(...) { ... }  // raw type
```

**Impact:** OpenAPI/Swagger cannot infer response schemas. The generated API documentation shows `object` instead of the actual DTO structure.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/` but there is no mechanism for introducing `/api/v2/` alongside v1, no version negotiation via headers, and no documentation of the versioning strategy.

### 5.3 Inconsistent Resource Naming

**Severity: Low | Effort: Small**

- Core Banking: `/api/v1/account/bank-account/{account_number}` (uses underscores in path variable)
- Core Banking: `/api/v1/account/util-account/{account_name}` (abbreviates "utility")
- User Service: `/api/v1/bank-users/register` (non-RESTful verb in URL)
- User Service: `/api/v1/bank-users/update/{id}` (non-RESTful verb, should use `PATCH /api/v1/bank-users/{id}`)

### 5.4 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept Spring `Pageable` parameters but return raw `List<T>`. Clients receive no information about total pages, total elements, or whether more pages exist.

```java
public ResponseEntity readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable)); // returns List<User>, not Page<User>
}
```

### 5.5 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

Spring's `Pageable` supports `sort`, `page`, and `size` query parameters by default but these are not documented in the API or OpenAPI annotations.

### 5.6 Swagger Dependency Mismatch

**Severity: Medium | Effort: Small**

The services use `springdoc-openapi-starter-webflux-ui` but only the API Gateway is a WebFlux application. The other services are Spring MVC (servlet-based) and should use `springdoc-openapi-starter-webmvc-ui`.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers log incoming requests (`log.info("Reading account by ID {}", accountNumber)`), others don't.
- Core Banking Service has no `@Slf4j` on `AccountService` or `UserService`.
- Utility Payment Controller has no `@Slf4j` annotation (no logging at all).
- Log messages contain typos: `"Reading utitlity account"`.

### 6.2 No Structured Logging

**Severity: Medium | Effort: Medium**

All logging uses plain text format. There is no JSON log format configured, making it difficult to parse logs in centralized logging systems (ELK, CloudWatch, etc.).

### 6.3 Health Check Configuration Not Visible

**Severity: Low | Effort: Small**

All services include `spring-boot-starter-actuator` but the actuator configuration (which endpoints are exposed, whether health checks include database/Eureka/Keycloak status) is managed externally via Spring Cloud Config, making it hard to audit locally.

### 6.4 No Custom Metrics

**Severity: Low | Effort: Medium**

No custom Micrometer metrics are defined for business operations (e.g., fund transfer count, payment success/failure rate, average transaction amount). Only default Spring Boot metrics are available.

### 6.5 Tracing Configuration Opacity

**Severity: Low | Effort: Small**

Tracing dependencies (Micrometer + Brave + Zipkin) are included but the sampling rate, propagation format, and excluded paths are configured externally in the Git-backed config repo. No local documentation exists for these settings.

### 6.6 No Correlation ID Propagation

**Severity: Medium | Effort: Small**

While Zipkin tracing provides trace IDs, these are not included in log output or API error responses. When a user reports an error, there is no way to correlate it with a specific trace.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service Feign calls have no circuit breaker configured. If Core Banking Service becomes slow or unavailable:
- Fund Transfer and Utility Payment services will block on Feign HTTP calls.
- Thread pools will exhaust.
- Cascading failures will propagate to the API Gateway and affect all endpoints.

Despite using Spring Cloud, neither Resilience4j nor Hystrix is in any `build.gradle`.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No Spring Retry or Resilience4j Retry is configured. Transient network failures between services cause immediate errors instead of retrying.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

Feign clients use default timeouts (which may be infinite depending on the HTTP client). No explicit `connectTimeout` or `readTimeout` is configured:

```java
@FeignClient(value = "core-banking-service", configuration = CustomFeignClientConfiguration.class)
```

If Core Banking hangs, the calling service hangs indefinitely.

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

No Feign fallback classes or factory methods are defined. When an upstream service fails, the raw exception propagates as a 400 error (due to the catch-all error handler).

### 7.5 No Idempotency Protection

**Severity: High | Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a request (due to timeout or network error), the same transfer could be processed multiple times.

### 7.6 Non-Atomic Balance Updates

**Severity: Critical | Effort: Medium**

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
bankAccountRepository.save(fromBankAccountEntity);
```

**Problems:**
- `availableBalance` is set to `actualBalance - amount` (double deduction on available balance).
- No pessimistic/optimistic locking on account entities. Concurrent transfers from the same account can result in negative balances.
- Same double-deduction bug exists in `utilPayment()`.

### 7.7 No Distributed Transaction Management

**Severity: High | Effort: Large**

Fund Transfer and Utility Payment services persist local records, then call Core Banking. If Core Banking succeeds but the local update fails (or vice versa), data becomes inconsistent. There is no Saga pattern, outbox pattern, or compensating transaction mechanism.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No multi-project Gradle build | Code Org | Medium | Medium |
| 1.2 | Duplicated code across services | Code Org | High | Medium |
| 1.3 | Inconsistent package structure | Code Org | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Org | Low | Small |
| 2.1 | Generic catch-all returns 400 for all errors | Error Handling | Critical | Small |
| 2.2 | Inconsistent error response formats | Error Handling | High | Small |
| 2.3 | Missing HTTP status code differentiation | Error Handling | High | Small |
| 2.4 | No Feign error handling in FT/UP services | Error Handling | High | Medium |
| 2.5 | No validation error handling | Error Handling | Medium | Small |
| 3.1 | Minimal test coverage | Testing | Critical | Large |
| 3.2 | No controller/API layer tests | Testing | High | Medium |
| 3.3 | No integration tests | Testing | High | Large |
| 3.4 | No contract tests between services | Testing | Medium | Large |
| 3.5 | Context load tests fail without deps | Testing | Medium | Small |
| 4.1 | Hardcoded credentials in source | Security | Critical | Small |
| 4.2 | No input validation | Security | Critical | Medium |
| 4.3 | CSRF disabled without documentation | Security | Medium | Small |
| 4.4 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.5 | Password logged in plain text | Security | High | Small |
| 4.6 | No rate limiting | Security | Medium | Medium |
| 4.7 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without type params | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Small |
| 5.3 | Inconsistent resource naming | API Design | Low | Small |
| 5.4 | No pagination metadata in responses | API Design | Medium | Small |
| 5.5 | No filtering/sorting documentation | API Design | Low | Small |
| 5.6 | Swagger dependency mismatch | API Design | Medium | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | No structured logging | Observability | Medium | Medium |
| 6.3 | Health check config not visible | Observability | Low | Small |
| 6.4 | No custom metrics | Observability | Low | Medium |
| 6.5 | Tracing configuration opacity | Observability | Low | Small |
| 6.6 | No correlation ID in logs/responses | Observability | Medium | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | High | Medium |
| 7.5 | No idempotency protection | Resilience | High | Medium |
| 7.6 | Non-atomic balance updates (double-deduction bug) | Resilience | Critical | Medium |
| 7.7 | No distributed transaction management | Resilience | High | Large |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 14 |
| Medium | 13 |
| Low | 7 |
