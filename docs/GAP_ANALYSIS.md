# Engineering Standards Gap Analysis

> **Assessment date:** 2026-05-07
> **Codebase:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0
> **Services assessed:** 6 microservices (Config Server, Service Registry, API Gateway, User Service, Fund Transfer Service, Utility Payment Service, Core Banking Service)

---

## Severity & Effort Definitions

| Severity | Definition |
|---|---|
| **Critical** | Security vulnerabilities, data integrity risks, or production blockers |
| **High** | Significant maintainability, reliability, or correctness concerns |
| **Medium** | Best-practice deviations that affect long-term quality |
| **Low** | Polish items and minor inconsistencies |

| Effort | Definition |
|---|---|
| **Small** | < 1 day per service, mostly configuration or boilerplate |
| **Medium** | 1-3 days, requires design decisions and moderate code changes |
| **Large** | 3+ days, requires architectural changes or significant refactoring |

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Module Build
**Severity: Medium | Effort: Medium**

Each service has its own independent Gradle project with duplicated wrapper files, plugin versions, and dependency declarations. Spring Boot `3.2.4`, Spring Cloud `2023.0.0`, and common dependencies like `mysql-connector-j:8.4.0` are repeated across all `build.gradle` files.

**Impact:** Version drift between services; one service could silently lag behind on a critical dependency update.

**Evidence:**
- `core-banking-service/build.gradle` line 3: `version '3.2.4'`
- `internet-banking-fund-transfer-service/build.gradle` line 3: `version '3.2.4'`
- Each service has its own `gradle/wrapper/` directory

### GAP-ORG-02: Duplicated Code Across Services (No Shared Library)
**Severity: High | Effort: Medium**

The following classes are copy-pasted (with slight variations) across 3-4 services:
- `AuditAware` (MappedSuperclass) — duplicated in fund-transfer, utility-payment, and user services
- `BaseMapper` — duplicated in all 4 business services
- `TransactionStatus` enum — duplicated in fund-transfer and utility-payment
- `ErrorResponse` — duplicated in all 4 business services with different constructors (builder vs. all-args)
- `SimpleBankingGlobalException` — duplicated in all 4 business services
- `GlobalExceptionHandler` — duplicated in all 4 business services
- `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter` — duplicated in fund-transfer, utility-payment, and user services
- `CustomFeignClientConfiguration` — duplicated in fund-transfer and utility-payment services

**Impact:** Bug fixes must be applied N times. Behavioral divergence is already present (e.g., `ErrorResponse` uses `@Builder` in some services and constructor in others).

### GAP-ORG-03: Inconsistent Package Structure
**Severity: Low | Effort: Small**

Package layout varies across services:
- Core Banking: `repository/` at top-level vs. `model.repository/` in other services
- User Service: `configuration.feign/` (with `CustomFeignErrorDecoder`) vs. other services with `configuration/` (flat)
- DTO package paths differ: `model.dto.request/response` (core, fund-transfer) vs. `model.rest.request/response` (utility-payment, user)

---

## 2. Error Handling

### GAP-ERR-01: Generic Catch-All Returns 400 for All Exceptions
**Severity: Critical | Effort: Small**

All four `GlobalExceptionHandler` implementations catch `Exception.class` and return `400 Bad Request` with the raw exception toString:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Impact:**
- Server errors (NPE, DB connection failures) return 400 instead of 500.
- Stack traces and internal details leak to API consumers.
- No structured error response for the generic handler (returns a plain string, not `ErrorResponse`).

### GAP-ERR-02: Missing HTTP Status Code Differentiation
**Severity: High | Effort: Small**

All custom exceptions map to `400 Bad Request` regardless of semantic meaning:
- `EntityNotFoundException` should return `404 Not Found`
- `InsufficientFundsException` could return `422 Unprocessable Entity`
- `UserAlreadyRegisteredException` could return `409 Conflict`

**Evidence:** All `GlobalExceptionHandler` classes use `ResponseEntity.badRequest()` for `SimpleBankingGlobalException`.

### GAP-ERR-03: No Error Handling for Feign Client Failures
**Severity: High | Effort: Medium**

