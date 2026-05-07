# Internet Banking Microservices - Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each service is a standalone Gradle project with its own `gradlew`, `gradle/wrapper`, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to manage shared dependencies, plugin versions, or common configuration.

**Impact:** Dependency version drift between services (e.g., MySQL connector version, Lombok version), duplicated build configuration, and no single command to build/test all services.

**Current state:** 7 independent `build.gradle` files with duplicated Spring Boot 3.2.4, Spring Cloud 2023.0.0, and plugin declarations.

### 1.2 Duplicated Code Across Services

**Severity: Medium | Effort: Medium**

The following classes are copy-pasted across multiple services with slight variations:

| Duplicated Class | Services |
|---|---|
| `BaseMapper` | core-banking, user, fund-transfer, utility-payment |
| `AuditAware` / `AuditConfig` / `AuditorAwareConfig` | user, fund-transfer, utility-payment |
| `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` | user, fund-transfer, utility-payment |
| `ErrorResponse` / `SimpleBankingGlobalException` / `GlobalExceptionHandler` | core-banking, user, fund-transfer, utility-payment |
| `GlobalErrorCode` | core-banking, user |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Impact:** Bug fixes or improvements must be applied to every copy individually. Inconsistencies can creep in silently.

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package structures vary across services:

- core-banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- user-service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `exception`, `configuration.audit`, `configuration.feign`, `configuration.filter`, `configuration.keycloak`
- fund-transfer: `model.dto`, `model.dto.request`, `model.dto.response`, `model.entity`, `model.mapper`, `model.repository`, `service`, `service.rest.client`, `controller`, `exception`, `configuration`, `configuration.audit`, `configuration.filter`
- utility-payment: `model.dto`, `model.entity`, `model.mapper`, `model.rest.request`, `model.rest.response`, `repository`, `service`, `service.rest`, `controller`, `exception`, `configuration`, `configuration.audit`, `configuration.filter`

Notable inconsistencies:
- Repository package: `repository` vs `model.repository`
- DTO location: `model.dto.request` vs `model.rest.request`
- Feign client package: `service.rest.client` vs `service.rest`

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated directly in service classes instead of being injected as Spring beans:

```java
private UserMapper userMapper = new UserMapper();           // AccountService, UserService
private FundTransferMapper mapper = new FundTransferMapper(); // FundTransferService
```

**Impact:** Not testable via mocking, bypasses Spring lifecycle, inconsistent with the rest of the DI-based architecture.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` in every service returns `400 Bad Request` for all exceptions, including:
- Entity not found (should be `404`)
- Insufficient funds (could be `422 Unprocessable Entity`)
- Unexpected server errors (should be `500`)

```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

### 2.2 Exception Details Leaked to Clients

**Severity: High | Effort: Small**

The catch-all exception handler returns the full Java exception object as the response body:

```java
.body("Exception occur inside API " + e);
```

This exposes internal stack traces, class names, and potentially sensitive information to API consumers.

### 2.3 Inconsistent Error Response Format

**Severity: Medium | Effort: Small**

- Domain exceptions return structured `ErrorResponse` objects with `{code, message}`
- Generic exceptions return a plain string `"Exception occur inside API " + e`
- API consumers cannot reliably parse error responses

### 2.4 Missing Exception Types

**Severity: Medium | Effort: Small**

Several failure modes have no dedicated exception handling:
- Feign client errors (connection failures, timeouts, downstream 4xx/5xx) are not caught or translated
- Keycloak failures (connection issues, auth failures) are caught generically in `KeycloakUserService.readUser()` but other operations have no error handling
- Database constraint violations have no specific handler
- Request body validation errors have no handler (no `@Valid` annotations used)

### 2.5 No Error Codes in Fund Transfer / Utility Payment Services

**Severity: Low | Effort: Small**

Only `core-banking-service` and `user-service` define `GlobalErrorCode`. The fund-transfer and utility-payment services have no error code system at all.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Test Classes | Type | Coverage |
|---|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Unit (Mockito) | Service layer only; controllers and repositories untested |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` (empty) | Context load | No real tests |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` (empty) | Context load | No real tests |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` (empty) | Context load | No real tests |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` (empty) | Context load | No real tests |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` (empty) | Context load | No real tests |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` (empty) | Context load | No real tests |

Only 3 real test classes exist in the entire project, all in core-banking-service.

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller layer (no `@WebMvcTest` or `@SpringBootTest` with `MockMvc`)
- Repository layer (no `@DataJpaTest`)
- Service-to-service communication
- Database migrations (Flyway)
- Full request/response flows

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to verify that Feign client interfaces match the provider APIs. Breaking changes to core-banking-service APIs would silently break downstream consumers.

