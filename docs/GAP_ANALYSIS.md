# Engineering Standards Gap Analysis

This document compares the codebase against industry best practices for Spring Boot microservices and documents gaps with severity ratings and estimated remediation effort.

**Severity scale:** Critical > High > Medium > Low
**Effort scale:** Small (< 1 day) | Medium (1–3 days) | Large (> 3 days)

---

## 1. Project Structure & Modularity

### 1.1 No Multi-Module Build
| | |
|---|---|
| **Gap** | Each microservice is a standalone Gradle project with its own `build.gradle`, `gradlew`, and wrapper. There is no root `settings.gradle` or multi-module build, making it impossible to enforce consistent dependency versions, shared plugins, or run a single `./gradlew build` from the root. |
| **Severity** | Medium |
| **Effort** | Medium |

### 1.2 Massive Code Duplication Across Services
| | |
|---|---|
| **Gap** | The following classes are copy-pasted identically across 3–4 services with no shared library: `BaseMapper`, `AuditAware`, `AuditConfig`, `AuditorAwareConfig`, `ApiRequestContext`, `ApiRequestContextHolder`, `AppAuthUserFilter`, `SimpleBankingGlobalException`, `ErrorResponse`, `GlobalExceptionHandler`, `TransactionStatus`. This violates DRY and makes cross-cutting changes error-prone. |
| **Severity** | High |
| **Effort** | Medium |

### 1.3 Inconsistent Package Structure
| | |
|---|---|
| **Gap** | Package layout varies across services. Core Banking uses `repository/` at the top level; Fund Transfer and User Service nest it under `model/repository/`; Utility Payment uses a top-level `repository/`. Configuration classes are in `configuration/` in some services and `configuration/feign/`, `configuration/filter/`, `configuration/audit/` in others. Feign client packages differ (`service/rest/`, `service/rest/client/`). |
| **Severity** | Low |
| **Effort** | Small |

### 1.4 DTOs Located in `model.dto` Alongside JPA Entities
| | |
|---|---|
| **Gap** | `AuditAware` is a JPA `@MappedSuperclass` but lives in the `model.dto` package. DTOs and entities are co-mingled, making the separation of concerns unclear. |
| **Severity** | Low |
| **Effort** | Small |

### 1.5 Manual Mapper Classes Instead of MapStruct/ModelMapper
| | |
|---|---|
| **Gap** | All services use hand-written mapper classes based on `BeanUtils.copyProperties()` with `Object... args` varargs. This is fragile (no compile-time checking of property names) and produces boilerplate. MapStruct or ModelMapper would be type-safe and auto-generated. |
| **Severity** | Low |
| **Effort** | Medium |

---

## 2. Error Handling & Exception Management

### 2.1 Inconsistent Error Response Formats
| | |
|---|---|
| **Gap** | Core Banking, Fund Transfer, and Utility Payment services return `ErrorResponse { code, message }` for `SimpleBankingGlobalException` but return a raw string `"Exception occur inside API " + e` for all other exceptions. User Service has a richer exception hierarchy (`EntityNotFoundException`, `InvalidEmailException`, etc.) but the catch-all handler is still a raw string. The error format is not uniform across all services. |
| **Severity** | High |
| **Effort** | Small |

### 2.2 Generic Exception Catch-All Returns 400 Bad Request
| | |
|---|---|
| **Gap** | All `GlobalExceptionHandler` classes catch `Exception.class` and return `400 Bad Request` for every unhandled error — including `NullPointerException`, `IllegalStateException`, or any server error. This masks 500-level bugs as client errors, making debugging extremely difficult. |
| **Severity** | Critical |
| **Effort** | Small |

### 2.3 Exception Message Leaks Internal Details
| | |
|---|---|
| **Gap** | The catch-all handler returns `"Exception occur inside API " + e`, which includes the full exception `toString()` — stack trace fragments, class names, and potentially sensitive internal state. This is a security and usability concern. |
| **Severity** | High |
| **Effort** | Small |

### 2.4 No Error Codes in Fund Transfer / Utility Payment Services
| | |
|---|---|
| **Gap** | Core Banking and User Service define `GlobalErrorCode` constants. Fund Transfer and Utility Payment services have no error code constants — their `SimpleBankingGlobalException` is thrown without structured codes. |
| **Severity** | Medium |
| **Effort** | Small |

