# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against engineering best practices and documents gaps across seven categories. Each gap is rated by severity and estimated remediation effort.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (> 3 days)

---

## 1. Code Organization

### Current State
- Six independent Gradle projects, each with its own `build.gradle` — no parent/multi-project build
- All services use the same base package (`com.javatodev.finance`) and a consistent internal structure: `controller/`, `service/`, `model/`, `exception/`, `configuration/`
- No shared library; DTOs, exception classes, mappers, and configuration filters are copy-pasted across services

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| CO-1 | **No shared library for common code** | High | Large | `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `BaseMapper`, `AuditAware`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` are duplicated across 3-4 services with minor variations. This leads to inconsistency and maintenance burden. |
| CO-2 | **No Gradle multi-project build** | Medium | Medium | Each service is a standalone Gradle project with duplicated plugin versions (`3.2.4`, `1.1.4`, `2023.0.0`). Version bumps require editing 6+ `build.gradle` files. A root `settings.gradle` with version catalogs would centralize dependency management. |
| CO-3 | **Inconsistent package structure across services** | Low | Small | Most services follow `controller/service/model/exception/configuration`, but there are inconsistencies: `repository` is at the top level in core-banking and utility-payment, but under `model.repository` in user-service and fund-transfer. |
| CO-4 | **Mapper instantiation not Spring-managed** | Low | Small | Mappers (e.g., `UserMapper`, `FundTransferMapper`) are instantiated with `new` in service classes rather than being Spring beans. This prevents injection and makes testing harder. |

---

## 2. Error Handling

### Current State
- Each service has a `GlobalExceptionHandler` using `@ControllerAdvice`
- Custom exception hierarchy: `SimpleBankingGlobalException` → specific exceptions (`EntityNotFoundException`, `InsufficientFundsException`, etc.)
- `ErrorResponse` DTO with `code` and `message` fields

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| EH-1 | **Catch-all handler returns 400 for all exceptions** | Critical | Small | `handleException(Exception e)` returns `400 Bad Request` for ALL unhandled exceptions, including 500-class errors (NPEs, database failures, etc.). This masks server errors and violates HTTP semantics. |
| EH-2 | **Exception details leaked to client** | Critical | Small | The catch-all handler returns `"Exception occur inside API " + e`, which exposes internal stack traces, class names, and potentially sensitive information to clients. |
| EH-3 | **Inconsistent ErrorResponse construction** | Medium | Small | User service uses `ErrorResponse.builder()`, fund-transfer uses `new ErrorResponse(code, msg)`, and the catch-all returns a plain string. No consistent error envelope across the API. |
| EH-4 | **No error codes for common failure scenarios** | Medium | Small | Only `SimpleBankingGlobalException` subclasses carry error codes. Generic exceptions, validation failures, and Feign errors produce unstructured error responses. |
| EH-5 | **No Feign error decoder in most services** | High | Medium | Only the user-service has a `CustomFeignErrorDecoder`. Fund-transfer and utility-payment services use default Feign error handling, which wraps downstream errors in `FeignException` and surfaces raw HTTP error bodies. |
| EH-6 | **Missing HTTP status code differentiation** | High | Small | All handled exceptions return `400 Bad Request`. There is no differentiation between 404 (not found), 409 (conflict/duplicate), 422 (validation), or 500 (server error). `EntityNotFoundException` should return 404, `InsufficientFundsException` should return 422 or 409. |
| EH-7 | **No @Transactional rollback on fund transfer failure** | High | Medium | `FundTransferService.fundTransfer()` saves a PENDING record, calls core-banking, then updates to SUCCESS. If the core-banking call fails, the record remains PENDING with no rollback or compensation logic. The `@Transactional` annotation is missing from the fund-transfer and utility-payment services. |

---

## 3. Testing

### Current State
- **core-banking-service**: 3 test classes with meaningful unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) using Mockito
- **All other services**: Only auto-generated `*ApplicationTests.java` (Spring context load tests)
- Test dependencies: JUnit 5, H2 in-memory database, Spring Boot Test

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| TE-1 | **No unit tests for 4 of 6 services** | Critical | Large | User service, fund-transfer service, utility-payment service, API gateway, config server, and service registry have zero business logic tests — only auto-generated context load tests. |
| TE-2 | **No integration tests** | Critical | Large | No tests that verify actual HTTP endpoints, database interactions, or Spring context wiring with realistic configurations. No `@SpringBootTest` with `@AutoConfigureMockMvc` or `WebTestClient`. |
| TE-3 | **No contract tests between services** | High | Large | Services communicate via Feign clients, but there are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact). API changes in core-banking-service could silently break downstream consumers. |
| TE-4 | **No test coverage reporting** | Medium | Small | No JaCoCo or equivalent coverage plugin configured. Impossible to track coverage metrics or enforce thresholds. |
| TE-5 | **Test data management** | Medium | Medium | The `V1.0.20210427174721__temp_data.sql` migration embeds test/seed data in production Flyway migrations. Test data should be separate from schema migrations. |
| TE-6 | **No end-to-end tests** | High | Large | No automated tests that exercise the full request flow through the API gateway to downstream services. Manual testing via Postman collection is the only verification method. |

