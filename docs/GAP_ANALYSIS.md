# Engineering Standards Gap Analysis

This document compares the codebase against established engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 Inconsistent Package Structure Across Services

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** The package structures differ across services in ways that add cognitive load:

- **Core Banking:** `controller/`, `service/`, `repository/`, `model/entity/`, `model/dto/`, `model/mapper/`, `exception/`
- **User Service:** `controller/`, `service/`, `service/rest/`, `model/entity/`, `model/dto/`, `model/mapper/`, `model/repository/`, `model/rest/response/`, `configuration/audit/`, `configuration/feign/`, `configuration/filter/`, `configuration/keycloak/`, `exception/`
- **Fund Transfer:** `controller/`, `service/`, `service/rest/client/`, `model/entity/`, `model/dto/`, `model/mapper/`, `model/repository/`, `configuration/`, `configuration/audit/`, `configuration/filter/`, `exception/`
- **Utility Payment:** `controller/`, `service/`, `service/rest/`, `model/entity/`, `model/dto/`, `model/mapper/`, `repository/` (note: `repository/` at root, not `model/repository/`), `configuration/`, `configuration/audit/`, `configuration/filter/`, `exception/`

**Specific Issues:**
- Repository package location varies: `repository/` (Core Banking, Utility Payment) vs. `model/repository/` (User Service, Fund Transfer)
- Feign client location varies: `service/rest/` (User, Utility Payment) vs. `service/rest/client/` (Fund Transfer)
- Configuration sub-packages are inconsistent

### 1.2 No Shared Library / Common Module

| Severity | Effort |
|----------|--------|
| High | Large |

**Finding:** There is no multi-module Gradle build or shared library. Each service independently defines:
- `AuditAware` base class (duplicated in User, Fund Transfer, and Utility Payment services)
- `BaseMapper<E, D>` interface (duplicated in all 4 business services)
- `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` (duplicated in User, Fund Transfer, and Utility Payment)
- `ErrorResponse` class (duplicated in all 4 business services with slight variations)
- `SimpleBankingGlobalException` (duplicated in all 4 services)
- `GlobalExceptionHandler` (duplicated in all 4 services)
- `TransactionStatus` enum (duplicated in Fund Transfer and Utility Payment)

This means a bug fix or behavior change must be applied to every copy independently.

### 1.3 Mapper Instantiation Anti-Pattern

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** Mappers are instantiated with `new` inside `@Service` classes instead of being Spring-managed beans:
```java
private UserMapper userMapper = new UserMapper();  // in multiple services
private FundTransferMapper mapper = new FundTransferMapper();
```
This bypasses Spring's DI, making testing harder and violating the consistent DI pattern used elsewhere.

