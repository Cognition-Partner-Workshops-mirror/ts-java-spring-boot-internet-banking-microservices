# Remediation Roadmap

This document prioritizes the gaps identified in [GAP_ANALYSIS.md](./GAP_ANALYSIS.md) into a phased remediation plan. Each item includes a sample Devin prompt that can be used to kick off the remediation work.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact improvements that require small effort. These items address critical security issues, basic quality gates, and foundational best practices.

---

### 1.1 Externalize Secrets from Source Control

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 4.1 | Critical | Small | Security |

**What:** Replace all hardcoded passwords in `docker-compose.yml` and `privileges.sql` with environment variable references. Create a `.env.example` template and add `.env` to `.gitignore`.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, externalize all secrets from `docker-compose/docker-compose.yml` and `docker-compose/docker-compose-support-apps.yml`. Replace hardcoded passwords (MySQL root password, app DB password, Keycloak admin password, Keycloak DB password) with `${VARIABLE_NAME}` references. Create a `docker-compose/.env.example` file with placeholder values and comments. Add `docker-compose/.env` to `.gitignore`. Do the same for `docker-compose/mysql/privileges.sql`.

---

### 1.2 Fix Error Response Format and HTTP Status Codes

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 2.1, 2.2 | High | Small | Error Handling |

**What:** Standardize all `GlobalExceptionHandler` classes to return proper HTTP status codes and a consistent JSON error envelope. Remove the raw string error that leaks exception details.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` in all 4 services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to:
> 1. Return 404 for `EntityNotFoundException`, 422 for `InsufficientFundsException`, and 500 for unhandled `Exception`.
> 2. Always return a JSON `ErrorResponse` body with fields: `code`, `message`, `status`, `timestamp`, `path`.
> 3. Remove the raw string response from the generic `Exception` handler — never expose stack traces or exception class names to the client.
> 4. Add a `@ExceptionHandler(MethodArgumentNotValidException.class)` for future Bean Validation support.

---

### 1.3 Add Feign Timeout Configuration

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 7.3 | High | Small | Resilience |

**What:** Configure explicit connect and read timeouts for all Feign clients to prevent thread starvation when core-banking-service is slow.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Feign timeout configuration to the three internet-banking services that use Feign clients (user-service, fund-transfer-service, utility-payment-service). Set `connectTimeout: 3000` and `readTimeout: 5000` in each service's `application.yml` under `spring.cloud.openfeign.client.config.default`. Also add the same timeouts in the externalized Spring Cloud Config repository YAML files for the `docker` profile.

---

### 1.4 Add OpenAPI Documentation

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 5.3 | High | Small | API Design |

**What:** Add `springdoc-openapi-starter-webmvc-ui` (or `webflux-ui` for the gateway) to each service so Swagger UI and OpenAPI specs are auto-generated.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add OpenAPI 3 documentation to all services:
> 1. Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0` to `build.gradle` of core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.
> 2. Add `org.springdoc:springdoc-openapi-starter-webflux-ui:2.5.0` to internet-banking-api-gateway (reactive stack).
> 3. Add `@OpenAPIDefinition` with title, version, and description to each service's main application class.
> 4. Add `@Tag` annotations to each controller and `@Operation` + `@ApiResponse` annotations to each endpoint method.
> 5. Verify Swagger UI is accessible at `/swagger-ui.html` for each service.

---

### 1.5 Set Up CI Pipeline

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 3.4 | High | Small | Testing |

**What:** Create a GitHub Actions workflow that builds all services and runs tests on every push and PR.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a GitHub Actions CI workflow at `.github/workflows/ci.yml` that:
> 1. Triggers on push to `main` and on all pull requests.
> 2. Uses Java 21 (Eclipse Temurin) and Gradle.
> 3. Builds and tests each service in parallel using a matrix strategy across the 7 service directories.
> 4. Caches Gradle dependencies between runs.
> 5. Reports test results using a JUnit report action.
> 6. Fails the build if any test fails.

---

### 1.6 Add Feign Error Decoder to All Services

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 2.3 | Medium | Small | Error Handling |

