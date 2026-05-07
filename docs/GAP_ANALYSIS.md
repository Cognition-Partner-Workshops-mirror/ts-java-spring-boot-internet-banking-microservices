# Engineering Standards Gap Analysis

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Matrix](#8-summary-matrix)

---

## 1. Code Organization

### 1.1 No Gradle Multi-Project Build

**Severity: Medium** | **Effort: Medium**

Each service has its own standalone `build.gradle` with duplicated plugin versions, dependency management, and Spring Cloud BOM declarations. There is no root `settings.gradle` or `build.gradle` to coordinate versions centrally.

**Evidence:**
- All 7 services independently declare `spring-boot:3.2.4`, `dependency-management:1.1.4`, `spring-cloud:2023.0.0`
- Plugin version `com.gorylenko.gradle-git-properties:2.4.2` is repeated in 6 of 7 build files
- Upgrading Spring Boot or Spring Cloud requires editing 7 files

**Best Practice:** Use a Gradle multi-project build with a root `build.gradle` that declares shared plugin versions via `plugins { }` block and a `subprojects { }` block for common dependency management.

---

### 1.2 Duplicated Exception Classes Across Services

**Severity: Medium** | **Effort: Medium**

The following classes are copy-pasted across multiple services with minor variations:

| Class | Services |
|-------|----------|
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |

**Best Practice:** Extract shared code into a common library module (e.g., `banking-common`) published as a local Gradle dependency.

---

### 1.3 Inconsistent Code Formatting

**Severity: Low** | **Effort: Small**

- Mixed indentation: Some `build.gradle` files use tabs (utility-payment, config-server), others use spaces (core-banking, api-gateway)
- Inconsistent import styles (wildcard vs. explicit imports across services)
- No code formatter or linting tool configured (no Checkstyle, SpotBugs, or PMD)

---

### 1.4 Mapper Pattern Instantiation Anti-Pattern

**Severity: Low** | **Effort: Small**

Mappers are instantiated inline rather than injected via Spring DI:

```java
private UserMapper userMapper = new UserMapper();  // in multiple services
private FundTransferMapper mapper = new FundTransferMapper();
```

While functional, this bypasses Spring's lifecycle management and makes testing harder. Consider using MapStruct or Spring-managed beans.

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400 Bad Request

**Severity: Critical** | **Effort: Small**

Every `GlobalExceptionHandler` across all services maps **all** exceptions (including `EntityNotFoundException`) to HTTP 400:

```java
@ExceptionHandler(SimpleBankingGlobalException.class)
protected ResponseEntity handleGlobalException(...) {
    return ResponseEntity.badRequest().body(...);
}

@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```

**Issues:**
- `EntityNotFoundException` should return **404 Not Found**
- `InsufficientFundsException` should return **422 Unprocessable Entity** (or 409 Conflict)
- Generic `Exception` catch-all returns 400 instead of **500 Internal Server Error**
- Stack traces and internal details leak to clients via `"Exception occur inside API " + e`

---

### 2.2 Exception Details Leaked to Clients

**Severity: Critical** | **Effort: Small**

The generic exception handler concatenates the full exception object into the response body:

```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

This exposes:
- Full class names and package structure
- Stack trace information
- Internal implementation details
- Potential database error messages

**Best Practice:** Return a generic error message (e.g., "Internal server error") and log the full exception server-side.

---

### 2.3 No Validation on Request Bodies

**Severity: High** | **Effort: Small**

None of the DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Valid`):

- `FundTransferRequest`: No validation on `fromAccount`, `toAccount`, `amount`
- `UtilityPaymentRequest`: No validation on `providerId`, `amount`, `account`
- `User` (registration): No validation on `email`, `password`, `identification`

Null or empty values will propagate until they cause `NullPointerException` or database constraint violations.

---

### 2.4 Missing Feign Error Handling

**Severity: High** | **Effort: Medium**

The `FundTransferService` and `UtilityPaymentService` call core-banking via Feign with no error handling:

```java
FundTransferResponse fundTransferResponse = bankingCoreFeignClient.fundTransfer(request);
optFundTransfer.setStatus(TransactionStatus.SUCCESS);
```

If the Feign call fails:
- The `FundTransferEntity` remains in `PENDING` status permanently
- No rollback or compensation logic exists
- The raw Feign exception propagates to the client

**Best Practice:** Implement `CustomFeignErrorDecoder` (exists in user-service but not in fund-transfer or utility-payment), add try-catch with status updates to `FAILED`, and implement idempotency.

---

### 2.5 Inconsistent Error Response Format

