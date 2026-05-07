# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against industry engineering best practices. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Remediation Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Finding** | Each of the 6 microservices has its own independent `build.gradle` and Gradle wrapper. There is no root `settings.gradle` or `build.gradle` to unify them. This means dependency versions, plugin versions, and common configurations are duplicated across all services. |
| **Impact** | Version drift between services, duplicated maintenance effort, inability to run a single `./gradlew build` from the root. |
| **Best Practice** | Use a Gradle multi-project build with a root `build.gradle.kts` that defines shared configurations via `subprojects {}` or convention plugins. |

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Finding** | Significant code duplication exists across services: `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `AuditAware`, `BaseMapper`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `CustomFeignClientConfiguration`, audit configuration classes, and `TransactionStatus` are **copy-pasted** across fund-transfer, utility-payment, and user services. |
| **Impact** | Bug fixes or changes must be applied to 3-4 copies. Inconsistencies already exist (e.g., the user service has `CustomFeignErrorDecoder` while others don't). |
| **Best Practice** | Extract shared code into a common library module (e.g., `internet-banking-common`). |

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Finding** | Package naming varies: fund-transfer uses `model.repository` while utility-payment uses `repository` (top-level). Fund-transfer uses `service.rest.client` while utility-payment uses `service.rest`. DTO request/response classes use `model.dto.request` vs `model.rest.request`. |
| **Impact** | Harder to navigate and maintain. New developers must learn different conventions per service. |
| **Best Practice** | Standardize on a single package convention across all services. |

### 1.4 Mapper Classes Instantiated as Instance Fields (Not Beans)

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Finding** | All services instantiate mapper classes as plain fields (`private UserMapper userMapper = new UserMapper()`) rather than as Spring beans. This bypasses Spring's lifecycle management and makes testing harder. |
| **Impact** | Cannot mock mappers in tests, no Spring AOP support, inconsistent with the rest of the Spring-managed codebase. |
| **Best Practice** | Register mappers as `@Component` beans or use MapStruct with Spring integration. |

---

## 2. Error Handling

### 2.1 Generic Exception Handler Returns HTTP 400 for All Errors

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Finding** | The `GlobalExceptionHandler` in all services catches `Exception.class` and returns `400 Bad Request` for every unhandled exception, including 500-level server errors, NPEs, database failures, and Feign communication errors. The response body is a raw string: `"Exception occur inside API " + e`. |
| **Impact** | Clients cannot distinguish between client errors and server errors. Stack traces may leak to the client. Monitoring tools cannot properly categorize failures. |
| **Best Practice** | Return appropriate HTTP status codes (404 for not found, 500 for internal errors, 502 for downstream failures). Never expose exception details to clients. |

### 2.2 Inconsistent Error Response Format

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | Known business exceptions return `ErrorResponse { code, message }`. Unknown exceptions return a raw string. There is no standard envelope (e.g., RFC 7807 Problem Details). The `ErrorResponse` class is duplicated in each service with the same structure but different packages. |
| **Impact** | API consumers must handle two different response shapes for errors. |
| **Best Practice** | Adopt RFC 7807 Problem Details (`application/problem+json`) with a standardized error response across all services. |

### 2.3 Missing Feign Error Decoder in Most Services

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | Only the user service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services use Feign's default error decoder, which throws a generic `FeignException` on non-2xx responses. This means a 404 from core-banking will bubble up as a 500 in the calling service. |
| **Impact** | Poor error propagation between services. Callers cannot react to specific downstream failures. |
| **Best Practice** | Implement a custom `ErrorDecoder` for all Feign clients to translate downstream errors into appropriate domain exceptions. |

### 2.4 No Validation Annotations on Request DTOs

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Finding** | None of the request DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `User`) have any Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.). Controllers do not use `@Valid` or `@Validated`. |
| **Impact** | Null or invalid data flows through to the service layer and database, causing cryptic errors. Negative amounts, blank account numbers, and null emails are all accepted. |
| **Best Practice** | Add Bean Validation annotations to all request DTOs and `@Valid` to controller parameters. |

---

## 3. Testing

### 3.1 Minimal Test Coverage

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |
| **Finding** | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `UserServiceTest`, `TransactionServiceTest`). The remaining 5 services only have empty Spring Boot context load tests (`@SpringBootTest` classes with no test methods). |
| **Impact** | No safety net for regressions. Refactoring is high-risk. Business logic in user registration, fund transfer orchestration, and utility payment orchestration is untested. |
| **Best Practice** | Aim for >80% coverage of service and controller layers. |

### 3.2 No Integration Tests

| | |
|---|---|
| **Severity** | High |
| **Effort** | Large |
| **Finding** | No integration tests exist for any service. There are no tests that verify database interactions, Feign client behavior, or end-to-end request handling with `@SpringBootTest` + `MockMvc`. |
| **Impact** | Configuration issues, serialization problems, and wiring errors will only be caught in production. |
| **Best Practice** | Add `@SpringBootTest` integration tests with `TestContainers` for MySQL and WireMock for Feign clients. |

### 3.3 No Contract Tests

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |
| **Finding** | No consumer-driven contract tests (e.g., Spring Cloud Contract or Pact) exist between services. The Feign client interfaces and the corresponding controller endpoints could drift apart without detection. |
| **Impact** | Breaking changes in core-banking-service APIs will silently break downstream services. |
| **Best Practice** | Implement Spring Cloud Contract stubs for core-banking-service and verify consumers against them. |

### 3.4 Context Load Tests May Fail

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | The empty `@SpringBootTest` test classes in non-core services will fail to load because they depend on external services (Eureka, Config Server, Keycloak, MySQL) that aren't available in the test environment. Test application.yml files exist for some services but may not fully mock external dependencies. |
| **Impact** | Running `./gradlew test` will likely fail for most services without infrastructure. |
| **Best Practice** | Either provide proper test profiles that disable Eureka/Config/Feign or remove the broken context load tests. |

---

## 4. Security

### 4.1 No Input Validation

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Finding** | As noted in 2.4, no input validation exists. This is also a security concern: unvalidated input can lead to injection attacks, negative-amount transfers (money creation), and data corruption. |
| **Impact** | Critical financial security risk. A user could transfer negative amounts, use SQL-injectable strings in account numbers, or submit XSS payloads. |
| **Best Practice** | Bean Validation on all request DTOs + server-side validation in service layer for business rules. |

### 4.2 No Role-Based Access Control

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Finding** | The API Gateway authenticates users via JWT but performs **no authorization**. Any authenticated user can: approve other users (`PATCH /update/{id}`), view all users' data, transfer funds from any account, and make payments from any account. |
| **Impact** | Complete lack of authorization in a financial application. Any authenticated user has admin-level access. |
| **Best Practice** | Implement role-based access control (e.g., `ADMIN`, `USER` roles in Keycloak). Enforce ownership checks (users can only operate on their own accounts). |

### 4.3 Hardcoded Credentials in Docker Compose

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | Database passwords (`MYSQL_ROOT_PASSWORD: woVERANKliGharym`), Keycloak admin credentials (`admin/password`), and PostgreSQL credentials (`keycloak/password`) are hardcoded in `docker-compose.yml`. Test credentials are in the README. |
| **Impact** | Acceptable for local development but dangerous if deployed as-is. Credentials should never be in version control for production use. |
| **Best Practice** | Use `.env` files (git-ignored) or Docker secrets. Document the requirement for environment-specific credential injection. |

### 4.4 CSRF Disabled Without Documentation

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | `SecurityConfiguration` disables CSRF (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`) without any comment explaining why. While this is appropriate for a stateless JWT API, it should be documented. |
| **Impact** | Low risk for API-only services, but a code reviewer might not understand the rationale. |
| **Best Practice** | Add a comment explaining CSRF is disabled because the API is stateless and uses bearer tokens. |

