# Engineering Standards Gap Analysis

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Table](#summary-table)

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Current state**: Each microservice is an independent Gradle project with its own `build.gradle`, `gradlew` wrapper, and `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` that ties them together.

**Gap**: Without a multi-module build, there is no way to build all services with a single command, enforce consistent dependency versions, or share common build configuration. Each service independently declares its Spring Boot version (`3.2.4`), Spring Cloud version (`2023.0.0`), and plugin versions — any drift would be silent.

**Best practice**: Use a Gradle multi-module build with a root `build.gradle` (or `build.gradle.kts`) that defines common plugins, dependency versions, and shared configuration via `subprojects {}` or a convention plugin.

---

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Current state**: The following classes are copy-pasted across 3+ services with minor variations:

- `SimpleBankingGlobalException` — identical in core-banking, user, fund-transfer, utility-payment
- `ErrorResponse` — identical in core-banking (uses `@Builder`), fund-transfer (uses constructor), user and utility-payment (use `@Builder`)
- `GlobalExceptionHandler` — identical structure across all 4 services
- `BaseMapper` — identical abstract class in core-banking, user, fund-transfer, utility-payment
- `AuditAware` — identical `@MappedSuperclass` in user, fund-transfer, utility-payment
- `AuditConfig` / `AuditorAwareConfig` — identical in user, fund-transfer, utility-payment
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` — identical in user, fund-transfer, utility-payment
- `CustomFeignClientConfiguration` — identical in fund-transfer and utility-payment

**Gap**: Code duplication means bug fixes (e.g., the inconsistent `ErrorResponse` construction) must be applied in 4 places. It also increases onboarding friction and risk of divergence.

**Best practice**: Extract shared code into a common library module (e.g., `banking-common`) published as a local Gradle dependency. Include exception classes, DTOs, mappers, audit configuration, and filter infrastructure.

---

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Current state**: Package naming is inconsistent across services:

- Core banking: `com.javatodev.finance.repository` (top-level)
- User service: `com.javatodev.finance.model.repository` (nested under `model`)
- Fund transfer: `com.javatodev.finance.model.repository` (nested under `model`)
- User service Feign: `com.javatodev.finance.service.rest` (under `service`)
- Fund transfer Feign: `com.javatodev.finance.service.rest.client` (different nesting)
- User service config: `com.javatodev.finance.configuration.feign` vs fund-transfer: `com.javatodev.finance.configuration`

**Best practice**: Adopt a consistent package convention across all services, e.g.:
```
com.javatodev.finance.{service-name}
  ├── configuration/
  ├── controller/
  ├── exception/
  ├── model/
  │   ├── dto/
  │   └── entity/
  ├── repository/
  └── service/
      └── client/
```

---

### 1.4 Mapper Instantiation Anti-Pattern

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Current state**: Mappers are instantiated inline as instance fields:
```java
private UserMapper userMapper = new UserMapper();
```

This bypasses Spring's dependency injection, making mappers untestable and preventing use of `@Autowired` dependencies within mappers.

**Best practice**: Register mappers as Spring `@Component` beans and inject them, or use a mature mapping library like MapStruct.

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400 Bad Request

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Current state**: Every `GlobalExceptionHandler` across all services returns `400 Bad Request` for all exceptions — including:

- `EntityNotFoundException` → should be `404 Not Found`
- `InsufficientFundsException` → should be `422 Unprocessable Entity` or `409 Conflict`
- Generic `Exception` → should be `500 Internal Server Error`

The fallback handler also leaks the full exception stacktrace to the client:
```java
.body("Exception occur inside API " + e);
```

**Gap**: Incorrect HTTP status codes break REST semantics and make client-side error handling unreliable. Leaking exception details is a security risk.

**Best practice**: Map exception types to appropriate HTTP status codes. Use `@ResponseStatus` annotations or explicit status in the handler. Never expose internal exception details to clients.

---

### 2.2 Inconsistent Error Response Format

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**:
- `SimpleBankingGlobalException` instances produce `ErrorResponse { code, message }`.
- The generic `Exception` handler returns a raw string: `"Exception occur inside API " + e`.

This means clients cannot rely on a consistent error response schema.

**Best practice**: All error responses should use the same JSON structure (e.g., `{ code, message, timestamp, path }`). Use a shared `ErrorResponse` DTO for all error handlers.

---

### 2.3 No Feign Error Decoder in Most Services

| | |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Current state**: The user service has a `CustomFeignErrorDecoder` class (found in `configuration/feign/`), but the fund-transfer and utility-payment services do not. This means HTTP error responses from core-banking are wrapped in generic `FeignException`s that bubble up with unhelpful messages to clients.

**Best practice**: All Feign clients should have a custom error decoder that translates downstream HTTP errors into meaningful domain exceptions.

---

### 2.4 No Validation on Request Bodies

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Current state**: None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`, `UserUpdateRequest`) use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). Controllers do not use `@Valid` or `@Validated`.

