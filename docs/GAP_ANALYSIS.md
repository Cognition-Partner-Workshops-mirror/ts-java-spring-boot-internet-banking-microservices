# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps organized by category. Each gap includes a severity rating and effort estimate.

**Severity Levels:**
- **Critical:** Security vulnerability or data-integrity bug that could cause financial loss or breach.
- **High:** Significant architectural or reliability issue that will cause problems at scale.
- **Medium:** Deviation from best practice that impacts maintainability or developer productivity.
- **Low:** Polish item or minor inconsistency.

**Effort Levels:**
- **Small:** < 1 day, isolated change.
- **Medium:** 1-3 days, touches multiple files/services.
- **Large:** 1+ week, architectural change or cross-cutting concern.

---

## 1. Code Organization

### GAP-ORG-001: No Multi-Module Gradle Build
**Severity:** Medium | **Effort:** Medium

Each of the 6 services is an independent Gradle project with its own `gradlew`, `gradle/wrapper`, `settings.gradle`, and `build.gradle`. There is no root `build.gradle` or `settings.gradle` to orchestrate builds. This means:
- No single command to build/test all services.
- Gradle wrapper duplicated 6 times.
- Dependency versions managed independently per service (risk of drift).

### GAP-ORG-002: Duplicated Code Across Services
**Severity:** High | **Effort:** Large

The following classes are copy-pasted across 3-4 services with minor variations:
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` (in core-banking, user-service, fund-transfer, utility-payment)
- `AuditAware` (in user-service, fund-transfer, utility-payment)
- `BaseMapper` (in core-banking, user-service, fund-transfer, utility-payment)
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` (in user-service, fund-transfer, utility-payment)
- `CustomFeignClientConfiguration` (in fund-transfer, utility-payment)

No shared library or common module exists.

### GAP-ORG-003: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

- Core-banking: `repository/` at top level.
- User-service: `model/repository/` (repository nested inside model package).
- Fund-transfer: `model/repository/`.
- Utility-payment: `repository/` at top level.
- Feign clients: In `service/rest/` (user-service) vs. `service/rest/client/` (fund-transfer) vs. `service/rest/` (utility-payment).

### GAP-ORG-004: Mapper Instantiation Pattern
**Severity:** Low | **Effort:** Small

Mappers are instantiated with `new` directly in services (e.g., `private UserMapper userMapper = new UserMapper();`) instead of being Spring-managed beans or using a mapping framework like MapStruct. This prevents dependency injection and makes testing harder.

---

## 2. Error Handling

### GAP-ERR-001: Generic Exception Handler Returns 400 for All Errors
**Severity:** High | **Effort:** Medium

Every `GlobalExceptionHandler` has a catch-all `@ExceptionHandler({Exception.class})` that returns HTTP 400 Bad Request for ALL exceptions, including 500-class server errors. The response body is a raw string: `"Exception occur inside API " + e`, which leaks stack trace information.

### GAP-ERR-002: No Differentiated HTTP Status Codes
**Severity:** High | **Effort:** Medium

All custom exceptions return 400 Bad Request regardless of the actual error type:
- `EntityNotFoundException` should return 404.
- `InsufficientFundsException` should return 422 (Unprocessable Entity) or 409 (Conflict).
- `UserAlreadyRegisteredException` should return 409 (Conflict).
- Internal errors should return 500.

### GAP-ERR-003: Inconsistent Error Response Format
**Severity:** Medium | **Effort:** Small

The custom `ErrorResponse` (`{code, message}`) is only used for `SimpleBankingGlobalException` subclasses. The generic `Exception` handler returns a plain string. There is no consistent envelope for error responses.

### GAP-ERR-004: No Feign Error Decoder in Most Services
**Severity:** High | **Effort:** Medium

Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services use the default Feign error handling, meaning HTTP errors from core-banking are wrapped in `FeignException` and propagated as 500 Internal Server errors to the client.

### GAP-ERR-005: No Transaction Failure Handling in Orchestration Services
**Severity:** Critical | **Effort:** Medium

In `FundTransferService.fundTransfer()`, if the Feign call to core-banking fails (network error, timeout, or business error), the local `FundTransferEntity` remains in `PENDING` status forever. There is no:
- Try/catch around the Feign call.
- Status update to `FAILED` on error.
- Compensation/rollback logic.
- Retry mechanism.

The same issue exists in `UtilityPaymentService.utilPayment()`.

---

## 3. Testing