**What:** Add a `CustomFeignErrorDecoder` (matching the one in user-service) to fund-transfer-service and utility-payment-service.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, the `internet-banking-user-service` has a `CustomFeignErrorDecoder` that properly translates downstream errors into typed exceptions. Copy this pattern to `internet-banking-fund-transfer-service` and `internet-banking-utility-payment-service`:
> 1. Create a `CustomFeignErrorDecoder` class in each service's `configuration` package.
> 2. Register it as a `@Bean` in the existing `CustomFeignClientConfiguration` class.
> 3. Ensure the decoder handles 400, 401, 404, and default cases consistently.

---

### 1.7 Add Dependency Vulnerability Scanning

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 4.5 | Medium | Small | Security |

**What:** Add OWASP Dependency-Check Gradle plugin and/or enable GitHub Dependabot.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, set up dependency vulnerability scanning:
> 1. Create `.github/dependabot.yml` to monitor Gradle dependencies weekly for all 7 services.
> 2. Add the `org.owasp.dependencycheck` Gradle plugin (version 9.x) to each service's `build.gradle`.
> 3. Add a `dependencyCheckAnalyze` task configuration that fails the build on CVSS score >= 7.
> 4. Add a GitHub Actions step to the CI workflow that runs `./gradlew dependencyCheckAnalyze`.

---

### 1.8 Fix Keycloak Admin Client Thread Safety

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 4.4 | Medium | Small | Security |

**What:** Make the Keycloak singleton instance thread-safe and manage token refresh properly.

**Sample Devin Prompt:**
> In `internet-banking-user-service`, refactor `KeycloakUserService` to fix the thread-unsafe lazy initialization of the `Keycloak` instance. Either:
> 1. Create the `Keycloak` instance as a Spring `@Bean` in a configuration class and inject it, or
> 2. Use a `@PostConstruct` method to eagerly initialize it.
> Ensure the `Keycloak` bean is a singleton managed by Spring's container. Remove the manual `getKeycloak()` lazy-init method.

---

### 1.9 Add Pagination to List Endpoints

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 5.2 | Medium | Small | API Design |

**What:** Add Spring Data `Pageable` parameter support to all list endpoints.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add pagination to all list endpoints:
> 1. In core-banking-service: `GET /api/v1/bank-account` and `GET /api/v1/utility-account` should accept `Pageable` parameter and return `Page<T>`.
> 2. The `GET /api/v1/user` endpoint already supports pagination — verify it works correctly.
> 3. In internet-banking services: expose pagination parameters through the Feign clients and controllers where applicable.
> 4. Return pagination metadata (`totalElements`, `totalPages`, `number`, `size`) in the response.

---

### 1.10 Add Prometheus Metrics Endpoint

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 6.4 | Medium | Small | Observability |

**What:** Add Micrometer Prometheus registry so each service exports metrics that can be scraped.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Prometheus metrics to all services:
> 1. Add `io.micrometer:micrometer-registry-prometheus` dependency to each service's `build.gradle`.
> 2. Configure `management.endpoints.web.exposure.include` to include `prometheus,health,info,metrics`.
> 3. Verify `/actuator/prometheus` returns metrics in Prometheus exposition format.
> 4. Optionally add a `docker-compose` service for Prometheus with a `prometheus.yml` that scrapes all services.

---

### 1.11 Add Retry Policies for Feign Clients

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 7.2 | Medium | Small | Resilience |

**What:** Add Spring Retry with exponential backoff for Feign calls to handle transient failures.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add retry policies for Feign client calls in the three internet-banking services:
> 1. Add `org.springframework.retry:spring-retry` and `org.springframework:spring-aspects` dependencies to each service's `build.gradle`.
> 2. Enable retry with `@EnableRetry` on the application class.
> 3. Configure Feign retry in `application.yml`: `spring.cloud.openfeign.client.config.default.retryer` with max attempts = 3, initial interval = 100ms, max interval = 1000ms.
> 4. Ensure retries are only applied to idempotent operations (GET requests) — do NOT retry POST/PUT operations that modify state.

---

## Phase 2: Important Improvements (3-6 Weeks)

