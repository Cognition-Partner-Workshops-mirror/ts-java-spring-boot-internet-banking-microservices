# Internet Banking Microservices — Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices across 7 categories, identifying gaps with severity ratings and effort estimates.

---

## 1. Code Organization

### 1.1 Duplicated Classes Across Services

**Gap:** Exception classes (`SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ErrorResponse`), DTOs (`FundTransferRequest`, `UtilityPaymentRequest`, `AuditAware`), mapper base classes (`BaseMapper`), Feign configuration (`CustomFeignClientConfiguration`), and filter classes (`AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`) are copy-pasted across 3–4 services with minor variations.

- **Severity:** High
- **Effort:** Medium
- **Evidence:** `ErrorResponse.java` exists in `core-banking-service`, `fund-transfer-service`, and `utility-payment-service` with near-identical implementations. `AuditAware.java` is duplicated in `fund-transfer-service`, `user-service`, and `utility-payment-service`. `BaseMapper.java` is duplicated across 3 services.

### 1.2 No Multi-Module Build

**Gap:** Each service is a standalone Gradle project with its own `gradlew` wrapper. There is no root `settings.gradle` or multi-module build to enforce consistent dependency versions, plugin versions, or shared build logic.

- **Severity:** Medium
- **Effort:** Medium
- **Evidence:** 7 independent `build.gradle` files, each declaring Spring Boot `3.2.4` and Spring Cloud `2023.0.0` independently. Version drift risk exists.

### 1.3 Inconsistent Code Style

**Gap:** Mixed indentation (tabs vs. spaces) across build.gradle files. Some services use `@Builder` for `ErrorResponse` (core-banking) while others use `@AllArgsConstructor` constructor patterns. Mapper instantiation mixes `new XxxMapper()` in field initializers (not injectable) vs. other patterns.

- **Severity:** Low
- **Effort:** Small
- **Evidence:** `internet-banking-api-gateway/build.gradle` uses tab indentation, while `internet-banking-service-registry/build.gradle` uses spaces.

### 1.4 Package Structure Inconsistency

**Gap:** All services share the same root package `com.javatodev.finance`, which could cause classpath conflicts if services were ever collocated. Sub-package organization varies: `model.repository` in some services vs. `repository` in others; `model.rest.response` vs. `model.dto.response`.

- **Severity:** Low
- **Effort:** Medium
- **Evidence:** `core-banking-service` uses `repository.BankAccountRepository`, while `fund-transfer-service` uses `model.repository.FundTransferRepository`. `user-service` uses `model.rest.response.UserResponse` while `fund-transfer-service` uses `model.dto.response.FundTransferResponse`.

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400 for Everything

**Gap:** All `GlobalExceptionHandler` implementations catch `Exception.class` and return HTTP 400 (Bad Request) with a raw string body (`"Exception occur inside API " + e`). This is incorrect — server errors should return 5xx, not found should return 404, etc. Stack trace information is also leaked to the client.

- **Severity:** Critical
- **Effort:** Small
- **Evidence:** All three `GlobalExceptionHandler.java` files:
  ```java
  @ExceptionHandler({Exception.class})
  protected ResponseEntity handleException(Exception e, Locale locale) {
      return ResponseEntity.badRequest().body("Exception occur inside API " + e);
  }
  ```

### 2.2 EntityNotFoundException Returns 400 Instead of 404

**Gap:** `EntityNotFoundException` extends `SimpleBankingGlobalException`, which is handled by the global handler that always returns `ResponseEntity.badRequest()` (HTTP 400). A not-found entity should return HTTP 404.

- **Severity:** High
- **Effort:** Small
- **Evidence:** `GlobalExceptionHandler.handleGlobalException()` always returns `ResponseEntity.badRequest()` regardless of exception type.

### 2.3 No Raw Type Parameterization on ResponseEntity

**Gap:** Controllers return `ResponseEntity` without type parameters (raw type), losing compile-time type safety and making API documentation less useful.

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** `AccountController.java`, `TransactionController.java`, `FundTransferController.java`, `UtilityPaymentController.java` — all return raw `ResponseEntity`.

