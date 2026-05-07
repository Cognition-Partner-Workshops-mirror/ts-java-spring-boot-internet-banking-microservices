# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices and documents gaps across seven categories. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

## Table of Contents

1. [Code Organization](#1-code-organization)
2. [Error Handling](#2-error-handling)
3. [Testing](#3-testing)
4. [Security](#4-security)
5. [API Design](#5-api-design)
6. [Observability](#6-observability)
7. [Resilience](#7-resilience)
8. [Summary Matrix](#8-summary-matrix)

---

## 1. Code Organization

### What's Good

- Each microservice follows a consistent base package (`com.javatodev.finance`)
- Standard layered architecture within each service: `controller` → `service` → `repository` / `model`
- Clean separation of entities, DTOs, and mappers
- Gradle wrapper included per service for reproducible builds
- Externalized configuration via Spring Cloud Config Server

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| CO-1 | **No shared library / common module** | `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `BaseMapper`, `AuditAware`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, and `CustomFeignClientConfiguration` are copy-pasted across 3-4 services with slight variations. This creates maintenance burden and drift risk. | High | Medium |
| CO-2 | **Inconsistent package structure across services** | Core Banking uses `repository/` package while Fund Transfer uses `model.repository/`. User Service places Feign clients under `service.rest/` while Fund Transfer uses `service.rest.client/`. Utility Payment puts request/response DTOs under `model.rest/` while Fund Transfer uses `model.dto.request/` and `model.dto.response/`. | Medium | Small |
| CO-3 | **Mapper instantiation not via Spring DI** | All mappers are instantiated with `new` inside service classes (e.g., `private UserMapper userMapper = new UserMapper()`) rather than being Spring beans. This makes them harder to test and violates the DI pattern used everywhere else. | Low | Small |
| CO-4 | **No multi-module Gradle build** | Each service is a standalone Gradle project with its own `settings.gradle`. There is no root-level `settings.gradle` or `build.gradle` to manage shared dependencies, plugin versions, or common configurations. Dependency versions (e.g., `mysql-connector-j:8.4.0`, `springdoc:2.1.0`) are duplicated across all `build.gradle` files. | Medium | Medium |
| CO-5 | **Keycloak singleton is not thread-safe** | `KeycloakProperties.getInstance()` uses a non-synchronized static singleton pattern. Multiple threads could create duplicate instances during startup. | Medium | Small |
| CO-6 | **No `.editorconfig` or code formatting standard** | Inconsistent indentation (tabs vs spaces mix visible in `build.gradle` files). No enforced code formatting standard (e.g., Checkstyle, Spotless). | Low | Small |

---

## 2. Error Handling

### What's Good

- `@ControllerAdvice` with `GlobalExceptionHandler` is present in all data-bearing services
- Custom exception hierarchy exists: `SimpleBankingGlobalException` → `EntityNotFoundException`, `InsufficientFundsException`, etc.
- Structured `ErrorResponse` DTO with `code` and `message` fields

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| EH-1 | **Catch-all handler returns raw exception string** | `handleException(Exception e)` returns `"Exception occur inside API " + e` — a plain string, not the structured `ErrorResponse` format. This leaks stack traces and internal class names to clients. | Critical | Small |
| EH-2 | **All errors return HTTP 400 (Bad Request)** | Both `handleGlobalException()` and `handleException()` return `ResponseEntity.badRequest()`. `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422 or 409, and unexpected exceptions should return 500. | High | Small |
| EH-3 | **No error handling for Feign client failures** | When a Feign call to Core Banking fails (network error, timeout, 4xx/5xx), the exception propagates unhandled. Only User Service has a `CustomFeignErrorDecoder`; Fund Transfer and Utility Payment services lack graceful Feign error handling. | High | Medium |
| EH-4 | **Inconsistent exception classes across services** | Core Banking has `EntityNotFoundException` and `InsufficientFundsException` with `GlobalErrorCode`. User Service has additional exceptions (`InvalidEmailException`, `UserAlreadyRegisteredException`, `InvalidBankingUserException`). Fund Transfer and Utility Payment only have `SimpleBankingGlobalException`. No shared exception hierarchy. | Medium | Medium |
| EH-5 | **No validation error handling** | No `MethodArgumentNotValidException` handler for `@Valid` / Bean Validation errors. If validation annotations are added in the future, the errors will fall through to the generic catch-all. | Medium | Small |
| EH-6 | **ErrorResponse lacks timestamp and request path** | The `ErrorResponse` only has `code` and `message`. Industry standard error responses include `timestamp`, `path`, `status`, and optionally `traceId` for correlation with distributed tracing. | Medium | Small |

---

## 3. Testing

### What's Good

- Core Banking Service has meaningful unit tests for `AccountService` (6 tests) and `TransactionService` (10 tests)
- Tests use Mockito for mocking dependencies
- H2 in-memory database configured for test profile
- JUnit 5 platform configured in all services

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| TE-1 | **No tests for 5 out of 6 services** | User Service, Fund Transfer Service, Utility Payment Service, API Gateway, and Service Registry only have empty `@SpringBootTest` context-load tests. Zero business logic test coverage for these services. | Critical | Large |
| TE-2 | **No integration tests** | No tests verify actual HTTP endpoint behavior (e.g., `@WebMvcTest`, `MockMvc`, `@SpringBootTest` with `TestRestTemplate`). Controller layer is completely untested. | High | Large |
| TE-3 | **No contract tests between services** | Services communicate via Feign but there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify that API contracts between services remain compatible during evolution. | High | Large |
| TE-4 | **No end-to-end or smoke tests** | No automated tests that verify the full request flow through the API Gateway → downstream service → Core Banking. The Postman collection exists but is not automated in CI. | Medium | Medium |
| TE-5 | **Core Banking tests don't verify controller layer** | All existing tests are at the service layer only. No tests verify that controllers correctly map HTTP methods, path variables, or response codes. | Medium | Medium |
| TE-6 | **No test coverage reporting** | No JaCoCo or similar coverage plugin configured. No visibility into which code paths are tested. | Medium | Small |
| TE-7 | **Test configuration disables Flyway** | Test profile sets `flyway.enabled: false` and relies on Hibernate DDL or manual setup, which means Flyway migrations are never validated in tests. | Low | Small |

---

## 4. Security

### What's Good

- OAuth2/JWT authentication enforced at the API Gateway level via Keycloak
- User registration endpoint is correctly permitted without authentication
- Actuator endpoints are permitted (standard practice for health checks behind a network boundary)
- `X-Auth-Id` header propagation from Gateway to downstream services
- CSRF disabled (appropriate for stateless REST API)

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| SE-1 | **No input validation on any request body** | No `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Size`, or any Bean Validation annotations on any DTO or controller parameter across the entire codebase. Fund transfer amounts could be negative or zero; account numbers could be null or empty. | Critical | Medium |
| SE-2 | **Hardcoded credentials in docker-compose and SQL** | MySQL root password (`woVERANKliGharym`), application DB password (`oPItyPticIAt`), Keycloak admin password (`password`), Keycloak DB password (`password`), and test user credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`) are committed in plain text. | Critical | Medium |
| SE-3 | **Downstream services have no authentication** | Once past the API Gateway, services communicate over plain HTTP with no mutual authentication. Any service on the Docker network can call Core Banking endpoints directly without a token. The `X-Auth-Id` header is trusted implicitly — it can be spoofed by any caller bypassing the gateway. | High | Large |
| SE-4 | **No rate limiting** | No rate limiting at the API Gateway or any service level. Fund transfer and payment endpoints are vulnerable to abuse. | High | Medium |
| SE-5 | **Keycloak singleton stores credentials in static field** | `KeycloakProperties` stores the `Keycloak` client instance in a `static` field. If the class is unloaded and reloaded, the singleton may leak. Client secret is held in memory indefinitely. | Medium | Small |
| SE-6 | **No CORS configuration** | No CORS policy defined at the Gateway. If a web frontend is added, cross-origin requests will be blocked by default, or an overly permissive policy may be added hastily. | Medium | Small |
| SE-7 | **No dependency vulnerability scanning** | No OWASP Dependency Check, Snyk, or Dependabot configuration. No mechanism to detect known CVEs in transitive dependencies. | Medium | Small |
| SE-8 | **Password transmitted in plain JSON** | User registration sends the password in plain text JSON body (`user.getPassword()`). While HTTPS mitigates in-transit risk, the password may appear in access logs, Feign debug logs, or Zipkin traces. | Medium | Small |
| SE-9 | **No authorization beyond authentication** | The Gateway only checks "is the user authenticated?" — there is no role-based access control (RBAC). Any authenticated user can approve other users, view all transfers, etc. | High | Medium |

---

## 5. API Design

### What's Good

- Consistent `/api/v1/` prefix across all services
- RESTful resource-oriented URL structure
- Pagination support via Spring `Pageable` on list endpoints
- Swagger/OpenAPI annotations present on all controllers

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| AP-1 | **Raw `ResponseEntity` without type parameters** | Most controller methods return `ResponseEntity` instead of `ResponseEntity<SpecificType>`. This loses compile-time type safety and makes OpenAPI documentation generate `object` response types instead of concrete schemas. | High | Small |
| AP-2 | **No API versioning strategy** | While `/api/v1/` is used, there is no mechanism for version negotiation (header-based, content-type, etc.) and no documented versioning policy. | Low | Small |
| AP-3 | **Inconsistent response wrapper** | Successful operations return raw DTOs while errors return `ErrorResponse`. No standardized response envelope (e.g., `{ "data": ..., "error": null }`) for uniform client parsing. | Medium | Medium |
| AP-4 | **No filtering or search on list endpoints** | List endpoints only support pagination (page/size/sort). No filtering by date range, status, account number, or amount range. | Medium | Medium |
| AP-5 | **PATCH endpoint for user approval is not idempotent** | `PATCH /api/v1/bank-users/update/{id}` updates the Keycloak user on every call, even if the status is already `APPROVED`. | Low | Small |
| AP-6 | **No HATEOAS or hypermedia links** | Responses contain no navigational links (e.g., link to the account details from a transfer response). Clients must hardcode URL patterns. | Low | Medium |
| AP-7 | **Swagger dependency mismatch** | Services include `springdoc-openapi-starter-webflux-ui` but User Service, Fund Transfer, and Core Banking are Spring MVC (not WebFlux) applications. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. Only the API Gateway (which uses WebFlux) should use the webflux variant. | High | Small |
| AP-8 | **No consistent use of HTTP status codes for creation** | `POST /register` returns 200 instead of 201 (Created). Fund transfer and payment endpoints also return 200 instead of 201 or 202 (Accepted). | Medium | Small |

---

## 6. Observability

### What's Good

- Distributed tracing configured with Micrometer + Brave + Zipkin across all services
- Spring Boot Actuator enabled in all services
- `git.properties` generated via Gradle plugin for `/actuator/info`
- SLF4J logging via Lombok's `@Slf4j`

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| OB-1 | **Logging of sensitive data** | `FundTransferRequest.toString()` and `UtilityPaymentRequest.toString()` are logged, which includes account numbers and amounts. User registration logs `request.toString()` which includes the password. | Critical | Small |
| OB-2 | **No structured logging (JSON)** | Logs use default Spring Boot text format. No JSON log encoder (e.g., Logstash Logback Encoder) configured for machine-parseable log aggregation. | Medium | Small |
| OB-3 | **No health check customization** | Services rely on the default `/actuator/health` endpoint. No custom health indicators for database connectivity, Keycloak availability, or Feign client reachability. | Medium | Small |
| OB-4 | **No Prometheus metrics endpoint** | README lists Prometheus as a technology but no `micrometer-registry-prometheus` dependency exists in any `build.gradle`. The `/actuator/prometheus` endpoint is not available. | High | Small |
| OB-5 | **No alerting or monitoring configuration** | No Prometheus scrape configs, Grafana dashboards, or alerting rules. No documentation on what to monitor. | Medium | Medium |
| OB-6 | **No correlation ID in logs** | While Zipkin trace IDs exist in the tracing context, they are not consistently included in log output (no MDC configuration for `traceId`/`spanId` in log patterns). | Medium | Small |
| OB-7 | **No request/response logging at Gateway** | The API Gateway has no access logging. There is no record of which endpoints are called, by whom, response times, or status codes. | Medium | Small |

---

## 7. Resilience

### What's Good

- `wait-for-it.sh` scripts ensure infrastructure dependencies are ready before application startup
- Docker Compose uses a fixed-IP bridge network for predictable service discovery

### Gaps

| ID | Gap | Description | Severity | Effort |
|----|-----|-------------|----------|--------|
| RE-1 | **No circuit breakers** | No Resilience4j or Hystrix circuit breakers on any Feign client or service call. If Core Banking goes down, all upstream services will block indefinitely on HTTP calls, cascading the failure. | Critical | Medium |
| RE-2 | **No retry policies** | No Spring Retry or Resilience4j retry configuration. Transient network errors between services cause immediate failures. | High | Small |
| RE-3 | **No timeout configuration** | No explicit HTTP client timeouts on Feign clients. Default infinite/very-long timeouts mean a slow Core Banking response will tie up threads in upstream services indefinitely. | Critical | Small |
| RE-4 | **No fallback behavior** | No `@FeignClient` fallback classes defined. When Core Banking is unavailable, Fund Transfer and Utility Payment services throw unhandled exceptions with no graceful degradation. | High | Medium |
| RE-5 | **No database connection pool tuning** | No HikariCP pool size configuration. Default settings (max 10 connections) may be insufficient under load or may leak connections if services stall on Feign calls. | Medium | Small |
| RE-6 | **No idempotency keys for financial operations** | Fund transfer and utility payment endpoints have no idempotency mechanism. If a client retries a failed request, the operation may execute twice, causing double-debits. | Critical | Medium |
| RE-7 | **Non-atomic balance updates** | `TransactionService.internalFundTransfer()` performs separate reads, balance calculations, and saves without pessimistic locking. Concurrent transfers from the same account can cause race conditions and incorrect balances. | Critical | Medium |
| RE-8 | **Utility payment double-debit bug** | In `TransactionService.utilPayment()`, `availableBalance` is set to `actualBalance - amount` AFTER `actualBalance` has already been reduced. This double-subtracts the amount from `availableBalance`. | Critical | Small |
| RE-9 | **No health-check-based container restart** | Docker Compose services have no `healthcheck` definitions. Containers that start but fail to serve traffic are not automatically restarted. | Medium | Small |
| RE-10 | **No graceful shutdown configuration** | No `server.shutdown: graceful` or drain period configured. In-flight requests may be dropped during deployments. | Medium | Small |

---

## 8. Summary Matrix

### By Severity

| Severity | Count | IDs |
|----------|-------|-----|
| **Critical** | 9 | EH-1, TE-1, SE-1, SE-2, OB-1, RE-1, RE-3, RE-6, RE-7, RE-8 |
| **High** | 11 | CO-1, EH-2, EH-3, TE-2, TE-3, SE-3, SE-4, SE-9, AP-1, AP-7, OB-4, RE-2, RE-4 |
| **Medium** | 19 | CO-2, CO-4, CO-5, EH-4, EH-5, EH-6, TE-4, TE-5, TE-6, SE-5, SE-6, SE-7, SE-8, AP-3, AP-4, AP-8, OB-2, OB-3, OB-5, OB-6, OB-7, RE-5, RE-9, RE-10 |
| **Low** | 6 | CO-3, CO-6, TE-7, AP-2, AP-5, AP-6 |

### By Effort

| Effort | Count | IDs |
|--------|-------|-----|
| **Small** | 25 | CO-2, CO-3, CO-5, CO-6, EH-1, EH-2, EH-5, EH-6, TE-6, TE-7, SE-5, SE-6, SE-7, SE-8, AP-1, AP-2, AP-5, AP-7, AP-8, OB-1, OB-2, OB-3, OB-6, OB-7, RE-2, RE-3, RE-5, RE-8, RE-9, RE-10 |
| **Medium** | 15 | CO-1, CO-4, EH-3, EH-4, TE-4, TE-5, SE-1, SE-2, SE-4, SE-9, AP-3, AP-4, OB-5, RE-1, RE-4, RE-6, RE-7 |
| **Large** | 5 | TE-1, TE-2, TE-3, SE-3 |

### Critical Items Requiring Immediate Attention

1. **RE-8**: Utility payment double-debit bug — actively produces incorrect balances
2. **RE-7**: Race condition on concurrent balance updates — data corruption risk
3. **RE-6**: No idempotency on financial operations — double-processing risk
4. **SE-1**: No input validation — any malformed data accepted
5. **RE-1 / RE-3**: No circuit breakers or timeouts — cascade failure risk
6. **EH-1**: Exception details leaked to clients — information disclosure
7. **OB-1**: Passwords and account data logged in plain text
8. **SE-2**: Hardcoded credentials in version control
9. **TE-1**: 5 of 6 services have zero test coverage