- **Fund Transfer Service and Utility Payment Service:** No Feign error decoder configured. If Core Banking returns an error, the Feign client will throw a generic `FeignException` that is not properly handled.
- **User Service:** Has a `CustomFeignErrorDecoder` but it is only declared as a class — it is not wired into the Feign client configuration (`BankingCoreRestClient` uses `@FeignClient(name = "core-banking-service")` with no `configuration` attribute pointing to the error decoder).
- The `CustomFeignErrorDecoder` also creates a new `ObjectMapper` on every error, which is wasteful.

### GAP-ERR-04: Missing `@AllArgsConstructor` on `FundTransferRequest` in Core Banking
**Severity: Medium | Effort: Small**

`TransactionServiceTest` (line 183) constructs `FundTransferRequest` via `new FundTransferRequest("A1", "A2", BigDecimal.valueOf(100))`, but the class only has `@Data` (which does not generate an all-args constructor). This will cause a compilation error if the test is run.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage on 3 of 4 Business Services
**Severity: Critical | Effort: Large**

| Service | Test Files | Meaningful Tests |
|---|---|---|
| Core Banking | 3 (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) | ~17 unit tests |
| User Service | 1 (`InternetBankingUserServiceApplicationTests`) | 0 (empty context load test) |
| Fund Transfer | 1 (`InternetBankingFundTransferServiceApplicationTests`) | 0 (empty context load test) |
| Utility Payment | 1 (`InternetBankingUtilityPaymentServiceApplicationTests`) | 0 (empty context load test) |
| API Gateway | 1 (`InternetBankingApiGatewayApplicationTests`) | 0 (empty context load test) |
| Config Server | 1 (`InternetBankingConfigServerApplicationTests`) | 0 (empty context load test) |

**Impact:** No automated verification for user registration logic, Feign client behavior, fund transfer orchestration, or payment processing.

### GAP-TEST-02: No Integration Tests
**Severity: High | Effort: Large**

There are no:
- Spring Boot integration tests (`@SpringBootTest` with real context)
- Testcontainers-based database tests
- API-level tests (MockMvc / WebTestClient)
- End-to-end tests

### GAP-TEST-03: No Contract Tests Between Services
**Severity: Medium | Effort: Large**

Services communicate via Feign clients. There are no:
- Spring Cloud Contract tests
- Pact consumer/provider tests
- WireMock-based Feign client tests

**Impact:** Breaking API changes in Core Banking Service would not be caught until runtime.

### GAP-TEST-04: Context Load Tests Will Fail Without External Dependencies
**Severity: Low | Effort: Small**

The `ApplicationTests` classes in user, fund-transfer, and utility-payment services load the full Spring context, which requires MySQL, Eureka, Config Server, and (for user service) Keycloak. These tests will fail in any CI environment without Docker infrastructure.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code
**Severity: Critical | Effort: Small**

- `docker-compose.yml` line 52: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml` line 21-22: `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password`
- `privileges.sql` line 1: `IDENTIFIED BY 'oPItyPticIAt'`
- `Dockerfile` line 2: `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`
- `README.md` line 62: Test credentials exposed

**Impact:** Secrets committed to version control. If this were a production system, these would be compromised.

### GAP-SEC-02: No Input Validation on API Requests
**Severity: Critical | Effort: Small**

No request DTOs use Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Min`, `@Email`, etc.) or `@Valid`/`@Validated` on controller parameters.

**Evidence:**
- `FundTransferRequest`: No validation — `amount` could be null, negative, or zero
- `UtilityPaymentRequest`: No validation — `providerId` could be null
- `User` DTO: `email` is not validated, `password` has no length/complexity constraints
- Controllers use `@RequestBody` without `@Valid`

**Impact:** Null pointer exceptions, negative transfers, empty registrations can reach business logic.

### GAP-SEC-03: CSRF Disabled Without Documentation
**Severity: Medium | Effort: Small**

`SecurityConfiguration` disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While appropriate for a stateless REST API with JWT, this should be documented and the rationale recorded.

### GAP-SEC-04: Password Exposed in DTO Response
**Severity: High | Effort: Small**

The `User` DTO in the user service contains a `password` field. The same DTO is used for both request (registration) and response (read operations). The password field could be serialized and returned to clients in GET responses.

**Evidence:** `User.java` — `private String password;` with `@Data` (generates getter/setter).

