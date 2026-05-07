# Engineering Standards Gap Analysis

This document compares the codebase against industry engineering best practices and documents gaps with severity ratings and remediation effort estimates.

**Severity Scale:**
- **Critical** — Security vulnerability, data loss risk, or production outage potential
- **High** — Significant quality/reliability issue that should be addressed before production
- **Medium** — Notable deviation from best practices that impacts maintainability or operability
- **Low** — Minor improvement opportunity; polish or consistency issue

**Effort Scale:**
- **Small** — < 1 day per service, localized change
- **Medium** — 1–3 days, may span multiple files or services
- **Large** — > 3 days, architectural or cross-cutting change

---

## 1. Code Organization

### GAP-ORG-01: No Multi-Project Gradle Build
**Severity:** Medium | **Effort:** Small

Each service is an independent Gradle project with its own `build.gradle` and `settings.gradle`. There is no root-level `settings.gradle` linking them as subprojects.

**Impact:** Dependency versions (Spring Boot 3.2.4, Spring Cloud 2023.0.0, MySQL connector 8.4.0, etc.) are duplicated across 6 `build.gradle` files. Version drift between services is likely over time.

**Current state:** All services currently declare identical versions, but there is no mechanism to enforce this.

### GAP-ORG-02: Duplicated Code Across Services (No Shared Library)
**Severity:** Medium | **Effort:** Medium

The following classes are **copy-pasted identically** across 3–4 services with no shared library:
- `BaseMapper<E, D>` — identical in user-service, fund-transfer-service, utility-payment-service, core-banking-service
- `AuditAware` — identical in user-service, fund-transfer-service, utility-payment-service
- `SimpleBankingGlobalException` — identical in all 4 business services
- `ErrorResponse` — identical in all 4 business services
- `GlobalExceptionHandler` — nearly identical in all 4 business services
- `AppAuthUserFilter` / `ApiRequestContext` / `ApiRequestContextHolder` — duplicated in user-service, fund-transfer-service, utility-payment-service
- `AccountResponse` DTO — duplicated in fund-transfer-service and utility-payment-service

**Impact:** Bug fixes or improvements must be applied to each copy independently, leading to inconsistency.

### GAP-ORG-03: Inconsistent Package Structure
**Severity:** Low | **Effort:** Small

Package organization varies between services:
- **core-banking-service:** `repository/` at top-level
- **user-service:** `model/repository/` nested under model
- **utility-payment-service:** `repository/` at top-level, `model/rest/` for DTOs
- **fund-transfer-service:** `model/repository/`, `model/dto/request/`, `model/dto/response/`

Configuration packages also differ:
- **user-service:** `configuration/feign/`, `configuration/filter/`, `configuration/keycloak/`, `configuration/audit/`
- **utility-payment-service:** `configuration/` (flat), `configuration/audit/`, `configuration/filter/`
- **fund-transfer-service:** `configuration/` (flat), `configuration/filter/`

### GAP-ORG-04: Mapper Instantiation Inconsistency
**Severity:** Low | **Effort:** Small

Mappers are instantiated via `new` in service classes rather than being Spring-managed beans:
```java
private FundTransferMapper mapper = new FundTransferMapper();  // Not injected
```
This prevents leveraging dependency injection for testing and configuration.

---

## 2. Error Handling

### GAP-ERR-01: All Errors Return HTTP 400 Bad Request
**Severity:** High | **Effort:** Medium

Every `GlobalExceptionHandler` maps all exceptions (including `EntityNotFoundException`) to `ResponseEntity.badRequest()` (HTTP 400). This violates HTTP semantics:
- Entity not found should return **404 Not Found**
- Insufficient funds should return **422 Unprocessable Entity** or **409 Conflict**
- Unexpected exceptions should return **500 Internal Server Error**
- The catch-all `Exception` handler returns 400 with a raw exception string, potentially leaking stack traces

