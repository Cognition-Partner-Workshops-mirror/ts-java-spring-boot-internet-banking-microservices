# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps with severity ratings and estimated remediation effort.

**Severity Scale:**
- **Critical** — Security vulnerability or data-loss risk in production
- **High** — Significant reliability, maintainability, or correctness issue
- **Medium** — Suboptimal practice that impacts developer velocity or operational maturity
- **Low** — Polish items and minor inconsistencies

**Effort Scale:**
- **Small** — Less than 1 day of work
- **Medium** — 1-3 days of work
- **Large** — 3+ days of work

---

## 1. Code Organization

### GAP-ORG-001: No Shared Library / Multi-Module Build

**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with no root `settings.gradle`. Code is duplicated extensively across services:

- `BaseMapper` is copy-pasted into 4 services (core-banking, user, fund-transfer, utility-payment)
- `AuditAware` is copy-pasted into 3 services (user, fund-transfer, utility-payment)
- `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` are duplicated in 4 services
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` are duplicated in 3 services
- `CustomFeignClientConfiguration`, `CustomFeignErrorDecoder` are duplicated in fund-transfer and utility-payment

**Impact:** Bug fixes or enhancements must be applied to every copy independently, leading to drift and inconsistency.

### GAP-ORG-002: Inconsistent Package Structure Across Services

**Severity: Low | Effort: Small**

The package layout varies between services:

| Concern | Core Banking | User Service | Fund Transfer | Utility Payment |
|---------|-------------|-------------|---------------|-----------------|
| Entities | `model.entity` | `model.entity` | `model.entity` | `model.entity` |
| DTOs | `model.dto` | `model.dto` | `model.dto` | `model.dto` |
| Repositories | `repository` | `model.repository` | `model.repository` | `repository` |
| Request/Response | `model.dto.request/response` | `model.rest.response` | `model.dto.request/response` | `model.rest.request/response` |
| Feign clients | N/A | `service.rest` | `service.rest.client` | `service.rest` |

### GAP-ORG-003: Inconsistent Code Formatting

**Severity: Low | Effort: Small**

Mixed use of tabs and spaces across `build.gradle` files (e.g., core-banking uses 4-space indent while utility-payment uses tabs). No code formatter (Spotless, Checkstyle, etc.) is configured.

### GAP-ORG-004: Build Artifacts Committed to Repository

**Severity: Medium | Effort: Small**

The `build/` directories containing compiled output and generated resources are committed to Git for all services. The `.gitignore` does not exclude them properly. This bloats the repository and causes unnecessary merge conflicts.

---

## 2. Error Handling

### GAP-ERR-001: All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Medium**

Every `GlobalExceptionHandler` across all services maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). Correct HTTP semantics require:

- `EntityNotFoundException` → **404 Not Found**
- `InsufficientFundsException` → **422 Unprocessable Entity** or **409 Conflict**
- `UserAlreadyRegisteredException` → **409 Conflict**
- `InvalidEmailException` → **422 Unprocessable Entity**
- Unexpected `Exception` → **500 Internal Server Error** (currently returns 400)

### GAP-ERR-002: Generic Exception Handler Leaks Internal Details

**Severity: Critical | Effort: Small**

The catch-all exception handler in all services returns raw exception details to the client:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

This exposes stack traces, class names, SQL errors, and potentially sensitive internal state to API consumers. In a banking application, this is a security risk.

### GAP-ERR-003: Inconsistent Error Response Structure

**Severity: Medium | Effort: Small**

- Structured `ErrorResponse` (code + message) is returned for `SimpleBankingGlobalException`
- A raw string is returned for all other exceptions
- There is no standard error envelope (e.g., RFC 7807 Problem Details)

### GAP-ERR-004: No Feign Error Handling for Core Banking Failures

**Severity: High | Effort: Medium**

When the Core Banking Service returns an error, the Feign client in Fund Transfer and Utility Payment services has a `CustomFeignErrorDecoder` but:

- The user-service Feign client (`BankingCoreRestClient`) does **not** have a custom error decoder configuration
- Error responses from the core service are not translated into meaningful domain exceptions for the caller

### GAP-ERR-005: Missing Raw Type Parameterization on ResponseEntity

**Severity: Medium | Effort: Small**

Almost all controller methods return `ResponseEntity` without a type parameter (raw type). This should be `ResponseEntity<?>` or a specific type like `ResponseEntity<BankAccount>`. User Service's `UserController` is the only controller that properly parameterizes `ResponseEntity<User>`.

---

## 3. Testing

### GAP-TEST-001: Near-Zero Test Coverage on Business Services

**Severity: High | Effort: Large**

| Service | Tests | Coverage |
|---------|-------|----------|
| Core Banking | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Unit tests for service layer (good) |
| User Service | `contextLoads()` only | **No business logic tests** |
| Fund Transfer Service | `contextLoads()` only | **No business logic tests** |
| Utility Payment Service | `contextLoads()` only | **No business logic tests** |
| API Gateway | `contextLoads()` only | **No tests** |
| Config Server | `contextLoads()` only | **No tests** |
| Service Registry | `contextLoads()` only | **No tests** |

The `contextLoads()` tests in most services will fail without a running config server, Eureka, and database — they are not self-contained.

### GAP-TEST-002: No Integration Tests

**Severity: High | Effort: Large**

There are no `@SpringBootTest` tests that spin up the web layer with `@WebMvcTest` or test against a real H2 database. Only the User Service has an `application.yml` in `src/test/resources` for H2 configuration.

### GAP-TEST-003: No Contract Tests Between Services

**Severity: Medium | Effort: Large**

With 3 services calling Core Banking via Feign, there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to verify API compatibility. A breaking change in Core Banking would silently break downstream services.

### GAP-TEST-004: No Test Configuration for Most Services

**Severity: Medium | Effort: Small**

Only the User Service has a `src/test/resources/application.yml`. Other services with databases (Fund Transfer, Utility Payment) lack test-specific configuration, meaning tests would attempt to connect to a real MySQL instance.

---

## 4. Security

### GAP-SEC-001: Hardcoded Credentials in Docker Compose and Source Code

**Severity: Critical | Effort: Small**

Credentials are hardcoded in plain text across multiple files:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin`/`password` |
| `docker-compose.yml` | Keycloak DB: `keycloak`/`password` |
| `mysql/privileges.sql` | DB user: `javatodev_development`/`oPItyPticIAt` |
| `mysql/Dockerfile` | MySQL root password in ENV |
| `README.md` | Test credentials: `ib_admin@javatodev.com`/`5V7huE3G86uB` |
| `user-service test application.yml` | Keycloak client-secret: `e8548d56-d743-45ef-8655-063c9cd96759` |

