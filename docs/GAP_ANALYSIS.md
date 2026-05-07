# Engineering Standards Gap Analysis

> Systematic comparison of the Internet Banking Microservices codebase against engineering best practices.

---

## Rating Scale

**Severity:**
| Rating | Meaning |
|--------|---------|
| **Critical** | Blocks production readiness; data loss, security vulnerability, or correctness issue |
| **High** | Significant quality/reliability risk; should be fixed before scaling |
| **Medium** | Best-practice violation that increases maintenance burden |
| **Low** | Polish item; nice-to-have improvement |

**Effort:**
| Rating | Meaning |
|--------|---------|
| **Small** | < 1 day; localized change |
| **Medium** | 1–3 days; touches multiple files or services |
| **Large** | 3+ days; architectural change or cross-cutting concern |

---

## 1. Code Organization

### 1.1 Duplicated Exception Framework Across Services

**Severity: Medium | Effort: Medium**

Each service independently defines its own copy of:
- `SimpleBankingGlobalException`
- `ErrorResponse`
- `GlobalExceptionHandler`
- `GlobalErrorCode`

These classes are nearly identical across `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, and `internet-banking-utility-payment-service`.

**Evidence:** 4 copies of `GlobalExceptionHandler.java`, 4 copies of `SimpleBankingGlobalException.java`, 4 copies of `ErrorResponse.java`.

**Recommendation:** Extract a shared `banking-common` library (Gradle module or published artifact) containing the exception framework, audit utilities, and DTOs.

### 1.2 Duplicated Audit Infrastructure

**Severity: Medium | Effort: Medium**

Three services (User, Fund Transfer, Utility Payment) each duplicate:
- `AuditAware` base class
- `AuditConfig` / `AuditorAwareConfig`
- `AppAuthUserFilter`
- `ApiRequestContext` / `ApiRequestContextHolder`

**Recommendation:** Move to shared library alongside exception framework.

### 1.3 Duplicated Feign Error Decoder

**Severity: Low | Effort: Small**

`CustomFeignClientConfiguration` and `CustomFeignErrorDecoder` are duplicated in User Service, Fund Transfer Service, and Utility Payment Service.

**Recommendation:** Include in shared library.

### 1.4 No Gradle Multi-Project Build

**Severity: Low | Effort: Medium**

Each service is an independent Gradle project with its own `gradlew`, `settings.gradle`, and wrapper JARs. There is no root-level `settings.gradle` linking all sub-projects.

**Impact:**
- Cannot build all services with a single command
- No shared dependency version management
- Gradle wrapper duplicated 7× (~250KB × 7)

**Recommendation:** Convert to a Gradle multi-project build with a shared `buildSrc` or version catalog.

### 1.5 Mapper Classes Instantiated as Fields (Not Spring Beans)

**Severity: Low | Effort: Small**

In `AccountService`, `UserService`, etc., mapper classes are instantiated directly:
```java
private BankAccountMapper bankAccountMapper = new BankAccountMapper();
```

This bypasses Spring's lifecycle management and makes testing harder.

**Recommendation:** Register mappers as `@Component` beans and inject them, or use MapStruct.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400 (Bad Request)

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` for all exceptions, including:
- `EntityNotFoundException` → should be **404 Not Found**
- `InsufficientFundsException` → should be **422 Unprocessable Entity** or **409 Conflict**
- General `Exception` catch-all → should be **500 Internal Server Error**

**Evidence:**
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Impact:** Clients cannot distinguish between "your input was wrong" and "the server crashed."

### 2.2 Exception Details Leaked in Generic Handler

**Severity: High | Effort: Small**

The generic exception handler exposes the full exception `toString()` in the response body:
```java
.body("Exception occur inside API " + e);
```

This leaks stack traces, class names, and potentially sensitive data (database connection strings, internal paths) to clients.

**Recommendation:** Return a generic error message. Log the full exception server-side.

### 2.3 Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- Business exceptions return `{ "code": "...", "message": "..." }` (structured `ErrorResponse`)
- Generic exceptions return a plain string: `"Exception occur inside API ..."`
- The Fund Transfer service uses `new ErrorResponse(code, message)` while others use `ErrorResponse.builder().code(...).message(...).build()`

