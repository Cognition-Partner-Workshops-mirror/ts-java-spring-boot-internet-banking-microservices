# Gap Analysis — Internet Banking Microservices

## Data Integrity

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| Double-subtraction bug in fund transfer | `TransactionService.internalFundTransfer()` lines 90-91: `actualBalance` subtracted, then `availableBalance` set to `actualBalance.subtract(amount)` again — deducting 2× the amount. Same pattern on receiver side (lines 99-100) adds 2× the amount to `availableBalance`. | Both `actualBalance` and `availableBalance` should each be reduced/increased by the amount exactly once. | **Critical** | Small |
| Double-subtraction bug in utility payment | `TransactionService.utilPayment()` lines 63-64: same pattern — `availableBalance` double-subtracted. | Same fix: set `availableBalance` to `actualBalance` after subtraction (not subtract again). | **Critical** | Small |
| Wrong JPA relationship on TransactionEntity | `TransactionEntity.account` uses `@OneToOne(cascade=CascadeType.ALL)` to `BankAccountEntity`. Multiple transactions reference the same account. Cascade ALL means deleting a transaction could delete the bank account. | Should be `@ManyToOne` with no cascade (or `cascade = {}` explicitly). | **Critical** | Small |
| No idempotency keys on fund transfer or utility payment | Retrying a failed request creates a duplicate transaction. No deduplication mechanism. | Accept a client-supplied idempotency key; reject duplicate requests within a TTL window. | **Critical** | Medium |
| No saga/compensation on distributed transactions | If Feign call to core-banking succeeds but local DB save fails, the system is inconsistent. No rollback mechanism. | Implement saga pattern or at minimum a compensation endpoint to reverse core-banking transactions on failure. | **Critical** | Large |

## Error Handling

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| Stack trace leaks in all GlobalExceptionHandlers | `handleException()` returns `"Exception occur inside API " + e` — `e.toString()` includes class name, message, and potentially stack trace. Present in all 4 business services. | Return a generic error message with a correlation ID. Log the full exception server-side only. | **Critical** | Small |
| All exceptions return HTTP 400 | Both `handleGlobalException()` and `handleException()` return `ResponseEntity.badRequest()` regardless of exception type (404, 500, 409, etc.). | Map exception types to appropriate HTTP status codes (404 for EntityNotFound, 409 for UserAlreadyRegistered, 500 for unexpected errors, etc.). | **High** | Small |
| No Feign error handling | No `ErrorDecoder` configured on any Feign client. Feign errors are not translated to meaningful exceptions. | Implement a custom `ErrorDecoder` that maps HTTP status codes from downstream to appropriate local exceptions. | **High** | Medium |

## Input Validation

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| No `@Valid` on any `@RequestBody` | Zero `@Valid` annotations across all controllers in all services. No Bean Validation constraints on any DTO. | Add `@Valid` to all `@RequestBody` parameters. Add `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, etc. to all DTO fields. | **Critical** | Small |

## Security

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| Password field exposed in GET responses | `User.java` DTO in user-service has `private String password` with no `@JsonProperty(access = WRITE_ONLY)`. Returned in GET `/api/v1/bank-users/{id}` and GET `/api/v1/bank-users`. | Add `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to `password` field, or use separate request/response DTOs. | **Critical** | Small |
| Hardcoded secrets in docker-compose.yml | MySQL root password (`woVERANKliGharym`), Keycloak admin password (`password`), PostgreSQL password (`password`) all in plaintext. | Use Docker secrets, `.env` files excluded from VCS, or a vault solution. | **High** | Small |
| No authentication on downstream services | Only the API Gateway enforces OAuth2/JWT. All downstream services (user, fund-transfer, utility-payment, core-banking) accept unauthenticated requests on their ports. | Either propagate JWT tokens and validate in each service, or restrict network access so only the gateway can reach downstream services. | **High** | Medium |
| Sensitive data logged in plaintext | `log.info("Creating user with {}", request.toString())` logs the full User object including password. Similar patterns in fund-transfer and utility-payment controllers. | Mask sensitive fields in `toString()` or use a dedicated log-safe representation. | **High** | Small |

