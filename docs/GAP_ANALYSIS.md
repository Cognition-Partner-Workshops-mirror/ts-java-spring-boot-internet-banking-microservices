# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### GAP-ORG-01: No Shared Library / Multi-Module Build

**Severity:** Medium | **Effort:** Medium

**Finding:** Each microservice is a standalone Gradle project with no shared parent build or common library. Identical classes are duplicated across services:
- `ErrorResponse` — copied in `core-banking-service`, `internet-banking-user-service`, `internet-banking-fund-transfer-service`, `internet-banking-utility-payment-service`
- `SimpleBankingGlobalException` — copied in all four business services
- `GlobalExceptionHandler` — copied (with minor variations) in all four business services
- `AuditAware` — copied in user, fund-transfer, and utility-payment services
- `BaseMapper<E, D>` — copied in all four business services
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` — copied in user, fund-transfer, and utility-payment services

**Best Practice:** Use a Gradle multi-module build with a `shared-library` module containing common DTOs, exceptions, mappers, and filters.

---

### GAP-ORG-02: Inconsistent Package Structure Across Services

**Severity:** Low | **Effort:** Small

**Finding:** Package layouts are similar but not identical:
- Core banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- User service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `exception`, `configuration.keycloak`, `configuration.feign`, `configuration.filter`, `configuration.audit`
- Fund transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.dto.request`, `model.dto.response`, `service`, `service.rest.client`, `controller`, `exception`, `configuration`, `configuration.audit`, `configuration.filter`
- Utility payment: `model.dto`, `model.entity`, `model.mapper`, `model.rest.request`, `model.rest.response`, `repository` (not `model.repository`), `service`, `service.rest`, `controller`, `exception`, `configuration`, `configuration.audit`, `configuration.filter`

**Best Practice:** Standardize on a single package convention across all services (e.g., `controller`, `service`, `repository`, `model.entity`, `model.dto`, `model.mapper`, `exception`, `configuration`).

---

### GAP-ORG-03: Mapper Instantiation via `new` Instead of Spring DI

**Severity:** Low | **Effort:** Small

**Finding:** All mappers are instantiated directly in service classes rather than managed by Spring:
```java
private UserMapper userMapper = new UserMapper();           // UserService
private BankAccountMapper bankAccountMapper = new BankAccountMapper(); // AccountService
private FundTransferMapper mapper = new FundTransferMapper();  // FundTransferService
```

**Best Practice:** Either annotate mappers with `@Component` and inject them, or use a mapping library like MapStruct that integrates with Spring.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400 Bad Request

**Severity:** High | **Effort:** Small

**Finding:** Every `GlobalExceptionHandler` across all services returns `ResponseEntity.badRequest()` (HTTP 400) for every exception type, including:
- `EntityNotFoundException` — should return **404 Not Found**
- `InsufficientFundsException` — should return **422 Unprocessable Entity** or **409 Conflict**
- Generic `Exception` — should return **500 Internal Server Error**
- `UserAlreadyRegisteredException` — should return **409 Conflict**
- `InvalidEmailException` — 400 is acceptable
- `InvalidBankingUserException` — should return **404 Not Found**

**Best Practice:** Map exception types to appropriate HTTP status codes.

---

### GAP-ERR-02: Generic Exception Handler Leaks Stack Trace

**Severity:** Critical | **Effort:** Small

**Finding:** The catch-all exception handler in every service returns the raw exception object as a string:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

This exposes internal class names, stack traces, and potentially sensitive information (database connection strings, SQL queries, internal IPs) to API consumers.

**Best Practice:** Return a generic error message to the client and log the full exception server-side. Use the structured `ErrorResponse` format consistently.

---

### GAP-ERR-03: Inconsistent Error Response Format

**Severity:** Medium | **Effort:** Small

**Finding:** Business exceptions return structured `ErrorResponse { code, message }`, but generic exceptions return a plain string. The fund transfer service uses `new ErrorResponse(code, message)` while others use `ErrorResponse.builder().code(...).message(...).build()`. Clients cannot reliably parse error responses.

**Best Practice:** Always return the same `ErrorResponse` structure for all error types, including a timestamp and request path.

---

### GAP-ERR-04: Missing Raw Type Parameterization on ResponseEntity

**Severity:** Low | **Effort:** Small

**Finding:** Most controller methods return raw `ResponseEntity` without type parameters:
```java
public ResponseEntity getBankAccount(...)   // core-banking AccountController
public ResponseEntity fundTransfer(...)     // core-banking TransactionController
public ResponseEntity sendFundTransfer(...) // fund-transfer FundTransferController
```

