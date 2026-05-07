# Technical Assessment — Internet Banking Microservices

> **One-page kickstart guide** consolidating the architecture, gaps, and remediation plan for this Java 21 / Spring Boot 3.2.4 banking application.

---

## Architecture at a Glance

| Service | Port | Role | DB |
|---|---|---|---|
| **core-banking-service** | 8092 | Accounts, users, transactions, balances | MySQL `banking_core_service` (Flyway) |
| **internet-banking-user-service** | 8083 | Registration, approval, Keycloak identity | MySQL `banking_core_user_service` |
| **internet-banking-fund-transfer-service** | 8084 | Fund transfer orchestration | MySQL `banking_core_fund_transfer_service` |
| **internet-banking-utility-payment-service** | 8085 | Utility bill payments | MySQL `banking_core_utility_payment_service` |
| **internet-banking-api-gateway** | 8082 | OAuth2/JWT enforcement, routing | — |
| **internet-banking-service-registry** | 8081 | Eureka service discovery | — |
| **internet-banking-config-server** | 8090 | Spring Cloud Config (Git-backed) | — |

**Communication:** All traffic enters via the API Gateway (JWT-secured). Gateway propagates `X-Auth-Id` header. User, Fund Transfer, and Utility Payment services call Core Banking via **OpenFeign** (Eureka-discovered). User Service also calls **Keycloak 23.0.7** Admin API directly.

**Infra:** MySQL 8, PostgreSQL 15 (Keycloak), Zipkin 3 (tracing), Docker Compose orchestration.

---

## Key API Endpoints

| Service | Method | Path | Purpose |
|---|---|---|---|
| Core Banking | GET | `/api/v1/account/bank-account/{number}` | Get account by number |
| Core Banking | POST | `/api/v1/transaction/fund-transfer` | Execute fund transfer |
| Core Banking | POST | `/api/v1/transaction/util-payment` | Execute utility payment |
| Core Banking | GET | `/api/v1/user/{identification}` | Get user by NIC |
| User | POST | `/api/v1/bank-users/register` | Register user (public) |
| User | PATCH | `/api/v1/bank-users/update/{id}` | Update/approve user |
| User | GET | `/api/v1/bank-users` | List users (paginated) |
| Fund Transfer | POST | `/api/v1/transfer` | Initiate transfer |
| Fund Transfer | GET | `/api/v1/transfer` | List transfers (paginated) |
| Utility Payment | POST | `/api/v1/utility-payment` | Process payment |
| Utility Payment | GET | `/api/v1/utility-payment` | List payments (paginated) |

**Gateway prefixes:** `/user/**`, `/fund-transfer/**`, `/banking-core/**`, `/utility-payment/**`

---

## Data Model Summary

**Core Banking (Flyway-managed):**
- `banking_core_user` → id, first_name, last_name, email, identification_number
- `banking_core_account` → id, number, type (SAVINGS/FIXED/LOAN), status (ACTIVE/DORMANT/BLOCKED), available_balance, actual_balance, user_id (FK)
- `banking_core_transaction` → id, amount, transaction_type (FUND_TRANSFER/UTILITY_PAYMENT), reference_number, transaction_id, account_id (FK)
- `banking_core_utility_account` → id, number, provider_name

**Other services (Hibernate auto-DDL):** Each has audit fields (created_date/by, modified_date/by, version) via `AuditAware` base class.

---

## Gap Summary (35 Gaps Identified)

| Severity | Count | Examples |
|---|---|---|
| **Critical** | 4 | No input validation, hardcoded credentials, no failure handling in fund transfers, no saga pattern |
| **High** | 9 | All errors return HTTP 400, minimal tests, no circuit breakers, no timeouts, actuator publicly exposed, password logged |
| **Medium** | 15 | No shared library, inconsistent logging, no Prometheus metrics, no pagination metadata, no rate limiting |
| **Low** | 7 | No multi-project Gradle, inconsistent packages, non-RESTful URLs, no filtering/search |

---

## Remediation Plan

### Phase 1 — Quick Wins (do first, ~1 day each)