**Gap**: Any malformed request (null account number, negative amount, empty email) passes straight through to the service layer and potentially to the database, causing cryptic errors or data corruption.

**Best practice**: Add Bean Validation annotations to all request DTOs. Add `@Valid` to all `@RequestBody` parameters. Handle `MethodArgumentNotValidException` in the global exception handler with a structured error response listing field-level violations.

---

## 3. Testing

### 3.1 Tests Only Exist in Core Banking Service

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |

**Current state**:
- **core-banking-service**: 3 unit test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with ~20 test methods covering service layer logic.
- **All other services** (user, fund-transfer, utility-payment, api-gateway, service-registry, config-server): Only have the auto-generated Spring Boot `*ApplicationTests.java` with a single `contextLoads()` test (which likely fails without infrastructure since it needs MySQL/Keycloak/Config Server).

**Gap**: ~80% of the application's business logic is untested:
- User Service: Registration flow, Keycloak integration, approval workflow
- Fund Transfer Service: Transfer orchestration, status management
- Utility Payment Service: Payment orchestration, status management
- API Gateway: Security filter chain, route resolution, header propagation

**Best practice**: Minimum 80% unit test coverage on all service classes. Integration tests for Feign clients using WireMock. Spring Security test for the gateway filter chain.

---

### 3.2 No Integration Tests

| | |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Current state**: No `@SpringBootTest` integration tests, no Testcontainers usage, no WireMock stubs for Feign clients, no end-to-end test scenarios.

**Best practice**: Add `@SpringBootTest` integration tests with:
- **Testcontainers** for MySQL to validate JPA mappings, Flyway migrations, and repository queries.
- **WireMock** to stub Feign client responses for inter-service calls.
- **`@WebMvcTest`** for controller-layer tests with MockMvc.

---

### 3.3 No Contract Tests Between Services

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |

**Current state**: No Spring Cloud Contract or Pact tests exist. Each Feign client interface must match the controller it targets (e.g., `FundTransferRequest` exists in both fund-transfer-service and core-banking-service with slightly different fields — fund-transfer's version has `authID` but core-banking's does not).

**Gap**: API contract changes in core-banking-service can silently break fund-transfer and utility-payment services. The `FundTransferRequest` mismatch is already a latent bug.

**Best practice**: Implement consumer-driven contract tests using Spring Cloud Contract or Pact to ensure Feign client interfaces remain compatible with provider controllers.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Current state**: The following credentials are hardcoded in committed files:

| File | Credential |
|------|-----------|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/docker-compose.yml` | Keycloak admin: `admin` / `password` |
| `docker-compose/docker-compose.yml` | PostgreSQL: `keycloak` / `password` |
| `docker-compose/mysql/privileges.sql` | MySQL user password: `oPItyPticIAt` |
| `docker-compose/mysql/Dockerfile` | MySQL root password (ENV) |
| `README.md` | Test credentials: `ib_admin@javatodev.com` / `5V7huE3G86uB` |

**Best practice**: Use `.env` files (gitignored) or Docker Compose secrets. Reference environment variables in compose files. Never commit real or example passwords in source control.

---

### 4.2 CSRF Disabled Without Documentation

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: The API Gateway disables CSRF protection:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```

