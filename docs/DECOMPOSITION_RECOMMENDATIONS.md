# Decomposition Recommendations

> **Context**: Based on the [Business Capability Map](./BUSINESS_CAPABILITY_MAP.md) and [Payment Gap Analysis](./PAYMENT_GAP_ANALYSIS.md), this document recommends how to restructure the 7-service Internet Banking platform to better align service boundaries with business capabilities.

---

## 1. Recommendations Overview

| # | Recommendation | Type | Priority | Impact | Feasibility |
|---|---|---|---|---|---|
| R-01 | Extract shared library (`banking-common`) | **Consolidate** | **P0 — Immediate** | High | High |
| R-02 | Decompose Core Banking Service | **Split** | **P1 — High** | Critical | Medium |
| R-03 | Enrich Fund Transfer & Utility Payment services | **Restructure** | **P1 — High** | High | Medium |
| R-04 | Introduce async event bus | **Restructure** | **P2 — Medium** | High | Medium |
| R-05 | Add Notification Service | **New service** | **P2 — Medium** | Medium | High |
| R-06 | Add Reconciliation Service | **New service** | **P2 — Medium** | High | Medium |
| R-07 | Merge or federate User Service with Account domain | **Merge** | **P3 — Lower** | Medium | Low |

---

## 2. Detailed Recommendations

### R-01: Extract Shared Library (`banking-common`)

#### Business Justification

8 classes are duplicated across 3-4 services (see [Capability Map §3.1](./BUSINESS_CAPABILITY_MAP.md#31-code-level-duplication)). Every bug fix or enhancement must be applied in 3-4 places. This duplication has already caused inconsistencies (e.g., User Service has a `CustomFeignErrorDecoder` while Fund Transfer and Utility Payment do not). A shared library eliminates this maintenance burden and ensures consistent behavior.

#### Technical Approach

1. Create a new Gradle module `banking-common` at the repo root
2. Move shared classes into the common module:
   - `exception/` — `GlobalExceptionHandler`, `ErrorResponse`, `SimpleBankingGlobalException`
   - `model/` — `AuditAware` (MappedSuperclass)
   - `mapper/` — `BaseMapper<E,D>` interface
   - `configuration/audit/` — `AuditConfig`, `AuditorAwareConfig`
   - `configuration/filter/` — `AppAuthUserFilter`, `ApiRequestContext`, `ApiRequestContextHolder`
   - `dto/` — `AccountResponse`
3. Publish as a local Maven/Gradle composite build dependency
4. Update each service's `build.gradle` to depend on `banking-common`
5. Delete duplicated classes from all services

#### Affected Services

- `core-banking-service`
- `internet-banking-user-service`
- `internet-banking-fund-transfer-service`
- `internet-banking-utility-payment-service`

---

### R-02: Decompose Core Banking Service

#### Business Justification

Core Banking is currently a **God Service** owning 4 distinct business domains:

| Domain | Tables | APIs | Coupling Risk |
|---|---|---|---|
| User Management | `banking_core_user` | `GET /api/v1/user/*` | User Service depends on it for registration |
| Account Management | `banking_core_account` | `GET /api/v1/account/*` | Fund Transfer + Utility Payment query balances |
| Transaction Processing | `banking_core_transaction` | `POST /api/v1/transaction/*` | Fund Transfer + Utility Payment submit transactions |
| Utility Provider Mgmt | `banking_core_utility_account` | `GET /api/v1/account/util-account/*` | Utility Payment resolves providers |

A failure in any one domain (e.g., a slow query on `banking_core_transaction`) impacts all others. Deploying a change to user management requires redeploying the entire core, including transaction processing.

#### Technical Approach

Split Core Banking into 3 focused services:

**a) Account Service** (new)
- Owns `banking_core_account` and `banking_core_user` tables
- Exposes account CRUD, balance inquiry, account status management
- Exposes user lookup (by NIC, by email) for downstream consumption
- Includes account ownership verification logic

**b) Transaction Service** (new)
- Owns `banking_core_transaction` table
- Exposes fund transfer execution (debit source, credit destination, record transaction)
- Exposes utility payment execution (debit source, record transaction)
- Implements balance validation with proper locking
- Communicates with Account Service for balance reads/updates (or shares DB in phase 1)

**c) Utility Provider Service** (new, lightweight)
- Owns `banking_core_utility_account` table
- Exposes provider lookup and management
- Could be merged into Transaction Service if provider count remains small

#### Migration Path

1. **Phase 1**: Logical separation — create separate controller/service/repository packages within Core Banking, remove cross-domain method calls
2. **Phase 2**: Extract Account Service as a separate deployable — Fund Transfer and Utility Payment update Feign clients
3. **Phase 3**: Extract Transaction Service — Fund Transfer and Utility Payment route transaction submissions to the new service