### 2.4 Inconsistent Error Response Format

**Gap:** Structured errors use `ErrorResponse(code, message)` but the generic catch-all returns a plain string. Client consumers cannot reliably parse error responses.

- **Severity:** High
- **Effort:** Small
- **Evidence:** Structured: `ErrorResponse.builder().code(...).message(...)` vs. plain string: `"Exception occur inside API " + e`.

### 2.5 No Feign Error Handling in Fund Transfer / Utility Payment Services

**Gap:** When the Feign call to `core-banking-service` fails (e.g., network error, 4xx/5xx response), there is no error handling or fallback. The `FundTransferEntity` will remain in `PENDING` state with no mechanism for recovery. Only `user-service` has a `CustomFeignErrorDecoder`.

- **Severity:** Critical
- **Effort:** Medium
- **Evidence:** `FundTransferService.fundTransfer()` calls `bankingCoreFeignClient.fundTransfer(request)` without try-catch. If the call fails, the entity is left in `PENDING` state forever.

---

## 3. Testing

### 3.1 Only core-banking-service Has Meaningful Unit Tests

**Gap:** Only `core-banking-service` has service-layer unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). Other services only have empty Spring context-load tests (`*ApplicationTests`).

- **Severity:** High
- **Effort:** Large
- **Evidence:**
  - `core-banking-service`: 3 test files with actual assertions (AccountServiceTest: 6 tests, TransactionServiceTest: 5+ tests, UserServiceTest: 3 tests)
  - `fund-transfer-service`: only `InternetBankingFundTransferServiceApplicationTests` (context load)
  - `user-service`: only `InternetBankingUserServiceApplicationTests` (context load)
  - `utility-payment-service`: only `InternetBankingUtilityPaymentServiceApplicationTests` (context load)

### 3.2 No Integration Tests

**Gap:** No `@SpringBootTest` with real database, no Testcontainers, no end-to-end flow testing. The existing tests use mocks only.

- **Severity:** High
- **Effort:** Large
- **Evidence:** No integration test directories, no Testcontainers dependency, no `@SpringBootTest(webEnvironment = RANDOM_PORT)` usage.

### 3.3 No Contract Tests Between Services

**Gap:** Services communicate via Feign but there are no contract tests (e.g., Spring Cloud Contract, Pact) to validate API compatibility between producers and consumers.

- **Severity:** Medium
- **Effort:** Large
- **Evidence:** No `spring-cloud-contract` dependency or consumer-driven contract testing.

### 3.4 No Controller / API Layer Tests

**Gap:** No `@WebMvcTest` or MockMvc-based tests to verify request/response serialization, HTTP status codes, or input validation behavior.

- **Severity:** Medium
- **Effort:** Medium
- **Evidence:** Zero `@WebMvcTest` annotations in the codebase.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

**Gap:** Database passwords, Keycloak admin credentials, and MySQL root password are hardcoded in `docker-compose.yml` and `privileges.sql` files committed to source control.

- **Severity:** Critical
- **Effort:** Small
- **Evidence:**
  - `docker-compose.yml`: `MYSQL_ROOT_PASSWORD: woVERANKliGharym`, `KEYCLOAK_ADMIN_PASSWORD: password`, `KC_DB_PASSWORD: password`
  - `privileges.sql`: `IDENTIFIED BY 'oPItyPticIAt'`

### 4.2 No Input Validation on Request Bodies

**Gap:** No `@Valid` / `@NotNull` / `@Size` / `@Min` annotations on any request DTOs. Null amounts, empty account numbers, or negative transfer amounts are not validated.

- **Severity:** Critical
- **Effort:** Small
- **Evidence:** `FundTransferRequest`, `UtilityPaymentRequest`, `User` — none have Jakarta Validation annotations. Controllers accept `@RequestBody` without `@Valid`.

### 4.3 Actuator Endpoints Publicly Exposed

**Gap:** All `/actuator/**` endpoints are explicitly permitted without authentication in `SecurityConfiguration.java`. This may expose sensitive operational data (env, beans, configprops, heapdump).

