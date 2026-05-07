# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

**Severity Scale:** Critical > High > Medium > Low
**Effort Scale:** Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Medium

Each service is an entirely independent Gradle project with its own `gradlew` wrapper, `settings.gradle`, and `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds across all services.

**Impact:** Cannot build/test all services with a single command. Duplicated dependency versions across 6+ `build.gradle` files create drift risk (e.g., one service could accidentally use a different Spring Boot version).

**Evidence:**
- 6 separate `build.gradle` files all independently declaring `version '3.2.4'` and `springCloudVersion "2023.0.0"`
- No root `build.gradle` or `settings.gradle`

---

### GAP-ORG-02: Massive Code Duplication Across Services
**Severity:** High | **Effort:** Large

Identical classes are copy-pasted across 3+ services with no shared library:

| Duplicated Class | Services |
|-----------------|----------|
| `AuditAware` | user-service, fund-transfer-service, utility-payment-service |
| `ApiRequestContext` | user-service, fund-transfer-service, utility-payment-service |
| `ApiRequestContextHolder` | user-service, fund-transfer-service, utility-payment-service |
| `AppAuthUserFilter` | user-service, fund-transfer-service, utility-payment-service |
| `BaseMapper<E, D>` | user-service, fund-transfer-service, utility-payment-service, core-banking-service |
| `ErrorResponse` | all 4 business services |
| `GlobalExceptionHandler` | all 4 business services |
| `SimpleBankingGlobalException` | all 4 business services |
| `CustomFeignClientConfiguration` | fund-transfer-service, utility-payment-service |
| `AccountResponse` | user-service, fund-transfer-service, utility-payment-service |
| `TransactionStatus` enum | fund-transfer-service, utility-payment-service |

**Impact:** Bug fixes or improvements must be applied to every copy independently. Divergence is inevitable.

---

### GAP-ORG-03: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

Package naming is mostly consistent (`com.javatodev.finance`) but sub-package organization varies:
- `core-banking-service`: `repository/` at top level
- `user-service`: `model/repository/` nested under model
- `fund-transfer-service`: `model/repository/` nested under model
- `utility-payment-service`: `repository/` at top level

Feign clients are in different locations:
- `user-service`: `service/rest/BankingCoreRestClient`
- `fund-transfer-service`: `service/rest/client/BankingCoreFeignClient`
- `utility-payment-service`: `service/rest/BankingCoreRestClient`

---

### GAP-ORG-04: Inconsistent Indentation / Formatting
**Severity:** Low | **Effort:** Small

Some `build.gradle` files use tabs, others use spaces. No code formatter (Checkstyle, Spotless, etc.) is configured.

---

## 2. Error Handling

### GAP-ERR-01: Catch-All Exception Handler Returns Raw Exception Details
**Severity:** Critical | **Effort:** Small

All four services have a `GlobalExceptionHandler` with:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest()
        .body("Exception occur inside API " + e);
}
```

**Impact:**
- Leaks stack traces, internal class names, and potentially sensitive data to API consumers.
- All unhandled exceptions return HTTP 400 (Bad Request) regardless of actual cause — a `NullPointerException` should be 500, not 400.

---

### GAP-ERR-02: Missing Raw-Type Parameterization on ResponseEntity
**Severity:** Medium | **Effort:** Small

Most controller methods return raw `ResponseEntity` instead of `ResponseEntity<T>`:
```java
public ResponseEntity getBankAccount(...) // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)   // should be ResponseEntity<FundTransferResponse>
```

**Impact:** No compile-time type safety, OpenAPI/Swagger docs cannot infer response types accurately.

---

### GAP-ERR-03: No Feign Error Decoder in Most Services
**Severity:** High | **Effort:** Medium

Only `internet-banking-user-service` has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services have no Feign error decoder configured.

**Impact:** When `core-banking-service` returns an error (e.g., insufficient funds), Feign wraps it in a generic `FeignException` instead of propagating the meaningful error response to the caller. The end user receives an opaque error.

