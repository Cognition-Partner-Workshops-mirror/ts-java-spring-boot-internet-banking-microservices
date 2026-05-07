# Engineering Standards Gap Analysis

This document compares the current codebase against industry-standard engineering best practices across seven dimensions. Each gap is rated by **Severity** and **Remediation Effort**.

**Severity Scale:**
- **Critical** — Active risk in production; data loss, security vulnerability, or system instability likely.
- **High** — Significant quality or reliability issue; will cause problems at scale or under failure conditions.
- **Medium** — Best-practice violation that increases maintenance burden or technical debt.
- **Low** — Polish item; improves developer experience or code quality but minimal operational impact.

**Effort Scale:**
- **Small** — < 1 day per service; configuration change or minor code edit.
- **Medium** — 1–3 days per service; requires new code, patterns, or refactoring.
- **Large** — 1+ weeks; cross-cutting concern affecting multiple services or requiring new infrastructure.

---

## 1. Code Organization

### 1.1 No Multi-Project Gradle Build

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: Each microservice is an independent Gradle project with its own `build.gradle`, `gradlew`, and wrapper JAR. There is no root-level `settings.gradle` or `build.gradle` to manage shared configurations (plugin versions, dependency versions, common tasks).

**Impact**: Plugin and dependency versions must be updated independently in 6 files. Spring Boot version (`3.2.4`), Spring Cloud version (`2023.0.0`), and common dependencies (Lombok, MySQL connector, H2) are duplicated verbatim across all `build.gradle` files.

---

### 1.2 Duplicated Code Across Services

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding**: The following code is copy-pasted across multiple services with no shared library:

| Duplicated Code | Services |
|---|---|
| `GlobalExceptionHandler` | core-banking, user-service, fund-transfer, utility-payment |
| `ErrorResponse` | core-banking, user-service, fund-transfer, utility-payment |
| `SimpleBankingGlobalException` | core-banking, user-service, fund-transfer, utility-payment |
| `BaseMapper` interface | core-banking, user-service, fund-transfer, utility-payment |
| `AuditAware` base class | user-service, fund-transfer, utility-payment |
| `ApiRequestContext` / `ApiRequestContextHolder` | user-service, fund-transfer, utility-payment |
| `AppAuthUserFilter` | user-service, fund-transfer, utility-payment |
| `CustomFeignClientConfiguration` | fund-transfer, utility-payment |
| DTO classes (`FundTransferRequest`, `AccountResponse`, etc.) | Duplicated across consumer and producer services |

**Impact**: Bug fixes and improvements must be replicated across all copies. Divergence has already occurred — `ErrorResponse` in core-banking uses `@Builder`, while fund-transfer uses a constructor. Slight variations in exception handling logic exist between services.

---

### 1.3 Inconsistent Package Structure

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Package naming is inconsistent across services:

| Concern | core-banking | user-service | fund-transfer | utility-payment |
|---|---|---|---|---|
| Repository | `repository.*` | `model.repository.*` | `model.repository.*` | `repository.*` |
| REST clients | N/A | `service.rest.*` | `service.rest.client.*` | `service.rest.*` |
| Feign config | N/A | `configuration.feign.*` | `configuration.*` | `configuration.*` |

**Impact**: Developer onboarding friction; harder to navigate unfamiliar services.

---

### 1.4 Mapper Instantiation Anti-Pattern

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Mapper classes are instantiated inline as instance fields (`private UserMapper userMapper = new UserMapper()`) instead of being Spring-managed beans. This bypasses Spring's dependency injection and makes mappers harder to mock in tests.

**Impact**: Minor testability concern. If mappers ever need dependencies injected, they would require refactoring.

---

## 2. Error Handling

### 2.1 All Errors Return HTTP 400 Bad Request

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding**: Every `GlobalExceptionHandler` across all services maps all exceptions — including `EntityNotFoundException`, `InsufficientFundsException`, and generic `Exception` — to `ResponseEntity.badRequest()` (HTTP 400).