### 3.4 Empty Context Load Tests Will Fail

**Severity: Medium | Effort: Small**

The default `@SpringBootTest` context load tests in most services will fail when run without the full infrastructure (Config Server, Eureka, MySQL, Keycloak) because they attempt to bootstrap the full application context.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid` or `@Validated` annotations are used on any `@RequestBody` parameter. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) exist on any DTO.

Examples of unvalidated inputs:
- Fund transfer: `fromAccount`, `toAccount`, `amount` can be null or negative
- User registration: `email`, `password`, `identification` can be null or empty
- Utility payment: `providerId`, `amount`, `account` can be null or invalid

### 4.2 Hardcoded Credentials in Docker Compose

**Severity: High | Effort: Small**

Credentials are hardcoded in `docker-compose.yml`:
- MySQL root password: `woVERANKliGharym`
- Keycloak admin password: `password`
- Keycloak DB password: `password`
- MySQL app user password: `oPItyPticIAt` (in `privileges.sql`)

These should be externalized to environment variables or secrets management.

### 4.3 CSRF Disabled Without Documentation

**Severity: Medium | Effort: Small**

CSRF protection is explicitly disabled in the API Gateway:

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

While typical for stateless JWT APIs, there is no documentation explaining this decision.

### 4.4 All Actuator Endpoints Publicly Accessible

**Severity: High | Effort: Small**

All actuator endpoints are allowed without authentication:

```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

This exposes health, info, env, metrics, and potentially heap dump endpoints to unauthenticated users.

### 4.5 Keycloak Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-thread-safe lazy initialization pattern:

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

Race condition during startup could create multiple instances.

### 4.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured on any endpoint. The registration endpoint (publicly accessible) and transaction endpoints are vulnerable to abuse.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No dependency vulnerability scanning plugins (e.g., OWASP Dependency Check, Snyk) are configured in any `build.gradle`.

### 4.8 Test Credentials in README

**Severity: Low | Effort: Small**

Production-like credentials are published in the README:
```
Test Credentials : ib_admin@javatodev.com / 5V7huE3G86uB
```

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return raw `ResponseEntity` without generic type parameters:

```java
public ResponseEntity getBankAccount(...)   // should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)     // should be ResponseEntity<FundTransferResponse>
```

Only `UserController` in user-service uses typed responses (`ResponseEntity<User>`).

**Impact:** No compile-time type safety, weaker OpenAPI/Swagger documentation generation.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

While `/api/v1/` is used in all URL paths, there is no documented versioning strategy and no mechanism to support multiple API versions simultaneously.

### 5.3 No Standardized Pagination Response

**Severity: Medium | Effort: Small**

Paginated endpoints accept Spring's `Pageable` parameter but return raw `List<T>` instead of a pagination wrapper containing `totalElements`, `totalPages`, `page`, `size`, etc.

