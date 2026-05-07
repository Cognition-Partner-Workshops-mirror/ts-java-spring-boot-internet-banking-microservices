# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each of the 6 services is a standalone Gradle project with its own `gradlew`, `gradle/wrapper`, `build.gradle`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to coordinate builds, enforce consistent dependency versions, or share build logic.

**Impact:** Dependency version drift between services, duplicated build configuration, inability to run `./gradlew build` from the repository root.

**Evidence:** Each service directory contains its own `gradle/wrapper/gradle-wrapper.jar` and `gradlew` script. Spring Boot version is repeated as `3.2.4` in each `build.gradle`.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

Identical or near-identical classes are copy-pasted across services with no shared library:

| Duplicated Class | Services |
|---|---|
| `AuditAware` | User, FundTransfer, UtilityPayment |
| `AppAuthUserFilter` | User, FundTransfer, UtilityPayment |
| `ApiRequestContext` / `ApiRequestContextHolder` | User, FundTransfer, UtilityPayment |
| `BaseMapper<E, D>` | Core, User, FundTransfer, UtilityPayment |
| `ErrorResponse` | Core, User, FundTransfer, UtilityPayment |
| `GlobalExceptionHandler` | Core, User, FundTransfer, UtilityPayment |
| `SimpleBankingGlobalException` | Core, User, FundTransfer, UtilityPayment |
| `CustomFeignClientConfiguration` | User, FundTransfer, UtilityPayment |
| `TransactionStatus` enum | FundTransfer, UtilityPayment |

**Impact:** Bug fixes must be applied in multiple places. Divergence is already visible (e.g., FundTransfer's `ErrorResponse` uses constructor while Core's uses builder).

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package naming varies slightly between services:
- Core Banking: `model.mapper`, `repository`
- User Service: `model.mapper`, `model.repository`
- Fund Transfer: `model.mapper`, `model.repository`
- Utility Payment: `model.mapper`, `repository`

The Feign client location also varies:
- User Service: `service.rest.BankingCoreRestClient`
- Fund Transfer: `service.rest.client.BankingCoreFeignClient`
- Utility Payment: `service.rest.BankingCoreRestClient`

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated with `new` inside `@Service` classes rather than being Spring-managed beans:
```java
private UserMapper userMapper = new UserMapper();
```
This bypasses dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Catch-All Handler Returns 400 for All Errors

**Severity: Critical | Effort: Small**

Every service's `GlobalExceptionHandler` catches `Exception.class` and returns HTTP 400 with a string body:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:**
- Server errors (NPE, DB connection failures, etc.) return 400 instead of 500.
- Stack traces may be leaked to clients via `e.toString()`.
- Clients cannot distinguish between client errors and server errors.

### 2.2 Inconsistent Error Response Format

**Severity: High | Effort: Small**

Two different error response formats exist:
1. **Structured:** `{ code: String, message: String }` for `SimpleBankingGlobalException`
2. **Unstructured:** Plain string `"Exception occur inside API " + e` for everything else

Additionally, Fund Transfer service constructs `ErrorResponse` via constructor while Core Banking and User Service use the Lombok builder — different instantiation patterns for the same logical class.

### 2.3 Missing HTTP Status Code Differentiation

**Severity: High | Effort: Small**

All custom exceptions map to HTTP 400 regardless of the actual error:
- `EntityNotFoundException` → 400 (should be 404)
- `InsufficientFundsException` → 400 (should be 422)
- `UserAlreadyRegisteredException` → 400 (should be 409)
- `InvalidEmailException` → 400 (appropriate)
- Generic `Exception` → 400 (should be 500)

### 2.4 No Feign Error Handling in Fund Transfer / Utility Payment

**Severity: High | Effort: Medium**

The Fund Transfer service calls `bankingCoreFeignClient.fundTransfer(request)` with no error handling. If the core banking call fails (network error, 4xx, 5xx), the local record remains in `PENDING` status forever with no retry or compensation.

User Service has a `CustomFeignErrorDecoder` but Fund Transfer and Utility Payment services do not decode Feign errors, relying on default behavior that throws opaque `FeignException`.

### 2.5 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)
```
This suppresses compile-time type checking and generates Swagger documentation without response type information.

---

## 3. Testing

### 3.1 Near-Zero Test Coverage Outside Core Banking

**Severity: Critical | Effort: Large**

| Service | Test Classes | Meaningful Tests |
|---|---|---|
| Core Banking | 4 | 16+ tests (AccountService, TransactionService, UserService) |
| User Service | 1 | 0 (context load test only) |
| Fund Transfer | 1 | 0 (context load test only) |
| Utility Payment | 1 | 0 (context load test only) |
| API Gateway | 1 | 0 (context load test only) |
| Config Server | 1 | 0 (context load test only) |
| Service Registry | 1 | 0 (context load test only) |

**Impact:** Business-critical services (user registration, fund transfer, utility payment) have zero unit test coverage.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- API endpoint behavior (no `@WebMvcTest` or `@SpringBootTest` with `MockMvc`)
- Database interactions (no `@DataJpaTest`)
- Feign client contract validation
- End-to-end flows across services

### 3.3 No Contract Tests Between Services

**Severity: High | Effort: Large**

Services communicate via Feign clients but there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). If Core Banking changes its API, downstream services will break silently until runtime.

### 3.4 Context-Load Tests Will Fail Without Infrastructure

**Severity: Medium | Effort: Small**

The existing empty `@SpringBootTest` tests in User, FundTransfer, and UtilityPayment services will fail because they attempt to connect to Eureka and Config Server at startup. The test application.yml files do not fully disable these.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|---|---|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB` |

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or `@Pattern` annotations exist on any request DTO. Examples of missing validation:

