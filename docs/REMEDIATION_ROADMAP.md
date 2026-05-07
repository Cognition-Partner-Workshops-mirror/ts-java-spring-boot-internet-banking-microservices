# Remediation Roadmap

This roadmap prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into three phases based on impact, risk, and effort.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact, low-effort fixes that immediately improve reliability and security.

### 1.1 Fix HTTP Status Codes in Exception Handlers (EH-1, EH-2)

**Problem**: All exceptions return HTTP 400. Entity not found should be 404, insufficient funds should be 422, and unknown errors should be 500. The catch-all handler leaks stack traces.

**Effort**: Small | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `GlobalExceptionHandler` class in ALL four services (core-banking-service, internet-banking-fund-transfer-service, internet-banking-user-service, internet-banking-utility-payment-service) to:
> 1. Return HTTP 404 for `EntityNotFoundException`
> 2. Return HTTP 422 for `InsufficientFundsException` and other business validation exceptions
> 3. Return HTTP 500 for the generic `Exception` catch-all handler
> 4. Always return a structured `ErrorResponse` JSON (never a raw string)
> 5. Never include the exception stack trace or raw exception message in the response body
> Make sure all tests still pass after the changes.

---

### 1.2 Add Input Validation to Request DTOs (S-1)

**Problem**: No `@Valid` or Bean Validation annotations. Arbitrary input (null amounts, empty account numbers) is accepted.

**Effort**: Small | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Jakarta Bean Validation annotations to all request DTOs:
> - `FundTransferRequest`: `@NotBlank` on fromAccount and toAccount, `@NotNull @Positive` on amount
> - `UtilityPaymentRequest`: `@NotNull` on providerId, `@NotNull @Positive` on amount, `@NotBlank` on referenceNumber and account
> - `User` (registration): `@NotBlank @Email` on email, `@NotBlank` on identification and password
> - `UserUpdateRequest`: `@NotNull` on status
>
> Add `@Valid` annotation to all controller method parameters that accept these DTOs.
> Add a `MethodArgumentNotValidException` handler to each `GlobalExceptionHandler` that returns HTTP 400 with field-level error details.
> Add `spring-boot-starter-validation` to each service's `build.gradle` if not already present.

---

### 1.3 Configure Feign Client Timeouts (R-2)

**Problem**: No connect or read timeouts on Feign clients. A slow or unresponsive downstream service will block indefinitely.

**Effort**: Small | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add Feign client timeout configuration to the fund-transfer-service, user-service, and utility-payment-service. Set connect timeout to 5 seconds and read timeout to 10 seconds. Configure this in each service's `application.yml` using `spring.cloud.openfeign.client.config.default.connect-timeout` and `read-timeout` properties. Also add a request interceptor timeout for the Feign HTTP client.

---

### 1.4 Add Typed ResponseEntity to Controllers (A-1)

**Problem**: Raw `ResponseEntity` without generics breaks compile-time safety and OpenAPI documentation.

**Effort**: Small | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all controller methods across all services to use typed `ResponseEntity<T>` instead of raw `ResponseEntity`. For example, `ResponseEntity<BankAccount>`, `ResponseEntity<FundTransferResponse>`, etc. This will fix compiler warnings and improve the auto-generated Swagger documentation.

---

### 1.5 Protect Actuator Endpoints (S-4)

**Problem**: All actuator endpoints are publicly accessible through the gateway, potentially exposing environment variables, heap dumps, and config.

**Effort**: Small | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update the `SecurityConfiguration` in the API gateway to:
> 1. Only permit `/actuator/health` and `/actuator/info` without authentication
> 2. Require authentication for all other actuator paths (`/actuator/**`)
> 3. In each service's config, explicitly set `management.endpoints.web.exposure.include=health,info,prometheus` to limit exposed endpoints.

---

### 1.6 Remove Sensitive Data from Logs (O-3, EH-5)

**Problem**: Controllers log full request objects via `.toString()`, potentially exposing account numbers, passwords, and PII.

**Effort**: Small | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, audit all `log.info()` calls in controllers and services. Replace logging of full request objects (e.g., `fundTransferRequest.toString()`) with safe summaries that exclude sensitive fields. For the User service, ensure the password field is never logged. Add `@ToString.Exclude` on the `password` field in the User DTO using Lombok.

---

### 1.7 Add Prometheus Metrics Endpoint (O-1)

**Problem**: No Prometheus metrics registry despite having Actuator. Cannot monitor service health in production.

