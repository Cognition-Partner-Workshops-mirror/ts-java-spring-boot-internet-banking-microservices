# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### 1.1 Consistent Project Structure Across Services

**Current State:** All business services follow a consistent package structure under `com.javatodev.finance`:
- `controller/` - REST controllers
- `service/` - Business logic
- `model/entity/` - JPA entities
- `model/dto/` - Data transfer objects
- `model/mapper/` - Entity-DTO mappers
- `exception/` - Exception classes and handlers
- `configuration/` - Spring configuration classes
- `repository/` - Spring Data JPA repositories

This is a solid foundation. However, there are minor inconsistencies.

**Gaps Identified:**

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| CO-1 | **No shared library / common module** | Medium | Medium | Exception classes (`SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`), audit classes (`AuditAware`), filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`), Feign configuration (`CustomFeignClientConfiguration`), and mapper base classes (`BaseMapper`) are **copy-pasted across 4 services**. Changes must be replicated manually, creating drift risk. |
| CO-2 | **No multi-project Gradle build** | Medium | Medium | Each service has an independent `build.gradle` with duplicated dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector 8.4.0, etc.). A Gradle multi-project build or shared version catalog would reduce drift. |
| CO-3 | **Inconsistent Feign client naming** | Low | Small | Fund Transfer Service uses `BankingCoreFeignClient` in package `service.rest.client`; User Service and Utility Payment Service use `BankingCoreRestClient` in package `service.rest`. Naming and package structure should be consistent. |
| CO-4 | **Inconsistent Feign client configuration** | Low | Small | User Service's `BankingCoreRestClient` does not specify a `configuration` class, while Fund Transfer and Utility Payment services specify `CustomFeignClientConfiguration.class`. |
| CO-5 | **Mapper instantiation is not DI-managed** | Low | Small | Mappers are created via `new FundTransferMapper()` inline in service classes rather than being Spring-managed beans. This makes them harder to test and inconsistent with the DI pattern used elsewhere. |

### 1.2 Separation of Concerns

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| CO-6 | **Core Banking Service mixes concerns** | Medium | Large | The Core Banking Service acts as both a domain service (account/user CRUD) and a transaction processor (fund transfer + utility payment execution). These could be separate bounded contexts. |
| CO-7 | **`AuditAware` is in `model.dto` package** | Low | Small | `AuditAware` is a JPA `@MappedSuperclass` but lives in the `model.dto` package. It should be in `model.entity` or a dedicated `model.base` package. |

---

## 2. Error Handling

### 2.1 Centralized Error Handling

**Current State:** Each service that needs it has a `GlobalExceptionHandler` using `@ControllerAdvice`. This is good practice.

**Gaps Identified:**

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| EH-1 | **Catch-all handler returns plain string** | High | Small | The generic `Exception` handler in all services returns a raw string: `"Exception occur inside API " + e`. This leaks stack traces/internal details to clients and is not a structured error response. |
| EH-2 | **All errors return HTTP 400** | High | Medium | Both `SimpleBankingGlobalException` and generic `Exception` handlers always return `400 Bad Request`. Errors like "entity not found" should return `404`, "insufficient funds" could be `422`, and unexpected errors should return `500`. |
| EH-3 | **Inconsistent `ErrorResponse` construction** | Low | Small | Fund Transfer Service constructs `ErrorResponse` via constructor (`new ErrorResponse(code, message)`) while other services use the builder pattern (`ErrorResponse.builder()...build()`). |
| EH-4 | **No error handling for Feign client failures** | High | Medium | When Feign calls to Core Banking fail (network issues, 4xx/5xx responses), there is no `FeignErrorDecoder` in Fund Transfer or Utility Payment services. Only User Service has a `CustomFeignErrorDecoder`. Feign errors will surface as raw `FeignException` and get caught by the generic handler, leaking internal details. |
| EH-5 | **Missing error codes for some exceptions** | Medium | Small | `EntityNotFoundException` in Core Banking has no error code, while `InsufficientFundsException` does. Error codes should be consistent across all exception types. |
| EH-6 | **`ErrorResponse` duplicated across services** | Medium | Small | The `ErrorResponse` class is copy-pasted in each service. This is related to CO-1 (no shared library). |

### 2.2 Consistent Error Response Format

**Current State:** The structured `ErrorResponse` has `code` and `message` fields. The generic catch-all returns a plain string, breaking the contract.

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| EH-7 | **No timestamp, path, or trace ID in error responses** | Medium | Small | Error responses lack contextual metadata (timestamp, request path, trace ID) that aids debugging. |
| EH-8 | **No validation error detail** | Medium | Medium | There is no handler for `MethodArgumentNotValidException` or `ConstraintViolationException`. If Bean Validation annotations were added, validation errors would fall through to the generic handler. |

---

## 3. Testing

### 3.1 Unit Test Coverage

**Current State:** Only the Core Banking Service has meaningful unit tests:
- `AccountServiceTest` - 6 tests
- `TransactionServiceTest` - 10 tests
- `UserServiceTest` - 3 tests

All other services have only the auto-generated Spring Boot context load test (`*ApplicationTests.java`).

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| T-1 | **No unit tests for User Service business logic** | Critical | Medium | `UserService.createUser()` contains complex multi-step logic (Keycloak check, Core Banking validation, user creation) with zero unit test coverage. |
| T-2 | **No unit tests for Fund Transfer Service** | Critical | Medium | `FundTransferService.fundTransfer()` orchestrates Feign calls and status updates with zero unit test coverage. |
| T-3 | **No unit tests for Utility Payment Service** | Critical | Medium | `UtilityPaymentService.utilPayment()` has zero unit test coverage. |
| T-4 | **Application context tests require external dependencies** | Medium | Small | `*ApplicationTests.java` in User/Fund Transfer/Utility Payment services attempt to load Spring context but require Eureka and Config Server (even though Eureka is disabled in test config, Feign clients may still fail). |

### 3.2 Integration Tests

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| T-5 | **No integration tests** | High | Large | There are no `@SpringBootTest` integration tests with `MockMvc`/`WebTestClient` that test the full request-response cycle through controllers. |
| T-6 | **No database integration tests** | High | Large | No tests verify JPA repositories, Flyway migrations, or query correctness against an actual (H2 or Testcontainers) database. |
| T-7 | **No Testcontainers setup** | Medium | Medium | Tests use H2 in-memory database which has dialect differences from MySQL. Testcontainers with MySQL would provide higher-fidelity testing. |

### 3.3 Contract Tests

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| T-8 | **No contract tests between services** | High | Large | There are no Spring Cloud Contract or Pact tests to verify that Feign client expectations match provider APIs. API changes in Core Banking could silently break Fund Transfer, Utility Payment, and User services. |

---

## 4. Security

### 4.1 Input Validation

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| S-1 | **No Bean Validation annotations on request DTOs** | Critical | Medium | `FundTransferRequest`, `UtilityPaymentRequest`, `User` (registration), and `UserUpdateRequest` have zero `@NotNull`, `@NotBlank`, `@Min`, `@Email`, or other validation annotations. Null or negative amounts can be submitted without server-side validation. |
| S-2 | **No `@Valid` on controller parameters** | Critical | Small | Controller methods accept `@RequestBody` without `@Valid`, so even if annotations were added to DTOs, they would not be enforced. |
| S-3 | **No input sanitization** | Medium | Medium | String inputs (account numbers, identification, provider names) are passed directly to database queries and Feign calls without sanitization or length limits. |

### 4.2 Authentication and Authorization

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| S-4 | **No role-based access control (RBAC)** | High | Medium | The API Gateway only checks "authenticated or not." There is no `@PreAuthorize`, role check, or scope-based authorization. Any authenticated user can call any endpoint (e.g., approve users, transfer from any account). |
| S-5 | **No ownership validation on operations** | Critical | Medium | Fund transfers and utility payments do not verify that the authenticated user owns the source account. Any authenticated user can transfer funds from any account. |
| S-6 | **CSRF disabled without justification** | Low | Small | `ServerHttpSecurity.CsrfSpec::disable` is used in the API Gateway. This is common for stateless APIs but should be documented. |
| S-7 | **User registration endpoint is publicly accessible** | Medium | Small | `/user/api/v1/bank-users/register` is explicitly `permitAll()`. While intentional, there are no rate limiting or CAPTCHA protections against abuse. |

### 4.3 Secrets Management

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| S-8 | **Hardcoded credentials in Docker Compose** | Critical | Small | MySQL root password (`woVERANKliGharym`), MySQL dev user password (`oPItyPticIAt`), Keycloak admin password (`password`), Keycloak DB password (`password`) are all hardcoded in `docker-compose.yml` and `privileges.sql`. |
| S-9 | **Keycloak client secret in test config** | High | Small | `client-secret: e8548d56-d743-45ef-8655-063c9cd96759` is hardcoded in `internet-banking-user-service/src/test/resources/application.yml`. |
| S-10 | **Test credentials in README** | Medium | Small | `ib_admin@javatodev.com / 5V7huE3G86uB` credentials are in the README. |
| S-11 | **Static Keycloak singleton is not thread-safe** | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern (`if (keycloakInstance == null)`) which is a potential race condition. |

### 4.4 Dependency Vulnerabilities

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| S-12 | **No dependency vulnerability scanning** | High | Small | No OWASP Dependency-Check, Snyk, or Dependabot configuration exists. No Gradle security plugin is configured. |
| S-13 | **`commons-lang` (v2) used in filters** | Low | Small | `AppAuthUserFilter` imports `org.apache.commons.lang.StringUtils` (Commons Lang 2, end-of-life). Should use `org.apache.commons.lang3.StringUtils` or Java's built-in methods. |

---

## 5. API Design

### 5.1 RESTful Conventions

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| A-1 | **Raw `ResponseEntity` without type parameters** | Medium | Small | All controllers use `ResponseEntity` without generics (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This loses compile-time type safety and makes OpenAPI doc generation less accurate. |
| A-2 | **Inconsistent URL patterns** | Low | Small | Core Banking uses `/api/v1/account/bank-account/{account_number}` (kebab-case with underscore path variable), while user service uses `/api/v1/bank-users/register`. Some use singular (`/account`), some plural (`/bank-users`). |
| A-3 | **POST used for creating transfers but no `201 Created` status** | Medium | Small | `POST /api/v1/transfer` and `POST /api/v1/utility-payment` return `200 OK` instead of `201 Created` with a `Location` header. |
| A-4 | **PATCH update does not follow PATCH semantics** | Low | Small | `PATCH /api/v1/bank-users/update/{id}` includes "update" in the URL (redundant with PATCH method) and should be `PATCH /api/v1/bank-users/{id}`. |

### 5.2 Pagination, Filtering, and Versioning

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| A-5 | **Pagination response lacks metadata** | Medium | Small | List endpoints return `List<T>` instead of a paginated wrapper with `totalElements`, `totalPages`, `page`, `size`. Clients cannot know how many items exist or paginate effectively. |
| A-6 | **No filtering or search capabilities** | Medium | Medium | List endpoints accept only `Pageable` (page, size, sort). There is no filtering by date range, status, account number, etc. |
| A-7 | **No API versioning strategy** | Medium | Medium | While `/api/v1/` is used in URLs, there is no documented versioning strategy, no header-based versioning, and no plan for v2. |

### 5.3 OpenAPI Documentation

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| A-8 | **Incorrect Swagger dependency** | Medium | Small | All services use `springdoc-openapi-starter-webflux-ui` but User/Fund Transfer/Utility Payment/Core Banking are Spring MVC (not WebFlux) services. Should use `springdoc-openapi-starter-webmvc-ui`. Only the API Gateway (which is WebFlux-based) should use the webflux variant. |
| A-9 | **No request/response schema documentation** | Medium | Small | `@Operation` annotations have summary/description but no `@ApiResponse`, `@Schema`, or `@Parameter` annotations for detailed schema documentation. |
| A-10 | **No global OpenAPI configuration** | Low | Small | No `@OpenAPIDefinition` with title, version, description, or security scheme configuration. |

---

## 6. Observability

### 6.1 Logging Consistency

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| O-1 | **No structured/JSON logging** | Medium | Medium | Default Spring Boot logging uses plain text format. In a containerized microservices environment, structured JSON logging (via Logback/Log4j2 JSON encoder) enables better log aggregation and search. |
| O-2 | **Sensitive data logged** | High | Small | `UserService.createUser()` logs `user.toString()` which may include the password. `FundTransferService` logs the full request including account numbers and amounts. |
| O-3 | **Inconsistent log levels** | Low | Small | All controller methods log at `INFO` level. There is no `DEBUG`-level logging for detailed flow tracing and no `WARN` for degraded states. |
| O-4 | **No correlation ID in logs** | Medium | Small | Although Micrometer/Brave tracing is configured, there is no MDC-based trace ID injection in the log pattern to correlate logs with distributed traces. |

### 6.2 Health Checks

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| O-5 | **No custom health indicators** | Medium | Small | All services include `spring-boot-starter-actuator` but rely only on default health checks. No custom health indicators for Keycloak connectivity, RabbitMQ, or downstream service health. |
| O-6 | **Health endpoint exposure not configured** | Low | Small | Default actuator configuration may not expose all useful endpoints. No explicit `management.endpoints.web.exposure.include` configuration visible in the application YAML files (may be in the external config server). |

### 6.3 Metrics

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| O-7 | **No custom business metrics** | Medium | Medium | No `@Timed`, `@Counted`, or custom `MeterRegistry` usage for business metrics (transfers per minute, payment success rate, user registration rate). |
| O-8 | **Prometheus endpoint not configured** | Medium | Small | README lists Prometheus as a technology but no `micrometer-registry-prometheus` dependency exists in any `build.gradle`. Only Zipkin tracing is wired. |

### 6.4 Distributed Tracing

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| O-9 | **No custom span annotations** | Low | Small | Tracing relies entirely on auto-instrumented spans. No `@NewSpan` or `@SpanTag` annotations for business-relevant spans (e.g., "validate-balance", "keycloak-create-user"). |
| O-10 | **Sleuth referenced but not used** | Low | Small | README mentions "Spring Cloud Sleuth" but the codebase uses Micrometer Tracing (the Sleuth successor). README should be updated. |

---

## 7. Resilience

### 7.1 Circuit Breakers

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| R-1 | **No circuit breakers** | Critical | Medium | There are no Resilience4j or Spring Cloud CircuitBreaker annotations on any Feign client or service method. If Core Banking Service goes down, all upstream services will cascade-fail with unhandled exceptions. |

### 7.2 Retry Policies

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| R-2 | **No retry configuration** | High | Small | No Spring Retry or Resilience4j Retry is configured. Transient network errors between services cause immediate failure. |
| R-3 | **No Feign retry configuration** | High | Small | Feign clients use default configuration with no retry policy. |

### 7.3 Timeout Configuration

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| R-4 | **No explicit timeout configuration** | High | Small | No Feign timeouts (`connectTimeout`, `readTimeout`), no `RestTemplate` timeouts, no gateway route timeouts are configured. Services will use defaults (which may be very long), causing thread exhaustion under load. |

### 7.4 Fallback Behavior

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| R-5 | **No fallback methods** | High | Medium | No `@CircuitBreaker(fallbackMethod=...)` or Feign fallback classes. When downstream services fail, the error propagates as-is with no graceful degradation. |
| R-6 | **No bulkhead isolation** | Medium | Medium | No thread pool or semaphore bulkhead to prevent one slow downstream service from exhausting all threads. |

### 7.5 Data Consistency

| # | Gap | Severity | Effort | Detail |
|---|-----|----------|--------|--------|
| R-7 | **No compensation/saga for distributed transactions** | Critical | Large | Fund transfer flow: Fund Transfer Service saves `PENDING` -> calls Core Banking -> updates to `SUCCESS`. If Core Banking succeeds but the status update fails, the local record stays `PENDING` while money has moved. No saga, outbox pattern, or compensation logic exists. |
| R-8 | **`@Transactional` only on Core Banking** | High | Small | `TransactionService` in Core Banking uses `@Transactional`, but the orchestrating services (Fund Transfer, Utility Payment) do not wrap their multi-step flows in transactions. A failure between the Feign call and the status update leaves data inconsistent. |

---

## Summary Matrix

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 0 | 3 | 4 | 7 |
| Error Handling | 0 | 3 | 3 | 2 | 8 |
| Testing | 3 | 3 | 1 | 0 | 7 |
| Security | 3 | 3 | 4 | 3 | 13 |
| API Design | 0 | 0 | 7 | 3 | 10 |
| Observability | 0 | 1 | 5 | 4 | 10 |
| Resilience | 2 | 4 | 1 | 0 | 7 |
| **Totals** | **8** | **14** | **24** | **16** | **62** |

### Top 10 Most Impactful Gaps

| Rank | ID | Gap | Severity | Category |
|------|-----|-----|----------|----------|
| 1 | S-5 | No ownership validation on financial operations | Critical | Security |
| 2 | R-7 | No saga/compensation for distributed transactions | Critical | Resilience |
| 3 | S-1 | No input validation on request DTOs | Critical | Security |
| 4 | R-1 | No circuit breakers on inter-service calls | Critical | Resilience |
| 5 | T-1/T-2/T-3 | No unit tests for 3 of 4 business services | Critical | Testing |
| 6 | S-8 | Hardcoded credentials in Docker Compose | Critical | Security |
| 7 | EH-1 | Catch-all exception handler leaks internals | High | Error Handling |
| 8 | EH-2 | All errors return HTTP 400 regardless of cause | High | Error Handling |
| 9 | S-4 | No role-based access control | High | Security |
| 10 | R-4 | No timeout configuration on any inter-service call | High | Resilience |
