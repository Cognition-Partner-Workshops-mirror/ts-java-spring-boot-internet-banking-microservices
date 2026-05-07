# Engineering Standards Gap Analysis

This document compares the codebase against industry best practices across seven engineering dimensions. Each gap is rated by **Severity** and **Remediation Effort**.

**Severity scale:**
- **Critical** -- Production risk, data integrity issue, or security vulnerability
- **High** -- Significant quality or maintainability concern
- **Medium** -- Deviation from best practice that should be addressed
- **Low** -- Polish item or nice-to-have improvement

**Effort scale:**
- **Small** -- < 1 day per service
- **Medium** -- 1-3 days per service
- **Large** -- 3+ days per service or cross-cutting

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build
**Severity: Medium | Effort: Medium**

Each of the 6 services is an independent Gradle project with its own `gradlew` wrapper, `build.gradle`, and `settings.gradle`. There is no root `settings.gradle` or `build.gradle` to coordinate builds, enforce consistent dependency versions, or share plugins.

**Impact:** Dependency version drift between services, no single command to build/test all services, duplicated build configuration.

### 1.2 Massive Code Duplication Across Services
**Severity: High | Effort: Large**

The following classes are copy-pasted identically (or near-identically) across 3-4 services:
- `BaseMapper<E, D>` (4 copies: core, user, fund-transfer, utility-payment)
- `AuditAware` (3 copies: user, fund-transfer, utility-payment)
- `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` (3 copies: user, fund-transfer, utility-payment)
- `AuditConfig` / `AuditorAwareConfig` (3 copies)
- `ErrorResponse` (4 copies)
- `SimpleBankingGlobalException` (4 copies)
- `GlobalExceptionHandler` (4 copies)
- `TransactionStatus` enum (2 copies)

**Impact:** Bugs fixed in one copy won't be fixed in others. Inconsistencies will creep in over time (and already have -- the `ErrorResponse` in fund-transfer uses constructor instantiation while others use the builder pattern).

### 1.3 No Shared Library / Common Module
**Severity: High | Effort: Large**

There is no shared library for common DTOs, exceptions, mappers, or configuration classes. Each service redefines these independently.

**Impact:** Violates DRY principle, increases maintenance burden, causes subtle contract mismatches between services.

### 1.4 Inconsistent Package Structure
**Severity: Low | Effort: Small**

- Core banking: `repository/` at root package level
- Fund transfer: `model/repository/` (repository inside model)
- User service: `model/repository/`
- Utility payment: `repository/` at root
- Feign clients are in different packages: `service/rest/client/`, `service/rest/`, `configuration/`

### 1.5 Inconsistent Feign Client Configuration
**Severity: Medium | Effort: Small**

Three different patterns are used for Feign client configuration:
1. **Fund transfer service:** `CustomFeignClientConfiguration` returns `Logger.Level.FULL`
2. **User service:** `CustomFeignClientConfiguration` extends `FeignClientConfiguration` and provides `CustomFeignErrorDecoder`
3. **Utility payment service:** `CustomFeignClientConfiguration` extends `FeignClientConfiguration` with no custom beans
4. **User service Feign client:** No `configuration` attribute at all on `@FeignClient`

### 1.6 Mappers Instantiated Inline Instead of as Spring Beans
**Severity: Low | Effort: Small**

All mapper instances are created via `new` in service classes (`private FundTransferMapper mapper = new FundTransferMapper()`) rather than being Spring-managed beans. This prevents dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 All Exceptions Return HTTP 400
**Severity: High | Effort: Medium**

The `GlobalExceptionHandler` in every service maps all exceptions (including `EntityNotFoundException`) to `400 Bad Request`. Entity-not-found should be `404`, insufficient funds should arguably be `422`, and unexpected exceptions should be `500`.

The catch-all handler returns a raw string (`"Exception occur inside API " + e`) instead of a structured error response, leaking internal stack traces to clients.

