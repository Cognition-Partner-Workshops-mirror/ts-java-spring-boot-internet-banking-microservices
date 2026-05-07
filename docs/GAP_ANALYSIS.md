# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven categories. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity scale:** Critical > High > Medium > Low
**Effort scale:** Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is a standalone Gradle project with its own `settings.gradle` and Gradle wrapper. There is no root-level `settings.gradle` or `build.gradle` to orchestrate builds. This makes it impossible to:
- Build all services with a single command
- Share dependency versions or plugins across services
- Enforce consistent build configurations

**Current state:** 7 independent Gradle projects, each duplicating the Spring Boot 3.2.4 / Spring Cloud 2023.0.0 version declarations.

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are **copy-pasted identically** across 3-4 services with no shared library:

| Duplicated Class | Services |
|---|---|
| `BaseMapper<E, D>` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` (MappedSuperclass) | user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | All 4 business services |
| `ErrorResponse` | All 4 business services |
| `GlobalExceptionHandler` | All 4 business services |
| `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |

**Risk:** Bug fixes or improvements must be applied to every copy independently; divergence is inevitable.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

While all services use the `com.javatodev.finance` base package, sub-package naming is inconsistent:

| Concern | core-banking | user-service | fund-transfer | utility-payment |
|---|---|---|---|---|
| Repositories | `repository` | `model.repository` | `model.repository` | `repository` |
| Request DTOs | `model.dto.request` | `model.dto` | `model.dto.request` | `model.rest.request` |
| Response DTOs | `model.dto.response` | `model.rest.response` | `model.dto.response` | `model.rest.response` |
| Feign clients | N/A | `service.rest` | `service.rest.client` | `service.rest` |
| Feign config | N/A | `configuration.feign` | `configuration` | `configuration` |

### 1.4 Mappers Not Spring-Managed Beans

**Severity: Low | Effort: Small**

All mappers (`BankAccountMapper`, `UserMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` inside service classes rather than being Spring beans. This prevents dependency injection and makes testing harder.

```java
// Current pattern (in every service)
private UserMapper userMapper = new UserMapper();
```

---

## 2. Error Handling

### 2.1 Inconsistent GlobalExceptionHandler Implementations

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` exists in all 4 business services but has subtle differences:

- **core-banking-service** and **utility-payment-service**: Uses `ErrorResponse.builder()`.
- **fund-transfer-service**: Uses `new ErrorResponse(code, message)` constructor.
- **All services**: The catch-all `Exception` handler returns a **raw string** (`"Exception occur inside API " + e`) instead of a structured `ErrorResponse`, leaking stack trace information.

### 2.2 All Errors Return HTTP 400

**Severity: High | Effort: Small**

Every exception handler returns `ResponseEntity.badRequest()` (HTTP 400) regardless of the actual error type:

| Exception | Current Status | Correct Status |
|---|---|---|
| `EntityNotFoundException` | 400 | **404** |
| `InsufficientFundsException` | 400 | **422** (Unprocessable Entity) |
| `UserAlreadyRegisteredException` | 400 | **409** (Conflict) |
| `InvalidEmailException` | 400 | 400 (correct) |
| `InvalidBankingUserException` | 400 | **404** |
| Generic `Exception` | 400 | **500** |

### 2.3 Raw ResponseEntity Without Type Parameters

**Severity: Medium | Effort: Small**

All controller methods return `ResponseEntity` without type parameters (raw type), losing compile-time type safety:

```java
// Current (every controller)
public ResponseEntity getBankAccount(...) { ... }

// Should be
public ResponseEntity<BankAccount> getBankAccount(...) { ... }
```

### 2.4 Exception Message Leaks in Catch-All Handler

**Severity: High | Effort: Small**

The generic exception handler concatenates the full exception object into the response body:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This exposes internal class names, stack traces, and potentially sensitive information (database connection strings, SQL errors) to API consumers.

### 2.5 No Error Handling for Feign Client Failures

**Severity: High | Effort: Medium**

- **fund-transfer-service** and **utility-payment-service** have basic Feign configurations but **no custom error decoder**. If core-banking-service returns an error, the Feign default decoder throws a generic `FeignException`.
- **user-service** has a `CustomFeignErrorDecoder` that properly extracts error responses, but this pattern is not applied to the other services.
- **No circuit breaker or fallback** is configured on any Feign client.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Files | Test Type | Coverage |
|---|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | Unit (Mockito) + context load | Service layer only; no controller tests |
| user-service | `InternetBankingUserServiceApplicationTests` | Context load only | **0% business logic coverage** |
| fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only | **0% business logic coverage** |
| utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only | **0% business logic coverage** |
| api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only | No security config tests |
| config-server | `InternetBankingConfigServerApplicationTests` | Context load only | N/A |
| service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load only | N/A |

**Summary:** Only `core-banking-service` has real unit tests. The other 3 business services have zero meaningful tests.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist that verify:
- Database interactions with real database (e.g., Testcontainers)
- Feign client communication between services
- API Gateway routing and security rules
- End-to-end transaction flows

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

No Spring Cloud Contract or Pact tests exist to verify the API contracts between services. Given the tight coupling via Feign clients (e.g., fund-transfer-service depends on core-banking-service's exact response shape), contract changes can silently break consumers.

### 3.4 Context Load Tests Will Fail Without Infrastructure

**Severity: Medium | Effort: Small**

Most `*ApplicationTests` use `@SpringBootTest` but don't mock external dependencies. For example, `InternetBankingApiGatewayApplicationTests` will fail without a running Keycloak (needs JWK endpoint). These tests are effectively unusable in CI without Docker infrastructure.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No controller or DTO uses Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Valid`, etc.):

