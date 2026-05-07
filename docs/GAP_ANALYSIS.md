# Engineering Standards Gap Analysis

This document compares the `ts-java-spring-boot-internet-banking-microservices` codebase against industry engineering best practices. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

**Severity: Medium | Effort: Medium**

Each microservice is an independent Gradle project with its own `build.gradle`. There is no root `settings.gradle` or parent build file. This means:
- No shared dependency version management across services
- No single command to build/test all services
- Inconsistent dependency versions possible (currently consistent, but fragile)

### 1.2 Duplicated Code Across Services

**Severity: High | Effort: Medium**

The following classes are copy-pasted identically across 3+ services with no shared library:
- `BaseMapper` (4 copies: core-banking, user-service, fund-transfer, utility-payment)
- `AuditAware` (3 copies: user-service, fund-transfer, utility-payment)
- `AuditConfig` + `AuditorAwareConfig` (3 copies)
- `ApiRequestContext` + `ApiRequestContextHolder` + `AppAuthUserFilter` (3 copies)
- `SimpleBankingGlobalException` (4 copies)
- `ErrorResponse` (4 copies)
- `GlobalExceptionHandler` (4 copies, with slight inconsistencies — see Error Handling)

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

Package layout varies between services:
- Core Banking: `repository/` at top-level, `model/mapper/`, `model/dto/`, `model/entity/`
- User Service: `model/repository/`, `model/rest/response/`, `service/rest/`
- Fund Transfer: `model/repository/`, `service/rest/client/`
- Utility Payment: `repository/` at top-level, `service/rest/`, `model/rest/request/`, `model/rest/response/`

### 1.4 Mappers Instantiated Manually Instead of Spring Beans

**Severity: Low | Effort: Small**

All mappers are `new`-ed as instance fields (`private UserMapper userMapper = new UserMapper()`) rather than being Spring-managed beans. This prevents dependency injection into mappers and is inconsistent with the rest of the DI-driven architecture.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` in fund-transfer-service constructs `ErrorResponse` differently from the other three services:
- Fund transfer: `new ErrorResponse(e.getCode(), e.getMessage())` — uses constructor
- Others: `ErrorResponse.builder().code(...).message(...).build()` — uses builder

More critically, the catch-all `Exception` handler returns a raw string `"Exception occur inside API " + e` — this leaks stack traces and internal details to API consumers.

### 2.2 All Errors Return HTTP 400

**Severity: High | Effort: Small**

Every `GlobalExceptionHandler` returns `ResponseEntity.badRequest()` (HTTP 400) for all exceptions, including:
- `EntityNotFoundException` — should be HTTP 404
- `InsufficientFundsException` — should be HTTP 422 (Unprocessable Entity)
- `UserAlreadyRegisteredException` — should be HTTP 409 (Conflict)
- Generic `Exception` — should be HTTP 500

### 2.3 Raw Type Usage in ResponseEntity

**Severity: Medium | Effort: Small**

All controller methods and exception handlers use raw `ResponseEntity` without type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). This loses compile-time type safety and produces warnings.

### 2.4 SimpleBankingGlobalException Hides `message` Field

**Severity: Medium | Effort: Small**

`SimpleBankingGlobalException` declares its own `message` field via Lombok `@Getter/@Setter`, which shadows `Throwable.message`. The two-arg constructor sets the Lombok field but does NOT call `super(message)`, meaning `getMessage()` from Throwable returns `null` while the Lombok getter returns the actual message. This causes confusing behavior depending on how the exception is consumed.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

Only the **core-banking-service** has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services have only empty Spring Boot context-load tests that are commented out or will fail without infrastructure:

| Service | Test Files | Real Tests |
|---------|-----------|------------|
| core-banking-service | 4 | 15+ test methods |
| internet-banking-user-service | 1 | 0 (context-load only) |
| internet-banking-fund-transfer-service | 1 | 0 (context-load only) |
| internet-banking-utility-payment-service | 1 | 0 (context-load only) |
| internet-banking-api-gateway | 1 | 0 (context-load only) |
| internet-banking-config-server | 1 | 0 (context-load only) |
| internet-banking-service-registry | 1 | 0 (context-load only) |

### 3.2 No Integration Tests

**Severity: High | Effort: Large**

No integration tests exist that:
- Verify Feign client contracts between services
- Test the API gateway routing and security rules
- Validate the full fund-transfer or payment flow end-to-end
- Use Testcontainers or similar for database integration testing

### 3.3 No Contract Tests

**Severity: Medium | Effort: Large**

There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) between the Feign client consumers and the core-banking-service provider. Breaking changes in the provider API would not be caught until runtime.

### 3.4 Context-Load Tests Will Fail Without Infrastructure

**Severity: Medium | Effort: Small**

The `@SpringBootTest` context-load tests in user-service, fund-transfer, and utility-payment require Keycloak/Eureka to be available. While the test `application.yml` files disable Eureka, they don't mock Keycloak, and the JPA configuration with `ddl-auto: none` and no Flyway means H2 databases have empty schemas.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Severity: Critical | Effort: Small**

Multiple credentials are committed to the repository in plain text:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `docker-compose/mysql/Dockerfile`)
- MySQL app user password: `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin password: `password` (in `docker-compose.yml`)
- Keycloak DB password: `password` (in `docker-compose.yml`)
- Keycloak client secret: `e8548d56-d743-45ef-8655-063c9cd96759` (in test `application.yml`)
- Test user password: `5V7huE3G86uB` (in `README.md`)

