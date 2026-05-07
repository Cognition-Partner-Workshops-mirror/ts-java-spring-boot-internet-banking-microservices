# Engineering Standards Gap Analysis

> **Repository:** ts-java-spring-boot-internet-banking-microservices
> **Assessment Date:** 2026-05-07
> **Baseline:** Java 21 / Spring Boot 3.2.4 / Spring Cloud 2023.0.0

---

## Severity & Effort Definitions

| Severity | Meaning |
|----------|---------|
| **Critical** | Security vulnerability, data loss risk, or production blocker |
| **High** | Significant quality or reliability issue that should be addressed before production |
| **Medium** | Best-practice gap that affects maintainability or developer experience |
| **Low** | Polish item or nice-to-have improvement |

| Effort | Meaning |
|--------|---------|
| **Small** | < 1 day of work per service |
| **Medium** | 1-3 days of work |
| **Large** | 3+ days of work, may require architectural changes |

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Module Gradle Build
**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to unify them. This means:
- No way to run `./gradlew build` from the root to build all services.
- Dependency versions are duplicated across 6 `build.gradle` files.
- No shared dependency version catalog or BOM for internal consistency.

### GAP-ORG-02: Massive Code Duplication Across Services
**Severity: High | Effort: Large**

The following classes are **copy-pasted identically** (or near-identically) across 3-4 services:
- `BaseMapper<E, D>` — duplicated in 4 services
- `AuditAware` — duplicated in 3 services
- `AuditConfig` / `AuditorAwareConfig` — duplicated in 3 services
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — duplicated in 3 services
- `SimpleBankingGlobalException` / `ErrorResponse` / `GlobalExceptionHandler` — duplicated in 4 services
- `TransactionStatus` enum — duplicated in 2 services

This makes it error-prone to apply fixes; a change in one service may not be applied to others.

### GAP-ORG-03: Inconsistent Package Structure
**Severity: Low | Effort: Small**

- Fund Transfer Service places Feign config at `configuration.CustomFeignClientConfiguration`, while User Service places it at `configuration.feign.CustomFeignClientConfiguration`.
- User Service puts its repository under `model.repository`, while Core Banking puts it under `repository`.
- Fund Transfer Service uses `model.repository.FundTransferRepository` while Utility Payment uses `repository.UtilityPaymentRepository`.

### GAP-ORG-04: Build Artifacts Committed to VCS
**Severity: Medium | Effort: Small**

The `build/` directories and `.gradle/` cache directories for each service are committed to the repository. These should be in `.gitignore`.

---

## 2. Error Handling

### GAP-ERR-01: All Exceptions Return HTTP 400 Bad Request
**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` across all services maps **all** exceptions (including `EntityNotFoundException`, `InsufficientFundsException`, and the generic `Exception` catch-all) to `400 Bad Request`. Proper HTTP semantics require:
- `404 Not Found` for `EntityNotFoundException`
- `409 Conflict` for `UserAlreadyRegisteredException`
- `422 Unprocessable Entity` for `InsufficientFundsException` / business rule violations
- `500 Internal Server Error` for unexpected exceptions

### GAP-ERR-02: Generic Exception Catch-All Leaks Internal Details
**Severity: Critical | Effort: Small**

The catch-all handler in every service:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```
This leaks the full exception `toString()` including stack traces, class names, and potentially database column names to the API consumer. This is a security and information-disclosure risk.

### GAP-ERR-03: Inconsistent Error Response Format
**Severity: Medium | Effort: Small**

- Business exceptions return `ErrorResponse { code, message }`.
- The generic catch-all returns a plain `String`.
- Fund Transfer Service's `GlobalExceptionHandler` uses `new ErrorResponse(code, message)` constructor while others use the `@Builder` pattern.

Consumers cannot reliably parse error responses.

### GAP-ERR-04: No Error Handling for Failed Downstream Calls
**Severity: High | Effort: Medium**