**Current pattern (all services):**
```java
@ExceptionHandler({Exception.class})
protected ResponseEntity handleException(Exception e, Locale locale) {
    return ResponseEntity.badRequest()
        .body("Exception occur inside API " + e);  // Leaks internal details
}
```

### GAP-ERR-02: Inconsistent Error Response Formats
**Severity:** Medium | **Effort:** Small

- `SimpleBankingGlobalException` handler returns `ErrorResponse` (structured JSON with `code` and `message`)
- Generic `Exception` handler returns a raw `String` ("Exception occur inside API " + e)
- Clients cannot rely on a consistent error response schema

### GAP-ERR-03: No Error Handling for Feign Client Failures
**Severity:** High | **Effort:** Medium

- **Fund Transfer Service:** Uses `CustomFeignClientConfiguration` but has no `FeignErrorDecoder`
- **Utility Payment Service:** Has `CustomFeignClientConfiguration` but no error decoder
- **User Service:** Has `CustomFeignErrorDecoder` (the only service with one), but its implementation needs review
- If core-banking-service returns an error, the calling service may throw an unhandled `FeignException` resulting in an opaque 400/500 response

### GAP-ERR-04: No Transaction Rollback on Failure
**Severity:** Critical | **Effort:** Medium

In `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()`:
1. A record is saved with `PENDING`/`PROCESSING` status
2. A Feign call is made to core-banking-service
3. If the Feign call fails **after** step 1, the local record remains in `PENDING`/`PROCESSING` state with no mechanism to recover or retry

The `status` is never set to `FAILED` in any error path. No `@Transactional` annotation is used to manage the local transaction boundary.

In `TransactionService.internalFundTransfer()` (core-banking-service):
- Multiple `bankAccountRepository.save()` and `transactionRepository.save()` calls are made without `@Transactional`
- If a failure occurs mid-transfer, one account could be debited without the other being credited

---

## 3. Testing

### GAP-TEST-01: Minimal Test Coverage
**Severity:** High | **Effort:** Large

| Service | Test Files | Test Content |
|---------|-----------|-------------|
| core-banking-service | `CoreBankingServiceApplicationTests` + 3 unit test classes | Context load + unit tests for AccountService, TransactionService, UserService |
| internet-banking-user-service | `InternetBankingUserServiceApplicationTests` | Context load only |
| internet-banking-fund-transfer-service | `InternetBankingFundTransferServiceApplicationTests` | Context load only |
| internet-banking-utility-payment-service | `InternetBankingUtilityPaymentServiceApplicationTests` | Context load only |
| internet-banking-api-gateway | `InternetBankingApiGatewayApplicationTests` | Context load only |
| internet-banking-config-server | `InternetBankingConfigServerApplicationTests` | Context load only |
| internet-banking-service-registry | `InternetBankingServiceRegistryApplicationTests` | Context load only |

- **Core banking service** has reasonable unit test coverage for its service layer
- **All other services** have zero meaningful tests — only Spring context load tests (which likely fail without running infrastructure)
- No controller/integration tests in any service
- No tests for error scenarios in user-service, fund-transfer-service, or utility-payment-service

### GAP-TEST-02: No Integration Tests
**Severity:** Medium | **Effort:** Large

- No `@SpringBootTest` with `@AutoConfigureMockMvc` or `WebTestClient` tests
- No Testcontainers usage for database or infrastructure testing
- No end-to-end test suite
- Context load tests require external infrastructure (Eureka, Config Server, MySQL) which makes them fail in isolation

### GAP-TEST-03: No Contract Tests Between Services
**Severity:** Medium | **Effort:** Large

- No Spring Cloud Contract or Pact tests
- Feign client interfaces have no corresponding provider verification
- API changes in core-banking-service could silently break consumer services

---

## 4. Security

### GAP-SEC-01: Hardcoded Credentials in Source Code
**Severity:** Critical | **Effort:** Small

