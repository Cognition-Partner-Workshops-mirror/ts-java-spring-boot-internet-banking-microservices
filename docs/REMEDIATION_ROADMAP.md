# Remediation Roadmap

This document prioritizes the gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md) into a phased remediation plan. Each phase includes estimated effort, expected impact, and sample Devin prompts to execute the remediation.

---

## Phase 1: Quick Wins (1-2 Weeks)

High-impact items that can be resolved quickly with minimal risk.

### 1.1 Fix Error Response Consistency

**Gap**: Generic catch-all returns 400 for all errors; inconsistent format
**Severity**: Critical | **Effort**: Small-Medium

**What to do**:
- Map `EntityNotFoundException` to HTTP 404
- Map `InsufficientFundsException` to HTTP 422
- Map `UserAlreadyRegisteredException` to HTTP 409
- Remove exception details from generic error responses
- Standardize error response format across all services

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, refactor the GlobalExceptionHandler
in all 4 business services (core-banking-service, internet-banking-user-service,
internet-banking-fund-transfer-service, internet-banking-utility-payment-service) to:
1. Return 404 for EntityNotFoundException
2. Return 422 for InsufficientFundsException
3. Return 409 for UserAlreadyRegisteredException
4. Return a structured ErrorResponse{code, message, timestamp, traceId} for ALL exceptions
5. Never expose exception class names or stack traces in responses
6. Return 500 for unhandled exceptions
Keep the existing unit tests passing and add tests for error handling.
```

---

### 1.2 Add Input Validation

**Gap**: No `@Valid` annotations or Bean Validation constraints
**Severity**: Critical | **Effort**: Medium

**What to do**:
- Add `spring-boot-starter-validation` dependency
- Add constraints to all DTOs (`@NotNull`, `@NotBlank`, `@Positive`, `@Email`, etc.)
- Add `@Valid` to all `@RequestBody` parameters
- Handle `MethodArgumentNotValidException` in `GlobalExceptionHandler`

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Bean Validation to all
REST API request DTOs across all services:
1. Add spring-boot-starter-validation to each service's build.gradle
2. Add @NotNull, @NotBlank, @Positive, @Email, @Size constraints to:
   - FundTransferRequest (fromAccount not blank, toAccount not blank, amount positive)
   - UtilityPaymentRequest (account not blank, providerId not null, amount positive)
   - User DTO (email valid format, identification not blank)
3. Add @Valid annotation to all @RequestBody parameters in controllers
4. Add a handler for MethodArgumentNotValidException in GlobalExceptionHandler
   that returns 400 with field-level error details
```

---

### 1.3 Remove Sensitive Data from Logs

**Gap**: Full request objects (including passwords) logged
**Severity**: High | **Effort**: Small

**What to do**:
- Remove `request.toString()` from log statements
- Log only non-sensitive identifiers (account number suffix, user ID)
- Exclude `password` field from `User.toString()`

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix sensitive data exposure in logs:
1. In internet-banking-user-service UserController, don't log the full user request
   (it contains password). Log only the email.
2. In all controllers, replace log statements that log full request objects with
   sanitized versions that only include non-sensitive identifiers.
3. Add @ToString.Exclude on the password field in the User DTO.
4. Ensure no credentials, passwords, or full account numbers appear in log output.
```

---

### 1.4 Configure Feign Timeouts

**Gap**: No timeout configuration for Feign clients
**Severity**: High | **Effort**: Small

**What to do**:
- Set connection and read timeouts for all Feign clients
- Configure via application.yml or Feign configuration classes

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add timeout configuration
for all Feign clients (fund-transfer-service, utility-payment-service, user-service):
1. Set connect timeout to 5 seconds
2. Set read timeout to 10 seconds
3. Configure via spring.cloud.openfeign.client.config in each service's application.yml
4. Add a comment explaining the timeout values chosen
```

---

### 1.5 Fix OpenAPI Dependency

**Gap**: Wrong SpringDoc starter used (`webflux-ui` instead of `webmvc-ui`)
**Severity**: Medium | **Effort**: Small

**What to do**:
- Replace `springdoc-openapi-starter-webflux-ui` with `springdoc-openapi-starter-webmvc-ui` in non-gateway services
- Verify Swagger UI loads correctly

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, fix the OpenAPI dependency:
1. In core-banking-service, internet-banking-fund-transfer-service,
   internet-banking-user-service, and internet-banking-utility-payment-service,
   replace 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.1.0'
   with 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.1.0'
