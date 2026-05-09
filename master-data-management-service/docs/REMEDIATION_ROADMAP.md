# Master Data Management Service — Remediation Roadmap

> Prioritized plan to address gaps identified in the [Gap Analysis](./GAP_ANALYSIS.md).
> Each item includes a sample **Devin prompt** for execution.

---

## Phase 1 — Quick Wins (High Severity / Low Effort)

Items that significantly improve reliability and maintainability with minimal code changes.

### 1.1 Add `@Transactional` to Service Methods

**Gap**: Multi-step operations (merge, workflow approve/reject, snapshot create) have no transaction boundaries.
**Severity**: High | **Effort**: Small

Add `@Transactional` to all service methods that perform multiple repository writes.

**Devin prompt:**
> In the `master-data-management-service`, add `@Transactional` annotations to all public methods in the service classes that perform more than one repository call. Specifically: `DataspaceService.mergeDataspace`, `WorkflowService.approveWorkflow`, `WorkflowService.rejectWorkflow`, `SnapshotService.createSnapshot`, `MasterDataRecordService.insertRecord`, `MasterDataRecordService.updateRecord`, `MasterDataRecordService.deleteRecord`. Import `org.springframework.transaction.annotation.Transactional`. Add comments explaining why each method needs transactional behavior.

---

### 1.2 Add Request DTO Validation

**Gap**: No `@Valid` annotations on controller request bodies; missing fields cause raw JPA exceptions.
**Severity**: Medium | **Effort**: Small

Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`) to all request DTOs and `@Valid` on controller parameters.

**Devin prompt:**
> In the `master-data-management-service`, add Jakarta Bean Validation annotations to all request DTO classes under `model.request`. Add `@NotBlank` to required string fields (name, username, email), `@NotNull` to required reference fields (dataspaceId, datasetId, tableId), and `@Size` constraints where appropriate. Then add `@Valid` to every `@RequestBody` parameter in all 9 controller classes. Add a `MethodArgumentNotValidException` handler in `GlobalExceptionHandler` that returns a structured 400 response with field-level error details. Add comments explaining the validation rules.

---

### 1.3 Add Structured Logging

**Gap**: No SLF4J loggers in any service class. Operations produce zero log output.
**Severity**: High | **Effort**: Small

Add SLF4J loggers with meaningful log statements at key business operation points.

**Devin prompt:**
> In the `master-data-management-service`, add `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` to all 9 service classes. Add `log.info()` for successful business operations (create, merge, approve, reject, snapshot, sync), `log.warn()` for validation failures and rejected workflows, and `log.error()` for unexpected exceptions. Include relevant context (entity IDs, user names, operation types) in log messages. Add comments describing what each log statement captures.

---

### 1.4 Add JDBC Connection Timeouts

**Gap**: `EnvironmentSyncService` JDBC connections have no timeout; slow target DBs block threads indefinitely.
**Severity**: High | **Effort**: Small

Set connection and read timeouts on JDBC connections used for environment sync.

**Devin prompt:**
> In `EnvironmentSyncService.java`, update the `readTargetTableData` and `compareWithEnvironment` methods to set JDBC connection and query timeouts. Before `DriverManager.getConnection()`, set system properties or use `DriverManager.setLoginTimeout(10)`. After getting the connection, call `statement.setQueryTimeout(30)`. Wrap the JDBC block in a try-with-resources to ensure connections are always closed. Add comments explaining the timeout values chosen.

---

### 1.5 Configure Distributed Tracing

**Gap**: Micrometer/Brave dependencies are declared but tracing is not configured.
**Severity**: Medium | **Effort**: Small

Add tracing configuration to application properties.

**Devin prompt:**
> In the `master-data-management-service`, add the following properties to `application.yml`: `management.tracing.sampling.probability: 1.0` (or 0.1 for production), `management.zipkin.tracing.endpoint: http://zipkin:9411/api/v2/spans`. Also add `logging.pattern.level: '%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]'` for trace ID inclusion in log output. Add comments explaining each configuration property.

---

### 1.6 Enhance OpenAPI Documentation

**Gap**: springdoc-openapi is included but controllers lack descriptive annotations.
**Severity**: Medium | **Effort**: Small

Add `@Operation`, `@ApiResponse`, and `@Tag` annotations to all controllers.

