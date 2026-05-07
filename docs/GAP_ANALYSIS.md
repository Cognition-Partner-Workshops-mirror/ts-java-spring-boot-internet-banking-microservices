# Engineering Standards Gap Analysis

## Internet Banking Microservices - Java 21 / Spring Boot 3.2.4

---

## Assessment Methodology

Each gap is rated on:
- **Severity:** Critical / High / Medium / Low
- **Effort:** Small (< 1 day) / Medium (1-3 days) / Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:** Each microservice has its own independent `build.gradle` with no root `settings.gradle` or parent build file. This means:
- Dependency versions are duplicated across all 6 `build.gradle` files (e.g., Spring Boot 3.2.4, Spring Cloud 2023.0.0)
- No centralized dependency management or version catalog
- Plugin versions repeated in every service

**Evidence:** All 6 `build.gradle` files independently declare `id 'org.springframework.boot' version '3.2.4'` and `set('springCloudVersion', "2023.0.0")`.

### 1.2 Duplicated Code Across Services

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding:** Significant code duplication exists with no shared library:
- `BaseMapper` interface copied into 3 services (core-banking, user, fund-transfer, utility-payment)
- `AuditAware` base class copied into 3 services
- `ErrorResponse` class copied into 3 services with slightly different implementations (some use `@Builder`, others use constructors)
- `SimpleBankingGlobalException` copied into 3 services
- `GlobalExceptionHandler` copied into 3 services with inconsistent implementations
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` copied into fund-transfer and user services
- `AuditConfig` / `AuditorAwareConfig` copied into 3 services
- Request/Response DTOs duplicated across services (e.g., `FundTransferRequest`, `AccountResponse`)

### 1.3 Inconsistent Package Structure

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Package structure varies between services:
- Core Banking: `model.dto.request`, `model.dto.response`, `model.mapper`, `repository`
- User Service: `model.rest.response`, `model.repository`, `model.mapper`
- Fund Transfer: `model.dto.request`, `model.dto.response`, `model.repository`, `service.rest.client`
- Utility Payment: `model.rest.request`, `model.rest.response`, `repository` (no `model.` prefix)

### 1.4 Mapper Instantiation Anti-Pattern

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Mappers are instantiated inline rather than injected:
```java
private UserMapper userMapper = new UserMapper();  // in multiple services
```
This bypasses Spring's dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Inconsistent GlobalExceptionHandler Implementations

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** Each service has its own `GlobalExceptionHandler` with different behavior:
- **Core Banking & User Service:** Use `ErrorResponse.builder()` pattern
- **Fund Transfer:** Uses `new ErrorResponse(code, message)` constructor
- **Utility Payment:** Has a `GlobalExceptionHandler` but with different error types
- **All services:** The catch-all `Exception` handler returns a plain string: `"Exception occur inside API " + e` instead of a structured `ErrorResponse`

### 2.2 All Errors Return HTTP 400 Bad Request

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** Every exception handler returns `ResponseEntity.badRequest()` (HTTP 400) regardless of the actual error type:
- `EntityNotFoundException` -> 400 (should be 404)
- `InsufficientFundsException` -> 400 (should be 422)
- `UserAlreadyRegisteredException` -> 400 (could be 409)
- Generic `Exception` -> 400 (should be 500)

### 2.3 Exception Leaks Internal Details

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding:** The generic exception handler concatenates the full exception object into the response:
```java
.body("Exception occur inside API " + e);
```
This exposes stack traces, class names, and internal implementation details to API consumers.

### 2.4 No Feign Error Handling for Fund Transfer

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** The Fund Transfer Service calls Core Banking via Feign but has no error handling if the Feign call fails. A failure after the local `PENDING` record is saved will leave the record in `PENDING` status permanently. The `CustomFeignErrorDecoder` exists in User Service but not in Fund Transfer or Utility Payment services.

---

## 3. Testing

### 3.1 Unit Tests Only in Core Banking Service

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Large |

**Finding:** Only `core-banking-service` has meaningful unit tests:
- `AccountServiceTest` (6 tests)
- `TransactionServiceTest` (10 tests)
- `UserServiceTest` (3 tests)

The other 5 services have only the auto-generated Spring Boot context loader test (`*ApplicationTests.java`), which are effectively no-ops when infrastructure isn't available.

### 3.2 No Integration Tests

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding:** No integration tests exist that verify:
- REST controller endpoint behavior (MockMvc / WebTestClient)
- JPA repository queries against a real DB (e.g., Testcontainers)
- Spring context loading with proper configuration
- Feign client contract validation

### 3.3 No Contract Tests Between Services

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding:** Services communicate via OpenFeign with hardcoded endpoint paths. There are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that:
- Fund Transfer Service's `BankingCoreFeignClient` matches Core Banking's actual API
- User Service's `BankingCoreRestClient` matches Core Banking's actual API
- Utility Payment Service's `BankingCoreRestClient` matches Core Banking's actual API

### 3.4 No Test Configuration for Non-Core Services

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Only `core-banking-service` has a `src/test/resources/application.yml` with H2 configuration. Other services lack test configuration entirely, making it impossible to run tests without the full infrastructure.

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose and Source

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Small |

**Finding:** Multiple credentials are hardcoded:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `Dockerfile`)
- MySQL app user password: `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin password: `password` (in `docker-compose.yml`)
- Keycloak DB password: `password` (in `docker-compose.yml`)
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.2 CSRF Disabled Globally

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** The API Gateway disables CSRF protection entirely:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While acceptable for a pure REST API with JWT, this should be documented as a deliberate decision.