### GAP-SEC-002: No Input Validation on Request Bodies

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, or any Jakarta Bean Validation annotations are used on any request DTO or controller parameter across any service:

- `FundTransferRequest`: `amount` could be null, negative, or zero
- `UtilityPaymentRequest`: `providerId` could be null, `amount` could be negative
- `User` registration: `email` is not validated for format, `password` has no length/complexity requirements
- `UserUpdateRequest`: `status` could be null

### GAP-SEC-003: Overly Permissive Database User

**Severity: High | Effort: Small**

The `javatodev_development` MySQL user is granted `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` — global privileges across all databases. Application services should have narrowly scoped privileges on their own schema only.

### GAP-SEC-004: CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is explicitly disabled in the API Gateway's `SecurityConfiguration`. For a REST API this is acceptable, but it should be documented with a comment explaining the rationale.

### GAP-SEC-005: Keycloak Singleton is Not Thread-Safe

**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check-less singleton pattern that is not thread-safe:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

In a multi-threaded Spring Boot application, this can create multiple Keycloak instances or return a partially constructed instance.

### GAP-SEC-006: No Rate Limiting or Throttling

**Severity: Medium | Effort: Medium**

The API Gateway and downstream services have no rate limiting. The user registration endpoint is publicly accessible (`permitAll()`) and could be abused for account enumeration or denial-of-service attacks.

### GAP-SEC-007: Dependency Vulnerability Risk — No Security Scanning

**Severity: Medium | Effort: Small**

No dependency vulnerability scanning is configured (e.g., OWASP Dependency Check, Snyk, or Dependabot). With 30+ transitive dependencies per service, unpatched CVEs may exist.

---

## 5. API Design

### GAP-API-001: No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While endpoints use `/api/v1/`, there is no documented versioning strategy, no mechanism for supporting multiple versions, and no plan for deprecation. The API Gateway routes do not include version awareness.

### GAP-API-002: Inconsistent Endpoint Naming Conventions

**Severity: Low | Effort: Small**

| Service | Pattern | Example |
|---------|---------|---------|
| Core Banking | `snake_case` path variables | `/bank-account/{account_number}` |
| User Service | Verb-based paths | `/bank-users/register`, `/bank-users/update/{id}` |
| Fund Transfer | RESTful resource | `/transfer` (POST/GET) |
| Utility Payment | RESTful resource | `/utility-payment` (POST/GET) |

