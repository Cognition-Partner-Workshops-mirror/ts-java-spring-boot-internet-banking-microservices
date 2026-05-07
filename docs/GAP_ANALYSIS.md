# Engineering Standards Gap Analysis

## Internet Banking Microservices — Java 21 / Spring Boot 3.2.4

---

## Summary Dashboard

| Category | Critical | High | Medium | Low | Total Gaps |
|---|---|---|---|---|---|
| Code Organization | 0 | 2 | 4 | 2 | 8 |
| Error Handling | 1 | 2 | 2 | 0 | 5 |
| Testing | 1 | 2 | 2 | 0 | 5 |
| Security | 3 | 3 | 2 | 1 | 9 |
| API Design | 0 | 2 | 3 | 2 | 7 |
| Observability | 0 | 1 | 3 | 1 | 5 |
| Resilience | 1 | 2 | 2 | 0 | 5 |
| **Total** | **6** | **14** | **18** | **6** | **44** |

---

## 1. Code Organization

### GAP-CO-01: No Shared Library Module

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |
| **Current State** | Each microservice is an independent Gradle project. Common code (exception handling, audit entities, filters, mappers, DTOs) is copy-pasted across 4 services. |
| **Expected State** | A `banking-common` shared library module with centralized exception classes, audit base entities, Feign configurations, and error response DTOs. |
| **Impact** | Bug fixes and improvements must be applied in 4+ places. Inconsistencies creep in (e.g., fund-transfer's `ErrorResponse` uses constructor while others use builder). |
| **Files Affected** | `GlobalExceptionHandler.java` (x4), `ErrorResponse.java` (x4), `SimpleBankingGlobalException.java` (x4), `BaseMapper.java` (x4), `AuditAware.java` (x3), `AuditConfig.java` (x3), `AuditorAwareConfig.java` (x3), `AppAuthUserFilter.java` (x3), `ApiRequestContext.java` (x3), `ApiRequestContextHolder.java` (x3) |

### GAP-CO-02: No Multi-Module Gradle Build

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Current State** | Each service has its own `build.gradle`, `gradlew`, and `gradle/wrapper`. No root `settings.gradle` or shared dependency management. |
| **Expected State** | A root `settings.gradle` including all subprojects, a root `build.gradle` with shared plugin and dependency configurations, and a single Gradle wrapper. |
| **Impact** | Cannot run `./gradlew test` from root to test all services. Dependency versions drift between services. No BOM enforcement. |

### GAP-CO-03: Hardcoded Dependency Versions Outside BOM

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Several dependencies have hardcoded versions that bypass Spring Boot BOM management: `flyway-core:10.12.0`, `flyway-mysql:10.12.0`, `mysql-connector-j:8.4.0`, `h2:2.2.224`, `feign-okhttp:13.2.1`, `springdoc-openapi-starter-webflux-ui:2.1.0`, `keycloak-admin-client:24.0.4`. |
| **Expected State** | Use Spring Boot BOM-managed versions where available. Only pin versions when a specific override is intentionally needed and documented. |
| **Impact** | Potential version conflicts and missed security patches when Spring Boot is upgraded. |

### GAP-CO-04: Wrong OpenAPI Dependency for Servlet Services

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Core Banking, User, Fund Transfer, and Utility Payment services use `springdoc-openapi-starter-webflux-ui:2.1.0` despite being servlet-based (Spring MVC) applications. |
| **Expected State** | Servlet services should use `springdoc-openapi-starter-webmvc-ui`. The `webflux-ui` variant is for reactive (WebFlux) applications. |
| **Impact** | May cause runtime classpath conflicts or incorrect API documentation generation. |

### GAP-CO-05: Deprecated Spring Cloud Bootstrap

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Current State** | All services use `spring-cloud-starter-bootstrap` with `bootstrap.yml` files to connect to Config Server. |
| **Expected State** | Spring Cloud 2023.x deprecates the bootstrap context. Should migrate to `spring.config.import=configserver:http://config-server:8090` in `application.yml`. |
| **Impact** | Future Spring Cloud versions may remove bootstrap support entirely. |

### GAP-CO-06: Mapper Instantiation Outside Spring Context

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Mappers are instantiated as field-level `new` objects (e.g., `private UserMapper userMapper = new UserMapper()`) rather than Spring-managed beans. |
| **Expected State** | Mappers should be `@Component` beans injected via constructor, or static utility classes, or replaced with a framework like MapStruct. |
| **Impact** | Mappers cannot benefit from Spring features (AOP, profiles, testing injection). Makes unit testing harder. |

### GAP-CO-07: Identical Package Names Across Services

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Large |
| **Current State** | All services use `com.javatodev.finance` as the base package. Classes like `UserService`, `UserEntity`, `GlobalExceptionHandler` exist with the same FQCN in multiple services. |
| **Expected State** | Service-specific base packages (e.g., `com.javatodev.finance.core`, `com.javatodev.finance.usersvc`) to avoid classpath conflicts in integration testing or shared library scenarios. |
| **Impact** | Low risk currently since services run in separate JVMs, but blocks any future consolidation or shared-classpath testing. |

### GAP-CO-08: Legacy commons-lang Dependency

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Current State** | User service's `AppAuthUserFilter` imports `org.apache.commons.lang.StringUtils` (commons-lang v2, EOL). |
| **Expected State** | Use `org.apache.commons.lang3.StringUtils` (commons-lang3) or Spring's `StringUtils`. |
| **Impact** | commons-lang v2 receives no security patches. |

---

## 2. Error Handling

### GAP-EH-01: Exception Stack Trace Leakage to Clients

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Current State** | All four business services catch `Exception.class` and return `"Exception occur inside API " + e`, which serializes the full exception including stack trace, class names, and potentially SQL queries. |
| **Expected State** | Return a generic error message (e.g., "Internal server error") with a correlation ID. Log the full exception server-side at ERROR level. |
| **Files Affected** | `GlobalExceptionHandler.java` in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service |

### GAP-EH-02: All Errors Return HTTP 400 Bad Request

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | Both `handleGlobalException` and `handleException` return `ResponseEntity.badRequest()` (HTTP 400) regardless of the actual error type. Entity-not-found errors, insufficient funds, internal server errors, and validation failures all return 400. |
| **Expected State** | Use appropriate HTTP status codes: 404 for not-found, 409 for conflict/insufficient funds, 422 for validation errors, 500 for unexpected server errors. |
| **Impact** | Clients cannot distinguish between different error types. Monitoring cannot differentiate client errors from server errors. |

### GAP-EH-03: Inconsistent Error Response Format

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | Known exceptions return `ErrorResponse {code, message}` as JSON. Unknown exceptions return a raw string `"Exception occur inside API ..."`. These are different response shapes for the same endpoint. |
| **Expected State** | All error responses should use a consistent structure (e.g., `{code, message, timestamp, path}`). |
| **Impact** | Clients must handle two different error response formats. |

### GAP-EH-04: No Feign Error Decoder on All Clients

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | User Service's `BankingCoreRestClient` has no custom error decoder. Fund Transfer Service uses `CustomFeignClientConfiguration` with only a logger level setting. Utility Payment Service uses its own `CustomFeignClientConfiguration` with a logger level. Only User Service has a `CustomFeignErrorDecoder`. |
| **Expected State** | All Feign clients should have an error decoder that translates downstream HTTP errors into meaningful application exceptions. |
| **Impact** | Feign throws generic `FeignException` for all downstream failures, which are caught by the global handler and leak internal details. |

### GAP-EH-05: Broad Exception Catch in KeycloakUserService

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | `KeycloakUserService.readUser()` catches `Exception` and converts all errors to `EntityNotFoundException`, masking connectivity issues, authentication failures, or timeout errors. |
| **Expected State** | Catch specific Keycloak exceptions (`NotFoundException`, `ProcessingException`, etc.) and map appropriately. |
| **Impact** | Debugging Keycloak connectivity issues is very difficult; all errors appear as "user not found". |

---

## 3. Testing

### GAP-TS-01: 5 of 6 Services Have Zero Business Logic Tests

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Large |
| **Current State** | Only `core-banking-service` has unit tests for business logic (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). The other 5 services only have Spring context-load smoke tests (`*ApplicationTests.java`). Total: 10 test files for 131 source files. |
| **Expected State** | Each service should have unit tests for all service-layer classes, with a target of 70%+ line coverage. Critical financial logic should have 90%+ coverage. |
| **Impact** | No safety net for regressions in user registration, fund transfer orchestration, utility payment orchestration, API gateway routing, or security configuration. |

### GAP-TS-02: No Integration Tests

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Large |
| **Current State** | No tests verify Feign client calls against real or mocked downstream services. No tests verify JPA repository queries against a database. No `@SpringBootTest` with `@AutoConfigureMockMvc` or `WebTestClient`. |
| **Expected State** | Integration tests using `@SpringBootTest`, Testcontainers for MySQL, and WireMock for downstream service mocking. |
| **Impact** | Configuration errors, Feign client mismatches, and JPA mapping issues are only caught in production. |

### GAP-TS-03: No Contract Tests Between Services

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Current State** | No Pact, Spring Cloud Contract, or other consumer-driven contract testing. Feign client interfaces and controller DTOs are not verified to be compatible. |
| **Expected State** | Consumer-driven contract tests ensuring API compatibility between Fund Transfer/Utility Payment/User Service (consumers) and Core Banking (provider). |
| **Impact** | A breaking change in Core Banking's API will cascade silently to all consumers. |

### GAP-TS-04: Existing Tests Don't Catch Known Bugs

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | The `TransactionServiceTest` does not verify the correctness of `availableBalance` after fund transfers or utility payments. The double-debit bug (GAP-EH related, see Security section) is not caught by any test. |
| **Expected State** | Tests should assert the exact balance values after operations, not just response message strings. |
| **Impact** | Known financial calculation bug exists undetected by the test suite. |

### GAP-TS-05: No Security Tests

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Current State** | No tests verify JWT validation, endpoint protection, role enforcement, or CSRF behavior in the API Gateway. |
| **Expected State** | `@WebFluxTest` for API Gateway security configuration testing. Verify that protected endpoints reject unauthenticated requests and that permitAll endpoints work without tokens. |
| **Impact** | Security regressions (e.g., accidentally removing authentication) would not be caught. |

---

## 4. Security

### GAP-SE-01: Hardcoded Credentials in Version Control

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Current State** | Plaintext passwords committed in: `docker-compose.yml` (MySQL root: `woVERANKliGharym`, Keycloak admin: `admin/password`, Keycloak DB: `keycloak/password`), `mysql/Dockerfile` (MySQL root password), `mysql/privileges.sql` (app user: `javatodev_development/oPItyPticIAt`), `README.md` (test credentials). |
| **Expected State** | Use Docker secrets, `.env` files (gitignored), or a vault for all credentials. Rotate all exposed credentials. |
| **Impact** | Anyone with repository access (public or internal) has full database and admin access. |

### GAP-SE-02: No Input Validation on Any Endpoint

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Current State** | No `spring-boot-starter-validation` dependency. No Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`) on any DTO. No `@Valid` on any controller parameter. |
| **Expected State** | All request DTOs annotated with validation constraints. Controllers use `@Valid`. Custom validators for business rules (e.g., transfer amount > 0, account number format). |
| **Impact** | Negative transfer amounts, null account numbers, empty emails, and SQL injection vectors are accepted. Arbitrary data reaches the database layer. |
| **Files Affected** | `FundTransferRequest.java` (x2), `UtilityPaymentRequest.java` (x2), `User.java` (user-service), `UserUpdateRequest.java` |

### GAP-SE-03: Balance Double-Debit Bug (Financial Integrity)

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Small |
| **Current State** | In `TransactionService.java`, `availableBalance` is set to `actualBalance.subtract(amount)` *after* `actualBalance` has already been decremented. This double-subtracts from `availableBalance`. Occurs in three places: `utilPayment()` (lines 63-64), `internalFundTransfer()` debit (lines 90-91), and `internalFundTransfer()` credit (lines 99-100). |
| **Expected State** | Store the original balance or compute `availableBalance` from the original value, not the already-modified `actualBalance`. |
| **Impact** | Every transaction corrupts the `availableBalance` field, making it diverge from `actualBalance`. |

### GAP-SE-04: Password Exposed in API Responses

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | The `User` DTO in user-service contains a `password` field. The same DTO is used for both request (registration) and response (read user). Jackson serializes all fields by default. |
| **Expected State** | Use separate request/response DTOs, or annotate password with `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)`. |
| **Impact** | User passwords may be returned in API responses. |

### GAP-SE-05: Actuator Endpoints Publicly Accessible

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | `SecurityConfiguration.java` permits all actuator endpoints without authentication: `/actuator/**`, `/user/actuator/**`, `/fund-transfer/actuator/**`, `/banking-core/actuator/**`, `/utility-payment/actuator/**`. |
| **Expected State** | Restrict actuator to internal network or require admin authentication. Expose only `/actuator/health` publicly. |
| **Impact** | Exposes environment variables, configuration properties, beans, thread dumps, and heap dumps to unauthenticated users. |

### GAP-SE-06: No Authorization / RBAC at Endpoint Level

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Current State** | Authentication is enforced (JWT required), but no role-based access control exists. Any authenticated user can: approve other users, transfer funds from any account, view all users, access all transactions. |
| **Expected State** | Implement role-based security (e.g., `ADMIN`, `USER` roles from Keycloak) with `@PreAuthorize` or gateway-level route security. Users should only access their own accounts. |
| **Impact** | Any authenticated user has full access to all operations including admin functions. |

### GAP-SE-07: Config Server Fetches from Public GitHub Repo

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Config Server is configured to fetch from `https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git` — a public repository. |
| **Expected State** | Use a private repository. Enable Config Server encryption for sensitive properties. Add authentication to the Config Server endpoint. |
| **Impact** | All service configurations (database URLs, Keycloak secrets, etc.) are publicly readable. |

### GAP-SE-08: CSRF Disabled Without Alternative Protections

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | `SecurityConfiguration.java:35` disables CSRF entirely. No CORS configuration or SameSite cookie policies are configured as alternatives. |
| **Expected State** | For stateless JWT APIs, CSRF disable is acceptable but should be paired with proper CORS configuration restricting allowed origins. |
| **Impact** | Cross-origin requests are unrestricted. |

### GAP-SE-09: "SYSTEM USER" Fallback for Unauthenticated Requests

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Current State** | `GatewayConfiguration.java` defaults unauthenticated principal to `"SYSTEM USER"` in the `X-Auth-Id` header. Downstream services store this in audit fields. |
| **Expected State** | Unauthenticated requests (e.g., to `/register`) should not receive a misleading system-level identity. Use a clear indicator (e.g., `ANONYMOUS`) or omit the header. |
| **Impact** | Audit trails may falsely attribute actions to a system account. |

---

## 5. API Design

### GAP-AD-01: Raw ResponseEntity Without Type Parameters

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | Most controller methods return `ResponseEntity` without generics (e.g., `public ResponseEntity getBankAccount(...)` instead of `ResponseEntity<BankAccount>`). |
| **Expected State** | All controller methods should specify the response type: `ResponseEntity<BankAccount>`, `ResponseEntity<List<User>>`, etc. |
| **Impact** | OpenAPI/Swagger documentation cannot infer response schemas. Compile-time type safety is lost. |
| **Files Affected** | `AccountController.java`, `TransactionController.java`, `UserController.java` (core), `FundTransferController.java`, `UtilityPaymentController.java` |

### GAP-AD-02: No Pagination Limits or Defaults

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | All paginated endpoints accept Spring Data `Pageable` directly from query parameters with no maximum page size. A request for `?size=1000000` would be executed. |
| **Expected State** | Configure default and maximum page sizes via `spring.data.web.pageable.max-page-size` or custom `PageableHandlerMethodArgumentResolverCustomizer`. Return pagination metadata (total elements, total pages) in responses. |
| **Impact** | Unbounded queries can cause OOM errors or database strain. |

### GAP-AD-03: Inconsistent REST Conventions

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Mixed URL patterns: `PATCH /update/{id}` (user-service) vs standard REST `PATCH /{id}`. Fund transfer uses `POST /api/v1/transfer` (no resource noun). Path variables use underscores (`account_number`) inconsistently with camelCase JSON fields. |
| **Expected State** | Consistent RESTful URL design: `/api/v1/users/{id}`, `/api/v1/transfers`, `/api/v1/accounts/{accountNumber}`. Use hyphens in URLs, camelCase in JSON. |
| **Impact** | Inconsistent developer experience across services. |

### GAP-AD-04: No API Versioning Strategy

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Current State** | All endpoints use `/api/v1/` prefix but there is no actual versioning strategy, no documentation of what constitutes a breaking change, and no plan for v2 migration. |
| **Expected State** | Document the API versioning strategy (URL path, header, or media type). Plan for backward-compatible evolution. |
| **Impact** | Low immediate risk, but becomes critical when breaking changes are needed. |

### GAP-AD-05: No Standardized Response Envelope

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Current State** | Successful responses return raw domain objects. Error responses return either `ErrorResponse` or raw strings. No consistent wrapper. |
| **Expected State** | Standardized response envelope: `{data: ..., errors: [...], meta: {pagination, timestamp}}`. |
| **Impact** | Clients must handle different response shapes for success and error cases. |

### GAP-AD-06: No Filtering or Sorting on List Endpoints

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Medium |
| **Current State** | List endpoints (`GET /api/v1/transfer`, `GET /api/v1/utility-payment`, etc.) only support pagination. No filtering by date range, status, account, or amount. No sort parameter. |
| **Expected State** | Support query parameters for filtering (e.g., `?status=SUCCESS&fromDate=2024-01-01`) and sorting (`?sort=createdDate,desc`). |
| **Impact** | Clients must fetch all records and filter client-side, which is inefficient. |

### GAP-AD-07: Swagger UI Available but May Not Work Correctly

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Current State** | `springdoc-openapi-starter-webflux-ui` is included in servlet services. While Swagger UI may render, the underlying webflux dependency may cause issues with servlet-stack features. OpenAPI annotations (`@Operation`, `@Tag`) are present but incomplete. |
| **Expected State** | Correct dependency (`webmvc-ui`) with complete OpenAPI annotations including request/response schemas, error responses, and security scheme documentation. |

---

## 6. Observability

### GAP-OB-01: No Centralized Logging

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Medium |
| **Current State** | Each service logs to stdout via SLF4J/Logback defaults. No structured JSON logging. No centralized log aggregation (ELK, Loki, CloudWatch). |
| **Expected State** | Structured JSON logging with trace ID correlation. Centralized aggregation with search and alerting. |
| **Impact** | Debugging production issues requires accessing individual container logs. Correlating events across services is manual. |

### GAP-OB-02: No Prometheus Metrics

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Spring Boot Actuator is present with `micrometer-tracing-bridge-brave`, but no Prometheus registry is configured. The README lists Prometheus as a technology but it is not implemented. No `micrometer-registry-prometheus` dependency. |
| **Expected State** | Add `micrometer-registry-prometheus` dependency. Expose `/actuator/prometheus` endpoint. Deploy Prometheus + Grafana for dashboards. |
| **Impact** | No runtime performance metrics, no alerting on error rates, latency, or resource usage. |

### GAP-OB-03: No Custom Business Metrics

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | No custom Micrometer counters, gauges, or timers for business operations (e.g., transfer count, payment volume, registration rate, error rates by type). |
| **Expected State** | Instrument key business operations with Micrometer metrics for operational visibility. |
| **Impact** | Cannot monitor business health or detect anomalies (e.g., sudden drop in transfers). |

### GAP-OB-04: No Health Check Configuration

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Small |
| **Current State** | Actuator provides `/actuator/health` by default, but no custom health indicators for Keycloak connectivity, Config Server availability, or Eureka registration status. Docker Compose has no health check directives. |
| **Expected State** | Custom health indicators for critical dependencies. Docker Compose `healthcheck` blocks. Kubernetes-ready liveness/readiness probes. |
| **Impact** | Container orchestrators cannot distinguish healthy from unhealthy instances. |

### GAP-OB-05: Sensitive Data in Logs

| Attribute | Value |
|---|---|
| **Severity** | Low |
| **Effort** | Small |
| **Current State** | Controllers log request objects via `.toString()`, which may include account numbers, user emails, and passwords (user-service registration). Feign logging is set to `FULL` in fund-transfer, logging all headers and body content. |
| **Expected State** | Mask or exclude sensitive fields from log output. Use `BASIC` or `HEADERS` Feign log level in non-dev environments. Implement a custom `toString()` or `@ToString.Exclude` on sensitive fields. |
| **Impact** | PII and financial data in logs violates data protection regulations. |

---

## 7. Resilience

### GAP-RE-01: No Circuit Breakers

| Attribute | Value |
|---|---|
| **Severity** | Critical |
| **Effort** | Medium |
| **Current State** | No Resilience4j, Hystrix, or any circuit breaker implementation. All inter-service calls via OpenFeign are unprotected. A slow or failing Core Banking service will cause cascading thread exhaustion across all consumer services. |
| **Expected State** | Resilience4j circuit breakers on all Feign clients with configurable thresholds, fallback methods, and monitoring. |
| **Impact** | A single service failure can bring down the entire system through cascading timeouts. |

### GAP-RE-02: No Retry Policies

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | No retry configuration on Feign clients or RestTemplate calls. Transient network errors (brief connection resets, DNS hiccups) cause immediate failures. |
| **Expected State** | Configure Resilience4j retry with exponential backoff for idempotent GET operations. Non-idempotent operations (transfers, payments) should NOT be auto-retried without idempotency keys. |
| **Impact** | Transient failures cause unnecessary user-facing errors. |

### GAP-RE-03: No Timeout Configuration

| Attribute | Value |
|---|---|
| **Severity** | High |
| **Effort** | Small |
| **Current State** | Feign clients use default timeouts (varies by HTTP client implementation). No explicit `connectTimeout` or `readTimeout` configuration. No gateway-level timeout. |
| **Expected State** | Explicit timeout configuration on all Feign clients (e.g., `connectTimeout: 2s`, `readTimeout: 5s`). Gateway-level timeout per route. |
| **Impact** | A hung downstream service can hold threads indefinitely, leading to thread pool exhaustion. |

### GAP-RE-04: No Compensation / Saga Pattern for Distributed Transactions

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Large |
| **Current State** | Fund Transfer and Utility Payment services save a local entity, then call Core Banking. If Core Banking succeeds but the local update fails (or vice versa), data is inconsistent. No compensation logic, no saga orchestrator, no outbox pattern. |
| **Expected State** | Implement either: (a) Saga pattern with compensating transactions, (b) Outbox pattern with CDC, or (c) at minimum, a reconciliation job that detects and resolves inconsistencies. |
| **Impact** | Partial failures leave phantom `PENDING` / `PROCESSING` records with no recovery mechanism. |

### GAP-RE-05: No Idempotency Keys on Write Operations

| Attribute | Value |
|---|---|
| **Severity** | Medium |
| **Effort** | Medium |
| **Current State** | Fund transfer and utility payment endpoints have no idempotency mechanism. A retried request (e.g., client timeout + retry) will execute the operation twice. |
| **Expected State** | Accept an `Idempotency-Key` header. Store completed keys and return cached responses for duplicates. |
| **Impact** | Duplicate fund transfers or payments due to client-side retries. |

---

## Appendix: Gap Count by Severity and Effort

| ID | Severity | Effort | Category |
|---|---|---|---|
| GAP-SE-01 | Critical | Medium | Security |
| GAP-SE-02 | Critical | Medium | Security |
| GAP-SE-03 | Critical | Small | Security |
| GAP-EH-01 | Critical | Small | Error Handling |
| GAP-TS-01 | Critical | Large | Testing |
| GAP-RE-01 | Critical | Medium | Resilience |
| GAP-CO-01 | High | Large | Code Organization |
| GAP-CO-02 | High | Medium | Code Organization |
| GAP-EH-02 | High | Small | Error Handling |
| GAP-EH-03 | High | Small | Error Handling |
| GAP-SE-04 | High | Small | Security |
| GAP-SE-05 | High | Small | Security |
| GAP-SE-06 | High | Medium | Security |
| GAP-TS-02 | High | Large | Testing |
| GAP-TS-03 | High | Medium | Testing |
| GAP-AD-01 | High | Small | API Design |
| GAP-AD-02 | High | Small | API Design |
| GAP-RE-02 | High | Small | Resilience |
| GAP-RE-03 | High | Small | Resilience |
| GAP-OB-01 | High | Medium | Observability |
| GAP-CO-03 | Medium | Small | Code Organization |
| GAP-CO-04 | Medium | Small | Code Organization |
| GAP-CO-05 | Medium | Medium | Code Organization |
| GAP-CO-06 | Medium | Small | Code Organization |
| GAP-EH-04 | Medium | Small | Error Handling |
| GAP-EH-05 | Medium | Small | Error Handling |
| GAP-SE-07 | Medium | Small | Security |
| GAP-SE-08 | Medium | Small | Security |
| GAP-TS-04 | Medium | Small | Testing |
| GAP-TS-05 | Medium | Medium | Testing |
| GAP-AD-03 | Medium | Small | API Design |
| GAP-AD-04 | Medium | Medium | API Design |
| GAP-AD-05 | Medium | Medium | API Design |
| GAP-OB-02 | Medium | Small | Observability |
| GAP-OB-03 | Medium | Small | Observability |
| GAP-OB-04 | Medium | Small | Observability |
| GAP-RE-04 | Medium | Large | Resilience |
| GAP-RE-05 | Medium | Medium | Resilience |
| GAP-CO-07 | Low | Large | Code Organization |
| GAP-CO-08 | Low | Small | Code Organization |
| GAP-SE-09 | Low | Small | Security |
| GAP-AD-06 | Low | Medium | API Design |
| GAP-AD-07 | Low | Small | API Design |
| GAP-OB-05 | Low | Small | Observability |