In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`:
- If the Feign call to Core Banking fails **after** the local entity has been saved as `PENDING`/`PROCESSING`, the local record is never updated to `FAILED`.
- There is no try-catch around the Feign call, no compensation logic, and no retry.
- The status will remain `PENDING`/`PROCESSING` forever (orphaned record).

### GAP-ERR-05: SimpleBankingGlobalException Overrides `message` Field
**Severity: Medium | Effort: Small**

`SimpleBankingGlobalException` declares its own `private String message` field via Lombok `@Getter/@Setter`, shadowing `Throwable.message`. The `super(message)` single-arg constructor calls `RuntimeException(message)` but the two-arg constructor does not, meaning `getMessage()` from `Throwable` may return `null` while the Lombok-generated getter returns the field value. This can cause confusion in logging.

---

## 3. Testing

### GAP-TEST-01: Near-Zero Test Coverage
**Severity: Critical | Effort: Large**

| Service | Test Classes | Meaningful Tests |
|---------|-------------|-----------------|
| core-banking-service | 4 | 3 service-level test classes with ~15 tests |
| internet-banking-api-gateway | 1 | 0 (empty `contextLoads` only) |
| internet-banking-config-server | 1 | 0 (empty `contextLoads` only) |
| internet-banking-fund-transfer-service | 1 | 0 (empty `contextLoads` only) |
| internet-banking-service-registry | 1 | 0 (empty `contextLoads` only) |
| internet-banking-user-service | 1 | 0 (empty `contextLoads` only) |
| internet-banking-utility-payment-service | 1 | 0 (empty `contextLoads` only) |

Only Core Banking Service has real unit tests. All other services have **no tests** beyond the default Spring Boot context load test stub.

### GAP-TEST-02: No Integration Tests
**Severity: High | Effort: Large**

- No `@SpringBootTest` with real database or embedded containers.
- No Testcontainers usage for MySQL or Keycloak.
- No tests verify actual REST endpoint behavior (controller layer testing).

### GAP-TEST-03: No Contract Tests
**Severity: High | Effort: Large**

- No Spring Cloud Contract or Pact tests between services.
- Feign clients are tightly coupled to Core Banking Service endpoints, but there are no contracts validating the producer-consumer agreement.
- Breaking changes in Core Banking API would not be caught until runtime.

### GAP-TEST-04: Context Load Tests Will Fail Without Infrastructure
**Severity: Medium | Effort: Small**

The `@SpringBootTest` context load tests in most services will fail because they attempt to connect to Config Server, Eureka, MySQL, and Keycloak. The test `application.yml` files do not adequately mock or disable these dependencies.

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Docker Compose and Source
**Severity: Critical | Effort: Small**

| Location | Credential |
|----------|-----------|
| `docker-compose/docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose/mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | `'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'` |
| `docker-compose/docker-compose.yml` | `KC_DB_PASSWORD: password`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| `README.md` | `ib_admin@javatodev.com / 5V7huE3G86uB` |

These should be externalized to environment variables or Docker secrets.

### GAP-SEC-02: No Input Validation
**Severity: Critical | Effort: Medium**

- No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` / `@Size` annotations on any request DTO.
- `FundTransferRequest.amount` can be null, zero, or negative — there is no validation.
- `UtilityPaymentRequest` fields can all be null.
- User registration accepts any string as an email without format validation.
- No Jakarta Bean Validation (`spring-boot-starter-validation`) dependency in any `build.gradle`.

### GAP-SEC-03: CSRF Disabled Without Justification
**Severity: Medium | Effort: Small**

The API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a stateless JWT-based API, this should be documented. If any session-based flows are added later, this becomes a vulnerability.

### GAP-SEC-04: Keycloak Client Uses Static Singleton (Not Thread-Safe)
**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check-locking-free singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```
This is **not thread-safe**. Multiple threads could create duplicate instances. Also, if the Keycloak token expires, there is no refresh logic.

### GAP-SEC-05: No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

- No OWASP Dependency-Check plugin, Snyk, or Dependabot configured.
- No way to detect known CVEs in transitive dependencies.

### GAP-SEC-06: Wildcard MySQL User Privileges
**Severity: High | Effort: Small**

`privileges.sql` grants `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` to the application user. The application user should only have DML privileges (`INSERT, UPDATE, DELETE, SELECT`) on specific databases, not DDL on all databases.

### GAP-SEC-07: Password Handling in User Registration
**Severity: High | Effort: Small**

The `User` DTO includes a `password` field that is:
- Received in the request body.
- Passed to Keycloak as a `CredentialRepresentation`.
- Potentially logged via `log.info("Creating user with {}", request.toString())` since `@Data` generates `toString()` including all fields.

The password could appear in application logs.

---

## 5. API Design

### GAP-API-01: Raw `ResponseEntity` Without Type Parameters
**Severity: Medium | Effort: Small**