Only the user service controller consistently uses typed `ResponseEntity<User>`.

**Best Practice:** Always parameterize `ResponseEntity<T>` for type safety and accurate OpenAPI documentation.

---

### GAP-ERR-05: No Feign Error Handling in Fund Transfer or Utility Payment Services

**Severity:** High | **Effort:** Medium

**Finding:** The fund transfer and utility payment services call core banking via Feign but have no error decoder configured. The user service has a `CustomFeignErrorDecoder`, but fund transfer and utility payment services only configure `CustomFeignClientConfiguration` for request interceptors. If core banking returns an error, Feign will throw a generic `FeignException` which is caught by the catch-all handler and returns a leaked stack trace.

**Best Practice:** Configure a `FeignErrorDecoder` in all Feign clients to translate downstream HTTP errors into meaningful domain exceptions.

---

## 3. Testing

### GAP-TST-01: Only Core Banking Service Has Unit Tests

**Severity:** High | **Effort:** Large

**Finding:** Test coverage is concentrated entirely in `core-banking-service`:
- `AccountServiceTest` — 6 tests covering read operations
- `TransactionServiceTest` — 9 tests covering fund transfer and utility payment logic
- `UserServiceTest` — 3 tests covering read operations

The following services have **zero meaningful tests** (only the auto-generated Spring context load test):
- `internet-banking-user-service` — `InternetBankingUserServiceApplicationTests` (empty context test)
- `internet-banking-fund-transfer-service` — `InternetBankingFundTransferServiceApplicationTests` (empty context test)
- `internet-banking-utility-payment-service` — `InternetBankingUtilityPaymentServiceApplicationTests` (empty context test)
- `internet-banking-api-gateway` — `InternetBankingApiGatewayApplicationTests` (empty context test)
- `internet-banking-config-server` — `InternetBankingConfigServerApplicationTests` (empty context test)
- `internet-banking-service-registry` — `InternetBankingServiceRegistryApplicationTests` (empty context test)

**Best Practice:** Each service should have unit tests for its service layer, controller tests (MockMvc/WebTestClient), and the orchestration services (fund-transfer, utility-payment, user) need tests for their Feign integration logic.

---

### GAP-TST-02: No Integration Tests

**Severity:** High | **Effort:** Large

**Finding:** There are no integration tests that start the Spring context with an embedded database, test real JPA repository behavior, or verify controller endpoint wiring. The existing unit tests use Mockito mocks exclusively, and the context-loading tests will fail without Config Server and Eureka running.

**Best Practice:** Use `@SpringBootTest` with test profiles, H2 in-memory database (already in test dependencies), and `@MockBean` for external dependencies (Feign clients, Keycloak) to create integration tests.

---

### GAP-TST-03: No Contract Tests Between Services

**Severity:** Medium | **Effort:** Large

**Finding:** There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between the services. Changes to core banking API endpoints could silently break the fund transfer, utility payment, and user services.

**Best Practice:** Implement contract tests for the Feign client interfaces to verify API compatibility between producer and consumer.

---

### GAP-TST-04: No Test Coverage Tooling

**Severity:** Medium | **Effort:** Small

**Finding:** No JaCoCo or other coverage plugins are configured in any `build.gradle`. There is no visibility into which code paths are tested.

**Best Practice:** Add the JaCoCo Gradle plugin and configure minimum coverage thresholds.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Docker Compose and Source Code

**Severity:** Critical | **Effort:** Small

**Finding:** Production-adjacent credentials are hardcoded in version-controlled files:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`, `POSTGRES_PASSWORD: password`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`
- `KeycloakProperties.java`: Keycloak `clientSecret` is loaded from config but the config source (Git repo) may expose it

**Best Practice:** Use environment variables or a secrets manager (e.g., Docker secrets, HashiCorp Vault) for all credentials. Use `.env` files (excluded from VCS) for local development.

---

### GAP-SEC-02: No Input Validation on Request DTOs

**Severity:** Critical | **Effort:** Small

**Finding:** None of the request DTOs use Jakarta Validation annotations. Controllers accept raw `@RequestBody` without `@Valid`:
- `FundTransferRequest` — no validation on `amount` (could be null, negative, or zero)
- `UtilityPaymentRequest` — no validation on `amount`, `providerId`, `referenceNumber`, or `account`
- `User` (registration) — no validation on `email` format, `password` strength, or `identification` format

This means negative amounts, empty strings, and null fields will propagate into business logic and database operations.