---

## 4. Security

### Current State
- API Gateway enforces OAuth2 JWT validation via Keycloak
- Gateway injects `X-Auth-Id` header for downstream identity propagation
- CSRF disabled at gateway level
- Registration endpoint is publicly accessible

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| SE-1 | **Hardcoded credentials in Docker Compose and SQL** | Critical | Small | MySQL root password (`woVERANKliGharym`), application DB password (`oPItyPticIAt`), Keycloak admin password (`password`), and Keycloak DB password (`password`) are all hardcoded in `docker-compose.yml` and `privileges.sql`. |
| SE-2 | **No authentication on downstream services** | Critical | Medium | Individual services (user, fund-transfer, utility-payment, core-banking) have NO authentication or authorization. Any network-reachable client can call them directly, bypassing the API gateway's JWT validation. |
| SE-3 | **No input validation** | Critical | Medium | No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, or any Bean Validation annotations on request DTOs. The `UserController.createUser()` accepts arbitrary `@RequestBody` without validation. Fund transfer amounts are not validated for positivity. |
| SE-4 | **X-Auth-Id header not validated downstream** | High | Medium | The `AppAuthUserFilter` reads `X-Auth-Id` from the header but does not validate it against any trusted source. A direct call to a downstream service can spoof any identity. |
| SE-5 | **Keycloak singleton not thread-safe** | Medium | Small | `KeycloakProperties.getInstance()` uses a non-synchronized lazy initialization pattern (`if (keycloakInstance == null)`) with a static field, which is not thread-safe and could create multiple instances. |
| SE-6 | **No rate limiting** | Medium | Medium | No rate limiting at the API gateway or service level. The system is vulnerable to abuse and denial-of-service attacks. |
| SE-7 | **No dependency vulnerability scanning** | Medium | Small | No OWASP Dependency Check, Snyk, or equivalent plugin configured in Gradle builds. No automated detection of known CVEs in dependencies. |
| SE-8 | **CSRF disabled without documentation** | Low | Small | CSRF is disabled at the gateway (`csrfSpec::disable`). While common for stateless APIs, this should be documented with a rationale. |
| SE-9 | **Test credentials in README** | Medium | Small | Production-like credentials (`ib_admin@javatodev.com / 5V7huE3G86uB`) are published in the README. |

---

## 5. API Design