**Effort**: Small | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add `io.micrometer:micrometer-registry-prometheus` to the dependencies of all four business services (core-banking, fund-transfer, user-service, utility-payment). Ensure the `/actuator/prometheus` endpoint is exposed in each service's application configuration. Verify the endpoint returns Prometheus-format metrics.

---

### 1.8 Add Test Coverage Reporting (T-4)

**Problem**: No way to measure or enforce test coverage.

**Effort**: Small | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the JaCoCo Gradle plugin to all service `build.gradle` files. Configure it to generate HTML and XML reports on `./gradlew test`. Set a minimum coverage threshold of 50% as a starting point (to be increased over time). The build should warn (not fail) if coverage is below threshold.

---

## Phase 2: Important (3-6 Weeks)

Structural improvements that require more effort but significantly improve reliability and maintainability.

### 2.1 Add Circuit Breakers with Resilience4j (R-1, R-4)

**Problem**: No circuit breakers. If core-banking-service is down, calling services block indefinitely, cascading failures across the system.

**Effort**: Medium | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, integrate Resilience4j circuit breakers for all Feign client calls:
> 1. Add `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` to fund-transfer-service, user-service, and utility-payment-service
> 2. Configure circuit breaker with: failure rate threshold 50%, wait duration in open state 30s, sliding window size 10
> 3. Add fallback methods for each Feign client method that return appropriate error responses
> 4. Add retry configuration: max 3 attempts with exponential backoff (initial interval 500ms)
> 5. Ensure circuit breaker state is exposed via actuator (`/actuator/circuitbreakers`)
> 6. Add bulkhead configuration to limit concurrent calls to each downstream service

---

### 2.2 Create Shared Library Module (CO-2, CO-3)

**Problem**: Common code (mappers, DTOs, exceptions, filters) is duplicated across services. Changes require updates in 4 places.

**Effort**: Medium | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create a shared library module called `internet-banking-common`:
> 1. Extract common classes: `BaseMapper`, `AuditAware`, `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`
> 2. Extract shared DTOs used across service boundaries (e.g., `AccountResponse`, `FundTransferRequest`, `FundTransferResponse`, `UtilityPaymentRequest`, `UtilityPaymentResponse`)
> 3. Publish as a local Gradle module that other services depend on
> 4. Convert the project to a Gradle multi-project build with a root `settings.gradle` that includes all services
> 5. Remove duplicated code from individual services and replace with imports from the shared module
> 6. Ensure all services still build and tests pass

---

### 2.3 Add Unit Tests for All Services (T-1)

**Problem**: Only core-banking-service has meaningful tests. Other services have zero business logic tests.

**Effort**: Large | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add comprehensive unit tests for:
> 1. **FundTransferService**: Test successful transfer, test Feign client failure handling, test status transitions (PENDING → SUCCESS)
> 2. **UtilityPaymentService**: Test successful payment, test Feign client failure, test status transitions
> 3. **UserService**: Test registration flow (happy path), test duplicate email rejection, test invalid email, test user not found in core-banking, test approval flow enabling Keycloak user
> 4. **KeycloakUserService**: Test create, update, readByEmail, readUser methods with mocked KeycloakManager
>
> Use Mockito for mocking dependencies. Follow the existing test patterns in `AccountServiceTest`. Target 80% line coverage for service classes.

---

### 2.4 Handle Stuck Transactions (R-5, R-8)

**Problem**: Fund transfers and payments saved as `PENDING`/`PROCESSING` have no recovery mechanism if the downstream call fails.

**Effort**: Medium | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement transaction recovery for the fund-transfer-service and utility-payment-service:
> 1. Wrap the Feign client call in a try-catch. On failure, update the entity status to `FAILED` (instead of leaving it as `PENDING`/`PROCESSING`)
> 2. Add a scheduled job (`@Scheduled`) that runs every 5 minutes to find records stuck in `PENDING`/`PROCESSING` for more than 10 minutes and marks them as `FAILED`
> 3. Add a `failureReason` field to both `FundTransferEntity` and `UtilityPaymentEntity` to store the error message
> 4. Log the failure with sufficient detail for debugging
> 5. Add a new API endpoint `GET /api/v1/transfer/failed` and `GET /api/v1/utility-payment/failed` to list failed transactions for admin review

---

### 2.5 Implement Role-Based Access Control (S-5)

**Problem**: All authenticated users can access all endpoints. No admin vs. user distinction.