### 4.3 No Input Validation

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` / `@Max` annotations exist on any request DTOs or controller method parameters:
- `FundTransferRequest`: No validation on `fromAccount`, `toAccount`, `amount` (could be null or negative)
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `referenceNumber`, `account`
- `User` registration: No validation on `email`, `password`, `identification`

The `spring-boot-starter-validation` dependency is not included in any `build.gradle`.

### 4.4 Keycloak Singleton Not Thread-Safe

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** `KeycloakProperties.getInstance()` uses a non-thread-safe lazy singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This can result in multiple Keycloak instances being created under concurrent access.

### 4.5 No Authorization Beyond Authentication

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** The gateway enforces JWT authentication (any valid token grants access), but there is no role-based or permission-based authorization. Any authenticated user can:
- Transfer funds from any account
- Make utility payments from any account
- View and update any user
- Access admin-level endpoints (user approval)

### 4.6 Sensitive Data in Logs

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Controllers log request objects using `toString()`:
```java
log.info("Creating user with {}", request.toString()); // may contain password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```
The `User` object logged during registration contains the user's password.

---

## 5. API Design

### 5.1 Raw ResponseEntity Without Generics

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** Most controller methods return raw `ResponseEntity` without type parameters:
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)    // should be ResponseEntity<FundTransferResponse>
```
This prevents accurate OpenAPI documentation generation and removes compile-time type safety.

### 5.2 Non-RESTful URL Patterns

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Some endpoints don't follow REST conventions:
- `POST /api/v1/bank-users/register` (action verb in URL; should be `POST /api/v1/bank-users`)
- `PATCH /api/v1/bank-users/update/{id}` (action verb in URL; should be `PATCH /api/v1/bank-users/{id}`)
- `POST /api/v1/transaction/fund-transfer` (action verb; acceptable for command-style APIs)

### 5.3 No API Versioning Strategy

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Medium |

**Finding:** While URLs include `/v1/`, there is no documented versioning strategy, no mechanism for supporting multiple versions, and no deprecation policy.

### 5.4 No Pagination Metadata in Responses

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper that includes pagination metadata (total count, page number, page size, total pages):
```java
public List<User> readUsers(Pageable pageable) {
    return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
    // .getContent() discards pagination metadata
}
```

### 5.5 Swagger/OpenAPI Partially Configured

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** `springdoc-openapi-starter-webflux-ui` is included in business services but:
- Uses the WebFlux variant for WebMVC services (incorrect starter)
- No global API info (title, version, description) configured
- Controller/operation annotations added but response schemas are missing due to raw `ResponseEntity`

---

## 6. Observability

### 6.1 No Health Check Customization

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** `spring-boot-starter-actuator` is included but no custom health indicators exist for:
- Database connectivity health
- Keycloak availability
- Downstream service availability (Core Banking)
- RabbitMQ connectivity (when implemented)

Default actuator endpoints are exposed but not customized or secured beyond the gateway.

### 6.2 Inconsistent Logging

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding:**
- No structured logging format (JSON) configured
- Log levels are not consistently applied (`@Slf4j` used but some services log more verbosely than others)
- Feign logging set to `FULL` in production configuration (should be `BASIC` or `NONE`)
- No correlation ID propagation in log messages (relies solely on Zipkin trace IDs in MDC)
- No logback/log4j2 configuration files exist in any service

### 6.3 No Metrics Endpoints

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding:** While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, there is no:
- Prometheus metrics endpoint configuration
- Custom business metrics (transfer counts, payment volumes, error rates)
- Micrometer registry configuration beyond tracing

README mentions Prometheus but it's not configured in any service or Docker Compose.

### 6.4 Distributed Tracing Coverage Gaps

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding:** Tracing dependencies are included but:
- No sampling rate configuration (defaults to 10% in production)
- No custom span annotations for business operations
- Keycloak REST calls (from User Service) may not be automatically traced

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding:** No circuit breaker pattern is implemented. If Core Banking Service goes down:
- Fund Transfer Service will fail on every request (Feign throws exceptions)
- Utility Payment Service will fail on every request
- User Service registration will fail
- No fallback behavior defined anywhere
- No `resilience4j` or `spring-cloud-circuitbreaker` dependency in any `build.gradle`

### 7.2 No Retry Policies

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No retry configuration exists for Feign clients or any HTTP calls. Transient network failures will immediately fail the entire operation. No `spring-retry` dependency is included.

### 7.3 No Timeout Configuration

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding:** No explicit timeout configuration for:
- Feign client connection and read timeouts (defaults to infinite in some versions)
- Database connection pool timeouts
- Keycloak admin client timeouts
- API Gateway route timeouts

### 7.4 No Fallback Behavior

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding:** No fallback mechanisms exist:
- Fund Transfer: If Core Banking fails mid-transfer, the local record stays `PENDING` forever
- Utility Payment: Same issue; `PROCESSING` status never updated on failure
- User Service: Keycloak failure during registration leaves no user record (acceptable) but the error message is generic

### 7.5 Transaction Integrity Issues

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Large |

**Finding:** The distributed transaction pattern has data consistency issues:
1. **Fund Transfer Service:** Saves `PENDING` locally, then calls Core Banking. If Core Banking succeeds but the subsequent status update fails, the transfer was executed but shows as `PENDING`.
2. **Balance Calculation Bug:** In `TransactionService.internalFundTransfer()`, `availableBalance` is set to `actualBalance - amount` (line 91) instead of `availableBalance - amount`, causing a double-deduction of the available balance.
3. **Utility Payment Bug:** Same double-deduction bug at lines 63-64 of `TransactionService.utilPayment()`.
4. **No Saga Pattern or Compensating Transactions:** If a distributed operation partially fails, there's no rollback mechanism.

---

## Summary Table

| Category | Gap | Severity | Effort |
|----------|-----|----------|--------|
| **Code Organization** | No multi-project Gradle build | Medium | Medium |
| **Code Organization** | Duplicated code across services | High | Large |
| **Code Organization** | Inconsistent package structure | Low | Small |
| **Code Organization** | Mapper instantiation anti-pattern | Low | Small |
| **Error Handling** | Inconsistent GlobalExceptionHandler | High | Medium |
| **Error Handling** | All errors return HTTP 400 | Critical | Medium |
| **Error Handling** | Exception leaks internal details | High | Small |
| **Error Handling** | No Feign error handling | High | Medium |
| **Testing** | Tests only in core-banking-service | Critical | Large |
| **Testing** | No integration tests | High | Large |
| **Testing** | No contract tests | High | Large |
| **Testing** | No test config for non-core services | Medium | Small |
| **Security** | Hardcoded credentials | Critical | Small |
| **Security** | CSRF disabled globally | Medium | Small |
| **Security** | No input validation | Critical | Medium |
| **Security** | Keycloak singleton not thread-safe | Medium | Small |
| **Security** | No authorization beyond authentication | High | Medium |
| **Security** | Sensitive data in logs | Medium | Small |
| **API Design** | Raw ResponseEntity without generics | Medium | Small |
| **API Design** | Non-RESTful URL patterns | Low | Small |
| **API Design** | No API versioning strategy | Low | Medium |
| **API Design** | No pagination metadata | Medium | Small |
| **API Design** | Swagger/OpenAPI partially configured | Medium | Small |
| **Observability** | No health check customization | Medium | Small |
| **Observability** | Inconsistent logging | Medium | Medium |
| **Observability** | No metrics endpoints | Medium | Small |
| **Observability** | Distributed tracing gaps | Low | Small |
| **Resilience** | No circuit breakers | Critical | Medium |
| **Resilience** | No retry policies | High | Small |
| **Resilience** | No timeout configuration | High | Small |
| **Resilience** | No fallback behavior | High | Medium |
| **Resilience** | Transaction integrity issues | Critical | Large |

### Severity Distribution
- **Critical:** 6 gaps
- **High:** 11 gaps
- **Medium:** 11 gaps
- **Low:** 4 gaps