These items require moderate effort but significantly improve reliability, security, and maintainability.

---

### 2.1 Add Input Validation to All Services

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 4.2 | High | Medium | Security |

**What:** Add Bean Validation annotations to all DTOs and `@Valid` to all controller method parameters.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive input validation:
> 1. Add `org.springframework.boot:spring-boot-starter-validation` to all 4 business service `build.gradle` files.
> 2. Add Bean Validation annotations to all request DTOs:
>    - `FundTransferRequest`: `@NotBlank` on fromAccount/toAccount, `@NotNull @Positive` on amount
>    - `UtilityPaymentRequest`: `@NotBlank` on account/referenceNumber, `@NotNull @Positive` on providerId/amount
>    - `UserCreateRequest`: `@NotBlank @Email` on email, `@NotBlank` on required name fields, `@NotBlank @Size` on identification number
>    - All entity-facing DTOs with appropriate constraints
> 3. Add `@Valid` annotation to every `@RequestBody` parameter in all controllers.
> 4. Add a `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` that returns field-level error details.

---

### 2.2 Add Circuit Breakers

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 7.1 | High | Medium | Resilience |

**What:** Add Resilience4j circuit breakers around Feign client calls.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add circuit breakers to all inter-service communication:
> 1. Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to the three internet-banking services.
> 2. Configure circuit breaker defaults in each `application.yml`:
>    - `slidingWindowSize: 10`
>    - `failureRateThreshold: 50`
>    - `waitDurationInOpenState: 10s`
>    - `permittedNumberOfCallsInHalfOpenState: 3`
> 3. Add `@CircuitBreaker(name = "coreBanking", fallbackMethod = "...")` to the service methods that call Feign clients.
> 4. Implement fallback methods that return meaningful error responses rather than propagating raw exceptions.
> 5. Add Actuator endpoints to monitor circuit breaker state.

---

### 2.3 Add Unit Tests for Internet-Banking Services

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 3.1 | Critical | Large (start here, continue in Phase 3) | Testing |

**What:** Write unit tests for the three internet-banking service layers. Start with the most critical business logic.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add unit tests for the internet-banking services. For each service, create test classes that mock Feign clients and repositories:
>
> **internet-banking-fund-transfer-service:**
> - `FundTransferServiceTest`: Test successful transfer, insufficient funds, invalid account, Feign client error handling
> - `FundTransferControllerTest`: Test endpoint with `@WebMvcTest`, verify request/response shapes
>
> **internet-banking-utility-payment-service:**
> - `UtilityPaymentServiceTest`: Test successful payment, insufficient funds, invalid provider, Feign client error handling
> - `UtilityPaymentControllerTest`: Test endpoint with `@WebMvcTest`
>
> **internet-banking-user-service:**
> - `UserServiceTest`: Test create user (with Keycloak mock), read user, update user status, Feign client error handling
> - `UserControllerTest`: Test endpoints with `@WebMvcTest`
>
> Use Mockito to mock `BankingCoreFeignClient` and `KeycloakUserService`. Aim for at least 80% line coverage on service classes.

---

### 2.4 Standardize Project Structure

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 1.1 | Medium | Medium | Code Organization |

**What:** Align package structures across all services to a consistent convention.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, standardize the package structure across all services. Adopt this convention:
> - `controller` - REST controllers
> - `service` - Business logic
> - `repository` - Data access
> - `model.entity` - JPA entities
> - `model.dto` - Data transfer objects
> - `model.dto.request` - Request-specific DTOs
> - `model.dto.response` - Response-specific DTOs
> - `client` - Feign client interfaces (renamed from `model.rest`)
> - `configuration` - All configuration classes (flatten `configuration.feign` and `configuration.keycloak` into `configuration`)
> - `exception` - Exception classes
>
> Move classes as needed and update all imports. Run tests after each service to verify nothing breaks.

---

### 2.5 Add JWT Validation to Business Services

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 4.3 | Medium | Medium | Security |

