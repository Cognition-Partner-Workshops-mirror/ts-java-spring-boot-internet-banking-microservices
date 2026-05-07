# Remediation Roadmap

This document organizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased remediation plan. Each phase is ordered by impact and dependency.

---

## Phase 1: Quick Wins (1–2 weeks)

High-impact items that can be resolved with small effort and minimal risk.

### 1.1 Fix HTTP Status Codes

**Gap:** All errors return HTTP 400 regardless of type.  
**Severity:** High | **Effort:** Small

**Action:** Update `GlobalExceptionHandler` in each service to return appropriate status codes:
- `EntityNotFoundException` → 404
- `InsufficientFundsException` → 422
- `UserAlreadyRegisteredException` → 409
- `InvalidEmailException` / `InvalidBankingUserException` → 400
- Generic `Exception` → 500

**Sample Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, update the GlobalExceptionHandler in all services (core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to return proper HTTP status codes: EntityNotFoundException should return 404, InsufficientFundsException should return 422, UserAlreadyRegisteredException should return 409, and the generic Exception handler should return 500. Keep the ErrorResponse body format unchanged.

---

### 1.2 Add Input Validation

**Gap:** No Bean Validation on request DTOs.  
**Severity:** Critical | **Effort:** Medium

**Action:**
1. Add `spring-boot-starter-validation` dependency to each service's `build.gradle`.
2. Annotate request DTO fields with `@NotNull`, `@NotBlank`, `@Min`, `@Email`, `@Size`.
3. Add `@Valid` to controller method parameters.
4. Handle `MethodArgumentNotValidException` in `GlobalExceptionHandler`.

**Sample Devin Prompt:**
> Add Bean Validation to all request DTOs in the ts-java-spring-boot-internet-banking-microservices repo. Add spring-boot-starter-validation to each service's build.gradle. Add @NotNull, @NotBlank, @Min(1) on amount fields, @Email on email fields, and @Size constraints on string fields. Add @Valid annotations to all @RequestBody parameters. Add a MethodArgumentNotValidException handler to each GlobalExceptionHandler that returns 400 with field-level error details.

---

### 1.3 Add Timeout Configuration

**Gap:** No explicit timeouts on Feign clients, DB connections, or gateway routes.  
**Severity:** High | **Effort:** Small

**Action:** Add to each service's externalized config (or `application.yml`):
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 5000
            read-timeout: 10000
```

**Sample Devin Prompt:**
> Add default Feign client timeout configuration to all services in the ts-java-spring-boot-internet-banking-microservices repo. Set connect-timeout to 5000ms and read-timeout to 10000ms using spring.cloud.openfeign.client.config.default properties in each service's application.yml. Also add spring.cloud.gateway.httpclient.connect-timeout=5000 and response-timeout=10s to the API gateway config.

---

### 1.4 Add Retry Policies

**Gap:** No retry configuration for transient failures.  
**Severity:** High | **Effort:** Small

**Action:** Add Spring Retry or Feign Retryer configuration:
```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            retryer: feign.Retryer.Default
```

**Sample Devin Prompt:**
> Add retry configuration to Feign clients in internet-banking-fund-transfer-service and internet-banking-utility-payment-service. Configure a Feign Retryer bean that retries up to 3 times with 100ms initial interval and 1s max interval. Only retry on 5xx responses and connection exceptions, not on 4xx client errors.

---

### 1.5 Externalize Credentials

**Gap:** Hardcoded passwords in docker-compose and SQL files.  
**Severity:** Critical | **Effort:** Small

**Action:**
1. Replace hardcoded values in `docker-compose.yml` with environment variable references.
2. Create a `.env.example` file documenting required variables.
3. Add `.env` to `.gitignore`.

**Sample Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, externalize all hardcoded credentials in docker-compose/docker-compose.yml and docker-compose/docker-compose-support-apps.yml. Replace values with ${VARIABLE} references (MYSQL_ROOT_PASSWORD, MYSQL_APP_PASSWORD, KEYCLOAK_ADMIN_PASSWORD, KEYCLOAK_DB_PASSWORD). Create a docker-compose/.env.example file documenting all required variables with placeholder values. Add .env to .gitignore.

---

### 1.6 Type Controller Return Types

**Gap:** Raw `ResponseEntity` without generics.  
**Severity:** Medium | **Effort:** Small

**Action:** Add generic type parameters to all controller methods (e.g., `ResponseEntity<FundTransferResponse>`).

**Sample Devin Prompt:**
> In the ts-java-spring-boot-internet-banking-microservices repo, add proper generic type parameters to all ResponseEntity return types across all controllers. For example, change `public ResponseEntity fundTransfer(...)` to `public ResponseEntity<FundTransferResponse> fundTransfer(...)`. Do this for all controller methods in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service.

---

### 1.7 Add Structured Logging

**Gap:** Plain text logs without consistent format.  
**Severity:** Medium | **Effort:** Small

**Action:** Add `logback-spring.xml` with JSON encoder for production profile.

**Sample Devin Prompt:**
> Add structured JSON logging to all services in the ts-java-spring-boot-internet-banking-microservices repo. Create a shared logback-spring.xml configuration that uses plain text for local/dev profile and JSON (using logstash-logback-encoder) for docker profile. Include traceId and spanId in log output. Add the logstash-logback-encoder dependency to each service's build.gradle.

---

### 1.8 Add Dependency Vulnerability Scanning

**Gap:** No OWASP/Snyk/Trivy in build.  
**Severity:** Medium | **Effort:** Small

**Action:** Add OWASP Dependency-Check Gradle plugin to each service.

**Sample Devin Prompt:**
> Add the OWASP Dependency-Check Gradle plugin (org.owasp.dependencycheck version 9.0.9) to all services in the ts-java-spring-boot-internet-banking-microservices repo. Configure it to fail the build on CVSS score >= 7. Add a root-level task that runs dependency checks across all services.

---

## Phase 2: Important (2–4 weeks)

Items requiring more effort but critical for production readiness.

### 2.1 Add Circuit Breakers

**Gap:** No circuit breaker protection on Feign calls.  
**Severity:** High | **Effort:** Medium

**Action:**
1. Add `spring-cloud-starter-circuitbreaker-resilience4j` to fund-transfer, utility-payment, and user services.
2. Configure circuit breaker instances for Core Banking calls.
3. Add fallback methods that return meaningful error responses.

**Sample Devin Prompt:**
> Add Resilience4j circuit breakers to Feign clients in the ts-java-spring-boot-internet-banking-microservices repo. Add spring-cloud-starter-circuitbreaker-resilience4j to internet-banking-fund-transfer-service, internet-banking-utility-payment-service, and internet-banking-user-service. Configure a circuit breaker named 'coreBanking' with slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s. Add fallback methods to each Feign client that return appropriate error responses.

---

### 2.2 Standardize Error Response Contract

**Gap:** Different error formats across services.  
**Severity:** High | **Effort:** Medium

**Action:** Define a unified error response contract and implement consistently:
```json
{
  "timestamp": "ISO-8601",
  "status": 404,
  "error": "Not Found",
  "code": "SERVICE-PREFIX-XXXX",
  "message": "Human-readable message",
  "path": "/api/v1/...",
  "traceId": "zipkin-trace-id"
}
```

**Sample Devin Prompt:**
> Standardize the error response format across all services in the ts-java-spring-boot-internet-banking-microservices repo. Create a unified ErrorResponse class with fields: timestamp (Instant), status (int), error (String), code (String), message (String), path (String), traceId (String). Update all GlobalExceptionHandler implementations to use this format. Include the Zipkin trace ID from the MDC in every error response. Ensure the gateway doesn't wrap downstream error responses.

---

### 2.3 Add Unit Tests for All Services

**Gap:** Only core-banking-service has meaningful tests.  
**Severity:** Critical | **Effort:** Large

**Action:** Add unit tests for service layer classes in all services with mocked dependencies.

**Sample Devin Prompt:**
> Add comprehensive unit tests to internet-banking-user-service in the ts-java-spring-boot-internet-banking-microservices repo. Test UserService.createUser (happy path, duplicate email, invalid identification, email mismatch), UserService.updateUser (approve flow, entity not found), and UserService.readUsers. Mock KeycloakUserService, UserRepository, and BankingCoreRestClient. Use JUnit 5, Mockito, and AssertJ. Aim for >80% coverage on the service layer.

**Sample Devin Prompt:**
> Add unit tests to internet-banking-fund-transfer-service in the ts-java-spring-boot-internet-banking-microservices repo. Test FundTransferService.fundTransfer (happy path, core banking failure, save entity lifecycle). Mock FundTransferRepository and BankingCoreFeignClient. Verify the entity transitions from PENDING to SUCCESS on happy path. Use JUnit 5, Mockito, and AssertJ.

---

### 2.4 Add Rate Limiting to API Gateway

**Gap:** No rate limiting on the gateway.  
**Severity:** Medium | **Effort:** Medium

**Action:** Add Spring Cloud Gateway Redis rate limiter or in-memory rate limiter.

**Sample Devin Prompt:**
> Add rate limiting to the internet-banking-api-gateway in the ts-java-spring-boot-internet-banking-microservices repo. Use Spring Cloud Gateway's built-in RequestRateLimiter filter with an in-memory rate limiter (no Redis required for dev). Configure a default rate of 100 requests/second per client IP. Add a stricter limit of 10 requests/minute on the /user/api/v1/bank-users/register endpoint to prevent brute-force registration.

---

### 2.5 Normalize Package Structure

**Gap:** Inconsistent package layout across services.  
**Severity:** Medium | **Effort:** Medium

**Action:** Standardize on a single convention:
```
com.javatodev.finance/
├── controller/
├── service/
│   └── client/          (Feign clients)
├── model/
│   ├── entity/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── mapper/
├── repository/
├── configuration/
│   ├── security/
│   ├── feign/
│   └── audit/
└── exception/
```

**Sample Devin Prompt:**
> Refactor the package structure of internet-banking-utility-payment-service in the ts-java-spring-boot-internet-banking-microservices repo to match this convention: controller/, service/, service/client/ (for Feign clients), model/entity/, model/dto/, model/dto/request/, model/dto/response/, model/mapper/, repository/, configuration/, exception/. Move classes to the correct packages and update all imports. Ensure the application still compiles and tests pass.

---

### 2.6 Add Pagination Metadata to List Endpoints

**Gap:** Raw lists with no pagination info.  
**Severity:** Medium | **Effort:** Medium

**Action:** Return `Page<T>` (Spring's default) or a custom wrapper with totalElements, totalPages, page, size.

**Sample Devin Prompt:**
> Update all paginated list endpoints in the ts-java-spring-boot-internet-banking-microservices repo to return proper pagination metadata. Instead of returning List<T>, return a PageResponse<T> wrapper with fields: content (List<T>), page (int), size (int), totalElements (long), totalPages (int). Apply this to GET /api/v1/transfer, GET /api/v1/utility-payment, GET /api/v1/bank-users, and GET /api/v1/user in core-banking-service. Keep backward compatibility by defaulting page=0, size=20.

---

### 2.7 Complete OpenAPI Configuration

**Gap:** Incomplete Swagger documentation.  
**Severity:** Medium | **Effort:** Small

**Action:** 
1. Switch from `springdoc-openapi-starter-webflux-ui` to `springdoc-openapi-starter-webmvc-ui` for non-reactive services.
2. Add global OpenAPI configuration (title, version, security schemes).
3. Add `@ApiResponse` annotations for error cases.

**Sample Devin Prompt:**
> Fix and complete the OpenAPI/Swagger configuration in the ts-java-spring-boot-internet-banking-microservices repo. Replace springdoc-openapi-starter-webflux-ui with springdoc-openapi-starter-webmvc-ui (version 2.1.0) in core-banking-service, internet-banking-user-service, internet-banking-fund-transfer-service, and internet-banking-utility-payment-service (keep webflux for the gateway). Add an OpenApiConfig class to each service that sets title, version, and description. Add @ApiResponse annotations for 400, 404, and 500 error cases on all controller methods.

---

### 2.8 Add Custom Health Indicators

**Gap:** No health checks for Keycloak, Feign targets.  
**Severity:** Low | **Effort:** Small

**Action:** Add custom `HealthIndicator` implementations.

**Sample Devin Prompt:**
> Add custom health indicators to internet-banking-user-service in the ts-java-spring-boot-internet-banking-microservices repo. Create a KeycloakHealthIndicator that checks connectivity to Keycloak by calling the realm endpoint. Create a CoreBankingHealthIndicator that pings the core-banking-service actuator/health endpoint via Feign. Register both as Spring beans so they appear in /actuator/health.

---

### 2.9 Add Bulkhead Isolation

**Gap:** Shared thread pools for all Feign calls.  
**Severity:** Medium | **Effort:** Medium

**Action:** Configure Resilience4j Bulkhead instances for Feign client thread isolation.

**Sample Devin Prompt:**
> Add Resilience4j bulkhead configuration to internet-banking-fund-transfer-service and internet-banking-utility-payment-service in the ts-java-spring-boot-internet-banking-microservices repo. Configure a thread-pool-based bulkhead for core-banking-service Feign calls with maxConcurrentCalls=10, maxWaitDuration=500ms. This ensures slow calls to core banking don't exhaust the entire thread pool.

---

## Phase 3: Polish (4–8 weeks)

Items that improve engineering maturity and long-term maintainability.

### 3.1 Extract Shared Library Module

**Gap:** Duplicated code across services.  
**Severity:** Medium | **Effort:** Large

**Action:** Create a `banking-common` Gradle module with shared classes:
- `AuditAware`, `BaseMapper`
- `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
- `ErrorResponse`, `SimpleBankingGlobalException`, `GlobalExceptionHandler`
- Common DTOs and exception classes

**Sample Devin Prompt:**
> Create a shared Gradle module called banking-common in the ts-java-spring-boot-internet-banking-microservices repo. Move the following duplicated classes into it: AuditAware, BaseMapper, AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder, ErrorResponse, SimpleBankingGlobalException, and a base GlobalExceptionHandler. Configure it as a plain Java library (no Spring Boot plugin). Add it as a dependency to all business services. Remove the duplicated classes from each service and update imports.

---

### 3.2 Add Integration Tests with Testcontainers

**Gap:** No integration tests verifying cross-service flows.  
**Severity:** High | **Effort:** Large

**Action:** Add integration tests using Testcontainers for MySQL, Keycloak, and WireMock for Feign clients.

**Sample Devin Prompt:**
> Add integration tests to internet-banking-user-service in the ts-java-spring-boot-internet-banking-microservices repo using Testcontainers. Set up a MySQL Testcontainer for the user database and WireMock for the core-banking-service Feign client. Test the full user registration flow: POST /api/v1/bank-users/register with a valid request, verify the entity is persisted in MySQL, and verify Keycloak was called (mock Keycloak with WireMock). Add testcontainers and wiremock dependencies to build.gradle.

---

### 3.3 Add Contract Tests

**Gap:** No consumer-driven contracts between services.  
**Severity:** Medium | **Effort:** Large

**Action:** Implement Spring Cloud Contract for the Core Banking Service (provider) with consumers generating stubs.

**Sample Devin Prompt:**
> Add Spring Cloud Contract tests to core-banking-service in the ts-java-spring-boot-internet-banking-microservices repo. Define contracts for: GET /api/v1/account/bank-account/{number} (returns account), POST /api/v1/transaction/fund-transfer (success and insufficient funds cases), POST /api/v1/transaction/util-payment (success case). Generate WireMock stubs that consumer services (fund-transfer, utility-payment) can use in their integration tests.

---

### 3.4 Add Filtering and Sorting to List Endpoints

**Gap:** No domain-specific query capabilities.  
**Severity:** Medium | **Effort:** Medium

**Action:** Add query parameters for common filters.

**Sample Devin Prompt:**
> Add filtering capabilities to the GET /api/v1/transfer endpoint in internet-banking-fund-transfer-service. Accept optional query parameters: status (TransactionStatus), fromAccount (String), toAccount (String), fromDate (LocalDate), toDate (LocalDate). Use Spring Data JPA Specifications to build dynamic queries. Add similar filtering to GET /api/v1/utility-payment in the utility payment service with status, account, and date range filters.

---

### 3.5 Implement RabbitMQ Notifications

**Gap:** Planned but unimplemented messaging.  
**Severity:** Medium | **Effort:** Large

**Action:** Add RabbitMQ producer to fund-transfer and utility-payment services; create a notification consumer service.

**Sample Devin Prompt:**
> Implement RabbitMQ messaging in the ts-java-spring-boot-internet-banking-microservices repo. Add spring-boot-starter-amqp to internet-banking-fund-transfer-service and internet-banking-utility-payment-service. After a successful transfer/payment, publish a notification event to a RabbitMQ exchange (topic: banking.notifications). Create a new internet-banking-notification-service that consumes these messages and logs them. Add a RabbitMQ container to docker-compose.yml.

---

### 3.6 Add Prometheus + Grafana Monitoring

**Gap:** No metrics dashboards.  
**Severity:** Medium | **Effort:** Medium

**Action:** Add Prometheus scrape config and Grafana dashboards to docker-compose.

**Sample Devin Prompt:**
> Add Prometheus and Grafana to the docker-compose setup in the ts-java-spring-boot-internet-banking-microservices repo. Add prometheus and grafana containers to docker-compose.yml. Create a prometheus.yml that scrapes /actuator/prometheus from all services. Create a Grafana dashboard JSON for: JVM metrics, HTTP request rates, response times, and error rates. Ensure all services expose the /actuator/prometheus endpoint by adding the micrometer-registry-prometheus dependency.

---

### 3.7 Implement Fallback Behavior

**Gap:** No graceful degradation.  
**Severity:** Medium | **Effort:** Medium

**Action:** Add fallback implementations for Feign clients.

**Sample Devin Prompt:**
> Add fallback implementations for all Feign clients in the ts-java-spring-boot-internet-banking-microservices repo. In internet-banking-fund-transfer-service, create a BankingCoreFeignClientFallback that returns a FundTransferResponse with status FAILED and a meaningful error message when core-banking-service is unavailable. Similarly add fallbacks in internet-banking-utility-payment-service and internet-banking-user-service. Wire the fallbacks using @FeignClient(fallbackFactory = ...).

---

### 3.8 Add Security Headers

**Gap:** No CORS, HSTS, or other security headers.  
**Severity:** Low | **Effort:** Small

**Action:** Configure security headers in the API Gateway.

**Sample Devin Prompt:**
> Add security headers to the internet-banking-api-gateway in the ts-java-spring-boot-internet-banking-microservices repo. Configure the SecurityWebFilterChain to add: Strict-Transport-Security (max-age=31536000), X-Content-Type-Options (nosniff), X-Frame-Options (DENY), and a CORS policy allowing configurable origins. Use Spring Security's headers() DSL for the reactive gateway.

---

## Execution Priority Matrix

```
                    HIGH IMPACT
                        │
    ┌───────────────────┼───────────────────┐
    │ Phase 1:          │ Phase 2:          │
    │ • Input validation│ • Circuit breakers│
    │ • HTTP status     │ • Error contract  │
    │ • Credentials     │ • Unit tests      │
    │ • Timeouts        │ • Rate limiting   │
    │ • Retries         │                   │
LOW ├───────────────────┼───────────────────┤ HIGH
EFFORT│ Phase 1:        │ Phase 3:          │ EFFORT
    │ • Typed returns   │ • Shared library  │
    │ • Logging         │ • Integration tests│
    │ • Dep scanning    │ • Contract tests  │
    │                   │ • RabbitMQ        │
    │                   │ • Monitoring      │
    └───────────────────┼───────────────────┘
                        │
                    LOW IMPACT
```

---

## Success Metrics

| Phase | Key Result | Measurement |
|-------|-----------|-------------|
| Phase 1 | Zero hardcoded credentials in repo | `git grep -i password` returns 0 results |
| Phase 1 | All requests validated | No NullPointerException from invalid input in logs |
| Phase 1 | Proper HTTP semantics | 404s for missing resources, 422 for business rule violations |
| Phase 2 | >80% unit test coverage on service layer | JaCoCo report |
| Phase 2 | System survives core-banking 30s outage | Chaos test: circuit breaker opens, fallback serves |
| Phase 2 | Registration endpoint rate-limited | Load test: >10 req/min from same IP returns 429 |
| Phase 3 | Shared library eliminates code duplication | <5% duplicated code (SonarQube) |
| Phase 3 | Full observability stack | Alert fires within 60s of service degradation |