## Testing

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| 5 of 6 services have no real tests | Only `core-banking-service` has unit tests (`TransactionServiceTest`). The other 5 services have only empty Spring context-load stubs. | Each service should have unit tests for service layer, controller layer (MockMvc), and mapper logic. | **High** | Large |
| No integration tests | No tests verify Feign client communication, database migrations, or end-to-end flows. | Add integration tests using Testcontainers (MySQL, Keycloak) and WireMock for Feign clients. | **High** | Large |
| No contract tests | No consumer-driven contract tests between services. | Add Spring Cloud Contract or Pact tests to verify API compatibility between services. | **High** | Large |

## Resilience

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| No Feign timeouts configured | No connect/read timeouts on any Feign client. A hung downstream service blocks the caller indefinitely. | Configure `feign.client.config.default.connectTimeout` and `readTimeout` in application properties. | **Critical** | Small |
| No circuit breakers | No Resilience4j dependency in any service. No circuit breaker on any Feign call. | Add `spring-cloud-starter-circuitbreaker-resilience4j` and annotate Feign clients with `@CircuitBreaker`. | **High** | Medium |
| No retries | No retry configuration on Feign clients or service methods. | Configure Resilience4j retry for transient failures with exponential backoff. | **High** | Medium |

## Code Organization

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| Copy-pasted shared code | `BaseMapper`, `AuditAware`, `ErrorResponse`, `GlobalExceptionHandler` are duplicated across 3-4 services with minor variations. | Extract a shared library module (e.g., `banking-common`) published as a local Maven/Gradle dependency. | **Medium** | Medium |
| No multi-project Gradle build | Each service has its own independent `build.gradle`. No root `settings.gradle` for unified builds. | Create a Gradle multi-project build with shared dependency versions and plugin configuration. | **Medium** | Medium |
| Inconsistent package structure | Some services use `model.dto.request`, others use `model.rest.request`. Mapper instantiation varies (field vs constructor). | Standardize package naming conventions across all services. | **Low** | Small |

## API Design

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| Wrong Swagger dependency | All servlet-based services use `springdoc-openapi-starter-webflux-ui:2.1.0` instead of `springdoc-openapi-starter-webmvc-ui`. | Replace with `springdoc-openapi-starter-webmvc-ui` in all `build.gradle` files. | **Medium** | Small |
| Paginated endpoints return `List<T>` not `Page<T>` | Controllers return `List<T>` after calling `.getContent()` on the `Page`. Clients lose total count, page number, and page size metadata. | Return `Page<T>` or a custom paginated response wrapper. | **Medium** | Small |
| No API versioning strategy | Paths use `/api/v1/` but there is no documented versioning strategy or mechanism for introducing v2. | Document API versioning strategy; consider header-based or path-based versioning with deprecation policy. | **Low** | Small |

## Observability

| Gap | Current State | Expected State | Severity | Effort |
|---|---|---|---|---|
| No structured logging | Services use default Logback text format. No JSON structured logging for log aggregation. | Configure Logback to output JSON (e.g., `logstash-logback-encoder`) for ELK/Loki ingestion. | **Medium** | Small |
| No custom health indicators | Only default Spring Boot Actuator health checks. No checks for MySQL connectivity, Keycloak reachability, or Feign client health. | Add custom `HealthIndicator` beans for critical dependencies. | **Medium** | Medium |
| No custom Micrometer metrics | No business metrics (e.g., transfer count, payment amount, registration rate). | Add custom `MeterRegistry` counters/timers for key business operations. | **Medium** | Medium |
| No rate limiting | No rate limiting on any endpoint. | Add rate limiting at the API Gateway level (e.g., Spring Cloud Gateway `RequestRateLimiter` filter). | **Medium** | Medium |