Plaintext credentials committed to the repository:

| File | Credential |
|------|-----------|
| `docker-compose.yml` | MySQL root password: `woVERANKliGharym` |
| `docker-compose.yml` | Keycloak admin password: `password` |
| `docker-compose.yml` | Keycloak DB password: `password` |
| `docker-compose/mysql/Dockerfile` | `ENV MYSQL_ROOT_PASSWORD woVERANKliGharym` |
| `docker-compose/mysql/privileges.sql` | DB user password: `oPItyPticIAt` |
| `README.md` | Test credentials: `ib_admin@javatodev.com / 5V7huE3G86uB` |

These are visible to anyone with repository access.

### GAP-SEC-02: No Input Validation on API Endpoints
**Severity:** High | **Effort:** Medium

No `@Valid` / `@NotNull` / `@NotBlank` / `@Min` / `@Max` annotations on any request DTOs:

```java
// FundTransferRequest — no validation
@Data
public class FundTransferRequest {
    private String fromAccount;   // Could be null
    private String toAccount;     // Could be null
    private BigDecimal amount;    // Could be null, negative, or zero
    private String authID;
}
```

Similar gaps in `UtilityPaymentRequest`, `User` DTO, and `UserUpdateRequest`.

**Impact:** Null pointer exceptions, negative transfer amounts, or empty account numbers could reach the business logic layer.

### GAP-SEC-03: No Authorization Beyond Gateway
**Severity:** High | **Effort:** Medium

- The API Gateway enforces JWT authentication but **no endpoint-level authorization** exists
- Any authenticated user can access any endpoint (admin operations like user approval, viewing all transfers, etc.)
- Individual services have no security dependencies — they trust the gateway header `X-Auth-Id` unconditionally
- The `AppAuthUserFilter` extracts `X-Auth-Id` from the request header but does not validate it
- Services are directly accessible on their ports (8083–8092), bypassing gateway security entirely

### GAP-SEC-04: Downstream Services Accept Unauthenticated Direct Access
**Severity:** High | **Effort:** Medium

Business services (user, fund-transfer, utility-payment, core-banking) have no Spring Security dependency and no authentication/authorization checks. If exposed directly (not through the gateway), all endpoints are publicly accessible.

### GAP-SEC-05: Raw Exception Details Exposed to Clients
**Severity:** Medium | **Effort:** Small

The catch-all exception handler returns `"Exception occur inside API " + e`, which serializes the full exception including class names, internal messages, and potentially stack trace information. This aids attackers in understanding the application internals.

---

## 5. API Design

### GAP-API-01: Raw ResponseEntity Without Type Parameters
**Severity:** Medium | **Effort:** Small

All controller methods return untyped `ResponseEntity` instead of `ResponseEntity<T>`:

```java
public ResponseEntity sendFundTransfer(@RequestBody FundTransferRequest request) { ... }
// Should be: ResponseEntity<FundTransferResponse>
```

**Impact:** OpenAPI/Swagger cannot infer response schemas, making the generated API documentation incomplete.

### GAP-API-02: No API Versioning Strategy
**Severity:** Low | **Effort:** Small

All endpoints use `/api/v1/` prefix but there is no documented versioning strategy or mechanism for introducing v2 endpoints alongside v1. Current URL-based versioning is acceptable but undocumented.

### GAP-API-03: No Pagination Metadata in Responses
**Severity:** Medium | **Effort:** Small

List endpoints accept Spring `Pageable` parameters but return raw `List<T>` instead of a page wrapper:

```java
public ResponseEntity readFundTransfers(Pageable pageable) {
    return ResponseEntity.ok(fundTransferService.readAllTransfers(pageable));
    // Returns List<FundTransfer>, not Page<FundTransfer>
}
```

Clients have no way to know total pages, total elements, current page, or whether there are more pages.

### GAP-API-04: No Filtering or Sorting Support
**Severity:** Low | **Effort:** Medium