- `FundTransferRequest.amount` — no check for null, zero, or negative values
- `FundTransferRequest.fromAccount` / `toAccount` — no format validation, no check that they differ
- `User.email` — no email format validation
- `User.password` — no minimum length or complexity requirements
- `UtilityPaymentRequest.amount` — no minimum amount validation

### 4.3 CSRF Disabled Without Documentation

**Severity: Medium | Effort: Small**

CSRF protection is disabled at the gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While acceptable for a stateless JWT API, this should be explicitly documented and reviewed for any future browser-based clients.

### 4.4 Overly Broad Database Permissions

**Severity: High | Effort: Small**

The application database user has `CREATE, ALTER, DROP` privileges on `*.*`:
```sql
GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.* TO 'javatodev_development'@'%';
```
This grants DDL privileges across all databases, not just the application databases.

### 4.5 Keycloak Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static field with a simple null check — classic broken double-checked locking without `synchronized` or `volatile`. Under concurrent access this can create multiple Keycloak client instances or return a partially constructed object.

### 4.6 Password Returned in User DTO

**Severity: High | Effort: Small**

The `User` DTO contains a `password` field. When users are read (`GET /api/v1/bank-users/{id}`), the password field is included in the serialized JSON response. Even though it may be null after creation, the field should be annotated with `@JsonProperty(access = WRITE_ONLY)` or excluded from response DTOs.

### 4.7 No Downstream Service Authentication

**Severity: High | Effort: Medium**

Services behind the gateway (Core Banking, User, Fund Transfer, Utility Payment) have **no authentication or authorization**. Any network client that can reach them directly (bypassing the gateway) has unrestricted access. The `X-Auth-Id` header is read but never validated — it could be spoofed.

---

## 5. API Design

### 5.1 Non-RESTful URL Patterns

**Severity: Medium | Effort: Small**

Several endpoints deviate from RESTful conventions:
- `POST /api/v1/bank-users/register` — verb in URL (should be `POST /api/v1/bank-users`)
- `PATCH /api/v1/bank-users/update/{id}` — verb in URL (should be `PATCH /api/v1/bank-users/{id}`)
- `GET /api/v1/account/bank-account/{account_number}` — nested singular nouns
- `GET /api/v1/account/util-account/{account_name}` — abbreviation inconsistency

### 5.2 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While paths include `/v1/`, there is no versioning mechanism (header-based, content-type, or otherwise) to support backward-compatible evolution.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<T>`, discarding pagination metadata (total elements, total pages, current page). The `Page<T>` object is converted to `List<T>` before returning:
```java
return userMapper.convertToDtoList(allUsersInDb.getContent());
```

### 5.4 No Filtering or Sorting Parameters Documented

**Severity: Low | Effort: Small**

While Spring's `Pageable` supports `sort` and pagination query parameters, these are not documented in Swagger annotations.

### 5.5 OpenAPI Dependency Mismatch

**Severity: Medium | Effort: Small**

All services use `springdoc-openapi-starter-webflux-ui:2.1.0`, which is the **WebFlux** variant. However, all services except the API Gateway are **servlet-based** (Spring MVC). The correct dependency for servlet services is `springdoc-openapi-starter-webmvc-ui`.

### 5.6 No Standard Response Envelope

**Severity: Medium | Effort: Medium**

Successful responses wrap data directly in `ResponseEntity.ok(data)` while errors use `ErrorResponse`. There is no consistent envelope pattern (e.g., `{ data: T, errors: [], meta: {} }`).

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

Logging quality varies:
- Some controllers log request data: `log.info("Creating user with {}", request.toString())` — this may log sensitive data (passwords, account numbers).
- Core Banking `AccountController` has a typo: `"Reading utitlity account"`.
- Fund Transfer service concatenates instead of using SLF4J placeholders: `log.info("Sending fund transfer request {}" + request.toString())` — this defeats lazy evaluation.
- No structured logging (JSON format) is configured.

### 6.2 No Health Check Customization

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but rely on default health indicators. No custom health checks exist for:
- Database connectivity per service
- Keycloak reachability
- Core Banking service availability (for downstream services)

### 6.3 No Metrics Endpoints Configuration