**Proper mapping should be:**
| Exception | Correct HTTP Status |
|---|---|
| `EntityNotFoundException` | 404 Not Found |
| `InsufficientFundsException` | 422 Unprocessable Entity |
| `UserAlreadyRegisteredException` | 409 Conflict |
| `InvalidEmailException` | 400 Bad Request |
| `Exception` (catch-all) | 500 Internal Server Error |

**Impact**: API consumers cannot distinguish between client errors, resource-not-found, and server errors. This breaks REST conventions and makes client-side error handling unreliable.

---

### 2.2 Generic Exception Catch-All Leaks Internal Details

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Finding**: The catch-all `@ExceptionHandler({Exception.class})` returns:
```java
"Exception occur inside API " + e
```
This concatenates the full exception (including stack trace via `toString()`) into the HTTP response body as a raw string.

**Impact**: Internal implementation details (class names, package structure, database queries, stack traces) are exposed to API consumers. This is a security vulnerability (CWE-209: Information Exposure Through Error Messages).

---

### 2.3 Inconsistent Error Response Format

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: Two different error response formats are used:
1. **Structured** (for `SimpleBankingGlobalException`): `{"code": "...", "message": "..."}`
2. **Raw string** (for generic exceptions): `"Exception occur inside API com.example.SomeException: ..."`

**Impact**: API consumers must handle two completely different response shapes for errors from the same endpoint.

---

### 2.4 No Feign Error Decoder in User Service

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: The user service declares `CustomFeignErrorDecoder` but the fund-transfer and utility-payment services use `CustomFeignClientConfiguration` which may not properly translate downstream HTTP errors into meaningful exceptions for the caller.

**Impact**: When core-banking-service returns an error, the internet banking services may wrap it poorly or lose context.

---

## 3. Testing

### 3.1 Minimal Test Coverage

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding**: Test coverage across services:

| Service | Test Files | Type | Coverage |
|---|---|---|---|
| core-banking-service | `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`, `CoreBankingServiceApplicationTests` | Unit (Mockito) | Service layer only; no controller or repository tests |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load only | No business logic tests |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only | No business logic tests |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only | No business logic tests |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only | No business logic tests |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | Context load only | No business logic tests |

**Impact**: 4 out of 6 services have zero meaningful tests. Critical business logic (user registration with Keycloak, fund transfer orchestration, utility payment orchestration) is completely untested.

---

### 3.2 No Integration Tests

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding**: There are no integration tests, `@SpringBootTest` with real (or embedded) database tests, or Testcontainers-based tests. The existing tests are pure unit tests using Mockito.

**Impact**: Database interactions (JPA queries, Flyway migrations, transaction boundaries) are never validated. Feign client communication between services is never tested.

---

### 3.3 No Contract Tests

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |

**Finding**: No consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) exist between the internet banking services and core-banking-service.

**Impact**: API changes in core-banking-service can silently break consumer services. The Feign client interfaces could become stale without detection.

---

### 3.4 Context-Load Tests May Not Pass

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: The `*ApplicationTests` classes attempt to load the full Spring context, which requires external dependencies (Config Server, Eureka, MySQL/Keycloak). Test `application.yml` files exist with H2 configs for some services, but external service dependencies may still cause failures.

**Impact**: The test suite may not reliably pass in CI without infrastructure or proper test profiles.

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |

**Finding**: Credentials are hardcoded in version-controlled files:

| File | Credential |
|---|---|
| `docker-compose.yml` | `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| `docker-compose.yml` | `KC_DB_PASSWORD: password` |
| `docker-compose.yml` | `KEYCLOAK_ADMIN_PASSWORD: password` |
| `privileges.sql` | `javatodev_development` password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

**Impact**: Anyone with repository access has full database and admin credentials. If these match production values, the system is fully compromised.

---

### 4.2 No Input Validation

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |

**Finding**: No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` / `@Max` annotations exist on any `@RequestBody` parameter across all controllers. Request DTOs have no Bean Validation constraints.

Examples of unvalidated inputs:
- Fund transfer `amount` could be negative, zero, or null
- `fromAccount` and `toAccount` could be blank or null
- User registration `email` has no format validation (only checked against Keycloak later)
- `providerId` in utility payment could be null