List endpoints accept `Pageable` (which includes basic sorting) but there are no filtering parameters:
- Cannot filter transfers by account, status, or date range
- Cannot filter users by status
- Cannot filter payments by provider or status

### GAP-API-05: Incorrect Swagger Dependency
**Severity:** Medium | **Effort:** Small

Services use `springdoc-openapi-starter-webflux-ui:2.1.0` but the user-service, fund-transfer-service, utility-payment-service, and core-banking-service are **Spring MVC (servlet)** applications, not WebFlux. The correct dependency should be `springdoc-openapi-starter-webmvc-ui`. This may cause runtime issues or incomplete documentation generation.

### GAP-API-06: Inconsistent REST Resource Naming
**Severity:** Low | **Effort:** Small

- User Service: `/api/v1/bank-users/register` (verb in URL — should be `POST /api/v1/bank-users`)
- Core Banking: `/api/v1/account/bank-account/{account_number}` (uses singular, should be `/api/v1/accounts/{account_number}`)
- Utility Payment: `/api/v1/utility-payment` (singular — conventional REST uses plural: `/api/v1/utility-payments`)
- Fund Transfer: `/api/v1/transfer` (inconsistent — some use hyphenated nouns, some use verbs)

---

## 6. Observability

### GAP-OBS-01: No Structured Logging
**Severity:** Medium | **Effort:** Medium

- All services use `@Slf4j` with default Spring Boot logging (Logback with pattern layout)
- No JSON-structured logging configured for log aggregation (ELK, CloudWatch, Datadog)
- No correlation ID / trace ID included in log output by default
- Log levels and patterns are not standardized across services

### GAP-OBS-02: Sensitive Data in Logs
**Severity:** High | **Effort:** Small

Request objects are logged with `toString()` which may include sensitive data:

```java
log.info("Got fund transfer request from API {}", fundTransferRequest.toString());
// Logs: fromAccount, toAccount, amount, authID
```

```java
log.info("Utility payment processing {}", paymentRequest.toString());
// Logs: providerId, amount, referenceNumber, account
```

Account numbers, amounts, and auth IDs should not appear in plain text logs.

### GAP-OBS-03: No Custom Health Checks
**Severity:** Low | **Effort:** Small

Services include `spring-boot-starter-actuator` but rely only on default health indicators. No custom health checks for:
- Database connectivity validation
- Feign client reachability (downstream service health)
- Keycloak connectivity (user-service)

### GAP-OBS-04: No Metrics Customization
**Severity:** Low | **Effort:** Medium

- Actuator is included but no custom metrics are defined
- No business metrics (transfer count, payment volume, error rates)
- Prometheus is listed in the tech stack but no `micrometer-registry-prometheus` dependency exists in any service
- No Grafana dashboards or alerting rules

### GAP-OBS-05: Distributed Tracing Gaps
**Severity:** Low | **Effort:** Small

- Zipkin integration is present via Micrometer Brave bridge
- However, `feign-micrometer` is included even in the API Gateway and Core Banking Service, which do not use Feign, adding unnecessary dependencies
- No custom span annotations on business-critical operations
- No sampling rate configuration visible in local configs (depends on external config server)

---

## 7. Resilience

### GAP-RES-01: No Circuit Breakers
**Severity:** High | **Effort:** Medium

- No Resilience4j or Hystrix dependency in any service
- If core-banking-service goes down, fund-transfer-service and utility-payment-service will fail with unhandled exceptions
- No fallback behavior defined for any Feign client
- Cascading failures will propagate through the entire service chain

### GAP-RES-02: No Retry Policies
**Severity:** Medium | **Effort:** Small

- No Spring Retry or Resilience4j retry configuration
- Transient network failures or momentary service unavailability will immediately fail requests
- No retry on Feign client calls

### GAP-RES-03: No Timeout Configuration
**Severity:** High | **Effort:** Small