2. These services use Spring MVC (not WebFlux), so webmvc-ui is the correct dependency.
3. Leave the API Gateway as-is since it uses WebFlux.
```

---

### 1.6 Add Correlation IDs to Error Responses

**Gap**: No trace/correlation IDs in error responses
**Severity**: Medium | **Effort**: Small

**What to do**:
- Extract trace ID from Micrometer tracing context
- Include `traceId` field in all `ErrorResponse` objects

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add trace ID to error responses:
1. Update ErrorResponse to include a traceId field
2. In each GlobalExceptionHandler, inject Tracer from Micrometer
3. Extract current trace ID using tracer.currentSpan().context().traceId()
4. Include it in all error responses for client-server correlation
```

---

### 1.7 Fix Keycloak Singleton Thread Safety

**Gap**: Lazy singleton with race condition
**Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices/internet-banking-user-service,
fix the thread-safety issue in KeycloakProperties.getInstance():
1. Either make the method synchronized, or
2. Better: initialize the Keycloak instance eagerly in a @PostConstruct method,
   or use a Spring @Bean method to create it as a singleton bean.
Remove the static field and let Spring manage the lifecycle.
```

---

## Phase 2: Important (3-6 Weeks)

Structural improvements that require more effort but significantly improve maintainability and reliability.

### 2.1 Add Circuit Breakers with Resilience4j

**Gap**: No circuit breakers; cascading failures possible
**Severity**: Critical | **Effort**: Medium

**What to do**:
- Add `spring-cloud-starter-circuitbreaker-resilience4j` to services with Feign clients
- Configure circuit breakers for all Feign client calls
- Add fallback methods that return meaningful error responses
- Configure sliding window, failure rate threshold, and wait duration

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Resilience4j circuit breakers:
1. Add spring-cloud-starter-circuitbreaker-resilience4j to fund-transfer-service,
   utility-payment-service, and user-service build.gradle files
2. Enable circuit breaker with Feign: spring.cloud.openfeign.circuitbreaker.enabled=true
3. Create fallback classes for each Feign client:
   - BankingCoreFeignClientFallback
   - BankingCoreRestClientFallback
4. Configure circuit breaker in application.yml:
   - slidingWindowSize: 10
   - failureRateThreshold: 50
   - waitDurationInOpenState: 30s
5. Fallback methods should throw a ServiceUnavailableException (503)
```

---

### 2.2 Add Retry Policies

**Gap**: No retry for transient failures
**Severity**: High | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add retry policies for Feign clients:
1. Add spring-retry dependency to fund-transfer, utility-payment, and user services
2. Configure retry via application.yml:
   - maxAttempts: 3
   - backoff: 1000ms with multiplier 2.0
3. Only retry on 5xx responses and connection exceptions
4. Do NOT retry on 4xx responses (client errors should not be retried)
5. Ensure retries work with circuit breaker (retry inside circuit breaker)
```

---

### 2.3 Implement Service-to-Service Authentication

**Gap**: Downstream services accessible without auth
**Severity**: High | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, secure inter-service communication:
1. Add spring-boot-starter-security to core-banking-service,
   fund-transfer-service, utility-payment-service, and user-service
2. Configure each service to validate JWT tokens (same Keycloak issuer as gateway)
3. Alternatively, implement a shared secret/API key for service-to-service calls
   propagated via the X-Auth-Id header from the gateway
4. Ensure actuator/health endpoints remain publicly accessible
5. Update Feign client configurations to propagate the Authorization header
```

---

### 2.4 Add Unit Tests for All Services

**Gap**: Only core-banking-service has meaningful tests
**Severity**: Critical | **Effort**: Large

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add comprehensive unit tests:
1. internet-banking-fund-transfer-service:
   - FundTransferServiceTest: test fundTransfer success, failure (insufficient funds,
     account not found), and readAllTransfers
   - FundTransferControllerTest: test REST endpoints with MockMvc
2. internet-banking-utility-payment-service:
   - UtilityPaymentServiceTest: test utilPayment success/failure, readPayments
   - UtilityPaymentControllerTest: test REST endpoints with MockMvc
3. internet-banking-user-service:
   - UserServiceTest: test createUser (success, duplicate email, invalid email,
     user not found), readUser, updateUser (approve flow)
   - UserControllerTest: test REST endpoints with MockMvc
4. Use Mockito for mocking dependencies
5. Aim for >80% line coverage on service and controller layers
```

---

### 2.5 Add Structured Logging

**Gap**: Text-based logs, no JSON format
**Severity**: High | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, implement structured JSON logging:
1. Add logstash-logback-encoder dependency to all services
2. Create a logback-spring.xml in each service's resources:
   - JSON format for production/docker profile
   - Console text format for local development
3. Include MDC fields: traceId, spanId, serviceName, userId
4. Configure the AppAuthUserFilter to set userId in MDC
5. Ensure sensitive data is never logged (mask account numbers, exclude passwords)
```

