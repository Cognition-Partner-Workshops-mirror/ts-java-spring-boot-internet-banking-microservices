# Engineering Standards Gap Analysis

This document compares the codebase against engineering best practices across seven dimensions. Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Module Gradle Build

**Severity: Medium | Effort: Medium**

Each service has its own independent `build.gradle`, `settings.gradle`, and Gradle wrapper. There is no root-level `settings.gradle` or `build.gradle` that ties them together as a multi-module project. This leads to:
- Duplicated plugin versions and dependency declarations across all 6 services
- No single command to build/test all services
- Inconsistent Gradle wrapper versions possible across services

### 1.2 No Shared Library for Common Code

**Severity: High | Effort: Medium**

Significant code duplication exists across services:
- `BaseMapper` is copy-pasted in core-banking, fund-transfer, user, and utility-payment services
- `AuditAware` DTO is duplicated in fund-transfer, user, and utility-payment services
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler` are copy-pasted across 3 services with minor variations
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder` are duplicated in fund-transfer, user, and utility-payment
- `CustomFeignClientConfiguration` is duplicated in fund-transfer and utility-payment services
- `AuditConfig` and `AuditorAwareConfig` are duplicated

### 1.3 Inconsistent Package Structure

**Severity: Low | Effort: Small**

While all services use the `com.javatodev.finance` base package, sub-package organization varies:
- Core banking: `model.dto.request`, `model.dto.response`, `model.entity`
- Utility payment: `model.rest.request`, `model.rest.response`, `model.entity`
- Fund transfer: `model.dto.request`, `model.dto.response`, `model.entity`
- User service: `model.dto`, `model.rest.response`, `model.entity`

### 1.4 Mapper Instantiation Anti-Pattern

**Severity: Low | Effort: Small**

Mappers are instantiated via `new` inside `@Service` classes instead of being Spring beans:
```java
private UserMapper userMapper = new UserMapper();  // in UserService
private FundTransferMapper mapper = new FundTransferMapper();  // in FundTransferService
```
This bypasses dependency injection and makes testing harder.

---

## 2. Error Handling

### 2.1 Inconsistent Error Response Format

**Severity: High | Effort: Small**

The `GlobalExceptionHandler` across services returns two different formats:
- For `SimpleBankingGlobalException`: structured `ErrorResponse` with `code` and `message`
- For general `Exception`: raw string `"Exception occur inside API " + e`

The fallback handler exposes full exception details (including stack traces) to the client, which is both a security risk and a poor API contract.

### 2.2 All Errors Return HTTP 400 Bad Request

**Severity: High | Effort: Small**

Every exception — including entity-not-found (should be 404), server errors (should be 500), and validation errors — is mapped to HTTP 400:
```java
return ResponseEntity.badRequest().body(...)
```
This violates HTTP semantics and makes it impossible for clients to distinguish error types.

### 2.3 Missing Raw `ResponseEntity` Type Parameters

**Severity: Medium | Effort: Small**

All controllers use raw `ResponseEntity` without type parameters:
```java
public ResponseEntity getBankAccount(...)  // should be ResponseEntity<BankAccount>
```
This suppresses compile-time type checking and makes the API contract ambiguous.

### 2.4 No Error Handling for Feign Client Failures

**Severity: Critical | Effort: Medium**

When Feign calls fail (network timeout, downstream service unavailable, HTTP errors), there is no:
- Circuit breaker to prevent cascading failures
- Retry logic for transient errors
- Fallback behavior
- Proper error translation (Feign exceptions propagate raw to the client)

The `CustomFeignErrorDecoder` exists in the user-service but is not used in fund-transfer or utility-payment services' Feign configurations.

### 2.5 No Transaction Rollback on Downstream Failure

**Severity: Critical | Effort: Large**

In `FundTransferService.fundTransfer()`:
1. Entity saved with `PENDING` status
2. Feign call to core banking
3. If Feign call fails, the entity remains in `PENDING` forever — no rollback, no retry, no compensation

Same issue in `UtilityPaymentService.utilPayment()` where entity stays `PROCESSING` on failure.

---

## 3. Testing

### 3.1 Minimal Test Coverage

**Severity: Critical | Effort: Large**

| Service | Unit Tests | Integration Tests | Contract Tests |
|---|---|---|---|
| core-banking-service | 3 test classes (AccountServiceTest, TransactionServiceTest, UserServiceTest) | None | None |
| internet-banking-fund-transfer-service | 0 (only empty `ApplicationTests`) | None | None |
| internet-banking-utility-payment-service | 0 (only empty `ApplicationTests`) | None | None |
| internet-banking-user-service | 0 (only empty `ApplicationTests`) | None | None |
| internet-banking-api-gateway | 0 (only empty `ApplicationTests`) | None | None |
| internet-banking-service-registry | 0 (only empty `ApplicationTests`) | None | None |