### 1.4 No Gradle Root Build File

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Each service has its own independent `build.gradle` and Gradle wrapper. There is no root `settings.gradle` or `build.gradle` for unified builds. This means:
- No single command to build all services
- Dependency versions are duplicated across 6 `build.gradle` files
- No centralized dependency management or version catalogs

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** The `GlobalExceptionHandler` in every service has a catch-all `Exception.class` handler that returns a raw string instead of the structured `ErrorResponse`:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);  // Raw string, not ErrorResponse
}
```

This means:
- Clients receive two completely different response formats depending on the exception type
- Stack traces may leak to clients via `e.toString()`
- The raw string body includes full exception details, which is a security risk

### 2.2 All Errors Return HTTP 400 Bad Request

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** Every exception handler returns `ResponseEntity.badRequest()` (HTTP 400) regardless of the actual error:
- `EntityNotFoundException` -> 400 (should be 404)
- `InsufficientFundsException` -> 400 (should be 422 or 409)
- `UserAlreadyRegisteredException` -> 400 (should be 409)
- Generic `Exception` -> 400 (should be 500)

### 2.3 No Feign Error Handling in Fund Transfer Service

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** The Fund Transfer Service's `BankingCoreFeignClient` uses `CustomFeignClientConfiguration` but the Fund Transfer service has no `CustomFeignErrorDecoder` like the User Service does. If Core Banking returns an error, the raw Feign exception will propagate unhandled, potentially exposing internal service details.

### 2.4 Missing Error Codes in Some Services

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Only Core Banking and User Service define `GlobalErrorCode` constants. Fund Transfer and Utility Payment services have no error code system, making error categorization inconsistent.

### 2.5 ErrorResponse Class Inconsistency

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** The `ErrorResponse` class uses `@Builder` in Core Banking and Utility Payment but uses a constructor (`new ErrorResponse(code, message)`) in Fund Transfer. This inconsistency suggests the duplicated classes have diverged.

---

## 3. Testing

### 3.1 Unit Tests Only Exist in Core Banking Service

| Severity | Effort |
|----------|--------|
| Critical | Large |

**Finding:** Test coverage analysis:

| Service | Test Files | Tests | Coverage |
|---------|-----------|-------|----------|
| Core Banking | 4 (AccountServiceTest, TransactionServiceTest, UserServiceTest, ApplicationTests) | ~20 tests | Service layer only |
| User Service | 1 (ApplicationTests - context load only) | 0 real tests | None |
| Fund Transfer | 1 (ApplicationTests - context load only) | 0 real tests | None |
| Utility Payment | 1 (ApplicationTests - context load only) | 0 real tests | None |
| API Gateway | 1 (ApplicationTests - context load only) | 0 real tests | None |
| Service Registry | 1 (ApplicationTests - context load only) | 0 real tests | None |
| Config Server | 1 (ApplicationTests - context load only) | 0 real tests | None |

The only meaningful tests are in Core Banking Service. All other services have only the auto-generated Spring Boot application context test.

### 3.2 No Integration Tests

| Severity | Effort |
|----------|--------|
| Critical | Large |

**Finding:** There are no integration tests that verify:
- Feign client communication between services
- Database schema correctness (only Core Banking uses Flyway; others rely on Hibernate auto-DDL)
- API Gateway routing rules
- Keycloak authentication flows
- End-to-end transaction flows

### 3.3 No Contract Tests

| Severity | Effort |
|----------|--------|
| High | Large |

**Finding:** No Spring Cloud Contract or Pact tests exist. The Feign client interfaces define contracts between services, but there is no automated verification that:
- Core Banking's API matches what Fund Transfer and Utility Payment expect
- DTO classes are compatible across service boundaries (they are currently duplicated independently)

### 3.4 No Test Containers or Embedded Database Config for Non-Core Services

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** While Core Banking includes H2 as a test dependency and has a test `application.yml`, the other services include H2 but have no test configurations that would enable isolated testing.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

### 4.2 No Input Validation

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** None of the request DTOs have any validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Valid`, etc.):

```java
// FundTransferRequest - no validation
private String fromAccount;   // could be null
private String toAccount;     // could be null
private BigDecimal amount;    // could be null or negative

// UtilityPaymentRequest - no validation
private Long providerId;      // could be null
private BigDecimal amount;    // could be null or negative
```

No `@Valid` annotation is used on any controller method parameter.

### 4.3 No Authorization Beyond Gateway

| Severity | Effort |
|----------|--------|
| High | Large |

**Finding:** The API Gateway performs JWT validation, but downstream services have no security at all:
- No Spring Security configuration in any business service
- Services are directly accessible on their respective ports, bypassing the gateway entirely
- The `X-Auth-Id` header is trusted without verification - any client can set this header when calling services directly
- No role-based access control (RBAC) - any authenticated user can perform any operation

### 4.4 CSRF Disabled at Gateway

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** CSRF is explicitly disabled: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. This is acceptable for a stateless REST API using JWT tokens, but should be documented as an intentional decision.

### 4.5 Keycloak Singleton Anti-Pattern

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** `KeycloakProperties` uses a non-thread-safe lazy singleton pattern:
```java
private static Keycloak keycloakInstance = null;
if (keycloakInstance == null) {
    keycloakInstance = KeycloakBuilder.builder()...build();
}
```
This has a race condition in concurrent initialization and the static field prevents proper testability.

### 4.6 Password Included in User DTO Response

| Severity | Effort |
|----------|--------|
| High | Small |

**Finding:** The `User` DTO in User Service includes a `password` field with no `@JsonIgnore` or write-only annotation. The same class is used for both request and response, meaning the password could be returned in API responses.

