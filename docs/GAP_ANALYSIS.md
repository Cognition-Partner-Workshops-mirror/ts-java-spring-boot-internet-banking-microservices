# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices and documents gaps with severity ratings and effort estimates.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `build.gradle` and `settings.gradle`. There is no root-level `settings.gradle` for a unified multi-project build. This leads to:
- Duplicated dependency versions across services
- No shared dependency management or convention plugins
- Inability to build/test all services in a single command

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Large**

Significant code duplication exists without a shared library:
- `BaseMapper` interface is copy-pasted in 4 services
- `AuditAware` class is duplicated in 3 services
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` duplicated in 3 services
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` duplicated with slight variations
- `CustomFeignClientConfiguration` duplicated in fund-transfer and utility-payment services

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Minor inconsistencies in package naming:
- User service: `model.repository.UserRepository` vs other services: `repository.*Repository`
- User service: `service.rest.BankingCoreRestClient` vs fund-transfer: `service.rest.client.BankingCoreFeignClient`
- Utility payment: `model.rest.request/response` vs fund-transfer: `model.dto.request/response`

### 1.4 Mapper Instantiation Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated inline (`new FundTransferMapper()`) rather than being Spring-managed beans:
```java
private FundTransferMapper mapper = new FundTransferMapper();
```
This bypasses Spring's dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Generic Catch-All Returns 400 for All Errors

**Severity: Critical | Effort: Medium**