**Severity: Medium** | **Effort: Small**

- `SimpleBankingGlobalException` handler returns structured `ErrorResponse { code, message }`
- Generic `Exception` handler returns a raw string: `"Exception occur inside API " + e`
- Core banking `ErrorResponse` uses `@Builder`, fund-transfer `ErrorResponse` uses constructor
- No consistent error envelope across all endpoints

---

## 3. Testing

### 3.1 Only Core Banking Service Has Meaningful Tests

**Severity: High** | **Effort: Large**

Test file inventory:

| Service | Test Files | Meaningful Tests |
|---------|-----------|-----------------|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | **Yes** - 20 unit tests with Mockito |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | **No** - Empty context load test only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | **No** - Empty context load test only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | **No** - Empty context load test only |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | **No** - Empty context load test only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | **No** - Empty context load test only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | **No** - Empty context load test only |

**Missing coverage:**
- No tests for `UserService` in user-service (Keycloak integration, registration flow)
- No tests for `FundTransferService` (Feign client mocking, status transitions)
- No tests for `UtilityPaymentService`
- No controller/integration tests anywhere
- No tests for error handling paths

---

### 3.2 No Integration Tests

**Severity: High** | **Effort: Large**

- No `@SpringBootTest` integration tests with real database
- No Testcontainers usage for MySQL integration testing
- No WireMock or MockServer for Feign client testing
- Context-load tests are disabled or would fail without external services (Keycloak, MySQL, Config Server)

---

### 3.3 No Contract Tests Between Services

**Severity: Medium** | **Effort: Large**

Services communicate via Feign clients but there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify:
- Request/response schema compatibility between producer and consumer
- API evolution doesn't break downstream services

---

### 3.4 No Test Coverage Reporting

**Severity: Low** | **Effort: Small**

- No JaCoCo or similar coverage plugin configured in any `build.gradle`
- No coverage thresholds enforced
- No coverage reports generated during builds

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical** | **Effort: Small**

Multiple credentials are hardcoded in version-controlled files:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | PostgreSQL password: `password` |
| `privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |
| `temp_data.sql` | User emails in seed data |

**Best Practice:** Use environment variables, Docker secrets, or a vault for all credentials. Use `.env` files (gitignored) for local development.

---

### 4.2 CSRF Disabled Without Justification

**Severity: Medium** | **Effort: Small**

```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

CSRF protection is disabled at the API Gateway. While this is common for stateless APIs using JWT tokens, there is no documentation or comment explaining the rationale.

---

### 4.3 No Input Validation (Cross-Reference with 2.3)

**Severity: High** | **Effort: Small**

No `@Valid` annotation on any `@RequestBody` parameter. No Jakarta Bean Validation constraints on DTOs. This creates risk for:
- SQL injection via JPA (low risk due to parameterized queries but defense-in-depth is missing)
- Business logic errors from malformed data (negative amounts, null accounts)
- Denial of service via excessively large payloads

---

### 4.4 Keycloak Client Uses Static Singleton

**Severity: Medium** | **Effort: Small**

