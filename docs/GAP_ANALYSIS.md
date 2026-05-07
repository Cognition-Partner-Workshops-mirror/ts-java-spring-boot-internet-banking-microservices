# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by severity and estimated remediation effort.

**Severity Levels:** Critical (production risk / security vulnerability), High (significant quality/maintainability issue), Medium (deviation from best practices), Low (polish / nice-to-have)

**Effort Levels:** Small (< 1 day), Medium (1-3 days), Large (> 3 days)

---

## 1. Code Organization

### GAP-ORG-01: No Shared Library — Massive Code Duplication
**Severity: High | Effort: Large**

Identical classes are copy-pasted across 3-4 services:
- `BaseMapper` — duplicated in core-banking, fund-transfer, user-service, utility-payment (identical code)
- `AuditAware` — duplicated in fund-transfer, user-service, utility-payment
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — duplicated in fund-transfer, user-service, utility-payment
- `AuditConfig` / `AuditorAwareConfig` — duplicated in fund-transfer, user-service, utility-payment
- `ErrorResponse` / `SimpleBankingGlobalException` / `GlobalExceptionHandler` — duplicated across all 4 business services
- DTO classes (`FundTransferRequest`, `UtilityPaymentRequest`, response classes) — duplicated between services

There is no shared library module. Any bug fix or improvement must be applied to every copy independently.