---

### R-03: Enrich Fund Transfer & Utility Payment Services

#### Business Justification

Currently, these services are **anemic orchestrators** — they persist a status row, make a Feign call, and update the status. They add network latency without meaningful business value. However, they are the correct boundary for payment domain logic. Instead of merging them back into Core Banking (which would worsen the God Service problem), enrich them with the business logic they should own.

#### Technical Approach

**Fund Transfer Service — Add:**
- Input validation (`@Valid`, `@NotNull`, `@Positive`, `@NotBlank`)
- Idempotency key handling (`X-Idempotency-Key` header → check `fund_transfer` table for existing key)
- Account ownership verification (compare `authID` against account owner)
- Self-transfer prevention (`fromAccount != toAccount`)
- Amount limit enforcement (configurable min/max/daily limits)
- ISO 20022 aligned request model:
  - `currency` (ISO 4217 code)
  - `endToEndId` (client-generated unique ID)
  - `remittanceInformation` (memo/narration)
  - `requestedExecutionDate` (for future-dated transfers)
- Compensation/saga logic (retry or reverse on Feign failure)
- Circuit breaker on Core Banking Feign client (Resilience4j)

**Utility Payment Service — Add:**
- Same validation enhancements as Fund Transfer
- Provider validation (active/inactive status)
- Bill reference format validation
- ISO 20022 aligned request model with payment purpose code

#### Affected Files

| Service | File | Change |
|---|---|---|
| Fund Transfer | `FundTransferRequest.java` | Add validation annotations + new fields |
| Fund Transfer | `FundTransferService.java` | Add idempotency, ownership, limits logic |
| Fund Transfer | `FundTransferController.java` | Add `@Valid` on `@RequestBody` |
| Fund Transfer | `build.gradle` | Add `spring-boot-starter-validation`, `resilience4j` |
| Utility Payment | `UtilityPaymentRequest.java` | Add validation annotations + new fields |
| Utility Payment | `UtilityPaymentService.java` | Add idempotency, validation logic |
| Utility Payment | `UtilityPaymentController.java` | Add `@Valid` on `@RequestBody` |

---

### R-04: Introduce Async Event Bus

#### Business Justification

All inter-service communication is synchronous REST via OpenFeign. This creates:
- **Cascading failures**: Core Banking slowness blocks all payment processing
- **Tight temporal coupling**: All services must be online simultaneously
- **No event replay**: Failed notifications, reconciliation checks cannot be retried

An event bus enables eventual consistency, decoupled processing, and audit event streams.

#### Technical Approach

1. Introduce **RabbitMQ** (already mentioned in original docs) or **Apache Kafka** as the message broker
2. Define domain events:
   - `FundTransferInitiated` → published by Fund Transfer Service
   - `FundTransferCompleted` / `FundTransferFailed` → published by Transaction Service
   - `UtilityPaymentInitiated` → published by Utility Payment Service
   - `UtilityPaymentCompleted` / `UtilityPaymentFailed` → published by Transaction Service
   - `AccountBalanceChanged` → published by Account Service
   - `UserRegistered` / `UserApproved` → published by User Service
3. Replace synchronous Feign calls for non-critical paths (notifications, reconciliation, audit logging)
4. Keep synchronous Feign for the critical payment execution path (where immediate consistency is required) but add circuit breakers

#### Dependency Changes

```
Fund Transfer Service ──(async)──► Event Bus ──► Notification Service
                       ──(sync)──► Transaction Service (enriched Core Banking)
```

---

### R-05: Add Notification Service

#### Business Justification

No notification capability exists. Users receive no confirmation of transfers, no alerts for failed payments, and no registration emails beyond Keycloak's built-in verification. A notification service is a standard banking requirement for regulatory compliance and customer trust.

#### Technical Approach

1. Create `internet-banking-notification-service`
2. Subscribe to domain events from the event bus:
   - `FundTransferCompleted` → email/SMS confirmation to sender
   - `UtilityPaymentCompleted` → email/SMS receipt
   - `UserApproved` → welcome email
   - `FundTransferFailed` → alert to sender
3. Support multiple channels: email (SMTP/SES), SMS (SNS/Twilio), push (FCM)
4. Include template management and delivery status tracking

---

### R-06: Add Reconciliation Service

#### Business Justification

Orphaned `PENDING` fund transfers and `PROCESSING` utility payments are never resolved if the Feign call to Core Banking fails. There is no process to detect or fix these inconsistencies. This is a financial integrity risk.

#### Technical Approach