### GAP-TEST-001: Minimal Test Coverage
**Severity:** High | **Effort:** Large

- **core-banking-service:** 3 unit test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) with ~20 test methods. This is the only service with meaningful tests.
- **All other services:** Only have the auto-generated Spring Boot test class (`*ApplicationTests.java`) with a single `contextLoads()` test that likely fails without a database connection.
- **No tests** for controllers, Feign clients, or exception handlers.

### GAP-TEST-002: No Integration Tests
**Severity:** High | **Effort:** Large

There are no integration tests that verify:
- Database operations (repository layer with real/embedded DB).
- Feign client contracts.
- API endpoint behavior with Spring MockMvc / WebTestClient.
- End-to-end flows through the service mesh.

### GAP-TEST-003: No Contract Tests
**Severity:** Medium | **Effort:** Large

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between services. Breaking changes in core-banking-service APIs could silently break downstream services.

### GAP-TEST-004: Test Application Context May Not Load
**Severity:** Medium | **Effort:** Small

The `*ApplicationTests.java` files in non-core services attempt to load the full Spring context, which requires database connections, config server, and Eureka. Test `application.yml` files exist but may not properly mock/override all external dependencies.

---

## 4. Security

### GAP-SEC-001: Hardcoded Database Credentials
**Severity:** Critical | **Effort:** Small

Database credentials are hardcoded in multiple places:
- `docker-compose/mysql/Dockerfile`: `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`
- `docker-compose/docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`
- `docker-compose/mysql/privileges.sql`: `CREATE USER 'javatodev_development'@'%' IDENTIFIED BY 'oPItyPticIAt'`
- Keycloak admin credentials: `admin / password`
- Test credentials in README: `ib_admin@javatodev.com / 5V7huE3G86uB`

### GAP-SEC-002: CSRF Disabled Without Documentation
**Severity:** Medium | **Effort:** Small

