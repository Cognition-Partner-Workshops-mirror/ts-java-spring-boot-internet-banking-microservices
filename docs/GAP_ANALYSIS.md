# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against industry engineering best practices. Each gap is rated by **severity** and **remediation effort**.

**Severity Scale**: Critical > High > Medium > Low
**Effort Scale**: Small (< 1 day) | Medium (1-3 days) | Large (3+ days)

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| | |
|---|---|
| **Finding** | Each microservice is an independent Gradle project with its own wrapper, `build.gradle`, and `settings.gradle`. There is no root `settings.gradle` or `build.gradle` to unify builds, enforce consistent dependency versions, or share plugins. |
| **Impact** | Dependency version drift across services (e.g., MySQL connector pinned separately in each `build.gradle`). No single command to build/test all services. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Duplicated Code Across Services

| | |
|---|---|
| **Finding** | Significant code duplication exists across the business services: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `CustomFeignClientConfiguration`, and `TransactionStatus` are copy-pasted into each service with minor variations. |
| **Impact** | Maintenance burden — bug fixes or improvements must be applied in 3-4 places. Inconsistencies already exist (e.g., `ErrorResponse` uses `@Builder` in some services and a constructor in others). |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure

| | |
|---|---|
| **Finding** | The package layout differs across services. Core-banking uses `repository/` directly under `com.javatodev.finance`; user-service uses `model.repository/`; fund-transfer uses `model.repository/`; utility-payment uses `repository/` at the top. Exception classes also vary: core-banking has `GlobalErrorCode` while other services inline error codes or omit them. |
| **Impact** | Developers must learn different conventions when switching between services. |
| **Severity** | Low |
| **Effort** | Small |

### 1.4 Mappers Instantiated Inline (Not Spring Beans)

| | |
|---|---|
| **Finding** | All mapper classes (e.g., `BankAccountMapper`, `UserMapper`, `FundTransferMapper`) are instantiated via `new` within service classes rather than being managed as Spring beans. |
| **Impact** | Cannot leverage Spring lifecycle, AOP, or dependency injection in mappers. Inconsistent with the rest of the application's DI-based architecture. |
| **Severity** | Low |
| **Effort** | Small |

---

## 2. Error Handling

### 2.1 Generic Exception Catch-All Returns 400

| | |
|---|---|
| **Finding** | Every `GlobalExceptionHandler` has an `@ExceptionHandler({Exception.class})` that returns `400 Bad Request` with the raw exception as a string: `"Exception occur inside API " + e`. This applies to all unhandled exceptions including NPEs, database errors, and network failures. |
| **Impact** | **Security risk**: Exposes internal stack traces and class names to the client. **Incorrect semantics**: Server errors (5xx) are returned as 400. **Debugging difficulty**: Client has no structured error information. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.2 Inconsistent Error Response Structure

| | |
|---|---|
| **Finding** | For `SimpleBankingGlobalException`, the response is a structured `ErrorResponse { code, message }`. For all other exceptions, the response is a plain string. The `ErrorResponse` class itself varies: some services use `@Builder`, the fund-transfer service uses a constructor. |
| **Impact** | API consumers cannot reliably parse error responses. |
| **Severity** | High |
| **Effort** | Small |

### 2.3 No HTTP Status Code Granularity

| | |
|---|---|
| **Finding** | All custom exceptions return `400 Bad Request` regardless of the actual error type. `EntityNotFoundException` should return `404`, `InsufficientFundsException` could return `422`, and authentication/authorization errors should return `401`/`403`. |
| **Impact** | Clients cannot programmatically distinguish error types without parsing the response body. |
| **Severity** | High |
| **Effort** | Small |

### 2.4 No Error Handling for Feign Client Failures

| | |
|---|---|
| **Finding** | Only the user-service has a `CustomFeignErrorDecoder`. The fund-transfer and utility-payment services reference `CustomFeignClientConfiguration` but their implementations are minimal. There are no circuit breakers or fallback mechanisms for inter-service calls. If core-banking-service is down, the calling services will fail with unhandled exceptions. |
| **Impact** | Cascading failures across the system. Poor error messages for users when downstream services fail. |
| **Severity** | Critical |
| **Effort** | Medium |

### 2.5 `SimpleBankingGlobalException` Constructor Issue

| | |
|---|---|
| **Finding** | `SimpleBankingGlobalException` has a 2-arg constructor `(String code, String message)` and also extends `RuntimeException`. The `@AllArgsConstructor` from Lombok sets `code` and `message` fields but does NOT call `super(message)`, meaning `getMessage()` from `RuntimeException` returns `null` while the `message` field has the value. This can cause confusion in logging and exception handling. |
| **Impact** | Subtle bugs when code relies on `Throwable.getMessage()`. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 3. Testing