---

### GAP-ERR-04: No Handling for Partial Failure in Orchestrated Transactions
**Severity:** Critical | **Effort:** Large

In `FundTransferService.fundTransfer()`:
1. A `FundTransferEntity` is saved with `status=PENDING`.
2. A Feign call to core-banking-service executes the transfer.
3. If the Feign call succeeds, status is updated to `SUCCESS`.

If the Feign call succeeds but the subsequent local DB save fails, the transfer is executed in the core bank but the local record remains `PENDING` — with no retry or compensation logic. Same issue exists in `UtilityPaymentService`.

**Impact:** Data inconsistency between services with no recovery mechanism.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage for 5 of 6 Services
**Severity:** Critical | **Effort:** Large

| Service | Test Classes | Meaningful Tests |
|---------|-------------|-----------------|
| core-banking-service | 4 | 17 unit tests (AccountService, TransactionService, UserService) |
| internet-banking-user-service | 1 | 0 (empty context test) |
| internet-banking-fund-transfer-service | 1 | 0 (empty context test) |
| internet-banking-utility-payment-service | 1 | 0 (empty context test) |
| internet-banking-api-gateway | 1 | 0 (empty context test) |
| internet-banking-config-server | 1 | 0 (empty context test) |

**Impact:** No safety net for regressions. Critical business logic (fund transfers, user registration, Keycloak integration) is entirely untested.

---

### GAP-TEST-02: No Integration Tests
**Severity:** High | **Effort:** Large

There are no integration tests that verify:
- Database interaction (JPA queries, Flyway migrations)
- Feign client contracts between services
- API Gateway routing rules
- Keycloak integration workflows

**Impact:** Cannot validate end-to-end behavior. Schema changes or API modifications could silently break inter-service communication.

---

### GAP-TEST-03: No Contract Tests Between Services
**Severity:** Medium | **Effort:** Large

Services communicate via OpenFeign with implicit contracts. No Pact or Spring Cloud Contract tests verify that provider APIs match consumer expectations.

**Impact:** A change to `core-banking-service`'s API could break fund-transfer and utility-payment services silently.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials Throughout Codebase
**Severity:** Critical | **Effort:** Medium

Multiple credentials are hardcoded in version-controlled files:

| File | Secret |
|------|--------|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| `mysql/Dockerfile` | `MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `mysql/privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact:** Credentials are exposed in the Git history permanently. Anyone with repo access has database root access.

---

### GAP-SEC-02: No Input Validation on API Endpoints
**Severity:** Critical | **Effort:** Medium

No request body validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.) exist on any controller method or DTO:

```java
// No validation — accepts null/negative amounts, empty account numbers
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest fundTransferRequest)
```

**Impact:** Malformed requests (e.g., negative transfer amounts, null account numbers) will reach business logic and cause uncontrolled exceptions or corrupt data.

---

### GAP-SEC-03: CSRF Disabled Without Documentation
**Severity:** Low | **Effort:** Small

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF is disabled in the API Gateway's security configuration. While this is acceptable for a stateless JWT-based API, there is no documentation explaining this decision.

---

### GAP-SEC-04: Downstream Services Have No Authentication
**Severity:** High | **Effort:** Medium

Authentication is only enforced at the API Gateway level. Individual services (`core-banking-service`, `user-service`, `fund-transfer-service`, `utility-payment-service`) have no security filters or OAuth2 resource server configuration.

**Impact:** If any service is exposed directly (e.g., in a misconfigured network, or during development), all endpoints are unauthenticated. The `X-Auth-Id` header can be spoofed.

---

### GAP-SEC-05: Keycloak Client Singleton Is Not Thread-Safe
**Severity:** Medium | **Effort:** Small