### 2.5 No Feign Error Decoder in Fund Transfer / Utility Payment
| | |
|---|---|
| **Gap** | User Service has a `CustomFeignErrorDecoder` that deserializes upstream error responses and re-throws them as typed exceptions. Fund Transfer and Utility Payment services use a `CustomFeignClientConfiguration` that only forwards the `X-Auth-Id` header but has **no error decoder** — Feign exceptions bubble up as generic `FeignException`, losing the original error code and message. |
| **Severity** | High |
| **Effort** | Small |

### 2.6 Fund Transfer: No FAILED Status on Error
| | |
|---|---|
| **Gap** | `FundTransferService.fundTransfer()` saves a `PENDING` record, then calls core banking. If the Feign call throws, the record stays `PENDING` forever — it is never updated to `FAILED`. Same issue exists in `UtilityPaymentService`. |
| **Severity** | Critical |
| **Effort** | Small |

---

## 3. Test Coverage & Quality

### 3.1 Minimal Test Coverage
| | |
|---|---|
| **Gap** | Only the **core-banking-service** has meaningful unit tests (`AccountServiceTest`, `TransactionServiceTest`, `UserServiceTest`). All other services have only a single `contextLoads()` test that requires the full Spring context (and will fail without config server / Eureka). The fund transfer, utility payment, user service, API gateway, config server, and service registry have **zero business logic tests**. |
| **Severity** | Critical |
| **Effort** | Large |

### 3.2 No Integration Tests
| | |
|---|---|
| **Gap** | There are no integration tests that test REST endpoints (e.g., `@WebMvcTest`, `@SpringBootTest` with `TestRestTemplate`/`MockMvc`). No tests verify Feign client behavior, serialization/deserialization, or database operations with a real schema. |
| **Severity** | High |
| **Effort** | Large |

### 3.3 No Contract Tests
| | |
|---|---|
| **Gap** | No Spring Cloud Contract, Pact, or similar consumer-driven contract testing exists. The Feign client interfaces could drift from the actual provider endpoints without detection. |
| **Severity** | Medium |
| **Effort** | Large |

### 3.4 Context Load Tests Will Fail Without Infrastructure
| | |
|---|---|
| **Gap** | The `@SpringBootTest` `contextLoads()` tests in API Gateway, Config Server, and Service Registry attempt to start the full application context. API Gateway requires a JWT JWKS URI, Config Server needs the remote Git repo, and others need Eureka. These tests will fail in CI without mocks or test containers. |
| **Severity** | Medium |
| **Effort** | Medium |

### 3.5 No Test Coverage Reporting
| | |
|---|---|
| **Gap** | No JaCoCo or other coverage plugin is configured. There is no visibility into what percentage of code is covered. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 4. Security & Access Control