### 4.2 No Input Validation

**Severity: Critical | Effort: Medium**

No request body validation exists anywhere in the codebase:
- No `@Valid` / `@Validated` annotations on `@RequestBody` parameters
- No Bean Validation constraints (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, `@Email`) on DTO fields
- Fund transfers accept negative amounts
- No account number format validation
- No email format validation on user registration

### 4.3 Password Handling

**Severity: High | Effort: Small**

The `User` DTO in the user-service includes a plain-text `password` field that is:
- Received in the registration request body
- Set as a Keycloak credential
- Potentially logged (via `log.info("Creating user with {}", request.toString())`)
- Potentially serialized back in the response (no `@JsonIgnore`)

### 4.4 CSRF Disabled Without Documentation

**Severity: Low | Effort: Small**

CSRF is disabled in the API gateway's `SecurityConfiguration` (`httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`). While this is appropriate for a stateless JWT-based API, it should be documented as an intentional decision.

### 4.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured on the API gateway or individual services. Financial APIs are high-value targets for abuse.

### 4.6 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP Dependency Check, Snyk, or similar vulnerability scanning tool is configured in any `build.gradle`.

---

## 5. API Design

### 5.1 No API Versioning Strategy

**Severity: Medium | Effort: Medium**

While endpoints use `/api/v1/`, there is no documented versioning strategy, no content negotiation versioning, and no plan for handling breaking changes.

### 5.2 Pagination Response Lacks Metadata

**Severity: Medium | Effort: Small**

GET endpoints that accept `Pageable` parameters return raw `List<T>` instead of a paginated response wrapper. Consumers receive no information about:
- Total number of elements
- Total pages
- Current page number
- Whether there are more pages

### 5.3 No Filtering or Sorting Documentation

**Severity: Low | Effort: Small**

While Spring Data Pageable supports `sort` parameters, there is no documentation of which fields are sortable, and no custom filtering endpoints exist (e.g., search by date range, status, amount).

### 5.4 Inconsistent OpenAPI Documentation

**Severity: Medium | Effort: Small**

OpenAPI (`springdoc-openapi-starter-webflux-ui`) is included in core-banking, user-service, fund-transfer, and utility-payment services with `@Tag` and `@Operation` annotations, but:
- Uses the `webflux-ui` starter in non-reactive (servlet-based) services — should use `springdoc-openapi-starter-webmvc-ui`
- No `@ApiResponse` annotations to document error responses
- No `@Schema` annotations on DTOs to document field constraints
- API gateway and infra services don't expose a unified OpenAPI spec

### 5.5 Inconsistent Resource Naming

**Severity: Low | Effort: Small**

- Fund transfer: `POST /api/v1/transfer` and `GET /api/v1/transfer`
- Utility payment: `POST /api/v1/utility-payment` and `GET /api/v1/utility-payment`
- Core banking user: `GET /api/v1/user/{identification}`
- User service: `POST /api/v1/bank-users/register`

Pluralization and naming conventions are inconsistent.

### 5.6 POST Endpoints Return 200 Instead of 201

**Severity: Low | Effort: Small**

