# Engineering Standards Gap Analysis

> **Repository:** `ts-java-spring-boot-internet-banking-microservices`
> **Assessed against:** Production-grade microservices best practices
> **Date:** 2026-05-20

---

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Table](#8-summary-table)

---

## 1. Code Organization

### 1.1 What's in Place

- Each service follows a consistent package structure: `com.javatodev.finance` with sub-packages for `controller`, `service`, `model` (entity/dto/mapper), `repository`, `exception`, `configuration`.
- Clean separation of concerns: controllers delegate to services, services use repositories.
- Each service has its own `build.gradle` and `gradlew` wrapper.

### 1.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| CO-1 | **No shared library for duplicated code** | High | Medium | `BaseMapper`, `AuditAware`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `AuditorAwareConfig`, `AuditConfig`, `ErrorResponse`, `SimpleBankingGlobalException` are copy-pasted identically across 3–4 services. Any bug fix or change must be replicated in every service. |
| CO-2 | **Inconsistent Feign client configuration** | Medium | Small | Fund-transfer-service uses a simple `Logger.Level` bean. User-service has a full `CustomFeignErrorDecoder`. Utility-payment-service has an empty configuration class extending `FeignClientConfiguration`. Approach should be unified. |
| CO-3 | **No root build file / composite build** | Medium | Medium | Each service is a standalone Gradle project. There is no root `settings.gradle` or composite build to enable cross-service dependency management, consistent plugin versions, or single-command builds. |
| CO-4 | **Mappers not Spring-managed beans** | Low | Small | All mappers (`UserMapper`, `BankAccountMapper`, `FundTransferMapper`, `UtilityPaymentMapper`) are instantiated with `new` inside service classes instead of being `@Component` beans, bypassing Spring lifecycle and making them harder to test/mock. |
| CO-5 | **Inconsistent package naming** | Low | Small | Utility-payment-service puts DTOs in `model.rest.request` / `model.rest.response` while other services use `model.dto.request` / `model.dto.response`. |

---

## 2. Error Handling

### 2.1 What's in Place

- Each service has a `@ControllerAdvice` `GlobalExceptionHandler` that handles `SimpleBankingGlobalException` and generic `Exception`.
- Core-banking-service has typed exceptions: `EntityNotFoundException`, `InsufficientFundsException`.
- User-service has additional typed exceptions: `InvalidBankingUserException`, `InvalidEmailException`, `UserAlreadyRegisteredException`.
- User-service has a `CustomFeignErrorDecoder` that extracts error details from Feign responses.

### 2.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| EH-1 | **All exceptions return HTTP 400 (Bad Request)** | High | Small | `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()`. Entity-not-found should return `404`, insufficient funds could return `422`, and generic exceptions should return `500`. |
| EH-2 | **Inconsistent error response format** | High | Small | Core-banking and utility-payment use `ErrorResponse.builder()`. Fund-transfer uses `new ErrorResponse(code, message)` constructor. Generic exception handler returns a plain `String` instead of a structured JSON response. |
| EH-3 | **Generic exception handler leaks internal details** | Critical | Small | `handleException()` returns `"Exception occur inside API " + e` which exposes stack traces and internal class names to API consumers. |
| EH-4 | **Duplicated exception classes across services** | Medium | Medium | `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler` are duplicated in every service with slight variations. Should be in a shared library. |
| EH-5 | **Fund-transfer-service has no Feign error decoder** | Medium | Small | Unlike user-service, fund-transfer-service has no `CustomFeignErrorDecoder`, so Feign errors from core-banking bubble up as raw `FeignException` instead of domain exceptions. |
| EH-6 | **No error handling for failed downstream calls in orchestration services** | High | Medium | `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` do not catch Feign exceptions. If the core-banking call fails, the local entity remains in `PENDING`/`PROCESSING` status forever – there is no rollback or `FAILED` status update. |

---

## 3. Testing

### 3.1 What's in Place

- Core-banking-service has meaningful unit tests for `AccountService` (6 tests), `TransactionService` (5 tests), and `UserService` (3 tests) using Mockito.
- All services have `contextLoads()` smoke tests (though most would fail without infrastructure).
- Test configurations use H2 in-memory databases with Flyway disabled.
- JUnit 5 platform is configured in all `build.gradle` files.

### 3.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| TE-1 | **No tests for internet-banking-* services** | Critical | Large | User-service, fund-transfer-service, and utility-payment-service have **zero** unit or integration tests for their service/controller layers. Only `contextLoads()` exists. |
| TE-2 | **No integration tests** | High | Large | There are no `@SpringBootTest` integration tests that exercise the full request path (controller → service → repository) with a test database. |
| TE-3 | **No contract tests between services** | High | Large | Inter-service communication via Feign is tested against the real core-banking-service (or not at all). No Spring Cloud Contract or Pact consumer/provider tests exist. |
| TE-4 | **No test for API Gateway security rules** | Medium | Medium | Gateway security configuration (which paths are public vs authenticated) has no tests. |
| TE-5 | **contextLoads() tests require external config** | Medium | Small | Services depending on Spring Cloud Config will fail `contextLoads()` if the config server is unreachable. Test profiles should be self-contained. |
| TE-6 | **No test coverage reporting** | Medium | Small | No JaCoCo or similar plugin is configured to measure and enforce code coverage. |

---

## 4. Security

### 4.1 What's in Place

- API Gateway enforces JWT validation via Spring Security OAuth 2.0 Resource Server.
- CSRF is disabled (appropriate for a stateless REST API).
- Keycloak integration provides centralized identity management.
- User registration endpoint is explicitly public; all others require authentication.
- Actuator endpoints are public (appropriate for internal use with proper network segmentation).

### 4.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| SE-1 | **Hardcoded credentials in Docker Compose** | Critical | Small | MySQL root password (`woVERANKliGharym`) and Keycloak admin password (`password`) are in plaintext in `docker-compose.yml` and the MySQL `Dockerfile`. Should use environment variables or Docker secrets. |
| SE-2 | **Keycloak client secret in test application.yml** | High | Small | `internet-banking-user-service/src/test/resources/application.yml` contains `client-secret: e8548d56-d743-45ef-8655-063c9cd96759`. Even in test config, secrets should be externalized. |
| SE-3 | **No input validation on request DTOs** | Critical | Medium | None of the `@RequestBody` DTOs use Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, etc.). A transfer with `amount = -1000` or null `fromAccount` would be processed. |
| SE-4 | **No authorization beyond authentication** | High | Medium | All authenticated users can access all endpoints. There is no role-based access control (RBAC) for admin operations (e.g. user approval, reading all users). |
| SE-5 | **Downstream services trust X-Auth-Id header blindly** | High | Medium | Any request that bypasses the gateway (or a compromised gateway) can inject arbitrary `X-Auth-Id` headers. Downstream services should validate JWT tokens independently or the network must be locked down. |
| SE-6 | **Test credentials committed to README** | Medium | Small | `ib_admin@javatodev.com / 5V7huE3G86uB` is in the README. While acceptable for demo projects, it's a risk if anyone reuses these credentials in production. |
| SE-7 | **No dependency vulnerability scanning** | High | Small | No OWASP Dependency-Check, Snyk, or Dependabot configured. Old versions of transitive dependencies may have known CVEs. |
| SE-8 | **Expired JWT token in Postman collection** | Low | Small | The Postman collection contains a hardcoded expired JWT token. This is cosmetic but suggests tokens are being committed instead of generated dynamically. |

