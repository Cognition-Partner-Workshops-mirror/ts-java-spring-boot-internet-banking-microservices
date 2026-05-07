# Engineering Standards Gap Analysis

## Overview

This document compares the Internet Banking Microservices codebase against industry engineering best practices across seven dimensions. Each gap is rated by **severity** and **estimated remediation effort**.

**Severity Levels**:
- **Critical**: Immediate risk to security, data integrity, or system stability
- **High**: Significant technical debt impacting reliability or maintainability
- **Medium**: Deviation from best practices that should be addressed
- **Low**: Nice-to-have improvements

**Effort Levels**:
- **Small**: < 1 day per service
- **Medium**: 1-3 days per service
- **Large**: 1+ week per service or cross-cutting

---

## Table of Contents

- [1. Code Organization](#1-code-organization)
- [2. Error Handling](#2-error-handling)
- [3. Testing](#3-testing)
- [4. Security](#4-security)
- [5. API Design](#5-api-design)
- [6. Observability](#6-observability)
- [7. Resilience](#7-resilience)
- [Summary Matrix](#summary-matrix)

---

## 1. Code Organization

### GAP-CO-01: Duplicated Base Classes Across Services

**Severity**: Medium | **Effort**: Medium

The `BaseMapper`, `AuditAware`, `SimpleBankingGlobalException`, `ErrorResponse`, and `GlobalExceptionHandler` classes are duplicated identically (or nearly so) in 3-4 services. This leads to:
- Inconsistent evolution (e.g., the `CustomFeignClientConfiguration` in fund-transfer extends nothing useful, while in utility-payment it extends `FeignClientConfiguration`)
- Maintenance burden when fixing bugs in shared logic

**Current State**: Each of the 4 business services has its own copy of:
- `BaseMapper<E, D>` (identical in all 4 services)
- `AuditAware` (identical in user, fund-transfer, utility-payment services)
- `SimpleBankingGlobalException` (identical in user, fund-transfer, utility-payment services)
- `ErrorResponse` (identical in fund-transfer, utility-payment services)
- `GlobalExceptionHandler` (similar but slightly different in core-banking vs. fund-transfer vs. utility-payment)
- `AppAuthUserFilter` + `ApiRequestContext` + `ApiRequestContextHolder` (identical in fund-transfer and utility-payment, absent from core-banking)

**Best Practice**: Extract shared code into a `common` or `shared-lib` Gradle module published as a local dependency.

---

### GAP-CO-02: No Multi-Project Gradle Build

**Severity**: Low | **Effort**: Medium

Each service has its own standalone Gradle build (`settings.gradle`, `build.gradle`, `gradle/wrapper`). There is no parent `settings.gradle` or root `build.gradle` for unified builds, dependency version management, or plugin configuration.

**Current State**: 6 independent Gradle projects with duplicated version declarations (Spring Boot 3.2.4, Spring Cloud 2023.0.0, etc.).

**Best Practice**: Use a Gradle multi-project build with a root `build.gradle` for shared plugin and dependency version management, and a `settings.gradle` including all subprojects.

---

### GAP-CO-03: Inconsistent Package Structure

**Severity**: Low | **Effort**: Small

Package structures are inconsistent across services:
- Core Banking: `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `controller`, `exception`
- User Service: `model.dto`, `model.entity`, `model.mapper`, `model.repository`, `model.rest.response`, `service`, `service.rest`, `controller`, `exception`, `configuration.*`
- Fund Transfer: `model.dto`, `model.dto.request`, `model.dto.response`, `model.entity`, `model.mapper`, `model.repository`, `service`, `service.rest.client`, `controller`, `exception`, `configuration`
- Utility Payment: `model.rest.request`, `model.rest.response`, `model.dto`, `model.entity`, `model.mapper`, `repository`, `service`, `service.rest`, `controller`, `exception`, `configuration`

Key inconsistencies:
- Repository package is `repository` in core-banking/utility-payment but `model.repository` in user/fund-transfer
- Request/Response DTOs are in `model.dto.request`/`model.dto.response` in fund-transfer but `model.rest.request`/`model.rest.response` in utility-payment

**Best Practice**: Standardize on a single package layout convention across all services.

---

### GAP-CO-04: Mappers Instantiated as Fields Instead of Spring Beans

**Severity**: Low | **Effort**: Small

All mappers (e.g., `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are instantiated directly as class fields:
```java
private UserMapper userMapper = new UserMapper();
```
They are not managed by Spring, which prevents injection, testing, or AOP proxying.

**Best Practice**: Register mappers as `@Component` beans, or use MapStruct for compile-time-safe mapping.

---

## 2. Error Handling

### GAP-EH-01: Inconsistent Error Response Format

**Severity**: High | **Effort**: Small

Error response structures differ across services:

| Service | Error Model | Notes |
|---------|------------|-------|
| Core Banking | `ErrorResponse { code, message }` + `GlobalExceptionHandler` returning `ResponseEntity<ErrorResponse>` | Most complete |
| User Service | `ErrorResponse { code, message }` + `GlobalExceptionHandler` with specific exception types | Has `GlobalErrorCode` constants |
| Fund Transfer | `ErrorResponse { code, message }` but generic `Exception` handler returns raw string: `"Exception occur inside API " + e` | Exposes stack trace info |
| Utility Payment | `ErrorResponse { code, message }` builder pattern | Slightly different structure |

**Specific Issues**:
- Fund transfer and utility payment generic `Exception` handlers return raw exception text as string, not wrapped in `ErrorResponse`
- No consistent HTTP status code mapping (everything returns 400 Bad Request regardless of the actual error)
- `EntityNotFoundException` in core-banking returns 400 instead of 404

**Best Practice**: Use a single shared `ErrorResponse` format with proper HTTP status codes (400 for validation, 404 for not found, 500 for server errors).

---

### GAP-EH-02: Missing Error Handling in Feign Calls

**Severity**: High | **Effort**: Medium

When Feign calls between services fail (e.g., core-banking is down, or returns an error), there is no proper error decoding or handling:

- Fund Transfer Service: `BankingCoreFeignClient` uses `CustomFeignClientConfiguration` which only sets log level to FULL
- Utility Payment Service: `BankingCoreRestClient` uses `CustomFeignClientConfiguration` that extends `FeignClientConfiguration` but adds nothing
- User Service: Has a `CustomFeignErrorDecoder` (file exists) but its implementation is not clear

If core banking returns a 400/500, the Feign client will throw a `FeignException` that gets caught by the generic exception handler and leaks internal details.

**Best Practice**: Implement a custom `ErrorDecoder` for all Feign clients that maps upstream errors to appropriate domain exceptions.

---

### GAP-EH-03: No Validation on Request Bodies

**Severity**: Critical | **Effort**: Small

No `@Valid` or `@Validated` annotations on any `@RequestBody` parameter. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Positive`, etc.) on any request DTO.

Examples:
- `FundTransferRequest` accepts any `fromAccount`, `toAccount`, `amount` including null/negative values
- `UtilityPaymentRequest` has no validation
- `User` registration request has no email format validation

**Best Practice**: Add `jakarta.validation` annotations to all DTOs and `@Valid` to controller method parameters.

---

## 3. Testing

### GAP-TE-01: Minimal Test Coverage

**Severity**: High | **Effort**: Large

| Service | Test Files | Test Type | Coverage |
|---------|-----------|-----------|----------|
| Core Banking | `CoreBankingServiceApplicationTests`, `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` | Context load + Unit tests | Service layer only; no controller/integration tests |
| User Service | `InternetBankingUserServiceApplicationTests` | Context load only | No business logic tests |
| Fund Transfer | `InternetBankingFundTransferServiceApplicationTests` | Context load only | No business logic tests |
| Utility Payment | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only | No business logic tests |
| API Gateway | `InternetBankingApiGatewayApplicationTests` | Context load only | No routing/security tests |
| Config Server | `InternetBankingConfigServerApplicationTests` | Context load only | Trivial |
| Service Registry | `InternetBankingServiceRegistryApplicationTests` | Context load only | Trivial |

**Key Issues**:
- Only `core-banking-service` has meaningful unit tests (3 test files with mock-based service tests)
- 5 out of 6 services have only an empty `contextLoads()` test
- No controller/API layer tests (MockMvc or WebTestClient)
- No repository layer tests (`@DataJpaTest`)

**Best Practice**: Target 70-80% line coverage; at minimum, test all service methods, controllers, and repositories.

---

### GAP-TE-02: No Integration Tests

**Severity**: High | **Effort**: Large

There are zero integration tests that verify:
- Feign client communication between services
- End-to-end flows (registration → transfer → payment)
- Database interaction with real MySQL (tests use H2 in-memory)
- Keycloak integration

**Best Practice**: Add `@SpringBootTest` integration tests with Testcontainers for MySQL and Keycloak, and WireMock/MockServer for Feign clients.

---

### GAP-TE-03: No Contract Tests Between Services

**Severity**: Medium | **Effort**: Large

With 3 Feign clients calling core-banking-service, there are no contract tests (e.g., Spring Cloud Contract, Pact) to verify API compatibility between consumer and provider.

**Best Practice**: Implement consumer-driven contract tests to catch breaking API changes before deployment.

---

### GAP-TE-04: Context Load Tests Likely Failing

**Severity**: Medium | **Effort**: Small

Several `@SpringBootTest` context-load tests likely fail without external infrastructure:
- User Service test requires Keycloak (KeycloakProperties reads `@Value` properties)
- Fund Transfer and Utility Payment tests need Eureka client config
- API Gateway test needs OAuth2 configuration

The test `application.yml` files disable Eureka and Flyway, but some don't mock Keycloak or Config Server.

**Best Practice**: Ensure all `@SpringBootTest` tests are self-contained; use `@MockBean` for external dependencies or use `@SpringBootTest(webEnvironment = NONE)` with appropriate test profiles.

---

## 4. Security

### GAP-SE-01: Hardcoded Credentials in Source Code

**Severity**: Critical | **Effort**: Small

Multiple credentials are hardcoded in version-controlled files:

| Location | Credential | Value |
|----------|-----------|-------|
| `docker-compose.yml` | MySQL root password | `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password | `password` |
| `docker-compose.yml` | Keycloak DB password | `password` |
| `privileges.sql` | App DB user password | `oPItyPticIAt` |
| `README.md` | Test user password | `5V7huE3G86uB` |
| `test application.yml` (user-service) | Keycloak client secret | `e8548d56-d743-45ef-8655-063c9cd96759` |

**Best Practice**: Use environment variables, Docker secrets, or a vault for all credentials. Never commit secrets to source control.

---

### GAP-SE-02: No Input Validation or Sanitization

**Severity**: Critical | **Effort**: Small

As noted in GAP-EH-03, there is zero input validation. This creates security risks:
- SQL injection risk is mitigated by JPA/Hibernate parameterized queries, but application-level validation is still absent
- No protection against excessively large request bodies
- No rate limiting on sensitive endpoints (e.g., `/register`)
- `identification` and `email` fields are passed directly without sanitization

**Best Practice**: Apply Bean Validation (`@Valid`), add request size limits, and consider rate limiting on authentication endpoints.

---

### GAP-SE-03: Overly Permissive Actuator Endpoints

**Severity**: Medium | **Effort**: Small

All actuator endpoints are publicly accessible (no authentication required):
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

Actuator can expose sensitive information (`/actuator/env`, `/actuator/configprops`, `/actuator/beans`) depending on which endpoints are enabled.

**Best Practice**: Restrict actuator access to only `/actuator/health` and `/actuator/info` publicly; require authentication for others.

---

### GAP-SE-04: Keycloak Client Secret as Static Singleton

**Severity**: Medium | **Effort**: Small

`KeycloakProperties` uses a static singleton pattern for the Keycloak instance:
```java
private static Keycloak keycloakInstance = null;
```
This means:
- The instance is never refreshed (token expiry issues)
- Not thread-safe (multiple threads could create instances simultaneously)
- Cannot be reconfigured at runtime

**Best Practice**: Use Spring-managed bean with proper lifecycle management, or use the Keycloak Spring Boot adapter.

---

### GAP-SE-05: No CORS Configuration

**Severity**: Medium | **Effort**: Small

The API Gateway has no CORS configuration. If a frontend SPA attempts to call these APIs, it will be blocked by browser CORS policies.

**Best Practice**: Configure CORS in the API Gateway with appropriate allowed origins, methods, and headers.

---

### GAP-SE-06: No Dependency Vulnerability Scanning

**Severity**: Medium | **Effort**: Small

No dependency vulnerability scanning is configured (e.g., OWASP Dependency Check, Snyk, or GitHub Dependabot). The project uses several third-party libraries that may contain known vulnerabilities.

**Best Practice**: Add OWASP Dependency Check Gradle plugin or enable GitHub Dependabot alerts.

---

## 5. API Design

### GAP-AD-01: Raw ResponseEntity Without Type Parameters

**Severity**: Medium | **Effort**: Small

Most controller methods return raw `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)  // Missing type parameter
```

This impacts:
- Compile-time type safety
- OpenAPI/Swagger documentation generation (response schema cannot be inferred)

**Affected services**: Core Banking (all endpoints), Fund Transfer (all endpoints), Utility Payment (all endpoints).

**Exception**: User Service correctly uses `ResponseEntity<User>` and `ResponseEntity<List<User>>`.

**Best Practice**: Always specify `ResponseEntity<T>` with the appropriate response type.

---

### GAP-AD-02: Non-RESTful URL Patterns

**Severity**: Low | **Effort**: Small

Several endpoints deviate from REST conventions:

| Current | RESTful Alternative | Issue |
|---------|-------------------|-------|
| `PATCH /bank-users/update/{id}` | `PATCH /bank-users/{id}` | Verb in URL |
| `POST /transaction/fund-transfer` | `POST /fund-transfers` | Non-resource URL |
| `POST /transaction/util-payment` | `POST /utility-payments` | Non-resource URL |

**Best Practice**: Use nouns for resources, let HTTP methods convey the action.

---

### GAP-AD-03: No API Versioning Strategy

**Severity**: Low | **Effort**: Medium

While all endpoints include `/api/v1/`, there is no documented strategy or mechanism for maintaining multiple API versions. No backward-compatibility guarantees or deprecation policy.

**Best Practice**: Document the versioning strategy and plan for backward-compatible evolution.

---

### GAP-AD-04: Incomplete OpenAPI Documentation

**Severity**: Medium | **Effort**: Small

The `springdoc-openapi-starter-webflux-ui` dependency is included in build files, but:
- The dependency is `webflux-ui` in services that use Spring MVC (not WebFlux) - this is the wrong starter
- `@Operation` and `@Tag` annotations are present on controllers but response schemas are incomplete due to raw `ResponseEntity`
- No global API metadata (`@OpenAPIDefinition`) is configured
- No example request/response values documented

**Best Practice**: Use `springdoc-openapi-starter-webmvc-ui` for MVC services; add `@OpenAPIDefinition` and complete `@ApiResponse` annotations.

---

### GAP-AD-05: No Pagination Metadata in List Responses

**Severity**: Medium | **Effort**: Small

List endpoints accept Spring's `Pageable` parameter but return raw `List<T>` instead of including pagination metadata (total elements, total pages, current page):
```java
return ResponseEntity.ok(fundTransferService.readAllTransfers(pageable));
// Returns List<FundTransfer>, loses pagination metadata
```

**Best Practice**: Return `Page<T>` or a custom paginated response wrapper with `totalElements`, `totalPages`, `pageNumber`, `pageSize`.

---

## 6. Observability

### GAP-OB-01: No Structured Logging

**Severity**: Medium | **Effort**: Small

All services use SLF4J with default logging format. There is no structured logging (JSON format) configured, making log aggregation and parsing difficult in production.

**Best Practice**: Configure Logback to output JSON-structured logs (e.g., `logstash-logback-encoder`), include trace IDs, service name, and request context.

---

### GAP-OB-02: Inconsistent Log Levels and Sensitive Data in Logs

**Severity**: High | **Effort**: Small

Several controllers log request objects at INFO level using `toString()`:
```java
log.info("Creating user with {}", request.toString());  // May log passwords
log.info("Fund transfer initiated in core bank from {}", fundTransferRequest.toString());
```

The `User` DTO contains a `password` field, which could be logged in plaintext via `toString()`.

**Best Practice**: Never log sensitive data. Mask or exclude sensitive fields. Use `@ToString.Exclude` on password fields.

---

### GAP-OB-03: No Health Check Customization

**Severity**: Low | **Effort**: Small

While `spring-boot-starter-actuator` is included in all services, there are no custom health indicators for:
- Database connectivity (default Spring health check may suffice)
- Keycloak connectivity
- Downstream service availability (core-banking from fund-transfer/utility-payment)

**Best Practice**: Add custom `HealthIndicator` beans for critical dependencies.

---

### GAP-OB-04: No Metrics Endpoints or Dashboards

**Severity**: Medium | **Effort**: Medium

While Actuator and Micrometer are present, there is no:
- Prometheus metrics endpoint configured (`/actuator/prometheus`)
- Grafana dashboards defined
- Custom business metrics (e.g., transfer count, payment volume, error rates)

The README mentions Prometheus as a technology but it is not configured in any service.

**Best Practice**: Add `micrometer-registry-prometheus` dependency and expose `/actuator/prometheus`; create Grafana dashboards.

---

### GAP-OB-05: Incomplete Distributed Tracing Coverage

**Severity**: Low | **Effort**: Small

While Zipkin tracing dependencies are present, trace configuration relies entirely on the external config server. There is no local fallback configuration for:
- Sampling rate
- Trace propagation format
- Custom span annotations for business operations

**Best Practice**: Define sensible defaults in each service's `application.yml` for tracing, with config server overrides.

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

**Severity**: Critical | **Effort**: Medium

All inter-service Feign calls have no circuit breaker protection. If core-banking-service goes down:
- Fund Transfer Service will hang or throw unhandled exceptions
- Utility Payment Service will hang or throw unhandled exceptions
- User Service (calling core banking for user lookup) will hang

This creates a cascading failure risk across the entire system.

**Best Practice**: Add `spring-cloud-starter-circuitbreaker-resilience4j` with Feign integration. Configure circuit breaker policies per Feign client.

---

### GAP-RE-02: No Retry Policies

**Severity**: High | **Effort**: Small

There are no retry configurations for transient failures on:
- Feign client calls
- Database connections
- Config Server fetches at startup

**Best Practice**: Configure Spring Retry or Resilience4j Retry for Feign calls with exponential backoff. Add `spring.cloud.config.retry.*` for config server.

---

### GAP-RE-03: No Timeout Configuration

**Severity**: High | **Effort**: Small

No explicit timeout values are configured for:
- Feign client connection and read timeouts (defaults to no timeout in some versions)
- Database connection pool timeouts
- Gateway route timeouts

**Best Practice**: Set explicit timeouts for all external calls:
```yaml
spring.cloud.openfeign.client.config.default.connect-timeout: 5000
spring.cloud.openfeign.client.config.default.read-timeout: 5000
```

---

### GAP-RE-04: No Fallback Behavior

**Severity**: Medium | **Effort**: Medium

When downstream services fail, there is no graceful degradation:
- No fallback methods on Feign clients
- No cached responses for read operations
- Fund transfers and payments have no compensation/rollback mechanism

The `FundTransferService` saves a `PENDING` record, calls core banking, and updates to `SUCCESS`. If the update fails after core banking succeeds, the record stays `PENDING` forever with no reconciliation.

**Best Practice**: Implement fallback methods via `@FeignClient(fallback = ...)` or Resilience4j fallback. Add a scheduled job to reconcile `PENDING` transfers.

---

### GAP-RE-05: Non-Atomic Financial Transactions

**Severity**: Critical | **Effort**: Large

The `TransactionService.internalFundTransfer()` method has a critical bug in balance calculation:
```java
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// ^ availableBalance is set to (actualBalance - amount - amount) = double-subtracted
```

The same pattern appears in `utilPayment()`. `availableBalance` is set to `actualBalance` minus the amount again, resulting in a double deduction from available balance.

Additionally:
- No pessimistic/optimistic locking on account entity reads (concurrent transfers could cause race conditions)
- Core banking `@Transactional` is on the service class but individual save calls between debit and credit could partially succeed on database errors

**Best Practice**: Fix the balance calculation bug. Add optimistic locking (`@Version`) to `BankAccountEntity`. Consider using database-level row locking for financial operations.

---

### GAP-RE-06: No Graceful Shutdown Configuration

**Severity**: Low | **Effort**: Small

No graceful shutdown configuration is present. If a service is stopped during an in-flight request, it may be abruptly terminated.

**Best Practice**: Configure `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s`.

---

## Summary Matrix

| ID | Gap | Category | Severity | Effort |
|----|-----|----------|----------|--------|
| GAP-RE-05 | Non-atomic financial transactions / balance calc bug | Resilience | **Critical** | Large |
| GAP-EH-03 | No validation on request bodies | Error Handling | **Critical** | Small |
| GAP-SE-01 | Hardcoded credentials in source code | Security | **Critical** | Small |
| GAP-SE-02 | No input validation or sanitization | Security | **Critical** | Small |
| GAP-RE-01 | No circuit breakers | Resilience | **Critical** | Medium |
| GAP-EH-01 | Inconsistent error response format | Error Handling | **High** | Small |
| GAP-EH-02 | Missing error handling in Feign calls | Error Handling | **High** | Medium |
| GAP-OB-02 | Sensitive data in logs | Observability | **High** | Small |
| GAP-RE-02 | No retry policies | Resilience | **High** | Small |
| GAP-RE-03 | No timeout configuration | Resilience | **High** | Small |
| GAP-TE-01 | Minimal test coverage | Testing | **High** | Large |
| GAP-TE-02 | No integration tests | Testing | **High** | Large |
| GAP-CO-01 | Duplicated base classes across services | Code Org | **Medium** | Medium |
| GAP-SE-03 | Overly permissive actuator endpoints | Security | **Medium** | Small |
| GAP-SE-04 | Keycloak client as static singleton | Security | **Medium** | Small |
| GAP-SE-05 | No CORS configuration | Security | **Medium** | Small |
| GAP-SE-06 | No dependency vulnerability scanning | Security | **Medium** | Small |
| GAP-AD-01 | Raw ResponseEntity without type parameters | API Design | **Medium** | Small |
| GAP-AD-04 | Incomplete OpenAPI documentation | API Design | **Medium** | Small |
| GAP-AD-05 | No pagination metadata in list responses | API Design | **Medium** | Small |
| GAP-OB-01 | No structured logging | Observability | **Medium** | Small |
| GAP-OB-04 | No metrics/Prometheus endpoints | Observability | **Medium** | Medium |
| GAP-RE-04 | No fallback behavior | Resilience | **Medium** | Medium |
| GAP-TE-03 | No contract tests between services | Testing | **Medium** | Large |
| GAP-TE-04 | Context load tests likely failing | Testing | **Medium** | Small |
| GAP-CO-02 | No multi-project Gradle build | Code Org | **Low** | Medium |
| GAP-CO-03 | Inconsistent package structure | Code Org | **Low** | Small |
| GAP-CO-04 | Mappers not Spring-managed | Code Org | **Low** | Small |
| GAP-AD-02 | Non-RESTful URL patterns | API Design | **Low** | Small |
| GAP-AD-03 | No API versioning strategy | API Design | **Low** | Medium |
| GAP-OB-03 | No custom health checks | Observability | **Low** | Small |
| GAP-OB-05 | Incomplete tracing configuration | Observability | **Low** | Small |
| GAP-RE-06 | No graceful shutdown | Resilience | **Low** | Small |