Best practice: Use consistent kebab-case nouns without verbs in paths.

### GAP-API-003: No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Endpoints that return paginated data (`readUsers`, `readFundTransfers`, `readPayments`) accept Spring `Pageable` parameters but return a raw `List<T>`. The response does not include total count, page number, page size, or navigation links. Clients cannot know if more pages exist.

### GAP-API-004: No OpenAPI / Swagger Configuration Beyond Defaults

**Severity: Low | Effort: Small**

The `springdoc-openapi-starter-webflux-ui` dependency is included in 4 services, but:
- No `@OpenAPIDefinition` or custom OpenAPI configuration
- The `webflux-ui` variant is used in non-reactive (servlet) services — should use `springdoc-openapi-starter-webmvc-ui`
- Swagger annotations (`@Tag`, `@Operation`) are present but `@ApiResponse` and `@Schema` are missing

### GAP-API-005: No Standardized Error Response Format

**Severity: Medium | Effort: Small**

See GAP-ERR-003. The API does not use a standard error format like RFC 7807 Problem Details for HTTP APIs.

### GAP-API-006: Missing HATEOAS or Resource Links

**Severity: Low | Effort: Medium**

Responses are flat DTOs with no hypermedia links. For a banking API, linking related resources (e.g., user → accounts, account → transactions) would improve API discoverability.

---

## 6. Observability

### GAP-OBS-001: No Custom Health Check Indicators

**Severity: Medium | Effort: Small**

Spring Boot Actuator is included in all services, providing `/actuator/health`. However, no custom health indicators are defined for critical dependencies:

- MySQL connectivity health
- Keycloak connectivity health
- Feign client downstream service health
- Config Server availability

### GAP-OBS-002: Logging Lacks Structured Format

**Severity: Medium | Effort: Small**

Logging uses Spring Boot defaults (Logback with pattern layout). For production observability:
- No JSON-structured logging for log aggregation tools
- No MDC enrichment with user ID, transaction ID, or correlation ID
- Trace IDs from Micrometer/Brave are propagated but not guaranteed to appear in log output without explicit configuration

### GAP-OBS-003: No Metrics Endpoints or Custom Metrics

**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` is present, there are no:
- Custom business metrics (e.g., transfer count, transfer amount, failed transactions)
- Prometheus scrape endpoint configuration (`/actuator/prometheus`)
- Micrometer `@Timed` or `@Counted` annotations on service methods

The README mentions Prometheus in the technology stack, but no Prometheus-related dependencies are in any `build.gradle`.

### GAP-OBS-004: Zipkin Tracing Configuration is Externalized Without Visibility

**Severity: Low | Effort: Small**

Tracing configuration (sampling rate, Zipkin endpoint) is managed through the external Git config repository. There is no local fallback or documentation of what sampling rate is used.

### GAP-OBS-005: Logging of Sensitive Data

**Severity: High | Effort: Small**

Several controllers log full request objects using `toString()`:

```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Creating user with {}", request.toString());
```

These log statements may include account numbers, amounts, passwords (User registration `request` includes `password`), and other PII/financial data in plain text.

---

## 7. Resilience

### GAP-RES-001: No Circuit Breakers on Feign Clients

**Severity: High | Effort: Medium**

All inter-service communication uses synchronous Feign calls without circuit breakers (e.g., Resilience4j `@CircuitBreaker`). If the Core Banking Service goes down:
- Fund Transfer Service will hang until Feign timeout (default: infinite)
- Utility Payment Service will hang similarly
- User Service calls to Core Banking will cascade the failure

### GAP-RES-002: No Retry Policies on Feign Clients

**Severity: Medium | Effort: Small**

No `@Retry` or Feign `Retryer` is configured. Transient network failures (e.g., DNS blips, brief restarts) will immediately fail the request instead of retrying.

### GAP-RES-003: No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration is visible for:
- Feign client connect/read timeouts
- Database connection pool timeouts
- Keycloak admin client timeouts

Default Feign timeouts may be infinite, meaning a slow Core Banking Service could block thread pools indefinitely.

### GAP-RES-004: No Fallback Behavior

**Severity: Medium | Effort: Medium**

There are no fallback methods defined for any Feign client. When a downstream call fails, the exception propagates directly to the caller as a raw error.

### GAP-RES-005: No Idempotency Protection on Mutating Endpoints

**Severity: High | Effort: Medium**

`POST /api/v1/transfer` and `POST /api/v1/utility-payment` are not idempotent. If a client retries a failed request (e.g., network timeout where the server actually processed it), the fund transfer or payment could be executed twice. There is no idempotency key mechanism.

### GAP-RES-006: Non-Atomic Balance Updates (Race Condition)

**Severity: Critical | Effort: Medium**

In `TransactionService.internalFundTransfer()` and `utilPayment()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The account is read, modified in Java, and saved back. Under concurrent requests, two transfers from the same account could both pass the balance validation and both debit, resulting in a **negative balance** (lost update / race condition). There is no:
- Optimistic locking (`@Version`) on `BankAccountEntity`
- Pessimistic locking (`SELECT ... FOR UPDATE`)
- Database-level atomic update (`UPDATE SET balance = balance - ?`)