### GAP-SEC-05: Keycloak Client Singleton is Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a check-then-act pattern on a static field without synchronization. In a multi-threaded environment, multiple Keycloak instances could be created.

### GAP-SEC-06: No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured. Dependency versions are pinned but never scanned for known CVEs.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters
**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
```

**Evidence:** `AccountController`, `TransactionController`, `UserController` (core banking), `FundTransferController`, `UtilityPaymentController` — nearly all return raw `ResponseEntity`.

**Exception:** User Service `UserController` correctly types `ResponseEntity<User>` and `ResponseEntity<List<User>>`.

**Impact:** No compile-time type safety; OpenAPI/Swagger documentation cannot infer response types.

### GAP-API-02: No API Versioning Strategy
**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` but there is no mechanism for versioning (header-based, URL-based) or deprecation policy. This is acceptable for now but should be planned.

### GAP-API-03: Inconsistent Pagination Across Services
**Severity: Medium | Effort: Small**

Pagination is handled via Spring's `Pageable` parameter (passed as query params), which works. However:
- Core Banking `UserController.readUsers()` returns `List<User>` instead of a `Page<User>` (loses total count, page metadata).
- Fund Transfer `readAllTransfers()` similarly returns `List<FundTransfer>`.
- No standard pagination envelope across services.

### GAP-API-04: No Filtering or Sorting Endpoints
**Severity: Low | Effort: Medium**

List endpoints accept `Pageable` but no filtering parameters (e.g., by date range, status, account number). This limits the API's usefulness for UI consumers.

### GAP-API-05: OpenAPI/Swagger Dependency Mismatch
**Severity: Medium | Effort: Small**

Services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but the business services (core banking, user, fund-transfer, utility-payment) are standard Spring MVC (not WebFlux) applications. They should use `springdoc-openapi-starter-webmvc-ui` instead. Only the API Gateway (which is WebFlux-based) should use the webflux variant.

**Impact:** The Swagger UI may not work correctly or may not be generated at all for MVC-based services.

### GAP-API-06: Swagger Annotations Present But Incomplete
**Severity: Low | Effort: Small**

Controllers have `@Tag` and `@Operation` annotations (good), but:
- No `@ApiResponse` annotations documenting error responses
- No `@Schema` annotations on DTOs
- No `@Parameter` annotations on path/query params

---

## 6. Observability

### GAP-OBS-01: Inconsistent Logging Practices
**Severity: Medium | Effort: Small**

- `FundTransferService` line 32: String concatenation instead of parameterized logging: `log.info("Sending fund transfer request {}" + request.toString())` — the `+` means the string is always built even when INFO is disabled, and the `{}` placeholder is not used correctly.
- Error logging in `CustomFeignErrorDecoder` uses string concatenation: `log.error("IO Exception..." + e)` — should use `log.error("...", e)` to preserve stack traces.
- `KeycloakUserService.readUser()` logs `e.toString()` instead of the exception itself.
- Unused `Logger` import in `KeycloakUserService` (`org.slf4j.Logger` + `LoggerFactory` imported but `@Slf4j` is used instead).

### GAP-OBS-02: No Structured Logging
**Severity: Medium | Effort: Medium**

All services use default Spring Boot logging (Logback with pattern layout). No JSON/structured logging is configured, making log aggregation and searching difficult in production.

### GAP-OBS-03: Health Check Endpoints Not Customized
**Severity: Low | Effort: Small**

Spring Boot Actuator is included in all services (`/actuator/health`), but:
- No custom health indicators for database connectivity, Keycloak availability, or downstream service health.
- Actuator endpoints exposed through the gateway are not secured (intentionally public per security config, but this should be reviewed).

### GAP-OBS-04: No Metrics Endpoints Beyond Default
**Severity: Medium | Effort: Medium**

Micrometer is included (via Actuator + tracing bridge), but:
- No custom business metrics (e.g., fund transfer count, payment success rate, registration rate).
- No Prometheus endpoint configured (Prometheus is listed in the tech stack but `micrometer-registry-prometheus` is not in any `build.gradle`).

### GAP-OBS-05: Distributed Tracing Configuration Not Verified
**Severity: Low | Effort: Small**

