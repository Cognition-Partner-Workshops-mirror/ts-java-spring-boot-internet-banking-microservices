# Master Data Management Service — Gap Analysis

> Comparison of the current implementation against engineering best practices.
> Each gap is rated by **Severity** (Critical / High / Medium / Low) and **Effort** to remediate (Small / Medium / Large).

---

## 1. Code Organization

### 1.1 Project Structure
**Status**: Adequate

The service follows a clean layered architecture: `controller` → `service` → `repository` → `model`. Enums, DTOs, exception classes, and configuration are properly separated into dedicated packages.

### 1.2 Separation of Concerns
**Status**: Adequate with minor gaps

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No DTO validation layer | Medium | Small | Request DTOs lack `@Valid` / `@NotNull` / `@NotBlank` annotations. Validation is done ad-hoc in services (e.g., table name regex) rather than declaratively on DTOs. |
| No mapper layer | Low | Medium | Entity-to-DTO mapping is done inline in service methods. A mapper layer (MapStruct or manual mappers) would improve readability and reuse. |
| Environment sync JDBC logic in service | Medium | Medium | `EnvironmentSyncService` contains raw JDBC code alongside business logic. JDBC data access should be extracted to a repository/adapter class. |

### 1.3 Shared Libraries
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No shared library for common patterns | Low | Large | `AuditAware`, `GlobalExceptionHandler`, `ErrorResponse` are duplicated across services. A shared library module would reduce duplication. |

---

## 2. Error Handling

### 2.1 Centralized Exception Handling
**Status**: Implemented

`GlobalExceptionHandler` uses `@ControllerAdvice` to map exceptions to HTTP status codes:
- `EntityNotFoundException` → 404
- `IllegalStateException` → 400
- `IllegalArgumentException` → 400
- Generic `Exception` → 500

### 2.2 Error Response Format
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Inconsistent error response shape | Medium | Small | `ErrorResponse` contains `status`, `message`, `timestamp` but lacks `errorCode`, `path`, or `details` fields. Not aligned with RFC 7807 (Problem Details). |
| No request correlation in errors | Medium | Small | Error responses do not include a `traceId` or request path, making it hard to correlate errors with specific requests. |

### 2.3 Error Granularity
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Broad exception types | Medium | Small | Business rule violations (e.g., merging an already-merged dataspace, approving an already-approved workflow) use generic `IllegalStateException` rather than domain-specific exceptions. |

---

## 3. Testing

### 3.1 Unit Tests
**Status**: Critical Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No unit tests | Critical | Large | Zero unit tests exist. All 9 service classes and their business logic (merge, workflow approve/reject, snapshot comparison, SQL generation) are untested. |

### 3.2 Integration Tests
**Status**: Critical Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No integration tests | Critical | Large | No repository or controller integration tests. No `@SpringBootTest` or `@DataJpaTest` tests. The H2 test dependency is declared in `build.gradle` but unused. |

### 3.3 Contract Tests
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No API contract tests | High | Medium | No Spring Cloud Contract or Pact tests to verify the REST API contract. Breaking API changes would go undetected. |

### 3.4 Test Infrastructure
**Status**: Partial

- H2 dependency is declared for tests (good).
- `spring-boot-starter-test` and `spring-security-test` are declared (good).
- No test configuration, fixtures, or factories exist.

---

## 4. Security

### 4.1 Authentication
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| All endpoints permit unauthenticated access | High | Medium | `SecurityConfig` sets `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())`. Authentication is expected to be enforced at the API Gateway, but internal-only endpoints (e.g., user management, permission management) are unprotected if the service is accessed directly. |

### 4.2 Authorization
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Permission checks not enforced | High | Medium | `PermissionService.checkPermission()` exists but is never called from any controller or service method. Users can modify any dataspace/dataset/table without authorization. |
| No role-based endpoint restrictions | High | Medium | User roles (ADMIN, DATA_STEWARD, VIEWER) are stored but not used for endpoint access control. A VIEWER can create dataspaces, approve workflows, etc. |

### 4.3 Input Validation
**Status**: Partial

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Table name validation present | — | — | Table names are validated against `^[a-zA-Z_][a-zA-Z0-9_]*$` (good). |
| SQL injection defense-in-depth present | — | — | Table names quoted with backticks in SQL queries, plus runtime validation (good). |
| No request body validation | Medium | Small | No `@Valid` annotations on controller `@RequestBody` parameters. Missing or malformed fields cause raw JPA exceptions rather than structured 400 errors. |

### 4.4 Secrets Management
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Environment DB passwords stored in plaintext | High | Medium | `EnvironmentConfigEntity.dbPassword` is stored as plaintext in the MDM database and returned in API responses. Should be encrypted at rest and excluded from responses. |
| No secret rotation support | Medium | Medium | JDBC credentials for target environments are static with no rotation mechanism. |

### 4.5 Dependency Vulnerabilities
**Status**: Unknown

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No dependency vulnerability scanning | Medium | Small | No OWASP Dependency Check, Snyk, or similar plugin configured in `build.gradle`. |

---

## 5. API Design