- **Severity:** High
- **Effort:** Small
- **Evidence:** `SecurityConfiguration.java`:
  ```java
  exchanges.pathMatchers("/actuator/**").permitAll()
  ```

### 4.4 Downstream Services Have No Authentication

**Gap:** Only the API Gateway validates JWT tokens. Downstream services (`core-banking-service`, `fund-transfer-service`, `user-service`, `utility-payment-service`) have no security configuration — if they are accessible directly (not through the gateway), they are completely open.

- **Severity:** High
- **Effort:** Medium
- **Evidence:** No `spring-boot-starter-security` dependency in `core-banking-service`, `fund-transfer-service`, or `utility-payment-service` `build.gradle` files.

### 4.5 Keycloak Client Secret in Configuration

**Gap:** Keycloak client secret is fetched from Spring Cloud Config properties (`app.config.keycloak.client-secret`). The remote config Git repository may have the secret in plain text.

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** `KeycloakProperties.java`: `@Value("${app.config.keycloak.client-secret}")`.

### 4.6 Swagger/OpenAPI Dependency Mismatch

**Gap:** Services use `springdoc-openapi-starter-webflux-ui` but are Spring MVC (not WebFlux) applications. This is the wrong starter — should be `springdoc-openapi-starter-webmvc-ui`.

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** `core-banking-service`, `fund-transfer-service`, `user-service`, `utility-payment-service` all use `spring-boot-starter-web` (MVC) but depend on `springdoc-openapi-starter-webflux-ui:2.1.0`.

---

## 5. API Design

### 5.1 No Pagination Metadata in Responses

**Gap:** Paginated endpoints return `List<T>` instead of a page wrapper with total count, page number, and page size. Clients have no way to know total records or navigate pages.

- **Severity:** High
- **Effort:** Small
- **Evidence:** `UserService.readUsers()` returns `userMapper.convertToDtoList(allUsersInDb.getContent())` — discards Page metadata. Same pattern in `FundTransferService` and `UtilityPaymentService`.

### 5.2 No API Versioning Strategy

**Gap:** APIs use `/api/v1/` prefix but there is no documented versioning strategy, no header-based versioning, and no mechanism for introducing breaking changes.

- **Severity:** Low
- **Effort:** Small
- **Evidence:** URL path versioning (`/api/v1/`) is used but with no guidance for future versions.

### 5.3 Inconsistent Resource Naming

**Gap:** Endpoint naming is inconsistent — `bank-account` uses hyphens, `util-account` abbreviates "utility", transfer uses `/api/v1/transfer` (singular) while payment uses `/api/v1/utility-payment` (singular with hyphen).

- **Severity:** Low
- **Effort:** Small
- **Evidence:** `/api/v1/account/bank-account/`, `/api/v1/account/util-account/`, `/api/v1/transfer`, `/api/v1/utility-payment`, `/api/v1/bank-users`.

### 5.4 No Filtering or Search Parameters

**Gap:** List endpoints only support pagination (`page`, `size`, `sort`). No filtering by status, date range, account number, or amount.

- **Severity:** Medium
- **Effort:** Medium
- **Evidence:** `GET /api/v1/transfer` and `GET /api/v1/utility-payment` only accept `Pageable` parameters.

### 5.5 Missing DELETE/PUT Operations

**Gap:** User update uses `PATCH` but there are no `DELETE` operations for any resource. No ability to cancel a transfer or reverse a payment.

- **Severity:** Low
- **Effort:** Medium
- **Evidence:** Only `GET`, `POST`, and one `PATCH` operation across all controllers.

---

## 6. Observability

### 6.1 Inconsistent Logging

**Gap:** Some controllers log incoming requests (`log.info("Got fund transfer request...")`), others don't. No structured logging format (JSON), no MDC context (correlation IDs), and no log level configuration.

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** `UserController` in `user-service` logs all operations, but `UtilityPaymentController` has no logging at all.

### 6.2 No Custom Health Checks

