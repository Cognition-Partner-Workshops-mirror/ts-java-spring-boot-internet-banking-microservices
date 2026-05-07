# Internet Banking Microservices — Engineering Standards Gap Analysis

## Summary

This document compares the current codebase against industry-standard engineering best practices across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

| Category | Gaps Found | Critical | High | Medium | Low |
|---|---|---|---|---|---|
| Code Organization | 5 | 0 | 2 | 2 | 1 |
| Error Handling | 5 | 1 | 2 | 1 | 1 |
| Testing | 5 | 1 | 2 | 1 | 1 |
| Security | 7 | 3 | 2 | 1 | 1 |
| API Design | 6 | 0 | 2 | 3 | 1 |
| Observability | 5 | 0 | 2 | 2 | 1 |
| Resilience | 5 | 1 | 2 | 1 | 1 |
| **Total** | **38** | **6** | **14** | **11** | **7** |

---

## 1. Code Organization

### GAP-ORG-001: No Multi-Project Gradle Build
**Severity: High | Effort: Medium**

Each microservice has its own independent Gradle build with duplicated `build.gradle` configuration. There is no root `settings.gradle` or shared dependency management.

- **Current state**: 6 separate `build.gradle` files with identical Spring Boot/Cloud versions, plugin versions, and common dependencies copy-pasted across services.
- **Best practice**: A Gradle multi-project build with a root `build.gradle` and `settings.gradle` that defines common dependency versions via a version catalog or platform module.
- **Impact**: Version drift risk (e.g., one service upgrades Spring Boot while others don't), increased maintenance burden, inconsistent dependency versions.

### GAP-ORG-002: Duplicated Code Across Services
**Severity: High | Effort: Medium**

Exception handling classes, error response models, audit infrastructure, filter classes, and mapper base classes are copy-pasted across 4 services with minor variations.

- **Duplicated classes**: `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `AuditAware`, `BaseMapper`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`.
- **Variations**: `ErrorResponse` uses `@Builder` in some services and constructor in others. `GlobalExceptionHandler` has slightly different implementations.
- **Best practice**: Extract shared code into a common library module (e.g., `banking-common`).

### GAP-ORG-003: Inconsistent Package Structure Across Services
**Severity: Medium | Effort: Small**

While services follow a similar pattern (`controller`, `service`, `model`, `exception`, `configuration`), naming is inconsistent:

| Concept | core-banking-service | fund-transfer-service | utility-payment-service | user-service |
|---|---|---|---|---|
| Repository package | `repository` | `model.repository` | `repository` | `model.repository` |
| REST client package | N/A | `service.rest.client` | `service.rest` | `service.rest` |
| DTO request package | `model.dto.request` | `model.dto.request` | `model.rest.request` | `model.dto` |
| DTO response package | `model.dto.response` | `model.dto.response` | `model.rest.response` | `model.rest.response` |

### GAP-ORG-004: Mapper Classes Are Manually Instantiated
**Severity: Medium | Effort: Small**

Mapper instances are created with `new` rather than being Spring-managed beans or using a mapping framework:

```java
private UserMapper userMapper = new UserMapper();          // Not injected
private FundTransferMapper mapper = new FundTransferMapper(); // Not injected
```

- **Best practice**: Use MapStruct for compile-time-safe mapping, or at minimum make mappers Spring beans for consistency with the DI pattern.

### GAP-ORG-005: Build Artifacts Committed to Repository
**Severity: Low | Effort: Small**

The `.gradle` directories and `build/` directories within each service contain cached build outputs that should be gitignored. While a `.gitignore` exists at the root, some build artifacts appear in the repository.

---

## 2. Error Handling

### GAP-ERR-001: All Exceptions Return HTTP 400 Bad Request
**Severity: Critical | Effort: Medium**

Every `GlobalExceptionHandler` across all services maps ALL exceptions (including `EntityNotFoundException`) to `400 Bad Request`:

```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...); // Always 400
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e); // Always 400
}
```

- **Best practice**: `EntityNotFoundException` → `404`, validation errors → `400`, `InsufficientFundsException` → `422`, unexpected errors → `500`.
- **Impact**: Clients cannot distinguish between "not found", "validation error", "business rule violation", and "server error".

### GAP-ERR-002: Exception Details Leaked to Clients
**Severity: High | Effort: Small**

The catch-all exception handler returns the full exception object as a string:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

- **Impact**: Stack traces, class names, and internal details are exposed in API responses, which is both a security risk and poor UX.
- **Best practice**: Return a generic error message for unexpected exceptions; log the full details server-side.

### GAP-ERR-003: Inconsistent Error Response Format
**Severity: High | Effort: Small**

- `SimpleBankingGlobalException` handlers return `ErrorResponse { code, message }`.
- The catch-all handler returns a plain string.
- `ErrorResponse` is constructed differently per service (`@Builder` vs constructor).
- No timestamp, no request ID, no path information in the error response.

### GAP-ERR-004: No Feign Error Decoder in All Services
**Severity: Medium | Effort: Small**

Only `user-service` has a `CustomFeignErrorDecoder`. The `fund-transfer-service` and `utility-payment-service` use the default Feign error decoder, which means errors from `core-banking-service` are not properly translated into service-specific exceptions.

### GAP-ERR-005: Missing Raw Type Parameterization on ResponseEntity
**Severity: Low | Effort: Small**

Most controller methods return `ResponseEntity` without a type parameter (raw type) instead of `ResponseEntity<SpecificType>`. Only `user-service` uses typed responses (`ResponseEntity<User>`, `ResponseEntity<List<User>>`).

- **Impact**: Reduced compile-time type safety, poor OpenAPI schema generation.

---

## 3. Testing

### GAP-TEST-001: No Tests for 5 of 6 Services
**Severity: Critical | Effort: Large**

Only `core-banking-service` has meaningful unit tests (3 test classes, ~20 test methods). The remaining services have only empty application context test stubs:

| Service | Test Classes | Meaningful Tests |
|---|---|---|
| core-banking-service | 4 | 20 (AccountServiceTest, TransactionServiceTest, UserServiceTest) |
| internet-banking-user-service | 1 | 0 (empty context test) |
| internet-banking-fund-transfer-service | 0 | 0 |
| internet-banking-utility-payment-service | 0 | 0 |
| internet-banking-api-gateway | 1 | 0 (empty context test) |
| internet-banking-service-registry | 1 | 0 (empty context test) |

### GAP-TEST-002: No Integration Tests
**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller endpoints with `@WebMvcTest` or `@SpringBootTest`
- Repository queries against a real database
- Full request/response cycle through the API
- Feign client behavior with WireMock or similar

### GAP-TEST-003: No Contract Tests Between Services
**Severity: High | Effort: Large**

With 3 Feign clients calling `core-banking-service`, there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that producer and consumer APIs remain compatible.

- **Impact**: API changes in `core-banking-service` could silently break consumers.

### GAP-TEST-004: No Test Coverage Reporting
**Severity: Medium | Effort: Small**

No JaCoCo or similar coverage plugin is configured. There is no visibility into what percentage of the codebase is covered by tests.

### GAP-TEST-005: Test Configuration Does Not Fully Isolate External Dependencies
**Severity: Low | Effort: Small**

The `core-banking-service` test config still references Eureka (`eureka.client.service-url.defaultZone`). While existing tests are unit tests that don't start the full context, any `@SpringBootTest` would fail without Eureka.

---

## 4. Security

### GAP-SEC-001: Hard-Coded Credentials in Docker Compose
**Severity: Critical | Effort: Small**

Database passwords, Keycloak admin credentials, and the MySQL root password are hard-coded in `docker-compose.yml`:

```yaml
MYSQL_ROOT_PASSWORD: woVERANKliGharym
KEYCLOAK_ADMIN_PASSWORD: password
KC_DB_PASSWORD: password
```

- **Best practice**: Use Docker secrets, `.env` files, or a secrets manager. At minimum, reference environment variables.

### GAP-SEC-002: Test Credentials in README
**Severity: Critical | Effort: Small**

The README contains plaintext credentials:
```
Test Credentials : ib_admin@javatodev.com / 5V7huE3G86uB
```

### GAP-SEC-003: No Input Validation on Request Bodies
**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or other Bean Validation annotations are present on any request DTOs or controller parameters:

```java
// No validation at all
public class FundTransferRequest {
    private String fromAccount;   // Could be null
    private String toAccount;     // Could be null
    private BigDecimal amount;    // Could be null, zero, or negative
}
```

- **Impact**: Null pointer exceptions, negative transfers, empty account numbers, etc. In a banking application, this is especially dangerous.

### GAP-SEC-004: CSRF Disabled Without Documentation
**Severity: High | Effort: Small**

CSRF protection is disabled at the gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While this is common for stateless JWT APIs, there is no documentation explaining this decision or confirming that no session-based authentication is used.

### GAP-SEC-005: No Role-Based Access Control
**Severity: High | Effort: Medium**

All authenticated requests have equal access. There is no distinction between admin users and regular users. The `PATCH /update/{id}` endpoint (which changes user status to APPROVED, enabling Keycloak login) should be admin-only.

### GAP-SEC-006: Keycloak Singleton Is Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a naive singleton pattern without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {  // Race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

### GAP-SEC-007: No Dependency Vulnerability Scanning
**Severity: Low | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured. Some dependencies may have known CVEs (e.g., older versions of transitive dependencies).

---

## 5. API Design

### GAP-API-001: Non-Standard RESTful URL Naming
**Severity: High | Effort: Medium**

Inconsistent naming conventions across services:

| Current | RESTful Standard |
|---|---|
| `POST /api/v1/bank-users/register` | `POST /api/v1/bank-users` |
| `PATCH /api/v1/bank-users/update/{id}` | `PATCH /api/v1/bank-users/{id}` |
| `GET /api/v1/account/util-account/{account_name}` | `GET /api/v1/utility-accounts/{name}` |
| `POST /api/v1/transaction/fund-transfer` | `POST /api/v1/fund-transfers` |
| `POST /api/v1/transaction/util-payment` | `POST /api/v1/utility-payments` |

### GAP-API-002: Pagination Response Does Not Include Metadata
**Severity: High | Effort: Small**

Paginated endpoints return `List<T>` instead of a page wrapper with total count, page number, and page size:

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

- **Best practice**: Return `{ content: [...], totalElements, totalPages, pageNumber, pageSize }`.

### GAP-API-003: No API Versioning Strategy
**Severity: Medium | Effort: Medium**

While `/api/v1/` is used in URL paths, there is no documented versioning strategy, no mechanism to support `v2` alongside `v1`, and no content negotiation headers.

### GAP-API-004: OpenAPI/Swagger Configuration Incomplete
**Severity: Medium | Effort: Small**

The `springdoc-openapi-starter-webflux-ui` dependency is included in business services (which are WebMVC, not WebFlux), and there is no global OpenAPI configuration (title, description, server URLs, security schemes). Basic `@Operation` and `@Tag` annotations exist but are minimal.

- **Mismatch**: Using `webflux-ui` artifact in a `spring-boot-starter-web` (servlet) application. Should use `springdoc-openapi-starter-webmvc-ui`.

### GAP-API-005: No Request/Response Filtering or Searching
**Severity: Medium | Effort: Medium**

Paginated list endpoints support `page` and `size` but not filtering, sorting criteria, or search. For example, there is no way to filter fund transfers by date range, status, or account number.

### GAP-API-006: Inconsistent Use of HTTP Methods
**Severity: Low | Effort: Small**

The `POST /register` endpoint could follow REST convention (`POST /bank-users`). Using `PATCH /update/{id}` includes the verb "update" redundantly — `PATCH /{id}` is sufficient.

---

## 6. Observability

### GAP-OBS-001: Inconsistent Logging Patterns
**Severity: High | Effort: Small**

- Some services use `@Slf4j` consistently, others do not.
- Log messages inconsistently use structured patterns. Some use string concatenation instead of parameterized logging:
  ```java
  log.info("Sending fund transfer request {}" + request.toString()); // Bug: concatenation instead of placeholder
  log.error("IO Exception on reading exception message feign client" + e); // Same bug
  ```
- No structured logging format (JSON) configured for production.

### GAP-OBS-002: No Custom Health Check Endpoints
**Severity: High | Effort: Small**

While Spring Boot Actuator is included (providing `/actuator/health`), no custom health indicators exist for:
- Database connectivity
- Keycloak connectivity
- Feign client service availability
- RabbitMQ connectivity (when integrated)

The default health endpoint only reports `UP/DOWN` without meaningful checks of downstream dependencies.

### GAP-OBS-003: No Metrics Endpoints Beyond Actuator Defaults
**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` provides basic JVM and HTTP metrics, there are no custom business metrics:
- Fund transfers per minute
- Payment processing success/failure rates
- Average transaction amounts
- User registration rates

No Prometheus endpoint (`/actuator/prometheus`) is explicitly configured despite Prometheus being listed in the README tech stack.

### GAP-OBS-004: Distributed Tracing Coverage Gaps
**Severity: Medium | Effort: Small**

- Tracing libraries are included but there is no explicit trace ID propagation in log messages (MDC context).
- No `spring.zipkin.base-url` configuration is visible in bootstrap configs (may be in config server).
- No sampling rate configuration — defaults may send 100% or 10% of traces.

### GAP-OBS-005: No Centralized Log Aggregation Configuration
**Severity: Low | Effort: Medium**

No ELK/EFK stack, CloudWatch, or other log aggregation is configured. With 6 services, debugging production issues requires SSH-ing into individual containers.

---

## 7. Resilience

### GAP-RES-001: No Circuit Breakers on Feign Clients
**Severity: Critical | Effort: Medium**

All inter-service communication via Feign has no circuit breaker. If `core-banking-service` goes down, all dependent services will hang on HTTP calls until TCP timeout, cascading the failure.

- **Impact**: A single service failure cascades to the entire platform.
- **Best practice**: Add Resilience4j `@CircuitBreaker` annotations on Feign clients with fallback methods.

### GAP-RES-002: No Retry Policies on Inter-Service Calls
**Severity: High | Effort: Small**

Transient network errors (timeouts, connection resets) are not retried. A single failed HTTP call results in an immediate error to the client.

- **Best practice**: Configure Feign retry with exponential backoff, or use Resilience4j `@Retry`.

### GAP-RES-003: No Timeout Configuration on Feign Clients
**Severity: High | Effort: Small**

No explicit connection or read timeouts are configured for Feign clients. The default Feign timeout may be too generous (10+ seconds), causing thread pool exhaustion under load.

```java
@FeignClient(value = "core-banking-service", configuration = CustomFeignClientConfiguration.class)
// No timeout configuration
```

### GAP-RES-004: No Fallback Behavior
**Severity: Medium | Effort: Medium**

When inter-service calls fail, the error is propagated directly to the client. There are no fallback responses, degraded functionality modes, or queued retry mechanisms.

- **Example**: If core-banking is down, the fund-transfer-service could still accept the request and process it later (event-driven with RabbitMQ), but this pattern is not implemented.

### GAP-RES-005: Non-Atomic Transaction Processing
**Severity: Low | Effort: Large**

The fund transfer flow spans two services without distributed transaction support (e.g., Saga pattern). If the fund-transfer-service persists a `PENDING` record and then the Feign call to core-banking succeeds but the subsequent status update to `SUCCESS` fails (e.g., network error), the record remains `PENDING` forever with no reconciliation mechanism.

- **Current code** in `FundTransferService.fundTransfer()`:
  ```java
  FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);  // PENDING saved
  FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request); // Core debits/credits
  optFundTransfer.setStatus(TransactionStatus.SUCCESS); // If this line fails, money moved but status stuck at PENDING
  fundTransferRepository.save(optFundTransfer);
  ```

---

## Appendix: Gap Summary Matrix

| ID | Category | Description | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-001 | Code Organization | No multi-project Gradle build | High | Medium |
| GAP-ORG-002 | Code Organization | Duplicated code across services | High | Medium |
| GAP-ORG-003 | Code Organization | Inconsistent package structure | Medium | Small |
| GAP-ORG-004 | Code Organization | Manually instantiated mappers | Medium | Small |
| GAP-ORG-005 | Code Organization | Build artifacts in repository | Low | Small |
| GAP-ERR-001 | Error Handling | All exceptions return HTTP 400 | Critical | Medium |
| GAP-ERR-002 | Error Handling | Exception details leaked to clients | High | Small |
| GAP-ERR-003 | Error Handling | Inconsistent error response format | High | Small |
| GAP-ERR-004 | Error Handling | Missing Feign error decoder in some services | Medium | Small |
| GAP-ERR-005 | Error Handling | Raw `ResponseEntity` types | Low | Small |
| GAP-TEST-001 | Testing | No tests for 5 of 6 services | Critical | Large |
| GAP-TEST-002 | Testing | No integration tests | High | Large |
| GAP-TEST-003 | Testing | No contract tests | High | Large |
| GAP-TEST-004 | Testing | No test coverage reporting | Medium | Small |
| GAP-TEST-005 | Testing | Test config not fully isolated | Low | Small |
| GAP-SEC-001 | Security | Hard-coded credentials in Docker Compose | Critical | Small |
| GAP-SEC-002 | Security | Test credentials in README | Critical | Small |
| GAP-SEC-003 | Security | No input validation | Critical | Medium |
| GAP-SEC-004 | Security | CSRF disabled without documentation | High | Small |
| GAP-SEC-005 | Security | No role-based access control | High | Medium |
| GAP-SEC-006 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-007 | Security | No dependency vulnerability scanning | Low | Small |
| GAP-API-001 | API Design | Non-standard RESTful URLs | High | Medium |
| GAP-API-002 | API Design | Pagination missing metadata | High | Small |
| GAP-API-003 | API Design | No versioning strategy | Medium | Medium |
| GAP-API-004 | API Design | OpenAPI/Swagger misconfigured | Medium | Small |
| GAP-API-005 | API Design | No filtering or searching | Medium | Medium |
| GAP-API-006 | API Design | Inconsistent HTTP method usage | Low | Small |
| GAP-OBS-001 | Observability | Inconsistent logging | High | Small |
| GAP-OBS-002 | Observability | No custom health checks | High | Small |
| GAP-OBS-003 | Observability | No custom business metrics | Medium | Medium |
| GAP-OBS-004 | Observability | Distributed tracing gaps | Medium | Small |
| GAP-OBS-005 | Observability | No centralized log aggregation | Low | Medium |
| GAP-RES-001 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-002 | Resilience | No retry policies | High | Small |
| GAP-RES-003 | Resilience | No timeout configuration | High | Small |
| GAP-RES-004 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-005 | Resilience | Non-atomic transaction processing | Low | Large |