### 5.1 RESTful Conventions
**Status**: Mostly Compliant

- Resources use plural nouns (`/dataspaces`, `/datasets`, `/records`).
- Standard HTTP methods (GET, POST, PUT, DELETE) are used appropriately.
- Nested resources for scoped queries (`/datasets/dataspace/{dataspaceId}`).

| Gap | Severity | Effort | Description |
|---|---|---|---|
| DELETE on dataspace returns entity (close) rather than 204 | Low | Small | Semantic mismatch — DELETE closes but doesn't delete. Could be `POST /dataspaces/{id}/close` instead. |
| No HATEOAS links | Low | Large | Responses don't include hypermedia links to related resources. |

### 5.2 Pagination & Filtering
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No pagination on list endpoints | High | Medium | All list endpoints return unbounded results. `GET /api/v1/records/table/{tableId}` could return thousands of records with no limit. Controllers accept `Pageable` but don't return `Page<T>` metadata (total elements, total pages). |
| No filtering or search | Medium | Medium | No query parameters for filtering records by status, date range, or data content. |

### 5.3 API Versioning
**Status**: Partial

| Gap | Severity | Effort | Description |
|---|---|---|---|
| URL-based versioning with `/api/v1` | — | — | Version prefix exists (good). |
| No versioning strategy documented | Low | Small | No documentation on how version changes will be managed. |

### 5.4 OpenAPI Documentation
**Status**: Partial

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No custom OpenAPI annotations | Medium | Small | springdoc-openapi is included as a dependency but controllers lack `@Operation`, `@ApiResponse`, `@Schema` annotations. Auto-generated docs will have minimal descriptions. |

---

## 6. Observability

### 6.1 Logging
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No structured logging | High | Small | No SLF4J `Logger` instances in any service or controller class. Operations like merge, workflow approval, snapshot creation, and environment sync produce no log output. |
| No log correlation | Medium | Small | Without logging, trace IDs from Micrometer/Brave are not included in any log output. |

### 6.2 Health Checks
**Status**: Partial

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Default Actuator health only | Medium | Small | `spring-boot-starter-actuator` is included, providing `/actuator/health`. No custom health indicators for database connectivity or config server reachability. |

### 6.3 Metrics
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No custom business metrics | Medium | Medium | No Micrometer counters/timers for operations like records created, workflows approved/rejected, snapshots taken, sync comparisons performed. |

### 6.4 Distributed Tracing
**Status**: Partial

| Gap | Severity | Effort | Description |
|---|---|---|---|
| Tracing dependencies present but unconfigured | Medium | Small | `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` are in `build.gradle`, but sampling rate and Zipkin endpoint are not configured in application properties. |

---

## 7. Resilience

### 7.1 Circuit Breakers
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No circuit breakers | High | Medium | Environment sync connects to external databases via JDBC. No Resilience4j circuit breaker protects against slow or unavailable target databases. |

### 7.2 Retry Policies
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No retry policies | Medium | Small | JDBC connections to target environments have no retry mechanism for transient failures. |

### 7.3 Timeouts
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No connection/read timeouts | High | Small | `EnvironmentSyncService` creates `DriverManager.getConnection()` with no timeout. A slow target DB will block the calling thread indefinitely. |

### 7.4 Fallback Behavior
**Status**: Gap

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No graceful degradation | Medium | Medium | If Config Server or Eureka is unavailable, the service fails to start with no fallback behavior. |

---

## 8. Data Integrity & Consistency

| Gap | Severity | Effort | Description |
|---|---|---|---|
| No `@Transactional` annotations | High | Small | Service methods that perform multi-step operations (merge, workflow approve/reject) are not annotated with `@Transactional`. A failure mid-operation could leave data in an inconsistent state. |
| No optimistic locking on workflow approval | Medium | Small | Two reviewers could approve/reject the same workflow concurrently. The `@Version` field exists but race conditions are not explicitly handled. |
| Cascade delete risks | Medium | Small | `CascadeType.ALL` with `orphanRemoval = true` on some relationships could cascade deletes unexpectedly if not carefully managed. |

---

## 9. Gap Summary

| Category | Critical | High | Medium | Low |
|---|---|---|---|---|
| Code Organization | 0 | 0 | 2 | 2 |
| Error Handling | 0 | 0 | 3 | 0 |
| Testing | 2 | 1 | 0 | 0 |
| Security | 0 | 4 | 2 | 0 |
| API Design | 0 | 1 | 2 | 2 |
| Observability | 0 | 1 | 3 | 0 |
| Resilience | 0 | 2 | 2 | 0 |
| Data Integrity | 0 | 1 | 2 | 0 |
| **Total** | **2** | **10** | **16** | **4** |

### Priority Order for Remediation
1. **Critical**: Unit tests, integration tests
2. **High**: Permission enforcement, structured logging, pagination, circuit breakers, JDBC timeouts, `@Transactional`, plaintext passwords, request validation
3. **Medium**: DTO validation, error format, tracing config, custom metrics, retry policies, JDBC repository extraction
4. **Low**: Mapper layer, shared library, HATEOAS, versioning docs