This is acceptable for a pure API (no browser session cookies), but there is no documentation explaining the security rationale.

**Best practice**: Add a code comment and architectural decision record (ADR) explaining why CSRF is disabled (stateless JWT-based API, no cookie sessions).

---

### 4.3 Keycloak Singleton is Not Thread-Safe

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Current state**: `KeycloakProperties.getInstance()` uses a classic double-checked-locking-without-volatile pattern:
```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

This is **not thread-safe** — multiple threads can create multiple instances on startup.

**Best practice**: Use a `@Bean` method in a `@Configuration` class to create the `Keycloak` instance as a Spring-managed singleton, or add `synchronized` / use `volatile`.

---

### 4.4 No Authorization Beyond Authentication

| | |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Current state**: The API Gateway validates JWT tokens (authentication) but there is no role-based access control (authorization). Any authenticated user can:
- Transfer funds from any account
- Make utility payments from any account
- View and manage any user
- Access admin endpoints like user approval (`PATCH /update/{id}`)

The `X-Auth-Id` header is propagated but never verified against the resource being accessed.

**Best practice**: Implement role-based security (e.g., `ADMIN`, `USER` roles from Keycloak). Add method-level security (`@PreAuthorize`) or resource-ownership validation in service methods.

---

### 4.5 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: No OWASP Dependency Check, Snyk, or similar tool is configured in any `build.gradle`. No GitHub Dependabot configuration exists.

**Best practice**: Add the `org.owasp.dependencycheck` Gradle plugin and integrate it into CI. Enable Dependabot for automated dependency update PRs.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: Most controller methods return raw `ResponseEntity` without type parameters:
```java
public ResponseEntity getBankAccount(...)
public ResponseEntity fundTransfer(...)
```

Only the user-service controllers use generics: `ResponseEntity<User>`, `ResponseEntity<List<User>>`.

**Gap**: Without generic types, the OpenAPI/Swagger documentation generator cannot infer response schemas. Compile-time type safety is also lost.

**Best practice**: Always use typed `ResponseEntity<T>` with the correct response DTO type.

---

### 5.2 Inconsistent URL Patterns

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Current state**:
- Core banking uses underscores in path variables: `/bank-account/{account_number}`, `/util-account/{account_name}`
- Fund transfer uses no path for POST: `POST /api/v1/transfer`
- User service uses `/register` and `/update/{id}` sub-paths

Inconsistencies:
- Mix of `snake_case` and `kebab-case` in path variables
- Some endpoints use plural nouns (`/bank-users`), others use singular (`/account`)
- `PATCH /update/{id}` is redundant — the HTTP verb already implies update

**Best practice**: Adopt a consistent RESTful convention:
- Plural resource names: `/accounts`, `/users`, `/transfers`, `/payments`
- No verb in URLs: Use `PATCH /users/{id}` instead of `PATCH /users/update/{id}`
- Consistent case: Use `kebab-case` for paths, `camelCase` for query/body params

---

### 5.3 No API Versioning Strategy

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Current state**: All endpoints use `/api/v1/` prefix, but there is no documented versioning strategy, no v2 paths, and no content negotiation-based versioning.

**Best practice**: Document the versioning strategy (URL path-based is fine). Plan for backward-compatible evolution with deprecation headers.

---

### 5.4 No Pagination Metadata in Responses

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: Paginated endpoints accept Spring's `Pageable` parameter but return raw `List<T>`:
```java
return userMapper.convertToDtoList(userRepository.findAll(pageable).getContent());
```

The `Page` object's metadata (total elements, total pages, current page) is discarded.

**Best practice**: Return a paginated wrapper (e.g., Spring's `Page<T>` or a custom `PagedResponse { content, page, size, totalElements, totalPages }`) so clients can implement pagination correctly.

---

### 5.5 OpenAPI Documentation Misconfigured

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: Services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but this is the **WebFlux** starter — most services use Spring MVC (not WebFlux). The correct dependency for MVC services would be `springdoc-openapi-starter-webmvc-ui`. Only the API Gateway (which uses WebFlux) should use the WebFlux variant.

Some OpenAPI annotations (`@Tag`, `@Operation`) are present but response schemas are undocumented due to raw `ResponseEntity` usage (see 5.1).

**Best practice**: Use the correct `springdoc-openapi-starter-webmvc-ui` for MVC-based services. Add `@ApiResponse` annotations documenting success and error responses.

---

## 6. Observability

### 6.1 Inconsistent Logging Patterns

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: Logging is ad-hoc:
- Some controllers log incoming requests: `log.info("Fund transfer initiated in core bank from {}", ...)`.
- Others log very little or nothing.
- Service layer methods have sparse logging.
- No structured logging format (JSON).
- No correlation ID in log messages (despite Micrometer tracing being configured, log output may not include trace/span IDs without explicit Logback/Log4j configuration).

**Best practice**: Adopt a consistent logging strategy:
- Log at method entry/exit for all service methods (DEBUG level).
- Log all external calls (INFO level).
- Use structured JSON logging (Logback Logstash encoder).
- Ensure Micrometer trace/span IDs appear in every log line via MDC.

---

### 6.2 Health Check Endpoints Not Customized

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Current state**: Spring Boot Actuator is included in all services, and actuator paths are explicitly permitted in the gateway security config. However, there are no custom health indicators for:
- Database connectivity (Spring's auto-configured `DataSourceHealthIndicator` may be active but is not verified)
- Keycloak reachability
- Config Server connectivity
- Eureka registration status

**Best practice**: Configure `management.endpoint.health.show-details=always` (for internal endpoints) and add custom health indicators for critical integration points (Keycloak, RabbitMQ when added).

---

### 6.3 No Metrics Endpoints or Dashboards

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Current state**: While `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, there is no:
- Prometheus metrics endpoint configuration
- Grafana dashboard definitions
- Custom business metrics (e.g., transfer count, payment volume, error rates)

