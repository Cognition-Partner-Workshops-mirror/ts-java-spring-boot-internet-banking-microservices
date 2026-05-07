# Gap Analysis: Internet Banking Microservices

> Comparison of the current codebase against engineering best practices.
> Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 No Multi-Module Build
| | |
|---|---|
| **Current State** | Each microservice is an independent Gradle project with its own `gradlew`, `build.gradle`, and `settings.gradle`. There is no root-level `settings.gradle` or shared build configuration. |
| **Best Practice** | Use a Gradle multi-module build (or at minimum a shared BOM/version-catalog) to centralize dependency versions, plugin versions, and common configuration. |
| **Impact** | Dependency drift across services; version updates must be applied 6+ times. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Duplicated Code Across Services
| | |
|---|---|
| **Current State** | `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`, `ErrorResponse`, `GlobalExceptionHandler`, `SimpleBankingGlobalException` are copy-pasted across 3-4 services with identical or near-identical implementations. |
| **Best Practice** | Extract shared code into a common library module (e.g., `internet-banking-common`) published to a local Maven repository or included as a Gradle composite build. |
| **Impact** | Bug fixes must be applied in multiple places; inconsistency risk. |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure
| | |
|---|---|
| **Current State** | User service places Feign client under `service.rest.BankingCoreRestClient`, fund-transfer service under `service.rest.client.BankingCoreFeignClient`, and utility-payment under `service.rest.BankingCoreRestClient`. Feign config is in `configuration.feign` (user), `configuration` (fund-transfer, utility-payment). |
| **Best Practice** | Standardize package layout across all services for the same type of class. |
| **Impact** | Developer confusion; harder to navigate. |
| **Severity** | Low |
| **Effort** | Small |

---

## 2. Error Handling

### 2.1 Generic Exception Handler Leaks Stack Traces
| | |
|---|---|
| **Current State** | `GlobalExceptionHandler.handleException(Exception e)` returns `"Exception occur inside API " + e` as the response body. This serializes the full exception including stack trace to the client. Present in all 4 business services. |
| **Best Practice** | Return a structured `ErrorResponse` with a generic message; log the full exception server-side. Never expose internal details to clients. |
| **Impact** | Information disclosure vulnerability; attackers can learn internal class names, package structure, and database details. |
| **Severity** | **Critical** |
| **Effort** | Small |

### 2.2 All Errors Return HTTP 400 Bad Request
| | |
|---|---|
| **Current State** | Both `handleGlobalException` and `handleException` always return `ResponseEntity.badRequest()` (HTTP 400), regardless of the actual error type (e.g., 404 Not Found, 500 Internal Server Error, 409 Conflict). |
| **Best Practice** | Map exceptions to appropriate HTTP status codes: `EntityNotFoundException` -> 404, `InsufficientFundsException` -> 422, unexpected errors -> 500, `UserAlreadyRegisteredException` -> 409. |
| **Impact** | Clients cannot distinguish between error types; poor API contract. |
| **Severity** | High |
| **Effort** | Small |

### 2.3 No Feign Error Handling in Fund Transfer / Utility Payment
| | |
|---|---|
| **Current State** | `FundTransferService.fundTransfer()` and `UtilityPaymentService.utilPayment()` call the core banking Feign client but have no try-catch or error decoder. If the core banking call fails, the local entity remains in `PENDING`/`PROCESSING` state with no recovery mechanism. User service has a `CustomFeignErrorDecoder` but fund-transfer and utility-payment do not. |
| **Best Practice** | Add Feign error decoders consistently; implement compensation/rollback logic or at least mark the local entity as `FAILED`. |
| **Impact** | Orphaned PENDING/PROCESSING records; no visibility into failures. |
| **Severity** | **Critical** |
| **Effort** | Medium |

### 2.4 Missing `@Valid` / Bean Validation on Request Bodies
| | |
|---|---|
| **Current State** | No `@Valid` annotation on any `@RequestBody` parameter. No Jakarta Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.) on any DTO/request class. |
| **Best Practice** | Add `spring-boot-starter-validation`, annotate DTOs with constraints, and use `@Valid` on controller method parameters. |
| **Impact** | Null pointer exceptions, invalid data persisted to database, potential injection vectors. |
| **Severity** | **Critical** |
| **Effort** | Small |

---

## 3. Testing