---

## 5. API Design

### 5.1 What's in Place

- RESTful URL structure (`/api/v1/...`) with appropriate HTTP methods.
- API versioning via URL path (`v1`).
- Swagger/OpenAPI annotations on controllers (`@Tag`, `@Operation`) in core-banking, user, fund-transfer, and utility-payment services.
- Springdoc OpenAPI dependency included for auto-generated documentation.
- Consistent use of `ResponseEntity` return type.

### 5.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| AD-1 | **Raw `ResponseEntity` without type parameters** | Medium | Small | Most controller methods return `ResponseEntity` without generics (e.g. `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This degrades OpenAPI documentation quality and type safety. |
| AD-2 | **No pagination metadata in responses** | High | Medium | `GET` endpoints that accept `Pageable` return `List<T>` instead of `Page<T>` or a wrapper with `totalElements`, `totalPages`, `pageNumber`. Clients cannot implement pagination UIs. |
| AD-3 | **No filtering or search on list endpoints** | Medium | Medium | List endpoints accept only `Pageable` – no query parameters for filtering by status, date range, account number, etc. |
| AD-4 | **Inconsistent resource naming** | Medium | Small | User-service uses `/bank-users`, core uses `/user`. Fund-transfer uses `/transfer` (noun) while utility-payment uses `/utility-payment`. Should follow consistent plural nouns. |
| AD-5 | **POST for user registration does not return 201** | Medium | Small | `POST /register` returns `200 OK` instead of `201 Created` with a `Location` header. |
| AD-6 | **PATCH update returns full entity instead of 204** | Low | Small | `PATCH /update/{id}` returns the full user object. While acceptable, a `204 No Content` with `Location` header is more RESTful for updates. |
| AD-7 | **Wrong Springdoc dependency for servlet-based services** | Medium | Small | Services using `spring-boot-starter-web` (servlet) include `springdoc-openapi-starter-webflux-ui` instead of `springdoc-openapi-starter-webmvc-ui`. This works due to fallback but is incorrect and may cause issues. |
| AD-8 | **No global OpenAPI configuration** | Low | Small | No `@OpenAPIDefinition` or centralized OpenAPI config with API title, version, description, servers, or security scheme definitions. |