### GAP-RES-007: Available Balance Calculation Bug

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```

The available balance is set to `actualBalance - amount` **after** `actualBalance` was already reduced by `amount`. This means `availableBalance` is set to `actualBalance - 2*amount`. The same bug exists in the credit side and in `utilPayment()`.

### GAP-RES-008: Transaction Boundary Issues

**Severity: High | Effort: Medium**

- `TransactionService` is marked `@Transactional` at the class level, which is correct for the core banking service
- However, `FundTransferService` and `UtilityPaymentService` are **not** `@Transactional`. If the Feign call to core banking succeeds but the subsequent local database update fails, the system will be in an inconsistent state (money moved but local record not updated)

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-001 | Code Organization | No shared library / multi-module build | Medium | Medium |
| GAP-ORG-002 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-003 | Code Organization | No code formatter configured | Low | Small |
| GAP-ORG-004 | Code Organization | Build artifacts in Git | Medium | Small |
| GAP-ERR-001 | Error Handling | All errors return HTTP 400 | High | Medium |
| GAP-ERR-002 | Error Handling | Generic handler leaks internal details | Critical | Small |
| GAP-ERR-003 | Error Handling | Inconsistent error response structure | Medium | Small |
| GAP-ERR-004 | Error Handling | Missing Feign error handling | High | Medium |
| GAP-ERR-005 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-TEST-001 | Testing | Near-zero test coverage on 3 services | High | Large |
| GAP-TEST-002 | Testing | No integration tests | High | Large |
| GAP-TEST-003 | Testing | No contract tests | Medium | Large |
| GAP-TEST-004 | Testing | Missing test configuration | Medium | Small |
| GAP-SEC-001 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-002 | Security | No input validation | Critical | Medium |
| GAP-SEC-003 | Security | Overly permissive DB user | High | Small |
| GAP-SEC-004 | Security | CSRF disabled without docs | Low | Small |
| GAP-SEC-005 | Security | Non-thread-safe Keycloak singleton | High | Small |
| GAP-SEC-006 | Security | No rate limiting | Medium | Medium |
| GAP-SEC-007 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-001 | API Design | No versioning strategy | Medium | Medium |
| GAP-API-002 | API Design | Inconsistent endpoint naming | Low | Small |
| GAP-API-003 | API Design | No pagination metadata | Medium | Small |
| GAP-API-004 | API Design | Incorrect OpenAPI dependency | Low | Small |
| GAP-API-005 | API Design | No standard error format | Medium | Small |
| GAP-API-006 | API Design | No HATEOAS / resource links | Low | Medium |
| GAP-OBS-001 | Observability | No custom health indicators | Medium | Small |
| GAP-OBS-002 | Observability | No structured logging | Medium | Small |
| GAP-OBS-003 | Observability | No custom metrics / Prometheus | Medium | Medium |
| GAP-OBS-004 | Observability | Tracing config not documented | Low | Small |
| GAP-OBS-005 | Observability | Sensitive data in logs | High | Small |
| GAP-RES-001 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-002 | Resilience | No retry policies | Medium | Small |
| GAP-RES-003 | Resilience | No timeout configuration | High | Small |
| GAP-RES-004 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-005 | Resilience | No idempotency protection | High | Medium |
| GAP-RES-006 | Resilience | Race condition in balance updates | Critical | Medium |
| GAP-RES-007 | Resilience | Available balance calculation bug | Critical | Small |
| GAP-RES-008 | Resilience | Missing @Transactional in orchestrators | High | Medium |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 12 |
| Medium | 15 |
| Low | 6 |
| **Total** | **38** |