### 3.1 Minimal Test Coverage

| | |
|---|---|
| **Finding** | Only `core-banking-service` has meaningful unit tests (AccountServiceTest, TransactionServiceTest, UserServiceTest). All other services have only boilerplate `contextLoads()` tests that verify the Spring context starts — but these fail without infrastructure (MySQL, Config Server, Eureka). |
| **Impact** | No automated verification of business logic in user-service, fund-transfer-service, or utility-payment-service. Regressions can be introduced silently. |
| **Severity** | Critical |
| **Effort** | Large |

### 3.2 No Integration Tests

| | |
|---|---|
| **Finding** | There are no integration tests that verify the interaction between services, database operations with a real (or testcontainers) database, or end-to-end API flows. |
| **Impact** | Inter-service contracts are not verified. Database schema changes can break services without detection. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests

| | |
|---|---|
| **Finding** | There are no contract tests (e.g., Spring Cloud Contract, Pact) between the Feign clients and their provider services. The Feign client interfaces duplicate the API contract manually with no verification. |
| **Impact** | A change to core-banking-service's API can silently break fund-transfer and utility-payment services. |
| **Severity** | High |
| **Effort** | Large |

### 3.4 `@SpringBootTest` in Non-Unit Tests

| | |
|---|---|
| **Finding** | The `contextLoads()` tests use `@SpringBootTest` which attempts to load the full application context including database connections, Eureka registration, and Config Server. Without a proper test profile that disables these, the tests fail in isolation. |
| **Impact** | Tests cannot run in CI without infrastructure. Defeats the purpose of automated testing. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 4. Security

### 4.1 No Input Validation

| | |
|---|---|
| **Finding** | None of the request DTOs use Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc.). Controllers accept `@RequestBody` without `@Valid`. A fund transfer with a negative amount, null accounts, or a zero amount would pass through to the service layer. |
| **Impact** | **Critical for a banking application**. Invalid data can corrupt account balances. Null pointer exceptions on missing fields. No defense against malformed requests. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.2 Hardcoded Credentials in Source Code

| | |
|---|---|
| **Finding** | Database passwords are committed in `docker-compose.yml` (`MYSQL_ROOT_PASSWORD: woVERANKliGharym`), `privileges.sql` (`IDENTIFIED BY 'oPItyPticIAt'`), and Keycloak config (`KEYCLOAK_ADMIN_PASSWORD: password`). Test credentials are in `README.md`. |
| **Impact** | Credentials exposed in version control. Any developer or reader of the repo has database access. |
| **Severity** | High |
| **Effort** | Small |

### 4.3 Overly Broad Database Permissions

