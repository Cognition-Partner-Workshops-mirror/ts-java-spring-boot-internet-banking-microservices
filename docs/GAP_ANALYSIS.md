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

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Small**

Each service has its own standalone `build.gradle` and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` that ties them together as a Gradle multi-project build. This means:

- No single command to build/test all services.
- No shared dependency version catalog.
- Plugin and dependency versions are duplicated across all six `build.gradle` files (e.g., Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector 8.4.0).

### 1.2 Duplicated Classes Across Services

**Severity: High | Effort: Medium**

Several classes are copy-pasted across services with minor variations:

| Class | Duplicated In |
|---|---|
| `BaseMapper` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `AuditConfig` / `AuditorAwareConfig` | user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | user-service, fund-transfer, utility-payment |

There is no shared library module. Any bug fix or improvement must be applied in four places.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- Core banking uses `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`.
- User service uses `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `configuration.keycloak`, `configuration.feign`, `configuration.filter`, `configuration.audit`.
- Fund transfer uses `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `service`, `service.rest.client`, `configuration`, `configuration.audit`, `configuration.filter`.

The package nesting is inconsistent — `repository` is top-level in core-banking but under `model` in other services. Feign clients live in `service.rest.client` in fund-transfer but `service.rest` in user-service and utility-payment.

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated as non-final fields in service classes rather than injected as Spring beans:

```java
private UserMapper userMapper = new UserMapper();  // Not a Spring bean
```

This bypasses Spring's dependency injection, makes mocking in tests harder, and is inconsistent with the `@RequiredArgsConstructor` + `final` pattern used for other dependencies.

---

## 2. Error Handling

### 2.1 Catch-All Returns Plain String

**Severity: High | Effort: Small**

All `GlobalExceptionHandler` implementations have a generic `Exception` catch-all that returns a raw string:

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

Problems:
- Leaks internal exception details (stack traces, class names) to the client.
- Returns `400 Bad Request` for all errors, including `500`-class issues.
- Response body is a plain string, not the structured `ErrorResponse` format.

### 2.2 All Errors Return HTTP 400

**Severity: High | Effort: Small**

Both the typed and generic exception handlers always return `400 Bad Request`:

- `EntityNotFoundException` → should be `404 Not Found`.
- `InsufficientFundsException` → could be `422 Unprocessable Entity`.
- `UserAlreadyRegisteredException` → should be `409 Conflict`.
- Generic `Exception` → should be `500 Internal Server Error`.

### 2.3 Inconsistent ErrorResponse Construction

**Severity: Medium | Effort: Small**

- Core banking uses `ErrorResponse.builder().code(...).message(...).build()`.
- Fund transfer uses `new ErrorResponse(e.getCode(), e.getMessage())` (constructor).
- The `ErrorResponse` class varies between services (some have `@Builder`, others have constructors).

### 2.4 No Error Response for Feign Client Failures

**Severity: High | Effort: Medium**

Only the user-service has a `CustomFeignErrorDecoder`. Fund transfer and utility payment services have a `CustomFeignClientConfiguration` but no error decoder — Feign errors from core-banking-service will bubble up as raw `FeignException` and hit the generic catch-all handler, leaking internal details.

### 2.5 No Validation on Request Bodies

**Severity: Critical | Effort: Medium**

No `@Valid` annotation or Jakarta Bean Validation constraints on any `@RequestBody` parameter across all controllers:

- `FundTransferRequest` accepts null `fromAccount`, `toAccount`, or `amount`.
- `UtilityPaymentRequest` accepts null `providerId`, `amount`, or `account`.
- `User` registration accepts null `email`, `identification`, or `password`.

There is no validation dependency (`spring-boot-starter-validation`) in any `build.gradle`.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Files | Description |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Basic unit tests with Mockito — good start |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Empty context-load stub only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Empty context-load stub only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Empty context-load stub only |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Empty context-load stub only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Empty context-load stub only |
| internet-banking-config-server | (implicit) | No test file found |

Only core-banking-service has meaningful tests (~17 test cases). The four internet-banking services that contain business logic have zero tests for their service or controller layers.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- API endpoints with a running Spring context (`@SpringBootTest` + `@AutoConfigureMockMvc`).
- Database interactions with a real (H2 or Testcontainers) database.
- Feign client calls between services (even mocked).

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

Services communicate via OpenFeign and share no common API contract module. There are no:
- Spring Cloud Contract tests.
- Pact consumer/provider tests.
- Shared DTOs or API spec artifacts between producer and consumer.

If core-banking-service changes an endpoint signature, consumer services will break silently until runtime.

### 3.4 Context-Load Tests Will Fail Without Infrastructure

**Severity: Low | Effort: Small**

The empty `@SpringBootTest` context-load tests in user-service, fund-transfer, and utility-payment will fail when run locally without Keycloak, MySQL, Eureka, and Config Server because they load the full application context with real Feign clients and database connections. Test `application.yml` files exist but do not mock external dependencies.

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose and Source

**Severity: Critical | Effort: Small**

| Location | Credential |
|---|---|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password` |
| `privileges.sql` | `IDENTIFIED BY 'oPItyPticIAt'` |
| `README.md` | `ib_admin@javatodev.com / 5V7huE3G86uB` |
| `KeycloakProperties.java` | Client secret injected via `@Value` — safe, but the value comes from Config Server backed by a **public** Git repo |