```java
// Current - no validation at all
@PostMapping("/fund-transfer")
public ResponseEntity fundTransfer(@RequestBody FundTransferRequest fundTransferRequest) { ... }

// Missing: @Valid, and FundTransferRequest has no validation annotations
```

A request with null `fromAccount`, null `toAccount`, or negative `amount` will produce a `NullPointerException` or corrupt data rather than a clean validation error.

### 4.2 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

| Location | Credential |
|---|---|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin: `admin` / `password` |
| `docker-compose.yml` | Keycloak DB: `keycloak` / `password` |
| `privileges.sql` | MySQL user: `javatodev_development` / `oPItyPticIAt` |
| Test `application.yml` (user-service) | Keycloak client-secret: `e8548d56-d743-45ef-8655-063c9cd96759` |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |

While some of these are development-only, the pattern sets a dangerous precedent and the Keycloak client secret in `application.yml` could leak to production.

### 4.3 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

The API gateway disables CSRF protection:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
This is acceptable for a stateless JWT-based API but should be documented with a rationale.

### 4.4 No Role-Based Access Control (RBAC)

**Severity: Medium | Effort: Medium**

The gateway security configuration only checks `authenticated()` for all protected endpoints. There is no role-based access control. For example:
- Any authenticated user can approve other users (`PATCH /user/api/v1/bank-users/update/{id}`)
- Any authenticated user can list all users and all transactions
- No distinction between admin and regular user roles

### 4.5 Keycloak Client Configured as Singleton with Static Field

**Severity: Medium | Effort: Small**

`KeycloakProperties` uses a `private static Keycloak keycloakInstance` field. This is not thread-safe (potential double-initialization race condition) and prevents proper lifecycle management.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No dependency vulnerability scanning tool is configured (e.g., OWASP Dependency-Check, Snyk, Dependabot). Given the use of third-party libraries (Keycloak, MySQL connector, etc.), this is a significant blind spot.

---

## 5. API Design

### 5.1 Inconsistent URL Naming Conventions

**Severity: Low | Effort: Small**

| Service | Pattern | Example |
|---|---|---|
| core-banking | snake_case path params | `/bank-account/{account_number}` |
| user-service | kebab-case paths | `/bank-users/register` |
| fund-transfer | clean REST | `/transfer` |
| utility-payment | kebab-case | `/utility-payment` |

Path parameter naming mixes `snake_case` (`{account_number}`, `{account_name}`) with clean names (`{identification}`, `{id}`).

### 5.2 Non-RESTful Endpoint Design

**Severity: Medium | Effort: Small**

Several endpoints violate REST conventions:

| Current | RESTful Alternative | Issue |
|---|---|---|
| `POST /register` | `POST /bank-users` | Action verb in URL |
| `PATCH /update/{id}` | `PATCH /bank-users/{id}` | Redundant action verb |
| `POST /fund-transfer` | `POST /transactions` | Should be a transaction resource |
| `GET /api/v1/user` (paginated) | `GET /api/v1/users` | Should use plural noun |

### 5.3 No API Versioning Strategy

**Severity: Low | Effort: Small**

All endpoints use `/api/v1/` prefix, which is good. However, there is no documented versioning strategy, and the gateway routing strips prefixes inconsistently. No content-type versioning or header-based versioning is in place.

### 5.4 Pagination Implementation Is Implicit

**Severity: Medium | Effort: Small**

Paginated endpoints accept Spring's `Pageable` as a query parameter, but:
- No documentation of supported query parameters (`page`, `size`, `sort`)
- No default page size limits (could return entire dataset)
- No pagination metadata in response (total count, total pages, has-next)
- Response is a raw `List`, not a `Page` wrapper

### 5.5 No Filtering or Sorting Documented

**Severity: Low | Effort: Small**

List endpoints support basic Spring Data pagination via query params but no custom filtering (e.g., filter transactions by date range, filter users by status).

### 5.6 Wrong OpenAPI Starter Dependency

**Severity: Medium | Effort: Small**

All servlet-based services (core-banking, user, fund-transfer, utility-payment) incorrectly use:
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
```
This is the **WebFlux** starter, but these services use **Spring MVC** (Web). The correct dependency is:
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
```

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request data; others don't.
- Log messages include raw `toString()` calls on request objects, potentially logging sensitive data (passwords, account numbers).
- No structured logging format (JSON) is configured.
- No correlation ID propagation beyond Zipkin trace IDs.