| # | Gap | What to Do | Severity |
|---|---|---|---|
| **1** | No input validation | Add `@NotNull`, `@NotBlank`, `@Positive`, `@Email` to all request DTOs; add `@Valid` on controller params; add `spring-boot-starter-validation` dependency | Critical |
| **2** | All errors → HTTP 400 | Map `EntityNotFoundException` → 404, `InsufficientFundsException` → 422, `UserAlreadyRegisteredException` → 409, generic `Exception` → 500. Return structured `ErrorResponse` always | High |
| **3** | Password logged | Add `@ToString.Exclude` + `@JsonProperty(Access.WRITE_ONLY)` to `User.password`; fix log statement in `UserController` | High |
| **4** | Actuator public | Change gateway security: only permit `/actuator/health` and `/{service}/actuator/health`; require auth for all other actuator paths | High |
| **5** | No Feign timeouts | Add `spring.cloud.openfeign.client.config.default.connectTimeout=5000` and `readTimeout=10000` to all Feign-using services | High |
| **6** | Hardcoded credentials | Replace plaintext passwords in `docker-compose.yml` with `${ENV_VAR}` references; create `.env.example`; add `.env` to `.gitignore` | Critical |
| **7** | Keycloak singleton | Replace lazy singleton in `KeycloakProperties` with a Spring `@Bean`-managed singleton | Medium |
| **8** | OpenAPI wrong dep | Change `springdoc-openapi-starter-webflux-ui` → `webmvc-ui` in all servlet-based services (keep webflux only for gateway) | Medium |
| **9** | No DB pool config | Add HikariCP settings (max-pool-size: 10, connection-timeout: 20s, idle-timeout: 5min) to all DB-backed services | Medium |

<details>
<summary><strong>Sample Devin Prompts for Phase 1</strong></summary>

**Input Validation (Item 1):**
```
In ts-java-spring-boot-internet-banking-microservices, add Jakarta Bean Validation to all request DTOs:
- core-banking FundTransferRequest: @NotBlank on fromAccount/toAccount, @NotNull @Positive on amount
- core-banking UtilityPaymentRequest: @NotNull on providerId, @NotNull @Positive on amount, @NotBlank on referenceNumber/account
- fund-transfer FundTransferRequest: same validations
- utility-payment UtilityPaymentRequest: same validations
- user-service User: @NotBlank @Email on email, @NotBlank on identification/password
Add @Valid on all @RequestBody params. Add spring-boot-starter-validation to build.gradle where missing.
```

**HTTP Status Codes (Item 2):**
```
In ts-java-spring-boot-internet-banking-microservices, update GlobalExceptionHandler in all 4 services:
EntityNotFoundException → 404, InsufficientFundsException → 422, UserAlreadyRegisteredException → 409,
InvalidEmailException → 400, generic Exception → 500. Always return ErrorResponse JSON, never raw strings.
Never expose exception class names or stack traces.
```

**Feign Timeouts (Item 5):**
```
In ts-java-spring-boot-internet-banking-microservices, add Feign timeout config to fund-transfer,
user-service, and utility-payment application.yml:
spring.cloud.openfeign.client.config.default.connectTimeout=5000
spring.cloud.openfeign.client.config.default.readTimeout=10000
```

</details>

---

### Phase 2 — Important Improvements (~2-5 days each)

| # | Gap | What to Do | Severity |
|---|---|---|---|
| **10** | No failure handling in fund transfer | Wrap Feign calls in try-catch; update entity status to `FAILED` on exception; add Feign error decoders to fund-transfer and utility-payment services | Critical |
| **11** | No circuit breakers | Add `spring-cloud-starter-circuitbreaker-resilience4j`; configure circuit breakers on all Feign clients with fallback factories | High |
| **12** | Extract shared module | Create `banking-common` Gradle module with `BaseMapper`, `AuditAware`, `ApiRequestContext*`, `AppAuthUserFilter`, exception classes (all currently copy-pasted across 4 services) | Medium |
| **13** | Standardize error format | Unified `ErrorResponse` with timestamp, status, code, message, path; handle `MethodArgumentNotValidException` for validation errors | High |
| **14** | Add unit tests | Target 80%+ coverage for service/controller classes in user, fund-transfer, utility-payment services (currently only context-load tests) | High |
| **15** | Fix pagination | Return `PagedResponse<T>` wrapper (content, page, size, totalElements, totalPages) instead of raw `List<>` | Medium |
| **16** | Add rate limiting | Add `RequestRateLimiter` filter to API Gateway; stricter limits on `/register` endpoint | Medium |
| **17** | Fix logging | Fix string concatenation bug in `FundTransferService`; add `@ToString.Exclude` on sensitive fields; configure structured JSON logging | Medium |
| **18** | Add Prometheus metrics | Add `micrometer-registry-prometheus`; expose `/actuator/prometheus`; add custom counters/timers for transfers and payments | Medium |

