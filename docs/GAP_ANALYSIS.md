# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Shared Library / Multi-Module Build

**Gap**: Each microservice is an independent Gradle project with no shared module. Common classes (`AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `AppAuthUserFilter`) are **copy-pasted** across 3-4 services with minor variations.

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

**Evidence**: `AuditAware` appears in `fund-transfer-service`, `utility-payment-service`, and `user-service` with identical code. `BaseMapper<E,D>` is duplicated in all four business services. `GlobalExceptionHandler` is duplicated in four services with slightly different implementations.

### 1.2 Inconsistent Package Structure Across Services

**Gap**: The package hierarchy varies between services:
- `core-banking-service`: `repository/` at top level, DTOs in `model.dto.*`
- `user-service`: `model.repository/`, DTOs in `model.dto.*`, REST models in `model.rest.response.*`
- `fund-transfer-service`: `model.repository/`, Feign clients in `service.rest.client.*`
- `utility-payment-service`: `repository/` at top level, Feign clients in `service.rest.*`

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

### 1.3 Mappers Instantiated Manually Instead of Injected

**Gap**: All mapper instances (`BankAccountMapper`, `UserMapper`, `FundTransferMapper`, etc.) are created with `new` inside service classes rather than being Spring beans. This defeats dependency injection and makes testing harder.

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

**Evidence**: `AccountService.java:21` - `private BankAccountMapper bankAccountMapper = new BankAccountMapper();`

### 1.4 Configuration Classes Inconsistently Organized

**Gap**: The `configuration/` package structure varies:
- `user-service`: `configuration.keycloak.*`, `configuration.feign.*`, `configuration.filter.*`, `configuration.audit.*`
- `fund-transfer-service`: `configuration.*` (flat), `configuration.filter.*`, `configuration.audit.*`
- `utility-payment-service`: `configuration.*` (flat), `configuration.filter.*`, `configuration.audit.*`

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns 400 for All Errors

**Gap**: Every service's `GlobalExceptionHandler` catches `Exception.class` and returns `400 Bad Request` with a raw string body (`"Exception occur inside API " + e`), regardless of the actual error type. This means:
- `404 Not Found` scenarios return `400`
- `500 Internal Server Error` scenarios return `400`
- Stack traces are leaked to the client in the error message

| Severity | Effort |
|----------|--------|
| **Critical** | **Medium** |

**Evidence**: `core-banking-service/.../GlobalExceptionHandler.java:23-28`, identical pattern in all four services.

### 2.2 Inconsistent Error Response Format

**Gap**: The `GlobalExceptionHandler` has two handler methods that return different shapes:
- `SimpleBankingGlobalException` -> `ErrorResponse {code, message}` (structured JSON)
- `Exception` -> plain `String` (not JSON)

Clients cannot reliably parse error responses.

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 2.3 Raw `ResponseEntity` Without Type Parameters

**Gap**: Almost all controller methods return raw `ResponseEntity` instead of `ResponseEntity<T>`. This eliminates compile-time type safety and degrades OpenAPI documentation quality.

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

**Evidence**: `AccountController.java:27` - `public ResponseEntity getBankAccount(...)` (should be `ResponseEntity<BankAccount>`). Only `UserController` in user-service uses typed responses.

### 2.4 No Feign Error Decoder in Fund Transfer or Utility Payment Services

**Gap**: The `fund-transfer-service` and `utility-payment-service` use a `CustomFeignClientConfiguration` that only sets the log level to `FULL`. They have **no error decoder**. Only the `user-service` has a `CustomFeignErrorDecoder`. When core-banking returns an error, the fund-transfer and utility-payment services will get a raw Feign exception instead of a properly mapped business exception.

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

### 2.5 Exception Hierarchy Design Issues

**Gap**: `SimpleBankingGlobalException` extends `RuntimeException` but also uses Lombok `@AllArgsConstructor(String code, String message)`. The `message` field shadows `Throwable.message`, leading to confusing behavior. The exception is also used as a deserialization target in `CustomFeignErrorDecoder` (via Jackson), which requires `@NoArgsConstructor` - a code smell for exceptions.

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

---

## 3. Testing

### 3.1 Near-Zero Test Coverage Across Most Services

**Gap**: Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The remaining **six services** have only auto-generated Spring Boot context-loading tests that are empty or would fail without infrastructure.

| Severity | Effort |
|----------|--------|
| **Critical** | **Large** |

**Evidence**:
| Service | Test Files | Meaningful Tests |
|---------|-----------|-----------------|
| core-banking-service | 4 | 3 service-level test classes (~15 test methods) |
| internet-banking-user-service | 1 | 0 (empty context test) |
| internet-banking-fund-transfer-service | 1 | 0 (empty context test) |
| internet-banking-utility-payment-service | 1 | 0 (empty context test) |
| internet-banking-api-gateway | 1 | 0 (empty context test) |
| internet-banking-config-server | 1 | 0 (empty context test) |
| internet-banking-service-registry | 1 | 0 (empty context test) |

### 3.2 No Integration Tests

**Gap**: No integration tests exist that verify inter-service communication, database operations with real/testcontainers databases, or end-to-end request flows.

| Severity | Effort |
|----------|--------|
| **High** | **Large** |

### 3.3 No Contract Tests Between Services

**Gap**: No Pact, Spring Cloud Contract, or equivalent contract testing exists. Changes to core-banking-service's API could break all three consuming services without detection until runtime.

| Severity | Effort |
|----------|--------|
| **High** | **Large** |

### 3.4 No Test Configuration for Context Tests

**Gap**: The application context tests (`*ApplicationTests.java`) in services requiring MySQL, Keycloak, or Eureka will fail in isolation because there is no test profile that stubs out these external dependencies. Only `core-banking-service` has an `application.yml` in `src/test/resources` with H2 configuration.

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

---

## 4. Security

### 4.1 No Input Validation

**Gap**: **No `@Valid`, `@NotNull`, `@Size`, `@Min`, or any Bean Validation annotations** exist on any request DTO or controller method parameter across the entire codebase. A fund transfer with a negative amount or a null account number would pass through to the database layer.

| Severity | Effort |
|----------|--------|
| **Critical** | **Medium** |

**Evidence**: `FundTransferRequest` has `amount` (BigDecimal), `fromAccount`, `toAccount` - all without validation. `UtilityPaymentRequest` similarly has no constraints.

### 4.2 Hardcoded Secrets in Docker Compose

**Gap**: Database passwords and Keycloak credentials are hardcoded in `docker-compose.yml`:
- `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `KC_DB_PASSWORD: password`
- `KEYCLOAK_ADMIN_PASSWORD: password`

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 4.3 Test Credentials in README

**Gap**: Production-looking test credentials are committed to the README:
```
Test Credentials : ib_admin@javatodev.com / 5V7huE3G86uB
```

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

### 4.4 CSRF Disabled at Gateway

**Gap**: `SecurityConfiguration.java:35` explicitly disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While common for pure API services, this should be a conscious, documented decision.

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

### 4.5 No Authorization Beyond Authentication

**Gap**: The gateway enforces JWT authentication but there is **no role-based or scope-based authorization**. Any authenticated user can:
- Approve other users' registrations
- Transfer funds from any account
- View any user's data

| Severity | Effort |
|----------|--------|
| **Critical** | **Large** |

### 4.6 Password Handling Concerns

**Gap**: In `UserService.createUser()`, the user's plain-text password flows through the `User` DTO (which is also used for responses). The `User` DTO has a `password` field that could be serialized back to the client in the response body.

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 4.7 Keycloak Client Secret in Externalized Config

**Gap**: The Keycloak `client-secret` is configured via Spring Cloud Config Server which fetches from a public GitHub repository. This means the client secret may be visible in a public Git repo.

| Severity | Effort |
|----------|--------|
| **High** | **Medium** |

### 4.8 No Dependency Vulnerability Scanning

**Gap**: No OWASP dependency-check, Snyk, or Dependabot configuration exists. Dependencies like `commons-io` (used in `CustomFeignErrorDecoder` via `org.apache.commons.io.IOUtils`) may have known vulnerabilities.

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

---

## 5. API Design

### 5.1 Inconsistent URL Naming Conventions

**Gap**: Endpoint paths use mixed conventions:
- `bank-account` and `util-account` (abbreviation)
- `bank-users` and `user` (plural vs singular)
- `fund-transfer` and `util-payment` (abbreviation vs full name)
- `utility-payment` (full name, different from `util-payment` in core)

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 5.2 No API Versioning Strategy

**Gap**: All endpoints use `/api/v1/` but there is no documented versioning strategy, no mechanism for version negotiation, and no plan for backward compatibility.

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

### 5.3 No Pagination Metadata in Responses

**Gap**: Paginated endpoints (`GET /api/v1/user`, `GET /api/v1/transfer`, etc.) return raw `List<T>` instead of a paginated wrapper with total count, page number, and page size. Clients cannot know if more pages exist.

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 5.4 No Filtering or Search Capabilities

**Gap**: List endpoints support only Spring Data `Pageable` parameters (page, size, sort). No filtering by date range, status, account number, or any business-relevant criteria.

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 5.5 Missing OpenAPI/Swagger Documentation Details

**Gap**: While `springdoc-openapi` is included and basic `@Tag` and `@Operation` annotations are present, the documentation lacks:
- Request/response schema examples
- Error response documentation (`@ApiResponse`)
- Parameter descriptions
- The raw `ResponseEntity` return types degrade auto-generated schemas

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

### 5.6 No HATEOAS or Resource Links

**Gap**: Responses contain no links to related resources (e.g., a fund transfer response doesn't link to the transaction details or account balances).

| Severity | Effort |
|----------|--------|
| **Low** | **Medium** |

### 5.7 Non-Standard HTTP Methods

**Gap**: `PATCH /api/v1/bank-users/update/{id}` includes the verb "update" in the URL, which is redundant with the HTTP method. RESTful convention would be `PATCH /api/v1/bank-users/{id}`.

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

---

## 6. Observability

### 6.1 Inconsistent Logging Practices

**Gap**: Logging is ad-hoc across services:
- Some methods log entry but not exit or errors
- Log levels are inconsistent (all use `info` for request logging, even in `AppAuthUserFilter`)
- No structured logging format (JSON)
- No correlation ID propagation beyond what Micrometer provides automatically

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 6.2 No Health Check Customization

**Gap**: While Spring Boot Actuator is included in all services, there are no custom health indicators for:
- Database connectivity status
- Keycloak availability
- Downstream service availability
- Config server reachability

Default `/actuator/health` only shows basic UP/DOWN status.

| Severity | Effort |
|----------|--------|
| **Medium** | **Small** |

### 6.3 No Metrics Endpoints / Prometheus Integration

**Gap**: Despite Prometheus being listed in the tech stack (README), there is **no `micrometer-registry-prometheus`** dependency in any `build.gradle`. No custom metrics are defined for:
- Transaction counts/rates
- Transfer amounts
- Error rates per endpoint
- Feign client latency

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 6.4 Distributed Tracing Not Verified

**Gap**: While tracing dependencies (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) are included, the Zipkin endpoint is configured via the external config server. There is no verification that traces are actually being sent, and no custom span annotations for business-critical operations like fund transfers.

| Severity | Effort |
|----------|--------|
| **Low** | **Small** |

### 6.5 Sensitive Data in Logs

**Gap**: Controllers log full request objects using `toString()`:
- `FundTransferController`: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` - logs account numbers and amounts
- `UserController`: `log.info("Creating user with {}", request.toString())` - could log passwords (User DTO has a password field)

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap**: There are **no circuit breaker** implementations (Resilience4j, Hystrix, or Spring Cloud Circuit Breaker) on any Feign client or inter-service call. If core-banking-service is down, all upstream services will hang and eventually timeout.

| Severity | Effort |
|----------|--------|
| **Critical** | **Medium** |

### 7.2 No Retry Policies

**Gap**: No retry configuration exists for Feign clients or any REST calls. Transient network failures will cause immediate request failure.

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 7.3 No Timeout Configuration

**Gap**: No explicit HTTP client timeouts are configured for Feign clients. Default timeouts (often infinite or very long) will be used, which can cause thread pool exhaustion under load.

| Severity | Effort |
|----------|--------|
| **High** | **Small** |

### 7.4 No Fallback Behavior

**Gap**: No fallback methods or default responses are defined for when downstream services are unavailable. A core-banking outage cascades to all upstream services as 500 errors.

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 7.5 No Rate Limiting

**Gap**: No rate limiting exists at the API gateway or service level. The system is vulnerable to abuse or accidental overload.

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 7.6 No Idempotency for Financial Operations

**Gap**: Fund transfers and utility payments have **no idempotency keys**. Network retries or client retries could result in duplicate transactions, which is a serious concern for a financial application.

| Severity | Effort |
|----------|--------|
| **Critical** | **Medium** |

### 7.7 No Graceful Degradation or Bulkhead Pattern

**Gap**: No thread pool isolation or bulkhead pattern is implemented. A slow response from one downstream service could exhaust the thread pool and affect all other endpoints.

| Severity | Effort |
|----------|--------|
| **Medium** | **Medium** |

### 7.8 Balance Update Race Condition

**Gap**: In `TransactionService.internalFundTransfer()`, the account balance read-then-update is not protected by pessimistic locking. Concurrent transfers from the same account could lead to incorrect balances.

| Severity | Effort |
|----------|--------|
| **Critical** | **Small** |

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 1 | 2 | 2 | 0 | 5 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 2 | 3 | 2 | 1 | 8 |
| API Design | 0 | 1 | 3 | 3 | 7 |
| Observability | 0 | 2 | 2 | 1 | 5 |
| Resilience | 3 | 2 | 3 | 0 | 8 |
| **Total** | **7** | **13** | **14** | **7** | **41** |
