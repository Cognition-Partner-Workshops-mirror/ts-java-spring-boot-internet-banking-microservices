# Phase 2 Implementation Plan

Phase 2 focuses on architectural hardening, testing, and authorization. These items build on the Phase 1 foundation (bug fixes, validation, resilience basics) and address the remaining Critical/High severity gaps.

**Prerequisites:** All Phase 1 items (PR #108) must be merged first.

---

## Summary

| # | Item | Severity | Effort | Services Affected |
|---|------|----------|--------|-------------------|
| 2.1 | Circuit breakers on Feign clients | Critical | Medium | fund-transfer, utility-payment, user-service |
| 2.2 | Idempotency keys for financial transactions | Critical | Medium | fund-transfer, utility-payment, core-banking |
| 2.3 | Extract shared library (`banking-common`) | High | Medium | all 4 application services |
| 2.4 | Authorization enforcement in downstream services | Critical | Large | fund-transfer, utility-payment, user-service |
| 2.5 | Unit tests for all services | High | Large | fund-transfer, utility-payment, user-service |
| 2.6 | Standardize REST URI naming | Medium | Medium | all services + gateway + Feign clients |
| 2.7 | Structured JSON logging | Medium | Small | all 4 application services |
| 2.8 | Custom health indicators | Medium | Medium | all 4 application services + gateway |

**Estimated total effort:** 3–4 weeks

**Recommended execution order:** 2.1 → 2.2 → 2.4 → 2.3 → 2.5 → 2.7 → 2.8 → 2.6

---

## 2.1 Add Circuit Breakers to Feign Clients

**Gap Ref:** 7.1 | **Severity:** Critical | **Effort:** Medium (~2 days)

### Problem
When core-banking-service is down, fund-transfer and utility-payment services make unbounded blocking calls that cascade into thread exhaustion and full system outage. Phase 1 added timeouts (5s/10s) and retries (3 attempts), but there is no circuit breaker to stop calling a known-down service.

### Scope

**Dependency changes (3 services):**
- `internet-banking-fund-transfer-service/build.gradle`
- `internet-banking-utility-payment-service/build.gradle`
- `internet-banking-user-service/build.gradle`

Add:
```groovy
implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j'
```

**Configuration (3 services' `application.yml`):**
```yaml
spring:
  cloud:
    openfeign:
      circuitbreaker:
        enabled: true

resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
    instances:
      core-banking-service:
        base-config: default
```

**New files (3 fallback classes):**
- `fund-transfer/.../service/rest/client/BankingCoreFeignClientFallback.java`
  - `readAccount()` → throw `ServiceException("Core banking service unavailable", SERVICE_UNAVAILABLE)`
  - `fundTransfer()` → return `FundTransferResponse` with status `FAILED`
- `utility-payment/.../service/rest/BankingCoreRestClientFallback.java`
  - `readAccount()` → throw `ServiceException("Core banking service unavailable", SERVICE_UNAVAILABLE)`
  - `utilityPayment()` → return `UtilityPaymentResponse` with status `FAILED`
- `user-service/.../service/rest/BankingCoreRestClientFallback.java`
  - All methods → throw `ServiceException` with clear message

**Modified files:**
- Each `@FeignClient` annotation gets `fallback = XxxFallback.class`

### Verification
- Start the stack without core-banking-service running
- Call fund-transfer endpoint → should return graceful error within timeout, not hang
- After core-banking starts, verify calls succeed after half-open window

---

## 2.2 Add Idempotency Keys for Financial Transactions

**Gap Ref:** 7.5 | **Severity:** Critical | **Effort:** Medium (~2 days)

### Problem
`POST /api/v1/transfer` and `POST /api/v1/utility-payment` have no idempotency protection. If a client retries (or Phase 1's Feign retryer fires), duplicate transactions can be created, debiting accounts multiple times.

### Scope

**DTO changes:**
- `fund-transfer/.../model/dto/request/FundTransferRequest.java` — add `@NotBlank String idempotencyKey`
- `utility-payment/.../model/rest/request/UtilityPaymentRequest.java` — add `@NotBlank String idempotencyKey`
- `core-banking/.../model/dto/request/FundTransferRequest.java` — add `String idempotencyKey`
- `core-banking/.../model/dto/request/UtilityPaymentRequest.java` — add `String idempotencyKey`

**Entity changes:**
- `fund-transfer/.../model/entity/FundTransferEntity.java` — add `@Column(unique = true) String idempotencyKey`
- `utility-payment/.../model/entity/UtilityPaymentEntity.java` — add `@Column(unique = true) String idempotencyKey`

**Database migrations:**
- Add Flyway migration (or JPA auto-DDL) for `ALTER TABLE fund_transfer ADD COLUMN idempotency_key VARCHAR(36) UNIQUE`
- Same for `utility_payment`

**Service logic changes:**
- `FundTransferService.fundTransfer()`:
  ```java
  Optional<FundTransferEntity> existing = repo.findByIdempotencyKey(request.getIdempotencyKey());
  if (existing.isPresent()) {
      if (existing.get().getStatus() == TransactionStatus.SUCCESS) {
          return mapper.convertToDto(existing.get()); // return cached result
      }
      throw new ConflictException("Transaction is already being processed");
  }
  ```
- `UtilityPaymentService.utilPayment()` — same pattern

**Repository changes:**
- `FundTransferRepository` — add `Optional<FundTransferEntity> findByIdempotencyKey(String key)`
- `UtilityPaymentRepository` — add `Optional<UtilityPaymentEntity> findByIdempotencyKey(String key)`

**Exception handling:**
- New `ConflictException` class → maps to HTTP 409 in `GlobalExceptionHandler`

### Verification
- Send the same fund transfer request twice with same idempotency key → second call returns original response, account only debited once
- Send with different key → separate transaction

---

## 2.3 Extract Shared Library (`banking-common`)

**Gap Ref:** 1.2 | **Severity:** High | **Effort:** Medium (~2 days)

### Problem
7 classes are duplicated across 3–4 services. Bug fixes (like Phase 1's GlobalExceptionHandler changes) must be applied to every copy. Inconsistencies already exist.

### Scope

**New module:** `banking-common/`
```
banking-common/
├── build.gradle          # java-library plugin, no Spring Boot
└── src/main/java/com/javatodev/finance/common/
    ├── exception/
    │   ├── SimpleBankingGlobalException.java
    │   ├── EntityNotFoundException.java
    │   ├── ErrorResponse.java
    │   ├── ServiceException.java
    │   └── BaseGlobalExceptionHandler.java  (abstract)
    ├── mapper/
    │   └── BaseMapper.java
    ├── audit/
    │   ├── AuditAware.java
    │   ├── AuditConfig.java
    │   └── AuditorAwareConfig.java
    ├── filter/
    │   ├── AppAuthUserFilter.java
    │   ├── ApiRequestContext.java
    │   └── ApiRequestContextHolder.java
    └── dto/
        └── PageResponse.java
```

**Root project files (new):**
- `settings.gradle` — include all 8 modules
- `build.gradle` — common plugin versions, Java 21, shared repos

**Each service's `build.gradle`:**
```groovy
implementation project(':banking-common')
```
Then delete the local copies of the shared classes.

### Verification
- `./gradlew clean build` from root succeeds for all modules
- Existing tests still pass
- No duplicate class warnings

---

## 2.4 Add Authorization Enforcement in Downstream Services

**Gap Ref:** 4.6 | **Severity:** Critical | **Effort:** Large (~3 days)

### Problem
JWT validation only happens at the API Gateway. Downstream services trust the `X-Auth-Id` header without verification. Anyone who can reach a downstream service directly (e.g., within the Docker network) can impersonate any user.

### Scope

**fund-transfer-service:**
- `FundTransferService.fundTransfer()` — before processing, call `bankingCoreFeignClient.readAccount(fromAccount)` and verify the account's user auth ID matches `ApiRequestContextHolder.getContext().getAuthId()`
- If mismatch → throw `AuthorizationException("User does not own the source account")`

**utility-payment-service:**
- `UtilityPaymentService.utilPayment()` — same pattern: verify account ownership

**user-service:**
- `UserController.readUser()` / `updateUser()` — verify the authenticated user is accessing their own record OR has admin role (check Keycloak role claims from the token)

**New files (all 3 services):**
- `AuthorizationException.java` (or add to `banking-common` if 2.3 is done first)
- Add `@ExceptionHandler(AuthorizationException.class)` returning HTTP 403 to `GlobalExceptionHandler`

**core-banking-service changes:**
- `AccountResponse` DTO — ensure it includes the `userId` or `authId` field so downstream services can verify ownership (check if this field already exists; if not, add it to the entity/DTO/mapper)

### Verification
- Call fund-transfer with User A's auth header but User B's account → HTTP 403
- Call with correct ownership → succeeds
- Admin user can access any user record in user-service

---

## 2.5 Add Unit Tests to All Services

**Gap Ref:** 3.1 | **Severity:** High | **Effort:** Large (~4 days)

### Problem
Only `core-banking-service` has real unit tests (3 test classes). The other 3 services have only empty `ApplicationTests`. Phase 1 and Phase 2 changes have no test coverage.

### Scope

**internet-banking-fund-transfer-service:**
- `FundTransferServiceTest` — mock `FundTransferRepository`, `BankingCoreFeignClient`, `FundTransferMapper`
  - Happy path: verify entity saved with PENDING → Feign called → entity updated to SUCCESS
  - Feign error: verify entity stays PENDING / updated to FAILED
  - Idempotency: verify duplicate key returns existing result (if 2.2 is done)
  - Validation: verify invalid requests are rejected
- `FundTransferControllerTest` — `@WebMvcTest` with mocked service
  - Test POST /api/v1/transfer with valid/invalid payloads
  - Test GET /api/v1/transfer returns `PageResponse`

**internet-banking-utility-payment-service:**
- `UtilityPaymentServiceTest` — same pattern as fund-transfer
- `UtilityPaymentControllerTest` — `@WebMvcTest`

**internet-banking-user-service:**
- `UserServiceTest` — mock `UserRepository`, `KeycloakUserService`, `BankingCoreRestClient`
  - `createUser()`: happy path, user not found in core-banking, Keycloak error
  - `readUsers()`: verify pagination metadata
  - `updateUser()`: status transition PENDING → APPROVED
- `KeycloakUserServiceTest` — mock `KeycloakManager`
- `UserControllerTest` — `@WebMvcTest`

### Testing patterns
- Use `@ExtendWith(MockitoExtension.class)` with `@Mock` / `@InjectMocks`
- Follow existing core-banking test patterns (see `TransactionServiceTest`)
- Minimum target: every service method has at least one happy-path and one error-path test

### Verification
- `./gradlew test` passes in all 4 services
- No test depends on external services (MySQL, Keycloak, Eureka)

---

## 2.6 Standardize REST URI Naming

**Gap Ref:** 5.1 | **Severity:** Medium | **Effort:** Medium (~1.5 days)

### Problem
URI patterns are inconsistent and don't follow REST conventions:
- `/api/v1/bank-users/register` (verb in URI)
- `/api/v1/bank-users/update/{id}` (verb in URI)
- `/api/v1/account/bank-account/{number}` (redundant nesting)

### Scope

| Current | Proposed | Method |
|---------|----------|--------|
| `POST /api/v1/bank-users/register` | `POST /api/v1/bank-users` | user-service |
| `PATCH /api/v1/bank-users/update/{id}` | `PATCH /api/v1/bank-users/{id}` | user-service |
| `GET /api/v1/account/bank-account/{number}` | `GET /api/v1/accounts/{number}` | core-banking |
| `GET /api/v1/account/util-account/{name}` | `GET /api/v1/utility-accounts/{name}` | core-banking |

**Files to update:**
- Controllers in core-banking and user-service (`@RequestMapping` paths)
- Feign client interfaces in fund-transfer and utility-payment (path references)
- API Gateway route configuration
- `SecurityConfiguration` in user-service (permitAll path for registration)
- Postman collection files

### Risk
This is a **breaking API change**. Should be coordinated with any frontend/consumer teams. Consider keeping old paths as deprecated aliases during transition.

### Verification
- All Feign clients call correct new paths
- Gateway routes updated and tested
- Postman collection updated and all requests succeed

---

## 2.7 Improve Logging Consistency and Structure

**Gap Ref:** 6.1 | **Severity:** Medium | **Effort:** Small (~1 day)

### Problem
Logging is inconsistent. Some controllers have `@Slf4j`, others don't. Log output is plain text with no structured format. No correlation with Zipkin trace IDs in logs.

### Scope

**Dependency (all 4 services' `build.gradle`):**
```groovy
implementation 'net.logstash.logback:logstash-logback-encoder:7.4'
```

**New file (each service):**
- `src/main/resources/logback-spring.xml` — JSON structured log format with traceId/spanId from Micrometer/Brave

**Configuration (`application.yml` additions):**
```yaml
logging:
  level:
    com.javatodev.finance: INFO
    org.springframework.cloud: WARN
```

**Code audit:**
- Ensure all controllers and services have `@Slf4j`
- Ensure log statements use parameterized messages (`log.info("msg {}", val)` not string concatenation)

### Verification
- Start a service, hit an endpoint, verify JSON log output in console
- Verify traceId appears in log entries when Zipkin is running

---

## 2.8 Add Custom Health Indicators

**Gap Ref:** 6.2 | **Severity:** Medium | **Effort:** Medium (~1.5 days)

### Problem
Services expose `/actuator/health` but only show basic UP/DOWN. No visibility into downstream dependency health. No readiness/liveness probes for container orchestration.

### Scope

**Configuration (all services' `application.yml`):**
```yaml
management:
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  health:
    readinessstate:
      enabled: true
    livenessstate:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

**New files:**
- `user-service/.../health/KeycloakHealthIndicator.java` — ping Keycloak server URL
- `api-gateway/.../health/EurekaHealthIndicator.java` — check Eureka connectivity
- `api-gateway/.../health/ConfigServerHealthIndicator.java` — check Config Server connectivity

**Docker Compose updates:**
- Add `healthcheck` blocks to each service definition using `/actuator/health/liveness`

### Verification
- `GET /actuator/health` returns detailed component status
- Stop Keycloak → user-service health shows Keycloak DOWN
- `/actuator/health/readiness` and `/actuator/health/liveness` respond correctly

---

## Dependencies Between Items

```
2.1 (Circuit Breakers) ─── independent, do first
2.2 (Idempotency Keys) ─── independent, do first
        │
2.4 (Authorization) ─────── depends on core-banking AccountResponse having userId
        │
2.3 (Shared Library) ────── can be done anytime, but easier after 2.1/2.2/2.4
        │                    settle the exception classes
2.5 (Unit Tests) ─────────── do after 2.1–2.4 so tests cover final implementations
        │
2.7 (Logging) ───────────── independent
2.8 (Health) ─────────────── independent
2.6 (URI Naming) ─────────── do last (breaking change, coordinate with consumers)
```

---

## Risk Register

| Risk | Mitigation |
|------|------------|
| Circuit breaker fallbacks mask real errors | Log all fallback invocations at WARN level; add metrics counter for fallback calls |
| Idempotency key migration on existing data | Make column nullable initially; backfill existing records with generated UUIDs |
| Shared library coupling | Keep banking-common thin — only truly shared code, no business logic |
| Authorization breaks existing flows | Add authorization as opt-in via feature flag initially; test with existing Postman collection |
| URI rename breaks consumers | Introduce new URIs alongside old ones; deprecate old ones with `@Deprecated` and response headers |

---

## Devin Prompts

Each item above can be executed with a single Devin prompt. See `docs/REMEDIATION_ROADMAP.md` (PR #62) for the full prompt text for each item (sections 2.1–2.8).
