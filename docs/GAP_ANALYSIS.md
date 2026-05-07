# Engineering Standards Gap Analysis

This document compares the current codebase against engineering best practices and documents gaps with severity ratings and remediation effort estimates.

---

## 1. Code Organization

### Gap 1.1: No Multi-Project Gradle Build

**Current State:** Each microservice has its own independent `build.gradle` with duplicated dependency declarations, plugin versions, and configuration.

**Best Practice:** Use a Gradle multi-project build (`settings.gradle` at root) with a shared `buildSrc` or convention plugin to centralize dependency versions and common configuration.

**Impact:** Version drift between services, duplicated maintenance effort, inconsistent dependency versions across services.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### Gap 1.2: Duplicated Code Across Services

**Current State:** The following classes are copy-pasted across multiple services with minor variations:
- `GlobalExceptionHandler` (3 copies with slight differences)
- `ErrorResponse` (3 copies)
- `SimpleBankingGlobalException` (3 copies)
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` (3 copies)
- `CustomFeignClientConfiguration` (2 copies)
- `AuditAware` / `AuditConfig` / `AuditorAwareConfig` (3 copies)
- `BaseMapper` (3 copies)

**Best Practice:** Extract shared code into a common library module (e.g., `banking-common`) that other services depend on.

**Impact:** Bug fixes must be applied in multiple places; divergence over time; increased maintenance burden.

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### Gap 1.3: Inconsistent Package Structure

**Current State:** Services use slightly different package layouts:
- User service: `model.repository` vs others using `repository`
- User service Feign: `service.rest.BankingCoreRestClient` vs fund-transfer: `service.rest.client.BankingCoreFeignClient`
- User service Feign config: `configuration.feign.CustomFeignClientConfiguration` vs others: `configuration.CustomFeignClientConfiguration`

**Best Practice:** Standardize package naming conventions across all services.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

## 2. Error Handling

### Gap 2.1: Generic Exception Handler Returns Plain Strings

**Current State:** All `GlobalExceptionHandler` implementations have a catch-all `Exception.class` handler that returns a plain string:
```java
return ResponseEntity.badRequest().body("Exception occur inside API " + e);
```

**Best Practice:** Return a structured error response (consistent JSON shape) with appropriate HTTP status codes. Never expose raw exception details to clients (security risk: stack traces, internal class names).

| Severity | Effort |
|----------|--------|
| Critical | Small |

---

### Gap 2.2: All Errors Return 400 Bad Request

**Current State:** Both custom exceptions and generic exceptions return `400 Bad Request` regardless of the actual error type. `EntityNotFoundException` should return `404`, `InsufficientFundsException` should return `422`, and unexpected exceptions should return `500`.

**Best Practice:** Map exception types to appropriate HTTP status codes (404, 409, 422, 500, etc.).

| Severity | Effort |
|----------|--------|
| High | Small |

---

### Gap 2.3: No Error Handling for Feign Client Failures

**Current State:** The fund-transfer and utility-payment services call core-banking via Feign with no error handling. If core-banking returns an error or is unavailable, the raw Feign exception propagates to the client. Only user-service has a `CustomFeignErrorDecoder`.

**Best Practice:** Implement `ErrorDecoder` for all Feign clients to translate downstream errors into meaningful exceptions with appropriate context.

| Severity | Effort |
|----------|--------|
| High | Small |

---

### Gap 2.4: Inconsistent Error Response Structure

**Current State:** `ErrorResponse` uses `code` + `message` in custom exceptions, but the generic handler returns a plain string. The fund-transfer service uses `new ErrorResponse(code, message)` while others use the builder pattern.

**Best Practice:** Single, consistent error response DTO with fields like `timestamp`, `status`, `error`, `message`, `path`, `traceId`.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

## 3. Testing

### Gap 3.1: Minimal Test Coverage

**Current State:** Only `core-banking-service` has meaningful unit tests (3 test classes). The other 5 services have only the auto-generated Spring Boot context load test (which requires the full application context including external dependencies).

**Best Practice:** Each service should have unit tests for service layer logic, controller tests (MockMvc/WebTestClient), and integration tests for repository layer.

| Severity | Effort |
|----------|--------|
| Critical | Large |

---

### Gap 3.2: No Integration Tests

**Current State:** No integration tests exist that verify database operations, Feign client behavior, or end-to-end request flows. No Testcontainers usage.

**Best Practice:** Use Testcontainers for MySQL integration tests, WireMock for Feign client tests, and `@SpringBootTest` with test profiles for integration scenarios.

| Severity | Effort |
|----------|--------|
| High | Large |

---

### Gap 3.3: No Contract Tests Between Services

**Current State:** No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). Changes to core-banking-service APIs could silently break downstream consumers.

**Best Practice:** Implement contract tests to ensure API compatibility between services during independent deployment.

| Severity | Effort |
|----------|--------|
| Medium | Large |

---

### Gap 3.4: Context Load Tests Will Fail Without Infrastructure

**Current State:** Default `*ApplicationTests.java` classes attempt to load the full Spring context, which requires Config Server, Eureka, MySQL, and Keycloak to be running. These tests are effectively broken for CI.

**Best Practice:** Either configure test profiles that disable external dependencies or replace context load tests with focused slice tests (`@WebMvcTest`, `@DataJpaTest`).

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

## 4. Security

### Gap 4.1: No Input Validation

**Current State:** No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` annotations on any request DTOs. No validation on:
- Transfer amounts (could be negative or zero)
- Account numbers (could be empty or malformed)
- Email format during registration
- String length limits

