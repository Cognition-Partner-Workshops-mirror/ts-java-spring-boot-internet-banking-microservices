# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by severity and estimated remediation effort.

**Severity Scale**: Critical > High > Medium > Low  
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: Each service has an independent `build.gradle` with duplicated dependency declarations, plugin versions, and Spring Cloud BOM imports. There is no root `settings.gradle` or shared build configuration.

**Impact**: Version drift between services, duplicated maintenance effort, inconsistent dependency updates.

**Best Practice**: Use a Gradle multi-project build with a root `build.gradle` defining shared plugin versions, dependency management, and common configurations.

---

### 1.2 Duplicated Code Across Services

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: Identical classes are copy-pasted across services:
- `BaseMapper` (3 copies)
- `AuditAware` (3 copies)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (3 copies)
- `ErrorResponse` (3 copies)
- `SimpleBankingGlobalException` (3 copies)
- `GlobalExceptionHandler` (3 copies with slight variations)

**Impact**: Bug fixes must be applied in multiple places. Behavior divergence over time.

**Best Practice**: Extract shared code into a common library module (e.g., `banking-common`) published as an internal dependency.

---

### 1.3 Inconsistent Package Structure

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Package naming varies across services:
- fund-transfer: `model.dto.request`, `model.dto.response`, `model.entity`, `model.repository`
- utility-payment: `model.rest.request`, `model.rest.response`, `model.entity`, `repository`
- user-service: `model.dto`, `model.entity`, `model.repository`, `model.rest.response`

**Impact**: Developer confusion when navigating between services.

**Best Practice**: Standardize on a single package layout convention across all services.

---