**Impact**: Malformed requests will propagate through the system until they hit a `NullPointerException` or database constraint violation, producing cryptic 400 errors instead of clear validation messages.

---

### 4.3 CSRF Disabled Without Documentation

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: CSRF protection is disabled in the API Gateway: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. While this is standard practice for stateless JWT-based APIs, it is not documented or justified.

**Impact**: Low risk since the API is JWT-based and stateless, but should be documented for security audit compliance.

---

### 4.4 Keycloak Singleton is Not Thread-Safe

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding**: `KeycloakProperties.getInstance()` uses a static field with a lazy-init check-then-act pattern that is **not thread-safe** (no synchronization, no volatile, no double-checked locking).

```java
private static Keycloak keycloakInstance = null;
public Keycloak getInstance() {
    if (keycloakInstance == null) {  // race condition
        keycloakInstance = KeycloakBuilder.builder()...build();
    }
    return keycloakInstance;
}
```

**Impact**: Under concurrent requests, multiple Keycloak instances could be created, potentially causing connection pool issues or stale auth tokens.

---

### 4.5 No Dependency Vulnerability Scanning

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: No dependency vulnerability scanning is configured (no OWASP Dependency Check, Snyk, or Dependabot configuration). No `.github/dependabot.yml` or similar exists.

**Impact**: Known CVEs in transitive dependencies will not be detected.

---

### 4.6 Password Logged in User Registration

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding**: The user controller logs the full request object:
```java
log.info("Creating user with {}", request.toString());
```
The `User` DTO includes a `password` field, and `@Data` (Lombok) generates a `toString()` that includes all fields. This means **passwords are written to application logs**.

**Impact**: Plaintext passwords in log files. Any log aggregation system (ELK, CloudWatch, etc.) would store user passwords.

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Type Parameters

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: Most controllers return `ResponseEntity` without generic type parameters:
```java
public ResponseEntity getBankAccount(...)  // raw type
```
Only the user service controller uses typed responses: `ResponseEntity<User>`.

**Impact**: No compile-time type safety. OpenAPI/Swagger documentation cannot infer response types automatically, leading to incomplete API documentation.

---

### 5.2 Inconsistent Pagination Support

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: Pagination is accepted via Spring's `Pageable` parameter but the response does not include pagination metadata. The services convert `Page.getContent()` to a plain `List`, discarding total count, page number, and page size.

**Impact**: API consumers cannot implement proper pagination UI — they don't know the total number of pages or records.

---

### 5.3 No API Versioning Strategy

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: APIs use `/api/v1/` prefix but there is no strategy documented for introducing `v2` endpoints or deprecating `v1`. No `@Deprecated` annotations or sunset headers exist.

**Impact**: Low risk currently but will become important as the API evolves.

---

### 5.4 Non-RESTful URL Patterns

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |

**Finding**: Some URL patterns deviate from REST conventions:
- `PATCH /api/v1/bank-users/update/{id}` — The verb `update` is redundant; `PATCH /api/v1/bank-users/{id}` is idiomatic.
- `POST /api/v1/bank-users/register` — `register` is a verb; `POST /api/v1/bank-users` is sufficient for resource creation.
- `GET /api/v1/account/bank-account/{account_number}` uses `snake_case` path variable while others use `camelCase`.

**Impact**: Minor inconsistency. Deviates from REST conventions but functionally correct.

---

### 5.5 OpenAPI/Swagger Dependency Mismatch

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: Business services include `springdoc-openapi-starter-webflux-ui:2.1.0` but they are Spring MVC (Web) applications, not WebFlux. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. The API Gateway (which is WebFlux-based) does not include any Swagger dependency.

**Impact**: Swagger UI may not function correctly. The wrong starter could cause runtime conflicts or simply not render the documentation.

---

### 5.6 No Filtering or Search Capabilities

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Finding**: List endpoints (users, transfers, payments) only support pagination. There are no filtering parameters (by date range, status, account, amount range, etc.).