### 4.1 No Input Validation
| | |
|---|---|
| **Gap** | **None** of the request DTOs use Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Min`, `@Size`, etc.). `FundTransferRequest`, `UtilityPaymentRequest`, and user registration accept any payload — null amounts, empty account numbers, negative values. `spring-boot-starter-validation` is not even a dependency. |
| **Severity** | Critical |
| **Effort** | Small |

### 4.2 Hardcoded Credentials in Source Code
| | |
|---|---|
| **Gap** | Multiple secrets are hardcoded and committed: MySQL root password (`woVERANKliGharym`) in `docker-compose.yml`, MySQL app user password (`oPItyPticIAt`) in `privileges.sql`, Keycloak admin password (`password`) in compose files, Keycloak client secret in test `application.yml`, and test credentials in `README.md`. |
| **Severity** | High |
| **Effort** | Small |

### 4.3 Downstream Services Have No Authentication
| | |
|---|---|
| **Gap** | Only the API Gateway validates JWTs. All downstream services (Core Banking, User, Fund Transfer, Utility Payment) have **no Spring Security** dependency and accept any HTTP request directly on their ports. If any service port is exposed, the entire API is unprotected. The `X-Auth-Id` header can be trivially spoofed by any caller. |
| **Severity** | Critical |
| **Effort** | Large |

### 4.4 CSRF Disabled Without Documentation
| | |
|---|---|
| **Gap** | The API Gateway's `SecurityConfiguration` disables CSRF (`http.csrf(CsrfSpec::disable)`) without commentary. While acceptable for stateless JWT APIs, this should be explicitly documented and reviewed. |
| **Severity** | Low |
| **Effort** | Small |

### 4.5 No Dependency Vulnerability Scanning
| | |
|---|---|
| **Gap** | No OWASP Dependency Check, Snyk, or similar tool is configured. Dependencies like `commons-io` (transitive via Feign error decoder) and others are not scanned for known CVEs. |
| **Severity** | Medium |
| **Effort** | Small |

### 4.6 User Registration Accepts Plaintext Password
| | |
|---|---|
| **Gap** | The user registration flow accepts a plaintext password in the JSON body and passes it directly to Keycloak's `CredentialRepresentation`. While Keycloak hashes it internally, the password transits multiple services in cleartext over the internal network. |
| **Severity** | Medium |
| **Effort** | Medium |

---

## 5. API Design & Documentation

### 5.1 Raw `ResponseEntity` Without Type Parameters
| | |
|---|---|
| **Gap** | Every controller method returns `ResponseEntity` (raw type) instead of `ResponseEntity<SpecificType>`. This means: (1) no compile-time type safety, (2) OpenAPI/Swagger cannot infer response schemas, and (3) generic warnings are suppressed throughout. |
| **Severity** | High |
| **Effort** | Small |

### 5.2 No API Versioning Strategy
| | |
|---|---|
| **Gap** | While paths include `/api/v1/`, there is no mechanism for versioning (no content negotiation, no version headers). If a breaking change is needed, there is no defined strategy. |
| **Severity** | Low |
| **Effort** | Medium |

### 5.3 No Pagination Metadata in Responses
| | |
|---|---|
| **Gap** | All paginated endpoints (`GET /api/v1/user`, `GET /api/v1/transfer`, etc.) accept `Pageable` but return `List<T>` — discarding `totalElements`, `totalPages`, `hasNext`, etc. Clients have no way to know the total dataset size or navigate pages. |
| **Severity** | High |
| **Effort** | Small |

### 5.4 Incorrect OpenAPI/Swagger Dependency
| | |
|---|---|
| **Gap** | Core Banking, Fund Transfer, User Service, and Utility Payment include `springdoc-openapi-starter-webflux-ui:2.1.0` — the **WebFlux** variant. However, these services use Spring MVC (`spring-boot-starter-web`). The correct dependency is `springdoc-openapi-starter-webmvc-ui`. This likely causes Swagger UI to not function correctly. |
| **Severity** | Medium |
| **Effort** | Small |

### 5.5 No Standard Error Schema Documented
| | |
|---|---|
| **Gap** | There is no `@ApiResponse` annotation documenting error responses. Swagger documentation only shows the happy path. |
| **Severity** | Low |
| **Effort** | Small |

### 5.6 Missing HTTP Status Codes
| | |
|---|---|
| **Gap** | `POST` endpoints return `200 OK` instead of `201 Created` for resource creation (user registration, fund transfer initiation). No `404` is returned for not-found entities — they return `400 Bad Request` due to the catch-all exception handler. |
| **Severity** | Medium |
| **Effort** | Small |

---

## 6. Observability & Monitoring

### 6.1 Inconsistent Logging
| | |
|---|---|
| **Gap** | Some controllers use `@Slf4j` and log request details; others do not. Logging is ad-hoc — `log.info("Got fund transfer request from API {}", request.toString())` logs the full request object (potentially sensitive financial data). There is no structured logging (JSON format), no correlation ID logging, and no log level configuration. |
| **Severity** | Medium |
| **Effort** | Medium |

### 6.2 No Health Check Customization
| | |
|---|---|
| **Gap** | All services include `spring-boot-starter-actuator` but rely entirely on defaults. There are no custom health indicators (e.g., checking database connectivity, Keycloak availability, or downstream service reachability). The actuator endpoints exposed are not explicitly configured. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.3 No Metrics Endpoints / Prometheus Integration
| | |
|---|---|
| **Gap** | The README mentions Prometheus, but no `micrometer-registry-prometheus` dependency exists in any service. The `/actuator/prometheus` endpoint is not available. No custom metrics (e.g., transfer counts, payment amounts, error rates) are defined. |
| **Severity** | Medium |
| **Effort** | Small |

### 6.4 Tracing Covers Infrastructure But Not Business Spans
| | |
|---|---|
| **Gap** | Micrometer tracing with Brave is configured for HTTP and Feign calls, but there are no custom spans for business operations (e.g., "validate-balance", "create-keycloak-user"). This limits the usefulness of traces for debugging business logic. |
| **Severity** | Low |
| **Effort** | Medium |

### 6.5 Sensitive Data in Logs
| | |
|---|---|
| **Gap** | Controllers and services log request objects via `toString()`, which includes account numbers, amounts, and potentially user credentials. For example: `log.info("Got fund transfer request from API {}", fundTransferRequest.toString())` exposes `fromAccount`, `toAccount`, `amount`, and `authID`. |
| **Severity** | High |
| **Effort** | Small |

---

## 7. Resilience & Fault Tolerance

### 7.1 No Circuit Breakers
| | |
|---|---|
| **Gap** | All inter-service communication via Feign has **no circuit breaker** (Resilience4j, Hystrix, or Spring Cloud Circuit Breaker). If Core Banking Service goes down, all dependent services will block and eventually exhaust their thread pools (cascading failure). |
| **Severity** | Critical |
| **Effort** | Medium |

### 7.2 No Retry Policies
| | |
|---|---|
| **Gap** | Feign clients have no retry configuration. A transient network blip causes an immediate hard failure with no recovery attempt. Spring Retry or Resilience4j Retry are not configured. |
| **Severity** | High |
| **Effort** | Small |

### 7.3 No Timeout Configuration
| | |
|---|---|
| **Gap** | Feign clients use default timeouts (which are typically very long or infinite depending on the HTTP client). No `connectTimeout` or `readTimeout` is configured, meaning a slow downstream service can hang callers indefinitely. |
| **Severity** | High |
| **Effort** | Small |

### 7.4 No Fallback Behavior
| | |
|---|---|
| **Gap** | When a Feign call fails, the exception propagates directly to the caller. There are no fallback methods (e.g., returning cached data, a degraded response, or a meaningful error). |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.5 No Rate Limiting
| | |
|---|---|
| **Gap** | The API Gateway has no rate limiting configuration. A single client could overwhelm the system with requests. Spring Cloud Gateway supports `RequestRateLimiter` filter but it is not configured. |
| **Severity** | Medium |
| **Effort** | Medium |

### 7.6 No Transaction Compensation / Saga Pattern
| | |
|---|---|
| **Gap** | Fund Transfer and Utility Payment follow a "save PENDING, call remote, update SUCCESS" pattern — but if the process crashes between the remote call succeeding and the local update, the local record remains PENDING while money has already moved. There is no saga, outbox pattern, or compensation mechanism. The `@Transactional` annotation on Core Banking's `TransactionService` only covers the local database — not the distributed transaction across services. |
| **Severity** | Critical |
| **Effort** | Large |

### 7.7 Balance Calculation Bug in Core Banking
| | |
|---|---|
| **Gap** | In `TransactionService.utilPayment()`, the available balance is incorrectly computed: `fromAccount.setAvailableBalance(fromAccount.getActualBalance().subtract(amount))` is called **after** `actualBalance` was already decremented, causing a double-subtraction. Similarly in `internalFundTransfer()`, `setAvailableBalance` uses the already-decremented `actualBalance`. This is a **data integrity bug**, not just a resilience gap. |
| **Severity** | Critical |
| **Effort** | Small |

---

## Summary Table

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Project Structure & Modularity | 0 | 1 | 1 | 3 | 5 |
| Error Handling & Exception Management | 2 | 3 | 1 | 0 | 6 |
| Test Coverage & Quality | 1 | 1 | 3 | 0 | 5 |
| Security & Access Control | 2 | 1 | 3 | 1 | 7 |
| API Design & Documentation | 0 | 2 | 2 | 2 | 6 |
| Observability & Monitoring | 0 | 1 | 3 | 1 | 5 |
| Resilience & Fault Tolerance | 3 | 2 | 2 | 0 | 7 |
| **Total** | **8** | **11** | **15** | **7** | **41** |
