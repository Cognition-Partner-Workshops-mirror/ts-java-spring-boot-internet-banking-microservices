# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices across seven dimensions. Each gap is rated by **severity** (Critical / High / Medium / Low) and **estimated remediation effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Small**

Each microservice is a standalone Gradle project with its own `settings.gradle` and `build.gradle`. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds across all services. This makes it impossible to run a single `./gradlew build` from the project root and leads to duplicated dependency version declarations across all 6 `build.gradle` files.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted across 3–4 services with minor variations:
- `AuditAware` (user-service, fund-transfer-service, utility-payment-service)
- `BaseMapper` interface (core-banking, user-service, fund-transfer-service, utility-payment-service)
- `ErrorResponse` (core-banking, user-service, fund-transfer-service, utility-payment-service)
- `SimpleBankingGlobalException` (all business services)
- `GlobalExceptionHandler` (all business services)
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` (user-service, fund-transfer-service, utility-payment-service)
- `AccountResponse` DTO (fund-transfer-service, utility-payment-service)
- `FundTransferRequest` / `UtilityPaymentRequest` (duplicated between core-banking and their respective orchestrating services)

There is **no shared library module** to extract common code.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

- Core Banking uses `repository` package at root; other services use `model.repository`.
- Utility Payment puts Feign config in `configuration`; User Service puts it in `configuration.feign`.
- User Service has `model.rest.response`; Fund Transfer uses `model.dto.response`.
- Fund Transfer Feign client lives in `service.rest.client`; User Service's is in `service.rest`; Utility Payment's is in `service.rest`.

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated inline as fields (`new FundTransferMapper()`) rather than being Spring-managed beans or using a mapping framework (MapStruct, ModelMapper). This prevents dependency injection and testability.

---

## 2. Error Handling

### 2.1 Catch-All Returns 400 for All Errors

**Severity: Critical | Effort: Small**

Every `GlobalExceptionHandler` has a catch-all `@ExceptionHandler({Exception.class})` that returns HTTP **400 Bad Request** for ALL unhandled exceptions, including what should be 500 Internal Server Error, 404 Not Found, 503 Service Unavailable, etc.

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

### 2.2 Exception Stack Trace Leaked to Client

**Severity: Critical | Effort: Small**

The catch-all handler concatenates the full exception object (`"Exception occur inside API " + e`) into the response body. This exposes internal class names, stack traces, and potentially sensitive information to API consumers.

### 2.3 Inconsistent Error Response Format

**Severity: High | Effort: Small**

- `SimpleBankingGlobalException` handler returns structured `ErrorResponse` (code + message).
- The catch-all returns a plain string.
- Core Banking uses `ErrorResponse` with `@Builder`; Fund Transfer and Utility Payment use constructor-based `ErrorResponse`.
- No common error response contract across services.

### 2.4 EntityNotFoundException Returns 400 Instead of 404

**Severity: High | Effort: Small**

`EntityNotFoundException` extends `SimpleBankingGlobalException`, so it's caught by the `handleGlobalException` handler which always returns HTTP 400. Entity-not-found should return HTTP 404.

### 2.5 No Feign Error Handling in Fund Transfer / Utility Payment

**Severity: High | Effort: Medium**

When the core banking Feign call fails (network error, timeout, 4xx/5xx), the orchestrating services (`FundTransferService`, `UtilityPaymentService`) have no try-catch. The transaction entity remains in `PENDING`/`PROCESSING` status permanently. Only the User Service has a `CustomFeignErrorDecoder`.

### 2.6 SimpleBankingGlobalException Overrides `message` Field

**Severity: Medium | Effort: Small**

`SimpleBankingGlobalException` declares its own `private String message` field while extending `RuntimeException`, which already has a `message` field. This shadows the parent's field and can cause confusion — the Lombok-generated `getMessage()` returns the local field while `super.getMessage()` returns null.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: High | Effort: Large**

| Service | Test Files | Test Type |
|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | Unit (mock-based) |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | Context load only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only |

- Only `core-banking-service` has meaningful unit tests (3 service test classes, ~20 test methods).
- **Zero** controller/integration tests across all services.
- **Zero** Feign client contract tests.
- Fund Transfer, Utility Payment, and User services have **zero** service-level tests.

### 3.2 No Integration / E2E Test Infrastructure

**Severity: Medium | Effort: Large**

- No Testcontainers setup for MySQL/Keycloak integration tests.
- No Spring Boot `@SpringBootTest` with `RANDOM_PORT` for controller tests.
- No WireMock or MockServer for Feign client testing.
- No contract testing (Spring Cloud Contract, Pact) between services.

### 3.3 Context Load Tests Likely Fail Without Infrastructure

**Severity: Medium | Effort: Small**

The `*ApplicationTests` classes annotate with `@SpringBootTest` but require MySQL, Eureka, and Config Server to be running. The test `application.yml` files (where present) configure H2 and disable Flyway, but not all services have test configuration.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

**Zero** `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@Email`, or any Bean Validation annotations exist anywhere in the codebase. All `@RequestBody` parameters accept any payload shape without validation:

- Fund transfers accept negative amounts
- User registration accepts blank emails/passwords
- Utility payments accept zero or negative provider IDs

### 4.2 Hardcoded Credentials in Source Control

**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository:

| Location | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin/password` |
| `docker-compose.yml` | Keycloak DB: `keycloak/password` |
| `mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

### 4.3 CSRF Disabled Without Justification

**Severity: Medium | Effort: Small**

The API Gateway disables CSRF protection (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While common for stateless REST APIs, there's no documentation or comment explaining the security trade-off.

### 4.4 No Role-Based Access Control

**Severity: High | Effort: Medium**

The Gateway security configuration only distinguishes between authenticated and unauthenticated requests. There is no role-based or scope-based authorization. Any authenticated user can:
- Approve/disable other users (`PATCH /update/{id}`)
- Transfer funds from any account
- View all users' data

### 4.5 Password Stored in DTO Without Masking

**Severity: High | Effort: Small**

The `User` DTO contains a `password` field that flows through the entire registration pipeline. Since `User` is also used as a response type (returned from `createUser`), the password could be serialized back to the client. No `@JsonProperty(access = WRITE_ONLY)` or similar annotation is applied.

### 4.6 Keycloak Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a static field with a null check — a classic broken double-checked locking pattern without `synchronized` or `volatile`. This can create multiple `Keycloak` instances under concurrent access.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP dependency-check, Snyk, or similar tool is configured in any `build.gradle`.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` instead of `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`, etc. Only the User Service's `UserController` uses typed responses. This breaks OpenAPI schema generation and compile-time type safety.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/...` but there is no mechanism for version negotiation, no documentation of versioning policy, and no infrastructure for running multiple versions.