1. Create `internet-banking-reconciliation-service` (or a scheduled job within existing services)
2. Periodically scan:
   - `fund_transfer` records in `PENDING` status older than N minutes
   - `utility_payment` records in `PROCESSING` status older than N minutes
3. Cross-reference against `banking_core_transaction` to determine if the transaction actually completed
4. Either complete the status update or mark as `FAILED` and trigger compensation
5. Publish reconciliation events for audit trail

---

### R-07: Merge or Federate User Service with Account Domain

#### Business Justification

User Service depends on Core Banking for user identity verification (`GET /api/v1/user/{identification}`). The `banking_core_user` table — which stores names, emails, and NIC numbers — is owned by Core Banking, but registration and status management are owned by User Service. This split-ownership creates confusion about which service is the source of truth for user data.

#### Technical Approach

**Option A: Federate** (recommended for incremental change)
- When Core Banking is decomposed (R-02), the Account Service inherits `banking_core_user`
- User Service continues to own IAM orchestration (Keycloak) and `banking_core_user_service.user` (local status)
- Define a clear contract: Account Service owns identity data, User Service owns authentication/authorization state
- User Service subscribes to `UserCreated` events from Account Service instead of making synchronous Feign calls

**Option B: Merge** (simpler but larger blast radius)
- Merge User Service into the new Account Service
- Account Service owns the full user lifecycle: identity, accounts, and IAM orchestration
- Eliminates the Feign dependency entirely

---

## 3. Dependency Diagram — Current vs. Proposed

### Current Architecture

```
Client
  │
  ▼
┌──────────────┐
│  API Gateway │  ← OAuth2/JWT validation
└──┬──┬──┬─────┘
   │  │  │
   │  │  └──────────────────────────────┐
   │  └─────────────────┐               │
   ▼                    ▼               ▼
┌──────────┐     ┌────────────┐  ┌──────────────┐
│  User    │     │   Fund     │  │   Utility    │
│  Service │     │  Transfer  │  │   Payment    │
└────┬─────┘     └──────┬─────┘  └───────┬──────┘
     │                  │                │
     │  Feign (sync)    │  Feign (sync)  │  Feign (sync)
     │                  │                │
     └──────────────────┼────────────────┘
                        ▼
              ┌──────────────────┐
              │  Core Banking    │  ← GOD SERVICE
              │  (Users +        │     Single point of failure
              │   Accounts +     │     4 domains in 1 service
              │   Transactions + │
              │   Util Providers)│
              └────────┬─────────┘
                       ▼
                ┌─────────────┐
                │   MySQL     │
                │  (4 schemas,│
                │  1 instance)│
                └─────────────┘

Problems:
  ✗ Core Banking is a God Service (4 domains)
  ✗ All communication is synchronous (no fallback)
  ✗ Fund Transfer & Utility Payment are thin proxies
  ✗ No notification, reconciliation, or audit services
  ✗ No event-driven capabilities
```

### Proposed Architecture (Target State)

```
Client
  │
  ▼
┌──────────────┐
│  API Gateway │  ← OAuth2/JWT + rate limiting
└──┬──┬──┬──┬──┘
   │  │  │  │
   │  │  │  └───────────────────────────────────┐
   │  │  └──────────────────┐                    │
   │  └──────┐              │                    │
   ▼         ▼              ▼                    ▼
┌────────┐ ┌────────────┐ ┌──────────────┐ ┌────────────┐
│ User   │ │   Fund     │ │   Utility    │ │ (Future    │
│Service │ │  Transfer  │ │   Payment    │ │  Services) │
│(IAM)   │ │ (enriched) │ │ (enriched)   │ │            │
└───┬────┘ └──┬───┬─────┘ └──┬───┬───────┘ └────────────┘
    │         │   │           │   │
    │ Feign   │   │ Events    │   │ Events
    │ (sync)  │   │ (async)   │   │ (async)
    │         │   │           │   │
    │         │   ▼           │   ▼
    │         │  ┌────────────────────────┐
    │         │  │     Event Bus          │
    │         │  │  (RabbitMQ / Kafka)    │
    │         │  └──┬─────────┬──────┬───┘
    │         │     │         │      │
    │         │     ▼         ▼      ▼
    │         │  ┌────────┐ ┌────┐ ┌──────────────┐
    │         │  │Notifi- │ │Rec-│ │ Audit/       │
    │         │  │cation  │ │on- │ │ Compliance   │
    │         │  │Service │ │cil-│ │ Service      │
    │         │  └────────┘ │ia- │ └──────────────┘
    │         │             │tion│
    │   Feign │             └────┘
    │  (sync) │
    ▼         ▼
┌──────────────────┐  ┌─────────────────────┐
│ Account Service  │  │ Transaction Service  │
│ (users +         │  │ (transfers +         │
│  accounts)       │  │  payments + ledger)  │
└───────┬──────────┘  └──────────┬───────────┘
        │                        │
        ▼                        ▼
   ┌──────────┐            ┌──────────┐
   │  MySQL   │            │  MySQL   │
   │(accounts)│            │(txn/ledge│
   └──────────┘            └──────────┘

Improvements:
  ✓ Core Banking decomposed into Account + Transaction services
  ✓ Fund Transfer & Utility Payment enriched with business logic
  ✓ Event bus enables async processing (notifications, reconciliation)
  ✓ Circuit breakers on all synchronous Feign calls
  ✓ Shared library eliminates code duplication
  ✓ Each service has its own database (true data autonomy)
```