Almost all controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`. This:
- Suppresses compile-time type safety.
- Makes OpenAPI/Swagger documentation incomplete (response schema is `Object`).
- Only `UserController` in user-service uses typed `ResponseEntity<User>`.

### GAP-API-02: No API Versioning Strategy
**Severity: Low | Effort: Small**

While `/api/v1/` prefix exists, there is no mechanism for version negotiation (header-based or path-based), no deprecation policy, and no documentation of versioning strategy.

### GAP-API-03: No Pagination Metadata in Responses
**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. Consumers receive the data but not:
- Total elements count
- Total pages
- Current page number
- Whether there is a next/previous page

### GAP-API-04: No Filtering or Sorting Documentation
**Severity: Low | Effort: Small**

Pageable endpoints accept Spring's default `page`, `size`, `sort` parameters but these are not documented in the OpenAPI annotations.

### GAP-API-05: Incomplete OpenAPI/Swagger Documentation
**Severity: Medium | Effort: Small**

- `springdoc-openapi-starter-webflux-ui` is declared in build files but Core Banking Service does not include it.
- The `@Tag` and `@Operation` annotations are present but `@ApiResponse`, `@Schema`, and `@Parameter` annotations are missing.
- Request/response models lack `@Schema` descriptions.

### GAP-API-06: Non-RESTful Endpoint Design
**Severity: Low | Effort: Small**

- `PATCH /api/v1/bank-users/update/{id}` — the "update" segment is redundant; `PATCH /api/v1/bank-users/{id}` is sufficient.
- `POST /api/v1/transaction/fund-transfer` and `POST /api/v1/transaction/util-payment` are action-oriented rather than resource-oriented.

### GAP-API-07: Swagger UI Uses WebFlux Dependency in Servlet Services
**Severity: Medium | Effort: Small**

Fund Transfer, User, and Utility Payment services include `springdoc-openapi-starter-webflux-ui` but are Spring MVC (servlet-based) applications. They should use `springdoc-openapi-starter-webmvc-ui` instead.

---

## 6. Observability

### GAP-OBS-01: Core Banking Service Missing Tracing
**Severity: High | Effort: Small**

Core Banking Service's `build.gradle` does not include:
- `micrometer-tracing-bridge-brave`
- `zipkin-reporter-brave`
- `feign-micrometer`

This breaks the distributed trace chain — traces from Fund Transfer and Utility Payment services will terminate at the Core Banking boundary.

### GAP-OBS-02: No Structured Logging
**Severity: Medium | Effort: Medium**

- All services use default Spring Boot logging (Logback with pattern layout).
- No JSON log format configured for log aggregation tools (ELK, CloudWatch, Datadog).
- No MDC enrichment with trace IDs, user IDs, or request IDs beyond what Micrometer provides.

### GAP-OBS-03: Sensitive Data in Logs
**Severity: High | Effort: Small**

Multiple controllers log request bodies using `toString()`:
```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
log.info("Creating user with {}", request.toString());
```
Since DTOs use `@Data` (which generates `toString()`), this logs account numbers, transfer amounts, and potentially passwords.

### GAP-OBS-04: No Custom Health Checks
**Severity: Medium | Effort: Small**

- Spring Boot Actuator is included in all services, providing `/actuator/health`.
- However, no custom health indicators exist for critical dependencies (Keycloak connectivity, Core Banking reachability, database connection pools).

### GAP-OBS-05: No Metrics Dashboards or Alerting
**Severity: Medium | Effort: Medium**

- Prometheus is listed in the tech stack but `micrometer-registry-prometheus` dependency is not declared in any `build.gradle`.
- No Grafana dashboards, alerting rules, or metrics export configuration.

### GAP-OBS-06: Actuator Endpoints Publicly Accessible
**Severity: High | Effort: Small**

The API Gateway security configuration explicitly permits all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    ...
```
This exposes `/actuator/env`, `/actuator/configprops`, `/actuator/beans`, etc., which can leak sensitive configuration.

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity: High | Effort: Medium**

- No Resilience4j or Hystrix dependencies.
- Feign calls to Core Banking Service have no circuit breaker.
- If Core Banking Service goes down, all dependent services will cascade-fail with no fallback.

### GAP-RES-02: No Retry Policies
**Severity: High | Effort: Small**