### 2.2 Inconsistent Error Response Construction
**Severity: Medium | Effort: Small**

- Core, user, and utility-payment services use `ErrorResponse.builder().code(...).message(...).build()`
- Fund transfer service uses `new ErrorResponse(e.getCode(), e.getMessage())` (constructor), but `ErrorResponse` has `@Builder` and no all-args constructor visible -- this relies on Lombok generating a constructor implicitly, which is fragile.

### 2.3 No Error Codes in Fund Transfer or Utility Payment Services
**Severity: Medium | Effort: Small**

Unlike core-banking and user-service which define `GlobalErrorCode` constants, the fund-transfer and utility-payment services have no error code constants. Their `SimpleBankingGlobalException` can carry a code, but nothing sets one.

### 2.4 Feign Error Propagation Only in User Service
**Severity: High | Effort: Medium**

Only the user service has a `CustomFeignErrorDecoder` that extracts structured error responses from Feign failures. The fund-transfer and utility-payment services use default Feign error handling, which wraps errors in a generic `FeignException` and results in uninformative `400` responses with stack traces.

### 2.5 No Transaction Rollback on Feign Failures
**Severity: Critical | Effort: Medium**

In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`:
1. A local entity is saved with `PENDING`/`PROCESSING` status
2. A Feign call is made to core-banking
3. If the Feign call fails, the local entity is **never updated to FAILED**

The status remains `PENDING`/`PROCESSING` forever, creating orphaned records with no way to know they failed.

### 2.6 Silent Exception Swallowing in CustomFeignErrorDecoder
**Severity: Medium | Effort: Small**

In `CustomFeignErrorDecoder.extractBankingCoreGlobalException()`, if the response body cannot be parsed, `exceptionMessage` is returned as `null`, which will cause a `NullPointerException` in the calling `decode()` method.

---

## 3. Testing

### 3.1 Minimal Test Coverage
**Severity: High | Effort: Large**

| Service | Test Files | Content |
|---|---|---|
| core-banking-service | `CoreBankingServiceApplicationTests`, `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Context load + unit tests for services |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | Context load only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load only |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only |

Only `core-banking-service` has meaningful unit tests. The remaining 5 services have zero business logic tests.

### 3.2 No Integration Tests
**Severity: High | Effort: Large**

There are no integration tests that verify:
- Controller layer (no `@WebMvcTest` or `MockMvc` tests)
- Repository layer (no `@DataJpaTest` tests)
- Full application context with testcontainers
- API contract validation

### 3.3 No Contract Tests Between Services
**Severity: Medium | Effort: Large**

No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist to verify that Feign client interfaces match the actual provider API contracts. If the core-banking API changes, downstream services will break at runtime.

### 3.4 Context Load Tests Require External Dependencies
**Severity: Medium | Effort: Small**

The `@SpringBootTest` context load tests for API gateway and config server attempt to connect to Keycloak and Git respectively, making them fail in isolation. These need profiles or mocks to run in CI.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code
**Severity: Critical | Effort: Small**