---

### 2.6 Add Idempotency for Financial Operations

**Gap**: No idempotency controls; duplicate transfers possible
**Severity**: High | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add idempotency support:
1. Add an X-Idempotency-Key header requirement for POST /api/v1/transfer
   and POST /api/v1/utility-payment
2. Create an idempotency_key table in each service's database
3. Before processing a request, check if the idempotency key was already used
4. If already processed, return the cached response (same status code and body)
5. If new, process normally and store the response with the key
6. Add TTL/expiry for stored keys (e.g., 24 hours)
```

---

### 2.7 Create Shared Library Module

**Gap**: Duplicated code across services
**Severity**: High | **Effort**: Large

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, create a shared library module:
1. Create a root settings.gradle that includes all services as subprojects
2. Create a new module: internet-banking-common
3. Move shared code into the common module:
   - BaseMapper interface
   - AuditAware base class
   - AppAuthUserFilter, ApiRequestContext, ApiRequestContextHolder
   - ErrorResponse, SimpleBankingGlobalException, GlobalExceptionHandler base
   - Common exception classes (EntityNotFoundException, etc.)
4. Update each service's build.gradle to depend on the common module
5. Remove duplicated classes from individual services
6. Ensure all existing tests still pass
```

---

### 2.8 Fix Pagination Consistency

**Gap**: Some endpoints lose pagination metadata
**Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, standardize pagination responses:
1. Create a PageResponse<T> wrapper in a shared location with fields:
   content, page, size, totalElements, totalPages
2. Update FundTransferController.readFundTransfers to return PageResponse<FundTransfer>
3. Update UtilityPaymentController.readPayments to return PageResponse<UtilityPayment>
4. Update corresponding service methods to preserve pagination metadata
5. Keep backward compatibility by including the content array at top level
```

---

### 2.9 Add Custom Health Indicators

**Gap**: No health checks for critical dependencies
**Severity**: Medium | **Effort**: Small

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add custom health indicators:
1. In user-service: add KeycloakHealthIndicator that pings Keycloak realm endpoint
2. In fund-transfer-service and utility-payment-service: add a health indicator
   that checks Core Banking Service availability via Eureka
3. In all services: ensure DataSourceHealthIndicator is active (default with actuator)
4. Configure management.endpoint.health.show-details=always for non-production profiles
```

---

## Phase 3: Polish (6-12 Weeks)

Items that improve long-term maintainability and operational excellence.

### 3.1 Add Integration Tests with Testcontainers

**Gap**: No integration testing
**Severity**: High | **Effort**: Large

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add integration tests:
1. Add Testcontainers dependency (mysql, keycloak) to each service
2. For core-banking-service:
   - Create integration test with real MySQL container
   - Test full fund transfer flow through service layer
   - Verify Flyway migrations run correctly
3. For user-service:
   - Create integration test with MySQL + Keycloak containers
   - Test user registration end-to-end
4. For fund-transfer-service and utility-payment-service:
   - Create integration tests with MySQL container
   - Use WireMock for Core Banking Service responses
5. Configure separate test profile with Testcontainers
```

---

### 3.2 Add Contract Tests Between Services

**Gap**: No contract tests; API drift risk
**Severity**: Medium | **Effort**: Large

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add Spring Cloud Contract tests:
1. Add spring-cloud-starter-contract-verifier to core-banking-service (provider)
2. Add spring-cloud-starter-contract-stub-runner to consumer services
3. Define contracts for:
   - GET /api/v1/account/bank-account/{number} (used by fund-transfer, utility-payment)
   - POST /api/v1/transaction/fund-transfer (used by fund-transfer-service)
   - POST /api/v1/transaction/util-payment (used by utility-payment-service)
   - GET /api/v1/user/{identification} (used by user-service)
4. Generate and publish stubs from core-banking-service
5. Consumer services use stubs for their Feign client tests
```

---

### 3.3 Add Business Metrics

**Gap**: No custom application metrics
**Severity**: Medium | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add custom business metrics:
1. Add micrometer-registry-prometheus to all services
2. In core-banking-service TransactionService:
   - Counter: banking.transactions.total (tags: type=FUND_TRANSFER|UTILITY_PAYMENT, status=SUCCESS|FAILED)
   - Timer: banking.transactions.duration
   - Gauge: banking.accounts.balance.total
3. In fund-transfer-service:
   - Counter: banking.fund_transfers.initiated
   - Counter: banking.fund_transfers.completed
4. In utility-payment-service:
   - Counter: banking.utility_payments.processed