```java
// Logs password in plain text during registration
log.info("Creating user with {}", request.toString());
```

### 6.2 Actuator Endpoints Exposed Without Security

**Severity: Medium | Effort: Small**

All actuator endpoints are explicitly allowed without authentication in the gateway:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
```
This exposes health, info, metrics, environment, and potentially sensitive configuration data to unauthenticated users.

### 6.3 No Custom Health Checks

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Database connectivity
- Keycloak availability
- Downstream service reachability

### 6.4 No Metrics Endpoint Configuration

**Severity: Medium | Effort: Small**

While Micrometer tracing is configured (for Zipkin), there is no Prometheus metrics endpoint configured despite Prometheus being listed in the technology stack. No `micrometer-registry-prometheus` dependency exists in any `build.gradle`.

### 6.5 Zipkin Tracing Configuration Not Visible

**Severity: Low | Effort: Small**

Tracing libraries are included in all services, but the Zipkin endpoint URL and sampling rate are configured in the external Git config repository (not in this codebase), making it difficult to audit tracing configuration.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker library (Resilience4j, Hystrix) is configured. If `core-banking-service` becomes unavailable:
- `fund-transfer-service` will block on Feign calls until timeout
- `utility-payment-service` will block on Feign calls until timeout
- `user-service` will fail on registration

This can cascade into thread pool exhaustion across all services.

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No Spring Retry or Resilience4j retry configuration exists. Transient network failures between services will immediately fail the request with no retry attempt.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration exists for:
- Feign client HTTP calls (uses default, which can be very long)
- Database connection pool
- Keycloak admin client calls

A slow downstream service will hold threads indefinitely.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No Feign fallback classes are defined. When `core-banking-service` is down, the user gets a raw Feign exception rather than a graceful degradation message.

### 7.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

The API gateway has no rate limiting configured. A single client can flood the system with requests, potentially exhausting database connections or service threads.

### 7.6 Non-Atomic Balance Updates

**Severity: Critical | Effort: Medium**

The fund transfer logic in `TransactionService.internalFundTransfer()` reads the account balance, modifies it in Java, and saves it back. Under concurrent requests, this creates a **race condition** where two transfers could both read the same balance and both succeed, resulting in an overdrawn account. There is no database-level pessimistic or optimistic locking on the `banking_core_account` table.

Additionally, there is a **balance calculation bug** in `internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^ availableBalance is set to actualBalance MINUS amount AGAIN (double subtraction)
```

The same bug exists in `utilPayment()`:
```java
fromAccount.setActualBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()));
// ^ availableBalance = actualBalance - amount (should just be = actualBalance)
```

### 7.7 No Distributed Transaction Management

**Severity: High | Effort: Large**

The fund transfer and utility payment flows span two services (orchestration + core-banking). If the core-banking call succeeds but the orchestration service crashes before updating its local status, the transaction will be stuck in `PENDING`/`PROCESSING` forever. No Saga pattern, outbox pattern, or compensation mechanism exists.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers not Spring-managed beans | Code Organization | Low | Small |
| 2.1 | Inconsistent GlobalExceptionHandler | Error Handling | High | Small |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.3 | Raw ResponseEntity without type params | Error Handling | Medium | Small |
| 2.4 | Exception message leaks | Error Handling | High | Small |
| 2.5 | No Feign error handling in 2 services | Error Handling | High | Medium |
| 3.1 | Minimal test coverage | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context load tests fail without infra | Testing | Medium | Small |
| 4.1 | No input validation | Security | Critical | Medium |
| 4.2 | Hardcoded credentials | Security | Critical | Small |
| 4.3 | CSRF disabled without docs | Security | Low | Small |
| 4.4 | No RBAC | Security | Medium | Medium |
| 4.5 | Thread-unsafe Keycloak singleton | Security | Medium | Small |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Inconsistent URL naming | API Design | Low | Small |
| 5.2 | Non-RESTful endpoints | API Design | Medium | Small |
| 5.3 | No versioning strategy documented | API Design | Low | Small |
| 5.4 | Pagination lacks metadata | API Design | Medium | Small |
| 5.5 | No filtering/sorting | API Design | Low | Small |
| 5.6 | Wrong OpenAPI starter dependency | API Design | Medium | Small |
| 6.1 | Inconsistent/unsafe logging | Observability | Medium | Small |
| 6.2 | Actuator endpoints unprotected | Observability | Medium | Small |
| 6.3 | No custom health checks | Observability | Low | Small |
| 6.4 | No Prometheus metrics | Observability | Medium | Small |
| 6.5 | Zipkin config not auditable | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No rate limiting | Resilience | Medium | Medium |
| 7.6 | Non-atomic balance updates + bug | Resilience | Critical | Medium |
| 7.7 | No distributed transaction management | Resilience | High | Large |

**Critical items: 5** | **High items: 10** | **Medium items: 14** | **Low items: 8**