All database and Keycloak passwords are committed in plaintext. The Config Server reads from a public GitHub repository, meaning production credentials would be publicly accessible.

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

(See Section 2.5) No request body validation on any endpoint. This opens the door to:
- SQL injection (mitigated by JPA parameterized queries, but still a defense-in-depth gap).
- Business logic errors from null/negative values.
- Potential resource exhaustion from unbounded string inputs.

### 4.3 CSRF Disabled Without Justification

**Severity: Low | Effort: Small**

`SecurityConfiguration` disables CSRF globally:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
Acceptable for a pure REST API with JWT auth but should be documented.

### 4.4 No Rate Limiting

**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configuration. All endpoints, including the public registration endpoint, are vulnerable to brute-force or abuse.

### 4.5 No Role-Based Authorization

**Severity: High | Effort: Medium**

The gateway only checks "is the user authenticated?" via JWT validation. There is no role-based access control:
- Any authenticated user can call admin endpoints (e.g., `PATCH /bank-users/update/{id}` to approve users).
- No `@PreAuthorize`, `@Secured`, or gateway-level role checks exist.
- Keycloak roles are not mapped or enforced.

### 4.6 Keycloak Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // Race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
Under concurrent requests during startup, multiple Keycloak instances could be created.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP dependency-check, Snyk, or Dependabot configuration exists. There is no automated way to detect known CVEs in the dependency tree.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics

**Severity: Medium | Effort: Small**

Most controller methods return untyped `ResponseEntity` (no generic parameter):
```java
public ResponseEntity getBankAccount(...)  // Should be ResponseEntity<BankAccount>
```

This means:
- OpenAPI/Swagger cannot infer response schemas automatically.
- Compile-time type safety is lost.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

All endpoints use `/api/v1/` but there is no mechanism for evolving to v2 — no version header strategy, URL versioning plan, or content negotiation.

### 5.3 No Consistent Pagination Response Envelope

**Severity: Medium | Effort: Small**

List endpoints return raw `List<T>` instead of a pagination wrapper. Clients cannot determine:
- Total number of elements.
- Current page number.
- Whether more pages exist.

Spring Data's `Page<T>` is used internally but `.getContent()` is called to strip pagination metadata before returning.

### 5.4 No Filtering or Sorting Parameters

**Severity: Low | Effort: Medium**

List endpoints accept `Pageable` (page, size, sort) but there are no explicit query parameters for filtering (e.g., by status, date range, account number).

### 5.5 OpenAPI Documentation Is Minimal

**Severity: Medium | Effort: Small**

While `springdoc-openapi` is included and controllers have `@Tag` and `@Operation` annotations, there are no `@Schema`, `@ApiResponse`, or `@Parameter` annotations on DTOs, response codes, or path variables. The generated docs will be incomplete.

### 5.6 Wrong springdoc Dependency for MVC Services

**Severity: Medium | Effort: Small**

Business services (core-banking, user, fund-transfer, utility-payment) use Spring MVC (`spring-boot-starter-web`) but include the WebFlux Swagger dependency:
```
org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0
```
The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause runtime issues or the Swagger UI not loading.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log incoming requests; others do not (e.g., `UtilityPaymentController` has no `@Slf4j`).
- Service-layer methods have inconsistent logging — `FundTransferService.fundTransfer()` logs but with a string concatenation bug: `log.info("Sending fund transfer request {}" + request.toString())` — the `{}` is concatenated instead of replaced.
- No structured logging (JSON format) is configured.
- Log levels are not configurable per service.

### 6.2 No Custom Health Indicators

**Severity: Low | Effort: Small**

Actuator health endpoint is available but only reports the default health indicators. There are no custom health checks for:
- MySQL connectivity.
- Keycloak availability.
- Config Server reachability.
- Feign client targets.

### 6.3 Prometheus Integration Incomplete

**Severity: Medium | Effort: Small**

