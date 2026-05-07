# Engineering Standards Gap Analysis

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

This document compares the codebase against industry engineering best practices across seven dimensions. Each gap is rated by **Severity** and **Remediation Effort**.

**Severity Scale:**
- **Critical** — Production risk, data loss/corruption, or security vulnerability
- **High** — Significant quality/reliability impact; should be fixed before production
- **Medium** — Best-practice deviation that will cause maintenance pain
- **Low** — Polish / nice-to-have improvement

**Effort Scale:**
- **Small** — < 1 day per service
- **Medium** — 1–3 days per service
- **Large** — 3+ days, may require architectural changes

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Severity | Effort |
|----------|--------|
| Medium | Small |

Each service has its own standalone `build.gradle` with no root-level `settings.gradle` or parent build file. This prevents shared dependency version management, consistent plugin configuration, and single-command builds across the entire project.

**Evidence:** Seven independent `build.gradle` files with duplicated `springCloudVersion`, Spring Boot version, and plugin declarations.

### 1.2 Duplicated Code Across Services

| Severity | Effort |
|----------|--------|
| High | Medium |

The following classes are copy-pasted identically (or near-identically) across 3–4 services:
- `BaseMapper` — identical abstract mapper in core-banking, user-service, fund-transfer, utility-payment
- `AuditAware` — identical `@MappedSuperclass` in user-service, fund-transfer, utility-payment
- `AuditConfig` / `AuditorAwareConfig` — identical in user-service, fund-transfer, utility-payment
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` — identical in user-service, fund-transfer, utility-payment
- `SimpleBankingGlobalException` / `ErrorResponse` / `GlobalExceptionHandler` — duplicated in all four business services
- `TransactionStatus` enum — duplicated in fund-transfer and utility-payment

**Risk:** Bug fixes or changes to shared logic must be applied to every copy manually, leading to drift.

### 1.3 Inconsistent Package Structure

| Severity | Effort |
|----------|--------|
| Low | Small |

- Core banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- User service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `exception`, `configuration.*`
- Fund transfer: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.dto.request`, `model.dto.response`, `service.rest.client`
- Utility payment: `model.dto`, `model.entity`, `model.mapper`, `model.rest.request`, `model.rest.response`, `repository`

The `repository` package placement varies (`repository` vs `model.repository`), and DTO/request/response packages are organized differently across services.

### 1.4 No Shared Library Module

| Severity | Effort |
|----------|--------|
| Medium | Medium |

There is no `common` or `shared` library module for cross-cutting concerns (exception classes, audit infrastructure, request context, base mapper). This forces the duplication noted in 1.2.

---

## 2. Error Handling

### 2.1 Raw `ResponseEntity` Return Types (No Generics)

| Severity | Effort |
|----------|--------|
| Medium | Small |

Most controller methods return `ResponseEntity` without type parameters (raw type). For example:

```java
public ResponseEntity getBankAccount(...) // core-banking AccountController
public ResponseEntity fundTransfer(...)   // core-banking TransactionController
```

Only the User Service's `UserController` uses typed `ResponseEntity<User>`. Raw types suppress compiler checks and break Swagger/OpenAPI response schema generation.

### 2.2 All Exceptions Return HTTP 400

| Severity | Effort |
|----------|--------|
| High | Small |

The `GlobalExceptionHandler` in every service returns `400 Bad Request` for **all** exceptions, including:
- `EntityNotFoundException` (should be `404 Not Found`)
- `InsufficientFundsException` (could be `422 Unprocessable Entity`)
- Unexpected exceptions (should be `500 Internal Server Error`)

Additionally, the catch-all `Exception` handler returns the raw exception object concatenated into a string body: `"Exception occur inside API " + e`, which leaks stack traces to clients.

### 2.3 Inconsistent `ErrorResponse` Construction

| Severity | Effort |
|----------|--------|
| Low | Small |

- Core banking and user service use `ErrorResponse.builder().code(...).message(...).build()`
- Fund transfer service uses `new ErrorResponse(e.getCode(), e.getMessage())` (constructor-based)
- The `ErrorResponse` class is `@Builder` in all services but the fund-transfer constructor usage implies an implicit constructor was added