**Devin prompt:**
> In the `master-data-management-service`, add OpenAPI annotations to all 9 controller classes. Add `@Tag(name = "...", description = "...")` at the class level. Add `@Operation(summary = "...", description = "...")` to each endpoint method. Add `@ApiResponse` annotations for success (200/201) and error (400/404/500) responses. Add comments for any non-obvious API behaviors documented in the annotations.

---

## Phase 2 — Important (High Severity / Medium Effort)

Items that address significant quality and security gaps requiring more substantial changes.

### 2.1 Add Unit Tests for All Services

**Gap**: Zero unit tests. All business logic is untested.
**Severity**: Critical | **Effort**: Large

Create comprehensive unit tests for all 9 service classes using JUnit 5 and Mockito.

**Devin prompt:**
> In the `master-data-management-service`, create unit tests for all 9 service classes under `src/test/java/com/javatodev/finance/service/`. Use JUnit 5 with `@ExtendWith(MockitoExtension.class)`. Mock all repository dependencies with `@Mock` and inject into service with `@InjectMocks`. Test these critical paths: (1) DataspaceService — create, merge (verify deep-copy of columns), close; (2) WorkflowService — approve INSERT/UPDATE/DELETE, reject INSERT/UPDATE/DELETE; (3) SnapshotService — create snapshot, compare two snapshots; (4) EnvironmentSyncService — compare with environment, SQL script generation; (5) MasterDataRecordService — insert (creates workflow), update (saves history), delete; (6) MdmUserService — create (BCrypt hashing), deactivate; (7) PermissionService — grant, revoke, check. Add comments explaining the purpose of each test method.

---

### 2.2 Add Integration Tests for Controllers

**Gap**: No `@SpringBootTest` or `@WebMvcTest` tests.
**Severity**: Critical | **Effort**: Large

Create controller integration tests using MockMvc with H2 in-memory database.

**Devin prompt:**
> In the `master-data-management-service`, create integration tests for all 9 controllers under `src/test/java/com/javatodev/finance/controller/`. Use `@WebMvcTest` with `@MockBean` for service dependencies. Test happy paths (200/201 responses with correct JSON shape) and error paths (400 for invalid input, 404 for missing entities). Create an `application-test.yml` with H2 database configuration. Add a test for the full workflow: create dataspace → create dataset → create table → insert record → approve workflow → create snapshot. Add comments explaining the test scenarios.

---

### 2.3 Enforce Permission Checks

**Gap**: `PermissionService.checkPermission()` exists but is never called. All endpoints are unprotected.
**Severity**: High | **Effort**: Medium

Wire permission checks into service methods or use Spring Security method-level authorization.

**Devin prompt:**
> In the `master-data-management-service`, enforce permission checks for data-modifying operations. Option A (recommended for POC): Add permission check calls at the beginning of service methods — `DataspaceService.updateDataspace`, `DatasetService.createDataset`, `TableDefinitionService.createTableDefinition`, `MasterDataRecordService.insertRecord`, `WorkflowService.approveWorkflow`. Call `permissionService.checkPermission(userId, resourceType, resourceId, requiredLevel)` and throw `AccessDeniedException` if unauthorized. Add a handler for `AccessDeniedException` in `GlobalExceptionHandler` returning 403. The `userId` should come from a request header (e.g., `X-User-Id`) or the security context. Add comments explaining the permission model and why each check is placed where it is.

---

### 2.4 Encrypt Environment Database Passwords

**Gap**: `EnvironmentConfigEntity.dbPassword` stored in plaintext and returned in API responses.
**Severity**: High | **Effort**: Medium

Encrypt passwords at rest and exclude from API responses.

**Devin prompt:**
> In the `master-data-management-service`, protect environment database passwords. (1) Create a `CryptoUtil` class using AES-256 encryption with a key sourced from an environment variable `MDM_ENCRYPTION_KEY`. (2) In `EnvironmentSyncService.registerEnvironment`, encrypt `dbPassword` before persisting. In `compareWithEnvironment`, decrypt before JDBC connection. (3) In `EnvironmentConfigResponse` DTO, exclude the `dbPassword` field entirely (or mask it as `****`). (4) Add a `@JsonIgnore` on the password field in the response DTO. Add comments explaining the encryption approach and key management expectations.

---

### 2.5 Add Pagination Metadata to List Endpoints

**Gap**: List endpoints return unbounded results with no pagination metadata.
**Severity**: High | **Effort**: Medium

Return `Page<T>` from repositories and wrap responses with pagination info.