README mentions Prometheus, but:
- No `micrometer-registry-prometheus` dependency in any `build.gradle`.
- No Prometheus scrape configuration or `management.endpoints.web.exposure.include` setting visible.
- No `prometheus.yml` for Prometheus server.

### 6.4 No Centralized Log Aggregation

**Severity: Medium | Effort: Medium**

No ELK stack, Loki, or similar log aggregation is configured. With six services, correlating logs across services requires manual SSH access to each container.

### 6.5 Distributed Tracing Configuration Not Verified

**Severity: Low | Effort: Small**

Zipkin dependencies are included in all services, but the actual trace export configuration (endpoint URL, sampling rate) comes from the Config Server's remote Git repository and cannot be verified in this codebase alone.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service calls via OpenFeign are "fire and hope." If core-banking-service goes down:
- Fund transfer requests will hang or throw raw exceptions.
- Utility payment requests will fail similarly.
- User registration (which calls core-banking for verification) will fail.

No Resilience4j, Hystrix, or Spring Cloud Circuit Breaker dependency exists in any service.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

Feign clients have no retry configuration. A transient network issue or temporary service unavailability results in immediate failure with no retry attempt.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout settings for:
- Feign client connection and read timeouts.
- Database connection pool timeouts.
- Gateway route timeouts.

Default timeouts are used, which may be too lenient (e.g., default Feign read timeout is 60 seconds).

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

When core-banking-service is unavailable:
- Fund transfer service saves a `PENDING` record but then throws an exception. The record is never updated to `FAILED`.
- Utility payment service saves a `PROCESSING` record but then throws an exception. The record is never updated to `FAILED`.
- There is no dead letter queue, retry mechanism, or compensation logic.

### 7.5 No Idempotency Protection

**Severity: High | Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a request (e.g., due to network timeout), the same transfer/payment could be executed multiple times, causing double-debit.

### 7.6 Transaction Boundaries Span Network Calls

**Severity: Critical | Effort: Large**

In `FundTransferService.fundTransfer()`:
1. Local entity saved with status `PENDING` (DB write).
2. Feign call to core-banking-service (network call).
3. Local entity updated to `SUCCESS` (DB write).

If step 2 succeeds but step 3 fails (e.g., local DB issue), the core banking ledger is debited but the internet-banking record remains `PENDING`. There is no saga pattern, compensation, or outbox pattern to handle partial failures.

### 7.7 Non-Atomic Balance Updates

**Severity: Critical | Effort: Medium**

In `TransactionService.internalFundTransfer()`:
- Source and destination accounts are updated in separate `save()` calls.
- If the application crashes between the debit and credit operations, money is lost (debited from source but never credited to destination).
- While `@Transactional` is present, the method uses `bankAccountRepository.save()` which may not guarantee atomicity with the optimistic locking pattern used.

---

## Summary Matrix

| ID | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Small |
| 1.2 | Duplicated classes across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 2.1 | Catch-all returns plain string with internal details | Error Handling | High | Small |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.3 | Inconsistent ErrorResponse construction | Error Handling | Medium | Small |
| 2.4 | No Feign error decoder in most services | Error Handling | High | Medium |
| 2.5 | No request body validation | Error Handling | Critical | Medium |
| 3.1 | Minimal test coverage (only core-banking has tests) | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context-load tests will fail without infrastructure | Testing | Low | Small |
| 4.1 | Hardcoded credentials in source | Security | Critical | Small |
| 4.2 | No input validation | Security | Critical | Medium |
| 4.3 | CSRF disabled without documentation | Security | Low | Small |
| 4.4 | No rate limiting | Security | Medium | Medium |
| 4.5 | No role-based authorization | Security | High | Medium |
| 4.6 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.7 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without generics | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Medium |
| 5.3 | No pagination response envelope | API Design | Medium | Small |
| 5.4 | No filtering or sorting parameters | API Design | Low | Medium |
| 5.5 | OpenAPI documentation is minimal | API Design | Medium | Small |
| 5.6 | Wrong springdoc dependency (webflux vs webmvc) | API Design | Medium | Small |
| 6.1 | Inconsistent logging, string concatenation bug | Observability | Medium | Small |
| 6.2 | No custom health indicators | Observability | Low | Small |
| 6.3 | Prometheus integration incomplete | Observability | Medium | Small |
| 6.4 | No centralized log aggregation | Observability | Medium | Medium |
| 6.5 | Distributed tracing config not verifiable | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior / stuck status records | Resilience | High | Medium |
| 7.5 | No idempotency protection | Resilience | High | Medium |
| 7.6 | Transaction boundaries span network calls | Resilience | Critical | Large |
| 7.7 | Non-atomic balance updates | Resilience | Critical | Medium |
