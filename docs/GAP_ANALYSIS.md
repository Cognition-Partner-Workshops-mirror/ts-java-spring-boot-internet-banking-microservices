# Engineering Standards Gap Analysis

This document compares the Internet Banking Microservices codebase against industry engineering best practices and identifies gaps with severity ratings and remediation effort estimates.

**Severity Scale:**
- **Critical** — Production risk, security vulnerability, or data integrity issue
- **High** — Significant maintainability/reliability concern
- **Medium** — Deviation from best practices that increases tech debt
- **Low** — Polish/improvement opportunity

**Effort Scale:**
- **Small** — < 1 day per service
- **Medium** — 1–3 days per service
- **Large** — 1+ week, cross-cutting concern

---

## 1. Code Organization

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 1.1 | No shared library/module | Each service duplicates common code (`BaseMapper`, `AuditAware`, `TransactionStatus`, `ErrorResponse`, `GlobalExceptionHandler`) | Extract shared DTOs, exceptions, and utilities into a common Gradle module | Medium | Medium |
| 1.2 | Inconsistent package naming | Fund Transfer uses `service.rest.client` package; Utility Payment and User use `service.rest` | Standardize package structure across all services | Low | Small |
| 1.3 | No multi-module Gradle build | Each service is a standalone Gradle project with no parent `settings.gradle` | Use a Gradle multi-module project for unified builds, dependency management, and version alignment | Medium | Medium |
| 1.4 | Duplicated Feign client configuration | `CustomFeignClientConfiguration` is copy-pasted in Fund Transfer and Utility Payment services | Move to shared module or use common auto-configuration | Low | Small |
| 1.5 | No clear separation of layers | Controllers directly return `ResponseEntity` of service results; no dedicated response wrapper | Introduce a consistent API response envelope (e.g., `ApiResponse<T>`) | Low | Small |

---