### 5.3 Inconsistent Resource Naming

**Severity: Medium | Effort: Small**

| Pattern | Service |
|---|---|
| `/api/v1/bank-users/register` | User Service (verb in URL) |
| `/api/v1/transfer` | Fund Transfer (noun) |
| `/api/v1/utility-payment` | Utility Payment (noun) |
| `/api/v1/account/bank-account/{account_number}` | Core Banking (nested noun, underscore param) |
| `/api/v1/account/util-account/{account_name}` | Core Banking (abbreviation) |
| `/api/v1/bank-users/update/{id}` | User Service (verb in URL) |

REST best practice: Use nouns, avoid verbs in URLs. `PATCH /api/v1/bank-users/{id}` instead of `PATCH /api/v1/bank-users/update/{id}`.

### 5.4 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>`. Clients receive no total count, page number, or total pages metadata.

### 5.5 OpenAPI/Swagger Dependency Mismatch

**Severity: Medium | Effort: Small**

Services include `springdoc-openapi-starter-webflux-ui:2.1.0` but core-banking-service, user-service, fund-transfer-service, and utility-payment-service are Spring MVC (Web) applications, not WebFlux. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause runtime issues or missing Swagger UI.

### 5.6 PATCH Endpoint Uses PUT Semantics

**Severity: Low | Effort: Small**

`PATCH /api/v1/bank-users/update/{id}` accepts `UserUpdateRequest` with a single `status` field. True PATCH should support partial updates with merge semantics (JSON Merge Patch or JSON Patch). This is effectively a PUT of the status field.

---

## 6. Observability

### 6.1 No Structured Logging

**Severity: High | Effort: Medium**

All services use default Logback configuration with plain text output. For a microservices architecture, structured JSON logging (with correlation IDs, service name, trace IDs) is essential for log aggregation tools (ELK, Loki, CloudWatch).

### 6.2 Sensitive Data in Logs

**Severity: High | Effort: Small**

Multiple services log full request objects using `.toString()`:
- `FundTransferController`: Logs full fund transfer request (account numbers, amounts)
- `UserController` (User Service): Logs full user creation request (could include password)
- `UtilityPaymentService`: Logs full payment request

### 6.3 No Custom Health Checks

**Severity: Medium | Effort: Small**

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Downstream service connectivity (Feign targets reachable)
- Keycloak connectivity
- Database pool status beyond default

### 6.4 No Metrics Beyond Defaults

**Severity: Medium | Effort: Medium**

No custom Micrometer metrics are defined. Business-critical metrics are missing:
- Fund transfer count/latency
- Payment processing rate
- User registration rate
- Failed transaction count

### 6.5 Tracing Configuration in External Config

**Severity: Low | Effort: Small**

Zipkin configuration is managed via Spring Cloud Config (external Git repo), making it invisible in the codebase. The sampling rate and Zipkin URL are not documented locally.