- No Spring Retry or Resilience4j Retry configured.
- Transient network failures between services will immediately fail requests.
- No Feign retry configuration.

### GAP-RES-03: No Timeout Configuration
**Severity: High | Effort: Small**

- No Feign client timeout settings (`connectTimeout`, `readTimeout`).
- No Spring Cloud Gateway route-level timeouts.
- A slow Core Banking response could block thread pools in Fund Transfer and Utility Payment services indefinitely.

### GAP-RES-04: No Fallback Behavior
**Severity: Medium | Effort: Medium**

- No Feign fallback factories or fallback classes defined.
- No graceful degradation strategy (e.g., returning cached data, queueing requests for later).

### GAP-RES-05: No Rate Limiting
**Severity: Medium | Effort: Medium**

- The API Gateway has no rate limiting configuration.
- No Spring Cloud Gateway `RequestRateLimiter` filter.
- The system is vulnerable to abuse or accidental overload.

### GAP-RES-06: No Idempotency for Financial Operations
**Severity: Critical | Effort: Medium**

- Fund transfer and utility payment endpoints have no idempotency keys.
- If a client retries a failed request (e.g., due to network timeout), the transfer or payment could be processed twice.
- No duplicate-detection mechanism exists.

### GAP-RES-07: Balance Calculation Bug (Double Deduction)
**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` **after** `actualBalance` has already been reduced. This means `availableBalance = originalBalance - 2 * amount`. The same bug exists in `utilPayment()` and in the credit leg (where it adds double).

---

## Summary Table

| ID | Area | Gap | Severity | Effort |
|----|------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No multi-module Gradle build | Medium | Medium |
| GAP-ORG-02 | Code Organization | Massive code duplication | High | Large |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Build artifacts in VCS | Medium | Small |
| GAP-ERR-01 | Error Handling | All exceptions return 400 | High | Small |
| GAP-ERR-02 | Error Handling | Generic handler leaks internals | Critical | Small |
| GAP-ERR-03 | Error Handling | Inconsistent error format | Medium | Small |
| GAP-ERR-04 | Error Handling | No handling for failed Feign calls | High | Medium |
| GAP-ERR-05 | Error Handling | Exception message field shadowing | Medium | Small |
| GAP-TEST-01 | Testing | Near-zero test coverage | Critical | Large |
| GAP-TEST-02 | Testing | No integration tests | High | Large |
| GAP-TEST-03 | Testing | No contract tests | High | Large |
| GAP-TEST-04 | Testing | Context load tests fail without infra | Medium | Small |
| GAP-SEC-01 | Security | Hardcoded credentials | Critical | Small |
| GAP-SEC-02 | Security | No input validation | Critical | Medium |
| GAP-SEC-03 | Security | CSRF disabled undocumented | Medium | Small |
| GAP-SEC-04 | Security | Keycloak singleton not thread-safe | High | Small |
| GAP-SEC-05 | Security | No dependency vulnerability scanning | Medium | Small |
| GAP-SEC-06 | Security | Wildcard MySQL privileges | High | Small |
| GAP-SEC-07 | Security | Password in logs | High | Small |
| GAP-API-01 | API Design | Raw ResponseEntity types | Medium | Small |
| GAP-API-02 | API Design | No versioning strategy | Low | Small |
| GAP-API-03 | API Design | No pagination metadata | Medium | Small |
| GAP-API-04 | API Design | No sorting/filtering docs | Low | Small |
| GAP-API-05 | API Design | Incomplete OpenAPI docs | Medium | Small |
| GAP-API-06 | API Design | Non-RESTful endpoint naming | Low | Small |
| GAP-API-07 | API Design | Wrong Swagger dependency (WebFlux in Servlet) | Medium | Small |
| GAP-OBS-01 | Observability | Core Banking missing tracing | High | Small |
| GAP-OBS-02 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-03 | Observability | Sensitive data in logs | High | Small |
| GAP-OBS-04 | Observability | No custom health checks | Medium | Small |
| GAP-OBS-05 | Observability | No Prometheus metrics | Medium | Medium |
| GAP-OBS-06 | Observability | Actuator endpoints public | High | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | High | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-05 | Resilience | No rate limiting | Medium | Medium |
| GAP-RES-06 | Resilience | No idempotency for financial ops | Critical | Medium |
| GAP-RES-07 | Resilience | Balance calculation bug (double deduction) | Critical | Small |
