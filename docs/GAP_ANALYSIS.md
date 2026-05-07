# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Shared Library / Multi-Module Build

**Severity: Medium | Effort: Medium**

Each service is an independent Gradle project with no shared parent or common module. This results in significant code duplication:
- `BaseMapper<E, D>` is copy-pasted across 4 services
- `AuditAware` entity superclass is duplicated in 3 services
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` are duplicated across 3 services
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` are duplicated across 4 services
- `CustomFeignClientConfiguration` is duplicated across 3 services

### 1.2 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package naming is mostly consistent (`com.javatodev.finance`) but sub-package organization varies:
- Core Banking: `repository/` at top level
- User Service: `model/repository/`
- Fund Transfer: `model/repository/`
- Utility Payment: `repository/` at top level

Similarly, REST client location varies:
- User Service: `service/rest/BankingCoreRestClient.java`
- Fund Transfer: `service/rest/client/BankingCoreFeignClient.java`
- Utility Payment: `service/rest/BankingCoreRestClient.java`

### 1.3 Mapper Instantiation Not Managed by Spring

**Severity: Low | Effort: Small**

All mapper classes are instantiated with `new` inside service classes rather than being Spring-managed beans:
```java
private UserMapper userMapper = new UserMapper();
```
This prevents dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns 400 for All Errors

**Severity: Critical | Effort: Small**

