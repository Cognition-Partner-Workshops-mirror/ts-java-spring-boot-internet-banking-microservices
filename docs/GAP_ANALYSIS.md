# Engineering Standards Gap Analysis

This document compares the codebase against industry-standard engineering best practices and documents gaps across seven key areas. Each gap is rated by severity and estimated remediation effort.

**Severity Scale:**
- **Critical** -- Production risk; could cause data loss, security breaches, or outages
- **High** -- Significant technical debt; affects reliability, maintainability, or developer velocity
- **Medium** -- Notable deviation from best practices; manageable risk
- **Low** -- Polish item; improves quality but not urgent

**Effort Scale:**
- **Small** -- Less than 1 day; localized change
- **Medium** -- 1-3 days; touches multiple files or services
- **Large** -- 3+ days; cross-cutting concern or architectural change

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Aspect | Detail |
|---|---|
| **Gap** | Each service has its own standalone `build.gradle` with duplicated dependency versions, plugin configurations, and Spring Cloud BOM declarations. There is no root `settings.gradle` or parent build to unify them. |
| **Impact** | Version drift risk (e.g., one service could accidentally upgrade Spring Boot while others stay behind). Repetitive maintenance when updating shared versions. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Duplicated Code Across Services

| Aspect | Detail |
|---|---|
| **Gap** | Multiple classes are copy-pasted across services with minor variations: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `GlobalErrorCode`. |
| **Impact** | Bug fixes or improvements must be applied to each copy independently. Inconsistencies creep in (e.g., `ErrorResponse` uses `@Builder` in some services and constructor in others). |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure

| Aspect | Detail |
|---|---|
| **Gap** | Package naming is inconsistent: `model.repository` (user-service) vs. `repository` (core-banking, utility-payment). Feign clients live in `service.rest.client` (fund-transfer) vs. `service.rest` (user-service, utility-payment). Configuration classes are in `configuration.feign` (user-service) vs. `configuration` (fund-transfer, utility-payment). |
| **Impact** | Developer confusion; harder to navigate the codebase and locate conventions. |
| **Severity** | Low |
| **Effort** | Small |

### 1.4 Mapper Instantiation Outside DI