### 3.1 Only Core Banking Service Has Unit Tests
| | |
|---|---|
| **Current State** | Only `core-banking-service` has meaningful unit tests (3 test classes: `AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest` with ~20 test methods). The other 5 services only have empty Spring Boot context-load tests (`*ApplicationTests.java`). |
| **Best Practice** | Each service should have unit tests for service and controller layers, plus integration tests for critical flows. Aim for >80% coverage on business logic. |
| **Impact** | Regressions go undetected; refactoring is risky. |
| **Severity** | High |
| **Effort** | Large |

### 3.2 No Integration Tests
| | |
|---|---|
| **Current State** | No `@SpringBootTest` integration tests, no Testcontainers usage, no `@WebMvcTest` controller tests, no Feign client contract tests. |
| **Best Practice** | Add integration tests with Testcontainers (MySQL), `@WebMvcTest` for controller layer, and contract tests for Feign clients (Spring Cloud Contract or WireMock). |
| **Impact** | No confidence that services work end-to-end; inter-service contract breakages are invisible until runtime. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Test Coverage Tooling
| | |
|---|---|
| **Current State** | No JaCoCo or similar coverage plugin configured in any `build.gradle`. |
| **Best Practice** | Add JaCoCo plugin with minimum coverage thresholds enforced at build time. |
| **Impact** | No visibility into test coverage. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 4. Security

### 4.1 Hardcoded Credentials in Source Code
| | |
|---|---|
| **Current State** | `docker-compose.yml`: MySQL root password `woVERANKliGharym`, Keycloak admin/password `admin`/`password`, Keycloak DB password `password`. `privileges.sql`: MySQL user password `oPItyPticIAt`. `README.md`: Test credentials `ib_admin@javatodev.com / 5V7huE3G86uB`. |
| **Best Practice** | Use Docker secrets, environment variable files (`.env` excluded from VCS), or a secrets manager. Never commit credentials to source control. |
| **Impact** | Credential exposure; anyone with repo access knows all passwords. |
| **Severity** | **Critical** |
| **Effort** | Small |

### 4.2 CSRF Disabled on API Gateway
| | |
|---|---|
| **Current State** | `SecurityConfiguration`: `httpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)`. |
| **Best Practice** | For a pure REST API consumed by non-browser clients, CSRF disable is acceptable. However, document this decision and ensure no browser-based clients use session cookies. |
| **Impact** | Low for API-only usage; risky if a web frontend is added later. |
| **Severity** | Low |
| **Effort** | Small |

### 4.3 Actuator Endpoints Publicly Exposed
| | |
|---|---|
| **Current State** | API Gateway security config permits all `/actuator/**` paths without authentication for all services. |
| **Best Practice** | Restrict actuator endpoints; expose only `/actuator/health` publicly. Protect `/actuator/env`, `/actuator/configprops`, `/actuator/beans`, etc. behind authentication. |
| **Impact** | Actuator can expose environment variables, configuration properties, and internal service details. |
| **Severity** | High |
| **Effort** | Small |

### 4.4 No Input Validation (Repeated from Error Handling)
| | |
|---|---|
| **Current State** | No `@Valid`, no Bean Validation annotations, no input sanitization anywhere. |
| **Best Practice** | Validate all inputs at the API boundary. |
| **Impact** | SQL injection risk mitigated by JPA parameterized queries, but application-level data integrity issues remain (e.g., negative transfer amounts, empty account numbers). |
| **Severity** | **Critical** |
| **Effort** | Small |

### 4.5 Keycloak Singleton Not Thread-Safe
| | |
|---|---|
| **Current State** | `KeycloakProperties.getInstance()` uses a static singleton without synchronization. Race condition on startup if multiple threads call it simultaneously. |
| **Best Practice** | Use `@Bean` with Spring's singleton scope, or use `synchronized` / double-checked locking / `AtomicReference`. |
| **Impact** | Potential duplicate Keycloak client instances or NPE under high concurrency. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.6 No Rate Limiting
| | |
|---|---|
| **Current State** | No rate limiting on any endpoint, including public registration endpoint. |
| **Best Practice** | Add rate limiting at the API Gateway level (Spring Cloud Gateway `RequestRateLimiter` filter with Redis). |
| **Impact** | Vulnerable to brute-force attacks and DoS. |
| **Severity** | High |
| **Effort** | Medium |

