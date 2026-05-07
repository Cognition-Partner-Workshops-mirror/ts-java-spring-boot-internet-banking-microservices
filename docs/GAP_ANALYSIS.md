# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven categories. Each gap is rated by **Severity** and **Remediation Effort**.

> **Severity Scale**: Critical (production risk / security vulnerability) | High (significant quality issue) | Medium (maintainability / DX concern) | Low (polish / nice-to-have)
>
> **Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Gap**: Each service has its own independent `build.gradle` with duplicated plugin versions, dependency management, and Spring Cloud BOM declarations. There is no root `settings.gradle` or `build.gradle` to unify the build.

**Impact**: Version drift risk (e.g., one service could accidentally use a different Spring Boot version), repetitive maintenance when upgrading dependencies.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

### 1.2 Duplicated Code Across Services

**Gap**: The following classes are copy-pasted across 3-4 services with minor variations:
- `AuditAware` (user-service, fund-transfer, utility-payment)
- `BaseMapper` (core-banking, user-service, fund-transfer, utility-payment)
- `ErrorResponse` (all 4 domain services)
- `SimpleBankingGlobalException` (all 4 domain services)
- `GlobalExceptionHandler` (all 4 domain services)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (user-service, fund-transfer, utility-payment)

**Impact**: Bug fixes or improvements must be applied in 3-4 places; inconsistencies have already crept in (e.g., fund-transfer uses `new ErrorResponse()` while others use `ErrorResponse.builder()`).

| Severity | Effort |
|----------|--------|
| High | Medium |

### 1.3 No Shared Library / Common Module

**Gap**: There is no `common` or `shared` module for cross-cutting DTOs, exception classes, mappers, or audit infrastructure.

**Impact**: Forces duplication (see 1.2). New services would need to copy boilerplate again.

| Severity | Effort |
|----------|--------|
| High | Medium |

### 1.4 Mapper Instantiation Outside DI

**Gap**: Mappers are instantiated inline (`private UserMapper userMapper = new UserMapper()`) instead of being Spring beans. This bypasses dependency injection and makes testing harder.

| Severity | Effort |
|----------|--------|
| Low | Small |

### 1.5 Inconsistent Package Structure

**Gap**: Package structure varies across services:
- Core banking: `repository` package at root level
- User service: `model.repository` sub-package
- Fund transfer: `model.repository` sub-package
- Utility payment: `repository` at root level

Similar inconsistency exists for REST client packages (`service.rest` vs `service.rest.client`).

| Severity | Effort |
|----------|--------|
| Low | Small |

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns Raw Exception Object

**Gap**: All four `GlobalExceptionHandler` classes have a catch-all `@ExceptionHandler({Exception.class})` that returns:
```java
"Exception occur inside API " + e
```
This leaks stack traces, internal class names, and potentially sensitive information to API consumers.

| Severity | Effort |
|----------|--------|
| **Critical** | Small |

### 2.2 All Errors Return HTTP 400 Bad Request

**Gap**: Every exception -- including `EntityNotFoundException`, `InsufficientFundsException`, and unexpected server errors -- returns `400 Bad Request`. There is no differentiation between client errors (4xx) and server errors (5xx), nor between "not found" (404) and "bad request" (400).

| Severity | Effort |
|----------|--------|
| High | Small |

### 2.3 No Validation Error Handling

**Gap**: There is no `MethodArgumentNotValidException` handler or `@Valid` annotation on any `@RequestBody`. If Jakarta Bean Validation annotations were added to DTOs, validation failures would fall through to the generic handler.

| Severity | Effort |
|----------|--------|
| High | Small |

### 2.4 Inconsistent Error Response Format

**Gap**: Custom exceptions return `ErrorResponse { code, message }` but the catch-all handler returns a plain string. Consumers cannot rely on a single error response schema.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 2.5 No Feign Error Decoder in All Services

**Gap**: Only `internet-banking-user-service` has a `CustomFeignErrorDecoder`. Fund transfer and utility payment services reference `CustomFeignClientConfiguration` but the actual error decoding behavior is inconsistent. Feign 4xx/5xx responses may not be properly translated into domain exceptions.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Gap**: Test inventory across the entire codebase:

| Service | Test Files | Test Methods | Coverage |
|---------|-----------|-------------|----------|
| `core-banking-service` | 4 (3 unit + 1 context) | ~20 | Service layer only |
| `internet-banking-user-service` | 1 (context load) | 1 | None |
| `internet-banking-fund-transfer-service` | 1 (context load) | 1 | None |
| `internet-banking-utility-payment-service` | 1 (context load) | 1 | None |
| `internet-banking-api-gateway` | 1 (context load) | 1 | None |
| `internet-banking-service-registry` | 1 (context load) | 1 | None |
| `internet-banking-config-server` | 1 (context load) | 1 | None |

