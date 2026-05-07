# Gap Analysis — Internet Banking Microservices

## 1. Code Organization

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Code Organization | **Duplicated `BaseMapper` class across 4 services** — identical abstract class copy-pasted into each service instead of a shared library | Medium | M | `core-banking-service/src/main/java/com/javatodev/finance/model/mapper/BaseMapper.java`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/model/mapper/BaseMapper.java`, `internet-banking-user-service/src/main/java/com/javatodev/finance/model/mapper/BaseMapper.java`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/model/mapper/BaseMapper.java` |
| Code Organization | **Duplicated `AuditAware` MappedSuperclass across 3 services** — same JPA audit base entity in fund-transfer, user, and utility-payment services | Medium | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/model/dto/AuditAware.java`, `internet-banking-user-service/src/main/java/com/javatodev/finance/model/dto/AuditAware.java`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/model/dto/AuditAware.java` |
| Code Organization | **Duplicated `SimpleBankingGlobalException` and `ErrorResponse` across 4 services** — same exception hierarchy copied into each service | Medium | M | `core-banking-service/src/main/java/com/javatodev/finance/exception/SimpleBankingGlobalException.java`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/exception/SimpleBankingGlobalException.java`, etc. |
| Code Organization | **Duplicated `ApiRequestContext` / `ApiRequestContextHolder` / `AppAuthUserFilter` across 3 services** — identical filter infrastructure copy-pasted | Medium | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/configuration/filter/`, `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/filter/`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/configuration/filter/` |
| Code Organization | **No shared Gradle multi-project build** — each service is a standalone Gradle project with its own `gradlew`, `gradle/wrapper`, etc., leading to version drift risk | Low | L | Each service root contains independent `build.gradle`, `settings.gradle`, `gradlew` |
| Code Organization | **Mappers instantiated with `new` instead of Spring-managed beans** — prevents injection and testability | Low | S | `core-banking-service/src/main/java/com/javatodev/finance/service/AccountService.java:21-22`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java:28` |
| Code Organization | **Shared database for all services** — all four JPA services connect to the same MySQL instance, violating the database-per-service microservice pattern | High | L | `docker-compose/docker-compose.yml` — single `mysql_core_db` container; all services wait on it |

## 2. Error Handling

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Error Handling | **All exceptions return HTTP 400** — `GlobalExceptionHandler` maps both business exceptions and generic `Exception.class` to `ResponseEntity.badRequest()`, never returning 404, 409, 500, etc. | High | S | `core-banking-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java:14-28`, same pattern in fund-transfer, user, utility-payment services |
| Error Handling | **`EntityNotFoundException` returns 400 instead of 404** — entity-not-found is a subclass of `SimpleBankingGlobalException` which is caught by the handler that returns 400 | High | S | `core-banking-service/src/main/java/com/javatodev/finance/exception/EntityNotFoundException.java:3-8`, `GlobalExceptionHandler.java:13-20` |
| Error Handling | **Generic exception handler leaks stack traces** — `"Exception occur inside API " + e` serializes the full exception toString into the response body | Critical | S | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/exception/GlobalExceptionHandler.java:21-24`, same in all services |
| Error Handling | **Inconsistent error response format** — business exceptions return `ErrorResponse {code, message}` but generic exceptions return a raw String | Medium | S | All `GlobalExceptionHandler` classes — two different `@ExceptionHandler` methods return different shapes |
| Error Handling | **Raw `ResponseEntity` without type parameters** — all controller methods and exception handlers use raw `ResponseEntity` instead of `ResponseEntity<T>`, losing compile-time type safety | Low | S | `core-banking-service/src/main/java/com/javatodev/finance/controller/AccountController.java:27`, `TransactionController.java:29`, etc. |
| Error Handling | **No error handling for failed Feign calls in fund-transfer and utility-payment services** — if the Core Banking call fails, the local entity remains in PENDING/PROCESSING with no retry or compensation | High | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java:30-44`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/UtilityPaymentService.java:31-47` |

## 3. Testing

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Testing | **Only Core Banking Service has meaningful unit tests** — 3 test classes (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`) with good coverage of service logic | Medium | L | `core-banking-service/src/test/java/com/javatodev/finance/service/` |
| Testing | **All other services have only empty `contextLoads()` tests** — fund-transfer, user, utility-payment, api-gateway, config-server, service-registry each have a single placeholder test | High | L | `internet-banking-fund-transfer-service/src/test/java/.../InternetBankingFundTransferServiceApplicationTests.java`, same pattern for other services |
| Testing | **No integration tests** — no `@SpringBootTest` tests that exercise the full request flow, no Testcontainers, no WireMock for Feign clients | High | L | No integration test files found in any service |
| Testing | **No contract tests** — no consumer-driven contract tests (e.g., Spring Cloud Contract, Pact) for the Feign client interfaces | Medium | L | No contract test configuration or files in any service |
| Testing | **No controller/API layer tests** — no `@WebMvcTest` or MockMvc-based tests for any `@RestController` | Medium | M | No controller test files found |