```java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact:** Under concurrent load, multiple Keycloak instances could be created (benign but wasteful), or worse, a partially-constructed instance could be returned.

---

### GAP-SEC-06: No Dependency Vulnerability Scanning
**Severity:** Medium | **Effort:** Small

No OWASP Dependency-Check, Snyk, or Dependabot configuration exists. Dependencies include versions that may have known CVEs (e.g., specific Keycloak, MySQL connector versions).

---

## 5. API Design

### GAP-API-01: Inconsistent REST Resource Naming
**Severity:** Medium | **Effort:** Medium

| Service | Endpoint | Issue |
|---------|----------|-------|
| user-service | `/api/v1/bank-users/register` | Verb in URL (should be `POST /api/v1/bank-users`) |
| user-service | `/api/v1/bank-users/update/{id}` | Verb in URL (should be `PATCH /api/v1/bank-users/{id}`) |
| core-banking | `/api/v1/transaction/fund-transfer` | Mixed nouns and verbs |
| Postman vs Code | `/api/v1/bank-user` (Postman) vs `/api/v1/bank-users` (code) | Singular vs plural mismatch |

---

### GAP-API-02: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Small

Paginated endpoints accept `Pageable` parameters but return raw `List<T>` instead of a `Page<T>` wrapper with metadata (total elements, total pages, current page, etc.):

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

**Impact:** Clients cannot implement pagination UI because total count / page information is not returned.

---

### GAP-API-03: No API Versioning Strategy Beyond URL Prefix
**Severity:** Low | **Effort:** Small

All endpoints use `/api/v1/` prefix, which is fine. However, there is no documented versioning strategy, no header-based versioning support, and no plan for when `/v2` is needed.

---

### GAP-API-04: OpenAPI/Swagger Dependency Mismatch
**Severity:** Medium | **Effort:** Small

All four business services include `springdoc-openapi-starter-webflux-ui:2.1.0` — this is the **WebFlux** variant, but three of the four services use **Spring MVC** (only the API Gateway is reactive/WebFlux). The correct dependency for MVC services is `springdoc-openapi-starter-webmvc-ui`.

**Impact:** Swagger UI may not work correctly or at all in the MVC-based services.

---

### GAP-API-05: No Consistent Error Response Schema
**Severity:** Medium | **Effort:** Small

Custom exceptions use `ErrorResponse { code, message }`, but the catch-all handler returns a plain string: `"Exception occur inside API " + e`. Clients must handle two completely different error response formats.

---

## 6. Observability

### GAP-OBS-01: Actuator Endpoints Not Configured Beyond Defaults
**Severity:** Medium | **Effort:** Small

Spring Boot Actuator is included in all services, but there is no explicit configuration to expose useful endpoints (e.g., `/actuator/prometheus`, `/actuator/metrics`, `/actuator/env`, `/actuator/loggers`). Default configuration only exposes `/health` and `/info`.

---

### GAP-OBS-02: No Structured Logging
**Severity:** Medium | **Effort:** Medium

All services use default Spring Boot logging (Logback with pattern layout). There is no JSON log format configured, making log aggregation and querying in tools like ELK/Loki difficult.

---

### GAP-OBS-03: Log Statements Leak Potentially Sensitive Data
**Severity:** High | **Effort:** Small

```java
log.info("Creating user with {}", request.toString());
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

These log `toString()` output of DTOs that may contain sensitive data (email, account numbers, amounts). No log masking or redaction is applied.

---

### GAP-OBS-04: No Prometheus Metrics Endpoint
**Severity:** Medium | **Effort:** Small

README mentions Prometheus in the technology stack, but no `micrometer-registry-prometheus` dependency is included in any service. The `/actuator/prometheus` endpoint will not be available.

---

### GAP-OBS-05: No Health Check for External Dependencies
**Severity:** Medium | **Effort:** Medium

The default `/actuator/health` endpoint only checks basic JVM health. There are no custom health indicators for:
- MySQL connectivity
- Keycloak reachability
- Eureka registration status
- Zipkin collector availability

**Impact:** The health endpoint reports UP even when critical dependencies are down.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** High | **Effort:** Medium