<details>
<summary><strong>Sample Devin Prompts for Phase 2</strong></summary>

**Failure Handling (Item 10):**
```
In internet-banking-fund-transfer-service FundTransferService.fundTransfer(), wrap the
bankingCoreFeignClient.fundTransfer() call in try-catch. On exception: set entity status to FAILED,
save, then rethrow. Apply same pattern to utility-payment-service. Add CustomFeignErrorDecoder to
both services (copy pattern from user-service). Add unit tests for failure scenarios.
```

**Circuit Breakers (Item 11):**
```
Add spring-cloud-starter-circuitbreaker-resilience4j to fund-transfer, user-service, utility-payment.
Configure: slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=10s. Add retry:
maxAttempts=3, waitDuration=1s. Create fallback factories returning meaningful error responses.
```

**Shared Module (Item 12):**
```
Create banking-common/ module. Move BaseMapper, AuditAware, ApiRequestContext/Holder,
AppAuthUserFilter, SimpleBankingGlobalException, ErrorResponse, GlobalExceptionHandler,
AuditConfig/AuditorAwareConfig into it. Add root settings.gradle including all modules.
Update each service to depend on banking-common and remove duplicated classes.
```

</details>

---

### Phase 3 — Advanced / Polish (~5+ days each)

| # | Gap | What to Do | Severity |
|---|---|---|---|
| **19** | No saga pattern | Implement choreography-based saga for fund transfers using RabbitMQ (already in Docker Compose). Add outbox pattern for atomic event publishing. Add compensation logic | Critical |
| **20** | No integration tests | Add Testcontainers (MySQL, Keycloak) for full API integration tests; use WireMock for Feign call testing | High |
| **21** | No contract tests | Add Spring Cloud Contract: define contracts in core-banking (producer), verify Feign clients in consumers via stub runner | Medium |
| **22** | Multi-project Gradle | Root `settings.gradle.kts` + shared config in root `build.gradle.kts`; single `./gradlew build` for all services | Low |
| **23** | Custom health checks | Add `KeycloakHealthIndicator` (user-service), `CoreBankingHealthIndicator` (all Feign consumers) | Medium |
| **24** | Add filtering/search | Add query params (status, dateRange, account) to list endpoints using JPA Specifications | Low |

---

## Key Business Flows (Quick Reference)

**User Registration:** `POST /register` → check Keycloak for existing email → validate NIC against Core Banking → create Keycloak user (disabled) → save local entity (PENDING) → admin approves via PATCH (enables Keycloak account)

**Fund Transfer:** `POST /transfer` → save local entity (PENDING) → Feign call to Core Banking → validate balance → debit source, credit destination → create 2 transaction records → update local entity (SUCCESS)

**Utility Payment:** `POST /utility-payment` → save local entity (PROCESSING) → Feign call to Core Banking → validate balance → debit source → create transaction record → update local entity (SUCCESS)

**Balance Rule:** `actualBalance >= 0 AND actualBalance >= amount`, else `InsufficientFundsException`

---

## Tech Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.4 |
| Spring Cloud | 2023.0.0 |
| Keycloak | 23.0.7 |
| MySQL Connector | 8.4.0 |
| Flyway | 10.12.0 (core-banking only) |
| Gradle | 8.7 |
| Docker base | eclipse-temurin:21.0.2_13-jre-alpine |
| Zipkin | 3 |

---

## Getting Started

```bash
# Start infrastructure
docker-compose -f docker-compose/docker-compose-support-apps.yml up -d

# Run individual services locally (each in separate terminal)
cd core-banking-service && ./gradlew bootRun
cd internet-banking-user-service && ./gradlew bootRun
cd internet-banking-fund-transfer-service && ./gradlew bootRun
cd internet-banking-utility-payment-service && ./gradlew bootRun

# Test credentials
# Email: ib_admin@javatodev.com
# Password: 5V7huE3G86uB
```

**Config:** All services pull config from Spring Cloud Config Server (port 8090), which reads from [this Git repo](https://github.com/JavatoDev-com/internet-banking-microservices-configurations.git). Use `bootstrap.yml` profiles: `local` (default), `dev`, `docker`.