## 2. Error Handling

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 2.1 | Generic catch-all handler returns raw exception | `handleException(Exception e)` returns `"Exception occur inside API " + e` — leaks stack traces | Return structured error response without internal details; log the exception server-side | Critical | Small |
| 2.2 | All errors return HTTP 400 | Both `GlobalExceptionHandler` methods return `ResponseEntity.badRequest()` regardless of error type | Map exceptions to appropriate HTTP status codes (404 for not found, 409 for conflict, 500 for unexpected) | High | Small |
| 2.3 | No error handling in Feign calls | Feign clients have no fallback, error decoder, or circuit breaker | Implement `ErrorDecoder` or `FallbackFactory` for graceful degradation | High | Medium |
| 2.4 | Inconsistent error response format | Core Banking uses `ErrorResponse` (code + message); generic handler returns plain string | Standardize all error responses to a single format with code, message, timestamp, and path | Medium | Small |
| 2.5 | No validation error handling | No `@Valid` annotations on request DTOs; no `MethodArgumentNotValidException` handler | Add Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`) and handle validation errors | High | Small |

---

## 3. Testing

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 3.1 | Minimal test coverage | Only Core Banking Service has meaningful unit tests (`TransactionServiceTest`); other services have only empty `ApplicationTests` | Unit tests for all service classes; aim for 80%+ line coverage | High | Large |
| 3.2 | No integration tests | No tests verify Feign client calls, database queries, or end-to-end flows | Add `@SpringBootTest` integration tests with Testcontainers (MySQL) | High | Large |
| 3.3 | No contract tests | No consumer-driven contracts between services (e.g., Pact or Spring Cloud Contract) | Implement contract tests to prevent breaking inter-service APIs | Medium | Large |
| 3.4 | No controller/API tests | No `@WebMvcTest` or MockMvc tests for REST endpoints | Add controller layer tests with MockMvc to verify request/response mapping and validation | Medium | Medium |
| 3.5 | Test configuration gap | Test `application.yml` files are empty or minimal; no H2 profile configuration visible | Configure test profiles with H2 in-memory database for isolated testing | Medium | Small |

---

## 4. Security

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 4.1 | Hardcoded credentials in source | Docker Compose contains MySQL root password, Keycloak admin credentials, and DB user passwords in plain text | Use environment variables, Docker secrets, or a vault for credentials | Critical | Small |
| 4.2 | No input validation | No `@Valid`, `@NotNull`, `@Size` annotations on any request DTOs across all services | Add Bean Validation annotations to all request objects | Critical | Small |
| 4.3 | CSRF disabled without justification | API Gateway disables CSRF (`csrf.disable()`) | Document rationale; acceptable for stateless JWT APIs but should be explicit | Low | Small |
| 4.4 | No rate limiting | No rate limiting on any endpoint including registration and authentication | Add rate limiting at the API Gateway level (Spring Cloud Gateway filters) | Medium | Medium |
| 4.5 | No dependency vulnerability scanning | No OWASP dependency-check, Snyk, or similar tool in build pipeline | Add `org.owasp.dependencycheck` Gradle plugin or integrate with CI/CD scanning | Medium | Small |
| 4.6 | Overly permissive DB user | `javatodev_development` user has `CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES` on `*.*` | Create per-service DB users with least-privilege grants | High | Small |
| 4.7 | No security headers | API Gateway doesn't add security headers (X-Content-Type-Options, X-Frame-Options, etc.) | Configure security headers as global gateway filters | Medium | Small |
| 4.8 | Password stored without additional hashing | User password sent directly to Keycloak `CredentialRepresentation` without additional validation | Add password complexity validation before forwarding to Keycloak | Medium | Small |

---

## 5. API Design

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 5.1 | No API versioning strategy | URLs use `/api/v1/` but no header-based or content-type versioning; no documented versioning policy | Document versioning strategy; consider supporting version negotiation | Low | Small |
| 5.2 | Raw `ResponseEntity` without type parameters | Controllers return `ResponseEntity` (raw type) instead of `ResponseEntity<FundTransferResponse>` | Add proper generic types to all `ResponseEntity` returns for OpenAPI generation accuracy | Medium | Small |
| 5.3 | No pagination metadata in responses | List endpoints accept `Pageable` but return raw `List` without total count, page number, or links | Return `Page<T>` or a wrapper with pagination metadata (totalElements, totalPages, hasNext) | High | Small |
| 5.4 | No filtering or sorting documentation | Pageable parameters are implicit from Spring; not documented in OpenAPI | Add `@Parameter` annotations for page, size, sort; add filtering query params where applicable | Medium | Small |
| 5.5 | Inconsistent resource naming | User Service uses `/bank-users/register`; could be more RESTful as `POST /bank-users` | Follow REST conventions: use nouns, HTTP methods for verbs, avoid action words in URIs | Low | Small |
| 5.6 | OpenAPI docs partially configured | Swagger/springdoc dependency present; `@Tag` and `@Operation` annotations added but no global API info | Add `@OpenAPIDefinition` with title, version, description, contact, and security schemes | Low | Small |
| 5.7 | No HATEOAS/hypermedia links | Responses don't include links to related resources | Consider Spring HATEOAS for discoverability (optional for internal APIs) | Low | Medium |

---

## 6. Observability

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 6.1 | No structured logging | Uses default Logback with unstructured log messages (string concatenation) | Configure JSON-formatted logging (Logstyle/ELK-compatible) with correlation IDs | Medium | Small |
| 6.2 | Sensitive data in logs | `FundTransferRequest.toString()` and `User.toString()` logged directly — may include account numbers, emails | Implement log redaction or custom `toString()` that masks sensitive fields | High | Small |
| 6.3 | No custom health indicators | Only default Spring Boot Actuator health endpoints; no checks for downstream dependencies | Add custom health indicators for MySQL connectivity, Keycloak availability, Feign targets | Medium | Small |
| 6.4 | No metrics endpoints | Prometheus listed in tech stack but no `micrometer-registry-prometheus` dependency in build files | Add Prometheus registry dependency and configure metrics export | Medium | Small |
| 6.5 | No alerting configuration | No thresholds, SLOs, or alert definitions | Define SLIs (latency p99, error rate) and configure alerting (Prometheus AlertManager or similar) | Medium | Medium |
| 6.6 | Incomplete tracing coverage | Tracing dependencies present but no custom spans for business operations | Add `@Observed` or manual spans for key business methods (fundTransfer, utilPayment) | Low | Small |
| 6.7 | Log levels not configurable at runtime | No Spring Boot Admin or actuator endpoint for dynamic log level changes | Expose `/actuator/loggers` and document how to change log levels without restart | Low | Small |

---

## 7. Resilience

| # | Gap | Current State | Best Practice | Severity | Effort |
|---|-----|--------------|---------------|----------|--------|
| 7.1 | No circuit breakers | Feign clients call Core Banking Service synchronously with no circuit breaker | Add Resilience4j circuit breakers to all Feign clients | Critical | Medium |
| 7.2 | No retry policies | No retry configuration for transient failures (network blips, temporary DB unavailability) | Configure Resilience4j retry with exponential backoff for Feign calls | High | Small |
| 7.3 | No timeout configuration | No explicit `connectTimeout` or `readTimeout` on Feign clients or RestTemplate | Set appropriate timeouts (e.g., 5s connect, 10s read) for all external calls | High | Small |
| 7.4 | No fallback behavior | Failed Feign calls propagate exceptions directly to the caller | Implement fallback responses (cached data, graceful degradation) for read operations | Medium | Medium |
| 7.5 | No bulkhead isolation | All requests share the same thread pool; one slow downstream can exhaust all threads | Configure Resilience4j bulkheads or separate thread pools per downstream | Medium | Medium |
| 7.6 | No idempotency protection | Fund transfer and payment endpoints can be called multiple times without idempotency keys | Add idempotency key header support to prevent duplicate transactions | Critical | Medium |
| 7.7 | Non-atomic distributed transaction | Fund Transfer Service saves local record, then calls Core Banking — if Core fails after partial save, data is inconsistent | Implement Saga pattern or use transactional outbox pattern | Critical | Large |
| 7.8 | No graceful shutdown | No shutdown hooks or deregistration delay configured | Configure `server.shutdown=graceful` and Eureka deregistration delay | Medium | Small |

---

## Summary

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Code Organization | 0 | 0 | 2 | 3 |
| Error Handling | 1 | 2 | 1 | 0 | (extra: 1 High for validation)
| Testing | 0 | 2 | 3 | 0 | (extra: 2 Medium)
| Security | 2 | 1 | 3 | 1 | (extra: 1 Critical, 1 High)
| API Design | 0 | 1 | 2 | 4 |
| Observability | 0 | 1 | 3 | 2 | (extra: 1 Medium)
| Resilience | 3 | 2 | 2 | 0 |
| **Total** | **6** | **9** | **16** | **10** |

### Top Critical Issues

1. **7.6** — No idempotency protection on financial transactions
2. **7.7** — Non-atomic distributed transactions risk data inconsistency
3. **7.1** — No circuit breakers; cascading failures possible
4. **2.1** — Stack trace leakage in error responses
5. **4.1** — Hardcoded credentials in Docker Compose
6. **4.2** — No input validation on any request DTO