**Impact**: Consumers must fetch all records and filter client-side, which is inefficient and impractical at scale.

---

## 6. Observability

### 6.1 Inconsistent Logging Patterns

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**:
- Some controllers use `@Slf4j` for logging, some don't (utility payment controller has no logging).
- Log messages are unstructured: `log.info("Reading account by ID {}", accountNumber)` — no consistent format, no correlation IDs.
- `log.info` contains a typo: `"Reading utitlity account by ID {}"`.
- Fund transfer service has a string concatenation bug: `log.info("Sending fund transfer request {}" + request.toString())` — the `{}` placeholder is not used because `+` concatenates before SLF4J substitution.

**Impact**: Log aggregation and search are harder. The concatenation bug means the request is always logged even if INFO level is disabled, causing unnecessary `toString()` overhead.

---

### 6.2 No Structured Logging Configuration

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: No custom `logback-spring.xml` or logging configuration exists in any service. Default Spring Boot console logging is used. No JSON-formatted log output for machine parsing.

**Impact**: In a containerized environment, log aggregation systems (ELK, Datadog, CloudWatch) work best with structured JSON logs. Text logs require complex parsing patterns.

---

### 6.3 Actuator Endpoints Publicly Accessible

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding**: The API Gateway security configuration explicitly permits all actuator endpoints without authentication:
```java
exchanges.pathMatchers("/actuator/**").permitAll()
    .pathMatchers("/user/actuator/**").permitAll()
    .pathMatchers("/fund-transfer/actuator/**").permitAll()
    .pathMatchers("/banking-core/actuator/**").permitAll()
    .pathMatchers("/utility-payment/actuator/**").permitAll()
```

**Impact**: Health checks, environment variables, configuration properties, and metrics are accessible to unauthenticated users. Depending on which actuator endpoints are enabled, this could expose sensitive configuration data.

---

### 6.4 No Health Check Dependencies Configured

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: While `spring-boot-starter-actuator` is included in all services, there is no configuration of health indicator dependencies (database health, Eureka health, Keycloak connectivity, etc.). The default `/actuator/health` will report basic UP/DOWN without checking downstream dependencies.

**Impact**: Container orchestrators (Kubernetes, Docker health checks) cannot determine if a service is truly healthy — a service could report UP while its database connection is dead.

---

### 6.5 No Metrics Endpoints or Dashboards

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |

**Finding**: Although `spring-boot-starter-actuator` and `micrometer-tracing-bridge-brave` are included, there is no Prometheus endpoint configuration (`management.endpoints.web.exposure.include=prometheus`) and no metrics dashboards (Grafana) are defined. The README mentions Prometheus as part of the tech stack but no actual integration exists.

**Impact**: No runtime visibility into request rates, error rates, latency percentiles, JVM metrics, or database connection pool stats.

---

## 7. Resilience

### 7.1 No Circuit Breakers

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |

**Finding**: All inter-service calls via OpenFeign have no circuit breaker configuration. No `resilience4j-circuitbreaker`, `spring-cloud-circuitbreaker`, or Hystrix dependency exists in any service.

**Impact**: If core-banking-service becomes slow or unresponsive, all upstream services (fund-transfer, utility-payment, user-service) will hang, consuming thread pool resources. This will cascade into a full system failure.

---

### 7.2 No Retry Policies

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |

**Finding**: No Feign retry configuration exists. No `Retryer` bean is configured. Transient network failures between services will immediately fail the request.

**Impact**: Temporary network glitches or brief service restarts will result in user-facing errors that could have been transparently retried.

---

### 7.3 No Timeout Configuration

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |

**Finding**: No connection or read timeouts are configured for Feign clients. Default timeouts (which vary by HTTP client implementation) are used. The fund-transfer and utility-payment services use `feign-okhttp` but no `OkHttpClient.Builder` timeout configuration exists.

**Impact**: A slow or hung downstream service can cause the calling service's threads to block indefinitely, eventually exhausting the thread pool and making the service unresponsive.

---

### 7.4 No Fallback Behavior

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: No Feign fallback classes or factory methods are defined. When a downstream service call fails, the exception propagates directly to the caller.