Every `GlobalExceptionHandler` has a catch-all that returns HTTP 400 for ALL exceptions:
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest().body("Exception occur inside API " + e);
}
```
Problems:
- Server errors (500) are masked as client errors (400)
- Stack trace/exception details leaked to clients in response body
- No distinction between 404 Not Found, 409 Conflict, 422 Unprocessable, 500 Internal Server Error

### 2.2 Inconsistent Error Response Format

**Severity: High | Effort: Small**

- Known exceptions return `ErrorResponse{code, message}` (structured JSON)
- Unknown exceptions return a raw string: `"Exception occur inside API " + e`
- No consistent envelope/format for all error responses

### 2.3 Missing HTTP Status Code Granularity

**Severity: High | Effort: Medium**

All custom exceptions map to `400 Bad Request`:
- `EntityNotFoundException` should return `404 Not Found`
- `InsufficientFundsException` should return `422 Unprocessable Entity`
- `UserAlreadyRegisteredException` should return `409 Conflict`

### 2.4 No Error Correlation IDs

**Severity: Medium | Effort: Small**

Error responses do not include trace IDs or correlation IDs, making it difficult to correlate client-reported errors with server-side logs despite having Zipkin tracing enabled.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

- **core-banking-service**: 3 test files (AccountServiceTest, TransactionServiceTest, UserServiceTest) - basic unit tests only
- **internet-banking-user-service**: 1 file (ApplicationTests - context load only)
- **internet-banking-fund-transfer-service**: 1 file (ApplicationTests - context load only)
- **internet-banking-utility-payment-service**: 1 file (ApplicationTests - context load only)
- **internet-banking-api-gateway**: 1 file (ApplicationTests - context load only)
- **internet-banking-config-server**: 1 file (ApplicationTests - context load only)
- **internet-banking-service-registry**: 1 file (ApplicationTests - context load only)

Only core-banking-service has meaningful unit tests (10 test methods).

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

- No `@SpringBootTest` integration tests with real database
- No Testcontainers usage for MySQL/Keycloak
- No tests for Feign client communication
- No tests for API Gateway routing/security

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

- No Spring Cloud Contract or Pact tests
- Feign client interfaces can drift from actual service APIs without detection
- Breaking changes between services would only be caught in production

### 3.4 No Controller/API Tests

**Severity: High | Effort: Medium**

- No `@WebMvcTest` or `MockMvc` tests for REST controllers
- No validation of request/response serialization
- No testing of error handling paths at the HTTP layer

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials committed to version control:
- `docker-compose.yml`: MySQL root password `woVERANKliGharym`
- `docker-compose.yml`: Keycloak admin password `password`
- `privileges.sql`: App user password `oPItyPticIAt`
- `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`
- MySQL `Dockerfile`: `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym`

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

- No `@Valid` / `@Validated` annotations on any `@RequestBody` parameters
- No Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on DTOs
- Fund transfer amount could be negative or zero
- Account numbers are not validated for format
- Email format not validated before Keycloak registration

### 4.3 CSRF Disabled Without Documentation

**Severity: Medium | Effort: Small**

API Gateway disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While acceptable for stateless JWT APIs, there's no documentation justifying this decision.

### 4.4 Static Keycloak Singleton (Thread Safety)

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-thread-safe lazy singleton:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) { // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

### 4.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting on any endpoints, including:
- User registration (brute force risk)
- Fund transfers (potential abuse)
- Authentication endpoint passthrough

### 4.6 Downstream Services Lack Authentication

**Severity: High | Effort: Medium**

Only the API Gateway enforces JWT authentication. Downstream services (user, fund-transfer, utility-payment, core-banking) are accessible without authentication if called directly (bypassing gateway). No service-to-service authentication is implemented.

---

## 5. API Design

### 5.1 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While endpoints use `/api/v1/`, there is no documented versioning strategy, no header-based versioning support, and no mechanism to run multiple API versions simultaneously.

### 5.2 Inconsistent Pagination

**Severity: Medium | Effort: Small**

- Core Banking UserController accepts `Pageable` and returns `Page<User>` (with metadata)
- Fund Transfer Controller returns `List<FundTransfer>` (loses pagination metadata)
- Utility Payment Controller returns `List<UtilityPayment>` (loses pagination metadata)
- No consistent pagination envelope (total, page, size, etc.)

### 5.3 Raw ResponseEntity Without Type Parameters

**Severity: Low | Effort: Small**

Most controllers use raw `ResponseEntity` without type parameters:
```java
public ResponseEntity getBankAccount(...) // should be ResponseEntity<BankAccount>
```
This reduces API documentation quality and type safety.

### 5.4 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

Paginated endpoints accept Spring's `Pageable` but there's no documentation of supported sort fields or filter parameters.

### 5.5 OpenAPI Documentation Incomplete

**Severity: Medium | Effort: Medium**

- `springdoc-openapi-starter-webflux-ui` is included but services use Spring MVC (not WebFlux)
- Wrong starter dependency (`webflux-ui` instead of `webmvc-ui`)
- No aggregated OpenAPI spec at the gateway level
- No request/response schema examples

### 5.6 Non-RESTful Endpoint Naming

**Severity: Low | Effort: Small**

Some endpoints use verb-based naming:
- `POST /api/v1/bank-users/register` should be `POST /api/v1/bank-users`
- `PATCH /api/v1/bank-users/update/{id}` should be `PATCH /api/v1/bank-users/{id}`

---

## 6. Observability

### 6.1 No Structured Logging

**Severity: High | Effort: Medium**

- Uses default Spring Boot logging (text-based)
- No JSON log format for log aggregation tools
- No MDC context (correlation IDs, user IDs) in log entries
- Inconsistent log messages across services

### 6.2 No Custom Health Indicators

**Severity: Medium | Effort: Small**

- Actuator health endpoint exists but uses only default indicators
- No custom health checks for MySQL connectivity
- No health check for Keycloak availability
- No health check for Config Server availability

### 6.3 No Custom Metrics

**Severity: Medium | Effort: Medium**

- Actuator provides default JVM metrics
- No business metrics (transactions/sec, transfer amounts, payment success rate)
- No Prometheus metrics endpoint configured
- No Micrometer custom counters/timers

### 6.4 Sensitive Data in Logs

**Severity: High | Effort: Small**

Controllers log full request objects including potentially sensitive data:
```java
log.info("Creating user with {}", request.toString()); // may include password
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