**Devin prompt:**
> In the `master-data-management-service`, add proper pagination support. (1) Update all repository interfaces to extend `JpaRepository` (already done) and use `Pageable` parameter in custom query methods. (2) Update all service list methods to accept `Pageable` and return `Page<T>`. (3) Create a `PagedResponse<T>` wrapper DTO containing `content`, `page`, `size`, `totalElements`, `totalPages`, `last`. (4) Update all controller list endpoints to accept `@RequestParam defaultValue` for page/size and return `PagedResponse<T>`. Add comments explaining the pagination model.

---

### 2.6 Add Circuit Breakers for Environment Sync

**Gap**: JDBC connections to external databases have no circuit breaker protection.
**Severity**: High | **Effort**: Medium

Add Resilience4j circuit breakers around external JDBC calls.

**Devin prompt:**
> In the `master-data-management-service`, add Resilience4j circuit breaker support for environment sync operations. (1) Add `spring-cloud-starter-circuitbreaker-resilience4j` dependency to `build.gradle`. (2) Annotate `EnvironmentSyncService.compareWithEnvironment` with `@CircuitBreaker(name = "envSync", fallbackMethod = "compareWithEnvironmentFallback")`. (3) Create a fallback method that returns an error response indicating the target environment is unreachable. (4) Add Resilience4j configuration in `application.yml` with sensible defaults (failure rate threshold: 50%, wait duration: 30s, sliding window: 10). Add comments explaining the circuit breaker configuration and fallback behavior.

---

## Phase 3 — Polish (Lower Severity / Variable Effort)

Items that improve code quality and developer experience.

### 3.1 Improve Error Response Format (RFC 7807)

**Gap**: Error responses lack `errorCode`, `path`, `traceId` fields.
**Severity**: Medium | **Effort**: Small

Adopt RFC 7807 Problem Details format for all error responses.

**Devin prompt:**
> In the `master-data-management-service`, update `ErrorResponse` to follow RFC 7807 Problem Details. Add fields: `type` (URI reference), `title`, `status`, `detail`, `instance` (request path), `traceId`. Update `GlobalExceptionHandler` to populate all fields. Include the Micrometer trace ID from `Tracer.currentSpan()` when available. Add comments explaining the RFC 7807 format and each field's purpose.

---

### 3.2 Create Domain-Specific Exceptions

**Gap**: Business rule violations use generic `IllegalStateException`.
**Severity**: Medium | **Effort**: Small

Replace generic exceptions with descriptive domain exceptions.

**Devin prompt:**
> In the `master-data-management-service`, create domain-specific exception classes: `DataspaceNotOpenException`, `WorkflowAlreadyReviewedException`, `DuplicateRecordException`, `InvalidDataspaceStateException`. Place them in the `exception` package. Update service methods to throw these instead of `IllegalStateException`. Add handlers in `GlobalExceptionHandler` mapping each to appropriate HTTP status codes (409 Conflict for state violations, 422 for business rule violations). Add comments explaining when each exception is thrown.

---

### 3.3 Extract JDBC Logic to Repository Adapter

**Gap**: Raw JDBC code mixed with business logic in `EnvironmentSyncService`.
**Severity**: Medium | **Effort**: Medium

Extract JDBC operations into a dedicated adapter class.

**Devin prompt:**
> In the `master-data-management-service`, create a `TargetEnvironmentRepository` class in the `repository` package. Move all JDBC operations from `EnvironmentSyncService` (connecting to target DB, reading table data, executing SQL statements) into this new class. The adapter should accept `EnvironmentConfigEntity` for connection details and expose methods: `readTableData(config, tableName)`, `executeScript(config, sqlStatements)`. Update `EnvironmentSyncService` to delegate to `TargetEnvironmentRepository`. Add comments explaining the separation of concerns.

---

### 3.4 Add Entity-to-DTO Mappers

**Gap**: Entity-to-DTO mapping is done inline in service methods.
**Severity**: Low | **Effort**: Medium

Create a dedicated mapper layer for cleaner separation.

**Devin prompt:**
> In the `master-data-management-service`, create mapper classes in a new `mapper` package. Create `DataspaceMapper`, `DatasetMapper`, `TableDefinitionMapper`, `MasterDataRecordMapper`, `SnapshotMapper`, `WorkflowMapper`, `MdmUserMapper`, `PermissionMapper`, `EnvironmentConfigMapper`. Each mapper should have `toResponse(Entity)` and `toEntity(Request)` static methods. Update service classes to use mappers instead of inline mapping. Add comments explaining the mapping logic.

---

### 3.5 Add Custom Business Metrics