| | |
|---|---|
| **Finding** | The `javatodev_development` MySQL user has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES on *.*` — full DDL and DML access to ALL databases. |
| **Impact** | A SQL injection in one service could affect all databases. Violates principle of least privilege. |
| **Severity** | High |
| **Effort** | Small |

### 4.4 Keycloak Singleton Not Thread-Safe

| | |
|---|---|
| **Finding** | `KeycloakProperties.getInstance()` uses a non-synchronized singleton pattern (`if (keycloakInstance == null)`). In a multi-threaded environment, multiple instances could be created. |
| **Impact** | Potential resource leaks or unexpected behavior under concurrent requests. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.5 No Rate Limiting

| | |
|---|---|
| **Finding** | The API Gateway does not implement rate limiting. There is no protection against brute-force attacks on the registration endpoint (which is publicly accessible). |
| **Impact** | Vulnerable to abuse, DDoS, and credential stuffing on the public registration endpoint. |
| **Severity** | High |
| **Effort** | Medium |

### 4.6 Password Logged in Request Objects

| | |
|---|---|
| **Finding** | The user registration controller logs `request.toString()` which includes the user's password since the `User` DTO uses `@Data` (auto-generated `toString()`). |
| **Impact** | Passwords appear in plaintext in application logs. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.7 No Dependency Vulnerability Scanning

| | |
|---|---|
| **Finding** | No OWASP dependency-check plugin, Snyk, or Trivy integration exists in the build pipeline or CI. |
| **Impact** | Vulnerable dependencies may go undetected. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| | |
|---|---|
| **Finding** | Most controller methods return `ResponseEntity` without generic type parameters (e.g., `ResponseEntity` instead of `ResponseEntity<BankAccount>`). |
| **Impact** | OpenAPI/Swagger documentation cannot infer response types. IDE support and compile-time safety are reduced. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.2 No API Versioning Strategy

| | |
|---|---|
| **Finding** | While endpoints use `/api/v1/`, there is no documented versioning strategy, no v2 support mechanism, and no content negotiation or header-based versioning. |
| **Impact** | Breaking changes will require ad-hoc migration strategies. |
| **Severity** | Low |
| **Effort** | Small |

### 5.3 Inconsistent REST Conventions

| | |
|---|---|
| **Finding** | Mixed naming: `bank-account` and `util-account` (abbreviation), `bank-users` (plural with prefix), `transfer` (no prefix). PATCH is used for user updates but the update replaces the full status rather than patching fields. Path parameters use `snake_case` (`account_number`) while Java uses camelCase. |
| **Impact** | Confusing API surface for consumers. Inconsistent developer experience. |
| **Severity** | Low |
| **Effort** | Small |

### 5.4 No Pagination Metadata in Responses

| | |
|---|---|
| **Finding** | List endpoints accept `Pageable` parameters but return `List<T>` instead of `Page<T>`. Clients receive the data but have no information about total pages, total elements, or current page. |
| **Impact** | Clients cannot implement proper pagination UI or know when they've reached the last page. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.5 OpenAPI/Swagger Dependency Mismatch

| | |
|---|---|
| **Finding** | All services include `springdoc-openapi-starter-webflux-ui:2.1.0` but only the API Gateway uses WebFlux. The business services use Spring MVC (spring-boot-starter-web). The webflux starter may not generate correct documentation for MVC controllers. |
| **Impact** | Swagger UI may not work correctly or may show incomplete API documentation. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 6. Observability

### 6.1 Inconsistent Logging Practices

| | |
|---|---|
| **Finding** | Some controllers use `@Slf4j` and log request details; others do not. Log levels and formats are inconsistent. There is no structured logging (JSON) configuration. Some services log `toString()` of request objects (including sensitive data), while others log specific fields. |
| **Impact** | Difficult to trace requests across services using log correlation. Sensitive data in logs. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.2 No Health Check Customization

| | |
|---|---|
| **Finding** | While `spring-boot-starter-actuator` is included in all services, there are no custom health indicators for downstream dependencies (MySQL connectivity, Keycloak availability, Eureka registration status). Default health endpoints may not reflect the actual readiness of the service. |
| **Impact** | Container orchestrators (Docker, Kubernetes) cannot accurately determine service health. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Metrics Endpoints or Dashboards

| | |
|---|---|
| **Finding** | Despite including Micrometer dependencies and mentioning Prometheus in the technology stack, there is no Prometheus scrape configuration, no Grafana dashboards, and no custom metrics for business operations (e.g., transfer count, payment success rate). |
| **Impact** | No operational visibility into system performance or business KPIs. |
| **Severity** | Medium |
| **Effort** | Medium |

### 6.4 Distributed Tracing Not Fully Verified

| | |
|---|---|
| **Finding** | Zipkin and Micrometer Brave dependencies are included and Zipkin is in Docker Compose, but there is no configuration for sampling rates, no verification that trace context propagates through the API Gateway to downstream services, and no documentation on how to use the tracing setup. |
| **Impact** | Tracing may not work end-to-end, reducing its value for debugging production issues. |
| **Severity** | Low |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers

| | |
|---|---|
| **Finding** | There are no circuit breakers (Resilience4j, Hystrix) on any inter-service calls. If core-banking-service becomes slow or unresponsive, all callers will block indefinitely (no timeout configuration visible) and eventually exhaust their thread pools. |
| **Impact** | A single slow service can cascade failures across the entire system. Thread pool exhaustion can bring down all services. |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.2 No Retry Policies

| | |
|---|---|
| **Finding** | No retry configuration exists for Feign clients. Transient network errors or temporary service unavailability will result in immediate failure. |
| **Impact** | Reduced reliability. Operations that could succeed on retry fail permanently. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeout Configuration

| | |
|---|---|
| **Finding** | No connection or read timeouts are configured for Feign clients, database connections, or Keycloak admin client. Default timeouts (often infinite or very long) apply. |
| **Impact** | Requests can hang indefinitely, consuming server resources. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior

| | |
|---|---|
| **Finding** | When a downstream service fails, the error propagates directly to the client. There are no fallback responses, graceful degradation, or cached responses. |
| **Impact** | Users see raw error messages when any service in the chain fails. No partial functionality during outages. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 Non-Atomic Transaction Handling

| | |
|---|---|
| **Finding** | The fund transfer flow spans two services (fund-transfer → core-banking). If the core-banking call succeeds but the subsequent status update in the fund-transfer service fails, the system is in an inconsistent state. There is no saga pattern, compensation logic, or idempotency keys. Similarly, `TransactionService.internalFundTransfer()` has a `@Transactional` annotation but uses multiple `save()` calls — if an error occurs after debiting the source but before crediting the destination, the debit is rolled back (good), but the fund-transfer service won't know. |
| **Impact** | Potential for lost funds, double-processing, or inconsistent states between services. |
| **Severity** | Critical |
| **Effort** | Large |

### 7.6 Available Balance Calculation Bug

| | |
|---|---|
| **Finding** | In `TransactionService.internalFundTransfer()`, the available balance is set to `actualBalance - amount` AFTER `actualBalance` was already reduced by `amount`. This means `availableBalance = (original - amount) - amount`, effectively double-deducting. The same bug exists in `utilPayment()`. |
| **Impact** | **Available balance will be incorrect after every transaction.** This is a financial data integrity bug. |
| **Severity** | Critical |
| **Effort** | Small |

### 7.7 `TransactionEntity` Uses `@OneToOne` Instead of `@ManyToOne`

| | |
|---|---|
| **Finding** | `TransactionEntity.account` is mapped with `@OneToOne(cascade = CascadeType.ALL)` but logically an account can have many transactions. This is semantically wrong and the `CascadeType.ALL` means deleting a transaction could cascade-delete the associated bank account. |
| **Impact** | Data model integrity risk. Potential for accidental account deletion. |
| **Severity** | High |
| **Effort** | Small |

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|---|---|---|---|
| 2.1 | Generic exception catch-all returns 400 with stack trace | Critical | Small | Error Handling |
| 2.4 | No error handling for Feign failures / no circuit breakers | Critical | Medium | Error Handling |
| 3.1 | Minimal test coverage (only core-banking has unit tests) | Critical | Large | Testing |
| 4.1 | No input validation on any request DTO | Critical | Small | Security |
| 4.6 | Password logged in request objects | Critical | Small | Security |
| 7.1 | No circuit breakers on inter-service calls | Critical | Medium | Resilience |
| 7.5 | Non-atomic distributed transaction handling | Critical | Large | Resilience |
| 7.6 | Available balance double-deduction bug | Critical | Small | Resilience |
| 1.2 | Duplicated code across services | High | Medium | Code Org |
| 2.2 | Inconsistent error response structure | High | Small | Error Handling |
| 2.3 | No HTTP status code granularity | High | Small | Error Handling |
| 3.2 | No integration tests | High | Large | Testing |
| 3.3 | No contract tests | High | Large | Testing |
| 4.2 | Hardcoded credentials in source code | High | Small | Security |
| 4.3 | Overly broad database permissions | High | Small | Security |
| 4.5 | No rate limiting | High | Medium | Security |
| 7.2 | No retry policies for Feign clients | High | Small | Resilience |
| 7.3 | No timeout configuration | High | Small | Resilience |
| 7.7 | TransactionEntity @OneToOne should be @ManyToOne | High | Small | Resilience |
| 1.1 | No multi-project Gradle build | Medium | Medium | Code Org |
| 2.5 | SimpleBankingGlobalException constructor issue | Medium | Small | Error Handling |
| 3.4 | @SpringBootTest in non-unit tests | Medium | Small | Testing |
| 4.4 | Keycloak singleton not thread-safe | Medium | Small | Security |
| 4.7 | No dependency vulnerability scanning | Medium | Small | Security |
| 5.1 | Raw ResponseEntity without type parameters | Medium | Small | API Design |
| 5.4 | No pagination metadata in responses | Medium | Small | API Design |
| 5.5 | OpenAPI webflux dependency mismatch | Medium | Small | API Design |
| 6.1 | Inconsistent logging practices | Medium | Small | Observability |
| 6.2 | No health check customization | Medium | Small | Observability |
| 6.3 | No metrics endpoints or dashboards | Medium | Medium | Observability |
| 7.4 | No fallback behavior | Medium | Medium | Resilience |
| 1.3 | Inconsistent package structure | Low | Small | Code Org |
| 1.4 | Mappers not Spring beans | Low | Small | Code Org |
| 5.2 | No API versioning strategy | Low | Small | API Design |
| 5.3 | Inconsistent REST conventions | Low | Small | API Design |
| 6.4 | Distributed tracing not fully verified | Low | Small | Observability |