### 1.4 Mapper Instantiation Anti-Pattern

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Mappers are instantiated directly in service classes (`private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans or using a mapping framework.

**Impact**: Cannot leverage dependency injection, harder to test, no support for complex mappings.

**Best Practice**: Use MapStruct or register mappers as `@Component` beans.

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns String Body

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding**: All three `GlobalExceptionHandler` implementations have a catch-all that returns a raw string: `"Exception occur inside API " + e`. This exposes internal stack traces and exception details to clients.

**Impact**: Security risk (information leakage), inconsistent error response format, poor client experience.

**Best Practice**: Return a structured `ErrorResponse` object for ALL exceptions. Never expose raw exception details.

---

### 2.2 All Errors Return HTTP 400 Bad Request

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding**: Both the `SimpleBankingGlobalException` handler and the generic `Exception` handler return `ResponseEntity.badRequest()` (HTTP 400) for all errors — including not-found (should be 404), insufficient funds (could be 422), and server errors (should be 500).

**Impact**: Clients cannot distinguish between client errors and server errors. Monitoring/alerting based on HTTP status codes is ineffective.

**Best Practice**: Map exception types to appropriate HTTP status codes (404 for not found, 422 for business rule violations, 500 for unexpected errors).

---

### 2.3 No Input Validation Error Handling

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: No `@Valid` annotations on request bodies. No `MethodArgumentNotValidException` handler. No Bean Validation constraints on DTOs.

**Impact**: Invalid data flows through to the database layer where it may cause cryptic errors.

**Best Practice**: Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, etc.) to request DTOs and handle validation errors with proper 422 responses.

---

### 2.4 Raw ResponseEntity Without Generics

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Most controller methods return `ResponseEntity` without type parameters (raw type), making the API contract unclear from the code.

**Impact**: No compile-time type safety, unclear API documentation generation.

**Best Practice**: Always use typed `ResponseEntity<T>` returns.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Large |

**Finding**: Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services have only empty application context tests (`*ApplicationTests.java`) that simply verify the Spring context loads.

**Coverage Summary**:
| Service | Test Classes | Meaningful Tests |
|---------|-------------|-----------------|
| core-banking-service | 4 | 3 (service layer) |
| internet-banking-user-service | 1 | 0 (context only) |
| internet-banking-fund-transfer-service | 1 | 0 (context only) |
| internet-banking-utility-payment-service | 1 | 0 (context only) |
| internet-banking-api-gateway | 1 | 0 (context only) |
| internet-banking-service-registry | 1 | 0 (context only) |

**Impact**: No safety net for refactoring, bugs ship undetected, developer confidence is low.

**Best Practice**: Aim for 80%+ unit test coverage on service layers, controller layer tests, and integration tests for critical flows.

---

### 3.2 No Integration Tests

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding**: No integration tests exist. No `@SpringBootTest` tests with real database interactions (Testcontainers), no Feign client contract tests, no end-to-end API tests.

**Impact**: Inter-service contract changes go undetected until runtime. Database query issues are only found in production.

**Best Practice**: Add integration tests using Testcontainers for database verification and WireMock/Spring Cloud Contract for Feign client contracts.

---

### 3.3 No Contract Tests Between Services

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding**: Services communicate via Feign clients with hardcoded API paths. There are no consumer-driven contract tests to verify that the provider (core-banking-service) maintains compatibility with its consumers.

**Impact**: Breaking API changes in core-banking-service silently break fund-transfer and utility-payment services.

**Best Practice**: Implement Spring Cloud Contract or Pact for consumer-driven contract testing.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Small |

**Finding**: Credentials are hardcoded in multiple places:
- `docker-compose.yml`: MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`)
- `docker-compose/mysql/Dockerfile`: MySQL root password in ENV
- `docker-compose/mysql/privileges.sql`: Database user password (`oPItyPticIAt`)
- `README.md`: Test credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`)

**Impact**: Credential exposure in version control. Anyone with repo access has database and admin credentials.

**Best Practice**: Use Docker secrets, environment variable files (`.env` excluded from git), or a secrets manager. Never commit credentials.

---

### 4.2 No Input Validation

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding**: No Bean Validation annotations on any request DTOs. No validation of:
- Account number format
- Transfer amounts (could be negative or zero)
- Email format (user registration)
- String lengths

**Impact**: SQL injection risk (mitigated by JPA parameterization), business logic bypass (negative transfer amounts), data integrity issues.

**Best Practice**: Add `@Valid` + Bean Validation constraints on all API input DTOs.

---

### 4.3 Overly Permissive Database User

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: The application database user (`javatodev_development`) has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` (all databases).

**Impact**: A compromised service could modify or destroy any database, not just its own.

**Best Practice**: Create per-service database users with minimum required privileges scoped to their specific database.

---

### 4.4 CSRF Disabled Without Documentation

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: `ServerHttpSecurity.CsrfSpec::disable` in the gateway's `SecurityConfiguration`. This is acceptable for a stateless JWT API but should be explicitly documented as a conscious decision.

**Impact**: Low for API-only services, but risky if a web frontend is added later.

**Best Practice**: Document the CSRF decision. Add security headers (CORS, Content-Security-Policy).

---

### 4.5 Keycloak Singleton Not Thread-Safe

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: `KeycloakProperties.getInstance()` uses a naive singleton pattern (`if (keycloakInstance == null)`) without synchronization. Race condition on first access.

**Impact**: Potential multiple Keycloak client instances or NPE under concurrent load.

**Best Practice**: Use `@Bean` with Spring's singleton scope, or use `synchronized`/`volatile` with double-checked locking.

---

## 5. API Design

### 5.1 No API Versioning Strategy

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: URLs include `/api/v1/` but there is no actual versioning mechanism, no documentation of versioning policy, and no ability to run multiple versions simultaneously.

**Impact**: Breaking changes cannot be introduced safely.

**Best Practice**: Document versioning strategy. Consider header-based versioning for the gateway or URL-based with explicit deprecation policies.

---

### 5.2 Inconsistent API Naming Conventions

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**:
- Inconsistent use of hyphens vs underscores in path variables: `{account_number}` vs `{id}` vs `{account_name}`
- Mixed endpoint naming: `/bank-account/`, `/util-account/`, `/bank-users/`
- Inconsistent pluralization: `/transfer` (singular) vs `/bank-users` (plural)