**Effort**: Medium | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement RBAC:
> 1. Define roles in Keycloak realm: `ROLE_USER` and `ROLE_ADMIN`
> 2. Update the API Gateway security configuration to extract roles from the JWT token
> 3. Forward roles to downstream services via a new header (e.g., `X-Auth-Roles`)
> 4. Restrict the following endpoints to `ROLE_ADMIN` only:
>    - `PATCH /api/v1/bank-users/update/{id}` (user approval)
>    - `GET /api/v1/bank-users` (list all users)
> 5. Regular users should only access their own data (transfers, payments)
> 6. Update the Keycloak realm export to include the new roles and assign them to test users

---

### 2.6 Secure Downstream Service Communication (S-3)

**Problem**: Downstream services blindly trust the `X-Auth-Id` header. Direct calls bypassing the gateway can spoof identity.

**Effort**: Medium | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, secure internal service communication:
> 1. Add network-level restriction so downstream services only accept traffic from the API gateway (Docker network policies or Spring Security IP filtering)
> 2. Alternatively, implement a shared internal JWT: the gateway signs the `X-Auth-Id` claim with a secret, and downstream services verify the signature before trusting the header
> 3. Add a `OncePerRequestFilter` in each downstream service that validates the internal token
> 4. Reject any request missing or with an invalid internal authentication token

---

### 2.7 Externalize Secrets from Source Code (S-2)

**Problem**: Database passwords and Keycloak credentials are hardcoded in docker-compose files committed to the repository.

**Effort**: Medium | **Severity**: Critical

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, externalize all secrets from version control:
> 1. Create a `.env.example` file documenting all required environment variables
> 2. Update `docker-compose.yml` to reference environment variables instead of hardcoded values (e.g., `${MYSQL_ROOT_PASSWORD}`)
> 3. Create a `.env` file (add to `.gitignore`) with development defaults
> 4. Update `privileges.sql` to use environment variables or a template
> 5. Add `.env` to `.gitignore` if not already present
> 6. Document the required secrets in the README

---

### 2.8 Add Feign Error Decoders (EH-3)

**Problem**: Fund-transfer and utility-payment services have no Feign error decoder. Downstream HTTP errors surface as opaque exceptions.

**Effort**: Small | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add a `CustomFeignErrorDecoder` to the fund-transfer-service and utility-payment-service (modeled after the one in user-service):
> 1. Map 404 responses to `EntityNotFoundException`
> 2. Map 422 responses to appropriate business exceptions
> 3. Map 5xx responses to a `ServiceUnavailableException`
> 4. Register the decoder in each service's `CustomFeignClientConfiguration`
> 5. Ensure meaningful error messages are extracted from the downstream error response body

---

## Phase 3: Polish (6-12 Weeks)

Enhancements that improve developer experience, API quality, and long-term maintainability.

### 3.1 Add Integration and Contract Tests (T-2, T-3)

**Problem**: No tests verify real database interactions or inter-service contracts.

**Effort**: Large | **Severity**: High

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add integration and contract tests:
> 1. Add Testcontainers to each service for MySQL integration tests
> 2. Write integration tests for repository layer (save, find, pagination)
> 3. Add Spring Cloud Contract for producer-side contract verification in core-banking-service
> 4. Write consumer contract stubs for fund-transfer-service and utility-payment-service
> 5. Add a CI-compatible `docker-compose-test.yml` that spins up required infrastructure for integration tests
> 6. Ensure `./gradlew integrationTest` runs all integration tests separately from unit tests

---

### 3.2 Add Structured JSON Logging (O-2)

**Problem**: Plain text logs are hard to parse in production. No correlation IDs for tracing requests across log entries.

**Effort**: Medium | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, implement structured JSON logging:
> 1. Add `net.logstash.logback:logstash-logback-encoder` to all services
> 2. Create a `logback-spring.xml` in each service that outputs JSON format in the `docker` profile and plain text in the default profile
> 3. Include trace ID, span ID, service name, and `X-Auth-Id` as MDC fields in every log entry
> 4. Ensure the Sleuth/Micrometer trace context is automatically included

---

### 3.3 Add Rate Limiting to API Gateway (R-6)

**Problem**: No rate limiting on the gateway. APIs are vulnerable to abuse.

**Effort**: Medium | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add rate limiting to the API Gateway:
> 1. Add `spring-cloud-gateway-redis-rate-limiter` or `bucket4j` dependency
> 2. Configure rate limits per user (based on JWT subject): 100 requests/minute for reads, 20 requests/minute for writes
> 3. Add a global rate limit of 1000 requests/minute per IP for unauthenticated endpoints
> 4. Return HTTP 429 with `Retry-After` header when rate limit is exceeded
> 5. Add Redis to the docker-compose for rate limit token storage