### 4.7 Password Stored Without Hashing Context
| | |
|---|---|
| **Current State** | In `UserService.createUser()`, the raw password from the request is passed directly to `CredentialRepresentation.setValue()`. While Keycloak hashes it server-side, the password travels in plaintext in the request body. |
| **Best Practice** | Ensure TLS is enforced end-to-end. Consider adding password complexity validation before forwarding to Keycloak. |
| **Impact** | Passwords visible in logs if request logging is enabled. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 5. API Design

### 5.1 Raw `ResponseEntity` Without Generics
| | |
|---|---|
| **Current State** | Most controller methods return raw `ResponseEntity` without type parameters (e.g., `public ResponseEntity fundTransfer(...)`). Only the User Service controllers use typed responses like `ResponseEntity<User>`. |
| **Best Practice** | Always use `ResponseEntity<T>` to enable proper OpenAPI schema generation and compile-time type safety. |
| **Impact** | Swagger/OpenAPI docs show `object` instead of actual response schema. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.2 No API Versioning Strategy
| | |
|---|---|
| **Current State** | All endpoints use `/api/v1/` prefix but there is no documented versioning strategy, no v2 support mechanism. |
| **Best Practice** | Document the versioning strategy (URL path, header, or content-type). Consider supporting multiple versions for backward compatibility. |
| **Impact** | Breaking changes will affect all clients simultaneously. |
| **Severity** | Low |
| **Effort** | Small |

### 5.3 No Pagination Metadata in Responses
| | |
|---|---|
| **Current State** | List endpoints accept `Pageable` parameters but return raw `List<T>`, discarding pagination metadata (total elements, total pages, current page). |
| **Best Practice** | Return `Page<T>` or a wrapper DTO with pagination metadata. |
| **Impact** | Clients cannot implement proper pagination UI. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.4 Inconsistent REST Conventions
| | |
|---|---|
| **Current State** | User update uses `PATCH /update/{id}` (verb in URL). Registration uses `POST /register` (verb in URL). Some endpoints use underscores in path variables (`account_number`), others use kebab-case (`bank-users`). |
| **Best Practice** | Use nouns for resources, HTTP methods for actions. Standardize naming convention (kebab-case preferred). E.g., `PATCH /api/v1/bank-users/{id}`, `POST /api/v1/bank-users`. |
| **Impact** | Confusing API surface; inconsistent developer experience. |
| **Severity** | Low |
| **Effort** | Small |

### 5.5 Wrong Swagger Dependency
| | |
|---|---|
| **Current State** | All non-gateway services include `springdoc-openapi-starter-webflux-ui:2.1.0`, but they are Spring MVC (not WebFlux) applications. |
| **Best Practice** | Use `springdoc-openapi-starter-webmvc-ui` for Spring MVC services. |
| **Impact** | May cause runtime conflicts or missing Swagger UI. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 6. Observability

### 6.1 Inconsistent Logging
| | |
|---|---|
| **Current State** | Some controllers log incoming requests (`log.info("Creating user with {}", request.toString())`), others don't. `FundTransferService` has a string concatenation bug: `log.info("Sending fund transfer request {}" + request.toString())` -- the `{}` placeholder is concatenated with the value instead of being substituted by SLF4J. |
| **Best Practice** | Use structured logging consistently across all services. Use SLF4J parameterized logging only (`log.info("msg {}", param)`). Consider adding MDC context (trace ID, user ID). |
| **Impact** | Logging bug causes incorrect log output; inconsistent observability. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.2 No Health Check Configuration
| | |
|---|---|
| **Current State** | `spring-boot-starter-actuator` is included in all services, but no custom health indicators are configured. No Docker Compose `healthcheck` directives. Services use `wait-for-it.sh` (TCP port check) instead of proper health endpoints. |
| **Best Practice** | Configure `management.endpoint.health.show-details`, add custom health indicators for database and Keycloak connectivity. Use Docker Compose `healthcheck` with HTTP probes to `/actuator/health`. |
| **Impact** | `wait-for-it.sh` only checks port availability, not application readiness. Services may accept traffic before fully initialized. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Metrics / Prometheus Integration
| | |
|---|---|
| **Current State** | README lists Prometheus in the tech stack, but no `micrometer-registry-prometheus` dependency is present in any `build.gradle`. No Prometheus configuration or scrape targets. |
| **Best Practice** | Add `micrometer-registry-prometheus`, expose `/actuator/prometheus` endpoint, configure Prometheus scraping. |
| **Impact** | No runtime metrics visibility; cannot monitor request rates, latencies, error rates, JVM health. |
| **Severity** | High |
| **Effort** | Small |