### 4.5 Keycloak Singleton Not Thread-Safe

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | `KeycloakProperties.getInstance()` uses a non-thread-safe singleton pattern (`if (keycloakInstance == null)`) with a static field. Multiple threads could create multiple instances or see a partially-constructed `Keycloak` object during startup. |
| **Impact** | Race condition during first concurrent requests. Could lead to authentication failures or resource leaks. |
| **Best Practice** | Use a Spring `@Bean` method in a `@Configuration` class (guaranteed singleton by the container) or `synchronized` initialization. |

### 4.6 Password Handling in User DTO

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | The `User` DTO contains a `password` field that is used for both request and response. Since the same `User` class is returned from `createUser()` and `readUser()`, the password could be serialized in API responses. |
| **Impact** | Potential password exposure in API responses. |
| **Best Practice** | Use separate request/response DTOs. Mark password as `@JsonProperty(access = WRITE_ONLY)` or use `@JsonIgnore` on the getter. |

### 4.7 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | No dependency vulnerability scanning tool (e.g., OWASP Dependency-Check, Snyk, Dependabot) is configured. Dependencies like `commons-lang` (used in `AppAuthUserFilter`) may be the legacy Apache Commons Lang 2.x. |
| **Impact** | Known CVEs in transitive dependencies could go undetected. |
| **Best Practice** | Add the OWASP Dependency-Check Gradle plugin or enable GitHub Dependabot. |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generic Types

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | Most controller methods return raw `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<FundTransferResponse>`). This suppresses compile-time type checking and makes OpenAPI documentation incomplete. |
| **Impact** | Generated API docs won't show response schemas. IDE autocomplete is degraded. |
| **Best Practice** | Always use typed `ResponseEntity<T>` in controller method signatures. |