**Gap:** While `spring-boot-starter-actuator` is included, there are no custom health indicators for downstream dependencies (MySQL connectivity, Keycloak availability, Feign client health).

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** No `HealthIndicator` implementations in the codebase. Default actuator health only shows basic status.

### 6.3 No Metrics Endpoints Beyond Default

**Gap:** No custom Micrometer metrics for business operations (e.g., transfers/second, payment success rate, average transfer amount). Only default JVM/HTTP metrics from Actuator.

- **Severity:** Medium
- **Effort:** Medium
- **Evidence:** No `@Timed`, `MeterRegistry`, or custom metric registrations in any service.

### 6.4 Zipkin Tracing Only — No Prometheus Integration

**Gap:** While `spring-boot-starter-actuator` is present, there is no Prometheus registry dependency (`micrometer-registry-prometheus`). The README mentions Prometheus but it is not configured.

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** No `micrometer-registry-prometheus` in any `build.gradle`. README lists Prometheus in the tech stack but it is not actually integrated.

### 6.5 Config Server and Service Registry Lack Tracing

**Gap:** `internet-banking-config-server` and `internet-banking-service-registry` do not include Zipkin/Micrometer tracing dependencies.

- **Severity:** Low
- **Effort:** Small
- **Evidence:** Neither `build.gradle` file includes `micrometer-tracing-bridge-brave` or `zipkin-reporter-brave`.

---

## 7. Resilience

### 7.1 No Circuit Breakers

**Gap:** Feign clients call `core-banking-service` without any circuit-breaker mechanism. If the core service goes down, cascading failures will affect all consuming services.

- **Severity:** Critical
- **Effort:** Medium
- **Evidence:** No `spring-cloud-starter-circuitbreaker-resilience4j` or `@CircuitBreaker` annotations. Feign clients have no fallback classes defined.

### 7.2 No Retry Policies

**Gap:** No retry configuration for transient failures on Feign calls, database connections, or Keycloak API calls.

- **Severity:** High
- **Effort:** Small
- **Evidence:** No `spring-retry`, `@Retryable`, or Resilience4j retry configuration.

### 7.3 No Timeout Configuration

**Gap:** No explicit connection/read timeouts configured for Feign clients. Default timeouts may be too long, causing thread pool exhaustion under load.

- **Severity:** High
- **Effort:** Small
- **Evidence:** No `feign.client.config.default.connectTimeout` or `readTimeout` in any configuration file. `CustomFeignClientConfiguration` only sets log level.

### 7.4 No Fallback Behavior

**Gap:** When the Feign call fails in `FundTransferService` or `UtilityPaymentService`, there is no fallback — the transaction entity remains in `PENDING`/`PROCESSING` state with no recovery mechanism, dead-letter handling, or compensating transaction.

- **Severity:** Critical
- **Effort:** Large
- **Evidence:** `FundTransferService.fundTransfer()` does not handle Feign exceptions. There is no scheduled job to reprocess stuck transactions.

### 7.5 No Idempotency

**Gap:** Fund transfer and utility payment operations have no idempotency keys. Duplicate requests (e.g., from client retries) will create duplicate transfers and double-debit accounts.

- **Severity:** Critical
- **Effort:** Medium
- **Evidence:** `POST /api/v1/transfer` has no idempotency-key header or duplicate detection logic. Each request generates a new UUID `transactionId`.

### 7.6 Non-Atomic Balance Updates in Core Banking

**Gap:** In `TransactionService.internalFundTransfer()`, the debit and credit operations are in the same `@Transactional` block but `availableBalance` is calculated incorrectly — it subtracts amount twice from `actualBalance` after already subtracting.

- **Severity:** Critical
- **Effort:** Small
- **Evidence:**
  ```java
  fromBankAccountEntity.setActualBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
  fromBankAccountEntity.setAvailableBalance(fromBankAccountEntity.getActualBalance().subtract(amount));
  ```
  After line 1, `actualBalance = original - amount`. Line 2 then sets `availableBalance = (original - amount) - amount = original - 2*amount`. Same bug exists in `utilPayment()` and for the credit side (`toBankAccountEntity`).