The following secrets are committed to the repository:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `Dockerfile`)
- MySQL user password: `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin password: `password` (in `docker-compose.yml`)
- Keycloak DB password: `password` (in `docker-compose.yml`)
- Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` (in test `application.yml`)
- Test user credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` (in `README.md`)

### 4.2 No Input Validation
**Severity: Critical | Effort: Medium**

No `@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Min`, or other Bean Validation annotations are used anywhere in the codebase. Request bodies are accepted without any validation:

- Fund transfer accepts negative or zero amounts
- User registration accepts empty email/identification
- Utility payment accepts null provider ID

### 4.3 Raw `ResponseEntity` Without Type Parameters
**Severity: Medium | Effort: Small**

Almost all controller methods return raw `ResponseEntity` instead of typed `ResponseEntity<T>`. This bypasses compile-time type safety and makes OpenAPI documentation inaccurate.

Only the user-service controller uses typed returns (`ResponseEntity<User>`, `ResponseEntity<List<User>>`).

### 4.4 CSRF Disabled Without Documentation
**Severity: Low | Effort: Small**

CSRF is disabled in the gateway security configuration. While this is acceptable for a stateless JWT-based API, the decision should be documented with rationale.

### 4.5 Keycloak Singleton is Not Thread-Safe
**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a check-then-act pattern without synchronization, which can create multiple Keycloak instances under concurrent access. Should use `@Bean` or `synchronized`/`volatile`.

### 4.6 No Rate Limiting
**Severity: Medium | Effort: Medium**

No rate limiting is configured on the API gateway or individual services. Financial APIs are high-value targets for abuse.

### 4.7 Sensitive Data in Logs
**Severity: Medium | Effort: Small**

Controllers log full `toString()` of request objects:
```java
log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
log.info("Creating user with {}", request.toString()); // includes password
```
This logs sensitive financial data and user passwords to application logs.

### 4.8 No Dependency Vulnerability Scanning
**Severity: Medium | Effort: Small**

No OWASP Dependency-Check, Snyk, or similar tool is configured. The `springdoc-openapi-starter-webflux-ui:2.1.0` is pinned to an older version while Spring Boot 3.2.4 is in use.

---

## 5. API Design

### 5.1 Inconsistent Pagination Response Format
**Severity: Medium | Effort: Medium**

Paginated endpoints return `List<T>` rather than a paginated wrapper with `totalElements`, `totalPages`, `pageNumber`, `pageSize`, `hasNext` etc. Clients cannot determine total count or navigate pages.

### 5.2 No API Versioning Strategy
**Severity: Low | Effort: Small**

All APIs use `/api/v1/` prefix, which is good, but there's no documented versioning strategy for when breaking changes need to be introduced.

### 5.3 Non-RESTful URL Patterns
**Severity: Low | Effort: Small**

- `POST /api/v1/bank-users/register` -- should be `POST /api/v1/bank-users` (resource creation is implied by POST)
- `PATCH /api/v1/bank-users/update/{id}` -- should be `PATCH /api/v1/bank-users/{id}` (update is implied by PATCH)
- `POST /api/v1/transfer` -- should be `POST /api/v1/transfers` (plural resource names)

### 5.4 OpenAPI Dependency Mismatch
**Severity: Medium | Effort: Small**

Services use `springdoc-openapi-starter-webflux-ui:2.1.0` (WebFlux variant) but all business services are servlet-based (Spring MVC). The correct dependency is `springdoc-openapi-starter-webmvc-ui`. The API Gateway (which IS WebFlux-based) is the only service where this dependency is appropriate, but it doesn't include it.

### 5.5 No Filtering or Sorting Support
**Severity: Low | Effort: Medium**

List endpoints accept `Pageable` (which supports `sort`) but no filtering parameters. Real banking applications need to filter transactions by date range, status, amount, etc.

### 5.6 Missing HTTP Status Codes for Write Operations
**Severity: Low | Effort: Small**

All `POST` endpoints return `200 OK` instead of `201 Created`. `PATCH` should return `200 OK` (correct) or `204 No Content`.

---

## 6. Observability

### 6.1 No Structured Logging
**Severity: Medium | Effort: Medium**

Logging uses default Spring Boot format with no structured (JSON) output. In a microservices architecture, structured logs are essential for log aggregation (ELK, Loki, etc.).

### 6.2 No Health Check Customization
**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but do not define custom health indicators for critical dependencies (database connectivity, Keycloak availability, Feign client reachability).

### 6.3 No Metrics Endpoints Beyond Actuator Defaults
**Severity: Medium | Effort: Medium**

No custom business metrics are defined (e.g., transfer count, payment success rate, average transaction amount). Only default Actuator/Micrometer metrics are available.

### 6.4 Incomplete Distributed Tracing Coverage
**Severity: Medium | Effort: Small**

While Micrometer + Brave + Zipkin dependencies are included, there is no verification that trace context propagates correctly through:
- API Gateway to downstream services
- Feign client calls
- Database queries

No `management.tracing.sampling.probability` is configured (defaults to 0.1 = 10% sampling).

### 6.5 String Concatenation in Log Statements
**Severity: Low | Effort: Small**

Some log statements use string concatenation instead of parameterized logging:
```java
log.info("Sending fund transfer request {}" + request.toString()); // wrong: concatenation with placeholder
log.error("IO Exception on reading exception message feign client" + e); // concatenation
```
This defeats lazy evaluation and can impact performance.

### 6.6 No Centralized Log Correlation
**Severity: Medium | Effort: Medium**

While Zipkin collects traces, there is no MDC (Mapped Diagnostic Context) configuration to include trace IDs in log output, making it hard to correlate logs with traces.

---

## 7. Resilience

### 7.1 No Circuit Breakers
**Severity: Critical | Effort: Medium**

No circuit breaker pattern (Resilience4j, Spring Cloud Circuit Breaker) is implemented. If the core-banking-service goes down, all upstream services will hang on Feign calls, cascading the failure across the entire system.

### 7.2 No Retry Policies
**Severity: High | Effort: Small**

No retry configuration exists for Feign clients. Transient network failures will immediately fail the entire operation. Spring Cloud OpenFeign supports `spring.cloud.openfeign.client.config.default.retryer` but it is not configured.

### 7.3 No Timeout Configuration
**Severity: High | Effort: Small**

No explicit timeouts are configured for:
- Feign client connection/read timeouts
- Database connection pool timeouts
- API Gateway route timeouts

Default Feign timeout is 60 seconds for read, which is too long for a banking API and will exhaust thread pools under load.

### 7.4 No Fallback Behavior
**Severity: Medium | Effort: Medium**

No fallback methods are defined for Feign client failures. When core-banking-service is unreachable, the error propagates as a raw exception rather than a graceful degradation response.

### 7.5 No Bulkhead / Thread Pool Isolation
**Severity: Medium | Effort: Medium**

All Feign calls share the same thread pool. A slow response from one downstream service will consume threads and affect calls to other services.

### 7.6 Incorrect `wait-for-it.sh` Does Not Guarantee Readiness
**Severity: Low | Effort: Small**

The Docker startup script uses `wait-for-it.sh` to check TCP port availability, but a port being open does not mean the application is ready to serve requests. Services should use health check endpoints for readiness verification.

### 7.7 No Idempotency Keys for Financial Operations
**Severity: Critical | Effort: Medium**

Fund transfer and utility payment endpoints have no idempotency mechanism. If a client retries a request (due to timeout or network error), the same transfer/payment can be processed multiple times, causing duplicate debits.

### 7.8 Non-Atomic Fund Transfer in Core Banking
**Severity: Critical | Effort: Medium**

`TransactionService.internalFundTransfer()` performs two separate `bankAccountRepository.save()` calls. While `@Transactional` is present, the method uses JPA's default transaction propagation. If the process crashes between the debit and credit saves, data could be left in an inconsistent state depending on the isolation level and flush timing.

The balance calculation bug (double-deduction) mentioned in the Knowledge Base compounds this risk.

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Code Organization | 0 | 2 | 2 | 2 | 6 |
| Error Handling | 1 | 2 | 3 | 0 | 6 |
| Testing | 0 | 2 | 2 | 0 | 4 |
| Security | 2 | 0 | 4 | 2 | 8 |
| API Design | 0 | 0 | 2 | 4 | 6 |
| Observability | 0 | 0 | 4 | 2 | 6 |
| Resilience | 3 | 2 | 2 | 1 | 8 |
| **Total** | **6** | **8** | **19** | **11** | **44** |