Every service's `GlobalExceptionHandler` catches `Exception.class` and returns `400 Bad Request` with a raw exception string:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity
        .badRequest()
        .body("Exception occur inside API " + e);
}
```

**Issues:**
- Server errors (NPE, DB connection failures) return 400 instead of 500
- Stack traces and internal details leak to clients
- No structured error response for the catch-all handler

### 2.2 Inconsistent Error Response Format

**Severity: High | Effort: Small**

- Custom exceptions return structured `ErrorResponse { code, message }`
- The catch-all handler returns a plain string: `"Exception occur inside API " + e`
- Clients cannot reliably parse error responses

### 2.3 Missing HTTP Status Code Differentiation

**Severity: High | Effort: Small**

All custom exceptions map to `400 Bad Request`. There is no distinction between:
- 404 Not Found (EntityNotFoundException)
- 409 Conflict (UserAlreadyRegisteredException)
- 422 Unprocessable Entity (InsufficientFundsException, InvalidEmailException)
- 500 Internal Server Error (unexpected failures)

### 2.4 No Feign Error Handling in Fund Transfer / Utility Payment

**Severity: High | Effort: Medium**

- User Service has `CustomFeignErrorDecoder` for Feign error handling
- Fund Transfer Service and Utility Payment Service have `CustomFeignClientConfiguration` that only propagates auth headers but no error decoder
- If core-banking-service returns an error, Feign throws a generic `FeignException` that gets caught by the catch-all handler

### 2.5 No Transactional Rollback on Feign Failure

**Severity: Critical | Effort: Medium**

In `FundTransferService.fundTransfer()`:
1. Local record saved with `PENDING` status
2. Feign call to core-banking-service
3. If Feign call fails, the local record remains `PENDING` forever with no retry or compensation

Same pattern in `UtilityPaymentService.utilPayment()` where records stay `PROCESSING` on failure.

---

## 3. Testing

### 3.1 Only One Service Has Unit Tests

**Severity: Critical | Effort: Large**

- `core-banking-service`: 3 test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) with ~20 test cases
- `internet-banking-user-service`: Only empty context load test
- `internet-banking-fund-transfer-service`: Only empty context load test
- `internet-banking-utility-payment-service`: Only empty context load test
- `internet-banking-api-gateway`: Only empty context load test
- `internet-banking-service-registry`: Only empty context load test

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist that verify:
- Controller layer behavior (MockMvc / WebMvcTest)
- Repository layer with actual database (DataJpaTest)
- Full service flow with embedded containers (SpringBootTest)

### 3.3 No Contract Tests Between Services

**Severity: Medium | Effort: Large**

No contract tests (e.g., Spring Cloud Contract, Pact) exist to validate the API contracts between:
- Fund Transfer Service <-> Core Banking Service
- Utility Payment Service <-> Core Banking Service
- User Service <-> Core Banking Service

### 3.4 Context Load Tests Will Fail Without Infrastructure

**Severity: Medium | Effort: Small**

The `*ApplicationTests.java` files use `@SpringBootTest` which requires Config Server, Eureka, and MySQL to be running. These tests will fail in CI without test profiles or embedded alternatives.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are committed in plain text:
- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`)
- `privileges.sql`: MySQL user password (`oPItyPticIAt`)
- `README.md`: Test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`)

### 4.2 CSRF Disabled Without Documentation

**Severity: Medium | Effort: Small**

CSRF protection is disabled in the API Gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While common for stateless APIs, this should be documented and justified.

### 4.3 No Input Validation on Request Bodies

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or other Bean Validation annotations exist on any request DTO. Examples:
- `FundTransferRequest`: `amount` can be null, zero, or negative
- `UtilityPaymentRequest`: No validation on any field
- `User` registration: No email format validation, no password strength requirements

### 4.4 Non-Thread-Safe Keycloak Singleton

**Severity: High | Effort: Small**

`KeycloakProperties.getInstance()` uses a classic double-check-less singleton pattern with a static field. This is not thread-safe and could create multiple Keycloak client instances under concurrent access or cause visibility issues.

### 4.5 No Authorization Beyond Authentication

**Severity: High | Effort: Medium**

- The API Gateway only checks if a JWT token is present and valid
- No role-based access control (RBAC) is enforced
- Any authenticated user can call any endpoint including admin operations like user approval
- The `X-Auth-Id` header is injected but downstream services don't validate it against the requested resources

### 4.6 Sensitive Data in Log Statements

**Severity: Medium | Effort: Small**

Multiple controllers log full request objects via `toString()`:
```java
log.info("Creating user with {}", request.toString());
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```
This may expose passwords, account numbers, and amounts in log files.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning is configured in the Gradle build.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

**Severity: Medium | Effort: Small**

Most controller methods return untyped `ResponseEntity` instead of `ResponseEntity<T>`:
```java
public ResponseEntity getBankAccount(...) // raw type
```
Only User Service partially uses typed responses: `ResponseEntity<User>`.

This weakens compile-time type safety and degrades OpenAPI documentation.

### 5.2 No API Versioning Strategy

**Severity: Low | Effort: Medium**

While URLs contain `/v1/`, there is no documented versioning strategy, no content negotiation, and no mechanism to support multiple API versions.

### 5.3 Pagination Response Missing Metadata

**Severity: Medium | Effort: Small**

Paginated endpoints return `List<T>` instead of a wrapper with pagination metadata:
```java
return ResponseEntity.ok(userService.readUsers(pageable));
// Returns List<User> — no total count, page info, or links
```

### 5.4 Inconsistent REST Conventions

**Severity: Medium | Effort: Small**

- `POST /register` for user creation instead of `POST /bank-users`
- `PATCH /update/{id}` with redundant verb in URL
- `GET /util-account/{account_name}` mixes `_` separator with path convention
- Fund transfer: `POST /api/v1/transfer` at the service level; `POST /api/v1/transaction/fund-transfer` at core banking level

### 5.5 OpenAPI/Swagger Partially Configured

**Severity: Medium | Effort: Small**

- `springdoc-openapi-starter-webflux-ui` is included as a dependency
- Basic `@Tag` and `@Operation` annotations are present on controllers
- However, request/response schemas lack `@Schema` annotations for field descriptions
- No global API info, security scheme definition, or example values

### 5.6 No HATEOAS or Hypermedia Links

**Severity: Low | Effort: Medium**

Responses contain no links for discoverability or navigation between related resources.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log at `INFO` level; others don't log at all
- Core Banking `AccountController` logs request parameters; `UserController` doesn't
- No structured logging format (JSON) for log aggregation
- No correlation ID propagation in log messages beyond Zipkin trace IDs
- Service layer logging is sparse — only fund transfer logs a message

### 6.2 Health Check Endpoints Not Customized

**Severity: Low | Effort: Small**

- `spring-boot-starter-actuator` is included in all services
- Default `/actuator/health` endpoints are available
- No custom health indicators for database connectivity, Keycloak availability, or downstream service health
- No readiness/liveness probe differentiation

### 6.3 No Metrics Endpoints or Dashboards

**Severity: Medium | Effort: Medium**

- Actuator is present but Prometheus metrics export is not configured (despite Prometheus being listed in the tech stack)
- No custom business metrics (transfer count, payment volume, error rates)
- No Grafana dashboards or alerting configuration

### 6.4 Distributed Tracing Coverage is Passive

**Severity: Low | Effort: Small**

- `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` are included
- However, no custom spans are created for critical business operations
- No span tags for business context (account numbers, transaction IDs)

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker (Resilience4j, Hystrix) is configured on any Feign client. If `core-banking-service` goes down:
- Fund Transfer Service will block and eventually timeout
- Utility Payment Service will block and eventually timeout
- No fallback behavior or graceful degradation

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for:
- Feign client calls (transient network failures)
- Database operations
- Keycloak API calls

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

- No Feign client timeouts configured (connect timeout, read timeout)
- No Spring WebClient timeouts
- Default infinite timeouts could block threads indefinitely
- `wait-for-it.sh` has a 50-second timeout but application-level timeouts are absent

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

When downstream services are unavailable:
- No cached responses
- No graceful degradation
- No queue-based retry mechanism
- Failed fund transfers and payments are left in `PENDING`/`PROCESSING` state with no recovery path

### 7.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configured. All endpoints are equally vulnerable to traffic spikes or abuse.

### 7.6 Non-Atomic Balance Updates

**Severity: Critical | Effort: Medium**

In `TransactionService.internalFundTransfer()`:
- Two separate `bankAccountRepository.save()` calls for debit and credit
- No database-level locking (optimistic or pessimistic)
- Concurrent transfers from the same account could result in race conditions and incorrect balances
- If the process fails between debit and credit, the system is left in an inconsistent state

---

## Summary Table

| Category | Gap | Severity | Effort |
|---|---|---|---|
| Code Organization | No shared library / multi-module build | Medium | Medium |
| Code Organization | Inconsistent package structure | Low | Small |
| Code Organization | Mappers not Spring-managed | Low | Small |
| Error Handling | Generic catch-all returns 400 | Critical | Small |
| Error Handling | Inconsistent error response format | High | Small |
| Error Handling | Missing HTTP status code differentiation | High | Small |
| Error Handling | No Feign error handling in 2 services | High | Medium |
| Error Handling | No transactional rollback on Feign failure | Critical | Medium |
| Testing | Only one service has unit tests | Critical | Large |
| Testing | No integration tests | High | Large |
| Testing | No contract tests | Medium | Large |
| Testing | Context load tests fail without infra | Medium | Small |
| Security | Hardcoded credentials in source | Critical | Small |
| Security | CSRF disabled without documentation | Medium | Small |
| Security | No input validation | Critical | Medium |
| Security | Non-thread-safe Keycloak singleton | High | Small |
| Security | No RBAC beyond authentication | High | Medium |
| Security | Sensitive data in logs | Medium | Small |
| Security | No dependency vulnerability scanning | Medium | Small |
| API Design | Raw ResponseEntity without type params | Medium | Small |
| API Design | No API versioning strategy | Low | Medium |
| API Design | Pagination missing metadata | Medium | Small |
| API Design | Inconsistent REST conventions | Medium | Small |
| API Design | OpenAPI/Swagger partially configured | Medium | Small |
| API Design | No HATEOAS | Low | Medium |
| Observability | Inconsistent logging | Medium | Small |
| Observability | Health checks not customized | Low | Small |
| Observability | No metrics / Prometheus export | Medium | Medium |
| Observability | Tracing coverage is passive | Low | Small |
| Resilience | No circuit breakers | Critical | Medium |
| Resilience | No retry policies | High | Small |
| Resilience | No timeout configuration | High | Small |
| Resilience | No fallback behavior | Medium | Medium |
| Resilience | No rate limiting | Medium | Medium |
| Resilience | Non-atomic balance updates | Critical | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 7 |
| High | 8 |
| Medium | 15 |
| Low | 5 |
| **Total** | **35** |