---

## 5. API Design

### 5.1 Raw ResponseEntity Without Type Parameters

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Most controller methods return untyped `ResponseEntity` instead of `ResponseEntity<T>`:
```java
public ResponseEntity getBankAccount(...)  // No type parameter
public ResponseEntity fundTransfer(...)    // No type parameter
```
Only the User Service's `UserController` properly types its responses (`ResponseEntity<User>`, `ResponseEntity<List<User>>`). This prevents proper OpenAPI documentation generation and loses compile-time type safety.

### 5.2 No API Versioning Strategy

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** While endpoints use `/api/v1/` prefix, there is no documented versioning strategy and no infrastructure to support multiple API versions simultaneously.

### 5.3 Inconsistent Endpoint Naming

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:**
- Core Banking uses snake_case in path variables: `{account_number}`, `{account_name}`
- User Service uses camelCase in path variables: `{id}`
- Fund Transfer uses mixed: POST to root `/api/v1/transfer`
- Update endpoint uses non-RESTful naming: `PATCH /update/{id}` (the `/update` segment is redundant)

### 5.4 No Pagination Metadata in Responses

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. This means clients get the page content but lose:
- Total element count
- Total page count
- Current page number
- Page size

### 5.5 OpenAPI Documentation Partially Configured

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** The `springdoc-openapi-starter-webflux-ui` dependency is included in business services, but these services use Spring MVC (not WebFlux). The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. The API Gateway (which does use WebFlux) does not include this dependency at all. Swagger annotations (`@Tag`, `@Operation`) are present but the wrong starter may cause issues.

### 5.6 No Filtering or Search Capabilities

| Severity | Effort |
|----------|--------|
| Low | Medium |

**Finding:** List endpoints only support pagination via Spring's `Pageable`. There are no query parameters for filtering, sorting by custom fields, or searching.

---

## 6. Observability

### 6.1 Logging Inconsistency

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:**
- `@Slf4j` is used inconsistently: present in some controllers (User Service) but missing in others (Utility Payment controller)
- Log levels are all `INFO` - no structured use of `DEBUG`, `WARN`, `ERROR`
- Request/response bodies are logged at `INFO` level via `toString()`, which may expose sensitive data (passwords, account numbers)
- No correlation ID logging beyond Zipkin trace IDs
- `KeycloakUserService` imports `LoggerFactory` manually alongside `@Slf4j`

### 6.2 No Custom Health Check Endpoints

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** While `spring-boot-starter-actuator` is included in all services, there are no custom health indicators that check:
- Database connectivity from each service
- Keycloak availability (User Service)
- Core Banking Service availability (from dependent services)
- RabbitMQ connectivity (when implemented)

Default actuator endpoints are available but not customized.

### 6.3 No Metrics Endpoints Beyond Defaults

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** Despite including Micrometer and Actuator:
- No custom metrics (e.g., transfer count, payment amounts, error rates)
- No Prometheus endpoint configured (though Prometheus is mentioned in the tech stack)
- No dashboards or alerting configuration

### 6.4 Distributed Tracing Gaps

| Severity | Effort |
|----------|--------|
| Low | Small |

**Finding:** Tracing dependencies are included and should work for HTTP calls. However:
- The Config Server does not include tracing dependencies
- No sampling rate configuration is visible (defaults to 100%, which is expensive in production)
- No trace ID propagation to error responses for client-side debugging

### 6.5 No Structured Logging Format

| Severity | Effort |
|----------|--------|
| Medium | Small |

**Finding:** Log output uses default Spring Boot formatting. For production deployments, structured JSON logging (e.g., via Logback JSON encoder) would be needed for log aggregation systems.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| Critical | Medium |

**Finding:** There are no circuit breaker patterns implemented anywhere. If Core Banking Service goes down:
- Fund Transfer Service will keep sending requests that fail
- Utility Payment Service will keep sending requests that fail
- User Service registration will fail on every attempt
- No degradation path or fallback behavior exists