| Aspect | Detail |
|---|---|
| **Gap** | Mapper classes (e.g., `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are instantiated inline as `new FooMapper()` inside service classes instead of being managed as Spring beans. |
| **Impact** | Cannot benefit from dependency injection, making testing harder and preventing use of libraries like MapStruct that integrate with Spring. |
| **Severity** | Low |
| **Effort** | Small |

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400 for Everything

| Aspect | Detail |
|---|---|
| **Gap** | Every `GlobalExceptionHandler` catches `Exception.class` and returns `400 Bad Request` with a raw string `"Exception occur inside API " + e`. This catches server errors (NPEs, database failures, etc.) and mis-reports them as client errors. |
| **Impact** | Clients cannot distinguish between "bad request" and "server error". Stack traces leak in production responses. Monitoring/alerting tools cannot differentiate 4xx from 5xx. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.2 Inconsistent Error Response Format

| Aspect | Detail |
|---|---|
| **Gap** | Business exceptions return `ErrorResponse { code, message }` (structured JSON), while generic exceptions return a plain string concatenation. The `ErrorResponse` class uses `@Builder` in some services and a 2-arg constructor in fund-transfer-service. |
| **Impact** | Clients must handle two completely different error shapes. No consistent contract for error responses. |
| **Severity** | High |
| **Effort** | Small |

### 2.3 Missing HTTP Status Code Differentiation

| Aspect | Detail |
|---|---|
| **Gap** | `EntityNotFoundException` returns `400 Bad Request` instead of `404 Not Found`. `InsufficientFundsException` returns `400` instead of `422 Unprocessable Entity` or `409 Conflict`. All `SimpleBankingGlobalException` subclasses map to `400`. |
| **Impact** | RESTful clients cannot use HTTP status codes to programmatically handle different error conditions. |
| **Severity** | High |
| **Effort** | Small |

### 2.4 No Validation on Request Bodies

| Aspect | Detail |
|---|---|
| **Gap** | No `@Valid` annotations on any `@RequestBody` parameters. No Bean Validation (`jakarta.validation`) constraints on any DTO (e.g., `@NotNull`, `@NotBlank`, `@Positive` on amount). |
| **Impact** | Null or invalid data propagates to service/repository layers before being caught, producing confusing NPEs instead of clear 400 errors. |
| **Severity** | High |
| **Effort** | Small |

### 2.5 Feign Error Handling Incomplete

| Aspect | Detail |
|---|---|
| **Gap** | Only user-service has a `CustomFeignErrorDecoder`. Fund-transfer and utility-payment services configure `CustomFeignClientConfiguration` (which propagates the auth header) but have no custom error decoder. Feign 4xx/5xx errors from core-banking-service are not translated into meaningful domain exceptions. |
| **Impact** | Downstream errors surface as opaque `FeignException` to callers instead of structured business errors. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 3. Testing

### 3.1 No Tests in Most Services

| Aspect | Detail |
|---|---|
| **Gap** | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other services have only empty `ApplicationTests` stubs that verify the Spring context loads (and these would fail without infrastructure anyway). |
| **Impact** | No regression protection for user registration, fund transfer orchestration, or utility payment orchestration logic. |
| **Severity** | Critical |
| **Effort** | Large |

### 3.2 No Integration Tests

| Aspect | Detail |
|---|---|
| **Gap** | No `@SpringBootTest` or `@WebMvcTest` integration tests exist. No controller-layer tests verifying request/response serialization, status codes, or authentication. |
| **Impact** | Cannot verify end-to-end request handling, JSON binding, or security configuration. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests Between Services

| Aspect | Detail |
|---|---|
| **Gap** | No Spring Cloud Contract or Pact tests between the Feign clients and their providers (core-banking-service). API compatibility is only verified by manual testing. |
| **Impact** | Breaking changes in core-banking-service API could silently break downstream services without detection. |
| **Severity** | Medium |
| **Effort** | Large |

### 3.4 Test Context Loads Would Fail Without Infrastructure

| Aspect | Detail |
|---|---|
| **Gap** | `InternetBankingUserServiceApplicationTests`, `InternetBankingFundTransferServiceApplicationTests`, and `InternetBankingUtilityPaymentServiceApplicationTests` are `@SpringBootTest` tests that require MySQL, Eureka, and Config Server to be running. No test profiles or embedded alternatives are configured for these services (only core-banking-service has a test `application.yml` with H2). |
| **Impact** | CI cannot run tests for these services without external infrastructure. |
| **Severity** | High |
| **Effort** | Medium |

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Aspect | Detail |
|---|---|
| **Gap** | Database passwords (`woVERANKliGharym`, `oPItyPticIAt`, `password`), Keycloak admin credentials (`admin`/`password`), and test user credentials are committed in `docker-compose.yml`, `privileges.sql`, `README.md`, and Flyway migration scripts. |
| **Impact** | Credential exposure if the repository is made public. Bad practice even for development environments as it normalizes committing secrets. |
| **Severity** | Medium |
| **Effort** | Medium |

### 4.2 No Input Validation

| Aspect | Detail |
|---|---|
| **Gap** | No Bean Validation annotations on any request DTOs. No sanitization of user input. Transfer amounts are not validated for positivity, maximum limits, or precision. Account numbers are not validated for format. |
| **Impact** | Possible injection of invalid data (negative transfers, blank account numbers, etc.) that could corrupt the ledger. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.3 CSRF Disabled Without Justification

| Aspect | Detail |
|---|---|
| **Gap** | `SecurityConfiguration` disables CSRF globally (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is acceptable for a stateless JWT-based API, there is no documentation explaining the rationale. |
| **Impact** | Low risk since the API uses JWTs (no cookies), but the lack of documentation makes it unclear if this was a deliberate security decision. |
| **Severity** | Low |
| **Effort** | Small |

### 4.4 Keycloak Singleton Not Thread-Safe

| Aspect | Detail |
|---|---|
| **Gap** | `KeycloakProperties.getInstance()` uses a non-synchronized lazy singleton pattern (`if (keycloakInstance == null)`) with a mutable static field. This is not thread-safe in a multi-threaded application server. |
| **Impact** | Race condition could create multiple Keycloak instances, potentially causing authentication issues. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.5 No Role-Based Access Control

| Aspect | Detail |
|---|---|
| **Gap** | The gateway only checks "authenticated vs. unauthenticated". There is no role-based authorization (e.g., only admins can approve users, only account owners can transfer funds). |
| **Impact** | Any authenticated user can perform any operation, including admin functions like user approval. |
| **Severity** | High |
| **Effort** | Medium |

### 4.6 Password Transmitted in Request Body

| Aspect | Detail |
|---|---|
| **Gap** | The user registration DTO (`User`) includes a `password` field that is serialized in request/response JSON. The password is visible in logs (`log.info("Creating user with {}", request.toString())`). |
| **Impact** | Passwords may appear in log files, monitoring systems, and HTTP access logs. |
| **Severity** | High |
| **Effort** | Small |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics

| Aspect | Detail |
|---|---|
| **Gap** | Most controllers return `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`. Only the user-service controller properly uses `ResponseEntity<User>` and `ResponseEntity<List<User>>`. |
| **Impact** | No compile-time type safety. OpenAPI/Swagger documentation cannot infer response types, producing incomplete API docs. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.2 No API Versioning Strategy

| Aspect | Detail |
|---|---|
| **Gap** | While endpoints use `/api/v1/`, there is no documented versioning strategy or mechanism to evolve APIs (e.g., content negotiation, URL-based versioning plan). |
| **Impact** | Low risk currently but will become problematic as the API evolves and clients depend on specific contracts. |
| **Severity** | Low |
| **Effort** | Small |

### 5.3 Inconsistent Pagination Response

| Aspect | Detail |
|---|---|
| **Gap** | Paginated endpoints (e.g., `readUsers`, `readFundTransfers`) accept `Pageable` but return `List<T>` instead of Spring's `Page<T>`. Callers lose pagination metadata (total elements, total pages, current page). |
| **Impact** | Clients cannot implement proper pagination UI or know how many total pages exist. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.4 No OpenAPI/Swagger Fully Configured

| Aspect | Detail |
|---|---|
| **Gap** | `springdoc-openapi-starter-webflux-ui` is included in build dependencies and basic `@Operation`/`@Tag` annotations exist on controllers, but the dependency is the **WebFlux** variant being used in **servlet-based** (non-reactive) services. Only the API gateway is actually WebFlux-based. |
| **Impact** | Swagger UI may not work correctly in servlet services. The wrong starter is imported. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.5 No HATEOAS or Resource Linking

| Aspect | Detail |
|---|---|
| **Gap** | Responses are plain DTOs with no hypermedia links. No `_links` for navigating between related resources. |
| **Impact** | Low priority for an internal banking API but deviates from mature REST API standards. |
| **Severity** | Low |
| **Effort** | Medium |

### 5.6 No Rate Limiting or Throttling

| Aspect | Detail |
|---|---|
| **Gap** | The API gateway has no rate limiting configuration. No Spring Cloud Gateway `RequestRateLimiter` filter is configured. |
| **Impact** | API is vulnerable to abuse (accidental or malicious) without any throttling protection. |
| **Severity** | Medium |
| **Effort** | Medium |

---

## 6. Observability

### 6.1 Inconsistent Logging

| Aspect | Detail |
|---|---|
| **Gap** | Log levels and formats are inconsistent. Some controllers log request data (`log.info("Creating user with {}", request.toString())`) while others don't. No structured logging (JSON format). No correlation ID in log messages (despite Zipkin trace IDs being available). Sensitive data (passwords, full request objects) logged at INFO level. |
| **Impact** | Difficult to trace requests across services using logs alone. Sensitive data exposure in log files. |
| **Severity** | High |
| **Effort** | Medium |

### 6.2 No Custom Health Checks

| Aspect | Detail |
|---|---|
| **Gap** | Services include `spring-boot-starter-actuator` but rely solely on default health indicators. No custom health checks for critical dependencies (e.g., Keycloak connectivity, Feign client availability, RabbitMQ readiness). |
| **Impact** | Health endpoint reports "UP" even when critical downstream dependencies are unreachable. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Metrics Endpoints or Dashboards

| Aspect | Detail |
|---|---|
| **Gap** | While `micrometer-tracing-bridge-brave` is included, there is no Prometheus metrics exporter (`micrometer-registry-prometheus`) despite Prometheus being listed in the README's technology stack. No custom business metrics (e.g., transfer count, payment volume). |
| **Impact** | Cannot monitor business KPIs or system performance. Prometheus integration claimed but not implemented. |
| **Severity** | High |
| **Effort** | Small |

### 6.4 Incomplete Distributed Tracing Coverage

| Aspect | Detail |
|---|---|
| **Gap** | Zipkin dependencies are in place and traces should propagate through Feign calls, but there is no explicit configuration of trace sampling rate, no custom spans for business operations, and no verification that traces actually reach Zipkin. |
| **Impact** | Tracing may work at the HTTP level but misses important business context (e.g., which accounts were involved in a transfer). |
| **Severity** | Low |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Aspect | Detail |
|---|---|
| **Gap** | No circuit breaker library (Resilience4j, Spring Cloud Circuit Breaker) is included in any service. If core-banking-service goes down, all downstream services will hang or fail immediately with unhandled exceptions. |
| **Impact** | Cascading failures across the entire platform. One service failure can bring down all services that depend on it. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.2 No Retry Policies on Feign Clients

| Aspect | Detail |
|---|---|
| **Gap** | Feign clients have no retry configuration. Transient network failures (e.g., brief connection reset) immediately fail the request. |
| **Impact** | Increased error rate during minor network hiccups that would be resolved by a simple retry. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeout Configuration

| Aspect | Detail |
|---|---|
| **Gap** | No explicit timeouts configured for Feign clients, database connections, or the API gateway routes. Services rely on default JVM/library timeouts which may be very long (e.g., 30+ seconds). |
| **Impact** | Slow downstream services can cause thread pool exhaustion in callers. Users experience long waits before receiving an error. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior

| Aspect | Detail |
|---|---|
| **Gap** | No fallback methods defined for any Feign client call. When core-banking-service is unavailable, services throw unhandled exceptions. |
| **Impact** | No graceful degradation. Users see raw error messages instead of helpful responses like "Service temporarily unavailable, please try again." |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 No Idempotency Protection on Mutations

| Aspect | Detail |
|---|---|
| **Gap** | Fund transfer and utility payment endpoints have no idempotency keys. If a client retries a failed/timed-out request, the operation may be executed twice, resulting in double-debit. |
| **Impact** | Financial transactions could be duplicated, causing incorrect balances. This is critical for a banking application. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.6 Potential Balance Calculation Bug

| Aspect | Detail |
|---|---|
| **Gap** | In `TransactionService.internalFundTransfer()` and `utilPayment()`, `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been modified. This means `availableBalance` is doubly reduced. For example, if balance is 200 and transfer is 100: `actualBalance` becomes 100, then `availableBalance` = 100 - 100 = 0 (should be 100). |
| **Impact** | `availableBalance` becomes incorrect after every transaction, leading to incorrect balance reporting and premature insufficient-funds errors. |
| **Severity** | Critical |
| **Effort** | Small |

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 1.2 | Duplicated code across services | Code Organization | High | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation outside DI | Code Organization | Low | Small |
| 2.1 | Generic exception catch-all returns 400 | Error Handling | Critical | Small |
| 2.2 | Inconsistent error response format | Error Handling | High | Small |
| 2.3 | Missing HTTP status code differentiation | Error Handling | High | Small |
| 2.4 | No validation on request bodies | Error Handling | High | Small |
| 2.5 | Feign error handling incomplete | Error Handling | Medium | Small |
| 3.1 | No tests in most services | Testing | Critical | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 3.3 | No contract tests between services | Testing | Medium | Large |
| 3.4 | Test context loads fail without infra | Testing | High | Medium |
| 4.1 | Hardcoded credentials in source | Security | Medium | Medium |
| 4.2 | No input validation | Security | Critical | Small |
| 4.3 | CSRF disabled without justification | Security | Low | Small |
| 4.4 | Keycloak singleton not thread-safe | Security | Medium | Small |
| 4.5 | No role-based access control | Security | High | Medium |
| 4.6 | Password in request body / logs | Security | High | Small |
| 5.1 | Raw ResponseEntity without generics | API Design | Medium | Small |
| 5.2 | No API versioning strategy | API Design | Low | Small |
| 5.3 | Inconsistent pagination response | API Design | Medium | Small |
| 5.4 | Wrong OpenAPI dependency | API Design | Medium | Small |
| 5.5 | No HATEOAS or resource linking | API Design | Low | Medium |
| 5.6 | No rate limiting | API Design | Medium | Medium |
| 6.1 | Inconsistent / insecure logging | Observability | High | Medium |
| 6.2 | No custom health checks | Observability | Medium | Small |
| 6.3 | No metrics / Prometheus setup | Observability | High | Small |
| 6.4 | Incomplete distributed tracing | Observability | Low | Small |
| 7.1 | No circuit breakers | Resilience | Critical | Medium |
| 7.2 | No retry policies on Feign clients | Resilience | High | Small |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.5 | No idempotency on mutations | Resilience | Critical | Medium |
| 7.6 | Balance calculation bug | Resilience | Critical | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 7 |
| High | 12 |
| Medium | 10 |
| Low | 5 |
| **Total** | **34** |