```java
private static Keycloak keycloakInstance = null;

public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Issues:**
- Not thread-safe (race condition on initialization)
- Static singleton prevents proper dependency injection and testing
- No token refresh handling - client credentials token may expire

---

### 4.5 No Role-Based Access Control Beyond Gateway

**Severity: Medium** | **Effort: Medium**

- The API Gateway authenticates requests but only distinguishes authenticated vs. unauthenticated
- No role-based authorization (e.g., `ROLE_ADMIN` for user approval, `ROLE_USER` for transfers)
- Downstream services have no security configuration - they trust the gateway completely
- Any authenticated user can approve other users, initiate any transfer, etc.

---

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium** | **Effort: Small**

- No OWASP Dependency-Check plugin in Gradle
- No Snyk, Trivy, or similar tool in the build pipeline
- Dependencies are pinned to specific versions (good) but not actively monitored for CVEs

---

## 5. API Design

### 5.1 Raw ResponseEntity Without Type Parameters

**Severity: Medium** | **Effort: Small**

Most controllers return raw `ResponseEntity` without generic type parameters:

```java
public ResponseEntity getBankAccount(...)    // Should be ResponseEntity<BankAccount>
public ResponseEntity fundTransfer(...)      // Should be ResponseEntity<FundTransferResponse>
```

This loses compile-time type safety and makes OpenAPI/Swagger documentation incomplete (response schemas won't be generated).

**Note:** `UserController` in user-service correctly uses typed responses (`ResponseEntity<User>`, `ResponseEntity<List<User>>`).

---

### 5.2 Inconsistent URL Naming Conventions

**Severity: Low** | **Effort: Small**

| Service | Pattern | Example |
|---------|---------|---------|
| core-banking | snake_case path vars | `/bank-account/{account_number}` |
| user-service | Numeric ID | `/bank-users/{id}` |
| fund-transfer | No path variable | `POST /api/v1/transfer` |
| core-banking | Mixed | `/util-account/{account_name}` |

**Best Practice:** Standardize on kebab-case for paths and camelCase or snake_case for path variables consistently.

---

### 5.3 No API Versioning Strategy

**Severity: Low** | **Effort: Medium**

All APIs use `/api/v1/` prefix, which is good. However:
- No documented versioning strategy
- No mechanism for supporting multiple API versions simultaneously
- No content negotiation or header-based versioning

---

### 5.4 Pagination Returns Raw Lists

**Severity: Medium** | **Effort: Small**

Paginated endpoints return `List<T>` instead of a page wrapper:

```java
public List<User> readUsers(Pageable pageable) {
    return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
}
```

The `Page` metadata (total elements, total pages, current page) is discarded. Clients have no way to know if more data exists.

---

### 5.5 No Filtering or Search Capabilities

**Severity: Low** | **Effort: Medium**

- No query parameter filtering on list endpoints
- No search by date range, status, amount range, etc.
- Transaction history is not exposed at all (no GET endpoint for transactions)

---

### 5.6 OpenAPI/Swagger Misconfigured

**Severity: Medium** | **Effort: Small**

All services include `springdoc-openapi-starter-webflux-ui` but:
- This is the **WebFlux** starter - only the API Gateway uses WebFlux; the other services use Spring MVC (Web)
- The correct dependency for MVC services should be `springdoc-openapi-starter-webmvc-ui`
- Swagger annotations (`@Tag`, `@Operation`) are present but response schemas are incomplete due to raw `ResponseEntity`

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium** | **Effort: Small**

- Some controllers log request details, others don't
- `UtilityPaymentController` has no logging at all
- Log levels are not standardized (mix of `log.info` for normal operations)
- No structured logging (JSON format) configured
- Sensitive data potentially logged: `request.toString()` may include account numbers and amounts

```java
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
// Logs: fromAccount, toAccount, amount
```

---

### 6.2 No Custom Health Checks

**Severity: Medium** | **Effort: Small**

- Spring Boot Actuator is included in all services with `/actuator/health`
- No custom health indicators for:
  - Database connectivity
  - Keycloak connectivity
  - Downstream service availability
  - RabbitMQ connectivity (when implemented)
- Default health endpoint only reports `UP/DOWN` without granular component status

---

### 6.3 No Custom Metrics Endpoints

**Severity: Low** | **Effort: Medium**

- Micrometer is included via `spring-boot-starter-actuator` but:
  - No custom business metrics (transfer count, payment volume, error rates)
  - No Prometheus endpoint configured (`/actuator/prometheus`)
  - No Grafana dashboards or alerting defined
  - README mentions Prometheus but it's not configured in any service

---

### 6.4 Distributed Tracing Configuration Incomplete

**Severity: Medium** | **Effort: Small**

- Tracing libraries are included in all services (Brave + Zipkin reporter)
- Feign micrometer integration is included for trace propagation
- However, configuration is externalized to a Git config repo and cannot be verified from this codebase
- No sampling rate configuration visible
- No custom spans for business operations

---

### 6.5 No Centralized Logging Infrastructure

**Severity: Low** | **Effort: Large**

- No ELK/EFK stack or similar centralized logging
- No log aggregation configured
- Docker Compose logs are the only logging mechanism
- No log correlation IDs beyond Zipkin trace IDs

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical** | **Effort: Medium**

None of the Feign clients implement circuit breakers:

```java
@FeignClient(value = "core-banking-service", configuration = CustomFeignClientConfiguration.class)
public interface BankingCoreFeignClient {
    // No fallback, no circuit breaker
}
```

If core-banking-service goes down:
- All fund transfer requests will hang/fail
- All utility payment requests will hang/fail
- All user registration requests will fail
- Thread pools will be exhausted (cascading failure)

**Best Practice:** Add `spring-cloud-starter-circuitbreaker-resilience4j` with Feign integration, define fallback methods.

---

### 7.2 No Retry Policies

**Severity: High** | **Effort: Small**

- No Spring Retry or Resilience4j retry configuration
- Transient failures (network blips, temporary service unavailability) cause immediate failure
- No distinction between retryable and non-retryable errors

---

### 7.3 No Timeout Configuration

**Severity: High** | **Effort: Small**

- No Feign client timeout configuration visible in the codebase
- Default Feign timeouts may be too high (causing thread exhaustion) or too low (causing premature failures)
- No connection pool configuration for Feign/HTTP clients
- `wait-for-it.sh` timeouts (50s) only apply at startup, not at runtime

---

### 7.4 No Fallback Behavior

**Severity: High** | **Effort: Medium**

- No Feign fallback factories or fallback classes defined
- No graceful degradation strategies
- If any downstream service is unavailable, the entire request fails with an unhandled exception

---

### 7.5 Non-Atomic Financial Transactions

**Severity: Critical** | **Effort: Large**

The fund transfer in `TransactionService.internalFundTransfer()` is **not truly atomic** across the distributed system:

```java
// 1. Debit source account
fromBankAccountEntity.setActualBalance(...subtract...);
bankAccountRepository.save(fromBankAccountEntity);  // <-- committed