## 4. Security

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Security | **No `@Valid` / Bean Validation on any request body** — all `@RequestBody` parameters lack validation annotations; no `@NotNull`, `@NotBlank`, `@Positive`, etc. on DTO fields | Critical | S | `core-banking-service/src/main/java/com/javatodev/finance/controller/TransactionController.java:29,38`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/controller/FundTransferController.java:31`, `internet-banking-user-service/src/main/java/com/javatodev/finance/controller/UserController.java:31`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/controller/UtilityPaymentController.java:35` |
| Security | **MySQL root password in plain text in docker-compose** | Medium | S | `docker-compose/docker-compose.yml:43` — `MYSQL_ROOT_PASSWORD: woVERANKliGharym` |
| Security | **Keycloak admin credentials in plain text in docker-compose** | Medium | S | `docker-compose/docker-compose.yml:21-22` — `KEYCLOAK_ADMIN: admin`, `KEYCLOAK_ADMIN_PASSWORD: password` |
| Security | **Keycloak client-secret sourced from externalized config (Git repo) without encryption** — `KeycloakProperties` reads `app.config.keycloak.client-secret` from Config Server which stores configs in a public Git repo | High | M | `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java:23-24`, config server Git URI in `internet-banking-config-server/src/main/resources/application.yml:8` |
| Security | **Internal services (Core Banking) have no auth** — Core Banking endpoints are directly accessible without any authentication; only the Gateway enforces OAuth2 | High | M | `core-banking-service/src/main/java/com/javatodev/finance/CoreBankingServiceApplication.java` — no Spring Security dependency in `core-banking-service/build.gradle` |
| Security | **Keycloak singleton instance is not thread-safe** — `KeycloakProperties.getInstance()` uses a non-synchronized check-then-act pattern | Medium | S | `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/keycloak/KeycloakProperties.java:30-41` |
| Security | **Password transmitted in plain text in registration request body** — no note about HTTPS enforcement or password hashing before Keycloak delivery | Low | S | `internet-banking-user-service/src/main/java/com/javatodev/finance/service/UserService.java:55-56` |

## 5. API Design

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| API Design | **No API versioning strategy beyond `/v1/` in URL** — no header-based or content-negotiation versioning; no plan for v2 | Low | S | All controllers use `/api/v1/` prefix |
| API Design | **Pagination returns raw `List` instead of `Page` metadata** — `readUsers()`, `readFundTransfers()`, `readPayments()` all accept `Pageable` but return `List<T>`, discarding total count, page number, etc. | High | S | `core-banking-service/src/main/java/com/javatodev/finance/service/UserService.java:30-32`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java:48-50`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/UtilityPaymentService.java:48-51` |
| API Design | **Non-RESTful endpoint naming** — `POST /register`, `PATCH /update/{id}` instead of `POST /bank-users`, `PATCH /bank-users/{id}` | Medium | S | `internet-banking-user-service/src/main/java/com/javatodev/finance/controller/UserController.java:31,36` |
| API Design | **No HATEOAS links** — responses are plain DTOs with no hypermedia links | Low | M | All response DTOs are plain POJOs |
| API Design | **Swagger/OpenAPI partially configured** — `springdoc-openapi-starter-webflux-ui` dependency is present in fund-transfer, user, and utility-payment services with `@Tag` and `@Operation` annotations, but Core Banking uses it too while the gateway and registry do not | Low | S | `build.gradle` files across services |
| API Design | **`FundTransferResponse` in fund-transfer service uses `@Data` (mutable) and lacks `@Builder`** — inconsistent with same-named class in core-banking which uses `@Builder` | Low | S | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/model/dto/response/FundTransferResponse.java:6-8` |
| API Design | **POST endpoints return 200 instead of 201** — `createUser()`, `fundTransfer()`, `processPayment()` all return `ResponseEntity.ok()` for resource creation | Medium | S | `internet-banking-user-service/src/main/java/com/javatodev/finance/controller/UserController.java:33`, `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/controller/FundTransferController.java:32`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/controller/UtilityPaymentController.java:36` |