**Gap**: No Micrometer counters/timers for business operations.
**Severity**: Medium | **Effort**: Medium

Add Micrometer metrics for key operations.

**Devin prompt:**
> In the `master-data-management-service`, add Micrometer metrics using `MeterRegistry`. (1) Inject `MeterRegistry` into service classes. (2) Add counters: `mdm.records.created`, `mdm.records.approved`, `mdm.records.rejected`, `mdm.snapshots.created`, `mdm.sync.comparisons`, `mdm.dataspaces.merged`. (3) Add timers: `mdm.sync.comparison.duration`, `mdm.snapshot.creation.duration`. (4) Add gauges: `mdm.workflows.pending` (count of pending workflows). Tag metrics with relevant dimensions (dataspace, table). Add comments explaining what each metric measures.

---

### 3.6 Add Retry Policies for JDBC Operations

**Gap**: No retry mechanism for transient JDBC failures.
**Severity**: Medium | **Effort**: Small

Add Resilience4j retry around environment sync JDBC calls.

**Devin prompt:**
> In the `master-data-management-service`, add Resilience4j retry to `EnvironmentSyncService` JDBC operations. Add `@Retry(name = "envSync")` alongside the circuit breaker annotation. Configure in `application.yml`: max attempts 3, wait duration 2s, exponential backoff multiplier 2, retry on `SQLException` and `ConnectException`. Add comments explaining the retry strategy and when retries are appropriate.

---

### 3.7 Add Dependency Vulnerability Scanning

**Gap**: No vulnerability scanning configured.
**Severity**: Medium | **Effort**: Small

Add OWASP Dependency Check to the Gradle build.

**Devin prompt:**
> In the `master-data-management-service`, add the OWASP Dependency Check Gradle plugin. Add `id 'org.owasp.dependencycheck' version '9.0.9'` to the `plugins` block in `build.gradle`. Configure it to fail the build on CVSS score >= 7. Run `./gradlew dependencyCheckAnalyze` and address any critical/high vulnerabilities found. Add a comment in build.gradle explaining the dependency check configuration.

---

### 3.8 Add Config Server Fallback

**Gap**: Service fails to start if Config Server is unavailable.
**Severity**: Medium | **Effort**: Medium

Add local fallback configuration for development and resilience.

**Devin prompt:**
> In the `master-data-management-service`, add Config Server fallback behavior. (1) Set `spring.cloud.config.fail-fast: false` in `bootstrap.yml` for development profile. (2) Add a complete `application-local.yml` with all required properties (datasource, JPA, server port, Eureka) so the service can start without Config Server. (3) Document the fallback behavior in `README.md`. Add comments explaining each fallback configuration property.

---

## Phase Summary

| Phase | Items | Key Outcomes |
|---|---|---|
| **Phase 1** (Quick Wins) | 6 items | Transaction safety, input validation, logging, JDBC timeouts, tracing, OpenAPI docs |
| **Phase 2** (Important) | 6 items | Unit tests, integration tests, permission enforcement, password encryption, pagination, circuit breakers |
| **Phase 3** (Polish) | 8 items | RFC 7807 errors, domain exceptions, JDBC adapter, mappers, metrics, retries, vulnerability scanning, config fallback |
| **Total** | **20 items** | Full production readiness |

---

## Dependency Graph

Some items have dependencies on others. Recommended execution order within each phase:

**Phase 1** (can be parallelized):
```
1.1 @Transactional ──┐
1.2 DTO Validation ──┤
1.3 Logging ─────────┤── All independent, can run in parallel
1.4 JDBC Timeouts ───┤
1.5 Tracing Config ──┤
1.6 OpenAPI Docs ────┘
```

**Phase 2** (sequential dependencies):
```
2.1 Unit Tests ──────────────────────┐
2.2 Integration Tests ───────────────┤── Tests should come first
                                     │
2.3 Permission Enforcement ──────────┤── Independent
2.4 Password Encryption ────────────┤── Independent
2.5 Pagination ──────────────────────┤── Independent
2.6 Circuit Breakers ────────────────┘── Independent
```

**Phase 3** (suggested order):
```
3.1 RFC 7807 Errors ──→ 3.2 Domain Exceptions (builds on 3.1)
3.3 JDBC Adapter ─────→ 3.6 Retry Policies (depends on 3.3)
3.4 Mappers ───────────── Independent
3.5 Business Metrics ──── Independent
3.7 Vulnerability Scan ── Independent
3.8 Config Fallback ───── Independent
```