The README mentions Prometheus in the technology stack, but no Prometheus scrape configuration or container exists in Docker Compose.

**Best practice**: Enable `/actuator/prometheus` endpoint. Add a Prometheus container to Docker Compose with scrape configs. Create custom `MeterRegistry` counters for business events. Add Grafana with pre-built dashboards.

---

### 6.4 Distributed Tracing Not Fully Wired

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Current state**: Zipkin dependencies are present and a Zipkin container is in Docker Compose, but:
- Tracing configuration (sampling rate, endpoint URL) is in the external config server — not verifiable from the source repo.
- No explicit `management.tracing.sampling.probability` setting visible.
- The API Gateway does not explicitly propagate trace context headers through the reactive filter chain.

**Best practice**: Set `management.tracing.sampling.probability=1.0` for dev/test. Verify trace context propagation across all Feign calls. Add Zipkin URL to `application.yml` as a fallback.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Current state**: No Resilience4j or Hystrix circuit breakers are configured on any Feign client or service method. If core-banking-service becomes slow or unavailable:
- Fund transfer service will hang or throw connection timeouts.
- Utility payment service will hang or throw connection timeouts.
- User service registration will fail with a raw Feign exception.
- The API Gateway will propagate failures to clients.

**Gap**: A single service failure cascades to all dependent services, creating a complete system outage.

**Best practice**: Add Resilience4j (`spring-cloud-starter-circuitbreaker-resilience4j`) with circuit breakers on all Feign clients. Configure open/half-open thresholds, timeout durations, and fallback methods.

---

### 7.2 No Retry Policies

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Current state**: No retry configuration exists for Feign clients or any HTTP calls. Transient network failures immediately propagate as errors.

**Best practice**: Configure Spring Retry or Resilience4j retry on Feign clients for idempotent operations (GET requests). Ensure non-idempotent operations (fund transfers, payments) are NOT retried to prevent duplicate transactions.