**Impact:** Clients must handle two different response shapes. Inconsistent construction patterns increase maintenance burden.

### 2.4 No Validation on Request Bodies

**Severity: High | Effort: Medium**

No `@Valid` annotations on controller request parameters. No `@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc., on DTO fields. Examples:

- `FundTransferRequest` accepts null `fromAccount`, `toAccount`, or negative `amount`
- `UserController.createUser(@RequestBody User request)` — no validation at all
- `UtilityPaymentRequest` accepts null `providerId` or `account`

**Impact:** Null pointer exceptions deep in service logic instead of clear 400 validation errors at the controller boundary.

### 2.5 Missing Error Handling in Fund Transfer Orchestration

**Severity: Critical | Effort: Medium**

In `FundTransferService.fundTransfer()`:
```java
FundTransferEntity optFundTransfer = fundTransferRepository.save(entity);      // status=PENDING
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);  // call core
optFundTransfer.setStatus(TransactionStatus.SUCCESS);                          // optimistic
fundTransferRepository.save(optFundTransfer);
```

If the Feign call fails, the entity remains with `status=PENDING` forever. There is no:
- Try/catch around the Feign call
- Retry logic
- Status update to `FAILED`
- Compensation/rollback mechanism

The same issue exists in `UtilityPaymentService`.

---

## 3. Testing

### 3.1 Unit Tests Only in Core Banking Service

**Severity: High | Effort: Large**

Test coverage:
| Service | Unit Tests | Integration Tests | Contract Tests |
|---------|-----------|------------------|----------------|
| Core Banking | ✅ 3 test classes (20+ tests) | ❌ | ❌ |
| User Service | ❌ (only context load) | ❌ | ❌ |
| Fund Transfer | ❌ (only context load) | ❌ | ❌ |
| Utility Payment | ❌ (only context load) | ❌ | ❌ |
| API Gateway | ❌ (only context load) | ❌ | ❌ |
| Service Registry | ❌ (only context load) | ❌ | ❌ |
| Config Server | ❌ (only context load) | ❌ | ❌ |

**Impact:** No test coverage for Keycloak integration, Feign client behavior, fund transfer orchestration, utility payment orchestration, or API Gateway routing.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No Spring Boot integration tests (`@SpringBootTest` with actual context). The `CoreBankingServiceApplicationTests` context load test uses H2 in-memory database (Flyway disabled) but doesn't test any endpoints.

No Testcontainers usage for MySQL, Keycloak, or inter-service communication testing.

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

No Spring Cloud Contract or Pact tests exist for the Feign client interfaces. If Core Banking changes its API, downstream services discover the breakage only at runtime.

### 3.4 Test Configuration Disables Flyway

**Severity: Low | Effort: Small**

`core-banking-service/src/test/resources/application.yml` sets `flyway.enabled: false` and uses H2 with `ddl-auto: none`. This means tests don't validate the Flyway migration scripts at all.

---

## 4. Security

### 4.1 Hard-Coded Credentials in Source Control

**Severity: Critical | Effort: Medium**

Plain-text credentials committed to the repository:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | PostgreSQL password: `password` |
| `mysql/privileges.sql` | DB user password: `oPItyPticIAt` |
| Postman environment | Keycloak client secret: `0efd3e37-258e-4488-96ae-1dfe34679c9d` |

**Recommendation:** Use Docker Compose secrets, environment variable references, or a secrets manager. At minimum, use `.env` files excluded from version control.

### 4.2 No Input Validation (Bean Validation)

**Severity: High | Effort: Medium**

See [Section 2.4](#24-no-validation-on-request-bodies). This is also a security concern — no protection against malformed or malicious payloads.

### 4.3 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the API Gateway security configuration. This is correct for a stateless REST API using Bearer tokens, but it's not documented in the security configuration class.

### 4.4 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting at the API Gateway or service level. The registration endpoint is public and unauthenticated, making it a target for abuse.

**Recommendation:** Add `spring-cloud-gateway-ratelimiter` with Redis or use a simple in-memory rate limiter.

### 4.5 Keycloak Running in Dev Mode

**Severity: Medium | Effort: Small**

Docker Compose starts Keycloak with `start-dev` which disables HTTPS, uses an in-memory cache, and relaxes security settings. The production command should be `start` with TLS configured.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured in the build pipeline. No `dependencyCheck` Gradle plugin present.

### 4.7 No HTTPS/TLS Configuration

**Severity: Medium | Effort: Medium**

All inter-service communication is plain HTTP. No TLS certificates are configured for any service, including the API Gateway which is the public entry point.

---

## 5. API Design

### 5.1 Inconsistent Use of HTTP Status Codes

**Severity: High | Effort: Small**

See [Section 2.1](#21-all-errors-return-http-400-bad-request). Additionally:
- `POST` endpoints return `200 OK` instead of `201 Created`
- No `204 No Content` usage
- Raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<User>`)

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Small**