## 6. Observability

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Observability | **Core Banking Service lacks tracing dependencies** — no `micrometer-tracing-bridge-brave` or `zipkin-reporter-brave` in its `build.gradle`, creating a gap in distributed traces | High | S | `core-banking-service/build.gradle` — only has `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator` |
| Observability | **No structured logging** — all services use `log.info()` with ad-hoc string formatting; no JSON log format, no MDC correlation IDs | Medium | M | All controller and service classes, e.g., `FundTransferController.java:32` — `log.info("Got fund transfer request from API {}", ...)` |
| Observability | **Logging potentially exposes sensitive data** — `request.toString()` on DTOs containing account numbers and amounts is logged at INFO level | Medium | S | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/controller/FundTransferController.java:32`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/UtilityPaymentService.java:33` |
| Observability | **No custom health indicators** — Actuator dependency is present but no custom health checks for MySQL connectivity, Keycloak availability, or Feign client targets | Medium | M | No `HealthIndicator` implementations found in any service |
| Observability | **No Prometheus metrics endpoint** — Actuator is included but no `micrometer-registry-prometheus` dependency for metrics scraping | Medium | S | All `build.gradle` files |
| Observability | **String concatenation in log error statements** — `log.error("..." + e)` instead of parameterized logging with `{}` | Low | S | `internet-banking-user-service/src/main/java/com/javatodev/finance/configuration/feign/CustomFeignErrorDecoder.java:32,39` |

## 7. Resilience

| Category | Gap | Severity | Effort | Evidence |
|----------|-----|----------|--------|----------|
| Resilience | **No circuit breakers on Feign clients** — no Resilience4j or Hystrix configuration; a Core Banking outage cascades to all dependent services | Critical | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/rest/client/BankingCoreFeignClient.java`, `internet-banking-user-service/src/main/java/com/javatodev/finance/service/rest/BankingCoreRestClient.java`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/rest/BankingCoreRestClient.java` — no `@CircuitBreaker`, no fallback |
| Resilience | **No retry policies on Feign clients** — no Spring Retry or Resilience4j retry configuration | High | S | No `@Retryable` annotations, no `Retryer` bean, no Resilience4j dependency in any `build.gradle` |
| Resilience | **No Feign client timeouts configured** — default infinite/very long timeouts; a slow Core Banking response blocks the calling service indefinitely | High | S | No `feign.client.config` timeout properties in any `application.yml` or `bootstrap.yml`; `CustomFeignClientConfiguration` only sets log level |
| Resilience | **No fallback behavior** — when Core Banking is unavailable, fund-transfer and utility-payment services throw unhandled exceptions, leaving local entities in PENDING/PROCESSING permanently | High | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/service/FundTransferService.java:30-44`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/service/UtilityPaymentService.java:31-47` |
| Resilience | **No idempotency keys on fund transfer / payment endpoints** — duplicate POST requests can create duplicate transactions | High | M | `internet-banking-fund-transfer-service/src/main/java/com/javatodev/finance/controller/FundTransferController.java:30-33`, `internet-banking-utility-payment-service/src/main/java/com/javatodev/finance/controller/UtilityPaymentController.java:34-37` |
| Resilience | **No rate limiting on the API Gateway** — no Spring Cloud Gateway rate-limiter filter configured | Medium | S | `internet-banking-api-gateway/src/main/java/com/javatodev/finance/configuration/security/SecurityConfiguration.java` — only auth rules, no rate limiting |
| Resilience | **No bulkhead isolation** — all Feign calls share the same thread pool; one slow downstream service can starve threads for others | Medium | M | No bulkhead configuration in any service |
| Resilience | **Potential double-deduction bug in `utilPayment()`** — `availableBalance` is set to `actualBalance - amount` after `actualBalance` was already decremented, effectively deducting twice | Critical | S | `core-banking-service/src/main/java/com/javatodev/finance/service/TransactionService.java:63-64` |

---

## Summary

| Category | Critical | High | Medium | Low | Total |
|----------|----------|------|--------|-----|-------|
| Code Organization | 0 | 1 | 4 | 2 | 7 |
| Error Handling | 1 | 3 | 1 | 1 | 6 |
| Testing | 0 | 2 | 2 | 0 | 4 |
| Security | 1 | 2 | 3 | 1 | 7 |
| API Design | 0 | 1 | 2 | 4 | 7 |
| Observability | 0 | 1 | 3 | 1 | 5 |
| Resilience | 2 | 3 | 2 | 0 | 7 |
| **Total** | **4** | **13** | **17** | **9** | **43** |