No Resilience4j or Hystrix circuit breakers are configured on any Feign client or service call. If `core-banking-service` goes down:
- `fund-transfer-service` will hang until Feign timeout (default: none configured explicitly).
- `utility-payment-service` will hang similarly.
- `user-service` will hang on Keycloak and core-banking calls.

**Impact:** A single service failure cascades to all dependent services (cascading failure).

---

### GAP-RES-02: No Retry Policies
**Severity:** Medium | **Effort:** Small

No Spring Retry or Resilience4j retry is configured for any inter-service call. Transient network errors cause immediate failure.

---

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

No explicit Feign, HTTP client, or connection pool timeouts are configured. Services rely on JVM/OS defaults, which can be very long (30+ seconds).

**Impact:** Under partial failure, threads accumulate waiting for responses, eventually exhausting the thread pool and making the service unresponsive.

---

### GAP-RES-04: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

No fallback methods are defined for any Feign client call. When an inter-service call fails, the exception propagates directly to the caller with no graceful degradation.

---

### GAP-RES-05: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

The API Gateway has no rate limiting configuration. A single client could overwhelm the entire system with requests.

---

### GAP-RES-06: No Idempotency Protection on Financial Endpoints
**Severity:** Critical | **Effort:** Medium

Fund transfer and utility payment endpoints (`POST /api/v1/transfer`, `POST /api/v1/utility-payment`) have no idempotency key mechanism. If a client retries a request (e.g., due to network timeout), the same transaction could be processed multiple times.

**Impact:** Potential for duplicate financial transactions — a critical defect in any banking system.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Organization | No multi-project Gradle build | Medium | Medium |
| GAP-ORG-02 | Organization | Massive code duplication across services | High | Large |
| GAP-ORG-03 | Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Organization | No code formatter configured | Low | Small |
| GAP-ERR-01 | Error Handling | Catch-all handler leaks stack traces | Critical | Small |
| GAP-ERR-02 | Error Handling | Raw ResponseEntity types | Medium | Small |
| GAP-ERR-03 | Error Handling | Missing Feign error decoders | High | Medium |
| GAP-ERR-04 | Error Handling | No compensation for partial failures | Critical | Large |
| GAP-TEST-01 | Testing | Near-zero test coverage (5/6 services) | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-SEC-01 | Security | Hardcoded credentials in repo | Critical | Medium |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled without documentation | Low | Small |
| GAP-SEC-04 | Security | No auth on downstream services | High | Medium |
| GAP-SEC-05 | Security | Thread-unsafe Keycloak singleton | Medium | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | Inconsistent REST naming | Medium | Medium |
| GAP-API-02 | API Design | No pagination metadata | Medium | Small |
| GAP-API-03 | API Design | No versioning strategy documented | Low | Small |
| GAP-API-04 | API Design | Wrong Swagger dependency (WebFlux vs MVC) | Medium | Small |
| GAP-API-05 | API Design | Inconsistent error response schema | Medium | Small |
| GAP-OBS-01 | Observability | Actuator not configured beyond defaults | Medium | Small |
| GAP-OBS-02 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-03 | Observability | Logs leak sensitive data | High | Small |
| GAP-OBS-04 | Observability | No Prometheus metrics | Medium | Small |
| GAP-OBS-05 | Observability | No health checks for dependencies | Medium | Medium |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | Medium | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No rate limiting | Medium | Medium |
| GAP-RES-06 | Resilience | No idempotency on financial endpoints | Critical | Medium |

### Severity Distribution

- **Critical:** 6 (GAP-ERR-01, GAP-ERR-04, GAP-TEST-01, GAP-SEC-01, GAP-SEC-02, GAP-RES-06)
- **High:** 6 (GAP-ORG-02, GAP-ERR-03, GAP-TEST-02, GAP-SEC-04, GAP-OBS-03, GAP-RES-01, GAP-RES-03)
- **Medium:** 15
- **Low:** 4
