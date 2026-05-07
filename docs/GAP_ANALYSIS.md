# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven categories. Each gap is rated by severity and estimated remediation effort.

**Severity Scale:** Critical > High > Medium > Low
**Effort Scale:** Small (< 1 day) | Medium (1–3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build
**Severity: Medium | Effort: Medium**

Each of the 6 services is a standalone Gradle project with its own `gradlew`, `gradle/wrapper/`, `settings.gradle`, and `build.gradle`. There is no root-level `settings.gradle` or shared build configuration. This leads to:
- Duplicated Gradle wrapper versions and configurations
- No shared dependency version management
- Inability to build/test all services with a single command

**Current state:** 6 independent `build.gradle` files with duplicated Spring Boot 3.2.4 / Spring Cloud 2023.0.0 config.

### 1.2 Duplicated Code Across Services
**Severity: High | Effort: Medium**

Identical classes are copy-pasted across multiple services with no shared library:
- `AuditAware` — duplicated in user-service, fund-transfer-service, utility-payment-service
- `BaseMapper` — duplicated in all 4 business services
- `ErrorResponse` — duplicated in all 4 business services
- `GlobalExceptionHandler` — duplicated with subtle inconsistencies (some use `ErrorResponse.builder()`, others use `new ErrorResponse()`)
- `SimpleBankingGlobalException` — duplicated in all 4 services
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` — duplicated in 3 services
- `AccountResponse` — duplicated in fund-transfer and utility-payment services
- `FundTransferRequest`/`UtilityPaymentRequest` — duplicated between core-banking and calling services

### 1.3 Inconsistent Package Structure
**Severity: Low | Effort: Small**

Package naming is inconsistent across services:
- Core Banking: `repository/` at top-level package
- User Service: `model/repository/`
- Fund Transfer: `model/repository/`
- Utility Payment: `repository/` at top-level (different from fund-transfer)
- Feign clients live in different packages: `service/rest/client/` (fund-transfer) vs `service/rest/` (user, utility-payment)

### 1.4 Mapper Instantiation Anti-Pattern
**Severity: Low | Effort: Small**

Mappers are manually instantiated (`new FundTransferMapper()`) in service fields instead of being Spring-managed beans or using a mapping framework (e.g., MapStruct). This bypasses Spring's dependency injection and makes testing harder.

**Files affected:**
- `AccountService.java` — `new BankAccountMapper()`, `new UtilityAccountMapper()`
- `UserService.java` (core) — `new UserMapper()`
- `UserService.java` (user-svc) — `new UserMapper()`
- `FundTransferService.java` — `new FundTransferMapper()`
- `UtilityPaymentService.java` — `new UtilityPaymentMapper()`

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format
**Severity: High | Effort: Small**

The `GlobalExceptionHandler` in every service has a generic `Exception.class` handler that returns a raw string:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```
This exposes internal stack traces to clients and does not use the structured `ErrorResponse` format. The custom exception handler correctly uses `ErrorResponse`, but all unexpected errors bypass it.

**Files affected:** All 4 `GlobalExceptionHandler.java` files.

### 2.2 All Errors Return 400 Bad Request
**Severity: High | Effort: Small**

Both the custom and generic exception handlers return `400 Bad Request` for every error, regardless of the actual cause:
- `EntityNotFoundException` → should be `404 Not Found`
- `InsufficientFundsException` → `400` is debatable but acceptable
- Generic `Exception` → should be `500 Internal Server Error`
- No `422 Unprocessable Entity` for validation failures

### 2.3 No Input Validation
**Severity: Critical | Effort: Medium**

No request DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Valid`). No `@Valid` annotations on controller method parameters. All input is accepted as-is:
- `FundTransferRequest` — no validation on `fromAccount`, `toAccount`, or `amount` (could be null, negative, or zero)
- `UtilityPaymentRequest` — no validation on any field
- `User` (registration) — no email format validation, no password strength check

### 2.4 Missing Error Codes/Categories
**Severity: Medium | Effort: Small**

`GlobalErrorCode` only exists in core-banking and user-service with a limited set of codes. Fund-transfer and utility-payment services do not define error codes. There is no unified error code catalog across services.

---

## 3. Testing

### 3.1 Tests Only in Core Banking Service
**Severity: Critical | Effort: Large**

Only `core-banking-service` has meaningful unit tests:
- `AccountServiceTest` — 6 tests
- `TransactionServiceTest` — 10 tests
- `UserServiceTest` — 3 tests

The other 5 services only have auto-generated Spring Boot context-load tests (`*ApplicationTests.java`) that are effectively no-ops requiring full Spring context (and thus infrastructure).

### 3.2 No Integration Tests
**Severity: High | Effort: Large**

No integration tests exist. There are no:
- Controller/API layer tests (`@WebMvcTest`, `MockMvc`)
- Repository tests (`@DataJpaTest`)
- Full Spring Boot integration tests (`@SpringBootTest` with Testcontainers)
- End-to-end test scenarios

### 3.3 No Contract Tests Between Services
**Severity: High | Effort: Large**

Three services communicate with Core Banking via OpenFeign. There are no consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) to verify API compatibility between services. Breaking changes in Core Banking APIs could silently break downstream services.

### 3.4 Context-Load Tests Require Infrastructure
**Severity: Medium | Effort: Small**

The `*ApplicationTests.java` files attempt to load the full Spring context, which requires MySQL, Keycloak, Config Server, and Eureka to be running. These tests will fail in CI without infrastructure. Test `application.yml` only exists for core-banking (with H2 + disabled Flyway).

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code
**Severity: Critical | Effort: Small**

Database credentials and Keycloak admin passwords are hardcoded in Docker Compose files and SQL scripts:
- `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose.yml`: `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`
- `privileges.sql`: `CREATE USER ... IDENTIFIED BY 'oPItyPticIAt'`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.2 CSRF Disabled Globally
**Severity: Medium | Effort: Small**

API Gateway disables CSRF entirely: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for stateless JWT-only APIs, this should be explicitly documented as a conscious decision with justification.

### 4.3 No Rate Limiting
**Severity: High | Effort: Medium**

No rate limiting is configured on the API Gateway or individual services. Financial endpoints like `/api/v1/transfer` and `/api/v1/utility-payment` are vulnerable to abuse.

### 4.4 Keycloak Singleton Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check pattern without synchronization:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This is not thread-safe and could create multiple instances under concurrent load.

### 4.5 Password Stored in DTO
**Severity: High | Effort: Small**

The `User` DTO contains a `password` field that flows through the entire registration pipeline. There is no `@JsonIgnore` or separate request/response DTOs to prevent password leakage in responses.

### 4.6 No Security Headers
**Severity: Medium | Effort: Small**

API Gateway does not configure security headers (Content-Security-Policy, X-Content-Type-Options, X-Frame-Options, Strict-Transport-Security).

### 4.7 Downstream Services Not Secured
**Severity: High | Effort: Medium**

Only the API Gateway enforces OAuth2 authentication. The downstream services (Core Banking, User Service, Fund Transfer, Utility Payment) have no authentication/authorization. They trust the `X-Auth-Id` header injected by the gateway but do not validate it. If a service is reached directly (bypassing the gateway), all endpoints are fully open.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters
**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
```
This breaks OpenAPI documentation generation and IDE type-safety. The User Service controller is the only one that uses typed responses.

### 5.2 No API Versioning Strategy
**Severity: Low | Effort: Small**

While endpoints use `/api/v1/`, there is no documented versioning strategy, no version negotiation, and no plan for backward compatibility.

### 5.3 No Pagination Metadata in Responses
**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<>` instead of `Page<>` or a wrapper with pagination metadata (total count, page number, page size, total pages). Clients cannot navigate paginated results.

### 5.4 No Filtering or Sorting Support
**Severity: Low | Effort: Medium**

List endpoints only support basic pagination. There is no filtering (e.g., by date range, status, account) or explicit sorting support.

### 5.5 OpenAPI Documentation Partially Configured
**Severity: Medium | Effort: Small**

`springdoc-openapi-starter-webflux-ui` is included as a dependency in business services, but:
- This is the **WebFlux** starter, not WebMVC — business services use Spring MVC. This may cause classpath conflicts.
- `@Tag` and `@Operation` annotations are present but incomplete (no `@ApiResponse`, `@Schema`, or `@Parameter` annotations)
- No global OpenAPI configuration (title, version, description, security schemes)

### 5.6 Inconsistent URL Patterns
**Severity: Low | Effort: Small**

URL patterns use a mix of conventions:
- Snake_case path variables: `/bank-account/{account_number}` vs `/bank-users/{id}`
- Verbs in URLs: `/register`, `/update/{id}` (not RESTful)
- Mixed resource naming: `/bank-users` (plural) but `/transfer` (singular)

---

## 6. Observability

### 6.1 Logging is Minimal and Inconsistent
**Severity: Medium | Effort: Medium**

- Some controllers log incoming requests, others don't
- No structured logging (JSON format) — using default Spring Boot text format
- Log messages include `toString()` of request bodies, which could log sensitive data (passwords, account numbers)
- No correlation IDs in log messages (trace IDs from Micrometer are available but not explicitly used in log patterns)
- Inconsistent log levels: no WARN or ERROR logs for business rule violations

### 6.2 Health Checks Not Configured
**Severity: Medium | Effort: Small**

Spring Boot Actuator is included in all services, but:
- Health endpoint configuration is not explicit in local `application.yml` (depends on Config Server)
- No custom health indicators for critical dependencies (MySQL connectivity, Keycloak reachability, Core Banking availability)
- Gateway permits `/actuator/**` but doesn't aggregate downstream health

### 6.3 No Metrics Endpoints or Custom Metrics
**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included:
- No Prometheus metrics endpoint explicitly configured
- No custom business metrics (transfer count, payment volume, error rates)
- README mentions Prometheus but no configuration exists in the codebase

### 6.4 Distributed Tracing Coverage Gaps
**Severity: Low | Effort: Small**

Zipkin tracing infrastructure is in place, but:
- No custom span annotations for business-critical operations
- Database queries are not explicitly traced
- No sampling rate configuration visible in local configs

---

## 7. Resilience

### 7.1 No Circuit Breakers
**Severity: Critical | Effort: Medium**

All inter-service communication uses OpenFeign with no circuit breaker pattern. If Core Banking Service goes down, all downstream services will cascade-fail:
- Fund Transfer Service → blocks indefinitely waiting for Core Banking response
- Utility Payment Service → same issue
- User Service → blocks on Core Banking user lookup

Spring Cloud Circuit Breaker (Resilience4j) is not included in any `build.gradle`.

### 7.2 No Retry Policies
**Severity: High | Effort: Small**

No retry configuration on Feign clients. Transient network failures or temporary service unavailability will immediately fail requests. No Spring Retry or Resilience4j retry configuration exists.

### 7.3 No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Keycloak admin client timeouts

Default timeouts (if any) are unbounded, risking thread exhaustion under load.

### 7.4 No Fallback Behavior
**Severity: Medium | Effort: Medium**

No fallback methods are defined for Feign client failures. There are no:
- Cached responses
- Degraded responses
- Queue-and-retry patterns

### 7.5 Non-Atomic Fund Transfer
**Severity: Critical | Effort: Large**

`TransactionService.internalFundTransfer` performs debit and credit as separate `bankAccountRepository.save()` calls. While wrapped in `@Transactional`, the design is fragile:
- If the credit fails after the debit succeeds and the transaction rolls back, the behavior depends entirely on JPA transaction boundaries
- No idempotency key — retried transfers could double-charge
- The fund-transfer-service marks status as `SUCCESS` immediately after the Core Banking response, with no compensation if the local save fails
- Balance calculation bug: `availableBalance = actualBalance - amount` (double subtraction) exists in both `fundTransfer` and `utilPayment`

### 7.6 No Bulkhead Pattern
**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow response from one service can exhaust all threads and block all other requests.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.3 | No Input Validation | Error Handling | **Critical** | Medium |
| 3.1 | Tests Only in Core Banking | Testing | **Critical** | Large |
| 4.1 | Hardcoded Credentials | Security | **Critical** | Small |
| 7.1 | No Circuit Breakers | Resilience | **Critical** | Medium |
| 7.5 | Non-Atomic Fund Transfer | Resilience | **Critical** | Large |
| 1.2 | Duplicated Code | Code Organization | High | Medium |
| 2.1 | Inconsistent Error Responses | Error Handling | High | Small |
| 2.2 | All Errors Return 400 | Error Handling | High | Small |
| 3.2 | No Integration Tests | Testing | High | Large |
| 3.3 | No Contract Tests | Testing | High | Large |
| 4.3 | No Rate Limiting | Security | High | Medium |
| 4.5 | Password in DTO | Security | High | Small |
| 4.7 | Downstream Services Unsecured | Security | High | Medium |
| 7.2 | No Retry Policies | Resilience | High | Small |
| 7.3 | No Timeout Configuration | Resilience | High | Small |
| 1.1 | No Multi-Project Build | Code Organization | Medium | Medium |
| 1.3 | Inconsistent Packages | Code Organization | Low | Small |
| 1.4 | Mapper Anti-Pattern | Code Organization | Low | Small |
| 2.4 | Missing Error Codes | Error Handling | Medium | Small |
| 3.4 | Context-Load Tests Need Infra | Testing | Medium | Small |
| 4.2 | CSRF Disabled | Security | Medium | Small |
| 4.4 | Keycloak Singleton Thread-Safety | Security | Medium | Small |
| 4.6 | No Security Headers | Security | Medium | Small |
| 5.1 | Raw ResponseEntity | API Design | Medium | Small |
| 5.2 | No Versioning Strategy | API Design | Low | Small |
| 5.3 | No Pagination Metadata | API Design | Medium | Small |
| 5.4 | No Filtering/Sorting | API Design | Low | Medium |
| 5.5 | Wrong OpenAPI Starter | API Design | Medium | Small |
| 5.6 | Inconsistent URLs | API Design | Low | Small |
| 6.1 | Minimal/Inconsistent Logging | Observability | Medium | Medium |
| 6.2 | Health Checks Not Configured | Observability | Medium | Small |
| 6.3 | No Custom Metrics | Observability | Medium | Medium |
| 6.4 | Tracing Gaps | Observability | Low | Small |
| 7.4 | No Fallback Behavior | Resilience | Medium | Medium |
| 7.6 | No Bulkhead Pattern | Resilience | Medium | Medium |