**Best Practice:** Use Bean Validation (`jakarta.validation`) annotations on all request DTOs and `@Valid` on controller parameters. Add a validation exception handler.

| Severity | Effort |
|----------|--------|
| Critical | Small |

---

### Gap 4.2: Hardcoded Credentials in Docker Compose

**Current State:** Database passwords and Keycloak admin credentials are hardcoded in `docker-compose.yml` and the MySQL Dockerfile:
- MySQL root: `woVERANKliGharym`
- MySQL app user: `oPItyPticIAt`
- Keycloak admin: `admin`/`password`
- Keycloak DB: `keycloak`/`password`

**Best Practice:** Use Docker Compose `.env` files or Docker secrets for credential management. At minimum, use environment variable substitution.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 4.3: Keycloak Singleton is Not Thread-Safe

**Current State:** `KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Best Practice:** Use a `@Bean` in a `@Configuration` class (Spring manages thread-safety) or use double-checked locking.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 4.4: CSRF Disabled Without Justification

**Current State:** `SecurityConfiguration` disables CSRF: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While typical for pure API servers with token auth, there's no documentation of this security decision.

**Best Practice:** Document security decisions. If CSRF is disabled, ensure all state-changing operations require a valid Bearer token.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

### Gap 4.5: No Rate Limiting or Request Throttling

**Current State:** The API Gateway has no rate limiting. An attacker could flood the fund-transfer endpoint.

**Best Practice:** Configure Spring Cloud Gateway rate limiting filters (e.g., `RequestRateLimiter` with Redis).

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### Gap 4.6: Password Exposed in User DTO

**Current State:** The `User` DTO in user-service includes `password` field that gets serialized in responses.

**Best Practice:** Use separate request/response DTOs or `@JsonProperty(access = WRITE_ONLY)` to prevent password from being returned in API responses.

| Severity | Effort |
|----------|--------|
| Critical | Small |

---

## 5. API Design

### Gap 5.1: Raw ResponseEntity Without Type Parameters

**Current State:** Most controller methods return `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...) { ... }
```

**Best Practice:** Use typed responses (`ResponseEntity<BankAccount>`) for compile-time safety and accurate OpenAPI documentation generation.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 5.2: No API Versioning Strategy

**Current State:** APIs use `/api/v1/` prefix but there's no documented versioning strategy or mechanism for introducing v2 endpoints while maintaining backward compatibility.

**Best Practice:** Document the versioning strategy (URL path, header, or media type) and have a deprecation policy.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

### Gap 5.3: Inconsistent Pagination

**Current State:** Endpoints accepting `Pageable` don't document pagination parameters. Spring's default pagination works but the response doesn't include pagination metadata (total pages, total elements, current page).

**Best Practice:** Return paginated responses wrapped in a standard envelope with metadata: `{content: [...], page: {number, size, totalElements, totalPages}}`.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 5.4: No HATEOAS or Resource Links

**Current State:** Responses are flat DTOs with no hypermedia links for related resources.

**Best Practice:** For a banking API, consider at minimum providing self-links and links to related resources (e.g., account -> transactions).

| Severity | Effort |
|----------|--------|
| Low | Medium |

---

### Gap 5.5: Swagger Dependency Mismatch

**Current State:** Services include `springdoc-openapi-starter-webflux-ui` but core-banking, user, fund-transfer, and utility-payment services are all servlet-based (Spring MVC), not WebFlux. Only the API Gateway is reactive.

**Best Practice:** Use `springdoc-openapi-starter-webmvc-ui` for servlet-based services. The wrong dependency may cause classpath conflicts or non-functional Swagger UI.

| Severity | Effort |
|----------|--------|
| High | Small |

---

## 6. Observability

### Gap 6.1: Inconsistent Logging

**Current State:** Logging uses `@Slf4j` annotations but:
- Log messages contain `toString()` calls on request objects (potential PII exposure)
- No structured logging format (JSON)
- No correlation ID / trace ID in log messages
- Log levels are not configurable per-service without config server

**Best Practice:** Use structured JSON logging (Logback JSON encoder), include traceId/spanId in MDC, and never log sensitive data (passwords, account numbers in full).

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### Gap 6.2: No Health Check Dependencies

**Current State:** Spring Boot Actuator is included but health checks don't verify downstream dependencies (database connectivity, Eureka registration, Config Server availability).

**Best Practice:** Configure health indicators for all critical dependencies: `management.endpoint.health.show-details=always` and custom health indicators for Keycloak and RabbitMQ (when implemented).

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 6.3: No Custom Metrics

**Current State:** Only default Micrometer metrics are exposed. No business metrics (transfer count, payment volume, error rates by type, active users).

**Best Practice:** Add custom metrics using Micrometer counters/gauges/timers for key business operations. Configure Prometheus scraping.

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### Gap 6.4: Zipkin Tracing Configuration Not Verified

**Current State:** Tracing dependencies are included in all services, but tracing configuration (sample rate, Zipkin URL) depends on Spring Cloud Config which fetches from an external Git repo. No fallback if Config Server is unavailable.

**Best Practice:** Include sensible tracing defaults in local `application.yml` with Config Server values as overrides.

| Severity | Effort |
|----------|--------|
| Low | Small |

---

## 7. Resilience

### Gap 7.1: No Circuit Breakers

**Current State:** All inter-service communication is via synchronous Feign calls with no circuit breaker pattern. If core-banking-service becomes slow or unavailable, all dependent services will hang and eventually exhaust thread pools.

**Best Practice:** Implement Resilience4j circuit breakers on all Feign clients with appropriate thresholds, timeout configurations, and fallback responses.

| Severity | Effort |
|----------|--------|
| Critical | Medium |

---

### Gap 7.2: No Retry Policies

**Current State:** Feign calls have no retry configuration. Transient network failures result in immediate failure.

**Best Practice:** Configure Spring Cloud OpenFeign retry or Resilience4j retry with exponential backoff for idempotent operations (GET requests). Non-idempotent operations (POST fund-transfer) need idempotency keys before retry is safe.

| Severity | Effort |
|----------|--------|
| High | Medium |

---

### Gap 7.3: No Timeout Configuration

**Current State:** No explicit timeout configuration for Feign clients, database connections, or HTTP connections. Defaults apply (which may be too generous — e.g., no connect/read timeout).

**Best Practice:** Configure explicit timeouts at all integration points:
- Feign: `connectTimeout`, `readTimeout`
- HikariCP: `connectionTimeout`, `maximumPoolSize`
- API Gateway: route-level timeouts

| Severity | Effort |
|----------|--------|
| High | Small |

---

### Gap 7.4: No Fallback Behavior

**Current State:** Service failures propagate directly to the client. No graceful degradation.

**Best Practice:** Implement fallback responses for non-critical operations (e.g., return cached account balance if core-banking is temporarily unavailable for read operations).

| Severity | Effort |
|----------|--------|
| Medium | Medium |

---

### Gap 7.5: Startup Dependency Chain is Fragile

**Current State:** Services use `wait-for-it.sh` with a 50-second timeout to wait for dependencies. If Config Server or Eureka takes longer than 50 seconds, services fail to start with no automatic restart.

**Best Practice:** Use Spring Cloud's built-in retry for config fetching (`spring.cloud.config.retry.*`), configure Docker Compose `restart: on-failure`, and implement health-check-based dependencies.

| Severity | Effort |
|----------|--------|
| Medium | Small |

---

### Gap 7.6: Transaction Integrity Issues

**Current State:**
- `internalFundTransfer()` does not use pessimistic/optimistic locking on bank accounts
- The fund-transfer orchestration has no compensation logic if the core-banking call succeeds but the local status update fails
- `TransactionEntity` uses `@OneToOne` with `CascadeType.ALL` to `BankAccountEntity` — this is semantically incorrect (should be `@ManyToOne`) and cascading ALL on account from a transaction is dangerous

**Best Practice:** Use `@Version` for optimistic locking, implement the Saga pattern with compensation, and fix the JPA relationship mapping.

| Severity | Effort |
|----------|--------|
| Critical | Large |

---

## Summary Table

| Category | Gap | Severity | Effort |
|----------|-----|----------|--------|
| Code Organization | No multi-project Gradle build | Medium | Medium |
| Code Organization | Duplicated code across services | High | Medium |
| Code Organization | Inconsistent package structure | Low | Small |
| Error Handling | Generic exception returns plain strings | Critical | Small |
| Error Handling | All errors return 400 | High | Small |
| Error Handling | No Feign error handling | High | Small |
| Error Handling | Inconsistent error response structure | Medium | Small |
| Testing | Minimal test coverage | Critical | Large |
| Testing | No integration tests | High | Large |
| Testing | No contract tests | Medium | Large |
| Testing | Broken context load tests | Medium | Small |
| Security | No input validation | Critical | Small |
| Security | Hardcoded credentials | Medium | Small |
| Security | Non-thread-safe Keycloak singleton | Medium | Small |
| Security | CSRF disabled without documentation | Low | Small |
| Security | No rate limiting | High | Medium |
| Security | Password exposed in DTO | Critical | Small |
| API Design | Raw ResponseEntity types | Medium | Small |
| API Design | No versioning strategy documented | Low | Small |
| API Design | No pagination metadata | Medium | Small |
| API Design | No HATEOAS | Low | Medium |
| API Design | Wrong Swagger dependency | High | Small |
| Observability | Inconsistent logging | High | Medium |
| Observability | No health check dependencies | Medium | Small |
| Observability | No custom metrics | Medium | Medium |
| Observability | Tracing config not verified | Low | Small |
| Resilience | No circuit breakers | Critical | Medium |
| Resilience | No retry policies | High | Medium |
| Resilience | No timeout configuration | High | Small |
| Resilience | No fallback behavior | Medium | Medium |
| Resilience | Fragile startup chain | Medium | Small |
| Resilience | Transaction integrity issues | Critical | Large |

### Severity Distribution

- **Critical:** 7 gaps
- **High:** 10 gaps
- **Medium:** 11 gaps
- **Low:** 5 gaps