// 2. Credit destination account
toBankAccountEntity.setActualBalance(...add...);
bankAccountRepository.save(toBankAccountEntity);  // <-- what if this fails?
```

While `@Transactional` covers the local database, the orchestrating services (fund-transfer-service, utility-payment-service) make Feign calls with no distributed transaction support:

1. Fund transfer service saves `PENDING` record
2. Calls core banking (which debits and credits)
3. If the response is lost or fund-transfer-service crashes after step 2: the transfer happened but the status remains `PENDING`
4. No saga pattern, no compensation, no idempotency keys

---

### 7.6 Balance Calculation Bug

**Severity: Critical** | **Effort: Small**

In `TransactionService.internalFundTransfer()`:

```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// availableBalance = actualBalance - amount - amount (DOUBLE SUBTRACTION!)
```

After the first line, `actualBalance` is already reduced. The second line subtracts `amount` again from the already-reduced balance. Same bug exists for the credit side and in `utilPayment()`.

**Example:** Transfer $100 from account with $500:
- `actualBalance` = 500 - 100 = **400** (correct)
- `availableBalance` = 400 - 100 = **300** (incorrect, should be 400)

---

## 8. Summary Matrix

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No Gradle multi-project build | Code Organization | Medium | Medium |
| 1.2 | Duplicated exception classes across services | Code Organization | Medium | Medium |
| 1.3 | Inconsistent code formatting | Code Organization | Low | Small |
| 1.4 | Mapper pattern instantiation anti-pattern | Code Organization | Low | Small |
| 2.1 | All exceptions return HTTP 400 | Error Handling | Critical | Small |
| 2.2 | Exception details leaked to clients | Error Handling | Critical | Small |
| 2.3 | No validation on request bodies | Error Handling | High | Small |
| 2.4 | Missing Feign error handling | Error Handling | High | Medium |
| 2.5 | Inconsistent error response format | Error Handling | Medium | Small |
| 3.1 | Only core-banking has meaningful tests | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | Medium | Large |
| 3.4 | No test coverage reporting | Testing | Low | Small |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small |
| 4.2 | CSRF disabled without justification | Security | Medium | Small |
| 4.3 | No input validation | Security | High | Small |
| 4.4 | Keycloak client uses static singleton | Security | Medium | Small |
| 4.5 | No role-based access control beyond gateway | Security | Medium | Medium |
| 4.6 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without type parameters | API Design | Medium | Small |
| 5.2 | Inconsistent URL naming conventions | API Design | Low | Small |
| 5.3 | No API versioning strategy | API Design | Low | Medium |
| 5.4 | Pagination returns raw lists | API Design | Medium | Small |
| 5.5 | No filtering or search capabilities | API Design | Low | Medium |
| 5.6 | OpenAPI/Swagger misconfigured | API Design | Medium | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.3 | No custom metrics endpoints | Observability | Low | Medium |
| 6.4 | Distributed tracing configuration incomplete | Observability | Medium | Small |
| 6.5 | No centralized logging infrastructure | Observability | Low | Large |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | High | Medium |
| 7.5 | Non-atomic financial transactions | Resilience | Critical | Large |
| 7.6 | Balance calculation bug | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|----------|-------|
| **Critical** | 7 |
| **High** | 8 |
| **Medium** | 14 |
| **Low** | 7 |

### Effort Distribution

| Effort | Count |
|--------|-------|
| **Small** | 19 |
| **Medium** | 11 |
| **Large** | 6 |