Only `core-banking-service` has meaningful unit tests. All other services have only a single `contextLoads()` placeholder test.

| Severity | Effort |
|----------|--------|
| **Critical** | Large |

### 3.2 No Integration Tests

**Gap**: No `@SpringBootTest` tests with `@AutoConfigureMockMvc` or `WebTestClient`. No tests verify controller routing, request/response serialization, or Spring Security configuration.

| Severity | Effort |
|----------|--------|
| High | Large |

### 3.3 No Contract Tests Between Services

**Gap**: No Spring Cloud Contract or Pact tests to verify that Feign client interfaces remain compatible with the provider service's actual API. Breaking changes in core-banking would silently break downstream consumers.

| Severity | Effort |
|----------|--------|
| High | Large |

### 3.4 No Test Containers or Embedded Database Strategy

**Gap**: Test `application.yml` files exist but no `@TestContainers` or explicit H2/embedded database configuration for integration testing. H2 is declared as a test dependency but there is no evidence of it being configured.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Gap**: The following secrets are committed to the repository:
- MySQL root password in `docker-compose.yml`: `woVERANKliGharym`
- MySQL user password in `privileges.sql`: `oPItyPticIAt`
- Keycloak admin password: `password`
- Keycloak DB password: `password`
- Test credentials in `README.md`: `ib_admin@javatodev.com / 5V7huE3G86uB`

| Severity | Effort |
|----------|--------|
| **Critical** | Small |

### 4.2 No Input Validation on Request Bodies

**Gap**: None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) have Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). No `@Valid` annotations on controller method parameters.

**Risk**: Negative transfer amounts, null account numbers, empty emails, and other malformed input would pass through to the service layer unchecked.

| Severity | Effort |
|----------|--------|
| **Critical** | Small |

### 4.3 CSRF Disabled Without Documentation

**Gap**: The API Gateway disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is acceptable for a stateless JWT API, there is no comment or documentation explaining the decision.

| Severity | Effort |
|----------|--------|
| Low | Small |

### 4.4 Keycloak Client Uses Static Singleton (Thread Safety)

**Gap**: `KeycloakProperties.getInstance()` uses a non-synchronized static singleton pattern. In a multi-threaded Spring Boot application, this could lead to a race condition during initialization.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 4.5 No Rate Limiting or Request Throttling

**Gap**: The API Gateway has no rate limiting configuration. Financial transaction endpoints are exposed without any protection against abuse.

| Severity | Effort |
|----------|--------|
| High | Medium |

### 4.6 Password Stored in DTO, Not Excluded from Responses

**Gap**: The `User` DTO in user-service has a `password` field that is sent in the registration request but is not annotated with `@JsonProperty(access = WRITE_ONLY)` or `@JsonIgnore` on serialization. The password could be leaked in response bodies.

| Severity | Effort |
|----------|--------|
| **Critical** | Small |

### 4.7 No Dependency Vulnerability Scanning

**Gap**: No OWASP Dependency Check plugin, Snyk integration, or GitHub Dependabot configuration exists. Vulnerable transitive dependencies would go undetected.

| Severity | Effort |
|----------|--------|
| High | Small |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Gap**: Most controller methods return `ResponseEntity` (raw type) instead of `ResponseEntity<BankAccount>`, `ResponseEntity<List<FundTransfer>>`, etc. This causes compiler warnings and makes OpenAPI/Swagger documentation less useful.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 5.2 No API Versioning Strategy

**Gap**: API paths include `/v1/` but there is no documented versioning strategy (URL path, header, media type). No mechanism exists to run v1 and v2 simultaneously.

| Severity | Effort |
|----------|--------|
| Low | Small |

### 5.3 Inconsistent Pagination Response Format

**Gap**: Paginated endpoints return `List<T>` directly instead of a wrapper with pagination metadata (`totalPages`, `totalElements`, `currentPage`, `size`). Consumers cannot determine if there are more pages.

| Severity | Effort |
|----------|--------|
| High | Small |

### 5.4 No Filtering or Sorting Documentation

**Gap**: While Spring Data `Pageable` is accepted (implicitly supporting `sort` parameters), there is no explicit API documentation or validation for supported sort fields.

| Severity | Effort |
|----------|--------|
| Low | Small |

### 5.5 Non-RESTful URL Patterns

**Gap**: Some endpoints deviate from REST conventions:
- `POST /register` instead of `POST /bank-users`
- `PATCH /update/{id}` instead of `PATCH /bank-users/{id}`
- `GET /bank-account/{account_number}` uses snake_case path variables mixed with camelCase elsewhere

| Severity | Effort |
|----------|--------|
| Medium | Medium |

### 5.6 OpenAPI/Swagger Dependency Mismatch