The tracing dependencies are present (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`), but no sampling rate or custom span configuration exists. Default sampling rate (10%) may miss important traces in low-traffic scenarios.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity: High | Effort: Medium**

All inter-service calls via Feign are fire-and-forget with no circuit breaker pattern. If Core Banking Service goes down:
- Fund Transfer Service will hang or fail every request.
- Utility Payment Service will hang or fail every request.
- User Service registration will fail.

No `spring-cloud-starter-circuitbreaker-resilience4j` or similar dependency is present.

### GAP-RES-02: No Retry Policies
**Severity: High | Effort: Small**

No retry configuration exists for Feign clients. Transient failures (network blips, temporary DB locks) will immediately fail the request.

No `spring-retry` or Resilience4j retry configuration.

### GAP-RES-03: No Timeout Configuration
**Severity: High | Effort: Small**

Feign client timeouts are not explicitly configured. Default Feign timeouts (10 seconds connect, 60 seconds read) are in effect, which is too long for a banking application.

No `feign.client.config.default.connectTimeout` or `readTimeout` settings.

### GAP-RES-04: No Fallback Behavior
**Severity: Medium | Effort: Medium**

When downstream services fail, there are no fallback responses. The API returns either a 400 (via the catch-all exception handler) or a 500 (if the exception handler itself fails).

Desired behavior: graceful degradation (e.g., cached account data, queued transfers).

### GAP-RES-05: No Rate Limiting
**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configuration. In a banking application, this is important to prevent abuse (e.g., brute-force on fund transfers).

Spring Cloud Gateway supports `RequestRateLimiter` filter, which is not configured.

### GAP-RES-06: Potential Data Inconsistency in Orchestration
**Severity: Critical | Effort: Large**

Fund Transfer and Utility Payment services follow a two-phase pattern:
1. Save `PENDING`/`PROCESSING` record locally.
2. Call Core Banking Service to execute the transaction.
3. Update local record to `SUCCESS`.

**Problem:** If the application crashes after step 2 but before step 3, the money has been moved in core banking but the local record remains `PENDING`. There is no:
- Saga pattern or compensation logic
- Idempotency keys to prevent duplicate transfers
- Scheduled job to reconcile pending records

### GAP-RES-07: Transaction Mapping Issue in Core Banking
**Severity: High | Effort: Small**

`TransactionEntity` uses `@OneToOne(cascade = CascadeType.ALL)` for the account relationship. This is semantically incorrect — an account has many transactions. The `CascadeType.ALL` means deleting a transaction would cascade-delete the bank account.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| GAP-ORG-01 | Code Organization | No multi-module build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Duplicated code across services | High | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ERR-01 | Error Handling | Generic catch-all returns 400 | Critical | Small |
| GAP-ERR-02 | Error Handling | Missing HTTP status code differentiation | High | Small |
| GAP-ERR-03 | Error Handling | No Feign error handling (fund-transfer, utility-payment) | High | Medium |
| GAP-ERR-04 | Error Handling | Missing constructor on FundTransferRequest | Medium | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage (3/4 services) | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-TEST-04 | Testing | Context load tests fail without infra | Low | Small |
| GAP-SEC-01 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Small |
| GAP-SEC-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-04 | Security | Password exposed in DTO response | High | Small |
| GAP-SEC-05 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-06 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without type params | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Medium |
| GAP-API-03 | API Design | Inconsistent pagination | Medium | Small |
| GAP-API-04 | API Design | No filtering or sorting | Low | Medium |
| GAP-API-05 | API Design | OpenAPI webflux dependency on MVC services | Medium | Small |
| GAP-API-06 | API Design | Incomplete Swagger annotations | Low | Small |
| GAP-OBS-01 | Observability | Inconsistent logging practices | Medium | Small |
| GAP-OBS-02 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-03 | Observability | Health checks not customized | Low | Small |
| GAP-OBS-04 | Observability | No custom metrics / Prometheus | Medium | Medium |
| GAP-OBS-05 | Observability | Tracing sampling not configured | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No rate limiting | Medium | Medium |
| GAP-RES-06 | Resilience | Data inconsistency in orchestration | Critical | Large |
| GAP-RES-07 | Resilience | Incorrect @OneToOne on TransactionEntity | High | Small |