The URL path includes `/v1/` but there's no mechanism for routing to different versions. This is acceptable for now but noted for future evolution.

### 5.3 Pagination Response Missing Metadata

**Severity: Medium | Effort: Small**

Paginated endpoints return raw `List<T>` instead of including pagination metadata:
```java
return ResponseEntity.ok(userService.readUsers(pageable));
// Returns: [{ ... }, { ... }]
// Should return: { content: [...], totalElements: 100, totalPages: 10, ... }
```

**Impact:** Clients cannot implement proper pagination UI without knowing total element count or page count.

### 5.4 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

Paginated endpoints accept Spring `Pageable` (which supports `sort` and `page`/`size`) but this is not documented in API specifications or Postman collection.

### 5.5 SpringDoc/OpenAPI Present But Not Fully Leveraged

**Severity: Low | Effort: Small**

Services include `springdoc-openapi-starter-webmvc-ui` dependency and use `@Tag` and `@Operation` annotations on controllers, which is good. However:
- No global API info configuration (`@OpenAPIDefinition`)
- No example request/response values (`@Schema(example = "...")`)
- No error response documentation (`@ApiResponse`)

### 5.6 Untyped ResponseEntity Return Types

**Severity: Medium | Effort: Small**

Controllers use raw `ResponseEntity` without generics:
```java
public ResponseEntity sendFundTransfer(...)    // should be ResponseEntity<FundTransferResponse>
public ResponseEntity readFundTransfers(...)   // should be ResponseEntity<List<FundTransfer>>
```

**Impact:** OpenAPI docs cannot infer response schema. Type safety is lost at compile time.

---

## 6. Observability

### 6.1 Health Checks Are Minimal

**Severity: Medium | Effort: Small**

Actuator is included in all services, but only the default `/actuator/health` endpoint is exposed. No custom health indicators for:
- Database connectivity
- Keycloak availability
- Downstream service availability (for orchestration services)

### 6.2 No Structured Logging

**Severity: Medium | Effort: Medium**

Services use SLF4J (`@Slf4j`) with default Logback configuration. No structured (JSON) logging is configured. In a containerized deployment, JSON logs are essential for log aggregation (ELK, CloudWatch, etc.).

### 6.3 No Metrics Endpoints

**Severity: Medium | Effort: Small**

Although Micrometer is included (via actuator), no `/actuator/prometheus` endpoint is exposed and no custom business metrics are defined (e.g., transfer count, payment success rate, registration count).

### 6.4 Trace Sampling Not Configured

**Severity: Low | Effort: Small**

No explicit trace sampling configuration. The default Micrometer Brave setting samples 10% of traces in production. For a banking application, 100% sampling may be desirable for audit purposes.

### 6.5 Inconsistent Log Messages

**Severity: Low | Effort: Small**

Log messages vary in style:
```java
log.info("Creating user with {}", request.toString());        // toString() redundant with {}
log.info("Got fund transfer request from API {}", request);   // inconsistent prefix
log.info("Sending fund transfer request {}" + request);       // string concatenation (wrong)
```

The `"Sending fund transfer request {}" + request` in `FundTransferService` uses string concatenation instead of SLF4J parameterization, defeating lazy evaluation.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: High | Effort: Medium**

No `spring-cloud-starter-circuitbreaker-resilience4j` dependency. If Core Banking Service is down, all Feign calls from User, Fund Transfer, and Utility Payment services will fail without:
- Timeout limiting
- Failure counting
- Automatic circuit opening
- Fallback responses