### GAP-ORG-02: No Multi-Module Gradle Build
**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with its own `settings.gradle` and `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds across services. This makes it impossible to:
- Build all services with a single command
- Share dependency versions via a BOM or version catalog
- Enforce consistent plugin and dependency versions

### GAP-ORG-03: Inconsistent Package Structure Across Services
**Severity: Low | Effort: Small**

Package organization differs between services:
- **Fund Transfer:** `model.dto.request`, `model.dto.response`, `model.repository`, `service.rest.client`
- **User Service:** `model.rest.response`, `model.repository`, `service.rest`
- **Utility Payment:** `model.rest.request`, `model.rest.response`, `repository` (not under `model`)
- **Core Banking:** `repository` (top-level), `model.dto.request`, `model.dto.response`

The naming of Feign clients is also inconsistent: `BankingCoreFeignClient` vs `BankingCoreRestClient`.

### GAP-ORG-04: Mappers Instantiated Manually Instead of Spring-Managed
**Severity: Low | Effort: Small**

All mappers (`FundTransferMapper`, `UserMapper`, `BankAccountMapper`, etc.) are instantiated with `new` inside service classes rather than being Spring beans. This prevents dependency injection, makes testing harder, and bypasses the Spring lifecycle.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400 Bad Request
**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). This is semantically incorrect:
- `EntityNotFoundException` should return **404 Not Found**
- `InsufficientFundsException` should return **422 Unprocessable Entity** or **409 Conflict**
- Generic `Exception` should return **500 Internal Server Error**

### GAP-ERR-02: Generic Exception Handler Leaks Internal Details
**Severity: Critical | Effort: Small**

The catch-all handler in every service returns the full exception toString:
```java
.body("Exception occur inside API " + e);
```
This exposes stack traces, class names, and potentially sensitive internal details to API consumers. In a banking application, this is a significant security risk.

### GAP-ERR-03: Inconsistent Error Response Format
**Severity: Medium | Effort: Small**

- `SimpleBankingGlobalException` handlers return `ErrorResponse` (structured JSON with `code` and `message`).
- The generic `Exception` handler returns a **raw string** (`"Exception occur inside API " + e`).

API consumers cannot reliably parse error responses because the format changes depending on the exception type.

### GAP-ERR-04: No Error Codes in Fund Transfer and Utility Payment Services
**Severity: Medium | Effort: Small**

The `GlobalErrorCode` class exists only in core-banking-service and user-service. Fund transfer and utility payment services have no structured error codes.

### GAP-ERR-05: Missing `@Transactional` on Fund Transfer Service
**Severity: High | Effort: Small**

`FundTransferService.fundTransfer()` saves the entity, calls the remote core banking API, then saves again. If the second save fails, the first save is already committed, leaving the entity in an inconsistent `PENDING` state. The core-banking-service `TransactionService` is correctly annotated with `@Transactional`, but the orchestrating fund transfer service is not.

Similarly, `UtilityPaymentService.utilPayment()` has the same issue.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage for Business Services
**Severity: Critical | Effort: Large**

Test files per service:
- **core-banking-service:** `CoreBankingServiceApplicationTests` (context load only) + `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` (unit tests with mocks — good coverage for this service)
- **fund-transfer-service:** `InternetBankingFundTransferServiceApplicationTests` (context load only — **no business logic tests**)
- **utility-payment-service:** `InternetBankingUtilityPaymentServiceApplicationTests` (context load only — **no business logic tests**)
- **user-service:** `InternetBankingUserServiceApplicationTests` (context load only — **no business logic tests**)
- **api-gateway:** `InternetBankingApiGatewayApplicationTests` (context load only)
- **config-server:** `InternetBankingConfigServerApplicationTests` (context load only)
- **service-registry:** `InternetBankingServiceRegistryApplicationTests` (context load only)

Only core-banking-service has meaningful unit tests. The user-service (with complex Keycloak integration), fund-transfer-service, and utility-payment-service have **zero** business logic tests.

### GAP-TEST-02: No Integration Tests
**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller-level request/response mapping (MockMvc / WebTestClient)
- Repository queries against a real database (H2 is included as a test dependency but unused)
- End-to-end flows through the API gateway

### GAP-TEST-03: No Contract Tests Between Services
**Severity: High | Effort: Large**

Services communicate via Feign clients, but there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). Breaking changes in core-banking-service APIs could silently break downstream consumers.

### GAP-TEST-04: Context Load Tests Likely Fail Without Infrastructure
**Severity: Medium | Effort: Small**

Most `@SpringBootTest` context load tests will fail without MySQL, Keycloak, and the Config Server running, making them unusable in CI without Docker Compose or Testcontainers.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Docker Compose and Source
**Severity: Critical | Effort: Small**

The following credentials are hardcoded in version-controlled files:
- **MySQL root password:** `woVERANKliGharym` (in `docker-compose.yml` and `docker-compose/mysql/Dockerfile`)
- **MySQL app user:** `javatodev_development` / `oPItyPticIAt` (in `privileges.sql`)
- **Keycloak admin:** `admin` / `password` (in `docker-compose.yml`)
- **Keycloak DB:** `keycloak` / `password` (in `docker-compose.yml`)
- **Test credentials:** `ib_admin@javatodev.com` / `5V7huE3G86uB` (in `README.md`)

For a banking application, even development credentials should be externalized to environment variables or a secrets manager.

### GAP-SEC-02: No Input Validation on Any API Endpoint
**Severity: Critical | Effort: Medium**

No controller method uses Jakarta Bean Validation annotations (`@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Email`, etc.) on request bodies or path variables. This means:
- Fund transfers can be initiated with null/negative amounts
- Users can register with null/empty emails
- Account numbers are not validated before database lookups

### GAP-SEC-03: CSRF Disabled Without Documentation
**Severity: Medium | Effort: Small**

The API Gateway explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a stateless JWT API, this should be documented and accompanied by other protections (e.g., rate limiting, CORS configuration).

### GAP-SEC-04: Keycloak Client Instance Uses Static Singleton
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static mutable singleton that is never refreshed. If credentials are rotated or the Keycloak server is restarted with a new configuration, the application must be restarted. Additionally, the static field is not thread-safe (no synchronization).

### GAP-SEC-05: No Rate Limiting
**Severity: High | Effort: Medium**

There is no rate limiting at the API Gateway or service level. Financial APIs (especially fund transfer and payment endpoints) are vulnerable to abuse.

### GAP-SEC-06: Registration Endpoint Permits Unauthenticated Access Without Throttling
**Severity: High | Effort: Small**

`/user/api/v1/bank-users/register` is explicitly permitted without authentication in `SecurityConfiguration`. Without rate limiting, this is vulnerable to account enumeration and brute-force registration attacks.

### GAP-SEC-07: Password Handling Concerns
**Severity: Medium | Effort: Small**

The `User` DTO in user-service includes a `password` field that flows through the request/response cycle. While it's sent to Keycloak for credential creation, the DTO is also returned in the response body from the register endpoint — potentially exposing the password in logs and API responses.

### GAP-SEC-08: No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

There is no OWASP Dependency Check, Snyk, or similar vulnerability scanning configured in the build pipeline. The `springdoc-openapi-starter-webflux-ui` version (2.1.0) is significantly outdated relative to current releases.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Generic Type Parameters
**Severity: Medium | Effort: Small**

Most controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`. This:
- Loses compile-time type safety
- Prevents OpenAPI/Swagger from automatically generating response schemas
- Makes API documentation incomplete