**Impact**: Poor developer experience, confusing API surface.

**Best Practice**: Adopt consistent RESTful naming: plural nouns, lowercase hyphens, consistent path variable naming.

---

### 5.3 No Pagination Metadata in Responses

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: Paginated endpoints accept `Pageable` parameters but return raw `List<T>` without pagination metadata (total count, page size, current page, total pages).

**Impact**: Clients cannot implement proper pagination UI or know when they've reached the last page.

**Best Practice**: Return a wrapper object with `content`, `totalElements`, `totalPages`, `currentPage`, `size`.

---

### 5.4 No Filtering or Sorting Documentation

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: While Spring's `Pageable` supports sort parameters, there is no documentation or explicit filtering support (e.g., filter transfers by date range, status, account).

**Impact**: Clients must fetch all data and filter client-side.

**Best Practice**: Add explicit query parameters for common filters with documentation.

---

### 5.5 OpenAPI Documentation Present but Incomplete

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: `springdoc-openapi` is included in dependencies with `@Operation` and `@Tag` annotations on controllers. However:
- Raw `ResponseEntity` types prevent automatic response schema generation
- No `@ApiResponse` annotations for error cases
- No request body schema documentation

**Impact**: Auto-generated Swagger UI shows incomplete schemas.

**Best Practice**: Add typed `ResponseEntity<T>`, `@ApiResponse` for error codes, and `@Schema` annotations on DTOs.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**:
- `@Slf4j` used inconsistently (present on controllers but not all services)
- Log messages use `toString()` which may leak sensitive data: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())`
- No structured logging format (JSON)
- No correlation ID propagation in log messages

**Impact**: Difficult to trace requests across services in logs. Sensitive data may appear in log aggregators.

**Best Practice**: Use structured JSON logging (Logback JSON encoder), include trace IDs automatically via MDC, never log full request objects that may contain PII.

---

### 6.2 No Health Check Customization

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Spring Boot Actuator is included in all services, providing default `/actuator/health`. However, no custom health indicators exist for:
- Database connectivity
- Keycloak availability
- Downstream service availability

**Impact**: Health endpoint only reports "UP" based on application start, not actual dependency health.

**Best Practice**: Add custom `HealthIndicator` beans for critical dependencies.

---

### 6.3 No Custom Metrics

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: While Micrometer is included (for tracing), no custom business metrics are defined:
- No transaction count/rate metrics
- No transfer amount histograms
- No error rate counters
- No latency percentile tracking

**Impact**: Cannot monitor business health or set SLA-based alerts.

**Best Practice**: Add Micrometer counters/timers for key business operations. Expose Prometheus metrics endpoint.

---

### 6.4 Zipkin Tracing Not Verified

| Attribute | Value |
|-----------|-------|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Zipkin dependencies are included in all services but configuration is externalized to the config server. No verification that trace context propagates correctly across Feign calls or through the API Gateway.

**Impact**: Distributed tracing may be partially broken without anyone knowing.

**Best Practice**: Add integration tests that verify trace propagation. Document expected tracing behavior.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Value |
|-----------|-------|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding**: No circuit breaker pattern is implemented on any Feign client calls. If `core-banking-service` goes down:
- `fund-transfer-service` will hang on Feign calls until timeout
- `utility-payment-service` will hang on Feign calls until timeout
- `user-service` will hang on Feign calls until timeout
- Cascading failure will bring down the entire system

**Impact**: Single service failure causes system-wide outage. No graceful degradation.

**Best Practice**: Add Resilience4j circuit breakers on all Feign clients with fallback behavior.

---

### 7.2 No Retry Policies

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding**: No retry configuration on Feign clients. Transient network failures (DNS blips, connection resets) immediately fail the entire request.

**Impact**: Unnecessary failures for recoverable errors.

**Best Practice**: Configure Feign/Resilience4j retry with exponential backoff for idempotent operations. Non-idempotent operations (fund transfers) need careful consideration.

---

### 7.3 No Timeout Configuration

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Small |

**Finding**: No explicit timeout configuration visible in the codebase for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- Keycloak client timeouts

Default timeouts (often infinite or very long) are relied upon.

**Impact**: A slow downstream service causes thread starvation and cascading failures upstream.

**Best Practice**: Configure explicit timeouts on all external calls: Feign (connect: 2s, read: 5s), database pool (max-wait: 3s), Keycloak (connect: 5s).

---

### 7.4 No Fallback Behavior

| Attribute | Value |
|-----------|-------|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: When downstream calls fail, the exception propagates directly to the client with no graceful degradation. For example:
- If core-banking-service is unavailable, fund transfer fails completely
- If Keycloak is down, user listing fails (even though local data exists)

**Impact**: Partial outages become complete outages from the user's perspective.

**Best Practice**: Implement fallback strategies: cache last-known-good data, return partial results, queue for later processing.

---

### 7.5 No Idempotency Protection

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Medium |

**Finding**: Fund transfer and utility payment endpoints have no idempotency keys. A network timeout during a successful transfer could lead to a client retry and a duplicate transfer.

**Impact**: Potential double-charging of customer accounts.

**Best Practice**: Implement idempotency keys (client-generated UUID) with server-side deduplication.

---

### 7.6 Non-Atomic Transaction Processing

| Attribute | Value |
|-----------|-------|
| **Severity** | High |
| **Effort** | Large |

**Finding**: In `FundTransferService.fundTransfer()`:
1. Entity saved with `PENDING` status
2. Feign call to core-banking-service processes the transfer
3. Entity updated to `SUCCESS`

If the application crashes between steps 2 and 3, the transfer is processed but the local record remains `PENDING` (data inconsistency). There is no compensation/saga pattern.

**Impact**: Data inconsistency between services. No way to reconcile failed-in-flight transactions.

**Best Practice**: Implement the Saga pattern with compensation, or use the Transactional Outbox pattern for reliable state transitions.

---

## Summary Table

| Category | Gap | Severity | Effort |
|----------|-----|----------|--------|
| Code Organization | No multi-project build | Medium | Medium |
| Code Organization | Duplicated code across services | Medium | Medium |
| Code Organization | Inconsistent package structure | Low | Small |
| Code Organization | Mapper instantiation anti-pattern | Low | Small |
| Error Handling | Generic handler exposes internals | High | Small |
| Error Handling | All errors return HTTP 400 | High | Small |
| Error Handling | No input validation error handling | Medium | Small |
| Error Handling | Raw ResponseEntity types | Low | Small |
| Testing | Minimal test coverage | Critical | Large |
| Testing | No integration tests | High | Large |
| Testing | No contract tests | High | Medium |
| Security | Hardcoded credentials | Critical | Small |
| Security | No input validation | High | Medium |
| Security | Overly permissive DB user | Medium | Small |
| Security | CSRF disabled without docs | Low | Small |
| Security | Keycloak singleton race condition | Medium | Small |
| API Design | No versioning strategy | Medium | Medium |
| API Design | Inconsistent naming conventions | Low | Small |
| API Design | No pagination metadata | Medium | Small |
| API Design | No filtering documentation | Low | Small |
| API Design | Incomplete OpenAPI docs | Low | Small |
| Observability | Inconsistent logging | Medium | Small |
| Observability | No custom health checks | Low | Small |
| Observability | No custom metrics | Medium | Medium |
| Observability | Tracing not verified | Low | Small |
| Resilience | No circuit breakers | Critical | Medium |
| Resilience | No retry policies | High | Small |
| Resilience | No timeout configuration | High | Small |
| Resilience | No fallback behavior | Medium | Medium |
| Resilience | No idempotency protection | High | Medium |
| Resilience | Non-atomic transaction processing | High | Large |