**Impact:** A single downstream failure cascades to all upstream services.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No `@Retry` annotations, no Feign retry configuration, and no Spring Retry dependency. Transient network errors cause immediate failures.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout settings on Feign clients, database connections, or the API Gateway proxy. Default timeouts are used, which may be too long (hanging threads) or too short (premature failures).

Feign clients have no `connectTimeout` or `readTimeout` configuration.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No `@CircuitBreaker(fallbackMethod = "...")` or Feign fallback factories. When Core Banking is unavailable:
- Fund transfers fail with unhandled Feign exceptions
- User registration fails with unhandled Feign exceptions
- No graceful degradation

### 7.5 No Bulkhead Pattern

**Severity: Medium | Effort: Medium**

No thread pool isolation or semaphore bulkheads. A slow Core Banking response could exhaust the thread pool in the Fund Transfer service, affecting all other requests including health checks.

### 7.6 Optimistic Status Updates Without Compensation

**Severity: Critical | Effort: Large**

In `FundTransferService`:
```java
entity.setStatus(TransactionStatus.PENDING);
fundTransferRepository.save(entity);                           // saved as PENDING
FundTransferResponse response = bankingCoreFeignClient.fundTransfer(request);  // might fail
optFundTransfer.setStatus(TransactionStatus.SUCCESS);          // only reached on success
fundTransferRepository.save(optFundTransfer);
```

If the Feign call throws, the record stays `PENDING` forever. There is:
- No try/catch to set status to `FAILED`
- No scheduled job to clean up stale `PENDING` records
- No saga/compensation pattern
- No idempotency key for retry safety

The same pattern exists in `UtilityPaymentService`.

### 7.7 No Database Connection Pool Tuning

**Severity: Low | Effort: Small**

No HikariCP configuration is present (connection pool size, idle timeout, max lifetime). Default settings are used. For a multi-service setup sharing a single MySQL instance, pool sizing should be intentional.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | Duplicated exception framework | Code Organization | Medium | Medium |
| 1.2 | Duplicated audit infrastructure | Code Organization | Medium | Medium |
| 1.3 | Duplicated Feign error decoder | Code Organization | Low | Small |
| 1.4 | No Gradle multi-project build | Code Organization | Low | Medium |
| 1.5 | Mappers not Spring beans | Code Organization | Low | Small |
| 2.1 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.2 | Exception details leaked | Error Handling | High | Small |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | No request body validation | Error Handling | High | Medium |
| 2.5 | Missing Feign call error handling | Error Handling | Critical | Medium |
| 3.1 | Tests only in Core Banking | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Test config disables Flyway | Testing | Low | Small |
| 4.1 | Hard-coded credentials | Security | Critical | Medium |
| 4.2 | No input validation | Security | High | Medium |
| 4.3 | CSRF disabled undocumented | Security | Low | Small |
| 4.4 | No rate limiting | Security | Medium | Medium |
| 4.5 | Keycloak dev mode | Security | Medium | Small |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 4.7 | No HTTPS/TLS | Security | Medium | Medium |
| 5.1 | Wrong HTTP status codes | API Design | High | Small |
| 5.2 | No API versioning mechanism | API Design | Low | Small |
| 5.3 | Pagination missing metadata | API Design | Medium | Small |
| 5.4 | No filtering/sorting docs | API Design | Low | Small |
| 5.5 | OpenAPI not fully leveraged | API Design | Low | Small |
| 5.6 | Untyped ResponseEntity | API Design | Medium | Small |
| 6.1 | Minimal health checks | Observability | Medium | Small |
| 6.2 | No structured logging | Observability | Medium | Medium |
| 6.3 | No metrics endpoints | Observability | Medium | Small |
| 6.4 | Trace sampling not configured | Observability | Low | Small |
| 6.5 | Inconsistent log messages | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No bulkhead pattern | Resilience | Medium | Medium |
| 7.6 | No compensation for failed transfers | Resilience | Critical | Large |
| 7.7 | No connection pool tuning | Resilience | Low | Small |

**Totals by Severity:**
- **Critical:** 3
- **High:** 11
- **Medium:** 16
- **Low:** 11