Examples: `AccountController.getBankAccount()`, `TransactionController.fundTransfer()`, `UserController.readUsers()` (partially typed).

### GAP-API-02: No API Versioning Strategy
**Severity: Medium | Effort: Medium**

While all endpoints use `/api/v1/`, there is no documented versioning strategy or mechanism for introducing breaking changes. No content negotiation or header-based versioning is configured.

### GAP-API-03: No Pagination Metadata in Responses
**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return `List<T>` — stripping all pagination metadata (total elements, total pages, current page, size). Clients cannot implement proper pagination without this metadata.

### GAP-API-04: No OpenAPI Configuration or Customization
**Severity: Low | Effort: Small**

While `springdoc-openapi` is included as a dependency and basic `@Tag` and `@Operation` annotations are present, there is no `@OpenAPIDefinition` or centralized OpenAPI configuration (title, version, description, servers, security schemes). The auto-generated docs lack context.

### GAP-API-05: Inconsistent Request/Response Naming
**Severity: Low | Effort: Small**

- Fund transfer service has both `FundTransferRequest` and `UtilityPaymentRequest` (the latter is unused in this service).
- `UtilityPaymentResponse` in fund-transfer service is an empty class.
- `AccountResponse` differs between services (user-service uses `Integer id`; fund-transfer uses `Long id`).

### GAP-API-06: No HATEOAS or Hypermedia Links
**Severity: Low | Effort: Medium**

REST responses contain no links to related resources. For example, a fund transfer response doesn't link to the transaction details or account balances.

---

## 6. Observability

### GAP-OBS-01: No Custom Health Checks
**Severity: Medium | Effort: Small**

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. There are no custom health checks for:
- Downstream service availability (core-banking from fund-transfer/user/utility-payment)
- Keycloak connectivity (user-service)
- MySQL connectivity is auto-detected, but custom thresholds or degraded states are not configured

### GAP-OBS-02: Inconsistent Logging Patterns
**Severity: Medium | Effort: Small**

- Some log statements use string concatenation: `log.info("Sending fund transfer request {}" + request.toString())` — this is a bug (the `{}` placeholder is never substituted; the `+` concatenation happens regardless of log level).
- Other statements correctly use parameterized logging: `log.info("Reading account by ID {}", accountNumber)`.
- Error logging inconsistently uses `e.toString()` vs `e` as a parameter.
- No structured logging (JSON format) is configured for production use.

### GAP-OBS-03: No Metrics Endpoints or Custom Metrics
**Severity: Medium | Effort: Medium**

While `micrometer-tracing-bridge-brave` is included for tracing, there are no custom business metrics:
- No counter for fund transfers processed/failed
- No histogram for transaction amounts
- No gauge for active transactions
- Prometheus integration is mentioned in the README but no `micrometer-registry-prometheus` dependency exists.

### GAP-OBS-04: Zipkin Tracing Configuration Is Externalized but Unverified
**Severity: Low | Effort: Small**

Tracing dependencies are present, but the Zipkin endpoint URL and sampling rate are configured via the external Config Server. There's no fallback or verification that tracing is actually working.

### GAP-OBS-05: Sensitive Data in Log Statements
**Severity: High | Effort: Small**