---

## 4. Implementation Prioritization

### Phase 1: Foundation (Weeks 1-4) — High Impact, High Feasibility

| # | Task | Effort | Dependencies |
|---|---|---|---|
| 1.1 | **R-01**: Create `banking-common` shared library | 1 week | None |
| 1.2 | **R-03 (partial)**: Add Bean Validation to Fund Transfer & Utility Payment request DTOs | 2-3 days | None |
| 1.3 | **R-03 (partial)**: Add idempotency key support to Fund Transfer & Utility Payment | 3-4 days | None |
| 1.4 | **R-03 (partial)**: Add Resilience4j circuit breakers on Feign clients | 2-3 days | None |
| 1.5 | Fix G-02 double-deduction bug in `TransactionService` | 1 day | None |
| 1.6 | Add account status validation and ownership checks | 2-3 days | None |

### Phase 2: Core Restructuring (Weeks 5-10) — Critical Impact, Medium Feasibility

| # | Task | Effort | Dependencies |
|---|---|---|---|
| 2.1 | **R-02 (phase 1)**: Logically separate domains within Core Banking (package refactoring) | 1-2 weeks | 1.1 |
| 2.2 | **R-02 (phase 2)**: Extract Account Service as separate deployable | 2 weeks | 2.1 |
| 2.3 | **R-03 (complete)**: Add ISO 20022 aligned fields to payment DTOs | 1 week | 1.2 |
| 2.4 | **R-04 (initial)**: Introduce message broker (RabbitMQ) and publish first events | 1-2 weeks | None |
| 2.5 | **R-06**: Build reconciliation job for orphaned PENDING/PROCESSING records | 1 week | 2.1 |

### Phase 3: Ecosystem Completion (Weeks 11-16) — Medium Impact, Varied Feasibility

| # | Task | Effort | Dependencies |
|---|---|---|---|
| 3.1 | **R-02 (phase 3)**: Extract Transaction Service from Core Banking | 2 weeks | 2.2 |
| 3.2 | **R-05**: Build Notification Service subscribing to domain events | 2 weeks | 2.4 |
| 3.3 | **R-07**: Federate User Service with Account Service (event-driven) | 1-2 weeks | 2.2, 2.4 |
| 3.4 | Add proper HTTP status codes and structured error responses | 1 week | 1.1 |
| 3.5 | Database per service: migrate from shared MySQL to independent instances | 2 weeks | 2.2, 3.1 |
| 3.6 | Add comprehensive integration and contract tests | 2-3 weeks | All above |

---

## 5. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Data migration errors** during Core Banking decomposition | Medium | High | Dual-read strategy: new service reads from old + new schema, validated against each other before cutover |
| **Service communication failures** during Feign client updates | Medium | Medium | Feature flags: route traffic to old vs. new endpoints, with instant rollback |
| **Performance regression** from additional network hops | Low | Medium | Keep Account + Transaction co-located initially; benchmark before and after |
| **Team coordination overhead** across multiple services | Medium | Medium | Clear domain ownership: assign one team per bounded context |
| **Incomplete event schema** causing downstream failures | Medium | High | Define event contracts (Avro/Protobuf) upfront; use schema registry |

---

## 6. Success Metrics

| Metric | Current State | Target State |
|---|---|---|
| **Service availability** (per domain) | Single core failure = full outage | Per-domain failure is isolated |
| **Duplicated code files** | ~28 files across 4 services | 0 (shared library) |
| **Payment validation coverage** | ~10% of standard checks | >90% |
| **ISO 20022 readiness** (pain.001) | ~21% field coverage | >70% |
| **Orphaned transaction rate** | Unknown (no monitoring) | <0.01% with reconciliation |
| **Mean time to deploy** (per domain) | Full rebuild required | Independent per-service deploy |
| **Circuit breaker protection** | 0 Feign clients protected | 100% |