`SecurityConfiguration` disables CSRF with `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for a stateless API, this should be documented and alternative protections considered.

### GAP-SEC-003: No Input Validation
**Severity:** Critical | **Effort:** Medium

None of the request DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.):
- `FundTransferRequest`: `fromAccount`, `toAccount`, `amount` are all unvalidated. Null or negative amounts could cause unexpected behavior.
- `UtilityPaymentRequest`: Similarly unvalidated.
- `User` (registration): No email format validation at the DTO level.
- Controllers do not use `@Valid` on `@RequestBody` parameters.

### GAP-SEC-004: Stack Trace Exposure in Error Responses
**Severity:** High | **Effort:** Small

The catch-all exception handler returns `"Exception occur inside API " + e`, which includes the full exception message and potentially stack trace details. This can leak internal implementation details.

### GAP-SEC-005: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

No rate limiting is configured on the API Gateway or individual services. The fund transfer and payment endpoints are vulnerable to abuse.

### GAP-SEC-006: Keycloak Singleton Not Thread-Safe
**Severity:** High | **Effort:** Small

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern (`if (keycloakInstance == null)`). In a multi-threaded environment, multiple instances could be created, leading to race conditions.

### GAP-SEC-007: No Authorization Beyond Authentication
**Severity:** High | **Effort:** Medium

While the API Gateway requires JWT authentication, there is no role-based or resource-based authorization:
- Any authenticated user can access any other user's data.
- Any authenticated user can initiate transfers from any account.
- The `X-Auth-Id` header is set by the gateway but never validated against the requested resources.

### GAP-SEC-008: Internal Service-to-Service Calls Unauthenticated
**Severity:** High | **Effort:** Medium

Core-banking-service endpoints (accounts, transactions) have no authentication. If network policies are not strict, these endpoints are accessible without any credentials.

---

## 5. API Design

### GAP-API-001: Raw `ResponseEntity` Without Type Parameters
**Severity:** Medium | **Effort:** Small

Most controller methods return `ResponseEntity` without type parameters (e.g., `public ResponseEntity getBankAccount(...)` instead of `public ResponseEntity<BankAccount> getBankAccount(...)`). This:
- Loses compile-time type safety.
- Reduces OpenAPI/Swagger documentation quality.
- Makes the API contract unclear.

### GAP-API-002: No API Versioning Strategy
**Severity:** Medium | **Effort:** Medium

While endpoints use `/api/v1/` prefix, there is no documented versioning strategy and no mechanism to support multiple API versions simultaneously.

### GAP-API-003: Inconsistent Resource Naming
**Severity:** Low | **Effort:** Small

- Core-banking uses `bank-account` and `util-account` (abbreviation).
- User-service uses `bank-users` (plural with prefix).
- Fund-transfer uses `transfer` (action-oriented instead of resource-oriented).
- Utility-payment uses `utility-payment`.
- Path variables use `snake_case` (`account_number`) mixed with implicit `camelCase`.

### GAP-API-004: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Small

List endpoints accept `Pageable` but return `List<T>` instead of `Page<T>`. Clients have no way to know total count, total pages, or current page information.

### GAP-API-005: No Filtering or Search Capabilities
**Severity:** Low | **Effort:** Medium

List endpoints only support basic pagination. No filtering by date range, status, account number, or other attributes is supported.

### GAP-API-006: OpenAPI Dependency Mismatch
**Severity:** Medium | **Effort:** Small

Services use `springdoc-openapi-starter-webflux-ui` (WebFlux variant) even though the business services use Spring MVC (not WebFlux). Only the API Gateway actually uses WebFlux. This may cause documentation generation issues.

### GAP-API-007: No Standard Error Response Schema in OpenAPI
**Severity:** Low | **Effort:** Small

Swagger annotations exist for operations but `@ApiResponse` annotations with error schemas are not defined, so API documentation does not describe error response formats.

---

## 6. Observability

### GAP-OBS-001: Inconsistent Logging
**Severity:** Medium | **Effort:** Small

- Some controllers log request details (`log.info("Fund transfer initiated...")`), others do not.
- `FundTransferService` has a string concatenation bug: `log.info("Sending fund transfer request {}" + request.toString())` - the `{}` placeholder is followed by concatenation instead of parameterized logging.
- Log levels, formatting, and what gets logged vary across services.
- No structured logging (JSON format) is configured.

### GAP-OBS-002: No Health Check Customization
**Severity:** Low | **Effort:** Small

Spring Boot Actuator is included in all services, but no custom health indicators are defined for critical dependencies (database connectivity, Keycloak availability, Eureka registration status).

### GAP-OBS-003: No Metrics Endpoints Beyond Defaults
**Severity:** Medium | **Effort:** Medium

While Actuator provides default metrics, there are no custom business metrics:
- Transaction throughput.
- Transfer success/failure rates.
- Average transaction amount.
- Keycloak authentication latency.

Prometheus is listed in the technology stack but no Prometheus scrape configuration or Micrometer Prometheus registry is present.

### GAP-OBS-004: Actuator Endpoints Publicly Accessible
**Severity:** High | **Effort:** Small

The security configuration explicitly permits all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
```
This exposes sensitive operational data (env, beans, health details, metrics) to unauthenticated users.

### GAP-OBS-005: No Centralized Log Aggregation
**Severity:** Medium | **Effort:** Large

No log aggregation solution (ELK, Loki, CloudWatch) is configured. In a microservices architecture, correlating logs across services without centralization is extremely difficult.

---

## 7. Resilience

### GAP-RES-001: No Circuit Breakers
**Severity:** Critical | **Effort:** Medium

No circuit breaker pattern (Resilience4j, Spring Cloud Circuit Breaker) is implemented on any Feign client. If core-banking-service becomes slow or unavailable:
- Fund-transfer and utility-payment services will hang on Feign calls.
- Thread pools will be exhausted.
- Cascading failures will propagate to all upstream services and ultimately to clients.

### GAP-RES-002: No Retry Policies
**Severity:** High | **Effort:** Small

No retry configuration exists for Feign clients. Transient network failures will immediately fail the request without any retry attempt.

### GAP-RES-003: No Timeout Configuration
**Severity:** High | **Effort:** Small

No explicit timeout configuration for:
- Feign client connection/read timeouts.
- Database connection pool timeouts.
- Gateway route timeouts.

Default timeouts (which can be very long) will be used, leading to resource exhaustion under failure conditions.

### GAP-RES-004: No Fallback Behavior
**Severity:** Medium | **Effort:** Medium

When core-banking-service is unavailable, there is no fallback behavior:
- No cached responses.
- No graceful degradation.
- No queuing of requests for later processing.

### GAP-RES-005: No Idempotency Protection
**Severity:** Critical | **Effort:** Medium

Fund transfer and utility payment operations are not idempotent:
- No idempotency key is accepted or checked.
- A client retry after timeout could result in duplicate transfers/payments.
- The `transactionId` (UUID) is generated server-side after the operation begins, not client-supplied.