**Impact**: Users receive raw error messages instead of graceful degradation (e.g., "Service temporarily unavailable, please try again").

---

### 7.5 No Transaction Compensation (Saga Pattern)

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |

**Finding**: The fund transfer and utility payment services follow a two-step pattern:
1. Save local record with `PENDING` status
2. Call core-banking-service via Feign
3. Update local record with `SUCCESS` status

If step 2 succeeds but step 3 fails (e.g., local DB is down), the core banking transaction is committed but the local record remains `PENDING`. There is no compensation, retry, or reconciliation mechanism.

Similarly, in core-banking-service, the `internalFundTransfer` method debits the source account and credits the destination account as separate `save()` calls within a `@Transactional` boundary — but the `@Transactional` annotation uses `jakarta.transaction.Transactional` (JTA) instead of `org.springframework.transaction.annotation.Transactional`, which may have different default behavior.

**Impact**: Financial data inconsistency. Money could be debited from one account without being credited to another, or core banking records could become out of sync with internet banking records.

---

### 7.6 No Rate Limiting

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |

**Finding**: No rate limiting is configured at the API Gateway or individual service level. No `RequestRateLimiter` filter is defined in the gateway configuration.

**Impact**: The API is vulnerable to abuse, denial-of-service attacks, or accidental client loops that could overwhelm the system.

---

## Summary Table

| # | Gap | Category | Severity | Effort |
|---|---|---|---|---|
| 2.1 | All errors return HTTP 400 | Error Handling | Critical | Medium |
| 2.2 | Exception catch-all leaks internals | Error Handling | Critical | Small |
| 4.1 | Hardcoded credentials in source | Security | Critical | Small |
| 4.2 | No input validation | Security | Critical | Medium |
| 1.2 | Duplicated code across services | Code Organization | High | Large |
| 3.1 | Minimal test coverage | Testing | High | Large |
| 3.2 | No integration tests | Testing | High | Large |
| 4.4 | Keycloak singleton not thread-safe | Security | High | Small |
| 4.6 | Password logged in registration | Security | High | Small |
| 6.3 | Actuator endpoints publicly accessible | Observability | High | Small |
| 7.1 | No circuit breakers | Resilience | High | Medium |
| 7.3 | No timeout configuration | Resilience | High | Small |
| 7.5 | No transaction compensation | Resilience | High | Large |
| 1.1 | No multi-project Gradle build | Code Organization | Medium | Medium |
| 2.3 | Inconsistent error response format | Error Handling | Medium | Small |
| 2.4 | No Feign error decoder consistency | Error Handling | Medium | Small |
| 3.3 | No contract tests | Testing | Medium | Large |
| 3.4 | Context-load tests may not pass | Testing | Medium | Small |
| 4.5 | No dependency vulnerability scanning | Security | Medium | Small |
| 5.1 | Raw ResponseEntity without types | API Design | Medium | Small |
| 5.2 | Inconsistent pagination support | API Design | Medium | Medium |
| 5.5 | OpenAPI dependency mismatch | API Design | Medium | Small |
| 6.1 | Inconsistent logging patterns | Observability | Medium | Small |
| 6.2 | No structured logging | Observability | Medium | Small |
| 6.4 | No health check dependencies | Observability | Medium | Small |
| 7.2 | No retry policies | Resilience | Medium | Small |
| 7.4 | No fallback behavior | Resilience | Medium | Medium |
| 7.6 | No rate limiting | Resilience | Medium | Medium |
| 1.3 | Inconsistent package structure | Code Organization | Low | Small |
| 1.4 | Mapper instantiation anti-pattern | Code Organization | Low | Small |
| 4.3 | CSRF disabled without documentation | Security | Low | Small |
| 5.3 | No API versioning strategy | API Design | Low | Small |
| 5.4 | Non-RESTful URL patterns | API Design | Low | Small |
| 5.6 | No filtering or search capabilities | API Design | Low | Medium |
| 6.5 | No metrics endpoints or dashboards | Observability | Low | Medium |