**What:** Add OAuth2 resource server configuration to each business service so they independently validate JWT tokens.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add JWT token validation to the four business services (core-banking, user, fund-transfer, utility-payment):
> 1. Add `org.springframework.boot:spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-security` to each service's `build.gradle`.
> 2. Configure `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` pointing to Keycloak in each service's externalized config.
> 3. Create a `SecurityConfig` class in each service that permits Actuator endpoints but requires authentication for all API endpoints.
> 4. Propagate the JWT token from incoming requests through Feign clients using a `RequestInterceptor` that copies the `Authorization` header.
> 5. Update test configurations to either mock security or disable it for unit tests.

---

### 2.6 Add Structured Logging

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 6.1 | Medium | Medium | Observability |

**What:** Configure JSON-format logging with trace correlation across all services.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add structured JSON logging to all services:
> 1. Add `net.logstash.logback:logstash-logback-encoder:7.4` dependency to each service's `build.gradle`.
> 2. Create a `src/main/resources/logback-spring.xml` in each service that:
>    - Uses `LogstashEncoder` for the `docker` profile (JSON output).
>    - Uses standard pattern encoder for local development.
>    - Includes `traceId` and `spanId` from MDC in JSON output.
> 3. Add common fields: `service`, `environment`, `version`.
> 4. Remove any hardcoded log format patterns from `application.yml`.

---

### 2.7 Add Rate Limiting to API Gateway

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 7.5 | Medium | Medium | Resilience |

**What:** Configure Spring Cloud Gateway's `RequestRateLimiter` filter.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add rate limiting to the API gateway:
> 1. Add `org.springframework.boot:spring-boot-starter-data-redis-reactive` to the gateway's `build.gradle`.
> 2. Add a Redis container to `docker-compose.yml`.
> 3. Configure the `RequestRateLimiter` filter in the gateway's route definitions with:
>    - `redis-rate-limiter.replenishRate: 10` (requests per second)
>    - `redis-rate-limiter.burstCapacity: 20`
> 4. Implement a `KeyResolver` bean that resolves rate limit keys by JWT subject claim (authenticated user) or IP address (anonymous).
> 5. Test that rate-limited requests receive HTTP 429 Too Many Requests.

---

### 2.8 Add Fallback Behavior for Feign Clients

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 7.4 | Medium | Medium | Resilience |

**What:** Implement Feign fallback factories that return meaningful error responses when core-banking-service is unavailable.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add fallback behavior for all Feign clients:
> 1. For each `BankingCoreFeignClient` interface, create a `BankingCoreFeignClientFallbackFactory` that implements `FallbackFactory<BankingCoreFeignClient>`.
> 2. The fallback should log the cause and throw a custom `ServiceUnavailableException` with a user-friendly message.
> 3. Register the fallback factory on the `@FeignClient` annotation: `@FeignClient(name = "core-banking-service", fallbackFactory = BankingCoreFeignClientFallbackFactory.class)`.
> 4. Ensure `spring.cloud.openfeign.circuitbreaker.enabled: true` is set in each service's config.

---

### 2.9 Add API Versioning Strategy

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 5.1 | Medium | Medium | API Design |

**What:** Formalize API versioning with proper URL-path-based versioning and gateway route configuration.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, formalize API versioning:
> 1. Ensure all controller `@RequestMapping` paths consistently use `/api/v1/` prefix.
> 2. Create a versioning README section documenting the versioning strategy (URL path-based).
> 3. Configure the API gateway routes to support future `/api/v2/` routes alongside `/api/v1/`.
> 4. Add a `@RequestMapping("/api/v1")` at the class level on each controller and use method-level `@GetMapping`/`@PostMapping` for individual endpoints.

---

### 2.10 Set Up Centralized Log Aggregation

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 6.3 | Medium | Medium | Observability |

**What:** Add ELK stack or Loki to Docker Compose for centralized log aggregation.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add centralized log aggregation with Loki + Grafana:
> 1. Add Loki and Grafana containers to `docker-compose.yml`.
> 2. Configure Docker logging driver to send container logs to Loki, or add a Promtail sidecar.
> 3. Add a Grafana dashboard that shows logs from all services filterable by service name, log level, and traceId.
> 4. Alternatively, add Elasticsearch + Kibana if the team prefers ELK stack. Document the choice in the README.