### 7.7 Keycloak Client Singleton Not Thread-Safe

**Gap:** `KeycloakProperties.getInstance()` uses a non-synchronized singleton pattern with a static field. Under concurrent access, multiple Keycloak clients could be created (classic double-checked locking issue).

- **Severity:** Medium
- **Effort:** Small
- **Evidence:** `KeycloakProperties.java`:
  ```java
  private static Keycloak keycloakInstance = null;
  public Keycloak getInstance() {
      if (keycloakInstance == null) { keycloakInstance = KeycloakBuilder.builder()...build(); }
      return keycloakInstance;
  }
  ```

---

## Summary Table

| # | Category | Gap | Severity | Effort |
|---|---|---|---|---|
| 1.1 | Code Organization | Duplicated classes across services | High | Medium |
| 1.2 | Code Organization | No multi-module build | Medium | Medium |
| 1.3 | Code Organization | Inconsistent code style | Low | Small |
| 1.4 | Code Organization | Package structure inconsistency | Low | Medium |
| 2.1 | Error Handling | Generic catch-all returns 400 for everything | Critical | Small |
| 2.2 | Error Handling | EntityNotFoundException returns 400 not 404 | High | Small |
| 2.3 | Error Handling | Raw ResponseEntity types | Medium | Small |
| 2.4 | Error Handling | Inconsistent error response format | High | Small |
| 2.5 | Error Handling | No Feign error handling in fund-transfer/utility-payment | Critical | Medium |
| 3.1 | Testing | Only core-banking has meaningful tests | High | Large |
| 3.2 | Testing | No integration tests | High | Large |
| 3.3 | Testing | No contract tests | Medium | Large |
| 3.4 | Testing | No controller/API layer tests | Medium | Medium |
| 4.1 | Security | Hardcoded credentials in source | Critical | Small |
| 4.2 | Security | No input validation | Critical | Small |
| 4.3 | Security | Actuator endpoints publicly exposed | High | Small |
| 4.4 | Security | Downstream services have no authentication | High | Medium |
| 4.5 | Security | Keycloak client secret in config | Medium | Small |
| 4.6 | Security | Wrong OpenAPI starter (webflux instead of webmvc) | Medium | Small |
| 5.1 | API Design | No pagination metadata | High | Small |
| 5.2 | API Design | No API versioning strategy | Low | Small |
| 5.3 | API Design | Inconsistent resource naming | Low | Small |
| 5.4 | API Design | No filtering/search parameters | Medium | Medium |
| 5.5 | API Design | Missing DELETE/PUT operations | Low | Medium |
| 6.1 | Observability | Inconsistent logging | Medium | Small |
| 6.2 | Observability | No custom health checks | Medium | Small |
| 6.3 | Observability | No custom business metrics | Medium | Medium |
| 6.4 | Observability | Prometheus not configured | Medium | Small |
| 6.5 | Observability | Config Server/Service Registry lack tracing | Low | Small |
| 7.1 | Resilience | No circuit breakers | Critical | Medium |
| 7.2 | Resilience | No retry policies | High | Small |
| 7.3 | Resilience | No timeout configuration | High | Small |
| 7.4 | Resilience | No fallback behavior | Critical | Large |
| 7.5 | Resilience | No idempotency | Critical | Medium |
| 7.6 | Resilience | Non-atomic balance calculation bug | Critical | Small |
| 7.7 | Resilience | Keycloak singleton not thread-safe | Medium | Small |

### Severity Distribution

| Severity | Count |
|---|---|
| Critical | 9 |
| High | 9 |
| Medium | 13 |
| Low | 5 |

### Critical Items (Require Immediate Attention)

1. Generic exception handler returns 400 for all errors (including 5xx) and leaks stack traces
2. No input validation on any request body
3. Hardcoded credentials in Docker Compose files
4. No circuit breakers on Feign clients
5. No fallback/recovery for failed Feign calls
6. No idempotency on financial transactions
7. Balance calculation bug (double subtraction) in `TransactionService`
8. No Feign error handling in fund-transfer and utility-payment services