### 6.5 Distributed Tracing Not Verified

**Severity: Medium | Effort: Small**

- Zipkin dependencies are included in all services
- No explicit `management.tracing.sampling.probability` configuration visible
- Feign micrometer integration included but behavior not validated
- No span customization for business operations

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

- No Resilience4j or Hystrix dependencies
- If Core Banking Service goes down, all dependent services will cascade fail
- Feign calls will block until timeout (with default settings)
- No fallback behavior defined anywhere

### 7.2 No Retry Policies

**Severity: High | Effort: Medium**

- No Spring Retry or Resilience4j retry configuration
- Transient failures (network blips, DB connection resets) will immediately fail
- No retry on Feign client calls
- No retry on database operations

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

- No explicit timeout settings for Feign clients
- No connection/read timeouts configured
- Default Feign timeout is 60 seconds (too long for user-facing APIs)
- `wait-for-it.sh` has 50s timeout but no application-level timeouts

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

- No graceful degradation when dependencies are unavailable
- No cached responses for read operations
- No queue-based fallback for write operations
- Fund transfers will fail completely if Core Banking is momentarily unavailable

### 7.5 No Bulkhead Pattern

**Severity: Medium | Effort: Medium**

- No thread pool isolation between different types of requests
- A slow endpoint could exhaust the thread pool and block all other requests
- No connection pool limits configured for Feign clients

### 7.6 No Idempotency Controls

**Severity: High | Effort: Medium**

- Fund transfer endpoint has no idempotency key
- Retried requests could result in duplicate transfers
- No deduplication mechanism for utility payments
- No optimistic locking on balance updates (race conditions possible)

---

## Summary Table

| Category | Gap | Severity | Effort |
|----------|-----|----------|--------|
| Code Organization | No multi-project Gradle build | Medium | Medium |
| Code Organization | Duplicated code across services | High | Large |
| Code Organization | Inconsistent package structure | Low | Small |
| Code Organization | Mapper instantiation pattern | Low | Small |
| Error Handling | Generic catch-all returns 400 | Critical | Medium |
| Error Handling | Inconsistent error response format | High | Small |
| Error Handling | Missing HTTP status code granularity | High | Medium |
| Error Handling | No error correlation IDs | Medium | Small |
| Testing | Minimal test coverage | Critical | Large |
| Testing | No integration tests | High | Large |
| Testing | No contract tests | Medium | Large |
| Testing | No controller/API tests | High | Medium |
| Security | Hardcoded credentials | Critical | Small |
| Security | No input validation | Critical | Medium |
| Security | CSRF disabled without documentation | Medium | Small |
| Security | Static Keycloak singleton (thread safety) | Medium | Small |
| Security | No rate limiting | Medium | Medium |
| Security | Downstream services lack authentication | High | Medium |
| API Design | No API versioning strategy | Medium | Medium |
| API Design | Inconsistent pagination | Medium | Small |
| API Design | Raw ResponseEntity without types | Low | Small |
| API Design | No filtering/sorting documentation | Low | Small |
| API Design | OpenAPI documentation incomplete | Medium | Medium |
| API Design | Non-RESTful endpoint naming | Low | Small |
| Observability | No structured logging | High | Medium |
| Observability | No custom health indicators | Medium | Small |
| Observability | No custom metrics | Medium | Medium |
| Observability | Sensitive data in logs | High | Small |
| Observability | Distributed tracing not verified | Medium | Small |
| Resilience | No circuit breakers | Critical | Medium |
| Resilience | No retry policies | High | Medium |
| Resilience | No timeout configuration | High | Small |
| Resilience | No fallback behavior | Medium | Medium |
| Resilience | No bulkhead pattern | Medium | Medium |
| Resilience | No idempotency controls | High | Medium |

### Critical Issues (Immediate Attention Required): 5
### High Severity Issues: 13
### Medium Severity Issues: 14
### Low Severity Issues: 5