All `@PostMapping` handlers return `ResponseEntity.ok()` (HTTP 200) for resource creation. RESTful convention is to return HTTP 201 Created with a `Location` header.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` logging, others don't (service-registry, config-server have no application logging)
- Log messages use string concatenation in some places (`"Sending fund transfer request {}" + request.toString()`) instead of parameterized logging
- Log levels are not configurable per environment (all configuration is externalized to config server)
- Sensitive data (user requests with passwords) are logged at INFO level

### 6.2 Actuator Endpoints Fully Exposed

**Severity: Medium | Effort: Small**

The API gateway permits all `/actuator/**` endpoints without authentication. While useful for monitoring, endpoints like `/actuator/env`, `/actuator/configprops`, and `/actuator/beans` can expose sensitive configuration in production.

### 6.3 No Custom Health Checks

**Severity: Low | Effort: Small**

Services include `spring-boot-starter-actuator` but rely entirely on default health indicators. No custom health checks for:
- Core banking: MySQL connectivity + schema validation
- User service: Keycloak connectivity
- Fund transfer / utility payment: Core banking service availability

### 6.4 No Metrics Endpoints or Dashboards

**Severity: Medium | Effort: Medium**

The README mentions Prometheus, but no Prometheus metrics endpoint (`/actuator/prometheus`) is configured, and no Grafana dashboards or alerting rules exist. Micrometer is present (for tracing) but not leveraged for custom business metrics (e.g., transaction counts, transfer amounts, error rates).

### 6.5 Distributed Tracing Coverage Gaps

**Severity: Low | Effort: Small**

While Zipkin integration is configured, there is no verification that trace context propagates correctly through:
- Feign clients (feign-micrometer is included, but no sampling configuration)
- The API gateway to downstream services
- Database queries (no JDBC tracing)

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

Feign clients call the core-banking-service without any circuit breaker configuration. If core-banking-service is down or slow:
- Fund transfer service will block indefinitely
- Utility payment service will block indefinitely
- Cascading failures will propagate to the API gateway
- No `spring-cloud-starter-circuitbreaker-resilience4j` dependency in any service

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration exists for Feign clients or any HTTP calls. Transient network failures will immediately fail the request. No Spring Retry or Resilience4j retry is configured.

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeouts are configured for:
- Feign client HTTP calls (defaults to infinite/OS-level timeout)
- Database connections
- Keycloak admin client calls
- The only timeouts present are the `wait-for-it.sh` startup scripts (50 seconds)

### 7.4 No Fallback Behavior

**Severity: Medium | Effort: Medium**

No fallback mechanisms exist when downstream services are unavailable:
- No Feign fallback classes or fallback factories
- No cached responses for read operations
- No graceful degradation — all failures result in raw exception propagation

### 7.5 No Idempotency Protection

**Severity: High | Effort: Medium**

Financial transaction endpoints (`POST /api/v1/transfer`, `POST /api/v1/utility-payment`) have no idempotency protection:
- No idempotency key header support
- No duplicate transaction detection
- Network retries (if added) could cause double-transfers
- The fund transfer service saves a PENDING record then calls core banking — if the core banking call succeeds but the response is lost, the status remains PENDING but money has moved

### 7.6 No Transaction Compensation / Saga Pattern

**Severity: High | Effort: Large**

The fund transfer and utility payment flows span multiple services and databases without a saga pattern or distributed transaction coordinator:
- Fund Transfer Service saves locally → calls Core Banking → updates locally
- If the final local update fails, the fund transfer record stays PENDING while the core banking transaction completed
- No compensation logic to reverse the core banking transaction
- The `@Transactional` in Core Banking Service only covers its own local database

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|-----|----------|--------|----------|
| 1.1 | No multi-module Gradle build | Medium | Medium | Code Organization |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 1.3 | Inconsistent package structure | Low | Small | Code Organization |
| 1.4 | Mappers not Spring beans | Low | Small | Code Organization |
| 2.1 | Inconsistent error response format | High | Small | Error Handling |
| 2.2 | All errors return HTTP 400 | High | Small | Error Handling |
| 2.3 | Raw type ResponseEntity | Medium | Small | Error Handling |
| 2.4 | SimpleBankingGlobalException message shadowing | Medium | Small | Error Handling |
| 3.1 | Minimal test coverage | Critical | Large | Testing |
| 3.2 | No integration tests | High | Large | Testing |
| 3.3 | No contract tests | Medium | Large | Testing |
| 3.4 | Context-load tests fail without infra | Medium | Small | Testing |
| 4.1 | Hardcoded credentials in source | Critical | Small | Security |
| 4.2 | No input validation | Critical | Medium | Security |
| 4.3 | Password handling issues | High | Small | Security |
| 4.4 | CSRF disabled without docs | Low | Small | Security |
| 4.5 | No rate limiting | Medium | Medium | Security |
| 4.6 | No dependency vulnerability scanning | Medium | Small | Security |
| 5.1 | No API versioning strategy | Medium | Medium | API Design |
| 5.2 | Pagination lacks metadata | Medium | Small | API Design |
| 5.3 | No filtering/sorting docs | Low | Small | API Design |
| 5.4 | Inconsistent OpenAPI documentation | Medium | Small | API Design |
| 5.5 | Inconsistent resource naming | Low | Small | API Design |
| 5.6 | POST returns 200 instead of 201 | Low | Small | API Design |
| 6.1 | Inconsistent logging | Medium | Small | Observability |
| 6.2 | Actuator endpoints fully exposed | Medium | Small | Observability |
| 6.3 | No custom health checks | Low | Small | Observability |
| 6.4 | No metrics/dashboards | Medium | Medium | Observability |
| 6.5 | Tracing coverage gaps | Low | Small | Observability |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 7.4 | No fallback behavior | Medium | Medium | Resilience |
| 7.5 | No idempotency protection | High | Medium | Resilience |
| 7.6 | No saga / compensation pattern | High | Large | Resilience |