### 5.2 No Pagination Metadata in Responses

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | List endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>`. Clients receive the data but no pagination metadata (total elements, total pages, current page, etc.). |
| **Impact** | Clients cannot implement proper pagination UIs or know when they've reached the last page. |
| **Best Practice** | Return `Page<T>` or a wrapper with pagination metadata (totalElements, totalPages, pageNumber, pageSize). |

### 5.3 No API Versioning Strategy

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Finding** | While the URL path includes `/api/v1/`, there is no documented versioning strategy, no v2 evolution plan, and no content negotiation support. |
| **Impact** | Low risk currently, but breaking changes will be difficult to roll out. |
| **Best Practice** | Document the versioning strategy (URL path vs header-based). Plan for backward compatibility. |

### 5.4 Non-RESTful URL Patterns

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Finding** | Some endpoints violate REST conventions: `POST /register` and `PATCH /update/{id}` include verbs in the URL. The RESTful approach would be `POST /bank-users` and `PATCH /bank-users/{id}`. |
| **Impact** | Inconsistent API style, harder for consumers to predict endpoint patterns. |
| **Best Practice** | Use noun-based URLs. Operations should be expressed through HTTP methods, not URL verbs. |

### 5.5 OpenAPI Documentation Incomplete

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | The `springdoc-openapi` dependency is included but uses the **WebFlux** starter (`springdoc-openapi-starter-webflux-ui`) in WebMVC services (user, fund-transfer, utility-payment, core-banking). This is incorrect — WebMVC services should use `springdoc-openapi-starter-webmvc-ui`. Additionally, request/response schemas won't be auto-documented due to missing generic types on `ResponseEntity`. |
| **Impact** | Swagger UI may not work correctly. API documentation is unreliable. |
| **Best Practice** | Use the correct `springdoc-openapi-starter-webmvc-ui` artifact for WebMVC services. Add `@Schema` annotations to DTOs. |

---

## 6. Observability

### 6.1 Inconsistent Logging

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | Log messages use string concatenation in some places (`"Sending fund transfer request {}" + request.toString()`) and proper SLF4J placeholders in others (`"Utility payment processing {}", paymentRequest.toString()`). The `toString()` calls on request objects may log sensitive data (account numbers, amounts). No structured logging (JSON) is configured. |
| **Impact** | Inefficient logging (string concatenation happens regardless of log level). Potential data leakage in logs. Logs are hard to parse in aggregation tools. |
| **Best Practice** | Use SLF4J placeholders consistently. Implement structured JSON logging (Logback/Logstash encoder). Mask sensitive fields. |

### 6.2 No Health Check Configuration

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | While `spring-boot-starter-actuator` is included in all services, there is no custom health indicator configuration. Services don't check database connectivity, Keycloak reachability, or downstream service availability in their health endpoints. Docker Compose has no `healthcheck` directives. |
| **Impact** | The `/actuator/health` endpoint may report `UP` even when critical dependencies are unavailable. Docker orchestrators cannot properly manage container lifecycle. |
| **Best Practice** | Configure health indicators for databases, Keycloak, and Feign clients. Add `healthcheck` to Docker Compose. |

### 6.3 No Metrics Endpoints

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Finding** | Prometheus is listed in the technology stack but there is no `micrometer-registry-prometheus` dependency in any service's `build.gradle`. No custom metrics (transaction counts, amounts, error rates) are instrumented. |
| **Impact** | No production monitoring capability. Cannot track business KPIs or system performance. |
| **Best Practice** | Add `micrometer-registry-prometheus` and expose `/actuator/prometheus`. Add custom counters for business events. |

### 6.4 Distributed Tracing Not Verified

| | |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Finding** | Tracing dependencies (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) are included but the sampling rate and propagation format are configured remotely (via Config Server). There is no verification that traces propagate correctly through Feign calls. |
| **Impact** | Tracing may not work end-to-end without proper configuration. |
| **Best Practice** | Verify trace propagation with integration tests. Document the expected sampling rate and configuration. |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Finding** | There are no circuit breakers (e.g., Resilience4j, Hystrix) on any Feign client or external call. If `core-banking-service` becomes slow or unresponsive, all upstream services will hang on synchronous Feign calls, cascading the failure. |
| **Impact** | A single service failure will cascade across the entire system. Thread pools in upstream services will be exhausted. |
| **Best Practice** | Add Resilience4j circuit breakers to all Feign clients with defined failure thresholds, timeout, and fallback behaviors. |

### 7.2 No Retry Policies

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | No retry configuration exists for Feign clients or any external call. Transient network errors or brief service unavailability will immediately result in failure. |
| **Impact** | Poor reliability during routine infrastructure events (deploys, restarts, network blips). |
| **Best Practice** | Configure Feign/Resilience4j retry with exponential backoff for idempotent operations. Ensure non-idempotent operations (fund transfers) are NOT retried without idempotency keys. |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Finding** | No explicit timeouts are configured for Feign clients, database connections, or Keycloak API calls. Default timeouts (if any) are unpredictable and may be infinite. |
| **Impact** | Slow downstream services will cause upstream threads to hang indefinitely. |
| **Best Practice** | Configure explicit connection and read timeouts on all Feign clients, database connection pools, and Keycloak client. |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Finding** | There is no graceful degradation when downstream services are unavailable. Feign calls will throw exceptions that propagate as 400 errors (due to the catch-all exception handler). |
| **Impact** | Users receive unhelpful error messages. No ability to queue-and-retry failed transactions. |
| **Best Practice** | Implement Feign fallback factories. For financial transactions, consider a "pending review" state for transactions that fail downstream validation. |

### 7.5 Non-Atomic Financial Transactions

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |
| **Finding** | Fund transfers and utility payments span two services (e.g., fund-transfer-service and core-banking-service) without distributed transaction management. If the Feign call succeeds but the subsequent `fundTransferRepository.save()` fails, the money is transferred in core banking but the fund-transfer service shows no record. There is no compensation/rollback mechanism. |
| **Impact** | Data inconsistency in a financial system. Money could be debited without a corresponding transfer record, or vice versa. |
| **Best Practice** | Implement the Saga pattern with compensating transactions, or use transactional outbox pattern. At minimum, add idempotency keys to prevent duplicate processing. |

### 7.6 Balance Calculation Bug

| | |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Finding** | In `TransactionService.utilPayment()`, the available balance is incorrectly calculated: `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(utilityPaymentRequest.getAmount()))`. Since `actualBalance` was already reduced in the previous line, this double-subtracts the amount. The same bug exists in `internalFundTransfer()` for both source and destination accounts. |
| **Impact** | Account balances will be incorrect after every transaction. Available balance will diverge from actual balance. |
| **Best Practice** | Fix the calculation: `setAvailableBalance(newActualBalance)` or compute both from the original balance. Add unit tests that verify balance calculations. |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | All errors return HTTP 400 | Error Handling | Critical | Small |
| 2.4 | No input validation | Error Handling | Critical | Small |
| 3.1 | Minimal test coverage | Testing | Critical | Large |
| 4.1 | No input validation (security) | Security | Critical | Small |
| 4.2 | No role-based access control | Security | Critical | Medium |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.5 | Non-atomic financial transactions | Resilience | Critical | Large |
| 7.6 | Balance calculation bug | Resilience | Critical | Small |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 2.2 | Inconsistent error response format | Error Handling | High | Small |
| 2.3 | Missing Feign error decoder | Error Handling | High | Small |
| 3.2 | No integration tests | Testing | High | Large |
| 4.3 | Hardcoded credentials | Security | High | Small |
| 4.5 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.6 | Password in response DTO | Security | High | Small |
| 7.2 | No retry policies | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context load tests may fail | Testing | Medium | Small |
| 4.4 | CSRF disabled without docs | Security | Medium | Small |
| 4.7 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity types | API Design | Medium | Small |
| 5.2 | No pagination metadata | API Design | Medium | Small |
| 5.5 | Incorrect OpenAPI dependency | API Design | Medium | Small |
| 6.1 | Inconsistent logging | Observability | Medium | Small |
| 6.2 | No health check configuration | Observability | Medium | Small |
| 6.3 | No metrics endpoints | Observability | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mappers not Spring beans | Code Organization | Low | Small |
| 5.3 | No API versioning strategy | API Design | Low | Small |
| 5.4 | Non-RESTful URL patterns | API Design | Low | Small |
| 6.4 | Distributed tracing not verified | Observability | Low | Small |