Spring Cloud Circuit Breaker (Resilience4j) is not in any `build.gradle`.

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** No retry configuration exists for:
- Feign client calls (transient network failures will fail immediately)
- Database connections
- Keycloak Admin API calls
- Config Server bootstrap fetches

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| High | Medium |

**Finding:** No explicit timeout configuration for:
- Feign client calls (uses defaults, which may be too long or too short)
- Database connection pools
- Keycloak client connections
- Gateway route timeouts

A slow Core Banking response would block the calling service's thread indefinitely with default settings.

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| Medium | Large |

**Finding:** No fallback mechanisms exist:
- No cached responses for read operations when services are down
- No queue-based fallback for write operations (the planned RabbitMQ integration is not implemented)
- No graceful degradation - any downstream failure causes a full error response

### 7.5 Non-Atomic Fund Transfer Operations

| Severity | Effort |
|----------|--------|
| Critical | Large |

**Finding:** The fund transfer flow spans two services with no distributed transaction mechanism:

1. Fund Transfer Service saves entity with status `PENDING`
2. Fund Transfer Service calls Core Banking via Feign
3. Core Banking debits source and credits destination in a single `@Transactional` scope
4. Fund Transfer Service updates status to `SUCCESS`

If step 4 fails (e.g., Fund Transfer Service crashes after Core Banking succeeds), the transfer is completed in Core Banking but Fund Transfer Service shows `PENDING`. There is:
- No saga pattern implementation
- No compensation/rollback mechanism
- No idempotency keys to prevent duplicate processing on retry
- No transaction outbox pattern

Similarly, the Utility Payment Service has the same problem.

### 7.6 No Rate Limiting

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** The API Gateway has no rate limiting configured. A single client could overwhelm the system with requests.

### 7.7 No Bulkhead / Thread Pool Isolation

| Severity | Effort |
|----------|--------|
| Medium | Medium |

**Finding:** All Feign calls share the same thread pool. A slow response from Core Banking for fund transfers could exhaust threads and block utility payments.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | Inconsistent package structure | Code Organization | Medium | Medium |
| 1.2 | No shared library / common module | Code Organization | High | Large |
| 1.3 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 1.4 | No Gradle root build file | Code Organization | Medium | Small |
| 2.1 | Inconsistent error response format | Error Handling | High | Medium |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Medium |
| 2.3 | No Feign error handling in Fund Transfer | Error Handling | High | Medium |
| 2.4 | Missing error codes in some services | Error Handling | Medium | Small |
| 2.5 | ErrorResponse class inconsistency | Error Handling | Low | Small |
| 3.1 | Unit tests only in Core Banking | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | Critical | Large |
| 3.3 | No contract tests | Testing | High | Large |
| 3.4 | No test containers setup | Testing | Medium | Medium |
| 4.1 | Hardcoded credentials | Security | Critical | Medium |
| 4.2 | No input validation | Security | Critical | Medium |
| 4.3 | No authorization beyond gateway | Security | High | Large |
| 4.4 | CSRF disabled without documentation | Security | Low | Small |
| 4.5 | Keycloak singleton anti-pattern | Security | Medium | Small |
| 4.6 | Password in User DTO response | Security | High | Small |
| 5.1 | Raw ResponseEntity without types | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Medium | Medium |
| 5.3 | Inconsistent endpoint naming | API Design | Low | Small |
| 5.4 | No pagination metadata | API Design | Medium | Small |
| 5.5 | Wrong OpenAPI starter dependency | API Design | Medium | Small |
| 5.6 | No filtering / search | API Design | Low | Medium |
| 6.1 | Logging inconsistency | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.3 | No custom metrics | Observability | Medium | Medium |
| 6.4 | Distributed tracing gaps | Observability | Low | Small |
| 6.5 | No structured logging | Observability | Medium | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Medium |
| 7.3 | No timeout configuration | Resilience | High | Medium |
| 7.4 | No fallback behavior | Resilience | Medium | Large |
| 7.5 | Non-atomic fund transfer operations | Resilience | Critical | Large |
| 7.6 | No rate limiting | Resilience | Medium | Medium |
| 7.7 | No bulkhead / thread pool isolation | Resilience | Medium | Medium |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 10 |
| Medium | 16 |
| Low | 5 |
| **Total** | **37** |