**Best Practice:** Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email`, `@Size` annotations to DTOs and `@Valid` on controller method parameters.

---

### GAP-SEC-03: CSRF Disabled Without Documentation

**Severity:** Low | **Effort:** Small

**Finding:** The API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While this is standard for stateless JWT-based APIs, there is no documentation explaining this decision.

**Best Practice:** Add a code comment explaining why CSRF is disabled (stateless API, JWT-based authentication).

---

### GAP-SEC-04: Keycloak Singleton is Not Thread-Safe

**Severity:** Medium | **Effort:** Small

**Finding:** `KeycloakProperties.getInstance()` uses a non-synchronized static singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

In a multi-threaded Spring Boot application, this can result in multiple Keycloak instances being created or the instance being returned before it's fully initialized.

**Best Practice:** Use double-checked locking with `volatile`, or leverage Spring's `@Bean` lifecycle to create a singleton Keycloak instance.

---

### GAP-SEC-05: No Dependency Vulnerability Scanning

**Severity:** Medium | **Effort:** Small

**Finding:** No dependency vulnerability scanning is configured. There is no OWASP Dependency-Check Gradle plugin, no Snyk configuration, and no GitHub Dependabot alerts configured. The project uses specific versions for some dependencies (e.g., `mysql-connector-j:8.4.0`, `keycloak-admin-client:24.0.4`) which may contain known CVEs.

**Best Practice:** Add the OWASP Dependency-Check Gradle plugin or configure Dependabot/Snyk for automated vulnerability scanning.

---

### GAP-SEC-06: User Registration Endpoint Has No Rate Limiting

**Severity:** Medium | **Effort:** Medium

**Finding:** The `/user/api/v1/bank-users/register` endpoint is publicly accessible (permit-all) with no rate limiting, CAPTCHA, or abuse protection. This could be exploited for account enumeration or denial-of-service attacks against Keycloak.

**Best Practice:** Add rate limiting at the API Gateway level (e.g., Spring Cloud Gateway `RequestRateLimiter` filter with Redis).

---

## 5. API Design

### GAP-API-01: Inconsistent URL Naming Conventions

**Severity:** Low | **Effort:** Small

**Finding:** URL path segments use inconsistent naming:
- Snake case: `/bank-account/{account_number}`, `/util-account/{account_name}` (path variables)
- Kebab case: `/bank-users`, `/fund-transfer`, `/utility-payment` (resources)
- Abbreviations: `/util-account`, `/util-payment` (inconsistent — sometimes `utility`, sometimes `util`)

**Best Practice:** Use consistent kebab-case for path segments and camelCase or snake_case for path variables (pick one and standardize).

---

### GAP-API-02: No API Versioning Strategy

**Severity:** Low | **Effort:** Small

**Finding:** All endpoints include `/api/v1/` in their path, which is good. However, there is no documented versioning strategy, no plan for v2, and no mechanism for deprecation or sunset headers.

**Best Practice:** Document the API versioning strategy and plan for backward-compatible evolution.

---

### GAP-API-03: No Pagination Metadata in List Responses

**Severity:** Medium | **Effort:** Small

**Finding:** List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, `GET /api/v1/bank-users`, `GET /api/v1/user`) accept `Pageable` parameters but return raw `List<T>` instead of a paginated response wrapper. Clients have no way to know total count, total pages, or whether more pages exist.

**Best Practice:** Return a pagination wrapper (e.g., Spring's `Page<T>` or a custom `PaginatedResponse { content, totalElements, totalPages, page, size }`).

---

### GAP-API-04: OpenAPI Documentation Is Partially Configured

**Severity:** Medium | **Effort:** Small

**Finding:** All business services include `springdoc-openapi-starter-webflux-ui:2.1.0`, and controllers use `@Tag` and `@Operation` annotations. However:
- The wrong starter is used: `springdoc-openapi-starter-webflux-ui` is for WebFlux applications, but the business services use Spring MVC (`spring-boot-starter-web`). This may cause issues.
- Response schemas are not documented (raw `ResponseEntity` without type parameter means OpenAPI cannot infer the response type).
- Error responses are not documented with `@ApiResponse` annotations.
- There is no unified API documentation across all services (no gateway-level aggregation).

**Best Practice:** Use `springdoc-openapi-starter-webmvc-ui` for MVC services. Add `@ApiResponse` annotations. Consider gateway-level OpenAPI aggregation.

---

### GAP-API-05: No Filtering or Sorting Parameters Documented

**Severity:** Low | **Effort:** Small

**Finding:** List endpoints accept Spring Data's `Pageable` (which supports `page`, `size`, `sort` query parameters) but this is not documented in OpenAPI annotations or README. Consumers must guess the available query parameters.

**Best Practice:** Document supported query parameters explicitly or add `@Parameter` annotations.

---

## 6. Observability

### GAP-OBS-01: Logging is Inconsistent Across Services

**Severity:** Medium | **Effort:** Small

**Finding:**
- Some controllers log incoming requests (`UserController`, `FundTransferController`), others do not (`AccountController`'s `getUtilityAccount` has a typo: "Reading utitlity account").
- Log levels are inconsistent: all logging is at `INFO` level, including what should be `DEBUG` (request payloads) or `ERROR` (exceptions).
- The generic exception handler does not log exceptions at all — it only returns them to the client.
- `toString()` is called on request objects in log statements, which may log sensitive data (passwords in `User` DTO during registration).

**Best Practice:** Establish a logging standard: log request entry/exit at DEBUG, business events at INFO, errors at ERROR. Never log passwords or sensitive fields.

---

### GAP-OBS-02: Health Check Endpoints Are Not Customized

**Severity:** Low | **Effort:** Small

**Finding:** All services include `spring-boot-starter-actuator`, and the gateway permits actuator endpoints. However, there is no custom health indicator configuration to verify:
- Database connectivity (auto-configured by Spring Boot, but not explicitly verified)
- Keycloak connectivity (user service)
- Downstream service health (Feign client targets)

**Best Practice:** Add custom health indicators for critical dependencies and configure actuator to expose readiness and liveness probes.

---

### GAP-OBS-03: No Structured Logging Format

**Severity:** Medium | **Effort:** Small

**Finding:** All services use default Logback console logging with no structured format (JSON). In a containerized deployment, unstructured logs are difficult to parse and index in log aggregation systems (ELK, Datadog, CloudWatch).

**Best Practice:** Configure JSON logging format using `logstash-logback-encoder` or Spring Boot's built-in structured logging support.

---

### GAP-OBS-04: No Metrics Endpoints Beyond Default Actuator

**Severity:** Low | **Effort:** Medium

**Finding:** While Micrometer is included for tracing, there are no custom business metrics (e.g., transfer count, payment amounts, registration rate). The Prometheus endpoint is mentioned in the README tech stack but not configured in build files.

**Best Practice:** Add Micrometer metrics for key business operations and expose a `/actuator/prometheus` endpoint.

---

### GAP-OBS-05: Distributed Tracing Has No Sampling Configuration

**Severity:** Low | **Effort:** Small

**Finding:** Zipkin tracing is enabled but sampling rate is not explicitly configured. The default may be 10% in production, which could miss important traces, or 100% which could overwhelm Zipkin.

**Best Practice:** Explicitly configure `management.tracing.sampling.probability` (e.g., 1.0 for dev, 0.1 for production).

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers on Feign Clients

**Severity:** Critical | **Effort:** Medium

**Finding:** All inter-service communication uses OpenFeign without any circuit breaker. If core banking service becomes unavailable:
- Fund transfer service will hang or throw `FeignException` for every request
- Utility payment service will do the same
- User service registration will fail for every attempt

There is no `spring-cloud-starter-circuitbreaker-resilience4j` dependency in any service.

**Best Practice:** Add Resilience4j circuit breaker integration with Feign. Configure open/half-open/closed thresholds.

---

### GAP-RES-02: No Retry Policies on Feign Clients

**Severity:** High | **Effort:** Small

**Finding:** No retry configuration exists for Feign clients. Transient network errors (connection resets, DNS hiccups) will immediately fail requests. Spring Cloud's `Retryer.NEVER_RETRY` is the default for Feign.

**Best Practice:** Configure Feign retry with `Retryer.Default` or Resilience4j retry with appropriate max attempts and backoff.

---

### GAP-RES-03: No Timeout Configuration

**Severity:** High | **Effort:** Small

**Finding:** No explicit timeout configuration for:
- Feign client connection and read timeouts (defaults to infinite in some configurations)
- Database connection pool timeouts (using default HikariCP settings)
- Keycloak admin client timeouts (user service)

A slow or unresponsive core banking service could block all threads in upstream services.

**Best Practice:** Configure explicit timeouts for all external calls:
```yaml
spring.cloud.openfeign.client.config.default.connect-timeout: 5000
spring.cloud.openfeign.client.config.default.read-timeout: 10000
```

---

### GAP-RES-04: No Fallback Behavior for Service Failures

**Severity:** Medium | **Effort:** Medium

**Finding:** When a Feign call fails, the exception propagates directly to the client. There is no:
- Fallback response (e.g., cached data, degraded response)
- Graceful degradation (e.g., user listing without Keycloak enrichment)
- Partial success handling (e.g., local record saved but core banking call failed)

In the fund transfer and utility payment services, a failure after the local entity is saved with `PENDING`/`PROCESSING` status will leave orphaned records with no mechanism to retry or reconcile.

**Best Practice:** Implement Feign fallbacks, add a reconciliation mechanism for orphaned transactions, and consider using the Saga pattern for distributed transactions.

---

### GAP-RES-05: Fund Transfer Is Not Idempotent

**Severity:** High | **Effort:** Medium

**Finding:** The fund transfer endpoint has no idempotency key. If a client retries a failed request (due to timeout or network error), the transfer may execute twice. The `transactionId` is generated server-side as a UUID, so there is no way for the client to prevent duplicate processing.

**Best Practice:** Accept a client-provided idempotency key (e.g., `X-Idempotency-Key` header) and check for duplicate requests before processing.

---

### GAP-RES-06: Transaction Entity Relationship is Incorrect (@OneToOne instead of @ManyToOne)

**Severity:** High | **Effort:** Small

**Finding:** `TransactionEntity` uses `@OneToOne(cascade = CascadeType.ALL)` for its relationship to `BankAccountEntity`:
```java
@OneToOne(cascade = CascadeType.ALL)
@JoinColumn(name = "account_id", referencedColumnName = "id")
private BankAccountEntity account;
```

This is logically incorrect — one bank account can have many transactions. The `@OneToOne` with `CascadeType.ALL` means:
- Deleting a transaction would cascade-delete the bank account
- JPA may enforce uniqueness on the `account_id` column, preventing multiple transactions per account

**Best Practice:** Change to `@ManyToOne` and remove `CascadeType.ALL`.

---

### GAP-RES-07: Balance Calculation Bug — Double Subtraction/Addition

**Severity:** Critical | **Effort:** Small

**Finding:** In `TransactionService.internalFundTransfer()`:
```java
// Debit
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ↑ actualBalance already subtracted, subtracting again from it