---

### 3.4 Implement Multi-Project Gradle Build (CO-1)

**Problem**: Each service is a standalone Gradle project with duplicated configuration.

**Effort**: Medium | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, convert to a Gradle multi-project build:
> 1. Create a root `settings.gradle` including all service modules
> 2. Create a root `build.gradle` with shared configuration (Java version, Spring Boot version, Spring Cloud version, common dependencies, repositories)
> 3. Reduce each service's `build.gradle` to only service-specific dependencies
> 4. Ensure `./gradlew build` from the root builds all services
> 5. Ensure `./gradlew :core-banking-service:test` still works for individual service builds

---

### 3.5 Add Pagination Metadata to List Responses (A-3)

**Problem**: List endpoints return plain arrays without pagination info (total count, page number, has next page).

**Effort**: Small | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, update all paginated list endpoints (fund transfers, utility payments, users) to return a response wrapper:
> ```json
> {
>   "content": [...],
>   "page": 0,
>   "size": 20,
>   "totalElements": 150,
>   "totalPages": 8,
>   "hasNext": true
> }
> ```
> Create a generic `PageResponse<T>` class in the shared module that wraps Spring's `Page<T>`. Update all service layer methods and controllers to use this wrapper.

---

### 3.6 Add Custom Health Indicators (O-4)

**Problem**: Default health checks only. No visibility into downstream dependency health.

**Effort**: Small | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add custom health indicators:
> 1. In user-service: Add a `KeycloakHealthIndicator` that checks connectivity to the Keycloak server
> 2. In fund-transfer-service and utility-payment-service: Add a `CoreBankingHealthIndicator` that pings the core-banking actuator health endpoint
> 3. Register these as Spring beans so they appear in `/actuator/health`
> 4. Ensure the health check uses a short timeout (2s) so it doesn't block the health endpoint

---

### 3.7 Add Dependency Vulnerability Scanning (S-7)

**Problem**: No automated check for known vulnerabilities in dependencies.

**Effort**: Small | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, add the OWASP Dependency Check Gradle plugin:
> 1. Add `org.owasp:dependency-check-gradle` plugin to the root or each service's `build.gradle`
> 2. Configure it to fail the build on CVSS score >= 7 (High and Critical)
> 3. Add a `./gradlew dependencyCheckAnalyze` task
> 4. Add an HTML report output to `build/reports/dependency-check/`
> 5. Document in README how to run the vulnerability scan

---

### 3.8 Add End-to-End Test Suite (T-5)

**Problem**: No automated test exercises the full flow through the API gateway.

**Effort**: Large | **Severity**: Medium

**Devin Prompt**:
> In the `ts-java-spring-boot-internet-banking-microservices` repo, create an end-to-end test module:
> 1. Create a new Gradle module `e2e-tests` that depends on RestAssured and Testcontainers
> 2. Use Docker Compose (via Testcontainers) to spin up the full stack
> 3. Write tests for the golden path flows:
>    - Register a user → Approve the user → Authenticate → Get JWT
>    - Perform a fund transfer → Verify balances updated
>    - Make a utility payment → Verify transaction recorded
> 4. Configure a separate Gradle task `./gradlew e2eTest` that runs these tests
> 5. Document how to run E2E tests locally in the README

---

## Priority Summary

| Phase | Items | Timeline | Key Outcome |
|-------|-------|----------|-------------|
| **Phase 1** | 8 items | 1-2 weeks | Eliminate critical security and reliability gaps with minimal code changes |
| **Phase 2** | 8 items | 3-6 weeks | Structural resilience, proper error handling, test coverage, and access control |
| **Phase 3** | 8 items | 6-12 weeks | Production-grade observability, developer experience, and long-term maintainability |

## Dependency Graph

```
Phase 1 (prerequisites for Phase 2):
  1.1 (Fix HTTP codes) → 2.8 (Feign error decoders need proper codes)
  1.2 (Input validation) → 2.3 (Tests need validation to test)
  1.3 (Timeouts) → 2.1 (Circuit breakers build on timeout config)

Phase 2 (prerequisites for Phase 3):
  2.2 (Shared library) → 3.4 (Multi-project build), 3.5 (PageResponse in shared lib)
  2.3 (Unit tests) → 3.1 (Integration tests build on unit test patterns)
  2.1 (Circuit breakers) → 3.3 (Rate limiting complements circuit breakers)
```