**Gap**: Services include `springdoc-openapi-starter-webflux-ui` but the business services use Spring MVC (not WebFlux). Only the API Gateway uses WebFlux. The correct dependency for MVC services is `springdoc-openapi-starter-webmvc-ui`.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 5.7 No Standardized Response Envelope

**Gap**: Successful responses return the raw object; error responses return either `ErrorResponse` or a plain string. There is no consistent response envelope (e.g., `{ data: T, errors: [], meta: {} }`).

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

## 6. Observability

### 6.1 Inconsistent Logging

**Gap**: Logging patterns vary:
- Some controllers log incoming requests, others don't (e.g., `UtilityPaymentController` has no `@Slf4j`)
- Log levels are not standardized (all use `log.info()` for everything, including errors in `KeycloakUserService`)
- Sensitive data is logged: `request.toString()` in controllers may log passwords, account numbers, or amounts
- Fund transfer service has a string concatenation bug in logging: `"Sending fund transfer request {}" + request.toString()` (should use `{}` placeholder)

| Severity | Effort |
|----------|--------|
| High | Small |

### 6.2 Health Check Endpoints Not Explicitly Configured

**Gap**: While `spring-boot-starter-actuator` is included in all services, there is no explicit configuration of which health indicators are enabled, what detail level is exposed, or which endpoints are active. The config server's settings are not visible in this repo.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 6.3 No Custom Metrics

**Gap**: No custom Micrometer metrics are defined. Business-critical metrics (transfer count, payment success/failure rates, average transfer amount, Keycloak API latency) are not tracked.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

### 6.4 No Structured Logging (JSON)

**Gap**: Default Spring Boot logging uses plain-text format. In a containerized environment, structured JSON logging (e.g., via Logstash Logback encoder) would enable better log aggregation and querying.

| Severity | Effort |
|----------|--------|
| Medium | Small |

### 6.5 Trace Context Not Guaranteed in All Error Paths

**Gap**: When the catch-all exception handler fires, it does not include the trace ID in the error response. Correlating client-side errors to server-side traces requires manual lookup.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap**: No Resilience4j or Hystrix circuit breaker is configured on any Feign client. If `core-banking-service` goes down, all downstream services will accumulate blocked threads and eventually fail with cascading timeouts.

| Severity | Effort |
|----------|--------|
| **Critical** | Medium |

### 7.2 No Retry Policies

**Gap**: No retry configuration on Feign clients. Transient network errors or temporary core-banking unavailability will immediately fail the request. Spring Retry or Resilience4j Retry is not configured.

| Severity | Effort |
|----------|--------|
| High | Small |

### 7.3 No Timeout Configuration

**Gap**: No explicit HTTP client timeout, Feign client timeout, or connection pool configuration is visible. Default timeouts are typically very long and can cause thread exhaustion under load.

| Severity | Effort |
|----------|--------|
| High | Small |

### 7.4 No Fallback Behavior

**Gap**: When Feign calls fail, the exception propagates directly to the caller with no fallback logic. There is no graceful degradation (e.g., returning cached data, queuing for retry, or returning a partial response).

| Severity | Effort |
|----------|--------|
| Medium | Medium |

### 7.5 No Idempotency Keys for Financial Transactions

**Gap**: The `POST /api/v1/transfer` and `POST /api/v1/utility-payment` endpoints have no idempotency mechanism. If a client retries due to a timeout, the same transfer could be executed twice, causing double-debits.

| Severity | Effort |
|----------|--------|
| **Critical** | Medium |

### 7.6 Inconsistent Transaction Status Management

**Gap**: In `FundTransferService`, the entity is saved as `PENDING`, then the Feign call is made, then the entity is updated to `SUCCESS`. If the Feign call succeeds but the second `save()` fails, or if the application crashes between the two saves, the transfer is completed in core-banking but the local record stays `PENDING`. There is no compensation or reconciliation mechanism.

| Severity | Effort |
|----------|--------|
| **Critical** | Large |

### 7.7 Balance Calculation Bug in Core Banking

**Gap**: In `TransactionService.internalFundTransfer()` and `utilPayment()`, `availableBalance` is calculated by subtracting the amount from `actualBalance` *after* `actualBalance` has already been reduced. This effectively double-subtracts:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// availableBalance = actualBalance - amount - amount (double deduction)
```

| Severity | Effort |
|----------|--------|
| **Critical** | Small |

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 2 | 1 | 2 | 5 |
| Error Handling | 1 | 2 | 2 | 0 | 5 |
| Testing | 1 | 2 | 1 | 0 | 4 |
| Security | 3 | 2 | 1 | 1 | 7 |
| API Design | 0 | 1 | 4 | 2 | 7 |
| Observability | 0 | 1 | 4 | 0 | 5 |
| Resilience | 4 | 2 | 1 | 0 | 7 |
| **Total** | **9** | **12** | **14** | **5** | **40** |