Multiple log statements include full `toString()` of request objects:
- `log.info("Creating user with {}", request.toString())` — logs user registration data (may include password)
- `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` — logs account numbers and amounts
- `log.info("Incoming Request From {}", userAuthId)` — logs authentication IDs

In a banking application, this constitutes a data leak risk.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers on Feign Clients
**Severity: Critical | Effort: Medium**

All inter-service communication uses OpenFeign without any circuit breaker (Resilience4j, Hystrix). If core-banking-service becomes unavailable:
- Fund transfer requests will block until timeout, cascading failures to the API gateway
- Utility payment requests will similarly fail without graceful degradation
- User registration will fail silently

### GAP-RES-02: No Retry Policies
**Severity: High | Effort: Small**

No retry configuration exists on any Feign client. Transient network issues or momentary service unavailability will immediately fail requests rather than retrying.

### GAP-RES-03: No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeout configuration exists for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Keycloak admin client timeouts

Default timeouts (often 30-60 seconds) are too long for a banking API and can lead to thread exhaustion under load.

### GAP-RES-04: No Fallback Behavior
**Severity: Medium | Effort: Medium**

No service implements fallback responses. When a downstream service is unavailable, users receive raw error messages rather than graceful degradation (e.g., cached responses, queue-and-retry, informative error messages).

### GAP-RES-05: No Database Connection Pooling Configuration
**Severity: Medium | Effort: Small**

No HikariCP (Spring Boot's default pool) configuration is visible. Default pool settings (max 10 connections) may be insufficient under load. No connection validation queries or leak detection is configured.

### GAP-RES-06: Fund Transfer Has No Compensation/Rollback Mechanism
**Severity: High | Effort: Large**

`FundTransferService.fundTransfer()`:
1. Saves entity as `PENDING` (committed to DB).
2. Calls core-banking via Feign.
3. If Feign call succeeds, updates entity to `SUCCESS`.

If step 3 fails (network error after core-banking processed the transfer), the local record stays `PENDING` but the money has already moved. There is no saga pattern, compensation transaction, or reconciliation mechanism.

The same issue exists in `UtilityPaymentService`.

### GAP-RES-07: `wait-for-it.sh` with Fixed Timeouts
**Severity: Low | Effort: Small**

Docker Compose uses `wait-for-it.sh` with 50-second timeouts for startup ordering. This is fragile — if infrastructure takes longer to start, services will fail. Spring Boot's built-in retry or a healthcheck-based approach would be more robust.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No shared library — massive code duplication | High | Large |
| GAP-ORG-02 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Mappers not Spring-managed | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic exception handler leaks internals | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-04 | Error Handling | No error codes in some services | Medium | Small |
| GAP-ERR-05 | Error Handling | Missing `@Transactional` on orchestrating services | High | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage for business services | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests between services | High | Large |
| GAP-TEST-04 | Testing | Context load tests fail without infrastructure | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-05 | Security | No rate limiting | High | Medium |
| GAP-SEC-06 | Security | Registration endpoint unthrottled | High | Small |
| GAP-SEC-07 | Security | Password exposed in response DTO | Medium | Small |
| GAP-SEC-08 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity types | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-03 | API Design | No pagination metadata | Medium | Small |
| GAP-API-04 | API Design | No OpenAPI configuration | Low | Small |
| GAP-API-05 | API Design | Inconsistent naming | Low | Small |
| GAP-API-06 | API Design | No HATEOAS links | Low | Medium |
| GAP-OBS-01 | Observability | No custom health checks | Medium | Small |
| GAP-OBS-02 | Observability | Inconsistent logging | Medium | Small |
| GAP-OBS-03 | Observability | No custom metrics | Medium | Medium |
| GAP-OBS-04 | Observability | Tracing config unverified | Low | Small |
| GAP-OBS-05 | Observability | Sensitive data in logs | High | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No connection pool config | Medium | Small |
| GAP-RES-06 | Resilience | No compensation/rollback for distributed transactions | High | Large |
| GAP-RES-07 | Resilience | Fragile Docker startup ordering | Low | Small |

**Totals by Severity:**
- Critical: 6
- High: 13
- Medium: 15
- Low: 7