Only the core-banking-service has meaningful unit tests. No service has integration tests or controller-layer tests.

### 3.2 No Contract Tests Between Services

**Severity: High | Effort: Large**

Three services communicate via Feign clients to core-banking-service. There are no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) to verify API compatibility between services.

### 3.3 Empty ApplicationTests

**Severity: Low | Effort: Small**

Every service has a `*ApplicationTests.java` that attempts to load the full Spring context but will fail without infrastructure (MySQL, Eureka, Config Server). These should either be meaningful integration tests or removed.

### 3.4 No Test Configuration for Feign Clients

**Severity: Medium | Effort: Medium**

There is no WireMock or mock server setup for testing Feign client interactions in any service.

---

## 4. Security

### 4.1 No Input Validation

**Severity: Critical | Effort: Medium**

No `@Valid` / `@NotNull` / `@NotBlank` / `@Positive` annotations on any request DTOs:
```java
public ResponseEntity fundTransfer(@RequestBody FundTransferRequest fundTransferRequest)
// FundTransferRequest has no validation annotations
```
A request with null `fromAccount`, null `toAccount`, or negative `amount` will cause unhandled `NullPointerException` deep in service logic.

### 4.2 Hardcoded Credentials in Docker Compose

**Severity: High | Effort: Small**

Sensitive credentials are committed to version control:
- MySQL root password: `woVERANKliGharym` (in `docker-compose.yml` and `mysql/Dockerfile`)
- MySQL user password: `oPItyPticIAt` (in `privileges.sql`)
- Keycloak admin password: `password` (in `docker-compose.yml`)
- Keycloak DB password: `password` (in `docker-compose.yml`)
- Test credentials in `README.md`: `ib_admin@javatodev.com / 5V7huE3G86uB`

### 4.3 CSRF Disabled Without Justification

**Severity: Medium | Effort: Small**

CSRF protection is explicitly disabled in the API Gateway:
```java
httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable);
```
While acceptable for a pure API (no browser sessions), this should be documented and the security configuration should enforce that no session-based auth is used.

### 4.4 Keycloak Singleton Not Thread-Safe

**Severity: Medium | Effort: Small**

`KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern:
```java
if (keycloakInstance == null) {
    keycloakInstance = KeycloakBuilder.builder()...build();
}
```
This is a classic race condition in a multi-threaded environment.

### 4.5 No Rate Limiting

**Severity: Medium | Effort: Medium**

No rate limiting is configured at the API Gateway or any service level, leaving the system vulnerable to abuse and DoS attacks.

### 4.6 Password Logged / Exposed in Registration

**Severity: High | Effort: Small**

The user registration endpoint accepts `password` in the `User` DTO, which is also logged:
```java
log.info("Creating user with {}", request.toString());
```
The `@Data` annotation generates a `toString()` that includes the password field.

### 4.7 No Dependency Vulnerability Scanning

**Severity: Medium | Effort: Small**

No OWASP dependency-check, Snyk, or similar vulnerability scanning is configured in the build pipeline.

### 4.8 Downstream Services Unauthenticated

**Severity: High | Effort: Medium**

JWT validation only happens at the API Gateway. All downstream services (core-banking, fund-transfer, utility-payment, user) have **no authentication**. They rely solely on the `X-Auth-Id` header set by the gateway's global filter, which can be trivially spoofed if services are accessed directly (bypassing the gateway).

---

## 5. API Design

### 5.1 Inconsistent URL Naming Conventions

**Severity: Low | Effort: Small**

- Core banking uses snake_case path variables: `/bank-account/{account_number}`, `/util-account/{account_name}`
- User service uses numeric ID: `/bank-users/{id}`
- Mixed use of kebab-case and abbreviations: `util-account` vs `utility-payment`

### 5.2 No API Versioning Strategy

**Severity: Medium | Effort: Small**

While all endpoints include `/v1/` in the path, there is no documented versioning strategy, and no mechanism to support multiple API versions simultaneously.

### 5.3 No Pagination Metadata in Responses

**Severity: Medium | Effort: Small**

Paginated endpoints accept `Pageable` parameters but return raw `List<T>` instead of `Page<T>` or a wrapper with pagination metadata (total count, page number, page size, total pages):
```java
public ResponseEntity readUsers(Pageable pageable) {
    return ResponseEntity.ok(userService.readUsers(pageable));
}
```

### 5.4 OpenAPI/Swagger Misconfigured

**Severity: Medium | Effort: Small**

All services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but this is the **WebFlux** variant. Only the API Gateway uses WebFlux; the business services use Spring MVC. The correct dependency for MVC services should be `springdoc-openapi-starter-webmvc-ui`.

Additionally, while `@Tag` and `@Operation` annotations are present on controllers, there are no `@Schema` annotations on DTOs and no `@ApiResponse` annotations for error cases.

### 5.5 No Filtering or Sorting Support

**Severity: Low | Effort: Medium**

List endpoints only support basic pagination. There is no filtering (e.g., by date range, status, account) or explicit sorting support.

### 5.6 POST Endpoints Return 200 Instead of 201

**Severity: Low | Effort: Small**

Creation endpoints (user registration, fund transfer, utility payment) return HTTP 200 OK instead of the more semantically correct HTTP 201 Created.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Severity: Medium | Effort: Small**

- Some controllers use `@Slf4j` and log request details; others don't
- Log messages contain sensitive data (user details, request bodies including passwords)
- No structured logging (JSON format) configured
- No correlation ID / request ID in logs (only tracing via Zipkin)

### 6.2 No Custom Health Checks

**Severity: Medium | Effort: Small**

Services rely on default Actuator health endpoints. No custom health indicators for:
- Database connectivity
- Feign client availability (downstream service health)
- Keycloak connectivity
- Config server connectivity

### 6.3 No Metrics Endpoints

**Severity: Medium | Effort: Small**

While `spring-boot-starter-actuator` is included, there is no:
- Prometheus metrics endpoint (`/actuator/prometheus`)
- Micrometer registry for Prometheus configured
- Custom business metrics (transfer count, payment volume, error rates)
- Grafana dashboards or alerting

### 6.4 Zipkin Tracing Configuration Externalized

**Severity: Low | Effort: Small**

Tracing dependencies are present in all services, but actual Zipkin URL configuration is externalized to the config server. If the config server is unavailable, tracing silently fails with no indication.

### 6.5 No Centralized Log Aggregation

**Severity: Medium | Effort: Medium**

No ELK stack, Loki, or similar log aggregation is configured. In a Docker Compose deployment with 10+ containers, troubleshooting requires accessing individual container logs.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Severity: Critical | Effort: Medium**

No circuit breaker pattern (Resilience4j, Hystrix) is implemented anywhere. If core-banking-service goes down:
- Fund transfer service will block indefinitely on Feign calls
- Utility payment service will block indefinitely
- User service will block on user verification calls
- Cascading failure will propagate to all services

### 7.2 No Retry Policies

**Severity: High | Effort: Small**

No retry configuration for:
- Feign clients (transient network failures)
- Database connections
- Config server bootstrap (services fail to start if config server is momentarily unavailable)

### 7.3 No Timeout Configuration

**Severity: High | Effort: Small**

No explicit timeout configuration for:
- Feign client connection/read timeouts (uses Feign defaults)
- Database connection pool timeouts
- Gateway route timeouts

### 7.4 No Fallback Behavior

**Severity: High | Effort: Medium**

No fallback/degraded mode when downstream services are unavailable:
- No cached responses
- No graceful degradation
- No queued-for-later patterns

### 7.5 Non-Atomic Financial Transactions

**Severity: Critical | Effort: Large**

The `TransactionService.internalFundTransfer()` method performs debit and credit as separate `bankAccountRepository.save()` calls. While annotated with `@Transactional`, the available/actual balance calculation has a bug:
```java
// After subtract: actualBalance = 200 - 100 = 100
fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
// Bug: availableBalance = 100 - 100 = 0 (double subtraction)
fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
```
The available balance is computed from the *already-debited* actual balance, resulting in double deduction. Same bug exists on the credit side and in `utilPayment()`.

### 7.6 No Idempotency for Financial Operations

**Severity: Critical | Effort: Medium**

Fund transfer and utility payment POST endpoints have no idempotency keys. If a client retries a failed request (e.g., network timeout after successful processing), the transaction will be executed twice.

### 7.7 wait-for-it.sh with Fixed Timeouts

**Severity: Low | Effort: Small**

Container startup uses `wait-for-it.sh` with 50-second timeouts. If dependencies take longer, services fail to start with no retry mechanism.

---

## Summary Table

| Category | Critical | High | Medium | Low |
|---|---|---|---|---|
| Code Organization | 0 | 1 | 1 | 2 |
| Error Handling | 2 | 2 | 1 | 0 |
| Testing | 1 | 1 | 1 | 1 |
| Security | 1 | 3 | 3 | 0 |
| API Design | 0 | 0 | 3 | 3 |
| Observability | 0 | 0 | 4 | 1 |
| Resilience | 3 | 2 | 0 | 1 |
| **Total** | **7** | **9** | **13** | **8** |