- No explicit timeout configuration for Feign clients (connection timeout, read timeout)
- Default timeouts may be too long (or unlimited), causing thread pool exhaustion under load
- No gateway-level timeout for proxied requests
- `wait-for-it.sh` has a 50-second timeout for startup ordering, but runtime calls have no timeout

### GAP-RES-04: No Rate Limiting
**Severity:** Medium | **Effort:** Medium

- No rate limiting at the API Gateway level
- No request throttling on sensitive endpoints (fund transfer, payment processing)
- A single client could overwhelm the system with requests

### GAP-RES-05: No Idempotency Protection
**Severity:** High | **Effort:** Medium

- Fund transfer and utility payment endpoints have no idempotency keys
- Retry of a failed/timed-out request could result in duplicate transactions
- No mechanism to detect or prevent duplicate submissions

### GAP-RES-06: No Graceful Degradation
**Severity:** Medium | **Effort:** Medium

- No fallback responses when downstream services are unavailable
- No read-only mode or cached responses
- No bulkhead pattern to isolate failures between different call paths

---

## Summary Table

| ID | Category | Gap | Severity | Effort |
|----|----------|-----|----------|--------|
| GAP-ORG-01 | Code Organization | No multi-project Gradle build | Medium | Small |
| GAP-ORG-02 | Code Organization | Duplicated code across services | Medium | Medium |
| GAP-ORG-03 | Code Organization | Inconsistent package structure | Low | Small |
| GAP-ORG-04 | Code Organization | Mapper instantiation inconsistency | Low | Small |
| GAP-ERR-01 | Error Handling | All errors return HTTP 400 | High | Medium |
| GAP-ERR-02 | Error Handling | Inconsistent error response formats | Medium | Small |
| GAP-ERR-03 | Error Handling | No Feign client error handling | High | Medium |
| GAP-ERR-04 | Error Handling | No transaction rollback on failure | Critical | Medium |
| GAP-TEST-01 | Testing | Minimal test coverage | High | Large |
| GAP-TEST-02 | Testing | No integration tests | Medium | Large |
| GAP-TEST-03 | Testing | No contract tests | Medium | Large |
| GAP-SEC-01 | Security | Hardcoded credentials in source | Critical | Small |
| GAP-SEC-02 | Security | No input validation | High | Medium |
| GAP-SEC-03 | Security | No authorization beyond gateway | High | Medium |
| GAP-SEC-04 | Security | Direct service access unauthenticated | High | Medium |
| GAP-SEC-05 | Security | Raw exception details exposed | Medium | Small |
| GAP-API-01 | API Design | Raw ResponseEntity without types | Medium | Small |
| GAP-API-02 | API Design | No API versioning strategy | Low | Small |
| GAP-API-03 | API Design | No pagination metadata | Medium | Small |
| GAP-API-04 | API Design | No filtering or sorting support | Low | Medium |
| GAP-API-05 | API Design | Incorrect Swagger dependency | Medium | Small |
| GAP-API-06 | API Design | Inconsistent REST naming | Low | Small |
| GAP-OBS-01 | Observability | No structured logging | Medium | Medium |
| GAP-OBS-02 | Observability | Sensitive data in logs | High | Small |
| GAP-OBS-03 | Observability | No custom health checks | Low | Small |
| GAP-OBS-04 | Observability | No metrics customization | Low | Medium |
| GAP-OBS-05 | Observability | Distributed tracing gaps | Low | Small |
| GAP-RES-01 | Resilience | No circuit breakers | High | Medium |
| GAP-RES-02 | Resilience | No retry policies | Medium | Small |
| GAP-RES-03 | Resilience | No timeout configuration | High | Small |
| GAP-RES-04 | Resilience | No rate limiting | Medium | Medium |
| GAP-RES-05 | Resilience | No idempotency protection | High | Medium |
| GAP-RES-06 | Resilience | No graceful degradation | Medium | Medium |