**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` is included, the README mentions Prometheus but no Prometheus metrics configuration (`management.endpoints.web.exposure.include=prometheus`) or `micrometer-registry-prometheus` dependency is present in any `build.gradle`.

### 6.4 Distributed Tracing Partially Configured

**Severity: Medium | Effort: Small**

Tracing dependencies are present (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, `feign-micrometer`) but:
- No explicit `management.tracing.sampling.probability` configuration is visible (defaults to 0.1 / 10%).
- No custom span annotations on business methods.
- Zipkin URL configuration relies entirely on Config Server (not inspectable in repo).

### 6.5 Sensitive Data in Logs

**Severity: High | Effort: Small**

Request DTOs are logged with `toString()`, which will include:
- Passwords in user registration requests
- Account numbers in fund transfer requests
- Financial amounts

No log masking or sanitization is implemented.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service Feign calls are fire-and-forget with no circuit breaker pattern. If Core Banking becomes unavailable:
- Fund Transfer and Utility Payment services will block until Feign timeout (default: infinity in some configurations).
- No fallback behavior is defined.
- Thread pools will be exhausted under load.

`spring-cloud-starter-circuitbreaker-resilience4j` is not included in any `build.gradle`.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for:
- Feign clients (no `Retryer` bean or `spring-retry` dependency)
- Database operations
- Keycloak API calls

Transient failures (network blips, temporary DB lock contention) will result in immediate failure.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign clients (`connectTimeout`, `readTimeout`)
- Database connection pool
- Keycloak client operations

Default timeouts may be very long or infinite, leading to thread exhaustion under failure conditions.

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

When downstream services fail, there is no graceful degradation:
- Fund Transfer failure leaves local record in `PENDING` forever.
- Utility Payment failure leaves local record in `PROCESSING` forever.
- No background job to reconcile orphaned records.
- No compensation/rollback logic.

### 7.5 Non-Atomic Balance Operations

**Severity: Critical | Effort: Medium**

The `TransactionService.internalFundTransfer()` method performs multiple database writes within a single `@Transactional` boundary, but:
- No optimistic locking (`@Version`) on `BankAccountEntity` — concurrent transfers on the same account will cause lost updates.
- The method reads the balance as a DTO, then re-reads the entity to modify it — the balance could have changed between reads.
- If the process crashes between debiting the source and crediting the destination, the `@Transactional` annotation should roll back, but this relies on the transaction not having been committed in between.

### 7.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured at the API Gateway or service level. A malicious or buggy client could overwhelm the system with fund transfer requests.

### 7.7 wait-for-it.sh Is a Startup-Only Check

**Severity: Low | Effort: Small**

`wait-for-it.sh` only checks port availability at container startup. If a dependency goes down after startup, services have no mechanism to handle the outage gracefully.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | Catch-all handler returns 400 for all errors | Error Handling | Critical | Small |
| 3.1 | Near-zero test coverage outside Core Banking | Testing | Critical | Large |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small |
| 4.2 | No input validation on any DTO | Security | Critical | Medium |
| 7.1 | No circuit breakers on Feign calls | Resilience | Critical | Medium |
| 7.5 | Non-atomic balance operations (no optimistic locking) | Resilience | Critical | Medium |
| 1.2 | Duplicated code across services (no shared library) | Code Organization | High | Medium |
| 2.2 | Inconsistent error response format | Error Handling | High | Small |
| 2.3 | Missing HTTP status code differentiation | Error Handling | High | Small |
| 2.4 | No Feign error handling in Fund Transfer / Utility Payment | Error Handling | High | Medium |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | High | Large |
| 4.4 | Overly broad database permissions | Security | High | Small |
| 4.6 | Password returned in User DTO | Security | High | Small |
| 4.7 | No downstream service authentication | Security | High | Medium |
| 6.5 | Sensitive data in logs | Observability | High | Small |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior for failed downstream calls | Resilience | High | Medium |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 2.5 | Raw ResponseEntity without type parameters | Error Handling | Medium | Small |
| 3.4 | Context-load tests fail without infrastructure | Testing | Medium | Small |
| 4.3 | CSRF disabled without documentation | Security | Medium | Small |
| 4.5 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 5.1 | Non-RESTful URL patterns | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Medium | Medium |
| 5.3 | No pagination metadata in responses | API Design | Medium | Small |
| 5.5 | OpenAPI dependency mismatch (WebFlux in servlet apps) | API Design | Medium | Small |
| 5.6 | No standard response envelope | API Design | Medium | Medium |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.3 | No Prometheus metrics configuration | Observability | Medium | Small |
| 6.4 | Distributed tracing partially configured | Observability | Medium | Small |
| 7.6 | No rate limiting | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 5.4 | No filtering/sorting documentation | API Design | Low | Small |
| 6.2 | No health check customization | Observability | Low | Small |
| 7.7 | wait-for-it.sh startup-only check | Resilience | Low | Small |