---

## 6. Observability

### 6.1 What's in Place

- Spring Boot Actuator is included in all services (health check, info endpoint).
- Micrometer Tracing with Brave bridge + Zipkin reporter for distributed tracing.
- `feign-micrometer` for Feign call instrumentation.
- Git properties plugin generates build metadata.
- SLF4J/Logback logging via Lombok `@Slf4j` in most classes.

### 6.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| OB-1 | **No structured/JSON logging** | High | Small | Default Logback text format is used. Structured JSON logging (e.g. Logstash encoder) is needed for log aggregation platforms (ELK, Datadog, etc.). |
| OB-2 | **Inconsistent log levels and messages** | Medium | Small | Some methods use `log.info()` for all operations (including reading data), some use string concatenation (`"..." + variable`) instead of parameterized logging (`"... {}", variable`), risking unnecessary string allocations. |
| OB-3 | **No custom health indicators** | Medium | Small | Only default health endpoint is exposed. No custom health checks for MySQL connectivity, Keycloak availability, or downstream service reachability. |
| OB-4 | **No Prometheus metrics endpoint** | Medium | Small | Despite Prometheus being listed in the tech stack (README), `micrometer-registry-prometheus` is not included as a dependency in any `build.gradle`. |
| OB-5 | **Actuator endpoints not configured** | Medium | Small | No `management.endpoints.web.exposure.include` configuration in local `application.yml` – only health/info are exposed by default. Prometheus, env, and metrics endpoints are hidden. |
| OB-6 | **No correlation ID propagation** | Medium | Medium | While Micrometer tracing generates trace IDs, they are not included in log output (no MDC pattern configured) and not returned in API responses for client-side troubleshooting. |
| OB-7 | **Logging sensitive data** | High | Small | `FundTransferRequest.toString()` and `UtilityPaymentRequest.toString()` are logged directly, potentially exposing account numbers and amounts in plaintext. |

---

## 7. Resilience

### 7.1 What's in Place

- `wait-for-it.sh` in Docker entrypoints ensures services start after dependencies are available.
- `@Transactional` on `TransactionService` for database consistency.
- Optimistic locking via `@Version` on audit-aware entities.

### 7.2 Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| RE-1 | **No circuit breakers** | Critical | Medium | No Resilience4j or Hystrix circuit breakers on Feign clients. If core-banking-service goes down, all upstream services will hang until Feign timeout (default: no timeout) and cascade failures. |
| RE-2 | **No timeout configuration** | Critical | Small | No Feign, RestTemplate, or connection pool timeouts are configured. A slow or unresponsive downstream service will block threads indefinitely. |
| RE-3 | **No retry policies** | High | Small | No automatic retries for transient failures (network blips, temporary 503s). Spring Retry or Resilience4j retry is not configured. |
| RE-4 | **No fallback behavior** | High | Medium | No fallback methods or default responses when downstream services are unavailable. The system returns raw error responses. |
| RE-5 | **No idempotency for write operations** | High | Medium | Fund transfer and utility payment `POST` endpoints have no idempotency keys. Network retries or duplicate submissions will create duplicate transactions. |
| RE-6 | **Incomplete transaction state management** | High | Medium | If the Feign call to core-banking fails after the local entity is saved, the entity remains in `PENDING`/`PROCESSING` forever. No compensating transaction, scheduled cleanup, or retry mechanism exists. |
| RE-7 | **Singleton Keycloak instance (not thread-safe)** | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized singleton pattern. Under concurrent access, multiple Keycloak instances could be created, or a partially-constructed instance could be returned. |
| RE-8 | **No rate limiting** | Medium | Medium | API Gateway has no rate limiting. A single client can overwhelm the system. |
| RE-9 | **No bulkhead pattern** | Medium | Medium | All Feign calls share the same thread pool. A slow endpoint will consume all threads and starve other endpoints. |