### Current State
- RESTful endpoints with consistent `/api/v1/` prefix
- OpenAPI/Swagger annotations on controllers (`@Tag`, `@Operation`)
- Pagination support via Spring Data `Pageable`
- Postman collection available for manual testing

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| AD-1 | **Raw ResponseEntity without type parameters** | High | Small | Controllers return raw `ResponseEntity` without generic type (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This breaks OpenAPI schema generation — Swagger UI cannot display response models. |
| AD-2 | **No API versioning strategy** | Medium | Medium | While `/api/v1/` is used, there is no documented versioning strategy, no version negotiation, and no plan for v2 evolution. |
| AD-3 | **Inconsistent resource naming** | Medium | Small | Mixed naming conventions: `/bank-users/register` (hyphenated), `/fund-transfer` (hyphenated), `/util-payment` (abbreviated). Some endpoints use verbs (`/register`) instead of nouns. |
| AD-4 | **No filtering support** | Medium | Medium | List endpoints accept `Pageable` for pagination but provide no filtering or sorting parameters (e.g., filter transfers by status, date range, account). |
| AD-5 | **Missing response wrapper / HATEOAS** | Low | Medium | No standard response envelope (e.g., `{ data: ..., meta: { page, size, total } }`). Pagination metadata is lost — only the content list is returned from `Page.getContent()`. |
| AD-6 | **POST /register returns 200 instead of 201** | Low | Small | User registration returns `200 OK` instead of `201 Created` with a `Location` header, which is the RESTful convention for resource creation. |
| AD-7 | **No OpenAPI spec file generated** | Medium | Small | While SpringDoc annotations exist, there is no Gradle task to generate an `openapi.json`/`openapi.yaml` file at build time for consumers. |
| AD-8 | **Inconsistent Swagger dependency** | Low | Small | `springdoc-openapi-starter-webflux-ui` is used in WebMVC services (core-banking, user, fund-transfer, utility-payment). These services are Servlet-based, not WebFlux — they should use `springdoc-openapi-starter-webmvc-ui`. |

---

## 6. Observability

### Current State
- Spring Boot Actuator included in all services
- Micrometer Tracing with Brave bridge for distributed tracing to Zipkin
- Feign Micrometer instrumentation for tracing inter-service calls
- SLF4J/Logback logging with `@Slf4j` annotation

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| OB-1 | **No structured logging** | High | Medium | Logs use plain text format (`log.info("Creating user with {}", ...)`) with no JSON structure. In a containerized environment, structured (JSON) logging is essential for log aggregation (ELK, CloudWatch, etc.). |
| OB-2 | **Sensitive data in logs** | Critical | Small | `log.info("Creating user with {}", request.toString())` and similar patterns log entire request objects, which may include passwords, identification numbers, and other PII. User service logs the full `User` object including password field. |
| OB-3 | **No custom health checks** | Medium | Small | Services rely on default Actuator `/health` endpoint. No custom health indicators for critical dependencies (MySQL connectivity, Keycloak reachability, config server availability). |
| OB-4 | **No Prometheus metrics endpoint** | Medium | Small | Despite README mentioning Prometheus, no `micrometer-registry-prometheus` dependency exists. The Actuator `/metrics` endpoint exposes Micrometer metrics but not in Prometheus exposition format. |
| OB-5 | **Inconsistent logging levels and patterns** | Medium | Small | No standardized log format or correlation ID propagation beyond Zipkin trace IDs. No `logback-spring.xml` configuration files — all services use Spring Boot defaults. |
| OB-6 | **No alerting or monitoring configuration** | Low | Large | No Grafana dashboards, alert rules, or runbooks. Zipkin provides trace visualization but no automated alerting. |
| OB-7 | **Actuator endpoints exposed without security** | High | Small | All actuator endpoints are permitted without authentication in the gateway security config (`pathMatchers("/actuator/**").permitAll()`). This exposes environment variables, heap dumps, and configuration details. |

---

## 7. Resilience

### Current State
- `wait-for-it.sh` script for Docker container startup ordering
- `@Transactional` annotation on core-banking-service `TransactionService`
- Basic exception handling via `@ControllerAdvice`

### Gaps

| ID | Gap | Severity | Effort | Details |
|---|---|---|---|---|
| RE-1 | **No circuit breakers** | Critical | Medium | No Resilience4j or Hystrix circuit breakers on any Feign client. If core-banking-service goes down, all dependent services will block on failed HTTP calls until socket timeout, cascading failure throughout the system. |
| RE-2 | **No retry policies** | High | Small | No Spring Retry or Resilience4j retry configuration on Feign clients. Transient network failures immediately fail requests with no retry attempt. |
| RE-3 | **No timeout configuration** | High | Small | No explicit HTTP client timeouts configured on Feign clients. Default timeouts are extremely long, meaning a hung downstream service will block callers indefinitely. |
| RE-4 | **No fallback behavior** | High | Medium | No fallback methods defined for Feign clients. When a downstream service is unavailable, users receive raw error responses rather than graceful degradation messages. |
| RE-5 | **No idempotency keys** | High | Medium | Fund transfer and utility payment endpoints have no idempotency mechanism. Network retries or client retries could result in duplicate transactions. |
| RE-6 | **No bulkhead isolation** | Medium | Medium | No thread pool or semaphore bulkheads. A slow downstream service consumes all available threads in the calling service, affecting unrelated endpoints. |
| RE-7 | **Transaction consistency gaps** | Critical | Medium | Fund transfer and utility payment services save a PENDING/PROCESSING record locally, then call core-banking synchronously. If the core-banking call fails mid-transaction, local records are left in an inconsistent state. No saga pattern, no compensation logic, no dead letter handling. |
| RE-8 | **Single point of failure: Config Server** | High | Medium | All services depend on the Config Server at startup. If Config Server is unavailable, no service can start. No `fail-fast: false` configuration or local config fallback. |
| RE-9 | **No graceful shutdown** | Low | Small | No `server.shutdown=graceful` configuration. Services may drop in-flight requests during deployment. |
| RE-10 | **No health check dependencies in Docker Compose** | Medium | Small | Docker Compose uses `wait-for-it.sh` for port checks but no actual health checks (`healthcheck` directive). A service may have an open port before it is fully initialized. |

---

## Summary Dashboard

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 1 | 1 | 2 | 4 |
| Error Handling | 2 | 3 | 2 | 0 | 7 |
| Testing | 2 | 2 | 2 | 0 | 6 |
| Security | 3 | 1 | 3 | 2 | 9 |
| API Design | 0 | 1 | 3 | 3 | 8* |
| Observability | 1 | 2 | 3 | 1 | 7 |
| Resilience | 2 | 4 | 2 | 1 | 10* |
| **Total** | **10** | **14** | **16** | **9** | **51*** |

*Note: Some items overlap categories (e.g., EH-7 relates to both error handling and resilience).