---

## Phase 3: Polish (6-12 Weeks)

Longer-term improvements that complete the engineering maturity journey.

---

### 3.1 Create Shared Library Module

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 1.2, 1.3 | Medium | Large | Code Organization |

**What:** Create a Gradle multi-project build with a shared `common` module containing exception classes, DTOs, and error codes.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a multi-project Gradle build:
> 1. Create a root `settings.gradle` that includes all 7 services as subprojects.
> 2. Create a root `build.gradle` with shared configuration (Java 21, Spring Boot 3.2.x, dependency management).
> 3. Create a `common` module with:
>    - `SimpleBankingGlobalException`, `EntityNotFoundException`, `InsufficientFundsException`
>    - `GlobalErrorCode`
>    - `ErrorResponse`
>    - `GlobalExceptionHandler` (as a configurable base class)
> 4. Add `implementation project(':common')` to each service's `build.gradle`.
> 5. Remove the duplicated classes from each service.
> 6. Run all tests to verify nothing breaks.

---

### 3.2 Add Integration Tests with Testcontainers

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 3.2 | High | Large | Testing |

**What:** Add integration tests that verify database interactions, Feign communication, and full request lifecycle using Testcontainers.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration tests using Testcontainers:
> 1. Add `org.testcontainers:testcontainers`, `org.testcontainers:mysql`, and `org.testcontainers:junit-jupiter` dependencies to each service.
> 2. For core-banking-service: create `AccountRepositoryIntegrationTest` and `TransactionServiceIntegrationTest` that use a MySQL Testcontainer with Flyway migrations.
> 3. For internet-banking services: create `@SpringBootTest` tests that start the full application context with WireMock standing in for core-banking-service Feign calls.
> 4. Create an `AbstractIntegrationTest` base class with Testcontainers setup and shared configuration.
> 5. Add a `test-integration` Gradle task that runs only integration tests (separate from unit tests).

---

### 3.3 Add Contract Tests

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 3.3 | Medium | Large | Testing |

**What:** Add Spring Cloud Contract tests to verify API compatibility between services.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add contract tests between the internet-banking services and core-banking-service:
> 1. Add `org.springframework.cloud:spring-cloud-starter-contract-verifier` to core-banking-service (producer).
> 2. Write contracts in `src/test/resources/contracts/` for each endpoint consumed by Feign clients:
>    - `readBankAccount(accountNumber)` → `GET /api/v1/bank-account/{accountNumber}`
>    - `readUtilityAccount(providerId)` → `GET /api/v1/utility-account/{id}`
>    - `fundTransfer(request)` → `POST /api/v1/fund-transfer`
>    - `utilPayment(request)` → `POST /api/v1/utility-payment`
> 3. Add `org.springframework.cloud:spring-cloud-starter-contract-stub-runner` to the consumer services.
> 4. Generate stubs from core-banking-service and use them in consumer integration tests.

---

### 3.4 Complete Test Coverage for Core Banking Service

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 3.1 (continued) | Critical | Large | Testing |

**What:** Extend core-banking-service tests to cover controllers (MockMvc) and repository layer.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, expand core-banking-service test coverage:
> 1. Add `@WebMvcTest` controller tests for:
>    - `BankAccountController` (GET account, GET all accounts)
>    - `FundTransferController` (POST fund transfer)
>    - `UtilityPaymentController` (POST utility payment)
>    - `UserController` (GET user, GET users with pagination, POST create user)
> 2. Add `@DataJpaTest` repository tests for custom query methods:
>    - `BankAccountRepository.findByNumber()`
>    - `UtilityAccountRepository.findByProviderName()`
>    - `UserRepository.findByIdentificationNumber()`
> 3. Use `@MockBean` for service dependencies in controller tests.
> 4. Use H2 with Flyway migrations enabled for repository tests.
> 5. Aim for 90%+ line coverage on controller and service classes.

---

### 3.5 Add Custom Health Indicators

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 6.2 | Low | Small | Observability |