// Credit
toBankAccountEntity.setActualBalance(toBankAccountEntity.getActualBalance().add(amount));
toBankAccountEntity.setAvailableBalance(toBankAccountEntity.getActualBalance().add(amount));
// ↑ actualBalance already added, adding again to it
```

The same bug exists in `utilPayment()`. This causes `availableBalance` to diverge from `actualBalance` after every transaction.

**Best Practice:** Set `availableBalance = actualBalance` after updating the actual balance, or track holds/pending amounts separately.

---

## Summary Matrix

| ID | Category | Gap Description | Severity | Effort |
|----|----------|----------------|----------|--------|
| GAP-ORG-01 | Code Organization | No shared library / multi-module build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-03 | Code Organization | Mapper instantiation via `new` | Low | Small |
| GAP-ERR-01 | Error Handling | All exceptions return HTTP 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic exception handler leaks stack trace | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-04 | Error Handling | Raw `ResponseEntity` without type params | Low | Small |
| GAP-ERR-05 | Error Handling | No Feign error handling in 2 services | High | Medium |
| GAP-TST-01 | Testing | Only core banking has unit tests | High | Large |
| GAP-TST-02 | Testing | No integration tests | High | Large |
| GAP-TST-03 | Testing | No contract tests between services | Medium | Large |
| GAP-TST-04 | Testing | No test coverage tooling | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-02 | Security | No input validation on DTOs | Critical | Small |
| GAP-SEC-03 | Security | CSRF disabled without docs | Low | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | Medium | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-06 | Security | No rate limiting on registration | Medium | Medium |
| GAP-API-01 | API Design | Inconsistent URL naming | Low | Small |
| GAP-API-02 | API Design | No documented versioning strategy | Low | Small |
| GAP-API-03 | API Design | No pagination metadata in responses | Medium | Small |
| GAP-API-04 | API Design | OpenAPI partially configured / wrong starter | Medium | Small |
| GAP-API-05 | API Design | Filtering/sorting not documented | Low | Small |
| GAP-OBS-01 | Observability | Inconsistent logging | Medium | Small |
| GAP-OBS-02 | Observability | Health checks not customized | Low | Small |
| GAP-OBS-03 | Observability | No structured logging format | Medium | Small |
| GAP-OBS-04 | Observability | No custom business metrics | Low | Medium |
| GAP-OBS-05 | Observability | No tracing sampling configuration | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | Fund transfer not idempotent | High | Medium |
| GAP-RES-06 | Resilience | @OneToOne instead of @ManyToOne on Transaction | High | Small |
| GAP-RES-07 | Resilience | Balance calculation double subtraction/addition bug | Critical | Small |

### Severity Counts

| Severity | Count |
|----------|-------|
| Critical | 5 |
| High | 8 |
| Medium | 11 |
| Low | 10 |
| **Total** | **34** |