### 6.4 Zipkin Tracing Not Fully Configured
| | |
|---|---|
| **Current State** | Tracing dependencies present (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`) but tracing configuration (sampling rate, Zipkin URL) is delegated to the external config server. No local fallback configuration. |
| **Best Practice** | Include sensible defaults in `application.yml` with profile-specific overrides. |
| **Impact** | If config server is unreachable, tracing may be silently disabled. |
| **Severity** | Low |
| **Effort** | Small |

---

## 7. Resilience

### 7.1 No Circuit Breakers
| | |
|---|---|
| **Current State** | No Resilience4j or Hystrix circuit breakers on any Feign client or inter-service call. |
| **Best Practice** | Add Resilience4j circuit breakers on all Feign clients to prevent cascading failures. Configure failure thresholds, wait durations, and fallback methods. |
| **Impact** | If core-banking-service goes down, all dependent services will hang on Feign calls until timeout, cascading the failure to all users. |
| **Severity** | **Critical** |
| **Effort** | Medium |

### 7.2 No Retry Policies
| | |
|---|---|
| **Current State** | No Spring Retry or Resilience4j retry configuration on any Feign client. |
| **Best Practice** | Add retry with exponential backoff for transient failures (network blips, temporary 503s). |
| **Impact** | Transient failures cause immediate user-facing errors. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeouts Configured
| | |
|---|---|
| **Current State** | No explicit Feign client timeouts, no connection timeouts, no read timeouts configured in any service. |
| **Best Practice** | Configure `feign.client.config.default.connect-timeout` and `read-timeout`. Add gateway-level timeouts. |
| **Impact** | Slow downstream services can block threads indefinitely. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior
| | |
|---|---|
| **Current State** | No fallback methods defined for any Feign client. |
| **Best Practice** | Define `@FeignClient(fallback = ...)` or `fallbackFactory` for graceful degradation. |
| **Impact** | No graceful degradation; any downstream failure results in a raw error to the client. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 Non-Atomic Financial Transactions
| | |
|---|---|
| **Current State** | `TransactionService.internalFundTransfer()` performs two separate `bankAccountRepository.save()` calls. If the process crashes between the debit and credit, funds are lost. The `@Transactional` annotation helps within a single DB, but the fund-transfer-service -> core-banking-service call spans two services with no distributed transaction or saga pattern. |
| **Best Practice** | Implement the Saga pattern with compensating transactions, or use an event-driven approach with eventual consistency. |
| **Impact** | Financial data inconsistency in failure scenarios. |
| **Severity** | **Critical** |
| **Effort** | Large |

### 7.6 Balance Calculation Bug
| | |
|---|---|
| **Current State** | In `TransactionService.internalFundTransfer()`, `availableBalance` is set to `actualBalance.subtract(amount)` AFTER `actualBalance` has already been subtracted. This double-subtracts the amount from `availableBalance`. Same bug exists in `utilPayment()`. |
| **Best Practice** | Set `availableBalance = actualBalance` after the actualBalance update, or compute both independently. |
| **Impact** | Available balance is incorrectly lower than actual balance after every transaction. |
| **Severity** | **Critical** |
| **Effort** | Small |

---

## 8. Docker & Deployment

### 8.1 No CI/CD Pipeline
| | |
|---|---|
| **Current State** | No GitHub Actions, Jenkins, GitLab CI, or any automated build/test/deploy pipeline. |
| **Best Practice** | Implement CI pipeline (build, test, lint, Docker image build) triggered on PRs; CD pipeline for staging/production deployment. |
| **Impact** | All builds and deployments are manual; no automated quality gates. |
| **Severity** | High |
| **Effort** | Large |

### 8.2 Fat JAR Copied Directly into Docker Image
| | |
|---|---|
| **Current State** | Dockerfiles use `ADD build/libs/*.jar app.jar`. No multi-stage build; relies on pre-built JAR on the host. |
| **Best Practice** | Use multi-stage Docker builds that compile inside the container for reproducibility. Use Spring Boot layered JARs for better caching. |
| **Impact** | Not reproducible; Docker build depends on local Gradle build state. |
| **Severity** | Medium |
| **Effort** | Medium |

### 8.3 Static IP Assignment in Docker Compose
| | |
|---|---|
| **Current State** | All containers have hardcoded static IPs in the `172.25.0.0/16` subnet. |
| **Best Practice** | Use Docker DNS (container names) instead of static IPs. Services already use service names for Eureka and config server. |
| **Impact** | IP conflicts; maintenance burden; doesn't scale. |
| **Severity** | Low |
| **Effort** | Small |

### 8.4 Deprecated Docker Compose Version
| | |
|---|---|
| **Current State** | Both compose files use `version: '3.6'`, which is deprecated in modern Docker Compose. |
| **Best Practice** | Remove the `version` key entirely (Docker Compose V2 infers it). |
| **Impact** | Warning messages; eventual incompatibility. |
| **Severity** | Low |
| **Effort** | Small |

### 8.5 No Resource Limits in Docker Compose
| | |
|---|---|
| **Current State** | No `deploy.resources.limits` or `mem_limit` configured for any container. |
| **Best Practice** | Set memory and CPU limits to prevent any single service from consuming all host resources. |
| **Impact** | A misbehaving service can starve other services. |
| **Severity** | Medium |
| **Effort** | Small |

---

## Summary Table

| # | Gap | Severity | Effort | Category |
|---|---|---|---|---|
| 2.1 | Exception handler leaks stack traces | Critical | Small | Error Handling |
| 2.3 | No Feign error handling in fund-transfer/utility-payment | Critical | Medium | Error Handling |
| 2.4 | No input validation (`@Valid` / Bean Validation) | Critical | Small | Security / Error Handling |
| 4.1 | Hardcoded credentials in source code | Critical | Small | Security |
| 7.1 | No circuit breakers | Critical | Medium | Resilience |
| 7.5 | Non-atomic financial transactions (no Saga) | Critical | Large | Resilience |
| 7.6 | Balance calculation bug (double-subtraction) | Critical | Small | Resilience / Correctness |
| 1.2 | Duplicated code across services | High | Medium | Code Organization |
| 2.2 | All errors return HTTP 400 | High | Small | Error Handling |
| 3.1 | Only core-banking has unit tests | High | Large | Testing |
| 3.2 | No integration tests | High | Large | Testing |
| 4.3 | Actuator endpoints publicly exposed | High | Small | Security |
| 4.6 | No rate limiting | High | Medium | Security |
| 6.3 | No Prometheus metrics | High | Small | Observability |
| 7.2 | No retry policies | High | Small | Resilience |
| 7.3 | No timeouts configured | High | Small | Resilience |
| 8.1 | No CI/CD pipeline | High | Large | Deployment |
| 1.1 | No multi-module build | Medium | Medium | Code Organization |
| 3.3 | No test coverage tooling | Medium | Small | Testing |
| 4.5 | Keycloak singleton not thread-safe | Medium | Small | Security |
| 4.7 | Password in plaintext in request body | Medium | Small | Security |
| 5.1 | Raw ResponseEntity without generics | Medium | Small | API Design |
| 5.3 | No pagination metadata | Medium | Small | API Design |
| 5.5 | Wrong Swagger dependency (webflux vs webmvc) | Medium | Small | API Design |
| 6.1 | Inconsistent logging + SLF4J bug | Medium | Small | Observability |
| 6.2 | No health check configuration | Medium | Small | Observability |
| 8.2 | No multi-stage Docker builds | Medium | Medium | Deployment |
| 8.5 | No Docker resource limits | Medium | Small | Deployment |
| 1.3 | Inconsistent package structure | Low | Small | Code Organization |
| 4.2 | CSRF disabled (acceptable for REST API) | Low | Small | Security |
| 5.2 | No API versioning strategy documented | Low | Small | API Design |
| 5.4 | Inconsistent REST conventions | Low | Small | API Design |
| 6.4 | Zipkin tracing not fully configured | Low | Small | Observability |
| 8.3 | Static IP assignment in Docker Compose | Low | Small | Deployment |
| 8.4 | Deprecated Docker Compose version | Low | Small | Deployment |