```java
public ResponseEntity<List<User>> readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

Clients have no way to know total count or navigate pages.

### 5.4 Inconsistent URL Patterns

**Severity: Low | Effort: Small**

- Mixed snake_case and kebab-case: `/bank-account/{account_number}`, `/util-account/{account_name}`
- Abbreviations: `/util-account` vs `/utility-payment`
- Verb in URL: `/bank-users/register` (should be `POST /bank-users`)

### 5.5 No OpenAPI Spec Generation Endpoint Verified

**Severity: Low | Effort: Small**

While `springdoc-openapi-starter-webflux-ui` is included as a dependency, it is the WebFlux variant used in non-reactive (servlet) services (core-banking, user, fund-transfer, utility-payment). The correct dependency for servlet-based services should be `springdoc-openapi-starter-webmvc-ui`.

### 5.6 Missing DELETE and PUT Operations

**Severity: Low | Effort: Medium**

No endpoints exist for:
- Deleting users, accounts, transfers, or payments
- Full replacement updates (PUT) for any resource
- Account creation/management (only read endpoints exist in core-banking)

---

## 6. Observability

### 6.1 Logging Inconsistency

**Severity: Medium | Effort: Small**

- Some controllers log incoming requests; others don't
- Log messages use inconsistent formatting:
  - `"Reading account by ID {}"` vs `"Got fund transfer request from API {}"` vs `"Utility payment processing {}"`
- `toString()` called explicitly on request objects in log statements (potential NPE, performance)
- String concatenation in log statements: `log.info("Sending fund transfer request {}" + request.toString())` (note `+` instead of `,`)
- No structured logging (JSON) configured
- No correlation IDs in log messages (trace IDs from Micrometer exist but not logged explicitly)

### 6.2 No Custom Health Checks

**Severity: Medium | Effort: Small**

Services rely only on default Spring Boot Actuator health indicators. No custom health checks for:
- Keycloak connectivity (user-service)
- Core-banking-service availability (from consumer services)
- Config Server availability
- Database connection pool health

### 6.3 No Metrics Endpoints Beyond Defaults

**Severity: Low | Effort: Medium**

While `spring-boot-starter-actuator` is included, there are no custom metrics for:
- Fund transfer count/amount
- Utility payment count/amount
- User registration rate
- Transaction success/failure rates
- No Prometheus endpoint configured (mentioned in README but no `micrometer-registry-prometheus` dependency)

### 6.4 Zipkin Tracing Configuration Not in Source

**Severity: Low | Effort: Small**

Tracing dependencies are included, but sampling rate and Zipkin URL configuration are delegated entirely to the remote config server. No fallback or default tracing configuration exists in the application source.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker pattern is implemented. All Feign clients make synchronous calls to core-banking-service without any circuit breaker (e.g., Resilience4j, Hystrix). If core-banking-service is down or slow:
- fund-transfer-service will hang indefinitely
- utility-payment-service will hang indefinitely
- user-service will hang indefinitely

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for:
- Feign client calls (transient network failures will fail immediately)
- Keycloak API calls
- Database operations

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign client connections and read timeouts (defaults to infinite in some versions)
- Database connection timeouts
- Keycloak client timeouts

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback logic exists for any external dependency failure:
- If Keycloak is down, user registration fails with an unhandled exception
- If core-banking-service is down, fund transfers and payments fail with raw Feign exceptions
- No graceful degradation pattern

### 7.5 No Transaction Compensation / Saga Pattern

**Severity: High | Effort: Large**

The fund transfer and utility payment flows span multiple services without any compensation mechanism:

1. Fund transfer: local record saved as `PENDING` -> Feign call to core-banking -> update local record to `SUCCESS`
2. If the Feign call succeeds but the local update fails, the transfer is completed in core-banking but stuck at `PENDING` locally
3. If the Feign call fails after the debit in core-banking, there is no rollback
4. No saga orchestrator, no outbox pattern, no idempotency keys

### 7.6 Balance Calculation Bug (Not Resilience, But Critical)

**Severity: Critical | Effort: Small**

In `TransactionService.internalFundTransfer()` and `utilPayment()`, the `availableBalance` is calculated incorrectly:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
//                                        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                                        This is ALREADY subtracted, so availableBalance is double-subtracted
```

The `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` has already been reduced, effectively subtracting the amount twice from `availableBalance`. The same bug exists for the credit side and in `utilPayment()`.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 1.2 | Duplicated code across services | Code Organization | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 2.1 | All errors return HTTP 400 | Error Handling | High | Small |
| 2.2 | Exception details leaked to clients | Error Handling | High | Small |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | Missing exception types for Feign/Keycloak/DB | Error Handling | Medium | Small |
| 2.5 | No error codes in fund-transfer/utility-payment | Error Handling | Low | Small |
| 3.1 | Minimal test coverage (only core-banking has tests) | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Empty context load tests will fail | Testing | Medium | Small |
| 4.1 | No input validation | Security | Critical | Medium |
| 4.2 | Hardcoded credentials in Docker Compose | Security | High | Small |
| 4.3 | CSRF disabled without documentation | Security | Medium | Small |
| 4.4 | All actuator endpoints publicly accessible | Security | High | Small |
| 4.5 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.6 | No rate limiting | Security | Medium | Medium |
| 4.7 | No dependency vulnerability scanning | Security | Medium | Small |
| 4.8 | Test credentials in README | Security | Low | Small |
| 5.1 | Raw ResponseEntity without type parameters | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Medium |
| 5.3 | No standardized pagination response | API Design | Medium | Small |
| 5.4 | Inconsistent URL patterns | API Design | Low | Small |
| 5.5 | Wrong OpenAPI dependency (WebFlux in servlet apps) | API Design | Low | Small |
| 5.6 | Missing DELETE and PUT operations | API Design | Low | Medium |
| 6.1 | Logging inconsistency | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.3 | No custom metrics / Prometheus not configured | Observability | Low | Medium |
| 6.4 | Zipkin config not in source | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No transaction compensation / saga pattern | Resilience | High | Large |
| 7.6 | Balance calculation bug (double subtraction) | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 4 |
| High | 9 |
| Medium | 16 |
| Low | 8 |
| **Total** | **37** |