### 2.4 `SimpleBankingGlobalException` Design Issues

| Severity | Effort |
|----------|--------|
| Medium | Small |

- The class shadows `Throwable.message` with its own `message` field (both via Lombok `@Getter/@Setter` and through `RuntimeException`), causing confusion between `getMessage()` and the Lombok-generated getter.
- The single-arg constructor calls `super(message)` but doesn't set the `code` field.
- `@AllArgsConstructor` sets `(code, message)` but doesn't call `super(message)`, so `Throwable.getMessage()` returns `null` while the Lombok getter returns the field value.

### 2.5 No Feign Error Decoder in Fund Transfer Service

| Severity | Effort |
|----------|--------|
| Medium | Small |

The fund transfer service's `CustomFeignClientConfiguration` extends `FeignClientProperties.FeignClientConfiguration` but does not define a custom `ErrorDecoder` bean (unlike the user service which has `CustomFeignErrorDecoder`). This means Feign errors from core-banking are decoded with the default decoder, losing structured error information.

**Contrast:** The utility payment service also lacks a custom error decoder.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Severity | Effort |
|----------|--------|
| Critical | Large |

| Service | Test Files | What's Tested |
|---------|-----------|---------------|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` + context load test | Service layer unit tests with Mockito |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` (context load only) | Nothing meaningful |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` (context load only) | Nothing meaningful |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` (context load only) | Nothing meaningful |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` (context load only) | Nothing meaningful |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` (context load only) | Nothing meaningful |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` (context load only) | Nothing meaningful |

**Missing entirely:**
- Controller/integration tests (MockMvc / WebTestClient)
- Feign client contract tests
- End-to-end / smoke tests
- API Gateway routing tests
- Security configuration tests

### 3.2 No Test Database Migration Setup for Non-Core Services

| Severity | Effort |
|----------|--------|
| High | Small |

User, fund-transfer, and utility-payment services have `src/test/resources/application.yml` with H2 config and `flyway.enabled: false`, `ddl-auto: none`. This means JPA entities' tables won't be created in tests — the context load tests likely fail or are skipped. No Flyway migrations exist for these services.

### 3.3 No CI Pipeline

| Severity | Effort |
|----------|--------|
| High | Medium |

There are no GitHub Actions workflows, Jenkinsfiles, or other CI/CD configuration. Tests are not automatically run on pull requests.

---

## 4. Security

### 4.1 Hardcoded Credentials in Docker Compose and Source

| Severity | Effort |
|----------|--------|
| Critical | Small |

Sensitive credentials are hardcoded in version-controlled files:

| File | Credential |
|------|-----------|
| `docker-compose/docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose/mysql/Dockerfile` | MySQL root password (ENV) |
| `docker-compose/mysql/privileges.sql` | Dev user password: `oPItyPticIAt` |
| `docker-compose/docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose/docker-compose.yml` | Keycloak DB password: `password` |
| `internet-banking-user-service/src/test/resources/application.yml` | Keycloak client-secret: `e8548d56-d743-45ef-8655-063c9cd96759` |
| `README.md` | Test user password: `5V7huE3G86uB` |

### 4.2 No Input Validation

| Severity | Effort |
|----------|--------|
| Critical | Small |

None of the request DTOs use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.). Controllers accept `@RequestBody` without `@Valid` or `@Validated`. This means:
- Fund transfers can be initiated with null/negative amounts
- Users can register with empty emails or passwords
- Utility payments can reference null provider IDs

### 4.3 CSRF Disabled Without Documentation

| Severity | Effort |
|----------|--------|
| Low | Small |

`SecurityConfiguration` disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While appropriate for a stateless API, this decision should be documented and the service should enforce other protections (e.g., proper CORS configuration).

### 4.4 No Rate Limiting

| Severity | Effort |
|----------|--------|
| High | Medium |

The API Gateway has no rate limiting configuration. Financial APIs are prime targets for brute force and denial-of-service attacks.

### 4.5 Password Handling in User DTO

| Severity | Effort |
|----------|--------|
| High | Small |

The `User` DTO in the user service contains a `password` field that flows through the entire request/response cycle. The password:
- Is logged in `UserController.createUser()`: `log.info("Creating user with {}", request.toString())` — this logs the password in plaintext
- Has no `@JsonProperty(access = WRITE_ONLY)` annotation, so it could be serialized in responses
- Is passed directly to Keycloak without any validation (length, complexity)

### 4.6 Downstream Services Have No Authentication

| Severity | Effort |
|----------|--------|
| High | Medium |

Services behind the gateway (core-banking, user-service, fund-transfer, utility-payment) have no authentication/authorization of their own. They rely entirely on the gateway for JWT validation. If any service is directly accessible (e.g., misconfigured network, internal access), all endpoints are unprotected. The `X-Auth-Id` header is trusted without verification.

### 4.7 No Dependency Vulnerability Scanning

| Severity | Effort |
|----------|--------|
| Medium | Small |

No OWASP Dependency Check, Snyk, or similar tool is configured in the build. Known vulnerabilities in transitive dependencies would go undetected.

---

## 5. API Design

### 5.1 Inconsistent Base Paths

| Severity | Effort |
|----------|--------|
| Medium | Small |

- Core banking: `/api/v1/account`, `/api/v1/transaction`, `/api/v1/user`
- User service: `/api/v1/bank-users`
- Fund transfer: `/api/v1/transfer`
- Utility payment: `/api/v1/utility-payment`

The core banking service exposes three separate base paths while the pattern in other services is one base path per service. There's no consistent naming convention (hyphenated vs. non-hyphenated).

### 5.2 No API Versioning Strategy

| Severity | Effort |
|----------|--------|
| Medium | Small |

All endpoints use `/api/v1/` but there's no documented versioning strategy, no content negotiation headers, and no mechanism to evolve APIs without breaking clients.

### 5.3 Pagination Returns Raw Lists

| Severity | Effort |
|----------|--------|
| Medium | Small |

Paginated endpoints (e.g., `GET /api/v1/transfer`, `GET /api/v1/bank-users`) accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. This discards pagination metadata (total elements, total pages, current page) that clients need for proper pagination UX.

### 5.4 No Filtering or Sorting Documentation

| Severity | Effort |
|----------|--------|
| Low | Small |

While Spring Data's `Pageable` supports `sort` query parameters, there's no documentation or explicit configuration of allowed sort fields, default sorting, or additional filter parameters.

### 5.5 OpenAPI/Swagger Misconfiguration

| Severity | Effort |
|----------|--------|
| Medium | Small |

All four business services include `springdoc-openapi-starter-webflux-ui:2.1.0`, which is the **WebFlux** variant. However, core-banking, user-service, fund-transfer, and utility-payment are all **Spring MVC** (servlet-based) applications. They should use `springdoc-openapi-starter-webmvc-ui` instead. The WebFlux variant may not correctly detect servlet-based controllers.

The API Gateway (which IS WebFlux-based) does not include any springdoc dependency for aggregated API documentation.

### 5.6 No HATEOAS / Hypermedia Links

| Severity | Effort |
|----------|--------|
| Low | Medium |

API responses are plain DTOs with no links to related resources. For a banking API, linking accounts to their transactions, users to their accounts, etc. would improve API discoverability.

---

## 6. Observability

### 6.1 Inconsistent Logging

| Severity | Effort |
|----------|--------|
| Medium | Small |

- Some controllers log at `info` level for every request; others don't log at all
- String concatenation used in log statements instead of parameterized logging:
  ```java
  log.info("Sending fund transfer request {}" + request.toString()); // WRONG: concatenation, not placeholder
  ```
- `AppAuthUserFilter` logs every incoming request at `info` level (noisy in production)
- Error logging in `CustomFeignErrorDecoder` uses string concatenation: `log.error("..." + e)` instead of `log.error("...", e)`
- No structured logging (JSON) configured
- No MDC (Mapped Diagnostic Context) setup for trace correlation beyond what Micrometer provides

### 6.2 Health Checks Are Default Only

| Severity | Effort |
|----------|--------|
| Medium | Small |

Spring Boot Actuator is included in all services, providing `/actuator/health`. However:
- No custom health indicators for database connectivity, Keycloak reachability, or Feign client targets
- Health endpoint exposure is not explicitly configured (likely only `health` and `info` are exposed by default)
- No readiness/liveness probes configured for Kubernetes deployment

### 6.3 Metrics Not Explicitly Configured

| Severity | Effort |
|----------|--------|
| Medium | Small |

Prometheus is listed in the tech stack but:
- No `micrometer-registry-prometheus` dependency in any `build.gradle`
- No Prometheus scrape configuration
- No custom business metrics (e.g., transactions per second, transfer amounts, error rates)
- Actuator metrics endpoint not explicitly exposed

### 6.4 Distributed Tracing Coverage Unclear

| Severity | Effort |
|----------|--------|
| Low | Small |

Micrometer Tracing with Brave and Zipkin reporter are correctly included as dependencies. However:
- Sampling rate is not explicitly configured (defaults to 10%)
- No custom span annotations for critical business operations
- Trace propagation through the API Gateway to downstream services is not verified

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Severity | Effort |
|----------|--------|
| Critical | Medium |

There are no circuit breaker implementations (e.g., Resilience4j, Spring Cloud Circuit Breaker) on any Feign client calls. If core-banking-service becomes unavailable:
- Fund transfer service will continuously retry and fail
- User service registration will fail with poor error messages
- Utility payment service will fail
- No fallback behavior is defined

### 7.2 No Retry Policies

| Severity | Effort |
|----------|--------|
| High | Small |

Feign clients use default settings with no retry configuration. Transient network failures will immediately propagate as errors to clients. There are no Spring Retry configurations.

### 7.3 No Timeout Configuration

| Severity | Effort |
|----------|--------|
| High | Small |

No explicit connection or read timeouts are configured for:
- Feign clients (default timeouts are very long or infinite)
- Database connections
- Keycloak admin client
- RestTemplate/WebClient calls

A slow downstream service could tie up all threads in the calling service.

### 7.4 No Fallback Behavior

| Severity | Effort |
|----------|--------|
| Medium | Medium |

There are no fallback methods, cached responses, or graceful degradation patterns. If any dependency is down, the entire call chain fails with a raw exception.

### 7.5 No Bulkhead Pattern

| Severity | Effort |
|----------|--------|
| Medium | Medium |

All Feign calls share the same thread pool. A slow response from one service could exhaust all threads and prevent calls to other services.

### 7.6 Fund Transfer Not Idempotent

| Severity | Effort |
|----------|--------|
| Critical | Medium |

The `POST /api/v1/transfer` endpoint has no idempotency key. If a client retries due to a network timeout (after the server processed the request but before the response was received), the transfer will be executed twice. For a financial system, this is a critical gap.

### 7.7 No Compensation / Saga Pattern

| Severity | Effort |
|----------|--------|
| High | Large |

Fund transfers and utility payments involve multi-step distributed transactions:
1. Save local entity (fund-transfer/utility-payment service)
2. Call core-banking to execute the transaction

If step 2 succeeds but the response is lost, or if saving the updated status in step 3 fails, the local entity remains in `PENDING`/`PROCESSING` state with no reconciliation mechanism. There is no saga orchestration, compensation logic, or outbox pattern to handle partial failures.

---

## Summary Matrix

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 2 | 1 | 4 |
| Error Handling | 0 | 1 | 3 | 1 | 5 |
| Testing | 1 | 2 | 0 | 0 | 3 |
| Security | 2 | 3 | 1 | 1 | 7 |
| API Design | 0 | 0 | 4 | 2 | 6 |
| Observability | 0 | 0 | 3 | 1 | 4 |
| Resilience | 2 | 2 | 2 | 0 | 6 |
| **Total** | **5** | **9** | **15** | **6** | **35** |