---

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Current state**: No explicit connect or read timeouts are configured for Feign clients, RestTemplate, or any HTTP client. Default JVM socket timeouts (often infinite) will be used.

**Gap**: A hanging downstream service will cause upstream threads to block indefinitely, eventually exhausting the thread pool and bringing down the entire service.

**Best practice**: Configure explicit timeouts for all HTTP clients:
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
```

---

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Current state**: When core-banking-service is unavailable, the orchestrator services (fund-transfer, utility-payment, user) throw raw exceptions with no graceful degradation.

- Fund transfers in `PENDING` state have no mechanism to be retried or reconciled.
- Utility payments in `PROCESSING` state are abandoned.
- No dead letter queue or compensation transaction exists.

**Best practice**: Implement fallback methods for Feign clients that return meaningful error responses. Add a scheduled reconciliation job for stuck `PENDING`/`PROCESSING` transactions. Consider saga pattern for distributed transaction management.

---

### 7.5 No Rate Limiting

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Current state**: The API Gateway has no rate limiting configured. All endpoints are unbounded.

**Best practice**: Configure Spring Cloud Gateway's `RequestRateLimiter` filter with Redis-backed rate limiting, or use a dedicated API gateway (e.g., Kong) for production deployments.

---

### 7.6 Fund Transfer Has No Transaction Atomicity Across Services

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |

**Current state**: The fund transfer flow spans two services:
1. Fund Transfer Service saves entity with status `PENDING`.
2. Feign call to Core Banking Service performs the actual debit/credit.
3. Fund Transfer Service updates status to `SUCCESS`.

If the application crashes between steps 2 and 3, money has been moved in core banking but the fund transfer record remains `PENDING`. There is no saga, outbox pattern, or two-phase commit to ensure consistency.

Similarly, the `availableBalance` bug in `TransactionService` means account balances are silently corrupted.

**Best practice**: Implement the **Saga pattern** with compensation transactions, or use the **Transactional Outbox pattern** with an event table and message relay. Fix the balance calculation bug immediately.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|-----|----------|----------|--------|
| 1.1 | No multi-module Gradle build | Code Organization | Medium | Medium |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 2.1 | All exceptions return HTTP 400 | Error Handling | Critical | Small |
| 2.2 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.3 | No Feign error decoder in most services | Error Handling | High | Medium |
| 2.4 | No validation on request bodies | Error Handling | Critical | Small |
| 3.1 | Tests only exist in core banking service | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | Medium | Large |
| 4.1 | Hardcoded credentials in source code | Security | Critical | Small |
| 4.2 | CSRF disabled without documentation | Security | Medium | Small |
| 4.3 | Keycloak singleton is not thread-safe | Security | High | Small |
| 4.4 | No authorization beyond authentication | Security | High | Medium |
| 4.5 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw `ResponseEntity` without generics | API Design | Medium | Small |
| 5.2 | Inconsistent URL patterns | API Design | Low | Small |
| 5.3 | No API versioning strategy | API Design | Low | Small |
| 5.4 | No pagination metadata in responses | API Design | Medium | Small |
| 5.5 | OpenAPI documentation misconfigured | API Design | Medium | Small |
| 6.1 | Inconsistent logging patterns | Observability | Medium | Small |
| 6.2 | Health check endpoints not customized | Observability | Low | Small |
| 6.3 | No metrics endpoints or dashboards | Observability | Medium | Medium |
| 6.4 | Distributed tracing not fully wired | Observability | Medium | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No rate limiting | Resilience | Medium | Medium |
| 7.6 | No transaction atomicity across services | Resilience | Critical | Large |

### Severity Distribution

| Severity | Count |
|----------|-------|
| Critical | 6 |
| High | 7 |
| Medium | 13 |
| Low | 5 |

### Effort Distribution

| Effort | Count |
|--------|-------|
| Small | 19 |
| Medium | 8 |
| Large | 4 |