5. Expose /actuator/prometheus endpoint
6. Add a sample Grafana dashboard JSON to docs/
```

---

### 3.4 Add Rate Limiting

**Gap**: No rate limiting on sensitive endpoints
**Severity**: Medium | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add rate limiting at the API Gateway:
1. Add Spring Cloud Gateway RequestRateLimiter filter
2. Configure rate limits:
   - /user/api/v1/bank-users/register: 5 requests/minute per IP
   - /fund-transfer/api/v1/transfer: 20 requests/minute per user
   - /utility-payment/api/v1/utility-payment: 20 requests/minute per user
3. Use Redis (add redis to docker-compose) or in-memory rate limiter
4. Return 429 Too Many Requests with Retry-After header when limit exceeded
5. Add rate limit headers to responses (X-RateLimit-Remaining, X-RateLimit-Limit)
```

---

### 3.5 Externalize Credentials

**Gap**: Hardcoded credentials in version control
**Severity**: Critical (but low risk in dev-only context) | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, externalize all credentials:
1. Replace hardcoded passwords in docker-compose.yml with environment variables:
   - MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
   - KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD}
2. Create a .env.example file with placeholder values
3. Add .env to .gitignore
4. Update docker-compose to use env_file directive
5. Move the MySQL privileges.sql password to an environment variable
6. Document the required environment variables in README.md
```

---

### 3.6 Add Bulkhead Pattern

**Gap**: No thread pool isolation
**Severity**: Medium | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, add bulkhead isolation:
1. Add Resilience4j bulkhead configuration to fund-transfer and utility-payment services
2. Configure separate thread pools for:
   - Feign calls to Core Banking Service (maxConcurrentCalls: 10)
   - Database operations (use connection pool sizing)
3. Configure in application.yml:
   resilience4j.bulkhead.instances.coreBanking:
     maxConcurrentCalls: 10
     maxWaitDuration: 5s
4. Apply @Bulkhead annotation or configure via Feign integration
```

---

### 3.7 Implement API Gateway Aggregated Documentation

**Gap**: No unified API documentation
**Severity**: Medium | **Effort**: Medium

**Sample Devin Prompt**:
```
In ts-java-spring-boot-internet-banking-microservices, set up aggregated OpenAPI docs:
1. Add springdoc-openapi-starter-webflux-api to the API Gateway
2. Configure route-based group definitions for each service
3. Each downstream service exposes /v3/api-docs
4. Gateway aggregates all specs at /swagger-ui.html
5. Configure springdoc.swagger-ui.urls in gateway application.yml to list all services
6. Add proper @Schema annotations to all DTOs for rich documentation
```

---

## Implementation Priority Matrix

```
                    HIGH IMPACT
                        |
    Phase 1:            |           Phase 2:
    Quick Wins          |           Important
    (1.1, 1.2, 1.3,    |           (2.1, 2.2, 2.3,
     1.4)              |            2.4, 2.5, 2.6)
                        |
  LOW EFFORT -----------+----------- HIGH EFFORT
                        |
    Phase 1:            |           Phase 3:
    Easy Polish         |           Strategic
    (1.5, 1.6, 1.7)    |           (3.1, 3.2, 3.3,
                        |            3.4, 3.5, 3.6, 3.7)
                        |
                    LOW IMPACT
```

---

## Recommended Execution Order

1. **Week 1**: Items 1.1, 1.2, 1.3 (security and error handling fundamentals)
2. **Week 2**: Items 1.4, 1.5, 1.6, 1.7 (quick configuration fixes)
3. **Weeks 3-4**: Items 2.1, 2.2, 2.3 (resilience and security)
4. **Weeks 5-6**: Items 2.4, 2.5 (testing and observability)
5. **Weeks 7-8**: Items 2.6, 2.7, 2.8, 2.9 (code quality and consistency)
6. **Weeks 9-12**: Phase 3 items (strategic improvements)

---

## Success Criteria

| Phase | Metric | Target |
|-------|--------|--------|
| Phase 1 | Error responses use correct HTTP status codes | 100% |
| Phase 1 | All request DTOs have validation constraints | 100% |
| Phase 1 | No sensitive data in log output | 0 occurrences |
| Phase 2 | Unit test coverage (service + controller layers) | > 80% |
| Phase 2 | Circuit breaker configured for all Feign clients | 100% |
| Phase 2 | All inter-service calls authenticated | 100% |
| Phase 3 | Integration test coverage | > 60% |
| Phase 3 | Business metrics exposed | > 5 custom metrics per service |
| Phase 3 | All credentials externalized | 0 hardcoded secrets |