**What:** Add health indicators for downstream dependencies and configure liveness/readiness probes.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom health indicators:
> 1. In internet-banking services: add a health indicator that pings core-banking-service's Actuator health endpoint via Feign.
> 2. In internet-banking-user-service: add a health indicator that checks Keycloak availability.
> 3. Configure `management.endpoint.health.group.liveness` and `management.endpoint.health.group.readiness` for Kubernetes-style probes.
> 4. Set `management.endpoint.health.show-details: when-authorized` for production and `always` for development.

---

### 3.6 Standardize Response Envelopes

| Ref | Severity | Effort | Category |
|---|---|---|---|
| GAP 5.4, 5.5 | Low | Small | API Design |

**What:** Wrap all API responses in a consistent envelope and return proper HTTP status codes for creation operations.

**Sample Devin Prompt:**
> In the `ts-java-spring-boot-internet-banking-microservices` repo, standardize API responses:
> 1. Create an `ApiResponse<T>` wrapper class in the shared library with fields: `data`, `message`, `timestamp`.
> 2. Wrap all successful controller responses in `ApiResponse`.
> 3. Change POST endpoints that create resources to return `201 Created` with a `Location` header pointing to the created resource.
> 4. Ensure list endpoints return pagination metadata when paginated.
> 5. Update Feign client response types to match the new envelope format.

---

## Dependency Graph

Some items depend on others. Recommended execution order within each phase:

```
Phase 1 (parallel tracks):
  Track A: 1.1 → 1.2 → 1.6
  Track B: 1.3 → 1.11
  Track C: 1.4 (independent)
  Track D: 1.5 → 1.7
  Track E: 1.8, 1.9, 1.10 (independent)

Phase 2 (parallel tracks):
  Track A: 2.1 → 2.3 (validation before testing)
  Track B: 2.2 → 2.8 (circuit breakers before fallbacks)
  Track C: 2.4 (independent)
  Track D: 2.5 (independent)
  Track E: 2.6 → 2.10 (structured logging before aggregation)
  Track F: 2.7, 2.9 (independent)

Phase 3 (sequential):
  3.1 → 3.4 (shared library before expanding tests)
  3.2 → 3.3 (integration tests before contract tests)
  3.5, 3.6 (independent)
```

---

## Effort Summary

| Phase | Items | Estimated Total Effort |
|---|---|---|
| Phase 1 (Quick Wins) | 11 items | ~2 weeks |
| Phase 2 (Important) | 10 items | ~4-6 weeks |
| Phase 3 (Polish) | 6 items | ~4-6 weeks |
| **Total** | **27 items** | **~10-14 weeks** |

---

## Progress Tracking

Use this checklist to track remediation progress:

- [ ] **Phase 1**
  - [ ] 1.1 Externalize secrets
  - [ ] 1.2 Fix error responses & HTTP status codes
  - [ ] 1.3 Add Feign timeouts
  - [ ] 1.4 Add OpenAPI documentation
  - [ ] 1.5 Set up CI pipeline
  - [ ] 1.6 Add Feign error decoders
  - [ ] 1.7 Add dependency vulnerability scanning
  - [ ] 1.8 Fix Keycloak thread safety
  - [ ] 1.9 Add pagination
  - [ ] 1.10 Add Prometheus metrics
  - [ ] 1.11 Add retry policies
- [ ] **Phase 2**
  - [ ] 2.1 Add input validation
  - [ ] 2.2 Add circuit breakers
  - [ ] 2.3 Add unit tests for internet-banking services
  - [ ] 2.4 Standardize project structure
  - [ ] 2.5 Add JWT validation to business services
  - [ ] 2.6 Add structured logging
  - [ ] 2.7 Add rate limiting
  - [ ] 2.8 Add fallback behavior
  - [ ] 2.9 Formalize API versioning
  - [ ] 2.10 Set up log aggregation
- [ ] **Phase 3**
  - [ ] 3.1 Create shared library module
  - [ ] 3.2 Add integration tests with Testcontainers
  - [ ] 3.3 Add contract tests
  - [ ] 3.4 Complete test coverage for core-banking-service
  - [ ] 3.5 Add custom health indicators
  - [ ] 3.6 Standardize response envelopes