### GAP-RES-006: Balance Calculation Bug
**Severity:** Critical | **Effort:** Small

In `TransactionService.internalFundTransfer()`:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` was already reduced, effectively double-subtracting the amount from `availableBalance`. The same issue affects the credit side (double-adding) and `utilPayment()`.

### GAP-RES-007: No Database Transaction Boundaries in Orchestration Services
**Severity:** High | **Effort:** Small

`FundTransferService` and `UtilityPaymentService` save entities and call external Feign services within the same method without `@Transactional`. If the Feign call succeeds but the subsequent DB save fails, the transfer record will be inconsistent.

### GAP-RES-008: TransactionEntity Missing Default Constructor
**Severity:** Medium | **Effort:** Small

`TransactionEntity` uses `@Builder` but no `@NoArgsConstructor`/`@AllArgsConstructor`. JPA requires a no-arg constructor. This may cause runtime issues with certain JPA operations.

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-001 | Organization | No multi-module Gradle build | Medium | Medium |
| GAP-ORG-002 | Organization | Duplicated code across services | High | Large |
| GAP-ORG-003 | Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-004 | Organization | Mapper instantiation pattern | Low | Small |
| GAP-ERR-001 | Error Handling | Generic handler returns 400 for all errors | High | Medium |
| GAP-ERR-002 | Error Handling | No differentiated HTTP status codes | High | Medium |
| GAP-ERR-003 | Error Handling | Inconsistent error response format | Medium | Small |
| GAP-ERR-004 | Error Handling | No Feign error decoder in most services | High | Medium |
| GAP-ERR-005 | Error Handling | No transaction failure handling | Critical | Medium |
| GAP-TEST-001 | Testing | Minimal test coverage | High | Large |
| GAP-TEST-002 | Testing | No integration tests | High | Large |
| GAP-TEST-003 | Testing | No contract tests | Medium | Large |
| GAP-TEST-004 | Testing | Test context may not load | Medium | Small |
| GAP-SEC-001 | Security | Hardcoded database credentials | Critical | Small |
| GAP-SEC-002 | Security | CSRF disabled without documentation | Medium | Small |
| GAP-SEC-003 | Security | No input validation | Critical | Medium |
| GAP-SEC-004 | Security | Stack trace exposure | High | Small |
| GAP-SEC-005 | Security | No rate limiting | Medium | Medium |
| GAP-SEC-006 | Security | Keycloak singleton not thread-safe | High | Small |
| GAP-SEC-007 | Security | No authorization beyond authentication | High | Medium |
| GAP-SEC-008 | Security | Internal services unauthenticated | High | Medium |
| GAP-API-001 | API Design | Raw ResponseEntity without type params | Medium | Small |
| GAP-API-002 | API Design | No API versioning strategy | Medium | Medium |
| GAP-API-003 | API Design | Inconsistent resource naming | Low | Small |
| GAP-API-004 | API Design | No pagination metadata | Medium | Small |
| GAP-API-005 | API Design | No filtering or search | Low | Medium |
| GAP-API-006 | API Design | OpenAPI dependency mismatch | Medium | Small |
| GAP-API-007 | API Design | No error schemas in OpenAPI | Low | Small |
| GAP-OBS-001 | Observability | Inconsistent logging | Medium | Small |
| GAP-OBS-002 | Observability | No health check customization | Low | Small |
| GAP-OBS-003 | Observability | No custom metrics | Medium | Medium |
| GAP-OBS-004 | Observability | Actuator endpoints publicly accessible | High | Small |
| GAP-OBS-005 | Observability | No centralized log aggregation | Medium | Large |
| GAP-RES-001 | Resilience | No circuit breakers | Critical | Medium |
| GAP-RES-002 | Resilience | No retry policies | High | Small |
| GAP-RES-003 | Resilience | No timeout configuration | High | Small |
| GAP-RES-004 | Resilience | No fallback behavior | Medium | Medium |
| GAP-RES-005 | Resilience | No idempotency protection | Critical | Medium |
| GAP-RES-006 | Resilience | Balance calculation bug | Critical | Small |
| GAP-RES-007 | Resilience | No DB transaction boundaries in orchestration | High | Small |
| GAP-RES-008 | Resilience | TransactionEntity missing constructors | Medium | Small |

**Totals:** 5 Critical, 16 High, 14 Medium, 5 Low