### 6.6 No Log Correlation Across Services

**Severity: Medium | Effort: Small**

While Micrometer tracing with Brave is configured for distributed tracing to Zipkin, there's no MDC (Mapped Diagnostic Context) configuration to include trace IDs in log output. Log lines from different services for the same request cannot be correlated without Zipkin.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

All inter-service calls use raw OpenFeign clients with no circuit breaker (Resilience4j, Spring Cloud Circuit Breaker). If the core-banking-service goes down:
- Fund Transfer Service will fail every request with connection timeout
- Utility Payment Service will fail every request
- User Service registration will fail
- No fallback behavior is defined anywhere

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No Feign retry configuration, Spring Retry, or Resilience4j retry is configured. A single transient network failure causes immediate request failure.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No Feign client timeouts, connection timeouts, or read timeouts are configured anywhere in the codebase. Services use default timeouts which are typically too generous (60+ seconds) for a banking application.

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

When downstream calls fail, there are no fallback strategies:
- No cached responses
- No graceful degradation
- No user-friendly error messages
- Transactions left in `PENDING`/`PROCESSING` state permanently

### 7.5 No Transaction Compensation / Saga Pattern

**Severity: Critical | Effort: Large**

The fund transfer flow spans two services (Fund Transfer Service → Core Banking Service) with no distributed transaction management:
1. Fund Transfer Service saves entity with `PENDING` status
2. Calls Core Banking to execute transfer
3. If the Feign call succeeds but the subsequent `save()` fails, the fund transfer entity will have stale `PENDING` status but funds have already moved
4. If the Feign call fails with a network error after Core Banking processed the request, the transfer may execute but the local record shows `PENDING`
5. No compensation mechanism to reverse a partially completed transfer

### 7.6 No Rate Limiting

**Severity: Medium | Effort: Small**

The API Gateway has no rate limiting configuration. A malicious or buggy client could overwhelm the system.

### 7.7 No Bulkhead Pattern

**Severity: Medium | Effort: Medium**

All Feign calls to core-banking-service share the same thread pool. A slow response from one endpoint could exhaust all threads and block other operations.

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|---|---|---|---|
| 2.1 | Catch-all returns 400 for all errors | Critical | Small | Error Handling |
| 2.2 | Exception stack trace leaked to client | Critical | Small | Error Handling |
| 4.1 | No input validation | Critical | Medium | Security |
| 4.2 | Hardcoded credentials in source control | Critical | Small | Security |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.5 | No transaction compensation / Saga | Critical | Large | Resilience |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 2.3 | Inconsistent error response format | High | Small | Error Handling |
| 2.4 | EntityNotFoundException returns 400 | High | Small | Error Handling |
| 2.5 | No Feign error handling | High | Medium | Error Handling |
| 3.1 | Minimal test coverage | High | Large | Testing |
| 4.4 | No role-based access control | High | Medium | Security |
| 4.5 | Password in DTO without masking | High | Small | Security |
| 6.1 | No structured logging | High | Medium | Observability |
| 6.2 | Sensitive data in logs | High | Small | Observability |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 7.4 | No fallback behavior | High | Medium | Resilience |
| 1.1 | No multi-project Gradle build | Medium | Small | Code Organization |
| 2.6 | SimpleBankingGlobalException message shadow | Medium | Small | Error Handling |
| 3.2 | No integration test infrastructure | Medium | Large | Testing |
| 3.3 | Context load tests fail without infra | Medium | Small | Testing |
| 4.3 | CSRF disabled without justification | Medium | Small | Security |
| 4.6 | Keycloak singleton not thread-safe | Medium | Small | Security |
| 4.7 | No dependency vulnerability scanning | Medium | Small | Security |
| 5.1 | Raw ResponseEntity without types | Medium | Small | API Design |
| 5.3 | Inconsistent resource naming | Medium | Small | API Design |
| 5.4 | No pagination metadata | Medium | Small | API Design |
| 5.5 | OpenAPI dependency mismatch | Medium | Small | API Design |
| 6.3 | No custom health checks | Medium | Small | Observability |
| 6.4 | No custom metrics | Medium | Medium | Observability |
| 6.6 | No log correlation | Medium | Small | Observability |
| 7.6 | No rate limiting | Medium | Small | Resilience |
| 7.7 | No bulkhead pattern | Medium | Medium | Resilience |
| 1.3 | Inconsistent package structure | Low | Small | Code Organization |
| 1.4 | Mapper instantiation anti-pattern | Low | Small | Code Organization |
| 5.2 | No API versioning strategy | Low | Small | API Design |
| 5.6 | PATCH uses PUT semantics | Low | Small | API Design |
| 6.5 | Tracing config not documented locally | Low | Small | Observability |
