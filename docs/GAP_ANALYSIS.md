# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against industry engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

## Severity Ratings

| Rating | Definition |
|--------|-----------|
| **Critical** | Blocks production readiness; security or data-integrity risk |
| **High** | Significant quality or maintainability concern; should fix before scaling |
| **Medium** | Best-practice deviation; improves long-term health |
| **Low** | Polish item; nice to have |

## Effort Estimates

| Effort | Definition |
|--------|-----------|
| **Small** | < 1 day per service; config change or straightforward code addition |
| **Medium** | 1-3 days; requires code changes across multiple files or services |
| **Large** | 3+ days; architectural change, new infrastructure, or cross-cutting concern |

---

## 1. Code Organization

### 1.1 No Shared Library / Multi-Module Gradle Build

**Severity: High | Effort: Large**

Each service is an independent Gradle project with its own `build.gradle`, `gradlew`, and `settings.gradle`. There is no parent/root `build.gradle` or multi-module project structure. This leads to:

- **Duplicated code across services:** `AuditAware`, `BaseMapper`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder` are copy-pasted into each service with minor variations.
- **Inconsistent dependency versions:** While all services currently use the same Spring Boot/Cloud versions, there is no central version catalog or BOM to enforce this.
- **No shared DTOs:** Request/response classes like `FundTransferRequest` exist in both `core-banking-service` and `internet-banking-fund-transfer-service` with slightly different fields (e.g., the fund-transfer-service version adds `authID`).

### 1.2 Inconsistent Package Structure

**Severity: Medium | Effort: Small**

Package naming and class placement varies across services:

- Core Banking: `model.dto.request`, `model.dto.response`, `model.mapper`, `repository`
- User Service: `model.dto`, `model.rest.response`, `model.mapper`, `model.repository`
- Fund Transfer: `model.dto.request`, `model.dto.response`, `model.mapper`, `model.repository`, `service.rest.client`
- Utility Payment: `model.rest.request`, `model.rest.response`, `model.mapper`, `repository`, `service.rest`

The package conventions for DTOs, repositories, and REST clients differ between services.

### 1.3 Mapper Instantiation Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated via `new` in service fields (e.g., `private UserMapper userMapper = new UserMapper()`) instead of being managed as Spring beans. This makes them harder to mock in tests and breaks the DI pattern used everywhere else.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Construction

**Severity: Medium | Effort: Small**

The `GlobalExceptionHandler` classes are nearly identical across services but differ in how they construct error responses:

- Core Banking and User Service: Use `ErrorResponse.builder().code(...).message(...).build()`
- Fund Transfer Service: Uses constructor `new ErrorResponse(e.getCode(), e.getMessage())`

The `ErrorResponse` class itself is duplicated in each service.

### 2.2 All Errors Return HTTP 400

**Severity: High | Effort: Medium**

Every exception — including "entity not found" (`EntityNotFoundException`) — returns HTTP 400 Bad Request. Proper REST semantics require:

- `404 Not Found` for missing entities
- `409 Conflict` for duplicate registrations
- `422 Unprocessable Entity` for validation failures
- `500 Internal Server Error` for unexpected exceptions
- `502 Bad Gateway` for downstream service failures

The generic catch-all `Exception` handler also returns 400, which means server errors (NPE, connection failures, etc.) are misreported as client errors.

### 2.3 Error Response Leaks Internal Details

**Severity: High | Effort: Small**

The generic exception handler returns `"Exception occur inside API " + e`, which exposes stack traces and internal class names to API consumers. This is both a security risk and poor API design.

### 2.4 No Feign Error Handling in Fund Transfer Service

**Severity: Medium | Effort: Small**

The User Service has a `CustomFeignErrorDecoder` for handling errors from Feign calls, but the Fund Transfer Service's `CustomFeignClientConfiguration` only sets the log level — it has no error decoder. Errors from the core banking service during fund transfers will result in unhandled Feign exceptions.

---

## 3. Testing

### 3.1 Tests Only Exist for Core Banking Service

**Severity: Critical | Effort: Large**

Only the `core-banking-service` has meaningful unit tests (3 test classes: `TransactionServiceTest`, `AccountServiceTest`, `UserServiceTest` with ~20 test cases total). All other services only have the default Spring Boot context-load test (`*ApplicationTests.java`), which is typically disabled or fails without infrastructure.

Missing test coverage:

| Service | Service-Layer Tests | Controller Tests | Integration Tests |
|---------|-------------------|-----------------|------------------|
| Core Banking | 3 classes, ~20 tests | None | None |
| User Service | None | None | None |
| Fund Transfer Service | None | None | None |
| Utility Payment Service | None | None | None |
| API Gateway | None | None | None |
| Config Server | None | None | None |

### 3.2 No Controller / Web Layer Tests

**Severity: High | Effort: Medium**

No `@WebMvcTest` or `MockMvc`-based tests exist for any controller. This means request mapping, serialization/deserialization, validation, and HTTP status code behavior is never verified.

### 3.3 No Integration or Contract Tests

**Severity: High | Effort: Large**

There are no:

- Integration tests that test services with real databases (even H2)
- Contract tests (e.g., Spring Cloud Contract, Pact) between services
- End-to-end tests that verify the full request flow through the API gateway

Given the heavy reliance on OpenFeign for inter-service communication, the absence of contract tests means breaking API changes between services won't be caught until runtime.

### 3.4 No Test Coverage Reporting

**Severity: Medium | Effort: Small**

No JaCoCo or similar code coverage plugin is configured in any `build.gradle`. There is no visibility into what percentage of code is tested.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, `@Pattern`, or any Jakarta Bean Validation annotations exist on any request DTO or controller parameter. Examples:

- `FundTransferRequest.amount` accepts null or negative values
- `UtilityPaymentRequest.account` accepts null or empty strings
- `User.email` has no email format validation
- No `spring-boot-starter-validation` dependency in any `build.gradle`

This means any malformed request will reach business logic and potentially cause NPEs or corrupt data.

### 4.2 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Database passwords, Keycloak admin credentials, and MySQL user credentials are hardcoded in:

- `docker-compose/docker-compose.yml`: MySQL root password, Keycloak admin/password, PostgreSQL password
- `docker-compose/mysql/privileges.sql`: MySQL application user password
- Test credentials in `README.md`

These should be externalized to environment variables or a secrets manager, even for development.

### 4.3 CSRF Disabled Without Justification

**Severity: Medium | Effort: Small**

The API Gateway's `SecurityConfiguration` disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is common for stateless JWT-based APIs, there is no documentation explaining this decision or confirming the API is purely stateless.

### 4.4 Keycloak Singleton Is Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern (`if (keycloakInstance == null)`). In a multi-threaded Spring Boot application, this can lead to multiple Keycloak client instances being created concurrently, potentially causing resource leaks or authentication failures.

### 4.5 No Dependency Vulnerability Scanning

**Severity: High | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar vulnerability scanning plugin is configured. There is no mechanism to detect known CVEs in transitive dependencies.

### 4.6 No Rate Limiting

**Severity: Medium | Effort: Medium**

The API Gateway has no rate limiting configured. Financial APIs without rate limiting are vulnerable to abuse and brute-force attacks.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generic Types

**Severity: Medium | Effort: Small**

Most controller methods return untyped `ResponseEntity` instead of `ResponseEntity<SpecificType>`. This:

- Makes the API contract unclear
- Prevents OpenAPI/Swagger from generating accurate response schemas
- Loses compile-time type safety

Only the User Service controllers use typed responses (e.g., `ResponseEntity<User>`).

### 5.2 No Request Validation Error Responses

**Severity: High | Effort: Medium**

Since no validation annotations exist (see 4.1), there are no validation error responses. Once validation is added, the `GlobalExceptionHandler` will need to handle `MethodArgumentNotValidException` with proper 422 responses.

### 5.3 Pagination Lacks Envelope / HATEOAS

**Severity: Low | Effort: Medium**

Paginated endpoints return raw `List<T>` instead of a pagination envelope containing `totalElements`, `totalPages`, `currentPage`, etc. Consumers have no way to know the total dataset size or navigate pages.

### 5.4 No API Versioning Strategy

**Severity: Low | Effort: Small**

While all endpoints use `/api/v1/`, there is no documented versioning strategy (URL-based, header-based) or plan for handling breaking changes.

### 5.5 Swagger/OpenAPI Configuration Incomplete

**Severity: Medium | Effort: Small**

The `springdoc-openapi-starter-webflux-ui` dependency is included in service build files, and `@Tag` and `@Operation` annotations have been added to controllers. However:

- The webflux UI dependency is used in non-reactive (servlet-based) services, which may cause classpath conflicts
- There is no global OpenAPI configuration (`@OpenAPIDefinition`) with API title, version, description, or security scheme
- No OpenAPI specification is generated or published as part of the build

### 5.6 Inconsistent URL Patterns

**Severity: Low | Effort: Small**

- Core Banking uses `/api/v1/account/bank-account/{account_number}` and `/api/v1/account/util-account/{account_name}` (mixed naming)
- Fund Transfer uses `/api/v1/transfer`
- Utility Payment uses `/api/v1/utility-payment`
- Path variable naming inconsistency: `{account_number}` vs `{account_name}` vs `{id}` vs `{identification}`

---

## 6. Observability

### 6.1 Logging Is Inconsistent

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log incoming requests (e.g., `FundTransferController`, `UserController`), others don't (e.g., `UtilityPaymentController`)
- Log messages use `toString()` on request objects, which may expose sensitive data (passwords, account numbers)
- No structured logging (JSON format) is configured
- No correlation ID / trace ID is included in log output by default

### 6.2 No Custom Health Checks

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but only expose default health checks. No custom health indicators exist for:

- Database connectivity verification
- Keycloak availability
- Downstream service health (via Feign)

### 6.3 No Metrics Endpoints or Dashboards

**Severity: Medium | Effort: Medium**

While `spring-boot-starter-actuator` is included, there is no:

- Prometheus metrics exporter configured (despite Prometheus being listed in the README's tech stack)
- Grafana dashboards
- Custom business metrics (e.g., transfer count, payment volume, error rates)

### 6.4 Zipkin Tracing Configuration Is External

**Severity: Low | Effort: Small**

Tracing dependencies are included but the Zipkin endpoint configuration is served via the external Config Server Git repo. If the config server is unavailable, tracing silently fails. Consider a fallback configuration in `application.yml`.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

There are no circuit breakers (e.g., Resilience4j, Hystrix) on any OpenFeign client call. If `core-banking-service` becomes slow or unavailable:

- Fund Transfer Service will hang on Feign calls, consuming threads
- Utility Payment Service will also hang
- User Service registration will fail without graceful degradation
- Thread pool exhaustion will cascade to other requests

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists on Feign clients or Spring Cloud. Transient failures (network blips, temporary 503s) will immediately fail the entire operation without retry.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeouts are configured on:

- Feign client connections and read operations
- Database connection pool
- API Gateway route timeouts

Default timeouts are typically very long or infinite, leading to thread starvation under failure conditions.

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No Feign fallbacks (`@FeignClient(fallback = ...)`) or fallback factories are defined. When a downstream service fails, the error propagates directly to the client with no graceful degradation.

### 7.5 No Bulkhead Isolation

**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow response from the core banking service will consume threads that could serve other requests, including unrelated API calls.

### 7.6 Non-Atomic Fund Transfer

**Severity: Critical | Effort: Large**

The fund transfer flow in `FundTransferService` is not atomic across services:

1. Saves a `PENDING` record locally
2. Calls core banking service to execute the transfer
3. Updates local record to `SUCCESS`

If the application crashes between steps 2 and 3, or if the response is lost, the transfer executes in core banking but the local record remains `PENDING`. There is no compensation mechanism, saga pattern, or idempotency key to handle this.

The same issue exists in `UtilityPaymentService`.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No shared library / multi-module build | Code Organization | High | Large |
| 1.2 | Inconsistent package structure | Code Organization | Medium | Small |
| 1.3 | Mapper instantiation via `new` | Code Organization | Low | Small |
| 2.1 | Inconsistent error response construction | Error Handling | Medium | Small |
| 2.2 | All errors return HTTP 400 | Error Handling | High | Medium |
| 2.3 | Error response leaks internal details | Error Handling | High | Small |
| 2.4 | No Feign error handling in Fund Transfer | Error Handling | Medium | Small |
| 3.1 | Tests only in Core Banking Service | Testing | Critical | Large |
| 3.2 | No controller / web layer tests | Testing | High | Medium |
| 3.3 | No integration or contract tests | Testing | High | Large |
| 3.4 | No test coverage reporting | Testing | Medium | Small |
| 4.1 | No input validation | Security | Critical | Medium |
| 4.2 | Hardcoded credentials | Security | Critical | Small |
| 4.3 | CSRF disabled without documentation | Security | Medium | Small |
| 4.4 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.5 | No dependency vulnerability scanning | Security | High | Small |
| 4.6 | No rate limiting | Security | Medium | Medium |
| 5.1 | Raw `ResponseEntity` without generics | API Design | Medium | Small |
| 5.2 | No validation error responses | API Design | High | Medium |
| 5.3 | Pagination lacks envelope | API Design | Low | Medium |
| 5.4 | No API versioning strategy | API Design | Low | Small |
| 5.5 | Swagger/OpenAPI incomplete | API Design | Medium | Small |
| 5.6 | Inconsistent URL patterns | API Design | Low | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | No custom health checks | Observability | Low | Small |
| 6.3 | No metrics / Prometheus integration | Observability | Medium | Medium |
| 6.4 | Zipkin config has no fallback | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No bulkhead isolation | Resilience | Medium | Medium |
| 7.6 | Non-atomic distributed transactions | Resilience | Critical | Large |

### Gap Count by Severity

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 9 |
| Medium | 12 |
| Low | 5 |
| **Total** | **32** |