---

## 8. Summary Table

| ID | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| CO-1 | Code Organization | No shared library for duplicated code | High | Medium |
| CO-2 | Code Organization | Inconsistent Feign client configuration | Medium | Small |
| CO-3 | Code Organization | No root build file / composite build | Medium | Medium |
| CO-4 | Code Organization | Mappers not Spring-managed beans | Low | Small |
| CO-5 | Code Organization | Inconsistent package naming | Low | Small |
| EH-1 | Error Handling | All exceptions return HTTP 400 | High | Small |
| EH-2 | Error Handling | Inconsistent error response format | High | Small |
| EH-3 | Error Handling | Generic exception handler leaks internal details | Critical | Small |
| EH-4 | Error Handling | Duplicated exception classes | Medium | Medium |
| EH-5 | Error Handling | Fund-transfer missing Feign error decoder | Medium | Small |
| EH-6 | Error Handling | No error handling for failed downstream calls | High | Medium |
| TE-1 | Testing | No tests for internet-banking services | Critical | Large |
| TE-2 | Testing | No integration tests | High | Large |
| TE-3 | Testing | No contract tests between services | High | Large |
| TE-4 | Testing | No test for API Gateway security rules | Medium | Medium |
| TE-5 | Testing | contextLoads() tests require external config | Medium | Small |
| TE-6 | Testing | No test coverage reporting | Medium | Small |
| SE-1 | Security | Hardcoded credentials in Docker Compose | Critical | Small |
| SE-2 | Security | Keycloak client secret in test config | High | Small |
| SE-3 | Security | No input validation on request DTOs | Critical | Medium |
| SE-4 | Security | No authorization beyond authentication | High | Medium |
| SE-5 | Security | Downstream services trust X-Auth-Id blindly | High | Medium |
| SE-6 | Security | Test credentials in README | Medium | Small |
| SE-7 | Security | No dependency vulnerability scanning | High | Small |
| SE-8 | Security | Expired JWT in Postman collection | Low | Small |
| AD-1 | API Design | Raw ResponseEntity without type parameters | Medium | Small |
| AD-2 | API Design | No pagination metadata in responses | High | Medium |
| AD-3 | API Design | No filtering or search on list endpoints | Medium | Medium |
| AD-4 | API Design | Inconsistent resource naming | Medium | Small |
| AD-5 | API Design | POST registration returns 200 instead of 201 | Medium | Small |
| AD-6 | API Design | PATCH update returns entity instead of 204 | Low | Small |
| AD-7 | API Design | Wrong Springdoc dependency (webflux vs webmvc) | Medium | Small |
| AD-8 | API Design | No global OpenAPI configuration | Low | Small |
| OB-1 | Observability | No structured/JSON logging | High | Small |
| OB-2 | Observability | Inconsistent log levels and messages | Medium | Small |
| OB-3 | Observability | No custom health indicators | Medium | Small |
| OB-4 | Observability | No Prometheus metrics endpoint | Medium | Small |
| OB-5 | Observability | Actuator endpoints not configured | Medium | Small |
| OB-6 | Observability | No correlation ID propagation | Medium | Medium |
| OB-7 | Observability | Logging sensitive data | High | Small |
| RE-1 | Resilience | No circuit breakers | Critical | Medium |
| RE-2 | Resilience | No timeout configuration | Critical | Small |
| RE-3 | Resilience | No retry policies | High | Small |
| RE-4 | Resilience | No fallback behavior | High | Medium |
| RE-5 | Resilience | No idempotency for write operations | High | Medium |
| RE-6 | Resilience | Incomplete transaction state management | High | Medium |
| RE-7 | Resilience | Singleton Keycloak instance (not thread-safe) | Medium | Small |
| RE-8 | Resilience | No rate limiting | Medium | Medium |
| RE-9 | Resilience | No bulkhead pattern | Medium | Medium |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 6 |
| High | 18 |
| Medium | 18 |
| Low | 4 |
| **Total** | **46** |
